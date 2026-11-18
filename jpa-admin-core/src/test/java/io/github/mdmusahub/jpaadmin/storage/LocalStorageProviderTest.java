package io.github.mdmusahub.jpaadmin.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The local provider (UPL-3, UPL-4, UPL-10): random names, a root check on every path, no symbolic link, nothing left behind
 * by a failed copy, and nothing a client sends is ever a path.
 */
class LocalStorageProviderTest {

    private static final Clock NOVEMBER = Clock.fixed(Instant.parse("2026-11-18T10:15:30Z"), ZoneOffset.UTC);
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R'};

    @TempDir
    Path temp;

    private Path root;
    private Path outside;
    private LocalStorageProvider provider;

    @BeforeEach
    void setUp() throws IOException {
        root = temp.resolve("storage-root");
        outside = temp.resolve("outside");
        Files.createDirectories(outside);
        provider = provider(root);
        assertThat(provider.verify("default")).isEmpty();
    }

    private LocalStorageProvider provider(Path directory) {
        Map<String, StorageSettings> storages = Map.of(
                "default", new StorageSettings("default", "local", directory.toString(), 1024 * 1024, null),
                "public", new StorageSettings("public", "local", directory.resolve("pub").toString(), 1024 * 1024, "/files"),
                "cloud", new StorageSettings("cloud", "s3", directory.toString(), 1024 * 1024, null));
        return new LocalStorageProvider(storages, "/admin/files", List.of(), new SecureRandom(), NOVEMBER);
    }

    private static InputStream bytes(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    private Set<Path> filesBelow(Path folder) throws IOException {
        try (Stream<Path> walk = Files.walk(folder)) {
            return new HashSet<>(walk.filter(Files::isRegularFile).toList());
        }
    }

    // ---- names and places ---------------------------------------------------------------------------------------------------------

    @Test
    void aStoredFileGetsARandomNameInTheYearAndMonthFolderOfTheStorage() throws IOException {
        StoredFile stored = provider.store("default", "", "png", new ByteArrayInputStream(PNG), 1000);

        assertThat(stored.storage()).isEqualTo("default");
        assertThat(stored.key()).matches("2026/11/[0-9a-f]{32}\\.png");
        Path file = root.resolve(stored.key());
        assertThat(file).exists().hasBinaryContent(PNG);
        assertThat(filesBelow(root)).containsExactly(file);
    }

    @Test
    void theFolderIsPartOfTheKey() throws IOException {
        StoredFile stored = provider.store("default", "docs/scans", "pdf", bytes("%PDF-1.7"), 1000);

        assertThat(stored.key()).matches("docs/scans/2026/11/[0-9a-f]{32}\\.pdf");
        assertThat(root.resolve(stored.key())).exists();
    }

    @Test
    void everyStoredPathLiesInsideTheRealRoot() throws IOException {
        Path realRoot = root.toRealPath();
        for (String folder : List.of("", "a", "a/b", "a/b/c/d")) {
            StoredFile stored = provider.store("default", folder, "png", new ByteArrayInputStream(PNG), 1000);
            assertThat(realRoot.resolve(stored.key()).normalize().startsWith(realRoot)).isTrue();
        }
        assertThat(filesBelow(realRoot)).hasSize(4).allMatch(path -> path.startsWith(realRoot));
    }

    @Test
    void twoHundredUploadsNeverCollide() throws IOException {
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            keys.add(provider.store("default", "", "png", new ByteArrayInputStream(PNG), 1000).key());
        }

        assertThat(keys).hasSize(200);
        assertThat(filesBelow(root)).hasSize(200);
    }

    @Test
    void aNameThatIsAlreadyTakenIsNeverOverwritten() throws IOException {
        SecureRandom constant = new SecureRandom() {
            private static final long serialVersionUID = 1L;

            @Override
            public void nextBytes(byte[] bytes) {
                Arrays.fill(bytes, (byte) 7);
            }
        };
        LocalStorageProvider stuck = new LocalStorageProvider(Map.of("default",
                new StorageSettings("default", "local", root.toString(), 1024, null)), "/admin/files", List.of(), constant,
                NOVEMBER);
        StoredFile first = stuck.store("default", "", "png", bytes("first"), 1000);

        assertThatThrownBy(() -> stuck.store("default", "", "png", bytes("second"), 1000)).isInstanceOf(IOException.class);

        assertThat(Files.readString(root.resolve(first.key()))).isEqualTo("first");
        assertThat(filesBelow(root)).hasSize(1);
    }

    // ---- nothing a client sends is a path -------------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"..", "../x", "a/../../x", "/etc", "\\windows", "C:\\x", "a\\b", "a\u0000b", "aux", "com1/x", "NUL",
            "a/./b", "%2e%2e/x"})
    void aFolderThatIsNotSafeIsRefusedAndNothingIsCreated(String folder) throws IOException {
        assertThatThrownBy(() -> provider.store("default", folder, "png", new ByteArrayInputStream(PNG), 1000))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(filesBelow(root)).isEmpty();
        assertThat(filesBelow(outside)).isEmpty();
    }

    @Test
    void aVeryLongFolderIsRefused() {
        assertThatThrownBy(() -> provider.store("default", "a".repeat(5000), "png", new ByteArrayInputStream(PNG), 1000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "p", "png/../x", "../png", "p.g", "PNG", "pngpng", "p\u0000g", "exe;", "a/b"})
    void anExtensionThatIsNotAShortLowerCaseWordIsRefused(String extension) {
        assertThatThrownBy(() -> provider.store("default", "", extension, new ByteArrayInputStream(PNG), 1000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anUnknownOrNonLocalStorageIsRefused() {
        assertThatThrownBy(() -> provider.store("nothing", "", "png", new ByteArrayInputStream(PNG), 1000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> provider.store("cloud", "", "png", new ByteArrayInputStream(PNG), 1000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> provider.store("../x", "", "png", new ByteArrayInputStream(PNG), 1000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "..", "../outside/secret.png", "..\\outside\\secret.png", "/etc/passwd", "C:\\Windows\\win.ini",
            "2026/11/../../../outside/secret.png", "2026/11/%2e%2e/secret.png", "2026/11/aux.png", "2026/11/\u0000.png",
            "2026/11/0123456789abcdef0123456789abcdef.png\u0000", "CON", "nul", "2026/11/CON.png"})
    void openAndDeleteTreatAnythingButAKeyAsNotThere(String key) throws IOException {
        Files.writeString(outside.resolve("secret.png"), "secret");

        assertThat(provider.open("default", key)).isEmpty();
        assertThatThrownBy(() -> new StoredFile("default", key)).isInstanceOf(IllegalArgumentException.class);

        assertThat(outside.resolve("secret.png")).exists();
    }

    @Test
    void aVeryLongKeyAndANullAreNotThere() throws IOException {
        assertThat(provider.open("default", "a/".repeat(2000) + "x.png")).isEmpty();
        assertThat(provider.open("default", null)).isEmpty();
        assertThat(provider.open(null, "2026/11/0123456789abcdef0123456789abcdef.png")).isEmpty();
        assertThat(provider.open("nothing", "2026/11/0123456789abcdef0123456789abcdef.png")).isEmpty();
        assertThat(provider.open("cloud", "2026/11/0123456789abcdef0123456789abcdef.png")).isEmpty();
    }

    @Test
    void aFileOutsideTheRootIsNotReachableEvenWithAValidLookingKey() throws IOException {
        Files.createDirectories(outside.resolve("2026/11"));
        Files.writeString(outside.resolve("2026/11/0123456789abcdef0123456789abcdef.png"), "outside");

        assertThat(provider.open("default", "2026/11/0123456789abcdef0123456789abcdef.png")).isEmpty();
    }

    // ---- open, delete, url ---------------------------------------------------------------------------------------------------------

    @Test
    void whatWasStoredCanBeOpenedAndDeleted() throws IOException {
        StoredFile stored = provider.store("default", "docs", "pdf", bytes("%PDF-1.4 hello"), 1000);

        try (StoredContent content = provider.open("default", stored.key()).orElseThrow()) {
            assertThat(content.size()).isEqualTo(14);
            assertThat(new String(content.stream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("%PDF-1.4 hello");
        }
        provider.delete(stored);

        assertThat(provider.open("default", stored.key())).isEmpty();
        provider.delete(stored);
    }

    @Test
    void theUrlIsUnderTheConsoleOrUnderThePublicPath() {
        StoredFile inConsole = new StoredFile("default", "docs/2026/11/0123456789abcdef0123456789abcdef.png");
        StoredFile inPublic = new StoredFile("public", "2026/11/0123456789abcdef0123456789abcdef.png");

        assertThat(provider.url(inConsole)).isEqualTo("/admin/files/default/docs/2026/11/0123456789abcdef0123456789abcdef.png");
        assertThat(provider.url(inPublic)).isEqualTo("/files/2026/11/0123456789abcdef0123456789abcdef.png");
    }

    @Test
    void aStoredFileValueRefusesAnyOtherShape() {
        assertThatThrownBy(() -> new StoredFile("default", "../x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StoredFile("Default", "2026/11/0123456789abcdef0123456789abcdef.png"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void supportsOnlyLocalStoragesThatAreConfigured() {
        assertThat(provider.supports("default")).isTrue();
        assertThat(provider.supports("public")).isTrue();
        assertThat(provider.supports("cloud")).isFalse();
        assertThat(provider.supports("nothing")).isFalse();
        assertThat(provider.location("default")).isEqualTo(root.toAbsolutePath().normalize().toString());
        assertThat(provider.location("cloud")).isNull();
    }

    // ---- failed copies leave nothing --------------------------------------------------------------------------------------------------

    @Test
    void aFileOverTheLimitIsRefusedAndLeavesNothingBehind() throws IOException {
        assertThatThrownBy(() -> provider.store("default", "", "png", new ByteArrayInputStream(new byte[101]), 100))
                .isInstanceOf(IOException.class);

        assertThat(filesBelow(root)).isEmpty();
    }

    @Test
    void aFileExactlyAtTheLimitIsStored() throws IOException {
        StoredFile stored = provider.store("default", "", "png", new ByteArrayInputStream(new byte[100]), 100);

        assertThat(root.resolve(stored.key())).hasSize(100);
    }

    @Test
    void aStreamThatFailsHalfWayLeavesNothingBehind() throws IOException {
        InputStream failing = new InputStream() {
            private int count;

            @Override
            public int read() throws IOException {
                if (count++ > 10) {
                    throw new IOException("boom");
                }
                return 1;
            }
        };

        assertThatThrownBy(() -> provider.store("default", "", "png", failing, 1000)).isInstanceOf(IOException.class);

        assertThat(filesBelow(root)).isEmpty();
    }

    // ---- symbolic links ----------------------------------------------------------------------------------------------------------------

    /** A folder link that works without special rights: a symbolic link, or on Windows a junction. */
    private boolean linkFolder(Path link, Path target) throws IOException, InterruptedException {
        try {
            Files.createSymbolicLink(link, target);
            return true;
        } catch (IOException | UnsupportedOperationException | SecurityException noRight) {
            if (!System.getProperty("os.name").toLowerCase().contains("win")) {
                return false;
            }
            Process process = new ProcessBuilder("cmd", "/c", "mklink", "/J", link.toString(), target.toString())
                    .redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            return process.waitFor() == 0 && Files.exists(link);
        }
    }

    @Test
    void aFolderLinkInsideTheRootThatLeadsOutsideIsRefusedOnStore() throws Exception {
        Files.createDirectories(root);
        assumeTrue(linkFolder(root.resolve("docs"), outside), "this machine cannot make a folder link");

        assertThatThrownBy(() -> provider.store("default", "docs", "png", new ByteArrayInputStream(PNG), 1000))
                .isInstanceOf(IOException.class);

        assertThat(filesBelow(outside)).isEmpty();
    }

    @Test
    void aMonthFolderThatIsALinkOutsideIsRefusedOnStore() throws Exception {
        Files.createDirectories(root.resolve("2026"));
        assumeTrue(linkFolder(root.resolve("2026/11"), outside), "this machine cannot make a folder link");

        assertThatThrownBy(() -> provider.store("default", "", "png", new ByteArrayInputStream(PNG), 1000))
                .isInstanceOf(IOException.class);

        assertThat(filesBelow(outside)).isEmpty();
    }

    @Test
    void aFileUnderALinkedFolderIsNotOpened() throws Exception {
        Files.createDirectories(outside.resolve("11"));
        Files.writeString(outside.resolve("11/0123456789abcdef0123456789abcdef.png"), "outside");
        Files.createDirectories(root.resolve("2026"));
        assumeTrue(linkFolder(root.resolve("2026/11"), outside.resolve("11")), "this machine cannot make a folder link");

        assertThat(provider.open("default", "2026/11/0123456789abcdef0123456789abcdef.png")).isEmpty();
    }

    @Test
    void aFileThatIsALinkToSomethingOutsideIsNotOpened() throws Exception {
        Files.createDirectories(root.resolve("2026/11"));
        Path secret = outside.resolve("secret.png");
        Files.writeString(secret, "secret");
        Path link = root.resolve("2026/11/0123456789abcdef0123456789abcdef.png");
        try {
            Files.createSymbolicLink(link, secret);
        } catch (IOException | UnsupportedOperationException noRight) {
            assumeTrue(false, "this machine cannot make a file link");
        }

        assertThat(provider.open("default", "2026/11/0123456789abcdef0123456789abcdef.png")).isEmpty();
    }

    // ---- startup: verify ---------------------------------------------------------------------------------------------------------------

    @Test
    void verifyCreatesTheFolderAndLeavesNoProbeBehind() throws IOException {
        Path fresh = temp.resolve("fresh/nested/root");

        List<String> problems = provider(fresh).verify("default");

        assertThat(problems).isEmpty();
        assertThat(fresh).isDirectory();
        assertThat(filesBelow(fresh)).isEmpty();
    }

    @Test
    void verifyReportsAFolderThatCannotBeCreated() throws IOException {
        Path file = temp.resolve("a-file");
        Files.writeString(file, "x");

        List<String> problems = provider(file.resolve("below")).verify("default");

        assertThat(problems).hasSize(1);
        assertThat(problems.get(0)).contains("cannot be created", "jpa.admin.storage.default.directory");
    }

    @Test
    void verifyReportsAPathThatIsAFile() throws IOException {
        Path file = temp.resolve("just-a-file");
        Files.writeString(file, "x");

        List<String> problems = provider(file).verify("default");

        assertThat(problems).hasSize(1);
        assertThat(problems.get(0)).contains("cannot be created or is not a folder");
    }

    @Test
    void verifyRefusesAFolderInsideTheWebRoot() throws IOException {
        Path webRoot = temp.resolve("web");
        Files.createDirectories(webRoot);
        LocalStorageProvider inside = new LocalStorageProvider(Map.of("default",
                new StorageSettings("default", "local", webRoot.resolve("static/uploads").toString(), 1024, null)),
                "/admin/files", List.of(webRoot), new SecureRandom(), NOVEMBER);

        List<String> problems = inside.verify("default");

        assertThat(problems).hasSize(1);
        assertThat(problems.get(0)).contains("inside the web root", "jpa.admin.storage.default.directory");
        assertThat(webRoot.resolve("static")).doesNotExist();
    }

    @Test
    void aFolderThatOnlySharesAPrefixWithTheWebRootIsFine() throws IOException {
        Path webRoot = temp.resolve("web");
        LocalStorageProvider beside = new LocalStorageProvider(Map.of("default",
                new StorageSettings("default", "local", temp.resolve("web-uploads").toString(), 1024, null)),
                "/admin/files", List.of(webRoot), new SecureRandom(), NOVEMBER);

        assertThat(beside.verify("default")).isEmpty();
    }

    @Test
    void verifyOfAStorageThatIsNotLocalIsAProblem() {
        assertThat(provider.verify("cloud")).hasSize(1);
        assertThat(provider.verify("nothing")).hasSize(1);
    }

    @Test
    void aProviderIsReplaceableThroughTheInterface() {
        StorageProvider replacement = new StorageProvider() {
            @Override
            public boolean supports(String storage) {
                return true;
            }

            @Override
            public StoredFile store(String storage, String folder, String extension, InputStream content, long maxBytes) {
                return new StoredFile(storage, "2026/11/0123456789abcdef0123456789abcdef.png");
            }

            @Override
            public void delete(StoredFile file) {
            }

            @Override
            public Optional<StoredContent> open(String storage, String key) {
                return Optional.empty();
            }

            @Override
            public String url(StoredFile file) {
                return "/elsewhere/" + file.key();
            }
        };

        assertThat(replacement.verify("default")).isEmpty();
        assertThat(replacement.location("default")).isNull();
        assertThat(replacement.url(new StoredFile("default", "2026/11/0123456789abcdef0123456789abcdef.png")))
                .startsWith("/elsewhere/");
    }
}

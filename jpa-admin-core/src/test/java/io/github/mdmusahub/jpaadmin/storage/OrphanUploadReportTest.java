package io.github.mdmusahub.jpaadmin.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.testsupport.UploadTestPersistence;
import io.github.mdmusahub.jpaadmin.testsupport.uploadmodel.Dossier;
import jakarta.persistence.EntityManager;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The read-only report of files that no record holds (Phase 14a, UPL-9, D-132). Real files in a real folder, real records in a
 * real database: what is listed, what is not, and above all that the report <b>changes nothing</b> (every file is still there, with
 * the bytes it had). Mutation (b): a report that deletes what it finds fails the tests that look at the disk afterwards.
 */
class OrphanUploadReportTest {

    private static final Clock NOVEMBER = Clock.fixed(Instant.parse("2026-11-21T10:00:00Z"), ZoneOffset.UTC);
    private static final AdminEntityRegistry REGISTRY = UploadTestPersistence.registry();

    @TempDir
    Path directory;

    private Path defaultRoot;
    private Path vaultRoot;
    private LocalStorageProvider provider;
    private UploadTargets targets;
    private EntityManager entityManager;

    @BeforeEach
    void emptyTheTableAndBuildTheProvider() {
        defaultRoot = directory.resolve("default");
        vaultRoot = directory.resolve("vault");
        Map<String, StorageSettings> storages = Map.of(
                "default", new StorageSettings("default", "local", defaultRoot.toString(), 1024 * 1024, null),
                "vault", new StorageSettings("vault", "local", vaultRoot.toString(), 1024 * 1024, null));
        provider = new LocalStorageProvider(storages, "/admin/files", List.of(), new SecureRandom(), NOVEMBER);
        targets = UploadTargets.of(REGISTRY, storages);
        try (EntityManager em = UploadTestPersistence.entityManager()) {
            em.getTransaction().begin();
            em.createQuery("delete from Dossier").executeUpdate();
            em.getTransaction().commit();
        }
        entityManager = UploadTestPersistence.entityManager();
    }

    // ---- helpers -------------------------------------------------------------------------------------------------------------

    private StoredFile store(String storage, String folder, String extension, String content) throws IOException {
        return provider.store(storage, folder, extension, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), 1024);
    }

    private Path path(StoredFile file) {
        Path root = file.storage().equals("vault") ? vaultRoot : defaultRoot;
        return root.resolve(file.key());
    }

    private Long dossier(String title, StoredFile cover, StoredFile contract) {
        Long[] id = new Long[1];
        try (EntityManager em = UploadTestPersistence.entityManager()) {
            em.getTransaction().begin();
            Dossier dossier = new Dossier();
            dossier.setTitle(title);
            dossier.setCover(cover == null ? null : provider.url(cover));
            dossier.setContract(contract == null ? null : provider.url(contract));
            em.persist(dossier);
            em.flush();
            id[0] = dossier.getId();
            em.getTransaction().commit();
        }
        return id[0];
    }

    private OrphanUploadReport.Report report() {
        entityManager.clear();
        return OrphanUploadReport.find(provider, targets, REGISTRY, entityManager);
    }

    private static List<String> keys(OrphanUploadReport.Report report) {
        return report.orphans().stream().map(orphan -> orphan.storage() + ":" + orphan.key()).toList();
    }

    private static String id(StoredFile file) {
        return file.storage() + ":" + file.key();
    }

    // ---- what is listed -------------------------------------------------------------------------------------------------------

    @Test
    void aFileNoRecordHoldsIsListedAndAFileARecordHoldsIsNot() throws IOException {
        StoredFile held = store("default", "", "png", "held cover");
        StoredFile orphan = store("default", "", "png", "nobody has this one");
        StoredFile heldContract = store("vault", "docs/scans", "pdf", "held contract");
        StoredFile orphanContract = store("vault", "docs/scans", "pdf", "an old contract");
        dossier("first", held, heldContract);

        OrphanUploadReport.Report report = report();

        assertThat(keys(report)).containsExactlyInAnyOrder(id(orphan), id(orphanContract));
        assertThat(report.filesScanned()).isEqualTo(4);
        assertThat(report.filesHeld()).isEqualTo(2);
        assertThat(report.missingFiles()).isZero();
        assertThat(report.orphans()).allSatisfy(found -> {
            assertThat(found.bytes()).isGreaterThan(0);
            assertThat(found.address()).startsWith("/admin/files/" + found.storage() + "/");
        });
    }

    @Test
    void aFileTwoRecordsHoldIsHeldNotAnOrphan() throws IOException {
        StoredFile shared = store("default", "", "png", "shared by two records");
        dossier("one", shared, null);
        dossier("two", shared, null);

        assertThat(report().orphans()).isEmpty();
    }

    @Test
    void replacingAFileLeavesTheOldOneAsAnOrphanWhichIsExactlyTheLimitOfUpl9() throws IOException {
        StoredFile before = store("default", "", "png", "the first picture");
        StoredFile after = store("default", "", "png", "the replacement");
        Long id = dossier("a record", before, null);
        assertThat(report().orphans()).as("the replacement is not held yet: it is the orphan").hasSize(1);

        try (EntityManager em = UploadTestPersistence.entityManager()) {
            em.getTransaction().begin();
            em.find(Dossier.class, id).setCover(provider.url(after));
            em.getTransaction().commit();
        }

        assertThat(keys(report())).containsExactly(id(before));
    }

    @Test
    void aRemovedFieldLeavesItsFileAsAnOrphan() throws IOException {
        StoredFile file = store("default", "", "png", "was held once");
        Long id = dossier("a record", file, null);
        try (EntityManager em = UploadTestPersistence.entityManager()) {
            em.getTransaction().begin();
            em.find(Dossier.class, id).setCover(null);
            em.getTransaction().commit();
        }

        assertThat(keys(report())).containsExactly(id(file));
    }

    @Test
    void anAddressHeldAtThePublicPathIsTheSameFileAsTheOneUnderTheConsole() throws IOException {
        Map<String, StorageSettings> storages = Map.of("default",
                new StorageSettings("default", "local", defaultRoot.toString(), 1024 * 1024, "/files"));
        LocalStorageProvider publicProvider = new LocalStorageProvider(storages, "/admin/files", List.of(), new SecureRandom(), NOVEMBER);
        StoredFile file = publicProvider.store("default", "", "png",
                new ByteArrayInputStream("served publicly".getBytes(StandardCharsets.UTF_8)), 1024);
        try (EntityManager em = UploadTestPersistence.entityManager()) {
            em.getTransaction().begin();
            Dossier dossier = new Dossier();
            dossier.setCover(publicProvider.url(file));
            em.persist(dossier);
            em.getTransaction().commit();
        }

        OrphanUploadReport.Report report = OrphanUploadReport.find(publicProvider, UploadTargets.of(REGISTRY, storages), REGISTRY,
                entityManager);

        assertThat(publicProvider.url(file)).startsWith("/files/");
        assertThat(report.orphans()).isEmpty();
        assertThat(report.filesHeld()).isEqualTo(1);
    }

    // ---- what is not listed -----------------------------------------------------------------------------------------------------

    @Test
    void aFileThatIsNotInTheConsolesShapeIsCountedAndNeverListedAsAnOrphan() throws IOException {
        store("default", "", "png", "one the console made");
        Files.writeString(defaultRoot.resolve(".probe-1234"), "x");
        Files.writeString(defaultRoot.resolve("notes.txt"), "put here by hand");
        Path year = Files.createDirectories(defaultRoot.resolve("2026/11"));
        Files.writeString(year.resolve("NOT-A-RANDOM-NAME.png"), "wrong shape");

        OrphanUploadReport.Report report = report();

        assertThat(report.orphans()).hasSize(1);
        assertThat(report.notConsoleFiles()).as("the probe file, the note and the wrongly named file").isEqualTo(3);
        assertThat(Files.exists(defaultRoot.resolve("notes.txt"))).isTrue();
        assertThat(Files.exists(defaultRoot.resolve(".probe-1234"))).isTrue();
    }

    @Test
    void aSymbolicLinkIsNotFollowedAndItsTargetIsNeverListed() throws IOException {
        StoredFile real = store("default", "", "png", "a real file");
        Path outside = Files.createDirectories(directory.resolve("elsewhere"));
        Path secret = outside.resolve("0123456789abcdef0123456789abcdef.png");
        Files.writeString(secret, "a file outside the storage");
        Path link = defaultRoot.resolve("linked");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (IOException | UnsupportedOperationException | SecurityException noLinks) {
            assumeTrue(false, "SKIPPED, not passed: this machine does not allow symbolic links to be created here (" + noLinks.getClass().getSimpleName() + ")");
        }

        OrphanUploadReport.Report report = report();

        assertThat(keys(report)).as("only the real file, nothing from behind the link").containsExactly(id(real));
        assertThat(report.notConsoleFiles()).isGreaterThanOrEqualTo(0);
        assertThat(Files.exists(secret)).isTrue();
    }

    @Test
    void aWindowsDirectoryJunctionIsNotFollowedEitherAndItsTargetIsNeverListed() throws Exception {
        // A junction needs no special privilege on Windows, so this runs where a symbolic link cannot be made. Elsewhere it is
        // skipped, and the test above (a real symbolic link) is the one that runs.
        assumeTrue(System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows"),
                "SKIPPED, not passed: a directory junction exists on Windows only (the symbolic link test covers this elsewhere)");
        StoredFile real = store("default", "", "png", "a real file");
        Path outside = Files.createDirectories(directory.resolve("elsewhere-junction"));
        Path hidden = outside.resolve("0123456789abcdef0123456789abcdef.png");
        Files.writeString(hidden, "a file outside the storage");
        Path junction = defaultRoot.resolve("linked");
        Process made = new ProcessBuilder("cmd", "/c", "mklink", "/J", junction.toString(), outside.toString())
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        assumeTrue(made.waitFor() == 0 && Files.exists(junction),
                "SKIPPED, not passed: the junction could not be created on this machine");

        OrphanUploadReport.Report report = report();

        assertThat(keys(report)).as("only the real file, nothing from behind the junction").containsExactly(id(real));
        assertThat(Files.exists(hidden)).as("the file behind the junction is untouched").isTrue();
        Files.delete(junction); // removes the junction itself, never what it points at
        assertThat(Files.exists(hidden)).isTrue();
    }

    @Test
    void anAddressWhoseFileIsGoneIsCountedAsMissingNotListed() throws IOException {
        StoredFile gone = store("default", "", "png", "deleted by hand later");
        dossier("a record", gone, null);
        Files.delete(path(gone));

        OrphanUploadReport.Report report = report();

        assertThat(report.orphans()).isEmpty();
        assertThat(report.missingFiles()).isEqualTo(1);
    }

    @Test
    void aValueThatIsNotAnAddressOfThisConsoleHoldsNothing() throws IOException {
        StoredFile orphan = store("default", "", "png", "nobody has this one");
        try (EntityManager em = UploadTestPersistence.entityManager()) {
            em.getTransaction().begin();
            Dossier dossier = new Dossier();
            dossier.setCover("https://elsewhere.example/picture.png");
            dossier.setContract("../../etc/passwd");
            em.persist(dossier);
            em.getTransaction().commit();
        }

        OrphanUploadReport.Report report = report();

        assertThat(keys(report)).containsExactly(id(orphan));
        assertThat(report.missingFiles()).isZero();
    }

    @Test
    void aStorageFolderThatDoesNotExistYetGivesAnEmptyReport() {
        OrphanUploadReport.Report report = report();

        assertThat(report.orphans()).isEmpty();
        assertThat(report.filesScanned()).isZero();
        assertThat(Files.exists(defaultRoot)).as("looking never creates the folder").isFalse();
        assertThat(Files.exists(vaultRoot)).isFalse();
    }

    @Test
    void aConsoleWithNoUploadFieldHasNothingToReport() {
        OrphanUploadReport.Report report = OrphanUploadReport.find(provider, UploadTargets.none(), REGISTRY, entityManager);

        assertThat(report.orphans()).isEmpty();
        assertThat(report.storagesNotScanned()).isEmpty();
    }

    @Test
    void aStorageThatIsNotALocalFolderIsSaidAndNotScanned() {
        StorageProvider elsewhere = new StorageProvider() {
            @Override
            public boolean supports(String storage) {
                return false;
            }

            @Override
            public StoredFile store(String storage, String folder, String extension, java.io.InputStream content, long maxBytes) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void delete(StoredFile file) {
                throw new AssertionError("a report never deletes");
            }

            @Override
            public Optional<StoredContent> open(String storage, String key) {
                return Optional.empty();
            }

            @Override
            public String url(StoredFile file) {
                return "/x/" + file.key();
            }
        };

        OrphanUploadReport.Report report = OrphanUploadReport.find(elsewhere, targets, REGISTRY, entityManager);

        assertThat(report.orphans()).isEmpty();
        assertThat(report.storagesNotScanned()).containsExactlyInAnyOrder("default", "vault");
    }

    // ---- it changes nothing -----------------------------------------------------------------------------------------------------

    @Test
    void everyFileIsStillThereWithTheBytesItHadAfterTheReport() throws IOException {
        List<StoredFile> all = new ArrayList<>();
        all.add(store("default", "", "png", "held cover"));
        all.add(store("default", "", "png", "orphan one"));
        all.add(store("default", "", "png", "orphan two"));
        all.add(store("vault", "docs/scans", "pdf", "orphan contract"));
        dossier("a record", all.get(0), null);
        Path note = defaultRoot.resolve("notes.txt");
        Files.writeString(note, "by hand");
        List<byte[]> before = new ArrayList<>();
        for (StoredFile file : all) {
            before.add(Files.readAllBytes(path(file)));
        }

        OrphanUploadReport.Report report = report();
        report();
        report.describe();

        assertThat(report.orphans()).hasSize(3);
        for (int i = 0; i < all.size(); i++) {
            assertThat(Files.exists(path(all.get(i)))).as("still there: " + all.get(i).key()).isTrue();
            assertThat(Files.readAllBytes(path(all.get(i)))).as("unchanged: " + all.get(i).key()).isEqualTo(before.get(i));
        }
        assertThat(Files.exists(note)).isTrue();
        assertThat(count(defaultRoot)).as("no file was added, moved or removed in the default storage").isEqualTo(4);
        assertThat(count(vaultRoot)).isEqualTo(1);
    }

    @Test
    void everyRecordIsStillThereWithTheValueItHad() throws IOException {
        StoredFile held = store("default", "", "png", "held cover");
        store("default", "", "png", "orphan");
        Long id = dossier("a record", held, null);

        report();

        try (EntityManager em = UploadTestPersistence.entityManager()) {
            assertThat(em.find(Dossier.class, id).getCover()).isEqualTo(provider.url(held));
            assertThat(em.createQuery("select count(d) from Dossier d", Long.class).getSingleResult()).isEqualTo(1);
        }
    }

    @Test
    void theReportClassHasNoMethodThatCouldDeleteMoveOrWrite() {
        List<String> names = new ArrayList<>();
        for (Class<?> type : new Class<?>[] {OrphanUploadReport.class, OrphanUploadReport.Report.class, OrphanUploadReport.Orphan.class}) {
            for (Method method : type.getDeclaredMethods()) {
                names.add(method.getName().toLowerCase(Locale.ROOT));
            }
        }

        assertThat(names).noneMatch(name -> name.contains("delete") || name.contains("remove") || name.contains("clean")
                || name.contains("purge") || name.contains("move") || name.contains("write") || name.contains("prune"));
    }

    @Test
    void theTextOfTheReportNamesTheFilesAndHoldsNoRecordValue() throws IOException {
        StoredFile orphan = store("default", "", "png", "nobody has this one");
        dossier("A Very Private Title", store("default", "", "png", "held"), null);

        String text = report().describe();

        assertThat(text).contains("read-only report; nothing was changed").contains(orphan.key()).contains("default");
        assertThat(text).doesNotContain("A Very Private Title");
    }

    private static long count(Path root) throws IOException {
        if (!Files.exists(root)) {
            return 0;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile).count();
        }
    }
}

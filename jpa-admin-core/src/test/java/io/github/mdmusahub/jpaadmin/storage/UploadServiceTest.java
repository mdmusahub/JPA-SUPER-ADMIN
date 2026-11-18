package io.github.mdmusahub.jpaadmin.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.mdmusahub.jpaadmin.storage.UploadService.Refused;
import io.github.mdmusahub.jpaadmin.storage.UploadService.Stored;
import io.github.mdmusahub.jpaadmin.testsupport.UploadTestPersistence;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

/**
 * Every check of the upload sequence, with a real local provider on a temporary folder and real small files: what is accepted,
 * what is refused and why, and above all that a refused file leaves nothing on the disk (UPL-1, UPL-2, UPL-2a, UPL-3, UPL-5).
 */
class UploadServiceTest {

    private static final byte[] KEY = "the-service-test-key-0123456789".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    private static final Instant START = Instant.parse("2026-11-18T10:00:00Z");

    private static final class MovingClock extends Clock {

        private Instant now = START;

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @TempDir
    Path temp;

    private final MovingClock clock = new MovingClock();
    private UploadTicketService tickets;
    private UploadService service;
    private Path defaultRoot;
    private Path vaultRoot;

    @BeforeEach
    void setUp() {
        defaultRoot = temp.resolve("default-root");
        vaultRoot = temp.resolve("vault-root");
        Map<String, StorageSettings> storages = new LinkedHashMap<>();
        storages.put("default", new StorageSettings("default", "local", defaultRoot.toString(), 5L * 1024 * 1024, null));
        storages.put("vault", new StorageSettings("vault", "local", vaultRoot.toString(), 5L * 1024 * 1024, null));
        LocalStorageProvider provider = new LocalStorageProvider(storages, "/admin/files", List.of(), new SecureRandom(), clock);
        tickets = new UploadTicketService(KEY, clock);
        service = new UploadService(UploadTargets.of(UploadTestPersistence.registry(), storages), tickets, provider);
    }

    private String ticket(String field, long max) {
        return tickets.issue("dossier", field, max);
    }

    private UploadService.Upload upload(String field, String ticket, String declared, String clientName, byte[] bytes) {
        return new UploadService.Upload("dossier", field, ticket, declared, clientName, bytes.length,
                () -> new ByteArrayInputStream(bytes));
    }

    private UploadService.Outcome send(String field, String declared, String clientName, byte[] bytes) {
        return service.handle(upload(field, ticket(field, 5L * 1024 * 1024), declared, clientName, bytes));
    }

    private long filesStored() throws IOException {
        long count = 0;
        for (Path root : List.of(defaultRoot, vaultRoot)) {
            if (Files.exists(root)) {
                try (Stream<Path> walk = Files.walk(root)) {
                    count += walk.filter(Files::isRegularFile).count();
                }
            }
        }
        return count;
    }

    private static Refused refused(UploadService.Outcome outcome) {
        assertThat(outcome).isInstanceOf(Refused.class);
        return (Refused) outcome;
    }

    // ---- accepted -----------------------------------------------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("realFiles")
    void aRealFileOfEveryKnownTypeIsStoredAndComesBackByItsUrl(String mime, String extension, byte[] bytes) throws IOException {
        UploadService.Outcome outcome = send("cover", mime, "holiday photo.bin", bytes);

        assertThat(outcome).isInstanceOf(Stored.class);
        Stored stored = (Stored) outcome;
        assertThat(stored.entity()).isEqualTo("dossier");
        assertThat(stored.field()).isEqualTo("cover");
        assertThat(stored.size()).isEqualTo(bytes.length);
        assertThat(stored.name()).isEqualTo("holiday photo.bin");
        assertThat(stored.url()).matches("/admin/files/default/\\d{4}/\\d{2}/[0-9a-f]{32}\\." + extension);
        Path file = defaultRoot.resolve(stored.url().substring("/admin/files/default/".length()));
        assertThat(file).exists().hasBinaryContent(bytes);
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> realFiles() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of("image/png", "png", UploadTestFiles.png()),
                org.junit.jupiter.params.provider.Arguments.of("image/jpeg", "jpg", UploadTestFiles.jpeg()),
                org.junit.jupiter.params.provider.Arguments.of("image/gif", "gif", UploadTestFiles.gif()),
                org.junit.jupiter.params.provider.Arguments.of("image/webp", "webp", UploadTestFiles.webp()),
                org.junit.jupiter.params.provider.Arguments.of("application/pdf", "pdf", UploadTestFiles.pdf()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "application/octet-stream", "Application/Octet-Stream", "image/png", "IMAGE/PNG",
            "image/png; charset=binary", " image/png "})
    void aMissingGenericOrMatchingDeclaredTypeIsFine(String declared) {
        assertThat(send("cover", declared.isEmpty() ? null : declared, "a.png", UploadTestFiles.png())).isInstanceOf(Stored.class);
    }

    @Test
    void theFolderOfTheFieldIsPartOfTheStoredAddress() {
        Stored stored = (Stored) send("scan", "image/png", "a.png", UploadTestFiles.png());

        assertThat(stored.url()).startsWith("/admin/files/default/scans/");
    }

    @Test
    void aFileExactlyAtTheLimitIsAccepted() {
        assertThat(service.handle(upload("cover", ticket("cover", 100), "image/png", "a.png", UploadTestFiles.pngOfSize(100))))
                .isInstanceOf(Stored.class);
    }

    // ---- the client's name is only for display ------------------------------------------------------------------------------------

    @Test
    void theClientsNameNeverBecomesAPathAndTheStoredNameIsRandom() throws IOException {
        Stored stored = (Stored) send("cover", "image/png", "../../evil\u0000/..\\win.png", UploadTestFiles.png());

        assertThat(stored.name()).isEqualTo("win.png");
        assertThat(stored.url()).doesNotContain("evil", "win", "..", "%");
        try (Stream<Path> walk = Files.walk(temp)) {
            assertThat(walk.map(path -> path.getFileName().toString())).noneMatch(name -> name.contains("evil") || name.contains("win"));
        }
    }

    @Test
    void aClientNameIsNeverLoggedNotEvenWhenTheUploadIsRefused() {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logged = new ListAppender<>();
        logged.start();
        root.addAppender(logged);
        Level before = root.getLevel();
        root.setLevel(Level.TRACE);
        try {
            send("cover", "image/png", "SECRET-CLIENT-NAME.png", UploadTestFiles.png());
            send("cover", "image/png", "SECRET-CLIENT-NAME.png", UploadTestFiles.executable());
            send("cover", "image/png", "SECRET-CLIENT-NAME.png", UploadTestFiles.polyglotPng());
            send("cover", "image/png", "SECRET-CLIENT-NAME.png", new byte[0]);

            assertThat(logged.list).noneMatch(event -> event.getFormattedMessage().contains("SECRET-CLIENT-NAME"));
        } finally {
            root.detachAppender(logged);
            root.setLevel(before);
        }
    }

    // ---- disguised and dangerous files: refused, nothing stored -----------------------------------------------------------------------

    @Test
    void anExecutableNamedPngIsRefusedByItsContent() throws IOException {
        Refused refused = refused(send("cover", "image/png", "photo.png", UploadTestFiles.executable()));

        assertThat(refused.status()).isEqualTo(415);
        assertThat(refused.message()).contains("not one of the types").doesNotContain("photo.png");
        assertThat(filesStored()).isZero();
    }

    @Test
    void htmlNamedJpgIsRefusedByItsContent() throws IOException {
        Refused refused = refused(send("cover", "image/jpeg", "photo.jpg", UploadTestFiles.html()));

        assertThat(refused.status()).isEqualTo(415);
        assertThat(filesStored()).isZero();
    }

    @Test
    void htmlWithoutADeclaredTypeIsRefusedToo() throws IOException {
        assertThat(refused(send("cover", null, null, UploadTestFiles.html())).status()).isEqualTo(415);
        assertThat(filesStored()).isZero();
    }

    @Test
    void aSvgIsRefusedWhateverItIsCalled() throws IOException {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\"/>".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(refused(send("cover", "image/svg+xml", "logo.svg", svg)).status()).isEqualTo(415);
        assertThat(refused(send("cover", "image/png", "logo.png", svg)).status()).isEqualTo(415);
        assertThat(filesStored()).isZero();
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> polyglots() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of("png with a script after IEND", UploadTestFiles.polyglotPng()),
                org.junit.jupiter.params.provider.Arguments.of("png with a SCRIPT in capitals",
                        UploadTestFiles.append(UploadTestFiles.png(), "<SCRIPT SRC=//evil></SCRIPT>")),
                org.junit.jupiter.params.provider.Arguments.of("png with an html page",
                        UploadTestFiles.append(UploadTestFiles.png(), "<HtMl><body onload=x>")),
                org.junit.jupiter.params.provider.Arguments.of("jpeg with php",
                        UploadTestFiles.append(UploadTestFiles.jpeg(), "<?php system($_GET['c']); ?>")),
                org.junit.jupiter.params.provider.Arguments.of("gif with an svg",
                        UploadTestFiles.append(UploadTestFiles.gif(), "<svg onload=alert(1)>")),
                org.junit.jupiter.params.provider.Arguments.of("png with an iframe",
                        UploadTestFiles.append(UploadTestFiles.png(), "<iframe src=//evil>")),
                org.junit.jupiter.params.provider.Arguments.of("png with a doctype",
                        UploadTestFiles.append(UploadTestFiles.png(), "<!DOCTYPE html>")),
                org.junit.jupiter.params.provider.Arguments.of("gif header then a script (a classic GIF/JS polyglot)",
                        "GIF89a/*<script>alert(1)</script>*/".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1)),
                org.junit.jupiter.params.provider.Arguments.of("pdf with a script tag",
                        UploadTestFiles.append(UploadTestFiles.pdf(), "<script>x</script>")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("polyglots")
    void aPolyglotIsRefusedAndNothingIsStored(String what, byte[] bytes) throws IOException {
        String declared = what.startsWith("jpeg") ? "image/jpeg" : what.startsWith("gif") ? "image/gif"
                : what.startsWith("pdf") ? "application/pdf" : "image/png";

        Refused refused = refused(send("cover", declared, "x.bin", bytes));

        assertThat(refused.status()).isEqualTo(415);
        assertThat(refused.message()).contains("hidden inside");
        assertThat(filesStored()).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 5, 6, 12, 13, 14, 15, 16, 65520, 65530, 65535, 65536, 65537, 131066, 131072})
    void aMarkerIsFoundWhereverItSitsEvenAcrossTheBoundaryOfTwoReads(int offset) throws IOException {
        byte[] file = new byte[200_000];
        byte[] png = UploadTestFiles.png();
        System.arraycopy(png, 0, file, 0, png.length);
        byte[] marker = "<script>".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        int at = Math.max(offset, png.length);
        System.arraycopy(marker, 0, file, at, marker.length);

        assertThat(refused(send("cover", "image/png", "x.png", file)).status()).isEqualTo(415);
        assertThat(filesStored()).isZero();
    }

    @Test
    void aLessThanSignThatIsNotAMarkerIsFine() {
        byte[] file = UploadTestFiles.append(UploadTestFiles.png(), "<b>bold</b> a < b << c <scrip <htm <?ph <sv");

        assertThat(send("cover", "image/png", "x.png", file)).isInstanceOf(Stored.class);
    }

    // ---- empty -----------------------------------------------------------------------------------------------------------------------

    @Test
    void anEmptyFileIsRefused() throws IOException {
        Refused refused = refused(send("cover", "image/png", "empty.png", new byte[0]));

        assertThat(refused.status()).isEqualTo(400);
        assertThat(refused.message()).contains("empty");
        assertThat(filesStored()).isZero();
    }

    @Test
    void noFileAtAllIsRefused() throws IOException {
        Refused refused = refused(service.handle(new UploadService.Upload("dossier", "cover", ticket("cover", 1000), null, null,
                0, null)));

        assertThat(refused.status()).isEqualTo(400);
        assertThat(refused.message()).contains("No file");
        assertThat(filesStored()).isZero();
    }

    // ---- sizes ------------------------------------------------------------------------------------------------------------------------

    @Test
    void aFileOverTheFieldLimitIsRefusedBeforeItIsRead() throws IOException {
        AtomicInteger opened = new AtomicInteger();
        UploadService.Upload big = new UploadService.Upload("dossier", "contract", ticket("contract", 50L * 1024 * 1024),
                "application/pdf", "big.pdf", 2L * 1024 * 1024 + 1, () -> {
                    opened.incrementAndGet();
                    return new ByteArrayInputStream(new byte[0]);
                });

        Refused refused = refused(service.handle(big));

        assertThat(refused.status()).isEqualTo(413);
        assertThat(refused.message()).contains("2 MB");
        assertThat(opened).hasValue(0);
        assertThat(filesStored()).isZero();
    }

    @Test
    void theFieldsOwnLimitIsBelowTheStoragesAndTheStoredBytesAreCountedNotTrusted() throws IOException {
        // The claim says 10 bytes; the file really is 3 MB. The contract field allows 2 MB.
        byte[] bigPdf = new byte[3 * 1024 * 1024];
        byte[] head = UploadTestFiles.pdf();
        System.arraycopy(head, 0, bigPdf, 0, head.length);
        UploadService.Upload liar = new UploadService.Upload("dossier", "contract", ticket("contract", 50L * 1024 * 1024),
                "application/pdf", "big.pdf", 10, () -> new ByteArrayInputStream(bigPdf));

        Refused refused = refused(service.handle(liar));

        assertThat(refused.status()).isEqualTo(413);
        assertThat(filesStored()).isZero();
    }

    @Test
    void theStoragesGlobalCeilingIsNeverRaisedByATicket() throws IOException {
        // The ticket says 50 MB; the storage says 5 MB.
        byte[] six = UploadTestFiles.pngOfSize(6 * 1024 * 1024);

        Refused refused = refused(service.handle(upload("cover", ticket("cover", 50L * 1024 * 1024), "image/png", "a.png", six)));

        assertThat(refused.status()).isEqualTo(413);
        assertThat(refused.message()).contains("5 MB");
        assertThat(filesStored()).isZero();
    }

    @Test
    void aTicketCanLowerTheLimitBelowTheFields() throws IOException {
        Refused refused = refused(service.handle(upload("cover", ticket("cover", 1000), "image/png", "a.png",
                UploadTestFiles.pngOfSize(1001))));

        assertThat(refused.status()).isEqualTo(413);
        assertThat(filesStored()).isZero();
    }

    // ---- the declared type ------------------------------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"image/jpeg", "text/html", "application/pdf", "image/svg+xml", "text/plain", "image/jpg"})
    void aDeclaredTypeThatContradictsTheContentIsRefused(String declared) throws IOException {
        Refused refused = refused(send("cover", declared, "a.png", UploadTestFiles.png()));

        assertThat(refused.status()).isEqualTo(415);
        assertThat(refused.message()).contains("does not match");
        assertThat(filesStored()).isZero();
    }

    @Test
    void aTypeTheFieldDoesNotAcceptIsRefusedAndTheMessageNamesWhatItDoes() throws IOException {
        Refused refused = refused(send("contract", "image/jpeg", "a.jpg", UploadTestFiles.jpeg()));

        assertThat(refused.status()).isEqualTo(415);
        assertThat(refused.message()).contains("JPEG image", "PDF document", "PNG image");
        assertThat(filesStored()).isZero();
    }

    @Test
    void theContractFieldGoesToItsOwnStorageAndFolder() {
        Stored stored = (Stored) send("contract", "application/pdf", "c.pdf", UploadTestFiles.pdf());

        assertThat(stored.url()).startsWith("/admin/files/vault/docs/scans/");
        assertThat(vaultRoot.resolve("docs/scans")).isDirectory();
    }

    // ---- tickets -----------------------------------------------------------------------------------------------------------------------

    @Test
    void noTicketAndABadTicketAreForbiddenAndTheBodyIsNeverOpened() throws IOException {
        AtomicInteger opened = new AtomicInteger();
        UploadService.ContentSource source = () -> {
            opened.incrementAndGet();
            return new ByteArrayInputStream(UploadTestFiles.png());
        };

        for (String ticket : new String[] {null, "", "garbage", "v1.a.b"}) {
            Refused refused = refused(service.handle(new UploadService.Upload("dossier", "cover", ticket, "image/png", "a.png", 10,
                    source)));
            assertThat(refused.status()).isEqualTo(403);
            assertThat(refused.message()).contains("no longer valid");
        }
        assertThat(opened).hasValue(0);
        assertThat(filesStored()).isZero();
    }

    @Test
    void aTicketForAnotherFieldOrEntityIsForbidden() throws IOException {
        String forScan = ticket("scan", 1000);

        assertThat(refused(service.handle(upload("cover", forScan, "image/png", "a.png", UploadTestFiles.png())))
                .status()).isEqualTo(403);
        UploadService.Upload otherEntity = new UploadService.Upload("gadget", "cover", ticket("cover", 1000), "image/png", "a.png",
                10, () -> new ByteArrayInputStream(UploadTestFiles.png()));
        assertThat(refused(service.handle(otherEntity)).status()).isEqualTo(403);
        assertThat(filesStored()).isZero();
    }

    @Test
    void aTicketForAFieldThatIsNotAnUploadFieldGetsTheSameAnswerAsABadTicket() {
        String ticket = tickets.issue("dossier", "title", 1000);

        Refused refused = refused(service.handle(new UploadService.Upload("dossier", "title", ticket, "image/png", "a.png", 10,
                () -> new ByteArrayInputStream(UploadTestFiles.png()))));

        assertThat(refused.status()).isEqualTo(403);
        assertThat(refused.message()).isEqualTo(UploadMessages.ticketInvalid());
    }

    @Test
    void anExpiredTicketIsForbiddenWithAMessageThatSaysToOpenTheFormAgain() throws IOException {
        String ticket = ticket("cover", 1000);
        clock.advance(Duration.ofMinutes(11));

        Refused refused = refused(service.handle(upload("cover", ticket, "image/png", "a.png", UploadTestFiles.png())));

        assertThat(refused.status()).isEqualTo(403);
        assertThat(refused.message()).contains("too long", "Open the form page again");
        assertThat(filesStored()).isZero();
    }

    @Test
    void aTicketFromAnotherKeyIsForbidden() throws IOException {
        UploadTicketService stranger = UploadTicketService.withRandomKey(clock);

        Refused refused = refused(service.handle(upload("cover", stranger.issue("dossier", "cover", 1000), "image/png", "a.png",
                UploadTestFiles.png())));

        assertThat(refused.status()).isEqualTo(403);
        assertThat(filesStored()).isZero();
    }

    // ---- the provider fails ----------------------------------------------------------------------------------------------------------------

    @Test
    void aProviderThatFailsGivesAPlainMessageWithoutTheCause() {
        StorageProvider failing = new StorageProvider() {
            @Override
            public boolean supports(String storage) {
                return true;
            }

            @Override
            public StoredFile store(String storage, String folder, String extension, InputStream content, long maxBytes)
                    throws IOException {
                throw new IOException("disk /secret/place is full");
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
                return "/x";
            }
        };
        Map<String, StorageSettings> storages = Map.of("default", StorageSettings.defaults(), "vault",
                new StorageSettings("vault", "local", "./v", 1024 * 1024, null));
        UploadService broken = new UploadService(UploadTargets.of(UploadTestPersistence.registry(), storages), tickets, failing);

        UploadService.Outcome outcome = broken.handle(upload("cover", ticket("cover", 1000), "image/png", "a.png",
                UploadTestFiles.png()));

        Refused refused = refused(outcome);
        assertThat(refused.status()).isEqualTo(500);
        assertThat(refused.message()).doesNotContain("secret", "disk", "full").contains("could not be stored");
    }

    // ---- helpers under test ------------------------------------------------------------------------------------------------------------------

    @Test
    void contradictionIsDecidedOnTheTypeAloneParametersAndLetterCaseIgnored() {
        assertThat(UploadService.contradicts(null, "image/png")).isFalse();
        assertThat(UploadService.contradicts("  ", "image/png")).isFalse();
        assertThat(UploadService.contradicts("application/octet-stream", "image/png")).isFalse();
        assertThat(UploadService.contradicts("Image/PNG; foo=bar", "image/png")).isFalse();
        assertThat(UploadService.contradicts("image/jpeg", "image/png")).isTrue();
        assertThat(UploadService.contradicts(";charset=x", "image/png")).isFalse();
    }
}

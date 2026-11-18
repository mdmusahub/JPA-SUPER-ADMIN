package io.github.mdmusahub.jpaadmin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.storage.LocalStorageProvider;
import io.github.mdmusahub.jpaadmin.storage.StorageSettings;
import io.github.mdmusahub.jpaadmin.storage.StorageUploadFiles;
import io.github.mdmusahub.jpaadmin.storage.StoredFile;
import io.github.mdmusahub.jpaadmin.storage.UploadTargets;
import io.github.mdmusahub.jpaadmin.storage.UploadTestFiles;
import io.github.mdmusahub.jpaadmin.storage.UploadTicketService;
import io.github.mdmusahub.jpaadmin.testsupport.TransactionalRecordWriteService;
import io.github.mdmusahub.jpaadmin.testsupport.UploadTestPersistence;
import io.github.mdmusahub.jpaadmin.testsupport.uploadmodel.Dossier;
import io.github.mdmusahub.jpaadmin.web.view.Cell;
import io.github.mdmusahub.jpaadmin.web.view.FormView;
import io.github.mdmusahub.jpaadmin.web.view.FormView.Field.InputType;
import jakarta.persistence.EntityManager;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

/**
 * Phase 9b on the service side, against a real database and a real local provider: an upload field is an upload input with a
 * fresh ticket, the ticket is for that field only and is never logged, and a posted address is stored only when the field's own
 * storage made it (blank keeps, remove clears, replace swaps, anything else is refused for that field alone, and no other
 * name is ever read).
 */
class UploadFormTest {

    private static final byte[] KEY = "the-form-test-signing-key-0123456789".getBytes(java.nio.charset.StandardCharsets.UTF_8);
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

    /** Counts the tickets that are issued, so "one per render, only for upload fields" is a fact and not a guess. */
    private static final class Counting implements UploadFiles {

        private final UploadFiles delegate;
        final AtomicInteger tickets = new AtomicInteger();

        Counting(UploadFiles delegate) {
            this.delegate = delegate;
        }

        @Override
        public Optional<Settings> settings(String entityUrlName, String field) {
            return delegate.settings(entityUrlName, field);
        }

        @Override
        public String ticket(String entityUrlName, String field, long maxBytes) {
            tickets.incrementAndGet();
            return delegate.ticket(entityUrlName, field, maxBytes);
        }

        @Override
        public boolean accepts(String entityUrlName, String field, String url) {
            return delegate.accepts(entityUrlName, field, url);
        }

        @Override
        public Optional<Cell> cell(String url, String label) {
            return delegate.cell(url, label);
        }
    }

    @TempDir
    Path temp;

    private final MovingClock clock = new MovingClock();
    private UploadTicketService tickets;
    private LocalStorageProvider provider;
    private Counting uploads;
    private AdminEntityRegistry registry;
    private AdminEntity dossier;
    private EntityManager entityManager;
    private FormQueryService forms;
    private RecordWriteService writes;

    @BeforeEach
    void wire() {
        Map<String, StorageSettings> storages = new LinkedHashMap<>();
        storages.put("default", new StorageSettings("default", "local", temp.resolve("default").toString(), 5L * 1024 * 1024, null));
        storages.put("vault", new StorageSettings("vault", "local", temp.resolve("vault").toString(), 5L * 1024 * 1024, null));
        provider = new LocalStorageProvider(storages, "/admin/files", List.of(), new SecureRandom(), clock);
        tickets = new UploadTicketService(KEY, clock);
        registry = UploadTestPersistence.registry();
        uploads = new Counting(new StorageUploadFiles(UploadTargets.of(registry, storages), tickets, provider));
        dossier = registry.find("dossier").orElseThrow();
        entityManager = UploadTestPersistence.entityManager();
        forms = FormQueryService.over(entityManager, registry, uploads);
        writes = new TransactionalRecordWriteService(entityManager, registry, uploads);
    }

    @AfterEach
    void close() {
        entityManager.close();
    }

    private String address(String storage, String folder, String extension, byte[] bytes) throws IOException {
        StoredFile stored = provider.store(storage, folder, extension, new ByteArrayInputStream(bytes), 1024 * 1024);
        return provider.url(stored);
    }

    private static FormView.Field field(FormPage page, String key) {
        return page.fields().stream().filter(candidate -> candidate.key().equals(key)).findFirst().orElseThrow();
    }

    private Map<String, String> post(String... pairs) {
        Map<String, String> posted = new HashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            posted.put(pairs[i], pairs[i + 1]);
        }
        return posted;
    }

    private Long create(Map<String, String> posted) {
        RecordWriteService.Saved saved = writes.create(dossier, posted::get);
        return Long.valueOf(saved.id());
    }

    private Dossier reload(Long id) {
        entityManager.clear();
        return entityManager.find(Dossier.class, id);
    }

    // ---- the form: an upload input with a ticket of its own -------------------------------------------------------------------------

    @Test
    void anUploadFieldIsAnUploadInputWithATicketForThatFieldAndNoOtherFieldHasOne() {
        FormPage page = forms.blank(dossier);

        assertThat(page.fields()).extracting(FormView.Field::key).containsExactlyInAnyOrder("title", "cover", "contract", "scan");
        for (String key : List.of("cover", "contract", "scan")) {
            FormView.Field upload = field(page, key);
            assertThat(upload.inputType()).isEqualTo(InputType.UPLOAD);
            assertThat(upload.uploadTicket()).isNotBlank();
            assertThat(tickets.verify(upload.uploadTicket(), "dossier", key).valid()).as(key).isTrue();
        }
        assertThat(field(page, "title").inputType()).isEqualTo(InputType.TEXT);
        assertThat(field(page, "title").uploadTicket()).isNull();
        for (FormView.Field other : page.fields()) {
            if (other.inputType() != InputType.UPLOAD) {
                assertThat(other.uploadTicket()).as(other.key()).isNull();
            }
        }
    }

    @Test
    void aTicketIsOnlyGoodForTheFieldItWasIssuedFor() {
        FormPage page = forms.blank(dossier);

        assertThat(tickets.verify(field(page, "cover").uploadTicket(), "dossier", "scan").valid()).isFalse();
        assertThat(tickets.verify(field(page, "cover").uploadTicket(), "dossier", "title").valid()).isFalse();
        assertThat(tickets.verify(field(page, "cover").uploadTicket(), "other", "cover").valid()).isFalse();
    }

    @Test
    void everyRenderIssuesNewTicketsOneForEachUploadFieldAndNothingElse() {
        forms.blank(dossier);
        assertThat(uploads.tickets).hasValue(3);
        String first = field(forms.blank(dossier), "cover").uploadTicket();
        assertThat(uploads.tickets).hasValue(6);

        clock.advance(Duration.ofSeconds(30));
        FormPage later = forms.blank(dossier);

        assertThat(uploads.tickets).hasValue(9);
        assertThat(field(later, "cover").uploadTicket()).isNotEqualTo(first);
        // The new ticket lives its own ten minutes from its own render; the old one runs out first.
        clock.advance(Duration.ofMinutes(9).plusSeconds(40));
        assertThat(tickets.verify(first, "dossier", "cover").valid()).isFalse();
        assertThat(tickets.verify(field(later, "cover").uploadTicket(), "dossier", "cover").valid()).isTrue();
    }

    @Test
    void theEditAndCopyFormsIssueTicketsToo() {
        Long id = create(post("title", "T"));

        FormPage edit = forms.record(dossier, String.valueOf(id)).orElseThrow();
        FormPage copy = forms.copyOf(dossier, new CopySeed(String.valueOf(id), List.of(new CopySeed.Value("title", "T"))));

        assertThat(field(edit, "scan").uploadTicket()).isNotBlank();
        assertThat(field(copy, "scan").uploadTicket()).isNotBlank();
        assertThat(field(copy, "title").uploadTicket()).isNull();
    }

    @Test
    void theFormTellsTheUploaderItsAdvisoryLimitsAndAHumanSentence() {
        FormPage page = forms.blank(dossier);

        FormView.Field contract = field(page, "contract");
        assertThat(contract.options()).extracting(FormView.Option::value)
                .containsExactly(FormQueryService.UPLOAD_ACCEPT, FormQueryService.UPLOAD_MAX_BYTES);
        assertThat(contract.options().get(0).label()).isEqualTo("application/pdf,image/png");
        assertThat(contract.options().get(1).label()).isEqualTo(String.valueOf(2L * 1024 * 1024));
        assertThat(contract.help()).isEqualTo("Accepts PDF document, PNG image, up to 2 MB.");
        assertThat(field(page, "cover").help()).contains("PNG image", "JPEG image", "GIF image", "WebP image", "PDF document")
                .endsWith("up to 5 MB.");
        assertThat(field(page, "title").options()).isEmpty();
    }

    @Test
    void aTicketIsNeverLoggedWhileFormsAreRenderedAndSaved() throws IOException {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logged = new ListAppender<>();
        logged.start();
        root.addAppender(logged);
        Level before = root.getLevel();
        root.setLevel(Level.TRACE);
        try {
            forms.blank(dossier);
            create(post("title", "T", "cover", address("default", "", "png", UploadTestFiles.png())));
            assertThatThrownBy(() -> writes.create(dossier, post("title", "T2", "cover", "https://evil.example/x.png")::get))
                    .isInstanceOf(InvalidRecordException.class);
        } finally {
            root.detachAppender(logged);
            root.setLevel(before);
        }
        String ticket = field(forms.blank(dossier), "cover").uploadTicket();
        assertThat(logged.list).noneMatch(event -> event.getFormattedMessage().contains("v1."))
                .noneMatch(event -> event.getFormattedMessage().contains(ticket));
    }

    // ---- posted addresses: only the field's own storage's ---------------------------------------------------------------------------

    @Test
    void anAddressOfTheFieldsOwnStorageIsStoredOnCreate() throws IOException {
        String cover = address("default", "", "png", UploadTestFiles.png());
        String scan = address("default", "scans", "jpg", UploadTestFiles.jpeg());
        String contract = address("vault", "docs/scans", "pdf", UploadTestFiles.pdf());

        Dossier saved = reload(create(post("title", "T", "cover", cover, "scan", scan, "contract", contract)));

        assertThat(saved.getCover()).isEqualTo(cover);
        assertThat(saved.getScan()).isEqualTo(scan);
        assertThat(saved.getContract()).isEqualTo(contract);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://evil.example/admin/files/default/2026/11/0123456789abcdef0123456789abcdef.png",
            "//evil.example/x.png", "javascript:alert(1)", "data:image/png;base64,AAAA", "/etc/passwd", "../../x.png",
            "/admin/files/default/../../x.png", "/admin/files/default/2026/11/0123456789abcdef0123456789abcdef.png",
            "just some text", "<script>alert(1)</script>", "/admin/files/nothing/2026/11/0123456789abcdef0123456789abcdef.png"})
    void aValueThatTheStorageDidNotMakeIsRefusedForThatFieldOnly(String url) {
        assertThatThrownBy(() -> writes.create(dossier, post("title", "Kept?", "cover", url)::get))
                .isInstanceOfSatisfying(InvalidRecordException.class, refusal -> {
                    assertThat(refusal.fieldErrors()).containsOnlyKeys("cover");
                    assertThat(refusal.fieldErrors().get("cover")).isEqualTo("The file is not one this console uploaded. Upload it again.")
                            .doesNotContain(url);
                });

        assertThat(entityManager.createQuery("select count(d) from Dossier d where d.title = 'Kept?'", Long.class)
                .getSingleResult()).isZero();
    }

    @Test
    void anAddressOfAFileThatNeverExistedOrWasMovedAwayIsRefused() {
        String missing = "/admin/files/default/2026/11/0123456789abcdef0123456789abcdef.png";

        assertThatThrownBy(() -> writes.create(dossier, post("title", "T", "cover", missing)::get))
                .isInstanceOf(InvalidRecordException.class);
    }

    @Test
    void anAddressOfAnotherStorageOrAnotherFieldsFolderOrATypeTheFieldDoesNotTakeIsRefused() throws IOException {
        String vaultFile = address("vault", "", "png", UploadTestFiles.png());
        String scansFolder = address("default", "scans", "png", UploadTestFiles.png());
        String rootFolder = address("default", "", "png", UploadTestFiles.png());
        String jpegForContract = address("vault", "docs/scans", "jpg", UploadTestFiles.jpeg());

        // cover wants the default storage's root folder: the vault's file and the scans folder's file are not its files.
        assertThat(uploads.accepts("dossier", "cover", vaultFile)).isFalse();
        assertThat(uploads.accepts("dossier", "cover", scansFolder)).isFalse();
        assertThat(uploads.accepts("dossier", "cover", rootFolder)).isTrue();
        // scan wants default/scans: the root folder's file is not its file.
        assertThat(uploads.accepts("dossier", "scan", rootFolder)).isFalse();
        assertThat(uploads.accepts("dossier", "scan", scansFolder)).isTrue();
        // contract takes PDF and PNG only.
        assertThat(uploads.accepts("dossier", "contract", jpegForContract)).isFalse();
        assertThatThrownBy(() -> writes.create(dossier, post("title", "T", "contract", jpegForContract)::get))
                .isInstanceOf(InvalidRecordException.class);
    }

    @Test
    void aFieldThatIsNotAnUploadFieldHasNoAcceptedAddress() throws IOException {
        String ok = address("default", "", "png", UploadTestFiles.png());

        assertThat(uploads.accepts("dossier", "title", ok)).isFalse();
        assertThat(uploads.accepts("dossier", "wrongType", ok)).isFalse();
        assertThat(uploads.accepts("nothing", "cover", ok)).isFalse();
        assertThat(uploads.accepts("dossier", "cover", null)).isFalse();
    }

    // ---- update: blank keeps, remove clears, replace swaps ------------------------------------------------------------------------------

    @Test
    void aBlankValueOnEditKeepsTheStoredAddressAndNothingIsDeleted() throws IOException {
        String cover = address("default", "", "png", UploadTestFiles.png());
        Long id = create(post("title", "T", "cover", cover));

        writes.update(dossier, String.valueOf(id), null, post("title", "T2", "cover", "")::get);
        writes.update(dossier, String.valueOf(id), null, post("title", "T3")::get);

        Dossier saved = reload(id);
        assertThat(saved.getCover()).isEqualTo(cover);
        assertThat(saved.getTitle()).isEqualTo("T3");
        assertThat(provider.owns(cover)).as("the file is still stored").isTrue();
    }

    @Test
    void thePostedStoredAddressAgainIsNoChange() throws IOException {
        String cover = address("default", "", "png", UploadTestFiles.png());
        Long id = create(post("title", "T", "cover", cover));

        writes.update(dossier, String.valueOf(id), null, post("title", "T", "cover", cover)::get);

        assertThat(reload(id).getCover()).isEqualTo(cover);
    }

    @Test
    void theRemoveMarkerClearsTheFieldAndKeepsTheFileOnDisk() throws IOException {
        String cover = address("default", "", "png", UploadTestFiles.png());
        Long id = create(post("title", "T", "cover", cover));

        writes.update(dossier, String.valueOf(id), null, post("title", "T", "cover", cover, "cover-remove", "true")::get);

        assertThat(reload(id).getCover()).isNull();
        assertThat(provider.owns(cover)).as("UPL-9: the old file stays on disk").isTrue();
    }

    @Test
    void aMarkerThatIsNotTrueClearsNothing() throws IOException {
        String cover = address("default", "", "png", UploadTestFiles.png());
        Long id = create(post("title", "T", "cover", cover));

        writes.update(dossier, String.valueOf(id), null, post("title", "T", "cover-remove", "false")::get);
        writes.update(dossier, String.valueOf(id), null, post("title", "T", "cover-remove", "yes")::get);

        assertThat(reload(id).getCover()).isEqualTo(cover);
    }

    @Test
    void aNewAddressReplacesTheStoredOneAndTheOldFileStaysOnDisk() throws IOException {
        String first = address("default", "", "png", UploadTestFiles.png());
        String second = address("default", "", "jpg", UploadTestFiles.jpeg());
        Long id = create(post("title", "T", "cover", first));

        writes.update(dossier, String.valueOf(id), null, post("title", "T", "cover", second)::get);

        assertThat(reload(id).getCover()).isEqualTo(second);
        assertThat(provider.owns(first)).isTrue();
    }

    @Test
    void aRefusedAddressOnEditChangesNothingAtAll() throws IOException {
        String cover = address("default", "", "png", UploadTestFiles.png());
        Long id = create(post("title", "Before", "cover", cover));

        assertThatThrownBy(() -> writes.update(dossier, String.valueOf(id), null,
                post("title", "After", "cover", "https://evil.example/x.png")::get))
                .isInstanceOfSatisfying(InvalidRecordException.class,
                        refusal -> assertThat(refusal.fieldErrors()).containsOnlyKeys("cover"));

        Dossier saved = reload(id);
        assertThat(saved.getTitle()).isEqualTo("Before");
        assertThat(saved.getCover()).isEqualTo(cover);
    }

    @Test
    void theRemoveMarkerOnCreateIsIgnored() {
        Long id = create(post("title", "T", "cover-remove", "true"));

        assertThat(reload(id).getCover()).isNull();
    }

    // ---- mass assignment is still impossible ----------------------------------------------------------------------------------------------

    @Test
    void theSaveAsksOnlyForEditableNamesAndTheRemoveMarkerOfAnUploadField() {
        List<String> asked = new ArrayList<>();

        writes.create(dossier, name -> {
            asked.add(name);
            return name.equals("title") ? "T" : null;
        });

        assertThat(asked).containsExactlyInAnyOrder("title", "cover", "contract", "scan");
        asked.clear();
        Long id = create(post("title", "T"));
        writes.update(dossier, String.valueOf(id), null, name -> {
            asked.add(name);
            return null;
        });

        assertThat(asked).containsExactlyInAnyOrder("title", "cover", "cover-remove", "contract", "contract-remove", "scan",
                "scan-remove");
        assertThat(asked).doesNotContain("id", "wrongType", "class", "wrongType-remove", "title-remove");
    }

    @Test
    void anUpdateForANameThatIsNotEditableIsNeverRead() throws IOException {
        String cover = address("default", "", "png", UploadTestFiles.png());
        Long id = create(post("title", "T"));

        writes.update(dossier, String.valueOf(id), null,
                post("title", "T", "wrongType", "99", "id", "777", "cover.remove", "true", "Cover", cover)::get);

        Dossier saved = reload(id);
        assertThat(saved.getWrongType()).isNull();
        assertThat(saved.getId()).isEqualTo(id);
        assertThat(saved.getCover()).isNull();
    }

    // ---- reading the stored address, and the form shown again ----------------------------------------------------------------------------

    @Test
    void theEditFormReadsTheStoredAddressAsTheFieldsValue() throws IOException {
        String cover = address("default", "", "png", UploadTestFiles.png());
        Long id = create(post("title", "T", "cover", cover));

        FormPage page = forms.record(dossier, String.valueOf(id)).orElseThrow();

        assertThat(field(page, "cover").value()).isEqualTo(cover);
        assertThat(field(page, "scan").value()).isNull();
    }

    @Test
    void aFormShownAgainKeepsAnAcceptedAddressDropsARefusedOneAndKeepsARemoval() throws IOException {
        String cover = address("default", "", "png", UploadTestFiles.png());
        Long id = create(post("title", "T", "cover", cover));
        FormPage page = forms.record(dossier, String.valueOf(id)).orElseThrow();
        String newScan = address("default", "scans", "png", UploadTestFiles.png());

        FormPage again = page.reshown(post("title", "", "scan", newScan, "cover", "https://evil.example/x.png",
                "contract", "javascript:1")::get, Map.of("title", "Enter a value.", "contract", "no", "cover", "no"));

        assertThat(field(again, "scan").value()).isEqualTo(newScan);
        assertThat(field(again, "cover").value()).as("a refused address is not put back; the stored one stays").isEqualTo(cover);
        assertThat(field(again, "contract").value()).as("a refused address is not put back").isNull();
        assertThat(field(again, "contract").error()).isEqualTo("no");
        FormPage removed = page.reshown(post("title", "T", "cover-remove", "true")::get, Map.of());
        assertThat(field(removed, "cover").value()).isNull();
    }

    @Test
    void withoutUploadSupportAnUploadFieldIsStillNotAFormFieldAndAPostedValueIsNeverRead() throws IOException {
        FormQueryService plain = FormQueryService.over(entityManager, registry);
        RecordWriteService plainWrites = new TransactionalRecordWriteService(entityManager, registry);
        String cover = address("default", "", "png", UploadTestFiles.png());

        assertThat(plain.blank(dossier).fields()).extracting(FormView.Field::key).containsExactly("title");
        RecordWriteService.Saved saved = plainWrites.create(dossier, post("title", "T", "cover", cover)::get);

        assertThat(reload(Long.valueOf(saved.id())).getCover()).isNull();
    }

    // ---- a copy never carries a file -----------------------------------------------------------------------------------------------------

    @Test
    void aCopyOfARecordWithAnImageCarriesNoFile() throws IOException {
        String cover = address("default", "", "png", UploadTestFiles.png());
        Long id = create(post("title", "Original", "cover", cover));

        CopySeed seed = DetailQueryService.over(entityManager, registry, uploads).copySeed(dossier, String.valueOf(id)).orElseThrow();
        FormPage copy = forms.copyOf(dossier, seed);

        assertThat(seed.values()).extracting(CopySeed.Value::key).contains("title").doesNotContain("cover", "contract", "scan");
        assertThat(field(copy, "cover").value()).isNull();
        assertThat(field(copy, "title").value()).isEqualTo("Original");
    }

    // ---- the cell of a stored address -----------------------------------------------------------------------------------------------------------

    @Test
    void aStoredImageIsAnImageCellWhoseTextIsTheFieldLabelNeverAFileName() throws IOException {
        String cover = address("default", "", "png", UploadTestFiles.png());

        Cell cell = uploads.cell(cover, "Cover").orElseThrow();

        assertThat(cell.type()).isEqualTo(Cell.Type.IMAGE);
        assertThat(cell.display()).isEqualTo("Cover");
        assertThat(cell.href()).isEqualTo(cover);
    }

    @Test
    void aStoredDocumentIsAFileCellWithTheStoredNameAsHoverText() throws IOException {
        String contract = address("vault", "docs/scans", "pdf", UploadTestFiles.pdf());

        Cell cell = uploads.cell(contract, "Contract").orElseThrow();

        assertThat(cell.type()).isEqualTo(Cell.Type.FILE);
        assertThat(cell.display()).isEqualTo("Contract (PDF)");
        assertThat(cell.href()).isEqualTo(contract);
        assertThat(cell.title()).matches("[0-9a-f]{32}\\.pdf");
    }

    @Test
    void aStoredAddressWhoseFileIsGoneIsAPlainNoteAndNotALinkOrAnImage() throws IOException {
        StoredFile stored = provider.store("default", "", "png", new ByteArrayInputStream(UploadTestFiles.png()), 1000000);
        String address = provider.url(stored);
        provider.delete(stored);

        Cell cell = uploads.cell(address, "Cover").orElseThrow();

        assertThat(cell.type()).isEqualTo(Cell.Type.TEXT);
        assertThat(cell.display()).isEqualTo("Cover: file not found");
        assertThat(cell.href()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://evil.example/x.png", "javascript:alert(1)", "<img src=x onerror=alert(1)>", "plain text",
            "/admin/files/default/../../x.png", "/admin/files/default/2026/11/0123456789abcdef0123456789abcdef.svg"})
    void textThatIsNotAStoredAddressIsNoCellAtAllSoItIsShownAsEscapedText(String text) {
        assertThat(uploads.cell(text, "Cover")).isEmpty();
        assertThat(uploads.cell(null, "Cover")).isEmpty();
    }
}

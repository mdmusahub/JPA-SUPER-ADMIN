package io.github.mdmusahub.jpaadmin.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.service.CountService;
import io.github.mdmusahub.jpaadmin.service.DetailQueryService;
import io.github.mdmusahub.jpaadmin.service.FormQueryService;
import io.github.mdmusahub.jpaadmin.storage.LocalStorageProvider;
import io.github.mdmusahub.jpaadmin.storage.StorageSettings;
import io.github.mdmusahub.jpaadmin.storage.StorageUploadFiles;
import io.github.mdmusahub.jpaadmin.storage.UploadService;
import io.github.mdmusahub.jpaadmin.storage.UploadTargets;
import io.github.mdmusahub.jpaadmin.storage.UploadTestFiles;
import io.github.mdmusahub.jpaadmin.storage.UploadTicketService;
import io.github.mdmusahub.jpaadmin.testsupport.TransactionalRecordWriteService;
import io.github.mdmusahub.jpaadmin.testsupport.UploadTestPersistence;
import io.github.mdmusahub.jpaadmin.testsupport.uploadmodel.Dossier;
import io.github.mdmusahub.jpaadmin.web.view.FormView;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;

/**
 * The no-script path end to end inside the console, without a servlet container (option A, D-95): a plain form posts a real file
 * to the upload endpoint, the endpoint leaves the result in the visitor's session, and the form page the visitor is sent back to
 * shows the address as the upload field's value, once, only for the same form, and only if the storage still confirms it.
 */
class UploadFormPagesTest {

    private record Page(String template, Locale locale) implements View {
        @Override
        public void render(Map<String, ?> model, HttpServletRequest request, HttpServletResponse response) {
            throw new UnsupportedOperationException("the test double renders nothing");
        }
    }

    private static final BasePath BASE = BasePath.of("/admin");
    private static final CsrfTokens TOKENS = request -> new PageChrome.Csrf("_csrf", "X-CSRF-TOKEN", "token-123");

    @TempDir
    Path temp;

    private EntityManager entityManager;
    private FormController forms;
    private UploadController uploadEndpoint;
    private LocalStorageProvider provider;
    private UploadTicketService tickets;
    private Long id;

    @BeforeEach
    void wire() {
        Map<String, StorageSettings> storages = new LinkedHashMap<>();
        storages.put("default", new StorageSettings("default", "local", temp.resolve("default").toString(), 5L * 1024 * 1024, null));
        storages.put("vault", new StorageSettings("vault", "local", temp.resolve("vault").toString(), 5L * 1024 * 1024, null));
        provider = new LocalStorageProvider(storages, "/admin/files", List.of(), new SecureRandom(), Clock.systemUTC());
        tickets = UploadTicketService.withRandomKey(Clock.systemUTC());
        AdminEntityRegistry registry = UploadTestPersistence.registry();
        StorageUploadFiles uploads = new StorageUploadFiles(UploadTargets.of(registry, storages), tickets, provider);
        entityManager = UploadTestPersistence.entityManager();
        entityManager.getTransaction().begin();
        Dossier record = new Dossier();
        record.setTitle("Kept");
        entityManager.persist(record);
        entityManager.getTransaction().commit();
        id = record.getId();
        ConsoleNavigation navigation = new ConsoleNavigation(registry, CountService.over(entityManager, Clock.systemUTC()));
        ConsoleChromeFactory chromes = new ConsoleChromeFactory(BASE, new ConsoleSettings("Back office", "#0d9f6e"), TOKENS,
                navigation);
        ConsoleViews views = new ConsoleViews(Page::new);
        forms = new FormController(chromes, views, navigation, FormQueryService.over(entityManager, registry, uploads),
                DetailQueryService.over(entityManager, registry, uploads),
                new TransactionalRecordWriteService(entityManager, registry, uploads), BASE, uploads);
        uploadEndpoint = new UploadController(new UploadService(UploadTargets.of(registry, storages), tickets, provider), chromes,
                views, BASE);
    }

    @AfterEach
    void close() {
        entityManager.close();
    }

    private static MockHttpServletRequest request(MockHttpSession session, boolean json) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/uploads");
        request.setSession(session);
        request.addHeader("Accept", json ? "application/json" : "text/html");
        return request;
    }

    /** What a plain form does: posts a real PNG for one field of the form of {@code record}. */
    private String sendWithThePlainForm(MockHttpSession session, String field, String record, byte[] bytes) throws IOException {
        MockHttpServletRequest request = request(session, false);
        ModelAndView result = uploadEndpoint.upload("dossier", field, tickets.issue("dossier", field, 5L * 1024 * 1024), record,
                new MockMultipartFile("file", "picture.png", null, bytes), request, new MockHttpServletResponse());
        assertThat(result).isNotNull();
        return ((org.springframework.web.servlet.view.RedirectView) result.getView()).getUrl();
    }

    private static FormView formOf(ModelAndView result) {
        return (FormView) result.getModel().get(PageChrome.VIEW_ATTRIBUTE);
    }

    private static FormView.Field field(FormView view, String key) {
        return view.fields().stream().filter(candidate -> candidate.key().equals(key)).findFirst().orElseThrow();
    }

    private MockHttpServletRequest get(MockHttpSession session) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/dossier/new");
        request.setSession(session);
        return request;
    }

    // ---- the result of a plain-form upload is shown on the page the visitor is sent back to -----------------------------------------------

    @Test
    void theCreateFormShowsTheUploadedAddressAsTheFieldsValueOnceAndSavesNothing() throws IOException {
        MockHttpSession session = new MockHttpSession();
        Long before = entityManager.createQuery("select count(d) from Dossier d", Long.class).getSingleResult();

        String back = sendWithThePlainForm(session, "cover", null, UploadTestFiles.png());
        ModelAndView page = forms.newRecord("dossier", null, get(session));
        ModelAndView again = forms.newRecord("dossier", null, get(session));

        assertThat(back).isEqualTo("/admin/dossier/new");
        String address = field(formOf(page), "cover").value();
        assertThat(address).matches("/admin/files/default/\\d{4}/\\d{2}/[0-9a-f]{32}\\.png");
        assertThat(provider.owns(address)).isTrue();
        assertThat(field(formOf(again), "cover").value()).as("shown once").isNull();
        assertThat(entityManager.createQuery("select count(d) from Dossier d", Long.class).getSingleResult()).isEqualTo(before);
        assertThat(session.getAttribute(PendingUploads.ATTRIBUTE)).isNull();
    }

    @Test
    void anEntryIsKeptUntilTheFormItBelongsToIsOpened() throws IOException {
        MockHttpSession session = new MockHttpSession();
        String back = sendWithThePlainForm(session, "cover", String.valueOf(id), UploadTestFiles.png());

        ModelAndView create = forms.newRecord("dossier", null, get(session));
        assertThat(field(formOf(create), "cover").value()).as("a create form is not the form of record " + id).isNull();
        ModelAndView edit = forms.edit("dossier", String.valueOf(id), get(session));

        assertThat(back).isEqualTo("/admin/dossier/" + id + "/edit");
        assertThat(field(formOf(edit), "cover").value()).startsWith("/admin/files/default/");
    }

    @Test
    void eachFieldKeepsItsOwnEntry() throws IOException {
        MockHttpSession session = new MockHttpSession();
        sendWithThePlainForm(session, "cover", null, UploadTestFiles.png());
        sendWithThePlainForm(session, "scan", null, UploadTestFiles.jpeg());

        FormView view = formOf(forms.newRecord("dossier", null, get(session)));

        assertThat(field(view, "cover").value()).endsWith(".png");
        assertThat(field(view, "scan").value()).contains("/scans/").endsWith(".jpg");
        assertThat(field(view, "contract").value()).isNull();
    }

    @Test
    void aNewerFileForTheSameFieldReplacesTheOlderEntry() throws IOException {
        MockHttpSession session = new MockHttpSession();
        sendWithThePlainForm(session, "cover", null, UploadTestFiles.png());
        sendWithThePlainForm(session, "cover", null, UploadTestFiles.jpeg());

        assertThat(field(formOf(forms.newRecord("dossier", null, get(session))), "cover").value()).endsWith(".jpg");
    }

    @Test
    void anEntryTheStorageNoLongerConfirmsIsDroppedNotShown() throws IOException {
        MockHttpSession session = new MockHttpSession();
        sendWithThePlainForm(session, "cover", null, UploadTestFiles.png());
        @SuppressWarnings("unchecked")
        List<String> kept = (List<String>) session.getAttribute(PendingUploads.ATTRIBUTE);
        // Somebody (or a bug) put an address into the session that the storage never made.
        session.setAttribute(PendingUploads.ATTRIBUTE, List.of(kept.get(0).replaceAll("/admin/files/default/.*\u001f", "https://evil.example/x.png\u001f")));

        FormView view = formOf(forms.newRecord("dossier", null, get(session)));

        assertThat(field(view, "cover").value()).isNull();
    }

    @Test
    void aJsonUploadLeavesNothingInTheSession() throws IOException {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletResponse response = new MockHttpServletResponse();

        uploadEndpoint.upload("dossier", "cover", tickets.issue("dossier", "cover", 1000000), null,
                new MockMultipartFile("file", "a.png", "image/png", UploadTestFiles.png()), request(session, true), response);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(session.getAttribute(PendingUploads.ATTRIBUTE)).isNull();
    }

    @Test
    void aRefusedUploadLeavesNothingInTheSession() throws IOException {
        MockHttpSession session = new MockHttpSession();

        uploadEndpoint.upload("dossier", "cover", tickets.issue("dossier", "cover", 1000000), null,
                new MockMultipartFile("file", "a.png", "image/png", UploadTestFiles.executable()), request(session, false),
                new MockHttpServletResponse());

        assertThat(session.getAttribute(PendingUploads.ATTRIBUTE)).isNull();
    }

    @Test
    void aRecordIdThatCannotBeAnAddressFallsBackToTheCreateFormAndItsEntry() throws IOException {
        MockHttpSession session = new MockHttpSession();

        String back = sendWithThePlainForm(session, "cover", "7/../x", UploadTestFiles.png());

        assertThat(back).isEqualTo("/admin/dossier/new");
        assertThat(field(formOf(forms.newRecord("dossier", null, get(session))), "cover").value()).isNotNull();
    }

    @Test
    void thePendingEntryIsPlainTextInAConsoleOnlySessionAttribute() throws IOException {
        MockHttpSession session = new MockHttpSession();

        sendWithThePlainForm(session, "cover", null, UploadTestFiles.png());

        assertThat(PendingUploads.ATTRIBUTE).startsWith("JPA_ADMIN_");
        Object stored = session.getAttribute(PendingUploads.ATTRIBUTE);
        assertThat(stored).isInstanceOf(List.class);
        assertThat(((List<?>) stored)).allMatch(entry -> entry instanceof String);
    }

    @Test
    void atMostTenEntriesAreKept() throws IOException {
        MockHttpSession session = new MockHttpSession();
        for (int i = 0; i < 14; i++) {
            PendingUploads.add(request(session, false), new PendingUploads.Entry("dossier", "cover", String.valueOf(i),
                    "/admin/files/default/x" + i, "n", 1));
        }

        assertThat((List<?>) session.getAttribute(PendingUploads.ATTRIBUTE)).hasSize(10);
        assertThat(PendingUploads.take(request(session, false), "dossier", "cover", "0")).isEmpty();
        assertThat(PendingUploads.take(request(session, false), "dossier", "cover", "13")).isPresent();
    }

    @Test
    void takingFromAVisitorWithoutASessionCreatesNone() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThat(PendingUploads.take(request, "dossier", "cover", "")).isEmpty();
        assertThat(request.getSession(false)).isNull();
    }

    // ---- the saved record: only an address the storage made --------------------------------------------------------------------------------

    @Test
    void savingTheFormWithTheShownAddressStoresItAndAnInventedOneIsRefusedWith422() throws IOException {
        MockHttpSession session = new MockHttpSession();
        sendWithThePlainForm(session, "cover", null, UploadTestFiles.png());
        String address = field(formOf(forms.newRecord("dossier", null, get(session))), "cover").value();

        MockHttpServletRequest good = get(session);
        good.addParameter("title", "With a picture");
        good.addParameter("cover", address);
        MockHttpServletResponse goodResponse = new MockHttpServletResponse();
        forms.create("dossier", good, goodResponse);

        MockHttpServletRequest bad = get(session);
        bad.addParameter("title", "With an invented one");
        bad.addParameter("cover", "https://evil.example/x.png");
        MockHttpServletResponse badResponse = new MockHttpServletResponse();
        ModelAndView shownAgain = forms.create("dossier", bad, badResponse);

        entityManager.clear();
        assertThat(entityManager.createQuery("select d.cover from Dossier d where d.title = 'With a picture'", String.class)
                .getSingleResult()).isEqualTo(address);
        assertThat(entityManager.createQuery("select count(d) from Dossier d where d.title = 'With an invented one'", Long.class)
                .getSingleResult()).isZero();
        assertThat(badResponse.getStatus()).isEqualTo(422);
        FormView view = formOf(shownAgain);
        assertThat(field(view, "cover").error()).isEqualTo("The file is not one this console uploaded. Upload it again.");
        assertThat(field(view, "cover").value()).isNull();
        assertThat(field(view, "title").error()).isNull();
        assertThat(field(view, "cover").uploadTicket()).isNotBlank();
    }
}

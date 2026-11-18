package io.github.mdmusahub.jpaadmin.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.metadata.DiscoverySettings;
import io.github.mdmusahub.jpaadmin.metadata.EntityScanner;
import io.github.mdmusahub.jpaadmin.service.CountService;
import io.github.mdmusahub.jpaadmin.service.DetailQueryService;
import io.github.mdmusahub.jpaadmin.service.FieldMessages;
import io.github.mdmusahub.jpaadmin.service.FormQueryService;
import io.github.mdmusahub.jpaadmin.testsupport.ListTestPersistence;
import io.github.mdmusahub.jpaadmin.testsupport.RecordingStatementInspector;
import io.github.mdmusahub.jpaadmin.testsupport.TransactionalRecordWriteService;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Archive;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Courier;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Depot;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Folio;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Frozen;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Vault;
import io.github.mdmusahub.jpaadmin.web.view.FormView;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.view.RedirectView;

/**
 * The create and edit routes through the real controller, services, factories and a real database: what the visitor gets, the
 * 404 that never differs, the 403 of a flag, the redirect that is built by the console and never taken from the request, the
 * form that is shown again with the typed values (and never a typed secret), and the stale form.
 */
class ConsoleFormPagesTest {

    private record Page(String template, Locale locale) implements View {
        @Override
        public void render(Map<String, ?> model, HttpServletRequest request, HttpServletResponse response) {
            throw new UnsupportedOperationException("the test double renders nothing");
        }
    }

    private static final BasePath BASE = BasePath.of("/admin");
    private static final CsrfTokens TOKENS = request -> new PageChrome.Csrf("_csrf", "X-CSRF-TOKEN", "token-123");
    private static final String STORED_SECRET = "stored:old-secret-value";

    private FormController controller;
    private EntityManager entityManager;
    private Long depot;
    private Long folio;
    private Long vault;

    @BeforeEach
    void wire() {
        ListTestPersistence.empty();
        ListTestPersistence.inTransaction(em -> {
            Depot one = new Depot();
            one.setName("Alpha depot");
            em.persist(one);
            Vault hidden = new Vault();
            hidden.setName("Hidden vault");
            em.persist(hidden);
            Courier runner = new Courier();
            runner.setCallSign("Runner");
            em.persist(runner);
            Folio existing = new Folio();
            existing.setTitle("Existing folio");
            existing.setCode("E-1");
            existing.setQuantity(3);
            existing.setHome(one);
            existing.setSecretNote("old-secret-value");
            em.persist(existing);
            em.flush();
            depot = one.getId();
            vault = hidden.getId();
            folio = existing.getId();
        });
        AdminEntityRegistry registry = EntityScanner.scan(ListTestPersistence.factory().getMetamodel(),
                DiscoverySettings.defaults());
        entityManager = ListTestPersistence.entityManager();
        ConsoleNavigation navigation = new ConsoleNavigation(registry, CountService.over(entityManager, Clock.systemUTC()));
        ConsoleChromeFactory chromes = new ConsoleChromeFactory(BASE, new ConsoleSettings("Back office", "#0d9f6e"),
                TOKENS, navigation);
        controller = new FormController(chromes, new ConsoleViews(Page::new), navigation,
                FormQueryService.over(entityManager, registry), DetailQueryService.over(entityManager, registry),
                new TransactionalRecordWriteService(entityManager, registry), BASE);
        RecordingStatementInspector.clear();
    }

    @AfterEach
    void close() {
        entityManager.close();
    }

    private static MockHttpServletRequest request(String... parameters) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession());
        request.setUserPrincipal((Principal) () -> "admin");
        for (int i = 0; i + 1 < parameters.length; i += 2) {
            request.addParameter(parameters[i], parameters[i + 1]);
        }
        return request;
    }

    private static MockHttpServletRequest folioRequest(String... extra) {
        MockHttpServletRequest request = request("title", "A new folio", "code", "N-1", "quantity", "7", "urgent", "true",
                "home", "HOME", "secretNote", "typed-secret");
        for (int i = 0; i + 1 < extra.length; i += 2) {
            request.setParameter(extra[i], extra[i + 1]);
        }
        return request;
    }

    private MockHttpServletRequest validCreate(String... extra) {
        MockHttpServletRequest request = folioRequest(extra);
        if ("HOME".equals(request.getParameter("home"))) {
            request.setParameter("home", String.valueOf(depot));
        }
        return request;
    }

    private static FormView formOf(ModelAndView result) {
        return (FormView) result.getModel().get(PageChrome.VIEW_ATTRIBUTE);
    }

    private static PageChrome chromeOf(ModelAndView result) {
        return (PageChrome) result.getModel().get(PageChrome.ATTRIBUTE);
    }

    private static FormView.Field field(FormView view, String key) {
        return view.fields().stream().filter(f -> f.key().equals(key)).findFirst().orElseThrow();
    }

    private static String location(ModelAndView result) {
        assertThat(result.getView()).isInstanceOf(RedirectView.class);
        return ((RedirectView) result.getView()).getUrl();
    }

    private static ResponseStatusException refusal(Runnable call) {
        try {
            call.run();
        } catch (ResponseStatusException refused) {
            return refused;
        }
        throw new AssertionError("expected the call to be refused");
    }

    private long folios() {
        try (EntityManager em = ListTestPersistence.entityManager()) {
            return em.createQuery("select count(f) from Folio f", Long.class).getSingleResult();
        }
    }

    private Folio storedFolio() {
        try (EntityManager em = ListTestPersistence.entityManager()) {
            return em.find(Folio.class, folio);
        }
    }

    // ---- the forms ---------------------------------------------------------------------------------------------------

    @Test
    void theEditFormIsPrefilledAndPostsToTheRecordWithTheVersionItWasOpenedWith() {
        ModelAndView result = controller.edit("folio", String.valueOf(folio), request());

        assertThat(((Page) result.getView()).template()).isEqualTo(FormView.TEMPLATE);
        FormView form = formOf(result);
        assertThat(form.mode()).isEqualTo(FormView.Mode.EDIT);
        assertThat(form.recordId()).isEqualTo(String.valueOf(folio));
        assertThat(form.title()).isEqualTo("Edit Existing folio");
        assertThat(form.actionHref()).isEqualTo("/admin/folio/" + folio + "?jpa-admin-version=0");
        assertThat(form.cancelHref()).isEqualTo("/admin/folio/" + folio);
        assertThat(field(form, "title").value()).isEqualTo("Existing folio");
        assertThat(field(form, "secretNote").inputType()).isEqualTo(FormView.Field.InputType.MASKED);
        assertThat(field(form, "secretNote").value()).isNull();
        assertThat(form.toString()).doesNotContain("old-secret-value").doesNotContain(STORED_SECRET);
        assertThat(chromeOf(result).breadcrumbs()).containsExactly(new PageChrome.Breadcrumb("Dashboard", "/admin"),
                new PageChrome.Breadcrumb("Folios", "/admin/folio"),
                new PageChrome.Breadcrumb("Existing folio", "/admin/folio/" + folio), new PageChrome.Breadcrumb("Edit", null));
    }

    @Test
    void theCreateFormPostsToTheCollectionAndCancelLeadsToTheList() {
        FormView form = formOf(controller.newRecord("folio", null, request()));

        assertThat(form.actionHref()).isEqualTo("/admin/folio");
        assertThat(form.cancelHref()).isEqualTo("/admin/folio");
        assertThat(form.mode()).isEqualTo(FormView.Mode.CREATE);
        assertThat(form.recordId()).isNull();
    }

    @Test
    void openingAFormChangesNothingInTheDatabase() {
        RecordingStatementInspector.clear();

        controller.newRecord("folio", null, request());
        controller.newRecord("folio", String.valueOf(folio), request());
        controller.edit("folio", String.valueOf(folio), request());

        assertThat(RecordingStatementInspector.statements()).noneMatch(sql -> {
            String lower = sql.toLowerCase(Locale.ROOT);
            return lower.startsWith("insert") || lower.startsWith("update") || lower.startsWith("delete");
        });
        assertThat(folios()).isEqualTo(1);
        assertThat(storedFolio().getRevision()).isZero();
    }

    // ---- create ------------------------------------------------------------------------------------------------------

    @Test
    void aSavedCreateRedirectsToTheListWithAMessageThatNamesNoSecret() {
        MockHttpServletRequest request = validCreate();

        ModelAndView result = controller.create("folio", request, new MockHttpServletResponse());

        assertThat(location(result)).isEqualTo("/admin/folio");
        PageChrome.FlashMessage message = ConsoleFlash.take(request).get(0);
        assertThat(message.level()).isEqualTo(PageChrome.FlashMessage.Level.SUCCESS);
        assertThat(message.text()).isEqualTo("Created folio “A new folio”.").doesNotContain("typed-secret");
        assertThat(folios()).isEqualTo(2);
    }

    @Test
    void theRedirectAfterASaveIsNeverTakenFromTheRequest() {
        for (String name : new String[] {"next", "redirect", "returnTo", "return", "url", "back", "_redirect", "continue",
                "location", "target", "cancelHref", "actionHref"}) {
            for (String target : new String[] {"/admin/folio/1", "/admin/vault", "https://evil.example/steal"}) {
                MockHttpServletRequest request = validCreate(name, target);
                request.setParameter("code", "R-" + name + target.length());
                ModelAndView result = controller.create("folio", request, new MockHttpServletResponse());
                assertThat(location(result)).as(name + " = " + target).isEqualTo("/admin/folio");
            }
        }
        MockHttpServletRequest update = request("title", "Retitled", "next", "//evil.example", "redirect", "/admin/../x");
        update.setParameter("jpa-admin-version", "0");
        assertThat(location(controller.update("folio", String.valueOf(folio), update, new MockHttpServletResponse())))
                .isEqualTo("/admin/folio/" + folio);
    }

    @Test
    void aRefusedCreateShowsTheFormAgainWithStatus422TheTypedValuesAndNoTypedSecret() {
        MockHttpServletRequest request = validCreate("quantity", "twelve", "title", "", "secretNote", "needle-typed-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();

        ModelAndView result = controller.create("folio", request, response);

        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(((Page) result.getView()).template()).isEqualTo(FormView.TEMPLATE);
        FormView form = formOf(result);
        assertThat(field(form, "quantity").value()).isEqualTo("twelve");
        assertThat(field(form, "quantity").error()).isEqualTo(FieldMessages.wholeNumber());
        assertThat(field(form, "title").error()).isEqualTo(FieldMessages.required());
        assertThat(field(form, "code").value()).isEqualTo("N-1");
        assertThat(field(form, "secretNote").value()).isNull();
        assertThat(form.toString()).doesNotContain("needle-typed-secret");
        assertThat(chromeOf(result).toString()).doesNotContain("needle-typed-secret");
        assertThat(ConsoleFlash.take(request)).isEmpty();
        assertThat(folios()).isEqualTo(1);
    }

    @Test
    void aWholeFormErrorIsCarriedInFormErrors() {
        MockHttpServletRequest request = validCreate("code", "E-1"); // unique in the database
        MockHttpServletResponse response = new MockHttpServletResponse();

        FormView form = formOf(controller.create("folio", request, response));

        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(form.formErrors()).containsExactly(FieldMessages.refused());
        assertThat(form.fields()).allSatisfy(f -> assertThat(f.error()).isNull());
        assertThat(String.join(" ", form.formErrors())).doesNotContainIgnoringCase("unique").doesNotContain("E-1");
    }

    // ---- update ------------------------------------------------------------------------------------------------------

    @Test
    void aSavedUpdateRedirectsToTheRecordWithAMessage() {
        MockHttpServletRequest request = request("title", "Retitled", "jpa-admin-version", "0");

        ModelAndView result = controller.update("folio", String.valueOf(folio), request, new MockHttpServletResponse());

        assertThat(location(result)).isEqualTo("/admin/folio/" + folio);
        assertThat(ConsoleFlash.take(request).get(0).text()).isEqualTo("Saved the changes to folio “Retitled”.");
        assertThat(storedFolio().getTitle()).isEqualTo("Retitled");
    }

    @Test
    void aBlankSecretKeepsTheStoredOneThroughTheController() {
        controller.update("folio", String.valueOf(folio),
                request("secretNote", "", "title", "Existing folio", "jpa-admin-version", "0"), new MockHttpServletResponse());

        assertThat(storedFolio().getSecretNote()).isEqualTo(STORED_SECRET);
        assertThat(storedFolio().getRevision()).isZero();
    }

    @Test
    void aPostedIdAndVersionAreIgnoredAndTheAddressDecidesWhichRecord() {
        MockHttpServletRequest request = request("id", "999", "revision", "50", "title", "Retitled", "jpa-admin-version", "0");

        controller.update("folio", String.valueOf(folio), request, new MockHttpServletResponse());

        Folio updated = storedFolio();
        assertThat(updated.getId()).isEqualTo(folio);
        assertThat(updated.getRevision()).isEqualTo(1);
    }

    @Test
    void aStaleFormShowsTheFormAgainWithAPlainMessageAndStaysStale() {
        controller.update("folio", String.valueOf(folio), request("title", "First", "jpa-admin-version", "0"),
                new MockHttpServletResponse());
        MockHttpServletRequest request = request("title", "Second", "jpa-admin-version", "0");
        MockHttpServletResponse response = new MockHttpServletResponse();

        ModelAndView result = controller.update("folio", String.valueOf(folio), request, response);

        assertThat(response.getStatus()).isEqualTo(422);
        FormView form = formOf(result);
        assertThat(form.formErrors()).containsExactly(FieldMessages.stale());
        assertThat(field(form, "title").value()).isEqualTo("Second");
        assertThat(form.actionHref()).as("the old version is kept, so a second try is stale too")
                .isEqualTo("/admin/folio/" + folio + "?jpa-admin-version=0");
        assertThat(storedFolio().getTitle()).isEqualTo("First");
    }

    @Test
    void aFormWithNoVersionForAVersionedEntityIsStale() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        FormView form = formOf(controller.update("folio", String.valueOf(folio), request("title", "x"), response));

        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(form.formErrors()).containsExactly(FieldMessages.stale());
    }

    // ---- 404 and 403 -------------------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"99999", "abc", "1 or 1=1", "-1", "007", "1.5", "1e3"})
    void anUnknownOrMalformedIdIsTheSameConsoleNotFoundOnEditAndUpdate(String id) {
        ResponseStatusException edit = refusal(() -> controller.edit("folio", id, request()));
        ResponseStatusException update = refusal(
                () -> controller.update("folio", id, request("title", "x", "jpa-admin-version", "0"), new MockHttpServletResponse()));

        assertThat(edit.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(edit.getReason()).isNull();
        assertThat(update.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(update.getReason()).isNull();
        assertThat(storedFolio().getTitle()).isEqualTo("Existing folio");
    }

    @Test
    void aHiddenAndAnUnknownEntityAreTheSameConsoleNotFoundOnEveryFormRoute() {
        for (String entity : new String[] {"vault", "nosuchthing"}) {
            assertThat(refusal(() -> controller.newRecord(entity, null, request())).getStatusCode()).as(entity)
                    .isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(refusal(() -> controller.newRecord(entity, "1", request())).getStatusCode()).as(entity)
                    .isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(refusal(() -> controller.create(entity, validCreate(), new MockHttpServletResponse())).getStatusCode())
                    .as(entity).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(refusal(() -> controller.edit(entity, String.valueOf(vault), request())).getStatusCode()).as(entity)
                    .isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(refusal(() -> controller.update(entity, String.valueOf(vault), request("name", "x"),
                    new MockHttpServletResponse())).getStatusCode()).as(entity).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Test
    void anEntityTheConsoleMayNotCreateOrChangeIsForbiddenOnTheFormAndOnTheSave() {
        Long[] ids = new Long[2];
        ListTestPersistence.inTransaction(em -> {
            Frozen frozen = new Frozen();
            frozen.setTitle("kept");
            em.persist(frozen);
            Archive archive = new Archive();
            archive.setTitle("kept");
            em.persist(archive);
            em.flush();
            ids[0] = frozen.getId();
            ids[1] = archive.getId();
        });

        assertThat(refusal(() -> controller.newRecord("sealed", null, request())).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(refusal(() -> controller.create("sealed", request("title", "x"), new MockHttpServletResponse()))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(refusal(() -> controller.edit("frozen", String.valueOf(ids[0]), request())).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(refusal(() -> controller.update("frozen", String.valueOf(ids[0]), request("title", "x"),
                new MockHttpServletResponse())).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(refusal(() -> controller.edit("archive", String.valueOf(ids[1]), request())).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(refusal(() -> controller.create("archive", request("title", "x"), new MockHttpServletResponse()))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // A frozen entity (not updatable) can still be created in:
        assertThat(location(controller.create("frozen", request("title", "fresh"), new MockHttpServletResponse())))
                .isEqualTo("/admin/frozen");
    }
}

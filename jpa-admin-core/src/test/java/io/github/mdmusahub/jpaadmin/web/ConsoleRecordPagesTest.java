package io.github.mdmusahub.jpaadmin.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.metadata.DiscoverySettings;
import io.github.mdmusahub.jpaadmin.metadata.EntityScanner;
import io.github.mdmusahub.jpaadmin.service.CountService;
import io.github.mdmusahub.jpaadmin.service.DeleteService;
import io.github.mdmusahub.jpaadmin.service.DetailQueryService;
import io.github.mdmusahub.jpaadmin.service.ListQueryService;
import io.github.mdmusahub.jpaadmin.testsupport.ListTestPersistence;
import io.github.mdmusahub.jpaadmin.testsupport.RecordingStatementInspector;
import io.github.mdmusahub.jpaadmin.testsupport.TransactionalDeleteService;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Anchor;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Archive;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Depot;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Parcel;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.ParcelItem;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.ParcelStatus;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Pass;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Vault;
import io.github.mdmusahub.jpaadmin.web.view.Cell;
import io.github.mdmusahub.jpaadmin.web.view.DetailView;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.security.Principal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.view.RedirectView;

/**
 * The record routes (detail, delete, bulk delete, the duplicate checks) through the real controller, services, factories
 * and a real database. It checks what the visitor gets: the page's records, the 404 that never differs, the refusals, the
 * redirect that carries the list state and is never taken from the request, and the messages that follow a delete.
 */
class ConsoleRecordPagesTest {

    private record Page(String template, Locale locale) implements View {
        @Override
        public void render(Map<String, ?> model, HttpServletRequest request, HttpServletResponse response) {
            throw new UnsupportedOperationException("the test double renders nothing");
        }
    }

    private static final BasePath BASE = BasePath.of("/admin");
    private static final String SECRET = "needle-secret-value";
    private static final CsrfTokens TOKENS = request -> new PageChrome.Csrf("_csrf", "X-CSRF-TOKEN", "token-123");

    private RecordController controller;
    private EntityManager entityManager;
    private Long[] parcels;
    private Long depotId;
    private Long spareDepot;
    private Long parcelWithItem;
    private Long archiveId;
    private Long anchorId;
    private Long vaultId;

    @BeforeEach
    void wire() {
        ListTestPersistence.empty();
        parcels = new Long[60];
        ListTestPersistence.inTransaction(em -> {
            Depot depot = new Depot();
            depot.setName("Central depot");
            depot.setApiKey(SECRET);
            em.persist(depot);
            Depot spare = new Depot();
            spare.setName("Spare <b>depot</b>");
            em.persist(spare);
            Vault vault = new Vault();
            vault.setName("Hidden vault");
            em.persist(vault);
            for (int i = 0; i < parcels.length; i++) {
                Parcel parcel = new Parcel();
                parcel.setReference("REF-" + i);
                parcel.setNotes("Parcel number " + i);
                parcel.setStatus(ParcelStatus.values()[i % 4]);
                parcel.setDepot(depot);
                parcel.setVault(i == 0 ? vault : null);
                parcel.setAccessToken(SECRET + i);
                parcel.setMemo(SECRET + "memo" + i);
                em.persist(parcel);
                parcels[i] = parcel.getId();
            }
            Parcel withItem = new Parcel();
            withItem.setReference("REF-ITEM");
            withItem.setNotes("Parcel with an item");
            em.persist(withItem);
            ParcelItem item = new ParcelItem();
            item.setDescription("child-row-text");
            item.setParcel(withItem);
            em.persist(item);
            Archive archive = new Archive();
            archive.setTitle("Old record");
            em.persist(archive);
            Anchor anchor = new Anchor();
            anchor.setTitle("Fixed record");
            em.persist(anchor);
            Pass pass = new Pass();
            pass.setAccessToken(77L);
            pass.setHolder("Someone");
            em.persist(pass);
            em.flush();
            depotId = depot.getId();
            spareDepot = spare.getId();
            vaultId = vault.getId();
            parcelWithItem = withItem.getId();
            archiveId = archive.getId();
            anchorId = anchor.getId();
        });
        AdminEntityRegistry registry = EntityScanner.scan(ListTestPersistence.factory().getMetamodel(),
                DiscoverySettings.defaults());
        entityManager = ListTestPersistence.entityManager();
        ConsoleNavigation navigation = new ConsoleNavigation(registry, CountService.over(entityManager, Clock.systemUTC()));
        ConsoleChromeFactory chromes = new ConsoleChromeFactory(BASE, new ConsoleSettings("Back office", "#0d9f6e"),
                TOKENS, navigation);
        controller = new RecordController(chromes, new ConsoleViews(Page::new), navigation,
                ListQueryService.over(entityManager, registry), DetailQueryService.over(entityManager, registry),
                new TransactionalDeleteService(entityManager, registry), BASE);
        RecordingStatementInspector.clear();
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

    private ModelAndView open(String entity, Object id, String... parameters) {
        return controller.detail(entity, String.valueOf(id), request(parameters));
    }

    private static DetailView viewOf(ModelAndView result) {
        return (DetailView) result.getModel().get(PageChrome.VIEW_ATTRIBUTE);
    }

    private static PageChrome chromeOf(ModelAndView result) {
        return (PageChrome) result.getModel().get(PageChrome.ATTRIBUTE);
    }

    private static DetailView.Field field(DetailView view, String label) {
        return view.fields().stream().filter(f -> f.label().equals(label)).findFirst()
                .orElseThrow(() -> new AssertionError("no field " + label));
    }

    private static String location(ModelAndView result) {
        assertThat(result.getView()).isInstanceOf(RedirectView.class);
        RedirectView redirect = (RedirectView) result.getView();
        return redirect.getUrl();
    }

    private static long count(Class<?> type) {
        try (EntityManager em = ListTestPersistence.entityManager()) {
            return em.createQuery("select count(e) from " + type.getSimpleName() + " e", Long.class).getSingleResult();
        }
    }

    private static boolean exists(Class<?> type, Object id) {
        try (EntityManager em = ListTestPersistence.entityManager()) {
            return em.find(type, id) != null;
        }
    }

    private static ResponseStatusException refusal(Runnable call) {
        try {
            call.run();
        } catch (ResponseStatusException refused) {
            return refused;
        }
        throw new AssertionError("expected the call to be refused");
    }

    // ---- the detail page ---------------------------------------------------------------------------------------------

    @Test
    void theDetailPageIsTheDetailTemplateWithTheContractsModelAttributes() {
        ModelAndView result = open("parcel", parcels[3]);

        assertThat(result.getViewName()).as("a name would be offered to the host's view resolvers").isNull();
        assertThat(((Page) result.getView()).template()).isEqualTo(DetailView.TEMPLATE).isEqualTo("jpa-admin/detail");
        assertThat(result.getModel()).containsOnlyKeys(PageChrome.ATTRIBUTE, PageChrome.VIEW_ATTRIBUTE);
        DetailView view = viewOf(result);
        assertThat(view.entityLabel()).isEqualTo("Parcel");
        assertThat(view.id()).isEqualTo(String.valueOf(parcels[3]));
        assertThat(view.title()).as("the first text attribute names the record").isEqualTo("Parcel number 3");
        assertThat(view.images()).isEmpty();
        assertThat(field(view, "Reference").cell().display()).isEqualTo("REF-3");
        assertThat(chromeOf(result).activeEntity()).isEqualTo("parcel");
        assertThat(chromeOf(result).user()).isEqualTo("admin");
    }

    @Test
    void theBreadcrumbsLeadBackToTheListAndTheLastOneIsTheRecord() {
        PageChrome chrome = chromeOf(open("parcel", parcels[3]));

        assertThat(chrome.breadcrumbs()).containsExactly(new PageChrome.Breadcrumb("Dashboard", "/admin"),
                new PageChrome.Breadcrumb("Parcels", "/admin/parcel"),
                new PageChrome.Breadcrumb("Parcel number 3", null));
    }

    @Test
    void onlyDeleteIsOfferedOnTheDetailPageAndItsAddressIsTheRecordsOwn() {
        DetailView.Actions actions = viewOf(open("parcel", parcels[3])).actions();

        assertThat(actions.canEdit()).as("Edit is Phase 8").isFalse();
        assertThat(actions.canDuplicate()).as("Duplicate needs the Phase 8 form: no link that leads nowhere").isFalse();
        assertThat(actions.canDelete()).isTrue();
        assertThat(actions.deleteHref()).isEqualTo("/admin/parcel/" + parcels[3] + "/delete");
    }

    @Test
    void aRecordWithNoLabelValueIsTitledWithTheEntityAndItsId() {
        ModelAndView result = open("anchor", anchorId);

        assertThat(viewOf(result).title()).isEqualTo("Fixed record");
        ListTestPersistence.inTransaction(em -> em.find(Anchor.class, anchorId).setTitle(null));
        assertThat(viewOf(open("anchor", anchorId)).title()).isEqualTo("Anchor #" + anchorId);
    }

    @Test
    void aSensitiveValueIsMaskedAndIsInNoPartOfTheModel() {
        ModelAndView result = open("parcel", parcels[3]);

        assertThat(field(viewOf(result), "Access token").cell()).isEqualTo(Cell.masked());
        assertThat(field(viewOf(result), "Memo").cell()).isEqualTo(Cell.masked());
        assertThat(result.getModel().toString()).doesNotContain(SECRET);
        assertThat(String.join("\n", RecordingStatementInspector.statements()).toLowerCase(Locale.ROOT))
                .doesNotContain("access_token", "memo");
    }

    @Test
    void aHiddenFieldAndACollectionAreNotRendered() {
        DetailView view = viewOf(open("parcel", parcelWithItem));

        assertThat(view.fields()).extracting(DetailView.Field::label).doesNotContain("Internal code");
        assertThat(field(view, "Items").cell().display()).isEqualTo("(not shown)");
        assertThat(view.toString()).doesNotContain("child-row-text");
    }

    @Test
    void theDetailPageDoesNotCallTheFilterChoiceQueryAndCostsOneReadOfTheRecord() {
        RecordingStatementInspector.clear();

        open("parcel", parcels[3]);

        List<String> reads = RecordingStatementInspector.selects().stream()
                .filter(sql -> !sql.toLowerCase(Locale.ROOT).contains("count(")).toList();
        assertThat(reads).as("the record, and nothing for the filter dropdowns").hasSize(1);
        assertThat(reads.get(0)).contains("where p1_0.id=?");
    }

    // ---- links ---------------------------------------------------------------------------------------------------------

    @Test
    void aRelationFieldLinksToTheRelatedRecordWhenTheTargetIsInTheConsole() {
        DetailView view = viewOf(open("parcel", parcels[3]));

        Cell depot = field(view, "Depot").cell();
        assertThat(depot.type()).isEqualTo(Cell.Type.RELATION);
        assertThat(depot.display()).isEqualTo("Central depot");
        assertThat(depot.href()).isEqualTo("/admin/depot/" + depotId);
    }

    @Test
    void aRelationToAHiddenTargetHasNoLinkAndShowsHashId() {
        DetailView view = viewOf(open("parcel", parcels[0]));

        Cell vault = field(view, "Vault").cell();
        assertThat(vault.display()).isEqualTo("#" + vaultId).doesNotContain("Hidden vault");
        assertThat(vault.href()).isNull();
    }

    @Test
    void aMissingRelationIsAnEmptyCellWithNoLink() {
        Cell vault = field(viewOf(open("parcel", parcels[3])), "Vault").cell();

        assertThat(vault.type()).isEqualTo(Cell.Type.EMPTY);
        assertThat(vault.href()).isNull();
    }

    @Test
    void theListStateTheDetailPageWasOpenedWithTravelsInTheBreadcrumbAndTheDeleteAddress() {
        ModelAndView result = open("parcel", parcels[3], "q", "number", "sort", "reference", "dir", "desc", "page", "2",
                "size", "10", "filter.status", "PENDING");

        String state = "?q=number&filter.status=PENDING&sort=reference&dir=desc&page=2&size=10";
        assertThat(chromeOf(result).breadcrumbs().get(1).href()).isEqualTo("/admin/parcel" + state);
        assertThat(viewOf(result).actions().deleteHref()).isEqualTo("/admin/parcel/" + parcels[3] + "/delete" + state);
    }

    @Test
    void aSortOrFilterThatTheEntityCannotDoIsNotCarriedIntoAnyLink() {
        ModelAndView result = open("parcel", parcels[3], "sort", "accessToken", "dir", "asc", "filter.accessToken", "x",
                "filter.memo", "y", "page", "-4", "size", "7", "q", "<script>alert(1)</script>");

        String all = chromeOf(result).breadcrumbs() + " " + viewOf(result).actions();
        assertThat(all).doesNotContain("accessToken", "memo", "page=", "size=", "sort=").contains("q=%3Cscript%3E");
    }

    @Test
    void everyLinkOfTheDetailPageLiesUnderTheBasePathAndPassesTheLinkRule() {
        ModelAndView result = open("parcel", parcels[0], "q", "http://x", "page", "1");
        List<String> links = new ArrayList<>();
        chromeOf(result).breadcrumbs().forEach(crumb -> links.add(crumb.href()));
        links.add(viewOf(result).actions().deleteHref());
        viewOf(result).fields().forEach(f -> links.add(f.cell().href()));
        links.removeIf(link -> link == null);

        assertThat(links).isNotEmpty().allSatisfy(link -> {
            assertThat(link).startsWith("/admin");
            assertThat(link).doesNotContain(" ", "'", "\"", "http://x", "//");
            // The record constructors apply the rule of D-28 to these links; building a cell with the link repeats the check.
            new Cell(Cell.Type.TEXT, "x", Cell.Tone.NONE, link, null);
        });
    }

    // ---- the 404 that never differs (SEC-2) -------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"987654321", "abc", "1 or 1=1", "../x", "-1", "007", "99999999999999999999", "%00", "<script>"})
    void anIdThatHasNoRecordAndATextThatIsNotAnIdAnswerTheSameNotFound(String id) {
        ResponseStatusException unknown = refusal(() -> open("parcel", 987654321));
        ResponseStatusException tried = refusal(() -> open("parcel", id));

        assertThat(tried.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(tried.getReason()).as("no text that could repeat what was typed").isNull();
        assertThat(tried.getMessage()).isEqualTo(unknown.getMessage());
        assertThat(tried.getBody()).isEqualTo(unknown.getBody());
    }

    @Test
    void aHiddenEntityAnUnknownEntityAndAnEntityWithASensitiveIdentifierAnswerTheSameNotFoundAsAMissingRecord() {
        ResponseStatusException missing = refusal(() -> open("parcel", 987654321));

        for (String entity : List.of("vault", "nosuchthing", "pass", "VAULT")) {
            ResponseStatusException refused = refusal(() -> open(entity, 1));
            assertThat(refused.getStatusCode()).as(entity).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(refused.getMessage()).as(entity).isEqualTo(missing.getMessage());
            assertThat(refused.getBody()).as(entity).isEqualTo(missing.getBody());
        }
    }

    @Test
    void theNotFoundNamesNeitherTheEntityNorTheIdTheVisitorTyped() {
        ResponseStatusException refused = refusal(() -> open("vault", "secret-looking-id"));

        assertThat(String.valueOf(refused.getMessage())).doesNotContain("vault", "secret-looking-id");
        assertThat(String.valueOf(refused.getBody())).doesNotContain("vault", "secret-looking-id");
    }

    // ---- delete one record -------------------------------------------------------------------------------------------------

    @Test
    void deleteRemovesTheRecordRedirectsToTheListAndLeavesAMessage() {
        MockHttpServletRequest request = request();

        ModelAndView result = controller.delete("depot", String.valueOf(spareDepot), request);

        assertThat(exists(Depot.class, spareDepot)).isFalse();
        assertThat(count(Depot.class)).isEqualTo(1);
        assertThat(location(result)).isEqualTo("/admin/depot");
        assertThat(ConsoleFlash.take(request)).containsExactly(new PageChrome.FlashMessage(
                PageChrome.FlashMessage.Level.SUCCESS, "Deleted depot “Spare <b>depot</b>”."));
    }

    @Test
    void theMessageHoldsTheLabelAsPlainTextForThePageToEscape() {
        MockHttpServletRequest request = request();
        controller.delete("depot", String.valueOf(spareDepot), request);

        PageChrome chrome = chromeFactoryOf().chrome(request, List.of());

        assertThat(chrome.flash()).hasSize(1);
        assertThat(chrome.flash().get(0).text()).contains("<b>depot</b>");
    }

    @Test
    void theMessageIsShownOnceAndThenGone() {
        MockHttpServletRequest request = request();
        controller.delete("depot", String.valueOf(spareDepot), request);

        assertThat(chromeFactoryOf().chrome(request, List.of()).flash()).hasSize(1);
        assertThat(chromeFactoryOf().chrome(request, List.of()).flash()).isEmpty();
    }

    @Test
    void afterADeleteTheVisitorGoesBackToTheSameSearchFiltersSortAndSizeOnTheFirstPage() {
        MockHttpServletRequest request = request("q", "number", "sort", "reference", "dir", "desc", "page", "2", "size", "10",
                "filter.status", "PENDING");

        ModelAndView result = controller.delete("parcel", String.valueOf(parcels[4]), request);

        assertThat(location(result)).as("page reset: a deletion changes what the list holds (D-62)")
                .isEqualTo("/admin/parcel?q=number&filter.status=PENDING&sort=reference&dir=desc&size=10");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://evil.example/", "//evil.example/x", "/admin/../etc", "javascript:alert(1)", "\\\\evil"})
    void theReturnAddressIsNeverTakenFromTheRequest(String hostile) {
        MockHttpServletRequest request = request("returnTo", hostile, "redirect", hostile, "next", hostile, "url", hostile,
                "return", hostile);

        ModelAndView result = controller.delete("parcel", String.valueOf(parcels[5]), request);

        assertThat(location(result)).isEqualTo("/admin/parcel");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://evil.example/", "//evil.example/x", "javascript:alert(1)", "\\evil"})
    void aHostileSearchTextIsOnlyEverDataInTheEncodedQueryOfTheReturnAddress(String hostile) {
        ModelAndView result = controller.delete("parcel", String.valueOf(parcels[6]), request("q", hostile));

        assertThat(location(result)).startsWith("/admin/parcel?q=").doesNotContain("://", "//evil", " ", "\\");
    }

    @Test
    void aTextThatIsNotAnIdAndAMissingRecordAreTheSameNotFoundAndNothingIsDeleted() {
        for (String id : List.of("987654321", "abc", "1 or 1=1", "../x", "")) {
            ResponseStatusException refused = refusal(() -> controller.delete("parcel", id, request()));
            assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(refused.getReason()).isNull();
        }

        assertThat(count(Parcel.class)).isEqualTo(61);
    }

    @Test
    void deleteForAHiddenOrUnknownEntityIsTheSameNotFound() {
        for (String entity : List.of("vault", "nosuchthing", "pass")) {
            assertThat(refusal(() -> controller.delete(entity, "1", request())).getStatusCode()).as(entity)
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }

        assertThat(exists(Vault.class, vaultId)).isTrue();
        assertThat(count(Pass.class)).isEqualTo(1);
    }

    @Test
    void deleteForAReadOnlyOrNonDeletableEntityIsRefusedWithForbiddenAndTheRecordStays() {
        assertThat(refusal(() -> controller.delete("archive", String.valueOf(archiveId), request())).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(refusal(() -> controller.delete("anchor", String.valueOf(anchorId), request())).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(exists(Archive.class, archiveId)).isTrue();
        assertThat(exists(Anchor.class, anchorId)).isTrue();
    }

    @Test
    void aRecordThatOtherRecordsPointAtIsKeptAndTheVisitorIsToldWithoutAnySql() {
        MockHttpServletRequest request = request("page", "3");

        ModelAndView result = controller.delete("parcel", String.valueOf(parcelWithItem), request);

        assertThat(exists(Parcel.class, parcelWithItem)).isTrue();
        assertThat(location(result)).as("back to the record, which is still there")
                .isEqualTo("/admin/parcel/" + parcelWithItem + "?page=3");
        List<PageChrome.FlashMessage> messages = ConsoleFlash.take(request);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).level()).isEqualTo(PageChrome.FlashMessage.Level.ERROR);
        assertThat(messages.get(0).text().toLowerCase(Locale.ROOT)).contains("was not deleted", "depend")
                .doesNotContain("select", "sql", "constraint", "foreign key", "parcelitem", "jdbc", "exception");
    }

    @Test
    void thereIsNoGetRouteThatDeletesAndEveryDeleteRouteIsAPost() {
        for (Method method : RecordController.class.getDeclaredMethods()) {
            GetMapping get = method.getAnnotation(GetMapping.class);
            if (get != null) {
                assertThat(List.of(get.value())).as(method.getName()).noneMatch(path -> path.contains("delete"));
                assertThat(method.getName()).as("a GET route changes nothing").doesNotContain("delete", "Delete");
            }
        }
        List<String> posts = new ArrayList<>();
        for (Method method : RecordController.class.getDeclaredMethods()) {
            PostMapping post = method.getAnnotation(PostMapping.class);
            if (post != null) {
                posts.addAll(List.of(post.value()));
            }
        }
        assertThat(posts).containsExactlyInAnyOrder("/{entity}/{id}/delete", "/{entity}/bulk-delete");
    }

    @Test
    void openingTheDetailPageAndTheDuplicateRouteDeletesNothing() {
        open("parcel", parcels[3]);
        controller.copy("parcel", String.valueOf(parcels[3]), request());

        assertThat(count(Parcel.class)).isEqualTo(61);
    }

    // ---- bulk delete ---------------------------------------------------------------------------------------------------------

    private static String[] ids(Object... values) {
        String[] texts = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            texts[i] = String.valueOf(values[i]);
        }
        return texts;
    }

    @Test
    void bulkDeleteRemovesTheSelectedRecordsAndReportsHowManyWereDeleted() {
        MockHttpServletRequest request = request();

        ModelAndView result = controller.bulkDelete("parcel", ids(parcels[1], parcels[2], parcels[3]), request);

        assertThat(count(Parcel.class)).isEqualTo(58);
        assertThat(exists(Parcel.class, parcels[4])).isTrue();
        assertThat(location(result)).isEqualTo("/admin/parcel");
        assertThat(ConsoleFlash.take(request)).containsExactly(
                new PageChrome.FlashMessage(PageChrome.FlashMessage.Level.SUCCESS, "Deleted 3 records."));
    }

    @Test
    void bulkDeleteSaysHowManyWereNotDeletedAndRepeatsNoTypedValue() {
        MockHttpServletRequest request = request();

        controller.bulkDelete("parcel", ids(parcels[1], 987654321L, "<script>alert(1)</script>", "1 or 1=1"), request);

        List<PageChrome.FlashMessage> messages = ConsoleFlash.take(request);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).level()).isEqualTo(PageChrome.FlashMessage.Level.WARNING);
        assertThat(messages.get(0).text()).startsWith("Deleted 1 record.").contains("3 selected records were skipped")
                .doesNotContain("script", "1 or 1=1", "987654321");
    }

    @Test
    void afterABulkDeleteTheVisitorGoesBackToTheSameListOnTheFirstPage() {
        MockHttpServletRequest request = request("q", "REF", "sort", "reference", "dir", "asc", "page", "1", "size", "10");

        ModelAndView result = controller.bulkDelete("parcel", ids(parcels[1]), request);

        assertThat(location(result)).isEqualTo("/admin/parcel?q=REF&sort=reference&dir=asc&size=10");
    }

    @Test
    void whenNothingWasDeletedThePageStays() {
        MockHttpServletRequest request = request("page", "2", "size", "10");

        ModelAndView result = controller.bulkDelete("parcel", ids(987654321L), request);

        assertThat(location(result)).isEqualTo("/admin/parcel?page=2&size=10");
        assertThat(ConsoleFlash.take(request).get(0).level()).isEqualTo(PageChrome.FlashMessage.Level.WARNING);
    }

    @Test
    void anEmptySelectionIsAMessageNotAnError() {
        MockHttpServletRequest request = request("page", "1");

        ModelAndView result = controller.bulkDelete("parcel", null, request);

        assertThat(location(result)).isEqualTo("/admin/parcel?page=1");
        assertThat(ConsoleFlash.take(request).get(0).text()).startsWith("No records were selected.");
        assertThat(count(Parcel.class)).isEqualTo(61);
    }

    @Test
    void moreThanTheCapIsRefusedWithAMessageAndNothingIsDeleted() {
        MockHttpServletRequest request = request("page", "1");
        String[] tooMany = new String[DeleteService.MAX_BULK_DELETE + 1];
        for (int i = 0; i < tooMany.length; i++) {
            tooMany[i] = String.valueOf(i == 0 ? parcels[1] : 5_000_000 + i);
        }

        ModelAndView result = controller.bulkDelete("parcel", tooMany, request);

        assertThat(count(Parcel.class)).isEqualTo(61);
        assertThat(exists(Parcel.class, parcels[1])).isTrue();
        assertThat(location(result)).isEqualTo("/admin/parcel?page=1");
        PageChrome.FlashMessage message = ConsoleFlash.take(request).get(0);
        assertThat(message.level()).isEqualTo(PageChrome.FlashMessage.Level.ERROR);
        assertThat(message.text()).isEqualTo("You can delete at most 100 records at a time, and nothing was deleted. "
                + "Select fewer records and try again.");
    }

    @Test
    void exactlyTheCapIsAccepted() {
        String[] hundred = new String[DeleteService.MAX_BULK_DELETE];
        for (int i = 0; i < hundred.length; i++) {
            hundred[i] = String.valueOf(6_000_000 + i);
        }

        controller.bulkDelete("parcel", hundred, request());

        assertThat(count(Parcel.class)).isEqualTo(61);
    }

    @Test
    void oneRecordThatCannotGoKeepsEveryOtherSelectedRecordAndSaysSo() {
        MockHttpServletRequest request = request();

        ModelAndView result = controller.bulkDelete("parcel", ids(parcels[1], parcels[2], parcelWithItem), request);

        assertThat(count(Parcel.class)).as("all or nothing").isEqualTo(61);
        assertThat(location(result)).isEqualTo("/admin/parcel");
        PageChrome.FlashMessage message = ConsoleFlash.take(request).get(0);
        assertThat(message.level()).isEqualTo(PageChrome.FlashMessage.Level.ERROR);
        assertThat(message.text()).startsWith("Nothing was deleted.").contains("3 selected records")
                .doesNotContainIgnoringCase("sql").doesNotContain("parcelitem", "constraint");
    }

    @Test
    void bulkDeleteForAHiddenEntityIsNotFoundAndForAReadOnlyOrNonDeletableEntityIsForbidden() {
        assertThat(refusal(() -> controller.bulkDelete("vault", ids(vaultId), request())).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(refusal(() -> controller.bulkDelete("nosuchthing", ids(1), request())).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(refusal(() -> controller.bulkDelete("archive", ids(archiveId), request())).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(refusal(() -> controller.bulkDelete("anchor", ids(anchorId), request())).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(refusal(() -> controller.bulkDelete("archive", null, request())).getStatusCode())
                .as("even an empty selection learns nothing from a forbidden entity").isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(exists(Archive.class, archiveId)).isTrue();
        assertThat(exists(Anchor.class, anchorId)).isTrue();
        assertThat(exists(Vault.class, vaultId)).isTrue();
    }

    // ---- the duplicate route's checks ----------------------------------------------------------------------------------------------

    @Test
    void aValidCopySourceRedirectsToTheSourceRecordWithANoticeAndCreatesNothing() {
        MockHttpServletRequest request = request();

        ModelAndView result = controller.copy("parcel", String.valueOf(parcels[3]), request);

        assertThat(location(result)).isEqualTo("/admin/parcel/" + parcels[3]);
        assertThat(ConsoleFlash.take(request).get(0).level()).isEqualTo(PageChrome.FlashMessage.Level.INFO);
        assertThat(count(Parcel.class)).isEqualTo(61);
    }

    @ParameterizedTest
    @ValueSource(strings = {"987654321", "abc", "1 or 1=1", "../x", "", "-1", "007"})
    void anUnknownOrMalformedCopySourceIsTheConsoleNotFound(String copyFrom) {
        ResponseStatusException refused = refusal(() -> controller.copy("parcel", copyFrom, request()));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(refused.getReason()).isNull();
    }

    @Test
    void aMissingCopySourceAHiddenEntityAndASensitiveIdentifierAreTheConsoleNotFound() {
        assertThat(refusal(() -> controller.copy("parcel", null, request())).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(refusal(() -> controller.copy("vault", String.valueOf(vaultId), request())).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(refusal(() -> controller.copy("nosuchthing", "1", request())).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(refusal(() -> controller.copy("pass", "77", request())).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void nothingCanBeCreatedInAReadOnlyEntitySoItsCopyRouteIsForbidden() {
        assertThat(refusal(() -> controller.copy("archive", String.valueOf(archiveId), request())).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ---- the dashboard's permission flags -----------------------------------------------------------------------------------------

    @Test
    void theDashboardOffersDeleteOnlyWhereTheServiceWouldAllowIt() {
        ConsoleNavigation navigation = new ConsoleNavigation(
                EntityScanner.scan(ListTestPersistence.factory().getMetamodel(), DiscoverySettings.defaults()),
                CountService.over(entityManager, Clock.systemUTC()));
        Map<String, Boolean> deletes = new java.util.TreeMap<>();
        navigation.dashboard().groups().stream().flatMap(group -> group.entities().stream())
                .forEach(card -> deletes.put(card.entity(), card.permissions().delete()));

        assertThat(deletes).containsEntry("parcel", true).containsEntry("archive", false).containsEntry("anchor", false)
                .containsEntry("pass", false);
    }

    private ConsoleChromeFactory chromeFactoryOf() {
        AdminEntityRegistry registry = EntityScanner.scan(ListTestPersistence.factory().getMetamodel(),
                DiscoverySettings.defaults());
        return new ConsoleChromeFactory(BASE, new ConsoleSettings("Back office", "#0d9f6e"), TOKENS,
                new ConsoleNavigation(registry, CountService.over(entityManager, Clock.systemUTC())));
    }
}

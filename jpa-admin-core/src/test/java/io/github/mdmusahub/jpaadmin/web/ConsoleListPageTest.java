package io.github.mdmusahub.jpaadmin.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.metadata.DiscoverySettings;
import io.github.mdmusahub.jpaadmin.metadata.EntityScanner;
import io.github.mdmusahub.jpaadmin.service.CountService;
import io.github.mdmusahub.jpaadmin.service.ListQueryService;
import io.github.mdmusahub.jpaadmin.testsupport.ListTestPersistence;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Depot;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Parcel;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.ParcelStatus;
import io.github.mdmusahub.jpaadmin.web.view.Cell;
import io.github.mdmusahub.jpaadmin.web.view.ListView;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;

/**
 * The list page's controller (ARCH-2: thin) against a real database: it fills only the contract's records, it never
 * answers with an error for a bad parameter, it answers a hidden entity exactly like a name that never existed, and
 * what it hands to the template holds no entity, no collection and no sensitive value.
 */
class ConsoleListPageTest {

    private record Page(String template, Locale locale) implements View {
        @Override
        public void render(Map<String, ?> model, HttpServletRequest request, HttpServletResponse response) {
            throw new UnsupportedOperationException("the test double renders nothing");
        }
    }

    private static final BasePath BASE = BasePath.of("/admin");
    private static final String SECRET = "needle-secret-token";
    private static final CsrfTokens TOKENS = request -> new PageChrome.Csrf("_csrf", "X-CSRF-TOKEN", "token-123");

    private static EntityController controller;

    @BeforeAll
    static void wire() {
        ListTestPersistence.empty();
        ListTestPersistence.inTransaction(em -> {
            Depot depot = new Depot();
            depot.setName("Central depot");
            em.persist(depot);
            for (int i = 1; i <= 60; i++) {
                Parcel parcel = new Parcel();
                parcel.setReference("REF-" + i);
                parcel.setStatus(ParcelStatus.values()[i % 4]);
                parcel.setDepot(depot);
                parcel.setAccessToken(SECRET + i);
                parcel.setMemo("memo " + SECRET + i);
                em.persist(parcel);
            }
        });
        AdminEntityRegistry registry = EntityScanner.scan(ListTestPersistence.factory().getMetamodel(),
                DiscoverySettings.defaults());
        var entityManager = ListTestPersistence.entityManager();
        ConsoleNavigation navigation = new ConsoleNavigation(registry, CountService.over(entityManager, Clock.systemUTC()));
        ConsoleChromeFactory chromes = new ConsoleChromeFactory(BASE, new ConsoleSettings("Back office", "#0d9f6e"),
                TOKENS, navigation);
        controller = new EntityController(chromes, new ConsoleViews(Page::new), navigation,
                ListQueryService.over(entityManager, registry), BASE);
    }

    private static MockHttpServletRequest signedIn() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setUserPrincipal((Principal) () -> "admin");
        return request;
    }

    private static ModelAndView open(String entity, String q, String sort, String dir, String page, String size) {
        return controller.entity(entity, q, sort, dir, page, size, signedIn());
    }

    private static ModelAndView open(String entity) {
        return open(entity, null, null, null, null, null);
    }

    private static ListView viewOf(ModelAndView result) {
        return (ListView) result.getModel().get(PageChrome.VIEW_ATTRIBUTE);
    }

    private static PageChrome chromeOf(ModelAndView result) {
        return (PageChrome) result.getModel().get(PageChrome.ATTRIBUTE);
    }

    // ---- the page ---------------------------------------------------------------------------------------------

    @Test
    void aKnownEntityAnswersWithTheListTemplateAndTheContractsModelAttributes() {
        ModelAndView result = open("parcel");

        assertThat(result.getViewName()).as("a name would be offered to the host's view resolvers").isNull();
        assertThat(((Page) result.getView()).template()).isEqualTo(ListView.TEMPLATE).isEqualTo("jpa-admin/list");
        assertThat(result.getModel()).containsOnlyKeys(PageChrome.ATTRIBUTE, PageChrome.VIEW_ATTRIBUTE);
        assertThat(viewOf(result)).isInstanceOf(ListView.class);
    }

    @Test
    void theEntityIsMarkedInTheSidebarAndTheBreadcrumbsNameItsPlural() {
        PageChrome chrome = chromeOf(open("parcel"));

        assertThat(chrome.activeEntity()).isEqualTo("parcel");
        assertThat(chrome.breadcrumbs()).containsExactly(new PageChrome.Breadcrumb("Dashboard", "/admin"),
                new PageChrome.Breadcrumb("Parcels", null));
        assertThat(chrome.flash()).as("no notice any more: the table is real").isEmpty();
        assertThat(chrome.nav()).isNotEmpty();
        assertThat(chrome.user()).isEqualTo("admin");
    }

    @Test
    void theFirstPageHasTwentyFiveRowsOfSixtyAndTheEntityNames() {
        ListView view = viewOf(open("parcel"));

        assertThat(view.rows()).hasSize(25);
        assertThat(view.paging().total()).isEqualTo(60);
        assertThat(view.paging().page()).isZero();
        assertThat(view.paging().totalPages()).isEqualTo(3);
        assertThat(view.entity()).isEqualTo(new ListView.EntityInfo("Parcel", "Parcels", "table"));
        assertThat(view.empty().reason()).isEqualTo(ListView.Empty.Reason.NONE);
    }

    @Test
    void searchSortAndPagingFromTheAddressReachTheView() {
        ListView view = viewOf(open("parcel", "REF-1", "reference", "desc", "1", "10"));

        assertThat(view.search()).isEqualTo("REF-1");
        assertThat(view.sort()).isEqualTo("reference");
        assertThat(view.dir()).isEqualTo("desc");
        assertThat(view.paging().page()).isEqualTo(1);
        assertThat(view.paging().size()).isEqualTo(10);
        assertThat(view.paging().total()).as("REF-1, REF-10 to REF-19").isEqualTo(11);
        assertThat(view.rows()).hasSize(1);
    }

    @Test
    void thePageIsClampedToTheLastPage() {
        ListView view = viewOf(open("parcel", null, null, null, "99999999999999999999", "25"));

        assertThat(view.paging().page()).isEqualTo(2);
        assertThat(view.rows()).hasSize(10);
        assertThat(view.paging().nextHref()).isNull();
    }

    @Test
    void noActionIsOfferedYetNeitherExportNorNewNorBulkDelete() {
        assertThat(viewOf(open("parcel")).actions()).isEqualTo(new ListView.Actions(false, false, false, null));
    }

    // ---- bad parameters never fail ----------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"-1", "abc", "", " ", "1.5", "99999999999999999999", "0x10", "%00", "NaN", "٣"})
    void aBadPageNeverFailsThePage(String page) {
        ListView view = viewOf(open("parcel", null, null, null, page, null));

        assertThat(view.rows()).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "0", "7", "abc", "", "1000000", "99999999999999999999", "25.5", "%00"})
    void aBadSizeFallsBackToTheDefault(String size) {
        ListView view = viewOf(open("parcel", null, null, null, null, size));

        assertThat(view.paging().size()).isEqualTo(25);
        assertThat(view.rows()).hasSize(25);
    }

    @ParameterizedTest
    @ValueSource(strings = {"nosuch", "accessToken", "memo", "depot", "items", "' or 1=1 --", "", "reference desc", "<script>"})
    void aBadSortKeyIsIgnoredWithoutAnError(String key) {
        ListView view = viewOf(open("parcel", null, key, "desc", null, null));

        assertThat(view.sort()).isNull();
        assertThat(view.dir()).isNull();
        assertThat(view.columns()).allSatisfy(column -> assertThat(column.sortState())
                .isEqualTo(ListView.Column.SortState.NONE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"sideways", "", "1", "ASC; drop table parcel", "\u0000"})
    void aBadDirectionIsAscendingWithoutAnError(String dir) {
        ListView view = viewOf(open("parcel", null, "reference", dir, null, null));

        assertThat(view.dir()).isEqualTo("asc");
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "'", "\"", "\\", "http://x", "<script>alert(1)</script>", "\u0000", "\n",
            "'; drop table parcel; --", "😀", "%00", "!"})
    void anyTextAsASearchRendersAPageWithoutAnError(String q) {
        ListView view = viewOf(open("parcel", q, null, null, null, null));

        assertThat(view).isNotNull();
        assertThat(view.search().length()).isLessThanOrEqualTo(400);
        assertThat(view.search().codePointCount(0, view.search().length())).isLessThanOrEqualTo(100);
    }

    @Test
    void absurdlyLongValuesDoNotFailThePageEither() {
        ListView view = viewOf(open("parcel", "x".repeat(10_000), null, null, "9".repeat(5000), "9".repeat(5000)));

        assertThat(view.search()).hasSize(100);
        assertThat(view.paging().size()).isEqualTo(25);
        assertThat(view.empty().reason()).isEqualTo(ListView.Empty.Reason.NO_MATCHES);
        assertThat(viewOf(open("parcel", null, null, null, "9".repeat(5000), null)).paging().page()).isEqualTo(2);
    }

    @Test
    void everythingBadAtOnceStillRenders() {
        ListView view = viewOf(open("parcel", "x".repeat(5000), "nosuch", "sideways", "-5", "13"));

        assertThat(view.paging().size()).isEqualTo(25);
        assertThat(view.paging().page()).isZero();
        assertThat(view.sort()).isNull();
        assertThat(view.empty().reason()).isEqualTo(ListView.Empty.Reason.NO_MATCHES);
    }

    // ---- unknown and hidden entities --------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"nothing", "vault", "Vault", "PARCEL", "parcel2", "assets", "login", "", " ", "../etc/passwd"})
    void anUnknownOrHiddenEntityIsNotFoundWhateverTheOtherParameters(String entity) {
        assertThatThrownBy(() -> open(entity, "q", "reference", "desc", "1", "10"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(failure -> assertThat(((ResponseStatusException) failure).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void aHiddenEntityIsAnsweredExactlyLikeOneThatNeverExisted() {
        ResponseStatusException hidden = notFound("vault");
        ResponseStatusException unknown = notFound("there-is-no-such-entity");

        assertThat(hidden.getStatusCode()).isEqualTo(unknown.getStatusCode());
        assertThat(hidden.getReason()).isEqualTo(unknown.getReason()).isNull();
        assertThat(hidden.getMessage()).isEqualTo(unknown.getMessage());
    }

    private static ResponseStatusException notFound(String entity) {
        try {
            open(entity);
            throw new AssertionError("expected a 404");
        } catch (ResponseStatusException expected) {
            return expected;
        }
    }

    // ---- what the template gets -------------------------------------------------------------------------------

    @Test
    void theModelHoldsOnlyViewRecordsNeverAnEntityOrACollectionOfEntities() {
        ModelAndView result = open("parcel", "REF", "reference", "asc", "0", "50");

        for (Object value : result.getModel().values()) {
            assertThat(value.getClass().getName()).startsWith("io.github.mdmusahub.jpaadmin.web.view.");
        }
        assertThat(String.valueOf(result.getModel())).doesNotContain("testsupport.listmodel", "$HibernateProxy",
                "PersistentBag", "PersistentSet");
        ListView view = viewOf(result);
        for (ListView.Row row : view.rows()) {
            assertThat(row.cells()).allSatisfy(cell -> assertThat(cell).isInstanceOf(Cell.class));
        }
    }

    @Test
    void noSensitiveValueNorHiddenValueIsAnywhereInTheModel() {
        ModelAndView result = open("parcel", "needle-secret", null, null, null, "100");
        ModelAndView everything = open("parcel", null, null, null, null, "100");

        assertThat(viewOf(result).rows()).as("a sensitive value cannot be found by search").isEmpty();
        for (ModelAndView page : List.of(result, everything)) {
            String text = String.valueOf(page.getModel());
            assertThat(text).doesNotContain(SECRET).doesNotContain("memo needle");
        }
        assertThat(viewOf(everything).columns().stream().filter(column -> column.type() == Cell.Type.MASKED)
                .map(ListView.Column::key)).containsExactlyInAnyOrder("accessToken", "memo");
    }

    @Test
    void everyLinkOnThePageLiesUnderTheBasePathAndNoneNamesASensitiveColumn() {
        ListView view = viewOf(open("parcel", "a b/c'd\"e", "reference", "asc", "1", "10"));
        List<String> links = new java.util.ArrayList<>();
        view.columns().forEach(column -> links.add(column.sortHref()));
        links.add(view.paging().firstHref());
        links.add(view.paging().prevHref());
        links.add(view.paging().nextHref());
        links.add(view.paging().lastHref());
        view.paging().sizes().forEach(size -> links.add(size.href()));
        links.removeIf(link -> link == null);

        assertThat(links).isNotEmpty().allSatisfy(link -> {
            assertThat(link).startsWith("/admin/parcel");
            assertThat(link).doesNotContain(" ", "'", "\"", "accessToken", "memo");
        });
        assertThat(chromeOf(open("parcel")).breadcrumbs()).allSatisfy(crumb ->
                assertThat(crumb.href() == null || crumb.href().startsWith("/admin")).isTrue());
    }

    @Test
    void aRelationCellShowsTheTargetsLabelAndNoLink() {
        ListView view = viewOf(open("parcel"));
        int depot = indexOf(view, "depot");

        assertThat(view.rows()).allSatisfy(row -> {
            assertThat(row.cells().get(depot).type()).isEqualTo(Cell.Type.RELATION);
            assertThat(row.cells().get(depot).display()).isEqualTo("Central depot");
            assertThat(row.cells().get(depot).href()).isNull();
        });
    }

    private static int indexOf(ListView view, String key) {
        for (int i = 0; i < view.columns().size(); i++) {
            if (view.columns().get(i).key().equals(key)) {
                return i;
            }
        }
        throw new AssertionError(key);
    }
}

package io.github.mdmusahub.jpaadmin.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.metadata.DiscoverySettings;
import io.github.mdmusahub.jpaadmin.metadata.EntityScanner;
import io.github.mdmusahub.jpaadmin.metadata.testmodel.Gadget;
import io.github.mdmusahub.jpaadmin.metadata.testmodel.Widget;
import io.github.mdmusahub.jpaadmin.service.CountService;
import io.github.mdmusahub.jpaadmin.testsupport.TestPersistence;
import io.github.mdmusahub.jpaadmin.web.view.DashboardView;
import io.github.mdmusahub.jpaadmin.web.view.ErrorView;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;

/**
 * The pages Phase 3 adds: the dashboard built from real metadata and counts, the sidebar, and one entity's page.
 *
 * <p>Everything is built from the neutral test model through the real scanner (ARCH-12), so these tests also prove
 * that nothing in the console knows an entity name. The security side is here as well: a hidden, excluded or
 * unsupported entity answers exactly like one that never existed, and the name from the address is never echoed.
 */
class ConsoleEntityPagesTest {

    private record Page(String template, Locale locale) implements View {
        @Override
        public void render(Map<String, ?> model, HttpServletRequest request, HttpServletResponse response) {
            throw new UnsupportedOperationException("the test double renders nothing");
        }
    }

    private static final BasePath BASE = BasePath.of("/admin");
    private static final ConsoleSettings SETTINGS = new ConsoleSettings("Back office", "#0d9f6e");
    private static final CsrfTokens TOKENS = request -> new PageChrome.Csrf("_csrf", "X-CSRF-TOKEN", "token-123");

    private final AtomicInteger queries = new AtomicInteger();
    private final AtomicReference<Class<?>> failFor = new AtomicReference<>();

    private AdminEntityRegistry registry;
    private ConsoleNavigation navigation;
    private ConsoleChromeFactory chromes;
    private EntityController entities;
    private ConsoleController console;

    @BeforeEach
    void buildTheConsole() {
        wire(DiscoverySettings.defaults());
    }

    private void wire(DiscoverySettings settings) {
        registry = EntityScanner.scan(TestPersistence.factory().getMetamodel(), settings);
        CountService counts = CountService.over(TestPersistence.countingEntityManager(
                TestPersistence.entityManager(), queries, failFor::get), Clock.systemUTC());
        navigation = new ConsoleNavigation(registry, counts);
        chromes = new ConsoleChromeFactory(BASE, SETTINGS, TOKENS, navigation);
        ConsoleViews views = new ConsoleViews(Page::new);
        entities = new EntityController(chromes, views, navigation, BASE);
        console = new ConsoleController(chromes, views, SETTINGS, BASE, navigation);
    }

    private static MockHttpServletRequest signedIn() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setUserPrincipal((Principal) () -> "admin");
        return request;
    }

    private static Page pageOf(ModelAndView result) {
        assertThat(result.getViewName()).as("a name would be offered to the host's view resolvers").isNull();
        return (Page) result.getView();
    }

    private static PageChrome chromeOf(ModelAndView result) {
        return (PageChrome) result.getModel().get(PageChrome.ATTRIBUTE);
    }

    // ---- the sidebar ------------------------------------------------------------------------------------------

    @Test
    void theSidebarListsEveryEntityWithItsGroupLabelIconAndCount() {
        PageChrome chrome = chromes.chrome(signedIn(), List.of());

        assertThat(chrome.nav()).hasSize(1);
        PageChrome.NavGroup group = chrome.nav().get(0);
        assertThat(group.label()).isEqualTo("Entities");
        assertThat(group.items().stream().map(PageChrome.NavItem::entity))
                .containsExactlyElementsOf(registry.all().stream().map(AdminEntity::urlName).toList());
        PageChrome.NavItem widget = group.items().stream().filter(item -> item.entity().equals("widget"))
                .findFirst().orElseThrow();
        assertThat(widget.label()).isEqualTo("Widget");
        assertThat(widget.icon()).isEqualTo("table");
        assertThat(widget.count()).isNotNull().isNotNegative();
    }

    @Test
    void theSidebarOfAConsoleWithoutEntitiesIsEmptyAndCostsNoQuery() {
        ConsoleNavigation empty = ConsoleNavigation.empty();

        assertThat(empty.sidebar()).isEmpty();
        assertThat(empty.dashboard().groups()).isEmpty();
        assertThat(empty.dashboard().stats()).isEqualTo(new DashboardView.Stats(0, 0, 0, null));
        assertThat(queries).hasValue(0);
    }

    @Test
    void aHiddenOrExcludedEntityIsInNoSidebarAndOnNoDashboard() {
        wire(new DiscoverySettings(List.of("Thingamajig"), DiscoverySettings.DEFAULT_SENSITIVE_FIELDS));

        List<String> inTheSidebar = chromes.chrome(signedIn(), List.of()).nav().stream()
                .flatMap(group -> group.items().stream()).map(PageChrome.NavItem::entity).toList();
        List<String> onTheDashboard = navigation.dashboard().groups().stream()
                .flatMap(group -> group.entities().stream()).map(DashboardView.EntityCard::entity).toList();

        assertThat(inTheSidebar).doesNotContain("thingamajig", "doohickey", "contraption", "whatsit");
        assertThat(onTheDashboard).doesNotContain("thingamajig", "doohickey", "contraption", "whatsit");
        assertThat(inTheSidebar).contains("widget");
    }

    @Test
    void theSignedInUserIsOnThePageAndIsAbsentForAVisitorWhoIsNotSignedIn() {
        assertThat(chromes.chrome(signedIn(), List.of()).user()).isEqualTo("admin");
        assertThat(chromes.chrome(new MockHttpServletRequest(), List.of()).user()).isNull();
        assertThat(chromes.loginChrome(signedIn()).user())
                .as("the sign-in page never shows a user chip").isNull();
        assertThat(chromes.loginChrome(signedIn()).nav()).isEmpty();
    }

    // ---- the dashboard ---------------------------------------------------------------------------------------

    @Test
    void theDashboardAddsUpTheCountsAndNamesTheLargestTable() {
        ModelAndView result = console.dashboard(signedIn());
        DashboardView view = (DashboardView) result.getModel().get(PageChrome.VIEW_ATTRIBUTE);

        assertThat(pageOf(result).template()).isEqualTo(DashboardView.TEMPLATE);
        assertThat(view.stats().entityCount()).isEqualTo(registry.all().size());
        assertThat(view.stats().groupCount()).isEqualTo(1);
        assertThat(view.chartAvailable()).as("the chart arrives in Phase 10").isFalse();
        long sum = view.groups().stream().flatMap(group -> group.entities().stream())
                .filter(card -> card.count() != null).mapToLong(DashboardView.EntityCard::count).sum();
        assertThat(view.stats().totalRecords()).isEqualTo(sum);
        assertThat(view.stats().largestTable()).isNotNull();
        assertThat(view.stats().largestTable().count())
                .isEqualTo(view.groups().stream().flatMap(group -> group.entities().stream())
                        .filter(card -> card.count() != null).mapToLong(DashboardView.EntityCard::count).max()
                        .orElseThrow());
        assertThat(chromeOf(result).breadcrumbs())
                .containsExactly(new PageChrome.Breadcrumb("Dashboard", null));
    }

    @Test
    void anEntityWhoseCountFailedShowsNoNumberAndIsLeftOutOfTheTotals() {
        failFor.set(Widget.class);

        DashboardView view = navigation.dashboard();
        PageChrome.NavItem widget = chromes.chrome(signedIn(), List.of()).nav().get(0).items().stream()
                .filter(item -> item.entity().equals("widget")).findFirst().orElseThrow();

        DashboardView.EntityCard card = view.groups().get(0).entities().stream()
                .filter(entity -> entity.entity().equals("widget")).findFirst().orElseThrow();
        assertThat(card.count()).as("no number is better than a wrong one").isNull();
        assertThat(widget.count()).isNull();
        assertThat(view.groups().get(0).entities()).anySatisfy(other -> assertThat(other.count()).isNotNull());
        assertThat(view.stats().totalRecords()).isEqualTo(view.groups().stream()
                .flatMap(group -> group.entities().stream()).filter(entity -> entity.count() != null)
                .mapToLong(DashboardView.EntityCard::count).sum());
    }

    @Test
    void everyCountFailingStillGivesAPageWithNoLargestTable() {
        failFor.set(Object.class);
        CountService broken = CountService.unavailable(Clock.fixed(java.time.Instant.EPOCH, ZoneOffset.UTC));
        navigation = new ConsoleNavigation(registry, broken);

        DashboardView view = navigation.dashboard();

        assertThat(view.stats().totalRecords()).isZero();
        assertThat(view.stats().largestTable()).isNull();
        assertThat(view.groups().stream().flatMap(group -> group.entities().stream()))
                .allSatisfy(card -> assertThat(card.count()).isNull());
    }

    @Test
    void noActionIsOfferedForAnEntityYetSoNoPageShowsAButtonThatLeadsNowhere() {
        DashboardView view = navigation.dashboard();

        assertThat(view.groups().stream().flatMap(group -> group.entities().stream())
                .map(DashboardView.EntityCard::permissions).distinct())
                .containsExactly(new DashboardView.Permissions(false, false, false, false));
    }

    @Test
    void oneDashboardCostsOneQueryPerEntityAndTheSidebarOnTheSamePageCostsNothingMore() {
        queries.set(0);

        console.dashboard(signedIn());
        int afterTheDashboard = queries.get();
        chromes.chrome(signedIn(), List.of());

        assertThat(afterTheDashboard).isEqualTo(registry.all().size());
        assertThat(queries).hasValue(afterTheDashboard);
    }

    // ---- one entity's page ------------------------------------------------------------------------------------

    @Test
    void aKnownEntityAnswersWithTheShellTheNoticeAndItselfMarkedInTheSidebar() {
        ModelAndView result = entities.entity("widget", signedIn());

        assertThat(pageOf(result).template()).isEqualTo(EntityController.TEMPLATE)
                .isEqualTo("jpa-admin/entity-notice");
        PageChrome chrome = chromeOf(result);
        assertThat(chrome.activeEntity()).isEqualTo("widget");
        assertThat(chrome.flash()).containsExactly(new PageChrome.FlashMessage(
                PageChrome.FlashMessage.Level.INFO, "This table view is not available yet."));
        assertThat(chrome.breadcrumbs()).containsExactly(new PageChrome.Breadcrumb("Dashboard", "/admin"),
                new PageChrome.Breadcrumb("Widget", null));
        assertThat(result.getModel()).as("the page has nothing of its own to show yet")
                .doesNotContainKey(PageChrome.VIEW_ATTRIBUTE);
        assertThat(chrome.nav()).isNotEmpty();
    }

    @Test
    void everyLinkOnTheEntityPageLiesUnderTheBasePath() {
        PageChrome chrome = chromeOf(entities.entity("widget", signedIn()));

        assertThat(chrome.basePath()).isEqualTo("/admin");
        assertThat(chrome.breadcrumbs()).allSatisfy(crumb ->
                assertThat(crumb.href() == null || crumb.href().startsWith("/admin")).isTrue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"nothing", "doohickey", "contraption", "whatsit", "sprocket", "Widget", "WIDGET",
            "widget2", "internalNote", "assets", "login"})
    void anUnknownHiddenOrUnsupportedEntityAnswersTheSameNotFound(String typed) {
        assertThatThrownBy(() -> entities.entity(typed, signedIn()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(failure -> assertThat(((ResponseStatusException) failure).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void theAnswerForAHiddenEntityIsIdenticalToTheAnswerForAnEntityThatNeverExisted() {
        wire(new DiscoverySettings(List.of("Thingamajig"), DiscoverySettings.DEFAULT_SENSITIVE_FIELDS));

        ResponseStatusException hidden = catchNotFound("thingamajig");
        ResponseStatusException unknown = catchNotFound("there-is-no-such-entity");

        assertThat(hidden.getStatusCode()).isEqualTo(unknown.getStatusCode());
        assertThat(hidden.getReason()).isEqualTo(unknown.getReason()).isNull();
        assertThat(hidden.getMessage()).isEqualTo(unknown.getMessage());
        assertThat(ErrorViews.forStatus(hidden.getStatusCode().value()))
                .isEqualTo(ErrorViews.forStatus(unknown.getStatusCode().value()))
                .isEqualTo(new ErrorView(404, "Page not found", ErrorViews.forStatus(404).hint()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"<script>alert(1)</script>", "../../etc/passwd", "widget' or '1'='1",
            "%3Cscript%3E", "javascript:alert(1)", "\u0000widget"})
    void whatAVisitorTypedIsNeverPutIntoTheAnswer(String typed) {
        ResponseStatusException failure = catchNotFound(typed);

        assertThat(failure.getMessage()).doesNotContain(typed);
        assertThat(failure.getReason()).isNull();
        assertThat(String.valueOf(failure)).doesNotContain(typed);
    }

    private ResponseStatusException catchNotFound(String typed) {
        try {
            entities.entity(typed, signedIn());
            throw new AssertionError("expected a 404 for " + typed.length() + " characters");
        } catch (ResponseStatusException expected) {
            return expected;
        }
    }

    // ---- no sensitive value anywhere -------------------------------------------------------------------------

    @Test
    void noSensitiveFieldNameOrValueReachesThePages() {
        ModelAndView dashboard = console.dashboard(signedIn());
        ModelAndView page = entities.entity("widget", signedIn());

        for (ModelAndView result : List.of(dashboard, page)) {
            String rendered = String.valueOf(result.getModel());
            assertThat(rendered).doesNotContain("userPassword", "api_key", "recoveryAnswer", "internalNote");
        }
        assertThat(registry.find("widget").orElseThrow().fields())
                .anySatisfy(field -> assertThat(field.sensitive()).isTrue());
    }

    @Test
    void theGadgetEntityIsReachableToo() {
        assertThat(navigation.find("gadget")).isPresent();
        assertThat(navigation.find("gadget").orElseThrow().type()).isEqualTo(Gadget.class);
    }
}

package io.github.mdmusahub.jpaadmin.preview;

import io.github.mdmusahub.jpaadmin.web.ErrorViews;
import io.github.mdmusahub.jpaadmin.web.view.DashboardView;
import io.github.mdmusahub.jpaadmin.web.view.ErrorView;
import io.github.mdmusahub.jpaadmin.web.view.ListView;
import io.github.mdmusahub.jpaadmin.web.view.LoginView;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome.NavGroup;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome.NavItem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The page fixtures of {@code docs/FIXTURES.md}: the sign-in page (section 2), the dashboards (section 3), the list pages
 * (section 5, in {@link ListFixtures}) and the error pages (section 9). Each one is a fake record built through the real
 * constructor. The texts for the standard error pages come from {@link ErrorViews}, the same class that the real
 * console uses, so the preview shows what a visitor really sees.
 */
final class PageFixtures {

    private static final Map<String, LoginView> LOGIN = new LinkedHashMap<>();
    private static final Map<String, DashboardView> DASHBOARD = new LinkedHashMap<>();
    private static final Map<String, ErrorView> ERROR = new LinkedHashMap<>();

    /** What the console offers for an entity in Phase 3: nothing yet, so no card shows an action. */
    private static final DashboardView.Permissions PERMISSIONS =
            new DashboardView.Permissions(false, false, false, false);

    static {
        LOGIN.put("normal", new LoginView("JPA Admin", null, false));
        // The generic message: it never says which field was wrong.
        LOGIN.put("bad-credentials", new LoginView("JPA Admin", ErrorViews.SIGN_IN_FAILED, false));
        LOGIN.put("logged-out", new LoginView("JPA Admin", null, true));
        LOGIN.put("long-title", new LoginView(
                "Back office of the Example Company Regional Operations and Customer Support Team", null, false));

        // Zero values, like the dashboard of a console that found no entity at all.
        DASHBOARD.put("minimal", new DashboardView(new DashboardView.Stats(0, 0, 0, null), List.of(), false));
        DASHBOARD.put("normal", new DashboardView(
                new DashboardView.Stats(12_745, 8, 3, new DashboardView.LargestTable("Comments", 5_430)),
                groups(), true));
        DASHBOARD.put("no-chart", new DashboardView(
                new DashboardView.Stats(12_745, 8, 3, new DashboardView.LargestTable("Comments", 5_430)),
                groups(), false));
        // An empty database: entities are there, every count is zero, so there is no largest table.
        DASHBOARD.put("empty-database", new DashboardView(new DashboardView.Stats(0, 8, 3, null),
                groups(count -> 0L), false));
        // One count could not be read: the card says so and the entity is left out of the total.
        DASHBOARD.put("count-unavailable", new DashboardView(
                new DashboardView.Stats(7_315, 8, 3, new DashboardView.LargestTable("Comments", 5_430)),
                groups(count -> count == 1_204L ? null : count), false));
        DASHBOARD.put("no-entities", new DashboardView(new DashboardView.Stats(0, 0, 0, null), List.of(), false));
        DASHBOARD.put("many-groups", new DashboardView(
                new DashboardView.Stats(3_456_789, ChromeFixtures.LONG_NAVIGATION_ENTITIES, 8,
                        new DashboardView.LargestTable("Entity 8", 1_000_000)),
                manyGroups(), false));
        DASHBOARD.put("read-only-entities", new DashboardView(
                new DashboardView.Stats(12_745, 8, 3, new DashboardView.LargestTable("Comments", 5_430)),
                groups(), false));

        ERROR.put("not-found", ErrorViews.forStatus(404));
        ERROR.put("forbidden", ErrorViews.forStatus(403));
        ERROR.put("server-error", ErrorViews.forStatus(500));
        ERROR.put("bad-request", ErrorViews.forStatus(400));
        ERROR.put("method-not-allowed", ErrorViews.forStatus(405));
        ERROR.put("long-hint", new ErrorView(404, "Page not found",
                "This address does not exist in the console. " + "Check the address, or go to the dashboard and choose from there. "
                        .repeat(5) + "AVeryLongWordWithoutAnySpacesToSeeWhetherItBreaks" + "x".repeat(80)));
    }

    private PageFixtures() {
    }

    static Optional<LoginView> login(String state) {
        return Optional.ofNullable(LOGIN.get(state));
    }

    static Optional<DashboardView> dashboard(String state) {
        return Optional.ofNullable(DASHBOARD.get(state));
    }

    static Optional<ErrorView> error(String state) {
        return Optional.ofNullable(ERROR.get(state));
    }

    /** The list page for {@code state}, with its links under {@code basePath} (the base path of the chrome in use). */
    static Optional<ListView> list(String state, String basePath) {
        return ListFixtures.list(state, basePath);
    }

    /** The dashboard groups that match the default sidebar, with the counts the sidebar shows. */
    private static List<DashboardView.Group> groups() {
        return groups(count -> count);
    }

    private static List<DashboardView.Group> groups(java.util.function.LongFunction<Long> count) {
        List<DashboardView.Group> groups = new ArrayList<>();
        for (NavGroup group : ChromeFixtures.navigationForDashboard()) {
            List<DashboardView.EntityCard> cards = new ArrayList<>();
            for (NavItem item : group.items()) {
                cards.add(new DashboardView.EntityCard(item.entity(), item.label(), item.icon(),
                        count.apply(item.count()), PERMISSIONS));
            }
            groups.add(new DashboardView.Group(group.label(), cards));
        }
        return List.copyOf(groups);
    }

    private static List<DashboardView.Group> manyGroups() {
        List<DashboardView.Group> groups = new ArrayList<>();
        for (NavGroup group : ChromeFixtures.longNavigation()) {
            List<DashboardView.EntityCard> cards = new ArrayList<>();
            for (NavItem item : group.items()) {
                cards.add(new DashboardView.EntityCard(item.entity(), item.label(), item.icon(), item.count(),
                        PERMISSIONS));
            }
            groups.add(new DashboardView.Group(group.label(), cards));
        }
        return List.copyOf(groups);
    }

    /** Every page and the states it has, in display order. */
    static Map<String, Set<String>> catalog() {
        Map<String, Set<String>> catalog = new LinkedHashMap<>();
        catalog.put("login", LOGIN.keySet());
        catalog.put("dashboard", DASHBOARD.keySet());
        catalog.put("list", ListFixtures.states());
        catalog.put("error", ERROR.keySet());
        return catalog;
    }
}

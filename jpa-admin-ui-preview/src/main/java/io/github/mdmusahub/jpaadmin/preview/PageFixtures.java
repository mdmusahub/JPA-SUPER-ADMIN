package io.github.mdmusahub.jpaadmin.preview;

import io.github.mdmusahub.jpaadmin.web.ErrorViews;
import io.github.mdmusahub.jpaadmin.web.view.DashboardView;
import io.github.mdmusahub.jpaadmin.web.view.ErrorView;
import io.github.mdmusahub.jpaadmin.web.view.LoginView;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The page fixtures of {@code docs/FIXTURES.md} that task U0 needs: the sign-in page (section 2), the minimal dashboard
 * (a U0 addition to section 3) and the error pages (section 9). Each one is a fake record built through the real
 * constructor. The texts for the standard error pages come from {@link ErrorViews}, the same class that the real
 * console uses, so the preview shows what a visitor really sees.
 */
final class PageFixtures {

    private static final Map<String, LoginView> LOGIN = new LinkedHashMap<>();
    private static final Map<String, DashboardView> DASHBOARD = new LinkedHashMap<>();
    private static final Map<String, ErrorView> ERROR = new LinkedHashMap<>();

    static {
        LOGIN.put("normal", new LoginView("JPA Admin", null, false));
        // The generic message: it never says which field was wrong.
        LOGIN.put("bad-credentials", new LoginView("JPA Admin", ErrorViews.SIGN_IN_FAILED, false));
        LOGIN.put("logged-out", new LoginView("JPA Admin", null, true));
        LOGIN.put("long-title", new LoginView(
                "Back office of the Example Company Regional Operations and Customer Support Team", null, false));

        // Zero values, like the real Phase 2 dashboard: no groups, no chart.
        DASHBOARD.put("minimal", new DashboardView(new DashboardView.Stats(0, 0, 0, null), List.of(), false));

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

    /** Every page and the states it has, in display order. */
    static Map<String, Set<String>> catalog() {
        Map<String, Set<String>> catalog = new LinkedHashMap<>();
        catalog.put("login", LOGIN.keySet());
        catalog.put("dashboard", DASHBOARD.keySet());
        catalog.put("error", ERROR.keySet());
        return catalog;
    }
}

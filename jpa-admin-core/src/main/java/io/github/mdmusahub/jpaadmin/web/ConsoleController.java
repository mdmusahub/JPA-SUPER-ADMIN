package io.github.mdmusahub.jpaadmin.web;

import io.github.mdmusahub.jpaadmin.web.view.DashboardView;
import io.github.mdmusahub.jpaadmin.web.view.ErrorView;
import io.github.mdmusahub.jpaadmin.web.view.LoginView;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome.Breadcrumb;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.ModelAndView;

/**
 * The console's pages for Phase 2: the dashboard, the sign-in page and the "access denied" page.
 *
 * <p>Thin on purpose (ARCH-2, ARCH-3): it fills the frozen view-model records and returns them with the template (a
 * ready-made view, see {@link ConsoleViews}, so the host's view resolvers never see a console page). It reads no stored
 * data, and it never puts user text in a record. The sign-in page learns only whether the {@code error} or
 * {@code logout} query parameter is <em>present</em>; the messages themselves are fixed.
 *
 * <p>Mappings are relative to the console. The auto-configuration puts the configured base path in front of every
 * controller in this package, so the path is decided in one place (D-30). Signing in and out are done by Spring
 * Security's filters in the auto-configuration module, not by this controller.
 *
 * <p>Internal, not stable API. Public only so the auto-configuration module can create it.
 */
@Controller
public class ConsoleController {

    private final ConsoleChromeFactory chromeFactory;
    private final ConsoleViews views;
    private final ConsoleSettings settings;
    private final BasePath basePath;
    private final ConsoleNavigation navigation;

    public ConsoleController(ConsoleChromeFactory chromeFactory, ConsoleViews views, ConsoleSettings settings,
                             BasePath basePath, ConsoleNavigation navigation) {
        this.chromeFactory = Objects.requireNonNull(chromeFactory, "chromeFactory");
        this.views = Objects.requireNonNull(views, "views");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.basePath = Objects.requireNonNull(basePath, "basePath");
        this.navigation = Objects.requireNonNull(navigation, "navigation");
    }

    /**
     * {@code GET {base path}}: the dashboard. The numbers come from the entity metadata and the cached record
     * counts; a console that found no entity shows a welcome state instead of empty cards.
     */
    @GetMapping
    public ModelAndView dashboard(HttpServletRequest request) {
        PageChrome chrome = chromeFactory.chrome(request, List.of(new Breadcrumb("Dashboard", null)));
        return views.page(request, DashboardView.TEMPLATE, chrome, navigation.dashboard());
    }

    /**
     * {@code GET {base path}/login}: the sign-in page. {@code error} and {@code logout} are the markers that Spring
     * Security's redirects add (for example {@code /admin/login?error}); only their presence counts.
     */
    @GetMapping("/login")
    public ModelAndView login(HttpServletRequest request) {
        // Only whether the marker is present counts, and a bare "?error" has an empty value, which Spring MVC would hand
        // over as null, so the parameter map is asked instead. The value itself is never read.
        boolean failed = request.getParameterMap().containsKey("error");
        boolean signedOut = request.getParameterMap().containsKey("logout");
        // A failed attempt wins over a stale "signed out" marker: the visitor needs to see the error.
        LoginView view = new LoginView(settings.title(), failed ? ErrorViews.SIGN_IN_FAILED : null, !failed && signedOut);
        return views.page(request, LoginView.TEMPLATE, chromeFactory.loginChrome(request), view);
    }

    /**
     * The "access denied" page (403). The security chain forwards here when a request is refused, including a request
     * with a missing or wrong CSRF token, so the visitor sees the console's page and nothing from the exception. It
     * answers every HTTP method, because the refused request may be a POST.
     */
    @RequestMapping("/error/forbidden")
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ModelAndView forbidden(HttpServletRequest request) {
        ErrorView view = ErrorViews.forStatus(HttpStatus.FORBIDDEN.value());
        return views.page(request, ErrorView.TEMPLATE, chromeFactory.chrome(request,
                List.of(new Breadcrumb("Dashboard", basePath.value()), new Breadcrumb(view.heading(), null))), view);
    }
}

package io.github.mdmusahub.jpaadmin.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mdmusahub.jpaadmin.web.view.DashboardView;
import io.github.mdmusahub.jpaadmin.web.view.ErrorView;
import io.github.mdmusahub.jpaadmin.web.view.LoginView;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.HttpMethod;

/**
 * The console's controllers and helpers without Spring wiring: they fill only the frozen records, take nothing from user
 * text, carry no sensitive value (SEC-1), declare relative mappings, and the exception resolver answers only for the
 * console path (SEC-4). Every page comes with a ready-made view and never with a view name, so no view resolver of the
 * host can claim it (D-40).
 */
class ConsolePagesTest {

    /** A stand-in for the engine of the auto-configuration module: it remembers what it was asked for. */
    record Page(String template, Locale locale) implements View {
        @Override
        public void render(Map<String, ?> model, HttpServletRequest request, HttpServletResponse response) {
            throw new UnsupportedOperationException("the test double renders nothing");
        }
    }

    private static final ConsoleViewFactory FACTORY = Page::new;

    private static final BasePath BASE = BasePath.of("/admin");
    private static final ConsoleSettings SETTINGS = new ConsoleSettings("Back office", "#336699");
    private static final CsrfTokens TOKENS = request -> new PageChrome.Csrf("_csrf", "X-CSRF-TOKEN", "token-123");
    private final ConsoleChromeFactory chromes = new ConsoleChromeFactory(BASE, SETTINGS, TOKENS);
    private final ConsoleViews views = new ConsoleViews(FACTORY);
    private final ConsoleController controller = new ConsoleController(chromes, views, SETTINGS, BASE);

    private static Page pageOf(ModelAndView result) {
        assertThat(result.getViewName()).as("a name would be offered to the host's view resolvers").isNull();
        assertThat(result.isReference()).isFalse();
        return (Page) result.getView();
    }

    // ---- settings -----------------------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"red", "#fff", "#12345", "#1234567", "0d9f6e", "#0d9f6g", " #0d9f6e", "#0d9f6e ", "#0d9f6e\n",
            "#0d9f6e;}body{display:none", "url(x)", ""})
    void anAccentColourThatIsNotExactlyRrggbbIsRefusedWithoutBeingRepeated(String colour) {
        assertThatThrownBy(() -> new ConsoleSettings("t", colour)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("display").hasMessageNotContaining("url");
    }

    @Test
    void settingsAcceptAnyCaseOfAHexColour() {
        assertThat(new ConsoleSettings("t", "#ABCdef").primaryColor()).isEqualTo("#ABCdef");
    }

    // ---- chrome -------------------------------------------------------------------------------------------------------

    @Test
    void theChromeCarriesTheTitleBasePathTokenAnEmptySidebarAndNoFlash() {
        PageChrome chrome = chromes.chrome(new MockHttpServletRequest(), List.of());

        assertThat(chrome.title()).isEqualTo("Back office");
        assertThat(chrome.basePath()).isEqualTo("/admin");
        assertThat(chrome.theme()).isEqualTo(PageChrome.Theme.SYSTEM);
        assertThat(chrome.nav()).isEmpty();
        assertThat(chrome.flash()).isEmpty();
        assertThat(chrome.activeEntity()).isNull();
        assertThat(chrome.csrf().token()).isEqualTo("token-123");
    }

    // ---- controller ---------------------------------------------------------------------------------------------------

    @Test
    void theDashboardIsBuiltFromZeroValuesAndNeverFromStoredData() {
        ModelAndView result = controller.dashboard(new MockHttpServletRequest());

        assertThat(pageOf(result).template()).isEqualTo(DashboardView.TEMPLATE).isEqualTo("jpa-admin/dashboard");
        DashboardView view = (DashboardView) result.getModel().get(PageChrome.VIEW_ATTRIBUTE);
        assertThat(view.stats()).isEqualTo(new DashboardView.Stats(0, 0, 0, null));
        assertThat(view.groups()).isEmpty();
        assertThat(view.chartAvailable()).isFalse();
        PageChrome chrome = (PageChrome) result.getModel().get(PageChrome.ATTRIBUTE);
        assertThat(chrome.breadcrumbs()).containsExactly(new PageChrome.Breadcrumb("Dashboard", null));
    }

    private LoginView login(String... parameters) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        for (int i = 0; i + 1 < parameters.length; i += 2) {
            request.setParameter(parameters[i], parameters[i + 1]);
        }
        ModelAndView result = controller.login(request);
        assertThat(pageOf(result).template()).isEqualTo("jpa-admin/login");
        assertThat(((PageChrome) result.getModel().get(PageChrome.ATTRIBUTE)).nav()).isEmpty();
        return (LoginView) result.getModel().get(PageChrome.VIEW_ATTRIBUTE);
    }

    @Test
    void theSignInPageShowsAnErrorOrANoticeOnlyForThePresenceOfAMarker() {
        assertThat(login()).isEqualTo(new LoginView("Back office", null, false));
        assertThat(login("error", "")).isEqualTo(new LoginView("Back office", ErrorViews.SIGN_IN_FAILED, false));
        assertThat(login("logout", "")).isEqualTo(new LoginView("Back office", null, true));
        assertThat(login("error", "", "logout", "")).as("the error wins").isEqualTo(
                new LoginView("Back office", ErrorViews.SIGN_IN_FAILED, false));
    }

    @Test
    void userTextInAMarkerNeverReachesTheRecord() {
        LoginView view = login("error", "<script>alert(1)</script>", "logout", "Hacked");

        assertThat(view.error()).isEqualTo(ErrorViews.SIGN_IN_FAILED);
        assertThat(view.toString()).doesNotContain("script", "Hacked");
        assertThat(login("logout", "Hacked by me").toString()).doesNotContain("Hacked");
    }

    @Test
    void theSignInErrorNeverSaysWhichFieldWasWrong() {
        assertThat(ErrorViews.SIGN_IN_FAILED).containsIgnoringCase("username or password")
                .doesNotContainIgnoringCase("unknown").doesNotContainIgnoringCase("no such").doesNotContainIgnoringCase("not found");
    }

    @Test
    void theForbiddenPageHasAFixedTextTheBreadcrumbsUnderTheBasePathAndAnswers403() throws NoSuchMethodException {
        ModelAndView result = controller.forbidden(new MockHttpServletRequest());

        assertThat(pageOf(result).template()).isEqualTo(ErrorView.TEMPLATE);
        assertThat(result.getModel().get(PageChrome.VIEW_ATTRIBUTE)).isEqualTo(ErrorViews.forStatus(403));
        Method method = ConsoleController.class.getMethod("forbidden", HttpServletRequest.class);
        assertThat(method.getAnnotation(ResponseStatus.class).value()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(method.getAnnotation(RequestMapping.class).method()).as("answers every HTTP method").isEmpty();
    }

    @Test
    void controllersDeclareOnlyRelativeMappingsInThePackageTheAutoConfigurationPrefixes() throws NoSuchMethodException {
        for (Class<?> type : List.of(ConsoleController.class, ThemeController.class)) {
            assertThat(type.isAnnotationPresent(Controller.class)).isTrue();
            assertThat(type.getPackageName()).isEqualTo("io.github.mdmusahub.jpaadmin.web");
            assertThat(AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class)).as("no class-level path").isNull();
        }
        assertThat(ConsoleController.class.getMethod("dashboard", HttpServletRequest.class)
                .getAnnotation(GetMapping.class).value()).isEmpty();
        assertThat(ConsoleController.class.getMethod("login", HttpServletRequest.class)
                .getAnnotation(GetMapping.class).value()).containsExactly("/login");
    }

    // ---- the views ----------------------------------------------------------------------------------------------------

    @Test
    void everyPageUsesTheLocaleSpringMvcWouldHaveChosenForTheRequest() {
        MockHttpServletRequest acceptHeader = new MockHttpServletRequest();
        acceptHeader.addPreferredLocale(Locale.GERMANY);
        MockHttpServletRequest withResolver = new MockHttpServletRequest();
        withResolver.setAttribute(DispatcherServlet.LOCALE_RESOLVER_ATTRIBUTE, new FixedLocaleResolver(Locale.FRANCE));

        assertThat(pageOf(controller.dashboard(acceptHeader)).locale()).isEqualTo(Locale.GERMANY);
        assertThat(pageOf(controller.forbidden(acceptHeader)).locale()).isEqualTo(Locale.GERMANY);
        assertThat(pageOf(controller.login(withResolver)).locale()).as("the bound LocaleResolver wins").isEqualTo(Locale.FRANCE);
    }

    @Test
    void theViewsRefuseMissingParts() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        PageChrome chrome = chromes.chrome(request, List.of());

        assertThatThrownBy(() -> new ConsoleViews(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> views.page(request, ErrorView.TEMPLATE, null, "x")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> views.page(request, ErrorView.TEMPLATE, chrome, null)).isInstanceOf(NullPointerException.class);
    }

    // ---- theme.css ----------------------------------------------------------------------------------------------------

    @Test
    void themeCssIsExactlyOneVariableWithTheValidatedColourAndAShortPublicCache() {
        var response = new ThemeController(SETTINGS).themeCss();

        assertThat(response.getBody()).isEqualTo(":root { --ac: #336699; }\n");
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("text/css;charset=UTF-8");
        assertThat(response.getHeaders().getCacheControl()).contains("max-age=300").contains("public");
    }

    // ---- error texts ----------------------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(ints = {400, 403, 404, 405, 500, 401, 409, 415, 418, 502, 503})
    void everyErrorTextIsFixedSaysWhatToDoAndHoldsNoInternals(int status) {
        ErrorView view = ErrorViews.forStatus(status);

        assertThat(view.status()).isEqualTo(status);
        assertThat(view.heading()).isNotBlank();
        assertThat(view.hint()).isNotBlank().containsAnyOf("Try again", "try again", "Reload", "Go to", "Go back", "Check");
        assertThat((view.heading() + view.hint()).toLowerCase()).doesNotContain("exception", "sql", "stack", "org.", "java.");
    }

    // ---- the exception resolver -----------------------------------------------------------------------------------------

    private final ConsoleExceptionResolver resolver = new ConsoleExceptionResolver(BASE, chromes, views);

    private ModelAndView resolve(String uri, String contextPath, Exception exception, MockHttpServletResponse response) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", contextPath + uri);
        request.setContextPath(contextPath);
        return resolver.resolveException(request, response, null, exception);
    }

    private static Exception notFound(String uri) {
        return new NoResourceFoundException(HttpMethod.GET, uri, uri);
    }

    @Test
    void aMissingConsolePageGetsTheConsolesNotFoundPageWith404() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ModelAndView result = resolve("/admin/nope", "", notFound("/admin/nope"), response);

        assertThat(pageOf(result).template()).isEqualTo("jpa-admin/error");
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(result.getModel().get(PageChrome.VIEW_ATTRIBUTE)).isEqualTo(ErrorViews.forStatus(404));
        PageChrome chrome = (PageChrome) result.getModel().get(PageChrome.ATTRIBUTE);
        assertThat(chrome.breadcrumbs()).extracting(PageChrome.Breadcrumb::label).containsExactly("Dashboard", "Page not found");
        assertThat(chrome.breadcrumbs().get(0).href()).isEqualTo("/admin");
    }

    @Test
    void anUnexpectedFailureInTheConsoleGetsTheGenericPage500AndNoMessage() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ModelAndView result = resolve("/admin/people", "", new IllegalStateException("could not execute: select * from secret_table"), response);

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(result.getModel().toString()).doesNotContain("select", "secret_table", "IllegalState");
        assertThat(result.getModel().get(PageChrome.VIEW_ATTRIBUTE)).isEqualTo(ErrorViews.forStatus(500));
    }

    @Test
    void otherStatusesFromSpringMvcKeepTheirCode() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        resolve("/admin", "", new ErrorResponseException(HttpStatus.METHOD_NOT_ALLOWED), response);

        assertThat(response.getStatus()).isEqualTo(405);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/hello", "/", "/administrator", "/admin-old/x", "/api/data", "/other/admin", "/assets/css/x.css",
            "/admin/assets/css/missing.css", "/admin/assets", "/admin/assets/"})
    void everyRequestOutsideTheConsolePagesIsLeftToTheHostsOwnErrorHandling(String uri) {
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(resolve(uri, "", notFound(uri), response)).as(uri).isNull();
        assertThat(resolve(uri, "", new IllegalStateException("x"), response)).as(uri).isNull();
        assertThat(response.getStatus()).as("nothing was written").isEqualTo(200);
    }

    @Test
    void theServletContextPathIsIgnoredWhenDecidingWhetherARequestIsTheConsoles() {
        assertThat(resolve("/admin/nope", "/shop", notFound("/admin/nope"), new MockHttpServletResponse())).isNotNull();
        assertThat(resolve("/hello", "/shop", notFound("/hello"), new MockHttpServletResponse())).isNull();
        assertThat(resolve("/shop/admin/nope", "", notFound("/x"), new MockHttpServletResponse())).isNull();
    }

    @Test
    void aSecurityExceptionBelongsToSpringSecurityAndIsPassedOn() {
        assertThat(resolve("/admin/people", "", new org.springframework.security.fake.FakeAccessDeniedException(),
                new MockHttpServletResponse())).isNull();
    }

    @Test
    void aCommittedResponseIsLeftAlone() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setCommitted(true);

        assertThat(resolve("/admin/people", "", new IllegalStateException(), response)).isNull();
    }

    @Test
    void itRunsBeforeTheDefaultResolvers() {
        assertThat(resolver.getOrder()).isEqualTo(org.springframework.core.Ordered.HIGHEST_PRECEDENCE);
    }
}

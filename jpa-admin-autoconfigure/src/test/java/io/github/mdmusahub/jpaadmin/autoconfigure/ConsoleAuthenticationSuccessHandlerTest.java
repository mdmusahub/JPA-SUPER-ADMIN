package io.github.mdmusahub.jpaadmin.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.web.BasePath;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

/**
 * No open redirect after sign-in (SEC-4, and the brief): the browser goes to the dashboard, or back to the page it was
 * refused on, and never anywhere a request parameter says. A saved request is used only when it was a GET under the
 * console path, and it is rebuilt as a relative path, so it cannot leave the site.
 */
class ConsoleAuthenticationSuccessHandlerTest {

    private static final String SAVED_KEY = JpaAdminSecurityConfiguration.SAVED_REQUEST_KEY;

    private final BasePath basePath = BasePath.of("/admin");
    private final HttpSessionRequestCache requests = cache();
    private final ConsoleAuthenticationSuccessHandler handler = new ConsoleAuthenticationSuccessHandler(basePath, requests);

    private static HttpSessionRequestCache cache() {
        HttpSessionRequestCache cache = new HttpSessionRequestCache();
        cache.setSessionAttrName(SAVED_KEY);
        return cache;
    }

    /** A session in which the console's chain has saved the page that the visitor first asked for. */
    private MockHttpSession sessionWithSavedRequest(String method, String contextPath, String uri, String query) {
        MockHttpServletRequest original = new MockHttpServletRequest(method, contextPath + uri);
        original.setContextPath(contextPath);
        original.setQueryString(query);
        MockHttpSession session = new MockHttpSession();
        original.setSession(session);
        requests.saveRequest(original, new MockHttpServletResponse());
        return session;
    }

    private String redirectAfterSignIn(MockHttpSession session, String contextPath, String... parameters)
            throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", contextPath + "/admin/login");
        request.setContextPath(contextPath);
        request.setSession(session);
        for (int i = 0; i + 1 < parameters.length; i += 2) {
            request.setParameter(parameters[i], parameters[i + 1]);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(request, response, UsernamePasswordAuthenticationToken.authenticated("a", "b", null));
        return response.getRedirectedUrl();
    }

    // ---- the normal cases -----------------------------------------------------------------------------------------

    @Test
    void goesToTheDashboardWhenNothingWasSaved() throws IOException {
        assertThat(redirectAfterSignIn(new MockHttpSession(), "")).isEqualTo("/admin");
    }

    @Test
    void goesBackToThePageThatWasRefusedWhenItLiesUnderTheConsole() throws IOException {
        MockHttpSession session = sessionWithSavedRequest("GET", "", "/admin/people", "page=2&q=a%20b");

        assertThat(redirectAfterSignIn(session, "")).isEqualTo("/admin/people?page=2&q=a%20b");
    }

    @Test
    void keepsTheServletContextPathInFrontOfTheTarget() throws IOException {
        MockHttpSession session = sessionWithSavedRequest("GET", "/shop", "/admin/people", null);

        assertThat(redirectAfterSignIn(session, "/shop")).isEqualTo("/shop/admin/people");
    }

    @Test
    void usesTheSavedRequestOnlyOnce() throws IOException {
        MockHttpSession session = sessionWithSavedRequest("GET", "", "/admin/people", null);

        assertThat(redirectAfterSignIn(session, "")).isEqualTo("/admin/people");
        assertThat(session.getAttribute(SAVED_KEY)).isNull();
        assertThat(redirectAfterSignIn(session, "")).isEqualTo("/admin");
    }

    // ---- a saved request that must not be followed ------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"/host/orders", "/", "/administrator", "/admin-old/page", "/other", "/api/data"})
    void ignoresASavedPageOutsideTheConsolePathAndForgetsIt(String uri) throws IOException {
        MockHttpSession session = sessionWithSavedRequest("GET", "", uri, null);

        assertThat(redirectAfterSignIn(session, "")).isEqualTo("/admin");
        assertThat(session.getAttribute(SAVED_KEY)).as("forgotten, not kept for later").isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE", "PATCH"})
    void ignoresASavedRequestThatWasNotAGet(String method) throws IOException {
        MockHttpSession session = sessionWithSavedRequest(method, "", "/admin/people", null);

        assertThat(redirectAfterSignIn(session, "")).isEqualTo("/admin");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin/login", "/admin/login/", "/admin/login/x", "/admin/error/forbidden"})
    void neverSendsTheVisitorBackToThePagesThatWouldLoop(String uri) throws IOException {
        MockHttpSession session = sessionWithSavedRequest("GET", "", uri, null);

        assertThat(redirectAfterSignIn(session, "")).isEqualTo("/admin");
    }

    @ParameterizedTest
    @ValueSource(strings = {"//evil.example/admin", "///admin/x", "/admin//evil.example", "/admin/a//b",
            "/admin/x\\evil.example", "/admin/x y", "/admin/%0d%0aSet-Cookie:x=y"})
    void ignoresATargetWithADoubleSlashABackslashASpaceOrAnEscapedLineBreak(String uri) throws IOException {
        MockHttpSession session = sessionWithSavedRequest("GET", "", uri, null);

        String target = redirectAfterSignIn(session, "");
        assertThat(target).as(uri).satisfiesAnyOf(
                value -> assertThat(value).isEqualTo("/admin"),
                // A percent-escaped line break is plain text to a browser, so it may stay (it cannot split a header).
                value -> assertThat(value).isEqualTo("/admin/%0d%0aSet-Cookie:x=y"));
        assertThat(target).doesNotContain("evil.example").doesNotContain("\r").doesNotContain("\n");
    }

    @ParameterizedTest
    @ValueSource(strings = {"a=b\r\nSet-Cookie: x=y", "a=b\nx", "a=\"quoted\"", "a=<script>", "a=b c", "a=b\\c", "a=b#frag"})
    void ignoresAQueryWithACharacterThatCouldSplitAHeaderOrBreakOutOfAnAttribute(String query) throws IOException {
        MockHttpSession session = sessionWithSavedRequest("GET", "", "/admin/people", query);

        assertThat(redirectAfterSignIn(session, "")).isEqualTo("/admin");
    }

    @Test
    void ignoresAnAbsurdlyLongTarget() throws IOException {
        MockHttpSession session = sessionWithSavedRequest("GET", "", "/admin/people", "q=" + "a".repeat(3000));

        assertThat(redirectAfterSignIn(session, "")).isEqualTo("/admin");
    }

    @Test
    void aQueryThatContainsAnAddressStaysAnOrdinaryRelativeTargetOnThisSite() throws IOException {
        // The visitor asked for /admin/people?next=https://evil.example. Following it goes to this site's own /admin/people.
        MockHttpSession session = sessionWithSavedRequest("GET", "", "/admin/people", "next=https://evil.example/x");

        String target = redirectAfterSignIn(session, "");

        assertThat(target).isEqualTo("/admin/people?next=https://evil.example/x").startsWith("/admin/");
    }

    // ---- a request parameter is never a target ----------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({"redirect,https://evil.example", "continue,https://evil.example", "next,//evil.example", "returnTo,/host/orders",
            "url,https://evil.example/admin", "redirect_uri,https://evil.example", "target,javascript:alert(1)",
            "successUrl,https://evil.example", "returnUrl,/admin/people", "goto,/admin/people"})
    void neverReadsATargetFromARequestParameter(String name, String value) throws IOException {
        assertThat(redirectAfterSignIn(new MockHttpSession(), "", name, value)).isEqualTo("/admin");
    }

    @ParameterizedTest
    @CsvSource({"redirect,https://evil.example", "continue,https://evil.example", "next,//evil.example"})
    void aParameterDoesNotOverrideASavedPageEither(String name, String value) throws IOException {
        MockHttpSession session = sessionWithSavedRequest("GET", "", "/admin/people", null);

        assertThat(redirectAfterSignIn(session, "", name, value)).isEqualTo("/admin/people");
    }

    @Test
    void theRedirectIsAlwaysRelativeToThisSite() throws IOException {
        for (String uri : new String[] {"/admin/people", "/host/orders", "//evil.example/x"}) {
            String target = redirectAfterSignIn(sessionWithSavedRequest("GET", "", uri, null), "");
            assertThat(target).startsWith("/admin").doesNotContain("://").doesNotStartWith("//");
        }
    }
}

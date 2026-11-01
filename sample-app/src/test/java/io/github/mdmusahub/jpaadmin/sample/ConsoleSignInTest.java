package io.github.mdmusahub.jpaadmin.sample;

import static io.github.mdmusahub.jpaadmin.sample.MockBrowser.PASSWORD;
import static io.github.mdmusahub.jpaadmin.sample.MockBrowser.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Phase 2 acceptance on the sample app (a host without Spring Security): redirect to sign-in, bad sign-in rejected, good
 * sign-in works, sign-out works only as a POST with the CSRF token, and the failure never says which field was wrong.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConsoleSignInTest {

    @Autowired
    private MockMvc mvc;

    private MockBrowser browser() {
        return new MockBrowser(mvc);
    }

    // ---- who may see what -------------------------------------------------------------------------------------------

    @Test
    void anUnauthenticatedVisitToTheConsoleRedirectsToTheSignInPage() throws Exception {
        MockHttpServletResponse response = browser().get("/admin");

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getHeader("Location")).isEqualTo("/admin/login");
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    void headRequestsAreRedirectedToo() throws Exception {
        assertThat(mvc.perform(head("/admin")).andReturn().getResponse().getStatus()).isEqualTo(302);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin/people", "/admin/anything/at/all", "/admin/logout", "/admin/error/forbidden",
            "/admin/api/stats", "/admin/files/x", "/admin/uploads", "/admin/new", "/admin/1/edit"})
    void everyOtherConsolePageNeedsSignIn(String path) throws Exception {
        MockHttpServletResponse response = browser().get(path);

        assertThat(response.getStatus()).as(path).isEqualTo(302);
        assertThat(response.getHeader("Location")).isEqualTo("/admin/login");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin", "/admin/people", "/admin/uploads", "/admin/people/1/delete", "/admin/bulk-delete"})
    void aPostWithoutATokenIsRefusedAndAPostWithATokenButNoSignInIsSentToSignIn(String path) throws Exception {
        MockBrowser browser = browser();
        assertThat(browser.post(path).getStatus()).as("no token").isEqualTo(403);

        String token = MockBrowser.csrf(browser.get("/admin/login").getContentAsString());
        MockHttpServletResponse withToken = browser.post(path, "_csrf", token);
        assertThat(withToken.getStatus()).as("token but not signed in").isEqualTo(302);
        assertThat(withToken.getHeader("Location")).isEqualTo("/admin/login");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin/login", "/admin/assets/css/tokens.css", "/admin/assets/js/theme.js", "/admin/theme.css"})
    void theSignInPageAssetsAndThemeArePublic(String path) throws Exception {
        assertThat(browser().get(path).getStatus()).isEqualTo(200);
    }

    // ---- bad sign-in ------------------------------------------------------------------------------------------------

    private MockHttpServletResponse attempt(MockBrowser browser, String user, String password) throws Exception {
        return browser.signInToConsole("/admin", user, password);
    }

    @Test
    void aWrongUsernameAndAWrongPasswordGetExactlyTheSameAnswer() throws Exception {
        MockHttpServletResponse wrongUser = attempt(browser(), "nobody", PASSWORD);
        MockHttpServletResponse wrongPassword = attempt(browser(), USER, "not-the-password");
        MockHttpServletResponse bothWrong = attempt(browser(), "nobody", "not-the-password");

        for (MockHttpServletResponse response : new MockHttpServletResponse[] {wrongUser, wrongPassword, bothWrong}) {
            assertThat(response.getStatus()).isEqualTo(302);
            assertThat(response.getHeader("Location")).isEqualTo("/admin/login?error");
            assertThat(response.getContentAsString()).isEmpty();
        }
        assertThat(wrongUser.getHeaderNames()).isEqualTo(wrongPassword.getHeaderNames());
        assertThat(wrongUser.getHeaderNames()).isEqualTo(bothWrong.getHeaderNames());
    }

    @Test
    void theErrorPageShowsOneGenericMessageAndNeverSaysWhichFieldWasWrong() throws Exception {
        MockBrowser browser = browser();
        attempt(browser, "nobody", PASSWORD);
        String page = browser.get("/admin/login?error").getContentAsString();

        assertThat(page).contains("The username or password is not correct. Check both and try again.")
                .contains("role=\"alert\"").doesNotContain("nobody").doesNotContain("Unknown user")
                .doesNotContainIgnoringCase("bad credentials");
        MockBrowser other = browser();
        attempt(other, USER, "wrong-password");
        String normalised = "value=\"[^\"]*\"";
        assertThat(other.get("/admin/login?error").getContentAsString().replaceAll(normalised, "v"))
                .as("the page after a wrong password is the page after a wrong username")
                .isEqualTo(page.replaceAll(normalised, "v"));
    }

    @Test
    void usernameAndPasswordAreMatchedExactly() throws Exception {
        assertThat(attempt(browser(), USER.toUpperCase(), PASSWORD).getHeader("Location")).endsWith("/login?error");
        assertThat(attempt(browser(), USER, PASSWORD.toUpperCase()).getHeader("Location")).endsWith("/login?error");
        assertThat(attempt(browser(), "", "").getHeader("Location")).endsWith("/login?error");
    }

    @ParameterizedTest
    @ValueSource(ints = {72, 73, 10_000})
    void aVeryLongPasswordIsRefusedCleanlyNotWithAServerError(int length) throws Exception {
        MockHttpServletResponse response = attempt(browser(), USER, "x".repeat(length));

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getHeader("Location")).endsWith("/login?error");
    }

    // ---- good sign-in -----------------------------------------------------------------------------------------------

    @Test
    void aGoodSignInGoesToTheDashboardAndTheDashboardRenders() throws Exception {
        MockBrowser browser = browser();

        MockHttpServletResponse signedIn = attempt(browser, USER, PASSWORD);
        MockHttpServletResponse dashboard = browser.get("/admin");

        assertThat(signedIn.getStatus()).isEqualTo(302);
        assertThat(signedIn.getHeader("Location")).isEqualTo("/admin");
        assertThat(dashboard.getStatus()).isEqualTo(200);
        assertThat(dashboard.getContentAsString()).contains("<h1 class=\"page-title\">Dashboard</h1>",
                "Welcome to <span>JPA Admin</span>", "action=\"/admin/logout\"", "<span>Sign out</span>");
    }

    @Test
    void theSessionIdChangesAtSignInSoAnIdPlantedBeforeSignInIsUseless() throws Exception {
        MockBrowser browser = browser();
        browser.get("/admin/login");
        String before = browser.session().getId();

        attempt(browser, USER, PASSWORD);

        assertThat(browser.session().getId()).isNotEqualTo(before);
    }

    @Test
    void theTitleOfTheConsoleComesFromTheProperty() throws Exception {
        assertThat(browser().get("/admin/login").getContentAsString()).contains("<title>Sign in | JPA Admin</title>");
    }

    // ---- sign out ---------------------------------------------------------------------------------------------------

    @Test
    void signOutIsAPostWithTheTokenItEndsTheSessionAndShowsTheNotice() throws Exception {
        MockBrowser browser = MockBrowser.signedIn(mvc);
        String token = MockBrowser.csrf(browser.get("/admin").getContentAsString());
        var before = browser.session();

        MockHttpServletResponse out = browser.post("/admin/logout", "_csrf", token);

        assertThat(out.getStatus()).isEqualTo(302);
        assertThat(out.getHeader("Location")).isEqualTo("/admin/login?logout");
        assertThat(before.isInvalid()).as("the session is invalidated").isTrue();
        assertThat(browser.get("/admin").getStatus()).isEqualTo(302);
        String page = browser.get("/admin/login?logout").getContentAsString();
        assertThat(page).contains("You have been signed out.").doesNotContain("role=\"alert\"");
    }

    @Test
    void aGetToTheLogoutAddressDoesNotSignOut() throws Exception {
        MockBrowser browser = MockBrowser.signedIn(mvc);

        MockHttpServletResponse response = browser.get("/admin/logout");

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(browser.get("/admin").getStatus()).as("still signed in").isEqualTo(200);
        assertThat(mvc.perform(head("/admin/logout").session(browser.session())).andReturn().getResponse().getStatus())
                .isNotEqualTo(302);
    }

    @Test
    void signOutWithoutTheTokenOrWithAWrongOneDoesNothing() throws Exception {
        MockBrowser browser = MockBrowser.signedIn(mvc);
        browser.get("/admin");

        assertThat(browser.post("/admin/logout").getStatus()).isEqualTo(403);
        assertThat(browser.post("/admin/logout", "_csrf", "not-the-token").getStatus()).isEqualTo(403);
        assertThat(browser.get("/admin").getStatus()).as("still signed in").isEqualTo(200);
    }

    @Test
    void signingInAgainAfterSigningOutWorks() throws Exception {
        MockBrowser browser = MockBrowser.signedIn(mvc);
        browser.post("/admin/logout", "_csrf", MockBrowser.csrf(browser.get("/admin").getContentAsString()));

        assertThat(attempt(browser, USER, PASSWORD).getStatus()).isEqualTo(302);
        assertThat(browser.get("/admin").getStatus()).isEqualTo(200);
    }

    @Test
    void signingOutKeepsAHostsStateInTheSameSessionAndEndsOnlyAConsoleOnlySession() throws Exception {
        MockBrowser shared = browser();
        shared.get("/cart/add");
        attempt(shared, USER, PASSWORD);
        String token = MockBrowser.csrf(shared.get("/admin").getContentAsString());
        var session = shared.session();

        MockHttpServletResponse out = shared.post("/admin/logout", "_csrf", token);

        assertThat(out.getHeader("Location")).isEqualTo("/admin/login?logout");
        assertThat(session.isInvalid()).as("the host's cart keeps the session").isFalse();
        assertThat(shared.get("/cart").getContentAsString()).isEqualTo("cart: 3 items");
        assertThat(shared.get("/admin").getStatus()).as("signed out of the console").isEqualTo(302);

        MockBrowser consoleOnly = MockBrowser.signedIn(mvc);
        var before = consoleOnly.session();
        consoleOnly.post("/admin/logout", "_csrf", MockBrowser.csrf(consoleOnly.get("/admin").getContentAsString()));
        assertThat(before.isInvalid()).as("nothing but console state: ended").isTrue();
    }

    // ---- notices ------------------------------------------------------------------------------------------------------

    @Test
    void theNoticeAndTheErrorComeOnlyFromTheMarkersAndNeverFromUserText() throws Exception {
        MockBrowser browser = browser();

        String error = browser.get("/admin/login?error=<script>alert(1)</script>").getContentAsString();
        String both = browser.get("/admin/login?error&logout").getContentAsString();
        String notice = browser.get("/admin/login?logout=Hacked%20by%20me").getContentAsString();
        String plain = browser.get("/admin/login").getContentAsString();

        assertThat(error).doesNotContain("alert(1)").contains("The username or password is not correct");
        assertThat(both).contains("The username or password is not correct").doesNotContain("signed out");
        assertThat(notice).contains("You have been signed out.").doesNotContain("Hacked");
        assertThat(plain).doesNotContain("role=\"alert\"").doesNotContain("signed out");
    }

    @Test
    void anUnknownConsoleAddressShowsTheConsolesOwnNotFoundPageToASignedInAdmin() throws Exception {
        MockHttpServletResponse response = MockBrowser.signedIn(mvc).get("/admin/no/such/page");

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentAsString()).contains("<h1>Page not found</h1>", "Go to dashboard")
                .doesNotContain("Exception").doesNotContain("Whitelabel");
    }
}

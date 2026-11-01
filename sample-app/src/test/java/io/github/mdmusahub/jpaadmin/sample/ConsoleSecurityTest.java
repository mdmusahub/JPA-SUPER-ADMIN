package io.github.mdmusahub.jpaadmin.sample;

import static io.github.mdmusahub.jpaadmin.sample.MockBrowser.PASSWORD;
import static io.github.mdmusahub.jpaadmin.sample.MockBrowser.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/**
 * SEC-5, SEC-6, SEC-12 and SEC-13 on the sample app: response headers on console pages and only there, CSRF failures
 * shown as the console's own page with no detail, no password or hash anywhere (also in the log at TRACE level), and no
 * redirect target taken from the request.
 */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(properties = {"logging.level.org.springframework.security=TRACE", "logging.level.io.github.mdmusahub=TRACE"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class ConsoleSecurityTest {

    private static final String CSP = "default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
            + "connect-src 'self'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'";
    private static final String WRONG_USER = "nobody-ZZ-5521";
    private static final String WRONG_PASSWORD = "WrongPw-ZZ-5521";
    private static final Pattern BCRYPT = Pattern.compile("\\$2[aby]?\\$\\d\\d\\$[./A-Za-z0-9]{53}");

    @Autowired
    private MockMvc mvc;

    // ---- SEC-13: headers ----------------------------------------------------------------------------------------------

    private static void assertConsoleHeaders(MockHttpServletResponse response, String what) {
        assertThat(response.getHeader("X-Content-Type-Options")).as(what).isEqualTo("nosniff");
        assertThat(response.getHeader("X-Frame-Options")).as(what).isEqualTo("DENY");
        assertThat(response.getHeader("Referrer-Policy")).as(what).isEqualTo("no-referrer");
        assertThat(response.getHeader("Content-Security-Policy")).as(what).isEqualTo(CSP);
        assertThat(response.getHeader("Strict-Transport-Security")).as(what + ": HSTS belongs to the host").isNull();
    }

    @Test
    void everyConsolePageCarriesTheSecurityHeadersAndIsNotStored() throws Exception {
        MockBrowser browser = MockBrowser.signedIn(mvc);
        String token = MockBrowser.csrf(browser.get("/admin").getContentAsString());
        List<MockHttpServletResponse> pages = List.of(new MockBrowser(mvc).get("/admin/login"),
                new MockBrowser(mvc).get("/admin/login?error"), new MockBrowser(mvc).get("/admin"), browser.get("/admin"),
                browser.get("/admin/nope"), browser.get("/admin/error/forbidden"), browser.post("/admin"),
                browser.post("/admin/logout"), browser.post("/admin/people", "_csrf", token));

        for (MockHttpServletResponse page : pages) {
            String what = page.getStatus() + " " + page.getHeaderNames();
            assertConsoleHeaders(page, what);
            assertThat(page.getHeader("Cache-Control")).as(what).contains("no-store");
        }
    }

    @Test
    void assetsAndTheThemeStylesheetAreCachedBrieflyAndPublicly() throws Exception {
        for (String path : new String[] {"/admin/assets/css/tokens.css", "/admin/assets/js/theme.js", "/admin/theme.css"}) {
            MockHttpServletResponse response = new MockBrowser(mvc).get(path);

            assertConsoleHeaders(response, path);
            assertThat(response.getHeader("Cache-Control")).as(path).contains("max-age=300").contains("public")
                    .doesNotContain("no-store");
        }
    }

    @Test
    void noHstsEvenOverHttps() throws Exception {
        MockHttpServletResponse response = mvc.perform(get("/admin/login").secure(true)).andReturn().getResponse();

        assertThat(response.getHeader("Strict-Transport-Security")).isNull();
        assertConsoleHeaders(response, "https");
    }

    @Test
    void theHostsOwnRoutesGetNoneOfTheConsoleHeaders() throws Exception {
        for (String path : new String[] {"/host-marker.txt", "/hello", "/api/data", "/nothing-here", "/administrator"}) {
            MockHttpServletResponse response = mvc.perform(get(path)).andReturn().getResponse();

            assertThat(response.getHeaderNames()).as(path).doesNotContain("Content-Security-Policy", "Referrer-Policy",
                    "X-Frame-Options", "X-Content-Type-Options", "Cache-Control", "Pragma", "Expires",
                    "Strict-Transport-Security");
        }
    }

    // ---- SEC-5, SEC-12: CSRF -----------------------------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource(value = {"/admin/login|username,x,password,y", "/admin/login|username,x,password,y,_csrf,not-a-real-token",
            "/admin/login|username,x,password,y,_csrf,", "/admin/logout|_csrf,not-a-real-token"}, delimiter = '|')
    void aPostWithoutAValidTokenIsRefusedAndShowsTheConsolesOwnPageNotAnError(String path, String fields) throws Exception {
        MockBrowser browser = MockBrowser.signedIn(mvc);

        MockHttpServletResponse response = browser.post(path, fields.split(",", -1));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getForwardedUrl()).isEqualTo("/admin/error/forbidden");
        assertThat(browser.get("/admin").getStatus()).as("a refused sign-out leaves the session alone").isEqualTo(200);
    }

    @Test
    void theForbiddenPageHasAFixedTextAndNoInternalDetail() throws Exception {
        MockBrowser browser = MockBrowser.signedIn(mvc);
        MockHttpServletResponse page = browser.get("/admin/error/forbidden");

        assertThat(page.getStatus()).isEqualTo(403);
        assertThat(page.getContentAsString()).contains("<h1>Access denied</h1>", "Reload the page and try again")
                .doesNotContain("Exception", "csrf", "CSRF", "InvalidCsrf", "org.springframework", "at io.github", "stack");
        assertThat(new MockBrowser(mvc).get("/admin/error/forbidden").getStatus()).as("signed out: sent to sign-in").isEqualTo(302);
    }

    @Test
    void scriptsMayPresentTheTokenInAHeader() throws Exception {
        MockBrowser browser = MockBrowser.signedIn(mvc);
        String token = MockBrowser.csrf(browser.get("/admin").getContentAsString());

        MockHttpServletResponse out = browser.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/admin/logout").header("X-CSRF-TOKEN", token));

        assertThat(out.getHeader("Location")).isEqualTo("/admin/login?logout");
    }

    // ---- SEC-6: secrets ------------------------------------------------------------------------------------------------

    @Test
    void neitherThePasswordNorItsHashNorAWrongAttemptAppearsInAnyResponseRedirectHeaderSessionOrLog(CapturedOutput output)
            throws Exception {
        List<MockHttpServletResponse> responses = new ArrayList<>();
        MockBrowser wrong = new MockBrowser(mvc);
        responses.add(wrong.signInToConsole("/admin", WRONG_USER, WRONG_PASSWORD));
        responses.add(wrong.get("/admin/login?error"));
        MockBrowser browser = new MockBrowser(mvc);
        responses.add(browser.get("/admin/login"));
        responses.add(browser.signInToConsole("/admin", USER, PASSWORD));
        responses.add(browser.get("/admin"));
        responses.add(browser.get("/admin/nope"));
        responses.add(browser.post("/admin/logout"));
        responses.add(browser.get("/admin/error/forbidden"));
        responses.add(browser.get("/admin/theme.css"));
        responses.add(browser.get("/admin/assets/js/theme.js"));
        List<String> everythingSeen = new ArrayList<>();
        for (MockHttpServletResponse response : responses) {
            everythingSeen.add(response.getContentAsString());
            everythingSeen.add(String.valueOf(response.getRedirectedUrl()));
            everythingSeen.add(String.valueOf(response.getForwardedUrl()));
            for (String name : response.getHeaderNames()) {
                everythingSeen.add(name + ": " + response.getHeaders(name));
            }
        }
        for (var session : List.of(wrong.session(), browser.session())) {
            Collection<String> names = java.util.Collections.list(session.getAttributeNames());
            for (String name : names) {
                everythingSeen.add(name + "=" + session.getAttribute(name));
            }
        }
        everythingSeen.add(output.getAll());

        String all = String.join("\n", everythingSeen);
        assertThat(all).doesNotContain(PASSWORD).doesNotContain(WRONG_PASSWORD).doesNotContain(WRONG_USER);
        assertThat(BCRYPT.matcher(all).find()).as("no BCrypt hash anywhere").isFalse();
        assertThat(output.getAll()).as("the log at TRACE level was really captured").contains("Securing POST /admin/login");
    }

    // ---- no redirect target from the request --------------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({"redirect,https://evil.example/", "continue,https://evil.example/", "next,//evil.example/",
            "returnTo,https://evil.example/", "url,https://evil.example/", "redirect_uri,https://evil.example/",
            "successUrl,https://evil.example/", "target,/host-marker.txt"})
    void aSignInWithARedirectParameterStillGoesToTheDashboard(String name, String value) throws Exception {
        MockBrowser browser = new MockBrowser(mvc);
        String token = MockBrowser.csrf(browser.get("/admin/login").getContentAsString());

        MockHttpServletResponse inBody = browser.post("/admin/login", "username", USER, "password", PASSWORD, "_csrf", token, name, value);
        MockHttpServletResponse inUrl = new MockBrowser(mvc).perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/admin/login?" + name + "=" + value).param("username", USER).param("password", PASSWORD));

        assertThat(inBody.getHeader("Location")).isEqualTo("/admin");
        assertThat(inUrl.getStatus()).as("no token: refused, and still not followed").isEqualTo(403);
        assertThat(inUrl.getHeader("Location")).isNull();
    }

    @Test
    void theRefusedPageIsRememberedAndFollowedAfterSignInButOnlyAsALocalPath() throws Exception {
        MockBrowser browser = new MockBrowser(mvc);
        MockHttpServletResponse refused = browser.perform(get("/admin/people?page=2").header("Host", "evil.example"));
        assertThat(refused.getStatus()).isEqualTo(302);

        MockHttpServletResponse signedIn = browser.signInToConsole("/admin", USER, PASSWORD);

        assertThat(signedIn.getHeader("Location")).isEqualTo("/admin/people?page=2");
    }

    @Test
    void aPostIsNotRememberedSoNothingIsReplayedAfterSignIn() throws Exception {
        MockBrowser browser = new MockBrowser(mvc);
        String token = MockBrowser.csrf(browser.get("/admin/login").getContentAsString());
        browser.post("/admin/people/1/delete", "_csrf", token);

        assertThat(browser.signInToConsole("/admin", USER, PASSWORD).getHeader("Location")).isEqualTo("/admin");
    }
}

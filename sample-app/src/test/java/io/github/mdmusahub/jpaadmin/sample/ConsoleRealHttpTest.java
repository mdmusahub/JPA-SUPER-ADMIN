package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * The same promises as the MockMvc tests, over real HTTP against embedded Tomcat, the way a browser sees them: the real
 * servlet filter registration, the real session cookie, the real forward to the console's "access denied" page.
 */
@ExtendWith(OutputCaptureExtension.class)
class ConsoleRealHttpTest {

    private static final String PASSWORD = "Zx9-fake-strong-pw-QWERTY-7731";
    private static final String NO_CONFIG_FILES = "--spring.config.name=jpa-admin-no-such-config";

    private static ConfigurableApplicationContext start(String... extra) {
        String[] all = Stream.concat(Stream.of("--server.port=0", "--spring.main.banner-mode=off", NO_CONFIG_FILES,
                "--jpa.admin.username=admin", "--jpa.admin.password=" + PASSWORD), Stream.of(extra)).toArray(String[]::new);
        return SpringApplication.run(SampleApplication.class, all);
    }

    /** A browser: keeps cookies, follows nothing. */
    private static final class Browser {
        final CookieManager cookies = new CookieManager();
        final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).followRedirects(HttpClient.Redirect.NEVER).build();
        final String base;

        Browser(ConfigurableApplicationContext context) {
            this.base = "http://localhost:" + context.getEnvironment().getProperty("local.server.port");
        }

        HttpResponse<String> get(String path) throws Exception {
            return client.send(HttpRequest.newBuilder(URI.create(base + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> post(String path, String... fields) throws Exception {
            String body = new ArrayList<>(List.of(fields)).isEmpty() ? "" : form(fields);
            return client.send(HttpRequest.newBuilder(URI.create(base + path))
                    .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                    HttpResponse.BodyHandlers.ofString());
        }

        String sessionId() {
            return cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("JSESSIONID"))
                    .map(c -> c.getValue()).findFirst().orElse(null);
        }

        HttpResponse<String> signIn(String user, String password) throws Exception {
            return post("/admin/login", "username", user, "password", password, "_csrf", token(get("/admin/login").body()));
        }
    }

    private static String form(String... fields) {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i + 1 < fields.length; i += 2) {
            body.append(i > 0 ? "&" : "").append(URLEncoder.encode(fields[i], StandardCharsets.UTF_8)).append('=')
                    .append(URLEncoder.encode(fields[i + 1], StandardCharsets.UTF_8));
        }
        return body.toString();
    }

    private static String token(String html) {
        Matcher matcher = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(html);
        assertThat(matcher.find()).as("a CSRF field in the page").isTrue();
        return matcher.group(1);
    }

    // ---- sign in over real HTTP -------------------------------------------------------------------------------------

    @Test
    void signInChangesTheSessionIdAndTheCookieHasOnlyWhatABootHostWouldGiveIt(CapturedOutput output) throws Exception {
        try (ConfigurableApplicationContext context = start()) {
            Browser browser = new Browser(context);
            HttpResponse<String> hostSession = browser.get("/session");
            String hostCookie = hostSession.headers().firstValue("Set-Cookie").orElseThrow();
            Browser console = new Browser(context);

            HttpResponse<String> page = console.get("/admin/login");
            String before = console.sessionId();
            HttpResponse<String> signedIn = console.post("/admin/login", "username", "admin", "password", PASSWORD,
                    "_csrf", token(page.body()));

            assertThat(signedIn.statusCode()).isEqualTo(302);
            assertThat(console.sessionId()).as("the session id changes at sign-in").isNotNull().isNotEqualTo(before);
            assertThat(console.get("/admin").statusCode()).isEqualTo(200);
            // SEC-8: cookie attributes are the host's own. Compare the attribute names of the console's cookie with the
            // cookie of a host route that starts a session.
            String consoleCookie = page.headers().firstValue("Set-Cookie").orElseThrow();
            assertThat(attributeNames(consoleCookie)).isEqualTo(attributeNames(hostCookie)).containsExactly("httponly", "path");
        }
        assertThat(output.getAll()).doesNotContain(PASSWORD);
    }

    private static TreeSet<String> attributeNames(String setCookie) {
        return java.util.Arrays.stream(setCookie.split(";")).skip(1).map(part -> part.strip().split("=")[0].toLowerCase())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    @Test
    void aRefusedPostShowsTheConsolesForbiddenPageWithNoDetail() throws Exception {
        try (ConfigurableApplicationContext context = start()) {
            Browser browser = new Browser(context);
            browser.signIn("admin", PASSWORD);

            HttpResponse<String> noToken = browser.post("/admin/logout", "x", "y");
            HttpResponse<String> badToken = browser.post("/admin/logout", "_csrf", "wrong");
            HttpResponse<String> loginNoToken = new Browser(context).post("/admin/login", "username", "admin", "password", "x");

            for (HttpResponse<String> response : List.of(noToken, badToken, loginNoToken)) {
                assertThat(response.statusCode()).isEqualTo(403);
                assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("text/html");
                assertThat(response.body()).contains("<h1>Access denied</h1>")
                        .doesNotContain("Exception", "org.springframework", "CSRF", "csrf", "at io.github");
                assertThat(response.headers().firstValue("Content-Security-Policy")).isPresent();
            }
            assertThat(browser.get("/admin").statusCode()).as("still signed in").isEqualTo(200);
        }
    }

    @Test
    void signOutOverRealHttpEndsTheSession() throws Exception {
        try (ConfigurableApplicationContext context = start()) {
            Browser browser = new Browser(context);
            browser.signIn("admin", PASSWORD);

            HttpResponse<String> out = browser.post("/admin/logout", "_csrf", token(browser.get("/admin").body()));

            assertThat(out.statusCode()).isEqualTo(302);
            assertThat(out.headers().firstValue("Location").orElseThrow()).endsWith("/admin/login?logout");
            assertThat(browser.get("/admin").statusCode()).isEqualTo(302);
            assertThat(browser.get("/admin/login?logout").body()).contains("You have been signed out.");
        }
    }

    @Test
    void signOutOverRealHttpKeepsTheHostsSessionStateAndTheSameSessionId() throws Exception {
        try (ConfigurableApplicationContext context = start()) {
            Browser browser = new Browser(context);
            browser.get("/cart/add");
            browser.signIn("admin", PASSWORD);
            String idAfterSignIn = browser.sessionId();

            HttpResponse<String> out = browser.post("/admin/logout", "_csrf", token(browser.get("/admin").body()));

            assertThat(out.statusCode()).isEqualTo(302);
            assertThat(out.headers().allValues("Set-Cookie")).as("the session is not ended, so no new cookie").isEmpty();
            assertThat(browser.sessionId()).isEqualTo(idAfterSignIn);
            assertThat(browser.get("/cart").body()).as("the host's state survived").isEqualTo("cart: 3 items");
            assertThat(browser.get("/admin").statusCode()).as("signed out of the console").isEqualTo(302);
        }
    }

    @Test
    void signOutOfAConsoleOnlySessionEndsItOverRealHttp() throws Exception {
        try (ConfigurableApplicationContext context = start()) {
            Browser browser = new Browser(context);
            browser.signIn("admin", PASSWORD);
            String id = browser.sessionId();

            browser.post("/admin/logout", "_csrf", token(browser.get("/admin").body()));
            HttpResponse<String> next = browser.get("/admin/login");

            assertThat(next.headers().firstValue("Set-Cookie").orElseThrow()).as("a new session starts")
                    .startsWith("JSESSIONID=").doesNotContain(id);
        }
    }

    // ---- the host is the same with the console off and on --------------------------------------------------------------

    private static Map<String, String> hostAnswers(ConfigurableApplicationContext context) throws Exception {
        Browser browser = new Browser(context);
        Map<String, String> answers = new LinkedHashMap<>();
        for (String path : new String[] {"/hello", "/api/data", "/host-marker.txt", "/nothing-here", "/administrator",
                "/admin-old/x", "/assets/css/tokens.css", "/jpa-admin/assets/css/tokens.css", "/theme.css", "/login", "/logout"}) {
            HttpResponse<String> response = browser.get(path);
            answers.put("GET " + path, response.statusCode() + " "
                    + new TreeSet<>(response.headers().map().keySet().stream().map(String::toLowerCase)
                            .filter(name -> !name.equals("date") && !name.equals("content-length")).toList())
                    + " " + response.body().length() + " " + response.headers().firstValue("Content-Type").orElse(""));
        }
        HttpResponse<String> post = browser.post("/hello", "a", "b");
        answers.put("POST /hello", post.statusCode() + " " + new TreeSet<>(post.headers().map().keySet()).size());
        return answers;
    }

    @Test
    void everyHostRouteAnswersTheSameWithTheConsoleOffAndOnOverRealHttp() throws Exception {
        Map<String, String> off;
        Map<String, String> on;
        try (ConfigurableApplicationContext context = start("--jpa.admin.enabled=false")) {
            off = hostAnswers(context);
        }
        try (ConfigurableApplicationContext context = start()) {
            on = hostAnswers(context);
        }

        assertThat(on).isEqualTo(off);
        assertThat(on.get("GET /hello")).startsWith("200 ");
        assertThat(on.get("GET /api/data")).startsWith("200 ");
        assertThat(on.get("GET /host-marker.txt")).startsWith("200 ");
    }

    @Test
    void theHostsOwnRoutesAreNotPutBehindSignInAndGetNoGeneratedPassword(CapturedOutput output) throws Exception {
        try (ConfigurableApplicationContext context = start()) {
            Browser browser = new Browser(context);

            assertThat(browser.get("/hello").body()).isEqualTo("hello from the host");
            assertThat(browser.get("/api/data").statusCode()).isEqualTo(200);
            assertThat(browser.get("/hello").headers().map()).doesNotContainKeys("set-cookie", "content-security-policy");
        }
        assertThat(output.getAll()).doesNotContain("Using generated security password");
    }
}

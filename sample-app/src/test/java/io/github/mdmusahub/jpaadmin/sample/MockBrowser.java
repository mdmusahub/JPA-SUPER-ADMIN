package io.github.mdmusahub.jpaadmin.sample;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * A browser for MockMvc tests: it keeps one session between requests and sends the session id the way a cookie does
 * (so Spring Security's session-fixation protection acts as it does for a real browser), follows nothing on its own,
 * and starts a fresh session when the server invalidates the old one.
 */
final class MockBrowser {

    private static final Pattern TOKEN_AFTER_NAME = Pattern.compile("name=\"_csrf\"[^>]*?value=\"([^\"]+)\"");
    private static final Pattern TOKEN_BEFORE_NAME = Pattern.compile("value=\"([^\"]+)\"[^>]*?name=\"_csrf\"");

    private final MockMvc mvc;
    private MockHttpSession session = new MockHttpSession();

    MockBrowser(MockMvc mvc) {
        this.mvc = mvc;
    }

    MockHttpSession session() {
        return session;
    }

    MockHttpServletResponse get(String path) throws Exception {
        return perform(MockMvcRequestBuilders.get(path));
    }

    /** POST of a form: {@code fields} are name, value, name, value ... */
    MockHttpServletResponse post(String path, String... fields) throws Exception {
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.post(path);
        for (int i = 0; i + 1 < fields.length; i += 2) {
            request.param(fields[i], fields[i + 1]);
        }
        return perform(request);
    }

    MockHttpServletResponse perform(MockHttpServletRequestBuilder request) throws Exception {
        if (session.isInvalid()) {
            session = new MockHttpSession();
        }
        request.session(session).with(each -> {
            each.setRequestedSessionId(session.getId());
            return each;
        });
        return mvc.perform(request).andReturn().getResponse();
    }

    /** The CSRF token in a rendered form, whichever order the attributes are in. */
    static String csrf(String html) {
        Matcher after = TOKEN_AFTER_NAME.matcher(html);
        if (after.find()) {
            return after.group(1);
        }
        Matcher before = TOKEN_BEFORE_NAME.matcher(html);
        if (before.find()) {
            return before.group(1);
        }
        throw new AssertionError("The page has no CSRF token field");
    }

    static final String USER = "test-admin";
    static final String PASSWORD = "test-only-password-NOT-REAL-1";

    /** A browser already signed in to the console at {@code /admin} with the test credentials. */
    static MockBrowser signedIn(MockMvc mvc) throws Exception {
        MockBrowser browser = new MockBrowser(mvc);
        browser.signInToConsole("/admin", USER, PASSWORD);
        return browser;
    }

    static MockBrowser signedIn(MockMvc mvc, String base) throws Exception {
        MockBrowser browser = new MockBrowser(mvc);
        browser.signInToConsole(base, USER, PASSWORD);
        return browser;
    }

    /** Opens the console's sign-in page, reads the token and posts the credentials. Returns the response to the post. */
    MockHttpServletResponse signInToConsole(String base, String username, String password) throws Exception {
        String token = csrf(get(base + "/login").getContentAsString());
        return post(base + "/login", "username", username, "password", password, "_csrf", token);
    }
}

package io.github.mdmusahub.jpaadmin.autoconfigure;

import static io.github.mdmusahub.jpaadmin.autoconfigure.ConsoleTestHosts.CONSOLE_PASSWORD;
import static io.github.mdmusahub.jpaadmin.autoconfigure.ConsoleTestHosts.CONSOLE_USER;
import static io.github.mdmusahub.jpaadmin.autoconfigure.ConsoleTestHosts.HOST_PASSWORD;
import static io.github.mdmusahub.jpaadmin.autoconfigure.ConsoleTestHosts.HOST_USER;
import static io.github.mdmusahub.jpaadmin.autoconfigure.ConsoleTestHosts.mvc;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The console works inside every kind of host, and the console and the host do not share a login: a session that is
 * signed in to one is not signed in to the other (SEC-4). The browser has one session per site, and it is shared with
 * the host, so everything the console keeps in it is stored under its own names.
 */
class ConsoleInsideHostsTest {

    private static final String HOST_CONTEXT_KEY = "SPRING_SECURITY_CONTEXT";
    private static final String CONSOLE_CONTEXT_KEY = "JPA_ADMIN_SECURITY_CONTEXT";
    private static final String HOST_CSRF_KEY = "org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository.CSRF_TOKEN";
    private static final String CONSOLE_CSRF_KEY = "JPA_ADMIN_CSRF_TOKEN";
    private static final String CONSOLE_SAVED_REQUEST_KEY = "JPA_ADMIN_SAVED_REQUEST";
    private static final String HOST_SAVED_REQUEST_KEY = "SPRING_SECURITY_SAVED_REQUEST";

    @RestController
    static class HostRoutes {

        @GetMapping("/hello")
        String hello() {
            return "hello from the host";
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(HostRoutes.class)
    static class HostRelyingOnBootsDefaultChain {
    }

    private static final String[] BOOT_DEFAULT_USER = {"spring.security.user.name=" + HOST_USER,
            "spring.security.user.password=" + HOST_PASSWORD};

    private static WebApplicationContextRunner bootHost() {
        return ConsoleTestHosts.withBootSecurity().withUserConfiguration(HostRelyingOnBootsDefaultChain.class)
                .withPropertyValues(BOOT_DEFAULT_USER);
    }

    private static Set<String> attributeNames(MockHttpSession session) {
        return new TreeSet<>(Collections.list(session.getAttributeNames()));
    }

    // ---- the console works beside Spring Boot's default chain ----------------------------------------------------

    @Test
    void theConsoleSignsInBesideBootsDefaultChainAndTheHostKeepsItsOwnLoginPage() {
        bootHost().run(context -> {
            MockBrowser browser = new MockBrowser(mvc(context));

            assertThat(browser.get("/admin").getStatus()).isEqualTo(302);
            assertThat(browser.get("/admin").getHeader("Location")).endsWith("/admin/login");
            assertThat(browser.get("/admin/login").getContentAsString()).contains("<h1>Sign in</h1>").contains("name=\"username\"");
            assertThat(browser.signInToConsole("/admin", CONSOLE_USER, CONSOLE_PASSWORD).getHeader("Location")).endsWith("/admin");
            MockHttpServletResponse dashboard = browser.get("/admin");
            assertThat(dashboard.getStatus()).isEqualTo(200);
            assertThat(dashboard.getContentAsString()).contains("Welcome to <span>JPA Admin</span>");
            // The host's own login page is the host's, not ours.
            assertThat(new MockBrowser(mvc(context)).get("/login").getContentAsString()).contains("Please sign in")
                    .doesNotContain("JPA Admin");
        });
    }

    // ---- the two logins do not mix --------------------------------------------------------------------------------

    @Test
    void theHostsUsersCannotSignInToTheConsoleAndTheConsolesAdminCannotSignInToTheHost() {
        bootHost().run(context -> {
            MockBrowser console = new MockBrowser(mvc(context));
            MockHttpServletResponse refused = console.signInToConsole("/admin", HOST_USER, HOST_PASSWORD);
            assertThat(refused.getStatus()).isEqualTo(302);
            assertThat(refused.getHeader("Location")).endsWith("/admin/login?error");
            assertThat(console.get("/admin").getStatus()).as("still signed out").isEqualTo(302);

            MockBrowser host = new MockBrowser(mvc(context));
            String token = MockBrowser.csrf(host.get("/login").getContentAsString());
            MockHttpServletResponse hostLogin = host.post("/login", "username", CONSOLE_USER, "password", CONSOLE_PASSWORD,
                    "_csrf", token);
            assertThat(hostLogin.getHeader("Location")).endsWith("/login?error");
            assertThat(host.get("/hello").getStatus()).as("still signed out of the host").isIn(302, 401);
        });
    }

    @Test
    void aSessionSignedInToTheHostIsNotSignedInToTheConsole() {
        bootHost().run(context -> {
            MockBrowser browser = new MockBrowser(mvc(context));
            String token = MockBrowser.csrf(browser.get("/login").getContentAsString());
            browser.post("/login", "username", HOST_USER, "password", HOST_PASSWORD, "_csrf", token);
            assertThat(browser.get("/hello").getStatus()).as("signed in to the host").isEqualTo(200);

            MockHttpServletResponse console = browser.get("/admin");

            // Not "access denied": the host's user is simply nobody to the console, so it sends them to its own sign-in.
            assertThat(console.getStatus()).isEqualTo(302);
            assertThat(console.getHeader("Location")).endsWith("/admin/login");
        });
    }

    @Test
    void aSessionSignedInToTheConsoleIsNotSignedInToTheHost() {
        bootHost().run(context -> {
            MockBrowser browser = new MockBrowser(mvc(context));
            browser.signInToConsole("/admin", CONSOLE_USER, CONSOLE_PASSWORD);
            assertThat(browser.get("/admin").getStatus()).as("signed in to the console").isEqualTo(200);

            MockHttpServletResponse host = browser.get("/hello");

            assertThat(host.getStatus()).isIn(302, 401);
            assertThat(host.getContentAsString()).doesNotContain("hello from the host");
        });
    }

    @Test
    void signingInToTheConsoleDoesNotOverwriteTheHostsLoginRotateItsCsrfTokenOrReplayItsSavedRequest() {
        bootHost().run(context -> {
            MockBrowser browser = new MockBrowser(mvc(context));
            // A host page that needs login is requested first, so the host's chain saves the request in the session.
            browser.get("/hello");
            MockHttpSession session = browser.session();
            assertThat(session.getAttribute(HOST_SAVED_REQUEST_KEY)).as("the host saved its request").isNotNull();
            String token = MockBrowser.csrf(browser.get("/login").getContentAsString());
            browser.post("/login", "username", HOST_USER, "password", HOST_PASSWORD, "_csrf", token);
            browser.get("/login"); // the host's token is created when one of its pages first shows it
            Object hostContext = session.getAttribute(HOST_CONTEXT_KEY);
            Object hostToken = session.getAttribute(HOST_CSRF_KEY);
            assertThat(hostContext).isNotNull();
            assertThat(hostToken).isNotNull();
            session.setAttribute(HOST_SAVED_REQUEST_KEY, "a-request-the-host-saved");

            MockHttpServletResponse signedIn = browser.signInToConsole("/admin", CONSOLE_USER, CONSOLE_PASSWORD);

            assertThat(signedIn.getHeader("Location")).as("not sent to the host's saved page").endsWith("/admin");
            assertThat(session.getAttribute(HOST_CONTEXT_KEY)).as("the host's login is untouched").isSameAs(hostContext);
            assertThat(((SecurityContext) session.getAttribute(HOST_CONTEXT_KEY)).getAuthentication().getName())
                    .isEqualTo(HOST_USER);
            assertThat(session.getAttribute(HOST_CSRF_KEY)).as("the host's CSRF token is not rotated").isEqualTo(hostToken);
            assertThat(session.getAttribute(HOST_SAVED_REQUEST_KEY)).as("the host's saved request stays").isEqualTo("a-request-the-host-saved");
            assertThat(((SecurityContext) session.getAttribute(CONSOLE_CONTEXT_KEY)).getAuthentication().getName())
                    .isEqualTo(CONSOLE_USER);
            browser.get("/admin"); // the console's own token is created when a console page first shows it
            assertThat(session.getAttribute(CONSOLE_CSRF_KEY)).isNotNull().isNotEqualTo(hostToken);
            assertThat(session.getAttribute(HOST_CSRF_KEY)).as("and the host's token is still the host's").isEqualTo(hostToken);
        });
    }

    @Test
    void theConsoleKeepsItsSecurityContextCsrfTokenAndSavedRequestUnderItsOwnNames() {
        ConsoleTestHosts.withoutSpringSecurity().withUserConfiguration(HostRelyingOnBootsDefaultChain.class).run(context -> {
            MockBrowser browser = new MockBrowser(mvc(context));
            browser.get("/admin/people");
            assertThat(attributeNames(browser.session())).contains(CONSOLE_SAVED_REQUEST_KEY).doesNotContain(HOST_SAVED_REQUEST_KEY);
            browser.signInToConsole("/admin", CONSOLE_USER, CONSOLE_PASSWORD);
            browser.get("/admin"); // the token is created when a page first shows it

            Set<String> names = attributeNames(browser.session());

            assertThat(names).contains(CONSOLE_CONTEXT_KEY, CONSOLE_CSRF_KEY);
            assertThat(names).doesNotContain(HOST_CONTEXT_KEY, HOST_CSRF_KEY, HOST_SAVED_REQUEST_KEY);
            assertThat(names).as("everything the console stores is namespaced").allSatisfy(name -> {
                if (!name.startsWith("JPA_ADMIN_")) {
                    // The only other attribute allowed is the one that the mock servlet container itself may add.
                    assertThat(name).startsWith("org.springframework.web.context.request");
                }
            });
        });
    }

    // ---- sign-out (D-39): only the console's part of the session ends ------------------------------------------------

    @Test
    void signingOutOfTheConsoleKeepsTheHostsLoginAndTheSessionAlive() {
        bootHost().run(context -> {
            MockBrowser browser = new MockBrowser(mvc(context));
            String hostToken = MockBrowser.csrf(browser.get("/login").getContentAsString());
            browser.post("/login", "username", HOST_USER, "password", HOST_PASSWORD, "_csrf", hostToken);
            browser.signInToConsole("/admin", CONSOLE_USER, CONSOLE_PASSWORD);
            MockHttpSession session = browser.session();
            Object hostContext = session.getAttribute(HOST_CONTEXT_KEY);
            String token = MockBrowser.csrf(browser.get("/admin").getContentAsString());
            assertThat(attributeNames(session)).contains(CONSOLE_CONTEXT_KEY, CONSOLE_CSRF_KEY, HOST_CONTEXT_KEY);

            MockHttpServletResponse out = browser.post("/admin/logout", "_csrf", token);

            assertThat(out.getHeader("Location")).endsWith("/admin/login?logout");
            assertThat(session.isInvalid()).as("the host's login keeps the session").isFalse();
            assertThat(session.getAttribute(HOST_CONTEXT_KEY)).as("the host's login is untouched").isSameAs(hostContext);
            assertThat(attributeNames(session)).as("every console attribute is gone")
                    .noneMatch(name -> name.startsWith("JPA_ADMIN_"));
            MockHttpServletResponse host = browser.get("/hello");
            assertThat(host.getStatus()).as("still signed in to the host").isEqualTo(200);
            assertThat(host.getContentAsString()).isEqualTo("hello from the host");
            assertThat(browser.get("/admin").getStatus()).as("signed out of the console").isEqualTo(302);
            // And the console can be signed in to again, with the host login still there.
            assertThat(browser.signInToConsole("/admin", CONSOLE_USER, CONSOLE_PASSWORD).getHeader("Location")).endsWith("/admin");
            assertThat(browser.get("/admin").getStatus()).isEqualTo(200);
            assertThat(((SecurityContext) browser.session().getAttribute(HOST_CONTEXT_KEY)).getAuthentication().getName())
                    .isEqualTo(HOST_USER);
        });
    }

    @Test
    void signingOutOfAConsoleOnlySessionEndsIt() {
        bootHost().run(context -> {
            MockBrowser browser = new MockBrowser(mvc(context));
            browser.signInToConsole("/admin", CONSOLE_USER, CONSOLE_PASSWORD);
            String token = MockBrowser.csrf(browser.get("/admin").getContentAsString());
            MockHttpSession before = browser.session();
            assertThat(attributeNames(before)).allMatch(name -> name.startsWith("JPA_ADMIN_"));

            MockHttpServletResponse out = browser.post("/admin/logout", "_csrf", token);

            assertThat(out.getHeader("Location")).endsWith("/admin/login?logout");
            assertThat(before.isInvalid()).isTrue();
            assertThat(browser.get("/admin").getStatus()).isEqualTo(302);
        });
    }

    @Test
    void anyOtherStateInTheSessionKeepsItToo() {
        bootHost().run(context -> {
            MockBrowser browser = new MockBrowser(mvc(context));
            browser.session().setAttribute("host-cart", "2 items");
            browser.signInToConsole("/admin", CONSOLE_USER, CONSOLE_PASSWORD);
            String token = MockBrowser.csrf(browser.get("/admin").getContentAsString());

            browser.post("/admin/logout", "_csrf", token);

            assertThat(browser.session().isInvalid()).isFalse();
            assertThat(browser.session().getAttribute("host-cart")).isEqualTo("2 items");
            assertThat(attributeNames(browser.session())).containsExactly("host-cart");
        });
    }

    @Test
    void signOutStillNeedsAPostWithTheTokenAndANormalRequestChangesNothing() {
        bootHost().run(context -> {
            MockBrowser browser = new MockBrowser(mvc(context));
            String hostToken = MockBrowser.csrf(browser.get("/login").getContentAsString());
            browser.post("/login", "username", HOST_USER, "password", HOST_PASSWORD, "_csrf", hostToken);
            browser.signInToConsole("/admin", CONSOLE_USER, CONSOLE_PASSWORD);
            browser.get("/admin");
            Set<String> before = attributeNames(browser.session());

            assertThat(browser.get("/admin/logout").getStatus()).as("GET does nothing").isEqualTo(404);
            assertThat(browser.post("/admin/logout").getStatus()).as("no token").isEqualTo(403);
            assertThat(browser.post("/admin/logout", "_csrf", "wrong").getStatus()).as("wrong token").isEqualTo(403);

            assertThat(attributeNames(browser.session())).isEqualTo(before);
            assertThat(browser.get("/admin").getStatus()).isEqualTo(200);
            assertThat(browser.get("/hello").getStatus()).isEqualTo(200);
        });
    }
}

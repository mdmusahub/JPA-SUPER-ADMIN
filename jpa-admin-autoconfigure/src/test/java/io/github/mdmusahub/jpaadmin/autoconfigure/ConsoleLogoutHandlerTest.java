package io.github.mdmusahub.jpaadmin.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

/**
 * Signing out removes the console's own session state and nothing else (SEC-4, D-39): the host may share the session, and
 * its login or cart must survive. The session is ended only when nothing but console state was in it.
 */
class ConsoleLogoutHandlerTest {

    private final ConsoleLogoutHandler handler = new ConsoleLogoutHandler();

    private static Set<String> names(MockHttpSession session) {
        return new TreeSet<>(Collections.list(session.getAttributeNames()));
    }

    private void signOut(MockHttpSession session) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/logout");
        request.setSession(session);
        handler.logout(request, new MockHttpServletResponse(), null);
    }

    // ---- the console's names -----------------------------------------------------------------------------------------

    @Test
    void everyAttributeTheConsoleCreatesStartsWithOnePrefixSoNothingCanBeMissed() {
        assertThat(JpaAdminSecurityConfiguration.SECURITY_CONTEXT_KEY).startsWith(JpaAdminSecurityConfiguration.SESSION_PREFIX);
        assertThat(JpaAdminSecurityConfiguration.CSRF_TOKEN_KEY).startsWith(JpaAdminSecurityConfiguration.SESSION_PREFIX);
        assertThat(JpaAdminSecurityConfiguration.SAVED_REQUEST_KEY).startsWith(JpaAdminSecurityConfiguration.SESSION_PREFIX);
        assertThat(JpaAdminSecurityConfiguration.SESSION_PREFIX).isEqualTo("JPA_ADMIN_");
    }

    // ---- a session that holds only console state ------------------------------------------------------------------------

    @Test
    void aSessionWithOnlyConsoleStateIsEnded() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(JpaAdminSecurityConfiguration.SECURITY_CONTEXT_KEY, "context");
        session.setAttribute(JpaAdminSecurityConfiguration.CSRF_TOKEN_KEY, "token");
        session.setAttribute(JpaAdminSecurityConfiguration.SAVED_REQUEST_KEY, "request");

        signOut(session);

        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void aFreshSessionWithNothingInItIsEndedToo() {
        MockHttpSession session = new MockHttpSession();

        signOut(session);

        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void anAttributeAddedByAFutureConsoleFeatureWithTheSamePrefixIsCleared() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("JPA_ADMIN_SOMETHING_NEW", "x");

        signOut(session);

        assertThat(session.isInvalid()).isTrue();
    }

    // ---- a session that also holds the host's state -------------------------------------------------------------------

    @Test
    void theHostsAttributesStayUntouchedAndTheSessionStaysAlive() {
        MockHttpSession session = new MockHttpSession();
        Object hostLogin = new Object();
        session.setAttribute("SPRING_SECURITY_CONTEXT", hostLogin);
        session.setAttribute("cart", "3 items");
        session.setAttribute("org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository.CSRF_TOKEN", "host-token");
        session.setAttribute("SPRING_SECURITY_SAVED_REQUEST", "host-saved-request");
        session.setAttribute(JpaAdminSecurityConfiguration.SECURITY_CONTEXT_KEY, "context");
        session.setAttribute(JpaAdminSecurityConfiguration.CSRF_TOKEN_KEY, "token");
        session.setAttribute(JpaAdminSecurityConfiguration.SAVED_REQUEST_KEY, "request");

        signOut(session);

        assertThat(session.isInvalid()).as("the host's state keeps the session alive").isFalse();
        assertThat(names(session)).containsExactly("SPRING_SECURITY_CONTEXT", "SPRING_SECURITY_SAVED_REQUEST", "cart",
                "org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository.CSRF_TOKEN");
        assertThat(session.getAttribute("SPRING_SECURITY_CONTEXT")).isSameAs(hostLogin);
        assertThat(session.getAttribute("cart")).isEqualTo("3 items");
    }

    @ParameterizedTest
    @ValueSource(strings = {"cart", "locale", "JPA_ADMIN", "JPA_ADMINISTRATOR", "jpa_admin_lowercase", "my.JPA_ADMIN_inside",
            "SPRING_SECURITY_CONTEXT"})
    void anyOtherAttributeIsTheHostsEvenIfItsNameLooksSimilar(String name) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(name, "kept");
        session.setAttribute(JpaAdminSecurityConfiguration.SECURITY_CONTEXT_KEY, "context");

        signOut(session);

        assertThat(session.isInvalid()).isFalse();
        assertThat(session.getAttribute(name)).isEqualTo("kept");
        assertThat(session.getAttribute(JpaAdminSecurityConfiguration.SECURITY_CONTEXT_KEY)).isNull();
    }

    @Test
    void aSessionWithOnlyHostStateKeepsEverythingAndIsNotEnded() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("cart", "1");

        signOut(session);

        assertThat(session.isInvalid()).isFalse();
        assertThat(names(session)).containsExactly("cart");
    }

    // ---- edge cases -----------------------------------------------------------------------------------------------------

    @Test
    void withoutASessionNothingHappensAndNoSessionIsCreated() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/logout");

        handler.logout(request, new MockHttpServletResponse(), null);

        assertThat(request.getSession(false)).isNull();
    }

    @Test
    void aSessionThatAnotherHandlerAlreadyEndedCausesNoError() {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/logout");
        request.setSession(session);
        session.invalidate();

        handler.logout(request, new MockHttpServletResponse(), null);

        assertThat(session.isInvalid()).isTrue();
    }
}

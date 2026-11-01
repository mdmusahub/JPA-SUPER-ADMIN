package io.github.mdmusahub.jpaadmin.autoconfigure;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;

/**
 * Signing out of the console ends the console's part of the browser session and nothing else (SEC-4, D-39).
 *
 * <p>The browser has one HTTP session per site, shared with the host application. The host may keep a login, a cart or
 * any other state in it, so the console must not throw the whole session away. This handler removes every session
 * attribute that the console created (the names that start with {@value JpaAdminSecurityConfiguration#SESSION_PREFIX}:
 * its security context, CSRF token and saved request), and then invalidates the session <em>only if nothing else is
 * left in it</em>. A session that holds any other attribute stays, with that attribute untouched.
 *
 * <p>Spring Security's own logout handlers, which run beside this one, are set up not to invalidate the session. They
 * clear the current authentication and remove the same console-named attributes from the console's own repositories;
 * they never touch the host's.
 *
 * <p>One limit of the servlet API: "check that the session is empty" and "invalidate it" are two calls, and there is no
 * atomic version. A host request that runs in the same session at that very instant and adds state between the two
 * calls would lose it with the session. The window is microseconds wide and the effect is a lost host state, never a
 * disclosure.
 */
final class ConsoleLogoutHandler implements LogoutHandler {

    private static final Logger log = LoggerFactory.getLogger(ConsoleLogoutHandler.class);

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }
        try {
            for (String name : consoleAttributes(session)) {
                session.removeAttribute(name);
            }
            if (Collections.list(session.getAttributeNames()).isEmpty()) {
                session.invalidate();
                log.debug("Console sign-out: the session held only console state, so it was ended");
            } else {
                log.debug("Console sign-out: the session holds state that is not the console's, so it was kept");
            }
        } catch (IllegalStateException alreadyInvalid) {
            // Another handler ended the session first. There is nothing left to clear.
            log.trace("Console sign-out: the session was already invalid");
        }
    }

    /** The names of the attributes that the console created. A copy, so the session can be changed while it is read. */
    private static List<String> consoleAttributes(HttpSession session) {
        List<String> names = new ArrayList<>();
        for (String name : Collections.list(session.getAttributeNames())) {
            if (name.startsWith(JpaAdminSecurityConfiguration.SESSION_PREFIX)) {
                names.add(name);
            }
        }
        return names;
    }
}

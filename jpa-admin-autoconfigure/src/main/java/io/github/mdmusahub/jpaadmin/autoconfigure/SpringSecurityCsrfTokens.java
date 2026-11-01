package io.github.mdmusahub.jpaadmin.autoconfigure;

import io.github.mdmusahub.jpaadmin.web.CsrfTokens;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;

/**
 * Reads the CSRF token that Spring Security's {@code CsrfFilter} put on the request (SEC-5). This is the only place
 * that connects the console's pages to Spring Security's token, so {@code jpa-admin-core} needs no dependency on it.
 *
 * <p>The token is the request's own, a masked copy of the per-session secret; nothing here is a stored credential.
 * A request without a token gets an empty one, so a form built from it fails the CSRF check, which is the safe result.
 */
final class SpringSecurityCsrfTokens implements CsrfTokens {

    /** Spring Security's defaults, used only when a request carries no token. */
    private static final String PARAMETER_NAME = "_csrf";
    private static final String HEADER_NAME = "X-CSRF-TOKEN";

    @Override
    public PageChrome.Csrf forRequest(HttpServletRequest request) {
        if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token) {
            return new PageChrome.Csrf(token.getParameterName(), token.getHeaderName(), token.getToken());
        }
        return new PageChrome.Csrf(PARAMETER_NAME, HEADER_NAME, "");
    }
}

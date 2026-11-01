package io.github.mdmusahub.jpaadmin.autoconfigure;

import io.github.mdmusahub.jpaadmin.web.BasePath;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.DefaultSavedRequest;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

/**
 * After a successful sign-in the browser goes to the dashboard ({@code {path}}), or back to the page it was refused
 * on, and nowhere else.
 *
 * <p>No redirect target ever comes from the request that signs in: a {@code redirect}, {@code continue}, {@code next}
 * or similar parameter is not read at all (open redirect). The only other target is the <em>saved request</em>, the
 * page the visitor first asked for. It lives in the server-side session, under the console's own attribute name, and
 * is used only when all of this holds:
 * <ul>
 *   <li>it was a GET;</li>
 *   <li>its path lies under the console path (not the host's pages, which another chain may have saved), and is not
 *       the sign-in page or the "access denied" page, which would only loop;</li>
 *   <li>it is rebuilt as a <em>relative</em> path with the query, so the redirect cannot leave the site, and it
 *       passes a strict character check, so no header-splitting or scheme trick can ride along.</li>
 * </ul>
 * Anything else falls back to the dashboard.
 */
final class ConsoleAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final int MAX_TARGET_LENGTH = 2048;

    /** Path part: letters, digits and the RFC 3986 path characters, no backslash, no space. */
    private static final Pattern TARGET = Pattern
            .compile("/[A-Za-z0-9._~!$&'()*+,;=:@%/-]*(?:\\?[A-Za-z0-9._~!$&'()*+,;=:@%/?-]*)?");

    private final BasePath basePath;
    private final RequestCache requestCache;
    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    ConsoleAuthenticationSuccessHandler(BasePath basePath, RequestCache requestCache) {
        this.basePath = basePath;
        this.requestCache = requestCache;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        String target = savedTarget(request, response).orElse(basePath.value());
        requestCache.removeRequest(request, response);
        redirectStrategy.sendRedirect(request, response, target);
    }

    /** The page the visitor first asked for, as a path relative to the servlet context, if it may be used. */
    private Optional<String> savedTarget(HttpServletRequest request, HttpServletResponse response) {
        SavedRequest saved = requestCache.getRequest(request, response);
        if (!(saved instanceof DefaultSavedRequest page) || !"GET".equalsIgnoreCase(page.getMethod())) {
            return Optional.empty();
        }
        String uri = page.getRequestURI();
        String context = page.getContextPath() == null ? "" : page.getContextPath();
        if (uri == null || !uri.startsWith(context)) {
            return Optional.empty();
        }
        String path = uri.substring(context.length());
        String target = page.getQueryString() == null ? path : path + "?" + page.getQueryString();
        return isUsable(path, target) ? Optional.of(target) : Optional.empty();
    }

    private boolean isUsable(String path, String target) {
        String base = basePath.value();
        boolean underConsole = path.equals(base) || path.startsWith(base + "/");
        boolean loopsBack = path.equals(base + "/login") || path.startsWith(base + "/login/")
                || path.equals(base + "/error/forbidden");
        return target.length() <= MAX_TARGET_LENGTH
                && TARGET.matcher(target).matches()
                && underConsole
                && !loopsBack
                && !path.contains("//");
    }
}

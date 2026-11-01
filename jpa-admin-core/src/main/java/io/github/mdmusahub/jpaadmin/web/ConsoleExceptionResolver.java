package io.github.mdmusahub.jpaadmin.web;

import io.github.mdmusahub.jpaadmin.web.view.ErrorView;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome.Breadcrumb;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.web.ErrorResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/**
 * Shows the console's own error page (404, 405, 500 and the other statuses) for a failed request <em>under the console
 * path</em>, and does nothing for any other request.
 *
 * <p>That second half is the host-safety rule (SEC-4): for a host route, {@link #resolveException} answers
 * {@code null} ("not mine"), so the host's own error handling runs exactly as it did before the console was added.
 * Asset requests are left to the default handling as well, so a missing stylesheet does not render a page.
 *
 * <p>The page text is fixed by the status code ({@link ErrorViews}); the exception's message, class and stack never
 * reach the response (SEC-12). The cause of an unexpected failure is logged on the server, with the request path but
 * without the query string, which could hold anything a visitor typed.
 *
 * <p>Security exceptions (access denied, authentication) are not handled here: they belong to Spring Security's
 * filter chain, so they are passed on untouched.
 *
 * <p>Internal, not stable API. Public only so the auto-configuration module can create it.
 */
public final class ConsoleExceptionResolver implements HandlerExceptionResolver, Ordered {

    private static final Logger log = LoggerFactory.getLogger(ConsoleExceptionResolver.class);

    private static final String SECURITY_PACKAGE = "org.springframework.security.";

    private final BasePath basePath;
    private final ConsoleChromeFactory chromeFactory;
    private final ConsoleViews views;

    public ConsoleExceptionResolver(BasePath basePath, ConsoleChromeFactory chromeFactory, ConsoleViews views) {
        this.basePath = Objects.requireNonNull(basePath, "basePath");
        this.chromeFactory = Objects.requireNonNull(chromeFactory, "chromeFactory");
        this.views = Objects.requireNonNull(views, "views");
    }

    /** First in line: the default resolvers would answer with a bare status and hand the page to the host. */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response, Object handler,
                                         Exception exception) {
        if (response.isCommitted() || !isConsolePage(request) || isSecurityException(exception)) {
            return null;
        }
        int status = statusOf(exception);
        if (status >= 500) {
            log.error("A console request failed with status {} at {}", status, path(request), exception);
        } else {
            log.debug("A console request ended with status {} at {}", status, path(request));
        }
        ErrorView view = ErrorViews.forStatus(status);
        response.setStatus(status);
        return views.page(request, ErrorView.TEMPLATE, chromeFactory.chrome(request,
                List.of(new Breadcrumb("Dashboard", basePath.value()), new Breadcrumb(view.heading(), null))), view);
    }

    private static int statusOf(Exception exception) {
        if (exception instanceof ErrorResponse errorResponse) {
            return errorResponse.getStatusCode().value();
        }
        return 500;
    }

    private static boolean isSecurityException(Exception exception) {
        return exception.getClass().getName().startsWith(SECURITY_PACKAGE);
    }

    /** Under the console path, but not an asset: {@code /admin} and {@code /admin/x} count, {@code /administrator} does not. */
    private boolean isConsolePage(HttpServletRequest request) {
        String path = path(request);
        String base = basePath.value();
        boolean underConsole = path.equals(base) || path.startsWith(base + "/");
        boolean asset = path.equals(base + "/assets") || path.startsWith(base + "/assets/");
        return underConsole && !asset;
    }

    /** The request path without the servlet context path. */
    private static String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        return context != null && !context.isEmpty() && uri.startsWith(context) ? uri.substring(context.length()) : uri;
    }
}

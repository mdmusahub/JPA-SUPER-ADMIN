package io.github.mdmusahub.jpaadmin.autoconfigure;

import io.github.mdmusahub.jpaadmin.web.BasePath;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Fails closed: a console page is never served to a request that did not pass through the console's security chain.
 *
 * <p>The chain starts with {@link Marker}, which notes "this request entered the chain". This class is an MVC
 * interceptor on {@code {path}} and {@code {path}/**} that refuses any request without that note. It protects against
 * the one thing the chain cannot protect against by itself: not being run at all. Examples are a host that
 * excluded Spring Security's servlet filter registration, a host that told Spring Security to ignore the console
 * path, or a servlet path prefix ({@code spring.mvc.servlet.path}) that the chain's path matcher does not see.
 * Without this guard such a host would serve the console to anyone. With it, the console answers 500 and logs why.
 *
 * <p>The note is set before any decision, so a request the chain denies and forwards to the console's "access denied"
 * page carries it too.
 */
final class ConsoleChainGuard implements HandlerInterceptor, WebMvcConfigurer {

    /** Request attribute set by {@link Marker}. */
    static final String ENTERED_CHAIN = ConsoleChainGuard.class.getName() + ".ENTERED_CHAIN";

    private static final Logger log = LoggerFactory.getLogger(ConsoleChainGuard.class);

    private final BasePath basePath;
    private final AtomicBoolean logged = new AtomicBoolean();

    ConsoleChainGuard(BasePath basePath) {
        this.basePath = basePath;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns(basePath.value(), basePath.value() + "/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (request.getAttribute(ENTERED_CHAIN) != null) {
            return true;
        }
        if (logged.compareAndSet(false, true)) {
            log.error("A request to the console path {} did not pass through the console's security chain, so the "
                    + "console refuses to answer it (and will refuse every request like it). Check that Spring "
                    + "Security's filter is registered for this path, that no WebSecurityCustomizer ignores it, and "
                    + "that spring.mvc.servlet.path is not set.", basePath.value());
        }
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        return false;
    }

    /** The first filter of the console's chain: records that the request entered it. */
    static final class Marker extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            request.setAttribute(ENTERED_CHAIN, Boolean.TRUE);
            chain.doFilter(request, response);
        }
    }
}

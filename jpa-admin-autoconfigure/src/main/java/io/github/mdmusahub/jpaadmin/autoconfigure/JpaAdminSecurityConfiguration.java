package io.github.mdmusahub.jpaadmin.autoconfigure;

import io.github.mdmusahub.jpaadmin.web.BasePath;
import io.github.mdmusahub.jpaadmin.web.CsrfTokens;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.session.DisableEncodeUrlFilter;

/**
 * The console's sign-in: one {@link SecurityFilterChain} for {@code {path}/**} and nothing else (SEC-4, SEC-5, SEC-6,
 * SEC-13). It is the only thing in this module that the host's own security can feel, so each choice below is made so
 * that it cannot.
 *
 * <p><b>Scope.</b> The chain has the highest precedence and matches only the console path, so a request to a host route
 * never enters it, and a request to the console never enters a host chain, whatever the host's chains match.
 *
 * <p><b>One admin, kept local.</b> The only user is the one from {@code jpa.admin.username} and
 * {@code jpa.admin.password}. The password is hashed with BCrypt once, at startup. The {@link AuthenticationManager}
 * is a private {@link ProviderManager} with one provider and no parent, handed to this chain alone. No
 * {@code UserDetailsService}, {@code AuthenticationManager}, {@code AuthenticationProvider} or {@code PasswordEncoder}
 * is a bean, because a host's chains and Spring Boot's own auto-configuration pick up beans of those types. So the
 * host's users cannot sign in to the console, the console's admin cannot sign in to the host, and Boot's generated
 * password is neither suppressed nor created by us. For the same reason the other helpers (request cache, access
 * denied handler, entry point, repositories) are local objects, not beans.
 *
 * <p><b>Nothing shared in the session.</b> The browser has one session per site, shared with the host. The console
 * keeps its security context, its CSRF token and its saved request under {@code JPA_ADMIN_*} attribute names, so a host
 * user is not signed in to the console, a console admin is not signed in to the host, and signing in or out of one
 * does not rotate the other's CSRF token or replay the other's saved request. Signing out removes the console's
 * attributes and ends the session only when nothing else is in it ({@link ConsoleLogoutHandler}). Cookie and session
 * settings are left to Spring Boot's defaults (SEC-8).
 *
 * <p><b>Authority, not role prefix.</b> The admin holds the authority {@value #AUTHORITY} (the role {@code JPA_ADMIN}),
 * and the rule checks the authority itself, so a host that changes Spring Security's role prefix cannot lock the admin
 * out.
 */
@Configuration(proxyBeanMethods = false)
class JpaAdminSecurityConfiguration {

    /** The role of the one admin. */
    static final String ROLE = "JPA_ADMIN";

    /** The authority behind the role, and the one thing the chain requires everywhere except the public pages. */
    static final String AUTHORITY = "ROLE_" + ROLE;

    /**
     * Every session attribute that the console creates starts with this. That is how the console and the host keep out
     * of each other's way in the shared session, and how sign-out knows which attributes are the console's (D-39).
     */
    static final String SESSION_PREFIX = "JPA_ADMIN_";

    /** Session attribute names. Console-only, so the host's own security never reads or overwrites them. */
    static final String SECURITY_CONTEXT_KEY = SESSION_PREFIX + "SECURITY_CONTEXT";
    static final String CSRF_TOKEN_KEY = SESSION_PREFIX + "CSRF_TOKEN";
    static final String SAVED_REQUEST_KEY = SESSION_PREFIX + "SAVED_REQUEST";

    /**
     * What a console page may load: its own scripts, styles and images, plus {@code data:} images (the stylesheets use
     * small inline SVGs). No inline script or style (UI-13), no framing, no other site as a form target (SEC-13).
     */
    static final String CONTENT_SECURITY_POLICY = "default-src 'none'; script-src 'self'; style-src 'self'; "
            + "img-src 'self' data:; connect-src 'self'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'";

    /**
     * The chain. Registered under the highest precedence; a request enters it only if its path is {@code {path}} or
     * below. Public: {@code /login}, {@code /assets/**}, {@code /theme.css}. Everything else needs the admin authority.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain jpaAdminSecurityFilterChain(HttpSecurity http, BasePath basePath,
                                                    JpaAdminProperties properties) throws Exception {
        String base = basePath.value();
        PathPatternRequestMatcher.Builder paths = PathPatternRequestMatcher.withDefaults();

        HttpSessionSecurityContextRepository contexts = new HttpSessionSecurityContextRepository();
        contexts.setSpringSecurityContextKey(SECURITY_CONTEXT_KEY);
        HttpSessionCsrfTokenRepository csrfTokens = new HttpSessionCsrfTokenRepository();
        csrfTokens.setSessionAttributeName(CSRF_TOKEN_KEY);
        HttpSessionRequestCache savedRequests = new HttpSessionRequestCache();
        savedRequests.setSessionAttrName(SAVED_REQUEST_KEY);

        // A refused request, including one with a missing or wrong CSRF token, is forwarded to the console's own
        // "access denied" page: a fixed text, no exception detail (SEC-12).
        AccessDeniedHandlerImpl accessDenied = new AccessDeniedHandlerImpl();
        accessDenied.setErrorPage(base + "/error/forbidden");

        RedirectStrategy redirects = new DefaultRedirectStrategy();
        // The same redirect for every kind of failure, so the answer never tells a wrong username from a wrong
        // password. It does not store the failure in the session (the stored exception would carry the attempt), and
        // it puts nothing the visitor typed in the URL.
        AuthenticationFailureHandler signInFailed = (request, response, exception) ->
                redirects.sendRedirect(request, response, base + "/login?error");

        http.securityMatcher(paths.matcher(base + "/**"))
                // First, so every request that enters the chain is noted, even one the chain then refuses.
                .addFilterBefore(new ConsoleChainGuard.Marker(), DisableEncodeUrlFilter.class)
                .authenticationManager(consoleAuthentication(properties))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(paths.matcher(base + "/login"), paths.matcher(base + "/assets/**"),
                                paths.matcher(base + "/theme.css")).permitAll()
                        .anyRequest().hasAuthority(AUTHORITY))
                .formLogin(login -> login
                        .loginPage(base + "/login")
                        .loginProcessingUrl(base + "/login")
                        .successHandler(new ConsoleAuthenticationSuccessHandler(basePath, savedRequests))
                        .failureHandler(signInFailed))
                // POST only: a link, an image or a prefetch can never sign the admin out. The session is shared with the
                // host, so Spring's handler must not invalidate it; ConsoleLogoutHandler clears the console's own
                // attributes and ends the session only if nothing else is in it (D-39).
                .logout(logout -> logout
                        .logoutRequestMatcher(paths.matcher(HttpMethod.POST, base + "/logout"))
                        .logoutSuccessUrl(base + "/login?logout")
                        .invalidateHttpSession(false)
                        .clearAuthentication(true)
                        .addLogoutHandler(new ConsoleLogoutHandler()))
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokens))
                .securityContext(context -> context.securityContextRepository(contexts))
                .requestCache(cache -> cache.requestCache(savedRequests))
                .sessionManagement(session -> session.sessionFixation(fixation -> fixation.changeSessionId()))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint(base + "/login"))
                        .accessDeniedHandler(accessDenied))
                .headers(headers -> headers
                        .frameOptions(frames -> frames.deny())
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        // HSTS covers the whole host name, not just the console path, so it is the host's decision.
                        .httpStrictTransportSecurity(hsts -> hsts.disable()));
        return http.build();
    }

    /** Lets the console's pages read the CSRF token without a dependency on Spring Security in {@code jpa-admin-core}. */
    @Bean
    CsrfTokens jpaAdminCsrfTokens() {
        return new SpringSecurityCsrfTokens();
    }

    /** Refuses any console request that did not pass through the chain above (fail closed). */
    @Bean
    ConsoleChainGuard jpaAdminChainGuard(BasePath basePath) {
        return new ConsoleChainGuard(basePath);
    }

    /**
     * The chain's own authentication: one user, a BCrypt hash made now and never stored anywhere else, and no parent
     * manager. The user is looked up by exact name (an in-memory user manager would lower-case it).
     */
    private static AuthenticationManager consoleAuthentication(JpaAdminProperties properties) {
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        UserDetails admin = User.withUsername(properties.getUsername().strip())
                .password(encoder.encode(properties.getPassword()))
                .authorities(AUTHORITY)
                .build();
        // A user that never matches, for every other name. Spring Security would log "Failed to find user '<name>'" at
        // DEBUG if the lookup threw, which would put what a visitor typed in the log; with a stand-in the failure is an
        // ordinary wrong password. The random hash cannot be matched, and it makes the work (and so the time) the same.
        UserDetails nobody = User.withUsername("nobody")
                .password(encoder.encode(java.util.UUID.randomUUID().toString()))
                .authorities(AUTHORITY)
                .build();
        // A copy for every lookup: after a successful sign-in Spring Security erases the credentials of the principal it
        // was given, so handing out the one stored object would wipe the hash and lock the admin out after one sign-in.
        UserDetailsService users = name -> User.withUserDetails(admin.getUsername().equals(name) ? admin : nobody).build();
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }
}

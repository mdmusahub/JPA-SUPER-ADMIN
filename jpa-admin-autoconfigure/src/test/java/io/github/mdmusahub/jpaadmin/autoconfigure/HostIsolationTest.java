package io.github.mdmusahub.jpaadmin.autoconfigure;

import static io.github.mdmusahub.jpaadmin.autoconfigure.ConsoleTestHosts.CONSOLE_PASSWORD;
import static io.github.mdmusahub.jpaadmin.autoconfigure.ConsoleTestHosts.CONSOLE_USER;
import static io.github.mdmusahub.jpaadmin.autoconfigure.ConsoleTestHosts.HOST_PASSWORD;
import static io.github.mdmusahub.jpaadmin.autoconfigure.ConsoleTestHosts.HOST_USER;
import static io.github.mdmusahub.jpaadmin.autoconfigure.ConsoleTestHosts.mvc;
import static io.github.mdmusahub.jpaadmin.autoconfigure.ConsoleTestHosts.observe;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import io.github.mdmusahub.jpaadmin.autoconfigure.ConsoleTestHosts.Observed;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SEC-4, the host-safety promise: the console never changes, replaces or weakens the host application's own security
 * and never touches the host's routes. Each test builds one kind of host with the real auto-configurations, sends the
 * <em>same</em> list of requests with the console switched off and switched on, and requires the two answers to be
 * identical for every host route (status, redirects, authentication challenge, content type, body and the set of
 * response headers).
 *
 * <p>The three kinds of host are the ones that the design (D-35) has to serve: no Spring Security at all, Spring Security
 * with the host's own chain, and Spring Security with only Spring Boot's default chain.
 */
@ExtendWith(OutputCaptureExtension.class)
class HostIsolationTest {

    // ---- the hosts ------------------------------------------------------------------------------------------------

    /** The host's own routes. */
    @RestController
    static class HostRoutes {

        @GetMapping("/")
        String root() {
            return "host root";
        }

        @GetMapping("/hello")
        String hello() {
            return "hello from the host";
        }

        @GetMapping("/api/data")
        String data() {
            return "host data";
        }
    }

    /** A host with no security of its own and no Spring Boot security auto-configuration. */
    @Configuration(proxyBeanMethods = false)
    @Import(HostRoutes.class)
    static class PlainHost {
    }

    /** A host with its own chain for {@code /api/**} (HTTP Basic) and nothing for the rest. */
    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    @Import(HostRoutes.class)
    static class HostWithOwnChain {

        @Bean
        SecurityFilterChain hostApiChain(HttpSecurity http) throws Exception {
            http.securityMatcher("/api/**")
                    .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                    .httpBasic(Customizer.withDefaults())
                    .csrf(csrf -> csrf.disable());
            return http.build();
        }

        @Bean
        UserDetailsService hostUsers() {
            return new InMemoryUserDetailsManager(
                    User.withUsername(HOST_USER).password("{noop}" + HOST_PASSWORD).roles("USER").build());
        }
    }

    /** A host that asked for Spring Security and relies on Spring Boot's default chain (everything authenticated). */
    @Configuration(proxyBeanMethods = false)
    @Import(HostRoutes.class)
    static class HostRelyingOnBootsDefaultChain {
    }

    /** A host with a catch-all chain at a high (but not the highest) precedence. */
    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    @Import(HostRoutes.class)
    static class HostWithCatchAllChain {

        @Bean
        @Order(1)
        SecurityFilterChain denyEverything(HttpSecurity http) throws Exception {
            http.authorizeHttpRequests(requests -> requests.anyRequest().denyAll()).csrf(csrf -> csrf.disable());
            return http.build();
        }
    }

    private static final String[] BOOT_DEFAULT_USER = {"spring.security.user.name=" + HOST_USER,
            "spring.security.user.password=" + HOST_PASSWORD};

    // ---- the request matrix -----------------------------------------------------------------------------------------

    /** Every request that a host cares about, each with a readable name. None of them is a console URL. */
    private static Map<String, Observed> hostMatrix(MockMvc mvc) throws Exception {
        Map<String, Observed> results = new LinkedHashMap<>();
        results.put("GET /", observe(mvc, get("/")));
        results.put("GET /hello", observe(mvc, get("/hello")));
        results.put("GET /hello as a browser", observe(mvc, get("/hello").accept(MediaType.TEXT_HTML)));
        results.put("HEAD /hello", observe(mvc, head("/hello")));
        results.put("POST /hello without a token", observe(mvc, post("/hello")));
        results.put("GET /api/data without credentials", observe(mvc, get("/api/data")));
        results.put("GET /api/data with the host's credentials",
                observe(mvc, get("/api/data").with(httpBasic(HOST_USER, HOST_PASSWORD))));
        results.put("GET /api/data with wrong credentials", observe(mvc, get("/api/data").with(httpBasic(HOST_USER, "wrong"))));
        results.put("GET /api/data with the console's credentials",
                observe(mvc, get("/api/data").with(httpBasic(CONSOLE_USER, CONSOLE_PASSWORD))));
        results.put("GET /nothing", observe(mvc, get("/nothing")));
        results.put("GET /administrator (starts like /admin)", observe(mvc, get("/administrator")));
        results.put("GET /admin-old/page (starts like /admin)", observe(mvc, get("/admin-old/page")));
        results.put("GET /assets/css/tokens.css", observe(mvc, get("/assets/css/tokens.css")));
        results.put("GET /jpa-admin/assets/css/tokens.css", observe(mvc, get("/jpa-admin/assets/css/tokens.css")));
        results.put("GET /theme.css", observe(mvc, get("/theme.css")));
        results.put("GET /login", observe(mvc, get("/login")));
        results.put("GET /logout", observe(mvc, get("/logout")));
        results.put("POST /logout without a token", observe(mvc, post("/logout")));
        return results;
    }

    private static Map<String, Observed> run(WebApplicationContextRunner runner, Class<?> host, boolean console,
                                             String... properties) {
        AtomicReference<Map<String, Observed>> result = new AtomicReference<>();
        runner.withUserConfiguration(host).withPropertyValues("jpa.admin.enabled=" + console)
                .withPropertyValues(properties).run(context -> {
                    assertThat(context).hasNotFailed();
                    result.set(hostMatrix(mvc(context)));
                });
        return result.get();
    }

    // ---- no Spring Security in the host ---------------------------------------------------------------------------

    @Test
    void aHostWithoutSpringSecurityAnswersEveryHostRouteExactlyAsBeforeWithTheConsoleOn() {
        Map<String, Observed> off = run(ConsoleTestHosts.withoutSpringSecurity(), PlainHost.class, false);
        Map<String, Observed> on = run(ConsoleTestHosts.withoutSpringSecurity(), PlainHost.class, true);

        assertThat(on).isEqualTo(off);
        // Not vacuous: the host's routes answer, and nothing was newly protected, not even /api/**.
        assertThat(on.get("GET /hello").status()).isEqualTo(200);
        assertThat(on.get("GET /hello").body()).isEqualTo("hello from the host");
        assertThat(on.get("GET /api/data without credentials").status()).isEqualTo(200);
        assertThat(on.get("GET /api/data without credentials").body()).isEqualTo("host data");
        assertThat(on.get("GET /nothing").status()).isEqualTo(404);
        assertThat(on.get("GET /login").status()).isEqualTo(404);
    }

    @Test
    void aHostWithoutSpringSecurityGetsNoSecurityHeadersAndNoSpringSecurityFilterOnItsOwnRoutes() {
        Map<String, Observed> on = run(ConsoleTestHosts.withoutSpringSecurity(), PlainHost.class, true);

        for (String request : on.keySet()) {
            Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            names.addAll(on.get(request).headerNames());
            assertThat(names).as(request).doesNotContain("Content-Security-Policy", "Referrer-Policy", "X-Frame-Options",
                    "X-Content-Type-Options", "Cache-Control", "Pragma", "Expires");
        }
    }

    @Test
    void aHostWithoutSpringSecurityDoesNotGetItsRoutesBlockedByTheHttpFirewall() throws Exception {
        // Spring Security's HTTP firewall rejects paths such as //hello or /a;b. A host that never used Spring Security
        // must keep serving them, so the console's filter must not see them.
        ConsoleTestHosts.withoutSpringSecurity().withUserConfiguration(PlainHost.class).run(context -> {
            MockMvc mvc = mvc(context);
            assertThat(observe(mvc, get("/hello;a=b")).status()).isNotEqualTo(400);
            assertThat(observe(mvc, get("//hello")).status()).isNotEqualTo(400);
            assertThat(observe(mvc, get("/hello%2e")).status()).isNotEqualTo(400);
        });
    }

    @Test
    void noGeneratedSecurityPasswordIsLoggedInAHostWithoutSpringSecurity(CapturedOutput output) {
        run(ConsoleTestHosts.withoutSpringSecurity(), PlainHost.class, true);

        assertThat(output.getAll()).doesNotContain("Using generated security password")
                .doesNotContain("generated password");
    }

    // ---- the host's own chain -------------------------------------------------------------------------------------

    @Test
    void aHostWithItsOwnSecurityChainKeepsExactlyItsBehaviourWithTheConsoleOn() {
        Map<String, Observed> off = run(ConsoleTestHosts.withBootSecurity(), HostWithOwnChain.class, false);
        Map<String, Observed> on = run(ConsoleTestHosts.withBootSecurity(), HostWithOwnChain.class, true);

        assertThat(on).isEqualTo(off);
        // Not vacuous: the host's chain protects /api/** and challenges, and leaves /hello public.
        assertThat(on.get("GET /api/data without credentials").status()).isEqualTo(401);
        assertThat(on.get("GET /api/data without credentials").authenticate()).startsWith("Basic");
        assertThat(on.get("GET /api/data with the host's credentials").status()).isEqualTo(200);
        assertThat(on.get("GET /api/data with wrong credentials").status()).isEqualTo(401);
        assertThat(on.get("GET /hello").status()).isEqualTo(200);
    }

    @Test
    void theConsolesAdminDoesNotSignInToTheHostsChain() {
        Map<String, Observed> on = run(ConsoleTestHosts.withBootSecurity(), HostWithOwnChain.class, true);

        assertThat(on.get("GET /api/data with the console's credentials").status()).isEqualTo(401);
    }

    // ---- Spring Boot's default chain ------------------------------------------------------------------------------

    @Test
    void aHostThatRelliesOnSpringBootsDefaultChainKeepsItWithTheConsoleOn() {
        Map<String, Observed> off = run(ConsoleTestHosts.withBootSecurity(), HostRelyingOnBootsDefaultChain.class, false,
                BOOT_DEFAULT_USER);
        Map<String, Observed> on = run(ConsoleTestHosts.withBootSecurity(), HostRelyingOnBootsDefaultChain.class, true,
                BOOT_DEFAULT_USER);

        assertThat(on).isEqualTo(off);
        // Not vacuous: without the console the default chain protects every route, and it still does.
        assertThat(on.get("GET /hello").status()).isIn(401, 302);
        assertThat(on.get("GET /hello as a browser").status()).isEqualTo(302);
        assertThat(on.get("GET /hello as a browser").location()).endsWith("/login");
        assertThat(on.get("GET /api/data without credentials").status()).isIn(401, 302);
        assertThat(on.get("GET /nothing").status()).isIn(401, 302);
        assertThat(on.get("GET /login").status()).isEqualTo(200);
        assertThat(on.get("GET /login").body()).contains("Please sign in");
    }

    @Test
    void theConsoleDoesNotRemoveBootsDefaultChainOrAddAChainThatCapturesTheHostsRoutes() {
        ConsoleTestHosts.withBootSecurity().withUserConfiguration(HostRelyingOnBootsDefaultChain.class)
                .withPropertyValues(BOOT_DEFAULT_USER).run(context -> {
                    assertThat(context).hasBean("defaultSecurityFilterChain").hasBean("jpaAdminSecurityFilterChain");
                    List<SecurityFilterChain> chains = context.getBean(FilterChainProxy.class).getFilterChains();
                    assertThat(chains).hasSize(2);
                    assertThat(matcherOf(chains.get(0))).contains("/admin/**");
                    assertThat(chains.get(1)).isSameAs(context.getBean("defaultSecurityFilterChain"));
                });
    }

    // ---- precedence -----------------------------------------------------------------------------------------------

    @Test
    void theConsolesChainComesBeforeEveryChainOfTheHost() {
        ConsoleTestHosts.withBootSecurity().withUserConfiguration(HostWithOwnChain.class).run(context -> {
            List<SecurityFilterChain> chains = context.getBean(FilterChainProxy.class).getFilterChains();
            assertThat(chains).hasSize(2);
            assertThat(matcherOf(chains.get(0))).contains("/admin/**");
            assertThat(matcherOf(chains.get(1))).contains("/api/**");
        });
    }

    @Test
    void aHostCatchAllChainAtAHighPrecedenceDoesNotCaptureTheConsoleAndStillDeniesTheHostsRoutes() {
        Map<String, Observed> off = run(ConsoleTestHosts.withBootSecurity(), HostWithCatchAllChain.class, false);
        Map<String, Observed> on = run(ConsoleTestHosts.withBootSecurity(), HostWithCatchAllChain.class, true);

        assertThat(on).isEqualTo(off);
        assertThat(on.get("GET /hello").status()).isEqualTo(403);

        ConsoleTestHosts.withBootSecurity().withUserConfiguration(HostWithCatchAllChain.class).run(context -> {
            MockMvc mvc = mvc(context);
            assertThat(observe(mvc, get("/admin/login")).status()).as("the console's sign-in page").isEqualTo(200);
            assertThat(observe(mvc, get("/admin")).status()).isEqualTo(302);
            assertThat(observe(mvc, get("/hello")).status()).as("the host's catch-all still applies").isEqualTo(403);
        });
    }

    private static String matcherOf(SecurityFilterChain chain) {
        return String.valueOf(((DefaultSecurityFilterChain) chain).getRequestMatcher());
    }

    // ---- nothing is added that could change the host's authentication ----------------------------------------------

    /** The kinds of bean that a host's chains, or Spring Boot's own auto-configuration, would pick up. */
    private static final List<Class<?>> EXTENSION_POINTS = Arrays.asList(UserDetailsService.class,
            AuthenticationManager.class, AuthenticationProvider.class, AuthenticationManagerResolver.class,
            PasswordEncoder.class, RequestCache.class, SecurityContextRepository.class, CsrfTokenRepository.class,
            AccessDeniedHandler.class, AuthenticationEntryPoint.class, AuthenticationSuccessHandler.class,
            AuthenticationFailureHandler.class);

    private static Set<String> extensionBeans(ApplicationContext context) {
        Set<String> found = new TreeSet<>();
        for (Class<?> type : EXTENSION_POINTS) {
            for (String name : context.getBeanNamesForType(type)) {
                found.add(type.getSimpleName() + ":" + name);
            }
        }
        return found;
    }

    private static Set<String> extensionBeans(WebApplicationContextRunner runner, Class<?> host, boolean console,
                                              String... properties) {
        AtomicReference<Set<String>> result = new AtomicReference<>();
        runner.withUserConfiguration(host).withPropertyValues("jpa.admin.enabled=" + console)
                .withPropertyValues(properties).run(context -> {
                    assertThat(context).hasNotFailed();
                    result.set(extensionBeans(context));
                });
        return result.get();
    }

    @Test
    void theConsoleAddsNoUserDetailsServiceAuthenticationManagerProviderOrPasswordEncoderBeanToAHostWithoutSpringSecurity() {
        Set<String> off = extensionBeans(ConsoleTestHosts.withoutSpringSecurity(), PlainHost.class, false);
        Set<String> on = extensionBeans(ConsoleTestHosts.withoutSpringSecurity(), PlainHost.class, true);

        assertThat(on).isEqualTo(off).isEmpty();
    }

    @Test
    void theConsoleAddsNoSuchBeanToAHostWithItsOwnChain() {
        Set<String> off = extensionBeans(ConsoleTestHosts.withBootSecurity(), HostWithOwnChain.class, false);
        Set<String> on = extensionBeans(ConsoleTestHosts.withBootSecurity(), HostWithOwnChain.class, true);

        assertThat(on).isEqualTo(off);
        assertThat(on).anyMatch(entry -> entry.startsWith("UserDetailsService:hostUsers"));
    }

    @Test
    void theConsoleAddsNoSuchBeanToAHostThatRelliesOnBootsDefaultChain() {
        Set<String> off = extensionBeans(ConsoleTestHosts.withBootSecurity(), HostRelyingOnBootsDefaultChain.class, false,
                BOOT_DEFAULT_USER);
        Set<String> on = extensionBeans(ConsoleTestHosts.withBootSecurity(), HostRelyingOnBootsDefaultChain.class, true,
                BOOT_DEFAULT_USER);

        assertThat(on).isEqualTo(off);
    }

    // ---- Spring Boot's own behaviour, for the record (why the starter does not bring its auto-configuration) -------

    @Test
    void bootsOwnSecurityAutoConfigurationLogsAGeneratedPasswordInAHostThatDidNotSetUpUsers(CapturedOutput output) {
        // Not our behaviour: this is what Spring Boot does to a host the moment spring-boot-security is on the classpath.
        // It is the reason the starter brings the two Spring Security libraries and not Boot's auto-configuration (D-34).
        run(ConsoleTestHosts.withBootSecurity(), HostRelyingOnBootsDefaultChain.class, false);

        assertThat(output.getAll()).contains("Using generated security password");
    }

    @Test
    void aHostThatAskedForBootSecurityKeepsItsOwnGeneratedPasswordBehaviourWhenTheConsoleIsOn(CapturedOutput output) {
        run(ConsoleTestHosts.withBootSecurity(), HostRelyingOnBootsDefaultChain.class, true);

        // Unchanged for that host: Boot still creates its user; the console neither creates nor suppresses it.
        assertThat(output.getAll()).contains("Using generated security password");
        assertThat(output.getAll()).doesNotContain(CONSOLE_PASSWORD);
    }
}

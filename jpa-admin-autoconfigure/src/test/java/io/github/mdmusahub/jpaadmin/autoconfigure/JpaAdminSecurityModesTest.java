package io.github.mdmusahub.jpaadmin.autoconfigure;

import static io.github.mdmusahub.jpaadmin.autoconfigure.ConsoleTestHosts.mvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import jakarta.servlet.DispatcherType;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.DelegatingFilterProxyRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

/**
 * How the console joins Spring Security in each kind of host (D-35): by registering Spring Security's filter for its own
 * path only when the host has none, and by adding its chain to the host's filter chain proxy when the host has one. In
 * both cases the chain matches {@code {path}/**} and nothing else, and has the highest precedence.
 */
class JpaAdminSecurityModesTest {

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class HostThatEnabledWebSecurityItself {

        @Bean
        @Order(5)
        SecurityFilterChain hostChain(HttpSecurity http) throws Exception {
            http.securityMatcher("/api/**").authorizeHttpRequests(requests -> requests.anyRequest().permitAll());
            return http.build();
        }
    }

    private static String matcherOf(SecurityFilterChain chain) {
        return String.valueOf(((DefaultSecurityFilterChain) chain).getRequestMatcher());
    }

    // ---- a host without Spring Security: the console registers the filter for its own path ---------------------------

    @Test
    void forAHostWithoutSpringSecurityTheFilterIsRegisteredForTheConsolePathAlone() {
        ConsoleTestHosts.withoutSpringSecurity().run(context -> {
            assertThat(context).hasBean("springSecurityFilterChain").hasBean("jpaAdminSecurityFilterRegistration");
            DelegatingFilterProxyRegistrationBean registration = context.getBean(DelegatingFilterProxyRegistrationBean.class);

            assertThat(registration.getUrlPatterns()).containsExactlyInAnyOrder("/admin", "/admin/*");
            assertThat(registration.determineDispatcherTypes()).isEqualTo(EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC));
            assertThat(registration.getOrder()).isEqualTo(JpaAdminStandaloneSecurityConfiguration.FILTER_ORDER).isEqualTo(-100);
        });
    }

    @Test
    void theRegistrationFollowsACustomAndANestedPath() {
        ConsoleTestHosts.withoutSpringSecurity().withPropertyValues("jpa.admin.path=/backoffice").run(context ->
                assertThat(context.getBean(DelegatingFilterProxyRegistrationBean.class).getUrlPatterns())
                        .containsExactlyInAnyOrder("/backoffice", "/backoffice/*"));
        ConsoleTestHosts.withoutSpringSecurity().withPropertyValues("jpa.admin.path=/tools/admin").run(context ->
                assertThat(context.getBean(DelegatingFilterProxyRegistrationBean.class).getUrlPatterns())
                        .containsExactlyInAnyOrder("/tools/admin", "/tools/admin/*"));
    }

    @Test
    void theFilterIsNotRunForARequestOutsideTheConsolePath() {
        ConsoleTestHosts.withoutSpringSecurity().run(context -> {
            MockMvc mvc = mvc(context);
            // /admin/login answers (the filter ran and let the public page through); /administrator is not ours at all.
            assertThat(mvc.perform(get("/admin/login")).andReturn().getResponse().getStatus()).isEqualTo(200);
            assertThat(mvc.perform(get("/administrator")).andReturn().getResponse().getStatus()).isEqualTo(404);
            assertThat(mvc.perform(get("/administrator")).andReturn().getResponse().getHeaderNames())
                    .doesNotContain("X-Frame-Options", "Content-Security-Policy", "Referrer-Policy");
        });
    }

    // ---- a host with Spring Security: the console joins it -------------------------------------------------------------

    @Test
    void forAHostWithBootsSecurityTheHostsFilterRegistrationServesTheConsoleToo() {
        ConsoleTestHosts.withBootSecurity().run(context -> {
            assertThat(context).hasBean("springSecurityFilterChain").doesNotHaveBean("jpaAdminSecurityFilterRegistration");
            assertThat(context.getBeansOfType(DelegatingFilterProxyRegistrationBean.class)).hasSize(1);
            assertThat(context.getBean(FilterChainProxy.class).getFilterChains())
                    .anySatisfy(chain -> assertThat(matcherOf(chain)).contains("/admin/**"));
        });
    }

    @Test
    void forAHostThatEnabledWebSecurityItselfTheConsoleRegistersNoSecondFilter() {
        ConsoleTestHosts.withoutSpringSecurity().withUserConfiguration(HostThatEnabledWebSecurityItself.class).run(context -> {
            assertThat(context).doesNotHaveBean("jpaAdminSecurityFilterRegistration");
            assertThat(context.getBeansOfType(FilterChainProxy.class)).hasSize(1);
            List<SecurityFilterChain> chains = context.getBean(FilterChainProxy.class).getFilterChains();
            assertThat(chains).hasSize(2);
            assertThat(matcherOf(chains.get(0))).contains("/admin/**");
            assertThat(matcherOf(chains.get(1))).contains("/api/**");
        });
    }

    // ---- the chain itself ---------------------------------------------------------------------------------------------

    @Test
    void theChainMatchesTheConsolePathAndNothingElseWhateverThePathIs() {
        for (String path : new String[] {"/admin", "/backoffice", "/tools/admin", "/a-b_c"}) {
            ConsoleTestHosts.withoutSpringSecurity().withPropertyValues("jpa.admin.path=" + path).run(context -> {
                SecurityFilterChain chain = context.getBean("jpaAdminSecurityFilterChain", SecurityFilterChain.class);

                assertThat(matcherOf(chain)).as(path).contains(path + "/**");
                assertThat(((DefaultSecurityFilterChain) chain).getFilters()).first()
                        .isInstanceOf(ConsoleChainGuard.Marker.class);
            });
        }
    }

    @Test
    void theChainBeanHasTheHighestPrecedence() throws NoSuchMethodException {
        Method method = JpaAdminSecurityConfiguration.class.getDeclaredMethod("jpaAdminSecurityFilterChain",
                HttpSecurity.class, io.github.mdmusahub.jpaadmin.web.BasePath.class, JpaAdminProperties.class);

        assertThat(method.getAnnotation(Order.class).value()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }

    @Test
    void theAdminHoldsTheRoleAuthorityAndTheRuleChecksTheAuthorityItself() {
        assertThat(JpaAdminSecurityConfiguration.ROLE).isEqualTo("JPA_ADMIN");
        assertThat(JpaAdminSecurityConfiguration.AUTHORITY).isEqualTo("ROLE_JPA_ADMIN");
    }

    // ---- the content security policy ----------------------------------------------------------------------------------

    @Test
    void theContentSecurityPolicyAllowsOnlyTheConsolesOwnScriptsStylesAndImages() {
        String policy = JpaAdminSecurityConfiguration.CONTENT_SECURITY_POLICY;

        assertThat(policy).contains("default-src 'none'", "script-src 'self'", "style-src 'self'", "img-src 'self' data:",
                "form-action 'self'", "base-uri 'none'", "frame-ancestors 'none'", "connect-src 'self'");
        assertThat(policy).doesNotContain("unsafe-inline", "unsafe-eval", "*", "http:", "https:");
        assertThat(policy.split("; ")).as("script-src and style-src name only 'self'")
                .filteredOn(directive -> directive.startsWith("script-src") || directive.startsWith("style-src"))
                .containsExactlyInAnyOrder("script-src 'self'", "style-src 'self'");
    }
}

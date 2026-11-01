package io.github.mdmusahub.jpaadmin.autoconfigure;

import jakarta.servlet.Filter;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.assertj.AssertableWebApplicationContext;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.thymeleaf.autoconfigure.ThymeleafAutoConfiguration;
import org.springframework.boot.web.servlet.AbstractFilterRegistrationBean;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.ConfigurableWebApplicationContext;

/**
 * Builds the kinds of host application that the isolation tests need, with the real auto-configurations of Spring Boot
 * 4.1.2 and of the console, and turns a context into a {@link MockMvc} that has exactly the servlet filters that the
 * context registers (so a test sees what a browser would see).
 *
 * <ul>
 *   <li>{@link #withoutSpringSecurity()}: Spring MVC only, with no template engine of its own. Spring Boot's security
 *       and Thymeleaf auto-configurations are not on the context, as in a host that depends only on the starter (D-34,
 *       D-40).</li>
 *   <li>{@link #withBootSecurity()}: the same host, plus Spring Boot's security auto-configuration, as in a host that
 *       added {@code spring-boot-starter-security} itself.</li>
 *   <li>{@link #withHostThymeleaf()}: the first host, plus Spring Boot's Thymeleaf auto-configuration, as in a host that
 *       added {@code spring-boot-starter-thymeleaf} itself.</li>
 * </ul>
 */
final class ConsoleTestHosts {

    static final String CONSOLE_USER = "console-admin";
    static final String CONSOLE_PASSWORD = "console-pw-Qx7-not-a-real-secret";
    static final String HOST_USER = "host-user";
    static final String HOST_PASSWORD = "host-pw-Zk4-not-a-real-secret";

    private ConsoleTestHosts() {
    }

    /** One sorted set of auto-configurations, in the order Boot itself would process them. */
    private static WebApplicationContextRunner runner(Class<?>... autoConfigurations) {
        return new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(autoConfigurations))
                .withPropertyValues("jpa.admin.username=" + CONSOLE_USER, "jpa.admin.password=" + CONSOLE_PASSWORD);
    }

    static WebApplicationContextRunner withoutSpringSecurity() {
        return runner(WebMvcAutoConfiguration.class, JpaAdminAutoConfiguration.class);
    }

    static WebApplicationContextRunner withBootSecurity() {
        return runner(WebMvcAutoConfiguration.class, SecurityAutoConfiguration.class,
                UserDetailsServiceAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class, JpaAdminAutoConfiguration.class);
    }

    static WebApplicationContextRunner withHostThymeleaf() {
        return runner(WebMvcAutoConfiguration.class, ThymeleafAutoConfiguration.class, JpaAdminAutoConfiguration.class);
    }

    /** A MockMvc with the filters that the context registers for the servlet container, with their URL patterns. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static MockMvc mvc(AssertableWebApplicationContext context) {
        DefaultMockMvcBuilder builder = MockMvcBuilders
                .webAppContextSetup(context.getSourceApplicationContext(ConfigurableWebApplicationContext.class));
        for (AbstractFilterRegistrationBean registration : (Collection<AbstractFilterRegistrationBean>) context
                .getBeansOfType(AbstractFilterRegistrationBean.class).values()) {
            Filter filter = registration.getFilter();
            Collection<String> patterns = registration.getUrlPatterns();
            if (patterns.isEmpty()) {
                builder.addFilters(filter);
            } else {
                builder.addFilter(filter, patterns.toArray(String[]::new));
            }
        }
        return builder.build();
    }

    /** What a client can see of a response, for comparing two runs. Time-dependent and random values are left out. */
    record Observed(int status, String location, String authenticate, String contentType, String allow, String body,
                    Set<String> headerNames) {

        static Observed of(MockHttpServletResponse response) throws Exception {
            Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            names.addAll(response.getHeaderNames());
            names.remove("Date");
            names.remove("Set-Cookie");
            String body = response.getContentAsString().replaceAll("value=\"[^\"]*\"", "value=\"X\"");
            return new Observed(response.getStatus(), response.getHeader("Location"), response.getHeader("WWW-Authenticate"),
                    response.getContentType(), response.getHeader("Allow"), body, new LinkedHashSet<>(names));
        }
    }

    static Observed observe(MockMvc mvc, MockHttpServletRequestBuilder request) throws Exception {
        return Observed.of(mvc.perform(request).andReturn().getResponse());
    }

}

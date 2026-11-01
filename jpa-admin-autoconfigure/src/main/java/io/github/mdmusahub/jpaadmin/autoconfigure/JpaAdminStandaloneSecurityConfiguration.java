package io.github.mdmusahub.jpaadmin.autoconfigure;

import io.github.mdmusahub.jpaadmin.web.BasePath;
import jakarta.servlet.DispatcherType;
import java.util.EnumSet;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.DelegatingFilterProxyRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.BeanIds;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

/**
 * Spring Security's machinery for a host that has none of its own, and only for the console path.
 *
 * <p>Active only when the application context has no {@code springSecurityFilterChain} yet. A host that uses Spring
 * Security (Boot's auto-configuration, or its own {@code @EnableWebSecurity}) has one, and then this class does
 * nothing: the console's chain is added to the host's filter chain proxy and the host's filter registration serves it.
 *
 * <p>Without a host-owned proxy, this class switches Spring Security on (<code>@EnableWebSecurity</code>, which
 * assembles the proxy from the chains in the context, here only the console's) and registers it with the servlet
 * container for {@code {path}} and {@code {path}/*} alone. Requests to any other URL never reach Spring Security: no
 * filter, no HTTP firewall, no generated user, no default chain protecting the host's routes. That is how a host
 * without Spring Security stays exactly as it was (SEC-4). Spring Boot's own security auto-configuration is not on
 * the classpath in that case (D-34), because it would do the opposite.
 *
 * <p>Only {@code REQUEST} and {@code ASYNC} dispatches are filtered. The access-denied page is reached by a
 * {@code FORWARD}, which must not run the chain a second time.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnMissingBean(name = BeanIds.SPRING_SECURITY_FILTER_CHAIN)
@EnableWebSecurity
class JpaAdminStandaloneSecurityConfiguration {

    /** The same position Spring Boot gives its own security filter: after request-context setup, before the rest. */
    static final int FILTER_ORDER = -100;

    @Bean
    DelegatingFilterProxyRegistrationBean jpaAdminSecurityFilterRegistration(BasePath basePath) {
        DelegatingFilterProxyRegistrationBean registration =
                new DelegatingFilterProxyRegistrationBean(BeanIds.SPRING_SECURITY_FILTER_CHAIN);
        registration.addUrlPatterns(basePath.value(), basePath.value() + "/*");
        registration.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC));
        registration.setOrder(FILTER_ORDER);
        return registration;
    }
}

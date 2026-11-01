package io.github.mdmusahub.jpaadmin.autoconfigure;

import io.github.mdmusahub.jpaadmin.web.BasePath;
import io.github.mdmusahub.jpaadmin.web.ConsoleChromeFactory;
import io.github.mdmusahub.jpaadmin.web.ConsoleController;
import io.github.mdmusahub.jpaadmin.web.ConsoleExceptionResolver;
import io.github.mdmusahub.jpaadmin.web.ConsoleSettings;
import io.github.mdmusahub.jpaadmin.web.ConsoleViewFactory;
import io.github.mdmusahub.jpaadmin.web.ConsoleViews;
import io.github.mdmusahub.jpaadmin.web.CsrfTokens;
import io.github.mdmusahub.jpaadmin.web.ThemeController;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the console. It is active only in a servlet web application with Spring MVC, JPA, Spring Security and
 * Thymeleaf on the classpath, and only while {@code jpa.admin.enabled} is {@code true} (the default). Otherwise it backs
 * off completely: no properties are bound (so no credentials are demanded), no beans and no routes exist (ARCH-7).
 *
 * <p>Spring Security is a condition, not just a dependency, on purpose: a console without its sign-in chain must
 * never answer. The starter brings both libraries (D-34). JPA is checked by class name, so this module has no JPA
 * dependency of its own. Thymeleaf means the Thymeleaf-Spring library only: the console renders its pages with a private
 * engine ({@link ConsoleThymeleafViews}), so Spring Boot's Thymeleaf auto-configuration is neither needed nor used, and
 * a host's own Thymeleaf or other view technology is neither used nor changed (D-40). Bean names start with {@code jpaAdmin} (ARCH-8). The controllers and the assets handler are
 * replaceable ({@code @ConditionalOnMissingBean}); the base path, the path prefix, the password guard, the transport
 * check and everything in the security configuration are not, because they enforce rules (SEC-4, SEC-7, SEC-8, SEC-14).
 *
 * <p>Ordered after Spring Boot's own web security auto-configuration (when the host has it), so Boot decides about its
 * default chain before the console's chain exists. A host that relies on that default keeps it (D-35).
 */
@AutoConfiguration(afterName = "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(value = WebMvcConfigurer.class, name = {
        "jakarta.persistence.EntityManager",
        "org.springframework.security.web.SecurityFilterChain",
        "org.springframework.security.config.annotation.web.builders.HttpSecurity",
        "org.thymeleaf.spring6.SpringTemplateEngine"})
@ConditionalOnProperty(prefix = JpaAdminProperties.PREFIX, name = "enabled", havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(JpaAdminProperties.class)
@Import({JpaAdminSecurityConfiguration.class, JpaAdminStandaloneSecurityConfiguration.class})
public class JpaAdminAutoConfiguration {

    /** The validated console path. Created from the properties, so the properties are checked before it exists. */
    @Bean
    BasePath jpaAdminBasePath(JpaAdminProperties properties) {
        return BasePath.of(properties.getPath());
    }

    /**
     * The production password check. {@code @Lazy(false)} keeps it eager even when the host runs with
     * {@code spring.main.lazy-initialization=true}; nothing else injects it, so a lazy bean would never run and the
     * credential checks would be skipped silently.
     */
    @Bean
    @Lazy(false)
    JpaAdminPasswordGuard jpaAdminPasswordGuard(JpaAdminProperties properties, Environment environment) {
        return new JpaAdminPasswordGuard(properties, environment);
    }

    /** The plain-HTTP warning under the {@code prod} profile (SEC-8). Eager for the same reason as the guard. */
    @Bean
    @Lazy(false)
    JpaAdminTransportCheck jpaAdminTransportCheck(BasePath basePath, Environment environment) {
        return new JpaAdminTransportCheck(basePath, environment);
    }

    /** Adds the base path in front of console controllers only. Not replaceable (SEC-4). */
    @Bean
    JpaAdminPathPrefixConfigurer jpaAdminPathPrefixConfigurer(BasePath basePath) {
        return new JpaAdminPathPrefixConfigurer(basePath);
    }

    /** Serves {@code {path}/assets/**} from the console's own asset folder and from nowhere else. */
    @Bean
    @ConditionalOnMissingBean
    JpaAdminAssetsConfigurer jpaAdminAssetsConfigurer(BasePath basePath) {
        return new JpaAdminAssetsConfigurer(basePath);
    }

    /** The few values the pages need from the properties. Checks the colour once more (SEC-15). */
    @Bean
    ConsoleSettings jpaAdminSettings(JpaAdminProperties properties) {
        return new ConsoleSettings(properties.getTitle(), properties.getPrimaryColor());
    }

    @Bean
    ConsoleChromeFactory jpaAdminChromeFactory(BasePath basePath, ConsoleSettings settings, CsrfTokens csrfTokens) {
        return new ConsoleChromeFactory(basePath, settings, csrfTokens);
    }

    /**
     * The console's own Thymeleaf (D-40). The bean has the console's type, not a Thymeleaf or Spring MVC one: the template
     * engine and the view resolver inside are private and are not beans, because the host's Thymeleaf auto-configuration
     * would pick them up. Not replaceable.
     */
    @Bean
    ConsoleViewFactory jpaAdminViewFactory(ApplicationContext context) {
        return new ConsoleThymeleafViews(context);
    }

    /** Builds every page with a ready-made view, so no view resolver of the host is asked about a console page. */
    @Bean
    ConsoleViews jpaAdminViews(ConsoleViewFactory viewFactory) {
        return new ConsoleViews(viewFactory);
    }

    /** The dashboard, the sign-in page and the "access denied" page. */
    @Bean
    @ConditionalOnMissingBean
    ConsoleController jpaAdminConsoleController(ConsoleChromeFactory chromeFactory, ConsoleViews views,
                                                ConsoleSettings settings, BasePath basePath) {
        return new ConsoleController(chromeFactory, views, settings, basePath);
    }

    /** {@code {path}/theme.css}: the accent colour as one CSS variable (SEC-15). */
    @Bean
    @ConditionalOnMissingBean
    ThemeController jpaAdminThemeController(ConsoleSettings settings) {
        return new ThemeController(settings);
    }

    /** The console's 404, 405 and 500 pages. Answers "not mine" for every request outside the console path. */
    @Bean
    ConsoleExceptionResolver jpaAdminExceptionResolver(BasePath basePath, ConsoleChromeFactory chromeFactory,
                                                       ConsoleViews views) {
        return new ConsoleExceptionResolver(basePath, chromeFactory, views);
    }
}

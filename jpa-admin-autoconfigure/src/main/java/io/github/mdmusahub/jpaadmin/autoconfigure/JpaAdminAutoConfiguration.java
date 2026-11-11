package io.github.mdmusahub.jpaadmin.autoconfigure;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.metadata.EntityScanner;
import io.github.mdmusahub.jpaadmin.service.CountService;
import io.github.mdmusahub.jpaadmin.service.ListQueryService;
import io.github.mdmusahub.jpaadmin.web.BasePath;
import io.github.mdmusahub.jpaadmin.web.ConsoleChromeFactory;
import io.github.mdmusahub.jpaadmin.web.ConsoleController;
import io.github.mdmusahub.jpaadmin.web.ConsoleExceptionResolver;
import io.github.mdmusahub.jpaadmin.web.ConsoleNavigation;
import io.github.mdmusahub.jpaadmin.web.ConsoleSettings;
import io.github.mdmusahub.jpaadmin.web.ConsoleViewFactory;
import io.github.mdmusahub.jpaadmin.web.ConsoleViews;
import io.github.mdmusahub.jpaadmin.web.CsrfTokens;
import io.github.mdmusahub.jpaadmin.web.EntityController;
import io.github.mdmusahub.jpaadmin.web.ThemeController;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
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
import org.springframework.orm.jpa.SharedEntityManagerCreator;
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
@AutoConfiguration(afterName = {
        "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration",
        // Boot 4 moved the technology auto-configurations into per-technology modules (D-30). The entity manager
        // factory must be defined before the console asks whether there is one.
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"})
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

    private static final Logger log = LoggerFactory.getLogger(JpaAdminAutoConfiguration.class);

    /** Named, not referenced as a class, so the condition reads the same whether or not JPA is on the classpath. */
    private static final String ENTITY_MANAGER_FACTORY = "jakarta.persistence.EntityManagerFactory";

    /**
     * The one entity manager factory the console reads, or {@code null} when the host has several and marks none
     * as primary. It never throws: a console that cannot tell which data it would be showing shows none, and the
     * host application starts as usual (SEC-4).
     */
    private static EntityManagerFactory consoleEntityManagerFactory(ObjectProvider<EntityManagerFactory> factories) {
        EntityManagerFactory factory = factories.getIfUnique();
        if (factory == null && factories.stream().findAny().isPresent()) {
            log.warn("The JPA Admin console found more than one EntityManagerFactory and none of them is the "
                    + "primary one, so it cannot tell which data it would be showing. It lists no entities. Mark "
                    + "one factory as @Primary to use the console.");
        }
        return factory;
    }

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

    /**
     * The entity metadata, read once from the metamodel of the host's entity manager factory (ARCH-1). Only when
     * there is such a factory (ARCH-7). When the host has several and marks none as primary, the console cannot
     * know which data it would be showing, so it registers no entity, says so once in the log, and leaves the
     * host's startup alone.
     */
    @Bean
    @ConditionalOnBean(type = ENTITY_MANAGER_FACTORY)
    @ConditionalOnMissingBean
    AdminEntityRegistry jpaAdminEntityRegistry(ObjectProvider<EntityManagerFactory> factories,
                                               JpaAdminProperties properties) {
        EntityManagerFactory factory = consoleEntityManagerFactory(factories);
        return factory == null ? AdminEntityRegistry.empty()
                : EntityScanner.scan(factory.getMetamodel(), properties.discoverySettings());
    }

    /**
     * The record counts. The entity manager is Spring's shared, transaction-aware one, so the service's
     * {@code @Transactional(readOnly = true)} really opens a read-only transaction (ARCH-5) and the console never
     * holds a persistence context of its own between requests.
     */
    @Bean
    @ConditionalOnBean(type = ENTITY_MANAGER_FACTORY)
    @ConditionalOnMissingBean
    CountService jpaAdminCountService(ObjectProvider<EntityManagerFactory> factories, ObjectProvider<Clock> clocks) {
        Clock clock = clocks.getIfAvailable(Clock::systemUTC);
        EntityManagerFactory factory = consoleEntityManagerFactory(factories);
        return factory == null ? CountService.unavailable(clock)
                : CountService.over(SharedEntityManagerCreator.createSharedEntityManager(factory), clock);
    }

    /**
     * The list pages' query service. Same entity manager as the counts: Spring's shared, transaction-aware one, so the
     * service's {@code @Transactional(readOnly = true)} is a real read-only transaction (ARCH-5) and nothing is kept
     * between requests. It reads records only through the metadata, never an entity (ARCH-3).
     */
    @Bean
    @ConditionalOnBean(type = ENTITY_MANAGER_FACTORY)
    @ConditionalOnMissingBean
    ListQueryService jpaAdminListQueryService(ObjectProvider<EntityManagerFactory> factories,
                                              ObjectProvider<AdminEntityRegistry> registries) {
        EntityManagerFactory factory = consoleEntityManagerFactory(factories);
        return factory == null ? ListQueryService.unavailable()
                : ListQueryService.over(SharedEntityManagerCreator.createSharedEntityManager(factory),
                        registries.getIfAvailable(AdminEntityRegistry::empty));
    }

    /**
     * The sidebar and the dashboard. Without JPA, or without an entity manager factory, the console still works:
     * it then has no entities, and the pages say so.
     */
    @Bean
    ConsoleNavigation jpaAdminNavigation(ObjectProvider<AdminEntityRegistry> registries,
                                         ObjectProvider<CountService> counts) {
        AdminEntityRegistry registry = registries.getIfAvailable(AdminEntityRegistry::empty);
        CountService service = counts.getIfAvailable(() -> CountService.unavailable(Clock.systemUTC()));
        return new ConsoleNavigation(registry, service);
    }

    @Bean
    ConsoleChromeFactory jpaAdminChromeFactory(BasePath basePath, ConsoleSettings settings, CsrfTokens csrfTokens,
                                               ConsoleNavigation navigation) {
        return new ConsoleChromeFactory(basePath, settings, csrfTokens, navigation);
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
                                                ConsoleSettings settings, BasePath basePath,
                                                ConsoleNavigation navigation) {
        return new ConsoleController(chromeFactory, views, settings, basePath, navigation);
    }

    /** One entity's list page at {@code {path}/{entity}}. */
    @Bean
    @ConditionalOnMissingBean
    EntityController jpaAdminEntityController(ConsoleChromeFactory chromeFactory, ConsoleViews views,
                                              ConsoleNavigation navigation, ObjectProvider<ListQueryService> lists,
                                              BasePath basePath) {
        return new EntityController(chromeFactory, views, navigation,
                lists.getIfAvailable(ListQueryService::unavailable), basePath);
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

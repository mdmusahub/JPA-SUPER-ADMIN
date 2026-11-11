package io.github.mdmusahub.jpaadmin.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.web.BasePath;
import io.github.mdmusahub.jpaadmin.web.ConsoleChromeFactory;
import io.github.mdmusahub.jpaadmin.web.ConsoleController;
import io.github.mdmusahub.jpaadmin.web.ConsoleExceptionResolver;
import io.github.mdmusahub.jpaadmin.web.ConsoleNavigation;
import io.github.mdmusahub.jpaadmin.web.ConsoleSettings;
import io.github.mdmusahub.jpaadmin.web.ConsoleViews;
import io.github.mdmusahub.jpaadmin.web.ThemeController;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.boot.LazyInitializationBeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * ARCH-7 and ARCH-8: the console is active only in a servlet web application with Spring MVC, JPA, Spring Security and
 * Thymeleaf, only while {@code jpa.admin.enabled} is true, and otherwise leaves no bean and no trace. Replaceable beans
 * back off; the beans that enforce rules do not.
 */
class JpaAdminAutoConfigurationTest {

    private static final String[] CREDENTIALS = {"jpa.admin.username=admin", "jpa.admin.password=x7Gq-4mZp2vK-horse"};

    private final WebApplicationContextRunner web = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JpaAdminAutoConfiguration.class))
            .withPropertyValues(CREDENTIALS);

    private final ApplicationContextRunner nonWeb = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JpaAdminAutoConfiguration.class))
            .withPropertyValues(CREDENTIALS);

    /** Beans that belong to the console: created by our code, or named after it. */
    private static String[] consoleBeans(ApplicationContext context) {
        return Arrays.stream(context.getBeanDefinitionNames())
                .filter(name -> name.startsWith("jpaAdmin") || name.startsWith("jpa.admin")
                        || isOurs(context.getType(name, false)))
                .toArray(String[]::new);
    }

    private static boolean isOurs(Class<?> type) {
        return type != null && type.getPackageName().startsWith("io.github.mdmusahub.jpaadmin");
    }

    // ---- active ------------------------------------------------------------------------------------------------

    @Test
    void isActiveInAServletWebApplication() {
        web.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(JpaAdminProperties.class);
            assertThat(context).hasSingleBean(BasePath.class);
            assertThat(context).hasSingleBean(JpaAdminPasswordGuard.class);
            assertThat(context).hasSingleBean(JpaAdminTransportCheck.class);
            assertThat(context).hasSingleBean(JpaAdminPathPrefixConfigurer.class);
            assertThat(context).hasSingleBean(JpaAdminAssetsConfigurer.class);
            assertThat(context).hasSingleBean(ConsoleSettings.class);
            assertThat(context).hasSingleBean(ConsoleChromeFactory.class);
            assertThat(context).hasSingleBean(ConsoleController.class);
            assertThat(context).hasSingleBean(ThemeController.class);
            assertThat(context).hasSingleBean(ConsoleExceptionResolver.class);
            assertThat(context).hasSingleBean(ConsoleChainGuard.class);
            assertThat(context.getBean(BasePath.class).value()).isEqualTo("/admin");
        });
    }

    @Test
    void usesTheConfiguredPath() {
        web.withPropertyValues("jpa.admin.path=/backoffice").run(context ->
                assertThat(context.getBean(BasePath.class).value()).isEqualTo("/backoffice"));
    }

    @Test
    void passesTheTitleAndColourOnToThePages() {
        web.withPropertyValues("jpa.admin.title=Back office", "jpa.admin.primary-color=#336699").run(context ->
                assertThat(context.getBean(ConsoleSettings.class))
                        .isEqualTo(new ConsoleSettings("Back office", "#336699")));
    }

    @Test
    void everyBeanItRegistersIsNamedWithAPrefixSoItCannotClashWithTheHost() {
        web.run(context -> {
            // The auto-configuration and its imported configuration classes are registered under their class names,
            // which already carry the package; every bean they create must carry the prefix.
            assertThat(consoleBeans(context)).isNotEmpty()
                    .filteredOn(name -> !name.startsWith("io.github.mdmusahub.jpaadmin.autoconfigure.JpaAdmin"))
                    .isNotEmpty()
                    .allSatisfy(name -> assertThat(name).matches("jpaAdmin\\w+|jpa\\.admin-.+"));
            assertThat(context).hasBean("jpaAdminBasePath").hasBean("jpaAdminPasswordGuard")
                    .hasBean("jpaAdminTransportCheck").hasBean("jpaAdminPathPrefixConfigurer")
                    .hasBean("jpaAdminAssetsConfigurer").hasBean("jpaAdminSettings").hasBean("jpaAdminChromeFactory")
                    .hasBean("jpaAdminConsoleController").hasBean("jpaAdminThemeController")
                    .hasBean("jpaAdminExceptionResolver").hasBean("jpaAdminSecurityFilterChain")
                    .hasBean("jpaAdminCsrfTokens").hasBean("jpaAdminChainGuard")
                    .hasBean("jpaAdminSecurityFilterRegistration");
        });
    }

    @Test
    void theAssetsHandlerThePathPrefixAndTheChainGuardAreWebMvcConfigurers() {
        web.run(context -> {
            assertThat(context.getBean(JpaAdminAssetsConfigurer.class)).isInstanceOf(WebMvcConfigurer.class);
            assertThat(context.getBean(JpaAdminPathPrefixConfigurer.class)).isInstanceOf(WebMvcConfigurer.class);
            assertThat(context.getBean(ConsoleChainGuard.class)).isInstanceOf(WebMvcConfigurer.class);
        });
    }

    // ---- backs off ---------------------------------------------------------------------------------------------

    @Test
    void isAbsentInANonWebContextAndDoesNotAskForCredentials() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JpaAdminAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(consoleBeans(context)).isEmpty();
                });
        nonWeb.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(JpaAdminProperties.class);
            assertThat(context).doesNotHaveBean(BasePath.class);
            assertThat(context).doesNotHaveBean(ConsoleController.class);
            assertThat(consoleBeans(context)).isEmpty();
        });
    }

    @Test
    void isAbsentWhenDisabledAndThenDoesNotAskForCredentials() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JpaAdminAutoConfiguration.class))
                .withPropertyValues("jpa.admin.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(consoleBeans(context)).isEmpty();
                    assertThat(context).doesNotHaveBean(JpaAdminProperties.class);
                    assertThat(context).doesNotHaveBean(BasePath.class);
                    assertThat(context).doesNotHaveBean(JpaAdminAssetsConfigurer.class);
                    assertThat(context).doesNotHaveBean(ConsoleController.class);
                    assertThat(context).doesNotHaveBean("springSecurityFilterChain");
                });
    }

    @Test
    void isAbsentWhenDisabledEvenWithCredentialsAndABadPath() {
        web.withPropertyValues("jpa.admin.enabled=false", "jpa.admin.path=not a path").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(consoleBeans(context)).isEmpty();
        });
    }

    @Test
    void isActiveWhenEnabledIsExplicitlyTrue() {
        web.withPropertyValues("jpa.admin.enabled=true").run(context ->
                assertThat(context).hasSingleBean(ConsoleController.class));
    }

    @Test
    void isAbsentWhenJpaIsNotOnTheClasspath() {
        web.withClassLoader(new FilteredClassLoader("jakarta.persistence")).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(consoleBeans(context)).isEmpty();
        });
    }

    @Test
    void isAbsentWhenSpringMvcIsNotOnTheClasspath() {
        web.withClassLoader(new FilteredClassLoader(WebMvcConfigurer.class)).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(consoleBeans(context)).isEmpty();
        });
    }

    /** SEC-4: without Spring Security there is no sign-in, and a console that answers without sign-in is never acceptable. */
    @Test
    void isAbsentWhenSpringSecurityIsNotOnTheClasspathSoNoControllerCanAnswerUnprotected() {
        web.withClassLoader(new FilteredClassLoader("org.springframework.security")).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(consoleBeans(context)).isEmpty();
            assertThat(context).doesNotHaveBean(ConsoleController.class);
            assertThat(context).doesNotHaveBean(ThemeController.class);
        });
    }

    @Test
    void isAbsentWhenThymeleafIsNotOnTheClasspathBecauseThePagesCouldNotBeRendered() {
        web.withClassLoader(new FilteredClassLoader("org.thymeleaf")).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(consoleBeans(context)).isEmpty();
        });
    }

    // ---- replaceable beans back off ----------------------------------------------------------------------------

    @Configuration(proxyBeanMethods = false)
    static class HostReplacements {

        @Bean
        ConsoleController hostHome() {
            return new ConsoleController(
                    new ConsoleChromeFactory(BasePath.of("/admin"), new ConsoleSettings("Host", "#0d9f6e"),
                            request -> new PageChrome.Csrf("_csrf", "X-CSRF-TOKEN", "t"), ConsoleNavigation.empty()),
                    new ConsoleViews((name, locale) -> null),
                    new ConsoleSettings("Host", "#0d9f6e"), BasePath.of("/admin"), ConsoleNavigation.empty());
        }

        @Bean
        ThemeController hostTheme() {
            return new ThemeController(new ConsoleSettings("Host", "#0d9f6e"));
        }

        @Bean
        JpaAdminAssetsConfigurer hostAssets() {
            return new JpaAdminAssetsConfigurer(BasePath.of("/host-assets"));
        }
    }

    @Test
    void theControllersAndTheAssetsHandlerBackOffWhenTheHostProvidesItsOwn() {
        web.withUserConfiguration(HostReplacements.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(ConsoleController.class)).containsOnlyKeys("hostHome");
            assertThat(context.getBeansOfType(ThemeController.class)).containsOnlyKeys("hostTheme");
            assertThat(context.getBeansOfType(JpaAdminAssetsConfigurer.class)).containsOnlyKeys("hostAssets");
            assertThat(context).doesNotHaveBean("jpaAdminConsoleController");
            assertThat(context).doesNotHaveBean("jpaAdminThemeController");
            assertThat(context).doesNotHaveBean("jpaAdminAssetsConfigurer");
        });
    }

    @Test
    void theRulesThatProtectTheHostCannotBeReplacedAwayByAHostBean() {
        web.withUserConfiguration(HostReplacements.class).run(context -> {
            assertThat(context).hasBean("jpaAdminBasePath").hasBean("jpaAdminPathPrefixConfigurer")
                    .hasBean("jpaAdminPasswordGuard").hasBean("jpaAdminTransportCheck")
                    .hasBean("jpaAdminSecurityFilterChain").hasBean("jpaAdminChainGuard")
                    .hasBean("jpaAdminExceptionResolver");
        });
    }

    // ---- the startup checks run through the auto-configuration -------------------------------------------------

    @Test
    void startupFailsWhenTheUsernameIsMissing() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JpaAdminAutoConfiguration.class))
                .withPropertyValues("jpa.admin.password=x7Gq-4mZp2vK-horse")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context).getFailure().rootCause().hasMessageContaining("jpa.admin.username is required");
                });
    }

    @Test
    void startupFailsOnABadPathBeforeAnyMappingIsBuilt() {
        web.withPropertyValues("jpa.admin.path=/admin/").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context).getFailure().rootCause().hasMessageContaining("jpa.admin.path must start with '/'");
        });
    }

    @Test
    void underTheProdProfileAWeakPasswordStopsStartup() {
        web.withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .withPropertyValues("jpa.admin.password=short-pw-1").run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context).getFailure().rootCause()
                            .hasMessageContaining("jpa.admin.password is too weak for the 'prod' profile")
                            .hasMessageNotContaining("short-pw-1");
                });
    }

    @Test
    void underAnotherProfileAWeakPasswordOnlyWarns() {
        web.withInitializer(context -> context.getEnvironment().setActiveProfiles("dev"))
                .withPropertyValues("jpa.admin.password=short-pw-1").run(context ->
                        assertThat(context).hasNotFailed());
    }

    /** Nothing injects the password guard, so lazy initialization would silently skip the checks without {@code @Lazy(false)}. */
    @Test
    void theChecksStillRunWhenTheHostTurnsOnLazyInitialization() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JpaAdminAutoConfiguration.class))
                .withInitializer(context -> context.addBeanFactoryPostProcessor(new LazyInitializationBeanFactoryPostProcessor()))
                .withPropertyValues("jpa.admin.password=x7Gq-4mZp2vK-horse")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context).getFailure().rootCause().hasMessageContaining("jpa.admin.username is required");
                });
    }

    @Test
    void theProdPasswordCheckStillRunsWhenTheHostTurnsOnLazyInitialization() {
        web.withInitializer(context -> context.addBeanFactoryPostProcessor(new LazyInitializationBeanFactoryPostProcessor()))
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .withPropertyValues("jpa.admin.password=short-pw-1").run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context).getFailure().rootCause().hasMessageContaining("too weak");
                });
    }
}

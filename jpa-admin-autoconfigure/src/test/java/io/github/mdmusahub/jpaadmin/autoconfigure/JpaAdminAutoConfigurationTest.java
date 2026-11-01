package io.github.mdmusahub.jpaadmin.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.web.BasePath;
import io.github.mdmusahub.jpaadmin.web.PlaceholderController;
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
 * ARCH-7 and ARCH-8: the console is active only in a servlet web application with Spring MVC and JPA, only while
 * {@code jpa.admin.enabled} is true, and otherwise leaves no bean and no trace. Replaceable beans back off.
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
            assertThat(context).hasSingleBean(JpaAdminPathPrefixConfigurer.class);
            assertThat(context).hasSingleBean(JpaAdminAssetsConfigurer.class);
            assertThat(context).hasSingleBean(PlaceholderController.class);
            assertThat(context.getBean(BasePath.class).value()).isEqualTo("/admin");
        });
    }

    @Test
    void usesTheConfiguredPath() {
        web.withPropertyValues("jpa.admin.path=/backoffice").run(context ->
                assertThat(context.getBean(BasePath.class).value()).isEqualTo("/backoffice"));
    }

    @Test
    void everyBeanItRegistersIsNamedWithAPrefixSoItCannotClashWithTheHost() {
        web.run(context -> {
            // The auto-configuration class itself is registered under its own class name, which already carries
            // the package; every bean it creates must carry the prefix.
            assertThat(consoleBeans(context)).isNotEmpty()
                    .filteredOn(name -> !name.equals(JpaAdminAutoConfiguration.class.getName()))
                    .isNotEmpty()
                    .allSatisfy(name -> assertThat(name).matches("jpaAdmin\\w+|jpa\\.admin-.+"));
            assertThat(context).hasBean("jpaAdminBasePath").hasBean("jpaAdminPasswordGuard")
                    .hasBean("jpaAdminPathPrefixConfigurer").hasBean("jpaAdminAssetsConfigurer")
                    .hasBean("jpaAdminPlaceholderController");
        });
    }

    @Test
    void theAssetsHandlerAndThePathPrefixAreWebMvcConfigurers() {
        web.run(context -> {
            assertThat(context.getBean(JpaAdminAssetsConfigurer.class)).isInstanceOf(WebMvcConfigurer.class);
            assertThat(context.getBean(JpaAdminPathPrefixConfigurer.class)).isInstanceOf(WebMvcConfigurer.class);
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
            assertThat(context).doesNotHaveBean(PlaceholderController.class);
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
                    assertThat(context).doesNotHaveBean(PlaceholderController.class);
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
                assertThat(context).hasSingleBean(PlaceholderController.class));
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

    // ---- replaceable beans back off ----------------------------------------------------------------------------

    @Configuration(proxyBeanMethods = false)
    static class HostReplacements {

        @Bean
        PlaceholderController hostHome() {
            return new PlaceholderController();
        }

        @Bean
        JpaAdminAssetsConfigurer hostAssets() {
            return new JpaAdminAssetsConfigurer(BasePath.of("/host-assets"));
        }
    }

    @Test
    void theControllerAndTheAssetsHandlerBackOffWhenTheHostProvidesItsOwn() {
        web.withUserConfiguration(HostReplacements.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(PlaceholderController.class)).containsOnlyKeys("hostHome");
            assertThat(context.getBeansOfType(JpaAdminAssetsConfigurer.class)).containsOnlyKeys("hostAssets");
            assertThat(context).doesNotHaveBean("jpaAdminPlaceholderController");
            assertThat(context).doesNotHaveBean("jpaAdminAssetsConfigurer");
        });
    }

    @Test
    void theRulesThatProtectTheHostCannotBeReplacedAwayByAHostBean() {
        web.withUserConfiguration(HostReplacements.class).run(context -> {
            assertThat(context).hasBean("jpaAdminBasePath").hasBean("jpaAdminPathPrefixConfigurer")
                    .hasBean("jpaAdminPasswordGuard");
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

package io.github.mdmusahub.jpaadmin.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.env.MockEnvironment;

/**
 * Binds {@link JpaAdminProperties} from real property sources and checks the startup failures a user sees (SEC-7).
 * Uses a bare context with only the properties, so nothing else can influence the result.
 */
class JpaAdminPropertiesBindingTest {

    private static final String PASSWORD = "S3cret-test-value-NOT-REAL";

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Properties.class);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(JpaAdminProperties.class)
    static class Properties {
    }

    @Test
    void bindsTheTwoRequiredValuesAndKeepsTheDefaults() {
        runner.withPropertyValues("jpa.admin.username=admin", "jpa.admin.password=" + PASSWORD).run(context -> {
            assertThat(context).hasNotFailed();
            JpaAdminProperties properties = context.getBean(JpaAdminProperties.class);
            assertThat(properties.getUsername()).isEqualTo("admin");
            assertThat(properties.getPassword()).isEqualTo(PASSWORD);
            assertThat(properties.isEnabled()).isTrue();
            assertThat(properties.getPath()).isEqualTo("/admin");
            assertThat(properties.getTitle()).isEqualTo("JPA Admin");
            assertThat(properties.getPrimaryColor()).isEqualTo("#0d9f6e");
        });
    }

    @Test
    void bindsEveryPropertyIncludingTheKebabCaseName() {
        runner.withPropertyValues("jpa.admin.username=root", "jpa.admin.password=" + PASSWORD,
                "jpa.admin.enabled=true", "jpa.admin.path=/backoffice", "jpa.admin.title=Shop admin",
                "jpa.admin.primary-color=#123abc").run(context -> {
                    JpaAdminProperties properties = context.getBean(JpaAdminProperties.class);
                    assertThat(properties.getUsername()).isEqualTo("root");
                    assertThat(properties.getPath()).isEqualTo("/backoffice");
                    assertThat(properties.getTitle()).isEqualTo("Shop admin");
                    assertThat(properties.getPrimaryColor()).isEqualTo("#123abc");
                });
    }

    @Test
    void bindsAValueThatComesFromAResolvedPlaceholder() {
        runner.withPropertyValues("jpa.admin.username=admin", "jpa.admin.password=${secret.source}",
                "secret.source=" + PASSWORD).run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(JpaAdminProperties.class).getPassword()).isEqualTo(PASSWORD);
                });
    }

    // ---- startup failures name the exact property --------------------------------------------------------------

    @Test
    void startupFailsWhenTheUsernameIsMissing() {
        runner.withPropertyValues("jpa.admin.password=" + PASSWORD).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context).getFailure().rootCause()
                    .isInstanceOf(JpaAdminConfigurationException.class)
                    .hasMessageContaining("jpa.admin.username is required. Set it in application.yml, "
                            + "for example jpa.admin.username=admin.")
                    .hasMessageNotContaining("jpa.admin.password");
        });
    }

    @Test
    void startupFailsWhenTheUsernameIsBlank() {
        runner.withPropertyValues("jpa.admin.username=", "jpa.admin.password=" + PASSWORD).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context).getFailure().rootCause().hasMessageContaining("jpa.admin.username is required");
        });
    }

    @Test
    void startupFailsWhenThePasswordIsMissing() {
        runner.withPropertyValues("jpa.admin.username=admin").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context).getFailure().rootCause()
                    .hasMessageContaining("jpa.admin.password is required")
                    .hasMessageNotContaining("jpa.admin.username");
        });
    }

    @Test
    void startupFailsWhenThePasswordIsBlank() {
        runner.withPropertyValues("jpa.admin.username=admin", "jpa.admin.password=").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context).getFailure().rootCause().hasMessageContaining("jpa.admin.password is required");
        });
    }

    @Test
    void startupFailsWhenNothingIsConfiguredAndNamesBothProperties() {
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context).getFailure().rootCause()
                    .hasMessageContaining("jpa.admin.username is required")
                    .hasMessageContaining("jpa.admin.password is required");
        });
    }

    @Test
    void anUnsetEnvironmentVariableIsTreatedAsMissingNotAsThePasswordItself() {
        runner.withPropertyValues("jpa.admin.username=admin", "jpa.admin.password=${JPA_ADMIN_TEST_UNSET_8F3A}")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context).getFailure().rootCause()
                            .hasMessageContaining("jpa.admin.password refers to a variable that has no value");
                });
    }

    /**
     * The reason for the placeholder check. Spring Boot's binder leaves an unresolvable placeholder in place and does
     * not fail, so without the check the console would start with the password {@code ${...}}.
     */
    @Test
    void theBinderLeavesAnUnresolvedPlaceholderAsLiteralText() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("jpa.admin.password", "${JPA_ADMIN_TEST_UNSET_8F3A}");

        JpaAdminProperties bound = Binder.get(environment).bind("jpa.admin", JpaAdminProperties.class).get();

        assertThat(bound.getPassword()).isEqualTo("${JPA_ADMIN_TEST_UNSET_8F3A}");
    }

    @Test
    void startupFailsOnABadPath() {
        runner.withPropertyValues("jpa.admin.username=admin", "jpa.admin.password=" + PASSWORD,
                "jpa.admin.path=/admin/").run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context).getFailure().rootCause().hasMessageContaining("jpa.admin.path must start with '/'");
                });
    }

    @Test
    void startupFailsOnTheSiteRootAsPath() {
        runner.withPropertyValues("jpa.admin.username=admin", "jpa.admin.password=" + PASSWORD, "jpa.admin.path=/")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context).getFailure().rootCause().hasMessageContaining("jpa.admin.path");
                });
    }

    @Test
    void startupFailsOnABadColour() {
        runner.withPropertyValues("jpa.admin.username=admin", "jpa.admin.password=" + PASSWORD,
                "jpa.admin.primary-color=red").run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context).getFailure().rootCause()
                            .hasMessageContaining("jpa.admin.primary-color must be a colour written as #rrggbb");
                });
    }

    // ---- the password never reaches the failure output (SEC-6) -------------------------------------------------

    @Test
    void thePasswordIsNotInAnyMessageOfAFailedStartupNorInTheReport() {
        runner.withPropertyValues("jpa.admin.username=admin", "jpa.admin.password=" + PASSWORD,
                "jpa.admin.path=/bad path/", "jpa.admin.primary-color=nope").run(context -> {
                    Throwable failure = context.getStartupFailure();
                    assertThat(failure).isNotNull();

                    for (Throwable t = failure; t != null; t = t.getCause()) {
                        assertThat(String.valueOf(t.getMessage())).doesNotContain(PASSWORD);
                    }
                    var analysis = new JpaAdminFailureAnalyzer().analyze(failure);
                    assertThat(analysis).isNotNull();
                    assertThat(analysis.getDescription()).doesNotContain(PASSWORD);
                    assertThat(analysis.getAction()).doesNotContain(PASSWORD);
                });
    }

    @Test
    void thePasswordIsNotInTheBoundObjectsToString() {
        runner.withPropertyValues("jpa.admin.username=admin", "jpa.admin.password=" + PASSWORD).run(context ->
                assertThat(context.getBean(JpaAdminProperties.class).toString()).doesNotContain(PASSWORD));
    }
}

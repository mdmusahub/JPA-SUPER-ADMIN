package io.github.mdmusahub.jpaadmin.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mdmusahub.jpaadmin.service.ExportSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * The export properties (Phase 11, D-107): the documented defaults, binding from real property sources, and a bad cap stops
 * startup with a message that names the property, says what is allowed and never repeats the value.
 */
class JpaAdminExportPropertiesTest {

    private static final String PASSWORD = "S3cret-test-value-NOT-REAL";

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Properties.class)
            .withPropertyValues("jpa.admin.username=admin", "jpa.admin.password=" + PASSWORD);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(JpaAdminProperties.class)
    static class Properties {
    }

    private static JpaAdminProperties valid() {
        JpaAdminProperties properties = new JpaAdminProperties();
        properties.setUsername("admin");
        properties.setPassword(PASSWORD);
        return properties;
    }

    private static String problemsOf(JpaAdminProperties properties) {
        return assertThatThrownBy(properties::validate).isInstanceOf(JpaAdminConfigurationException.class)
                .actual().getMessage();
    }

    // ---- defaults -----------------------------------------------------------------------------------------------------

    @Test
    void theDefaultsAreOnFiftyThousandRowsAndNoByteOrderMark() {
        JpaAdminProperties properties = valid();

        assertThat(properties.isExportEnabled()).isTrue();
        assertThat(properties.getExportMaxRows()).isEqualTo(50_000);
        assertThat(properties.isExportBom()).isFalse();
        assertThatCode(properties::validate).doesNotThrowAnyException();
        assertThat(properties.exportSettings()).isEqualTo(ExportSettings.defaults());
        assertThat(properties.exportSettings().cap()).isEqualTo(50_000);
    }

    // ---- binding ------------------------------------------------------------------------------------------------------

    @Test
    void bindsTheThreePropertiesFromTheirKebabCaseNames() {
        runner.withPropertyValues("jpa.admin.export-enabled=false", "jpa.admin.export-max-rows=1234",
                "jpa.admin.export-bom=true").run(context -> {
                    assertThat(context).hasNotFailed();
                    ExportSettings settings = context.getBean(JpaAdminProperties.class).exportSettings();
                    assertThat(settings.enabled()).isFalse();
                    assertThat(settings.cap()).isEqualTo(1234);
                    assertThat(settings.bom()).isTrue();
                });
    }

    @Test
    void aLowerCapIsAccepted() {
        JpaAdminProperties properties = valid();
        properties.setExportMaxRows(1);

        assertThatCode(properties::validate).doesNotThrowAnyException();
        assertThat(properties.exportSettings().cap()).isEqualTo(1);
    }

    @Test
    void theCeilingItselfIsAccepted() {
        JpaAdminProperties properties = valid();
        properties.setExportMaxRows(50_000);

        assertThatCode(properties::validate).doesNotThrowAnyException();
        assertThat(properties.exportSettings().cap()).isEqualTo(50_000);
    }

    // ---- a bad cap -----------------------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(longs = {50_001, 60_000, 1_000_000, Integer.MAX_VALUE, 99_999_999_999L, 0, -1, Long.MIN_VALUE})
    void aCapOutsideOneToFiftyThousandStopsStartupAndNamesTheProperty(long value) {
        JpaAdminProperties properties = valid();
        properties.setExportMaxRows(value);

        String message = problemsOf(properties);

        assertThat(message).contains("jpa.admin.export-max-rows", "1 to 50000", "never raise");
    }

    @Test
    void theMessageNeverRepeatsTheValueThatWasSet() {
        JpaAdminProperties properties = valid();
        properties.setExportMaxRows(987_654_321L);

        assertThat(problemsOf(properties)).doesNotContain("987654321", "987,654,321");
    }

    @Test
    void aTooLargeCapFailsTheRealStartupToo() {
        runner.withPropertyValues("jpa.admin.export-max-rows=99999999999").run(context -> {
            assertThat(context).hasFailed();
            Throwable failure = context.getStartupFailure();
            assertThat(rootMessages(failure)).contains("jpa.admin.export-max-rows");
            assertThat(rootMessages(failure)).doesNotContain(PASSWORD);
        });
    }

    @Test
    void aBadCapIsReportedTogetherWithOtherProblemsInOnePass() {
        JpaAdminProperties properties = valid();
        properties.setPrimaryColor("green");
        properties.setExportMaxRows(70_000);

        assertThat(problemsOf(properties)).contains("jpa.admin.primary-color", "jpa.admin.export-max-rows");
    }

    @Test
    void aCapThatIsNotANumberFailsBindingAndNamesTheProperty() {
        runner.withPropertyValues("jpa.admin.export-max-rows=lots").run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootMessages(context.getStartupFailure())).contains("export-max-rows");
        });
    }

    @Test
    void theCapOfTheCoreRecordCanNeverBeRaisedEvenWhenBuiltByHand() {
        assertThat(new ExportSettings(true, 1_000_000, false).cap()).isEqualTo(50_000);
        assertThatThrownBy(() -> new ExportSettings(true, 0, false)).isInstanceOf(IllegalArgumentException.class);
    }

    private static String rootMessages(Throwable failure) {
        StringBuilder text = new StringBuilder();
        for (Throwable t = failure; t != null; t = t.getCause()) {
            text.append(t.getMessage()).append('\n');
        }
        return text.toString();
    }
}

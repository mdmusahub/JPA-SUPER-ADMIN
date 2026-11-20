package io.github.mdmusahub.jpaadmin.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mdmusahub.jpaadmin.service.ListQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * {@code jpa.admin.page-size} (Phase 13, D-119): the default is 25, the four sizes on offer are accepted, anything else
 * stops startup with a message that names the property, says what is allowed and never repeats the value, and the real
 * binding names the property for a value that is not a number.
 */
class JpaAdminPageSizePropertyTest {

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

    @Test
    void theDefaultIsTwentyFiveAndMatchesTheListsOwnDefault() {
        JpaAdminProperties properties = valid();

        assertThat(properties.getPageSize()).isEqualTo(25).isEqualTo(ListQuery.DEFAULT_SIZE);
        assertThatCode(properties::validate).doesNotThrowAnyException();
        assertThat(properties.pageSize()).isEqualTo(25);
    }

    @ParameterizedTest
    @ValueSource(longs = {10, 25, 50, 100})
    void everySizeOnOfferIsAccepted(long size) {
        JpaAdminProperties properties = valid();
        properties.setPageSize(size);

        assertThatCode(properties::validate).doesNotThrowAnyException();
        assertThat(properties.pageSize()).isEqualTo((int) size);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 5, 7, 9, 11, 24, 26, 49, 51, 99, 101, 200, 1000, -1, -25, Integer.MAX_VALUE,
            (long) Integer.MAX_VALUE + 25, 99_999_999_999L, Long.MAX_VALUE, Long.MIN_VALUE, (long) Integer.MIN_VALUE - 25})
    void anyOtherSizeStopsStartupAndNamesTheProperty(long size) {
        JpaAdminProperties properties = valid();
        properties.setPageSize(size);

        String message = problemsOf(properties);

        assertThat(message).contains("jpa.admin.page-size", "10, 25, 50, 100", "leave the property out");
    }

    @Test
    void theMessageNeverRepeatsTheValueThatWasSet() {
        JpaAdminProperties properties = valid();
        properties.setPageSize(987_654_321L);

        assertThat(problemsOf(properties)).doesNotContain("987654321", "987,654,321");
    }

    @Test
    void aBadSizeIsReportedTogetherWithOtherProblemsInOnePass() {
        JpaAdminProperties properties = valid();
        properties.setPrimaryColor("green");
        properties.setPageSize(7);

        assertThat(problemsOf(properties)).contains("jpa.admin.primary-color", "jpa.admin.page-size");
    }

    @Test
    void bindsFromTheKebabCaseNameAndReachesTheListSetting() {
        runner.withPropertyValues("jpa.admin.page-size=50").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(JpaAdminProperties.class).pageSize()).isEqualTo(50);
        });
    }

    @Test
    void noPropertyMeansTwentyFiveInARealStartToo() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(JpaAdminProperties.class).pageSize()).isEqualTo(25);
        });
    }

    @Test
    void aSizeThatIsNotOnOfferFailsTheRealStartupAndNeverPrintsTheSecret() {
        runner.withPropertyValues("jpa.admin.page-size=7").run(context -> {
            assertThat(context).hasFailed();
            String text = rootMessages(context.getStartupFailure());
            assertThat(text).contains("jpa.admin.page-size").doesNotContain(PASSWORD);
        });
    }

    @Test
    void aSizeThatIsNotANumberFailsBindingAndNamesTheProperty() {
        runner.withPropertyValues("jpa.admin.page-size=lots").run(context -> {
            assertThat(context).hasFailed();
            String text = rootMessages(context.getStartupFailure());
            assertThat(text).contains("page-size").doesNotContain(PASSWORD);
        });
    }

    @Test
    void anAbsurdSizeStillReachesThePlainMessageInsteadOfABindingError() {
        runner.withPropertyValues("jpa.admin.page-size=99999999999").run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootMessages(context.getStartupFailure())).contains("jpa.admin.page-size", "10, 25, 50, 100");
        });
    }

    @Test
    void theToStringNamesTheSizeButNeverThePassword() {
        JpaAdminProperties properties = valid();
        properties.setPageSize(50);

        assertThat(properties.toString()).contains("pageSize=50").doesNotContain(PASSWORD);
    }

    private static String rootMessages(Throwable failure) {
        StringBuilder text = new StringBuilder();
        for (Throwable t = failure; t != null; t = t.getCause()) {
            text.append(t.getMessage()).append('\n');
        }
        return text.toString();
    }
}

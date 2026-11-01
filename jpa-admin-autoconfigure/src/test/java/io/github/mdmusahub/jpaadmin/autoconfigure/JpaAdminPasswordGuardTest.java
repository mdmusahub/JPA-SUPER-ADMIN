package io.github.mdmusahub.jpaadmin.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.mock.env.MockEnvironment;

/** SEC-7 and D-7: under {@code prod} a weak password stops startup, elsewhere it logs a warning. Never the password. */
class JpaAdminPasswordGuardTest {

    private static final String SHORT = "short-pw-1";
    private static final String COMMON = "CorrectHorseBatteryStaple";
    private static final String STRONG = "x7Gq-4mZp2vK-horse";

    private final Logger logger = (Logger) LoggerFactory.getLogger(JpaAdminPasswordGuard.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void captureLogs() {
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        logger.detachAppender(logs);
        logs.stop();
    }

    private static JpaAdminPasswordGuard guard(String password, String... activeProfiles) {
        JpaAdminProperties properties = new JpaAdminProperties();
        properties.setUsername("admin");
        properties.setPassword(password);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(activeProfiles);
        return new JpaAdminPasswordGuard(properties, environment);
    }

    private List<String> warnings() {
        return logs.list.stream().filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage).toList();
    }

    // ---- prod: weak passwords stop startup ---------------------------------------------------------------------

    @Test
    void prodRefusesAShortPasswordAndSaysWhy() {
        assertThatThrownBy(() -> guard(SHORT, "prod").afterPropertiesSet())
                .isInstanceOf(JpaAdminConfigurationException.class)
                .hasMessageContaining("jpa.admin.password is too weak for the 'prod' profile")
                .hasMessageContaining("it is shorter than 12 characters")
                .hasMessageNotContaining(SHORT);
    }

    @Test
    void prodRefusesACommonPasswordEvenWhenItIsLongEnough() {
        assertThatThrownBy(() -> guard(COMMON, "prod").afterPropertiesSet())
                .isInstanceOf(JpaAdminConfigurationException.class)
                .hasMessageContaining("it is on the list of common passwords")
                .hasMessageNotContaining(COMMON);
    }

    @Test
    void prodStillRefusesWhenOtherProfilesAreActiveToo() {
        assertThatThrownBy(() -> guard(SHORT, "cloud", "prod", "eu").afterPropertiesSet())
                .isInstanceOf(JpaAdminConfigurationException.class);
    }

    @Test
    void prodAcceptsAStrongPasswordSilently() {
        assertThatCode(() -> guard(STRONG, "prod").afterPropertiesSet()).doesNotThrowAnyException();
        assertThat(warnings()).isEmpty();
    }

    @Test
    void prodRefusesTheTwelveCharacterBoundaryMinusOne() {
        assertThatThrownBy(() -> guard("x7Gq-4mZp2v", "prod").afterPropertiesSet())
                .isInstanceOf(JpaAdminConfigurationException.class);
        assertThatCode(() -> guard("x7Gq-4mZp2vK", "prod").afterPropertiesSet()).doesNotThrowAnyException();
    }

    // ---- other profiles: warn, never fail ----------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"dev", "test", "staging", "production", "PROD", "prod-eu"})
    void anyProfileButProdOnlyWarnsAboutAShortPassword(String profile) {
        assertThatCode(() -> guard(SHORT, profile).afterPropertiesSet()).doesNotThrowAnyException();

        assertThat(warnings()).singleElement().satisfies(message -> {
            assertThat(message).contains("jpa.admin.password is weak: it is shorter than 12 characters");
            assertThat(message).doesNotContain(SHORT);
        });
    }

    @Test
    void withNoProfileAtAllAShortPasswordOnlyWarns() {
        assertThatCode(() -> guard(SHORT).afterPropertiesSet()).doesNotThrowAnyException();
        assertThat(warnings()).hasSize(1);
    }

    @Test
    void aCommonPasswordOutsideProdWarnsWithoutRepeatingIt() {
        assertThatCode(() -> guard(COMMON, "dev").afterPropertiesSet()).doesNotThrowAnyException();

        assertThat(warnings()).singleElement().satisfies(message -> {
            assertThat(message).contains("it is on the list of common passwords");
            assertThat(message).doesNotContain(COMMON);
        });
    }

    @Test
    void aStrongPasswordOutsideProdLogsNothing() {
        assertThatCode(() -> guard(STRONG, "dev").afterPropertiesSet()).doesNotThrowAnyException();
        assertThat(logs.list).isEmpty();
    }

    @Test
    void theWarningTellsTheOperatorWhatProdWouldDo() {
        guard(SHORT, "dev").afterPropertiesSet();

        assertThat(warnings()).singleElement().asString()
                .contains("Use at least 12 characters")
                .contains("'prod' profile");
    }
}

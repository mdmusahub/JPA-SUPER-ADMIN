package io.github.mdmusahub.jpaadmin.engines;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mdmusahub.jpaadmin.engines.EngineGate.Decision;
import io.github.mdmusahub.jpaadmin.engines.EngineGate.Verdict;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.TestAbortedException;

/**
 * The rule for a missing Docker (Phase 14a, D-128), without a container: a skip is never silent and never a pass, and a CI that
 * requires the engines fails instead of skipping. Mutation (d): a gate that skips without a reason, or that skips where it must
 * fail, breaks these tests.
 */
class EngineGateTest {

    @Test
    void whenDockerIsThereTheEngineRuns() {
        Decision decision = EngineGate.decide("PostgreSQL", "PostgreSqlEngineTest", true, true);

        assertThat(decision.verdict()).isEqualTo(Verdict.RUN);
        EngineGate.apply(decision); // returns, neither skips nor fails
    }

    @Test
    void whenDockerIsMissingOnALaptopTheClassIsSkippedWithAReasonThatNamesTheEngineAndTheCommand() {
        Decision decision = EngineGate.decide("PostgreSQL", "PostgreSqlEngineTest", false, false);

        assertThat(decision.verdict()).isEqualTo(Verdict.SKIP);
        assertThat(decision.reason()).isNotBlank().contains("PostgreSQL").contains("Docker").contains("not passed")
                .contains("mvn -B -pl jpa-admin-core test -Dtest=PostgreSqlEngineTest");
        assertThatThrownBy(() -> EngineGate.apply(decision)).isInstanceOf(TestAbortedException.class)
                .hasMessageContaining("PostgreSQL").hasMessageContaining("not passed");
    }

    @Test
    void whenDockerIsMissingAndCiRequiresItTheBuildFailsAndDoesNotSkip() {
        Decision decision = EngineGate.decide("MySQL", "MySqlEngineTest", false, true);

        assertThat(decision.verdict()).isEqualTo(Verdict.FAIL);
        assertThat(decision.reason()).contains("MySQL").contains("Docker").contains(EngineGate.REQUIRE_VARIABLE)
                .contains("-Dtest=MySqlEngineTest");
        assertThatThrownBy(() -> EngineGate.apply(decision)).isInstanceOf(AssertionFailedError.class)
                .isNotInstanceOf(TestAbortedException.class).hasMessageContaining("MySQL");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n"})
    void aSkipOrAFailureWithoutAReasonCannotBeBuilt(String blank) {
        assertThatThrownBy(() -> new Decision(Verdict.SKIP, blank)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Decision(Verdict.FAIL, blank)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new Decision(Verdict.RUN, blank).verdict()).as("a run needs no reason").isEqualTo(Verdict.RUN);
    }

    @Test
    void everyCombinationOfDockerAndTheRequirementGivesExactlyTheDocumentedVerdict() {
        for (boolean docker : new boolean[] {true, false}) {
            for (boolean required : new boolean[] {true, false}) {
                Verdict expected = docker ? Verdict.RUN : required ? Verdict.FAIL : Verdict.SKIP;
                assertThat(EngineGate.decide("H", "T", docker, required).verdict())
                        .as("docker=" + docker + ", required=" + required).isEqualTo(expected);
            }
        }
    }

    @Test
    void theRequirementComesFromTheEnvironmentVariableOrTheSystemProperty() {
        String before = System.getProperty(EngineGate.REQUIRE_PROPERTY);
        try {
            System.setProperty(EngineGate.REQUIRE_PROPERTY, "true");
            assertThat(EngineGate.required()).isTrue();
            System.setProperty(EngineGate.REQUIRE_PROPERTY, "false");
            assertThat(EngineGate.required()).as("only 'true' requires").isEqualTo("true".equalsIgnoreCase(System.getenv(EngineGate.REQUIRE_VARIABLE)));
        } finally {
            if (before == null) {
                System.clearProperty(EngineGate.REQUIRE_PROPERTY);
            } else {
                System.setProperty(EngineGate.REQUIRE_PROPERTY, before);
            }
        }
    }
}

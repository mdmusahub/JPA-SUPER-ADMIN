package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.TestAbortedException;

/**
 * The rule for a missing Docker in the demo-application check (Phase 14a, D-128), without a container: a skip carries a reason and
 * says "not passed", a CI that requires the engines fails instead of skipping, and a skip or failure without a reason cannot exist.
 */
class SampleEngineGateTest {

    @Test
    void whenDockerIsThereTheCheckRuns() {
        SampleEngineGate.Decision decision = SampleEngineGate.decide("PostgreSQL", "PostgreSqlSampleEngineTest", true, true);

        assertThat(decision.verdict()).isEqualTo(SampleEngineGate.Verdict.RUN);
        SampleEngineGate.apply(decision);
    }

    @Test
    void whenDockerIsMissingOnALaptopTheTestIsSkippedWithAReasonThatNamesTheEngineAndTheCommand() {
        SampleEngineGate.Decision decision = SampleEngineGate.decide("MySQL", "MySqlSampleEngineTest", false, false);

        assertThat(decision.verdict()).isEqualTo(SampleEngineGate.Verdict.SKIP);
        assertThat(decision.reason()).contains("MySQL").contains("Docker").contains("not passed")
                .contains("mvn -B -pl sample-app -am test -Dtest=MySqlSampleEngineTest");
        assertThatThrownBy(() -> SampleEngineGate.apply(decision)).isInstanceOf(TestAbortedException.class)
                .hasMessageContaining("not passed");
    }

    @Test
    void whenDockerIsMissingAndCiRequiresItTheBuildFailsAndDoesNotSkip() {
        SampleEngineGate.Decision decision = SampleEngineGate.decide("MySQL", "MySqlSampleEngineTest", false, true);

        assertThat(decision.verdict()).isEqualTo(SampleEngineGate.Verdict.FAIL);
        assertThat(decision.reason()).contains(SampleEngineGate.REQUIRE_VARIABLE);
        assertThatThrownBy(() -> SampleEngineGate.apply(decision)).isInstanceOf(AssertionFailedError.class)
                .isNotInstanceOf(TestAbortedException.class);
    }

    @Test
    void aSkipOrAFailureWithoutAReasonCannotBeBuilt() {
        assertThatThrownBy(() -> new SampleEngineGate.Decision(SampleEngineGate.Verdict.SKIP, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SampleEngineGate.Decision(SampleEngineGate.Verdict.FAIL, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyCombinationOfDockerAndTheRequirementGivesExactlyTheDocumentedVerdict() {
        for (boolean docker : new boolean[] {true, false}) {
            for (boolean required : new boolean[] {true, false}) {
                SampleEngineGate.Verdict expected = docker ? SampleEngineGate.Verdict.RUN
                        : required ? SampleEngineGate.Verdict.FAIL : SampleEngineGate.Verdict.SKIP;
                assertThat(SampleEngineGate.decide("H", "T", docker, required).verdict()).isEqualTo(expected);
            }
        }
    }

    @Test
    void aTestSkippedAlthoughTheEngineStartedIsAProblemAndASkipTheGateDecidedIsNot() {
        SampleEngineGate.Decision run = new SampleEngineGate.Decision(SampleEngineGate.Verdict.RUN, "");
        SampleEngineGate.Decision skip = new SampleEngineGate.Decision(SampleEngineGate.Verdict.SKIP, "SKIPPED, not passed: no Docker");

        assertThat(SampleEngineSkipLedger.problem(run, "MySQL", java.util.List.of("a stray assumption")))
                .startsWith("[MySQL]").contains("1 test(s) were skipped").contains("a stray assumption");
        assertThat(SampleEngineSkipLedger.problem(run, "MySQL", java.util.List.of())).isNull();
        assertThat(SampleEngineSkipLedger.problem(skip, "MySQL", java.util.List.of("SKIPPED, not passed: no Docker"))).isNull();
    }
}

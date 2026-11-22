package io.github.mdmusahub.jpaadmin.engines;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The check against a silent skip (Phase 14a, D-128, mutation d): when the gate said "run", a scenario that was skipped anyway fails
 * the class and says which engine and why; a skip the gate itself decided (no Docker on a laptop) is not a failure here.
 */
class EngineSkipLedgerTest {

    private static final EngineGate.Decision RUN = new EngineGate.Decision(EngineGate.Verdict.RUN, "");
    private static final EngineGate.Decision SKIP = new EngineGate.Decision(EngineGate.Verdict.SKIP, "SKIPPED, not passed: no Docker");

    @Test
    void aScenarioSkippedAlthoughTheEngineStartedIsAProblemThatNamesTheEngineAndTheReason() {
        String problem = EngineSkipLedger.problem(RUN, "PostgreSQL 17.11", List.of("a stray assumption", "another"));

        assertThat(problem).startsWith("[PostgreSQL 17.11]").contains("2 scenario(s) were skipped").contains("not a passed one")
                .contains("a stray assumption");
    }

    @Test
    void noSkippedScenarioIsNoProblem() {
        assertThat(EngineSkipLedger.problem(RUN, "H2", List.of())).isNull();
    }

    @Test
    void aSkipTheGateDecidedIsNotAProblemHere() {
        assertThat(EngineSkipLedger.problem(SKIP, "MySQL", List.of("SKIPPED, not passed: no Docker", "same"))).isNull();
    }
}

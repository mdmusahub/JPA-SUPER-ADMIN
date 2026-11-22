package io.github.mdmusahub.jpaadmin.engines;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

/** A failure of an engine test says which engine failed; a skip is left alone (Phase 14a, D-128). */
class EngineFailureNamesEngineTest {

    @Test
    void anAssertionFailureStartsWithTheEngineNameAndVersionAndKeepsTheOriginalAsItsCause() {
        AssertionError original = new AssertionError("expected 25 rows");

        Throwable labelled = EngineNamesFailures.label("PostgreSQL 17.11", original);

        assertThat(labelled).isInstanceOf(AssertionError.class).hasMessage("[PostgreSQL 17.11] expected 25 rows");
        assertThat(labelled.getCause()).isSameAs(original);
    }

    @Test
    void anErrorThatHasNoMessageIsNamedByItsClass() {
        Throwable labelled = EngineNamesFailures.label("MySQL 8.4.11", new IllegalStateException());

        assertThat(labelled.getMessage()).startsWith("[MySQL 8.4.11] ").contains("IllegalStateException");
    }

    @Test
    void aSkipPassesThroughUnchangedSoItIsNeverReportedAsAFailure() {
        TestAbortedException skip = new TestAbortedException("SKIPPED, not passed: no Docker");

        assertThat(EngineNamesFailures.label("MySQL 8.4.11", skip)).isSameAs(skip);
    }

    @Test
    void aMessageThatAlreadyNamesTheEngineIsNotNamedTwice() {
        AssertionError already = new AssertionError("[MySQL 8.4.11] 3 scenario(s) were skipped");

        assertThat(EngineNamesFailures.label("MySQL 8.4.11", already)).isSameAs(already);
    }
}

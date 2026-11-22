package io.github.mdmusahub.jpaadmin.perf;

import io.github.mdmusahub.jpaadmin.engines.EngineDatabase;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * The performance measurements on H2 in memory (Phase 14a, D-131). Runs only with the Maven profile {@code perf}: without the
 * system property it is skipped with the reason, and the default surefire configuration does not even select it.
 */
@EnabledIfSystemProperty(named = PerfSwitch.PROPERTY, matches = "true", disabledReason = PerfSwitch.OFF)
class H2PerfTest extends AbstractPerfMeasurement {

    @Override
    EngineDatabase newDatabase() {
        return EngineDatabase.h2();
    }

    @Override
    String analyze(String table) {
        return "analyze table " + table;
    }
}

package io.github.mdmusahub.jpaadmin.sample;

import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;

/**
 * Puts the SQL logging of the "log" tests (the ones that read the captured output for leaks) back to what they need.
 *
 * <p>Several of those tests have identical properties, so Spring reuses one cached application context for them. Every <em>new</em>
 * context with other properties that is created in the same JVM in between (a test of another page size, a test with another
 * switch) runs Boot's logging initialisation again and resets the levels, and the reused context then logs nothing: the test
 * fails for a reason that has nothing to do with what it checks (found in Phase 13 when two page-size contexts were added, and
 * this is what the order dependence of TEST-6 looks like). Setting the levels in {@code @BeforeEach} makes each test say what it
 * needs instead of relying on which context ran last.
 */
final class SqlLogLevels {

    private SqlLogLevels() {
    }

    /** The statements, the bound values, the extracted values and the console's own TRACE lines. */
    static void apply() {
        applyWithoutValues();
        LoggingSystem system = LoggingSystem.get(SqlLogLevels.class.getClassLoader());
        system.setLogLevel("org.hibernate.orm.jdbc.bind", LogLevel.TRACE);
        system.setLogLevel("org.hibernate.orm.jdbc.extract", LogLevel.TRACE);
    }

    /** The statements and the console's own TRACE lines only: a test that writes secrets must not turn value logging on. */
    static void applyWithoutValues() {
        LoggingSystem system = LoggingSystem.get(SqlLogLevels.class.getClassLoader());
        system.setLogLevel("org.hibernate.SQL", LogLevel.DEBUG);
        system.setLogLevel("io.github.mdmusahub.jpaadmin", LogLevel.TRACE);
    }
}

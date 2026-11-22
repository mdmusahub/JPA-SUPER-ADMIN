package io.github.mdmusahub.jpaadmin.sample;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Objects;

/**
 * What a test class that needs a database in a container does when it cannot have one (Phase 14a, D-128). The same rule as the
 * one in {@code jpa-admin-core}'s engine tests (a module's test classes cannot be shared with another module without a test jar,
 * and a test jar of core would put its four persistence units on this module's classpath):
 * <ul>
 *   <li>Docker is there: run;</li>
 *   <li>Docker is not there, nobody required it: skip <b>with a reason</b> (taken test by test, so the report says "Skipped: n"
 *       and every skipped test holds the reason, never "0 tests run");</li>
 *   <li>Docker is not there and {@code JPA_ADMIN_REQUIRE_ENGINES} is {@code true} (CI): fail.</li>
 * </ul>
 */
final class SampleEngineGate {

    static final String REQUIRE_VARIABLE = "JPA_ADMIN_REQUIRE_ENGINES";
    static final String REQUIRE_PROPERTY = "jpa.admin.require-engines";

    /** The system property that makes this machine look as if it had no Docker, to prove what the gate does. */
    static final String NO_DOCKER_PROPERTY = "jpa.admin.test.no-docker";

    enum Verdict { RUN, SKIP, FAIL }

    record Decision(Verdict verdict, String reason) {

        Decision {
            Objects.requireNonNull(verdict, "verdict");
            Objects.requireNonNull(reason, "reason");
            if (verdict != Verdict.RUN && reason.isBlank()) {
                throw new IllegalArgumentException("a skip or a failure must say why");
            }
        }
    }

    private SampleEngineGate() {
    }

    static Decision decide(String engine, String testClass, boolean dockerAvailable, boolean required) {
        if (dockerAvailable) {
            return new Decision(Verdict.RUN, "");
        }
        String command = "mvn -B -pl sample-app -am test -Dtest=" + testClass + " -Dsurefire.failIfNoSpecifiedTests=false";
        if (required) {
            return new Decision(Verdict.FAIL, "FAILED: Docker is not available, and " + REQUIRE_VARIABLE + " is true, so the "
                    + engine + " check of the demo application cannot be skipped. Start Docker, or run it where it runs: " + command);
        }
        return new Decision(Verdict.SKIP, "SKIPPED, not passed: Docker is not available on this machine, so the " + engine
                + " check of the demo application did not run. Run it where Docker runs: " + command + " (on CI, "
                + REQUIRE_VARIABLE + "=true makes this a failure)");
    }

    static boolean required() {
        return "true".equalsIgnoreCase(System.getenv(REQUIRE_VARIABLE))
                || "true".equalsIgnoreCase(System.getProperty(REQUIRE_PROPERTY));
    }

    static boolean dockerAvailable() {
        if (Boolean.getBoolean(NO_DOCKER_PROPERTY)) {
            return false;
        }
        try {
            return org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException | LinkageError unavailable) {
            return false;
        }
    }

    static void apply(Decision decision) {
        switch (decision.verdict()) {
            case RUN -> { }
            case SKIP -> assumeTrue(false, decision.reason());
            case FAIL -> fail(decision.reason());
        }
    }
}

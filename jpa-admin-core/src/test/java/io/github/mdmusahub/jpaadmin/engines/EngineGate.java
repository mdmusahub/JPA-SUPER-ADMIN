package io.github.mdmusahub.jpaadmin.engines;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Objects;

/**
 * Decides what a test class that needs a database in a container does when it cannot have one (Phase 14a, D-128).
 *
 * <p>Three outcomes and no others:
 * <ul>
 *   <li>{@link Verdict#RUN}: Docker is there, go on;</li>
 *   <li>{@link Verdict#SKIP}: Docker is not there and nobody required it (a laptop): the class is skipped <b>with a reason</b> that
 *       names the engine, says why and gives the exact command to run elsewhere. It is never reported as passed;</li>
 *   <li>{@link Verdict#FAIL}: Docker is not there and {@code JPA_ADMIN_REQUIRE_ENGINES} (or the system property
 *       {@code jpa.admin.require-engines}) is {@code true}, as it is on CI: a missing Docker must not hide a database defect, so the
 *       build fails.</li>
 * </ul>
 *
 * <p>A silent skip (no reason) is not an outcome: the {@link Decision} refuses to be built without a reason.
 */
public final class EngineGate {

    /** The environment variable CI sets. */
    public static final String REQUIRE_VARIABLE = "JPA_ADMIN_REQUIRE_ENGINES";

    /** The same switch as a system property, for a build started with {@code -D}. */
    public static final String REQUIRE_PROPERTY = "jpa.admin.require-engines";

    /** What to do. */
    public enum Verdict { RUN, SKIP, FAIL }

    /**
     * The verdict and, for a skip or a failure, the sentence that says why.
     *
     * @param verdict what to do
     * @param reason  empty text is refused for {@link Verdict#SKIP} and {@link Verdict#FAIL}; it is {@code ""} for a run
     */
    public record Decision(Verdict verdict, String reason) {

        public Decision {
            Objects.requireNonNull(verdict, "verdict");
            Objects.requireNonNull(reason, "reason");
            if (verdict != Verdict.RUN && reason.isBlank()) {
                throw new IllegalArgumentException("a skip or a failure must say why");
            }
        }
    }

    private EngineGate() {
    }

    /**
     * The decision for one engine.
     *
     * @param engine          the engine's name, for the sentence (for example {@code PostgreSQL})
     * @param testClass       the test class to run elsewhere, for the command in the sentence
     * @param dockerAvailable whether a Docker daemon answered
     * @param required        whether a missing Docker must fail the build
     */
    public static Decision decide(String engine, String testClass, boolean dockerAvailable, boolean required) {
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(testClass, "testClass");
        if (dockerAvailable) {
            return new Decision(Verdict.RUN, "");
        }
        String command = "mvn -B -pl jpa-admin-core test -Dtest=" + testClass;
        if (required) {
            return new Decision(Verdict.FAIL, "FAILED: Docker is not available, and " + REQUIRE_VARIABLE
                    + " is true, so the " + engine + " tests cannot be skipped. Start Docker, or run them where it runs: "
                    + command);
        }
        return new Decision(Verdict.SKIP, "SKIPPED, not passed: Docker is not available on this machine, so the " + engine
                + " tests did not run. Run them where Docker runs: " + command + " (on CI, " + REQUIRE_VARIABLE
                + "=true makes this a failure)");
    }

    /** The system property that makes this machine look as if it had no Docker, to prove what the gate does. */
    public static final String NO_DOCKER_PROPERTY = "jpa.admin.test.no-docker";

    /** Whether a Docker daemon answers (always {@code false} when {@link #NO_DOCKER_PROPERTY} is {@code true}). */
    public static boolean dockerAvailable() {
        if (Boolean.getBoolean(NO_DOCKER_PROPERTY)) {
            return false;
        }
        try {
            return org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException | LinkageError unavailable) {
            return false;
        }
    }

    /** Whether a missing Docker must fail the build: the environment variable or the system property is {@code true}. */
    public static boolean required() {
        return "true".equalsIgnoreCase(System.getenv(REQUIRE_VARIABLE))
                || "true".equalsIgnoreCase(System.getProperty(REQUIRE_PROPERTY));
    }

    /**
     * Carries a decision out: returns for a run, aborts the test class with the reason for a skip (the reason is what the test
     * report shows), fails the test class for a failure.
     */
    public static void apply(Decision decision) {
        switch (decision.verdict()) {
            case RUN -> { }
            case SKIP -> assumeTrue(false, decision.reason());
            case FAIL -> fail(decision.reason());
        }
    }
}

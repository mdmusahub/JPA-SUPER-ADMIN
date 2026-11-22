package io.github.mdmusahub.jpaadmin.sample;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * What a real-browser test does when the machine has no Chrome or Edge (Phase 14a, D-129). Every browser test asks here, so there
 * is one rule and not one per class:
 * <ul>
 *   <li>a browser is found: run;</li>
 *   <li>none is found and nobody required one (a laptop): the test is <b>skipped with a reason</b> that says it was not passed and
 *       how to point at a browser; the report shows "Skipped: n" and every skipped test holds the reason;</li>
 *   <li>none is found and {@code JPA_ADMIN_REQUIRE_BROWSER} (or {@code jpa.admin.require-browser}) is {@code true}, as on CI: the
 *       test <b>fails</b>. A skipped browser test is not a passed one, and a build that can hide a layout defect behind a
 *       missing browser is not a build.</li>
 * </ul>
 * The browser is the one the runner already has (Google Chrome on {@code ubuntu-latest}); nothing is downloaded.
 */
final class BrowserPolicy {

    static final String REQUIRE_VARIABLE = "JPA_ADMIN_REQUIRE_BROWSER";
    static final String REQUIRE_PROPERTY = "jpa.admin.require-browser";

    /** The system property that makes this machine look as if it had no browser, to prove what the policy does. */
    static final String NO_BROWSER_PROPERTY = "jpa.admin.test.no-browser";

    static final String SKIP_REASON = "SKIPPED, not passed: no Chrome or Edge was found on this machine "
            + "(set -Dtest.chrome=<path> or CHROME_BIN to run the browser tests; on CI " + REQUIRE_VARIABLE
            + "=true makes this a failure)";

    static final String FAIL_REASON = "FAILED: no Chrome or Edge was found, and " + REQUIRE_VARIABLE
            + " is true, so the browser tests cannot be skipped. Install one on the machine, or set -Dtest.chrome=<path> or CHROME_BIN";

    enum Verdict { RUN, SKIP, FAIL }

    /**
     * @param verdict what to do
     * @param browser the browser to start, for a run
     * @param reason  the sentence for a skip or a failure; never blank for those
     */
    record Decision(Verdict verdict, Path browser, String reason) {

        Decision {
            Objects.requireNonNull(verdict, "verdict");
            Objects.requireNonNull(reason, "reason");
            if (verdict == Verdict.RUN && browser == null) {
                throw new IllegalArgumentException("a run needs a browser");
            }
            if (verdict != Verdict.RUN && reason.isBlank()) {
                throw new IllegalArgumentException("a skip or a failure must say why");
            }
        }
    }

    private BrowserPolicy() {
    }

    static Decision decide(Optional<Path> browser, boolean required) {
        if (browser.isPresent()) {
            return new Decision(Verdict.RUN, browser.get(), "");
        }
        return required ? new Decision(Verdict.FAIL, null, FAIL_REASON) : new Decision(Verdict.SKIP, null, SKIP_REASON);
    }

    static boolean required() {
        return "true".equalsIgnoreCase(System.getenv(REQUIRE_VARIABLE))
                || "true".equalsIgnoreCase(System.getProperty(REQUIRE_PROPERTY));
    }

    /** The browser of this machine, or empty (also when the test-only switch says there is none). */
    static Optional<Path> find() {
        return Boolean.getBoolean(NO_BROWSER_PROPERTY) ? Optional.empty() : HeadlessChrome.find();
    }

    /** Carries a decision out: returns the browser for a run, skips the test with the reason, or fails it. */
    static Path apply(Decision decision) {
        switch (decision.verdict()) {
            case SKIP -> assumeTrue(false, decision.reason());
            case FAIL -> fail(decision.reason());
            case RUN -> { }
        }
        return decision.browser();
    }

    /** What every browser test calls first: the browser to use, or the skip or the failure. */
    static Path require() {
        return apply(decide(find(), required()));
    }
}

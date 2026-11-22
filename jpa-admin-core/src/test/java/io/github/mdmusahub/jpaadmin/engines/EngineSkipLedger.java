package io.github.mdmusahub.jpaadmin.engines;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

/**
 * Writes down every scenario of an engine class that was <b>aborted</b> (an assumption that did not hold), and checks at the end of
 * the class that none was aborted <b>although the engine started</b> (Phase 14a, D-128, mutation d).
 *
 * <p>Why: {@link EngineGate} decides, before anything runs, what a missing Docker means. But a skip that comes from anywhere else
 * (a stray {@code assumeTrue(false)}, an {@code @Disabled}, a condition that was meant for another machine) is invisible to it: with
 * Docker running, every scenario of an engine could be skipped and the build would stay green. This class closes that: when the
 * gate said "run", a skipped scenario is a failure of the class, named with the engine and the reason.
 */
public final class EngineSkipLedger implements TestWatcher {

    @Override
    public void testAborted(ExtensionContext context, Throwable cause) {
        Optional<Object> instance = context.getTestInstance();
        if (instance.isPresent() && instance.get() instanceof EngineTestBase base) {
            base.recordAbort(cause == null || cause.getMessage() == null ? "aborted without a message" : cause.getMessage());
        }
    }

    /**
     * The failure to raise at the end of a class, or {@code null} when all is well.
     *
     * @param gate    what the gate said for this class
     * @param engine  the engine's name and version
     * @param aborted the reasons of the scenarios that were aborted
     * @return a message when the gate said "run" and scenarios were aborted anyway; {@code null} otherwise (a skip the gate decided
     *         is expected to abort every scenario, and a fail is reported where it happened)
     */
    static String problem(EngineGate.Decision gate, String engine, List<String> aborted) {
        if (gate.verdict() != EngineGate.Verdict.RUN || aborted.isEmpty()) {
            return null;
        }
        return "[" + engine + "] " + aborted.size() + " scenario(s) were skipped although the engine started and Docker is available "
                + "(a skipped scenario is not a passed one). First reason: " + aborted.get(0);
    }
}

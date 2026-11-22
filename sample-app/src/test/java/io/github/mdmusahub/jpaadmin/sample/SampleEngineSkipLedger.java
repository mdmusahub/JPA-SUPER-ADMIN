package io.github.mdmusahub.jpaadmin.sample;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

/**
 * Writes down every test of a demo-application engine class that was aborted, and checks at the end of the class that none was
 * aborted although the engine started (Phase 14a, D-128, mutation d). The same idea as the ledger of the core engine tests: the gate
 * decides what a missing Docker means, and this catches a skip that comes from anywhere else.
 */
final class SampleEngineSkipLedger implements TestWatcher {

    @Override
    public void testAborted(ExtensionContext context, Throwable cause) {
        Optional<Object> instance = context.getTestInstance();
        if (instance.isPresent() && instance.get() instanceof AbstractSampleEngineTest test) {
            test.recordAbort(cause == null || cause.getMessage() == null ? "aborted without a message" : cause.getMessage());
        }
    }

    /** The failure to raise at the end of a class, or {@code null}. */
    static String problem(SampleEngineGate.Decision gate, String engine, List<String> aborted) {
        if (gate.verdict() != SampleEngineGate.Verdict.RUN || aborted.isEmpty()) {
            return null;
        }
        return "[" + engine + "] " + aborted.size() + " test(s) were skipped although the engine started and Docker is available "
                + "(a skipped test is not a passed one). First reason: " + aborted.get(0);
    }
}

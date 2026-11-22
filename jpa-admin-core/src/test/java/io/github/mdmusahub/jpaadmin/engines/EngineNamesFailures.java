package io.github.mdmusahub.jpaadmin.engines;

import java.util.Optional;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.LifecycleMethodExecutionExceptionHandler;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.opentest4j.TestAbortedException;

/**
 * Puts the engine's name and version in front of every failure of an engine test, so a red build says which database broke
 * ({@code [PostgreSQL 17.11] expected ...}) without reading the class name. A skip (an assumption that did not hold) passes
 * through untouched: it is not a failure.
 */
public final class EngineNamesFailures implements TestExecutionExceptionHandler, LifecycleMethodExecutionExceptionHandler {

    @Override
    public void handleTestExecutionException(ExtensionContext context, Throwable failure) throws Throwable {
        throw label(engineOf(context), failure);
    }

    @Override
    public void handleBeforeAllMethodExecutionException(ExtensionContext context, Throwable failure) throws Throwable {
        throw label(engineOf(context), failure);
    }

    @Override
    public void handleBeforeEachMethodExecutionException(ExtensionContext context, Throwable failure) throws Throwable {
        throw label(engineOf(context), failure);
    }

    @Override
    public void handleAfterEachMethodExecutionException(ExtensionContext context, Throwable failure) throws Throwable {
        throw label(engineOf(context), failure);
    }

    @Override
    public void handleAfterAllMethodExecutionException(ExtensionContext context, Throwable failure) throws Throwable {
        throw label(engineOf(context), failure);
    }

    private static String engineOf(ExtensionContext context) {
        Optional<Object> instance = context.getTestInstance();
        if (instance.isPresent() && instance.get() instanceof EngineTestBase base) {
            return base.engineLabel();
        }
        return "unknown engine";
    }

    /**
     * The failure with {@code [engine]} in front of its message. A skip is returned as it is; any other failure becomes an
     * {@link AssertionError} that keeps the original as its cause (so the stack trace is still there).
     */
    static Throwable label(String engine, Throwable failure) {
        if (failure instanceof TestAbortedException) {
            return failure;
        }
        String message = failure.getMessage() == null ? failure.getClass().getName() : failure.getMessage();
        if (message.startsWith("[" + engine + "] ")) {
            return failure; // already says which engine (the skip ledger's message does)
        }
        return new AssertionError("[" + engine + "] " + message, failure);
    }
}

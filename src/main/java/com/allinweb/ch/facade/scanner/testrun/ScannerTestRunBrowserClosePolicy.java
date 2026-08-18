package com.allinweb.ch.facade.scanner.testrun;

import java.util.Objects;
import java.util.function.LongPredicate;
import java.util.function.LongSupplier;

/** Serializes run-owned STOP and browser-close decisions for the shared Playwright session. */
public final class ScannerTestRunBrowserClosePolicy {
    private final LongSupplier activeExecutionId;
    private final LongPredicate requestExecutionStop;
    private final LongPredicate executionInterrupted;
    private long browserCloseCommittedExecutionId;

    public ScannerTestRunBrowserClosePolicy(
            LongSupplier activeExecutionId,
            LongPredicate requestExecutionStop,
            LongPredicate executionInterrupted) {
        this.activeExecutionId = Objects.requireNonNull(activeExecutionId, "activeExecutionId");
        this.requestExecutionStop = Objects.requireNonNull(requestExecutionStop, "requestExecutionStop");
        this.executionInterrupted = Objects.requireNonNull(executionInterrupted, "executionInterrupted");
    }

    /** Policy used before the Scanner runtime installs its run-aware instance. */
    public static ScannerTestRunBrowserClosePolicy unrestricted() {
        return new ScannerTestRunBrowserClosePolicy(() -> 0L, executionId -> false, executionId -> false);
    }

    /**
     * Accepts STOP only while this execution has not committed an explicit browser close.
     * Synchronizing this with {@link #closeBrowserIfAllowed} guarantees that an accepted STOP wins
     * every later close decision.
     */
    public synchronized boolean requestStop(long executionId) {
        if (executionId <= 0L || browserCloseCommittedExecutionId == executionId) return false;
        return requestExecutionStop.test(executionId);
    }

    /**
     * Runs an execution-owned browser close only when the active run has not been interrupted.
     * Application/workspace shutdown deliberately bypasses this method and remains unconditional.
     */
    public synchronized boolean closeBrowserIfAllowed(boolean closeRequested, Runnable closeBrowser) {
        Objects.requireNonNull(closeBrowser, "closeBrowser");
        if (!closeRequested) return false;

        long executionId = activeExecutionId.getAsLong();
        if (executionId > 0L
                && (browserCloseCommittedExecutionId == executionId || executionInterrupted.test(executionId))) {
            return false;
        }

        closeBrowser.run();
        if (executionId > 0L) browserCloseCommittedExecutionId = executionId;
        return true;
    }
}

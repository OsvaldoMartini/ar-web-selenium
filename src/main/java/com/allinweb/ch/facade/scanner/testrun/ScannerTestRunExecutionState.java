package com.allinweb.ch.facade.scanner.testrun;

import java.util.concurrent.atomic.AtomicLong;

public final class ScannerTestRunExecutionState {
    private final AtomicLong activeExecutionId;
    private final AtomicLong lastSubmittedExecutionId;
    private final AtomicLong completedExecutionId;
    private final TestRunExecutionOutcomeTracker outcomes;

    public ScannerTestRunExecutionState(
            AtomicLong activeExecutionId,
            AtomicLong lastSubmittedExecutionId,
            AtomicLong completedExecutionId,
            TestRunExecutionOutcomeTracker outcomes) {
        this.activeExecutionId = activeExecutionId;
        this.lastSubmittedExecutionId = lastSubmittedExecutionId;
        this.completedExecutionId = completedExecutionId;
        this.outcomes = outcomes;
    }

    public long activeExecutionId() {
        return activeExecutionId.get();
    }

    public long lastSubmittedExecutionId() {
        return lastSubmittedExecutionId.get();
    }

    public long completedExecutionId() {
        return completedExecutionId.get();
    }

    public long currentExecutionId() {
        long active = activeExecutionId();
        return active > 0 ? active : lastSubmittedExecutionId();
    }

    public boolean isExecutionComplete(long executionId) {
        return executionId <= 0 || completedExecutionId() >= executionId;
    }

    public boolean requestStop(long executionId) {
        return outcomes.requestStop(executionId);
    }

    public boolean isExecutionInterrupted(long executionId) {
        return executionId > 0L && outcomes.isInterrupted(executionId);
    }

    public void completeExecution(long executionId, boolean executionPassed) {
        outcomes.completed(executionId, executionPassed);
        completedExecutionId.accumulateAndGet(executionId, Math::max);
    }

    public void clearActiveExecution(long executionId) {
        activeExecutionId.compareAndSet(executionId, 0L);
    }

    public String terminalState(long executionId) {
        return outcomes.terminalOutcome(executionId).name();
    }
}

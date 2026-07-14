package com.allinweb.ch.component.pane;

import java.util.concurrent.atomic.AtomicLong;

final class ScannerTestRunExecutionState {
    private final AtomicLong activeExecutionId;
    private final AtomicLong lastSubmittedExecutionId;
    private final AtomicLong completedExecutionId;
    private final TestRunExecutionOutcomeTracker outcomes;

    ScannerTestRunExecutionState(
            AtomicLong activeExecutionId,
            AtomicLong lastSubmittedExecutionId,
            AtomicLong completedExecutionId,
            TestRunExecutionOutcomeTracker outcomes) {
        this.activeExecutionId = activeExecutionId;
        this.lastSubmittedExecutionId = lastSubmittedExecutionId;
        this.completedExecutionId = completedExecutionId;
        this.outcomes = outcomes;
    }

    long activeExecutionId() {
        return activeExecutionId.get();
    }

    long lastSubmittedExecutionId() {
        return lastSubmittedExecutionId.get();
    }

    long completedExecutionId() {
        return completedExecutionId.get();
    }

    long currentExecutionId() {
        long active = activeExecutionId();
        return active > 0 ? active : lastSubmittedExecutionId();
    }

    boolean isExecutionComplete(long executionId) {
        return executionId <= 0 || completedExecutionId() >= executionId;
    }

    boolean requestStop(long executionId) {
        return outcomes.requestStop(executionId);
    }

    void completeExecution(long executionId, boolean executionPassed) {
        outcomes.completed(executionId, executionPassed);
        completedExecutionId.accumulateAndGet(executionId, Math::max);
    }

    void clearActiveExecution(long executionId) {
        activeExecutionId.compareAndSet(executionId, 0L);
    }

    String terminalState(long executionId) {
        return outcomes.terminalOutcome(executionId).name();
    }
}

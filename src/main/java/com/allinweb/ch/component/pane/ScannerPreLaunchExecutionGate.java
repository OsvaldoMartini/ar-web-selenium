package com.allinweb.ch.component.pane;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongPredicate;

final class ScannerPreLaunchExecutionGate {
    private final AtomicBoolean running;
    private final AtomicLong executionSequence;
    private final AtomicLong activeExecutionId;
    private final AtomicLong lastSubmittedExecutionId;
    private final TestRunExecutionOutcomeTracker executionOutcomes;

    ScannerPreLaunchExecutionGate(
            AtomicBoolean running,
            AtomicLong executionSequence,
            AtomicLong activeExecutionId,
            AtomicLong lastSubmittedExecutionId,
            TestRunExecutionOutcomeTracker executionOutcomes) {
        this.running = running;
        this.executionSequence = executionSequence;
        this.activeExecutionId = activeExecutionId;
        this.lastSubmittedExecutionId = lastSubmittedExecutionId;
        this.executionOutcomes = executionOutcomes;
    }

    StartAttempt startIfIdle(LongPredicate executionComplete) {
        long currentExecution = activeExecutionId.get();
        if (currentExecution > 0 && !executionComplete.test(currentExecution)) {
            return StartAttempt.activeExecution();
        }
        if (!running.compareAndSet(false, true)) {
            return StartAttempt.alreadyRunning();
        }
        long executionId = executionSequence.incrementAndGet();
        executionOutcomes.started(executionId);
        activeExecutionId.set(executionId);
        lastSubmittedExecutionId.set(executionId);
        return StartAttempt.started(executionId);
    }

    record StartAttempt(Status status, long executionId) {
        private static StartAttempt started(long executionId) {
            return new StartAttempt(Status.STARTED, executionId);
        }

        private static StartAttempt activeExecution() {
            return new StartAttempt(Status.ACTIVE_EXECUTION, 0L);
        }

        private static StartAttempt alreadyRunning() {
            return new StartAttempt(Status.ALREADY_RUNNING, 0L);
        }

        boolean started() {
            return status == Status.STARTED;
        }
    }

    enum Status {
        STARTED,
        ACTIVE_EXECUTION,
        ALREADY_RUNNING
    }
}

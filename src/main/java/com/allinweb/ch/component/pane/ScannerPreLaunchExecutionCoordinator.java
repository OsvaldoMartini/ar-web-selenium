package com.allinweb.ch.component.pane;

import java.util.concurrent.ExecutorService;
import java.util.function.LongPredicate;

final class ScannerPreLaunchExecutionCoordinator {
    private final ScannerPreLaunchExecutionGate executionGate;
    private final ExecutorService executorService;
    private final ScannerPreLaunchExecutionTask.Operations executionOperations;
    private final ScannerPreLaunchWindowBookkeeping windowBookkeeping;
    private final LongPredicate executionComplete;
    private final Operations operations;

    ScannerPreLaunchExecutionCoordinator(
            ScannerPreLaunchExecutionGate executionGate,
            ExecutorService executorService,
            ScannerPreLaunchExecutionTask.Operations executionOperations,
            ScannerPreLaunchWindowBookkeeping windowBookkeeping,
            LongPredicate executionComplete,
            Operations operations) {
        this.executionGate = executionGate;
        this.executorService = executorService;
        this.executionOperations = executionOperations;
        this.windowBookkeeping = windowBookkeeping;
        this.executionComplete = executionComplete;
        this.operations = operations;
    }

    long recallJobExecutionId() {
        long submittedExecutionId = 0L;
        ScannerPreLaunchExecutionGate.StartAttempt startAttempt =
                executionGate.startIfIdle(executionComplete);
        if (startAttempt.status() == ScannerPreLaunchExecutionGate.Status.ACTIVE_EXECUTION) {
            operations.info("recallJob() requested while executeJob() was still active.");
            return 0L;
        }
        if (startAttempt.started()) {
            long executionId = startAttempt.executionId();
            ScannerPreLaunchExecutionSubmission submission = new ScannerPreLaunchExecutionSubmission(
                    executorService,
                    executionOperations,
                    error -> operations.error(
                            "Error submitting to executorServicePreLaunch: {}",
                            error.getMessage(),
                            error));
            if (submission.submit(executionId)) {
                submittedExecutionId = executionId;
            }
        } else {
            operations.info("recallJob() requested while executeJob() was running.");
        }

        windowBookkeeping.refreshChangedWindows();
        return submittedExecutionId;
    }

    interface Operations {
        void info(String message, Object... args);

        void error(String message, Object... args);
    }
}

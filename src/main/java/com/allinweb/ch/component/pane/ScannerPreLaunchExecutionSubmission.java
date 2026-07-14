package com.allinweb.ch.component.pane;

import java.util.concurrent.ExecutorService;

final class ScannerPreLaunchExecutionSubmission {
    private final ExecutorService executorService;
    private final ScannerPreLaunchExecutionTask.Operations operations;
    private final FailureReporter failureReporter;

    ScannerPreLaunchExecutionSubmission(
            ExecutorService executorService,
            ScannerPreLaunchExecutionTask.Operations operations,
            FailureReporter failureReporter) {
        this.executorService = executorService;
        this.operations = operations;
        this.failureReporter = failureReporter;
    }

    boolean submit(long executionId) {
        try {
            executorService.submit(new ScannerPreLaunchExecutionTask(executionId, operations));
            return true;
        } catch (Exception error) {
            operations.completeExecution(executionId, false);
            operations.clearActiveExecution(executionId);
            operations.markNotRunning();
            operations.stopScreenshotLoop();
            operations.reenableLaunchButton();
            failureReporter.report(error);
            return false;
        }
    }

    interface FailureReporter {
        void report(Exception error);
    }
}

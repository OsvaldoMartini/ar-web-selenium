package com.allinweb.ch.facade.scanner.prelaunch;

import java.util.concurrent.ExecutorService;

public final class ScannerPreLaunchExecutionSubmission {
    private final ExecutorService executorService;
    private final ScannerPreLaunchExecutionTask.Operations operations;
    private final FailureReporter failureReporter;

    public ScannerPreLaunchExecutionSubmission(
            ExecutorService executorService,
            ScannerPreLaunchExecutionTask.Operations operations,
            FailureReporter failureReporter) {
        this.executorService = executorService;
        this.operations = operations;
        this.failureReporter = failureReporter;
    }

    public boolean submit(long executionId) {
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

    public interface FailureReporter {
        void report(Exception error);
    }
}

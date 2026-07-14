package com.allinweb.ch.component.pane;

final class ScannerPreLaunchExecutionTask implements Runnable {
    private final long executionId;
    private final Operations operations;

    ScannerPreLaunchExecutionTask(long executionId, Operations operations) {
        this.executionId = executionId;
        this.operations = operations;
    }

    @Override
    public void run() {
        boolean executionPassed = false;
        try {
            executionPassed = operations.executeJob();
        } catch (Throwable error) {
            operations.reportExecutionError(error);
        } finally {
            operations.completeExecution(executionId, executionPassed);
            operations.clearActiveExecution(executionId);
            operations.markNotRunning();
            operations.stopScreenshotLoop();
            operations.reenableLaunchButton();
        }
    }

    interface Operations {
        boolean executeJob();

        void reportExecutionError(Throwable error);

        void completeExecution(long executionId, boolean executionPassed);

        void clearActiveExecution(long executionId);

        void markNotRunning();

        void stopScreenshotLoop();

        void reenableLaunchButton();
    }
}

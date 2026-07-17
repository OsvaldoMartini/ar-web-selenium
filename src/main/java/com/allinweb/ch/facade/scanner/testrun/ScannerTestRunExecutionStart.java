package com.allinweb.ch.facade.scanner.testrun;

public final class ScannerTestRunExecutionStart {
    private final Operations operations;

    public ScannerTestRunExecutionStart(Operations operations) {
        this.operations = operations;
    }

    public Result start() {
        if (!operations.openBrowser()) {
            operations.setRunSingleBlock(false);
            return Result.browserOpenFailed();
        }

        operations.resetInstructionExecutionFlags();
        long executionId = operations.recallJobExecutionId();
        if (executionId <= 0L && !operations.isJobRunning()) {
            operations.setRunSingleBlock(false);
        }
        return Result.started(executionId);
    }

    public record Result(Status status, long executionId) {
        private static Result browserOpenFailed() {
            return new Result(Status.BROWSER_OPEN_FAILED, 0L);
        }

        private static Result started(long executionId) {
            return new Result(Status.STARTED, executionId);
        }
    }

    public enum Status {
        STARTED,
        BROWSER_OPEN_FAILED
    }

    public interface Operations {
        boolean openBrowser();

        void resetInstructionExecutionFlags();

        long recallJobExecutionId();

        boolean isJobRunning();

        void setRunSingleBlock(boolean runSingleBlock);
    }
}

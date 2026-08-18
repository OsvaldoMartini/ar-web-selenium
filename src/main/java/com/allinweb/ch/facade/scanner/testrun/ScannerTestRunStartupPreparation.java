package com.allinweb.ch.facade.scanner.testrun;

import com.allinweb.ch.model.BotJobLoadDTO;

public final class ScannerTestRunStartupPreparation {
    private final Operations operations;

    public ScannerTestRunStartupPreparation(Operations operations) {
        this.operations = operations;
    }

    public Result prepare(BotJobLoadDTO botJob, int blockOrderNumber, boolean runSingleBlock) {
        if (botJob == null) {
            return Result.missingBotJob();
        }

        long activeExecution = operations.activeExecutionId();
        if ((activeExecution > 0 && !operations.isExecutionComplete(activeExecution)) || operations.isJobRunning()) {
            return Result.alreadyRunning();
        }

        operations.ensureDriver();
        operations.setCurrentBotJob(botJob);
        operations.setInterceptBotJob(false);
        operations.markNotRunning();

        try {
            operations.setExcelPath(operations.resolveExcelBasePath());
        } catch (Exception error) {
            operations.reportExcelPathError(error);
        }

        operations.setExecuteSpecificBlock(blockOrderNumber < 0 ? 0 : blockOrderNumber - 1);
        operations.setRunSingleBlock(runSingleBlock);
        operations.clearFields();
        return Result.ready();
    }

    public record Result(Status status) {
        private static Result ready() {
            return new Result(Status.READY);
        }

        private static Result missingBotJob() {
            return new Result(Status.MISSING_BOT_JOB);
        }

        private static Result alreadyRunning() {
            return new Result(Status.ALREADY_RUNNING);
        }
    }

    public enum Status {
        READY,
        MISSING_BOT_JOB,
        ALREADY_RUNNING
    }

    public interface Operations {
        long activeExecutionId();

        boolean isExecutionComplete(long executionId);

        boolean isJobRunning();

        void ensureDriver();

        void setCurrentBotJob(BotJobLoadDTO botJob);

        void setInterceptBotJob(boolean intercept);

        void markNotRunning();

        String resolveExcelBasePath();

        void setExcelPath(String excelPath);

        void reportExcelPathError(Exception error);

        void setExecuteSpecificBlock(int executeSpecificBlock);

        void setRunSingleBlock(boolean runSingleBlock);

        void clearFields();
    }
}

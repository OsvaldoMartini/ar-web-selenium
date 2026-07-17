package com.allinweb.ch.component.pane;

import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.facade.scanner.testrun.ScannerTestRunExecutionStart;
import com.allinweb.ch.facade.scanner.testrun.ScannerTestRunExcelPreparation;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.util.ErrorMessage;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

final class ScannerTestRunPreparationFlow {
    private final ScannerTestRunStartupPreparation startupPreparation;
    private final ScannerTestRunDefinitionLoad definitionLoad;
    private final ScannerTestRunDefinitionValidation definitionValidation;
    private final ScannerTestRunBotJobPreparation botJobPreparation;
    private final ScannerTestRunExcelPreparation excelPreparation;
    private final ScannerTestRunExecutionStart executionStart;
    private final Supplier<BotJobLoadDTO> currentBotJob;
    private final Supplier<String> excelPath;
    private final PerformLists performLists;

    ScannerTestRunPreparationFlow(
            ScannerTestRunStartupPreparation startupPreparation,
            ScannerTestRunDefinitionLoad definitionLoad,
            ScannerTestRunDefinitionValidation definitionValidation,
            ScannerTestRunBotJobPreparation botJobPreparation,
            ScannerTestRunExcelPreparation excelPreparation,
            ScannerTestRunExecutionStart executionStart,
            Supplier<BotJobLoadDTO> currentBotJob,
            Supplier<String> excelPath,
            PerformLists performLists) {
        this.startupPreparation = startupPreparation;
        this.definitionLoad = definitionLoad;
        this.definitionValidation = definitionValidation;
        this.botJobPreparation = botJobPreparation;
        this.excelPreparation = excelPreparation;
        this.executionStart = executionStart;
        this.currentBotJob = currentBotJob;
        this.excelPath = excelPath;
        this.performLists = performLists;
    }

    Result prepare(
            BotJobLoadDTO botJob,
            int blockOrderNumber,
            String endpointUrl,
            boolean runSingleBlock,
            BooleanSupplier cancellationRequested) {
        if (cancellationRequested.getAsBoolean()) return Result.canceled();

        ScannerTestRunStartupPreparation.Result startup =
                startupPreparation.prepare(botJob, blockOrderNumber, runSingleBlock);
        if (startup.status() == ScannerTestRunStartupPreparation.Status.MISSING_BOT_JOB) {
            return Result.status(Status.STARTUP_MISSING_BOT_JOB);
        }
        if (startup.status() == ScannerTestRunStartupPreparation.Status.ALREADY_RUNNING) {
            return Result.status(Status.ALREADY_RUNNING);
        }

        if (cancellationRequested.getAsBoolean()) return Result.canceled();

        ScannerPreLaunchPreparation.Result definitions = definitionLoad.loadAndApply(currentBotJob.get());

        if (cancellationRequested.getAsBoolean()) return Result.canceled();

        ScannerTestRunDefinitionValidation.Result validation = definitionValidation.validate(definitions);
        if (validation.status() == ScannerTestRunDefinitionValidation.Status.LOAD_ERROR) {
            return Result.loadError(validation.errorMessage());
        }
        if (validation.status() == ScannerTestRunDefinitionValidation.Status.MISSING_BOT_JOB) {
            return Result.withCurrentBotJob(Status.DEFINITION_MISSING_BOT_JOB, currentBotJob.get());
        }
        if (validation.status() == ScannerTestRunDefinitionValidation.Status.EMPTY_BLOCKS) {
            return Result.withCurrentBotJob(Status.EMPTY_BLOCKS, currentBotJob.get());
        }

        ScannerTestRunBotJobPreparation.Result botJobResult =
                botJobPreparation.prepare(currentBotJob.get(), excelPath.get(), endpointUrl);
        if (botJobResult.status() == ScannerTestRunBotJobPreparation.Status.MISSING_BOT_JOB) {
            return Result.withCurrentBotJob(Status.BOT_JOB_MISSING, currentBotJob.get());
        }
        if (botJobResult.status() == ScannerTestRunBotJobPreparation.Status.MISSING_HOME_BANKING) {
            return Result.withCurrentBotJob(Status.HOME_BANKING_MISSING, currentBotJob.get());
        }

        ScannerTestRunExcelPreparation.Result excelResult =
                excelPreparation.prepare(excelPath.get(), performLists);

        if (cancellationRequested.getAsBoolean()) {
            return Result.completed(Status.CANCELED, 0L, botJobResult.endpointApplied(), excelResult.loadError());
        }

        ScannerTestRunExecutionStart.Result execution = executionStart.start();
        if (execution.status() == ScannerTestRunExecutionStart.Status.BROWSER_OPEN_FAILED) {
            return Result.completed(
                    Status.BROWSER_OPEN_FAILED, 0L, botJobResult.endpointApplied(), excelResult.loadError());
        }

        if (cancellationRequested.getAsBoolean()) {
            return Result.completed(Status.CANCELED, 0L, botJobResult.endpointApplied(), excelResult.loadError());
        }

        return Result.completed(
                Status.STARTED, execution.executionId(), botJobResult.endpointApplied(), excelResult.loadError());
    }

    record Result(
            Status status,
            long executionId,
            boolean endpointApplied,
            ErrorMessage errorMessage,
            Exception excelLoadError,
            Integer botJobId,
            Integer homeBankingId) {
        private static Result status(Status status) {
            return new Result(status, 0L, false, null, null, null, null);
        }

        private static Result canceled() {
            return status(Status.CANCELED);
        }

        private static Result loadError(ErrorMessage errorMessage) {
            return new Result(Status.DEFINITION_LOAD_ERROR, 0L, false, errorMessage, null, null, null);
        }

        private static Result withCurrentBotJob(Status status, BotJobLoadDTO botJob) {
            Integer botJobId = botJob == null ? null : botJob.getId();
            Integer homeBankingId = botJob == null ? null : botJob.getHomeBankingId();
            return new Result(status, 0L, false, null, null, botJobId, homeBankingId);
        }

        private static Result completed(
                Status status, long executionId, boolean endpointApplied, Exception excelLoadError) {
            return new Result(status, executionId, endpointApplied, null, excelLoadError, null, null);
        }

        boolean usedSyntheticExcelFallback() {
            return excelLoadError != null;
        }
    }

    enum Status {
        STARTED,
        CANCELED,
        STARTUP_MISSING_BOT_JOB,
        ALREADY_RUNNING,
        DEFINITION_LOAD_ERROR,
        DEFINITION_MISSING_BOT_JOB,
        EMPTY_BLOCKS,
        BOT_JOB_MISSING,
        HOME_BANKING_MISSING,
        BROWSER_OPEN_FAILED
    }
}

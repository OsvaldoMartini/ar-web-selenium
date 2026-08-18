package com.allinweb.ch.facade.scanner.testrun;

public final class ScannerTestRunResultHandler {
    private final Operations operations;

    public ScannerTestRunResultHandler(Operations operations) {
        this.operations = operations;
    }

    public long finish(ScannerTestRunPreparationFlow.Result result, String endpointUrl) {
        return switch (result.status()) {
            case STARTED -> finishStarted(result, endpointUrl);
            case CANCELED -> finishCanceled(result, endpointUrl);
            case STARTUP_MISSING_BOT_JOB -> failWithoutReset("TEST RUN — no bot job supplied");
            case ALREADY_RUNNING -> ignore("TEST RUN — a job is already running; ignoring request.");
            case DEFINITION_LOAD_ERROR ->
                    fail("TEST RUN — load error: {}", result.errorMessage().getErrorMessage());
            case DEFINITION_MISSING_BOT_JOB ->
                    fail("TEST RUN — cannot find bot job with id: {}", result.botJobId());
            case EMPTY_BLOCKS ->
                    fail("TEST RUN — bot job has no loaded executable blocks: {}", result.botJobId());
            case BOT_JOB_MISSING -> fail("TEST RUN - cannot find bot job with id: {}", result.botJobId());
            case HOME_BANKING_MISSING ->
                    fail("TEST RUN — cannot find home banking env id: {}", result.homeBankingId());
            case BROWSER_OPEN_FAILED -> failBrowserOpen(result, endpointUrl);
        };
    }

    private long finishStarted(ScannerTestRunPreparationFlow.Result result, String endpointUrl) {
        reportNonTerminalSignals(result, endpointUrl);
        return result.executionId();
    }

    private long finishCanceled(ScannerTestRunPreparationFlow.Result result, String endpointUrl) {
        reportNonTerminalSignals(result, endpointUrl);
        return 0L;
    }

    private long failBrowserOpen(ScannerTestRunPreparationFlow.Result result, String endpointUrl) {
        reportNonTerminalSignals(result, endpointUrl);
        return failWithoutReset("TEST RUN — failed to open the Playwright browser");
    }

    private void reportNonTerminalSignals(ScannerTestRunPreparationFlow.Result result, String endpointUrl) {
        if (result.endpointApplied()) {
            operations.info("TEST RUN — using endpoint URL from the page: {}", endpointUrl);
        }
        if (result.usedSyntheticExcelFallback()) {
            operations.warn(
                    "TEST RUN — no/invalid Excel file, using synthetic $EMPTY row: {}",
                    result.excelLoadError().getMessage());
        }
    }

    private long fail(String message, Object... args) {
        operations.error(message, args);
        operations.resetSingleBlock();
        return 0L;
    }

    private long failWithoutReset(String message, Object... args) {
        operations.error(message, args);
        return 0L;
    }

    private long ignore(String message, Object... args) {
        operations.info(message, args);
        return 0L;
    }

    public interface Operations {
        void error(String message, Object... args);

        void warn(String message, Object... args);

        void info(String message, Object... args);

        void resetSingleBlock();
    }
}

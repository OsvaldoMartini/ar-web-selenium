package com.allinweb.ch.facade.scanner.prelaunch;

import com.allinweb.ch.util.ErrorMessage;

public final class ScannerPreLaunchStarter {
    private final Operations operations;

    public ScannerPreLaunchStarter(Operations operations) {
        this.operations = operations;
    }

    public void start() {
        if (!operations.lastBrowserTab()) {
            return;
        }

        operations.beginRun();
        ErrorMessage errorMessage = operations.loadDefinitions();
        operations.reportLoadError(errorMessage);
        if (errorMessage != null) {
            return;
        }
        if (!operations.loadCurrentBotJob()) {
            return;
        }
        operations.observeExecutionPreflight();
        operations.prepareExcel();
        if (!operations.validateExcel()) {
            return;
        }
        if (!operations.confirmMultipleExcelRows()) {
            return;
        }
        operations.resetInstructionsAndRecall();
    }

    public interface Operations {
        boolean lastBrowserTab();

        void beginRun();

        ErrorMessage loadDefinitions();

        void reportLoadError(ErrorMessage errorMessage);

        boolean loadCurrentBotJob();

        void observeExecutionPreflight();

        void prepareExcel();

        boolean validateExcel();

        boolean confirmMultipleExcelRows();

        void resetInstructionsAndRecall();
    }
}

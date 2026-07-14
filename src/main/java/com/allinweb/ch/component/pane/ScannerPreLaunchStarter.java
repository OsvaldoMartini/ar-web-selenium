package com.allinweb.ch.component.pane;

import com.allinweb.ch.util.ErrorMessage;

final class ScannerPreLaunchStarter {
    private final Operations operations;

    ScannerPreLaunchStarter(Operations operations) {
        this.operations = operations;
    }

    void start() {
        if (!operations.lastBrowserTab()) {
            return;
        }

        operations.beginRun();
        ErrorMessage errorMessage = operations.loadDefinitions();
        operations.reportLoadError(errorMessage);
        if (!operations.loadCurrentBotJob()) {
            return;
        }
        operations.prepareExcel();
        if (!operations.validateExcel()) {
            return;
        }
        if (!operations.confirmMultipleExcelRows()) {
            return;
        }
        operations.resetInstructionsAndRecall();
    }

    interface Operations {
        boolean lastBrowserTab();

        void beginRun();

        ErrorMessage loadDefinitions();

        void reportLoadError(ErrorMessage errorMessage);

        boolean loadCurrentBotJob();

        void prepareExcel();

        boolean validateExcel();

        boolean confirmMultipleExcelRows();

        void resetInstructionsAndRecall();
    }
}

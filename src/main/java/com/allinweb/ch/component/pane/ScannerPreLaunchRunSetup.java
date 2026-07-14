package com.allinweb.ch.component.pane;

final class ScannerPreLaunchRunSetup {
    private final Operations operations;

    ScannerPreLaunchRunSetup(Operations operations) {
        this.operations = operations;
    }

    void beginRun() {
        operations.disableLaunch();
        operations.setInterceptBotJob(false);
        operations.markNotRunning();

        try {
            operations.setExcelPath(operations.resolveExcelBasePath());
        } catch (Exception error) {
            operations.reportExcelPathError(error);
        }

        int selectedBlockOrderNumber = operations.selectedBlockOrderNumber();
        operations.setExecuteSpecificBlock(selectedBlockOrderNumber < 0 ? 0 : selectedBlockOrderNumber - 1);
        operations.setRunSingleBlock(false);
        operations.clearFields();
    }

    interface Operations {
        void disableLaunch();

        void setInterceptBotJob(boolean intercept);

        void markNotRunning();

        String resolveExcelBasePath();

        void setExcelPath(String excelPath);

        void reportExcelPathError(Exception error);

        int selectedBlockOrderNumber();

        void setExecuteSpecificBlock(int blockIndex);

        void setRunSingleBlock(boolean runSingleBlock);

        void clearFields();
    }
}

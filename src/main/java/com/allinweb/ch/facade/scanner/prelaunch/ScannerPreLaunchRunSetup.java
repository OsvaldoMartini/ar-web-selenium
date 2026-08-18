package com.allinweb.ch.facade.scanner.prelaunch;

public final class ScannerPreLaunchRunSetup {
    private final Operations operations;

    public ScannerPreLaunchRunSetup(Operations operations) {
        this.operations = operations;
    }

    public void beginRun() {
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

    public interface Operations {
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

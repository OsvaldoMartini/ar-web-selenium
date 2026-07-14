package com.allinweb.ch.component.pane;

import com.allinweb.ch.model.BotJobLoadDTO;

final class ScannerPreLaunchBotJobSelection {
    private final Operations operations;

    ScannerPreLaunchBotJobSelection(Operations operations) {
        this.operations = operations;
    }

    boolean loadCurrentBotJob() {
        BotJobLoadDTO currentBotJob = operations.currentBotJob();
        ScannerPreLaunchPreparation.BotJobSelection selection =
                operations.loadCurrentBotJob(currentBotJob, operations.excelPath());
        if (selection.botJobMissing()) {
            operations.error("Cannot find Bot Jobs with this Id:" + currentBotJob.getId());
            operations.reenableLaunchButton();
            return false;
        }
        if (selection.homeBankingMissing()) {
            operations.error("Cannot find Home Banking Environment Id:" + currentBotJob.getHomeBankingId());
            operations.reenableLaunchButton();
            return false;
        }

        operations.applySelection(selection);
        return true;
    }

    interface Operations {
        BotJobLoadDTO currentBotJob();

        String excelPath();

        ScannerPreLaunchPreparation.BotJobSelection loadCurrentBotJob(
                BotJobLoadDTO currentBotJob, String excelPath);

        void applySelection(ScannerPreLaunchPreparation.BotJobSelection selection);

        void reenableLaunchButton();

        void error(String message);
    }
}

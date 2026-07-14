package com.allinweb.ch.component.pane;

import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.HomeBankingLoadDTO;
import com.allinweb.ch.model.HomeUrlDTO;
import com.google.common.base.Strings;

final class ScannerTestRunBotJobPreparation {
    private final Operations operations;

    ScannerTestRunBotJobPreparation(Operations operations) {
        this.operations = operations;
    }

    Result prepare(BotJobLoadDTO currentBotJob, String excelBasePath, String endpointUrl) {
        ScannerPreLaunchPreparation.BotJobSelection selection =
                operations.loadCurrentBotJob(currentBotJob, excelBasePath);
        if (selection.botJobMissing()) {
            return Result.missingBotJob();
        }
        if (selection.homeBankingMissing()) {
            return Result.missingHomeBanking();
        }

        operations.applySelection(selection);
        BotJobLoadDTO selectedBotJob = selection.botJob();
        HomeBankingLoadDTO homeBanking = selectedBotJob.getHomeBankingLoadDTO();
        HomeUrlDTO homeUrl =
                operations.homeUrlByBankId(selectedBotJob.getHomeBankingId(), selectedBotJob.getHomeUrlId());
        boolean endpointApplied = false;
        if (!Strings.isNullOrEmpty(endpointUrl)) {
            if (homeUrl != null) {
                homeUrl.setUrl(endpointUrl);
            }
            homeBanking.setUrl(endpointUrl);
            endpointApplied = true;
        }
        return Result.ready(endpointApplied);
    }

    record Result(Status status, boolean endpointApplied) {
        private static Result ready(boolean endpointApplied) {
            return new Result(Status.READY, endpointApplied);
        }

        private static Result missingBotJob() {
            return new Result(Status.MISSING_BOT_JOB, false);
        }

        private static Result missingHomeBanking() {
            return new Result(Status.MISSING_HOME_BANKING, false);
        }
    }

    enum Status {
        READY,
        MISSING_BOT_JOB,
        MISSING_HOME_BANKING
    }

    interface Operations {
        ScannerPreLaunchPreparation.BotJobSelection loadCurrentBotJob(BotJobLoadDTO currentBotJob, String excelBasePath);

        void applySelection(ScannerPreLaunchPreparation.BotJobSelection selection);

        HomeUrlDTO homeUrlByBankId(int homeBankingId, int homeUrlId);
    }
}

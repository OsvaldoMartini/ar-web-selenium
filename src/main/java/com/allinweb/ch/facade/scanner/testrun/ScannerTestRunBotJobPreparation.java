package com.allinweb.ch.facade.scanner.testrun;

import com.allinweb.ch.facade.scanner.prelaunch.ScannerPreLaunchPreparation;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.HomeBankingLoadDTO;
import com.allinweb.ch.model.HomeUrlDTO;
import com.google.common.base.Strings;

public final class ScannerTestRunBotJobPreparation {
    private final Operations operations;

    public ScannerTestRunBotJobPreparation(Operations operations) {
        this.operations = operations;
    }

    public Result prepare(BotJobLoadDTO currentBotJob, String excelBasePath, String endpointUrl) {
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

    public record Result(Status status, boolean endpointApplied) {
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

    public enum Status {
        READY,
        MISSING_BOT_JOB,
        MISSING_HOME_BANKING
    }

    public interface Operations {
        ScannerPreLaunchPreparation.BotJobSelection loadCurrentBotJob(BotJobLoadDTO currentBotJob, String excelBasePath);

        void applySelection(ScannerPreLaunchPreparation.BotJobSelection selection);

        HomeUrlDTO homeUrlByBankId(int homeBankingId, int homeUrlId);
    }
}

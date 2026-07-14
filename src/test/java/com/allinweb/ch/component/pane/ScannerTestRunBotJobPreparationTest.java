package com.allinweb.ch.component.pane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.HomeBankingLoadDTO;
import com.allinweb.ch.model.HomeUrlDTO;
import org.junit.jupiter.api.Test;

class ScannerTestRunBotJobPreparationTest {

    @Test
    void prepareReportsMissingBotJob() {
        FakeOperations operations = new FakeOperations();
        operations.selection = ScannerPreLaunchPreparation.BotJobSelection.missingBotJob();
        ScannerTestRunBotJobPreparation preparation = new ScannerTestRunBotJobPreparation(operations);

        ScannerTestRunBotJobPreparation.Result result = preparation.prepare(new BotJobLoadDTO(), "D:\\Excel", null);

        assertEquals(ScannerTestRunBotJobPreparation.Status.MISSING_BOT_JOB, result.status());
        assertFalse(operations.selectionApplied);
    }

    @Test
    void prepareReportsMissingHomeBanking() {
        FakeOperations operations = new FakeOperations();
        operations.selection = ScannerPreLaunchPreparation.BotJobSelection.missingHomeBanking();
        ScannerTestRunBotJobPreparation preparation = new ScannerTestRunBotJobPreparation(operations);

        ScannerTestRunBotJobPreparation.Result result = preparation.prepare(new BotJobLoadDTO(), "D:\\Excel", null);

        assertEquals(ScannerTestRunBotJobPreparation.Status.MISSING_HOME_BANKING, result.status());
        assertFalse(operations.selectionApplied);
    }

    @Test
    void prepareAppliesLoadedSelectionWithoutEndpointOverride() {
        FakeOperations operations = new FakeOperations();
        BotJobLoadDTO botJob = botJob();
        HomeBankingLoadDTO homeBanking = homeBanking("https://base.example");
        botJob.setHomeBankingLoadDTO(homeBanking);
        operations.selection = ScannerPreLaunchPreparation.BotJobSelection.loaded(botJob, "Job", "D:\\Excel\\Job.xlsx");
        operations.homeUrl = homeUrl("https://home-url.example");
        ScannerTestRunBotJobPreparation preparation = new ScannerTestRunBotJobPreparation(operations);

        ScannerTestRunBotJobPreparation.Result result = preparation.prepare(botJob, "D:\\Excel", "");

        assertEquals(ScannerTestRunBotJobPreparation.Status.READY, result.status());
        assertFalse(result.endpointApplied());
        assertSame(operations.selection, operations.appliedSelection);
        assertEquals("https://base.example", homeBanking.getUrl());
        assertEquals("https://home-url.example", operations.homeUrl.getUrl());
    }

    @Test
    void prepareAppliesEndpointOverrideToHomeBankingAndHomeUrl() {
        FakeOperations operations = new FakeOperations();
        BotJobLoadDTO botJob = botJob();
        HomeBankingLoadDTO homeBanking = homeBanking("https://base.example");
        botJob.setHomeBankingLoadDTO(homeBanking);
        operations.selection = ScannerPreLaunchPreparation.BotJobSelection.loaded(botJob, "Job", "D:\\Excel\\Job.xlsx");
        operations.homeUrl = homeUrl("https://home-url.example");
        ScannerTestRunBotJobPreparation preparation = new ScannerTestRunBotJobPreparation(operations);

        ScannerTestRunBotJobPreparation.Result result =
                preparation.prepare(botJob, "D:\\Excel", "https://selected.example");

        assertEquals(ScannerTestRunBotJobPreparation.Status.READY, result.status());
        assertTrue(result.endpointApplied());
        assertEquals("https://selected.example", homeBanking.getUrl());
        assertEquals("https://selected.example", operations.homeUrl.getUrl());
    }

    private BotJobLoadDTO botJob() {
        BotJobLoadDTO botJob = new BotJobLoadDTO();
        botJob.setHomeBankingId(2);
        botJob.setHomeUrlId(7);
        return botJob;
    }

    private HomeBankingLoadDTO homeBanking(String url) {
        HomeBankingLoadDTO homeBanking = new HomeBankingLoadDTO();
        homeBanking.setUrl(url);
        return homeBanking;
    }

    private HomeUrlDTO homeUrl(String url) {
        HomeUrlDTO homeUrl = new HomeUrlDTO();
        homeUrl.setUrl(url);
        return homeUrl;
    }

    private static final class FakeOperations implements ScannerTestRunBotJobPreparation.Operations {
        private ScannerPreLaunchPreparation.BotJobSelection selection;
        private ScannerPreLaunchPreparation.BotJobSelection appliedSelection;
        private HomeUrlDTO homeUrl;
        private boolean selectionApplied;

        @Override
        public ScannerPreLaunchPreparation.BotJobSelection loadCurrentBotJob(
                BotJobLoadDTO currentBotJob, String excelBasePath) {
            return selection;
        }

        @Override
        public void applySelection(ScannerPreLaunchPreparation.BotJobSelection selection) {
            selectionApplied = true;
            appliedSelection = selection;
        }

        @Override
        public HomeUrlDTO homeUrlByBankId(int homeBankingId, int homeUrlId) {
            return homeUrl;
        }
    }
}

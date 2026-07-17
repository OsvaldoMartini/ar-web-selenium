package com.allinweb.ch.component.pane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.facade.scanner.prelaunch.ScannerPreLaunchPreparation;
import com.allinweb.ch.facade.scanner.testrun.ScannerTestRunBotJobPreparation;
import com.allinweb.ch.facade.scanner.testrun.ScannerTestRunDefinitionLoad;
import com.allinweb.ch.facade.scanner.testrun.ScannerTestRunDefinitionValidation;
import com.allinweb.ch.facade.scanner.testrun.ScannerTestRunExecutionStart;
import com.allinweb.ch.facade.scanner.testrun.ScannerTestRunExcelPreparation;
import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.HomeBankingLoadDTO;
import com.allinweb.ch.model.HomeUrlDTO;
import com.allinweb.ch.util.ExtractedData;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerTestRunPreparationFlowTest {

    @Test
    void prepareRunsCompleteFlowAndReturnsExecutionId() {
        Fixture fixture = new Fixture();
        fixture.executionId = 12L;
        fixture.endpointShouldApply = true;
        fixture.excelLoadError = new IllegalStateException("missing workbook");

        ScannerTestRunPreparationFlow.Result result = fixture.flow().prepare(
                fixture.requestBotJob, 3, "https://selected.example", true, () -> false);

        assertEquals(ScannerTestRunPreparationFlow.Status.STARTED, result.status());
        assertEquals(12L, result.executionId());
        assertTrue(result.endpointApplied());
        assertEquals("missing workbook", result.excelLoadError().getMessage());
        assertSame(fixture.requestBotJob, fixture.startup.currentBotJob);
        assertSame(fixture.currentBotJob, fixture.definitionLoad.currentBotJob);
        assertSame(fixture.currentBotJob, fixture.botJob.currentBotJob);
        assertTrue(fixture.excel.excelPrepared);
        assertTrue(fixture.execution.executionStarted);
    }

    @Test
    void prepareStopsWhenDefinitionValidationFails() {
        Fixture fixture = new Fixture();
        fixture.definitionResult = new ScannerPreLaunchPreparation.Result(null, List.of(), List.of(), false);

        ScannerTestRunPreparationFlow.Result result = fixture.flow().prepare(
                fixture.requestBotJob, 1, "", false, () -> false);

        assertEquals(ScannerTestRunPreparationFlow.Status.EMPTY_BLOCKS, result.status());
        assertEquals(fixture.currentBotJob.getId(), result.botJobId());
        assertFalse(fixture.botJob.botJobPrepared);
        assertFalse(fixture.excel.excelPrepared);
    }

    @Test
    void prepareCarriesSignalsWhenCanceledAfterExcelPreparation() {
        Fixture fixture = new Fixture();
        fixture.endpointShouldApply = true;
        fixture.excelLoadError = new IllegalStateException("missing workbook");
        final int[] calls = {0};

        ScannerTestRunPreparationFlow.Result result = fixture.flow().prepare(
                fixture.requestBotJob,
                1,
                "https://selected.example",
                false,
                () -> ++calls[0] == 4);

        assertEquals(ScannerTestRunPreparationFlow.Status.CANCELED, result.status());
        assertTrue(result.endpointApplied());
        assertEquals("missing workbook", result.excelLoadError().getMessage());
        assertFalse(fixture.execution.executionStarted);
    }

    private static final class Fixture {
        private final BotJobLoadDTO requestBotJob = botJob(11, 21);
        private final BotJobLoadDTO currentBotJob = botJob(42, 84);
        private final StartupOps startup = new StartupOps(this);
        private final DefinitionLoadOps definitionLoad = new DefinitionLoadOps(this);
        private final BotJobOps botJob = new BotJobOps(this);
        private final ExcelOps excel = new ExcelOps(this);
        private final ExecutionOps execution = new ExecutionOps(this);
        private ScannerPreLaunchPreparation.Result definitionResult =
                new ScannerPreLaunchPreparation.Result(null, List.of(), List.of(new BlockLoadDTO()), false);
        private boolean endpointShouldApply;
        private Exception excelLoadError;
        private long executionId = 1L;

        private ScannerTestRunPreparationFlow flow() {
            return new ScannerTestRunPreparationFlow(
                    new ScannerTestRunStartupPreparation(startup),
                    new ScannerTestRunDefinitionLoad(definitionLoad),
                    new ScannerTestRunDefinitionValidation(),
                    new ScannerTestRunBotJobPreparation(botJob),
                    new ScannerTestRunExcelPreparation(excel, excel),
                    new ScannerTestRunExecutionStart(execution),
                    () -> currentBotJob,
                    () -> "D:\\Excel\\Job.xlsx",
                    null);
        }

        private static BotJobLoadDTO botJob(int botJobId, int homeBankingId) {
            BotJobLoadDTO botJob = new BotJobLoadDTO();
            botJob.setId(botJobId);
            botJob.setName("Job");
            botJob.setHomeBankingId(homeBankingId);
            botJob.setHomeUrlId(7);
            HomeBankingLoadDTO homeBanking = new HomeBankingLoadDTO();
            homeBanking.setUrl("https://base.example");
            botJob.setHomeBankingLoadDTO(homeBanking);
            return botJob;
        }
    }

    private static final class StartupOps implements ScannerTestRunStartupPreparation.Operations {
        private final Fixture fixture;
        private BotJobLoadDTO currentBotJob;

        private StartupOps(Fixture fixture) {
            this.fixture = fixture;
        }

        @Override
        public long activeExecutionId() {
            return 0L;
        }

        @Override
        public boolean isExecutionComplete(long executionId) {
            return true;
        }

        @Override
        public boolean isJobRunning() {
            return false;
        }

        @Override
        public void ensureDriver() {}

        @Override
        public void setCurrentBotJob(BotJobLoadDTO botJob) {
            currentBotJob = botJob;
        }

        @Override
        public void setInterceptBotJob(boolean intercept) {}

        @Override
        public void markNotRunning() {}

        @Override
        public String resolveExcelBasePath() {
            return "D:\\Excel";
        }

        @Override
        public void setExcelPath(String excelPath) {}

        @Override
        public void reportExcelPathError(Exception error) {}

        @Override
        public void setExecuteSpecificBlock(int executeSpecificBlock) {}

        @Override
        public void setRunSingleBlock(boolean runSingleBlock) {}

        @Override
        public void clearFields() {}
    }

    private static final class DefinitionLoadOps implements ScannerTestRunDefinitionLoad.Operations {
        private final Fixture fixture;
        private BotJobLoadDTO currentBotJob;

        private DefinitionLoadOps(Fixture fixture) {
            this.fixture = fixture;
        }

        @Override
        public ScannerPreLaunchPreparation.Result loadDefinitions(BotJobLoadDTO currentBotJob) {
            this.currentBotJob = currentBotJob;
            return fixture.definitionResult;
        }

        @Override
        public void setExcelDataGoto(List<InstructionLoad> excelDataGoto) {}

        @Override
        public void setBlocksLoaded(List<BlockLoadDTO> blocksLoaded) {}
    }

    private static final class BotJobOps implements ScannerTestRunBotJobPreparation.Operations {
        private final Fixture fixture;
        private BotJobLoadDTO currentBotJob;
        private boolean botJobPrepared;

        private BotJobOps(Fixture fixture) {
            this.fixture = fixture;
        }

        @Override
        public ScannerPreLaunchPreparation.BotJobSelection loadCurrentBotJob(
                BotJobLoadDTO currentBotJob, String excelBasePath) {
            botJobPrepared = true;
            this.currentBotJob = currentBotJob;
            return ScannerPreLaunchPreparation.BotJobSelection.loaded(
                    currentBotJob, currentBotJob.getName(), excelBasePath + "\\Job.xlsx");
        }

        @Override
        public void applySelection(ScannerPreLaunchPreparation.BotJobSelection selection) {}

        @Override
        public HomeUrlDTO homeUrlByBankId(int homeBankingId, int homeUrlId) {
            if (!fixture.endpointShouldApply) {
                return null;
            }
            HomeUrlDTO homeUrl = new HomeUrlDTO();
            homeUrl.setUrl("https://home-url.example");
            return homeUrl;
        }
    }

    private static final class ExcelOps
            implements ScannerTestRunExcelPreparation.ExcelLoader, ScannerTestRunExcelPreparation.Operations {
        private final Fixture fixture;
        private boolean excelPrepared;

        private ExcelOps(Fixture fixture) {
            this.fixture = fixture;
        }

        @Override
        public ExtractedData load(String excelPath, PerformLists performLists) throws Exception {
            excelPrepared = true;
            if (fixture.excelLoadError != null) {
                throw fixture.excelLoadError;
            }
            ExtractedData extractedData = new ExtractedData();
            extractedData.addFieldValue("username", "martini", 0);
            return extractedData;
        }

        @Override
        public ExtractedData ensureEmptyDataRow(ExtractedData extractedData) {
            ExtractedData data = extractedData == null ? new ExtractedData() : extractedData;
            if (data.getNumberOfDataRows() == null || data.getNumberOfDataRows() == 0) {
                data.addField("$EMPTY");
                data.addFieldValue("$EMPTY", "$EMPTY", 0);
            }
            return data;
        }

        @Override
        public void setExtractedData(ExtractedData extractedData) {}
    }

    private static final class ExecutionOps implements ScannerTestRunExecutionStart.Operations {
        private final Fixture fixture;
        private boolean executionStarted;

        private ExecutionOps(Fixture fixture) {
            this.fixture = fixture;
        }

        @Override
        public boolean openBrowser() {
            executionStarted = true;
            return true;
        }

        @Override
        public void resetInstructionExecutionFlags() {}

        @Override
        public long recallJobExecutionId() {
            return fixture.executionId;
        }

        @Override
        public boolean isJobRunning() {
            return false;
        }

        @Override
        public void setRunSingleBlock(boolean runSingleBlock) {}
    }
}

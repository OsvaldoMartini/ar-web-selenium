package com.allinweb.ch.facade.scanner.testrun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.BotJobLoadDTO;
import org.junit.jupiter.api.Test;

class ScannerTestRunStartupPreparationTest {

    @Test
    void prepareRejectsMissingBotJob() {
        FakeOperations operations = new FakeOperations();
        ScannerTestRunStartupPreparation preparation = new ScannerTestRunStartupPreparation(operations);

        ScannerTestRunStartupPreparation.Result result = preparation.prepare(null, 1, true);

        assertEquals(ScannerTestRunStartupPreparation.Status.MISSING_BOT_JOB, result.status());
        assertFalse(operations.driverEnsured);
    }

    @Test
    void prepareRejectsWhenExecutionIsStillActive() {
        FakeOperations operations = new FakeOperations();
        operations.activeExecutionId = 8L;
        operations.executionComplete = false;
        ScannerTestRunStartupPreparation preparation = new ScannerTestRunStartupPreparation(operations);

        ScannerTestRunStartupPreparation.Result result = preparation.prepare(new BotJobLoadDTO(), 1, true);

        assertEquals(ScannerTestRunStartupPreparation.Status.ALREADY_RUNNING, result.status());
        assertFalse(operations.driverEnsured);
    }

    @Test
    void prepareInitializesRunState() {
        FakeOperations operations = new FakeOperations();
        BotJobLoadDTO botJob = new BotJobLoadDTO();
        ScannerTestRunStartupPreparation preparation = new ScannerTestRunStartupPreparation(operations);

        ScannerTestRunStartupPreparation.Result result = preparation.prepare(botJob, 3, true);

        assertEquals(ScannerTestRunStartupPreparation.Status.READY, result.status());
        assertTrue(operations.driverEnsured);
        assertSame(botJob, operations.currentBotJob);
        assertFalse(operations.interceptBotJob);
        assertTrue(operations.markNotRunningCalled);
        assertEquals("D:\\Excel", operations.excelPath);
        assertEquals(2, operations.executeSpecificBlock);
        assertTrue(operations.runSingleBlock);
        assertTrue(operations.clearFieldsCalled);
    }

    @Test
    void prepareNormalizesNegativeBlockToZero() {
        FakeOperations operations = new FakeOperations();
        ScannerTestRunStartupPreparation preparation = new ScannerTestRunStartupPreparation(operations);

        preparation.prepare(new BotJobLoadDTO(), -1, false);

        assertEquals(0, operations.executeSpecificBlock);
        assertFalse(operations.runSingleBlock);
    }

    private static final class FakeOperations implements ScannerTestRunStartupPreparation.Operations {
        private long activeExecutionId;
        private boolean executionComplete = true;
        private boolean jobRunning;
        private boolean driverEnsured;
        private BotJobLoadDTO currentBotJob;
        private boolean interceptBotJob = true;
        private boolean markNotRunningCalled;
        private String excelPath;
        private int executeSpecificBlock = -1;
        private boolean runSingleBlock;
        private boolean clearFieldsCalled;

        @Override
        public long activeExecutionId() {
            return activeExecutionId;
        }

        @Override
        public boolean isExecutionComplete(long executionId) {
            return executionComplete;
        }

        @Override
        public boolean isJobRunning() {
            return jobRunning;
        }

        @Override
        public void ensureDriver() {
            driverEnsured = true;
        }

        @Override
        public void setCurrentBotJob(BotJobLoadDTO botJob) {
            currentBotJob = botJob;
        }

        @Override
        public void setInterceptBotJob(boolean intercept) {
            interceptBotJob = intercept;
        }

        @Override
        public void markNotRunning() {
            markNotRunningCalled = true;
        }

        @Override
        public String resolveExcelBasePath() {
            return "D:\\Excel";
        }

        @Override
        public void setExcelPath(String excelPath) {
            this.excelPath = excelPath;
        }

        @Override
        public void reportExcelPathError(Exception error) {}

        @Override
        public void setExecuteSpecificBlock(int executeSpecificBlock) {
            this.executeSpecificBlock = executeSpecificBlock;
        }

        @Override
        public void setRunSingleBlock(boolean runSingleBlock) {
            this.runSingleBlock = runSingleBlock;
        }

        @Override
        public void clearFields() {
            clearFieldsCalled = true;
        }
    }
}

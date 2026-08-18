package com.allinweb.ch.facade.scanner.testrun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScannerTestRunExecutionStartTest {

    @Test
    void startReportsBrowserOpenFailureAndClearsSingleBlockMode() {
        FakeOperations operations = new FakeOperations();
        operations.browserOpened = false;
        ScannerTestRunExecutionStart start = new ScannerTestRunExecutionStart(operations);

        ScannerTestRunExecutionStart.Result result = start.start();

        assertEquals(ScannerTestRunExecutionStart.Status.BROWSER_OPEN_FAILED, result.status());
        assertEquals(0L, result.executionId());
        assertFalse(operations.runSingleBlock);
        assertFalse(operations.instructionsReset);
    }

    @Test
    void startResetsInstructionsAndReturnsExecutionId() {
        FakeOperations operations = new FakeOperations();
        operations.executionId = 9L;
        ScannerTestRunExecutionStart start = new ScannerTestRunExecutionStart(operations);

        ScannerTestRunExecutionStart.Result result = start.start();

        assertEquals(ScannerTestRunExecutionStart.Status.STARTED, result.status());
        assertEquals(9L, result.executionId());
        assertTrue(operations.instructionsReset);
        assertTrue(operations.runSingleBlock);
    }

    @Test
    void startClearsSingleBlockModeWhenExecutionDidNotStartAndJobIsNotRunning() {
        FakeOperations operations = new FakeOperations();
        operations.executionId = 0L;
        operations.jobRunning = false;
        ScannerTestRunExecutionStart start = new ScannerTestRunExecutionStart(operations);

        ScannerTestRunExecutionStart.Result result = start.start();

        assertEquals(ScannerTestRunExecutionStart.Status.STARTED, result.status());
        assertEquals(0L, result.executionId());
        assertFalse(operations.runSingleBlock);
    }

    @Test
    void startKeepsSingleBlockModeWhenExecutionIsAlreadyRunning() {
        FakeOperations operations = new FakeOperations();
        operations.executionId = 0L;
        operations.jobRunning = true;
        ScannerTestRunExecutionStart start = new ScannerTestRunExecutionStart(operations);

        start.start();

        assertTrue(operations.runSingleBlock);
    }

    private static final class FakeOperations implements ScannerTestRunExecutionStart.Operations {
        private boolean browserOpened = true;
        private boolean instructionsReset;
        private long executionId = 1L;
        private boolean jobRunning;
        private boolean runSingleBlock = true;

        @Override
        public boolean openBrowser() {
            return browserOpened;
        }

        @Override
        public void resetInstructionExecutionFlags() {
            instructionsReset = true;
        }

        @Override
        public long recallJobExecutionId() {
            return executionId;
        }

        @Override
        public boolean isJobRunning() {
            return jobRunning;
        }

        @Override
        public void setRunSingleBlock(boolean runSingleBlock) {
            this.runSingleBlock = runSingleBlock;
        }
    }
}

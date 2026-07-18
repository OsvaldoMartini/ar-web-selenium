package com.allinweb.ch.facade.scanner.testrun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScannerTestRunStopperTest {

    @Test
    void cancelStartupIgnoresInactiveStartup() {
        FakeOperations operations = new FakeOperations();
        ScannerTestRunStopper stopper = new ScannerTestRunStopper(operations);

        assertFalse(stopper.cancelStartup());

        assertFalse(operations.resetSingleBlock);
        assertFalse(operations.interceptRequested);
        assertEquals(0, operations.closeCalls);
    }

    @Test
    void cancelStartupInterruptsBrowserWhenActive() {
        FakeOperations operations = new FakeOperations();
        operations.startupActive = true;
        ScannerTestRunStopper stopper = new ScannerTestRunStopper(operations);

        assertTrue(stopper.cancelStartup());

        assertTrue(operations.resetSingleBlock);
        assertTrue(operations.interceptRequested);
        assertEquals(1, operations.closeCalls);
    }

    @Test
    void stopRejectsStaleExecution() {
        FakeOperations operations = new FakeOperations();
        operations.activeExecutionId = 9L;
        ScannerTestRunStopper stopper = new ScannerTestRunStopper(operations);

        assertFalse(stopper.stop(8L));

        assertEquals(1, operations.infoCalls);
        assertEquals(0, operations.requestStopCalls);
        assertEquals(0, operations.closeCalls);
    }

    @Test
    void stopRejectsCompletedExecution() {
        FakeOperations operations = new FakeOperations();
        operations.activeExecutionId = 9L;
        operations.completedExecutionId = 9L;
        ScannerTestRunStopper stopper = new ScannerTestRunStopper(operations);

        assertFalse(stopper.stop(9L));

        assertEquals(1, operations.infoCalls);
        assertEquals(0, operations.requestStopCalls);
    }

    @Test
    void stopRejectsWhenOutcomeTrackerAlreadyCompleted() {
        FakeOperations operations = new FakeOperations();
        operations.activeExecutionId = 9L;
        operations.requestStopResult = false;
        ScannerTestRunStopper stopper = new ScannerTestRunStopper(operations);

        assertFalse(stopper.stop(9L));

        assertEquals(2, operations.infoCalls);
        assertEquals(1, operations.requestStopCalls);
        assertEquals(0, operations.closeCalls);
    }

    @Test
    void stopInterruptsBrowserForActiveRunningExecution() {
        FakeOperations operations = new FakeOperations();
        operations.activeExecutionId = 9L;
        operations.requestStopResult = true;
        ScannerTestRunStopper stopper = new ScannerTestRunStopper(operations);

        assertTrue(stopper.stop(9L));

        assertEquals(1, operations.requestStopCalls);
        assertTrue(operations.resetSingleBlock);
        assertTrue(operations.interceptRequested);
        assertEquals(1, operations.closeCalls);
    }

    @Test
    void reportsCurrentCompletionAndTerminalState() {
        FakeOperations operations = new FakeOperations();
        operations.lastSubmittedExecutionId = 5L;
        operations.completedExecutionId = 4L;
        operations.terminalOutcome = "PASSED";
        ScannerTestRunStopper stopper = new ScannerTestRunStopper(operations);

        assertEquals(5L, stopper.currentExecutionId());
        assertFalse(stopper.isExecutionComplete(5L));
        assertTrue(stopper.isExecutionComplete(4L));
        assertEquals("PASSED", stopper.terminalState(5L));

        operations.activeExecutionId = 6L;
        assertEquals(6L, stopper.currentExecutionId());
    }

    private static final class FakeOperations implements ScannerTestRunStopper.Operations {
        private boolean startupActive;
        private long activeExecutionId;
        private long lastSubmittedExecutionId;
        private long completedExecutionId;
        private boolean requestStopResult;
        private String terminalOutcome = "FAILED";
        private boolean resetSingleBlock;
        private boolean interceptRequested;
        private int requestStopCalls;
        private int closeCalls;
        private int infoCalls;

        @Override
        public boolean startupActive() {
            return startupActive;
        }

        @Override
        public long activeExecutionId() {
            return activeExecutionId;
        }

        @Override
        public long lastSubmittedExecutionId() {
            return lastSubmittedExecutionId;
        }

        @Override
        public long completedExecutionId() {
            return completedExecutionId;
        }

        @Override
        public boolean requestStop(long executionId) {
            requestStopCalls++;
            return requestStopResult;
        }

        @Override
        public String terminalOutcome(long executionId) {
            return terminalOutcome;
        }

        @Override
        public void resetSingleBlock() {
            resetSingleBlock = true;
        }

        @Override
        public void requestIntercept() {
            interceptRequested = true;
        }

        @Override
        public void closeBrowser() {
            closeCalls++;
        }

        @Override
        public void info(String message, Object... args) {
            infoCalls++;
        }

        @Override
        public void warn(String message, Object... args) {
        }
    }
}

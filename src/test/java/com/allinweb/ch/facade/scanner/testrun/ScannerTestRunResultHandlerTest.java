package com.allinweb.ch.facade.scanner.testrun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.util.ErrorMessage;
import org.junit.jupiter.api.Test;

class ScannerTestRunResultHandlerTest {

    @Test
    void finishReturnsExecutionAndReportsSignalsWhenStarted() {
        FakeOperations operations = new FakeOperations();
        ScannerTestRunResultHandler handler = new ScannerTestRunResultHandler(operations);
        ScannerTestRunPreparationFlow.Result result = new ScannerTestRunPreparationFlow.Result(
                ScannerTestRunPreparationFlow.Status.STARTED,
                17L,
                true,
                null,
                new IllegalStateException("missing workbook"),
                null,
                null);

        long executionId = handler.finish(result, "https://selected.example");

        assertEquals(17L, executionId);
        assertEquals(1, operations.infoCalls);
        assertEquals(1, operations.warnCalls);
        assertFalse(operations.resetSingleBlock);
    }

    @Test
    void finishResetsSingleBlockForDefinitionLoadError() {
        FakeOperations operations = new FakeOperations();
        ScannerTestRunResultHandler handler = new ScannerTestRunResultHandler(operations);
        ScannerTestRunPreparationFlow.Result result = new ScannerTestRunPreparationFlow.Result(
                ScannerTestRunPreparationFlow.Status.DEFINITION_LOAD_ERROR,
                0L,
                false,
                new ErrorMessage("Load", "failed", "Cannot load"),
                null,
                null,
                null);

        long executionId = handler.finish(result, "");

        assertEquals(0L, executionId);
        assertEquals(1, operations.errorCalls);
        assertTrue(operations.resetSingleBlock);
    }

    @Test
    void finishDoesNotResetForAlreadyRunning() {
        FakeOperations operations = new FakeOperations();
        ScannerTestRunResultHandler handler = new ScannerTestRunResultHandler(operations);

        long executionId = handler.finish(
                new ScannerTestRunPreparationFlow.Result(
                        ScannerTestRunPreparationFlow.Status.ALREADY_RUNNING, 0L, false, null, null, null, null),
                "");

        assertEquals(0L, executionId);
        assertEquals(1, operations.infoCalls);
        assertFalse(operations.resetSingleBlock);
    }

    @Test
    void finishReportsSignalsBeforeBrowserOpenFailure() {
        FakeOperations operations = new FakeOperations();
        ScannerTestRunResultHandler handler = new ScannerTestRunResultHandler(operations);
        ScannerTestRunPreparationFlow.Result result = new ScannerTestRunPreparationFlow.Result(
                ScannerTestRunPreparationFlow.Status.BROWSER_OPEN_FAILED,
                0L,
                true,
                null,
                new IllegalStateException("missing workbook"),
                null,
                null);

        long executionId = handler.finish(result, "https://selected.example");

        assertEquals(0L, executionId);
        assertEquals(1, operations.infoCalls);
        assertEquals(1, operations.warnCalls);
        assertEquals(1, operations.errorCalls);
        assertFalse(operations.resetSingleBlock);
    }

    private static final class FakeOperations implements ScannerTestRunResultHandler.Operations {
        private int errorCalls;
        private int warnCalls;
        private int infoCalls;
        private boolean resetSingleBlock;

        @Override
        public void error(String message, Object... args) {
            errorCalls++;
        }

        @Override
        public void warn(String message, Object... args) {
            warnCalls++;
        }

        @Override
        public void info(String message, Object... args) {
            infoCalls++;
        }

        @Override
        public void resetSingleBlock() {
            resetSingleBlock = true;
        }
    }
}

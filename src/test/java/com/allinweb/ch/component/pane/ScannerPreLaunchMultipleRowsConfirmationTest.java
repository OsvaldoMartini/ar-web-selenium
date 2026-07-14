package com.allinweb.ch.component.pane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.util.ARExecution;
import com.allinweb.ch.util.ExtractedData;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerPreLaunchMultipleRowsConfirmationTest {

    @Test
    void confirmSkipsModalWhenConfirmationNotRequired() {
        FakeExcelLoader loader = new FakeExcelLoader();
        FakeOperations operations = new FakeOperations();
        ScannerPreLaunchMultipleRowsConfirmation confirmation =
                new ScannerPreLaunchMultipleRowsConfirmation(loader, operations);

        assertTrue(confirmation.confirm());

        assertEquals(0, operations.modalCalls);
        assertEquals(0, operations.warnCalls);
    }

    @Test
    void confirmContinuesWhenUserAccepts() {
        FakeExcelLoader loader = new FakeExcelLoader();
        loader.requiresConfirmation = true;
        FakeOperations operations = new FakeOperations();
        operations.response = ARExecution.DialogModal.OK;
        ScannerPreLaunchMultipleRowsConfirmation confirmation =
                new ScannerPreLaunchMultipleRowsConfirmation(loader, operations);

        assertTrue(confirmation.confirm());

        assertEquals(1, operations.warnCalls);
        assertEquals(1, operations.modalCalls);
        assertEquals(0, operations.interceptCalls);
    }

    @Test
    void confirmStopsAndCleansUpWhenUserStops() {
        FakeExcelLoader loader = new FakeExcelLoader();
        loader.requiresConfirmation = true;
        FakeOperations operations = new FakeOperations();
        operations.response = ARExecution.DialogModal.STOP;
        ScannerPreLaunchMultipleRowsConfirmation confirmation =
                new ScannerPreLaunchMultipleRowsConfirmation(loader, operations);

        assertFalse(confirmation.confirm());

        assertEquals(1, operations.interceptCalls);
        assertEquals(1, operations.notRunningCalls);
        assertEquals(1, operations.reenableCalls);
        assertEquals(1, operations.lastBrowserTabCalls);
    }

    private static final class FakeExcelLoader implements ScannerPreLaunchMultipleRowsConfirmation.ExcelLoader {
        private boolean requiresConfirmation;

        @Override
        public boolean requiresMultipleRowsConfirmation(
                ExtractedData extractedData, List<InstructionLoad> excelDataGoto) {
            return requiresConfirmation;
        }
    }

    private static final class FakeOperations implements ScannerPreLaunchMultipleRowsConfirmation.Operations {
        private final ExtractedData extractedData = new ExtractedData();
        private ARExecution.DialogModal response = ARExecution.DialogModal.NONE;
        private int modalCalls;
        private int warnCalls;
        private int interceptCalls;
        private int notRunningCalls;
        private int reenableCalls;
        private int lastBrowserTabCalls;

        @Override
        public ExtractedData extractedData() {
            return extractedData;
        }

        @Override
        public List<InstructionLoad> excelDataGoto() {
            return List.of();
        }

        @Override
        public ARExecution.DialogModal showMultipleRowsConfirmation() {
            modalCalls++;
            return response;
        }

        @Override
        public void requestIntercept() {
            interceptCalls++;
        }

        @Override
        public void markNotRunning() {
            notRunningCalls++;
        }

        @Override
        public void reenableLaunchButton() {
            reenableCalls++;
        }

        @Override
        public boolean lastBrowserTab() {
            lastBrowserTabCalls++;
            return true;
        }

        @Override
        public void warn(String message) {
            warnCalls++;
        }
    }
}

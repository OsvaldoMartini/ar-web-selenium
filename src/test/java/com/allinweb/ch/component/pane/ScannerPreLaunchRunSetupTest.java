package com.allinweb.ch.component.pane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerPreLaunchRunSetupTest {

    @Test
    void beginRunInitializesStateFromSelectedBlock() {
        RecordingOperations operations = new RecordingOperations();
        operations.selectedBlockOrderNumber = 3;
        ScannerPreLaunchRunSetup setup = new ScannerPreLaunchRunSetup(operations);

        setup.beginRun();

        assertEquals("/excel", operations.excelPath);
        assertEquals(2, operations.executeSpecificBlock);
        assertFalse(operations.runSingleBlock);
        assertEquals(
                List.of(
                        "disableLaunch",
                        "setIntercept:false",
                        "markNotRunning",
                        "resolveExcelBasePath",
                        "setExcelPath:/excel",
                        "selectedBlockOrderNumber",
                        "setExecuteSpecificBlock:2",
                        "setRunSingleBlock:false",
                        "clearFields"),
                operations.calls);
    }

    @Test
    void beginRunStartsAtZeroForNegativeBlockSelection() {
        RecordingOperations operations = new RecordingOperations();
        operations.selectedBlockOrderNumber = -1;
        ScannerPreLaunchRunSetup setup = new ScannerPreLaunchRunSetup(operations);

        setup.beginRun();

        assertEquals(0, operations.executeSpecificBlock);
    }

    @Test
    void beginRunReportsExcelPathErrorAndContinuesSetup() {
        RecordingOperations operations = new RecordingOperations();
        operations.excelPathFailure = new IllegalStateException("missing config");
        ScannerPreLaunchRunSetup setup = new ScannerPreLaunchRunSetup(operations);

        setup.beginRun();

        assertEquals(1, operations.reportedErrors);
        assertEquals(0, operations.executeSpecificBlock);
        assertEquals(
                List.of(
                        "disableLaunch",
                        "setIntercept:false",
                        "markNotRunning",
                        "resolveExcelBasePath",
                        "reportExcelPathError",
                        "selectedBlockOrderNumber",
                        "setExecuteSpecificBlock:0",
                        "setRunSingleBlock:false",
                        "clearFields"),
                operations.calls);
    }

    private static final class RecordingOperations implements ScannerPreLaunchRunSetup.Operations {
        private final List<String> calls = new ArrayList<>();
        private int selectedBlockOrderNumber = 1;
        private int executeSpecificBlock = -1;
        private boolean runSingleBlock = true;
        private String excelPath;
        private RuntimeException excelPathFailure;
        private int reportedErrors;

        @Override
        public void disableLaunch() {
            calls.add("disableLaunch");
        }

        @Override
        public void setInterceptBotJob(boolean intercept) {
            calls.add("setIntercept:" + intercept);
        }

        @Override
        public void markNotRunning() {
            calls.add("markNotRunning");
        }

        @Override
        public String resolveExcelBasePath() {
            calls.add("resolveExcelBasePath");
            if (excelPathFailure != null) {
                throw excelPathFailure;
            }
            return "/excel";
        }

        @Override
        public void setExcelPath(String excelPath) {
            calls.add("setExcelPath:" + excelPath);
            this.excelPath = excelPath;
        }

        @Override
        public void reportExcelPathError(Exception error) {
            calls.add("reportExcelPathError");
            reportedErrors++;
        }

        @Override
        public int selectedBlockOrderNumber() {
            calls.add("selectedBlockOrderNumber");
            return selectedBlockOrderNumber;
        }

        @Override
        public void setExecuteSpecificBlock(int blockIndex) {
            calls.add("setExecuteSpecificBlock:" + blockIndex);
            executeSpecificBlock = blockIndex;
        }

        @Override
        public void setRunSingleBlock(boolean runSingleBlock) {
            calls.add("setRunSingleBlock:" + runSingleBlock);
            this.runSingleBlock = runSingleBlock;
        }

        @Override
        public void clearFields() {
            calls.add("clearFields");
        }
    }
}

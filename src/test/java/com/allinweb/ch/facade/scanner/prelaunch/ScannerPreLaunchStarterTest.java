package com.allinweb.ch.facade.scanner.prelaunch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.allinweb.ch.util.ErrorMessage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerPreLaunchStarterTest {

    @Test
    void startRunsPreLaunchStepsInOrder() {
        RecordingOperations operations = new RecordingOperations();
        ScannerPreLaunchStarter starter = new ScannerPreLaunchStarter(operations);

        starter.start();

        assertEquals(
                List.of(
                        "lastBrowserTab",
                        "beginRun",
                        "loadDefinitions",
                        "reportLoadError",
                        "loadCurrentBotJob",
                        "prepareExcel",
                        "validateExcel",
                        "confirmMultipleExcelRows",
                        "resetInstructionsAndRecall"),
                operations.calls);
    }

    @Test
    void startStopsWhenNotOnLastBrowserTab() {
        RecordingOperations operations = new RecordingOperations();
        operations.lastBrowserTab = false;
        ScannerPreLaunchStarter starter = new ScannerPreLaunchStarter(operations);

        starter.start();

        assertEquals(List.of("lastBrowserTab"), operations.calls);
    }

    @Test
    void startStopsWhenBotJobCannotLoad() {
        RecordingOperations operations = new RecordingOperations();
        operations.loadCurrentBotJob = false;
        ScannerPreLaunchStarter starter = new ScannerPreLaunchStarter(operations);

        starter.start();

        assertEquals(
                List.of(
                        "lastBrowserTab",
                        "beginRun",
                        "loadDefinitions",
                        "reportLoadError",
                        "loadCurrentBotJob"),
                operations.calls);
    }

    @Test
    void startStopsWhenExcelValidationFails() {
        RecordingOperations operations = new RecordingOperations();
        operations.validateExcel = false;
        ScannerPreLaunchStarter starter = new ScannerPreLaunchStarter(operations);

        starter.start();

        assertEquals(
                List.of(
                        "lastBrowserTab",
                        "beginRun",
                        "loadDefinitions",
                        "reportLoadError",
                        "loadCurrentBotJob",
                        "prepareExcel",
                        "validateExcel"),
                operations.calls);
    }

    @Test
    void startStopsWhenMultipleRowsConfirmationStops() {
        RecordingOperations operations = new RecordingOperations();
        operations.confirmMultipleExcelRows = false;
        ScannerPreLaunchStarter starter = new ScannerPreLaunchStarter(operations);

        starter.start();

        assertEquals(
                List.of(
                        "lastBrowserTab",
                        "beginRun",
                        "loadDefinitions",
                        "reportLoadError",
                        "loadCurrentBotJob",
                        "prepareExcel",
                        "validateExcel",
                        "confirmMultipleExcelRows"),
                operations.calls);
    }

    private static final class RecordingOperations implements ScannerPreLaunchStarter.Operations {
        private final List<String> calls = new ArrayList<>();
        private boolean lastBrowserTab = true;
        private boolean loadCurrentBotJob = true;
        private boolean validateExcel = true;
        private boolean confirmMultipleExcelRows = true;

        @Override
        public boolean lastBrowserTab() {
            calls.add("lastBrowserTab");
            return lastBrowserTab;
        }

        @Override
        public void beginRun() {
            calls.add("beginRun");
        }

        @Override
        public ErrorMessage loadDefinitions() {
            calls.add("loadDefinitions");
            return null;
        }

        @Override
        public void reportLoadError(ErrorMessage errorMessage) {
            calls.add("reportLoadError");
        }

        @Override
        public boolean loadCurrentBotJob() {
            calls.add("loadCurrentBotJob");
            return loadCurrentBotJob;
        }

        @Override
        public void prepareExcel() {
            calls.add("prepareExcel");
        }

        @Override
        public boolean validateExcel() {
            calls.add("validateExcel");
            return validateExcel;
        }

        @Override
        public boolean confirmMultipleExcelRows() {
            calls.add("confirmMultipleExcelRows");
            return confirmMultipleExcelRows;
        }

        @Override
        public void resetInstructionsAndRecall() {
            calls.add("resetInstructionsAndRecall");
        }
    }
}

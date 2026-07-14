package com.allinweb.ch.component.pane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.BotJobLoadDTO;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerPreLaunchBotJobSelectionTest {

    @Test
    void loadCurrentBotJobAppliesLoadedSelection() {
        RecordingOperations operations = new RecordingOperations();
        BotJobLoadDTO loadedBotJob = botJob(42, 8);
        operations.selection = ScannerPreLaunchPreparation.BotJobSelection.loaded(
                loadedBotJob, "Loaded Job", "C:\\excel\\Loaded Job.xlsx");
        ScannerPreLaunchBotJobSelection selection = new ScannerPreLaunchBotJobSelection(operations);

        assertTrue(selection.loadCurrentBotJob());

        assertSame(loadedBotJob, operations.appliedSelection.botJob());
        assertEquals(List.of("currentBotJob", "excelPath", "loadCurrentBotJob", "applySelection"), operations.calls);
    }

    @Test
    void loadCurrentBotJobRejectsMissingBotJobAndReenablesLaunch() {
        RecordingOperations operations = new RecordingOperations();
        operations.selection = ScannerPreLaunchPreparation.BotJobSelection.missingBotJob();
        ScannerPreLaunchBotJobSelection selection = new ScannerPreLaunchBotJobSelection(operations);

        assertFalse(selection.loadCurrentBotJob());

        assertEquals(1, operations.errorCalls);
        assertEquals(1, operations.reenableCalls);
        assertEquals(List.of("currentBotJob", "excelPath", "loadCurrentBotJob", "error", "reenableLaunchButton"),
                operations.calls);
    }

    @Test
    void loadCurrentBotJobRejectsMissingHomeBankingAndReenablesLaunch() {
        RecordingOperations operations = new RecordingOperations();
        operations.selection = ScannerPreLaunchPreparation.BotJobSelection.missingHomeBanking();
        ScannerPreLaunchBotJobSelection selection = new ScannerPreLaunchBotJobSelection(operations);

        assertFalse(selection.loadCurrentBotJob());

        assertEquals(1, operations.errorCalls);
        assertEquals(1, operations.reenableCalls);
        assertEquals(List.of("currentBotJob", "excelPath", "loadCurrentBotJob", "error", "reenableLaunchButton"),
                operations.calls);
    }

    private static BotJobLoadDTO botJob(int id, int homeBankingId) {
        BotJobLoadDTO botJob = new BotJobLoadDTO();
        botJob.setId(id);
        botJob.setHomeBankingId(homeBankingId);
        return botJob;
    }

    private static final class RecordingOperations implements ScannerPreLaunchBotJobSelection.Operations {
        private final List<String> calls = new ArrayList<>();
        private final BotJobLoadDTO currentBotJob = botJob(7, 3);
        private ScannerPreLaunchPreparation.BotJobSelection selection =
                ScannerPreLaunchPreparation.BotJobSelection.loaded(botJob(7, 3), "Job", "C:\\excel\\Job.xlsx");
        private ScannerPreLaunchPreparation.BotJobSelection appliedSelection;
        private int reenableCalls;
        private int errorCalls;

        @Override
        public BotJobLoadDTO currentBotJob() {
            calls.add("currentBotJob");
            return currentBotJob;
        }

        @Override
        public String excelPath() {
            calls.add("excelPath");
            return "C:\\excel";
        }

        @Override
        public ScannerPreLaunchPreparation.BotJobSelection loadCurrentBotJob(
                BotJobLoadDTO currentBotJob, String excelPath) {
            calls.add("loadCurrentBotJob");
            return selection;
        }

        @Override
        public void applySelection(ScannerPreLaunchPreparation.BotJobSelection selection) {
            calls.add("applySelection");
            appliedSelection = selection;
        }

        @Override
        public void reenableLaunchButton() {
            calls.add("reenableLaunchButton");
            reenableCalls++;
        }

        @Override
        public void error(String message) {
            calls.add("error");
            errorCalls++;
        }
    }
}

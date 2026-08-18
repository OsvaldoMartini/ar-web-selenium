package com.allinweb.ch.facade.scanner.prelaunch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerPreLaunchRecallAfterResetTest {

    @Test
    void resetInstructionsAndRecallRecallsWhenResetSucceeds() {
        RecordingOperations operations = new RecordingOperations();
        operations.resetResult = true;
        ScannerPreLaunchRecallAfterReset recallAfterReset = new ScannerPreLaunchRecallAfterReset(operations);

        recallAfterReset.resetInstructionsAndRecall();

        assertEquals(List.of("resetInstructionExecutionFlags", "recallJob"), operations.calls);
    }

    @Test
    void resetInstructionsAndRecallDoesNotRecallWhenResetFails() {
        RecordingOperations operations = new RecordingOperations();
        operations.resetResult = false;
        ScannerPreLaunchRecallAfterReset recallAfterReset = new ScannerPreLaunchRecallAfterReset(operations);

        recallAfterReset.resetInstructionsAndRecall();

        assertEquals(List.of("resetInstructionExecutionFlags"), operations.calls);
    }

    private static final class RecordingOperations implements ScannerPreLaunchRecallAfterReset.Operations {
        private final List<String> calls = new ArrayList<>();
        private boolean resetResult;

        @Override
        public boolean resetInstructionExecutionFlags() {
            calls.add("resetInstructionExecutionFlags");
            return resetResult;
        }

        @Override
        public boolean recallJob() {
            calls.add("recallJob");
            return true;
        }
    }
}

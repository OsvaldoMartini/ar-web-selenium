package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerRunningProcessCleanupServiceTest {
    private final ScannerRunningProcessCleanupService service = new ScannerRunningProcessCleanupService();

    @Test
    void resetsBrowserAndControlsWithoutInterceptWhenJobIsStopped() {
        RecordingOperations operations = new RecordingOperations(false);

        service.cleanup(operations);

        assertEquals(
                List.of("clearClone", "enableLaunch", "revertClone", "revertHover", "isJobRunning"),
                operations.calls);
    }

    @Test
    void enablesInterceptionWhenJobIsStillRunning() {
        RecordingOperations operations = new RecordingOperations(true);

        service.cleanup(operations);

        assertEquals(
                List.of("clearClone", "enableLaunch", "revertClone", "revertHover", "isJobRunning", "intercept"),
                operations.calls);
    }

    private static final class RecordingOperations implements ScannerRunningProcessCleanupService.Operations {
        private final List<String> calls = new ArrayList<>();
        private final boolean jobRunning;

        private RecordingOperations(boolean jobRunning) {
            this.jobRunning = jobRunning;
        }

        @Override
        public void clearCloneSelection() {
            calls.add("clearClone");
        }

        @Override
        public void enableLaunchAction() {
            calls.add("enableLaunch");
        }

        @Override
        public void revertCloneInjections() {
            calls.add("revertClone");
        }

        @Override
        public void revertHoverPickInjections() {
            calls.add("revertHover");
        }

        @Override
        public boolean isJobRunning() {
            calls.add("isJobRunning");
            return jobRunning;
        }

        @Override
        public void interceptBotJob() {
            calls.add("intercept");
        }
    }
}

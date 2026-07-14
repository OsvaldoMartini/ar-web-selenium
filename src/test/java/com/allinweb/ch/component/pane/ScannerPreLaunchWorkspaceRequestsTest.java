package com.allinweb.ch.component.pane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerPreLaunchWorkspaceRequestsTest {

    @Test
    void requestStartValidatesJobAndSchedulesStart() {
        RecordingOperations operations = new RecordingOperations();
        ScannerPreLaunchWorkspaceRequests requests = new ScannerPreLaunchWorkspaceRequests(operations);

        requests.requestStart(42);

        assertEquals(List.of("currentBotJobId", "preLaunchControlsReady", "runLater", "startPreLaunch"),
                operations.calls);
    }

    @Test
    void requestStopValidatesJobAndSchedulesStop() {
        RecordingOperations operations = new RecordingOperations();
        ScannerPreLaunchWorkspaceRequests requests = new ScannerPreLaunchWorkspaceRequests(operations);

        requests.requestStop(42);

        assertEquals(List.of("currentBotJobId", "stopPreLaunchControlsReady", "runLater", "stopPreLaunch"),
                operations.calls);
    }

    @Test
    void requestStartRejectsMismatchedJob() {
        RecordingOperations operations = new RecordingOperations();
        operations.currentBotJobId = 7;
        ScannerPreLaunchWorkspaceRequests requests = new ScannerPreLaunchWorkspaceRequests(operations);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> requests.requestStart(42));

        assertEquals("Scanner workspace is not open for Bot Job 42", error.getMessage());
        assertEquals(List.of("currentBotJobId"), operations.calls);
    }

    @Test
    void requestStartRejectsMissingControls() {
        RecordingOperations operations = new RecordingOperations();
        operations.preLaunchControlsReady = false;
        ScannerPreLaunchWorkspaceRequests requests = new ScannerPreLaunchWorkspaceRequests(operations);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> requests.requestStart(42));

        assertEquals("Scanner Pre-Launch controls are not ready", error.getMessage());
        assertEquals(List.of("currentBotJobId", "preLaunchControlsReady"), operations.calls);
    }

    @Test
    void requestStopRejectsMissingStopControls() {
        RecordingOperations operations = new RecordingOperations();
        operations.stopPreLaunchControlsReady = false;
        ScannerPreLaunchWorkspaceRequests requests = new ScannerPreLaunchWorkspaceRequests(operations);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> requests.requestStop(42));

        assertEquals("Scanner Pre-Launch controls are not ready", error.getMessage());
        assertEquals(List.of("currentBotJobId", "stopPreLaunchControlsReady"), operations.calls);
    }

    private static final class RecordingOperations implements ScannerPreLaunchWorkspaceRequests.Operations {
        private final List<String> calls = new ArrayList<>();
        private Integer currentBotJobId = 42;
        private boolean preLaunchControlsReady = true;
        private boolean stopPreLaunchControlsReady = true;

        @Override
        public Integer currentBotJobId() {
            calls.add("currentBotJobId");
            return currentBotJobId;
        }

        @Override
        public boolean preLaunchControlsReady() {
            calls.add("preLaunchControlsReady");
            return preLaunchControlsReady;
        }

        @Override
        public boolean stopPreLaunchControlsReady() {
            calls.add("stopPreLaunchControlsReady");
            return stopPreLaunchControlsReady;
        }

        @Override
        public void runLater(Runnable task) {
            calls.add("runLater");
            task.run();
        }

        @Override
        public void startPreLaunch() {
            calls.add("startPreLaunch");
        }

        @Override
        public void stopPreLaunch() {
            calls.add("stopPreLaunch");
        }
    }
}

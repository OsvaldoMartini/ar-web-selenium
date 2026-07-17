package com.allinweb.ch.facade.scanner.prelaunch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerPreLaunchStopperTest {

    @Test
    void stopRequestsInterceptAndMarksRunStopped() {
        RecordingOperations operations = new RecordingOperations();
        ScannerPreLaunchStopper stopper = new ScannerPreLaunchStopper(operations);

        stopper.stop();

        assertEquals(List.of("enableLaunch", "requestIntercept", "markNotRunning", "lastBrowserTab"), operations.calls);
    }

    private static final class RecordingOperations implements ScannerPreLaunchStopper.Operations {
        private final List<String> calls = new ArrayList<>();

        @Override
        public void enableLaunch() {
            calls.add("enableLaunch");
        }

        @Override
        public void requestIntercept() {
            calls.add("requestIntercept");
        }

        @Override
        public void markNotRunning() {
            calls.add("markNotRunning");
        }

        @Override
        public boolean lastBrowserTab() {
            calls.add("lastBrowserTab");
            return true;
        }
    }
}

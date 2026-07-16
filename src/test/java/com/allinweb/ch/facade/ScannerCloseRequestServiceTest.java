package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerCloseRequestServiceTest {
    private final ScannerCloseRequestService service = new ScannerCloseRequestService();

    @Test
    void interruptsThreadsAndClosesWebDriverResourcesInOrder() {
        RecordingCloseRequest request = new RecordingCloseRequest(true);

        service.close(request);

        assertEquals(
                List.of("interrupt", "closeWebDrivers", "quitCurrentDriver", "clearCurrentDriver", "shutdown"),
                request.calls);
    }

    @Test
    void onlyInterruptsThreadsWhenNoWebDriverExists() {
        RecordingCloseRequest request = new RecordingCloseRequest(false);

        service.close(request);

        assertEquals(List.of("interrupt"), request.calls);
    }

    @Test
    void reportsCloseFailure() {
        RecordingCloseRequest request = new RecordingCloseRequest(true);
        request.failQuit = true;

        service.close(request);

        assertEquals(List.of("interrupt", "closeWebDrivers", "quitCurrentDriver", "failed:boom"), request.calls);
    }

    private static final class RecordingCloseRequest implements ScannerCloseRequestService.CloseRequest {
        private final List<String> calls = new ArrayList<>();
        private final boolean hasWebDriver;
        private boolean failQuit;

        private RecordingCloseRequest(boolean hasWebDriver) {
            this.hasWebDriver = hasWebDriver;
        }

        @Override
        public void interruptThreads() {
            calls.add("interrupt");
        }

        @Override
        public boolean hasWebDriver() {
            return hasWebDriver;
        }

        @Override
        public void closeWebDrivers() {
            calls.add("closeWebDrivers");
        }

        @Override
        public void quitCurrentDriver() {
            calls.add("quitCurrentDriver");
            if (failQuit) {
                throw new IllegalStateException("boom");
            }
        }

        @Override
        public void clearCurrentDriver() {
            calls.add("clearCurrentDriver");
        }

        @Override
        public void shutdownExecutors() {
            calls.add("shutdown");
        }

        @Override
        public void closeFailed(Exception error) {
            calls.add("failed:" + error.getMessage());
        }
    }
}

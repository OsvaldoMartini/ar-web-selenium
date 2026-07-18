package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerCloseRequestServiceTest {
    private final ScannerCloseRequestService service = new ScannerCloseRequestService();

    @Test
    void interruptsThreadsAndClosesBrowserRuntimeBeforeExecutors() {
        RecordingCloseRequest request = new RecordingCloseRequest();

        service.close(request);

        assertEquals(List.of("interrupt", "closeBrowserRuntime", "shutdown"), request.calls);
    }

    @Test
    void reportsCloseFailureAndStillShutsDownExecutors() {
        RecordingCloseRequest request = new RecordingCloseRequest();
        request.failClose = true;

        service.close(request);

        assertEquals(
                List.of("interrupt", "closeBrowserRuntime", "failed:boom", "shutdown"), request.calls);
    }

    private static final class RecordingCloseRequest implements ScannerCloseRequestService.CloseRequest {
        private final List<String> calls = new ArrayList<>();
        private boolean failClose;

        @Override
        public void interruptThreads() {
            calls.add("interrupt");
        }

        @Override
        public void closeBrowserRuntime() {
            calls.add("closeBrowserRuntime");
            if (failClose) {
                throw new IllegalStateException("boom");
            }
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

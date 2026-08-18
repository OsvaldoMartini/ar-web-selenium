package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.BotJobLoadDTO;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ScannerTestRunHandlersTest {
    private final ScannerTestRunHandlers registry = ScannerTestRunHandlers.getInstance();
    private RecordingHandler handler;

    @AfterEach
    void cleanup() {
        registry.unregister(handler);
    }

    @Test
    void routesLifecycleCommandsToRegisteredHandler() {
        handler = new RecordingHandler();
        registry.register(handler);

        long executionId = registry.startTestRun(null, 7, "https://example.test", true, () -> false);
        registry.cancelTestRunStartup();
        boolean stopped = registry.stopTestRun(executionId);
        boolean complete = registry.isTestRunComplete(executionId);
        String outcome = registry.testRunTerminalOutcome(executionId);

        assertEquals(42L, executionId);
        assertTrue(stopped);
        assertFalse(complete);
        assertEquals("RUNNING", outcome);
        assertEquals(
                List.of(
                        "start:7:https://example.test:true",
                        "cancel-startup",
                        "stop:42",
                        "complete:42",
                        "outcome:42"),
                handler.calls);
    }

    @Test
    void usesSafeDefaultsWhenNoHandlerIsRegistered() {
        assertEquals(0L, registry.startTestRun(null, 1, "", false, () -> false));
        registry.cancelTestRunStartup();
        assertFalse(registry.stopTestRun(99L));
        assertTrue(registry.isTestRunComplete(99L));
        assertEquals("UNAVAILABLE", registry.testRunTerminalOutcome(99L));
    }

    private static final class RecordingHandler implements ScannerTestRunHandler {
        private final List<String> calls = new ArrayList<>();

        @Override
        public long startTestRun(
                BotJobLoadDTO botJob,
                int blockOrderNumber,
                String endpointUrl,
                boolean runSingleBlock,
                BooleanSupplier cancellationRequested) {
            calls.add("start:" + blockOrderNumber + ":" + endpointUrl + ":" + runSingleBlock);
            return 42L;
        }

        @Override
        public void cancelTestRunStartup() {
            calls.add("cancel-startup");
        }

        @Override
        public boolean stopTestRun(long executionId) {
            calls.add("stop:" + executionId);
            return true;
        }

        @Override
        public boolean isTestRunComplete(long executionId) {
            calls.add("complete:" + executionId);
            return false;
        }

        @Override
        public String testRunTerminalOutcome(long executionId) {
            calls.add("outcome:" + executionId);
            return "RUNNING";
        }
    }
}

package com.allinweb.ch.facade.scanner.prelaunch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerPreLaunchExecutionTaskTest {

    @Test
    void runCompletesSuccessfulExecutionAndAlwaysCleansUp() {
        RecordingOperations operations = new RecordingOperations();
        operations.executionPassed = true;
        ScannerPreLaunchExecutionTask task = new ScannerPreLaunchExecutionTask(12L, operations);

        task.run();

        assertEquals(
                List.of(
                        "executeJob",
                        "completeExecution:12:true",
                        "clearActiveExecution:12",
                        "markNotRunning",
                        "stopScreenshotLoop",
                        "reenableLaunchButton"),
                operations.calls);
    }

    @Test
    void runReportsExecutionErrorAndCompletesAsFailed() {
        RecordingOperations operations = new RecordingOperations();
        RuntimeException failure = new RuntimeException("boom");
        operations.failure = failure;
        ScannerPreLaunchExecutionTask task = new ScannerPreLaunchExecutionTask(13L, operations);

        task.run();

        assertSame(failure, operations.reportedError);
        assertEquals(
                List.of(
                        "executeJob",
                        "reportExecutionError",
                        "completeExecution:13:false",
                        "clearActiveExecution:13",
                        "markNotRunning",
                        "stopScreenshotLoop",
                        "reenableLaunchButton"),
                operations.calls);
    }

    private static final class RecordingOperations implements ScannerPreLaunchExecutionTask.Operations {
        private final List<String> calls = new ArrayList<>();
        private boolean executionPassed;
        private RuntimeException failure;
        private Throwable reportedError;

        @Override
        public boolean executeJob() {
            calls.add("executeJob");
            if (failure != null) {
                throw failure;
            }
            return executionPassed;
        }

        @Override
        public void reportExecutionError(Throwable error) {
            calls.add("reportExecutionError");
            reportedError = error;
        }

        @Override
        public void completeExecution(long executionId, boolean executionPassed) {
            calls.add("completeExecution:" + executionId + ":" + executionPassed);
        }

        @Override
        public void clearActiveExecution(long executionId) {
            calls.add("clearActiveExecution:" + executionId);
        }

        @Override
        public void markNotRunning() {
            calls.add("markNotRunning");
        }

        @Override
        public void stopScreenshotLoop() {
            calls.add("stopScreenshotLoop");
        }

        @Override
        public void reenableLaunchButton() {
            calls.add("reenableLaunchButton");
        }
    }
}

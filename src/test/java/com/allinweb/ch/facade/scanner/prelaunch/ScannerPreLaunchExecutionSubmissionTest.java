package com.allinweb.ch.facade.scanner.prelaunch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ScannerPreLaunchExecutionSubmissionTest {

    @Test
    void submitSchedulesExecutionTask() {
        RecordingExecutor executor = new RecordingExecutor();
        RecordingOperations operations = new RecordingOperations();
        RecordingFailureReporter reporter = new RecordingFailureReporter();
        ScannerPreLaunchExecutionSubmission submission =
                new ScannerPreLaunchExecutionSubmission(executor, operations, reporter);

        boolean submitted = submission.submit(21L);

        assertTrue(submitted);
        assertEquals(1, executor.tasks.size());
        executor.tasks.get(0).run();
        assertEquals(List.of("executeJob", "completeExecution:21:false", "clearActiveExecution:21",
                "markNotRunning", "stopScreenshotLoop", "reenableLaunchButton"), operations.calls);
        assertEquals(0, reporter.errors.size());
    }

    @Test
    void submitCleansUpWhenExecutorRejectsTask() {
        RuntimeException failure = new RuntimeException("reject");
        RecordingExecutor executor = new RecordingExecutor();
        executor.failure = failure;
        RecordingOperations operations = new RecordingOperations();
        RecordingFailureReporter reporter = new RecordingFailureReporter();
        ScannerPreLaunchExecutionSubmission submission =
                new ScannerPreLaunchExecutionSubmission(executor, operations, reporter);

        boolean submitted = submission.submit(22L);

        assertEquals(false, submitted);
        assertEquals(List.of("completeExecution:22:false", "clearActiveExecution:22",
                "markNotRunning", "stopScreenshotLoop", "reenableLaunchButton"), operations.calls);
        assertEquals(1, reporter.errors.size());
        assertSame(failure, reporter.errors.get(0));
    }

    private static final class RecordingExecutor extends AbstractExecutorService {
        private final List<Runnable> tasks = new ArrayList<>();
        private RuntimeException failure;

        @Override
        public void shutdown() {}

        @Override
        public List<Runnable> shutdownNow() {
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return false;
        }

        @Override
        public void execute(Runnable command) {
            if (failure != null) {
                throw failure;
            }
            tasks.add(command);
        }
    }

    private static final class RecordingOperations implements ScannerPreLaunchExecutionTask.Operations {
        private final List<String> calls = new ArrayList<>();

        @Override
        public boolean executeJob() {
            calls.add("executeJob");
            return false;
        }

        @Override
        public void reportExecutionError(Throwable error) {
            calls.add("reportExecutionError");
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

    private static final class RecordingFailureReporter implements ScannerPreLaunchExecutionSubmission.FailureReporter {
        private final List<Exception> errors = new ArrayList<>();

        @Override
        public void report(Exception error) {
            errors.add(error);
        }
    }
}

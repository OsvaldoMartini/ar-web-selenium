package com.allinweb.ch.facade.scanner.prelaunch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.allinweb.ch.facade.scanner.prelaunch.ScannerPreLaunchExecutionGate;
import com.allinweb.ch.facade.scanner.prelaunch.ScannerPreLaunchExecutionTask;
import com.allinweb.ch.facade.scanner.prelaunch.ScannerPreLaunchWindowBookkeeping;
import com.allinweb.ch.facade.scanner.testrun.TestRunExecutionOutcomeTracker;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class ScannerPreLaunchExecutionCoordinatorTest {

    @Test
    void recallSubmitsNewExecutionAndRefreshesWindows() {
        AtomicBoolean running = new AtomicBoolean(false);
        AtomicLong active = new AtomicLong();
        AtomicLong lastSubmitted = new AtomicLong();
        ScannerPreLaunchExecutionGate gate = new ScannerPreLaunchExecutionGate(
                running, new AtomicLong(), active, lastSubmitted, new TestRunExecutionOutcomeTracker());
        RecordingExecutor executor = new RecordingExecutor();
        RecordingWindowOperations windowOperations = new RecordingWindowOperations();
        ScannerPreLaunchExecutionCoordinator coordinator = new ScannerPreLaunchExecutionCoordinator(
                gate,
                executor,
                new RecordingExecutionOperations(),
                new ScannerPreLaunchWindowBookkeeping(windowOperations),
                id -> true,
                new RecordingOperations());

        long executionId = coordinator.recallJobExecutionId();

        assertEquals(1L, executionId);
        assertEquals(1L, active.get());
        assertEquals(1L, lastSubmitted.get());
        assertEquals(1, executor.tasks.size());
        assertEquals(1, windowOperations.refreshCalls);
    }

    @Test
    void recallRejectsIncompleteActiveExecutionWithoutWindowRefresh() {
        ScannerPreLaunchExecutionGate gate = new ScannerPreLaunchExecutionGate(
                new AtomicBoolean(false),
                new AtomicLong(),
                new AtomicLong(7L),
                new AtomicLong(),
                new TestRunExecutionOutcomeTracker());
        RecordingWindowOperations windowOperations = new RecordingWindowOperations();
        RecordingOperations operations = new RecordingOperations();
        ScannerPreLaunchExecutionCoordinator coordinator = new ScannerPreLaunchExecutionCoordinator(
                gate,
                new RecordingExecutor(),
                new RecordingExecutionOperations(),
                new ScannerPreLaunchWindowBookkeeping(windowOperations),
                id -> false,
                operations);

        long executionId = coordinator.recallJobExecutionId();

        assertEquals(0L, executionId);
        assertEquals(1, operations.infoCalls);
        assertEquals(0, windowOperations.refreshCalls);
    }

    @Test
    void recallReportsAlreadyRunningAndRefreshesWindows() {
        ScannerPreLaunchExecutionGate gate = new ScannerPreLaunchExecutionGate(
                new AtomicBoolean(true),
                new AtomicLong(),
                new AtomicLong(),
                new AtomicLong(),
                new TestRunExecutionOutcomeTracker());
        RecordingWindowOperations windowOperations = new RecordingWindowOperations();
        RecordingOperations operations = new RecordingOperations();
        ScannerPreLaunchExecutionCoordinator coordinator = new ScannerPreLaunchExecutionCoordinator(
                gate,
                new RecordingExecutor(),
                new RecordingExecutionOperations(),
                new ScannerPreLaunchWindowBookkeeping(windowOperations),
                id -> true,
                operations);

        long executionId = coordinator.recallJobExecutionId();

        assertEquals(0L, executionId);
        assertEquals(1, operations.infoCalls);
        assertEquals(1, windowOperations.refreshCalls);
    }

    @Test
    void recallCleansUpAndReportsExecutorSubmissionFailure() {
        RecordingExecutor executor = new RecordingExecutor();
        executor.failure = new RuntimeException("reject");
        ScannerPreLaunchExecutionGate gate = new ScannerPreLaunchExecutionGate(
                new AtomicBoolean(false),
                new AtomicLong(),
                new AtomicLong(),
                new AtomicLong(),
                new TestRunExecutionOutcomeTracker());
        RecordingExecutionOperations executionOperations = new RecordingExecutionOperations();
        RecordingOperations operations = new RecordingOperations();
        RecordingWindowOperations windowOperations = new RecordingWindowOperations();
        ScannerPreLaunchExecutionCoordinator coordinator = new ScannerPreLaunchExecutionCoordinator(
                gate,
                executor,
                executionOperations,
                new ScannerPreLaunchWindowBookkeeping(windowOperations),
                id -> true,
                operations);

        long executionId = coordinator.recallJobExecutionId();

        assertEquals(0L, executionId);
        assertEquals(List.of("complete:1:false", "clear:1", "notRunning", "stopScreens", "enableLaunch"),
                executionOperations.calls);
        assertEquals(1, operations.errorCalls);
        assertEquals(1, windowOperations.refreshCalls);
    }

    private static final class RecordingExecutor extends AbstractExecutorService {
        private final List<Runnable> tasks = new ArrayList<>();
        private RuntimeException failure;

        @Override
        public void shutdown() {
        }

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

    private static final class RecordingExecutionOperations implements ScannerPreLaunchExecutionTask.Operations {
        private final List<String> calls = new ArrayList<>();

        @Override
        public boolean executeJob() {
            calls.add("executeJob");
            return true;
        }

        @Override
        public void reportExecutionError(Throwable error) {
            calls.add("error");
        }

        @Override
        public void completeExecution(long executionId, boolean executionPassed) {
            calls.add("complete:" + executionId + ":" + executionPassed);
        }

        @Override
        public void clearActiveExecution(long executionId) {
            calls.add("clear:" + executionId);
        }

        @Override
        public void markNotRunning() {
            calls.add("notRunning");
        }

        @Override
        public void stopScreenshotLoop() {
            calls.add("stopScreens");
        }

        @Override
        public void reenableLaunchButton() {
            calls.add("enableLaunch");
        }
    }

    private static final class RecordingWindowOperations implements ScannerPreLaunchWindowBookkeeping.Operations {
        private int refreshCalls;

        @Override
        public Integer currentWindowHandleCount() {
            return 2;
        }

        @Override
        public int knownWindowHandleCount() {
            return 1;
        }

        @Override
        public void updateWindowHandlesList() {
            refreshCalls++;
        }

        @Override
        public void updateButtonState() {
        }
    }

    private static final class RecordingOperations implements ScannerPreLaunchExecutionCoordinator.Operations {
        private int infoCalls;
        private int errorCalls;

        @Override
        public void info(String message, Object... args) {
            infoCalls++;
        }

        @Override
        public void error(String message, Object... args) {
            errorCalls++;
        }
    }
}

package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ApplicationShutdownCoordinatorTest {

    @Test
    void shutdownRunsEveryOwnedCleanupStageInOrderBeforeExitingBackend() {
        RecordingShutdownOperations operations = new RecordingShutdownOperations();
        ApplicationShutdownCoordinator coordinator =
                new ApplicationShutdownCoordinator(operations, Runnable::run);

        assertFalse(coordinator.isShutdownRequested());
        assertTrue(coordinator.requestShutdown());

        assertTrue(coordinator.isShutdownRequested());
        assertEquals(
                List.of(
                        "broadcastShutdown",
                        "stopOwnedAutomation",
                        "stopPluginWatcher",
                        "retireWorkspaces",
                        "closeSessions",
                        "shutdownExecutors",
                        "stopServers",
                        "releaseSingleInstance",
                        "exitBackend:0"),
                operations.events);
    }

    @Test
    void repeatedRequestsCannotRunCleanupOrExitTwice() {
        RecordingShutdownOperations operations = new RecordingShutdownOperations();
        ApplicationShutdownCoordinator coordinator =
                new ApplicationShutdownCoordinator(operations, Runnable::run);

        assertTrue(coordinator.requestShutdown());
        assertFalse(coordinator.requestShutdown());
        assertFalse(coordinator.requestShutdown());

        assertEquals(1, operations.exitCount.get());
        assertEquals(9, operations.events.size());
    }

    @Test
    void oneCleanupFailureCannotSkipLaterStagesOrBackendExit() {
        RecordingShutdownOperations operations = new RecordingShutdownOperations() {
            @Override
            public void stopPluginWatcher() {
                super.stopPluginWatcher();
                throw new IllegalStateException("watcher failed");
            }

            @Override
            public void closeSessions() {
                super.closeSessions();
                throw new IllegalStateException("sessions failed");
            }
        };
        ApplicationShutdownCoordinator coordinator =
                new ApplicationShutdownCoordinator(operations, Runnable::run);

        assertTrue(coordinator.requestShutdown());

        assertEquals(
                List.of(
                        "broadcastShutdown",
                        "stopOwnedAutomation",
                        "stopPluginWatcher",
                        "retireWorkspaces",
                        "closeSessions",
                        "shutdownExecutors",
                        "stopServers",
                        "releaseSingleInstance",
                        "exitBackend:0"),
                operations.events);
        assertEquals(1, operations.exitCount.get());
    }

    @Test
    void concurrentRequestsElectExactlyOneShutdownOwner() throws Exception {
        RecordingShutdownOperations operations = new RecordingShutdownOperations();
        ExecutorService shutdownExecutor = Executors.newSingleThreadExecutor();
        ExecutorService callers = Executors.newFixedThreadPool(8);
        try {
            ApplicationShutdownCoordinator coordinator =
                    new ApplicationShutdownCoordinator(operations, shutdownExecutor);
            CountDownLatch ready = new CountDownLatch(8);
            CountDownLatch start = new CountDownLatch(1);
            List<Boolean> results = Collections.synchronizedList(new ArrayList<>());

            for (int index = 0; index < 8; index++) {
                callers.submit(() -> {
                    ready.countDown();
                    start.await();
                    results.add(coordinator.requestShutdown());
                    return null;
                });
            }

            assertTrue(ready.await(2, TimeUnit.SECONDS));
            start.countDown();
            callers.shutdown();
            assertTrue(callers.awaitTermination(2, TimeUnit.SECONDS));
            shutdownExecutor.shutdown();
            assertTrue(shutdownExecutor.awaitTermination(2, TimeUnit.SECONDS));

            assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
            assertEquals(7, results.stream().filter(result -> !result).count());
            assertEquals(1, operations.exitCount.get());
            assertEquals(9, operations.events.size());
        } finally {
            callers.shutdownNow();
            shutdownExecutor.shutdownNow();
        }
    }

    private static class RecordingShutdownOperations
            implements ApplicationShutdownCoordinator.ShutdownOperations {
        private final List<String> events = Collections.synchronizedList(new ArrayList<>());
        private final AtomicInteger exitCount = new AtomicInteger();

        @Override
        public void broadcastShutdown() {
            events.add("broadcastShutdown");
        }

        @Override
        public void stopOwnedAutomation() {
            events.add("stopOwnedAutomation");
        }

        @Override
        public void stopPluginWatcher() {
            events.add("stopPluginWatcher");
        }

        @Override
        public void retireWorkspaces() {
            events.add("retireWorkspaces");
        }

        @Override
        public void closeSessions() {
            events.add("closeSessions");
        }

        @Override
        public void shutdownExecutors() {
            events.add("shutdownExecutors");
        }

        @Override
        public void stopServers() {
            events.add("stopServers");
        }

        @Override
        public void releaseSingleInstance() {
            events.add("releaseSingleInstance");
        }

        @Override
        public void exitBackend(int status) {
            events.add("exitBackend:" + status);
            exitCount.incrementAndGet();
        }
    }
}

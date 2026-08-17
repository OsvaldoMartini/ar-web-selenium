package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.facade.execution.SmokeRecoveryScannerRegistry;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.RuntimeMode;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExecutionPauseCoordinatorTest {

    private BotJobDetailsWorkspaceRegistry registry;
    private FakePublisher publisher;
    private ExecutionPauseCoordinator coordinator;
    private BotJobDetailsWorkspaceRegistry.ExecutionAttempt attempt;

    @BeforeEach
    void setUp() {
        registry = new BotJobDetailsWorkspaceRegistry();
        BotJobLoadDTO job = new BotJobLoadDTO();
        job.setId(42);
        job.setName("Payments");
        job.setHomeBankingId(7);
        job.setHomeUrlId(8);
        registry.activate(job, false);
        long epoch = registry.require(42).workspaceEpoch();
        attempt = registry.beginTestRun(42, epoch);
        registry.markTestRunRunning(attempt);
        publisher = new FakePublisher();
        coordinator = new ExecutionPauseCoordinator(registry, publisher);
    }

    @AfterEach
    void tearDown() {
        coordinator.cancelAll();
        SmokeRecoveryScannerRegistry.getInstance().clear("recovery-run");
    }

    @Test
    void blocksTheExecutionWorkerUntilTheExactContinueResponseArrives() throws Exception {
        CompletableFuture<ExecutionPauseCoordinator.Decision> paused = startPause();
        ExecutionPauseCoordinator.PauseRequest request = publisher.awaitRequest();

        assertFalse(paused.isDone());
        ExecutionPauseCoordinator.ResponseResult result = coordinator.respond(response(request, "CONTINUE"));

        assertTrue(result.accepted());
        assertEquals(ExecutionPauseCoordinator.Decision.CONTINUE, paused.get(2, TimeUnit.SECONDS));
    }

    @Test
    void rejectsAStaleResponseWithoutReleasingTheCurrentExecution() throws Exception {
        CompletableFuture<ExecutionPauseCoordinator.Decision> paused = startPause();
        ExecutionPauseCoordinator.PauseRequest request = publisher.awaitRequest();
        ExecutionPauseCoordinator.PauseResponse stale = new ExecutionPauseCoordinator.PauseResponse(
                "stale-request",
                request.botJobId(),
                request.workspaceEpoch(),
                request.executionId(),
                request.executionAttemptId(),
                "CONTINUE");

        assertFalse(coordinator.respond(stale).accepted());
        assertFalse(paused.isDone());
        assertTrue(coordinator.respond(response(request, "STOP")).accepted());
        assertEquals(ExecutionPauseCoordinator.Decision.STOP, paused.get(2, TimeUnit.SECONDS));
    }

    @Test
    void continueWaitsForTheWholePageScannerOperationToReleaseTheSharedBrowser() throws Exception {
        CompletableFuture<ExecutionPauseCoordinator.Decision> paused = startPause();
        ExecutionPauseCoordinator.PauseRequest request = publisher.awaitRequest();
        ExecutionPauseCoordinator.ScannerActivity scanner =
                coordinator.beginScannerActivity(42, request.workspaceEpoch());

        assertTrue(coordinator.respond(response(request, "CONTINUE")).accepted());
        Thread.sleep(100);
        assertFalse(paused.isDone(), "TEST RUN must remain paused while Page Scanner/OCR owns Playwright");

        scanner.close();
        assertEquals(ExecutionPauseCoordinator.Decision.CONTINUE, paused.get(2, TimeUnit.SECONDS));
    }

    @Test
    void executionStartAndScannerActivityCannotAcquireTheBrowserTogether() {
        try (ExecutionPauseCoordinator.ExecutionStart ignored = coordinator.reserveExecutionStart()) {
            assertThrows(
                    IllegalStateException.class,
                    () -> coordinator.beginScannerActivity(42, attempt.workspaceEpoch()));
        }
    }

    @Test
    void exactV1LocatorRecoveryCanInspectTheReservedSharedBrowser() {
        assertTrue(registry.finishTestRun(attempt, "PASSED"));
        try (ExecutionPauseCoordinator.ExecutionStart ignored = coordinator.reserveExecutionStart()) {
            SmokeRecoveryScannerRegistry.getInstance().register(
                    "recovery-run", RuntimeMode.JAVA_V1, 7, 42, attempt.workspaceEpoch());
            try (ExecutionPauseCoordinator.ScannerActivity scanner =
                         coordinator.beginScannerActivity(42, attempt.workspaceEpoch())) {
                assertTrue(coordinator.hasScannerActivity());
            }
        }
    }

    @Test
    void v2RecoveryAuthorityCannotInspectTheReservedV1SharedBrowser() {
        assertTrue(registry.finishTestRun(attempt, "PASSED"));
        try (ExecutionPauseCoordinator.ExecutionStart ignored = coordinator.reserveExecutionStart()) {
            SmokeRecoveryScannerRegistry.getInstance().register(
                    "recovery-run", RuntimeMode.TYPESCRIPT_PLAYWRIGHT_V2,
                    7, 42, attempt.workspaceEpoch());
            assertThrows(
                    IllegalStateException.class,
                    () -> coordinator.beginScannerActivity(42, attempt.workspaceEpoch()));
        }
    }

    @Test
    void scannerWaitsForAStoppingExecutionLeaseAndThenAcquiresTheBrowser() throws Exception {
        assertTrue(registry.finishTestRun(attempt, "INTERRUPTED"));
        ExecutionPauseCoordinator.ExecutionStart execution = coordinator.reserveExecutionStart();
        assertTrue(coordinator.requestExecutionRelease());

        CompletableFuture<ExecutionPauseCoordinator.ScannerActivity> scanner =
                CompletableFuture.supplyAsync(
                        () -> coordinator.beginScannerActivity(42, attempt.workspaceEpoch()));
        Thread.sleep(100);
        assertFalse(scanner.isDone());

        execution.close();
        try (ExecutionPauseCoordinator.ScannerActivity ignored = scanner.get(2, TimeUnit.SECONDS)) {
            assertTrue(coordinator.hasScannerActivity());
        }
        assertFalse(coordinator.hasScannerActivity());
    }

    @Test
    void disconnectedReactFailsSafeToStop() throws Exception {
        publisher.available = false;
        CompletableFuture<ExecutionPauseCoordinator.Decision> paused = startPause();
        publisher.awaitRequest();

        assertEquals(ExecutionPauseCoordinator.Decision.STOP, paused.get(2, TimeUnit.SECONDS));
    }

    private CompletableFuture<ExecutionPauseCoordinator.Decision> startPause() {
        return CompletableFuture.supplyAsync(() -> coordinator.pause(
                42, 101, "Login", "Review page", () -> false));
    }

    private static ExecutionPauseCoordinator.PauseResponse response(
            ExecutionPauseCoordinator.PauseRequest request, String decision) {
        return new ExecutionPauseCoordinator.PauseResponse(
                request.requestId(),
                request.botJobId(),
                request.workspaceEpoch(),
                request.executionId(),
                request.executionAttemptId(),
                decision);
    }

    private static final class FakePublisher implements ExecutionPauseCoordinator.Publisher {
        private final AtomicReference<ExecutionPauseCoordinator.PauseRequest> request = new AtomicReference<>();
        private final CountDownLatch published = new CountDownLatch(1);
        private volatile boolean available = true;

        @Override
        public boolean publish(ExecutionPauseCoordinator.PauseRequest request) {
            this.request.set(request);
            published.countDown();
            return true;
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        private ExecutionPauseCoordinator.PauseRequest awaitRequest() throws InterruptedException {
            assertTrue(published.await(Duration.ofSeconds(2).toMillis(), TimeUnit.MILLISECONDS));
            return request.get();
        }
    }
}

package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.BotJobToolbarContext;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class BotJobTestRunCoordinatorTest {

    private BotJobDetailsWorkspaceRegistry registry;
    private FakeScanner scanner;
    private QueuedExecutor monitors;
    private List<String> publications;
    private BotJobToolbarContext context;

    @BeforeEach
    void setUp() {
        registry = new BotJobDetailsWorkspaceRegistry();
        scanner = new FakeScanner();
        monitors = new QueuedExecutor();
        publications = new CopyOnWriteArrayList<>();
        BotJobLoadDTO botJob = botJob(42, "Payments");
        BotJobDetailsWorkspaceRegistry.Snapshot snapshot = registry.activate(botJob, true);
        context = context(snapshot, botJob);
    }

    @Test
    void acceptedRunPublishesOwnedPassedOutcome() {
        BotJobTestRunCoordinator coordinator = coordinator();

        BotJobTestRunCoordinator.StartResult result =
                coordinator.start(context, -1, false, "ALL", "Execute All", "start-1");

        assertTrue(result.accepted());
        assertEquals("RUNNING", state());
        monitors.runNext();
        assertEquals("PASSED", state());
        assertEquals(List.of(1L), scanner.terminalOutcomeExecutionIds);
        assertEquals(List.of("42:start-1", "42:test-run-complete"), publications);
    }

    @Test
    void naturalFailureRemainsFailed() {
        scanner.nextOutcome = "FAILED";
        BotJobTestRunCoordinator coordinator = coordinator();

        assertTrue(coordinator.start(context, 2, true, "ONE", "Payment", "start-2").accepted());
        monitors.runNext();

        assertEquals("FAILED", state());
        assertEquals(List.of(1L), scanner.terminalOutcomeExecutionIds);
    }

    @Test
    void stopAcceptedDuringSynchronousStartupIsForcedToInterrupted() {
        AtomicReference<BotJobTestRunCoordinator> coordinatorRef = new AtomicReference<>();
        scanner.stopAccepted = false;
        scanner.onStart = () -> {
            CompletableFuture<BotJobTestRunCoordinator.StopResult> stop =
                    coordinatorRef.get().requestStop(42);
            assertTrue(stop.join().accepted());
        };
        BotJobTestRunCoordinator coordinator = coordinator();
        coordinatorRef.set(coordinator);

        BotJobTestRunCoordinator.StartResult result =
                coordinator.start(context, -1, false, "ALL", "Execute All", "start-stop");

        assertFalse(result.accepted());
        assertTrue(result.message().contains("stopped during startup"));
        assertEquals(1, scanner.cancelStartupCalls.get());
        assertTrue(scanner.cancellationObserved);
        assertTrue(scanner.startedExecutionIds.isEmpty());
        assertTrue(scanner.stopExecutionIds.isEmpty());
        assertTrue(monitors.isEmpty());
        assertEquals("INTERRUPTED", state());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void blockedSynchronousStartupIsCancelledAndFinishesInterrupted() throws Exception {
        scanner.startupEntered = new CountDownLatch(1);
        scanner.startupRelease = new CountDownLatch(1);
        BotJobTestRunCoordinator coordinator = coordinator();
        CompletableFuture<BotJobTestRunCoordinator.StartResult> start = CompletableFuture.supplyAsync(
                () -> coordinator.start(
                        context, -1, false, "ALL", "Execute All", "blocked-start"));

        assertTrue(scanner.startupEntered.await(2, TimeUnit.SECONDS));
        assertEquals("STARTING", state());

        BotJobTestRunCoordinator.StopResult stop = coordinator.requestStop(42).join();
        BotJobTestRunCoordinator.StartResult result = start.get(2, TimeUnit.SECONDS);

        assertTrue(stop.accepted());
        assertFalse(result.accepted());
        assertTrue(result.message().contains("stopped during startup"));
        assertEquals(1, scanner.cancelStartupCalls.get());
        assertTrue(scanner.cancellationObserved);
        assertTrue(scanner.startedExecutionIds.isEmpty());
        assertTrue(scanner.stopExecutionIds.isEmpty());
        assertTrue(monitors.isEmpty());
        assertEquals("INTERRUPTED", state());
    }

    @Test
    void exactRunningStopPublishesInterrupted() {
        scanner.nextComplete = false;
        scanner.completeWhenStopped = true;
        BotJobTestRunCoordinator coordinator = coordinator();
        assertTrue(coordinator.start(context, 1, false, "ALL", "Login", "start-3").accepted());

        BotJobTestRunCoordinator.StopResult stop = coordinator.requestStop(42).join();
        monitors.runNext();

        assertTrue(stop.accepted());
        assertEquals(List.of(1L), scanner.stopExecutionIds);
        assertEquals("INTERRUPTED", state());
    }

    @Test
    void lateRejectedStopCannotRewriteNaturalPass() {
        scanner.stopAccepted = false;
        BotJobTestRunCoordinator coordinator = coordinator();
        assertTrue(coordinator.start(context, -1, false, "ALL", "Execute All", "start-4").accepted());

        assertTrue(coordinator.requestStop(42).join().accepted());
        monitors.runNext();

        assertEquals(List.of(1L), scanner.stopExecutionIds);
        assertEquals("PASSED", state());
    }

    @Test
    void rejectedScannerStartupFinishesFailedWithoutMonitor() {
        scanner.startAccepted = false;
        BotJobTestRunCoordinator coordinator = coordinator();

        BotJobTestRunCoordinator.StartResult result =
                coordinator.start(context, -1, false, "ALL", "Execute All", "rejected");

        assertFalse(result.accepted());
        assertEquals("FAILED", state());
        assertTrue(scanner.startedExecutionIds.isEmpty());
        assertTrue(monitors.isEmpty());
    }

    @Test
    void invalidExecutorOutcomeFailsClosedAndPublishesTerminalState() {
        scanner.nextOutcome = "RUNNING";
        BotJobTestRunCoordinator coordinator = coordinator();
        assertTrue(coordinator.start(context, -1, false, "ALL", "Execute All", "bad-outcome").accepted());

        monitors.runNext();

        assertEquals("FAILED", state());
        assertEquals("42:test-run-complete", publications.get(publications.size() - 1));
    }

    @Test
    void oldMonitorCannotFinishANewerAttempt() {
        BotJobTestRunCoordinator coordinator = coordinator();
        assertTrue(coordinator.start(context, -1, false, "ALL", "Execute All", "old").accepted());
        BotJobDetailsWorkspaceRegistry.Snapshot old = registry.require(42);
        BotJobDetailsWorkspaceRegistry.ExecutionAttempt oldAttempt =
                new BotJobDetailsWorkspaceRegistry.ExecutionAttempt(
                        42, old.workspaceEpoch(), old.executionAttemptId());
        assertTrue(registry.finishTestRun(oldAttempt, "PASSED"));

        scanner.nextOutcome = "FAILED";
        assertTrue(coordinator.start(context, -1, false, "ALL", "Execute All", "new").accepted());
        monitors.runNext();
        assertEquals("RUNNING", state());
        monitors.runNext();

        assertEquals("FAILED", state());
        assertEquals(List.of(1L, 2L), scanner.terminalOutcomeExecutionIds);
    }

    @Test
    void publisherFailureConcurrentWithStopFinishesInterrupted() {
        AtomicReference<BotJobTestRunCoordinator> coordinatorRef = new AtomicReference<>();
        AtomicReference<BotJobTestRunCoordinator.StopResult> stopResult = new AtomicReference<>();
        BotJobTestRunCoordinator.RuntimeStatePublisher failingPublisher = (botJobId, requestId) -> {
            if ("start-publisher-stop".equals(requestId)) {
                if (stopResult.get() == null) {
                    stopResult.set(coordinatorRef.get().requestStop(botJobId).join());
                }
                throw new IllegalStateException("STARTING publication failed");
            }
            publications.add(botJobId + ":" + requestId);
        };
        BotJobTestRunCoordinator coordinator = coordinator(
                failingPublisher, Runnable::run, monitors, 0L);
        coordinatorRef.set(coordinator);

        assertThrows(
                RuntimeException.class,
                () -> coordinator.start(
                        context, -1, false, "ALL", "Execute All", "start-publisher-stop"));

        assertTrue(stopResult.get().accepted());
        assertEquals("INTERRUPTED", state());
        assertTrue(scanner.startedExecutionIds.isEmpty());
        assertTrue(monitors.isEmpty());
    }

    @Test
    void rejectedStopExecutorUsesFallbackDelivery() {
        scanner.nextComplete = false;
        scanner.completeWhenStopped = true;
        Executor rejectingStopExecutor = command -> {
            throw new RejectedExecutionException("stop executor unavailable");
        };
        BotJobTestRunCoordinator coordinator = coordinator(
                recordingPublisher(), rejectingStopExecutor, monitors, 0L);
        assertTrue(coordinator.start(context, -1, false, "ALL", "Execute All", "stop-reject").accepted());

        BotJobTestRunCoordinator.StopResult stop = coordinator.requestStop(42).join();

        assertTrue(stop.accepted());
        assertEquals(List.of(1L), scanner.stopExecutionIds);
        monitors.runNext();

        assertEquals("INTERRUPTED", state());
        assertFalse(BotJobDetailsWorkspaceRegistry.isExecutionActive(state()));
    }

    @Test
    void failedStopDeliveryRestoresRunningSoTheUserCanRetry() {
        scanner.nextComplete = false;
        scanner.stopFailure = new IllegalStateException("scanner stop unavailable");
        BotJobTestRunCoordinator coordinator = coordinator();
        assertTrue(coordinator.start(context, -1, false, "ALL", "Execute All", "stop-fails").accepted());
        BotJobDetailsWorkspaceRegistry.Snapshot runningBeforeStop = registry.require(42);
        assertEquals("RUNNING", runningBeforeStop.executionState());

        BotJobTestRunCoordinator.StopResult firstStop = coordinator.requestStop(42).join();
        BotJobDetailsWorkspaceRegistry.Snapshot restored = registry.require(42);

        assertFalse(firstStop.accepted());
        assertEquals("RUNNING", restored.executionState());
        assertEquals(runningBeforeStop.workspaceEpoch(), restored.workspaceEpoch());
        assertEquals(runningBeforeStop.executionAttemptId(), restored.executionAttemptId());
        assertTrue(restored.revision() > runningBeforeStop.revision());

        scanner.stopFailure = null;
        scanner.completeWhenStopped = true;
        BotJobTestRunCoordinator.StopResult retry = coordinator.requestStop(42).join();
        monitors.runNext();

        assertTrue(retry.accepted());
        assertEquals(List.of(1L, 1L), scanner.stopExecutionIds);
        assertEquals("INTERRUPTED", state());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void rejectedMonitorExecutorUsesFallbackAndWaitsForActualCompletion() throws Exception {
        scanner.nextComplete = false;
        CountDownLatch fallbackProbe = new CountDownLatch(1);
        scanner.probeObserved = fallbackProbe;
        CountDownLatch terminalPublished = new CountDownLatch(1);
        BotJobTestRunCoordinator.RuntimeStatePublisher publisher = (botJobId, requestId) -> {
            publications.add(botJobId + ":" + requestId);
            if ("test-run-complete".equals(requestId)) {
                terminalPublished.countDown();
            }
        };
        Executor rejectingMonitorExecutor = command -> {
            throw new RejectedExecutionException("monitor executor unavailable");
        };
        BotJobTestRunCoordinator coordinator = coordinator(
                publisher, Runnable::run, rejectingMonitorExecutor, 1L);

        assertTrue(coordinator.start(context, -1, false, "ALL", "Execute All", "monitor-reject").accepted());
        assertTrue(fallbackProbe.await(2, TimeUnit.SECONDS));
        assertEquals("RUNNING", state());

        scanner.complete(1L, "PASSED");

        assertTrue(terminalPublished.await(2, TimeUnit.SECONDS));
        assertEquals("PASSED", state());
        assertEquals(List.of(1L), scanner.terminalOutcomeExecutionIds);
    }

    @Test
    void transientCompletionProbeFailureDoesNotTerminalizeEarly() {
        scanner.nextCompletionProbeFailures = 1;
        BotJobTestRunCoordinator coordinator = coordinator();
        assertTrue(coordinator.start(context, -1, false, "ALL", "Execute All", "probe-retry").accepted());

        monitors.runNext();

        assertEquals(2, scanner.execution(1L).completionProbeCalls.get());
        assertEquals("PASSED", state());
        assertEquals(List.of(1L), scanner.terminalOutcomeExecutionIds);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void terminalPublisherFailureIsRetried() throws Exception {
        AtomicInteger terminalAttempts = new AtomicInteger();
        CountDownLatch terminalPublished = new CountDownLatch(1);
        BotJobTestRunCoordinator.RuntimeStatePublisher publisher = (botJobId, requestId) -> {
            if ("test-run-complete".equals(requestId)) {
                if (terminalAttempts.incrementAndGet() == 1) {
                    throw new IllegalStateException("terminal publication failed once");
                }
                terminalPublished.countDown();
            }
            publications.add(botJobId + ":" + requestId);
        };
        BotJobTestRunCoordinator coordinator = coordinator(
                publisher, Runnable::run, monitors, 0L);
        assertTrue(coordinator.start(context, -1, false, "ALL", "Execute All", "publish-retry").accepted());

        monitors.runNext();

        assertTrue(terminalPublished.await(2, TimeUnit.SECONDS));
        assertEquals(2, terminalAttempts.get());
        assertEquals("PASSED", state());
        assertEquals(1, publications.stream().filter("42:test-run-complete"::equals).count());
    }

    private BotJobTestRunCoordinator coordinator() {
        return coordinator(recordingPublisher(), Runnable::run, monitors, 0L);
    }

    private BotJobTestRunCoordinator coordinator(
            BotJobTestRunCoordinator.RuntimeStatePublisher publisher,
            Executor stopExecutor,
            Executor monitorExecutor,
            long pollIntervalMillis) {
        return new BotJobTestRunCoordinator(
                registry,
                scanner,
                publisher,
                stopExecutor,
                monitorExecutor,
                pollIntervalMillis);
    }

    private BotJobTestRunCoordinator.RuntimeStatePublisher recordingPublisher() {
        return (botJobId, requestId) -> publications.add(botJobId + ":" + requestId);
    }

    private String state() {
        return registry.require(42).executionState();
    }

    private static BotJobToolbarContext context(
            BotJobDetailsWorkspaceRegistry.Snapshot snapshot, BotJobLoadDTO botJob) {
        return new BotJobToolbarContext(
                snapshot.workspaceEpoch(),
                botJob.getId(),
                botJob.getHomeBankingId(),
                botJob.getHomeUrlId(),
                botJob.getName(),
                botJob.getPriority(),
                "Test Bank",
                "https://test.example",
                true);
    }

    private static BotJobLoadDTO botJob(int id, String name) {
        BotJobLoadDTO botJob = new BotJobLoadDTO();
        botJob.setId(id);
        botJob.setBotJobId(id);
        botJob.setName(name);
        botJob.setPriority("Web App");
        botJob.setHomeBankingId(7);
        botJob.setHomeUrlId(8);
        botJob.setActive(true);
        return botJob;
    }

    private static final class QueuedExecutor implements Executor {
        private final Deque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.addLast(command);
        }

        void runNext() {
            tasks.removeFirst().run();
        }

        boolean isEmpty() {
            return tasks.isEmpty();
        }
    }

    private static final class FakeScanner implements BotJobTestRunCoordinator.ScannerPort {
        private final AtomicLong executionIds = new AtomicLong();
        private final Map<Long, FakeExecution> executions = new ConcurrentHashMap<>();
        private final List<Long> startedExecutionIds = new CopyOnWriteArrayList<>();
        private final List<Long> stopExecutionIds = new CopyOnWriteArrayList<>();
        private final List<Long> terminalOutcomeExecutionIds = new CopyOnWriteArrayList<>();
        private volatile boolean startAccepted = true;
        private volatile boolean stopAccepted = true;
        private volatile boolean nextComplete = true;
        private volatile boolean completeWhenStopped;
        private volatile RuntimeException stopFailure;
        private volatile String nextOutcome = "PASSED";
        private volatile int nextCompletionProbeFailures;
        private volatile CountDownLatch probeObserved;
        private volatile CountDownLatch startupEntered;
        private volatile CountDownLatch startupRelease;
        private final AtomicInteger cancelStartupCalls = new AtomicInteger();
        private volatile boolean cancellationObserved;
        private volatile Runnable onStart = () -> {};

        @Override
        public long start(
                BotJobLoadDTO botJob,
                int blockOrderNumber,
                String endpointUrl,
                boolean runSingleBlock,
                BooleanSupplier cancellationRequested) {
            onStart.run();
            CountDownLatch entered = startupEntered;
            if (entered != null) entered.countDown();
            CountDownLatch release = startupRelease;
            if (release != null) {
                try {
                    if (!release.await(2, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting for startup cancellation");
                    }
                } catch (InterruptedException interrupted) {
                    cancellationObserved = cancellationRequested.getAsBoolean();
                    return 0L;
                }
            }
            boolean cancelled = cancellationRequested.getAsBoolean();
            cancellationObserved = cancellationObserved || cancelled;
            if (!startAccepted || cancelled) return 0L;

            long executionId = executionIds.incrementAndGet();
            FakeExecution execution = new FakeExecution(
                    nextComplete, nextOutcome, nextCompletionProbeFailures);
            nextComplete = true;
            nextOutcome = "PASSED";
            nextCompletionProbeFailures = 0;
            executions.put(executionId, execution);
            startedExecutionIds.add(executionId);
            return executionId;
        }

        @Override
        public void cancelStartup() {
            cancelStartupCalls.incrementAndGet();
            CountDownLatch release = startupRelease;
            if (release != null) release.countDown();
        }

        @Override
        public boolean stop(long executionId) {
            FakeExecution execution = execution(executionId);
            stopExecutionIds.add(executionId);
            if (stopFailure != null) throw stopFailure;
            if (stopAccepted && completeWhenStopped) {
                execution.outcome = "INTERRUPTED";
                execution.complete = true;
            }
            return stopAccepted;
        }

        @Override
        public boolean isComplete(long executionId) {
            FakeExecution execution = execution(executionId);
            execution.completionProbeCalls.incrementAndGet();
            CountDownLatch observed = probeObserved;
            if (observed != null) observed.countDown();
            if (execution.remainingCompletionProbeFailures.getAndUpdate(value -> Math.max(0, value - 1)) > 0) {
                throw new IllegalStateException("transient completion probe failure");
            }
            return execution.complete;
        }

        @Override
        public String terminalOutcome(long executionId) {
            terminalOutcomeExecutionIds.add(executionId);
            return execution(executionId).outcome;
        }

        void complete(long executionId, String outcome) {
            FakeExecution execution = execution(executionId);
            execution.outcome = outcome;
            execution.complete = true;
        }

        FakeExecution execution(long executionId) {
            FakeExecution execution = executions.get(executionId);
            if (execution == null) {
                throw new AssertionError("Coordinator used unknown execution ID " + executionId);
            }
            return execution;
        }
    }

    private static final class FakeExecution {
        private volatile boolean complete;
        private volatile String outcome;
        private final AtomicInteger remainingCompletionProbeFailures;
        private final AtomicInteger completionProbeCalls = new AtomicInteger();

        private FakeExecution(boolean complete, String outcome, int completionProbeFailures) {
            this.complete = complete;
            this.outcome = outcome;
            this.remainingCompletionProbeFailures = new AtomicInteger(completionProbeFailures);
        }
    }
}

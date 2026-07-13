package com.allinweb.ch.facade;

import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.BotJobToolbarContext;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import lombok.extern.slf4j.Slf4j;

/**
 * JavaFX-free owner of the Bot Job Details TEST RUN lifecycle.
 *
 * <p>The coordinator binds START, prompt STOP, scanner execution IDs, terminal outcomes, workspace
 * epochs, and runtime-state publication. A stale completion can therefore never finish a newer
 * workspace or execution attempt.
 */
@Slf4j
public final class BotJobTestRunCoordinator {

    private static final Set<String> TERMINAL_STATES = Set.of("PASSED", "FAILED", "INTERRUPTED");
    private static final int TERMINAL_PUBLISH_ATTEMPTS = 3;

    private final BotJobDetailsWorkspaceRegistry registry;
    private final ScannerPort scanner;
    private final RuntimeStatePublisher publisher;
    private final Executor stopExecutor;
    private final Executor monitorExecutor;
    private final long pollIntervalMillis;
    private final AtomicReference<ActiveRun> activeRun = new AtomicReference<>();

    public BotJobTestRunCoordinator(
            BotJobDetailsWorkspaceRegistry registry,
            ScannerPort scanner,
            RuntimeStatePublisher publisher) {
        this(
                registry,
                scanner,
                publisher,
                defaultStopExecutor(),
                Executors.newCachedThreadPool(runnable -> daemonThread(runnable, "bot-job-test-run-monitor")),
                250L);
    }

    BotJobTestRunCoordinator(
            BotJobDetailsWorkspaceRegistry registry,
            ScannerPort scanner,
            RuntimeStatePublisher publisher,
            Executor stopExecutor,
            Executor monitorExecutor,
            long pollIntervalMillis) {
        if (registry == null || scanner == null || publisher == null || stopExecutor == null || monitorExecutor == null) {
            throw new IllegalArgumentException("TEST RUN coordinator dependencies are required");
        }
        if (pollIntervalMillis < 0) {
            throw new IllegalArgumentException("TEST RUN poll interval cannot be negative");
        }
        this.registry = registry;
        this.scanner = scanner;
        this.publisher = publisher;
        this.stopExecutor = stopExecutor;
        this.monitorExecutor = monitorExecutor;
        this.pollIntervalMillis = pollIntervalMillis;
    }

    public StartResult start(
            BotJobToolbarContext context,
            int blockOrderNumber,
            boolean runSingleBlock,
            String mode,
            String selectedBlockName,
            String requestId) {
        if (context == null) {
            throw new IllegalArgumentException("TEST RUN context is required");
        }
        String normalizedMode = safe(mode).toUpperCase(Locale.ROOT);
        if (!"ALL".equals(normalizedMode) && !"ONE".equals(normalizedMode)) {
            throw new IllegalArgumentException("Execution mode must be ALL or ONE");
        }

        BotJobDetailsWorkspaceRegistry.ExecutionAttempt attempt =
                registry.beginTestRun(context.botJobId(), context.workspaceEpoch());
        ActiveRun run = new ActiveRun(attempt);
        activeRun.set(run);
        if (isOwnedAttemptStopping(attempt)) {
            run.forceInterrupted();
            registry.finishTestRun(attempt, "INTERRUPTED");
            activeRun.compareAndSet(run, null);
            return StartResult.rejected("TEST RUN was stopped during startup");
        }
        try {
            publisher.publish(context.botJobId(), safe(requestId));
        } catch (RuntimeException error) {
            registry.finishTestRun(attempt, startupTerminalState(attempt));
            activeRun.compareAndSet(run, null);
            throw error;
        }
        if (run.isInterrupted() || isOwnedAttemptStopping(attempt)) {
            run.forceInterrupted();
            registry.finishTestRun(attempt, "INTERRUPTED");
            activeRun.compareAndSet(run, null);
            return StartResult.rejected("TEST RUN was stopped during startup");
        }

        final long scannerExecutionId;
        run.bindStartupThread(Thread.currentThread());
        try {
            scannerExecutionId = scanner.start(
                    context.executionBotJob(),
                    blockOrderNumber,
                    context.endpointUrl(),
                    runSingleBlock,
                    run::isInterrupted);
        } catch (RuntimeException error) {
            boolean stoppedDuringStartup = run.isInterrupted() || isOwnedAttemptStopping(attempt);
            registry.finishTestRun(attempt, stoppedDuringStartup ? "INTERRUPTED" : "FAILED");
            activeRun.compareAndSet(run, null);
            if (stoppedDuringStartup) {
                Thread.interrupted();
                return StartResult.rejected("TEST RUN was stopped during startup");
            }
            throw error;
        } finally {
            run.clearStartupThread();
        }
        if (scannerExecutionId <= 0) {
            boolean stoppedDuringStartup = run.isInterrupted() || isOwnedAttemptStopping(attempt);
            registry.finishTestRun(attempt, stoppedDuringStartup ? "INTERRUPTED" : "FAILED");
            activeRun.compareAndSet(run, null);
            if (stoppedDuringStartup) Thread.interrupted();
            return StartResult.rejected(stoppedDuringStartup
                    ? "TEST RUN was stopped during startup"
                    : "TEST RUN was not started; review the browser and job configuration");
        }

        run.bindScannerExecution(scannerExecutionId);
        if (!registry.markTestRunRunning(attempt)) {
            boolean stopAcceptedDuringStartup = run.isInterrupted() || isOwnedAttemptStopping(attempt);
            if (stopAcceptedDuringStartup) {
                run.forceInterrupted();
            }
            try {
                scanner.stop(scannerExecutionId);
            } catch (RuntimeException error) {
                log.warn("Unable to stop TEST RUN execution {} during startup: {}", scannerExecutionId, error.getMessage());
            }
            monitor(run);
            if (stopAcceptedDuringStartup) Thread.interrupted();
            return StartResult.rejected(stopAcceptedDuringStartup
                    ? "TEST RUN was stopped during startup"
                    : "TEST RUN workspace changed during startup");
        }

        monitor(run);
        return StartResult.accepted(
                "TEST RUN started in " + normalizedMode + " mode from " + safe(selectedBlockName));
    }

    public CompletableFuture<StopResult> requestStop(int botJobId) {
        CompletableFuture<StopResult> completion = new CompletableFuture<>();
        final BotJobDetailsWorkspaceRegistry.Snapshot snapshot;
        final BotJobDetailsWorkspaceRegistry.StopDecision decision;
        try {
            snapshot = registry.require(botJobId);
            decision = registry.requestTestRunStop(botJobId, snapshot.workspaceEpoch());
        } catch (RuntimeException error) {
            completion.complete(StopResult.rejected(message(error)));
            return completion;
        }
        if (!decision.accepted()) {
            completion.complete(StopResult.rejected("No TEST RUN is active"));
            return completion;
        }
        if (decision.alreadyRequested()) {
            completion.complete(StopResult.accepted("TEST RUN stop already requested"));
            return completion;
        }

        BotJobDetailsWorkspaceRegistry.ExecutionAttempt attempt =
                new BotJobDetailsWorkspaceRegistry.ExecutionAttempt(
                        botJobId, snapshot.workspaceEpoch(), decision.attemptId());
        try {
            stopExecutor.execute(() -> stopOwnedRun(attempt, decision.previousState(), completion));
        } catch (RejectedExecutionException rejected) {
            log.warn("Primary TEST RUN stop executor rejected attempt {}; using fallback", attempt.attemptId());
            startFallbackStop(attempt, decision.previousState(), completion);
        }
        return completion;
    }

    private void stopOwnedRun(
            BotJobDetailsWorkspaceRegistry.ExecutionAttempt attempt,
            String previousState,
            CompletableFuture<StopResult> completion) {
        try {
            registry.require(attempt.botJobId(), attempt.workspaceEpoch());
            ActiveRun run = activeRun.get();
            if (run != null && run.attempt().equals(attempt)) {
                long scannerExecutionId = run.scannerExecutionId();
                if (scannerExecutionId <= 0L) {
                    run.forceInterrupted();
                    try {
                        scanner.cancelStartup();
                    } catch (RuntimeException error) {
                        log.warn(
                                "TEST RUN startup cancellation hook failed for attempt {}; interrupting its owner thread: {}",
                                attempt.attemptId(),
                                message(error));
                    }
                    run.interruptStartupThread();
                } else if (scanner.stop(scannerExecutionId)) {
                    run.forceInterrupted();
                } else if (!scanner.isComplete(scannerExecutionId)) {
                    registry.restoreTestRunAfterStopFailure(attempt, previousState);
                    completion.complete(StopResult.rejected("TEST RUN executor did not accept the stop request"));
                    return;
                }
            }
            completion.complete(StopResult.accepted("TEST RUN stop requested"));
        } catch (RuntimeException error) {
            registry.restoreTestRunAfterStopFailure(attempt, previousState);
            completion.complete(StopResult.rejected(message(error)));
        }
    }

    private void startFallbackStop(
            BotJobDetailsWorkspaceRegistry.ExecutionAttempt attempt,
            String previousState,
            CompletableFuture<StopResult> completion) {
        try {
            daemonThread(
                            () -> stopOwnedRun(attempt, previousState, completion),
                            "bot-job-test-run-stop-fallback")
                    .start();
        } catch (RuntimeException threadStartFailure) {
            log.error(
                    "Unable to start fallback STOP delivery for TEST RUN attempt {}; delivering on the caller thread",
                    attempt.attemptId(),
                    threadStartFailure);
            stopOwnedRun(attempt, previousState, completion);
        }
    }

    private void monitor(ActiveRun run) {
        try {
            monitorExecutor.execute(() -> awaitCompletion(run));
        } catch (RejectedExecutionException rejected) {
            log.warn(
                    "Primary TEST RUN monitor rejected execution {}; using a dedicated fallback monitor",
                    run.scannerExecutionId());
            startFallbackMonitor(run);
        }
    }

    private void awaitCompletion(ActiveRun run) {
        try {
            while (!executionComplete(run)) {
                pauseBeforeNextProbe(run.scannerExecutionId());
            }
            String terminalState = run.isInterrupted()
                    ? "INTERRUPTED"
                    : normalizeTerminalState(scanner.terminalOutcome(run.scannerExecutionId()));
            finishAndPublish(run.attempt(), terminalState);
        } catch (RuntimeException error) {
            log.error(
                    "Unable to resolve TEST RUN terminal state for execution {}",
                    run.scannerExecutionId(),
                    error);
            finishAndPublish(run.attempt(), "FAILED");
        } finally {
            activeRun.compareAndSet(run, null);
        }
    }

    private boolean executionComplete(ActiveRun run) {
        while (true) {
            try {
                return scanner.isComplete(run.scannerExecutionId());
            } catch (RuntimeException error) {
                log.warn(
                        "Unable to probe TEST RUN execution {} completion; retrying: {}",
                        run.scannerExecutionId(),
                        message(error));
                pauseBeforeNextProbe(run.scannerExecutionId());
            }
        }
    }

    private void pauseBeforeNextProbe(long scannerExecutionId) {
        if (pollIntervalMillis == 0L) {
            Thread.yield();
            return;
        }
        try {
            Thread.sleep(pollIntervalMillis);
        } catch (InterruptedException interrupted) {
            log.warn(
                    "TEST RUN monitor for execution {} was interrupted; ownership monitoring will continue",
                    scannerExecutionId);
        }
    }

    private void startFallbackMonitor(ActiveRun run) {
        try {
            daemonThread(() -> awaitCompletion(run), "bot-job-test-run-monitor-fallback").start();
        } catch (RuntimeException threadStartFailure) {
            log.error(
                    "Unable to start fallback monitor for TEST RUN execution {}; monitoring on the caller thread",
                    run.scannerExecutionId(),
                    threadStartFailure);
            awaitCompletion(run);
        }
    }

    private void finishAndPublish(
            BotJobDetailsWorkspaceRegistry.ExecutionAttempt attempt, String terminalState) {
        if (registry.finishTestRun(attempt, terminalState)) {
            for (int publishAttempt = 1; publishAttempt <= TERMINAL_PUBLISH_ATTEMPTS; publishAttempt++) {
                try {
                    publisher.publish(attempt.botJobId(), "test-run-complete");
                    return;
                } catch (RuntimeException error) {
                    log.warn(
                            "Unable to publish TEST RUN terminal state for Bot Job {} (attempt {}/{}): {}",
                            attempt.botJobId(),
                            publishAttempt,
                            TERMINAL_PUBLISH_ATTEMPTS,
                            message(error));
                }
            }
        }
    }

    private String startupTerminalState(BotJobDetailsWorkspaceRegistry.ExecutionAttempt attempt) {
        return isOwnedAttemptStopping(attempt) ? "INTERRUPTED" : "FAILED";
    }

    private boolean isOwnedAttemptStopping(BotJobDetailsWorkspaceRegistry.ExecutionAttempt attempt) {
        try {
            BotJobDetailsWorkspaceRegistry.Snapshot snapshot =
                    registry.require(attempt.botJobId(), attempt.workspaceEpoch());
            return snapshot.executionAttemptId() == attempt.attemptId()
                    && "STOPPING".equals(snapshot.executionState());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static String normalizeTerminalState(String terminalState) {
        String normalized = safe(terminalState).toUpperCase(Locale.ROOT);
        if (!TERMINAL_STATES.contains(normalized)) {
            throw new IllegalStateException("Unsupported TEST RUN terminal state: " + terminalState);
        }
        return normalized;
    }

    private static ExecutorService defaultStopExecutor() {
        return new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1),
                runnable -> daemonThread(runnable, "bot-job-test-run-stop"),
                new ThreadPoolExecutor.AbortPolicy());
    }

    private static Thread daemonThread(Runnable runnable, String name) {
        Thread worker = new Thread(runnable, name);
        worker.setDaemon(true);
        return worker;
    }

    private static String message(Throwable error) {
        return error == null || safe(error.getMessage()).isEmpty()
                ? "Bot Job TEST RUN operation failed"
                : error.getMessage();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class ActiveRun {
        private final BotJobDetailsWorkspaceRegistry.ExecutionAttempt attempt;
        private final AtomicLong scannerExecutionId = new AtomicLong();
        private final AtomicBoolean interruptionRequested = new AtomicBoolean(false);
        private final AtomicReference<Thread> startupThread = new AtomicReference<>();

        private ActiveRun(BotJobDetailsWorkspaceRegistry.ExecutionAttempt attempt) {
            this.attempt = attempt;
        }

        private BotJobDetailsWorkspaceRegistry.ExecutionAttempt attempt() {
            return attempt;
        }

        private long scannerExecutionId() {
            return scannerExecutionId.get();
        }

        private void bindScannerExecution(long executionId) {
            if (executionId <= 0L || !scannerExecutionId.compareAndSet(0L, executionId)) {
                throw new IllegalStateException("TEST RUN scanner execution can only be bound once");
            }
        }

        private void bindStartupThread(Thread thread) {
            startupThread.set(thread);
        }

        private void clearStartupThread() {
            startupThread.set(null);
        }

        private void interruptStartupThread() {
            Thread thread = startupThread.get();
            if (thread != null) thread.interrupt();
        }

        private void forceInterrupted() {
            interruptionRequested.set(true);
        }

        private boolean isInterrupted() {
            return interruptionRequested.get();
        }
    }

    public interface ScannerPort {
        long start(
                BotJobLoadDTO botJob,
                int blockOrderNumber,
                String endpointUrl,
                boolean runSingleBlock,
                BooleanSupplier cancellationRequested);

        void cancelStartup();

        boolean stop(long executionId);

        boolean isComplete(long executionId);

        String terminalOutcome(long executionId);
    }

    @FunctionalInterface
    public interface RuntimeStatePublisher {
        void publish(int botJobId, String requestId);
    }

    public record StartResult(boolean accepted, String message) {
        static StartResult accepted(String message) {
            return new StartResult(true, safe(message));
        }

        static StartResult rejected(String message) {
            return new StartResult(false, safe(message));
        }
    }

    public record StopResult(boolean accepted, String message) {
        static StopResult accepted(String message) {
            return new StopResult(true, safe(message));
        }

        static StopResult rejected(String message) {
            return new StopResult(false, safe(message));
        }
    }
}

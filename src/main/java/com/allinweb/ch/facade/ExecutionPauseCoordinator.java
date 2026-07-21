package com.allinweb.ch.facade;

import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.allinweb.ch.socket.WebSocketSessionManager;
import com.google.gson.Gson;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import lombok.extern.slf4j.Slf4j;

/**
 * Owns the one instruction-level PAUSE that can exist in the process-wide Playwright execution.
 *
 * <p>The execution worker publishes a correlated request to the active Bot Job Details React
 * workspace and waits here. React answers with the same job, workspace, execution, attempt, and
 * request identities. Stale tabs and stale runs therefore cannot resume a newer execution.
 */
@Slf4j
public final class ExecutionPauseCoordinator {

    public enum Decision {
        CONTINUE,
        STOP
    }

    private static final long AVAILABILITY_POLL_MILLIS = 250L;
    private static final String REQUEST_OPERATION = "botJobExecution.pause.request";
    private static final ExecutionPauseCoordinator INSTANCE = new ExecutionPauseCoordinator(
            BotJobDetailsWorkspaceRegistry.getInstance(),
            new WebSocketPublisher(WebSocketSessionManager.getInstance(), new Gson()));

    private final BotJobDetailsWorkspaceRegistry workspaces;
    private final Publisher publisher;
    private final AtomicReference<PendingPause> pending = new AtomicReference<>();
    private final Object scannerActivityLock = new Object();
    private int scannerActivities;
    private boolean executionStartReserved;

    ExecutionPauseCoordinator(BotJobDetailsWorkspaceRegistry workspaces, Publisher publisher) {
        this.workspaces = Objects.requireNonNull(workspaces, "workspaces");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
    }

    public static ExecutionPauseCoordinator getInstance() {
        return INSTANCE;
    }

    /**
     * Publishes PAUSE and blocks only the execution worker until React selects Continue or Stop.
     * Missing/disconnected UI, cancellation, interruption, or a retired workspace fail safely to
     * STOP; none of those conditions may silently continue a Bot Job.
     */
    public Decision pause(
            int botJobId,
            long executionId,
            String blockName,
            String instructionName,
            BooleanSupplier cancellationRequested) {
        if (botJobId <= 0) throw new IllegalArgumentException("A positive Bot Job ID is required for PAUSE");
        if (executionId <= 0) throw new IllegalArgumentException("A positive execution ID is required for PAUSE");

        BotJobDetailsWorkspaceRegistry.Snapshot snapshot = workspaces.require(botJobId);
        PauseRequest request = new PauseRequest(
                UUID.randomUUID().toString(),
                botJobId,
                snapshot.workspaceEpoch(),
                executionId,
                snapshot.executionAttemptId(),
                "PAUSE BOT JOB",
                "Paused at block",
                safe(blockName),
                safe(instructionName),
                "The same Playwright page remains open. You can use Page Scanner before continuing.",
                "Continue",
                "Stop Run");
        PendingPause candidate = new PendingPause(request);
        if (!pending.compareAndSet(null, candidate)) {
            log.error("Rejected overlapping PAUSE for Bot Job {} execution {}", botJobId, executionId);
            return Decision.STOP;
        }

        try {
            if (!publisher.publish(request)) {
                log.error("React PAUSE confirmation is unavailable for Bot Job {}", botJobId);
                return Decision.STOP;
            }
            candidate.markPublished();
            BooleanSupplier cancellation = cancellationRequested == null ? () -> false : cancellationRequested;
            Decision decision = awaitDecision(candidate, cancellation);
            return awaitScannerIdle(candidate, decision, cancellation);
        } finally {
            pending.compareAndSet(candidate, null);
        }
    }

    public ResponseResult respond(PauseResponse response) {
        if (response == null) return ResponseResult.rejected("PAUSE response is required");
        PendingPause active = pending.get();
        if (active == null) return ResponseResult.rejected("No PAUSE confirmation is pending");
        if (!active.request.matches(response)) {
            return ResponseResult.rejected("PAUSE response does not match the active execution");
        }
        if (!bindingIsCurrent(active.request)) {
            closeScannerAdmissionAndComplete(active, Decision.STOP);
            return ResponseResult.rejected("PAUSE execution is no longer active");
        }
        Decision decision;
        try {
            decision = Decision.valueOf(safe(response.decision()).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalidDecision) {
            return ResponseResult.rejected("PAUSE decision must be CONTINUE or STOP");
        }
        if (!closeScannerAdmissionAndComplete(active, decision)) {
            return ResponseResult.rejected("PAUSE confirmation was already resolved");
        }
        return ResponseResult.accepted(decision);
    }

    /** True only while this exact Bot Job owns a published, unresolved PAUSE. */
    public boolean isPaused(int botJobId, long workspaceEpoch) {
        PendingPause active = pending.get();
        return active != null
                && active.published
                && active.acceptingScannerActivities
                && !active.decision.isDone()
                && active.request.botJobId() == botJobId
                && active.request.workspaceEpoch() == workspaceEpoch;
    }

    /** Releases a blocked PAUSE when toolbar STOP closes the active execution browser. */
    public boolean cancelExecution(long executionId) {
        PendingPause active = pending.get();
        if (active == null || active.request.executionId() != executionId) return false;
        synchronized (scannerActivityLock) {
            active.acceptingScannerActivities = false;
        }
        return active.complete(Decision.STOP);
    }

    public void cancelAll() {
        PendingPause active = pending.get();
        if (active != null) {
            synchronized (scannerActivityLock) {
                active.acceptingScannerActivities = false;
            }
            active.complete(Decision.STOP);
        }
    }

    /**
     * Acquires a whole-operation lease for Page Scanner/OCR work on the shared browser.
     * During an active TEST RUN the lease exists only while that exact execution is paused.
     */
    public ScannerActivity beginScannerActivity(int botJobId, long workspaceEpoch) {
        BotJobDetailsWorkspaceRegistry.Snapshot snapshot = workspaces.require(botJobId, workspaceEpoch);
        synchronized (scannerActivityLock) {
            if (executionStartReserved) {
                throw new IllegalStateException("TEST RUN is taking ownership of the Playwright browser");
            }
            if (BotJobDetailsWorkspaceRegistry.isExecutionActive(snapshot.executionState())) {
                PendingPause active = pending.get();
                if (active == null
                        || !active.published
                        || !active.acceptingScannerActivities
                        || active.decision.isDone()
                        || active.request.botJobId() != botJobId
                        || active.request.workspaceEpoch() != workspaceEpoch) {
                    throw new IllegalStateException(
                            "Page Scanner can use the active Playwright page when TEST RUN is paused");
                }
            }
            scannerActivities++;
            return new ScannerActivity(this);
        }
    }

    public boolean hasScannerActivity() {
        synchronized (scannerActivityLock) {
            return scannerActivities > 0;
        }
    }

    /** Atomically prevents a new Page Scanner task from starting while TEST RUN is being bound. */
    public ExecutionStart reserveExecutionStart() {
        synchronized (scannerActivityLock) {
            if (executionStartReserved) {
                throw new IllegalStateException("A TEST RUN is already taking ownership of Playwright");
            }
            if (scannerActivities > 0) {
                throw new IllegalStateException("Wait for the current Page Scanner operation to finish");
            }
            executionStartReserved = true;
            return new ExecutionStart(this);
        }
    }

    /** STOP must not close Playwright in the middle of a scanner/OCR operation. */
    public void awaitScannerIdle(long executionId) {
        PendingPause active = pending.get();
        if (active != null && active.request.executionId() == executionId) {
            awaitScannerIdle(active, Decision.STOP, () -> false);
        }
    }

    private Decision awaitDecision(PendingPause active, BooleanSupplier cancellationRequested) {
        while (true) {
            if (cancellationRequested.getAsBoolean()
                    || !publisher.isAvailable()
                    || !bindingIsCurrent(active.request)) {
                return Decision.STOP;
            }
            try {
                return active.decision.get(AVAILABILITY_POLL_MILLIS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException ignored) {
                // Recheck cancellation, workspace ownership, and the React transport.
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return Decision.STOP;
            } catch (ExecutionException failure) {
                log.error("Unable to resolve PAUSE confirmation", failure.getCause());
                return Decision.STOP;
            }
        }
    }

    private Decision awaitScannerIdle(
            PendingPause active, Decision requestedDecision, BooleanSupplier cancellationRequested) {
        Decision decision = requestedDecision;
        synchronized (scannerActivityLock) {
            active.acceptingScannerActivities = false;
            while (scannerActivities > 0) {
                if (cancellationRequested.getAsBoolean()) decision = Decision.STOP;
                try {
                    scannerActivityLock.wait(AVAILABILITY_POLL_MILLIS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    decision = Decision.STOP;
                }
            }
        }
        return decision;
    }

    private void finishScannerActivity() {
        synchronized (scannerActivityLock) {
            if (scannerActivities <= 0) {
                throw new IllegalStateException("Page Scanner activity lease is not active");
            }
            scannerActivities--;
            scannerActivityLock.notifyAll();
        }
    }

    private void releaseExecutionStart() {
        synchronized (scannerActivityLock) {
            executionStartReserved = false;
            scannerActivityLock.notifyAll();
        }
    }

    private boolean closeScannerAdmissionAndComplete(PendingPause active, Decision decision) {
        synchronized (scannerActivityLock) {
            active.acceptingScannerActivities = false;
            return active.complete(decision);
        }
    }

    private boolean bindingIsCurrent(PauseRequest request) {
        try {
            BotJobDetailsWorkspaceRegistry.Snapshot snapshot =
                    workspaces.require(request.botJobId(), request.workspaceEpoch());
            return request.executionAttemptId() <= 0
                    || snapshot.executionAttemptId() == request.executionAttemptId();
        } catch (RuntimeException retired) {
            return false;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public record PauseRequest(
            String requestId,
            int botJobId,
            long workspaceEpoch,
            long executionId,
            long executionAttemptId,
            String title,
            String header,
            String blockName,
            String instructionName,
            String body,
            String continueLabel,
            String stopLabel) {
        private boolean matches(PauseResponse response) {
            return requestId.equals(response.requestId())
                    && botJobId == response.botJobId()
                    && workspaceEpoch == response.workspaceEpoch()
                    && executionId == response.executionId()
                    && executionAttemptId == response.executionAttemptId();
        }
    }

    public record PauseResponse(
            String requestId,
            int botJobId,
            long workspaceEpoch,
            long executionId,
            long executionAttemptId,
            String decision) {}

    public record ResponseResult(boolean accepted, Decision decision, String message) {
        private static ResponseResult accepted(Decision decision) {
            return new ResponseResult(true, decision, "PAUSE " + decision.name().toLowerCase(Locale.ROOT) + " accepted");
        }

        private static ResponseResult rejected(String message) {
            return new ResponseResult(false, null, message);
        }
    }

    public static final class ScannerActivity implements AutoCloseable {
        private final ExecutionPauseCoordinator owner;
        private boolean closed;

        private ScannerActivity(ExecutionPauseCoordinator owner) {
            this.owner = owner;
        }

        @Override
        public void close() {
            synchronized (this) {
                if (closed) return;
                closed = true;
            }
            owner.finishScannerActivity();
        }
    }

    public static final class ExecutionStart implements AutoCloseable {
        private final ExecutionPauseCoordinator owner;
        private boolean closed;

        private ExecutionStart(ExecutionPauseCoordinator owner) {
            this.owner = owner;
        }

        @Override
        public void close() {
            synchronized (this) {
                if (closed) return;
                closed = true;
            }
            owner.releaseExecutionStart();
        }
    }

    interface Publisher {
        boolean publish(PauseRequest request);

        boolean isAvailable();
    }

    private static final class WebSocketPublisher implements Publisher {
        private final WebSocketSessionManager sessions;
        private final Gson gson;

        private WebSocketPublisher(WebSocketSessionManager sessions, Gson gson) {
            this.sessions = sessions;
            this.gson = gson;
        }

        @Override
        public boolean publish(PauseRequest request) {
            return sessions.sendMessageJson(
                            -1,
                            ScannerWorkspaceSessions.BOT_JOB_TASKS,
                            gson.toJson(request),
                            REQUEST_OPERATION)
                    != null;
        }

        @Override
        public boolean isAvailable() {
            return WebSocketSessionManager.isSessionOpen(ScannerWorkspaceSessions.BOT_JOB_TASKS);
        }
    }

    private static final class PendingPause {
        private final PauseRequest request;
        private final CompletableFuture<Decision> decision = new CompletableFuture<>();
        private volatile boolean published;
        private volatile boolean acceptingScannerActivities = true;

        private PendingPause(PauseRequest request) {
            this.request = request;
        }

        private void markPublished() {
            published = true;
        }

        private boolean complete(Decision value) {
            return decision.complete(value);
        }
    }
}

package com.allinweb.ch.facade;

import com.allinweb.ch.model.BotJobLoadDTO;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** UI-agnostic registry for the single Bot Job Details workspace owned by the desktop shell. */
public final class BotJobDetailsWorkspaceRegistry {

    private static final BotJobDetailsWorkspaceRegistry INSTANCE = new BotJobDetailsWorkspaceRegistry();

    private final AtomicLong revisions = new AtomicLong();
    private final AtomicLong workspaceEpochs = new AtomicLong();
    private final AtomicLong executionAttempts = new AtomicLong();
    private final AtomicReference<Snapshot> active = new AtomicReference<>();

    BotJobDetailsWorkspaceRegistry() {}

    public static BotJobDetailsWorkspaceRegistry getInstance() {
        return INSTANCE;
    }

    public synchronized Snapshot activate(BotJobLoadDTO botJob, boolean licenseGuardEnabled) {
        if (botJob == null || botJob.getId() == null || botJob.getId() <= 0) {
            throw new IllegalArgumentException("An active Bot Job is required");
        }
        String organizationName = botJob.getHomeBankingLoadDTO() == null
                ? ""
                : safe(botJob.getHomeBankingLoadDTO().getName());
        Snapshot next = new Snapshot(
                revisions.incrementAndGet(),
                1,
                workspaceEpochs.incrementAndGet(),
                botJob.getId(),
                safe(botJob.getName()),
                safe(botJob.getDescription()),
                safe(botJob.getPriority()),
                value(botJob.getHomeBankingId()),
                organizationName,
                value(botJob.getHomeUrlId()),
                licenseGuardEnabled,
                true,
                "botJob",
                false,
                "IDLE",
                0);
        active.set(next);
        return next;
    }

    public synchronized Snapshot require(int botJobId) {
        Snapshot snapshot = active.get();
        if (snapshot == null || !snapshot.open() || snapshot.botJobId() != botJobId) {
            throw new IllegalArgumentException("Bot Job Details request does not match the active Bot Job");
        }
        return snapshot;
    }

    /**
     * Requires the organization currently owned by the single Bot Job Details workspace.
     *
     * <p>Reusable Components are organization-scoped and may carry a synthetic Bot Job id. Their
     * authorization boundary is therefore the active organization, not the submitted Bot Job id.
     */
    public synchronized Snapshot requireHomeBanking(int homeBankingId) {
        Snapshot snapshot = active.get();
        if (snapshot == null
                || !snapshot.open()
                || homeBankingId <= 0
                || snapshot.homeBankingId() != homeBankingId) {
            throw new IllegalArgumentException(
                    "Components do not match the active Bot Job Details organization");
        }
        return snapshot;
    }

    public synchronized Snapshot require(int botJobId, long workspaceEpoch) {
        Snapshot snapshot = require(botJobId);
        if (snapshot.workspaceEpoch() != workspaceEpoch) {
            throw new IllegalArgumentException("Bot Job Details toolbar context is no longer active");
        }
        return snapshot;
    }

    /**
     * Keeps the persisted metadata mutation and its workspace revision transition under the same
     * registry lock. Workspace actions and close/activate cannot advance the active snapshot
     * between the optimistic revision check and a successful database commit.
     *
     * <p>A non-null value returned by {@code persistence} represents a persistence failure and
     * leaves the active snapshot unchanged.
     */
    public synchronized <T> MetadataCommit<T> commitMetadata(
            int botJobId,
            long expectedRevision,
            String name,
            String description,
            int homeUrlId,
            Supplier<T> persistence) {
        Snapshot current = require(botJobId);
        if (current.metadataRevision() != expectedRevision) {
            throw new RevisionConflictException(
                    "Bot Job Details changed; review the latest values before saving");
        }
        if (persistence == null) {
            throw new IllegalArgumentException("Bot Job Details persistence operation is required");
        }

        T persistenceError = persistence.get();
        if (persistenceError != null) {
            return new MetadataCommit<>(false, current, persistenceError);
        }

        Snapshot next = new Snapshot(
                revisions.incrementAndGet(),
                current.metadataRevision() + 1,
                current.workspaceEpoch(),
                current.botJobId(),
                safe(name),
                safe(description),
                current.projectType(),
                current.homeBankingId(),
                current.organizationName(),
                homeUrlId,
                current.licenseGuardEnabled(),
                current.open(),
                current.activeSurface(),
                current.componentsVisible(),
                current.executionState(),
                current.executionAttemptId());
        active.set(next);
        return new MetadataCommit<>(true, next, null);
    }

    /**
     * Runs a workspace-bound mutation while holding the same lifecycle lock used by activate and
     * close. A Bot Job switch therefore cannot invalidate the epoch halfway through a detached
     * Page Scanner database commit.
     */
    public synchronized <T> T commitWorkspaceMutation(
            int botJobId, long workspaceEpoch, Supplier<T> mutation) {
        require(botJobId, workspaceEpoch);
        if (mutation == null) {
            throw new IllegalArgumentException("Bot Job Details mutation operation is required");
        }
        return mutation.get();
    }

    public synchronized Snapshot environmentsChanged(int botJobId) {
        Snapshot current = require(botJobId);
        Snapshot next = copyWithRevision(current, revisions.incrementAndGet());
        active.set(next);
        return next;
    }

    /** Advances the public state revision after a native toolbar action changes runtime/config state. */
    public synchronized Snapshot touch(int botJobId) {
        Snapshot current = require(botJobId);
        Snapshot next = copyWithRevision(current, revisions.incrementAndGet());
        active.set(next);
        return next;
    }

    public synchronized Snapshot updateWorkspace(int botJobId, String activeSurface, boolean componentsVisible) {
        Snapshot current = require(botJobId);
        Snapshot next = new Snapshot(
                revisions.incrementAndGet(),
                current.metadataRevision(),
                current.workspaceEpoch(),
                current.botJobId(),
                current.name(),
                current.description(),
                current.projectType(),
                current.homeBankingId(),
                current.organizationName(),
                current.homeUrlId(),
                current.licenseGuardEnabled(),
                current.open(),
                safe(activeSurface),
                componentsVisible,
                current.executionState(),
                current.executionAttemptId());
        active.set(next);
        return next;
    }

    public synchronized Snapshot updateExecutionState(int botJobId, String executionState) {
        Snapshot current = require(botJobId);
        String nextState = safe(executionState).toUpperCase(java.util.Locale.ROOT);
        if (nextState.isEmpty()) nextState = "UNKNOWN";
        if (nextState.equals(current.executionState())) return current;
        Snapshot next = new Snapshot(
                revisions.incrementAndGet(),
                current.metadataRevision(),
                current.workspaceEpoch(),
                current.botJobId(),
                current.name(),
                current.description(),
                current.projectType(),
                current.homeBankingId(),
                current.organizationName(),
                current.homeUrlId(),
                current.licenseGuardEnabled(),
                current.open(),
                current.activeSurface(),
                current.componentsVisible(),
                nextState,
                current.executionAttemptId());
        active.set(next);
        return next;
    }

    public synchronized ExecutionAttempt beginTestRun(int botJobId, long workspaceEpoch) {
        Snapshot current = require(botJobId, workspaceEpoch);
        if (isExecutionActive(current.executionState())) {
            throw new IllegalStateException("A TEST RUN is already active");
        }
        long attemptId = executionAttempts.incrementAndGet();
        Snapshot next = copyWithExecution(current, "STARTING", attemptId);
        active.set(next);
        return new ExecutionAttempt(botJobId, workspaceEpoch, attemptId);
    }

    public synchronized boolean markTestRunRunning(ExecutionAttempt attempt) {
        Snapshot current = active.get();
        if (!matches(current, attempt) || !"STARTING".equals(current.executionState())) return false;
        active.set(copyWithExecution(current, "RUNNING", attempt.attemptId()));
        return true;
    }

    public synchronized StopDecision requestTestRunStop(int botJobId, long workspaceEpoch) {
        Snapshot current = require(botJobId, workspaceEpoch);
        if ("STOPPING".equals(current.executionState())) {
            return new StopDecision(true, true, current.executionAttemptId(), current.executionState());
        }
        if (!"STARTING".equals(current.executionState()) && !"RUNNING".equals(current.executionState())) {
            return new StopDecision(false, false, current.executionAttemptId(), current.executionState());
        }
        String previousState = current.executionState();
        active.set(copyWithExecution(current, "STOPPING", current.executionAttemptId()));
        return new StopDecision(true, false, current.executionAttemptId(), previousState);
    }

    public synchronized boolean finishTestRun(ExecutionAttempt attempt, String terminalState) {
        Snapshot current = active.get();
        if (!matches(current, attempt) || !isExecutionActive(current.executionState())) return false;
        String normalized = safe(terminalState).toUpperCase(java.util.Locale.ROOT);
        if (!normalized.equals("PASSED") && !normalized.equals("FAILED") && !normalized.equals("INTERRUPTED")) {
            throw new IllegalArgumentException("Unsupported TEST RUN terminal state: " + terminalState);
        }
        active.set(copyWithExecution(current, normalized, attempt.attemptId()));
        return true;
    }

    /**
     * Releases an owned STOPPING transition when the stop command could not be delivered. This
     * makes STOP retryable without allowing a stale attempt to rewrite a newer execution.
     */
    public synchronized boolean restoreTestRunAfterStopFailure(
            ExecutionAttempt attempt, String previousState) {
        Snapshot current = active.get();
        String restored = safe(previousState).toUpperCase(java.util.Locale.ROOT);
        if (!matches(current, attempt)
                || !"STOPPING".equals(current.executionState())
                || (!"STARTING".equals(restored) && !"RUNNING".equals(restored))) {
            return false;
        }
        active.set(copyWithExecution(current, restored, attempt.attemptId()));
        return true;
    }

    public synchronized void close(int botJobId) {
        Snapshot current = active.get();
        if (current == null || current.botJobId() != botJobId || !current.open()) {
            return;
        }
        active.set(new Snapshot(
                revisions.incrementAndGet(),
                current.metadataRevision(),
                current.workspaceEpoch(),
                current.botJobId(),
                current.name(),
                current.description(),
                current.projectType(),
                current.homeBankingId(),
                current.organizationName(),
                current.homeUrlId(),
                current.licenseGuardEnabled(),
                false,
                current.activeSurface(),
                current.componentsVisible(),
                current.executionState(),
                current.executionAttemptId()));
    }

    private Snapshot copyWithRevision(Snapshot current, long revision) {
        return new Snapshot(
                revision,
                current.metadataRevision(),
                current.workspaceEpoch(),
                current.botJobId(),
                current.name(),
                current.description(),
                current.projectType(),
                current.homeBankingId(),
                current.organizationName(),
                current.homeUrlId(),
                current.licenseGuardEnabled(),
                current.open(),
                current.activeSurface(),
                current.componentsVisible(),
                current.executionState(),
                current.executionAttemptId());
    }

    private Snapshot copyWithExecution(Snapshot current, String state, long attemptId) {
        return new Snapshot(
                revisions.incrementAndGet(),
                current.metadataRevision(),
                current.workspaceEpoch(),
                current.botJobId(),
                current.name(),
                current.description(),
                current.projectType(),
                current.homeBankingId(),
                current.organizationName(),
                current.homeUrlId(),
                current.licenseGuardEnabled(),
                current.open(),
                current.activeSurface(),
                current.componentsVisible(),
                state,
                attemptId);
    }

    private static boolean matches(Snapshot snapshot, ExecutionAttempt attempt) {
        return snapshot != null
                && snapshot.open()
                && snapshot.botJobId() == attempt.botJobId()
                && snapshot.workspaceEpoch() == attempt.workspaceEpoch()
                && snapshot.executionAttemptId() == attempt.attemptId();
    }

    public static boolean isExecutionActive(String state) {
        return "STARTING".equals(state) || "RUNNING".equals(state) || "STOPPING".equals(state);
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public record Snapshot(
            long revision,
            long metadataRevision,
            long workspaceEpoch,
            int botJobId,
            String name,
            String description,
            String projectType,
            int homeBankingId,
            String organizationName,
            int homeUrlId,
            boolean licenseGuardEnabled,
            boolean open,
            String activeSurface,
            boolean componentsVisible,
            String executionState,
            long executionAttemptId) {}

    public record ExecutionAttempt(int botJobId, long workspaceEpoch, long attemptId) {}

    public record StopDecision(
            boolean accepted, boolean alreadyRequested, long attemptId, String previousState) {}

    public record MetadataCommit<T>(boolean committed, Snapshot snapshot, T persistenceError) {}

    public static final class RevisionConflictException extends IllegalStateException {
        public RevisionConflictException(String message) {
            super(message);
        }
    }
}

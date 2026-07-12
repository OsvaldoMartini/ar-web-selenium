package com.allinweb.ch.facade;

import com.allinweb.ch.model.BotJobLoadDTO;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** UI-agnostic registry for the single Bot Job Details workspace owned by the desktop shell. */
public final class BotJobDetailsWorkspaceRegistry {

    private static final BotJobDetailsWorkspaceRegistry INSTANCE = new BotJobDetailsWorkspaceRegistry();

    private final AtomicLong revisions = new AtomicLong();
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
                false);
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
        if (current.revision() != expectedRevision) {
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
                current.componentsVisible());
        active.set(next);
        return new MetadataCommit<>(true, next, null);
    }

    public synchronized Snapshot environmentsChanged(int botJobId) {
        Snapshot current = require(botJobId);
        Snapshot next = copyWithRevision(current, revisions.incrementAndGet());
        active.set(next);
        return next;
    }

    public synchronized Snapshot updateWorkspace(int botJobId, String activeSurface, boolean componentsVisible) {
        Snapshot current = require(botJobId);
        Snapshot next = new Snapshot(
                revisions.incrementAndGet(),
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
                componentsVisible);
        active.set(next);
        return next;
    }

    public synchronized void close(int botJobId) {
        Snapshot current = active.get();
        if (current == null || current.botJobId() != botJobId || !current.open()) {
            return;
        }
        active.set(new Snapshot(
                revisions.incrementAndGet(),
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
                current.componentsVisible()));
    }

    private Snapshot copyWithRevision(Snapshot current, long revision) {
        return new Snapshot(
                revision,
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
                current.componentsVisible());
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public record Snapshot(
            long revision,
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
            boolean componentsVisible) {}

    public record MetadataCommit<T>(boolean committed, Snapshot snapshot, T persistenceError) {}

    public static final class RevisionConflictException extends IllegalStateException {
        public RevisionConflictException(String message) {
            super(message);
        }
    }
}

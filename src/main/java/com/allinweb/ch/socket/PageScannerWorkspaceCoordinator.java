package com.allinweb.ch.socket;

import com.allinweb.ch.facade.BotJobWorkspaceController;
import com.allinweb.ch.facade.BotJobDetailsWorkspaceRegistry;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.google.gson.JsonObject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

/** Owns the trusted context and lifecycle of detached Page Scanner workspaces. */
@Slf4j
final class PageScannerWorkspaceCoordinator {

    static final Duration WORKSPACE_TTL = Duration.ofHours(4);
    static final Duration INITIAL_CONNECTION_GRACE = Duration.ofSeconds(15);
    static final Duration RECONNECT_GRACE = Duration.ofSeconds(2);
    static final String WORKSPACE_CLOSED_OPERATION = "pageScanner.workspaceClosed";
    static final String WORKSPACE_RETARGET_OPERATION = "pageScanner.workspaceRetarget";
    private static final int MAX_ID_ATTEMPTS = 16;
    private static final int MAX_ACTIVE_WORKSPACES = 1;
    private static final int MAX_BOT_JOB_NAME_LENGTH = 512;
    private static final int MAX_ENDPOINT_URL_LENGTH = 8_192;
    private static final int MAX_BROWSER_TYPE_LENGTH = 128;
    private static final int MAX_OPTIONS_CONFIG_LENGTH = 65_536;
    private static final int MAX_JSON_PATH_LENGTH = 4_096;

    private final WorkspaceLauncher launcher;
    private final Supplier<String> idSupplier;
    private final Clock clock;
    private final int maximumActiveWorkspaces;
    private final WorkspaceLifecycle workspaceLifecycle;
    private final WorkspaceConnectionProbe connectionProbe;
    private final Duration connectionGrace;
    private final WorkspaceInvalidationNotifier invalidationNotifier;
    private final WorkspaceRetargetNotifier retargetNotifier;
    private final Duration reconnectGrace;
    private final DeferredExecutor deferredExecutor;
    private final WorkspaceAuthorityValidator workspaceAuthorityValidator;
    private final Map<String, WorkspaceEntry> workspaces = new LinkedHashMap<>();
    private PendingOpen pendingOpen;

    private PageScannerWorkspaceCoordinator() {
        this(
                sessionId -> ARWebSocketServer.getInstance().openPageScannerDesktopShell(sessionId),
                () -> UUID.randomUUID().toString(),
                Clock.systemUTC(),
                MAX_ACTIVE_WORKSPACES,
                PageScannerWorkspaceCoordinator::releaseWorkspaceResources,
                WebSocketSessionManager::isSessionOpen,
                INITIAL_CONNECTION_GRACE,
                PageScannerWorkspaceCoordinator::publishWorkspaceClosed,
                PageScannerWorkspaceCoordinator::publishWorkspaceRetarget,
                RECONNECT_GRACE,
                (delay, task) -> CompletableFuture.delayedExecutor(
                                Math.max(1L, delay.toMillis()), TimeUnit.MILLISECONDS)
                        .execute(task),
                PageScannerWorkspaceCoordinator::requireActiveRegistryOwner);
    }

    PageScannerWorkspaceCoordinator(WorkspaceLauncher launcher, Supplier<String> idSupplier, Clock clock) {
        this(
                launcher,
                idSupplier,
                clock,
                MAX_ACTIVE_WORKSPACES,
                (context, reason) -> {},
                sessionId -> true,
                INITIAL_CONNECTION_GRACE);
    }

    PageScannerWorkspaceCoordinator(
            WorkspaceLauncher launcher,
            Supplier<String> idSupplier,
            Clock clock,
            int maximumActiveWorkspaces) {
        this(
                launcher,
                idSupplier,
                clock,
                maximumActiveWorkspaces,
                (context, reason) -> {},
                sessionId -> true,
                INITIAL_CONNECTION_GRACE);
    }

    PageScannerWorkspaceCoordinator(
            WorkspaceLauncher launcher,
            Supplier<String> idSupplier,
            Clock clock,
            int maximumActiveWorkspaces,
            WorkspaceLifecycle workspaceLifecycle) {
        this(
                launcher,
                idSupplier,
                clock,
                maximumActiveWorkspaces,
                workspaceLifecycle,
                sessionId -> true,
                INITIAL_CONNECTION_GRACE);
    }

    PageScannerWorkspaceCoordinator(
            WorkspaceLauncher launcher,
            Supplier<String> idSupplier,
            Clock clock,
            int maximumActiveWorkspaces,
            WorkspaceLifecycle workspaceLifecycle,
            WorkspaceConnectionProbe connectionProbe,
            Duration connectionGrace) {
        this(
                launcher,
                idSupplier,
                clock,
                maximumActiveWorkspaces,
                workspaceLifecycle,
                connectionProbe,
                connectionGrace,
                (context, reason, message) -> false);
    }

    PageScannerWorkspaceCoordinator(
            WorkspaceLauncher launcher,
            Supplier<String> idSupplier,
            Clock clock,
            int maximumActiveWorkspaces,
            WorkspaceLifecycle workspaceLifecycle,
            WorkspaceConnectionProbe connectionProbe,
            Duration connectionGrace,
            WorkspaceInvalidationNotifier invalidationNotifier) {
        this(
                launcher,
                idSupplier,
                clock,
                maximumActiveWorkspaces,
                workspaceLifecycle,
                connectionProbe,
                connectionGrace,
                invalidationNotifier,
                (previous, current, message) -> false);
    }

    PageScannerWorkspaceCoordinator(
            WorkspaceLauncher launcher,
            Supplier<String> idSupplier,
            Clock clock,
            int maximumActiveWorkspaces,
            WorkspaceLifecycle workspaceLifecycle,
            WorkspaceConnectionProbe connectionProbe,
            Duration connectionGrace,
            WorkspaceInvalidationNotifier invalidationNotifier,
            WorkspaceRetargetNotifier retargetNotifier) {
        this(
                launcher,
                idSupplier,
                clock,
                maximumActiveWorkspaces,
                workspaceLifecycle,
                connectionProbe,
                connectionGrace,
                invalidationNotifier,
                retargetNotifier,
                RECONNECT_GRACE,
                (delay, task) -> {});
    }

    PageScannerWorkspaceCoordinator(
            WorkspaceLauncher launcher,
            Supplier<String> idSupplier,
            Clock clock,
            int maximumActiveWorkspaces,
            WorkspaceLifecycle workspaceLifecycle,
            WorkspaceConnectionProbe connectionProbe,
            Duration connectionGrace,
            WorkspaceInvalidationNotifier invalidationNotifier,
            WorkspaceRetargetNotifier retargetNotifier,
            Duration reconnectGrace,
            DeferredExecutor deferredExecutor) {
        this(
                launcher,
                idSupplier,
                clock,
                maximumActiveWorkspaces,
                workspaceLifecycle,
                connectionProbe,
                connectionGrace,
                invalidationNotifier,
                retargetNotifier,
                reconnectGrace,
                deferredExecutor,
                context -> {});
    }

    private PageScannerWorkspaceCoordinator(
            WorkspaceLauncher launcher,
            Supplier<String> idSupplier,
            Clock clock,
            int maximumActiveWorkspaces,
            WorkspaceLifecycle workspaceLifecycle,
            WorkspaceConnectionProbe connectionProbe,
            Duration connectionGrace,
            WorkspaceInvalidationNotifier invalidationNotifier,
            WorkspaceRetargetNotifier retargetNotifier,
            Duration reconnectGrace,
            DeferredExecutor deferredExecutor,
            WorkspaceAuthorityValidator workspaceAuthorityValidator) {
        this.launcher = Objects.requireNonNull(launcher, "Page Scanner workspace launcher is required");
        this.idSupplier = Objects.requireNonNull(idSupplier, "Page Scanner workspace ID supplier is required");
        this.clock = Objects.requireNonNull(clock, "Page Scanner workspace clock is required");
        if (maximumActiveWorkspaces <= 0) {
            throw new IllegalArgumentException("Page Scanner workspace limit must be positive");
        }
        this.maximumActiveWorkspaces = maximumActiveWorkspaces;
        this.workspaceLifecycle = Objects.requireNonNull(
                workspaceLifecycle, "Page Scanner workspace lifecycle is required");
        this.connectionProbe = Objects.requireNonNull(
                connectionProbe, "Page Scanner workspace connection probe is required");
        this.connectionGrace = Objects.requireNonNull(
                connectionGrace, "Page Scanner workspace connection grace is required");
        if (connectionGrace.isZero() || connectionGrace.isNegative()) {
            throw new IllegalArgumentException("Page Scanner workspace connection grace must be positive");
        }
        this.invalidationNotifier = Objects.requireNonNull(
                invalidationNotifier, "Page Scanner workspace invalidation notifier is required");
        this.retargetNotifier = Objects.requireNonNull(
                retargetNotifier, "Page Scanner workspace retarget notifier is required");
        this.reconnectGrace = Objects.requireNonNull(
                reconnectGrace, "Page Scanner reconnect grace is required");
        if (reconnectGrace.isZero() || reconnectGrace.isNegative()) {
            throw new IllegalArgumentException("Page Scanner reconnect grace must be positive");
        }
        this.deferredExecutor = Objects.requireNonNull(
                deferredExecutor, "Page Scanner deferred executor is required");
        this.workspaceAuthorityValidator = Objects.requireNonNull(
                workspaceAuthorityValidator, "Page Scanner workspace authority validator is required");
    }

    static PageScannerWorkspaceCoordinator getInstance() {
        return InstanceHolder.INSTANCE;
    }

    synchronized OpenResult open(OpenRequest request) {
        Objects.requireNonNull(request, "Page Scanner workspace open request is required");
        purgeExpiredEntries();
        requireBotJobTransport(request.transportSessionId());
        WorkspaceContext context = requireActiveOwner(normalizeContext(request.context()));

        WorkspaceEntry existing = activeWorkspace();
        if (existing != null && existing.context().equals(context)) {
            return reopenIfDisconnected(existing);
        }
        if (existing != null) {
            return retarget(existing, request.transportSessionId(), context);
        }
        return createAndLaunch(request.transportSessionId(), context);
    }

    /**
     * Retargets the already-open physical Page Scanner from the server-owned Bot Job switch.
     *
     * <p>The ordinary {@link #open(OpenRequest)} path still requires the exact Bot Job Details
     * transport. This narrower path is called only after the active workspace registry has moved
     * to the supplied owner, and it never launches a scanner that the user did not already have
     * open.
     */
    synchronized boolean retargetActive(WorkspaceContext requestedContext) {
        purgeExpiredEntries();
        WorkspaceEntry existing = activeWorkspace();
        if (existing == null) return false;

        WorkspaceContext context = requireActiveOwner(normalizeContext(requestedContext));
        if (existing.context().equals(context)) return true;
        return retarget(existing, existing.sourceBotJobSessionId(), context).ok();
    }

    synchronized BootstrapContext bootstrap(String transportSessionId) {
        purgeExpiredEntries();
        WorkspaceEntry entry = requireActiveWorkspace(transportSessionId);
        requireActiveOwner(entry.context());
        if (!entry.connectionEstablished()) {
            entry = entry.withConnectionEstablished();
            workspaces.put(entry.sessionId(), entry);
        }
        return entry.bootstrapContext();
    }

    synchronized boolean isActiveWorkspace(String transportSessionId) {
        purgeExpiredEntries();
        return ScannerWorkspaceSessions.isPageScannerSession(transportSessionId)
                && workspaces.containsKey(transportSessionId);
    }

    /**
     * Returns the server-owned context for one active detached Page Scanner transport.
     *
     * <p>Unlike {@link #bootstrap(String)}, this read-only authorization lookup does not mark a
     * newly launched workspace as connected. Callers must still prove ownership of the exact
     * registered WebSocket transport before using the returned context.
     */
    synchronized WorkspaceContext authoritativeContext(String transportSessionId) {
        purgeExpiredEntries();
        return requireActiveOwner(requireActiveWorkspace(transportSessionId).context());
    }

    /**
     * Executes an owner-sensitive action while the exact Page Scanner generation remains fixed.
     *
     * <p>This prevents a detached transport from being retargeted between reading its context and
     * committing a downstream owner binding.
     */
    synchronized <T> T withAuthoritativeContext(
            String transportSessionId, Function<WorkspaceContext, T> action) {
        Objects.requireNonNull(action, "action");
        purgeExpiredEntries();
        return action.apply(requireActiveOwner(requireActiveWorkspace(transportSessionId).context()));
    }

    private WorkspaceContext requireActiveOwner(WorkspaceContext context) {
        workspaceAuthorityValidator.validate(context);
        return context;
    }

    private static void requireActiveRegistryOwner(WorkspaceContext context) {
        BotJobDetailsWorkspaceRegistry.Snapshot active =
                BotJobDetailsWorkspaceRegistry.getInstance()
                        .require(context.botJobId(), context.workspaceEpoch());
        if (active.homeBankingId() != context.homeBankingId()) {
            throw new IllegalArgumentException(
                    "Page Scanner does not match the active Bot Job organization");
        }
    }

    synchronized boolean close(String transportSessionId) {
        purgeExpiredEntries();
        WorkspaceEntry entry = requireActiveWorkspace(transportSessionId);
        removeEntry(entry, CloseReason.CLOSED);
        return true;
    }

    synchronized boolean disconnected(String transportSessionId) {
        if (!ScannerWorkspaceSessions.isPageScannerSession(transportSessionId)) return false;
        WorkspaceEntry entry = workspaces.get(transportSessionId);
        if (entry == null || !entry.connectionEstablished()) return false;
        workspaces.put(entry.sessionId(), entry.disconnectedAt(clock.instant()));
        return true;
    }

    synchronized boolean closeForBotJob(int homeBankingId, int botJobId, long workspaceEpoch) {
        BotJobKey key = new BotJobKey(
                requirePositive(homeBankingId, "homeBankingId"),
                requirePositive(botJobId, "botJobId"));
        requirePositive(workspaceEpoch, "workspaceEpoch");
        purgeExpiredEntries();
        WorkspaceEntry entry = activeWorkspace();
        if (entry == null
                || !entry.botJobKey().equals(key)
                || entry.context().workspaceEpoch() != workspaceEpoch) {
            return false;
        }
        removeEntry(entry, CloseReason.BOT_JOB_CLOSED);
        return true;
    }

    synchronized boolean closeActive() {
        purgeExpiredEntries();
        WorkspaceEntry entry = activeWorkspace();
        if (entry == null) return false;
        removeEntry(entry, CloseReason.BOT_JOB_CLOSED);
        return true;
    }

    synchronized void purgeExpired() {
        purgeExpiredEntries();
    }

    synchronized int activeWorkspaceCount() {
        purgeExpiredEntries();
        return workspaces.size();
    }

    synchronized Optional<String> activeSessionIdForBotJob(int botJobId) {
        purgeExpiredEntries();
        return workspaces.values().stream()
                .filter(entry -> entry.context().botJobId() == botJobId)
                .map(WorkspaceEntry::sessionId)
                .findFirst();
    }

    synchronized Optional<String> activeSessionIdForBotJob(
            int botJobId, long workspaceEpoch) {
        purgeExpiredEntries();
        return workspaces.values().stream()
                .filter(entry -> entry.context().botJobId() == botJobId
                        && entry.context().workspaceEpoch() == workspaceEpoch)
                .map(WorkspaceEntry::sessionId)
                .findFirst();
    }

    private WorkspaceEntry activeWorkspace() {
        return workspaces.values().stream().findFirst().orElse(null);
    }

    /**
     * Rebinds the one physical Page Scanner panel without reusing its logical transport identity.
     * A fresh session prevents late scan chunks, mutation responses, or OCR callbacks from the
     * previous Bot Job from being accepted by the new binding.
     */
    private OpenResult retarget(
            WorkspaceEntry previous,
            String sourceBotJobSessionId,
            WorkspaceContext nextContext) {
        Instant now = clock.instant();

        // The first app window has not connected yet, so it cannot have produced scanner state.
        // Update the pending bootstrap context in place and keep the single in-flight launch.
        if (!previous.connectionEstablished()
                && !isConnectionOpen(previous.sessionId())
                && now.isBefore(previous.lastLaunchAt().plus(connectionGrace))) {
            releaseWorkspaceState(previous, CloseReason.SUPERSEDED);
            WorkspaceEntry pending = new WorkspaceEntry(
                    previous.sessionId(),
                    sourceBotJobSessionId,
                    nextContext,
                    now,
                    now.plus(WORKSPACE_TTL),
                    previous.lastLaunchAt(),
                    false);
            workspaces.put(pending.sessionId(), pending);
            return alreadyOpen(
                    pending,
                    "Page Scanner workspace will open for the selected Bot Job.");
        }

        if (isConnectionOpen(previous.sessionId())) {
            String nextSessionId = reserveSessionId();
            WorkspaceEntry next = new WorkspaceEntry(
                    nextSessionId,
                    sourceBotJobSessionId,
                    nextContext,
                    now,
                    now.plus(WORKSPACE_TTL),
                    now,
                    false);

            // Register the new trusted context before publishing. Bootstrap on the new transport
            // blocks on this synchronized coordinator until the retarget operation completes.
            workspaces.put(next.sessionId(), next);
            workspaces.remove(previous.sessionId(), previous);
            boolean delivered = publishRetarget(
                    previous.bootstrapContext(),
                    next.bootstrapContext(),
                    "Page Scanner switched to Bot Job " + nextContext.botJobName() + ".");
            if (!delivered) {
                workspaces.remove(next.sessionId(), next);
                workspaces.put(previous.sessionId(), previous);
                return new OpenResult(
                        false,
                        false,
                        true,
                        previous.sessionId(),
                        "The existing Page Scanner window could not switch Bot Jobs.",
                        previous.expiresAt());
            }
            // Cleanup is intentionally after successful delivery. A failed notification restores
            // a fully usable prior binding instead of reviving an entry whose browser, mutation
            // ledger, and queued operations were already destroyed.
            releaseWorkspaceState(previous, CloseReason.SUPERSEDED);
            return new OpenResult(
                    true,
                    false,
                    true,
                    next.sessionId(),
                    "Page Scanner workspace switched to the selected Bot Job.",
                    next.expiresAt());
        }

        if (previous.connectionEstablished()) {
            return deferRecovery(previous, sourceBotJobSessionId, nextContext);
        }

        // The prior native window is no longer connected (for example, Alt+F4). Retire its
        // logical binding and launch one replacement for the latest requested Bot Job.
        workspaces.remove(previous.sessionId(), previous);
        releaseWorkspaceState(previous, CloseReason.SUPERSEDED);
        return createAndLaunch(sourceBotJobSessionId, nextContext);
    }

    private OpenResult createAndLaunch(
            String sourceBotJobSessionId,
            WorkspaceContext context) {
        if (workspaces.size() >= maximumActiveWorkspaces) {
            throw new IllegalStateException("A Page Scanner workspace is already open");
        }
        String sessionId = reserveSessionId();
        Instant createdAt = clock.instant();
        Instant expiresAt = createdAt.plus(WORKSPACE_TTL);
        WorkspaceEntry entry = new WorkspaceEntry(
                sessionId,
                sourceBotJobSessionId,
                context,
                createdAt,
                expiresAt,
                createdAt,
                false);
        workspaces.put(sessionId, entry);

        boolean opened;
        try {
            opened = launcher.launch(sessionId);
        } catch (RuntimeException launchFailure) {
            removeEntry(entry, CloseReason.LAUNCH_FAILED);
            throw launchFailure;
        }
        if (!opened) {
            removeEntry(entry, CloseReason.LAUNCH_FAILED);
            return new OpenResult(
                    false,
                    false,
                    false,
                    "",
                    "Chrome or Edge application mode is unavailable.",
                    expiresAt);
        }

        return new OpenResult(
                true,
                true,
                false,
                sessionId,
                "Page Scanner workspace opened.",
                expiresAt);
    }

    private OpenResult reopenIfDisconnected(WorkspaceEntry entry) {
        if (isConnectionOpen(entry.sessionId())) {
            if (!entry.connectionEstablished()) {
                entry = entry.withConnectionEstablished();
                workspaces.put(entry.sessionId(), entry);
            }
            publishRetarget(entry.bootstrapContext(), entry.bootstrapContext(),
                    "Page Scanner workspace is already open.");
            return alreadyOpen(entry, "Page Scanner workspace is already open.");
        }

        Instant now = clock.instant();
        if (!entry.connectionEstablished()
                && now.isBefore(entry.lastLaunchAt().plus(connectionGrace))) {
            return alreadyOpen(entry, "Page Scanner workspace is opening.");
        }

        if (entry.connectionEstablished()) {
            return deferRecovery(entry, entry.sourceBotJobSessionId(), entry.context());
        }

        return relaunchExisting(entry, now);
    }

    private OpenResult relaunchExisting(WorkspaceEntry entry, Instant now) {
        boolean reopened = launcher.launch(entry.sessionId());
        if (!reopened) {
            return new OpenResult(
                    false,
                    false,
                    false,
                    "",
                    "Chrome or Edge application mode is unavailable.",
                    entry.expiresAt());
        }

        WorkspaceEntry relaunched = entry.relaunchedAt(now);
        workspaces.put(relaunched.sessionId(), relaunched);
        return new OpenResult(
                true,
                true,
                false,
                relaunched.sessionId(),
                "Page Scanner workspace reopened.",
                relaunched.expiresAt());
    }

    private OpenResult deferRecovery(
            WorkspaceEntry entry,
            String sourceBotJobSessionId,
            WorkspaceContext requestedContext) {
        Instant now = clock.instant();
        WorkspaceEntry disconnected = entry.disconnectedAt() == null
                ? entry.disconnectedAt(now)
                : entry;
        pendingOpen = new PendingOpen(
                disconnected.sessionId(), sourceBotJobSessionId, requestedContext);
        Instant deadline = disconnected.disconnectedAt().plus(reconnectGrace);
        if (!now.isBefore(deadline)) {
            return recoverNow(disconnected, pendingOpen);
        }

        if (!disconnected.recoveryScheduled()) {
            disconnected = disconnected.withRecoveryScheduled(true);
            workspaces.put(disconnected.sessionId(), disconnected);
            Duration delay = Duration.between(now, deadline);
            try {
                String scheduledSessionId = disconnected.sessionId();
                deferredExecutor.schedule(delay, () -> recoverAfterReconnectGrace(scheduledSessionId));
            } catch (RuntimeException schedulingFailure) {
                workspaces.put(disconnected.sessionId(), disconnected.withRecoveryScheduled(false));
                pendingOpen = null;
                return new OpenResult(
                        false,
                        false,
                        true,
                        disconnected.sessionId(),
                        "Page Scanner recovery could not be scheduled.",
                        disconnected.expiresAt());
            }
        }
        return alreadyOpen(
                disconnected,
                requestedContext.equals(entry.context())
                        ? "Page Scanner workspace is reconnecting."
                        : "Page Scanner workspace will switch after reconnect recovery.");
    }

    private synchronized void recoverAfterReconnectGrace(String sessionId) {
        WorkspaceEntry entry = workspaces.get(sessionId);
        if (entry == null || !entry.recoveryScheduled()) return;
        PendingOpen requested = pendingOpen;
        if (requested == null || !requested.previousSessionId().equals(sessionId)) return;
        Instant now = clock.instant();
        Instant deadline = entry.disconnectedAt().plus(reconnectGrace);
        if (now.isBefore(deadline)) {
            Duration remaining = Duration.between(now, deadline);
            deferredExecutor.schedule(remaining, () -> recoverAfterReconnectGrace(sessionId));
            return;
        }
        OpenResult recovered = recoverNow(entry, requested);
        if (!recovered.ok()) {
            log.warn("Page Scanner reconnect recovery failed for {}: {}", sessionId, recovered.message());
        }
    }

    private OpenResult recoverNow(WorkspaceEntry entry, PendingOpen requested) {
        pendingOpen = null;
        WorkspaceEntry recoverable = entry.withRecoveryScheduled(false);
        workspaces.put(recoverable.sessionId(), recoverable);
        if (requested.context().equals(recoverable.context())) {
            if (isConnectionOpen(recoverable.sessionId())) {
                WorkspaceEntry connected = recoverable.withConnectionEstablished();
                workspaces.put(connected.sessionId(), connected);
                publishRetarget(
                        connected.bootstrapContext(),
                        connected.bootstrapContext(),
                        "Page Scanner workspace is already open.");
                return alreadyOpen(connected, "Page Scanner workspace is already open.");
            }
            return relaunchExisting(recoverable, clock.instant());
        }
        if (isConnectionOpen(recoverable.sessionId())) {
            return retarget(recoverable, requested.sourceBotJobSessionId(), requested.context());
        }
        workspaces.remove(recoverable.sessionId(), recoverable);
        releaseWorkspaceState(recoverable, CloseReason.SUPERSEDED);
        return createAndLaunch(requested.sourceBotJobSessionId(), requested.context());
    }

    private OpenResult alreadyOpen(WorkspaceEntry entry, String message) {
        return new OpenResult(
                true,
                false,
                true,
                entry.sessionId(),
                message,
                entry.expiresAt());
    }

    private boolean isConnectionOpen(String sessionId) {
        try {
            return connectionProbe.isOpen(sessionId);
        } catch (RuntimeException connectionFailure) {
            log.debug(
                    "Unable to inspect Page Scanner connection {}: {}",
                    sessionId,
                    connectionFailure.getMessage());
            return false;
        }
    }

    private static void releaseWorkspaceResources(BootstrapContext context, CloseReason reason) {
        try {
            BotJobWorkspaceController.getInstance().closePageScanner(context.sessionId());
        } catch (IllegalStateException inactiveHost) {
            log.debug(
                    "Page Scanner resources were already inactive for {} after {}: {}",
                    context.sessionId(),
                    reason,
                    inactiveHost.getMessage());
        }
    }

    static boolean publishWorkspaceClosed(
            BootstrapContext workspace,
            CloseReason reason,
            String message) {
        JsonObject payload = new JsonObject();
        payload.addProperty("closed", true);
        payload.addProperty("sessionId", workspace.sessionId());
        payload.addProperty("botJobId", workspace.context().botJobId());
        payload.addProperty("workspaceEpoch", workspace.context().workspaceEpoch());
        payload.addProperty("reason", reason.name());
        payload.addProperty("message", message);
        return WebSocketSessionManager.getInstance()
                        .sendMessageJson(
                                workspace.context().homeBankingId(),
                                workspace.sessionId(),
                                payload.toString(),
                                WORKSPACE_CLOSED_OPERATION)
                != null;
    }

    static boolean publishWorkspaceRetarget(
            BootstrapContext previous,
            BootstrapContext current,
            String message) {
        JsonObject payload = new JsonObject();
        payload.addProperty("previousSessionId", previous.sessionId());
        payload.addProperty("sessionId", current.sessionId());
        payload.addProperty("botJobId", current.context().botJobId());
        payload.addProperty("workspaceEpoch", current.context().workspaceEpoch());
        payload.addProperty("message", message);
        return WebSocketSessionManager.getInstance()
                        .sendMessageJson(
                                previous.context().homeBankingId(),
                                previous.sessionId(),
                                payload.toString(),
                                WORKSPACE_RETARGET_OPERATION)
                != null;
    }

    private boolean publishRetarget(
            BootstrapContext previous,
            BootstrapContext current,
            String message) {
        try {
            return retargetNotifier.notify(previous, current, message);
        } catch (RuntimeException notificationFailure) {
            log.warn(
                    "Unable to retarget Page Scanner workspace {} to {}: {}",
                    previous.sessionId(),
                    current.sessionId(),
                    notificationFailure.getMessage());
            return false;
        }
    }

    private WorkspaceEntry requireActiveWorkspace(String sessionId) {
        if (!ScannerWorkspaceSessions.isPageScannerSession(sessionId)) {
            throw new IllegalArgumentException("Page Scanner workspace session is invalid");
        }
        WorkspaceEntry entry = workspaces.get(sessionId);
        if (entry == null || !entry.expiresAt().isAfter(clock.instant())) {
            if (entry != null) {
                removeEntry(entry, CloseReason.EXPIRED);
            }
            throw new IllegalArgumentException("Page Scanner workspace session is unknown or expired");
        }
        return entry;
    }

    private void purgeExpiredEntries() {
        Instant now = clock.instant();
        workspaces.values().stream()
                .filter(entry -> !entry.expiresAt().isAfter(now))
                .toList()
                .forEach(entry -> removeEntry(entry, CloseReason.EXPIRED));
    }

    private void removeEntry(WorkspaceEntry entry, CloseReason reason) {
        if (!Objects.equals(workspaces.get(entry.sessionId()), entry)) {
            return;
        }
        notifyWorkspaceInvalidated(entry, reason);
        if (!workspaces.remove(entry.sessionId(), entry)) {
            return;
        }
        if (pendingOpen != null && pendingOpen.previousSessionId().equals(entry.sessionId())) {
            pendingOpen = null;
        }
        releaseWorkspaceState(entry, reason);
    }

    private void releaseWorkspaceState(WorkspaceEntry entry, CloseReason reason) {
        PageScannerMutationLedger.getInstance().clearSession(entry.sessionId());
        try {
            workspaceLifecycle.closed(entry.bootstrapContext(), reason);
        } catch (RuntimeException cleanupFailure) {
            log.warn(
                    "Page Scanner workspace cleanup failed for {} after {}: {}",
                    entry.sessionId(),
                    reason,
                    cleanupFailure.getMessage());
        }
    }

    private void notifyWorkspaceInvalidated(WorkspaceEntry entry, CloseReason reason) {
        String message = invalidationMessage(reason);
        if (message == null) {
            return;
        }
        try {
            boolean delivered = invalidationNotifier.notify(
                    entry.bootstrapContext(), reason, message);
            if (!delivered) {
                log.debug(
                        "Page Scanner invalidation {} had no live transport for {}",
                        reason,
                        entry.sessionId());
            }
        } catch (RuntimeException notificationFailure) {
            log.warn(
                    "Unable to notify Page Scanner workspace {} before {}: {}",
                    entry.sessionId(),
                    reason,
                    notificationFailure.getMessage());
        }
    }

    private static String invalidationMessage(CloseReason reason) {
        return switch (reason) {
            case BOT_JOB_CLOSED ->
                    "The active Bot Job was closed. This Page Scanner workspace is no longer available.";
            case SUPERSEDED ->
                    "This Page Scanner workspace was replaced by a newer Bot Job workspace.";
            case EXPIRED ->
                    "This Page Scanner workspace expired. Open it again from Bot Job Details.";
            case CLOSED, LAUNCH_FAILED -> null;
        };
    }

    private String reserveSessionId() {
        for (int attempt = 0; attempt < MAX_ID_ATTEMPTS; attempt++) {
            String id = requireValidId(idSupplier.get());
            String sessionId = ScannerWorkspaceSessions.PAGE_SCANNER_PREFIX + id;
            if (!workspaces.containsKey(sessionId)) {
                return sessionId;
            }
        }
        throw new IllegalStateException("Unable to allocate a unique Page Scanner workspace session");
    }

    private static void requireBotJobTransport(String transportSessionId) {
        if (!ScannerWorkspaceSessions.BOT_JOB_TASKS.equals(transportSessionId)) {
            throw new IllegalArgumentException("Page Scanner can only be opened from Bot Job Details");
        }
    }

    private static WorkspaceContext normalizeContext(WorkspaceContext context) {
        Objects.requireNonNull(context, "Page Scanner Bot Job context is required");
        int homeBankingId = requirePositive(context.homeBankingId(), "homeBankingId");
        int botJobId = requirePositive(context.botJobId(), "botJobId");
        long workspaceEpoch = requirePositive(context.workspaceEpoch(), "workspaceEpoch");
        String botJobName = requireBounded(
                context.botJobName(), "Page Scanner Bot Job name is required", MAX_BOT_JOB_NAME_LENGTH);
        Integer homeUrlId = positiveOrNull(context.homeUrlId());
        String endpointUrl = boundedOptional(
                context.endpointUrl(), "Page Scanner endpoint URL is too long", MAX_ENDPOINT_URL_LENGTH);
        String browserType = boundedOptional(
                context.browserType(), "Page Scanner browser type is too long", MAX_BROWSER_TYPE_LENGTH);
        String optionsConfig = boundedOptional(
                context.optionsConfig(), "Page Scanner browser options are too long", MAX_OPTIONS_CONFIG_LENGTH);
        String jsonPath = boundedOptional(
                context.jsonPath(), "Page Scanner diagnostic path is too long", MAX_JSON_PATH_LENGTH);
        return new WorkspaceContext(
                homeBankingId,
                botJobId,
                workspaceEpoch,
                botJobName,
                homeUrlId,
                endpointUrl,
                browserType,
                optionsConfig,
                jsonPath);
    }

    private static int requirePositive(int value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException("Page Scanner " + field + " must be positive");
        }
        return value;
    }

    private static long requirePositive(long value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException("Page Scanner " + field + " must be positive");
        }
        return value;
    }

    private static Integer positiveOrNull(Integer value) {
        return value != null && value > 0 ? value : null;
    }

    private static String requireValidId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Page Scanner workspace ID is required");
        }
        String normalized = id.trim();
        if (normalized.length() > 80 || !normalized.matches("[A-Za-z0-9-]+")) {
            throw new IllegalArgumentException("Page Scanner workspace ID contains unsupported characters");
        }
        return normalized;
    }

    private static String requireBounded(String value, String errorMessage, int maximumLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(errorMessage);
        }
        String normalized = value.trim();
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(errorMessage);
        }
        return normalized;
    }

    private static String boundedOptional(String value, String errorMessage, int maximumLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(errorMessage);
        }
        return normalized;
    }

    record OpenRequest(String transportSessionId, WorkspaceContext context) {
        OpenRequest {
            transportSessionId = transportSessionId == null ? "" : transportSessionId.trim();
            context = normalizeContext(context);
        }
    }

    record OpenResult(
            boolean ok,
            boolean launched,
            boolean alreadyOpen,
            String sessionId,
            String message,
            Instant expiresAt) {}

    record WorkspaceContext(
            int homeBankingId,
            int botJobId,
            long workspaceEpoch,
            String botJobName,
            Integer homeUrlId,
            String endpointUrl,
            String browserType,
            String optionsConfig,
            String jsonPath) {
        WorkspaceContext {
            botJobName = botJobName == null ? "" : botJobName;
            endpointUrl = endpointUrl == null ? "" : endpointUrl;
            browserType = browserType == null ? "" : browserType;
            optionsConfig = optionsConfig == null ? "" : optionsConfig;
            jsonPath = jsonPath == null ? "" : jsonPath;
        }
    }

    record BootstrapContext(
            String sessionId,
            String sourceBotJobSessionId,
            WorkspaceContext context,
            Instant createdAt,
            Instant expiresAt) {}

    @FunctionalInterface
    interface WorkspaceLauncher {
        boolean launch(String sessionId);
    }

    @FunctionalInterface
    interface WorkspaceLifecycle {
        void closed(BootstrapContext context, CloseReason reason);
    }

    @FunctionalInterface
    interface WorkspaceConnectionProbe {
        boolean isOpen(String sessionId);
    }

    @FunctionalInterface
    interface WorkspaceInvalidationNotifier {
        boolean notify(BootstrapContext context, CloseReason reason, String message);
    }

    @FunctionalInterface
    interface WorkspaceRetargetNotifier {
        boolean notify(BootstrapContext previous, BootstrapContext current, String message);
    }

    @FunctionalInterface
    interface DeferredExecutor {
        void schedule(Duration delay, Runnable task);
    }

    @FunctionalInterface
    private interface WorkspaceAuthorityValidator {
        void validate(WorkspaceContext context);
    }

    enum CloseReason {
        CLOSED,
        BOT_JOB_CLOSED,
        EXPIRED,
        SUPERSEDED,
        LAUNCH_FAILED
    }

    private record BotJobKey(int homeBankingId, int botJobId) {}

    private record PendingOpen(
            String previousSessionId,
            String sourceBotJobSessionId,
            WorkspaceContext context) {}

    private record WorkspaceEntry(
            String sessionId,
            String sourceBotJobSessionId,
            WorkspaceContext context,
            Instant createdAt,
            Instant expiresAt,
            Instant lastLaunchAt,
            boolean connectionEstablished,
            Instant disconnectedAt,
            boolean recoveryScheduled) {

        WorkspaceEntry(
                String sessionId,
                String sourceBotJobSessionId,
                WorkspaceContext context,
                Instant createdAt,
                Instant expiresAt,
                Instant lastLaunchAt,
                boolean connectionEstablished) {
            this(
                    sessionId,
                    sourceBotJobSessionId,
                    context,
                    createdAt,
                    expiresAt,
                    lastLaunchAt,
                    connectionEstablished,
                    null,
                    false);
        }

        BotJobKey botJobKey() {
            return new BotJobKey(context.homeBankingId(), context.botJobId());
        }

        BootstrapContext bootstrapContext() {
            return new BootstrapContext(
                    sessionId,
                    sourceBotJobSessionId,
                    context,
                    createdAt,
                    expiresAt);
        }

        WorkspaceEntry withConnectionEstablished() {
            if (connectionEstablished && disconnectedAt == null && !recoveryScheduled) return this;
            return new WorkspaceEntry(
                    sessionId,
                    sourceBotJobSessionId,
                    context,
                    createdAt,
                    expiresAt,
                    lastLaunchAt,
                    true,
                    recoveryScheduled ? disconnectedAt : null,
                    recoveryScheduled);
        }

        WorkspaceEntry relaunchedAt(Instant relaunchedAt) {
            return new WorkspaceEntry(
                    sessionId,
                    sourceBotJobSessionId,
                    context,
                    createdAt,
                    expiresAt,
                    relaunchedAt,
                    false,
                    null,
                    false);
        }

        WorkspaceEntry disconnectedAt(Instant disconnected) {
            Instant firstDisconnect = disconnectedAt == null ? disconnected : disconnectedAt;
            return new WorkspaceEntry(
                    sessionId,
                    sourceBotJobSessionId,
                    context,
                    createdAt,
                    expiresAt,
                    lastLaunchAt,
                    connectionEstablished,
                    firstDisconnect,
                    recoveryScheduled);
        }

        WorkspaceEntry withRecoveryScheduled(boolean scheduled) {
            return new WorkspaceEntry(
                    sessionId,
                    sourceBotJobSessionId,
                    context,
                    createdAt,
                    expiresAt,
                    lastLaunchAt,
                    connectionEstablished,
                    disconnectedAt,
                    scheduled);
        }
    }

    private static final class InstanceHolder {
        private static final PageScannerWorkspaceCoordinator INSTANCE = new PageScannerWorkspaceCoordinator();
    }
}

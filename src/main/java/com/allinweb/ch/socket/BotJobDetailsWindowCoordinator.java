package com.allinweb.ch.socket;

import com.google.gson.JsonObject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/** Owns the single native Bot Job Details window and its latest authoritative target. */
@Slf4j
public final class BotJobDetailsWindowCoordinator {

    public static final String CONTROL_SESSION_PREFIX = "bot-job-window-";
    public static final String TARGET_OPERATION = "botJobDetails.windowTarget";
    public static final Duration INITIAL_CONNECTION_GRACE = Duration.ofSeconds(15);
    public static final Duration RECONNECT_GRACE = Duration.ofSeconds(2);

    private final WindowLauncher launcher;
    private final Supplier<String> idSupplier;
    private final Clock clock;
    private final ConnectionProbe connectionProbe;
    private final TargetPublisher targetPublisher;
    private final Duration connectionGrace;
    private final Duration reconnectGrace;
    private final DeferredExecutor deferredExecutor;

    private WindowEntry activeWindow;

    private BotJobDetailsWindowCoordinator() {
        this(
                (botJobId, controlSessionId) -> ARWebSocketServer.getInstance()
                        .openBotJobDetailsDesktopShell(botJobId, controlSessionId),
                () -> UUID.randomUUID().toString(),
                Clock.systemUTC(),
                WebSocketSessionManager::isSessionOpen,
                BotJobDetailsWindowCoordinator::publishTarget,
                INITIAL_CONNECTION_GRACE,
                RECONNECT_GRACE,
                (delay, task) -> CompletableFuture.delayedExecutor(
                                Math.max(1L, delay.toMillis()), TimeUnit.MILLISECONDS)
                        .execute(task));
    }

    BotJobDetailsWindowCoordinator(
            WindowLauncher launcher,
            Supplier<String> idSupplier,
            Clock clock,
            ConnectionProbe connectionProbe,
            TargetPublisher targetPublisher) {
        this(launcher, idSupplier, clock, connectionProbe, targetPublisher, INITIAL_CONNECTION_GRACE);
    }

    BotJobDetailsWindowCoordinator(
            WindowLauncher launcher,
            Supplier<String> idSupplier,
            Clock clock,
            ConnectionProbe connectionProbe,
            TargetPublisher targetPublisher,
            Duration connectionGrace) {
        this(
                launcher,
                idSupplier,
                clock,
                connectionProbe,
                targetPublisher,
                connectionGrace,
                RECONNECT_GRACE,
                (delay, task) -> {});
    }

    BotJobDetailsWindowCoordinator(
            WindowLauncher launcher,
            Supplier<String> idSupplier,
            Clock clock,
            ConnectionProbe connectionProbe,
            TargetPublisher targetPublisher,
            Duration connectionGrace,
            Duration reconnectGrace,
            DeferredExecutor deferredExecutor) {
        this.launcher = Objects.requireNonNull(launcher, "Bot Job Details window launcher is required");
        this.idSupplier = Objects.requireNonNull(idSupplier, "Bot Job Details window ID supplier is required");
        this.clock = Objects.requireNonNull(clock, "Bot Job Details window clock is required");
        this.connectionProbe = Objects.requireNonNull(
                connectionProbe, "Bot Job Details window connection probe is required");
        this.targetPublisher = Objects.requireNonNull(
                targetPublisher, "Bot Job Details window target publisher is required");
        this.connectionGrace = Objects.requireNonNull(
                connectionGrace, "Bot Job Details window connection grace is required");
        if (connectionGrace.isZero() || connectionGrace.isNegative()) {
            throw new IllegalArgumentException("Bot Job Details window connection grace must be positive");
        }
        this.reconnectGrace = Objects.requireNonNull(
                reconnectGrace, "Bot Job Details window reconnect grace is required");
        if (reconnectGrace.isZero() || reconnectGrace.isNegative()) {
            throw new IllegalArgumentException("Bot Job Details window reconnect grace must be positive");
        }
        this.deferredExecutor = Objects.requireNonNull(
                deferredExecutor, "Bot Job Details deferred executor is required");
    }

    public static BotJobDetailsWindowCoordinator getInstance() {
        return InstanceHolder.INSTANCE;
    }

    /** Opens the one native window or retargets/reopens that same logical window. */
    public synchronized OpenResult open(Target requestedTarget) {
        Target target = requireTarget(requestedTarget);
        Instant now = clock.instant();
        if (activeWindow == null) {
            return launchNew(target, now);
        }

        WindowEntry previous = activeWindow;
        boolean retargeted = !previous.target().equals(target);
        WindowEntry current = previous.withTarget(target);
        activeWindow = current;

        if (isConnectionOpen(current.controlSessionId())) {
            current = current.withConnectionEstablished();
            activeWindow = current;
            boolean published = publish(current);
            return new OpenResult(
                    published,
                    false,
                    true,
                    retargeted,
                    published,
                    current.controlSessionId(),
                    current.target(),
                    published
                            ? retargeted
                                    ? "Bot Job Details window retargeted."
                                    : "Bot Job Details window is already open."
                            : "Bot Job Details window is connected, but its target could not be delivered.");
        }

        if (!current.connectionEstablished()
                && now.isBefore(current.lastLaunchAt().plus(connectionGrace))) {
            return new OpenResult(
                    true,
                    false,
                    true,
                    retargeted,
                    false,
                    current.controlSessionId(),
                    current.target(),
                    "Bot Job Details window is opening.");
        }

        if (current.connectionEstablished()) {
            if (current.disconnectedAt() == null) {
                current = current.disconnectedAt(now);
                activeWindow = current;
            }
            Instant reconnectDeadline = current.disconnectedAt().plus(reconnectGrace);
            if (now.isBefore(reconnectDeadline)) {
                return deferRecovery(current, reconnectDeadline, retargeted);
            }
        }

        return relaunch(current, now, retargeted);
    }

    /** Marks the exact control connection ready and publishes the newest requested Bot Job. */
    public synchronized boolean connected(String controlSessionId) {
        WindowEntry current = requireOwnedWindow(controlSessionId);
        current = current.withConnectionEstablished();
        activeWindow = current;
        return publish(current);
    }

    /** Validates an exact disconnect callback without allowing a stale window to affect ownership. */
    public synchronized boolean disconnected(String controlSessionId) {
        requireControlSessionId(controlSessionId);
        if (activeWindow == null || !activeWindow.controlSessionId().equals(controlSessionId)) {
            return false;
        }
        if (activeWindow.connectionEstablished()) {
            activeWindow = activeWindow.disconnectedAt(clock.instant());
        }
        return true;
    }

    /** Explicitly releases the exact logical window reservation. */
    public synchronized boolean retire(String controlSessionId) {
        requireControlSessionId(controlSessionId);
        if (activeWindow == null || !activeWindow.controlSessionId().equals(controlSessionId)) {
            return false;
        }
        activeWindow = null;
        return true;
    }

    public synchronized boolean isActiveControlSession(String controlSessionId) {
        return isControlSessionId(controlSessionId)
                && activeWindow != null
                && activeWindow.controlSessionId().equals(controlSessionId);
    }

    synchronized int activeWindowCount() {
        return activeWindow == null ? 0 : 1;
    }

    synchronized String activeControlSessionId() {
        return activeWindow == null ? "" : activeWindow.controlSessionId();
    }

    synchronized Target activeTarget() {
        return activeWindow == null ? null : activeWindow.target();
    }

    public static boolean isControlSessionId(String controlSessionId) {
        if (controlSessionId == null || !controlSessionId.startsWith(CONTROL_SESSION_PREFIX)) {
            return false;
        }
        String identifier = controlSessionId.substring(CONTROL_SESSION_PREFIX.length());
        try {
            UUID parsed = UUID.fromString(identifier);
            return parsed.toString().equals(identifier);
        } catch (IllegalArgumentException invalidIdentifier) {
            return false;
        }
    }

    static boolean publishTarget(String controlSessionId, Target target) {
        JsonObject payload = new JsonObject();
        payload.addProperty("controlSessionId", controlSessionId);
        payload.addProperty("botJobId", target.botJobId());
        payload.addProperty("workspaceEpoch", target.workspaceEpoch());
        payload.addProperty("homeBankingId", target.homeBankingId());
        return WebSocketSessionManager.getInstance()
                        .sendMessageJson(
                                target.homeBankingId(),
                                controlSessionId,
                                payload.toString(),
                                TARGET_OPERATION)
                != null;
    }

    private OpenResult launchNew(Target target, Instant now) {
        String controlSessionId = reserveControlSessionId();
        WindowEntry reserved = new WindowEntry(controlSessionId, target, now, false, null, false);
        activeWindow = reserved;
        return launchReserved(reserved, false, now, "Bot Job Details window opened.");
    }

    private OpenResult relaunch(WindowEntry current, Instant now, boolean retargeted) {
        WindowEntry reserved = current.relaunchedAt(now);
        activeWindow = reserved;
        return launchReserved(reserved, retargeted, now, "Bot Job Details window reopened.");
    }

    private OpenResult deferRecovery(
            WindowEntry current, Instant reconnectDeadline, boolean retargeted) {
        WindowEntry scheduled = current;
        if (!current.recoveryScheduled()) {
            scheduled = current.withRecoveryScheduled(true);
            activeWindow = scheduled;
            Duration delay = Duration.between(clock.instant(), reconnectDeadline);
            try {
                String scheduledSessionId = scheduled.controlSessionId();
                deferredExecutor.schedule(delay, () -> recoverAfterReconnectGrace(scheduledSessionId));
            } catch (RuntimeException schedulingFailure) {
                activeWindow = scheduled.withRecoveryScheduled(false);
                return new OpenResult(
                        false,
                        false,
                        true,
                        retargeted,
                        false,
                        scheduled.controlSessionId(),
                        scheduled.target(),
                        "Bot Job Details recovery could not be scheduled.");
            }
        }
        return new OpenResult(
                true,
                false,
                true,
                retargeted,
                false,
                scheduled.controlSessionId(),
                scheduled.target(),
                "Bot Job Details window is reconnecting.");
    }

    private synchronized void recoverAfterReconnectGrace(String controlSessionId) {
        if (activeWindow == null
                || !activeWindow.controlSessionId().equals(controlSessionId)
                || !activeWindow.recoveryScheduled()) {
            return;
        }
        WindowEntry current = activeWindow;
        if (isConnectionOpen(controlSessionId)) {
            current = current.withConnectionEstablished();
            activeWindow = current;
            publish(current);
            return;
        }
        Instant now = clock.instant();
        Instant deadline = current.disconnectedAt().plus(reconnectGrace);
        if (now.isBefore(deadline)) {
            Duration remaining = Duration.between(now, deadline);
            deferredExecutor.schedule(remaining, () -> recoverAfterReconnectGrace(controlSessionId));
            return;
        }
        relaunch(current.withRecoveryScheduled(false), now, false);
    }

    private OpenResult launchReserved(
            WindowEntry reserved, boolean retargeted, Instant launchTime, String successMessage) {
        boolean launched;
        try {
            launched = launcher.launch(reserved.target().botJobId(), reserved.controlSessionId());
        } catch (RuntimeException launchFailure) {
            releaseReservation(reserved);
            throw launchFailure;
        }
        if (!launched) {
            releaseReservation(reserved);
            return new OpenResult(
                    false,
                    false,
                    false,
                    retargeted,
                    false,
                    "",
                    reserved.target(),
                    "Chrome or Edge application mode is unavailable.");
        }

        WindowEntry launchedWindow = reserved.relaunchedAt(launchTime);
        activeWindow = launchedWindow;
        return new OpenResult(
                true,
                true,
                false,
                retargeted,
                false,
                launchedWindow.controlSessionId(),
                launchedWindow.target(),
                successMessage);
    }

    private void releaseReservation(WindowEntry reserved) {
        if (activeWindow == reserved || (activeWindow != null
                && activeWindow.controlSessionId().equals(reserved.controlSessionId()))) {
            activeWindow = null;
        }
    }

    private WindowEntry requireOwnedWindow(String controlSessionId) {
        requireControlSessionId(controlSessionId);
        if (activeWindow == null || !activeWindow.controlSessionId().equals(controlSessionId)) {
            throw new IllegalArgumentException("Bot Job Details window session is not active");
        }
        return activeWindow;
    }

    private static void requireControlSessionId(String controlSessionId) {
        if (!isControlSessionId(controlSessionId)) {
            throw new IllegalArgumentException("Bot Job Details window session is invalid");
        }
    }

    private String reserveControlSessionId() {
        String identifier = Objects.toString(idSupplier.get(), "").trim();
        String controlSessionId = CONTROL_SESSION_PREFIX + identifier;
        requireControlSessionId(controlSessionId);
        return controlSessionId;
    }

    private boolean isConnectionOpen(String controlSessionId) {
        try {
            return connectionProbe.isOpen(controlSessionId);
        } catch (RuntimeException connectionFailure) {
            log.debug(
                    "Unable to inspect Bot Job Details window connection {}: {}",
                    controlSessionId,
                    connectionFailure.getMessage());
            return false;
        }
    }

    private boolean publish(WindowEntry entry) {
        try {
            return targetPublisher.publish(entry.controlSessionId(), entry.target());
        } catch (RuntimeException publicationFailure) {
            log.warn(
                    "Unable to publish Bot Job Details target {} to {}: {}",
                    entry.target().botJobId(),
                    entry.controlSessionId(),
                    publicationFailure.getMessage());
            return false;
        }
    }

    private static Target requireTarget(Target target) {
        return Objects.requireNonNull(target, "Bot Job Details window target is required");
    }

    public record Target(int botJobId, long workspaceEpoch, int homeBankingId) {
        public Target {
            if (botJobId <= 0) throw new IllegalArgumentException("Bot Job ID must be positive");
            if (workspaceEpoch <= 0) throw new IllegalArgumentException("Workspace epoch must be positive");
            if (homeBankingId <= 0) throw new IllegalArgumentException("Organization ID must be positive");
        }
    }

    public record OpenResult(
            boolean ok,
            boolean launched,
            boolean alreadyOpen,
            boolean retargeted,
            boolean targetPublished,
            String controlSessionId,
            Target target,
            String message) {}

    @FunctionalInterface
    interface WindowLauncher {
        boolean launch(int botJobId, String controlSessionId);
    }

    @FunctionalInterface
    interface ConnectionProbe {
        boolean isOpen(String controlSessionId);
    }

    @FunctionalInterface
    interface TargetPublisher {
        boolean publish(String controlSessionId, Target target);
    }

    @FunctionalInterface
    interface DeferredExecutor {
        void schedule(Duration delay, Runnable task);
    }

    private record WindowEntry(
            String controlSessionId,
            Target target,
            Instant lastLaunchAt,
            boolean connectionEstablished,
            Instant disconnectedAt,
            boolean recoveryScheduled) {

        WindowEntry withTarget(Target nextTarget) {
            return new WindowEntry(
                    controlSessionId,
                    nextTarget,
                    lastLaunchAt,
                    connectionEstablished,
                    disconnectedAt,
                    recoveryScheduled);
        }

        WindowEntry withConnectionEstablished() {
            if (connectionEstablished && disconnectedAt == null && !recoveryScheduled) return this;
            return new WindowEntry(controlSessionId, target, lastLaunchAt, true, null, false);
        }

        WindowEntry relaunchedAt(Instant relaunchedAt) {
            return new WindowEntry(controlSessionId, target, relaunchedAt, false, null, false);
        }

        WindowEntry disconnectedAt(Instant disconnected) {
            Instant firstDisconnect = disconnectedAt == null ? disconnected : disconnectedAt;
            return new WindowEntry(
                    controlSessionId,
                    target,
                    lastLaunchAt,
                    connectionEstablished,
                    firstDisconnect,
                    false);
        }

        WindowEntry withRecoveryScheduled(boolean scheduled) {
            return new WindowEntry(
                    controlSessionId,
                    target,
                    lastLaunchAt,
                    connectionEstablished,
                    disconnectedAt,
                    scheduled);
        }
    }

    private static final class InstanceHolder {
        private static final BotJobDetailsWindowCoordinator INSTANCE = new BotJobDetailsWindowCoordinator();
    }
}

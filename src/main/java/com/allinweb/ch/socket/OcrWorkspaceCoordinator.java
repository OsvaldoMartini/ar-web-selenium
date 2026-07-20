package com.allinweb.ch.socket;

import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/** Coordinates one detached OCR Chromium window per OCR kind. */
@Slf4j
final class OcrWorkspaceCoordinator {

    static final Duration WORKSPACE_TTL = Duration.ofHours(4);
    static final Duration INITIAL_CONNECTION_GRACE = Duration.ofSeconds(15);
    static final Duration RECONNECT_GRACE = Duration.ofSeconds(2);
    static final String WINDOW_RETARGET_OPERATION = "ocrWorkspace.windowRetarget";
    private static final int MAX_ID_ATTEMPTS = 16;
    private static final int MAX_SUGGESTIONS = 10_000;
    private static final int MAX_XPATH_LENGTH = 8_192;
    private static final int MAX_CLIENT_NAME_LENGTH = 1_024;
    private static final Gson GSON = new Gson();

    private final WorkspaceLauncher launcher;
    private final Supplier<String> idSupplier;
    private final Clock clock;
    private final SuggestionsPublisher suggestionsPublisher;
    private final PageScannerContextResolver pageScannerContextResolver;
    private final WorkspaceConnectionProbe connectionProbe;
    private final WorkspaceRetargetNotifier retargetNotifier;
    private final Duration connectionGrace;
    private final Duration reconnectGrace;
    private final DeferredExecutor deferredExecutor;
    private final Map<String, WorkspaceEntry> workspaces = new LinkedHashMap<>();
    private final Map<Kind, String> activeSessionByKind = new EnumMap<>(Kind.class);

    private OcrWorkspaceCoordinator() {
        this(
                (kind, sessionId) -> ARWebSocketServer.getInstance()
                        .openOcrWorkspaceDesktopShell(kind, sessionId),
                () -> UUID.randomUUID().toString(),
                Clock.systemUTC(),
                OcrWorkspaceCoordinator::publishSuggestions,
                OcrWorkspaceCoordinator::resolvePageScannerContext,
                WebSocketSessionManager::isSessionOpen,
                OcrWorkspaceCoordinator::publishWindowRetarget,
                INITIAL_CONNECTION_GRACE,
                RECONNECT_GRACE,
                (delay, task) -> CompletableFuture.delayedExecutor(
                                Math.max(1L, delay.toMillis()), TimeUnit.MILLISECONDS)
                        .execute(task));
    }

    OcrWorkspaceCoordinator(
            WorkspaceLauncher launcher,
            Supplier<String> idSupplier,
            Clock clock,
            SuggestionsPublisher suggestionsPublisher) {
        this(
                launcher,
                idSupplier,
                clock,
                suggestionsPublisher,
                OcrWorkspaceCoordinator::resolvePageScannerContext,
                sessionId -> true,
                (previous, current) -> true,
                INITIAL_CONNECTION_GRACE,
                RECONNECT_GRACE,
                (delay, task) -> {});
    }

    OcrWorkspaceCoordinator(
            WorkspaceLauncher launcher,
            Supplier<String> idSupplier,
            Clock clock,
            SuggestionsPublisher suggestionsPublisher,
            PageScannerContextResolver pageScannerContextResolver) {
        this(
                launcher,
                idSupplier,
                clock,
                suggestionsPublisher,
                pageScannerContextResolver,
                sessionId -> true,
                (previous, current) -> true,
                INITIAL_CONNECTION_GRACE,
                RECONNECT_GRACE,
                (delay, task) -> {});
    }

    OcrWorkspaceCoordinator(
            WorkspaceLauncher launcher,
            Supplier<String> idSupplier,
            Clock clock,
            SuggestionsPublisher suggestionsPublisher,
            PageScannerContextResolver pageScannerContextResolver,
            WorkspaceConnectionProbe connectionProbe,
            WorkspaceRetargetNotifier retargetNotifier,
            Duration connectionGrace) {
        this(
                launcher,
                idSupplier,
                clock,
                suggestionsPublisher,
                pageScannerContextResolver,
                connectionProbe,
                retargetNotifier,
                connectionGrace,
                RECONNECT_GRACE,
                (delay, task) -> {});
    }

    OcrWorkspaceCoordinator(
            WorkspaceLauncher launcher,
            Supplier<String> idSupplier,
            Clock clock,
            SuggestionsPublisher suggestionsPublisher,
            PageScannerContextResolver pageScannerContextResolver,
            WorkspaceConnectionProbe connectionProbe,
            WorkspaceRetargetNotifier retargetNotifier,
            Duration connectionGrace,
            Duration reconnectGrace,
            DeferredExecutor deferredExecutor) {
        this.launcher = Objects.requireNonNull(launcher, "OCR workspace launcher is required");
        this.idSupplier = Objects.requireNonNull(idSupplier, "OCR workspace ID supplier is required");
        this.clock = Objects.requireNonNull(clock, "OCR workspace clock is required");
        this.suggestionsPublisher =
                Objects.requireNonNull(suggestionsPublisher, "OCR suggestions publisher is required");
        this.pageScannerContextResolver = Objects.requireNonNull(
                pageScannerContextResolver, "Page Scanner context resolver is required");
        this.connectionProbe = Objects.requireNonNull(
                connectionProbe, "OCR workspace connection probe is required");
        this.retargetNotifier = Objects.requireNonNull(
                retargetNotifier, "OCR workspace retarget notifier is required");
        this.connectionGrace = Objects.requireNonNull(
                connectionGrace, "OCR workspace connection grace is required");
        if (connectionGrace.isZero() || connectionGrace.isNegative()) {
            throw new IllegalArgumentException("OCR workspace connection grace must be positive");
        }
        this.reconnectGrace = Objects.requireNonNull(
                reconnectGrace, "OCR workspace reconnect grace is required");
        if (reconnectGrace.isZero() || reconnectGrace.isNegative()) {
            throw new IllegalArgumentException("OCR workspace reconnect grace must be positive");
        }
        this.deferredExecutor = Objects.requireNonNull(
                deferredExecutor, "OCR workspace deferred executor is required");
    }

    static OcrWorkspaceCoordinator getInstance() {
        return InstanceHolder.INSTANCE;
    }

    synchronized OpenResult open(OpenRequest request) {
        Objects.requireNonNull(request, "OCR workspace open request is required");
        purgeExpiredEntries();

        WorkspaceTarget target = resolveTarget(request);
        WorkspaceEntry existing = activeWorkspace(request.kind());
        if (existing == null) {
            return createAndLaunch(request.kind(), target);
        }
        if (existing.target().equals(target)) {
            return focusOrReopen(existing);
        }
        return retarget(existing, target);
    }

    private WorkspaceTarget resolveTarget(OpenRequest request) {
        if (isLegacySourceScannerSession(request.transportSessionId())) {
            return new WorkspaceTarget(
                    request.transportSessionId(),
                    requirePositive(request.homeBankingId(), "homeBankingId"),
                    requirePositive(request.botJobId(), "botJobId"),
                    positiveOrNull(request.homeUrlId()),
                    request.parameters());
        } else if (ScannerWorkspaceSessions.isPageScannerSession(request.transportSessionId())) {
            PageScannerContext pageScannerContext =
                    pageScannerContextResolver.resolve(request.transportSessionId());
            return new WorkspaceTarget(
                    request.transportSessionId(),
                    pageScannerContext.homeBankingId(),
                    pageScannerContext.botJobId(),
                    pageScannerContext.homeUrlId(),
                    request.parameters());
        } else if (request.kind() == Kind.RESULTS) {
            WorkspaceEntry sourceConfig = requireActiveWorkspace(request.transportSessionId(), Kind.CONFIG);
            return new WorkspaceTarget(
                    sourceConfig.sourceScannerSessionId(),
                    sourceConfig.homeBankingId(),
                    sourceConfig.botJobId(),
                    sourceConfig.homeUrlId(),
                    request.parameters());
        } else {
            throw new IllegalArgumentException(
                    "OCR Config can only be opened from a scanner workspace transport");
        }
    }

    private OpenResult createAndLaunch(Kind kind, WorkspaceTarget target) {
        String sessionId = reserveSessionId(kind);
        Instant now = clock.instant();
        WorkspaceEntry entry = WorkspaceEntry.created(kind, sessionId, target, now);
        putActive(entry);

        boolean opened;
        try {
            opened = launcher.launch(kind, sessionId);
        } catch (RuntimeException launchFailure) {
            removeEntry(entry);
            throw launchFailure;
        }
        if (!opened) {
            removeEntry(entry);
            return unavailable(kind, entry.expiresAt());
        }

        return new OpenResult(
                true,
                kind,
                sessionId,
                "OCR " + kind.routeValue() + " workspace opened.",
                entry.expiresAt());
    }

    /**
     * Reuses the one physical window for this kind while assigning a fresh logical session.
     * The fresh identity prevents late OCR results or suggestion callbacks from an old scanner
     * binding from being accepted after a Bot Job/Page Scanner switch.
     */
    private OpenResult retarget(WorkspaceEntry previous, WorkspaceTarget target) {
        Instant now = clock.instant();

        // The app window has not bootstrapped yet, so it cannot own OCR state. Rebind the pending
        // launch in place and keep exactly one in-flight native browser launch.
        if (!previous.connectionEstablished()
                && !isConnectionOpen(previous.sessionId())
                && now.isBefore(previous.lastLaunchAt().plus(connectionGrace))) {
            WorkspaceEntry pending = previous.retargetPending(target, now);
            putActive(pending);
            return new OpenResult(
                    true,
                    pending.kind(),
                    pending.sessionId(),
                    "OCR " + pending.kind().routeValue() + " workspace will open for the selected scanner.",
                    pending.expiresAt());
        }

        if (isConnectionOpen(previous.sessionId())) {
            WorkspaceEntry connected = previous.withConnectionEstablished().withoutPendingTarget();
            String nextSessionId = reserveSessionId(previous.kind());
            WorkspaceEntry current = WorkspaceEntry.created(previous.kind(), nextSessionId, target, now);

            // The new context must exist before the browser switches transports and bootstraps.
            putActive(current);
            boolean delivered = publishRetarget(connected, current);
            if (!delivered) {
                removeEntry(current);
                putActive(connected);
                return new OpenResult(
                        false,
                        connected.kind(),
                        connected.sessionId(),
                        "The existing OCR " + connected.kind().routeValue()
                                + " window could not switch scanner context.",
                        connected.expiresAt());
            }
            return new OpenResult(
                    true,
                    current.kind(),
                    current.sessionId(),
                    "OCR " + current.kind().routeValue() + " workspace switched scanner context.",
                    current.expiresAt());
        }

        if (previous.connectionEstablished()) {
            return deferRecovery(previous, target);
        }

        // The old physical window was closed. Retire its stale logical session and launch one
        // replacement for the latest target.
        removeEntry(previous);
        return createAndLaunch(previous.kind(), target);
    }

    private OpenResult focusOrReopen(WorkspaceEntry entry) {
        if (isConnectionOpen(entry.sessionId())) {
            WorkspaceEntry connected = entry.withConnectionEstablished().withoutPendingTarget();
            putActive(connected);
            boolean delivered = publishRetarget(connected, connected);
            return new OpenResult(
                    delivered,
                    connected.kind(),
                    connected.sessionId(),
                    delivered
                            ? "OCR " + connected.kind().routeValue() + " workspace is already open."
                            : "The existing OCR " + connected.kind().routeValue() + " window could not be focused.",
                    connected.expiresAt());
        }

        Instant now = clock.instant();
        if (!entry.connectionEstablished()
                && now.isBefore(entry.lastLaunchAt().plus(connectionGrace))) {
            return new OpenResult(
                    true,
                    entry.kind(),
                    entry.sessionId(),
                    "OCR " + entry.kind().routeValue() + " workspace is opening.",
                    entry.expiresAt());
        }

        if (entry.connectionEstablished()) {
            return deferRecovery(entry, entry.target());
        }

        return relaunch(entry, now);
    }

    private OpenResult deferRecovery(WorkspaceEntry entry, WorkspaceTarget requestedTarget) {
        Instant now = clock.instant();
        WorkspaceEntry scheduled = entry.withPendingTarget(requestedTarget);
        if (scheduled.disconnectedAt() == null) {
            scheduled = scheduled.disconnectedAt(now);
        }
        if (!scheduled.recoveryScheduled()) {
            scheduled = scheduled.withRecoveryScheduled(true);
            putActive(scheduled);
            Instant reconnectDeadline = scheduled.disconnectedAt().plus(reconnectGrace);
            Duration delay = Duration.between(now, reconnectDeadline);
            try {
                Kind scheduledKind = scheduled.kind();
                String scheduledSessionId = scheduled.sessionId();
                deferredExecutor.schedule(
                        delay,
                        () -> recoverAfterReconnectGrace(scheduledKind, scheduledSessionId));
            } catch (RuntimeException schedulingFailure) {
                WorkspaceEntry unscheduled = scheduled.withRecoveryScheduled(false);
                putActive(unscheduled);
                return new OpenResult(
                        false,
                        unscheduled.kind(),
                        unscheduled.sessionId(),
                        "OCR " + unscheduled.kind().routeValue() + " recovery could not be scheduled.",
                        unscheduled.expiresAt());
            }
        } else {
            putActive(scheduled);
        }
        return new OpenResult(
                true,
                scheduled.kind(),
                scheduled.sessionId(),
                "OCR " + scheduled.kind().routeValue() + " workspace is reconnecting.",
                scheduled.expiresAt());
    }

    private synchronized void recoverAfterReconnectGrace(Kind kind, String sessionId) {
        WorkspaceEntry current = activeWorkspace(kind);
        if (current == null
                || !current.sessionId().equals(sessionId)
                || !current.recoveryScheduled()) {
            return;
        }

        WorkspaceTarget requestedTarget = current.pendingTarget() == null
                ? current.target()
                : current.pendingTarget();
        if (isConnectionOpen(sessionId)) {
            WorkspaceEntry connected = current.withConnectionEstablished().withoutPendingTarget();
            putActive(connected);
            if (connected.target().equals(requestedTarget)) {
                publishRetarget(connected, connected);
            } else {
                retarget(connected, requestedTarget);
            }
            return;
        }

        Instant now = clock.instant();
        Instant reconnectDeadline = current.disconnectedAt().plus(reconnectGrace);
        if (now.isBefore(reconnectDeadline)) {
            Duration remaining = Duration.between(now, reconnectDeadline);
            deferredExecutor.schedule(
                    remaining,
                    () -> recoverAfterReconnectGrace(kind, sessionId));
            return;
        }

        WorkspaceEntry ready = current.withRecoveryScheduled(false).withoutPendingTarget();
        if (!ready.target().equals(requestedTarget)) {
            removeEntry(ready);
            createAndLaunch(kind, requestedTarget);
        } else {
            relaunch(ready, now);
        }
    }

    private OpenResult relaunch(WorkspaceEntry entry, Instant now) {
        boolean opened = launcher.launch(entry.kind(), entry.sessionId());
        if (!opened) {
            WorkspaceEntry retryable = entry.withRecoveryScheduled(false);
            putActive(retryable);
            return unavailable(entry.kind(), entry.expiresAt());
        }
        WorkspaceEntry relaunched = entry.relaunchedAt(now);
        putActive(relaunched);
        return new OpenResult(
                true,
                relaunched.kind(),
                relaunched.sessionId(),
                "OCR " + relaunched.kind().routeValue() + " workspace reopened.",
                relaunched.expiresAt());
    }

    private OpenResult unavailable(Kind kind, Instant expiresAt) {
        return new OpenResult(
                false,
                kind,
                "",
                "Chrome or Edge application mode is unavailable.",
                expiresAt);
    }

    synchronized BootstrapContext bootstrap(String transportSessionId) {
        purgeExpiredEntries();
        WorkspaceEntry entry = requireActiveWorkspace(transportSessionId, null);
        if (!entry.connectionEstablished()) {
            entry = entry.withConnectionEstablished();
            putActive(entry);
        }
        return entry.bootstrapContext();
    }

    synchronized ApplyResult applySuggestions(String transportSessionId, List<Suggestion> suggestions) {
        purgeExpiredEntries();
        WorkspaceEntry entry = requireActiveWorkspace(transportSessionId, Kind.RESULTS);
        List<Suggestion> normalized = normalizeSuggestions(suggestions);
        boolean published = suggestionsPublisher.publish(
                entry.homeBankingId(), entry.sourceScannerSessionId(), normalized);
        String message = published
                ? "OCR suggestions sent to the scanner workspace."
                : "The originating scanner workspace is not connected.";
        return new ApplyResult(published, entry.sourceScannerSessionId(), normalized.size(), message);
    }

    synchronized void purgeExpired() {
        purgeExpiredEntries();
    }

    static boolean isWorkspaceSessionId(String sessionId) {
        return Kind.fromSessionId(sessionId) != null;
    }

    synchronized int activeWorkspaceCount() {
        purgeExpiredEntries();
        return workspaces.size();
    }

    synchronized boolean isActiveWorkspace(String sessionId) {
        purgeExpiredEntries();
        if (!isWorkspaceSessionId(sessionId)) return false;
        WorkspaceEntry entry = workspaces.get(sessionId);
        return entry != null && sessionId.equals(activeSessionByKind.get(entry.kind()));
    }

    synchronized boolean disconnected(String sessionId) {
        purgeExpiredEntries();
        if (!isWorkspaceSessionId(sessionId)) return false;
        WorkspaceEntry entry = workspaces.get(sessionId);
        if (entry == null
                || !sessionId.equals(activeSessionByKind.get(entry.kind()))
                || !entry.connectionEstablished()) {
            return false;
        }
        putActive(entry.disconnectedAt(clock.instant()));
        return true;
    }

    synchronized int activeWindowCount(Kind kind) {
        purgeExpiredEntries();
        return activeWorkspace(Objects.requireNonNull(kind, "OCR workspace kind is required")) == null ? 0 : 1;
    }

    private WorkspaceEntry activeWorkspace(Kind kind) {
        String sessionId = activeSessionByKind.get(kind);
        return sessionId == null ? null : workspaces.get(sessionId);
    }

    private void putActive(WorkspaceEntry entry) {
        String previousSessionId = activeSessionByKind.put(entry.kind(), entry.sessionId());
        if (previousSessionId != null && !previousSessionId.equals(entry.sessionId())) {
            workspaces.remove(previousSessionId);
        }
        workspaces.put(entry.sessionId(), entry);
    }

    private void removeEntry(WorkspaceEntry entry) {
        workspaces.remove(entry.sessionId());
        activeSessionByKind.remove(entry.kind(), entry.sessionId());
    }

    private void purgeExpiredEntries() {
        Instant now = clock.instant();
        for (WorkspaceEntry entry : List.copyOf(workspaces.values())) {
            if (!entry.expiresAt().isAfter(now)) {
                removeEntry(entry);
            }
        }
    }

    private boolean isConnectionOpen(String sessionId) {
        try {
            return connectionProbe.isOpen(sessionId);
        } catch (RuntimeException connectionFailure) {
            log.debug(
                    "Unable to inspect OCR workspace connection {}: {}",
                    sessionId,
                    connectionFailure.getMessage());
            return false;
        }
    }

    private boolean publishRetarget(WorkspaceEntry previous, WorkspaceEntry current) {
        try {
            return retargetNotifier.notify(previous.bootstrapContext(), current.bootstrapContext());
        } catch (RuntimeException publicationFailure) {
            log.warn(
                    "Unable to retarget OCR {} workspace from {} to {}: {}",
                    previous.kind().routeValue(),
                    previous.sessionId(),
                    current.sessionId(),
                    publicationFailure.getMessage());
            return false;
        }
    }

    private WorkspaceEntry requireActiveWorkspace(String sessionId, Kind expectedKind) {
        String normalizedSessionId = requireNonBlank(sessionId, "OCR workspace transport session is required");
        WorkspaceEntry entry = workspaces.get(normalizedSessionId);
        if (entry == null || !entry.expiresAt().isAfter(clock.instant())) {
            if (entry != null) removeEntry(entry);
            throw new IllegalArgumentException("OCR workspace session is unknown or expired");
        }
        if (expectedKind != null && entry.kind() != expectedKind) {
            throw new IllegalArgumentException("OCR workspace operation is not valid for " + entry.kind().routeValue());
        }
        return entry;
    }

    private String reserveSessionId(Kind kind) {
        for (int attempt = 0; attempt < MAX_ID_ATTEMPTS; attempt++) {
            String id = requireValidId(idSupplier.get());
            String sessionId = kind.sessionPrefix() + id;
            if (!workspaces.containsKey(sessionId)) return sessionId;
        }
        throw new IllegalStateException("Unable to allocate a unique OCR workspace session");
    }

    private static List<Suggestion> normalizeSuggestions(List<Suggestion> suggestions) {
        if (suggestions == null || suggestions.isEmpty()) {
            throw new IllegalArgumentException("At least one OCR suggestion is required");
        }
        if (suggestions.size() > MAX_SUGGESTIONS) {
            throw new IllegalArgumentException("Too many OCR suggestions");
        }

        Map<String, Suggestion> byXPath = new LinkedHashMap<>();
        for (Suggestion suggestion : suggestions) {
            if (suggestion == null) throw new IllegalArgumentException("OCR suggestions cannot contain null entries");
            String xPath = requireBounded(
                    suggestion.xPath(), "OCR suggestion XPath is required", MAX_XPATH_LENGTH);
            String clientNamed = requireBounded(
                    suggestion.clientNamed(), "OCR suggestion client name is required", MAX_CLIENT_NAME_LENGTH);
            byXPath.put(xPath, new Suggestion(xPath, clientNamed));
        }
        return List.copyOf(byXPath.values());
    }

    private static boolean publishSuggestions(
            int homeBankingId, String sourceScannerSessionId, List<Suggestion> suggestions) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("suggestions", suggestions);
        return WebSocketSessionManager.getInstance()
                        .sendMessageJson(
                                homeBankingId,
                                sourceScannerSessionId,
                                GSON.toJson(payload),
                                "applyOcrSuggestions")
                != null;
    }

    static boolean publishWindowRetarget(BootstrapContext previous, BootstrapContext current) {
        JsonObject payload = new JsonObject();
        payload.addProperty("kind", current.kind().routeValue());
        payload.addProperty("previousSessionId", previous.sessionId());
        payload.addProperty("sessionId", current.sessionId());
        payload.addProperty("homeBankingId", current.homeBankingId());
        payload.addProperty("botJobId", current.botJobId());
        if (current.homeUrlId() != null) payload.addProperty("homeUrlId", current.homeUrlId());
        return WebSocketSessionManager.getInstance()
                        .sendMessageJson(
                                previous.homeBankingId(),
                                previous.sessionId(),
                                payload.toString(),
                                WINDOW_RETARGET_OPERATION)
                != null;
    }

    private static boolean isLegacySourceScannerSession(String sessionId) {
        return ScannerWorkspaceSessions.SCANNER_GRID.equals(sessionId)
                || ScannerWorkspaceSessions.PRE_SCANNER_GRID.equals(sessionId);
    }

    private static PageScannerContext resolvePageScannerContext(String sessionId) {
        PageScannerWorkspaceCoordinator.WorkspaceContext context =
                PageScannerWorkspaceCoordinator.getInstance().bootstrap(sessionId).context();
        return new PageScannerContext(
                context.homeBankingId(),
                context.botJobId(),
                context.homeUrlId());
    }

    private static int requirePositive(int value, String field) {
        if (value <= 0) throw new IllegalArgumentException("OCR workspace " + field + " must be positive");
        return value;
    }

    private static Integer positiveOrNull(Integer value) {
        return value != null && value > 0 ? value : null;
    }

    private static String requireValidId(String id) {
        String normalized = requireNonBlank(id, "OCR workspace ID is required");
        if (normalized.length() > 80 || !normalized.matches("[A-Za-z0-9-]+")) {
            throw new IllegalArgumentException("OCR workspace ID contains unsupported characters");
        }
        return normalized;
    }

    private static String requireBounded(String value, String errorMessage, int maximumLength) {
        String normalized = requireNonBlank(value, errorMessage);
        if (normalized.length() > maximumLength) throw new IllegalArgumentException(errorMessage);
        return normalized;
    }

    private static String requireNonBlank(String value, String errorMessage) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(errorMessage);
        return value.trim();
    }

    private static JsonArray copy(JsonArray parameters) {
        return parameters == null ? new JsonArray() : parameters.deepCopy();
    }

    enum Kind {
        CONFIG("config", "ocr-config-"),
        RESULTS("results", "ocr-results-");

        private final String routeValue;
        private final String sessionPrefix;

        Kind(String routeValue, String sessionPrefix) {
            this.routeValue = routeValue;
            this.sessionPrefix = sessionPrefix;
        }

        static Kind parse(String value) {
            String normalized = requireNonBlank(value, "OCR workspace kind is required")
                    .toLowerCase(Locale.ROOT);
            for (Kind kind : values()) {
                if (kind.routeValue.equals(normalized) || kind.name().equalsIgnoreCase(normalized)) return kind;
            }
            throw new IllegalArgumentException("Unsupported OCR workspace kind: " + value);
        }

        static Kind fromSessionId(String sessionId) {
            if (sessionId == null) return null;
            for (Kind kind : values()) {
                String prefix = kind.sessionPrefix;
                if (sessionId.startsWith(prefix) && sessionId.length() > prefix.length()) {
                    String id = sessionId.substring(prefix.length());
                    if (id.length() <= 80 && id.matches("[A-Za-z0-9-]+")) return kind;
                }
            }
            return null;
        }

        String routeValue() {
            return routeValue;
        }

        String sessionPrefix() {
            return sessionPrefix;
        }
    }

    record OpenRequest(
            Kind kind,
            String transportSessionId,
            int homeBankingId,
            int botJobId,
            Integer homeUrlId,
            JsonArray parameters) {
        OpenRequest {
            kind = Objects.requireNonNull(kind, "OCR workspace kind is required");
            transportSessionId = requireNonBlank(
                    transportSessionId, "OCR workspace transport session is required");
            parameters = copy(parameters);
        }

        @Override
        public JsonArray parameters() {
            return copy(parameters);
        }
    }

    record OpenResult(boolean ok, Kind kind, String sessionId, String message, Instant expiresAt) {}

    record BootstrapContext(
            Kind kind,
            String sessionId,
            String sourceScannerSessionId,
            int homeBankingId,
            int botJobId,
            Integer homeUrlId,
            JsonArray parameters,
            Instant createdAt,
            Instant expiresAt) {
        BootstrapContext {
            parameters = copy(parameters);
        }

        @Override
        public JsonArray parameters() {
            return copy(parameters);
        }
    }

    record Suggestion(String xPath, String clientNamed) {}

    record ApplyResult(boolean published, String sourceScannerSessionId, int suggestionCount, String message) {}

    @FunctionalInterface
    interface WorkspaceLauncher {
        boolean launch(Kind kind, String sessionId);
    }

    @FunctionalInterface
    interface SuggestionsPublisher {
        boolean publish(int homeBankingId, String sourceScannerSessionId, List<Suggestion> suggestions);
    }

    @FunctionalInterface
    interface PageScannerContextResolver {
        PageScannerContext resolve(String sessionId);
    }

    @FunctionalInterface
    interface WorkspaceConnectionProbe {
        boolean isOpen(String sessionId);
    }

    @FunctionalInterface
    interface WorkspaceRetargetNotifier {
        boolean notify(BootstrapContext previous, BootstrapContext current);
    }

    @FunctionalInterface
    interface DeferredExecutor {
        void schedule(Duration delay, Runnable task);
    }

    record PageScannerContext(int homeBankingId, int botJobId, Integer homeUrlId) {
        PageScannerContext {
            requirePositive(homeBankingId, "homeBankingId");
            requirePositive(botJobId, "botJobId");
            homeUrlId = positiveOrNull(homeUrlId);
        }
    }

    private record WorkspaceTarget(
            String sourceScannerSessionId,
            int homeBankingId,
            int botJobId,
            Integer homeUrlId,
            JsonArray parameters) {
        WorkspaceTarget {
            sourceScannerSessionId = requireNonBlank(
                    sourceScannerSessionId, "OCR source scanner session is required");
            homeBankingId = requirePositive(homeBankingId, "homeBankingId");
            botJobId = requirePositive(botJobId, "botJobId");
            homeUrlId = positiveOrNull(homeUrlId);
            parameters = copy(parameters);
        }

        @Override
        public JsonArray parameters() {
            return copy(parameters);
        }
    }

    private record WorkspaceEntry(
            Kind kind,
            String sessionId,
            WorkspaceTarget target,
            Instant createdAt,
            Instant expiresAt,
            Instant lastLaunchAt,
            boolean connectionEstablished,
            WorkspaceTarget pendingTarget,
            Instant disconnectedAt,
            boolean recoveryScheduled) {

        static WorkspaceEntry created(
                Kind kind, String sessionId, WorkspaceTarget target, Instant createdAt) {
            return new WorkspaceEntry(
                    kind,
                    sessionId,
                    target,
                    createdAt,
                    createdAt.plus(WORKSPACE_TTL),
                    createdAt,
                    false,
                    null,
                    null,
                    false);
        }

        String sourceScannerSessionId() {
            return target.sourceScannerSessionId();
        }

        int homeBankingId() {
            return target.homeBankingId();
        }

        int botJobId() {
            return target.botJobId();
        }

        Integer homeUrlId() {
            return target.homeUrlId();
        }

        BootstrapContext bootstrapContext() {
            return new BootstrapContext(
                    kind,
                    sessionId,
                    target.sourceScannerSessionId(),
                    target.homeBankingId(),
                    target.botJobId(),
                    target.homeUrlId(),
                    target.parameters(),
                    createdAt,
                    expiresAt);
        }

        WorkspaceEntry withConnectionEstablished() {
            if (connectionEstablished && disconnectedAt == null && !recoveryScheduled) return this;
            return new WorkspaceEntry(
                    kind,
                    sessionId,
                    target,
                    createdAt,
                    expiresAt,
                    lastLaunchAt,
                    true,
                    pendingTarget,
                    null,
                    false);
        }

        WorkspaceEntry relaunchedAt(Instant relaunchedAt) {
            return new WorkspaceEntry(
                    kind,
                    sessionId,
                    target,
                    createdAt,
                    expiresAt,
                    relaunchedAt,
                    false,
                    null,
                    null,
                    false);
        }

        WorkspaceEntry retargetPending(WorkspaceTarget nextTarget, Instant retargetedAt) {
            return new WorkspaceEntry(
                    kind,
                    sessionId,
                    nextTarget,
                    retargetedAt,
                    retargetedAt.plus(WORKSPACE_TTL),
                    lastLaunchAt,
                    false,
                    null,
                    null,
                    false);
        }

        WorkspaceEntry withPendingTarget(WorkspaceTarget requestedTarget) {
            return new WorkspaceEntry(
                    kind,
                    sessionId,
                    target,
                    createdAt,
                    expiresAt,
                    lastLaunchAt,
                    connectionEstablished,
                    requestedTarget,
                    disconnectedAt,
                    recoveryScheduled);
        }

        WorkspaceEntry withoutPendingTarget() {
            if (pendingTarget == null) return this;
            return new WorkspaceEntry(
                    kind,
                    sessionId,
                    target,
                    createdAt,
                    expiresAt,
                    lastLaunchAt,
                    connectionEstablished,
                    null,
                    disconnectedAt,
                    recoveryScheduled);
        }

        WorkspaceEntry disconnectedAt(Instant disconnected) {
            return new WorkspaceEntry(
                    kind,
                    sessionId,
                    target,
                    createdAt,
                    expiresAt,
                    lastLaunchAt,
                    connectionEstablished,
                    pendingTarget,
                    disconnectedAt == null ? disconnected : disconnectedAt,
                    recoveryScheduled);
        }

        WorkspaceEntry withRecoveryScheduled(boolean scheduled) {
            return new WorkspaceEntry(
                    kind,
                    sessionId,
                    target,
                    createdAt,
                    expiresAt,
                    lastLaunchAt,
                    connectionEstablished,
                    pendingTarget,
                    disconnectedAt,
                    scheduled);
        }
    }

    private static final class InstanceHolder {
        private static final OcrWorkspaceCoordinator INSTANCE = new OcrWorkspaceCoordinator();
    }
}

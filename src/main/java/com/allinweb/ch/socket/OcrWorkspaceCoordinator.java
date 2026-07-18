package com.allinweb.ch.socket;

import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Coordinates detached OCR Chromium workspaces without coupling them to a scanner React tree. */
final class OcrWorkspaceCoordinator {

    static final Duration WORKSPACE_TTL = Duration.ofHours(4);
    private static final int MAX_ID_ATTEMPTS = 16;
    private static final int MAX_ACTIVE_WORKSPACES = 256;
    private static final int MAX_SUGGESTIONS = 10_000;
    private static final int MAX_XPATH_LENGTH = 8_192;
    private static final int MAX_CLIENT_NAME_LENGTH = 1_024;
    private static final Gson GSON = new Gson();

    private final WorkspaceLauncher launcher;
    private final Supplier<String> idSupplier;
    private final Clock clock;
    private final SuggestionsPublisher suggestionsPublisher;
    private final ConcurrentHashMap<String, WorkspaceEntry> workspaces = new ConcurrentHashMap<>();

    private OcrWorkspaceCoordinator() {
        this(
                (kind, sessionId) -> ARWebSocketServer.getInstance()
                        .openOcrWorkspaceDesktopShell(kind, sessionId),
                () -> UUID.randomUUID().toString(),
                Clock.systemUTC(),
                OcrWorkspaceCoordinator::publishSuggestions);
    }

    OcrWorkspaceCoordinator(
            WorkspaceLauncher launcher,
            Supplier<String> idSupplier,
            Clock clock,
            SuggestionsPublisher suggestionsPublisher) {
        this.launcher = Objects.requireNonNull(launcher, "OCR workspace launcher is required");
        this.idSupplier = Objects.requireNonNull(idSupplier, "OCR workspace ID supplier is required");
        this.clock = Objects.requireNonNull(clock, "OCR workspace clock is required");
        this.suggestionsPublisher =
                Objects.requireNonNull(suggestionsPublisher, "OCR suggestions publisher is required");
    }

    static OcrWorkspaceCoordinator getInstance() {
        return InstanceHolder.INSTANCE;
    }

    synchronized OpenResult open(OpenRequest request) {
        Objects.requireNonNull(request, "OCR workspace open request is required");
        purgeExpired();
        if (workspaces.size() >= MAX_ACTIVE_WORKSPACES) {
            throw new IllegalStateException("Too many OCR workspaces are already open");
        }

        String sourceScannerSessionId;
        int homeBankingId;
        int botJobId;
        Integer homeUrlId;
        JsonArray parameters;

        if (isSourceScannerSession(request.transportSessionId())) {
            sourceScannerSessionId = request.transportSessionId();
            homeBankingId = requirePositive(request.homeBankingId(), "homeBankingId");
            botJobId = requirePositive(request.botJobId(), "botJobId");
            homeUrlId = positiveOrNull(request.homeUrlId());
            parameters = copy(request.parameters());
        } else if (request.kind() == Kind.RESULTS) {
            WorkspaceEntry sourceConfig = requireActiveWorkspace(request.transportSessionId(), Kind.CONFIG);
            sourceScannerSessionId = sourceConfig.sourceScannerSessionId();
            homeBankingId = sourceConfig.homeBankingId();
            botJobId = sourceConfig.botJobId();
            homeUrlId = sourceConfig.homeUrlId();
            parameters = copy(request.parameters());
        } else {
            throw new IllegalArgumentException(
                    "OCR Config can only be opened from a scanner workspace transport");
        }

        String sessionId = reserveSessionId(request.kind());
        Instant createdAt = clock.instant();
        Instant expiresAt = createdAt.plus(WORKSPACE_TTL);
        WorkspaceEntry entry = new WorkspaceEntry(
                request.kind(),
                sessionId,
                sourceScannerSessionId,
                homeBankingId,
                botJobId,
                homeUrlId,
                parameters,
                createdAt,
                expiresAt);

        workspaces.put(sessionId, entry);
        boolean opened;
        try {
            opened = launcher.launch(request.kind(), sessionId);
        } catch (RuntimeException launchFailure) {
            workspaces.remove(sessionId, entry);
            throw launchFailure;
        }
        if (!opened) {
            workspaces.remove(sessionId, entry);
            return new OpenResult(
                    false,
                    request.kind(),
                    "",
                    "Chrome or Edge application mode is unavailable.",
                    expiresAt);
        }

        return new OpenResult(true, request.kind(), sessionId, "OCR workspace opened.", expiresAt);
    }

    BootstrapContext bootstrap(String transportSessionId) {
        purgeExpired();
        WorkspaceEntry entry = requireActiveWorkspace(transportSessionId, null);
        return entry.bootstrapContext();
    }

    ApplyResult applySuggestions(String transportSessionId, List<Suggestion> suggestions) {
        purgeExpired();
        WorkspaceEntry entry = requireActiveWorkspace(transportSessionId, Kind.RESULTS);
        List<Suggestion> normalized = normalizeSuggestions(suggestions);
        boolean published = suggestionsPublisher.publish(
                entry.homeBankingId(), entry.sourceScannerSessionId(), normalized);
        String message = published
                ? "OCR suggestions sent to the scanner workspace."
                : "The originating scanner workspace is not connected.";
        return new ApplyResult(published, entry.sourceScannerSessionId(), normalized.size(), message);
    }

    void purgeExpired() {
        Instant now = clock.instant();
        workspaces.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    static boolean isWorkspaceSessionId(String sessionId) {
        return Kind.fromSessionId(sessionId) != null;
    }

    int activeWorkspaceCount() {
        purgeExpired();
        return workspaces.size();
    }

    private WorkspaceEntry requireActiveWorkspace(String sessionId, Kind expectedKind) {
        String normalizedSessionId = requireNonBlank(sessionId, "OCR workspace transport session is required");
        WorkspaceEntry entry = workspaces.get(normalizedSessionId);
        if (entry == null || !entry.expiresAt().isAfter(clock.instant())) {
            if (entry != null) workspaces.remove(normalizedSessionId, entry);
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

    private static boolean isSourceScannerSession(String sessionId) {
        return ScannerWorkspaceSessions.SCANNER_GRID.equals(sessionId)
                || ScannerWorkspaceSessions.PRE_SCANNER_GRID.equals(sessionId);
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

    private record WorkspaceEntry(
            Kind kind,
            String sessionId,
            String sourceScannerSessionId,
            int homeBankingId,
            int botJobId,
            Integer homeUrlId,
            JsonArray parameters,
            Instant createdAt,
            Instant expiresAt) {
        WorkspaceEntry {
            parameters = copy(parameters);
        }

        @Override
        public JsonArray parameters() {
            return copy(parameters);
        }

        BootstrapContext bootstrapContext() {
            return new BootstrapContext(
                    kind,
                    sessionId,
                    sourceScannerSessionId,
                    homeBankingId,
                    botJobId,
                    homeUrlId,
                    parameters,
                    createdAt,
                    expiresAt);
        }
    }

    private static final class InstanceHolder {
        private static final OcrWorkspaceCoordinator INSTANCE = new OcrWorkspaceCoordinator();
    }
}

package com.allinweb.ch.socket;

import com.allinweb.ch.facade.BotJobDetailsWorkspaceRegistry;
import com.allinweb.ch.model.DetachedWorkspaceSessions;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import javax.websocket.Session;
import lombok.extern.slf4j.Slf4j;

/** Owner-scoped backend seam for the detached Page Mappings explorer. */
@Slf4j
public final class PageMappingsWorkspaceService {

    static final String RETARGET_OPERATION = "pageMappings.retarget";
    private static final String SESSION_ID = DetachedWorkspaceSessions.PAGE_MAPPINGS_MANAGER;
    private static final PageMappingsWorkspaceService INSTANCE = new PageMappingsWorkspaceService();

    private final Object bindingLock = new Object();
    private final BotJobOwnerResolver botJobOwnerResolver;
    private final PageScannerOwnerResolver pageScannerOwnerResolver;
    private final WindowAccess windowAccess;
    private final RetargetPublisher retargetPublisher;
    private final RetargetObserver retargetObserver;
    private final ExactTransportAuthorizer transportAuthorizer;
    private Binding binding;

    public static PageMappingsWorkspaceService getInstance() {
        return INSTANCE;
    }

    private PageMappingsWorkspaceService() {
        this(
                PageMappingsWorkspaceService::resolveBotJobOwner,
                PageMappingsWorkspaceService::resolvePageScannerOwner,
                new WindowAccess() {
                    @Override
                    public boolean isOpen() {
                        return WebSocketSessionManager.isSessionOpen(SESSION_ID);
                    }

                    @Override
                    public boolean openOrFocus(int botJobId) {
                        return PagesOpenWorkspaceService.getInstance().openOrFocusDetachedWorkspace(
                                SESSION_ID,
                                botJobId,
                                "Page Mappings requested for this Bot Job.");
                    }
                },
                PageMappingsWorkspaceService::publishRetarget,
                (previous, current) -> MemoryListWorkspaceService.getInstance()
                        .pageMappingsRetargeted(previous, current),
                PageMappingsWorkspaceService::isExactRegisteredTransport);
    }

    PageMappingsWorkspaceService(
            BotJobOwnerResolver botJobOwnerResolver,
            PageScannerOwnerResolver pageScannerOwnerResolver,
            WindowAccess windowAccess,
            RetargetPublisher retargetPublisher,
            RetargetObserver retargetObserver,
            ExactTransportAuthorizer transportAuthorizer) {
        this.botJobOwnerResolver = Objects.requireNonNull(botJobOwnerResolver);
        this.pageScannerOwnerResolver = Objects.requireNonNull(pageScannerOwnerResolver);
        this.windowAccess = Objects.requireNonNull(windowAccess);
        this.retargetPublisher = Objects.requireNonNull(retargetPublisher);
        this.retargetObserver = Objects.requireNonNull(retargetObserver);
        this.transportAuthorizer = Objects.requireNonNull(transportAuthorizer);
    }

    /** Opens Page Mappings for the active, server-owned Bot Job Details workspace. */
    public JsonObject openForBotJob(int botJobId) {
        try {
            return open(botJobOwnerResolver.resolve(botJobId), null);
        } catch (IllegalArgumentException unauthorized) {
            return failure(null, unauthorized.getMessage());
        } catch (RuntimeException failure) {
            log.warn("Unable to open Page Mappings for Bot Job {}", botJobId, failure);
            return failure(null, "Page Mappings workspace could not be opened.");
        }
    }

    /** Opens Page Mappings using only the context owned by the exact Page Scanner transport. */
    public JsonObject openFromPageScanner(
            JsonObject body, String requesterSessionId, Session requesterTransport) {
        try {
            requireExactPageScannerTransport(requesterSessionId, requesterTransport);
            OwnerTarget target = pageScannerOwnerResolver.resolve(requesterSessionId);
            validateOwnerAssertions(body, target, null);
            return open(target, body);
        } catch (IllegalArgumentException unauthorized) {
            return failure(body, unauthorized.getMessage());
        } catch (RuntimeException failure) {
            log.warn("Unable to open Page Mappings from Page Scanner {}", requesterSessionId, failure);
            return failure(body, "Page Mappings workspace could not be opened.");
        }
    }

    /** Loads owner-filtered capture history for the exact detached Page Mappings transport. */
    public JsonObject bootstrap(
            JsonObject body,
            String requesterSessionId,
            Session requesterTransport,
            Connection connection) {
        Binding authorized;
        try {
            authorized = authorizeDetachedRequest(body, requesterSessionId, requesterTransport);
        } catch (IllegalArgumentException unauthorized) {
            return failure(body, unauthorized.getMessage());
        }

        JsonObject response = baseResponse(body, authorized);
        response.addProperty("ok", true);
        response.addProperty("sessionId", SESSION_ID);
        JsonArray snapshots = new JsonArray();
        String sql = "SELECT scan_id, home_url_id, page_key, page_url, captured_at, element_count, "
                + "artifact_path, manifest_sha256, status, pinned "
                + "FROM page_scan_snapshot WHERE home_banking_id = ? AND bot_job_id = ? "
                + "ORDER BY captured_at DESC";
        try (PreparedStatement statement = Objects.requireNonNull(connection).prepareStatement(sql)) {
            statement.setInt(1, authorized.homeBankingId());
            statement.setInt(2, authorized.botJobId());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    JsonObject snapshot = new JsonObject();
                    snapshot.addProperty("scanId", rows.getString("scan_id"));
                    if (rows.getObject("home_url_id") != null) {
                        snapshot.addProperty("homeUrlId", rows.getInt("home_url_id"));
                    }
                    snapshot.addProperty("pageKey", rows.getString("page_key"));
                    snapshot.addProperty("pageUrl", rows.getString("page_url"));
                    snapshot.addProperty("capturedAt", rows.getString("captured_at"));
                    snapshot.addProperty("elementCount", rows.getInt("element_count"));
                    snapshot.addProperty("artifactPath", rows.getString("artifact_path"));
                    snapshot.addProperty("manifestSha256", rows.getString("manifest_sha256"));
                    snapshot.addProperty("status", rows.getString("status"));
                    snapshot.addProperty("pinned", rows.getInt("pinned") != 0);
                    snapshots.add(snapshot);
                }
            }
        } catch (Exception failure) {
            log.error(
                    "Unable to load Page Mappings snapshots for homeBankingId={} botJobId={}",
                    authorized.homeBankingId(),
                    authorized.botJobId(),
                    failure);
            response.addProperty("ok", false);
            response.addProperty("message", "Page Mappings history is unavailable.");
        }
        response.add("snapshots", snapshots);
        return response;
    }

    /** Loads one artifact that belongs to the exact currently bound owner. */
    public JsonObject capture(
            JsonObject body,
            String requesterSessionId,
            Session requesterTransport,
            Connection connection) {
        Binding authorized;
        try {
            authorized = authorizeDetachedRequest(body, requesterSessionId, requesterTransport);
        } catch (IllegalArgumentException unauthorized) {
            return failure(body, unauthorized.getMessage());
        }

        String scanId = string(body, "scanId");
        if (scanId.isBlank()) {
            return failure(body, "A valid scan ID is required.", authorized);
        }
        String artifactPath;
        try (PreparedStatement statement = Objects.requireNonNull(connection).prepareStatement(
                "SELECT artifact_path FROM page_scan_snapshot "
                        + "WHERE scan_id = ? AND home_banking_id = ? AND bot_job_id = ?")) {
            statement.setString(1, scanId);
            statement.setInt(2, authorized.homeBankingId());
            statement.setInt(3, authorized.botJobId());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return failure(body, "The selected scan capture was not found.", authorized);
                }
                artifactPath = rows.getString(1);
            }
        } catch (Exception failure) {
            return failure(body, "The selected scan capture was not found.", authorized);
        }

        JsonObject response = baseResponse(body, authorized);
        try {
            String configuredRoot = ARPropertyManager.getInstance().getProperty(ARPropertyEnum.PATH_DB);
            Path root = Path.of(configuredRoot)
                    .toAbsolutePath()
                    .normalize()
                    .resolve("page_diagnostics")
                    .resolve("Scanned")
                    .normalize();
            Path folder = root.resolve(artifactPath == null ? "" : artifactPath).normalize();
            if (!folder.startsWith(root) || !Files.isDirectory(folder)) {
                return failure(body, "The capture artifact is unavailable.", authorized);
            }
            response.addProperty("ok", true);
            response.add("elements", JsonParser.parseString(Files.readString(folder.resolve("elements.json"))));
            Path screenshot = folder.resolve("page-BJ.png");
            if (Files.isRegularFile(screenshot) && Files.size(screenshot) <= 8_000_000) {
                response.addProperty(
                        "screenshotBase64",
                        Base64.getEncoder().encodeToString(Files.readAllBytes(screenshot)));
                response.addProperty("screenshotMime", "image/png");
            }
            return response;
        } catch (Exception failure) {
            log.warn("Unable to load Page Mappings capture {}", scanId, failure);
            return failure(
                    body, "The selected scan artifact could not be loaded.", authorized);
        }
    }

    /**
     * Authorizes a Page Mappings Memory List contribution against the current owner and epoch.
     * Client owner fields remain assertions only and never choose the server-side scope.
     */
    Binding authorizeMemoryListSource(
            JsonObject body, String requesterSessionId, Session requesterTransport) {
        return authorizeDetachedRequest(body, requesterSessionId, requesterTransport);
    }

    private JsonObject open(OwnerTarget target, JsonObject request) {
        if (target.homeBankingId() <= 0 || target.botJobId() <= 0 || target.workspaceEpoch() <= 0) {
            return failure(request, "Page Mappings requires an active Bot Job owner.");
        }

        synchronized (bindingLock) {
            Binding previous = binding;
            boolean changed = previous == null || !previous.matches(target);
            Binding candidate = changed
                    ? new Binding(
                            UUID.randomUUID().toString(),
                            target.workspaceEpoch(),
                            target.homeBankingId(),
                            target.botJobId(),
                            target.botJobName())
                    : previous;
            boolean alreadyOpen = windowAccess.isOpen();
            binding = candidate;
            final boolean opened;
            try {
                opened = windowAccess.openOrFocus(candidate.botJobId());
            } catch (RuntimeException launchFailure) {
                binding = previous;
                throw launchFailure;
            }
            if (!opened) {
                binding = previous;
                return failure(request, "Page Mappings workspace could not be opened.");
            }

            boolean retargetPublished = changed && (alreadyOpen || windowAccess.isOpen());
            if (retargetPublished && !retargetPublisher.publish(candidate)) {
                binding = previous;
                return failure(request, "The existing Page Mappings page could not be retargeted.");
            }
            if (changed) {
                try {
                    retargetObserver.retargeted(previous, candidate);
                } catch (RuntimeException cleanupFailure) {
                    binding = previous;
                    log.warn("Unable to clear the previous Page Mappings Memory List owner", cleanupFailure);
                    if (retargetPublished
                            && previous != null
                            && !retargetPublisher.publish(previous)) {
                        log.error("Unable to publish the Page Mappings retarget rollback");
                    }
                    return failure(request, "Page Mappings owner could not be changed safely.");
                }
            }

            JsonObject response = baseResponse(request, candidate);
            response.addProperty("ok", true);
            response.addProperty("alreadyOpen", alreadyOpen);
            response.addProperty("retargeted", retargetPublished);
            response.addProperty(
                    "message",
                    retargetPublished
                            ? "Page Mappings workspace retargeted."
                            : alreadyOpen
                                    ? "Page Mappings workspace focused."
                            : "Page Mappings workspace opened.");
            return response;
        }
    }

    private Binding authorizeDetachedRequest(
            JsonObject body, String requesterSessionId, Session requesterTransport) {
        if (!SESSION_ID.equals(requesterSessionId)
                || !transportAuthorizer.isExact(requesterSessionId, requesterTransport)) {
            throw new IllegalArgumentException(
                    "The detached Page Mappings transport is not authoritative.");
        }
        synchronized (bindingLock) {
            if (binding == null) {
                throw new IllegalArgumentException("Page Mappings has no active Bot Job owner.");
            }
            validateOwnerAssertions(body, binding.asTarget(), binding.bindingEpoch());
            JsonObject nested = object(body, "snapshot");
            if (nested != null) {
                validateOwnerAssertions(nested, binding.asTarget(), binding.bindingEpoch());
            }
            return binding;
        }
    }

    private void requireExactPageScannerTransport(String sessionId, Session transport) {
        if (!ScannerWorkspaceSessions.isPageScannerSession(sessionId)
                || !transportAuthorizer.isExact(sessionId, transport)) {
            throw new IllegalArgumentException("The Page Scanner transport is not authoritative.");
        }
    }

    private static void validateOwnerAssertions(
            JsonObject body, OwnerTarget target, String expectedBindingEpoch) {
        assertInteger(body, "homeBankingId", target.homeBankingId());
        assertInteger(body, "botJobId", target.botJobId());
        assertLong(body, "workspaceEpoch", target.workspaceEpoch());
        if (expectedBindingEpoch != null) {
            assertEpoch(body, "bindingEpoch", expectedBindingEpoch);
            assertEpoch(body, "sourceBindingEpoch", expectedBindingEpoch);
        }
    }

    private static void assertInteger(JsonObject body, String field, int expected) {
        long asserted = assertedPositiveLong(body, field);
        if (asserted > 0 && asserted != expected) {
            throw new IllegalArgumentException("Page Mappings owner changed. Refresh this page.");
        }
    }

    private static void assertLong(JsonObject body, String field, long expected) {
        long asserted = assertedPositiveLong(body, field);
        if (asserted > 0 && asserted != expected) {
            throw new IllegalArgumentException("Page Mappings owner changed. Refresh this page.");
        }
    }

    private static long assertedPositiveLong(JsonObject body, String field) {
        if (body == null || !body.has(field) || body.get(field).isJsonNull()) return 0;
        try {
            long value = body.get(field).getAsLong();
            return Math.max(0, value);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Page Mappings owner assertion is invalid.");
        }
    }

    private static void assertEpoch(JsonObject body, String field, String expected) {
        if (body == null || !body.has(field) || body.get(field).isJsonNull()) return;
        String asserted = string(body, field).trim();
        if (!asserted.isEmpty() && !asserted.equals(expected)) {
            throw new IllegalArgumentException("Page Mappings owner changed. Refresh this page.");
        }
    }

    private static OwnerTarget resolveBotJobOwner(int botJobId) {
        BotJobDetailsWorkspaceRegistry.Snapshot active =
                BotJobDetailsWorkspaceRegistry.getInstance().require(botJobId);
        return new OwnerTarget(
                active.homeBankingId(),
                active.botJobId(),
                active.workspaceEpoch(),
                active.name());
    }

    private static OwnerTarget resolvePageScannerOwner(String requesterSessionId) {
        PageScannerWorkspaceCoordinator.WorkspaceContext context =
                PageScannerWorkspaceCoordinator.getInstance()
                        .authoritativeContext(requesterSessionId);
        return new OwnerTarget(
                context.homeBankingId(),
                context.botJobId(),
                context.workspaceEpoch(),
                context.botJobName());
    }

    private static boolean isExactRegisteredTransport(String sessionId, Session transport) {
        return transport != null
                && transport.isOpen()
                && WebSocketSessionManager.getSession(sessionId) == transport;
    }

    private static boolean publishRetarget(Binding current) {
        Session transport = WebSocketSessionManager.getSession(SESSION_ID);
        if (!isExactRegisteredTransport(SESSION_ID, transport)) return false;

        JsonObject body = baseResponse(null, current);
        body.addProperty("ok", true);
        body.addProperty("retargeted", true);
        body.addProperty("message", "Page Mappings workspace retargeted.");
        JsonObject envelope = new JsonObject();
        envelope.addProperty("homeBankingId", current.homeBankingId());
        envelope.addProperty("sessionId", SESSION_ID);
        envelope.addProperty("operationId", RETARGET_OPERATION);
        envelope.addProperty("body", body.toString());
        try {
            WebSocketSessionManager.sendText(transport, envelope.toString());
            return true;
        } catch (IOException | RuntimeException sendFailure) {
            log.warn("Unable to publish Page Mappings retarget", sendFailure);
            return false;
        }
    }

    private static JsonObject baseResponse(JsonObject request, Binding owner) {
        JsonObject response = new JsonObject();
        copyRequestId(response, request);
        response.addProperty("bindingEpoch", owner.bindingEpoch());
        response.addProperty("workspaceEpoch", owner.workspaceEpoch());
        response.addProperty("homeBankingId", owner.homeBankingId());
        response.addProperty("botJobId", owner.botJobId());
        response.addProperty("botJobName", owner.botJobName());
        return response;
    }

    private static JsonObject failure(JsonObject request, String message) {
        JsonObject response = new JsonObject();
        copyRequestId(response, request);
        response.addProperty("ok", false);
        response.addProperty(
                "message",
                message == null || message.isBlank()
                        ? "Page Mappings request was rejected."
                        : message);
        return response;
    }

    private static JsonObject failure(JsonObject request, String message, Binding owner) {
        JsonObject response = baseResponse(request, owner);
        response.addProperty("ok", false);
        response.addProperty("message", message);
        return response;
    }

    private static void copyRequestId(JsonObject target, JsonObject source) {
        String requestId = string(source, "requestId");
        if (!requestId.isBlank()) target.addProperty("requestId", requestId);
    }

    private static String string(JsonObject body, String field) {
        if (body == null || !body.has(field) || body.get(field).isJsonNull()) return "";
        try {
            return body.get(field).getAsString();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static JsonObject object(JsonObject body, String field) {
        if (body == null || !body.has(field) || !body.get(field).isJsonObject()) return null;
        return body.getAsJsonObject(field);
    }

    record OwnerTarget(
            int homeBankingId,
            int botJobId,
            long workspaceEpoch,
            String botJobName) {
        OwnerTarget {
            botJobName = botJobName == null ? "" : botJobName;
        }
    }

    record Binding(
            String bindingEpoch,
            long workspaceEpoch,
            int homeBankingId,
            int botJobId,
            String botJobName) {
        Binding {
            bindingEpoch = Objects.requireNonNull(bindingEpoch);
            botJobName = botJobName == null ? "" : botJobName;
        }

        boolean matches(OwnerTarget target) {
            return homeBankingId == target.homeBankingId()
                    && botJobId == target.botJobId()
                    && workspaceEpoch == target.workspaceEpoch();
        }

        OwnerTarget asTarget() {
            return new OwnerTarget(homeBankingId, botJobId, workspaceEpoch, botJobName);
        }
    }

    @FunctionalInterface
    interface BotJobOwnerResolver {
        OwnerTarget resolve(int botJobId);
    }

    @FunctionalInterface
    interface PageScannerOwnerResolver {
        OwnerTarget resolve(String requesterSessionId);
    }

    interface WindowAccess {
        boolean isOpen();

        boolean openOrFocus(int botJobId);
    }

    @FunctionalInterface
    interface RetargetPublisher {
        boolean publish(Binding current);
    }

    @FunctionalInterface
    interface RetargetObserver {
        void retargeted(Binding previous, Binding current);
    }

    @FunctionalInterface
    interface ExactTransportAuthorizer {
        boolean isExact(String sessionId, Session transport);
    }
}

package com.allinweb.ch.socket;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

/** Read-only P2 seam for the detached Page Mappings explorer. */
@Slf4j
public final class PageMappingsWorkspaceService {

    private static final PageMappingsWorkspaceService INSTANCE = new PageMappingsWorkspaceService();

    public static PageMappingsWorkspaceService getInstance() { return INSTANCE; }

    private PageMappingsWorkspaceService() {}

    public JsonObject capture(JsonObject body, Connection connection) {
        JsonObject response = new JsonObject();
        int homeBankingId = positive(body, "homeBankingId");
        int botJobId = positive(body, "botJobId");
        String scanId = body != null && body.has("scanId") ? body.get("scanId").getAsString() : "";
        if (scanId.isBlank() || homeBankingId <= 0 || botJobId <= 0) {
            return failure(response, "A valid owner and scan ID are required.");
        }
        String artifactPath;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT artifact_path FROM page_scan_snapshot WHERE scan_id = ? AND home_banking_id = ? AND bot_job_id = ?")) {
            statement.setString(1, scanId);
            statement.setInt(2, homeBankingId);
            statement.setInt(3, botJobId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return failure(response, "The selected scan capture was not found.");
                artifactPath = rows.getString(1);
            }
        } catch (Exception failure) {
            return failure(response, "The selected scan capture was not found.");
        }
        try {
            String configuredRoot = ARPropertyManager.getInstance().getProperty(ARPropertyEnum.PATH_DB);
            Path root = Path.of(configuredRoot).toAbsolutePath().normalize()
                    .resolve("page_diagnostics").resolve("Scanned").normalize();
            Path folder = root.resolve(artifactPath == null ? "" : artifactPath).normalize();
            if (!folder.startsWith(root) || !Files.isDirectory(folder)) return failure(response, "The capture artifact is unavailable.");
            response.addProperty("ok", true);
            response.add("elements", JsonParser.parseString(Files.readString(folder.resolve("elements.json"))));
            Path screenshot = folder.resolve("page-BJ.png");
            if (Files.isRegularFile(screenshot) && Files.size(screenshot) <= 8_000_000) {
                response.addProperty("screenshotBase64", Base64.getEncoder().encodeToString(Files.readAllBytes(screenshot)));
                response.addProperty("screenshotMime", "image/png");
            }
            return response;
        } catch (Exception failure) {
            log.warn("Unable to load Page Mappings capture {}", scanId, failure);
            return failure(response, "The selected scan artifact could not be loaded.");
        }
    }

    private static JsonObject failure(JsonObject response, String message) {
        response.addProperty("ok", false);
        response.addProperty("message", message);
        return response;
    }

    public JsonObject openForBotJob(int botJobId) {
        JsonObject response = new JsonObject();
        response.addProperty("botJobId", botJobId);
        try {
            boolean opened = PagesOpenWorkspaceService.getInstance().openOrFocusDetachedWorkspace(
                    com.allinweb.ch.model.DetachedWorkspaceSessions.PAGE_MAPPINGS_MANAGER,
                    botJobId,
                    "Page Mappings requested for this Bot Job.");
            response.addProperty("ok", opened);
            response.addProperty("alreadyOpen", WebSocketSessionManager.isSessionOpen(
                    com.allinweb.ch.model.DetachedWorkspaceSessions.PAGE_MAPPINGS_MANAGER));
            response.addProperty("message", opened ? "Page Mappings workspace opened." : "Page Mappings workspace could not be opened.");
        } catch (RuntimeException failure) {
            response.addProperty("ok", false);
            response.addProperty("message", failure.getMessage() == null
                    ? "Page Mappings workspace could not be opened." : failure.getMessage());
        }
        return response;
    }

    public JsonObject bootstrap(JsonObject body, String sessionId, Connection connection) {
        JsonObject response = new JsonObject();
        response.addProperty("ok", true);
        response.addProperty("sessionId", sessionId == null ? "" : sessionId);
        int homeBankingId = positive(body, "homeBankingId");
        int botJobId = positive(body, "botJobId");
        response.addProperty("homeBankingId", homeBankingId);
        response.addProperty("botJobId", botJobId);
        JsonArray snapshots = new JsonArray();
        String sql = "SELECT scan_id, home_url_id, page_key, page_url, captured_at, element_count, "
                + "artifact_path, manifest_sha256, status, pinned "
                + "FROM page_scan_snapshot WHERE home_banking_id = ? AND bot_job_id = ? "
                + "ORDER BY captured_at DESC";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, homeBankingId);
            statement.setInt(2, botJobId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    JsonObject snapshot = new JsonObject();
                    snapshot.addProperty("scanId", rows.getString("scan_id"));
                    if (rows.getObject("home_url_id") != null) snapshot.addProperty("homeUrlId", rows.getInt("home_url_id"));
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
            log.error("Unable to load Page Mappings snapshots for homeBankingId={} botJobId={}",
                    homeBankingId, botJobId, failure);
            response.addProperty("ok", false);
            response.addProperty("message", "Page Mappings history is unavailable.");
        }
        response.add("snapshots", snapshots);
        return response;
    }

    private static int positive(JsonObject body, String field) {
        if (body == null || !body.has(field) || !body.get(field).isJsonPrimitive()) return 0;
        try { return Math.max(0, body.get(field).getAsInt()); } catch (RuntimeException ignored) { return 0; }
    }
}

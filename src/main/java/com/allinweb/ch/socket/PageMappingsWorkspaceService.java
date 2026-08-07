package com.allinweb.ch.socket;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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

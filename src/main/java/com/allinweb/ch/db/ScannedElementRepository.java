package com.allinweb.ch.db;

import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.ScannedElement;
import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Data access for the {@code scanned_element} source-of-truth registry. Methods take a
 * {@link Connection} so they can be unit-tested against in-memory SQLite; {@code PerformDataBase}
 * wraps them with its pooled connection.
 *
 * <p>{@link #upsert} is dialect-agnostic (SELECT-then-INSERT/UPDATE rather than vendor-specific
 * upsert syntax) so it works across Postgres / SQLServer / SQLite (TEXT) / Access.
 */
@Slf4j
public final class ScannedElementRepository {

    private static final Gson GSON = new Gson();

    private ScannedElementRepository() {}

    /**
     * Stable identity hash from the locator fields. Same-name elements with different
     * xPath/iframe/id/css produce different hashes — the disambiguation key within a scope.
     */
    public static String hashOf(ElementDTO e) {
        String basis = nz(e.getXPath()) + "|" + nz(e.getIFrameXPath()) + "|" + nz(e.getAttribId()) + "|"
                + nz(e.getCssSelector());
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(basis.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception ex) {
            // Fallback: never fail a scan over hashing — degrade to a stable string hash.
            return Integer.toHexString(basis.hashCode());
        }
    }

    /** Result of an upsert batch. */
    public record UpsertResult(int inserted, int updated) {}

    /**
     * Insert new elements or refresh existing ones (matched by scope + element_hash), bumping
     * scan_count and last_scanned_at. {@code someText}/{@code definedName} on the DTOs are already
     * OCR-corrected by ElementTextResolver, so persisting them captures the OCR correction.
     */
    public static UpsertResult upsert(
            Connection conn,
            Integer homeBankingId,
            Integer botJobId,
            Integer homeUrlId,
            String pageUrl,
            List<ElementDTO> elements)
            throws SQLException {
        if (elements == null || elements.isEmpty()) {
            return new UpsertResult(0, 0);
        }

        String selectSql = "SELECT id, custom_x_path FROM scanned_element"
                + " WHERE home_banking_id = ? AND bot_job_id = ? AND element_hash = ?";
        String insertSql = "INSERT INTO scanned_element ("
                + "home_banking_id, bot_job_id, home_url_id, page_url, element_hash, tag_name, type_element,"
                + "defined_name, client_named, some_text, x_path, custom_x_path, css_selector, attrib_id,"
                + "attrib_name, coordinates, iframe_xpath, shadow_host, shadow_root, attribute_data,"
                + "scan_count, first_scanned_at, last_scanned_at) VALUES ("
                + "?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)";
        String updateSql = "UPDATE scanned_element SET "
                + "home_url_id = ?, page_url = ?, tag_name = ?, type_element = ?, defined_name = ?,"
                + "client_named = ?, some_text = ?, x_path = ?, custom_x_path = ?, css_selector = ?,"
                + "attrib_id = ?, attrib_name = ?, coordinates = ?, iframe_xpath = ?, shadow_host = ?,"
                + "shadow_root = ?, attribute_data = ?, scan_count = scan_count + 1,"
                + "last_scanned_at = CURRENT_TIMESTAMP WHERE id = ?";

        int inserted = 0;
        int updated = 0;
        boolean prevAuto = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try (PreparedStatement sel = conn.prepareStatement(selectSql);
                PreparedStatement ins = conn.prepareStatement(insertSql);
                PreparedStatement upd = conn.prepareStatement(updateSql)) {
            for (ElementDTO e : elements) {
                if (e == null) continue;
                String hash = hashOf(e);

                Long existingId = null;
                String existingCustomXPath = null;
                sel.setObject(1, homeBankingId);
                sel.setObject(2, botJobId);
                sel.setString(3, hash);
                try (ResultSet rs = sel.executeQuery()) {
                    if (rs.next()) {
                        existingId = rs.getLong(1);
                        existingCustomXPath = rs.getString(2);
                    }
                }

                String attrJson = e.getAttributeData() == null ? null : GSON.toJson(e.getAttributeData());

                if (existingId == null) {
                    int i = 1;
                    ins.setObject(i++, homeBankingId);
                    ins.setObject(i++, botJobId);
                    ins.setObject(i++, homeUrlId);
                    ins.setString(i++, pageUrl);
                    ins.setString(i++, hash);
                    ins.setString(i++, e.getTagName());
                    ins.setString(i++, e.getTypeElement());
                    ins.setString(i++, e.getDefinedName());
                    ins.setString(i++, e.getClientNamed());
                    ins.setString(i++, e.getSomeText());
                    ins.setString(i++, e.getXPath());
                    ins.setString(i++, e.getCustomXPath());
                    ins.setString(i++, e.getCssSelector());
                    ins.setString(i++, e.getAttribId());
                    ins.setString(i++, e.getAttribName());
                    ins.setString(i++, e.getCoordinates());
                    ins.setString(i++, e.getIFrameXPath());
                    ins.setString(i++, e.getShadowHost());
                    ins.setString(i++, e.getShadowRoot());
                    ins.setString(i++, attrJson);
                    ins.executeUpdate();
                    inserted++;
                } else {
                    int i = 1;
                    upd.setObject(i++, homeUrlId);
                    upd.setString(i++, pageUrl);
                    upd.setString(i++, e.getTagName());
                    upd.setString(i++, e.getTypeElement());
                    upd.setString(i++, e.getDefinedName());
                    upd.setString(i++, e.getClientNamed());
                    upd.setString(i++, e.getSomeText());
                    upd.setString(i++, e.getXPath());
                    // A normal re-scan does not know about a client-authored override. Preserve the
                    // persisted custom XPath unless this DTO explicitly supplies a replacement.
                    String customXPath = e.getCustomXPath() == null || e.getCustomXPath().isBlank()
                            ? existingCustomXPath
                            : e.getCustomXPath();
                    if ((e.getCustomXPath() == null || e.getCustomXPath().isBlank())
                            && customXPath != null
                            && !customXPath.isBlank()) {
                        // Rehydrate the override into the outgoing scan DTO as well as preserving
                        // it in the registry. The detached grid can then apply the element later
                        // without silently falling back to the raw scanner XPath.
                        e.setCustomXPath(customXPath);
                    }
                    upd.setString(i++, customXPath);
                    upd.setString(i++, e.getCssSelector());
                    upd.setString(i++, e.getAttribId());
                    upd.setString(i++, e.getAttribName());
                    upd.setString(i++, e.getCoordinates());
                    upd.setString(i++, e.getIFrameXPath());
                    upd.setString(i++, e.getShadowHost());
                    upd.setString(i++, e.getShadowRoot());
                    upd.setString(i++, attrJson);
                    upd.setLong(i++, existingId);
                    upd.executeUpdate();
                    updated++;
                }
            }
            conn.commit();
        } catch (SQLException ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(prevAuto);
        }
        log.info(
                "scanned_element upsert — hb={} bot={} inserted={} updated={}",
                homeBankingId,
                botJobId,
                inserted,
                updated);
        return new UpsertResult(inserted, updated);
    }

    /**
     * Persist a client-authored XPath override for one element that already exists in the scanner
     * registry. This deliberately does not insert a row or increment {@code scan_count}: applying a
     * locator is a mutation of an authoritative scan result, not a new scan.
     *
     * @return the number of rows updated (zero when the scoped element identity is stale/missing).
     */
    public static int updateCustomXPath(
            Connection conn,
            Integer homeBankingId,
            Integer botJobId,
            ElementDTO element)
            throws SQLException {
        if (element == null
                || element.getXPath() == null
                || element.getXPath().isBlank()
                || element.getCustomXPath() == null
                || element.getCustomXPath().isBlank()) {
            return 0;
        }
        String sql = "UPDATE scanned_element SET custom_x_path = ?"
                + " WHERE home_banking_id = ? AND bot_job_id = ? AND element_hash = ?";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, element.getCustomXPath());
            statement.setObject(2, homeBankingId);
            statement.setObject(3, botJobId);
            statement.setString(4, hashOf(element));
            return statement.executeUpdate();
        }
    }

    /** Load all registry rows for a scope, most-recently-scanned first. */
    public static List<ScannedElement> load(Connection conn, Integer homeBankingId, Integer botJobId)
            throws SQLException {
        String sql = "SELECT * FROM scanned_element WHERE home_banking_id = ? AND bot_job_id = ? "
                + "ORDER BY last_scanned_at DESC, id ASC";
        List<ScannedElement> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, homeBankingId);
            ps.setObject(2, botJobId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
        }
        return out;
    }

    /**
     * Load registry rows by bot job alone (a bot job belongs to one organization, so this is the
     * right scope for execution-time resolution without needing the home_banking_id).
     */
    public static List<ScannedElement> loadByBotJob(Connection conn, Integer botJobId) throws SQLException {
        String sql = "SELECT * FROM scanned_element WHERE bot_job_id = ? ORDER BY last_scanned_at DESC, id ASC";
        List<ScannedElement> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, botJobId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
        }
        return out;
    }

    private static ScannedElement map(ResultSet rs) throws SQLException {
        ScannedElement s = new ScannedElement();
        s.setId(rs.getLong("id"));
        s.setHomeBankingId((Integer) rs.getObject("home_banking_id"));
        s.setBotJobId((Integer) rs.getObject("bot_job_id"));
        s.setHomeUrlId((Integer) rs.getObject("home_url_id"));
        s.setPageUrl(rs.getString("page_url"));
        s.setElementHash(rs.getString("element_hash"));
        s.setTagName(rs.getString("tag_name"));
        s.setTypeElement(rs.getString("type_element"));
        s.setDefinedName(rs.getString("defined_name"));
        s.setClientNamed(rs.getString("client_named"));
        s.setSomeText(rs.getString("some_text"));
        s.setXPath(rs.getString("x_path"));
        s.setCustomXPath(rs.getString("custom_x_path"));
        s.setCssSelector(rs.getString("css_selector"));
        s.setAttribId(rs.getString("attrib_id"));
        s.setAttribName(rs.getString("attrib_name"));
        s.setCoordinates(rs.getString("coordinates"));
        s.setIFrameXPath(rs.getString("iframe_xpath"));
        s.setShadowHost(rs.getString("shadow_host"));
        s.setShadowRoot(rs.getString("shadow_root"));
        s.setAttributeData(rs.getString("attribute_data"));
        s.setOcrText(rs.getString("ocr_text"));
        s.setOcrMatchQuality(rs.getString("ocr_match_quality"));
        Object conf = rs.getObject("ocr_confidence");
        s.setOcrConfidence(conf == null ? null : ((Number) conf).doubleValue());
        s.setScanCount(rs.getInt("scan_count"));
        s.setFirstScannedAt(rs.getString("first_scanned_at"));
        s.setLastScannedAt(rs.getString("last_scanned_at"));
        return s;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}

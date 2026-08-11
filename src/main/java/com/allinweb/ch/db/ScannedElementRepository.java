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
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
        return hash(locatorBasis(e));
    }

    /**
     * Stable row identity for a locator observed on one exact page.
     *
     * <p>Including the page key in the digest makes the original cross-dialect unique constraint
     * page-aware without destructive table rebuilds on SQLite or Access.
     */
    public static String pageScopedHash(String pageKey, ElementDTO element) {
        if (pageKey == null || pageKey.isBlank()) {
            throw new IllegalArgumentException("A scanned page key is required");
        }
        return hash(pageKey + "\u0000" + locatorBasis(element));
    }

    private static String locatorBasis(ElementDTO element) {
        if (element == null) return "0:0:0:0:";
        return encoded(element.getXPath())
                + encoded(element.getIFrameXPath())
                + encoded(element.getAttribId())
                + encoded(element.getCssSelector());
    }

    private static String encoded(String value) {
        String safe = nz(value);
        return safe.length() + ":" + safe;
    }

    private static String hash(String basis) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(basis.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "SHA-256 is required for scanned element identity", ex);
        }
    }

    /** Result of an upsert batch. */
    public record UpsertResult(int inserted, int updated) {}

    /**
     * Insert new elements or refresh existing ones (matched by organization + Bot Job + exact page
     * + page-scoped element hash), bumping scan_count and last_scanned_at. {@code
     * someText}/{@code definedName} on the DTOs are already OCR-corrected by ElementTextResolver,
     * so persisting them captures the OCR correction.
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

        requireScope(homeBankingId, botJobId);
        ScannedPageIdentity page = ScannedPageIdentity.fromLiveUrl(pageUrl);
        String selectSql = "SELECT id, custom_x_path, client_named FROM scanned_element"
                + " WHERE home_banking_id = ? AND bot_job_id = ? AND page_key = ? AND element_hash = ?";
        String insertSql = "INSERT INTO scanned_element ("
                + "home_banking_id, bot_job_id, home_url_id, page_url, page_key, element_hash, tag_name,"
                + "type_element, defined_name, client_named, some_text, x_path, custom_x_path,"
                + "css_selector, attrib_id, attrib_name, coordinates, iframe_xpath, shadow_host,"
                + "shadow_root, attribute_data,"
                + "scan_count, first_scanned_at, last_scanned_at) VALUES ("
                + "?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)";
        String updateSql = "UPDATE scanned_element SET "
                + "home_url_id = ?, page_url = ?, page_key = ?, tag_name = ?, type_element = ?,"
                + "defined_name = ?, client_named = ?, some_text = ?, x_path = ?, custom_x_path = ?,"
                + "css_selector = ?, attrib_id = ?, attrib_name = ?, coordinates = ?, iframe_xpath = ?,"
                + "shadow_host = ?, shadow_root = ?, attribute_data = ?, scan_count = scan_count + 1,"
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
                String elementHash = pageScopedHash(page.pageKey(), e);

                Long existingId = null;
                String existingCustomXPath = null;
                String existingClientNamed = null;
                sel.setObject(1, homeBankingId);
                sel.setObject(2, botJobId);
                sel.setString(3, page.pageKey());
                sel.setString(4, elementHash);
                try (ResultSet rs = sel.executeQuery()) {
                    if (rs.next()) {
                        existingId = rs.getLong(1);
                        existingCustomXPath = rs.getString(2);
                        existingClientNamed = rs.getString(3);
                    }
                }

                String attrJson = e.getAttributeData() == null ? null : GSON.toJson(e.getAttributeData());

                if (existingId == null) {
                    int i = 1;
                    ins.setObject(i++, homeBankingId);
                    ins.setObject(i++, botJobId);
                    ins.setObject(i++, homeUrlId);
                    ins.setString(i++, page.actualUrl());
                    ins.setString(i++, page.pageKey());
                    ins.setString(i++, elementHash);
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
                    inserted += ins.executeUpdate();
                } else {
                    int i = 1;
                    upd.setObject(i++, homeUrlId);
                    upd.setString(i++, page.actualUrl());
                    upd.setString(i++, page.pageKey());
                    upd.setString(i++, e.getTagName());
                    upd.setString(i++, e.getTypeElement());
                    upd.setString(i++, e.getDefinedName());
                    // The registry owns a client-authored alias after the first scan. A raw or
                    // stale scanner DTO must never erase, restore, or replace that alias during a
                    // re-scan; only the explicit owner-scoped rename mutation may change it.
                    e.setClientNamed(existingClientNamed);
                    upd.setString(i++, existingClientNamed);
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
                    updated += upd.executeUpdate();
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
            String pageUrl,
            ElementDTO element)
            throws SQLException {
        if (element == null
                || element.getXPath() == null
                || element.getXPath().isBlank()
                || element.getCustomXPath() == null
                || element.getCustomXPath().isBlank()) {
            return 0;
        }
        requireScope(homeBankingId, botJobId);
        ScannedPageIdentity page = ScannedPageIdentity.fromLiveUrl(pageUrl);
        String sql = "UPDATE scanned_element SET custom_x_path = ?"
                + " WHERE home_banking_id = ? AND bot_job_id = ? AND page_key = ? AND element_hash = ?";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, element.getCustomXPath());
            statement.setObject(2, homeBankingId);
            statement.setObject(3, botJobId);
            statement.setString(4, page.pageKey());
            statement.setString(5, pageScopedHash(page.pageKey(), element));
            return statement.executeUpdate();
        }
    }

    /** Result of one exact, owner- and page-scoped client alias mutation. */
    public record ClientNamedMutationResult(int affectedRows, ScannedElement element) {}

    /**
     * Rename one authoritative scanner row without changing its locator identity or scan count.
     * The current database row supplies the immutable canonical names used to normalize a cleared
     * override. No matching row is inserted, and a stale/cross-page identity affects zero rows.
     */
    public static ClientNamedMutationResult updateClientNamed(
            Connection conn,
            Integer homeBankingId,
            Integer botJobId,
            String pageUrl,
            ElementDTO identity,
            String requestedClientNamed)
            throws SQLException {
        if (identity == null) return new ClientNamedMutationResult(0, null);
        requireScope(homeBankingId, botJobId);
        ScannedPageIdentity page = ScannedPageIdentity.fromLiveUrl(pageUrl);
        String elementHash = pageScopedHash(page.pageKey(), identity);
        String selectSql = "SELECT * FROM scanned_element"
                + " WHERE home_banking_id = ? AND bot_job_id = ? AND page_key = ? AND element_hash = ?";
        String updateSql = "UPDATE scanned_element SET client_named = ?"
                + " WHERE id = ? AND home_banking_id = ? AND bot_job_id = ? AND page_key = ?";

        boolean previousAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            ScannedElement existing = null;
            try (PreparedStatement select = conn.prepareStatement(selectSql)) {
                select.setObject(1, homeBankingId);
                select.setObject(2, botJobId);
                select.setString(3, page.pageKey());
                select.setString(4, elementHash);
                try (ResultSet rows = select.executeQuery()) {
                    if (rows.next()) existing = map(rows);
                }
            }
            if (existing == null) {
                conn.rollback();
                return new ClientNamedMutationResult(0, null);
            }

            String normalized = normalizeClientNamed(requestedClientNamed, existing);
            int affectedRows;
            try (PreparedStatement update = conn.prepareStatement(updateSql)) {
                update.setString(1, normalized);
                update.setLong(2, existing.getId());
                update.setObject(3, homeBankingId);
                update.setObject(4, botJobId);
                update.setString(5, page.pageKey());
                affectedRows = update.executeUpdate();
            }
            if (affectedRows != 1) {
                conn.rollback();
                return new ClientNamedMutationResult(affectedRows, null);
            }
            existing.setClientNamed(normalized);
            conn.commit();
            return new ClientNamedMutationResult(affectedRows, existing);
        } catch (SQLException failure) {
            conn.rollback();
            throw failure;
        } finally {
            conn.setAutoCommit(previousAutoCommit);
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

    /** Load only observations made on the active Playwright page. */
    public static List<ScannedElement> loadByBotJobAndPage(
            Connection conn, Integer botJobId, String pageUrl) throws SQLException {
        if (botJobId == null || botJobId <= 0) {
            throw new IllegalArgumentException("A Bot Job is required for page-scoped scanner lookup");
        }
        ScannedPageIdentity page = ScannedPageIdentity.fromLiveUrl(pageUrl);
        String sql = "SELECT * FROM scanned_element WHERE bot_job_id = ? AND page_key = ?"
                + " ORDER BY last_scanned_at DESC, id ASC";
        List<ScannedElement> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, botJobId);
            ps.setString(2, page.pageKey());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
        }
        return out;
    }

    /**
     * Load observations for one server-verified owner and the exact active browser page.
     *
     * <p>This is the runtime-healing read seam. Unlike the legacy Bot-Job-only helper, every
     * ownership dimension is part of the query so a stale or spoofed caller cannot borrow locator
     * history from another organization.
     */
    public static List<ScannedElement> loadByOwnerAndPage(
            Connection conn,
            Integer homeBankingId,
            Integer botJobId,
            String pageUrl)
            throws SQLException {
        ScannedPageIdentity page = ScannedPageIdentity.fromLiveUrl(pageUrl);
        return loadByOwnerAndPageKey(conn, homeBankingId, botJobId, page.pageKey());
    }

    /**
     * Load observations using a page key produced by a trusted runtime page observation.
     *
     * <p>The caller must obtain this key from the server-custodied browser runtime, never from a
     * client assertion. Strict validation prevents broad or malformed registry reads.
     */
    public static List<ScannedElement> loadByOwnerAndPageKey(
            Connection conn,
            Integer homeBankingId,
            Integer botJobId,
            String pageKey)
            throws SQLException {
        requireScope(homeBankingId, botJobId);
        if (pageKey == null || !pageKey.matches("url-v1:[0-9a-f]{64}")) {
            throw new IllegalArgumentException("The scanned page key is invalid");
        }
        String sql = "SELECT * FROM scanned_element"
                + " WHERE home_banking_id = ? AND bot_job_id = ? AND page_key = ?"
                + " ORDER BY last_scanned_at DESC, id ASC";
        List<ScannedElement> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, homeBankingId);
            ps.setObject(2, botJobId);
            ps.setString(3, pageKey);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
        }
        return out;
    }

    /**
     * Reload one exact Page Mapping registry row inside the caller's transaction.
     *
     * <p>Every scope dimension is required deliberately. A detached Page Mappings payload is only
     * a revision reference; it must never be allowed to select a row by its globally generated id
     * alone.
     */
    public static Optional<ScannedElement> loadExact(
            Connection conn,
            int homeBankingId,
            int botJobId,
            String pageKey,
            long scannedElementId)
            throws SQLException {
        requireScope(homeBankingId, botJobId);
        if (pageKey == null || pageKey.isBlank() || scannedElementId <= 0) {
            return Optional.empty();
        }
        String sql = "SELECT * FROM scanned_element"
                + " WHERE id = ? AND home_banking_id = ? AND bot_job_id = ? AND page_key = ?";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setLong(1, scannedElementId);
            statement.setInt(2, homeBankingId);
            statement.setInt(3, botJobId);
            statement.setString(4, pageKey);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                ScannedElement element = map(rows);
                if (rows.next()) {
                    throw new SQLException("Duplicate owner-scoped scanned element identity");
                }
                return Optional.of(element);
            }
        }
    }

    /**
     * Update only the client-authored alias when the complete owner, page, identity and scanner
     * revision still match the values verified by the caller.
     *
     * <p>This is deliberately a transaction-neutral compare-and-set seam. The Page Mappings OCR
     * Review service owns the surrounding SERIALIZABLE batch transaction, so a stale row makes
     * the complete batch roll back instead of partially applying aliases.
     */
    public static int updateClientNamedExact(
            Connection conn,
            int homeBankingId,
            int botJobId,
            String pageKey,
            long scannedElementId,
            String expectedElementHash,
            String expectedLastScannedAt,
            int expectedScanCount,
            String expectedClientNamed,
            String replacementClientNamed)
            throws SQLException {
        requireScope(homeBankingId, botJobId);
        if (pageKey == null
                || pageKey.isBlank()
                || scannedElementId <= 0
                || expectedElementHash == null
                || expectedElementHash.isBlank()
                || expectedLastScannedAt == null
                || expectedLastScannedAt.isBlank()
                || expectedScanCount <= 0) {
            return 0;
        }

        String sql = "UPDATE scanned_element SET client_named = ?"
                + " WHERE id = ? AND home_banking_id = ? AND bot_job_id = ? AND page_key = ?"
                + " AND element_hash = ? AND last_scanned_at = ? AND scan_count = ?"
                + " AND ((client_named = ?) OR (client_named IS NULL AND ? IS NULL))";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            bindNullableString(statement, 1, replacementClientNamed);
            statement.setLong(2, scannedElementId);
            statement.setInt(3, homeBankingId);
            statement.setInt(4, botJobId);
            statement.setString(5, pageKey);
            statement.setString(6, expectedElementHash);
            statement.setString(7, expectedLastScannedAt);
            statement.setInt(8, expectedScanCount);
            bindNullableString(statement, 9, expectedClientNamed);
            bindNullableString(statement, 10, expectedClientNamed);
            return statement.executeUpdate();
        }
    }

    private static ScannedElement map(ResultSet rs) throws SQLException {
        ScannedElement s = new ScannedElement();
        s.setId(rs.getLong("id"));
        s.setHomeBankingId((Integer) rs.getObject("home_banking_id"));
        s.setBotJobId((Integer) rs.getObject("bot_job_id"));
        s.setHomeUrlId((Integer) rs.getObject("home_url_id"));
        s.setPageUrl(rs.getString("page_url"));
        s.setPageKey(rs.getString("page_key"));
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

    private static void bindNullableString(
            PreparedStatement statement, int parameter, String value) throws SQLException {
        if (value == null) statement.setNull(parameter, Types.VARCHAR);
        else statement.setString(parameter, value);
    }

    private static String normalizeClientNamed(String requested, ScannedElement existing) {
        String normalized = requested == null ? "" : requested.trim();
        if (normalized.isEmpty()
                || normalized.equals(existing.getDefinedName())
                || normalized.equals(existing.getSomeText())
                || normalized.equals(existing.getTagName())) {
            return null;
        }
        return normalized;
    }

    private static void requireScope(Integer homeBankingId, Integer botJobId) {
        if (homeBankingId == null || homeBankingId <= 0 || botJobId == null || botJobId <= 0) {
            throw new IllegalArgumentException(
                    "Organization and Bot Job are required for scanner persistence");
        }
    }
}

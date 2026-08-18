package com.allinweb.ch.db.migrations;

import com.allinweb.ch.db.MigrationRunner.Migration;
import com.allinweb.ch.db.ScannedElementRepository;
import com.allinweb.ch.db.ScannedPageIdentity;
import com.allinweb.ch.model.ElementDTO;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Adds exact-page identity to the scanner repository.
 *
 * <p>The original table has a cross-dialect unique constraint on {@code
 * (home_banking_id, bot_job_id, element_hash)} that cannot be safely removed in-place on SQLite or
 * Access. This migration therefore changes {@code element_hash} to a page-scoped hash while also
 * storing the independently queryable {@code page_key}. The existing constraint then remains
 * effective and identical locators on different pages no longer collide.
 *
 * <p>Every operation is metadata-guarded or recomputed from source columns, making the migration
 * resumable when a database engine commits DDL independently from the migration ledger.
 */
@Slf4j
public final class M20260724_ScannedElementPageScope implements Migration {

    private static final String NAME = "2026-07-24__scanned_element_page_scope";
    private static final String PAGE_UNIQUE_INDEX = "uq_scanned_scope_page_hash";
    private static final String PAGE_NAME_INDEX = "idx_scanned_page_name";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void apply(Connection conn, String dialect) throws SQLException {
        if (!tableExists(conn, "scanned_element")) {
            throw new SQLException("scanned_element must exist before applying " + NAME);
        }

        if (!columnExists(conn, "scanned_element", "page_key")) {
            exec(conn, addPageKeySql(dialect));
        }

        backfillPageIdentity(conn);

        if (!indexExists(conn, "scanned_element", PAGE_UNIQUE_INDEX)) {
            exec(
                    conn,
                    "CREATE UNIQUE INDEX "
                            + PAGE_UNIQUE_INDEX
                            + " ON scanned_element "
                            + "(home_banking_id, bot_job_id, page_key, element_hash)");
        }
        if (!indexExists(conn, "scanned_element", PAGE_NAME_INDEX)) {
            exec(
                    conn,
                    "CREATE INDEX "
                            + PAGE_NAME_INDEX
                            + " ON scanned_element "
                            + "(home_banking_id, bot_job_id, page_key, defined_name)");
        }

        verify(conn);
        log.info("{} - page-scoped scanner identity is ready", NAME);
    }

    private static String addPageKeySql(String dialect) {
        if ("Postgres".equalsIgnoreCase(dialect)) {
            return "ALTER TABLE scanned_element ADD COLUMN page_key VARCHAR(71)";
        }
        if ("SQLServer".equalsIgnoreCase(dialect)) {
            return "ALTER TABLE scanned_element ADD page_key NVARCHAR(71)";
        }
        if ("TEXT".equalsIgnoreCase(dialect)) {
            return "ALTER TABLE scanned_element ADD COLUMN page_key TEXT";
        }
        return "ALTER TABLE scanned_element ADD COLUMN page_key VARCHAR(71)";
    }

    private static void backfillPageIdentity(Connection conn) throws SQLException {
        String selectSql = "SELECT id, page_url, x_path, iframe_xpath, attrib_id, css_selector"
                + " FROM scanned_element";
        String updateSql = "UPDATE scanned_element SET page_key = ?, element_hash = ? WHERE id = ?";
        List<PageElementRow> legacyRows = new ArrayList<>();

        try (Statement statement = conn.createStatement();
                ResultSet rows = statement.executeQuery(selectSql)) {
            while (rows.next()) {
                legacyRows.add(new PageElementRow(
                        rows.getLong("id"),
                        rows.getString("page_url"),
                        rows.getString("x_path"),
                        rows.getString("iframe_xpath"),
                        rows.getString("attrib_id"),
                        rows.getString("css_selector")));
            }
        }

        try (PreparedStatement update = conn.prepareStatement(updateSql)) {
            for (PageElementRow row : legacyRows) {
                ElementDTO element = new ElementDTO();
                element.setXPath(row.xPath());
                element.setIFrameXPath(row.iframeXPath());
                element.setAttribId(row.attribId());
                element.setCssSelector(row.cssSelector());

                ScannedPageIdentity page = ScannedPageIdentity.fromStoredUrl(row.pageUrl());
                update.setString(1, page.pageKey());
                update.setString(2, ScannedElementRepository.pageScopedHash(page.pageKey(), element));
                update.setLong(3, row.id());
                update.executeUpdate();
            }
        }
    }

    private record PageElementRow(
            long id,
            String pageUrl,
            String xPath,
            String iframeXPath,
            String attribId,
            String cssSelector) {}

    private static void verify(Connection conn) throws SQLException {
        String sql = "SELECT COUNT(*) FROM scanned_element"
                + " WHERE page_key IS NULL OR page_key = '' OR element_hash IS NULL OR element_hash = ''";
        try (Statement statement = conn.createStatement();
                ResultSet rows = statement.executeQuery(sql)) {
            if (!rows.next() || rows.getLong(1) != 0L) {
                throw new SQLException(NAME + " left scanner rows without page identity");
            }
        }
        if (!indexExists(conn, "scanned_element", PAGE_UNIQUE_INDEX)
                || !indexExists(conn, "scanned_element", PAGE_NAME_INDEX)) {
            throw new SQLException(NAME + " did not create the required page-scoped indexes");
        }
        try (Statement statement = conn.createStatement();
                ResultSet rows =
                        statement.executeQuery("SELECT page_key, element_hash FROM scanned_element")) {
            while (rows.next()) {
                String pageKey = rows.getString("page_key");
                String elementHash = rows.getString("element_hash");
                if (pageKey == null
                        || !pageKey.matches("url-v1:[0-9a-f]{64}")
                        || elementHash == null
                        || !elementHash.matches("[0-9a-f]{64}")) {
                    throw new SQLException(NAME + " produced an invalid scanner identity");
                }
            }
        }
    }

    private static boolean tableExists(Connection conn, String tableName) throws SQLException {
        DatabaseMetaData metadata = conn.getMetaData();
        try (ResultSet tables = metadata.getTables(null, null, null, new String[] {"TABLE"})) {
            while (tables.next()) {
                if (tableName.equalsIgnoreCase(tables.getString("TABLE_NAME"))) return true;
            }
        }
        return false;
    }

    private static boolean columnExists(Connection conn, String tableName, String columnName)
            throws SQLException {
        DatabaseMetaData metadata = conn.getMetaData();
        try (ResultSet columns = metadata.getColumns(null, null, null, null)) {
            while (columns.next()) {
                if (tableName.equalsIgnoreCase(columns.getString("TABLE_NAME"))
                        && columnName.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean indexExists(Connection conn, String tableName, String indexName)
            throws SQLException {
        DatabaseMetaData metadata = conn.getMetaData();
        try (ResultSet indexes = metadata.getIndexInfo(null, null, tableName, false, false)) {
            while (indexes.next()) {
                if (indexName.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) return true;
            }
        }
        try (ResultSet indexes =
                metadata.getIndexInfo(null, null, tableName.toUpperCase(), false, false)) {
            while (indexes.next()) {
                if (indexName.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) return true;
            }
        }
        return false;
    }

    private static void exec(Connection conn, String sql) throws SQLException {
        log.info("{} - exec: {}", NAME, sql);
        try (Statement statement = conn.createStatement()) {
            statement.executeUpdate(sql);
        }
    }
}

package com.allinweb.ch.db.migrations;

import com.allinweb.ch.db.MigrationRunner.Migration;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import lombok.extern.slf4j.Slf4j;

/**
 * Roadmap 3 Phase 3b — locator persistence tables.
 *
 * <ul>
 *   <li>{@code element_locator} — one row per (homebanking_id, home_url_id, defined_name).
 *       Frozen {@code *_original} columns capture the first-time state; {@code *_current}
 *       columns track the most recent pick. Used by Phase 3c recovery to find an element
 *       when its xPath drifts.</li>
 *   <li>{@code element_locator_rename} — append-only audit trail. Phase 3c writes a row
 *       here every time the recovery service detects drift. Phase 3b creates the table
 *       only; nothing writes to it yet.</li>
 * </ul>
 */
@Slf4j
public class M20260428_ElementLocator implements Migration {

    private static final String NAME = "2026-04-28__element_locator";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void apply(Connection conn, String dialect) throws SQLException {
        log.info("{} — applying on dialect={}", NAME, dialect);
        createLocatorTable(conn, dialect);
        createRenameTable(conn, dialect);
    }

    // ── element_locator ───────────────────────────────────────────────────

    private void createLocatorTable(Connection conn, String dialect) throws SQLException {
        if (tableExists(conn, "element_locator")) return;

        String ddl;
        switch (dialect) {
            case "Postgres":
                ddl = "CREATE TABLE element_locator ("
                        + "id BIGSERIAL PRIMARY KEY,"
                        + "homebanking_id INTEGER NULL,"
                        + "home_url_id INTEGER NULL,"
                        + "defined_name VARCHAR(255) NOT NULL,"
                        // frozen
                        + "x_path_original TEXT NOT NULL,"
                        + "custom_x_path_original TEXT,"
                        + "css_selector_original VARCHAR(512),"
                        + "attrib_id_original VARCHAR(255),"
                        + "attrib_name_original VARCHAR(255),"
                        + "tag_name_original VARCHAR(64),"
                        + "some_text_original VARCHAR(512),"
                        + "coords_original VARCHAR(64),"
                        + "ocr_text_original VARCHAR(512),"
                        + "iframe_xpath_original TEXT,"
                        + "shadow_host_original TEXT,"
                        // mutable
                        + "x_path_current TEXT,"
                        + "custom_x_path_current TEXT,"
                        + "css_selector_current VARCHAR(512),"
                        + "attrib_id_current VARCHAR(255),"
                        + "attrib_name_current VARCHAR(255),"
                        + "tag_name_current VARCHAR(64),"
                        + "some_text_current VARCHAR(512),"
                        + "coords_current VARCHAR(64),"
                        + "ocr_text_current VARCHAR(512),"
                        + "iframe_xpath_current TEXT,"
                        + "shadow_host_current TEXT,"
                        + "pick_count INTEGER NOT NULL DEFAULT 0,"
                        + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                        + "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                        + "CONSTRAINT uq_locator_scope_name UNIQUE (homebanking_id, home_url_id, defined_name)"
                        + ")";
                break;
            case "SQLServer":
                ddl = "CREATE TABLE element_locator ("
                        + "id BIGINT IDENTITY(1,1) PRIMARY KEY,"
                        + "homebanking_id INT NULL,"
                        + "home_url_id INT NULL,"
                        + "defined_name NVARCHAR(255) NOT NULL,"
                        + "x_path_original NVARCHAR(MAX) NOT NULL,"
                        + "custom_x_path_original NVARCHAR(MAX),"
                        + "css_selector_original NVARCHAR(512),"
                        + "attrib_id_original NVARCHAR(255),"
                        + "attrib_name_original NVARCHAR(255),"
                        + "tag_name_original NVARCHAR(64),"
                        + "some_text_original NVARCHAR(512),"
                        + "coords_original NVARCHAR(64),"
                        + "ocr_text_original NVARCHAR(512),"
                        + "iframe_xpath_original NVARCHAR(MAX),"
                        + "shadow_host_original NVARCHAR(MAX),"
                        + "x_path_current NVARCHAR(MAX),"
                        + "custom_x_path_current NVARCHAR(MAX),"
                        + "css_selector_current NVARCHAR(512),"
                        + "attrib_id_current NVARCHAR(255),"
                        + "attrib_name_current NVARCHAR(255),"
                        + "tag_name_current NVARCHAR(64),"
                        + "some_text_current NVARCHAR(512),"
                        + "coords_current NVARCHAR(64),"
                        + "ocr_text_current NVARCHAR(512),"
                        + "iframe_xpath_current NVARCHAR(MAX),"
                        + "shadow_host_current NVARCHAR(MAX),"
                        + "pick_count INT NOT NULL DEFAULT 0,"
                        + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP,"
                        + "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,"
                        + "CONSTRAINT uq_locator_scope_name UNIQUE (homebanking_id, home_url_id, defined_name)"
                        + ")";
                break;
            case "TEXT":
                ddl = "CREATE TABLE element_locator ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "homebanking_id INTEGER,"
                        + "home_url_id INTEGER,"
                        + "defined_name TEXT NOT NULL,"
                        + "x_path_original TEXT NOT NULL,"
                        + "custom_x_path_original TEXT,"
                        + "css_selector_original TEXT,"
                        + "attrib_id_original TEXT,"
                        + "attrib_name_original TEXT,"
                        + "tag_name_original TEXT,"
                        + "some_text_original TEXT,"
                        + "coords_original TEXT,"
                        + "ocr_text_original TEXT,"
                        + "iframe_xpath_original TEXT,"
                        + "shadow_host_original TEXT,"
                        + "x_path_current TEXT,"
                        + "custom_x_path_current TEXT,"
                        + "css_selector_current TEXT,"
                        + "attrib_id_current TEXT,"
                        + "attrib_name_current TEXT,"
                        + "tag_name_current TEXT,"
                        + "some_text_current TEXT,"
                        + "coords_current TEXT,"
                        + "ocr_text_current TEXT,"
                        + "iframe_xpath_current TEXT,"
                        + "shadow_host_current TEXT,"
                        + "pick_count INTEGER NOT NULL DEFAULT 0,"
                        + "created_at TEXT DEFAULT CURRENT_TIMESTAMP,"
                        + "updated_at TEXT DEFAULT CURRENT_TIMESTAMP,"
                        + "UNIQUE (homebanking_id, home_url_id, defined_name)"
                        + ")";
                break;
            default: // Access
                ddl = "CREATE TABLE element_locator ("
                        + "id AUTOINCREMENT PRIMARY KEY,"
                        + "homebanking_id LONG,"
                        + "home_url_id LONG,"
                        + "defined_name VARCHAR(255) NOT NULL,"
                        + "x_path_original MEMO NOT NULL,"
                        + "custom_x_path_original MEMO,"
                        + "css_selector_original VARCHAR(255),"
                        + "attrib_id_original VARCHAR(255),"
                        + "attrib_name_original VARCHAR(255),"
                        + "tag_name_original VARCHAR(64),"
                        + "some_text_original VARCHAR(255),"
                        + "coords_original VARCHAR(64),"
                        + "ocr_text_original VARCHAR(255),"
                        + "iframe_xpath_original MEMO,"
                        + "shadow_host_original MEMO,"
                        + "x_path_current MEMO,"
                        + "custom_x_path_current MEMO,"
                        + "css_selector_current VARCHAR(255),"
                        + "attrib_id_current VARCHAR(255),"
                        + "attrib_name_current VARCHAR(255),"
                        + "tag_name_current VARCHAR(64),"
                        + "some_text_current VARCHAR(255),"
                        + "coords_current VARCHAR(64),"
                        + "ocr_text_current VARCHAR(255),"
                        + "iframe_xpath_current MEMO,"
                        + "shadow_host_current MEMO,"
                        + "pick_count LONG NOT NULL,"
                        + "created_at DATETIME,"
                        + "updated_at DATETIME,"
                        + "CONSTRAINT uq_locator_scope_name UNIQUE (homebanking_id, home_url_id, defined_name)"
                        + ")";
                break;
        }
        exec(conn, ddl);
    }

    // ── element_locator_rename ───────────────────────────────────────────

    private void createRenameTable(Connection conn, String dialect) throws SQLException {
        if (tableExists(conn, "element_locator_rename")) return;

        String ddl;
        switch (dialect) {
            case "Postgres":
                ddl = "CREATE TABLE element_locator_rename ("
                        + "id BIGSERIAL PRIMARY KEY,"
                        + "locator_id BIGINT NOT NULL,"
                        + "observed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                        + "change_type VARCHAR(32) NOT NULL,"
                        + "field_name VARCHAR(64),"
                        + "old_value TEXT,"
                        + "new_value TEXT,"
                        + "match_confidence DECIMAL(5,2),"
                        + "recovery_strategy VARCHAR(64),"
                        + "CONSTRAINT fk_rename_locator FOREIGN KEY (locator_id) "
                        + "REFERENCES element_locator(id) ON DELETE CASCADE"
                        + ")";
                break;
            case "SQLServer":
                ddl = "CREATE TABLE element_locator_rename ("
                        + "id BIGINT IDENTITY(1,1) PRIMARY KEY,"
                        + "locator_id BIGINT NOT NULL,"
                        + "observed_at DATETIME DEFAULT CURRENT_TIMESTAMP,"
                        + "change_type NVARCHAR(32) NOT NULL,"
                        + "field_name NVARCHAR(64),"
                        + "old_value NVARCHAR(MAX),"
                        + "new_value NVARCHAR(MAX),"
                        + "match_confidence DECIMAL(5,2),"
                        + "recovery_strategy NVARCHAR(64),"
                        + "CONSTRAINT fk_rename_locator FOREIGN KEY (locator_id) "
                        + "REFERENCES element_locator(id) ON DELETE CASCADE"
                        + ")";
                break;
            case "TEXT":
                ddl = "CREATE TABLE element_locator_rename ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "locator_id INTEGER NOT NULL,"
                        + "observed_at TEXT DEFAULT CURRENT_TIMESTAMP,"
                        + "change_type TEXT NOT NULL,"
                        + "field_name TEXT,"
                        + "old_value TEXT,"
                        + "new_value TEXT,"
                        + "match_confidence REAL,"
                        + "recovery_strategy TEXT,"
                        + "FOREIGN KEY (locator_id) REFERENCES element_locator(id) ON DELETE CASCADE"
                        + ")";
                break;
            default: // Access
                ddl = "CREATE TABLE element_locator_rename ("
                        + "id AUTOINCREMENT PRIMARY KEY,"
                        + "locator_id LONG NOT NULL,"
                        + "observed_at DATETIME,"
                        + "change_type VARCHAR(32) NOT NULL,"
                        + "field_name VARCHAR(64),"
                        + "old_value MEMO,"
                        + "new_value MEMO,"
                        + "match_confidence DOUBLE,"
                        + "recovery_strategy VARCHAR(64)"
                        + ")";
                break;
        }
        exec(conn, ddl);
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private boolean tableExists(Connection conn, String tableName) throws SQLException {
        DatabaseMetaData md = conn.getMetaData();
        try (ResultSet rs = md.getTables(null, null, tableName, new String[] {"TABLE"})) {
            if (rs.next()) return true;
        }
        try (ResultSet rs = md.getTables(null, null, tableName.toLowerCase(), new String[] {"TABLE"})) {
            return rs.next();
        }
    }

    private void exec(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement()) {
            log.info("{} — exec: {}", NAME, sql);
            st.executeUpdate(sql);
        }
    }
}

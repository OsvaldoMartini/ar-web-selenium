package com.allinweb.ch.db.migrations;

import com.allinweb.ch.db.MigrationRunner.Migration;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import lombok.extern.slf4j.Slf4j;

/**
 * Group B — scanned-element registry (source of truth).
 *
 * <p>{@code scanned_element} holds EVERY element seen by the scanner, scoped by organization
 * ({@code home_banking_id}) and {@code bot_job_id}. Unlike {@code element_locator} (one row per
 * defined_name), this table keeps multiple distinct elements even when they share a name — each
 * row is identified by a stable {@code element_hash} derived from its locator identity
 * (xpath + iframe + attrib_id + css_selector), so "same name, different xPath/identifier" cases
 * are disambiguated precisely.
 *
 * <p>Every re-scan upserts on {@code (home_banking_id, bot_job_id, element_hash)}: bumping
 * {@code scan_count}, refreshing the mutable locator/OCR fields, and stamping {@code last_scanned_at}
 * so we always know when an element was last observed. OCR results (ocr_text / quality / confidence)
 * are stored here too and used to correct element names. Bot-job execution validates its target
 * against this registry.
 */
@Slf4j
public class M20260704_ScannedElement implements Migration {

    private static final String NAME = "2026-07-04__scanned_element";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void apply(Connection conn, String dialect) throws SQLException {
        log.info("{} — applying on dialect={}", NAME, dialect);
        if (tableExists(conn, "scanned_element")) return;

        String ddl;
        switch (dialect) {
            case "Postgres":
                ddl = "CREATE TABLE scanned_element ("
                        + "id BIGSERIAL PRIMARY KEY,"
                        + "home_banking_id INTEGER NULL,"
                        + "bot_job_id INTEGER NULL,"
                        + "home_url_id INTEGER NULL,"
                        + "page_url TEXT,"
                        + "element_hash VARCHAR(64) NOT NULL,"
                        + "tag_name VARCHAR(64),"
                        + "type_element VARCHAR(64),"
                        + "defined_name VARCHAR(255),"
                        + "client_named VARCHAR(255),"
                        + "some_text VARCHAR(512),"
                        + "x_path TEXT,"
                        + "custom_x_path TEXT,"
                        + "css_selector VARCHAR(512),"
                        + "attrib_id VARCHAR(255),"
                        + "attrib_name VARCHAR(255),"
                        + "coordinates VARCHAR(64),"
                        + "iframe_xpath TEXT,"
                        + "shadow_host TEXT,"
                        + "shadow_root TEXT,"
                        + "attribute_data TEXT,"
                        + "ocr_text VARCHAR(512),"
                        + "ocr_match_quality VARCHAR(32),"
                        + "ocr_confidence DOUBLE PRECISION,"
                        + "scan_count INTEGER NOT NULL DEFAULT 0,"
                        + "first_scanned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                        + "last_scanned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                        + "CONSTRAINT uq_scanned_scope_hash UNIQUE (home_banking_id, bot_job_id, element_hash)"
                        + ")";
                break;
            case "SQLServer":
                ddl = "CREATE TABLE scanned_element ("
                        + "id BIGINT IDENTITY(1,1) PRIMARY KEY,"
                        + "home_banking_id INT NULL,"
                        + "bot_job_id INT NULL,"
                        + "home_url_id INT NULL,"
                        + "page_url NVARCHAR(MAX),"
                        + "element_hash NVARCHAR(64) NOT NULL,"
                        + "tag_name NVARCHAR(64),"
                        + "type_element NVARCHAR(64),"
                        + "defined_name NVARCHAR(255),"
                        + "client_named NVARCHAR(255),"
                        + "some_text NVARCHAR(512),"
                        + "x_path NVARCHAR(MAX),"
                        + "custom_x_path NVARCHAR(MAX),"
                        + "css_selector NVARCHAR(512),"
                        + "attrib_id NVARCHAR(255),"
                        + "attrib_name NVARCHAR(255),"
                        + "coordinates NVARCHAR(64),"
                        + "iframe_xpath NVARCHAR(MAX),"
                        + "shadow_host NVARCHAR(MAX),"
                        + "shadow_root NVARCHAR(MAX),"
                        + "attribute_data NVARCHAR(MAX),"
                        + "ocr_text NVARCHAR(512),"
                        + "ocr_match_quality NVARCHAR(32),"
                        + "ocr_confidence FLOAT,"
                        + "scan_count INT NOT NULL DEFAULT 0,"
                        + "first_scanned_at DATETIME DEFAULT CURRENT_TIMESTAMP,"
                        + "last_scanned_at DATETIME DEFAULT CURRENT_TIMESTAMP,"
                        + "CONSTRAINT uq_scanned_scope_hash UNIQUE (home_banking_id, bot_job_id, element_hash)"
                        + ")";
                break;
            case "TEXT":
                ddl = "CREATE TABLE scanned_element ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "home_banking_id INTEGER,"
                        + "bot_job_id INTEGER,"
                        + "home_url_id INTEGER,"
                        + "page_url TEXT,"
                        + "element_hash TEXT NOT NULL,"
                        + "tag_name TEXT,"
                        + "type_element TEXT,"
                        + "defined_name TEXT,"
                        + "client_named TEXT,"
                        + "some_text TEXT,"
                        + "x_path TEXT,"
                        + "custom_x_path TEXT,"
                        + "css_selector TEXT,"
                        + "attrib_id TEXT,"
                        + "attrib_name TEXT,"
                        + "coordinates TEXT,"
                        + "iframe_xpath TEXT,"
                        + "shadow_host TEXT,"
                        + "shadow_root TEXT,"
                        + "attribute_data TEXT,"
                        + "ocr_text TEXT,"
                        + "ocr_match_quality TEXT,"
                        + "ocr_confidence REAL,"
                        + "scan_count INTEGER NOT NULL DEFAULT 0,"
                        + "first_scanned_at TEXT DEFAULT CURRENT_TIMESTAMP,"
                        + "last_scanned_at TEXT DEFAULT CURRENT_TIMESTAMP,"
                        + "UNIQUE (home_banking_id, bot_job_id, element_hash)"
                        + ")";
                break;
            default: // Access
                ddl = "CREATE TABLE scanned_element ("
                        + "id AUTOINCREMENT PRIMARY KEY,"
                        + "home_banking_id LONG,"
                        + "bot_job_id LONG,"
                        + "home_url_id LONG,"
                        + "page_url MEMO,"
                        + "element_hash VARCHAR(64) NOT NULL,"
                        + "tag_name VARCHAR(64),"
                        + "type_element VARCHAR(64),"
                        + "defined_name VARCHAR(255),"
                        + "client_named VARCHAR(255),"
                        + "some_text VARCHAR(255),"
                        + "x_path MEMO,"
                        + "custom_x_path MEMO,"
                        + "css_selector VARCHAR(255),"
                        + "attrib_id VARCHAR(255),"
                        + "attrib_name VARCHAR(255),"
                        + "coordinates VARCHAR(64),"
                        + "iframe_xpath MEMO,"
                        + "shadow_host MEMO,"
                        + "shadow_root MEMO,"
                        + "attribute_data MEMO,"
                        + "ocr_text VARCHAR(255),"
                        + "ocr_match_quality VARCHAR(32),"
                        + "ocr_confidence DOUBLE,"
                        + "scan_count LONG NOT NULL,"
                        + "first_scanned_at DATETIME,"
                        + "last_scanned_at DATETIME,"
                        + "CONSTRAINT uq_scanned_scope_hash UNIQUE (home_banking_id, bot_job_id, element_hash)"
                        + ")";
                break;
        }
        exec(conn, ddl);

        // Helpful lookup index for re-scan / execution validation by scope.
        String idxDialect = dialect == null ? "" : dialect;
        if (!"Access".equalsIgnoreCase(idxDialect)) {
            exec(conn, "CREATE INDEX idx_scanned_scope ON scanned_element (home_banking_id, bot_job_id, defined_name)");
        }
    }

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

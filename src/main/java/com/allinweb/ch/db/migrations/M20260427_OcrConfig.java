package com.allinweb.ch.db.migrations;

import com.allinweb.ch.db.MigrationRunner.Migration;
import com.allinweb.ch.model.OcrConfigDefaults;
import com.allinweb.ch.model.OcrConfigParam;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import lombok.extern.slf4j.Slf4j;

/**
 * Create {@code ocr_config_profile} and {@code ocr_config_param} tables for the
 * OCR configuration system (Roadmap 4). Seeds one {@code is_default=true} profile
 * named {@code "default"} with the current hardcoded values so enabling the feature
 * is a no-op until a user creates an override profile.
 */
@Slf4j
public class M20260427_OcrConfig implements Migration {

    private static final String NAME = "2026-04-27__ocr_config";
    private static final String DEFAULT_PROFILE_NAME = "default";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void apply(Connection conn, String dialect) throws SQLException {
        log.info("{} — applying on dialect={}", NAME, dialect);

        boolean createdProfile = createProfileTable(conn, dialect);
        boolean createdParam = createParamTable(conn, dialect);

        if (createdProfile || !defaultProfileExists(conn)) {
            int profileId = insertDefaultProfile(conn, dialect);
            seedDefaultParams(conn, profileId);
            log.info("{} — default profile seeded with id={}", NAME, profileId);
        }
    }

    // ── Schema ────────────────────────────────────────────────────────────

    private boolean createProfileTable(Connection conn, String dialect) throws SQLException {
        if (tableExists(conn, "ocr_config_profile")) return false;

        String ddl;
        switch (dialect) {
            case "Postgres":
                ddl = "CREATE TABLE ocr_config_profile ("
                        + "id SERIAL PRIMARY KEY,"
                        + "name VARCHAR(128) NOT NULL,"
                        + "description TEXT,"
                        + "homebanking_id INTEGER NULL,"
                        + "home_url_id INTEGER NULL,"
                        + "is_default BOOLEAN NOT NULL DEFAULT FALSE,"
                        + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                        + "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                        + "CONSTRAINT uq_ocr_profile_name UNIQUE (name)"
                        + ")";
                break;
            case "SQLServer":
                ddl = "CREATE TABLE ocr_config_profile ("
                        + "id INT IDENTITY(1,1) PRIMARY KEY,"
                        + "name NVARCHAR(128) NOT NULL,"
                        + "description NVARCHAR(MAX),"
                        + "homebanking_id INT NULL,"
                        + "home_url_id INT NULL,"
                        + "is_default BIT NOT NULL DEFAULT 0,"
                        + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP,"
                        + "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,"
                        + "CONSTRAINT uq_ocr_profile_name UNIQUE (name)"
                        + ")";
                break;
            case "TEXT":
                ddl = "CREATE TABLE ocr_config_profile ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "name TEXT NOT NULL UNIQUE,"
                        + "description TEXT,"
                        + "homebanking_id INTEGER,"
                        + "home_url_id INTEGER,"
                        + "is_default INTEGER NOT NULL DEFAULT 0,"
                        + "created_at TEXT DEFAULT CURRENT_TIMESTAMP,"
                        + "updated_at TEXT DEFAULT CURRENT_TIMESTAMP"
                        + ")";
                break;
            default: // Access
                ddl = "CREATE TABLE ocr_config_profile ("
                        + "id AUTOINCREMENT PRIMARY KEY,"
                        + "name VARCHAR(128) NOT NULL,"
                        + "description MEMO,"
                        + "homebanking_id LONG,"
                        + "home_url_id LONG,"
                        + "is_default YESNO NOT NULL,"
                        + "created_at DATETIME,"
                        + "updated_at DATETIME,"
                        + "CONSTRAINT uq_ocr_profile_name UNIQUE (name)"
                        + ")";
                break;
        }
        exec(conn, ddl);
        return true;
    }

    private boolean createParamTable(Connection conn, String dialect) throws SQLException {
        if (tableExists(conn, "ocr_config_param")) return false;

        String ddl;
        switch (dialect) {
            case "Postgres":
                ddl = "CREATE TABLE ocr_config_param ("
                        + "id SERIAL PRIMARY KEY,"
                        + "profile_id INTEGER NOT NULL,"
                        + "category VARCHAR(64) NOT NULL,"
                        + "name VARCHAR(128) NOT NULL,"
                        + "value_type VARCHAR(16) NOT NULL,"
                        + "value TEXT NOT NULL,"
                        + "CONSTRAINT fk_ocr_param_profile FOREIGN KEY (profile_id)"
                        + "  REFERENCES ocr_config_profile(id) ON DELETE CASCADE,"
                        + "CONSTRAINT uq_ocr_param UNIQUE (profile_id, category, name)"
                        + ")";
                break;
            case "SQLServer":
                ddl = "CREATE TABLE ocr_config_param ("
                        + "id INT IDENTITY(1,1) PRIMARY KEY,"
                        + "profile_id INT NOT NULL,"
                        + "category NVARCHAR(64) NOT NULL,"
                        + "name NVARCHAR(128) NOT NULL,"
                        + "value_type NVARCHAR(16) NOT NULL,"
                        + "value NVARCHAR(MAX) NOT NULL,"
                        + "CONSTRAINT fk_ocr_param_profile FOREIGN KEY (profile_id)"
                        + "  REFERENCES ocr_config_profile(id) ON DELETE CASCADE,"
                        + "CONSTRAINT uq_ocr_param UNIQUE (profile_id, category, name)"
                        + ")";
                break;
            case "TEXT":
                ddl = "CREATE TABLE ocr_config_param ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "profile_id INTEGER NOT NULL,"
                        + "category TEXT NOT NULL,"
                        + "name TEXT NOT NULL,"
                        + "value_type TEXT NOT NULL,"
                        + "value TEXT NOT NULL,"
                        + "UNIQUE (profile_id, category, name),"
                        + "FOREIGN KEY (profile_id) REFERENCES ocr_config_profile(id) ON DELETE CASCADE"
                        + ")";
                break;
            default: // Access
                ddl = "CREATE TABLE ocr_config_param ("
                        + "id AUTOINCREMENT PRIMARY KEY,"
                        + "profile_id LONG NOT NULL,"
                        + "category VARCHAR(64) NOT NULL,"
                        + "name VARCHAR(128) NOT NULL,"
                        + "value_type VARCHAR(16) NOT NULL,"
                        + "value MEMO NOT NULL,"
                        + "CONSTRAINT uq_ocr_param UNIQUE (profile_id, category, name)"
                        + ")";
                break;
        }
        exec(conn, ddl);
        return true;
    }

    // ── Seed ──────────────────────────────────────────────────────────────

    private boolean defaultProfileExists(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM ocr_config_profile WHERE name = ?")) {
            ps.setString(1, DEFAULT_PROFILE_NAME);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private int insertDefaultProfile(Connection conn, String dialect) throws SQLException {
        boolean trueVal = !"TEXT".equals(dialect); // SQLite uses 0/1; others handle boolean via driver
        String sql;
        if ("Access".equals(dialect)) {
            sql = "INSERT INTO ocr_config_profile (name, description, is_default, created_at, updated_at) "
                    + "VALUES (?, ?, ?, NOW(), NOW())";
        } else if ("TEXT".equals(dialect) || "Postgres".equals(dialect) || "SQLServer".equals(dialect)) {
            sql = "INSERT INTO ocr_config_profile (name, description, is_default) VALUES (?, ?, ?)";
        } else {
            sql = "INSERT INTO ocr_config_profile (name, description, is_default) VALUES (?, ?, ?)";
        }

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, DEFAULT_PROFILE_NAME);
            ps.setString(2, "Seeded by migration; reproduces Phase 2a hardcoded behavior.");
            if ("TEXT".equals(dialect)) {
                ps.setInt(3, 1);
            } else {
                ps.setBoolean(3, true);
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }

        // Fallback — driver did not return keys. Select by name.
        try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM ocr_config_profile WHERE name = ?")) {
            ps.setString(1, DEFAULT_PROFILE_NAME);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("Failed to obtain id of inserted default OCR profile");
    }

    private void seedDefaultParams(Connection conn, int profileId) throws SQLException {
        String sql =
                "INSERT INTO ocr_config_param (profile_id, category, name, value_type, value) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (OcrConfigParam canon : OcrConfigDefaults.CANONICAL) {
                addParam(ps, profileId, canon.getCategory(), canon.getName(), canon.getValueType(), canon.getValue());
            }
            ps.executeBatch();
        }
    }

    private void addParam(PreparedStatement ps, int profileId, String category, String name, String type, String value)
            throws SQLException {
        ps.setInt(1, profileId);
        ps.setString(2, category);
        ps.setString(3, name);
        ps.setString(4, type);
        ps.setString(5, value);
        ps.addBatch();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private boolean tableExists(Connection conn, String tableName) throws SQLException {
        DatabaseMetaData md = conn.getMetaData();
        try (ResultSet rs = md.getTables(null, null, tableName, new String[] {"TABLE"})) {
            if (rs.next()) return true;
        }
        // Postgres sometimes wants lowercase; retry with metadata default.
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

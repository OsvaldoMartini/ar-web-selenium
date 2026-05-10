package com.allinweb.ch.db.migrations;

import com.allinweb.ch.db.MigrationRunner.Migration;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Phase 1a of ROADMAP_9 — introduces named groups (Use Cases) above the
 * {@code use_case_field_mapping} table created in M20260510.
 *
 * <p>Three operations, all idempotent:
 * <ol>
 *   <li>Create {@code use_case (id, bot_job_id, name, description, timestamps)}.
 *       FK to {@code bot_job(id)} CASCADE in dialects that allow it inline.</li>
 *   <li>Add nullable {@code use_case_id} column to {@code use_case_field_mapping}.
 *       (No FK enforced via ALTER — SQLite and Access don't support it; we keep
 *       referential integrity in application code.)</li>
 *   <li>Backfill: for every distinct {@code bot_job_id} that has rows in
 *       {@code use_case_field_mapping} but no "Default" use case, create one
 *       and re-point existing mappings at it. Pure Java loop — portable.</li>
 * </ol>
 */
@Slf4j
public class M20260511_UseCaseTable implements Migration {

    private static final String NAME = "2026-05-11__use_case_table";
    private static final String TABLE_USE_CASE = "use_case";
    private static final String TABLE_MAPPING = "use_case_field_mapping";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void apply(Connection conn, String dialect) throws SQLException {
        log.info("{} — applying on dialect={}", NAME, dialect);
        createUseCaseTable(conn, dialect);
        addUseCaseIdColumn(conn, dialect);
        backfillDefaultUseCases(conn);
    }

    private void createUseCaseTable(Connection conn, String dialect) throws SQLException {
        if (tableExists(conn, TABLE_USE_CASE)) {
            log.info("{} — table {} already present, skipping create", NAME, TABLE_USE_CASE);
            return;
        }
        String ddl;
        switch (dialect) {
            case "Postgres":
                ddl = "CREATE TABLE " + TABLE_USE_CASE + " ("
                        + "id BIGSERIAL PRIMARY KEY, "
                        + "bot_job_id INTEGER NOT NULL, "
                        + "name VARCHAR(255) NOT NULL, "
                        + "description VARCHAR(1024), "
                        + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                        + "updated_at TIMESTAMP, "
                        + "CONSTRAINT uq_use_case_job_name UNIQUE (bot_job_id, name), "
                        + "FOREIGN KEY (bot_job_id) REFERENCES bot_job(id) ON DELETE CASCADE"
                        + ")";
                break;
            case "SQLServer":
                ddl = "CREATE TABLE " + TABLE_USE_CASE + " ("
                        + "id BIGINT IDENTITY(1,1) PRIMARY KEY, "
                        + "bot_job_id INT NOT NULL, "
                        + "name NVARCHAR(255) NOT NULL, "
                        + "description NVARCHAR(1024), "
                        + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, "
                        + "updated_at DATETIME, "
                        + "CONSTRAINT uq_use_case_job_name UNIQUE (bot_job_id, name), "
                        + "FOREIGN KEY (bot_job_id) REFERENCES bot_job(id) ON DELETE CASCADE"
                        + ")";
                break;
            case "TEXT": // SQLite
                ddl = "CREATE TABLE " + TABLE_USE_CASE + " ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "bot_job_id INTEGER NOT NULL, "
                        + "name TEXT NOT NULL, "
                        + "description TEXT, "
                        + "created_at TEXT DEFAULT CURRENT_TIMESTAMP, "
                        + "updated_at TEXT, "
                        + "UNIQUE (bot_job_id, name), "
                        + "FOREIGN KEY (bot_job_id) REFERENCES bot_job(id) ON DELETE CASCADE"
                        + ")";
                break;
            default: // Access (UCanAccess)
                ddl = "CREATE TABLE " + TABLE_USE_CASE + " ("
                        + "id COUNTER PRIMARY KEY, "
                        + "bot_job_id LONG NOT NULL, "
                        + "name VARCHAR(255) NOT NULL, "
                        + "description VARCHAR(255), "
                        + "created_at DATETIME, "
                        + "updated_at DATETIME, "
                        + "CONSTRAINT uq_use_case_job_name UNIQUE (bot_job_id, name), "
                        + "FOREIGN KEY (bot_job_id) REFERENCES bot_job(id) ON DELETE CASCADE"
                        + ")";
                break;
        }
        try (Statement st = conn.createStatement()) {
            log.info("{} — exec: {}", NAME, ddl);
            st.executeUpdate(ddl);
        }
    }

    private void addUseCaseIdColumn(Connection conn, String dialect) throws SQLException {
        if (columnExists(conn, TABLE_MAPPING, "use_case_id")) {
            log.info("{} — column use_case_id already present on {}", NAME, TABLE_MAPPING);
            return;
        }
        String ddl;
        switch (dialect) {
            case "Postgres":
                ddl = "ALTER TABLE " + TABLE_MAPPING + " ADD COLUMN use_case_id INTEGER NULL";
                break;
            case "SQLServer":
                ddl = "ALTER TABLE " + TABLE_MAPPING + " ADD use_case_id INT NULL";
                break;
            case "TEXT":
                ddl = "ALTER TABLE " + TABLE_MAPPING + " ADD COLUMN use_case_id INTEGER";
                break;
            default: // Access
                ddl = "ALTER TABLE " + TABLE_MAPPING + " ADD COLUMN use_case_id LONG";
                break;
        }
        try (Statement st = conn.createStatement()) {
            log.info("{} — exec: {}", NAME, ddl);
            st.executeUpdate(ddl);
        }
    }

    /**
     * For every bot_job_id that has at least one mapping, ensure a "Default"
     * use case exists and point all NULL-use_case mappings at it.
     * Pure Java loop — portable across all 4 dialects.
     */
    private void backfillDefaultUseCases(Connection conn) throws SQLException {
        List<Integer> jobsNeedingDefault = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT DISTINCT bot_job_id FROM " + TABLE_MAPPING + " WHERE use_case_id IS NULL");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                jobsNeedingDefault.add(rs.getInt(1));
            }
        }
        if (jobsNeedingDefault.isEmpty()) {
            log.info("{} — no orphan mappings to backfill", NAME);
            return;
        }
        log.info("{} — backfilling Default use case for {} bot job(s)", NAME, jobsNeedingDefault.size());

        Boolean prevAuto = null;
        try {
            prevAuto = conn.getAutoCommit();
            conn.setAutoCommit(false);
            for (Integer botJobId : jobsNeedingDefault) {
                int useCaseId = ensureDefaultUseCase(conn, botJobId);
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE " + TABLE_MAPPING + " SET use_case_id = ? WHERE bot_job_id = ? AND use_case_id IS NULL")) {
                    ps.setInt(1, useCaseId);
                    ps.setInt(2, botJobId);
                    int n = ps.executeUpdate();
                    log.info("{} — bot_job {} -> use_case {} ({} rows)", NAME, botJobId, useCaseId, n);
                }
            }
            conn.commit();
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            throw e;
        } finally {
            if (prevAuto != null) {
                try { conn.setAutoCommit(prevAuto); } catch (SQLException ignored) {}
            }
        }
    }

    /** Returns the use_case.id of the "Default" use case for this bot job, creating it if missing. */
    private int ensureDefaultUseCase(Connection conn, int botJobId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM " + TABLE_USE_CASE + " WHERE bot_job_id = ? AND name = ?")) {
            ps.setInt(1, botJobId);
            ps.setString(2, "Default");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        // Insert
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO " + TABLE_USE_CASE
                        + " (bot_job_id, name, description, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, botJobId);
            ps.setString(2, "Default");
            ps.setString(3, "Auto-created during migration M20260511");
            String now = new Timestamp(System.currentTimeMillis()).toString();
            ps.setString(4, now);
            ps.setString(5, now);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        // Fallback: re-query
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM " + TABLE_USE_CASE + " WHERE bot_job_id = ? AND name = ?")) {
            ps.setInt(1, botJobId);
            ps.setString(2, "Default");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("Could not create or find Default use case for bot_job " + botJobId);
    }

    private boolean tableExists(Connection conn, String tableName) throws SQLException {
        DatabaseMetaData md = conn.getMetaData();
        for (String tn : new String[] {tableName, tableName.toLowerCase(), tableName.toUpperCase()}) {
            try (ResultSet rs = md.getTables(null, null, tn, new String[] {"TABLE"})) {
                if (rs.next()) return true;
            }
        }
        return false;
    }

    private boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
        DatabaseMetaData md = conn.getMetaData();
        for (String tn : new String[] {tableName, tableName.toLowerCase(), tableName.toUpperCase()}) {
            try (ResultSet rs = md.getColumns(null, null, tn, null)) {
                while (rs.next()) {
                    String n = rs.getString("COLUMN_NAME");
                    if (n != null && n.equalsIgnoreCase(columnName)) return true;
                }
            }
        }
        return false;
    }
}

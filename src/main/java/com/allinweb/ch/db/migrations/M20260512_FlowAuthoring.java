package com.allinweb.ch.db.migrations;

import com.allinweb.ch.db.MigrationRunner.Migration;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import lombok.extern.slf4j.Slf4j;

/**
 * Phase 1b of ROADMAP_9 — schema for the Flow authoring tab.
 *
 * <p>Two new tables:
 * <ul>
 *   <li>{@code flow} — one named, ordered execution sequence per bot job.
 *       FK to {@code bot_job(id)} CASCADE in dialects that allow it inline.</li>
 *   <li>{@code flow_step} — ordered steps within a flow. FK to {@code flow(id)}
 *       CASCADE. Step-type-specific fields (URL, headers, captures, substitutions,
 *       block references, etc.) are stored as a single JSON blob in
 *       {@code payload_json}, so the schema doesn't need to grow when a step
 *       type gains a knob.</li>
 * </ul>
 *
 * <p>This migration creates schema only — no UI consumes the tables yet.
 * Phase 2a wires the Flow tab editor to these tables via new socket verbs.
 *
 * <p>Idempotent: skips when tables already exist.
 */
@Slf4j
public class M20260512_FlowAuthoring implements Migration {

    private static final String NAME = "2026-05-12__flow_authoring";
    private static final String TABLE_FLOW = "flow";
    private static final String TABLE_FLOW_STEP = "flow_step";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void apply(Connection conn, String dialect) throws SQLException {
        log.info("{} — applying on dialect={}", NAME, dialect);
        createFlowTable(conn, dialect);
        createFlowStepTable(conn, dialect);
    }

    private void createFlowTable(Connection conn, String dialect) throws SQLException {
        if (tableExists(conn, TABLE_FLOW)) {
            log.info("{} — table {} already present, skipping", NAME, TABLE_FLOW);
            return;
        }
        String ddl;
        switch (dialect) {
            case "Postgres":
                ddl = "CREATE TABLE " + TABLE_FLOW + " ("
                        + "id BIGSERIAL PRIMARY KEY, "
                        + "bot_job_id INTEGER NOT NULL, "
                        + "name VARCHAR(255) NOT NULL, "
                        + "description VARCHAR(1024), "
                        + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                        + "updated_at TIMESTAMP, "
                        + "CONSTRAINT uq_flow_job_name UNIQUE (bot_job_id, name), "
                        + "FOREIGN KEY (bot_job_id) REFERENCES bot_job(id) ON DELETE CASCADE"
                        + ")";
                break;
            case "SQLServer":
                ddl = "CREATE TABLE " + TABLE_FLOW + " ("
                        + "id BIGINT IDENTITY(1,1) PRIMARY KEY, "
                        + "bot_job_id INT NOT NULL, "
                        + "name NVARCHAR(255) NOT NULL, "
                        + "description NVARCHAR(1024), "
                        + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, "
                        + "updated_at DATETIME, "
                        + "CONSTRAINT uq_flow_job_name UNIQUE (bot_job_id, name), "
                        + "FOREIGN KEY (bot_job_id) REFERENCES bot_job(id) ON DELETE CASCADE"
                        + ")";
                break;
            case "TEXT": // SQLite
                ddl = "CREATE TABLE " + TABLE_FLOW + " ("
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
                ddl = "CREATE TABLE " + TABLE_FLOW + " ("
                        + "id COUNTER PRIMARY KEY, "
                        + "bot_job_id LONG NOT NULL, "
                        + "name VARCHAR(255) NOT NULL, "
                        + "description VARCHAR(255), "
                        + "created_at DATETIME, "
                        + "updated_at DATETIME, "
                        + "CONSTRAINT uq_flow_job_name UNIQUE (bot_job_id, name), "
                        + "FOREIGN KEY (bot_job_id) REFERENCES bot_job(id) ON DELETE CASCADE"
                        + ")";
                break;
        }
        try (Statement st = conn.createStatement()) {
            log.info("{} — exec: {}", NAME, ddl);
            st.executeUpdate(ddl);
        }
    }

    private void createFlowStepTable(Connection conn, String dialect) throws SQLException {
        if (tableExists(conn, TABLE_FLOW_STEP)) {
            log.info("{} — table {} already present, skipping", NAME, TABLE_FLOW_STEP);
            return;
        }
        String ddl;
        switch (dialect) {
            case "Postgres":
                ddl = "CREATE TABLE " + TABLE_FLOW_STEP + " ("
                        + "id BIGSERIAL PRIMARY KEY, "
                        + "flow_id BIGINT NOT NULL, "
                        + "step_order INTEGER NOT NULL, "
                        + "name VARCHAR(255), "
                        + "step_type VARCHAR(16) NOT NULL, "
                        + "payload_json TEXT, "
                        + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                        + "updated_at TIMESTAMP, "
                        + "FOREIGN KEY (flow_id) REFERENCES flow(id) ON DELETE CASCADE"
                        + ")";
                break;
            case "SQLServer":
                ddl = "CREATE TABLE " + TABLE_FLOW_STEP + " ("
                        + "id BIGINT IDENTITY(1,1) PRIMARY KEY, "
                        + "flow_id BIGINT NOT NULL, "
                        + "step_order INT NOT NULL, "
                        + "name NVARCHAR(255), "
                        + "step_type NVARCHAR(16) NOT NULL, "
                        + "payload_json NVARCHAR(MAX), "
                        + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, "
                        + "updated_at DATETIME, "
                        + "FOREIGN KEY (flow_id) REFERENCES flow(id) ON DELETE CASCADE"
                        + ")";
                break;
            case "TEXT": // SQLite
                ddl = "CREATE TABLE " + TABLE_FLOW_STEP + " ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "flow_id INTEGER NOT NULL, "
                        + "step_order INTEGER NOT NULL, "
                        + "name TEXT, "
                        + "step_type TEXT NOT NULL, "
                        + "payload_json TEXT, "
                        + "created_at TEXT DEFAULT CURRENT_TIMESTAMP, "
                        + "updated_at TEXT, "
                        + "FOREIGN KEY (flow_id) REFERENCES flow(id) ON DELETE CASCADE"
                        + ")";
                break;
            default: // Access (UCanAccess) — MEMO for unbounded text
                ddl = "CREATE TABLE " + TABLE_FLOW_STEP + " ("
                        + "id COUNTER PRIMARY KEY, "
                        + "flow_id LONG NOT NULL, "
                        + "step_order LONG NOT NULL, "
                        + "name VARCHAR(255), "
                        + "step_type VARCHAR(16) NOT NULL, "
                        + "payload_json MEMO, "
                        + "created_at DATETIME, "
                        + "updated_at DATETIME, "
                        + "FOREIGN KEY (flow_id) REFERENCES flow(id) ON DELETE CASCADE"
                        + ")";
                break;
        }
        try (Statement st = conn.createStatement()) {
            log.info("{} — exec: {}", NAME, ddl);
            st.executeUpdate(ddl);
        }
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
}

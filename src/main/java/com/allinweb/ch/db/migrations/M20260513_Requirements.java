package com.allinweb.ch.db.migrations;

import com.allinweb.ch.db.MigrationRunner.Migration;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import lombok.extern.slf4j.Slf4j;

/**
 * Requirements + traceability links — schema for the Requirements tab.
 *
 * <p>Three new tables:
 * <ul>
 *   <li>{@code requirement} — high-context descriptions of functional cases
 *       that must be executed. Scoped to a bot job (FK CASCADE).</li>
 *   <li>{@code requirement_use_case} — many-to-many link from a requirement
 *       to one or more named Use Cases (Functional Cases). Composite PK,
 *       both FKs CASCADE so deleting either side cleans up its links.</li>
 *   <li>{@code requirement_flow} — many-to-many link from a requirement
 *       to one or more flows (Flow Tests). Same shape as above.</li>
 * </ul>
 *
 * <p>Idempotent: skips when tables already exist.
 */
@Slf4j
public class M20260513_Requirements implements Migration {

    private static final String NAME = "2026-05-13__requirements";
    private static final String TABLE_REQUIREMENT = "requirement";
    private static final String TABLE_LINK_USE_CASE = "requirement_use_case";
    private static final String TABLE_LINK_FLOW = "requirement_flow";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void apply(Connection conn, String dialect) throws SQLException {
        log.info("{} — applying on dialect={}", NAME, dialect);
        createRequirementTable(conn, dialect);
        createLinkTable(conn, dialect, TABLE_LINK_USE_CASE, "use_case_id", "use_case");
        createLinkTable(conn, dialect, TABLE_LINK_FLOW, "flow_id", "flow");
    }

    private void createRequirementTable(Connection conn, String dialect) throws SQLException {
        if (tableExists(conn, TABLE_REQUIREMENT)) {
            log.info("{} — table {} already present, skipping", NAME, TABLE_REQUIREMENT);
            return;
        }
        String ddl;
        switch (dialect) {
            case "Postgres":
                ddl = "CREATE TABLE " + TABLE_REQUIREMENT + " ("
                        + "id BIGSERIAL PRIMARY KEY, "
                        + "bot_job_id INTEGER NOT NULL, "
                        + "external_ref VARCHAR(255), "
                        + "title VARCHAR(255) NOT NULL, "
                        + "description TEXT, "
                        + "priority VARCHAR(16), "
                        + "status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE', "
                        + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                        + "updated_at TIMESTAMP, "
                        + "CONSTRAINT uq_requirement_job_title UNIQUE (bot_job_id, title), "
                        + "FOREIGN KEY (bot_job_id) REFERENCES bot_job(id) ON DELETE CASCADE"
                        + ")";
                break;
            case "SQLServer":
                ddl = "CREATE TABLE " + TABLE_REQUIREMENT + " ("
                        + "id BIGINT IDENTITY(1,1) PRIMARY KEY, "
                        + "bot_job_id INT NOT NULL, "
                        + "external_ref NVARCHAR(255), "
                        + "title NVARCHAR(255) NOT NULL, "
                        + "description NVARCHAR(MAX), "
                        + "priority NVARCHAR(16), "
                        + "status NVARCHAR(16) NOT NULL DEFAULT 'ACTIVE', "
                        + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, "
                        + "updated_at DATETIME, "
                        + "CONSTRAINT uq_requirement_job_title UNIQUE (bot_job_id, title), "
                        + "FOREIGN KEY (bot_job_id) REFERENCES bot_job(id) ON DELETE CASCADE"
                        + ")";
                break;
            case "TEXT": // SQLite
                ddl = "CREATE TABLE " + TABLE_REQUIREMENT + " ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "bot_job_id INTEGER NOT NULL, "
                        + "external_ref TEXT, "
                        + "title TEXT NOT NULL, "
                        + "description TEXT, "
                        + "priority TEXT, "
                        + "status TEXT NOT NULL DEFAULT 'ACTIVE', "
                        + "created_at TEXT DEFAULT CURRENT_TIMESTAMP, "
                        + "updated_at TEXT, "
                        + "UNIQUE (bot_job_id, title), "
                        + "FOREIGN KEY (bot_job_id) REFERENCES bot_job(id) ON DELETE CASCADE"
                        + ")";
                break;
            default: // Access (UCanAccess) — MEMO for unbounded text
                ddl = "CREATE TABLE " + TABLE_REQUIREMENT + " ("
                        + "id COUNTER PRIMARY KEY, "
                        + "bot_job_id LONG NOT NULL, "
                        + "external_ref VARCHAR(255), "
                        + "title VARCHAR(255) NOT NULL, "
                        + "description MEMO, "
                        + "priority VARCHAR(16), "
                        + "status VARCHAR(16) NOT NULL, "
                        + "created_at DATETIME, "
                        + "updated_at DATETIME, "
                        + "CONSTRAINT uq_requirement_job_title UNIQUE (bot_job_id, title), "
                        + "FOREIGN KEY (bot_job_id) REFERENCES bot_job(id) ON DELETE CASCADE"
                        + ")";
                break;
        }
        try (Statement st = conn.createStatement()) {
            log.info("{} — exec: {}", NAME, ddl);
            st.executeUpdate(ddl);
        }
    }

    /** Generic many-to-many link table builder; both columns NOT NULL, composite PK, both FKs CASCADE. */
    private void createLinkTable(Connection conn, String dialect, String table, String otherCol, String otherTable)
            throws SQLException {
        if (tableExists(conn, table)) {
            log.info("{} — table {} already present, skipping", NAME, table);
            return;
        }
        String ddl;
        switch (dialect) {
            case "Postgres":
                ddl = "CREATE TABLE " + table + " ("
                        + "requirement_id BIGINT NOT NULL, "
                        + otherCol + " BIGINT NOT NULL, "
                        + "PRIMARY KEY (requirement_id, " + otherCol + "), "
                        + "FOREIGN KEY (requirement_id) REFERENCES requirement(id) ON DELETE CASCADE, "
                        + "FOREIGN KEY (" + otherCol + ") REFERENCES " + otherTable + "(id) ON DELETE CASCADE"
                        + ")";
                break;
            case "SQLServer":
                ddl = "CREATE TABLE " + table + " ("
                        + "requirement_id BIGINT NOT NULL, "
                        + otherCol + " BIGINT NOT NULL, "
                        + "PRIMARY KEY (requirement_id, " + otherCol + "), "
                        + "FOREIGN KEY (requirement_id) REFERENCES requirement(id) ON DELETE CASCADE, "
                        + "FOREIGN KEY (" + otherCol + ") REFERENCES " + otherTable + "(id) ON DELETE CASCADE"
                        + ")";
                break;
            case "TEXT":
                ddl = "CREATE TABLE " + table + " ("
                        + "requirement_id INTEGER NOT NULL, "
                        + otherCol + " INTEGER NOT NULL, "
                        + "PRIMARY KEY (requirement_id, " + otherCol + "), "
                        + "FOREIGN KEY (requirement_id) REFERENCES requirement(id) ON DELETE CASCADE, "
                        + "FOREIGN KEY (" + otherCol + ") REFERENCES " + otherTable + "(id) ON DELETE CASCADE"
                        + ")";
                break;
            default: // Access
                ddl = "CREATE TABLE " + table + " ("
                        + "requirement_id LONG NOT NULL, "
                        + otherCol + " LONG NOT NULL, "
                        + "PRIMARY KEY (requirement_id, " + otherCol + "), "
                        + "FOREIGN KEY (requirement_id) REFERENCES requirement(id) ON DELETE CASCADE, "
                        + "FOREIGN KEY (" + otherCol + ") REFERENCES " + otherTable + "(id) ON DELETE CASCADE"
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

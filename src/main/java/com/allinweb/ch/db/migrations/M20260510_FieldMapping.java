package com.allinweb.ch.db.migrations;

import com.allinweb.ch.db.MigrationRunner.Migration;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import lombok.extern.slf4j.Slf4j;

/**
 * Functional Test tab — adds {@code use_case_field_mapping} table to persist
 * the API-field ↔ bot-job-INPUT-instruction pairs created in the React mapping
 * UI. The table is the same one the future Use Case Orchestrator (ROADMAP_8)
 * will reuse — no rename when that feature lands.
 *
 * <p>Constraints:
 * <ul>
 *   <li>{@code (bot_job_id, api_key)} unique → one mapping per API field per job</li>
 *   <li>{@code (bot_job_id, bot_instruction_id)} unique → one mapping per bot field per job</li>
 *   <li>FK {@code bot_job_id → bot_job(id)} ON DELETE CASCADE — deleting a job removes its mappings</li>
 *   <li>FK {@code bot_instruction_id → instruction(id)} ON DELETE CASCADE — deleting an instruction removes the mapping</li>
 * </ul>
 *
 * <p>Idempotent: skips when the table already exists.
 */
@Slf4j
public class M20260510_FieldMapping implements Migration {

    private static final String NAME = "2026-05-10__use_case_field_mapping";
    private static final String TABLE = "use_case_field_mapping";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void apply(Connection conn, String dialect) throws SQLException {
        log.info("{} — applying on dialect={}", NAME, dialect);
        if (tableExists(conn, TABLE)) {
            log.info("{} — table {} already present, skipping", NAME, TABLE);
            return;
        }
        String ddl;
        switch (dialect) {
            case "Postgres":
                ddl = "CREATE TABLE " + TABLE + " ("
                        + "id BIGSERIAL PRIMARY KEY, "
                        + "bot_job_id INTEGER NOT NULL, "
                        + "api_key VARCHAR(512) NOT NULL, "
                        + "api_spec_file VARCHAR(512), "
                        + "api_field_name VARCHAR(255), "
                        + "bot_instruction_id INTEGER NOT NULL, "
                        + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                        + "updated_at TIMESTAMP, "
                        + "CONSTRAINT uq_ucfm_job_api UNIQUE (bot_job_id, api_key), "
                        + "CONSTRAINT uq_ucfm_job_bot UNIQUE (bot_job_id, bot_instruction_id), "
                        + "FOREIGN KEY (bot_job_id) REFERENCES bot_job(id) ON DELETE CASCADE, "
                        + "FOREIGN KEY (bot_instruction_id) REFERENCES instruction(id) ON DELETE CASCADE"
                        + ")";
                break;
            case "SQLServer":
                ddl = "CREATE TABLE " + TABLE + " ("
                        + "id BIGINT IDENTITY(1,1) PRIMARY KEY, "
                        + "bot_job_id INT NOT NULL, "
                        + "api_key NVARCHAR(512) NOT NULL, "
                        + "api_spec_file NVARCHAR(512), "
                        + "api_field_name NVARCHAR(255), "
                        + "bot_instruction_id INT NOT NULL, "
                        + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, "
                        + "updated_at DATETIME, "
                        + "CONSTRAINT uq_ucfm_job_api UNIQUE (bot_job_id, api_key), "
                        + "CONSTRAINT uq_ucfm_job_bot UNIQUE (bot_job_id, bot_instruction_id), "
                        + "FOREIGN KEY (bot_job_id) REFERENCES bot_job(id) ON DELETE CASCADE, "
                        + "FOREIGN KEY (bot_instruction_id) REFERENCES instruction(id) ON DELETE CASCADE"
                        + ")";
                break;
            case "TEXT": // SQLite
                ddl = "CREATE TABLE " + TABLE + " ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "bot_job_id INTEGER NOT NULL, "
                        + "api_key TEXT NOT NULL, "
                        + "api_spec_file TEXT, "
                        + "api_field_name TEXT, "
                        + "bot_instruction_id INTEGER NOT NULL, "
                        + "created_at TEXT DEFAULT CURRENT_TIMESTAMP, "
                        + "updated_at TEXT, "
                        + "UNIQUE (bot_job_id, api_key), "
                        + "UNIQUE (bot_job_id, bot_instruction_id), "
                        + "FOREIGN KEY (bot_job_id) REFERENCES bot_job(id) ON DELETE CASCADE, "
                        + "FOREIGN KEY (bot_instruction_id) REFERENCES instruction(id) ON DELETE CASCADE"
                        + ")";
                break;
            default: // Access (UCanAccess)
                ddl = "CREATE TABLE " + TABLE + " ("
                        + "id COUNTER PRIMARY KEY, "
                        + "bot_job_id LONG NOT NULL, "
                        + "api_key VARCHAR(255) NOT NULL, "
                        + "api_spec_file VARCHAR(255), "
                        + "api_field_name VARCHAR(255), "
                        + "bot_instruction_id LONG NOT NULL, "
                        + "created_at DATETIME, "
                        + "updated_at DATETIME, "
                        + "CONSTRAINT uq_ucfm_job_api UNIQUE (bot_job_id, api_key), "
                        + "CONSTRAINT uq_ucfm_job_bot UNIQUE (bot_job_id, bot_instruction_id), "
                        + "FOREIGN KEY (bot_job_id) REFERENCES bot_job(id) ON DELETE CASCADE, "
                        + "FOREIGN KEY (bot_instruction_id) REFERENCES instruction(id) ON DELETE CASCADE"
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

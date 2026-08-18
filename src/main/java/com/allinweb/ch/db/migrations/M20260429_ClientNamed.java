package com.allinweb.ch.db.migrations;

import com.allinweb.ch.db.MigrationRunner.Migration;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import lombok.extern.slf4j.Slf4j;

/**
 * Roadmap 3 Phase 3d — add a nullable {@code client_named} column to {@code instruction} and
 * {@code component_instruction}.
 *
 * <p>Semantics:
 * <ul>
 *   <li>{@code client_named IS NULL} → the existing {@code name} column is the canonical identifier.
 *       Every backend flow (matchers, locator lookup, recovery service) keeps using {@code name}.
 *       Front-end shows {@code name}.</li>
 *   <li>{@code client_named IS NOT NULL} → the user has typed a custom display name in the React
 *       grid. Front-end displays {@code client_named}; backend STILL uses {@code name} for all
 *       resolution and lookup. Pure display override — no behavioural impact on bot runs or
 *       locator persistence.</li>
 * </ul>
 *
 * <p>Idempotent: skips when the column already exists. The PerformBackup INSERTs and the
 * load/save SQL in PerformDataBase are updated separately to read/write the new column.
 */
@Slf4j
public class M20260429_ClientNamed implements Migration {

    private static final String NAME = "2026-04-29__client_named";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void apply(Connection conn, String dialect) throws SQLException {
        log.info("{} — applying on dialect={}", NAME, dialect);
        addColumnIfMissing(conn, dialect, "instruction");
        addColumnIfMissing(conn, dialect, "component_instruction");
    }

    private void addColumnIfMissing(Connection conn, String dialect, String table) throws SQLException {
        if (columnExists(conn, table, "client_named")) {
            log.info("{} — column already present on {}", NAME, table);
            return;
        }
        String ddl;
        switch (dialect) {
            case "Postgres":
                ddl = "ALTER TABLE " + table + " ADD COLUMN client_named VARCHAR(255) NULL";
                break;
            case "SQLServer":
                ddl = "ALTER TABLE " + table + " ADD client_named NVARCHAR(255) NULL";
                break;
            case "TEXT":
                ddl = "ALTER TABLE " + table + " ADD COLUMN client_named TEXT";
                break;
            default: // Access — UCanAccess accepts ALTER TABLE … ADD COLUMN.
                ddl = "ALTER TABLE " + table + " ADD COLUMN client_named VARCHAR(255)";
                break;
        }
        try (Statement st = conn.createStatement()) {
            log.info("{} — exec: {}", NAME, ddl);
            st.executeUpdate(ddl);
        }
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

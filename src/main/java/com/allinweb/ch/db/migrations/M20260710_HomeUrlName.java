package com.allinweb.ch.db.migrations;

import com.allinweb.ch.db.MigrationRunner.Migration;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import lombok.extern.slf4j.Slf4j;

/**
 * Adds a user-facing environment name to {@code home_url}.
 *
 * <p>Existing rows are backfilled to {@code TEST}. The column remains nullable at
 * the database layer so older restore paths and external databases can migrate
 * cleanly; application writes normalize blank names to {@code TEST}.
 */
@Slf4j
public class M20260710_HomeUrlName implements Migration {

    private static final String NAME = "2026-07-10__home_url_name";
    private static final String DEFAULT_ENV_NAME = "TEST";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void apply(Connection conn, String dialect) throws SQLException {
        log.info("{} — applying on dialect={}", NAME, dialect);
        if (!columnExists(conn, "home_url", "name")) {
            String ddl;
            switch (dialect) {
                case "Postgres":
                    ddl = "ALTER TABLE home_url ADD COLUMN name VARCHAR(255) DEFAULT 'TEST'";
                    break;
                case "SQLServer":
                    ddl = "ALTER TABLE home_url ADD name NVARCHAR(255) DEFAULT 'TEST'";
                    break;
                case "TEXT":
                    ddl = "ALTER TABLE home_url ADD COLUMN name TEXT DEFAULT 'TEST'";
                    break;
                default:
                    ddl = "ALTER TABLE home_url ADD COLUMN name VARCHAR(255)";
                    break;
            }
            exec(conn, ddl);
        }

        try (PreparedStatement ps =
                conn.prepareStatement("UPDATE home_url SET name = ? WHERE name IS NULL OR name = ''")) {
            ps.setString(1, DEFAULT_ENV_NAME);
            int updated = ps.executeUpdate();
            log.info("{} — backfilled {} home_url name row(s)", NAME, updated);
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

    private void exec(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement()) {
            log.info("{} — exec: {}", NAME, sql);
            st.executeUpdate(sql);
        }
    }
}

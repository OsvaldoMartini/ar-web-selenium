package com.allinweb.ch.db.migrations;

import com.allinweb.ch.db.MigrationRunner.Migration;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;

/** Creates the immutable, owner-scoped Page Scanner history registry. */
public final class M20260807_PageScanSnapshot implements Migration {

    private static final String NAME = "2026-08-07__page_scan_snapshot";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void apply(Connection conn, String dialect) throws SQLException {
        if (!tableExists(conn, "page_scan_snapshot")) {
            String text = "TEXT".equalsIgnoreCase(dialect) ? "TEXT" : "VARCHAR(4000)";
            String sql = "CREATE TABLE page_scan_snapshot ("
                    + "scan_id " + text + " PRIMARY KEY,"
                    + "home_banking_id INTEGER NOT NULL,"
                    + "bot_job_id INTEGER NOT NULL,"
                    + "home_url_id INTEGER,"
                    + "page_key " + text + " NOT NULL,"
                    + "page_url " + text + ","
                    + "captured_at " + text + " NOT NULL,"
                    + "element_count INTEGER NOT NULL,"
                    + "artifact_path " + text + " NOT NULL,"
                    + "manifest_sha256 " + text + ","
                    + "status " + text + " NOT NULL,"
                    + "pinned INTEGER NOT NULL DEFAULT 0"
                    + ")";
            try (Statement statement = conn.createStatement()) {
                statement.executeUpdate(sql);
                statement.executeUpdate("CREATE INDEX idx_page_scan_snapshot_owner "
                        + "ON page_scan_snapshot (home_banking_id, bot_job_id, captured_at)");
                statement.executeUpdate("CREATE INDEX idx_page_scan_snapshot_page "
                        + "ON page_scan_snapshot (home_banking_id, bot_job_id, page_key)");
            }
        }
    }

    private static boolean tableExists(Connection conn, String name) throws SQLException {
        DatabaseMetaData metadata = conn.getMetaData();
        try (var tables = metadata.getTables(null, null, null, new String[] {"TABLE"})) {
            while (tables.next()) {
                if (name.equalsIgnoreCase(tables.getString("TABLE_NAME"))) return true;
            }
        }
        return false;
    }
}

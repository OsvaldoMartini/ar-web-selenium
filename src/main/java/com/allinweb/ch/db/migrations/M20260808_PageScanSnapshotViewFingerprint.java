package com.allinweb.ch.db.migrations;

import com.allinweb.ch.db.MigrationRunner.Migration;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Adds the bounded structural fingerprint used by Page Mappings cache-first scanning. */
public final class M20260808_PageScanSnapshotViewFingerprint implements Migration {

    private static final String NAME = "2026-08-08__page_scan_snapshot_view_fingerprint";
    private static final String TABLE = "page_scan_snapshot";
    private static final String COLUMN = "view_fingerprint";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void apply(Connection connection, String dialect) throws SQLException {
        if (columnExists(connection)) return;
        boolean sqlServer = "SQLServer".equalsIgnoreCase(dialect);
        String type = sqlServer ? "NVARCHAR(64)" : "VARCHAR(64)";
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "ALTER TABLE " + TABLE + (sqlServer ? " ADD " : " ADD COLUMN ")
                            + COLUMN + " " + type);
        }
    }

    private static boolean columnExists(Connection connection) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet columns = metadata.getColumns(null, null, null, null)) {
            while (columns.next()) {
                if (TABLE.equalsIgnoreCase(columns.getString("TABLE_NAME"))
                        && COLUMN.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }
}

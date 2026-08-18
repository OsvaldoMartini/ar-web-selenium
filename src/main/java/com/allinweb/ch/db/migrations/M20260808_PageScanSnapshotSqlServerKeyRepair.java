package com.allinweb.ch.db.migrations;

import com.allinweb.ch.db.MigrationRunner.Migration;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Replays the idempotent Page Scan snapshot schema reconciliation under a new migration identity.
 *
 * <p>The original migration may already be present in {@code schema_migrations} on an installation
 * whose SQL Server table still has the draft 4000-character key columns. A new immutable name is
 * required so those installations execute the bounded-key repair exactly once.</p>
 */
public final class M20260808_PageScanSnapshotSqlServerKeyRepair implements Migration {

    private static final String NAME = "2026-08-08__page_scan_snapshot_sqlserver_key_repair";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void apply(Connection conn, String dialect) throws SQLException {
        if (!"SQLServer".equalsIgnoreCase(dialect)) return;
        M20260807_PageScanSnapshot.reconcileSchema(conn, dialect);
    }
}

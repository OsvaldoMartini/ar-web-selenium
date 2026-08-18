package com.allinweb.ch.db.migrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class M20260807_PageScanSnapshotTest {

    @Test
    void createsOwnerScopedSnapshotRegistryOnSqlite() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            new M20260807_PageScanSnapshot().apply(connection, "TEXT");
            try (Statement statement = connection.createStatement();
                    ResultSet columns = statement.executeQuery("PRAGMA table_info(page_scan_snapshot)")) {
                int count = 0;
                while (columns.next()) count++;
                assertEquals(12, count);
            }
            try (ResultSet indexes = connection.getMetaData().getIndexInfo(null, null, "page_scan_snapshot", false, false)) {
                Set<String> names = new HashSet<>();
                while (indexes.next()) {
                    String name = indexes.getString("INDEX_NAME");
                    if (name != null) names.add(name.toLowerCase());
                }
                assertTrue(names.contains("idx_page_scan_snapshot_owner"));
                assertTrue(names.contains("idx_page_scan_snapshot_page"));
            }
            new M20260807_PageScanSnapshot().apply(connection, "TEXT");
        }
    }

    @Test
    void repairsMissingIndexesWhenTheTableAlreadyExists() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(M20260807_PageScanSnapshot.createTableSql("TEXT"));

            new M20260807_PageScanSnapshot().apply(connection, "TEXT");

            Set<String> names = new HashSet<>();
            try (ResultSet indexes = connection.getMetaData().getIndexInfo(
                    null, null, "page_scan_snapshot", false, false)) {
                while (indexes.next()) {
                    String name = indexes.getString("INDEX_NAME");
                    if (name != null) names.add(name.toLowerCase());
                }
            }
            assertEquals(
                    Set.of("idx_page_scan_snapshot_owner", "idx_page_scan_snapshot_page"),
                    names.stream().filter(name -> name.startsWith("idx_page_scan_snapshot_")).collect(
                            java.util.stream.Collectors.toSet()));
        }
    }

    @Test
    void sqlServerDdlKeepsEveryIndexedStringWithinTheIndexKeyLimit() {
        String ddl = M20260807_PageScanSnapshot.createTableSql("SQLServer");

        assertTrue(ddl.contains("scan_id NVARCHAR(36)"));
        assertTrue(ddl.contains("page_key NVARCHAR(128)"));
        assertTrue(ddl.contains("captured_at NVARCHAR(40)"));
        assertTrue(ddl.contains("manifest_sha256 NVARCHAR(64)"));
        assertTrue(ddl.contains("status NVARCHAR(16)"));
        assertTrue(ddl.contains("page_url NVARCHAR(MAX)"));
        assertTrue(ddl.contains("artifact_path NVARCHAR(MAX)"));
        assertFalse(ddl.contains("VARCHAR(4000)"));
    }

    @Test
    void everySupportedDialectUsesBoundedIdentityAndIndexColumns() {
        for (String dialect : Set.of("Postgres", "SQLServer", "Access")) {
            String ddl = M20260807_PageScanSnapshot.createTableSql(dialect);
            assertFalse(ddl.contains("scan_id VARCHAR(4000)"), dialect);
            assertFalse(ddl.contains("page_key VARCHAR(4000)"), dialect);
            assertFalse(ddl.contains("captured_at VARCHAR(4000)"), dialect);
        }
    }
}

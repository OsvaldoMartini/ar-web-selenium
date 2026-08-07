package com.allinweb.ch.db.migrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
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
                int count = 0;
                while (indexes.next()) if (indexes.getString("INDEX_NAME") != null) count++;
                assertTrue(count >= 2);
            }
            new M20260807_PageScanSnapshot().apply(connection, "TEXT");
        }
    }
}

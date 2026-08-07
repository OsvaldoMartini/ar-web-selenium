package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.db.ScannedPageIdentity;
import com.allinweb.ch.db.migrations.M20260807_PageScanSnapshot;
import com.allinweb.ch.model.ElementDTO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import org.junit.jupiter.api.Test;

class PageScanSnapshotStoreTest {

    @Test
    void persistsEmptyScanWithoutOverwritingPreviousSnapshot() throws Exception {
        Path diagnostics = Files.createTempDirectory("page-scan-diagnostics");
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            new M20260807_PageScanSnapshot().apply(connection, "TEXT");
            ScannedPageIdentity page = ScannedPageIdentity.fromLiveUrl("https://example.test/account");
            PageScanSnapshotStore.Snapshot first = PageScanSnapshotStore.persist(
                    connection, 7, 32, null, "Job", page, List.of(new ElementDTO()), diagnostics.toString());
            PageScanSnapshotStore.Snapshot second = PageScanSnapshotStore.persist(
                    connection, 7, 32, null, "Job", page, List.of(), diagnostics.toString());

            assertEquals("READY", first.status());
            assertEquals("READY", second.status());
            assertFalse(first.scanId().equals(second.scanId()));
            Path firstFolder = diagnostics.resolve("page_diagnostics").resolve("Scanned").resolve(first.artifactPath());
            Path secondFolder = diagnostics.resolve("page_diagnostics").resolve("Scanned").resolve(second.artifactPath());
            assertTrue(Files.exists(firstFolder.resolve("manifest.json")));
            assertTrue(Files.exists(firstFolder.resolve("elements.json")));
            assertTrue(Files.exists(secondFolder.resolve("elements.json")));
            assertEquals(2, count(connection));
        }
    }

    private static int count(java.sql.Connection connection) throws Exception {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM page_scan_snapshot")) {
            rows.next();
            return rows.getInt(1);
        }
    }
}

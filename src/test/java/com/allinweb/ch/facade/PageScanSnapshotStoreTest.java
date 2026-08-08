package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.allinweb.ch.db.ScannedPageIdentity;
import com.allinweb.ch.db.migrations.M20260807_PageScanSnapshot;
import com.allinweb.ch.driver.ARPlaywrightDriver;
import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.vision.RasterImage;
import com.allinweb.ch.vision.RasterImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PageScanSnapshotStoreTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsEmptyScanWithoutOverwritingPreviousSnapshot() throws Exception {
        Path diagnostics = temporaryDirectory.resolve("empty-scan");
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            new M20260807_PageScanSnapshot().apply(connection, "TEXT");
            ScannedPageIdentity page = ScannedPageIdentity.fromLiveUrl("https://example.test/account");
            PageScanSnapshotStore.Snapshot first = PageScanSnapshotStore.persist(
                    connection, 7, 32, null, "Job", page, List.of(new ElementDTO()), diagnostics.toString(),
                    PageScanSnapshotStoreTest::writeMinimalCapture);
            PageScanSnapshotStore.Snapshot second = PageScanSnapshotStore.persist(
                    connection, 7, 32, null, "Job", page, List.of(), diagnostics.toString(),
                    PageScanSnapshotStoreTest::writeMinimalCapture);

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

    @Test
    void storesOnlyArtifactsWrittenForTheCurrentScan() throws Exception {
        Path diagnostics = temporaryDirectory.resolve("isolated-artifacts");
        Path mutableDiagnostics = diagnostics.resolve("page_diagnostics");
        Files.createDirectories(mutableDiagnostics);
        Files.writeString(mutableDiagnostics.resolve("page-BJ.png"), "previous-owner-artifact");
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            new M20260807_PageScanSnapshot().apply(connection, "TEXT");
            ScannedPageIdentity page = ScannedPageIdentity.fromLiveUrl("https://example.test/account");
            PageScanSnapshotStore.Snapshot snapshot = PageScanSnapshotStore.persist(
                    connection,
                    7,
                    32,
                    null,
                    "Job",
                    page,
                    List.of(),
                    diagnostics.toString(),
                    staging -> {
                        Files.writeString(staging.resolve("screenshot.png"), "current-scan-artifact");
                        Files.writeString(staging.resolve("rects.json"), "[]");
                        return new PageScanSnapshotStore.CaptureMetadata(
                                "viewport", 2.0d, 640.0d, 480.0d, 1280, 960, 0.0d, 0.0d);
                    });

            Path capture = diagnostics.resolve("page_diagnostics").resolve("Scanned").resolve(snapshot.artifactPath());
            assertEquals("current-scan-artifact", Files.readString(capture.resolve("screenshot.png")));
            assertFalse(Files.exists(capture.resolve("page-BJ.png")));
            assertTrue(Files.readString(capture.resolve("meta.json")).contains("\"devicePixelRatio\": 2.0"));
        }
    }

    @Test
    void redactsPageCredentialsQueryAndFragmentAtTheSnapshotBoundary() throws Exception {
        Path diagnostics = temporaryDirectory.resolve("redacted-url");
        String secretUrl = "https://client:password@BANK.EXAMPLE:443/accounts?token=secret#account-42";
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            new M20260807_PageScanSnapshot().apply(connection, "TEXT");
            PageScanSnapshotStore.Snapshot snapshot = PageScanSnapshotStore.persist(
                    connection,
                    7,
                    32,
                    null,
                    "Job",
                    ScannedPageIdentity.fromLiveUrl(secretUrl),
                    List.of(),
                    diagnostics.toString(),
                    PageScanSnapshotStoreTest::writeMinimalCapture);

            String storedUrl;
            try (var statement = connection.createStatement();
                    var row = statement.executeQuery("SELECT page_url FROM page_scan_snapshot")) {
                assertTrue(row.next());
                storedUrl = row.getString(1);
            }
            assertEquals("https://bank.example/accounts", storedUrl);
            Path capture = diagnostics.resolve("page_diagnostics").resolve("Scanned").resolve(snapshot.artifactPath());
            String durableText = Files.readString(capture.resolve("meta.json"))
                    + Files.readString(capture.resolve("manifest.json"));
            assertFalse(durableText.contains("client"));
            assertFalse(durableText.contains("password"));
            assertFalse(durableText.contains("secret"));
            assertFalse(durableText.contains("account-42"));
        }
    }

    @Test
    void recordsFailedWhenTheStagingDirectoryCannotBeCreated() throws Exception {
        Path diagnostics = temporaryDirectory.resolve("blocked-staging");
        Path scannedRoot = diagnostics.resolve("page_diagnostics").resolve("Scanned");
        Files.createDirectories(scannedRoot.getParent());
        Files.writeString(scannedRoot, "not-a-directory");
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            new M20260807_PageScanSnapshot().apply(connection, "TEXT");

            assertThrows(
                    Exception.class,
                    () -> PageScanSnapshotStore.persist(
                            connection,
                            7,
                            32,
                            null,
                            "Job",
                            ScannedPageIdentity.fromLiveUrl("https://example.test/account"),
                            List.of(),
                            diagnostics.toString(),
                            PageScanSnapshotStoreTest::writeMinimalCapture));

            assertSnapshotState(connection, "FAILED", "");
        }
    }

    @Test
    void removesFinalizedArtifactsWhenTheReadyDatabaseUpdateFails() throws Exception {
        Path diagnostics = temporaryDirectory.resolve("ready-db-failure");
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            new M20260807_PageScanSnapshot().apply(connection, "TEXT");
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("""
                        CREATE TRIGGER reject_ready_snapshot
                        BEFORE UPDATE OF status ON page_scan_snapshot
                        WHEN NEW.status = 'READY'
                        BEGIN
                          SELECT RAISE(ABORT, 'READY refused for test');
                        END
                        """);
            }

            assertThrows(
                    Exception.class,
                    () -> PageScanSnapshotStore.persist(
                            connection,
                            7,
                            32,
                            null,
                            "Job",
                            ScannedPageIdentity.fromLiveUrl("https://example.test/account"),
                            List.of(),
                            diagnostics.toString(),
                            PageScanSnapshotStoreTest::writeMinimalCapture));

            assertSnapshotState(connection, "FAILED", "");
            Path scannedRoot = diagnostics.resolve("page_diagnostics").resolve("Scanned");
            if (Files.exists(scannedRoot)) {
                try (var paths = Files.walk(scannedRoot)) {
                    assertFalse(paths.anyMatch(path -> path.getFileName().toString().equals("manifest.json")));
                }
            }
        }
    }

    @Test
    void pageDriftDuringArtifactCaptureLeavesFailedHistoryAndNoArtifactFolder() throws Exception {
        Path diagnostics = temporaryDirectory.resolve("page-drift");
        ScannedPageIdentity expectedPage =
                ScannedPageIdentity.fromLiveUrl("https://bank.example/accounts");
        ARPlaywrightDriver browser = mock(ARPlaywrightDriver.class);
        when(browser.currentUrl()).thenReturn(
                expectedPage.actualUrl(), "https://bank.example/payments");
        when(browser.evaluate(anyString(), any())).thenReturn(java.util.Map.of(
                "meta", java.util.Map.of(
                        "devicePixelRatio", 1.0d,
                        "viewportWidth", 2.0d,
                        "viewportHeight", 2.0d,
                        "documentWidth", 2.0d,
                        "documentHeight", 2.0d,
                        "scrollX", 0.0d,
                        "scrollY", 0.0d),
                "rects", List.of()));
        when(browser.screenshot(false)).thenReturn(png(2, 2));
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            new M20260807_PageScanSnapshot().apply(connection, "TEXT");

            Exception failure = assertThrows(
                    Exception.class,
                    () -> PageScanSnapshotStore.persist(
                            connection,
                            7,
                            32,
                            null,
                            "Job",
                            expectedPage,
                            List.of(),
                            diagnostics.toString(),
                            staging -> PageScanArtifactCapture.capture(
                                    browser, expectedPage, List.of(), staging, "viewport")));

            assertTrue(failure.getMessage().contains("browser page changed"));
            assertSnapshotState(connection, "FAILED", "");
            assertNoCaptureFiles(diagnostics.resolve("page_diagnostics").resolve("Scanned"));
        }
    }

    @Test
    void oversizedWriterPayloadCannotBecomeReady() throws Exception {
        Path diagnostics = temporaryDirectory.resolve("oversized-payload");
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            new M20260807_PageScanSnapshot().apply(connection, "TEXT");

            Exception failure = assertThrows(
                    Exception.class,
                    () -> PageScanSnapshotStore.persist(
                            connection,
                            7,
                            32,
                            null,
                            "Job",
                            ScannedPageIdentity.fromLiveUrl("https://bank.example/accounts"),
                            List.of(),
                            diagnostics.toString(),
                            staging -> {
                                Files.write(
                                        staging.resolve("screenshot.png"),
                                        new byte[(int) PageScanArtifactPolicy.MAX_SCREENSHOT_BYTES + 1]);
                                Files.writeString(staging.resolve("rects.json"), "[]");
                                return PageScanSnapshotStore.CaptureMetadata.unavailable();
                            }));

            assertTrue(failure.getMessage().contains("exceeds its safe size"));
            assertSnapshotState(connection, "FAILED", "");
            assertNoCaptureFiles(diagnostics.resolve("page_diagnostics").resolve("Scanned"));
        }
    }

    @Test
    void writerPolicyMatchesEveryBoundedReaderLimit() throws Exception {
        assertEquals(1_000_000L, PageScanArtifactPolicy.maximumBytes("manifest.json"));
        assertEquals(1_000_000L, PageScanArtifactPolicy.maximumBytes("meta.json"));
        assertEquals(16_000_000L, PageScanArtifactPolicy.maximumBytes("elements.json"));
        assertEquals(16_000_000L, PageScanArtifactPolicy.maximumBytes("rects.json"));
        assertEquals(8_000_000L, PageScanArtifactPolicy.maximumBytes("screenshot.png"));
        for (String fileName : List.of(
                "manifest.json", "meta.json", "elements.json", "rects.json", "screenshot.png")) {
            long maximum = PageScanArtifactPolicy.maximumBytes(fileName);
            assertDoesNotThrow(() -> PageScanArtifactPolicy.requireWritableSize(fileName, maximum));
            assertThrows(
                    java.io.IOException.class,
                    () -> PageScanArtifactPolicy.requireWritableSize(fileName, maximum + 1));
        }
    }

    private static int count(java.sql.Connection connection) throws Exception {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM page_scan_snapshot")) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private static PageScanSnapshotStore.CaptureMetadata writeMinimalCapture(Path staging)
            throws Exception {
        Files.writeString(staging.resolve("screenshot.png"), "png");
        Files.writeString(staging.resolve("rects.json"), "[]");
        return PageScanSnapshotStore.CaptureMetadata.unavailable();
    }

    private byte[] png(int width, int height) throws Exception {
        Path source = temporaryDirectory.resolve("source-" + width + "x" + height + ".png");
        RasterImageIO.writePng(new RasterImage(width, height, new int[width * height]), source);
        return Files.readAllBytes(source);
    }

    private static void assertNoCaptureFiles(Path scannedRoot) throws Exception {
        if (!Files.exists(scannedRoot)) return;
        try (var paths = Files.walk(scannedRoot)) {
            assertFalse(paths.anyMatch(Files::isRegularFile));
        }
    }

    private static void assertSnapshotState(
            java.sql.Connection connection, String expectedStatus, String expectedArtifactPath)
            throws Exception {
        try (var statement = connection.createStatement();
                var row = statement.executeQuery(
                        "SELECT status, artifact_path FROM page_scan_snapshot")) {
            assertTrue(row.next());
            assertEquals(expectedStatus, row.getString("status"));
            assertEquals(expectedArtifactPath, row.getString("artifact_path"));
            assertFalse(row.next());
        }
    }
}

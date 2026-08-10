package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.db.migrations.M20260807_PageScanSnapshot;
import com.allinweb.ch.util.PageDiagnosticDumper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

@Isolated("Mutates snapshot storage health and ARPropertyManager retention settings")
class PageScanSnapshotRetentionServiceTest {

    private static final int HOME_BANKING_ID = 7;
    private static final int BOT_JOB_ID = 42;

    @TempDir
    Path temporaryDirectory;

    private PageScanSnapshotTestState state;
    private Path snapshotRoot;

    @BeforeEach
    void isolateSnapshotConfiguration() throws Exception {
        state = PageScanSnapshotTestState.isolate(temporaryDirectory);
        snapshotRoot = temporaryDirectory
                .resolve(PageDiagnosticDumper.SUBFOLDER)
                .resolve("Scanned");
    }

    @AfterEach
    void restoreSnapshotConfiguration() throws Exception {
        state.close();
    }

    @Test
    void summaryIsOwnerScopedPreservesPinnedAndAlwaysKeepsNewestUnpinnedPerPage()
            throws Exception {
        state.setPolicy(30, 2);
        try (Connection connection = database()) {
            insert(connection, "alpha-new", HOME_BANKING_ID, BOT_JOB_ID, "alpha",
                    "2099-01-01T00:00:00Z", "alpha-new", false, "READY");
            insert(connection, "alpha-middle", HOME_BANKING_ID, BOT_JOB_ID, "alpha",
                    "2000-01-02T00:00:00Z", "alpha-middle", false, "READY");
            insert(connection, "alpha-old", HOME_BANKING_ID, BOT_JOB_ID, "alpha",
                    "2000-01-01T00:00:00Z", "alpha-old", false, "READY");
            insert(connection, "alpha-pinned", HOME_BANKING_ID, BOT_JOB_ID, "alpha",
                    "1999-01-01T00:00:00Z", "alpha-pinned", true, "READY");
            insert(connection, "beta-only", HOME_BANKING_ID, BOT_JOB_ID, "beta",
                    "1998-01-01T00:00:00Z", "beta-only", false, "READY");
            insert(connection, "other-owner-new", 8, BOT_JOB_ID, "alpha",
                    "2099-01-01T00:00:00Z", "other-owner-new", false, "READY");
            insert(connection, "other-owner-old", 8, BOT_JOB_ID, "alpha",
                    "1990-01-01T00:00:00Z", "other-owner-old", false, "READY");
            insert(connection, "failed", HOME_BANKING_ID, BOT_JOB_ID, "alpha",
                    "1990-01-01T00:00:00Z", "", false, "FAILED");

            PageScanSnapshotRetentionService.Summary summary =
                    PageScanSnapshotRetentionService.getInstance()
                            .summary(connection, HOME_BANKING_ID, BOT_JOB_ID);

            assertEquals(new PageScanSnapshotRetentionService.Policy(30, 2), summary.policy());
            assertEquals(5, summary.readyCount());
            assertEquals(1, summary.pinnedCount());
            assertEquals(2, summary.eligibleCount());
        }
    }

    @Test
    void pinUsesAnExactReadyOwnerRowAndRestoresTheCallerConnectionState() throws Exception {
        state.setPolicy(0, 0);
        try (Connection connection = database()) {
            insert(connection, "owned", HOME_BANKING_ID, BOT_JOB_ID, "alpha",
                    "2026-08-01T00:00:00Z", "owned", false, "READY");
            insert(connection, "other", 8, BOT_JOB_ID, "alpha",
                    "2026-08-01T00:00:00Z", "other", false, "READY");
            int originalIsolation = connection.getTransactionIsolation();

            PageScanSnapshotRetentionService.PinResult result =
                    PageScanSnapshotRetentionService.getInstance().pin(
                            connection, HOME_BANKING_ID, BOT_JOB_ID, "owned", true);

            assertTrue(result.pinned());
            assertEquals(1, result.summary().pinnedCount());
            assertTrue(pinned(connection, "owned"));
            assertFalse(pinned(connection, "other"));
            assertTrue(connection.getAutoCommit());
            assertEquals(originalIsolation, connection.getTransactionIsolation());

            PageScanSnapshotRetentionService.StaleSnapshotException stale = assertThrows(
                    PageScanSnapshotRetentionService.StaleSnapshotException.class,
                    () -> PageScanSnapshotRetentionService.getInstance().pin(
                            connection, HOME_BANKING_ID, BOT_JOB_ID, "other", true));
            assertEquals("other", stale.scanId());
            assertTrue(connection.getAutoCommit());
        }
    }

    @Test
    void purgeRejectsAStaleDisplayedPolicyBeforeChangingRowsOrArtifacts() throws Exception {
        state.setPolicy(30, 2);
        try (Connection connection = database()) {
            insert(connection, "owned", HOME_BANKING_ID, BOT_JOB_ID, "alpha",
                    "2026-08-01T00:00:00Z", "owned", false, "READY");

            PageScanSnapshotRetentionService.StaleRetentionPolicyException stale = assertThrows(
                    PageScanSnapshotRetentionService.StaleRetentionPolicyException.class,
                    () -> PageScanSnapshotRetentionService.getInstance().purgeConfigured(
                            connection, HOME_BANKING_ID, BOT_JOB_ID, 29, 2));

            assertEquals(new PageScanSnapshotRetentionService.Policy(29, 2),
                    stale.expectedPolicy());
            assertEquals(new PageScanSnapshotRetentionService.Policy(30, 2),
                    stale.currentPolicy());
            assertEquals(1, rowCount(connection, HOME_BANKING_ID, BOT_JOB_ID));
            assertTrue(connection.getAutoCommit());
        }
    }

    @Test
    void purgeMakesProgressPastMissingArtifactsAndNeverTouchesPinnedOrOtherOwners()
            throws Exception {
        state.setPolicy(0, 1);
        try (Connection connection = database()) {
            String newest = artifactPath("page", "2099-01-01T00:00:00Z", "newest");
            String older = artifactPath("page", "2000-01-02T00:00:00Z", "older");
            String missing = artifactPath("page", "2000-01-01T00:00:00Z", "missing");
            String pinned = artifactPath("page", "1999-01-01T00:00:00Z", "pinned");
            createArtifact(newest);
            createArtifact(older);
            createArtifact(pinned);
            insert(connection, "newest", HOME_BANKING_ID, BOT_JOB_ID, "page",
                    "2099-01-01T00:00:00Z", newest, false, "READY");
            insert(connection, "older", HOME_BANKING_ID, BOT_JOB_ID, "page",
                    "2000-01-02T00:00:00Z", older, false, "READY");
            insert(connection, "missing", HOME_BANKING_ID, BOT_JOB_ID, "page",
                    "2000-01-01T00:00:00Z", missing, false, "READY");
            insert(connection, "pinned", HOME_BANKING_ID, BOT_JOB_ID, "page",
                    "1999-01-01T00:00:00Z", pinned, true, "READY");
            insert(connection, "other", 8, BOT_JOB_ID, "page",
                    "1998-01-01T00:00:00Z", "other", false, "READY");

            PageScanSnapshotRetentionService.PurgeResult result =
                    PageScanSnapshotRetentionService.getInstance().purgeConfigured(
                            connection, HOME_BANKING_ID, BOT_JOB_ID, 0, 1);

            assertEquals(List.of("missing", "older"), result.purgedScanIds());
            assertFalse(result.cleanupPending());
            assertEquals(2, result.summary().readyCount());
            assertEquals(1, result.summary().pinnedCount());
            assertEquals(0, result.summary().eligibleCount());
            assertEquals(2, rowCount(connection, HOME_BANKING_ID, BOT_JOB_ID));
            assertEquals(1, rowCount(connection, 8, BOT_JOB_ID));
            assertTrue(Files.isDirectory(snapshotRoot.resolve(newest)));
            assertFalse(Files.exists(snapshotRoot.resolve(older)));
            assertFalse(Files.exists(snapshotRoot.resolve(missing)));
            assertTrue(Files.isDirectory(snapshotRoot.resolve(pinned)));
            assertTrue(connection.getAutoCommit());
        }
    }

    private Connection database() throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        new M20260807_PageScanSnapshot().apply(connection, "TEXT");
        return connection;
    }

    private void createArtifact(String relative) throws Exception {
        Path capture = snapshotRoot.resolve(relative);
        Files.createDirectories(capture);
        Files.writeString(capture.resolve("manifest.json"), "{}");
        PageScanSnapshotFileSecurity.secureDirectoryTree(snapshotRoot, capture);
    }

    private static void insert(
            Connection connection,
            String scanId,
            int homeBankingId,
            int botJobId,
            String pageKey,
            String capturedAt,
            String artifactPath,
            boolean pinned,
            String status)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO page_scan_snapshot "
                        + "(scan_id,home_banking_id,bot_job_id,home_url_id,page_key,page_url,"
                        + "captured_at,element_count,artifact_path,manifest_sha256,status,pinned) "
                        + "VALUES (?,?,?,NULL,?,'https://example.test',?,0,?,'sha',?,?)")) {
            statement.setString(1, scanId);
            statement.setInt(2, homeBankingId);
            statement.setInt(3, botJobId);
            statement.setString(4, pageKey);
            statement.setString(5, capturedAt);
            statement.setString(6, artifactPath);
            statement.setString(7, status);
            statement.setInt(8, pinned ? 1 : 0);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private String artifactPath(String pageKey, String capturedAt, String scanId) {
        return "org-" + HOME_BANKING_ID
                + "/bot-job-" + BOT_JOB_ID
                + "/" + pageKey
                + "/" + capturedAt.replace(':', '-') + "-" + scanId;
    }

    private static boolean pinned(Connection connection, String scanId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT pinned FROM page_scan_snapshot WHERE scan_id=?")) {
            statement.setString(1, scanId);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getInt(1) != 0;
            }
        }
    }

    private static int rowCount(Connection connection, int homeBankingId, int botJobId)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM page_scan_snapshot "
                        + "WHERE home_banking_id=? AND bot_job_id=? AND status='READY'")) {
            statement.setInt(1, homeBankingId);
            statement.setInt(2, botJobId);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getInt(1);
            }
        }
    }
}

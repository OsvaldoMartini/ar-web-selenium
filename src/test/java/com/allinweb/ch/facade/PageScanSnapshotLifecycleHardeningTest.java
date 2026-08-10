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
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

@Isolated("Mutates snapshot storage health and ARPropertyManager PATH_DB")
class PageScanSnapshotLifecycleHardeningTest {

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
    void creationRecoveryDeletesCrashLeftFoldersAndRecordsFailedUsingSchemaSafeValues()
            throws Exception {
        String scanId = "11111111-1111-1111-1111-111111111111";
        String capturedAt = "2026-08-09T10:11:12Z";
        String safePageKey = "payments_account";
        String finalName = capturedAt.replace(':', '-') + "-" + scanId;
        Path owner = snapshotRoot
                .resolve("org-" + HOME_BANKING_ID)
                .resolve("bot-job-" + BOT_JOB_ID)
                .resolve(safePageKey);
        Path staging = owner.resolve("." + finalName + ".staging");
        Path finalized = owner.resolve(finalName);
        Files.createDirectories(staging);
        Files.createDirectories(finalized);
        Files.writeString(staging.resolve("elements.json"), "[]");
        Files.writeString(finalized.resolve("manifest.json"), "{}");

        try (Connection connection = database(false)) {
            insertSnapshot(
                    connection,
                    scanId,
                    HOME_BANKING_ID,
                    BOT_JOB_ID,
                    "payments/account",
                    capturedAt,
                    "staged-artifact",
                    "staged-sha",
                    "STAGED");
            insertSnapshot(
                    connection,
                    "22222222-2222-2222-2222-222222222222",
                    HOME_BANKING_ID,
                    BOT_JOB_ID,
                    "payments/account",
                    "2026-08-09T10:12:12Z",
                    "ready-artifact",
                    "ready-sha",
                    "READY");

            PageScanSnapshotCreationLifecycle.reconcile(connection);

            assertFalse(Files.exists(staging));
            assertFalse(Files.exists(finalized));
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT status,artifact_path,manifest_sha256 "
                            + "FROM page_scan_snapshot WHERE scan_id=?")) {
                statement.setString(1, scanId);
                try (ResultSet rows = statement.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals("FAILED", rows.getString("status"));
                    assertEquals("", rows.getString("artifact_path"));
                    assertEquals("", rows.getString("manifest_sha256"));
                }
            }
            assertEquals("READY", status(connection,
                    "22222222-2222-2222-2222-222222222222"));
        }
    }

    @Test
    void creationRecoveryRefusesToJoinAnExistingTransaction() throws Exception {
        try (Connection connection = database(false)) {
            connection.setAutoCommit(false);

            SQLException failure = assertThrows(
                    SQLException.class,
                    () -> PageScanSnapshotCreationLifecycle.reconcile(connection));

            assertTrue(failure.getMessage().contains("independent database connection"));
            connection.rollback();
        }
    }

    @Test
    void generationBoundDeletionJournalRestoresTheExactUnchangedGeneration() throws Exception {
        Path artifactRoot = createBotJobArtifact("old-capture");
        try (Connection connection = database(true)) {
            insertBotJob(connection, BOT_JOB_ID, HOME_BANKING_ID);
            insertSnapshot(
                    connection,
                    "old-scan",
                    HOME_BANKING_ID,
                    BOT_JOB_ID,
                    "page",
                    "2026-08-01T00:00:00Z",
                    "org-7/bot-job-42/page/old-capture",
                    "old-sha",
                    "READY");
            PageScanSnapshotArtifactLifecycle lifecycle =
                    new PageScanSnapshotArtifactLifecycle(snapshotRoot);

            PageScanSnapshotArtifactLifecycle.Plan plan =
                    lifecycle.stage(connection, List.of(BOT_JOB_ID));
            assertEquals("page-scan-delete-v2", plan.journalVersion());
            assertFalse(Files.exists(artifactRoot));

            lifecycle.reconcile(connection);

            assertTrue(Files.isDirectory(artifactRoot));
            assertTrue(Files.exists(artifactRoot.resolve("page/old-capture/manifest.json")));
        }
    }

    @Test
    void reusedBotJobIdCannotRestoreArtifactsFromThePreviousOwnerGeneration() throws Exception {
        Path artifactRoot = createBotJobArtifact("old-capture");
        try (Connection connection = database(true)) {
            insertBotJob(connection, BOT_JOB_ID, HOME_BANKING_ID);
            insertSnapshot(
                    connection,
                    "old-scan",
                    HOME_BANKING_ID,
                    BOT_JOB_ID,
                    "page",
                    "2026-08-01T00:00:00Z",
                    "org-7/bot-job-42/page/old-capture",
                    "old-sha",
                    "READY");
            PageScanSnapshotArtifactLifecycle lifecycle =
                    new PageScanSnapshotArtifactLifecycle(snapshotRoot);
            lifecycle.stage(connection, List.of(BOT_JOB_ID));

            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM page_scan_snapshot");
                statement.executeUpdate("DELETE FROM bot_job WHERE id=" + BOT_JOB_ID);
            }
            insertBotJob(connection, BOT_JOB_ID, 8);
            insertSnapshot(
                    connection,
                    "new-scan",
                    8,
                    BOT_JOB_ID,
                    "page",
                    "2026-08-09T00:00:00Z",
                    "org-8/bot-job-42/page/new-capture",
                    "new-sha",
                    "READY");

            lifecycle.reconcile(connection);

            assertFalse(Files.exists(artifactRoot));
        }
    }

    @Test
    void changedReadySnapshotHashCannotRestoreAnOldSameOwnerGeneration() throws Exception {
        Path artifactRoot = createBotJobArtifact("old-capture");
        try (Connection connection = database(true)) {
            insertBotJob(connection, BOT_JOB_ID, HOME_BANKING_ID);
            insertSnapshot(
                    connection,
                    "old-scan",
                    HOME_BANKING_ID,
                    BOT_JOB_ID,
                    "page",
                    "2026-08-01T00:00:00Z",
                    "org-7/bot-job-42/page/old-capture",
                    "old-sha",
                    "READY");
            PageScanSnapshotArtifactLifecycle lifecycle =
                    new PageScanSnapshotArtifactLifecycle(snapshotRoot);
            lifecycle.stage(connection, List.of(BOT_JOB_ID));

            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE page_scan_snapshot SET manifest_sha256=? WHERE scan_id=?")) {
                update.setString(1, "new-sha");
                update.setString(2, "old-scan");
                assertEquals(1, update.executeUpdate());
            }

            lifecycle.reconcile(connection);

            assertFalse(Files.exists(artifactRoot));
        }
    }

    private Connection database(boolean withBotJob) throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        new M20260807_PageScanSnapshot().apply(connection, "TEXT");
        if (withBotJob) {
            connection.createStatement().executeUpdate(
                    "CREATE TABLE bot_job(id INTEGER PRIMARY KEY, home_banking_id INTEGER NOT NULL)");
        }
        return connection;
    }

    private Path createBotJobArtifact(String captureName) throws Exception {
        Path botJob = snapshotRoot
                .resolve("org-" + HOME_BANKING_ID)
                .resolve("bot-job-" + BOT_JOB_ID);
        Path capture = botJob.resolve("page").resolve(captureName);
        Files.createDirectories(capture);
        Files.writeString(capture.resolve("manifest.json"), "{}");
        PageScanSnapshotFileSecurity.secureDirectoryTree(snapshotRoot, botJob);
        return botJob;
    }

    private static void insertBotJob(Connection connection, int botJobId, int homeBankingId)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO bot_job(id,home_banking_id) VALUES (?,?)")) {
            statement.setInt(1, botJobId);
            statement.setInt(2, homeBankingId);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void insertSnapshot(
            Connection connection,
            String scanId,
            int homeBankingId,
            int botJobId,
            String pageKey,
            String capturedAt,
            String artifactPath,
            String manifestSha256,
            String status)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO page_scan_snapshot "
                        + "(scan_id,home_banking_id,bot_job_id,home_url_id,page_key,page_url,"
                        + "captured_at,element_count,artifact_path,manifest_sha256,status,pinned) "
                        + "VALUES (?,?,?,NULL,?,'https://example.test',?,0,?,?,?,0)")) {
            statement.setString(1, scanId);
            statement.setInt(2, homeBankingId);
            statement.setInt(3, botJobId);
            statement.setString(4, pageKey);
            statement.setString(5, capturedAt);
            statement.setString(6, artifactPath);
            statement.setString(7, manifestSha256);
            statement.setString(8, status);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static String status(Connection connection, String scanId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT status FROM page_scan_snapshot WHERE scan_id=?")) {
            statement.setString(1, scanId);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getString(1);
            }
        }
    }
}

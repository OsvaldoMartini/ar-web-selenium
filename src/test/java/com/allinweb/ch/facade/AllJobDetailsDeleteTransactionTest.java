package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.db.migrations.M20260807_PageScanSnapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

@Isolated("Mutates snapshot storage health and ARPropertyManager PATH_DB")
class AllJobDetailsDeleteTransactionTest {

    @TempDir
    Path tempDir;

    private PageScanSnapshotTestState snapshotState;

    @BeforeEach
    void isolateSnapshotStorage() throws Exception {
        snapshotState = PageScanSnapshotTestState.isolate(tempDir);
    }

    @AfterEach
    void restoreSnapshotStorage() throws Exception {
        snapshotState.close();
    }

    @Test
    void clearsSnapshotRowsAndEveryStrictBotJobArtifactRoot() throws Exception {
        try (Connection connection = database("all.db")) {
            createSchema(connection);
            seed(connection);
            Path owned = artifactRoot().resolve("org-2/bot-job-1/page/elements.json");
            Path orphan = artifactRoot().resolve("org-9/bot-job-999/page/elements.json");
            Path unrelated = artifactRoot().resolve("notes/keep.txt");
            write(owned);
            write(orphan);
            write(unrelated);

            transaction().execute(connection);

            assertEquals(0, count(connection, "bot_job"));
            assertEquals(0, count(connection, "page_scan_snapshot"));
            assertEquals(0, count(connection, "scanned_element"));
            assertEquals(0, count(connection, "component_block"));
            assertTrue(Files.notExists(owned));
            assertTrue(Files.notExists(orphan));
            assertTrue(Files.exists(unrelated));
            assertTrue(connection.getAutoCommit());
        }
    }

    @Test
    void databaseFailureRestoresEveryStagedArtifactAndAllRows() throws Exception {
        try (Connection connection = database("rollback.db")) {
            createSchema(connection);
            seed(connection);
            Path artifact = artifactRoot().resolve("org-2/bot-job-1/page/elements.json");
            write(artifact);
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TRIGGER reject_bot_job_delete BEFORE DELETE ON bot_job "
                        + "BEGIN SELECT RAISE(ABORT,'delete rejected'); END");
            }

            assertThrows(SQLException.class, () -> transaction().execute(connection));

            assertEquals(1, count(connection, "bot_job"));
            assertEquals(1, count(connection, "page_scan_snapshot"));
            assertEquals(1, count(connection, "scanned_element"));
            assertEquals(1, count(connection, "component_block"));
            assertTrue(Files.exists(artifact));
            assertTrue(connection.getAutoCommit());
        }
    }

    @Test
    void replacementObserverRunsAfterCommitAndBeforeArtifactPurge()
            throws Exception {
        try (Connection connection = database("all-commit-observer.db")) {
            createSchema(connection);
            seed(connection);
            Path artifact = artifactRoot().resolve("org-2/bot-job-1/page/elements.json");
            write(artifact);
            AtomicInteger notifications = new AtomicInteger();
            AtomicBoolean databaseCommitted = new AtomicBoolean();
            AtomicBoolean artifactStillStaged = new AtomicBoolean();

            transaction().execute(connection, () -> {
                notifications.incrementAndGet();
                try {
                    databaseCommitted.set(count(connection, "bot_job") == 0);
                    try (var pending = Files.walk(
                            artifactRoot().resolve(".delete-pending"))) {
                        artifactStillStaged.set(pending.anyMatch(
                                path -> path.endsWith("elements.json")));
                    }
                } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                }
            });

            assertEquals(1, notifications.get());
            assertTrue(databaseCommitted.get());
            assertTrue(artifactStillStaged.get());
            assertTrue(Files.notExists(artifact));
        }
    }

    private AllJobDetailsDeleteTransaction transaction() {
        return new AllJobDetailsDeleteTransaction(
                new PageScanSnapshotArtifactLifecycle(artifactRoot()));
    }

    private Path artifactRoot() {
        return tempDir.resolve("page_diagnostics/Scanned");
    }

    private Connection database(String name) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + tempDir.resolve(name).toAbsolutePath());
    }

    private void createSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE bot_job(id INTEGER PRIMARY KEY)");
            statement.execute("CREATE TABLE scanned_element(id INTEGER,bot_job_id INTEGER)");
            statement.execute("CREATE TABLE component_block(id INTEGER)");
        }
        new M20260807_PageScanSnapshot().apply(connection, "TEXT");
    }

    private void seed(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO bot_job VALUES(1)");
            statement.execute("INSERT INTO page_scan_snapshot("
                    + "scan_id,home_banking_id,bot_job_id,home_url_id,page_key,page_url,"
                    + "captured_at,element_count,artifact_path,manifest_sha256,status,pinned) "
                    + "VALUES('scan',2,1,NULL,'page','https://bank.example/page',"
                    + "'2026-08-13T00:00:00Z',1,'org-2/bot-job-1/page','hash','READY',0)");
            statement.execute("INSERT INTO scanned_element VALUES(1,1)");
            statement.execute("INSERT INTO component_block VALUES(1)");
        }
    }

    private void write(Path path) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, "[]");
    }

    private int count(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rows.next();
            return rows.getInt(1);
        }
    }
}

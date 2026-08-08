package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PageScanSnapshotArtifactLifecycleTest {

    @TempDir
    Path tempDir;

    @Test
    void reconciliationRestoresArtifactsWhenTheBotJobStillExists() throws Exception {
        Path artifact = artifact(5);
        write(artifact);
        PageScanSnapshotArtifactLifecycle lifecycle = lifecycle();
        try (Connection connection = database()) {
            PageScanSnapshotArtifactLifecycle.Plan ignored = lifecycle.stage(List.of(5));
            assertTrue(Files.notExists(artifact));

            lifecycle.reconcile(connection);

            assertTrue(Files.exists(artifact));
        }
    }

    @Test
    void reconciliationPurgesArtifactsAfterTheBotJobCommit() throws Exception {
        Path artifact = artifact(5);
        write(artifact);
        PageScanSnapshotArtifactLifecycle lifecycle = lifecycle();
        try (Connection connection = database()) {
            PageScanSnapshotArtifactLifecycle.Plan ignored = lifecycle.stage(List.of(5));
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM bot_job WHERE id=5");
            }

            lifecycle.reconcile(connection);

            assertTrue(Files.notExists(artifact));
        }
    }

    @Test
    void targetedStageNeverMovesAnotherBotJobsArtifacts() throws Exception {
        Path selected = artifact(5);
        Path preserved = artifact(6);
        write(selected);
        write(preserved);
        PageScanSnapshotArtifactLifecycle lifecycle = lifecycle();

        PageScanSnapshotArtifactLifecycle.Plan plan = lifecycle.stage(List.of(5));
        lifecycle.purge(plan);

        assertTrue(Files.notExists(selected));
        assertTrue(Files.exists(preserved));
    }

    private PageScanSnapshotArtifactLifecycle lifecycle() {
        return new PageScanSnapshotArtifactLifecycle(tempDir.resolve("Scanned"));
    }

    private Path artifact(int botJobId) {
        return tempDir.resolve("Scanned/org-2/bot-job-" + botJobId + "/page/elements.json");
    }

    private void write(Path path) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, "[]");
    }

    private Connection database() throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE bot_job(id INTEGER PRIMARY KEY)");
            statement.execute("INSERT INTO bot_job VALUES(5),(6)");
        }
        return connection;
    }
}

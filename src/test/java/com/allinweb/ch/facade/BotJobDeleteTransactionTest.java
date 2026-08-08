package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BotJobDeleteTransactionTest {

    @TempDir
    Path tempDir;

    @Test
    void deletesSelectedBotJobsAndEveryNonCascadingOwnedRow() throws Exception {
        try (Connection connection = database("cleanup.db")) {
            createCompleteSchema(connection);
            seedTwoBotJobs(connection);
            seedArtifacts(1, 2);

            List<Integer> deleted = transaction().execute(connection, List.of(1));

            assertEquals(List.of(1), deleted);
            assertEquals(0, count(connection, "bot_job", "id=1"));
            assertEquals(0, count(connection, "block", "bot_job_id=1"));
            assertEquals(0, count(connection, "instruction_variable_slot", "bot_job_id=1"));
            assertEquals(0, count(connection, "instruction_variable_command_config", "bot_job_id=1"));
            assertEquals(0, count(connection, "instruction_graph_state",
                    "workspace_kind='BOT_JOB' AND owner_id=1"));
            assertEquals(0, count(connection, "scanned_element", "bot_job_id=1"));
            assertEquals(0, count(connection, "page_scan_snapshot", "bot_job_id=1"));
            assertEquals(0, count(connection, "bot_job_variable_migration_note", "bot_job_id=1"));
            assertTrue(Files.notExists(artifactRoot(1)));

            assertEquals(1, count(connection, "bot_job", "id=2"));
            assertEquals(1, count(connection, "instruction_variable_slot", "bot_job_id=2"));
            assertEquals(1, count(connection, "instruction_variable_command_config", "bot_job_id=2"));
            assertEquals(1, count(connection, "instruction_graph_state",
                    "workspace_kind='BOT_JOB' AND owner_id=2"));
            assertEquals(1, count(connection, "scanned_element", "bot_job_id=2"));
            assertEquals(1, count(connection, "page_scan_snapshot", "bot_job_id=2"));
            assertEquals(1, count(connection, "bot_job_variable_migration_note", "bot_job_id=2"));
            assertTrue(Files.exists(artifactRoot(2).resolve("page/elements.json")));
            assertEquals(1, count(connection, "instruction_graph_state",
                    "workspace_kind='COMPONENT' AND owner_id=1"));
            assertTrue(connection.getAutoCommit());
        }
    }

    @Test
    void missingBotJobRollsBackTheCompleteSelection() throws Exception {
        try (Connection connection = database("missing.db")) {
            createCompleteSchema(connection);
            seedTwoBotJobs(connection);
            seedArtifacts(1, 2);

            assertThrows(
                    SQLException.class,
                    () -> transaction().execute(connection, List.of(1, 999)));

            assertEquals(1, count(connection, "bot_job", "id=1"));
            assertEquals(1, count(connection, "block", "bot_job_id=1"));
            assertEquals(1, count(connection, "instruction_variable_slot", "bot_job_id=1"));
            assertEquals(1, count(connection, "instruction_variable_command_config", "bot_job_id=1"));
            assertEquals(1, count(connection, "instruction_graph_state",
                    "workspace_kind='BOT_JOB' AND owner_id=1"));
            assertEquals(1, count(connection, "scanned_element", "bot_job_id=1"));
            assertEquals(1, count(connection, "page_scan_snapshot", "bot_job_id=1"));
            assertEquals(1, count(connection, "bot_job_variable_migration_note", "bot_job_id=1"));
            assertTrue(Files.exists(artifactRoot(1).resolve("page/elements.json")));
            assertTrue(connection.getAutoCommit());
        }
    }

    @Test
    void databaseFailureRestoresChildrenAndParentsDeletedEarlierInTheBatch() throws Exception {
        try (Connection connection = database("failure.db")) {
            createCompleteSchema(connection);
            seedTwoBotJobs(connection);
            seedArtifacts(1, 2);
            try (Statement sql = connection.createStatement()) {
                sql.execute("CREATE TRIGGER reject_second_bot_job BEFORE DELETE ON bot_job "
                        + "WHEN OLD.id=2 BEGIN SELECT RAISE(ABORT,'delete rejected'); END");
            }

            assertThrows(
                    SQLException.class,
                    () -> transaction().execute(connection, List.of(1, 2)));

            assertEquals(2, count(connection, "bot_job", "1=1"));
            assertEquals(2, count(connection, "block", "1=1"));
            assertEquals(2, count(connection, "instruction_variable_slot", "1=1"));
            assertEquals(2, count(connection, "instruction_variable_command_config", "1=1"));
            assertEquals(2, count(connection, "instruction_graph_state",
                    "workspace_kind='BOT_JOB'"));
            assertEquals(2, count(connection, "scanned_element", "1=1"));
            assertEquals(2, count(connection, "page_scan_snapshot", "1=1"));
            assertEquals(2, count(connection, "bot_job_variable_migration_note", "1=1"));
            assertTrue(Files.exists(artifactRoot(1).resolve("page/elements.json")));
            assertTrue(Files.exists(artifactRoot(2).resolve("page/elements.json")));
            assertTrue(connection.getAutoCommit());
        }
    }

    @Test
    void toleratesInstallationsWhereOptionalOwnedTablesDoNotExist() throws Exception {
        try (Connection connection = database("minimal.db");
                Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE bot_job(id INTEGER PRIMARY KEY,name TEXT)");
            sql.execute("INSERT INTO bot_job VALUES(1,'One')");

            transaction().execute(connection, List.of(1));

            assertEquals(0, count(connection, "bot_job", "id=1"));
            assertTrue(connection.getAutoCommit());
        }
    }

    private Connection database(String fileName) throws SQLException {
        Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + tempDir.resolve(fileName).toAbsolutePath());
        try (Statement sql = connection.createStatement()) {
            sql.execute("PRAGMA foreign_keys=ON");
        }
        return connection;
    }

    private void createCompleteSchema(Connection connection) throws SQLException {
        try (Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE bot_job(id INTEGER PRIMARY KEY,name TEXT)");
            sql.execute("CREATE TABLE block(id INTEGER PRIMARY KEY,bot_job_id INTEGER,"
                    + "FOREIGN KEY(bot_job_id) REFERENCES bot_job(id) ON DELETE CASCADE)");
            sql.execute("CREATE TABLE instruction_variable_slot(bot_job_id INTEGER,value TEXT)");
            sql.execute("CREATE TABLE instruction_variable_command_config(bot_job_id INTEGER,value TEXT)");
            sql.execute("CREATE TABLE instruction_graph_state(workspace_kind TEXT,owner_id INTEGER,value TEXT)");
            sql.execute("CREATE TABLE scanned_element(bot_job_id INTEGER,value TEXT)");
            sql.execute("CREATE TABLE page_scan_snapshot(scan_id TEXT PRIMARY KEY,"
                    + "home_banking_id INTEGER,bot_job_id INTEGER,artifact_path TEXT)");
            sql.execute("CREATE TABLE bot_job_variable_migration_note(bot_job_id INTEGER,value TEXT)");
        }
    }

    private void seedTwoBotJobs(Connection connection) throws SQLException {
        try (Statement sql = connection.createStatement()) {
            sql.execute("INSERT INTO bot_job VALUES(1,'One'),(2,'Two')");
            sql.execute("INSERT INTO block VALUES(11,1),(22,2)");
            sql.execute("INSERT INTO instruction_variable_slot VALUES(1,'one'),(2,'two')");
            sql.execute("INSERT INTO instruction_variable_command_config VALUES(1,'one'),(2,'two')");
            sql.execute("INSERT INTO instruction_graph_state VALUES"
                    + "('BOT_JOB',1,'one'),('BOT_JOB',2,'two'),('COMPONENT',1,'component')");
            sql.execute("INSERT INTO scanned_element VALUES(1,'one'),(2,'two')");
            sql.execute("INSERT INTO page_scan_snapshot VALUES"
                    + "('scan-one',2,1,'org-2/bot-job-1/page'),"
                    + "('scan-two',2,2,'org-2/bot-job-2/page')");
            sql.execute("INSERT INTO bot_job_variable_migration_note VALUES(1,'one'),(2,'two')");
        }
    }

    private BotJobDeleteTransaction transaction() {
        return new BotJobDeleteTransaction(
                new PageScanSnapshotArtifactLifecycle(snapshotRoot()));
    }

    private Path snapshotRoot() {
        return tempDir.resolve("page_diagnostics").resolve("Scanned");
    }

    private Path artifactRoot(int botJobId) {
        return snapshotRoot().resolve("org-2").resolve("bot-job-" + botJobId);
    }

    private void seedArtifacts(int... botJobIds) throws Exception {
        for (int botJobId : botJobIds) {
            Path page = artifactRoot(botJobId).resolve("page");
            Files.createDirectories(page);
            Files.writeString(page.resolve("elements.json"), "[]");
        }
    }

    private int count(Connection connection, String table, String predicate) throws SQLException {
        try (Statement sql = connection.createStatement();
                ResultSet rows = sql.executeQuery(
                        "SELECT COUNT(*) FROM " + table + " WHERE " + predicate)) {
            rows.next();
            return rows.getInt(1);
        }
    }
}

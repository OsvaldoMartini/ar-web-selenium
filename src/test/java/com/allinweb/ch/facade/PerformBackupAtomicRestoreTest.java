package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.util.ErrorMessage;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PerformBackupAtomicRestoreTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void stageCommitCallsRemainInsideTheOwningTransaction() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE imported_value(id INTEGER PRIMARY KEY, value TEXT)");
            connection.setAutoCommit(false);
            Connection stages = PerformBackup.commitSuppressingConnection(connection);

            statement.executeUpdate("INSERT INTO imported_value(id, value) VALUES (1, 'first')");
            stages.commit();
            assertEquals(1, countRows(connection, "imported_value"));
            assertThrows(SQLException.class, () -> stages.setAutoCommit(true));

            connection.rollback();
            assertEquals(0, countRows(connection, "imported_value"));
        }
    }

    @Test
    void malformedLateStageRollsBackThePreviouslyInsertedBotJob() throws Exception {
        Path snapshot = temporaryDirectory.resolve("bot-job.sql");
        Files.writeString(snapshot, """
                -- AR-WEB BOT JOB SNAPSHOT v1
                -- TABLE: home_banking
                INSERT INTO home_banking (id, url, name, priority, search_config, options_config, cookies, driver_session, username, password) VALUES (5, 'https://bank.example', 'Bank', 'Web App', '{}', '{}', '', '', '', '');
                -- TABLE: bot_job
                INSERT INTO bot_job (id, name, description, priority, home_banking_id, home_url_id, active) VALUES (42, 'Payments', 'Flow', 'Web App', 5, 8, 1);
                -- TABLE: block
                INSERT INTO block (id) VALUES (11);
                """, Charset.forName("windows-1252"));

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE bot_job (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT,
                        description TEXT,
                        priority TEXT,
                        home_banking_id INTEGER,
                        home_url_id INTEGER,
                        active INTEGER)
                    """);
            statement.execute("""
                    CREATE TABLE block (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        block_order_number INTEGER,
                        name TEXT,
                        description TEXT,
                        type_id INTEGER,
                        export_file TEXT,
                        active INTEGER,
                        wait INTEGER,
                        bot_job_id INTEGER)
                    """);

            ErrorMessage error = PerformBackup.getInstance().restoreBotJobFromSingleFile(
                    connection, snapshot.toString(), 5, 8, 42, "Bank");

            assertNotNull(error);
            assertEquals("Parse Error", error.getErrorTitle());
            assertEquals("Expected 9 values for block", error.getErrorHeader());
            assertEquals(0, countRows(connection, "bot_job"));
            assertEquals(0, countRows(connection, "block"));
            assertTrue(connection.getAutoCommit());
        }
    }

    @Test
    void currentInstructionSnapshotPreservesBothParentLinksAndClientName() throws Exception {
        Charset snapshotCharset = Charset.forName("windows-1252");
        Path botJobSnapshot = temporaryDirectory.resolve("bot-job-current.sql");
        Path blockSnapshot = temporaryDirectory.resolve("block-current.sql");
        Path instructionSnapshot = temporaryDirectory.resolve("instruction-current.sql");
        Files.writeString(
                botJobSnapshot,
                "INSERT INTO bot_job (id, name, description, priority, home_banking_id, home_url_id, active) "
                        + "VALUES (42, 'Payments', 'Flow', 'Web App', 5, 8, 1);",
                snapshotCharset);
        Files.writeString(
                blockSnapshot,
                """
                INSERT INTO block (id, block_order_number, name, description, type_id, export_file, active, wait, bot_job_id) VALUES (11, 1, 'Parent Block', 'Parent', 1, NULL, 1, 0, 42);
                INSERT INTO block (id, block_order_number, name, description, type_id, export_file, active, wait, bot_job_id) VALUES (12, 2, 'Child Block', 'Child', 1, NULL, 1, 0, 42);
                """,
                snapshotCharset);
        Files.writeString(
                instructionSnapshot,
                """
                INSERT INTO instruction (id, instruction_order_number, actions, name, xpath, coordinates, force_coordinates, iframe_xpath, tag_name, shadow_host, shadow_root, css_selector, description, operation, optional, block_marked, default_value, action_custom_max_wait_sec, on_hold_seconds, codified, export_to_abr, active, block_id, variable_id, parent_block_id, parent_id, bot_job_id, client_named) VALUES (100, 1, 'C', 'Parent', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'Parent', NULL, 0, 0, NULL, 30, 0, 0, 0, 1, 11, NULL, NULL, NULL, 42, 'Parent Alias');
                INSERT INTO instruction (id, instruction_order_number, actions, name, xpath, coordinates, force_coordinates, iframe_xpath, tag_name, shadow_host, shadow_root, css_selector, description, operation, optional, block_marked, default_value, action_custom_max_wait_sec, on_hold_seconds, codified, export_to_abr, active, block_id, variable_id, parent_block_id, parent_id, bot_job_id, client_named) VALUES (101, 1, 'C', 'Child', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'Child', NULL, 0, 0, NULL, 30, 0, 0, 0, 1, 12, NULL, 11, 100, 42, 'Child Alias');
                """,
                snapshotCharset);

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE bot_job (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT, description TEXT, priority TEXT,
                        home_banking_id INTEGER, home_url_id INTEGER, active INTEGER)
                    """);
            statement.execute("""
                    CREATE TABLE block (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        block_order_number INTEGER, name TEXT, description TEXT, type_id INTEGER,
                        export_file TEXT, active INTEGER, wait INTEGER, bot_job_id INTEGER)
                    """);
            statement.execute("""
                    CREATE TABLE instruction (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        instruction_order_number INTEGER, actions TEXT, name TEXT, xpath TEXT,
                        coordinates TEXT, force_coordinates TEXT, iframe_xpath TEXT, tag_name TEXT,
                        shadow_host TEXT, shadow_root TEXT, css_selector TEXT, description TEXT,
                        operation TEXT, optional INTEGER, block_marked INTEGER, default_value TEXT,
                        action_custom_max_wait_sec INTEGER, on_hold_seconds INTEGER, codified INTEGER,
                        export_to_abr INTEGER, active INTEGER, block_id INTEGER, variable_id INTEGER,
                        parent_block_id INTEGER, parent_id INTEGER, bot_job_id INTEGER, client_named TEXT)
                    """);

            PerformBackup backup = PerformBackup.getInstance();
            assertNull(backup.restoreBotJob(connection, botJobSnapshot.toString(), 5, 8, 42));
            assertNull(backup.restoreBlock(connection, blockSnapshot.toString(), 42));
            assertNull(backup.restoreInstruction(connection, instructionSnapshot.toString(), 42));
            assertNull(backup.restoreUpdateInstruction(connection, 42));

            int parentInstructionId;
            try (ResultSet parent = statement.executeQuery("SELECT id FROM instruction WHERE name = 'Parent'")) {
                assertTrue(parent.next());
                parentInstructionId = parent.getInt("id");
            }
            int parentBlockId;
            try (ResultSet parentBlock = statement.executeQuery("SELECT id FROM block WHERE name = 'Parent Block'")) {
                assertTrue(parentBlock.next());
                parentBlockId = parentBlock.getInt("id");
            }
            try (ResultSet child = statement.executeQuery(
                    "SELECT parent_block_id, parent_id, client_named FROM instruction WHERE name = 'Child'")) {
                assertTrue(child.next());
                assertEquals(parentBlockId, child.getInt("parent_block_id"));
                assertEquals(parentInstructionId, child.getInt("parent_id"));
                assertEquals("Child Alias", child.getString("client_named"));
            }
        }
    }

    private static int countRows(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return result.next() ? result.getInt(1) : -1;
        }
    }
}

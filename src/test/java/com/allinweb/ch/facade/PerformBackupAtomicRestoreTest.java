package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    private static int countRows(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return result.next() ? result.getInt(1) : -1;
        }
    }
}

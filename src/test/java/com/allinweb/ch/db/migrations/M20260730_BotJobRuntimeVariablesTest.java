package com.allinweb.ch.db.migrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

class M20260730_BotJobRuntimeVariablesTest {

    @Test
    void migrationKeepsLowestProducerDefinitionRemapsReferencesAndStartsAllValuesVoid()
            throws Exception {
        try (Connection connection = database()) {
            seedLegacyRows(connection);

            M20260730_BotJobRuntimeVariables migration =
                    new M20260730_BotJobRuntimeVariables();
            migration.apply(connection, "TEXT");
            migration.apply(connection, "TEXT");

            assertEquals(3, count(connection, "bot_job_variable_definition"));
            assertEquals(3, count(connection, "bot_job_runtime_variable_value"));
            assertEquals(4, count(connection, "bot_job_variable_migration_note"));
            assertEquals(10L, longValue(
                    connection,
                    "SELECT variable_id FROM instruction WHERE id = 101"));

            try (Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery(
                            "SELECT configured_value FROM bot_job_variable_definition"
                                    + " WHERE bot_job_id = 5 AND id = 10")) {
                rows.next();
                assertEquals(" 1.234,56 CHF ", rows.getString(1));
            }

            try (Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery(
                            "SELECT value_state, raw_value, void_reason"
                                    + " FROM bot_job_runtime_variable_value"
                                    + " WHERE bot_job_id = 5 ORDER BY variable_id")) {
                int found = 0;
                while (rows.next()) {
                    assertEquals("VOID", rows.getString("value_state"));
                    assertNull(rows.getString("raw_value"));
                    assertEquals("NO_PRODUCER_YET", rows.getString("void_reason"));
                    found++;
                }
                assertEquals(3, found);
            }

            try (Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery(
                            "SELECT retained_legacy_variable_id, definition_id, note_type"
                                    + " FROM bot_job_variable_migration_note"
                                    + " WHERE legacy_variable_id = 11")) {
                rows.next();
                assertEquals(10L, rows.getLong("retained_legacy_variable_id"));
                assertEquals(10L, rows.getLong("definition_id"));
                assertEquals("DUPLICATE_MERGED", rows.getString("note_type"));
            }
        }
    }

    private static Connection database() throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute(
                    "CREATE TABLE home_banking (id INTEGER PRIMARY KEY)");
            statement.execute(
                    "CREATE TABLE bot_job (id INTEGER PRIMARY KEY, home_banking_id INTEGER)");
            statement.execute(
                    "CREATE TABLE instruction (id INTEGER PRIMARY KEY,"
                            + " bot_job_id INTEGER, variable_id INTEGER)");
            statement.execute(
                    "CREATE TABLE variable (id INTEGER PRIMARY KEY, type TEXT, name TEXT,"
                            + " value TEXT, local_format TEXT, delimiter TEXT,"
                            + " instruction_id INTEGER, bot_job_id INTEGER)");
        }
        return connection;
    }

    private static void seedLegacyRows(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO home_banking(id) VALUES (2)");
            statement.executeUpdate(
                    "INSERT INTO bot_job(id,home_banking_id) VALUES (5,2)");
            statement.executeUpdate(
                    "INSERT INTO instruction(id,bot_job_id,variable_id) VALUES (100,5,10)");
            statement.executeUpdate(
                    "INSERT INTO instruction(id,bot_job_id,variable_id) VALUES (101,5,11)");
            statement.executeUpdate(
                    "INSERT INTO variable(id,type,name,value,instruction_id,bot_job_id)"
                            + " VALUES (10,'$String','amount',' 1.234,56 CHF ',100,5)");
            statement.executeUpdate(
                    "INSERT INTO variable(id,type,name,value,instruction_id,bot_job_id)"
                            + " VALUES (11,'$String','duplicate','must not win',100,5)");
            statement.executeUpdate(
                    "INSERT INTO variable(id,type,name,value,instruction_id,bot_job_id)"
                            + " VALUES (12,'$String','manual one',NULL,NULL,5)");
            statement.executeUpdate(
                    "INSERT INTO variable(id,type,name,value,instruction_id,bot_job_id)"
                            + " VALUES (13,'$String','manual two','',NULL,5)");
        }
    }

    private static int count(Connection connection, String table) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private static long longValue(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }
}

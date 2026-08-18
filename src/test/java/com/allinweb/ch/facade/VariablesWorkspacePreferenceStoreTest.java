package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class VariablesWorkspacePreferenceStoreTest {

    @Test
    void upgradesExistingPreferenceRowsWithoutLosingTheirValue() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=ON");
            statement.executeUpdate("CREATE TABLE home_banking (id INTEGER PRIMARY KEY)");
            statement.executeUpdate("CREATE TABLE bot_job (id INTEGER PRIMARY KEY)");
            statement.executeUpdate("INSERT INTO home_banking(id) VALUES (2)");
            statement.executeUpdate("INSERT INTO bot_job(id) VALUES (32)");
            statement.executeUpdate(
                    "CREATE TABLE bot_job_workspace_preference ("
                            + "home_banking_id INTEGER NOT NULL,bot_job_id INTEGER NOT NULL,"
                            + "preference_key TEXT NOT NULL,preference_value TEXT NOT NULL,"
                            + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                            + "PRIMARY KEY(home_banking_id,bot_job_id,preference_key))");
            statement.executeUpdate(
                    "INSERT INTO bot_job_workspace_preference"
                            + " (home_banking_id,bot_job_id,preference_key,preference_value)"
                            + " VALUES (2,32,'variables.resolve.variableMode','DISTINCT')");

            VariablesWorkspacePreferenceStore.ensureTable(connection);

            Set<String> columns = new HashSet<>();
            try (ResultSet rows = statement.executeQuery(
                    "PRAGMA table_info(bot_job_workspace_preference)")) {
                while (rows.next()) columns.add(rows.getString("name"));
            }
            assertTrue(columns.containsAll(Set.of(
                    "id", "organization_id", "home_banking_id", "bot_job_id",
                    "preference_key", "preference_value", "metadata_json",
                    "created_at", "updated_at")));

            try (ResultSet rows = statement.executeQuery(
                    "SELECT organization_id,home_banking_id,bot_job_id,preference_value,"
                            + "metadata_json,created_at,updated_at"
                            + " FROM bot_job_workspace_preference")) {
                assertTrue(rows.next());
                assertEquals(2, rows.getInt("organization_id"));
                assertEquals(2, rows.getInt("home_banking_id"));
                assertEquals(32, rows.getInt("bot_job_id"));
                assertEquals("DISTINCT", rows.getString("preference_value"));
                assertEquals("{}", rows.getString("metadata_json"));
                assertTrue(!rows.getString("created_at").isBlank());
                assertTrue(!rows.getString("updated_at").isBlank());
            }
        }
    }
}

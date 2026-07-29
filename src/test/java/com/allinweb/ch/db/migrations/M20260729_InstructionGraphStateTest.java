package com.allinweb.ch.db.migrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;

class M20260729_InstructionGraphStateTest {

    @Test
    void createsTheFreshSchemaIdempotentlyWithACompoundOwnerKey() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            M20260729_InstructionGraphState migration =
                    new M20260729_InstructionGraphState();

            migration.apply(connection, "TEXT");
            migration.apply(connection, "TEXT");

            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO instruction_graph_state "
                        + "(workspace_kind, home_banking_id, owner_id, graph_version,"
                        + " created_at, updated_at) "
                        + "VALUES ('BOT_JOB', 2, 5, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            }

            assertThrows(
                    SQLException.class,
                    () -> {
                        try (Statement duplicate = connection.createStatement()) {
                            duplicate.executeUpdate("INSERT INTO instruction_graph_state "
                                    + "(workspace_kind, home_banking_id, owner_id, graph_version,"
                                    + " created_at, updated_at) "
                                    + "VALUES ('BOT_JOB', 2, 5, 0,"
                                    + " CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
                        }
                    });

            try (Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery(
                            "SELECT COUNT(*) FROM instruction_graph_state")) {
                assertTrue(rows.next());
                assertEquals(1, rows.getInt(1));
            }
        }
    }

    @Test
    void definesTheCompoundOwnerKeyForEverySupportedProductionDialect() {
        for (String dialect : List.of("Postgres", "SQLServer", "TEXT", "Access")) {
            String ddl = M20260729_InstructionGraphState.createTableSql(dialect);

            assertTrue(ddl.contains("workspace_kind"));
            assertTrue(ddl.contains("home_banking_id"));
            assertTrue(ddl.contains("owner_id"));
            assertTrue(ddl.contains("graph_version"));
            assertTrue(ddl.contains(
                    "PRIMARY KEY (workspace_kind, home_banking_id, owner_id)"));
        }
    }
}

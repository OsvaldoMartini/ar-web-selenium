package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.allinweb.ch.model.VariableUserDTO;
import com.allinweb.ch.util.ErrorMessage;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

class ComponentVariableCreationTransactionTest {

    @Test
    void createsTheVariableOnlyInsideTheOwningComponentOrganization() throws Exception {
        try (Connection connection = database()) {
            VariableUserDTO variable =
                    new VariableUserDTO(
                            -1,
                            "$String",
                            "account",
                            "$EMPTY",
                            99,
                            10,
                            "Input",
                            "",
                            "",
                            "");

            ErrorMessage error = PerformDataBase.getInstance().createVariableTransaction(
                    connection,
                    "component_variable",
                    "component_instruction",
                    "home_banking_id",
                    7,
                    variable);

            assertNull(error);
            assertEquals(1, count(connection, "component_variable"));
            assertEquals(7, scalar(connection, "SELECT home_banking_id FROM component_variable"));
            assertEquals(0, count(connection, "variable"));
        }
    }

    @Test
    void rejectsAParentInstructionOwnedByAnotherOrganization() throws Exception {
        try (Connection connection = database()) {
            VariableUserDTO variable =
                    new VariableUserDTO(
                            -1,
                            "$String",
                            "account",
                            "$EMPTY",
                            99,
                            11,
                            "Foreign",
                            "",
                            "",
                            "");

            Exception failure = null;
            try {
                PerformDataBase.getInstance().createVariableTransaction(
                        connection,
                        "component_variable",
                        "component_instruction",
                        "home_banking_id",
                        7,
                        variable);
            } catch (Exception error) {
                failure = error;
            }

            assertNotNull(failure);
            assertEquals(0, count(connection, "component_variable"));
            assertEquals(0, count(connection, "variable"));
        }
    }

    @Test
    void rejectsASecondVariableForTheSameComponentInstruction() throws Exception {
        try (Connection connection = database()) {
            VariableUserDTO first = variable("first", 10);
            VariableUserDTO second = variable("second", 10);

            assertNull(PerformDataBase.getInstance().createVariableTransaction(
                    connection,
                    "component_variable",
                    "component_instruction",
                    "home_banking_id",
                    7,
                    first));
            SQLException failure = assertThrows(
                    SQLException.class,
                    () -> PerformDataBase.getInstance().createVariableTransaction(
                            connection,
                            "component_variable",
                            "component_instruction",
                            "home_banking_id",
                            7,
                            second));

            assertEquals(
                    "The instruction already owns a variable. Edit the existing variable instead.",
                    failure.getMessage());
            assertEquals(1, count(connection, "component_variable"));
        }
    }

    @Test
    void rejectsASecondVariableForTheSameBotJobInstruction() throws Exception {
        try (Connection connection = database()) {
            VariableUserDTO first = variable("first", 20);
            VariableUserDTO second = variable("second", 20);

            assertNull(PerformDataBase.getInstance().createVariableTransaction(
                    connection,
                    "variable",
                    "instruction",
                    "bot_job_id",
                    99,
                    first));
            assertThrows(
                    SQLException.class,
                    () -> PerformDataBase.getInstance().createVariableTransaction(
                            connection,
                            "variable",
                            "instruction",
                            "bot_job_id",
                            99,
                            second));

            assertEquals(1, count(connection, "variable"));
        }
    }

    private static VariableUserDTO variable(String name, int instructionId) {
        return new VariableUserDTO(
                -1,
                "$String",
                name,
                "$EMPTY",
                99,
                instructionId,
                "Input",
                "",
                "",
                "");
    }

    private static Connection database() throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE component_instruction ("
                    + "id INTEGER PRIMARY KEY, home_banking_id INTEGER NOT NULL)");
            statement.execute("CREATE TABLE instruction ("
                    + "id INTEGER PRIMARY KEY, bot_job_id INTEGER NOT NULL)");
            statement.execute("CREATE TABLE component_variable ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT, Name TEXT, Value TEXT, "
                    + "home_banking_id INTEGER, instruction_id INTEGER, local_format TEXT, "
                    + "delimiter TEXT)");
            statement.execute("CREATE TABLE variable ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT, Name TEXT, Value TEXT, "
                    + "bot_job_id INTEGER, instruction_id INTEGER, local_format TEXT, delimiter TEXT)");
            statement.executeUpdate(
                    "INSERT INTO component_instruction(id, home_banking_id) VALUES (10, 7), (11, 8)");
            statement.executeUpdate(
                    "INSERT INTO instruction(id, bot_job_id) VALUES (20, 99)");
        }
        return connection;
    }

    private static int count(Connection connection, String table) throws Exception {
        return scalar(connection, "SELECT COUNT(*) FROM " + table);
    }

    private static int scalar(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            return result.next() ? result.getInt(1) : -1;
        }
    }
}

package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

class BlockCreationServiceTransactionTest {

    @Test
    void shiftsAndInsertsBeforeAReferenceInOneCommit() throws Exception {
        try (Connection connection = database(false)) {
            BlockCreationService.InsertedBlock inserted = BlockCreationService.insertBlockTransaction(
                    connection, 42, "Inserted", BlockCreationService.Position.BEFORE, 2, null);

            assertEquals(2, inserted.orderNumber());
            assertEquals(3, orderOf(connection, 2));
            assertEquals(2, orderOf(connection, inserted.blockId()));
        }
    }

    @Test
    void insertFailureRollsBackTheOrderShift() throws Exception {
        try (Connection connection = database(true)) {
            assertThrows(
                    SQLException.class,
                    () -> BlockCreationService.insertBlockTransaction(
                            connection, 42, "Inserted", BlockCreationService.Position.BEFORE, 2, null));

            assertEquals(2, orderOf(connection, 2));
            assertEquals(2, count(connection));
        }
    }

    private static Connection database(boolean rejectNewBlock) throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE block ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, block_order_number INTEGER, "
                    + "description TEXT, name TEXT"
                    + (rejectNewBlock ? " CHECK(name <> 'Inserted')" : "")
                    + ", type_id INTEGER, active INTEGER, wait INTEGER, bot_job_id INTEGER)");
            statement.executeUpdate("INSERT INTO block "
                    + "(block_order_number, description, name, type_id, active, wait, bot_job_id) "
                    + "VALUES (1, 'One', 'One', 1, 1, 3, 42), (2, 'Two', 'Two', 1, 1, 3, 42)");
        }
        return connection;
    }

    private static int orderOf(Connection connection, int blockId) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet row = statement.executeQuery(
                        "SELECT block_order_number FROM block WHERE id = " + blockId)) {
            return row.next() ? row.getInt(1) : -1;
        }
    }

    private static int count(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet row = statement.executeQuery("SELECT COUNT(*) FROM block")) {
            return row.next() ? row.getInt(1) : -1;
        }
    }
}

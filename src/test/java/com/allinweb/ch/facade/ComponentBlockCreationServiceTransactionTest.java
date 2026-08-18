package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

class ComponentBlockCreationServiceTransactionTest {

    @Test
    void insertsInsideTheRequestedOrganizationWithoutMovingAnotherOrganizationsBlocks()
            throws Exception {
        try (Connection connection = database(false)) {
            ComponentBlockCreationService.InsertedBlock inserted =
                    ComponentBlockCreationService.insertBlockTransaction(
                            connection,
                            7,
                            "Inserted",
                            ComponentBlockCreationService.Position.BEFORE,
                            2,
                            null);

            assertEquals(2, inserted.orderNumber());
            assertEquals(3, orderOf(connection, 2));
            assertEquals(2, orderOf(connection, inserted.blockId()));
            assertEquals(1, orderOf(connection, 3));
        }
    }

    @Test
    void insertFailureRollsBackTheOrganizationScopedOrderShift() throws Exception {
        try (Connection connection = database(true)) {
            assertThrows(
                    SQLException.class,
                    () -> ComponentBlockCreationService.insertBlockTransaction(
                            connection,
                            7,
                            "Inserted",
                            ComponentBlockCreationService.Position.BEFORE,
                            2,
                            null));

            assertEquals(2, orderOf(connection, 2));
            assertEquals(3, count(connection));
        }
    }

    @Test
    void rejectsAStaleOrForeignBeforeBlockInsteadOfTrustingItsSubmittedOrder() throws Exception {
        try (Connection connection = database(false)) {
            assertThrows(
                    SQLException.class,
                    () -> ComponentBlockCreationService.insertBlockTransaction(
                            connection,
                            7,
                            "Inserted",
                            ComponentBlockCreationService.Position.BEFORE,
                            3,
                            1));

            assertEquals(1, orderOf(connection, 1));
            assertEquals(2, orderOf(connection, 2));
            assertEquals(1, orderOf(connection, 3));
            assertEquals(3, count(connection));
        }
    }

    private static Connection database(boolean rejectNewBlock) throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE component_block ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, home_banking_id INTEGER, "
                    + "block_order_number INTEGER, name TEXT"
                    + (rejectNewBlock ? " CHECK(name <> 'Inserted')" : "")
                    + ", description TEXT, type_id INTEGER, export_file TEXT, active INTEGER, "
                    + "wait INTEGER)");
            statement.executeUpdate("INSERT INTO component_block "
                    + "(home_banking_id, block_order_number, name, description, type_id, "
                    + "export_file, active, wait) VALUES "
                    + "(7, 1, 'One', 'One', 1, NULL, 1, 3), "
                    + "(7, 2, 'Two', 'Two', 1, NULL, 1, 3), "
                    + "(8, 1, 'Other', 'Other', 1, NULL, 1, 3)");
        }
        return connection;
    }

    private static int orderOf(Connection connection, int blockId) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet row = statement.executeQuery(
                        "SELECT block_order_number FROM component_block WHERE id = " + blockId)) {
            return row.next() ? row.getInt(1) : -1;
        }
    }

    private static int count(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet row =
                        statement.executeQuery("SELECT COUNT(*) FROM component_block")) {
            return row.next() ? row.getInt(1) : -1;
        }
    }
}

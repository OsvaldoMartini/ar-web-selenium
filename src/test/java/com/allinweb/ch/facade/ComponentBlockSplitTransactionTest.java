package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.BlockDetailsDTO;
import com.allinweb.ch.model.BlockOrderDetailDTO;
import com.allinweb.ch.model.UpdatedRow;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;

class ComponentBlockSplitTransactionTest {

    @Test
    void splitsOnlyTheActiveOrganizationsComponentGraph() throws Exception {
        try (Connection connection = database()) {
            int newBlockId = PerformDataBase.splitBlockTransaction(
                    connection,
                    "component_block",
                    "component_instruction",
                    7,
                    newBlock(),
                    1,
                    List.of(movedRow(101, 1), movedRow(102, 2)),
                    List.of(laterBlock(2, 3)));

            assertTrue(newBlockId > 3);
            assertEquals(3, count(connection, "component_block", "home_banking_id", 7));
            assertEquals(1, count(connection, "component_block", "home_banking_id", 8));
            assertEquals(2, blockOrder(connection, "component_block", newBlockId));
            assertEquals(3, blockOrder(connection, "component_block", 2));
            assertEquals(1, blockOrder(connection, "component_block", 3));
            assertEquals(newBlockId, instructionBlock(connection, "component_instruction", 101));
            assertEquals(newBlockId, instructionBlock(connection, "component_instruction", 102));
            assertEquals(1, instructionBlock(connection, "component_instruction", 100));
            assertEquals(3, instructionBlock(connection, "component_instruction", 200));

            assertEquals(1, count(connection, "block", "bot_job_id", 42));
            assertEquals(1, instructionBlock(connection, "instruction", 500));
        }
    }

    @Test
    void foreignComponentReorderRollsBackTheInsertAndInstructionMoves() throws Exception {
        try (Connection connection = database()) {
            assertThrows(
                    SQLException.class,
                    () -> PerformDataBase.splitBlockTransaction(
                            connection,
                            "component_block",
                            "component_instruction",
                            7,
                            newBlock(),
                            1,
                            List.of(movedRow(101, 1), movedRow(102, 2)),
                            List.of(laterBlock(3, 2))));

            assertEquals(2, count(connection, "component_block", "home_banking_id", 7));
            assertEquals(1, count(connection, "component_block", "home_banking_id", 8));
            assertEquals(1, instructionBlock(connection, "component_instruction", 101));
            assertEquals(1, instructionBlock(connection, "component_instruction", 102));
            assertEquals(2, blockOrder(connection, "component_block", 2));
            assertEquals(1, blockOrder(connection, "component_block", 3));
        }
    }

    @Test
    void refusesMixedBotJobAndComponentTablePairs() throws Exception {
        try (Connection connection = database()) {
            assertThrows(
                    SQLException.class,
                    () -> PerformDataBase.splitBlockTransaction(
                            connection,
                            "component_block",
                            "instruction",
                            7,
                            newBlock(),
                            1,
                            List.of(movedRow(101, 1)),
                            List.of()));
        }
    }

    @Test
    void retainsTheBotJobTableAndOwnerPath() throws Exception {
        try (Connection connection = database()) {
            int newBlockId = PerformDataBase.splitBlockTransaction(
                    connection,
                    "block",
                    "instruction",
                    42,
                    newBlock(),
                    1,
                    List.of(movedRow(500, 1)),
                    List.of());

            assertEquals(2, count(connection, "block", "bot_job_id", 42));
            assertEquals(newBlockId, instructionBlock(connection, "instruction", 500));
            assertEquals(2, count(connection, "component_block", "home_banking_id", 7));
            assertEquals(1, instructionBlock(connection, "component_instruction", 100));
        }
    }

    private static Connection database() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE component_block ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, home_banking_id INTEGER NOT NULL, "
                    + "block_order_number INTEGER NOT NULL, description TEXT, name TEXT, "
                    + "type_id INTEGER, active INTEGER, wait INTEGER)");
            statement.execute("CREATE TABLE component_instruction ("
                    + "id INTEGER PRIMARY KEY, home_banking_id INTEGER NOT NULL, "
                    + "block_id INTEGER NOT NULL, instruction_order_number INTEGER NOT NULL)");
            statement.execute("CREATE TABLE block ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, bot_job_id INTEGER NOT NULL, "
                    + "block_order_number INTEGER NOT NULL, description TEXT, name TEXT, "
                    + "type_id INTEGER, active INTEGER, wait INTEGER)");
            statement.execute("CREATE TABLE instruction ("
                    + "id INTEGER PRIMARY KEY, bot_job_id INTEGER NOT NULL, "
                    + "block_id INTEGER NOT NULL, instruction_order_number INTEGER NOT NULL)");

            statement.executeUpdate("INSERT INTO component_block "
                    + "(id, home_banking_id, block_order_number, description, name, type_id, active, wait) VALUES "
                    + "(1, 7, 1, 'Source', 'Source', 1, 1, 3), "
                    + "(2, 7, 2, 'Later', 'Later', 1, 1, 3), "
                    + "(3, 8, 1, 'Foreign', 'Foreign', 1, 1, 3)");
            statement.executeUpdate("INSERT INTO component_instruction "
                    + "(id, home_banking_id, block_id, instruction_order_number) VALUES "
                    + "(100, 7, 1, 1), (101, 7, 1, 2), (102, 7, 1, 3), "
                    + "(200, 8, 3, 1)");
            statement.executeUpdate("INSERT INTO block "
                    + "(id, bot_job_id, block_order_number, description, name, type_id, active, wait) "
                    + "VALUES (1, 42, 1, 'Bot', 'Bot', 1, 1, 3)");
            statement.executeUpdate("INSERT INTO instruction "
                    + "(id, bot_job_id, block_id, instruction_order_number) "
                    + "VALUES (500, 42, 1, 1)");
        }
        return connection;
    }

    private static BlockDetailsDTO newBlock() {
        BlockDetailsDTO block = new BlockDetailsDTO();
        block.setBlockName("Split");
        block.setBlockDescription("Split description");
        block.setBlockOrderNumber(2);
        block.setTypeId(1);
        block.setActive(true);
        block.setWait(3);
        return block;
    }

    private static UpdatedRow movedRow(int instructionId, int order) {
        UpdatedRow row = new UpdatedRow();
        row.setInstructionId(instructionId);
        row.setInstructionOrderNumber(order);
        return row;
    }

    private static BlockOrderDetailDTO laterBlock(int blockId, int order) {
        BlockOrderDetailDTO block = new BlockOrderDetailDTO();
        block.setBlockId(blockId);
        block.setBlockOrderNumber(order);
        return block;
    }

    private static int count(
            Connection connection, String table, String ownerColumn, int ownerId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM " + table + " WHERE " + ownerColumn + " = ?")) {
            statement.setInt(1, ownerId);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? row.getInt(1) : -1;
            }
        }
    }

    private static int blockOrder(Connection connection, String table, int blockId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT block_order_number FROM " + table + " WHERE id = ?")) {
            statement.setInt(1, blockId);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? row.getInt(1) : -1;
            }
        }
    }

    private static int instructionBlock(
            Connection connection, String table, int instructionId)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement("SELECT block_id FROM " + table + " WHERE id = ?")) {
            statement.setInt(1, instructionId);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? row.getInt(1) : -1;
            }
        }
    }
}

package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.allinweb.ch.model.UpdatedRow;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;

class InstructionMoveTransactionTest {
    @Test
    void rollsBackEarlierRowsWhenALaterUpdateFails() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE instruction(id INTEGER PRIMARY KEY,bot_job_id INTEGER,block_id INTEGER,"
                    + "instruction_order_number INTEGER,parent_id INTEGER,parent_block_id INTEGER)");
            sql.execute("CREATE TABLE block(id INTEGER PRIMARY KEY,bot_job_id INTEGER,block_order_number INTEGER)");
            sql.execute("INSERT INTO block VALUES(10,19,1),(20,19,2)");
            sql.execute("INSERT INTO instruction VALUES(1,19,10,1,NULL,NULL)");

            assertThrows(Exception.class, () -> new InstructionMoveTransaction().execute(
                    connection, "block", 19, List.of(update(1, 20, 2), update(999, 20, 1))));

            try (ResultSet row = sql.executeQuery(
                    "SELECT block_id,instruction_order_number FROM instruction WHERE id=1")) {
                row.next();
                assertEquals(10, row.getInt("block_id"));
                assertEquals(1, row.getInt("instruction_order_number"));
            }
        }
    }

    @Test
    void rejectsDestinationBlockOwnedByAnotherOrganizationBeforeUpdatingRows() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE component_block("
                    + "id INTEGER PRIMARY KEY,home_banking_id INTEGER,block_order_number INTEGER)");
            sql.execute("CREATE TABLE component_instruction("
                    + "id INTEGER PRIMARY KEY,home_banking_id INTEGER,block_id INTEGER,"
                    + "instruction_order_number INTEGER,parent_id INTEGER,parent_block_id INTEGER)");
            sql.execute("INSERT INTO component_block VALUES(10,7,1),(99,8,1)");
            sql.execute("INSERT INTO component_instruction VALUES(1,7,10,1,NULL,NULL)");

            SQLException error = assertThrows(
                    SQLException.class,
                    () -> new InstructionMoveTransaction().execute(
                            connection,
                            "component_block",
                            7,
                            List.of(update(1, 99, 1))));
            assertEquals(
                    "Destination block does not belong to the active owner.",
                    error.getMessage());

            try (ResultSet row = sql.executeQuery(
                    "SELECT block_id,instruction_order_number FROM component_instruction WHERE id=1")) {
                row.next();
                assertEquals(10, row.getInt("block_id"));
                assertEquals(1, row.getInt("instruction_order_number"));
            }
        }
    }

    @Test
    void deletesOnlyBlocksEmptiedByTheMoveAndPreservesExistingEmptyBlocks() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE block(id INTEGER PRIMARY KEY,bot_job_id INTEGER,block_order_number INTEGER)");
            sql.execute("CREATE TABLE instruction(id INTEGER PRIMARY KEY,bot_job_id INTEGER,block_id INTEGER,"
                    + "instruction_order_number INTEGER,parent_id INTEGER,parent_block_id INTEGER)");
            sql.execute("INSERT INTO block VALUES(10,19,1),(20,19,2),(30,19,3)");
            sql.execute("INSERT INTO instruction VALUES(1,19,10,1,NULL,NULL),(2,19,30,1,NULL,NULL)");

            new InstructionMoveTransaction().execute(
                    connection, "block", 19, List.of(update(1, 30, 2), update(2, 30, 1)));

            try (ResultSet rows = sql.executeQuery("SELECT id,block_order_number FROM block ORDER BY block_order_number")) {
                rows.next();
                assertEquals(20, rows.getInt("id"));
                assertEquals(1, rows.getInt("block_order_number"));
                rows.next();
                assertEquals(30, rows.getInt("id"));
                assertEquals(2, rows.getInt("block_order_number"));
                assertFalse(rows.next());
            }
        }
    }

    @Test
    void partialMoveUsesTheStoredBlockOfAnUnsubmittedParent() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE block(id INTEGER PRIMARY KEY,bot_job_id INTEGER,block_order_number INTEGER)");
            sql.execute("CREATE TABLE instruction(id INTEGER PRIMARY KEY,bot_job_id INTEGER,block_id INTEGER,"
                    + "instruction_order_number INTEGER,parent_id INTEGER,parent_block_id INTEGER)");
            sql.execute("INSERT INTO block VALUES(10,19,1),(20,19,2)");
            sql.execute("INSERT INTO instruction VALUES"
                    + "(1,19,10,1,NULL,NULL),"
                    + "(2,19,10,2,1,10)");

            new InstructionMoveTransaction()
                    .execute(connection, "block", 19, List.of(update(2, 20, 1)));

            try (ResultSet child = sql.executeQuery(
                    "SELECT block_id,instruction_order_number,parent_id,parent_block_id "
                            + "FROM instruction WHERE id=2")) {
                child.next();
                assertEquals(20, child.getInt("block_id"));
                assertEquals(1, child.getInt("instruction_order_number"));
                assertEquals(1, child.getInt("parent_id"));
                assertEquals(10, child.getInt("parent_block_id"));
            }
        }
    }

    private UpdatedRow update(int id, int blockId, int order) {
        UpdatedRow row = new UpdatedRow();
        row.setInstructionId(id);
        row.setBlockId(blockId);
        row.setInstructionOrderNumber(order);
        return row;
    }
}

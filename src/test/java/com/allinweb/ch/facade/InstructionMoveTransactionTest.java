package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.allinweb.ch.model.UpdatedRow;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
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
    void deletesEmptyBlocksAndNormalizesRemainingOrder() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE block(id INTEGER PRIMARY KEY,bot_job_id INTEGER,block_order_number INTEGER)");
            sql.execute("CREATE TABLE instruction(id INTEGER PRIMARY KEY,bot_job_id INTEGER,block_id INTEGER,"
                    + "instruction_order_number INTEGER,parent_id INTEGER,parent_block_id INTEGER)");
            sql.execute("INSERT INTO block VALUES(10,19,1),(20,19,2),(30,19,3)");
            sql.execute("INSERT INTO instruction VALUES(1,19,10,1,NULL,NULL),(2,19,30,1,NULL,NULL)");

            new InstructionMoveTransaction().execute(
                    connection, "block", 19, List.of(update(1, 10, 1), update(2, 30, 1)));

            try (ResultSet rows = sql.executeQuery("SELECT id,block_order_number FROM block ORDER BY block_order_number")) {
                rows.next();
                assertEquals(10, rows.getInt("id"));
                assertEquals(1, rows.getInt("block_order_number"));
                rows.next();
                assertEquals(30, rows.getInt("id"));
                assertEquals(2, rows.getInt("block_order_number"));
                assertFalse(rows.next());
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

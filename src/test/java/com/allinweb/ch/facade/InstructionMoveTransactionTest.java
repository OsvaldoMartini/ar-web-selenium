package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void rollsBackEarlierRowsWhenA laterUpdateFails() throws Exception {
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

    private UpdatedRow update(int id, int blockId, int order) {
        UpdatedRow row = new UpdatedRow();
        row.setInstructionId(id);
        row.setBlockId(blockId);
        row.setInstructionOrderNumber(order);
        return row;
    }
}

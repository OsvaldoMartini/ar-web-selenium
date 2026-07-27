package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.allinweb.ch.model.BlockLoadDTO;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;

class BlockOrderTransactionTest {

    @Test
    void persistsTheSubmittedCompleteComponentPermutation() throws Exception {
        try (Connection connection = database();
                Statement sql = connection.createStatement()) {
            new BlockOrderTransaction().execute(
                    connection,
                    "component_block",
                    7,
                    List.of(block(10, 2), block(20, 1)));

            try (ResultSet rows = sql.executeQuery(
                    "SELECT id,block_order_number FROM component_block "
                            + "WHERE home_banking_id=7 ORDER BY block_order_number")) {
                rows.next();
                assertEquals(20, rows.getInt("id"));
                assertEquals(1, rows.getInt("block_order_number"));
                rows.next();
                assertEquals(10, rows.getInt("id"));
                assertEquals(2, rows.getInt("block_order_number"));
            }
        }
    }

    @Test
    void rejectsForeignOrIncompleteLayoutsWithoutChangingAnyOrder() throws Exception {
        try (Connection connection = database();
                Statement sql = connection.createStatement()) {
            assertThrows(
                    SQLException.class,
                    () -> new BlockOrderTransaction().execute(
                            connection,
                            "component_block",
                            7,
                            List.of(block(99, 1))));

            try (ResultSet rows = sql.executeQuery(
                    "SELECT id,block_order_number FROM component_block "
                            + "WHERE home_banking_id=7 ORDER BY id")) {
                rows.next();
                assertEquals(10, rows.getInt("id"));
                assertEquals(1, rows.getInt("block_order_number"));
                rows.next();
                assertEquals(20, rows.getInt("id"));
                assertEquals(2, rows.getInt("block_order_number"));
            }
        }
    }

    private Connection database() throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE component_block("
                    + "id INTEGER PRIMARY KEY,home_banking_id INTEGER,block_order_number INTEGER)");
            sql.execute("INSERT INTO component_block VALUES(10,7,1),(20,7,2),(99,8,1)");
        }
        return connection;
    }

    private BlockLoadDTO block(int id, int order) {
        BlockLoadDTO block = new BlockLoadDTO();
        block.setId(id);
        block.setBlockOrderNumber(order);
        return block;
    }
}

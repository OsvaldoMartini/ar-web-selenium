package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.BlockOrderDetailDTO;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.UpdatedRow;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BlockRollbackTransactionTest {

    @Test
    void remapsIntraBlockParentsAndDeletesEmptySourceBlocksAtomically() throws Exception {
        try (Connection connection = componentDatabase();
                Statement sql = connection.createStatement()) {
            sql.execute(
                    "INSERT INTO component_block"
                            + "(id,home_banking_id,block_order_number,name,active,wait,export_file) VALUES"
                            + "(10,7,1,'Block 10',1,0,NULL),"
                            + "(20,7,2,'Block 20',1,0,NULL),"
                            + "(30,7,3,'Block 30',1,0,NULL),"
                            + "(90,8,1,'Block 90',1,0,NULL)");
            sql.execute(
                    "INSERT INTO component_instruction"
                            + "(id,home_banking_id,block_id,instruction_order_number,"
                            + "parent_block_id,parent_id) VALUES"
                            + "(1,7,10,1,10,501),"
                            + "(2,7,20,1,20,502),"
                            + "(3,7,20,2,NULL,503),"
                            + "(4,7,30,1,30,504),"
                            + "(90,8,90,1,90,590)");

            UpdatedRow forgedParent = update(3, 10, 3);
            forgedParent.setParentBlockId(999); // A forged client value must never win over the DB.
            executeCurrent(
                    connection,
                    "component_instruction",
                    7,
                    10,
                    List.of(
                            update(1, 10, 1),
                            update(2, 10, 2),
                            forgedParent,
                            update(4, 10, 4)));

            assertInstruction(sql, 1, 10, 1, 10, 501);
            assertInstruction(sql, 2, 10, 2, 10, 502);
            assertInstruction(sql, 3, 10, 3, null, 503);
            assertInstruction(sql, 4, 10, 4, 10, 504);
            assertInstruction(sql, 90, 90, 1, 90, 590);
            assertEquals(1, scalar(sql, "SELECT COUNT(*) FROM component_block WHERE home_banking_id=7"));
            assertEquals(1, scalar(sql, "SELECT COUNT(*) FROM component_block WHERE home_banking_id=8"));
        }
    }

    @Test
    void refusesCrossBlockReferencesInsteadOfCreatingADanglingOrSelfReference()
            throws Exception {
        try (Connection connection = componentDatabase();
                Statement sql = connection.createStatement()) {
            sql.execute(
                    "INSERT INTO component_block"
                            + "(id,home_banking_id,block_order_number,name,active,wait,export_file) VALUES"
                            + "(10,7,1,'Block 10',1,0,NULL),(20,7,2,'Block 20',1,0,NULL)");
            sql.execute(
                    "INSERT INTO component_instruction"
                            + "(id,home_banking_id,block_id,instruction_order_number,"
                            + "parent_block_id,parent_id) VALUES"
                            + "(1,7,10,1,20,501),"
                            + "(2,7,20,1,20,502)");

            assertThrows(
                    SQLException.class,
                    () -> executeCurrent(
                            connection,
                            "component_instruction",
                            7,
                            10,
                            List.of(update(1, 10, 1), update(2, 10, 2))));

            assertInstruction(sql, 1, 10, 1, 20, 501);
            assertInstruction(sql, 2, 20, 1, 20, 502);
            assertEquals(2, scalar(sql, "SELECT COUNT(*) FROM component_block"));
        }
    }

    @Test
    void normalizesTheSurvivingDestinationBlockToOrderOne() throws Exception {
        try (Connection connection = componentDatabase();
                Statement sql = connection.createStatement()) {
            sql.execute(
                    "INSERT INTO component_block"
                            + "(id,home_banking_id,block_order_number,name,active,wait,export_file) VALUES"
                            + "(10,7,1,'Block 10',1,0,NULL),(20,7,2,'Block 20',1,0,NULL)");
            sql.execute(
                    "INSERT INTO component_instruction"
                            + "(id,home_banking_id,block_id,instruction_order_number,"
                            + "parent_block_id,parent_id) VALUES"
                            + "(1,7,10,1,10,501),"
                            + "(2,7,20,1,20,502)");

            executeCurrent(
                    connection,
                    "component_instruction",
                    7,
                    20,
                    List.of(update(1, 20, 1), update(2, 20, 2)));

            assertEquals(
                    1,
                    scalar(
                            sql,
                            "SELECT block_order_number FROM component_block "
                                    + "WHERE id=20 AND home_banking_id=7"));
            assertEquals(
                    1,
                    scalar(
                            sql,
                            "SELECT COUNT(*) FROM component_block WHERE home_banking_id=7"));
        }
    }

    @Test
    void rejectsDuplicateInstructionIdsWithoutChangingTheDatabase() throws Exception {
        try (Connection connection = botJobDatabase();
                Statement sql = connection.createStatement()) {
            seedBotJob(sql);

            assertThrows(
                    SQLException.class,
                    () -> executeCurrent(
                            connection,
                            "instruction",
                            19,
                            10,
                            List.of(update(1, 10, 1), update(1, 10, 2))));

            assertInstruction(sql, 1, 10, 1, 10, 501);
            assertInstruction(sql, 2, 20, 1, 20, 502);
        }
    }

    @Test
    void rejectsDuplicateAndNonContiguousOrdersWithoutChangingTheDatabase()
            throws Exception {
        assertInvalidBotJobLayout(
                List.of(update(1, 10, 1), update(2, 10, 1)));
        assertInvalidBotJobLayout(
                List.of(update(1, 10, 1), update(2, 10, 3)));
    }

    @Test
    void rejectsForeignOwnerRowsWithoutTouchingEitherOwner() throws Exception {
        try (Connection connection = botJobDatabase();
                Statement sql = connection.createStatement()) {
            seedBotJob(sql);
            sql.execute(
                    "INSERT INTO block"
                            + "(id,bot_job_id,block_order_number,name,active,wait,export_file) "
                            + "VALUES(90,29,1,'Block 90',1,0,NULL)");
            sql.execute(
                    "INSERT INTO instruction"
                            + "(id,bot_job_id,block_id,instruction_order_number,"
                            + "parent_block_id,parent_id) VALUES(90,29,90,1,90,590)");

            assertThrows(
                    SQLException.class,
                    () -> executeCurrent(
                            connection,
                            "instruction",
                            19,
                            10,
                            List.of(update(1, 10, 1), update(90, 10, 2))));

            assertInstruction(sql, 1, 10, 1, 10, 501);
            assertInstruction(sql, 2, 20, 1, 20, 502);
            assertInstruction(sql, 90, 90, 1, 90, 590);
        }
    }

    @Test
    void rollsBackEarlierUpdatesWhenALaterDatabaseWriteFails() throws Exception {
        try (Connection connection = botJobDatabase();
                Statement sql = connection.createStatement()) {
            seedBotJob(sql);
            sql.execute(
                    "CREATE TRIGGER reject_second_rollback "
                            + "BEFORE UPDATE ON instruction WHEN OLD.id = 2 "
                            + "BEGIN SELECT RAISE(ABORT, 'forced rollback failure'); END");

            assertThrows(
                    SQLException.class,
                    () -> executeCurrent(
                            connection,
                            "instruction",
                            19,
                            10,
                            List.of(update(1, 10, 2), update(2, 10, 1))));

            assertInstruction(sql, 1, 10, 1, 10, 501);
            assertInstruction(sql, 2, 20, 1, 20, 502);
        }
    }

    @Test
    void refusesAStaleInstructionRevisionInsideTheRollbackTransaction() throws Exception {
        try (Connection connection = botJobDatabase();
                Statement sql = connection.createStatement()) {
            seedBotJob(sql);
            String staleRevision =
                    currentGraphRevision(connection, "instruction", 19);
            List<BlockOrderDetailDTO> expectedBlocks =
                    currentBlockCatalog(connection, "instruction", 19);

            sql.execute("UPDATE instruction SET instruction_order_number=2 WHERE id=1");
            sql.execute("UPDATE instruction SET instruction_order_number=1 WHERE id=2");

            assertThrows(
                    SQLException.class,
                    () -> new BlockRollbackTransaction()
                            .execute(
                                    connection,
                                    "instruction",
                                    19,
                                    10,
                                    staleRevision,
                                    expectedBlocks,
                                    List.of(update(1, 10, 1), update(2, 10, 2))));

            assertInstruction(sql, 1, 10, 2, 10, 501);
            assertInstruction(sql, 2, 20, 1, 20, 502);
            assertEquals(2, scalar(sql, "SELECT COUNT(*) FROM block WHERE bot_job_id=19"));
        }
    }

    @Test
    void refusesAStaleCatalogAndPreservesANewEmptyBlock() throws Exception {
        try (Connection connection = botJobDatabase();
                Statement sql = connection.createStatement()) {
            seedBotJob(sql);
            String expectedRevision =
                    currentGraphRevision(connection, "instruction", 19);
            List<BlockOrderDetailDTO> staleBlocks =
                    currentBlockCatalog(connection, "instruction", 19);

            sql.execute(
                    "INSERT INTO block"
                            + "(id,bot_job_id,block_order_number,name,active,wait,export_file) "
                            + "VALUES(30,19,3,'Concurrent empty block',1,0,NULL)");

            assertThrows(
                    SQLException.class,
                    () -> new BlockRollbackTransaction()
                            .execute(
                                    connection,
                                    "instruction",
                                    19,
                                    10,
                                    expectedRevision,
                                    staleBlocks,
                                    List.of(update(1, 10, 1), update(2, 10, 2))));

            assertInstruction(sql, 1, 10, 1, 10, 501);
            assertInstruction(sql, 2, 20, 1, 20, 502);
            assertEquals(1, scalar(sql, "SELECT COUNT(*) FROM block WHERE id=30"));
            assertEquals(3, scalar(sql, "SELECT COUNT(*) FROM block WHERE bot_job_id=19"));
        }
    }

    private void assertInvalidBotJobLayout(List<UpdatedRow> rows) throws Exception {
        try (Connection connection = botJobDatabase();
                Statement sql = connection.createStatement()) {
            seedBotJob(sql);

            assertThrows(
                    SQLException.class,
                    () -> executeCurrent(connection, "instruction", 19, 10, rows));

            assertInstruction(sql, 1, 10, 1, 10, 501);
            assertInstruction(sql, 2, 20, 1, 20, 502);
        }
    }

    private Connection componentDatabase() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement sql = connection.createStatement()) {
            sql.execute(
                    "CREATE TABLE component_block("
                            + "id INTEGER PRIMARY KEY,"
                            + "home_banking_id INTEGER NOT NULL,"
                            + "block_order_number INTEGER NOT NULL,"
                            + "name TEXT NOT NULL,"
                            + "active INTEGER,"
                            + "wait INTEGER,"
                            + "export_file TEXT)");
            sql.execute(
                    "CREATE TABLE component_instruction("
                            + "id INTEGER PRIMARY KEY,"
                            + "home_banking_id INTEGER NOT NULL,"
                            + "block_id INTEGER NOT NULL,"
                            + "instruction_order_number INTEGER NOT NULL,"
                            + "parent_block_id INTEGER,"
                            + "parent_id INTEGER,"
                            + "actions TEXT,"
                            + "variable_id INTEGER,"
                            + "operation TEXT)");
        }
        return connection;
    }

    private Connection botJobDatabase() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement sql = connection.createStatement()) {
            sql.execute(
                    "CREATE TABLE block("
                            + "id INTEGER PRIMARY KEY,"
                            + "bot_job_id INTEGER NOT NULL,"
                            + "block_order_number INTEGER NOT NULL,"
                            + "name TEXT NOT NULL,"
                            + "active INTEGER,"
                            + "wait INTEGER,"
                            + "export_file TEXT)");
            sql.execute(
                    "CREATE TABLE instruction("
                            + "id INTEGER PRIMARY KEY,"
                            + "bot_job_id INTEGER NOT NULL,"
                            + "block_id INTEGER NOT NULL,"
                            + "instruction_order_number INTEGER NOT NULL,"
                            + "parent_block_id INTEGER,"
                            + "parent_id INTEGER,"
                            + "actions TEXT,"
                            + "variable_id INTEGER,"
                            + "operation TEXT)");
        }
        return connection;
    }

    private void seedBotJob(Statement sql) throws SQLException {
        sql.execute(
                "INSERT INTO block"
                        + "(id,bot_job_id,block_order_number,name,active,wait,export_file) VALUES"
                        + "(10,19,1,'Block 10',1,0,NULL),(20,19,2,'Block 20',1,0,NULL)");
        sql.execute(
                "INSERT INTO instruction"
                        + "(id,bot_job_id,block_id,instruction_order_number,"
                        + "parent_block_id,parent_id) VALUES"
                        + "(1,19,10,1,10,501),"
                        + "(2,19,20,1,20,502)");
    }

    private void executeCurrent(
            Connection connection,
            String instructionTable,
            int ownerId,
            int destinationBlockId,
            List<UpdatedRow> rows)
            throws SQLException {
        new BlockRollbackTransaction()
                .execute(
                        connection,
                        instructionTable,
                        ownerId,
                        destinationBlockId,
                        currentGraphRevision(connection, instructionTable, ownerId),
                        currentBlockCatalog(connection, instructionTable, ownerId),
                        rows);
    }

    private String currentGraphRevision(
            Connection connection, String instructionTable, int ownerId)
            throws SQLException {
        boolean component = "component_instruction".equals(instructionTable);
        String ownerColumn = component ? "home_banking_id" : "bot_job_id";
        String query = "SELECT id,block_id,instruction_order_number,actions,parent_id,"
                + "parent_block_id,variable_id,operation FROM "
                + instructionTable
                + " WHERE "
                + ownerColumn
                + "=? ORDER BY id";
        List<InstructionLoad> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, ownerId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    InstructionLoad row = new InstructionLoad();
                    row.setId(result.getInt("id"));
                    row.setBlockId(result.getInt("block_id"));
                    row.setInstructionOrderNumber(
                            result.getInt("instruction_order_number"));
                    row.setActions(result.getString("actions"));
                    row.setParentId(nullableInteger(result, "parent_id"));
                    row.setParentBlockId(nullableInteger(result, "parent_block_id"));
                    row.setVariableId(nullableInteger(result, "variable_id"));
                    row.setOperation(result.getString("operation"));
                    rows.add(row);
                }
            }
        }
        return new InstructionGraphRevisionService().revision(rows);
    }

    private List<BlockOrderDetailDTO> currentBlockCatalog(
            Connection connection, String instructionTable, int ownerId)
            throws SQLException {
        boolean component = "component_instruction".equals(instructionTable);
        String blockTable = component ? "component_block" : "block";
        String ownerColumn = component ? "home_banking_id" : "bot_job_id";
        String query = "SELECT id,block_order_number,name,active,wait,export_file FROM "
                + blockTable
                + " WHERE "
                + ownerColumn
                + "=? ORDER BY block_order_number,id";
        List<BlockOrderDetailDTO> blocks = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, ownerId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    BlockOrderDetailDTO block = new BlockOrderDetailDTO();
                    block.setBlockId(result.getInt("id"));
                    block.setBlockOrderNumber(result.getInt("block_order_number"));
                    block.setBlockName(result.getString("name"));
                    block.setBlockActive(
                            result.getObject("active") == null
                                    || result.getInt("active") != 0);
                    block.setBlockWait(
                            result.getObject("wait") == null
                                    ? 0
                                    : result.getInt("wait"));
                    block.setExportFile(result.getString("export_file"));
                    if (component) {
                        block.setHomeBankId(ownerId);
                    } else {
                        block.setBotJobId(ownerId);
                    }
                    blocks.add(block);
                }
            }
        }
        return blocks;
    }

    private Integer nullableInteger(ResultSet result, String column)
            throws SQLException {
        Object value = result.getObject(column);
        return value == null ? null : ((Number) value).intValue();
    }

    private UpdatedRow update(int instructionId, int blockId, int order) {
        UpdatedRow row = new UpdatedRow();
        row.setInstructionId(instructionId);
        row.setBlockId(blockId);
        row.setInstructionOrderNumber(order);
        return row;
    }

    private void assertInstruction(
            Statement sql,
            int id,
            int blockId,
            int order,
            Integer parentBlockId,
            int parentId)
            throws SQLException {
        try (ResultSet row = sql.executeQuery(
                "SELECT block_id,instruction_order_number,parent_block_id,parent_id "
                        + "FROM "
                        + instructionTable(sql)
                        + " WHERE id="
                        + id)) {
            assertFalse(row.isClosed());
            assertTrue(row.next());
            assertEquals(blockId, row.getInt("block_id"));
            assertEquals(order, row.getInt("instruction_order_number"));
            if (parentBlockId == null) {
                assertNull(row.getObject("parent_block_id"));
            } else {
                assertEquals(parentBlockId.intValue(), row.getInt("parent_block_id"));
            }
            assertEquals(parentId, row.getInt("parent_id"));
        }
    }

    private String instructionTable(Statement sql) throws SQLException {
        try (ResultSet table = sql.executeQuery(
                "SELECT name FROM sqlite_master "
                        + "WHERE type='table' AND name='component_instruction'")) {
            return table.next() ? "component_instruction" : "instruction";
        }
    }

    private int scalar(Statement sql, String query) throws SQLException {
        try (ResultSet result = sql.executeQuery(query)) {
            return result.next() ? result.getInt(1) : -1;
        }
    }
}

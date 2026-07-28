package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.UpdatedRow;
import com.allinweb.ch.model.VariableLoadDTO;
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
    void rejectsPartialLayouts() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE block(id INTEGER PRIMARY KEY,bot_job_id INTEGER,block_order_number INTEGER)");
            sql.execute("CREATE TABLE instruction(id INTEGER PRIMARY KEY,bot_job_id INTEGER,block_id INTEGER,"
                    + "instruction_order_number INTEGER,parent_id INTEGER,parent_block_id INTEGER)");
            sql.execute("INSERT INTO block VALUES(10,19,1),(20,19,2)");
            sql.execute("INSERT INTO instruction VALUES"
                    + "(1,19,10,1,NULL,NULL),"
                    + "(2,19,10,2,1,10)");

            assertThrows(SQLException.class, () -> new InstructionMoveTransaction()
                    .execute(connection, "block", 19, List.of(update(2, 20, 1))));

            try (ResultSet child = sql.executeQuery(
                    "SELECT block_id,instruction_order_number,parent_id,parent_block_id "
                            + "FROM instruction WHERE id=2")) {
                child.next();
                assertEquals(10, child.getInt("block_id"));
                assertEquals(2, child.getInt("instruction_order_number"));
                assertEquals(1, child.getInt("parent_id"));
                assertEquals(10, child.getInt("parent_block_id"));
            }
        }
    }

    @Test
    void preservesExplicitParentBlockForRowsWithoutAParentAndKeepsItsEmptyBlock() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE block(id INTEGER PRIMARY KEY,bot_job_id INTEGER,block_order_number INTEGER)");
            sql.execute("CREATE TABLE instruction(id INTEGER PRIMARY KEY,bot_job_id INTEGER,block_id INTEGER,"
                    + "instruction_order_number INTEGER,parent_id INTEGER,parent_block_id INTEGER)");
            sql.execute("INSERT INTO block VALUES(10,19,1),(20,19,2)");
            sql.execute("INSERT INTO instruction VALUES(1,19,10,1,NULL,10)");
            UpdatedRow row = update(1, 20, 1);
            row.setParentBlockId(10);

            new InstructionMoveTransaction().execute(connection, "block", 19, List.of(row));

            try (ResultSet result = sql.executeQuery("SELECT parent_block_id FROM instruction WHERE id=1")) {
                result.next();
                assertEquals(10, result.getInt("parent_block_id"));
            }
            try (ResultSet result = sql.executeQuery("SELECT COUNT(*) FROM block WHERE id=10")) {
                result.next();
                assertEquals(1, result.getInt(1));
            }
        }
    }

    @Test
    void acceptsSameBlockInsertionBetween917AndItsLoopBoundary() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE block(id INTEGER PRIMARY KEY,bot_job_id INTEGER,block_order_number INTEGER)");
            sql.execute("CREATE TABLE instruction(id INTEGER PRIMARY KEY,bot_job_id INTEGER,block_id INTEGER,"
                    + "instruction_order_number INTEGER,parent_id INTEGER,parent_block_id INTEGER)");
            sql.execute("INSERT INTO block VALUES(10,19,1)");
            sql.execute("INSERT INTO instruction VALUES"
                    + "(917,19,10,1,NULL,NULL),"
                    + "(919,19,10,2,917,10),"
                    + "(918,19,10,3,NULL,NULL)");

            UpdatedRow first = update(917, 10, 1);
            UpdatedRow x = update(918, 10, 2);
            UpdatedRow loop = update(919, 10, 3);
            loop.setParentId(917);
            loop.setParentBlockId(10);

            new InstructionMoveTransaction().execute(connection, "block", 19, List.of(first, x, loop));

            try (ResultSet rows = sql.executeQuery(
                    "SELECT id,block_id,instruction_order_number FROM instruction ORDER BY instruction_order_number")) {
                rows.next();
                assertEquals(917, rows.getInt("id"));
                rows.next();
                assertEquals(918, rows.getInt("id"));
                rows.next();
                assertEquals(919, rows.getInt("id"));
                assertFalse(rows.next());
            }
        }
    }

    @Test
    void acceptsSameBlockInsertionBetweenIfAndEndIfBoundary() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE block(id INTEGER PRIMARY KEY,bot_job_id INTEGER,block_order_number INTEGER)");
            sql.execute("CREATE TABLE instruction(id INTEGER PRIMARY KEY,bot_job_id INTEGER,block_id INTEGER,"
                    + "instruction_order_number INTEGER,parent_id INTEGER,parent_block_id INTEGER)");
            sql.execute("INSERT INTO block VALUES(10,19,1)");
            sql.execute("INSERT INTO instruction VALUES"
                    + "(701,19,10,1,NULL,NULL),"
                    + "(702,19,10,2,701,10),"
                    + "(703,19,10,3,NULL,NULL)");

            UpdatedRow ifRow = update(701, 10, 1);
            UpdatedRow inserted = update(703, 10, 2);
            UpdatedRow endIf = update(702, 10, 3);
            endIf.setParentId(701);
            endIf.setParentBlockId(10);

            new InstructionMoveTransaction().execute(
                    connection, "block", 19, List.of(ifRow, inserted, endIf));

            try (ResultSet rows = sql.executeQuery(
                    "SELECT id FROM instruction ORDER BY instruction_order_number")) {
                rows.next();
                assertEquals(701, rows.getInt("id"));
                rows.next();
                assertEquals(703, rows.getInt("id"));
                rows.next();
                assertEquals(702, rows.getInt("id"));
                assertFalse(rows.next());
            }
        }
    }

    @Test
    void remapsParentBlockWhenReactMovesAParentAndChildTogether() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE block(id INTEGER PRIMARY KEY,bot_job_id INTEGER,block_order_number INTEGER)");
            sql.execute("CREATE TABLE instruction(id INTEGER PRIMARY KEY,bot_job_id INTEGER,block_id INTEGER,"
                    + "instruction_order_number INTEGER,parent_id INTEGER,parent_block_id INTEGER)");
            sql.execute("INSERT INTO block VALUES(10,19,1),(20,19,2)");
            sql.execute("INSERT INTO instruction VALUES"
                    + "(1,19,10,1,NULL,NULL),"
                    + "(2,19,10,2,1,10),"
                    + "(3,19,20,1,NULL,NULL)");

            UpdatedRow existing = update(3, 20, 1);
            UpdatedRow parent = update(1, 20, 2);
            UpdatedRow child = update(2, 20, 3);
            child.setParentId(1);
            child.setParentBlockId(20);

            new InstructionMoveTransaction().execute(
                    connection, "block", 19, List.of(existing, parent, child));

            try (ResultSet result = sql.executeQuery(
                    "SELECT block_id,parent_id,parent_block_id FROM instruction WHERE id=2")) {
                result.next();
                assertEquals(20, result.getInt("block_id"));
                assertEquals(1, result.getInt("parent_id"));
                assertEquals(20, result.getInt("parent_block_id"));
            }
        }
    }

    @Test
    void preservesUnchangedLegacyOrGotoParentBlockOutsideTheOwner() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE block(id INTEGER PRIMARY KEY,bot_job_id INTEGER,block_order_number INTEGER)");
            sql.execute("CREATE TABLE instruction(id INTEGER PRIMARY KEY,bot_job_id INTEGER,block_id INTEGER,"
                    + "instruction_order_number INTEGER,parent_id INTEGER,parent_block_id INTEGER)");
            sql.execute("INSERT INTO block VALUES(10,19,1),(20,19,2)");
            sql.execute("INSERT INTO instruction VALUES"
                    + "(1,19,10,1,2,999),"
                    + "(2,19,10,2,NULL,NULL),"
                    + "(3,19,20,1,NULL,NULL)");

            UpdatedRow parent = update(2, 10, 1);
            UpdatedRow existing = update(3, 20, 1);
            UpdatedRow legacyOrGoto = update(1, 20, 2);
            legacyOrGoto.setParentId(2);
            legacyOrGoto.setParentBlockId(999);

            new InstructionMoveTransaction().execute(
                    connection, "block", 19, List.of(parent, existing, legacyOrGoto));

            try (ResultSet result = sql.executeQuery(
                    "SELECT block_id,parent_id,parent_block_id FROM instruction WHERE id=1")) {
                result.next();
                assertEquals(20, result.getInt("block_id"));
                assertEquals(2, result.getInt("parent_id"));
                assertEquals(999, result.getInt("parent_block_id"));
            }
        }
    }

    @Test
    void rejectsAChangedParentBlockOutsideTheOwner() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE block(id INTEGER PRIMARY KEY,bot_job_id INTEGER,block_order_number INTEGER)");
            sql.execute("CREATE TABLE instruction(id INTEGER PRIMARY KEY,bot_job_id INTEGER,block_id INTEGER,"
                    + "instruction_order_number INTEGER,parent_id INTEGER,parent_block_id INTEGER)");
            sql.execute("INSERT INTO block VALUES(10,19,1),(20,19,2)");
            sql.execute("INSERT INTO instruction VALUES(1,19,10,1,NULL,10)");

            UpdatedRow changed = update(1, 20, 1);
            changed.setParentBlockId(999);

            SQLException error = assertThrows(
                    SQLException.class,
                    () -> new InstructionMoveTransaction().execute(
                            connection, "block", 19, List.of(changed)));

            assertEquals("Changed parent block does not belong to the active owner.", error.getMessage());
            try (ResultSet result = sql.executeQuery(
                    "SELECT block_id,parent_block_id FROM instruction WHERE id=1")) {
                result.next();
                assertEquals(10, result.getInt("block_id"));
                assertEquals(10, result.getInt("parent_block_id"));
            }
        }
    }

    @Test
    void rejectsStaleRevisionAfterVariableOwnershipChangesAndLeavesMoveFieldsUntouched()
            throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement sql = connection.createStatement()) {
            createProductionRevisionSchema(sql, false);
            seedProductionRevisionGraph(sql, false, 19);
            String expectedRevision = productionGraphRevision(false, 19);

            // Simulate another committed editor changing dependency ownership after React
            // received its revision but before this transaction starts.
            sql.execute("UPDATE variable SET instruction_id=2 WHERE id=7 AND bot_job_id=19");

            UpdatedRow parent = update(1, 20, 1);
            UpdatedRow child = update(2, 20, 2);
            child.setParentId(1);
            child.setParentBlockId(20);

            SQLException error = assertThrows(
                    SQLException.class,
                    () -> new InstructionMoveTransaction().execute(
                            connection,
                            "block",
                            19,
                            List.of(parent, child),
                            expectedRevision,
                            2));

            assertEquals(
                    "Instruction graph revision changed before persistence.",
                    error.getMessage());
            try (ResultSet rows = sql.executeQuery(
                    "SELECT id,block_id,instruction_order_number,parent_block_id "
                            + "FROM instruction WHERE bot_job_id=19 ORDER BY id")) {
                rows.next();
                assertEquals(1, rows.getInt("id"));
                assertEquals(10, rows.getInt("block_id"));
                assertEquals(1, rows.getInt("instruction_order_number"));
                rows.next();
                assertEquals(2, rows.getInt("id"));
                assertEquals(10, rows.getInt("block_id"));
                assertEquals(2, rows.getInt("instruction_order_number"));
                assertEquals(10, rows.getInt("parent_block_id"));
                assertFalse(rows.next());
            }
            try (ResultSet variable = sql.executeQuery(
                    "SELECT instruction_id FROM variable WHERE id=7 AND bot_job_id=19")) {
                variable.next();
                assertEquals(2, variable.getInt("instruction_id"));
            }
        }
    }

    @Test
    void acceptsMatchingComponentRevisionUsingScopedComponentVariableOwnership()
            throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement sql = connection.createStatement()) {
            createProductionRevisionSchema(sql, true);
            seedProductionRevisionGraph(sql, true, 7);
            String expectedRevision = productionGraphRevision(true, 7);

            UpdatedRow parent = update(1, 20, 1);
            UpdatedRow child = update(2, 20, 2);
            child.setParentId(1);
            child.setParentBlockId(20);

            new InstructionMoveTransaction().execute(
                    connection,
                    "component_block",
                    7,
                    List.of(parent, child),
                    expectedRevision,
                    2);

            try (ResultSet rows = sql.executeQuery(
                    "SELECT id,block_id,instruction_order_number,parent_block_id "
                            + "FROM component_instruction WHERE home_banking_id=7 ORDER BY id")) {
                rows.next();
                assertEquals(20, rows.getInt("block_id"));
                assertEquals(1, rows.getInt("instruction_order_number"));
                rows.next();
                assertEquals(20, rows.getInt("block_id"));
                assertEquals(2, rows.getInt("instruction_order_number"));
                assertEquals(20, rows.getInt("parent_block_id"));
                assertFalse(rows.next());
            }
        }
    }

    @Test
    void rejectsDuplicateMissingAndGappedLayouts() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE block(id INTEGER PRIMARY KEY,bot_job_id INTEGER,block_order_number INTEGER)");
            sql.execute("CREATE TABLE instruction(id INTEGER PRIMARY KEY,bot_job_id INTEGER,block_id INTEGER,"
                    + "instruction_order_number INTEGER,parent_id INTEGER,parent_block_id INTEGER)");
            sql.execute("INSERT INTO block VALUES(10,19,1)");
            sql.execute("INSERT INTO instruction VALUES(1,19,10,1,NULL,NULL),(2,19,10,2,NULL,NULL)");

            assertThrows(SQLException.class, () -> new InstructionMoveTransaction().execute(
                    connection, "block", 19, List.of(update(1, 10, 1), update(1, 10, 2), update(2, 10, 3))));
            assertThrows(SQLException.class, () -> new InstructionMoveTransaction().execute(
                    connection, "block", 19, List.of(update(1, 10, 1))));
            assertThrows(SQLException.class, () -> new InstructionMoveTransaction().execute(
                    connection, "block", 19, List.of(update(1, 10, 1), update(2, 10, 3))));
            assertThrows(SQLException.class, () -> new InstructionMoveTransaction().execute(
                    connection, "block", 19, null));
            assertThrows(SQLException.class, () -> new InstructionMoveTransaction().execute(
                    connection, "block", 19, List.of()));
        }
    }

    @Test
    void rejectsForgedParentBlockAndRollsBack() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE block(id INTEGER PRIMARY KEY,bot_job_id INTEGER,block_order_number INTEGER)");
            sql.execute("CREATE TABLE instruction(id INTEGER PRIMARY KEY,bot_job_id INTEGER,block_id INTEGER,"
                    + "instruction_order_number INTEGER,parent_id INTEGER,parent_block_id INTEGER)");
            sql.execute("INSERT INTO block VALUES(10,19,1),(20,19,2)");
            sql.execute("INSERT INTO instruction VALUES(1,19,10,1,NULL,NULL),(2,19,10,2,1,10)");

            UpdatedRow parent = update(1, 10, 1);
            UpdatedRow child = update(2, 20, 1);
            child.setParentId(1);
            child.setParentBlockId(20);
            assertThrows(SQLException.class, () -> new InstructionMoveTransaction()
                    .execute(connection, "block", 19, List.of(parent, child)));

            try (ResultSet rows = sql.executeQuery("SELECT block_id,parent_block_id FROM instruction WHERE id=2")) {
                rows.next();
                assertEquals(10, rows.getInt("block_id"));
                assertEquals(10, rows.getInt("parent_block_id"));
            }
        }
    }

    private UpdatedRow update(int id, int blockId, int order) {
        UpdatedRow row = new UpdatedRow();
        row.setInstructionId(id);
        row.setBlockId(blockId);
        row.setBlockOrderNumber(blockId / 10);
        row.setInstructionOrderNumber(order);
        return row;
    }

    private void createProductionRevisionSchema(Statement sql, boolean component)
            throws SQLException {
        String ownerColumn = component ? "home_banking_id" : "bot_job_id";
        String blockTable = component ? "component_block" : "block";
        String instructionTable = component ? "component_instruction" : "instruction";
        String variableTable = component ? "component_variable" : "variable";
        sql.execute("CREATE TABLE " + blockTable
                + "(id INTEGER PRIMARY KEY," + ownerColumn
                + " INTEGER,block_order_number INTEGER)");
        sql.execute("CREATE TABLE " + instructionTable
                + "(id INTEGER PRIMARY KEY," + ownerColumn
                + " INTEGER,block_id INTEGER,instruction_order_number INTEGER,"
                + "actions TEXT,parent_id INTEGER,parent_block_id INTEGER,"
                + "variable_id INTEGER,operation TEXT)");
        sql.execute("CREATE TABLE " + variableTable
                + "(id INTEGER PRIMARY KEY," + ownerColumn
                + " INTEGER,instruction_id INTEGER)");
    }

    private void seedProductionRevisionGraph(
            Statement sql, boolean component, int ownerId)
            throws SQLException {
        String blockTable = component ? "component_block" : "block";
        String instructionTable = component ? "component_instruction" : "instruction";
        String variableTable = component ? "component_variable" : "variable";
        sql.execute("INSERT INTO " + blockTable + " VALUES"
                + "(10," + ownerId + ",1),(20," + ownerId + ",2)");
        sql.execute("INSERT INTO " + instructionTable + " VALUES"
                + "(1," + ownerId + ",10,1,'WEB_FIELD',NULL,NULL,7,'produce'),"
                + "(2," + ownerId + ",10,2,'LOOP',1,10,NULL,'repeat')");
        sql.execute("INSERT INTO " + variableTable + " VALUES"
                + "(7," + ownerId + ",1),(8," + (ownerId + 1) + ",999)");
    }

    private String productionGraphRevision(boolean component, int ownerId) {
        InstructionLoad producer = new InstructionLoad();
        producer.setId(1);
        producer.setBlockId(10);
        producer.setInstructionOrderNumber(1);
        producer.setActions("WEB_FIELD");
        producer.setVariableId(7);
        producer.setOperation("produce");

        InstructionLoad child = new InstructionLoad();
        child.setId(2);
        child.setBlockId(10);
        child.setInstructionOrderNumber(2);
        child.setActions("LOOP");
        child.setParentId(1);
        child.setParentBlockId(10);
        child.setOperation("repeat");

        VariableLoadDTO ownership = new VariableLoadDTO(
                7,
                component ? ownerId : null,
                component ? null : ownerId,
                1,
                null,
                null,
                null,
                null,
                null,
                0);
        return new InstructionGraphRevisionService()
                .revision(List.of(producer, child), List.of(ownership));
    }
}

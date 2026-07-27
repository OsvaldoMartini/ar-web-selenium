package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.InstructionLoad;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ComponentMemoryApplyServiceTest {

    @TempDir Path temporaryDirectory;

    @Test
    void wholeBlockCopiesInstructionsVariablesReferencesAndRelationshipsInOneCommit()
            throws Exception {
        String url = databaseUrl("whole-block");
        initializeDatabase(url);
        String revision = sourceRevision();
        ComponentMemoryApplyService service =
                new ComponentMemoryApplyService(() -> DriverManager.getConnection(url));

        ComponentMemoryApplyService.Result result = service.apply(new ComponentMemoryApplyService.Request(
                "copy-component-block-1",
                5,
                2,
                -1,
                List.of(ComponentMemoryApplyService.OrderedItem.componentBlock(
                        "COMPONENT:BLOCK:2:20", 20, revision))));

        assertTrue(result.committed());
        assertFalse(result.duplicate());
        assertEquals(1, result.generatedBlockIds().size());
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            assertEquals(2, scalar(statement, "SELECT COUNT(*) FROM block WHERE bot_job_id=5"));
            assertEquals(
                    2,
                    scalar(
                            statement,
                            "SELECT COUNT(*) FROM instruction WHERE bot_job_id=5"));
            assertEquals(1, scalar(statement, "SELECT COUNT(*) FROM variable WHERE bot_job_id=5"));
            assertEquals(1, scalar(statement, "SELECT COUNT(*) FROM reference WHERE bot_job_id=5"));

            try (ResultSet rows = statement.executeQuery(
                    "SELECT id, name, parent_id, variable_id, block_id "
                            + "FROM instruction WHERE bot_job_id=5 ORDER BY instruction_order_number")) {
                assertTrue(rows.next());
                int generatedWebFieldId = rows.getInt("id");
                int generatedBlockId = rows.getInt("block_id");
                assertEquals("Field", rows.getString("name"));
                assertNotNull(rows.getObject("variable_id"));

                assertTrue(rows.next());
                assertEquals("LOOP", rows.getString("name"));
                assertEquals(generatedWebFieldId, rows.getInt("parent_id"));
                assertEquals(generatedBlockId, rows.getInt("block_id"));
            }
        }
    }

    @Test
    void staleComponentRevisionRollsBackWithoutPartialRows() throws Exception {
        String url = databaseUrl("stale");
        initializeDatabase(url);
        ComponentMemoryApplyService service =
                new ComponentMemoryApplyService(() -> DriverManager.getConnection(url));

        ComponentMemoryApplyService.Result result = service.apply(new ComponentMemoryApplyService.Request(
                "stale-component-block",
                5,
                2,
                -1,
                List.of(ComponentMemoryApplyService.OrderedItem.componentBlock(
                        "COMPONENT:BLOCK:2:20", 20, "stale-revision"))));

        assertFalse(result.committed());
        assertNotNull(result.error());
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            assertEquals(1, scalar(statement, "SELECT COUNT(*) FROM block WHERE bot_job_id=5"));
            assertEquals(
                    0,
                    scalar(
                            statement,
                            "SELECT COUNT(*) FROM instruction WHERE bot_job_id=5"));
        }
    }

    @Test
    void individualCommandWithMissingParentIsRejectedWithoutCopying() throws Exception {
        String url = databaseUrl("missing-parent");
        initializeDatabase(url);
        ComponentMemoryApplyService service =
                new ComponentMemoryApplyService(() -> DriverManager.getConnection(url));

        ComponentMemoryApplyService.Result result = service.apply(new ComponentMemoryApplyService.Request(
                "copy-orphan-loop",
                5,
                2,
                10,
                List.of(ComponentMemoryApplyService.OrderedItem.componentInstruction(
                        "COMPONENT:INSTRUCTION:2:20:102", 102, 20, sourceRevision()))));

        assertFalse(result.committed());
        assertNotNull(result.error());
        assertTrue(result.error().getErrorHeader().contains("references an instruction"));
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            assertEquals(
                    0,
                    scalar(
                            statement,
                            "SELECT COUNT(*) FROM instruction WHERE bot_job_id=5"));
        }
    }

    @Test
    void duplicateRequestIdReturnsCachedSuccessWithoutCopyingTwice() throws Exception {
        String url = databaseUrl("idempotent");
        initializeDatabase(url);
        ComponentMemoryApplyService service =
                new ComponentMemoryApplyService(() -> DriverManager.getConnection(url));
        ComponentMemoryApplyService.Request request = new ComponentMemoryApplyService.Request(
                "same-request-id",
                5,
                2,
                -1,
                List.of(ComponentMemoryApplyService.OrderedItem.componentBlock(
                        "COMPONENT:BLOCK:2:20", 20, sourceRevision())));

        ComponentMemoryApplyService.Result first = service.apply(request);
        ComponentMemoryApplyService.Result retry = service.apply(request);

        assertTrue(first.committed());
        assertFalse(first.duplicate());
        assertTrue(retry.committed());
        assertTrue(retry.duplicate());
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            assertEquals(2, scalar(statement, "SELECT COUNT(*) FROM block WHERE bot_job_id=5"));
            assertEquals(
                    2,
                    scalar(
                            statement,
                            "SELECT COUNT(*) FROM instruction WHERE bot_job_id=5"));
        }
    }

    @Test
    void reusedRequestIdWithDifferentPayloadIsRefusedInsteadOfReturningOldSuccess()
            throws Exception {
        String url = databaseUrl("idempotency-payload");
        initializeDatabase(url);
        ComponentMemoryApplyService service =
                new ComponentMemoryApplyService(() -> DriverManager.getConnection(url));

        ComponentMemoryApplyService.Result first = service.apply(
                new ComponentMemoryApplyService.Request(
                        "payload-bound-request",
                        5,
                        2,
                        -1,
                        List.of(ComponentMemoryApplyService.OrderedItem.componentBlock(
                                "COMPONENT:BLOCK:2:20", 20, sourceRevision()))));
        ComponentMemoryApplyService.Result conflictingRetry = service.apply(
                new ComponentMemoryApplyService.Request(
                        "payload-bound-request",
                        5,
                        2,
                        10,
                        List.of(ComponentMemoryApplyService.OrderedItem.componentInstruction(
                                "COMPONENT:INSTRUCTION:2:20:101",
                                101,
                                20,
                                sourceRevision()))));

        assertTrue(first.committed());
        assertFalse(conflictingRetry.committed());
        assertNotNull(conflictingRetry.error());
        assertTrue(conflictingRetry.error().getErrorHeader().contains("different"));
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            assertEquals(
                    2,
                    scalar(
                            statement,
                            "SELECT COUNT(*) FROM instruction WHERE bot_job_id=5"));
        }
    }

    @Test
    void individualSimpleInstructionCopiesIntoExistingTargetBlock() throws Exception {
        String url = databaseUrl("individual");
        initializeDatabase(url);
        ComponentMemoryApplyService service =
                new ComponentMemoryApplyService(() -> DriverManager.getConnection(url));

        ComponentMemoryApplyService.Result result = service.apply(new ComponentMemoryApplyService.Request(
                "copy-individual-field",
                5,
                2,
                10,
                List.of(ComponentMemoryApplyService.OrderedItem.componentInstruction(
                        "COMPONENT:INSTRUCTION:2:20:101", 101, 20, sourceRevision()))));

        assertTrue(result.committed());
        assertEquals(1, result.generatedInstructionIds().size());
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            assertEquals(
                    1,
                    scalar(
                            statement,
                            "SELECT COUNT(*) FROM instruction WHERE bot_job_id=5 AND block_id=10"));
            assertEquals(1, scalar(statement, "SELECT COUNT(*) FROM variable WHERE bot_job_id=5"));
            assertEquals(1, scalar(statement, "SELECT COUNT(*) FROM reference WHERE bot_job_id=5"));
        }
    }

    @Test
    void blockAndItsSameInstructionDoNotCreateDuplicateInstruction() throws Exception {
        String url = databaseUrl("block-overlap");
        initializeDatabase(url);
        ComponentMemoryApplyService service =
                new ComponentMemoryApplyService(() -> DriverManager.getConnection(url));

        ComponentMemoryApplyService.Result result = service.apply(new ComponentMemoryApplyService.Request(
                "copy-overlapping-selections",
                5,
                2,
                -1,
                List.of(
                        ComponentMemoryApplyService.OrderedItem.componentBlock(
                                "COMPONENT:BLOCK:2:20", 20, sourceRevision()),
                        ComponentMemoryApplyService.OrderedItem.componentInstruction(
                                "COMPONENT:INSTRUCTION:2:20:101",
                                101,
                                20,
                                sourceRevision()))));

        assertTrue(result.committed());
        assertEquals(1, result.generatedBlockIds().size());
        assertEquals(1, result.generatedInstructionIds().size());
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            assertEquals(
                    2,
                    scalar(
                            statement,
                            "SELECT COUNT(*) FROM instruction WHERE bot_job_id=5"));
            assertEquals(
                    0,
                    scalar(
                            statement,
                            "SELECT COUNT(*) FROM instruction WHERE bot_job_id=5 AND block_id=10"));
        }
    }

    private String databaseUrl(String name) {
        return "jdbc:sqlite:" + temporaryDirectory.resolve(name + ".db");
    }

    private void initializeDatabase(String url) throws Exception {
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute(
                    "CREATE TABLE home_banking (id INTEGER PRIMARY KEY, name TEXT)");
            statement.execute(
                    "CREATE TABLE bot_job (id INTEGER PRIMARY KEY, active INTEGER NOT NULL, "
                            + "home_banking_id INTEGER)");
            statement.execute(
                    "CREATE TABLE block (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                            + "block_order_number INTEGER NOT NULL, name TEXT NOT NULL, "
                            + "description TEXT, type_id INTEGER, export_file TEXT, "
                            + "active INTEGER NOT NULL, wait INTEGER, bot_job_id INTEGER)");
            statement.execute(instructionTable("instruction", "bot_job_id"));
            statement.execute(
                    "CREATE TABLE reference (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                            + "reference_type TEXT, value TEXT, instruction_id INTEGER NOT NULL, "
                            + "bot_job_id INTEGER)");
            statement.execute(
                    "CREATE TABLE variable (id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT, "
                            + "name TEXT, value TEXT, local_format TEXT, delimiter TEXT, "
                            + "instruction_id INTEGER, bot_job_id INTEGER)");
            statement.execute(
                    "CREATE TABLE component_block (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                            + "home_banking_id INTEGER, block_order_number INTEGER NOT NULL, "
                            + "name TEXT NOT NULL, description TEXT, type_id INTEGER, "
                            + "export_file TEXT, active INTEGER, wait INTEGER)");
            statement.execute(instructionTable("component_instruction", "home_banking_id"));
            statement.execute(
                    "CREATE TABLE component_reference (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                            + "reference_type TEXT, value TEXT, instruction_id INTEGER NOT NULL, "
                            + "home_banking_id INTEGER)");
            statement.execute(
                    "CREATE TABLE component_variable (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                            + "type TEXT, name TEXT, value TEXT, local_format TEXT, delimiter TEXT, "
                            + "instruction_id INTEGER, home_banking_id INTEGER)");

            statement.execute("INSERT INTO home_banking(id,name) VALUES(2,'Bank')");
            statement.execute(
                    "INSERT INTO bot_job(id,active,home_banking_id) VALUES(5,1,2)");
            statement.execute(
                    "INSERT INTO block(id,block_order_number,name,description,type_id,active,wait,bot_job_id) "
                            + "VALUES(10,1,'Target','Target',1,1,0,5)");
            statement.execute(
                    "INSERT INTO component_block(id,home_banking_id,block_order_number,name,"
                            + "description,type_id,active,wait) "
                            + "VALUES(20,2,1,'Reusable','Reusable block',1,1,3)");
            statement.execute(componentInstructionInsert(
                    101, 1, "C", "Field", 20, 201, null));
            statement.execute(componentInstructionInsert(
                    102, 2, "LOOP", "LOOP", 20, null, 101));
            statement.execute(
                    "INSERT INTO component_variable(id,type,name,value,local_format,delimiter,"
                            + "instruction_id,home_banking_id) "
                            + "VALUES(201,'TEXT','value','x',NULL,NULL,101,2)");
            statement.execute(
                    "INSERT INTO component_reference(id,reference_type,value,instruction_id,"
                            + "home_banking_id) VALUES(301,'xpath','//button',101,2)");
        }
    }

    private String instructionTable(String table, String ownerColumn) {
        return "CREATE TABLE " + table + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, instruction_order_number INTEGER NOT NULL,"
                + "actions TEXT, name TEXT, xpath TEXT, coordinates TEXT, force_coordinates TEXT,"
                + "iframe_xpath TEXT, tag_name TEXT, shadow_host TEXT, shadow_root TEXT,"
                + "css_selector TEXT, description TEXT, operation TEXT, optional INTEGER,"
                + "block_marked INTEGER, default_value TEXT, action_custom_max_wait_sec INTEGER,"
                + "on_hold_seconds INTEGER, codified INTEGER, export_to_abr INTEGER,"
                + "active INTEGER NOT NULL, block_id INTEGER, variable_id INTEGER,"
                + "parent_block_id INTEGER, parent_id INTEGER, " + ownerColumn + " INTEGER,"
                + "client_named TEXT)";
    }

    private String componentInstructionInsert(
            int id,
            int order,
            String action,
            String name,
            int blockId,
            Integer variableId,
            Integer parentId) {
        return "INSERT INTO component_instruction("
                + "id,instruction_order_number,actions,name,active,block_id,variable_id,parent_id,"
                + "home_banking_id,client_named) VALUES("
                + id + "," + order + ",'" + action + "','" + name + "',1," + blockId + ","
                + nullable(variableId) + "," + nullable(parentId) + ",2,NULL)";
    }

    private String sourceRevision() {
        InstructionLoad field = new InstructionLoad();
        field.setId(101);
        field.setBlockId(20);
        field.setInstructionOrderNumber(1);
        field.setActions("C");
        field.setVariableId(201);
        InstructionLoad loop = new InstructionLoad();
        loop.setId(102);
        loop.setBlockId(20);
        loop.setInstructionOrderNumber(2);
        loop.setActions("LOOP");
        loop.setParentId(101);
        return new InstructionGraphRevisionService().revision(List.of(field, loop));
    }

    private int scalar(Statement statement, String sql) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private String nullable(Integer value) {
        return value == null ? "NULL" : value.toString();
    }
}

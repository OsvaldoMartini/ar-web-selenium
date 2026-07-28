package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.ReferenceLoadDTO;
import com.allinweb.ch.model.VariableLoadDTO;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
                    "SELECT id, name, parent_id, parent_block_id, variable_id, block_id "
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
                assertEquals(generatedBlockId, rows.getInt("parent_block_id"));
            }
        }
    }

    @Test
    void componentCopyDoesNotRepairLegacySourceParentBlockAndNormalizesOnlyGeneratedClone()
            throws Exception {
        String url = databaseUrl("component-legacy-null-parent-block-copy");
        initializeDatabase(url);
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "UPDATE component_instruction "
                            + "SET actions='C', name='Child', parent_block_id=NULL "
                            + "WHERE id=102");
        }
        ComponentMemoryApplyService service =
                new ComponentMemoryApplyService(() -> DriverManager.getConnection(url));

        ComponentMemoryApplyService.Result result = service.apply(
                new ComponentMemoryApplyService.Request(
                        "component-legacy-null-parent-block-copy",
                        5,
                        2,
                        -1,
                        List.of(ComponentMemoryApplyService.OrderedItem.componentBlock(
                                "COMPONENT:BLOCK:2:20",
                                20,
                                componentRevisionWithLegacyNullParentBlock()))));

        assertTrue(result.committed());
        int generatedBlockId =
                result.generatedBlockIds().get("COMPONENT:BLOCK:2:20");
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            try (ResultSet source = statement.executeQuery(
                    "SELECT block_id,parent_id,parent_block_id "
                            + "FROM component_instruction WHERE id=102")) {
                assertTrue(source.next());
                assertEquals(20, source.getInt("block_id"));
                assertEquals(101, source.getInt("parent_id"));
                assertTrue(source.getObject("parent_block_id") == null);
            }

            int copiedParentId;
            try (ResultSet copied = statement.executeQuery(
                    "SELECT id,block_id,parent_id,parent_block_id "
                            + "FROM instruction WHERE bot_job_id=5 AND name='Child'")) {
                assertTrue(copied.next());
                copiedParentId = copied.getInt("parent_id");
                assertTrue(copied.getInt("id") != 102);
                assertEquals(generatedBlockId, copied.getInt("block_id"));
                assertTrue(copiedParentId != 101);
                assertEquals(generatedBlockId, copied.getInt("parent_block_id"));
                assertTrue(!copied.next());
            }
            assertEquals(
                    1,
                    scalar(
                            statement,
                            "SELECT COUNT(*) FROM instruction WHERE id="
                                    + copiedParentId
                                    + " AND block_id="
                                    + generatedBlockId
                                    + " AND bot_job_id=5"));
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
    void variableOwnershipChangeMakesComponentRevisionStale() throws Exception {
        String url = databaseUrl("stale-variable-owner");
        initializeDatabase(url);
        String staleRevision = sourceRevision();
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "UPDATE component_variable SET instruction_id=102 WHERE id=201");
        }
        ComponentMemoryApplyService service =
                new ComponentMemoryApplyService(() -> DriverManager.getConnection(url));

        ComponentMemoryApplyService.Result result = service.apply(
                new ComponentMemoryApplyService.Request(
                        "stale-component-variable-owner",
                        5,
                        2,
                        -1,
                        List.of(ComponentMemoryApplyService.OrderedItem.componentBlock(
                                "COMPONENT:BLOCK:2:20", 20, staleRevision))));

        assertFalse(result.committed());
        assertNotNull(result.error());
        assertTrue(result.error().getErrorHeader().contains("Components changed"));
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
        assertTrue(result.error().getErrorHeader().contains("complete connected"));
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            assertEquals(
                    0,
                    scalar(
                            statement,
                            "SELECT COUNT(*) FROM instruction WHERE bot_job_id=5"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"E", "CK", "PDF CHECK", "CSV CHECK"})
    void variableConsumerWithoutItsGetProducerIsRejectedAtomically(String consumerAction)
            throws Exception {
        String url = databaseUrl("missing-get-" + consumerAction.replace(' ', '-'));
        initializeDatabase(url);
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            statement.execute(componentInstructionInsert(
                    103, 3, "GET", "GET", 20, 201, 101));
            statement.execute(componentInstructionInsert(
                    104, 4, consumerAction, consumerAction, 20, 201, 101));
        }
        ComponentMemoryApplyService service =
                new ComponentMemoryApplyService(() -> DriverManager.getConnection(url));

        ComponentMemoryApplyService.Result result = service.apply(
                new ComponentMemoryApplyService.Request(
                        "copy-consumer-without-get-" + consumerAction,
                        5,
                        2,
                        10,
                        List.of(
                                ComponentMemoryApplyService.OrderedItem.componentInstruction(
                                        "COMPONENT:INSTRUCTION:2:20:101",
                                        101,
                                        20,
                                        variableConsumerRevision(consumerAction)),
                                ComponentMemoryApplyService.OrderedItem.componentInstruction(
                                        "COMPONENT:INSTRUCTION:2:20:104",
                                        104,
                                        20,
                                        variableConsumerRevision(consumerAction)))));

        assertFalse(result.committed());
        assertNotNull(result.error());
        assertTrue(result.error().getErrorHeader().contains("complete connected"));
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            assertEquals(
                    0,
                    scalar(
                            statement,
                            "SELECT COUNT(*) FROM instruction WHERE bot_job_id=5"));
            assertEquals(
                    0,
                    scalar(statement, "SELECT COUNT(*) FROM variable WHERE bot_job_id=5"));
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
                componentConnectedGroup(sourceRevision())));

        assertTrue(result.committed());
        assertEquals(2, result.generatedInstructionIds().size());
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            assertEquals(
                    2,
                    scalar(
                            statement,
                            "SELECT COUNT(*) FROM instruction WHERE bot_job_id=5 AND block_id=10"));
            assertEquals(1, scalar(statement, "SELECT COUNT(*) FROM variable WHERE bot_job_id=5"));
            assertEquals(1, scalar(statement, "SELECT COUNT(*) FROM reference WHERE bot_job_id=5"));
            try (ResultSet rows = statement.executeQuery(
                    "SELECT id,name,parent_id,parent_block_id,variable_id "
                            + "FROM instruction WHERE bot_job_id=5 AND block_id=10 "
                            + "ORDER BY instruction_order_number")) {
                assertTrue(rows.next());
                int generatedParentId = rows.getInt("id");
                assertEquals("Field", rows.getString("name"));
                assertNotNull(rows.getObject("variable_id"));
                assertTrue(rows.next());
                assertEquals("LOOP", rows.getString("name"));
                assertEquals(generatedParentId, rows.getInt("parent_id"));
                assertEquals(10, rows.getInt("parent_block_id"));
            }
        }
    }

    @Test
    void newEndTargetBlockAndIndividualInstructionCommitAtomically() throws Exception {
        String url = databaseUrl("new-target-end");
        initializeDatabase(url);
        ComponentMemoryApplyService service =
                new ComponentMemoryApplyService(() -> DriverManager.getConnection(url));

        ComponentMemoryApplyService.Result result = service.apply(
                new ComponentMemoryApplyService.Request(
                        "new-target-end-1",
                        5,
                        2,
                        -1,
                        componentConnectedGroup(sourceRevision()),
                        new ComponentMemoryApplyService.NewTargetBlock(
                                "Created Target",
                                BlockCreationService.Position.END,
                                null,
                                null)));

        assertTrue(result.committed());
        assertFalse(result.duplicate());
        assertEquals(2, result.createdTargetBlockOrderNumber());
        assertTrue(result.createdTargetBlockId() > 0);
        assertEquals(2, result.generatedInstructionIds().size());
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            int createdBlockId = result.createdTargetBlockId();
            assertEquals(
                    1,
                    scalar(
                            statement,
                            "SELECT COUNT(*) FROM block WHERE id=" + createdBlockId
                                    + " AND bot_job_id=5 AND name='Created Target'"
                                    + " AND block_order_number=2"));
            assertEquals(
                    1,
                    scalar(
                            statement,
                            "SELECT COUNT(*) FROM instruction WHERE bot_job_id=5 AND block_id="
                                    + createdBlockId + " AND name='Field'"));
        }
    }

    @Test
    void newTargetBlockReceivesScannerInstructionAndItsReferenceInOneCommit()
            throws Exception {
        String url = databaseUrl("new-target-scanner");
        initializeDatabase(url);
        ComponentMemoryApplyService service =
                new ComponentMemoryApplyService(() -> DriverManager.getConnection(url));
        InstructionLoad scannerInstruction = new InstructionLoad();
        scannerInstruction.setInstructionOrderNumber(1);
        scannerInstruction.setActions("C");
        scannerInstruction.setName("Scanned Button");
        scannerInstruction.setTagName("button");
        scannerInstruction.setXpath("//button[@data-testid='submit']");
        scannerInstruction.setInstructionActive(true);
        scannerInstruction.setBlockId(7_777);
        ReferenceLoadDTO reference = new ReferenceLoadDTO();
        reference.setReferenceType("currentXPath");
        reference.setValue("//button[@data-testid='submit']");
        scannerInstruction.setReferenceLoadDTOList(List.of(reference));

        ComponentMemoryApplyService.Result result = service.apply(
                new ComponentMemoryApplyService.Request(
                        "new-target-scanner-1",
                        5,
                        2,
                        -1,
                        List.of(ComponentMemoryApplyService.OrderedItem.scanner(
                                "PAGE_SCANNER:scanner-row-1", scannerInstruction)),
                        new ComponentMemoryApplyService.NewTargetBlock(
                                "Scanner Target",
                                BlockCreationService.Position.END,
                                null,
                                null)));

        assertTrue(result.committed());
        assertFalse(result.duplicate());
        assertTrue(result.createdTargetBlockId() > 0);
        assertEquals(2, result.createdTargetBlockOrderNumber());
        assertEquals(1, result.generatedInstructionIds().size());
        int generatedInstructionId =
                result.generatedInstructionIds().get("PAGE_SCANNER:scanner-row-1");
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            int createdBlockId = result.createdTargetBlockId();
            assertEquals(
                    1,
                    scalar(
                            statement,
                            "SELECT COUNT(*) FROM instruction WHERE id="
                                    + generatedInstructionId + " AND bot_job_id=5 AND block_id="
                                    + createdBlockId + " AND name='Scanned Button'"));
            assertEquals(
                    1,
                    scalar(
                            statement,
                            "SELECT COUNT(*) FROM reference WHERE instruction_id="
                                    + generatedInstructionId + " AND bot_job_id=5"
                                    + " AND reference_type='currentXPath'"
                                    + " AND value='//button[@data-testid=''submit'']'"));
        }
    }

    @Test
    void duplicateNewTargetRequestCreatesOnlyOneBlockAndInstructionSet() throws Exception {
        String url = databaseUrl("new-target-idempotent");
        initializeDatabase(url);
        ComponentMemoryApplyService service =
                new ComponentMemoryApplyService(() -> DriverManager.getConnection(url));
        ComponentMemoryApplyService.Request request =
                new ComponentMemoryApplyService.Request(
                        "new-target-same-request",
                        5,
                        2,
                        -1,
                        componentConnectedGroup(sourceRevision()),
                        new ComponentMemoryApplyService.NewTargetBlock(
                                "Idempotent Target",
                                BlockCreationService.Position.END,
                                null,
                                null));

        ComponentMemoryApplyService.Result first = service.apply(request);
        ComponentMemoryApplyService.Result retry = service.apply(request);

        assertTrue(first.committed());
        assertFalse(first.duplicate());
        assertTrue(retry.committed());
        assertTrue(retry.duplicate());
        assertEquals(first.createdTargetBlockId(), retry.createdTargetBlockId());
        assertEquals(
                first.createdTargetBlockOrderNumber(),
                retry.createdTargetBlockOrderNumber());
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            assertEquals(2, scalar(statement, "SELECT COUNT(*) FROM block WHERE bot_job_id=5"));
            assertEquals(
                    1,
                    scalar(
                            statement,
                            "SELECT COUNT(*) FROM block WHERE bot_job_id=5"
                                    + " AND name='Idempotent Target'"));
            assertEquals(
                    2,
                    scalar(
                            statement,
                            "SELECT COUNT(*) FROM instruction WHERE bot_job_id=5"));
        }
    }

    @Test
    void orphanInstructionFailureRollsBackNewTargetAndShiftedOrder() throws Exception {
        String url = databaseUrl("new-target-rollback");
        initializeDatabase(url);
        ComponentMemoryApplyService service =
                new ComponentMemoryApplyService(() -> DriverManager.getConnection(url));

        ComponentMemoryApplyService.Result result = service.apply(
                new ComponentMemoryApplyService.Request(
                        "new-target-orphan",
                        5,
                        2,
                        -1,
                        List.of(ComponentMemoryApplyService.OrderedItem.componentInstruction(
                                "COMPONENT:INSTRUCTION:2:20:102",
                                102,
                                20,
                                sourceRevision())),
                        new ComponentMemoryApplyService.NewTargetBlock(
                                "Rolled Back Target",
                                BlockCreationService.Position.BEFORE,
                                10,
                                1)));

        assertFalse(result.committed());
        assertNotNull(result.error());
        assertTrue(result.error().getErrorHeader().contains("complete connected"));
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            assertEquals(1, scalar(statement, "SELECT COUNT(*) FROM block WHERE bot_job_id=5"));
            assertEquals(
                    1,
                    scalar(
                            statement,
                            "SELECT block_order_number FROM block WHERE id=10 AND bot_job_id=5"));
            assertEquals(
                    0,
                    scalar(
                            statement,
                            "SELECT COUNT(*) FROM block WHERE bot_job_id=5"
                                    + " AND name='Rolled Back Target'"));
            assertEquals(
                    0,
                    scalar(
                            statement,
                            "SELECT COUNT(*) FROM instruction WHERE bot_job_id=5"));
        }
    }

    @Test
    void newTargetBeforeExistingBlockKeepsContiguousOrderAndReceivesInstruction()
            throws Exception {
        String url = databaseUrl("new-target-before");
        initializeDatabase(url);
        ComponentMemoryApplyService service =
                new ComponentMemoryApplyService(() -> DriverManager.getConnection(url));

        ComponentMemoryApplyService.Result result = service.apply(
                new ComponentMemoryApplyService.Request(
                        "new-target-before-1",
                        5,
                        2,
                        -1,
                        componentConnectedGroup(sourceRevision()),
                        new ComponentMemoryApplyService.NewTargetBlock(
                                "Before Target",
                                BlockCreationService.Position.BEFORE,
                                10,
                                1)));

        assertTrue(result.committed());
        assertEquals(1, result.createdTargetBlockOrderNumber());
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            int createdBlockId = result.createdTargetBlockId();
            assertEquals(
                    1,
                    scalar(
                            statement,
                            "SELECT block_order_number FROM block WHERE id=" + createdBlockId
                                    + " AND bot_job_id=5"));
            assertEquals(
                    2,
                    scalar(
                            statement,
                            "SELECT block_order_number FROM block WHERE id=10 AND bot_job_id=5"));
            assertEquals(
                    2,
                    scalar(
                            statement,
                            "SELECT COUNT(DISTINCT block_order_number) FROM block"
                                    + " WHERE bot_job_id=5 AND block_order_number BETWEEN 1 AND 2"));
            assertEquals(
                    1,
                    scalar(
                            statement,
                            "SELECT COUNT(*) FROM instruction WHERE bot_job_id=5 AND block_id="
                                    + createdBlockId + " AND name='Field'"));
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

    @Test
    void connectedGotoCopiesReferencedBlockAndRemapsOnlyToGeneratedIds() throws Exception {
        String url = databaseUrl("connected-goto");
        initializeDatabase(url);
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO component_block(id,home_banking_id,block_order_number,name,"
                            + "description,type_id,active,wait) "
                            + "VALUES(30,2,2,'Caller','Caller block',1,1,0)");
            statement.execute(componentInstructionInsert(
                    201, 1, "GOTO", "GOTO", 30, null, 101));
            statement.execute(
                    "UPDATE component_instruction SET parent_block_id=20 WHERE id=201");
        }
        String revision = componentRevisionWithGoto();
        ComponentMemoryApplyService service =
                new ComponentMemoryApplyService(() -> DriverManager.getConnection(url));

        ComponentMemoryApplyService.Result result = service.apply(
                new ComponentMemoryApplyService.Request(
                        "connected-goto-copy",
                        5,
                        2,
                        10,
                        List.of(
                                ComponentMemoryApplyService.OrderedItem.componentInstruction(
                                        "COMPONENT:INSTRUCTION:2:30:201",
                                        201,
                                        30,
                                        revision),
                                ComponentMemoryApplyService.OrderedItem.componentBlock(
                                        "COMPONENT:BLOCK:2:20",
                                        20,
                                        revision))));

        assertTrue(result.committed());
        int generatedTargetBlock = result.generatedBlockIds().get("COMPONENT:BLOCK:2:20");
        int generatedGotoId =
                result.generatedInstructionIds().get("COMPONENT:INSTRUCTION:2:30:201");
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement();
                ResultSet row = statement.executeQuery(
                        "SELECT parent_id,parent_block_id,block_id FROM instruction WHERE id="
                                + generatedGotoId)) {
            assertTrue(row.next());
            int generatedParentId = row.getInt("parent_id");
            assertEquals(generatedTargetBlock, row.getInt("parent_block_id"));
            assertEquals(10, row.getInt("block_id"));
            assertTrue(generatedParentId != 101);
            assertEquals(
                    1,
                    scalar(
                            statement,
                            "SELECT COUNT(*) FROM instruction WHERE id=" + generatedParentId
                                    + " AND block_id=" + generatedTargetBlock
                                    + " AND bot_job_id=5"));
        }
    }

    @Test
    void individualWebFieldGetExtractFamilyUsesOnlyFreshRemappedIds() throws Exception {
        String url = databaseUrl("individual-variable-family");
        initializeDatabase(url);
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM component_instruction WHERE id=102");
            statement.execute(componentInstructionInsert(
                    103, 2, "GET", "Get Value", 20, 201, 101));
            statement.execute(componentInstructionInsert(
                    104, 3, "E", "Extract Field", 20, 201, 101));
            statement.execute(
                    "UPDATE component_instruction SET parent_block_id=20 WHERE id IN (103,104)");
            statement.execute(
                    "INSERT INTO component_reference(id,reference_type,value,instruction_id,"
                            + "home_banking_id) VALUES"
                            + "(302,'css','#account',103,2),"
                            + "(303,'text','Balance',104,2)");
        }
        String revision = webFieldGetExtractRevision();
        ComponentMemoryApplyService service =
                new ComponentMemoryApplyService(() -> DriverManager.getConnection(url));

        ComponentMemoryApplyService.Result result = service.apply(
                new ComponentMemoryApplyService.Request(
                        "individual-variable-family-copy",
                        5,
                        2,
                        10,
                        List.of(
                                ComponentMemoryApplyService.OrderedItem.componentInstruction(
                                        "COMPONENT:INSTRUCTION:2:20:101",
                                        101,
                                        20,
                                        revision),
                                ComponentMemoryApplyService.OrderedItem.componentInstruction(
                                        "COMPONENT:INSTRUCTION:2:20:103",
                                        103,
                                        20,
                                        revision),
                                ComponentMemoryApplyService.OrderedItem.componentInstruction(
                                        "COMPONENT:INSTRUCTION:2:20:104",
                                        104,
                                        20,
                                        revision))));

        assertTrue(result.committed());
        assertEquals(3, result.generatedInstructionIds().size());
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            int generatedVariableId;
            int generatedVariableOwner;
            try (ResultSet variable = statement.executeQuery(
                    "SELECT id,instruction_id FROM variable WHERE bot_job_id=5")) {
                assertTrue(variable.next());
                generatedVariableId = variable.getInt("id");
                generatedVariableOwner = variable.getInt("instruction_id");
                assertTrue(generatedVariableId != 201);
                assertTrue(!variable.next());
            }

            int generatedFieldId = -1;
            int generatedGetId = -1;
            int generatedExtractId = -1;
            try (ResultSet rows = statement.executeQuery(
                    "SELECT id,name,parent_id,parent_block_id,variable_id "
                            + "FROM instruction WHERE bot_job_id=5 AND block_id=10 "
                            + "ORDER BY instruction_order_number")) {
                assertTrue(rows.next());
                generatedFieldId = rows.getInt("id");
                assertEquals("Field", rows.getString("name"));
                assertTrue(rows.getObject("parent_id") == null);
                assertTrue(rows.getObject("parent_block_id") == null);
                assertEquals(generatedVariableId, rows.getInt("variable_id"));

                assertTrue(rows.next());
                generatedGetId = rows.getInt("id");
                assertEquals("Get Value", rows.getString("name"));
                assertEquals(generatedFieldId, rows.getInt("parent_id"));
                assertEquals(10, rows.getInt("parent_block_id"));
                assertEquals(generatedVariableId, rows.getInt("variable_id"));

                assertTrue(rows.next());
                generatedExtractId = rows.getInt("id");
                assertEquals("Extract Field", rows.getString("name"));
                assertEquals(generatedFieldId, rows.getInt("parent_id"));
                assertEquals(10, rows.getInt("parent_block_id"));
                assertEquals(generatedVariableId, rows.getInt("variable_id"));
                assertTrue(!rows.next());
            }
            assertEquals(generatedFieldId, generatedVariableOwner);
            assertTrue(generatedFieldId != 101);
            assertTrue(generatedGetId != 103);
            assertTrue(generatedExtractId != 104);
            assertEquals(
                    3,
                    scalar(
                            statement,
                            "SELECT COUNT(*) FROM reference WHERE bot_job_id=5"
                                    + " AND instruction_id IN ("
                                    + generatedFieldId + "," + generatedGetId + ","
                                    + generatedExtractId + ")"));
            assertEquals(
                    0,
                    scalar(
                            statement,
                            "SELECT COUNT(*) FROM reference WHERE bot_job_id=5"
                                    + " AND (id IN (301,302,303)"
                                    + " OR instruction_id IN (101,103,104))"));
        }
    }

    @Test
    void botJobSameBlockGotoFamilyIsClonedWithFreshIdsAndSourceRemainsUnchanged()
            throws Exception {
        String url = databaseUrl("same-block-goto-copy");
        initializeDatabase(url);
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO block(id,block_order_number,name,description,type_id,active,wait,"
                            + "bot_job_id) VALUES(11,2,'Destination','Destination',1,1,0,5)");
            statement.execute(
                    "INSERT INTO instruction(id,instruction_order_number,actions,name,active,"
                            + "block_id,parent_block_id,parent_id,bot_job_id) VALUES"
                            + "(501,1,'C','Same-block target',1,10,NULL,NULL,5),"
                            + "(502,2,'GOTO','Same-block GOTO',1,10,10,501,5),"
                            + "(503,1,'C','Destination member',1,11,NULL,NULL,5)");
        }
        ComponentMemoryApplyService service =
                new ComponentMemoryApplyService(() -> DriverManager.getConnection(url));

        ComponentMemoryApplyService.Result result = service.apply(
                new ComponentMemoryApplyService.Request(
                        "same-block-goto-copy",
                        5,
                        2,
                        11,
                        List.of(
                                ComponentMemoryApplyService.OrderedItem.botJob(
                                        "BOT_JOB:501", 501),
                                ComponentMemoryApplyService.OrderedItem.botJob(
                                        "BOT_JOB:502", 502))));

        assertTrue(result.committed());
        int copiedTargetId = result.generatedInstructionIds().get("BOT_JOB:501");
        int copiedGotoId = result.generatedInstructionIds().get("BOT_JOB:502");
        assertTrue(copiedTargetId != 501);
        assertTrue(copiedGotoId != 502);
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            assertEquals(5, scalar(statement, "SELECT COUNT(*) FROM instruction WHERE bot_job_id=5"));
            try (ResultSet source = statement.executeQuery(
                    "SELECT id,instruction_order_number,block_id,parent_id,parent_block_id "
                            + "FROM instruction WHERE id IN (501,502) ORDER BY id")) {
                assertTrue(source.next());
                assertEquals(501, source.getInt("id"));
                assertEquals(1, source.getInt("instruction_order_number"));
                assertEquals(10, source.getInt("block_id"));
                assertTrue(source.getObject("parent_id") == null);
                assertTrue(source.getObject("parent_block_id") == null);

                assertTrue(source.next());
                assertEquals(502, source.getInt("id"));
                assertEquals(2, source.getInt("instruction_order_number"));
                assertEquals(10, source.getInt("block_id"));
                assertEquals(501, source.getInt("parent_id"));
                assertEquals(10, source.getInt("parent_block_id"));
                assertTrue(!source.next());
            }
            try (ResultSet copied = statement.executeQuery(
                    "SELECT id,instruction_order_number,block_id,parent_id,parent_block_id "
                            + "FROM instruction WHERE id IN (" + copiedTargetId + ","
                            + copiedGotoId + ") ORDER BY instruction_order_number")) {
                assertTrue(copied.next());
                assertEquals(copiedTargetId, copied.getInt("id"));
                assertEquals(2, copied.getInt("instruction_order_number"));
                assertEquals(11, copied.getInt("block_id"));
                assertTrue(copied.getObject("parent_id") == null);
                assertTrue(copied.getObject("parent_block_id") == null);

                assertTrue(copied.next());
                assertEquals(copiedGotoId, copied.getInt("id"));
                assertEquals(3, copied.getInt("instruction_order_number"));
                assertEquals(11, copied.getInt("block_id"));
                assertEquals(copiedTargetId, copied.getInt("parent_id"));
                assertEquals(11, copied.getInt("parent_block_id"));
                assertTrue(!copied.next());
            }
        }
    }

    @Test
    void botJobCopyDoesNotRepairLegacySourceParentBlockAndNormalizesOnlyTheClone()
            throws Exception {
        String url = databaseUrl("bot-job-legacy-null-parent-block-copy");
        initializeDatabase(url);
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO block(id,block_order_number,name,description,type_id,active,wait,"
                            + "bot_job_id) VALUES(11,2,'Destination','Destination',1,1,0,5)");
            statement.execute(
                    "INSERT INTO instruction(id,instruction_order_number,actions,name,active,"
                            + "block_id,parent_block_id,parent_id,bot_job_id) VALUES"
                            + "(501,1,'C','Legacy parent',1,10,NULL,NULL,5),"
                            + "(502,2,'GET','Legacy child',1,10,NULL,501,5)");
        }
        ComponentMemoryApplyService service =
                new ComponentMemoryApplyService(() -> DriverManager.getConnection(url));

        ComponentMemoryApplyService.Result result = service.apply(
                new ComponentMemoryApplyService.Request(
                        "bot-job-legacy-null-parent-block-copy",
                        5,
                        2,
                        11,
                        List.of(
                                ComponentMemoryApplyService.OrderedItem.botJob(
                                        "BOT_JOB:501", 501),
                                ComponentMemoryApplyService.OrderedItem.botJob(
                                        "BOT_JOB:502", 502))));

        assertTrue(result.committed());
        int copiedParentId = result.generatedInstructionIds().get("BOT_JOB:501");
        int copiedChildId = result.generatedInstructionIds().get("BOT_JOB:502");
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            try (ResultSet source = statement.executeQuery(
                    "SELECT block_id,parent_id,parent_block_id FROM instruction WHERE id=502")) {
                assertTrue(source.next());
                assertEquals(10, source.getInt("block_id"));
                assertEquals(501, source.getInt("parent_id"));
                assertTrue(source.getObject("parent_block_id") == null);
            }
            try (ResultSet copied = statement.executeQuery(
                    "SELECT block_id,parent_id,parent_block_id FROM instruction WHERE id="
                            + copiedChildId)) {
                assertTrue(copied.next());
                assertEquals(11, copied.getInt("block_id"));
                assertEquals(copiedParentId, copied.getInt("parent_id"));
                assertEquals(11, copied.getInt("parent_block_id"));
            }
        }
    }

    @Test
    void botJobWebFieldGetExtractFamilyCopiesVariablesReferencesAndKeepsSource()
            throws Exception {
        String url = databaseUrl("bot-job-variable-family-copy");
        initializeDatabase(url);
        seedBotJobWebFieldFamily(url);
        ComponentMemoryApplyService service =
                new ComponentMemoryApplyService(() -> DriverManager.getConnection(url));

        ComponentMemoryApplyService.Result result = service.apply(
                new ComponentMemoryApplyService.Request(
                        "bot-job-variable-family-copy",
                        5,
                        2,
                        11,
                        List.of(
                                ComponentMemoryApplyService.OrderedItem.botJob(
                                        "BOT_JOB:503", 503),
                                ComponentMemoryApplyService.OrderedItem.botJob(
                                        "BOT_JOB:502", 502),
                                ComponentMemoryApplyService.OrderedItem.botJob(
                                        "BOT_JOB:501", 501))));

        assertTrue(result.committed());
        int copiedFieldId = result.generatedInstructionIds().get("BOT_JOB:501");
        int copiedGetId = result.generatedInstructionIds().get("BOT_JOB:502");
        int copiedExtractId = result.generatedInstructionIds().get("BOT_JOB:503");
        assertTrue(copiedFieldId != 501);
        assertTrue(copiedGetId != 502);
        assertTrue(copiedExtractId != 503);

        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            assertEquals(
                    3,
                    scalar(
                            statement,
                            "SELECT COUNT(*) FROM instruction WHERE bot_job_id=5 AND block_id=10"));
            assertEquals(
                    3,
                    scalar(
                            statement,
                            "SELECT COUNT(*) FROM instruction WHERE bot_job_id=5 AND block_id=11"));
            assertEquals(2, scalar(statement, "SELECT COUNT(*) FROM variable WHERE bot_job_id=5"));
            assertEquals(6, scalar(statement, "SELECT COUNT(*) FROM reference WHERE bot_job_id=5"));

            try (ResultSet source = statement.executeQuery(
                    "SELECT id,instruction_order_number,block_id,variable_id,parent_id,parent_block_id "
                            + "FROM instruction WHERE id IN (501,502,503) "
                            + "ORDER BY instruction_order_number")) {
                assertTrue(source.next());
                assertEquals(501, source.getInt("id"));
                assertEquals(1, source.getInt("instruction_order_number"));
                assertEquals(10, source.getInt("block_id"));
                assertEquals(701, source.getInt("variable_id"));
                assertTrue(source.getObject("parent_id") == null);

                assertTrue(source.next());
                assertEquals(502, source.getInt("id"));
                assertEquals(2, source.getInt("instruction_order_number"));
                assertEquals(10, source.getInt("block_id"));
                assertEquals(701, source.getInt("variable_id"));
                assertEquals(501, source.getInt("parent_id"));
                assertEquals(10, source.getInt("parent_block_id"));

                assertTrue(source.next());
                assertEquals(503, source.getInt("id"));
                assertEquals(3, source.getInt("instruction_order_number"));
                assertEquals(10, source.getInt("block_id"));
                assertEquals(701, source.getInt("variable_id"));
                assertEquals(501, source.getInt("parent_id"));
                assertEquals(10, source.getInt("parent_block_id"));
                assertTrue(!source.next());
            }

            int copiedVariableId;
            try (ResultSet variable = statement.executeQuery(
                    "SELECT id,type,name,value,local_format,delimiter,instruction_id "
                            + "FROM variable WHERE bot_job_id=5 AND id<>701")) {
                assertTrue(variable.next());
                copiedVariableId = variable.getInt("id");
                assertEquals("TEXT", variable.getString("type"));
                assertEquals("balance", variable.getString("name"));
                assertEquals("0", variable.getString("value"));
                assertEquals("CH", variable.getString("local_format"));
                assertEquals(",", variable.getString("delimiter"));
                assertEquals(copiedGetId, variable.getInt("instruction_id"));
                assertTrue(!variable.next());
            }

            try (ResultSet copied = statement.executeQuery(
                    "SELECT id,name,instruction_order_number,variable_id,parent_id,parent_block_id "
                            + "FROM instruction WHERE block_id=11 "
                            + "ORDER BY instruction_order_number")) {
                assertTrue(copied.next());
                assertEquals(copiedFieldId, copied.getInt("id"));
                assertEquals("Web Field", copied.getString("name"));
                assertEquals(copiedVariableId, copied.getInt("variable_id"));
                assertTrue(copied.getObject("parent_id") == null);

                assertTrue(copied.next());
                assertEquals(copiedGetId, copied.getInt("id"));
                assertEquals("Get Value", copied.getString("name"));
                assertEquals(copiedVariableId, copied.getInt("variable_id"));
                assertEquals(copiedFieldId, copied.getInt("parent_id"));
                assertEquals(11, copied.getInt("parent_block_id"));

                assertTrue(copied.next());
                assertEquals(copiedExtractId, copied.getInt("id"));
                assertEquals("Extract Field", copied.getString("name"));
                assertEquals(copiedVariableId, copied.getInt("variable_id"));
                assertEquals(copiedFieldId, copied.getInt("parent_id"));
                assertEquals(11, copied.getInt("parent_block_id"));
                assertTrue(!copied.next());
            }
            assertEquals(
                    3,
                    scalar(
                            statement,
                            "SELECT COUNT(*) FROM reference WHERE bot_job_id=5"
                                    + " AND instruction_id IN (" + copiedFieldId + ","
                                    + copiedGetId + "," + copiedExtractId + ")"));
            assertEquals(
                    3,
                    scalar(
                            statement,
                            "SELECT COUNT(*) FROM reference WHERE bot_job_id=5"
                                    + " AND instruction_id IN (501,502,503)"));
        }
    }

    @Test
    void botJobIncompleteConnectedFamilyIsRejectedWithoutAnyWrites()
            throws Exception {
        String url = databaseUrl("bot-job-incomplete-family-copy");
        initializeDatabase(url);
        seedBotJobWebFieldFamily(url);
        ComponentMemoryApplyService service =
                new ComponentMemoryApplyService(() -> DriverManager.getConnection(url));

        ComponentMemoryApplyService.Result result = service.apply(
                new ComponentMemoryApplyService.Request(
                        "bot-job-incomplete-family-copy",
                        5,
                        2,
                        11,
                        List.of(ComponentMemoryApplyService.OrderedItem.botJob(
                                "BOT_JOB:502", 502))));

        assertFalse(result.committed());
        assertNotNull(result.error());
        assertTrue(result.error().getErrorHeader().contains("complete connected Bot Job"));
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            assertEquals(3, scalar(statement, "SELECT COUNT(*) FROM instruction WHERE bot_job_id=5"));
            assertEquals(1, scalar(statement, "SELECT COUNT(*) FROM variable WHERE bot_job_id=5"));
            assertEquals(3, scalar(statement, "SELECT COUNT(*) FROM reference WHERE bot_job_id=5"));
            assertEquals(
                    3,
                    scalar(
                            statement,
                            "SELECT COUNT(*) FROM instruction WHERE bot_job_id=5 AND block_id=10"));
            assertEquals(
                    0,
                    scalar(
                            statement,
                            "SELECT COUNT(*) FROM instruction WHERE bot_job_id=5 AND block_id=11"));
        }
    }

    @Test
    void botJobFamilyCreatesNewTargetOnceAndRetryDoesNotDuplicateRows()
            throws Exception {
        String url = databaseUrl("bot-job-new-target-copy");
        initializeDatabase(url);
        seedBotJobWebFieldFamily(url);
        ComponentMemoryApplyService service =
                new ComponentMemoryApplyService(() -> DriverManager.getConnection(url));
        ComponentMemoryApplyService.Request request =
                new ComponentMemoryApplyService.Request(
                        "bot-job-new-target-copy",
                        5,
                        2,
                        -1,
                        List.of(
                                ComponentMemoryApplyService.OrderedItem.botJob(
                                        "BOT_JOB:501", 501),
                                ComponentMemoryApplyService.OrderedItem.botJob(
                                        "BOT_JOB:502", 502),
                                ComponentMemoryApplyService.OrderedItem.botJob(
                                        "BOT_JOB:503", 503)),
                        new ComponentMemoryApplyService.NewTargetBlock(
                                "Copied Bot Job Family",
                                BlockCreationService.Position.END,
                                null,
                                null));

        ComponentMemoryApplyService.Result first = service.apply(request);
        ComponentMemoryApplyService.Result retry = service.apply(request);

        assertTrue(first.committed());
        assertFalse(first.duplicate());
        assertTrue(retry.committed());
        assertTrue(retry.duplicate());
        assertEquals(first.createdTargetBlockId(), retry.createdTargetBlockId());
        assertEquals(first.generatedInstructionIds(), retry.generatedInstructionIds());
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            assertEquals(3, scalar(statement, "SELECT COUNT(*) FROM block WHERE bot_job_id=5"));
            assertEquals(
                    1,
                    scalar(
                            statement,
                            "SELECT COUNT(*) FROM block WHERE bot_job_id=5"
                                    + " AND name='Copied Bot Job Family'"));
            assertEquals(6, scalar(statement, "SELECT COUNT(*) FROM instruction WHERE bot_job_id=5"));
            assertEquals(
                    3,
                    scalar(
                            statement,
                            "SELECT COUNT(*) FROM instruction WHERE bot_job_id=5 AND block_id="
                                    + first.createdTargetBlockId()));
            assertEquals(
                    3,
                    scalar(
                            statement,
                            "SELECT COUNT(*) FROM instruction WHERE bot_job_id=5 AND block_id=10"));
            assertEquals(2, scalar(statement, "SELECT COUNT(*) FROM variable WHERE bot_job_id=5"));
            assertEquals(6, scalar(statement, "SELECT COUNT(*) FROM reference WHERE bot_job_id=5"));
        }
    }

    @Test
    void botJobCopyFailureRollsBackNewBlockAndEveryGeneratedRow() throws Exception {
        String url = databaseUrl("bot-job-copy-rollback");
        initializeDatabase(url);
        seedBotJobWebFieldFamily(url);
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TRIGGER refuse_copied_reference BEFORE INSERT ON reference "
                            + "BEGIN SELECT RAISE(ABORT, 'forced bot clone reference failure'); END");
        }
        ComponentMemoryApplyService service =
                new ComponentMemoryApplyService(() -> DriverManager.getConnection(url));

        ComponentMemoryApplyService.Result result = service.apply(
                new ComponentMemoryApplyService.Request(
                        "bot-job-copy-rollback",
                        5,
                        2,
                        -1,
                        List.of(
                                ComponentMemoryApplyService.OrderedItem.botJob(
                                        "BOT_JOB:501", 501),
                                ComponentMemoryApplyService.OrderedItem.botJob(
                                        "BOT_JOB:502", 502),
                                ComponentMemoryApplyService.OrderedItem.botJob(
                                        "BOT_JOB:503", 503)),
                        new ComponentMemoryApplyService.NewTargetBlock(
                                "Must Roll Back",
                                BlockCreationService.Position.END,
                                null,
                                null)));

        assertFalse(result.committed());
        assertNotNull(result.error());
        assertTrue(result.error().getErrorMessage().contains("forced bot clone reference failure"));
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            assertEquals(2, scalar(statement, "SELECT COUNT(*) FROM block WHERE bot_job_id=5"));
            assertEquals(
                    0,
                    scalar(
                            statement,
                            "SELECT COUNT(*) FROM block WHERE bot_job_id=5"
                                    + " AND name='Must Roll Back'"));
            assertEquals(3, scalar(statement, "SELECT COUNT(*) FROM instruction WHERE bot_job_id=5"));
            assertEquals(1, scalar(statement, "SELECT COUNT(*) FROM variable WHERE bot_job_id=5"));
            assertEquals(3, scalar(statement, "SELECT COUNT(*) FROM reference WHERE bot_job_id=5"));
        }
    }

    @Test
    void botJobCrossBlockGotoCopyPreservesItsExistingSameJobDestination()
            throws Exception {
        String url = databaseUrl("bot-job-cross-block-goto-copy");
        initializeDatabase(url);
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO block(id,block_order_number,name,description,type_id,active,wait,"
                            + "bot_job_id) VALUES"
                            + "(11,2,'Navigation Target','Navigation Target',1,1,0,5),"
                            + "(12,3,'Copy Target','Copy Target',1,1,0,5)");
            statement.execute(
                    "INSERT INTO instruction(id,instruction_order_number,actions,name,active,"
                            + "block_id,parent_block_id,parent_id,bot_job_id) VALUES"
                            + "(601,1,'GOTO','Open target',1,10,11,602,5),"
                            + "(602,1,'C','Target field',1,11,NULL,NULL,5)");
        }
        ComponentMemoryApplyService service =
                new ComponentMemoryApplyService(() -> DriverManager.getConnection(url));

        ComponentMemoryApplyService.Result result = service.apply(
                new ComponentMemoryApplyService.Request(
                        "bot-job-cross-block-goto-copy",
                        5,
                        2,
                        12,
                        List.of(ComponentMemoryApplyService.OrderedItem.botJob(
                                "BOT_JOB:601", 601))));

        assertTrue(result.committed());
        int copiedGotoId = result.generatedInstructionIds().get("BOT_JOB:601");
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            try (ResultSet source = statement.executeQuery(
                    "SELECT block_id,parent_block_id,parent_id FROM instruction WHERE id=601")) {
                assertTrue(source.next());
                assertEquals(10, source.getInt("block_id"));
                assertEquals(11, source.getInt("parent_block_id"));
                assertEquals(602, source.getInt("parent_id"));
            }
            try (ResultSet copied = statement.executeQuery(
                    "SELECT block_id,parent_block_id,parent_id FROM instruction WHERE id="
                            + copiedGotoId)) {
                assertTrue(copied.next());
                assertEquals(12, copied.getInt("block_id"));
                assertEquals(11, copied.getInt("parent_block_id"));
                assertEquals(602, copied.getInt("parent_id"));
            }
        }
    }

    @Test
    void botJobCrossBlockGotoCopyRefusesItsOwnNavigationDestinationAsTarget()
            throws Exception {
        String url = databaseUrl("bot-job-cross-block-goto-self-target-refused");
        initializeDatabase(url);
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO block(id,block_order_number,name,description,type_id,active,wait,"
                            + "bot_job_id) VALUES"
                            + "(11,2,'Navigation Target','Navigation Target',1,1,0,5)");
            statement.execute(
                    "INSERT INTO instruction(id,instruction_order_number,actions,name,active,"
                            + "block_id,parent_block_id,parent_id,bot_job_id) VALUES"
                            + "(601,1,'GOTO','Open target',1,10,11,602,5),"
                            + "(602,1,'C','Target field',1,11,NULL,NULL,5)");
        }
        ComponentMemoryApplyService service =
                new ComponentMemoryApplyService(() -> DriverManager.getConnection(url));

        ComponentMemoryApplyService.Result result = service.apply(
                new ComponentMemoryApplyService.Request(
                        "bot-job-cross-block-goto-self-target-refused",
                        5,
                        2,
                        11,
                        List.of(ComponentMemoryApplyService.OrderedItem.botJob(
                                "BOT_JOB:601", 601))));

        assertFalse(result.committed());
        assertNotNull(result.error());
        assertTrue(result.error().getErrorHeader().contains("own destination block"));
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            assertEquals(2, scalar(statement, "SELECT COUNT(*) FROM instruction WHERE bot_job_id=5"));
            assertEquals(
                    1,
                    scalar(
                            statement,
                            "SELECT COUNT(*) FROM instruction WHERE id=601"
                                    + " AND block_id=10 AND parent_block_id=11 AND parent_id=602"));
        }
    }

    @Test
    void botJobExcelGotoCopyIsRefusedBecauseTheSourceMustRemain()
            throws Exception {
        String url = databaseUrl("bot-job-excel-goto-copy-refused");
        initializeDatabase(url);
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO block(id,block_order_number,name,description,type_id,active,wait,"
                            + "bot_job_id) VALUES"
                            + "(11,2,'Navigation Target','Navigation Target',1,1,0,5),"
                            + "(12,3,'Copy Target','Copy Target',1,1,0,5)");
            statement.execute(
                    "INSERT INTO instruction(id,instruction_order_number,actions,name,active,"
                            + "block_id,parent_block_id,parent_id,bot_job_id) VALUES"
                            + "(611,1,'EXCEL GOTO','Excel navigation',1,10,11,612,5),"
                            + "(612,1,'C','Target field',1,11,NULL,NULL,5)");
        }
        ComponentMemoryApplyService service =
                new ComponentMemoryApplyService(() -> DriverManager.getConnection(url));

        ComponentMemoryApplyService.Result result = service.apply(
                new ComponentMemoryApplyService.Request(
                        "bot-job-excel-goto-copy-refused",
                        5,
                        2,
                        12,
                        List.of(ComponentMemoryApplyService.OrderedItem.botJob(
                                "BOT_JOB:611", 611))));

        assertFalse(result.committed());
        assertNotNull(result.error());
        assertTrue(result.error().getErrorHeader().contains("only one EXCEL GOTO"));
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            assertEquals(2, scalar(statement, "SELECT COUNT(*) FROM instruction WHERE bot_job_id=5"));
            assertEquals(
                    1,
                    scalar(
                            statement,
                            "SELECT COUNT(*) FROM instruction WHERE id=611"
                                    + " AND block_id=10 AND parent_block_id=11 AND parent_id=612"));
        }
    }

    @Test
    void wholeComponentBlockRequiresExternalVariableDependencies() throws Exception {
        String url = databaseUrl("whole-block-external-variable");
        initializeDatabase(url);
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO component_block(id,home_banking_id,block_order_number,name,"
                            + "description,type_id,active,wait) "
                            + "VALUES(30,2,2,'External','External dependency',1,1,0)");
            statement.execute(componentInstructionInsert(
                    201, 1, "GET", "External GET", 30, 201, null));
        }
        String revision = componentRevisionWithExternalGet();
        ComponentMemoryApplyService service =
                new ComponentMemoryApplyService(() -> DriverManager.getConnection(url));

        ComponentMemoryApplyService.Result result = service.apply(
                new ComponentMemoryApplyService.Request(
                        "whole-block-without-external-dependency",
                        5,
                        2,
                        -1,
                        List.of(ComponentMemoryApplyService.OrderedItem.componentBlock(
                                "COMPONENT:BLOCK:2:20", 20, revision))));

        assertFalse(result.committed());
        assertNotNull(result.error());
        assertTrue(result.error().getErrorHeader().contains("complete connected"));
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            assertEquals(1, scalar(statement, "SELECT COUNT(*) FROM block WHERE bot_job_id=5"));
            assertEquals(0, scalar(statement, "SELECT COUNT(*) FROM instruction WHERE bot_job_id=5"));
            assertEquals(0, scalar(statement, "SELECT COUNT(*) FROM variable WHERE bot_job_id=5"));
            assertEquals(0, scalar(statement, "SELECT COUNT(*) FROM reference WHERE bot_job_id=5"));
        }
    }

    private List<ComponentMemoryApplyService.OrderedItem> componentConnectedGroup(
            String revision) {
        return List.of(
                ComponentMemoryApplyService.OrderedItem.componentInstruction(
                        "COMPONENT:INSTRUCTION:2:20:101", 101, 20, revision),
                ComponentMemoryApplyService.OrderedItem.componentInstruction(
                        "COMPONENT:INSTRUCTION:2:20:102", 102, 20, revision));
    }

    private void seedBotJobWebFieldFamily(String url) throws Exception {
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO block(id,block_order_number,name,description,type_id,active,wait,"
                            + "bot_job_id) VALUES(11,2,'Copy Target','Copy Target',1,1,0,5)");
            statement.execute(
                    "INSERT INTO instruction("
                            + "id,instruction_order_number,actions,name,xpath,css_selector,"
                            + "description,operation,active,block_id,variable_id,parent_block_id,"
                            + "parent_id,bot_job_id,client_named) VALUES"
                            + "(501,1,'C','Web Field','//input[@id=''balance'']','#balance',"
                            + "'Balance input','ET',1,10,701,NULL,NULL,5,'balanceField'),"
                            + "(502,2,'GET','Get Value',NULL,NULL,'Read balance',NULL,"
                            + "1,10,701,10,501,5,NULL),"
                            + "(503,3,'E','Extract Field',NULL,NULL,'Extract balance',NULL,"
                            + "1,10,701,10,501,5,NULL)");
            statement.execute(
                    "INSERT INTO variable(id,type,name,value,local_format,delimiter,"
                            + "instruction_id,bot_job_id) "
                            + "VALUES(701,'TEXT','balance','0','CH',',',502,5)");
            statement.execute(
                    "INSERT INTO reference(id,reference_type,value,instruction_id,bot_job_id) VALUES"
                            + "(801,'xpath','//input[@id=''balance'']',501,5),"
                            + "(802,'css','#balance',502,5),"
                            + "(803,'text','Balance',503,5)");
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
                    "UPDATE component_instruction SET parent_block_id=20 WHERE id=102");
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
        loop.setParentBlockId(20);
        return componentRevision(List.of(field, loop));
    }

    private String componentRevisionWithLegacyNullParentBlock() {
        InstructionLoad field = revisionRow(101, 1, "C", 201, null);
        InstructionLoad child = revisionRow(102, 2, "C", null, 101);
        return componentRevision(List.of(field, child));
    }

    private String variableConsumerRevision(String consumerAction) {
        InstructionLoad field = revisionRow(101, 1, "C", 201, null);
        InstructionLoad loop = revisionRow(102, 2, "LOOP", null, 101);
        loop.setParentBlockId(20);
        InstructionLoad get = revisionRow(103, 3, "GET", 201, 101);
        InstructionLoad consumer =
                revisionRow(104, 4, consumerAction, 201, 101);
        return componentRevision(List.of(field, loop, get, consumer));
    }

    private String componentRevisionWithGoto() {
        InstructionLoad field = revisionRow(101, 1, "C", 201, null);
        field.setBlockOrderNumber(1);
        InstructionLoad loop = revisionRow(102, 2, "LOOP", null, 101);
        loop.setBlockOrderNumber(1);
        loop.setParentBlockId(20);
        InstructionLoad goTo = revisionRow(201, 1, "GOTO", null, 101);
        goTo.setBlockId(30);
        goTo.setBlockOrderNumber(2);
        goTo.setParentBlockId(20);
        return componentRevision(List.of(field, loop, goTo));
    }

    private String webFieldGetExtractRevision() {
        InstructionLoad field = revisionRow(101, 1, "C", 201, null);
        InstructionLoad get = revisionRow(103, 2, "GET", 201, 101);
        get.setParentBlockId(20);
        InstructionLoad extract = revisionRow(104, 3, "E", 201, 101);
        extract.setParentBlockId(20);
        return componentRevision(List.of(field, get, extract));
    }

    private String componentRevisionWithExternalGet() {
        InstructionLoad field = revisionRow(101, 1, "C", 201, null);
        InstructionLoad loop = revisionRow(102, 2, "LOOP", null, 101);
        loop.setParentBlockId(20);
        InstructionLoad externalGet = revisionRow(201, 1, "GET", 201, null);
        externalGet.setBlockId(30);
        return componentRevision(List.of(field, loop, externalGet));
    }

    private String componentRevision(List<InstructionLoad> rows) {
        return new InstructionGraphRevisionService()
                .revision(
                        rows,
                        List.of(new VariableLoadDTO(
                                201,
                                2,
                                null,
                                101,
                                null,
                                null,
                                null,
                                null,
                                null,
                                0)));
    }

    private InstructionLoad revisionRow(
            int id, int order, String action, Integer variableId, Integer parentId) {
        InstructionLoad row = new InstructionLoad();
        row.setId(id);
        row.setBlockId(20);
        row.setInstructionOrderNumber(order);
        row.setActions(action);
        row.setVariableId(variableId);
        row.setParentId(parentId);
        return row;
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

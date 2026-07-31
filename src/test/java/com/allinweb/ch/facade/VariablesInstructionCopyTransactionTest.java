package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.db.InstructionGraphStateRepository;
import com.allinweb.ch.db.InstructionGraphStateRepository.OwnerKey;
import com.allinweb.ch.db.migrations.M20260729_InstructionGraphState;
import com.allinweb.ch.db.migrations.M20260730_BotJobRuntimeVariables;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.AuthenticatedBotJob;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.GraphSnapshot;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.MutationRefusedException;
import com.allinweb.ch.facade.VariablesInstructionCopyTransaction.CopyResult;
import com.allinweb.ch.facade.VariablesInstructionCopyTransaction.TransactionPhase;
import com.allinweb.ch.model.VariablesInstructionCopyV1;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VariablesInstructionCopyTransactionTest {

    private static final int HOME_BANKING_ID = 2;
    private static final int BOT_JOB_ID = 5;
    private static final long WORKSPACE_EPOCH = 9L;
    private static final int SOURCE_BLOCK_ID = 10;
    private static final int TARGET_BLOCK_ID = 20;

    private final InstructionGraphStateRepository stateRepository =
            new InstructionGraphStateRepository();
    private final AuthenticatedBotJob authenticatedOwner =
            AuthenticatedBotJob.of(HOME_BANKING_ID, BOT_JOB_ID, WORKSPACE_EPOCH);

    @TempDir Path temporaryDirectory;

    @Test
    void onlyInstructionCopiesOneFreshRowWithoutMovingSourceOrCloningVariable()
            throws Exception {
        try (Connection connection = database()) {
            VariablesInstructionCopyV1.Request request =
                    request(
                            connection,
                            "copy-only-consumer",
                            VariablesInstructionCopyV1.Scope.ONLY_INSTRUCTION,
                            TARGET_BLOCK_ID,
                            102,
                            List.of(102));

            CopyResult result =
                    new VariablesInstructionCopyTransaction()
                            .execute(connection, authenticatedOwner, request);

            assertFalse(result.duplicate());
            assertEquals(List.of(102), result.sourceInstructionIds());
            assertEquals(1, result.generatedInstructionIds().size());
            assertTrue(result.generatedVariableIds().isEmpty());
            assertEquals(1, result.copiedReferenceCount());
            assertEquals(0L, result.previousGraphVersion());
            assertEquals(1L, result.committedGraphVersion());

            int generatedInstructionId =
                    result.generatedInstructionIds().get(102);
            assertNotEquals(102, generatedInstructionId);
            assertEquals(
                    TARGET_BLOCK_ID,
                    integer(
                            connection,
                            "SELECT block_id FROM instruction WHERE id=?",
                            generatedInstructionId));
            assertEquals(
                    2,
                    integer(
                            connection,
                            "SELECT instruction_order_number FROM instruction WHERE id=?",
                            generatedInstructionId));
            assertEquals(
                    501,
                    integer(
                            connection,
                            "SELECT variable_id FROM instruction WHERE id=?",
                            generatedInstructionId));
            assertNull(
                    value(
                            connection,
                            "SELECT parent_id FROM instruction WHERE id=?",
                            generatedInstructionId));
            assertNull(
                    value(
                            connection,
                            "SELECT parent_block_id FROM instruction WHERE id=?",
                            generatedInstructionId));
            assertEquals(
                    1,
                    count(
                            connection,
                            "reference",
                            "instruction_id=" + generatedInstructionId));
            assertEquals(
                    2,
                    count(
                            connection,
                            "bot_job_variable_definition",
                            "bot_job_id=5"));
            assertEquals(
                    2,
                    count(
                            connection,
                            "bot_job_runtime_variable_value",
                            "bot_job_id=5"));
            assertEquals(7, count(connection, "instruction", "bot_job_id=5"));
            assertEquals(1L, currentVersion(connection));
            assertSourceFixtureUnchanged(connection);
            assertTrue(connection.getAutoCommit());
        }
    }

    @Test
    void withParentsUsesExactRequestOrderAndRemapsParentsVariableOwnerUsersAndReferences()
            throws Exception {
        try (Connection connection = database()) {
            List<Integer> exactReactOrder = List.of(100, 102, 101);
            VariablesInstructionCopyV1.Request request =
                    request(
                            connection,
                            "copy-with-parents",
                            VariablesInstructionCopyV1.Scope.WITH_PARENTS,
                            TARGET_BLOCK_ID,
                            102,
                            exactReactOrder);

            CopyResult result =
                    new VariablesInstructionCopyTransaction()
                            .execute(connection, authenticatedOwner, request);

            assertEquals(exactReactOrder, result.sourceInstructionIds());
            assertEquals(3, result.generatedInstructionIds().size());
            assertEquals(1, result.generatedVariableIds().size());
            assertEquals(2, result.copiedReferenceCount());

            Map<Integer, Integer> generatedInstructions =
                    result.generatedInstructionIds();
            int copiedParentId = generatedInstructions.get(100);
            int copiedExcelId = generatedInstructions.get(102);
            int copiedGetId = generatedInstructions.get(101);
            int copiedVariableId = result.generatedVariableIds().get(501);

            assertNotEquals(100, copiedParentId);
            assertNotEquals(102, copiedExcelId);
            assertNotEquals(101, copiedGetId);
            assertNotEquals(501, copiedVariableId);
            assertEquals(
                    List.of(copiedParentId, copiedExcelId, copiedGetId),
                    instructionIdsByOrder(connection, TARGET_BLOCK_ID));
            assertEquals(
                    List.of(2, 3, 4),
                    instructionOrders(
                            connection,
                            List.of(copiedParentId, copiedExcelId, copiedGetId)));

            assertRelationship(
                    connection,
                    copiedExcelId,
                    TARGET_BLOCK_ID,
                    copiedParentId,
                    copiedVariableId);
            assertRelationship(
                    connection,
                    copiedGetId,
                    TARGET_BLOCK_ID,
                    copiedParentId,
                    copiedVariableId);
            assertEquals(
                    copiedVariableId,
                    integer(
                            connection,
                            "SELECT variable_id FROM instruction WHERE id=?",
                            copiedParentId));
            assertEquals(
                    copiedParentId,
                    integer(
                            connection,
                            "SELECT producer_instruction_id"
                                    + " FROM bot_job_variable_definition WHERE id=?",
                            copiedVariableId));
            assertEquals(
                    "account_number",
                    value(
                            connection,
                            "SELECT name FROM bot_job_variable_definition WHERE id=?",
                            copiedVariableId));
            assertEquals(
                    2,
                    count(
                            connection,
                            "reference",
                            "instruction_id IN ("
                                    + copiedExcelId
                                    + ","
                                    + copiedGetId
                                    + ")"));
            assertEquals(
                    3,
                    count(
                            connection,
                            "bot_job_variable_definition",
                            "bot_job_id=5"));
            assertEquals(
                    1,
                    count(
                            connection,
                            "bot_job_runtime_variable_value",
                            "bot_job_id=5"
                                    + " AND variable_id="
                                    + copiedVariableId
                                    + " AND value_state='VOID'"));
            assertEquals(9, count(connection, "instruction", "bot_job_id=5"));
            assertSourceFixtureUnchanged(connection);
        }
    }

    @Test
    void rejectsStaleRevisionAndIncompleteExactParentSelectionWithoutWriting()
            throws Exception {
        try (Connection connection = database()) {
            VariablesInstructionCopyV1.Request current =
                    request(
                            connection,
                            "stale-copy",
                            VariablesInstructionCopyV1.Scope.ONLY_INSTRUCTION,
                            TARGET_BLOCK_ID,
                            102,
                            List.of(102));
            VariablesInstructionCopyV1.Request stale =
                    new VariablesInstructionCopyV1.Request(
                            current.contractVersion(),
                            current.requestId(),
                            current.bindingEpoch(),
                            current.workspaceEpoch(),
                            current.baseGraphVersion(),
                            "stale-revision",
                            current.targetBlockId(),
                            current.selectedInstructionId(),
                            current.scope(),
                            current.sourceInstructionIds());

            MutationRefusedException staleFailure =
                    assertThrows(
                            MutationRefusedException.class,
                            () ->
                                    new VariablesInstructionCopyTransaction()
                                            .execute(
                                                    connection,
                                                    authenticatedOwner,
                                                    stale));
            assertEquals(
                    "VARIABLE_COPY_GRAPH_REVISION_STALE",
                    staleFailure.code());
            assertInitialState(connection);

            VariablesInstructionCopyV1.Request missingParent =
                    request(
                            connection,
                            "missing-parent-copy",
                            VariablesInstructionCopyV1.Scope.WITH_PARENTS,
                            TARGET_BLOCK_ID,
                            102,
                            List.of(102));
            MutationRefusedException parentFailure =
                    assertThrows(
                            MutationRefusedException.class,
                            () ->
                                    new VariablesInstructionCopyTransaction()
                                            .execute(
                                                    connection,
                                                    authenticatedOwner,
                                                    missingParent));
            assertEquals(
                    "VARIABLE_COPY_PARENT_NOT_SELECTED",
                    parentFailure.code());
            assertInitialState(connection);
            assertTrue(connection.getAutoCommit());
        }
    }

    @Test
    void rollsBackFreshRowsVariablesReferencesAndGraphVersionAtEveryPhase()
            throws Exception {
        for (TransactionPhase failedPhase : TransactionPhase.values()) {
            try (Connection connection = database()) {
                VariablesInstructionCopyV1.Request request =
                        request(
                                connection,
                                "rollback-" + failedPhase,
                                VariablesInstructionCopyV1.Scope.WITH_PARENTS,
                                TARGET_BLOCK_ID,
                                102,
                                List.of(100, 101, 102));
                VariablesInstructionCopyTransaction transaction =
                        new VariablesInstructionCopyTransaction(
                                stateRepository,
                                new InstructionGraphRevisionService(),
                                reached -> {
                                    if (reached == failedPhase) {
                                        throw new SQLException(
                                                "simulated " + failedPhase);
                                    }
                                });

                assertThrows(
                        SQLException.class,
                        () ->
                                transaction.execute(
                                        connection,
                                        authenticatedOwner,
                                        request));

                assertInitialState(connection);
                assertTrue(connection.getAutoCommit());
            }
        }
    }

    @Test
    void serviceReturnsDuplicateForExactReplayAndRejectsRequestIdReuse()
            throws Exception {
        String databaseUrl =
                "jdbc:sqlite:"
                        + temporaryDirectory.resolve("copy-service.sqlite").toAbsolutePath();
        try (Connection connection = DriverManager.getConnection(databaseUrl)) {
            initializeDatabase(connection);
        }
        VariablesInstructionCopyService service =
                new VariablesInstructionCopyService(
                        () -> DriverManager.getConnection(databaseUrl),
                        new VariablesInstructionCopyTransaction());
        VariablesInstructionCopyV1.Request request;
        try (Connection connection = DriverManager.getConnection(databaseUrl)) {
            request =
                    request(
                            connection,
                            "idempotent-copy",
                            VariablesInstructionCopyV1.Scope.ONLY_INSTRUCTION,
                            TARGET_BLOCK_ID,
                            102,
                            List.of(102));
        }

        CopyResult first =
                service.copy(
                        HOME_BANKING_ID,
                        BOT_JOB_ID,
                        WORKSPACE_EPOCH,
                        request);
        CopyResult replay =
                service.copy(
                        HOME_BANKING_ID,
                        BOT_JOB_ID,
                        WORKSPACE_EPOCH,
                        request);

        assertFalse(first.duplicate());
        assertTrue(replay.duplicate());
        assertEquals(
                first.generatedInstructionIds(),
                replay.generatedInstructionIds());
        assertEquals(1, service.completedRequestCount());
        try (Connection connection = DriverManager.getConnection(databaseUrl)) {
            assertEquals(7, count(connection, "instruction", "bot_job_id=5"));
            assertEquals(1L, currentVersion(connection));
        }

        VariablesInstructionCopyV1.Request reusedRequestId =
                new VariablesInstructionCopyV1.Request(
                        request.contractVersion(),
                        request.requestId(),
                        request.bindingEpoch(),
                        request.workspaceEpoch(),
                        request.baseGraphVersion(),
                        request.graphRevision(),
                        request.targetBlockId(),
                        101,
                        request.scope(),
                        List.of(101));
        MutationRefusedException reusedFailure =
                assertThrows(
                        MutationRefusedException.class,
                        () ->
                                service.copy(
                                        HOME_BANKING_ID,
                                        BOT_JOB_ID,
                                        WORKSPACE_EPOCH,
                                        reusedRequestId));
        assertEquals(
                "VARIABLE_COPY_REQUEST_ID_REUSED",
                reusedFailure.code());
    }

    private VariablesInstructionCopyV1.Request request(
            Connection connection,
            String requestId,
            VariablesInstructionCopyV1.Scope scope,
            int targetBlockId,
            int selectedInstructionId,
            List<Integer> sourceInstructionIds)
            throws Exception {
        GraphSnapshot graph =
                new BotJobGraphMutationTransaction()
                        .inspect(connection, authenticatedOwner);
        return new VariablesInstructionCopyV1.Request(
                VariablesInstructionCopyV1.CONTRACT_VERSION,
                requestId,
                "binding-epoch-1",
                WORKSPACE_EPOCH,
                graph.graphVersion(),
                graph.graphRevision(),
                targetBlockId,
                selectedInstructionId,
                scope,
                sourceInstructionIds);
    }

    private Connection database() throws Exception {
        Connection connection =
                DriverManager.getConnection("jdbc:sqlite::memory:");
        initializeDatabase(connection);
        return connection;
    }

    private void initializeDatabase(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute(
                    "CREATE TABLE home_banking("
                            + "id INTEGER PRIMARY KEY)");
            statement.execute(
                    "CREATE TABLE bot_job("
                            + "id INTEGER PRIMARY KEY,"
                            + "home_banking_id INTEGER NOT NULL)");
            statement.execute(
                    "CREATE TABLE block("
                            + "id INTEGER PRIMARY KEY,"
                            + "bot_job_id INTEGER NOT NULL,"
                            + "block_order_number INTEGER NOT NULL,"
                            + "name TEXT)");
            statement.execute(
                    "CREATE TABLE instruction("
                            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                            + "instruction_order_number INTEGER NOT NULL,"
                            + "actions TEXT,"
                            + "name TEXT,"
                            + "xpath TEXT,"
                            + "coordinates TEXT,"
                            + "force_coordinates INTEGER,"
                            + "iframe_xpath TEXT,"
                            + "tag_name TEXT,"
                            + "shadow_host TEXT,"
                            + "shadow_root TEXT,"
                            + "css_selector TEXT,"
                            + "description TEXT,"
                            + "operation TEXT,"
                            + "optional INTEGER,"
                            + "block_marked INTEGER,"
                            + "default_value TEXT,"
                            + "action_custom_max_wait_sec INTEGER,"
                            + "on_hold_seconds INTEGER,"
                            + "codified INTEGER,"
                            + "export_to_abr INTEGER,"
                            + "active INTEGER,"
                            + "block_id INTEGER NOT NULL,"
                            + "variable_id INTEGER,"
                            + "parent_block_id INTEGER,"
                            + "parent_id INTEGER,"
                            + "bot_job_id INTEGER NOT NULL,"
                            + "client_named TEXT)");
            statement.execute(
                    "CREATE TABLE variable("
                            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                            + "type TEXT,"
                            + "name TEXT,"
                            + "value TEXT,"
                            + "local_format TEXT,"
                            + "delimiter TEXT,"
                            + "instruction_id INTEGER,"
                            + "bot_job_id INTEGER NOT NULL)");
            statement.execute(
                    "CREATE TABLE reference("
                            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                            + "reference_type TEXT,"
                            + "value TEXT,"
                            + "instruction_id INTEGER,"
                            + "bot_job_id INTEGER NOT NULL)");

            statement.executeUpdate(
                    "INSERT INTO home_banking VALUES (2),(3)");
            statement.executeUpdate(
                    "INSERT INTO bot_job VALUES (5,2),(6,3)");
            statement.executeUpdate(
                    "INSERT INTO block VALUES"
                            + "(10,5,1,'Source'),"
                            + "(20,5,2,'Target'),"
                            + "(30,5,3,'Navigation Target')");
            statement.executeUpdate(
                    "INSERT INTO instruction("
                            + "id,instruction_order_number,actions,name,xpath,"
                            + "coordinates,force_coordinates,iframe_xpath,tag_name,"
                            + "shadow_host,shadow_root,css_selector,description,operation,"
                            + "optional,block_marked,default_value,"
                            + "action_custom_max_wait_sec,on_hold_seconds,codified,"
                            + "export_to_abr,active,block_id,variable_id,parent_block_id,"
                            + "parent_id,bot_job_id,client_named) VALUES"
                            + "(100,1,'O','Account field','//account','10,20',0,'',"
                            + "'input','','','','Source Web Field','read',0,0,'',"
                            + "4,0,0,0,1,10,501,NULL,NULL,5,'account'),"
                            + "(101,2,'GET','Get account','',NULL,0,'','',"
                            + "'','','','Get producer','capture',0,0,'',"
                            + "0,0,0,0,1,10,501,10,100,5,'get-account'),"
                            + "(102,3,'E','Write account','',NULL,0,'','',"
                            + "'','','','Excel consumer','write',0,0,'',"
                            + "0,0,0,1,1,10,501,10,100,5,'write-account'),"
                            + "(103,4,'C','Click child','',NULL,0,'','',"
                            + "'','','','Plain child','click',1,0,'',"
                            + "0,0,0,0,1,10,NULL,10,100,5,'click-child'),"
                            + "(200,1,'O','Target field','//target',NULL,0,'','input',"
                            + "'','','','Target Web Field','read',0,0,'',"
                            + "0,0,0,0,1,20,502,NULL,NULL,5,'target'),"
                            + "(300,1,'O','Navigation field','//navigation',NULL,0,'',"
                            + "'input','','','','Navigation Web Field','read',0,0,'',"
                            + "0,0,0,0,1,30,NULL,NULL,NULL,5,'navigation')");
            statement.executeUpdate(
                    "INSERT INTO variable("
                            + "id,type,name,value,local_format,delimiter,instruction_id,bot_job_id)"
                            + " VALUES"
                            + "(501,'$String','account_number','VOID','text',';',100,5),"
                            + "(502,'$String','target_value','ready','text',',',200,5),"
                            + "(601,'$String','foreign','VOID','text',';',NULL,6)");
            statement.executeUpdate(
                    "INSERT INTO reference("
                            + "id,reference_type,value,instruction_id,bot_job_id) VALUES"
                            + "(700,'XPATH','//get-account',101,5),"
                            + "(701,'CSS','#write-account',102,5)");
        }
        new M20260730_BotJobRuntimeVariables().apply(connection, "TEXT");
        new M20260729_InstructionGraphState().apply(connection, "TEXT");
        stateRepository.loadOrCreate(
                connection,
                OwnerKey.botJob(HOME_BANKING_ID, BOT_JOB_ID));
    }

    private void assertRelationship(
            Connection connection,
            int instructionId,
            int expectedParentBlockId,
            int expectedParentId,
            int expectedVariableId)
            throws Exception {
        assertEquals(
                expectedParentBlockId,
                integer(
                        connection,
                        "SELECT parent_block_id FROM instruction WHERE id=?",
                        instructionId));
        assertEquals(
                expectedParentId,
                integer(
                        connection,
                        "SELECT parent_id FROM instruction WHERE id=?",
                        instructionId));
        assertEquals(
                expectedVariableId,
                integer(
                        connection,
                        "SELECT variable_id FROM instruction WHERE id=?",
                        instructionId));
    }

    private void assertInitialState(Connection connection) throws Exception {
        assertEquals(6, count(connection, "instruction", "bot_job_id=5"));
        assertEquals(
                2,
                count(
                        connection,
                        "bot_job_variable_definition",
                        "bot_job_id=5"));
        assertEquals(
                2,
                count(
                        connection,
                        "bot_job_runtime_variable_value",
                        "bot_job_id=5 AND value_state='VOID'"));
        assertEquals(2, count(connection, "reference", "bot_job_id=5"));
        assertEquals(0L, currentVersion(connection));
        assertSourceFixtureUnchanged(connection);
    }

    private void assertSourceFixtureUnchanged(Connection connection)
            throws Exception {
        assertEquals(
                SOURCE_BLOCK_ID,
                integer(
                        connection,
                        "SELECT block_id FROM instruction WHERE id=?",
                        100));
        assertEquals(
                SOURCE_BLOCK_ID,
                integer(
                        connection,
                        "SELECT block_id FROM instruction WHERE id=?",
                        101));
        assertEquals(
                SOURCE_BLOCK_ID,
                integer(
                        connection,
                        "SELECT block_id FROM instruction WHERE id=?",
                        102));
        assertEquals(
                100,
                integer(
                        connection,
                        "SELECT parent_id FROM instruction WHERE id=?",
                        101));
        assertEquals(
                100,
                integer(
                        connection,
                        "SELECT parent_id FROM instruction WHERE id=?",
                        102));
        assertEquals(
                501,
                integer(
                        connection,
                        "SELECT variable_id FROM instruction WHERE id=?",
                        100));
        assertEquals(
                501,
                integer(
                        connection,
                        "SELECT variable_id FROM instruction WHERE id=?",
                        101));
        assertEquals(
                501,
                integer(
                        connection,
                        "SELECT variable_id FROM instruction WHERE id=?",
                        102));
        assertEquals(
                100,
                integer(
                        connection,
                        "SELECT producer_instruction_id"
                                + " FROM bot_job_variable_definition WHERE id=?",
                        501));
        assertEquals(
                2,
                count(
                        connection,
                        "reference",
                        "instruction_id IN (101,102)"));
    }

    private long currentVersion(Connection connection) throws Exception {
        return stateRepository
                .load(
                        connection,
                        OwnerKey.botJob(HOME_BANKING_ID, BOT_JOB_ID))
                .orElseThrow()
                .version();
    }

    private List<Integer> instructionIdsByOrder(
            Connection connection, int blockId) throws Exception {
        try (PreparedStatement statement =
                        connection.prepareStatement(
                                "SELECT id FROM instruction "
                                        + "WHERE bot_job_id=? AND block_id=? AND id<>200 "
                                        + "ORDER BY instruction_order_number,id")) {
            statement.setInt(1, BOT_JOB_ID);
            statement.setInt(2, blockId);
            try (ResultSet rows = statement.executeQuery()) {
                java.util.ArrayList<Integer> ids = new java.util.ArrayList<>();
                while (rows.next()) ids.add(rows.getInt(1));
                return List.copyOf(ids);
            }
        }
    }

    private List<Integer> instructionOrders(
            Connection connection, List<Integer> instructionIds)
            throws Exception {
        java.util.ArrayList<Integer> orders = new java.util.ArrayList<>();
        for (Integer instructionId : instructionIds) {
            orders.add(
                    integer(
                            connection,
                            "SELECT instruction_order_number FROM instruction WHERE id=?",
                            instructionId));
        }
        return List.copyOf(orders);
    }

    private int count(Connection connection, String table, String where)
            throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                "SELECT COUNT(*) FROM "
                                        + table
                                        + " WHERE "
                                        + where)) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }

    private int integer(Connection connection, String sql, int parameter)
            throws Exception {
        return ((Number) value(connection, sql, parameter)).intValue();
    }

    private Object value(Connection connection, String sql, int parameter)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, parameter);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getObject(1);
            }
        }
    }
}

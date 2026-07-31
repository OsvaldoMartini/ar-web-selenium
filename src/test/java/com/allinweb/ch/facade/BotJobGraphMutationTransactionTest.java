package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.db.InstructionGraphStateRepository;
import com.allinweb.ch.db.InstructionGraphStateRepository.OwnerKey;
import com.allinweb.ch.db.migrations.M20260729_InstructionGraphState;
import com.allinweb.ch.db.migrations.M20260730_BotJobRuntimeVariables;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.AuthenticatedBotJob;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.GraphInstructionFact;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.GraphSnapshot;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.MutationRefusedException;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.TransactionPhase;
import com.allinweb.ch.model.InstructionGraphMutationV3;
import com.allinweb.ch.model.InstructionGraphMutationV3.InstructionRelationKind;
import com.allinweb.ch.model.InstructionGraphMutationV3.InstructionRelationPatch;
import com.allinweb.ch.model.InstructionGraphMutationV3.InstructionRelationState;
import com.allinweb.ch.model.InstructionGraphMutationV3.LayoutRow;
import com.allinweb.ch.model.InstructionGraphMutationV3.NullableId;
import com.allinweb.ch.model.InstructionGraphMutationV3.PatchOperation;
import com.allinweb.ch.model.InstructionGraphMutationV3.VariableBindingPatch;
import com.allinweb.ch.model.InstructionGraphMutationV3.VariableOwnerPatch;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.VariableLoadDTO;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BotJobGraphMutationTransactionTest {

    private static final int HOME_BANKING_ID = 2;
    private static final int BOT_JOB_ID = 5;
    private static final long WORKSPACE_EPOCH = 9L;

    private final InstructionGraphStateRepository stateRepository =
            new InstructionGraphStateRepository();
    private final AuthenticatedBotJob authenticatedOwner =
            AuthenticatedBotJob.of(HOME_BANKING_ID, BOT_JOB_ID, WORKSPACE_EPOCH);

    @TempDir
    Path tempDir;

    @Test
    void atomicallyMovesLayoutAndClearsLoopWhilePreservingEmptyBlocks() throws Exception {
        try (Connection connection = database()) {
            InstructionGraphMutationV3.Request request = request(
                    connection,
                    InstructionGraphMutationV3.MutationKind.ROW_MOVE,
                    101,
                    layoutWithLoopMovedAndDisconnected(),
                    List.of(relationPatch(
                            101,
                            InstructionRelationKind.LOOP_ANCHOR,
                            PatchOperation.CLEAR,
                            relation(100, 10),
                            InstructionRelationState.disconnected())),
                    List.of(),
                    List.of());

            BotJobGraphMutationTransaction.CommitResult result =
                    new BotJobGraphMutationTransaction()
                            .execute(connection, authenticatedOwner, request);

            assertEquals(0L, result.previousGraphVersion());
            assertEquals(1L, result.committedGraphVersion());
            assertEquals(1L, currentVersion(connection));
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT block_id,instruction_order_number,parent_id,parent_block_id"
                            + " FROM instruction WHERE id=101")) {
                try (ResultSet row = statement.executeQuery()) {
                    assertTrue(row.next());
                    assertEquals(20, row.getInt("block_id"));
                    assertEquals(4, row.getInt("instruction_order_number"));
                    assertNull(nullableInteger(row, "parent_id"));
                    assertNull(nullableInteger(row, "parent_block_id"));
                }
            }
            assertEquals(3, blockCount(connection));
            assertEquals(0, instructionCount(connection, 30));
            assertTrue(connection.getAutoCommit());
            assertFalse(connection.isClosed());
        }
    }

    @Test
    void setsGotoToADifferentOwnedBlockWithoutChangingItsContainingBlock()
            throws Exception {
        try (Connection connection = database()) {
            InstructionGraphMutationV3.Request request = request(
                    connection,
                    InstructionGraphMutationV3.MutationKind.RELATIONSHIP_UPDATE,
                    102,
                    defaultLayout(),
                    List.of(relationPatch(
                            102,
                            InstructionRelationKind.BLOCK_TARGET,
                            PatchOperation.SET,
                            relation(null, 10),
                            relation(null, 30))),
                    List.of(),
                    List.of());

            new BotJobGraphMutationTransaction()
                    .execute(connection, authenticatedOwner, request);

            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT block_id,parent_id,parent_block_id FROM instruction WHERE id=102");
                    ResultSet row = statement.executeQuery()) {
                assertTrue(row.next());
                assertEquals(20, row.getInt("block_id"));
                assertNull(nullableInteger(row, "parent_id"));
                assertEquals(30, row.getInt("parent_block_id"));
            }
        }
    }

    @Test
    void appliesVariableBindingAndVariableOwnerPatchesInTheSameCommit()
            throws Exception {
        try (Connection connection = database()) {
            VariableBindingPatch binding = new VariableBindingPatch(
                    103,
                    PatchOperation.SET,
                    NullableId.of(501),
                    NullableId.of(502));
            VariableOwnerPatch owner = new VariableOwnerPatch(
                    502,
                    PatchOperation.SET,
                    NullableId.of(104),
                    NullableId.of(103));
            InstructionGraphMutationV3.Request request = request(
                    connection,
                    InstructionGraphMutationV3.MutationKind.RELATIONSHIP_UPDATE,
                    103,
                    defaultLayout(),
                    List.of(),
                    List.of(binding),
                    List.of(owner));

            new BotJobGraphMutationTransaction()
                    .execute(connection, authenticatedOwner, request);

            assertEquals(502, integerValue(
                    connection,
                    "SELECT variable_id FROM instruction WHERE id=103"));
            assertEquals(103, integerValue(
                    connection,
                    "SELECT producer_instruction_id FROM bot_job_variable_definition"
                            + " WHERE id=502"));
            assertEquals(1L, currentVersion(connection));
        }
    }

    @Test
    void atomicallyAppliesMultipleReactAuthoredRelationshipAndVariablePatches()
            throws Exception {
        try (Connection connection = database()) {
            insertDisconnectedGet(connection);
            InstructionGraphMutationV3.Request request =
                    reactAuthoredBatchRequest(connection, NullableId.of(null));

            BotJobGraphMutationTransaction.CommitResult committed =
                    new BotJobGraphMutationTransaction()
                            .execute(connection, authenticatedOwner, request);

            assertEquals(1L, committed.committedGraphVersion());
            assertNull(nullableIntegerValue(
                    connection, "SELECT parent_id FROM instruction WHERE id=101"));
            assertNull(nullableIntegerValue(
                    connection, "SELECT parent_block_id FROM instruction WHERE id=101"));
            assertNull(nullableIntegerValue(
                    connection, "SELECT parent_id FROM instruction WHERE id=103"));
            assertNull(nullableIntegerValue(
                    connection, "SELECT parent_block_id FROM instruction WHERE id=103"));
            assertNull(nullableIntegerValue(
                    connection, "SELECT variable_id FROM instruction WHERE id=103"));
            assertEquals(30, integerValue(
                    connection, "SELECT parent_block_id FROM instruction WHERE id=102"));
            assertEquals(104, integerValue(
                    connection, "SELECT parent_id FROM instruction WHERE id=105"));
            assertEquals(20, integerValue(
                    connection, "SELECT parent_block_id FROM instruction WHERE id=105"));
            assertEquals(502, integerValue(
                    connection, "SELECT variable_id FROM instruction WHERE id=105"));
            assertEquals(1L, currentVersion(connection));
            assertTrue(connection.getAutoCommit());
        }
    }

    @Test
    void rejectsTheWholeReactAuthoredBatchWhenOneExpectedValueIsStale()
            throws Exception {
        try (Connection connection = database()) {
            insertDisconnectedGet(connection);
            InstructionGraphMutationV3.Request request =
                    reactAuthoredBatchRequest(connection, NullableId.of(999));

            MutationRefusedException refused = assertThrows(
                    MutationRefusedException.class,
                    () -> new BotJobGraphMutationTransaction()
                            .execute(connection, authenticatedOwner, request));

            assertEquals("EXPECTED_VARIABLE_BINDING_MISMATCH", refused.code());
            assertEquals(100, integerValue(
                    connection, "SELECT parent_id FROM instruction WHERE id=101"));
            assertEquals(10, integerValue(
                    connection, "SELECT parent_block_id FROM instruction WHERE id=101"));
            assertEquals(104, integerValue(
                    connection, "SELECT parent_id FROM instruction WHERE id=103"));
            assertEquals(20, integerValue(
                    connection, "SELECT parent_block_id FROM instruction WHERE id=103"));
            assertEquals(501, integerValue(
                    connection, "SELECT variable_id FROM instruction WHERE id=103"));
            assertEquals(10, integerValue(
                    connection, "SELECT parent_block_id FROM instruction WHERE id=102"));
            assertNull(nullableIntegerValue(
                    connection, "SELECT parent_id FROM instruction WHERE id=105"));
            assertNull(nullableIntegerValue(
                    connection, "SELECT parent_block_id FROM instruction WHERE id=105"));
            assertNull(nullableIntegerValue(
                    connection, "SELECT variable_id FROM instruction WHERE id=105"));
            assertEquals(0L, currentVersion(connection));
            assertTrue(connection.getAutoCommit());
        }
    }

    @Test
    void refusesExpectedOldAndGraphVersionMismatchesWithoutWriting()
            throws Exception {
        try (Connection connection = database()) {
            InstructionGraphMutationV3.Request wrongExpected = request(
                    connection,
                    InstructionGraphMutationV3.MutationKind.RELATIONSHIP_UPDATE,
                    101,
                    defaultLayout(),
                    List.of(relationPatch(
                            101,
                            InstructionRelationKind.LOOP_ANCHOR,
                            PatchOperation.CLEAR,
                            relation(999, 10),
                            InstructionRelationState.disconnected())),
                    List.of(),
                    List.of());

            MutationRefusedException expectedError = assertThrows(
                    MutationRefusedException.class,
                    () -> new BotJobGraphMutationTransaction()
                            .execute(connection, authenticatedOwner, wrongExpected));
            assertEquals("EXPECTED_RELATION_MISMATCH", expectedError.code());
            assertEquals(100, integerValue(
                    connection,
                    "SELECT parent_id FROM instruction WHERE id=101"));
            assertEquals(0L, currentVersion(connection));

            InstructionGraphMutationV3.Request stale = request(
                    connection,
                    InstructionGraphMutationV3.MutationKind.ROW_MOVE,
                    101,
                    defaultLayout(),
                    List.of(),
                    List.of(),
                    List.of(),
                    1L);
            MutationRefusedException staleError = assertThrows(
                    MutationRefusedException.class,
                    () -> new BotJobGraphMutationTransaction()
                            .execute(connection, authenticatedOwner, stale));
            assertEquals("GRAPH_VERSION_MISMATCH", staleError.code());
            assertEquals(0L, currentVersion(connection));
            assertTrue(connection.getAutoCommit());
            assertFalse(connection.isClosed());
        }
    }

    @Test
    void injectedFailureAfterVersionAdvanceRollsBackEveryGraphFieldAndVersion()
            throws Exception {
        try (Connection connection = database()) {
            InstructionGraphMutationV3.Request request = request(
                    connection,
                    InstructionGraphMutationV3.MutationKind.ROW_MOVE,
                    101,
                    layoutWithLoopMovedAndDisconnected(),
                    List.of(relationPatch(
                            101,
                            InstructionRelationKind.LOOP_ANCHOR,
                            PatchOperation.CLEAR,
                            relation(100, 10),
                            InstructionRelationState.disconnected())),
                    List.of(),
                    List.of());
            BotJobGraphMutationTransaction transaction =
                    new BotJobGraphMutationTransaction(
                            new InstructionGraphMutationContractValidator(),
                            stateRepository,
                            new InstructionGraphRevisionService(),
                            phase -> {
                                if (phase == TransactionPhase.AFTER_VERSION_ADVANCE) {
                                    throw new SQLException("injected rollback");
                                }
                            });

            SQLException failure = assertThrows(
                    SQLException.class,
                    () -> transaction.execute(connection, authenticatedOwner, request));
            assertEquals("injected rollback", failure.getMessage());

            assertEquals(10, integerValue(
                    connection,
                    "SELECT block_id FROM instruction WHERE id=101"));
            assertEquals(2, integerValue(
                    connection,
                    "SELECT instruction_order_number FROM instruction WHERE id=101"));
            assertEquals(100, integerValue(
                    connection,
                    "SELECT parent_id FROM instruction WHERE id=101"));
            assertEquals(10, integerValue(
                    connection,
                    "SELECT parent_block_id FROM instruction WHERE id=101"));
            assertEquals(0L, currentVersion(connection));
            assertTrue(connection.getAutoCommit());
            assertFalse(connection.isClosed());
        }
    }

    @Test
    void refusesGotoSelfTargetAndLeavesTheDatabaseUntouched() throws Exception {
        try (Connection connection = database()) {
            InstructionGraphMutationV3.Request request = request(
                    connection,
                    InstructionGraphMutationV3.MutationKind.RELATIONSHIP_UPDATE,
                    102,
                    defaultLayout(),
                    List.of(relationPatch(
                            102,
                            InstructionRelationKind.BLOCK_TARGET,
                            PatchOperation.SET,
                            relation(null, 10),
                            relation(null, 20))),
                    List.of(),
                    List.of());

            MutationRefusedException failure = assertThrows(
                    MutationRefusedException.class,
                    () -> new BotJobGraphMutationTransaction()
                            .execute(connection, authenticatedOwner, request));

            assertEquals("BLOCK_TARGET_EQUALS_CONTAINING_BLOCK", failure.code());
            assertEquals(10, integerValue(
                    connection,
                    "SELECT parent_block_id FROM instruction WHERE id=102"));
            assertEquals(0L, currentVersion(connection));
        }
    }

    @Test
    void refusesImplicitKeepWhenGotoMovesIntoItsExistingTargetBlock() throws Exception {
        try (Connection connection = database()) {
            List<LayoutRow> movedIntoTargetBlock = List.of(
                    row(100, 10, 1, 1),
                    row(101, 10, 1, 2),
                    row(102, 10, 1, 3),
                    row(104, 20, 2, 1),
                    row(103, 20, 2, 2));
            InstructionGraphMutationV3.Request request = request(
                    connection,
                    InstructionGraphMutationV3.MutationKind.ROW_MOVE,
                    102,
                    movedIntoTargetBlock,
                    List.of(),
                    List.of(),
                    List.of());

            MutationRefusedException failure = assertThrows(
                    MutationRefusedException.class,
                    () -> new BotJobGraphMutationTransaction()
                            .execute(connection, authenticatedOwner, request));

            assertEquals("BLOCK_TARGET_EQUALS_CONTAINING_BLOCK", failure.code());
            assertEquals(20, integerValue(
                    connection,
                    "SELECT block_id FROM instruction WHERE id=102"));
            assertEquals(3, integerValue(
                    connection,
                    "SELECT instruction_order_number FROM instruction WHERE id=102"));
            assertEquals(10, integerValue(
                    connection,
                    "SELECT parent_block_id FROM instruction WHERE id=102"));
            assertEquals(0L, currentVersion(connection));
            assertTrue(connection.getAutoCommit());
        }
    }

    @Test
    void rollbackFailureClosesConnectionWithoutRestoringAutoCommitAndPreservesOriginalFailure()
            throws Exception {
        String url = "jdbc:sqlite:" + tempDir.resolve("rollback-failure.db");
        Connection delegate = database(url);
        AtomicBoolean closeAttempted = new AtomicBoolean();
        AtomicBoolean autoCommitRestoreAttempted = new AtomicBoolean();
        Connection failingConnection = rollbackFailingConnection(
                delegate,
                closeAttempted,
                autoCommitRestoreAttempted);
        InstructionGraphMutationV3.Request request = request(
                failingConnection,
                InstructionGraphMutationV3.MutationKind.ROW_MOVE,
                101,
                layoutWithLoopMovedAndDisconnected(),
                List.of(relationPatch(
                        101,
                        InstructionRelationKind.LOOP_ANCHOR,
                        PatchOperation.CLEAR,
                        relation(100, 10),
                        InstructionRelationState.disconnected())),
                List.of(),
                List.of());
        BotJobGraphMutationTransaction transaction =
                new BotJobGraphMutationTransaction(
                        new InstructionGraphMutationContractValidator(),
                        stateRepository,
                        new InstructionGraphRevisionService(),
                        phase -> {
                            if (phase == TransactionPhase.AFTER_GRAPH_WRITES) {
                                throw new SQLException("original mutation failure");
                            }
                        });

        SQLException failure;
        try {
            failure = assertThrows(
                    SQLException.class,
                    () -> transaction.execute(
                            failingConnection,
                            authenticatedOwner,
                            request));
        } finally {
            if (!delegate.isClosed()) {
                delegate.close();
            }
        }

        assertEquals("original mutation failure", failure.getMessage());
        assertEquals(2, failure.getSuppressed().length);
        assertEquals("simulated rollback failure", failure.getSuppressed()[0].getMessage());
        assertEquals("simulated close failure", failure.getSuppressed()[1].getMessage());
        assertTrue(closeAttempted.get());
        assertFalse(autoCommitRestoreAttempted.get());
        assertTrue(delegate.isClosed());

        try (Connection verification = DriverManager.getConnection(url)) {
            assertEquals(10, integerValue(
                    verification,
                    "SELECT block_id FROM instruction WHERE id=101"));
            assertEquals(2, integerValue(
                    verification,
                    "SELECT instruction_order_number FROM instruction WHERE id=101"));
            assertEquals(100, integerValue(
                    verification,
                    "SELECT parent_id FROM instruction WHERE id=101"));
            assertEquals(0L, currentVersion(verification));
        }
    }

    @Test
    void refusesRequestEpochThatDiffersFromAuthenticatedServerEpochWithoutWriting()
            throws Exception {
        try (Connection connection = database()) {
            InstructionGraphMutationV3.Request matching = request(
                    connection,
                    InstructionGraphMutationV3.MutationKind.ROW_MOVE,
                    101,
                    layoutWithLoopMovedAndDisconnected(),
                    List.of(relationPatch(
                            101,
                            InstructionRelationKind.LOOP_ANCHOR,
                            PatchOperation.CLEAR,
                            relation(100, 10),
                            InstructionRelationState.disconnected())),
                    List.of(),
                    List.of());
            InstructionGraphMutationV3.Request mismatched =
                    withWorkspaceEpoch(matching, WORKSPACE_EPOCH + 1L);

            MutationRefusedException failure = assertThrows(
                    MutationRefusedException.class,
                    () -> new BotJobGraphMutationTransaction()
                            .execute(connection, authenticatedOwner, mismatched));

            assertEquals("WORKSPACE_EPOCH_MISMATCH", failure.code());
            assertEquals(10, integerValue(
                    connection,
                    "SELECT block_id FROM instruction WHERE id=101"));
            assertEquals(2, integerValue(
                    connection,
                    "SELECT instruction_order_number FROM instruction WHERE id=101"));
            assertEquals(100, integerValue(
                    connection,
                    "SELECT parent_id FROM instruction WHERE id=101"));
            assertEquals(0L, currentVersion(connection));
            assertTrue(connection.getAutoCommit());
            assertFalse(connection.isClosed());
        }
    }

    @Test
    void inspectReturnsCanonicalVersionedLayoutAndRelationshipFactsWithoutWriting()
            throws Exception {
        try (Connection connection = database()) {
            GraphSnapshot snapshot =
                    new BotJobGraphMutationTransaction()
                            .inspect(connection, authenticatedOwner);

            assertEquals(0L, snapshot.graphVersion());
            assertEquals(revision(connection), snapshot.graphRevision());
            assertEquals(defaultLayout(), snapshot.layoutRows());
            assertEquals(
                    List.of(
                            new GraphInstructionFact(
                                    100,
                                    10,
                                    1,
                                    1,
                                    "C",
                                    null,
                                    null,
                                    null),
                            new GraphInstructionFact(
                                    101,
                                    10,
                                    1,
                                    2,
                                    "LOOP",
                                    100,
                                    10,
                                    null),
                            new GraphInstructionFact(
                                    104,
                                    20,
                                    2,
                                    1,
                                    "C",
                                    null,
                                    null,
                                    null),
                            new GraphInstructionFact(
                                    103,
                                    20,
                                    2,
                                    2,
                                    "GET",
                                    104,
                                    20,
                                    501),
                            new GraphInstructionFact(
                                    102,
                                    20,
                                    2,
                                    3,
                                    "GOTO",
                                    null,
                                    10,
                                    null)),
                    snapshot.instructionFacts());
            assertEquals(0L, currentVersion(connection));
            assertTrue(connection.getAutoCommit());
            assertFalse(connection.isClosed());
        }
    }

    @Test
    void variablesProfileCommitsOneSameBlockCommandReinsertAtomically()
            throws Exception {
        try (Connection connection = database()) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "UPDATE instruction SET instruction_order_number=4 WHERE id=102");
                statement.executeUpdate(
                        "INSERT INTO instruction VALUES"
                                + "(105,5,20,3,'H',NULL,NULL,NULL,'wait')");
            }
            List<LayoutRow> movedGetAcrossOrdinaryWait = List.of(
                    row(100, 10, 1, 1),
                    row(101, 10, 1, 2),
                    row(104, 20, 2, 1),
                    row(105, 20, 2, 2),
                    row(103, 20, 2, 3),
                    row(102, 20, 2, 4));
            InstructionGraphMutationV3.Request request = request(
                    connection,
                    InstructionGraphMutationV3.MutationKind.ROW_MOVE,
                    103,
                    movedGetAcrossOrdinaryWait,
                    List.of(),
                    List.of(),
                    List.of());

            BotJobGraphMutationTransaction.CommitResult committed =
                    new BotJobGraphMutationTransaction()
                            .executeVariablesInstructionMove(
                                    connection, authenticatedOwner, request);

            assertEquals(1L, committed.committedGraphVersion());
            assertEquals(3, integerValue(
                    connection,
                    "SELECT instruction_order_number FROM instruction WHERE id=103"));
            assertEquals(2, integerValue(
                    connection,
                    "SELECT instruction_order_number FROM instruction WHERE id=105"));
            assertEquals(104, integerValue(
                    connection,
                    "SELECT parent_id FROM instruction WHERE id=103"));
            assertEquals(501, integerValue(
                    connection,
                    "SELECT variable_id FROM instruction WHERE id=103"));
            assertEquals(4, integerValue(
                    connection,
                    "SELECT instruction_order_number FROM instruction WHERE id=102"));
            assertTrue(connection.getAutoCommit());
        }
    }

    @Test
    void variablesProfileRefusesGenericGraphMutationBeforeAnyWrite()
            throws Exception {
        try (Connection connection = database()) {
            InstructionGraphMutationV3.Request request = request(
                    connection,
                    InstructionGraphMutationV3.MutationKind.RELATIONSHIP_UPDATE,
                    103,
                    defaultLayout(),
                    List.of(),
                    List.of(),
                    List.of());

            MutationRefusedException refused = assertThrows(
                    MutationRefusedException.class,
                    () -> new BotJobGraphMutationTransaction()
                            .executeVariablesInstructionMove(
                                    connection, authenticatedOwner, request));

            assertEquals("VARIABLES_ROW_MOVE_ONLY", refused.code());
            assertEquals(2, integerValue(
                    connection,
                    "SELECT instruction_order_number FROM instruction WHERE id=103"));
            assertEquals(104, integerValue(
                    connection,
                    "SELECT parent_id FROM instruction WHERE id=103"));
            assertEquals(0L, currentVersion(connection));
            assertTrue(connection.getAutoCommit());
        }
    }

    @Test
    void variablesCrossBlockProfileCommitsOneConsumerAndExplicitReconnect()
            throws Exception {
        try (Connection connection = database()) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "UPDATE instruction SET actions='CK' WHERE id=103");
                statement.executeUpdate(
                        "UPDATE instruction SET actions='H',parent_block_id=NULL WHERE id=102");
                statement.executeUpdate(
                        "INSERT INTO instruction VALUES"
                                + "(200,5,30,1,'C',NULL,NULL,NULL,'click')");
            }
            InstructionGraphMutationV3.Request request = request(
                    connection,
                    InstructionGraphMutationV3.MutationKind.ROW_MOVE,
                    103,
                    List.of(
                            row(100, 10, 1, 1),
                            row(101, 10, 1, 2),
                            row(104, 20, 2, 1),
                            row(102, 20, 2, 2),
                            row(200, 30, 3, 1),
                            row(103, 30, 3, 2)),
                    List.of(relationPatch(
                            103,
                            InstructionRelationKind.ELEMENT_TARGET,
                            PatchOperation.SET,
                            relation(104, 20),
                            relation(200, 30))),
                    List.of(),
                    List.of());

            BotJobGraphMutationTransaction.CommitResult committed =
                    new BotJobGraphMutationTransaction()
                            .executeVariablesInstructionCrossBlockMove(
                                    connection, authenticatedOwner, request);

            assertEquals(1L, committed.committedGraphVersion());
            assertEquals(30, integerValue(
                    connection, "SELECT block_id FROM instruction WHERE id=103"));
            assertEquals(2, integerValue(
                    connection,
                    "SELECT instruction_order_number FROM instruction WHERE id=103"));
            assertEquals(200, integerValue(
                    connection, "SELECT parent_id FROM instruction WHERE id=103"));
            assertEquals(30, integerValue(
                    connection,
                    "SELECT parent_block_id FROM instruction WHERE id=103"));
            assertEquals(501, integerValue(
                    connection, "SELECT variable_id FROM instruction WHERE id=103"));
            assertEquals(100, integerValue(
                    connection,
                    "SELECT producer_instruction_id FROM bot_job_variable_definition"
                            + " WHERE id=501"));
            assertTrue(connection.getAutoCommit());
        }
    }

    private Connection database() throws Exception {
        return database("jdbc:sqlite::memory:");
    }

    private Connection database(String url) throws Exception {
        Connection connection = DriverManager.getConnection(url);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("CREATE TABLE home_banking("
                    + "id INTEGER PRIMARY KEY)");
            statement.execute("CREATE TABLE bot_job("
                    + "id INTEGER PRIMARY KEY,home_banking_id INTEGER NOT NULL)");
            statement.execute("CREATE TABLE block("
                    + "id INTEGER PRIMARY KEY,bot_job_id INTEGER NOT NULL,"
                    + "block_order_number INTEGER NOT NULL)");
            statement.execute("CREATE TABLE instruction("
                    + "id INTEGER PRIMARY KEY,bot_job_id INTEGER NOT NULL,"
                    + "block_id INTEGER NOT NULL,instruction_order_number INTEGER NOT NULL,"
                    + "actions TEXT,parent_id INTEGER,parent_block_id INTEGER,"
                    + "variable_id INTEGER,operation TEXT)");
            statement.execute("CREATE TABLE variable("
                    + "id INTEGER PRIMARY KEY,bot_job_id INTEGER NOT NULL,"
                    + "instruction_id INTEGER,type TEXT,name TEXT,value TEXT,"
                    + "local_format TEXT,delimiter TEXT)");
            statement.executeUpdate("INSERT INTO home_banking VALUES (2)");
            statement.executeUpdate("INSERT INTO bot_job VALUES (5,2)");
            statement.executeUpdate("INSERT INTO block VALUES"
                    + "(10,5,1),(20,5,2),(30,5,3)");
            statement.executeUpdate("INSERT INTO instruction VALUES"
                    + "(100,5,10,1,'C',NULL,NULL,NULL,'click'),"
                    + "(101,5,10,2,'LOOP',100,10,NULL,'1:3'),"
                    + "(104,5,20,1,'C',NULL,NULL,NULL,'click'),"
                    + "(103,5,20,2,'GET',104,20,501,'capture'),"
                    + "(102,5,20,3,'GOTO',NULL,10,NULL,'1')");
            statement.executeUpdate("INSERT INTO variable("
                    + "id,bot_job_id,instruction_id) VALUES"
                    + "(501,5,100),(502,5,104)");
        }
        new M20260730_BotJobRuntimeVariables().apply(connection, "TEXT");
        new M20260729_InstructionGraphState().apply(connection, "TEXT");
        stateRepository.loadOrCreate(connection, OwnerKey.botJob(HOME_BANKING_ID, BOT_JOB_ID));
        return connection;
    }

    private Connection rollbackFailingConnection(
            Connection delegate,
            AtomicBoolean closeAttempted,
            AtomicBoolean autoCommitRestoreAttempted) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                (proxy, method, arguments) -> {
                    if ("rollback".equals(method.getName())
                            && (arguments == null || arguments.length == 0)) {
                        throw new SQLException("simulated rollback failure");
                    }
                    if ("setAutoCommit".equals(method.getName())
                            && arguments != null
                            && arguments.length == 1
                            && Boolean.TRUE.equals(arguments[0])) {
                        autoCommitRestoreAttempted.set(true);
                        throw new SQLException("unexpected auto-commit restoration");
                    }
                    if ("close".equals(method.getName())
                            && (arguments == null || arguments.length == 0)) {
                        closeAttempted.set(true);
                        delegate.close();
                        throw new SQLException("simulated close failure");
                    }
                    try {
                        return method.invoke(delegate, arguments);
                    } catch (InvocationTargetException reflectionFailure) {
                        throw reflectionFailure.getCause();
                    }
                });
    }

    private InstructionGraphMutationV3.Request withWorkspaceEpoch(
            InstructionGraphMutationV3.Request request,
            long workspaceEpoch) {
        return new InstructionGraphMutationV3.Request(
                request.contractVersion(),
                request.mutationKind(),
                request.requestId(),
                request.baseGraphVersion(),
                request.graphRevision(),
                workspaceEpoch,
                request.ownerAssertion(),
                request.draggedInstructionId(),
                request.layoutRows(),
                request.instructionRelationPatches(),
                request.variableBindingPatches(),
                request.variableOwnerPatches());
    }

    private InstructionGraphMutationV3.Request request(
            Connection connection,
            InstructionGraphMutationV3.MutationKind kind,
            Integer draggedInstructionId,
            List<LayoutRow> layout,
            List<InstructionRelationPatch> relationPatches,
            List<VariableBindingPatch> bindingPatches,
            List<VariableOwnerPatch> ownerPatches)
            throws Exception {
        return request(
                connection,
                kind,
                draggedInstructionId,
                layout,
                relationPatches,
                bindingPatches,
                ownerPatches,
                currentVersion(connection));
    }

    private InstructionGraphMutationV3.Request request(
            Connection connection,
            InstructionGraphMutationV3.MutationKind kind,
            Integer draggedInstructionId,
            List<LayoutRow> layout,
            List<InstructionRelationPatch> relationPatches,
            List<VariableBindingPatch> bindingPatches,
            List<VariableOwnerPatch> ownerPatches,
            long baseVersion)
            throws Exception {
        return new InstructionGraphMutationV3.Request(
                InstructionGraphMutationV3.CONTRACT_VERSION,
                kind,
                "request-" + draggedInstructionId,
                baseVersion,
                revision(connection),
                WORKSPACE_EPOCH,
                new InstructionGraphMutationV3.OwnerAssertion(
                        InstructionGraphMutationV3.WorkspaceKind.BOT_JOB,
                        HOME_BANKING_ID,
                        BOT_JOB_ID),
                draggedInstructionId,
                layout,
                relationPatches,
                bindingPatches,
                ownerPatches);
    }

    private List<LayoutRow> defaultLayout() {
        return List.of(
                row(100, 10, 1, 1),
                row(101, 10, 1, 2),
                row(104, 20, 2, 1),
                row(103, 20, 2, 2),
                row(102, 20, 2, 3));
    }

    private List<LayoutRow> layoutWithLoopMovedAndDisconnected() {
        return List.of(
                row(100, 10, 1, 1),
                row(104, 20, 2, 1),
                row(103, 20, 2, 2),
                row(102, 20, 2, 3),
                row(101, 20, 2, 4));
    }

    private void insertDisconnectedGet(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "INSERT INTO instruction VALUES"
                            + "(105,5,20,4,'GET',NULL,NULL,NULL,'capture')");
        }
    }

    private InstructionGraphMutationV3.Request reactAuthoredBatchRequest(
            Connection connection,
            NullableId expectedDisconnectedBinding)
            throws Exception {
        return request(
                connection,
                InstructionGraphMutationV3.MutationKind.RELATIONSHIP_UPDATE,
                null,
                List.of(
                        row(100, 10, 1, 1),
                        row(101, 10, 1, 2),
                        row(104, 20, 2, 1),
                        row(103, 20, 2, 2),
                        row(102, 20, 2, 3),
                        row(105, 20, 2, 4)),
                List.of(
                        relationPatch(
                                101,
                                InstructionRelationKind.LOOP_ANCHOR,
                                PatchOperation.CLEAR,
                                relation(100, 10),
                                InstructionRelationState.disconnected()),
                        relationPatch(
                                103,
                                InstructionRelationKind.ELEMENT_TARGET,
                                PatchOperation.CLEAR,
                                relation(104, 20),
                                InstructionRelationState.disconnected()),
                        relationPatch(
                                102,
                                InstructionRelationKind.BLOCK_TARGET,
                                PatchOperation.SET,
                                relation(null, 10),
                                relation(null, 30)),
                        relationPatch(
                                105,
                                InstructionRelationKind.ELEMENT_TARGET,
                                PatchOperation.SET,
                                InstructionRelationState.disconnected(),
                                relation(104, 20))),
                List.of(
                        new VariableBindingPatch(
                                103,
                                PatchOperation.CLEAR,
                                NullableId.of(501),
                                NullableId.of(null)),
                        new VariableBindingPatch(
                                105,
                                PatchOperation.SET,
                                expectedDisconnectedBinding,
                                NullableId.of(502))),
                List.of());
    }

    private LayoutRow row(
            int instructionId,
            int blockId,
            int blockOrder,
            int instructionOrder) {
        return new LayoutRow(
                instructionId,
                blockId,
                blockOrder,
                instructionOrder);
    }

    private InstructionRelationPatch relationPatch(
            int instructionId,
            InstructionRelationKind kind,
            PatchOperation operation,
            InstructionRelationState expected,
            InstructionRelationState replacement) {
        return new InstructionRelationPatch(
                instructionId,
                kind,
                operation,
                expected,
                replacement);
    }

    private InstructionRelationState relation(
            Integer parentId,
            Integer parentBlockId) {
        return new InstructionRelationState(parentId, parentBlockId);
    }

    private String revision(Connection connection) throws Exception {
        List<InstructionLoad> instructions = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT id,block_id,instruction_order_number,actions,parent_id,"
                                + "parent_block_id,variable_id,operation FROM instruction"
                                + " WHERE bot_job_id=5 ORDER BY id")) {
            while (rows.next()) {
                InstructionLoad row = new InstructionLoad();
                row.setId(rows.getInt("id"));
                row.setBlockId(rows.getInt("block_id"));
                row.setInstructionOrderNumber(
                        rows.getInt("instruction_order_number"));
                row.setActions(rows.getString("actions"));
                row.setParentId(nullableInteger(rows, "parent_id"));
                row.setParentBlockId(nullableInteger(rows, "parent_block_id"));
                row.setVariableId(nullableInteger(rows, "variable_id"));
                row.setOperation(rows.getString("operation"));
                instructions.add(row);
            }
        }
        List<VariableLoadDTO> variables = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT id,producer_instruction_id"
                                + " AS instruction_id FROM bot_job_variable_definition"
                                + " WHERE bot_job_id=5 ORDER BY id")) {
            while (rows.next()) {
                variables.add(new VariableLoadDTO(
                        rows.getInt("id"),
                        null,
                        BOT_JOB_ID,
                        nullableInteger(rows, "instruction_id"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        0));
            }
        }
        return new InstructionGraphRevisionService().revision(instructions, variables);
    }

    private long currentVersion(Connection connection) throws Exception {
        return stateRepository
                .load(connection, OwnerKey.botJob(HOME_BANKING_ID, BOT_JOB_ID))
                .orElseThrow()
                .version();
    }

    private int blockCount(Connection connection) throws Exception {
        return integerValue(connection, "SELECT COUNT(*) FROM block WHERE bot_job_id=5");
    }

    private int instructionCount(Connection connection, int blockId)
            throws Exception {
        return integerValue(
                connection,
                "SELECT COUNT(*) FROM instruction WHERE bot_job_id=5 AND block_id=" + blockId);
    }

    private int integerValue(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet row = statement.executeQuery(sql)) {
            assertTrue(row.next());
            return row.getInt(1);
        }
    }

    private Integer nullableIntegerValue(Connection connection, String sql)
            throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet row = statement.executeQuery(sql)) {
            assertTrue(row.next());
            int value = row.getInt(1);
            return row.wasNull() ? null : value;
        }
    }

    private Integer nullableInteger(ResultSet rows, String column)
            throws Exception {
        int value = rows.getInt(column);
        return rows.wasNull() ? null : value;
    }
}

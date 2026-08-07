package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.db.InstructionGraphStateRepository;
import com.allinweb.ch.db.InstructionGraphStateRepository.OwnerKey;
import com.allinweb.ch.db.migrations.M20260729_InstructionGraphState;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.AuthenticatedBotJob;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.GraphSnapshot;
import com.allinweb.ch.facade.GridItemWebElementTypeUpdateTransaction.MutationRefusedException;
import com.allinweb.ch.facade.GridItemWebElementTypeUpdateTransaction.UpdateResult;
import com.allinweb.ch.model.GridItemWebElementTypeContracts;
import com.allinweb.ch.model.GridItemWebElementTypeContracts.Request;
import com.allinweb.ch.model.GridItemWebElementTypeContracts.WebElementType;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

class GridItemWebElementTypeUpdateTransactionTest {
    private static final int HOME_BANKING_ID = 2;
    private static final int BOT_JOB_ID = 5;
    private static final long WORKSPACE_EPOCH = 9L;
    private static final AuthenticatedBotJob OWNER =
            AuthenticatedBotJob.of(HOME_BANKING_ID, BOT_JOB_ID, WORKSPACE_EPOCH);

    @Test
    void usesTheSameAuthoritativeRevisionAsTheGraphMutationTransaction() throws Exception {
        try (Connection connection = database()) {
            GraphSnapshot before = new BotJobGraphMutationTransaction().inspect(connection, OWNER);
            Request request = request(
                    "type-input-output",
                    100,
                    before,
                    WebElementType.INPUT,
                    WebElementType.OUTPUT);

            UpdateResult result = new GridItemWebElementTypeUpdateTransaction()
                    .execute(connection, OWNER, request);
            GraphSnapshot after = new BotJobGraphMutationTransaction().inspect(connection, OWNER);

            assertTrue(result.changed());
            assertFalse(result.duplicate());
            assertEquals("O:User name", stringValue(
                    connection, "SELECT actions FROM instruction WHERE id=100"));
            assertEquals(before.graphVersion() + 1L, result.committedGraphVersion());
            assertEquals(after.graphVersion(), result.committedGraphVersion());
            assertEquals(after.graphRevision(), result.graphRevision());
            assertEquals(501, integerValue(
                    connection,
                    "SELECT variable_id FROM instruction_variable_slot"
                            + " WHERE instruction_id=101 AND slot='GET_WRITE'"));
            assertTrue(connection.getAutoCommit());
        }
    }

    @Test
    void normalizesLegacyOtherThroughTheCurrentClickExecutionSemantics() throws Exception {
        try (Connection connection = database()) {
            GraphSnapshot graph = new BotJobGraphMutationTransaction().inspect(connection, OWNER);

            new GridItemWebElementTypeUpdateTransaction().execute(
                    connection,
                    OWNER,
                    request(
                            "type-other-input",
                            102,
                            graph,
                            WebElementType.CLICK,
                            WebElementType.INPUT));

            assertEquals("I:Legacy target", stringValue(
                    connection, "SELECT actions FROM instruction WHERE id=102"));
        }
    }

    @Test
    void refusesCommandsAndLeavesTheGraphUnchanged() throws Exception {
        try (Connection connection = database()) {
            GraphSnapshot graph = new BotJobGraphMutationTransaction().inspect(connection, OWNER);
            Request request = request(
                    "type-command-refused",
                    101,
                    graph,
                    WebElementType.OUTPUT,
                    WebElementType.CLICK);

            MutationRefusedException refusal = assertThrows(
                    MutationRefusedException.class,
                    () -> new GridItemWebElementTypeUpdateTransaction()
                            .execute(connection, OWNER, request));

            assertEquals("WEB_ELEMENT_TYPE_NOT_ELIGIBLE", refusal.code());
            assertEquals("GET", stringValue(
                    connection, "SELECT actions FROM instruction WHERE id=101"));
            assertEquals(graph.graphVersion(), currentVersion(connection));
            assertTrue(connection.getAutoCommit());
        }
    }

    @Test
    void refusesAStaleRevisionBeforeWriting() throws Exception {
        try (Connection connection = database()) {
            GraphSnapshot graph = new BotJobGraphMutationTransaction().inspect(connection, OWNER);
            Request request = new Request(
                    GridItemWebElementTypeContracts.CONTRACT_VERSION,
                    "type-stale",
                    HOME_BANKING_ID,
                    BOT_JOB_ID,
                    100,
                    WORKSPACE_EPOCH,
                    graph.graphVersion(),
                    "f".repeat(64),
                    WebElementType.INPUT,
                    WebElementType.CLICK);

            MutationRefusedException refusal = assertThrows(
                    MutationRefusedException.class,
                    () -> new GridItemWebElementTypeUpdateTransaction()
                            .execute(connection, OWNER, request));

            assertEquals("WEB_ELEMENT_TYPE_GRAPH_REVISION_STALE", refusal.code());
            assertEquals("I:User name", stringValue(
                    connection, "SELECT actions FROM instruction WHERE id=100"));
            assertEquals(graph.graphVersion(), currentVersion(connection));
        }
    }

    @Test
    void refusesMismatchedOwnerWorkspaceVersionAndExpectedTypeBeforeWriting()
            throws Exception {
        try (Connection connection = database()) {
            GraphSnapshot graph = new BotJobGraphMutationTransaction().inspect(connection, OWNER);
            GridItemWebElementTypeUpdateTransaction transaction =
                    new GridItemWebElementTypeUpdateTransaction();

            Request ownerMismatch = new Request(
                    GridItemWebElementTypeContracts.CONTRACT_VERSION,
                    "type-owner",
                    3,
                    BOT_JOB_ID,
                    100,
                    WORKSPACE_EPOCH,
                    graph.graphVersion(),
                    graph.graphRevision(),
                    WebElementType.INPUT,
                    WebElementType.OUTPUT);
            Request workspaceMismatch = new Request(
                    GridItemWebElementTypeContracts.CONTRACT_VERSION,
                    "type-workspace",
                    HOME_BANKING_ID,
                    BOT_JOB_ID,
                    100,
                    WORKSPACE_EPOCH + 1L,
                    graph.graphVersion(),
                    graph.graphRevision(),
                    WebElementType.INPUT,
                    WebElementType.OUTPUT);
            Request versionMismatch = new Request(
                    GridItemWebElementTypeContracts.CONTRACT_VERSION,
                    "type-version",
                    HOME_BANKING_ID,
                    BOT_JOB_ID,
                    100,
                    WORKSPACE_EPOCH,
                    graph.graphVersion() + 1L,
                    graph.graphRevision(),
                    WebElementType.INPUT,
                    WebElementType.OUTPUT);
            Request expectedMismatch = request(
                    "type-expected",
                    100,
                    graph,
                    WebElementType.OUTPUT,
                    WebElementType.CLICK);

            assertEquals(
                    "WEB_ELEMENT_TYPE_OWNER_MISMATCH",
                    assertThrows(
                                    MutationRefusedException.class,
                                    () -> transaction.execute(connection, OWNER, ownerMismatch))
                            .code());
            assertEquals(
                    "WEB_ELEMENT_TYPE_WORKSPACE_CHANGED",
                    assertThrows(
                                    MutationRefusedException.class,
                                    () -> transaction.execute(connection, OWNER, workspaceMismatch))
                            .code());
            assertEquals(
                    "WEB_ELEMENT_TYPE_GRAPH_VERSION_STALE",
                    assertThrows(
                                    MutationRefusedException.class,
                                    () -> transaction.execute(connection, OWNER, versionMismatch))
                            .code());
            assertEquals(
                    "WEB_ELEMENT_TYPE_EXPECTED_STALE",
                    assertThrows(
                                    MutationRefusedException.class,
                                    () -> transaction.execute(connection, OWNER, expectedMismatch))
                            .code());
            assertEquals("I:User name", stringValue(
                    connection, "SELECT actions FROM instruction WHERE id=100"));
            assertEquals(graph.graphVersion(), currentVersion(connection));
            assertTrue(connection.getAutoCommit());
        }
    }

    private static Request request(
            String requestId,
            int instructionId,
            GraphSnapshot graph,
            WebElementType expected,
            WebElementType replacement) {
        return new Request(
                GridItemWebElementTypeContracts.CONTRACT_VERSION,
                requestId,
                HOME_BANKING_ID,
                BOT_JOB_ID,
                instructionId,
                WORKSPACE_EPOCH,
                graph.graphVersion(),
                graph.graphRevision(),
                expected,
                replacement);
    }

    private static Connection database() throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("CREATE TABLE bot_job("
                    + "id INTEGER PRIMARY KEY,home_banking_id INTEGER NOT NULL)");
            statement.execute("CREATE TABLE block("
                    + "id INTEGER PRIMARY KEY,bot_job_id INTEGER NOT NULL,"
                    + "block_order_number INTEGER NOT NULL)");
            statement.execute("CREATE TABLE instruction("
                    + "id INTEGER PRIMARY KEY,bot_job_id INTEGER NOT NULL,"
                    + "block_id INTEGER NOT NULL,instruction_order_number INTEGER NOT NULL,"
                    + "actions TEXT NOT NULL,name TEXT,parent_id INTEGER,parent_block_id INTEGER,"
                    + "operation TEXT)");
            statement.execute("CREATE TABLE instruction_variable_slot("
                    + "home_banking_id INTEGER NOT NULL,bot_job_id INTEGER NOT NULL,"
                    + "instruction_id INTEGER NOT NULL,slot TEXT NOT NULL,"
                    + "variable_id INTEGER NOT NULL)");
            statement.execute("CREATE TABLE bot_job_variable_definition("
                    + "id INTEGER PRIMARY KEY,home_banking_id INTEGER NOT NULL,"
                    + "bot_job_id INTEGER NOT NULL,producer_instruction_id INTEGER)");
            statement.executeUpdate("INSERT INTO bot_job VALUES (5,2)");
            statement.executeUpdate("INSERT INTO block VALUES (10,5,1)");
            statement.executeUpdate("INSERT INTO instruction VALUES"
                    + "(100,5,10,1,'I:User name','User name',NULL,NULL,'input'),"
                    + "(101,5,10,2,'GET','Get Value',100,10,'capture'),"
                    + "(102,5,10,3,'W:Legacy target','Legacy target',NULL,NULL,'click')");
            statement.executeUpdate(
                    "INSERT INTO bot_job_variable_definition VALUES (501,2,5,100)");
            statement.executeUpdate(
                    "INSERT INTO instruction_variable_slot VALUES (2,5,101,'GET_WRITE',501)");
        }
        new M20260729_InstructionGraphState().apply(connection, "TEXT");
        new InstructionGraphStateRepository()
                .loadOrCreate(connection, OwnerKey.botJob(HOME_BANKING_ID, BOT_JOB_ID));
        return connection;
    }

    private static long currentVersion(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT graph_version FROM instruction_graph_state"
                        + " WHERE workspace_kind='BOT_JOB'"
                        + " AND home_banking_id=? AND owner_id=?")) {
            statement.setInt(1, HOME_BANKING_ID);
            statement.setInt(2, BOT_JOB_ID);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getLong(1) : -1L;
            }
        }
    }

    private static String stringValue(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(sql)) {
            return rows.next() ? rows.getString(1) : null;
        }
    }

    private static int integerValue(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(sql)) {
            return rows.next() ? rows.getInt(1) : -1;
        }
    }
}

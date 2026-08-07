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
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GridItemWebElementTypeUpdateServiceTest {
    private static final int HOME_BANKING_ID = 2;
    private static final int BOT_JOB_ID = 5;
    private static final long WORKSPACE_EPOCH = 9L;

    @TempDir
    Path tempDir;

    @Test
    void replaysTheSameRequestWithoutASecondGraphWrite() throws Exception {
        String url = databaseUrl();
        GraphSnapshot graph;
        try (Connection connection = DriverManager.getConnection(url)) {
            initialize(connection);
            graph = new BotJobGraphMutationTransaction().inspect(
                    connection,
                    AuthenticatedBotJob.of(
                            HOME_BANKING_ID, BOT_JOB_ID, WORKSPACE_EPOCH));
        }

        GridItemWebElementTypeUpdateService service =
                new GridItemWebElementTypeUpdateService(
                        () -> DriverManager.getConnection(url),
                        new GridItemWebElementTypeUpdateTransaction());
        Request request = request(graph, WebElementType.INPUT, WebElementType.OUTPUT);

        UpdateResult first = service.update(request);
        UpdateResult replay = service.update(request);

        assertTrue(first.changed());
        assertFalse(first.duplicate());
        assertTrue(replay.duplicate());
        assertEquals(first.committedGraphVersion(), replay.committedGraphVersion());
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT graph_version FROM instruction_graph_state"
                                + " WHERE workspace_kind='BOT_JOB'"
                                + " AND home_banking_id=2 AND owner_id=5")) {
            assertTrue(rows.next());
            assertEquals(1L, rows.getLong(1));
        }
    }

    @Test
    void rejectsRequestIdReuseWithDifferentData() throws Exception {
        String url = databaseUrl();
        GraphSnapshot graph;
        try (Connection connection = DriverManager.getConnection(url)) {
            initialize(connection);
            graph = new BotJobGraphMutationTransaction().inspect(
                    connection,
                    AuthenticatedBotJob.of(
                            HOME_BANKING_ID, BOT_JOB_ID, WORKSPACE_EPOCH));
        }
        GridItemWebElementTypeUpdateService service =
                new GridItemWebElementTypeUpdateService(
                        () -> DriverManager.getConnection(url),
                        new GridItemWebElementTypeUpdateTransaction());
        service.update(request(graph, WebElementType.INPUT, WebElementType.OUTPUT));

        MutationRefusedException refusal = assertThrows(
                MutationRefusedException.class,
                () -> service.update(request(
                        graph, WebElementType.INPUT, WebElementType.CLICK)));

        assertEquals("WEB_ELEMENT_TYPE_REQUEST_ID_REUSED", refusal.code());
    }

    private static Request request(
            GraphSnapshot graph,
            WebElementType expected,
            WebElementType replacement) {
        return new Request(
                GridItemWebElementTypeContracts.CONTRACT_VERSION,
                "same-request",
                HOME_BANKING_ID,
                BOT_JOB_ID,
                100,
                WORKSPACE_EPOCH,
                graph.graphVersion(),
                graph.graphRevision(),
                expected,
                replacement);
    }

    private String databaseUrl() {
        return "jdbc:sqlite:" + tempDir.resolve("web-element-type.db").toAbsolutePath();
    }

    private static void initialize(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
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
                    + "instruction_id INTEGER NOT NULL,slot TEXT NOT NULL,variable_id INTEGER NOT NULL)");
            statement.execute("CREATE TABLE bot_job_variable_definition("
                    + "id INTEGER PRIMARY KEY,home_banking_id INTEGER NOT NULL,"
                    + "bot_job_id INTEGER NOT NULL,producer_instruction_id INTEGER)");
            statement.executeUpdate("INSERT INTO bot_job VALUES (5,2)");
            statement.executeUpdate("INSERT INTO block VALUES (10,5,1)");
            statement.executeUpdate(
                    "INSERT INTO instruction VALUES"
                            + "(100,5,10,1,'I:User name','User name',NULL,NULL,'input')");
        }
        new M20260729_InstructionGraphState().apply(connection, "TEXT");
        new InstructionGraphStateRepository()
                .loadOrCreate(connection, OwnerKey.botJob(HOME_BANKING_ID, BOT_JOB_ID));
    }
}

package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.allinweb.ch.facade.VariablesVariableDeleteTransaction.DeleteResult;
import com.allinweb.ch.facade.VariablesVariableDeleteTransaction.TransactionPhase;
import com.allinweb.ch.model.VariablesWorkspaceVariableDelete;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VariablesVariableDeleteTransactionTest {

    private static final int HOME_BANKING_ID = 2;
    private static final int BOT_JOB_ID = 5;
    private static final long WORKSPACE_EPOCH = 9L;

    private final InstructionGraphStateRepository stateRepository =
            new InstructionGraphStateRepository();
    private final AuthenticatedBotJob authenticatedOwner =
            AuthenticatedBotJob.of(
                    HOME_BANKING_ID, BOT_JOB_ID, WORKSPACE_EPOCH);

    @TempDir
    Path tempDir;

    @Test
    void deletesOneExactVariableAndClearsOnlyItsInstructionBindings()
            throws Exception {
        try (Connection connection = database()) {
            VariablesWorkspaceVariableDelete.Request request =
                    request(connection, VariablesWorkspaceVariableDelete.Mode.SINGLE, List.of(501));

            DeleteResult result =
                    new VariablesVariableDeleteTransaction()
                            .execute(connection, authenticatedOwner, request);

            assertEquals(1, result.deletedCount());
            assertEquals(2, result.clearedInstructionCount());
            assertEquals(List.of(501), result.variableIds());
            assertEquals(0L, result.previousGraphVersion());
            assertEquals(1L, result.committedGraphVersion());
            assertEquals(1L, currentVersion(connection));
            assertEquals(
                    0,
                    count(
                            connection,
                            "bot_job_variable_definition",
                            "id=501"));
            assertEquals(
                    1,
                    count(
                            connection,
                            "bot_job_variable_definition",
                            "id=502"));
            assertEquals(
                    0,
                    count(
                            connection,
                            "bot_job_runtime_variable_value",
                            "variable_id=501"));
            assertEquals(
                    1,
                    count(
                            connection,
                            "bot_job_runtime_variable_value",
                            "variable_id=502"));
            assertNull(value(
                    connection, "SELECT variable_id FROM instruction WHERE id=101"));
            assertNull(value(
                    connection, "SELECT variable_id FROM instruction WHERE id=102"));
            assertEquals(
                    502,
                    value(connection, "SELECT variable_id FROM instruction WHERE id=103"));

            assertEquals(
                    "capture-one",
                    value(connection, "SELECT operation FROM instruction WHERE id=101"));
            assertEquals(
                    100,
                    value(connection, "SELECT parent_id FROM instruction WHERE id=101"));
            assertEquals(
                    10,
                    value(connection, "SELECT parent_block_id FROM instruction WHERE id=101"));
            assertEquals(4, count(connection, "instruction", "bot_job_id=5"));
            assertEquals(2, count(connection, "block", "bot_job_id=5"));
            assertEquals(1, count(connection, "reference", "bot_job_id=5"));
            assertEquals(1, count(connection, "component_instruction", "home_banking_id=2"));
            assertEquals(1, count(connection, "component_variable", "home_banking_id=2"));
            assertTrue(connection.getAutoCommit());
        }
    }

    @Test
    void deletesAllOnlyWhenReactSubmitsTheCompleteCurrentCatalog()
            throws Exception {
        try (Connection connection = database()) {
            VariablesWorkspaceVariableDelete.Request request =
                    request(
                            connection,
                            VariablesWorkspaceVariableDelete.Mode.ALL,
                            List.of(501, 502));

            DeleteResult result =
                    new VariablesVariableDeleteTransaction()
                            .execute(connection, authenticatedOwner, request);

            assertEquals(2, result.deletedCount());
            assertEquals(3, result.clearedInstructionCount());
            assertEquals(
                    0,
                    count(
                            connection,
                            "bot_job_variable_definition",
                            "bot_job_id=5"));
            assertEquals(
                    0,
                    count(
                            connection,
                            "bot_job_runtime_variable_value",
                            "bot_job_id=5"));
            assertEquals(
                    0,
                    count(
                            connection,
                            "instruction",
                            "bot_job_id=5 AND variable_id IS NOT NULL"));
            assertEquals(4, count(connection, "instruction", "bot_job_id=5"));
        }
    }

    @Test
    void refusesIncompleteAllAndForeignVariableIdsWithoutWriting()
            throws Exception {
        try (Connection connection = database()) {
            VariablesWorkspaceVariableDelete.Request incomplete =
                    request(
                            connection,
                            VariablesWorkspaceVariableDelete.Mode.ALL,
                            List.of(501));
            MutationRefusedException incompleteFailure = assertThrows(
                    MutationRefusedException.class,
                    () -> new VariablesVariableDeleteTransaction()
                            .execute(connection, authenticatedOwner, incomplete));
            assertEquals(
                    "VARIABLE_DELETE_ALL_INCOMPLETE",
                    incompleteFailure.code());
            assertUnchanged(connection);

            VariablesWorkspaceVariableDelete.Request foreign =
                    request(
                            connection,
                            VariablesWorkspaceVariableDelete.Mode.SINGLE,
                            List.of(601));
            MutationRefusedException foreignFailure = assertThrows(
                    MutationRefusedException.class,
                    () -> new VariablesVariableDeleteTransaction()
                            .execute(connection, authenticatedOwner, foreign));
            assertEquals("VARIABLE_DELETE_NOT_OWNED", foreignFailure.code());
            assertUnchanged(connection);
        }
    }

    @Test
    void rejectsStaleRevisionAndVersionBeforeAnyWrite()
            throws Exception {
        try (Connection connection = database()) {
            VariablesWorkspaceVariableDelete.Request current =
                    request(
                            connection,
                            VariablesWorkspaceVariableDelete.Mode.SINGLE,
                            List.of(501));
            VariablesWorkspaceVariableDelete.Request staleRevision =
                    new VariablesWorkspaceVariableDelete.Request(
                            current.contractVersion(),
                            current.requestId(),
                            current.baseGraphVersion(),
                            "stale-revision",
                            current.workspaceEpoch(),
                            current.mode(),
                            current.variableIds());
            MutationRefusedException revisionFailure = assertThrows(
                    MutationRefusedException.class,
                    () -> new VariablesVariableDeleteTransaction()
                            .execute(connection, authenticatedOwner, staleRevision));
            assertEquals(
                    "VARIABLE_DELETE_GRAPH_REVISION_STALE",
                    revisionFailure.code());
            assertUnchanged(connection);

            VariablesWorkspaceVariableDelete.Request staleVersion =
                    new VariablesWorkspaceVariableDelete.Request(
                            current.contractVersion(),
                            current.requestId(),
                            current.baseGraphVersion() + 1L,
                            current.graphRevision(),
                            current.workspaceEpoch(),
                            current.mode(),
                            current.variableIds());
            MutationRefusedException versionFailure = assertThrows(
                    MutationRefusedException.class,
                    () -> new VariablesVariableDeleteTransaction()
                            .execute(connection, authenticatedOwner, staleVersion));
            assertEquals(
                    "VARIABLE_DELETE_GRAPH_VERSION_STALE",
                    versionFailure.code());
            assertUnchanged(connection);
        }
    }

    @Test
    void rollsBackBindingsVariablesAndVersionWhenAnyPhaseFails()
            throws Exception {
        for (TransactionPhase phase :
                List.of(
                        TransactionPhase.AFTER_BINDINGS_CLEARED,
                        TransactionPhase.AFTER_VARIABLES_DELETED,
                        TransactionPhase.AFTER_VERSION_ADVANCE,
                        TransactionPhase.AFTER_FINAL_VERIFICATION)) {
            try (Connection connection = database()) {
                VariablesWorkspaceVariableDelete.Request request =
                        request(
                                connection,
                                VariablesWorkspaceVariableDelete.Mode.SINGLE,
                                List.of(501));
                VariablesVariableDeleteTransaction transaction =
                        new VariablesVariableDeleteTransaction(
                                stateRepository,
                                new InstructionGraphRevisionService(),
                                reached -> {
                                    if (reached == phase) {
                                        throw new SQLException(
                                                "simulated " + phase);
                                    }
                                });

                assertThrows(
                        SQLException.class,
                        () -> transaction.execute(
                                connection, authenticatedOwner, request));

                assertUnchanged(connection);
                assertTrue(connection.getAutoCommit());
            }
        }
    }

    @Test
    void refusesWrongWorkspaceEpochAndOwnerWithoutWriting()
            throws Exception {
        try (Connection connection = database()) {
            VariablesWorkspaceVariableDelete.Request current =
                    request(
                            connection,
                            VariablesWorkspaceVariableDelete.Mode.SINGLE,
                            List.of(501));
            VariablesWorkspaceVariableDelete.Request wrongEpoch =
                    new VariablesWorkspaceVariableDelete.Request(
                            current.contractVersion(),
                            current.requestId(),
                            current.baseGraphVersion(),
                            current.graphRevision(),
                            current.workspaceEpoch() + 1L,
                            current.mode(),
                            current.variableIds());
            MutationRefusedException epochFailure = assertThrows(
                    MutationRefusedException.class,
                    () -> new VariablesVariableDeleteTransaction()
                            .execute(connection, authenticatedOwner, wrongEpoch));
            assertEquals("VARIABLE_DELETE_WORKSPACE_CHANGED", epochFailure.code());
            assertUnchanged(connection);

            AuthenticatedBotJob wrongOwner =
                    AuthenticatedBotJob.of(3, BOT_JOB_ID, WORKSPACE_EPOCH);
            MutationRefusedException ownerFailure = assertThrows(
                    MutationRefusedException.class,
                    () -> new VariablesVariableDeleteTransaction()
                            .execute(connection, wrongOwner, current));
            assertEquals("VARIABLE_DELETE_OWNER_MISMATCH", ownerFailure.code());
            assertUnchanged(connection);
        }
    }

    private VariablesWorkspaceVariableDelete.Request request(
            Connection connection,
            VariablesWorkspaceVariableDelete.Mode mode,
            List<Integer> ids)
            throws Exception {
        GraphSnapshot graph = new BotJobGraphMutationTransaction()
                .inspect(connection, authenticatedOwner);
        return new VariablesWorkspaceVariableDelete.Request(
                VariablesWorkspaceVariableDelete.CONTRACT_VERSION,
                "delete-" + mode + "-" + ids,
                graph.graphVersion(),
                graph.graphRevision(),
                WORKSPACE_EPOCH,
                mode,
                ids);
    }

    private Connection database() throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("CREATE TABLE home_banking("
                    + "id INTEGER PRIMARY KEY)");
            statement.execute("CREATE TABLE bot_job("
                    + "id INTEGER PRIMARY KEY,home_banking_id INTEGER NOT NULL)");
            statement.execute("CREATE TABLE block("
                    + "id INTEGER PRIMARY KEY,bot_job_id INTEGER NOT NULL,"
                    + "block_order_number INTEGER NOT NULL,name TEXT)");
            statement.execute("CREATE TABLE instruction("
                    + "id INTEGER PRIMARY KEY,bot_job_id INTEGER NOT NULL,"
                    + "block_id INTEGER NOT NULL,instruction_order_number INTEGER NOT NULL,"
                    + "actions TEXT,parent_id INTEGER,parent_block_id INTEGER,"
                    + "variable_id INTEGER,operation TEXT,name TEXT,xpath TEXT)");
            statement.execute("CREATE TABLE variable("
                    + "id INTEGER PRIMARY KEY,bot_job_id INTEGER NOT NULL,"
                    + "instruction_id INTEGER,type TEXT,name TEXT,value TEXT,"
                    + "local_format TEXT,delimiter TEXT)");
            statement.execute("CREATE TABLE reference("
                    + "id INTEGER PRIMARY KEY,instruction_id INTEGER,bot_job_id INTEGER,"
                    + "reference_type TEXT,value TEXT)");
            statement.execute("CREATE TABLE component_instruction("
                    + "id INTEGER PRIMARY KEY,home_banking_id INTEGER,variable_id INTEGER,"
                    + "operation TEXT)");
            statement.execute("CREATE TABLE component_variable("
                    + "id INTEGER PRIMARY KEY,home_banking_id INTEGER,instruction_id INTEGER,"
                    + "name TEXT)");

            statement.executeUpdate("INSERT INTO home_banking VALUES (2),(3)");
            statement.executeUpdate("INSERT INTO bot_job VALUES (5,2),(6,3)");
            statement.executeUpdate("INSERT INTO block VALUES"
                    + "(10,5,1,'First'),(20,5,2,'Second')");
            statement.executeUpdate("INSERT INTO instruction VALUES"
                    + "(100,5,10,1,'O',NULL,NULL,NULL,'read','Web Field','//field'),"
                    + "(101,5,10,2,'GET',100,10,501,'capture-one','Get',''),"
                    + "(102,5,10,3,'E',100,10,501,'write-one','Excel',''),"
                    + "(103,5,20,1,'CK',100,10,502,'check-two','Check','')");
            statement.executeUpdate("INSERT INTO variable("
                    + "id,bot_job_id,instruction_id,type,name,value) VALUES"
                    + "(501,5,100,'$String','First','$EMPTY'),"
                    + "(502,5,103,'#Numeric','Second','0'),"
                    + "(601,6,NULL,'$String','Foreign','$EMPTY')");
            statement.executeUpdate("INSERT INTO reference VALUES"
                    + "(700,101,5,'XPATH','//saved')");
            statement.executeUpdate("INSERT INTO component_instruction VALUES"
                    + "(800,2,501,'component-operation')");
            statement.executeUpdate("INSERT INTO component_variable VALUES"
                    + "(801,2,800,'component-variable')");
        }
        new M20260730_BotJobRuntimeVariables().apply(connection, "TEXT");
        new M20260729_InstructionGraphState().apply(connection, "TEXT");
        stateRepository.loadOrCreate(
                connection, OwnerKey.botJob(HOME_BANKING_ID, BOT_JOB_ID));
        return connection;
    }

    private void assertUnchanged(Connection connection) throws Exception {
        assertEquals(0L, currentVersion(connection));
        assertEquals(
                1,
                count(
                        connection,
                        "bot_job_variable_definition",
                        "id=501"));
        assertEquals(
                1,
                count(
                        connection,
                        "bot_job_variable_definition",
                        "id=502"));
        assertEquals(
                1,
                count(
                        connection,
                        "bot_job_runtime_variable_value",
                        "variable_id=501"));
        assertEquals(
                1,
                count(
                        connection,
                        "bot_job_runtime_variable_value",
                        "variable_id=502"));
        assertEquals(
                501,
                value(connection, "SELECT variable_id FROM instruction WHERE id=101"));
        assertEquals(
                501,
                value(connection, "SELECT variable_id FROM instruction WHERE id=102"));
        assertEquals(
                502,
                value(connection, "SELECT variable_id FROM instruction WHERE id=103"));
    }

    private long currentVersion(Connection connection) throws Exception {
        return stateRepository
                .load(connection, OwnerKey.botJob(HOME_BANKING_ID, BOT_JOB_ID))
                .orElseThrow()
                .version();
    }

    private int count(Connection connection, String table, String where)
            throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT COUNT(*) FROM " + table + " WHERE " + where)) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }

    private Object value(Connection connection, String sql) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet rows = statement.executeQuery()) {
            assertTrue(rows.next());
            return rows.getObject(1);
        }
    }
}

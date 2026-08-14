package com.allinweb.ch.facade.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.facade.execution.ExecutionPreflightSnapshot.Owner;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExecutionPreflightSnapshotRepositoryTest {

    @TempDir
    Path tempDirectory;

    @Test
    void loadsOnlyTheExactOwnerWithoutRepairingNullableRelationships() throws Exception {
        String url = database("owner-snapshot.db");
        bootstrap(url, true);
        ExecutionPreflightSnapshotRepository repository =
                new ExecutionPreflightSnapshotRepository(
                        () -> DriverManager.getConnection(url));

        ExecutionPreflightSnapshotRepository.LoadedSnapshot loaded =
                repository.load(new Owner(2, 5));

        assertEquals(new Owner(2, 5), loaded.snapshot().owner());
        assertEquals(2, loaded.snapshot().blocks().size());
        assertEquals(3, loaded.snapshot().instructions().size());
        assertEquals(1, loaded.snapshot().variables().size());
        assertTrue(loaded.graphVersion().isPresent());
        assertEquals(7L, loaded.graphVersion().getAsLong());

        ExecutionPreflightSnapshot.InstructionFact gotoRow =
                loaded.snapshot().instructions().stream()
                        .filter(row -> row.id() == 103)
                        .findFirst()
                        .orElseThrow();
        assertEquals("EXCEL GOTO", gotoRow.action());
        assertNull(gotoRow.parentBlockId(), "preflight must not silently normalize EXCEL GOTO");
        assertNull(
                loaded.snapshot().variables().get(0).ownerInstructionId(),
                "SQL NULL variable owners must remain NULL");
        assertFalse(
                loaded.snapshot().instructions().stream()
                        .anyMatch(row -> row.id() == 999),
                "another Bot Job must never leak into the snapshot");
    }

    @Test
    void refusesAHomeBankingAndBotJobOwnerMismatch() throws Exception {
        String url = database("owner-mismatch.db");
        bootstrap(url, true);
        ExecutionPreflightSnapshotRepository repository =
                new ExecutionPreflightSnapshotRepository(
                        () -> DriverManager.getConnection(url));

        SQLException error = assertThrows(
                SQLException.class,
                () -> repository.load(new Owner(3, 5)));

        assertTrue(error.getMessage().contains("not owned"));
    }

    @Test
    void keepsSnapshotAvailableWhenP5GraphStateIsNotInstalled() throws Exception {
        String url = database("without-graph-state.db");
        bootstrap(url, false);
        ExecutionPreflightSnapshotRepository repository =
                new ExecutionPreflightSnapshotRepository(
                        () -> DriverManager.getConnection(url));

        ExecutionPreflightSnapshotRepository.LoadedSnapshot loaded =
                repository.load(new Owner(2, 5));

        assertFalse(loaded.graphVersion().isPresent());
        assertEquals(3, loaded.snapshot().instructions().size());
    }

    @Test
    void prefersTheCurrentInstructionVariableSlotOverTheLegacyColumn() throws Exception {
        String url = database("current-variable-slot.db");
        bootstrap(url, true);
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TABLE instruction_variable_slot ("
                            + "home_banking_id INTEGER NOT NULL, bot_job_id INTEGER NOT NULL,"
                            + "instruction_id INTEGER NOT NULL, slot TEXT NOT NULL, variable_id INTEGER NOT NULL)");
            statement.execute(
                    "INSERT INTO instruction_variable_slot VALUES (2, 5, 102, 'GET_WRITE', 777)");
        }
        ExecutionPreflightSnapshotRepository repository =
                new ExecutionPreflightSnapshotRepository(
                        () -> DriverManager.getConnection(url));

        ExecutionPreflightSnapshot.InstructionFact getRow = repository.load(new Owner(2, 5))
                .snapshot()
                .instructions()
                .stream()
                .filter(row -> row.id() == 102)
                .findFirst()
                .orElseThrow();

        assertEquals(Integer.valueOf(777), getRow.variableId());
    }

    private String database(String name) {
        return "jdbc:sqlite:" + tempDirectory.resolve(name);
    }

    private void bootstrap(String url, boolean includeGraphState) throws Exception {
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TABLE bot_job (id INTEGER PRIMARY KEY, home_banking_id INTEGER NOT NULL)");
            statement.execute(
                    "CREATE TABLE block (id INTEGER PRIMARY KEY, bot_job_id INTEGER NOT NULL,"
                            + " block_order_number INTEGER NOT NULL, active BOOLEAN NOT NULL)");
            statement.execute(
                    "CREATE TABLE instruction (id INTEGER PRIMARY KEY, bot_job_id INTEGER NOT NULL,"
                            + " block_id INTEGER NOT NULL,"
                            + " instruction_order_number INTEGER NOT NULL, actions TEXT,"
                            + " tag_name TEXT, active BOOLEAN NOT NULL, parent_id INTEGER,"
                            + " parent_block_id INTEGER, variable_id INTEGER)");
            statement.execute(
                    "CREATE TABLE bot_job_variable_definition ("
                            + " home_banking_id INTEGER NOT NULL,"
                            + " bot_job_id INTEGER NOT NULL,"
                            + " id INTEGER NOT NULL,"
                            + " variable_type TEXT,"
                            + " name TEXT NOT NULL,"
                            + " configured_value TEXT,"
                            + " local_format TEXT,"
                            + " delimiter TEXT,"
                            + " producer_instruction_id INTEGER,"
                            + " created_at TEXT NOT NULL,"
                            + " updated_at TEXT NOT NULL,"
                            + " PRIMARY KEY (home_banking_id, bot_job_id, id))");
            statement.execute(
                    "CREATE TABLE bot_job_runtime_memory ("
                            + " home_banking_id INTEGER NOT NULL,"
                            + " bot_job_id INTEGER NOT NULL,"
                            + " runtime_revision INTEGER NOT NULL,"
                            + " reset_generation INTEGER NOT NULL,"
                            + " next_variable_id INTEGER NOT NULL,"
                            + " created_at TEXT NOT NULL,"
                            + " updated_at TEXT NOT NULL,"
                            + " PRIMARY KEY (home_banking_id, bot_job_id))");
            statement.execute(
                    "CREATE TABLE bot_job_runtime_variable_value ("
                            + " home_banking_id INTEGER NOT NULL,"
                            + " bot_job_id INTEGER NOT NULL,"
                            + " variable_id INTEGER NOT NULL,"
                            + " value_state TEXT NOT NULL,"
                            + " raw_value TEXT,"
                            + " void_reason TEXT,"
                            + " value_source TEXT NOT NULL,"
                            + " entry_revision INTEGER NOT NULL,"
                            + " last_execution_id TEXT,"
                            + " updated_at TEXT NOT NULL,"
                            + " PRIMARY KEY (home_banking_id, bot_job_id, variable_id))");
            if (includeGraphState) {
                statement.execute(
                        "CREATE TABLE instruction_graph_state (workspace_kind TEXT NOT NULL,"
                                + " home_banking_id INTEGER NOT NULL, owner_id INTEGER NOT NULL,"
                                + " graph_version BIGINT NOT NULL,"
                                + " PRIMARY KEY (workspace_kind, home_banking_id, owner_id))");
            }

            statement.execute("INSERT INTO bot_job VALUES (5, 2), (6, 3)");
            statement.execute(
                    "INSERT INTO block VALUES (10, 5, 1, 1), (20, 5, 2, 1), (90, 6, 1, 1)");
            statement.execute(
                    "INSERT INTO instruction VALUES"
                            + " (101, 5, 10, 1, 'FIELD', 'input', 1, NULL, NULL, NULL),"
                            + " (102, 5, 10, 2, 'GET', NULL, 1, 101, NULL, 501),"
                            + " (103, 5, 10, 3, 'EXCEL GOTO', NULL, 1, NULL, NULL, NULL),"
                            + " (999, 6, 90, 1, 'FIELD', 'button', 1, NULL, NULL, NULL)");
            statement.execute(
                    "INSERT INTO bot_job_variable_definition"
                            + " (home_banking_id, bot_job_id, id, variable_type, name,"
                            + " producer_instruction_id, created_at, updated_at) VALUES"
                            + " (2, 5, 501, '$String', 'Variable 501', NULL,"
                            + " '2026-07-30T00:00:00Z', '2026-07-30T00:00:00Z'),"
                            + " (3, 6, 999, '$String', 'Variable 999', 999,"
                            + " '2026-07-30T00:00:00Z', '2026-07-30T00:00:00Z')");
            statement.execute(
                    "INSERT INTO bot_job_runtime_memory VALUES"
                            + " (2, 5, 0, 0, 502, '2026-07-30T00:00:00Z',"
                            + " '2026-07-30T00:00:00Z'),"
                            + " (3, 6, 0, 0, 1000, '2026-07-30T00:00:00Z',"
                            + " '2026-07-30T00:00:00Z')");
            statement.execute(
                    "INSERT INTO bot_job_runtime_variable_value VALUES"
                            + " (2, 5, 501, 'VOID', NULL, 'NO_PRODUCER_YET',"
                            + " 'SYSTEM', 0, NULL, '2026-07-30T00:00:00Z'),"
                            + " (3, 6, 999, 'VOID', NULL, 'NO_PRODUCER_YET',"
                            + " 'SYSTEM', 0, NULL, '2026-07-30T00:00:00Z')");
            if (includeGraphState) {
                statement.execute(
                        "INSERT INTO instruction_graph_state VALUES ('BOT_JOB', 2, 5, 7)");
            }
        }
    }
}

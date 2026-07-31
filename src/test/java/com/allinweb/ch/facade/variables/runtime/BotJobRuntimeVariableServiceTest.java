package com.allinweb.ch.facade.variables.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.db.migrations.M20260730_BotJobRuntimeVariables;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.DefinitionDraft;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.MutationResult;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.MutationStatus;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.OwnerKey;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.RuntimeValue;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.Snapshot;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.ValueSource;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.ValueState;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.VoidReason;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BotJobRuntimeVariableServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void rawTextAndEmptyValueRemainExactAcrossServiceAndConnectionRestart()
            throws Exception {
        String url = bootstrap("runtime-persistence.db");
        OwnerKey owner = new OwnerKey(2, 5);
        long variableId;

        try (Connection connection = DriverManager.getConnection(url)) {
            BotJobRuntimeVariableService service = new BotJobRuntimeVariableService();
            Snapshot empty = service.hydrate(connection, owner);
            MutationResult created = service.createDefinition(
                    connection,
                    owner,
                    DefinitionDraft.voidDefinition("amount"),
                    empty.memory().runtimeRevision());
            assertTrue(created.applied());
            variableId = created.definition().id();
            assertEquals(ValueState.VOID, created.value().state());

            MutationResult emptyValue = service.setValue(
                    connection,
                    owner,
                    variableId,
                    "",
                    ValueSource.MANUAL,
                    null,
                    0L);
            assertTrue(emptyValue.applied());
            assertEquals(ValueState.VALUE, emptyValue.value().state());
            assertEquals("", emptyValue.value().rawValue());

            MutationResult localized = service.setValue(
                    connection,
                    owner,
                    variableId,
                    " 1.234,56 CHF ",
                    ValueSource.EXECUTION,
                    77L,
                    1L);
            assertTrue(localized.applied());
            assertEquals(" 1.234,56 CHF ", localized.value().rawValue());
        }

        try (Connection connection = DriverManager.getConnection(url)) {
            Snapshot rehydrated =
                    new BotJobRuntimeVariableService().hydrate(connection, owner);
            RuntimeValue value = rehydrated.values().get(0);
            assertEquals(ValueState.VALUE, value.state());
            assertEquals(" 1.234,56 CHF ", value.rawValue());
            assertEquals(77L, value.lastExecutionId());
        }
    }

    @Test
    void clearAllIsAtomicPreservesDefinitionsAndAdvancesResetGeneration()
            throws Exception {
        String url = bootstrap("clear-all.db");
        OwnerKey owner = new OwnerKey(2, 5);
        try (Connection connection = DriverManager.getConnection(url)) {
            BotJobRuntimeVariableService service = new BotJobRuntimeVariableService();
            Snapshot start = service.hydrate(connection, owner);
            MutationResult first = service.createDefinition(
                    connection,
                    owner,
                    new DefinitionDraft(
                            "$String",
                            "empty",
                            null,
                            null,
                            null,
                            null,
                            ValueState.VALUE,
                            ""),
                    start.memory().runtimeRevision());
            MutationResult second = service.createDefinition(
                    connection,
                    owner,
                    DefinitionDraft.voidDefinition("currency"),
                    first.snapshot().memory().runtimeRevision());
            service.setValue(
                    connection,
                    owner,
                    second.definition().id(),
                    "CHF 1'234.50",
                    ValueSource.MANUAL,
                    null,
                    0L);
            Snapshot beforeClear = service.hydrate(connection, owner);

            MutationResult cleared = service.clearAll(
                    connection,
                    owner,
                    beforeClear.memory().runtimeRevision(),
                    VoidReason.CLIENT_RESET,
                    ValueSource.RESET,
                    88L);

            assertTrue(cleared.applied());
            assertEquals(2, cleared.snapshot().definitions().size());
            assertEquals(2, cleared.snapshot().values().size());
            assertEquals(
                    beforeClear.memory().runtimeRevision() + 1L,
                    cleared.snapshot().memory().runtimeRevision());
            assertEquals(1L, cleared.snapshot().memory().resetGeneration());
            cleared.snapshot().values().forEach(value -> {
                assertEquals(ValueState.VOID, value.state());
                assertNull(value.rawValue());
                assertEquals(VoidReason.CLIENT_RESET, value.voidReason());
            });
        }
    }

    @Test
    void staleEntryRevisionAndWrongOwnerCannotOverwriteCurrentValue()
            throws Exception {
        String url = bootstrap("owner-cas.db");
        OwnerKey owner = new OwnerKey(2, 5);
        try (Connection connection = DriverManager.getConnection(url)) {
            BotJobRuntimeVariableService service = new BotJobRuntimeVariableService();
            MutationResult created = service.createDefinition(
                    connection,
                    owner,
                    DefinitionDraft.voidDefinition("user"),
                    0L);
            long id = created.definition().id();
            assertTrue(service.setValue(
                            connection,
                            owner,
                            id,
                            "first",
                            ValueSource.MANUAL,
                            null,
                            0L)
                    .applied());

            MutationResult stale = service.setValue(
                    connection,
                    owner,
                    id,
                    "must not win",
                    ValueSource.MANUAL,
                    null,
                    0L);
            assertEquals(MutationStatus.STALE_ENTRY_REVISION, stale.status());
            assertEquals(
                    "first",
                    service.hydrate(connection, owner).values().get(0).rawValue());

            MutationResult wrongOwner = service.setValue(
                    connection,
                    new OwnerKey(3, 5),
                    id,
                    "wrong owner",
                    ValueSource.MANUAL,
                    null,
                    1L);
            assertEquals(MutationStatus.OWNER_NOT_FOUND, wrongOwner.status());
        }
    }

    @Test
    void deletingDefinitionDetachesInstructionAndDeletesRuntimeValue()
            throws Exception {
        String url = bootstrap("delete-definition.db");
        OwnerKey owner = new OwnerKey(2, 5);
        try (Connection connection = DriverManager.getConnection(url)) {
            BotJobRuntimeVariableService service = new BotJobRuntimeVariableService();
            MutationResult created = service.createDefinition(
                    connection,
                    owner,
                    DefinitionDraft.voidDefinition("temporary"),
                    0L);
            long id = created.definition().id();
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "INSERT INTO instruction(id,bot_job_id,variable_id)"
                                + " VALUES (100,5," + id + ")");
            }

            MutationResult deleted = service.deleteDefinitions(
                    connection,
                    owner,
                    java.util.List.of(id),
                    created.snapshot().memory().runtimeRevision());
            assertTrue(deleted.applied());
            assertTrue(deleted.snapshot().definitions().isEmpty());
            assertTrue(deleted.snapshot().values().isEmpty());
            try (Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery(
                            "SELECT variable_id FROM instruction WHERE id=100")) {
                rows.next();
                assertNull(rows.getObject(1));
            }
        }
    }

    private String bootstrap(String name) throws Exception {
        String url = "jdbc:sqlite:" + tempDir.resolve(name);
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("CREATE TABLE home_banking (id INTEGER PRIMARY KEY)");
            statement.execute(
                    "CREATE TABLE bot_job (id INTEGER PRIMARY KEY, home_banking_id INTEGER)");
            statement.execute(
                    "CREATE TABLE instruction (id INTEGER PRIMARY KEY,"
                            + " bot_job_id INTEGER, variable_id INTEGER)");
            statement.execute(
                    "CREATE TABLE variable (id INTEGER PRIMARY KEY, type TEXT, name TEXT,"
                            + " value TEXT, local_format TEXT, delimiter TEXT,"
                            + " instruction_id INTEGER, bot_job_id INTEGER)");
            statement.executeUpdate("INSERT INTO home_banking(id) VALUES (2)");
            statement.executeUpdate("INSERT INTO home_banking(id) VALUES (3)");
            statement.executeUpdate(
                    "INSERT INTO bot_job(id,home_banking_id) VALUES (5,2)");
            new M20260730_BotJobRuntimeVariables().apply(connection, "TEXT");
        }
        return url;
    }
}

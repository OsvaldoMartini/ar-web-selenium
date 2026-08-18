package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.db.migrations.M20260730_BotJobRuntimeVariables;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * RAW_FACTS_V1 contract tests. Java no longer classifies variable commands or emits
 * edges/diagnostics/summary — that semantic layer lives in React (variablesGraph.ts,
 * covered by variablesGraph.test.ts). Java's contract here is: raw rows, owner-join
 * evidence, strict Bot Job scoping, a stable revision, and safe SQL failures.
 */
class VariableRelationshipServiceTest {
    private Connection anchor;
    private String databaseUrl;
    private VariableRelationshipService service;

    @BeforeEach
    void setUp() throws Exception {
        databaseUrl = "jdbc:sqlite:file:variables-relationship-"
                + UUID.randomUUID() + "?mode=memory&cache=shared";
        anchor = DriverManager.getConnection(databaseUrl);
        initialize(anchor);
        service = new VariableRelationshipService(
                () -> DriverManager.getConnection(databaseUrl));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (anchor != null) anchor.close();
    }

    @Test
    void emitsRawFactsWithoutJavaSideClassification() throws Exception {
        execute(
                "INSERT INTO block VALUES (10,5,1,'Login',1)",
                "INSERT INTO block VALUES (11,5,2,'Checks',1)",
                "INSERT INTO instruction VALUES"
                        + " (100,5,10,1,'Amount','CLICK','','input',1,NULL,NULL,1,1),"
                        + " (101,5,10,2,'Reference','CLICK','','input',NULL,NULL,NULL,NULL,1),"
                        + " (110,5,10,3,'Get amount','GET','Amount','',1,100,10,1,1),"
                        + " (113,5,11,3,'PDF amount','PDF CHECK','Amount:$Value','',1,100,10,1,1)",
                "INSERT INTO variable VALUES"
                        + " (1,5,100,'$String','Same name','$EMPTY','',''),"
                        + " (2,5,101,'$String','Same name','configured','','')");
        migrateVariables();

        JsonObject response = service.load(5);

        assertTrue(response.get("ok").getAsBoolean());
        assertEquals("RAW_FACTS_V1", response.get("graphKind").getAsString());
        assertEquals(2, response.getAsJsonArray("blocks").size());
        assertEquals(2, response.getAsJsonArray("rawVariables").size());
        // Every command is present, including the disconnected row with no variable_id.
        assertEquals(4, response.getAsJsonArray("rawCommands").size());
        assertTrue(rawCommand(response, 101).get("variableId").isJsonNull());

        JsonObject declaration = rawVariable(response, 1);
        assertEquals(100, declaration.get("ownerInstructionId").getAsInt());
        assertEquals(100, declaration.get("resolvedOwnerId").getAsInt());
        assertEquals(10, declaration.get("ownerBlockId").getAsInt());
        assertEquals(10, declaration.get("resolvedOwnerBlockId").getAsInt());
        assertEquals("$EMPTY", declaration.get("configuredValue").getAsString());
        assertEquals("input", rawCommand(response, 100).get("tagName").getAsString());

        JsonObject pdfCheck = rawCommand(response, 113);
        // Actions stay RAW — canonicalization and roles are React's job.
        assertEquals("PDF CHECK", pdfCheck.get("action").getAsString());
        assertEquals(11, pdfCheck.get("resolvedBlockId").getAsInt());
        assertFalse(pdfCheck.has("role"));

        assertFalse(response.has("summary"));
        assertFalse(response.has("variables"));
        assertFalse(response.has("edges"));
        assertFalse(response.has("diagnostics"));
    }

    @Test
    void scopesJoinsToTheRequestedBotJobAndKeepsMissingJoinsVisible() throws Exception {
        execute(
                "INSERT INTO block VALUES (10,5,1,'Job 5',1)",
                "INSERT INTO block VALUES (20,6,1,'Job 6 secret',1)",
                "INSERT INTO instruction VALUES"
                        + " (100,5,10,1,'Local owner','CLICK','','input',NULL,NULL,NULL,NULL,1),"
                        + " (200,6,20,1,'Foreign owner secret','CLICK','','input',NULL,NULL,NULL,NULL,1),"
                        + " (201,6,20,2,'Foreign command secret','GET','','',1,200,20,1,1),"
                        + " (120,5,10,2,'Dangling local','GET','','',999,NULL,NULL,1,1),"
                        + " (122,5,999,4,'Command without block','GET','','',2,100,10,1,1)",
                "INSERT INTO variable VALUES"
                        + " (1,5,200,'$String','Cross owner','$EMPTY','',''),"
                        + " (2,5,100,'$String','Local','$EMPTY','','')");
        migrateVariables();

        JsonObject response = service.load(5);

        assertTrue(response.get("ok").getAsBoolean());
        // The cross-job owner join must not resolve: raw FK stays, resolved evidence is null.
        JsonObject crossOwner = rawVariable(response, 1);
        assertEquals(200, crossOwner.get("ownerInstructionId").getAsInt());
        assertTrue(crossOwner.get("resolvedOwnerId").isJsonNull());
        // A command whose block does not exist keeps a null resolvedBlockId.
        assertTrue(rawCommand(response, 122).get("resolvedBlockId").isJsonNull());
        // The dangling variable link stays visible for React to diagnose.
        assertEquals(999, rawCommand(response, 120).get("variableId").getAsInt());
        String serialized = response.toString();
        assertFalse(serialized.contains("Foreign owner secret"));
        assertFalse(serialized.contains("Foreign command secret"));
    }

    @Test
    void producesStableSixtyFourCharacterRevisionAndChangesOnSemanticUpdate()
            throws Exception {
        execute(
                "INSERT INTO block VALUES (10,5,1,'Only',1)",
                "INSERT INTO instruction VALUES"
                        + " (100,5,10,1,'Owner','CLICK','','input',NULL,NULL,NULL,NULL,1)",
                "INSERT INTO variable VALUES"
                        + " (1,5,100,'$String','Value','$EMPTY','','')");
        migrateVariables();

        String first = service.load(5).get("graphRevision").getAsString();
        String repeated = service.load(5).get("graphRevision").getAsString();
        execute("UPDATE bot_job_variable_definition"
                + " SET configured_value='changed' WHERE id=1");
        String changed = service.load(5).get("graphRevision").getAsString();

        assertEquals(64, first.length());
        assertTrue(first.matches("[0-9a-f]{64}"));
        assertEquals(first, repeated);
        assertNotEquals(first, changed);

        JsonObject empty = service.load(99);
        assertTrue(empty.get("ok").getAsBoolean());
        assertEquals(0, empty.getAsJsonArray("rawVariables").size());
        assertEquals(0, empty.getAsJsonArray("rawCommands").size());
    }

    @Test
    void sqlFailureDoesNotExposeDatabaseDetailsToTheClient() throws Exception {
        migrateVariables();
        execute("DROP TABLE bot_job_variable_definition");

        JsonObject failed = service.load(5);

        assertFalse(failed.get("ok").getAsBoolean());
        assertTrue(failed.get("preserveSnapshot").getAsBoolean());
        assertEquals(
                "Variable relationships could not be loaded.",
                failed.get("error").getAsString());
        assertFalse(failed.toString().contains("no such table"));
    }

    private void initialize(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE home_banking (id INTEGER PRIMARY KEY)");
            statement.execute("CREATE TABLE bot_job ("
                    + "id INTEGER PRIMARY KEY, home_banking_id INTEGER)");
            statement.execute("CREATE TABLE block ("
                    + "id INTEGER PRIMARY KEY, bot_job_id INTEGER,"
                    + "block_order_number INTEGER, name TEXT, active INTEGER)");
            statement.execute("CREATE TABLE instruction ("
                    + "id INTEGER PRIMARY KEY, bot_job_id INTEGER, block_id INTEGER,"
                    + "instruction_order_number INTEGER, name TEXT, actions TEXT,"
                    + "operation TEXT, tag_name TEXT, variable_id INTEGER, parent_id INTEGER,"
                    + "parent_block_id INTEGER, executed INTEGER, active INTEGER)");
            statement.execute("CREATE TABLE variable ("
                    + "id INTEGER PRIMARY KEY, bot_job_id INTEGER, instruction_id INTEGER,"
                    + "type TEXT, name TEXT, value TEXT, local_format TEXT, delimiter TEXT)");
            statement.execute("INSERT INTO home_banking(id) VALUES(2),(3)");
            statement.execute(
                    "INSERT INTO bot_job(id,home_banking_id) VALUES(5,2),(6,3)");
        }
    }

    private void execute(String... sql) throws Exception {
        try (Statement statement = anchor.createStatement()) {
            for (String command : sql) statement.execute(command);
        }
    }

    private void migrateVariables() throws Exception {
        new M20260730_BotJobRuntimeVariables().apply(anchor, "TEXT");
        execute("ALTER TABLE instruction ADD COLUMN on_hold_seconds INTEGER");
    }

    private JsonObject rawVariable(JsonObject response, int id) {
        return findById(response.getAsJsonArray("rawVariables"), "id", id);
    }

    private JsonObject rawCommand(JsonObject response, int id) {
        return findById(response.getAsJsonArray("rawCommands"), "instructionId", id);
    }

    private JsonObject findById(JsonArray rows, String key, int id) {
        for (var item : rows) {
            JsonObject row = item.getAsJsonObject();
            if (row.get(key).getAsInt() == id) return row;
        }
        throw new AssertionError("Missing row " + key + "=" + id);
    }
}

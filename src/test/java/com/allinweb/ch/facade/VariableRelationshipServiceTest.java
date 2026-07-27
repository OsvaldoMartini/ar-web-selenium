package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    void loadsDistinctDeclarationsAndCanonicalCommandRoles() throws Exception {
        execute(
                "INSERT INTO block VALUES (10,5,1,'Login',1)",
                "INSERT INTO block VALUES (11,5,2,'Checks',1)",
                "INSERT INTO instruction VALUES"
                        + " (100,5,10,1,'Amount','CLICK','',1,NULL,NULL,1,1),"
                        + " (101,5,10,2,'Reference','CLICK','',NULL,NULL,NULL,NULL,1),"
                        + " (110,5,10,3,'Get amount','GET','Amount',1,100,10,1,1),"
                        + " (111,5,11,1,'Extract amount','E','Amount:$Value',1,100,10,1,1),"
                        + " (112,5,11,2,'Check amount','CK','Amount:$Value:=:12',1,100,10,1,1),"
                        + " (113,5,11,3,'PDF amount','PDF CHECK','Amount:$Value',1,100,10,1,1),"
                        + " (114,5,11,4,'CSV amount','CSV CHECK','Amount:$Value',1,100,10,1,1),"
                        + " (115,5,11,5,'Literal amount','SET','Amount:12',1,100,10,1,1),"
                        + " (116,5,11,6,'Stale link','C','',1,NULL,NULL,1,1)",
                "INSERT INTO variable VALUES"
                        + " (1,5,100,'$String','Same name','$EMPTY','',''),"
                        + " (2,5,101,'$String','Same name','configured','','')");

        JsonObject response = service.load(5);

        assertTrue(response.get("ok").getAsBoolean());
        assertEquals(2, response.getAsJsonArray("variables").size());
        JsonObject first = variable(response, 1);
        JsonObject second = variable(response, 2);
        assertEquals("Same name", first.get("name").getAsString());
        assertEquals("Same name", second.get("name").getAsString());
        assertEquals(7, first.getAsJsonArray("commands").size());
        assertEquals(0, second.getAsJsonArray("commands").size());
        assertFalse(first.get("unused").getAsBoolean());
        assertTrue(second.get("unused").getAsBoolean());
        assertEquals("PRODUCER", command(first, 110).get("role").getAsString());
        assertEquals("CONSUMER", command(first, 111).get("role").getAsString());
        assertEquals("CONSUMER", command(first, 112).get("role").getAsString());
        assertEquals("CONSUMER", command(first, 113).get("role").getAsString());
        assertEquals("CONSUMER", command(first, 114).get("role").getAsString());
        assertEquals(
                "LITERAL_ASSIGNMENT",
                command(first, 115).get("role").getAsString());
        assertEquals("INVALID_LINK", command(first, 116).get("role").getAsString());
        assertEquals("$EMPTY", first.get("configuredValue").getAsString());
        assertFalse(first.has("currentValue"));

        JsonObject summary = response.getAsJsonObject("summary");
        assertEquals(1, summary.get("producerCount").getAsInt());
        assertEquals(4, summary.get("consumerCount").getAsInt());
        assertEquals(1, summary.get("literalAssignmentCount").getAsInt());
        assertEquals(1, summary.get("unusedCount").getAsInt());
        assertTrue(hasDiagnostic(first.getAsJsonArray("diagnostics"), "NON_VARIABLE_ACTION_LINK"));
        assertTrue(hasEdge(response.getAsJsonArray("edges"), "DECLARES"));
        assertTrue(hasEdge(response.getAsJsonArray("edges"), "WRITES"));
        assertTrue(hasEdge(response.getAsJsonArray("edges"), "READS"));
        assertTrue(hasEdge(response.getAsJsonArray("edges"), "ASSIGNS_LITERAL"));
    }

    @Test
    void scopesJoinsAndKeepsMissingRelationshipsVisible() throws Exception {
        execute(
                "INSERT INTO block VALUES (10,5,1,'Job 5',1)",
                "INSERT INTO block VALUES (20,6,1,'Job 6 secret',1)",
                "INSERT INTO instruction VALUES"
                        + " (100,5,10,1,'Local owner','CLICK','',NULL,NULL,NULL,NULL,1),"
                         + " (200,6,20,1,'Foreign owner secret','CLICK','',NULL,NULL,NULL,NULL,1),"
                         + " (201,6,20,2,'Foreign command secret','GET','',1,200,20,1,1),"
                         + " (120,5,10,2,'Dangling local','GET','',999,NULL,NULL,1,1),"
                         + " (121,5,999,3,'Owner without block','CLICK','',NULL,NULL,NULL,NULL,1),"
                         + " (122,5,999,4,'Command without block','GET','',2,100,10,1,1)",
                 "INSERT INTO variable VALUES"
                         + " (1,5,200,'$String','Cross owner','$EMPTY','',''),"
                         + " (2,5,100,'$String','Local','$EMPTY','',''),"
                         + " (3,5,121,'$String','Missing block','$EMPTY','','')");

        JsonObject response = service.load(5);

        assertTrue(response.get("ok").getAsBoolean());
        assertTrue(variable(response, 1).get("owner").isJsonNull());
        assertTrue(hasDiagnostic(
                variable(response, 1).getAsJsonArray("diagnostics"), "MISSING_OWNER"));
        assertTrue(hasDiagnostic(
                response.getAsJsonArray("diagnostics"), "DANGLING_VARIABLE_LINK"));
        assertTrue(hasDiagnostic(
                variable(response, 3).getAsJsonArray("diagnostics"), "OWNER_BLOCK_MISMATCH"));
        assertTrue(hasDiagnostic(
                variable(response, 2).getAsJsonArray("diagnostics"), "COMMAND_BLOCK_MISMATCH"));
        String serialized = response.toString();
        assertFalse(serialized.contains("Foreign owner secret"));
        assertFalse(serialized.contains("Foreign command secret"));
    }

    @Test
    void diagnosesOrderingAndProducerIntegrity() throws Exception {
        execute(
                "INSERT INTO block VALUES (10,5,1,'First',1)",
                "INSERT INTO block VALUES (11,5,2,'Second',0)",
                "INSERT INTO instruction VALUES"
                        + " (100,5,10,1,'Owner A','CLICK','',NULL,NULL,NULL,NULL,1),"
                         + " (101,5,10,2,'Owner B','CLICK','',NULL,NULL,NULL,NULL,1),"
                         + " (110,5,10,3,'Early consumer','E','',1,100,10,1,1),"
                         + " (111,5,11,1,'Producer one','GET','',1,100,10,1,0),"
                         + " (112,5,10,4,'Producer two','GET','',1,100,10,1,1),"
                         + " (114,5,10,5,'Producer three','GET','',1,101,10,1,1),"
                         + " (113,5,10,6,'No producer consumer','CK','',2,101,10,1,1)",
                "INSERT INTO variable VALUES"
                        + " (1,5,100,'$String','Ordered','$EMPTY','',''),"
                        + " (2,5,101,'$String','Missing producer','$EMPTY','','')");

        JsonObject response = service.load(5);
        JsonObject ordered = variable(response, 1);
        JsonObject missing = variable(response, 2);

        assertTrue(hasDiagnostic(
                ordered.getAsJsonArray("diagnostics"), "MULTIPLE_PRODUCERS"));
        assertTrue(hasDiagnostic(
                ordered.getAsJsonArray("diagnostics"), "CONSUMER_BEFORE_PRODUCER"));
        assertTrue(hasDiagnostic(
                missing.getAsJsonArray("diagnostics"), "MISSING_PRODUCER"));
        assertTrue(hasDiagnostic(
                ordered.getAsJsonArray("diagnostics"), "PRODUCER_OWNER_MISMATCH"));
        assertFalse(command(ordered, 111).get("active").getAsBoolean());
        assertFalse(command(ordered, 111).get("blockActive").getAsBoolean());
        assertFalse(command(ordered, 111).get("effectiveActive").getAsBoolean());
    }

    @Test
    void producesStableSixtyFourCharacterRevisionAndChangesOnSemanticUpdate()
            throws Exception {
        execute(
                "INSERT INTO block VALUES (10,5,1,'Only',1)",
                "INSERT INTO instruction VALUES"
                        + " (100,5,10,1,'Owner','CLICK','',NULL,NULL,NULL,NULL,1)",
                "INSERT INTO variable VALUES"
                        + " (1,5,100,'$String','Value','$EMPTY','','')");

        String first = service.load(5).get("graphRevision").getAsString();
        String repeated = service.load(5).get("graphRevision").getAsString();
        execute("UPDATE variable SET value='changed' WHERE id=1");
        String changed = service.load(5).get("graphRevision").getAsString();

        assertEquals(64, first.length());
        assertTrue(first.matches("[0-9a-f]{64}"));
        assertEquals(first, repeated);
        assertNotEquals(first, changed);

        JsonObject empty = service.load(99);
        assertEquals(0, empty.getAsJsonObject("summary").get("variableCount").getAsInt());
        assertEquals(0, empty.getAsJsonArray("variables").size());
    }

    @Test
    void sqlFailureDoesNotExposeDatabaseDetailsToTheClient() throws Exception {
        execute("DROP TABLE variable");

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
            statement.execute("CREATE TABLE block ("
                    + "id INTEGER PRIMARY KEY, bot_job_id INTEGER,"
                    + "block_order_number INTEGER, name TEXT, active INTEGER)");
            statement.execute("CREATE TABLE instruction ("
                    + "id INTEGER PRIMARY KEY, bot_job_id INTEGER, block_id INTEGER,"
                     + "instruction_order_number INTEGER, name TEXT, actions TEXT,"
                     + "operation TEXT, variable_id INTEGER, parent_id INTEGER,"
                     + "parent_block_id INTEGER, executed INTEGER, active INTEGER)");
            statement.execute("CREATE TABLE variable ("
                    + "id INTEGER PRIMARY KEY, bot_job_id INTEGER, instruction_id INTEGER,"
                    + "type TEXT, name TEXT, value TEXT, local_format TEXT, delimiter TEXT)");
        }
    }

    private void execute(String... sql) throws Exception {
        try (Statement statement = anchor.createStatement()) {
            for (String command : sql) statement.execute(command);
        }
    }

    private JsonObject variable(JsonObject response, int id) {
        for (var item : response.getAsJsonArray("variables")) {
            JsonObject variable = item.getAsJsonObject();
            if (variable.get("id").getAsInt() == id) return variable;
        }
        throw new AssertionError("Missing variable " + id);
    }

    private JsonObject command(JsonObject variable, int id) {
        for (var item : variable.getAsJsonArray("commands")) {
            JsonObject command = item.getAsJsonObject();
            if (command.get("instructionId").getAsInt() == id) return command;
        }
        throw new AssertionError("Missing command " + id);
    }

    private boolean hasDiagnostic(JsonArray diagnostics, String code) {
        for (var item : diagnostics) {
            if (code.equals(item.getAsJsonObject().get("code").getAsString())) return true;
        }
        return false;
    }

    private boolean hasEdge(JsonArray edges, String type) {
        for (var item : edges) {
            if (type.equals(item.getAsJsonObject().get("type").getAsString())) return true;
        }
        return false;
    }
}

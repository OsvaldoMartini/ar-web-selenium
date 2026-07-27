package com.allinweb.ch.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class SimpleWebSocketServerCommandEditorCorrelationTest {

    @Test
    void memoryCapabilitiesResponseEchoesAuthorizedRequestCorrelation() {
        JsonObject response = new JsonObject();
        response.addProperty("ok", true);
        response.addProperty("graphRevision", "revision-from-loaded-graph");
        response.addProperty("requestId", "stale-request");
        response.addProperty("targetSessionId", "stale-session");
        response.addProperty("homeBankingId", 1);
        response.addProperty("botJobId", 2);

        JsonObject authorizedRequest = new JsonObject();
        authorizedRequest.addProperty("requestId", "capabilities-request-42");
        authorizedRequest.addProperty("bindingEpoch", "binding-7");
        authorizedRequest.addProperty("targetSessionId", "botJobTasks");
        authorizedRequest.addProperty("homeBankingId", 17);
        authorizedRequest.addProperty("botJobId", 42);

        JsonObject correlated =
                SimpleWebSocketServer.attachMemoryCapabilitiesCorrelation(
                        response,
                        authorizedRequest);

        assertSame(response, correlated);
        assertEquals(
                "capabilities-request-42",
                correlated.get("requestId").getAsString());
        assertEquals("binding-7", correlated.get("bindingEpoch").getAsString());
        assertEquals(
                "botJobTasks",
                correlated.get("targetSessionId").getAsString());
        assertEquals(17, correlated.get("homeBankingId").getAsInt());
        assertEquals(42, correlated.get("botJobId").getAsInt());
        assertEquals(
                "revision-from-loaded-graph",
                correlated.get("graphRevision").getAsString());
    }

    @Test
    void gridMemoryCapabilitiesResponseIsCorrelatedToo() {
        JsonObject response = new JsonObject();
        response.addProperty("ok", true);
        JsonObject request = new JsonObject();
        request.addProperty("requestId", "legacy-request");
        request.addProperty("targetSessionId", "botJobTasks");
        request.addProperty("homeBankingId", 17);
        request.addProperty("botJobId", 42);

        JsonObject correlated =
                SimpleWebSocketServer.attachMemoryCapabilitiesCorrelation(
                        response, request);

        assertSame(response, correlated);
        assertEquals("legacy-request", correlated.get("requestId").getAsString());
        assertEquals("botJobTasks", correlated.get("targetSessionId").getAsString());
        assertEquals(17, correlated.get("homeBankingId").getAsInt());
        assertEquals(42, correlated.get("botJobId").getAsInt());
    }

    @Test
    void variableMutationResponseUsesCanonicalRequestCorrelation() {
        JsonObject response = new JsonObject();
        response.addProperty("ok", true);
        JsonObject request = new JsonObject();
        request.addProperty("requestId", "variable-save-7");
        request.addProperty("targetSessionId", "componentTasks");
        request.addProperty("homeBankingId", 17);
        request.addProperty("botJobId", 42);

        JsonObject correlated =
                SimpleWebSocketServer.attachInstructionRequestCorrelation(
                        response, request);

        assertEquals("variable-save-7", correlated.get("requestId").getAsString());
        assertEquals("componentTasks", correlated.get("targetSessionId").getAsString());
        assertEquals(17, correlated.get("homeBankingId").getAsInt());
        assertEquals(42, correlated.get("botJobId").getAsInt());
    }
}

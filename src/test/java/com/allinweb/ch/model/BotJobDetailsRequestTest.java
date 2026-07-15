package com.allinweb.ch.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class BotJobDetailsRequestTest {

    @Test
    void parsesStringBodyBoundToMatchingTransportSession() {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("sessionId", "botJobTasks");
        envelope.addProperty(
                "body", "{\"requestId\":\"refresh-1\",\"botJobId\":42,\"action\":\"REFRESH\"}");

        BotJobDetailsRequest request = BotJobDetailsRequest.parse(envelope, "botJobTasks");

        assertEquals("botJobTasks", request.sessionId());
        assertEquals("refresh-1", request.requestId());
        assertEquals(42, request.botJobId());
        assertEquals("REFRESH", request.body().get("action").getAsString());
    }

    @Test
    void parsesObjectBodyWithoutRequiringAClientSessionClaim() {
        JsonObject body = new JsonObject();
        body.addProperty("requestId", "bootstrap-1");
        body.addProperty("botJobId", 7);
        JsonObject envelope = new JsonObject();
        envelope.add("body", body);

        BotJobDetailsRequest request = BotJobDetailsRequest.parse(envelope, ScannerWorkspaceSessions.PRE_SCANNER_GRID);

        assertEquals(ScannerWorkspaceSessions.PRE_SCANNER_GRID, request.sessionId());
        assertEquals("bootstrap-1", request.requestId());
        assertEquals(7, request.botJobId());
        assertEquals(7, request.body().get("botJobId").getAsInt());
    }

    @Test
    void rejectsClientSessionClaimThatDoesNotMatchTransport() {
        JsonObject envelope = envelope("request-1", 42);
        envelope.addProperty("sessionId", "componentTasks");

        assertThrows(
                IllegalArgumentException.class,
                () -> BotJobDetailsRequest.parse(envelope, "botJobTasks"));
    }

    @Test
    void rejectsMissingOrBlankTransportSession() {
        JsonObject envelope = envelope("request-1", 42);

        assertThrows(IllegalArgumentException.class, () -> BotJobDetailsRequest.parse(envelope, null));
        assertThrows(IllegalArgumentException.class, () -> BotJobDetailsRequest.parse(envelope, "   "));
    }

    @Test
    void rejectsMissingOrBlankRequestId() {
        JsonObject missing = envelope(null, 42);
        JsonObject blank = envelope("   ", 42);

        assertThrows(
                IllegalArgumentException.class,
                () -> BotJobDetailsRequest.parse(missing, "botJobTasks"));
        assertThrows(
                IllegalArgumentException.class,
                () -> BotJobDetailsRequest.parse(blank, "botJobTasks"));
    }

    @Test
    void rejectsNonPositiveBotJobId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BotJobDetailsRequest.parse(envelope("request-1", 0), "botJobTasks"));
        assertThrows(
                IllegalArgumentException.class,
                () -> BotJobDetailsRequest.parse(envelope("request-2", -7), "botJobTasks"));
    }

    @Test
    void extractsCorrelationEvenWhenTransportValidationWillFail() {
        BotJobDetailsRequest.Correlation correlation =
                BotJobDetailsRequest.correlation(envelope("request-9", 42));

        assertEquals("request-9", correlation.requestId());
        assertEquals(42, correlation.botJobId());
    }

    @Test
    void preservesRequestCorrelationWhenTheJobIdIsInvalid() {
        BotJobDetailsRequest.Correlation correlation =
                BotJobDetailsRequest.correlation(envelope("request-invalid-job", 0));

        assertEquals("request-invalid-job", correlation.requestId());
        assertEquals(-1, correlation.botJobId());
    }

    private JsonObject envelope(String requestId, int botJobId) {
        JsonObject body = new JsonObject();
        if (requestId != null) {
            body.addProperty("requestId", requestId);
        }
        body.addProperty("botJobId", botJobId);
        JsonObject envelope = new JsonObject();
        envelope.add("body", body);
        return envelope;
    }
}

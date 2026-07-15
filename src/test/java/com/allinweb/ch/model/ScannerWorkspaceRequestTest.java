package com.allinweb.ch.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class ScannerWorkspaceRequestTest {

    @Test
    void parsesStringBodyBoundToTransportSession() {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("sessionId", ScannerWorkspaceSessions.SCANNER_GRID);
        envelope.addProperty("body", "{\"requestId\":\"scanner-1\",\"botJobId\":42}");

        ScannerWorkspaceRequest request = ScannerWorkspaceRequest.parse(envelope, ScannerWorkspaceSessions.SCANNER_GRID);

        assertEquals(ScannerWorkspaceSessions.SCANNER_GRID, request.sessionId());
        assertEquals("scanner-1", request.requestId());
        assertEquals(42, request.botJobId());
    }

    @Test
    void rejectsSessionMismatchAndInvalidJob() {
        JsonObject envelope = envelope("scanner-2", 42);
        envelope.addProperty("sessionId", ScannerWorkspaceSessions.PRE_SCANNER_GRID);

        assertThrows(
                IllegalArgumentException.class,
                () -> ScannerWorkspaceRequest.parse(envelope, ScannerWorkspaceSessions.SCANNER_GRID));
        assertThrows(
                IllegalArgumentException.class,
                () -> ScannerWorkspaceRequest.parse(envelope("scanner-3", 0), ScannerWorkspaceSessions.SCANNER_GRID));
    }

    @Test
    void extractsCorrelationForParseFailures() {
        ScannerWorkspaceRequest.Correlation correlation =
                ScannerWorkspaceRequest.correlation(envelope("scanner-4", 77));

        assertEquals("scanner-4", correlation.requestId());
        assertEquals(77, correlation.botJobId());
    }

    @Test
    void rejectsMalformedStringBody() {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("body", "{invalid");

        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> ScannerWorkspaceRequest.parse(envelope, ScannerWorkspaceSessions.SCANNER_GRID));

        assertEquals("Scanner request body must be valid JSON", thrown.getMessage());
    }

    @Test
    void isolatesParsedBodyFromEnvelopeMutations() {
        JsonObject envelope = envelope("scanner-5", 42);

        ScannerWorkspaceRequest request = ScannerWorkspaceRequest.parse(envelope, ScannerWorkspaceSessions.SCANNER_GRID);
        envelope.getAsJsonObject("body").addProperty("requestId", "mutated");

        assertEquals("scanner-5", request.requestId());
        assertEquals("scanner-5", request.body().get("requestId").getAsString());
    }

    private JsonObject envelope(String requestId, int botJobId) {
        JsonObject body = new JsonObject();
        body.addProperty("requestId", requestId);
        body.addProperty("botJobId", botJobId);
        JsonObject envelope = new JsonObject();
        envelope.add("body", body);
        return envelope;
    }
}

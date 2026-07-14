package com.allinweb.ch.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class ScannerWorkspaceRequestTest {

    @Test
    void parsesStringBodyBoundToTransportSession() {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("sessionId", "scannerGrid");
        envelope.addProperty("body", "{\"requestId\":\"scanner-1\",\"botJobId\":42}");

        ScannerWorkspaceRequest request = ScannerWorkspaceRequest.parse(envelope, "scannerGrid");

        assertEquals("scannerGrid", request.sessionId());
        assertEquals("scanner-1", request.requestId());
        assertEquals(42, request.botJobId());
    }

    @Test
    void rejectsSessionMismatchAndInvalidJob() {
        JsonObject envelope = envelope("scanner-2", 42);
        envelope.addProperty("sessionId", "preScannerGrid");

        assertThrows(
                IllegalArgumentException.class,
                () -> ScannerWorkspaceRequest.parse(envelope, "scannerGrid"));
        assertThrows(
                IllegalArgumentException.class,
                () -> ScannerWorkspaceRequest.parse(envelope("scanner-3", 0), "scannerGrid"));
    }

    @Test
    void extractsCorrelationForParseFailures() {
        ScannerWorkspaceRequest.Correlation correlation =
                ScannerWorkspaceRequest.correlation(envelope("scanner-4", 77));

        assertEquals("scanner-4", correlation.requestId());
        assertEquals(77, correlation.botJobId());
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

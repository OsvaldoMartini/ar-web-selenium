package com.allinweb.ch.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class ScannerWorkspaceResponseTest {

    @Test
    void failureUsesFallbackMessageWhenBlank() {
        ScannerWorkspaceRequest request = request();

        ScannerWorkspaceResponse response =
                ScannerWorkspaceResponse.failure(" ", "SCANNER_ACTION_FAILED", request, ScannerWorkspaceAction.CLEAR_GRID);

        assertEquals(false, response.ok());
        assertEquals("Scanner operation failed", response.message());
        assertEquals("SCANNER_ACTION_FAILED", response.errorCode());
        assertEquals("CLEAR_GRID", response.action());
        assertEquals("response-1", response.requestId());
        assertEquals(42, response.botJobId());
    }

    @Test
    void failureHandlesMissingRequest() {
        ScannerWorkspaceResponse response =
                ScannerWorkspaceResponse.failure(null, "SCANNER_BOOTSTRAP_FAILED", null, null);

        assertEquals("Scanner operation failed", response.message());
        assertEquals("", response.requestId());
        assertEquals(-1, response.botJobId());
    }

    private ScannerWorkspaceRequest request() {
        JsonObject body = new JsonObject();
        body.addProperty("requestId", "response-1");
        body.addProperty("botJobId", 42);
        body.addProperty("action", "CLEAR_GRID");
        return new ScannerWorkspaceRequest(ScannerWorkspaceSessions.SCANNER_GRID, "response-1", 42, body);
    }
}

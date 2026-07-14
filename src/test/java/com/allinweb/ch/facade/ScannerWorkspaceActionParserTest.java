package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.allinweb.ch.model.ScannerWorkspaceAction;
import com.allinweb.ch.model.ScannerWorkspaceRequest;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class ScannerWorkspaceActionParserTest {

    private final ScannerWorkspaceActionParser parser = new ScannerWorkspaceActionParser();

    @Test
    void parsesValidActionCaseInsensitively() {
        assertEquals(ScannerWorkspaceAction.PAGE_SCANNER, parser.parse(request("page_scanner")));
    }

    @Test
    void rejectsMissingAction() {
        JsonObject body = body();

        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> parser.parse(request(body)));

        assertEquals("Scanner action is required", error.getMessage());
    }

    @Test
    void rejectsNonStringAction() {
        JsonObject body = body();
        body.add("action", new JsonObject());

        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> parser.parse(request(body)));

        assertEquals("Scanner action must be a string", error.getMessage());
    }

    @Test
    void rejectsUnsupportedAction() {
        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> parser.parse(request("NOPE")));

        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("Unsupported Scanner action"));
    }

    private ScannerWorkspaceRequest request(String action) {
        JsonObject body = body();
        body.addProperty("action", action);
        return request(body);
    }

    private ScannerWorkspaceRequest request(JsonObject body) {
        return new ScannerWorkspaceRequest("scannerGrid", "action-parser-1", 42, body);
    }

    private JsonObject body() {
        JsonObject body = new JsonObject();
        body.addProperty("requestId", "action-parser-1");
        body.addProperty("botJobId", 42);
        return body;
    }
}

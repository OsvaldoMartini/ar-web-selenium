package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.ScannerWorkspaceOperations;
import com.allinweb.ch.model.ScannerWorkspaceRequest;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.allinweb.ch.model.ScannerWorkspaceState;
import com.allinweb.ch.model.SplitDTO;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerWorkspacePayloadsTest {

    @Test
    void parsesCommaSeparatedSearchTerms() {
        ScannerWorkspaceRequest request = request("input, button, [role='tab']");

        assertEquals(List.of("input", "button", "[role='tab']"), List.of(ScannerWorkspacePayloads.searchTerms(request)));
    }

    @Test
    void parsesSearchTermArraysAndDropsBlanks() {
        JsonArray terms = new JsonArray();
        terms.add(" input ");
        terms.add("");
        terms.add("button");
        JsonObject body = body();
        body.add(ScannerWorkspaceOperations.SEARCH_TERMS, terms);
        ScannerWorkspaceRequest request = new ScannerWorkspaceRequest(
                ScannerWorkspaceSessions.SCANNER_GRID, "payload-1", 42, body);

        assertEquals(List.of("input", "button"), List.of(ScannerWorkspacePayloads.searchTerms(request)));
    }

    @Test
    void fallsBackToDefaultSearchTerms() {
        assertTrue(List.of(ScannerWorkspacePayloads.searchTerms(request(null))).contains("input"));
        assertTrue(List.of(ScannerWorkspacePayloads.searchTerms(request(" , , "))).contains("input"));
        assertTrue(ScannerWorkspacePayloads.defaultPageScanTerms().contains("button"));
    }

    @Test
    void mapsScannerStateAndElementsToSearchPayload() {
        ElementDTO element = new ElementDTO();
        element.setDefinedName("Login input");
        element.setTagName("input");

        SplitDTO payload = ScannerWorkspacePayloads.payload(state(), List.of(element));

        assertEquals(2, payload.getHomeBankingId());
        assertEquals(42, payload.getBotJobId());
        assertEquals("Apre Acconto", payload.getBotJobName());
        assertEquals(ScannerWorkspaceSessions.SCANNER_GRID, payload.getSessionId());
        assertEquals(ScannerWorkspaceOperations.SEARCH_TERMS, payload.getOperationId());
        assertEquals("SEARCH_TOOL", payload.getType());
        assertEquals(1, payload.getElementDetails().length);
        assertEquals("Login input", payload.getElementDetails()[0].getDefinedName());
        assertEquals(1, payload.getBlocks().size());
    }

    private ScannerWorkspaceRequest request(String searchTerms) {
        JsonObject body = body();
        if (searchTerms != null) body.addProperty(ScannerWorkspaceOperations.SEARCH_TERMS, searchTerms);
        return new ScannerWorkspaceRequest(ScannerWorkspaceSessions.SCANNER_GRID, "payload-1", 42, body);
    }

    private JsonObject body() {
        JsonObject body = new JsonObject();
        body.addProperty("requestId", "payload-1");
        body.addProperty("botJobId", 42);
        body.addProperty("action", "PAGE_SCANNER");
        return body;
    }

    private ScannerWorkspaceState state() {
        return new ScannerWorkspaceState(
                1L,
                42,
                "Apre Acconto",
                2,
                "https://bank.example",
                List.of(new ScannerWorkspaceState.Block(100, 1, "Login", true)),
                new ScannerWorkspaceState.Browser("OPEN", "https://bank.example/login", "Login", 1, true),
                new ScannerWorkspaceState.Focus("default", List.of("input")),
                new ScannerWorkspaceState.Ocr(true, "IDLE"),
                new ScannerWorkspaceState.Capabilities(true, true, true, true, true),
                "IDLE");
    }
}

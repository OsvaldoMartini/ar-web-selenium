package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.allinweb.ch.model.ScannerWorkspaceOperations;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PluginContextTest {

    @Test
    void pageScannerContextUsesScannerContractFields() {
        Map<String, Object> context = PluginContext.forPageScanner(
                        List.of("button", "input"),
                        false,
                        54545,
                        ScannerWorkspaceSessions.SCANNER_TOOL,
                        ScannerWorkspaceSessions.SCANNER_GRID,
                        ScannerWorkspaceOperations.SEARCH_TERMS,
                        7,
                        42)
                .toJsContext();

        assertEquals("pageScanner", context.get("pluginId"));
        assertEquals(2, context.get("apiVersion"));
        assertEquals(List.of("button", "input"), context.get(ScannerWorkspacePayloads.searchTermsFieldName()));
        assertEquals(ScannerWorkspaceSessions.SCANNER_TOOL, context.get("sessionId"));
        assertEquals(ScannerWorkspaceSessions.SCANNER_GRID, context.get("destination"));
        assertEquals(ScannerWorkspaceOperations.SEARCH_TERMS, context.get("operationId"));
        assertEquals(7, context.get("homeBankingId"));
        assertEquals(42, context.get("botJobId"));
    }

    @Test
    void hoverPickContextOmitsSearchTermsAndKeepsOrigins() {
        Map<String, Object> context = PluginContext.forHoverPick(
                        true,
                        54545,
                        ScannerWorkspaceSessions.SCANNER_TOOL,
                        ScannerWorkspaceSessions.SCANNER_GRID,
                        ScannerWorkspaceOperations.ADD_PICK_ONE,
                        7,
                        42,
                        "https://bank.example",
                        "https://bank.example")
                .toJsContext();

        assertEquals("hoverPick", context.get("pluginId"));
        assertFalse(context.containsKey(ScannerWorkspacePayloads.searchTermsFieldName()));
        assertEquals(true, context.get("hiddenFields"));
        assertEquals("https://bank.example", context.get("targetOriginURL"));
        assertEquals("https://bank.example", context.get("trustedOriginURL"));
    }
}

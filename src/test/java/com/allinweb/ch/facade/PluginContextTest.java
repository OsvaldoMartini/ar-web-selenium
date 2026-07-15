package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.allinweb.ch.model.ScannerWorkspaceOperations;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PluginContextTest {

    @Test
    void pageScannerContextUsesScannerContractFields() {
        ScannerSearchRoute route = ScannerSearchRoute.standardPageScanner();
        Map<String, Object> context = PluginContext.forPageScanner(
                        List.of("button", "input"),
                        false,
                        54545,
                        route.sourceSessionId(),
                        route.destinationSessionId(),
                        route.operationId(),
                        7,
                        42)
                .toJsContext();

        assertEquals("pageScanner", context.get("pluginId"));
        assertEquals(2, context.get("apiVersion"));
        assertEquals(List.of("button", "input"), context.get(ScannerWorkspacePayloads.searchTermsFieldName()));
        assertEquals(route.sourceSessionId(), context.get("sessionId"));
        assertEquals(route.destinationSessionId(), context.get("destination"));
        assertEquals(route.operationId(), context.get("operationId"));
        assertEquals(7, context.get("homeBankingId"));
        assertEquals(42, context.get("botJobId"));
    }

    @Test
    void hoverPickContextOmitsSearchTermsAndKeepsOrigins() {
        ScannerSearchRoute route = ScannerSearchRoute.standardPageScanner();
        Map<String, Object> context = PluginContext.forHoverPick(
                        true,
                        54545,
                        route.sourceSessionId(),
                        route.destinationSessionId(),
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

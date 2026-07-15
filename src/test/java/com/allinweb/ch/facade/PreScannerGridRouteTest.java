package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.allinweb.ch.model.ScannerWorkspaceOperations;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import org.junit.jupiter.api.Test;

class PreScannerGridRouteTest {

    @Test
    void standardSearchTermsTargetsPreScannerGrid() {
        PreScannerGridRoute route = PreScannerGridRoute.standardSearchTerms();

        assertEquals(ScannerWorkspaceSessions.PRE_SCANNER_GRID, route.destinationSessionId());
        assertEquals(ScannerWorkspaceOperations.SEARCH_TOOL, route.payloadType());
        assertEquals(ScannerWorkspaceOperations.SEARCH_TERMS, route.operationId());
    }
}

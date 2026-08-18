package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.allinweb.ch.model.ScannerWorkspaceOperations;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import org.junit.jupiter.api.Test;

class ScannerSearchRouteTest {

    @Test
    void standardPageScannerTargetsScannerGridSearchTerms() {
        ScannerSearchRoute route = ScannerSearchRoute.standardPageScanner();

        assertEquals(ScannerWorkspaceSessions.SCANNER_TOOL, route.sourceSessionId());
        assertEquals(ScannerWorkspaceSessions.SCANNER_GRID, route.destinationSessionId());
        assertEquals(ScannerWorkspaceOperations.SEARCH_TERMS, route.operationId());
    }
}

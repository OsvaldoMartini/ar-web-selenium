package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.allinweb.ch.model.ScannerWorkspaceOperations;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import org.junit.jupiter.api.Test;

class ScannerMobilePickRouteTest {

    @Test
    void standardRouteKeepsMobilePayloadAndChunkRoutingContracts() {
        ScannerMobilePickRoute route = ScannerMobilePickRoute.standard();

        assertEquals(ScannerWorkspaceSessions.SCANNER_TOOL, route.sourceSessionId());
        assertEquals(ScannerWorkspaceSessions.MOBILE_SCANNER_GRID, route.payloadSessionId());
        assertEquals(ScannerWorkspaceOperations.ADD_PICK_ONE, route.payloadOperationId());
        assertEquals(ScannerWorkspaceSessions.SCANNER_GRID, route.chunkOperationId());
    }
}

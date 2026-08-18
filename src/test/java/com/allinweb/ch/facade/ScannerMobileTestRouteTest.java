package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.ScannerWorkspaceSessions;
import org.junit.jupiter.api.Test;

class ScannerMobileTestRouteTest {

    @Test
    void standardRouteTargetsMobileScannerAndReturnServer() {
        ScannerMobileTestRoute route = ScannerMobileTestRoute.standard();

        assertEquals(ScannerWorkspaceSessions.MOBILE_SCANNER_GRID, route.scannerSessionId());
        assertEquals(ScannerWorkspaceSessions.MOBILE_RETURN_SERVER, route.returnSessionId());
        assertTrue(route.isScannerSession(ScannerWorkspaceSessions.MOBILE_SCANNER_GRID));
        assertFalse(route.isScannerSession(ScannerWorkspaceSessions.SCANNER_GRID));
    }
}

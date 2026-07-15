package com.allinweb.ch.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ScannerWorkspaceSessionsTest {
    @Test
    void keepsScannerWorkspaceSessionIdsStable() {
        assertEquals("scannerGrid", ScannerWorkspaceSessions.SCANNER_GRID);
        assertEquals("preScannerGrid", ScannerWorkspaceSessions.PRE_SCANNER_GRID);
    }
}

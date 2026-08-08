package com.allinweb.ch.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScannerWorkspaceSessionsTest {
    @Test
    void keepsScannerWorkspaceSessionIdsStable() {
        assertEquals("scannerGrid", ScannerWorkspaceSessions.SCANNER_GRID);
        assertEquals("preScannerGrid", ScannerWorkspaceSessions.PRE_SCANNER_GRID);
        assertEquals("botJobTasks", ScannerWorkspaceSessions.BOT_JOB_TASKS);
        assertEquals("componentTasks", ScannerWorkspaceSessions.COMPONENT_TASKS);
        assertEquals("mobileScannerGrid", ScannerWorkspaceSessions.MOBILE_SCANNER_GRID);
        assertEquals("mobile-return-server", ScannerWorkspaceSessions.MOBILE_RETURN_SERVER);
        assertEquals("perform-list-data", ScannerWorkspaceSessions.PERFORM_LIST_DATA);
        assertEquals("scannerTool", ScannerWorkspaceSessions.SCANNER_TOOL);
        assertEquals("scanner-element-pane", ScannerWorkspaceSessions.SCANNER_ELEMENT_PANE);
    }

    @Test
    void classifiesOnlyWellFormedDetachedPageScannerSessions() {
        assertEquals("page-scanner-", ScannerWorkspaceSessions.PAGE_SCANNER_PREFIX);
        assertTrue(ScannerWorkspaceSessions.isPageScannerSession("page-scanner-window-42"));
        assertTrue(ScannerWorkspaceSessions.isOcrSourceScannerSession("page-scanner-window-42"));
        assertTrue(ScannerWorkspaceSessions.isOcrSourceScannerSession(ScannerWorkspaceSessions.SCANNER_GRID));
        assertTrue(ScannerWorkspaceSessions.isOcrSourceScannerSession(ScannerWorkspaceSessions.PRE_SCANNER_GRID));

        assertFalse(ScannerWorkspaceSessions.isPageScannerSession(null));
        assertFalse(ScannerWorkspaceSessions.isPageScannerSession("page-scanner-"));
        assertFalse(ScannerWorkspaceSessions.isPageScannerSession("page-scanner-window/42"));
        assertFalse(ScannerWorkspaceSessions.isPageScannerSession("ocr-config-window-42"));
        assertFalse(ScannerWorkspaceSessions.isOcrSourceScannerSession(ScannerWorkspaceSessions.BOT_JOB_TASKS));
    }
}

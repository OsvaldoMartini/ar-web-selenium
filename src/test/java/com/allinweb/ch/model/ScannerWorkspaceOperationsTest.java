package com.allinweb.ch.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ScannerWorkspaceOperationsTest {

    @Test
    void keepsScannerOperationIdsStable() {
        assertEquals("scanner.bootstrap", ScannerWorkspaceOperations.BOOTSTRAP_COMMAND);
        assertEquals("scanner.action", ScannerWorkspaceOperations.ACTION_COMMAND);
        assertEquals("scanner.bootstrapResponse", ScannerWorkspaceOperations.BOOTSTRAP_RESPONSE);
        assertEquals("scanner.actionResponse", ScannerWorkspaceOperations.ACTION_RESPONSE);
        assertEquals("scanner.state", ScannerWorkspaceOperations.STATE_EVENT);
        assertEquals("searchTerms", ScannerWorkspaceOperations.SEARCH_TERMS);
        assertEquals("SEARCH_TOOL", ScannerWorkspaceOperations.SEARCH_TOOL);
        assertEquals("addPickOne", ScannerWorkspaceOperations.ADD_PICK_ONE);
        assertEquals("PRE_SCAN_PAGE", ScannerWorkspaceOperations.PRE_SCAN_PAGE);
        assertEquals("PRE_SCAN_REFRESH_PAGE", ScannerWorkspaceOperations.PRE_SCAN_REFRESH_PAGE);
        assertEquals("PRE_SCAN_CLEAR_GRID", ScannerWorkspaceOperations.PRE_SCAN_CLEAR_GRID);
        assertEquals("PRE_SCAN_SEND_DOM_REVIEW", ScannerWorkspaceOperations.PRE_SCAN_SEND_DOM_REVIEW);
        assertEquals("PRE_SCAN_REQUEST_SUPPORT", ScannerWorkspaceOperations.PRE_SCAN_REQUEST_SUPPORT);
        assertEquals("SCANNER_APP", ScannerWorkspaceOperations.SCANNER_APP);
    }
}

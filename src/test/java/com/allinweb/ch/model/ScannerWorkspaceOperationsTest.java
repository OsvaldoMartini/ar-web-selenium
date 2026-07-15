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
    }
}

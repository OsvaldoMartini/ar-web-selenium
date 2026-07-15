package com.allinweb.ch.facade;

import com.allinweb.ch.model.ScannerWorkspaceOperations;
import com.allinweb.ch.model.ScannerWorkspaceSessions;

public record ScannerSearchRoute(String sourceSessionId, String destinationSessionId, String operationId) {
    public static ScannerSearchRoute standardPageScanner() {
        return new ScannerSearchRoute(
                ScannerWorkspaceSessions.SCANNER_TOOL,
                ScannerWorkspaceSessions.SCANNER_GRID,
                ScannerWorkspaceOperations.SEARCH_TERMS);
    }
}

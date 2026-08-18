package com.allinweb.ch.facade;

import com.allinweb.ch.model.ScannerWorkspaceOperations;
import com.allinweb.ch.model.ScannerWorkspaceSessions;

public record PreScannerGridRoute(String destinationSessionId, String payloadType, String operationId) {
    public static PreScannerGridRoute standardSearchTerms() {
        return new PreScannerGridRoute(
                ScannerWorkspaceSessions.PRE_SCANNER_GRID,
                ScannerWorkspaceOperations.SEARCH_TOOL,
                ScannerWorkspaceOperations.SEARCH_TERMS);
    }
}

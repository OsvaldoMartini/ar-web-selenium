package com.allinweb.ch.facade;

import com.allinweb.ch.model.ScannerWorkspaceOperations;
import com.allinweb.ch.model.ScannerWorkspaceSessions;

public record ScannerMobilePickRoute(
        String sourceSessionId, String payloadSessionId, String payloadOperationId, String chunkOperationId) {
    public static ScannerMobilePickRoute standard() {
        return new ScannerMobilePickRoute(
                ScannerWorkspaceSessions.SCANNER_TOOL,
                ScannerWorkspaceSessions.MOBILE_SCANNER_GRID,
                ScannerWorkspaceOperations.ADD_PICK_ONE,
                ScannerWorkspaceSessions.SCANNER_GRID);
    }
}

package com.allinweb.ch.facade;

import com.allinweb.ch.model.ScannerWorkspaceSessions;

public final class ScannerWorkspaceSessionClassifier {

    public boolean isInstructionWorkspaceSession(String sessionId) {
        return contains(sessionId, ScannerWorkspaceSessions.BOT_JOB_TASKS)
                || contains(sessionId, ScannerWorkspaceSessions.SCANNER_TOOL)
                || contains(sessionId, ScannerWorkspaceSessions.SCANNER_GRID)
                || contains(sessionId, ScannerWorkspaceSessions.MOBILE_SCANNER_GRID)
                || contains(sessionId, ScannerWorkspaceSessions.SCANNER_ELEMENT_PANE);
    }

    public boolean isScannerGridSession(String sessionId) {
        return ScannerWorkspaceSessions.SCANNER_GRID.equals(sessionId);
    }

    public boolean isScannerToolSession(String sessionId) {
        return ScannerWorkspaceSessions.SCANNER_TOOL.equals(sessionId);
    }

    public boolean isScannerElementPaneSession(String sessionId) {
        return ScannerWorkspaceSessions.SCANNER_ELEMENT_PANE.equals(sessionId);
    }

    private boolean contains(String sessionId, String expectedSessionId) {
        return sessionId != null && sessionId.contains(expectedSessionId);
    }
}

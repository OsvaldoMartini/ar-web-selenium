package com.allinweb.ch.facade;

import com.allinweb.ch.model.ScannerWorkspaceSessions;

public record ScannerMobileTestRoute(String scannerSessionId, String returnSessionId) {
    public static ScannerMobileTestRoute standard() {
        return new ScannerMobileTestRoute(
                ScannerWorkspaceSessions.MOBILE_SCANNER_GRID,
                ScannerWorkspaceSessions.MOBILE_RETURN_SERVER);
    }

    public boolean isScannerSession(String sessionId) {
        return scannerSessionId.equals(sessionId);
    }
}

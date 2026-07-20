package com.allinweb.ch.model;

public final class ScannerWorkspaceSessions {
    public static final String PAGE_SCANNER_PREFIX = "page-scanner-";
    public static final String SCANNER_GRID = "scannerGrid";
    public static final String PRE_SCANNER_GRID = "preScannerGrid";
    public static final String BOT_JOB_TASKS = "botJobTasks";
    public static final String COMPONENT_TASKS = "componentTasks";
    public static final String MOBILE_SCANNER_GRID = "mobileScannerGrid";
    public static final String MOBILE_RETURN_SERVER = "mobile-return-server";
    public static final String PERFORM_LIST_DATA = "perform-list-data";
    public static final String SCANNER_TOOL = "scannerTool";
    public static final String SCANNER_ELEMENT_PANE = "scanner-element-pane";

    /** Returns whether the logical session belongs to a detached Page Scanner workspace. */
    public static boolean isPageScannerSession(String sessionId) {
        if (sessionId == null || !sessionId.startsWith(PAGE_SCANNER_PREFIX)) {
            return false;
        }
        String id = sessionId.substring(PAGE_SCANNER_PREFIX.length());
        return !id.isEmpty() && id.length() <= 80 && id.matches("[A-Za-z0-9-]+");
    }

    /** Classifies possible OCR source sessions; detached sessions still require registry validation. */
    public static boolean isOcrSourceScannerSession(String sessionId) {
        return SCANNER_GRID.equals(sessionId)
                || PRE_SCANNER_GRID.equals(sessionId)
                || isPageScannerSession(sessionId);
    }

    private ScannerWorkspaceSessions() {
    }
}

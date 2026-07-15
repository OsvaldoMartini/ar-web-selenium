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
        assertEquals("LAUNCH_BOT_JOB_TEST", ScannerWorkspaceOperations.LAUNCH_BOT_JOB_TEST);
        assertEquals("ATTACHED_DEVICE", ScannerWorkspaceOperations.ATTACHED_DEVICE);
        assertEquals("DISCOVERY_APP", ScannerWorkspaceOperations.DISCOVERY_APP);
        assertEquals("MOBILE_SCROLL_UP", ScannerWorkspaceOperations.MOBILE_SCROLL_UP);
        assertEquals("MOBILE_SCROLL_DOWN", ScannerWorkspaceOperations.MOBILE_SCROLL_DOWN);
        assertEquals("MOBILE_BACK", ScannerWorkspaceOperations.MOBILE_BACK);
        assertEquals("MOBILE_HOME", ScannerWorkspaceOperations.MOBILE_HOME);
        assertEquals("MOBILE_RECENTS", ScannerWorkspaceOperations.MOBILE_RECENTS);
        assertEquals("MOBILE_CLOSE_ALL", ScannerWorkspaceOperations.MOBILE_CLOSE_ALL);
        assertEquals("MOBILE_NEXT_DONE", ScannerWorkspaceOperations.MOBILE_NEXT_DONE);
        assertEquals("MOBILE_CLOSE_KEYBOARD", ScannerWorkspaceOperations.MOBILE_CLOSE_KEYBOARD);
        assertEquals("REACTIVATE_BUTTONS", ScannerWorkspaceOperations.REACTIVATE_BUTTONS);
        assertEquals("MOBILE_LOAD_JOBS", ScannerWorkspaceOperations.MOBILE_LOAD_JOBS);
        assertEquals("MOBILE_VALIDATE_FIELDS", ScannerWorkspaceOperations.MOBILE_VALIDATE_FIELDS);
        assertEquals("botJobList", ScannerWorkspaceOperations.BOT_JOB_LIST);
        assertEquals("validateFields", ScannerWorkspaceOperations.VALIDATE_FIELDS);
        assertEquals("CLOSE_BROWSER", ScannerWorkspaceOperations.CLOSE_BROWSER);
        assertEquals("closeBrowser", ScannerWorkspaceOperations.CLOSE_BROWSER_OPERATION);
        assertEquals("HOVERED_ROW", ScannerWorkspaceOperations.HOVERED_ROW);
        assertEquals("highlight", ScannerWorkspaceOperations.HIGHLIGHT);
        assertEquals("CLEAR_HOVER_PICK_FILE", ScannerWorkspaceOperations.CLEAR_HOVER_PICK_FILE);
        assertEquals("NEW_ELEMENT_DTO", ScannerWorkspaceOperations.NEW_ELEMENT_DTO);
        assertEquals("SEND_ALL_ELEMENTS_DTO", ScannerWorkspaceOperations.SEND_ALL_ELEMENTS_DTO);
        assertEquals("UPDATE_ALL_ELEMENTS_DTO", ScannerWorkspaceOperations.UPDATE_ALL_ELEMENTS_DTO);
        assertEquals("DEL_ELEMENT_DTO", ScannerWorkspaceOperations.DEL_ELEMENT_DTO);
        assertEquals("DETAILS_ELEMENT_DTO", ScannerWorkspaceOperations.DETAILS_ELEMENT_DTO);
        assertEquals("TEST_CLICK_DTO", ScannerWorkspaceOperations.TEST_CLICK_DTO);
        assertEquals("TEST_INPUT_DTO", ScannerWorkspaceOperations.TEST_INPUT_DTO);
        assertEquals("ACTION_EXECUTOR", ScannerWorkspaceOperations.ACTION_EXECUTOR);
        assertEquals("UPDATE_LIST_ELEMENTS", ScannerWorkspaceOperations.UPDATE_LIST_ELEMENTS);
        assertEquals("PRE_SCAN_PAGE", ScannerWorkspaceOperations.PRE_SCAN_PAGE);
        assertEquals("PRE_SCAN_REFRESH_PAGE", ScannerWorkspaceOperations.PRE_SCAN_REFRESH_PAGE);
        assertEquals("PRE_SCAN_CLEAR_GRID", ScannerWorkspaceOperations.PRE_SCAN_CLEAR_GRID);
        assertEquals("PRE_SCAN_SEND_DOM_REVIEW", ScannerWorkspaceOperations.PRE_SCAN_SEND_DOM_REVIEW);
        assertEquals("PRE_SCAN_REQUEST_SUPPORT", ScannerWorkspaceOperations.PRE_SCAN_REQUEST_SUPPORT);
        assertEquals("SCANNER_APP", ScannerWorkspaceOperations.SCANNER_APP);
    }
}

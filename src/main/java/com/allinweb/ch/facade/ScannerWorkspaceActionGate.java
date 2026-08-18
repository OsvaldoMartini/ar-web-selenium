package com.allinweb.ch.facade;

import com.allinweb.ch.model.ScannerWorkspaceAction;
import com.allinweb.ch.model.ScannerWorkspaceState;

final class ScannerWorkspaceActionGate {

    void validateAllowed(ScannerWorkspaceAction action, ScannerWorkspaceState state) {
        switch (action) {
            case PAGE_SCANNER -> {
                if (!state.capabilities().canUsePageScanner()) {
                    throw new IllegalStateException("Page Scanner is not available for this Bot Job");
                }
                requireScannableBrowser("Page Scanner", state);
            }
            case REFRESH_PAGE -> requireScannableBrowser("Refresh Web Page", state);
            case PREVIOUS_TAB, NEXT_TAB -> requireScannableBrowser("Browser tab navigation", state);
            case PRE_LAUNCH -> {
                if (!state.capabilities().canExecute()) {
                    throw new IllegalStateException("Scanner execution is not available for this Bot Job");
                }
            }
            default -> {
            }
        }
    }

    private void requireScannableBrowser(String actionName, ScannerWorkspaceState state) {
        if (!state.browser().scannable()) {
            throw new IllegalStateException(actionName + " requires an open scanner browser");
        }
    }
}

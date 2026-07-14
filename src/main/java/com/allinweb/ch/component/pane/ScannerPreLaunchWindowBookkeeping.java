package com.allinweb.ch.component.pane;

final class ScannerPreLaunchWindowBookkeeping {
    private final Operations operations;

    ScannerPreLaunchWindowBookkeeping(Operations operations) {
        this.operations = operations;
    }

    void refreshChangedWindows() {
        Integer currentWindowHandleCount = operations.currentWindowHandleCount();
        if (currentWindowHandleCount == null) {
            return;
        }
        if (currentWindowHandleCount.intValue() != operations.knownWindowHandleCount()) {
            operations.updateWindowHandlesList();
            operations.updateButtonState();
        }
    }

    interface Operations {
        Integer currentWindowHandleCount();

        int knownWindowHandleCount();

        void updateWindowHandlesList();

        void updateButtonState();
    }
}

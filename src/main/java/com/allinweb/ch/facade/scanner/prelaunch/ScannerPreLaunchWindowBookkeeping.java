package com.allinweb.ch.facade.scanner.prelaunch;

public final class ScannerPreLaunchWindowBookkeeping {
    private final Operations operations;

    public ScannerPreLaunchWindowBookkeeping(Operations operations) {
        this.operations = operations;
    }

    public void refreshChangedWindows() {
        Integer currentWindowHandleCount = operations.currentWindowHandleCount();
        if (currentWindowHandleCount == null) {
            return;
        }
        if (currentWindowHandleCount.intValue() != operations.knownWindowHandleCount()) {
            operations.updateWindowHandlesList();
            operations.updateButtonState();
        }
    }

    public interface Operations {
        Integer currentWindowHandleCount();

        int knownWindowHandleCount();

        void updateWindowHandlesList();

        void updateButtonState();
    }
}

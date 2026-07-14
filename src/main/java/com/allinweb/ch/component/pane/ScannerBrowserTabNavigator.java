package com.allinweb.ch.component.pane;

final class ScannerBrowserTabNavigator {
    private final Operations operations;

    ScannerBrowserTabNavigator(Operations operations) {
        this.operations = operations;
    }

    void switchLeft() {
        if (!operations.hasCurrentDriver()) {
            return;
        }
        if (operations.currentWindowHandleCount() > 1 && operations.currentTabIndex() > 0) {
            operations.setCurrentTabIndex(operations.currentTabIndex() - 1);
            switchToCurrentTab();
        }
    }

    void switchRight() {
        if (!operations.hasCurrentDriver()) {
            return;
        }
        if (operations.currentWindowHandleCount() > 1
                && operations.currentTabIndex() < operations.knownWindowHandleCount() - 1) {
            operations.setCurrentTabIndex(operations.currentTabIndex() + 1);
            switchToCurrentTab();
        }
    }

    void handleWindowHandlesChange() {
        if (!operations.hasCurrentDriver()) {
            return;
        }
        if (operations.currentWindowHandleCount() != operations.knownWindowHandleCount()) {
            operations.updateWindowHandlesList();
            operations.setCurrentTabIndex(operations.knownWindowHandleCount() - 1);
            switchToCurrentTab();
        }
    }

    private void switchToCurrentTab() {
        operations.switchToWindow(operations.windowHandleAt(operations.currentTabIndex()));
        operations.updateSceneTitleWithCurrentUrl(operations.currentUrl());
    }

    interface Operations {
        boolean hasCurrentDriver();

        int currentWindowHandleCount();

        int knownWindowHandleCount();

        int currentTabIndex();

        void setCurrentTabIndex(int currentTabIndex);

        String windowHandleAt(int index);

        void switchToWindow(String windowHandle);

        String currentUrl();

        void updateSceneTitleWithCurrentUrl(String currentUrl);

        void updateWindowHandlesList();
    }
}

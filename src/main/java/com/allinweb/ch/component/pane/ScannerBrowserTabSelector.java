package com.allinweb.ch.component.pane;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class ScannerBrowserTabSelector {
    private final Operations operations;

    ScannerBrowserTabSelector(Operations operations) {
        this.operations = operations;
    }

    boolean switchToLastBrowserTab() {
        if (!operations.hasCurrentDriver()) {
            return true;
        }
        try {
            Set<String> windowHandles = operations.windowHandles();
            List<String> windowHandlesList = new ArrayList<>(windowHandles);
            operations.switchToWindow(windowHandlesList.get(windowHandlesList.size() - 1));
            return true;
        } catch (Exception error) {
            operations.browserNotAttached();
            return false;
        }
    }

    interface Operations {
        boolean hasCurrentDriver();

        Set<String> windowHandles();

        void switchToWindow(String windowHandle);

        void browserNotAttached();
    }
}

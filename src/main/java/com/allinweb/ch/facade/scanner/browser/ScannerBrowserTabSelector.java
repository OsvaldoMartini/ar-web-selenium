package com.allinweb.ch.facade.scanner.browser;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ScannerBrowserTabSelector {
    private final Operations operations;

    public ScannerBrowserTabSelector(Operations operations) {
        this.operations = operations;
    }

    public boolean switchToLastBrowserTab() {
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

    public interface Operations {
        boolean hasCurrentDriver();

        Set<String> windowHandles();

        void switchToWindow(String windowHandle);

        void browserNotAttached();
    }
}

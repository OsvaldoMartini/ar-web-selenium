package com.allinweb.ch.component.pane;

final class ScannerPreLaunchStopper {
    private final Operations operations;

    ScannerPreLaunchStopper(Operations operations) {
        this.operations = operations;
    }

    void stop() {
        operations.enableLaunch();
        operations.requestIntercept();
        operations.markNotRunning();
        operations.lastBrowserTab();
    }

    interface Operations {
        void enableLaunch();

        void requestIntercept();

        void markNotRunning();

        boolean lastBrowserTab();
    }
}

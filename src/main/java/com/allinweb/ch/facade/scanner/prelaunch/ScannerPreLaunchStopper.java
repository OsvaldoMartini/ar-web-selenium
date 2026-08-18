package com.allinweb.ch.facade.scanner.prelaunch;

public final class ScannerPreLaunchStopper {
    private final Operations operations;

    public ScannerPreLaunchStopper(Operations operations) {
        this.operations = operations;
    }

    public void stop() {
        operations.enableLaunch();
        operations.requestIntercept();
        operations.markNotRunning();
        operations.lastBrowserTab();
    }

    public interface Operations {
        void enableLaunch();

        void requestIntercept();

        void markNotRunning();

        boolean lastBrowserTab();
    }
}

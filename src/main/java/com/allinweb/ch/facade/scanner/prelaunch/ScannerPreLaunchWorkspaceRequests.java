package com.allinweb.ch.facade.scanner.prelaunch;

public final class ScannerPreLaunchWorkspaceRequests {
    private final Operations operations;

    public ScannerPreLaunchWorkspaceRequests(Operations operations) {
        this.operations = operations;
    }

    public void requestStart(int botJobId) {
        ensureCurrentScannerJob(botJobId);
        if (!operations.preLaunchBackendReady()) {
            throw new IllegalStateException("Scanner Pre-Launch backend is not ready");
        }
        operations.runLater(operations::startPreLaunch);
    }

    public void requestStop(int botJobId) {
        ensureCurrentScannerJob(botJobId);
        if (!operations.stopPreLaunchBackendReady()) {
            throw new IllegalStateException("Scanner Pre-Launch backend is not ready");
        }
        operations.runLater(operations::stopPreLaunch);
    }

    private void ensureCurrentScannerJob(int botJobId) {
        Integer currentBotJobId = operations.currentBotJobId();
        if (currentBotJobId == null || currentBotJobId.intValue() != botJobId) {
            throw new IllegalStateException("Scanner workspace is not open for Bot Job " + botJobId);
        }
    }

    public interface Operations {
        Integer currentBotJobId();

        boolean preLaunchBackendReady();

        boolean stopPreLaunchBackendReady();

        void runLater(Runnable task);

        void startPreLaunch();

        void stopPreLaunch();
    }
}

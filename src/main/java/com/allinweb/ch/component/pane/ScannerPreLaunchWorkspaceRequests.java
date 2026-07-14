package com.allinweb.ch.component.pane;

final class ScannerPreLaunchWorkspaceRequests {
    private final Operations operations;

    ScannerPreLaunchWorkspaceRequests(Operations operations) {
        this.operations = operations;
    }

    void requestStart(int botJobId) {
        ensureCurrentScannerJob(botJobId);
        if (!operations.preLaunchControlsReady()) {
            throw new IllegalStateException("Scanner Pre-Launch controls are not ready");
        }
        operations.runLater(operations::startPreLaunch);
    }

    void requestStop(int botJobId) {
        ensureCurrentScannerJob(botJobId);
        if (!operations.stopPreLaunchControlsReady()) {
            throw new IllegalStateException("Scanner Pre-Launch controls are not ready");
        }
        operations.runLater(operations::stopPreLaunch);
    }

    private void ensureCurrentScannerJob(int botJobId) {
        Integer currentBotJobId = operations.currentBotJobId();
        if (currentBotJobId == null || currentBotJobId.intValue() != botJobId) {
            throw new IllegalStateException("Scanner workspace is not open for Bot Job " + botJobId);
        }
    }

    interface Operations {
        Integer currentBotJobId();

        boolean preLaunchControlsReady();

        boolean stopPreLaunchControlsReady();

        void runLater(Runnable task);

        void startPreLaunch();

        void stopPreLaunch();
    }
}

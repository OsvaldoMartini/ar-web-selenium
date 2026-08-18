package com.allinweb.ch.facade;

final class ScannerExecutionOperations implements ScannerWorkspaceService.ExecutionOperations {
    @Override
    public void preLaunch(int botJobId) {
        throw new IllegalStateException("Scanner Pre-Launch backend adapter is not connected yet");
    }

    @Override
    public void stopPreLaunch(int botJobId) {
        PerformActions.getInstance().setInterceptBotJob(true);
    }
}

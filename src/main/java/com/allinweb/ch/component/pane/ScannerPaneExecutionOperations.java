package com.allinweb.ch.component.pane;

import com.allinweb.ch.facade.ScannerWorkspaceService;

final class ScannerPaneExecutionOperations implements ScannerWorkspaceService.ExecutionOperations {
    private final ARScannedElementPane pane;

    ScannerPaneExecutionOperations(ARScannedElementPane pane) {
        this.pane = pane;
    }

    @Override
    public void preLaunch(int botJobId) {
        pane.requestPreLaunchFromWorkspace(botJobId);
    }

    @Override
    public void stopPreLaunch(int botJobId) {
        pane.requestStopPreLaunchFromWorkspace(botJobId);
    }
}

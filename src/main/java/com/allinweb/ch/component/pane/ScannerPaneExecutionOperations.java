package com.allinweb.ch.component.pane;

import com.allinweb.ch.facade.ScannerWorkspaceService;

final class ScannerPaneExecutionOperations implements ScannerWorkspaceService.ExecutionOperations {
    private final ScannerPreLaunchControls preLaunchControls;

    ScannerPaneExecutionOperations(ScannerPreLaunchControls preLaunchControls) {
        this.preLaunchControls = preLaunchControls;
    }

    @Override
    public void preLaunch(int botJobId) {
        preLaunchControls.requestPreLaunchFromWorkspace(botJobId);
    }

    @Override
    public void stopPreLaunch(int botJobId) {
        preLaunchControls.requestStopPreLaunchFromWorkspace(botJobId);
    }
}

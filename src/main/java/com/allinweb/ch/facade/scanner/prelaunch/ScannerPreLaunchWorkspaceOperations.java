package com.allinweb.ch.facade.scanner.prelaunch;

import com.allinweb.ch.facade.ScannerWorkspaceService;

public final class ScannerPreLaunchWorkspaceOperations implements ScannerWorkspaceService.ExecutionOperations {
    private final ScannerPreLaunchControls preLaunchControls;

    public ScannerPreLaunchWorkspaceOperations(ScannerPreLaunchControls preLaunchControls) {
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

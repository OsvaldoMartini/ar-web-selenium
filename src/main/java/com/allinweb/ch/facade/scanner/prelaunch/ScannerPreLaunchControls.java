package com.allinweb.ch.facade.scanner.prelaunch;

public interface ScannerPreLaunchControls {
    void requestPreLaunchFromWorkspace(int botJobId);

    void requestStopPreLaunchFromWorkspace(int botJobId);
}

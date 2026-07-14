package com.allinweb.ch.component.pane;

interface ScannerPreLaunchControls {
    void requestPreLaunchFromWorkspace(int botJobId);

    void requestStopPreLaunchFromWorkspace(int botJobId);
}

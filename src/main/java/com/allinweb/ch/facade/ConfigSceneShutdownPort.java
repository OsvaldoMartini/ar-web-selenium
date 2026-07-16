package com.allinweb.ch.facade;

public interface ConfigSceneShutdownPort {
    void closeNewBotJob();

    void closeBotJobWorkspaceIfIdle();

    void closeOrganizationManager();

    void closeScanner();

    void closeScannerWebDrivers();

    void closeAllWebDrivers();

    void closeCurrentWebDriver();
}

package com.allinweb.ch.facade;

/** JavaFX-free shutdown ordering for configuration changes that require closing open workspaces. */
final class ConfigSceneShutdownService {
    private final ScenesPort scenes;

    ConfigSceneShutdownService(ScenesPort scenes) {
        this.scenes = scenes;
    }

    void closeAll() {
        scenes.closeNewBotJob();
        scenes.closeBotJobWorkspaceIfIdle();
        scenes.closeOrganizationManager();
        scenes.closeScanner();
        scenes.closeScannerWebDrivers();
        scenes.closeAllWebDrivers();
        scenes.closeCurrentWebDriver();
    }

    interface ScenesPort {
        void closeNewBotJob();

        void closeBotJobWorkspaceIfIdle();

        void closeOrganizationManager();

        void closeScanner();

        void closeScannerWebDrivers();

        void closeAllWebDrivers();

        void closeCurrentWebDriver();
    }
}

package com.allinweb.ch.facade;

import java.util.function.Supplier;

/** Presentation-neutral shutdown ordering for configuration changes that require closing open workspaces. */
final class ConfigSceneShutdownService {
    private final Supplier<ConfigSceneShutdownPort> scenes;

    ConfigSceneShutdownService(ConfigSceneShutdownPort scenes) {
        this(() -> scenes);
    }

    ConfigSceneShutdownService(Supplier<ConfigSceneShutdownPort> scenes) {
        this.scenes = scenes;
    }

    void closeAll() {
        ConfigSceneShutdownPort current = scenes.get();
        current.closeNewBotJob();
        current.closeBotJobWorkspaceIfIdle();
        current.closeOrganizationManager();
        current.closeScanner();
        current.closeScannerWebDrivers();
        current.closeAllWebDrivers();
        current.closeCurrentWebDriver();
    }
}

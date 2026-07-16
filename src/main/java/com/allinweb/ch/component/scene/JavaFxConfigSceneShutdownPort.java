package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.BotJobDetailsWorkspaceHost;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.facade.ConfigSceneShutdownPort;
import com.allinweb.ch.facade.ConfigSceneShutdownRegistry;
import com.allinweb.ch.facade.MainDashboardPresentationRegistry;

public final class JavaFxConfigSceneShutdownPort implements ConfigSceneShutdownPort {
    private static final JavaFxConfigSceneShutdownPort INSTANCE = new JavaFxConfigSceneShutdownPort();

    private JavaFxConfigSceneShutdownPort() {}

    public static void install() {
        ConfigSceneShutdownRegistry.getInstance().install(INSTANCE);
    }

    @Override
    public void closeNewBotJob() {
        ARNewBotJobScene.getInstance().closeModal();
    }

    @Override
    public void closeBotJobWorkspaceIfIdle() {
        BotJobDetailsWorkspaceHost.getInstance().closeWorkspaceIfIdle();
    }

    @Override
    public void closeOrganizationManager() {
        AROrganizationManagerScene.getInstance().closeModal();
    }

    @Override
    public void closeScanner() {
        MainDashboardPresentationRegistry.getInstance().current().closeScanner();
    }

    @Override
    public void closeScannerWebDrivers() {
        MainDashboardPresentationRegistry.getInstance().current().closeScannerWebDrivers();
    }

    @Override
    public void closeAllWebDrivers() {
        ARWebDriver.getInstance().closeAllDrivers();
    }

    @Override
    public void closeCurrentWebDriver() {
        ARWebDriver.getInstance().closeCurrentDriver();
    }
}

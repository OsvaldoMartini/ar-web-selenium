package com.allinweb.ch.facade;

import com.allinweb.ch.component.pane.BotJobDetailsWorkspaceHost;
import com.allinweb.ch.driver.ARWebDriver;

public final class DefaultConfigSceneShutdownPort implements ConfigSceneShutdownPort {
    private static final DefaultConfigSceneShutdownPort INSTANCE = new DefaultConfigSceneShutdownPort();

    private DefaultConfigSceneShutdownPort() {}

    public static void install() {
        ConfigSceneShutdownRegistry.getInstance().install(INSTANCE);
    }

    @Override
    public void closeNewBotJob() {
        NewBotJobPresentationRegistry.getInstance().current().closeModal();
    }

    @Override
    public void closeBotJobWorkspaceIfIdle() {
        ConfigPresentationRegistry.getInstance().current().closeModal();
        BotJobDetailsWorkspaceHost.getInstance().closeWorkspaceIfIdle();
    }

    @Override
    public void closeOrganizationManager() {
        OrganizationManagerLifecycle.getInstance().closeModal();
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
        ARWebDriver.getInstance().closeBrowser();
    }

    @Override
    public void closeCurrentWebDriver() {
        ARWebDriver.getInstance().closeBrowser();
    }
}

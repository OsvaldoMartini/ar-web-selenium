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
        // Detached New Bot Job pages are retired after a successful database reload by
        // PagesOpenWorkspaceService. Calling the shared presentation close action here can
        // navigate the Configuration/TEMP requester back to the Main Dashboard.
    }

    @Override
    public void closeBotJobWorkspaceIfIdle() {
        // Configuration/TEMP owns the database reload request and must remain open to receive the
        // completed save/restore response. Only the Bot Job workspace is retired here.
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

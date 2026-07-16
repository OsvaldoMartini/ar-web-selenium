package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ConfigSceneShutdownRegistryTest {
    private final ConfigSceneShutdownRegistry registry = ConfigSceneShutdownRegistry.getInstance();

    @AfterEach
    void resetRegistry() {
        registry.reset();
    }

    @Test
    void installsCurrentPort() {
        ConfigSceneShutdownPort port = new RecordingPort();

        registry.install(port);

        assertSame(port, registry.current());
    }

    @Test
    void defaultPortIsNoop() {
        registry.reset();

        registry.current().closeNewBotJob();
        registry.current().closeBotJobWorkspaceIfIdle();
        registry.current().closeOrganizationManager();
        registry.current().closeScanner();
        registry.current().closeScannerWebDrivers();
        registry.current().closeAllWebDrivers();
        registry.current().closeCurrentWebDriver();
    }

    private static final class RecordingPort implements ConfigSceneShutdownPort {
        @Override
        public void closeNewBotJob() {}

        @Override
        public void closeBotJobWorkspaceIfIdle() {}

        @Override
        public void closeOrganizationManager() {}

        @Override
        public void closeScanner() {}

        @Override
        public void closeScannerWebDrivers() {}

        @Override
        public void closeAllWebDrivers() {}

        @Override
        public void closeCurrentWebDriver() {}
    }
}

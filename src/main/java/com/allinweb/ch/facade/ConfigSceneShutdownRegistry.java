package com.allinweb.ch.facade;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class ConfigSceneShutdownRegistry {
    private static final ConfigSceneShutdownRegistry INSTANCE = new ConfigSceneShutdownRegistry();

    private final AtomicReference<ConfigSceneShutdownPort> port =
            new AtomicReference<>(new NoopConfigSceneShutdownPort());

    private ConfigSceneShutdownRegistry() {}

    public static ConfigSceneShutdownRegistry getInstance() {
        return INSTANCE;
    }

    public void install(ConfigSceneShutdownPort port) {
        this.port.set(Objects.requireNonNull(port, "port"));
    }

    public void reset() {
        port.set(new NoopConfigSceneShutdownPort());
    }

    public ConfigSceneShutdownPort current() {
        return port.get();
    }

    private static final class NoopConfigSceneShutdownPort implements ConfigSceneShutdownPort {
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

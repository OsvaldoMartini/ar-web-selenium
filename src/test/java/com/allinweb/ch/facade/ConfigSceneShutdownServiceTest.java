package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigSceneShutdownServiceTest {

    @Test
    void closesScenesAndDriversInLegacyOrder() {
        RecordingScenes scenes = new RecordingScenes();
        ConfigSceneShutdownService service = new ConfigSceneShutdownService(scenes);

        service.closeAll();

        assertEquals(
                List.of(
                        "newBotJob",
                        "botJobWorkspace",
                        "organizationManager",
                        "scanner",
                        "scannerWebDrivers",
                        "allWebDrivers",
                        "currentWebDriver"),
                scenes.calls);
    }

    private static final class RecordingScenes implements ConfigSceneShutdownPort {
        private final List<String> calls = new ArrayList<>();

        @Override
        public void closeNewBotJob() {
            calls.add("newBotJob");
        }

        @Override
        public void closeBotJobWorkspaceIfIdle() {
            calls.add("botJobWorkspace");
        }

        @Override
        public void closeOrganizationManager() {
            calls.add("organizationManager");
        }

        @Override
        public void closeScanner() {
            calls.add("scanner");
        }

        @Override
        public void closeScannerWebDrivers() {
            calls.add("scannerWebDrivers");
        }

        @Override
        public void closeAllWebDrivers() {
            calls.add("allWebDrivers");
        }

        @Override
        public void closeCurrentWebDriver() {
            calls.add("currentWebDriver");
        }
    }
}

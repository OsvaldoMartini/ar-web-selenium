package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertSame;

import com.allinweb.ch.model.BotJobLoadDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MainDashboardPresentationRegistryTest {
    private final MainDashboardPresentationRegistry registry = MainDashboardPresentationRegistry.getInstance();

    @AfterEach
    void resetRegistry() {
        registry.reset();
    }

    @Test
    void installsCurrentPresentation() {
        MainDashboardPresentation presentation = new RecordingPresentation();

        registry.install(presentation);

        assertSame(presentation, registry.current());
    }

    @Test
    void defaultPresentationIsNoop() {
        registry.reset();

        registry.current().openOrganizations();
    }

    private static final class RecordingPresentation implements MainDashboardPresentation {
        @Override
        public void openOrganizations() {}

        @Override
        public void openNewBotJob() {}

        @Override
        public void openCloneBotJob(BotJobLoadDTO botJob) {}

        @Override
        public void openCloneOrganizations() {}

        @Override
        public void closeCloneJob() {}

        @Override
        public void closeScanner() {}

        @Override
        public void closeScannerWebDrivers() {}

        @Override
        public void openBotJob(BotJobLoadDTO botJob) {}

        @Override
        public void openConfig() {}

        @Override
        public void openInfo() {}

        @Override
        public void exitApplication() {}

        @Override
        public void launchBotJob(BotJobLoadDTO botJob) {}
    }
}

package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.BotJobLoadDTO;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MainDashboardServiceShutdownTest {
    private final MainDashboardPresentationRegistry registry =
            MainDashboardPresentationRegistry.getInstance();

    @AfterEach
    void resetPresentation() {
        registry.reset();
    }

    @Test
    void exitRouteDelegatesToTheInstalledApplicationShutdownBoundary() {
        RecordingPresentation presentation = new RecordingPresentation();
        registry.install(presentation);

        Map<String, Object> response = MainDashboardService.getInstance().exit();

        assertTrue((Boolean) response.get("ok"));
        assertEquals("Exit requested", response.get("message"));
        assertEquals(1, presentation.exitRequests.get());
    }

    private static final class RecordingPresentation implements MainDashboardPresentation {
        private final AtomicInteger exitRequests = new AtomicInteger();

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
        public void openTemplate() {}

        @Override
        public void openInfo() {}

        @Override
        public void exitApplication() {
            exitRequests.incrementAndGet();
        }

        @Override
        public void launchBotJob(BotJobLoadDTO botJob) {}
    }
}

package com.allinweb.ch.facade;

import com.allinweb.ch.model.BotJobLoadDTO;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class MainDashboardPresentationRegistry {
    private static final MainDashboardPresentationRegistry INSTANCE = new MainDashboardPresentationRegistry();

    private final AtomicReference<MainDashboardPresentation> presentation =
            new AtomicReference<>(new NoopMainDashboardPresentation());

    private MainDashboardPresentationRegistry() {}

    public static MainDashboardPresentationRegistry getInstance() {
        return INSTANCE;
    }

    public void install(MainDashboardPresentation presentation) {
        this.presentation.set(Objects.requireNonNull(presentation, "presentation"));
    }

    public void reset() {
        presentation.set(new NoopMainDashboardPresentation());
    }

    public MainDashboardPresentation current() {
        return presentation.get();
    }

    private static final class NoopMainDashboardPresentation implements MainDashboardPresentation {
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

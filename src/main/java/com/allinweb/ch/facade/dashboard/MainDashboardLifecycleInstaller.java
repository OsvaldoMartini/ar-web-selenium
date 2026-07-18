package com.allinweb.ch.facade.dashboard;

import com.allinweb.ch.component.pane.MainDashboardPresentationAdapter;
import com.allinweb.ch.facade.MainShellLifecycle;

public final class MainDashboardLifecycleInstaller {
    private static final MainDashboardPresentationAdapter presentation = MainDashboardPresentationAdapter.getInstance();

    private MainDashboardLifecycleInstaller() {}

    public static void install() {
        MainShellLifecycle.getInstance().install(new MainDashboardHandler());
    }

    private static final class MainDashboardHandler implements MainShellLifecycle.Handler {
        @Override
        public void openMain(boolean enabledLicence) {
            presentation.initialize(enabledLicence);
        }

        @Override
        public void openMain(boolean enabledLicence, String initialSessionId) {
            presentation.initialize(enabledLicence, initialSessionId);
        }
    }
}

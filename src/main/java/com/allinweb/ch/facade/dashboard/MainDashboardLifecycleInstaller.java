package com.allinweb.ch.facade.dashboard;

import com.allinweb.ch.component.pane.MainDashboardPresentationAdapter;
import com.allinweb.ch.facade.MainShellLifecycle;
import java.util.ArrayList;
import java.util.List;
import org.openqa.selenium.WebDriver;

public final class MainDashboardLifecycleInstaller {
    private static final MainDashboardPresentationAdapter presentation = MainDashboardPresentationAdapter.getInstance();
    private static final List<WebDriver> webDriverList = new ArrayList<>();

    private MainDashboardLifecycleInstaller() {}

    public static void install() {
        MainShellLifecycle.getInstance().install(new MainDashboardHandler());
    }

    private static final class MainDashboardHandler implements MainShellLifecycle.Handler {
        @Override
        public void openMain(boolean enabledLicence) {
            presentation.initialize(webDriverList, enabledLicence);
        }

        @Override
        public void openMain(boolean enabledLicence, String initialSessionId) {
            presentation.initialize(webDriverList, enabledLicence, initialSessionId);
        }
    }
}

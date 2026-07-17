package com.allinweb.ch.component.pane;

import com.allinweb.ch.facade.MainShellLifecycle;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.openqa.selenium.WebDriver;

public final class MainDashboardLifecycleInstaller {
    private static final MainDashboardPresentationAdapter presentation = MainDashboardPresentationAdapter.getInstance();
    private static final ObservableList<WebDriver> webDriverList = FXCollections.observableArrayList();

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

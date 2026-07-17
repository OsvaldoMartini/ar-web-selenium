package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.MainDashboardLifecycleInstaller;
import com.allinweb.ch.facade.UiThreadDispatcher;
import javafx.application.Platform;

/** Installs lifecycle adapters owned by the legacy JavaFX shell. */
public final class JavaFxShellBootstrap {
    private JavaFxShellBootstrap() {}

    public static void install() {
        UiThreadDispatcher.getInstance().install(Platform::runLater);
        MainDashboardLifecycleInstaller.install();
        ARScannedElementScene.getInstance();
    }
}

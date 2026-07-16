package com.allinweb.ch.component.scene;

/** Installs lifecycle adapters owned by the legacy JavaFX shell. */
public final class JavaFxShellBootstrap {
    private JavaFxShellBootstrap() {}

    public static void install() {
        ARConfigManagerScene.getInstance();
        ARMainScene.getInstance();
        ARNewBotJobManagerScene.getInstance();
        AROrganizationManagerScene.getInstance();
    }
}

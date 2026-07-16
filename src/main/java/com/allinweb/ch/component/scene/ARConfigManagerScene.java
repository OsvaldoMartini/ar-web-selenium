package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ARConfigManagerPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import com.allinweb.ch.facade.ConfigManagerLifecycle;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ARConfigManagerScene extends ARScene {

    private static final ARConfigManagerPane configManagerPane = ARConfigManagerPane.getInstance();
    private static final Double SCENE_HEIGHT = 760D;
    private static final Double SCENE_WIDTH = 1120D;
    private static final String TITLE = "Configuration";

    protected static volatile ARConfigManagerScene instance;

    private Stage modalStage;
    private Scene modalScene;
    private boolean isEnabledLicence;

    private ARConfigManagerScene() {
        super();
        ConfigManagerLifecycle.getInstance().install(new SceneConfigManagerHandler());
    }

    public static ARConfigManagerScene getInstance() {
        if (instance == null) {
            synchronized (ARConfigManagerScene.class) {
                if (instance == null) {
                    instance = new ARConfigManagerScene();
                }
            }
        }
        return instance;
    }

    public void initialize(boolean isEnabledLicence) {
        this.isEnabledLicence = isEnabledLicence;
    }

    public void showModal() {
        showModal(null);
    }

    public void showModal(Stage parentStage) {
        if (modalStage == null) {
            modalStage = new Stage();
            modalStage.getIcons().add(icon);
            configManagerPane.initialize(modalStage, isEnabledLicence);
            IARPane pane = buildPane();
            if (pane == null) {
                log.error("Failed to build Config manager pane.");
                return;
            }
            modalScene = new Scene(pane.createPane(), getSceneWidth(), getSceneHeight());
            modalStage.setScene(modalScene);
            modalStage.setTitle(getTitle());
            if (parentStage != null) {
                modalStage.initOwner(parentStage);
            }
            modalStage.initModality(Modality.NONE);
            modalStage.setAlwaysOnTop(true);
            modalStage.setOnShown(event -> Platform.runLater(() -> modalStage.setAlwaysOnTop(false)));
        } else {
            configManagerPane.initialize(modalStage, isEnabledLicence);
        }

        if (!modalStage.isShowing()) {
            modalStage.show();
            modalStage.toFront();
            modalStage.setAlwaysOnTop(false);
        } else {
            modalStage.requestFocus();
        }
    }

    public void closeModal() {
        if (modalStage != null && modalStage.isShowing()) {
            modalStage.close();
        }
    }

    @Override
    public IARPane buildPane() {
        return configManagerPane;
    }

    @Override
    public Double getSceneHeight() {
        return SCENE_HEIGHT;
    }

    @Override
    public Double getSceneWidth() {
        return SCENE_WIDTH;
    }

    @Override
    public String getTitle() {
        return TITLE;
    }

    private final class SceneConfigManagerHandler implements ConfigManagerLifecycle.Handler {
        @Override
        public void openConfig(boolean enabledLicence) {
            ARConfigManagerScene.this.initialize(enabledLicence);
            ARConfigManagerScene.this.showModal();
        }

        @Override
        public void closeModal() {
            ARConfigManagerScene.this.closeModal();
        }
    }
}

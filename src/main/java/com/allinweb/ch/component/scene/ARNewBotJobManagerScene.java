package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ARNewBotJobManagerPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ARNewBotJobManagerScene extends ARScene {

    private static final ARNewBotJobManagerPane newBotJobManagerPane = ARNewBotJobManagerPane.getInstance();
    private static final Double SCENE_HEIGHT = 520D;
    private static final Double SCENE_WIDTH = 760D;
    private static final String TITLE = "New Bot Job";

    protected static volatile ARNewBotJobManagerScene instance;

    private Stage modalStage;
    private Scene modalScene;
    private boolean isEnabledLicence;

    private ARNewBotJobManagerScene() {
        super();
    }

    public static ARNewBotJobManagerScene getInstance() {
        if (instance == null) {
            synchronized (ARNewBotJobManagerScene.class) {
                if (instance == null) {
                    instance = new ARNewBotJobManagerScene();
                }
            }
        }
        return instance;
    }

    public void initialize(boolean isEnabledLicence) {
        this.isEnabledLicence = isEnabledLicence;
    }

    public void showModal(Stage parentStage) {
        if (modalStage == null) {
            modalStage = new Stage();
            modalStage.getIcons().add(icon);
            newBotJobManagerPane.initialize(modalStage, isEnabledLicence);
            IARPane pane = buildPane();
            if (pane == null) {
                log.error("Failed to build New Bot Job manager pane.");
                return;
            }
            modalScene = new Scene(pane.createPane(), getSceneWidth(), getSceneHeight());
            modalStage.setScene(modalScene);
            modalStage.setTitle(getTitle());
            modalStage.initOwner(parentStage);
            modalStage.initModality(Modality.NONE);
            modalStage.setAlwaysOnTop(true);
            modalStage.setOnShown(event -> Platform.runLater(() -> modalStage.setAlwaysOnTop(false)));
        } else {
            newBotJobManagerPane.initialize(modalStage, isEnabledLicence);
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
        return newBotJobManagerPane;
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
}

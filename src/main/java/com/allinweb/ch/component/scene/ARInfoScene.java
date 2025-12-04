package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ARInfoPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ARInfoScene extends ARScene {

    private static final Double SCENE_HEIGHT = 300D;
    private static final Double SCENE_WIDTH = 400D;
    private static final String TITLE = "About";
    protected static volatile ARInfoScene instance;
    private static ARInfoPane arInfoPane;

    static {
        arInfoPane = ARInfoPane.getInstance();
    }

    private Stage modalStage;
    private Scene modalScene;
    private boolean isEnabledLicence;
    // Private constructor to prevent instantiation
    private ARInfoScene() {

        super();
    }

    public static ARInfoScene getInstance() {
        if (instance == null) {
            synchronized (ARInfoScene.class) {
                if (instance == null) {
                    instance = new ARInfoScene();
                }
            }
        }
        return instance;
    }

    @Override
    public IARPane buildPane() {
        return arInfoPane;
    }

    @Override
    public int getSceneHeight() {
        return SCENE_HEIGHT;
    }

    @Override
    public int getSceneWidth() {
        return SCENE_WIDTH;
    }

    @Override
    public String getTitle() {
        return TITLE;
    }

    public void showModal() {

        arInfoPane.initialize(isEnabledLicence);

        if (modalStage == null) {
            modalStage = new Stage();
            modalStage.getIcons().add(icon);
            IARPane pane = buildPane();
            if (pane != null) {
                modalScene = new Scene(pane.createPane(), getSceneWidth(), getSceneHeight());
                modalStage.setScene(modalScene);
                modalStage.setTitle(getTitle());
                modalStage.initModality(Modality.WINDOW_MODAL);
                modalStage.setAlwaysOnTop(true); // Set always on top
                modalStage.toFront();
                // Reset alwaysOnTop after showing so it behaves normally afterward
                modalStage.setAlwaysOnTop(false);

                // Once shown, reset AlwaysOnTop to false so it behaves normally
                modalStage.setOnShown(event -> {
                    Platform.runLater(() -> modalStage.setAlwaysOnTop(false));
                });

            } else {
                // Handle the case where pane creation failed
                log.error("Failed to build pane for modal.");
                return;
            }
        }
        modalStage.setTitle(getTitle()); // Update title if it might have changed
        // Check if the stage is already showing
        if (!modalStage.isShowing()) {
            modalStage.showAndWait(); // Show and wait only if not already showing
        }
    }
}

package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ARInfoPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import com.allinweb.ch.util.ARLogger;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ARInfoScene extends ARScene {

    protected static volatile ARInfoScene instance;

    // Private constructor to prevent instantiation
    private ARInfoScene() {
        // Initialize if necessary
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

    private Stage modalStage;
    private Scene modalScene;

    private static ARInfoPane arInfoPane;

    static {
        arInfoPane = ARInfoPane.getInstance();
    }

    private boolean isEnabledLicence;
    private static final Double SCENE_HEIGHT = 300D;
    private static final Double SCENE_WIDTH = 400D;
    private static final String TITLE = "About";

    @Override
    public IARPane buildPane() {
        return arInfoPane;
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

    public void showModal() {

        arInfoPane.initialize(isEnabledLicence);

        if (modalStage == null) {
            modalStage = new Stage();
            IARPane pane = buildPane();
            if (pane != null) {
                modalScene = new Scene(pane.createPane(), getSceneWidth(), getSceneHeight());
                modalStage.setScene(modalScene);
                modalStage.setTitle(getTitle());
                modalStage.initModality(Modality.WINDOW_MODAL); // Changed to NONE
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
                ARLogger.getInstance(ARInfoScene.class).severe("Failed to build pane for modal.");
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

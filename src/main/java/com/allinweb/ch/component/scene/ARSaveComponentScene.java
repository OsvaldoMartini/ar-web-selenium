package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.model.BlockDetailsDTO;
import com.allinweb.ch.component.pane.ARSaveComponentPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import com.allinweb.ch.util.ARLogger;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ARSaveComponentScene extends ARScene {

    protected static volatile ARSaveComponentScene instance;

    // Private constructor to prevent instantiation
    private ARSaveComponentScene() {

        super();
    }

    public static ARSaveComponentScene getInstance() {
        if (instance == null) {
            synchronized (ARSaveComponentScene.class) {
                if (instance == null) {
                    instance = new ARSaveComponentScene();
                }
            }
        }
        return instance;
    }

    private Stage modalStage;
    private Scene modalScene;

    private static ARSaveComponentPane arSaveComponentPane;

    static {
        arSaveComponentPane = ARSaveComponentPane.getInstance();
    }

    private static final Double SCENE_HEIGHT = 250D;
    private static final Double SCENE_WIDTH = 600D;
    private static String TITLE = "Move Block";

    private BlockDetailsDTO blockDetailsDTO;

    public void initialize(BlockDetailsDTO blockDetailsDTO) {
        this.blockDetailsDTO = blockDetailsDTO;
        TITLE = "Save Block:  Comp - " + blockDetailsDTO.getBlockName();
    }

    public void showModal() {

        arSaveComponentPane.initialize(blockDetailsDTO);

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
                ARLogger.getInstance(ARSaveComponentScene.class).severe("Failed to build pane for modal.");
                return;
            }
        }

        modalStage.setTitle(getTitle());

        // Check if the stage is already showing
        if (!modalStage.isShowing()) {
            modalStage.showAndWait(); // Show and wait only if not already showing
        }
    }

    @Override
    public IARPane buildPane() {
        //        arSaveComponentPane.initialize(blockDetailsDTO);
        return arSaveComponentPane;
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

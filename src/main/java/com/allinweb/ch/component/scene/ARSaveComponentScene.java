package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.model.BlockDetailsDTO;
import com.allinweb.ch.component.pane.ARSaveComponentPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import com.allinweb.ch.util.ARLogger;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ARSaveComponentScene extends ARScene {

    protected static volatile ARSaveComponentScene instance;

    // Private constructor to prevent instantiation
    private ARSaveComponentScene() {
        // Initialize if necessary
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

    public ARSaveComponentScene(BlockDetailsDTO blockDetailsDTO) {
        this.blockDetailsDTO = blockDetailsDTO;
        TITLE = "Save Block:  Comp - " + blockDetailsDTO.getBlockName();
    }

    public void showModal() {
        if (modalStage == null) {
            modalStage = new Stage();
            IARPane pane = buildPane();
            if (pane != null) {
                modalScene = new Scene(pane.createPane(), getSceneWidth(), getSceneHeight());
                modalStage.setScene(modalScene);
                modalStage.setTitle(getTitle());
                modalStage.initModality(Modality.WINDOW_MODAL); // Changed to NONE
                modalStage.setAlwaysOnTop(true); // Set always on top
            } else {
                // Handle the case where pane creation failed
                ARLogger.getInstance(ARNewCommandScene.class).severe("Failed to build pane for modal.");
                return;
            }
        } else {
            arSaveComponentPane.initialize(blockDetailsDTO);
            modalStage.setTitle(getTitle()); // Update title if it might have changed
        }
        //        modalStage.show(); // Block until this window is closed
        modalStage.showAndWait(); // Block until this window is closed
    }

    @Override
    public IARPane buildPane() {
        arSaveComponentPane.initialize(blockDetailsDTO);
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

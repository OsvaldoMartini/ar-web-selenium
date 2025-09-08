package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.model.SplitDTO;
import com.allinweb.ch.component.pane.ARExcelFilePane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ARExcelFileScene extends ARScene {

    protected static volatile ARExcelFileScene instance;

    // Private constructor to prevent instantiation
    private ARExcelFileScene() {

        super();
    }

    public static ARExcelFileScene getInstance() {
        if (instance == null) {
            synchronized (ARExcelFileScene.class) {
                if (instance == null) {
                    instance = new ARExcelFileScene();
                }
            }
        }
        return instance;
    }

    public void initialize(String sessionId, SplitDTO splitDTO) {
        this.sessionId = sessionId;
        this.splitDTO = splitDTO;
    }

    private Stage modalStage;
    private Scene modalScene;

    private static ARExcelFilePane arExcelFilePane = ARExcelFilePane.getInstance();

    private static final Double SCENE_HEIGHT = 300D;
    private static final Double SCENE_WIDTH = 800D;
    private static final String TITLE = "Create or Delete the Export Excel File";
    private SplitDTO splitDTO;
    private String sessionId;

    @Override
    public IARPane buildPane() {
        //        arExcelFilePane.initialize(sessionId, blockExcelDTO, modalStage);
        return arExcelFilePane;
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

        arExcelFilePane.initialize(sessionId, splitDTO, modalStage);

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

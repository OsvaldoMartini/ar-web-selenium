package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.model.RowMoveDTO;
import com.allinweb.ch.component.pane.ARElementValuePane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import com.allinweb.ch.util.ARLogger;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ARElementValueScene extends ARScene {

    private static final Double SCENE_HEIGHT = 500D;
    private static final Double SCENE_WIDTH = 300D;
    private static final String TITLE = "New Variables";
    private RowMoveDTO rowMoveDTO;
    private int instructionId;
    private String instructionName;
    private String varName;
    private String instructionType;

    private Stage modalStage;
    private Scene modalScene;

    private static ARElementValuePane arElementValuePane;

    static {
        arElementValuePane = ARElementValuePane.getInstance();
    }

    public ARElementValueScene(
            RowMoveDTO rowMoveDTO, int instructionId, String instructionName, String varName, String instructionType) {
        super();
        this.rowMoveDTO = rowMoveDTO;
        this.instructionId = instructionId;
        this.instructionName = instructionName;
        this.varName = varName;
        this.instructionType = instructionType;
    }

    @Override
    public IARPane buildPane() {
        arElementValuePane.initialize(rowMoveDTO, instructionId, instructionName, varName, instructionType);
        return arElementValuePane;
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
        if (modalStage == null) {
            modalStage = new Stage();
            IARPane pane = buildPane();
            if (pane != null) {
                modalScene = new Scene(pane.createPane(), getSceneWidth(), getSceneHeight());
                modalStage.setScene(modalScene);
                modalStage.setTitle(getTitle());
                if (getTitle().equalsIgnoreCase("New Variables")) {
                    modalStage.initModality(Modality.WINDOW_MODAL); // Changed to NONE
                } else {
                    modalStage.initModality(Modality.NONE);
                }
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
                ARLogger.getInstance(ARElementValueScene.class).severe("Failed to build pane for modal.");
                return;
            }
        } else {
            arElementValuePane.initialize(rowMoveDTO, instructionId, instructionName, varName, instructionType);
            modalStage.setTitle(getTitle()); // Update title if it might have changed
        }
        //        modalStage.show(); // Block until this window is closed
        modalStage.showAndWait(); // Block until this window is closed
    }
}

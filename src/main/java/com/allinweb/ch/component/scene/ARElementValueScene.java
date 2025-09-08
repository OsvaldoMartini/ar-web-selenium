package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.model.SplitDTO;
import com.allinweb.ch.component.pane.ARElementValuePane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ARElementValueScene extends ARScene {

    private static final Double SCENE_HEIGHT = 600D;
    private static final Double SCENE_WIDTH = 600D;
    private static final String TITLE = "New Variables";
    protected static volatile ARElementValueScene instance;
    private static ARElementValuePane arElementValuePane = ARElementValuePane.getInstance();
    public boolean closeCalled;

    @Getter
    @Setter
    public SplitDTO splitDTO;

    private Stage modalStage;
    private Scene modalScene;
    private int varId;
    private String varValue;
    private int instructionId;
    private String instructionName;
    private String varName;
    private String instructionType;
    private boolean firstLoad = true;
    // Private constructor to prevent instantiation
    private ARElementValueScene() {

        super();
    }

    public static ARElementValueScene getInstance() {
        if (instance == null) {
            synchronized (ARElementValueScene.class) {
                if (instance == null) {
                    instance = new ARElementValueScene();
                }
            }
        }
        return instance;
    }

    public void initialize(
            SplitDTO splitDTO,
            int varId,
            String varName,
            String varValue,
            int instructionId,
            String instructionName,
            String instructionType) {
        this.splitDTO = splitDTO;
        this.varId = varId;
        this.varName = varName;
        this.varValue = varValue;
        this.instructionId = instructionId;
        this.instructionName = instructionName;
        this.instructionType = instructionType;

        if (!firstLoad) {
            arElementValuePane.initialize(
                    splitDTO, varId, varValue, instructionId, instructionName, varName, instructionType);
        }
    }

    @Override
    public IARPane buildPane() {
        //        arElementValuePane.initialize(rowMoveDTO, varId, instructionId, instructionName, varName,
        // instructionType);
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

        firstLoad = false;

        arElementValuePane.initialize(
                splitDTO, varId, varValue, instructionId, instructionName, varName, instructionType);

        if (modalStage == null) {
            modalStage = new Stage();
            modalStage.getIcons().add(icon);
            IARPane pane = buildPane();
            if (pane != null) {
                modalScene = new Scene(pane.createPane(), getSceneWidth(), getSceneHeight());
                modalStage.setScene(modalScene);
                modalStage.setTitle(getTitle());
                if (getTitle().equalsIgnoreCase("New Variables")) {
                    modalStage.initModality(Modality.WINDOW_MODAL);
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

    public void closeModal() {
        try {
            if (modalStage != null) { // && modalStage.isShowing()) {
                modalStage.close();
            }
            modalStage = null;
            closeCalled = true;
        } catch (Exception error) {
            closeCalled = true;
        }
    }

    public void setTableRowById(Integer varId) {
        arElementValuePane.selectRowById(varId);
    }
}

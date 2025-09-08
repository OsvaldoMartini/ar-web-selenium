package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.pane.ARSaveClonePane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;

import java.util.List;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ARSaveCloneScene extends ARScene {

    protected static volatile ARSaveCloneScene instance;

    // Private constructor to prevent instantiation
    private ARSaveCloneScene() {

        super();
    }

    public static ARSaveCloneScene getInstance() {
        if (instance == null) {
            synchronized (ARSaveCloneScene.class) {
                if (instance == null) {
                    instance = new ARSaveCloneScene();
                }
            }
        }
        return instance;
    }

    private Stage modalStage;
    private Scene modalScene;

    private boolean isEnabledLicence;

    private static final ARSaveClonePane arSaveClonePane;

    static {
        arSaveClonePane = ARSaveClonePane.getInstance();
    }

    private static final Double SCENE_HEIGHT = 450D;
    private static final Double SCENE_WIDTH = 800D;
    private static final String TITLE = "Clone Job";

    private BotJobLoadDTO selecBotJobDTO;
    private List<BotJobLoadDTO> botJobList;

    public void initialize(BotJobLoadDTO selecBotJobDTO, List<BotJobLoadDTO> botJobList, boolean isEnabledLicence) {
        this.isEnabledLicence = isEnabledLicence;
        this.selecBotJobDTO = selecBotJobDTO;
        this.botJobList = botJobList;
    }

    @Override
    public IARPane buildPane() {
        //        arSaveClonePane.initialize(selecBotJobDTO, botJobList);
        return arSaveClonePane;
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

    public void showModal(Stage primareStage) {

        arSaveClonePane.initialize(selecBotJobDTO, botJobList, isEnabledLicence);

        if (modalStage == null) {
            modalStage = new Stage();
            modalStage.getIcons().add(icon);
            IARPane pane = buildPane();
            if (pane != null) {
                modalScene = new Scene(pane.createPane(), getSceneWidth(), getSceneHeight());
                modalStage.setScene(modalScene);
                modalStage.setTitle(getTitle());
                modalStage.initOwner(primareStage);
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

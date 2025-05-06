package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.model.HomeBankingLoadDTO;
import com.allinweb.ch.component.pane.ARNewHomeBankingPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import com.allinweb.ch.util.ARLogger;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ARNewHomeBankingScene extends ARScene {

    protected static volatile ARNewHomeBankingScene instance;

    // Private constructor to prevent instantiation
    private ARNewHomeBankingScene() {
        // Initialize if necessary
        super();
    }

    public static ARNewHomeBankingScene getInstance() {
        if (instance == null) {
            synchronized (ARNewHomeBankingScene.class) {
                if (instance == null) {
                    instance = new ARNewHomeBankingScene();
                }
            }
        }
        return instance;
    }

    private Stage modalStage;
    private Scene modalScene;

    private static final ARNewHomeBankingPane arNewHomeBankingPane;

    static {
        arNewHomeBankingPane = ARNewHomeBankingPane.getInstance();
    }

    private static final Double SCENE_HEIGHT = 750D;
    private static final Double SCENE_WIDTH = 1200D;
    private static final String TITLE = "New Url";

    private ObservableList<HomeBankingLoadDTO> homeBankingList;

    public void initialize(ObservableList<HomeBankingLoadDTO> homeBankingList) {
        this.homeBankingList = homeBankingList;
    }

    public void showModal() {
        if (modalStage == null) {
            modalStage = new Stage();
            IARPane pane = buildPane();
            if (pane != null) {
                modalScene = new Scene(pane.createPane(), getSceneWidth(), getSceneHeight());
                modalStage.setScene(modalScene);
                modalStage.setTitle(getTitle());
                modalStage.initModality(Modality.NONE); // Changed to NONE
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
                ARLogger.getInstance(ARNewCommandScene.class).severe("Failed to build pane for modal.");
                return;
            }
        } else {
            arNewHomeBankingPane.initialize(homeBankingList);
            modalStage.setTitle(getTitle()); // Update title if it might have changed
        }
        modalStage.show(); // Block until this window is closed
        //        modalStage.showAndWait(); // Block until this window is closed
    }

    @Override
    public IARPane buildPane() {
        arNewHomeBankingPane.initialize(homeBankingList);
        return arNewHomeBankingPane;
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

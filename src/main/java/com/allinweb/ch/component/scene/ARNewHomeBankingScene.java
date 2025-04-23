package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.model.HomeBankingLoadDTO;
import com.allinweb.ch.component.pane.ARNewHomeBankingPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
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

    private static final Double SCENE_HEIGHT = 750D;
    private static final Double SCENE_WIDTH = 1200D;
    private static final String TITLE = "New Url";

    private ObservableList<HomeBankingLoadDTO> homeBankingList;

    public void initialize(ObservableList<HomeBankingLoadDTO> homeBankingList) {
        this.homeBankingList = homeBankingList;
    }

    public void showModal() {
        Stage modalStage = new Stage();
        IARPane pane = buildPane();
        if (pane != null) {
            Scene scene = new Scene(pane.createPane(), getSceneWidth(), getSceneHeight());
            modalStage.setScene(scene);
            modalStage.setTitle(getTitle());
            modalStage.initModality(Modality.APPLICATION_MODAL); // Make it modal
            modalStage.showAndWait(); // Block until this window is closed
        }
    }

    @Override
    public IARPane buildPane() {
        return new ARNewHomeBankingPane(homeBankingList);
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

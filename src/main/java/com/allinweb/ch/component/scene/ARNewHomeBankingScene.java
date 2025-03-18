package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.model.HomeBankingLoadDTO;
import com.allinweb.ch.component.pane.ARNewHomeBankingPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import com.allinweb.ch.facade.SingletonSupplier;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ARNewHomeBankingScene extends ARScene {
    private static final Double SCENE_HEIGHT = 750D;
    private static final Double SCENE_WIDTH = 1200D;
    private static final String TITLE = "New Url";

    protected static final SingletonSupplier<ARNewHomeBankingScene> instance = () -> new ARNewHomeBankingScene();

    private ObservableList<HomeBankingLoadDTO> homeBankingList;

    // Private constructor to prevent instantiation
    public ARNewHomeBankingScene() {
        // Initialize if necessary
        super();
    }

    // Public method to access the singleton instance
    public static ARNewHomeBankingScene getInstance() {
        return instance.get();
    }

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

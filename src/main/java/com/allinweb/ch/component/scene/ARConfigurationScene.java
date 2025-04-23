package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ARConfigurationPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import java.time.format.DateTimeFormatter;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ARConfigurationScene extends ARScene {

    protected static volatile ARConfigurationScene instance;

    // Private constructor to prevent instantiation
    private ARConfigurationScene() {
        // Initialize if necessary
        super();
    }

    public static ARConfigurationScene getInstance() {
        if (instance == null) {
            synchronized (ARConfigurationScene.class) {
                if (instance == null) {
                    instance = new ARConfigurationScene();
                }
            }
        }
        return instance;
    }

    private static final Double SCENE_HEIGHT = 700D;
    private static final Double SCENE_WIDTH = 800D;
    private static final String TITLE = "Configuration";
    // Static final variable to hold the singleton instance
    private static final DateTimeFormatter FORMAT_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    // Private constructor to prevent instantiation

    @Override
    public IARPane buildPane() {
        return new ARConfigurationPane();
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

    public void initialize() {}
}

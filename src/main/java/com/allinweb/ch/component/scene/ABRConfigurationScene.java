package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ABRConfigurationPane;
import com.allinweb.ch.component.pane.base.IABRPane;
import com.allinweb.ch.component.scene.base.ABRScene;
import com.allinweb.ch.facade.SingletonSupplier;
import java.time.format.DateTimeFormatter;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ABRConfigurationScene extends ABRScene {

    private static final Double SCENE_HEIGHT = 700D;
    private static final Double SCENE_WIDTH = 800D;
    private static final String TITLE = "Configuration";
    // Static final variable to hold the singleton instance
    protected static final SingletonSupplier<ABRConfigurationScene> instance = () -> new ABRConfigurationScene();

    private static final DateTimeFormatter FORMAT_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    // Private constructor to prevent instantiation

    public ABRConfigurationScene() {
        // Initialize if necessary
        super();
    }

    // Public method to access the singleton instance
    public static ABRConfigurationScene getInstance() {
        return instance.get();
    }

    @Override
    public IABRPane buildPane() {
        return new ABRConfigurationPane();
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
        IABRPane pane = buildPane();
        if (pane != null) {
            Scene scene = new Scene(pane.createPane(), getSceneWidth(), getSceneHeight());
            modalStage.setScene(scene);
            modalStage.setTitle(getTitle());
            modalStage.initModality(Modality.APPLICATION_MODAL); // Make it modal
            modalStage.showAndWait(); // Block until this window is closed
        }
    }
}

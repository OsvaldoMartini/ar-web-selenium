package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ARLicensePane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ARLicenseScene extends ARScene {

    private static final Double SCENE_HEIGHT = 400D;
    private static final Double SCENE_WIDTH = 800D;
    private static final String TITLE = "Activation Software Required";

    @Override
    public IARPane buildPane() {
        return new ARLicensePane();
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
}

package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ABRNewCommandPane;
import com.allinweb.ch.component.pane.base.IABRPane;
import com.allinweb.ch.component.scene.base.ABRScene;
import com.allinweb.ch.util.ComboBoxVars;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ABRNewCommandScene extends ABRScene {

    private static final Double SCENE_HEIGHT = 300D;
    private static final Double SCENE_WIDTH = 650D;
    private static final String TITLE = "Add Command";
    private int botJobId;
    private ObservableList<ComboBoxVars> webPageItems;

    public ABRNewCommandScene(int botJobId, ObservableList<ComboBoxVars> webPageItems) {
        super();
        this.botJobId = botJobId;
        this.webPageItems = webPageItems;
    }

    @Override
    public IABRPane buildPane() {
        return new ABRNewCommandPane(botJobId, webPageItems);
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

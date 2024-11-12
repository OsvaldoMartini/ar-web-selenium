package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ABRNewBotJobPane;
import com.allinweb.ch.component.pane.base.IABRPane;
import com.allinweb.ch.component.scene.base.ABRScene;
import com.allinweb.ch.persistence.BotJobDTO;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ABRNewBotJobScene extends ABRScene {

    private static final Double SCENE_HEIGHT = 400D;
    private static final Double SCENE_WIDTH = 300D;
    private static final String TITLE = "New Bot Job";
    ListView<BotJobDTO> viewBotJobListView;

    public ABRNewBotJobScene(ListView<BotJobDTO> viewBotJobListView) {
        super();
        this.viewBotJobListView = viewBotJobListView;
    }

    @Override
    public IABRPane buildPane() {
        return new ABRNewBotJobPane(viewBotJobListView);
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

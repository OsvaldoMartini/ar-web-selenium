package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.pane.ABRSaveBotJobAsPane;
import com.allinweb.ch.component.pane.base.IABRPane;
import com.allinweb.ch.component.scene.base.ABRScene;
import java.util.List;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ABRSaveBotJobAsScene extends ABRScene {

    private static final Double SCENE_HEIGHT = 300D;
    private static final Double SCENE_WIDTH = 300D;
    private static final String TITLE = "Save Bot Job As";

    private BotJobLoadDTO selecBotJobDTO;
    private List<BotJobLoadDTO> botJobList;

    public ABRSaveBotJobAsScene(BotJobLoadDTO selecBotJobDTO, List<BotJobLoadDTO> botJobList) {
        this.selecBotJobDTO = selecBotJobDTO;
        this.botJobList = botJobList;
    }

    @Override
    public IABRPane buildPane() {
        return new ABRSaveBotJobAsPane(selecBotJobDTO, botJobList);
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

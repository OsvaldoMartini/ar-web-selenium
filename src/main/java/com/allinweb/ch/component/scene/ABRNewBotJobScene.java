package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ABRNewBotJobPane;
import com.allinweb.ch.component.pane.base.IABRPane;
import com.allinweb.ch.component.scene.base.ABRScene;
import com.allinweb.ch.persistence.BotJobDTO;
import javafx.scene.control.ListView;

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
}

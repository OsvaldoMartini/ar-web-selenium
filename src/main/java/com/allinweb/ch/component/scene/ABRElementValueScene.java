package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ABRElementValuePane;
import com.allinweb.ch.component.pane.base.IABRPane;
import com.allinweb.ch.component.scene.base.ABRScene;
import com.allinweb.ch.persistence.BotJobDTO;
import javafx.scene.control.ListView;

public class ABRElementValueScene extends ABRScene {

    private static final Double SCENE_HEIGHT = 400D;
    private static final Double SCENE_WIDTH = 300D;
    private static final String TITLE = "New Variables";
    private ListView<BotJobDTO> viewBotJobListView;
    private int botJobId;

    public ABRElementValueScene(ListView<BotJobDTO> viewBotJobListView, int botJobId) {
        super();
        this.viewBotJobListView = viewBotJobListView;
        this.botJobId = botJobId;
    }

    @Override
    public IABRPane buildPane() {
        return new ABRElementValuePane(viewBotJobListView, botJobId);
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

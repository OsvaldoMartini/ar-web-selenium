package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ABRSaveBotJobAsPane;
import com.allinweb.ch.component.pane.base.IABRPane;
import com.allinweb.ch.component.scene.base.ABRScene;

public class ABRSaveBotJobAsScene extends ABRScene {

    private static final Double SCENE_HEIGHT = 300D;
    private static final Double SCENE_WIDTH = 300D;
    private static final String TITLE = "Save Bot Job As";

    private int botJobId;

    public ABRSaveBotJobAsScene(int botJobId) {
        this.botJobId = botJobId;
    }

    @Override
    public IABRPane buildPane() {
        return new ABRSaveBotJobAsPane(botJobId);
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

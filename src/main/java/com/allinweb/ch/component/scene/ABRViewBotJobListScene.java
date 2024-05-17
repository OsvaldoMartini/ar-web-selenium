package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ABRViewBotJobListPane;
import com.allinweb.ch.component.pane.base.IABRPane;
import com.allinweb.ch.component.scene.base.ABRScene;

public class ABRViewBotJobListScene extends ABRScene {

    private static final Double SCENE_HEIGHT = 600D;
    private static final Double SCENE_WIDTH = 800D;
    private static final String TITLE = "Bot Job List";

    public ABRViewBotJobListScene() {
        super();
    }

    @Override
    public IABRPane buildPane() {
        return new ABRViewBotJobListPane();
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

package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ABRNewBotJobPane;
import com.allinweb.ch.component.pane.base.IABRPane;
import com.allinweb.ch.component.scene.base.ABRScene;

public class ABRNewBotJobScene extends ABRScene {

    private static final Double SCENE_HEIGHT = 400D;
    private static final Double SCENE_WIDTH = 300D;
    private static final String TITLE = "New Bot Job";

    public ABRNewBotJobScene() {
        super();
    }

    @Override
    public IABRPane buildPane() {
        return new ABRNewBotJobPane();
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

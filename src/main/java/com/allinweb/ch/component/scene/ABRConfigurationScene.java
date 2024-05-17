package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ABRConfigurationPane;
import com.allinweb.ch.component.pane.base.IABRPane;
import com.allinweb.ch.component.scene.base.ABRScene;

public class ABRConfigurationScene extends ABRScene {

    private static final Double SCENE_HEIGHT = 700D;
    private static final Double SCENE_WIDTH = 800D;
    private static final String TITLE = "Configuration";

    public ABRConfigurationScene() {
        super();
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
}

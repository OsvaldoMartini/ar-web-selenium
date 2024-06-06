package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ABRMainPane;
import com.allinweb.ch.component.pane.base.IABRPane;
import com.allinweb.ch.component.scene.base.ABRScene;

public class ABRMainScene extends ABRScene {

    private static final Double SCENE_HEIGHT = 600D;
    private static final Double SCENE_WIDTH = 800D;
    private static final String TITLE = "ABR Web Scanner";

    public ABRMainScene() {
        super();
    }

    @Override
    public IABRPane buildPane() {
        return new ABRMainPane();
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

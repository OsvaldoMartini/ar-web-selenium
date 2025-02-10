package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ARViewBotJobListPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;

public class ARViewBotJobListScene extends ARScene {

    private static final Double SCENE_HEIGHT = 600D;
    private static final Double SCENE_WIDTH = 800D;
    private static final String TITLE = "Bot Job List";

    public ARViewBotJobListScene() {
        super();
    }

    @Override
    public IARPane buildPane() {
        return new ARViewBotJobListPane();
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

package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ARMainPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;

public class ARMainScene extends ARScene {

    private static final Double SCENE_HEIGHT = 600D;
    private static final Double SCENE_WIDTH = 700D;
    private static final String TITLE = "AR Web Bot Job List";

    public ARMainScene() {
        super();
    }

    @Override
    public IARPane buildPane() {
        return new ARMainPane();
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

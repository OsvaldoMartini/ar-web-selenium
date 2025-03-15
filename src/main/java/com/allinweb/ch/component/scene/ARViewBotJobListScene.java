package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ARViewBotJobListPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformMessage;

public class ARViewBotJobListScene extends ARScene {

    private static final Double SCENE_HEIGHT = 600D;
    private static final Double SCENE_WIDTH = 800D;
    private static final String TITLE = "Bot Job List";

    private final ARViewBotJobScene arViewBotJobScene;
    private final PerformDataBase performDataBase;
    private final PerformMessage performMessage;

    public ARViewBotJobListScene(
            ARViewBotJobScene arViewBotJobScene, PerformDataBase performDataBase, PerformMessage performMessage) {
        super();
        this.arViewBotJobScene = arViewBotJobScene;
        this.performDataBase = performDataBase;
        this.performMessage = performMessage;
    }

    @Override
    public IARPane buildPane() {
        return new ARViewBotJobListPane(arViewBotJobScene, performDataBase, performMessage);
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

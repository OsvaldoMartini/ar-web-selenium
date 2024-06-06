package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ABRExportFilterPane;
import com.allinweb.ch.component.pane.base.IABRPane;
import com.allinweb.ch.component.scene.base.ABRScene;
import com.allinweb.ch.persistence.BotJobDTO;

public class ABRExportFilterScene extends ABRScene {

    private static final Double SCENE_HEIGHT = 600D;
    private static final Double SCENE_WIDTH = 1000D;
    private static final String TITLE = "Excel Export Field Filters";

    private BotJobDTO botJob;

    public ABRExportFilterScene(BotJobDTO botJob) {
        this.botJob = botJob;
    }

    @Override
    public IABRPane buildPane() {
        return new ABRExportFilterPane(botJob);
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

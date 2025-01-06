package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.pane.ABRExportFilterPane;
import com.allinweb.ch.component.pane.base.IABRPane;
import com.allinweb.ch.component.scene.base.ABRScene;

public class ABRExportFilterScene extends ABRScene {

    private static final Double SCENE_HEIGHT = 600D;
    private static final Double SCENE_WIDTH = 1000D;
    private static final String TITLE = "Excel Export Field Filters";

    private BotJobLoadDTO botJobLoad;

    public ABRExportFilterScene(BotJobLoadDTO botJobLoad) {
        this.botJobLoad = botJobLoad;
    }

    @Override
    public IABRPane buildPane() {
        return new ABRExportFilterPane(botJobLoad);
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

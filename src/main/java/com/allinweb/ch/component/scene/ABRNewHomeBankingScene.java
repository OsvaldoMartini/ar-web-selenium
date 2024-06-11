package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ABRNewHomeBankingPane;
import com.allinweb.ch.component.pane.base.IABRPane;
import com.allinweb.ch.component.scene.base.ABRScene;

public class ABRNewHomeBankingScene extends ABRScene {
    private static final Double SCENE_HEIGHT = 800D;
    private static final Double SCENE_WIDTH = 1000D;
    private static final String TITLE = "New Url";

    @Override
    public IABRPane buildPane() {
        return new ABRNewHomeBankingPane();
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

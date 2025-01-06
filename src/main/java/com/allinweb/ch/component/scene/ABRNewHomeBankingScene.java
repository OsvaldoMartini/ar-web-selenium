package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ABRNewHomeBankingPane;
import com.allinweb.ch.component.pane.base.IABRPane;
import com.allinweb.ch.component.scene.base.ABRScene;
import com.allinweb.ch.facade.SingletonSupplier;

public class ABRNewHomeBankingScene extends ABRScene {
    private static final Double SCENE_HEIGHT = 750D;
    private static final Double SCENE_WIDTH = 1200D;
    private static final String TITLE = "New Url";

    protected static final SingletonSupplier<ABRNewHomeBankingScene> instance = () -> new ABRNewHomeBankingScene();

    // Private constructor to prevent instantiation
    public ABRNewHomeBankingScene() {
        // Initialize if necessary
        super();
    }

    // Public method to access the singleton instance
    public static ABRNewHomeBankingScene getInstance() {
        return instance.get();
    }

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

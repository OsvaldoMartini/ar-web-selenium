package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ARNewHomeBankingPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import com.allinweb.ch.facade.SingletonSupplier;

public class ARNewHomeBankingScene extends ARScene {
    private static final Double SCENE_HEIGHT = 750D;
    private static final Double SCENE_WIDTH = 1200D;
    private static final String TITLE = "New Url";

    protected static final SingletonSupplier<ARNewHomeBankingScene> instance = () -> new ARNewHomeBankingScene();

    // Private constructor to prevent instantiation
    public ARNewHomeBankingScene() {
        // Initialize if necessary
        super();
    }

    // Public method to access the singleton instance
    public static ARNewHomeBankingScene getInstance() {
        return instance.get();
    }

    @Override
    public IARPane buildPane() {
        return new ARNewHomeBankingPane();
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

package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ABRViewBotJobPane;
import com.allinweb.ch.component.pane.base.IABRPane;
import com.allinweb.ch.component.scene.base.ABRScene;
import com.allinweb.ch.core.ABRSharedResources;
import com.allinweb.ch.persistence.BotJobDTO;

public class ABRViewBotJobScene extends ABRScene {

    private static final Double SCENE_HEIGHT = 600D;
    private static final Double SCENE_WIDTH = 1100D;
    private static final String TITLE = "Bot Job Details";

    private final Integer botJobId;

    public ABRViewBotJobScene(Integer botJobId) {
        super();
        this.botJobId = botJobId;
    }

    @Override
    public IABRPane buildPane() {
        return new ABRViewBotJobPane(ABRSharedResources.getInstance().getEntityById(BotJobDTO.class, botJobId));
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

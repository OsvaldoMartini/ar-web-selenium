package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ABRScannedElementPane;
import com.allinweb.ch.component.pane.base.IABRPane;
import com.allinweb.ch.component.scene.base.ABRScene;
import com.allinweb.ch.core.ABRSharedResources;
import com.allinweb.ch.driver.ABRWebDriver;
import com.allinweb.ch.persistence.BlockDTO;
import com.allinweb.ch.persistence.BotJobDTO;
import javafx.stage.Stage;

public class ABRScannedElementScene extends ABRScene {

    private static final Double SCENE_HEIGHT = 650D;
    private static final Double SCENE_WIDTH = 1080D;
    private static final String TITLE = "Scanner Tool";

    private ABRWebDriver abrWebDriver;
    private final Integer botJobId;
    private final Integer blockId;
    private String priority;

    public ABRScannedElementScene(String priority, Integer botJobId, Integer blockId) {
        super();
        this.priority = priority;
        this.botJobId = botJobId;
        this.blockId = blockId;
    }

    @Override
    public IABRPane buildPane() {
        abrWebDriver = new ABRWebDriver();
        return new ABRScannedElementPane(
                priority,
                ABRSharedResources.getInstance().getEntityById(BotJobDTO.class, botJobId),
                ABRSharedResources.getInstance().getEntityById(BlockDTO.class, blockId),
                abrWebDriver);
    }

    @Override
    public void setStageBehaviour(Stage stage) {
        super.setStageBehaviour(stage);
        stage.setOnCloseRequest(windowEvent -> abrWebDriver.closeDriver());
    }

    @Override
    public String getTitle() {
        return TITLE;
    }

    @Override
    public Double getSceneHeight() {
        return SCENE_HEIGHT;
    }

    @Override
    public Double getSceneWidth() {
        return SCENE_WIDTH;
    }
}

package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ABRComponentDetailsPane;
import com.allinweb.ch.component.pane.base.IABRPane;
import com.allinweb.ch.component.scene.base.ABRScene;
import com.allinweb.ch.persistence.SavedBlocksDTO;

public class ABRComponentDetailsScene extends ABRScene {

    private static final Double SCENE_HEIGHT = 400D;
    private static final Double SCENE_WIDTH = 650D;
    private static String TITLE = "";

    private SavedBlocksDTO savedBlocksDTO;

    public ABRComponentDetailsScene(SavedBlocksDTO savedBlocksDTO) {
        this.savedBlocksDTO = savedBlocksDTO;
        TITLE = "Details - " + savedBlocksDTO.getName();
    }

    @Override
    public IABRPane buildPane() {
        return new ABRComponentDetailsPane(savedBlocksDTO);
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

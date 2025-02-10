package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ARComponentDetailsPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import com.allinweb.ch.persistence.SavedBlocksDTO;

public class ARComponentDetailsScene extends ARScene {

    private static final Double SCENE_HEIGHT = 400D;
    private static final Double SCENE_WIDTH = 650D;
    private static String TITLE = "";

    private SavedBlocksDTO savedBlocksDTO;

    public ARComponentDetailsScene(SavedBlocksDTO savedBlocksDTO) {
        this.savedBlocksDTO = savedBlocksDTO;
        TITLE = "Details - " + savedBlocksDTO.getName();
    }

    @Override
    public IARPane buildPane() {
        return new ARComponentDetailsPane(savedBlocksDTO);
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

package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ABRSaveBlockPane;
import com.allinweb.ch.component.pane.base.IABRPane;
import com.allinweb.ch.component.scene.base.ABRScene;
import com.allinweb.ch.persistence.BlockDTO;
import com.allinweb.ch.persistence.SavedBlocksDTO;

public class ABRSaveBlockScene extends ABRScene {
    private static final Double SCENE_HEIGHT = 250D;
    private static final Double SCENE_WIDTH = 600D;
    private static String TITLE = "Move Block";

    private SavedBlocksDTO savedBlocksDTO;
    private BlockDTO blockDTO;

    public ABRSaveBlockScene(SavedBlocksDTO savedBlocksDTO, BlockDTO blockDTO) {
        this.savedBlocksDTO = savedBlocksDTO;
        this.blockDTO = blockDTO;
        TITLE = "Save Block - " + savedBlocksDTO.getName();
    }

    @Override
    public IABRPane buildPane() {
        return new ABRSaveBlockPane(savedBlocksDTO, blockDTO);
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

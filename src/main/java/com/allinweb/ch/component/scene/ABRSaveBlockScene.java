package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.model.BlockDetailsDTO;
import com.allinweb.ch.component.pane.ABRSaveBlockPane;
import com.allinweb.ch.component.pane.base.IABRPane;
import com.allinweb.ch.component.scene.base.ABRScene;
import com.allinweb.ch.persistence.BlockDTO;
import com.allinweb.ch.persistence.SavedBlocksDTO;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ABRSaveBlockScene extends ABRScene {
    private static final Double SCENE_HEIGHT = 250D;
    private static final Double SCENE_WIDTH = 600D;
    private static String TITLE = "Move Block";

    private SavedBlocksDTO savedBlocksDTO;
    private BlockDTO blockDTO;
    private BlockDetailsDTO blockDetailsDTO;

    public ABRSaveBlockScene(SavedBlocksDTO savedBlocksDTO, BlockDTO blockDTO, BlockDetailsDTO blockDetailsDTO) {
        this.savedBlocksDTO = savedBlocksDTO;
        this.blockDTO = blockDTO;
        this.blockDetailsDTO = blockDetailsDTO;
        TITLE = "Save Block - " + savedBlocksDTO.getName();
    }

    public void showModal() {
        Stage modalStage = new Stage();
        IABRPane pane = buildPane();
        if (pane != null) {
            Scene scene = new Scene(pane.createPane(), getSceneWidth(), getSceneHeight());
            modalStage.setScene(scene);
            modalStage.setTitle(getTitle());
            modalStage.initModality(Modality.APPLICATION_MODAL); // Make it modal
            modalStage.showAndWait(); // Block until this window is closed
        }
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

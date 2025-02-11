package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.model.DetailsDTO;
import com.allinweb.ch.component.pane.ARSaveBlockPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import com.allinweb.ch.persistence.BlockDTO;
import com.allinweb.ch.persistence.ComponentBlockDTO;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ARSaveBlockScene extends ARScene {
    private static final Double SCENE_HEIGHT = 250D;
    private static final Double SCENE_WIDTH = 600D;
    private static String TITLE = "Move Block";

    private ComponentBlockDTO componentBlockDTO;
    private BlockDTO blockDTO;
    private DetailsDTO detailsDTO;

    public ARSaveBlockScene(ComponentBlockDTO componentBlockDTO, BlockDTO blockDTO, DetailsDTO detailsDTO) {
        this.componentBlockDTO = componentBlockDTO;
        this.blockDTO = blockDTO;
        this.detailsDTO = detailsDTO;
        TITLE = "Save Block - " + componentBlockDTO.getName();
    }

    public void showModal() {
        Stage modalStage = new Stage();
        IARPane pane = buildPane();
        if (pane != null) {
            Scene scene = new Scene(pane.createPane(), getSceneWidth(), getSceneHeight());
            modalStage.setScene(scene);
            modalStage.setTitle(getTitle());
            modalStage.initModality(Modality.APPLICATION_MODAL); // Make it modal
            modalStage.showAndWait(); // Block until this window is closed
        }
    }

    @Override
    public IARPane buildPane() {
        return new ARSaveBlockPane(componentBlockDTO, blockDTO, detailsDTO);
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

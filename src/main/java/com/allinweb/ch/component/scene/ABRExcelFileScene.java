package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.model.BlockDetailsDTO;
import com.allinweb.ch.component.pane.ABRExcelFilePane;
import com.allinweb.ch.component.pane.base.IABRPane;
import com.allinweb.ch.component.scene.base.ABRScene;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ABRExcelFileScene extends ABRScene {

    private static final Double SCENE_HEIGHT = 300D;
    private static final Double SCENE_WIDTH = 800D;
    private static final String TITLE = "Create or Delete the Export Excel File";
    private BlockDetailsDTO blockExcelDTO;

    public ABRExcelFileScene(BlockDetailsDTO blockExcelDTO) {
        super();
        this.blockExcelDTO = blockExcelDTO;
    }

    @Override
    public IABRPane buildPane() {
        return new ABRExcelFilePane(blockExcelDTO);
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
}

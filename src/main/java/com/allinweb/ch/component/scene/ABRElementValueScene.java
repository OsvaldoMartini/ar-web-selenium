package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.model.RowMoveDTO;
import com.allinweb.ch.component.pane.ABRElementValuePane;
import com.allinweb.ch.component.pane.base.IABRPane;
import com.allinweb.ch.component.scene.base.ABRScene;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ABRElementValueScene extends ABRScene {

    private static final Double SCENE_HEIGHT = 500D;
    private static final Double SCENE_WIDTH = 300D;
    private static final String TITLE = "New Variables";
    private RowMoveDTO rowMoveDTO;
    private int instructionId;
    private String instructionName;
    private String varName;
    private String instructionType;

    public ABRElementValueScene(
            RowMoveDTO rowMoveDTO, int instructionId, String instructionName, String varName, String instructionType) {
        super();
        this.rowMoveDTO = rowMoveDTO;
        this.instructionId = instructionId;
        this.instructionName = instructionName;
        this.varName = varName;
        this.instructionType = instructionType;
    }

    @Override
    public IABRPane buildPane() {
        return new ABRElementValuePane(rowMoveDTO, instructionId, instructionName, varName, instructionType);
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

package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.model.RowMoveDTO;
import com.allinweb.ch.component.pane.ARNewCommandPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import com.allinweb.ch.util.ComboBoxVars;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ARNewCommandScene extends ARScene {

    private static final Double SCENE_HEIGHT = 300D;
    private static final Double SCENE_WIDTH = 700D;
    private static final String TITLE = "Add Command";
    private RowMoveDTO rowMoveDTO;
    private BotJobLoadDTO botJobLoad;
    private ObservableList<ComboBoxVars> webPageItems;
    private String sessionId;

    public ARNewCommandScene(
            RowMoveDTO rowMoveDTO,
            BotJobLoadDTO botJobLoad,
            ObservableList<ComboBoxVars> webPageItems,
            String sessionId) {
        super();
        this.rowMoveDTO = rowMoveDTO;
        this.botJobLoad = botJobLoad;
        this.webPageItems = webPageItems;
        this.sessionId = sessionId;
    }

    @Override
    public IARPane buildPane() {
        return new ARNewCommandPane(rowMoveDTO, botJobLoad, webPageItems, sessionId);
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
        String titleMsg = createDescriptionString(rowMoveDTO, webPageItems);
        if (titleMsg != null) {
            return titleMsg;
        } else {
            return TITLE;
        }
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

    public String createDescriptionString(RowMoveDTO rowMoveDTO, ObservableList<ComboBoxVars> webPageItems) {
        // Ensure there are updatedRows to work with
        if (rowMoveDTO.getUpdatedRows() == null || rowMoveDTO.getUpdatedRows().isEmpty()) {
            return "No updated rows available";
        }

        // Construct the final string
        String result =
                " " + rowMoveDTO.getType().replace("_", " ") + " -> Block Selected: " + rowMoveDTO.getBlockName();

        return result;
    }
}

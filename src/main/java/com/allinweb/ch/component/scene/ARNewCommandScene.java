package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.model.RowMoveDTO;
import com.allinweb.ch.component.pane.ARNewCommandPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import com.allinweb.ch.util.ARLogger;
import com.allinweb.ch.util.ComboBoxVars;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ARNewCommandScene extends ARScene {

    protected static volatile ARNewCommandScene instance;

    // Private constructor to prevent instantiation
    private ARNewCommandScene() {
        // Initialize if necessary
        super();
    }

    public static ARNewCommandScene getInstance() {
        if (instance == null) {
            synchronized (ARNewCommandScene.class) {
                if (instance == null) {
                    instance = new ARNewCommandScene();
                }
            }
        }
        return instance;
    }

    private Stage modalStage;
    private Scene modalScene;

    private static final Double SCENE_HEIGHT = 300D;
    private static final Double SCENE_WIDTH = 800D;
    private static final String TITLE = "Add Command";
    private RowMoveDTO rowMoveDTO;
    private BotJobLoadDTO botJobLoad;
    private ObservableList<ComboBoxVars> webPageItems = FXCollections.observableArrayList();
    private String sessionId;

    private static ARNewCommandPane arNewCommandPane;

    static {
        arNewCommandPane = ARNewCommandPane.getInstance();
    }

    public void initialize(
            RowMoveDTO rowMoveDTO, BotJobLoadDTO botJobLoad, List<ComboBoxVars> webPageItems, String sessionId) {

        this.rowMoveDTO = rowMoveDTO;
        this.botJobLoad = botJobLoad;
        this.webPageItems.clear();
        this.webPageItems.addAll(webPageItems);
        this.sessionId = sessionId;
    }

    @Override
    public IARPane buildPane() {
        arNewCommandPane.initialize(rowMoveDTO, botJobLoad, webPageItems, sessionId);
        return arNewCommandPane;
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
        if (modalStage == null) {
            modalStage = new Stage();
            IARPane pane = buildPane();
            if (pane != null) {
                modalScene = new Scene(pane.createPane(), getSceneWidth(), getSceneHeight());
                modalStage.setScene(modalScene);
                modalStage.setTitle(getTitle());
                modalStage.initModality(Modality.NONE); // Changed to NONE
                modalStage.setAlwaysOnTop(true); // Set always on top
            } else {
                // Handle the case where pane creation failed
                ARLogger.getInstance(ARNewCommandScene.class).severe("Failed to build pane for modal.");
                return;
            }
        } else {
            arNewCommandPane.initialize(rowMoveDTO, botJobLoad, webPageItems, sessionId);
            modalStage.setTitle(getTitle()); // Update title if it might have changed
        }
        modalStage.show(); // Block until this window is closed
        //        modalStage.showAndWait(); // Block until this window is closed
    }

    public void closeModal() {
        if (modalStage != null && modalStage.isShowing()) {
            modalStage.close();
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

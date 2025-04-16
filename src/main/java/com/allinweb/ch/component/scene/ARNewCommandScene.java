package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.model.RowMoveDTO;
import com.allinweb.ch.component.pane.ARNewCommandPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import com.allinweb.ch.facade.SingletonSupplier;
import com.allinweb.ch.util.ARLogger;
import com.allinweb.ch.util.ComboBoxVars;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ARNewCommandScene extends ARScene {

    // Static final variable to hold the singleton instance
    protected static final SingletonSupplier<ARNewCommandScene> instance = () -> new ARNewCommandScene();

    private Stage modalStage;
    private Scene modalScene;

    // Public method to access the singleton instance
    public static ARNewCommandScene getInstance() {
        return instance.get();
    }

    // Private constructor to prevent instantiation
    public ARNewCommandScene() {
        // Initialize if necessary
        super();
    }

    private static final Double SCENE_HEIGHT = 300D;
    private static final Double SCENE_WIDTH = 800D;
    private static final String TITLE = "Add Command";
    private RowMoveDTO rowMoveDTO;
    private BotJobLoadDTO botJobLoad;
    private ObservableList<ComboBoxVars> webPageItems;
    private String sessionId;

    private static ARNewCommandPane arNewCommandPane;

    static {
        arNewCommandPane = ARNewCommandPane.getInstance();
    }

    public void initialize(
            RowMoveDTO rowMoveDTO,
            BotJobLoadDTO botJobLoad,
            ObservableList<ComboBoxVars> webPageItems,
            String sessionId) {

        this.rowMoveDTO = rowMoveDTO;
        this.botJobLoad = botJobLoad;
        this.webPageItems = webPageItems;
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

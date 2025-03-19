package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.pane.ARNewBotJobPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.facade.SingletonSupplier;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ARNewBotJobScene extends ARScene {

    // Static final variable to hold the singleton instance
    protected static final SingletonSupplier<ARNewBotJobScene> instance = () -> new ARNewBotJobScene();

    // Public method to access the singleton instance
    public static ARNewBotJobScene getInstance() {
        return instance.get();
    }

    // Private constructor to prevent instantiation
    public ARNewBotJobScene() {
        // Initialize if necessary
        super();
    }

    private static final Double SCENE_HEIGHT = 400D;
    private static final Double SCENE_WIDTH = 300D;
    private static final String TITLE = "New Bot Job";
    //    ListView<BotJobLoadDTO> viewBotJobListView;
    private ARViewBotJobScene arViewBotJobScene;
    private PerformDataBase performDataBase;
    private PerformMessage performMessage;
    private ObservableList<BotJobLoadDTO> botJobList;

    public void initialize(
            ARViewBotJobScene arViewBotJobScene,
            PerformDataBase performDataBase,
            PerformMessage performMessage,
            ObservableList<BotJobLoadDTO> botJobList) {
        this.arViewBotJobScene = arViewBotJobScene;
        this.performDataBase = performDataBase;
        this.performMessage = performMessage;
        this.botJobList = botJobList;
    }

    @Override
    public IARPane buildPane() {
        // Create ARNewBotJobPane without passing ListView here
        return new ARNewBotJobPane(arViewBotJobScene, performDataBase, performMessage, botJobList);
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
        IARPane pane = buildPane();
        if (pane != null) {
            Scene scene = new Scene(pane.createPane(), getSceneWidth(), getSceneHeight());
            modalStage.setScene(scene);
            modalStage.setTitle(getTitle());
            modalStage.initModality(Modality.APPLICATION_MODAL); // Make it modal
            modalStage.showAndWait(); // Block until this window is closed
        }
    }
}

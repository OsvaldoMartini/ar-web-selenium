package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.pane.ARNewBotJobPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.util.ARLogger;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.openqa.selenium.WebDriver;

public class ARNewBotJobScene extends ARScene {

    protected static volatile ARNewBotJobScene instance;

    // Private constructor to prevent instantiation
    private ARNewBotJobScene() {
        // Initialize if necessary
        super();
    }

    public static ARNewBotJobScene getInstance() {
        if (instance == null) {
            synchronized (ARNewBotJobScene.class) {
                if (instance == null) {
                    instance = new ARNewBotJobScene();
                }
            }
        }
        return instance;
    }

    private Stage modalStage;
    private Scene modalScene;

    private static ARNewBotJobPane arNewBotJobPane;

    static {
        arNewBotJobPane = ARNewBotJobPane.getInstance();
    }

    private static final Double SCENE_HEIGHT = 400D;
    private static final Double SCENE_WIDTH = 300D;
    private static final String TITLE = "New Bot Job";
    //    ListView<BotJobLoadDTO> viewBotJobListView;
    private ARViewBotJobScene arViewBotJobScene;
    private ARWebDriver arWebDriver;
    private ObservableList<BotJobLoadDTO> botJobList;
    private ObservableList<WebDriver> webDriverList;

    public void initialize(
            ARViewBotJobScene arViewBotJobScene,
            ARWebDriver arWebDriver,
            ObservableList<BotJobLoadDTO> botJobList,
            ObservableList<WebDriver> webDriverList) {
        this.arViewBotJobScene = arViewBotJobScene;
        this.arWebDriver = arWebDriver;
        this.botJobList = botJobList;
        this.webDriverList = webDriverList;
    }

    @Override
    public IARPane buildPane() {
        // Create ARNewBotJobPane without passing ListView here
        arNewBotJobPane.initialize(arViewBotJobScene, arWebDriver, botJobList);
        return arNewBotJobPane;
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
        if (modalStage == null) {
            modalStage = new Stage();
            IARPane pane = buildPane();
            if (pane != null) {
                modalScene = new Scene(pane.createPane(), getSceneWidth(), getSceneHeight());
                modalStage.setScene(modalScene);
                modalStage.setTitle(getTitle());
                modalStage.initModality(Modality.WINDOW_MODAL); // Changed to NONE
                modalStage.setAlwaysOnTop(true); // Set always on top
            } else {
                // Handle the case where pane creation failed
                ARLogger.getInstance(ARNewCommandScene.class).severe("Failed to build pane for modal.");
                return;
            }
        } else {
            arNewBotJobPane.initialize(arViewBotJobScene, arWebDriver, botJobList);
            modalStage.setTitle(getTitle()); // Update title if it might have changed
        }
        //        modalStage.show(); // Block until this window is closed
        modalStage.showAndWait(); // Block until this window is closed
    }
}

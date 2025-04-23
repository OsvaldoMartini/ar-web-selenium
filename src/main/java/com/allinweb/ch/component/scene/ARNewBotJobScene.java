package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.pane.ARNewBotJobPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.facade.PerformActions;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.facade.PerformPreLoad;
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

    private static final Double SCENE_HEIGHT = 400D;
    private static final Double SCENE_WIDTH = 300D;
    private static final String TITLE = "New Bot Job";
    //    ListView<BotJobLoadDTO> viewBotJobListView;
    private ARViewBotJobScene arViewBotJobScene;
    private ARWebDriver arWebDriver;
    private PerformDataBase performDataBase;
    private PerformActions performActions;
    private PerformMessage performMessage;
    private PerformPreLoad performPreLoad;
    private ObservableList<BotJobLoadDTO> botJobList;
    private ObservableList<WebDriver> webDriverList;

    public void initialize(
            ARViewBotJobScene arViewBotJobScene,
            ARWebDriver arWebDriver,
            PerformDataBase performDataBase,
            PerformActions performActions,
            PerformMessage performMessage,
            ObservableList<BotJobLoadDTO> botJobList,
            ObservableList<WebDriver> webDriverList) {
        this.arViewBotJobScene = arViewBotJobScene;
        this.arWebDriver = arWebDriver;
        this.performDataBase = performDataBase;
        this.performMessage = performMessage;
        this.performActions = performActions;
        this.performPreLoad = performPreLoad;
        this.botJobList = botJobList;
        this.webDriverList = webDriverList;
    }

    @Override
    public IARPane buildPane() {
        // Create ARNewBotJobPane without passing ListView here
        return new ARNewBotJobPane(
                arViewBotJobScene,
                arWebDriver,
                performDataBase,
                performActions,
                performMessage,
                performPreLoad,
                botJobList,
                webDriverList);
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

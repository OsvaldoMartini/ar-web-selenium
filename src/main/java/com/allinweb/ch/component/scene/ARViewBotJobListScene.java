package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ARViewBotJobListPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import com.allinweb.ch.driver.ARWebDriver;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.openqa.selenium.WebDriver;

import lombok.extern.slf4j.Slf4j;  @Slf4j public class ARViewBotJobListScene extends ARScene {

    protected static volatile ARViewBotJobListScene instance;

    // Private constructor to prevent instantiation
    private ARViewBotJobListScene() {

        super();
    }

    public static ARViewBotJobListScene getInstance() {
        if (instance == null) {
            synchronized (ARViewBotJobListScene.class) {
                if (instance == null) {
                    instance = new ARViewBotJobListScene();
                }
            }
        }
        return instance;
    }

    private static final Double SCENE_HEIGHT = 600D;
    private static final Double SCENE_WIDTH = 800D;
    private static final String TITLE = "Bot Job List";

    private ARViewBotJobScene arViewBotJobScene;
    private ARWebDriver arWebDriver;
    private ObservableList<WebDriver> webDriverList;

    public void initialize(
            ARViewBotJobScene arViewBotJobScene, ARWebDriver arWebDriver, ObservableList<WebDriver> webDriverList) {
        this.arViewBotJobScene = arViewBotJobScene;
        this.arWebDriver = arWebDriver;
        this.webDriverList = webDriverList;
    }

    private Stage modalStage;
    private Scene modalScene;

    private static final ARViewBotJobListPane arViewBotJobListPane;

    static {
        arViewBotJobListPane = ARViewBotJobListPane.getInstance();
    }

    @Override
    public IARPane buildPane() {
        return arViewBotJobListPane;
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

        arViewBotJobListPane.initialize(arViewBotJobScene, arWebDriver, webDriverList);

        if (modalStage == null) {
            modalStage = new Stage();
            modalStage.getIcons().add(icon);
            IARPane pane = buildPane();
            if (pane != null) {
                modalScene = new Scene(pane.createPane(), getSceneWidth(), getSceneHeight());
                modalStage.setScene(modalScene);
                modalStage.setTitle(getTitle());
                modalStage.initModality(Modality.WINDOW_MODAL);
                modalStage.setAlwaysOnTop(true); // Set always on top
                modalStage.toFront();
                // Reset alwaysOnTop after showing so it behaves normally afterward
                modalStage.setAlwaysOnTop(false);

                // Once shown, reset AlwaysOnTop to false so it behaves normally
                modalStage.setOnShown(event -> {
                    Platform.runLater(() -> modalStage.setAlwaysOnTop(false));
                });
            } else {
                // Handle the case where pane creation failed
                log.error("Failed to build pane for modal.");
                return;
            }
        }

        modalStage.setTitle(getTitle());

        // Check if the stage is already showing
        if (!modalStage.isShowing()) {
            modalStage.showAndWait(); // Show and wait only if not already showing
        }
    }
}

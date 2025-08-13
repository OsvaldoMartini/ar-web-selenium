package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ARNewBotJobPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.util.ARLogger;
import javafx.application.Platform;
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

    private static final Double SCENE_HEIGHT = 300D;
    private static final Double SCENE_WIDTH = 350D;
    private static final String TITLE = "New Bot Job";
    //    ListView<BotJobLoadDTO> viewBotJobListView;
    private ARViewBotJobScene arViewBotJobScene;
    private ARWebDriver arWebDriver;
    private ObservableList<WebDriver> webDriverList;
    private boolean isEnabledLicence;

    public void initialize(
            ARViewBotJobScene arViewBotJobScene,
            ARWebDriver arWebDriver,
            ObservableList<WebDriver> webDriverList,
            boolean isEnabledLicence) {
        this.isEnabledLicence = isEnabledLicence;
        this.arViewBotJobScene = arViewBotJobScene;
        this.arWebDriver = arWebDriver;
        this.webDriverList = webDriverList;
    }

    @Override
    public IARPane buildPane() {
        // Create ARNewBotJobPane without passing ListView here
        //        arNewBotJobPane.initialize(arViewBotJobScene, arWebDriver, botJobList);
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

        arNewBotJobPane.initialize(arViewBotJobScene, arWebDriver, isEnabledLicence);

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
                ARLogger.getInstance(ARNewBotJobScene.class).severe("Failed to build pane for modal.");
                return;
            }
        }

        modalStage.setTitle(getTitle()); // Update title if it might have changed

        // Check if the stage is already showing
        if (!modalStage.isShowing()) {
            modalStage.showAndWait(); // Show and wait only if not already showing
        }
    }

    public void closeModal() {
        try {
            if (modalStage != null) {
                modalStage.close();
            }
            modalStage = null;
        } catch (Exception error) {
            System.err.println("Browser Closed Before Web Scanner. Error: " + error.getMessage());
        }
    }
}

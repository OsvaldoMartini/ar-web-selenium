package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ARNewBotJobPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import com.allinweb.ch.driver.ARWebDriver;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;

@Slf4j
public class ARNewBotJobScene extends ARScene {

    private static final Double SCENE_HEIGHT = 430D;
    private static final Double SCENE_WIDTH = 450D;
    private static final String TITLE = "New Bot Job";
    protected static volatile ARNewBotJobScene instance;
    private static ARNewBotJobPane arNewBotJobPane;

    static {
        arNewBotJobPane = ARNewBotJobPane.getInstance();
    }

    private Stage modalStage;
    private Scene modalScene;
    //    ListView<BotJobLoadDTO> viewBotJobListView;
    private ARViewBotJobScene arViewBotJobScene;
    private ARWebDriver arWebDriver;
    private ObservableList<WebDriver> webDriverList;
    private boolean isEnabledLicence;
    // Private constructor to prevent instantiation
    private ARNewBotJobScene() {

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
    public int getSceneHeight() {
        return SCENE_HEIGHT;
    }

    @Override
    public int getSceneWidth() {
        return SCENE_WIDTH;
    }

    @Override
    public String getTitle() {
        return TITLE;
    }

    public void showModal(Stage primareStage) {

        arNewBotJobPane.initialize(arViewBotJobScene, arWebDriver, isEnabledLicence);

        if (modalStage == null) {
            modalStage = new Stage();
            modalStage.getIcons().add(icon);
            IARPane pane = buildPane();
            if (pane != null) {
                modalScene = new Scene(pane.createPane(), getSceneWidth(), getSceneHeight());
                modalStage.setScene(modalScene);
                modalStage.setTitle(getTitle());
                modalStage.initOwner(primareStage);
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
            log.error("Browser Closed Before Web Scanner. Error: " + error.getMessage());
        }
    }
}

package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.pane.ARConfigurationPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import java.time.format.DateTimeFormatter;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ARConfigurationScene extends ARScene {

    // Private constructor to prevent instantiation
    private static final Double SCENE_HEIGHT = 700D;
    private static final Double SCENE_WIDTH = 800D;
    private static final String TITLE = "Configuration";
    // Static final variable to hold the singleton instance
    private static final DateTimeFormatter FORMAT_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    protected static volatile ARConfigurationScene instance;
    private static ARConfigurationPane arConfigurationPane;

    static {
        arConfigurationPane = ARConfigurationPane.getInstance();
    }

    private boolean isEnabledLicence;
    private Stage modalStage;
    private Scene modalScene;
    private ListView<BotJobLoadDTO> viewBotJobListView;
    // Private constructor to prevent instantiation
    private ARConfigurationScene() {

        super();
    }

    public static ARConfigurationScene getInstance() {
        if (instance == null) {
            synchronized (ARConfigurationScene.class) {
                if (instance == null) {
                    instance = new ARConfigurationScene();
                }
            }
        }
        return instance;
    }

    @Override
    public IARPane buildPane() {
        //        arConfigurationPane.initialize(modalStage);
        return arConfigurationPane;
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

    private void cleanupAndClose(Stage stage) {
        System.out.println("Cleanup and Close: Exiting Threads");
        // Interrupt running threads
        threadList.forEach(this::interruptThread);
        // Add any other cleanup logic here (e.g., WebDriver quit)
        stage.close();
    }

    public void showModal() {

        arConfigurationPane.initialize(modalStage, viewBotJobListView, isEnabledLicence);

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

                // Set the onCloseRequest handler for the modal stage
                modalStage.setOnCloseRequest(event -> {
                    System.out.println("Handle Close (Modal Stage): Exiting Threads from Modal");
                    cleanupAndClose(modalStage);
                    event.consume(); // Prevent default close behavior if needed
                });

                //                // Set an event handler for when the window is closed (via the X button)
                //                modalStage.setOnCloseRequest(new EventHandler<WindowEvent>() {
                //                    @Override
                //                    public void handle(WindowEvent event) {
                //                        ARMainScene.setConfiguring(false); // Signal that the configuration is done
                //                    }
                //                });
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

    public void initialize(ListView<BotJobLoadDTO> viewBotJobListView, boolean isEnabledLicence) {
        this.isEnabledLicence = isEnabledLicence;
        this.viewBotJobListView = viewBotJobListView;
    }

    public void initializeLicense(boolean isEnabledLicence) {
        this.isEnabledLicence = isEnabledLicence;
    }
}

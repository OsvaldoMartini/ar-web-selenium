package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ARLicensePane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import com.allinweb.ch.util.ARLogger;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

public class ARLicenseScene extends ARScene {
    protected static volatile ARLicenseScene instance;

    // Private constructor to prevent instantiation
    private ARLicenseScene() {
        // Initialize if necessary
        super();
    }

    public static ARLicenseScene getInstance() {
        if (instance == null) {
            synchronized (ARLicenseScene.class) {
                if (instance == null) {
                    arLicensePane.initialize();
                    instance = new ARLicenseScene();
                }
            }
        }
        return instance;
    }

    private Stage modalStage;
    private Scene modalScene;

    private static final ARLicensePane arLicensePane;

    static {
        arLicensePane = ARLicensePane.getInstance();
    }

    private static final Double SCENE_HEIGHT = 550D;
    private static final Double SCENE_WIDTH = 800D;
    private static final String TITLE = "Activation Software Required";

    @Override
    public IARPane buildPane() {
        return arLicensePane;
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

    @Override
    public void setStageBehaviour(Stage stage) {
        super.setStageBehaviour(stage); // Call the parent class method

        // Only set the close request handler if it's not already set
        if (!isCloseHandlerSet) {
            stage.setOnCloseRequest(this::handleCloseRequest);
            isCloseHandlerSet = true; // Update the flag to prevent setting it again
        }
    }

    private void handleCloseRequest(WindowEvent event) {
        System.out.println("Handle Close (Main Stage): Exiting Threads and Quitting WebDriver");
        cleanupAndClose((Stage) event.getSource());
    }

    private void cleanupAndClose(Stage stage) {
        System.out.println("Cleanup and Close: Exiting Threads");
        // Interrupt running threads
        threadList.forEach(this::interruptThread);
        // Add any other cleanup logic here (e.g., WebDriver quit)
        stage.close();
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

            } else {
                // Handle the case where pane creation failed
                ARLogger.getInstance(ARLicenseScene.class).severe("Failed to build pane for modal.");
                return;
            }
        }

        arLicensePane.initialize();
        modalStage.setTitle(getTitle()); // Update title if it might have changed
        // Check if the stage is already showing
        if (!modalStage.isShowing()) {
            modalStage.showAndWait(); // Show and wait only if not already showing
        }
    }
}

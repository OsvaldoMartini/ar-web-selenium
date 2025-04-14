package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ARLicensePane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

public class ARLicenseScene extends ARScene {

    private static final Double SCENE_HEIGHT = 400D;
    private static final Double SCENE_WIDTH = 800D;
    private static final String TITLE = "Activation Software Required";

    @Override
    public IARPane buildPane() {
        return new ARLicensePane();
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
        Stage modalStage = new Stage();
        IARPane pane = buildPane();
        if (pane != null) {
            Scene scene = new Scene(pane.createPane(), getSceneWidth(), getSceneHeight());
            modalStage.setScene(scene);
            modalStage.setTitle(getTitle());
            modalStage.initModality(Modality.APPLICATION_MODAL); // Make it modal

            // Set the onCloseRequest handler for the modal stage
            modalStage.setOnCloseRequest(event -> {
                System.out.println("Handle Close (Modal Stage): Exiting Threads from Modal");
                cleanupAndClose(modalStage);
                event.consume(); // Prevent default close behavior if needed
            });

            modalStage.showAndWait(); // Block until this window is closed
        }
    }
}

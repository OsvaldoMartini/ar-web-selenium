package com.allinweb.ch.tests;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

public class CountdownAlertAsynchronous extends Application {

    private static final int SECONDS = 10; // Total seconds for the countdown
    private int remainingSeconds = SECONDS;
    private ExecutorService executorService;
    private Timeline timeline;
    private Alert alertToShow;

    @Override
    public void start(Stage primaryStage) {
        // Create a label to display the countdown
        Label countdownLabel = new Label(String.valueOf(remainingSeconds));
        countdownLabel.setStyle("-fx-font-size: 24px;");

        // Create a stack pane to hold the label
        StackPane stackPane = new StackPane(countdownLabel);
        stackPane.setPadding(new Insets(20));

        // Create a dialog for the alert
        alertToShow = new Alert(Alert.AlertType.INFORMATION);
        alertToShow.setTitle("Countdown Alert");
        alertToShow.setHeaderText(null);
        alertToShow.initModality(Modality.APPLICATION_MODAL);

        // Set the content of the alert
        alertToShow.getDialogPane().setContent(stackPane);

        // Create a single-threaded executor service
        executorService = Executors.newSingleThreadExecutor();

        // Create the Timeline and Alert in the JavaFX Application Thread
        timeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            remainingSeconds--;
            countdownLabel.setText(String.valueOf(remainingSeconds));
            if (remainingSeconds <= 0) {
                timeline.stop(); // Stop the timeline when countdown finishes
                alertToShow.close(); // Close the alert dialog
            }
        }));

        // Execute the countdown in a separate thread
        executorService.execute(() -> {
            timeline.setCycleCount(SECONDS); // Run for SECONDS seconds
            timeline.play(); // Start the timeline

            // Show the alert on the JavaFX Application Thread
            javafx.application.Platform.runLater(() -> alertToShow.showAndWait());
        });

        // Continue with the rest of the program logic
        // For demonstration purposes, we'll just print a message
        System.out.println("Program continues executing...");

        // Example: Simulate other work being done while countdown runs
        try {
            Thread.sleep(3000); // Simulate some other work
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Cleanup: Shutdown the executor service
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        // Cleanup: Shutdown the executor service if the application stops
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }
}

package com.allinweb.ch.tests;

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

public class CountdownAlert extends Application {

    private static final int SECONDS = 10; // Total seconds for the countdown
    private int remainingSeconds = SECONDS;
    private Timeline timeline;

    @Override
    public void start(Stage primaryStage) {
        // Create a label to display the countdown
        Label countdownLabel = new Label(String.valueOf(remainingSeconds));
        countdownLabel.setStyle("-fx-font-size: 24px;");

        // Create a stack pane to hold the label
        StackPane stackPane = new StackPane(countdownLabel);
        stackPane.setPadding(new Insets(20));

        // Create a dialog for the alert
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Countdown Alert");
        alert.setHeaderText(null);
        alert.initModality(Modality.APPLICATION_MODAL);

        // Set the content of the alert
        alert.getDialogPane().setContent(stackPane);

        // Create a timeline to update the countdown
        timeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            remainingSeconds--;
            countdownLabel.setText(String.valueOf(remainingSeconds));
            if (remainingSeconds <= 0) {
                timeline.stop(); // Stop the timeline when countdown finishes
                alert.close(); // Close the alert dialog
            }
        }));
        timeline.setCycleCount(SECONDS); // Run for SECONDS seconds
        timeline.play(); // Start the timeline

        // Show the alert
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

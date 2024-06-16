package com.allinweb.ch.tests;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;

public class CountdownApp2 extends Application {
    private TextField countdownTextField;
    private Timeline timeline;

    @Override
    public void start(Stage primaryStage) {
        // Create a TextField to display the countdown
        countdownTextField = new TextField();
        countdownTextField.setStyle("-fx-font-size: 24px;");
        countdownTextField.setEditable(false);

        // Create buttons for starting and stopping the countdown
        Button startButton = new Button("Start Countdown");
        startButton.setOnAction(event -> startCountdown());

        Button stopButton = new Button("Stop Countdown");
        stopButton.setOnAction(event -> stopCountdown());

        // Create an HBox to hold the buttons
        HBox buttonsBox = new HBox(10, startButton, stopButton);
        buttonsBox.setPadding(new Insets(10));

        // Create a stack pane to hold the TextField and buttons
        StackPane stackPane = new StackPane(countdownTextField);
        stackPane.getChildren().add(buttonsBox);
        stackPane.setPadding(new Insets(20));

        // Set up the primary stage
        primaryStage.setTitle("Countdown App");
        primaryStage.setScene(new Scene(stackPane, 300, 200));
        primaryStage.show();
    }

    private void startCountdown() {
        // Initialize countdown seconds
        final Integer[] initialSeconds = {10}; // Example countdown starting from 10 seconds

        // Stop any existing timeline if it's running
        stopCountdown();

        // Initialize Timeline for countdown
        timeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            // Update countdownTextField with current countdown value
            countdownTextField.setText(String.valueOf(initialSeconds[0]));
            initialSeconds[0]--;
            if (initialSeconds[0] <= 0) {
                timeline.stop();
                showCountdownCompleteAlert();
            }
        }));
        timeline.setCycleCount(initialSeconds[0]); // Set the number of cycles

        // Start the timeline
        timeline.play();
    }

    private void stopCountdown() {
        if (timeline != null && timeline.getStatus() == Animation.Status.RUNNING) {
            timeline.stop();
        }
    }

    private void showCountdownCompleteAlert() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Countdown Complete");
        alert.setHeaderText(null);
        alert.setContentText("Countdown has finished.");
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

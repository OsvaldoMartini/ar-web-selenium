package com.allinweb.ch.tests;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;

public class CountDownApp extends Application {
    private int remainingSeconds = 60; // Set your countdown start time here
    private Timeline timeline;
    private ExecutorService executorService;

    @Override
    public void start(Stage primaryStage) {
        // Create a TextField to display the countdown
        TextField countdownTextField = new TextField(String.valueOf(remainingSeconds));
        countdownTextField.setStyle("-fx-font-size: 24px;");
        countdownTextField.setEditable(false);

        // Create a stack pane to hold the TextField
        StackPane stackPane = new StackPane(countdownTextField);
        stackPane.setPadding(new javafx.geometry.Insets(20));

        // Create a single-threaded executor service
        executorService = Executors.newSingleThreadExecutor();

        // Create a timeline to update the countdown
        timeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            remainingSeconds--;
            countdownTextField.setText(String.valueOf(remainingSeconds));
            if (remainingSeconds <= 0) {
                timeline.stop(); // Stop the timeline when countdown finishes
            }
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();

        // Set up the primary stage
        primaryStage.setTitle("Countdown App");
        primaryStage.setScene(new Scene(stackPane, 300, 200));
        primaryStage.show();
    }

    @Override
    public void stop() {
        // Shut down the executor service
        executorService.shutdownNow();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

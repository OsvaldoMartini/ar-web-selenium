package com.allinweb.ch.tests;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class ProgressBarDecreasingApp extends Application {

    private ProgressBar progressBar;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        progressBar = new ProgressBar(1); // Start with full progress (1.0)

        VBox vbox = new VBox(progressBar);
        vbox.setSpacing(10);

        Scene scene = new Scene(vbox, 300, 100);

        primaryStage.setScene(scene);
        primaryStage.setTitle("Progress Bar Decreasing");
        primaryStage.show();

        startProgressBarDecreasing();
    }

    private void startProgressBarDecreasing() {
        new Thread(() -> {
                    try {
                        while (progressBar.getProgress() > 0) {
                            Thread.sleep(100); // Decrease every 100ms
                            double progress = progressBar.getProgress() - 0.1;
                            progressBar.setProgress(Math.max(progress, 0));
                        }
                    } catch (InterruptedException e) {
                        System.out.println(e.getMessage());
                    }
                })
                .start();
    }
}

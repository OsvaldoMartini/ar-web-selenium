package com.allinweb.ch.tests;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class ProgressBarDynamic_3 extends Application {

    private ExecutorService executorService = Executors.newCachedThreadPool();
    private List<ProgressBar> progressBars = new java.util.ArrayList<>();

    @Override
    public void start(Stage primaryStage) {
        HBox bottomPane = new HBox();

        // Set up the scene and add the bottomPane to the root
        VBox container = new VBox();
        container.getChildren().add(bottomPane);
        Scene scene = new Scene(container, 400, 300);
        primaryStage.setScene(scene);
        primaryStage.show();

        // Example button to trigger the process
        Button addButton = new Button("Add ProgressBars");
        addButton.setOnAction(event -> {
            List<String> listNames =
                    Arrays.asList("John", "Alice", "Bob", "Carol", "David", "Emma", "Frank", "Grace", "Henry", "Ivy");

            for (String name : listNames) {
                addProgressBar(bottomPane, name);
            }
        });
        container.getChildren().add(addButton); // Add button to container
    }

    private void addProgressBar(HBox bottomPane, String name) {
        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(200); // Set preferred width for ProgressBar

        bottomPane.getChildren().add(progressBar);
        progressBars.add(progressBar); // Add ProgressBar to the progressBars list

        // Simulate async task completion with CompletableFuture
        CompletableFuture<Void> future = CompletableFuture.runAsync(
                () -> {
                    try {
                        Random random = new Random();
                        // Simulate random delay between 3 to 10 seconds
                        int delayMillis = random.nextInt(7000) + 3000;
                        TimeUnit.MILLISECONDS.sleep(delayMillis);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                },
                executorService);

        // Handle completion of the CompletableFuture to remove the ProgressBar
        future.thenRun(() -> {
            Platform.runLater(() -> {
                System.out.println("Removing ProgressBar for " + name);
                progressBars.remove(progressBar);
                bottomPane.getChildren().remove(progressBar);
            });
        });
    }

    @Override
    public void stop() {
        // Shutdown the executor service when the application stops
        executorService.shutdown();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

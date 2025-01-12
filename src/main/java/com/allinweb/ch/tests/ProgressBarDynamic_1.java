package com.allinweb.ch.tests;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class ProgressBarDynamic_1 extends Application {

    private ExecutorService executorService = Executors.newCachedThreadPool();
    private List<ProgressBar> progressBars = new ArrayList<>();

    @Override
    public void start(Stage primaryStage) {
        HBox bottomPane = new HBox();

        // Set up the scene and add the bottomPane to the root
        AnchorPane root = new AnchorPane();
        VBox container = new VBox();
        container.getChildren().add(bottomPane);
        root.getChildren().add(container);
        Scene scene = new Scene(root, 300, 200);
        primaryStage.setScene(scene);
        primaryStage.show();

        // Example button to trigger the process
        Button addButton = new Button("Add ProgressBars");
        addButton.setOnAction(event -> {
            addProgress(bottomPane); // Start adding progress bars
        });
        container.getChildren().add(addButton); // Add button to container

        // Set up a listener for the children of bottomPane
        bottomPane.getChildren().addListener((ListChangeListener<Node>) change -> {
            while (change.next()) {
                if (change.wasRemoved()) {
                    for (Node removedNode : change.getRemoved()) {
                        // Find and remove the corresponding ProgressBar from the progressBars list
                        progressBars.removeIf(pb -> pb == removedNode);
                    }
                }
            }
        });
    }

    private void addProgress(HBox bottomPane) {
        // Simulate adding multiple progress bars
        for (int i = 0; i < 5; i++) {
            ProgressBar progressBar = new ProgressBar();
            bottomPane.getChildren().add(progressBar);
            progressBars.add(progressBar); // Add ProgressBar to the progressBars list

            // Schedule the removal of this ProgressBar after a delay
            scheduleRemoval(bottomPane, progressBar, 5000);
        }
    }

    private void scheduleRemoval(HBox bottomPane, ProgressBar progressBar, int delayMillis) {
        CompletableFuture<Void> removalFuture = CompletableFuture.runAsync(
                () -> {
                    try {
                        TimeUnit.MILLISECONDS.sleep(delayMillis); // Wait for the specified delay
                    } catch (InterruptedException e) {
                        System.out.println(e.getMessage());
                    }
                    Platform.runLater(() -> bottomPane
                            .getChildren()
                            .remove(progressBar)); // Remove the ProgressBar on the JavaFX Application Thread
                },
                executorService);

        // Chain an action to be executed after the removal future completes
        removalFuture.thenRun(() -> {
            Platform.runLater(() -> {
                System.out.println("ProgressBar removed: " + progressBar);
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

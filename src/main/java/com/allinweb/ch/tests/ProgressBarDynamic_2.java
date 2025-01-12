package com.allinweb.ch.tests;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
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

public class ProgressBarDynamic_2 extends Application {

    private ExecutorService executorService = Executors.newCachedThreadPool();
    private List<ProgressBar> progressBars = new ArrayList<>();
    private List<CompletableFuture<Void>> futures = new ArrayList<>();

    @Override
    public void start(Stage primaryStage) {
        HBox bottomPane = new HBox();

        // Set up the scene and add the bottomPane to the root
        AnchorPane root = new AnchorPane();
        VBox container = new VBox();
        container.getChildren().add(bottomPane);
        root.getChildren().add(container);
        Scene scene = new Scene(root, 400, 300);
        primaryStage.setScene(scene);
        primaryStage.show();

        // Example button to trigger the process
        Button addButton = new Button("Add ProgressBars");
        addButton.setOnAction(event -> {
            addMultipleProgressBars(bottomPane, 20); // Add 20 progress bars
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

    private void addMultipleProgressBars(HBox bottomPane, int count) {
        Random random = new Random();
        futures.clear(); // Clear the list of previous futures

        for (int i = 0; i < count; i++) {
            ProgressBar progressBar = new ProgressBar();
            bottomPane.getChildren().add(progressBar);
            progressBars.add(progressBar); // Add ProgressBar to the progressBars list

            // Generate a random delay between 3 to 10 seconds (3000 to 10000 milliseconds)
            int delayMillis = random.nextInt(7000) + 3000;
            CompletableFuture<Void> future = CompletableFuture.runAsync(
                    () -> {
                        try {
                            TimeUnit.MILLISECONDS.sleep(delayMillis); // Wait for the specified delay
                        } catch (InterruptedException e) {
                            System.out.println(e.getMessage());
                        }
                    },
                    executorService);

            // Handle completion of the CompletableFuture to remove the ProgressBar
            future.thenRun(() -> {
                Platform.runLater(() -> {
                    System.out.println("Removing ProgressBar after delay");
                    bottomPane
                            .getChildren()
                            .remove(bottomPane
                                    .getChildren()
                                    .get(bottomPane.getChildren().size() - 1));
                });
            });

            futures.add(future); // Add CompletableFuture to the futures list
        }
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

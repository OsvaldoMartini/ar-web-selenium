package com.allinweb.ch.tests;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;

public class ProgressAppBox extends Application {

    private static final int BOX_COUNT = 10;
    private static final double BOX_SIZE = 30;
    private HBox progressBox;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        progressBox = new HBox(5); // Spacing between boxes

        // Initialize with all boxes filled
        for (int i = 0; i < BOX_COUNT; i++) {
            Rectangle rect = new Rectangle(BOX_SIZE, BOX_SIZE, Color.GREEN);
            progressBox.getChildren().add(rect);
        }

        Scene scene = new Scene(progressBox, BOX_COUNT * BOX_SIZE + (BOX_COUNT - 1) * 5, BOX_SIZE + 10);

        primaryStage.setScene(scene);
        primaryStage.setTitle("Box Progress");
        primaryStage.show();

        startBoxProgressDecreasing();
    }

    private void startBoxProgressDecreasing() {
        new Thread(() -> {
                    try {
                        while (progressBox.getChildren().size() > 0) {
                            Thread.sleep(500); // Decrease every 500ms
                            progressBox
                                    .getChildren()
                                    .remove(progressBox.getChildren().size() - 1);
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                })
                .start();
    }
}

package com.allinweb.ch.tests;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

public class HboxWithButtonLeftPos extends Application {

    @Override
    public void start(Stage primaryStage) {
        // Create an HBox
        HBox hbox = new HBox();

        // Create a button
        Button button = new Button("Button");

        // Create a region to act as a spacer for left position
        Region spacer = new Region();
        spacer.setMinWidth(50); // Set the minimum width to 50 pixels

        // Add the button and spacer to the HBox
        hbox.getChildren().addAll(spacer, button);

        // Create a Scene
        Scene scene = new Scene(hbox, 300, 100);

        // Set the Scene to the Stage
        primaryStage.setScene(scene);

        // Set the title of the Stage
        primaryStage.setTitle("Button with Initial Left Position");

        // Show the Stage
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

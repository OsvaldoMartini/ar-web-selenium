package com.allinweb.ch.tests;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

public class HBoxWithButtons extends Application {

    @Override
    public void start(Stage primaryStage) {
        // Create an HBox
        HBox hbox = new HBox();

        // Create buttons
        Button leftButton = new Button("Left");
        Button centerButton = new Button("Center");
        Button rightButton = new Button("Right");

        // Create spacer regions
        Region spacer1 = new Region();
        Region spacer2 = new Region();

        // Set the initial left position for the first button
        HBox.setMargin(leftButton, new javafx.geometry.Insets(0, 0, 0, 30));
        HBox.setMargin(rightButton, new javafx.geometry.Insets(0, 30, 0, 0));

        // Set Hgrow for spacer regions to make them equally distributed
        HBox.setHgrow(spacer1, Priority.ALWAYS);
        HBox.setHgrow(spacer2, Priority.ALWAYS);

        // Add buttons and spacer regions to the HBox
        hbox.getChildren().addAll(leftButton, spacer1, centerButton, spacer2, rightButton);

        // Create a Scene
        Scene scene = new Scene(hbox, 400, 100);

        // Set the Scene to the Stage
        primaryStage.setScene(scene);

        // Set the title of the Stage
        primaryStage.setTitle("Buttons in HBox - Equally Distributed");

        // Show the Stage
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

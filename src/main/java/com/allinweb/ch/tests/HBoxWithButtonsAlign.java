package com.allinweb.ch.tests;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

public class HBoxWithButtonsAlign extends Application {

    @Override
    public void start(Stage primaryStage) {
        // Create an HBox
        HBox hbox = new HBox();

        // Create buttons
        Button leftButton = new Button("Left");
        Button centerButton = new Button("Center");
        Button rightButton = new Button("Right");

        // Create spacer region
        Region spacer = new Region();

        // Set the margin for the first button to start at position 30
        HBox.setMargin(leftButton, new javafx.geometry.Insets(0, 0, 0, 30));

        // Set Hgrow for spacer to make it expand and push the last button to the right edge
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Add buttons and spacer to the HBox
        hbox.getChildren().addAll(leftButton, centerButton, spacer, rightButton);

        // Create a Scene
        Scene scene = new Scene(hbox, 400, 100);

        // Set the Scene to the Stage
        primaryStage.setScene(scene);

        // Set the title of the Stage
        primaryStage.setTitle("Buttons in HBox - Customized Positions");

        // Show the Stage
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

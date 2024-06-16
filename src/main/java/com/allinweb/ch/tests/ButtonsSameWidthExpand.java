package com.allinweb.ch.tests;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

public class ButtonsSameWidthExpand extends Application {

    @Override
    public void start(Stage primaryStage) {
        // Create buttons
        Button button1 = new Button("Button 1");
        Button button2 = new Button("Button 2");
        Button button3 = new Button("Button 3");
        Button button4 = new Button("Button 4");

        // Create Region spacers
        Region spacer1 = new Region();
        Region spacer2 = new Region();
        Region spacer3 = new Region();

        // Set HBox.hgrow to make buttons and spacers expand horizontally
        HBox.setHgrow(button1, Priority.ALWAYS);
        HBox.setHgrow(button2, Priority.ALWAYS);
        HBox.setHgrow(button3, Priority.ALWAYS);
        HBox.setHgrow(button4, Priority.ALWAYS);

        // Create an HBox for buttons and spacers
        HBox hbox = new HBox();
        hbox.setSpacing(10); // Optional spacing between nodes
        hbox.setPadding(new Insets(10)); // Optional padding

        // Add buttons and spacers to the HBox
        hbox.getChildren().addAll(button1, spacer1, button2, spacer2, button3, spacer3, button4);

        // Create a scene
        Scene scene = new Scene(hbox, 400, 100);

        // Set the scene and show the stage
        primaryStage.setScene(scene);
        primaryStage.setTitle("Same Width Buttons Example");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

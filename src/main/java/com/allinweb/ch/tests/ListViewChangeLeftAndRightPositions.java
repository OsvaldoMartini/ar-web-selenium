package com.allinweb.ch.tests;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

public class ListViewChangeLeftAndRightPositions extends Application {

    @Override
    public void start(Stage primaryStage) {
        // Create observable array lists with sample data
        ObservableList<String> list1 = FXCollections.observableArrayList("Item 1", "Item 2", "Item 3");
        ObservableList<String> list2 = FXCollections.observableArrayList("Apple", "Banana", "Orange");

        // Create ListViews
        ListView<String> listView1 = new ListView<>(list1);
        ListView<String> listView2 = new ListView<>(list2);

        // Create Buttons
        Button moveRightButton = new Button("Move List2 Right");
        Button moveLeftButton = new Button("Move List2 Left");

        // Create HBox to hold the buttons
        HBox buttonBox = new HBox(10); // 10 pixels spacing between buttons
        buttonBox.getChildren().addAll(moveRightButton, moveLeftButton);

        // Create AnchorPane
        AnchorPane anchorPane = new AnchorPane();

        // Set the layout constraints for the Button HBox
        AnchorPane.setTopAnchor(buttonBox, 0.0);
        AnchorPane.setLeftAnchor(buttonBox, 10.0);

        // Add ListViews and Buttons to AnchorPane
        anchorPane.getChildren().addAll(listView1, listView2, buttonBox);

        // Set the layout constraints for the first ListView
        AnchorPane.setLeftAnchor(listView1, 10.0);
        AnchorPane.setTopAnchor(listView1, AnchorPane.getBottomAnchor(buttonBox));

        // Set the layout constraints for the second ListView
        AnchorPane.setTopAnchor(listView2, AnchorPane.getBottomAnchor(buttonBox));
        // Set the initial left anchor for the second ListView
        AnchorPane.setLeftAnchor(listView2, 10.0 + AnchorPane.getLeftAnchor(listView1));

        // Add event handler for the "Move List2 Right" button
        moveRightButton.setOnAction(event -> {
            // Move listView2 right by changing its left anchor
            double newLeftAnchor = AnchorPane.getLeftAnchor(listView2) + 10; // Move listView2 right by 10 pixels
            AnchorPane.setLeftAnchor(listView2, newLeftAnchor);
        });

        // Add event handler for the "Reset List2 Position" button
        moveLeftButton.setOnAction(event -> {
            // Reset listView2 to its initial position
            AnchorPane.setLeftAnchor(listView2, AnchorPane.getLeftAnchor(listView2) - 10.0);
        });

        // Set up the scene
        Scene scene = new Scene(anchorPane, 500, 300);

        // Set the scene to the stage and show the stage
        primaryStage.setScene(scene);
        primaryStage.setTitle("ListView Side By Side");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

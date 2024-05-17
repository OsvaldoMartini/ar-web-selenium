package com.allinweb.ch.tests;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;

public class ListViewAddText extends Application {

    @Override
    public void start(Stage primaryStage) {
        // Create observable array lists with sample data
        ObservableList<String> list1 = FXCollections.observableArrayList("Item 1", "Item 2", "Item 3");
        ObservableList<String> list2 = FXCollections.observableArrayList("Apple", "Banana", "Orange");

        // Create ListViews
        ListView<String> listView1 = new ListView<>(list1);
        ListView<String> listView2 = new ListView<>(list2);

        // Create TextField and Button for adding text
        TextField textField = new TextField();
        Button addButton = new Button("Add Text");

        // Create AnchorPane
        AnchorPane anchorPane = new AnchorPane();

        // Add ListViews, TextField, and Button to AnchorPane
        anchorPane.getChildren().addAll(listView1, listView2, textField, addButton);

        // Set the layout constraints for the first ListView
        AnchorPane.setLeftAnchor(listView1, 10.0);
        AnchorPane.setTopAnchor(listView1, 10.0);

        // Set the layout constraints for the second ListView
        AnchorPane.setTopAnchor(listView2, 10.0);

        // Set the initial left anchor for the second ListView
        AnchorPane.setLeftAnchor(listView2, 10.0 + listView1.getBoundsInLocal().getWidth() + 5.0);

        // Set the layout constraints for the TextField
        AnchorPane.setLeftAnchor(textField, 10.0);
        AnchorPane.setTopAnchor(textField, 50.0);

        // Set the layout constraints for the Add Button
        AnchorPane.setLeftAnchor(addButton, 150.0);
        AnchorPane.setTopAnchor(addButton, 50.0);

        // Add event handler for the Add Button
        addButton.setOnAction(event -> {
            String textToAdd = textField.getText();
            if (!textToAdd.isEmpty()) {
                list2.add(textToAdd);
                textField.clear();
            }
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

package com.allinweb.ch.tests;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class ListViewClickExample extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Create data for the ListView
        ObservableList<String> items = FXCollections.observableArrayList("a", "b", "c");

        // Create the ListView
        ListView<String> listView = new ListView<>(items);

        // Handle mouse click event on ListView items
        listView.setOnMouseClicked(event -> {
            String selectedItem = listView.getSelectionModel().getSelectedItem();
            if (selectedItem != null) {
                // Print the selected item
                System.out.println("Clicked on item: " + selectedItem);
            }
        });

        // Create a VBox to hold the ListView
        VBox root = new VBox(listView);

        // Create the Scene
        Scene scene = new Scene(root, 200, 200);

        // Set the Scene to the Stage
        primaryStage.setScene(scene);

        // Set the title of the Stage
        primaryStage.setTitle("ListView Example");

        // Show the Stage
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

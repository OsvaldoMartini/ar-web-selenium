package com.allinweb.ch.tests;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class UpdateListViewOtherPage extends Application {

    @Override
    public void start(Stage primaryStage) {
        // ObservableList to store items
        ObservableList<String> items = FXCollections.observableArrayList("Item 1", "Item 2", "Item 3");

        // ListView to display items
        ListView<String> listView = new ListView<>(items);

        // Button to add items
        Button addButton = new Button("Add Item");
        addButton.setOnAction(event -> {
            items.add("New Item");
        });

        // Button to remove items
        Button removeButton = new Button("Remove Item");
        removeButton.setOnAction(event -> {
            int selectedIndex = listView.getSelectionModel().getSelectedIndex();
            if (selectedIndex >= 0) {
                items.remove(selectedIndex);
            }
        });

        // Pane to contain ListView
        VBox listViewPane = new VBox(listView, new HBox(addButton, removeButton));

        // Pane to contain buttons to update ListView
        VBox updatePane = new VBox();
        Button updateButton = new Button("Update ListView");
        updateButton.setOnAction(event -> {
            items.setAll("Updated Item 1", "Updated Item 2", "Updated Item 3");
        });
        updatePane.getChildren().add(updateButton);

        // Layout to contain both panes
        HBox root = new HBox(listViewPane, updatePane);

        primaryStage.setScene(new Scene(root, 400, 200));
        primaryStage.setTitle("Observable ListView Example");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

package com.allinweb.ch.tests;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

public class ObservableArrayList extends Application {

    @Override
    public void start(Stage primaryStage) {
        // Creating two ObservableArrayLists
        var list1 = FXCollections.observableArrayList("Item 1", "Item 2", "Item 3");
        var list2 = FXCollections.observableArrayList("Apple", "Banana", "Orange");
        var list3 = FXCollections.observableArrayList("Afghanistan", "Albania", "Algeria");

        // Creating ListViews for each list
        var listView1 = new ListView<>(list1);
        var listView2 = new ListView<>(list2);
        var listView3 = new ListView<>(list3);

        // Creating a layout to hold the ListViews side by side
        var root = new HBox(10);
        root.getChildren().addAll(listView1, listView2, listView3);

        // Setting up the scene and showing the stage
        var scene = new Scene(root, 400, 300);
        primaryStage.setScene(scene);
        primaryStage.setTitle("ObservableArrayList App");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

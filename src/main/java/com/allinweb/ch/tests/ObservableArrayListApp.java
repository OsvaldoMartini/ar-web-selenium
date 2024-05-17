package com.allinweb.ch.tests;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

public class ObservableArrayListApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        // Create observable array lists with sample data
        ObservableList<String> list1 = FXCollections.observableArrayList("Item 1", "Item 2", "Item 3");
        ObservableList<String> list2 = FXCollections.observableArrayList("Apple", "Banana", "Orange");
        ObservableList<String> list3 = FXCollections.observableArrayList("Dog", "Cat", "Bird");

        // Create list views to display the observable lists
        ListView<String> listView1 = new ListView<>(list1);
        ListView<String> listView2 = new ListView<>(list2);
        ListView<String> listView3 = new ListView<>(list3);

        // Create a Pane to hold the ListViews side by side
        Pane pane = new HBox(10);
        pane.getChildren().addAll(listView1, listView2, listView3);

        // Set up the scene
        Scene scene = new Scene(pane, 600, 300);

        // Set the scene to the stage and show the stage
        primaryStage.setScene(scene);
        primaryStage.setTitle("ObservableArrayList App");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

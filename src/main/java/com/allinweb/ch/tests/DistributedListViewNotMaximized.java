package com.allinweb.ch.tests;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

public class DistributedListViewNotMaximized extends Application {

    @Override
    public void start(Stage primaryStage) {
        // Create an HBox
        HBox hbox = new HBox();
        hbox.setMaxWidth(600); // Set maximum width for HBox

        // Create three ListViews
        ListView<String> listView1 = new ListView<>();
        ListView<String> listView2 = new ListView<>();
        ListView<String> listView3 = new ListView<>();

        // Add items to the ListViews (optional)
        listView1.getItems().addAll("Item 1", "Item 2", "Item 3");
        listView2.getItems().addAll("Item A", "Item B", "Item C");
        listView3.getItems().addAll("Apple", "Banana", "Orange");

        // Bind the height of ListViews to the height of the HBox
        listView1.prefHeightProperty().bind(hbox.heightProperty());
        listView2.prefHeightProperty().bind(hbox.heightProperty());
        listView3.prefHeightProperty().bind(hbox.heightProperty());

        // Set HBox properties
        hbox.setSpacing(10); // Optional: Set spacing between ListViews

        // Add ListViews to the HBox
        hbox.getChildren().addAll(listView1, listView2, listView3);

        // Create a Scene
        Scene scene = new Scene(hbox, 600, 400);

        // Set the Scene to the Stage
        primaryStage.setScene(scene);

        // Set the title of the Stage
        primaryStage.setTitle("Three ListViews in HBox - Equally Distributed and Height Matched");

        // Show the Stage
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

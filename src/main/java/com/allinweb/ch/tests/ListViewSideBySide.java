package com.allinweb.ch.tests;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;

public class ListViewSideBySide extends Application {

    @Override
    public void start(Stage primaryStage) {
        // Create observable array lists with sample data
        ObservableList<String> list1 = FXCollections.observableArrayList("Item 1", "Item 2", "Item 3");
        ObservableList<String> list2 = FXCollections.observableArrayList("Apple", "Banana", "Orange");

        // Create ListViews
        ListView<String> listView1 = new ListView<>(list1);
        ListView<String> listView2 = new ListView<>(list2);

        // Create AnchorPane
        AnchorPane anchorPane = new AnchorPane();

        // Add ListViews to AnchorPane
        anchorPane.getChildren().addAll(listView1, listView2);

        // Set the layout constraints for the first ListView
        AnchorPane.setLeftAnchor(listView1, 10.0);
        AnchorPane.setTopAnchor(listView1, 10.0);

        // Set the layout constraints for the second ListView
        AnchorPane.setTopAnchor(listView2, 10.0);

        // Get the width of the first ListView
        double listView1Width = listView1.getBoundsInLocal().getWidth();

        // Set the left anchor for the second ListView based on the width of the first ListView plus 5 pixels
        AnchorPane.setLeftAnchor(listView2, 10.0 + listView1Width + 5.0); // Add 5.0 for the additional gap

        // Set up the scene
        Scene scene = new Scene(anchorPane, 400, 300);

        // Set the scene to the stage and show the stage
        primaryStage.setScene(scene);
        primaryStage.setTitle("ListView Side By Side");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

package com.allinweb.ch.component.scene;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class WebViewExample extends Application {

    @Override
    public void start(Stage primaryStage) {
        // Create three AnchorPane elements
        AnchorPane pane1 = createAnchorPane("Pane 1", 50, 50);
        AnchorPane pane2 = createAnchorPane("Pane 2", 200, 100);
        AnchorPane pane3 = createAnchorPane("Pane 3", 350, 150);

        // Create a layout to hold the AnchorPanes
        AnchorPane root = new AnchorPane();
        root.getChildren().addAll(pane1, pane2, pane3);

        // Create a Scene
        Scene scene = new Scene(root, 500, 300);

        // Set the Scene to the Stage
        primaryStage.setScene(scene);

        // Set the title of the Stage
        primaryStage.setTitle("Three AnchorPanes Example");

        // Show the Stage
        primaryStage.show();

        // Create sample data for the ListView
        ObservableList<String> data =
                FXCollections.observableArrayList("Item 1", "Item 2", "Item 3", "Item 4", "Item 5");

        // Create a ListView and populate it with the sample data
        ListView<String> listView = new ListView<>(data);

        // Create a layout to hold the ListView
        VBox rootVbox = new VBox(listView);

        // Create a Scene
        Scene scene2 = new Scene(rootVbox, 200, 200);

        // Set the Scene to the Stage
        primaryStage.setScene(scene2);

        // Set the title of the Stage
        primaryStage.setTitle("ListView Example");

        // Show the Stage
        primaryStage.show();
    }

    // Helper method to create an AnchorPane with a label
    private AnchorPane createAnchorPane(String label, double layoutX, double layoutY) {
        AnchorPane pane = new AnchorPane();
        pane.setPrefSize(100, 100);
        pane.setLayoutX(layoutX);
        pane.setLayoutY(layoutY);

        Label titleLabel = new Label(label);
        pane.getChildren().add(titleLabel);
        AnchorPane.setTopAnchor(titleLabel, 10.0);
        AnchorPane.setLeftAnchor(titleLabel, 10.0);

        return pane;
    }

    public static void main(String[] args) {
        launch(args);
    }
}

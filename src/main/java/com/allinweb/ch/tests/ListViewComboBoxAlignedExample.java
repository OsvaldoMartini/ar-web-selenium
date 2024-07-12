package com.allinweb.ch.tests;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class ListViewComboBoxAlignedExample extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Create data for the ListView
        ObservableList<String> items = FXCollections.observableArrayList("a", "b", "c");

        // Create the ListView
        ListView<String> listView = new ListView<>(items);

        // Set a custom cell factory to include ComboBox
        listView.setCellFactory(param -> new ListCell<String>() {
            private final ComboBox<String> comboBox = new ComboBox<>(items);

            {
                // Setup ComboBox in each cell
                comboBox.setOnAction(event -> {
                    System.out.println("Selected item in ComboBox: " + comboBox.getValue());
                });

                // Align ComboBox to the right
                HBox.setHgrow(comboBox, Priority.ALWAYS);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    // Create label for the ListView item
                    Label label = new Label(item);

                    // Create HBox to hold label and ComboBox
                    HBox hbox = new HBox(label, comboBox);
                    hbox.setSpacing(10);
                    hbox.setFillHeight(true); // Ensure HBox fills the cell height
                    HBox.setHgrow(comboBox, Priority.ALWAYS); // Align ComboBox to the right
                    HBox.setHgrow(hbox, Priority.ALWAYS); // Ensure HBox fills the cell width

                    // Align label to the left
                    HBox.setHgrow(label, Priority.ALWAYS);

                    // Set the cell's graphic to the HBox
                    setGraphic(hbox);
                }
            }
        });

        // Create a VBox to hold the ListView
        VBox root = new VBox(listView);

        // Create the Scene
        Scene scene = new Scene(root, 400, 300);

        // Set the Scene to the Stage
        primaryStage.setScene(scene);

        // Set the title of the Stage
        primaryStage.setTitle("ListView with Aligned ComboBox Example");

        // Show the Stage
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

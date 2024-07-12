package com.allinweb.ch.tests;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class CustomComboBoxExample extends Application {

    @Override
    public void start(Stage primaryStage) {
        // List of items for ComboBox
        String[] items = {"a", "b", "c"};

        // Create a VBox for the clickable images and labels
        VBox setValueBox = createImageWithLabel("Icon_Set_Value.png", "set value to variable", () -> {
            System.out.println("Set Value clicked");
        });
        VBox getValueBox = createImageWithLabel("Icon_Get_Value.png", "get value from element", () -> {
            System.out.println("Get Value clicked");
        });
        VBox variablesBox = createImageWithLabel("Icon_Variables.png", "define variables", () -> {
            System.out.println("Variables clicked");
        });

        // Create a VBox to hold the clickable components
        VBox componentsVBox = new VBox(10, variablesBox, setValueBox, getValueBox);

        // Create a ComboBox
        ComboBox<String> comboBox = new ComboBox<>(FXCollections.observableArrayList(items));
        comboBox.setPrefWidth(250); // Set preferred width of ComboBox
        comboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? "components" : null);
            }
        });
        comboBox.setPromptText("components");

        // Set the cell factory to display VBox when ComboBox is expanded
        comboBox.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    setGraphic(componentsVBox);
                }
            }
        });

        // Create the scene
        Scene scene = new Scene(comboBox, 400, 300);
        scene.getStylesheets().add(getClass().getResource("/combobox.css").toExternalForm());

        // Set the stage
        primaryStage.setScene(scene);
        primaryStage.setTitle("Custom ComboBox Example");
        primaryStage.show();
    }

    // Method to create VBox with clickable images and labels
    private VBox createImageWithLabel(String iconPath, String labelText, Runnable action) {
        // Implementation details for creating VBox with image and label
        // Replace with your actual implementation
        return new VBox();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

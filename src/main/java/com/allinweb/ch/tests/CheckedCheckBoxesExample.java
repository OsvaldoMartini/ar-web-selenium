package com.allinweb.ch.tests;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

public class CheckedCheckBoxesExample extends Application {

    @Override
    public void start(Stage primaryStage) {
        // Create the GridPane layout
        GridPane gridPane = new GridPane();
        gridPane.setPadding(new Insets(20));
        gridPane.setHgap(10);
        gridPane.setVgap(10);

        // Create the Button and set it disabled initially
        Button button = new Button("Click Me");
        button.setDisable(true);
        GridPane.setConstraints(button, 0, 0); // Column 0, Row 0
        GridPane.setValignment(button, javafx.geometry.VPos.TOP);

        // Create the first CheckBox, start checked
        CheckBox checkBox1 = new CheckBox("CheckBox 1");
        checkBox1.setSelected(true); // Start checked
        GridPane.setConstraints(checkBox1, 1, 0); // Column 1, Row 0

        // Create the second CheckBox, start checked
        CheckBox checkBox2 = new CheckBox("CheckBox 2");
        checkBox2.setSelected(false); // Start checked
        GridPane.setConstraints(checkBox2, 1, 1); // Column 1, Row 1

        // Logic to enable/disable the button based on checkbox selection
        checkBox1.setOnAction(event -> updateButtonState(button, checkBox1, checkBox2));
        checkBox2.setOnAction(event -> updateButtonState(button, checkBox1, checkBox2));

        // Add controls to the GridPane
        gridPane.getChildren().addAll(button, checkBox1, checkBox2);

        // Set up the scene and show the stage
        Scene scene = new Scene(gridPane, 300, 200);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Checked Checkboxes Example");
        primaryStage.show();
    }

    // Method to update button state based on checkbox selection
    private void updateButtonState(Button button, CheckBox checkBox1, CheckBox checkBox2) {
        if (checkBox1.isSelected() || checkBox2.isSelected()) {
            button.setDisable(false); // Enable the button if any checkbox is selected
        } else {
            button.setDisable(true); // Disable the button if no checkbox is selected
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

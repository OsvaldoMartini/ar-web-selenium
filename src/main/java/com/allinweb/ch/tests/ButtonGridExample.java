package com.allinweb.ch.tests;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;

public class ButtonGridExample extends Application {

    @Override
    public void start(Stage primaryStage) {
        // Create buttons
        Button refreshInputFieldsButton = new Button("Refresh Input Fields");
        Button searchWithIdsButton = new Button("Search With IDs");
        Button searchWithNamesButton = new Button("Search With Names");
        Button searchWithoutIdsAndNamesButton = new Button("Search Without IDs and Names");
        Button refreshOutputFieldsButton = new Button("Refresh Output Fields");
        Button refreshOtherFieldsButton = new Button("Refresh Other Fields");

        // Create checkbox
        CheckBox checkBoxAction = new CheckBox("Action");

        // Create a GridPane
        GridPane gridPane = new GridPane();
        gridPane.setPadding(new Insets(10));
        gridPane.setHgap(10); // Set horizontal gap between columns

        // Add buttons and checkbox to the GridPane
        gridPane.add(refreshInputFieldsButton, 0, 0);
        gridPane.add(searchWithIdsButton, 1, 0);
        gridPane.add(searchWithNamesButton, 2, 0);
        gridPane.add(searchWithoutIdsAndNamesButton, 3, 0);
        gridPane.add(refreshOutputFieldsButton, 4, 0);
        gridPane.add(refreshOtherFieldsButton, 5, 0);
        gridPane.add(checkBoxAction, 6, 0); // Add checkbox in the same row but a separate column

        // Set Hgrow for each button and checkbox to make them equally distributed
        GridPane.setHgrow(refreshInputFieldsButton, Priority.ALWAYS);
        GridPane.setHgrow(searchWithIdsButton, Priority.ALWAYS);
        GridPane.setHgrow(searchWithNamesButton, Priority.ALWAYS);
        GridPane.setHgrow(searchWithoutIdsAndNamesButton, Priority.ALWAYS);
        GridPane.setHgrow(refreshOutputFieldsButton, Priority.ALWAYS);
        GridPane.setHgrow(refreshOtherFieldsButton, Priority.ALWAYS);
        GridPane.setHgrow(checkBoxAction, Priority.ALWAYS);

        // Set the minimum width for each button and checkbox to ensure they expand
        refreshInputFieldsButton.setMaxWidth(Double.MAX_VALUE);
        searchWithIdsButton.setMaxWidth(Double.MAX_VALUE);
        searchWithNamesButton.setMaxWidth(Double.MAX_VALUE);
        searchWithoutIdsAndNamesButton.setMaxWidth(Double.MAX_VALUE);
        refreshOutputFieldsButton.setMaxWidth(Double.MAX_VALUE);
        refreshOtherFieldsButton.setMaxWidth(Double.MAX_VALUE);
        checkBoxAction.setMaxWidth(Double.MAX_VALUE);

        // Create the scene and set it on the stage
        Scene scene = new Scene(gridPane, 900, 100); // Adjusted width to accommodate all elements
        primaryStage.setScene(scene);
        primaryStage.setTitle("Button Grid Example");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

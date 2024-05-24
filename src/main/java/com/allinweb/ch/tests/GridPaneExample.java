package com.allinweb.ch.tests;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

public class GridPaneExample extends Application {

    @Override
    public void start(Stage primaryStage) {
        // Create buttons
        Button refreshButton = new Button("Refresh");
        Button openScannerButton = new Button("Open Scanner");
        Button editBotJobButton = new Button("Edit Bot Job");
        Button launchBotJobButton = new Button("Launch Bot Job");
        Button saveBotJobButton = new Button("Save Bot Job");
        Button saveAsBotJobButton = new Button("Save As Bot Job");
        Button openExcelFileButton = new Button("Open Excel File");
        Button generateExcelButton = new Button("Generate Excel");
        Button openExcelFilterPanelButton = new Button("Open Excel Filter Panel");
        Button closeBotJobButton = new Button("Close Bot Job");
        CheckBox checkBoxUpdatePriority = new CheckBox("Update Priority");

        // Create a GridPane
        GridPane gridPane = new GridPane();

        // Add buttons to the first row
        gridPane.add(refreshButton, 0, 0);
        gridPane.add(openScannerButton, 1, 0);
        gridPane.add(editBotJobButton, 2, 0);
        gridPane.add(launchBotJobButton, 3, 0);
        gridPane.add(saveBotJobButton, 4, 0);
        gridPane.add(saveAsBotJobButton, 5, 0);
        gridPane.add(openExcelFileButton, 6, 0);
        gridPane.add(generateExcelButton, 7, 0);
        gridPane.add(openExcelFilterPanelButton, 8, 0);
        gridPane.add(closeBotJobButton, 9, 0);

        // Add checkBoxUpdatePriority below saveBotJobButton
        gridPane.add(checkBoxUpdatePriority, 4, 1);

        // Create a Scene and add the GridPane to it
        Scene scene = new Scene(gridPane, 800, 200);

        // Configure the Stage
        primaryStage.setTitle("GridPane Example");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

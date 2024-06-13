package com.allinweb.ch.tests;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public class GridPaneExampleLOG extends Application {

    private Label pathLogLabel;
    private TextField pathLog;
    private Button pathLogButton;
    private Label sizeLogLabel;
    private TextField sizeLog;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("GridPane Example");

        pathLogLabel = new Label("Log Path:");
        pathLog = createPathTextField(ABRPropertyEnum.FOLDER_PATH_LOG);
        pathLogButton = createPathButton();
        sizeLogLabel = new Label("Size Log");
        sizeLog = createPathTextField(ABRPropertyEnum.FOLDER_PATH_LOG);

        GridPane gridPane = new GridPane();
        gridPane.setVgap(10);
        gridPane.setHgap(10);

        // Set column constraints for pathLog (80%), sizeLog (15%), and pathLogButton (5%)
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(80);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(15);
        ColumnConstraints col3 = new ColumnConstraints();
        col3.setPercentWidth(5);
        gridPane.getColumnConstraints().addAll(col1, col2, col3);

        // Add labels in the first row
        gridPane.add(pathLogLabel, 0, 0);
        gridPane.add(sizeLogLabel, 1, 0);

        // Add text fields in the second row
        gridPane.add(pathLog, 0, 1);
        gridPane.add(sizeLog, 1, 1);

        // Add button in the second row, third column
        gridPane.add(pathLogButton, 2, 1);

        // Set margin for pathLogButton to create spacing from right border
        GridPane.setMargin(pathLogButton, new Insets(0, 0, 0, 5));

        Scene scene = new Scene(gridPane, 600, 200);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private TextField createPathTextField(ABRPropertyEnum property) {
        TextField textField = new TextField();
        // Mock implementation to set text for pathLog and sizeLog
        textField.setText("Path or Size");
        return textField;
    }

    private Button createPathButton() {
        Button button = new Button("Choose Path");
        // Mock implementation for button action
        return button;
    }
}

// Mock Enum to make this code compile
enum ABRPropertyEnum {
    FOLDER_PATH_LOG
}

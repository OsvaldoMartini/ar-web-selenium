package com.allinweb.ch.tests;

import java.io.File;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

public class FileChooserExample extends Application {

    private TextArea textArea;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("File Chooser Example");

        textArea = new TextArea();
        textArea.setEditable(false);

        Button openButton = new Button("Open Directory");
        openButton.setOnAction(e -> openDirectoryChooser(primaryStage));

        BorderPane root = new BorderPane();
        root.setTop(openButton);
        root.setCenter(textArea);

        Scene scene = new Scene(root, 600, 400);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void openDirectoryChooser(Stage primaryStage) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Directory");

        File selectedDirectory = directoryChooser.showDialog(primaryStage);
        if (selectedDirectory != null) {
            displayFilesInDirectory(selectedDirectory);
        }
    }

    private void displayFilesInDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            textArea.clear(); // Clear previous contents
            for (File file : files) {
                textArea.appendText(file.getAbsolutePath() + "\n");
            }
        } else {
            textArea.setText("No files found in the selected directory.");
        }
    }
}

package com.allinweb.ch.javafxapp;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class JavaFxApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        TextField textField = new TextField();
        textField.setPromptText("Enter text here");

        StackPane root = new StackPane();
        root.getChildren().add(textField);

        Scene scene = new Scene(root, 300, 200);

        primaryStage.setTitle("JavaFX Text Box Example");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

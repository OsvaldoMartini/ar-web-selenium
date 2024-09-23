package com.allinweb.ch.tests;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

public class JavaFXWithReactApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        // Create the WebView
        WebView webView = new WebView();
        WebEngine webEngine = webView.getEngine();

        // Load the React app - this could be a URL or a local file
        // Load from a local file (you need to build your React app first and use its output)
        webEngine.load(getClass().getResource("/build/index.html").toExternalForm());
        // Alternatively, you can load from a server URL
        // webEngine.load("http://localhost:3000"); // Example URL of a React development server

        // Create the layout
        BorderPane root = new BorderPane();
        root.setCenter(webView);

        Scene scene = new Scene(root, 800, 600);

        // Set up the stage
        primaryStage.setTitle("JavaFX with React");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

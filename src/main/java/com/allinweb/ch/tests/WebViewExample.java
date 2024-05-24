//package com.allinweb.ch.tests;
//
//import javafx.application.Application;
//import javafx.scene.Scene;
//import javafx.scene.layout.StackPane;
//import javafx.scene.web.WebEngine;
//import javafx.scene.web.WebView;
//import javafx.stage.Stage;
//
//public class WebViewExample extends Application {
//
//    @Override
//    public void start(Stage primaryStage) {
//        // Create a WebView
//        WebView webView = new WebView();
//
//        // Load a webpage
//        WebEngine webEngine = webView.getEngine();
//        webEngine.load("https://www.fnz.com/contact");
//
//        // Create a layout and add the WebView to it
//        StackPane root = new StackPane();
//        root.getChildren().add(webView);
//
//        // Create a Scene
//        Scene scene = new Scene(root, 800, 600);
//
//        // Set the Scene to the Stage
//        primaryStage.setScene(scene);
//
//        // Set the title of the Stage
//        primaryStage.setTitle("JavaFX WebView Example");
//
//        // Show the Stage
//        primaryStage.show();
//    }
//
//    public static void main(String[] args) {
//        launch(args);
//    }
//}

package com.allinweb.ch.tests;

import javafx.application.Application;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

public class NavigatingElements extends Application {

    private WebEngine webEngine;
    private int currentIndex = 0; // To keep track of the current index of elements on the page

    @Override
    public void start(Stage primaryStage) {
        // Create buttons
        Button btnPrevious = new Button("Previous");
        Button btnNext = new Button("Next");

        // WebView and WebEngine setup
        WebView webView = new WebView();
        webEngine = webView.getEngine();

        // Load the HTML page
        webEngine.load(getClass().getResource("/build/index.html").toExternalForm());

        // Add listener to execute the script after the page has loaded
        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                // Initialize or reset the navigation when the page is loaded
                initializeNavigation();
            }
        });

        // Handle "Previous" button action
        btnPrevious.setOnAction(e -> navigatePrevious());

        // Handle "Next" button action
        btnNext.setOnAction(e -> navigateNext());

        // Layout for buttons
        HBox layout = new HBox(10); // 10px spacing between buttons
        layout.getChildren().addAll(btnPrevious, btnNext);

        // Layout for WebView and buttons
        HBox mainLayout = new HBox(10);
        mainLayout.getChildren().addAll(layout, webView);

        // Scene setup
        Scene scene = new Scene(mainLayout, 800, 600);
        primaryStage.setTitle("Navigating Elements");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void initializeNavigation() {
        // Reset or initialize the current index if needed
        currentIndex = 0; // For example, start from the first element
    }

    private void navigatePrevious() {
        // Decrease the current index and execute the JavaScript to navigate to the previous element
        if (currentIndex > 0) {
            currentIndex--;
            executeJavaScriptToNavigate(currentIndex);
        }
    }

    private void navigateNext() {
        // Increase the current index and execute the JavaScript to navigate to the next element
        currentIndex++;
        executeJavaScriptToNavigate(currentIndex);
    }

    private void executeJavaScriptToNavigate(int index) {
        try {
            // Execute the JavaScript to navigate the page, such as scrolling or changing elements
            String script = "window.navigateElement(" + index
                    + ");"; // Assuming you have a function in the HTML page called `navigateElement`
            webEngine.executeScript(script);
        } catch (Exception e) {
            System.err.println("Error executing JavaScript: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

package com.allinweb.ch.tests;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.edge.EdgeDriver;

public class TabSwitcherApp extends Application {
    private WebDriver driver; // Selenium WebDriver instance
    private List<String> windowHandlesList; // List to store browser tab handles
    private int currentTabIndex = 0; // Track the currently active tab index

    private Button leftButton; // Button for switching to previous tab
    private Button rightButton; // Button for switching to next tab

    @Override
    public void start(Stage primaryStage) {
        // Initialize EdgeDriver with the correct path to msedgedriver
        System.setProperty(
                "webdriver.edge.driver",
                "D:\\Projects\\AllinWeb\\abr-web-selenium-archive\\abr-web-selenium-files\\ProgramFiles\\edgedriver-versions\\msedgedriver_64-(129.0.2792.65).exe");

        driver = new EdgeDriver();
        driver.get("https://www.google.com"); // Open first tab (Google)

        // Open a second tab
        ((EdgeDriver) driver).executeScript("window.open('https://www.bing.com', '_blank');");

        // Create buttons for switching left and right between tabs
        leftButton = new Button("<< Previous Tab");
        rightButton = new Button("Next Tab >>");
        leftButton.setDisable(true);
        rightButton.setDisable(true);

        updateWindowHandlesList();

        // Button action to switch to the previous tab
        leftButton.setOnAction(e -> switchToLeftTab());

        // Button action to switch to the next tab
        rightButton.setOnAction(e -> switchToRightTab());

        // Enable/Disable buttons based on the number of tabs
        updateButtonState();

        // Create a layout for the buttons
        HBox hbox = new HBox(10, leftButton, rightButton);
        Scene scene = new Scene(hbox, 300, 100);

        // Set up the JavaFX stage
        primaryStage.setTitle("Tab Switcher");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    // Update the list of window handles (tabs)
    private void updateWindowHandlesList() {
        Set<String> windowHandles = driver.getWindowHandles();
        windowHandlesList = new ArrayList<>(windowHandles);
        updateButtonState(); // Update button states after updating the window handles
    }

    // Enable or disable the tab switching buttons based on the number of tabs
    private void updateButtonState() {
        if (windowHandlesList.size() > 1) {
            if (leftButton != null && rightButton != null) {
                leftButton.setDisable(false); // Enable the buttons if more than one tab is open
                rightButton.setDisable(false);
            }
        } else {
            if (leftButton != null && rightButton != null) {
                leftButton.setDisable(true); // Disable the buttons if there's only one or no tab
                rightButton.setDisable(true);
            }
        }
    }

    // Switch to the previous tab (left)
    private void switchToLeftTab() {
        if (windowHandlesList.size() > 1) {
            currentTabIndex = (currentTabIndex - 1 + windowHandlesList.size()) % windowHandlesList.size();
            driver.switchTo().window(windowHandlesList.get(currentTabIndex));
        }
    }

    // Switch to the next tab (right)
    private void switchToRightTab() {
        if (windowHandlesList.size() > 1) {
            currentTabIndex = (currentTabIndex + 1) % windowHandlesList.size();
            driver.switchTo().window(windowHandlesList.get(currentTabIndex));
        }
    }

    @Override
    public void stop() throws Exception {
        // Close the browser when the JavaFX application is stopped
        if (driver != null) {
            driver.quit();
        }
        super.stop();
    }

    public static void main(String[] args) {
        // Launch the JavaFX application
        launch(args);
    }
}

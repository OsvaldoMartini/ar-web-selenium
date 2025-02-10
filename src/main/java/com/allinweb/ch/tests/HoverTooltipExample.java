package com.allinweb.ch.tests;

import java.time.Duration;
import java.util.Collections;
import java.util.NoSuchElementException;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.Wait;

public class HoverTooltipExample extends Application {

    private static WebDriver driver;
    private static Tooltip hoverTooltip;
    private static TextField hoverInfoField;

    @Override
    public void start(Stage primaryStage) {
        VBox root = new VBox();
        hoverInfoField = new TextField();
        hoverInfoField.setPromptText("Hover info will be displayed here");

        root.getChildren().add(hoverInfoField);
        Scene scene = new Scene(root, 400, 200);

        primaryStage.setTitle("Hover Info Retriever");
        primaryStage.setScene(scene);
        primaryStage.show();

        // Initialize WebDriver
        setupWebDriver();

        // Inject JavaScript to capture hover events
        injectJavaScript();

        // Initialize Tooltip
        hoverTooltip = new Tooltip();
        Tooltip.install(hoverInfoField, hoverTooltip);

        // Start polling for hover info
        startHoverPolling();
    }

    private void setupWebDriver() {
        String logFolder = "D:\\Projects\\AllinWeb\\ARWeb\\Logs";
        String webDriverPath = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
        // Set the path to the ChromeDriver executable
        ChromeOptions options = new ChromeOptions();
        //        options.addArguments("--headless"); // Run in headless mode
        System.setProperty("webdriver.chrome.verboseLogging", "true");
        System.setProperty("webdriver.chrome.logfile", logFolder + "\\_chrome_browser.log");

        //                        options.setBinary(ARConstants.CURRENT_PATH + "\\chrome\\chrome.exe");
        options.setBinary(webDriverPath);
        //                                                options.setBinary("C:/Program
        // Files/Google/Chrome/Application/chrome.exe");
        //                        options.setBinary("C:/Program Files
        // (x86)/Google/Chrome/Application/chrome.exe");
        //                        options.addArguments("headless");
        //                        options.addArguments("--disable-infobars");
        //                        options.addArguments("--disable-dev-shm-usage");
        //                        options.addArguments("--no-sandbox");
        //                        options.addArguments("--remote-debugging-port=9222");
        options.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));
        driver = new ChromeDriver(options);

        // Load a webpage
        driver.get("https://ME-34272.dev.marginedge.com"); // Replace with your target URL

        Wait<WebDriver> wait = new FluentWait<>(driver)
                .withTimeout(Duration.ofSeconds(10))
                .pollingEvery(Duration.ofMillis(500))
                .ignoring(NoSuchElementException.class);

        WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("username")));
    }

    private void injectJavaScript() {
        String script = "document.addEventListener('mousemove', function(event) {" + "    var element = event.target;"
                + "    var tagName = element.tagName.toLowerCase();"
                + "    var rect = element.getBoundingClientRect();"
                + "    var coordinates = '(' + rect.left + ',' + rect.top + ')';"
                + "    window.hoverInfo = tagName + '-Coordinates:' + coordinates;"
                + "}, true);";

        ((JavascriptExecutor) driver).executeScript(script);
    }

    private void startHoverPolling() {
        new Thread(() -> {
                    while (true) {
                        try {
                            Thread.sleep(500); // Check every 500 milliseconds

                            // Retrieve the hover info stored in window.hoverInfo
                            String hoverInfo =
                                    (String) ((JavascriptExecutor) driver).executeScript("return window.hoverInfo;");
                            if (hoverInfo != null && !hoverInfo.isEmpty()) {
                                // Clear window.hoverInfo after reading
                                ((JavascriptExecutor) driver).executeScript("window.hoverInfo = null;");

                                // Update the Tooltip in the JavaFX application thread
                                Platform.runLater(() -> {
                                    hoverTooltip.setText(hoverInfo);
                                    hoverInfoField.setText(hoverInfo);
                                });
                            }
                        } catch (Exception e) {
                            System.out.println(e.getMessage());
                        }
                    }
                })
                .start();
    }

    public static void main(String[] args) {
        launch(args);

        // JavaFX application thread will start, now we add a hook to stop WebDriver
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (driver != null) {
                driver.quit();
            }
        }));
    }
}

package com.allinweb.ch.tests;

import com.allinweb.ch.util.ABRConstants;
import java.util.Collections;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

public class TooltipInjector extends Application {

    private WebDriver driver;
    private TextField currentXPathTextField;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        // Set up the WebDriver
        ChromeOptions options = new ChromeOptions();
        options.setBinary(ABRConstants.CURRENT_PATH + "\\chrome\\chrome.exe");
        options.setBinary("C:/Program Files/Google/Chrome/Application/chrome.exe");
        options.setBinary("C:/Program Files (x86)/Google/Chrome/Application/chrome.exe");
        options.setExperimentalOption("useAutomationExtension", false);
        options.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));
        driver = new ChromeDriver(options);
        driver.get("https://www.inlinea.ch/auth/login");

        // JavaScript code to inject
        String jsCode = "(function() {" + "    var tooltip = document.createElement('div');"
                + "    tooltip.style.position = 'absolute';"
                + "    tooltip.style.backgroundColor = 'black';"
                + "    tooltip.style.color = 'white';"
                + "    tooltip.style.padding = '5px';"
                + "    tooltip.style.borderRadius = '3px';"
                + "    tooltip.style.display = 'none';"
                + "    tooltip.style.zIndex = '1000';"
                + "    document.body.appendChild(tooltip);"
                + "    function getXPath(element) {"
                + "        if (element.id !== '') {"
                + "            return 'id(\"' + element.id + '\")';"
                + "        }"
                + "        if (element === document.body) {"
                + "            return element.tagName;"
                + "        }"
                + "        var ix = 0;"
                + "        var siblings = element.parentNode.childNodes;"
                + "        for (var i = 0; i < siblings.length; i++) {"
                + "            var sibling = siblings[i];"
                + "            if (sibling === element) {"
                + "                return getXPath(element.parentNode) + '/' + element.tagName + '[' + (ix + 1) + ']';"
                + "            }"
                + "            if (sibling.nodeType === 1 && sibling.tagName === element.tagName) {"
                + "                ix++;"
                + "            }"
                + "        }"
                + "        return '';"
                + "    }"
                + "    function showTooltip(event) {"
                + "        var tagName = event.target.tagName.toLowerCase();"
                + "        tooltip.textContent = tagName;"
                + "        tooltip.style.left = event.pageX + 'px';"
                + "        tooltip.style.top = (event.pageY + 15) + 'px';"
                + "        tooltip.style.display = 'block';"
                + "    }"
                + "    function hideTooltip() {"
                + "        tooltip.style.display = 'none';"
                + "    }"
                + "    function handleClick(event) {"
                + "        var xpath = getXPath(event.target);"
                + "        window.currentXPath = xpath;"
                + "    }"
                + "    document.addEventListener('mouseover', showTooltip);"
                + "    document.addEventListener('mouseout', hideTooltip);"
                + "    document.addEventListener('click', handleClick);"
                + "})();";

        // Inject the JavaScript into the webpage
        JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;
        jsExecutor.executeScript(jsCode);

        // Set up the JavaFX UI
        VBox root = new VBox();
        currentXPathTextField = new TextField();
        currentXPathTextField.setPromptText("Clicked element XPath will appear here");
        root.getChildren().add(currentXPathTextField);

        Scene scene = new Scene(root, 400, 200);
        primaryStage.setTitle("XPath Tooltip Injector");
        primaryStage.setScene(scene);
        primaryStage.show();

        // Start a thread to periodically check the XPath value and update the TextField
        new Thread(() -> {
                    while (true) {
                        String currentXPath = (String) jsExecutor.executeScript("return window.currentXPath;");
                        Platform.runLater(() -> currentXPathTextField.setText(currentXPath));
                        try {
                            Thread.sleep(500); // Check every 500 milliseconds
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                })
                .start();
    }

    @Override
    public void stop() {
        // Close the WebDriver when the application is closed
        if (driver != null) {
            driver.quit();
        }
    }
}

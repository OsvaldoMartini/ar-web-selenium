package com.allinweb.ch.driver;

import com.allinweb.ch.builder.WebElementAttributeEnum;
import com.allinweb.ch.builder.WebElementScriptFactory;
import com.allinweb.ch.component.scene.ABRAlertScene;
import com.allinweb.ch.util.ABRConstants;
import com.allinweb.ch.util.ABRLogger;
import com.allinweb.ch.util.ABRPropertyEnum;
import com.allinweb.ch.util.ABRPropertyManager;
import com.google.common.base.Strings;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javax.swing.*;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;

public class ABRWebDriver {

    private WebDriver driver = null;
    private final WebElementScriptFactory scriptFactory = new WebElementScriptFactory();

    public void openDriver(String url) {
        ABRLogger.getInstance(ABRWebDriver.class).fine("Going to call WebDriver for \n" + url);

        ABRPropertyManager managerProps = ABRPropertyManager.getInstance();
        String webDriverPath = managerProps.getProperty(ABRPropertyEnum.PATH_WEBDRIVER);

        if (Strings.isNullOrEmpty(webDriverPath)) {
            ABRLogger.getInstance(ABRWebDriver.class).fine("URL IS EMPTY");
            //            JOptionPane.showMessageDialog(
            //                    null,
            //                    "An error has occurred PATH_WEBDRIVER is NULL",
            //                    "Error in WebDriver PATH",
            //                    JOptionPane.ERROR_MESSAGE);
        }

        if (driver == null) {
            String browser = ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.BROWSER);
            String logFolder = ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.FOLDER_PATH_LOG);
            try {
                switch (browser) {
                    case ABRConstants.CHROME -> {
                        //                        String driverPath = webDriverPath + "\\chrome.exe";
                        if (!(new File(webDriverPath)).exists()) {
                            ABRLogger.getInstance(ABRWebDriver.class).fine("Web Driver NOT EXIST \n" + webDriverPath);
                            //                            new ABRAlertScene(
                            //                                    Alert.AlertType.WARNING,
                            //                                    "Missing file Web Driver",
                            //                                    "Please verify the WebDriver File  first before
                            // launching the bot job\n"
                            //                                            + driverPath,
                            //                                    new ButtonType[] {ButtonType.OK});
                        }

                        //                        System.setProperty("webdriver.chrome.verboseLogging", "true");
                        //                        System.setProperty("webdriver.chrome.logfile", logFolder +
                        // "\\_chrome_browser.log");

                        ChromeOptions options = new ChromeOptions();

                        //                        options.setBinary(ABRConstants.CURRENT_PATH + "\\chrome\\chrome.exe");
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
                        options.setExperimentalOption(
                                "excludeSwitches", Collections.singletonList("enable-automation"));
                        driver = new ChromeDriver(options);
                    }
                    case ABRConstants.EDGE -> {
                        //                        String driverPath = webDriverPath + "\\msedgedriver.exe";
                        if (!(new File(webDriverPath)).exists()) {
                            ABRLogger.getInstance(ABRWebDriver.class).fine("Web Driver NOT EXIST \n" + webDriverPath);
                            new ABRAlertScene(
                                    Alert.AlertType.WARNING,
                                    "Missing file excel",
                                    "Please generate and compile the data of the file excel first before launching the bot job",
                                    new ButtonType[] {ButtonType.OK});
                        }
                        // Set path to Edge WebDriver executable
                        System.setProperty("webdriver.edge.driver", webDriverPath);

                        // Define EdgeOptions and LoggingPreferences
                        EdgeOptions options = new EdgeOptions();
                        // Create LoggingPreferences object
                        LoggingPreferences logs = new LoggingPreferences();
                        logs.enable(LogType.BROWSER, Level.ALL); // Enable browser logs
                        // Set the path where you want to save the log file
                        String logFilePath = logFolder + "_edge_browser.log"; // Replace with your desired log file path
                        // Specify the logging preferences
                        logs.enable(LogType.BROWSER, Level.ALL);
                        options.setCapability(
                                "ms:edgeOptions",
                                "{verbose: true, loggingPrefs: {" + "\"browser\": \"ALL\", \"driver\": \"ALL\"}}");
                        driver = new EdgeDriver(options);
                    }
                    case ABRConstants.FIREFOX -> {
                        //                        String driverPath = webDriverPath + "\\geckodriver.exe";
                        if (!(new File(webDriverPath)).exists()) {
                            ABRLogger.getInstance(ABRWebDriver.class).fine("Web Driver NOT EXIST \n" + webDriverPath);
                        }
                        FirefoxOptions options = new FirefoxOptions();
                        //                        options.setBinary(ABRConstants.CURRENT_PATH + "\\geckodriver.exe");
                        options.setBinary(webDriverPath);
                        driver = new FirefoxDriver(options);
                    }
                }
            } catch (Exception e) {
                ABRLogger.getInstance(ABRWebDriver.class)
                        .severe("An error has occurred during WebDriver Load " + e.getMessage());
                JOptionPane.showMessageDialog(
                        null,
                        "An error has occurred during WebDriver Load: \nError:" + e.getMessage() + "\nCause: "
                                + e.getCause(),
                        "Error in WebDriver Load",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
        driver.manage().window().maximize();
        if (Strings.isNullOrEmpty(url)) {
            ABRLogger.getInstance(ABRWebDriver.class).fine("URL IS EMPTY");
            //            JOptionPane.showMessageDialog(
            //                    null,
            //                    "An error has occurred during WebDriver Load: \nError:  URL IE NULL",
            //                    "Error in WebDriver Load",
            //                    JOptionPane.ERROR_MESSAGE);
        }

        try {
            driver.get(url);

        } catch (Exception e) {
            ABRLogger.getInstance(ABRWebDriver.class)
                    .fine("An error has occurred during driver.get(url) Load " + e.getMessage());
            JOptionPane.showMessageDialog(
                    null,
                    "An error has occurred during WebDriver Load: \nError:" + e.getMessage() + " Cause: "
                            + e.getCause(),
                    "Error in WebDriver Load",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    public void highlightElement(WebElement element) {
        applyCssToElement(element, "background-color:red");
    }

    public void dehighlightElement(WebElement element) {
        applyCssToElement(element, "background-color:");
    }

    public void applyCssToElement(WebElement element, String cssToApply) {
        String script = scriptFactory.forElement(element).createSetStyleScript(cssToApply);
        runScript(element, script);
    }

    public List<String> extractAttributes(WebElement element, WebElementAttributeEnum... attributes) {
        return extractAttributes(element).stream()
                .filter(s -> Arrays.stream(attributes)
                        .anyMatch(attr -> attr.getValue().equals(s)))
                .collect(Collectors.toList());
    }

    public List<String> extractAttributes(WebElement element) {
        String script = scriptFactory.forElement(element).extractAttributesScript();
        return runScript(element, script);
    }

    private <T> T runScript(WebElement element, String script) {
        if (elementExists(element)) {
            return runScript(script);
        }
        return null;
    }

    private boolean elementExists(WebElement element) {
        String reference = scriptFactory.forElement(element).elementReferenceScript();
        return runScript(reference) != null;
    }

    private <T> T runScript(String script) {
        if (driver == null) {
            throw new ABRWebDriverNotStartedException();
        }
        JavascriptExecutor executor = (JavascriptExecutor) driver;
        return (T) executor.executeScript(script);
    }

    public void closeDriver() {
        driver.quit();
        driver = null;
    }

    public WebDriver getDriver() {
        return driver;
    }
}

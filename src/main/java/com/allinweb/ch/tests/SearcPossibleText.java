package com.allinweb.ch.tests;

import com.allinweb.ch.util.ABRConstants;
import java.util.Collections;
import java.util.List;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

public class SearcPossibleText {

    public static void main(String[] args) {
        // Set up WebDriver
        //        System.setProperty("webdriver.chrome.driver", "path_to_chromedriver");
        //        WebDriver driver = new ChromeDriver(options);
        //        driver.get("https://www.example.com");
        ChromeOptions options = new ChromeOptions();
        options.setBinary(ABRConstants.CURRENT_PATH + "\\chrome\\chrome.exe");
        options.setBinary("C:/Program Files/Google/Chrome/Application/chrome.exe");
        options.setBinary("C:/Program Files (x86)/Google/Chrome/Application/chrome.exe");

        options.setExperimentalOption("useAutomationExtension", false);
        options.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));
        // options.addArguments("--headless"); // Optional: run Chrome in headless mode
        options.addArguments("start-maximized");
        WebDriver driver = new ChromeDriver(options);

        try {
            // Open the desired webpage
            driver.get("https://www.ca-nextbank.ch/en/contact"); // Replace with the desired URL

            // Find all label elements
            List<WebElement> labels = driver.findElements(By.tagName("label"));
            printLabelAndAssociatedText(labels, driver);

            // Find other text-containing elements (div, span, p)
            printElementsText(driver, "div");
            printElementsText(driver, "span");
            printElementsText(driver, "p");

        } finally {
            // Close the browser
            driver.quit();
        }
    }

    private static void printLabelAndAssociatedText(List<WebElement> labels, WebDriver driver) {
        for (WebElement label : labels) {
            String labelText = label.getText();
            String associatedText = "";

            // Get the value of the 'for' attribute
            String forAttribute = label.getAttribute("for");
            if (forAttribute != null) {
                // Find the associated element using the 'for' attribute value
                WebElement associatedElement = driver.findElement(By.id(forAttribute));
                associatedText = getElementText(associatedElement);
            }

            System.out.println("Label: " + labelText);
            System.out.println("Associated Text: " + associatedText);
            System.out.println("---------");
        }
    }

    private static void printElementsText(WebDriver driver, String tagName) {
        List<WebElement> elements = driver.findElements(By.tagName(tagName));
        for (WebElement element : elements) {
            String elementText = element.getText();
            if (!elementText.trim().isEmpty()) {
                System.out.println(tagName.toUpperCase() + " Text: " + elementText);
                System.out.println("---------");
            }
        }
    }

    // Helper method to get the text of an associated element
    private static String getElementText(WebElement element) {
        String tagName = element.getTagName();

        switch (tagName.toLowerCase()) {
            case "input":
                return element.getAttribute("value");
            case "textarea":
                return element.getText();
            case "select":
                List<WebElement> selectedOptions = element.findElements(By.cssSelector("option[selected]"));
                return selectedOptions.stream()
                        .map(WebElement::getText)
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("");
            default:
                return element.getText();
        }
    }
}

package com.allinweb.ch.tests;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;

import java.util.List;

public class DynamicWebElementEdge {

    public static void main(String[] args) {
        // Set the path to the Edge WebDriver (replace with your actual path)
        System.setProperty("webdriver.edge.driver",
                "D:/Projects/AllinWeb/ar-web-selenium-archive/ar-web-selenium-files/ProgramFiles/edgedriver-versions/msedgedriver_64-(134.0.3124.77).exe");

        // Configure Edge Options
        EdgeOptions options = new EdgeOptions();

        WebDriver driver = null; // Declare driver outside the try block

        try {
            // Initialize Edge WebDriver
            driver = new EdgeDriver(options);

            // Open a webpage
            driver.get("http://wservices.co.uk");

            // Locate the existing element by its class name
            WebElement existingElement = driver.findElement(By.className("input-field"));

            // JavaScript to replace the existing element
            String newElementJs = "var oldElement = arguments[0];" +
                    "var newElement = document.createElement('div');" +
                    "newElement.id = 'new-element';" +
                    "newElement.innerHTML = 'This is the new element';" +
                    "oldElement.parentNode.replaceChild(newElement, oldElement);";

            // Execute JavaScript to replace the element
            ((JavascriptExecutor) driver).executeScript(newElementJs, existingElement);

            // Verify the replacement
            WebElement newElement = driver.findElement(By.id("new-element"));
            System.out.println(newElement.getText());

            // Locate all anchor elements (links)
            List<WebElement> links = driver.findElements(By.tagName("a"));

            // Print the href attribute of each link
            for (WebElement link : links) {
                String href = link.getAttribute("href");
                System.out.println(href);
            }

            // Pause to see the result (optional)
            Thread.sleep(5000); // Move sleep into the try block
        } catch (Exception error) {
            error.printStackTrace();
        } finally {
            // Clean up
            if (driver != null) { // Check if driver was initialized
                driver.quit();
            }
        }
    }
}
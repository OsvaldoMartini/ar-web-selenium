package com.allinweb.ch.tests;

import java.util.Collections;
import java.util.List;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

public class DynamicWebElement {

    public static void main(String[] args) {
        // Set the path to the ChromeDriver executable
        // Set up Chrome WebDriver
        //        System.setProperty("webdriver.chrome.driver", "path_to_chromedriver");
        ChromeOptions options = new ChromeOptions();
        options.setBinary("C:/Program Files/Google/Chrome/Application/chrome.exe");
        options.setExperimentalOption("useAutomationExtension", false);
        options.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));
        // options.addArguments("--headless"); // Optional: run Chrome in headless mode
        options.addArguments("start-maximized");
        // Initialize WebDriver
        WebDriver driver = new ChromeDriver(options);

        try {
            // Open a webpage
            driver.get("http://wservices.co.uk");

            // Locate the existing element
            // Locate the element by its class name
            WebElement existingElement = driver.findElement(By.className("input-field"));

            // Define the JavaScript to create and replace the element
            String newElementJs = "var oldElement = arguments[0];" + "var newElement = document.createElement('div');"
                    + "newElement.id = 'new-element';"
                    + "newElement.innerHTML = 'This is the new element';"
                    + "oldElement.parentNode.replaceChild(newElement, oldElement);";

            // Execute the JavaScript to replace the element
            ((JavascriptExecutor) driver).executeScript(newElementJs, existingElement);

            // Verify the replacement
            WebElement newElement = driver.findElement(By.id("new-element"));
            System.out.println(newElement.getText()); // Output: This is the new element

            // Locate all anchor elements (links)
            List<WebElement> links = driver.findElements(By.tagName("a"));

            // Iterate through the list and print the href attribute of each link
            for (WebElement link : links) {
                String href = link.getAttribute("href");
                System.out.println(href);
            }

        } finally {
            // Pause to see the result (optional)
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // Clean up
            driver.quit();
        }
    }
}

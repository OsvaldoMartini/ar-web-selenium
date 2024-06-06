package com.allinweb.ch.tests;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

public class JavaScriptXPathExample2 {

    // Method to find XPath using JavaScript
    public static CompletableFuture<String> findXPathAsync(WebElement element, WebDriver driver) {
        CompletableFuture<String> future = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> {
            String xpath = findXPath(element, driver);
            future.complete(xpath);
        });
        return future;
    }

    // Method to execute JavaScript to find XPath
    private static String findXPath(WebElement element, WebDriver driver) {
        JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;
        String script = "function getXPath(element) {" + "  if (element === document.body)"
                + "    return '/html';"
                + "  var ix = 0;"
                + "  var siblings = element.parentNode.childNodes;"
                + "  for (var i = 0; i < siblings.length; i++) {"
                + "    var sibling = siblings[i];"
                + "    if (sibling === element)"
                + "      return getXPath(element.parentNode) + '/' + element.tagName.toLowerCase() + '[' + (ix + 1) + ']';"
                + "    if (sibling.nodeType === 1 && sibling.tagName === element.tagName)"
                + "      ix++;"
                + "  }"
                + "}"
                + "return getXPath(arguments[0]);";
        return (String) jsExecutor.executeScript(script, element);
    }

    public static void main(String[] args) {
        // Set up WebDriver
        //        System.setProperty("webdriver.chrome.driver", "path_to_chromedriver");
        //        WebDriver driver = new ChromeDriver(options);
        //        driver.get("https://www.example.com");
        ChromeOptions options = new ChromeOptions();
        options.setBinary("C:/Program Files/Google/Chrome/Application/chrome.exe");
        options.setExperimentalOption("useAutomationExtension", false);
        options.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));
        // options.addArguments("--headless"); // Optional: run Chrome in headless mode
        options.addArguments("start-maximized");
        WebDriver driver = new ChromeDriver(options);

        // Navigate to the webpage
        driver.get("https://www.fnz.com/contact");

        // Find WebElement (example)
        WebElement element = driver.findElement(By.tagName("h1"));

        // Find XPath asynchronously
        CompletableFuture<String> future = findXPathAsync(element, driver);
        future.thenAccept(xpath -> System.out.println("XPath: " + xpath));

        // Wait for the computation to complete
        future.join(); // This waits for the CompletableFuture to complete

        // Close WebDriver
        driver.quit();
    }
}

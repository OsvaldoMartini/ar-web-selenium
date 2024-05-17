package com.allinweb.ch.tests;

import java.util.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebElement;

public class DynamicMatchUniqueReference {

    public static void main(String[] args) {
        // Set the path to the ChromeDriver executable
        // Set up Chrome WebDriver
        //        System.setProperty("webdriver.chrome.driver", "path_to_chromedriver");
        ChromeOptions options = new ChromeOptions();
        options.setBinary("C:/Program Files/Google/Chrome/Application/chrome.exe");
        options.setExperimentalOption("useAutomationExtension", false);
        options.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));
        //        options.addArguments("--headless"); // Optional: run Chrome in headless mode
        options.addArguments("start-maximized");

        // Initialize WebDriver
        WebDriver driver = new ChromeDriver(options);

        try {
            // Open a webpage
            driver.get("https://www.ca-nextbank.ch/en/contact");

            // Search for all remote elements on the page
            List<RemoteWebElement> remoteElements = findAllRemoteElements(driver);

            // Print information about each remote element
            for (RemoteWebElement element : remoteElements) {
                System.out.println("Remote Element: " + element.toString());
                if (element.getText().equalsIgnoreCase("e-banking")) {
                    element.click();
                    break;
                }
            }

            // Find all anchor elements on the page
            List<WebElement> webElements = driver.findElements(By.tagName("a"));

            Elements anchorElements = Jsoup.parse(driver.getPageSource()).select("a");

            // Create a map to store WebElement objects by their unique reference
            Map<String, WebElement> webElementMap = new HashMap<>();

            // Populate the map with WebElement objects
            for (WebElement element : webElements) {
                //                String uniqueReference = element.toString(); // Get the unique reference of the
                // WebElement
                String uniqueReference = ((RemoteWebElement) element).getId();
                webElementMap.put(uniqueReference, element);
            }

            // Iterate through each anchor element parsed by Jsoup
            for (Element anchor : anchorElements) {
                // Get the attributes of the anchor element
                String id = anchor.attr("id");
                String href = anchor.attr("href");

                // Check if there's a corresponding WebElement with the same id
                WebElement originalElement = webElementMap.get(id);
                if (originalElement != null) {
                    // Do something with the original WebElement
                    // For example, you can interact with it using WebDriver methods
                    originalElement.click();
                }
            }

        } finally {
            // Pause to see the result (optional)
            try {
                Thread.sleep(15000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // Clean up
            driver.quit();
        }
    }

    // Method to generate the XPath of an element
    private static String generateXPath(WebElement element, String current) {
        String tag = element.getTagName();
        if (tag.equals("html")) {
            return "/html" + current;
        }
        WebElement parentElement = element.findElement(By.xpath(".."));
        int count = 0;
        int index = 1;
        List<WebElement> children = parentElement.findElements(By.xpath("*"));
        for (WebElement child : children) {
            String childTag = child.getTagName();
            if (childTag.equals(tag)) {
                if (child.equals(element)) {
                    index = count + 1;
                }
                count++;
            }
        }
        return generateXPath(parentElement, "/" + tag + "[" + index + "]" + current);
    }

    // Method to find all remote elements on a page
    private static List<RemoteWebElement> findAllRemoteElements(WebDriver driver) {
        List<RemoteWebElement> remoteElements = new ArrayList<>();

        // Find all elements on the page
        List<WebElement> allElements = driver.findElements(org.openqa.selenium.By.xpath("//*"));

        // Filter out RemoteWebElement instances
        for (WebElement element : allElements) {
            if (element instanceof RemoteWebElement) {
                remoteElements.add((RemoteWebElement) element);
            }
        }

        return remoteElements;
    }
}

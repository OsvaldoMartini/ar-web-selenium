package com.allinweb.ch.tests;

import java.util.Collections;
import java.util.List;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

public class DynamicXPathWebElement {

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

            // Get the XPath of the existing element
            String existingElementXPath = generateXPath(existingElement, "");

            // Define the JavaScript to create and replace the element with the complete XPath
            String newElementJs = "var oldElement = arguments[0];" + "var newElement = document.createElement('div');"
                    + "newElement.id = 'new-element';"
                    + "newElement.innerHTML = 'This is the new element';"
                    + "newElement.setAttribute('data-xpath', '"
                    + existingElementXPath + "');" + "oldElement.parentNode.replaceChild(newElement, oldElement);";

            // Execute the JavaScript to replace the element
            ((JavascriptExecutor) driver).executeScript(newElementJs, existingElement);

            // Verify the replacement
            WebElement newElement = driver.findElement(By.id("new-element"));
            System.out.println("New element text: " + newElement.getText()); // Output: This is the new element
            System.out.println("New element XPath: "
                    + newElement.getAttribute("data-xpath")); // Output: Complete XPath of the original element

            // Locate all anchor elements (links)
            List<WebElement> links = driver.findElements(By.tagName("a"));

            // Iterate through the list and print the href attribute of each link
            for (WebElement link : links) {
                String href = link.getAttribute("href");
                System.out.println(href);
            }

            driver.get("https://www.ca-nextbank.ch/en/contact");
            //            BUILD XPATH   FOR ALL HREF
            // Locate all anchor elements (links) with href attributes
            List<WebElement> linksHref = driver.findElements(By.xpath("//a[@href]"));

            // Iterate through the list and update each link with its XPath
            for (WebElement link : linksHref) {
                // Get the XPath of the current element
                String linkXPath = generateXPath(link, "");

                // Define the JavaScript to add the data-xpath attribute
                String script = "arguments[0].setAttribute('data-xpath', arguments[1]);";
                // Execute the JavaScript to add the data-xpath attribute
                ((JavascriptExecutor) driver).executeScript(script, link, linkXPath);

                // Set the data-xpath attribute directly on the element
                //                link.setAttribute("data-xpath", linkXPath);
            }

            // Verify the updates
            for (WebElement link : linksHref) {
                System.out.println("Link href: " + link.getAttribute("href"));
                System.out.println("Link XPath: " + link.getAttribute("data-xpath"));
                if (link.getText().equalsIgnoreCase("e-banking")) {
                    link.click();
                    break;
                }
            }

        } finally {
            // Pause to see the result (optional)
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                System.out.println(e.getMessage());
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
}

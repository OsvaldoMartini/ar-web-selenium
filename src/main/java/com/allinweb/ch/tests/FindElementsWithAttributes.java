package com.allinweb.ch.tests;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.Wait;

public class FindElementsWithAttributes {

    public static void main(String[] args) {

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
        WebDriver driver = new ChromeDriver(options);

        try {
            // Navigate to the main page
            //            driver.get("https://www.inlinea.ch/auth/login");
            driver.get("https://me-34272.dev.marginedge.com");

            //            Thread.sleep(5000); // Sleep for 5 seconds (5000 milliseconds)

            Wait<WebDriver> wait = new FluentWait<>(driver)
                    .withTimeout(Duration.ofSeconds(10))
                    .pollingEvery(Duration.ofMillis(500))
                    .ignoring(NoSuchElementException.class);

            WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("username")));

            // Find all elements with an "id" attribute and their XPaths
            Map<String, WebElement> elementsWithIdXPath = findElementsWithXPath(driver, "id");
            // Print out the elements, their IDs, and XPaths
            printElementsWithAttributeAndXPath(elementsWithIdXPath, "id");

            // Find all elements with a "name" attribute and their XPaths
            Map<String, WebElement> elementsWithNameXPath = findElementsWithXPath(driver, "name");
            // Print out the elements, their names, and XPaths
            printElementsWithAttributeAndXPath(elementsWithNameXPath, "name");

            // Find all input elements without "id" or "name" attributes and their XPaths
            Map<String, WebElement> inputElementsWithoutIdOrNameXPath = findElementsWithoutIdOrName(driver, "input");
            // Print out the input elements without "id" or "name" and their XPaths
            printElementsWithAttributeAndXPath(inputElementsWithoutIdOrNameXPath, "input");

            // Find all button elements without "id" or "name" attributes and their XPaths
            Map<String, WebElement> buttonElementsWithoutIdOrNameXPath = findElementsWithoutIdOrName(driver, "button");
            // Print out the button elements without "id" or "name" and their XPaths
            printElementsWithAttributeAndXPath(buttonElementsWithoutIdOrNameXPath, "button");

        } finally {
            // Close the browser
            driver.quit();
        }
    }

    /**
     * Finds all elements with the specified attribute and returns a map with their XPaths as keys.
     *
     * @param driver the WebDriver instance
     * @param attribute the attribute to find elements by (e.g., "id" or "name")
     * @return a map where keys are XPaths of elements and values are WebElements
     */
    private static Map<String, WebElement> findElementsWithXPath(WebDriver driver, String attribute) {
        JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;
        List<WebElement> elements = (List<WebElement>)
                jsExecutor.executeScript("return Array.from(document.querySelectorAll('[" + attribute + "]'));");
        Map<String, WebElement> elementMap = new HashMap<>();
        for (WebElement element : elements) {
            String xpath = getElementXPath(driver, element);
            elementMap.put(xpath, element);
        }
        return elementMap;
    }

    /**
     * Finds all elements of the specified tag name without "id" or "name" attributes and returns a map with their XPaths as keys.
     *
     * @param driver the WebDriver instance
     * @param tagName the tag name of the elements to find (e.g., "input", "button")
     * @return a map where keys are XPaths of elements and values are WebElements
     */
    private static Map<String, WebElement> findElementsWithoutIdOrName(WebDriver driver, String tagName) {
        JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;
        List<WebElement> elements = (List<WebElement>) jsExecutor.executeScript(
                "return Array.from(document.querySelectorAll('" + tagName + ":not([id]):not([name])'));");
        Map<String, WebElement> elementMap = new HashMap<>();
        for (WebElement element : elements) {
            String xpath = getElementXPath(driver, element);
            elementMap.put(xpath, element);
        }
        return elementMap;
    }

    /**
     * Prints out the elements, their specified attribute, and their XPath.
     *
     * @param elements a map where keys are XPaths of elements and values are WebElements
     * @param attribute the attribute to print
     */
    private static void printElementsWithAttributeAndXPath(Map<String, WebElement> elements, String attribute) {
        for (Map.Entry<String, WebElement> entry : elements.entrySet()) {
            WebElement element = entry.getValue();
            String xpath = entry.getKey();
            String attributeValue = element.getAttribute(attribute);
            System.out.println(
                    "Tag: " + element.getTagName() + ", " + attribute + ": " + attributeValue + ", XPath: " + xpath);
        }
    }

    /**
     * Constructs the XPath of a given WebElement.
     *
     * @param driver the WebDriver instance
     * @param element the WebElement to construct the XPath for
     * @return the XPath of the element
     */
    private static String getElementXPath(WebDriver driver, WebElement element) {
        return (String) ((JavascriptExecutor) driver)
                .executeScript(
                        "function absoluteXPath(element) {" + "    var comp, comps = [];"
                                + "    var parent = null;"
                                + "    var xpath = '';"
                                + "    var getPos = function(element) {"
                                + "        var position = 1, curNode;"
                                + "        if (element.nodeType == Node.ATTRIBUTE_NODE) {"
                                + "            return null;"
                                + "        }"
                                + "        for (curNode = element.previousSibling; curNode; curNode = curNode.previousSibling) {"
                                + "            if (curNode.nodeName == element.nodeName) {"
                                + "                ++position;"
                                + "            }"
                                + "        }"
                                + "        return position;"
                                + "    };"
                                + "    if (element instanceof Document) {"
                                + "        return '/';"
                                + "    }"
                                + "    for (; element && !(element instanceof Document); element = element.nodeType == Node.ATTRIBUTE_NODE ? element.ownerElement : element.parentNode) {"
                                + "        comp = comps[comps.length] = {};"
                                + "        switch (element.nodeType) {"
                                + "            case Node.TEXT_NODE:"
                                + "                comp.name = 'text()';"
                                + "                break;"
                                + "            case Node.ATTRIBUTE_NODE:"
                                + "                comp.name = '@' + element.nodeName;"
                                + "                break;"
                                + "            case Node.PROCESSING_INSTRUCTION_NODE:"
                                + "                comp.name = 'processing-instruction()';"
                                + "                break;"
                                + "            case Node.COMMENT_NODE:"
                                + "                comp.name = 'comment()';"
                                + "                break;"
                                + "            case Node.ELEMENT_NODE:"
                                + "                comp.name = element.nodeName;"
                                + "                break;"
                                + "        }"
                                + "        comp.position = getPos(element);"
                                + "    }"
                                + "    for (var i = comps.length - 1; i >= 0; i--) {"
                                + "        comp = comps[i];"
                                + "        xpath += '/' + comp.name.toLowerCase();"
                                + "        if (comp.position !== null) {"
                                + "            xpath += '[' + comp.position + ']';"
                                + "        }"
                                + "    }"
                                + "    return xpath;"
                                + "}"
                                + "return absoluteXPath(arguments[0]);",
                        element);
    }
}

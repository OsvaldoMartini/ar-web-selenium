package com.allinweb.ch.tests;

import com.allinweb.ch.builder.WebElementAttributeEnum;
import com.allinweb.ch.builder.WebElementTagNameEnum;
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
            //            driver.get("https://www.fnz.com/contact"); // Replace with the desired URL

            // Find all label elements
            List<WebElement> labels = driver.findElements(By.tagName("label"));
            printLabelAndAssociatedText(labels, driver);

            // Find other text-containing elements (div, span, p)
            printElementsText(driver, "div");
            printElementsText(driver, "span");
            printElementsText(driver, "p");
            printElementsText(driver, "button");

        } finally {
            // Close the browser
            driver.quit();
        }
    }

    private static void printLabelAndAssociatedText(List<WebElement> labels, WebDriver driver) {
        int printed = labels.size();
        for (WebElement element : labels) {
            String labelText = element.getText();
            String associatedText = "";
            String ariaLabelValue = element.getAttribute(WebElementAttributeEnum.ARIA_LABEL.getValue());
            String innerHTMLValue = element.getAttribute(WebElementAttributeEnum.INNER_HTML.getValue());
            String formControlNameAttributeValue =
                    element.getAttribute(WebElementAttributeEnum.FORM_CONTROL_NAME.getValue());
            String testIdAttributeValue = element.getAttribute(WebElementAttributeEnum.TEST_ID.getValue());
            String idAttributeValue = element.getAttribute(WebElementAttributeEnum.ID.getValue());
            String nameAttributeValue = element.getAttribute(WebElementAttributeEnum.NAME.getValue());
            String valueAttributeValue = element.getAttribute(WebElementAttributeEnum.VALUE.getValue());

            String tagname = element.getTagName();
            String textLabel = element.getText();

            boolean isAnchor = element.getTagName().equals(WebElementTagNameEnum.ANCHOR.getValue());
            boolean hasAriaLabel = ariaLabelValue != null && !ariaLabelValue.isBlank();
            boolean hasInnerHTML = innerHTMLValue != null && !innerHTMLValue.isBlank();
            boolean hasInnerHTMLTag = hasInnerHTML && (innerHTMLValue.contains("<") || innerHTMLValue.contains(">"));
            boolean hasFormControlName =
                    formControlNameAttributeValue != null && !formControlNameAttributeValue.isBlank();
            boolean hasTestId = testIdAttributeValue != null && !testIdAttributeValue.isBlank();
            boolean hasName = nameAttributeValue != null && !nameAttributeValue.isBlank();
            boolean hasId = idAttributeValue != null && !idAttributeValue.isBlank();
            boolean hasValue = valueAttributeValue != null && !valueAttributeValue.isBlank();

            if (hasFormControlName) {
                System.out.println("formControlNameAttributeValue: " + formControlNameAttributeValue);
            } else if (hasTestId) {
                System.out.println("testIdAttributeValue: " + testIdAttributeValue);
            } else if (hasName) {
                System.out.println("nameAttributeValue: " + nameAttributeValue);
            } else if (hasAriaLabel) {
                System.out.println("ariaLabelValue: " + ariaLabelValue);
            } else if (isAnchor && hasInnerHTML && !hasInnerHTMLTag) {
                System.out.println("innerHTMLValue: " + innerHTMLValue);
            } else if (hasId) {
                System.out.println("idAttributeValue: " + idAttributeValue);
            }

            // Get the value of the 'for' attribute
            String forAttribute = element.getAttribute("for");
            if (forAttribute != null) {
                // Find the associated element using the 'for' attribute value
                WebElement associatedElement = driver.findElement(By.id(forAttribute));
                associatedText = getElementText(associatedElement);
            }

            if (!labelText.isEmpty()
                    || !labelText.isBlank()
                    || !associatedText.isEmpty()
                    || !associatedText.isBlank()) {
                printed--;
                System.out.println("Label: " + labelText);
                System.out.println("Associated Text: " + associatedText);
                System.out.println("---------");
            }
        }
        System.out.println(String.format(
                "Total %s with Text of %s elements from %s", labels.size() - printed, labels.size(), "label"));
    }

    private static void printElementsText(WebDriver driver, String tagName) {
        List<WebElement> elements = driver.findElements(By.tagName(tagName));
        int printed = elements.size();
        for (WebElement element : elements) {
            String elementText = element.getText();
            if (!elementText.trim().isEmpty()) {
                printed--;
                System.out.println(tagName.toUpperCase() + " Text: " + elementText);
                System.out.println("---------");
            }
        }
        System.out.println(String.format(
                "Total %s with Text of %s elements from %s", elements.size() - printed, elements.size(), tagName));
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

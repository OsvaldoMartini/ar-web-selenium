package com.allinweb.ch.facade.actions;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Browser-side JavaScript helpers extracted from PerformActions (cluster M, driver part).
 * All methods are stateless; the driver is always passed in — never cached.
 */
public final class BrowserJsUtils {

    private static final Logger logOperations = LoggerFactory.getLogger("com.allinweb.operations");

    private BrowserJsUtils() {}

    // Function to check if the element is visible
    public static boolean isElementVisible(WebElement element, WebDriver driver) {
        // Check if the element is displayed and within the viewport
        try {
            return element.isDisplayed() && isInViewport(element, driver);
        } catch (Exception e) {
            logOperations.info(e.getMessage());
            return false;
        }
    }

    // Function to check if the element is within the viewport
    private static boolean isInViewport(WebElement element, WebDriver driver) {
        // Use JavaScript to check if the element is in the viewport
        // Use the WebDriver (which implements JavascriptExecutor) to execute JavaScript
        JavascriptExecutor js = (JavascriptExecutor) driver;

        // Execute the JavaScript to get the element's position and check if it's in the viewport
        return (boolean) js.executeScript(
                "var rect = arguments[0].getBoundingClientRect(); "
                        + "return (rect.top >= 0 && rect.left >= 0 && rect.bottom <= (window.innerHeight || document.documentElement.clientHeight) && rect.right <= (window.innerWidth || document.documentElement.clientWidth));",
                element);
    }

    public static String insertValueIFrameElement(
            WebDriver driver, String iframeXPath, String inputXPath, String inputValue) {
        JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;

        String script = "(function(iframeXPath, inputXPath, inputValue) {" + "    let logs = [];"
                + "    let iframe = document.evaluate(iframeXPath, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue;"
                + "    if (iframe) {"
                + "        let iframeDocument = iframe.contentDocument || iframe.contentWindow.document;"
                + "        let inputElement = document.evaluate(inputXPath, iframeDocument, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue;"
                + "        if (inputElement) {"
                + "            inputElement.value = inputValue;"
                + "            inputElement.dispatchEvent(new Event('input', { bubbles: true }));"
                + "            logs.push('Text entered successfully.');"
                + "        } else {"
                + "            logs.push('Input field not found inside the iframe.');"
                + "        }"
                + "    } else {"
                + "        logs.push('Iframe not found.');"
                + "    }"
                + "    return logs.join('\n');"
                + "})(arguments[0], arguments[1], arguments[2]);";

        return (String) jsExecutor.executeScript(script, iframeXPath, inputXPath, inputValue);
    }

    public static String insertValueIFrameElement(
            WebDriver driver,
            String iframeXPath,
            String inputXPath,
            String inputValue,
            String targetOriginURL,
            String trustedOriginURL) {

        JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;

        String script = "(function(iframeXPath, inputXPath, inputValue, targetOriginURL, trustedOriginURL) {"
                + "    let logs = [];"
                + "    let iframe = document.evaluate(iframeXPath, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue;"
                + "    if (iframe) {"
                + "        let iframeDocument = iframe.contentDocument || iframe.contentWindow.document;"
                + "        let inputElement = document.evaluate(inputXPath, iframeDocument, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue;"
                + "        if (inputElement) {"
                + "            inputElement.value = inputValue;"
                + "            inputElement.dispatchEvent(new Event('input', { bubbles: true }));"
                + "            logs.push('Text entered successfully.');"
                + "            "
                + "            // Send a message to the targetOriginURL (globally, once input is set)"
                + "            window.postMessage({ type: 'myMessage', data: 'some data' }, targetOriginURL);"
                + "        } else {"
                + "            logs.push('Input field not found inside the iframe.');"
                + "        }"
                + "    } else {"
                + "        logs.push('Iframe not found.');"
                + "    }"
                + "    return logs.join('\\n');"
                + "} )(arguments[0], arguments[1], arguments[2], arguments[3], arguments[4]);"
                + " // Listen for messages from the trusted origin (this needs to be in the global scope)"
                + "window.addEventListener('message', function (event) {"
                + "    if (event.origin !== trustedOriginURL) return;" // Validate message source
                + "    console.log('Received message:', event.data);"
                + "});";

        return (String) jsExecutor.executeScript(
                script, iframeXPath, inputXPath, inputValue, targetOriginURL, trustedOriginURL);
    }

    public static void highlightElement(
            JavascriptExecutor jsExecutor, WebElement previousElement, WebElement currentElement) {
        // Reset background color of the previous element
        try {
            if (previousElement != null) {
                jsExecutor.executeScript("arguments[0].style.backgroundColor = '';", previousElement);
            }

            // Highlight the current element
            if (currentElement != null) {
                jsExecutor.executeScript("arguments[0].style.backgroundColor = 'red';", currentElement);
            }
        } catch (Exception error) {

        }
    }
}

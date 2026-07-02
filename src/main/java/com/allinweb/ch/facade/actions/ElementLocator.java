package com.allinweb.ch.facade.actions;

import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.ReferenceLoadDTO;
import com.allinweb.ch.model.TargetElement;
import com.allinweb.ch.util.Priority;
import com.allinweb.ch.util.PriorityTypeEnum;
import com.google.common.base.Strings;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Element location (cluster A): the priority-driven locator ladder (xpath → id/name → css →
 * attributes → coordinates), shadow-DOM and iframe resolution, and the JS-based find helpers.
 * The driver is always re-read from the context at call time — never cached. Bodies moved
 * verbatim from PerformActions.
 */
public class ElementLocator {

    private static final Logger logOperations = LoggerFactory.getLogger("com.allinweb.operations");

    static final String DEFAULT_LOCATOR_PRIORITIES = "1,xpath,currentXPath" + System.lineSeparator() + "2,xpath,xpath"
            + System.lineSeparator() + "3,xpath"
            + System.lineSeparator() + "4,ById,locator.best.byId"
            + System.lineSeparator() + "5,ByName,locator.best.byName"
            + System.lineSeparator() + "6,ByCssSelector,locator.css.id"
            + System.lineSeparator() + "7,ByCssSelector,locator.css.tagId"
            + System.lineSeparator() + "8,ByCssSelector,locator.css.name"
            + System.lineSeparator() + "9,ByCssSelector,locator.css.generated"
            + System.lineSeparator() + "10,xpath,locator.xpath.id"
            + System.lineSeparator() + "11,xpath,locator.xpath.name"
            + System.lineSeparator() + "12,xpath,locator.xpath.nameType"
            + System.lineSeparator() + "13,attributeID,attributeID"
            + System.lineSeparator() + "14,attributeName,attributeName"
            + System.lineSeparator() + "15,searchAttribute,searchAttribute"
            + System.lineSeparator() + "16,coordinates,coordinates"
            + System.lineSeparator() + "17,attribute,test-id"
            + System.lineSeparator();

    private final ActionContext ctx;

    public ElementLocator(ActionContext ctx) {
        this.ctx = ctx;
    }

    public static WebElement findElementByID(WebDriver driver, String elementID) {
        JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;
        return (WebElement) jsExecutor.executeScript("return document.getElementById(arguments[0]);", elementID);
    }

    public static WebElement findElementsByName(WebDriver driver, String elementName) {
        JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;
        return (WebElement)
                jsExecutor.executeScript("return document.getElementsByName(arguments[0])[0];", elementName);
    }

    public static WebElement findElementByAttributeParams(
            WebDriver driver, String attributeName, String attributeValue) {

        attributeName = attributeName.trim().replaceAll("^\"|\"$", "");
        attributeValue = attributeValue.trim().replaceAll("^\"|\"$", "");

        JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;
        try {
            // Remove extra quotes around the attribute name and value before passing them to JavaScript
            return (WebElement) jsExecutor.executeScript(
                    "return document.querySelector('[\"' + arguments[0] + '\"]' + '=\"' + arguments[1] + '\"]');",
                    attributeName.trim(),
                    attributeValue.trim());
        } catch (Exception ignore) {
        }
        return null;
    }

    public WebElement searchElement(
            InstructionLoad instruction, int botJobId, boolean forceCoordinates, boolean byPassFlagLoop) {
        WebElement instructionElement = null;

        if (!StringUtils.isBlank(instruction.getXpath())) {
            instructionElement = locateElement(instruction, botJobId, forceCoordinates, byPassFlagLoop);
        }
        return instructionElement;
    }

    public static WebElement getElementAtCoordinates(int x, int y, WebDriver driver) {
        String script = "return document.elementFromPoint(arguments[0], arguments[1]);";

        // Execute the script and retrieve the element
        Object element = ((JavascriptExecutor) driver).executeScript(script, x, y);

        // Check if the returned element is not null and cast it to WebElement
        if (element instanceof WebElement) {
            return (WebElement) element;
        } else {
            throw new NoSuchElementException("No element found at the given coordinates: (" + x + ", " + y + ")");
        }
    }

    public WebElement locateTargetElement(boolean byPassNotFound, String targetXPath, Integer actionCustomMaxWaitSec) {

        String tagName = null;
        try {
            tagName = WebTextUtils.removeTrailingSlash(targetXPath);
            tagName = WebTextUtils.extractTagName(targetXPath);
        } catch (Exception e) {

            logOperations.info(String.format(
                    "Error RemoveTrailingSlash for %s -> xPath  %s -> Cause: %s",
                    tagName, targetXPath, e.getMessage()));
        }

        WaitSupport.waitPage(ctx.pageWait(), ctx.driver());

        WebElement elementFound = null;
        List<By> criterias = Arrays.asList(new By[] {By.xpath(targetXPath)});

        // Actually here is Calling the Actions
        if (criterias != null) {

            for (By criteria : criterias) {
                List<WebElement> foundElementList = ctx.driver().findElements(criteria);

                if (foundElementList != null && foundElementList.size() > 0) {
                    if (ctx.justCalledRefreshPage()) {
                        ctx.justCalledRefreshPage(false);
                        try {
                            ctx.pageWait().until(ExpectedConditions.visibilityOfElementLocated(criteria));
                        } catch (Exception e) {

                            logOperations.warn(String.format(
                                    "Could Not Find xPath \"%s\" Criteria \"%s\" -> Cause: %s",
                                    targetXPath, criteria, e.getMessage()));

                            if (!byPassNotFound) {
                                PerformMessage.getInstance().couldNotFindElement(String.valueOf(criteria));
                            }
                        }
                    } else if (actionCustomMaxWaitSec != null) {
                        try {
                            new WebDriverWait(ctx.driver(), Duration.ofSeconds(actionCustomMaxWaitSec))
                                    .until(ExpectedConditions.presenceOfElementLocated(criteria));
                        } catch (Exception e) {
                            logOperations.warn(String.format(
                                    "Could Not Find xPath \"%s\" Criteria \"%s\" -> Cause: %s",
                                    targetXPath, criteria, e.getMessage()));
                            if (!byPassNotFound) {
                                PerformMessage.getInstance().couldNotFindElement(String.valueOf(criteria));
                            }
                        }
                    } else {
                        try {
                            ctx.actionWait().until(ExpectedConditions.visibilityOfElementLocated(criteria));
                        } catch (Exception e) {

                            logOperations.warn(String.format(
                                    "Could Not Find xPath \"%s\" Criteria \"%s\" -> Cause: %s",
                                    targetXPath, criteria, e.getMessage()));

                            if (!byPassNotFound) {
                                PerformMessage.getInstance().couldNotFindElement(String.valueOf(criteria));
                            }
                        }
                    }
                    if (foundElementList.size() > 0) {
                        elementFound = foundElementList.get(0);
                    }
                }
            }

            return elementFound;
        } else {
            return null;
        }
    }

    public WebElement locateElement(
            InstructionLoad currentInstruction, int botJobId, boolean forceCoordinates, boolean byPassFlagLoop) {

        String instructionPath = currentInstruction.getXpath();
        String tagName = null;

        WebDriverWait waitLocator = new WebDriverWait(ctx.driver(), Duration.ofSeconds(0));

        ctx.driver().switchTo().defaultContent();
        if (ctx.driver().getWindowHandles().size() > 1) {
            try {
                ctx.driver().switchTo().window(ctx.windowHandles().get(ctx.tabIndex()));
            } catch (Exception ignore) {
            }
        }

        try {
            tagName = WebTextUtils.extractTagName(WebTextUtils.removeTrailingSlash(instructionPath));
        } catch (Exception e) {
            logOperations.warn(String.format(
                    "Error RemoveTrailingSlash for %s -> xPath %s -> Cause: %s",
                    tagName, instructionPath, e.getMessage()));
        }

        List<ReferenceLoadDTO> instructionReferenceList = currentInstruction.getReferenceLoadDTOList();

        if (instructionReferenceList.isEmpty()) {
            logOperations.warn("#### Not XPath to Be Located! ####");
            return null;
        }

        if (ctx.priorities().getAllPriorityList() == null
                || ctx.priorities().getAllPriorityList().isEmpty()
                || ctx.priorities().getAllPriorityList().size() < 15) {
            ctx.priorities().loadPrioritiesFromString(DEFAULT_LOCATOR_PRIORITIES);
        }

        WebElement elementFound = null;

        if (!Strings.isNullOrEmpty(currentInstruction.getIFrameXPath())) {
            try {
                WebElement iframe = ctx.driver().findElement(By.xpath(currentInstruction.getIFrameXPath()));
                ctx.driver().switchTo().frame(iframe);
            } catch (Exception e) {
                logOperations.warn("iFrame Not Found: " + currentInstruction.getIFrameXPath());
                return null;
            }
        }

        if (!Strings.isNullOrEmpty(currentInstruction.getShadowHost())
                && !Strings.isNullOrEmpty(currentInstruction.getCssSelector())) {
            elementFound = findShadowElementByCssSelector(
                    currentInstruction.getShadowHost(), currentInstruction.getCssSelector());
        }

        int attempts = 0;
        int maxAttempts = forceCoordinates || byPassFlagLoop ? 2 : 4;

        while (elementFound == null && attempts < maxAttempts) {

            for (Priority priority : ctx.priorities().getAllPriorityList()) {
                if (elementFound != null) break;

                PriorityTypeEnum priorityTypeEnum;
                try {
                    priorityTypeEnum = priority.getPriorityType(); // already returns enum
                } catch (Exception e) {
                    continue;
                }

                // ✅ IMPORTANT: try ALL references matching this priority (not only findFirst)
                List<ReferenceLoadDTO> instructionReferences = instructionReferenceList.stream()
                        .filter(ref ->
                                priority.getName().stream().anyMatch(p -> p.equalsIgnoreCase(ref.getReferenceType())))
                        .toList();

                if (instructionReferences.isEmpty()) {
                    continue;
                }

                for (ReferenceLoadDTO ref : instructionReferences) {
                    if (elementFound != null) break;

                    List<By> criterias = null;
                    String value = ref.getValue();

                    switch (priorityTypeEnum) {
                        case xpath -> criterias = List.of(By.xpath(value));

                        case ById -> criterias =
                                List.of(By.id(WebTextUtils.normalizeLocatorValue(ref.getReferenceType(), value)));

                        case ByName -> criterias =
                                List.of(By.name(WebTextUtils.normalizeLocatorValue(ref.getReferenceType(), value)));

                        case ByCssSelector -> criterias = List.of(
                                By.cssSelector(WebTextUtils.normalizeLocatorValue(ref.getReferenceType(), value)));

                        case ByClassName -> criterias = List.of(By.className(value));
                        case ByTagName -> criterias = List.of(By.tagName(value));
                        case ByLinkText -> criterias = List.of(By.linkText(value));
                        case ByPartialLinkText -> criterias = List.of(By.partialLinkText(value));

                        case attribute, attributeID, attributeName, searchAttribute -> criterias =
                                WebTextUtils.convertToCriteriaList(tagName, priority.getName(), value);

                        default -> {}
                    }

                    if (criterias == null) continue;

                    for (By criteria : criterias) {

                        List<WebElement> foundElementList = new ArrayList<>();
                        try {
                            waitLocator.until(ExpectedConditions.presenceOfElementLocated(criteria));
                            foundElementList = ctx.driver().findElements(criteria);

                            if (!foundElementList.isEmpty()) {
                                elementFound = foundElementList.get(0);
                                break;
                            }
                        } catch (TimeoutException ignored) {
                        } catch (Exception ignored) {
                        }
                    }
                }
            }

            attempts++;
            if (elementFound == null) {
                try {
                    if (ctx.isInterceptBotJob()) {
                        break;
                    }
                    //                    Thread.sleep(100);

                    logOperations.warn(String.format(
                            "Re-try %d Locate Web Element TagName \"%s\"", attempts, currentInstruction.getName()));

                } catch (Exception e) {
                }
            }
        }

        return elementFound;
    }

    public List<WebElement> findBySmartLocator(String locator) {
        Set<WebElement> uniqueElements = new HashSet<>();

        // Extract tag
        String tag = locator.split("#")[0]; // e.g., "input"

        // Extract ID (if present)
        String idPart = locator.contains("#") ? locator.split("#")[1].split("\\.")[0] : null;

        // Extract classes (if present)
        String[] classes = new String[0];
        if (locator.contains(".")) {
            String classesPart = locator.substring(locator.indexOf('.') + 1);
            classes = classesPart.split("\\.");
        }

        // Try locating by full CSS
        uniqueElements.addAll(ctx.driver().findElements(By.cssSelector(locator)));

        // Try locating by tag
        if (tag != null && !tag.isEmpty()) {
            uniqueElements.addAll(ctx.driver().findElements(By.tagName(tag)));
        }

        // Try locating by ID
        if (idPart != null && !idPart.isEmpty()) {
            uniqueElements.addAll(ctx.driver().findElements(By.id(idPart)));
        }

        // Try locating by each class
        for (String cls : classes) {
            if (!cls.isEmpty()) {
                uniqueElements.addAll(ctx.driver().findElements(By.className(cls)));
            }
        }

        return new ArrayList<>(uniqueElements);
    }

    public WebElement findElementByXPaths(List<String> xpaths, WebDriver driver) {
        JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;

        for (String xpath : xpaths) {
            try {
                Object result = jsExecutor.executeScript("return document.evaluate(\"" + xpath
                        + "\", document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue;");
                if (result instanceof WebElement) {
                    return (WebElement) result;
                }
            } catch (Exception e) {
                // Log or handle the exception if needed
                logOperations.error("Error locating element with XPath: " + xpath + ". Exception: " + e.getMessage());
            }
        }
        return null;
    }

    public WebElement findShadowElementByCssSelector(String shadowLocator, String cssSelector) {
        try {
            // Find the shadow host
            WebElement shadowHost = ctx.driver().findElement(By.cssSelector(shadowLocator));
            SearchContext shadowRoot = shadowHost.getShadowRoot();
            return shadowRoot.findElement(By.cssSelector(cssSelector));
        } catch (Exception e) {

        }
        return null;
    }

    public WebElement findWebElement(TargetElement targetFind) {

        WebElement elementFound = null;

        ctx.driver().switchTo().defaultContent();
        if (ctx.driver().getWindowHandles().size() > 1) {
            try {
                ctx.driver().switchTo().window(ctx.windowHandles().get(ctx.tabIndex()));
            } catch (Exception ignore) {

            }
        }

        try {

            if (!Strings.isNullOrEmpty(targetFind.getShadowHost())
                    && !Strings.isNullOrEmpty(targetFind.getCssSelector())) {
                elementFound = findShadowElementByCssSelector(targetFind.getShadowHost(), targetFind.getCssSelector());
            } else if (!Strings.isNullOrEmpty(targetFind.getIFrameXPath())) {

                try {
                    WebElement iFrame = ctx.driver().findElement(By.xpath(targetFind.getIFrameXPath()));

                    ctx.driver().switchTo().frame(iFrame);
                    elementFound = ctx.driver().findElement(By.xpath(targetFind.getXPath()));
                } catch (Exception error) {

                    logOperations.warn("iFrame Element not Located\niFrameXPath"
                            + targetFind.getIFrameXPath()
                            + "iFrameChild: "
                            + targetFind.getXPath());
                }
            } else {
                elementFound = ctx.driver().findElement(By.xpath(targetFind.getXPath()));
            }

        } catch (Exception error) {
            logOperations.warn("Scope Changed - Element not Located - : " + targetFind.getXPath());
            return null;
        }

        return elementFound;
    }

    public WebElement findElementByCssSelector(String cssSelector) throws Exception {
        try {
            if (cssSelector == null || cssSelector.isEmpty()) {
                throw new IllegalArgumentException("CSS Selector cannot be null or empty.");
            }

            // Escape single quotes within the CSS selector for JavaScript
            String escapedCssSelector = cssSelector.replace("'", "\\'");

            String script = "return document.querySelectorAll('" + escapedCssSelector + "')[0];";

            WebElement foundElement = (WebElement) ((JavascriptExecutor) ctx.driver()).executeScript(script);

            if (foundElement == null) {

                logOperations.warn(String.format("Element with CSS Selector \"%s\" not found.", cssSelector));
                return null;
            }
            return foundElement;

        } catch (Exception e) {

            logOperations.error(String.format(
                    "Error finding element with CSS Selector \"%s\" -> Cause: %s", cssSelector, e.getMessage()));
            return null;
        }
    }

    public WebElement findElementByCssSelector(String cssSelector, boolean byPassNotFound) throws Exception {
        WebElement element = findElementByCssSelector(cssSelector);
        if (element == null && !byPassNotFound) {
            logOperations.warn("Could not find element with CSS Selector: " + cssSelector);
            PerformMessage.getInstance()
                    .couldNotFindElement("Could not find element with CSS Selector: " + cssSelector);
        }
        return element;
    }
}

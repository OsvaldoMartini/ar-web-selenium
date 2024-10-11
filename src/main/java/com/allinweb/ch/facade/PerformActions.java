package com.allinweb.ch.facade;

import com.allinweb.ch.component.model.BlockLoadDTO;
import com.allinweb.ch.component.model.BlockLoopInstructionLoadDTO;
import com.allinweb.ch.component.model.ComplexInstructionLoadDTO;
import com.allinweb.ch.component.model.InstructionReferenceLoadDTO;
import com.allinweb.ch.component.pane.ABRScannedElementPane;
import com.allinweb.ch.core.ABRSharedResources;
import com.allinweb.ch.driver.ABRWebDriver;
import com.allinweb.ch.readersAndWriters.ExcelWriter;
import com.allinweb.ch.util.ABRLogger;
import com.allinweb.ch.util.ABRPriorities;
import com.allinweb.ch.util.ABRPropertyEnum;
import com.allinweb.ch.util.ABRPropertyManager;
import com.allinweb.ch.util.ABRWebUtil;
import com.allinweb.ch.util.Constants;
import com.allinweb.ch.util.CryptationAlgorithm;
import com.allinweb.ch.util.ExcelReportStatusEnum;
import com.allinweb.ch.util.PriorityTypeEnum;
import com.allinweb.ch.util.UtilsMethods;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Wait;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PerformActions.
 *
 * @author Osvaldo Martini
 * @version 1.0
 */
public class PerformActions {
    private static final Logger logger = LoggerFactory.getLogger(PerformActions.class);

    private ABRPriorities abrPriorities;
    private ABRWebDriver abrWebDriver;
    private Map<String, String> mapOperators;
    private Map<String, String> mapExport;
    public static Wait<WebDriver> waitForPage;
    public static Wait<WebDriver> waitForAction;
    private boolean justCalledRefreshPage = false;

    // Static final variable to hold the singleton instance
    protected static final SingletonSupplier<PerformActions> instance = () -> new PerformActions();

    private static final DateTimeFormatter FORMAT_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    // Private constructor to prevent instantiation
    private PerformActions() {
        // Initialize if necessary
    }

    public void initializePerformActions(ABRPriorities abrPriorities, ABRWebDriver abrWebDriver) {
        this.abrPriorities = abrPriorities;
        this.abrWebDriver = abrWebDriver;
    }

    // Public method to access the singleton instance
    public static PerformActions getInstance() {
        return instance.get();
    }

    public String performActions(
            Map<String, String> data, BlockLoopInstructionLoadDTO instruction, int botJobId, String blockJobName)
            throws Exception {
        WebElement instructionElement = null;
        String[] actions = instruction.getActions().split(Constants.ACTIONS_AND_PATHS_SPLITTER);

        if (!StringUtils.isBlank(instruction.getPath())) {
            instructionElement = locateElement(instruction, botJobId);
        }
        String result = null;
        if (instructionElement != null
                || actions[0].equals(Constants.HOLD)
                || actions[0].equals(Constants.QUIT)
                || actions[0].equals(Constants.SCREEN)) {

            for (String action : actions) {
                switch (String.valueOf(action.charAt(0))) {
                    case Constants.VISUALIZE:
                        scrollToElement(instructionElement);
                        break;
                    case Constants.CLICK:
                        result = "clickElement --> " + instruction.getName() + " --> "
                                + clickElement(instructionElement);
                        break;
                    case Constants.INSERT:
                        result = insertInElement(instructionElement, data, action, instruction);
                        break;
                    case Constants.LIST_OPERATION:
                        listOperation(instruction, data);
                        break;
                    case Constants.HOLD:
                        //                        executeAlert(instruction);
                        result = onHoldForSeconds(instruction);
                        break;
                    case Constants.REFRESH:
                        refreshPage();
                        result = "refreshPage";
                        break;
                    case Constants.QUIT:
                        Alert alert = new Alert(
                                Alert.AlertType.CONFIRMATION,
                                "Do you want to continue?",
                                ButtonType.YES,
                                ButtonType.NO);
                        alert.setTitle("Confirmation");
                        alert.setHeaderText("This Action Closes the Browser and Scanner!");
                        //                        alert.setContentText(content);

                        Optional<ButtonType> quitResult = alert.showAndWait();
                        if (quitResult.isPresent() && quitResult.get() == ButtonType.YES) {
                            ABRSharedResources.getInstance().cacheEntitiesFromDB();
                            result = "Close Browser";
                            quit(1);
                        } else {
                            ABRSharedResources.getInstance().cacheEntitiesFromDB();
                            result = "Close Browser Cancelled";
                        }
                        break;
                        //                    case Constants.EXTRACT:
                        //                        result = "insertValueFieldNameInExcel-->"
                        //                                + insertValueFieldNameInExcel(instructionElement, instruction,
                        // action, blockJobName);
                        //                        break;
                    case Constants.SCREEN:
                        result = instruction.getName() + " --> " + blockJobName;
                        break;
                }
                onHoldForSeconds(null);
            }
        }
        //        } else {
        //            executeActionsAtInstructionCoordinates(instruction, data);
        //            onHoldForSeconds(null);
        //        }
        return result;
    }

    public String performActionOperator(
            BlockLoopInstructionLoadDTO instruction,
            String targetXPath,
            String action,
            String[] operations,
            String parentField,
            Map<String, String> mapOperators)
            throws Exception {

        WebElement instructionElement = null;

        if (!StringUtils.isBlank(targetXPath)) {
            instructionElement = locateTargetElement(targetXPath, instruction.getActionCustomMaxWaitSec());
        }
        if (instructionElement != null) {

            switch (action) {
                case "SET":
                    insertTargetElement(instructionElement, operations[0], operations[1]);
                    return "SET_VALUE to (" + parentField + ") Var:" + operations[0] + " <-- " + operations[1];
                case "GET":
                    String valueElem = getValueInElement(instructionElement);
                    mapOperators.put(parentField, valueElem);
                    return "GET_VALUE from (" + parentField + ") Var" + operations[1] + " <-- " + valueElem;
                    //                    case "CK":
                    //                        if (operator.equalsIgnoreCase("=")) {
                    //                            result = "Equals -> "
                    //                                    + String.valueOf(getValueInElement(instructionElement)
                    //                                            .equalsIgnoreCase(valueOperator));
                    //                        } else if (operator.equalsIgnoreCase(">")) {
                    //                            result = "Greater -> "
                    //                                    + String.valueOf(getValueInElement(instructionElement)
                    //                                            .equalsIgnoreCase(valueOperator));
                    //                        }
                    //                        break;
            }
            onHoldForSeconds(null);
        }

        return null;
    }

    private WebElement locateTargetElement(String targetXPath, Integer actionCustomMaxWaitSec) {

        String tagName = null;
        try {
            tagName = removeTrailingSlash(targetXPath);
            tagName = extractTagName(targetXPath);
        } catch (Exception e) {
            ABRLogger.getInstance(ABRScannedElementPane.class)
                    .fine(String.format(
                            "Error RemoveTrailingSlash for %s   \nxPath  %s\nCause: %s",
                            tagName, targetXPath, e.getMessage()));
        }

        waitPage();

        WebElement elementFound = null;
        List<By> criterias = Arrays.asList(new By[] {By.xpath(targetXPath)});

        // Actually here is Calling the Actions
        if (criterias != null) {

            for (By criteria : criterias) {
                List<WebElement> foundElementList = abrWebDriver.getDriver().findElements(criteria);

                if (foundElementList != null && foundElementList.size() > 0) {
                    if (justCalledRefreshPage) {
                        justCalledRefreshPage = false;
                        try {
                            waitForPage.until(ExpectedConditions.visibilityOfElementLocated(criteria));
                        } catch (Exception e) {
                            ABRLogger.getInstance(ABRScannedElementPane.class)
                                    .fine(String.format(
                                            "Could Not Find Elements %s   \nCriteria  %s\nCause: %s",
                                            targetXPath, criteria, e.getMessage()));
                        }
                    } else if (actionCustomMaxWaitSec != null) {
                        try {

                            new WebDriverWait(abrWebDriver.getDriver(), Duration.ofSeconds(actionCustomMaxWaitSec))
                                    .until(ExpectedConditions.presenceOfElementLocated(criteria));
                        } catch (Exception e) {
                            ABRLogger.getInstance(ABRScannedElementPane.class)
                                    .fine(String.format(
                                            "Could Not Find Elements %s   \nCriteria  %s\nCause: %s",
                                            targetXPath, criteria, e.getMessage()));
                        }
                    } else {
                        try {

                            waitForAction.until(ExpectedConditions.visibilityOfElementLocated(criteria));
                        } catch (Exception e) {
                            ABRLogger.getInstance(ABRScannedElementPane.class)
                                    .fine(String.format(
                                            "Could Not Find Elements %s   \nCriteria  %s\nCause: %s",
                                            targetXPath, criteria, e.getMessage()));
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

    private WebElement locateElement(BlockLoopInstructionLoadDTO instruction, int botJobId) {

        String instructionPath = instruction.getPath();
        String tagName = null;
        try {
            tagName = removeTrailingSlash(instructionPath);
            tagName = extractTagName(instructionPath);
        } catch (Exception e) {
            ABRLogger.getInstance(ABRScannedElementPane.class)
                    .fine(String.format(
                            "Error RemoveTrailingSlash for %s   \nxPath  %s\nCause: %s",
                            tagName, instructionPath, e.getMessage()));
        }
        List<InstructionReferenceLoadDTO> instructionReferenceList = instruction.getInstructionReferenceLoadDTOList();

        if (instructionReferenceList.size() == 0) {
            ABRLogger.getInstance(ABRScannedElementPane.class)
                    .severe("####    Access Database Error   ####"
                            + "\n####    It means there is not XPath to Be Located!   ####"
                            + "\n####    Remove and Re-Scan the Failed Field Again   ####");

            return null;
        }

        waitPage();

        // If Not Loaded get if the JobId Changed
        if (abrPriorities.getJobId() == null) {
            abrPriorities.setJobId(botJobId);
            if (instruction.getPriority() != null) {
                abrPriorities.loadPrioritiesFromString(instruction.getPriority());
            } else {
                abrPriorities.loadPriorities();
            }
        } else if (abrPriorities.getJobId() != botJobId) {
            abrPriorities.setJobId(botJobId);
            if (instruction.getPriority() != null) {
                abrPriorities.loadPrioritiesFromString(instruction.getPriority());
            } else {
                abrPriorities.loadPriorities();
            }
        }

        List<com.allinweb.ch.util.Priority> priorityList = abrPriorities.getAllPriorityList();
        if (abrPriorities.getAllPriorityList().size() > 0) {

            //            if (instruction.getActionCustomMaxWaitSec() > 5) {
            //                instruction.setActionCustomMaxWaitSec(5);
            //            }
            WebElement elementFound = null;
            //            for (int i = 0; i < priorityList.size() && elementFound == null; i++) {
            for (com.allinweb.ch.util.Priority priority : abrPriorities.getAllPriorityList()) {
                if (elementFound != null) {
                    break;
                }

                PriorityTypeEnum priorityTypeEnum = null;
                try {
                    priorityTypeEnum = PriorityTypeEnum.getPriorityType(
                            priority.getPriorityType().toString());
                } catch (Exception e) {
                    System.out.println(String.format("The ENUM: was not defined!"));
                    continue;
                }
                if (priorityTypeEnum == null) {
                    System.out.println("Define priorities!");
                    return null;
                }

                //            Optional<InstructionReferenceDTO> reference = instructionReferenceList.stream()
                //                    .filter(ref -> ref.getReferenceType().equals(priority.getName()))
                //                    .findFirst();

                // Find the first matching instruction reference
                Optional<InstructionReferenceLoadDTO> instructionReference = instructionReferenceList.stream()
                        .filter(reference -> priority.getName().stream()
                                .anyMatch(p -> p.equalsIgnoreCase(reference.getReferenceType())))
                        .findFirst();
                // Print or process the first matching instruction reference
                if (instructionReference.isPresent()) {

                    System.out.println(String.format(
                            "Search for %s   Type:  %s   Value: %s",
                            priority.getName(),
                            instructionReference.get().getReferenceType(),
                            instructionReference.get().getValue()));
                    ABRLogger.getInstance(ABRScannedElementPane.class)
                            .fine(String.format(
                                    "Search for %s   Type:  %s   Value: %s",
                                    priority.getName(),
                                    instructionReference.get().getReferenceType(),
                                    instructionReference.get().getValue()));
                }
                if (instructionReference.isPresent()) {
                    List<By> criterias = null;
                    switch (priority.getPriorityType()) {
                        case xpath -> criterias = Arrays.asList(
                                new By[] {By.xpath(instructionReference.get().getValue())});
                        case attribute -> criterias = convertToCriteriaList(
                                tagName,
                                priority.getName(),
                                instructionReference.get().getValue());
                            //                                criteria = By.cssSelector(tagName + "[" +
                            // priority.getName() + "='" + instructionReference.get().getValue() + "']");
                        case coordinates -> {} // System.out.println("coordinates case");
                        case ById -> {} // System.out.println("ById case");
                        case ByClassName -> {} // System.out.println("Default case");
                        case ByName -> {} // System.out.println("Default case");
                        case ByTagName -> {} // System.out.println("Default case");
                        case ByLinkText -> {} // System.out.println("Default case");
                        case ByPartialLinkText -> {} // System.out.println("Default case");
                        case ByCssSelector -> {} // System.out.println("Default case"); //      ".nav-menu li";
                        case ExecuteScript -> {} // System.out.println("Default case"); //      "return
                            // document.getElementById('search-top')");
                        case createXPath -> {} // System.out.println("Default case"); //         Generates XPath
                            // Recursive tom the Elements Found
                        case dynamic -> {} // System.out.println("Default case"); //         Generates Dynamic Action ->
                            // Click, Hover, Etc.
                        case jsoup -> {} // System.out.println("Default case");
                    }

                    // Actualy here is Calling the Actions
                    if (criterias != null) {

                        for (By criteria : criterias) {
                            List<WebElement> foundElementList =
                                    abrWebDriver.getDriver().findElements(criteria);

                            //                            try {
                            //                                elementFound = scroolUntilFindElement(criteria);
                            //                            } catch (Exception e) {
                            //                                e.printStackTrace();
                            //                            }
                            //                            if (elementFound != null) {
                            //                                break;
                            //                            }
                            if (foundElementList != null && foundElementList.size() > 0) {
                                if (justCalledRefreshPage) {
                                    justCalledRefreshPage = false;
                                    try {
                                        waitForPage.until(ExpectedConditions.visibilityOfElementLocated(criteria));
                                    } catch (Exception e) {
                                        ABRLogger.getInstance(ABRScannedElementPane.class)
                                                .fine(String.format(
                                                        "Could Not Find Elements %s   \nCriteria  %s\nCause: %s",
                                                        instructionPath, criteria, e.getMessage()));
                                    }
                                } else if (instruction.getActionCustomMaxWaitSec() != null) {
                                    try {

                                        new WebDriverWait(
                                                        abrWebDriver.getDriver(),
                                                        Duration.ofSeconds(instruction.getActionCustomMaxWaitSec()))
                                                .until(ExpectedConditions.presenceOfElementLocated(criteria));
                                    } catch (Exception e) {
                                        ABRLogger.getInstance(ABRScannedElementPane.class)
                                                .fine(String.format(
                                                        "Could Not Find Elements %s   \nCriteria  %s\nCause: %s",
                                                        instructionPath, criteria, e.getMessage()));
                                    }
                                } else {
                                    try {

                                        waitForAction.until(ExpectedConditions.visibilityOfElementLocated(criteria));
                                    } catch (Exception e) {
                                        ABRLogger.getInstance(ABRScannedElementPane.class)
                                                .fine(String.format(
                                                        "Could Not Find Elements %s   \nCriteria  %s\nCause: %s",
                                                        instructionPath, criteria, e.getMessage()));
                                    }
                                }
                                int k = 0;
                                //                            MAYBE THIS SHOUL BE NOT NECESSARY  USE UNIQUE ID   OR
                                // SESSION  SAVED TO GET THE SAME XPATHORELEMENT
                                if (foundElementList.size() > 1) {
                                    while (elementFound == null && k < foundElementList.size()) {
                                        String xpath = ABRWebUtil.extractXPath(
                                                foundElementList.get(k).toString());

                                        // Second Verification for XPath Found
                                        if (instructionReference.isPresent()
                                                && xpath.equals(instructionReference
                                                        .get()
                                                        .getValue())) {
                                            elementFound = foundElementList.get(k);
                                            break;
                                        }
                                        k++;
                                    }
                                } else {
                                    elementFound = foundElementList.get(0);
                                }
                            }
                        }
                    }
                }
            }
            return elementFound;
        } else {
            return null;
        }
    }

    public static String removeTrailingSlash(String xPath) {
        if (xPath != null && xPath.endsWith("/")) {
            return xPath.substring(0, xPath.length() - 1);
        }
        return xPath;
    }

    public static String extractTagName(String xPath) {
        // Find the position of the last '/'
        int lastSlashIndex = xPath.lastIndexOf("/");

        // Extract the substring after the last '/'
        String lastSegment = xPath.substring(lastSlashIndex + 1);

        // If the last segment contains '[', extract the tag name before it
        int bracketIndex = lastSegment.indexOf("[");
        if (bracketIndex != -1) {
            return lastSegment.substring(0, bracketIndex);
        }

        // Return the last segment as the tag name
        return lastSegment;
    }

    public static List<By> convertToCriteriaList(String tagName, List<String> priorityToSearch, String someXPath) {
        // Split the string by commas and trim any leading/trailing whitespace from each element
        List<By> criteriaList = new ArrayList<>();

        for (String priority : priorityToSearch) {
            priority = priority.trim();
            // Create the By.cssSelector object and add it to the list
            By criteria = By.cssSelector(tagName + "[" + priority + "='" + someXPath + "']");
            criteriaList.add(criteria);
        }

        return criteriaList;
    }

    private String insertTargetElement(WebElement element, String fieldName, String dataFieldValue) throws Exception {
        UtilsMethods.exceptionIfNullWebElement(element);
        waitForAction.until(ExpectedConditions.visibilityOf(element));

        if (dataFieldValue != null) {
            element.clear();
            element.sendKeys(dataFieldValue);
            element.sendKeys(Keys.TAB);
        }

        return fieldName + "->" + dataFieldValue;
    }

    private String getValueInElement(WebElement element) throws Exception {
        UtilsMethods.exceptionIfNullWebElement(element);
        waitForAction.until(ExpectedConditions.visibilityOf(element));

        // Assuming instructionElement is an input field
        return element.getAttribute("value");
    }

    public synchronized String onHoldForSeconds(BlockLoopInstructionLoadDTO instruction) throws Exception {
        if (instruction != null) {
            Integer instructionSeconds = instruction.getOnHoldSeconds();
            if (instructionSeconds != null && instructionSeconds > 0) {
                wait(fromSecondsToMilliseconds(TimeUnit.SECONDS, instructionSeconds));
                return "HOLD" + "->" + instructionSeconds + " seconds";
            } else {
                String stopSeconds =
                        ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.DEFAULT_INSTRUCTION_STOP_SECONDS);
                wait(fromSecondsToMilliseconds(TimeUnit.SECONDS, Integer.parseInt(stopSeconds)));
                return "HOLD" + "->" + stopSeconds + " seconds";
            }
        } else {
            wait(400);
            return "HOLD" + "->" + "400 milliseconds";
        }
    }

    private long fromSecondsToMilliseconds(TimeUnit timeUnit, int units) throws Exception {
        long milliseconds;

        switch (timeUnit) {
            case SECONDS:
                milliseconds = units * 1000L;
                break;

            case MINUTES:
                milliseconds = units * 1000L * 60L;
                break;

            default:
                throw new Exception("time unit: " + timeUnit.name() + " is not available for this operation");
        }
        return milliseconds;
    }

    private void waitPage() {
        waitForPage.until(driver -> ((JavascriptExecutor) abrWebDriver.getDriver())
                .executeScript("return document.readyState")
                .equals("complete"));
    }

    public void scrollToElement(WebElement element) throws Exception {
        UtilsMethods.exceptionIfNullWebElement(element);
        ((JavascriptExecutor) abrWebDriver.getDriver()).executeScript("arguments[0].scrollIntoView(true);", element);
    }

    public String clickElement(WebElement element) throws Exception {
        UtilsMethods.exceptionIfNullWebElement(element);
        if (!element.isEnabled()) {
            // throw new TimeoutException();
        }
        waitForAction.until(ExpectedConditions.visibilityOf(element).andThen(e -> {
            ((JavascriptExecutor) abrWebDriver.getDriver())
                    .executeScript("arguments[0].scrollIntoView(true);", element);
            return waitForAction.until(ExpectedConditions.elementToBeClickable(element));
        }));
        try {
            element.click();
            try {
                return ABRWebUtil.extractXPath(element.toString());
            } catch (Exception e) {
                return "Error: ON ABRWebUtil.extractXPath for " + element.toString();
            }
        } catch (ElementClickInterceptedException e) {
            JavascriptExecutor jse = (JavascriptExecutor) abrWebDriver.getDriver();
            jse.executeScript("arguments[0].click()", element);
            return "Error: " + ABRWebUtil.extractXPath(element.toString());
        }
    }

    public void refreshPage() {
        abrWebDriver.getDriver().navigate().refresh();
        justCalledRefreshPage = true;
    }

    private String insertInElement(
            WebElement element,
            Map<String, String> data,
            String singleInstruction,
            BlockLoopInstructionLoadDTO instructionDTO)
            throws Exception {
        UtilsMethods.exceptionIfNullWebElement(element);
        waitForAction.until(ExpectedConditions.visibilityOf(element));
        String dataFieldName = "";
        String dataFieldValue = "";
        if (data != null) {
            String[] arr = UtilsMethods.splitIfContains(singleInstruction, Constants.ACTION_SPECIFICATIONS_SPLITTER);
            if (arr.length > 1) {
                dataFieldName = arr[1].split(Constants.PATH_FIELD_SUBSTITUTION)[0];

                dataFieldValue = data.get(dataFieldName);
                if (instructionDTO.isEncrypted()) {
                    dataFieldValue = CryptationAlgorithm.decrypt(dataFieldValue);
                }

                if (dataFieldValue != null) {
                    element.clear();
                    element.sendKeys(dataFieldValue);
                    element.sendKeys(Keys.TAB);

                } else {
                    element.sendKeys(UtilsMethods.generateRandomID(10));
                    element.sendKeys(Keys.TAB);
                }
            }
        } else if (instructionDTO.getDefaultValue() != null) {
            dataFieldValue = instructionDTO.getDefaultValue();
            if (instructionDTO.isEncrypted()) {
                dataFieldValue = CryptationAlgorithm.decrypt(dataFieldValue);
            }
            element.sendKeys(dataFieldValue);
        }

        return dataFieldName + "->" + dataFieldValue;
    }

    private void listOperation(BlockLoopInstructionLoadDTO instructionDTO, Map<String, String> data) {

        /*
        TODO: Da rivedere, attualmente non del tutto funzionante
        Complex instruction string interpretation:
        [       0       ||       1      ||       2         ||    3    ||        4       ||  5   ||            6                ]
        [backward_button||forward_button||list_elements_tag||condition||expected_results||action||sub_element_on_execute_action]
        */
        List<ComplexInstructionLoadDTO> complexInstructionDTOS = instructionDTO.getComplexInstructionLoadDTOList();
        String[] complexActionParts =
                complexInstructionDTOS.get(0).getInstruction().split(Constants.COMPLEX_INSTRUCTION_SEPARATOR);
        List<WebElement> webElementList;
        WebElement forwardButton;
        WebElement backwardButton;
        boolean shouldContinue = true;

        boolean existNextPage;
        do {
            waitForPage.until(ExpectedConditions.visibilityOfElementLocated(By.tagName(complexActionParts[2])));
            backwardButton = abrWebDriver.getDriver().findElement(By.xpath(complexActionParts[0]));
            forwardButton = abrWebDriver.getDriver().findElement(By.xpath(complexActionParts[1]));
            webElementList = abrWebDriver.getDriver().findElements(By.tagName(complexActionParts[2]));

            WebElement element;
            WebElement reasonWebElement;
            for (int i = 0; i < 5; i++) {

                if (i != 0) {
                    waitForPage.until(ExpectedConditions.visibilityOfElementLocated(By.tagName(complexActionParts[2])));
                    webElementList = abrWebDriver.getDriver().findElements(By.tagName(complexActionParts[2]));
                }

                element = webElementList.get(i);
                try {
                    Thread.sleep(1000);

                    reasonWebElement = element.findElement(
                            By.xpath(".//div[@class='payments-table-field reason ng-star-inserted']"));
                    if (!UtilsMethods.testFixedCheck(reasonWebElement.getText())) {
                        continue;
                    }

                    clickElement(element.findElement(
                            By.xpath(".//button[@test-id='web-banking-payment-core.payment-ctx-action.button']")));
                    clickElement(
                            abrWebDriver
                                    .getDriver()
                                    .findElement(
                                            By.xpath(
                                                    ".//button[@test-id='web-banking-payment-core.payment-ctx-action.payment-action-VIEW']")));

                    Thread.sleep(1000);
                    clickElement(abrWebDriver
                            .getDriver()
                            .findElement(By.xpath(
                                    ".//button[@test-id='web-banking-common.export-to-file.single-file-button']")));

                    Thread.sleep(1000);
                    clickElement(
                            abrWebDriver
                                    .getDriver()
                                    .findElement(
                                            By.xpath(
                                                    ".//avq-breadcrumb[@test-id='web-banking-portal.pages.payments-overview.breadcrumb']")));
                } catch (Exception e) {
                    System.out.println("Impossible execute operation on this element: " + element.toString());
                }
            }

            try {
                scrollToElement(forwardButton);
                clickElement(forwardButton);
                existNextPage = true;
            } catch (Exception e) {
                existNextPage = false;
            }

        } while (existNextPage && shouldContinue);
    }

    public void quit(int status) {
        abrWebDriver.getDriver().quit();
        if (status == 0) {
            System.exit(status);
        }
    }

    public short operationLog(
            boolean success, String mainMsg, String resultActions, String lastInstructionExecuted, long duration) {

        ABRLogger.getInstance(ABRScannedElementPane.class)
                .severe(String.format(
                        success
                                ? "SUCCESS %s Previous: %s --> Current Cmd: %s - Duration: %s"
                                : "FAILED %s Previous: %s --> Current Cmd: %s - Duration: %s",
                        mainMsg,
                        resultActions,
                        lastInstructionExecuted,
                        LocalTime.ofNanoOfDay(duration).format(FORMAT_TIME)));

        return (short) (success ? ExcelReportStatusEnum.SUCCESS.ordinal() : ExcelReportStatusEnum.ERROR.ordinal());
    }

    public String getValueIsNotDefined(BlockLoopInstructionLoadDTO currentInstruction, String lastInstructionExecuted) {
        showAlertError(
                "GET is Not Defined for \"+" + currentInstruction.getName() + "\"",
                "\"" + currentInstruction.getName() + "\" - GET is Not Defined",
                "There is NOT GET VALUE defined for: "
                        + currentInstruction.getName()
                        + "\n --------------------- "
                        + "\nCheck the GET for "
                        + currentInstruction.getParentId() + "-"
                        + currentInstruction.getOperation());

        return "Failed to Execute Cmd: " + lastInstructionExecuted;
    }

    public String checkValidationFailed(String parentField, String lastInstructionExecuted, String[] operations) {
        showAlertError(
                "Validation Error",
                "Check Validation Error",
                "The Value: " + operations[2]
                        + "\nis not " + operations[1] + " "
                        + mapOperators.get(parentField)
                        + " Length: ("
                        + mapOperators.get(parentField).length()
                        + ")" + "\n --------------------- "
                        + "\nCheck the GET of "
                        + operations[0] + " for " + parentField
                        + "\nCurrent value: "
                        + operations[2] + " Length: (" + operations[2].length()
                        + ")" + "\nExpected value: "
                        + mapOperators.get(parentField)
                        + " Length: ("
                        + mapOperators.get(parentField).length()
                        + ")");

        return "Failed to Execute Cmd: " + lastInstructionExecuted;
    }

    private void showAlertError(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    public String parentIdWrongBlock(BlockLoopInstructionLoadDTO currentInstruction, BlockLoadDTO blockLoad) {
        showAlertError(
                "Parent Id Error",
                "Check Parent Id",
                "The Parent Id: " + currentInstruction.getParentId()
                        + "\nFor the : "
                        + currentInstruction.getOperation()
                        + "\nDoes not belong to this block: "
                        + blockLoad.getId() + "-" + blockLoad.getName()
                        + "\nCheck the Field Names and Fields Ids");

        ABRLogger.getInstance(ABRScannedElementPane.class)
                .severe(String.format(
                        "Parent Id Error\nCheck Parent Id: %d"
                                + "\nFor the %s \nDoes not belong to this block: "
                                + blockLoad.getId() + "-" + blockLoad.getName(),
                        currentInstruction.getParentId(),
                        currentInstruction.getOperation()));

        return String.format(
                "This ParentId: %d does not belong to this block: %d - %s. Check the Field Names and Fields Ids",
                currentInstruction.getParentId(), blockLoad.getId(), blockLoad.getName());
    }

    public void excelReportWrite(
            boolean success,
            BlockLoopInstructionLoadDTO currentInstruction,
            long duration,
            Map<String, String> dataExcel,
            ExcelWriter.ExcelChain writerReport) {
        writerReport.insertInstructionResult(
                currentInstruction, dataExcel, LocalTime.ofNanoOfDay(duration), success ? "success" : "failed");
    }

    public long duration(long startTime) {
        long currentInstructionEndTime = System.nanoTime();
        return currentInstructionEndTime - startTime;
    }

    public String blockGotoFailed(String resultActions) {
        showAlertError("Block GO TO Error", "Check Correct Block Existence", "CMD: \n" + resultActions);

        ABRLogger.getInstance(ABRScannedElementPane.class)
                .severe("Block GO TO Error.\n" + "Check Correct Block Existence!\n" + "CMD: \n" + resultActions);

        return resultActions;
    }

    public void alertExecutionTimes(int executionTimes, String lastInstructionExecuted) {
        showAlertError(
                "Execution Time LIMIT",
                "Attention The Process Reached the LIMIT of Loop Executions",
                String.format(
                        "Attention the Process Reached the LOOP LIMIT of %d\nLast Instruction Executed : %s\nWe are Exiting All of processes Now!",
                        executionTimes, lastInstructionExecuted));
    }
}

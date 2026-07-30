package com.allinweb.ch.facade;

import com.allinweb.ch.builder.WebElementTagNameEnum;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.facade.actions.ActionContext;
import com.allinweb.ch.facade.actions.CoordinateActions;
import com.allinweb.ch.facade.actions.DataExtractor;
import com.allinweb.ch.facade.actions.ElementDtoMapper;
import com.allinweb.ch.facade.actions.ElementInteraction;
import com.allinweb.ch.facade.actions.ElementLocator;
import com.allinweb.ch.facade.actions.EngineDialogs;
import com.allinweb.ch.facade.actions.ExecutionReporter;
import com.allinweb.ch.facade.actions.InstructionGraph;
import com.allinweb.ch.facade.actions.PlaywrightBridge;
import com.allinweb.ch.facade.actions.RuntimeVariableStore;
import com.allinweb.ch.facade.actions.RuntimeVariableValue;
import com.allinweb.ch.facade.actions.ValidationMessageBuilder;
import com.allinweb.ch.facade.actions.WaitSupport;
import com.allinweb.ch.facade.actions.WebTextUtils;
import com.allinweb.ch.facade.actions.WindowAndFrameManager;
import com.allinweb.ch.facade.scanner.testrun.ScannerTestRunBrowserClosePolicy;
import com.allinweb.ch.model.*;
import com.allinweb.ch.readersAndWriters.ExcelWriter;
import com.allinweb.ch.util.*;
import com.google.common.base.Strings;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Wait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PerformActions — facade over the action layer.
 *
 * <p>Historically a single 4,800-line god class; the implementation now lives in
 * {@code com.allinweb.ch.facade.actions} and this class keeps the stable public API
 * (all method signatures, the public fields {@code windowHandlesList}/{@code currentTabIndex}
 * and the statics {@code waitForPage}/{@code waitForAction}) as one-line delegators, plus the
 * three orchestrators ({@code performWebActions}, {@code performOtherActions},
 * {@code performOperatorActions}). Mutable state is owned here and exposed to the extracted
 * classes through {@link ActionContext}.
 *
 * <p>Cluster → class map:
 * <ul>
 *   <li>string/format utils → {@link WebTextUtils}; browser JS helpers → {@link BrowserJsUtils}
 *   <li>validation/report rows → {@link ValidationMessageBuilder}
 *   <li>instruction/block graph → {@link InstructionGraph}
 *   <li>TargetElement/ElementDTO mapping → {@link ElementDtoMapper}
 *   <li>waits/timing → {@link WaitSupport} (HOLD sleeps stay here: synchronized on this singleton)
 *   <li>Playwright/actionExecutor routing → {@link PlaywrightBridge}
 *   <li>coordinate fallbacks → {@link CoordinateActions}
 *   <li>windows/iframes → {@link WindowAndFrameManager}
 *   <li>element location ladder → {@link ElementLocator}
 *   <li>click/insert/select + key cascade + command sequencer → {@link ElementInteraction}
 *   <li>field data / OUTPUT text → {@link DataExtractor}
 *   <li>engine dialogs/messages → {@link EngineDialogs}
 *   <li>reporting + condition state machine → {@link ExecutionReporter}
 * </ul>
 *
 * <p><b>ar-web-engine note:</b> the Engine repo carries a near-duplicate PerformActions
 * (~4,700 lines) that was NOT decomposed. When porting fixes between the two repos, use the
 * map above to find the counterpart code here.
 *
 * @author Osvaldo Martini
 * @version 2.0
 */
@Slf4j
public class PerformActions implements ActionContext {
    private static final Logger logOperations = LoggerFactory.getLogger("com.allinweb.operations");

    private static final PerformMessage performMessage;
    private static final ARPropertyManager arPropertyManager;
    public static Wait<WebDriver> waitForPage;
    public static Wait<WebDriver> waitForAction;
    // Static final variable to hold the singleton instance
    protected static volatile PerformActions instance;

    static {
        arPropertyManager = ARPropertyManager.getInstance();
        performMessage = PerformMessage.getInstance();
    }

    public List<String> windowHandlesList = new ArrayList<>();
    public int currentTabIndex = 0; // Track the currently active tab index

    private AtomicBoolean interceptBotJob = new AtomicBoolean(false);
    private ARPriorities arPriorities;

    @Getter
    @Setter
    private WebDriver currentDriver;

    @Getter
    @Setter
    private ARWebDriver currentARWebDriver;

    private volatile ScannerTestRunBrowserClosePolicy testRunBrowserClosePolicy =
            ScannerTestRunBrowserClosePolicy.unrestricted();

    @Getter
    @Setter
    private boolean justCalledRefreshPage = false;

    /** Called after page refresh/navigation to re-inject plugins (e.g. actionExecutor). */
    @Setter
    private Runnable onPageRefresh;

    private final PlaywrightBridge playwrightBridge = new PlaywrightBridge(this);
    private final CoordinateActions coordinateActions = new CoordinateActions(this);
    private final WindowAndFrameManager windowAndFrameManager = new WindowAndFrameManager(this);
    private final ElementLocator elementLocator = new ElementLocator(this);
    private final ElementInteraction elementInteraction = new ElementInteraction(this, coordinateActions);
    private final DataExtractor dataExtractor = new DataExtractor(this);
    private final EngineDialogs engineDialogs = new EngineDialogs(this);
    private final ExecutionReporter executionReporter = new ExecutionReporter();

    public long getTotalExecutionTime() {
        return executionReporter.getTotalExecutionTime();
    }

    // ---- ActionContext implementation: live one-line views over the facade's own state ----

    @Override
    public WebDriver driver() {
        return currentDriver;
    }

    @Override
    public void driver(WebDriver driver) {
        this.currentDriver = driver;
    }

    @Override
    public ARWebDriver arWebDriver() {
        return currentARWebDriver;
    }

    @Override
    public boolean closeBrowserForExecutionAction() {
        ARWebDriver browser = currentARWebDriver;
        return browser != null && testRunBrowserClosePolicy.closeBrowserIfAllowed(true, browser::closeBrowser);
    }

    @Override
    public List<String> windowHandles() {
        return windowHandlesList;
    }

    @Override
    public void windowHandles(List<String> handles) {
        this.windowHandlesList = handles;
    }

    @Override
    public int tabIndex() {
        return currentTabIndex;
    }

    @Override
    public void tabIndex(int tabIndex) {
        this.currentTabIndex = tabIndex;
    }

    @Override
    public Wait<WebDriver> pageWait() {
        return waitForPage;
    }

    @Override
    public Wait<WebDriver> actionWait() {
        return waitForAction;
    }

    @Override
    public ARPriorities priorities() {
        return arPriorities;
    }

    @Override
    public boolean justCalledRefreshPage() {
        return justCalledRefreshPage;
    }

    @Override
    public void justCalledRefreshPage(boolean value) {
        this.justCalledRefreshPage = value;
    }

    @Override
    public void notifyPageRefresh() {
        if (onPageRefresh != null) {
            onPageRefresh.run();
        }
    }

    @Override
    public String holdForSeconds(InstructionLoad instruction) throws Exception {
        return onHoldForSeconds(instruction);
    }

    // Private constructor to prevent instantiation
    private PerformActions() {}

    // Public method to access the singleton instance
    public static PerformActions getInstance() {
        if (instance == null) {
            synchronized (PerformActions.class) {
                if (instance == null) {
                    instance = new PerformActions();
                }
            }
        }
        return instance;
    }

    public static String removeTrailingSlash(String xPath) {
        return WebTextUtils.removeTrailingSlash(xPath);
    }

    public static String extractTagName(String xPath) {
        return WebTextUtils.extractTagName(xPath);
    }

    public static String convertToCssSelector(String tagName, List<String> priorityToSearch, String attributeValue) {
        return WebTextUtils.convertToCssSelector(tagName, priorityToSearch, attributeValue);
    }

    public static List<By> convertToCriteriaList(String tagName, List<String> priorityToSearch, String someXPath) {
        return WebTextUtils.convertToCriteriaList(tagName, priorityToSearch, someXPath);
    }

    public static FieldData insertRandomName(String key) {
        return WebTextUtils.insertRandomName(key);
    }

    public static String generateRandomName() {
        return WebTextUtils.generateRandomName();
    }

    public static String truncateAndNormalize(String someText, int limit) {
        return WebTextUtils.truncateAndNormalize(someText, limit);
    }

    public static String extractFileExtension(String input) {
        return WebTextUtils.extractFileExtension(input);
    }

    public AtomicBoolean interceptBotJobProperty() {
        return interceptBotJob;
    }

    public boolean isInterceptBotJob() {
        return interceptBotJob.get();
    }

    public void setInterceptBotJob(boolean value) {
        interceptBotJob.set(value);
    }

    public void setTestRunBrowserClosePolicy(ScannerTestRunBrowserClosePolicy policy) {
        testRunBrowserClosePolicy = Objects.requireNonNull(policy, "policy");
    }

    public void initialize(ARPriorities arPriorities) {
        this.arPriorities = arPriorities;
    }

    public WebElement searchElement(
            InstructionLoad instruction, int botJobId, boolean forceCoordinates, boolean byPassFlagLoop) {
        return elementLocator.searchElement(instruction, botJobId, forceCoordinates, byPassFlagLoop);
    }

    public boolean performWebActions(
            boolean byPassNotFound,
            String savedCoordinates,
            FieldData data,
            InstructionLoad currentInstruction,
            Map<String, String> mapOperators,
            WebElement instructionElement,
            String[] actions,
            boolean isMobileApp,
            SplitDTO splitDTO)
            throws Exception {

        WebDriver originalDriver = this.currentDriver; // Save the original WebDriver state
        boolean switchedToIframe = false;

        try {
            String xPath = currentInstruction.getXpath().toLowerCase();
            // Selenium iframe pre-switch — skip when there is no Selenium driver (Playwright-only);
            // Playwright handles iframes itself via frameLocator in PlaywrightActionExecutor.
            if (this.currentDriver != null && currentInstruction.getXpath() != null && xPath.contains("iframe")) {
                // Locate and switch to the iframe
                WebElement iframeElement = this.currentDriver.findElement(By.xpath(xPath));
                WebDriver driver = this.currentDriver.switchTo().frame(iframeElement);

                setCurrentDriver(driver);
                switchedToIframe = true;
            }

            // The legacy "I:E:..." token in actions is gone; Enter is now a bit in
            // force_coordinates. See InputFlags + migration 2026-04-26.
            InputFlags flags = InputFlags.of(currentInstruction.getForceCoordinates());
            Boolean pressEnterAfter = flags.hasEnter();

            if (!ARConstantsEngine.VISUALIZE.equals(actions[0])
                    && tryPlaywrightWebAction(
                            currentInstruction,
                            data,
                            actions[0],
                            mapOperators)) {
                return true;
            }

            if (isPlaywrightOnlyMode()) {
                logOperations.warn(
                        "Playwright did not complete action '{}'; the retired Selenium fallback will not run.",
                        actions[0]);
                return false;
            }

            if (instructionElement != null) {
                boolean passed = true;

                // S bit (was the legacy "I:S:" token before the 2026-04-26 migration):
                // force-scroll the element into view BEFORE the action runs. Applies
                // uniformly to CLICK / INSERT / OUTPUT / OTHER — the VISUALIZE branch
                // already does its own scroll via scrollToElement. Failure here is
                // non-fatal — the downstream action may still succeed.
                if (flags.hasScroll() && !ARConstantsEngine.VISUALIZE.equals(actions[0])) {
                    try {
                        scrollToElement(true, instructionElement);
                    } catch (Exception scrollEx) {
                        logOperations.debug("hasScroll pre-scroll failed (non-fatal): {}", scrollEx.getMessage());
                    }
                }

                switch (actions[0]) {
                    case ARConstantsEngine.VISUALIZE:
                        passed = scrollToElement(byPassNotFound, instructionElement);

                        if (!passed) {
                            // Try by coordinates
                            FieldData filedData = new FieldData("&EMPTY", "&EMPTY");
                            passed = executeActionsAtCoordinates(
                                    savedCoordinates, filedData, ARConstantsEngine.VISUALIZE, pressEnterAfter);
                        }
                        return passed;
                    case ARConstantsEngine.OUTPUT:
                        String fieldName = currentInstruction.getId() + "-" + currentInstruction.getName();
                        String valueElem = getOutPutElement(
                                byPassNotFound,
                                instructionElement,
                                fieldName,
                                currentInstruction.getActions(),
                                mapOperators);

                        // Empty text is legitimate Web data. Only null means that OUTPUT could not
                        // be read.
                        return valueElem != null;
                    case ARConstantsEngine.CLICK:
                    case ARConstantsEngine.OTHER:
                        if (isMobileApp) {
                            //                            androidDevice.executeAction(instructionElement, splitDTO);
                        } else {
                            try {
                                passed = clickElement(byPassNotFound, instructionElement);
                            } catch (Exception clickEx) {
                                logOperations.warn(
                                        "clickElement threw: {} - trying actionExecutor", clickEx.getMessage());
                                passed = false;
                            }
                        }
                        return passed;
                    case ARConstantsEngine.INSERT:
                        if ("select".equalsIgnoreCase(instructionElement.getTagName())) {
                            if (isMobileApp) {
                                //                                androidDevice.executeAction(instructionElement,
                                // splitDTO, null, data.getValue());
                            } else {
                                try {
                                    passed = insertDataInSelectElement(
                                            byPassNotFound,
                                            instructionElement,
                                            savedCoordinates,
                                            data,
                                            pressEnterAfter);
                                } catch (Exception selectEx) {
                                    logOperations.warn(
                                            "Selenium select threw: {} - trying fallbacks", selectEx.getMessage());
                                    passed = false;
                                }

                                if (!passed) {
                                    // Last resort: try by coordinates
                                    passed = executeActionsAtCoordinates(
                                            savedCoordinates, data, ARConstantsEngine.SELECT, pressEnterAfter);
                                }
                                return passed;
                            }
                        } else {
                            if (isMobileApp) {
                                //                                androidDevice.executeAction(instructionElement,
                                // splitDTO, null, data.getValue());
                            } else {
                                try {
                                    //                            instructionElement.click();
                                    instructionElement.clear();
                                    clearElement(instructionElement);
                                    //                            clearValueAtCoordinates(savedCoordinates);

                                    passed = insertInElement(
                                            byPassNotFound,
                                            instructionElement,
                                            data.getValue(),
                                            currentInstruction.getDefaultValue(),
                                            currentInstruction.getCodified(),
                                            InputFlags.ofLegacy(
                                                    currentInstruction.getForceCoordinates(), pressEnterAfter));
                                } catch (Exception insertEx) {
                                    logOperations.warn(
                                            "Selenium insert threw: {} - trying fallbacks", insertEx.getMessage());
                                    passed = false;
                                }

                                if (!passed) {
                                    // Last resort: try by coordinates
                                    passed = executeActionsAtCoordinates(
                                            savedCoordinates, data, ARConstantsEngine.INSERT, pressEnterAfter);
                                }
                                return passed;
                            }
                        }
                }

                onHoldForSeconds(null);
            }

            return true;
        } finally {
            // Restore the original WebDriver state
            if (switchedToIframe) {
                setCurrentDriver(originalDriver);
            }
        }
    }

    private boolean tryPlaywrightWebAction(
            InstructionLoad instruction,
            FieldData data,
            String action,
            Map<String, String> outputValues) {
        return playwrightBridge.tryPlaywrightWebAction(
                instruction,
                data,
                action,
                outputValues);
    }

    private boolean isPlaywrightOnlyMode() {
        return playwrightBridge.isPlaywrightOnlyMode();
    }

    public void performOtherActions(boolean byPassNotFound, InstructionLoad instruction, String[] actions)
            throws Exception {

        switch (actions[0]) {
            case ARConstantsEngine.LIST_OPERATION:
                //                listOperation(byPassNotFound, instruction);
                break;
            case ARConstantsEngine.HOLD:
            case ARConstantsEngine.REFRESH_HOLD:
                //                        executeAlert(instruction);
                onHoldForSeconds(instruction);
                break;
            case ARConstantsEngine.REFRESH_ONLY:
            case ARConstantsEngine.REFRESH_LOOP:
                refreshPage();
                break;
            case ARConstantsEngine.BACK:
                goBackPage();
                break;
            case ARConstantsEngine.QUIT:
                // Minimal confirmation using your custom modal
                ARExecution.DialogModal respModal = performMessage.showCustomModalDialogDragWin11(
                        "Confirmation",
                        "Do you want to continue?",
                        "This Action Closes the Browser and Scanner!",
                        null,
                        null,
                        true,
                        "OK",
                        "Close Browser",
                        350);

                if (respModal.equals(ARExecution.DialogModal.STOP)) {
                    quit(1);
                }

                break;
                //                    case ARConstants.EXTRACT:
                //                        result = "insertValueFieldNameInExcel-->"
                //                                + insertValueFieldNameInExcel(instructionElement, instruction,
                // action, blockJobName);
                //                        break;
            case ARConstantsEngine.SCREEN:
                break;
        }

        onHoldForSeconds(null);
    }

    public String performOperatorActions(
            boolean byPassNotFound,
            InstructionLoad instruction,
            InstructionLoad targetInstruction,
            String targetXPath,
            String[] parentOperations,
            String action,
            String[] operations,
            String parentField,
            String variableField,
            Integer variableId,
            RuntimeVariableStore runtimeVariables,
            Map<String, String> outputValues,
            WebElement instructionElement) {

        if (playwrightBridge.isPlaywrightOnlyMode()) {
            String playwrightMessage = "Error performing GET or SET through Playwright";
            boolean playwrightSuccess = false;
            try {
                if (targetInstruction != null
                        && ARConstantsEngine.SET_VALUE.equalsIgnoreCase(action)) {
                    playwrightMessage = "SET_VALUE to (Parent: " + parentField + ") Var:"
                            + variableField + " <-- " + operations[1];
                    playwrightSuccess = playwrightBridge.tryPlaywrightWebAction(
                            targetInstruction,
                            new FieldData(operations[0], operations[1]),
                            ARConstantsEngine.INSERT,
                            outputValues);
                    if (playwrightSuccess) {
                        runtimeVariables.write(variableId, operations[1].trim());
                    }
                } else if (targetInstruction != null
                        && ARConstantsEngine.GET_VALUE.equalsIgnoreCase(action)) {
                    playwrightMessage =
                            "GET_VALUE from (Parent: " + parentField + ") Var" + variableField;
                    String value = playwrightBridge.readPlaywrightText(targetInstruction);
                    if (value != null) {
                        if (!value.isEmpty()) {
                            playwrightMessage += " <-- " + value;
                        }
                        playwrightSuccess = runtimeVariables.write(variableId, value.trim());
                    }
                }
            } catch (RuntimeException error) {
                logOperations.warn(
                        "Playwright variable action '{}' failed; runtime value remains VOID: {}",
                        action,
                        error.getMessage());
            }
            return operatorActionResult(
                    instruction,
                    parentField,
                    playwrightMessage,
                    playwrightSuccess);
        }

        try {
            Thread.sleep(100);
        } catch (Exception ignore) {
        }

        if (!StringUtils.isBlank(targetXPath) && instructionElement == null) {
            instructionElement =
                    locateTargetElement(byPassNotFound, targetXPath, instruction.getActionCustomMaxWaitSec());
        }

        String msgReturn = "Error performing GET or SET";
        boolean success = false;

        if (instructionElement != null) {
            try {
                switch (action) {
                    case "SET":
                        msgReturn = "SET_VALUE to (Parent: " + parentField + ") Var:" + variableField + " <-- "
                                + operations[1];
                        insertTargetElement(byPassNotFound, instructionElement, operations[0], operations[1]);
                        runtimeVariables.write(variableId, operations[1].trim());
                        // Page interaction and variable memory are independent. Missing variable
                        // metadata leaves the runtime value VOID, but SET still reaches the page.
                        success = true;
                        break;

                    case "GET":
                        String valueElem;
                        msgReturn = "GET_VALUE from (Parent: " + parentField + ") Var" + variableField;
                        if (parentOperations[0].equals(ARConstantsEngine.OUTPUT)) {
                            valueElem = getOutPutElement(
                                    byPassNotFound,
                                    instructionElement,
                                    parentField,
                                    instruction.getActions(),
                                    outputValues);
                        } else {
                            valueElem = getValueInElement(byPassNotFound, instructionElement);
                        }
                        if (!Strings.isNullOrEmpty(valueElem)) {
                            msgReturn += " <-- " + valueElem;
                        }
                        success = valueElem != null
                                && runtimeVariables.write(variableId, valueElem.trim());
                        break;

                    case "CopyVar":
                        RuntimeVariableValue copyValue = runtimeVariables.read(variableId);
                        String valueVar = copyValue.isValue() ? copyValue.value() : "";
                        msgReturn = "COPY_VAR from (Parent: "
                                + parentField
                                + ") Var"
                                + variableField
                                + " <-- "
                                + valueVar;
                        success = copyValue.isValue();
                        break;
                }
                onHoldForSeconds(null);

            } catch (Exception error) {
                switch (action) {
                    case "SET":
                        msgReturn = String.format(
                                "SET failed - unable to set value \"%s\". Verify that the target element is correct, visible, and editable.",
                                "\"" + operations[1] + "\"");
                        break;
                }
            }
        } else {
            msgReturn = "Error: Instruction is null";
        }

        // =========================
        // 🔹 ONLY NEW PART (RETURN)
        // =========================

        String time = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());

        String testName = "";
        String mainField = "";

        if (parentField != null && parentField.contains("-")) {
            int idx = parentField.indexOf('-');
            testName = parentField.substring(0, idx).trim();
            mainField = parentField.substring(idx + 1).trim();
        } else {
            mainField = parentField == null ? "" : parentField;
        }

        String desc = instruction != null && instruction.getName() != null ? instruction.getName() : "";
        String result = success ? "PASSED" : "FAIL";
        String conditionText = msgReturn;

        return time + " | " + testName + " | " + desc + " | " + mainField + " | " + conditionText + " | " + result;
    }

    private String operatorActionResult(
            InstructionLoad instruction,
            String parentField,
            String message,
            boolean success) {
        String time = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
        String testName = "";
        String mainField = "";
        if (parentField != null && parentField.contains("-")) {
            int separator = parentField.indexOf('-');
            testName = parentField.substring(0, separator).trim();
            mainField = parentField.substring(separator + 1).trim();
        } else if (parentField != null) {
            mainField = parentField;
        }
        String description =
                instruction != null && instruction.getName() != null
                        ? instruction.getName()
                        : "";
        return time
                + " | "
                + testName
                + " | "
                + description
                + " | "
                + mainField
                + " | "
                + message
                + " | "
                + (success ? "PASSED" : "FAIL");
    }

    private WebElement locateTargetElement(boolean byPassNotFound, String targetXPath, Integer actionCustomMaxWaitSec) {
        return elementLocator.locateTargetElement(byPassNotFound, targetXPath, actionCustomMaxWaitSec);
    }

    private WebElement locateElement(
            InstructionLoad currentInstruction, int botJobId, boolean forceCoordinates, boolean byPassFlagLoop) {
        return elementLocator.locateElement(currentInstruction, botJobId, forceCoordinates, byPassFlagLoop);
    }

    private String normalizeLocatorValue(String referenceType, String value) {
        return WebTextUtils.normalizeLocatorValue(referenceType, value);
    }

    private String insertTargetElement(
            boolean byPassNotFound, WebElement element, String fieldName, String dataFieldValue) throws Exception {
        UtilsMethods.exceptionIfNullWebElement(element);
        try {
            waitForAction.until(ExpectedConditions.visibilityOf(element));
        } catch (Exception e) {

            logOperations.warn(String.format(
                    "Could Not Find Field Name \"%s\" Value \"%s\" -> Cause: %s",
                    fieldName, dataFieldValue, e.getMessage()));

            if (!byPassNotFound) {
                performMessage.couldNotFindElement(fieldName);
            }
        }

        if (dataFieldValue != null) {
            element.clear();
            element.sendKeys(dataFieldValue);
            element.sendKeys(Keys.TAB);
        }

        return fieldName + "->" + dataFieldValue;
    }

    private String getValueInElement(boolean byPassNotFound, WebElement element) throws Exception {
        UtilsMethods.exceptionIfNullWebElement(element);
        try {
            waitForAction.until(ExpectedConditions.visibilityOf(element));
        } catch (Exception e) {

            logOperations.warn(
                    String.format("Could Not Find TagName \"%s\" -> Cause: %s", element.getTagName(), e.getMessage()));

            if (!byPassNotFound) {
                performMessage.couldNotFindElement(element.getTagName());
            }
        }

        // Assuming instructionElement is an input field
        return element.getAttribute("value");
    }

    public synchronized String onHoldForSeconds(InstructionLoad instruction) throws Exception {
        if (instruction != null) {
            Integer instructionSeconds = instruction.getOnHoldSeconds();
            if (instructionSeconds != null && instructionSeconds > 0) {
                wait(fromSecondsToMilliseconds(TimeUnit.SECONDS, instructionSeconds));
                return "HOLD" + "->" + instructionSeconds + " seconds";
            } else {
                String stopSeconds = arPropertyManager.getProperty(ARPropertyEnum.INSTRUCTION_STOP_SECONDS);
                wait(fromSecondsToMilliseconds(TimeUnit.SECONDS, Integer.parseInt(stopSeconds)));
                return "HOLD" + "->" + stopSeconds + " seconds";
            }
        } else {
            wait(400);
            return "HOLD" + "->" + "400 milliseconds";
        }
    }

    public synchronized String onHoldInSeconds(Integer seconds) throws Exception {
        wait(fromSecondsToMilliseconds(TimeUnit.SECONDS, seconds));
        return "HOLD" + "->" + seconds + " seconds";
    }

    private long fromSecondsToMilliseconds(TimeUnit timeUnit, int units) throws Exception {
        return WaitSupport.fromSecondsToMilliseconds(timeUnit, units);
    }

    public void waitPage() {
        WaitSupport.waitPage(waitForPage, this.currentDriver);
    }

    public boolean scrollToElement(boolean byPassNotFound, WebElement element) throws Exception {
        return elementInteraction.scrollToElement(byPassNotFound, element);
    }

    public boolean clickElement(boolean byPassNotFound, WebElement element) throws Exception {
        return elementInteraction.clickElement(byPassNotFound, element);
    }

    public void refreshPage() {
        windowAndFrameManager.refreshPage();
    }

    public void goBackPage() {
        windowAndFrameManager.navigateBack();
    }

    private boolean insertInElement(
            boolean byPassNotFound,
            WebElement element,
            String dataFieldValue,
            String defaultValue,
            boolean isEncrypted,
            InputFlags flags)
            throws Exception {
        return elementInteraction.insertInElement(
                byPassNotFound, element, dataFieldValue, defaultValue, isEncrypted, flags);
    }

    public FieldData extractFieldData(
            Map<String, String> data, String[] actions, String defaultValue, boolean isEncrypted) throws Exception {
        return dataExtractor.extractFieldData(data, actions, defaultValue, isEncrypted);
    }

    public FieldData extractFieldData(
            ExtractedData extractedData,
            String blockName,
            int row,
            InstructionLoad instruction,
            String[] actions,
            String defaultValue,
            boolean isEncrypted)
            throws Exception {
        return dataExtractor.extractFieldData(
                extractedData, blockName, row, instruction, actions, defaultValue, isEncrypted);
    }

    private boolean insertDataInSelectElement(
            boolean byPassNotFound, WebElement element, String coordinates, FieldData data, boolean pressEnterAfter)
            throws Exception {
        return elementInteraction.insertDataInSelectElement(
                byPassNotFound, element, coordinates, data, pressEnterAfter);
    }

    private String getOutPutElement(
            boolean byPassNotFound,
            WebElement element,
            String fieldName,
            String action,
            Map<String, String> mapOperators)
            throws Exception {
        return dataExtractor.getOutPutElement(byPassNotFound, element, fieldName, action, mapOperators);
    }

    public void quit(int status) {
        engineDialogs.quit(status);
    }

    public short operationLog(boolean success, String mainMsg, String currentExecution, long duration) {
        return executionReporter.operationLog(success, mainMsg, currentExecution, duration);
    }

    public String pauseEngine(String blockName) {
        return engineDialogs.pauseEngine(blockName);
    }

    public String getValueIsNotDefinedEngine(
            InstructionLoad currentInstruction, String lastInstructionExecuted, boolean ifClause, boolean elseClause) {
        return engineDialogs.getValueIsNotDefinedEngine(
                currentInstruction, lastInstructionExecuted, ifClause, elseClause);
    }

    public String getValueIsNotDefined(
            String action,
            InstructionLoad currentInstruction,
            String lastInstructionExecuted,
            ARExecution.ConditionStatus conditionStatus,
            String parentField,
            String variableField) {
        return engineDialogs.getValueIsNotDefined(
                action, currentInstruction, lastInstructionExecuted, conditionStatus, parentField, variableField);
    }

    public String parentValueIsNotDefined(String instructionName, String parentField, String resultActions) {
        return engineDialogs.parentValueIsNotDefined(instructionName, parentField, resultActions);
    }

    public String parentValueIsNotDefinedEngine(String instructionName, String parentField, String resultActions) {
        return engineDialogs.parentValueIsNotDefinedEngine(instructionName, parentField, resultActions);
    }

    public String parentIdWrongBlockEngine(
            InstructionLoad currentInstruction, BlockLoadDTO blockLoad, boolean ifClause, boolean elseClause) {
        return engineDialogs.parentIdWrongBlockEngine(currentInstruction, blockLoad, ifClause, elseClause);
    }

    public String parentIdWrongBlock(
            InstructionLoad currentInstruction,
            BlockLoadDTO blockLoad,
            String lastInstructionExecuted,
            ARExecution.ConditionStatus conditionStatus) {
        return engineDialogs.parentIdWrongBlock(
                currentInstruction, blockLoad, lastInstructionExecuted, conditionStatus);
    }

    public String checkValidationFailedEngine(
            String parent,
            String expected,
            String lastInstructionExecuted,
            String[] operations,
            boolean ifClause,
            boolean elseClause,
            boolean byPassFlagLoop) {
        return engineDialogs.checkValidationFailedEngine(
                parent, expected, lastInstructionExecuted, operations, ifClause, elseClause, byPassFlagLoop);
    }

    public String checkValidationFailed(
            String invalidValues,
            String parent,
            String expected,
            String lastInstructionExecuted,
            String[] operations,
            ARExecution.ConditionStatus conditionStatus,
            boolean byPassFlagLoop) {
        return engineDialogs.checkValidationFailed(
                invalidValues, parent, expected, lastInstructionExecuted, operations, conditionStatus, byPassFlagLoop);
    }

    public String buildValidationReason(
            String invalidValues,
            String parent,
            String actualValue, // current web field value
            String expectedValue, // EXPECTED VALUE AS PARAM
            String lastInstructionExecuted,
            String[] operations, // [0]=variableName, [1]=operator
            ARExecution.ConditionStatus conditionStatus,
            boolean byPassFlagLoop,
            boolean includeLengths,
            String blockName,
            Integer testRow,
            boolean success) {

        return ValidationMessageBuilder.buildValidationReason(
                invalidValues,
                parent,
                actualValue,
                expectedValue,
                lastInstructionExecuted,
                operations,
                conditionStatus,
                byPassFlagLoop,
                includeLengths,
                blockName,
                testRow,
                success);
    }

    //    /**
    //     * Builds a human-readable reason when a required GET variable is missing / not assigned.
    //     *
    //     * IMPORTANT:
    //     * - This method does NOT log.
    //     * - Callers can logOperations.error(...) OUTSIDE using the returned string.
    //     * - It follows the same principle as buildValidationFailureReason:
    //     *   - If conditionStatus != NONE -> prepend block transition text + " -> " + lastInstructionExecuted
    //     *   - If conditionStatus == NONE -> return lastInstructionExecuted (keep original behavior)
    //     *
    //     * If you want a message even when NONE, just change the last return branch accordingly.
    //     */
    //    public String buildGetVariableReason(
    //            String action,
    //            InstructionLoad currentInstruction,
    //            String lastInstructionExecuted,
    //            ARExecution.ConditionStatus conditionStatus,
    //            String parentField,
    //            String variableField,
    //            boolean byPassFlagLoop) {
    //
    //        // If bypassing, behave like original logic: just return the last instruction executed
    //        if (byPassFlagLoop) {
    //            return withConditionalPrefix(conditionStatus, lastInstructionExecuted);
    //        }
    //
    //        // Preserve raw values
    //        String instrName =
    //                currentInstruction != null && currentInstruction.getName() != null ? currentInstruction.getName()
    // : "?";
    //
    //        String var = variableField == null ? "?" : variableField;
    //        String parent = parentField == null ? "" : parentField;
    //
    //        // Build a concise reason (plain text; keep HTML out because caller may log it)
    //        String summary;
    //        if (ARConstantsEngine.EXTRACT_FIELD.equals(action) || ARConstantsEngine.CHECK_VALUE.equals(action)) {
    //            summary = String.format(
    //                    "Get Value Is Not Defined - variable \"%s\" has not been assigned (instruction \"%s\")",
    //                    var, instrName);
    //        } else {
    //            if (parentField != null) {
    //                summary = String.format(
    //                        "Get Value Is Not Defined - no GET value defined for instruction \"%s\" (parent field
    // \"%s\")",
    //                        instrName, parent);
    //            } else {
    //                summary = String.format(
    //                        "Get Value Is Not Defined - no GET value defined for instruction \"%s\" (parent field not
    // defined)",
    //                        instrName);
    //            }
    //        }
    //
    //        // Follow the SAME “append lastInstructionExecuted + conditional prefix” pattern
    //        // Note: original getValueIsNotDefined returned lastInstructionExecuted when conditionStatus==NONE.
    //        // We keep that behavior here.
    //        String reasonWithTrail = summary + " -> " + lastInstructionExecuted;
    //
    //        return withConditionalPrefix(conditionStatus, reasonWithTrail);
    //    }

    public String messageExcel(
            String action,
            InstructionLoad instruction,
            String parentField,
            String variableField,
            String value,
            String blockName,
            Integer testRow,
            boolean success) {
        return executionReporter.messageExcel(
                action, instruction, parentField, variableField, value, blockName, testRow, success);
    }

    public static String sanitizeValue(String input) {
        return ValidationMessageBuilder.sanitizeValue(input);
    }

    /**
     * Builds a JTable row (pipe-separated) for CHECK_VALUE / validation messages.
     *
     * Output pattern:
     *   time | testName | desc | mainField | conditionText | result
     */
    public String checkValidationMesssage(
            String action,
            InstructionLoad currentInstruction,
            String lastInstructionExecuted,
            ARExecution.ConditionStatus conditionStatus,
            String parentField, // e.g. "8838-BancaStato" OR "BancaStato"
            String variableField, // e.g. "$BancaStato"
            String actualValue, // actual extracted value
            String expectedValue, // expected value
            String operator, // "=", "!=", ">", "<", etc.
            boolean byPassFlagLoop,
            String blockName,
            Integer testRow,
            boolean includeLengths,
            boolean success) {

        return ValidationMessageBuilder.checkValidationMesssage(
                action,
                currentInstruction,
                lastInstructionExecuted,
                conditionStatus,
                parentField,
                variableField,
                actualValue,
                expectedValue,
                operator,
                byPassFlagLoop,
                blockName,
                testRow,
                includeLengths,
                success);
    }

    /**
     * Builds a JTable row (same pipe-separated pattern as buildValidationReason)
     * for the case "GET variable missing / not assigned".
     *
     * Output pattern:
     *   time | testName | desc | mainField | conditionText | result
     */
    public String buildGetVariableReason(
            String action,
            InstructionLoad currentInstruction,
            String lastInstructionExecuted,
            ARExecution.ConditionStatus conditionStatus,
            String parentField, // MAIN FIELD (e.g. "BancaStato")
            String variableField, // variable name (e.g. "$BancaStato")
            boolean byPassFlagLoop,
            String blockName,
            Integer testRow,
            boolean success // usually false for "not defined"
            ) {

        return ValidationMessageBuilder.buildGetVariableReason(
                action,
                currentInstruction,
                lastInstructionExecuted,
                conditionStatus,
                parentField,
                variableField,
                byPassFlagLoop,
                blockName,
                testRow,
                success);
    }

    private String normalizeNumber(String value) {
        return ValidationMessageBuilder.normalizeNumber(value);
    }

    private String withConditionalPrefix(ARExecution.ConditionStatus conditionStatus, String message) {
        return ValidationMessageBuilder.withConditionalPrefix(conditionStatus, message);
    }

    public boolean excelReportWrite(
            ARExecution.ConditionStatus currentCondition,
            String blockName,
            boolean success,
            String[] actions,
            FieldData msgLoop,
            long duration,
            Map<String, String> dataExcel,
            ExcelWriter.ExcelChain writerReport) {
        return executionReporter.excelReportWrite(
                currentCondition, blockName, success, actions, msgLoop, duration, dataExcel, writerReport);
    }

    public long duration(long startTime) {
        return WaitSupport.duration(startTime);
    }

    public String blockGotoFailed(String resultActions) {
        return engineDialogs.blockGotoFailed(resultActions);
    }

    public void gotoLimitExecution(int executionTimes, String lastInstructionExecuted) {
        engineDialogs.gotoLimitExecution(executionTimes, lastInstructionExecuted);
    }

    // Update the list of window handles (tabs)
    public void updateWindowHandlesList() {
        windowAndFrameManager.updateWindowHandlesList();
    }

    public void alertMessage(String message) {
        engineDialogs.alertMessage(message);
    }

    public String actionResultMessage(String blockJobName, String[] actions, FieldData msgInstruction) {
        return engineDialogs.actionResultMessage(blockJobName, actions, msgInstruction);
    }

    public String buildMessageResult(
            boolean success, String testName, String description, String mainField, String conditionText) {

        String time = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));

        String result = success ? "PASSED" : "FAIL";

        return time + " | " + testName + " | " + description + " | " + mainField + " | " + conditionText + " | "
                + result;
    }

    public int[] addElementToArray(int[] refreshLoopArray, int newItem) {
        int[] extendedRefreshArray = new int[refreshLoopArray.length + 1];
        System.arraycopy(refreshLoopArray, 0, extendedRefreshArray, 0, refreshLoopArray.length);
        extendedRefreshArray[refreshLoopArray.length] = newItem;
        return extendedRefreshArray;
    }

    public String getXPathInstruction(InstructionLoad currentInstruction, BlockLoadDTO blockLoad) {
        return InstructionGraph.getXPathInstruction(currentInstruction, blockLoad);
    }

    public String getInstructionParentField(InstructionLoad currentInstruction, BlockLoadDTO blockLoad) {
        return InstructionGraph.getInstructionParentField(currentInstruction, blockLoad);
    }

    public String getInstructionParentActions(InstructionLoad currentInstruction, BlockLoadDTO blockLoad) {
        return InstructionGraph.getInstructionParentActions(currentInstruction, blockLoad);
    }

    public String getInstructionVariableField(InstructionLoad currentInstruction, List<VariableLoadDTO> variableLoad) {
        return InstructionGraph.getInstructionVariableField(currentInstruction, variableLoad);
    }

    public String getInstructionVariableFormat(InstructionLoad currentInstruction, List<VariableLoadDTO> variableLoad) {
        return InstructionGraph.getInstructionVariableFormat(currentInstruction, variableLoad);
    }

    public String getInstructionVariableDelimiter(
            InstructionLoad currentInstruction, List<VariableLoadDTO> variableLoad) {
        return InstructionGraph.getInstructionVariableDelimiter(currentInstruction, variableLoad);
    }

    public int searchMapConditional(
            Map<String, List<Integer>> mapConditional,
            int parentBlockCondition,
            ARExecution.ConditionStatus condition,
            int currentIndex,
            boolean showMessage) {
        return InstructionGraph.searchMapConditional(
                mapConditional, parentBlockCondition, condition, currentIndex, showMessage);
    }

    public Map<String, List<Integer>> getConditionIndexMapByParentId(BlockLoadDTO blockLoad) {
        return InstructionGraph.getConditionIndexMapByParentId(blockLoad);
    }

    /**
     * Find elements by splitting a CSS locator into tag, ID, and classes.
     * Returns a combined list of unique WebElements.
     */
    public List<WebElement> findBySmartLocator(String locator) {
        return elementLocator.findBySmartLocator(locator);
    }

    public boolean executeActionsAtCoordinates(
            String savedCoordinates, FieldData data, String action, boolean pressEnterAfter) {
        return coordinateActions.executeActionsAtCoordinates(savedCoordinates, data, action, pressEnterAfter);
    }

    public WebElement getElementFromCoordinates(String savedCoordinates) {
        return coordinateActions.getElementFromCoordinates(savedCoordinates);
    }

    public String sequenceOfCommands(
            WebElement element,
            String typeCommand,
            String[] coordinates,
            FieldData fieldData,
            WebDriver driver,
            boolean pressEnterAfter) {
        return elementInteraction.sequenceOfCommands(
                element, typeCommand, coordinates, fieldData, driver, pressEnterAfter);
    }

    private void clearElement(WebElement element) {
        elementInteraction.clearElement(element);
    }

    public boolean setValueAtCoordinates(String savedCoords, String textToSet) {
        return coordinateActions.setValueAtCoordinates(savedCoords, textToSet);
    }

    public boolean clearValueAtCoordinates(String savedCoords) {
        return coordinateActions.clearValueAtCoordinates(savedCoords);
    }

    public boolean clickElementAtCoordinates(String savedCoords) {
        return coordinateActions.clickElementAtCoordinates(savedCoords);
    }

    public void sendInputJS(int x, int y, String text, WebDriver driver) {
        coordinateActions.sendInputJS(x, y, text, driver);
    }

    public String moveAndClickAtCoordinates(String savedCoordinates, boolean pressEnterAfter) {
        return coordinateActions.moveAndClickAtCoordinates(savedCoordinates, pressEnterAfter);
    }

    public FieldData getBlockDetailsById(List<BlockLoadDTO> blocksLoaded, InstructionLoad currentInstruction) {
        return InstructionGraph.getBlockDetailsById(blocksLoaded, currentInstruction);
    }

    public int getBlockOrderNumber(List<BlockLoadDTO> blocksLoaded, Integer parentBlockId) {
        return InstructionGraph.getBlockOrderNumber(blocksLoaded, parentBlockId);
    }

    public FieldData getInstructionDetailsById(
            List<InstructionLoad> InstructionLoadS, InstructionLoad currentInstruction) {
        return InstructionGraph.getInstructionDetailsById(InstructionLoadS, currentInstruction);
    }

    public Map<String, Integer[]> getLoopAndRefreshLoops(List<InstructionLoad> InstructionLoadS) {
        return InstructionGraph.getLoopAndRefreshLoops(InstructionLoadS);
    }

    public Set<Integer> getParentIdsForLoop(List<InstructionLoad> InstructionLoadS) {
        return InstructionGraph.getParentIdsForLoop(InstructionLoadS);
    }

    public Set<Integer> getAllOutputsPerBlock(List<InstructionLoad> InstructionLoadS) {
        return InstructionGraph.getAllOutputsPerBlock(InstructionLoadS);
    }

    public void logAndReport(
            ARExecution.ConditionStatus currentCondition,
            boolean excelReport,
            boolean logOperation,
            long blockStartTime,
            String blockReportName,
            boolean success,
            String[] action,
            FieldData msgBlock,
            Map<String, String> dataExcel,
            ExcelWriter.ExcelChain writerReport,
            String mainMsg,
            String bodyLog) {
        executionReporter.logAndReport(
                currentCondition,
                excelReport,
                logOperation,
                blockStartTime,
                blockReportName,
                success,
                action,
                msgBlock,
                dataExcel,
                writerReport,
                mainMsg,
                bodyLog);
    }

    public ARExecution.ConditionStatus updateProgressSuccess(
            boolean success, ARExecution.ConditionStatus currentCondition) {
        return executionReporter.updateProgressSuccess(success, currentCondition);
    }

    public int checkActionToJump(
            String action,
            ARExecution.ConditionStatus progressCondition,
            Map<String, List<Integer>> mapConditional,
            int parentBlockCondition,
            int currentIndex) {
        return InstructionGraph.checkActionToJump(
                action, progressCondition, mapConditional, parentBlockCondition, currentIndex);
    }

    public ElementDTO convertTargetToElementDTO(TargetElement targetElement) {
        return ElementDtoMapper.convertTargetToElementDTO(targetElement);
    }

    public TargetElement defineSearchReturn(ElementDTO elemenDTO, TargetElement targetDefine) {
        return ElementDtoMapper.defineSearchReturn(elemenDTO, targetDefine);
    }

    public ElementDTO buildElementDTO(InstructionLoad instructionDTO) {
        return ElementDtoMapper.buildElementDTO(instructionDTO);
    }

    public TargetElement defineNameTitles(TargetElement target) {
        return ElementDtoMapper.defineNameTitles(target);
    }

    public TargetElement defineTagType(TargetElement targetTagType) {
        return ElementDtoMapper.defineTagType(targetTagType);
    }

    private boolean isValidString(String value) {
        return WebTextUtils.isValidString(value);
    }

    public boolean isClickable(WebElement element, String tagNameDefined) {
        return ElementDtoMapper.isClickable(element, tagNameDefined);
    }

    public WebElement findShadowElementByCssSelector(String shadowLocator, String cssSelector) {
        return elementLocator.findShadowElementByCssSelector(shadowLocator, cssSelector);
    }

    public InstructionLoad buildNewInstruction(
            WebElementTagNameEnum forceTag,
            String actionReq,
            boolean identityHover,
            Integer orderNumber,
            TargetElement targetBuild) {
        return ElementDtoMapper.buildNewInstruction(forceTag, actionReq, identityHover, orderNumber, targetBuild);
    }

    public Map<String, String> defineSavedReferenced(TargetElement targetRefs) {
        return ElementDtoMapper.defineSavedReferenced(targetRefs);
    }

    public WebElement findWebElement(TargetElement targetFind) {
        return elementLocator.findWebElement(targetFind);
    }

    public WebElement findElementByCssSelector(String cssSelector) throws Exception {
        return elementLocator.findElementByCssSelector(cssSelector);
    }

    public WebElement findElementByCssSelector(String cssSelector, boolean byPassNotFound) throws Exception {
        return elementLocator.findElementByCssSelector(cssSelector, byPassNotFound);
    }

    public Map<String, String> removeCurrencySymbols(Map<String, String> mapExport) {
        return WebTextUtils.removeCurrencySymbols(mapExport);
    }

    public String removeAllCurrencySymbols(String input) {
        return WebTextUtils.removeAllCurrencySymbols(input);
    }

    public String formatLocalNumber(String numberString, String localFormat) {
        return WebTextUtils.formatLocalNumber(numberString, localFormat);
    }

    //    private void listOperation(boolean byPassNotFound, InstructionLoad instructionDTO) {
    //
    //        /*
    //        TODO: Da rivedere, attualmente non del tutto funzionante
    //        Complex instruction string interpretation:
    //        [       0       ||       1      ||       2         ||    3    ||        4       ||  5   ||            6
    //             ]
    //
    // [backward_button||forward_button||list_elements_tag||condition||expected_results||action||sub_element_on_execute_action]
    //        */
    //        List<ComplexInstructionLoad> complexInstructionDTOS =
    // instructionDTO.getComplexInstructionLoadList();
    //        String[] complexActionParts =
    //                complexInstructionDTOS.get(0).getInstruction().split(ARConstants.COMPLEX_INSTRUCTION_SEPARATOR);
    //        List<WebElement> webElementList;
    //        WebElement forwardButton;
    //        WebElement backwardButton;
    //        boolean shouldContinue = true;
    //
    //        boolean existNextPage;
    //        do {
    //            try {
    //
    // waitForPage.until(ExpectedConditions.visibilityOfElementLocated(By.tagName(complexActionParts[2])));
    //            } catch (Exception e) {
    //
    //                        operationsLog.warn(String.format(
    //                                "Could Not Find TagName \"%s\" Criteria \"%s\" -> Cause: %s",
    //                                complexActionParts[2], By.tagName(complexActionParts[2]), e.getMessage()));
    //
    //                if (!byPassNotFound) {
    //                    performMessage.couldNotFindElement(complexActionParts[2]);
    //                }
    //            }
    //
    //            backwardButton = this.currentDriver.findElement(By.xpath(complexActionParts[0]));
    //            forwardButton = this.currentDriver.findElement(By.xpath(complexActionParts[1]));
    //            webElementList = this.currentDriver.findElements(By.tagName(complexActionParts[2]));
    //
    //            WebElement element;
    //            WebElement reasonWebElement;
    //            for (int i = 0; i < 5; i++) {
    //
    //                if (i != 0) {
    //
    //                    try {
    //                        waitForPage.until(
    //                                ExpectedConditions.visibilityOfElementLocated(By.tagName(complexActionParts[2])));
    //                        webElementList = this.currentDriver.findElements(By.tagName(complexActionParts[2]));
    //                    } catch (Exception e) {
    //
    //                                operationsLog.warn(String.format(
    //                                        "Could Not Find TagName \"%s\" Criteria \"%s\" -> Cause: %s",
    //                                        complexActionParts[2], By.tagName(complexActionParts[2]),
    // e.getMessage()));
    //
    //                        if (!byPassNotFound) {
    //                            performMessage.couldNotFindElement(complexActionParts[2]);
    //                        }
    //                    }
    //                }
    //
    //                element = webElementList.get(i);
    //                try {
    //                    Thread.sleep(1000);
    //
    //                    reasonWebElement = element.findElement(
    //                            By.xpath(".//div[@class='payments-table-field reason ng-star-inserted']"));
    //                    if (!UtilsMethods.testFixedCheck(reasonWebElement.getText())) {
    //                        continue;
    //                    }
    //
    //                    clickElement(
    //                            byPassNotFound,
    //                            element.findElement(By.xpath(
    //                                    ".//button[@test-id='web-banking-payment-core.payment-ctx-action.button']")));
    //                    clickElement(
    //                            byPassNotFound,
    //                            this.currentDriver.findElement(
    //                                    By.xpath(
    //
    // ".//button[@test-id='web-banking-payment-core.payment-ctx-action.payment-action-VIEW']")));
    //
    //                    Thread.sleep(1000);
    //                    clickElement(
    //                            byPassNotFound,
    //                            this.currentDriver.findElement(By.xpath(
    //
    // ".//button[@test-id='web-banking-common.export-to-file.single-file-button']")));
    //
    //                    Thread.sleep(1000);
    //                    clickElement(
    //                            byPassNotFound,
    //                            this.currentDriver.findElement(
    //                                    By.xpath(
    //
    // ".//avq-breadcrumb[@test-id='web-banking-portal.pages.payments-overview.breadcrumb']")));
    //                } catch (Exception e) {
    //                    operationsLog.warn("Impossible execute operation on this element: " + element.toString());
    //                }
    //            }
    //
    //            try {
    //                scrollToElement(byPassNotFound, forwardButton);
    //                clickElement(byPassNotFound, forwardButton);
    //                existNextPage = true;
    //            } catch (Exception e) {
    //                existNextPage = false;
    //            }
    //
    //        } while (existNextPage && shouldContinue);
    //    }

}

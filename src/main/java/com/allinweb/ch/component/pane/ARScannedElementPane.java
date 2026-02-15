package com.allinweb.ch.component.pane;

import com.allinweb.ch.builder.WebElementAttributeEnum;
import com.allinweb.ch.builder.WebElementAttributeTypeValueEnum;
import com.allinweb.ch.builder.WebElementTagNameEnum;
import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.component.scene.ARNewHomeBankingScene;
import com.allinweb.ch.component.scene.ARScannedElementScene;
import com.allinweb.ch.control.ARComponentBuilder;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.facade.*;
import com.allinweb.ch.model.*;
import com.allinweb.ch.readersAndWriters.ExcelReader;
import com.allinweb.ch.readersAndWriters.ExcelWriter;
import com.allinweb.ch.socket.WebSocketSessionManager;
import com.allinweb.ch.util.*;
import com.allinweb.ch.vision.VisionElementMapper;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import io.opentelemetry.api.internal.StringUtils;
import java.io.*;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.Alert;
import javafx.scene.layout.*;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

@Slf4j
public class ARScannedElementPane extends ARPane {

    private static final Logger logLaunch = LoggerFactory.getLogger("com.allinweb.launch");
    private static final Logger logOperations = LoggerFactory.getLogger("com.allinweb.operations");

    private static final ARComponentBuilder builder = ARComponentBuilder.getInstance();
    private static final String END_OF_FILE_MARKER = "END OF FILE";
    // Very important sequence on initiation
    private static final ARPropertyManager arPropertyManager = ARPropertyManager.getInstance();
    private static final ARPriorities arPriorities = ARPriorities.getInstance();
    private static final WebSocketSessionManager webSocketSessionManager = WebSocketSessionManager.getInstance();
    private static final ARScannedElementScene arScannedElementScene = ARScannedElementScene.getInstance();
    private static final PerformCloneLoad performCloneLoad = PerformCloneLoad.getInstance();
    private static final PerformLists performLists = PerformLists.getInstance();
    private static final PerformDBEngine performDBEngine = PerformDBEngine.getInstance();
    private static final PerformDataBase performDataBase = PerformDataBase.getInstance();
    private static final PerformActions performActions = PerformActions.getInstance();
    private static final PerformMessage performMessage = PerformMessage.getInstance();
    private static final PerformPreLoad performPreLoad = PerformPreLoad.getInstance();
    private static final PerformListElements performListElements = PerformListElements.getInstance();
    private static final ARNewHomeBankingScene arNewHomeBankingScene = ARNewHomeBankingScene.getInstance();
    public static TargetElement targetSelected = new TargetElement();
    protected static volatile ARScannedElementPane instance;
    private static SimpleDateFormat dateFormatter;
    private static String excelPath = null;
    private static JavascriptExecutor jsExecutor;
    private static String[] lstAllPaths;
    public final AtomicBoolean isJobRunning = new AtomicBoolean(false);
    private final Gson gson = new Gson();
    private final WebView webView = new WebView();
    public Button launchBotJobButton;
    public CheckBox checkClickElement;
    public CheckBox checkInputText;
    public CheckBox checkOutputText;
    public TextField defineNameField;
    public TextField searchAttribValueField;
    public String xpathTextPrevious;
    protected BooleanProperty interceptBotJob = new SimpleBooleanProperty(false);
    double comboWidth = 200;
    Button refreshBlocksButton;
    String excelFieldName;
    String delimiterCSV = null;
    private Stage stage;
    private Set<String> windowHandles;
    private ExecutorService executorServicePreLaunch;
    private int portSocketInitial = 54525;
    private BotJobLoadDTO currentBotJob;
    private static String currentBotJobName = null;
    private int currentBlockId;
    private int currentBlockOrder;
    private int executeSpecificBlock;
    private Integer lastInstructionIdPushed = null;
    private boolean firstPageLoadDone = false;
    private boolean isMobileApp = false;
    private SplitDTO splitDTO = new SplitDTO();
    private ExtractedData extractedData = null;
    private List<BlockLoadDTO> blocksLoaded;
    private List<InstructionLoad> excelDataGoto = new ArrayList<>();
    private ComboBox<BlockOptions> comboBoxBlocks;
    // UI COMPONENTS
    private HBox topPane;
    private VBox verticalBox;
    private AnchorPane mainPane;
    private WebEngine webEngine;
    private VBox elements2VBox;
    private HBox componentBox;
    private Button cloneElementsButton;
    private Button configureButton;
    private Button stopBotJobButton;
    private Button pageScannerButton;
    private Button refreshWebPageButton;
    private Button leftButton;
    private Button rightButton;
    private Button cleanListButton;
    private Button turnOnOffButton;
    private Button searchButton;
    private CheckBox checkCloneElement;
    private Label testActionLabel;
    private CheckBox checkForceEnterText;
    private CheckBox checkForceCoordText;
    private Label searchTermsLabel;
    private Label defineNameLabel;
    private Label coordsTextFieldLabel;
    private Text currentURL;
    private Text iFrameText;
    private VBox textFieldVBox;
    //    private TextFlow textFlowResult;
    private TextArea countdownTextField;
    private TextField searchTermsField;
    private TextField testActionsField;
    private TextField coordsTextField;
    private Map<String, String> mapOperators = new HashMap<>();
    private Map<String, String> mapExportRows = new HashMap<>();
    private Set<String> headersExport = new LinkedHashSet<>();
    private List<String> columnsCSV = new ArrayList<>(); // set once
    private List<CsvRow> rowsCSV = new ArrayList<>();
    CsvTable tableCSV = new CsvTable();
    private List<VariableLoadDTO> variablesLoaded;
    private String[] defaultSearch;
    private boolean searchHiddenFields;
    private String sessionIdFromJava;
    private String sessionRowStatus;
    private String jsonStatus;
    private RowStatus rowStatus = new RowStatus();
    private PayloadJson payloadEmpty;
    private ARWebDriver currentARWebDriver;
    WebDriverWait waitXPath = null;

    // Private constructor to prevent instantiation
    private ARScannedElementPane() {}

    public static ARScannedElementPane getInstance() {
        if (instance == null) {
            synchronized (ARScannedElementPane.class) {
                if (instance == null) {
                    instance = new ARScannedElementPane();
                }
            }
        }
        return instance;
    }

    public static double jaccardSimilarity(String text1, String text2) {
        Set<Character> set1 = new HashSet<>();
        for (char c : text1.toCharArray()) {
            set1.add(c);
        }

        Set<Character> set2 = new HashSet<>();
        for (char c : text2.toCharArray()) {
            set2.add(c);
        }

        Set<Character> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        Set<Character> union = new HashSet<>(set1);
        union.addAll(set2);

        return (double) intersection.size() / union.size();
    }

    // Method to get XPath of a WebElement
    public static String getXPath(WebDriver driver, WebElement element) {
        return (String) ((JavascriptExecutor) driver)
                .executeScript(
                        "function getElementXPath(elt) {" + "    var path = '';"
                                + "    for (; elt && elt.nodeType == 1; elt = elt.parentNode) {"
                                + "        var idx = getElementIdx(elt);"
                                + "        var xname = elt.tagName;"
                                + "        if (idx > 1) xname += '[' + idx + ']';"
                                + "        path = '/' + xname + path;"
                                + "    }"
                                + "    return path;"
                                + "}"
                                + "function getElementIdx(elt) {"
                                + "    var count = 1;"
                                + "    for (var sib = elt.previousSibling; sib; sib = sib.previousSibling) {"
                                + "        if (sib.nodeType == 1 && sib.tagName == elt.tagName) count++;"
                                + "    }"
                                + "    return count;"
                                + "}"
                                + "return getElementXPath(arguments[0]);",
                        element);
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

    public static String truncate(String someText, int limit) {
        if (someText == null || someText.isEmpty()) {
            return someText;
        }

        if (someText.length() <= limit) {
            return someText;
        }

        return someText.substring(0, limit) + "...";
    }

    private static By[] parseLocators(String input) {
        // Split the input string by commas to get individual locator strings
        // DB Access Cannot have "'"
        input = input.replace("\"", "'");

        String[] locatorStrings = input.split(",");

        // List to hold the By objects
        List<By> byList = new ArrayList<>();

        // Loop through each locator string
        for (String locatorString : locatorStrings) {
            // Split each locator string by colon to separate the type and value
            String[] parts = locatorString.split(":");

            // Get the type and value
            String type = parts[0].replace("By.", "").toUpperCase();
            String value = String.join(",", Arrays.copyOfRange(parts, 1, parts.length));

            value = value.replace("COMMA", ",");

            // Create the By object based on the type
            switch (LocatorType.valueOf(type)) {
                case TAGNAME:
                    byList.add(By.tagName(value));
                    break;
                case ID:
                    byList.add(By.id(value));
                    break;
                case CLASSNAME:
                    byList.add(By.className(value));
                    break;
                case CSSSELECTOR:
                    byList.add(By.cssSelector(value));
                    break;
                case XPATH:
                    byList.add(By.xpath(value));
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported locator type: " + type);
            }
        }

        // Convert the list to an array and return
        return byList.toArray(new By[0]);
    }

    public static List<InstructionLoad> getUnexecutedInstructions(
            List<InstructionLoad> instructionsExecuted, List<InstructionLoad> otherList) {
        // Create a set of instructionOrderNumbers from instructionsExecuted
        Set<Integer> executedInstructionOrderNumbers = instructionsExecuted.stream()
                .map(InstructionLoad::getInstructionOrderNumber)
                .collect(Collectors.toSet());

        // Filter the otherList to get instructions where executed is false and not in executedInstructionOrderNumbers
        return otherList.stream()
                //                .filter(instruction -> instruction.getExecuted() != null &&
                // !instruction.getExecuted())
                .filter(instruction ->
                        !executedInstructionOrderNumbers.contains(instruction.getInstructionOrderNumber()))
                .collect(Collectors.toList());
    }

    private static String generateTimestamp() {
        Date date = new Date();
        dateFormatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        return dateFormatter.format(date);
    }
    //    private static final PerformCloseBrowser performCloseBrowser;

    private static void printLog(String resultActions, boolean result) {
        String resultMsg = result ? ARConstants.SUCCESS : ARConstants.FAIL;
        String log = String.join(ARConstants.FIELDS_SEPARATOR, resultMsg, resultActions);
        logLaunch.info(log);
    }

    /**
     * Finds all elements of the specified tag name without "id" or "name" attributes and returns a map with their XPaths as keys.
     *
     * @param driver  the WebDriver instance
     * @param tagName the tag name of the elements to find (e.g., "input", "button")
     * @return a map where keys are XPaths of elements and values are WebElements
     */
    private static Map<String, WebElement> findElementsWithoutIdOrName(WebDriver driver, String tagName) {
        jsExecutor = (JavascriptExecutor) driver;
        List<WebElement> elements = (List<WebElement>) jsExecutor.executeScript(
                "return Array.from(document.querySelectorAll('" + tagName + ":not([id]):not([name])'));");
        Set<WebElement> uniqueElements = new HashSet<>(elements);
        Map<String, WebElement> elementMap = new HashMap<>();
        for (WebElement element : uniqueElements) {
            String xpath = getElementXPath(driver, element);
            elementMap.put(xpath, element);
        }
        return elementMap;
    }

    /**
     * Constructs the XPath of a given WebElement.
     *
     * @param driver  the WebDriver instance
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

    private static void showAlertInfo(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private static String loadScriptFromResource(String resourcePath) throws IOException {
        // Use ClassLoader to get the resource as an InputStream
        try (InputStream inputStream =
                ARScannedElementPane.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }

            // Convert InputStream to String
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static boolean isBrowserClosed(WebDriver webDriver) {
        try {
            webDriver.getTitle(); // Try accessing a property
            return false; // If no exception, browser is open
        } catch (Exception e) {
            return true; // If exception occurs, browser is closed
        }
    }

    private static int getMajorJavaVersion(String version) {
        // For Java 9 and above, the version string starts with the major version (e.g., "17.0.1")
        // For Java 8 and below, it starts with "1." (e.g., "1.8.0_311")
        if (version.startsWith("1.")) {
            return Integer.parseInt(version.substring(2, 3)); // e.g., "1.8" -> 8
        } else {
            String[] parts = version.split("\\.");
            return Integer.parseInt(parts[0]); // e.g., "17.0.1" -> 17
        }
    }

    // Helper method for distinct by text
    private static Predicate<BlockOptions> distinctByText() {
        Set<String> seen = new HashSet<>();
        return b -> seen.add(b.getText());
    }

    // Helper method for distinct by text AND blockOrderNumber
    private static Predicate<BlockOptions> distinctByTextAndId() {
        Set<String> seen = new HashSet<>();
        return b -> {
            // Combine text and blockOrderNumber as a unique key
            String key = b.getText() + "#" + b.getBlockId();
            return seen.add(key);
        };
    }

    public void destroy() {
        clearPane(getPaneReference());
        pane = null;
        scene = null;
        instance = null;
    }

    private void preTestCoordinates(TargetElement targetPreTest) {

        FieldData filedData = new FieldData("martini", "Martini");
        try {
            if (checkCloneElement.isSelected()) {

                performActions.executeActionsAtCoordinates(
                        targetPreTest.getCoordinates(), filedData, ARConstants.CLICK, false);
            } else {
                performActions.executeActionsAtCoordinates(
                        targetPreTest.getCoordinates(), filedData, ARConstants.COORD_MOVE_CLICK_RED, false);
            }

        } catch (Exception e) {
            logOperations.info(e.getMessage());
        }
    }

    public int validateBlockDB(String blockTable, int whereId, String message) {
        int newBlockID = createBlockIfNone(blockTable, whereId);
        if (newBlockID > 0) {
            ErrorMessage errorMessage = performDataBase.loadBlocks(whereId, "", blockTable);
            if (errorMessage == null) {
                refreshBlocks(true);
            }
        }

        if (newBlockID > 0) {
            currentBlockId = newBlockID;
        } else {
            try {
                currentBlockId = comboBoxBlocks.getValue().getBlockId();
                executeSpecificBlock = comboBoxBlocks.getValue().getBlockOrderNumber() < 0
                        ? 0
                        : comboBoxBlocks.getValue().getBlockOrderNumber() - 1;
            } catch (Exception error) {
                currentBlockId = -1;
                executeSpecificBlock = 0;
            }

            if (currentBlockId < 0) {
                performMessage.errorMessage(
                        "Operation \"" + message + "\" No Block Selected",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>No Block Selected ❌</span>",
                        "<span style='color: #E65100; font-weight: bold;'>You must select a Block from the dropdown list</span> before adding a new command.",
                        "<span style='font-style: italic;'>Context:</span> Bot Job: <b>" + currentBotJob.getName()
                                + "</b>",
                        "<span style='color: #455A64;'>Tip: Use the block selector (ComboBox) above the table to choose the target block.</span>",
                        0);

            } else {
                return currentBlockId;
            }
        }
        return newBlockID;
    }

    public void prepareToInsertElementDTO(
            List<InstructionLoad> instructionList,
            int currentBlockId,
            int nextInstOrderNumber,
            TargetElement targetInsert,
            boolean manyElements) {

        if (targetInsert.getXPath() == null) {
            targetInsert.setXPath(targetInsert.getSavedReferences().get("currentXPath"));
        }

        if (targetInsert.getCoordinates() == null) {
            targetInsert.setCoordinates(targetInsert.getSavedReferences().get("coordinates"));
        }

        String actionReq = targetInsert.getTagName();
        if (!manyElements) {
            actionReq = checkClickElement.isSelected()
                    ? ARConstants.CLICK
                    : checkInputText.isSelected()
                            ? ARConstants.INSERT
                            : checkOutputText.isSelected() ? ARConstants.OUTPUT : ARConstants.OTHER;
        }

        targetInsert.setClickElement(checkClickElement.isSelected());
        WebElementTagNameEnum tagType = targetInsert.getTagType();
        if (checkForceEnterText.isSelected() && tagType.equals(WebElementTagNameEnum.INPUT)) {
            tagType = WebElementTagNameEnum.INPUT_ENTER;
        }

        Integer currentBotJobId = currentBotJob.getId();

        InstructionLoad instruction =
                performActions.buildNewInstruction(tagType, actionReq, false, nextInstOrderNumber, targetInsert);

        instruction.setForceCoordinates(true); // default
        instruction.setCoordinates(targetInsert.getCoordinates());
        instruction.setIFrameXPath(targetInsert.getIFrameXPath());
        instruction.setShadowHost(targetInsert.getShadowHost());
        instruction.setShadowRoot(targetInsert.getShadowRoot());
        instruction.setCssSelector(targetInsert.getCssSelector());
        instruction.setBlockId(currentBlockId);
        instruction.setBotJobId(currentBotJobId);
        instruction.setName(targetInsert.getDefinedName());

        if (instruction.getName() == null && targetInsert.getNameLabel() == null) {
            if (targetInsert.getSomeText() != null) {
                instruction.setName(targetInsert.getSomeText());
            } else {
                instruction.setName(targetInsert.getTagName());
            }
        } else if (instruction.getName() == null && targetInsert.getNameLabel() != null) {
            instruction.setName(targetInsert.getNameLabel());
        }

        // Fix action string
        String actions = instruction.getActions();
        String[] parts = actions.split(",");
        if (actions.startsWith("I:")) {
            for (int i = 0; i < parts.length; i++) {
                parts[i] = parts[i].trim();
                if (parts[i].startsWith("I:")) {
                    parts[i] = parts[i].contains(":E:")
                            ? "I:E:" + targetInsert.getDefinedName()
                            : "I:" + targetInsert.getDefinedName();
                    break;
                }
            }
            instruction.setActions(parts[0]);
        }

        // Set references
        List<ReferenceLoadDTO> referenceList = new ArrayList<>();
        for (Map.Entry<String, String> entry : targetInsert.getSavedReferences().entrySet()) {
            ReferenceLoadDTO reference = new ReferenceLoadDTO();
            reference.setReferenceType(entry.getKey());
            reference.setValue(entry.getValue());
            reference.setBotJobId(currentBotJobId);
            referenceList.add(reference);
        }

        instruction.setReferenceLoadDTOList(referenceList);
        instructionList.add(instruction);
    }

    public void testingActions(TargetElement originTarget, String testType) {
        WebDriver driverTestActions = performActions.getCurrentDriver();

        TargetElement targetDeepCopy = originTarget.deepCopy();
        try {

            if (targetDeepCopy.getElement() == null) {
                if (!Strings.isNullOrEmpty(targetDeepCopy.getShadowHost())
                        && !Strings.isNullOrEmpty(targetDeepCopy.getCssSelector())) {
                    WebElement elementFound = performActions.findShadowElementByCssSelector(
                            targetDeepCopy.getShadowHost(), targetDeepCopy.getCssSelector());

                    targetDeepCopy.setElement(elementFound);
                }
            }

            if (targetDeepCopy.getElement() != null) {

                //                            arWebDriver.dehighlightElement(targetDeepCopy.getElement());

                //                            WebElement elementXPath =
                //
                // performActions.getCurrentDriver().findElement(By.xpath(arWebElement.getTargetElement().getXPath()));
                //                            if (elementXPath != null) {
                //                                elementXPath.click();
                //                            }

                FieldData fieldData = new FieldData("Test", testActionsField.getText());

                String mainCoordenates = targetDeepCopy.getCoordinates();
                String savedCoordenates = targetDeepCopy.getSavedReferences().get("coordinates");
                if (Strings.isNullOrEmpty(mainCoordenates)) {
                    mainCoordenates = targetDeepCopy.getCoordinates();
                }

                if (Strings.isNullOrEmpty(savedCoordenates)) {
                    savedCoordenates = mainCoordenates;
                }

                String mainCoordinates = targetDeepCopy.getCoordinates();
                //                String savedCoordinates = targetDeepCopy.getSavedReferences().get("coordinates");

                if (Strings.isNullOrEmpty(mainCoordinates)) {
                    mainCoordinates = targetDeepCopy.getCoordinates();
                }

                //                if (Strings.isNullOrEmpty(savedCoordinates)) {
                //                    savedCoordinates = mainCoordinates;
                //                }

                List<String> coordinatesList = new ArrayList<>();
                if (!Strings.isNullOrEmpty(mainCoordinates)) {
                    coordinatesList.add(mainCoordinates);
                }
                //                if (!Strings.isNullOrEmpty(savedCoordinates) &&
                // !savedCoordinates.equals(mainCoordinates)) {
                //                    coordinatesList.add(savedCoordinates);
                //                }

                String[] coordinates = coordinatesList.toArray(new String[0]);

                //                            if (checkTestCoordinates.isSelected()) {
                //                                performActions.executeActionsAtCoordinates(
                //                                        coordinates[1], fieldData, ARConstants.VISUALIZE,
                // false);
                //                                performActions.executeActionsAtCoordinates(
                //                                        coordinates[0], fieldData, ARConstants.VISUALIZE,
                // false);
                //
                //                                performActions.executeActionsAtCoordinates(
                //                                        coordinates[1], fieldData, ARConstants.CLICK,
                // false);
                //                                performActions.executeActionsAtCoordinates(
                //                                        coordinates[0], fieldData, ARConstants.CLICK,
                // false);
                //
                //                                performActions.executeActionsAtCoordinates(
                //                                        coordinates[1], fieldData, ARConstants.INSERT,
                // false);
                //                                performActions.executeActionsAtCoordinates(
                //                                        coordinates[0], fieldData, ARConstants.INSERT,
                // false);
                //
                //                                performActions.executeActionsAtCoordinates(
                //                                        coordinates[1], fieldData, ARConstants.INSERT,
                // true);
                //                                performActions.executeActionsAtCoordinates(
                //                                        coordinates[0], fieldData, ARConstants.INSERT,
                // true);
                //
                //                                performActions.moveAndClickAtCoordinates(coordinates[1],
                // performActions.getCurrentDriver());
                //                                performActions.moveAndClickAtCoordinates(coordinates[0],
                // performActions.getCurrentDriver());
                //                            }

                Text actionText1;
                Text actionText2;
                Text actionText3;
                Text actionText4;
                Text actionText5;
                Text actionText6;
                Text actionText7;
                Text actionText8;
                Text actionText9;
                Text actionText10;
                Text actionText11;
                Text actionText12;
                Text actionText13;

                StringBuilder actionsTested = new StringBuilder();
                actionsTested.append("Actions Tested:" + System.lineSeparator());

                actionText1 = new Text("Actions Tested:");
                actionText1.setStyle("-fx-font-size: 12px; -fx-fill: blue;");

                if (!Strings.isNullOrEmpty(targetDeepCopy.getIFrameXPath())) {
                    try {
                        // Locate and switch to the iframe first
                        WebElement iframe = driverTestActions.findElement(By.xpath(targetDeepCopy.getIFrameXPath()));
                        driverTestActions.switchTo().frame(iframe);

                        logOperations.info("Found iFrame XPath: " + targetDeepCopy.getIFrameXPath());
                    } catch (Exception e) {
                        logOperations.info("iFrame Not Found with XPath: " + targetDeepCopy.getIFrameXPath());
                        //                performMessage.generalErrorIFrame(currentInstruction.getName());
                        //                        return null;
                    }
                }

                String result = performActions.sequenceOfCommands(
                        targetDeepCopy.getElement(),
                        ARConstants.SELECT,
                        coordinates,
                        fieldData,
                        driverTestActions,
                        false);
                logOperations.info(result);
                actionsTested.append(result + System.lineSeparator());
                actionText2 = new Text(result);
                if (result.contains("Failed")) {
                    actionText2.setStyle("-fx-font-size: 12px; -fx-fill: red;");
                } else {
                    actionText2.setStyle("-fx-font-size: 12px; -fx-fill: green;");
                }

                if (testType.equals("TEST_CLICK_DTO")) {
                    result = performActions.sequenceOfCommands(
                            targetDeepCopy.getElement(),
                            ARConstants.CLICK,
                            coordinates,
                            fieldData,
                            driverTestActions,
                            false);
                    logOperations.info(result);
                    actionsTested.append(result + System.lineSeparator());
                    actionText3 = new Text(result);
                    if (result.contains("Failed")) {
                        actionText3.setStyle("-fx-font-size: 12px; -fx-fill: red;");
                    } else {
                        actionText3.setStyle("-fx-font-size: 12px; -fx-fill: green;");
                    }
                }
                //                result = performActions.sequenceOfCommands(
                //                        targetDeepCopy.getElement(),
                //                        ARConstants.GET_VALUE,
                //                        coordinates,
                //                        fieldData,
                //                        driverTestActions,
                //                        false);
                //                logOperations.info(result);
                //                actionsTested.append(result + System.lineSeparator());
                //                actionText4 = new Text(result);
                //                if (result.contains("Failed")) {
                //                    actionText4.setStyle("-fx-font-size: 12px; -fx-fill: red;");
                //                } else {
                //                    actionText4.setStyle("-fx-font-size: 12px; -fx-fill: green;");
                //                }

                if (testType.equals("TEST_INPUT_DTO")) {
                    result = performActions.sequenceOfCommands(
                            targetDeepCopy.getElement(),
                            ARConstants.CLICK,
                            coordinates,
                            fieldData,
                            driverTestActions,
                            false);
                    logOperations.info(result);
                    actionsTested.append(result + System.lineSeparator());
                    actionText3 = new Text(result);
                    if (result.contains("Failed")) {
                        actionText3.setStyle("-fx-font-size: 12px; -fx-fill: red;");
                    } else {
                        actionText3.setStyle("-fx-font-size: 12px; -fx-fill: green;");
                    }
                    performActions.onHoldInSeconds(1);

                    result = performActions.sequenceOfCommands(
                            targetDeepCopy.getElement(),
                            ARConstants.CLEAR,
                            coordinates,
                            fieldData,
                            driverTestActions,
                            false);
                    logOperations.info(result);
                    actionsTested.append(result + System.lineSeparator());
                    actionText5 = new Text(result);
                    if (result.contains("Failed")) {
                        actionText5.setStyle("-fx-font-size: 12px; -fx-fill: red;");
                    } else {
                        actionText5.setStyle("-fx-font-size: 12px; -fx-fill: green;");
                    }

                    result = performActions.sequenceOfCommands(
                            targetDeepCopy.getElement(),
                            ARConstants.INSERT,
                            coordinates,
                            fieldData,
                            driverTestActions,
                            false);
                    logOperations.info(result);
                    actionsTested.append(result + System.lineSeparator());
                    actionText6 = new Text(result);
                    if (result.contains("Failed")) {
                        actionText6.setStyle("-fx-font-size: 12px; -fx-fill: red;");
                    } else {
                        actionText6.setStyle("-fx-font-size: 12px; -fx-fill: green;");
                    }

                    performActions.onHoldInSeconds(1);
                    result = performActions.sequenceOfCommands(
                            targetDeepCopy.getElement(),
                            ARConstants.CLEAR,
                            coordinates,
                            fieldData,
                            driverTestActions,
                            false);
                    logOperations.info(result);
                    actionsTested.append(result + System.lineSeparator());
                    actionText5 = new Text(result);
                    if (result.contains("Failed")) {
                        actionText5.setStyle("-fx-font-size: 12px; -fx-fill: red;");
                    } else {
                        actionText5.setStyle("-fx-font-size: 12px; -fx-fill: green;");
                    }

                    result = performActions.sequenceOfCommands(
                            targetDeepCopy.getElement(),
                            ARConstants.COORD_CLICK,
                            coordinates,
                            fieldData,
                            driverTestActions,
                            false);
                    logOperations.info(result);
                    actionsTested.append(result + System.lineSeparator());
                    actionText5 = new Text(result);
                    if (result.contains("Failed")) {
                        actionText5.setStyle("-fx-font-size: 12px; -fx-fill: red;");
                    } else {
                        actionText5.setStyle("-fx-font-size: 12px; -fx-fill: green;");
                    }

                    result = performActions.sequenceOfCommands(
                            targetDeepCopy.getElement(),
                            ARConstants.COORD_INSERT,
                            coordinates,
                            fieldData,
                            driverTestActions,
                            false);
                    logOperations.info(result);
                    actionsTested.append(result + System.lineSeparator());
                    actionText5 = new Text(result);
                    if (result.contains("Failed")) {
                        actionText5.setStyle("-fx-font-size: 12px; -fx-fill: red;");
                    } else {
                        actionText5.setStyle("-fx-font-size: 12px; -fx-fill: green;");
                    }

                    performActions.onHoldInSeconds(1);

                    result = performActions.sequenceOfCommands(
                            targetDeepCopy.getElement(),
                            ARConstants.CLEAR,
                            coordinates,
                            fieldData,
                            driverTestActions,
                            false);
                    logOperations.info(result);
                    actionsTested.append(result + System.lineSeparator());
                    actionText5 = new Text(result);
                    if (result.contains("Failed")) {
                        actionText5.setStyle("-fx-font-size: 12px; -fx-fill: red;");
                    } else {
                        actionText5.setStyle("-fx-font-size: 12px; -fx-fill: green;");
                    }
                }

                //                result = performActions.sequenceOfCommands(
                //                        targetDeepCopy.getElement(), ARConstants.FOCUS, coordinates, fieldData,
                // driverTestActions, false);
                //                logOperations.info(result);
                //
                //                actionsTested.append(result + System.lineSeparator());
                //
                //                actionText7 = new Text(result);
                //                if (result.contains("Failed")) {
                //                    actionText7.setStyle("-fx-font-size: 12px; -fx-fill: red;");
                //                } else {
                //                    actionText7.setStyle("-fx-font-size: 12px; -fx-fill: green;");
                //                }
                //
                //                result = performActions.sequenceOfCommands(
                //                        targetDeepCopy.getElement(), ARConstants.TAB, coordinates, fieldData,
                // driverTestActions, false);
                //                logOperations.info(result);
                //
                //                actionsTested.append(result + System.lineSeparator());
                //
                //                actionText8 = new Text(result);
                //                if (result.contains("Failed")) {
                //                    actionText8.setStyle("-fx-font-size: 12px; -fx-fill: red;");
                //                } else {
                //                    actionText8.setStyle("-fx-font-size: 12px; -fx-fill: green;");
                //                }

                //                result = performActions.sequenceOfCommands(
                //                        targetDeepCopy.getElement(),
                //                        ARConstants.COORD_VISUALIZA,
                //                        coordinates,
                //                        fieldData,
                //                        driverTestActions,
                //                        false);
                //                logOperations.info(result);
                //                actionsTested.append(result + System.lineSeparator());
                //                actionText9 = new Text(result);
                //                if (result.contains("Failed")) {
                //                    actionText9.setStyle("-fx-font-size: 12px; -fx-fill: red;");
                //                } else {
                //                    actionText9.setStyle("-fx-font-size: 12px; -fx-fill: green;");
                //                }

                //                result = performActions.sequenceOfCommands(
                //                        targetDeepCopy.getElement(),
                //                        ARConstants.COORD_CLICK,
                //                        coordinates,
                //                        fieldData,
                //                        driverTestActions,
                //                        false);
                //                logOperations.info(result);
                //
                //                actionsTested.append(result + System.lineSeparator());
                //
                //                actionText10 = new Text(result);
                //                if (result.contains("Failed")) {
                //                    actionText10.setStyle("-fx-font-size: 12px; -fx-fill: red;");
                //                } else {
                //                    actionText10.setStyle("-fx-font-size: 12px; -fx-fill: green;");
                //                }
                //
                //                result = performActions.sequenceOfCommands(
                //                        targetDeepCopy.getElement(),
                //                        ARConstants.COORD_INSERT,
                //                        coordinates,
                //                        fieldData,
                //                        driverTestActions,
                //                        false);
                //                logOperations.info(result);
                //                actionsTested.append(result + System.lineSeparator());
                //                actionText11 = new Text(result);
                //                if (result.contains("Failed")) {
                //                    actionText11.setStyle("-fx-font-size: 12px; -fx-fill: red;");
                //                } else {
                //                    actionText11.setStyle("-fx-font-size: 12px; -fx-fill: green;");
                //                }

                //                result = performActions.sequenceOfCommands(
                //                        targetDeepCopy.getElement(),
                //                        ARConstants.COORD_INSERT,
                //                        coordinates,
                //                        fieldData,
                //                        driverTestActions,
                //                        true);
                //                logOperations.info(result);
                //                actionsTested.append(result + System.lineSeparator());
                //                actionText12 = new Text(result);
                //                if (result.contains("Failed")) {
                //                    actionText12.setStyle("-fx-font-size: 12px; -fx-fill: red;");
                //                } else {
                //                    actionText12.setStyle("-fx-font-size: 12px; -fx-fill: green;");
                //                }

                //                result = performActions.sequenceOfCommands(
                //                        targetDeepCopy.getElement(),
                //                        ARConstants.COORD_MOVE_CLICK_RED,
                //                        coordinates,
                //                        fieldData,
                //                        driverTestActions,
                //                        true);
                //                logOperations.info(result);
                //                actionsTested.append(result + System.lineSeparator());
                //                actionText13 = new Text(result);
                //                if (result.contains("Failed")) {
                //                    actionText13.setStyle("-fx-font-size: 12px; -fx-fill: red;");
                //                } else {
                //                    actionText13.setStyle("-fx-font-size: 12px; -fx-fill: green;");
                //                }
                //
                //                logOperations.info(actionsTested);

                //                VBox vertical = new VBox();
                //                vertical.getChildren()
                //                        .addAll(
                //                                actionText1,
                //                                actionText2,
                //                                actionText3,
                //                                actionText4,
                //                                actionText5,
                //                                actionText6,
                //                                actionText7,
                //                                actionText8,
                //                                actionText9,
                //                                actionText10,
                //                                actionText11,
                //                                actionText12,
                //                                actionText13);

                //                Platform.runLater(() -> {
                //                    textFlowResult.getChildren().clear();
                //                    textFlowResult.getChildren().addAll(vertical);
                //
                //                    textFlowResult.requestLayout();
                //
                //                    //                                boxListViews.requestLayout();
                //                    //                                verticalBox.requestLayout();
                //                    //                                getChildren().addAll(blockAndUrl, boxListViews);
                //                    contentPane.requestLayout();
                //                    VBox vBoxResult = new VBox();
                //                    vBoxResult.getChildren().addAll(textFlowResult);
                //                    performMessage.showAlertCombinedVBOX(
                //                            Alert.AlertType.INFORMATION,
                //                            "Test Actions Results",
                //                            "Web Actions Tested:",
                //                            null,
                //                            vBoxResult);
                //
                //                    //
                // countdownTextField.setText(actionsTested.toString());
                //                    //                                countdownTextField.setStyle("-fx-font-size:
                // 12px;
                //                    // -fx-text-fill: blue;");
                //                });
            }
            //                                arWebElement.getElement().click();
        } catch (Exception e) {
            performMessage.couldNotFindElement("No TagName");
        } finally {
            if (driverTestActions != null) {
                driverTestActions.switchTo().defaultContent();
            }

            Platform.runLater(() -> {
                defineNameField.clear();
                searchAttribValueField.clear();
            });
        }
    }

    public BooleanProperty interceptBotJobProperty() {
        return interceptBotJob;
    }

    public boolean isInterceptBotJob() {
        return interceptBotJob.get();
    }

    public void setInterceptBotJob(boolean value) {
        interceptBotJob.set(value);
    }

    public void initialize(ARWebDriver currentARWebDriver, BotJobLoadDTO botJobLoad, int portSocketInitial) {
        this.portSocketInitial = portSocketInitial;
        this.currentARWebDriver = currentARWebDriver;

        searchHiddenFields = false;

        defaultSearch = new String[] {"input", "textarea", "button", "a", "select", "label"};

        log.info("Calling ARScannedElementPane");

        // Ensure botJob and arPriorities are not null before accessing their methods
        if (this.currentBotJob != null && arPriorities != null) {
            // Check if we need to update arPriorities
            if (arPriorities.getJobId() == null || !arPriorities.getJobId().equals(this.currentBotJob.getId())) {
                // Set Job ID in arPriorities
                arPriorities.setJobId(this.currentBotJob.getId());

                // Check for non-null HomeBanking and Priority
                HomeBankingLoadDTO homeBanking = performLists.getHomeBankingById(botJobLoad.getHomeBankingId());
                if (homeBanking != null) {
                    String priorityValue = homeBanking.getPriority();
                    String searchConfig = homeBanking.getSearchConfig();

                    if (priorityValue != null) {
                        ARPriorities.loadPrioritiesFromString(priorityValue);
                    } else {
                        arPriorities.loadPriorities();
                    }

                    ARPriorities.loadSearchElementsConfig(searchConfig);
                }

                // Initialize performAction with arPriorities and arWebDriver

                performActions.initialize(arPriorities);
                performActions.setCurrentDriver(currentARWebDriver.getCurrentDriver());
            }
        }

        // Assign instance variables
        this.currentBotJob = botJobLoad;
        performActions.initialize(arPriorities);
        performActions.setCurrentDriver(currentARWebDriver.getCurrentDriver());

        if (!openWebDriver(false)) {
            arScannedElementScene.closeWebDrivers();
            arScannedElementScene.closeModal();
            return;
        }

        HomeUrlDTO homeUrlDTO = performLists.getHomeUrlByBankId(
                this.currentBotJob.getHomeBankingId(), this.currentBotJob.getHomeUrlId());

        updateSceneTitleWithCurrentURL(homeUrlDTO.getUrl());

        //        if (!initializeWebView()) {
        //            return;
        //        }
        //        }

        if (comboBoxBlocks != null) {
            List<BlockOptions> listOptions = performLists.loadComboOptions("block", "ScannerPane");
            if (listOptions.isEmpty()) {
                // If list is empty, populate AllBlocks with a default block
                ObservableList<BlockOptions> defaultAll = FXCollections.observableArrayList(
                        new BlockOptions("#1 Default Block", "Default Block", -1, -1, -1));

                comboBoxBlocks.setItems(defaultAll);
                comboBoxBlocks.getSelectionModel().selectFirst();
            }
        }

        if (componentBox != null) {
            //            Platform.runLater(() -> refreshBlocks(false));

            Platform.runLater(() -> refreshGrids());

            componentBox.getChildren().clear();
            componentBox.getChildren().addAll(this.webView);
            //            contentPane.getChildren().clear();
            //            contentPane.getChildren().addAll(topPane, verticalBox);
            componentBox.requestLayout();
            elements2VBox.requestLayout();
            verticalBox.requestLayout();
            mainPane.requestLayout();
        }
    }

    @Override
    public Pane getPaneReference() {
        return mainPane;
    }

    private void refreshGrids() {
        String jsonData = gson.toJson(payloadEmpty);
        webSocketSessionManager.sendMessageJson(
                this.currentBotJob.getHomeBankingId(), "scannerGrid", jsonData, "searchTerms");
    }

    private boolean initializeWebView() {
        setPayloadEmpty();

        webEngine = webView.getEngine();
        webEngine.javaScriptEnabledProperty().set(true);

        String jsonData = gson.toJson(payloadEmpty);

        // sessionIdFromJava
        // (SENDER: scannerTool) -> scannerGrid /  (SENDER: insertTool) -> botJobTasks /
        sessionIdFromJava = "scannerGrid"; // + this.currentBotJob.getHomeBankingId();
        buildWebView(
                webEngine,
                jsonData,
                portSocketInitial,
                sessionIdFromJava,
                this.currentBotJob.getHomeBankingId(),
                this.currentBotJob.getId(),
                this.currentBotJob.getName());

        if (isBrowserClosed(performActions.getCurrentDriver()) && performActions.getCurrentDriver() != null) {
            performActions.getCurrentDriver().quit();
            performActions.setCurrentDriver(null);
            currentARWebDriver.getCurrentDriver().quit();
            currentARWebDriver.setCurrentDriver(null);
        }

        String version = System.getProperty("java.version");
        log.info("Detected Java Version: " + version);

        int majorVersion = getMajorJavaVersion(version);
        if (majorVersion >= 17) {
            log.info("✅ Java 17 or higher is installed.");
        } else {
            log.error("Compatibility Issue: Incompatible Java Version");
            performMessage.errorMessage(
                    "Compatibility Issue: Incompatible Java Version",
                    "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Critical: Your Java version is lower than the required 17!</span>",
                    "<span style='color: #2E7D32; font-weight: bold;'>Attempting to execute the Engine with this older version may lead to unexpected behavior or failures.</span>",
                    "<span style='font-style: italic;'>Please upgrade your Java installation to version 17 or higher for optimal performance and stability.</span>",
                    null,
                    0);
        }

        if (!openWebDriver(true)) {
            arScannedElementScene.closeWebDrivers();
            arScannedElementScene.closeModal();
            return false;
        }
        // "scannerTool", "scannerGrid", "searchTerms"
        //        performPreLoad.dynamicLoadElementsDTO(
        //                performActions.getCurrentDriver(),
        //                performActions.getCurrentDriver().getCurrentUrl(),
        //                defaultSearch,
        //                searchHiddenFields,
        //                portSocketInitial,
        //                "scannerTool",
        //                "scannerGrid",
        //                "searchTerms");

        //        Platform.runLater(() -> {
        //            performCloseBrowser.dynamicCloseBrowser(
        //                    performActions.getCurrentDriver(),
        //                    portSocketInitial,
        //                    "closeBrowser",
        //                    "scannerGrid",
        //                    "closeBrowser",
        //                    this.currentBotJob.getHomeBankingId(),
        //                    homeBanking.getUrl());
        //        });

        performActions.getIframeElementsMap();

        handleWindowHandlesChange();

        return true;
    }

    //    private static WebElement convertJsoupElementToWebElement(Element jsoupElement, WebDriver driver) {
    //        // Create a new RemoteWebElement instance and set its properties
    //        RemoteWebElement webElement = new RemoteWebElement();
    //        webElement.setParent((RemoteWebElement) driver.findElementByTagName("html")); // Set a dummy parent
    //        webElement.setId("dummy_id"); // Set a dummy id
    //        // Simulate the href and text attributes
    //        webElement.setAttribute("href", jsoupElement.attr("href"));
    //        webElement.setText(jsoupElement.text());
    //
    //        return webElement;
    //    }

    private boolean openWebDriver(boolean firstLoad) {

        String webDriverPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_WEBDRIVER);
        if (!(new File(webDriverPath)).exists()) {
            logOperations.error("Action Required: Missing WebDriver");
            performMessage.errorMessage(
                    "Action Required: Missing WebDriver",
                    "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Critical: The WebDriver file is missing!</span>",
                    "<span style='color: #2E7D32; font-weight: bold;'>To execute automated browser interactions, the WebDriver is absolutely essential.</span>",
                    "<span style='font-style: italic;'>Please download the correct WebDriver for your browser and ensure it is accessible by the application.</span>",
                    null,
                    0);
            return false;
        }
        String browserType = arPropertyManager.getProperty(ARPropertyEnum.BROWSER);

        if (!firstLoad
                && isBrowserClosed(performActions.getCurrentDriver())
                && performActions.getCurrentDriver() != null) {
            performActions.getCurrentDriver().quit();
            performActions.setCurrentDriver(null);
            currentARWebDriver.getCurrentDriver().quit();
            currentARWebDriver.setCurrentDriver(null);
            firstLoad = true;
        }

        if (firstLoad) {
            HomeUrlDTO homeUrlDTO = performLists.getHomeUrlByBankId(
                    this.currentBotJob.getHomeBankingId(), this.currentBotJob.getHomeUrlId());
            HomeBankingLoadDTO homeBanking = performLists.getHomeBankingById(this.currentBotJob.getHomeBankingId());

            WebDriver returned = currentARWebDriver.openDriver(
                    browserType,
                    webDriverPath,
                    homeUrlDTO.getUrl(),
                    homeBanking.getOptionsConfig(),
                    defaultSearch,
                    searchHiddenFields,
                    portSocketInitial);

            if (returned == null) {
                return false;
            }

            performActions.initialize(arPriorities);
            performActions.setCurrentDriver(currentARWebDriver.getCurrentDriver());
        } else {

            if (currentARWebDriver.getCurrentDriver() != null) {
                HomeUrlDTO homeUrlDTO = performLists.getHomeUrlByBankId(
                        this.currentBotJob.getHomeBankingId(), this.currentBotJob.getHomeUrlId());
                currentARWebDriver.getCurrentDriver().get(homeUrlDTO.getUrl());
            }
        }

        //        try {
        //            performActions.onHoldInSeconds(3);
        //        } catch (Exception ignore) {
        //        }

        return true;
    }

    @Override
    public void initUIComponents() {

        if (!initializeWebView()) {
            return;
        }

        addCompBoxWebView();

        buildUIComponents();

        refreshBlocks(false);
    }

    //    public void saveReferencesToFile(String filePath, List<ARWebElement> elements) {
    //        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
    //            for (ARWebElement element : elements) {
    //                Map<String, String> savedReferences = element.getSavedReferences();
    //
    //                for (Map.Entry<String, String> entry : savedReferences.entrySet()) {
    //                    writer.write(entry.getKey() + "=" + entry.getValue());
    //                    writer.newLine();
    //                }
    //            }
    //            logOperations.info("References saved to " + filePath);
    //        } catch (IOException e) {
    //            logOperations.error("Error writing to file: " + e.getMessage());
    //        }
    //    }

    private void addCompBoxWebView() {
        componentBox = new HBox(this.webView);

        HBox.setHgrow(this.webView, Priority.ALWAYS);
        VBox.setVgrow(this.webView, Priority.ALWAYS);
    }

    private void buildWebView(
            WebEngine webEngine,
            String jsonData,
            int finalPort,
            String sessionIdFromJava,
            int homeBanking,
            int botJobId,
            String botJobName) {
        webEngine.load(getClass().getResource("/build/index.html").toExternalForm());

        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                // After the page has successfully loaded
                try {
                    webEngine.executeScript("setTimeout(function() { window.receiveDataFromJava(JSON.stringify("
                            + jsonData + "), " + finalPort + ", '" + sessionIdFromJava + "', " + homeBanking + ", "
                            + botJobId + ", '" + botJobName + "' ) }, 1000)");
                } catch (Exception e) {
                    log.error("buildWebView  Error: " + e.getMessage());
                }
            }
        });
    }

    private void buildUIComponents() {
        topPane = builder.createTopPanel(ARConstants.SPACE_L, ARConstants.SPACE_SM);
        mainPane = builder.createContentPanel(ARConstants.SPACE_L, ARConstants.SPACE_XL, ARConstants.SPACE_SM);

        cloneElementsButton = builder.buildButton(
                "Clone", ARConstants.SPACE_L, ARConstants.ICON_TICK, ARConstants.SPACE_SM, new Insets(5));
        pageScannerButton = builder.buildButton(
                "Page Scanner", ARConstants.SPACE_ZERO, "/refresh.png", ARConstants.SPACE_M, new Insets(5.0D));

        turnOnOffButton = new Button("Search Hidden Fields: Off");
        turnOnOffButton.setStyle("-fx-background-color: grey; -fx-text-fill: white;");

        refreshWebPageButton = builder.buildButton(
                "Refresh Web Page", ARConstants.SPACE_ZERO, "/refresh.png", ARConstants.SPACE_M, new Insets(5.0D));

        cleanListButton = builder.buildButton(
                "Clear Grid", // No text
                25.0, // Smaller height
                "/cross.png", // Icon source
                16.0, // Smaller icon size
                new Insets(2.0) // Reduced padding
                );

        testActionLabel = new Label("Test Actions :");

        checkClickElement = new CheckBox("For Click");
        checkInputText = new CheckBox("For Input");
        checkOutputText = new CheckBox("For Output (Excel Export)");

        checkForceEnterText = new CheckBox("With <PRESS ENTER> Action");
        checkForceEnterText.setStyle("-fx-font-size: 12px; -fx-fill: blue;");

        checkForceCoordText = new CheckBox("Force Coordinates");
        checkForceCoordText.setStyle("-fx-font-size: 12px; -fx-fill: blue;");

        iFrameText = new Text("");
        iFrameText.setStyle("-fx-font-size: 12px; -fx-fill: blue;");

        configureButton = builder.buildButton(
                "Config", ARConstants.SPACE_M, ARConstants.ICON_CONFIG, ARConstants.SPACE_M, new Insets(5.0D));

        launchBotJobButton = builder.buildButton(
                "Pre-Launch", ARConstants.SPACE_ZERO, "/play.png", ARConstants.SPACE_M, new Insets(5.0D));
        stopBotJobButton =
                builder.buildButton("STOP", ARConstants.SPACE_ZERO, "/stop.png", ARConstants.SPACE_M, new Insets(5.0D));

        stopBotJobButton.setPrefWidth(100);

        //        textFlowResult = new TextFlow();

        countdownTextField = new TextArea("Pre-Launch status: Ready");
        countdownTextField.setStyle("-fx-font-size: 12px; -fx-text-fill: blue;");
        countdownTextField.setEditable(true);

        checkCloneElement = new CheckBox("PICK ONE ");

        searchTermsLabel = new Label("Search by :");
        defineNameLabel = new Label("DEFINE ELEMENT NAME");
        coordsTextFieldLabel = new Label("Main Coordinates");

        searchTermsField = new TextField();
        searchTermsField.setPromptText("button, label, input, with id, with text");
        searchTermsField.setPrefWidth(300);

        defineNameField = new TextField();
        defineNameField.setPromptText("DEFINE A NAME");

        coordsTextFieldLabel = new Label("Main Coordinates");

        searchAttribValueField = new TextField();
        searchAttribValueField.setPromptText("Search per Attrib");

        coordsTextField = new TextField();
        coordsTextField.setPromptText("Coordinates");

        leftButton = builder.buildButton(
                "Previous", ARConstants.SPACE_M, ARConstants.ICON_LEFT, ARConstants.SPACE_M, new Insets(5.0D));
        rightButton = builder.buildButton(
                "Next", ARConstants.SPACE_M, ARConstants.ICON_RIGHT, ARConstants.SPACE_M, new Insets(5.0D));
        searchButton = builder.buildButton(
                "", ARConstants.SPACE_M, ARConstants.ICON_SEARCH, ARConstants.SPACE_M, new Insets(5.0D));

        leftButton.setDisable(true);
        rightButton.setDisable(true);

        leftButton.setOnAction(e -> switchToLeftTab());
        rightButton.setOnAction(e -> switchToRightTab());

        refreshWebPageButton.setOnAction(e -> {
            if (!lastBrowserTab()) {
                return;
            }

            performActions.refreshPage();

            try {
                performActions.onHoldInSeconds(2);
            } catch (Exception ignore) {

            }

            //            Platform.runLater(() -> {
            //                performCloseBrowser.dynamicCloseBrowser(
            //                        performActions.getCurrentDriver(),
            //                        portSocketInitial,
            //                        "closeBrowser",
            //                        "scannerGrid",
            //                        "closeBrowser",
            //                        this.currentBotJob.getHomeBankingId(),
            //                        homeBanking.getUrl());
            //            });
        });

        cleanListButton.setOnAction(e -> {
            if (webEngine != null) {
                //                webEngine.reload();

                var processDTO = new SplitDTO();
                processDTO.setHomeBankingId(this.currentBotJob.getHomeBankingId());
                processDTO.setBotJobId(this.currentBotJob.getId());
                processDTO.setBotJobName(this.currentBotJob.getName());
                processDTO.setSessionId("scannerGrid"); // + this.currentBotJob.getHomeBankingId());
                processDTO.setOperationId("searchTerms");
                processDTO.setElementDetails(new ElementDTO[0]);
                webSocketSessionManager.sendMessageJson(
                        this.currentBotJob.getHomeBankingId(), "scannerGrid", gson.toJson(processDTO), "searchTerms");

                Platform.runLater(() -> {
                    countdownTextField.setText("Pre-Launch status: Ready");
                });
            }
        });

        currentURL = new Text("");
        currentURL.setFill(Color.BLUE);
        currentURL.setStyle("-fx-font-size: 16px;");

        HomeUrlDTO homeUrlDTO = performLists.getHomeUrlByBankId(
                this.currentBotJob.getHomeBankingId(), this.currentBotJob.getHomeUrlId());
        updateSceneTitleWithCurrentURL(homeUrlDTO.getUrl());

        //        loadAllBlockItems(performLists.getListBlock());

        refreshBlocksButton = createPathButton();

        refreshBlocksButton.setOnMouseClicked(e -> {
            refreshBlocks(false);
        });

        comboBoxBlocks = new ComboBox<>();
        comboBoxBlocks.setPrefWidth(comboWidth);
        comboBoxBlocks.getSelectionModel().selectFirst();
        comboBoxBlocks.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(BlockOptions item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item.getText());
                    setTextFill(Color.BLACK); // Ensure text is black
                }
            }
        });
        comboBoxBlocks.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(BlockOptions item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item.getText());
                    setTextFill(Color.BLACK); // Ensure text is black
                }

                // Add hover effect
                setOnMouseEntered(e -> setStyle("-fx-background-color: lightgray;"));
                setOnMouseExited(e -> setStyle("-fx-background-color: none;"));
            }
        });

        try {
            // Starting the View

            // Create a GridPane for the top section
            GridPane gridPaneTop = new GridPane();
            gridPaneTop.setPadding(new Insets(10));
            gridPaneTop.setHgap(10); // Set horizontal gap between columns

            // Add buttons and checkbox to the GridPane
            gridPaneTop.add(pageScannerButton, 0, 0);
            gridPaneTop.add(searchTermsLabel, 3, 0);
            gridPaneTop.add(searchTermsField, 4, 0);
            gridPaneTop.add(searchButton, 5, 0);
            gridPaneTop.add(turnOnOffButton, 6, 0);
            gridPaneTop.add(leftButton, 7, 0);
            gridPaneTop.add(rightButton, 8, 0);

            VBox vBoxCheckBox = new VBox();
            vBoxCheckBox
                    .getChildren()
                    .addAll(
                            createSpacerVert(),
                            checkClickElement,
                            checkInputText,
                            checkOutputText,
                            createCustomSeparator(Color.DARKBLUE, 2),
                            checkForceEnterText,
                            checkForceCoordText,
                            iFrameText);
            vBoxCheckBox.setSpacing(6); // Adjust spacing between CheckBoxes

            topPane.getChildren().add(gridPaneTop); // Add gridPaneTop to topPane

            verticalBox = new VBox();
            verticalBox.setSpacing(10);
            verticalBox.setPadding(new Insets(10));
            VBox.setVgrow(verticalBox, Priority.ALWAYS);

            // Create an HBox to hold launchBotJobButton and stopBotJobButton
            HBox hBoxLaunchButon = new HBox();
            hBoxLaunchButon.setSpacing(10); // Optional: adjust spacing between buttons

            // Add buttons to the HBox
            hBoxLaunchButon.getChildren().addAll(launchBotJobButton, stopBotJobButton);

            HBox boxName = new HBox();
            boxName.setSpacing(5);

            // Ensure the text field expands and takes all available space
            HBox.setHgrow(defineNameField, Priority.ALWAYS);
            defineNameField.setMaxWidth(Double.MAX_VALUE); // Allows full width usage

            // Ensure the button has a reasonable width
            cloneElementsButton.setMinWidth(50); // Adjust as needed

            boxName.getChildren().addAll(defineNameField, cloneElementsButton);

            HBox boxActions = new HBox();
            boxActions.setSpacing(5);

            testActionLabel.setMinWidth(100);

            testActionsField = new TextField("0001");

            HBox.setHgrow(testActionsField, Priority.ALWAYS);
            testActionsField.setMaxWidth(Double.MAX_VALUE); // Ensures full width usage

            boxActions.getChildren().addAll(testActionLabel, testActionsField);

            HBox boxCoordinates = new HBox();
            boxCoordinates.setSpacing(5);

            // Ensure the label has a reasonable width
            coordsTextFieldLabel.setMinWidth(120);

            // Allow the TextField to take up the remaining space
            HBox.setHgrow(coordsTextField, Priority.ALWAYS);
            coordsTextField.setMaxWidth(Double.MAX_VALUE); // Ensures full width usage

            boxCoordinates.getChildren().addAll(coordsTextFieldLabel, coordsTextField);

            HBox hBoxPickClone = new HBox();
            hBoxPickClone.getChildren().addAll(createSpacerHoriz(), checkCloneElement, createSpacerHoriz());

            // Create the VBox for TextFields
            textFieldVBox = new VBox();
            textFieldVBox.setSpacing(6); // Adjust spacing between TextFields
            textFieldVBox
                    .getChildren()
                    .addAll(
                            hBoxPickClone,
                            defineNameLabel,
                            boxName,
                            vBoxCheckBox,
                            createCustomSeparator(Color.DARKBLUE, 2),
                            createSpacerVert(),
                            countdownTextField,
                            boxActions,
                            boxCoordinates,
                            createSpacerVert(),
                            createCustomSeparator(Color.DARKBLUE, 2),
                            hBoxLaunchButon,
                            configureButton);

            // Bind button widths to VBox width
            boxActions.maxWidthProperty().bind(textFieldVBox.widthProperty());

            // Bind button widths to VBox width
            cloneElementsButton.maxWidthProperty().bind(textFieldVBox.widthProperty());
            // Bind the widths of the buttons to percentages of the HBox width
            countdownTextField.maxWidthProperty().bind(textFieldVBox.widthProperty());
            configureButton.maxWidthProperty().bind(textFieldVBox.widthProperty());

            // Fix the widths to 70% and 30% of the HBox width
            hBoxLaunchButon.widthProperty().addListener((obs, oldVal, newVal) -> {
                double totalWidth = newVal.doubleValue();
                launchBotJobButton.setMaxWidth(totalWidth * 0.6);
                stopBotJobButton.setMaxWidth(totalWidth * 0.7);
            });

            HBox boxListViews = new HBox();

            // Bind the height of ListViews to the height of the HBox
            componentBox.prefHeightProperty().bind(boxListViews.heightProperty());

            boxListViews.setSpacing(5);

            HBox.setHgrow(componentBox, Priority.ALWAYS);

            StackPane stackCurrentURL = new StackPane();
            stackCurrentURL.getChildren().add(currentURL);
            stackCurrentURL.setAlignment(Pos.CENTER);
            HBox currentURLBox = new HBox(stackCurrentURL);

            Label labelOthers = new Label("Web Elements Found");
            StackPane stackLabelOthers = new StackPane();
            HBox othersBox = new HBox();
            createSpacerHoriz();
            othersBox
                    .getChildren()
                    .addAll(
                            labelOthers,
                            createSpacerHoriz(),
                            refreshWebPageButton,
                            createSpacerHoriz(),
                            cleanListButton);
            stackLabelOthers.getChildren().addAll(othersBox);

            stackLabelOthers.setAlignment(Pos.CENTER);
            elements2VBox = new VBox(stackLabelOthers, componentBox);
            HBox.setHgrow(elements2VBox, Priority.ALWAYS);
            boxListViews.getChildren().addAll(elements2VBox, textFieldVBox);

            VBox.setVgrow(boxListViews, Priority.ALWAYS);
            HBox.setHgrow(boxListViews, Priority.ALWAYS);

            HBox blockAndUrl = new HBox();
            blockAndUrl.setSpacing(0); // No spacing, use margins instead
            HBox.setMargin(comboBoxBlocks, new Insets(0, 3, 0, 0)); // Right margin of 3 pixels
            HBox.setMargin(refreshBlocksButton, new Insets(0, 3, 0, 0)); // Right margin of 3 pixels
            blockAndUrl.getChildren().addAll(comboBoxBlocks, refreshBlocksButton, currentURLBox);

            verticalBox.getChildren().addAll(topPane, blockAndUrl, boxListViews);
            VBox.setVgrow(verticalBox, Priority.ALWAYS);

            mainPane.getChildren().addAll(verticalBox);

            AnchorPane.setTopAnchor(verticalBox, 0.0);
            AnchorPane.setBottomAnchor(verticalBox, 0.0);
            AnchorPane.setLeftAnchor(verticalBox, 0.0);
            AnchorPane.setRightAnchor(verticalBox, 0.0);

            AnchorPane.setTopAnchor(topPane, 0.0);
            AnchorPane.setLeftAnchor(topPane, 0.0);
            AnchorPane.setRightAnchor(topPane, 0.0);

        } catch (Exception ex) {
            log.info("Error using Separator line: " + ex);
        }
    }

    public void refreshBlocks(boolean secondItem) {
        if (comboBoxBlocks != null) {
            Platform.runLater(() -> {
                loadAllBlocks();
                if (!secondItem) {
                    comboBoxBlocks.getSelectionModel().selectFirst();
                } else {
                    comboBoxBlocks.getSelectionModel().select(1);
                }
            });
        }
    }

    // Enable or disable the tab switching buttons based on the number of tabs
    private void updateButtonState() {
        // If more than one tab is open
        if (performActions.windowHandlesList.size() > 1) {
            // Disable the left button if we are on the first tab
            //            leftButton.setDisable(currentTabIndex == 0);
            //
            //            // Disable the right button if we are on the last tab
            //            rightButton.setDisable(currentTabIndex == performActions.windowHandlesList.size() - 1);
        } else {
            // Disable both buttons if there's only one tab or no tabs
            leftButton.setDisable(true);
            rightButton.setDisable(true);
        }
    }

    // Switch to the previous tab (left)
    private void switchToLeftTab() {
        if (performActions.getCurrentDriver().getWindowHandles().size() > 1 && performActions.currentTabIndex > 0) {
            // Decrease the index to move to the left
            performActions.currentTabIndex--;

            // Switch to the previous tab
            performActions
                    .getCurrentDriver()
                    .switchTo()
                    .window(performActions.windowHandlesList.get(performActions.currentTabIndex));
            updateSceneTitleWithCurrentURL(performActions.getCurrentDriver().getCurrentUrl());

            // Disable the left button if we are at the first tab
            //            leftButton.setDisable(currentTabIndex == 0);

            // Enable the right button since we're no longer on the last tab
            //            rightButton.setDisable(false);
        }
    }

    // Switch to the next tab (right)
    private void switchToRightTab() {
        if (performActions.getCurrentDriver().getWindowHandles().size() > 1
                && performActions.currentTabIndex < performActions.windowHandlesList.size() - 1) {
            // Increase the index to move to the right
            performActions.currentTabIndex++;

            // Switch to the next tab
            performActions
                    .getCurrentDriver()
                    .switchTo()
                    .window(performActions.windowHandlesList.get(performActions.currentTabIndex));
            updateSceneTitleWithCurrentURL(performActions.getCurrentDriver().getCurrentUrl());

            // Disable the right button if we are at the last tab
            //            rightButton.setDisable(currentTabIndex == performActions.windowHandlesList.size() - 1);

            // Enable the left button since we're no longer on the first tab
            //            leftButton.setDisable(false);
        }
    }

    // Method to handle the scenario where the window handles size changes
    private void handleWindowHandlesChange() {
        Set<String> currentWindowHandles = performActions.getCurrentDriver().getWindowHandles();

        // If the number of window handles has changed
        if (currentWindowHandles.size() != performActions.windowHandlesList.size()) {
            // Update the window handles list with the new handles
            performActions.updateWindowHandlesList();

            // Switch to the last window (most recent tab)
            performActions.currentTabIndex = performActions.windowHandlesList.size() - 1; // The last index in the list
            performActions
                    .getCurrentDriver()
                    .switchTo()
                    .window(performActions.windowHandlesList.get(performActions.currentTabIndex));

            // Update the scene title with the current URL of the last tab
            updateSceneTitleWithCurrentURL(performActions.getCurrentDriver().getCurrentUrl());
        }
    }

    // Assuming you have access to the Stage object
    public void updateSceneTitleWithCurrentURL(String currentUrl) {
        if (currentURL != null) {
            currentURL.setText("Current URL:      " + currentUrl);
        }
    }

    private Node createSpacerVert() {
        // Create a Region as a spacer
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS); // Make spacer expand vertically
        return spacer;
    }

    private Node createSpacerHoriz() {
        // Create a Region as a spacer
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS); // Make spacer expand vertically
        return spacer;
    }

    // Method to create a custom separator with specified color and width
    private Separator createCustomSeparator(Color color, double width) {
        Separator separator = new Separator();
        separator.setOrientation(Orientation.HORIZONTAL);
        separator.setValignment(VPos.CENTER); // Extend the line horizontally
        separator.setPrefHeight(2); // Default height
        separator.setStyle("-fx-background-color: " + color.toString().replace("0x", "#") + ";");
        return separator;
    }

    @Override
    public void initUIBehaviour() {
        interceptBotJobProperty().addListener((obs, oldVal, newVal) -> {
            log.info("interceptBotJob changed from " + oldVal + " to " + newVal);
        });

        configureButton.setOnMouseClicked(e -> arNewHomeBankingScene.show());
        launchBotJobButton.setOnMouseClicked(e -> {
            if (!lastBrowserTab()) {
                return;
            }

            launchBotJobButton.setDisable(true);
            performActions.setInterceptBotJob(false);
            setInterceptBotJob(false);
            isJobRunning.set(false);

            try {
                excelPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_EXCEL);
            } catch (Exception error) {
                log.error("Error Defining Excel or BaseLog File: " + error.getMessage());
            }

            executeSpecificBlock = comboBoxBlocks.getValue().getBlockOrderNumber() < 0
                    ? 0
                    : comboBoxBlocks.getValue().getBlockOrderNumber() - 1; // Start in a specific Block/UseCase

            clearFields();

            ErrorMessage errorMessage = performDBEngine.loadHomeBanking(null);
            if (errorMessage == null)
                errorMessage = performDBEngine.loadHomeUrls(this.currentBotJob.getHomeBankingId());

            if (errorMessage == null) {
                excelDataGoto = performDBEngine.loadExcelGotoBlock(this.currentBotJob.getId(), "instruction");

                if ((!excelDataGoto.isEmpty() && excelDataGoto.get(0).getParentBlockId() == null)
                        || (!excelDataGoto.isEmpty() && excelDataGoto.get(0).getParentBlockId() <= 0)) {
                    performDBEngine.fixExcelGoto(
                            "instruction",
                            currentBotJob.getId(),
                            excelDataGoto.get(0).getId(),
                            excelDataGoto.get(0).getBlockId());

                    excelDataGoto = performDBEngine.loadExcelGotoBlock(this.currentBotJob.getId(), "instruction");
                }
            }
            if (errorMessage == null)
                errorMessage = performDataBase.loadAllColumnsExcelWrite("instruction", currentBotJob.getId());

            if (errorMessage == null) errorMessage = performDBEngine.loadCompleteJobs(this.currentBotJob.getId());

            if (errorMessage == null)
                errorMessage = performDBEngine.loadAllVariables("variable", this.currentBotJob.getId());

            if (errorMessage == null && !performLists.getListBotJob().isEmpty()) {
                blocksLoaded = performLists.getListBotJob().get(0).getBlockLoadDTOList();
                errorMessage = performDBEngine.loadAllActionsPerBlock(blocksLoaded);
            } else if (performLists.getListBotJob().isEmpty()) {
                log.warn("I cannot find a Bot Job with this Organization ID: " + this.currentBotJob.getHomeBankingId()
                        + " Environment ID: " + this.currentBotJob.getId());
            }

            if (errorMessage != null) {
                log.error("Error: " + errorMessage.getErrorMessage());
                performMessage.errorMessageOperationFailed(errorMessage);
            }

            if (performLists.getListBotJob().isEmpty()) {
                log.error("Cannot find Bot Jobs with this Id:" + this.currentBotJob.getId());
                return;
            }
            HomeBankingLoadDTO homeBanking = performLists.getHomeBankingById(this.currentBotJob.getHomeBankingId());
            if (homeBanking == null || StringUtils.isNullOrEmpty(homeBanking.getUrl())) {
                log.error("Cannot find Home Banking Environment Id:" + this.currentBotJob.getHomeBankingId());
                return;
            }

            currentBotJob = performLists.getListBotJob().get(0);
            currentBotJob.setHomeBankingLoadDTO(homeBanking);
            HomeUrlDTO homeUrlDTO =
                    performLists.getHomeUrlByBankId(currentBotJob.getHomeBankingId(), currentBotJob.getHomeUrlId());

            if (homeUrlDTO != null) {
                currentBotJob.setHomeUrlId(homeUrlDTO.getId());
                homeBanking.setUrl(homeUrlDTO.getUrl());
            }

            currentBotJobName = currentBotJob.getName();
            excelPath = excelPath + "\\" + currentBotJobName + ".xlsx";

            ExcelReader excelReader = new ExcelReader();
            try {
                extractedData = excelReader.extractData(excelPath, performLists.getAllActions());
            } catch (Exception error) {
                log.error("Error Processing Excel File");
                performMessage.errorMessage(
                        "Error Processing Excel File",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Failed to Execute Excel File!</span> ⚠️",
                        "<span style='color: #E65100; font-weight: bold;'>Please carefully review all Excel columns and their values for potential errors.</span>",
                        "<span style='font-style: italic;'>Inconsistent or incorrect data can prevent the application from processing the file.</span>",
                        null,
                        0);
            }

            if (extractedData.getNumberOfDataRows() == 0) {
                extractedData.addField("$EMPTY");
                extractedData.addFieldValue("$EMPTY", "$EMPTY", 0);
            }

            if (extractedData != null && extractedData.getErrorMessage() != null) {
                performMessage.errorMessage(
                        "Excel Error", "Could Not Execute Excel File", extractedData.getErrorMessage(), null, null, 0);
                return;
            }

            if (extractedData.getNumberOfDataRows() != null
                    && extractedData.getNumberOfDataRows() > 1
                    && excelDataGoto.isEmpty()) {

                log.warn("Multiple Excel Rows Detected: each next row will return to first block");

                ARExecution.DialogModal respModal = performMessage.showCustomModalDialogDragWin11(
                        "Multiple Excel Rows Detected",
                        "<span style='font-weight: bold;'>Your Excel data file contains multiple rows.</span>",
                        "By default, each Excel test row <span style='font-weight: bold; color: #e854c8;'>will be processed through all blocks</span>, and after  will jump back to <span style='font-weight: bold;'>first block (Use Case).</span>",
                        "Add the <span style='font-weight: bold; color: #FF4500;'>'Excel GOTO'</span> operation to your flow to modify the <span style='font-weight: bold;'>default behaviour.</span>",
                        "The <span style='font-weight: bold; color: #FF4500;'>Excel GOTO</span> allows you to specify which block <span style='font-weight: bold;'>the flow should continue from</span>, after the execution of the first row across all blocks.",
                        false,
                        "Continue",
                        "Stop All",
                        0);

                if (respModal.equals(ARExecution.DialogModal.STOP)) {
                    performActions.setInterceptBotJob(true);
                    setInterceptBotJob(true);
                    isJobRunning.set(false);

                    if (!lastBrowserTab()) {
                        return;
                    }
                }
            }

            // Set all instructions' executed field to false
            if (!performLists.getListBotJob().isEmpty()) {

                performLists.getListBotJob().get(0).getBlockLoadDTOList().stream()
                        .flatMap(block -> block.getInstructionLoad().stream())
                        .forEach(instruction -> instruction.setExecuted(false));

                recallJob();
            }
        });

        stopBotJobButton.setOnMouseClicked(e -> {
            launchBotJobButton.setDisable(false);
            performActions.setInterceptBotJob(true);
            setInterceptBotJob(true);
            isJobRunning.set(false);
            executorServicePreLaunch = null;

            if (!lastBrowserTab()) {
                return;
            }
        });

        checkCloneElement.setOnMouseClicked(e -> {
            if (!lastBrowserTab()) {
                return;
            }

            performActions.getCurrentDriver().switchTo().defaultContent();
            targetSelected = null;

            revertCloneInjections(performActions.getCurrentDriver());
            revertHoverPickInjections(performActions.getCurrentDriver());

            if (checkCloneElement.isSelected()) {
                // String[] dataArrayClone = {"*"};
                int finalPort = portSocketInitial;
                String socketSessionId = "scannerTool";
                String destinationId = "scannerGrid"; // + this.currentBotJob.getHomeBankingId();
                Platform.runLater(() -> periodicPickOneCloneThread(
                        performActions.getCurrentDriver(),
                        false,
                        finalPort,
                        socketSessionId,
                        destinationId,
                        "addPickOne",
                        this.currentBotJob.getHomeBankingId(),
                        this.currentBotJob.getId(),
                        performActions.getCurrentDriver().getCurrentUrl()));
            }

            Platform.runLater(() -> {
                launchBotJobButton.setDisable(checkCloneElement.isSelected());

                if (!checkCloneElement.isSelected()) {
                    Platform.runLater(() -> {
                        defineNameField.clear();
                        searchAttribValueField.clear();
                    });
                }
            });
        });

        cloneElementsButton.setOnAction(e -> {
            if (targetSelected != null && targetSelected.getElement() != null) {
                cloneElementDTO(targetSelected);
                Platform.runLater(() -> {
                    defineNameField.clear();
                    searchAttribValueField.clear();
                });
            } else {

                performMessage.showCustomModalDialogDragWin11(
                        "Select a Web Element to Clone",
                        "Click on the row of the Web Element to clone it.",
                        null,
                        null,
                        null,
                        false,
                        "OK",
                        null,
                        0);
            }
        });

        pageScannerButton.setOnAction(e -> searchTermsBtn(null));

        searchButton.setOnAction(e -> searchTermsBtn(searchTermsField.getText().trim()));

        turnOnOffButton.setVisible(false);
    }

    public boolean lastBrowserTab() {
        // Get all window handles (all open tabs/windows)
        try {
            windowHandles = performActions.getCurrentDriver().getWindowHandles();

            // Convert the window handles set to a list
            List<String> windowHandlesList = new ArrayList<>(windowHandles);

            // Switch to the last window (newly opened tab)
            performActions.getCurrentDriver().switchTo().window(windowHandlesList.get(windowHandlesList.size() - 1));

            return true;
        } catch (Exception e) {

            browserNotAttached();

            return false;
        }
    }

    private void cloneElementDTO(TargetElement targetToClone) {

        if (Strings.isNullOrEmpty(defineNameField.getText().trim())) {

            performMessage.showCustomModalDialogDragWin11(
                    "MANDATORY FIELD",
                    "Define the Element Name",
                    "Web Element \"NAME\" must be defined!",
                    null,
                    null,
                    true,
                    "OK",
                    null,
                    0);

            return;
        }

        if (targetToClone != null) {

            ElementDTO elementDTO = performActions.convertTargetToElementDTO(targetToClone);

            elementDTO.setSomeText(defineNameField.getText().trim());

            var processDTO = new SplitDTO();
            processDTO.setHomeBankingId(this.currentBotJob.getHomeBankingId());
            processDTO.setBotJobId(this.currentBotJob.getId());
            processDTO.setBotJobName(this.currentBotJob.getName());
            processDTO.setSessionId("scannerGrid");
            processDTO.setOperationId("clonedElement");

            List<ElementDTO> detailsList = new ArrayList<>();

            if (checkInputText.isSelected()) {
                ElementDTO inputElementDTO = elementDTO.deepCopy(); // Create a copy
                inputElementDTO.setTypeElement(
                        WebElementTagNameEnum.INPUT.getValue().toLowerCase());
                inputElementDTO.setTagName(
                        WebElementTagNameEnum.INPUT.getValue().toLowerCase());
                detailsList.add(inputElementDTO);
            }
            if (checkClickElement.isSelected()) {
                ElementDTO buttonElementDTO = elementDTO.deepCopy(); // Create a copy
                buttonElementDTO.setTypeElement(
                        WebElementTagNameEnum.BUTTON.getValue().toLowerCase());
                buttonElementDTO.setTagName(
                        WebElementTagNameEnum.BUTTON.getValue().toLowerCase());
                detailsList.add(buttonElementDTO);
            }
            if (checkOutputText.isSelected()) {
                ElementDTO outputElementDTO = elementDTO.deepCopy(); // Create a copy
                outputElementDTO.setTypeElement(
                        WebElementTagNameEnum.OUTPUT.getValue().toLowerCase());
                outputElementDTO.setTagName(
                        WebElementTagNameEnum.LABEL.getValue().toLowerCase());
                detailsList.add(outputElementDTO);
            }

            ElementDTO[] detailsArray = detailsList.toArray(new ElementDTO[0]);
            processDTO.setElementDetails(detailsArray);

            for (int x = 0; x < detailsArray.length; x++) {
                detailsArray[x].setTypeElement("tagName-Found");
                detailsArray[x].setId(x + 1);
            }

            webSocketSessionManager.sendMessageJson(
                    this.currentBotJob.getHomeBankingId(), "scannerGrid", gson.toJson(processDTO), "clonedElement");
        }
    }

    public void itPrintsElementDTO() {

        //                textFlowResult.getChildren().clear();
        //                textFlowResult.getChildren().addAll(countdownTextField);
        //                textFlowResult.requestLayout();
        //                contentPane.requestLayout();

        //                                boxListViews.requestLayout();
        //                                verticalBox.requestLayout();
        //                                getChildren().addAll(blockAndUrl, boxListViews);

        //        for (ARWebElement arWebElement : scannedElements2.getItems()) {
        //            performActions.highlightElement(jsExecutor, arWebElement.getElement(), null);
        //        }
        if (targetSelected != null) {
            StringBuilder sb = new StringBuilder();
            String nameDefined = "";

            if (targetSelected.getElement() != null) {

                defineNameField.setText("");
                if (!Strings.isNullOrEmpty(targetSelected.getAttribId())
                        || !Strings.isNullOrEmpty(targetSelected.getAttribName())
                        || !Strings.isNullOrEmpty(targetSelected.getSomeText())) {
                    nameDefined = (!Strings.isNullOrEmpty(targetSelected.getSomeText())
                            ? PerformActions.truncateAndNormalize(targetSelected.getSomeText(), 30)
                            : !Strings.isNullOrEmpty(targetSelected.getAttribId())
                                    ? targetSelected.getAttribId()
                                    : !Strings.isNullOrEmpty(targetSelected.getAttribName())
                                            ? targetSelected.getAttribName()
                                            : "");

                    if (targetSelected.getDefinedName() != null
                            && !targetSelected.getDefinedName().equalsIgnoreCase(nameDefined)) {
                        nameDefined = targetSelected.getDefinedName();
                    }

                    String finalNameDefined = nameDefined;
                    Platform.runLater(
                            () -> defineNameField.setText(PerformActions.truncateAndNormalize(finalNameDefined, 30)));

                } else if (targetSelected.getAttributeData() != null && targetSelected.getAttributeData().length > 0) {

                    // Split by comma to get key-value pairs

                    String idValue = null;
                    String nameValue = null;
                    String typeValue = null;

                    // Loop through each key-value pair
                    for (AttributeData attributeData : targetSelected.getAttributeData()) {

                        String key = attributeData.getName().trim();
                        String value = attributeData.getValue().trim().replaceAll("\"", ""); // Remove quotes

                        if (key.equals("id")) {
                            idValue = value;
                        } else if (key.equals("name")) {
                            nameValue = value;
                        } else if (key.equals("type")) {
                            typeValue = value;
                        }
                    }

                    // Print based on priority: ID -> Name -> Type
                    if (idValue != null) {
                        nameDefined = targetSelected.getTagName() + "-" + idValue;
                    } else if (nameValue != null) {
                        nameDefined = targetSelected.getTagName() + "-" + nameValue;
                    } else if (typeValue != null) {
                        nameDefined = targetSelected.getTagName() + "-" + typeValue;
                    } else {
                        nameDefined = targetSelected.getTagName();
                    }

                    if (targetSelected.getDefinedName() != null
                            && !targetSelected.getDefinedName().equalsIgnoreCase(nameDefined)) {
                        nameDefined = targetSelected.getDefinedName();
                    }

                    String finalSomeText = nameDefined;
                    Platform.runLater(
                            () -> defineNameField.setText(PerformActions.truncateAndNormalize(finalSomeText, 30)));

                } else if (!Strings.isNullOrEmpty(targetSelected.getTagName())) {

                    if (targetSelected.getDefinedName() != null
                            && !targetSelected.getDefinedName().equalsIgnoreCase(nameDefined)) {
                        nameDefined = targetSelected.getDefinedName();
                    } else {
                        nameDefined = targetSelected.getTagName();
                    }
                    String finalSomeText = nameDefined;

                    Platform.runLater(() -> defineNameField.setText(finalSomeText));
                }
            }

            sb.append("TagType: " + targetSelected.getTagType()).append("\n");
            sb.append("ID: " + targetSelected.getAttribId()).append("\n");
            sb.append("Name: " + targetSelected.getAttribName()).append("\n");
            if (!Strings.isNullOrEmpty(targetSelected.getShadowRoot())) {
                sb.append("ShadowHost: " + targetSelected.getShadowHost()).append("\n");
                sb.append("cssSelector: " + targetSelected.getCssSelector()).append("\n");
            }
            sb.append("Text: " + targetSelected.getSomeText()).append("\n");

            if (!Strings.isNullOrEmpty(targetSelected.getCoordinates())) {
                sb.append("Coordinates: " + targetSelected.getCoordinates()).append("\n");
                coordsTextField.setText(targetSelected.getCoordinates());
            } else {
                sb.append("Coordinates: EMPTY").append("\n");
            }

            if (!Strings.isNullOrEmpty(targetSelected.getSearchAttributeValue())) {
                sb.append("Search Attrib: " + targetSelected.getSearchAttributeValue())
                        .append("\n");
                searchAttribValueField.setText(targetSelected.getSearchAttributeValue());
                searchAttribValueField.setStyle("-fx-font-size: 12px; -fx-text-fill: blue;");
            } else {
                sb.append("Search Attrib: No Defined").append("\n");
            }

            sb.append("Named: " + nameDefined).append("\n");
            sb.append("All Attributes Found: ").append("\n");
            if (targetSelected.getAttributeData() != null) {
                for (AttributeData attribute : targetSelected.getAttributeData()) {
                    sb.append("->  ")
                            .append(attribute.getName().trim() + "="
                                    + attribute.getValue().trim())
                            .append("\n");
                }
            }

            Platform.runLater(() -> {
                countdownTextField.setText(sb.toString());
                countdownTextField.setStyle("-fx-font-size: 12px; -fx-text-fill: blue;");
            });

            //                textFlowResult.getChildren().clear();
            //                textFlowResult.getChildren().addAll(countdownTextField);
            //                textFlowResult.requestLayout();
            //                contentPane.requestLayout();

            defineCheckBoxesClickable(targetSelected);
        }
        performActions.getCurrentDriver().switchTo().defaultContent();
    }

    private void periodicPickOneCloneThread(
            WebDriver driver,
            boolean searchHiddenFields,
            int port,
            String sessionId,
            String destination,
            String operationId,
            int homeBankingId,
            int botJobId,
            String currentUrl) {

        ErrorMessage errorMessage = performCloneLoad.dynamicPickOneCloneElementsDTO(
                driver,
                searchHiddenFields,
                port,
                sessionId,
                destination,
                operationId,
                homeBankingId,
                botJobId,
                currentUrl);

        if (errorMessage != null) {
            String[] lines = errorMessage.getErrorMessage().split("\n");

            logOperations.error(
                    "Error: Dynamic Pick One Clone ElementsDTO - {} - {} - {}",
                    errorMessage.getErrorTitle(),
                    errorMessage.getErrorHeader(),
                    errorMessage.getErrorMessage());
            //            performMessage.errorMessage(
            //                    errorMessage.getErrorTitle(),
            //                    errorMessage.getErrorHeader(),
            //                    (!Strings.isNullOrEmpty(lines[0]) ? lines[0] : null),
            //                    (!Strings.isNullOrEmpty(lines[0]) ? lines[1] : null),
            //                    null,
            //                    0);
        }
    }

    public void periodicSearchThread(
            WebDriver driver,
            String[] dataArray,
            int port,
            String sessionId,
            String destinationId,
            String operationId,
            int homeBankingId,
            int botJobId) {
        // "scannerTool", "scannerGrid", "searchTerms"
        ErrorMessage errorMessage = performPreLoad.dynamicLoadElementsDTO(
                driver,
                dataArray,
                searchHiddenFields,
                port,
                sessionId,
                destinationId,
                operationId,
                homeBankingId,
                botJobId);

        if (errorMessage != null) {
            logOperations.error(
                    "Error: Dynamic Pick One Clone ElementsDTO - {} - {} - {}",
                    errorMessage.getErrorTitle(),
                    errorMessage.getErrorHeader(),
                    errorMessage.getErrorMessage());
        }
    }

    public void updateListElements(
            WebDriver driver,
            String[] dataArray,
            int port,
            String sessionId,
            String destinationId,
            String operationId,
            int homeBankingId,
            int botJobId) {
        // "UPDATE_LIST_ELEMENTS", "perform-list-data", "searchTerms"
        ErrorMessage errorMessage = performListElements.dynamicLoadElementsDTO(
                driver,
                dataArray,
                searchHiddenFields,
                port,
                sessionId,
                destinationId,
                operationId,
                homeBankingId,
                botJobId);

        if (errorMessage != null) {
            logOperations.error(
                    "Error: Dynamic Pick One Clone ElementsDTO - {} - {} - {}",
                    errorMessage.getErrorTitle(),
                    errorMessage.getErrorHeader(),
                    errorMessage.getErrorMessage());
        }
    }

    public void revertCloneInjections(WebDriver driver) {
        try {
            jsExecutor = (JavascriptExecutor) driver;
            // Remove the injected element
            jsExecutor.executeScript("window.revertCloneInjections();");
            jsExecutor.executeScript(
                    "let elem = document.getElementById('Martini-Is-Awesome'); if (elem) { elem.remove(); }");

            // Reset the background color
            //        jsExecutor.executeScript("document.body.style.backgroundColor = '';");
        } catch (Exception ignore) {
        }
    }

    public void revertPickInjections(WebDriver driver) {
        try {
            jsExecutor = (JavascriptExecutor) driver;
            // Remove the injected element
            jsExecutor.executeScript("window.revertPickInjections();");
            jsExecutor.executeScript(
                    "let elem = document.getElementById('Martini-Is-Awesome'); if (elem) { elem.remove(); }");

            // Reset the background color
            //        jsExecutor.executeScript("document.body.style.backgroundColor = '';");
        } catch (Exception ignore) {
        }
    }

    private void revertHoverPickInjections(WebDriver driver) {
        try {
            jsExecutor = (JavascriptExecutor) driver;
            jsExecutor.executeScript("window.revertHoverPickInjections();");
        } catch (Exception ignore) {
        }
    }

    public void injectJumpTab(WebDriver driver) {
        ((JavascriptExecutor) driver)
                .executeScript("var inputs = document.getElementsByTagName('input');"
                        + "for (var i = 0; i < inputs.length; i++) {"
                        + "    inputs[i].scrollIntoView();"
                        + "}");
    }

    public List<WebElement> searchAllInputs(WebDriver driver) {
        // Execute JavaScript to find all input elements
        String script = "var inputs = document.getElementsByTagName('input');" + "return inputs;";
        List<WebElement> inputElements = (List<WebElement>) ((JavascriptExecutor) driver).executeScript(script);

        // Print the number of input elements found
        logOperations.info("Number of input elements: " + inputElements.size());
        return inputElements;
    }

    private void recallJob() {
        if (isJobRunning.compareAndSet(false, true)) { // Try to set to true if currently false
            try {
                if (executorServicePreLaunch == null || executorServicePreLaunch.isShutdown()) {
                    executorServicePreLaunch = Executors.newSingleThreadExecutor();
                }

                executorServicePreLaunch.submit(() -> {
                    try {
                        executeJob();
                        launchBotJobButton.setDisable(false);
                    } finally {
                        isJobRunning.set(false);
                    }
                });
            } catch (Exception ignore) {
                // Log the error properly instead of ignoring

                log.error("Error submitting to executorServicePreLaunch: " + ignore.getMessage());
                isJobRunning.set(false); // Ensure flag is reset on submission failure
            }
        } else {
            // Optionally log that a new execution was requested but is already running
            log.info("recallJob() requested, but executeJob() is already running.");

            log.info("recallJob() requested while executeJob() was running.");
        }

        if (performActions.getCurrentDriver().getWindowHandles().size() != performActions.windowHandlesList.size()) {
            performActions.updateWindowHandlesList();
            updateButtonState();
        }
    }

    private void shutDownExecutorService(ExecutorService executorService) {
        if (executorService == null || executorService.isShutdown()) {
            return;
        }
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("ExecutorService did not terminate");
                }
            }
        } catch (InterruptedException error) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
            log.warn("ExecutorService did not terminate: " + error.getMessage());
        }
    }

    private void clearFields() {
        coordsTextField.setText("");
        countdownTextField.setText("Pre-Launch status: Ready");
        countdownTextField.setStyle("-fx-font-size: 12px; -fx-text-fill: blue;");
        mainPane.requestLayout();
    }

    public void quit(int status) {
        performActions.getCurrentDriver().quit();
        if (status == 0) {
            System.exit(status);
        }
        Close();
    }

    /**
     * Finds all elements with the specified attribute and returns a map with their XPaths as keys.
     *
     * @param driver    the WebDriver instance
     * @param attribute the attribute to find elements by (e.g., "id" or "name")
     * @return a map where keys are XPaths of elements and values are WebElements
     */
    private Map<String, WebElement> findElementsWithXPath(WebDriver driver, String attribute) {
        jsExecutor = (JavascriptExecutor) driver;
        List<WebElement> elements = (List<WebElement>)
                jsExecutor.executeScript("return Array.from(document.querySelectorAll('[" + attribute + "]'));");
        Set<WebElement> uniqueElements = new HashSet<>(elements);
        Map<String, WebElement> elementMap = new HashMap<>();
        for (WebElement element : uniqueElements) {
            String xpath = getElementXPath(driver, element);
            elementMap.put(xpath, element);
        }
        return elementMap;
    }

    @Override
    public void start(Stage stage) throws Exception {
        log.error("start from ARScannedElementPane");
    }

    @Override
    public void stop() throws Exception {
        // Cleanup tasks when the application stops
        executorServicePreLaunch.shutdown();
        try {
            if (!executorServicePreLaunch.awaitTermination(5, TimeUnit.SECONDS)) {
                executorServicePreLaunch.shutdownNow();
                if (!executorServicePreLaunch.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("ExecutorService did not terminate");
                }
            }
        } catch (InterruptedException e) {
            executorServicePreLaunch.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void Close() {
        log.info("ARScannedElementPane Close()");
        Platform.runLater(() -> {
            Stage stage = (Stage) mainPane.getScene().getWindow();
            stage.close();
        });
    }

    private Button createPathButton() {
        Button button = builder.buildButton(
                "", ARConstants.SPACE_L, ARConstants.ICON_REFRESH, ARConstants.SPACE_M, new Insets(3D));
        button.setMaxWidth(ARConstants.SPACE_L);
        AnchorPane.setRightAnchor(button, 0D);
        return button;
    }

    private void browserNotAttached() {
        String webDriverPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_WEBDRIVER);
        log.error("Error: The Browser attached with this Web Scanner is Not Active");
        performMessage.errorMessage(
                "The Browser attached with this Web Scanner is Not Active",
                "<span style='font-style: italic;'>Session deleted as the browser has closed the connection!</span>",
                "<span style='color: #E65100; font-weight: bold;'>WebDriver path:</span> <span style='font-weight: bold;'>"
                        + webDriverPath + "</span>",
                "<span style='font-style: italic;'>Please close and Re-Open the Scanner Tool.</span>",
                "<span style='font-style: italic;'>Details: " + "Web Browser was closed before the Scanner Tool"
                        + "</span>",
                0);
    }

    private int handleGreaterThan(String value1, String value2) {
        double num1 = parseValueGreaterThan(clean(value1), true);
        double num2 = parseValueGreaterThan(clean(value2), false);

        return num1 > num2 ? 1 : 0;
    }

    private double parseValueGreaterThan(String value, boolean isValue1) {
        // Handle EMPTY markers
        if (value == null || "$EMPTY".equalsIgnoreCase(value) || "#EMPTY".equalsIgnoreCase(value)) {
            return Double.MIN_VALUE;
        }

        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            if (isValue1) {
                logOperations.warn("Invalid numeric value for value1: " + value);
                return Double.MIN_VALUE;
            } else {
                logOperations.warn("Invalid numeric value for value2: " + value);
                return Double.MAX_VALUE;
            }
        }
    }

    private double parseValueForLessThan(String value, boolean isValue1) {
        // Handle EMPTY markers
        if (value == null || "$EMPTY".equalsIgnoreCase(value) || "#EMPTY".equalsIgnoreCase(value)) {
            return Double.MAX_VALUE;
        }

        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            if (isValue1) {
                logOperations.warn("Invalid numeric value for value1: {}", value);
                return Double.MAX_VALUE;
            } else {
                logOperations.warn("Invalid numeric value for value2: {}", value);
                return Double.MIN_VALUE;
            }
        }
    }

    private int handleLessThan(String value1, String value2) {
        double num1 = parseValueForLessThan(clean(value1), true);
        double num2 = parseValueForLessThan(clean(value2), false);

        return num1 < num2 ? 1 : 0;
    }

    private String finalLogMessage(String failedMessage, String resultActions) {
        if (!Strings.isNullOrEmpty(failedMessage)) {
            return failedMessage + resultActions;
        }
        return resultActions;
    }

    public void checkRunningProcess() {
        checkCloneElement.setSelected(false);
        launchBotJobButton.setDisable(false);
        revertCloneInjections(performActions.getCurrentDriver());
        revertHoverPickInjections(performActions.getCurrentDriver());
        if (isJobRunning.get()) {
            setInterceptBotJob(true);
        }
    }

    private FieldData updateMSGInstruction(FieldData msgInstruction, String failedMessage) {
        String currentKey = msgInstruction.getKey();
        String updatedKey = failedMessage + " - " + currentKey;
        return new FieldData(updatedKey, msgInstruction.getValue());
    }

    public void setPayloadEmpty() {
        if (!performLists.getListBotJob().isEmpty()
                && performLists.getListBlock().isEmpty()) {
            performDataBase.loadBlocks(this.currentBotJob.getId(), "", "block");
        }
        int blockId = -1;
        String blockName = "1# Default Block";
        if (this.currentBotJob.getBlockId() == null
                && !performLists.getListBlock().isEmpty()) {
            blockId = performLists.getListBlock().get(0).getId();
            blockName = performLists.getListBlock().get(0).getName();
        }

        this.payloadEmpty = new PayloadJson(this.currentBotJob.getId(), blockId, blockName, 0);
    }

    private void setPayloadEmpty(String destination) {
        int blockId = -1;
        String blockName = "1# Default Block";
        if (destination.equalsIgnoreCase("botJobTasks")) {
            if (!performLists.getListBotJob().isEmpty()
                    && performLists.getListBlock().isEmpty()) {
                performDataBase.loadBlocks(currentBotJob.getId(), "", "block");
            }
            if (currentBotJob.getBlockId() == null
                    && !performLists.getListBlock().isEmpty()) {
                blockId = performLists.getListBlock().get(0).getId();
                blockName = performLists.getListBlock().get(0).getName();
            }
        } else if (destination.equalsIgnoreCase("componentTasks")) {
            if (!performLists.getListBotJobComp().isEmpty()
                    && performLists.getListBlockComp().isEmpty()) {
                performDataBase.loadBlocks(currentBotJob.getHomeBankingId(), "", "component_block");
            }
            if (currentBotJob.getBlockId() == null
                    && !performLists.getListBlockComp().isEmpty()) {
                blockId = performLists.getListBlockComp().get(0).getId();
                blockName = performLists.getListBlockComp().get(0).getName();
            }
        }
        this.payloadEmpty = new PayloadJson(this.currentBotJob.getId(), blockId, blockName, 0);
    }

    /**
     * Adds or updates a row at the given index using a Map.
     * Columns must already be initialized via setColumns().
     */
    public void addColumnsToRowFromMap(Map<String, String> map, int rowIndex) {

        // Ensure row exists
        while (rowsCSV.size() < rowIndex) {
            rowsCSV.add(new CsvRow());
        }

        CsvRow row = rowsCSV.get(rowIndex);

        // Only set values for known columns
        for (String column : columnsCSV) {
            row.set(column, map.getOrDefault(column, ""));
        }
    }

    /**
     * Adds a row with values matching the columns.
     * Missing values are filled with empty strings.
     *
     * @param values Array of values; may be less than columns.
     */
    //    public void addRow(String... values) {
    //        if (columnsCSV.isEmpty()) {
    //            throw new IllegalStateException("Columns must be initialized before adding a row using values.");
    //        }
    //
    //        List<String> row = new ArrayList<>();
    //        int maxCols = columnsCSV.size();
    //
    //        for (int i = 0; i < maxCols; i++) {
    //            if (i < values.length) {
    //                row.add(values[i]);
    //            } else {
    //                row.add(""); // fill missing with empty string
    //            }
    //        }
    //        rowsCSV.add(row);
    //    }

    //    public void addColumnsToRowFromMap(Map<String, String> map, int rowIndex) {
    //
    //        // Initialize columns once
    //        if (columnsCSV.isEmpty()) {
    //            if (map instanceof LinkedHashMap) {
    //                columnsCSV.addAll(map.keySet());
    //            } else {
    //                List<String> sortedKeys = new ArrayList<>(map.keySet());
    //                Collections.sort(sortedKeys);
    //                columnsCSV.addAll(sortedKeys);
    //            }
    //        }
    //
    //        // Ensure row exists
    //        while (rowsCSV.size() <= rowIndex) {
    //            rowsCSV.add(new LinkedHashMap<>());
    //        }
    //
    //        // Put values directly
    //        rowsCSV.get(rowIndex).putAll(map);
    //    }

    //    /**
    //     * Adds a row using a Map<String, String>. If this is the first row added,
    //     * it sets the column order based on the map's keys.
    //     */
    //    public void addRowFromMap(Map<String, String> map) {
    //        // Initialize column order on first insert
    //        if (columnsCSV.isEmpty()) {
    //            if (map instanceof LinkedHashMap) {
    //                columnsCSV.addAll(map.keySet()); // preserve order
    //            } else {
    //                // Default to alphabetical if insertion order is unknown
    //                List<String> sortedKeys = new ArrayList<>(map.keySet());
    //                Collections.sort(sortedKeys);
    //                columnsCSV.addAll(sortedKeys);
    //            }
    //        }
    //
    //        List<String> row = new ArrayList<>();
    //        for (String column : columnsCSV) {
    //            row.add(map.getOrDefault(column, ""));
    //        }
    //        rowsCSV.add(row);
    //    }

    /**
     * Adds N empty rows. Column order must already be initialized (or will be initialized later).
     */
    //    public void addEmptyRows(int count) {
    //        for (int i = 0; i < count; i++) {
    //            List<String> row = new ArrayList<>(Collections.nCopies(columnsCSV.size(), ""));
    //            rowsCSV.add(row);
    //        }
    //    }

    public String getCsvContent() {
        StringBuilder sb = new StringBuilder();
        sb.append("0: ").append(String.join(",", columnsCSV)).append("\n");

        int rowNumber = 1;
        //        for (List<String> row : rowsCSV) {
        //            sb.append(rowNumber).append(": ").append(String.join(",", row)).append("\n");
        //            rowNumber++;
        //        }
        sb.append(END_OF_FILE_MARKER);
        return sb.toString();
    }

    public String getBancaStatoCsvContent(String delimiter) {
        StringBuilder sb = new StringBuilder();
        sb.append("KEY")
                .append(delimiter)
                .append(String.join(delimiter, columnsCSV))
                .append("\n");

        int xRow = 1;
        //        for (List<String> row : rowsCSV) {
        //            sb.append("EXTERNAL_" + xRow)
        //                    .append(delimiter)
        //                    .append(String.join(delimiter, row))
        //                    .append("\n");
        //            xRow++;
        //        }

        //        sb.append(END_OF_FILE_MARKER);
        return sb.toString();
    }

    private void loadAllBlocks() {
        if (comboBoxBlocks != null) {
            Platform.runLater(() -> {
                comboBoxBlocks.getItems().clear();
                List<BlockOptions> listOptions = performLists.loadComboOptions("block", "ScannerPane");
                comboBoxBlocks.setItems(FXCollections.observableArrayList(listOptions));

                if (!listOptions.isEmpty()) {
                    comboBoxBlocks.getSelectionModel().selectFirst();
                }
            });
        }
    }

    public void printCsv() {
        logOperations.info(getCsvContent());
    }

    // Allow the stage to be set from outside when pane is shown
    public void setStage(Stage stage) {
        this.stage = stage;
    }

    // 🔹 Method to close the window
    public void closePane() {
        if (this.stage != null) {
            Platform.runLater(() -> {
                this.stage.close();
                instance = null; // optional reset for singleton
            });
        }
    }

    private void searchTermsBtn(String searchTerms) {
        //        readAllElementsWithWebDriver();

        if (!lastBrowserTab()) {
            return;
        }

        String[] dataArray;

        //        String[] dataArray = {"with id"};
        //        String[] dataArray = {"with name"};
        //        String[] dataArray = {"with text"};
        //        String[] dataArray = {"button"};
        //        String[] dataArray = {"input"};

        if (searchTerms != null && !searchTerms.trim().isEmpty()) {
            dataArray = searchTerms.split("\\s*,\\s*"); // Splitting by comma, allowing spaces around it
        } else {
            dataArray = new String[] {"input", "textarea", "button", "a", "select", "label"}; // Default values
        }

        handleSearchTermClick(dataArray);

        try {
            Thread.sleep(2000);
            revertSearchTermsInjections(performActions.getCurrentDriver());
        } catch (Exception e) {

        }
    }

    private void handleSearchTermClick(String[] dataArray) {
        //        webElementObservableList1.clear();

        performActions.getCurrentDriver().switchTo().defaultContent();

        xpathTextPrevious = "";
        //        targetSelected = null;

        revertCloneInjections(performActions.getCurrentDriver());
        revertPickInjections(performActions.getCurrentDriver());

        int finalPort = portSocketInitial;
        String socketSessionId = "scannerTool";
        String destinationId = "scannerGrid";

        periodicSearchThread(
                performActions.getCurrentDriver(),
                dataArray,
                finalPort,
                socketSessionId,
                destinationId,
                "searchTerms",
                this.currentBotJob.getHomeBankingId(),
                this.currentBotJob.getId());

        //        Platform.runLater(() -> periodicSearchThread(
        //                performActions.getCurrentDriver(),
        //                performActions.getCurrentDriver().getCurrentUrl(),
        //                dataArray,
        //                finalPort));
    }

    private void revertSearchTermsInjections(WebDriver driver) {
        try {
            jsExecutor = (JavascriptExecutor) driver;
            jsExecutor.executeScript("window.revertSearchInjections();");
        } catch (Exception ignore) {
        }
    }

    public void defineCheckBoxesClickable(TargetElement targetCheck) {
        boolean clickable = isClickable(targetCheck.getElement());

        boolean tagClickable = false;
        // Define regex to extract specific tags (e.g., a, button)
        String regex = "/([^/\\[]+)";
        Pattern pattern = Pattern.compile(regex);

        // Iterate through each attribute in the array
        if (targetCheck.getAttributeData() != null) {

            for (AttributeData attribute : targetCheck.getAttributeData()) {
                // Assuming you want to use the value of the attribute for matching
                String attributeValue = attribute.getValue(); // Get the value of the attribute

                Matcher matcher = pattern.matcher(attributeValue); // Use the value for matching

                // Check for matches in the current attribute value
                while (matcher.find()) {
                    String tag = matcher.group(1);
                    if (tag.equals("a") || tag.equals("button")) {
                        logOperations.info("Found clickable tag: <" + tag + ">");
                        tagClickable = true;
                        break;
                    }
                }
                if (tagClickable) {
                    break; // Exit the loop once a clickable tag is found
                }
            }
        }

        Boolean inputContains = targetCheck.getTagName().toLowerCase().contains("input");

        Boolean selectContains = targetCheck.getTagName().toLowerCase().contains("select");

        if (targetCheck.getCloned() == null) {

            boolean finalTagClickable = tagClickable;
            Platform.runLater(() -> {
                if (finalTagClickable || clickable) {
                    checkClickElement.setSelected(true);
                    checkOutputText.setSelected(false);
                    checkInputText.setSelected(false);

                } else if (inputContains || selectContains) {
                    checkInputText.setSelected(inputContains || selectContains);
                    checkClickElement.setSelected(false);
                    checkOutputText.setSelected(false);

                } else {
                    checkClickElement.setSelected(clickable);
                    checkOutputText.setSelected(!clickable);
                    checkInputText.setSelected(false);
                }
            });
        } else {
            Platform.runLater(() -> {
                if (targetCheck.getTagType().equals(WebElementTagNameEnum.BUTTON)) {
                    checkClickElement.setSelected(true);
                    checkOutputText.setSelected(false);
                    checkInputText.setSelected(false);
                } else if (targetCheck.getTagType().equals(WebElementTagNameEnum.INPUT)) {
                    checkClickElement.setSelected(false);
                    checkOutputText.setSelected(false);
                    checkInputText.setSelected(true);
                } else if (targetCheck.getTagType().equals(WebElementTagNameEnum.OUTPUT)) {
                    checkClickElement.setSelected(false);
                    checkOutputText.setSelected(true);
                    checkInputText.setSelected(false);
                } else {
                    checkClickElement.setSelected(false);
                    checkOutputText.setSelected(false);
                    checkInputText.setSelected(false);
                }
            });
        }
    }

    private boolean isClickable(WebElement element) {
        try {
            List<WebElementTagNameEnum> clickableTags = WebElementTagNameEnum.clickableTags();
            boolean isClickableTag =
                    clickableTags.stream().anyMatch(t -> t.getValue().equals(element.getTagName()));
            List<WebElementAttributeTypeValueEnum> clickableValues =
                    WebElementAttributeTypeValueEnum.getClickableValues();
            boolean isClickableValue = clickableValues.stream()
                    .anyMatch(v -> v.getValue().equals(element.getAttribute(WebElementAttributeEnum.TYPE.getValue())));
            boolean isInputTag = element.getTagName().equals(WebElementTagNameEnum.INPUT.getValue());
            return (isClickableTag && !isInputTag) || (isInputTag && isClickableValue && isClickableTag);

        } catch (Exception ignore) {
        }
        return false;
        // Signal for Force Click or Not from the Target Definitions
    }

    public int createBlockIfNone(String blockTable, int whereId) {

        // It Prevents Start without blocks
        ErrorMessage errorMessage = performDataBase.loadBlocks(whereId, null, blockTable);
        if (errorMessage == null && performLists.getListBlock().isEmpty()) {

            errorMessage =
                    performDataBase.initiateNewBlock(blockTable, whereId, "Default Block", "Default Block", 1, false);

            if (errorMessage == null) {
                if (!performDataBase.getIdsBlockAfter().isEmpty()
                        && performDataBase.getIdsBlockAfter().get(0) > 0) {
                    return performDataBase.getIdsBlockAfter().get(0);
                } else {
                    return -1;
                }
            } else {

                performMessage.errorMessageOperationFailed(errorMessage);
            }
        }
        return -1;
    }

    public enum LocatorType {
        TAGNAME,
        ID,
        CLASSNAME,
        CSSSELECTOR,
        XPATH
    }

    public void updateHasAnyInput() {
        if (blocksLoaded == null) return;

        blocksLoaded.forEach(block -> {
            boolean hasInput = block.getInstructionLoad() != null
                    && block.getInstructionLoad().stream()
                            .anyMatch(instr -> instr.getActions() != null
                                    && instr.getActions().startsWith("I:"));

            block.setHasAnyInput(hasInput);
        });
    }

    private void updateRowStatusAndNotify(String color) {
        rowStatus.setColor(color);
        jsonStatus = gson.toJson(rowStatus);
        webSocketSessionManager.sendMessageJson(
                this.currentBotJob.getHomeBankingId(), sessionRowStatus, jsonStatus, "rowStatus");
    }

    private boolean executeJob() {
        if (PerformActions.waitForPage == null) {
            String updateTimeout = arPropertyManager.getProperty(ARPropertyEnum.WEBDRIVER_PAGE_UPDATE_TIMEOUT_SEC);
            String interactionTimeout = arPropertyManager.getProperty(ARPropertyEnum.WEBDRIVER_PAGE_UPDATE_TIMEOUT_SEC);
            PerformActions.waitForPage = new WebDriverWait(
                    performActions.getCurrentDriver(), Duration.ofSeconds(Integer.parseInt(updateTimeout)));
            PerformActions.waitForAction = new WebDriverWait(
                    performActions.getCurrentDriver(), Duration.ofSeconds(Integer.parseInt(interactionTimeout)));
        }

        Labels.initializeLabelsInSpecLang("en");
        Properties labelsValue = Labels.labelsValue;

        String baseLogString =
                currentBotJobName + ARConstantsEngine.FIELDS_SEPARATOR + labelsValue.getProperty(Labels.START);

        logLaunch.info(baseLogString);

        ExcelWriter.ExcelChain writerReport =
                new ExcelWriter(currentBotJobName, performActions.getCurrentDriver(), false).withPurpose("report");
        writerReport.insertReportHead();

        ExcelWriter.ExcelChain writerExport = null;
        //                new ExcelWriter(blocksLoaded.get(0).getName(),
        // performActions.getCurrentDriver()).withPurpose("export");
        boolean excelExportOnceCreation = true;
        //        writerExport.insertReportHead();

        Set<String> mapIgnore = new HashSet<>();

        String mainMsg = "";
        boolean byPassNotFound = false;
        boolean byPassFlagLoop = false;
        boolean success = true;
        boolean stopAll = false;
        boolean firstRound = true;
        boolean anyFailure = false;
        boolean alreadyLogged = false;
        long botJobStartTime = System.nanoTime();
        long totalExecutionTime = 0;
        String resultActions = "No instruction executed yet";
        String failedMessage = "";
        Map<String, String> dataExcel = null;
        Integer lastBlockOrderPushed = null;
        TargetElement matchScanned = null;
        TargetElement matchXPath = null;
        WebElement webElementFound = null;
        int navTime = getNavigationTimeSeconds();
        String previousExcelFieldName = "";
        String newExcelFieldName = "";

        //        List<InputInfo> inputs = new ArrayList<>();

        sessionRowStatus = "botJobTasks"; // + botJobId;

        variablesLoaded = performLists.getListVariable();
        //        Map<String, String> mapSavedLocators = new HashMap<>();

        Set<Integer> parentIdsForLoop = null;
        Set<Integer> allOutPuts = null;

        Map<String, List<Integer>> mapConditional = new HashMap<>(); // <parentId:Limit Loops> -> <1|5 Times>
        Map<String, Integer> mapLoops = new HashMap<>(); // <parentId:Limit Loops> -> <1|5 Times>
        Map<String, Integer> mapRefresh = new HashMap<>(); // <parentId:Limit Loops> -> <1|5 Times>
        Set<String> loopBlockActive = new HashSet<>();
        Map<String, Integer> loopBlockLimits = new HashMap<>();

        ARExecution.ConditionStatus currentCondition = ARExecution.ConditionStatus.NONE;
        ARExecution.ConditionStatus previousCondition;
        ARExecution.ConditionStatus progressCondition;
        ARExecution.DialogModal respModal = null;

        int exportIndex = 1;
        boolean webElementWork = false;

        if (extractedData.getNumberOfDataRows() > 0) {

            setColumns(performLists.getExcelColumnNames());
            //            addColumnsToRowFromMap(columnsCSV, 1);

            // Execute All Blocks starting from executeSpecificBlock if Defined
            currentBlockOrder = (executeSpecificBlock > -1) ? executeSpecificBlock : 0;
            int blockRecall = currentBlockOrder;
            int blockExcelGoto = blockRecall;

            // BLOCK DEFINED BY "DEFAULT" OR "EXCEL GOTO"
            if (!excelDataGoto.isEmpty() && !blocksLoaded.isEmpty()) {
                Integer parentBlockId =
                        excelDataGoto.get(excelDataGoto.size() - 1).getParentBlockId();
                excelDataGoto
                        .get(0)
                        .setParentBlockId(excelDataGoto.get(0).getBlockId()); // overwrite/fix using block table

                blockExcelGoto = performActions.getBlockOrderNumber(blocksLoaded, parentBlockId) - 1;

                // PREVENTID  LATGER DELETION
                if (blockExcelGoto < 0) {

                    Integer blockOrder = (excelDataGoto != null && !excelDataGoto.isEmpty())
                            ? excelDataGoto.get(0).getBlockOrderNumber()
                            : null;

                    blockExcelGoto = (blockOrder != null && blockOrder > 0) ? blockOrder : 1;
                }
            }

            int xExcelCurrentRow = 0;
            int xExcelDataSize = extractedData.getNumberOfDataRows();
            mapOperators.clear();
            mapExportRows = new LinkedHashMap<>();
            headersExport.clear();
            columnsCSV.clear();
            rowsCSV.clear();

            while (xExcelCurrentRow <= xExcelDataSize - 1 && !blocksLoaded.isEmpty() && !stopAll) {
                // Clear's Up Any Loop as Per New Line
                mapLoops.clear();
                mapRefresh.clear();

                if (firstRound) {
                    firstRound = false;
                    currentBlockOrder = blockRecall; // start blocks from initial for this row
                } else {
                    currentBlockOrder = blockExcelGoto; // start blocks from initial for this row
                }

                blockLoop:
                while (currentBlockOrder <= blocksLoaded.size() - 1 && !blocksLoaded.isEmpty() && !stopAll) {
                    long blockStartTime = System.nanoTime();
                    failedMessage = "";

                    currentCondition = ARExecution.ConditionStatus.NONE;
                    previousCondition = ARExecution.ConditionStatus.NONE;
                    progressCondition = ARExecution.ConditionStatus.NONE;

                    respModal = ARExecution.DialogModal.NONE;

                    int parentBlockCondition = -1;

                    BlockLoadDTO blockLoad = blocksLoaded.get(currentBlockOrder);

                    String blockName = blocksLoaded.get(currentBlockOrder).getName();
                    int blockOrder = blocksLoaded.get(currentBlockOrder).getBlockOrderNumber();
                    String blockReportName = "#" + blockOrder + " " + blockName;

                    int blockWait = blocksLoaded.get(currentBlockOrder).getWait() > 0
                            ? blocksLoaded.get(currentBlockOrder).getWait()
                            : 2;

                    boolean blockActive = blocksLoaded.get(currentBlockOrder).getActive();

                    if (blockActive) {

                        // Fire only when the block CHANGES, and only for ACTIVE blocks
                        if (lastBlockOrderPushed == null || !lastBlockOrderPushed.equals(currentBlockOrder)) {

                            // RESET instruction-level first-load flag
                            firstPageLoadDone = false;

                            performActions.waitPage();
                            lastBlockOrderPushed = currentBlockOrder;

                            performLists.resetListElements();
                            pushUpdateListElements();

                            logOperations.info("Total Target Elements: "
                                    + performLists.getListTargetElements().size());

                            // Inputs-only list with inferred labels
                            //                            inputs.clear();
                            //                            inputs =
                            //
                            //
                            // DomIntrospectionUtil.listAllRelevantElements(performActions.getCurrentDriver());
                        }

                        newExcelFieldName = blockLoad.getExportFile();

                        if (newExcelFieldName != null && !newExcelFieldName.equals(previousExcelFieldName)) {

                            //                            saveExcelWrite(newExcelFieldName, xExcelDataSize,
                            // writerExport, exportIndex);

                            previousExcelFieldName = newExcelFieldName;
                        }

                        if (!Strings.isNullOrEmpty(newExcelFieldName)) {
                            String[] parts = newExcelFieldName.split(":");
                            if (parts.length > 2) {
                                delimiterCSV = parts[2];
                                newExcelFieldName =
                                        newExcelFieldName.replace(":,", "").replace(":|", "");
                            }
                        }
                    }

                    // It Searches the Block That have finished the Loops to Avoid recursivity
                    if (loopBlockActive.size() > 0) {
                        for (String blocLoopKey : loopBlockActive) {
                            if (mapLoops.containsKey(blocLoopKey)) {
                                if (mapLoops.get(blocLoopKey) == 0) {
                                    stopAll = true;
                                    int limit = loopBlockLimits.get(blocLoopKey);

                                    FieldData msgBlock = new FieldData(blocLoopKey, "0");

                                    // Excel Report and Log
                                    performActions.logAndReport(
                                            currentCondition,
                                            true,
                                            true,
                                            blockStartTime,
                                            blockReportName,
                                            success,
                                            new String[] {ARConstantsEngine.GOTO},
                                            msgBlock,
                                            dataExcel,
                                            writerReport,
                                            "GOTO Limit Reached",
                                            blocLoopKey + " Reached: 0");

                                    msgBlock = new FieldData(
                                            String.format("Exit at Block Name: \"%s\"", blockLoad.getName()),
                                            ARConstantsEngine.EXIT);

                                    // Excel Report and Log
                                    performActions.logAndReport(
                                            currentCondition,
                                            true,
                                            true,
                                            blockStartTime,
                                            blockReportName,
                                            success,
                                            new String[] {ARConstantsEngine.EXIT},
                                            msgBlock,
                                            dataExcel,
                                            writerReport,
                                            "Stopping App",
                                            String.format("Exit at Block Name: \"%s\"", blockName));

                                    // performActions.gotoLimitExecution(limit, resultActions);

                                    continue blockLoop;
                                }
                            }
                        }
                    }

                    if (!blockActive) {
                        currentBlockOrder++;

                        FieldData msgBlock = new FieldData(
                                String.format("Ignore: \"%s\"", blockLoad.getName()), ARConstantsEngine.IGNORE);

                        // Excel Report and Log
                        performActions.logAndReport(
                                currentCondition,
                                true,
                                true,
                                blockStartTime,
                                blockReportName,
                                success,
                                new String[] {ARConstantsEngine.IGNORE},
                                msgBlock,
                                dataExcel,
                                writerReport,
                                "BLOCK IGNORED",
                                String.format("Block: \"%s\" is Inactive: ", blockName));

                        continue;
                    }

                    try {

                        FieldData msgBlock = new FieldData(blockLoad.getName(), ARConstantsEngine.EXCEL_BLOCK_HEADER);

                        // Block Header Format
                        performActions.logAndReport(
                                currentCondition,
                                true,
                                false,
                                blockStartTime,
                                blockReportName,
                                success,
                                new String[] {ARConstantsEngine.EXCEL_BLOCK_HEADER},
                                msgBlock,
                                null,
                                writerReport,
                                null,
                                null);

                        performActions.onHoldInSeconds(blockWait);

                        msgBlock = new FieldData(
                                String.format("Default Wait: \"%s\" ->  %d Seconds", blockLoad.getName(), blockWait),
                                ARConstantsEngine.HOLD);

                        // Excel Report and Log
                        performActions.logAndReport(
                                currentCondition,
                                true,
                                true,
                                blockStartTime,
                                blockReportName,
                                success,
                                new String[] {ARConstantsEngine.HOLD},
                                msgBlock,
                                dataExcel,
                                writerReport,
                                "BLOCK DEFAULT WAIT",
                                String.format("Block: \"%s\" Wait %s Seconds: ", blockName, blockWait));

                    } catch (Exception ex) {

                        logOperations.error(String.format("Error Wait Block for :\"%s\"", blockLoad.getName()));
                    }

                    allOutPuts = performActions.getAllOutputsPerBlock(
                            blocksLoaded.get(currentBlockOrder).getInstructionLoad());

                    // Step 1: Get all ParentIds For LOOPs Filter rows where actions = "REFRESH_LOOP" or "LOOP" on
                    // current
                    // Block
                    parentIdsForLoop = performActions.getParentIdsForLoop(
                            blocksLoaded.get(currentBlockOrder).getInstructionLoad());

                    // Step 2: Get all Conditional By parentId for Index Locator on current Block Relocate "IF",
                    // "ELSEIF",
                    // "ELSE", and "ENDIF"
                    mapConditional = performActions.getConditionIndexMapByParentId(blockLoad);

                    // Step 3: Get all Instructions Ids on current Block
                    int[] instructionIds = blockLoad.getInstructionLoad().stream()
                            .mapToInt(InstructionLoad::getId)
                            .toArray();

                    // Step 2: Filter rows where actions = "REFRESH_LOOP" or "LOOP" and collect into the map

                    //                mapLoops = performActions.getLoopAndRefreshLoops(
                    //                        blocksLoaded.get(currentBlockOrder).getBlockLoopInstructionLoadS());

                    //                executionTimes++;
                    boolean jumpGoto = false;
                    boolean jumpLoop = false;
                    boolean jumpGotoError = false;
                    boolean jumpLoopError = false;
                    boolean refreshLoop = false;
                    boolean refreshOnly = false;

                    while (xExcelCurrentRow < extractedData.getNumberOfDataRows() && !stopAll) {
                        failedMessage = "";
                        //                        mapExportRows.clear();

                        //                    writerReport.insertBlockSeparation(blockLoad.getName());

                        dataExcel = extractedData.getRowFieldValues(xExcelCurrentRow);

                        int currentIndex = 0;

                        instructionLoop:
                        while (currentIndex < instructionIds.length && !stopAll) {
                            // Resets the success

                            stopAll = isInterceptBotJob();
                            if (stopAll) {
                                break;
                            }

                            success = true;
                            webElementWork = false;

                            long currentInstructionStartTime = System.nanoTime();

                            InstructionLoad currentInstruction =
                                    blockLoad.getInstructionLoad().get(currentIndex);

                            byPassFlagLoop = parentIdsForLoop.contains(currentInstruction.getId());

                            mainMsg =
                                    currentInstruction.getOptional() ? "optional instruction" : "mandatory instruction";

                            if (!currentInstruction.getInstructionActive()) {

                                String nameInstruc =
                                        "(" + currentInstruction.getId() + ") " + currentInstruction.getName();
                                FieldData msgBlock = new FieldData(
                                        String.format("Ignore: \"%s\"", nameInstruc), ARConstantsEngine.IGNORE);

                                // Excel Report and Log
                                performActions.logAndReport(
                                        currentCondition,
                                        true,
                                        true,
                                        blockStartTime,
                                        blockReportName,
                                        success,
                                        new String[] {ARConstantsEngine.IGNORE},
                                        msgBlock,
                                        dataExcel,
                                        writerReport,
                                        "INSTRUCTION IGNORED",
                                        String.format("Instruction: \"%s\" is Inactive: ", nameInstruc));

                                currentIndex++;

                                continue;
                            }

                            // FIRST IMMEDIATE ATTEMPT TO LOCATE
                            if (isWebElementInstruction(currentInstruction)) {
                                webElementFound = immediateXPath(currentInstruction.getXpath());
                            }

                            performActions.waitPage();
                            try {
                                if (navTime > 0) {
                                    performActions.onHoldInSeconds(navTime);
                                    logOperations.info("Navigation Time : {}", navTime);
                                }
                            } catch (Exception ignore) {
                            }

                            // Fire on FIRST page load OR when the INSTRUCTION changes
                            // and only for web-element work (INPUT / OUTPUT / CLICK / GET / SET)
                            if (isWebElementInstruction(currentInstruction) && webElementFound == null) {

                                Integer currentInstructionId = currentInstruction.getId();

                                if (!firstPageLoadDone
                                        || lastInstructionIdPushed == null
                                        || !lastInstructionIdPushed.equals(currentInstructionId)) {

                                    firstPageLoadDone = true;
                                    lastInstructionIdPushed = currentInstructionId;

                                    performLists.resetListElements();
                                    pushUpdateListElements();

                                    logOperations.info("Total Target Elements: "
                                            + performLists
                                                    .getListTargetElements()
                                                    .size());

                                    // runYourScript(currentInstructionId);
                                }
                            }

                            //                            mapSavedLocators.clear();
                            //
                            //                            // Loop through the instructionReferenceLoadDTOList
                            //                            if (currentInstruction.getReferenceLoadDTOList() != null) {
                            //                                for (ReferenceLoadDTO reference :
                            // currentInstruction.getReferenceLoadDTOList()) {
                            //                                    // Populate the map with referenceType as the key and
                            // value as the value
                            //                                    mapSavedLocators.put(reference.getReferenceType(),
                            // reference.getValue());
                            //                                }
                            //                            }

                            currentIndex++;

                            // Allow Re-Execute Instructions in Previous Blocks
                            //                        if (currentInstruction.getExecuted() == null ||
                            // !currentInstruction.getExecuted()) {
                            boolean execGetOrSet = false;
                            boolean execCheckValue = false;
                            boolean execPDFCheck = false;
                            boolean execCSVCheck = false;
                            boolean execOutPut = false;
                            boolean execExcellWrite = false;
                            boolean pauseOperation = false;
                            boolean nextEnter = false;
                            boolean swipeUp = false;
                            boolean swipeDown = false;

                            String xPathOperation = null;
                            String[] parentActions = null;
                            String parentField = null;
                            String parentFieldLoop = null;
                            String variableField = null;
                            String localFormat = null;
                            //                            delimiterCSV = null;
                            String fieldName = null;
                            int parentId = currentInstruction.getParentId();

                            if (mapIgnore.contains(currentInstruction.getId() + "-" + currentInstruction.getName())) {
                                continue;
                            }

                            // webSocketSessionManager.sendMessageJson(int homeBankingId, String sessionId, String msg1,
                            // String msg2)
                            if (rowStatus.getInstructionId() == null) {
                                rowStatus.setInstructionId(currentInstruction.getId());
                                rowStatus.setColor("yellow"); // #fcba03 deep carmine yellow
                                jsonStatus = gson.toJson(rowStatus);
                                webSocketSessionManager.sendMessageJson(
                                        this.currentBotJob.getHomeBankingId(),
                                        sessionRowStatus,
                                        jsonStatus,
                                        "rowStatus");
                            } else {
                                // Previous
                                rowStatus.setColor("green"); // #1d9c06 green
                                jsonStatus = gson.toJson(rowStatus);
                                webSocketSessionManager.sendMessageJson(
                                        this.currentBotJob.getHomeBankingId(),
                                        sessionRowStatus,
                                        jsonStatus,
                                        "rowStatus");
                                try {
                                    Thread.sleep(300);
                                } catch (Exception e) {
                                }
                                // Current
                                rowStatus.setInstructionId(currentInstruction.getId());
                                rowStatus.setColor("yellow"); // #fcba03 deep carmine yellow
                                jsonStatus = gson.toJson(rowStatus);
                                webSocketSessionManager.sendMessageJson(
                                        this.currentBotJob.getHomeBankingId(),
                                        sessionRowStatus,
                                        jsonStatus,
                                        "rowStatus");
                            }

                            //                        String[] operation =
                            // UtilsMethods.splitIfContains(instruction.getOperation(),
                            // ARConstants.ACTION_SPECIFICATIONS_SPLITTER);
                            String[] actions = currentInstruction
                                    .getActions()
                                    .split(ARConstantsEngine.ACTION_SPECIFICATIONS_SPLITTER);
                            String[] operations = currentInstruction.getOperation() != null
                                    ? currentInstruction
                                            .getOperation()
                                            .split(ARConstantsEngine.ACTION_SPECIFICATIONS_SPLITTER)
                                    : null;

                            if (actions[0].equalsIgnoreCase(ARConstantsEngine.IF)
                                    || actions[0].equalsIgnoreCase(ARConstantsEngine.ELSEIF)
                                    || actions[0].equalsIgnoreCase(ARConstantsEngine.ELSE)
                                    || actions[0].equalsIgnoreCase(ARConstantsEngine.ENDIF)) {
                                currentCondition = ARExecution.ConditionStatus.valueOf(actions[0]);
                                if (previousCondition.equals(ARExecution.ConditionStatus.NONE)) {
                                    previousCondition = currentCondition;
                                    parentBlockCondition = parentId;
                                } else if (!previousCondition.equals(
                                        currentCondition)) { // To Reset the Progress to the Next Block
                                    previousCondition = currentCondition;
                                }

                                // Conditions When Pass to any of then
                                if (progressCondition.equals(ARExecution.ConditionStatus.IF_PASSED)
                                        || progressCondition.equals(ARExecution.ConditionStatus.ELSEIF_PASSED)) {
                                    int jumpPassed = performActions.checkActionToJump(
                                            actions[0],
                                            progressCondition,
                                            mapConditional,
                                            parentBlockCondition,
                                            currentIndex);

                                    // Any Error
                                    if (jumpPassed < 0) {
                                        stopAll = true;
                                        continue blockLoop;
                                    }
                                    // Found Next Block
                                    if (jumpPassed > 0) {
                                        currentIndex = jumpPassed;
                                        // reset all Conditional
                                        currentCondition = ARExecution.ConditionStatus.NONE;
                                        progressCondition = ARExecution.ConditionStatus.NONE;
                                        continue instructionLoop;
                                    }
                                } else if (currentCondition.equals(ARExecution.ConditionStatus.ENDIF)) {
                                    currentCondition = ARExecution.ConditionStatus.NONE;
                                    previousCondition = ARExecution.ConditionStatus.NONE;
                                    progressCondition = ARExecution.ConditionStatus.NONE;
                                    parentBlockCondition = -1;
                                }
                                continue;
                            }

                            // Case for Inputs
                            String valueInsert = "CHANGE ME";
                            if (actions[0].equals(ARConstantsEngine.INSERT)
                                    && actions[1].equals(ARConstantsEngine.ENTER)) {
                                String reference = actions[2];
                                valueInsert = dataExcel.get(reference);
                            } else if (actions[0].equals(ARConstantsEngine.INSERT)) {
                                String reference = actions[1];
                                valueInsert = dataExcel.get(reference);
                            }

                            FieldData msgInstruction = null;
                            if (actions[0].equalsIgnoreCase(ARConstantsEngine.EXCEL_GOTO)) {

                                //                                currentIndex++;
                                continue instructionLoop;

                            } else if (actions[0].equalsIgnoreCase(ARConstantsEngine.NEXT_ROW)) {
                                // <currentId:blockId:blockOrderNumber:bockName>
                                xExcelCurrentRow++;

                                String bodyMsg = "Excel Data Calling Next Row: " + (xExcelCurrentRow + 1);

                                if (xExcelCurrentRow >= xExcelDataSize - 1) {
                                    xExcelCurrentRow = xExcelDataSize - 1;
                                    msgInstruction = new FieldData(
                                            "Excel Data (limit reached) keeping last row",
                                            String.valueOf(xExcelCurrentRow + 1));
                                    bodyMsg = "Excel Data (limit reached) keeping last row: " + xExcelCurrentRow + 1;
                                } else {
                                    msgInstruction =
                                            new FieldData("Excel Data next row", String.valueOf(xExcelCurrentRow + 1));
                                }

                                // Excel Report and Log
                                performActions.logAndReport(
                                        currentCondition,
                                        true,
                                        true,
                                        blockStartTime,
                                        blockReportName,
                                        success,
                                        new String[] {ARConstantsEngine.NEXT_ROW},
                                        msgInstruction,
                                        dataExcel,
                                        writerReport,
                                        "Excel Data Calling Next Row",
                                        bodyMsg);

                                //                                currentIndex++;
                                continue instructionLoop;

                            } else if (actions[0].equalsIgnoreCase(ARConstantsEngine.GOTO)) {
                                // <currentId:blockId:blockOrderNumber:bockName>
                                msgInstruction = performActions.getBlockDetailsById(blocksLoaded, currentInstruction);
                                if (msgInstruction == null) {
                                    msgInstruction = new FieldData("GO TO Block \"Unknown\"", "Unknown");
                                    success = false;
                                    jumpGotoError = true;
                                    jumpGoto = true;
                                } else if (!mapLoops.containsKey(msgInstruction.getKey())) {
                                    jumpGoto = true;
                                    jumpGotoError = false;
                                    mapLoops.put(
                                            msgInstruction.getKey(),
                                            Integer.valueOf(msgInstruction.getValue())); // <id:orderId:blockName>
                                } else if (mapLoops.containsKey(msgInstruction.getKey())) {
                                    // Updates the msgInstruction
                                    jumpGoto = true;
                                    msgInstruction = new FieldData(
                                            msgInstruction.getKey(),
                                            String.valueOf(mapLoops.get(msgInstruction.getKey())));
                                }

                            } else if (actions[0].equalsIgnoreCase(ARConstantsEngine.LOOP)) {
                                // <currentId:parentId:parentName>
                                msgInstruction = performActions.getInstructionDetailsById(
                                        blocksLoaded.get(currentBlockOrder).getInstructionLoad(), currentInstruction);

                                if (msgInstruction == null) {
                                    msgInstruction = new FieldData("Jump To Parent \"Unknown\"", "Unknown");
                                    success = false;
                                } else if (!mapLoops.containsKey(msgInstruction.getKey())) {
                                    jumpLoopError = false;
                                    String[] parts = msgInstruction.getValue().split(":"); // Split by ':'
                                    mapLoops.put(msgInstruction.getKey(), Integer.valueOf(parts[1])); // Loop Times
                                    mapRefresh.put(msgInstruction.getKey(), Integer.valueOf(parts[0])); // Wait Time
                                } else if (mapLoops.containsKey(msgInstruction.getKey())) {
                                    // Updates the msgInstruction
                                    msgInstruction = new FieldData(
                                            msgInstruction.getKey(),
                                            String.valueOf(mapLoops.get(msgInstruction.getKey())));
                                }
                            } else if (actions[0].equalsIgnoreCase(ARConstantsEngine.REFRESH_LOOP)) {
                                msgInstruction = performActions.getInstructionDetailsById(
                                        blocksLoaded.get(currentBlockOrder).getInstructionLoad(), currentInstruction);
                                if (msgInstruction == null) {
                                    msgInstruction = new FieldData("Jump To Parent \"Unknown\"", "Unknown");
                                    success = false;
                                } else if (!mapLoops.containsKey(msgInstruction.getKey())) {
                                    jumpLoopError = false;
                                    String[] parts = msgInstruction.getValue().split(":"); // Split by ':'
                                    mapLoops.put(msgInstruction.getKey(), Integer.valueOf(parts[1])); // Loop Times
                                    mapRefresh.put(msgInstruction.getKey(), Integer.valueOf(parts[0])); // Wait Time
                                } else if (mapLoops.containsKey(msgInstruction.getKey())) {
                                    // Updates the msgInstruction
                                    // Refresh Loop  <5:5> <WAIT:LOOP>
                                    String updMsg = mapRefresh.get(msgInstruction.getKey()) + ":"
                                            + mapLoops.get(msgInstruction.getKey());
                                    msgInstruction = new FieldData(msgInstruction.getKey(), updMsg);
                                }
                            } else if (actions[0].equalsIgnoreCase(ARConstantsEngine.SET_VALUE)
                                    || (actions[0].equalsIgnoreCase(ARConstantsEngine.GET_VALUE))) {
                                msgInstruction = new FieldData(
                                        currentInstruction.getName(),
                                        (currentInstruction.getOperation() != null
                                                ? "(" + parentId + ")-" + operations[0] + ":" + operations[1]
                                                : (actions[0].equalsIgnoreCase(ARConstantsEngine.INSERT))
                                                        ? valueInsert
                                                        : ""));
                            } else {
                                msgInstruction = new FieldData(
                                        "(" + currentInstruction.getId() + ")-" + currentInstruction.getName(),
                                        (currentInstruction.getOperation() != null
                                                ? currentInstruction.getOperation()
                                                : (actions[0].equalsIgnoreCase(ARConstantsEngine.INSERT))
                                                        ? valueInsert
                                                        : ""));
                            }

                            resultActions = performActions.actionResultMessage(blockName, actions, msgInstruction);

                            if (actions[0].equalsIgnoreCase(ARConstantsEngine.PAUSE)) {
                                pauseOperation = true;

                                respModal = performMessage.showCustomModalDialogDragWin11(
                                        "PAUSE BOT JOB",
                                        "PAUSED at Block Name",
                                        blockLoad.getName(),
                                        " Please click OK to continue!",
                                        null,
                                        false,
                                        "Continue",
                                        "Stop Run",
                                        0);
                            } else if (actions[0].equalsIgnoreCase(ARConstantsEngine.NEXT_ENTER)) {
                                nextEnter = true;
                            } else if (actions[0].equalsIgnoreCase(ARConstantsEngine.SWIPE_UP)) {
                                swipeUp = true;
                            } else if (actions[0].equalsIgnoreCase(ARConstantsEngine.SWIPE_DOWN)) {
                                swipeDown = true;
                            }

                            if (actions[0].equalsIgnoreCase(ARConstantsEngine.LOOP)) {
                                parentFieldLoop =
                                        performActions.getInstructionParentField(currentInstruction, blockLoad);
                                if (parentField == null && parentFieldLoop == null) {
                                    parentFieldLoop = "Unknown parent";
                                    parentField = parentFieldLoop;
                                } else {
                                    parentField = parentFieldLoop;
                                }

                                parentFieldLoop = currentInstruction.getId() + ":" + parentId + ":" + parentFieldLoop;

                                if (mapLoops.containsKey(parentFieldLoop)) {
                                    int currentLoop = mapLoops.get(parentFieldLoop);
                                    if (currentLoop > 0) {
                                        jumpLoop = true;
                                        refreshLoop = false;
                                    } else {

                                        jumpLoop = false;
                                        refreshLoop = false;

                                        continue;
                                    }

                                } else {
                                    jumpLoopError = true;
                                }

                            } else if (actions[0].equalsIgnoreCase(ARConstantsEngine.REFRESH_ONLY)) {
                                refreshOnly = true;
                            } else if (actions[0].equalsIgnoreCase(ARConstantsEngine.REFRESH_LOOP)) {
                                parentFieldLoop =
                                        performActions.getInstructionParentField(currentInstruction, blockLoad);
                                if (parentField == null && parentFieldLoop == null) {
                                    parentFieldLoop = "Unknown parent";
                                    parentField = parentFieldLoop;
                                } else {
                                    parentField = parentFieldLoop;
                                }

                                parentFieldLoop = currentInstruction.getId() + ":" + parentId + ":" + parentFieldLoop;

                                if (mapLoops.containsKey(parentFieldLoop)) {
                                    int currentLoop = mapLoops.get(parentFieldLoop);
                                    if (currentLoop > 0) {
                                        jumpLoop = true;
                                        refreshLoop = true;
                                    } else {

                                        jumpLoop = false;
                                        refreshLoop = false;

                                        continue;
                                    }

                                } else {
                                    jumpLoopError = true;
                                }
                            } else if (actions[0].equalsIgnoreCase(ARConstantsEngine.GET_VALUE)
                                    || actions[0].equalsIgnoreCase(ARConstantsEngine.SET_VALUE)) {

                                execGetOrSet = true;

                                xPathOperation = performActions.getXPathInstruction(currentInstruction, blockLoad);
                                String actionsParent =
                                        performActions.getInstructionParentActions(currentInstruction, blockLoad);
                                parentActions = actionsParent != null
                                        ? actionsParent.split(ARConstantsEngine.ACTION_SPECIFICATIONS_SPLITTER)
                                        : null;

                                parentField = performActions.getInstructionParentField(currentInstruction, blockLoad);
                                variableField =
                                        performActions.getInstructionVariableField(currentInstruction, variablesLoaded);
                                localFormat = performActions.getInstructionVariableFormat(
                                        currentInstruction, variablesLoaded);
                                if (variableField == null) {
                                    variableField = "Not Variable defined";
                                }

                            } else if (actions[0].equalsIgnoreCase(ARConstantsEngine.OUTPUT)) {
                                execOutPut = true;
                                fieldName = currentInstruction.getId() + "-" + currentInstruction.getName();
                            } else if (actions[0].equalsIgnoreCase(ARConstantsEngine.CHECK_VALUE)) {
                                execCheckValue = true;
                                parentField = performActions.getInstructionParentField(currentInstruction, blockLoad);
                                variableField =
                                        performActions.getInstructionVariableField(currentInstruction, variablesLoaded);
                                if (variableField == null) {
                                    variableField = "Not Variable defined";
                                }
                            } else if (actions[0].equalsIgnoreCase(ARConstantsEngine.PDF_CHECK)) {
                                execPDFCheck = true;
                                parentField = performActions.getInstructionParentField(currentInstruction, blockLoad);
                                variableField =
                                        performActions.getInstructionVariableField(currentInstruction, variablesLoaded);
                                if (variableField == null) {
                                    variableField = "Not Variable defined";
                                }
                            } else if (actions[0].equalsIgnoreCase(ARConstantsEngine.CSV_CHECK)) {
                                execCSVCheck = true;
                                parentField = performActions.getInstructionParentField(currentInstruction, blockLoad);
                                variableField =
                                        performActions.getInstructionVariableField(currentInstruction, variablesLoaded);
                                if (variableField == null) {
                                    variableField = "Not Variable defined";
                                }
                            } else if (actions[0].equalsIgnoreCase(ARConstantsEngine.EXTRACT_FIELD)) {
                                execExcellWrite = true;
                                parentField = performActions.getInstructionParentField(currentInstruction, blockLoad);
                                variableField =
                                        performActions.getInstructionVariableField(currentInstruction, variablesLoaded);
                                //                                if (delimiterCSV == null) {
                                //                                    delimiterCSV =
                                // performActions.getInstructionVariableDelimiter(
                                //                                            currentInstruction, variablesLoaded);
                                //                                }
                                if (variableField == null) {
                                    variableField = "Not Variable defined";
                                }
                            }

                            try {
                                if (jumpGoto) {

                                    if (jumpGotoError) {
                                        success = false;
                                        failedMessage = "Failed: GO TO ";
                                        resultActions = performActions.blockGotoFailed(resultActions);
                                    } else {
                                        if (!loopBlockActive.contains(msgInstruction.getKey())) {
                                            loopBlockActive.add(msgInstruction.getKey());
                                            loopBlockLimits.put(
                                                    msgInstruction.getKey(),
                                                    Integer.valueOf(msgInstruction.getValue()));
                                        }
                                        int repeat = mapLoops.get(msgInstruction.getKey()) - 1;
                                        if (repeat > 0) {
                                            mapLoops.put(msgInstruction.getKey(), repeat);
                                            try {

                                                String[] parts =
                                                        msgInstruction.getKey().split(":");
                                                int blockOrderNumber = Integer.parseInt(parts[2]);

                                                currentBlockOrder = blockOrderNumber - 1;
                                                currentInstruction.setExecuted(true);

                                                failedMessage = "";
                                                success = true;

                                            } catch (Exception ex) {
                                                failedMessage = "Failed: GO TO ";
                                                msgInstruction = updateMSGInstruction(msgInstruction, failedMessage);

                                                success = false;

                                                resultActions = performActions.blockGotoFailed(resultActions);
                                            }

                                            FieldData currentPair = new FieldData(
                                                    msgInstruction.getKey(),
                                                    String.valueOf(mapLoops.get(msgInstruction.getKey())));

                                            // Excel Report and Log
                                            performActions.logAndReport(
                                                    currentCondition,
                                                    true,
                                                    true,
                                                    currentInstructionStartTime,
                                                    blockReportName,
                                                    success,
                                                    actions,
                                                    currentPair,
                                                    dataExcel,
                                                    writerReport,
                                                    mainMsg,
                                                    finalLogMessage(failedMessage, resultActions));

                                            if (success) {
                                                continue blockLoop;
                                            } else {
                                                stopAll = true;
                                                if (stopAll) {
                                                    continue blockLoop;
                                                }
                                            }

                                        } else {
                                            mapLoops.put(msgInstruction.getKey(), repeat);
                                            continue blockLoop;
                                        }
                                    }

                                } else if (jumpLoop) {

                                    if (mapRefresh.containsKey(parentFieldLoop)) {
                                        int timerLoop = mapRefresh.get(parentFieldLoop);
                                        performActions.onHoldInSeconds(timerLoop);
                                    }

                                    if (mapLoops.containsKey(parentFieldLoop)) {

                                        int repeat = mapLoops.get(parentFieldLoop) - 1;
                                        String[] parts = parentFieldLoop.split(":");
                                        if (repeat > 0) {
                                            mapLoops.put(parentFieldLoop, repeat);

                                            logOperations.info(String.format(
                                                    "Loop to Parent :\"%s\" - %d Times",
                                                    parts[0] + "-(" + parts[1] + ") " + parts[2],
                                                    mapLoops.get(parentFieldLoop)));

                                            if (refreshLoop) {

                                                String extraLog = performActions.actionResultMessage(
                                                        blockName,
                                                        new String[] {ARConstantsEngine.REFRESH_HOLD},
                                                        msgInstruction);

                                                performActions.performOtherActions(
                                                        byPassNotFound,
                                                        currentInstruction,
                                                        new String[] {ARConstantsEngine.REFRESH_HOLD});

                                                // Excel Report and Log
                                                performActions.logAndReport(
                                                        currentCondition,
                                                        true,
                                                        true,
                                                        currentInstructionStartTime,
                                                        blockReportName,
                                                        success,
                                                        new String[] {ARConstantsEngine.REFRESH_HOLD},
                                                        msgInstruction,
                                                        dataExcel,
                                                        writerReport,
                                                        mainMsg,
                                                        extraLog);

                                                // Refresh For REFRESH_LOOP
                                                extraLog = performActions.actionResultMessage(
                                                        blockName,
                                                        new String[] {ARConstantsEngine.REFRESH_ONLY},
                                                        msgInstruction);

                                                performActions.performOtherActions(
                                                        byPassNotFound,
                                                        currentInstruction,
                                                        new String[] {ARConstantsEngine.REFRESH_ONLY});

                                                // Excel Report and Log
                                                performActions.logAndReport(
                                                        currentCondition,
                                                        true,
                                                        true,
                                                        currentInstructionStartTime,
                                                        blockReportName,
                                                        success,
                                                        new String[] {ARConstantsEngine.REFRESH_ONLY},
                                                        msgInstruction,
                                                        dataExcel,
                                                        writerReport,
                                                        mainMsg,
                                                        extraLog);

                                                refreshLoop = false;
                                            }

                                            for (int x = 0; x < instructionIds.length; x++) {
                                                if (instructionIds[x] == parentId) {
                                                    currentIndex = x;
                                                    break; // Exit the loop once the value is found
                                                }
                                            }

                                            // Get Correct Updated Pair for REFRESH_LOOP ACTION
                                            FieldData currentPair = new FieldData(
                                                    msgInstruction.getKey(),
                                                    String.valueOf(mapLoops.get(msgInstruction.getKey())));

                                            // Excel Report and Log
                                            performActions.logAndReport(
                                                    currentCondition,
                                                    true,
                                                    true,
                                                    currentInstructionStartTime,
                                                    blockReportName,
                                                    success,
                                                    actions,
                                                    currentPair,
                                                    dataExcel,
                                                    writerReport,
                                                    mainMsg,
                                                    finalLogMessage(failedMessage, resultActions));

                                        } else {
                                            mapLoops.put(parentFieldLoop, repeat);
                                        }

                                        jumpLoop = false;
                                        refreshLoop = false;

                                        if (repeat > 0) {
                                            continue instructionLoop;
                                        } else {

                                            logOperations.info(String.format(
                                                    "IGNORING Loop to Parent :\"%s\" - %d Times",
                                                    parts[0] + "-(" + parts[1] + ") " + parts[2],
                                                    mapLoops.get(parentFieldLoop)));
                                            continue;
                                        }

                                    } else {
                                        resultActions = performActions.parentValueIsNotDefined(
                                                currentInstruction.getName(),
                                                "(" + parentId + ")-" + parentField,
                                                resultActions);

                                        success = false;
                                    }

                                } else if (refreshOnly) {

                                    performActions.performOtherActions(byPassNotFound, currentInstruction, actions);

                                    resultActions = "Refresh Current Web Page ->  inside Block :\""
                                            + blockLoad.getName() + "\"";

                                    refreshOnly = false;

                                } else if (actions[0].equals(ARConstantsEngine.HOLD)
                                        || actions[0].equals(ARConstantsEngine.QUIT)
                                        || actions[0].equals(ARConstantsEngine.SCREEN)
                                        || actions[0].equals(ARConstantsEngine.REFRESH_ONLY)) {

                                    performActions.performOtherActions(byPassNotFound, currentInstruction, actions);

                                    if (actions[0].equals(ARConstantsEngine.QUIT)) {
                                        stopAll = true;
                                        success = true;
                                    }

                                } else if (!jumpGotoError
                                        && !jumpLoopError
                                        && !execGetOrSet
                                        && !execCheckValue
                                        && !execPDFCheck
                                        && !execCSVCheck
                                        && !execExcellWrite
                                        && !pauseOperation
                                        && !nextEnter
                                        && !swipeUp
                                        && !swipeDown) {

                                    webElementWork = true;

                                    FieldData fieldData = performActions.extractFieldData(
                                            dataExcel,
                                            actions,
                                            currentInstruction.getDefaultValue(),
                                            currentInstruction.getCodified());

                                    //                                    webElementFound = null;
                                    boolean forceCoordinates = currentInstruction.getForceCoordinates() != null
                                            && currentInstruction.getForceCoordinates();

                                    if (!isMobileApp) {

                                        if (isWebElementInstruction(currentInstruction) && webElementFound == null) {
                                            try {
                                                performActions.waitPage();

                                                matchXPath = InstructionLoadMatcher.findMatchingTargetElementByXPath(
                                                        performLists.getListTargetElements(), currentInstruction);
                                                matchScanned = null;
                                                //                                            InputInfo match =
                                                // findMatchingInput(inputs, currentInstruction);

                                                if (matchXPath == null) {
                                                    matchScanned = InstructionLoadMatcher.findMatchingTargetElement(
                                                            performLists.getListTargetElements(), currentInstruction);

                                                    if (matchScanned != null) {
                                                        InstructionLoadUpdater.applyMatchToInstruction(
                                                                currentInstruction, matchScanned);

                                                        // SECOND IMMEDIATE ATTEMPT TO LOCATE
                                                        webElementFound = immediateXPath(matchScanned.getXPath());
                                                    }
                                                }

                                                // VERY IMPORTANT TO VALIDAE IF THE ELEMENT IS ON TEH PAGE FIRST
                                                //                                            if (matchXPath != null ||
                                                // matchScanned != null || match != null) {
                                                if (webElementFound == null) {
                                                    webElementFound = performActions.searchElement(
                                                            currentInstruction,
                                                            this.currentBotJob.getId(),
                                                            forceCoordinates,
                                                            byPassFlagLoop);
                                                }
                                                //                                            } else {
                                                //                                                webElementFound =
                                                // null;
                                                //                                                forceCoordinates =
                                                // false;
                                                //                                            }
                                            } catch (Exception ex) {
                                                success = false;
                                            }
                                        }
                                    } else {
                                        // Safely extract the first element ID (if present)
                                        //                                        Integer elementId =
                                        // Optional.ofNullable(splitDTO.getElementDetails())
                                        //                                                .filter(arr -> arr.length > 0)
                                        //                                                .map(arr -> arr[0])
                                        //                                                .map(ElementDTO::getId)
                                        //                                                .orElse(null);

                                        // Find matching instruction by variableId
                                        //                                        InstructionLoad matchingInstruction =
                                        // Optional.ofNullable(
                                        //
                                        // performLists.getListInstruction())
                                        //
                                        // .orElse(Collections.emptyList())
                                        //                                                .stream()
                                        //                                                .filter(i ->
                                        // Objects.equals(i.getId(), elementId))
                                        //                                                .findFirst()
                                        //                                                .orElse(null);

                                        // 2) Apply only non-empty values into splitDTO and elementDetails[0]
                                        //                                        if (matchingInstruction != null) {
                                        // >>> Add AttrData:* references into elementDetails.attributesData
                                        SplitDTO.applyAttrDataFromReferences(splitDTO, currentInstruction);

                                        SplitDTO.applyInstructionToSplit(splitDTO, currentInstruction);
                                        //                                        }

                                        // Already Maps List<TargetElement>
                                        //
                                        // androidHelper.scanElementsWithCanonicalXmlOnly(
                                        //
                                        // androidDevice.getCurrentDriver());

                                        // webElementFound = androidDevice.searchElement(splitDTO, actions,
                                        // currentPage);

                                        //                                        if (webElementFound == null) {
                                        //                                            appendLog(
                                        //
                                        // currentInstruction.getName() + "- Not Found- using coordinates",
                                        //                                                    "warn");
                                        // androidDevice.executeAction(webElementFound, splitDTO, actions);
                                        //                                        }
                                    }

                                    // VERY IMPORTANT FORCE COORDINATES
                                    // FORCE COORDINATES COMMENTED
                                    //                                    if (webElementFound == null &&
                                    // forceCoordinates && !isMobileApp) {
                                    //
                                    //                                        Boolean pressEnterAfter = false;
                                    //                                        if
                                    // (actions[0].equals(ARConstantsEngine.INSERT)
                                    //                                                &&
                                    // actions[1].equals(ARConstantsEngine.ENTER)) {
                                    //                                            pressEnterAfter = true;
                                    //                                        }
                                    //                                        if
                                    // (actions[0].equalsIgnoreCase(ARConstantsEngine.VISUALIZE)
                                    //                                                ||
                                    // actions[0].equalsIgnoreCase(ARConstantsEngine.CLICK)
                                    //                                                ||
                                    // actions[0].equalsIgnoreCase(ARConstantsEngine.INSERT)) {
                                    //
                                    //                                            List<WebElement> smartSearch =
                                    // performActions.findBySmartLocator(
                                    //
                                    // currentInstruction.getCssSelector());
                                    //                                            if (!smartSearch.isEmpty()) {
                                    //                                                success =
                                    // performActions.executeActionsAtCoordinates(
                                    //                                                        "coordinates", fieldData,
                                    // actions[0], pressEnterAfter);
                                    //                                            }
                                    //                                        }
                                    //                                    }

                                    byPassNotFound = byPassFlagLoop
                                            || !currentCondition.equals(ARExecution.ConditionStatus.NONE);

                                    if (webElementFound != null && success) {

                                        success = performActions.performWebActions(
                                                byPassNotFound,
                                                "coordinates",
                                                fieldData,
                                                currentInstruction,
                                                mapOperators,
                                                webElementFound,
                                                actions,
                                                isMobileApp,
                                                splitDTO);

                                        if (execOutPut) {
                                            if (mapOperators.containsKey(fieldName)) {
                                                msgInstruction = new FieldData(fieldName, mapOperators.get(fieldName));
                                            } else {
                                                msgInstruction = new FieldData(fieldName, "TEXT OUTPUT NOT FOUND");
                                            }
                                        }
                                    }
                                    // Special Cases for Select Responses
                                    // It could be Improved the case
                                    if (resultActions.contains("FAIL")
                                            || performLists
                                                    .getListTargetElements()
                                                    .isEmpty()
                                            || (matchXPath == null && matchScanned == null && webElementFound == null)
                                            || (webElementFound == null && !forceCoordinates)) {
                                        failedMessage = "Failed execution Web Element ";
                                        msgInstruction = updateMSGInstruction(msgInstruction, failedMessage);
                                        if (resultActions.contains("PASSED")) {
                                            resultActions = resultActions.replaceAll("PASSED", "FAIL");
                                        }
                                        success = false;

                                        if (performLists.getListTargetElements().isEmpty()) {
                                            String reason = performActions.buildMessageResult(
                                                    success, blockName, "TIME OUT", "Device timeout", "TIME OUT");
                                            appendLog("[TEST]" + reason, "error");
                                            stopAll = true;
                                        }
                                    } else if (resultActions != null && success) {
                                        failedMessage = "";
                                        currentInstruction.setExecuted(true);
                                    }

                                } else if (execGetOrSet) {
                                    // GET && SET Special Operators

                                    if (parentField != null && parentId != 0) {
                                        parentField = parentId + "-" + parentField;
                                    }
                                    // Mandatory for GET_VALUE
                                    if (xPathOperation == null
                                            && actions[0].equalsIgnoreCase(ARConstantsEngine.GET_VALUE)) {
                                        failedMessage = "Parent Id in Wrong Block ";
                                        msgInstruction = updateMSGInstruction(msgInstruction, failedMessage);
                                        resultActions = performActions.parentIdWrongBlock(
                                                currentInstruction, blockLoad, resultActions, currentCondition);
                                        success = false;
                                    } else if (parentField == null) {
                                        failedMessage = "Parent Id in Wrong Block ";
                                        msgInstruction = updateMSGInstruction(msgInstruction, failedMessage);
                                        resultActions = performActions.parentIdWrongBlock(
                                                currentInstruction, blockLoad, resultActions, currentCondition);
                                        success = false;
                                    } else {

                                        webElementFound = null;
                                        if (isMobileApp) {
                                            int index = IntStream.range(0, instructionIds.length)
                                                    .filter(i -> instructionIds[i] == parentId)
                                                    .findFirst()
                                                    .orElse(-1);

                                            InstructionLoad refInstruction = blockLoad
                                                    .getInstructionLoad()
                                                    .get(index);

                                            SplitDTO.applyAttrDataFromReferences(splitDTO, refInstruction);
                                            SplitDTO.applyInstructionToSplit(splitDTO, refInstruction);

                                            // webElementFound =androidDevice.searchElement(splitDTO, actions,
                                            // currentPage);
                                        }

                                        resultActions = performActions.performOperatorActions(
                                                byPassNotFound,
                                                currentInstruction,
                                                xPathOperation,
                                                parentActions,
                                                actions[0],
                                                operations,
                                                parentField,
                                                variableField,
                                                mapOperators,
                                                webElementFound);

                                        if (resultActions.contains("FAIL")) {
                                            failedMessage = "Failed: Operation (GetValue / SetValue) ";
                                            msgInstruction = updateMSGInstruction(msgInstruction, failedMessage);
                                            if (resultActions.contains("PASSED")) {
                                                resultActions = resultActions.replaceAll("PASSED", "FAIL");
                                            }
                                            success = false;
                                        } else {
                                            failedMessage = "";
                                            success = true;
                                            if (!Strings.isNullOrEmpty(localFormat)) {
                                                String valueTo = mapOperators.get(variableField);
                                                valueTo = performActions.removeAllCurrencySymbols(valueTo);
                                                valueTo = performActions.formatLocalNumber(valueTo, localFormat);
                                                mapOperators.put(variableField, valueTo);
                                            }
                                        }
                                    }

                                } else if (execCheckValue) {
                                    // Check Validation Operator

                                    if (!mapOperators.containsKey(variableField)) {
                                        failedMessage = "Get Value Is Not Defined ";
                                        msgInstruction = updateMSGInstruction(msgInstruction, failedMessage);
                                        //                                        resultActions =
                                        // performActions.getValueIsNotDefined(
                                        //                                                actions[0],
                                        //                                                currentInstruction,
                                        //                                                resultActions,
                                        //                                                ARExecution.ConditionStatus
                                        //                                                        .NONE, // NOT
                                        // currentCondition to Force Message,
                                        //                                                parentField,
                                        //                                                variableField);

                                        String reason = performActions.buildGetVariableReason(
                                                actions[0],
                                                currentInstruction,
                                                resultActions,
                                                currentCondition,
                                                parentField,
                                                variableField,
                                                byPassNotFound, // or your bypass flag
                                                blockName,
                                                currentInstruction.getId(),
                                                false);

                                        appendLog("[TEST]" + reason, "error");
                                        alreadyLogged = true;

                                        logOperations.error("{}", reason);

                                        success = false;
                                    } else {
                                        //                                    fieldName = parentField;

                                        resultActions = "Check Value for " + String.join(" ", operations);
                                        boolean isOperationValid = false;
                                        String invalidValues = null;

                                        if (operations[1].equalsIgnoreCase("=")) {
                                            isOperationValid = mapOperators
                                                    .get(variableField)
                                                    .trim()
                                                    .equalsIgnoreCase(operations[2].trim());

                                        } else if (operations[1].equalsIgnoreCase(">")) {
                                            int resp = handleGreaterThan(
                                                    mapOperators
                                                            .get(variableField)
                                                            .trim(),
                                                    operations[2].trim());
                                            if (resp == 1) {
                                                isOperationValid = true;
                                            } else if (resp == 0) {
                                                isOperationValid = false;
                                            } else {
                                                isOperationValid = false;
                                                invalidValues = "Invalid Numbers";
                                            }
                                        } else if (operations[1].equalsIgnoreCase("!=")) {
                                            isOperationValid = !mapOperators
                                                    .get(variableField)
                                                    .trim()
                                                    .equalsIgnoreCase(operations[2].trim());
                                        } else if (operations[1].equalsIgnoreCase("<")) {
                                            int resp = handleLessThan(
                                                    mapOperators
                                                            .get(variableField)
                                                            .trim(),
                                                    operations[2].trim());
                                            if (resp == 1) {
                                                isOperationValid = true;
                                            } else if (resp == 0) {
                                                isOperationValid = false;
                                            } else {
                                                isOperationValid = false;
                                                invalidValues = "Invalid Numbers";
                                            }
                                        }

                                        if (isOperationValid) {
                                            currentInstruction.setExecuted(true);
                                            failedMessage = "";

                                            resultActions = performActions.buildValidationReason(
                                                    invalidValues,
                                                    parentField,
                                                    mapOperators.get(variableField), // actual/current web value
                                                    operations[2].trim(),
                                                    resultActions, // lastInstructionExecuted
                                                    operations,
                                                    currentCondition,
                                                    byPassNotFound,
                                                    true,
                                                    blockName,
                                                    currentInstruction.getId(),
                                                    isOperationValid);

                                            appendLog("[TEST]" + resultActions, "info");
                                            alreadyLogged = true;

                                            success = true;
                                        } else {
                                            failedMessage = "Failed: Check Validation ";
                                            msgInstruction = updateMSGInstruction(msgInstruction, failedMessage);
                                            //                                            resultActions =
                                            // performActions.checkValidationFailed(
                                            //                                                    invalidValues,
                                            //                                                    parentField,
                                            //
                                            // mapOperators.get(variableField),
                                            //                                                    resultActions,
                                            //                                                    operations,
                                            //                                                    currentCondition,
                                            //                                                    byPassNotFound);

                                            resultActions = performActions.buildValidationReason(
                                                    invalidValues,
                                                    parentField,
                                                    mapOperators.get(variableField), // actual/current web value
                                                    operations[2].trim(),
                                                    resultActions, // lastInstructionExecuted
                                                    operations,
                                                    currentCondition,
                                                    byPassNotFound,
                                                    true,
                                                    blockName,
                                                    currentInstruction.getId(),
                                                    isOperationValid);

                                            appendLog("[TEST]" + resultActions, "error");
                                            alreadyLogged = true;

                                            logOperations.error("Validation failed: {}", resultActions);

                                            success = false;
                                        }
                                    }

                                } else if (execCSVCheck || execPDFCheck) {
                                    String msgCSVPrefix = "CSV ";
                                    if (execPDFCheck) {
                                        msgCSVPrefix = "PDF ";
                                    }

                                    // If fieldsToValidate is null/empty => ignore (no log)
                                    Map<String, FieldsToValidate> fMap = splitDTO.getFieldsToValidate();
                                    if (fMap == null || fMap.isEmpty()) {
                                        // ignore
                                        resultActions = resultActions.replaceAll("PASSED", "IGNORED");
                                    } else {

                                        for (Map.Entry<String, FieldsToValidate> entry : fMap.entrySet()) {

                                            FieldsToValidate expectedField = entry.getValue();

                                            // Only run if parentField exists as a key. If not found => ignore (no log).
                                            if (expectedField == null || expectedField.getValue() == null) {
                                                // ignore
                                            } else {

                                                String parentFieldCSV = entry.getKey();

                                                String foundKey = null;
                                                if (allOutPuts != null && !allOutPuts.isEmpty()) {
                                                    for (Integer outId : allOutPuts) {
                                                        String k = outId + "-" + parentFieldCSV;
                                                        if (mapOperators.containsKey(k)) {
                                                            foundKey = k;
                                                            break;
                                                        }
                                                    }
                                                }

                                                if (foundKey == null) {
                                                    // ignore
                                                } else {

                                                    String actualValue = mapOperators.get(foundKey);

                                                    // You still keep your "Get Value Is Not Defined" behavior
                                                    if (actualValue == null
                                                            || actualValue
                                                                    .trim()
                                                                    .isEmpty()) {
                                                        failedMessage = "Get Value Is Not Defined ";
                                                        msgInstruction =
                                                                updateMSGInstruction(msgInstruction, failedMessage);

                                                        //                                                resultActions
                                                        // =
                                                        // performActions.getValueIsNotDefined(
                                                        //
                                                        // actions[0],
                                                        //
                                                        // currentInstruction,
                                                        //
                                                        // resultActions,
                                                        //
                                                        // ARExecution.ConditionStatus.NONE,
                                                        //
                                                        // parentField,
                                                        //
                                                        // variableField);

                                                        String reason = performActions.buildGetVariableReason(
                                                                actions[0],
                                                                currentInstruction,
                                                                resultActions,
                                                                currentCondition,
                                                                parentField,
                                                                variableField,
                                                                byPassNotFound, // or your bypass flag
                                                                blockName,
                                                                currentInstruction.getId(),
                                                                false);

                                                        appendLog("[TEST]" + reason, "error");
                                                        alreadyLogged = true;

                                                        logOperations.error("{}", reason);

                                                        success = false;

                                                    } else {
                                                        // actual/current value on the web/app side

                                                        // expected value comes from
                                                        // splitDTO.fieldsToValidate[parentField].value
                                                        String expectedValue = expectedField.getValue();

                                                        // operator comes from your parsed operations array
                                                        String operator = operations[1];

                                                        String msgCSV = msgCSVPrefix + parentFieldCSV;
                                                        resultActions = "Check Value for " + parentFieldCSV;
                                                        ValidationResult vr =
                                                                evaluateOperation(actualValue, operator, expectedValue);

                                                        if (vr.valid) {
                                                            currentInstruction.setExecuted(true);
                                                            failedMessage = "";
                                                            success = true;

                                                            resultActions = performActions.buildValidationReason(
                                                                    vr.invalidReason,
                                                                    msgCSV,
                                                                    actualValue, // actual/current web value
                                                                    expectedValue,
                                                                    resultActions,
                                                                    operations,
                                                                    currentCondition,
                                                                    byPassNotFound,
                                                                    true,
                                                                    blockName,
                                                                    currentInstruction.getId(),
                                                                    true);

                                                            appendLog("[TEST]" + resultActions, "info");
                                                            alreadyLogged = true;

                                                            logOperations.info(
                                                                    msgCSVPrefix
                                                                            + " Validation SUCCESS for field '{}': actual='{}' {} expected='{}'",
                                                                    parentFieldCSV,
                                                                    actualValue,
                                                                    operator,
                                                                    expectedValue);

                                                        } else {
                                                            failedMessage = "Failed: Check Validation ";
                                                            msgInstruction =
                                                                    updateMSGInstruction(msgInstruction, failedMessage);

                                                            resultActions = performActions.buildValidationReason(
                                                                    vr.invalidReason,
                                                                    msgCSV,
                                                                    actualValue, // actual/current web value
                                                                    expectedValue,
                                                                    resultActions,
                                                                    operations,
                                                                    currentCondition,
                                                                    byPassNotFound,
                                                                    true,
                                                                    blockName,
                                                                    currentInstruction.getId(),
                                                                    false);

                                                            appendLog("[TEST]" + resultActions, "error");
                                                            alreadyLogged = true;

                                                            logOperations.error(
                                                                    msgCSVPrefix
                                                                            + " Values Validation FAILED for field '{}': actual='{}' {} expected='{}'. Reason: {}",
                                                                    msgCSV,
                                                                    actualValue,
                                                                    operator,
                                                                    expectedValue,
                                                                    resultActions);

                                                            success = false;
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else if (execExcellWrite) {
                                    // Excel Write Operator

                                    if (parentField == null) {
                                        failedMessage = "Parent Id in Wrong Block ";
                                        msgInstruction = updateMSGInstruction(msgInstruction, failedMessage);
                                        resultActions = performActions.parentIdWrongBlock(
                                                currentInstruction, blockLoad, resultActions, currentCondition);

                                        success = false;

                                    } else if (!mapOperators.containsKey(variableField)) {
                                        failedMessage = "Get Value Is Not Defined ";
                                        msgInstruction = updateMSGInstruction(msgInstruction, failedMessage);
                                        //                                        resultActions =
                                        // performActions.getValueIsNotDefined(
                                        //                                                actions[0],
                                        //                                                currentInstruction,
                                        //                                                resultActions,
                                        //                                                ARExecution.ConditionStatus
                                        //                                                        .NONE, // NOT
                                        // currentCondition to Force Message,
                                        //                                                parentField,
                                        //                                                variableField);

                                        String reason = performActions.buildGetVariableReason(
                                                actions[0],
                                                currentInstruction,
                                                resultActions,
                                                currentCondition,
                                                parentField,
                                                variableField,
                                                byPassNotFound, // or your bypass flag
                                                blockName,
                                                currentInstruction.getId(),
                                                false);

                                        appendLog("[TEST]" + reason, "error");
                                        alreadyLogged = true;

                                        logOperations.error("{}", reason);

                                        success = false;
                                    } else {

                                        if (excelExportOnceCreation) {
                                            //
                                            // writerExport.insertReportHead();
                                            excelExportOnceCreation = false;
                                        }

                                        if (!Strings.isNullOrEmpty(newExcelFieldName)) {
                                            writerExport = new ExcelWriter(
                                                            newExcelFieldName, performActions.getCurrentDriver(), true)
                                                    .withPurpose("export");

                                            // Only create Columns if Have a file to write
                                            tableCSV.put(
                                                    xExcelCurrentRow, parentField, mapOperators.get(variableField));
                                        }

                                        resultActions = performActions.messageExcel(
                                                "Excel Write",
                                                currentInstruction,
                                                parentField,
                                                variableField,
                                                mapOperators.get(variableField),
                                                blockName,
                                                currentInstruction.getId(),
                                                (writerExport != null));

                                        if (mapExportRows.size() == 0) {
                                            //
                                            // writerExport.insertBlockSeparation(blockLoad.getName());
                                            //                                            exportIndex *= 2;
                                        }

                                        // Insert the updated mapExport into the Excel after each instruction
                                        if (writerExport != null) {
                                            headersExport.add(parentField.trim());

                                            String webData = mapOperators
                                                    .get(variableField)
                                                    .trim();
                                            webData = performActions.sanitizeValue(webData);
                                            mapExportRows.put(parentField.trim(), webData);

                                            //
                                            // addRowFromMap(mapExportRows);
                                            if (newExcelFieldName != null
                                                    && newExcelFieldName
                                                            .toLowerCase()
                                                            .endsWith(".csv")) {
                                                if (Strings.isNullOrEmpty(delimiterCSV)) {
                                                    delimiterCSV = ",";
                                                }

                                                //
                                                //                                                String csvContent
                                                // =
                                                // getBancaStatoCsvContent(delimiterCSV);
                                                //
                                                // writeToFile(newExcelFieldName, csvContent);

                                                // writerExport.writeMapToCSV(mapExport, newExcelFieldName,
                                                // delimiterCSV);
                                            } else {
                                                //
                                                // writerExport.insertFieldNameAndValueLastColumn(
                                                //                                                        mapExport,
                                                // exportIndex - 1);
                                            }
                                        }
                                        performActions.onHoldForSeconds(null);

                                        if (resultActions != null) {
                                            currentInstruction.setExecuted(true);
                                            failedMessage = "";
                                            success = true;
                                        } else {
                                            failedMessage = "Failed: Generate File -> Excel/CSV ";
                                            msgInstruction = updateMSGInstruction(msgInstruction, failedMessage);
                                            success = false;
                                        }
                                    }
                                }

                            } catch (Throwable t) {
                                success = false;

                                String[] lines = t.getMessage().split("\n");
                                String msg1 = "";
                                String msg2 = "";

                                for (String line : lines) {
                                    if (Strings.isNullOrEmpty(msg1)) {
                                        msg1 = line;
                                    } else if (Strings.isNullOrEmpty(msg2)) {
                                        msg2 = line;
                                    }
                                }

                                String msg3 = resultActions;

                                if (Strings.isNullOrEmpty(failedMessage)) {
                                    failedMessage = "Failed: General Execution ";
                                    msgInstruction = updateMSGInstruction(msgInstruction, failedMessage);
                                }
                                logOperations.error("Error: {} - {} - {} - {}", resultActions, msg1, msg2, msg3);
                                //                                performMessage.errorMessage(resultActions, msg1, msg2,
                                // msg3, null, 260);
                                //                            throw new RuntimeException(t);
                            }

                            if (success && !alreadyLogged) {
                                if (resultActions.contains("IGNORED")) {
                                    appendLog("[TEST]" + resultActions, "warn");
                                } else {
                                    appendLog("[TEST]" + resultActions, "info");
                                }
                            } else if (!alreadyLogged) {
                                appendLog("[TEST]" + resultActions, "error");
                                anyFailure = true;
                            }

                            alreadyLogged = false;

                            printLog(finalLogMessage(failedMessage, resultActions), success);

                            // Here mark the Status of a progress Condition Fail or Success at the end of each Kind
                            // of Execution
                            if (!jumpGotoError
                                    && !jumpLoopError
                                    && !currentCondition.equals(ARExecution.ConditionStatus.NONE)) {
                                progressCondition = performActions.updateProgressSuccess(success, currentCondition);
                                //                                continue instructionLoop;
                            } else {
                                progressCondition = ARExecution.ConditionStatus.NONE;
                            }

                            // Excel Report and Log
                            performActions.logAndReport(
                                    !byPassFlagLoop ? progressCondition : ARExecution.ConditionStatus.BY_PASS,
                                    true,
                                    true,
                                    currentInstructionStartTime,
                                    blockReportName,
                                    success,
                                    actions,
                                    msgInstruction,
                                    dataExcel,
                                    writerReport,
                                    mainMsg,
                                    finalLogMessage(failedMessage, resultActions));

                            failedMessage = "";

                            if (pauseOperation && respModal.equals(ARExecution.DialogModal.STOP)) {

                                String nameInstruc =
                                        "(" + currentInstruction.getId() + ") " + currentInstruction.getName();

                                resultActions = String.format("STOP ALL PROCESSES: \"%s\"", nameInstruc);

                                FieldData msgBlock = new FieldData(resultActions, ARConstantsEngine.PAUSE);

                                // Excel Report and Log
                                performActions.logAndReport(
                                        currentCondition,
                                        true,
                                        true,
                                        blockStartTime,
                                        blockReportName,
                                        success,
                                        new String[] {ARConstantsEngine.PAUSE},
                                        msgBlock,
                                        dataExcel,
                                        writerReport,
                                        "PAUSE -> STOP",
                                        String.format("STOP ALL CALLED AT: \"%s\" : ", nameInstruc));

                                respModal = ARExecution.DialogModal.NONE;
                                stopAll = true;
                                break;
                            }

                            if (nextEnter) {

                                String nameInstruc =
                                        "(" + currentInstruction.getId() + ") " + currentInstruction.getName();

                                resultActions = String.format("Device : \"%s\"", nameInstruc);

                                FieldData msgBlock = new FieldData(resultActions, ARConstantsEngine.NEXT_ENTER);

                                // Excel Report and Log
                                performActions.logAndReport(
                                        currentCondition,
                                        true,
                                        true,
                                        blockStartTime,
                                        blockReportName,
                                        success,
                                        new String[] {ARConstantsEngine.NEXT_ENTER},
                                        msgBlock,
                                        dataExcel,
                                        writerReport,
                                        "DEVICE -> NEXT/ENTER",
                                        String.format("NEXT/ENTER CALLED AT: \"%s\" : ", nameInstruc));

                                respModal = ARExecution.DialogModal.NONE;
                                continue;
                            } else if (swipeUp) {

                                String nameInstruc =
                                        "(" + currentInstruction.getId() + ") " + currentInstruction.getName();

                                resultActions = String.format("Device : \"%s\"", nameInstruc);

                                FieldData msgBlock = new FieldData(resultActions, ARConstantsEngine.SWIPE_UP);

                                int timesSwipe = 1;

                                String operation = currentInstruction.getOperation();
                                if (operation != null && !operation.trim().isEmpty()) {
                                    try {
                                        timesSwipe = Integer.parseInt(operation.trim());
                                    } catch (NumberFormatException ignored) {
                                        appendLog("Invalid swipe count: " + operation + ". Defaulting to 1.", "warn");
                                    }
                                }

                                for (int i = 0; i < timesSwipe; i++) {
                                    //                                    androidDevice.swipeUp();
                                    // androidDevice.swipeVertical(true); // false = down
                                    // androidDevice.swipeADB(splitDTO.getDeviceId(), true);
                                }

                                // Excel Report and Log
                                performActions.logAndReport(
                                        currentCondition,
                                        true,
                                        true,
                                        blockStartTime,
                                        blockReportName,
                                        success,
                                        new String[] {ARConstantsEngine.SWIPE_UP},
                                        msgBlock,
                                        dataExcel,
                                        writerReport,
                                        "DEVICE -> SWIPE UP",
                                        String.format("SWIPE UP CALLED AT: \"%s\" : ", nameInstruc));

                                respModal = ARExecution.DialogModal.NONE;
                                continue;
                            } else if (swipeDown) {

                                String nameInstruc =
                                        "(" + currentInstruction.getId() + ") " + currentInstruction.getName();

                                resultActions = String.format("Device : \"%s\"", nameInstruc);

                                FieldData msgBlock = new FieldData(resultActions, ARConstantsEngine.SWIPE_DOWN);

                                int timesSwipe = 1;

                                String operation = currentInstruction.getOperation();
                                if (operation != null && !operation.trim().isEmpty()) {
                                    try {
                                        timesSwipe = Integer.parseInt(operation.trim());
                                    } catch (NumberFormatException ignored) {
                                        appendLog("Invalid swipe count: " + operation + ". Defaulting to 1.", "warn");
                                    }
                                }

                                for (int i = 0; i < timesSwipe; i++) {
                                    //                                    androidDevice.swipeDown();
                                    //                                    androidDevice.swipeVertical(false); // false =
                                    // down
                                    // androidDevice.swipeADB(splitDTO.getDeviceId(), false);
                                }
                                // Excel Report and Log
                                performActions.logAndReport(
                                        currentCondition,
                                        true,
                                        true,
                                        blockStartTime,
                                        blockReportName,
                                        success,
                                        new String[] {ARConstantsEngine.SWIPE_DOWN},
                                        msgBlock,
                                        dataExcel,
                                        writerReport,
                                        "DEVICE -> SWIPE DOWN",
                                        String.format("SWIPE DOWN CALLED AT: \"%s\" : ", nameInstruc));

                                respModal = ARExecution.DialogModal.NONE;
                                continue;
                            }

                            // It decides Here if ByPass as per Loop or Per IF-ELSEIF-ELSE-ENDIF blocks
                            // Does not block other executions if it fails for any reason and jumps to the beginning or
                            // Excel GOTO position block
                            if (!success
                                    && !byPassFlagLoop
                                    && currentCondition.equals(ARExecution.ConditionStatus.NONE)) {

                                // Record failure but do NOT alter execution flow
                                anyFailure = true;

                                // Reset success so execution can continue
                                success = true;

                                // Continue with next instruction
                                continue instructionLoop;
                            }

                            // It decides Here if ByPass as per Loop or Per IF-ELSEIF-ELSE-ENDIF blocks
                            if (jumpGotoError || jumpLoopError) {
                                stopAll = true;
                                break;
                            }

                            // Close Browser Action
                            if (resultActions.equalsIgnoreCase("Close Browser")) {
                                stopAll = true;
                                break;
                            }

                            // Here it Call the next block of IF, ELSIF, ELSE OR ENDIF as Per the Machine State
                            // Conditions When Pass to any of then
                            if (progressCondition.equals(ARExecution.ConditionStatus.IF_PASSED)
                                    || progressCondition.equals(ARExecution.ConditionStatus.ELSEIF_PASSED)) {
                                int jumpPassed = performActions.checkActionToJump(
                                        actions[0],
                                        progressCondition,
                                        mapConditional,
                                        parentBlockCondition,
                                        currentIndex);

                                // Any Error
                                if (jumpPassed < 0) {
                                    stopAll = true;
                                    continue blockLoop;
                                }
                                // Found Next Block
                                if (jumpPassed > 0) {
                                    currentIndex = jumpPassed;
                                    // reset all Conditional
                                    currentCondition = ARExecution.ConditionStatus.NONE;
                                    progressCondition = ARExecution.ConditionStatus.NONE;
                                    continue instructionLoop;
                                }
                            }

                            // Conditions When Fails to any of then and Look for the next Correct Block
                            if (progressCondition.equals(ARExecution.ConditionStatus.IF_FAILED)
                                    || progressCondition.equals(ARExecution.ConditionStatus.ELSEIF_FAILED)) {

                                // Goes to the next ELSEIF IF EXIST (ELSEIF index + 1);
                                int index = performActions.searchMapConditional(
                                        mapConditional,
                                        parentBlockCondition,
                                        ARExecution.ConditionStatus.ELSEIF,
                                        currentIndex,
                                        false);

                                // Goes to the next ELSE IF ELSEIF  DOES NOT EXIST  (ELSE index + 1);
                                if (index < 0) {
                                    index = performActions.searchMapConditional(
                                            mapConditional,
                                            parentBlockCondition,
                                            ARExecution.ConditionStatus.ELSE,
                                            currentIndex,
                                            true);
                                }
                                if (index < 0) {
                                    stopAll = true;
                                    continue blockLoop;
                                }
                                currentIndex = index;
                                currentCondition = ARExecution.ConditionStatus.NONE;
                                progressCondition = ARExecution.ConditionStatus.NONE;
                                continue instructionLoop;

                            } else if (progressCondition.equals(ARExecution.ConditionStatus.ELSE_FAILED)) {
                                // Goes to the ENDIF (ENDIF index + 1);
                                int index = performActions.searchMapConditional(
                                        mapConditional,
                                        parentBlockCondition,
                                        ARExecution.ConditionStatus.ENDIF,
                                        currentIndex,
                                        true);

                                if (index < 0) {
                                    stopAll = true;
                                    continue blockLoop;
                                }
                                currentIndex = index;
                                currentCondition = ARExecution.ConditionStatus.NONE;
                                progressCondition = ARExecution.ConditionStatus.NONE;
                                continue instructionLoop;
                            }
                        }

                        // Has Transversed All Columns in the Block
                        // Way Out from the Current Excel Data Row to another Block keeping the Same Excel Data Row
                        break;
                    }
                    currentBlockOrder++;
                    if (!mapExportRows.isEmpty()) {
                        addRowFromMap(mapExportRows, xExcelCurrentRow);
                        saveExcelWrite(newExcelFieldName, xExcelDataSize, writerExport, exportIndex);
                    }
                }

                currentBlockOrder = blockExcelGoto; // BLOCK DEFINED BY "DEFAULT" OR "EXCEL GOTO"
                xExcelCurrentRow++;
                //                addRowFromMap(mapExportRows);
                if (excelFieldName != null && excelFieldName.toLowerCase().endsWith(".csv")) {
                    if (Strings.isNullOrEmpty(delimiterCSV)) {
                        delimiterCSV = ",";
                    }

                    String csvContent = getBancaStatoCsvContent(delimiterCSV);
                    writeToFile(excelFieldName, csvContent);
                    if (xExcelDataSize > 1) {
                        mapExportRows = new LinkedHashMap<>();
                    }
                    excelFieldName = "";
                } else if (excelFieldName != null
                        && excelFieldName.toLowerCase().endsWith(".xlsx")) {
                    //
                    //                    writerExport.insertFieldNameAndValueLastColumn(mapExportRows, exportIndex -
                    // 1);
                    if (writerExport != null) {
                        //                        writerExport.insertCSVContentIntoExcel(columnsCSV, rowsCSV,
                        // exportIndex - 1);
                    }
                }
            }
        }

        totalExecutionTime = performActions.getTotalExecutionTime();

        if (totalExecutionTime == 0) {
            writerReport.insertTotalExecutionTimes(botJobStartTime, botJobStartTime);
        } else {
            writerReport.insertTotalExecutionTimes(botJobStartTime, System.nanoTime());
        }

        // PRINT END BASE LOG//

        String[] parts = resultActions.split("\\|", -1);

        String firstFour = String.join(
                "|",
                parts.length > 0 ? parts[0] : "",
                parts.length > 1 ? parts[1] : "",
                parts.length > 2 ? parts[2] : "",
                parts.length > 3 ? parts[3] : "");

        if (success) {
            baseLogString = blocksLoaded.get(0).getName()
                    + ARConstantsEngine.FIELDS_SEPARATOR
                    + labelsValue.getProperty(Labels.END)
                    + ARConstantsEngine.FIELDS_SEPARATOR
                    + labelsValue.getProperty(Labels.OK);

            if (isInterceptBotJob()) {
                updateRowStatusAndNotify("yellow"); // #fcba03 deep carmine yellow
                performMessage.showCustomModalDialogDragWin11TimerAuto(
                        "Bot-Job Interrupted successfully",
                        currentBotJobName,
                        "Last Execution:",
                        firstFour,
                        null,
                        false,
                        "OK",
                        null,
                        300,
                        10);
            } else {
                updateRowStatusAndNotify("green"); // #1d9c06 deep carmine green
                respModal = performMessage.showCustomModalDialogDragWin11(
                        "Bot-Job Finished - successfully",
                        currentBotJobName,
                        "Last Execution:",
                        firstFour,
                        null,
                        false,
                        "OK",
                        "Close Browser",
                        300);
            }

            performActions.setInterceptBotJob(false);
            setInterceptBotJob(false);
            isJobRunning.set(false);

        } else {
            countdownTextField.setStyle("-fx-font-size: 12px; -fx-text-fill: red;");
            countdownTextField.setText(resultActions);
            baseLogString = blocksLoaded.get(0).getName()
                    + ARConstantsEngine.FIELDS_SEPARATOR
                    + labelsValue.getProperty(Labels.END)
                    + ARConstantsEngine.FIELDS_SEPARATOR
                    + labelsValue.getProperty(Labels.KO)
                    + ARConstantsEngine.FIELDS_SEPARATOR
                    + resultActions;

            if (isInterceptBotJob()) {
                updateRowStatusAndNotify("yellow"); // #fcba03 deep carmine yellow
                performMessage.showCustomModalDialogDragWin11TimerAuto(
                        "Bot-Job Interrupted successfully",
                        currentBotJobName,
                        "Last Execution:",
                        firstFour,
                        null,
                        false,
                        "OK",
                        null,
                        300,
                        10);

            } else {
                updateRowStatusAndNotify("red"); // #FF3131 deep carmine red
                if (webElementWork) {
                    respModal = performMessage.showCustomModalDialogDragWin11(
                            "Bot-Job Finished - successfully",
                            currentBotJobName,
                            "Last Execution:",
                            firstFour,
                            null,
                            false,
                            "OK",
                            "Close Browser",
                            300);
                } else {
                    respModal = performMessage.showCustomModalDialogDragWin11(
                            "Process Execution Terminated",
                            !Strings.isNullOrEmpty(failedMessage) ? failedMessage : "Failed:",
                            "Last Execution:",
                            firstFour,
                            null,
                            true,
                            "OK",
                            "Close Browser",
                            350);
                }
            }
        }

        logLaunch.info(baseLogString);

        if (resultActions.equalsIgnoreCase("Close Browser") || respModal.equals(ARExecution.DialogModal.STOP)) {
            currentARWebDriver.getCurrentDriver().quit();
        }

        shutDownExecutorService(executorServicePreLaunch);
        performActions.setInterceptBotJob(true);
        setInterceptBotJob(false);
        isJobRunning.set(false);
        return true;
    }

    private WebElement immediateXPath(String xPath) {
        try {
            if (waitXPath == null && performActions.getCurrentDriver() != null) {
                waitXPath = new WebDriverWait(performActions.getCurrentDriver(), Duration.ofSeconds(0));
            }
            waitXPath.until(ExpectedConditions.presenceOfElementLocated(By.xpath(xPath)));
            List<WebElement> foundElementList =
                    performActions.getCurrentDriver().findElements(By.xpath(xPath));
            if (foundElementList.size() > 0) {
                return foundElementList.get(0);
            }
        } catch (TimeoutException ignored) {
        } catch (Exception ignored) {
        }
        return null;
    }

    private static class ValidationResult {
        final boolean valid;
        final String invalidReason; // null if none

        ValidationResult(boolean valid, String invalidReason) {
            this.valid = valid;
            this.invalidReason = invalidReason;
        }
    }

    private ValidationResult evaluateOperation(String actualRaw, String operator, String expectedRaw) {
        if (actualRaw == null || expectedRaw == null || operator == null) {
            return new ValidationResult(false, "Null values");
        }

        String actual = actualRaw.trim();
        String expected = expectedRaw.trim();

        switch (operator.trim()) {
            case "=":
                return new ValidationResult(actual.equalsIgnoreCase(expected), null);

            case "!=":
                return new ValidationResult(!actual.equalsIgnoreCase(expected), null);

            case ">": {
                int resp = handleGreaterThan(actual, expected);
                if (resp == 1) return new ValidationResult(true, null);
                if (resp == 0) return new ValidationResult(false, null);
                return new ValidationResult(false, "Invalid Numbers");
            }

            case "<": {
                int resp = handleLessThan(actual, expected);
                if (resp == 1) return new ValidationResult(true, null);
                if (resp == 0) return new ValidationResult(false, null);
                return new ValidationResult(false, "Invalid Numbers");
            }

            default:
                return new ValidationResult(false, "Unknown operator: " + operator);
        }
    }

    private void appendLog(String message, String style) {}

    public void readAllElementsWithWebDriver() {
        WebDriver driver = performActions.getCurrentDriver();

        if (driver == null) {
            appendLog("Please connect to device first", "warn");
            return;
        }

        appendLog("Starting XML-based deep scan (pageSource)...", "info");

        boolean activateSent = false;

        try {
            Thread.sleep(500);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }

        try {
            List<ElementDTO> results = new ArrayList<>();
            List<RenameEntry> renameReport = new ArrayList<>();

            String canonicalXml;
            try {
                String rawPageSource = driver.getPageSource();

                if (isMobileApp) {
                    canonicalXml = CanonicalXmlNormalizer.normalize(rawPageSource);
                } else {
                    // HTML -> XHTML so DocumentBuilder can parse it
                    canonicalXml = CanonicalXmlNormalizer.normalizeHtmlToXhtml(rawPageSource);

                    // ✅ WEB: parse XHTML -> Document -> extract labels/inputs/buttons/links
                    Document doc = parseXhtmlToDocument(canonicalXml);

                    // If you want, keep dedup using attribId/xpath
                    List<ElementDTO> webControls = extractWebControls(doc);

                    // Optional dedup guard (recommended if your page repeats nodes)
                    Set<String> seenKeys = new HashSet<>();
                    for (ElementDTO dto : webControls) {
                        String key = nz(dto.getAttribId()) + "||" + nz(dto.getXPath());
                        if (seenKeys.add(key)) {
                            results.add(dto);
                        }
                    }
                }
            } catch (Exception ex) {
                appendLog("driver.getPageSource() failed: " + ex.getMessage(), "error");
                return;
            }

            if (canonicalXml == null || canonicalXml.isBlank()) {
                appendLog("Empty pageSource XML; stopping.", "warn");
                return;
            }

            // Keep dedup structure if your traverse uses it
            Set<String> seenKeys = new HashSet<>(50_000);

            saveCanonicalXmlToAppFolder(canonicalXml);

            extractAllTextElementsFromCanonicalXml(canonicalXml, results);

            parseAppiumPageSourceXml(canonicalXml, results, renameReport, seenKeys);
            if (isMobileApp) {
                parseAppiumPageSourceXml(canonicalXml, results, renameReport, seenKeys);
            } else {
                parseWebPageSourceXhtml(canonicalXml, results, seenKeys);
            }

            appendLog("XML deep scan complete. Elements kept: " + results.size(), "info");

            // ---- Wrap in SplitDTO and send as before ----
            splitDTO.setType("SEARCH_TOOL");
            splitDTO.setSessionId("mobileScannerGrid");
            splitDTO.setOperationId("addPickOne");
            splitDTO.setElementDetails(results.toArray(new ElementDTO[0]));

            sendChunks(results, 25, splitDTO, webSocketSessionManager, "scannerTool", "scannerGrid");

            List<String> excludeList = List.of("optional", "blockMarked", "editMode");
            String jsonPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_DB);
            performMessage.outputJsonElementDTO(splitDTO.getElementDetails(), excludeList, "elementDTO-PG", jsonPath);

            excludeList = List.of(
                    "optional",
                    "blockMarked",
                    "editMode",
                    "id",
                    "attributeData",
                    "typeElement",
                    "customXPath",
                    "shadowRoot",
                    "nestedShadow",
                    "searchAttributeValue",
                    "attributeType",
                    "attributeValue");
            performMessage.outputJsonElementDTO(
                    splitDTO.getElementDetails(), excludeList, "AI-ElementDTO-PG", jsonPath);

            appendLog("Payload sent. Elements in payload: " + results.size(), "info");

            //            SwingUtilities.invokeLater(() -> {
            //                arObjectsLabel.setText("Objects detected: " + results.size() + ".                    ");
            //            });

            //            splitDTO.setType("REACTIVATE_BUTTONS");
            //            splitDTO.setSessionId("mobileScannerVision");
            //            splitDTO.setOperationId("activate-scanner-app");
            //            splitDTO.setElementDetails(null);
            //
            //            String jsonData = gson.toJson(splitDTO);
            //            webSocketSessionManager.sendMessageJson(
            //                    splitDTO.getHomeBankingId(), "mobile-return-server", jsonData,
            // "activate-scanner-app");

            activateSent = true;

        } catch (Exception e) {
            appendLog("XML deep scan failed: " + e.getMessage(), "error");
        } finally {
            //            if (!activateSent) {
            //                splitDTO.setType("REACTIVATE_BUTTONS");
            //                splitDTO.setSessionId("mobileScannerGrid");
            //                splitDTO.setOperationId("activate-scanner-app");
            //                splitDTO.setElementDetails(null);
            //
            //                String jsonData = gson.toJson(splitDTO);
            //                webSocketSessionManager.sendMessageJson(
            //                        splitDTO.getHomeBankingId(), "mobile-return-server", jsonData,
            // "activate-scanner-app");
            //            }
        }
    }

    // Optional: small struct to track renames (for logs/inspection)
    private static final class RenameEntry {
        final int id;
        final String originalTag;
        final String newTag;
        final String reason;

        RenameEntry(int id, String originalTag, String newTag, String reason) {
            this.id = id;
            this.originalTag = originalTag;
            this.newTag = newTag;
            this.reason = reason;
        }
    }

    private void saveCanonicalXmlToAppFolder(String canonicalXml) {
        if (canonicalXml == null || canonicalXml.isBlank()) {
            appendLog("canonicalXml is empty, nothing to save.", "warn");
            return;
        }

        try {
            String base = arPropertyManager.getProperty(ARPropertyEnum.PATH_APPIUM);
            Path baseDir = Paths.get(base, "appium-xml-dumps");
            Files.createDirectories(baseDir);

            // Timestamped filename
            String fileName = "pageSource_"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"))
                    + ".xml";

            Path filePath = baseDir.resolve(fileName);

            Files.writeString(
                    filePath,
                    canonicalXml,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            appendLog("Canonical XML saved to: " + filePath.toAbsolutePath(), "info");

        } catch (Exception ex) {
            appendLog("Failed to save canonical XML: " + ex.getMessage(), "error");
        }
    }

    // =======================================================
    // 2) PARSER: Appium pageSource XML -> ElementDTOs
    //    - Dedup via "seenKeys"
    //    - Generates: id (sequential), typeElement, xPath, someText, attribId, attribName, coordinates, attributeData
    // =======================================================
    private void parseAppiumPageSourceXml(
            String xml, List<ElementDTO> results, List<RenameEntry> renameReport, Set<String> seenKeys) {
        Document doc;
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(false);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            doc = db.parse(new InputSource(new StringReader(xml)));
        } catch (Exception ex) {
            appendLog("Failed to parse pageSource XML: " + ex.getMessage(), "warn");
            return;
        }

        Element root = doc.getDocumentElement();
        if (root == null) return;

        // We want all UI nodes under <hierarchy> (skip the hierarchy node itself)
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node n = children.item(i);
            if (n.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                traverseUiNode((Element) n, results, renameReport, seenKeys);
            }
        }
    }

    private void parseWebPageSourceXhtml(String xhtml, List<ElementDTO> results, Set<String> seenKeys) {

        Document doc;
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(false);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

            DocumentBuilder db = dbf.newDocumentBuilder();
            doc = db.parse(new InputSource(new StringReader(xhtml)));
        } catch (Exception ex) {
            appendLog("Failed to parse WEB XHTML: " + ex.getMessage(), "warn");
            return;
        }

        Element root = doc.getDocumentElement();
        if (root == null) return;

        traverseWebNode(root, results, seenKeys);
    }

    private void traverseWebNode(Element el, List<ElementDTO> results, Set<String> seenKeys) {

        String tag = nz(el.getTagName()).toLowerCase(Locale.ROOT);

        // Skip non-UI / noisy tags
        if (tag.equals("head")
                || tag.equals("script")
                || tag.equals("style")
                || tag.equals("meta")
                || tag.equals("link")) {
            recurseWebChildren(el, results, seenKeys);
            return;
        }

        String id = nz(el.getAttribute("id"));
        String name = nz(el.getAttribute("name"));
        String cls = nz(el.getAttribute("class"));
        String role = nz(el.getAttribute("role"));
        String aria = nz(el.getAttribute("aria-label"));
        String title = nz(el.getAttribute("title"));
        String href = nz(el.getAttribute("href"));
        String type = nz(el.getAttribute("type"));
        String value = nz(el.getAttribute("value"));
        String onclick = nz(el.getAttribute("onclick"));

        boolean isClickable = tag.equals("a")
                || tag.equals("button")
                || tag.equals("select")
                || tag.equals("textarea")
                || tag.equals("label")
                || (tag.equals("input") && !type.equalsIgnoreCase("hidden"))
                || !href.isEmpty()
                || !onclick.isEmpty()
                || role.equalsIgnoreCase("button")
                || role.equalsIgnoreCase("link");

        // Display text: prefer visible-ish sources
        String text = nz(el.getTextContent()).trim();
        String someText = firstNonEmpty(text, aria, title, value, name, id);
        if (isNullishText(someText)) someText = "";

        // Dedup key (WEB)
        String dedupeKey = tag + "|" + id + "|" + name + "|" + aria + "|" + title + "|" + href + "|" + someText;
        if (!seenKeys.add(dedupeKey)) {
            recurseWebChildren(el, results, seenKeys);
            return;
        }

        // Build WEB XPath (prefer id/name/aria, else structural)
        String xPath = buildWebXPath(el, tag, id, name, aria, someText);

        // attribId (WEB) - keep simple but stable
        String attribId = buildWebAttribId(tag, id, name, aria);

        // coords not available from pageSource -> leave 0
        String coords = "0.00,0.00";

        // attributeData for WEB
        List<AttributeData> attrs = new ArrayList<>();
        attrs.add(new AttributeData("tag", tag));
        if (!id.isEmpty()) attrs.add(new AttributeData("id", id));
        if (!name.isEmpty()) attrs.add(new AttributeData("name", name));
        if (!cls.isEmpty()) attrs.add(new AttributeData("class", cls));
        if (!role.isEmpty()) attrs.add(new AttributeData("role", role));
        if (!aria.isEmpty()) attrs.add(new AttributeData("aria-label", aria));
        if (!title.isEmpty()) attrs.add(new AttributeData("title", title));
        if (!href.isEmpty()) attrs.add(new AttributeData("href", href));
        if (!type.isEmpty()) attrs.add(new AttributeData("type", type));
        if (!value.isEmpty()) attrs.add(new AttributeData("value", value));
        attrs.add(new AttributeData("clickable", isClickable ? "true" : "false"));

        ElementDTO dto = new ElementDTO();
        dto.setId(results.size() + 1);
        dto.setTypeElement("tagName-Found");
        dto.setTagName(tag); // WEB tagName = real HTML tag
        dto.setXPath(xPath);
        dto.setAttribId(attribId);
        dto.setSomeText(someText);
        dto.setAttribName("");
        dto.setCoordinates(coords);
        dto.setAttributeData(attrs.toArray(new AttributeData[0]));

        // WEB: clear Android-specific fields
        dto.setAndroidData(null);

        dto.setCustomXPath("");
        dto.setIFrameXPath("");
        dto.setShadowHost("");
        dto.setShadowRoot("false");
        dto.setNestedShadow("false");
        dto.setCssSelector(""); // optional: you can add later
        dto.setAttributeValue("");
        dto.setAttributeType("");
        dto.setSearchAttributeValue("");

        results.add(dto);

        // IMPORTANT: do NOT call addVariantsLikeSecret() for web.
        // It creates fake tagName "link" which collides with real <link> and is also not needed.

        recurseWebChildren(el, results, seenKeys);
    }

    private void recurseWebChildren(Element el, List<ElementDTO> results, Set<String> seenKeys) {
        NodeList kids = el.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            org.w3c.dom.Node n = kids.item(i);
            if (n.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                traverseWebNode((Element) n, results, seenKeys);
            }
        }
    }

    private String buildWebAttribId(String tag, String id, String name, String aria) {
        if (id != null && !id.isEmpty()) return "//*[@id=\"" + id.replace("\"", "") + "\"]";
        if (name != null && !name.isEmpty()) return "//" + tag + "[@name=\"" + name.replace("\"", "") + "\"]";
        if (aria != null && !aria.isEmpty()) return "//" + tag + "[@aria-label=\"" + aria.replace("\"", "") + "\"]";
        return "//" + tag;
    }

    private String buildWebXPath(Element el, String tag, String id, String name, String aria, String text) {
        if (id != null && !id.isEmpty()) {
            return "//*[@id='" + escapeXPathSQ(id) + "']";
        }
        if (name != null && !name.isEmpty()) {
            return "//" + tag + "[@name='" + escapeXPathSQ(name) + "']";
        }
        if (aria != null && !aria.isEmpty()) {
            return "//" + tag + "[@aria-label='" + escapeXPathSQ(aria) + "']";
        }
        // optional: text match if short
        if (text != null && !text.isEmpty() && text.length() <= 60 && !isNullishText(text)) {
            return "//" + tag + "[normalize-space(.)='" + escapeXPathSQ(text) + "']";
        }
        return buildWebStructuralPathWithIndex(el);
    }

    private String buildWebStructuralPathWithIndex(Element el) {
        ArrayDeque<String> parts = new ArrayDeque<>();
        org.w3c.dom.Node cur = el;

        while (cur != null && cur.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
            Element ce = (Element) cur;
            String tag = nz(ce.getTagName()).toLowerCase(Locale.ROOT);

            int idx = computeSiblingIndexSameTag(ce, tag);
            parts.addFirst("/" + tag + "[" + idx + "]");

            cur = cur.getParentNode();
            // stop at html root
            if (cur != null
                    && cur.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE
                    && "html".equalsIgnoreCase(((Element) cur).getTagName())) {
                parts.addFirst("/html[1]");
                break;
            }
        }

        StringBuilder sb = new StringBuilder();
        for (String p : parts) sb.append(p);
        return sb.length() > 0 ? sb.toString() : "/";
    }

    private int computeSiblingIndexSameTag(Element el, String tag) {
        org.w3c.dom.Node parent = el.getParentNode();
        if (parent == null) return 1;

        NodeList siblings = parent.getChildNodes();
        int count = 0;

        for (int i = 0; i < siblings.getLength(); i++) {
            org.w3c.dom.Node n = siblings.item(i);
            if (n.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) continue;

            Element sib = (Element) n;
            String sibTag = nz(sib.getTagName()).toLowerCase(Locale.ROOT);

            if (tag.equals(sibTag)) count++;
            if (sib == el) return Math.max(1, count);
        }
        return 1;
    }

    private void addVariantsLikeSecret(ElementDTO dto, List<ElementDTO> results) {
        try {
            ElementDTO dtoInput = gson.fromJson(gson.toJson(dto), ElementDTO.class);
            dtoInput.setId(results.size() + 1);
            dtoInput.setTagName("input");
            VisionElementMapper.overrideClassAttribute(dtoInput, "android.widget.EditText");
            results.add(dtoInput);

            ElementDTO dtoButton = gson.fromJson(gson.toJson(dto), ElementDTO.class);
            dtoButton.setId(results.size() + 1);
            dtoButton.setTagName("button");
            VisionElementMapper.overrideClassAttribute(dtoButton, "android.widget.Button");
            results.add(dtoButton);

            ElementDTO dtoLabel = gson.fromJson(gson.toJson(dto), ElementDTO.class);
            dtoLabel.setId(results.size() + 1);
            dtoLabel.setTagName("label");
            VisionElementMapper.overrideClassAttribute(dtoLabel, "android.widget.TextView");
            results.add(dtoLabel);

            ElementDTO dtoLink = gson.fromJson(gson.toJson(dto), ElementDTO.class);
            dtoLink.setId(results.size() + 1);
            dtoLink.setTagName("link");
            VisionElementMapper.overrideClassAttribute(dtoLink, "android.widget.ImageView");
            results.add(dtoLink);

        } catch (Exception cloneEx) {
            appendLog("Variant clone failed for XML element #" + dto.getId() + ": " + cloneEx.getMessage(), "warn");
        }
    }

    // =======================================================
    // traverseUiNode (ONLY clickables, but steals nested TextView text/desc)
    // Ready to copy/paste
    // =======================================================
    // =======================================================
    // traverseUiNode (ONLY clickables, steals nested TextView text/desc)
    // Uses recurseChildren(...) consistently
    // =======================================================
    private void traverseUiNode(
            Element el, List<ElementDTO> results, List<RenameEntry> renameReport, Set<String> seenKeys) {

        // Resolve class
        String cls = nz(el.getAttribute("class"));
        if (cls.isEmpty()) cls = nz(el.getTagName());
        if (cls.isEmpty()) cls = "android.view.View";

        // Ignore ProgressBar as in secret/scanContext
        if ("android.widget.ProgressBar".equals(cls)) {
            recurseChildren(el, results, renameReport, seenKeys);
            return;
        }

        // RAW attributes (ONLY these are allowed for XPath/attribId)
        String resId = nz(el.getAttribute("resource-id"));
        String rawText = nz(el.getAttribute("text"));
        String rawDesc = nz(el.getAttribute("content-desc"));
        String clickableStr = nz(el.getAttribute("clickable"));
        String enabled = nz(el.getAttribute("enabled"));
        String password = nz(el.getAttribute("password"));
        String bounds = nz(el.getAttribute("bounds"));
        String focused = nz(el.getAttribute("focused"));

        boolean isClickable = "true".equalsIgnoreCase(clickableStr);

        // ✅ ALSO include drawer group rows even if clickable="false"
        boolean includeAsAction = isClickable || isDrawerGroupContainer(el, resId);

        if (includeAsAction) {

            // -----------------------------
            // 1) DISPLAY TEXT (frontend only)
            // -----------------------------
            String effectiveText = rawText;
            String effectiveDesc = rawDesc;

            boolean localTextNullish = effectiveText.isEmpty() || isNullishText(effectiveText);
            boolean localDescNullish = effectiveDesc.isEmpty() || isNullishText(effectiveDesc);

            // If container has no text/desc, steal from nested children (TextView preferred)
            if (localTextNullish && localDescNullish) {
                NestedText nt = extractNestedText(el);
                if (!nt.text.isEmpty()) effectiveText = nt.text;
                if (!nt.desc.isEmpty()) effectiveDesc = nt.desc;
            }

            // someText (frontend-only)
            String someText = !effectiveText.isEmpty() ? effectiveText : effectiveDesc;
            if (isNullishText(someText)) {
                someText = "";
            }

            // Semantic fallbacks for toolbar icons (DISPLAY only)
            if (someText.isEmpty()) {
                if (isBackElement(cls, resId, rawDesc)) {
                    someText = "back";
                } else if (isMenuElement(cls, resId)) {
                    someText = "menu";
                }
            }

            // Menu fallback for empty labels (hamburger)
            if (someText.isEmpty() && isClickable && isMenuElement(cls, resId)) {
                someText = "menu";
            }

            // ✅ Prefix menu context (DO NOT affect XPath / attribId)
            boolean inMenu = isInDrawerMenu(el);
            if (inMenu && !someText.isEmpty()) {
                someText = "MENU -> " + someText;
            }

            // -----------------------------
            // 2) DEDUPE KEY (RAW only)
            // -----------------------------
            // Important for drawer: many items share resource-id="...:id/container".
            // Bounds disambiguates; if bounds missing, structural XPath will.
            String dedupeKey = cls + "|" + resId + "|" + rawText + "|" + rawDesc + "|" + bounds;
            if (seenKeys.add(dedupeKey)) {

                // -----------------------------
                // 3) XPATH + attribId (RAW only)
                // -----------------------------
                String xPath = buildSafeXPathWithIndex(el, cls, resId, rawText, rawDesc, bounds);

                String attribId = buildAttribId(cls, resId, rawText);
                if ((bounds == null || bounds.isEmpty()) && (resId == null || resId.isEmpty())) {
                    attribId = xPath; // last fallback uniqueness
                }

                String coords = computeCoordinatesFromBounds(bounds);

                // Tag mapping should use RAW text/desc too (not stolen)
                String mappedTag = mapTagName(cls, xPath, includeAsAction, rawText, rawDesc);

                // attributeData should store RAW values (so locators stay honest)
                List<AttributeData> attrs = new ArrayList<>();
                attrs.add(new AttributeData("class", cls));
                attrs.add(new AttributeData("resource-id", resId.isEmpty() ? "null" : resId));
                attrs.add(new AttributeData("text", rawText.isEmpty() ? "null" : rawText));
                attrs.add(new AttributeData("content-desc", rawDesc.isEmpty() ? "null" : rawDesc));
                attrs.add(new AttributeData("clickable", boolString(clickableStr)));
                attrs.add(new AttributeData("enabled", boolString(enabled)));
                attrs.add(new AttributeData("focused", boolString(focused)));
                if (!password.isEmpty()) attrs.add(new AttributeData("password", boolString(password)));
                attrs.add(new AttributeData("bounds", bounds.isEmpty() ? "null" : bounds));

                ElementDTO dto = new ElementDTO();
                dto.setId(results.size() + 1);
                dto.setTypeElement("tagName-Found");
                dto.setTagName(mappedTag.toLowerCase());

                dto.setXPath(xPath);
                dto.setAttribId(attribId);

                // ✅ DISPLAY ONLY
                dto.setSomeText(someText);

                dto.setAttribName("");
                dto.setCoordinates(coords);
                dto.setAttributeData(attrs.toArray(new AttributeData[0]));

                dto.setCustomXPath("");
                dto.setIFrameXPath("");
                dto.setShadowHost("");
                dto.setShadowRoot("false");
                dto.setNestedShadow("false");
                dto.setCssSelector("");
                dto.setAttributeValue("");
                dto.setAttributeType("");
                dto.setSearchAttributeValue("");

                results.add(dto);

                // ⚠️ If you want ONLY clickables, consider disabling variants here.
                addVariantsLikeSecret(dto, results);
            }
        }

        recurseChildren(el, results, renameReport, seenKeys);
    }

    private void recurseChildren(
            Element el, List<ElementDTO> results, List<RenameEntry> renameReport, Set<String> seenKeys) {
        NodeList kids = el.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            org.w3c.dom.Node n = kids.item(i);
            if (n.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                traverseUiNode((Element) n, results, renameReport, seenKeys);
            }
        }
    }

    private boolean isDrawerGroupContainer(Element el, String resId) {
        if (resId == null) return false;

        // Your drawer rows all share resource-id "...:id/container"
        if (!resId.toLowerCase(Locale.ROOT).endsWith(":id/container")) {
            return false;
        }

        // If it contains a drawer_group_title with non-empty text => treat as a menu group item
        ArrayDeque<Element> q = new ArrayDeque<>();
        q.add(el);

        while (!q.isEmpty()) {
            Element cur = q.removeFirst();

            String childResId = nz(cur.getAttribute("resource-id"));
            String childText = nz(cur.getAttribute("text"));

            if (!childResId.isEmpty()
                    && childResId.toLowerCase(Locale.ROOT).contains("drawer_group_title")
                    && !childText.isEmpty()
                    && !isNullishText(childText)) {
                return true;
            }

            NodeList kids = cur.getChildNodes();
            for (int i = 0; i < kids.getLength(); i++) {
                org.w3c.dom.Node n = kids.item(i);
                if (n.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                    q.add((Element) n);
                }
            }
        }
        return false;
    }

    private boolean isBackElement(String cls, String resId, String desc) {
        String id = (resId == null) ? "" : resId.toLowerCase(Locale.ROOT);
        String cd = (desc == null) ? "" : desc.toLowerCase(Locale.ROOT);

        // resource-id patterns
        if (id.contains("back")
                || id.contains("up")
                || id.contains("navigate_up")
                || id.contains("nav_up")
                || id.contains("action_back")
                || id.contains("drawer_back")) {
            return true;
        }

        // content-desc patterns (common for toolbar icons)
        if (cd.contains("back") || cd.contains("navigate up") || cd.contains("up")) {
            return true;
        }

        // Often used as icon button (optional heuristic)
        if ((cls.endsWith("ImageView") || cls.endsWith("ImageButton") || cls.endsWith("FrameLayout"))
                && (id.contains("arrow") || id.contains("chevron"))) {
            return true;
        }

        return false;
    }

    private boolean isMenuElement(String cls, String resId) {
        if (resId == null) return false;

        String id = resId.toLowerCase(Locale.ROOT);

        // Common menu identifiers
        if (id.contains("menu") || id.contains("drawer") || id.contains("hamburger") || id.contains("nav")) {
            return true;
        }

        // Clickable FrameLayout/ImageView used as toolbar menu
        if ((cls.endsWith("FrameLayout") || cls.endsWith("ImageView")) && id.contains("drawer")) {
            return true;
        }

        return false;
    }

    private boolean isInDrawerMenu(Element el) {
        if (el == null) return false;

        // First: identify if THIS element is itself a drawer structural node
        String selfResId = nz(el.getAttribute("resource-id")).toLowerCase(Locale.ROOT);

        if (isDrawerRootId(selfResId)) {
            return false; // drawer root itself is NOT "in menu"
        }

        // Now: walk ancestors ONLY
        org.w3c.dom.Node parent = el.getParentNode();

        while (parent != null && parent.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
            Element pe = (Element) parent;

            String resId = nz(pe.getAttribute("resource-id")).toLowerCase(Locale.ROOT);

            if (isDrawerRootId(resId)) {
                return true; // element is a child of drawer
            }

            parent = parent.getParentNode();

            // stop at hierarchy root
            if (parent != null
                    && parent.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE
                    && "hierarchy".equalsIgnoreCase(((Element) parent).getTagName())) {
                break;
            }
        }
        return false;
    }

    private boolean isDrawerRootId(String resId) {
        if (resId == null || resId.isEmpty()) return false;

        return resId.endsWith(":id/drawer") || resId.endsWith(":id/drawer_recyclerview");
    }

    // =======================================================
    // 3) SMALL HELPERS (copy/paste)
    // =======================================================

    // =======================================================
    // Nested text extractor (required by traverseUiNode)
    // Prefers TextView[@text], falls back to any @text, then @content-desc
    // =======================================================
    private static final class NestedText {
        final String text;
        final String desc;

        NestedText(String text, String desc) {
            this.text = text == null ? "" : text;
            this.desc = desc == null ? "" : desc;
        }
    }

    private NestedText extractNestedText(Element container) {
        ArrayDeque<Element> q = new ArrayDeque<>();
        q.add(container);

        String bestTextViewText = "";
        String bestAnyText = "";
        String bestAnyDesc = "";

        while (!q.isEmpty()) {
            Element cur = q.removeFirst();

            String cls = nz(cur.getAttribute("class"));
            String t = nz(cur.getAttribute("text"));
            String d = nz(cur.getAttribute("content-desc"));

            if (!t.isEmpty() && !isNullishText(t)) {
                if ("android.widget.TextView".equals(cls) && bestTextViewText.isEmpty()) {
                    bestTextViewText = t;
                } else if (bestAnyText.isEmpty()) {
                    bestAnyText = t;
                }
            }

            if (!d.isEmpty() && !isNullishText(d) && bestAnyDesc.isEmpty()) {
                bestAnyDesc = d;
            }

            // Early exit: we found best possible (TextView text)
            if (!bestTextViewText.isEmpty()) break;

            NodeList kids = cur.getChildNodes();
            for (int i = 0; i < kids.getLength(); i++) {
                org.w3c.dom.Node n = kids.item(i);
                if (n.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                    q.add((Element) n);
                }
            }
        }

        String chosenText = !bestTextViewText.isEmpty() ? bestTextViewText : bestAnyText;
        return new NestedText(chosenText, bestAnyDesc);
    }

    private String boolString(String v) {
        if (v == null || v.isBlank()) return "false";
        return "true".equalsIgnoreCase(v) ? "true" : "false";
    }

    private String buildSafeXPathWithIndex(
            Element el, String cls, String resId, String text, String desc, String bounds) {

        String safeClass = (cls == null || cls.isEmpty()) ? "android.view.View" : cls;

        boolean hasResId = resId != null && !resId.isEmpty();
        boolean hasBounds = bounds != null && !bounds.isEmpty();

        String predicate = null;
        if (hasResId) {
            predicate = "@resource-id='" + escapeXPathSQ(resId) + "'";
        } else if (desc != null && !desc.isEmpty() && !isNullishText(desc)) {
            predicate = "@content-desc='" + escapeXPathSQ(desc) + "'";
        } else if (text != null && !text.isEmpty() && !isNullishText(text)) {
            predicate = "@text='" + escapeXPathSQ(text) + "'";
        }

        // 1) If we have predicate + bounds, return immediately
        if (predicate != null && hasBounds) {
            return "//" + safeClass + "[" + predicate + " and @bounds='" + escapeXPathSQ(bounds) + "']";
        }

        // 2) If we have bounds only, return immediately
        if (predicate == null && hasBounds) {
            return "//" + safeClass + "[@bounds='" + escapeXPathSQ(bounds) + "']";
        }

        // 3) If we have predicate only, return (short & stable)
        if (predicate != null) {
            return "//" + safeClass + "[" + predicate + "]";
        }

        // 4) Otherwise fallback to structural (only here)
        return buildStructuralPathWithIndex(el);
    }

    private String buildStructuralPathWithIndex(Element el) {
        // Build absolute-ish (but stable) path from root element downwards,
        // using class names + sibling index among same-class siblings.
        ArrayDeque<String> parts = new ArrayDeque<>();

        org.w3c.dom.Node cur = el;

        while (cur != null && cur.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
            Element ce = (Element) cur;

            String cls = nz(ce.getAttribute("class"));
            if (cls.isEmpty()) cls = nz(ce.getTagName());
            if (cls.isEmpty()) cls = "android.view.View";

            int index = computeSiblingIndexSameClass(ce, cls);

            // Use /<class>[<idx>] style
            parts.addFirst("/" + cls + "[" + index + "]");

            cur = cur.getParentNode();

            // Stop once we reach <hierarchy> (the document root)
            if (cur != null
                    && cur.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE
                    && "hierarchy".equalsIgnoreCase(((Element) cur).getTagName())) {
                break;
            }
        }

        // Start at //hierarchy (Appium root) then append
        StringBuilder sb = new StringBuilder("//hierarchy");
        for (String p : parts) sb.append(p);

        return sb.toString();
    }

    private int computeSiblingIndexSameClass(Element el, String cls) {
        org.w3c.dom.Node parent = el.getParentNode();
        if (parent == null) return 1;

        NodeList siblings = parent.getChildNodes();
        int count = 0;

        for (int i = 0; i < siblings.getLength(); i++) {
            org.w3c.dom.Node n = siblings.item(i);
            if (n.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) continue;

            Element sib = (Element) n;
            String sibCls = nz(sib.getAttribute("class"));
            if (sibCls.isEmpty()) sibCls = nz(sib.getTagName());
            if (sibCls.isEmpty()) sibCls = "android.view.View";

            if (cls.equals(sibCls)) {
                count++;
            }

            // Once we reach the current element, that count is its 1-based index
            if (sib == el) {
                return Math.max(1, count);
            }
        }
        return 1;
    }

    // XPath escaping for single quotes
    private String escapeXPathSQ(String s) {
        if (s == null) return "";
        return s.replace("'", "&apos;");
    }

    // Helper: text considered "nullish" if null, empty, whitespace or literal "null"
    private static boolean isNullishText(String s) {
        if (s == null) return true;
        String t = s.trim();
        return t.isEmpty() || t.equalsIgnoreCase("null") || t.equalsIgnoreCase("(null)");
    }

    public void sendChunks(
            List<ElementDTO> elements,
            int chunkSize,
            SplitDTO splitDTO,
            WebSocketSessionManager webSocketSessionManager,
            String server,
            String routingKey) {
        if (elements == null || elements.isEmpty()) {
            appendLog("No elements to send.", "warn");
            return;
        }

        appendLog("Sending " + elements.size() + " elements in chunks of " + chunkSize, "info");

        for (int i = 0; i < elements.size(); i += chunkSize) {

            int end = Math.min(i + chunkSize, elements.size());
            List<ElementDTO> chunk = elements.subList(i, end);

            // update DTO
            splitDTO.setElementDetails(chunk.toArray(new ElementDTO[0]));

            // serialize
            String jsonData = new Gson().toJson(splitDTO);

            // log
            appendLog("Sending chunk " + (i / chunkSize + 1) + " containing " + chunk.size() + " elements", "info");

            // send
            webSocketSessionManager.sendMessageJson(0, server, jsonData, routingKey);
        }
    }

    private String buildAttribId(String cls, String resId, String text) {
        StringBuilder sb = new StringBuilder();
        sb.append("//").append(cls);

        boolean first = true;

        if (resId != null && !resId.isEmpty()) {
            sb.append(first ? "[@" : " and @")
                    .append("resource-id=\"")
                    .append(resId.replace("\"", ""))
                    .append("\"");
            first = false;
        }
        if (text != null && !text.isEmpty()) {
            sb.append(first ? "[@" : " and @")
                    .append("text=\"")
                    .append(text.replace("\"", ""))
                    .append("\"");
            first = false;
        }

        if (!first) sb.append("]");
        return sb.toString();
    }

    // =======================================================
    // Tag mapping (clickable-aware, closer to secret rules)
    // Now: ✅ if clickable (and not input), default is "button"
    // =======================================================
    private String mapTagName(String cls, String xPath, boolean isClickable, String text, String desc) {
        boolean xpathSaysButton = xPath != null && xPath.contains("android.widget.Button");

        String someText = (text != null && !text.isEmpty()) ? text : (desc == null ? "" : desc);
        boolean nullish = isNullishText(someText);

        // ---- Strong class rules ----
        // input always stays input even if clickable
        if ("android.widget.EditText".equals(cls) || cls.endsWith("EditText")) {
            return "input";
        }

        // Buttons
        if ("android.widget.Button".equals(cls) || xpathSaysButton) {
            return "button";
        }
        if ("android.widget.ImageButton".equals(cls) || cls.endsWith("ImageButton")) {
            return "button";
        }

        // ImageView: many are icon-buttons when clickable
        if ("android.widget.ImageView".equals(cls) || cls.endsWith("ImageView")) {
            return isClickable ? "button" : "label";
        }

        // TextView: clickable -> button, else label
        if ("android.widget.TextView".equals(cls) || cls.endsWith("TextView")) {
            return isClickable ? "button" : "label";
        }

        // Spinner-like: clickable -> button, else label
        if ("android.widget.Spinner".equals(cls) || cls.endsWith("Spinner")) {
            return isClickable ? "button" : "label";
        }

        // ---- Containers / generic views ----
        // If container is clickable, treat it as a button (your requirement),
        // otherwise label. (nullish not needed anymore for the decision)
        if (cls.endsWith("LinearLayout")
                || cls.endsWith("FrameLayout")
                || cls.endsWith("RelativeLayout")
                || cls.endsWith("ConstraintLayout")
                || cls.endsWith("ViewGroup")
                || cls.endsWith("ScrollView")
                || "android.view.View".equals(cls)
                || cls.endsWith("View")) {
            return isClickable ? "button" : "label";
        }

        // ---- Final fallback ----
        return isClickable ? "button" : "label";
    }

    private String computeCoordinatesFromBounds(String bounds) {
        int[] b = parseBounds(bounds);
        if (b == null) return "0.00,0.00";
        int w = b[2] - b[0];
        int h = b[3] - b[1];
        if (w <= 0 || h <= 0) return "0.00,0.00";
        int cx = b[0] + w / 2;
        int cy = b[1] + h / 2;
        return String.format(Locale.US, "%.2f,%.2f", (double) cx, (double) cy);
    }

    private static int[] parseBounds(String bounds) {
        // format: [x1,y1][x2,y2]
        // returns {x1,y1,x2,y2} or null if invalid
        try {
            if (bounds == null || bounds.isEmpty()) return null;
            String cleaned = bounds.replace("[", "").replace("]", ",");
            String[] parts = cleaned.split(",");
            if (parts.length < 4) return null;
            int x1 = Integer.parseInt(parts[0].trim());
            int y1 = Integer.parseInt(parts[1].trim());
            int x2 = Integer.parseInt(parts[2].trim());
            int y2 = Integer.parseInt(parts[3].trim());
            return new int[] {x1, y1, x2, y2};
        } catch (Exception ignore) {
            return null;
        }
    }

    public List<ElementDTO> extractAllTextElementsFromCanonicalXml(String canonicalXml, List<ElementDTO> results) {
        if (canonicalXml == null || canonicalXml.isBlank()) {
            appendLog("Canonical XML is empty", "warn");
            return results;
        }

        Document doc;
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(false);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            doc = db.parse(new InputSource(new StringReader(canonicalXml)));
        } catch (Exception ex) {
            appendLog("Failed to parse canonical XML: " + ex.getMessage(), "error");
            return results;
        }

        Element root = doc.getDocumentElement();
        if (root == null) return results;

        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node n = children.item(i);
            if (n.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                traverseForTextOnly((Element) n, results);
            }
        }

        appendLog("Text-only extraction complete. Elements found: " + results.size(), "info");
        return results;
    }

    private void traverseForTextOnly(Element el, List<ElementDTO> results) {

        String tag = nz(el.getTagName()).toLowerCase();

        // Skip non-visible-content tags (DOM-only heuristic)
        if (tag.equals("script") || tag.equals("style") || tag.equals("noscript")) return;

        // HTML "text": for inputs use value/placeholder; otherwise textContent
        String rawText = extractHtmlText(el);

        // Keep ONLY elements with meaningful text
        if (!rawText.isEmpty() && !isNullishText(rawText)) {

            String cls = nz(el.getAttribute("class"));
            if (cls.isEmpty()) cls = tag;

            String id = nz(el.getAttribute("id"));
            String name = nz(el.getAttribute("name"));
            String ariaLabel = nz(el.getAttribute("aria-label"));
            String title = nz(el.getAttribute("title"));
            String alt = nz(el.getAttribute("alt"));
            String href = nz(el.getAttribute("href"));
            String role = nz(el.getAttribute("role"));
            String onclick = nz(el.getAttribute("onclick"));
            String tabindex = nz(el.getAttribute("tabindex"));

            boolean clickable = isHtmlClickable(tag, href, onclick, role, tabindex);

            // Build XPath for HTML (prefer id, then stable attributes)
            String xPath = buildSafeHtmlXPathWithIndex(el, tag, id, cls, rawText, ariaLabel);

            // attribId: use id if exists; otherwise XPath
            String attribId = !id.isEmpty() ? id : xPath;

            // No bounds in DOM-only parsing
            String coords = "null";

            List<AttributeData> attrs = new ArrayList<>();
            attrs.add(new AttributeData("tag", tag));
            attrs.add(new AttributeData("id", id.isEmpty() ? "null" : id));
            attrs.add(new AttributeData("class", cls.isEmpty() ? "null" : cls));
            attrs.add(new AttributeData("name", name.isEmpty() ? "null" : name));
            attrs.add(new AttributeData("aria-label", ariaLabel.isEmpty() ? "null" : ariaLabel));
            attrs.add(new AttributeData("title", title.isEmpty() ? "null" : title));
            attrs.add(new AttributeData("alt", alt.isEmpty() ? "null" : alt));
            attrs.add(new AttributeData("href", href.isEmpty() ? "null" : href));
            attrs.add(new AttributeData("role", role.isEmpty() ? "null" : role));
            attrs.add(new AttributeData("clickable", String.valueOf(clickable)));
            attrs.add(new AttributeData("text", rawText));

            ElementDTO dto = new ElementDTO();
            dto.setId(results.size() + 1);
            dto.setTypeElement("tagName-Found");
            dto.setTagName(mapTagName(tag, xPath, clickable, rawText, ariaLabel));
            dto.setXPath(xPath);
            dto.setAttribId(attribId);
            dto.setSomeText(rawText);

            dto.setAttribName("");
            dto.setCoordinates(coords);
            dto.setAttributeData(attrs.toArray(new AttributeData[0]));

            dto.setCustomXPath("");
            dto.setIFrameXPath("");
            dto.setShadowHost("");
            dto.setShadowRoot("false");
            dto.setNestedShadow("false");
            dto.setCssSelector(""); // optional: you can build from id/class if you want
            dto.setAttributeValue("");
            dto.setAttributeType("");
            dto.setSearchAttributeValue("");

            results.add(dto);
        }

        // Continue traversal
        org.w3c.dom.Node child = el.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                traverseForTextOnly((Element) child, results);
            }
            child = child.getNextSibling();
        }
    }

    private String extractHtmlText(Element el) {
        String tag = nz(el.getTagName()).toLowerCase();

        // For inputs, text is in value/placeholder
        if (tag.equals("input") || tag.equals("textarea")) {
            String v = nz(el.getAttribute("value"));
            if (!v.isEmpty()) return normalizeSpace(v);
            String ph = nz(el.getAttribute("placeholder"));
            if (!ph.isEmpty()) return normalizeSpace(ph);
            // fallback
        }
        String txt = nz(el.getTextContent());
        return normalizeSpace(txt);
    }

    private boolean isHtmlClickable(String tag, String href, String onclick, String role, String tabindex) {
        if (tag == null) return false;
        tag = tag.toLowerCase();

        // Native clickable HTML elements
        if (tag.equals("a") && !href.isEmpty()) return true;
        if (tag.equals("button")) return true;
        if (tag.equals("input")) return true;
        if (tag.equals("select")) return true;
        if (tag.equals("textarea")) return true;

        // JS or accessibility-based clickability
        if (!onclick.isEmpty()) return true;
        if ("button".equalsIgnoreCase(role)) return true;
        if ("link".equalsIgnoreCase(role)) return true;

        // Focusable elements are often interactive
        if (!tabindex.isEmpty()) return true;

        return false;
    }

    private String buildSafeHtmlXPathWithIndex(
            Element el, String tag, String id, String cls, String text, String ariaLabel) {
        // Strong preference: //*[@id='...']
        if (!id.isEmpty()) {
            return "//*[@" + "id='" + escapeXPathLiteral(id) + "']";
        }

        // Next: aria-label / title / name / class + index
        // Use your existing buildSafeXPathWithIndex pattern but with HTML attributes.
        // Minimal example (you likely already have an index builder):
        return buildXPathByTagAndIndex(el, tag);
    }

    /**
     * Builds an XPath like:
     * /html[1]/body[1]/div[2]/span[1]
     *
     * It uses tag names + 1-based index among same-tag siblings.
     * Works on an org.w3c.dom.Element tree.
     */
    private String buildXPathByTagAndIndex(Element el, String tagIgnored) {
        if (el == null) return "";

        StringBuilder path = new StringBuilder();
        org.w3c.dom.Node current = el;

        while (current != null && current.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
            Element curEl = (Element) current;

            String tag = nz(curEl.getTagName());
            if (tag.isEmpty()) tag = "*";

            int index = getIndexAmongSameTagSiblings(curEl);

            // prepend segment
            String segment = "/" + tag + "[" + index + "]";
            path.insert(0, segment);

            current = current.getParentNode();
            if (current != null && current.getNodeType() == org.w3c.dom.Node.DOCUMENT_NODE) break;
        }
        return path.toString();
    }

    public List<ElementDTO> extractWebControls(Document doc) {
        List<ElementDTO> results = new ArrayList<>();
        Element root = doc.getDocumentElement();
        traverseWebControls(root, results);
        return results;
    }

    private void traverseWebControls(Element el, List<ElementDTO> results) {
        String tag = nz(el.getTagName()).toLowerCase();

        // skip non-content tags
        if (tag.equals("script") || tag.equals("style") || tag.equals("noscript")) {
            return;
        }

        if (isWantedWebElement(tag, el)) {
            ElementDTO dto = buildWebElementDTO(el, tag, results.size() + 1);
            if (dto != null) {
                results.add(dto);
            }
        }

        // DFS traversal
        org.w3c.dom.Node child = el.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                traverseWebControls((Element) child, results);
            }
            child = child.getNextSibling();
        }
    }

    private boolean isWantedWebElement(String tag, Element el) {
        if (tag.equals("label")) return true;
        if (tag.equals("input") || tag.equals("textarea") || tag.equals("select")) return true;
        if (tag.equals("button")) return true;
        if (tag.equals("a")) return true;
        // optional: include elements that act like buttons
        String role = nz(el.getAttribute("role"));
        String onclick = nz(el.getAttribute("onclick"));
        if ("button".equalsIgnoreCase(role) || "link".equalsIgnoreCase(role)) return true;
        if (!onclick.isEmpty()) return true;
        return false;
    }

    private ElementDTO buildWebElementDTO(Element el, String tag, int idSeq) {

        // Common attributes
        String id = nz(el.getAttribute("id"));
        String name = nz(el.getAttribute("name"));
        String cls = nz(el.getAttribute("class"));
        String role = nz(el.getAttribute("role"));
        String tabindex = nz(el.getAttribute("tabindex"));
        String ariaLabel = nz(el.getAttribute("aria-label"));
        String title = nz(el.getAttribute("title"));
        String disabled = nz(el.getAttribute("disabled"));

        // Element text (labels/buttons/links)
        String text = normalizeSpace(el.getTextContent());

        // Input specific
        String type = "";
        String value = "";
        String placeholder = "";
        if (tag.equals("input")) {
            type = nz(el.getAttribute("type")).toLowerCase();
            value = nz(el.getAttribute("value"));
            placeholder = nz(el.getAttribute("placeholder"));
        } else if (tag.equals("textarea")) {
            value = normalizeSpace(el.getTextContent());
            placeholder = nz(el.getAttribute("placeholder"));
        }

        // Link specific
        String href = "";
        if (tag.equals("a")) {
            href = nz(el.getAttribute("href"));
        }

        // Label specific (for="...")
        String forId = "";
        if (tag.equals("label")) {
            forId = nz(el.getAttribute("for"));
        }

        // For inputs: try to find label text by matching <label for="id">
        String linkedLabel = "";
        if ((tag.equals("input") || tag.equals("textarea") || tag.equals("select")) && !id.isEmpty()) {
            linkedLabel = findLabelForInput(el.getOwnerDocument(), id);
        }

        // Decide "someText" (what you display)
        String someText;
        if (tag.equals("label")) someText = text;
        else if (tag.equals("button")) someText = text;
        else if (tag.equals("a")) someText = !text.isEmpty() ? text : href;
        else { // input/select/textarea
            // prefer label, then aria-label/title/placeholder/value
            someText = firstNonEmpty(linkedLabel, ariaLabel, title, placeholder, value, name, id);
        }
        someText = nz(someText);

        // If still empty, skip it (you said you want exactly labels/inputs/buttons but "nice" list)
        if (someText.isEmpty()) {
            // If you truly want EVERYTHING regardless of text, comment this out
            // return null;
        }

        // XPath
        String xPath = !id.isEmpty() ? "//*[@" + "id=" + escapeXPathLiteral(id) + "]" : buildXPathByTagAndIndex(el);

        // Build AttributeData list
        List<AttributeData> attrs = new ArrayList<>();
        attrs.add(new AttributeData("tag", tag));
        attrs.add(new AttributeData("id", id.isEmpty() ? "null" : id));
        attrs.add(new AttributeData("name", name.isEmpty() ? "null" : name));
        attrs.add(new AttributeData("class", cls.isEmpty() ? "null" : cls));
        attrs.add(new AttributeData("type", type.isEmpty() ? "null" : type));
        attrs.add(new AttributeData("value", value.isEmpty() ? "null" : value));
        attrs.add(new AttributeData("placeholder", placeholder.isEmpty() ? "null" : placeholder));
        attrs.add(new AttributeData("for", forId.isEmpty() ? "null" : forId));
        attrs.add(new AttributeData("href", href.isEmpty() ? "null" : href));
        attrs.add(new AttributeData("role", role.isEmpty() ? "null" : role));
        attrs.add(new AttributeData("tabindex", tabindex.isEmpty() ? "null" : tabindex));
        attrs.add(new AttributeData("aria-label", ariaLabel.isEmpty() ? "null" : ariaLabel));
        attrs.add(new AttributeData("title", title.isEmpty() ? "null" : title));
        attrs.add(new AttributeData("disabled", disabled.isEmpty() ? "false" : "true"));

        // Create DTO (kept close to your original shape)
        ElementDTO dto = new ElementDTO();
        dto.setId(idSeq);
        dto.setTypeElement("web-control");
        dto.setTagName(tag);
        dto.setXPath(xPath);
        dto.setAttribId(!id.isEmpty() ? id : xPath);
        dto.setSomeText(someText);
        dto.setAttributeData(attrs.toArray(new AttributeData[0]));

        // Not available in pure DOM parsing
        dto.setCoordinates("null");
        dto.setCustomXPath("");
        dto.setIFrameXPath("");
        dto.setShadowHost("");
        dto.setShadowRoot("false");
        dto.setNestedShadow("false");
        dto.setCssSelector("");
        dto.setAttribName("");
        dto.setAttributeValue("");
        dto.setAttributeType("");
        dto.setSearchAttributeValue("");

        return dto;
    }

    private String findLabelForInput(Document doc, String inputId) {
        if (doc == null || inputId == null || inputId.isEmpty()) return "";

        Element root = doc.getDocumentElement();
        return findLabelForInputRec(root, inputId);
    }

    private String findLabelForInputRec(Element el, String inputId) {
        String tag = nz(el.getTagName()).toLowerCase();
        if (tag.equals("label")) {
            String f = nz(el.getAttribute("for"));
            if (inputId.equals(f)) {
                return normalizeSpace(el.getTextContent());
            }
        }

        org.w3c.dom.Node child = el.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                String found = findLabelForInputRec((Element) child, inputId);
                if (!found.isEmpty()) return found;
            }
            child = child.getNextSibling();
        }
        return "";
    }

    private String escapeXPathLiteral(String s) {
        s = nz(s);
        if (!s.contains("'")) return "'" + s + "'";
        if (!s.contains("\"")) return "\"" + s + "\"";

        StringBuilder sb = new StringBuilder("concat(");
        boolean first = true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            String part;
            if (c == '\'') part = "\"'\"";
            else if (c == '"') part = "'\"'";
            else {
                int j = i;
                while (j < s.length()) {
                    char cj = s.charAt(j);
                    if (cj == '\'' || cj == '"') break;
                    j++;
                }
                part = "'" + s.substring(i, j) + "'";
                i = j - 1;
            }
            if (!first) sb.append(", ");
            sb.append(part);
            first = false;
        }
        sb.append(")");
        return sb.toString();
    }

    private String buildXPathByTagAndIndex(Element el) {
        if (el == null) return "";
        StringBuilder path = new StringBuilder();
        org.w3c.dom.Node current = el;

        while (current != null && current.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
            Element curEl = (Element) current;
            String tag = nz(curEl.getTagName());
            if (tag.isEmpty()) tag = "*";

            int index = getIndexAmongSameTagSiblings(curEl);
            path.insert(0, "/" + tag + "[" + index + "]");

            current = current.getParentNode();
            if (current != null && current.getNodeType() == org.w3c.dom.Node.DOCUMENT_NODE) break;
        }
        return path.toString();
    }

    private int getIndexAmongSameTagSiblings(Element el) {
        org.w3c.dom.Node parent = el.getParentNode();
        if (parent == null) return 1;

        String tag = nz(el.getTagName());
        int idx = 0;

        org.w3c.dom.Node child = parent.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                Element ce = (Element) child;
                if (tag.equals(nz(ce.getTagName()))) {
                    idx++;
                    if (ce == el) return idx;
                }
            }
            child = child.getNextSibling();
        }
        return 1;
    }

    private String firstNonEmpty(String... vals) {
        if (vals == null) return "";
        for (String v : vals) {
            v = nz(v);
            if (!v.isEmpty()) return v;
        }
        return "";
    }

    private String normalizeSpace(String s) {
        s = nz(s).replace('\u00A0', ' ');
        return s.replaceAll("\\s+", " ").trim();
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }

    private Document parseXhtmlToDocument(String xhtml) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

            // security hardening
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbf.setXIncludeAware(false);
            dbf.setExpandEntityReferences(false);

            dbf.setNamespaceAware(false);

            DocumentBuilder db = dbf.newDocumentBuilder();
            return db.parse(new InputSource(new StringReader(xhtml)));
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse XHTML into Document: " + e.getMessage(), e);
        }
    }

    public static InputInfo findMatchingInput(List<InputInfo> inputs, InstructionLoad currentInstruction) {
        if (inputs == null || inputs.isEmpty() || currentInstruction == null) {
            return null;
        }

        String instrName = normalize(currentInstruction.getName());
        String instrTag = normalize(currentInstruction.getTagName());

        for (InputInfo info : inputs) {
            if (info == null) continue;

            String inputName = normalize(info.name());
            String inputTag = normalize(info.tag());

            if (instrName.equalsIgnoreCase(inputName) && instrTag.equalsIgnoreCase(inputTag)) {
                return info;
            }
        }

        return null;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim();
    }

    private void pushUpdateListElements() {
        if (performActions == null || performActions.getCurrentDriver() == null) return;

        int finalPort = portSocketInitial;
        String socketSessionId = "UPDATE_LIST_ELEMENTS";
        String destinationId = "perform-list-data";
        String[] dataArray = new String[] {"input", "textarea", "button", "a", "select", "label"};

        updateListElements(
                performActions.getCurrentDriver(),
                dataArray,
                finalPort,
                socketSessionId,
                destinationId,
                "searchTerms",
                this.currentBotJob.getHomeBankingId(),
                this.currentBotJob.getId());
    }

    private static boolean isWebElementInstruction(InstructionLoad instr) {
        if (instr == null) return false;

        String actions = instr.getActions();
        if (actions == null) return false;

        String raw = actions.trim();
        if (raw.isEmpty()) return false;

        // split() takes a regex, so quote the splitter to treat it literally
        String[] parts = raw.split(Pattern.quote(ARConstantsEngine.ACTION_SPECIFICATIONS_SPLITTER), 2);
        String first = parts[0].trim();
        if (first.isEmpty()) return false;

        String upper = first.toUpperCase(Locale.ROOT);

        // Required prefixes: "C" (including "C:"), "I:", "O:"
        if (upper.startsWith("C") || upper.startsWith("I") || upper.startsWith("O")) {
            return true;
        }

        // Optional: support plain operation tokens
        return upper.equals("SET") || upper.equals("GET");
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        return value.replace(".", "").replace(",", "");
    }

    private int getNavigationTimeSeconds() {
        String v = arPropertyManager.getProperty(ARPropertyEnum.NAVIGATION_TIME);
        try {
            int s = Integer.parseInt(v);
            if (s < 0) return 0;
            if (s > 10) return 10;
            return s;
        } catch (Exception ignore) {
            return 0;
        }
    }

    public void writeToFileCSV(String filename, String content) {
        try (Writer writer =
                new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filename), StandardCharsets.UTF_8))) {
            writer.write(content);
            log.info("CSV written to file: " + filename);
        } catch (IOException e) {
            log.error("Error writing file: " + e.getMessage());
        }
    }

    private void saveExcelWrite(
            String newExcelFieldName, int xExcelDataSize, ExcelWriter.ExcelChain writerExport, int exportIndex) {
        if (newExcelFieldName != null && newExcelFieldName.toLowerCase().endsWith(".csv")) {
            if (Strings.isNullOrEmpty(delimiterCSV)) {
                delimiterCSV = ",";
            }

            String csvContent = getBancaStatoCsvContent(delimiterCSV);
            if (csvContent != null) {
                writeToFileCSV(newExcelFieldName, csvContent);
                if (xExcelDataSize > 1) {
                    mapExportRows = new LinkedHashMap<>();
                }
            }
            newExcelFieldName = "";
        } else if (newExcelFieldName != null && newExcelFieldName.toLowerCase().endsWith(".xlsx")) {
            //
            //                    writerExport.insertFieldNameAndValueLastColumn(mapExportRows, exportIndex -
            // 1);
            if (writerExport != null) {
                //                writerExport.insertCSVContentIntoExcel(columnsCSV, rowsCSV, exportIndex - 1);
            }
        }
    }

    /**
     * Adds a row using a Map<String, String>. If this is the first row added,
     * it sets the column order based on the map's keys.
     */
    public void addRowFromMap(Map<String, String> map, int xExcelCurrentRow) {
        // Initialize column order on first insert
        if (columnsCSV.isEmpty()) {
            if (map instanceof LinkedHashMap) {
                columnsCSV.addAll(map.keySet()); // preserve order
            } else {
                // Default to alphabetical if insertion order is unknown
                List<String> sortedKeys = new ArrayList<>(map.keySet());
                Collections.sort(sortedKeys);
                columnsCSV.addAll(sortedKeys);
            }
        }

        List<String> row = new ArrayList<>();
        for (String column : columnsCSV) {
            row.add(map.getOrDefault(column, ""));
        }
        if (rowsCSV.isEmpty() || rowsCSV.size() <= xExcelCurrentRow) {
            //            rowsCSV.add(row);
        }
    }

    public void writeToFile(String filename, String content) {
        try (Writer writer =
                new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filename), StandardCharsets.UTF_8))) {
            writer.write(content);
            logOperations.info("CSV written to file: " + filename);
        } catch (IOException e) {
            logOperations.error("Error writing file: " + e.getMessage());
        }
    }

    public void setColumns(List<String> columns) {
        columnsCSV.clear();
        columnsCSV.addAll(columns);
    }
}

package com.allinweb.ch.component.pane;

import com.allinweb.ch.builder.WebElementAttributeEnum;
import com.allinweb.ch.builder.WebElementAttributeTypeValueEnum;
import com.allinweb.ch.builder.WebElementIcon;
import com.allinweb.ch.builder.WebElementTagNameEnum;
import com.allinweb.ch.component.model.AttributeData;
import com.allinweb.ch.component.model.BlockDetailsDTO;
import com.allinweb.ch.component.model.BlockLoadDTO;
import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.model.ElementDTO;
import com.allinweb.ch.component.model.ElementSplitDTO;
import com.allinweb.ch.component.model.HomeBankingLoadDTO;
import com.allinweb.ch.component.model.InstructionLoadDTO;
import com.allinweb.ch.component.model.InstructionReferenceLoadDTO;
import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.component.scene.ARNewHomeBankingScene;
import com.allinweb.ch.control.ARComponentBuilder;
import com.allinweb.ch.core.ARSharedResources;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.driver.ARWebElement;
import com.allinweb.ch.facade.IframeInputLocator;
import com.allinweb.ch.facade.PerformActions;
import com.allinweb.ch.facade.PerformCloneLoad;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.facade.PerformPickLoad;
import com.allinweb.ch.facade.PerformPreLoad;
import com.allinweb.ch.persistence.*;
import com.allinweb.ch.readersAndWriters.ExcelReader;
import com.allinweb.ch.readersAndWriters.ExcelWriter;
import com.allinweb.ch.socket.SimpleWebSocketServer;
import com.allinweb.ch.util.*;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.Alert;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Pair;
import javax.net.ssl.*;
import javax.websocket.ClientEndpoint;
import javax.websocket.ContainerProvider;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.*;
import org.openqa.selenium.support.pagefactory.ByChained;
import org.openqa.selenium.support.ui.WebDriverWait;

@ClientEndpoint
public class ARScannedElementPane extends ARPane {
    private static Map<String, Session> activeSessions;

    private final Gson gson = new Gson();

    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        System.out.println("Connected to WebSocket server at: " + session.getRequestURI());

        // Sending an initial message
        sendMessage("Hello from JavaFX WebSocket client!");
    }

    @OnMessage
    public void onMessage(String message) {
        System.out.println("Received: " + message);
        if (message == null || message.trim().isEmpty() || message.contains("CONNECT")) {
            // Ignore null or empty messages
            return;
        }

        String type = null;
        int homeBankingId = -1;
        try {
            // Parse the incoming message (assuming JSON format)
            JsonObject jsonObjMSG = JsonParser.parseString(message).getAsJsonObject();
            homeBankingId = jsonObjMSG.has("homeBankingId")
                    ? Integer.parseInt(jsonObjMSG.get("homeBankingId").getAsString())
                    : -1;

            type = jsonObjMSG.has("type") ? jsonObjMSG.get("type").getAsString() : "unknown";
            String sessionId =
                    jsonObjMSG.has("sessionId") ? jsonObjMSG.get("sessionId").getAsString() : "unknown";

            // Process the message based on its type
            switch (type) {
                case "NEW_ELEMENT_DTO":
                case "SEND_ALL_ELEMENTS_DTO":
                case "DEL_ELEMENT_DTO":
                case "DETAILS_ELEMENT_DTO":
                    // Extract the "body" field from the JsonObject
                    ElementSplitDTO processDTO = gson.fromJson(jsonObjMSG, ElementSplitDTO.class);
                    homeBankingId = processDTO.getHomeBankingId();
                    itPrintsElementDTO(processDTO.getDetails()[0]);
                    break;
                default:
                    break;
            }
        } catch (Exception error) {
            System.err.println("Error processing message: " + error.getMessage());
        }
    }

    @OnClose
    public void onClose(Session session) {
        System.out.println("Connection closed.");
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        System.out.println("Error: " + throwable.getMessage());
    }

    // Method to send a message
    public void sendMessage(String message) {
        executorWebSocket.submit(() -> {
            //            if (session != null && session.isOpen()) {
            //                try {
            //                    session.getBasicRemote().sendText(message);
            //                } catch (Exception e) {
            //                    e.printStackTrace();
            //                }
            //            }
        });
    }

    private void connectWebSocketClient(int portSocket, String sessionId) {
        String serverUri = "ws://localhost:" + portSocket + "/websocket?sessionId=" + sessionId;
        executorWebSocket.submit(() -> {
            try {
                WebSocketContainer container = ContainerProvider.getWebSocketContainer();
                container.connectToServer(this, new URI(serverUri));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private int currentTabIndex = 0; // Track the currently active tab index
    public WebDriver currentDriver;
    private Set<String> windowHandles;
    private WebElement previousElement = null;
    private WebElement currentElement = null;

    private Session session;
    private static final ExecutorService executorWebSocket = Executors.newSingleThreadExecutor();

    private ExecutorService executorService;
    private static final int SECONDS = 3; // Total seconds for the countdown
    private int remainingSeconds = SECONDS;
    private Timeline timeline;
    private Alert alertToShow;
    public static Repository repository;

    private static TargetElement targetSelected = new TargetElement();

    private static File baseLogFile = null;
    private static SimpleDateFormat dateFormatter;

    private static JavascriptExecutor jsExecutor;

    private final ARComponentBuilder componentBuilder = new ARComponentBuilder();

    private DatabaseUserDTO databaseUserDto;

    private BotJobLoadDTO botJobLoad;
    private BlockLoadDTO blockLoad;

    private int currentBlockId;

    double comboWidth = 200;

    private List<BotJobLoadDTO> botJobLoadList = new ArrayList<>();
    private List<BlockLoadDTO> blockLoadList = new ArrayList<>();
    private HomeBankingLoadDTO homeBanking;

    private ComboBox<ComboBoxVars> comboBoxBlocks;
    private ObservableList<ComboBoxVars> blocksItems = FXCollections.observableArrayList();

    Button refreshBlocksButton;

    // UI COMPONENTS
    private HBox topPane;
    private HBox bottomPane;
    private HBox bottomPaneTime;
    private AnchorPane contentPane;

    private WebView webView = new WebView();
    private WebEngine webEngine;
    private HBox componentBox;

    //    private ObservableList<ARWebElement> webElementObservableList1;
    //    private ObservableList<ARWebElement> webElementObservableList2;
    //    private ObservableList<ARWebElement> webElementObservableList3;
    //    private ObservableList<ARWebElementNew> webElementObservableList4;
    //    private ListView<ARWebElement> scannedElements1;
    //    private ListView<ARWebElement> scannedElements2;
    //    private ListView<ARWebElement> scannedElements3;
    //    private ListView<ARWebElementNew> scannedElements4;

    private Button scanIFrameButton;
    private Button addButtonNewElement;
    private Button configureButton;
    private Button launchBotJobButton;
    private Button recallJobButton;
    private Button searchWithIdsButton;
    private Button searchWithNamesButton;
    private Button searchButtons;
    private Button refreshInputFieldsButton;
    private Button refreshOutputFieldsButton;
    //    private Button refreshOtherFieldsButton;
    private Button magicFieldsButton;
    private Button leftButton;
    private Button rightButton;
    private Button cleanListButton;
    private Button turnOnOffButton;
    private Button includeAllSelected;

    private CheckBox checkCloneElement;

    private CheckBox checkTestAction;
    private CheckBox checkJavaScript;
    //    private CheckBox checkTestCoordinates;
    private CheckBox checkClickElement;
    private CheckBox checkInputText;
    private CheckBox checkOutputText;
    private CheckBox checkForceEnterText;
    private CheckBox checkForceCoordText;

    private Label defineNameLabel;
    //    private Label searchAttribNameLabel;
    private Label searchAttribValueLabel;
    private Label currentXPathLabel;
    private Label currentAllAttributesLabel;
    private Label customXPathLabel;
    private Label originalTagNameLabel;
    private Label coordsTextFieldLabel;

    private Text currentURL;
    private Text iFrameText;

    private VBox textFieldVBox;
    private TextFlow textFlowResult;
    private TextArea countdownTextField;

    private TextField defineNameField;
    private TextField testActionsField;
    //    private TextField searchAttribNameField;
    private TextField searchAttribValueField;
    private TextField currentXPathTextField;
    private TextField allAttributesTextField;
    private TextField customXPathTextField;
    private TextField originalTagNameField;
    private TextField coordsTextField;

    private Boolean resultElementSearch = false;

    private Boolean idAttributeFirst = false;
    private Boolean nameAttributeFirst = false;
    private Boolean withoutNameAndId = false;

    private Map<String, String> mapOperators;
    private Map<String, String> mapExport;

    private String iFrameXPath;
    private String[] iFrameElements;
    private static String[] lstAllPaths;
    private String iFrameCoords;
    private List<ElementDTO> elementsFound = new ArrayList<>();

    List<InstructionLoadDTO> instructionsExecuted = new ArrayList<>();
    List<Integer> executedSuccess = new ArrayList<>();
    Map<String, WebElement> mapAdvanced = new HashMap<>();

    // Very important sequence on initiation
    private static int reduceSearchCriteria;
    private static ARPropertyManager managerProps;
    private static ARPriorities arPriorities;

    private PerformDataBase performDataBase;
    private PerformActions performActions;
    private PerformMessage performMessage;
    private PerformPreLoad performPreLoad;
    private ARWebDriver currentARWebDriver;

    private static final PerformCloneLoad performCloneLoad;
    private static final PerformPickLoad performPickLoad;

    private static final ARNewHomeBankingScene arNewHomeBankingScene;
    private static final IframeInputLocator iframeInputLocator;
    private int portSocket = 8080;
    private String sessionId;

    private String[] defaultSearch;
    private boolean searchHiddenFields;
    private String xpathTextPrevious;

    // Static block to initialize
    static {
        iframeInputLocator = IframeInputLocator.getInstance();
        performCloneLoad = PerformCloneLoad.getInstance();
        performPickLoad = PerformPickLoad.getInstance();
        arPriorities = ARPriorities.getInstance();
        managerProps = ARPropertyManager.getInstance();
        arNewHomeBankingScene = ARNewHomeBankingScene.getInstance();
    }

    public ARScannedElementPane(
            ARWebDriver currentARWebDriver,
            PerformDataBase performDataBase,
            PerformActions performActions,
            PerformMessage performMessage,
            PerformPreLoad performPreLoad,
            HomeBankingLoadDTO homeBanking,
            BotJobLoadDTO botJobLoadDTO,
            BlockLoadDTO blockLoadDTO) {
        this.currentARWebDriver = currentARWebDriver;
        this.performDataBase = performDataBase;
        this.performActions = performActions;
        this.performMessage = performMessage;
        this.performPreLoad = performPreLoad;
        this.homeBanking = homeBanking;

        activeSessions = SimpleWebSocketServer.getAllSessions();

        String port = ARPropertyManager.getInstance().getProperty(ARPropertyEnum.PORT_SOCKET);
        if (!Strings.isNullOrEmpty(port)) {
            portSocket = Integer.parseInt(port);
        }

        connectWebSocketClient(portSocket, "scannerReceiver");

        searchHiddenFields = false;

        defaultSearch = new String[] {"input", "button", "a", "select"};

        ARLogger.getInstance(ARWebDriver.class).fine("Calling ARScannedElementPane");

        // Ensure botJob and arPriorities are not null before accessing their methods
        if (this.botJobLoad != null && arPriorities != null) {
            // Check if we need to update arPriorities
            if (arPriorities.getJobId() == null || !arPriorities.getJobId().equals(this.botJobLoad.getId())) {
                // Set Job ID in arPriorities
                arPriorities.setJobId(this.botJobLoad.getId());

                // Check for non-null HomeBanking and Priority
                if (homeBanking != null) {
                    String priorityValue = homeBanking.getPriority();
                    String searchConfig = homeBanking.getSearchConfig();

                    if (priorityValue != null) {
                        arPriorities.loadPrioritiesFromString(priorityValue);
                    } else {
                        arPriorities.loadPriorities();
                    }

                    arPriorities.loadSearchElementsConfig(searchConfig);
                }

                // Initialize performAction with arPriorities and arWebDriver

                performActions.initialize(arPriorities);
                performActions.setCurrentDriver(currentARWebDriver.getCurrentDriver());
            }
        }

        // Assign instance variables
        this.botJobLoad = botJobLoadDTO;
        this.blockLoad = blockLoadDTO;
        performActions.initialize(arPriorities);
        performActions.setCurrentDriver(currentARWebDriver.getCurrentDriver());
    }

    @Override
    public Pane getPaneReference() {
        return new AnchorPane(topPane, contentPane);
    }

    @Override
    public void initUIComponents() {
        // Create a label to display the countdown
        Label countdownLabel = new Label(String.valueOf(remainingSeconds));
        countdownLabel.setStyle("-fx-font-size: 24px;");
        //        countdownLabel.setVisible(false);
        // Create a stack pane to hold the label
        StackPane stackPane = new StackPane(countdownLabel);
        stackPane.setPadding(new Insets(20));
        // Create a dialog for the alert
        alertToShow = new Alert(Alert.AlertType.INFORMATION);
        alertToShow.setTitle("Countdown Alert");
        alertToShow.setHeaderText("Count Down");
        alertToShow.initModality(Modality.APPLICATION_MODAL);
        // Set the content of the alert
        alertToShow.getDialogPane().setContent(stackPane);
        // Create a timeline to update the countdown
        timeline = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), event -> {
            remainingSeconds--;
            countdownLabel.setText(String.valueOf(remainingSeconds));
            if (remainingSeconds <= 0) {
                timeline.stop(); // Stop the timeline when countdown finishes
                alertToShow.close(); // Close the alert dialog
            }
        }));

        webEngine = webView.getEngine();
        webEngine.javaScriptEnabledProperty().set(true);

        String jsonData = "[]";

        // sessionIdFromJava
        sessionId = "scannerGrid-"
                + this.homeBanking
                        .getId(); // (SENDER: scannerTool) -> scannerGrid /  (SENDER: insertTool) -> botJobTasks /
        // Default
        // session
        buildWebView(
                webEngine,
                jsonData,
                portSocket,
                sessionId,
                homeBanking.getId(),
                this.botJobLoad.getId(),
                this.botJobLoad.getName());

        componentBox = new HBox(new Node[] {this.webView});

        HBox.setHgrow(this.webView, Priority.ALWAYS);
        VBox.setVgrow(this.webView, Priority.ALWAYS);

        //        if (performActions.getCurrentDriver() == null) {
        //            arWebDriver = new ARWebDriver(); // Initialize WebDriver
        //        }

        if (isBrowserClosed(performActions.getCurrentDriver()) && performActions.getCurrentDriver() != null) {
            performActions.getCurrentDriver().quit();
            performActions.setCurrentDriver(null);
            currentARWebDriver.getCurrentDriver().quit();
            currentARWebDriver.setCurrentDriver(null);
        }

        String browserType = ARPropertyManager.getInstance().getProperty(ARPropertyEnum.BROWSER);
        String webDriverPath = managerProps.getProperty(ARPropertyEnum.PATH_WEBDRIVER);

        currentARWebDriver.openDriver(
                browserType,
                webDriverPath,
                homeBanking.getUrl(),
                homeBanking.getOptionsConfig(),
                defaultSearch,
                searchHiddenFields,
                portSocket);

        performActions.initialize(arPriorities);
        performActions.setCurrentDriver(currentARWebDriver.getCurrentDriver());

        try {
            performActions.onHoldInSeconds(3);
        } catch (Exception e) {
            e.printStackTrace();
        }
        // "scannerTool", "scannerGrid", "searchTerms"
        //        performPreLoad.dynamicLoadElementsDTO(
        //                performActions.getCurrentDriver(),
        //                performActions.getCurrentDriver().getCurrentUrl(),
        //                defaultSearch,
        //                searchHiddenFields,
        //                portSocket,
        //                "scannerTool",
        //                "scannerGrid",
        //                "searchTerms");

        performActions.getIframeElementsMap();

        handleWindowHandlesChange();

        buildUIComponents();
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
                    ARLogger.getInstance(ARViewBotJobPane.class).severe("buildWebView  \nError: " + e.getMessage());
                }
            }
        });
    }

    private void buildUIComponents() {
        topPane = componentBuilder.createTopPanel(ARConstants.SPACE_L, ARConstants.SPACE_SM);
        bottomPane = componentBuilder.createBottomPanel(ARConstants.SPACE_L, ARConstants.SPACE_SM);
        bottomPaneTime = componentBuilder.createBottomPanel(ARConstants.SPACE_L, ARConstants.SPACE_SM);
        contentPane =
                componentBuilder.createContentPanel(ARConstants.SPACE_L, ARConstants.SPACE_XL, ARConstants.SPACE_SM);

        scanIFrameButton = componentBuilder.buildButton(
                "iFrames", ARConstants.SPACE_L, ARConstants.ICON_SEARCH, ARConstants.SPACE_M, new Insets(5));
        addButtonNewElement = componentBuilder.buildButton(
                "Clone", ARConstants.SPACE_L, ARConstants.ICON_TICK, ARConstants.SPACE_SM, new Insets(5));

        searchWithIdsButton = componentBuilder.buildButton(
                "Defined Identifiers", ARConstants.SPACE_ZERO, "/refresh.png", ARConstants.SPACE_M, new Insets(5.0D));
        searchWithNamesButton = componentBuilder.buildButton(
                "Defined Names", ARConstants.SPACE_ZERO, "/refresh.png", ARConstants.SPACE_M, new Insets(5.0D));
        searchButtons = componentBuilder.buildButton(
                "Buttons", ARConstants.SPACE_ZERO, "/refresh.png", ARConstants.SPACE_M, new Insets(5.0D));

        refreshInputFieldsButton = componentBuilder.buildButton(
                "Input Fields", ARConstants.SPACE_ZERO, "/refresh.png", ARConstants.SPACE_M, new Insets(5.0D));
        refreshOutputFieldsButton = componentBuilder.buildButton(
                "Output Fields", ARConstants.SPACE_ZERO, "/refresh.png", ARConstants.SPACE_M, new Insets(5.0D));
        //        refreshOtherFieldsButton = componentBuilder.buildButton(
        //                "Other Elements", ARConstants.SPACE_ZERO, "/refresh.png", ARConstants.SPACE_M, new
        // Insets(5.0D));
        magicFieldsButton = componentBuilder.buildButton(
                "", ARConstants.SPACE_ZERO, "/magic2.png", ARConstants.SPACE_M, new Insets(5.0D));
        magicFieldsButton.setDisable(true);

        turnOnOffButton = new Button("Search Hidden Fields: Off");
        turnOnOffButton.setStyle("-fx-background-color: grey; -fx-text-fill: white;");

        includeAllSelected = new Button("Include All Below to the Job");
        includeAllSelected.setStyle("-fx-background-color: green; -fx-text-fill: white;");
        includeAllSelected.setVisible(false);

        cleanListButton = componentBuilder.buildButton(
                "", // No text
                25.0, // Smaller height
                "/cross.png", // Icon source
                16.0, // Smaller icon size
                new Insets(2.0) // Reduced padding
                );

        checkTestAction = new CheckBox("Test Actions");
        //        checkJavaScript = new CheckBox("JS");

        //        checkTestCoordinates = new CheckBox("Test Coordinates");

        //        checkClickElement.setSelected(true);
        checkClickElement = new CheckBox("For Click");
        checkInputText = new CheckBox("For Input");
        checkOutputText = new CheckBox("For Output (Excel Export)");

        checkForceEnterText = new CheckBox("With <PRESS ENTER> Action");
        checkForceEnterText.setStyle("-fx-font-size: 12px; -fx-fill: blue;");

        checkForceCoordText = new CheckBox("Force Coordinates");
        checkForceCoordText.setStyle("-fx-font-size: 12px; -fx-fill: blue;");

        iFrameText = new Text("");
        iFrameText.setStyle("-fx-font-size: 12px; -fx-fill: blue;");

        //        webElementObservableList1 = FXCollections.observableArrayList();

        //        scannedElements1 = new ListView<>(webElementObservableList1);
        //        scannedElements1 = componentBuilder.setAnchorPaneAnchors(scannedElements1, ARConstants.SPACE_ZERO);
        //        scannedElements1.setCellFactory(new ARCellFactory<>(ARWebElementListCell.class)::call);

        //        webElementObservableList2 = FXCollections.observableArrayList();
        //        scannedElements2 = new ListView<>(webElementObservableList2);
        //        scannedElements2 = componentBuilder.setAnchorPaneAnchors(scannedElements2, ARConstants.SPACE_ZERO);
        //        scannedElements2.setCellFactory(new ARCellFactory<>(ARWebElementListCell.class)::call);

        //        webElementObservableList3 = FXCollections.observableArrayList();
        //        scannedElements3 = new ListView<>(webElementObservableList3);
        //        scannedElements3 = componentBuilder.setAnchorPaneAnchors(scannedElements3, ARConstants.SPACE_ZERO);
        //        scannedElements3.setCellFactory(new ARCellFactory<>(ARWebElementListCell.class)::call);

        //        webElementObservableList4 = FXCollections.observableArrayList();
        //        scannedElements4 = new ListView<>(webElementObservableList4);
        //        scannedElements4 = componentBuilder.setAnchorPaneAnchors(scannedElements4, ARConstants.SPACE_ZERO);
        //        scannedElements4.setCellFactory(new ARCellFactory<>(ARWebElementListCell.class)::call);

        configureButton = componentBuilder.buildButton(
                "Config", ARConstants.SPACE_M, ARConstants.ICON_CONFIG, ARConstants.SPACE_M, new Insets(5.0D));

        launchBotJobButton = componentBuilder.buildButton(
                "Pre-Launch", ARConstants.SPACE_ZERO, "/play.png", ARConstants.SPACE_M, new Insets(5.0D));
        recallJobButton = componentBuilder.buildButton(
                "Resume", ARConstants.SPACE_ZERO, "/play.png", ARConstants.SPACE_M, new Insets(5.0D));

        textFlowResult = new TextFlow();

        countdownTextField = new TextArea("Pre-Launch status: Ready");
        countdownTextField.setStyle("-fx-font-size: 12px; -fx-text-fill: blue;");
        countdownTextField.setEditable(true);

        checkCloneElement = new CheckBox("PICK ONE ");

        defineNameLabel = new Label("DEFINE ELEMENT NAME");

        //        searchAttribNameLabel = new Label("Search Attribute Name");
        searchAttribValueLabel = new Label("Search Per Attribute");

        currentXPathLabel = new Label("XPath");
        currentAllAttributesLabel = new Label("All Attributes");
        customXPathLabel = new Label("Custom XPath");
        originalTagNameLabel = new Label("Tag Name");
        coordsTextFieldLabel = new Label("Main Coordinates");

        defineNameField = new TextField();
        defineNameField.setPromptText("DEFINE A NAME");

        //        searchAttribNameField = new TextField();
        //        searchAttribNameField.setPromptText("Search Attrib Name");

        searchAttribValueField = new TextField();
        searchAttribValueField.setPromptText("Search per Attrib");

        currentXPathTextField = new TextField();
        currentXPathTextField.setPromptText("XPath");
        //        iFrameXPathTextField = new TextField();
        //        iFrameXPathTextField.setPromptText("iFrame XPath");
        allAttributesTextField = new TextField();
        allAttributesTextField.setPromptText("All Attributes");
        customXPathTextField = new TextField();
        customXPathTextField.setPromptText("Custom XPath");
        originalTagNameField = new TextField();
        originalTagNameField.setPromptText("Tag Name");
        coordsTextField = new TextField();
        coordsTextField.setPromptText("Coordinates");

        customXPathLabel.setVisible(false);
        customXPathTextField.setVisible(false);
        originalTagNameLabel.setVisible(false);
        originalTagNameField.setVisible(false);
        currentAllAttributesLabel.setVisible(false);
        allAttributesTextField.setVisible(false);

        leftButton = componentBuilder.buildButton(
                "Previous", ARConstants.SPACE_M, ARConstants.ICON_LEFT, ARConstants.SPACE_M, new Insets(5.0D));
        rightButton = componentBuilder.buildButton(
                "Next", ARConstants.SPACE_M, ARConstants.ICON_RIGHT, ARConstants.SPACE_M, new Insets(5.0D));

        leftButton.setDisable(true);
        rightButton.setDisable(true);

        leftButton.setOnAction(e -> switchToLeftTab());
        rightButton.setOnAction(e -> switchToRightTab());

        cleanListButton.setOnAction(e -> {
            //            webElementObservableList1.clear();
            //            webElementObservableList2.clear();
            //            webElementObservableList2.clear();
            Platform.runLater(() -> {
                countdownTextField.setText("Pre-Launch status: Ready");
                //                textFlowResult.getChildren().clear();
                //                //                textFlowResult.getChildren().addAll(countdownTextField);
                //                textFlowResult.requestLayout();
                //                contentPane.requestLayout();
            });
        });

        currentURL = new Text("");
        currentURL.setFill(Color.BLUE);
        currentURL.setStyle("-fx-font-size: 16px;");

        updateSceneTitleWithCurrentURL(homeBanking.getUrl());

        this.blockLoadList = performDataBase.loadBlocksByBotJobId(this.botJobLoad.getId());
        loadAllBlockItems(this.blockLoadList);

        refreshBlocksButton = createPathButton();

        refreshBlocksButton.setOnMouseClicked(e -> {
            refreshBlocks(false);
        });

        comboBoxBlocks = new ComboBox<>(blocksItems);
        comboBoxBlocks.setPrefWidth(comboWidth);
        comboBoxBlocks.getSelectionModel().selectFirst();
        comboBoxBlocks.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(ComboBoxVars item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setTextFill(Color.BLACK); // Ensure text is black
                } else {
                    setText(item.getText());
                    setTextFill(Color.BLACK); // Ensure text is black
                }
            }
        });
        comboBoxBlocks.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(ComboBoxVars item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setTextFill(Color.BLACK); // Ensure text is black
                } else {
                    setText(item.getText());
                    setTextFill(Color.BLACK); // Ensure text is black
                }

                // Add hover effect
                setOnMouseEntered(e -> setStyle("-fx-background-color: lightgray;"));
                setOnMouseExited(e -> setStyle("-fx-background-color: none;"));
            }
        });
        comboBoxBlocks.getSelectionModel().selectFirst();

        try {
            // Starting the View

            // Create a GridPane for the top section
            GridPane gridPaneTop = new GridPane();
            gridPaneTop.setPadding(new Insets(10));
            gridPaneTop.setHgap(10); // Set horizontal gap between columns

            // Add buttons and checkbox to the GridPane
            //            gridPaneTop.add(scanIFrameButton, 0, 0);
            gridPaneTop.add(refreshInputFieldsButton, 0, 0);
            gridPaneTop.add(searchWithIdsButton, 1, 0);
            gridPaneTop.add(searchWithNamesButton, 2, 0);
            gridPaneTop.add(searchButtons, 3, 0);
            gridPaneTop.add(refreshOutputFieldsButton, 4, 0);
            //            gridPaneTop.add(refreshOtherFieldsButton, 5, 0);
            gridPaneTop.add(magicFieldsButton, 5, 0);
            gridPaneTop.add(turnOnOffButton, 6, 0);
            gridPaneTop.add(leftButton, 7, 0);
            gridPaneTop.add(rightButton, 8, 0);

            //        gridPaneTop.add(configureButton, 4, 0);
            //        gridPaneTop.add(launchBotJobButton, 5, 0);
            //        gridPaneTop.add(checkPickElement, 6, 0);
            //        gridPaneTop.add(addButtonNewElement, 7, 0);
            //        gridPaneTop.add(currentXPathTextField, 8, 0);

            HBox boxCoordenates = new HBox();
            boxCoordenates.setSpacing(5);

            checkClickElement
                    .prefWidthProperty()
                    .bind(boxCoordenates.widthProperty().multiply(0.50));
            //            checkTestCoordinates
            //                    .prefWidthProperty()
            //                    .bind(boxCoordenates.widthProperty().multiply(0.50));

            // Add elements to the HBox
            //            boxCoordenates.getChildren().addAll(checkClickElement, checkCoordinates);
            boxCoordenates.getChildren().addAll(checkClickElement);

            VBox vBoxCheckBox = new VBox();
            vBoxCheckBox
                    .getChildren()
                    .addAll(
                            boxCoordenates,
                            checkInputText,
                            checkOutputText,
                            checkForceEnterText,
                            checkForceCoordText,
                            iFrameText);
            vBoxCheckBox.setSpacing(6); // Adjust spacing between CheckBoxes
            //        gridPaneTop.add(vBox, 9, 0);

            topPane.getChildren().add(gridPaneTop); // Add gridPaneTop to topPane

            VBox verticalBox = new VBox();
            verticalBox.setSpacing(10);
            verticalBox.setPadding(new Insets(10));
            VBox.setVgrow(verticalBox, Priority.ALWAYS);

            // Create a GridPane for the middle section
            //            GridPane gridPane = new GridPane();
            //            gridPane.setPadding(new Insets(10));
            //            gridPane.setHgap(10); // Set horizontal gap between columns

            // Add buttons and checkbox to the GridPane
            //            gridPane.add(refreshInputFieldsButton, 0, 0);
            //            gridPane.add(searchWithIdsButton, 1, 0);
            //            gridPane.add(searchWithNamesButton, 2, 0);
            //            gridPane.add(searchWithoutIdsAndNamesBtn, 3, 0);
            //            gridPane.add(refreshOutputFieldsButton, 4, 0);
            //            gridPane.add(refreshOtherFieldsButton, 5, 0);
            //        gridPane.add(checkTestAction, 6, 0);
            //        gridPane.add(originalTagNameField, 7, 0);
            //        gridPane.add(coordsTextField, 8, 0);

            // Create an HBox to hold launchBotJobButton and recallJobButton
            HBox hBoxLaunchButon = new HBox();
            hBoxLaunchButon.setSpacing(10); // Optional: adjust spacing between buttons

            // Add buttons to the HBox
            hBoxLaunchButon.getChildren().addAll(launchBotJobButton);

            HBox boxName = new HBox();
            boxName.getChildren().addAll(defineNameField, addButtonNewElement);

            HBox boxActions = new HBox();
            boxActions.setSpacing(5);
            // Set proportional widths for each child
            testActionsField = new TextField("0001");
            checkTestAction.prefWidthProperty().bind(boxActions.widthProperty().multiply(0.70));
            //            checkJavaScript.prefWidthProperty().bind(boxActions.widthProperty().multiply(0.10));
            testActionsField.prefWidthProperty().bind(boxActions.widthProperty().multiply(0.3));

            boxActions.getChildren().addAll(checkTestAction, testActionsField);

            HBox boxCorrdinates = new HBox();
            boxCorrdinates.setSpacing(5);
            //            checkTestCoordinates
            //                    .prefWidthProperty()
            //                    .bind(boxActions.widthProperty().multiply(0.70));

            coordsTextField.prefWidthProperty().bind(boxActions.widthProperty().multiply(0.3));

            boxCorrdinates.getChildren().addAll(coordsTextFieldLabel, coordsTextField);

            //            coordsTextField
            //                    .prefWidthProperty()
            //                    .bind(boxCoordenates.widthProperty().multiply(0.50));

            HBox hBoxPickClone = new HBox();
            hBoxPickClone.getChildren().addAll(createSpacerHoriz(), checkCloneElement, createSpacerHoriz());

            //            textFlowResult.getChildren().addAll(countdownTextField);

            // Create the VBox for TextFields
            textFieldVBox = new VBox();
            textFieldVBox.setSpacing(6); // Adjust spacing between TextFields
            textFieldVBox
                    .getChildren()
                    .addAll(
                            hBoxPickClone,
                            defineNameLabel,
                            boxName,
                            //                            attribIdTextFieldLabel,
                            //                            attribIdTextField,
                            //                            attribNameTextFieldLabel,
                            //                            attribNameTextField,
                            //                            currentXPathLabel,
                            //                            currentXPathTextField,
                            //                            currentAllAttributesLabel,
                            //                            allAttributesTextField,
                            //                            customXPathLabel,
                            //                            customXPathTextField,
                            //                            originalTagNameLabel,
                            //                            originalTagNameField,
                            //                            coordsTextFieldLabel,
                            //                            coordsTextField,
                            vBoxCheckBox,
                            createCustomSeparator(Color.DARKBLUE, 2),
                            createSpacerVert(),
                            countdownTextField,
                            //                            textFlowResult,
                            boxActions,
                            boxCorrdinates,
                            createSpacerVert(),
                            createCustomSeparator(Color.DARKBLUE, 2),
                            hBoxLaunchButon,
                            configureButton);

            customXPathLabel.setVisible(false);
            customXPathTextField.setVisible(false);
            originalTagNameLabel.setVisible(false);
            originalTagNameField.setVisible(false);
            currentAllAttributesLabel.setVisible(false);
            allAttributesTextField.setVisible(false);

            // Bind button widths to VBox width
            boxActions.maxWidthProperty().bind(textFieldVBox.widthProperty());

            // Bind button widths to VBox width
            addButtonNewElement.maxWidthProperty().bind(textFieldVBox.widthProperty());
            //            launchBotJobButton.maxWidthProperty().bind(textFieldVBox.widthProperty());
            // Bind the widths of the buttons to percentages of the HBox width
            countdownTextField.maxWidthProperty().bind(textFieldVBox.widthProperty());
            configureButton.maxWidthProperty().bind(textFieldVBox.widthProperty());

            // Fix the widths to 70% and 30% of the HBox width
            hBoxLaunchButon.widthProperty().addListener((obs, oldVal, newVal) -> {
                double totalWidth = newVal.doubleValue();
                launchBotJobButton.setMaxWidth(totalWidth * 0.6);
                recallJobButton.setMaxWidth(totalWidth * 0.7);
            });

            HBox boxListViews = new HBox();

            // Bind the height of ListViews to the height of the HBox
            //            scannedElements1.prefHeightProperty().bind(boxListViews.heightProperty());
            componentBox.prefHeightProperty().bind(boxListViews.heightProperty());
            //            scannedElements3.prefHeightProperty().bind(boxListViews.heightProperty());
            //            scannedElements4.prefHeightProperty().bind(boxListViews.heightProperty());

            boxListViews.setSpacing(5);

            // Set Hgrow for each ListView to make them equally distributed
            //            HBox.setHgrow(scannedElements1, Priority.ALWAYS);
            HBox.setHgrow(componentBox, Priority.ALWAYS);
            //            HBox.setHgrow(scannedElements3, Priority.ALWAYS);
            //            HBox.setHgrow(scannedElements4, Priority.ALWAYS);

            StackPane stackCurrentURL = new StackPane();
            stackCurrentURL.getChildren().add(currentURL);
            stackCurrentURL.setAlignment(Pos.CENTER);
            HBox currentURLBox = new HBox(stackCurrentURL);

            //            Label labelInput = new Label("Input/IDs/Names(No Ids/Names)/Buttons");
            //            StackPane stackLabelInput = new StackPane();
            //            stackLabelInput.getChildren().add(labelInput);
            //            stackLabelInput.setAlignment(Pos.CENTER);
            //            VBox elements1VBox = new VBox(stackLabelInput, scannedElements1);
            //            HBox.setHgrow(elements1VBox, Priority.ALWAYS);

            //            Label labelOutput = new Label("Output Fields Results");
            //            StackPane stackLabelOutput = new StackPane();
            //            stackLabelOutput.getChildren().add(labelOutput);
            //            stackLabelOutput.setAlignment(Pos.CENTER);
            //            VBox elements2VBox = new VBox(stackLabelOutput, scannedElements2);
            //            HBox.setHgrow(elements2VBox, Priority.ALWAYS);

            Label labelOthers = new Label("Input/IDs/Names(No Ids/Names)/Buttons and Other Elements Results");
            StackPane stackLabelOthers = new StackPane();
            HBox othersBox = new HBox();
            createSpacerHoriz();
            othersBox.getChildren().addAll(labelOthers, createSpacerHoriz(), cleanListButton);
            stackLabelOthers.getChildren().addAll(othersBox);

            stackLabelOthers.setAlignment(Pos.CENTER);
            VBox elements2VBox = new VBox(stackLabelOthers, componentBox);
            HBox.setHgrow(elements2VBox, Priority.ALWAYS);

            //            Label labelNew = new Label("New");
            //            StackPane stackLabelNew = new StackPane();
            //            HBox newWebElem = new HBox();
            //            createSpacerHoriz();
            //            newWebElem.getChildren().addAll(labelNew, createSpacerHoriz());
            //            stackLabelNew.getChildren().addAll(newWebElem);

            //            stackLabelNew.setAlignment(Pos.CENTER);
            //            VBox elements4VBox = new VBox(stackLabelNew, scannedElements4);

            //            boxListViews.getChildren().addAll(elements1VBox, elements2VBox, elements3VBox, textFieldVBox);
            //            boxListViews.getChildren().addAll(elements1VBox, elements2VBox, textFieldVBox);
            boxListViews.getChildren().addAll(elements2VBox, textFieldVBox);
            //                    .addAll(elements1VBox, elements2VBox, elements3VBox, elements4VBox, textFieldVBox);

            VBox.setVgrow(boxListViews, Priority.ALWAYS);
            HBox.setHgrow(boxListViews, Priority.ALWAYS);

            HBox blockAndUrl = new HBox();
            blockAndUrl.setSpacing(0); // No spacing, use margins instead
            HBox.setMargin(comboBoxBlocks, new Insets(0, 3, 0, 0)); // Right margin of 3 pixels
            HBox.setMargin(refreshBlocksButton, new Insets(0, 3, 0, 0)); // Right margin of 3 pixels
            blockAndUrl.getChildren().addAll(comboBoxBlocks, refreshBlocksButton, currentURLBox);

            verticalBox.getChildren().addAll(blockAndUrl, boxListViews);
            VBox.setVgrow(verticalBox, Priority.ALWAYS);

            VBox.setVgrow(bottomPane, Priority.NEVER);
            VBox.setVgrow(bottomPaneTime, Priority.NEVER);

            contentPane.getChildren().addAll(topPane, verticalBox, bottomPaneTime, bottomPane);

            AnchorPane.setBottomAnchor(bottomPane, -45.0);

            AnchorPane.setLeftAnchor(
                    bottomPane, 0.0); // Optional: Anchors the left edge of bottomPane to the left of the AnchorPane
            AnchorPane.setRightAnchor(
                    bottomPane, 0.0); // Optional: Anchors the right edge of bottomPane to the right of the AnchorPane

            AnchorPane.setTopAnchor(verticalBox, 0.0);
            AnchorPane.setBottomAnchor(verticalBox, 0.0);
            AnchorPane.setLeftAnchor(verticalBox, 0.0);
            AnchorPane.setRightAnchor(verticalBox, 0.0);

            AnchorPane.setTopAnchor(topPane, 0.0);
            AnchorPane.setLeftAnchor(topPane, 0.0);
            AnchorPane.setRightAnchor(topPane, 0.0);

            //            // Add a listener to the children list of bottomPane
            //            bottomPane.getChildren().addListener((ListChangeListener<Node>) change -> {
            //                while (change.next()) {
            //                    if (change.wasAdded()) {
            //                        for (Node node : change.getAddedSubList()) {
            //                            scheduleRemoval(bottomPane, node, 3000); // Schedule removal for the newly
            // added node
            //                        }
            //                    }
            //                }
            //            });

            bottomPaneTime.getChildren().addListener((ListChangeListener<Node>) change -> {
                while (change.next()) {
                    if (change.wasAdded()) {
                        for (Node node : change.getAddedSubList()) {
                            scheduleRemoval(bottomPaneTime, node, 3300); // Schedule removal for the newly added node
                        }
                    }
                }
            });

        } catch (Exception ex) {
            ARLogger.getInstance(ARScannedElementPane.class).fine("Error using Separator line\n" + ex);
        }
    }

    private void refreshBlocks(boolean secondItem) {
        this.blockLoadList = performDataBase.loadBlocksByBotJobId(this.botJobLoad.getId());
        loadAllBlockItems(this.blockLoadList);

        if (!secondItem) {
            comboBoxBlocks.getSelectionModel().selectFirst(); // Select the first item
        } else {
            comboBoxBlocks.getSelectionModel().select(1); // Select the second item (index 1)
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
        if (performActions.getCurrentDriver().getWindowHandles().size() > 1 && currentTabIndex > 0) {
            // Decrease the index to move to the left
            currentTabIndex--;

            // Switch to the previous tab
            performActions.getCurrentDriver().switchTo().window(performActions.windowHandlesList.get(currentTabIndex));
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
                && currentTabIndex < performActions.windowHandlesList.size() - 1) {
            // Increase the index to move to the right
            currentTabIndex++;

            // Switch to the next tab
            performActions.getCurrentDriver().switchTo().window(performActions.windowHandlesList.get(currentTabIndex));
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
            currentTabIndex = performActions.windowHandlesList.size() - 1; // The last index in the list
            performActions.getCurrentDriver().switchTo().window(performActions.windowHandlesList.get(currentTabIndex));

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

    private void scheduleRemoval(HBox bottomPane, Node node, int delayMillis) {
        executorService = Executors.newCachedThreadPool();

        executorService.execute(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(delayMillis); // Wait for the specified delay
            } catch (InterruptedException e) {
                System.out.println(e.getMessage());
            }
            Platform.runLater(
                    () -> bottomPane.getChildren().remove(node)); // Remove the node on the JavaFX Application Thread
        });

        if (executorService != null) {
            remainingSeconds = SECONDS;
            executorService.shutdown();
        }
    }

    @Override
    public void initUIBehaviour() {
        try {
            reduceSearchCriteria =
                    !Strings.isNullOrEmpty(managerProps.getProperty(ARPropertyEnum.REDUCE_SEARCH_CRITERIA))
                            ? Integer.parseInt(managerProps.getProperty(ARPropertyEnum.MAX_LOG_SIZE))
                            : 20;
        } catch (Exception ex) {
            ARLogger.getInstance(ARScannedElementPane.class)
                    .fine("REDUCE_SEARCH_CRITERIA is Empty -> Setting REDUCE_SEARCH_CRITERIA to Max 20elements");
            reduceSearchCriteria = 20;
        }

        //        configureButton.setOnMouseClicked(e -> new ARConfigurationScene().show());
        configureButton.setOnMouseClicked(e -> arNewHomeBankingScene.show());
        launchBotJobButton.setOnMouseClicked(e -> {
            //                        loadBotJob(botJob);

            if (!lastBrowserTab()) {
                return;
            }

            this.botJobLoadList = performDataBase.loadCompleteJobs(botJobLoad.getId());
            instructionsExecuted.clear();

            // Set all instructions' executed field to false
            botJobLoadList.get(0).getBlockLoadDTOList().stream()
                    .flatMap(block -> block.getInstructionLoadDTOS().stream())
                    .forEach(instruction -> instruction.setExecuted(false));

            recallJob();
        });

        recallJobButton.setOnMouseClicked(e -> {
            if (!lastBrowserTab()) {
                return;
            }

            this.botJobLoadList = performDataBase.loadCompleteJobs(botJobLoad.getId());
            // loadBotJob(botJob);
            recallJob();
        });
        //        checkPickElement.setOnMouseClicked(e -> {
        //            performActions.getCurrentDriver().switchTo().defaultContent();
        //            this.targetSelected = null;
        //            elementsFound.clear();
        //            resultElementSearch = false;
        //
        //            revertPickInjections(performActions.getCurrentDriver());
        //
        //            if (checkPickElement.isSelected()) {
        //                String[] dataArrayClone = {"*"};
        //                int finalPort = portSocket;
        //                Platform.runLater(() -> periodicHoverPickThread(
        //                        performActions.getCurrentDriver(), performActions.getCurrentDriver().getCurrentUrl(),
        // dataArrayClone,
        // finalPort));
        //            }
        //
        //            Platform.runLater(() -> {
        //                launchBotJobButton.setDisable(checkPickElement.isSelected());
        //                recallJobButton.setDisable(checkPickElement.isSelected());
        //
        //                checkTestAction.setDisable(checkPickElement.isSelected());
        //                checkTestAction.setSelected(false);
        //
        //                checkCloneElement.setDisable(checkPickElement.isSelected());
        //                checkCloneElement.setSelected(false);
        //
        //                if (!checkPickElement.isSelected()) {
        //                    defineNameField.clear();
        //                }
        //            });
        //        });
        checkCloneElement.setOnMouseClicked(e -> {
            performActions.getCurrentDriver().switchTo().defaultContent();
            this.targetSelected = null;
            elementsFound.clear();
            resultElementSearch = false;

            revertCloneInjections(performActions.getCurrentDriver());

            if (checkCloneElement.isSelected()) {
                String[] dataArrayClone = {"*"};
                int finalPort = portSocket;
                Platform.runLater(() -> periodicPickOneCloneThread(
                        performActions.getCurrentDriver(),
                        performActions.getCurrentDriver().getCurrentUrl(),
                        dataArrayClone,
                        finalPort,
                        homeBanking.getId()));
            } else {
                revertHoverPickInjections(performActions.getCurrentDriver());
            }

            Platform.runLater(() -> {
                launchBotJobButton.setDisable(checkCloneElement.isSelected());
                recallJobButton.setDisable(checkCloneElement.isSelected());

                checkTestAction.setDisable(checkCloneElement.isSelected());
                checkTestAction.setSelected(false);

                //                checkPickElement.setDisable(checkCloneElement.isSelected());
                //                checkPickElement.setSelected(false);

                if (!checkCloneElement.isSelected()) {
                    defineNameField.clear();
                }
            });
        });
        checkClickElement.setOnAction(event -> {
            if (checkClickElement.isSelected()) {
                checkInputText.setSelected(false);
                checkOutputText.setSelected(false);
            }
        });

        checkInputText.setOnAction(event -> {
            if (checkInputText.isSelected()) {
                checkClickElement.setSelected(false);
                checkOutputText.setSelected(false);
            }
        });

        checkOutputText.setOnAction(event -> {
            if (checkOutputText.isSelected()) {
                checkClickElement.setSelected(false);
                checkInputText.setSelected(false);
            }
        });

        scanIFrameButton.setOnAction(e -> manageUIScanIFrames("Scan iFrames and Nested Web Elements"));

        addButtonNewElement.setOnAction(e -> {
            if (elementsFound.size() == 0 && this.targetSelected.getElement() != null) {
                insertNewElement();
            } else {
                if (elementsFound.size() > 0) {
                    //                    webElementObservableList2.clear();

                    Optional<ElementDTO> iframeElement = elementsFound.stream()
                            .filter(element -> "clicked-iFrame".equalsIgnoreCase(element.getTypeElement()))
                            //                                || "clicked".equalsIgnoreCase(element.getTypeElement())
                            //                                ||
                            // "tagName-found".equalsIgnoreCase(element.getTypeElement()))
                            .findFirst(); // Get the first matching ElementDTO

                    if (iframeElement.isPresent()) {
                        insertNewElement(iframeElement.get(), elementsFound);
                    } else {
                        insertNewElement(elementsFound);
                    }
                    this.targetSelected.setElement(null);

                } else {
                    performMessage.errorMessage(
                            "Not Web Element to be Detected!",
                            "Release -> Checkbox -> \"HOVER PICK  ELEMENT\" or \"PICK ONE \" again!",
                            "\"Refresh\" the Page -> <CTRL + F5>",
                            "Try  \"HOVER PICK  ELEMENT\" or \"PICK ONE \" again!",
                            null,
                            0);
                }
            }
        });

        refreshInputFieldsButton.setOnAction(e -> refreshInputBtn("Input Web Elements"));
        refreshOutputFieldsButton.setOnAction(e -> refreshOutputBtn("Output Web Elements"));
        //        refreshOtherFieldsButton.setOnAction(e -> refreshOtherElemBtn("Others Types of Web Elements"));
        magicFieldsButton.setOnAction(e -> performActions.createOutputHtml("input", performActions.getCurrentDriver()));
        searchWithIdsButton.setOnAction(e -> refreshWithIdsBtn("Web Elements with Attribute ID"));
        searchWithNamesButton.setOnAction(e -> refreshWithNamesBtn("Web Elements with Attribute Name"));
        searchButtons.setOnAction(e -> refreshSearchButtons("Web Elements without Attributes Name/ID"));

        turnOnOffButton.setOnAction(e -> {
            searchHiddenFields = !searchHiddenFields; // Toggle value

            if (searchHiddenFields) {
                turnOnOffButton.setText("Search Hidden Fields: ON");
                turnOnOffButton.setStyle("-fx-background-color: green; -fx-text-fill: white;");
            } else {
                turnOnOffButton.setText("Search Hidden Fields: Off");
                turnOnOffButton.setStyle("-fx-background-color: grey; -fx-text-fill: white;");
            }
        });

        //        scannedElements1.getItems().addListener(this::addBehaviourToAddedElements);
        //        scannedElements2.getItems().addListener(this::addBehaviourToAddedElements);
        //        scannedElements3.getItems().addListener(this::addBehaviourToAddedElements);

        //        manageUIScan();
    }

    private boolean lastBrowserTab() {
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

    private void insertNewElement() {

        if (Strings.isNullOrEmpty(defineNameField.getText().trim())) {

            Text variableText1Styled = new Text("Web Element \"NAME\" must be defined!");
            variableText1Styled.setStyle("-fx-font-size: 18px; -fx-fill: red;");

            VBox combinedTextContainer = new VBox();
            combinedTextContainer.setSpacing(5); // Add some sp

            combinedTextContainer.getChildren().add(variableText1Styled);

            performMessage.showAlertCombinedVBOX(
                    Alert.AlertType.ERROR, "MANDATORY FIELD", "Define the Element Name", null, combinedTextContainer);

            return;
        }

        if (this.targetSelected != null) {

            TargetElement cloneTarget = new TargetElement(this.targetSelected);

            //            if (checkCloneElement.isSelected()) {
            cloneTarget.setCloned(true);
            if (checkInputText.isSelected()) {
                cloneTarget.setTagType(WebElementTagNameEnum.INPUT);
                cloneTarget.setIconType(WebElementIcon.INSERT);
            } else if (checkClickElement.isSelected()) {
                cloneTarget.setTagType(WebElementTagNameEnum.BUTTON);
                cloneTarget.setIconType(WebElementIcon.CLICK);
            } else if (checkOutputText.isSelected()) {
                cloneTarget.setTagType(WebElementTagNameEnum.OUTPUT);
                cloneTarget.setIconType(WebElementIcon.OUTPUT);
            }
            //            }

            if (defineNameField.getText().trim() != cloneTarget.getDefinedName()) {
                cloneTarget.setDefinedName(defineNameField.getText().trim());
                cloneTarget.setNameLabel(defineNameField.getText().trim());
                cloneTarget.setNameField(defineNameField.getText().trim());
            }

            //            if (!Strings.isNullOrEmpty(searchAttribNameField.getText().trim())) {
            //                cloneTarget.setAttribSearchName(searchAttribNameField.getText().trim());
            //            }

            if (!Strings.isNullOrEmpty(searchAttribValueField.getText().trim())) {
                cloneTarget.setSearchAttributeValue(
                        searchAttribValueField.getText().trim());
            }

            try {
                if (cloneTarget.getElement() != null) {

                    ARWebElement arWebElement = new ARWebElement(cloneTarget, botJobLoad.getId());
                    if (arWebElement != null && arWebElement.getElement() != null) {
                        //                        webElementObservableList2.add(arWebElement);
                        //                        Platform.runLater(() -> {
                        //                            scannedElements2.refresh();
                        //                        });
                    }

                } else {
                    ARLogger.getInstance(ARScannedElementPane.class).severe("Could not find the Web Element!");
                    performMessage.errorMessage(
                            "Not Web Element to be Detected!",
                            "Release -> Checkbox -> \"HOVER PICK  ELEMENT\" or \"PICK ONE \"   again!",
                            "\"Refresh\" the Page -> <CTRL + F5>",
                            "Try  \"HOVER PICK  ELEMENT\" or \"PICK ONE \"   again!",
                            null,
                            0);
                }

            } catch (Exception ex) {
                ARLogger.getInstance(ARScannedElementPane.class)
                        .severe("Error Attempt to create a Dynamic Element\n" + ex.getMessage());
                performMessage.errorMessage(
                        "\"Error Attempt to create a Dynamic Element!",
                        "Release -> Checkbox -> \"HOVER PICK  ELEMENT\" or \"PICK ONE \"   again!",
                        "\"Refresh\" the Page -> <CTRL + F5>",
                        "Try  \"HOVER PICK  ELEMENT\" or \"PICK ONE \"   again!",
                        null,
                        0);
            }
        }
    }

    private boolean insertNewElement(ElementDTO iframeElementDTO, List<ElementDTO> elementsFound) {

        try {
            // Locate and switch to the iframe first
            WebElement iframe = performActions.getCurrentDriver().findElement(By.xpath(iframeElementDTO.getXPath()));

            // I Need to create a new targetElement
            TargetElement targetIFrame = performActions.defineSearchReturn(iframeElementDTO, iframe, null);
            targetIFrame = performActions.defineTargetNameTitles(targetIFrame);

            performActions.getCurrentDriver().switchTo().frame(iframe);

            // Adding the Variants for iFrame
            try {

                //                targetIFrame.setElement(iframe);
                targetIFrame.setIFrameXPath(iframeElementDTO.getXPath());
                targetIFrame.setDefinedName("iFrame");
                targetIFrame.setOriginalTagName("iFrame");
                //                targetIFrame.setXPathWorkedFirst(ARConstants.REGULAR_XPATH);
                //                targetIFrame.setMainXPath(iframeElement.getXPath());
                //                targetIFrame.setCurrentXPath(iframeElement.getXPath());
                //                targetIFrame.setAllAttributes(iframeElement.getAllAttributes());
                targetIFrame.setAttributeValue("iFrame");
                targetIFrame.setMainCoordinates(iframeElementDTO.getCoords());
                targetIFrame.setCoords(iframeElementDTO.getCoords());
                //                targetLocal.setTagType(WebElementTagNameEnum.BUTTON);
                //                targetLocal.setIconType(WebElementIcon.CLICK);

                ARWebElement arWebElement = new ARWebElement(targetIFrame, botJobLoad.getId());
                if (arWebElement != null && arWebElement.getElement() != null) {
                    //                    webElementObservableList2.add(arWebElement);
                    //                    Platform.runLater(() -> scannedElements2.refresh());
                }

                System.out.println("IFrame Element as Button: " + targetIFrame.getDefinedName());
            } catch (Exception e) {
                System.out.println("IFrame was Not Added as Button" + targetIFrame.getDefinedName());
            }

            // Loop through and find elements
            //            addProgressBar(elementsFound.size());

            for (ElementDTO elementChild : elementsFound) { // Start from index 1

                if (elementChild.getTypeElement().equalsIgnoreCase("clicked-iFrame")) {
                    continue;
                }

                if (elementChild.getTagName().equalsIgnoreCase("html")
                        || elementChild.getTagName().equalsIgnoreCase("body")
                        || elementChild.getTagName().equalsIgnoreCase("main")
                        || elementChild.getTagName().equalsIgnoreCase("script")
                        || elementChild.getTagName().equalsIgnoreCase("meta")
                        || elementChild.getTagName().equalsIgnoreCase("head")
                        || elementChild.getTagName().equalsIgnoreCase("style")) {
                    continue;
                }

                try {
                    WebElement elemFound =
                            performActions.getCurrentDriver().findElement(By.xpath(elementChild.getXPath()));

                    elementChild.setIFrameXPath(iframeElementDTO.getXPath());

                    TargetElement targetChild = performActions.defineSearchReturn(elementChild, elemFound, null);
                    targetChild = performActions.defineTargetNameTitles(targetChild);

                    ARWebElement arWebElement = new ARWebElement(targetChild, botJobLoad.getId());

                    if (arWebElement != null && arWebElement.getElement() != null) {
                        //                        webElementObservableList2.add(arWebElement);
                        //                        Platform.runLater(() -> {
                        //                            scannedElements2.refresh();
                        //                        });
                    }

                    System.out.println(
                            "Found element: " + elemFound.getTagName() + " with XPath: " + elementChild.getXPath());
                } catch (Exception e) {
                    System.out.println("Element not found for XPath: " + elementChild.getXPath());
                    ARConstants.DialogModal respModal = performMessage.showCustomModalDialogDragWin11(
                            "Fail Searching IFrame Elements",
                            "Error: Attempt identify IFrame elements",
                            "\"iFrame Web Elements\"",
                            "Action: Search iFrame Elements!",
                            null,
                            true,
                            "Continue",
                            "stop all",
                            0);
                    if (respModal.equals(ARConstants.DialogModal.STOP)) {
                        return true;
                    }
                    //                    performMessage.generalErrorIFrame(elementChild.getXPath());
                }
            }

            Platform.runLater(() -> {
                try {
                    Thread.sleep(2000);
                    bottomPane.getChildren().clear();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                //                scannedElements1.requestLayout();
                //                scannedElements1.refresh();
                //                scannedElements2.requestLayout();
                //                scannedElements2.refresh();
                bottomPane.requestLayout();
            });

        } catch (Exception e) {
            browserNotAttached();
        } finally {

            //            Platform.runLater(() -> {
            ////                scannedElements1.requestLayout();
            ////                scannedElements1.refresh();
            //                //                scannedElements2.requestLayout();
            //                //                scannedElements2.refresh();
            //                bottomPane.requestLayout();
            //            });

            // Close the browser
            performActions.getCurrentDriver().switchTo().defaultContent();
        }
        return false;
    }

    private void insertNewElement(String iFrameXPath, List<ElementDTO> elementsFound) {

        try {
            // Locate and switch to the iframe first
            WebElement iframe = performActions.getCurrentDriver().findElement(By.xpath(iFrameXPath));
            performActions.getCurrentDriver().switchTo().frame(iframe);

            TargetElement targetFound = new TargetElement();
            // Loop through and find elements
            for (ElementDTO elementFound : elementsFound) { // Start from index 1

                if (elementFound.getTagName().equalsIgnoreCase("html")
                        || elementFound.getTagName().equalsIgnoreCase("body")
                        || elementFound.getTagName().equalsIgnoreCase("main")
                        || elementFound.getTagName().equalsIgnoreCase("script")
                        || elementFound.getTagName().equalsIgnoreCase("meta")
                        || elementFound.getTagName().equalsIgnoreCase("head")
                        || elementFound.getTagName().equalsIgnoreCase("style")) {
                    continue;
                }

                try {
                    WebElement element =
                            performActions.getCurrentDriver().findElement(By.xpath(elementFound.getXPath()));
                    //                                elements.add(element);

                    targetFound = performActions.defineSearchReturn(elementFound, element, targetFound);

                    ARWebElement arWebElement = new ARWebElement(targetFound, botJobLoad.getId());
                    if (arWebElement != null && arWebElement.getElement() != null) {
                        //                        webElementObservableList2.add(arWebElement);
                        //                        Platform.runLater(() -> {
                        //                            scannedElements2.refresh();
                        //                        });
                    }

                    System.out.println(
                            "Found element: " + element.getTagName() + " with XPath: " + elementFound.getXPath());
                } catch (Exception e) {
                    System.out.println("Element not found for XPath: " + elementFound.getXPath());
                    performMessage.generalErrorIFrame(elementFound.getTagName());
                }
            }

        } catch (Exception e) {
            //            browserNotAttached();
        } finally {
            // Close the browser
            performActions.getCurrentDriver().switchTo().defaultContent();
        }
    }

    private boolean insertNewElement(List<ElementDTO> elementsDTO) {

        try {
            // Loop through and find elements
            for (ElementDTO elementDTO : elementsDTO) { // Start from index 1
                WebElement elementSearched = null;
                if (elementDTO.getTagName().equalsIgnoreCase("html")
                        || elementDTO.getTagName().equalsIgnoreCase("body")
                        || elementDTO.getTagName().equalsIgnoreCase("main")
                        || elementDTO.getTagName().equalsIgnoreCase("script")
                        || elementDTO.getTagName().equalsIgnoreCase("meta")
                        || elementDTO.getTagName().equalsIgnoreCase("head")
                        || elementDTO.getTagName().equalsIgnoreCase("style")) {
                    continue;
                }

                try {
                    WebElement elementFound =
                            performActions.getCurrentDriver().findElement(By.xpath(elementDTO.getXPath()));
                    //                                elements.add(element);

                    if (elementFound != null) {
                        // I Need to create a new targetElement
                        TargetElement targetLocal = performActions.defineSearchReturn(elementDTO, elementFound, null);

                        targetLocal = performActions.defineTargetNameTitles(targetLocal);

                        // First  Search for xPath
                        //                        TargetElement targetValidated =
                        // checkValidateSearchPriorities(targetLocal);

                        if (!elementDTO.getTagName().equalsIgnoreCase("clicked")) {

                            ARWebElement arWebElement = new ARWebElement(targetLocal, botJobLoad.getId());
                            targetLocal.setElement(null);

                            if (arWebElement != null && arWebElement.getElement() != null) {
                                if (resultElementSearch) {
                                    //                                    webElementObservableList1.add(arWebElement);
                                    //                                    Platform.runLater(() -> {
                                    //                                        scannedElements1.refresh();
                                    //                                    });
                                } else {
                                    //                                    webElementObservableList2.add(arWebElement);
                                    //                                    Platform.runLater(() -> {
                                    //                                        scannedElements2.refresh();
                                    //                                    });
                                }
                            }

                            System.out.println("Found element: " + elementFound.getTagName() + " with XPath: "
                                    + elementDTO.getXPath());

                        } else {
                            defineCheckBoxesClickabe(targetLocal);
                        }
                    }

                } catch (Exception e) {
                    xpathTextPrevious = ""; // Allows clicking in the same element again
                    System.out.println("Element not found for XPath: " + elementDTO.getXPath());
                    ARConstants.DialogModal respModal = performMessage.showCustomModalDialogDragWin11(
                            "Error selecting Web Element",
                            "Mandatory Value not Defined",
                            "Not able defining the Name/Label for the New AR Element",
                            null,
                            null,
                            true,
                            "Continue",
                            "stop all",
                            0);

                    if (respModal.equals(ARConstants.DialogModal.STOP)) {
                        elementsDTO.clear();
                        return true;
                    }
                }
            }

            elementsDTO.clear();

        } catch (Exception e) {
            //            browserNotAttached();
        } finally {
            // Close the browser
            performActions.getCurrentDriver().switchTo().defaultContent();
        }
        return false;
    }

    private TargetElement extractPickClone(ElementDTO elementDTO) {

        xpathTextPrevious = elementDTO.getXPath();

        WebElement elementFound = null;
        if (!Strings.isNullOrEmpty(elementDTO.getShadowHost()) || !Strings.isNullOrEmpty(elementDTO.getShadowRoot())) {
            elementFound = performActions.findShadowElementByCssSelector(
                    elementDTO.getShadowHost(), elementDTO.getCssSelector());
        } else if (!Strings.isNullOrEmpty(elementDTO.getIFrameXPath())) {

            try {
                WebElement iFrame =
                        performActions.getCurrentDriver().findElement(By.xpath(elementDTO.getIFrameXPath()));

                performActions.getCurrentDriver().switchTo().frame(iFrame);
                elementFound = performActions.getCurrentDriver().findElement(By.xpath(elementDTO.getXPath()));
            } catch (Exception error) {
                ARLogger.getInstance(ARScannedElementPane.class)
                        .info("iFrame Element not Located\niFrameXPath"
                                + elementDTO.getIFrameXPath()
                                + "iFrameChild: "
                                + elementDTO.getXPath());
            }
        } else {
            elementFound = performActions.getCurrentDriver().findElement(By.xpath(elementDTO.getXPath()));
        }

        TargetElement targetLocal = performActions.defineSearchReturn(elementDTO, elementFound, null);

        targetLocal = performActions.defineTargetNameTitles(targetLocal);

        // First  Search for xPath
        TargetElement targetValidated = checkValidateSearchPriorities(targetLocal);

        if (targetValidated.getElement() == null) {

            performMessage.errorMessage(
                    "I Cannot define this element",
                    "I will use the Locato \"COORDINATES\"",
                    "Try to get it again -> \"HOVER PICK  ELEMENT\" or \"PICK ONE \"",
                    null,
                    null,
                    0);

            return null;
        }

        //        targetElement = performActions.defineTagType(targetElement);

        defineCheckBoxesClickabe(targetLocal);

        return targetLocal;
    }

    private void defineCheckBoxesClickabe(TargetElement targetCheck) {
        boolean clickable = isClickable(targetCheck.getElement());

        boolean tagClickable = false;
        // Define regex to extract specific tags (e.g., a, button)
        String regex = "/([^/\\[]+)";
        Pattern pattern = Pattern.compile(regex);

        // Iterate through each attribute in the array
        for (AttributeData attribute : targetCheck.getAttributeData()) {
            // Assuming you want to use the value of the attribute for matching
            String attributeValue = attribute.getValue(); // Get the value of the attribute

            Matcher matcher = pattern.matcher(attributeValue); // Use the value for matching

            // Check for matches in the current attribute value
            while (matcher.find()) {
                String tag = matcher.group(1);
                if (tag.equals("a") || tag.equals("button")) {
                    System.out.println("Found clickable tag: <" + tag + ">");
                    tagClickable = true;
                    break;
                }
            }
            if (tagClickable) {
                break; // Exit the loop once a clickable tag is found
            }
        }

        Boolean inputContains = targetCheck.getOriginalTagName().toLowerCase().contains("input");

        Boolean selectContains = targetCheck.getOriginalTagName().toLowerCase().contains("select");

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

    //    private TargetElement defineTargetTagType(TargetElement target) {
    //    targetElement = performActions.defineTargetNameTitles(targetElement);
    //    targetElement performActions.defineTagType(TargetElement targetTagType) {
    //    defineTagTypeAdvanced

    // iFrames
    private TargetElement defineTagTypeAdvanced(
            WebElement elementChild, String iFrameXPathScan, String xPathElementChild, TargetElement targetIFrames) {
        try {

            String tagName = elementChild.getTagName().toLowerCase();
            String retrievedValue = "";

            switch (tagName.toLowerCase()) {
                    // Handle form elements and text inputs
                case "input":
                    targetIFrames.setTagType(WebElementTagNameEnum.INPUT);
                    targetIFrames.setIconType(WebElementIcon.INSERT);
                    elementChild.sendKeys("abc123");
                    retrievedValue = elementChild.getAttribute("value");
                    String placeholder = elementChild.getAttribute("placeholder");
                    if ((retrievedValue == null || retrievedValue.isEmpty())
                            && placeholder != null
                            && !placeholder.isEmpty()) {
                        retrievedValue = placeholder; // Use placeholder if value is empty
                    }
                    break;

                case "textarea":
                    targetIFrames.setTagType(WebElementTagNameEnum.TEXT_AREA);
                    targetIFrames.setIconType(WebElementIcon.INSERT);
                    retrievedValue = elementChild.getAttribute("value");
                    placeholder = elementChild.getAttribute("placeholder");
                    if ((retrievedValue == null || retrievedValue.isEmpty())
                            && placeholder != null
                            && !placeholder.isEmpty()) {
                        retrievedValue = placeholder; // Use placeholder if value is empty
                    }
                    break;

                    // Handle button, form, select, option, and material elements
                case "button":
                    targetIFrames.setTagType(WebElementTagNameEnum.BUTTON);
                    targetIFrames.setIconType(WebElementIcon.CLICK);
                    break;
                case "form":
                    targetIFrames.setTagType(WebElementTagNameEnum.FORM);
                    targetIFrames.setIconType(WebElementIcon.TEXT);
                    break;
                case "select":
                    targetIFrames.setTagType(WebElementTagNameEnum.SELECT);
                    targetIFrames.setIconType(WebElementIcon.CLICK);
                    break;
                case "option":
                    targetIFrames.setTagType(WebElementTagNameEnum.OPTION);
                    targetIFrames.setIconType(WebElementIcon.CLICK);
                    break;
                case "mat-select":
                    targetIFrames.setTagType(WebElementTagNameEnum.MAT_SELECT);
                    targetIFrames.setIconType(WebElementIcon.CLICK);
                    break;
                case "mat-option":
                    targetIFrames.setTagType(WebElementTagNameEnum.MAT_OPTION);
                    targetIFrames.setIconType(WebElementIcon.CLICK);
                    break;
                case "mat-expansion-panel":
                    targetIFrames.setTagType(WebElementTagNameEnum.MAT_EXPANSION_PANEL);
                    targetIFrames.setIconType(WebElementIcon.CLICK);
                    break;

                    // Handle text container elements (div, span, etc.)
                case "div":
                case "span":
                case "section":
                case "article":
                case "aside":
                case "header":
                case "footer":
                    targetIFrames.setTagType(WebElementTagNameEnum.DIV);
                    targetIFrames.setIconType(WebElementIcon.OUTPUT);
                    retrievedValue = elementChild.getText(); // Retrieve the text content
                    break;

                    // Handle heading tags (h1-h6)
                case "h1":
                case "h2":
                case "h3":
                case "h4":
                case "h5":
                case "h6":
                    targetIFrames.setTagType(WebElementTagNameEnum.OUTPUT);
                    targetIFrames.setIconType(WebElementIcon.OUTPUT);
                    retrievedValue = elementChild.getText(); // Retrieve heading text
                    break;

                    // Handle anchor tags (links)
                case "a":
                    targetIFrames.setTagType(WebElementTagNameEnum.ANCHOR);
                    targetIFrames.setIconType(WebElementIcon.CLICK);
                    retrievedValue = elementChild.getText(); // Retrieve link text
                    break;

                    // Handle other cases (default)
                default:
                    targetIFrames.setTagType(WebElementTagNameEnum.ALL);
                    targetIFrames.setIconType(WebElementIcon.TEXT);
                    break;
            }

            // If you need to handle `retrievedValue` further or return it, do so here:
            if (retrievedValue != null && !retrievedValue.isEmpty()) {
                // Perform any further actions with the `retrievedValue`, e.g., logging or
                // storing.
            }

            targetIFrames.setElement(elementChild);
            targetIFrames.setIFrameXPath(iFrameXPathScan);
            targetIFrames.setDefinedName(tagName);
            targetIFrames.setOriginalTagName(tagName);
            targetIFrames.setXPathWorkedFirst(ARConstants.REGULAR_XPATH);

            targetIFrames.setMainXPath(xPathElementChild);
            targetIFrames.setAttributeData(new AttributeData[0]); // TO DO
            targetIFrames.setCurrentXPath(xPathElementChild);
            targetIFrames.setAttributeValue(retrievedValue);

            if (tagName.equalsIgnoreCase(WebElementTagNameEnum.BUTTON.getValue())
                    || tagName.equalsIgnoreCase(WebElementTagNameEnum.ANCHOR.getValue())
                    || tagName.equalsIgnoreCase(WebElementTagNameEnum.DIV.getValue())
                    || tagName.equalsIgnoreCase(WebElementTagNameEnum.OPTION.getValue())
                    || tagName.equalsIgnoreCase(WebElementTagNameEnum.MAT_SELECT.getValue())) {
                targetIFrames.setTagType(WebElementTagNameEnum.BUTTON);
                targetIFrames.setIconType(WebElementIcon.CLICK);
            } else if (tagName.equalsIgnoreCase(WebElementTagNameEnum.INPUT.getValue())
                    || tagName.equalsIgnoreCase(WebElementTagNameEnum.TEXT_AREA.getValue())) {
                targetIFrames.setTagType(WebElementTagNameEnum.INPUT);
                targetIFrames.setIconType(WebElementIcon.INSERT);
            } else {
                targetIFrames.setTagType(WebElementTagNameEnum.ALL);
                targetIFrames.setIconType(WebElementIcon.TEXT);
            }

            if (targetIFrames.getDefinedName() != null) {
                ARWebElement arWebElement = new ARWebElement(targetIFrames, botJobLoad.getId());
                if (arWebElement != null && arWebElement.getElement() != null) {
                    //                    webElementObservableList1.add(arWebElement);
                    //                    Platform.runLater(() -> scannedElements1.refresh());
                }
                System.out.println("Found element: " + tagName + " with XPath: " + "xpath ???");
            }
            return targetIFrames;

        } catch (Exception error) {
            System.out.println("Element not found for XPath: " + "xpath ???");
            performMessage.generalErrorIFrame("xpath ???");
        }
        return null;
    }

    private TargetElement checkValidateSearchPriorities(TargetElement target) {
        WebElement elementValid = null;
        if (!Strings.isNullOrEmpty(target.getCurrentXPath())) {

            if (target.getForceCoordinates() != null && target.getForceCoordinates()) {
                // Try by coordinates
                try {
                    Pair<String, String> filedData = new Pair("&EMPTY", "&EMPTY");
                    boolean passed = performActions.executeActionsAtCoordinates(
                            target.getCoords(), filedData, ARConstants.VISUALIZE, false);
                    if (passed) {
                        elementValid = performActions.getElementFromCoordinates(target.getCoords());
                        if (elementValid != null && elementValid.getTagName() != null) {
                            target.setElement(elementValid);
                        }

                        target.setXPathWorkedFirst(ARConstants.SEARCH_COORD);
                    }

                } catch (Exception e) {
                    ARLogger.getInstance(ARScannedElementPane.class)
                            .warning(String.format(
                                    "Cannot locate a Web Element with Name: \n%s", target.getAttribName()));
                }
            } else if (elementValid == null) {
                try {
                    elementValid = performActions.getCurrentDriver().findElement(By.xpath(target.getCurrentXPath()));
                    if (elementValid != null && elementValid.getTagName() != null) {
                        target.setElement(elementValid);
                        target.setXPathWorkedFirst(
                                ARConstants.REGULAR_XPATH); // BECAUSE OS LIMITATION OF ACCESS DB 255 CHARACTER
                    }
                } catch (Exception e) {
                    ARLogger.getInstance(ARScannedElementPane.class)
                            .warning(String.format(
                                    "Cannot locate a Web Element with Regular XPath\n%s", target.getCurrentXPath()));
                }
            } else if (elementValid == null) {
                try {
                    elementValid = performActions.getCurrentDriver().findElement(By.xpath(target.getCustomXPath()));
                    if (elementValid != null && elementValid.getTagName() != null) {
                        target.setElement(elementValid);
                        target.setXPathWorkedFirst(
                                ARConstants.CUSTOM_XPATH); // BECAUSE OS LIMITATION OF ACCESS DB 255 CHARACTER
                    }
                } catch (Exception e) {
                    ARLogger.getInstance(ARScannedElementPane.class)
                            .warning(String.format(
                                    "Cannot locate a Web Element with Absolut XPath\n%s", target.getAttributeData()));
                }
            } else {
                if (elementValid == null) {
                    //            if (searchReturn.getCurrentXPath().startsWith("id(")) {
                    if (!Strings.isNullOrEmpty(target.getAttribId())) {
                        try {
                            elementValid = performActions.getCurrentDriver().findElement(By.id(target.getAttribId()));
                            if (elementValid != null && elementValid.getTagName() != null) {
                                target.setElement(elementValid);
                                target.setXPathWorkedFirst(ARConstants.ATTRIBUTE_ID);
                                target.setAttributeType("id");
                                target.setAttributeValue(target.getAttribId());
                            }
                        } catch (Exception e) {
                            ARLogger.getInstance(ARScannedElementPane.class)
                                    .warning(String.format(
                                            "Cannot locate a Web Element with ID: \n%s", target.getAttribId()));
                        }
                    }
                } else if (elementValid == null) {

                    if (!Strings.isNullOrEmpty(target.getAttribName())) {
                        try {
                            elementValid =
                                    performActions.getCurrentDriver().findElement(By.name(target.getAttribName()));
                            if (elementValid != null && elementValid.getTagName() != null) {
                                target.setElement(elementValid);
                                target.setAttributeType("name");
                                target.setXPathWorkedFirst(ARConstants.ATTRIBUTE_NAME);
                            }
                        } catch (Exception e) {
                            ARLogger.getInstance(ARScannedElementPane.class)
                                    .warning(String.format(
                                            "Cannot locate a Web Element with Name: \n%s", target.getAttribName()));
                        }
                    }
                }
            }
        }

        target.setElement(elementValid);

        return target;
    }

    //
    //    Maybe Pre Test the Action
    //
    //    String isCurrentXPathOK;
    //    String isAllAttributesOK;
    //    String isCustomXPathOK;
    //    String isCoordsOK;
    //    TO MAKE  PRE TEST    private WebElement checkBestAction() {

    private boolean isClickable(WebElement element) {
        List<WebElementTagNameEnum> clickableTags = WebElementTagNameEnum.clickableTags();
        boolean isClickableTag =
                clickableTags.stream().anyMatch(t -> t.getValue().equals(element.getTagName()));
        List<WebElementAttributeTypeValueEnum> clickableValues = WebElementAttributeTypeValueEnum.getClickableValues();
        boolean isClickableValue = clickableValues.stream()
                .anyMatch(v -> v.getValue().equals(element.getAttribute(WebElementAttributeEnum.TYPE.getValue())));
        boolean isInputTag = element.getTagName().equals(WebElementTagNameEnum.INPUT.getValue());
        return (isClickableTag && !isInputTag) || (isInputTag && isClickableValue && isClickableTag);
    }

    private void refreshSearchButtons(String searchType) {
        //        webElementObservableList1.clear();
        //        manageUIScanWithoutNameAndId();

        String[] dataArray = {"button"};

        handleSearchTermClick(dataArray);
    }

    private void refreshWithNamesBtn(String searchType) {
        //        webElementObservableList1.clear();
        //        manageUIScanAttributeNameFirst();

        String[] dataArray = {"with name"};

        handleSearchTermClick(dataArray);
    }

    private void refreshWithIdsBtn(String searchType) {
        //        webElementObservableList1.clear();
        //        manageUIScanIdsFirst();

        String[] dataArray = {"with id"};

        handleSearchTermClick(dataArray);
    }

    private void handleSearchTermClick(String[] dataArray) {
        //        webElementObservableList1.clear();

        performActions.getCurrentDriver().switchTo().defaultContent();
        checkTestAction.setSelected(false);

        resultElementSearch = true;

        elementsFound.clear();
        xpathTextPrevious = "";
        this.targetSelected = null;

        revertCloneInjections(performActions.getCurrentDriver());
        revertPickInjections(performActions.getCurrentDriver());

        int finalPort = portSocket;
        Platform.runLater(() -> periodicSearchThread(
                performActions.getCurrentDriver(),
                performActions.getCurrentDriver().getCurrentUrl(),
                dataArray,
                finalPort));
    }

    private void revertCloneButtons() {
        Platform.runLater(() -> {
            checkTestAction.setDisable(true);
            launchBotJobButton.setDisable(true);
            recallJobButton.setDisable(true);

            if (!checkCloneElement.isSelected()) {
                defineNameField.clear();
            }
        });
    }

    private void manageUIScanIFrames(String searchDesc) {
        ARLogger.getInstance(ARScannedElementPane.class).info("iFrames scan triggered");
        //        webElementObservableList1.clear();

        //        boolean scanOk = scanIframesAndElements(webElementObservableList1, searchDesc);
    }

    private void manageUIScanWithoutNameAndId() {
        idAttributeFirst = false;
        nameAttributeFirst = false;
        withoutNameAndId = true;
        // addProgressBar();

        // First Check About the Scanner havina  a Browser Attached
        //        boolean scanOk = scanARElementsAsync(
        //                null, null, null, webElementObservableList1, "input", "UI Scan \"Inputs\" Without Name And
        // Id");
        //        // addProgressBar();
        //        if (scanOk) {
        //            scanARElementsAsync(
        //                    null, null, null, webElementObservableList1, "button", "UI Scan \"Buttons\" Without Name
        // And Id");
        //        }
    }

    private void addProgressBar(int items) {
        int currentChildrenCount = bottomPane.getChildren().size();
        if (currentChildrenCount < 20) {
            // Calculate how many more ProgressBars can be added without exceeding 20
            int availableSlots = 20 - currentChildrenCount;

            // Determine the number of ProgressBars to add, ensuring it does not exceed available slots
            int progressBarCountToAdd = Math.min(items, availableSlots);

            Platform.runLater(() -> {
                for (int x = 0; x < progressBarCountToAdd; x++) {
                    ProgressBar progressBar = new ProgressBar();
                    bottomPane.getChildren().add(progressBar);
                }
            });
        }
    }

    //    private void manageUIScanAttributeNameFirst() {
    //        idAttributeFirst = false;
    //        nameAttributeFirst = true;
    //        withoutNameAndId = false;
    //        scanARElementsAsync(null, null, null, webElementObservableList1, "name", "UI Scan Attribute Name First");
    //    }

    //    private boolean manageUIScanIdsFirst() {
    //        idAttributeFirst = true;
    //        nameAttributeFirst = false;
    //        withoutNameAndId = false;
    //        return scanARElementsAsync(null, null, null, webElementObservableList1, "id", "UI Scan Ids First");
    //    }

    //    private void manageUIScanInputs() {
    //        List<WebElementTagNameEnum> inputTags = WebElementTagNameEnum.insertableTags();
    //        for (WebElementTagNameEnum tag : inputTags) {
    //            // addProgressBar();
    //            boolean scanOk = scanARElementsAsync(
    //                    null,
    //                    By.tagName(tag.getValue()),
    //                    ARWebElement::isNotClickable,
    //                    webElementObservableList1,
    //                    null,
    //                    "UI Scan Inputs");
    //
    //            if (!scanOk) {
    //                break;
    //            }
    //        }
    //    }

    //    private void manageUIScanClickable() {
    //        List<WebElementTagNameEnum> clickableTags = WebElementTagNameEnum.clickableTags();
    //        for (WebElementTagNameEnum tag : clickableTags) {
    //            // addProgressBar();
    //            boolean scanOK = scanARElementsAsync(
    //                    null,
    //                    By.tagName(tag.getValue()),
    //                    ARWebElement::isClickable,
    //                    webElementObservableList2,
    //                    null,
    //                    "UI Scan Clickable");
    //
    //            if (!scanOK) {
    //                break;
    //            }
    //        }
    //    }

    //    private void manageUIScanPriorities() {
    //        Set<WebElement> webElements = managePrioritiesCriteria();
    //        try {
    //            if (webElements != null && webElements.size() > 0) {
    //                // addProgressBar();
    //                scanARElementsAsync(webElements, null, null, webElementObservableList2, null, "UI Scan By
    // Priorities");
    //            }
    //        } catch (Exception e) {
    //            System.out.println("Error " + e.getMessage());
    //        }
    //    }

    //    private void manageUIScanOutputs() {
    //        scanARElementsAsync(By.xpath("CODE_CRITERIA"), webElementObservableList2, "UI Scan Outputs");
    //    }

    private void scanARElementsAsync(
            By criteria, ObservableList<ARWebElement> listToAddNewElements, String criteriaMSG) {
        scanARElementsAsync(null, criteria, null, listToAddNewElements, null, criteriaMSG);
    }

    private boolean scanARElementsAsync(
            Set<WebElement> preElements,
            By criteria,
            Predicate<ARWebElement> filterCondition,
            ObservableList<ARWebElement> listToAddNewElements,
            String elementType,
            String criteriaMSG) {

        // Check if Browser is Inactive
        try {
            windowHandles = performActions.getCurrentDriver().getWindowHandles();
        } catch (Exception e) {
            browserNotAttached();
            return false;
        }

        executorService = Executors.newCachedThreadPool();

        // External variables to hold the sizes
        AtomicInteger listARElementsSize = new AtomicInteger(0);
        AtomicInteger scannedElementListSize = new AtomicInteger(0);

        // Simulate async task completion with CompletableFuture
        // Simulate async task completion with CompletableFuture
        CompletableFuture<Void> future = CompletableFuture.runAsync(
                () -> {
                    List<ARWebElement> listARElements = new ArrayList<>();
                    List<WebElement> scannedElementList = new ArrayList<>();

                    // Separation between creation of AR Elements
                    try {
                        ARLogger.getInstance(ARScannedElementPane.class)
                                .fine("Starting scan of elements for criteria: "
                                        + (criteria != null ? criteria : criteriaMSG));

                        if (idAttributeFirst || nameAttributeFirst) {
                            mapAdvanced = findElementsWithXPath(performActions.getCurrentDriver(), elementType);
                            listARElements = createAdvancedARElement(mapAdvanced, elementType, null);
                        } else if (withoutNameAndId) {
                            mapAdvanced = findElementsWithoutIdOrName(performActions.getCurrentDriver(), elementType);
                            listARElements = createAdvancedARElement(mapAdvanced, elementType, null);
                        } else if (preElements != null && preElements.size() > 0) {
                            scannedElementList.addAll(preElements);
                        } else if (criteria != null) {
                            if (criteria.equals(By.xpath("CODE_CRITERIA"))) {
                                mapAdvanced = findElementsOutputCriteria(performActions.getCurrentDriver());
                                listARElements =
                                        createAdvancedARElement(mapAdvanced, elementType, WebElementTagNameEnum.OUTPUT);
                            } else {
                                scannedElementList =
                                        performActions.getCurrentDriver().findElements(criteria);
                            }
                        }
                        if (listARElements != null && listARElements.size() > 0) {
                            listARElementsSize.set(listARElements.size());
                            //                            addProgressBar(listARElements.size());
                            ARLogger.getInstance(ARScannedElementPane.class)
                                    .finer("list of Advanced Scanner elements has " + listARElementsSize.get());
                        }

                        // Reset these
                        idAttributeFirst = false;
                        nameAttributeFirst = false;
                        withoutNameAndId = false;

                        if (scannedElementList != null && scannedElementList.size() > 0) {
                            scannedElementListSize.set(scannedElementList.size());
                            addProgressBar(scannedElementListSize.get());

                            ARLogger.getInstance(ARScannedElementPane.class)
                                    .finer("list of scanned elements has " + scannedElementListSize.get()
                                            + " elements for Search Criteria " + criteria);
                            if (scannedElementListSize.get() > reduceSearchCriteria) {

                                ARLogger.getInstance(ARScannedElementPane.class)
                                        .fine("Reduces to the Limit of ARWebElements : " + reduceSearchCriteria);
                                List<WebElement> scannedElementListReduced =
                                        scannedElementListSize.get() > reduceSearchCriteria
                                                ? new ArrayList<>(scannedElementList.subList(0, reduceSearchCriteria))
                                                : scannedElementList;
                                scannedElementList.clear();
                                scannedElementList.addAll(scannedElementListReduced);
                                scannedElementListReduced.clear();
                            }

                            WebElementTagNameEnum typeSearch = null;
                            if (criteria != null && criteria.equals(By.tagName("input"))) {
                                typeSearch = WebElementTagNameEnum.INPUT;
                            }

                            try {

                                TargetElement targetElement = new TargetElement();
                                for (WebElement item : scannedElementList) {

                                    targetElement.setElement(item);
                                    targetElement.setDefinedName(
                                            targetElement.getElement().getTagName());
                                    targetElement = performActions.defineTargetNameTitles(targetElement);
                                    targetElement = performActions.defineTagType(targetElement);

                                    // First  Search for xPath
                                    //                                    TargetElement targetValidated =
                                    // checkValidateSearchPriorities(targetElement);

                                    if (targetElement.getDefinedName() != null) {
                                        ARWebElement arWebElement = new ARWebElement(targetElement, botJobLoad.getId());
                                        if (arWebElement != null) {
                                            listARElements.add(arWebElement);
                                            //                                            Platform.runLater(() -> {
                                            //
                                            // scannedElements1.requestLayout();
                                            //
                                            // scannedElements1.refresh();
                                            //                                            });
                                        }
                                    }
                                    targetElement.reset();
                                }
                                listARElementsSize.set(listARElements.size());
                            } catch (Exception e) {
                                ARLogger.getInstance(ARScannedElementPane.class)
                                        .fine("Final size of listARElements: " + listARElementsSize.get());
                            } finally {
                                ARLogger.getInstance(ARScannedElementPane.class).fine("Error on new ARWebElement");
                            }
                        }

                    } catch (EnumConstantNotPresentException ex) {
                        ARLogger.getInstance(ARScannedElementPane.class)
                                .severe("ENUM Property not Defined of Web Element\n " + ex.getMessage());
                        performActions.showAlert(
                                Alert.AlertType.ERROR,
                                "ERROR ADD WEB ELEMENT",
                                "Enum Constant Not Present!",
                                "Contact ADM to verify the \"Constant Locator Used\"\n"
                                        + "\"XPath , Attribute, Coordinates\" ... \n" + ex.getMessage());
                        return;

                    } catch (Exception e) {
                        shutDownExecutorService();
                        Thread.currentThread().interrupt();
                    }

                    // After Creation of AR Elements - > Update View List
                    if (listARElements != null) {
                        //                        addProgressBar(listARElements.size());
                        for (ARWebElement element : listARElements) {
                            Platform.runLater(() -> {
                                listToAddNewElements.add(element);
                                if (element.getSavedReferences() != null
                                        && element.getSavedReferences().size() > 0) {
                                    String absolutPath =
                                            element.getSavedReferences().get(0);
                                    ARLogger.getInstance(ARScannedElementPane.class)
                                            .finer(String.format(
                                                    "added ARWebElement with %s References -> xPath: ",
                                                    element.getSavedReferences().size(), absolutPath));

                                } else if (element.getSavedReferences() != null
                                        && element.getSavedReferences().size() == 0) {
                                    ARLogger.getInstance(ARScannedElementPane.class)
                                            .finer("added ARWebElement with NO References!");
                                }
                            });
                        }

                        if (bottomPane.getChildren().size() > 0) {
                            int elementsToRemove = Math.min(
                                    listARElementsSize.get() + scannedElementListSize.get(),
                                    bottomPane.getChildren().size());
                            for (int x = 0; x < elementsToRemove; x++) {
                                bottomPane
                                        .getChildren()
                                        .remove(bottomPane
                                                .getChildren()
                                                .get(bottomPane.getChildren().size() - 1));
                            }
                        }
                    }
                },
                executorService);

        if (executorService != null) {
            executorService.shutdown();
        }

        // Handle completion of the CompletableFuture to remove the ProgressBar
        future.handle((result, ex) -> {
            if (ex != null) {
                Platform.runLater(() -> {
                    if (bottomPane.getChildren().size() > 0) {
                        int elementsToRemove = Math.min(
                                listARElementsSize.get() + scannedElementListSize.get(),
                                bottomPane.getChildren().size());

                        for (int x = 0; x < elementsToRemove; x++) {
                            bottomPane
                                    .getChildren()
                                    .remove(bottomPane
                                            .getChildren()
                                            .get(bottomPane.getChildren().size() - 1));
                        }

                        for (int x = 0; x < bottomPane.getChildren().size(); x++) {
                            bottomPane
                                    .getChildren()
                                    .remove(bottomPane
                                            .getChildren()
                                            .get(bottomPane.getChildren().size() - 1));
                        }
                    }
                });
            } else {
                //                Platform.runLater(() -> {
                //                    scannedElements1.requestLayout();
                //                    scannedElements1.refresh();
                //                });
            }
            return result;
        });

        // Force complete the future exceptionally on error
        future.exceptionally(ex -> {
            future.completeExceptionally(ex);
            return null;
        });

        // Handle completion of the CompletableFuture to remove the ProgressBar
        future.thenRun(() -> {
            Platform.runLater(() -> {
                ARLogger.getInstance(ARScannedElementPane.class)
                        .fine("thenRun executed. Sizes: " + "listARElements="
                                + listARElementsSize.get() + ", scannedElementList="
                                + scannedElementListSize.get());

                if (bottomPane.getChildren().size() > 0) {
                    int elementsToRemove = Math.min(
                            listARElementsSize.get() + scannedElementListSize.get(),
                            bottomPane.getChildren().size());
                    for (int x = 0; x < elementsToRemove; x++) {
                        bottomPane
                                .getChildren()
                                .remove(bottomPane
                                        .getChildren()
                                        .get(bottomPane.getChildren().size() - 1));
                    }

                    for (int x = 0; x < bottomPane.getChildren().size(); x++) {
                        bottomPane
                                .getChildren()
                                .remove(bottomPane
                                        .getChildren()
                                        .get(bottomPane.getChildren().size() - 1));
                    }
                }
            });
        });

        //        new Thread(workingTask).start();
        return true;
    }

    public boolean scanIframesAndElements(ObservableList<ARWebElement> listToAddNewElements, String searchDesc) {

        // Check if Browser is Inactive
        try {
            windowHandles = performActions.getCurrentDriver().getWindowHandles();
        } catch (Exception e) {
            browserNotAttached();
            return false;
        }

        executorService = Executors.newCachedThreadPool();
        AtomicInteger totalElementsSize = new AtomicInteger(0);

        CompletableFuture<Void> future = CompletableFuture.runAsync(
                () -> {
                    // Switch back to the main page before checking the next iframe
                    performActions.getCurrentDriver().switchTo().defaultContent();

                    //                    List<String> allXPath =
                    // iframeInputLocator.listAllXPaths(performActions.getCurrentDriver());
                    //                    // Filter the list to keep only elements with tag name "iframe"
                    //                    List<String> onlyIFrames = allXPath.stream()
                    //                            .filter(xpath -> xpath.toLowerCase().contains("iframe"))
                    //                            .collect(Collectors.toList());
                    //
                    //                    for (String xPATH : onlyIFrames) {
                    //                        System.out.println("iFrame: " + xPATH);
                    //                    }

                    List<WebElement> iframes = performActions.getCurrentDriver().findElements(By.tagName("iframe"));

                    //                    Map<WebElement, List<WebElement>> iframeElementsMap = new HashMap<>();

                    //                    for (String iFrameScan : onlyIFrames) {
                    boolean foundElements = false;
                    String iFrameXPathScan = "";
                    for (WebElement iframe : iframes) {
                        foundElements = true;

                        try {
                            try {
                                String iFrameTemp = iframeInputLocator.getElementXPathIFrame(
                                        iframe, performActions.getCurrentDriver());
                                if (!Strings.isNullOrEmpty(iFrameTemp)) {
                                    iFrameXPathScan = iFrameTemp;
                                }
                            } catch (Exception ignore) {

                            }
                            // CHANGING THE CONTEXT HERE
                            performActions.getCurrentDriver().switchTo().frame(iframe);

                            //                            List<String> allInsedeInfo  =
                            // iframeInputLocator.getIframeElementsInfo(iFrameElement,performActions.getCurrentDriver());

                            //                            try {
                            //                                String xPathList =
                            // iframeInputLocator.getElementXPathIFrame(
                            //                                        iFrameElement, performActions.getCurrentDriver());
                            //                                System.out.println("xPathList: " + xPathList);
                            //                            } catch (Exception x) {
                            //                                System.out.println("Error xPathList");
                            //                            }

                            List<WebElement> elementsInsideIframe =
                                    performActions.getCurrentDriver().findElements(By.xpath("//*"));

                            System.out.println(
                                    "Scanned " + elementsInsideIframe.size() + " elements inside an iframe.");

                            TargetElement targetIFrames = new TargetElement();

                            for (WebElement elementChild : elementsInsideIframe) {

                                targetIFrames.setTagType(WebElementTagNameEnum.ALL); // Default to ALL
                                targetIFrames.setIconType(WebElementIcon.TEXT);

                                String xPathElementChild;
                                // Main XPath
                                try {
                                    xPathElementChild = iframeInputLocator.getElementXPath(
                                            elementChild, performActions.getCurrentDriver());
                                } catch (Exception error) {
                                    System.out.println("Failed to ge iframe element XPATH: " + error.getMessage());
                                    //                                    performMessage.generalErrorIFrame("Error
                                    // Scanning iFrames");
                                    continue;
                                }

                                // Main Coordinates
                                try {
                                    Rectangle coord = elementChild.getRect();
                                    String coordTemp = (coord.getX() + (coord.getWidth() / 2)) + ","
                                            + (coord.getY() + (coord.getHeight() / 2));
                                    targetIFrames.setMainCoordinates(coordTemp);
                                } catch (Exception error) {
                                    System.out.println(
                                            "Failed to get iframe element Coordinates: " + error.getMessage());
                                    //                                    performMessage.generalErrorIFrame("Error
                                    // Scanning iFrames");
                                    continue;
                                }

                                //                                String tagName = null;
                                //                                try {
                                //                                    xPathInsideFrame =
                                // iframeInputLocator.getElementXPathIFrame(
                                //                                            elementChild,
                                // performActions.getCurrentDriver());
                                //                                    tagName =
                                // performActions.removeTrailingSlash(xPathInsideFrame);
                                //                                    tagName =
                                // performActions.extractTagName(xPathInsideFrame);
                                //                                } catch (Exception ignore) {
                                //                                }

                                try {
                                    String tagName = elementChild.getTagName().toLowerCase();

                                    if (tagName.equalsIgnoreCase("html")
                                            || tagName.equalsIgnoreCase("script")
                                            || tagName.equalsIgnoreCase("meta")
                                            || tagName.equalsIgnoreCase("head")
                                            || tagName.equalsIgnoreCase("body")) {
                                        continue;
                                    }
                                } catch (Exception ignore) {
                                    continue;
                                }

                                targetIFrames = defineTagTypeAdvanced(
                                        elementChild, iFrameXPathScan, xPathElementChild, targetIFrames);

                                try {
                                    if (targetIFrames.getDefinedName() != null) {
                                        ARWebElement arWebElement = new ARWebElement(targetIFrames, botJobLoad.getId());
                                        if (arWebElement != null && arWebElement.getElement() != null) {
                                            //
                                            // webElementObservableList1.add(arWebElement);
                                            //                                            Platform.runLater(() ->
                                            // scannedElements1.refresh());
                                            //                                            System.out.println("Found
                                            // element: " + targetIFrames.getDefinedName());
                                        }
                                    }

                                } catch (Exception error) {
                                    System.out.println("Element not found for XPath: " + "xpath ???");
                                    performMessage.generalErrorIFrame("xpath ???");
                                }
                            }
                        } catch (Exception error) {
                            performActions.getCurrentDriver().switchTo().defaultContent();
                            System.out.println("Failed to scan iframe: " + error.getMessage());
                            performMessage.generalErrorIFrame("Error Scanning iFrames");
                            return;
                        }
                        performActions.getCurrentDriver().switchTo().defaultContent();
                    }

                    if (!foundElements) {

                        Platform.runLater(() -> {
                            // Styled text elements
                            Text titleMessage = new Text("I did not find any Web Elements in this Page!");
                            titleMessage.setStyle("-fx-font-size: 18px; -fx-fill: blue;");

                            // Styled text elements
                            Text titleSearch = new Text("Search Type:");
                            titleSearch.setStyle("-fx-font-size: 18px; -fx-fill: blue;");

                            // Styled text elements
                            Text titleDescr = new Text(searchDesc);
                            titleDescr.setStyle("-fx-font-size: 18px; -fx-fill: blue;");

                            // Create a container for the message
                            VBox messageContainer = new VBox(5); // Adds spacing of 5px

                            // Add relevant elements to the container
                            messageContainer.getChildren().addAll(titleMessage, titleSearch, titleDescr);

                            performMessage.showAlertCombinedVBOX(
                                    Alert.AlertType.INFORMATION,
                                    "Scanning Elements",
                                    "No Web Elements Found!",
                                    null,
                                    messageContainer);
                        });
                    }
                },
                executorService);

        executorService.shutdown();

        future.handle((result, ex) -> {
            if (ex != null) {
                Platform.runLater(() -> System.out.println("Error scanning iframes: " + ex.getMessage()));
            } else {
                Platform.runLater(
                        () -> System.out.println("Scanning complete. Total elements: " + totalElementsSize.get()));
            }
            return result;
        });

        return true;
    }

    private void shutDownExecutorService() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.err.println("ExecutorService did not terminate");
                    ARLogger.getInstance(ARWebDriver.class).severe("ExecutorService did not terminate");
                }
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
            ARLogger.getInstance(ARWebDriver.class).severe("ExecutorService did not terminate\n" + e.getMessage());
        }
    }

    private void refreshInputBtn(String searchType) {
        //        webElementObservableList1.clear();
        //        manageUIScanInputs();
        String[] dataArray = {"input"};

        handleSearchTermClick(dataArray);
    }

    private void refreshOutputBtn(String searchType) {
        //        webElementObservableList2.clear();
        //        manageUIScanClickable();
        //        manageUIScanOutputs();

        String[] dataArray = {"allWithText"};

        handleSearchTermClick(dataArray);
    }

    //    private void refreshOtherElemBtn(String searchType) {
    //        webElementObservableList2.clear();
    //
    //        // Check if Browser is Inactive
    //        try {
    //            windowHandles = performActions.getCurrentDriver().getWindowHandles();
    //        } catch (Exception e) {
    //            browserNotAttached();
    //            return;
    //        }
    //        manageUIScanPriorities();
    //    }

    private void addBehaviourToAddedElements(ListChangeListener.Change<? extends ARWebElement> change) {
        while (change.next()) {
            change.getAddedSubList().forEach(this::addBehaviourToAbrWebElement);
        }
    }

    private void itPrintsElementDate(ARWebElement arWebHover) {

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

        StringBuilder sb = new StringBuilder();
        String nameDefined = "";
        if (arWebHover.getTargetElement() != null) {
            this.targetSelected = arWebHover.getTargetElement();
            this.targetSelected.setElement(arWebHover.getElement()); // RETURN THE ELEMENT FOR OTHER PURPOSE

            performActions.getCurrentDriver().switchTo().defaultContent();

            // iFrame
            if (!Strings.isNullOrEmpty(this.targetSelected.getIFrameXPath())) {
                try {
                    WebElement iFrame = performActions
                            .getCurrentDriver()
                            .findElement(By.xpath(this.targetSelected.getIFrameXPath()));
                    performActions.getCurrentDriver().switchTo().frame(iFrame);
                } catch (Exception error) {
                    ARLogger.getInstance(ARScannedElementPane.class)
                            .info("iFrame Element not Located\niFrameXPath"
                                    + arWebHover.getTargetElement().getIFrameXPath()
                                    + "iFrameChild: "
                                    + arWebHover.getTargetElement().getMainXPath());
                    //                            performMessage.errorMessage(
                    //                                    "iFrame Element not Located",
                    //                                    "Cannot able to find the iFrame",
                    //                                    "iFrame Parent or Child",
                    //                                    null,
                    //                                    null,
                    //                                    0);
                }
            }

            defineNameField.setText("");
            if (!Strings.isNullOrEmpty(this.targetSelected.getAttribId())
                    || !Strings.isNullOrEmpty(this.targetSelected.getAttribName())
                    || !Strings.isNullOrEmpty(this.targetSelected.getSomeText())) {
                nameDefined = (!Strings.isNullOrEmpty(this.targetSelected.getSomeText())
                        ? performActions.truncateAndNormalize(this.targetSelected.getSomeText(), 30)
                        : !Strings.isNullOrEmpty(this.targetSelected.getAttribId())
                                ? this.targetSelected.getAttribId()
                                : !Strings.isNullOrEmpty(this.targetSelected.getAttribName())
                                        ? this.targetSelected.getAttribName()
                                        : "");

                if (this.targetSelected.getDefinedName() != null
                        && !this.targetSelected.getDefinedName().equalsIgnoreCase(nameDefined)) {
                    nameDefined = this.targetSelected.getDefinedName();
                }

                String finalNameDefined = nameDefined;
                Platform.runLater(
                        () -> defineNameField.setText(performActions.truncateAndNormalize(finalNameDefined, 30)));

            } else if (this.targetSelected.getAttributeData().length > 0) {

                // Split by comma to get key-value pairs

                String idValue = null;
                String nameValue = null;
                String typeValue = null;

                // Loop through each key-value pair
                for (AttributeData attributeData : this.targetSelected.getAttributeData()) {

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
                    nameDefined = this.targetSelected.getOriginalTagName() + "-" + idValue;
                } else if (nameValue != null) {
                    nameDefined = this.targetSelected.getOriginalTagName() + "-" + nameValue;
                } else if (typeValue != null) {
                    nameDefined = this.targetSelected.getOriginalTagName() + "-" + typeValue;
                } else {
                    nameDefined = this.targetSelected.getOriginalTagName();
                }

                if (this.targetSelected.getDefinedName() != null
                        && !this.targetSelected.getDefinedName().equalsIgnoreCase(nameDefined)) {
                    nameDefined = this.targetSelected.getDefinedName();
                }

                String finalSomeText = nameDefined;
                Platform.runLater(
                        () -> defineNameField.setText(performActions.truncateAndNormalize(finalSomeText, 30)));

            } else if (!Strings.isNullOrEmpty(this.targetSelected.getOriginalTagName())) {

                if (this.targetSelected.getDefinedName() != null
                        && !this.targetSelected.getDefinedName().equalsIgnoreCase(nameDefined)) {
                    nameDefined = this.targetSelected.getDefinedName();
                } else {
                    nameDefined = this.targetSelected.getOriginalTagName();
                }
                String finalSomeText = nameDefined;

                Platform.runLater(() -> defineNameField.setText(finalSomeText));
            }
        }

        //                sb.append(this.targetElement.getOriginalTagName() + "-" +
        // this.targetElement.getSomeText())
        //                        .append("\n");

        sb.append("TagType: " + this.targetSelected.getTagType()).append("\n");
        sb.append("ID: " + this.targetSelected.getAttribId()).append("\n");
        sb.append("Name: " + this.targetSelected.getAttribName()).append("\n");
        sb.append("Text: " + this.targetSelected.getSomeText()).append("\n");

        if (!Strings.isNullOrEmpty(this.targetSelected.getCoords())) {
            sb.append("Coordinates: " + this.targetSelected.getCoords()).append("\n");
            coordsTextField.setText(this.targetSelected.getCoords());
        } else {
            sb.append("Coordinates: EMPTY").append("\n");
        }

        if (!Strings.isNullOrEmpty(this.targetSelected.getSearchAttributeValue())) {
            sb.append("Search Attrib: " + this.targetSelected.getSearchAttributeValue())
                    .append("\n");
            searchAttribValueField.setText(this.targetSelected.getSearchAttributeValue());
            searchAttribValueField.setStyle("-fx-font-size: 12px; -fx-text-fill: blue;");
        } else {
            sb.append("Search Attrib: No Defined").append("\n");
        }

        sb.append("Named: " + nameDefined).append("\n");
        sb.append("All Attributes Found: ").append("\n");
        for (AttributeData attribute : this.targetSelected.getAttributeData()) {
            sb.append("->  ")
                    .append(attribute.getName().trim() + "="
                            + attribute.getValue().trim())
                    .append("\n");
        }

        Platform.runLater(() -> {
            countdownTextField.setText(sb.toString());
            countdownTextField.setStyle("-fx-font-size: 12px; -fx-text-fill: blue;");
        });

        //                textFlowResult.getChildren().clear();
        //                textFlowResult.getChildren().addAll(countdownTextField);
        //                textFlowResult.requestLayout();
        //                contentPane.requestLayout();

        defineCheckBoxesClickabe(this.targetSelected);
        performActions.getCurrentDriver().switchTo().defaultContent();
    }

    private void itPrintsElementDTO(ElementDTO elementDTO) {

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

        StringBuilder sb = new StringBuilder();
        String nameDefined = "";

        if (this.targetSelected == null
                || elementDTO.getIFrameXPath() != this.targetSelected.getIFrameXPath()
                        && elementDTO.getXPath() != targetSelected.getMainXPath()) {

            this.targetSelected = extractPickClone(elementDTO);

            performActions.getCurrentDriver().switchTo().defaultContent();

            // iFrame
            if (!Strings.isNullOrEmpty(this.targetSelected.getIFrameXPath())) {
                try {
                    WebElement iFrame = performActions
                            .getCurrentDriver()
                            .findElement(By.xpath(this.targetSelected.getIFrameXPath()));
                    performActions.getCurrentDriver().switchTo().frame(iFrame);
                } catch (Exception error) {
                    ARLogger.getInstance(ARScannedElementPane.class)
                            .info("iFrame Element not Located\niFrameXPath"
                                    + this.targetSelected.getIFrameXPath()
                                    + "iFrameChild: "
                                    + this.targetSelected.getMainXPath());
                    //                            performMessage.errorMessage(
                    //                                    "iFrame Element not Located",
                    //                                    "Cannot able to find the iFrame",
                    //                                    "iFrame Parent or Child",
                    //                                    null,
                    //                                    null,
                    //                                    0);
                }
            }

            defineNameField.setText("");
            if (!Strings.isNullOrEmpty(this.targetSelected.getAttribId())
                    || !Strings.isNullOrEmpty(this.targetSelected.getAttribName())
                    || !Strings.isNullOrEmpty(this.targetSelected.getSomeText())) {
                nameDefined = (!Strings.isNullOrEmpty(this.targetSelected.getSomeText())
                        ? performActions.truncateAndNormalize(this.targetSelected.getSomeText(), 30)
                        : !Strings.isNullOrEmpty(this.targetSelected.getAttribId())
                                ? this.targetSelected.getAttribId()
                                : !Strings.isNullOrEmpty(this.targetSelected.getAttribName())
                                        ? this.targetSelected.getAttribName()
                                        : "");

                if (this.targetSelected.getDefinedName() != null
                        && !this.targetSelected.getDefinedName().equalsIgnoreCase(nameDefined)) {
                    nameDefined = this.targetSelected.getDefinedName();
                }

                String finalNameDefined = nameDefined;
                Platform.runLater(
                        () -> defineNameField.setText(performActions.truncateAndNormalize(finalNameDefined, 30)));

            } else if (this.targetSelected.getAttributeData().length > 0) {

                // Split by comma to get key-value pairs

                String idValue = null;
                String nameValue = null;
                String typeValue = null;

                // Loop through each key-value pair
                for (AttributeData attributeData : this.targetSelected.getAttributeData()) {

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
                    nameDefined = this.targetSelected.getOriginalTagName() + "-" + idValue;
                } else if (nameValue != null) {
                    nameDefined = this.targetSelected.getOriginalTagName() + "-" + nameValue;
                } else if (typeValue != null) {
                    nameDefined = this.targetSelected.getOriginalTagName() + "-" + typeValue;
                } else {
                    nameDefined = this.targetSelected.getOriginalTagName();
                }

                if (this.targetSelected.getDefinedName() != null
                        && !this.targetSelected.getDefinedName().equalsIgnoreCase(nameDefined)) {
                    nameDefined = this.targetSelected.getDefinedName();
                }

                String finalSomeText = nameDefined;
                Platform.runLater(
                        () -> defineNameField.setText(performActions.truncateAndNormalize(finalSomeText, 30)));

            } else if (!Strings.isNullOrEmpty(this.targetSelected.getOriginalTagName())) {

                if (this.targetSelected.getDefinedName() != null
                        && !this.targetSelected.getDefinedName().equalsIgnoreCase(nameDefined)) {
                    nameDefined = this.targetSelected.getDefinedName();
                } else {
                    nameDefined = this.targetSelected.getOriginalTagName();
                }
                String finalSomeText = nameDefined;

                Platform.runLater(() -> defineNameField.setText(finalSomeText));
            }
        }

        //                sb.append(this.targetElement.getOriginalTagName() + "-" +
        // this.targetElement.getSomeText())
        //                        .append("\n");

        sb.append("TagType: " + this.targetSelected.getTagType()).append("\n");
        sb.append("ID: " + this.targetSelected.getAttribId()).append("\n");
        sb.append("Name: " + this.targetSelected.getAttribName()).append("\n");
        sb.append("Text: " + this.targetSelected.getSomeText()).append("\n");

        if (!Strings.isNullOrEmpty(this.targetSelected.getCoords())) {
            sb.append("Coordinates: " + this.targetSelected.getCoords()).append("\n");
            coordsTextField.setText(this.targetSelected.getCoords());
        } else {
            sb.append("Coordinates: EMPTY").append("\n");
        }

        if (!Strings.isNullOrEmpty(this.targetSelected.getSearchAttributeValue())) {
            sb.append("Search Attrib: " + this.targetSelected.getSearchAttributeValue())
                    .append("\n");
            searchAttribValueField.setText(this.targetSelected.getSearchAttributeValue());
            searchAttribValueField.setStyle("-fx-font-size: 12px; -fx-text-fill: blue;");
        } else {
            sb.append("Search Attrib: No Defined").append("\n");
        }

        sb.append("Named: " + nameDefined).append("\n");
        sb.append("All Attributes Found: ").append("\n");
        for (AttributeData attribute : this.targetSelected.getAttributeData()) {
            sb.append("->  ")
                    .append(attribute.getName().trim() + "="
                            + attribute.getValue().trim())
                    .append("\n");
        }

        Platform.runLater(() -> {
            countdownTextField.setText(sb.toString());
            countdownTextField.setStyle("-fx-font-size: 12px; -fx-text-fill: blue;");
        });

        //                textFlowResult.getChildren().clear();
        //                textFlowResult.getChildren().addAll(countdownTextField);
        //                textFlowResult.requestLayout();
        //                contentPane.requestLayout();

        defineCheckBoxesClickabe(this.targetSelected);
        performActions.getCurrentDriver().switchTo().defaultContent();
    }

    private void addBehaviourToAbrWebElement(ARWebElement arWebHover) {
        EventHandler<MouseEvent> mouseEnteredHandler = mouseEvent -> {
            Task<Void> handleEvent = new Task<>() {
                @Override
                protected Void call() {

                    if (!Strings.isNullOrEmpty(arWebHover.getTargetElement().getIFrameXPath())) {
                        try {
                            WebElement iFrame = performActions
                                    .getCurrentDriver()
                                    .findElement(By.xpath(
                                            arWebHover.getTargetElement().getIFrameXPath()));
                            performActions.getCurrentDriver().switchTo().frame(iFrame);
                        } catch (Exception e) {
                            //                            ARLogger.getInstance(ARScannedElementPane.class)
                            //                                    .info("iFrame Element not Located\niFrameXPath"
                            //                                            +
                            // arWebHover.getTargetElement().getIFrameXPath()
                            //                                            +"iFrameChild: " +
                            // arWebHover.getTargetElement().getMainXPath());
                            //                            performMessage.errorMessage(
                            //                                    "iFrame Element not Located",
                            //                                    "Cannot able to find the iFrame",
                            //                                    "iFrame Parent or Child",
                            //                                    null,
                            //                                    null,
                            //                                    0);
                        }
                    } else {
                        performActions.getCurrentDriver().switchTo().defaultContent();
                    }

                    WebElement currentElement = arWebHover.getElement();

                    if (currentElement != null) {
                        performActions.highlightElement(jsExecutor, previousElement, currentElement);
                        previousElement = currentElement; // Store the new previous element
                    }

                    return null;
                }
            };
            new Thread(handleEvent).start();
        };

        EventHandler<MouseEvent> mouseExitedHandler = mouseEvent -> {
            Task<Void> handleEvent = new Task<>() {
                @Override
                protected Void call() {
                    if (previousElement != null) {
                        performActions.highlightElement(jsExecutor, previousElement, null);
                        previousElement = null; // Reset previous
                    }
                    return null;
                }
            };
            new Thread(handleEvent).start();
        };

        //        scannedElements2.getSelectionModel().selectedIndexProperty().addListener((obs, oldIndex, newIndex) ->
        // {
        //            if (newIndex.intValue() == -1) {
        //                return; // No selection, do nothing
        //            }
        //
        //            if (newIndex.intValue() > oldIndex.intValue()) {
        //                itPrintsElementDate(arWebHover);
        ////                System.out.println("Moved Down to: " +
        // scannedElements2.getSelectionModel().getSelectedItem());
        //            } else if (newIndex.intValue() < oldIndex.intValue()) {
        //                itPrintsElementDate(arWebHover);
        ////                System.out.println("Moved Up to: " +
        // scannedElements2.getSelectionModel().getSelectedItem());
        //            }
        //        });

        EventHandler<MouseEvent> mouseClickedHandler = mouseEvent -> {
            if (mouseEvent.getClickCount() == 2) {
                // Double clicked the element
                if (arWebHover.getSavedReferences().size() == 0) {

                    Text variableText1Styled = new Text(String.format(
                            "The Instruction \"%s\" don't have any locators!",
                            arWebHover.getElement().getText()));

                    variableText1Styled.setStyle("-fx-font-size: 18px; -fx-fill: red;");

                    VBox combinedTextContainer = new VBox();
                    combinedTextContainer.setSpacing(5); // Add some sp

                    combinedTextContainer.getChildren().add(variableText1Styled);

                    performMessage.showAlertCombinedVBOX(
                            Alert.AlertType.ERROR,
                            "ERROR ADD WEB ELEMENT",
                            "Instructions CANNOT BE ADDED WITHOUT LOCATORS!",
                            null,
                            combinedTextContainer);

                    return;
                }

                //                String reTakeXPath = getXPath(performActions.getCurrentDriver(),
                // arWebElement.getElement());
                //                arWebElement.getTargetElement().setMainXPath(reTakeXPath);

                // IF SOME REFRESH CHANGED THE ELEMENT IT TRIGGERS THIS EXCEPTION
                String elemTagName = "No TagName";
                if (!checkTestAction.isSelected()) {
                    try {

                        if (arWebHover.getTargetElement().getMainXPath() == null) {
                            arWebHover
                                    .getTargetElement()
                                    .setMainXPath(
                                            arWebHover.getSavedReferences().get("currentXPath"));
                        }
                        if (arWebHover.getTargetElement().getMainCoordinates() == null) {
                            arWebHover
                                    .getTargetElement()
                                    .setMainCoordinates(
                                            arWebHover.getSavedReferences().get("coordinates"));
                        }

                        if (!Strings.isNullOrEmpty(arWebHover.getTargetElement().getIFrameXPath())) {
                            try {
                                WebElement iFrame = performActions
                                        .getCurrentDriver()
                                        .findElement(By.xpath(
                                                arWebHover.getTargetElement().getIFrameXPath()));
                                performActions.getCurrentDriver().switchTo().frame(iFrame);
                            } catch (Exception e) {
                                ARLogger.getInstance(ARScannedElementPane.class)
                                        .info("iFrame Element not Located\niFrameXPath"
                                                + arWebHover.getTargetElement().getIFrameXPath()
                                                + "iFrameChild: "
                                                + arWebHover.getTargetElement().getMainXPath());
                                performMessage.errorMessage(
                                        "iFrame Element not Located",
                                        "Cannot able to find the iFrame",
                                        "iFrame Parent or Child",
                                        null,
                                        null,
                                        0);
                                return;
                            }
                        }

                        // Last check xPat before Adding to the BotJob
                        WebElement elementFinder = null;
                        try {
                            elementFinder = performActions
                                    .getCurrentDriver()
                                    .findElement(By.xpath(
                                            arWebHover.getTargetElement().getMainXPath()));
                            arWebHover.setElement(elementFinder);
                        } catch (Exception e) {
                            System.out.println(e.getMessage());
                        }

                        if (elementFinder == null) {
                            // Try by coordinates
                            Pair<String, String> filedData = new Pair("martini", "Martini");
                            try {
                                performActions.executeActionsAtCoordinates(
                                        arWebHover.getSavedReferences().get("coordinates"),
                                        filedData,
                                        ARConstants.CLICK,
                                        false);

                                // It Means Did Not Failed to Coordinates
                                // I am Setting here to avoid the Not Found Message
                                elementFinder = arWebHover.getElement();
                            } catch (Exception e) {
                                System.out.println(e.getMessage());
                            }
                        }

                        //                    if (elementFinder != null) {
                        //                        arWebElement.setElement(elementFinder);
                        //                    }

                        if (elementFinder != null
                                && arWebHover.getElement() != null
                                && arWebHover.getElement().getTagName() != null) {
                            elemTagName = arWebHover.getElement().getTagName();
                        }
                    } catch (Exception ex) {
                        //                        performMessage.multipleActionsElement("Multiple Actions");
                    }
                }

                if (checkTestAction.isSelected()) {
                    try {
                        if (arWebHover.getElement() != null) {

                            if (arWebHover.getTargetElement().getIFrameXPath() != null) {

                                performActions.getCurrentDriver().switchTo().defaultContent();

                                try {
                                    WebElement iFrame = performActions
                                            .getCurrentDriver()
                                            .findElement(By.xpath(arWebHover
                                                    .getTargetElement()
                                                    .getIFrameXPath()));
                                    if (iFrame != null) {

                                        performActions
                                                .getCurrentDriver()
                                                .switchTo()
                                                .frame(iFrame);

                                        WebElement elementClicked = performActions
                                                .getCurrentDriver()
                                                .findElement(By.xpath(arWebHover
                                                        .getTargetElement()
                                                        .getMainXPath()));
                                    }
                                } catch (Exception error) {
                                    performMessage.errorMessage(
                                            "iFrame Element error",
                                            "Not possible to locate the element",
                                            null,
                                            null,
                                            null,
                                            0);
                                    return;
                                }
                            }

                            //                            arWebDriver.dehighlightElement(arWebHover.getElement());

                            //                            WebElement elementXPath =
                            //
                            // performActions.getCurrentDriver().findElement(By.xpath(arWebElement.getTargetElement().getMainXPath()));
                            //                            if (elementXPath != null) {
                            //                                elementXPath.click();
                            //                            }

                            Pair<String, String> fieldData = new Pair<>("Test", testActionsField.getText());

                            String mainCoordenates =
                                    arWebHover.getTargetElement().getMainCoordinates();
                            String savedCoordenates =
                                    arWebHover.getSavedReferences().get("coordinates");
                            if (Strings.isNullOrEmpty(mainCoordenates)) {
                                mainCoordenates = arWebHover.getTargetElement().getMainCoordinates();
                            }

                            if (Strings.isNullOrEmpty(savedCoordenates)) {
                                savedCoordenates = mainCoordenates;
                            }

                            String mainCoordinates =
                                    arWebHover.getTargetElement().getMainCoordinates();
                            String savedCoordinates =
                                    arWebHover.getSavedReferences().get("coordinates");

                            if (Strings.isNullOrEmpty(mainCoordinates)) {
                                mainCoordinates = arWebHover.getTargetElement().getMainCoordinates();
                            }

                            if (Strings.isNullOrEmpty(savedCoordinates)) {
                                savedCoordinates = mainCoordinates;
                            }

                            List<String> coordinatesList = new ArrayList<>();
                            if (!Strings.isNullOrEmpty(mainCoordinates)) {
                                coordinatesList.add(mainCoordinates);
                            }
                            if (!Strings.isNullOrEmpty(savedCoordinates) && !savedCoordinates.equals(mainCoordinates)) {
                                coordinatesList.add(savedCoordinates);
                            }

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

                            String result = performActions.sequenceOfCommands(
                                    arWebHover.getElement(),
                                    ARConstants.SELECT,
                                    coordinates,
                                    fieldData,
                                    performActions.getCurrentDriver(),
                                    false);
                            System.out.println(result);
                            actionsTested.append(result + System.lineSeparator());
                            actionText2 = new Text(result);
                            if (result.contains("Failed")) {
                                actionText2.setStyle("-fx-font-size: 12px; -fx-fill: red;");
                            } else {
                                actionText2.setStyle("-fx-font-size: 12px; -fx-fill: green;");
                            }

                            result = performActions.sequenceOfCommands(
                                    arWebHover.getElement(),
                                    ARConstants.CLICK,
                                    coordinates,
                                    fieldData,
                                    performActions.getCurrentDriver(),
                                    false);
                            System.out.println(result);
                            actionsTested.append(result + System.lineSeparator());
                            actionText3 = new Text(result);
                            if (result.contains("Failed")) {
                                actionText3.setStyle("-fx-font-size: 12px; -fx-fill: red;");
                            } else {
                                actionText3.setStyle("-fx-font-size: 12px; -fx-fill: green;");
                            }

                            result = performActions.sequenceOfCommands(
                                    arWebHover.getElement(),
                                    ARConstants.GET_VALUE,
                                    coordinates,
                                    fieldData,
                                    performActions.getCurrentDriver(),
                                    false);
                            System.out.println(result);
                            actionsTested.append(result + System.lineSeparator());
                            actionText4 = new Text(result);
                            if (result.contains("Failed")) {
                                actionText4.setStyle("-fx-font-size: 12px; -fx-fill: red;");
                            } else {
                                actionText4.setStyle("-fx-font-size: 12px; -fx-fill: green;");
                            }

                            result = performActions.sequenceOfCommands(
                                    arWebHover.getElement(),
                                    ARConstants.CLEAR,
                                    coordinates,
                                    fieldData,
                                    performActions.getCurrentDriver(),
                                    false);
                            System.out.println(result);
                            actionsTested.append(result + System.lineSeparator());
                            actionText5 = new Text(result);
                            if (result.contains("Failed")) {
                                actionText5.setStyle("-fx-font-size: 12px; -fx-fill: red;");
                            } else {
                                actionText5.setStyle("-fx-font-size: 12px; -fx-fill: green;");
                            }

                            result = performActions.sequenceOfCommands(
                                    arWebHover.getElement(),
                                    ARConstants.INSERT,
                                    coordinates,
                                    fieldData,
                                    performActions.getCurrentDriver(),
                                    false);
                            System.out.println(result);
                            actionsTested.append(result + System.lineSeparator());
                            actionText6 = new Text(result);
                            if (result.contains("Failed")) {
                                actionText6.setStyle("-fx-font-size: 12px; -fx-fill: red;");
                            } else {
                                actionText6.setStyle("-fx-font-size: 12px; -fx-fill: green;");
                            }

                            result = performActions.sequenceOfCommands(
                                    arWebHover.getElement(),
                                    ARConstants.FOCUS,
                                    coordinates,
                                    fieldData,
                                    performActions.getCurrentDriver(),
                                    false);
                            System.out.println(result);

                            actionsTested.append(result + System.lineSeparator());

                            actionText7 = new Text(result);
                            if (result.contains("Failed")) {
                                actionText7.setStyle("-fx-font-size: 12px; -fx-fill: red;");
                            } else {
                                actionText7.setStyle("-fx-font-size: 12px; -fx-fill: green;");
                            }

                            result = performActions.sequenceOfCommands(
                                    arWebHover.getElement(),
                                    ARConstants.TAB,
                                    coordinates,
                                    fieldData,
                                    performActions.getCurrentDriver(),
                                    false);
                            System.out.println(result);

                            actionsTested.append(result + System.lineSeparator());

                            actionText8 = new Text(result);
                            if (result.contains("Failed")) {
                                actionText8.setStyle("-fx-font-size: 12px; -fx-fill: red;");
                            } else {
                                actionText8.setStyle("-fx-font-size: 12px; -fx-fill: green;");
                            }

                            result = performActions.sequenceOfCommands(
                                    arWebHover.getElement(),
                                    ARConstants.COORD_VISUALIZA,
                                    coordinates,
                                    fieldData,
                                    performActions.getCurrentDriver(),
                                    false);
                            System.out.println(result);
                            actionsTested.append(result + System.lineSeparator());
                            actionText9 = new Text(result);
                            if (result.contains("Failed")) {
                                actionText9.setStyle("-fx-font-size: 12px; -fx-fill: red;");
                            } else {
                                actionText9.setStyle("-fx-font-size: 12px; -fx-fill: green;");
                            }

                            result = performActions.sequenceOfCommands(
                                    arWebHover.getElement(),
                                    ARConstants.COORD_CLICK,
                                    coordinates,
                                    fieldData,
                                    performActions.getCurrentDriver(),
                                    false);
                            System.out.println(result);

                            actionsTested.append(result + System.lineSeparator());

                            actionText10 = new Text(result);
                            if (result.contains("Failed")) {
                                actionText10.setStyle("-fx-font-size: 12px; -fx-fill: red;");
                            } else {
                                actionText10.setStyle("-fx-font-size: 12px; -fx-fill: green;");
                            }

                            result = performActions.sequenceOfCommands(
                                    arWebHover.getElement(),
                                    ARConstants.COORD_INSERT,
                                    coordinates,
                                    fieldData,
                                    performActions.getCurrentDriver(),
                                    false);
                            System.out.println(result);
                            actionsTested.append(result + System.lineSeparator());
                            actionText11 = new Text(result);
                            if (result.contains("Failed")) {
                                actionText11.setStyle("-fx-font-size: 12px; -fx-fill: red;");
                            } else {
                                actionText11.setStyle("-fx-font-size: 12px; -fx-fill: green;");
                            }

                            result = performActions.sequenceOfCommands(
                                    arWebHover.getElement(),
                                    ARConstants.COORD_INSERT,
                                    coordinates,
                                    fieldData,
                                    performActions.getCurrentDriver(),
                                    true);
                            System.out.println(result);
                            actionsTested.append(result + System.lineSeparator());
                            actionText12 = new Text(result);
                            if (result.contains("Failed")) {
                                actionText12.setStyle("-fx-font-size: 12px; -fx-fill: red;");
                            } else {
                                actionText12.setStyle("-fx-font-size: 12px; -fx-fill: green;");
                            }

                            result = performActions.sequenceOfCommands(
                                    arWebHover.getElement(),
                                    ARConstants.COORD_MOVE_CLICK_RED,
                                    coordinates,
                                    fieldData,
                                    performActions.getCurrentDriver(),
                                    true);
                            System.out.println(result);
                            actionsTested.append(result + System.lineSeparator());
                            actionText13 = new Text(result);
                            if (result.contains("Failed")) {
                                actionText13.setStyle("-fx-font-size: 12px; -fx-fill: red;");
                            } else {
                                actionText13.setStyle("-fx-font-size: 12px; -fx-fill: green;");
                            }

                            System.out.println(actionsTested.toString());

                            VBox vertical = new VBox();
                            vertical.getChildren()
                                    .addAll(
                                            actionText1,
                                            actionText2,
                                            actionText3,
                                            actionText4,
                                            actionText5,
                                            actionText6,
                                            actionText7,
                                            actionText8,
                                            actionText9,
                                            actionText10,
                                            actionText11,
                                            actionText12,
                                            actionText13);

                            Platform.runLater(() -> {
                                textFlowResult.getChildren().clear();
                                textFlowResult.getChildren().addAll(vertical);

                                textFlowResult.requestLayout();

                                //                                boxListViews.requestLayout();
                                //                                verticalBox.requestLayout();
                                //                                getChildren().addAll(blockAndUrl, boxListViews);
                                contentPane.requestLayout();
                                VBox vBoxResult = new VBox();
                                vBoxResult.getChildren().addAll(textFlowResult);
                                performMessage.showAlertCombinedVBOX(
                                        Alert.AlertType.INFORMATION,
                                        "Test Actions Results",
                                        "Web Actions Tested:",
                                        null,
                                        vBoxResult);

                                //                                countdownTextField.setText(actionsTested.toString());
                                //                                countdownTextField.setStyle("-fx-font-size: 12px;
                                // -fx-text-fill: blue;");
                            });
                        }
                        //                                arWebElement.getElement().click();
                    } catch (Exception e) {
                        performMessage.couldNotFindElement("No TagName");
                        return;
                    }
                } else {
                    ARLogger.getInstance(ARScannedElementPane.class)
                            .info("Double clicked the element: "
                                    + arWebHover.getTargetElement().getMainXPath());

                    String blockName = "Default Block";
                    try {
                        currentBlockId = comboBoxBlocks.getValue().getExtraId();
                        blockName = comboBoxBlocks.getValue().getText();

                    } catch (Exception erro) {
                        currentBlockId = -1;
                    }

                    if (currentBlockId < 0) {

                        Text variableText1Styled = new Text("Select the block you wan to Add New Command!");
                        variableText1Styled.setStyle("-fx-font-size: 16px; -fx-fill: red;");

                        VBox combinedTextContainer = new VBox();
                        combinedTextContainer.setSpacing(5); // Add some sp

                        combinedTextContainer.getChildren().add(variableText1Styled);

                        performMessage.showAlertCombinedVBOX(
                                Alert.AlertType.ERROR,
                                "Block Not Selected",
                                "Select the Block!",
                                null,
                                combinedTextContainer);
                        return;
                    }

                    Text blockNameLabel = new Text("Block : ");
                    blockNameLabel.setStyle("-fx-font-size: 18px; -fx-fill: blue;");

                    Text blockNameText = new Text(blockName);
                    blockNameText.setStyle("-fx-font-size: 18px; -fx-fill: green;");

                    Text variableText1Styled = new Text("Web Element Instruction");
                    variableText1Styled.setStyle("-fx-font-size: 18px; -fx-fill: blue;");

                    String nameDefined = defineNameField.getText().trim();
                    if (this.targetSelected.getDefinedName() != null
                            && !this.targetSelected.getDefinedName().equalsIgnoreCase(nameDefined)) {
                        nameDefined = this.targetSelected.getDefinedName();
                    }

                    Text variableText2Styled = new Text(nameDefined);
                    variableText2Styled.setStyle("-fx-font-size: 18px; -fx-fill: green;");

                    VBox combinedTextContainer = new VBox();
                    combinedTextContainer.setSpacing(5); // Add some sp

                    combinedTextContainer
                            .getChildren()
                            .addAll(blockNameLabel, blockNameText, variableText1Styled, variableText2Styled);

                    boolean result = performMessage.showAlertCombinedVBOX(
                            Alert.AlertType.CONFIRMATION,
                            "Add Instruction to Bot-Job",
                            "Add the Instruction Selected to the Bot-Job?",
                            null,
                            combinedTextContainer);

                    if (result) {

                        BotJobLoadDTO botJobCheck = performDataBase.loadBotJobById(this.botJobLoad.getId());

                        if (botJobCheck == null) {

                            variableText1Styled = new Text(String.format(
                                    "Check if you already have a Bot Job \"%\" Created!", this.botJobLoad.getName()));
                            variableText1Styled.setStyle("-fx-font-size: 18px; -fx-fill: red;");

                            combinedTextContainer.getChildren().clear();
                            combinedTextContainer.getChildren().add(variableText1Styled);

                            performMessage.showAlertCombinedVBOX(
                                    Alert.AlertType.ERROR,
                                    "Bot Job DOES NOT EXIST",
                                    "Verify the Bot Job Name if have any: ",
                                    null,
                                    combinedTextContainer);

                            ARLogger.getInstance(ARScannedElementPane.class)
                                    .severe(String.format(
                                            "Check if you already have a Bot Job \"%\" Created!",
                                            this.botJobLoad.getName()));
                            return;
                        }

                        // It Prevents Start without blocks
                        this.blockLoadList = performDataBase.loadBlocksByBotJobId(this.botJobLoad.getId());
                        if (blockLoadList.isEmpty()) {

                            BlockDetailsDTO newBlockDetails = new BlockDetailsDTO();
                            newBlockDetails.setBlockName("Default Block");
                            newBlockDetails.setBlockDescription("  description");
                            newBlockDetails.setTypeId(1);
                            newBlockDetails.setActive(true);
                            newBlockDetails.setWait(3);

                            newBlockDetails.setBotJobId(botJobLoad.getId());

                            currentBlockId = performDataBase.createNewBlock(newBlockDetails);

                            if (currentBlockId < 0) {
                                performActions.showAlert(
                                        Alert.AlertType.ERROR,
                                        "Error Creating new Block",
                                        "Verify the Bot Job Name if have any",
                                        "Check if you already have a Bot Job Created!");

                                ARLogger.getInstance(Thread.class)
                                        .severe(String.format(
                                                "Error Creating a new Block for bot job Id %d\nCheck if you already have a Bot Job Created!",
                                                botJobLoad.getId()));
                                return;
                            } else {

                                //                                setBlockJob(
                                //
                                // ARSharedResources.getInstance().getEntityById(BlockDTO.class, currentBlockId));
                                ARLogger.getInstance(Thread.class)
                                        .info(String.format(
                                                "Created a new Block id %d for bot job Id %d",
                                                currentBlockId, botJobLoad.getId()));
                            }

                            Platform.runLater(() -> {
                                refreshBlocks(true);
                            });
                        }

                        String finalNameWebElement = nameDefined;
                        String finalBlockName = blockName;
                        Task<Void> handleEvent = new Task<>() {
                            @Override
                            protected Void call() throws Exception {
                                ARLogger.getInstance(Task.class).info("THREAD: Started");

                                ARLogger.getInstance(Task.class)
                                        .fine("THREAD: fetching instruction list from database");

                                //                                ObservableList<InstructionLoadDTO> list =
                                // ARSharedResources.getInstance()
                                //                                        .getEntityList(InstructionLoadDTO.class,
                                // (instr) -> instr.getBlockId()
                                //                                                .equals(currentBlockId));

                                List<InstructionLoadDTO> listInstr =
                                        performDataBase.getInstructionsByBlockId(botJobLoad.getId(), currentBlockId);

                                ARLogger.getInstance(Task.class)
                                        .finer("THREAD: instruction list size " + listInstr.size());

                                String actionReq = checkClickElement.isSelected()
                                        ? ARConstants.CLICK
                                        : checkInputText.isSelected()
                                                ? ARConstants.INSERT
                                                : checkOutputText.isSelected() ? ARConstants.OUTPUT : ARConstants.OTHER;

                                WebElementTagNameEnum tagType =
                                        arWebHover.getTargetElement().getTagType();

                                if (checkForceEnterText.isSelected() && tagType.equals(WebElementTagNameEnum.INPUT)) {
                                    tagType = WebElementTagNameEnum.INPUT_ENTER;
                                }

                                InstructionLoadDTO instruction =
                                        arWebHover.buildNewInstruction(tagType, actionReq, false, listInstr.size());

                                if (checkForceCoordText.isSelected()) {
                                    instruction.setForceCoordinates(true);
                                } else {
                                    instruction.setForceCoordinates(false);
                                }

                                instruction.setCoordinates(
                                        arWebHover.getTargetElement().getMainCoordinates());
                                instruction.setIFrameXPath(
                                        arWebHover.getTargetElement().getIFrameXPath());

                                instruction.setBlockId(currentBlockId);

                                instruction.setInstructionOrderNumber(listInstr.size() + 1);

                                ARLogger.getInstance(Task.class).fine("THREAD: adding instruction to database");

                                Integer currentBotJobId = botJobLoad.getId();

                                // Change the Name on the fly
                                if (!Strings.isNullOrEmpty(finalNameWebElement)) {
                                    instruction.setName(finalNameWebElement);

                                    // Update the action string if it contains "I:"
                                    String actions = instruction.getActions();
                                    String[] parts = actions.split(",");

                                    if (actions.startsWith("I:")) {
                                        for (int i = 0; i < parts.length; i++) {
                                            parts[i] = parts[i].trim(); // Ensure no leading/trailing spaces
                                            if (parts[i].startsWith("I:")) {
                                                if (parts[i].contains(":E:")) {
                                                    parts[i] = "I:E:" + finalNameWebElement;
                                                } else {
                                                    parts[i] = "I:" + finalNameWebElement;
                                                }
                                                break; // Stop after modifying the first match
                                            }
                                        }

                                        instruction.setActions(parts[0]);
                                    }
                                }

                                int newId = preFillAddInstruction(
                                        instruction.getName().trim(),
                                        instruction.getDescription().trim(),
                                        instruction.getActions(),
                                        instruction.getOperation(),
                                        instruction.getOnHoldSeconds(),
                                        instruction.getVariableId(),
                                        instruction.getInstructionOrderNumber(),
                                        instruction.getExportToABR(),
                                        instruction.getXpath(),
                                        instruction.getCoordinates(),
                                        instruction.getForceCoordinates(),
                                        instruction.getIFrameXPath(),
                                        currentBotJobId,
                                        currentBlockId);

                                if (newId < 0) {

                                    Text variableText1Styled = new Text(String.format(
                                            "\"Component\" Instruction \"%s\"\nCannot be saved",
                                            instruction.getName()));
                                    variableText1Styled.setStyle("-fx-font-size: 18px; -fx-fill: red;");

                                    VBox combinedTextContainer = new VBox();
                                    combinedTextContainer.setSpacing(5); // Add some sp

                                    combinedTextContainer.getChildren().add(variableText1Styled);

                                    performMessage.showAlertCombinedVBOX(
                                            Alert.AlertType.ERROR,
                                            "Error Add New \"Component\" Instruction",
                                            "Not possible to insert new Operation",
                                            null,
                                            combinedTextContainer);

                                    return null;
                                }

                                instruction.setId(newId);

                                arWebHover.setInstructionId(instruction.getId());
                                List<InstructionReferenceLoadDTO> queue = new ArrayList<>();
                                for (String key :
                                        arWebHover.getSavedReferences().keySet()) {
                                    InstructionReferenceLoadDTO reference = new InstructionReferenceLoadDTO();
                                    reference.setReferenceType(key);
                                    reference.setValue(
                                            arWebHover.getSavedReferences().get(key));

                                    reference.setBotJobId(currentBotJobId);

                                    //
                                    // reference.setBlockLoopInstructionLoadDTO(instruction);
                                    queue.add(reference);
                                }
                                try {

                                    Platform.runLater(() -> {
                                        boolean saved = performDataBase.insertReferences(queue, instruction.getId());
                                        if (saved) {

                                            //                                            Text blockNameLabel = new
                                            // Text("Block : ");
                                            //
                                            // blockNameLabel.setStyle("-fx-font-size: 18px; -fx-fill: blue;");
                                            //
                                            //                                            Text blockNameText = new
                                            // Text(finalBlockName);
                                            //
                                            // blockNameText.setStyle("-fx-font-size: 18px; -fx-fill: green;");
                                            //
                                            //                                            Text variableText1Styled =
                                            //                                                    new Text("The Web
                                            // Instruction \"" + instruction.getName() + "\"");
                                            //
                                            // variableText1Styled.setStyle("-fx-font-size: 18px; -fx-fill: blue;");
                                            //
                                            //                                            Text variableText2Styled =
                                            //                                                    new Text("With " +
                                            // queue.size() + " reference locators");
                                            //
                                            // variableText2Styled.setStyle("-fx-font-size: 18px; -fx-fill: blue;");
                                            //
                                            //                                            Text variableText3Styled = new
                                            // Text("Has been added successfully!");
                                            //
                                            // variableText3Styled.setStyle("-fx-font-size: 18px; -fx-fill: blue;");
                                            //
                                            //                                            VBox combinedTextContainer =
                                            // new VBox();
                                            //
                                            // combinedTextContainer.setSpacing(5); // Add some sp

                                            //                                            combinedTextContainer
                                            //                                                    .getChildren()
                                            //                                                    .addAll(
                                            //
                                            // blockNameLabel,
                                            //                                                            blockNameText,
                                            //
                                            // variableText1Styled,
                                            //
                                            // variableText2Styled,
                                            //
                                            // variableText3Styled);

                                            botJobLoadList = performDataBase.loadCompleteJobs(currentBotJobId);
                                            String jsonData = "[]";
                                            if (botJobLoadList.size() > 0) {
                                                List<InstructionLoadDTO> blockLoopInstructions =
                                                        performDataBase.buildJsonViewData(botJobLoadList);
                                                jsonData = gson.toJson(blockLoopInstructions);
                                            }
                                            sendMessageJson(
                                                    homeBanking.getId(),
                                                    "botJobTasks-" + currentBotJobId,
                                                    jsonData,
                                                    "updateInstructions");

                                            //
                                            // performMessage.showAlertCombinedVBOX(
                                            //
                                            // Alert.AlertType.INFORMATION,
                                            //                                                    "Web Instruction Add",
                                            //                                                    "Added New \"Web
                                            // Instruction\" Instruction",
                                            //                                                    null,
                                            //
                                            // combinedTextContainer);

                                        } else {

                                            performMessage.errorMessage(
                                                    "Web Instruction Warning",
                                                    "Potential Issue with Web Instruction",
                                                    "The instruction \"" + instruction.getName() + "\" was added with "
                                                            + queue.size() + " reference locators.",
                                                    "However, the engine may not process this element correctly",
                                                    "due to insufficient identifiable attributes.",
                                                    0);
                                        }
                                    });
                                } catch (Exception ex) {
                                    ARLogger.getInstance(Task.class).severe("Error Adding Instruction elements");
                                }
                                //                                        });
                                return null;
                            }
                        };
                        ARLogger.getInstance(ARScannedElementPane.class).fine("Thread created");
                        ARLogger.getInstance(ARScannedElementPane.class).fine("Before thread execution");
                        new Thread(handleEvent).start();
                        ARLogger.getInstance(ARScannedElementPane.class).fine("After thread execution");
                    }
                }
            } else if (mouseEvent.getClickCount() == 1) {
                itPrintsElementDate(arWebHover);
            }
        };
        arWebHover.addEventHandler(MouseEvent.MOUSE_ENTERED, mouseEnteredHandler);
        arWebHover.addEventHandler(MouseEvent.MOUSE_EXITED, mouseExitedHandler);
        arWebHover.addEventHandler(MouseEvent.MOUSE_CLICKED, mouseClickedHandler);
    }

    private Set<WebElement> managePrioritiesCriteria() {

        // Gets Always the Latest info form DB
        databaseUserDto = loadUserData(homeBanking.getId());
        arPriorities.loadSearchElementsConfig(databaseUserDto.getSearchConfig());

        Set<WebElement> elementsResponse = new HashSet<>();
        if (arPriorities.getSearchConfigList() == null) {
            System.out.println("Is going to Search using \"searchConfigTemplate\"!  Please Add to the DB");
            return null;
        }
        if (arPriorities.getSearchConfigList().size() > 0) {

            // Fetch the HTML content of the page
            Document docJSoup = null;
            docJSoup = JsoupConnect(homeBanking.getUrl());
            Set<WebElementWrapper> uniqueWrapperElements = new HashSet<>();
            List<WebElement> finalList = new ArrayList<>();
            Set<WebElement> uniqueWebElements = new HashSet<>();
            for (com.allinweb.ch.util.SearchConfig searchConfig : arPriorities.getSearchConfigList()) {
                PriorityTypeEnum priorityTypeEnum = null;
                try {
                    priorityTypeEnum = PriorityTypeEnum.getPriorityType(
                            searchConfig.getSearchType().toString());
                } catch (Exception e) {
                    System.out.println(String.format("The ENUM: was not defined!"));
                    continue;
                }
                if (priorityTypeEnum == null) {
                    System.out.println("Define priorities!");
                    return null;
                }
                switch (priorityTypeEnum) {
                    case ByXPath -> {
                        List<String> names = searchConfig.getName();
                        Elements elementJSoup = null;
                        // TO DO  SEARCH VARIANTS AND DISTINCT BY THOSE WERE FOUND
                        for (String name : names) {
                            try {
                                List<WebElement> searchingElems =
                                        performActions.getCurrentDriver().findElements((By.xpath(name)));
                                uniqueWebElements.addAll(searchingElems);
                                // Add elements from the first list to the set
                                for (WebElement element : uniqueWebElements) {
                                    String href = element.getAttribute("href");
                                    String text = element.getText();
                                    String uniqueKey = href + text;
                                    WebElementWrapper wrapper = new WebElementWrapper(name, text, href, element);
                                    if (uniqueWrapperElements.add(wrapper)) {
                                        finalList.add(element);
                                    }
                                }
                            } catch (Exception e) {
                                System.out.println(String.format("WebDriver cannot read this format: %s", name));
                            }
                            try {
                                elementJSoup = docJSoup.select(name);
                                for (Element element : elementJSoup) {
                                    String href = element.absUrl("href");
                                    String text = element.text();

                                    // Print the URL and text
                                    if (Strings.isNullOrEmpty(href)) {
                                        href = element.attr("href");
                                    }

                                    // Check if the text is empty
                                    if (element.text().isEmpty()) {
                                        // Check for nested elements like SVG
                                        Element svg = element.selectFirst("svg");
                                        if (svg != null
                                                && svg.selectFirst("use") != null
                                                && svg.hasAttr("xlink:href")) {
                                            String svgHref =
                                                    svg.selectFirst("use").attr("xlink:href");
                                            System.out.println("Found SVG with href: " + svgHref
                                                    + " inside anchor with href: " + href);
                                            text = svgHref.toString();
                                        } else if (svg != null) {
                                            System.out.println(
                                                    "Found anchor with href: " + href + " containing nested SVG.");
                                            text = svg.toString();
                                        } else {
                                            System.out.println(
                                                    "Anchor with href: " + href + " has no text and no nested SVG.");
                                        }
                                    }

                                    WebElementWrapper bestMatch = null;
                                    double highestSimilarity = 0.0;

                                    for (WebElementWrapper wrapper : uniqueWrapperElements) {
                                        double similarity = jaccardSimilarity(text, wrapper.getText());
                                        if (similarity > highestSimilarity) {
                                            highestSimilarity = similarity;
                                            bestMatch = wrapper;
                                        }
                                    }

                                    if (bestMatch == null
                                            || highestSimilarity
                                                    < 0.8) { // Threshold to add new elements if no close match is
                                        // found
                                        // Convert Jsoup Element to Selenium WebElement
                                        //                WebElement webElement =
                                        // driver.findElement(By.xpath("//a[contains(text(), '" + text + "')]"));
                                        WebElementWrapper wrapper = new WebElementWrapper(name, text, href, null);
                                        if (uniqueWrapperElements.add(wrapper)) {
                                            finalList.add(wrapper.getWebElement());
                                        }
                                        ;
                                    } else {
                                        finalList.add(bestMatch.getWebElement());
                                    }
                                }
                            } catch (Exception e) {
                                System.out.println(String.format("Jsoup cannot read this format: %s", name));
                            }

                            //                                // Convert unique wrappers back to a list of
                            // elementsResponse
                            //                                for (WebElementWrapper wrapper : uniqueWrapperElements) {
                            //                                    if (wrapper.getWebElement() != null) {
                            //                                        finalList.add(wrapper.getWebElement());
                            //                                    } else {
                            //                                        // Handle Jsoup elements if necessary
                            //                                    }
                            //                                }
                        }
                        // Iterate over the selected links
                        //                          savedReferences.put(text, url);
                        elementsResponse.addAll(finalList);
                        finalList.clear();
                    }
                    case ByTagName -> {
                        List<String> names = searchConfig.getName();

                        // TO DO  SEARCH VARIANTS AND DISTINCT BY THOSE WERE FOUND
                        for (String name : names) {

                            if (name.equalsIgnoreCase("label")) {
                                try {
                                    List<WebElement> searchingElems =
                                            performActions.getCurrentDriver().findElements((By.tagName(name)));
                                    uniqueWebElements.addAll(searchingElems);
                                    // Add elements from the first list to the set
                                    for (WebElement element : uniqueWebElements) {
                                        String labelText = element.getText();
                                        String associatedText = "";

                                        // Get the value of the 'for' attribute
                                        String forAttribute = element.getAttribute("for");
                                        if (forAttribute != null) {
                                            // Find the associated element using the 'for' attribute value
                                            WebElement associatedElement = performActions
                                                    .getCurrentDriver()
                                                    .findElement(By.id(forAttribute));
                                            associatedText = getElementText(associatedElement);
                                        }
                                        if (!Strings.isNullOrEmpty(associatedText)) {
                                            labelText = labelText + "\n" + associatedText;
                                        }
                                        finalList.add(element);
                                    }
                                } catch (Exception e) {
                                    System.out.println(String.format("WebDriver cannot read this format: %s", name));
                                }
                            } else if (name.equalsIgnoreCase("input")) {
                                try {
                                    List<WebElement> searchingElems =
                                            performActions.getCurrentDriver().findElements((By.tagName(name)));
                                    uniqueWebElements.addAll(searchingElems);
                                    // Add elements from the first list to the set
                                    for (WebElement element : uniqueWebElements) {
                                        String labelText = element.getText();

                                        finalList.add(element);
                                        try {
                                            // Check if the input element has a placeholder attribute
                                            String placeholder = element.getAttribute("placeholder");
                                            if (placeholder != null && !placeholder.isEmpty()) {
                                                // If the placeholder attribute exists, print its value
                                                System.out.println("Placeholder: " + placeholder);
                                            }
                                        } catch (Exception e) {
                                            System.out.println(
                                                    String.format("WebDriver cannot read this format: %s", name));
                                        }
                                    }
                                } catch (Exception e) {
                                    System.out.println(String.format("WebDriver cannot read this format: %s", name));
                                }
                            }

                            // JavaScfript Search
                            if (name.equalsIgnoreCase("input")) {
                                List<WebElement> searchingElems = searchAllInputs(performActions.getCurrentDriver());
                                uniqueWebElements.addAll(searchingElems);
                                if (searchingElems != null && searchingElems.size() > 0) {
                                    finalList.addAll(searchingElems);
                                }
                            }
                        }
                        // Iterate over the selected links
                        //                          savedReferences.put(text, url);
                        elementsResponse.addAll(finalList);
                        finalList.clear();
                    }
                    case ByAttribute -> {
                        //                        String attributeValue =
                        //                                element.getAttribute(priority.getName().get(0));
                        //                        if (attributeValue != null && !attributeValue.isBlank()) {
                        //                            savedReferences.put(priority.getName().get(0), attributeValue);
                        //                        }

                        List<String> names = searchConfig.getName();

                        // TO DO  SEARCH VARIANTS AND DISTINCT BY THOSE WERE FOUND
                        for (String name : names) {
                            try {
                                List<WebElement> searchingElems = performActions
                                        .getCurrentDriver()
                                        .findElements((By.cssSelector("[" + name + "]")));
                                uniqueWebElements.addAll(searchingElems);
                                //                                List<WebElement> elements2 = webElements =
                                // arWebDriver
                                //                                        .getDriver()
                                //                                        .findElements(By.xpath("//*[@" +
                                // searchConfig.getName() + "]"));

                                // Add elements from the first list to the set
                                for (WebElement element : uniqueWebElements) {
                                    String testId = element.getAttribute(name);
                                    String labelText = element.getText();
                                    String associatedText = "";

                                    if (Strings.isNullOrEmpty(labelText)) {
                                        labelText = testId;
                                    }
                                    // Get the value of the 'for' attribute
                                    String forAttribute = element.getAttribute("for");
                                    if (forAttribute != null) {
                                        // Find the associated element using the 'for' attribute value
                                        WebElement associatedElement = performActions
                                                .getCurrentDriver()
                                                .findElement(By.id(forAttribute));
                                        associatedText = getElementText(associatedElement);
                                    }
                                    if (!Strings.isNullOrEmpty(associatedText)) {
                                        labelText = labelText + "\n" + associatedText;
                                    }
                                    finalList.add(element);
                                }
                            } catch (Exception e) {
                                System.out.println(String.format("WebDriver cannot read this format: %s", name));
                            }

                            //                            try {
                            //                                searchingElems =
                            //
                            // performActions.getCurrentDriver().findElements((By.cssSelector("input[" + name + "]")));
                            //                                //                                List<WebElement>
                            // elements2 = webElements =
                            //                                // arWebDriver
                            //                                //                                        .getDriver()
                            //                                //
                            // .findElements(By.xpath("//*[@" +
                            //                                // searchConfig.getName() + "]"));
                            //
                            //                                // Add elements from the first list to the set
                            //                                for (WebElement element : uniqueWebElements) {
                            //                                    String testId = element.getAttribute(name);
                            //                                    String labelText = element.getText();
                            //                                    String associatedText = "";
                            //
                            //                                    if (Strings.isNullOrEmpty(labelText)) {
                            //                                        labelText = testId;
                            //                                    }
                            //
                            //                                    // Get the value of the 'for' attribute
                            //                                    String forAttribute = element.getAttribute("for");
                            //                                    if (forAttribute != null) {
                            //                                        // Find the associated element using the 'for'
                            // attribute value
                            //                                        WebElement associatedElement =
                            //
                            // performActions.getCurrentDriver().findElement(By.id(forAttribute));
                            //                                        associatedText =
                            // getElementText(associatedElement);
                            //                                    }
                            //                                    if (!Strings.isNullOrEmpty(associatedText)) {
                            //                                        labelText = labelText + "\n" + associatedText;
                            //                                    }
                            //                                    finalList.add(element);
                            //                                }
                            //                            } catch (Exception e) {
                            //                                System.out.println(String.format("WebDriver cannot read
                            // this format: %s", name));
                            //                            }
                        }
                        // Iterate over the selected links
                        //                          savedReferences.put(text, url);
                        elementsResponse.addAll(finalList);
                        finalList.clear();

                        //                        try {
                        //                            webElements =
                        // performActions.getCurrentDriver().findElements((By.cssSelector("[" +
                        // searchConfig.getName() + "]"));
                        //                            webElements = arWebDriver
                        //                                    .getDriver()
                        //                                    .findElements(By.xpath("//*[@" + searchConfig.getName() +
                        // "]"));
                        //                            // Add elements from the first list to the set
                        //                            for (WebElement element : webElements) {
                        //                                String attributeValue = element.getAttribute(
                        //                                        searchConfig.getName().get(0));
                        //                                if (attributeValue != null && !attributeValue.isBlank()) {
                        //                                    savedReferences.put(searchConfig.getName().get(0),
                        // attributeValue);
                        //                                }
                        //                            }
                        //                        } catch (Exception e) {
                        //                            System.out.println(
                        //                                    String.format("WebDriver cannot read this format: %s",
                        // searchConfig.getName()));
                        //                        }
                    }
                    case ByChained -> {
                        String input = String.join(",", searchConfig.getName());
                        //                        String input = "By.tagName:input,By.id:id_Start,By.className:blabla";

                        // Parse the input string and create By objects
                        By[] locators = parseLocators(input);

                        // TO DO  SEARCH VARIANTS AND DISTINCT BY THOSE WERE FOUND
                        try {
                            List<WebElement> searchingElems =
                                    performActions.getCurrentDriver().findElements(new ByChained(locators));
                            uniqueWebElements.addAll(searchingElems);
                            for (WebElement element : uniqueWebElements) {
                                String labelText = element.getText();
                                String associatedText = "";

                                // Get the value of the 'for' attribute
                                String forAttribute = element.getAttribute("for");
                                if (forAttribute != null) {
                                    // Find the associated element using the 'for' attribute value
                                    WebElement associatedElement =
                                            performActions.getCurrentDriver().findElement(By.id(forAttribute));
                                    associatedText = getElementText(associatedElement);
                                }
                                if (!Strings.isNullOrEmpty(associatedText)) {
                                    labelText = labelText + "\n" + associatedText;
                                }
                                finalList.add(element);
                            }
                        } catch (Exception e) {
                            System.out.println("WebDriver cannot read this format");
                        }

                        // Iterate over the selected links
                        //                          savedReferences.put(text, url);
                        elementsResponse.addAll(finalList);
                        finalList.clear();

                        //                        try {
                        //                            webElements =
                        // performActions.getCurrentDriver().findElements((By.cssSelector("[" +
                        // searchConfig.getName() + "]"));
                        //                            webElements = arWebDriver
                        //                                    .getDriver()
                        //                                    .findElements(By.xpath("//*[@" + searchConfig.getName() +
                        // "]"));
                        //                            // Add elements from the first list to the set
                        //                            for (WebElement element : webElements) {
                        //                                String attributeValue = element.getAttribute(
                        //                                        searchConfig.getName().get(0));
                        //                                if (attributeValue != null && !attributeValue.isBlank()) {
                        //                                    savedReferences.put(searchConfig.getName().get(0),
                        // attributeValue);
                        //                                }
                        //                            }
                        //                        } catch (Exception e) {
                        //                            System.out.println(
                        //                                    String.format("WebDriver cannot read this format: %s",
                        // searchConfig.getName()));
                        //                        }
                    }
                    case xpath -> System.out.println("xpath case");
                    case coordinates -> System.out.println("coordinates case");
                    case ById -> System.out.println("ById case");
                    case ByClassName -> System.out.println("Default case");
                    case ByName -> System.out.println("Default case");
                    case ByLabels -> System.out.println("Default case");
                    case ByLinkText -> System.out.println("Default case");
                    case ByPartialLinkText -> System.out.println("Default case");
                    case ByCssSelector -> System.out.println("Default case"); //      ".nav-menu li";
                    case ExecuteScript -> System.out.println(
                            "Default case"); //      "return document.getElementById('search-top')");
                    case createXPath -> System.out.println(
                            "Default case"); //         Generates XPath Recursive tom the Elements Found
                    case dynamic -> System.out.println(
                            "Default case"); //         Generates Dynamic Action -> Click, Hover, Etc.
                    case jsoup -> System.out.println("Default case");
                        // OLD CASES
                        //                    case attribute -> {
                        //                        String attributeValue = element.getAttribute(priority.getName());
                        //                        if (attributeValue != null && !attributeValue.isBlank()){
                        //                            savedReferences.put(priority.getName(),attributeValue);
                        //                            System.out.println("savedReferences size: " +
                        // savedReferences.size());
                        //                        }
                        //                    }
                        //                    case xpath -> {
                        //                        savedReferences.put(priority.getName(),
                        // ARWebUtil.extractWebElementXPath(element));
                        //                        System.out.println("savedReferences size: " + savedReferences.size());
                        //                    }
                        //                    case coordinates -> {
                        //                        Rectangle coordinates = element.getRect();
                        //                        savedReferences.put(priority.getName(), (coordinates.getX() +
                        // (coordinates.getWidth()/2)) + "," +
                        //                                (coordinates.getY() + (coordinates.getHeight()/2)));
                        //                        System.out.println("savedReferences size: " + savedReferences.size());
                        //                    }
                }
            }
        }
        return elementsResponse;
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

    //        return (String) driver.executeScript(
    //                "function absoluteXPath(element) {" +
    //                        "var comp, comps = [];" +
    //                        "var parent = null;" +
    //                        "var xpath = '';" +
    //                        "var getPos = function(element) {" +
    //                        "var position = 1, curNode;" +
    //                        "if (element.nodeType == Node.ATTRIBUTE_NODE) {" +
    //                        "return null;" +
    //                        "}" +
    //                        "for (curNode = element.previousSibling; curNode; curNode = curNode.previousSibling) {" +
    //                        "if (curNode.nodeName == element.nodeName) {" +
    //                        "++position;" +
    //                        "}" +
    //                        "}" +
    //                        "return position;" +
    //                        "};" +
    //
    //                        "if (element instanceof Document) {" +
    //                        "return '/';" +
    //                        "}" +
    //
    //                        "for (; element && !(element instanceof Document); element = element.nodeType ==
    // Node.ATTRIBUTE_NODE ? element.ownerElement : element.parentNode) {" +
    //                        "comp = comps[comps.length] = {};" +
    //                        "switch (element.nodeType) {" +
    //                        "case Node.TEXT_NODE:" +
    //                        "comp.name = 'text()';" +
    //                        "break;" +
    //                        "case Node.ATTRIBUTE_NODE:" +
    //                        "comp.name = '@' + element.nodeName;" +
    //                        "break;" +
    //                        "case Node.PROCESSING_INSTRUCTION_NODE:" +
    //                        "comp.name = 'processing-instruction()';" +
    //                        "break;" +
    //                        "case Node.COMMENT_NODE:" +
    //                        "comp.name = 'comment()';" +
    //                        "break;" +
    //                        "case Node.ELEMENT_NODE:" +
    //                        "comp.name = element.nodeName;" +
    //                        "break;" +
    //                        "}" +
    //                        "comp.position = getPos(element);" +
    //                        "}" +
    //
    //                        "for (var i = comps.length - 1; i >= 0; i--) {" +
    //                        "comp = comps[i];" +
    //                        "xpath += '/' + comp.name.toLowerCase();" +
    //                        "if (comp.position !== null) {" +
    //                        "xpath += '[' + comp.position + ']';" +
    //                        "}" +
    //                        "}" +
    //
    //                        "return xpath;" +
    //
    //                        "} return absoluteXPath(arguments[0]);", element);

    private Document JsoupConnect(String Url) {
        try {
            // Set up an all-trusting trust manager
            TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                    public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {}

                    public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                }
            };

            // Install the all-trusting trust manager
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

            // Set up a hostname verifier that accepts all hostnames
            HostnameVerifier allHostsValid = new HostnameVerifier() {
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            };

            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);

            // Use Jsoup to connect to the URL
            return Jsoup.connect(Url).get();

        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return null;
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
    //            System.out.println("References saved to " + filePath);
    //        } catch (IOException e) {
    //            System.err.println("Error writing to file: " + e.getMessage());
    //        }
    //    }

    private void buildPriorityReferences(List<ARWebElement> elements) {
        Map<String, String> references = new HashMap<>();
        for (ARWebElement arElement : elements) {
            for (com.allinweb.ch.util.Priority priority : arPriorities.getAllPriorityList()) {
                switch (priority.getPriorityType()) {
                    case attribute -> {
                        String attributeValue = arElement
                                .getElement()
                                .getAttribute(priority.getName().get(0));
                        if (attributeValue != null && !attributeValue.isBlank()) {
                            references.put(priority.getName().get(0), attributeValue);
                        }
                    }
                    case xpath -> references.put(
                            priority.getName().get(0), ARWebUtil.extractWebElementXPath(arElement.getElement()));

                    case coordinates -> {
                        Rectangle coordinates = arElement.getElement().getRect();
                        references.put(
                                priority.getName().get(0),
                                (coordinates.getX() + (coordinates.getWidth() / 2)) + ","
                                        + (coordinates.getY() + (coordinates.getHeight() / 2)));
                    }
                }
            }
            arElement.getSavedReferences().putAll(references);
            references.clear();
        }
    }

    private void injectJavaScript(WebDriver driver) {
        // The JavaScript code to be injected
        try {
            // Navigate to the target page
            //            driver.get("https://www.ca-nextbank.ch/en/contact");

            // The JavaScript code to be injected
            //            String jsCode = "const hint = document.createElement('div');" + "hint.id = 'hint';"
            //                    + "hint.className = 'hint';"
            //                    + "document.body.appendChild(hint);"
            //                    + "const style = document.createElement('style');"
            //                    + "style.innerHTML = ` .hint {"
            //                    + "  position: absolute;"
            //                    + "  background-color: #f9f9f9;"
            //                    + "  border: 1px solid #ccc;"
            //                    + "  padding: 5px;"
            //                    + "  border-radius: 3px;"
            //                    + "  display: none;"
            //                    + "  z-index: 1000;"
            //                    + "} `;"
            //                    + "document.head.appendChild(style);"
            //                    + "document.body.addEventListener('mouseover', function(event) {"
            //                    + "  const target = event.target;"
            //                    + "  let hintText = `Tag: ${target.tagName.toLowerCase()}`;"
            //                    + "  if (target.type) {"
            //                    + "    hintText += `, Type: ${target.type}`;"
            //                    + "  }"
            //                    + "  if (target.innerText) {"
            //                    + "    hintText += `, Text: ${target.innerText}`;"
            //                    + "  }"
            //                    + "  hint.innerText = hintText;"
            //                    + "  hint.style.display = 'block';"
            //                    + "  hint.style.left = event.pageX + 'px';"
            //                    + "  hint.style.top = event.pageY + 'px';"
            //                    + "});"
            //                    + "document.body.addEventListener('mousemove', function(event) {"
            //                    + "  hint.style.left = event.pageX + 'px';"
            //                    + "  hint.style.top = event.pageY + 'px';"
            //                    + "});"
            //                    + "document.body.addEventListener('mouseout', function() {"
            //                    + "  hint.style.display = 'none';"
            //                    + "});";

            // JavaScript code to add and remove tooltip functionality
            String jsCode = "(function() {" + "    var tooltip = document.createElement('div');"
                    + "    tooltip.style.position = 'absolute';"
                    + "    tooltip.style.backgroundColor = 'black';"
                    + "    tooltip.style.color = 'white';"
                    + "    tooltip.style.padding = '5px';"
                    + "    tooltip.style.borderRadius = '3px';"
                    + "    tooltip.style.display = 'none';"
                    + "    tooltip.style.zIndex = '1000';"
                    + "    document.body.appendChild(tooltip);"
                    + "    function showTooltip(event) {"
                    + "        var tagName = event.target.tagName.toLowerCase();"
                    + "        tooltip.textContent = tagName;"
                    + "        tooltip.style.left = event.pageX + 'px';"
                    + "        tooltip.style.top = (event.pageY + 15) + 'px';"
                    + "        tooltip.style.display = 'block';"
                    + "    }"
                    + "    function hideTooltip() {"
                    + "        tooltip.style.display = 'none';"
                    + "    }"
                    + "    var mouseOverListener = showTooltip;"
                    + "    var mouseOutListener = hideTooltip;"
                    + "    window.addTooltipListeners = function() {"
                    + "        document.addEventListener('mouseover', mouseOverListener);"
                    + "        document.addEventListener('mouseout', mouseOutListener);"
                    + "    };"
                    + "    window.removeTooltipListeners = function() {"
                    + "        document.removeEventListener('mouseover', mouseOverListener);"
                    + "        document.removeEventListener('mouseout', mouseOutListener);"
                    + "        tooltip.style.display = 'none';"
                    + "    };"
                    + "    addTooltipListeners();"
                    + "})();";

            // Inject the JavaScript code into the page
            ((JavascriptExecutor) driver).executeScript(jsCode);

            // Allow some time to see the effect
            Thread.sleep(10000); // Sleep for 10 seconds to observe the result
        } catch (InterruptedException e) {
            System.out.println(e.getMessage());
        } finally {
            // Close the browser
            driver.quit();
        }
    }

    private void periodicPickOneCloneThread(
            WebDriver driver, String currentUrl, String[] dataArray, int port, int homeBankingId) {

        ErrorMessage errorMessage = performCloneLoad.dynamicPickOneCloneElementsDTO(
                driver, currentUrl, dataArray, searchHiddenFields, port, homeBankingId);

        if (errorMessage != null) {
            String[] lines = errorMessage.getErrorMessage().split("\n");

            performMessage.errorMessage(
                    errorMessage.getErrorTitle(),
                    errorMessage.getErrorHeader(),
                    (!Strings.isNullOrEmpty(lines[0]) ? lines[0] : null),
                    (!Strings.isNullOrEmpty(lines[0]) ? lines[1] : null),
                    null,
                    0);
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

    private void periodicHoverPickThread(WebDriver driver, String currentUrl, String[] dataArray, int port) {
        ErrorMessage errorMessage =
                performPickLoad.dynamicHoverPickElementsDTO(driver, currentUrl, dataArray, searchHiddenFields, port);

        if (errorMessage != null) {
            String[] lines = errorMessage.getErrorMessage().split("\n");

            performMessage.errorMessage(
                    errorMessage.getErrorTitle(),
                    errorMessage.getErrorHeader(),
                    (!Strings.isNullOrEmpty(lines[0]) ? lines[0] : null),
                    (!Strings.isNullOrEmpty(lines[0]) ? lines[1] : null),
                    null,
                    0);
        }
    }

    private void periodicSearchThread(WebDriver driver, String currentUrl, String[] dataArray, int port) {
        // "scannerTool", "scannerGrid", "searchTerms"
        ErrorMessage errorMessage = performPreLoad.dynamicLoadElementsDTO(
                driver, currentUrl, dataArray, searchHiddenFields, port, "scannerTool", "scannerGrid", "searchTerms");

        if (errorMessage != null) {
            String[] lines = errorMessage.getErrorMessage().split("\n");

            performMessage.errorMessage(
                    errorMessage.getErrorTitle(),
                    errorMessage.getErrorHeader(),
                    (!Strings.isNullOrEmpty(lines[0]) ? lines[0] : null),
                    (!Strings.isNullOrEmpty(lines[0]) ? lines[1] : null),
                    null,
                    0);
        }
    }

    private ElementDTO prefillDefinedName(List<ElementDTO> elementsFound) {

        StringBuilder sb = new StringBuilder();

        ElementDTO pickTarget = null;
        String nameDefined = "";
        for (ElementDTO picked : elementsFound) {
            //            if (picked.getTypeElement().equalsIgnoreCase("clicked")) {
            //                continue; // To avoid the Clicked Twice
            //            }

            pickTarget = new ElementDTO(picked);

            if (!Strings.isNullOrEmpty(pickTarget.getAttribId())
                    || !Strings.isNullOrEmpty(pickTarget.getAttribName())
                    || !Strings.isNullOrEmpty(pickTarget.getSomeText())) {
                //                nameDefined = pickTarget.getTagName()
                //                        + (!Strings.isNullOrEmpty(pickTarget.getAttribName())
                //                                ? "-" + pickTarget.getAttribName()
                //                                : !Strings.isNullOrEmpty(pickTarget.getAttribId())
                //                                        ? "-" + pickTarget.getAttribId()
                //                                        : !Strings.isNullOrEmpty(pickTarget.getSomeText())
                //                                                ? "-" +
                // performActions.truncateAndNormalize(pickTarget.getSomeText(), 30)
                //                                                : "");
                nameDefined = (!Strings.isNullOrEmpty(pickTarget.getSomeText())
                        ? performActions.truncateAndNormalize(pickTarget.getSomeText(), 30)
                        : !Strings.isNullOrEmpty(pickTarget.getAttribId())
                                ? pickTarget.getAttribId()
                                : !Strings.isNullOrEmpty(pickTarget.getAttribName()) ? pickTarget.getAttribName() : "");

            } else if (picked.getAttributeData() != null) {

                // Split by comma to get key-value pairs
                pickTarget.getAttributeData();

                String idValue = null;
                String nameValue = null;
                String typeValue = null;

                // Loop through each key-value pair
                for (AttributeData part : pickTarget.getAttributeData()) {

                    String key = part.getName();
                    String value = part.getValue().trim().replaceAll("\"", ""); // Remove quotes

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
                    nameDefined = pickTarget.getTagName() + "-" + idValue;
                } else if (nameValue != null) {
                    nameDefined = pickTarget.getTagName() + "-" + nameValue;
                } else if (typeValue != null) {
                    nameDefined = pickTarget.getTagName() + "-" + typeValue;
                } else {
                    nameDefined = pickTarget.getTagName();
                }

                // sb.append("nameDefined: " + nameDefined).append("\n");
                sb.append("TagType: " + pickTarget.getTagName()).append("\n");
                sb.append("ID: " + pickTarget.getAttribId()).append("\n");
                sb.append("Name: " + pickTarget.getAttribName()).append("\n");
                sb.append("Text: " + pickTarget.getSomeText()).append("\n");

                if (!Strings.isNullOrEmpty(pickTarget.getCoords())) {
                    sb.append("Coordinates: " + pickTarget.getCoords()).append("\n");
                    coordsTextField.setText(pickTarget.getCoords());
                } else {
                    sb.append("Coordinates: EMPTY").append("\n");
                }

                if (!Strings.isNullOrEmpty(pickTarget.getSearchAttributeValue())) {
                    sb.append("Search Attrib: " + pickTarget.getSearchAttributeValue() + "="
                                    + pickTarget.getSearchAttributeValue())
                            .append("\n");
                    searchAttribValueField.setText(pickTarget.getSearchAttributeValue());
                    searchAttribValueField.setStyle("-fx-font-size: 12px; -fx-text-fill: blue;");
                } else {
                    sb.append("Search Attrib: No Defined").append("\n");
                }

                sb.append("Named: " + nameDefined).append("\n");
                sb.append("All Attributes Found: ").append("\n");
                for (AttributeData attributeData : pickTarget.getAttributeData()) {
                    sb.append("->  ")
                            .append(attributeData.getName() + "=" + attributeData.getValue())
                            .append("\n");
                }

                iFrameCoords = "";
            }
        }

        Platform.runLater(() -> {
            countdownTextField.setText(sb.toString());
            countdownTextField.setStyle("-fx-font-size: 12px; -fx-text-fill: blue;");
            //            textFlowResult.getChildren().clear();
            //            textFlowResult.getChildren().addAll(countdownTextField);
            //            textFlowResult.requestLayout();
            //            contentPane.requestLayout();
        });

        return pickTarget;
    }

    // Recursive helper method to process LinkedHashMap
    private String handleLinkedHashMap(LinkedHashMap<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            sb.append(entry.getKey())
                    .append(": ")
                    .append(entry.getValue().toString())
                    .append(" ");
        }
        return sb.toString();
    }

    private void revertCloneInjections(WebDriver driver) {
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

    private void revertPickInjections(WebDriver driver) {
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
        ARLogger.getInstance(ARScannedElementPane.class).fine("Number of input elements: " + inputElements.size());
        return inputElements;
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

    public enum LocatorType {
        TAGNAME,
        ID,
        CLASSNAME,
        CSSSELECTOR,
        XPATH
    }

    private DatabaseUserDTO loadUserData(int bankId) {
        String selectSQL =
                "SELECT bank.ID, bank.Name, Url, bank.priority, COUNT(bot.ID) Jobs, search_config searchConfig, options_config optionsConfig, username, password "
                        + "                         FROM home_banking bank "
                        + "                         left join bot_job bot on bot.active = 1 and bot.home_banking_id = bank.id "
                        + " WHERE bank.id = " + bankId
                        + "                         group by bank.ID, bank.Name, bank.Url, bank.priority, bank.search_config, bank.options_config, bank.username, bank.password ";
        try (Statement stmt = ARSharedResources.getInstance().getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(selectSQL)) {
            while (rs.next()) {
                String id = rs.getString("ID");
                String jobs = rs.getString("Jobs");
                String name = rs.getString("Name");
                String url = rs.getString("Url");
                String priority = rs.getString("Priority");
                String searchConfig = rs.getString("searchConfig");
                String optionsConfig = rs.getString("optionsConfig");
                String username = rs.getString("username");
                String password = rs.getString("password");
                databaseUserDto = new DatabaseUserDTO(
                        id, jobs, name, url, priority, searchConfig, optionsConfig, username, password);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return databaseUserDto;
        //        jobUserList.clear();
        //        loadBotJobData();
    }

    private void recallJob() {
        executeJob();

        if (performActions.getCurrentDriver().getWindowHandles().size() != performActions.windowHandlesList.size()) {
            performActions.updateWindowHandlesList();
            updateButtonState();
        }

        // Review if Has Not Executed Instructions
        boolean hasUnexecutedInstructions = botJobLoadList.get(0).getBlockLoadDTOList().stream()
                .flatMap(block -> block.getInstructionLoadDTOS().stream())
                .anyMatch(instruction -> instruction.getExecuted() == null || !instruction.getExecuted());

        if (hasUnexecutedInstructions) {
            Text variableText1Styled = new Text("Recall the Executions for this page?");

            variableText1Styled.setStyle("-fx-font-size: 18px; -fx-fill: blue;");

            VBox combinedTextContainer = new VBox();
            combinedTextContainer.setSpacing(5); // Add some sp

            combinedTextContainer.getChildren().add(variableText1Styled);

            //            boolean result = performMessage.showAlertCombinedVBOX(
            //                    Alert.AlertType.CONFIRMATION, "Recall Pre-Launch", "Recall Pre Test.", null,
            // combinedTextContainer);
            //
            //            if (result) {
            //                recallJob();
            //            }
        }
    }

    private boolean executeJob() {
        if (performActions.waitForPage == null) {
            String updateTimeout =
                    ARPropertyManager.getInstance().getProperty(ARPropertyEnum.WEBDRIVER_PAGE_UPDATE_TIMEOUT_SEC);
            String interactionTimeout =
                    ARPropertyManager.getInstance().getProperty(ARPropertyEnum.WEBDRIVER_PAGE_UPDATE_TIMEOUT_SEC);
            performActions.waitForPage = new WebDriverWait(
                    performActions.getCurrentDriver(), Duration.ofSeconds(Integer.parseInt(updateTimeout)));
            performActions.waitForAction = new WebDriverWait(
                    performActions.getCurrentDriver(), Duration.ofSeconds(Integer.parseInt(interactionTimeout)));
        }

        //        if (repository == null) {
        //            repository = new Repository(ARSharedResources.getInstance().getSession());
        //        }
        try {
            baseLogFile = new File(ARPropertyManager.getInstance().getProperty(ARPropertyEnum.FOLDER_PATH_LOG)
                    + ARConstants.FILE_NAME_SCANNER_BASE_LOG);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        List<BlockLoadDTO> blocksLoaded = botJobLoadList.get(0).getBlockLoadDTOList();
        String botJobName = botJobLoadList.get(0).getName();

        //        ARPropertyManager managerProps = ARPropertyManager.getInstance();
        String excelPath = ARPropertyManager.getInstance().getProperty(ARPropertyEnum.FOLDER_PATH_EXCEL);
        excelPath = excelPath + "\\" + blocksLoaded.get(0).getBotJobName() + ".xlsx";
        if (!(new File(excelPath)).exists()) {

            Text variableText1Styled = new Text("File Excel Does not Exist");
            variableText1Styled.setStyle("-fx-font-size: 18px; -fx-fill: blue;");

            Text variableText2Styled = new Text("IS MANDATORY TO HAVE EXCEL FILE FOR TESTS!");
            variableText2Styled.setStyle("-fx-font-size: 18px; -fx-fill: red;");

            Text variableText3Styled = new Text(excelPath);
            variableText3Styled.setStyle("-fx-font-size: 18px; -fx-fill: blue;");

            VBox combinedTextContainer = new VBox();
            combinedTextContainer.setSpacing(5); // Add some sp

            combinedTextContainer.getChildren().addAll(variableText1Styled, variableText2Styled, variableText3Styled);

            performMessage.showAlertCombinedVBOX(
                    Alert.AlertType.WARNING, "Missing file excel", "File Not Exist!", null, combinedTextContainer);
            return false;
        }

        Labels.initializeLabelsInSpecLang("en");
        Properties labelsValue = Labels.labelsValue;

        // Assuming blocksLoaded is your List<BlockLoadDTO>
        List<String> allActions = blocksLoaded.stream()
                .flatMap(blockLoadDTO ->
                        blockLoadDTO
                                .getInstructionLoadDTOS()
                                .stream()) // Flatten the stream of BlockLoopInstructionLoadDTO
                .map(InstructionLoadDTO::getActions) // Extract the actions
                .collect(Collectors.toList()); // Collect all actions into a List

        ExcelReader excelReader = new ExcelReader();
        ExtractedData extractedData = null;
        try {
            extractedData = excelReader.extractData(excelPath, allActions);
        } catch (Exception e) {

            performMessage.errorMessage(
                    "Excel Error",
                    "Could Not Execute Excel File",
                    "Check All Excel Columns and Values!",
                    null,
                    null,
                    0);

            //            Platform.exit();
        }

        if (extractedData.getNumberOfDataRows() == 0) {
            extractedData.addField("$EMPTY");
            extractedData.addFieldValue("$EMPTY", "$EMPTY", 0);
        }

        if (extractedData != null && extractedData.getErrorMessage() != null) {

            //            Text variableText1Styled = new Text("Verify the Possible Errors:");
            //            variableText1Styled.setStyle("-fx-font-size: 18px; -fx-fill: blue;");
            //
            //            Text variableText2Styled = new Text("1. Excel File is OPEN");
            //            variableText2Styled.setStyle("-fx-font-size: 18px; -fx-fill: red;");
            //
            //            Text variableText3Styled = new Text("2. Column Names Different from INPUT names");
            //            variableText3Styled.setStyle("-fx-font-size: 18px; -fx-fill: red;");
            //
            //            Text variableText4Styled = new Text("3. INPUTS names Not In Excel File");
            //            variableText4Styled.setStyle("-fx-font-size: 18px; -fx-fill: red;");
            //
            //            VBox combinedTextContainer = new VBox();
            //            combinedTextContainer.setSpacing(5); // Add some sp
            //
            //            combinedTextContainer
            //                    .getChildren()
            //                    .addAll(variableText1Styled, variableText2Styled, variableText3Styled,
            // variableText4Styled);
            //
            //            performMessage.showAlertCombinedVBOX(
            //                    Alert.AlertType.ERROR,
            //                    "Excel File Error",
            //                    "Check All Excel Columns and Values!",
            //                    null,
            //                    combinedTextContainer);

            performMessage.errorMessage(
                    "Excel Error", "Could Not Execute Excel File", extractedData.getErrorMessage(), null, null, 0);

            return false;
        }

        //        Set<String> blockClickables = blocksLoaded.stream()
        //                .map(BlockLoadDTO::getBlockLoopInstructionLoadDTOS)
        //                .reduce((identity, accumulated) -> {
        //                    accumulated.addAll(identity);
        //                    return accumulated;
        //                })
        //                .get()
        //                .stream()
        //                .map(BlockLoopInstructionLoadDTO::getActions)
        //                .filter(action -> action.contains(ARConstants.CLICK))
        //                .collect(Collectors.toSet());

        //        String browser = ARPropertyManager.getInstance().getProperty(ARPropertyEnum.BROWSER);
        //            WebPage webPage = new WebPage(browser, homeBankingDTO.getUrl());

        int botJobId = blocksLoaded.get(0).getBotJobId();

        // Original BotJobDTO
        //        BotJobDTO selectedJob = ARSharedResources.getInstance().getEntityById(BotJobDTO.class, botJobId);

        String baseLogString = blocksLoaded.get(0).getBotJobName()
                + ARConstants.FIELDS_SEPARATOR
                + labelsValue.getProperty(Labels.START);

        printBaseLog(baseLogFile, generateTimestamp(), baseLogString);

        ExcelWriter.ExcelChain writerReport = new ExcelWriter(
                        botJobLoadList.get(0).getName(), performActions.getCurrentDriver(), false)
                .withPurpose("report");
        writerReport.insertReportHead();

        ExcelWriter.ExcelChain writerExport = null;
        //                new ExcelWriter(blocksLoaded.get(0).getName(),
        // performActions.getCurrentDriver()).withPurpose("export");
        boolean excelExportOnceCreation = true;
        //        writerExport.insertReportHead();

        Set<String> mapIgnore = new HashSet<>();

        //        boolean searchByJavaScript = checkJavaScript.isSelected();

        String mainMsg = "";
        boolean byPassNotFound = false;
        boolean byPassFlagLoop = false;
        boolean success = true;
        boolean stopAll = false;
        long botJobStartTime = System.nanoTime();
        long totalExecutionTime = 0;
        String resultActions = "No instruction executed yet";
        boolean showAlert = true;
        String extraMsg = "";
        short status = (short) ExcelReportStatusEnum.ERROR.ordinal();
        Map<String, String> dataExcel = null;

        clearFields();

        //        ExcelReportDTO report = new ExcelReportDTO();
        //        report.setOrder((short) blocksLoaded.get(0).getId());
        //        report.setStartDate(LocalDateTime.now());
        //        report.setBatchJobId(selectedJob.getId());
        //        report.setBotJobDTO(selectedJob);
        //        report.setStatus((short) ExcelReportStatusEnum.NOT_RUN.ordinal());

        // Execute All Blocks starting from executeSpecificBlock if Defined
        int executeSpecificBlock = comboBoxBlocks.getValue().getVarId();

        mapOperators = new HashMap<>();
        mapExport = new LinkedHashMap<>();

        Map<String, String> mapSavedLocators = new HashMap<>();

        Set<Integer> parentIdsForLoop = null;
        Map<String, List<Integer>> mapConditional = new HashMap<>(); // <parentId:Limit Loops> -> <1|5 Times>
        Map<String, Integer> mapLoops = new HashMap<>(); // <parentId:Limit Loops> -> <1|5 Times>
        Map<String, Integer> mapRefresh = new HashMap<>(); // <parentId:Limit Loops> -> <1|5 Times>
        Set<String> loopBlockActive = new HashSet<>();
        Map<String, Integer> loopBlockLimits = new HashMap<>();

        ARConstants.ConditionStatus currentCondition = ARConstants.ConditionStatus.NONE;
        ARConstants.ConditionStatus previousCondition;
        ARConstants.ConditionStatus progressCondition;
        ARConstants.DialogModal respModal;

        int exportIndex = 1;
        if (extractedData.getNumberOfDataRows() > 0) {

            // Execute All Blocks starting from executeSpecificBlock if Defined
            int currentBlock = (executeSpecificBlock > -1) ? executeSpecificBlock - 1 : 0;

            blockLoop:
            while (currentBlock <= blocksLoaded.size() - 1 && blocksLoaded.size() > 0 && !stopAll) {
                long blockStartTime = System.nanoTime();

                currentCondition = ARConstants.ConditionStatus.NONE;
                previousCondition = ARConstants.ConditionStatus.NONE;
                progressCondition = ARConstants.ConditionStatus.NONE;

                respModal = ARConstants.DialogModal.NONE;

                int parentBlockCondition = -1;

                instructionsExecuted.clear();

                BlockLoadDTO blockLoad = blocksLoaded.get(currentBlock);
                String excelFieldName = blockLoad.getExportFile();

                String blockName = blocksLoaded.get(currentBlock).getName();
                int blockOrder = blocksLoaded.get(currentBlock).getBlockOrderNumber();
                String blockReportName = "#" + blockOrder + " " + blockName;

                int blockWait = blocksLoaded.get(currentBlock).getWait() > 0
                        ? blocksLoaded.get(currentBlock).getWait()
                        : 2;
                boolean blockActive = blocksLoaded.get(currentBlock).getActive();

                // It Searches the Block That have finished the Loops to Avoid recursivity
                if (loopBlockActive.size() > 0) {
                    for (String blocLoopKey : loopBlockActive) {
                        if (mapLoops.containsKey(blocLoopKey)) {
                            if (mapLoops.get(blocLoopKey) == 0) {
                                stopAll = true;
                                int limit = loopBlockLimits.get(blocLoopKey);

                                Pair<String, String> msgBlock = new Pair(blocLoopKey, "0");

                                // Excel Report and Log
                                performActions.logAndReport(
                                        currentCondition,
                                        true,
                                        true,
                                        blockStartTime,
                                        blockReportName,
                                        success,
                                        new String[] {ARConstants.GOTO},
                                        msgBlock,
                                        dataExcel,
                                        writerReport,
                                        "GOTO Limit Reached",
                                        blocLoopKey + " Reached: 0");

                                msgBlock = new Pair(
                                        String.format("Exit at Block Name: \"%s\"", blockLoad.getName()),
                                        ARConstants.EXIT);

                                // Excel Report and Log
                                performActions.logAndReport(
                                        currentCondition,
                                        true,
                                        true,
                                        blockStartTime,
                                        blockReportName,
                                        success,
                                        new String[] {ARConstants.EXIT},
                                        msgBlock,
                                        dataExcel,
                                        writerReport,
                                        "Stopping App",
                                        String.format("Exit at Block Name: \"%s\"", blockName));

                                performActions.gotoLimitExecution(limit, resultActions);

                                continue blockLoop;
                            }
                        }
                    }
                }

                if (!blockActive) {
                    currentBlock++;

                    Pair<String, String> msgBlock =
                            new Pair(String.format("Ignore: \"%s\"", blockLoad.getName()), ARConstants.IGNORE);

                    // Excel Report and Log
                    performActions.logAndReport(
                            currentCondition,
                            true,
                            true,
                            blockStartTime,
                            blockReportName,
                            success,
                            new String[] {ARConstants.IGNORE},
                            msgBlock,
                            dataExcel,
                            writerReport,
                            "BLOCK IGNORED",
                            String.format("Block: \"%s\" is Inactive: ", blockName));

                    continue;
                }

                try {

                    Pair<String, String> msgBlock = new Pair(blockLoad.getName(), ARConstants.EXCEL_BLOCK_HEADER);

                    // Block Header Format
                    performActions.logAndReport(
                            currentCondition,
                            true,
                            false,
                            blockStartTime,
                            blockReportName,
                            success,
                            new String[] {ARConstants.EXCEL_BLOCK_HEADER},
                            msgBlock,
                            null,
                            writerReport,
                            null,
                            null);

                    performActions.onHoldInSeconds(blockWait);

                    msgBlock = new Pair(
                            String.format("Default Wait: \"%s\" ->  %d Seconds", blockLoad.getName(), blockWait),
                            ARConstants.HOLD);

                    // Excel Report and Log
                    performActions.logAndReport(
                            currentCondition,
                            true,
                            true,
                            blockStartTime,
                            blockReportName,
                            success,
                            new String[] {ARConstants.HOLD},
                            msgBlock,
                            dataExcel,
                            writerReport,
                            "BLOCK DEFAULT WAIT",
                            String.format("Block: \"%s\" Wait %s Seconds: ", blockName, blockWait));

                } catch (Exception ex) {
                    ARLogger.getInstance(ARScannedElementPane.class)
                            .severe(String.format("Error Wait Block for :\"%s\"", blockLoad.getName()));
                }

                // Step 1: Get all ParentIds For LOOPs Filter rows where actions = "REFRESH_LOOP" or "LOOP" on current
                // Block
                parentIdsForLoop = performActions.getParentIdsForLoop(
                        blocksLoaded.get(currentBlock).getInstructionLoadDTOS());

                // Step 2: Get all Conditional By parentId for Index Locator on current Block Relocate "IF", "ELSEIF",
                // "ELSE", and "ENDIF"
                mapConditional = performActions.getConditionIndexMapByParentId(blockLoad);

                // Step 3: Get all Instructions Ids on current Block
                int[] instructionIds = blockLoad.getInstructionLoadDTOS().stream()
                        .mapToInt(InstructionLoadDTO::getId)
                        .toArray();

                // Step 2: Filter rows where actions = "REFRESH_LOOP" or "LOOP" and collect into the map

                //                mapLoops = performActions.getLoopAndRefreshLoops(
                //                        blocksLoaded.get(currentBlock).getBlockLoopInstructionLoadDTOS());

                //                executionTimes++;
                boolean jumpGoto = false;
                boolean jumpLoop = false;
                boolean jumpGotoError = false;
                boolean jumpLoopError = false;
                boolean refreshLoop = false;
                boolean refreshOnly = false;

                for (int i = 0; success && i < extractedData.getNumberOfDataRows() && !stopAll; i++) {
                    mapExport.clear();
                    //                    writerReport.insertBlockSeparation(blockLoad.getName());

                    dataExcel = extractedData.getRowFieldValues(i);

                    int currentIndex = 0;

                    instructionLoop:
                    while (currentIndex < instructionIds.length && !stopAll) {
                        // Resets the success
                        success = true;

                        long currentInstructionStartTime = System.nanoTime();

                        InstructionLoadDTO currentInstruction =
                                blockLoad.getInstructionLoadDTOS().get(currentIndex);

                        byPassFlagLoop = parentIdsForLoop.contains(currentInstruction.getId());

                        mainMsg = currentInstruction.getOptional() ? "OPTIONAL INSTRUCTION" : "MANDATORY INSTRUCTION";

                        if (!currentInstruction.getInstructionActive()) {

                            String nameInstruc = "(" + currentInstruction.getId() + ") " + currentInstruction.getName();
                            Pair<String, String> msgBlock =
                                    new Pair(String.format("Ignore: \"%s\"", nameInstruc), ARConstants.IGNORE);

                            // Excel Report and Log
                            performActions.logAndReport(
                                    currentCondition,
                                    true,
                                    true,
                                    blockStartTime,
                                    blockReportName,
                                    success,
                                    new String[] {ARConstants.IGNORE},
                                    msgBlock,
                                    dataExcel,
                                    writerReport,
                                    "INSTRUCTION IGNORED",
                                    String.format("Instruction: \"%s\" is Inactive: ", nameInstruc));

                            currentIndex++;

                            continue;
                        }

                        mapSavedLocators.clear();

                        // Loop through the instructionReferenceLoadDTOList
                        if (currentInstruction.getInstructionReferenceLoadDTOList() != null) {
                            for (InstructionReferenceLoadDTO reference :
                                    currentInstruction.getInstructionReferenceLoadDTOList()) {
                                // Populate the map with referenceType as the key and value as the value
                                mapSavedLocators.put(reference.getReferenceType(), reference.getValue());
                            }
                        }

                        currentIndex++;

                        // Allow Re-Execute Instructions in Previous Blocks
                        //                        if (currentInstruction.getExecuted() == null ||
                        // !currentInstruction.getExecuted()) {
                        boolean execGetOrSet = false;
                        boolean getAction = false;
                        boolean execCheckValue = false;
                        boolean excelWriteOperation = false;
                        boolean pauseOperation = false;

                        String xPathOperation = null;
                        String parentField = null;
                        String parentFieldLoop = null;
                        String fieldName = null;
                        int parentId = currentInstruction.getParentId();

                        if (mapIgnore.contains(currentInstruction.getId() + "-" + currentInstruction.getName())) {
                            continue;
                        }

                        //                        String[] operation =
                        // UtilsMethods.splitIfContains(instruction.getOperation(),
                        // ARConstants.ACTION_SPECIFICATIONS_SPLITTER);
                        String[] actions =
                                currentInstruction.getActions().split(ARConstants.ACTION_SPECIFICATIONS_SPLITTER);
                        String[] operations = currentInstruction.getOperation() != null
                                ? currentInstruction.getOperation().split(ARConstants.ACTION_SPECIFICATIONS_SPLITTER)
                                : null;

                        if (actions[0].equalsIgnoreCase(ARConstants.IF)
                                || actions[0].equalsIgnoreCase(ARConstants.ELSEIF)
                                || actions[0].equalsIgnoreCase(ARConstants.ELSE)
                                || actions[0].equalsIgnoreCase(ARConstants.ENDIF)) {
                            currentCondition = ARConstants.ConditionStatus.valueOf(actions[0]);
                            if (previousCondition.equals(ARConstants.ConditionStatus.NONE)) {
                                previousCondition = currentCondition;
                                parentBlockCondition = parentId;
                            } else if (!previousCondition.equals(
                                    currentCondition)) { // To Reset the Progress to the Next Block
                                previousCondition = currentCondition;
                            }

                            // Conditions When Pass to any of then
                            if (progressCondition.equals(ARConstants.ConditionStatus.IF_PASSED)
                                    || progressCondition.equals(ARConstants.ConditionStatus.ELSEIF_PASSED)) {
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
                                    currentCondition = ARConstants.ConditionStatus.NONE;
                                    progressCondition = ARConstants.ConditionStatus.NONE;
                                    continue instructionLoop;
                                }
                            } else if (currentCondition.equals(ARConstants.ConditionStatus.ENDIF)) {
                                currentCondition = ARConstants.ConditionStatus.NONE;
                                previousCondition = ARConstants.ConditionStatus.NONE;
                                progressCondition = ARConstants.ConditionStatus.NONE;
                                parentBlockCondition = -1;
                            }
                            continue;
                        }

                        // Case for Inputs
                        String valueInsert = "No Data Found";
                        if (actions[0].equals(ARConstants.INSERT) && actions[1].equals(ARConstants.ENTER)) {
                            String reference = actions[2];
                            valueInsert = dataExcel.get(reference);
                        } else if (actions[0].equals(ARConstants.INSERT)) {
                            String reference = actions[1];
                            valueInsert = dataExcel.get(reference);
                        }

                        Pair<String, String> msgInstruction = null;
                        if (actions[0].equalsIgnoreCase(ARConstants.GOTO)) {
                            // <currentId:blockId:blockOrderNumber:bockName>
                            msgInstruction = performActions.getBlockDetailsById(blocksLoaded, currentInstruction);
                            if (msgInstruction == null) {
                                msgInstruction = new Pair("GO TO Block \"Unknown\"", "Unknown");
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
                                msgInstruction = new Pair<>(
                                        msgInstruction.getKey(), String.valueOf(mapLoops.get(msgInstruction.getKey())));
                            }

                        } else if (actions[0].equalsIgnoreCase(ARConstants.LOOP)) {
                            // <currentId:parentId:parentName>
                            msgInstruction = performActions.getInstructionDetailsById(
                                    blocksLoaded.get(currentBlock).getInstructionLoadDTOS(), currentInstruction);

                            if (msgInstruction == null) {
                                msgInstruction = new Pair("Jump To Parent \"Unknown\"", "Unknown");
                                success = false;
                            } else if (!mapLoops.containsKey(msgInstruction.getKey())) {
                                jumpLoopError = false;
                                mapLoops.put(msgInstruction.getKey(), Integer.valueOf(msgInstruction.getValue()));
                            } else if (mapLoops.containsKey(msgInstruction.getKey())) {
                                // Updates the msgInstruction
                                msgInstruction = new Pair<>(
                                        msgInstruction.getKey(), String.valueOf(mapLoops.get(msgInstruction.getKey())));
                            }
                        } else if (actions[0].equalsIgnoreCase(ARConstants.REFRESH_LOOP)) {
                            msgInstruction = performActions.getInstructionDetailsById(
                                    blocksLoaded.get(currentBlock).getInstructionLoadDTOS(), currentInstruction);
                            if (msgInstruction == null) {
                                msgInstruction = new Pair("Jump To Parent \"Unknown\"", "Unknown");
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
                                msgInstruction = new Pair<>(msgInstruction.getKey(), updMsg);
                            }
                        } else if (actions[0].equalsIgnoreCase(ARConstants.SET_VALUE)
                                || (actions[0].equalsIgnoreCase(ARConstants.GET_VALUE))) {
                            msgInstruction = new Pair(
                                    currentInstruction.getName(),
                                    (currentInstruction.getOperation() != null
                                            ? "(" + parentId + ")-" + operations[0] + ":" + operations[1]
                                            : (actions[0].equalsIgnoreCase(ARConstants.INSERT)) ? valueInsert : ""));
                        } else {
                            msgInstruction = new Pair(
                                    "(" + currentInstruction.getId() + ")-" + currentInstruction.getName(),
                                    (currentInstruction.getOperation() != null
                                            ? currentInstruction.getOperation()
                                            : (actions[0].equalsIgnoreCase(ARConstants.INSERT)) ? valueInsert : ""));
                        }

                        resultActions = performActions.actionResultMessage(blockName, actions, msgInstruction);

                        extraMsg = "";

                        if (actions[0].equalsIgnoreCase(ARConstants.PAUSE)) {
                            pauseOperation = true;

                            respModal = performMessage.showCustomModalDialogDragWin11(
                                    "PAUSE BOT JOB",
                                    String.format("PAUSE BOT JOB at Block Name:\"%s\"", blockLoad.getName()),
                                    " Please click OK to continue!",
                                    null,
                                    null,
                                    false,
                                    "Continue",
                                    "stop all",
                                    0);
                        }

                        if (actions[0].equalsIgnoreCase(ARConstants.LOOP)) {
                            parentFieldLoop = performActions.getInstructionParentField(currentInstruction, blockLoad);
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

                        } else if (actions[0].equalsIgnoreCase(ARConstants.REFRESH_ONLY)) {
                            refreshOnly = true;
                        } else if (actions[0].equalsIgnoreCase(ARConstants.REFRESH_LOOP)) {
                            parentFieldLoop = performActions.getInstructionParentField(currentInstruction, blockLoad);
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
                        } else if (actions[0].equalsIgnoreCase(ARConstants.GET_VALUE)
                                || actions[0].equalsIgnoreCase(ARConstants.SET_VALUE)) {

                            execGetOrSet = true;

                            getAction = actions[0].equalsIgnoreCase(ARConstants.GET_VALUE);

                            xPathOperation = performActions.getXPathInstruction(currentInstruction, blockLoad);
                            parentField = performActions.getInstructionParentField(currentInstruction, blockLoad);

                        } else if (actions[0].equalsIgnoreCase(ARConstants.CHECK_VALUE)) {
                            execCheckValue = true;
                            parentField = performActions.getInstructionParentField(currentInstruction, blockLoad);
                        } else if (actions[0].equalsIgnoreCase(ARConstants.EXTRACT_FIELD)) {
                            excelWriteOperation = true;
                            parentField = performActions.getInstructionParentField(currentInstruction, blockLoad);
                        }

                        File logFileForSingleExcel = excelReader.createLogFile(excelPath);

                        fillUpCurretLocators(currentInstruction);

                        try {
                            if (jumpGoto) {

                                if (jumpGotoError) {
                                    resultActions = "Failed " + resultActions;

                                    success = false;

                                    resultActions = performActions.blockGotoFailed(resultActions);
                                } else {
                                    if (!loopBlockActive.contains(msgInstruction.getKey())) {
                                        loopBlockActive.add(msgInstruction.getKey());
                                        loopBlockLimits.put(
                                                msgInstruction.getKey(), Integer.valueOf(msgInstruction.getValue()));
                                    }
                                    int repeat = mapLoops.get(msgInstruction.getKey()) - 1;
                                    if (repeat > 0) {
                                        mapLoops.put(msgInstruction.getKey(), repeat);
                                        try {

                                            String[] parts =
                                                    msgInstruction.getKey().split(":");
                                            int blockOrderNumber = Integer.parseInt(parts[2]);

                                            currentBlock = blockOrderNumber - 1;
                                            currentInstruction.setExecuted(true);

                                            // Assuming currentInstruction and instructionsExecuted are already defined
                                            if (currentInstruction != null
                                                    && instructionsExecuted.stream()
                                                            .noneMatch(instruction ->
                                                                    instruction.getInstructionOrderNumber()
                                                                            == currentInstruction
                                                                                    .getInstructionOrderNumber())) {
                                                instructionsExecuted.add(currentInstruction);
                                            }

                                            executedSuccess.add(currentInstruction.getId());
                                            success = true;

                                        } catch (Exception ex) {
                                            resultActions = "Failed " + resultActions;

                                            success = false;

                                            resultActions = performActions.blockGotoFailed(resultActions);
                                        }

                                        Pair<String, String> currentPair = new Pair(
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
                                                resultActions);

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

                                if (mapLoops.containsKey(parentFieldLoop)) {

                                    int repeat = mapLoops.get(parentFieldLoop) - 1;
                                    String[] parts = parentFieldLoop.split(":");
                                    if (repeat > 0) {
                                        mapLoops.put(parentFieldLoop, repeat);

                                        ARLogger.getInstance(ARScannedElementPane.class)
                                                .info(String.format(
                                                        "Loop to Parent :\"%s\" - %d Times",
                                                        parts[0] + "-(" + parts[1] + ") " + parts[2],
                                                        mapLoops.get(parentFieldLoop)));

                                        if (refreshLoop) {

                                            String extraLog = performActions.actionResultMessage(
                                                    blockName, new String[] {ARConstants.REFRESH_HOLD}, msgInstruction);

                                            performActions.performOtherActions(
                                                    byPassNotFound,
                                                    currentInstruction,
                                                    new String[] {ARConstants.REFRESH_HOLD});

                                            // Excel Report and Log
                                            performActions.logAndReport(
                                                    currentCondition,
                                                    true,
                                                    true,
                                                    currentInstructionStartTime,
                                                    blockReportName,
                                                    success,
                                                    new String[] {ARConstants.REFRESH_HOLD},
                                                    msgInstruction,
                                                    dataExcel,
                                                    writerReport,
                                                    mainMsg,
                                                    extraLog);

                                            // Refresh For REFRESH_LOOP
                                            extraLog = performActions.actionResultMessage(
                                                    blockName, new String[] {ARConstants.REFRESH_ONLY}, msgInstruction);

                                            performActions.performOtherActions(
                                                    byPassNotFound,
                                                    currentInstruction,
                                                    new String[] {ARConstants.REFRESH_ONLY});

                                            // Excel Report and Log
                                            performActions.logAndReport(
                                                    currentCondition,
                                                    true,
                                                    true,
                                                    currentInstructionStartTime,
                                                    blockReportName,
                                                    success,
                                                    new String[] {ARConstants.REFRESH_ONLY},
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
                                        Pair<String, String> currentPair = new Pair(
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
                                                resultActions);

                                    } else {
                                        mapLoops.put(parentFieldLoop, repeat);
                                    }

                                    jumpLoop = false;
                                    refreshLoop = false;

                                    if (repeat > 0) {
                                        continue instructionLoop;
                                    } else {
                                        ARLogger.getInstance(ARScannedElementPane.class)
                                                .info(String.format(
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

                                resultActions =
                                        "Refresh Current Web Page ->  inside Block :\"" + blockLoad.getName() + "\"";

                                refreshOnly = false;

                            } else if (actions[0].equals(ARConstants.HOLD)
                                    || actions[0].equals(ARConstants.QUIT)
                                    || actions[0].equals(ARConstants.SCREEN)
                                    || actions[0].equals(ARConstants.REFRESH_ONLY)) {

                                performActions.performOtherActions(byPassNotFound, currentInstruction, actions);

                                if (actions[0].equals(ARConstants.QUIT)) {
                                    stopAll = true;
                                    success = true;
                                }

                            } else if (!jumpGotoError
                                    && !jumpLoopError
                                    && !execGetOrSet
                                    && !execCheckValue
                                    && !excelWriteOperation
                                    && !pauseOperation) {

                                // Extract dataFieldName and dataFieldValue using a separate method
                                Pair<String, String> fieldData = performActions.extractFieldData(
                                        dataExcel,
                                        actions,
                                        currentInstruction.getDefaultValue(),
                                        currentInstruction.getCodified());

                                WebElement webElementFound = null;
                                try {
                                    webElementFound =
                                            performActions.searchElement(currentInstruction, this.botJobLoad.getId());
                                } catch (Exception ex) {
                                    extraMsg = "Element not found. Please try rescanning.!";
                                    success = false;
                                }

                                if (webElementFound == null
                                        && currentInstruction.getForceCoordinates() != null
                                        && currentInstruction.getForceCoordinates()) {

                                    Boolean pressEnterAfter = false;
                                    if (actions[0].equals(ARConstants.INSERT) && actions[1].equals(ARConstants.ENTER)) {
                                        pressEnterAfter = true;
                                    }
                                    if (actions[0].equalsIgnoreCase(ARConstants.VISUALIZE)
                                            || actions[0].equalsIgnoreCase(ARConstants.CLICK)
                                            || actions[0].equalsIgnoreCase(ARConstants.INSERT)) {
                                        success = performActions.executeActionsAtCoordinates(
                                                mapSavedLocators.get("coordinates"),
                                                fieldData,
                                                actions[0],
                                                pressEnterAfter);
                                    }
                                }

                                byPassNotFound =
                                        byPassFlagLoop || !currentCondition.equals(ARConstants.ConditionStatus.NONE);

                                if (webElementFound != null && success) {

                                    success = performActions.performWebActions(
                                            byPassNotFound,
                                            mapSavedLocators.get("coordinates"),
                                            fieldData,
                                            currentInstruction,
                                            mapOperators,
                                            webElementFound,
                                            actions);

                                    if (actions[0].equalsIgnoreCase(ARConstants.OUTPUT)) {
                                        fieldName = currentInstruction.getId() + "-" + currentInstruction.getName();
                                        if (mapOperators.containsKey(fieldName)) {
                                            msgInstruction = new Pair(fieldName, mapOperators.get(fieldName));
                                        } else {
                                            msgInstruction = new Pair(fieldName, "TEXT OUTPUT NOT FOUND");
                                        }
                                    }
                                }
                                // Special Cases for Select Responses
                                // It could be Improved the case
                                if (resultActions.contains("Error:") || webElementFound == null || !success) {
                                    resultActions = "Failed " + resultActions;
                                    success = false;
                                } else if (resultActions != null && success) {
                                    currentInstruction.setExecuted(true);
                                    // Assuming currentInstruction and instructionsExecuted are already defined
                                    if (currentInstruction != null
                                            && instructionsExecuted.stream()
                                                    .noneMatch(instruction -> instruction.getInstructionOrderNumber()
                                                            == currentInstruction.getInstructionOrderNumber())) {
                                        instructionsExecuted.add(currentInstruction);
                                    }

                                    executedSuccess.add(currentInstruction.getId());
                                }

                            } else if (execGetOrSet) {
                                // GET && SET Special Operators

                                if (parentField != null && parentId != 0) {
                                    parentField = parentId + "-" + parentField;
                                }
                                // Mandatory for GET_VALUE
                                if (xPathOperation == null && actions[0].equalsIgnoreCase(ARConstants.GET_VALUE)) {
                                    resultActions = performActions.parentIdWrongBlock(
                                            currentInstruction, blockLoad, resultActions, currentCondition);
                                    success = false;
                                } else if (parentField == null) {
                                    resultActions = performActions.parentIdWrongBlock(
                                            currentInstruction, blockLoad, resultActions, currentCondition);
                                    success = false;
                                } else if (!mapOperators.containsKey(parentField) && !getAction) {
                                    resultActions = performActions.getValueIsNotDefined(
                                            currentInstruction, resultActions, currentCondition);

                                    success = false;
                                } else {

                                    resultActions = performActions.performOperatorActions(
                                            byPassNotFound,
                                            currentInstruction,
                                            xPathOperation,
                                            actions[0],
                                            operations,
                                            parentField,
                                            mapOperators);

                                    if (resultActions != null) {
                                        currentInstruction.setExecuted(true);

                                        // Assuming currentInstruction and instructionsExecuted are already
                                        // defined
                                        if (currentInstruction != null
                                                && instructionsExecuted.stream()
                                                        .noneMatch(
                                                                instruction -> instruction.getInstructionOrderNumber()
                                                                        == currentInstruction
                                                                                .getInstructionOrderNumber())) {
                                            instructionsExecuted.add(currentInstruction);
                                        }

                                        executedSuccess.add(currentInstruction.getId());
                                        success = true;
                                    } else {
                                        resultActions = "Failed: " + resultActions;
                                        success = false;
                                    }
                                }

                            } else if (execCheckValue) {
                                // Check Validation Operator

                                if (parentField != null) {
                                    parentField = parentId + "-" + parentField;
                                }

                                if (parentField == null) {
                                    resultActions = performActions.parentIdWrongBlock(
                                            currentInstruction, blockLoad, resultActions, currentCondition);

                                    resultActions = performActions.getValueIsNotDefined(
                                            currentInstruction, resultActions, currentCondition);

                                    success = false;

                                } else if (!mapOperators.containsKey(parentField)) {
                                    resultActions = performActions.getValueIsNotDefined(
                                            currentInstruction, resultActions, currentCondition);

                                    success = false;
                                } else {
                                    //                                    fieldName = parentField;

                                    resultActions = "CHECK_VALUE for (Parent: " + parentField + ")"
                                            + String.join(" ", operations);
                                    boolean isOperationValid = false;
                                    if (operations[1].equalsIgnoreCase("=")) {
                                        isOperationValid = mapOperators
                                                .get(parentField)
                                                .trim()
                                                .equalsIgnoreCase(operations[2]);

                                    } else if (operations[1].equalsIgnoreCase(">")) {
                                        isOperationValid = mapOperators
                                                .get(parentField)
                                                .trim()
                                                .equalsIgnoreCase(operations[2]);
                                    } else if (operations[1].equalsIgnoreCase("!=")) {
                                        isOperationValid = !mapOperators
                                                .get(parentField)
                                                .trim()
                                                .equalsIgnoreCase(operations[2]);
                                    }

                                    if (isOperationValid) {

                                        currentInstruction.setExecuted(true);

                                        // Assuming currentInstruction and instructionsExecuted are already
                                        // defined
                                        if (currentInstruction != null
                                                && instructionsExecuted.stream()
                                                        .noneMatch(
                                                                instruction -> instruction.getInstructionOrderNumber()
                                                                        == currentInstruction
                                                                                .getInstructionOrderNumber())) {
                                            instructionsExecuted.add(currentInstruction);
                                        }

                                        executedSuccess.add(currentInstruction.getId());
                                        success = true;
                                    } else {
                                        resultActions = performActions.checkValidationFailed(
                                                parentField,
                                                mapOperators.get(parentField),
                                                resultActions,
                                                operations,
                                                currentCondition,
                                                byPassNotFound);

                                        success = false;
                                    }
                                }

                            } else if (excelWriteOperation && operations.length == 2) {
                                // Excel Write Operator

                                if (parentField != null) {
                                    fieldName = parentField;
                                    parentField = parentId + "-" + parentField;
                                }

                                if (parentField == null) {

                                    resultActions = performActions.parentIdWrongBlock(
                                            currentInstruction, blockLoad, resultActions, currentCondition);

                                    success = false;

                                } else if (!mapOperators.containsKey(parentField)) {
                                    resultActions = performActions.getValueIsNotDefined(
                                            currentInstruction, resultActions, currentCondition);

                                    success = false;
                                } else {

                                    if (excelExportOnceCreation) {
                                        //
                                        // writerExport.insertReportHead();
                                        excelExportOnceCreation = false;
                                    }

                                    if (!Strings.isNullOrEmpty(excelFieldName)) {
                                        writerExport = new ExcelWriter(
                                                        excelFieldName, performActions.getCurrentDriver(), true)
                                                .withPurpose("export");
                                    }

                                    if (writerExport != null) {

                                        resultActions = "insertValueFieldNameInExcel -> " + parentField + "-"
                                                + mapOperators.get(parentField);
                                    } else {
                                        resultActions = "NO Export Excel File defined -> " + parentField + "-"
                                                + mapOperators.get(parentField);
                                    }

                                    if (mapExport.size() == 0) {
                                        //
                                        // writerExport.insertBlockSeparation(blockLoad.getName());
                                        //                                            exportIndex *= 2;
                                    }

                                    // Insert the updated mapExport into the Excel after each instruction
                                    if (writerExport != null) {
                                        mapExport.put("KEY", "EXTERNAL");
                                        mapExport.put(fieldName, mapOperators.get(parentField));

                                        writerExport.insertFieldNameAndValueLastColumn(mapExport, exportIndex - 1);
                                    }
                                    performActions.onHoldForSeconds(null);

                                    if (resultActions != null) {
                                        currentInstruction.setExecuted(true);

                                        // Assuming currentInstruction and instructionsExecuted are already
                                        // defined
                                        if (currentInstruction != null
                                                && instructionsExecuted.stream()
                                                        .noneMatch(
                                                                instruction -> instruction.getInstructionOrderNumber()
                                                                        == currentInstruction
                                                                                .getInstructionOrderNumber())) {
                                            instructionsExecuted.add(currentInstruction);
                                        }

                                        executedSuccess.add(currentInstruction.getId());
                                        success = true;
                                    } else {
                                        resultActions = "Failed: " + resultActions;
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

                            performMessage.errorMessage(resultActions, msg1, msg2, msg3, null, 260);
                            //                            throw new RuntimeException(t);
                        }

                        printLog(generateTimestamp(), logFileForSingleExcel, resultActions, success);

                        // Here mark the Status of a progress Condition Fail or Success at the end of each Kind
                        // of Execution
                        if (!jumpGotoError
                                && !jumpLoopError
                                && !currentCondition.equals(ARConstants.ConditionStatus.NONE)) {
                            progressCondition = performActions.updateProgressSuccess(success, currentCondition);
                            //                                continue instructionLoop;
                        } else {
                            progressCondition = ARConstants.ConditionStatus.NONE;
                        }

                        // Excel Report and Log
                        performActions.logAndReport(
                                !byPassFlagLoop ? progressCondition : ARConstants.ConditionStatus.BY_PASS,
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
                                resultActions);

                        if (pauseOperation && respModal.equals(ARConstants.DialogModal.STOP)) {

                            String nameInstruc = "(" + currentInstruction.getId() + ") " + currentInstruction.getName();

                            resultActions = String.format("STOP ALL PROCESSES: \"%s\"", nameInstruc);

                            Pair<String, String> msgBlock = new Pair(resultActions, ARConstants.PAUSE);

                            // Excel Report and Log
                            performActions.logAndReport(
                                    currentCondition,
                                    true,
                                    true,
                                    blockStartTime,
                                    blockReportName,
                                    success,
                                    new String[] {ARConstants.PAUSE},
                                    msgBlock,
                                    dataExcel,
                                    writerReport,
                                    "PAUSE -> STOP",
                                    String.format("STOP ALL CALLED AT: \"%s\" : ", nameInstruc));

                            respModal = ARConstants.DialogModal.NONE;
                            stopAll = true;
                            break;
                        }

                        // It decides Here if ByPass as per Loop or Per IF-ELSEIF-ELSE-ENDIF blocks
                        if (!success && !byPassFlagLoop && currentCondition.equals(ARConstants.ConditionStatus.NONE)) {
                            stopAll = true;
                            break;
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
                        if (progressCondition.equals(ARConstants.ConditionStatus.IF_PASSED)
                                || progressCondition.equals(ARConstants.ConditionStatus.ELSEIF_PASSED)) {
                            int jumpPassed = performActions.checkActionToJump(
                                    actions[0], progressCondition, mapConditional, parentBlockCondition, currentIndex);

                            // Any Error
                            if (jumpPassed < 0) {
                                stopAll = true;
                                continue blockLoop;
                            }
                            // Found Next Block
                            if (jumpPassed > 0) {
                                currentIndex = jumpPassed;
                                // reset all Conditional
                                currentCondition = ARConstants.ConditionStatus.NONE;
                                progressCondition = ARConstants.ConditionStatus.NONE;
                                continue instructionLoop;
                            }
                        }

                        // Conditions When Fails to any of then and Look for the next Correct Block
                        if (progressCondition.equals(ARConstants.ConditionStatus.IF_FAILED)
                                || progressCondition.equals(ARConstants.ConditionStatus.ELSEIF_FAILED)) {

                            // Goes to the next ELSEIF IF EXIST (ELSEIF index + 1);
                            int index = performActions.searchMapConditional(
                                    mapConditional,
                                    parentBlockCondition,
                                    ARConstants.ConditionStatus.ELSEIF,
                                    currentIndex,
                                    false);

                            // Goes to the next ELSE IF ELSEIF  DOES NOT EXIST  (ELSE index + 1);
                            if (index < 0) {
                                index = performActions.searchMapConditional(
                                        mapConditional,
                                        parentBlockCondition,
                                        ARConstants.ConditionStatus.ELSE,
                                        currentIndex,
                                        true);
                            }
                            if (index < 0) {
                                stopAll = true;
                                continue blockLoop;
                            }
                            currentIndex = index;
                            currentCondition = ARConstants.ConditionStatus.NONE;
                            progressCondition = ARConstants.ConditionStatus.NONE;
                            continue instructionLoop;

                        } else if (progressCondition.equals(ARConstants.ConditionStatus.ELSE_FAILED)) {
                            // Goes to the ENDIF (ENDIF index + 1);
                            int index = performActions.searchMapConditional(
                                    mapConditional,
                                    parentBlockCondition,
                                    ARConstants.ConditionStatus.ENDIF,
                                    currentIndex,
                                    true);

                            if (index < 0) {
                                stopAll = true;
                                continue blockLoop;
                            }
                            currentIndex = index;
                            currentCondition = ARConstants.ConditionStatus.NONE;
                            progressCondition = ARConstants.ConditionStatus.NONE;
                            continue instructionLoop;
                        }
                    }
                }

                currentBlock++;
            }

        } else { //  if dataExel is NULL
            // Creating Dynamic Data if Default is Null
            Pair<String, String> dataDynamic = null;
            for (int j = 0; success && j < blocksLoaded.size(); j++) {

                // Call the method to get the filtered list
                List<InstructionLoadDTO> unexecutedInstructions = getUnexecutedInstructions(
                        instructionsExecuted, blocksLoaded.get(j).getInstructionLoadDTOS());

                for (InstructionLoadDTO currentInstruction : unexecutedInstructions) {
                    if (currentInstruction.getDefaultValue() == null) {
                        String[] arr = UtilsMethods.splitIfContains(
                                currentInstruction.getActions(), ARConstants.ACTION_SPECIFICATIONS_SPLITTER);
                        if (arr.length > 1) {
                            String dataFieldName = arr[1].split(ARConstants.PATH_FIELD_SUBSTITUTION)[0];
                            performActions.insertRandomName(dataFieldName);
                        }
                    }
                }
            }
            for (int j = 0; success && j < blocksLoaded.size(); j++) {

                String blockName = blocksLoaded.get(j).getName();
                int blockOrder = blocksLoaded.get(j).getBlockOrderNumber();
                String blockReportName = "#" + blockOrder + " " + blockName;

                // Call the method to get the filtered list
                List<InstructionLoadDTO> unexecutedInstructions = getUnexecutedInstructions(
                        instructionsExecuted, blocksLoaded.get(j).getInstructionLoadDTOS());

                for (InstructionLoadDTO currentInstruction : unexecutedInstructions) {

                    long currentInstructionStartTime = System.nanoTime();
                    File logFileForSingleExcel = excelReader.createLogFile(excelPath);

                    String[] actions = currentInstruction.getActions().split(ARConstants.ACTIONS_AND_PATHS_SPLITTER);

                    // Case for Inputs
                    String valueInsert = "No Data Found";
                    if (actions[0].equals(ARConstants.INSERT) && actions[1].equals(ARConstants.ENTER)) {
                        String reference = actions[2];
                        valueInsert = dataExcel.get(reference);
                    } else if (actions[0].equals(ARConstants.INSERT)) {
                        String reference = actions[1];
                        valueInsert = dataExcel.get(reference);
                    }

                    Pair<String, String> msgInstruction = new Pair(
                            currentInstruction.getName(),
                            (currentInstruction.getOperation() != null
                                    ? currentInstruction.getOperation()
                                    : (actions[0].equalsIgnoreCase(ARConstants.INSERT)) ? valueInsert : ""));

                    resultActions = performActions.actionResultMessage(blockName, actions, msgInstruction);

                    try {

                        if (actions[0].equals(ARConstants.HOLD)
                                || actions[0].equals(ARConstants.QUIT)
                                || actions[0].equals(ARConstants.SCREEN)
                                || actions[0].equals(ARConstants.REFRESH_ONLY)) {
                            performActions.performOtherActions(byPassNotFound, currentInstruction, actions);

                            if (actions[0].equals(ARConstants.QUIT)) {
                                stopAll = true;
                                success = true;
                            }

                            // Excel Report and Log
                            performActions.logAndReport(
                                    currentCondition,
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
                                    resultActions);

                            continue;
                        }

                        WebElement webElementFound = null;
                        try {
                            webElementFound = performActions.searchElement(currentInstruction, this.botJobLoad.getId());
                        } catch (Exception ex) {
                            extraMsg = "Element not found. Please try rescanning.!";
                        }

                        success = performActions.performWebActions(
                                byPassNotFound,
                                mapSavedLocators.get("coordinates"),
                                dataDynamic,
                                currentInstruction,
                                mapOperators,
                                webElementFound,
                                actions);

                        // Special Cases for Select Responses
                        // It could be Improved the case
                        if (resultActions.contains("Error:")) {
                            success = false;
                        } else if (resultActions != null) {
                            currentInstruction.setExecuted(true);
                            success = true;
                        } else {
                            resultActions = "Failed to Execute -> " + currentInstruction.getName();
                            success = false;
                        }

                        // Excel Report and Log
                        performActions.logAndReport(
                                currentCondition,
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
                                resultActions);

                    } catch (Throwable t) {
                        success = false;
                        currentInstruction.setExecuted(false);

                        // Excel Report and Log
                        performActions.logAndReport(
                                currentCondition,
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
                                resultActions);

                        //                        throw new RuntimeException(t);
                    }
                    printLog(generateTimestamp(), logFileForSingleExcel, resultActions, success);
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

        Text variableText1Styled = new Text("Bot-Job Finished - successfully");
        variableText1Styled.setStyle("-fx-font-size: 18px; -fx-fill: blue;");

        Text variableText2Styled = new Text(botJobName);
        variableText2Styled.setStyle("-fx-font-size: 18px; -fx-fill: blue;");

        Text variableText3Styled = new Text("Last Execution:");
        variableText3Styled.setStyle("-fx-font-size: 18px; -fx-fill: blue;");

        Text variableText4Styled = new Text(resultActions);
        variableText4Styled.setStyle("-fx-font-size: 18px; -fx-fill: green;");

        VBox combinedTextContainer = new VBox();
        combinedTextContainer.setSpacing(5); // Add some sp

        if (success) {
            baseLogString = blocksLoaded.get(0).getName()
                    + ARConstants.FIELDS_SEPARATOR
                    + labelsValue.getProperty(Labels.END)
                    + ARConstants.FIELDS_SEPARATOR
                    + labelsValue.getProperty(Labels.OK);

            combinedTextContainer
                    .getChildren()
                    .addAll(variableText1Styled, variableText2Styled, variableText3Styled, variableText4Styled);

            performMessage.showAlertCombinedVBOX(
                    Alert.AlertType.INFORMATION, "Success", "Execution Finished", null, combinedTextContainer);

        } else {
            countdownTextField.setStyle("-fx-font-size: 12px; -fx-text-fill: red;");
            countdownTextField.setText(resultActions);
            baseLogString = blocksLoaded.get(0).getName()
                    + ARConstants.FIELDS_SEPARATOR
                    + labelsValue.getProperty(Labels.END)
                    + ARConstants.FIELDS_SEPARATOR
                    + labelsValue.getProperty(Labels.KO)
                    + ARConstants.FIELDS_SEPARATOR
                    + resultActions;

            performMessage.errorMessage(
                    "Failed to locate the element after 10 attempts.",
                    "Try to use \"Force Coordinates\".",
                    "Last Execution:",
                    resultActions,
                    null,
                    300);
        }
        printBaseLog(baseLogFile, generateTimestamp(), baseLogString);
        return true;
    }

    private void clearFields() {
        allAttributesTextField.setText("");
        currentXPathTextField.setText("");
        iFrameXPath = "";
        iFrameElements = null;
        coordsTextField.setText("");
        customXPathTextField.setText("");
        countdownTextField.setText("Pre-Launch status: Ready");
        countdownTextField.setStyle("-fx-font-size: 12px; -fx-text-fill: blue;");
        //        textFlowResult.getChildren().clear();
        //        textFlowResult.getChildren().addAll(countdownTextField);
        //        textFlowResult.requestLayout();
        contentPane.requestLayout();
    }

    private void fillUpCurretLocators(InstructionLoadDTO currentInstruction) {
        for (InstructionReferenceLoadDTO reference : currentInstruction.getInstructionReferenceLoadDTOList()) {
            switch (reference.getReferenceType()) {
                case "allAttributes":
                    allAttributesTextField.setText(reference.getValue());
                    break;
                case "currentXPath":
                    currentXPathTextField.setText(reference.getValue());
                    break;
                case "coords":
                    coordsTextField.setText(reference.getValue());
                    break;
                case "customXPath":
                    customXPathTextField.setText(reference.getValue());
                    break;
                default:
                    ARLogger.getInstance(ARScannedElementPane.class)
                            .fine("Unknown reference type: " + reference.getReferenceType());
            }
        }
    }

    public static List<InstructionLoadDTO> getUnexecutedInstructions(
            List<InstructionLoadDTO> instructionsExecuted, List<InstructionLoadDTO> otherList) {
        // Create a set of instructionOrderNumbers from instructionsExecuted
        Set<Integer> executedInstructionOrderNumbers = instructionsExecuted.stream()
                .map(InstructionLoadDTO::getInstructionOrderNumber)
                .collect(Collectors.toSet());

        // Filter the otherList to get instructions where executed is false and not in executedInstructionOrderNumbers
        return otherList.stream()
                //                .filter(instruction -> instruction.getExecuted() != null &&
                // !instruction.getExecuted())
                .filter(instruction ->
                        !executedInstructionOrderNumbers.contains(instruction.getInstructionOrderNumber()))
                .collect(Collectors.toList());
    }

    private static void printBaseLog(File logFile, String timeStamp, String msg) {
        String resultMsg;
        String log = String.join(ARConstants.FIELDS_SEPARATOR, timeStamp, msg);

        try {
            FileWriter fileWriter = new FileWriter(logFile, true);
            fileWriter.write(log + System.lineSeparator());
            fileWriter.close();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    private static String generateTimestamp() {
        Date date = new Date();
        dateFormatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        return dateFormatter.format(date);
    }

    private static void printLog(String timeStamp, File logFile, String resultActions, boolean result) {
        String resultMsg = result ? ARConstants.SUCCESS : ARConstants.FAIL;
        String log = String.join(ARConstants.FIELDS_SEPARATOR, timeStamp, resultMsg, resultActions);

        try {
            FileWriter fileWriter = new FileWriter(logFile, true);
            fileWriter.write(log + System.lineSeparator());
            fileWriter.close();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    private void executeAlert(InstructionLoadDTO instruction) {
        // Execute the countdown in a separate thread
        if (instruction != null) {
            Integer instructionSeconds = instruction.getOnHoldSeconds();
            for (int x = 1; x < instructionSeconds; x++) {
                ProgressBar progress = new ProgressBar();
                bottomPaneTime.getChildren().add(progress);
            }
        }
    }

    private String insertValueFieldNameInExcel(
            String parentId, String innerHTMLValue, String action, String botJobName) {
        //        String innerHTMLValue = element.getAttribute(WebElementAttributeEnum.INNER_HTML.getValue());

        if (innerHTMLValue.contains("<div")) {
            int lastIndexOfDiv = innerHTMLValue.lastIndexOf("<div");
            innerHTMLValue = innerHTMLValue.substring(lastIndexOfDiv + 1);
            int firstIndexOfOpenTag = innerHTMLValue.indexOf("<");
            int firstIndexOfCloseTag = innerHTMLValue.indexOf(">");
            innerHTMLValue = innerHTMLValue.substring(firstIndexOfCloseTag + 1, firstIndexOfOpenTag);
        }
        String fieldName = null;
        String[] arr = UtilsMethods.splitIfContains(action, ARConstants.ACTION_SPECIFICATIONS_SPLITTER);
        if (arr.length > 1) {
            fieldName = arr[1].split(ARConstants.PATH_FIELD_SUBSTITUTION)[0];
        }

        new ExcelWriter(botJobName, performActions.getCurrentDriver(), false)
                .withPurpose("excel")
                .insertValueFieldName(parentId + "-" + fieldName, innerHTMLValue);
        return action + " fieldName " + fieldName;
    }

    public void quit(int status) {
        performActions.getCurrentDriver().quit();
        if (status == 0) {
            System.exit(status);
        }
        Close();
    }

    public List<ARWebElement> createAdvancedARElement(
            Map<String, WebElement> mapAdvanced, String attributeName, WebElementTagNameEnum typeElement) {
        List<ARWebElement> listARElements = new ArrayList<>();

        if (attributeName == null) {
            attributeName = "id";
        }
        if (!mapAdvanced.isEmpty()) {
            ARLogger.getInstance(ARScannedElementPane.class)
                    .fine(String.format("Advance Search Element with total of %s elements", mapAdvanced.size()));

            for (Map.Entry<String, WebElement> entry : mapAdvanced.entrySet()) {
                WebElement entryElem = entry.getValue();
                String xPath = entry.getKey();
                String attributeValue = entryElem.getAttribute(attributeName);

                if (Strings.isNullOrEmpty(attributeValue)) {
                    attributeValue = "(" + attributeName + ") has no value";
                }
                System.out.println("AR Element Creation ->  Tag: " + entryElem.getTagName() + ", " + attributeName
                        + ": " + attributeValue + ", XPath: " + xPath);

                try {

                    if (listARElements.size() < 30) {
                        addProgressBar(1);
                    }

                    ElementDTO elementDTO = new ElementDTO();

                    TargetElement targetLocal = performActions.defineSearchReturn(elementDTO, entryElem, null);

                    targetLocal = performActions.defineTargetNameTitles(targetLocal);

                    targetLocal.setElement(entryElem);

                    if (targetLocal.getDefinedName() != null) {
                        ARWebElement arWebElement = new ARWebElement(targetLocal, botJobLoad.getId());
                        if (arWebElement != null) {
                            listARElements.add(arWebElement);
                        }
                    }
                } catch (EnumConstantNotPresentException ex) {
                    throw ex;
                } catch (Exception ex) {
                    ARLogger.getInstance(ARScannedElementPane.class)
                            .fine(String.format(
                                    "Error attempt to create Advance Element  attribute: %s xPath: %s\nError: %s",
                                    attributeValue, xPath, ex.getMessage()));
                }
            }
        } else {
            // Add progress bars
            for (int none = 0; none < 20; none++) {
                addProgressBar(1);
            }

            new Thread(() -> {
                        try {
                            // Sleep for 3 seconds
                            Thread.sleep(3000);

                            Platform.runLater(() -> {
                                try {
                                    Thread.sleep(2000);
                                    bottomPane.getChildren().clear();
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }

                                //                                scannedElements1.requestLayout();
                                //                                scannedElements1.refresh();
                                //                                scannedElements2.requestLayout();
                                //                                scannedElements2.refresh();
                                bottomPane.requestLayout();
                            });

                        } catch (InterruptedException e) {
                            System.out.println(e.getMessage()); // Handle interruption
                        }
                    })
                    .start();
        }
        return listARElements;
    }

    /**
     * Finds all elements with the specified attribute and returns a map with their XPaths as keys.
     *
     * @param driver the WebDriver instance
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

    /**
     * Finds all elements of the specified tag name without "id" or "name" attributes and returns a map with their XPaths as keys.
     *
     * @param driver the WebDriver instance
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
     * Finds all elements of the specified tag name without "id" or "name" attributes and returns a map with their XPaths as keys.
     *
     * @param driver the WebDriver instance
     * @return a map where keys are XPaths of elements and values are WebElements
     */
    private static Map<String, WebElement> findElementsOutputCriteria(WebDriver driver) {

        String allWithText = "// Global array to store XPaths of elements with text\n" + "let elementsWithText = [];\n"
                + "(function() {\n"
                + "    function getXPath(element) {\n"
                + "        if (element.id) {\n"
                + "            return `//*[@id='${element.id}']`;\n"
                + "        }\n"
                + "        if (element === document.body) {\n"
                + "            return '/html/body';\n"
                + "        }\n"
                + "        let index = 1;\n"
                + "        let siblings = element.parentNode ? element.parentNode.children : [];\n"
                + "        for (let i = 0; i < siblings.length; i++) {\n"
                + "            if (siblings[i] === element) {\n"
                + "                return getXPath(element.parentNode) + '/' + element.tagName.toLowerCase() + `[${index}]`;\n"
                + "            }\n"
                + "            if (siblings[i].tagName === element.tagName) {\n"
                + "                index++;\n"
                + "            }\n"
                + "        }\n"
                + "        return '';\n"
                + "    }\n"
                + "\n"
                + "    function collectElementsWithText() {\n"
                + "        let elements = document.querySelectorAll('*');\n"
                + "\n"
                + "        elements.forEach(element => {\n"
                + "            let text = element.textContent.trim();\n"
                + "            if (text.length > 0 && element.offsetWidth > 0 && element.offsetHeight > 0) {\n"
                + "                let xpath = getXPath(element);\n"
                + "                if (xpath) {\n"
                + "                    elementsWithText.push(xpath);\n"
                + "                }\n"
                + "            }\n"
                + "        });\n"
                + "        window.allWithText = elementsWithText;\n"
                + "    }\n"
                + "\n"
                + "    window.allWithText = [];\n"
                + "    collectElementsWithText();\n"
                + "})();\n";

        List<WebElement> elements = new ArrayList<>();
        Map<String, WebElement> elementMap = new HashMap<>();

        try {
            jsExecutor = (JavascriptExecutor) driver;
            jsExecutor.executeScript(allWithText);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        try {
            Thread.sleep(2);
        } catch (InterruptedException e) {
        }

        String[] listXPaths = new String[0];

        LinkedHashMap<String, Object> linkedHashMap = (LinkedHashMap<String, Object>)
                jsExecutor.executeScript("var obj = { allWithText: window.allWithText }; return obj;");

        // Convert the LinkedHashMap to a Java Map (if necessary)
        Map<String, Object> resultMap = new LinkedHashMap<>(linkedHashMap);

        if (linkedHashMap != null) {
            //            Platform.runLater(() -> {
            //                                iFrameXPath = (String) resultMap.get("iFrameXPath");

            Object iframeElementsObject = resultMap.get("allWithText");

            if (iframeElementsObject instanceof List<?>) {
                // Convert List to String[]
                List<?> iframeElementsList = (List<?>) iframeElementsObject;
                lstAllPaths = iframeElementsList.toArray(new String[0]);
            } else if (iframeElementsObject instanceof Object[]) {
                // If it's an array, check if it's an array of Strings
                lstAllPaths = Arrays.copyOf(
                        (Object[]) iframeElementsObject, ((Object[]) iframeElementsObject).length, String[].class);
            } else {
                System.out.println("The iframeElements data is not a List or an array.");
            }

            for (String xPath : lstAllPaths) {
                WebElement element = driver.findElement(By.xpath(xPath));
                if (element != null) {
                    elementMap.put(xPath, element);
                }
            }
            //            });
        }

        //        List<WebElement> elements = driver.findElements(By.xpath("//label[@for]"));
        //        Set<WebElement> uniqueElements = new HashSet<>(elements);
        //
        //        elements = driver.findElements(By.xpath("//label[not(@for)]"));
        //        uniqueElements.addAll(elements);
        //
        //        elements = driver.findElements(By.xpath("//label[normalize-space(text()) != '']"));
        //        uniqueElements.addAll(elements);
        //
        //        elements = driver.findElements(By.xpath("//div[normalize-space(text()) != '']"));
        //        uniqueElements.addAll(elements);
        //
        //        elements = driver.findElements(By.xpath("//span[normalize-space(text()) != '']"));
        //        uniqueElements.addAll(elements);
        //
        //        elements = driver.findElements(By.xpath("//div[@for]"));
        //        uniqueElements.addAll(elements);
        //
        //        elements = driver.findElements(By.xpath("//div[not(@for)]"));
        //        uniqueElements.addAll(elements);
        //
        //        elements = driver.findElements(By.xpath("//span[@for]"));
        //        uniqueElements.addAll(elements);
        //
        //        elements = driver.findElements(By.xpath("//span[not(@for)]"));
        //        uniqueElements.addAll(elements);
        //
        //        elements = driver.findElements(By.xpath("//label[@title != '' or @aria-label != '']"));
        //        uniqueElements.addAll(elements);

        //        Map<String, WebElement> elementMap = new HashMap<>();
        //        for (WebElement element : elements) {
        //            String xpath = getElementXPath(driver, element);
        //            elementMap.put(xpath, element);
        //        }
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

    @Override
    public void start(Stage stage) throws Exception {
        ARLogger.getInstance(ARWebDriver.class).severe("start from ARScannedElementPane");
    }

    @Override
    public void stop() throws Exception {
        // Cleanup tasks when the application stops
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.err.println("ExecutorService did not terminate");
                    ARLogger.getInstance(ARWebDriver.class).severe("ExecutorService did not terminate");
                }
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void Close() {
        Platform.runLater(() -> {
            Stage stage = (Stage) contentPane.getScene().getWindow();
            stage.close();
        });
    }

    //    private int createBlock(BlockDTO blockDTO) {
    //        // Generate a Unique-ID for the block
    //        Integer nextId = loadNextIdBlockData() + 1;
    //        Integer nextBlockOrder =
    //                performDataBase.loadNextBlockOrderNumber(blockDTO.getBotJobDTO().getId()) + 1;
    //
    //        // Build the SQL insert query
    //        String insertSQL = "INSERT INTO block(id, block_order_number, description, name, type_id, bot_job_id)
    // VALUES ("
    //                + nextId + ", "
    //                + nextBlockOrder + ", " // block_order_number
    //                + "'" + blockDTO.getDescription() + "', " // description
    //                + "'" + blockDTO.getName() + "', " // name
    //                + 1 + ", " // type_id
    //                + blockDTO.getBotJobDTO().getId() + ")"; // bot_job_id, assuming BotJobDTO has an ID
    //
    //        try (Statement stmt = ARSharedResources.getInstance().getConnection().createStatement()) {
    //            stmt.executeUpdate(insertSQL);
    //            ARLogger.getInstance(ARScannedElementPane.class).info("Block data saved successfully id: " +
    // nextId);
    //            return nextId;
    //        } catch (SQLException e) {
    //            ARLogger.getInstance(ARScannedElementPane.class).severe("saveBlock  \nError: " + e.getMessage());
    //            return -1;
    //        }
    //    }

    private Integer loadNextIdBlockData() {
        //        String selectSQL = "SELECT NEXT_VAL fROM homeBankingSeq";
        String selectSQL = "SELECT MAX(ID) AS max_id FROM block";
        try (Statement stmt = ARSharedResources.getInstance().getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(selectSQL)) {
            while (rs.next()) {
                return rs.getInt("max_id");
            }
        } catch (SQLException e) {
            ARLogger.getInstance(ARScannedElementPane.class).severe("loadNextIdBlockData  \nError: " + e.getMessage());
        }
        return null;
    }

    //    public void setBlockJob(BlockDTO blockLoadDTO) {
    //        this.blockLoad = blockLoadDTO;
    //    }

    private static void showAlertInfo(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void showAlert(Alert.AlertType alertType, String title, String header, String content) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private int preFillAddInstruction(
            String name,
            String description,
            String actions,
            String operation,
            Integer onHold,
            Integer varId,
            Integer instructionOrderNumber,
            boolean exportToAR,
            String xPath,
            String coordinates,
            boolean forceCoordinates,
            String iFrameXPath,
            Integer currentBotJobId,
            Integer currentBlockId) {

        InstructionLoadDTO InstructionLoadDTO = new InstructionLoadDTO();

        InstructionLoadDTO.setXpath(xPath);
        InstructionLoadDTO.setCoordinates(coordinates);
        InstructionLoadDTO.setForceCoordinates(forceCoordinates);
        InstructionLoadDTO.setIFrameXPath(iFrameXPath);
        InstructionLoadDTO.setName(name);

        InstructionLoadDTO.setCodified(false);

        InstructionLoadDTO.setInstructionOrderNumber(instructionOrderNumber);

        InstructionLoadDTO.setOptional(false);

        //        InstructionLoadDTO.setOperation(operation);
        InstructionLoadDTO.setActions(actions);
        InstructionLoadDTO.setDescription(description);

        InstructionLoadDTO.setVariableId(varId);

        InstructionLoadDTO.setActionCustomMaxWaitSec(30);
        InstructionLoadDTO.setOnHoldSeconds(onHold);
        //        InstructionLoadDTO.setBlock(savedBlockDTO);
        InstructionLoadDTO.setExportToABR(exportToAR);
        InstructionLoadDTO.setInstructionActive(true);

        // Wrap the persistence in a try-catch block
        int newId = -1;

        try {
            newId = performDataBase.insertInstruction(InstructionLoadDTO, currentBotJobId, currentBlockId);

        } catch (Exception e) {

            ARLogger.getInstance(ARScannedElementPane.class)
                    .severe(String.format(
                            "Cannot Insert \"Instruction\"  \"%s\"\nCannot be saved!\nError: %s",
                            InstructionLoadDTO.getName(), e.getMessage()));

            return -1;
        }
        return newId;
    }

    private void loadAllBlockItems(List<BlockLoadDTO> blockLoadDTOList) {
        blocksItems.clear();
        if (blockLoadDTOList.size() > 0) {
            blocksItems.add(new ComboBoxVars("Execute All Blocks", "", -1, -1));
        } else {
            blocksItems.add(new ComboBoxVars("#1 Default Block", "Default Block", 1, 1));
        }
        for (BlockLoadDTO block : blockLoadDTOList) {
            blocksItems.add(new ComboBoxVars(
                    block.getBlockOrderNumber() + "# " + block.getName(),
                    block.getName(),
                    block.getBlockOrderNumber(),
                    block.getId()));
        }
    }

    private Button createPathButton() {
        Button button = componentBuilder.buildButton(
                "", ARConstants.SPACE_L, ARConstants.ICON_REFRESH, ARConstants.SPACE_M, new Insets(3D));
        button.setMaxWidth(ARConstants.SPACE_L);
        AnchorPane.setRightAnchor(button, 0D);
        return button;
    }

    //    private void broadcastMessageToAll(String message) {
    //        synchronized (sessions) {
    //            for (Session session : sessions) {
    //                if (session.isOpen()) {
    //                    sendMessageJson(session, "data_updated", message);
    //                }
    //            }
    //        }
    //    }

    private void broadcastMessageToAll(String message) {
        activeSessions = SimpleWebSocketServer.getAllSessions();

        for (Session session : activeSessions.values()) { // Looping correctly
            if (session.isOpen()) {
                sendMessageJson(session, message, null);
            }
        }
    }

    public static void sendMessageJson(int homeBankingId, String sessionId, String msg1, String msg2) {
        activeSessions = SimpleWebSocketServer.getAllSessions();
        Session session = activeSessions.get(sessionId);

        if (session != null && session.isOpen()) {
            try {
                JsonObject jsonMessage = new JsonObject();
                jsonMessage.addProperty("body", msg1);
                jsonMessage.addProperty("homeBankingId", homeBankingId);
                jsonMessage.addProperty("sessionId", sessionId);
                if (msg2 != null && !msg2.isEmpty()) {
                    jsonMessage.addProperty("operationId", msg2);
                }
                session.getBasicRemote().sendText(jsonMessage.toString());
            } catch (IOException e) {
                System.err.println("Error sending message to session " + sessionId + ": " + e.getMessage());
            }
        } else {
            System.err.println("Session " + sessionId + " not found or closed.");
        }
    }

    private void sendMessageJson(Session session, String msg1, String msg2) {
        try {
            // Create a JSON object with the key "body" and the provided message
            JsonObject jsonMessage = new JsonObject();
            jsonMessage.addProperty("body", msg1);
            if (!Strings.isNullOrEmpty(msg2)) {
                jsonMessage.addProperty("footer", msg2);
            }
            // Convert the JSON object to a string
            String jsonString = jsonMessage.toString();

            // Send the JSON string over WebSocket
            session.getBasicRemote().sendText(jsonString);
        } catch (IOException e) {
            System.err.println("Error sending message to session " + session.getId() + ": " + e.getMessage());
        }
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

    private void browserNotAttached() {
        Text variableText1Styled = new Text("The Browser attached with this Web Scanner is Not Active");
        variableText1Styled.setStyle("-fx-font-size: 18px; -fx-fill: blue;");

        Text variableText2Styled = new Text("Close and Re-open this Scanner Screen");
        variableText2Styled.setStyle("-fx-font-size: 18px; -fx-fill: red;");

        VBox combinedTextContainer = new VBox();
        combinedTextContainer.setSpacing(5); // Add some sp

        combinedTextContainer.getChildren().addAll(variableText1Styled, variableText2Styled);

        performMessage.showAlertCombinedVBOX(
                Alert.AlertType.WARNING, "Missing Web Browser", "Browser Not Active!", null, combinedTextContainer);
    }
}

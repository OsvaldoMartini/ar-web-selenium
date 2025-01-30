package com.allinweb.ch.component.pane;

import com.allinweb.ch.builder.WebElementAttributeEnum;
import com.allinweb.ch.builder.WebElementAttributeTypeValueEnum;
import com.allinweb.ch.builder.WebElementTagNameEnum;
import com.allinweb.ch.component.listCell.ABRCellFactory;
import com.allinweb.ch.component.listCell.ABRWebElementListCell;
import com.allinweb.ch.component.model.BlockDetailsDTO;
import com.allinweb.ch.component.model.BlockLoadDTO;
import com.allinweb.ch.component.model.BlockLoopInstructionLoadDTO;
import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.model.InstructionReferenceLoadDTO;
import com.allinweb.ch.component.pane.base.ABRPane;
import com.allinweb.ch.component.scene.ABRNewHomeBankingScene;
import com.allinweb.ch.control.ABRComponentBuilder;
import com.allinweb.ch.core.ABRSharedResources;
import com.allinweb.ch.driver.ABRWebDriver;
import com.allinweb.ch.driver.ABRWebElement;
import com.allinweb.ch.facade.IframeInputLocator;
import com.allinweb.ch.facade.PerformActions;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.persistence.*;
import com.allinweb.ch.readersAndWriters.ExcelReader;
import com.allinweb.ch.readersAndWriters.ExcelWriter;
import com.allinweb.ch.util.*;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
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
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Pair;
import javax.net.ssl.*;
import javax.websocket.Session;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.*;
import org.openqa.selenium.remote.RemoteWebElement;
import org.openqa.selenium.support.pagefactory.ByChained;
import org.openqa.selenium.support.ui.WebDriverWait;

public class ABRScannedElementPane extends ABRPane {

    //    private Stage compStage;
    private final Gson gson = new Gson();
    private Set<Session> sessions;

    private int currentTabIndex = 0; // Track the currently active tab index
    public ABRWebDriver abrWebDriver;
    private Set<String> windowHandles;

    private ExecutorService executorService;
    private static final int SECONDS = 3; // Total seconds for the countdown
    private int remainingSeconds = SECONDS;
    private Timeline timeline;
    private Alert alertToShow;
    public static Repository repository;

    private static SearchReturn searchReturn = new SearchReturn();

    private static File baseLogFile = null;
    private static SimpleDateFormat dateFormatter;

    private static JavascriptExecutor jsExecutor;

    private final ABRComponentBuilder componentBuilder = new ABRComponentBuilder();

    private DatabaseUserDTO databaseUserDto;

    private BotJobDTO botJob;
    private BlockDTO blockJob;
    private int currentBlockId;

    double comboWidth = 200;

    private List<BlockLoadDTO> blockLoadList = new ArrayList<>();
    private List<BotJobLoadDTO> botJobLoadList = new ArrayList<>();

    private ComboBox<ComboBoxVars> comboBoxBlocks;
    private ObservableList<ComboBoxVars> blocksItems = FXCollections.observableArrayList();

    Button refreshBlocksButton;

    // UI COMPONENTS
    private HBox topPane;
    private HBox bottomPane;
    private HBox bottomPaneTime;
    private AnchorPane contentPane;
    private ObservableList<ABRWebElement> webElementObservableList1;
    private ObservableList<ABRWebElement> webElementObservableList2;
    private ObservableList<ABRWebElement> webElementObservableList3;
    private ListView<ABRWebElement> scannedElements1;
    private ListView<ABRWebElement> scannedElements2;
    private ListView<ABRWebElement> scannedElements3;
    private Button scanButton;
    private Button addNewElement;
    private Button configureButton;
    private Button launchBotJobButton;
    private Button recallJobButton;
    private Button searchWithIdsButton;
    private Button searchWithNamesButton;
    private Button searchWithoutIdsAndNamesBtn;
    private Button refreshInputFieldsButton;
    private Button refreshOutputFieldsButton;
    private Button refreshOtherFieldsButton;
    private Button magicFieldsButton;
    private Button leftButton;
    private Button rightButton;
    private Button cleanListButton;

    private CheckBox checkTestAction;
    private CheckBox checkJavaScript;
    private CheckBox checkCoordinates;
    private CheckBox checkActiveHover;
    private CheckBox checkClickElement;
    private CheckBox checkInputText;
    private CheckBox checkOutputText;
    private CheckBox checkFrameText;

    private Label defineNameLabel;
    private Label attribIdTextFieldLabel;
    private Label attribNameTextFieldLabel;
    private Label currentXPathLabel;
    private Label currentAbsoluteXPathLabel;
    private Label customXPathLabel;
    private Label originalTagNameLabel;
    private Label coordsTextFieldLabel;

    private Text currentURL;
    private TextField defineNameField;
    private TextField testActionsField;
    private TextArea countdownTextField;
    private TextField attribIdTextField;
    private TextField attribNameTextField;
    private TextField currentXPathTextField;
    private TextField absolutXPathTextField;
    private TextField customXPathTextField;
    private TextField originalTagNameField;
    private TextField coordsTextField;
    private String xpathTextPrevious = "";
    private Boolean periodicActivated = false;

    private Boolean idAttributeFirst = false;
    private Boolean nameAttributeFirst = false;
    private Boolean withoutNameAndId = false;

    private Map<String, String> mapOperators;
    private Map<String, String> mapExport;

    private String iFrameXPath;
    private String[] iFrameElements;

    List<BlockLoopInstructionLoadDTO> instructionsExecuted = new ArrayList<>();
    List<Integer> executedSuccess = new ArrayList<>();
    Map<String, WebElement> mapAdvanced = new HashMap<>();

    // Very important sequence on initiation
    private static int reduceSearchCriteria;
    private static ABRPropertyManager managerProps;
    private static ABRPriorities abrPriorities;
    private static final PerformMessage performMessage;
    private static final PerformActions performAction;
    private static final PerformDataBase performDataBase;
    private static final ABRNewHomeBankingScene abrNewHomeBankingScene;
    private static final IframeInputLocator iframeInputLocator;

    // Static block to initialize
    static {
        iframeInputLocator = IframeInputLocator.getInstance();
        performMessage = PerformMessage.getInstance();
        performDataBase = PerformDataBase.getInstance();
        performAction = PerformActions.getInstance();
        abrPriorities = ABRPriorities.getInstance();
        managerProps = ABRPropertyManager.getInstance();
        abrNewHomeBankingScene = ABRNewHomeBankingScene.getInstance();
    }

    public ABRWebDriver getAbrWebDriver() {
        return abrWebDriver;
    }

    public ABRScannedElementPane(
            BotJobDTO botJob, BlockDTO blockJob, ABRWebDriver abrWebDriver, Set<Session> sessions) {
        this.sessions = sessions;

        ABRLogger.getInstance(ABRWebDriver.class).fine("Calling ABRScannedElementPane");

        // Ensure botJob and abrPriorities are not null before accessing their methods
        if (botJob != null && abrPriorities != null) {
            // Check if we need to update abrPriorities
            if (abrPriorities.getJobId() == null || !abrPriorities.getJobId().equals(botJob.getId())) {
                // Set Job ID in abrPriorities
                abrPriorities.setJobId(botJob.getId());

                // Check for non-null HomeBanking and Priority
                if (botJob.getHomeBanking() != null) {
                    String priorityValue = botJob.getHomeBanking().getPriority();
                    String searchConfig = botJob.getHomeBanking().getSearchConfig();

                    if (priorityValue != null) {
                        abrPriorities.loadPrioritiesFromString(priorityValue);
                    } else {
                        abrPriorities.loadPriorities();
                    }

                    abrPriorities.loadSearchElementsConfig(searchConfig);
                }

                // Initialize performAction with abrPriorities and abrWebDriver
                performAction.initializePerformActions(abrPriorities, abrWebDriver);
            }
        }

        // Assign instance variables
        this.botJob = botJob;
        this.blockJob = blockJob;
        this.abrWebDriver = abrWebDriver;
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

        //        if (abrWebDriver.getDriver() == null) {
        //            abrWebDriver = new ABRWebDriver(); // Initialize WebDriver
        //        }
        abrWebDriver.openDriver(
                botJob.getHomeBanking().getUrl(),
                botJob.getHomeBanking().getOptionsConfig().toString());

        performAction.getIframeElementsMap();

        handleWindowHandlesChange();

        topPane = componentBuilder.createTopPanel(ABRConstants.SPACE_L, ABRConstants.SPACE_SM);
        bottomPane = componentBuilder.createBottomPanel(ABRConstants.SPACE_L, ABRConstants.SPACE_SM);
        bottomPaneTime = componentBuilder.createBottomPanel(ABRConstants.SPACE_L, ABRConstants.SPACE_SM);
        contentPane =
                componentBuilder.createContentPanel(ABRConstants.SPACE_L, ABRConstants.SPACE_XL, ABRConstants.SPACE_SM);

        scanButton = componentBuilder.buildButton(
                "Scan", ABRConstants.SPACE_L, ABRConstants.ICON_SEARCH, ABRConstants.SPACE_M, new Insets(5));
        addNewElement = componentBuilder.buildButton(
                "Add", ABRConstants.SPACE_L, ABRConstants.ICON_TICK, ABRConstants.SPACE_SM, new Insets(5));

        searchWithIdsButton = componentBuilder.buildButton(
                "With IDs", ABRConstants.SPACE_ZERO, "/refresh.png", ABRConstants.SPACE_M, new Insets(5.0D));
        searchWithNamesButton = componentBuilder.buildButton(
                "With Names", ABRConstants.SPACE_ZERO, "/refresh.png", ABRConstants.SPACE_M, new Insets(5.0D));
        searchWithoutIdsAndNamesBtn = componentBuilder.buildButton(
                "Input/Buttons (No IDs/Names)",
                ABRConstants.SPACE_ZERO,
                "/refresh.png",
                ABRConstants.SPACE_M,
                new Insets(5.0D));

        refreshInputFieldsButton = componentBuilder.buildButton(
                "Input Fields", ABRConstants.SPACE_ZERO, "/refresh.png", ABRConstants.SPACE_M, new Insets(5.0D));
        refreshOutputFieldsButton = componentBuilder.buildButton(
                "Output Fields", ABRConstants.SPACE_ZERO, "/refresh.png", ABRConstants.SPACE_M, new Insets(5.0D));
        refreshOtherFieldsButton = componentBuilder.buildButton(
                "Other Elements", ABRConstants.SPACE_ZERO, "/refresh.png", ABRConstants.SPACE_M, new Insets(5.0D));
        magicFieldsButton = componentBuilder.buildButton(
                "", ABRConstants.SPACE_ZERO, "/magic2.png", ABRConstants.SPACE_M, new Insets(5.0D));
        magicFieldsButton.setDisable(true);

        cleanListButton = componentBuilder.buildButton(
                "", // No text
                25.0, // Smaller height
                "/cross.png", // Icon source
                16.0, // Smaller icon size
                new Insets(2.0) // Reduced padding
                );

        checkTestAction = new CheckBox("Test Actions");
        checkJavaScript = new CheckBox("JS");

        checkCoordinates = new CheckBox("Coordinates");

        //        checkClickElement.setSelected(true);
        checkClickElement = new CheckBox("For Click");
        checkInputText = new CheckBox("For Input");
        checkOutputText = new CheckBox("For Output (Excel Export)");
        checkFrameText = new CheckBox("iFrame Detected");

        webElementObservableList1 = FXCollections.observableArrayList();

        scannedElements1 = new ListView<>(webElementObservableList1);
        scannedElements1 = componentBuilder.setAnchorPaneAnchors(scannedElements1, ABRConstants.SPACE_ZERO);
        scannedElements1.setCellFactory(new ABRCellFactory<>(ABRWebElementListCell.class)::call);

        webElementObservableList2 = FXCollections.observableArrayList();
        scannedElements2 = new ListView<>(webElementObservableList2);
        scannedElements2 = componentBuilder.setAnchorPaneAnchors(scannedElements2, ABRConstants.SPACE_ZERO);
        scannedElements2.setCellFactory(new ABRCellFactory<>(ABRWebElementListCell.class)::call);

        webElementObservableList3 = FXCollections.observableArrayList();
        scannedElements3 = new ListView<>(webElementObservableList3);
        scannedElements3 = componentBuilder.setAnchorPaneAnchors(scannedElements3, ABRConstants.SPACE_ZERO);
        scannedElements3.setCellFactory(new ABRCellFactory<>(ABRWebElementListCell.class)::call);

        configureButton = componentBuilder.buildButton(
                "Config", ABRConstants.SPACE_M, ABRConstants.ICON_CONFIG, ABRConstants.SPACE_M, new Insets(5.0D));

        launchBotJobButton = componentBuilder.buildButton(
                "Pre-Launch", ABRConstants.SPACE_ZERO, "/play.png", ABRConstants.SPACE_M, new Insets(5.0D));
        recallJobButton = componentBuilder.buildButton(
                "Resume", ABRConstants.SPACE_ZERO, "/play.png", ABRConstants.SPACE_M, new Insets(5.0D));

        countdownTextField = new TextArea("10");
        countdownTextField.setStyle("-fx-font-size: 14px; -fx-text-fill: blue;");
        countdownTextField.setEditable(false);

        checkActiveHover = new CheckBox("Identify");

        defineNameLabel = new Label("DEFINE ELEMENT NAME");

        attribIdTextFieldLabel = new Label("Attrib Id Found");
        attribNameTextFieldLabel = new Label("Attrib Name Found");
        currentXPathLabel = new Label("XPath");
        currentAbsoluteXPathLabel = new Label("Absolut XPath");
        customXPathLabel = new Label("Custom XPath");
        originalTagNameLabel = new Label("Tag Name");
        coordsTextFieldLabel = new Label("Coordinates");

        defineNameField = new TextField();
        defineNameField.setPromptText("DEFINE A NAME");

        attribIdTextField = new TextField();
        attribIdTextField.setPromptText("Attrib Id");
        attribNameTextField = new TextField();
        attribNameTextField.setPromptText("Attrib Name");
        currentXPathTextField = new TextField();
        currentXPathTextField.setPromptText("XPath");
        //        iFrameXPathTextField = new TextField();
        //        iFrameXPathTextField.setPromptText("iFrame XPath");
        absolutXPathTextField = new TextField();
        absolutXPathTextField.setPromptText("Absolut XPath");
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
        currentAbsoluteXPathLabel.setVisible(false);
        absolutXPathTextField.setVisible(false);

        leftButton = componentBuilder.buildButton(
                "Previous", ABRConstants.SPACE_M, ABRConstants.ICON_LEFT, ABRConstants.SPACE_M, new Insets(5.0D));
        rightButton = componentBuilder.buildButton(
                "Next", ABRConstants.SPACE_M, ABRConstants.ICON_RIGHT, ABRConstants.SPACE_M, new Insets(5.0D));

        leftButton.setDisable(true);
        rightButton.setDisable(true);

        leftButton.setOnAction(e -> switchToLeftTab());
        rightButton.setOnAction(e -> switchToRightTab());

        cleanListButton.setOnAction(e -> {
            //            webElementObservableList1.clear();
            //            webElementObservableList2.clear();
            webElementObservableList3.clear();
        });

        currentURL = new Text("");
        currentURL.setFill(Color.BLUE);
        currentURL.setStyle("-fx-font-size: 16px;");

        updateSceneTitleWithCurrentURL(botJob.getHomeBanking().getUrl());

        this.blockLoadList = performDataBase.loadBlocksByBotJobId(this.botJob.getId());
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
            gridPaneTop.add(scanButton, 0, 0);
            gridPaneTop.add(refreshInputFieldsButton, 1, 0);
            gridPaneTop.add(searchWithIdsButton, 2, 0);
            gridPaneTop.add(searchWithNamesButton, 3, 0);
            gridPaneTop.add(searchWithoutIdsAndNamesBtn, 4, 0);
            gridPaneTop.add(refreshOutputFieldsButton, 5, 0);
            gridPaneTop.add(refreshOtherFieldsButton, 6, 0);
            gridPaneTop.add(magicFieldsButton, 7, 0);
            gridPaneTop.add(leftButton, 8, 0);
            gridPaneTop.add(rightButton, 9, 0);

            //        gridPaneTop.add(configureButton, 4, 0);
            //        gridPaneTop.add(launchBotJobButton, 5, 0);
            //        gridPaneTop.add(checkActiveHover, 6, 0);
            //        gridPaneTop.add(addNewElement, 7, 0);
            //        gridPaneTop.add(currentXPathTextField, 8, 0);

            HBox boxCoordenates = new HBox();
            boxCoordenates.setSpacing(5);

            checkClickElement
                    .prefWidthProperty()
                    .bind(boxCoordenates.widthProperty().multiply(0.50));
            checkCoordinates
                    .prefWidthProperty()
                    .bind(boxCoordenates.widthProperty().multiply(0.50));

            // Add elements to the HBox
            //            boxCoordenates.getChildren().addAll(checkClickElement, checkCoordinates);
            boxCoordenates.getChildren().addAll(checkClickElement);

            VBox vBoxCheckBox = new VBox();
            vBoxCheckBox.getChildren().addAll(boxCoordenates, checkInputText, checkOutputText, checkFrameText);
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
            hBoxLaunchButon.getChildren().addAll(launchBotJobButton, recallJobButton);

            HBox boxName = new HBox();
            boxName.getChildren().addAll(defineNameField, addNewElement);

            HBox boxActions = new HBox();
            boxActions.setSpacing(5);

            // Set proportional widths for each child
            testActionsField = new TextField("0001");
            checkTestAction.prefWidthProperty().bind(boxActions.widthProperty().multiply(0.70));
            checkJavaScript.prefWidthProperty().bind(boxActions.widthProperty().multiply(0.10));
            testActionsField.prefWidthProperty().bind(boxActions.widthProperty().multiply(0.3));

            // Add elements to the HBox
            //            boxActions.getChildren().addAll(checkTestAction, checkJavaScript, testActionsField);
            boxActions.getChildren().addAll(checkTestAction, testActionsField);

            // Create the VBox for TextFields
            VBox textFieldVBox = new VBox();
            textFieldVBox.setSpacing(6); // Adjust spacing between TextFields
            textFieldVBox
                    .getChildren()
                    .addAll(
                            checkActiveHover,
                            defineNameLabel,
                            boxName,
                            //                            attribIdTextFieldLabel,
                            //                            attribIdTextField,
                            //                            attribNameTextFieldLabel,
                            //                            attribNameTextField,
                            //                            currentXPathLabel,
                            //                            currentXPathTextField,
                            //                            currentAbsoluteXPathLabel,
                            //                            absolutXPathTextField,
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
                            boxActions,
                            createSpacerVert(),
                            createCustomSeparator(Color.DARKBLUE, 2),
                            hBoxLaunchButon,
                            configureButton);

            customXPathLabel.setVisible(false);
            customXPathTextField.setVisible(false);
            originalTagNameLabel.setVisible(false);
            originalTagNameField.setVisible(false);
            currentAbsoluteXPathLabel.setVisible(false);
            absolutXPathTextField.setVisible(false);

            // Bind button widths to VBox width
            boxActions.maxWidthProperty().bind(textFieldVBox.widthProperty());

            // Bind button widths to VBox width
            addNewElement.maxWidthProperty().bind(textFieldVBox.widthProperty());
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
            scannedElements1.prefHeightProperty().bind(boxListViews.heightProperty());
            scannedElements2.prefHeightProperty().bind(boxListViews.heightProperty());
            scannedElements3.prefHeightProperty().bind(boxListViews.heightProperty());

            boxListViews.setSpacing(5);

            // Set Hgrow for each ListView to make them equally distributed
            HBox.setHgrow(scannedElements1, Priority.ALWAYS);
            HBox.setHgrow(scannedElements2, Priority.ALWAYS);
            HBox.setHgrow(scannedElements3, Priority.ALWAYS);

            StackPane stackCurrentURL = new StackPane();
            stackCurrentURL.getChildren().add(currentURL);
            stackCurrentURL.setAlignment(Pos.CENTER);
            HBox currentURLBox = new HBox(stackCurrentURL);

            Label labelInput = new Label("Input/IDs/Names(No Ids/Names)/Buttons");
            StackPane stackLabelInput = new StackPane();
            stackLabelInput.getChildren().add(labelInput);
            stackLabelInput.setAlignment(Pos.CENTER);
            VBox elements1VBox = new VBox(stackLabelInput, scannedElements1);

            Label labelOutput = new Label("Output Fields Results");
            StackPane stackLabelOutput = new StackPane();
            stackLabelOutput.getChildren().add(labelOutput);
            stackLabelOutput.setAlignment(Pos.CENTER);
            VBox elements2VBox = new VBox(stackLabelOutput, scannedElements2);

            Label labelOthers = new Label("Other Elements Results (Config)");
            StackPane stackLabelOthers = new StackPane();
            HBox othersBox = new HBox();
            createSpacerHoriz();
            othersBox.getChildren().addAll(labelOthers, createSpacerHoriz(), cleanListButton);
            stackLabelOthers.getChildren().addAll(othersBox);

            stackLabelOthers.setAlignment(Pos.CENTER);
            VBox elements3VBox = new VBox(stackLabelOthers, scannedElements3);

            boxListViews.getChildren().addAll(elements1VBox, elements2VBox, elements3VBox, textFieldVBox);

            VBox.setVgrow(boxListViews, Priority.ALWAYS);

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
            ABRLogger.getInstance(ABRScannedElementPane.class).fine("Error using Separator line\n" + ex);
        }
    }

    private void refreshBlocks(boolean secondItem) {
        this.blockLoadList = performDataBase.loadBlocksByBotJobId(this.botJob.getId());
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
        if (performAction.windowHandlesList.size() > 1) {
            // Disable the left button if we are on the first tab
            leftButton.setDisable(currentTabIndex == 0);

            // Disable the right button if we are on the last tab
            rightButton.setDisable(currentTabIndex == performAction.windowHandlesList.size() - 1);
        } else {
            // Disable both buttons if there's only one tab or no tabs
            leftButton.setDisable(true);
            rightButton.setDisable(true);
        }
    }

    // Switch to the previous tab (left)
    private void switchToLeftTab() {
        if (abrWebDriver.getDriver().getWindowHandles().size() > 1 && currentTabIndex > 0) {
            // Decrease the index to move to the left
            currentTabIndex--;

            // Switch to the previous tab
            abrWebDriver.getDriver().switchTo().window(performAction.windowHandlesList.get(currentTabIndex));
            updateSceneTitleWithCurrentURL(abrWebDriver.getDriver().getCurrentUrl());

            // Disable the left button if we are at the first tab
            leftButton.setDisable(currentTabIndex == 0);

            // Enable the right button since we're no longer on the last tab
            rightButton.setDisable(false);
        }
    }

    // Switch to the next tab (right)
    private void switchToRightTab() {
        if (abrWebDriver.getDriver().getWindowHandles().size() > 1
                && currentTabIndex < performAction.windowHandlesList.size() - 1) {
            // Increase the index to move to the right
            currentTabIndex++;

            // Switch to the next tab
            abrWebDriver.getDriver().switchTo().window(performAction.windowHandlesList.get(currentTabIndex));
            updateSceneTitleWithCurrentURL(abrWebDriver.getDriver().getCurrentUrl());

            // Disable the right button if we are at the last tab
            rightButton.setDisable(currentTabIndex == performAction.windowHandlesList.size() - 1);

            // Enable the left button since we're no longer on the first tab
            leftButton.setDisable(false);
        }
    }

    // Method to handle the scenario where the window handles size changes
    private void handleWindowHandlesChange() {
        Set<String> currentWindowHandles = abrWebDriver.getDriver().getWindowHandles();

        // If the number of window handles has changed
        if (currentWindowHandles.size() != performAction.windowHandlesList.size()) {
            // Update the window handles list with the new handles
            performAction.updateWindowHandlesList();

            // Switch to the last window (most recent tab)
            currentTabIndex = performAction.windowHandlesList.size() - 1; // The last index in the list
            abrWebDriver.getDriver().switchTo().window(performAction.windowHandlesList.get(currentTabIndex));

            // Update the scene title with the current URL of the last tab
            updateSceneTitleWithCurrentURL(abrWebDriver.getDriver().getCurrentUrl());
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
        VBox.setVgrow(spacer, Priority.ALWAYS); // Make spacer expand vertically
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
                    !Strings.isNullOrEmpty(managerProps.getProperty(ABRPropertyEnum.REDUCE_SEARCH_CRITERIA))
                            ? Integer.parseInt(managerProps.getProperty(ABRPropertyEnum.MAX_LOG_SIZE))
                            : 20;
        } catch (Exception ex) {
            ABRLogger.getInstance(ABRScannedElementPane.class)
                    .fine("REDUCE_SEARCH_CRITERIA is Empty -> Setting REDUCE_SEARCH_CRITERIA to Max 20elements");
            reduceSearchCriteria = 20;
        }

        //        configureButton.setOnMouseClicked(e -> new ABRConfigurationScene().show());
        configureButton.setOnMouseClicked(e -> abrNewHomeBankingScene.show());
        launchBotJobButton.setOnMouseClicked(e -> {
            //                        loadBotJob(botJob);

            if (!lastBrowserTab()) {
                return;
            }

            this.botJobLoadList = performDataBase.loadBotJobComplete(botJob.getId());
            instructionsExecuted.clear();

            // Set all instructions' executed field to false
            botJobLoadList.get(0).getBlockLoadDTOList().stream()
                    .flatMap(block -> block.getBlockLoopInstructionLoadDTOS().stream())
                    .forEach(instruction -> instruction.setExecuted(false));

            recallJob();
        });

        recallJobButton.setOnMouseClicked(e -> {
            if (!lastBrowserTab()) {
                return;
            }

            this.botJobLoadList = performDataBase.loadBotJobComplete(botJob.getId());
            // loadBotJob(botJob);
            recallJob();
        });
        checkActiveHover.setOnMouseClicked(e -> {
            checkTestAction.setSelected(false);

            handleHoverCheckClick();
        });
        checkClickElement.setOnAction(event -> {
            if (checkClickElement.isSelected()) {
                checkInputText.setSelected(false);
                checkOutputText.setSelected(false);
                checkFrameText.setSelected(false);
            }
        });

        checkInputText.setOnAction(event -> {
            if (checkInputText.isSelected()) {
                checkClickElement.setSelected(false);
                checkOutputText.setSelected(false);
                checkFrameText.setSelected(false);
            }
        });

        checkOutputText.setOnAction(event -> {
            if (checkOutputText.isSelected()) {
                checkClickElement.setSelected(false);
                checkInputText.setSelected(false);
                checkFrameText.setSelected(false);
            }
        });

        checkFrameText.setOnAction(event -> {
            if (checkFrameText.isSelected()) {
                checkClickElement.setSelected(false);
                checkInputText.setSelected(false);
                checkOutputText.setSelected(false);
            }
        });

        scanButton.setOnAction(e -> manageUIScan());

        addNewElement.setOnAction(e -> {
            if (searchReturn.getElement() != null) {
                insertNewElement();
            }
        });

        refreshInputFieldsButton.setOnAction(e -> refreshInputBtn());
        refreshOutputFieldsButton.setOnAction(e -> refreshOutputBtn());
        refreshOtherFieldsButton.setOnAction(e -> refreshOtherElemBtn());
        magicFieldsButton.setOnAction(e -> performAction.createOutputHtml("input", abrWebDriver.getDriver()));
        searchWithIdsButton.setOnAction(e -> refreshWithIdsBtn());
        searchWithNamesButton.setOnAction(e -> refreshWithNamesBtn());
        searchWithoutIdsAndNamesBtn.setOnAction(e -> refreshWithoutIdsAndNamesBtn());

        scannedElements1.getItems().addListener(this::addBehaviourToAddedElements);
        scannedElements2.getItems().addListener(this::addBehaviourToAddedElements);
        scannedElements3.getItems().addListener(this::addBehaviourToAddedElements);

        //        manageUIScan();
    }

    private boolean lastBrowserTab() {
        // Get all window handles (all open tabs/windows)
        try {
            windowHandles = abrWebDriver.getDriver().getWindowHandles();

            // Convert the window handles set to a list
            List<String> windowHandlesList = new ArrayList<>(windowHandles);

            // Switch to the last window (newly opened tab)
            abrWebDriver.getDriver().switchTo().window(windowHandlesList.get(windowHandlesList.size() - 1));

            return true;
        } catch (Exception e) {

            browserNotAttached();

            return false;
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

        if (searchReturn != null) {

            searchReturn.setDefinedName(defineNameField.getText().trim());

            try {
                if (searchReturn.getElement() == null) {
                    // First  Search for xPath
                    searchWebElementSequence();
                }

                if (searchReturn.getElement() != null) {

                    ABRWebElement abrWebElement = new ABRWebElement(this.searchReturn, botJob.getId());
                    if (abrWebElement != null && abrWebElement.getElement() != null) {
                        webElementObservableList3.add(abrWebElement);
                    }

                } else {
                    ABRLogger.getInstance(ABRScannedElementPane.class).severe("Could not find the Web Element!");
                }

            } catch (Exception ex) {
                ABRLogger.getInstance(ABRScannedElementPane.class)
                        .severe("Error Attempt to create a Dynamic Element\n" + ex.getMessage());
            }
        }
    }

    private SearchReturn extractValidateDynamic() {

        defineNameField.setText("");
        xpathTextPrevious = absolutXPathTextField.getText();

        // Reset Previous Values
        searchReturn.setAttribId(attribIdTextField.getText());
        searchReturn.setAttribName(attribNameTextField.getText());
        searchReturn.setOriginalTagName(originalTagNameField.getText());
        searchReturn.setAttributeType("");
        searchReturn.setAttributeValue("");
        searchReturn.setCoords("");
        searchReturn.setCurrentXPath(currentXPathTextField.getText());
        searchReturn.setiFrameXPath(iFrameXPath);
        //        searchReturn.setiFrameElements(iFrameElements);
        searchReturn.setAbsolutXPath(absolutXPathTextField.getText());
        searchReturn.setCustomXPath(customXPathTextField.getText());
        searchReturn.setElement(null);
        searchReturn.setForceTypeEnum(WebElementTagNameEnum.ALL);

        // First  Search for xPath
        searchWebElementSequence();

        if (searchReturn.getElement() != null) {
            // Extract id or name between '(' and ')'
            String tagName = originalTagNameField.getText();
            String coordinates = coordsTextField.getText();
            searchReturn.setCoords(coordsTextField.getText());

            // Split coordinates into coordLeft and coordRight based on comma
            String[] coords = coordinates.split(",");
            if (coords.length == 2) {
                String coordLeft = coords[0].trim();
                String coordRight = coords[1].trim();

                // Print or use the extracted values
                System.out.println("Tag Name: " + searchReturn.getOriginalTagName());
                System.out.println("Id: " + searchReturn.getAttribId());
                System.out.println("Name: " + searchReturn.getAttribName());
                System.out.println("xPath: " + searchReturn.getCurrentXPath());
                System.out.println("Absolut xPath: " + searchReturn.getAbsolutXPath());
                System.out.println("Custom xPath: " + searchReturn.getCustomXPath());
                System.out.println("iFrame xPath: " + searchReturn.getiFrameXPath());
                System.out.println("CoordLeft: " + coordLeft);
                System.out.println("CoordRight: " + coordRight);

                // Here I am forcing as Button "CLICKABLE" or "IMPUTABLE"
                if (tagName.equalsIgnoreCase(WebElementTagNameEnum.BUTTON.getValue())
                        || tagName.equalsIgnoreCase(WebElementTagNameEnum.INPUT.getValue())
                        || tagName.equalsIgnoreCase(WebElementTagNameEnum.TEXT_AREA.getValue())
                        || tagName.equalsIgnoreCase(WebElementTagNameEnum.DIV.getValue())
                        || tagName.equalsIgnoreCase(WebElementTagNameEnum.OPTION.getValue())
                        || tagName.equalsIgnoreCase(WebElementTagNameEnum.MAT_SELECT.getValue())) {
                    searchReturn.setForceTypeEnum(WebElementTagNameEnum.BUTTON);
                } else {
                    searchReturn.setForceTypeEnum(WebElementTagNameEnum.INPUT);
                }

                boolean clickable = isClickable(searchReturn.getElement());

                boolean tagClickable = false;
                // Define regex to extract specific tags (e.g., a, button)
                String regex = "/([^/\\[]+)";
                Pattern pattern = Pattern.compile(regex);
                Matcher matcher = pattern.matcher(searchReturn.getAbsolutXPath());

                // Iterate through all matches and check for target tags
                while (matcher.find()) {
                    String tag = matcher.group(1);
                    if (tag.equals("a") || tag.equals("button")) {
                        System.out.println("Found clickable tag: <" + tag + ">");
                        tagClickable = true;
                        break;
                    }
                }

                Boolean inputContains =
                        searchReturn.getCurrentXPath().toLowerCase().contains("input");

                Boolean selectContains =
                        searchReturn.getCurrentXPath().toLowerCase().contains("select");

                boolean finalTagClickable = tagClickable;
                Platform.runLater(() -> {
                    if (!checkFrameText.isSelected()) {
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
                    }
                });

                return searchReturn;
            } else {
                return null;
            }

        } else {
            ABRLogger.getInstance(ABRScannedElementPane.class)
                    .severe("Could not find any Web Element with XPath/Id/Attributes values.");
        }

        return null;
    }

    private WebElement searchWebElementSequence() {
        WebElement element = null;
        if (!Strings.isNullOrEmpty(searchReturn.getCurrentXPath())) {
            if (element == null) {
                try {
                    element = abrWebDriver.getDriver().findElement(By.xpath("//" + searchReturn.getCurrentXPath()));
                    if (element != null) {
                        searchReturn.setElement(element);
                        searchReturn.setxPathWorkedFirst(
                                ABRConstants.REGULAR_XPATH); // BECAUSE OS LIMITATION OF ACCESS DB 255 CHARACTER
                    }
                } catch (Exception e) {
                    ABRLogger.getInstance(ABRScannedElementPane.class)
                            .warning(String.format(
                                    "Cannot locate a Web Element with Regular XPath\n%s",
                                    searchReturn.getCurrentXPath()));
                }
            }
            if (element == null) {
                try {
                    element = abrWebDriver.getDriver().findElement(By.xpath(searchReturn.getAbsolutXPath()));
                    if (element != null) {
                        searchReturn.setElement(element);
                        searchReturn.setxPathWorkedFirst(
                                ABRConstants.ABSOLUT_XPATH); // BECAUSE OS LIMITATION OF ACCESS DB 255 CHARACTER
                    }
                } catch (Exception e) {
                    ABRLogger.getInstance(ABRScannedElementPane.class)
                            .warning(String.format(
                                    "Cannot locate a Web Element with Absolut XPath\n%s",
                                    searchReturn.getAbsolutXPath()));
                }
            }

            //            if (searchReturn.getCurrentXPath().startsWith("id(")) {
            if (!Strings.isNullOrEmpty(searchReturn.getAttribId())) {
                searchReturn.setAttributeType("id");
                searchReturn.setAttributeValue(searchReturn.getAttribId());
                if (searchReturn.getElement() == null) {
                    try {
                        element = abrWebDriver.getDriver().findElement(By.id(searchReturn.getAttribId()));
                        if (element != null) {
                            searchReturn.setElement(element);
                        }
                    } catch (Exception e) {
                        ABRLogger.getInstance(ABRScannedElementPane.class)
                                .warning(String.format(
                                        "Cannot locate a Web Element with ID: \n%s", searchReturn.getAttribId()));
                    }
                }

            } else if (!Strings.isNullOrEmpty(searchReturn.getAttribName())) {
                searchReturn.setAttributeType("name");
                if (searchReturn.getElement() == null) {
                    try {
                        element = abrWebDriver.getDriver().findElement(By.name(searchReturn.getAttribName()));
                        if (element != null) {
                            searchReturn.setElement(element);
                        }
                    } catch (Exception e) {
                        ABRLogger.getInstance(ABRScannedElementPane.class)
                                .warning(String.format(
                                        "Cannot locate a Web Element with Name: \n%s", searchReturn.getAttribName()));
                    }
                }
            }
        }
        return element;
    }

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

    private void refreshWithoutIdsAndNamesBtn() {
        webElementObservableList1.clear();
        manageUIScanWithoutNameAndId();
    }

    private void refreshWithNamesBtn() {
        webElementObservableList1.clear();
        manageUIScanAttributeNameFirst();
    }

    private void refreshWithIdsBtn() {
        webElementObservableList1.clear();
        manageUIScanIdsFirst();
    }

    private void handleHoverCheckClick() {
        if (checkActiveHover.isSelected()) {
            Platform.runLater(() -> periodicThread(abrWebDriver.getDriver()));
            //            injectJavaScript(abrWebDriver.getDriver());
            //            injectJumpTab(abrWebDriver.getDriver());
        } else {
            Platform.runLater(() -> revertInjectedChanges(abrWebDriver.getDriver()));
        }
        //        checkClickElement.setDisable(checkActiveHover.isSelected());
        //        checkInputText.setDisable(checkActiveHover.isSelected());
        //        addNewElement.setDisable(checkActiveHover.isSelected());
        Platform.runLater(() -> {
            checkTestAction.setDisable(checkActiveHover.isSelected());
            launchBotJobButton.setDisable(checkActiveHover.isSelected());
            recallJobButton.setDisable(checkActiveHover.isSelected());
            periodicActivated = checkActiveHover.isSelected();

            if (!checkActiveHover.isSelected()) {
                defineNameField.clear();
            }
        });
    }

    private void manageUIScan() {
        ABRLogger.getInstance(ABRScannedElementPane.class).info("iFrames scan triggered");
        webElementObservableList1.clear();

        boolean scanOk = scanIframesAndElements(webElementObservableList1);
    }

    private void manageUIScanWithoutNameAndId() {
        idAttributeFirst = false;
        nameAttributeFirst = false;
        withoutNameAndId = true;
        // addProgressBar();

        // First Check About the Scanner havina  a Browser Attached
        boolean scanOk = scanABRElementsAsync(
                null, null, null, webElementObservableList1, "input", "UI Scan \"Inputs\" Without Name And Id");
        // addProgressBar();
        if (scanOk) {
            scanABRElementsAsync(
                    null, null, null, webElementObservableList1, "button", "UI Scan \"Buttons\" Without Name And Id");
        }
    }

    private void addProgressBar(int items) {
        int currentChildrenCount = bottomPane.getChildren().size();
        if (currentChildrenCount < 50) {
            // Calculate how many more ProgressBars can be added without exceeding 20
            int availableSlots = 50 - currentChildrenCount;

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

    private void manageUIScanAttributeNameFirst() {
        idAttributeFirst = false;
        nameAttributeFirst = true;
        withoutNameAndId = false;
        scanABRElementsAsync(null, null, null, webElementObservableList1, "name", "UI Scan Attribute Name First");
    }

    private boolean manageUIScanIdsFirst() {
        idAttributeFirst = true;
        nameAttributeFirst = false;
        withoutNameAndId = false;
        return scanABRElementsAsync(null, null, null, webElementObservableList1, "id", "UI Scan Ids First");
    }

    private void manageUIScanInputs() {
        List<WebElementTagNameEnum> inputTags = WebElementTagNameEnum.insertableTags();
        for (WebElementTagNameEnum tag : inputTags) {
            // addProgressBar();
            boolean scanOk = scanABRElementsAsync(
                    null,
                    By.tagName(tag.getValue()),
                    ABRWebElement::isNotClickable,
                    webElementObservableList1,
                    null,
                    "UI Scan Inputs");

            if (!scanOk) {
                break;
            }
        }
    }

    private void manageUIScanClickable() {
        List<WebElementTagNameEnum> clickableTags = WebElementTagNameEnum.clickableTags();
        for (WebElementTagNameEnum tag : clickableTags) {
            // addProgressBar();
            boolean scanOK = scanABRElementsAsync(
                    null,
                    By.tagName(tag.getValue()),
                    ABRWebElement::isClickable,
                    webElementObservableList2,
                    null,
                    "UI Scan Clickable");

            if (!scanOK) {
                break;
            }
        }
    }

    private void manageUIScanPriorities() {
        Set<WebElement> webElements = managePrioritiesCriteria();
        try {
            if (webElements != null && webElements.size() > 0) {
                // addProgressBar();
                scanABRElementsAsync(webElements, null, null, webElementObservableList3, null, "UI Scan By Priorities");
            }
        } catch (Exception e) {
            System.out.println("Error " + e.getMessage());
        }
    }

    private void manageUIScanOutputs() {
        scanABRElementsAsync(By.xpath("CODE_CRITERIA"), webElementObservableList2, "UI Scan Outputs");
    }

    private void scanABRElementsAsync(
            By criteria, ObservableList<ABRWebElement> listToAddNewElements, String criteriaMSG) {
        scanABRElementsAsync(null, criteria, null, listToAddNewElements, null, criteriaMSG);
    }

    private boolean scanABRElementsAsync(
            Set<WebElement> preElements,
            By criteria,
            Predicate<ABRWebElement> filterCondition,
            ObservableList<ABRWebElement> listToAddNewElements,
            String elementType,
            String criteriaMSG) {

        // Check if Browser is Inactive
        try {
            windowHandles = abrWebDriver.getDriver().getWindowHandles();
        } catch (Exception e) {
            browserNotAttached();
            return false;
        }

        executorService = Executors.newCachedThreadPool();

        // External variables to hold the sizes
        AtomicInteger listABRElementsSize = new AtomicInteger(0);
        AtomicInteger scannedElementListSize = new AtomicInteger(0);

        // Simulate async task completion with CompletableFuture
        // Simulate async task completion with CompletableFuture
        CompletableFuture<Void> future = CompletableFuture.runAsync(
                () -> {
                    List<ABRWebElement> listABRElements = null;
                    List<WebElement> scannedElementList = new ArrayList<>();

                    // Separation between creation of ABR Elements
                    try {
                        ABRLogger.getInstance(ABRScannedElementPane.class)
                                .fine("Starting scan of elements for criteria: "
                                        + (criteria != null ? criteria : criteriaMSG));

                        if (idAttributeFirst || nameAttributeFirst) {
                            mapAdvanced = findElementsWithXPath(abrWebDriver.getDriver(), elementType);
                            listABRElements = createAdvancedABRElement(mapAdvanced, elementType, null);
                        } else if (withoutNameAndId) {
                            mapAdvanced = findElementsWithoutIdOrName(abrWebDriver.getDriver(), elementType);
                            listABRElements = createAdvancedABRElement(mapAdvanced, elementType, null);
                        } else if (preElements != null && preElements.size() > 0) {
                            scannedElementList.addAll(preElements);
                        } else if (criteria != null) {
                            if (criteria.equals(By.xpath("CODE_CRITERIA"))) {
                                mapAdvanced = findElementsOutputCriteria(abrWebDriver.getDriver());
                                listABRElements = createAdvancedABRElement(
                                        mapAdvanced, elementType, WebElementTagNameEnum.OUTPUT);
                            } else {
                                scannedElementList = abrWebDriver.getDriver().findElements(criteria);
                            }
                        }
                        if (listABRElements != null && listABRElements.size() > 0) {
                            listABRElementsSize.set(listABRElements.size());
                            //                            addProgressBar(listABRElements.size());
                            ABRLogger.getInstance(ABRScannedElementPane.class)
                                    .finer("list of Advanced Scanner elements has " + listABRElementsSize.get());
                        }

                        // Reset these
                        idAttributeFirst = false;
                        nameAttributeFirst = false;
                        withoutNameAndId = false;

                        if (scannedElementList != null && scannedElementList.size() > 0) {
                            scannedElementListSize.set(scannedElementList.size());
                            addProgressBar(scannedElementListSize.get());

                            ABRLogger.getInstance(ABRScannedElementPane.class)
                                    .finer("list of scanned elements has " + scannedElementListSize.get()
                                            + " elements for Search Criteria " + criteria);
                            if (scannedElementListSize.get() > reduceSearchCriteria) {

                                ABRLogger.getInstance(ABRScannedElementPane.class)
                                        .fine("Reduces to the Limit of ABRWebElements : " + reduceSearchCriteria);
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
                                WebElementTagNameEnum finalTypeSearch = typeSearch;
                                listABRElements = scannedElementList.stream()
                                        .filter(element -> element != null) // Filter out null elements
                                        //                                        .peek(element -> addProgressBar(1))
                                        .map(element -> new ABRWebElement(element, botJob.getId(), finalTypeSearch))
                                        .collect(Collectors.toList());
                                listABRElementsSize.set(listABRElements.size());

                            } finally {
                                ABRLogger.getInstance(ABRScannedElementPane.class)
                                        .fine("Final size of listABRElements: " + listABRElementsSize.get());
                                ABRLogger.getInstance(ABRScannedElementPane.class)
                                        .fine("Final size of scannedElementList: " + scannedElementListSize.get());
                            }
                        }

                    } catch (EnumConstantNotPresentException ex) {
                        ABRLogger.getInstance(ABRScannedElementPane.class)
                                .severe("ENUM Property not Defined of Web Element\n " + ex.getMessage());
                        performAction.showAlert(
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

                    // After Creation of ABR Elements - > Update View List
                    if (listABRElements != null) {
                        //                        addProgressBar(listABRElements.size());
                        for (ABRWebElement element : listABRElements) {
                            Platform.runLater(() -> {
                                listToAddNewElements.add(element);
                                if (element.getSavedReferences() != null
                                        && element.getSavedReferences().size() > 0) {
                                    String absolutPath =
                                            element.getSavedReferences().get(0);
                                    ABRLogger.getInstance(ABRScannedElementPane.class)
                                            .finer(String.format(
                                                    "added ABRWebElement with %s References -> xPath: ",
                                                    element.getSavedReferences().size(), absolutPath));

                                } else if (element.getSavedReferences() != null
                                        && element.getSavedReferences().size() == 0) {
                                    ABRLogger.getInstance(ABRScannedElementPane.class)
                                            .finer("added ABRWebElement with NO References!");
                                }
                            });
                        }

                        if (bottomPane.getChildren().size() > 0) {
                            int elementsToRemove = Math.min(
                                    listABRElementsSize.get() + scannedElementListSize.get(),
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
                    //                    Platform.runLater(() -> {
                    //                        if (bottomPane.getChildren().size() > 0) {
                    //                            Node node = bottomPane
                    //                                    .getChildren()
                    //                                    .get(bottomPane.getChildren().size() - 1);
                    //                            bottomPane.getChildren().remove(node);
                    //                        }
                    //                    });
                },
                executorService);

        if (executorService != null) {
            executorService.shutdown();
        }

        // Handle completion of the CompletableFuture to remove the ProgressBar
        future.handle((result, ex) -> {
            if (ex != null) {
                Platform.runLater(() -> {
                    ABRLogger.getInstance(ABRScannedElementPane.class)
                            .fine("Removing ProgressBar due to exception. Sizes: " + "listABRElements="
                                    + listABRElementsSize.get() + ", scannedElementList="
                                    + scannedElementListSize.get());
                    if (bottomPane.getChildren().size() > 0) {
                        int elementsToRemove = Math.min(
                                listABRElementsSize.get() + scannedElementListSize.get(),
                                bottomPane.getChildren().size());

                        for (int x = 0; x < elementsToRemove; x++) {
                            bottomPane
                                    .getChildren()
                                    .remove(bottomPane
                                            .getChildren()
                                            .get(bottomPane.getChildren().size() - 1));
                        }
                    }
                });
            } else {
                Platform.runLater(() -> {
                    ABRLogger.getInstance(ABRScannedElementPane.class)
                            .fine("Future handle completed successfully. Sizes: " + "listABRElements="
                                    + listABRElementsSize.get() + ", scannedElementList="
                                    + scannedElementListSize.get());
                });
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
                ABRLogger.getInstance(ABRScannedElementPane.class)
                        .fine("thenRun executed. Sizes: " + "listABRElements="
                                + listABRElementsSize.get() + ", scannedElementList="
                                + scannedElementListSize.get());

                if (bottomPane.getChildren().size() > 0) {
                    int elementsToRemove = Math.min(
                            listABRElementsSize.get() + scannedElementListSize.get(),
                            bottomPane.getChildren().size());
                    for (int x = 0; x < elementsToRemove; x++) {
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

    public boolean scanIframesAndElements(ObservableList<ABRWebElement> listToAddNewElements) {

        // Check if Browser is Inactive
        try {
            windowHandles = abrWebDriver.getDriver().getWindowHandles();
        } catch (Exception e) {
            browserNotAttached();
            return false;
        }

        executorService = Executors.newCachedThreadPool();
        AtomicInteger totalElementsSize = new AtomicInteger(0);

        CompletableFuture<Void> future = CompletableFuture.runAsync(
                () -> {
                    // Switch back to the main page before checking the next iframe
                    abrWebDriver.getDriver().switchTo().defaultContent();

                    List<String> allXPath = iframeInputLocator.listAllXPaths(abrWebDriver.getDriver());

                    for (String xPATH : allXPath) {
                        if (xPATH.contains("iframe")) {
                            System.out.println(xPATH);
                        }
                    }

                    List<WebElement> iframes = abrWebDriver.getDriver().findElements(By.tagName("iframe"));

                    Map<WebElement, List<WebElement>> iframeElementsMap = new HashMap<>();

                    for (WebElement iframe : iframes) {
                        try {
                            abrWebDriver.getDriver().switchTo().frame(iframe);
                            List<WebElement> elementsInsideIframe =
                                    abrWebDriver.getDriver().findElements(By.xpath("//*"));
                            iframeElementsMap.put(iframe, elementsInsideIframe);
                            totalElementsSize.addAndGet(elementsInsideIframe.size());

                            System.out.println(
                                    "Scanned " + elementsInsideIframe.size() + " elements inside an iframe.");
                            abrWebDriver.getDriver().switchTo().defaultContent();
                        } catch (Exception e) {
                            System.out.println("Failed to scan iframe: " + e.getMessage());
                            abrWebDriver.getDriver().switchTo().defaultContent();
                        }
                    }

                    for (Map.Entry<WebElement, List<WebElement>> entry : iframeElementsMap.entrySet()) {
                        WebElement iframe = entry.getKey();
                        List<WebElement> elementsInsideIframe = entry.getValue();

                        // Switch back to the main page before checking the next iframe
                        abrWebDriver.getDriver().switchTo().frame(iframe);

                        String iFrameXPath = iframeInputLocator.getElementXPathIFrame(iframe, abrWebDriver.getDriver());

                        for (WebElement elementIFrame : elementsInsideIframe) {
                            WebElementTagNameEnum typeSearch = WebElementTagNameEnum.ALL; // Default to ALL

                            String tagName = null;
                            if (elementIFrame.getTagName().equalsIgnoreCase("input")
                                    || elementIFrame.getTagName().equalsIgnoreCase("textarea")) {

                                // Send keys to the input element
                                String inputText = "aaaaaa";
                                elementIFrame.clear(); // Clear any existing value
                                elementIFrame.sendKeys(inputText);

                                // Retrieve the value back
                                String retrievedValue = elementIFrame.getAttribute("value");

                                // Validate if the input was correctly received
                                if (inputText.equals(retrievedValue)) {
                                    System.out.println("SUCCESS: Sent '" + inputText + "' and received '"
                                            + retrievedValue + "' in IFrame.");
                                    tagName = elementIFrame.getTagName().toLowerCase();
                                } else {
                                    System.out.println("ERROR: Sent '" + inputText + "' but received '" + retrievedValue
                                            + "' in IFrame.");
                                }

                                switch (tagName) {
                                    case "input":
                                        typeSearch = WebElementTagNameEnum.INPUT;
                                        break;
                                    case "textarea":
                                        typeSearch = WebElementTagNameEnum.TEXT_AREA;
                                        break;
                                    case "button":
                                        typeSearch = WebElementTagNameEnum.BUTTON;
                                        break;
                                    case "form":
                                        typeSearch = WebElementTagNameEnum.FORM;
                                        break;
                                    case "div":
                                        typeSearch = WebElementTagNameEnum.DIV;
                                        break;
                                    case "p":
                                        typeSearch = WebElementTagNameEnum.PARAGRAPH;
                                        break;
                                    case "a":
                                        typeSearch = WebElementTagNameEnum.ANCHOR;
                                        break;
                                    case "select":
                                        typeSearch = WebElementTagNameEnum.SELECT;
                                        break;
                                    case "option":
                                        typeSearch = WebElementTagNameEnum.OPTION;
                                        break;
                                    case "mat-select":
                                        typeSearch = WebElementTagNameEnum.MAT_SELECT;
                                        break;
                                    case "mat-option":
                                        typeSearch = WebElementTagNameEnum.MAT_OPTION;
                                        break;
                                    case "mat-expansion-panel":
                                        typeSearch = WebElementTagNameEnum.MAT_EXPANSION_PANEL;
                                        break;
                                    default:
                                        typeSearch = WebElementTagNameEnum.ALL;
                                        break;
                                }

                                // Check if element can receive input
                                boolean canSendKeys = false;
                                try {
                                    elementIFrame.sendKeys("test"); // Test sending keys
                                    canSendKeys = true;
                                } catch (Exception ignored) {
                                }

                                // Check if element is clickable
                                boolean isClickable = false;
                                try {
                                    if (elementIFrame.isDisplayed() && elementIFrame.isEnabled()) {
                                        elementIFrame.click(); // Test clicking
                                        isClickable = true;
                                    }
                                } catch (Exception ignored) {
                                }

                                // Debugging logs
                                System.out.println("Element: " + tagName + " | CanSendKeys: " + canSendKeys
                                        + " | Clickable: " + isClickable);

                                // Add to the list with correct typeSearch
                                if (typeSearch != WebElementTagNameEnum.ALL) {

                                    WebElementTagNameEnum finalTypeSearch = typeSearch;

                                    // Platform.runLater(() -> {
                                    listToAddNewElements.add(new ABRWebElement(
                                            elementIFrame, botJob.getId(), finalTypeSearch, iFrameXPath));
                                    System.out.println("Added element: " + elementIFrame.getTagName() + " | Type: "
                                            + finalTypeSearch);
                                    // });

                                    Platform.runLater(() -> {
                                        scannedElements1.refresh();
                                        scannedElements2.refresh();
                                        scannedElements3.refresh();
                                    });
                                }
                            }
                        }
                        abrWebDriver.getDriver().switchTo().defaultContent();
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
                    ABRLogger.getInstance(ABRWebDriver.class).severe("ExecutorService did not terminate");
                }
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
            ABRLogger.getInstance(ABRWebDriver.class).severe("ExecutorService did not terminate\n" + e.getMessage());
        }
    }

    private void refreshInputBtn() {
        webElementObservableList1.clear();
        manageUIScanInputs();
    }

    private void refreshOutputBtn() {
        webElementObservableList2.clear();
        //        manageUIScanClickable();
        manageUIScanOutputs();
    }

    private void refreshOtherElemBtn() {
        webElementObservableList3.clear();

        // Check if Browser is Inactive
        try {
            windowHandles = abrWebDriver.getDriver().getWindowHandles();
        } catch (Exception e) {
            browserNotAttached();
            return;
        }
        manageUIScanPriorities();
    }

    private void addBehaviourToAddedElements(ListChangeListener.Change<? extends ABRWebElement> change) {
        while (change.next()) {
            change.getAddedSubList().forEach(this::addBehaviourToAbrWebElement);
        }
    }

    private void addBehaviourToAbrWebElement(ABRWebElement abrWebElement) {
        EventHandler<MouseEvent> mouseEnteredHandler = mouseEvent -> {
            Task<Void> handleEvent = new Task<>() {
                @Override
                protected Void call() {
                    List<WebElement> elementList =
                            abrWebDriver.getDriver().findElements(By.xpath(abrWebElement.getMainXPath()));
                    for (WebElement element : elementList) {
                        if (((RemoteWebElement) element).getId().equalsIgnoreCase(abrWebElement.getElementId())) {
                            abrWebDriver.highlightElement(element);
                        } else {
                            abrWebDriver.dehighlightElement(element);
                        }
                    }
                    return null;
                }
            };
            new Thread(handleEvent).start();
        };

        EventHandler<MouseEvent> mouseExitedHandler = mouseEvent -> {
            Task<Void> handleEvent = new Task<Void>() {
                @Override
                protected Void call() throws Exception {
                    //                    WebElement element =
                    // abrWebDriver.getDriver().findElement(By.xpath(abrWebElement.getXPath()));
                    //                    if (element != null){
                    //                        abrWebDriver.dehighlightElement(abrWebElement.getElement());
                    //                    }

                    List<WebElement> elementList =
                            abrWebDriver.getDriver().findElements((By.xpath(abrWebElement.getMainXPath())));
                    for (WebElement element : elementList) {
                        if (((RemoteWebElement) element).getId().equalsIgnoreCase(abrWebElement.getElementId())) {
                            abrWebDriver.dehighlightElement(element);
                        }
                    }
                    return null;
                }
            };
            new Thread(handleEvent).start();
        };

        EventHandler<MouseEvent> mouseClickedHandler = mouseEvent -> {
            if (mouseEvent.getClickCount() == 2) {
                // Double clicked the element
                if (abrWebElement.getSavedReferences().size() == 0) {

                    Text variableText1Styled = new Text(String.format(
                            "The Instruction \"%s\" don't have any locators!",
                            abrWebElement.getElement().getText()));

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

                //                String reTakeXPath = getXPath(abrWebDriver.getDriver(), abrWebElement.getElement());
                //                abrWebElement.setMainXPath(reTakeXPath);

                // IF SOME REFRESH CHANGED THE ELEMENT IT TRIGGERS THIS EXCEPTION
                String elemTagName = "No TagName";
                if (!checkTestAction.isSelected()) {
                    try {

                        if (abrWebElement.getMainXPath() == null) {
                            abrWebElement.setMainXPath(
                                    abrWebElement.getSavedReferences().get("absolutXPath"));
                        }
                        if (abrWebElement.getMainCoordinates() == null) {
                            abrWebElement.setMainCoordinates(
                                    abrWebElement.getSavedReferences().get("coordinates"));
                        }

                        WebElement elementFinder = null;
                        try {
                            elementFinder =
                                    abrWebDriver.getDriver().findElement(By.xpath(abrWebElement.getMainXPath()));
                        } catch (Exception e) {
                            System.out.println(e.getMessage());
                        }

                        if (elementFinder == null) {
                            // Try by coordinates
                            Pair<String, String> filedData = new Pair("martini", "Martini");
                            try {
                                performAction.executeActionsAtCoordinates(
                                        abrWebElement.getSavedReferences().get("coordinates"),
                                        filedData,
                                        ABRConstants.CLICK);

                                // It Means Did Not Failed to Coordinates
                                // I am Setting here to avoid the Not Found Message
                                elementFinder = abrWebElement.getElement();
                            } catch (Exception e) {
                                System.out.println(e.getMessage());
                            }
                        }

                        //                    if (elementFinder != null) {
                        //                        abrWebElement.setElement(elementFinder);
                        //                    }

                        if (elementFinder != null
                                && abrWebElement.getElement() != null
                                && abrWebElement.getElement().getTagName() != null) {
                            elemTagName = abrWebElement.getElement().getTagName();
                        }
                    } catch (Exception ex) {
                        performMessage.couldNotFindElement(elemTagName);
                        return;
                    }
                }

                if (checkTestAction.isSelected()) {
                    try {
                        if (abrWebElement.getElement() != null) {

                            abrWebDriver.dehighlightElement(abrWebElement.getElement());

                            //                            WebElement elementXPath =
                            //
                            // abrWebDriver.getDriver().findElement(By.xpath(abrWebElement.getMainXPath()));
                            //                            if (elementXPath != null) {
                            //                                elementXPath.click();
                            //                            }

                            Pair<String, String> fieldData = new Pair<>("Test", testActionsField.getText());

                            String mainCoordenates = coordsTextField.getText().trim();
                            String savedCoordenates =
                                    abrWebElement.getSavedReferences().get("coordinates");
                            if (Strings.isNullOrEmpty(mainCoordenates)) {
                                mainCoordenates = abrWebElement.getMainCoordinates();
                            }

                            if (Strings.isNullOrEmpty(savedCoordenates)) {
                                savedCoordenates = mainCoordenates;
                            }

                            String[] coordinates = new String[] {mainCoordenates, savedCoordenates};

                            if (checkCoordinates.isSelected()) {
                                performAction.executeActionsAtCoordinates(
                                        coordinates[1], fieldData, ABRConstants.VISUALIZE);
                                performAction.executeActionsAtCoordinates(
                                        coordinates[0], fieldData, ABRConstants.VISUALIZE);

                                performAction.executeActionsAtCoordinates(
                                        coordinates[1], fieldData, ABRConstants.CLICK);
                                performAction.executeActionsAtCoordinates(
                                        coordinates[0], fieldData, ABRConstants.CLICK);

                                performAction.executeActionsAtCoordinates(
                                        coordinates[1], fieldData, ABRConstants.INSERT);
                                performAction.executeActionsAtCoordinates(
                                        coordinates[0], fieldData, ABRConstants.INSERT);

                                performAction.moveAndClickAtCoordinates(coordinates[1], abrWebDriver.getDriver());
                                performAction.moveAndClickAtCoordinates(coordinates[0], abrWebDriver.getDriver());
                            }

                            String result = performAction.sequenceOfCommands(
                                    abrWebElement.getElement(),
                                    ABRConstants.SELECT,
                                    coordinates,
                                    fieldData,
                                    abrWebDriver.getDriver());
                            System.out.println(result);
                            result = performAction.sequenceOfCommands(
                                    abrWebElement.getElement(),
                                    ABRConstants.CLICK,
                                    coordinates,
                                    fieldData,
                                    abrWebDriver.getDriver());
                            System.out.println(result);
                            result = performAction.sequenceOfCommands(
                                    abrWebElement.getElement(),
                                    ABRConstants.GET_VALUE,
                                    coordinates,
                                    fieldData,
                                    abrWebDriver.getDriver());
                            System.out.println(result);
                            result = performAction.sequenceOfCommands(
                                    abrWebElement.getElement(),
                                    ABRConstants.CLEAR,
                                    coordinates,
                                    fieldData,
                                    abrWebDriver.getDriver());
                            System.out.println(result);
                            result = performAction.sequenceOfCommands(
                                    abrWebElement.getElement(),
                                    ABRConstants.INSERT,
                                    coordinates,
                                    fieldData,
                                    abrWebDriver.getDriver());
                            System.out.println(result);
                            result = performAction.sequenceOfCommands(
                                    abrWebElement.getElement(),
                                    ABRConstants.GET_VALUE,
                                    coordinates,
                                    fieldData,
                                    abrWebDriver.getDriver());
                            System.out.println(result);
                            result = performAction.sequenceOfCommands(
                                    abrWebElement.getElement(),
                                    ABRConstants.FOCUS,
                                    coordinates,
                                    fieldData,
                                    abrWebDriver.getDriver());
                            System.out.println(result);
                            result = performAction.sequenceOfCommands(
                                    abrWebElement.getElement(),
                                    ABRConstants.TAB,
                                    coordinates,
                                    fieldData,
                                    abrWebDriver.getDriver());
                            System.out.println(result);
                            result = performAction.sequenceOfCommands(
                                    abrWebElement.getElement(),
                                    ABRConstants.COORD_VISUALIZA,
                                    coordinates,
                                    fieldData,
                                    abrWebDriver.getDriver());
                            System.out.println(result);
                            result = performAction.sequenceOfCommands(
                                    abrWebElement.getElement(),
                                    ABRConstants.COORD_CLICK,
                                    coordinates,
                                    fieldData,
                                    abrWebDriver.getDriver());
                            System.out.println(result);
                            result = performAction.sequenceOfCommands(
                                    abrWebElement.getElement(),
                                    ABRConstants.COORD_INSERT,
                                    coordinates,
                                    fieldData,
                                    abrWebDriver.getDriver());
                            System.out.println(result);
                        }
                        //                                abrWebElement.getElement().click();
                    } catch (Exception e) {
                        performMessage.couldNotFindElement("No TagName");
                        return;
                    }
                } else {
                    ABRLogger.getInstance(ABRScannedElementPane.class)
                            .info("Double clicked the element: " + abrWebElement.getMainXPath());

                    currentBlockId = comboBoxBlocks.getValue().getExtraId();
                    String blockName = comboBoxBlocks.getValue().getText();

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

                    String nameWebElement = defineNameField.getText().trim();
                    if (Strings.isNullOrEmpty(nameWebElement)) {
                        nameWebElement = abrWebElement.getNameFieldTitle().trim();
                    }

                    Text variableText2Styled = new Text(nameWebElement);
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

                        BotJobLoadDTO botJobLoadDTO = performDataBase.loadBotJobById(this.botJob.getId());

                        if (botJobLoadDTO == null) {

                            variableText1Styled = new Text(String.format(
                                    "Check if you already have a Bot Job \"%\" Created!", this.botJob.getName()));
                            variableText1Styled.setStyle("-fx-font-size: 18px; -fx-fill: red;");

                            combinedTextContainer.getChildren().clear();
                            combinedTextContainer.getChildren().add(variableText1Styled);

                            performMessage.showAlertCombinedVBOX(
                                    Alert.AlertType.ERROR,
                                    "Bot Job DOES NOT EXIST",
                                    "Verify the Bot Job Name if have any: ",
                                    null,
                                    combinedTextContainer);

                            ABRLogger.getInstance(ABRScannedElementPane.class)
                                    .severe(String.format(
                                            "Check if you already have a Bot Job \"%\" Created!",
                                            this.botJob.getName()));
                            return;
                        }

                        // It Prevents Start without blocks
                        this.blockLoadList = performDataBase.loadBlocksByBotJobId(this.botJob.getId());
                        if (blockLoadList.isEmpty()) {

                            BlockDetailsDTO newBlockDetails = new BlockDetailsDTO();
                            newBlockDetails.setBlockName("Default Block");
                            newBlockDetails.setBlockDescription("  description");
                            newBlockDetails.setTypeId(1);
                            newBlockDetails.setActive(true);
                            newBlockDetails.setWait(3);

                            newBlockDetails.setBotJobId(botJob.getId());

                            currentBlockId = performDataBase.createNewBlock(newBlockDetails);

                            if (currentBlockId < 0) {
                                performAction.showAlert(
                                        Alert.AlertType.ERROR,
                                        "Error Creating new Block",
                                        "Verify the BVot Job Name if have any",
                                        "Check if you already have a Bot Job Created!");

                                ABRLogger.getInstance(Thread.class)
                                        .severe(String.format(
                                                "Error Creating a new Block for bot job Id %d\nCheck if you already have a Bot Job Created!",
                                                botJob.getId()));
                                return;
                            } else {

                                setBlockJob(
                                        ABRSharedResources.getInstance().getEntityById(BlockDTO.class, currentBlockId));
                                ABRLogger.getInstance(Thread.class)
                                        .info(String.format(
                                                "Created a new Block id %d for bot job Id %d",
                                                currentBlockId, botJob.getId()));
                            }

                            Platform.runLater(() -> {
                                refreshBlocks(true);
                            });
                        }
                        //                        else {
                        //                            if (blockLoadList.size() > 0 && this.blockJob == null) {
                        //                                currentBlockId = blockLoadList.get(0).getId();
                        //                                setBlockJob(
                        //
                        // ABRSharedResources.getInstance().getEntityById(BlockDTO.class, currentBlockId));
                        //                            } else if (this.blockJob != null) {
                        //                                currentBlockId = this.blockJob.getId();
                        //                            }
                        //                        }

                        String finalNameWebElement = nameWebElement;
                        Task<Void> handleEvent = new Task<>() {
                            @Override
                            protected Void call() throws Exception {
                                ABRLogger.getInstance(Task.class).info("THREAD: Started");

                                //                                if
                                // (Strings.isNullOrEmpty(abrWebElement.getMainXPath())) {
                                //                                    try {
                                //                                        abrWebElement.setMainXPath(
                                //                                                getXPath(abrWebDriver.getDriver(),
                                // abrWebElement.getElement()));
                                //
                                // abrWebDriver.dehighlightElement(abrWebElement.getElement());
                                //                                    } catch (Exception e) {
                                //                                        ABRLogger.getInstance(Task.class)
                                //                                                .severe("Cannot find the XPath for
                                // this Element ");
                                //                                    }
                                //                                }

                                //                            List<WebElement> elementList =
                                // abrWebDriver.getDriver().findElements((By.xpath(abrWebElement.getXPath()));
                                //                            ABRLogger.getInstance(Task.class)
                                //                                    .fine("THREAD: scan ended. Detected " +
                                // elementList.size() + "element(s)");
                                //                            ABRLogger.getInstance(Task.class).fine("THREAD:
                                // dehighlighting
                                // all elements of list");
                                //                            for (WebElement element : elementList) {
                                //                                ABRLogger.getInstance(Task.class).finer("THREAD:
                                // dehilighting " + element);
                                //                                abrWebDriver.dehighlightElement(element);
                                //                                ABRLogger.getInstance(Task.class).finer("THREAD:
                                // dehilighted " + element);
                                //                            }
                                ABRLogger.getInstance(Task.class)
                                        .fine("THREAD: fetching instruction list from database");
                                ObservableList<BlockLoopInstructionDTO> list = ABRSharedResources.getInstance()
                                        .getEntityList(
                                                BlockLoopInstructionDTO.class,
                                                (instr) -> instr.getBlock().getId() == currentBlockId);
                                ABRLogger.getInstance(Task.class).finer("THREAD: instruction list size " + list.size());

                                String actionReq = checkClickElement.isSelected()
                                        ? "CLICK"
                                        : checkInputText.isSelected()
                                                ? "INPUT"
                                                : checkOutputText.isSelected() ? "OUTPUT" : "OTHER";
                                BlockLoopInstructionDTO instruction = abrWebElement.buildBlockLoopInstruction(
                                        abrWebElement.getForceTagEnum(),
                                        actionReq,
                                        checkActiveHover.isSelected(),
                                        list.size());

                                instruction.setBlock(blockJob);
                                instruction.setInstructionOrderNumber(list.size() + 1);

                                ABRLogger.getInstance(Task.class).fine("THREAD: adding instruction to database");
                                //                                ABRSharedResources.getInstance()
                                //                                        .addEntity(instruction,
                                // BlockLoopInstructionDTO.class, () -> {

                                Integer currentBotJobId = botJob.getId();

                                // Change the Name on the fly
                                if (!Strings.isNullOrEmpty(finalNameWebElement)) {
                                    instruction.setName(finalNameWebElement);
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
                                        instruction.getPath(),
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

                                abrWebElement.setInstructionId(instruction.getId());
                                List<InstructionReferenceLoadDTO> queue = new ArrayList<>();
                                for (String key :
                                        abrWebElement.getSavedReferences().keySet()) {
                                    InstructionReferenceLoadDTO reference = new InstructionReferenceLoadDTO();
                                    reference.setReferenceType(key);
                                    reference.setValue(
                                            abrWebElement.getSavedReferences().get(key));

                                    reference.setBotJobId(currentBotJobId);

                                    //
                                    // reference.setBlockLoopInstructionDTO(instruction);
                                    queue.add(reference);
                                }
                                try {

                                    Platform.runLater(() -> {
                                        boolean saved = insertReferences(queue, instruction.getId());
                                        if (saved) {

                                            Text blockNameLabel = new Text("Block : ");
                                            blockNameLabel.setStyle("-fx-font-size: 18px; -fx-fill: blue;");

                                            Text blockNameText = new Text(blockName);
                                            blockNameText.setStyle("-fx-font-size: 18px; -fx-fill: green;");

                                            Text variableText1Styled =
                                                    new Text("The Web Instruction \"" + instruction.getName() + "\"");
                                            variableText1Styled.setStyle("-fx-font-size: 18px; -fx-fill: blue;");

                                            Text variableText2Styled =
                                                    new Text("With " + queue.size() + " reference locators");
                                            variableText2Styled.setStyle("-fx-font-size: 18px; -fx-fill: blue;");

                                            Text variableText3Styled = new Text("Has been added successfully!");
                                            variableText3Styled.setStyle("-fx-font-size: 18px; -fx-fill: blue;");

                                            VBox combinedTextContainer = new VBox();
                                            combinedTextContainer.setSpacing(5); // Add some sp

                                            combinedTextContainer
                                                    .getChildren()
                                                    .addAll(
                                                            blockNameLabel,
                                                            blockNameText,
                                                            variableText1Styled,
                                                            variableText2Styled,
                                                            variableText3Styled);

                                            botJobLoadList = performDataBase.loadBotJobComplete(currentBotJobId);
                                            if (botJobLoadList.size() > 0) {
                                                List<BlockLoopInstructionLoadDTO> blockLoopInstructions =
                                                        performDataBase.buildJsonViewData(botJobLoadList);

                                                String jsonData = gson.toJson(blockLoopInstructions);
                                                broadcastMessageToAll(jsonData);
                                            }

                                            performMessage.showAlertCombinedVBOX(
                                                    Alert.AlertType.INFORMATION,
                                                    "Web Instruction Add",
                                                    "Added New \"Web Instruction\" Instruction",
                                                    null,
                                                    combinedTextContainer);

                                        } else {

                                            Text variableText1Styled =
                                                    new Text("The Instruction " + instruction.getName() + " with "
                                                            + queue.size() + " reference locators"
                                                            + "\nWas Added!"
                                                            + "\nTHE ENGINE IS GOING TO FAIL FOR THIS ELEMENT");
                                            variableText1Styled.setStyle("-fx-font-size: 18px; -fx-fill: red;");

                                            VBox combinedTextContainer = new VBox();
                                            combinedTextContainer.setSpacing(5); // Add some sp

                                            combinedTextContainer.getChildren().add(variableText1Styled);

                                            performMessage.showAlertCombinedVBOX(
                                                    Alert.AlertType.ERROR,
                                                    "Web Instruction Failed",
                                                    "Add Web Instruction FAILED",
                                                    null,
                                                    combinedTextContainer);
                                        }
                                    });
                                } catch (Exception ex) {
                                    ABRLogger.getInstance(Task.class).severe("Error Adding Instruction elements");
                                }
                                //                                        });
                                return null;
                            }
                        };
                        ABRLogger.getInstance(ABRScannedElementPane.class).fine("Thread created");
                        ABRLogger.getInstance(ABRScannedElementPane.class).fine("Before thread execution");
                        new Thread(handleEvent).start();
                        ABRLogger.getInstance(ABRScannedElementPane.class).fine("After thread execution");
                    }
                }
            }
        };
        abrWebElement.addEventHandler(MouseEvent.MOUSE_ENTERED, mouseEnteredHandler);
        abrWebElement.addEventHandler(MouseEvent.MOUSE_EXITED, mouseExitedHandler);
        abrWebElement.addEventHandler(MouseEvent.MOUSE_CLICKED, mouseClickedHandler);
    }

    private Set<WebElement> managePrioritiesCriteria() {

        // Gets Always the Latest info form DB
        databaseUserDto = loadUserData(botJob.getHomeBanking().getId());
        abrPriorities.loadSearchElementsConfig(databaseUserDto.getSearchConfig());

        Set<WebElement> elementsResponse = new HashSet<>();
        if (abrPriorities.getSearchConfigList() == null) {
            System.out.println("Is going to Search using \"searchConfigTemplate\"!  Please Add to the DB");
            return null;
        }
        if (abrPriorities.getSearchConfigList().size() > 0) {

            // Fetch the HTML content of the page
            Document docJSoup = null;
            docJSoup = JsoupConnect(botJob.getHomeBanking().getUrl());
            Set<WebElementWrapper> uniqueWrapperElements = new HashSet<>();
            List<WebElement> finalList = new ArrayList<>();
            Set<WebElement> uniqueWebElements = new HashSet<>();
            for (com.allinweb.ch.util.SearchConfig searchConfig : abrPriorities.getSearchConfigList()) {
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
                                        abrWebDriver.getDriver().findElements((By.xpath(name)));
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
                                            abrWebDriver.getDriver().findElements((By.tagName(name)));
                                    uniqueWebElements.addAll(searchingElems);
                                    // Add elements from the first list to the set
                                    for (WebElement element : uniqueWebElements) {
                                        String labelText = element.getText();
                                        String associatedText = "";

                                        // Get the value of the 'for' attribute
                                        String forAttribute = element.getAttribute("for");
                                        if (forAttribute != null) {
                                            // Find the associated element using the 'for' attribute value
                                            WebElement associatedElement =
                                                    abrWebDriver.getDriver().findElement(By.id(forAttribute));
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
                                            abrWebDriver.getDriver().findElements((By.tagName(name)));
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
                                List<WebElement> searchingElems = searchAllInputs(abrWebDriver.getDriver());
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
                                List<WebElement> searchingElems =
                                        abrWebDriver.getDriver().findElements((By.cssSelector("[" + name + "]")));
                                uniqueWebElements.addAll(searchingElems);
                                //                                List<WebElement> elements2 = webElements =
                                // abrWebDriver
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
                                        WebElement associatedElement =
                                                abrWebDriver.getDriver().findElement(By.id(forAttribute));
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
                            // abrWebDriver.getDriver().findElements((By.cssSelector("input[" + name + "]")));
                            //                                //                                List<WebElement>
                            // elements2 = webElements =
                            //                                // abrWebDriver
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
                            // abrWebDriver.getDriver().findElement(By.id(forAttribute));
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
                        // abrWebDriver.getDriver().findElements((By.cssSelector("[" +
                        // searchConfig.getName() + "]"));
                        //                            webElements = abrWebDriver
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
                                    abrWebDriver.getDriver().findElements(new ByChained(locators));
                            uniqueWebElements.addAll(searchingElems);
                            for (WebElement element : uniqueWebElements) {
                                String labelText = element.getText();
                                String associatedText = "";

                                // Get the value of the 'for' attribute
                                String forAttribute = element.getAttribute("for");
                                if (forAttribute != null) {
                                    // Find the associated element using the 'for' attribute value
                                    WebElement associatedElement =
                                            abrWebDriver.getDriver().findElement(By.id(forAttribute));
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
                        // abrWebDriver.getDriver().findElements((By.cssSelector("[" +
                        // searchConfig.getName() + "]"));
                        //                            webElements = abrWebDriver
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
                        // ABRWebUtil.extractWebElementXPath(element));
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

    //    public void saveReferencesToFile(String filePath, List<ABRWebElement> elements) {
    //        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
    //            for (ABRWebElement element : elements) {
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

    private void buildPriorityReferences(List<ABRWebElement> elements) {
        Map<String, String> references = new HashMap<>();
        for (ABRWebElement abrElement : elements) {
            for (com.allinweb.ch.util.Priority priority : abrPriorities.getAllPriorityList()) {
                switch (priority.getPriorityType()) {
                    case attribute -> {
                        String attributeValue = abrElement
                                .getElement()
                                .getAttribute(priority.getName().get(0));
                        if (attributeValue != null && !attributeValue.isBlank()) {
                            references.put(priority.getName().get(0), attributeValue);
                        }
                    }
                    case xpath -> references.put(
                            priority.getName().get(0), ABRWebUtil.extractWebElementXPath(abrElement.getElement()));

                    case coordinates -> {
                        Rectangle coordinates = abrElement.getElement().getRect();
                        references.put(
                                priority.getName().get(0),
                                (coordinates.getX() + (coordinates.getWidth() / 2)) + ","
                                        + (coordinates.getY() + (coordinates.getHeight() / 2)));
                    }
                }
            }
            abrElement.getSavedReferences().putAll(references);
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

    private void periodicThread(WebDriver driver) {
        // JavaScript code to inject
        String jsCode = "(function () {"
                + "  var tooltip = document.createElement('div');"
                + "  tooltip.id = 'Martini-Is-Awesome';"
                + "  tooltip.style.position = 'absolute';"
                + "  tooltip.style.backgroundColor = 'rgba(255, 165, 0, 0.5)';"
                + "  tooltip.style.border = '1px solid #ccc';"
                + "  tooltip.style.padding = '10px';"
                + "  tooltip.style.borderRadius = '5px';"
                + "  tooltip.style.boxShadow = '0 2px 4px rgba(0, 0, 0, 0.2)';"
                + "  tooltip.style.fontFamily = 'Arial, sans-serif';"
                + "  tooltip.style.fontSize = '14px';"
                + "  tooltip.style.color = '#333';"
                + "  tooltip.style.zIndex = '10000';"
                + "  tooltip.style.display = 'none';"
                + "  document.body.appendChild(tooltip);"
                + "  function getMartiniAbsoluteXPath(element) {"
                + "    if (element === document.body) {"
                + "      return '/html/' + element.tagName.toLowerCase();"
                + "    }"
                + "    var ix = 0;"
                + "    var siblings = element.parentNode.childNodes;"
                + "    for (var i = 0; i < siblings.length; i++) {"
                + "      var sibling = siblings[i];"
                + "      if (sibling === element) {"
                + "        return (getMartiniAbsoluteXPath(element.parentNode) + '/' + element.tagName.toLowerCase() + '[' + (ix + 1) + ']');"
                + "      }"
                + "      if (sibling.nodeType === 1 && sibling.tagName === element.tagName) {"
                + "        ix++;"
                + "      }"
                + "    }"
                + "    return '';"
                + "  }"
                + "  function getMartiniXPath(element) {"
                + "    if (element === document.body) {"
                + "      return '/html/body';"
                + "    }"
                + "    var ix = 0;"
                + "    var siblings = element.parentNode ? element.parentNode.childNodes : [];"
                + "    for (var i = 0; i < siblings.length; i++) {"
                + "      var sibling = siblings[i];"
                + "      if (sibling.nodeType === 1 && sibling.tagName === element.tagName) {"
                + "        if (sibling === element) {"
                + "          return (getMartiniXPath(element.parentNode) + '/' + element.tagName.toLowerCase() + '[' + (ix + 1) + ']');"
                + "        }"
                + "        ix++;"
                + "      }"
                + "    }"
                + "    return '';"
                + "  }"
                + "  function getMartiniCustomXPath(element) {"
                + "    if (element === document.body) {"
                + "      return '/html/' + element.tagName.toLowerCase();"
                + "    }"
                + "    var className = (typeof element.className === 'string' ? element.className : '')"
                + "      .split(' ')"
                + "      .filter(function (cls) {"
                + "        return !/\\d/.test(cls);"
                + "      })"
                + "      .join('.');"
                + "    var tagName = element.tagName.toLowerCase();"
                + "    var ix = 0;"
                + "    var siblings = element.parentNode.childNodes;"
                + "    for (var i = 0; i < siblings.length; i++) {"
                + "      var sibling = siblings[i];"
                + "      if (sibling === element) {"
                + "        var path = getMartiniCustomXPath(element.parentNode) + '/' + tagName;"
                + "        if (className) {"
                + "          path += '[contains(@class, \"' + className + '\")]';"
                + "        } else {"
                + "          path += '[' + (ix + 1) + ']';"
                + "        }"
                + "        return path;"
                + "      }"
                + "      if (sibling.nodeType === 1 && sibling.tagName === element.tagName) {"
                + "        ix++;"
                + "      }"
                + "    }"
                + "    return '';"
                + "  }"
                + "  function showMartiniTooltip(event) {"
                + "    var elementBelowTooltip = document.elementFromPoint(event.clientX, event.clientY);"
                + "    window.tagNameTemp = elementBelowTooltip.tagName.toLowerCase();"
                + "    window.coordsTemp = elementBelowTooltip.getBoundingClientRect();"
                + "    window.coordsTemp = window.coordsTemp.left + ',' + window.coordsTemp.top;"
                + "    tooltip.textContent = window.tagNameTemp + '-Coordinates:(' + window.coordsTemp + ')';"
                + "    var tooltipWidth = tooltip.offsetWidth;"
                + "    var tooltipHeight = tooltip.offsetHeight;"
                + "    var left = event.pageX - tooltipWidth / 2;"
                + "    var top = event.pageY - tooltipHeight / 2;"
                + "    tooltip.style.left = left + 'px';"
                + "    tooltip.style.top = top + 'px';"
                + "    tooltip.style.display = 'block';"
                + "  }"
                + "  function hideMartiniTooltip() {"
                + "    tooltip.style.display = 'none';"
                + "  }"
                + "  function handleMartiniClick(event) {"
                + "    event.preventDefault();"
                + "    event.stopPropagation();"
                + "    tooltip.style.display = 'none';"
                + "    var elementBelowTooltip = document.elementFromPoint(event.clientX, event.clientY);"
                + "    tooltip.style.display = 'block';"
                + "    cleanOldValues();"
                + "    if (elementBelowTooltip.tagName.toLowerCase() === 'iframe') {"
                + "      var iframeXPath = getMartiniXPath(elementBelowTooltip);"
                + "      window.iFrameXPath = iframeXPath;"
                + "      var iframeDocument = elementBelowTooltip.contentDocument || elementBelowTooltip.contentWindow.document;"
                + "      var iframeElements = iframeDocument.querySelectorAll('*');"
                + "      var iframeElementInfo = [];"
                + "      iframeElements.forEach(function (elementInsideIframe) {"
                + "        var iframeElementXPath = getMartiniXPath(elementInsideIframe);"
                + "        var someText = '';"
                + "        var tagName = elementInsideIframe.tagName.toLowerCase();"
                + "        if (tagName === 'input' || tagName === 'textarea' || tagName === 'select' || tagName === 'button') {"
                + "          someText = elementInsideIframe.value.trim() || elementInsideIframe.placeholder.trim() || '';"
                + "        } else if (tagName === 'option') {"
                + "          someText = elementInsideIframe.textContent.trim() || '';"
                + "        } else if (tagName === 'html' || tagName === 'body' || tagName === 'script') {"
                + "          someText = '';"
                + "        } else {"
                + "          someText = elementInsideIframe.textContent.trim() || elementInsideIframe.innerText.trim() || '';"
                + "        }"
                + "        var elementInfoString = 'tagName:' + elementInsideIframe.tagName.toLowerCase() + ';xpath:' + iframeElementXPath + ';text:' + someText;"
                + "        iframeElementInfo.push(elementInfoString);"
                + "      });"
                + "      console.log('iFrameXPath', window.iFrameXPath);"
                + "      console.log('List of iframe elements:', iframeElementInfo);"
                + "      window.iframeElements = iframeElementInfo;"
                + "    } else {"
                + "      window.attribId = elementBelowTooltip.id || '';"
                + "      window.attribName = elementBelowTooltip.name || '';"
                + "      window.tagName = elementBelowTooltip.tagName.toLowerCase();"
                + "      window.coords = elementBelowTooltip.getBoundingClientRect();"
                + "      window.coords = window.coords.left + ',' + window.coords.top;"
                + "      if (elementBelowTooltip.tagName.toLowerCase() === 'input' || elementBelowTooltip.tagName.toLowerCase() === 'textarea') {"
                + "        window.text = elementBelowTooltip.value || '';"
                + "      } else {"
                + "        window.text = elementBelowTooltip.textContent.trim() || '';"
                + "      }"
                + "      var xpath = getMartiniXPath(elementBelowTooltip);"
                + "      var absoluteXPath = getMartiniAbsoluteXPath(elementBelowTooltip);"
                + "      var customXPath = getMartiniCustomXPath(elementBelowTooltip);"
                + "      window.currentXPath = xpath;"
                + "      window.currentAbsoluteXPath = absoluteXPath;"
                + "      window.customXPath = customXPath;"
                + "      console.log('tagName', window.tagName);"
                + "      console.log('Current XPath:', window.currentXPath);"
                + "      console.log('Absolute XPath:', absoluteXPath);"
                + "      console.log('Custom XPath:', customXPath);"
                + "      console.log('Extracted Text:', window.text);"
                + "    }"
                + "  }"
                + "  function cleanOldValues() {"
                + "    window.iFrameXPath = '';"
                + "    window.iframeElements = [];"
                + "    window.currentXPath = '';"
                + "    window.currentAbsoluteXPath = '';"
                + "    window.customXPath = '';"
                + "    window.attribId = '';"
                + "    window.attribName = '';"
                + "    window.tagName = '';"
                + "    window.coords = '';"
                + "    window.tagNameTemp = '';"
                + "    window.coordsTemp = '';"
                + "    window.text = '';"
                + "  }"
                + "  cleanOldValues();"
                + "  document.addEventListener('mouseover', showMartiniTooltip);"
                + "  document.addEventListener('click', handleMartiniClick);"
                + "  window.removeClickListener = function () {"
                + "    document.removeEventListener('mouseover', showMartiniTooltip);"
                + "    document.removeEventListener('click', handleMartiniClick);"
                + "  };"
                + "})();";

        // Inject the JavaScript into the webpage
        jsExecutor = (JavascriptExecutor) driver;

        //        File scriptFile = new File("path/to/tooltipScript.js");

        String scriptContent = null;
        try {
            scriptContent = loadScriptFromResource("tooltipScript.js");
            //            scriptContent = new String(Files.readAllBytes(scriptFile.toPath()), StandardCharsets.UTF_8);

            jsExecutor.executeScript(scriptContent);
        } catch (IOException e) {
            jsExecutor.executeScript(jsCode);
            e.printStackTrace();
        }

        // Start a thread to periodically check the XPath value and update the TextField
        new Thread(() -> {
                    while (periodicActivated) {
                        //                        String currentXPath = (String) jsExecutor.executeScript("return
                        // window.currentXPath;");

                        // Execute JavaScript to construct and return a custom object
                        LinkedHashMap<String, Object> linkedHashMap = (LinkedHashMap<String, Object>)
                                jsExecutor.executeScript(
                                        "var obj = { iFrameXPath: window.iFrameXPath, iframeElements: window.iframeElements, attribId: window.attribId, attribName: window.attribName, customXPath: window.customXPath, currentXPath: window.currentXPath, currentAbsoluteXPath: window.currentAbsoluteXPath, tagName: window.tagName, coords: window.coords }; return obj;");

                        // Convert the LinkedHashMap to a Java Map (if necessary)
                        Map<String, Object> resultMap = new LinkedHashMap<>(linkedHashMap);

                        // Loop through resultMap and print each entry
                        //                        for (Map.Entry<String, Object> entry : resultMap.entrySet()) {
                        //                            if (!Strings.isNullOrEmpty(entry.getValue().toString())) {
                        //                                System.out.println("Key: " + entry.getKey() + ", Value: "
                        //                                        + entry.getValue().toString());
                        //                            }
                        //                        }

                        if (linkedHashMap != null) {
                            Platform.runLater(() -> {
                                iFrameXPath = (String) resultMap.get("iFrameXPath");

                                Object iframeElementsObject = resultMap.get("iframeElements");

                                if (iframeElementsObject instanceof List<?>) {
                                    // Convert List to String[]
                                    List<?> iframeElementsList = (List<?>) iframeElementsObject;
                                    iFrameElements = iframeElementsList.toArray(new String[0]);
                                } else if (iframeElementsObject instanceof Object[]) {
                                    // If it's an array, check if it's an array of Strings
                                    iFrameElements = Arrays.copyOf(
                                            (Object[]) iframeElementsObject,
                                            ((Object[]) iframeElementsObject).length,
                                            String[].class);
                                } else {
                                    System.out.println("The iframeElements data is not a List or an array.");
                                }

                                if (!Strings.isNullOrEmpty(iFrameXPath)) {
                                    StringBuilder sb = new StringBuilder();
                                    sb.append("iFrame: ").append(iFrameXPath).append("\n");

                                    if (iFrameElements != null && iFrameElements.length > 0) {
                                        sb.append("iFrame Elements: ").append("\n");
                                        // Print each element in iFrameElements
                                        for (String element : iFrameElements) {
                                            sb.append(element).append("\n");
                                        }
                                    }

                                    countdownTextField.setText(sb.toString());
                                    checkClickElement.setSelected(false);
                                    checkInputText.setSelected(false);
                                    checkOutputText.setSelected(false);
                                    checkFrameText.setSelected(true);
                                } else {
                                    checkFrameText.setSelected(false);
                                    countdownTextField.setText("");
                                }

                                attribIdTextField.setText((String) resultMap.get("attribId"));
                                attribNameTextField.setText((String) resultMap.get("attribName"));
                                currentXPathTextField.setText((String) resultMap.get("currentXPath"));
                                absolutXPathTextField.setText((String) resultMap.get("currentAbsoluteXPath"));
                                customXPathTextField.setText((String) resultMap.get("customXPath"));
                                originalTagNameField.setText((String) resultMap.get("tagName"));
                                coordsTextField.setText((String) resultMap.get("coords"));

                                if (!Strings.isNullOrEmpty(absolutXPathTextField.getText())
                                        && !xpathTextPrevious.equalsIgnoreCase(absolutXPathTextField.getText())) {
                                    extractValidateDynamic();
                                }

                                if (Strings.isNullOrEmpty(attribNameTextField.getText())) {
                                    attribNameTextField.setText(originalTagNameField.getText());
                                }
                            });
                        }
                        try {
                            Thread.sleep(500); // Check every 500 milliseconds
                        } catch (InterruptedException e) {
                            ABRLogger.getInstance(ABRScannedElementPane.class)
                                    .fine(String.format(
                                            "Error Attempt to get currentXPath / tagName / coords", e.getMessage()));
                        }
                    }
                })
                .start();
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

    private void revertInjectedChanges(WebDriver driver) {
        jsExecutor = (JavascriptExecutor) driver;

        // Remove the injected element
        jsExecutor.executeScript(
                "let elem = document.getElementById('Martini-Is-Awesome'); if (elem) { elem.remove(); }");

        jsExecutor.executeScript("window.removeClickListener();");

        // Reset the background color
        //        jsExecutor.executeScript("document.body.style.backgroundColor = '';");
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
        ABRLogger.getInstance(ABRScannedElementPane.class).fine("Number of input elements: " + inputElements.size());
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
                        + "                         left join bot_job bot on bot.home_banking_id = bank.id "
                        + " WHERE bank.id = " + bankId
                        + "                         group by bank.ID, bank.Name, bank.Url, bank.priority, bank.search_config, bank.options_config, bank.username, bank.password ";
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement();
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

    private void loadBotJob(BotJobDTO botJob) {
        String selectSQL =
                " SELECT bot.ID botId, bot.Name botName, blk.ID blockId, blk.Name blockName, blk.block_order_number, "
                        + " blockInstr.id blockInstrId, blockInstr.instruction_order_number instructionOrderNumber, blockInstr.actions, "
                        + " instr.id instId, instr.reference_type, instr.value"
                        + " FROM instruction_reference instr "
                        + " join block_loop_instruction blockInstr on blockInstr.id = instr.block_loop_instruction_id"
                        + " join bot_job bot on bot.id = " + botJob.getId()
                        + " join block blk on blk.bot_job_id = bot.id "
                        + " order by blockInstr.id, blockInstr.instruction_order_number, instr.id";
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(selectSQL)) {

            List<InstructionReferenceDTO> instructions = new ArrayList<>();

            while (rs.next()) {
                String botId = rs.getString("botId");
                String botName = rs.getString("botName");
                String blockId = rs.getString("blockId");
                String blockName = rs.getString("blockName");
                String blockOrderNumber = rs.getString("block_order_number");

                String blockInstrId = rs.getString("blockInstrId");
                String instructionOrderNumber = rs.getString("instructionOrderNumber");
                String actions = rs.getString("actions");

                String instId = rs.getString("instId");
                String referenceType = rs.getString("reference_type");
                String value = rs.getString("value");

                if (botJob.getId() == Integer.parseInt(botId)) {
                    for (BlockDTO block : botJob.getBlocks()) {
                        if (block.getId() == Integer.parseInt(blockId)) {
                            boolean exist = false;
                            for (BlockLoopInstructionDTO blockInstruction : block.getBlockLoopInstructionDTOS()) {
                                if (blockInstruction.getId() == Integer.parseInt(blockInstrId)) {
                                    for (InstructionReferenceDTO instructionReference :
                                            blockInstruction.getInstructionReferenceDTOList()) {
                                        if (instructionReference.getId() == Integer.parseInt(instId)
                                                && instructionReference
                                                        .getReferenceType()
                                                        .equalsIgnoreCase(referenceType)
                                                && instructionReference
                                                        .getValue()
                                                        .equalsIgnoreCase(value)) {
                                            exist = true;
                                            break;
                                        }
                                    }
                                    if (!exist) {
                                        InstructionReferenceDTO inst = new InstructionReferenceDTO();
                                        inst.setId(Integer.parseInt(instId));
                                        inst.setReferenceType(referenceType);
                                        inst.setValue(value);
                                        instructions.add(inst);
                                        break;
                                    }
                                }
                                if (exist) {
                                    break;
                                }
                            }
                        }
                    }
                }

                //                System.out.println(String.format(
                //                        "%s  %s  %s  %s  %s   %s   %s   %s",
                //                        botId, botName, blockId, blockName, blockOrderNumber, referenceType, value));

                //               databaseUserDto = new DatabaseUserDTO(
                //                        id, jobs, name, url, priority, searchConfig, optionsConfig, username,
                // password);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        //        jobUserList.clear();
        //        loadBotJobData();
    }

    private void recallJob() {
        executeJob();

        if (abrWebDriver.getDriver().getWindowHandles().size() != performAction.windowHandlesList.size()) {
            performAction.updateWindowHandlesList();
            updateButtonState();
        }

        // Review if Has Not Executed Instructions
        boolean hasUnexecutedInstructions = botJobLoadList.get(0).getBlockLoadDTOList().stream()
                .flatMap(block -> block.getBlockLoopInstructionLoadDTOS().stream())
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
        if (performAction.waitForPage == null) {
            String updateTimeout =
                    ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.WEBDRIVER_PAGE_UPDATE_TIMEOUT_SEC);
            String interactionTimeout =
                    ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.WEBDRIVER_PAGE_UPDATE_TIMEOUT_SEC);
            performAction.waitForPage =
                    new WebDriverWait(abrWebDriver.getDriver(), Duration.ofSeconds(Integer.parseInt(updateTimeout)));
            performAction.waitForAction = new WebDriverWait(
                    abrWebDriver.getDriver(), Duration.ofSeconds(Integer.parseInt(interactionTimeout)));
        }

        if (repository == null) {
            repository = new Repository(ABRSharedResources.getInstance().getSession());
        }
        try {
            baseLogFile = new File(ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.FOLDER_PATH_LOG)
                    + ABRConstants.FILE_NAME_SCANNER_BASE_LOG);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        List<BlockLoadDTO> blocksLoaded = botJobLoadList.get(0).getBlockLoadDTOList();
        String botJobName = botJobLoadList.get(0).getName();

        //        ABRPropertyManager managerProps = ABRPropertyManager.getInstance();
        String excelPath = ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.FOLDER_PATH_EXCEL);
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
                .flatMap(
                        blockLoadDTO -> blockLoadDTO
                                .getBlockLoopInstructionLoadDTOS()
                                .stream()) // Flatten the stream of BlockLoopInstructionLoadDTO
                .map(BlockLoopInstructionLoadDTO::getActions) // Extract the actions
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

        if (extractedData.getErrorMessage() != null) {

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
        //                .filter(action -> action.contains(ABRConstants.CLICK))
        //                .collect(Collectors.toSet());

        //        String browser = ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.BROWSER);
        //            WebPage webPage = new WebPage(browser, homeBankingDTO.getUrl());

        int botJobId = blocksLoaded.get(0).getBotJobId();

        // Original BotJobDTO
        //        BotJobDTO selectedJob = ABRSharedResources.getInstance().getEntityById(BotJobDTO.class, botJobId);

        String baseLogString = blocksLoaded.get(0).getBotJobName()
                + ABRConstants.FIELDS_SEPARATOR
                + labelsValue.getProperty(Labels.START);

        printBaseLog(baseLogFile, generateTimestamp(), baseLogString);

        ExcelWriter.ExcelChain writerReport =
                new ExcelWriter(botJobLoadList.get(0).getName(), abrWebDriver.getDriver(), false).withPurpose("report");
        writerReport.insertReportHead();

        ExcelWriter.ExcelChain writerExport = null;
        //                new ExcelWriter(blocksLoaded.get(0).getName(),
        // abrWebDriver.getDriver()).withPurpose("export");
        boolean excelExportOnceCreation = true;
        //        writerExport.insertReportHead();

        Set<String> mapIgnore = new HashSet<>();

        boolean searchByJavaScript = checkJavaScript.isSelected();

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

        ABRConstants.ConditionStatus currentCondition = ABRConstants.ConditionStatus.NONE;
        ABRConstants.ConditionStatus previousCondition;
        ABRConstants.ConditionStatus progressCondition;
        ABRConstants.DialogModal respModal;

        int exportIndex = 1;
        if (extractedData.getNumberOfDataRows() > 0) {

            // Execute All Blocks starting from executeSpecificBlock if Defined
            int currentBlock = (executeSpecificBlock > -1) ? executeSpecificBlock - 1 : 0;

            blockLoop:
            while (currentBlock <= blocksLoaded.size() - 1 && blocksLoaded.size() > 0 && !stopAll) {
                long blockStartTime = System.nanoTime();

                currentCondition = ABRConstants.ConditionStatus.NONE;
                previousCondition = ABRConstants.ConditionStatus.NONE;
                progressCondition = ABRConstants.ConditionStatus.NONE;

                respModal = ABRConstants.DialogModal.NONE;

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
                                performAction.logAndReport(
                                        currentCondition,
                                        true,
                                        true,
                                        blockStartTime,
                                        blockReportName,
                                        success,
                                        new String[] {ABRConstants.GOTO},
                                        msgBlock,
                                        dataExcel,
                                        writerReport,
                                        "GOTO Limit Reached",
                                        blocLoopKey + " Reached: 0");

                                msgBlock = new Pair(
                                        String.format("Exit at Block Name: \"%s\"", blockLoad.getName()),
                                        ABRConstants.EXIT);

                                // Excel Report and Log
                                performAction.logAndReport(
                                        currentCondition,
                                        true,
                                        true,
                                        blockStartTime,
                                        blockReportName,
                                        success,
                                        new String[] {ABRConstants.EXIT},
                                        msgBlock,
                                        dataExcel,
                                        writerReport,
                                        "Stopping App",
                                        String.format("Exit at Block Name: \"%s\"", blockName));

                                performAction.gotoLimitExecution(limit, resultActions);

                                continue blockLoop;
                            }
                        }
                    }
                }

                if (!blockActive) {
                    currentBlock++;

                    Pair<String, String> msgBlock =
                            new Pair(String.format("Ignore: \"%s\"", blockLoad.getName()), ABRConstants.IGNORE);

                    // Excel Report and Log
                    performAction.logAndReport(
                            currentCondition,
                            true,
                            true,
                            blockStartTime,
                            blockReportName,
                            success,
                            new String[] {ABRConstants.IGNORE},
                            msgBlock,
                            dataExcel,
                            writerReport,
                            "BLOCK IGNORED",
                            String.format("Block: \"%s\" is Inactive: ", blockName));

                    continue;
                }

                try {

                    Pair<String, String> msgBlock = new Pair(blockLoad.getName(), ABRConstants.EXCEL_BLOCK_HEADER);

                    // Block Header Format
                    performAction.logAndReport(
                            currentCondition,
                            true,
                            false,
                            blockStartTime,
                            blockReportName,
                            success,
                            new String[] {ABRConstants.EXCEL_BLOCK_HEADER},
                            msgBlock,
                            null,
                            writerReport,
                            null,
                            null);

                    performAction.onHoldInSeconds(blockWait);

                    msgBlock = new Pair(
                            String.format("Default Wait: \"%s\" ->  %d Seconds", blockLoad.getName(), blockWait),
                            ABRConstants.HOLD);

                    // Excel Report and Log
                    performAction.logAndReport(
                            currentCondition,
                            true,
                            true,
                            blockStartTime,
                            blockReportName,
                            success,
                            new String[] {ABRConstants.HOLD},
                            msgBlock,
                            dataExcel,
                            writerReport,
                            "BLOCK DEFAULT WAIT",
                            String.format("Block: \"%s\" Wait %s Seconds: ", blockName, blockWait));

                } catch (Exception ex) {
                    ABRLogger.getInstance(ABRScannedElementPane.class)
                            .severe(String.format("Error Wait Block for :\"%s\"", blockLoad.getName()));
                }

                // Step 1: Get all ParentIds For LOOPs Filter rows where actions = "REFRESH_LOOP" or "LOOP" on current
                // Block
                parentIdsForLoop = performAction.getParentIdsForLoop(
                        blocksLoaded.get(currentBlock).getBlockLoopInstructionLoadDTOS());

                // Step 2: Get all Conditional By parentId for Index Locator on current Block Relocate "IF", "ELSEIF",
                // "ELSE", and "ENDIF"
                mapConditional = performAction.getConditionIndexMapByParentId(blockLoad);

                // Step 3: Get all Instructions Ids on current Block
                int[] instructionIds = blockLoad.getBlockLoopInstructionLoadDTOS().stream()
                        .mapToInt(BlockLoopInstructionLoadDTO::getId)
                        .toArray();

                // Step 2: Filter rows where actions = "REFRESH_LOOP" or "LOOP" and collect into the map

                //                mapLoops = performAction.getLoopAndRefreshLoops(
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

                        BlockLoopInstructionLoadDTO currentInstruction =
                                blockLoad.getBlockLoopInstructionLoadDTOS().get(currentIndex);

                        byPassFlagLoop = parentIdsForLoop.contains(currentInstruction.getId());

                        mainMsg = currentInstruction.getOptional() ? "OPTIONAL INSTRUCTION" : "MANDATORY INSTRUCTION";

                        if (!currentInstruction.getInstructionActive()) {

                            String nameInstruc = "(" + currentInstruction.getId() + ") " + currentInstruction.getName();
                            Pair<String, String> msgBlock =
                                    new Pair(String.format("Ignore: \"%s\"", nameInstruc), ABRConstants.IGNORE);

                            // Excel Report and Log
                            performAction.logAndReport(
                                    currentCondition,
                                    true,
                                    true,
                                    blockStartTime,
                                    blockReportName,
                                    success,
                                    new String[] {ABRConstants.IGNORE},
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
                        // ABRConstants.ACTION_SPECIFICATIONS_SPLITTER);
                        String[] actions =
                                currentInstruction.getActions().split(ABRConstants.ACTION_SPECIFICATIONS_SPLITTER);
                        String[] operations = currentInstruction.getOperation() != null
                                ? currentInstruction.getOperation().split(ABRConstants.ACTION_SPECIFICATIONS_SPLITTER)
                                : null;

                        if (actions[0].equalsIgnoreCase(ABRConstants.IF)
                                || actions[0].equalsIgnoreCase(ABRConstants.ELSEIF)
                                || actions[0].equalsIgnoreCase(ABRConstants.ELSE)
                                || actions[0].equalsIgnoreCase(ABRConstants.ENDIF)) {
                            currentCondition = ABRConstants.ConditionStatus.valueOf(actions[0]);
                            if (previousCondition.equals(ABRConstants.ConditionStatus.NONE)) {
                                previousCondition = currentCondition;
                                parentBlockCondition = parentId;
                            } else if (!previousCondition.equals(
                                    currentCondition)) { // To Reset the Progress to the Next Block
                                previousCondition = currentCondition;
                            }

                            // Conditions When Pass to any of then
                            if (progressCondition.equals(ABRConstants.ConditionStatus.IF_PASSED)
                                    || progressCondition.equals(ABRConstants.ConditionStatus.ELSEIF_PASSED)) {
                                int jumpPassed = performAction.checkActionToJump(
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
                                    currentCondition = ABRConstants.ConditionStatus.NONE;
                                    progressCondition = ABRConstants.ConditionStatus.NONE;
                                    continue instructionLoop;
                                }
                            } else if (currentCondition.equals(ABRConstants.ConditionStatus.ENDIF)) {
                                currentCondition = ABRConstants.ConditionStatus.NONE;
                                previousCondition = ABRConstants.ConditionStatus.NONE;
                                progressCondition = ABRConstants.ConditionStatus.NONE;
                                parentBlockCondition = -1;
                            }
                            continue;
                        }

                        // Case for Inputs
                        String valueInsert = "No Data Found";
                        if (actions[0].equalsIgnoreCase(ABRConstants.INSERT)) {
                            String reference = actions[1];
                            valueInsert = dataExcel.get(reference);
                        }

                        Pair<String, String> msgInstruction = null;
                        if (actions[0].equalsIgnoreCase(ABRConstants.GOTO)) {
                            // <currentId:blockId:blockOrderNumber:bockName>
                            msgInstruction = performAction.getBlockDetailsById(blocksLoaded, currentInstruction);
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

                        } else if (actions[0].equalsIgnoreCase(ABRConstants.LOOP)) {
                            // <currentId:parentId:parentName>
                            msgInstruction = performAction.getInstructionDetailsById(
                                    blocksLoaded.get(currentBlock).getBlockLoopInstructionLoadDTOS(),
                                    currentInstruction);

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
                        } else if (actions[0].equalsIgnoreCase(ABRConstants.REFRESH_LOOP)) {
                            msgInstruction = performAction.getInstructionDetailsById(
                                    blocksLoaded.get(currentBlock).getBlockLoopInstructionLoadDTOS(),
                                    currentInstruction);
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
                        } else if (actions[0].equalsIgnoreCase(ABRConstants.SET_VALUE)
                                || (actions[0].equalsIgnoreCase(ABRConstants.GET_VALUE))) {
                            msgInstruction = new Pair(
                                    currentInstruction.getName(),
                                    (currentInstruction.getOperation() != null
                                            ? "(" + parentId + ")-" + operations[0] + ":" + operations[1]
                                            : (actions[0].equalsIgnoreCase(ABRConstants.INSERT)) ? valueInsert : ""));
                        } else {
                            msgInstruction = new Pair(
                                    "(" + currentInstruction.getId() + ")-" + currentInstruction.getName(),
                                    (currentInstruction.getOperation() != null
                                            ? currentInstruction.getOperation()
                                            : (actions[0].equalsIgnoreCase(ABRConstants.INSERT)) ? valueInsert : ""));
                        }

                        resultActions = performAction.actionResultMessage(blockName, actions, msgInstruction);

                        extraMsg = "";

                        if (actions[0].equalsIgnoreCase(ABRConstants.PAUSE)) {
                            pauseOperation = true;

                            respModal = performMessage.showCustomModalDialog(
                                    "PAUSE BOT JOB",
                                    String.format("PAUSE BOT JOB at Block Name:\"%s\"", blockLoad.getName()),
                                    " Please click OK to continue!",
                                    null,
                                    null,
                                    false,
                                    "stop all",
                                    0);
                        }

                        if (actions[0].equalsIgnoreCase(ABRConstants.LOOP)) {
                            parentFieldLoop = performAction.getInstructionParentField(currentInstruction, blockLoad);
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

                        } else if (actions[0].equalsIgnoreCase(ABRConstants.REFRESH_ONLY)) {
                            refreshOnly = true;
                        } else if (actions[0].equalsIgnoreCase(ABRConstants.REFRESH_LOOP)) {
                            parentFieldLoop = performAction.getInstructionParentField(currentInstruction, blockLoad);
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
                        } else if (actions[0].equalsIgnoreCase(ABRConstants.GET_VALUE)
                                || actions[0].equalsIgnoreCase(ABRConstants.SET_VALUE)) {

                            execGetOrSet = true;

                            getAction = actions[0].equalsIgnoreCase(ABRConstants.GET_VALUE);

                            xPathOperation = performAction.getXPathInstruction(currentInstruction, blockLoad);
                            parentField = performAction.getInstructionParentField(currentInstruction, blockLoad);

                        } else if (actions[0].equalsIgnoreCase(ABRConstants.CHECK_VALUE)) {
                            execCheckValue = true;
                            parentField = performAction.getInstructionParentField(currentInstruction, blockLoad);
                        } else if (actions[0].equalsIgnoreCase(ABRConstants.EXTRACT_FIELD)) {
                            excelWriteOperation = true;
                            parentField = performAction.getInstructionParentField(currentInstruction, blockLoad);
                        }

                        File logFileForSingleExcel = excelReader.createLogFile(excelPath);

                        fillUpCurretLocators(currentInstruction);

                        try {
                            if (jumpGoto) {

                                if (jumpGotoError) {
                                    resultActions = "Failed " + resultActions;

                                    success = false;

                                    resultActions = performAction.blockGotoFailed(resultActions);
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

                                            resultActions = performAction.blockGotoFailed(resultActions);
                                        }

                                        Pair<String, String> currentPair = new Pair(
                                                msgInstruction.getKey(),
                                                String.valueOf(mapLoops.get(msgInstruction.getKey())));

                                        // Excel Report and Log
                                        performAction.logAndReport(
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

                                        ABRLogger.getInstance(ABRScannedElementPane.class)
                                                .info(String.format(
                                                        "Loop to Parent :\"%s\" - %d Times",
                                                        parts[0] + "-(" + parts[1] + ") " + parts[2],
                                                        mapLoops.get(parentFieldLoop)));

                                        if (refreshLoop) {

                                            String extraLog = performAction.actionResultMessage(
                                                    blockName,
                                                    new String[] {ABRConstants.REFRESH_HOLD},
                                                    msgInstruction);

                                            performAction.performOtherActions(
                                                    byPassNotFound,
                                                    currentInstruction,
                                                    new String[] {ABRConstants.REFRESH_HOLD});

                                            // Excel Report and Log
                                            performAction.logAndReport(
                                                    currentCondition,
                                                    true,
                                                    true,
                                                    currentInstructionStartTime,
                                                    blockReportName,
                                                    success,
                                                    new String[] {ABRConstants.REFRESH_HOLD},
                                                    msgInstruction,
                                                    dataExcel,
                                                    writerReport,
                                                    mainMsg,
                                                    extraLog);

                                            // Refresh For REFRESH_LOOP
                                            extraLog = performAction.actionResultMessage(
                                                    blockName,
                                                    new String[] {ABRConstants.REFRESH_ONLY},
                                                    msgInstruction);

                                            performAction.performOtherActions(
                                                    byPassNotFound,
                                                    currentInstruction,
                                                    new String[] {ABRConstants.REFRESH_ONLY});

                                            // Excel Report and Log
                                            performAction.logAndReport(
                                                    currentCondition,
                                                    true,
                                                    true,
                                                    currentInstructionStartTime,
                                                    blockReportName,
                                                    success,
                                                    new String[] {ABRConstants.REFRESH_ONLY},
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
                                        performAction.logAndReport(
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
                                        ABRLogger.getInstance(ABRScannedElementPane.class)
                                                .info(String.format(
                                                        "IGNORING Loop to Parent :\"%s\" - %d Times",
                                                        parts[0] + "-(" + parts[1] + ") " + parts[2],
                                                        mapLoops.get(parentFieldLoop)));
                                        continue;
                                    }

                                } else {
                                    resultActions = performAction.parentValueIsNotDefined(
                                            currentInstruction.getName(),
                                            "(" + parentId + ")-" + parentField,
                                            resultActions);

                                    success = false;
                                }

                            } else if (refreshOnly) {

                                performAction.performOtherActions(byPassNotFound, currentInstruction, actions);

                                resultActions =
                                        "Refresh Current Web Page ->  inside Block :\"" + blockLoad.getName() + "\"";

                                refreshOnly = false;

                            } else if (actions[0].equals(ABRConstants.HOLD)
                                    || actions[0].equals(ABRConstants.QUIT)
                                    || actions[0].equals(ABRConstants.SCREEN)
                                    || actions[0].equals(ABRConstants.REFRESH_ONLY)) {

                                performAction.performOtherActions(byPassNotFound, currentInstruction, actions);

                                if (actions[0].equals(ABRConstants.QUIT)) {
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
                                Pair<String, String> fieldData = performAction.extractFieldData(
                                        dataExcel,
                                        actions,
                                        currentInstruction.getDefaultValue(),
                                        currentInstruction.getCodified());

                                WebElement webElementFound = null;
                                try {
                                    webElementFound =
                                            performAction.searchElement(currentInstruction, this.botJob.getId());
                                } catch (Exception ex) {
                                    extraMsg = "Element not found. Please try rescanning.!";
                                    success = false;
                                }

                                if (webElementFound == null && searchByJavaScript) {
                                    if (actions[0].equalsIgnoreCase(ABRConstants.VISUALIZE)
                                            || actions[0].equalsIgnoreCase(ABRConstants.CLICK)
                                            || actions[0].equalsIgnoreCase(ABRConstants.INSERT)) {
                                        success = performAction.executeActionsAtCoordinates(
                                                mapSavedLocators.get("coordinates"), fieldData, actions[0]);
                                    }
                                }

                                byPassNotFound =
                                        byPassFlagLoop || !currentCondition.equals(ABRConstants.ConditionStatus.NONE);

                                if (webElementFound != null && success) {

                                    success = performAction.performWebActions(
                                            byPassNotFound,
                                            mapSavedLocators.get("coordinates"),
                                            fieldData,
                                            currentInstruction,
                                            mapOperators,
                                            webElementFound,
                                            actions);

                                    if (actions[0].equalsIgnoreCase(ABRConstants.OUTPUT)) {
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
                                if (xPathOperation == null && actions[0].equalsIgnoreCase(ABRConstants.GET_VALUE)) {
                                    resultActions = performAction.parentIdWrongBlock(
                                            currentInstruction, blockLoad, resultActions, currentCondition);
                                    success = false;
                                } else if (parentField == null) {
                                    resultActions = performAction.parentIdWrongBlock(
                                            currentInstruction, blockLoad, resultActions, currentCondition);
                                    success = false;
                                } else if (!mapOperators.containsKey(parentField) && !getAction) {
                                    resultActions = performAction.getValueIsNotDefined(
                                            currentInstruction, resultActions, currentCondition);

                                    success = false;
                                } else {

                                    resultActions = performAction.performOperatorActions(
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
                                    resultActions = performAction.parentIdWrongBlock(
                                            currentInstruction, blockLoad, resultActions, currentCondition);

                                    resultActions = performAction.getValueIsNotDefined(
                                            currentInstruction, resultActions, currentCondition);

                                    success = false;

                                } else if (!mapOperators.containsKey(parentField)) {
                                    resultActions = performAction.getValueIsNotDefined(
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
                                        resultActions = performAction.checkValidationFailed(
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

                                    resultActions = performAction.parentIdWrongBlock(
                                            currentInstruction, blockLoad, resultActions, currentCondition);

                                    success = false;

                                } else if (!mapOperators.containsKey(parentField)) {
                                    resultActions = performAction.getValueIsNotDefined(
                                            currentInstruction, resultActions, currentCondition);

                                    success = false;
                                } else {

                                    if (excelExportOnceCreation) {
                                        //
                                        // writerExport.insertReportHead();
                                        excelExportOnceCreation = false;
                                    }

                                    if (!Strings.isNullOrEmpty(excelFieldName)) {
                                        writerExport = new ExcelWriter(excelFieldName, abrWebDriver.getDriver(), true)
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
                                    performAction.onHoldForSeconds(null);

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
                                && !currentCondition.equals(ABRConstants.ConditionStatus.NONE)) {
                            progressCondition = performAction.updateProgressSuccess(success, currentCondition);
                            //                                continue instructionLoop;
                        } else {
                            progressCondition = ABRConstants.ConditionStatus.NONE;
                        }

                        // Excel Report and Log
                        performAction.logAndReport(
                                !byPassFlagLoop ? progressCondition : ABRConstants.ConditionStatus.BY_PASS,
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

                        if (pauseOperation && respModal.equals(ABRConstants.DialogModal.STOP)) {

                            String nameInstruc = "(" + currentInstruction.getId() + ") " + currentInstruction.getName();

                            resultActions = String.format("STOP ALL PROCESSES: \"%s\"", nameInstruc);

                            Pair<String, String> msgBlock = new Pair(resultActions, ABRConstants.PAUSE);

                            // Excel Report and Log
                            performAction.logAndReport(
                                    currentCondition,
                                    true,
                                    true,
                                    blockStartTime,
                                    blockReportName,
                                    success,
                                    new String[] {ABRConstants.PAUSE},
                                    msgBlock,
                                    dataExcel,
                                    writerReport,
                                    "PAUSE -> STOP",
                                    String.format("STOP ALL CALLED AT: \"%s\" : ", nameInstruc));

                            respModal = ABRConstants.DialogModal.NONE;
                            stopAll = true;
                            break;
                        }

                        // It decides Here if ByPass as per Loop or Per IF-ELSEIF-ELSE-ENDIF blocks
                        if (!success && !byPassFlagLoop && currentCondition.equals(ABRConstants.ConditionStatus.NONE)) {
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
                        if (progressCondition.equals(ABRConstants.ConditionStatus.IF_PASSED)
                                || progressCondition.equals(ABRConstants.ConditionStatus.ELSEIF_PASSED)) {
                            int jumpPassed = performAction.checkActionToJump(
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
                                currentCondition = ABRConstants.ConditionStatus.NONE;
                                progressCondition = ABRConstants.ConditionStatus.NONE;
                                continue instructionLoop;
                            }
                        }

                        // Conditions When Fails to any of then and Look for the next Correct Block
                        if (progressCondition.equals(ABRConstants.ConditionStatus.IF_FAILED)
                                || progressCondition.equals(ABRConstants.ConditionStatus.ELSEIF_FAILED)) {

                            // Goes to the next ELSEIF IF EXIST (ELSEIF index + 1);
                            int index = performAction.searchMapConditional(
                                    mapConditional,
                                    parentBlockCondition,
                                    ABRConstants.ConditionStatus.ELSEIF,
                                    currentIndex,
                                    false);

                            // Goes to the next ELSE IF ELSEIF  DOES NOT EXIST  (ELSE index + 1);
                            if (index < 0) {
                                index = performAction.searchMapConditional(
                                        mapConditional,
                                        parentBlockCondition,
                                        ABRConstants.ConditionStatus.ELSE,
                                        currentIndex,
                                        true);
                            }
                            if (index < 0) {
                                stopAll = true;
                                continue blockLoop;
                            }
                            currentIndex = index;
                            currentCondition = ABRConstants.ConditionStatus.NONE;
                            progressCondition = ABRConstants.ConditionStatus.NONE;
                            continue instructionLoop;

                        } else if (progressCondition.equals(ABRConstants.ConditionStatus.ELSE_FAILED)) {
                            // Goes to the ENDIF (ENDIF index + 1);
                            int index = performAction.searchMapConditional(
                                    mapConditional,
                                    parentBlockCondition,
                                    ABRConstants.ConditionStatus.ENDIF,
                                    currentIndex,
                                    true);

                            if (index < 0) {
                                stopAll = true;
                                continue blockLoop;
                            }
                            currentIndex = index;
                            currentCondition = ABRConstants.ConditionStatus.NONE;
                            progressCondition = ABRConstants.ConditionStatus.NONE;
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
                List<BlockLoopInstructionLoadDTO> unexecutedInstructions = getUnexecutedInstructions(
                        instructionsExecuted, blocksLoaded.get(j).getBlockLoopInstructionLoadDTOS());

                for (BlockLoopInstructionLoadDTO currentInstruction : unexecutedInstructions) {
                    if (currentInstruction.getDefaultValue() == null) {
                        String[] arr = UtilsMethods.splitIfContains(
                                currentInstruction.getActions(), ABRConstants.ACTION_SPECIFICATIONS_SPLITTER);
                        if (arr.length > 1) {
                            String dataFieldName = arr[1].split(ABRConstants.PATH_FIELD_SUBSTITUTION)[0];
                            performAction.insertRandomName(dataFieldName);
                        }
                    }
                }
            }
            for (int j = 0; success && j < blocksLoaded.size(); j++) {

                String blockName = blocksLoaded.get(j).getName();
                int blockOrder = blocksLoaded.get(j).getBlockOrderNumber();
                String blockReportName = "#" + blockOrder + " " + blockName;

                // Call the method to get the filtered list
                List<BlockLoopInstructionLoadDTO> unexecutedInstructions = getUnexecutedInstructions(
                        instructionsExecuted, blocksLoaded.get(j).getBlockLoopInstructionLoadDTOS());

                for (BlockLoopInstructionLoadDTO currentInstruction : unexecutedInstructions) {

                    long currentInstructionStartTime = System.nanoTime();
                    File logFileForSingleExcel = excelReader.createLogFile(excelPath);

                    String[] actions = currentInstruction.getActions().split(ABRConstants.ACTIONS_AND_PATHS_SPLITTER);

                    // Case for Inputs
                    String valueInsert = "No Data Found";
                    if (actions[0].equalsIgnoreCase(ABRConstants.INSERT)) {

                        String reference = actions[1];
                        valueInsert = dataExcel.get(reference);
                    }

                    Pair<String, String> msgInstruction = new Pair(
                            currentInstruction.getName(),
                            (currentInstruction.getOperation() != null
                                    ? currentInstruction.getOperation()
                                    : (actions[0].equalsIgnoreCase(ABRConstants.INSERT)) ? valueInsert : ""));

                    resultActions = performAction.actionResultMessage(blockName, actions, msgInstruction);

                    try {

                        if (actions[0].equals(ABRConstants.HOLD)
                                || actions[0].equals(ABRConstants.QUIT)
                                || actions[0].equals(ABRConstants.SCREEN)
                                || actions[0].equals(ABRConstants.REFRESH_ONLY)) {
                            performAction.performOtherActions(byPassNotFound, currentInstruction, actions);

                            if (actions[0].equals(ABRConstants.QUIT)) {
                                stopAll = true;
                                success = true;
                            }

                            // Excel Report and Log
                            performAction.logAndReport(
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
                            webElementFound = performAction.searchElement(currentInstruction, this.botJob.getId());
                        } catch (Exception ex) {
                            extraMsg = "Element not found. Please try rescanning.!";
                        }

                        success = performAction.performWebActions(
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
                        performAction.logAndReport(
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
                        performAction.logAndReport(
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

        totalExecutionTime = performAction.getTotalExecutionTime();

        if (totalExecutionTime == 0) {
            writerReport.insertTotalExecutionTimes(botJobStartTime, botJobStartTime);
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

            writerReport.insertTotalExecutionTimes(botJobStartTime, System.nanoTime());

            baseLogString = blocksLoaded.get(0).getName()
                    + ABRConstants.FIELDS_SEPARATOR
                    + labelsValue.getProperty(Labels.END)
                    + ABRConstants.FIELDS_SEPARATOR
                    + labelsValue.getProperty(Labels.OK);

            combinedTextContainer
                    .getChildren()
                    .addAll(variableText1Styled, variableText2Styled, variableText3Styled, variableText4Styled);

            performMessage.showAlertCombinedVBOX(
                    Alert.AlertType.INFORMATION, "Success", "Execution Finished", null, combinedTextContainer);

        } else {
            countdownTextField.setStyle("-fx-font-size: 16px; -fx-text-fill: red;");
            countdownTextField.setText(resultActions);

            baseLogString = blocksLoaded.get(0).getName()
                    + ABRConstants.FIELDS_SEPARATOR
                    + labelsValue.getProperty(Labels.END)
                    + ABRConstants.FIELDS_SEPARATOR
                    + labelsValue.getProperty(Labels.KO)
                    + ABRConstants.FIELDS_SEPARATOR
                    + resultActions;
            //            report.setStatus(status);
            //            report.setDuration(totalExecutionTime / 100);
            writerReport.insertTotalExecutionTimes(botJobStartTime, System.nanoTime());

            variableText1Styled = new Text("Bot-Job Error");
            variableText1Styled.setStyle("-fx-font-size: 18px; -fx-fill: red;");

            variableText2Styled.setStyle("-fx-font-size: 18px; -fx-fill: blue;");

            variableText3Styled = new Text("Last Execution:");
            variableText3Styled.setStyle("-fx-font-size: 18px; -fx-fill: blue;");

            variableText4Styled = new Text(resultActions);
            variableText4Styled.setStyle("-fx-font-size: 18px; -fx-fill: red;");

            Text variableText5Styled = new Text(extraMsg);
            variableText5Styled.setStyle("-fx-font-size: 18px; -fx-fill: red;");

            combinedTextContainer
                    .getChildren()
                    .addAll(
                            variableText1Styled,
                            variableText2Styled,
                            variableText3Styled,
                            variableText4Styled,
                            variableText5Styled);

            performMessage.showAlertCombinedVBOX(
                    Alert.AlertType.ERROR, "FAIL", "Execution Failed", null, combinedTextContainer);
        }
        printBaseLog(baseLogFile, generateTimestamp(), baseLogString);
        return true;
    }

    private void clearFields() {
        absolutXPathTextField.setText("");
        currentXPathTextField.setText("");
        iFrameXPath = "";
        iFrameElements = null;
        coordsTextField.setText("");
        customXPathTextField.setText("");
        countdownTextField.setText("10");
        countdownTextField.setStyle("-fx-font-size: 18px; -fx-text-fill: blue;");
    }

    private void fillUpCurretLocators(BlockLoopInstructionLoadDTO currentInstruction) {
        for (InstructionReferenceLoadDTO reference : currentInstruction.getInstructionReferenceLoadDTOList()) {
            switch (reference.getReferenceType()) {
                case "absolutXPath":
                    absolutXPathTextField.setText(reference.getValue());
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
                    ABRLogger.getInstance(ABRScannedElementPane.class)
                            .fine("Unknown reference type: " + reference.getReferenceType());
            }
        }
    }

    public static List<BlockLoopInstructionLoadDTO> getUnexecutedInstructions(
            List<BlockLoopInstructionLoadDTO> instructionsExecuted, List<BlockLoopInstructionLoadDTO> otherList) {
        // Create a set of instructionOrderNumbers from instructionsExecuted
        Set<Integer> executedInstructionOrderNumbers = instructionsExecuted.stream()
                .map(BlockLoopInstructionLoadDTO::getInstructionOrderNumber)
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
        String log = String.join(ABRConstants.FIELDS_SEPARATOR, timeStamp, msg);

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
        String resultMsg = result ? ABRConstants.SUCCESS : ABRConstants.FAIL;
        String log = String.join(ABRConstants.FIELDS_SEPARATOR, timeStamp, resultMsg, resultActions);

        try {
            FileWriter fileWriter = new FileWriter(logFile, true);
            fileWriter.write(log + System.lineSeparator());
            fileWriter.close();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    private void executeAlert(BlockLoopInstructionDTO instruction) {
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
        String[] arr = UtilsMethods.splitIfContains(action, ABRConstants.ACTION_SPECIFICATIONS_SPLITTER);
        if (arr.length > 1) {
            fieldName = arr[1].split(ABRConstants.PATH_FIELD_SUBSTITUTION)[0];
        }

        new ExcelWriter(botJobName, abrWebDriver.getDriver(), false)
                .withPurpose("excel")
                .insertValueFieldName(parentId + "-" + fieldName, innerHTMLValue);
        return action + " fieldName " + fieldName;
    }

    public void quit(int status) {
        abrWebDriver.getDriver().quit();
        if (status == 0) {
            System.exit(status);
        }
        Close();
    }

    public List<ABRWebElement> createAdvancedABRElement(
            Map<String, WebElement> mapAdvanced, String attributeName, WebElementTagNameEnum typeElement) {
        List<ABRWebElement> listABRElements = new ArrayList<>();

        if (attributeName == null) {
            attributeName = "id";
        }
        if (!mapAdvanced.isEmpty()) {
            ABRLogger.getInstance(ABRScannedElementPane.class)
                    .fine(String.format("Advance Search Element with total of %s elements", mapAdvanced.size()));

            for (Map.Entry<String, WebElement> entry : mapAdvanced.entrySet()) {
                WebElement element = entry.getValue();
                String xpath = entry.getKey();
                String attributeValue = element.getAttribute(attributeName);

                if (Strings.isNullOrEmpty(attributeValue)) {
                    attributeValue = "(" + attributeName + ") has no value";
                }
                System.out.println("ABR Element Creation ->  Tag: " + element.getTagName() + ", " + attributeName + ": "
                        + attributeValue + ", XPath: " + xpath);

                try {

                    if (listABRElements.size() < 30) {
                        addProgressBar(1);
                    }
                    listABRElements.add(new ABRWebElement(entry, attributeName, botJob.getId(), typeElement));
                } catch (EnumConstantNotPresentException ex) {
                    throw ex;
                } catch (Exception ex) {
                    ABRLogger.getInstance(ABRScannedElementPane.class)
                            .fine(String.format(
                                    "Error attempt to create Advance Element  attribute: %s xPath: %s\nError: %s",
                                    attributeValue, xpath, ex.getMessage()));
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

                            // Remove elements on the JavaFX Application Thread
                            Platform.runLater(() -> {
                                if (bottomPane.getChildren().size() > 0) {
                                    bottomPane.getChildren().clear();
                                }
                            });
                        } catch (InterruptedException e) {
                            System.out.println(e.getMessage()); // Handle interruption
                        }
                    })
                    .start();
        }
        return listABRElements;
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
        jsExecutor = (JavascriptExecutor) driver;

        List<WebElement> elements = driver.findElements(By.xpath("//label[@for]"));
        Set<WebElement> uniqueElements = new HashSet<>(elements);

        elements = driver.findElements(By.xpath("//label[not(@for)]"));
        uniqueElements.addAll(elements);

        elements = driver.findElements(By.xpath("//label[normalize-space(text()) != '']"));
        uniqueElements.addAll(elements);

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

        Map<String, WebElement> elementMap = new HashMap<>();
        for (WebElement element : uniqueElements) {
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

    @Override
    public void start(Stage stage) throws Exception {
        ABRLogger.getInstance(ABRWebDriver.class).severe("start from ABRScannedElementPane");
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
                    ABRLogger.getInstance(ABRWebDriver.class).severe("ExecutorService did not terminate");
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
    //        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {
    //            stmt.executeUpdate(insertSQL);
    //            ABRLogger.getInstance(ABRScannedElementPane.class).info("Block data saved successfully id: " +
    // nextId);
    //            return nextId;
    //        } catch (SQLException e) {
    //            ABRLogger.getInstance(ABRScannedElementPane.class).severe("saveBlock  \nError: " + e.getMessage());
    //            return -1;
    //        }
    //    }

    private Integer loadNextIdBlockData() {
        //        String selectSQL = "SELECT NEXT_VAL fROM homeBankingSeq";
        String selectSQL = "SELECT MAX(ID) AS max_id FROM block";
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(selectSQL)) {
            while (rs.next()) {
                return rs.getInt("max_id");
            }
        } catch (SQLException e) {
            ABRLogger.getInstance(ABRScannedElementPane.class)
                    .severe("loadNextIdBlockData  \nError: " + e.getMessage());
        }
        return null;
    }

    private boolean insertReferences(List<InstructionReferenceLoadDTO> queue, int instructionId) {
        // Generate a Unique-ID for the block

        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {

            for (InstructionReferenceLoadDTO reference : queue) {

                Integer nextId = loadNextIdBReferenceData() + 1;

                // Build the SQL insert query
                String insertSQL =
                        "INSERT INTO instruction_reference(id, reference_type, value, block_loop_instruction_id, bot_job_id) VALUES ("
                                + nextId + ", "
                                + "'" + reference.getReferenceType() + "', "
                                + "'" + reference.getValue() + "', " // name
                                + instructionId + ","
                                + reference.getBotJobId() + ")"; // bot_job_id, assuming BotJobDTO has an ID

                int rowsAffected = stmt.executeUpdate(insertSQL);
                if (rowsAffected > 0) {
                    ABRLogger.getInstance(ABRScannedElementPane.class)
                            .info(String.format(
                                    "Instruction Reference SAVED SUCCESSFULLY\nid: %d\nRef Type: %s\nValue: %s\nInstructionId: %d",
                                    nextId, reference.getReferenceType(), reference.getValue(), instructionId));
                } else {
                    ABRLogger.getInstance(ABRScannedElementPane.class)
                            .warning(String.format(
                                    "Instruction Reference NOT SAVED\nid: %d\nRef Type: %s\nValue: %s\nInstructionId: %d",
                                    nextId, reference.getReferenceType(), reference.getValue(), instructionId));
                }
            }
            return true;
        } catch (SQLException e) {
            ABRLogger.getInstance(ABRScannedElementPane.class)
                    .severe("Cannot Insert References\nError " + e.getMessage());
            return false;
        }
    }

    private Integer loadNextIdBReferenceData() {
        //        String selectSQL = "SELECT NEXT_VAL fROM homeBankingSeq";
        String selectSQL = "SELECT MAX(ID) AS max_id FROM instruction_reference";
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(selectSQL)) {
            while (rs.next()) {
                return rs.getInt("max_id");
            }
        } catch (SQLException e) {
            ABRLogger.getInstance(ABRScannedElementPane.class)
                    .severe("loadNextIdBReferenceData  \nError: " + e.getMessage());
        }
        return null;
    }

    public void setBlockJob(BlockDTO blockJob) {
        this.blockJob = blockJob;
    }

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
            boolean exportToABR,
            String xPath,
            Integer currentBotJobId,
            Integer currentBlockId) {

        BlockLoopInstructionLoadDTO instructionDTO = new BlockLoopInstructionLoadDTO();

        instructionDTO.setName(name);

        instructionDTO.setCodified(false);

        instructionDTO.setInstructionOrderNumber(instructionOrderNumber);

        instructionDTO.setOptional(false);

        //        instructionDTO.setOperation(operation);
        instructionDTO.setActions(actions);
        instructionDTO.setDescription(description);

        instructionDTO.setVariableId(varId);

        instructionDTO.setActionCustomMaxWaitSec(30);
        instructionDTO.setOnHoldSeconds(onHold);
        //        instructionDTO.setBlock(savedBlockDTO);
        instructionDTO.setExportToABR(exportToABR);
        instructionDTO.setInstructionActive(true);

        instructionDTO.setPath(xPath);

        // Wrap the persistence in a try-catch block
        int newId = -1;

        try {
            newId = performDataBase.insertInstruction(instructionDTO, currentBotJobId, currentBlockId);

        } catch (Exception e) {

            ABRLogger.getInstance(ABRScannedElementPane.class)
                    .severe(String.format(
                            "Cannot Insert \"Instruction\"  \"%s\"\nCannot be saved!\nError: %s",
                            instructionDTO.getName(), e.getMessage()));

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
                "", ABRConstants.SPACE_L, ABRConstants.ICON_REFRESH, ABRConstants.SPACE_M, new Insets(3D));
        button.setMaxWidth(ABRConstants.SPACE_L);
        AnchorPane.setRightAnchor(button, 0D);
        return button;
    }

    private void broadcastMessageToAll(String message) {
        synchronized (sessions) {
            for (Session session : sessions) {
                if (session.isOpen()) {
                    sendMessageJson(session, "data_updated", message);
                }
            }
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
                ABRScannedElementPane.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }

            // Convert InputStream to String
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

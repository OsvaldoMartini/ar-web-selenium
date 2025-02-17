package com.allinweb.ch.component.pane;

import com.allinweb.ch.builder.WebElementAttributeEnum;
import com.allinweb.ch.builder.WebElementAttributeTypeValueEnum;
import com.allinweb.ch.builder.WebElementIcon;
import com.allinweb.ch.builder.WebElementTagNameEnum;
import com.allinweb.ch.component.listCell.ARCellFactory;
import com.allinweb.ch.component.listCell.ARWebElementListCell;
import com.allinweb.ch.component.model.BlockDetailsDTO;
import com.allinweb.ch.component.model.BlockLoadDTO;
import com.allinweb.ch.component.model.BlockLoopInstructionLoadDTO;
import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.model.ElementDTO;
import com.allinweb.ch.component.model.InstructionReferenceLoadDTO;
import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.component.scene.ARNewHomeBankingScene;
import com.allinweb.ch.control.ARComponentBuilder;
import com.allinweb.ch.core.ARSharedResources;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.driver.ARWebElement;
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
import org.openqa.selenium.support.pagefactory.ByChained;
import org.openqa.selenium.support.ui.WebDriverWait;

public class ARScannedElementPane extends ARPane {

    //    private Stage compStage;
    private final Gson gson = new Gson();
    private Set<Session> sessions;

    private int currentTabIndex = 0; // Track the currently active tab index
    public ARWebDriver arWebDriver;
    private Set<String> windowHandles;
    private WebElement previousElement = null;
    private WebElement currentElement = null;

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
    private ObservableList<ARWebElement> webElementObservableList1;
    private ObservableList<ARWebElement> webElementObservableList2;
    //    private ObservableList<ARWebElement> webElementObservableList3;
    //    private ObservableList<ARWebElementNew> webElementObservableList4;
    private ListView<ARWebElement> scannedElements1;
    private ListView<ARWebElement> scannedElements2;
    //    private ListView<ARWebElement> scannedElements3;
    //    private ListView<ARWebElementNew> scannedElements4;

    private Button scanIFrameButton;
    private Button addButtonNewElement;
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

    private CheckBox checkPickElement;
    private CheckBox checkCloneElement;

    private CheckBox checkTestAction;
    private CheckBox checkJavaScript;
    private CheckBox checkCoordinates;
    private CheckBox checkClickElement;
    private CheckBox checkInputText;
    private CheckBox checkOutputText;

    private Label defineNameLabel;
    private Label attribIdTextFieldLabel;
    private Label attribNameTextFieldLabel;
    private Label currentXPathLabel;
    private Label currentAllAttributesLabel;
    private Label customXPathLabel;
    private Label originalTagNameLabel;
    private Label coordsTextFieldLabel;

    private Text currentURL;
    private Text iFrameText;

    private TextField defineNameField;
    private TextField testActionsField;
    private TextArea countdownTextField;
    private TextField attribIdTextField;
    private TextField attribNameTextField;
    private TextField currentXPathTextField;
    private TextField allAttributesTextField;
    private TextField customXPathTextField;
    private TextField originalTagNameField;
    private TextField coordsTextField;
    private String xpathTextPrevious = "";
    private Boolean periodicPickActivated = false;
    private Boolean periodicCloneActivated = false;

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

    List<BlockLoopInstructionLoadDTO> instructionsExecuted = new ArrayList<>();
    List<Integer> executedSuccess = new ArrayList<>();
    Map<String, WebElement> mapAdvanced = new HashMap<>();

    // Very important sequence on initiation
    private static int reduceSearchCriteria;
    private static ARPropertyManager managerProps;
    private static ARPriorities arPriorities;
    private static final PerformMessage performMessage;
    private static final PerformActions performAction;
    private static final PerformDataBase performDataBase;
    private static final ARNewHomeBankingScene arNewHomeBankingScene;
    private static final IframeInputLocator iframeInputLocator;

    // Static block to initialize
    static {
        iframeInputLocator = IframeInputLocator.getInstance();
        performMessage = PerformMessage.getInstance();
        performDataBase = PerformDataBase.getInstance();
        performAction = PerformActions.getInstance();
        arPriorities = ARPriorities.getInstance();
        managerProps = ARPropertyManager.getInstance();
        arNewHomeBankingScene = ARNewHomeBankingScene.getInstance();
    }

    public ARWebDriver getAbrWebDriver() {
        return arWebDriver;
    }

    public ARScannedElementPane(BotJobDTO botJob, BlockDTO blockJob, ARWebDriver arWebDriver, Set<Session> sessions) {
        this.sessions = sessions;

        ARLogger.getInstance(ARWebDriver.class).fine("Calling ARScannedElementPane");

        // Ensure botJob and arPriorities are not null before accessing their methods
        if (botJob != null && arPriorities != null) {
            // Check if we need to update arPriorities
            if (arPriorities.getJobId() == null || !arPriorities.getJobId().equals(botJob.getId())) {
                // Set Job ID in arPriorities
                arPriorities.setJobId(botJob.getId());

                // Check for non-null HomeBanking and Priority
                if (botJob.getHomeBanking() != null) {
                    String priorityValue = botJob.getHomeBanking().getPriority();
                    String searchConfig = botJob.getHomeBanking().getSearchConfig();

                    if (priorityValue != null) {
                        arPriorities.loadPrioritiesFromString(priorityValue);
                    } else {
                        arPriorities.loadPriorities();
                    }

                    arPriorities.loadSearchElementsConfig(searchConfig);
                }

                // Initialize performAction with arPriorities and arWebDriver
                performAction.initializePerformActions(arPriorities, arWebDriver);
            }
        }

        // Assign instance variables
        this.botJob = botJob;
        this.blockJob = blockJob;
        this.arWebDriver = arWebDriver;
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

        //        if (arWebDriver.getDriver() == null) {
        //            arWebDriver = new ARWebDriver(); // Initialize WebDriver
        //        }
        arWebDriver.openDriver(
                botJob.getHomeBanking().getUrl(),
                botJob.getHomeBanking().getOptionsConfig().toString());

        performAction.getIframeElementsMap();

        handleWindowHandlesChange();

        topPane = componentBuilder.createTopPanel(ARConstants.SPACE_L, ARConstants.SPACE_SM);
        bottomPane = componentBuilder.createBottomPanel(ARConstants.SPACE_L, ARConstants.SPACE_SM);
        bottomPaneTime = componentBuilder.createBottomPanel(ARConstants.SPACE_L, ARConstants.SPACE_SM);
        contentPane =
                componentBuilder.createContentPanel(ARConstants.SPACE_L, ARConstants.SPACE_XL, ARConstants.SPACE_SM);

        scanIFrameButton = componentBuilder.buildButton(
                "iFrames", ARConstants.SPACE_L, ARConstants.ICON_SEARCH, ARConstants.SPACE_M, new Insets(5));
        addButtonNewElement = componentBuilder.buildButton(
                "Add", ARConstants.SPACE_L, ARConstants.ICON_TICK, ARConstants.SPACE_SM, new Insets(5));

        searchWithIdsButton = componentBuilder.buildButton(
                "With IDs", ARConstants.SPACE_ZERO, "/refresh.png", ARConstants.SPACE_M, new Insets(5.0D));
        searchWithNamesButton = componentBuilder.buildButton(
                "With Names", ARConstants.SPACE_ZERO, "/refresh.png", ARConstants.SPACE_M, new Insets(5.0D));
        searchWithoutIdsAndNamesBtn = componentBuilder.buildButton(
                "Input/Buttons (No IDs/Names)",
                ARConstants.SPACE_ZERO,
                "/refresh.png",
                ARConstants.SPACE_M,
                new Insets(5.0D));

        refreshInputFieldsButton = componentBuilder.buildButton(
                "Input Fields", ARConstants.SPACE_ZERO, "/refresh.png", ARConstants.SPACE_M, new Insets(5.0D));
        refreshOutputFieldsButton = componentBuilder.buildButton(
                "Output Fields", ARConstants.SPACE_ZERO, "/refresh.png", ARConstants.SPACE_M, new Insets(5.0D));
        refreshOtherFieldsButton = componentBuilder.buildButton(
                "Other Elements", ARConstants.SPACE_ZERO, "/refresh.png", ARConstants.SPACE_M, new Insets(5.0D));
        magicFieldsButton = componentBuilder.buildButton(
                "", ARConstants.SPACE_ZERO, "/magic2.png", ARConstants.SPACE_M, new Insets(5.0D));
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
        iFrameText = new Text("");
        iFrameText.setStyle("-fx-font-size: 12px; -fx-fill: blue;");

        webElementObservableList1 = FXCollections.observableArrayList();

        scannedElements1 = new ListView<>(webElementObservableList1);
        scannedElements1 = componentBuilder.setAnchorPaneAnchors(scannedElements1, ARConstants.SPACE_ZERO);
        scannedElements1.setCellFactory(new ARCellFactory<>(ARWebElementListCell.class)::call);

        webElementObservableList2 = FXCollections.observableArrayList();
        scannedElements2 = new ListView<>(webElementObservableList2);
        scannedElements2 = componentBuilder.setAnchorPaneAnchors(scannedElements2, ARConstants.SPACE_ZERO);
        scannedElements2.setCellFactory(new ARCellFactory<>(ARWebElementListCell.class)::call);

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

        countdownTextField = new TextArea("10");
        countdownTextField.setStyle("-fx-font-size: 12px; -fx-text-fill: blue;");
        countdownTextField.setEditable(false);

        checkCloneElement = new CheckBox("FORCE CLONE");
        checkPickElement = new CheckBox("PICK");

        defineNameLabel = new Label("DEFINE ELEMENT NAME");

        attribIdTextFieldLabel = new Label("Attrib Id Found");
        attribNameTextFieldLabel = new Label("Attrib Name Found");
        currentXPathLabel = new Label("XPath");
        currentAllAttributesLabel = new Label("All Attributes");
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
            webElementObservableList2.clear();
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
            gridPaneTop.add(scanIFrameButton, 0, 0);
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
            //        gridPaneTop.add(checkPickElement, 6, 0);
            //        gridPaneTop.add(addButtonNewElement, 7, 0);
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
            vBoxCheckBox.getChildren().addAll(boxCoordenates, checkInputText, checkOutputText, iFrameText);
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
            checkJavaScript.prefWidthProperty().bind(boxActions.widthProperty().multiply(0.10));
            testActionsField.prefWidthProperty().bind(boxActions.widthProperty().multiply(0.3));

            // Add elements to the HBox
            //            boxActions.getChildren().addAll(checkTestAction, checkJavaScript, testActionsField);
            boxActions.getChildren().addAll(checkTestAction, testActionsField);

            HBox hBoxPickClone = new HBox();
            hBoxPickClone
                    .getChildren()
                    .addAll(
                            createSpacerHoriz(),
                            checkCloneElement,
                            createSpacerHoriz(),
                            checkPickElement,
                            createSpacerHoriz());

            // Create the VBox for TextFields
            VBox textFieldVBox = new VBox();
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
                            boxActions,
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
            scannedElements1.prefHeightProperty().bind(boxListViews.heightProperty());
            scannedElements2.prefHeightProperty().bind(boxListViews.heightProperty());
            //            scannedElements3.prefHeightProperty().bind(boxListViews.heightProperty());
            //            scannedElements4.prefHeightProperty().bind(boxListViews.heightProperty());

            boxListViews.setSpacing(5);

            // Set Hgrow for each ListView to make them equally distributed
            HBox.setHgrow(scannedElements1, Priority.ALWAYS);
            HBox.setHgrow(scannedElements2, Priority.ALWAYS);
            //            HBox.setHgrow(scannedElements3, Priority.ALWAYS);
            //            HBox.setHgrow(scannedElements4, Priority.ALWAYS);

            StackPane stackCurrentURL = new StackPane();
            stackCurrentURL.getChildren().add(currentURL);
            stackCurrentURL.setAlignment(Pos.CENTER);
            HBox currentURLBox = new HBox(stackCurrentURL);

            Label labelInput = new Label("Input/IDs/Names(No Ids/Names)/Buttons");
            StackPane stackLabelInput = new StackPane();
            stackLabelInput.getChildren().add(labelInput);
            stackLabelInput.setAlignment(Pos.CENTER);
            VBox elements1VBox = new VBox(stackLabelInput, scannedElements1);
            HBox.setHgrow(elements1VBox, Priority.ALWAYS);

            //            Label labelOutput = new Label("Output Fields Results");
            //            StackPane stackLabelOutput = new StackPane();
            //            stackLabelOutput.getChildren().add(labelOutput);
            //            stackLabelOutput.setAlignment(Pos.CENTER);
            //            VBox elements2VBox = new VBox(stackLabelOutput, scannedElements2);
            //            HBox.setHgrow(elements2VBox, Priority.ALWAYS);

            Label labelOthers = new Label("Other Elements Results (Config)");
            StackPane stackLabelOthers = new StackPane();
            HBox othersBox = new HBox();
            createSpacerHoriz();
            othersBox.getChildren().addAll(labelOthers, createSpacerHoriz(), cleanListButton);
            stackLabelOthers.getChildren().addAll(othersBox);

            stackLabelOthers.setAlignment(Pos.CENTER);
            VBox elements2VBox = new VBox(stackLabelOthers, scannedElements2);
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
            boxListViews.getChildren().addAll(elements1VBox, elements2VBox, textFieldVBox);
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
        if (arWebDriver.getDriver().getWindowHandles().size() > 1 && currentTabIndex > 0) {
            // Decrease the index to move to the left
            currentTabIndex--;

            // Switch to the previous tab
            arWebDriver.getDriver().switchTo().window(performAction.windowHandlesList.get(currentTabIndex));
            updateSceneTitleWithCurrentURL(arWebDriver.getDriver().getCurrentUrl());

            // Disable the left button if we are at the first tab
            leftButton.setDisable(currentTabIndex == 0);

            // Enable the right button since we're no longer on the last tab
            rightButton.setDisable(false);
        }
    }

    // Switch to the next tab (right)
    private void switchToRightTab() {
        if (arWebDriver.getDriver().getWindowHandles().size() > 1
                && currentTabIndex < performAction.windowHandlesList.size() - 1) {
            // Increase the index to move to the right
            currentTabIndex++;

            // Switch to the next tab
            arWebDriver.getDriver().switchTo().window(performAction.windowHandlesList.get(currentTabIndex));
            updateSceneTitleWithCurrentURL(arWebDriver.getDriver().getCurrentUrl());

            // Disable the right button if we are at the last tab
            rightButton.setDisable(currentTabIndex == performAction.windowHandlesList.size() - 1);

            // Enable the left button since we're no longer on the first tab
            leftButton.setDisable(false);
        }
    }

    // Method to handle the scenario where the window handles size changes
    private void handleWindowHandlesChange() {
        Set<String> currentWindowHandles = arWebDriver.getDriver().getWindowHandles();

        // If the number of window handles has changed
        if (currentWindowHandles.size() != performAction.windowHandlesList.size()) {
            // Update the window handles list with the new handles
            performAction.updateWindowHandlesList();

            // Switch to the last window (most recent tab)
            currentTabIndex = performAction.windowHandlesList.size() - 1; // The last index in the list
            arWebDriver.getDriver().switchTo().window(performAction.windowHandlesList.get(currentTabIndex));

            // Update the scene title with the current URL of the last tab
            updateSceneTitleWithCurrentURL(arWebDriver.getDriver().getCurrentUrl());
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
        checkPickElement.setOnMouseClicked(e -> {
            arWebDriver.getDriver().switchTo().defaultContent();
            checkTestAction.setDisable(checkCloneElement.isSelected());
            checkTestAction.setSelected(false);

            checkCloneElement.setDisable(checkPickElement.isSelected());
            checkCloneElement.setSelected(false);

            xpathTextPrevious = "";
            this.targetSelected = null;
            elementsFound.clear();
            //            webElementObservableList2.clear();

            periodicPickActivated = checkPickElement.isSelected();

            handlePickElementClick();
        });
        checkCloneElement.setOnMouseClicked(e -> {
            arWebDriver.getDriver().switchTo().defaultContent();
            checkTestAction.setDisable(checkCloneElement.isSelected());
            checkTestAction.setSelected(false);

            checkPickElement.setDisable(checkCloneElement.isSelected());
            checkPickElement.setSelected(false);

            xpathTextPrevious = "";
            this.targetSelected = null;
            elementsFound.clear();
            //            webElementObservableList2.clear();

            periodicCloneActivated = checkCloneElement.isSelected();

            handleCloneElementClick();
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
                    webElementObservableList2.clear();

                    Optional<ElementDTO> iframeElement = elementsFound.stream()
                            .filter(element -> "clicked-iFrame".equalsIgnoreCase(element.getTypeElement()))
                            //                                || "clicked".equalsIgnoreCase(element.getTypeElement())
                            //                                ||
                            // "tagName-found".equalsIgnoreCase(element.getTypeElement()))
                            .findFirst(); // Get the first matching ElementDTO

                    if (checkPickElement.isSelected()) {
                        handlePickElementClick();
                    } else if (iframeElement.isPresent()) {
                        insertNewElement(iframeElement.get(), elementsFound);
                    } else {
                        insertNewElement(elementsFound);
                    }
                    this.targetSelected.setElement(null);

                } else {
                    performMessage.errorMessage(
                            "Not Web Element to be Detected!",
                            "Release -> Checkbox -> \"PICK ELEMENT\" or \"FORCE CLONE\"   again!",
                            "\"Refresh\" the Page -> <CTRL + F5>",
                            "Try  \"PICK ELEMENT\" or \"FORCE CLONE\"   again!",
                            null,
                            0);
                }
            }
        });

        refreshInputFieldsButton.setOnAction(e -> refreshInputBtn("Input Web Elements"));
        refreshOutputFieldsButton.setOnAction(e -> refreshOutputBtn("Output Web Elements"));
        refreshOtherFieldsButton.setOnAction(e -> refreshOtherElemBtn("Others Types of Web Elements"));
        magicFieldsButton.setOnAction(e -> performAction.createOutputHtml("input", arWebDriver.getDriver()));
        searchWithIdsButton.setOnAction(e -> refreshWithIdsBtn("Web Elements with Attribute ID"));
        searchWithNamesButton.setOnAction(e -> refreshWithNamesBtn("Web Elements with Attribute Name"));
        searchWithoutIdsAndNamesBtn.setOnAction(
                e -> refreshWithoutIdsAndNamesBtn("Web Elements without Attributes Name/ID"));

        scannedElements1.getItems().addListener(this::addBehaviourToAddedElements);
        scannedElements2.getItems().addListener(this::addBehaviourToAddedElements);
        //        scannedElements3.getItems().addListener(this::addBehaviourToAddedElements);

        //        manageUIScan();
    }

    private boolean lastBrowserTab() {
        // Get all window handles (all open tabs/windows)
        try {
            windowHandles = arWebDriver.getDriver().getWindowHandles();

            // Convert the window handles set to a list
            List<String> windowHandlesList = new ArrayList<>(windowHandles);

            // Switch to the last window (newly opened tab)
            arWebDriver.getDriver().switchTo().window(windowHandlesList.get(windowHandlesList.size() - 1));

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

            try {
                if (cloneTarget.getElement() != null) {

                    ARWebElement arWebElement = new ARWebElement(cloneTarget, botJob.getId());
                    if (arWebElement != null && arWebElement.getElement() != null) {
                        webElementObservableList2.add(arWebElement);
                        Platform.runLater(() -> {
                            scannedElements2.refresh();
                        });
                    }

                } else {
                    ARLogger.getInstance(ARScannedElementPane.class).severe("Could not find the Web Element!");
                    performMessage.errorMessage(
                            "Not Web Element to be Detected!",
                            "Release -> Checkbox -> \"PICK ELEMENT\" or \"FORCE CLONE\"   again!",
                            "\"Refresh\" the Page -> <CTRL + F5>",
                            "Try  \"PICK ELEMENT\" or \"FORCE CLONE\"   again!",
                            null,
                            0);
                }

            } catch (Exception ex) {
                ARLogger.getInstance(ARScannedElementPane.class)
                        .severe("Error Attempt to create a Dynamic Element\n" + ex.getMessage());
                performMessage.errorMessage(
                        "\"Error Attempt to create a Dynamic Element!",
                        "Release -> Checkbox -> \"PICK ELEMENT\" or \"FORCE CLONE\"   again!",
                        "\"Refresh\" the Page -> <CTRL + F5>",
                        "Try  \"PICK ELEMENT\" or \"FORCE CLONE\"   again!",
                        null,
                        0);
            }
        }
    }

    private void insertNewElement(ElementDTO iframeElementDTO, List<ElementDTO> elementsFound) {

        try {
            // Locate and switch to the iframe first
            WebElement iframe = arWebDriver.getDriver().findElement(By.xpath(iframeElementDTO.getXPath()));

            // I Need to create a new targetElement
            TargetElement targetIFrame = performAction.defineSearchReturn(iframeElementDTO, iframe, null);
            targetIFrame = performAction.defineTargetNameTitles(targetIFrame);

            arWebDriver.getDriver().switchTo().frame(iframe);

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

                ARWebElement arWebElement = new ARWebElement(targetIFrame, botJob.getId());
                if (arWebElement != null && arWebElement.getElement() != null) {
                    webElementObservableList2.add(arWebElement);
                    Platform.runLater(() -> scannedElements2.refresh());
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
                        || elementChild.getTagName().equalsIgnoreCase("script")
                        || elementChild.getTagName().equalsIgnoreCase("meta")
                        || elementChild.getTagName().equalsIgnoreCase("head")
                        || elementChild.getTagName().equalsIgnoreCase("body")) {
                    continue;
                }

                try {
                    WebElement elemFound = arWebDriver.getDriver().findElement(By.xpath(elementChild.getXPath()));

                    elementChild.setIFrameXPath(iframeElementDTO.getXPath());

                    TargetElement targetChild = performAction.defineSearchReturn(elementChild, elemFound, null);
                    targetChild = performAction.defineTargetNameTitles(targetChild);

                    ARWebElement arWebElement = new ARWebElement(targetChild, botJob.getId());

                    if (arWebElement != null && arWebElement.getElement() != null) {
                        webElementObservableList2.add(arWebElement);
                        Platform.runLater(() -> {
                            scannedElements2.refresh();
                        });
                    }

                    System.out.println(
                            "Found element: " + elemFound.getTagName() + " with XPath: " + elementChild.getXPath());
                } catch (Exception e) {
                    System.out.println("Element not found for XPath: " + elementChild.getXPath());
                    ARConstants.DialogModal respModal = performMessage.showCustomModalDialog(
                            "Fail Searching IFrame Elements",
                            "Error: Attempt identify IFrame elements",
                            "\"iFrame Web Elements\"",
                            "Action: Search iFrame Elements!",
                            null,
                            true,
                            "stop all",
                            0);
                    if (respModal.equals(ARConstants.DialogModal.STOP)) {
                        return;
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

                scannedElements1.requestLayout();
                scannedElements1.refresh();
                scannedElements2.requestLayout();
                scannedElements2.refresh();
                bottomPane.requestLayout();
            });

        } catch (Exception e) {
            browserNotAttached();
        } finally {

            Platform.runLater(() -> {
                scannedElements1.requestLayout();
                scannedElements1.refresh();
                scannedElements2.requestLayout();
                scannedElements2.refresh();
                bottomPane.requestLayout();
            });

            // Close the browser
            arWebDriver.getDriver().switchTo().defaultContent();
        }
    }

    private void insertNewElement(String iFrameXPath, List<ElementDTO> elementsFound) {

        try {
            // Locate and switch to the iframe first
            WebElement iframe = arWebDriver.getDriver().findElement(By.xpath(iFrameXPath));
            arWebDriver.getDriver().switchTo().frame(iframe);

            TargetElement targetFound = new TargetElement();
            // Loop through and find elements
            for (ElementDTO elementFound : elementsFound) { // Start from index 1

                if (elementFound.getTagName().equalsIgnoreCase("html")
                        || elementFound.getTagName().equalsIgnoreCase("script")
                        || elementFound.getTagName().equalsIgnoreCase("meta")
                        || elementFound.getTagName().equalsIgnoreCase("head")
                        || elementFound.getTagName().equalsIgnoreCase("body")) {
                    continue;
                }

                try {
                    WebElement element = arWebDriver.getDriver().findElement(By.xpath(elementFound.getXPath()));
                    //                                elements.add(element);

                    targetFound = performAction.defineSearchReturn(elementFound, element, targetFound);

                    ARWebElement arWebElement = new ARWebElement(targetFound, botJob.getId());
                    if (arWebElement != null && arWebElement.getElement() != null) {
                        webElementObservableList2.add(arWebElement);
                        Platform.runLater(() -> {
                            scannedElements2.refresh();
                        });
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
            arWebDriver.getDriver().switchTo().defaultContent();
        }
    }

    private void insertNewElement(List<ElementDTO> elementsDTO) {

        try {
            // Loop through and find elements
            for (ElementDTO elementDTO : elementsDTO) { // Start from index 1
                WebElement elementSearched = null;
                if (elementDTO.getTagName().equalsIgnoreCase("html")
                        || elementDTO.getTagName().equalsIgnoreCase("script")
                        || elementDTO.getTagName().equalsIgnoreCase("meta")
                        || elementDTO.getTagName().equalsIgnoreCase("head")
                        || elementDTO.getTagName().equalsIgnoreCase("body")) {
                    continue;
                }

                try {
                    WebElement elementFound = arWebDriver.getDriver().findElement(By.xpath(elementDTO.getXPath()));
                    //                                elements.add(element);

                    if (elementFound != null) {
                        // I Need to create a new targetElement
                        TargetElement targetLocal = performAction.defineSearchReturn(elementDTO, elementFound, null);

                        targetLocal = performAction.defineTargetNameTitles(targetLocal);

                        // First  Search for xPath
                        //                        TargetElement targetValidated =
                        // checkValidateSearchPriorities(targetLocal);

                        if (!elementDTO.getTagName().equalsIgnoreCase("clicked")) {

                            ARWebElement arWebElement = new ARWebElement(targetLocal, botJob.getId());
                            targetLocal.setElement(null);

                            if (arWebElement != null && arWebElement.getElement() != null) {
                                webElementObservableList2.add(arWebElement);
                                Platform.runLater(() -> {
                                    scannedElements2.refresh();
                                });
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
                    ARConstants.DialogModal respModal = performMessage.showCustomModalDialog(
                            "Error selecting Web Element",
                            "Mandatory Value not Defined",
                            "Not able defining the Name/Label for the New AR Element",
                            null,
                            null,
                            true,
                            "stop all",
                            0);

                    if (respModal.equals(ARConstants.DialogModal.STOP)) {
                        return;
                    }
                }
            }

            elementsDTO.clear();

        } catch (Exception e) {
            //            browserNotAttached();
        } finally {
            // Close the browser
            arWebDriver.getDriver().switchTo().defaultContent();
        }
    }

    private TargetElement extractPickClone(ElementDTO pickTarget) {

        xpathTextPrevious = pickTarget.getXPath();

        WebElement elementFound = arWebDriver.getDriver().findElement(By.xpath(pickTarget.getXPath()));

        TargetElement targetLocal = performAction.defineSearchReturn(pickTarget, elementFound, null);

        targetLocal = performAction.defineTargetNameTitles(targetLocal);

        // First  Search for xPath
        TargetElement targetValidated = checkValidateSearchPriorities(targetLocal);

        if (targetValidated.getElement() == null) {

            performMessage.errorMessage(
                    "I Cannot define this element",
                    "I will use the Locato \"COORDINATES\"",
                    "Try to get it again -> \"PICK ELEMENT\" or \"FORCE CLONE\"",
                    null,
                    null,
                    0);

            return null;
        }

        //        targetElement = performAction.defineTagType(targetElement);

        defineCheckBoxesClickabe(targetLocal);

        return targetLocal;
    }

    private void defineCheckBoxesClickabe(TargetElement targetCheck) {
        boolean clickable = isClickable(targetCheck.getElement());

        boolean tagClickable = false;
        // Define regex to extract specific tags (e.g., a, button)
        String regex = "/([^/\\[]+)";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(targetCheck.getAllAttributes());

        // Iterate through all matches and check for target tags
        while (matcher.find()) {
            String tag = matcher.group(1);
            if (tag.equals("a") || tag.equals("button")) {
                System.out.println("Found clickable tag: <" + tag + ">");
                tagClickable = true;
                break;
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
    //    targetElement = performAction.defineTargetNameTitles(targetElement);
    //    targetElement performAction.defineTagType(TargetElement targetTagType) {
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
            targetIFrames.setAllAttributes(xPathElementChild); // TO DO
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
                ARWebElement arWebElement = new ARWebElement(targetIFrames, botJob.getId());
                if (arWebElement != null && arWebElement.getElement() != null) {
                    webElementObservableList1.add(arWebElement);
                    Platform.runLater(() -> scannedElements1.refresh());
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
            if (elementValid == null) {
                try {
                    elementValid = arWebDriver.getDriver().findElement(By.xpath(target.getCurrentXPath()));
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
                    elementValid = arWebDriver.getDriver().findElement(By.xpath(target.getCustomXPath()));
                    if (elementValid != null && elementValid.getTagName() != null) {
                        target.setElement(elementValid);
                        target.setXPathWorkedFirst(
                                ARConstants.CUSTOM_XPATH); // BECAUSE OS LIMITATION OF ACCESS DB 255 CHARACTER
                    }
                } catch (Exception e) {
                    ARLogger.getInstance(ARScannedElementPane.class)
                            .warning(String.format(
                                    "Cannot locate a Web Element with Absolut XPath\n%s", target.getAllAttributes()));
                }
            } else {
                if (elementValid == null) {
                    //            if (searchReturn.getCurrentXPath().startsWith("id(")) {
                    if (!Strings.isNullOrEmpty(target.getAttribId())) {
                        try {
                            elementValid = arWebDriver.getDriver().findElement(By.id(target.getAttribId()));
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
                            elementValid = arWebDriver.getDriver().findElement(By.name(target.getAttribName()));
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

                } else if (elementValid == null) {
                    // Try by coordinates
                    try {
                        Pair<String, String> filedData = new Pair("&EMPTY", "&EMPTY");
                        boolean passed = performAction.executeActionsAtCoordinates(
                                target.getCoords(), filedData, ARConstants.VISUALIZE);
                        if (passed) {
                            elementValid = performAction.getElementFromCoordinates(target.getCoords());
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

    private void refreshWithoutIdsAndNamesBtn(String searchType) {
        webElementObservableList1.clear();
        manageUIScanWithoutNameAndId();
    }

    private void refreshWithNamesBtn(String searchType) {
        webElementObservableList1.clear();
        manageUIScanAttributeNameFirst();
    }

    private void refreshWithIdsBtn(String searchType) {
        webElementObservableList1.clear();
        manageUIScanIdsFirst();
    }

    private void handlePickElementClick() {
        revertCloneInjections(arWebDriver.getDriver());
        revertPickInjections(arWebDriver.getDriver());

        if (checkPickElement.isSelected()) {

            Platform.runLater(() -> periodicPickThread(
                    arWebDriver.getDriver(), arWebDriver.getDriver().getCurrentUrl()));
            //            injectJavaScript(arWebDriver.getDriver());
            //            injectJumpTab(arWebDriver.getDriver());
        }

        revertPickButtons();
    }

    private void handleCloneElementClick() {

        elementsFound.clear();

        revertCloneInjections(arWebDriver.getDriver());
        revertPickInjections(arWebDriver.getDriver());

        if (checkCloneElement.isSelected()) {
            Platform.runLater(() -> periodicCloneThread(arWebDriver.getDriver()));
            //            injectJavaScript(arWebDriver.getDriver());
            //            injectJumpTab(arWebDriver.getDriver());
        }
        revertCloneButtons();
    }

    private void revertPickButtons() {
        Platform.runLater(() -> {
            checkTestAction.setDisable(checkPickElement.isSelected());
            launchBotJobButton.setDisable(checkPickElement.isSelected());
            recallJobButton.setDisable(checkPickElement.isSelected());

            checkCloneElement.setDisable(checkPickElement.isSelected());

            periodicPickActivated = checkPickElement.isSelected();

            if (!checkPickElement.isSelected()) {
                defineNameField.clear();
            }
        });
    }

    private void revertCloneButtons() {
        Platform.runLater(() -> {
            checkTestAction.setDisable(checkCloneElement.isSelected());
            launchBotJobButton.setDisable(checkCloneElement.isSelected());
            recallJobButton.setDisable(checkCloneElement.isSelected());

            checkPickElement.setDisable(checkCloneElement.isSelected());

            periodicCloneActivated = checkCloneElement.isSelected();

            if (!checkCloneElement.isSelected()) {
                defineNameField.clear();
            }
        });
    }

    private void manageUIScanIFrames(String searchDesc) {
        ARLogger.getInstance(ARScannedElementPane.class).info("iFrames scan triggered");
        webElementObservableList1.clear();

        boolean scanOk = scanIframesAndElements(webElementObservableList1, searchDesc);
    }

    private void manageUIScanWithoutNameAndId() {
        idAttributeFirst = false;
        nameAttributeFirst = false;
        withoutNameAndId = true;
        // addProgressBar();

        // First Check About the Scanner havina  a Browser Attached
        boolean scanOk = scanARElementsAsync(
                null, null, null, webElementObservableList1, "input", "UI Scan \"Inputs\" Without Name And Id");
        // addProgressBar();
        if (scanOk) {
            scanARElementsAsync(
                    null, null, null, webElementObservableList1, "button", "UI Scan \"Buttons\" Without Name And Id");
        }
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

    private void manageUIScanAttributeNameFirst() {
        idAttributeFirst = false;
        nameAttributeFirst = true;
        withoutNameAndId = false;
        scanARElementsAsync(null, null, null, webElementObservableList1, "name", "UI Scan Attribute Name First");
    }

    private boolean manageUIScanIdsFirst() {
        idAttributeFirst = true;
        nameAttributeFirst = false;
        withoutNameAndId = false;
        return scanARElementsAsync(null, null, null, webElementObservableList1, "id", "UI Scan Ids First");
    }

    private void manageUIScanInputs() {
        List<WebElementTagNameEnum> inputTags = WebElementTagNameEnum.insertableTags();
        for (WebElementTagNameEnum tag : inputTags) {
            // addProgressBar();
            boolean scanOk = scanARElementsAsync(
                    null,
                    By.tagName(tag.getValue()),
                    ARWebElement::isNotClickable,
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
            boolean scanOK = scanARElementsAsync(
                    null,
                    By.tagName(tag.getValue()),
                    ARWebElement::isClickable,
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
                scanARElementsAsync(webElements, null, null, webElementObservableList2, null, "UI Scan By Priorities");
            }
        } catch (Exception e) {
            System.out.println("Error " + e.getMessage());
        }
    }

    private void manageUIScanOutputs() {
        scanARElementsAsync(By.xpath("CODE_CRITERIA"), webElementObservableList2, "UI Scan Outputs");
    }

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
            windowHandles = arWebDriver.getDriver().getWindowHandles();
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
                            mapAdvanced = findElementsWithXPath(arWebDriver.getDriver(), elementType);
                            listARElements = createAdvancedARElement(mapAdvanced, elementType, null);
                        } else if (withoutNameAndId) {
                            mapAdvanced = findElementsWithoutIdOrName(arWebDriver.getDriver(), elementType);
                            listARElements = createAdvancedARElement(mapAdvanced, elementType, null);
                        } else if (preElements != null && preElements.size() > 0) {
                            scannedElementList.addAll(preElements);
                        } else if (criteria != null) {
                            if (criteria.equals(By.xpath("CODE_CRITERIA"))) {
                                mapAdvanced = findElementsOutputCriteria(arWebDriver.getDriver());
                                listARElements =
                                        createAdvancedARElement(mapAdvanced, elementType, WebElementTagNameEnum.OUTPUT);
                            } else {
                                scannedElementList = arWebDriver.getDriver().findElements(criteria);
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
                                    targetElement = performAction.defineTargetNameTitles(targetElement);
                                    targetElement = performAction.defineTagType(targetElement);

                                    // First  Search for xPath
                                    //                                    TargetElement targetValidated =
                                    // checkValidateSearchPriorities(targetElement);

                                    if (targetElement.getDefinedName() != null) {
                                        ARWebElement arWebElement = new ARWebElement(targetElement, botJob.getId());
                                        if (arWebElement != null) {
                                            listARElements.add(arWebElement);
                                            Platform.runLater(() -> {
                                                scannedElements1.requestLayout();
                                                scannedElements1.refresh();
                                            });
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
                Platform.runLater(() -> {
                    scannedElements1.requestLayout();
                    scannedElements1.refresh();
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
            windowHandles = arWebDriver.getDriver().getWindowHandles();
        } catch (Exception e) {
            browserNotAttached();
            return false;
        }

        executorService = Executors.newCachedThreadPool();
        AtomicInteger totalElementsSize = new AtomicInteger(0);

        CompletableFuture<Void> future = CompletableFuture.runAsync(
                () -> {
                    // Switch back to the main page before checking the next iframe
                    arWebDriver.getDriver().switchTo().defaultContent();

                    //                    List<String> allXPath =
                    // iframeInputLocator.listAllXPaths(arWebDriver.getDriver());
                    //                    // Filter the list to keep only elements with tag name "iframe"
                    //                    List<String> onlyIFrames = allXPath.stream()
                    //                            .filter(xpath -> xpath.toLowerCase().contains("iframe"))
                    //                            .collect(Collectors.toList());
                    //
                    //                    for (String xPATH : onlyIFrames) {
                    //                        System.out.println("iFrame: " + xPATH);
                    //                    }

                    List<WebElement> iframes = arWebDriver.getDriver().findElements(By.tagName("iframe"));

                    //                    Map<WebElement, List<WebElement>> iframeElementsMap = new HashMap<>();

                    //                    for (String iFrameScan : onlyIFrames) {
                    boolean foundElements = false;
                    String iFrameXPathScan = "";
                    for (WebElement iframe : iframes) {
                        foundElements = true;

                        try {
                            try {
                                String iFrameTemp =
                                        iframeInputLocator.getElementXPathIFrame(iframe, arWebDriver.getDriver());
                                if (!Strings.isNullOrEmpty(iFrameTemp)) {
                                    iFrameXPathScan = iFrameTemp;
                                }
                            } catch (Exception ignore) {

                            }
                            // CHANGING THE CONTEXT HERE
                            arWebDriver.getDriver().switchTo().frame(iframe);

                            //                            List<String> allInsedeInfo  =
                            // iframeInputLocator.getIframeElementsInfo(iFrameElement,arWebDriver.getDriver());

                            //                            try {
                            //                                String xPathList =
                            // iframeInputLocator.getElementXPathIFrame(
                            //                                        iFrameElement, arWebDriver.getDriver());
                            //                                System.out.println("xPathList: " + xPathList);
                            //                            } catch (Exception x) {
                            //                                System.out.println("Error xPathList");
                            //                            }

                            List<WebElement> elementsInsideIframe =
                                    arWebDriver.getDriver().findElements(By.xpath("//*"));

                            System.out.println(
                                    "Scanned " + elementsInsideIframe.size() + " elements inside an iframe.");

                            TargetElement targetIFrames = new TargetElement();

                            for (WebElement elementChild : elementsInsideIframe) {

                                targetIFrames.setTagType(WebElementTagNameEnum.ALL); // Default to ALL
                                targetIFrames.setIconType(WebElementIcon.TEXT);

                                String xPathElementChild;
                                // Main XPath
                                try {
                                    xPathElementChild =
                                            iframeInputLocator.getElementXPath(elementChild, arWebDriver.getDriver());
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
                                //                                            elementChild, arWebDriver.getDriver());
                                //                                    tagName =
                                // performAction.removeTrailingSlash(xPathInsideFrame);
                                //                                    tagName =
                                // performAction.extractTagName(xPathInsideFrame);
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
                                        ARWebElement arWebElement = new ARWebElement(targetIFrames, botJob.getId());
                                        if (arWebElement != null && arWebElement.getElement() != null) {
                                            webElementObservableList1.add(arWebElement);
                                            Platform.runLater(() -> scannedElements1.refresh());
                                            System.out.println("Found element: " + targetIFrames.getDefinedName());
                                        }
                                    }

                                } catch (Exception error) {
                                    System.out.println("Element not found for XPath: " + "xpath ???");
                                    performMessage.generalErrorIFrame("xpath ???");
                                }
                            }
                        } catch (Exception error) {
                            arWebDriver.getDriver().switchTo().defaultContent();
                            System.out.println("Failed to scan iframe: " + error.getMessage());
                            performMessage.generalErrorIFrame("Error Scanning iFrames");
                            return;
                        }
                        arWebDriver.getDriver().switchTo().defaultContent();
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
        webElementObservableList1.clear();
        manageUIScanInputs();
    }

    private void refreshOutputBtn(String searchType) {
        webElementObservableList2.clear();
        //        manageUIScanClickable();
        manageUIScanOutputs();
    }

    private void refreshOtherElemBtn(String searchType) {
        webElementObservableList2.clear();

        // Check if Browser is Inactive
        try {
            windowHandles = arWebDriver.getDriver().getWindowHandles();
        } catch (Exception e) {
            browserNotAttached();
            return;
        }
        manageUIScanPriorities();
    }

    private void addBehaviourToAddedElements(ListChangeListener.Change<? extends ARWebElement> change) {
        while (change.next()) {
            change.getAddedSubList().forEach(this::addBehaviourToAbrWebElement);
        }
    }

    private void addBehaviourToAbrWebElement(ARWebElement arWebHover) {
        EventHandler<MouseEvent> mouseEnteredHandler = mouseEvent -> {
            Task<Void> handleEvent = new Task<>() {
                @Override
                protected Void call() {

                    if (!Strings.isNullOrEmpty(arWebHover.getTargetElement().getIFrameXPath())) {
                        try {
                            WebElement iFrame = arWebDriver
                                    .getDriver()
                                    .findElement(By.xpath(
                                            arWebHover.getTargetElement().getIFrameXPath()));
                            arWebDriver.getDriver().switchTo().frame(iFrame);
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
                        arWebDriver.getDriver().switchTo().defaultContent();
                    }

                    WebElement currentElement = arWebHover.getElement();

                    if (currentElement != null) {
                        performAction.highlightElement(jsExecutor, previousElement, currentElement);
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
                        performAction.highlightElement(jsExecutor, previousElement, null);
                        previousElement = null; // Reset previous
                    }
                    return null;
                }
            };
            new Thread(handleEvent).start();
        };

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

                //                String reTakeXPath = getXPath(arWebDriver.getDriver(), arWebElement.getElement());
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
                                WebElement iFrame = arWebDriver
                                        .getDriver()
                                        .findElement(By.xpath(
                                                arWebHover.getTargetElement().getIFrameXPath()));
                                arWebDriver.getDriver().switchTo().frame(iFrame);
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
                            elementFinder = arWebDriver
                                    .getDriver()
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
                                performAction.executeActionsAtCoordinates(
                                        arWebHover.getSavedReferences().get("coordinates"),
                                        filedData,
                                        ARConstants.CLICK);

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
                        performMessage.couldNotFindElement(elemTagName);
                        return;
                    }
                }

                if (checkTestAction.isSelected()) {
                    try {
                        if (arWebHover.getElement() != null) {

                            if (arWebHover.getTargetElement().getIFrameXPath() != null) {

                                arWebDriver.getDriver().switchTo().defaultContent();

                                try {
                                    WebElement iFrame = arWebDriver
                                            .getDriver()
                                            .findElement(By.xpath(arWebHover
                                                    .getTargetElement()
                                                    .getIFrameXPath()));
                                    if (iFrame != null) {

                                        arWebDriver.getDriver().switchTo().frame(iFrame);

                                        WebElement elementClicked = arWebDriver
                                                .getDriver()
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

                            arWebDriver.dehighlightElement(arWebHover.getElement());

                            //                            WebElement elementXPath =
                            //
                            // arWebDriver.getDriver().findElement(By.xpath(arWebElement.getTargetElement().getMainXPath()));
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

                            String[] coordinates = new String[] {mainCoordenates, savedCoordenates};

                            if (checkCoordinates.isSelected()) {
                                performAction.executeActionsAtCoordinates(
                                        coordinates[1], fieldData, ARConstants.VISUALIZE);
                                performAction.executeActionsAtCoordinates(
                                        coordinates[0], fieldData, ARConstants.VISUALIZE);

                                performAction.executeActionsAtCoordinates(coordinates[1], fieldData, ARConstants.CLICK);
                                performAction.executeActionsAtCoordinates(coordinates[0], fieldData, ARConstants.CLICK);

                                performAction.executeActionsAtCoordinates(
                                        coordinates[1], fieldData, ARConstants.INSERT);
                                performAction.executeActionsAtCoordinates(
                                        coordinates[0], fieldData, ARConstants.INSERT);

                                performAction.moveAndClickAtCoordinates(coordinates[1], arWebDriver.getDriver());
                                performAction.moveAndClickAtCoordinates(coordinates[0], arWebDriver.getDriver());
                            }

                            StringBuilder actionsTested = new StringBuilder();
                            actionsTested.append("Actions Tested:" + System.lineSeparator());

                            String result = performAction.sequenceOfCommands(
                                    arWebHover.getElement(),
                                    ARConstants.SELECT,
                                    coordinates,
                                    fieldData,
                                    arWebDriver.getDriver());
                            System.out.println(result);
                            actionsTested.append(result + System.lineSeparator());

                            result = performAction.sequenceOfCommands(
                                    arWebHover.getElement(),
                                    ARConstants.CLICK,
                                    coordinates,
                                    fieldData,
                                    arWebDriver.getDriver());
                            System.out.println(result);
                            actionsTested.append(result + System.lineSeparator());

                            result = performAction.sequenceOfCommands(
                                    arWebHover.getElement(),
                                    ARConstants.GET_VALUE,
                                    coordinates,
                                    fieldData,
                                    arWebDriver.getDriver());
                            System.out.println(result);
                            actionsTested.append(result + System.lineSeparator());

                            result = performAction.sequenceOfCommands(
                                    arWebHover.getElement(),
                                    ARConstants.CLEAR,
                                    coordinates,
                                    fieldData,
                                    arWebDriver.getDriver());
                            System.out.println(result);

                            actionsTested.append(result + System.lineSeparator());
                            result = performAction.sequenceOfCommands(
                                    arWebHover.getElement(),
                                    ARConstants.INSERT,
                                    coordinates,
                                    fieldData,
                                    arWebDriver.getDriver());
                            System.out.println(result);
                            actionsTested.append(result + System.lineSeparator());

                            result = performAction.sequenceOfCommands(
                                    arWebHover.getElement(),
                                    ARConstants.GET_VALUE,
                                    coordinates,
                                    fieldData,
                                    arWebDriver.getDriver());
                            System.out.println(result);
                            actionsTested.append(result + System.lineSeparator());

                            result = performAction.sequenceOfCommands(
                                    arWebHover.getElement(),
                                    ARConstants.FOCUS,
                                    coordinates,
                                    fieldData,
                                    arWebDriver.getDriver());
                            System.out.println(result);
                            actionsTested.append(result + System.lineSeparator());

                            result = performAction.sequenceOfCommands(
                                    arWebHover.getElement(),
                                    ARConstants.TAB,
                                    coordinates,
                                    fieldData,
                                    arWebDriver.getDriver());
                            System.out.println(result);
                            result = performAction.sequenceOfCommands(
                                    arWebHover.getElement(),
                                    ARConstants.COORD_VISUALIZA,
                                    coordinates,
                                    fieldData,
                                    arWebDriver.getDriver());
                            System.out.println(result);
                            actionsTested.append(result + System.lineSeparator());

                            result = performAction.sequenceOfCommands(
                                    arWebHover.getElement(),
                                    ARConstants.COORD_CLICK,
                                    coordinates,
                                    fieldData,
                                    arWebDriver.getDriver());
                            System.out.println(result);
                            actionsTested.append(result + System.lineSeparator());

                            result = performAction.sequenceOfCommands(
                                    arWebHover.getElement(),
                                    ARConstants.COORD_INSERT,
                                    coordinates,
                                    fieldData,
                                    arWebDriver.getDriver());
                            System.out.println(result);
                            actionsTested.append(result + System.lineSeparator());

                            Platform.runLater(() -> {
                                countdownTextField.setText(actionsTested.toString());
                                countdownTextField.setStyle("-fx-font-size: 12px; -fx-text-fill: blue;");
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

                            ARLogger.getInstance(ARScannedElementPane.class)
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

                                ARLogger.getInstance(Thread.class)
                                        .severe(String.format(
                                                "Error Creating a new Block for bot job Id %d\nCheck if you already have a Bot Job Created!",
                                                botJob.getId()));
                                return;
                            } else {

                                setBlockJob(
                                        ARSharedResources.getInstance().getEntityById(BlockDTO.class, currentBlockId));
                                ARLogger.getInstance(Thread.class)
                                        .info(String.format(
                                                "Created a new Block id %d for bot job Id %d",
                                                currentBlockId, botJob.getId()));
                            }

                            Platform.runLater(() -> {
                                refreshBlocks(true);
                            });
                        }

                        String finalNameWebElement = nameDefined;
                        Task<Void> handleEvent = new Task<>() {
                            @Override
                            protected Void call() throws Exception {
                                ARLogger.getInstance(Task.class).info("THREAD: Started");

                                ARLogger.getInstance(Task.class)
                                        .fine("THREAD: fetching instruction list from database");
                                ObservableList<InstructionDTO> list = ARSharedResources.getInstance()
                                        .getEntityList(
                                                InstructionDTO.class,
                                                (instr) -> instr.getBlock().getId() == currentBlockId);
                                ARLogger.getInstance(Task.class).finer("THREAD: instruction list size " + list.size());

                                String actionReq = checkClickElement.isSelected()
                                        ? "CLICK"
                                        : checkInputText.isSelected()
                                                ? "INPUT"
                                                : checkOutputText.isSelected() ? "OUTPUT" : "OTHER";
                                InstructionDTO instruction = arWebHover.buildBlockLoopInstruction(
                                        arWebHover.getTargetElement().getTagType(),
                                        actionReq,
                                        checkPickElement.isSelected(),
                                        list.size());

                                instruction.setCoordinates(
                                        arWebHover.getTargetElement().getMainCoordinates());
                                instruction.setIFrameXPath(
                                        arWebHover.getTargetElement().getIFrameXPath());
                                instruction.setBlock(blockJob);
                                instruction.setInstructionOrderNumber(list.size() + 1);

                                ARLogger.getInstance(Task.class).fine("THREAD: adding instruction to database");

                                Integer currentBotJobId = botJob.getId();

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
                                                parts[i] = "I:" + finalNameWebElement; // Modify only the relevant part
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
                                        instruction.getPath(),
                                        instruction.getCoordinates(),
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
                                    // reference.setBlockLoopInstructionDTO(instruction);
                                    queue.add(reference);
                                }
                                try {

                                    Platform.runLater(() -> {
                                        boolean saved = performDataBase.insertReferences(queue, instruction.getId());
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

                for (ARWebElement arWebElement : scannedElements2.getItems()) {
                    performAction.highlightElement(jsExecutor, arWebElement.getElement(), null);
                }

                StringBuilder sb = new StringBuilder();
                String nameDefined = "";
                if (arWebHover.getTargetElement() != null) {
                    this.targetSelected = arWebHover.getTargetElement();
                    this.targetSelected.setElement(arWebHover.getElement()); // RETURN THE ELEMENT FOR OTHER PURPOSE

                    arWebDriver.getDriver().switchTo().defaultContent();

                    // iFrame
                    if (!Strings.isNullOrEmpty(this.targetSelected.getIFrameXPath())) {
                        try {
                            WebElement iFrame =
                                    arWebDriver.getDriver().findElement(By.xpath(this.targetSelected.getIFrameXPath()));
                            arWebDriver.getDriver().switchTo().frame(iFrame);
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
                        nameDefined = this.targetSelected.getOriginalTagName()
                                + (!Strings.isNullOrEmpty(this.targetSelected.getAttribName())
                                        ? "-" + this.targetSelected.getAttribName()
                                        : !Strings.isNullOrEmpty(this.targetSelected.getAttribId())
                                                ? "-" + this.targetSelected.getAttribId()
                                                : !Strings.isNullOrEmpty(this.targetSelected.getSomeText())
                                                        ? "-" + truncate(this.targetSelected.getSomeText(), 50)
                                                        : "");
                        if (this.targetSelected.getDefinedName() != null
                                && !this.targetSelected.getDefinedName().equalsIgnoreCase(nameDefined)) {
                            nameDefined = this.targetSelected.getDefinedName();
                        }

                        String finalNameDefined = nameDefined;
                        Platform.runLater(() -> defineNameField.setText(truncate(finalNameDefined, 50)));

                    } else if (!Strings.isNullOrEmpty(this.targetSelected.getAllAttributes())) {

                        // Split by comma to get key-value pairs
                        String[] parts = this.targetSelected.getAllAttributes().split(",");

                        String idValue = null;
                        String nameValue = null;
                        String typeValue = null;

                        // Loop through each key-value pair
                        for (String part : parts) {
                            String[] keyValue = part.split("=");

                            if (keyValue.length == 2) { // Ensure valid key-value pair
                                String key = keyValue[0].trim();
                                String value = keyValue[1].trim().replaceAll("\"", ""); // Remove quotes

                                if (key.equals("id")) {
                                    idValue = value;
                                } else if (key.equals("name")) {
                                    nameValue = value;
                                } else if (key.equals("type")) {
                                    typeValue = value;
                                }
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
                        Platform.runLater(() -> defineNameField.setText(truncate(finalSomeText, 50)));

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
                sb.append("All Attributes: " + this.targetSelected.getAllAttributes())
                        .append("\n");
                //                sb.append("Attrib Value: " + this.targetSelected.getAttributeValue())
                //                        .append("\n");
                sb.append("Named: " + nameDefined).append("\n");
                countdownTextField.setText(sb.toString());
                countdownTextField.setStyle("-fx-font-size: 12px; -fx-text-fill: blue;");

                defineCheckBoxesClickabe(this.targetSelected);
                arWebDriver.getDriver().switchTo().defaultContent();
            }
        };
        arWebHover.addEventHandler(MouseEvent.MOUSE_ENTERED, mouseEnteredHandler);
        arWebHover.addEventHandler(MouseEvent.MOUSE_EXITED, mouseExitedHandler);
        arWebHover.addEventHandler(MouseEvent.MOUSE_CLICKED, mouseClickedHandler);
    }

    private Set<WebElement> managePrioritiesCriteria() {

        // Gets Always the Latest info form DB
        databaseUserDto = loadUserData(botJob.getHomeBanking().getId());
        arPriorities.loadSearchElementsConfig(databaseUserDto.getSearchConfig());

        Set<WebElement> elementsResponse = new HashSet<>();
        if (arPriorities.getSearchConfigList() == null) {
            System.out.println("Is going to Search using \"searchConfigTemplate\"!  Please Add to the DB");
            return null;
        }
        if (arPriorities.getSearchConfigList().size() > 0) {

            // Fetch the HTML content of the page
            Document docJSoup = null;
            docJSoup = JsoupConnect(botJob.getHomeBanking().getUrl());
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
                                        arWebDriver.getDriver().findElements((By.xpath(name)));
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
                                            arWebDriver.getDriver().findElements((By.tagName(name)));
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
                                                    arWebDriver.getDriver().findElement(By.id(forAttribute));
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
                                            arWebDriver.getDriver().findElements((By.tagName(name)));
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
                                List<WebElement> searchingElems = searchAllInputs(arWebDriver.getDriver());
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
                                        arWebDriver.getDriver().findElements((By.cssSelector("[" + name + "]")));
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
                                        WebElement associatedElement =
                                                arWebDriver.getDriver().findElement(By.id(forAttribute));
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
                            // arWebDriver.getDriver().findElements((By.cssSelector("input[" + name + "]")));
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
                            // arWebDriver.getDriver().findElement(By.id(forAttribute));
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
                        // arWebDriver.getDriver().findElements((By.cssSelector("[" +
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
                                    arWebDriver.getDriver().findElements(new ByChained(locators));
                            uniqueWebElements.addAll(searchingElems);
                            for (WebElement element : uniqueWebElements) {
                                String labelText = element.getText();
                                String associatedText = "";

                                // Get the value of the 'for' attribute
                                String forAttribute = element.getAttribute("for");
                                if (forAttribute != null) {
                                    // Find the associated element using the 'for' attribute value
                                    WebElement associatedElement =
                                            arWebDriver.getDriver().findElement(By.id(forAttribute));
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
                        // arWebDriver.getDriver().findElements((By.cssSelector("[" +
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

    private void periodicCloneThread(WebDriver driver) {
        // JavaScript code to inject
        String jsCode = "(function (targetOriginURL, trustedOriginURL) {\n"
                + "  var tooltip = document.createElement(\"div\");\n"
                + "  tooltip.id = \"Martini-Is-Awesome\";\n"
                + "  tooltip.style.position = \"absolute\";\n"
                + "  tooltip.style.backgroundColor = \"rgba(255, 165, 0, 0.5)\";\n"
                + "  tooltip.style.border = \"1px solid #ccc\";\n"
                + "  tooltip.style.padding = \"10px\";\n"
                + "  tooltip.style.borderRadius = \"5px\";\n"
                + "  tooltip.style.boxShadow = \"0 2px 4px rgba(0, 0, 0, 0.2)\";\n"
                + "  tooltip.style.fontFamily = \"Arial, sans-serif\";\n"
                + "  tooltip.style.fontSize = \"14px\";\n"
                + "  tooltip.style.color = \"#333\";\n"
                + "  tooltip.style.zIndex = \"10000\";\n"
                + "  tooltip.style.display = \"none\";\n"
                + "  document.body.appendChild(tooltip);\n"
                + "\n"
                + "  var elementInfoMap = new Map();\n"
                + "  var allElementInfo = [];\n"
                + "\n"
                + "  function getElementIdentity(element) {\n"
                + "    var xpath = getMartiniXPath(element);\n"
                + "    var allAttributes = \"\";\n"
                + "    try {\n"
                + "      // console.log(\"element\", element);\n"
                + "      allAttributes = getElementAttributes(element);\n"
                + "    } catch (error) {}\n"
                + "    var customXPath = \"\";\n"
                + "    try {\n"
                + "      customXPath = getElementLocators(element);\n"
                + "    } catch (error) {}\n"
                + "\n"
                + "    var attribId = element.id || \"\";\n"
                + "    var attribName = element.name || \"\";\n"
                + "    var coords = element.getBoundingClientRect();\n"
                + "    coords = `${coords.left},${coords.top}`;\n"
                + "\n"
                + "    var someText = element.textContent.trim() || \"\";\n"
                + "    if (\n"
                + "      element.tagName.toLowerCase() === \"input\" ||\n"
                + "      element.tagName.toLowerCase() === \"textarea\"\n"
                + "    ) {\n"
                + "      someText = element.value || \"\";\n"
                + "    }\n"
                + "\n"
                + "    var someText = getSomeText(element.tagName.toLowerCase(), element);\n"
                + "\n"
                + "    return {\n"
                + "      xpath,\n"
                + "      allAttributes,\n"
                + "      customXPath,\n"
                + "      attribId,\n"
                + "      attribName,\n"
                + "      coords,\n"
                + "      someText,\n"
                + "    };\n"
                + "  }\n"
                + "  function getElementAttributes(element) {\n"
                + "    const attributes = [];\n"
                + "\n"
                + "    try {\n"
                + "      for (const attr of element.attributes) {\n"
                + "        attributes.push(`${attr.name}=\"${attr.value}\"`);\n"
                + "      }\n"
                + "    } catch (error) {\n"
                + "      // If accessing attributes directly fails (likely due to cross-origin restrictions)\n"
                + "      // Attempt to get attributes using JavaScript execution within the iframe's context\n"
                + "      const iframe = element.ownerDocument.defaultView.frameElement;\n"
                + "      if (iframe) {\n"
                + "        const iframeWindow = iframe.contentWindow;\n"
                + "        iframeWindow.document.addEventListener(\"DOMContentLoaded\", () => {\n"
                + "          const iframeElement = iframeWindow.document.querySelector(\n"
                + "            `#${element.id}`\n"
                + "          ); // Adjust selector as needed\n"
                + "          if (iframeElement) {\n"
                + "            for (const attr of iframeElement.attributes) {\n"
                + "              attributes.push(`${attr.name}=\"${attr.value}\"`);\n"
                + "            }\n"
                + "          }\n"
                + "        });\n"
                + "      }\n"
                + "    }\n"
                + "\n"
                + "    return attributes;\n"
                + "  }\n"
                + "  function getElementLocators(element) {\n"
                + "    const locators = [];\n"
                + "\n"
                + "    if (element === document.body) {\n"
                + "      locators.push(\"/html/\" + element.tagName.toLowerCase());\n"
                + "      return locators;\n"
                + "    }\n"
                + "\n"
                + "    const tagName = element.tagName.toLowerCase();\n"
                + "    const id = element.id ? `#${element.id}` : \"\";\n"
                + "    const className = (\n"
                + "      typeof element.className === \"string\" ? element.className : \"\"\n"
                + "    )\n"
                + "      .split(\" \")\n"
                + "      .filter((cls) => !/\\d/.test(cls))\n"
                + "      .join(\".\");\n"
                + "\n"
                + "    if (id) {\n"
                + "      locators.push(id);\n"
                + "    }\n"
                + "\n"
                + "    if (className) {\n"
                + "      locators.push(`//${tagName}[contains(@class, '${className}')]`);\n"
                + "    }\n"
                + "\n"
                + "    // Check for other attributes (e.g., 'data-*' attributes)\n"
                + "    const attributes = Array.from(element.attributes);\n"
                + "    attributes.forEach((attr) => {\n"
                + "      if (attr.name !== \"class\" && attr.name !== \"id\") {\n"
                + "        // Exclude class and id\n"
                + "        locators.push(`${tagName}[@${attr.name}=\"${attr.value}\"]`);\n"
                + "      }\n"
                + "    });\n"
                + "\n"
                + "    // Handle iframe elements\n"
                + "    if (element.ownerDocument !== document) {\n"
                + "      try {\n"
                + "        const iframe = element.ownerDocument.defaultView.frameElement;\n"
                + "        const iframeLocators = getElementLocators(iframe);\n"
                + "        iframeLocators.forEach((iframePath) => {\n"
                + "          locators.push(`${iframePath}//${tagName}`);\n"
                + "        });\n"
                + "      } catch (error) {\n"
                + "        console.error(\"Error getting locators for iframe element:\", error);\n"
                + "      }\n"
                + "    } else {\n"
                + "      // Handle regular elements\n"
                + "      let ix = 0;\n"
                + "      const siblings = element.parentNode.childNodes;\n"
                + "\n"
                + "      for (let i = 0; i < siblings.length; i++) {\n"
                + "        const sibling = siblings[i];\n"
                + "\n"
                + "        if (sibling === element) {\n"
                + "          const parentLocators = getElementLocators(element.parentNode);\n"
                + "          parentLocators.forEach((parentPath) => {\n"
                + "            locators.push(`${parentPath}/${tagName}[${ix + 1}]`);\n"
                + "          });\n"
                + "          break;\n"
                + "        }\n"
                + "\n"
                + "        if (sibling.nodeType === 1 && sibling.tagName === element.tagName) {\n"
                + "          ix++;\n"
                + "        }\n"
                + "      }\n"
                + "    }\n"
                + "\n"
                + "    return locators;\n"
                + "  }\n"
                + "  function getMartiniXPath(element) {\n"
                + "    if (element === document.body) {\n"
                + "      return \"/html/body\";\n"
                + "    }\n"
                + "    var ix = 0;\n"
                + "    var siblings = element.parentNode ? element.parentNode.childNodes : [];\n"
                + "    for (var i = 0; i < siblings.length; i++) {\n"
                + "      var sibling = siblings[i];\n"
                + "      if (sibling.nodeType === 1 && sibling.tagName === element.tagName) {\n"
                + "        if (sibling === element) {\n"
                + "          return (\n"
                + "            getMartiniXPath(element.parentNode) +\n"
                + "            \"/\" +\n"
                + "            element.tagName.toLowerCase() +\n"
                + "            \"[\" +\n"
                + "            (ix + 1) +\n"
                + "            \"]\"\n"
                + "          );\n"
                + "        }\n"
                + "        ix++;\n"
                + "      }\n"
                + "    }\n"
                + "    return \"\";\n"
                + "  }\n"
                + "  function getMartiniCustomXPath(element) {\n"
                + "    if (element === document.body) {\n"
                + "      return \"/html/\" + element.tagName.toLowerCase();\n"
                + "    }\n"
                + "    var className = element.className\n"
                + "      .split(\" \")\n"
                + "      .filter(function (cls) {\n"
                + "        return !/\\d/.test(cls);\n"
                + "      })\n"
                + "      .join(\".\");\n"
                + "    var tagName = element.tagName.toLowerCase();\n"
                + "    var ix = 0;\n"
                + "    var siblings = element.parentNode.childNodes;\n"
                + "    for (var i = 0; i < siblings.length; i++) {\n"
                + "      var sibling = siblings[i];\n"
                + "      if (sibling === element) {\n"
                + "        var path = getMartiniCustomXPath(element.parentNode) + \"/\" + tagName;\n"
                + "        if (className) {\n"
                + "          path += '[contains(@class, \"' + className + '\")]';\n"
                + "        } else {\n"
                + "          path += \"[\" + (ix + 1) + \"]\";\n"
                + "        }\n"
                + "        return path;\n"
                + "      }\n"
                + "      if (sibling.nodeType === 1 && sibling.tagName === element.tagName) {\n"
                + "        ix++;\n"
                + "      }\n"
                + "    }\n"
                + "    return \"\";\n"
                + "  }\n"
                + "\n"
                + "  var lastHoveredIsIframe = null; // Keep track of the last hovered element type\n"
                + "\n"
                + "  let lastHoveredElement = null; // Keep track of the previously hovered element\n"
                + "  // Declare global variables to store iframe details\n"
                + "  var iframeDocument = null;\n"
                + "  var iframeElementsCount = 0;\n"
                + "\n"
                + "  function showMartiniTooltip(event) {\n"
                + "    var elementBelowTooltip = document.elementFromPoint(\n"
                + "      event.clientX,\n"
                + "      event.clientY\n"
                + "    );\n"
                + "\n"
                + "    // Do nothing if the hovered element is the tooltip itself or an excluded tag (html, body, main)\n"
                + "    if (\n"
                + "      !elementBelowTooltip ||\n"
                + "      elementBelowTooltip === tooltip ||\n"
                + "      [\"html\", \"body\", \"main\"].includes(\n"
                + "        elementBelowTooltip.tagName.toLowerCase()\n"
                + "      )\n"
                + "    ) {\n"
                + "      return;\n"
                + "    }\n"
                + "\n"
                + "    var isIframe = elementBelowTooltip.tagName.toLowerCase() === \"iframe\";\n"
                + "\n"
                + "    // Reset only if switching between iframe and non-iframe elements\n"
                + "    if (lastHoveredIsIframe !== isIframe) {\n"
                + "      console.clear();\n"
                + "      elementInfoMap.clear();\n"
                + "      allElementInfo = [];\n"
                + "    }\n"
                + "\n"
                + "    lastHoveredIsIframe = isIframe; // Update last hovered element type\n"
                + "\n"
                + "    // Get the tag name of the element\n"
                + "    var tagNameTemp = elementBelowTooltip.tagName.toLowerCase();\n"
                + "\n"
                + "    // // Get the text content of the element (if it has text)\n"
                + "    // var someText = elementBelowTooltip.textContent.trim();\n"
                + "    // if (someText === \"\") {\n"
                + "    //   someText = \"No text content\";\n"
                + "    // }\n"
                + "\n"
                + "    var someText = getSomeText(\n"
                + "      elementBelowTooltip.tagName.toLowerCase(),\n"
                + "      elementBelowTooltip\n"
                + "    );\n"
                + "\n"
                + "    // If it's an iframe, get the number of elements inside the iframe\n"
                + "    var iframeDetails = \"\";\n"
                + "    if (isIframe) {\n"
                + "      iframeDocument =\n"
                + "        elementBelowTooltip.contentDocument ||\n"
                + "        elementBelowTooltip.contentWindow.document;\n"
                + "      iframeElementsCount = iframeDocument\n"
                + "        ? iframeDocument.body.getElementsByTagName(\"*\").length\n"
                + "        : 0;\n"
                + "      iframeDetails = `Elements inside iframe: ${iframeElementsCount}`;\n"
                + "    }\n"
                + "\n"
                + "    var elementXPath = getMartiniXPath(elementBelowTooltip);\n"
                + "\n"
                + "    // Store tagName and other details in the Map\n"
                + "    if (iframeDetails && iframeDetails.length > 0) {\n"
                + "      elementInfoMap.set(\n"
                + "        elementXPath,\n"
                + "        `xpath:${elementXPath};text:${someText};${iframeDetails};`\n"
                + "      );\n"
                + "    } else {\n"
                + "      const {\n"
                + "        xpath,\n"
                + "        allAttributes,\n"
                + "        customXPath,\n"
                + "        attribId,\n"
                + "        attribName,\n"
                + "        coords,\n"
                + "        someText,\n"
                + "      } = getElementIdentity(elementBelowTooltip);\n"
                + "\n"
                + "      var elementInfoString = `${elementBelowTooltip.tagName.toLowerCase()};xpath:${xpath};text:${someText};attribId:${attribId};attribName:${attribName};coords:${coords};allAttributes:${allAttributes};customXPath:${customXPath};`;\n"
                + "\n"
                + "      elementInfoMap.set(xpath, elementInfoString);\n"
                + "    }\n"
                + "\n"
                + "    // Parse the someText using the semicolon delimiter\n"
                + "    var parsedText = someText.split(\";\");\n"
                + "\n"
                + "    // Format the tooltip content to make it more readable\n"
                + "    var tooltipContent = \"\";\n"
                + "    tooltipContent += isIframe ? \"[Iframe] <br>\" : \"\";\n"
                + "    tooltipContent += `Tag Name: ${tagNameTemp}<br>`;\n"
                + "    tooltipContent += isIframe ? `- ${iframeDetails}<br>` : \"\";\n"
                + "\n"
                + "    // Replace new lines with <br> before adding each item from parsedText\n"
                + "    tooltipContent += someText\n"
                + "      ? parsedText.map((item) => `- ${item}<br>`).join(\"\")\n"
                + "      : \"No Text<br>\";\n"
                + "\n"
                + "    // Set the tooltip content with line breaks\n"
                + "    tooltip.innerHTML = tagNameTemp;\n"
                + "\n"
                + "    // Position the tooltip near the mouse cursor\n"
                + "    var tooltipWidth = tooltip.offsetWidth;\n"
                + "    var tooltipHeight = tooltip.offsetHeight;\n"
                + "    var left = event.pageX - tooltipWidth / 2;\n"
                + "    var top = event.pageY - tooltipHeight / 2;\n"
                + "\n"
                + "    tooltip.style.left = left + \"px\";\n"
                + "    tooltip.style.top = top + \"px\";\n"
                + "    tooltip.style.display = \"block\";\n"
                + "\n"
                + "    // Highlight the hovered element\n"
                + "    if (lastHoveredElement !== elementBelowTooltip) {\n"
                + "      // Remove highlight from the previous element if any\n"
                + "      if (lastHoveredElement) {\n"
                + "        lastHoveredElement.style.outline = \"\"; // Remove the previous highlight\n"
                + "      }\n"
                + "      // Add a border to highlight the current element\n"
                + "      elementBelowTooltip.style.outline = \"3px solid red\"; // Highlight the element\n"
                + "\n"
                + "      lastHoveredElement = elementBelowTooltip; // Update the last hovered element\n"
                + "    }\n"
                + "\n"
                + "    // console.log(\"Element Info:\", elementInfoMap);\n"
                + "  }\n"
                + "\n"
                + "  function limitMapCharacters(elementInfoMap, coordText) {\n"
                + "    elementInfoMap.forEach((value, key) => {\n"
                + "      let modifiedValue = value;\n"
                + "\n"
                + "      // TO DO  REDUCE ONLY THE TEXT FIELD\n"
                + "\n"
                + "      // // Check if the key is \"html\" or value length is greater than 400\n"
                + "      // if (key === \"html\" || value.length > 400) {\n"
                + "      //   // Truncate the value to 150 characters and add \"...\"\n"
                + "      //   if (value.length > 150) {\n"
                + "      //     modifiedValue = value.substring(0, 150) + \"...\";\n"
                + "      //   }\n"
                + "\n"
                + "      //   // If the length exceeds 400 characters, break the value into multiple lines\n"
                + "      //   if (value.length > 400) {\n"
                + "      //     const firstPart = value.substring(0, 150);\n"
                + "      //     const secondPart = value.substring(150);\n"
                + "      //     modifiedValue = `${firstPart}<br>...${secondPart}`;\n"
                + "      //   }\n"
                + "      // }\n"
                + "\n"
                + "      // Push the formatted value and key to the array\n"
                + "      allElementInfo.push(`${coordText}:${modifiedValue}`);\n"
                + "    });\n"
                + "  }\n"
                + "\n"
                + "  function getSomeText(tagName, element) {\n"
                + "    let someText = \"\";\n"
                + "\n"
                + "    if ([\"input\", \"textarea\", \"select\", \"button\"].includes(tagName)) {\n"
                + "      const extractedText = extractTextFromHTML(element || \"\");\n"
                + "      someText = [\n"
                + "        ...extractedText.titles,\n"
                + "        ...extractedText.text,\n"
                + "        ...extractedText.labels,\n"
                + "      ]\n"
                + "        .join(\"; \")\n"
                + "        .trim();\n"
                + "    } else if ([\"option\", \"label\", \"a\"].includes(tagName)) {\n"
                + "      const extractedText = extractTextFromHTML(element || \"\");\n"
                + "      someText = [\n"
                + "        ...extractedText.titles,\n"
                + "        ...extractedText.text,\n"
                + "        ...extractedText.labels,\n"
                + "      ]\n"
                + "        .join(\"; \")\n"
                + "        .trim();\n"
                + "    } else if (![\"html\", \"body\", \"script\"].includes(tagName)) {\n"
                + "      const extractedText = extractTextFromHTML(element || \"\");\n"
                + "      someText = [\n"
                + "        ...extractedText.titles,\n"
                + "        ...extractedText.text,\n"
                + "        ...extractedText.labels,\n"
                + "      ]\n"
                + "        .join(\"; \")\n"
                + "        .trim();\n"
                + "    }\n"
                + "\n"
                + "    someText = someText\n"
                + "      .split(\";\")\n"
                + "      .map((text) => text.trim())\n"
                + "      .filter(Boolean)\n"
                + "      .join(\";\"); // Clean up sequential text\n"
                + "\n"
                + "    return someText;\n"
                + "  }\n"
                + "\n"
                + "  function extractTextFromHTML(element) {\n"
                + "    const result = {\n"
                + "      text: new Set(), // Using Set to avoid duplicate text\n"
                + "      labels: new Set(), // Using Set to avoid duplicate labels\n"
                + "      titles: new Set(), // Using Set to avoid duplicate titles\n"
                + "    };\n"
                + "\n"
                + "    // Extract text content directly from the element (in case it has no children)\n"
                + "    if (element.textContent) {\n"
                + "      let elementText = element.textContent.trim();\n"
                + "      if (elementText) {\n"
                + "        result.text.add(elementText); // Using .add() instead of .push() for Set\n"
                + "      }\n"
                + "    }\n"
                + "\n"
                + "    // Extract label text from input placeholders and other form-related data\n"
                + "    element.querySelectorAll(\"label\").forEach((label) => {\n"
                + "      if (label.textContent) {\n"
                + "        let labelText = label.textContent.trim();\n"
                + "        if (labelText) {\n"
                + "          result.labels.add(labelText); // Using .add() for Set to ensure uniqueness\n"
                + "        }\n"
                + "      }\n"
                + "\n"
                + "      // Handle associated input fields (if the label has a 'for' attribute)\n"
                + "      let forAttribute = label.getAttribute(\"for\");\n"
                + "      if (forAttribute) {\n"
                + "        let associatedInput = element.querySelector(`#${forAttribute}`);\n"
                + "        if (associatedInput) {\n"
                + "          // Check if it's an input field or textarea and extract value or placeholder\n"
                + "          let inputValue = associatedInput.value?.trim();\n"
                + "          let inputPlaceholder = associatedInput.placeholder?.trim();\n"
                + "          if (inputValue) {\n"
                + "            result.text.add(inputValue); // Using .add() for Set to ensure uniqueness\n"
                + "          } else if (inputPlaceholder) {\n"
                + "            result.text.add(inputPlaceholder); // Fallback to placeholder\n"
                + "          }\n"
                + "        }\n"
                + "      }\n"
                + "    });\n"
                + "\n"
                + "    // Extract text from common block and inline elements\n"
                + "    const textExtractors = [\n"
                + "      \"p\",\n"
                + "      \"h1\",\n"
                + "      \"h2\",\n"
                + "      \"h3\",\n"
                + "      \"h4\",\n"
                + "      \"h5\",\n"
                + "      \"h6\",\n"
                + "      \"li\",\n"
                + "      \"span\",\n"
                + "      \"div\",\n"
                + "      \"strong\",\n"
                + "      \"em\",\n"
                + "      \"b\",\n"
                + "      \"i\",\n"
                + "      \"blockquote\",\n"
                + "    ];\n"
                + "\n"
                + "    textExtractors.forEach((tagName) => {\n"
                + "      element.querySelectorAll(tagName).forEach((childElement) => {\n"
                + "        if (childElement.textContent) {\n"
                + "          let elemText = childElement.textContent.trim();\n"
                + "          if (elemText) {\n"
                + "            result.text.add(elemText); // Using .add() for Set to ensure uniqueness\n"
                + "          }\n"
                + "        }\n"
                + "      });\n"
                + "    });\n"
                + "\n"
                + "    // Extract text from <a> tags (links)\n"
                + "    element.querySelectorAll(\"a\").forEach((link) => {\n"
                + "      if (link.textContent) {\n"
                + "        let linkText = link.textContent.trim();\n"
                + "        if (linkText) {\n"
                + "          result.text.add(linkText); // Using .add() for Set to ensure uniqueness\n"
                + "        }\n"
                + "      }\n"
                + "    });\n"
                + "\n"
                + "    // Extract iframe titles and nested content\n"
                + "    element.querySelectorAll(\"iframe\").forEach((iframe) => {\n"
                + "      if (iframe.getAttribute(\"title\")) {\n"
                + "        let title = iframe.getAttribute(\"title\")?.trim();\n"
                + "        if (title) {\n"
                + "          result.titles.add(title); // Using .add() for Set to ensure uniqueness\n"
                + "        }\n"
                + "      }\n"
                + "\n"
                + "      try {\n"
                + "        let iframeDoc =\n"
                + "          iframe.contentDocument ||\n"
                + "          new DOMParser().parseFromString(iframe.srcdoc || \"\", \"text/html\");\n"
                + "        let iframeContent = extractTextFromHTML(iframeDoc); // Here we assume iframeDoc is an element.\n"
                + "        iframeContent.titles.forEach((title) => result.titles.add(title));\n"
                + "        iframeContent.text.forEach((text) => result.text.add(text));\n"
                + "        iframeContent.labels.forEach((label) => result.labels.add(label));\n"
                + "      } catch (e) {\n"
                + "        console.warn(\"Could not access iframe content\", e);\n"
                + "      }\n"
                + "    });\n"
                + "\n"
                + "    // Convert Sets to arrays before returning to maintain previous structure\n"
                + "    return {\n"
                + "      text: Array.from(result.text),\n"
                + "      labels: Array.from(result.labels),\n"
                + "      titles: Array.from(result.titles),\n"
                + "    };\n"
                + "  }\n"
                + "\n"
                + "  function hideMartiniTooltip() {\n"
                + "    tooltip.style.display = \"none\";\n"
                + "  }\n"
                + "  function handleMartiniClick(event) {\n"
                + "    event.preventDefault();\n"
                + "    event.stopPropagation();\n"
                + "    tooltip.style.display = \"none\";\n"
                + "\n"
                + "    // Determine the element below the tooltip (mouse position)\n"
                + "    var elementBelowTooltip = document.elementFromPoint(\n"
                + "      event.clientX,\n"
                + "      event.clientY\n"
                + "    );\n"
                + "\n"
                + "    // Hide the tooltip\n"
                + "    tooltip.style.display = \"none\";\n"
                + "\n"
                + "    // If the element below the tooltip is an iframe\n"
                + "    if (\n"
                + "      elementBelowTooltip &&\n"
                + "      elementBelowTooltip.tagName.toLowerCase() === \"iframe\"\n"
                + "    ) {\n"
                + "      // Get the document inside the iframe\n"
                + "      var iframeDocument =\n"
                + "        elementBelowTooltip.contentDocument ||\n"
                + "        elementBelowTooltip.contentWindow.document;\n"
                + "\n"
                + "      // If the iframe document is valid\n"
                + "      if (iframeDocument) {\n"
                + "        // Format the iframe details\n"
                + "        var iframeDetails = `Elements inside iframe: ${\n"
                + "          iframeDocument.body.getElementsByTagName(\"*\").length\n"
                + "        }`;\n"
                + "\n"
                + "        // Display the tooltip with iframe details\n"
                + "        tooltip.innerHTML = `[Iframe] <br> ${iframeDetails}`;\n"
                + "\n"
                + "        // Position the tooltip near the mouse cursor\n"
                + "        var tooltipWidth = tooltip.offsetWidth;\n"
                + "        var tooltipHeight = tooltip.offsetHeight;\n"
                + "        var left = event.pageX - tooltipWidth / 2;\n"
                + "        var top = event.pageY - tooltipHeight / 2;\n"
                + "\n"
                + "        tooltip.style.left = left + \"px\";\n"
                + "        tooltip.style.top = top + \"px\";\n"
                + "        tooltip.style.display = \"block\";\n"
                + "\n"
                + "        // Initialize an array to store the iframe element information\n"
                + "        allElementInfo = [];\n"
                + "\n"
                + "        const {\n"
                + "          xpath,\n"
                + "          allAttributes,\n"
                + "          customXPath,\n"
                + "          attribId,\n"
                + "          attribName,\n"
                + "          coords,\n"
                + "          someText,\n"
                + "        } = getElementIdentity(elementBelowTooltip);\n"
                + "\n"
                + "        var elementInfoString = `${elementBelowTooltip.tagName.toLowerCase()};xpath:${xpath};text:${someText};attribId:${attribId};attribName:${attribName};coords:${coords};allAttributes:${allAttributes};customXPath:${customXPath};`;\n"
                + "\n"
                + "        allElementInfo.push(`clicked-iFrame:${elementInfoString};`);\n"
                + "\n"
                + "        // limitMapCharacters(elementInfoMap, \"clicked-tagName\");\n"
                + "\n"
                + "        // Get all elements inside the iframe and log their details\n"
                + "        var iframeElements = iframeDocument.querySelectorAll(\"*\");\n"
                + "        iframeElements.forEach(function (elementInsideIframe) {\n"
                + "          const {\n"
                + "            xpath,\n"
                + "            allAttributes,\n"
                + "            customXPath,\n"
                + "            attribId,\n"
                + "            attribName,\n"
                + "            coords,\n"
                + "            someText,\n"
                + "          } = getElementIdentity(elementInsideIframe);\n"
                + "\n"
                + "          var elementInfoString = `iFrame-Child:${elementInsideIframe.tagName.toLowerCase()};xpath:${xpath};text:${someText};attribId:${attribId};attribName:${attribName};coords:${coords};allAttributes:${allAttributes};customXPath:${customXPath};`;\n"
                + "\n"
                + "          allElementInfo.push(elementInfoString);\n"
                + "        });\n"
                + "\n"
                + "        // Log the list of iframe elements\n"
                + "        console.log(\"List of iframe elements:\", allElementInfo);\n"
                + "        window.allElementInfo = allElementInfo;\n"
                + "      } else {\n"
                + "        tooltip.innerHTML = \"No iframe document found.\";\n"
                + "\n"
                + "        // Position the tooltip near the mouse cursor\n"
                + "        var tooltipWidth = tooltip.offsetWidth;\n"
                + "        var tooltipHeight = tooltip.offsetHeight;\n"
                + "        var left = event.pageX - tooltipWidth / 2;\n"
                + "        var top = event.pageY - tooltipHeight / 2;\n"
                + "\n"
                + "        tooltip.style.left = left + \"px\";\n"
                + "        tooltip.style.top = top + \"px\";\n"
                + "        tooltip.style.display = \"block\";\n"
                + "      }\n"
                + "    } else {\n"
                + "      // If the clicked element is not an iframe, gather its regular information\n"
                + "      var tagName = elementBelowTooltip.tagName.toLowerCase();\n"
                + "\n"
                + "      // Avoid main, body, and html tags\n"
                + "      if ([\"html\", \"body\", \"main\"].includes(tagName)) {\n"
                + "        return; // Don't proceed if it's one of these elements\n"
                + "      }\n"
                + "\n"
                + "      // Format and push regular element information to the array\n"
                + "      // limitMapCharacters(elementInfoMap, \"tagName-found\");\n"
                + "\n"
                + "      const {\n"
                + "        xpath,\n"
                + "        allAttributes,\n"
                + "        customXPath,\n"
                + "        attribId,\n"
                + "        attribName,\n"
                + "        coords,\n"
                + "        someText,\n"
                + "      } = getElementIdentity(elementBelowTooltip);\n"
                + "\n"
                + "      var elementInfoString = `clicked:${elementBelowTooltip.tagName.toLowerCase()};xpath:${xpath};text:${someText};attribId:${attribId};attribName:${attribName};coords:${coords};allAttributes:${allAttributes};customXPath:${customXPath};`;\n"
                + "\n"
                + "      allElementInfo.push(elementInfoString);\n"
                + "\n"
                + "      console.log(\"List of elements:\", allElementInfo);\n"
                + "      window.allElementInfo = allElementInfo;\n"
                + "\n"
                + "      allElementInfo = [];\n"
                + "\n"
                + "      // Show the tooltip with the element details\n"
                + "      // tooltip.innerHTML = `${tagName} <br> ${someText}`;\n"
                + "      tooltip.innerHTML = `${tagName} <br> ${someText}`;\n"
                + "      var tooltipWidth = tooltip.offsetWidth;\n"
                + "      var tooltipHeight = tooltip.offsetHeight;\n"
                + "      var left = event.pageX - tooltipWidth / 2;\n"
                + "      var top = event.pageY - tooltipHeight / 2;\n"
                + "\n"
                + "      tooltip.style.left = left + \"px\";\n"
                + "      tooltip.style.top = top + \"px\";\n"
                + "      tooltip.style.display = \"block\";\n"
                + "    }\n"
                + "\n"
                + "    // window.revertCloneInjections();\n"
                + "\n"
                + "    // Remove the tooltip from the page and delete the reference after 5 seconds\n"
                + "    setTimeout(() => {\n"
                + "      window.allElementInfo = [];\n"
                + "\n"
                + "      // if (tooltip) {\n"
                + "      //   tooltip.remove(); // Completely remove the tooltip from the DOM\n"
                + "      //   tooltip = null; // Clear the reference to free memory\n"
                + "      //   console.log(\"Tooltip completely removed.\");\n"
                + "      // }\n"
                + "\n"
                + "      // if (lastHoveredElement || elementBelowTooltip) {\n"
                + "      //   // Remove highlight from the previous element if any\n"
                + "      //   if (lastHoveredElement) {\n"
                + "      //     lastHoveredElement.style.outline = \"\"; // Remove the previous highlight\n"
                + "      //   }\n"
                + "\n"
                + "      //   // Remove highlight from the previous element if any\n"
                + "      //   if (elementBelowTooltip) {\n"
                + "      //     elementBelowTooltip.style.outline = \"\"; // Remove the previous highlight\n"
                + "      //   }\n"
                + "      // }\n"
                + "    }, 2000);\n"
                + "  }\n"
                + "\n"
                + "  document.addEventListener(\"mouseover\", showMartiniTooltip);\n"
                + "  document.addEventListener(\"click\", handleMartiniClick);\n"
                + "\n"
                + "  window.revertCloneInjections = function () {\n"
                + "    // alert(\"revertCloneInjections\");\n"
                + "\n"
                + "    document.removeEventListener(\"mouseover\", showMartiniTooltip);\n"
                + "    document.removeEventListener(\"click\", handleMartiniClick);\n"
                + "    console.log(\"revertCloneInjections\");\n"
                + "\n"
                + "    // Remove the tooltip from the page and delete the reference after 5 seconds\n"
                + "    setTimeout(() => {\n"
                + "      removeElements();\n"
                + "    }, 1000);\n"
                + "  };\n"
                + "\n"
                + "  function removeElements() {\n"
                + "    if (tooltip) {\n"
                + "      tooltip.remove(); // Completely remove the tooltip from the DOM\n"
                + "      tooltip = null; // Clear the reference to free memory\n"
                + "      console.log(\"Tooltip completely removed.\");\n"
                + "    }\n"
                + "  }\n"
                + "\n"
                + "  // window.postMessage({ type: \"myMessage\", data: \"some data\" }, targetOriginURL);\n"
                + "\n"
                + "  window.addEventListener(\"message\", function (event) {\n"
                + "    if (event.origin !== trustedOriginURL) return; // check the origin\n"
                + "    console.log(event.data);\n"
                + "  });\n"
                + "})(arguments[0], arguments[1]);\n"
                + "// })(\"http://localhost:3000/\", \"http://localhost:3000/\");\n";

        // Inject the JavaScript into the webpage
        jsExecutor = (JavascriptExecutor) driver;
        jsExecutor.executeScript(jsCode);

        // Start a thread to periodically check the XPath value and update the TextField
        new Thread(() -> {
                    while (periodicCloneActivated) {

                        // Execute JavaScript to construct and return a custom object
                        LinkedHashMap<String, Object> linkedHashMap =
                                (LinkedHashMap<String, Object>) jsExecutor.executeScript(
                                        "var obj = { allElementInfo: window.allElementInfo }; return obj;");

                        // Convert the LinkedHashMap to a Java Map (if necessary)
                        Map<String, Object> resultMap = new LinkedHashMap<>(linkedHashMap);

                        if (linkedHashMap != null) {
                            Platform.runLater(() -> {
                                Object iframeElementsObject = resultMap.get("allElementInfo");

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

                                if (iFrameElements != null && iFrameElements.length > 0) {

                                    // Extract elements from input lines
                                    elementsFound.clear();
                                    elementsFound = performAction.extractElementData(iFrameElements);
                                    iFrameElements = null;

                                    Optional<ElementDTO> iframeElement = elementsFound.stream()
                                            .filter(element ->
                                                    "clicked-iFrame".equalsIgnoreCase(element.getTypeElement()))
                                            .findFirst();

                                    if (iframeElement.isPresent()) {
                                        if (iframeElement.get().getTypeElement().equals("clicked-iFrame")) {
                                            iFrameText.setText("iFrame Detected");
                                            elementsFound = elementsFound.stream()
                                                    .map(elementDTO -> {
                                                        if ("iFrame-Child".equals(elementDTO.getTypeElement())) {
                                                            elementDTO.setIFrameXPath(iframeElement
                                                                    .get()
                                                                    .getXPath());
                                                        }
                                                        return elementDTO;
                                                    })
                                                    .collect(Collectors.toList());
                                        }
                                    } else {
                                        iFrameText.setText("");
                                    }

                                    checkTestAction.setSelected(false);
                                    //                                    checkCloneElement.setSelected(false);
                                    //                                    revertCloneButtons();

                                    if (elementsFound.size() > 0) {
                                        ElementDTO pickTarget = prefillDefinedName(elementsFound);

                                        // Direct Insert to the Factory of Elements
                                        if (iframeElement.isPresent()) {
                                            insertNewElement(iframeElement.get(), elementsFound);
                                        } else {

                                            if (!Strings.isNullOrEmpty(pickTarget.getXPath())
                                                    && !xpathTextPrevious.equalsIgnoreCase(pickTarget.getXPath())) {

                                                TargetElement targetLocal = extractPickClone(pickTarget);

                                                if (targetLocal.getNameField() != null
                                                        && targetLocal.getNameLabel() != null) {
                                                    insertNewElement(elementsFound);
                                                }
                                            }
                                        }

                                        elementsFound.clear();
                                    }

                                } else {
                                    iFrameText.setText("");
                                }
                            });
                        }
                        try {
                            Thread.sleep(300); // Check every 300 milliseconds
                        } catch (InterruptedException e) {
                            ARLogger.getInstance(ARScannedElementPane.class)
                                    .fine(String.format(
                                            "Error Attempt to get currentXPath / tagName / coords", e.getMessage()));
                        }
                    }
                })
                .start();
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

    private void periodicPickThread(WebDriver driver, String currentUrl) {
        // JavaScript code to inject
        String jsCode = "(function (targetOriginURL, trustedOriginURL) {\n"
                + "  var tooltip = document.createElement(\"div\");\n"
                + "  tooltip.id = \"Martini-Is-Awesome\";\n"
                + "  tooltip.style.position = \"absolute\";\n"
                + "  // tooltip.style.backgroundColor = \"rgba(255, 165, 0, 0.5)\"; // Slightly opaque light orange\n"
                + "  tooltip.style.backgroundColor = \"rgba(0, 0, 0, 0)\"; // Full transparency\n"
                + "  tooltip.style.border = \"1px solid #ccc\";\n"
                + "  tooltip.style.padding = \"10px\";\n"
                + "  tooltip.style.borderRadius = \"5px\";\n"
                + "  tooltip.style.boxShadow = \"0 2px 4px rgba(0, 0, 0, 0.2)\";\n"
                + "  tooltip.style.fontFamily = \"Arial, sans-serif\";\n"
                + "  tooltip.style.fontSize = \"14px\";\n"
                + "  tooltip.style.color = \"#333\";\n"
                + "  tooltip.style.zIndex = \"10000\"; // Higher z-index\n"
                + "  tooltip.style.display = \"none\";\n"
                + "  document.body.appendChild(tooltip);\n"
                + "\n"
                + "  var elementInfoMap = new Map();\n"
                + "  var allElementInfo = [];\n"
                + "\n"
                + "  function getElementLocators(element) {\n"
                + "    const locators = [];\n"
                + "\n"
                + "    if (element === document.body) {\n"
                + "      locators.push(\"/html/\" + element.tagName.toLowerCase());\n"
                + "      return locators;\n"
                + "    }\n"
                + "\n"
                + "    const tagName = element.tagName.toLowerCase();\n"
                + "    const id = element.id ? `#${element.id}` : \"\";\n"
                + "    const className = (\n"
                + "      typeof element.className === \"string\" ? element.className : \"\"\n"
                + "    )\n"
                + "      .split(\" \")\n"
                + "      .filter((cls) => !/\\d/.test(cls))\n"
                + "      .join(\".\");\n"
                + "\n"
                + "    if (id) {\n"
                + "      locators.push(id);\n"
                + "    }\n"
                + "\n"
                + "    if (className) {\n"
                + "      locators.push(`//${tagName}[contains(@class, '${className}')]`);\n"
                + "    }\n"
                + "\n"
                + "    // Check for other attributes (e.g., 'data-*' attributes)\n"
                + "    const attributes = Array.from(element.attributes);\n"
                + "    attributes.forEach((attr) => {\n"
                + "      if (attr.name !== \"class\" && attr.name !== \"id\") {\n"
                + "        // Exclude class and id\n"
                + "        locators.push(`${tagName}[@${attr.name}=\"${attr.value}\"]`);\n"
                + "      }\n"
                + "    });\n"
                + "\n"
                + "    // Handle iframe elements\n"
                + "    if (element.ownerDocument !== document) {\n"
                + "      try {\n"
                + "        const iframe = element.ownerDocument.defaultView.frameElement;\n"
                + "        const iframeLocators = getElementLocators(iframe);\n"
                + "        iframeLocators.forEach((iframePath) => {\n"
                + "          locators.push(`${iframePath}//${tagName}`);\n"
                + "        });\n"
                + "      } catch (error) {\n"
                + "        console.error(\"Error getting locators for iframe element:\", error);\n"
                + "      }\n"
                + "    } else {\n"
                + "      // Handle regular elements\n"
                + "      let ix = 0;\n"
                + "      const siblings = element.parentNode.childNodes;\n"
                + "\n"
                + "      for (let i = 0; i < siblings.length; i++) {\n"
                + "        const sibling = siblings[i];\n"
                + "\n"
                + "        if (sibling === element) {\n"
                + "          const parentLocators = getElementLocators(element.parentNode);\n"
                + "          parentLocators.forEach((parentPath) => {\n"
                + "            locators.push(`${parentPath}/${tagName}[${ix + 1}]`);\n"
                + "          });\n"
                + "          break;\n"
                + "        }\n"
                + "\n"
                + "        if (sibling.nodeType === 1 && sibling.tagName === element.tagName) {\n"
                + "          ix++;\n"
                + "        }\n"
                + "      }\n"
                + "    }\n"
                + "\n"
                + "    return locators;\n"
                + "  }\n"
                + "  function getMartiniXPath(element) {\n"
                + "    if (element === document.body) {\n"
                + "      return \"/html/body\";\n"
                + "    }\n"
                + "    var ix = 0;\n"
                + "    var siblings = element.parentNode ? element.parentNode.childNodes : [];\n"
                + "    for (var i = 0; i < siblings.length; i++) {\n"
                + "      var sibling = siblings[i];\n"
                + "      if (sibling.nodeType === 1 && sibling.tagName === element.tagName) {\n"
                + "        if (sibling === element) {\n"
                + "          return (\n"
                + "            getMartiniXPath(element.parentNode) +\n"
                + "            \"/\" +\n"
                + "            element.tagName.toLowerCase() +\n"
                + "            \"[\" +\n"
                + "            (ix + 1) +\n"
                + "            \"]\"\n"
                + "          );\n"
                + "        }\n"
                + "        ix++;\n"
                + "      }\n"
                + "    }\n"
                + "    return \"\";\n"
                + "  }\n"
                + "  function getMartiniCustomXPath(element) {\n"
                + "    if (element === document.body) {\n"
                + "      return \"/html/\" + element.tagName.toLowerCase();\n"
                + "    }\n"
                + "\n"
                + "    // Ensure className is a string; otherwise, set it as an empty string\n"
                + "    var className = (\n"
                + "      typeof element.className === \"string\" ? element.className : \"\"\n"
                + "    )\n"
                + "      .split(\" \")\n"
                + "      .filter(function (cls) {\n"
                + "        return !/\\d/.test(cls);\n"
                + "      })\n"
                + "      .join(\".\");\n"
                + "\n"
                + "    var tagName = element.tagName.toLowerCase();\n"
                + "    var ix = 0;\n"
                + "    var siblings = element.parentNode.childNodes;\n"
                + "\n"
                + "    for (var i = 0; i < siblings.length; i++) {\n"
                + "      var sibling = siblings[i];\n"
                + "\n"
                + "      if (sibling === element) {\n"
                + "        var path = getMartiniCustomXPath(element.parentNode) + \"/\" + tagName;\n"
                + "\n"
                + "        if (className) {\n"
                + "          path += '[contains(@class, \"' + className + '\")]';\n"
                + "        } else {\n"
                + "          path += \"[\" + (ix + 1) + \"]\";\n"
                + "        }\n"
                + "        return path;\n"
                + "      }\n"
                + "\n"
                + "      if (sibling.nodeType === 1 && sibling.tagName === element.tagName) {\n"
                + "        ix++;\n"
                + "      }\n"
                + "    }\n"
                + "\n"
                + "    return \"\";\n"
                + "  }\n"
                + "\n"
                + "  var lastHoveredIsIframe = null; // Keep track of the last hovered element type\n"
                + "\n"
                + "  let lastHoveredElement = null; // Keep track of the previously hovered element\n"
                + "  // Declare global variables to store iframe details\n"
                + "  var iframeDocument = null;\n"
                + "  var iframeElementsCount = 0;\n"
                + "\n"
                + "  function showMartiniTooltip(event) {\n"
                + "    var elementBelowTooltip = document.elementFromPoint(\n"
                + "      event.clientX,\n"
                + "      event.clientY\n"
                + "    );\n"
                + "\n"
                + "    // Do nothing if the hovered element is the tooltip itself or an excluded tag (html, body, main)\n"
                + "    if (\n"
                + "      !elementBelowTooltip ||\n"
                + "      elementBelowTooltip === tooltip ||\n"
                + "      [\"html\", \"body\", \"main\"].includes(\n"
                + "        elementBelowTooltip.tagName.toLowerCase()\n"
                + "      )\n"
                + "    ) {\n"
                + "      return;\n"
                + "    }\n"
                + "\n"
                + "    var isIframe = elementBelowTooltip.tagName.toLowerCase() === \"iframe\";\n"
                + "\n"
                + "    // Reset only if switching between iframe and non-iframe elements\n"
                + "    if (lastHoveredIsIframe !== isIframe) {\n"
                + "      console.clear();\n"
                + "      elementInfoMap.clear();\n"
                + "      allElementInfo = [];\n"
                + "    }\n"
                + "\n"
                + "    lastHoveredIsIframe = isIframe; // Update last hovered element type\n"
                + "\n"
                + "    // Get the tag name of the element\n"
                + "    var tagNameTemp = elementBelowTooltip.tagName.toLowerCase();\n"
                + "\n"
                + "    // // Get the text content of the element (if it has text)\n"
                + "    // var someText = elementBelowTooltip.textContent.trim();\n"
                + "    // if (someText === \"\") {\n"
                + "    //   someText = \"No text content\";\n"
                + "    // }\n"
                + "\n"
                + "    var someText = getSomeText(\n"
                + "      elementBelowTooltip.tagName.toLowerCase(),\n"
                + "      elementBelowTooltip\n"
                + "    );\n"
                + "\n"
                + "    // If it's an iframe, get the number of elements inside the iframe\n"
                + "    var iframeDetails = \"\";\n"
                + "    if (isIframe) {\n"
                + "      iframeDocument =\n"
                + "        elementBelowTooltip.contentDocument ||\n"
                + "        elementBelowTooltip.contentWindow.document;\n"
                + "      iframeElementsCount = iframeDocument\n"
                + "        ? iframeDocument.body.getElementsByTagName(\"*\").length\n"
                + "        : 0;\n"
                + "      iframeDetails = `Elements inside iframe: ${iframeElementsCount}`;\n"
                + "    }\n"
                + "\n"
                + "    var elementXPath = getMartiniXPath(elementBelowTooltip);\n"
                + "\n"
                + "    // Store tagName and other details in the Map\n"
                + "    if (iframeDetails && iframeDetails.length > 0) {\n"
                + "      elementInfoMap.set(\n"
                + "        elementXPath,\n"
                + "        `xpath:${elementXPath};text:${someText};${iframeDetails};`\n"
                + "      );\n"
                + "    } else {\n"
                + "      const {\n"
                + "        xpath,\n"
                + "        allAttributes,\n"
                + "        customXPath,\n"
                + "        attribId,\n"
                + "        attribName,\n"
                + "        coords,\n"
                + "        someText,\n"
                + "      } = getElementIdentity(elementBelowTooltip);\n"
                + "\n"
                + "      var elementInfoString = `${elementBelowTooltip.tagName.toLowerCase()};xpath:${xpath};text:${someText};attribId:${attribId};attribName:${attribName};coords:${coords};allAttributes:${allAttributes};customXPath:${customXPath};`;\n"
                + "\n"
                + "      elementInfoMap.set(xpath, elementInfoString);\n"
                + "    }\n"
                + "\n"
                + "    // Parse the someText using the semicolon delimiter\n"
                + "    var parsedText = someText.split(\";\");\n"
                + "\n"
                + "    // Format the tooltip content to make it more readable\n"
                + "    var tooltipContent = \"\";\n"
                + "    tooltipContent += isIframe ? \"[Iframe] <br>\" : \"\";\n"
                + "    tooltipContent += `Tag Name: ${tagNameTemp}<br>`;\n"
                + "    tooltipContent += isIframe ? `- ${iframeDetails}<br>` : \"\";\n"
                + "\n"
                + "    // Replace new lines with <br> before adding each item from parsedText\n"
                + "    tooltipContent += someText\n"
                + "      ? parsedText.map((item) => `- ${item}<br>`).join(\"\")\n"
                + "      : \"No Text<br>\";\n"
                + "\n"
                + "    // Set the tooltip content with line breaks\n"
                + "    tooltip.innerHTML = tagNameTemp;\n"
                + "\n"
                + "    // Position the tooltip near the mouse cursor\n"
                + "    var tooltipWidth = tooltip.offsetWidth;\n"
                + "    var tooltipHeight = tooltip.offsetHeight;\n"
                + "    var left = event.pageX - tooltipWidth / 2;\n"
                + "    var top = event.pageY - tooltipHeight / 2;\n"
                + "\n"
                + "    tooltip.style.left = left + \"px\";\n"
                + "    tooltip.style.top = top + \"px\";\n"
                + "    tooltip.style.display = \"block\";\n"
                + "\n"
                + "    // Highlight the hovered element\n"
                + "    if (lastHoveredElement !== elementBelowTooltip) {\n"
                + "      // Remove highlight from the previous element if any\n"
                + "      if (lastHoveredElement) {\n"
                + "        lastHoveredElement.style.outline = \"\"; // Remove the previous highlight\n"
                + "      }\n"
                + "      // Add a border to highlight the current element\n"
                + "      elementBelowTooltip.style.outline = \"3px solid red\"; // Highlight the element\n"
                + "\n"
                + "      lastHoveredElement = elementBelowTooltip; // Update the last hovered element\n"
                + "    }\n"
                + "\n"
                + "    // console.log(\"Element Info:\", elementInfoMap);\n"
                + "  }\n"
                + "\n"
                + "  function hideMartiniTooltip() {\n"
                + "    tooltip.style.display = \"none\";\n"
                + "  }\n"
                + "\n"
                + "  function handleMartiniClick(event) {\n"
                + "    event.preventDefault();\n"
                + "    event.stopPropagation();\n"
                + "    tooltip.style.display = \"none\";\n"
                + "\n"
                + "    // Determine the element below the tooltip (mouse position)\n"
                + "    var elementBelowTooltip = document.elementFromPoint(\n"
                + "      event.clientX,\n"
                + "      event.clientY\n"
                + "    );\n"
                + "\n"
                + "    // Hide the tooltip\n"
                + "    tooltip.style.display = \"none\";\n"
                + "\n"
                + "    // If the element below the tooltip is an iframe\n"
                + "    if (\n"
                + "      elementBelowTooltip &&\n"
                + "      elementBelowTooltip.tagName.toLowerCase() === \"iframe\"\n"
                + "    ) {\n"
                + "      // Get the document inside the iframe\n"
                + "      var iframeDocument =\n"
                + "        elementBelowTooltip.contentDocument ||\n"
                + "        elementBelowTooltip.contentWindow.document;\n"
                + "\n"
                + "      // If the iframe document is valid\n"
                + "      if (iframeDocument) {\n"
                + "        // Format the iframe details\n"
                + "        var iframeDetails = `Elements inside iframe: ${\n"
                + "          iframeDocument.body.getElementsByTagName(\"*\").length\n"
                + "        }`;\n"
                + "\n"
                + "        // Display the tooltip with iframe details\n"
                + "        tooltip.innerHTML = `[Iframe] <br> ${iframeDetails}`;\n"
                + "\n"
                + "        // Position the tooltip near the mouse cursor\n"
                + "        var tooltipWidth = tooltip.offsetWidth;\n"
                + "        var tooltipHeight = tooltip.offsetHeight;\n"
                + "        var left = event.pageX - tooltipWidth / 2;\n"
                + "        var top = event.pageY - tooltipHeight / 2;\n"
                + "\n"
                + "        tooltip.style.left = left + \"px\";\n"
                + "        tooltip.style.top = top + \"px\";\n"
                + "        tooltip.style.display = \"block\";\n"
                + "\n"
                + "        // Initialize an array to store the iframe element information\n"
                + "        allElementInfo = [];\n"
                + "\n"
                + "        const {\n"
                + "          xpath,\n"
                + "          allAttributes,\n"
                + "          customXPath,\n"
                + "          attribId,\n"
                + "          attribName,\n"
                + "          coords,\n"
                + "          someText,\n"
                + "        } = getElementIdentity(elementBelowTooltip);\n"
                + "\n"
                + "        var elementInfoString = `${elementBelowTooltip.tagName.toLowerCase()};xpath:${xpath};text:${someText};attribId:${attribId};attribName:${attribName};coords:${coords};allAttributes:${allAttributes};customXPath:${customXPath};`;\n"
                + "\n"
                + "        allElementInfo.push(`clicked-iFrame:${elementInfoString};`);\n"
                + "\n"
                + "        // limitMapCharacters(elementInfoMap, \"clicked-tagName\");\n"
                + "\n"
                + "        // Get all elements inside the iframe and log their details\n"
                + "        var iframeElements = iframeDocument.querySelectorAll(\"*\");\n"
                + "        iframeElements.forEach(function (elementInsideIframe) {\n"
                + "          const {\n"
                + "            xpath,\n"
                + "            allAttributes,\n"
                + "            customXPath,\n"
                + "            attribId,\n"
                + "            attribName,\n"
                + "            coords,\n"
                + "            someText,\n"
                + "          } = getElementIdentity(elementInsideIframe);\n"
                + "\n"
                + "          var elementInfoString = `iFrame-Child:${elementInsideIframe.tagName.toLowerCase()};xpath:${xpath};text:${someText};attribId:${attribId};attribName:${attribName};coords:${coords};allAttributes:${allAttributes};customXPath:${customXPath};`;\n"
                + "\n"
                + "          allElementInfo.push(elementInfoString);\n"
                + "        });\n"
                + "\n"
                + "        // Log the list of iframe elements\n"
                + "        console.log(\"List of iframe elements:\", allElementInfo);\n"
                + "        window.allElementInfo = allElementInfo;\n"
                + "      } else {\n"
                + "        tooltip.innerHTML = \"No iframe document found.\";\n"
                + "\n"
                + "        // Position the tooltip near the mouse cursor\n"
                + "        var tooltipWidth = tooltip.offsetWidth;\n"
                + "        var tooltipHeight = tooltip.offsetHeight;\n"
                + "        var left = event.pageX - tooltipWidth / 2;\n"
                + "        var top = event.pageY - tooltipHeight / 2;\n"
                + "\n"
                + "        tooltip.style.left = left + \"px\";\n"
                + "        tooltip.style.top = top + \"px\";\n"
                + "        tooltip.style.display = \"block\";\n"
                + "      }\n"
                + "    } else {\n"
                + "      // If the clicked element is not an iframe, gather its regular information\n"
                + "      var tagName = elementBelowTooltip.tagName.toLowerCase();\n"
                + "\n"
                + "      // Avoid main, body, and html tags\n"
                + "      if ([\"html\", \"body\", \"main\"].includes(tagName)) {\n"
                + "        return; // Don't proceed if it's one of these elements\n"
                + "      }\n"
                + "\n"
                + "      // Format and push regular element information to the array\n"
                + "      limitMapCharacters(elementInfoMap, \"tagName-found\");\n"
                + "\n"
                + "      const {\n"
                + "        xpath,\n"
                + "        allAttributes,\n"
                + "        customXPath,\n"
                + "        attribId,\n"
                + "        attribName,\n"
                + "        coords,\n"
                + "        someText,\n"
                + "      } = getElementIdentity(elementBelowTooltip);\n"
                + "\n"
                + "      var elementInfoString = `clicked:${elementBelowTooltip.tagName.toLowerCase()};xpath:${xpath};text:${someText};attribId:${attribId};attribName:${attribName};coords:${coords};allAttributes:${allAttributes};customXPath:${customXPath};`;\n"
                + "\n"
                + "      allElementInfo.push(elementInfoString);\n"
                + "\n"
                + "      console.log(\"List of elements:\", allElementInfo);\n"
                + "      window.allElementInfo = allElementInfo;\n"
                + "\n"
                + "      // Show the tooltip with the element details\n"
                + "      // tooltip.innerHTML = `${tagName} <br> ${someText}`;\n"
                + "      tooltip.innerHTML = `${tagName} <br> ${someText}`;\n"
                + "      var tooltipWidth = tooltip.offsetWidth;\n"
                + "      var tooltipHeight = tooltip.offsetHeight;\n"
                + "      var left = event.pageX - tooltipWidth / 2;\n"
                + "      var top = event.pageY - tooltipHeight / 2;\n"
                + "\n"
                + "      tooltip.style.left = left + \"px\";\n"
                + "      tooltip.style.top = top + \"px\";\n"
                + "      tooltip.style.display = \"block\";\n"
                + "    }\n"
                + "\n"
                + "    window.revertPickInjections();\n"
                + "\n"
                + "    // Remove the tooltip from the page and delete the reference after 5 seconds\n"
                + "    // Remove the tooltip from the page and delete the reference after 5 seconds\n"
                + "    setTimeout(() => {\n"
                + "      if (tooltip) {\n"
                + "        tooltip.remove(); // Completely remove the tooltip from the DOM\n"
                + "        tooltip = null; // Clear the reference to free memory\n"
                + "        console.log(\"Tooltip completely removed.\");\n"
                + "      }\n"
                + "\n"
                + "      if (lastHoveredElement || elementBelowTooltip) {\n"
                + "        // Remove highlight from the previous element if any\n"
                + "        if (lastHoveredElement) {\n"
                + "          lastHoveredElement.style.outline = \"\"; // Remove the previous highlight\n"
                + "        }\n"
                + "\n"
                + "        // Remove highlight from the previous element if any\n"
                + "        if (elementBelowTooltip) {\n"
                + "          elementBelowTooltip.style.outline = \"\"; // Remove the previous highlight\n"
                + "        }\n"
                + "      }\n"
                + "    }, 3000);\n"
                + "  }\n"
                + "\n"
                + "  function getElementAttributes(element) {\n"
                + "    const attributes = [];\n"
                + "\n"
                + "    try {\n"
                + "      for (const attr of element.attributes) {\n"
                + "        attributes.push(`${attr.name}=\"${attr.value}\"`);\n"
                + "      }\n"
                + "    } catch (error) {\n"
                + "      // If accessing attributes directly fails (likely due to cross-origin restrictions)\n"
                + "      // Attempt to get attributes using JavaScript execution within the iframe's context\n"
                + "      const iframe = element.ownerDocument.defaultView.frameElement;\n"
                + "      if (iframe) {\n"
                + "        const iframeWindow = iframe.contentWindow;\n"
                + "        iframeWindow.document.addEventListener(\"DOMContentLoaded\", () => {\n"
                + "          const iframeElement = iframeWindow.document.querySelector(\n"
                + "            `#${element.id}`\n"
                + "          ); // Adjust selector as needed\n"
                + "          if (iframeElement) {\n"
                + "            for (const attr of iframeElement.attributes) {\n"
                + "              attributes.push(`${attr.name}=\"${attr.value}\"`);\n"
                + "            }\n"
                + "          }\n"
                + "        });\n"
                + "      }\n"
                + "    }\n"
                + "\n"
                + "    return attributes;\n"
                + "  }\n"
                + "\n"
                + "  function getElementIdentity(element) {\n"
                + "    var xpath = getMartiniXPath(element);\n"
                + "    var allAttributes = \"\";\n"
                + "    try {\n"
                + "      // console.log(\"element\", element);\n"
                + "      allAttributes = getElementAttributes(element);\n"
                + "    } catch (error) {}\n"
                + "    var customXPath = \"\";\n"
                + "    try {\n"
                + "      customXPath = getElementLocators(element);\n"
                + "    } catch (error) {}\n"
                + "\n"
                + "    var attribId = element.id || \"\";\n"
                + "    var attribName = element.name || \"\";\n"
                + "    var coords = element.getBoundingClientRect();\n"
                + "    coords = `${coords.left},${coords.top}`;\n"
                + "\n"
                + "    var someText = element.textContent.trim() || \"\";\n"
                + "    if (\n"
                + "      element.tagName.toLowerCase() === \"input\" ||\n"
                + "      element.tagName.toLowerCase() === \"textarea\"\n"
                + "    ) {\n"
                + "      someText = element.value || \"\";\n"
                + "    }\n"
                + "\n"
                + "    var someText = getSomeText(element.tagName.toLowerCase(), element);\n"
                + "\n"
                + "    return {\n"
                + "      xpath,\n"
                + "      allAttributes,\n"
                + "      customXPath,\n"
                + "      attribId,\n"
                + "      attribName,\n"
                + "      coords,\n"
                + "      someText,\n"
                + "    };\n"
                + "  }\n"
                + "\n"
                + "  function limitMapCharacters(elementInfoMap, coordText) {\n"
                + "    elementInfoMap.forEach((value, key) => {\n"
                + "      let modifiedValue = value;\n"
                + "\n"
                + "      // TO DO  REDUCE ONLY THE TEXT FIELD\n"
                + "\n"
                + "      // // Check if the key is \"html\" or value length is greater than 400\n"
                + "      // if (key === \"html\" || value.length > 400) {\n"
                + "      //   // Truncate the value to 150 characters and add \"...\"\n"
                + "      //   if (value.length > 150) {\n"
                + "      //     modifiedValue = value.substring(0, 150) + \"...\";\n"
                + "      //   }\n"
                + "\n"
                + "      //   // If the length exceeds 400 characters, break the value into multiple lines\n"
                + "      //   if (value.length > 400) {\n"
                + "      //     const firstPart = value.substring(0, 150);\n"
                + "      //     const secondPart = value.substring(150);\n"
                + "      //     modifiedValue = `${firstPart}<br>...${secondPart}`;\n"
                + "      //   }\n"
                + "      // }\n"
                + "\n"
                + "      // Push the formatted value and key to the array\n"
                + "      allElementInfo.push(`${coordText}:${modifiedValue}`);\n"
                + "    });\n"
                + "  }\n"
                + "\n"
                + "  function getSomeText(tagName, element) {\n"
                + "    let someText = \"\";\n"
                + "\n"
                + "    if ([\"input\", \"textarea\", \"select\", \"button\"].includes(tagName)) {\n"
                + "      const extractedText = extractTextFromHTML(element || \"\");\n"
                + "      someText = [\n"
                + "        ...extractedText.titles,\n"
                + "        ...extractedText.text,\n"
                + "        ...extractedText.labels,\n"
                + "      ]\n"
                + "        .join(\"; \")\n"
                + "        .trim();\n"
                + "    } else if ([\"option\", \"label\", \"a\"].includes(tagName)) {\n"
                + "      const extractedText = extractTextFromHTML(element || \"\");\n"
                + "      someText = [\n"
                + "        ...extractedText.titles,\n"
                + "        ...extractedText.text,\n"
                + "        ...extractedText.labels,\n"
                + "      ]\n"
                + "        .join(\"; \")\n"
                + "        .trim();\n"
                + "    } else if (![\"html\", \"body\", \"script\"].includes(tagName)) {\n"
                + "      const extractedText = extractTextFromHTML(element || \"\");\n"
                + "      someText = [\n"
                + "        ...extractedText.titles,\n"
                + "        ...extractedText.text,\n"
                + "        ...extractedText.labels,\n"
                + "      ]\n"
                + "        .join(\"; \")\n"
                + "        .trim();\n"
                + "    }\n"
                + "\n"
                + "    someText = someText\n"
                + "      .split(\";\")\n"
                + "      .map((text) => text.trim())\n"
                + "      .filter(Boolean)\n"
                + "      .join(\";\"); // Clean up sequential text\n"
                + "\n"
                + "    return someText;\n"
                + "  }\n"
                + "\n"
                + "  function extractTextFromHTML(element) {\n"
                + "    const result = {\n"
                + "      text: new Set(), // Using Set to avoid duplicate text\n"
                + "      labels: new Set(), // Using Set to avoid duplicate labels\n"
                + "      titles: new Set(), // Using Set to avoid duplicate titles\n"
                + "    };\n"
                + "\n"
                + "    // Extract text content directly from the element (in case it has no children)\n"
                + "    if (element.textContent) {\n"
                + "      let elementText = element.textContent.trim();\n"
                + "      if (elementText) {\n"
                + "        result.text.add(elementText); // Using .add() instead of .push() for Set\n"
                + "      }\n"
                + "    }\n"
                + "\n"
                + "    // Extract label text from input placeholders and other form-related data\n"
                + "    element.querySelectorAll(\"label\").forEach((label) => {\n"
                + "      if (label.textContent) {\n"
                + "        let labelText = label.textContent.trim();\n"
                + "        if (labelText) {\n"
                + "          result.labels.add(labelText); // Using .add() for Set to ensure uniqueness\n"
                + "        }\n"
                + "      }\n"
                + "\n"
                + "      // Handle associated input fields (if the label has a 'for' attribute)\n"
                + "      let forAttribute = label.getAttribute(\"for\");\n"
                + "      if (forAttribute) {\n"
                + "        let associatedInput = element.querySelector(`#${forAttribute}`);\n"
                + "        if (associatedInput) {\n"
                + "          // Check if it's an input field or textarea and extract value or placeholder\n"
                + "          let inputValue = associatedInput.value?.trim();\n"
                + "          let inputPlaceholder = associatedInput.placeholder?.trim();\n"
                + "          if (inputValue) {\n"
                + "            result.text.add(inputValue); // Using .add() for Set to ensure uniqueness\n"
                + "          } else if (inputPlaceholder) {\n"
                + "            result.text.add(inputPlaceholder); // Fallback to placeholder\n"
                + "          }\n"
                + "        }\n"
                + "      }\n"
                + "    });\n"
                + "\n"
                + "    // Extract text from common block and inline elements\n"
                + "    const textExtractors = [\n"
                + "      \"p\",\n"
                + "      \"h1\",\n"
                + "      \"h2\",\n"
                + "      \"h3\",\n"
                + "      \"h4\",\n"
                + "      \"h5\",\n"
                + "      \"h6\",\n"
                + "      \"li\",\n"
                + "      \"span\",\n"
                + "      \"div\",\n"
                + "      \"strong\",\n"
                + "      \"em\",\n"
                + "      \"b\",\n"
                + "      \"i\",\n"
                + "      \"blockquote\",\n"
                + "    ];\n"
                + "\n"
                + "    textExtractors.forEach((tagName) => {\n"
                + "      element.querySelectorAll(tagName).forEach((childElement) => {\n"
                + "        if (childElement.textContent) {\n"
                + "          let elemText = childElement.textContent.trim();\n"
                + "          if (elemText) {\n"
                + "            result.text.add(elemText); // Using .add() for Set to ensure uniqueness\n"
                + "          }\n"
                + "        }\n"
                + "      });\n"
                + "    });\n"
                + "\n"
                + "    // Extract text from <a> tags (links)\n"
                + "    element.querySelectorAll(\"a\").forEach((link) => {\n"
                + "      if (link.textContent) {\n"
                + "        let linkText = link.textContent.trim();\n"
                + "        if (linkText) {\n"
                + "          result.text.add(linkText); // Using .add() for Set to ensure uniqueness\n"
                + "        }\n"
                + "      }\n"
                + "    });\n"
                + "\n"
                + "    // Extract iframe titles and nested content\n"
                + "    element.querySelectorAll(\"iframe\").forEach((iframe) => {\n"
                + "      if (iframe.getAttribute(\"title\")) {\n"
                + "        let title = iframe.getAttribute(\"title\")?.trim();\n"
                + "        if (title) {\n"
                + "          result.titles.add(title); // Using .add() for Set to ensure uniqueness\n"
                + "        }\n"
                + "      }\n"
                + "\n"
                + "      try {\n"
                + "        let iframeDoc =\n"
                + "          iframe.contentDocument ||\n"
                + "          new DOMParser().parseFromString(iframe.srcdoc || \"\", \"text/html\");\n"
                + "        let iframeContent = extractTextFromHTML(iframeDoc); // Here we assume iframeDoc is an element.\n"
                + "        iframeContent.titles.forEach((title) => result.titles.add(title));\n"
                + "        iframeContent.text.forEach((text) => result.text.add(text));\n"
                + "        iframeContent.labels.forEach((label) => result.labels.add(label));\n"
                + "      } catch (e) {\n"
                + "        console.warn(\"Could not access iframe content\", e);\n"
                + "      }\n"
                + "    });\n"
                + "\n"
                + "    // Convert Sets to arrays before returning to maintain previous structure\n"
                + "    return {\n"
                + "      text: Array.from(result.text),\n"
                + "      labels: Array.from(result.labels),\n"
                + "      titles: Array.from(result.titles),\n"
                + "    };\n"
                + "  }\n"
                + "\n"
                + "  function cleanOldValues() {\n"
                + "    window.allElementInfo = [];\n"
                + "  }\n"
                + "\n"
                + "  cleanOldValues();\n"
                + "\n"
                + "  window.revertPickInjections = function () {\n"
                + "    // alert(\"revertPickInjections\");\n"
                + "\n"
                + "    document.removeEventListener(\"mouseover\", showMartiniTooltip);\n"
                + "    document.removeEventListener(\"click\", handleMartiniClick);\n"
                + "    console.log(\"revertPickInjections\");\n"
                + "\n"
                + "    // Remove the tooltip from the page and delete the reference after 5 seconds\n"
                + "    setTimeout(() => {\n"
                + "      removeElements();\n"
                + "      window.allElementInfo = [];\n"
                + "    }, 1000);\n"
                + "  };\n"
                + "\n"
                + "  function removeElements() {\n"
                + "    // Remove highlight from the previous element if any\n"
                + "    if (lastHoveredElement) {\n"
                + "      lastHoveredElement.style.outline = \"\"; // Remove the previous highlight\n"
                + "    }\n"
                + "\n"
                + "    if (tooltip) {\n"
                + "      tooltip.remove(); // Completely remove the tooltip from the DOM\n"
                + "      tooltip = null; // Clear the reference to free memory\n"
                + "      console.log(\"Tooltip completely removed.\");\n"
                + "    }\n"
                + "  }\n"
                + "\n"
                + "  document.addEventListener(\"mouseover\", showMartiniTooltip);\n"
                + "  //                document.addEventListener('mouseout', hideMartiniTooltip);\n"
                + "  document.addEventListener(\"click\", handleMartiniClick);\n"
                + "\n"
                + "  // window.postMessage({ type: \"myMessage\", data: \"some data\" }, targetOriginURL);\n"
                + "\n"
                + "  window.addEventListener(\"message\", function (event) {\n"
                + "    if (event.origin !== trustedOriginURL) return; // check the origin\n"
                + "    console.log(event.data);\n"
                + "  });\n"
                + "})(arguments[0], arguments[1]);\n"
                + "// })(\"http://localhost:3000/\", \"http://localhost:3000/\");\n";

        try {
            jsExecutor = (JavascriptExecutor) driver;
            jsExecutor.executeScript(jsCode, currentUrl);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        // Start a thread to periodically check the XPath value and update the TextField
        new Thread(() -> {
                    while (periodicPickActivated) {

                        // Execute JavaScript to construct and return a custom object
                        LinkedHashMap<String, Object> linkedHashMap =
                                (LinkedHashMap<String, Object>) jsExecutor.executeScript(
                                        "var obj = { allElementInfo: window.allElementInfo }; return obj;");

                        // Convert the LinkedHashMap to a Java Map (if necessary)
                        Map<String, Object> resultMap = new LinkedHashMap<>(linkedHashMap);

                        if (linkedHashMap != null) {
                            Platform.runLater(() -> {
                                Object iframeElementsObject = resultMap.get("allElementInfo");

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

                                if (iFrameElements != null && iFrameElements.length > 0) {

                                    // Extract elements from input lines
                                    elementsFound.clear();
                                    elementsFound = performAction.extractElementData(iFrameElements);
                                    iFrameElements = null;

                                    Optional<ElementDTO> iframeElement = elementsFound.stream()
                                            .filter(element ->
                                                    "clicked-iFrame".equalsIgnoreCase(element.getTypeElement()))
                                            .findFirst();

                                    if (iframeElement.isPresent()) {
                                        if (iframeElement.get().getTypeElement().equals("clicked-iFrame")) {
                                            iFrameText.setText("iFrame Detected");
                                            elementsFound = elementsFound.stream()
                                                    .map(elementDTO -> {
                                                        if ("iFrame-Child".equals(elementDTO.getTypeElement())) {
                                                            elementDTO.setIFrameXPath(iframeElement
                                                                    .get()
                                                                    .getXPath());
                                                        }
                                                        return elementDTO;
                                                    })
                                                    .collect(Collectors.toList());
                                        }
                                    } else {
                                        iFrameText.setText("");
                                    }

                                    checkTestAction.setSelected(false);
                                    checkPickElement.setSelected(false);
                                    revertPickButtons();

                                    if (elementsFound.size() > 0) {
                                        ElementDTO pickTarget = prefillDefinedName(elementsFound);

                                        // Direct Insert to the Factory of Elements
                                        if (iframeElement.isPresent()) {
                                            insertNewElement(iframeElement.get(), elementsFound);
                                        } else {

                                            if (!Strings.isNullOrEmpty(pickTarget.getXPath())
                                                    && !xpathTextPrevious.equalsIgnoreCase(pickTarget.getXPath())) {

                                                TargetElement targetLocal = extractPickClone(pickTarget);

                                                if (targetLocal.getNameField() != null
                                                        && targetLocal.getNameLabel() != null) {
                                                    insertNewElement(elementsFound);
                                                }
                                            }
                                        }

                                        elementsFound.clear();
                                    }

                                } else {
                                    iFrameText.setText("");
                                }
                            });
                        }
                        try {
                            Thread.sleep(300); // Check every 300 milliseconds
                        } catch (InterruptedException e) {
                            ARLogger.getInstance(ARScannedElementPane.class)
                                    .fine(String.format(
                                            "Error Attempt to get currentXPath / tagName / coords", e.getMessage()));
                        }
                    }
                })
                .start();
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
                    || !Strings.isNullOrEmpty(pickTarget.getText())) {
                nameDefined = pickTarget.getTagName()
                        + (!Strings.isNullOrEmpty(pickTarget.getAttribName())
                                ? "-" + pickTarget.getAttribName()
                                : !Strings.isNullOrEmpty(pickTarget.getAttribId())
                                        ? "-" + pickTarget.getAttribId()
                                        : !Strings.isNullOrEmpty(pickTarget.getText())
                                                ? "-" + truncate(pickTarget.getText(), 50)
                                                : "");

            } else if (picked.getAllAttributes() != null) {

                // Split by comma to get key-value pairs
                String[] parts = pickTarget.getAllAttributes().split(",");

                String idValue = null;
                String nameValue = null;
                String typeValue = null;

                // Loop through each key-value pair
                for (String part : parts) {
                    String[] keyValue = part.split("=");

                    if (keyValue.length == 2) { // Ensure valid key-value pair
                        String key = keyValue[0].trim();
                        String value = keyValue[1].trim().replaceAll("\"", ""); // Remove quotes

                        if (key.equals("id")) {
                            idValue = value;
                        } else if (key.equals("name")) {
                            nameValue = value;
                        } else if (key.equals("type")) {
                            typeValue = value;
                        }
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
                sb.append("All Attributes: " + pickTarget.getAllAttributes()).append("\n");
                //
                // sb.append("Attrib Value: " + pickTarget.getAttributeValue())
                //
                //  .append("\n");
                sb.append("Named: " + nameDefined).append("\n");

                iFrameCoords = "";
            }
        }

        Platform.runLater(() -> {
            countdownTextField.setText(sb.toString());
            countdownTextField.setStyle("-fx-font-size: 12px; -fx-text-fill: blue;");
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
                        + "                         left join bot_job bot on bot.home_banking_id = bank.id "
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

    private void loadBotJob(BotJobDTO botJob) {
        String selectSQL =
                " SELECT bot.ID botId, bot.Name botName, blk.ID blockId, blk.Name blockName, blk.block_order_number, "
                        + " blockInstr.id blockInstrId, blockInstr.instruction_order_number instructionOrderNumber, blockInstr.actions, "
                        + " instr.id instId, instr.reference_type, instr.value"
                        + " FROM reference instr "
                        + " join instruction blockInstr on blockInstr.id = instr.instruction_id"
                        + " join bot_job bot on bot.id = " + botJob.getId()
                        + " join block blk on blk.bot_job_id = bot.id "
                        + " order by blockInstr.id, blockInstr.instruction_order_number, instr.id";
        try (Statement stmt = ARSharedResources.getInstance().getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(selectSQL)) {

            List<ReferenceDTO> instructions = new ArrayList<>();

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
                            for (InstructionDTO blockInstruction : block.getBlockLoopInstructionDTOS()) {
                                if (blockInstruction.getId() == Integer.parseInt(blockInstrId)) {
                                    for (ReferenceDTO instructionReference :
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
                                        ReferenceDTO inst = new ReferenceDTO();
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

        if (arWebDriver.getDriver().getWindowHandles().size() != performAction.windowHandlesList.size()) {
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
                    ARPropertyManager.getInstance().getProperty(ARPropertyEnum.WEBDRIVER_PAGE_UPDATE_TIMEOUT_SEC);
            String interactionTimeout =
                    ARPropertyManager.getInstance().getProperty(ARPropertyEnum.WEBDRIVER_PAGE_UPDATE_TIMEOUT_SEC);
            performAction.waitForPage =
                    new WebDriverWait(arWebDriver.getDriver(), Duration.ofSeconds(Integer.parseInt(updateTimeout)));
            performAction.waitForAction = new WebDriverWait(
                    arWebDriver.getDriver(), Duration.ofSeconds(Integer.parseInt(interactionTimeout)));
        }

        if (repository == null) {
            repository = new Repository(ARSharedResources.getInstance().getSession());
        }
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

        ExcelWriter.ExcelChain writerReport =
                new ExcelWriter(botJobLoadList.get(0).getName(), arWebDriver.getDriver(), false).withPurpose("report");
        writerReport.insertReportHead();

        ExcelWriter.ExcelChain writerExport = null;
        //                new ExcelWriter(blocksLoaded.get(0).getName(),
        // arWebDriver.getDriver()).withPurpose("export");
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
                                performAction.logAndReport(
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
                                performAction.logAndReport(
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

                                performAction.gotoLimitExecution(limit, resultActions);

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
                    performAction.logAndReport(
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
                    performAction.logAndReport(
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

                    performAction.onHoldInSeconds(blockWait);

                    msgBlock = new Pair(
                            String.format("Default Wait: \"%s\" ->  %d Seconds", blockLoad.getName(), blockWait),
                            ARConstants.HOLD);

                    // Excel Report and Log
                    performAction.logAndReport(
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
                                    new Pair(String.format("Ignore: \"%s\"", nameInstruc), ARConstants.IGNORE);

                            // Excel Report and Log
                            performAction.logAndReport(
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
                        if (actions[0].equalsIgnoreCase(ARConstants.INSERT)) {
                            String reference = actions[1];
                            valueInsert = dataExcel.get(reference);
                        }

                        Pair<String, String> msgInstruction = null;
                        if (actions[0].equalsIgnoreCase(ARConstants.GOTO)) {
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

                        } else if (actions[0].equalsIgnoreCase(ARConstants.LOOP)) {
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
                        } else if (actions[0].equalsIgnoreCase(ARConstants.REFRESH_LOOP)) {
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

                        resultActions = performAction.actionResultMessage(blockName, actions, msgInstruction);

                        extraMsg = "";

                        if (actions[0].equalsIgnoreCase(ARConstants.PAUSE)) {
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

                        if (actions[0].equalsIgnoreCase(ARConstants.LOOP)) {
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

                        } else if (actions[0].equalsIgnoreCase(ARConstants.REFRESH_ONLY)) {
                            refreshOnly = true;
                        } else if (actions[0].equalsIgnoreCase(ARConstants.REFRESH_LOOP)) {
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
                        } else if (actions[0].equalsIgnoreCase(ARConstants.GET_VALUE)
                                || actions[0].equalsIgnoreCase(ARConstants.SET_VALUE)) {

                            execGetOrSet = true;

                            getAction = actions[0].equalsIgnoreCase(ARConstants.GET_VALUE);

                            xPathOperation = performAction.getXPathInstruction(currentInstruction, blockLoad);
                            parentField = performAction.getInstructionParentField(currentInstruction, blockLoad);

                        } else if (actions[0].equalsIgnoreCase(ARConstants.CHECK_VALUE)) {
                            execCheckValue = true;
                            parentField = performAction.getInstructionParentField(currentInstruction, blockLoad);
                        } else if (actions[0].equalsIgnoreCase(ARConstants.EXTRACT_FIELD)) {
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

                                        ARLogger.getInstance(ARScannedElementPane.class)
                                                .info(String.format(
                                                        "Loop to Parent :\"%s\" - %d Times",
                                                        parts[0] + "-(" + parts[1] + ") " + parts[2],
                                                        mapLoops.get(parentFieldLoop)));

                                        if (refreshLoop) {

                                            String extraLog = performAction.actionResultMessage(
                                                    blockName, new String[] {ARConstants.REFRESH_HOLD}, msgInstruction);

                                            performAction.performOtherActions(
                                                    byPassNotFound,
                                                    currentInstruction,
                                                    new String[] {ARConstants.REFRESH_HOLD});

                                            // Excel Report and Log
                                            performAction.logAndReport(
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
                                            extraLog = performAction.actionResultMessage(
                                                    blockName, new String[] {ARConstants.REFRESH_ONLY}, msgInstruction);

                                            performAction.performOtherActions(
                                                    byPassNotFound,
                                                    currentInstruction,
                                                    new String[] {ARConstants.REFRESH_ONLY});

                                            // Excel Report and Log
                                            performAction.logAndReport(
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
                                        ARLogger.getInstance(ARScannedElementPane.class)
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

                            } else if (actions[0].equals(ARConstants.HOLD)
                                    || actions[0].equals(ARConstants.QUIT)
                                    || actions[0].equals(ARConstants.SCREEN)
                                    || actions[0].equals(ARConstants.REFRESH_ONLY)) {

                                performAction.performOtherActions(byPassNotFound, currentInstruction, actions);

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
                                    if (actions[0].equalsIgnoreCase(ARConstants.VISUALIZE)
                                            || actions[0].equalsIgnoreCase(ARConstants.CLICK)
                                            || actions[0].equalsIgnoreCase(ARConstants.INSERT)) {
                                        success = performAction.executeActionsAtCoordinates(
                                                mapSavedLocators.get("coordinates"), fieldData, actions[0]);
                                    }
                                }

                                byPassNotFound =
                                        byPassFlagLoop || !currentCondition.equals(ARConstants.ConditionStatus.NONE);

                                if (webElementFound != null && success) {

                                    success = performAction.performWebActions(
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
                                        writerExport = new ExcelWriter(excelFieldName, arWebDriver.getDriver(), true)
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
                                && !currentCondition.equals(ARConstants.ConditionStatus.NONE)) {
                            progressCondition = performAction.updateProgressSuccess(success, currentCondition);
                            //                                continue instructionLoop;
                        } else {
                            progressCondition = ARConstants.ConditionStatus.NONE;
                        }

                        // Excel Report and Log
                        performAction.logAndReport(
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
                            performAction.logAndReport(
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
                                currentCondition = ARConstants.ConditionStatus.NONE;
                                progressCondition = ARConstants.ConditionStatus.NONE;
                                continue instructionLoop;
                            }
                        }

                        // Conditions When Fails to any of then and Look for the next Correct Block
                        if (progressCondition.equals(ARConstants.ConditionStatus.IF_FAILED)
                                || progressCondition.equals(ARConstants.ConditionStatus.ELSEIF_FAILED)) {

                            // Goes to the next ELSEIF IF EXIST (ELSEIF index + 1);
                            int index = performAction.searchMapConditional(
                                    mapConditional,
                                    parentBlockCondition,
                                    ARConstants.ConditionStatus.ELSEIF,
                                    currentIndex,
                                    false);

                            // Goes to the next ELSE IF ELSEIF  DOES NOT EXIST  (ELSE index + 1);
                            if (index < 0) {
                                index = performAction.searchMapConditional(
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
                            int index = performAction.searchMapConditional(
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
                List<BlockLoopInstructionLoadDTO> unexecutedInstructions = getUnexecutedInstructions(
                        instructionsExecuted, blocksLoaded.get(j).getBlockLoopInstructionLoadDTOS());

                for (BlockLoopInstructionLoadDTO currentInstruction : unexecutedInstructions) {
                    if (currentInstruction.getDefaultValue() == null) {
                        String[] arr = UtilsMethods.splitIfContains(
                                currentInstruction.getActions(), ARConstants.ACTION_SPECIFICATIONS_SPLITTER);
                        if (arr.length > 1) {
                            String dataFieldName = arr[1].split(ARConstants.PATH_FIELD_SUBSTITUTION)[0];
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

                    String[] actions = currentInstruction.getActions().split(ARConstants.ACTIONS_AND_PATHS_SPLITTER);

                    // Case for Inputs
                    String valueInsert = "No Data Found";
                    if (actions[0].equalsIgnoreCase(ARConstants.INSERT)) {

                        String reference = actions[1];
                        valueInsert = dataExcel.get(reference);
                    }

                    Pair<String, String> msgInstruction = new Pair(
                            currentInstruction.getName(),
                            (currentInstruction.getOperation() != null
                                    ? currentInstruction.getOperation()
                                    : (actions[0].equalsIgnoreCase(ARConstants.INSERT)) ? valueInsert : ""));

                    resultActions = performAction.actionResultMessage(blockName, actions, msgInstruction);

                    try {

                        if (actions[0].equals(ARConstants.HOLD)
                                || actions[0].equals(ARConstants.QUIT)
                                || actions[0].equals(ARConstants.SCREEN)
                                || actions[0].equals(ARConstants.REFRESH_ONLY)) {
                            performAction.performOtherActions(byPassNotFound, currentInstruction, actions);

                            if (actions[0].equals(ARConstants.QUIT)) {
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
        allAttributesTextField.setText("");
        currentXPathTextField.setText("");
        iFrameXPath = "";
        iFrameElements = null;
        coordsTextField.setText("");
        customXPathTextField.setText("");
        countdownTextField.setText("10");
        countdownTextField.setStyle("-fx-font-size: 12px; -fx-text-fill: blue;");
    }

    private void fillUpCurretLocators(BlockLoopInstructionLoadDTO currentInstruction) {
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

    private void executeAlert(InstructionDTO instruction) {
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

        new ExcelWriter(botJobName, arWebDriver.getDriver(), false)
                .withPurpose("excel")
                .insertValueFieldName(parentId + "-" + fieldName, innerHTMLValue);
        return action + " fieldName " + fieldName;
    }

    public void quit(int status) {
        arWebDriver.getDriver().quit();
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
                String xpath = entry.getKey();
                String attributeValue = entryElem.getAttribute(attributeName);

                if (Strings.isNullOrEmpty(attributeValue)) {
                    attributeValue = "(" + attributeName + ") has no value";
                }
                System.out.println("AR Element Creation ->  Tag: " + entryElem.getTagName() + ", " + attributeName
                        + ": " + attributeValue + ", XPath: " + xpath);

                try {

                    if (listARElements.size() < 30) {
                        addProgressBar(1);
                    }

                    ElementDTO elementDTO = new ElementDTO();

                    TargetElement targetLocal = performAction.defineSearchReturn(elementDTO, entryElem, null);

                    targetLocal = performAction.defineTargetNameTitles(targetLocal);

                    targetLocal.setElement(entryElem);

                    if (targetLocal.getDefinedName() != null) {
                        ARWebElement arWebElement = new ARWebElement(targetLocal, botJob.getId());
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

                            Platform.runLater(() -> {
                                try {
                                    Thread.sleep(2000);
                                    bottomPane.getChildren().clear();
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }

                                scannedElements1.requestLayout();
                                scannedElements1.refresh();
                                scannedElements2.requestLayout();
                                scannedElements2.refresh();
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
                + "\n"
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
            boolean exportToAR,
            String xPath,
            String coordinates,
            String iFrameXPath,
            Integer currentBotJobId,
            Integer currentBlockId) {

        BlockLoopInstructionLoadDTO instructionDTO = new BlockLoopInstructionLoadDTO();

        instructionDTO.setPath(xPath);
        instructionDTO.setCoordinates(coordinates);
        instructionDTO.setIFrameXPath(iFrameXPath);
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
        instructionDTO.setExportToAR(exportToAR);
        instructionDTO.setInstructionActive(true);

        // Wrap the persistence in a try-catch block
        int newId = -1;

        try {
            newId = performDataBase.insertInstruction(instructionDTO, currentBotJobId, currentBlockId);

        } catch (Exception e) {

            ARLogger.getInstance(ARScannedElementPane.class)
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
                "", ARConstants.SPACE_L, ARConstants.ICON_REFRESH, ARConstants.SPACE_M, new Insets(3D));
        button.setMaxWidth(ARConstants.SPACE_L);
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
                ARScannedElementPane.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }

            // Convert InputStream to String
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

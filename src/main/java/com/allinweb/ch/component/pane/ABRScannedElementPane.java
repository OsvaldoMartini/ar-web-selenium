package com.allinweb.ch.component.pane;

import com.allinweb.ch.builder.WebElementAttributeEnum;
import com.allinweb.ch.builder.WebElementAttributeTypeValueEnum;
import com.allinweb.ch.builder.WebElementTagNameEnum;
import com.allinweb.ch.component.listCell.ABRCellFactory;
import com.allinweb.ch.component.listCell.ABRWebElementListCell;
import com.allinweb.ch.component.model.BlockLoadDTO;
import com.allinweb.ch.component.model.BlockLoopInstructionLoadDTO;
import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.model.ComplexInstructionLoadDTO;
import com.allinweb.ch.component.model.InstructionReferenceLoadDTO;
import com.allinweb.ch.component.pane.base.ABRPane;
import com.allinweb.ch.component.scene.ABRAlertScene;
import com.allinweb.ch.component.scene.ABRNewHomeBankingScene;
import com.allinweb.ch.control.ABRComponentBuilder;
import com.allinweb.ch.core.ABRSharedResources;
import com.allinweb.ch.driver.ABRWebDriver;
import com.allinweb.ch.driver.ABRWebElement;
import com.allinweb.ch.persistence.*;
import com.allinweb.ch.readersAndWriters.ExcelReader;
import com.allinweb.ch.readersAndWriters.ExcelWriter;
import com.allinweb.ch.util.*;
import com.google.common.base.Strings;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
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
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.Alert;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javax.net.ssl.*;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.remote.RemoteWebElement;
import org.openqa.selenium.support.pagefactory.ByChained;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Wait;
import org.openqa.selenium.support.ui.WebDriverWait;

public class ABRScannedElementPane extends ABRPane {

    private Connection conn = null;

    private ExecutorService executorService;
    private static final int SECONDS = 3; // Total seconds for the countdown
    private int remainingSeconds = SECONDS;
    private Timeline timeline;
    private Alert alertToShow;
    public static Repository repository;

    private static SearchReturn searchReturn = new SearchReturn();

    private static Wait<WebDriver> waitForPage;
    private static Wait<WebDriver> waitForAction;
    private boolean justCalledRefreshPage = false;

    private static File baseLogFile = null;
    private static SimpleDateFormat dateFormatter;

    private static JavascriptExecutor jsExecutor;

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int MIN_LENGTH = 3;
    private static final int MAX_LENGTH = 30;
    private static final Random RANDOM = new Random();
    private static final DateTimeFormatter FORMAT_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ABRComponentBuilder componentBuilder = new ABRComponentBuilder();

    private DatabaseUserDTO databaseUserDto;

    private ABRWebDriver abrWebDriver;
    private BotJobDTO botJob;
    private BlockDTO block;

    private List<BotJobLoadDTO> botLoadJobs = new ArrayList<>();

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
    private Button addWaitButton30;
    private Button addWaitButton15;
    private Button addWaitButton5;
    private Button addNewElement;
    private Button addCloseActionButton;
    private Button addScreenButton;
    private Button configureButton;
    private Button launchBotJobButton;
    private Button recallJobButton;
    private Button searchWithIdsButton;
    private Button searchWithNamesButton;
    private Button searchWithoutIdsAndNamesBtn;
    private Button refreshInputFieldsButton;
    private Button refreshOutputFieldsButton;
    private Button refreshOtherFieldsButton;
    private CheckBox checkBoxAction;
    private CheckBox checkActiveHover;
    private CheckBox checkClickElement;
    private CheckBox checkInputText;

    private Label defineNameLabel;
    private Label attribIdTextFieldLabel;
    private Label attribNameTextFieldLabel;
    private Label currentXPathLabel;
    private Label currentAbsoluteXPathLabel;
    private Label customXPathLabel;
    private Label originalTagNameLabel;
    private Label coordsTextFieldLabel;

    private TextField defineNameField;
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

    List<BlockLoopInstructionLoadDTO> instructionsExecuted = new ArrayList<>();

    Map<String, WebElement> mapAdvanced = new HashMap<>();

    // Very important sequence on initiation
    private static ABRPriorities abrPriorities;
    private static Map<String, String> savedReferences;
    private static int reduceSearchCriteria;
    private static ABRPropertyManager managerProps;
    // Static block to initialize
    static {
        abrPriorities = ABRPriorities.getInstance();
        savedReferences = new HashMap<>();
        managerProps = ABRPropertyManager.getInstance();
    }

    public ABRScannedElementPane(String priority, BotJobDTO botJob, BlockDTO block, ABRWebDriver abrWebDriver) {
        super();

        ABRLogger.getInstance(ABRWebDriver.class).fine("Calling ABRScannedElementPane");

        if ((botJob != null && abrPriorities.getJobId() == null) || (abrPriorities.getJobId() != botJob.getId())) {
            abrPriorities.setJobId(botJob.getId());
            if (botJob.getHomeBanking().getPriority() != null) {
                abrPriorities.loadPrioritiesFromString(botJob.getHomeBanking().getPriority());
                abrPriorities.loadSearchElementsConfig(botJob.getHomeBanking().getSearchConfig());
            } else {
                abrPriorities.loadPriorities();
                abrPriorities.loadSearchElementsConfig(botJob.getHomeBanking().getSearchConfig());
            }
        }

        this.botJob = botJob;
        this.block = block;
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

        abrWebDriver.openDriver(
                botJob.getHomeBanking().getUrl(),
                botJob.getHomeBanking().getOptionsConfig().toString());

        topPane = componentBuilder.createTopPanel(ABRConstants.SPACE_L, ABRConstants.SPACE_SM);
        bottomPane = componentBuilder.createBottomPanel(ABRConstants.SPACE_L, ABRConstants.SPACE_SM);
        bottomPaneTime = componentBuilder.createBottomPanel(ABRConstants.SPACE_L, ABRConstants.SPACE_SM);
        contentPane =
                componentBuilder.createContentPanel(ABRConstants.SPACE_L, ABRConstants.SPACE_XL, ABRConstants.SPACE_SM);

        scanButton = componentBuilder.buildButton(
                "Scan", ABRConstants.SPACE_L, ABRConstants.ICON_SEARCH, ABRConstants.SPACE_M, new Insets(5));
        addWaitButton30 = componentBuilder.buildButton(
                "30s", ABRConstants.SPACE_L, ABRConstants.ICON_WAIT, ABRConstants.SPACE_M, new Insets(5));

        addWaitButton15 = componentBuilder.buildButton(
                "15s", ABRConstants.SPACE_L, ABRConstants.ICON_WAIT, ABRConstants.SPACE_M, new Insets(5));

        addWaitButton5 = componentBuilder.buildButton(
                "5s", ABRConstants.SPACE_L, ABRConstants.ICON_WAIT, ABRConstants.SPACE_M, new Insets(5));

        addNewElement = componentBuilder.buildButton(
                "Add Element", ABRConstants.SPACE_L, ABRConstants.ICON_TICK, ABRConstants.SPACE_M, new Insets(5));

        addCloseActionButton = componentBuilder.buildButton(
                "Add Close Browser",
                ABRConstants.SPACE_L,
                ABRConstants.ICON_CROSS,
                ABRConstants.SPACE_M,
                new Insets(5));
        addScreenButton = componentBuilder.buildButton(
                "Add Screenshot", ABRConstants.SPACE_L, ABRConstants.ICON_SCREEN, ABRConstants.SPACE_M, new Insets(5));
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
        checkBoxAction = new CheckBox("Test Action\n(RELEASE AFTER USE)");
        checkClickElement = new CheckBox("For Click");
        checkClickElement.setSelected(true);
        checkInputText = new CheckBox("For Input");

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
        countdownTextField.setStyle("-fx-font-size: 18px; -fx-text-fill: blue;");
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
        absolutXPathTextField = new TextField();
        absolutXPathTextField.setPromptText("Absolut XPath");
        customXPathTextField = new TextField();
        customXPathTextField.setPromptText("Custom XPath");
        originalTagNameField = new TextField();
        originalTagNameField.setPromptText("Tag Name");
        coordsTextField = new TextField();
        coordsTextField.setPromptText("Coordinates");
        try {
            // Starting the View

            // Create a GridPane for the top section
            GridPane gridPaneTop = new GridPane();
            gridPaneTop.setPadding(new Insets(10));
            gridPaneTop.setHgap(10); // Set horizontal gap between columns

            // Add buttons and checkbox to the GridPane
            gridPaneTop.add(scanButton, 0, 0);
            gridPaneTop.add(addWaitButton30, 1, 0);
            gridPaneTop.add(addWaitButton15, 2, 0);
            gridPaneTop.add(addWaitButton5, 3, 0);
            gridPaneTop.add(addCloseActionButton, 4, 0);
            gridPaneTop.add(addScreenButton, 5, 0);
            //        gridPaneTop.add(configureButton, 4, 0);
            //        gridPaneTop.add(launchBotJobButton, 5, 0);
            //        gridPaneTop.add(checkActiveHover, 6, 0);
            //        gridPaneTop.add(addNewElement, 7, 0);
            //        gridPaneTop.add(currentXPathTextField, 8, 0);

            VBox vBoxCheckBox = new VBox();
            vBoxCheckBox.getChildren().addAll(checkClickElement, checkInputText);
            vBoxCheckBox.setSpacing(6); // Adjust spacing between CheckBoxes
            //        gridPaneTop.add(vBox, 9, 0);

            topPane.getChildren().add(gridPaneTop); // Add gridPaneTop to topPane

            VBox verticalBox = new VBox();
            verticalBox.setSpacing(10);
            verticalBox.setPadding(new Insets(10));
            VBox.setVgrow(verticalBox, Priority.ALWAYS);

            // Create a GridPane for the middle section
            GridPane gridPane = new GridPane();
            gridPane.setPadding(new Insets(10));
            gridPane.setHgap(10); // Set horizontal gap between columns

            // Add buttons and checkbox to the GridPane
            gridPane.add(refreshInputFieldsButton, 0, 0);
            gridPane.add(searchWithIdsButton, 1, 0);
            gridPane.add(searchWithNamesButton, 2, 0);
            gridPane.add(searchWithoutIdsAndNamesBtn, 3, 0);
            gridPane.add(refreshOutputFieldsButton, 4, 0);
            gridPane.add(refreshOtherFieldsButton, 5, 0);
            //        gridPane.add(checkBoxAction, 6, 0);
            //        gridPane.add(originalTagNameField, 7, 0);
            //        gridPane.add(coordsTextField, 8, 0);

            // Create an HBox to hold launchBotJobButton and recallJobButton
            HBox hBoxLaunchButon = new HBox();
            hBoxLaunchButon.setSpacing(10); // Optional: adjust spacing between buttons

            // Add buttons to the HBox
            hBoxLaunchButon.getChildren().addAll(launchBotJobButton, recallJobButton);

            // Create the VBox for TextFields
            VBox textFieldVBox = new VBox();
            textFieldVBox.setSpacing(6); // Adjust spacing between TextFields
            textFieldVBox
                    .getChildren()
                    .addAll(
                            checkActiveHover,
                            defineNameLabel,
                            defineNameField,
                            attribIdTextFieldLabel,
                            attribIdTextField,
                            attribNameTextFieldLabel,
                            attribNameTextField,
                            currentXPathLabel,
                            currentXPathTextField,
                            currentAbsoluteXPathLabel,
                            absolutXPathTextField,
                            customXPathLabel,
                            customXPathTextField,
                            originalTagNameLabel,
                            originalTagNameField,
                            coordsTextFieldLabel,
                            coordsTextField,
                            vBoxCheckBox,
                            addNewElement,
                            createCustomSeparator(Color.DARKBLUE, 2),
                            createSpacer(),
                            countdownTextField,
                            checkBoxAction,
                            createSpacer(),
                            createCustomSeparator(Color.DARKBLUE, 2),
                            hBoxLaunchButon,
                            configureButton);

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

            boxListViews.getChildren().addAll(scannedElements1, scannedElements2, scannedElements3, textFieldVBox);

            VBox.setVgrow(boxListViews, Priority.ALWAYS);

            verticalBox.getChildren().addAll(gridPane, boxListViews);
            VBox.setVgrow(verticalBox, Priority.ALWAYS);

            VBox.setVgrow(bottomPane, Priority.NEVER);
            VBox.setVgrow(bottomPaneTime, Priority.NEVER);

            contentPane.getChildren().addAll(topPane, verticalBox, bottomPaneTime, bottomPane);

            AnchorPane.setBottomAnchor(bottomPane, -15.0);

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

    private Node createSpacer() {
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
                e.printStackTrace();
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
        configureButton.setOnMouseClicked(e -> new ABRNewHomeBankingScene().show());
        launchBotJobButton.setOnMouseClicked(e -> {
            //                        loadBotJob(botJob);
            loadBlockAll(botJob.getId());
            instructionsExecuted.clear();

            // Set all instructions' executed field to false
            botLoadJobs.get(0).getBlockLoadDTOList().stream()
                    .flatMap(block -> block.getBlockLoopInstructionLoadDTOS().stream())
                    .forEach(instruction -> instruction.setExecuted(false));

            recallJob();
        });

        recallJobButton.setOnMouseClicked(e -> {
            loadBlockAll(botJob.getId());
            // loadBotJob(botJob);
            recallJob();
        });
        checkActiveHover.setOnMouseClicked(e -> handleHoverCheckClick());
        checkClickElement.setOnAction(event -> {
            if (checkClickElement.isSelected()) {
                checkInputText.setSelected(false);
            } else {
                checkInputText.setSelected(true);
            }
        });

        checkInputText.setOnAction(event -> {
            if (checkInputText.isSelected()) {
                checkClickElement.setSelected(false);
            } else {
                checkClickElement.setSelected(true);
            }
        });
        scanButton.setOnAction(e -> manageUIScan());
        addWaitButton30.setOnAction(e -> addWaitTask(30));
        addWaitButton15.setOnAction(e -> addWaitTask(15));
        addWaitButton5.setOnAction(e -> addWaitTask(5));
        addNewElement.setOnAction(e -> {
            if (searchReturn.getElement() != null) {
                insertNewElement();
            }
        });
        addCloseActionButton.setOnAction(e -> addCloseBrowserTask());
        addScreenButton.setOnAction(e -> addScreenTask());

        refreshInputFieldsButton.setOnAction(e -> refreshInputBtn());
        refreshOutputFieldsButton.setOnAction(e -> refreshOutputBtn());
        refreshOtherFieldsButton.setOnAction(e -> refreshOtherElemBtn());
        searchWithIdsButton.setOnAction(e -> refreshWithIdsBtn());
        searchWithNamesButton.setOnAction(e -> refreshWithNamesBtn());
        searchWithoutIdsAndNamesBtn.setOnAction(e -> refreshWithoutIdsAndNamesBtn());

        scannedElements1.getItems().addListener(this::addBehaviourToAddedElements);
        scannedElements2.getItems().addListener(this::addBehaviourToAddedElements);
        scannedElements3.getItems().addListener(this::addBehaviourToAddedElements);

        //        manageUIScan();
    }

    private void insertNewElement() {

        if (Strings.isNullOrEmpty(defineNameField.getText())) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("MANDATORY FIELD");
            alert.setHeaderText("Define the Element Name");
            alert.setContentText("Element name must be defined!");
            alert.showAndWait();
            return;
        }

        if (searchReturn != null) {

            searchReturn.setDefinedName(defineNameField.getText());

            try {
                if (searchReturn.getElement() == null) {
                    // First  Search for xPath
                    searchWebElementSequence();
                }

                if (searchReturn.getElement() != null) {

                    // Last Moment to Be Change by the User
                    if (checkClickElement.isSelected()) {
                        this.searchReturn.setForceTypeEnum(WebElementTagNameEnum.BUTTON);
                    } else if (checkInputText.isSelected()) {
                        this.searchReturn.setForceTypeEnum(WebElementTagNameEnum.INPUT);
                    }

                    ABRWebElement abrWebElement = new ABRWebElement(this.searchReturn, botJob.getId());
                    if (abrWebElement != null) {
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

                Boolean clickable = isClickable(searchReturn.getElement());
                Platform.runLater(() -> {
                    checkClickElement.setSelected(clickable);
                    checkInputText.setSelected(!clickable);
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
                                Constants.REGULAR_XPATH); // BECAUSE OS LIMITATION OF ACCESS DB 255 CHARACTER
                    }
                } catch (Exception e) {
                    ABRLogger.getInstance(ABRScannedElementPane.class)
                            .fine(String.format(
                                    "Cannot locate anWeb Element with Regular XPath\n%s",
                                    searchReturn.getCurrentXPath()));
                }
            }
            if (element == null) {
                try {
                    element = abrWebDriver.getDriver().findElement(By.xpath(searchReturn.getAbsolutXPath()));
                    if (element != null) {
                        searchReturn.setElement(element);
                        searchReturn.setxPathWorkedFirst(
                                Constants.ABSOLUT_XPATH); // BECAUSE OS LIMITATION OF ACCESS DB 255 CHARACTER
                    }
                } catch (Exception e) {
                    ABRLogger.getInstance(ABRScannedElementPane.class)
                            .fine(String.format(
                                    "Cannot locate anWeb Element with Absolut XPath\n%s",
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
                                .fine(String.format(
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
                                .fine(String.format(
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
        manageUIScanWithoudNameAndId();
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
            periodicThread(abrWebDriver.getDriver());
            //            injectJavaScript(abrWebDriver.getDriver());
            //            injectJumpTab(abrWebDriver.getDriver());
        } else {
            revertInjectedChanges(abrWebDriver.getDriver());
        }
        //        checkClickElement.setDisable(checkActiveHover.isSelected());
        //        checkInputText.setDisable(checkActiveHover.isSelected());
        //        addNewElement.setDisable(checkActiveHover.isSelected());
        launchBotJobButton.setDisable(checkActiveHover.isSelected());
        recallJobButton.setDisable(checkActiveHover.isSelected());
        periodicActivated = checkActiveHover.isSelected();
    }

    private void manageUIScan() {
        ABRLogger.getInstance(ABRScannedElementPane.class).info("General scan triggered");
        webElementObservableList1.clear();
        webElementObservableList2.clear();
        webElementObservableList3.clear();
        manageUIScanIdsFirst();
        manageUIScanAttributeNameFirst();
        manageUIScanWithoudNameAndId();

        manageUIScanPriorities();
        manageUIScanInputs();
        manageUIScanClickable();
        //        manageUIScanOutputs();
    }

    private void manageUIScanWithoudNameAndId() {
        idAttributeFirst = false;
        nameAttributeFirst = false;
        withoutNameAndId = true;
        // addProgressBar();
        scanABRElementsAsync(null, null, null, webElementObservableList1, "input");
        // addProgressBar();
        scanABRElementsAsync(null, null, null, webElementObservableList1, "button");
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
        scanABRElementsAsync(null, null, null, webElementObservableList1, "name");
    }

    private void manageUIScanIdsFirst() {
        idAttributeFirst = true;
        nameAttributeFirst = false;
        withoutNameAndId = false;
        scanABRElementsAsync(null, null, null, webElementObservableList1, "id");
    }

    private void manageUIScanInputs() {
        List<WebElementTagNameEnum> inputTags = WebElementTagNameEnum.insertableTags();
        for (WebElementTagNameEnum tag : inputTags) {
            // addProgressBar();
            scanABRElementsAsync(
                    null, By.tagName(tag.getValue()), ABRWebElement::isNotClickable, webElementObservableList1, null);
        }
    }

    private void manageUIScanClickable() {
        List<WebElementTagNameEnum> clickableTags = WebElementTagNameEnum.clickableTags();
        for (WebElementTagNameEnum tag : clickableTags) {
            // addProgressBar();
            scanABRElementsAsync(
                    null, By.tagName(tag.getValue()), ABRWebElement::isClickable, webElementObservableList2, null);
        }
    }

    private void manageUIScanPriorities() {
        List<WebElement> webElements = managePrioritiesCriteria();
        //        manageUIScanPrioritiesJSoup();
        //        scanABRElementsAsync(By.cssSelector("*[" + extRef + "]"), webElementObservableList3);
        try {
            if (webElements != null && webElements.size() > 0) {
                // addProgressBar();
                scanABRElementsAsync(webElements, null, null, webElementObservableList3, null);
            }
        } catch (Exception e) {
            System.out.println("Error " + e.getMessage());
        }
    }

    private void manageUIScanOutputs() {
        String extRef = ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.WEBDRIVER_EXT_REFERENCE);
        // addProgressBar();
        scanABRElementsAsync(By.cssSelector("*[" + extRef + "]"), webElementObservableList2);
    }

    private void scanABRElementsAsync(By criteria, ObservableList<ABRWebElement> listToAddNewElements) {
        scanABRElementsAsync(null, criteria, null, listToAddNewElements, null);
    }

    private void scanABRElementsAsync(
            List<WebElement> preElements,
            By criteria,
            Predicate<ABRWebElement> filterCondition,
            ObservableList<ABRWebElement> listToAddNewElements,
            String elementType) {

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
                                .fine("Starting scan of elements for criteria: " + criteria);

                        if (idAttributeFirst || nameAttributeFirst) {
                            mapAdvanced = findElementsWithXPath(abrWebDriver.getDriver(), elementType);
                            listABRElements = createAdvancedABRElement(mapAdvanced, elementType);
                        } else if (withoutNameAndId) {
                            mapAdvanced = findElementsWithoutIdOrName(abrWebDriver.getDriver(), elementType);
                            listABRElements = createAdvancedABRElement(mapAdvanced, elementType);
                        } else if (preElements != null && preElements.size() > 0) {
                            scannedElementList.addAll(preElements);
                        } else if (criteria != null) {
                            scannedElementList = abrWebDriver.getDriver().findElements(criteria);
                        }
                        if (listABRElements != null && listABRElements.size() > 0) {
                            listABRElementsSize.set(listABRElements.size());
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
                            try {
                                listABRElements = scannedElementList.stream()
                                        .filter(element -> element != null) // Filter out null elements
                                        //                                        .peek(element -> addProgressBar(1))
                                        .map(element -> new ABRWebElement(element, botJob.getId()))
                                        .collect(Collectors.toList());
                                listABRElementsSize.set(listABRElements.size());

                            } finally {
                                ABRLogger.getInstance(ABRScannedElementPane.class)
                                        .fine("Final size of listABRElements: " + listABRElementsSize.get());
                                ABRLogger.getInstance(ABRScannedElementPane.class)
                                        .fine("Final size of scannedElementList: " + scannedElementListSize.get());
                            }
                        }

                    } catch (Exception e) {
                        shutDownExecutorService();
                        Thread.currentThread().interrupt();
                    }

                    // After Creation of ABR Elements - > Update View List
                    if (listABRElements != null) {
                        addProgressBar(listABRElements.size());
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

    private void addWaitTask(Integer secondsToWait) {
        Alert alert = new Alert(
                Alert.AlertType.CONFIRMATION,
                "Are you sure you want to add a wait of " + secondsToWait + " seconds to the botjob?",
                ButtonType.YES,
                ButtonType.NO);
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            Task<Void> waitTask = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    List<BlockLoopInstructionDTO> instructionList = block.getBlockLoopInstructions();
                    BlockLoopInstructionDTO waitInstruction = new BlockLoopInstructionDTO();
                    waitInstruction.setName("Wait " + secondsToWait + "second(s)");
                    waitInstruction.setDescription("Waiting action");
                    waitInstruction.setEncrypted(false);
                    waitInstruction.setInstructionOrderNumber(instructionList.size());
                    waitInstruction.setOptional(false);
                    waitInstruction.setActions(ABRConstants.HOLD);
                    waitInstruction.setOnHoldSeconds(secondsToWait);
                    waitInstruction.setBlock(block);
                    waitInstruction.setExportToABR(false);
                    ABRSharedResources.getInstance()
                            .addEntity(
                                    waitInstruction,
                                    BlockLoopInstructionDTO.class,
                                    () -> new ABRAlertScene(
                                            Alert.AlertType.INFORMATION,
                                            "Instruction Added",
                                            "Instruction Wait 30 second(s) has been added successfully",
                                            ButtonType.OK));
                    return null;
                }
            };
            new Thread(waitTask).start();
        }
    }

    private void addCloseBrowserTask() {
        Alert alert = new Alert(
                Alert.AlertType.CONFIRMATION,
                "Are you sure you want to add the browser closing action to the bot job?",
                ButtonType.YES,
                ButtonType.NO);
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            Task<Void> addCloseTask = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    List<BlockLoopInstructionDTO> instructionList = block.getBlockLoopInstructions();
                    BlockLoopInstructionDTO closeInstruction = new BlockLoopInstructionDTO();
                    closeInstruction.setName("Close Browser");
                    closeInstruction.setDescription("Close Browser");
                    closeInstruction.setEncrypted(false);
                    closeInstruction.setInstructionOrderNumber(instructionList.size() + 1);
                    closeInstruction.setOptional(false);
                    closeInstruction.setActions(ABRConstants.QUIT);
                    closeInstruction.setOnHoldSeconds(0);
                    closeInstruction.setBlock(block);
                    closeInstruction.setExportToABR(false);
                    ABRSharedResources.getInstance()
                            .addEntity(
                                    closeInstruction,
                                    BlockLoopInstructionDTO.class,
                                    () -> new ABRAlertScene(
                                            Alert.AlertType.INFORMATION,
                                            "Instruction Added",
                                            "Instruction Close Browser has been added successfully",
                                            ButtonType.OK));
                    return null;
                }
            };
            new Thread(addCloseTask).start();
        }
    }

    private void refreshInputBtn() {
        webElementObservableList1.clear();
        manageUIScanInputs();
    }

    private void refreshOutputBtn() {
        webElementObservableList2.clear();
        manageUIScanClickable();
        manageUIScanOutputs();
    }

    private void refreshOtherElemBtn() {
        webElementObservableList3.clear();
        manageUIScanPriorities();
    }

    private void addScreenTask() {
        Alert alert = new Alert(
                Alert.AlertType.CONFIRMATION,
                "Are you sure you want to add a screenshot of the browser to the bot job?",
                ButtonType.YES,
                ButtonType.NO);
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            Task<Void> addCloseTask = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    List<BlockLoopInstructionDTO> instructionList = block.getBlockLoopInstructions();
                    BlockLoopInstructionDTO screenInstruction = new BlockLoopInstructionDTO();
                    screenInstruction.setName("Screenshot Browser");
                    screenInstruction.setDescription("Screenshot Browser");
                    screenInstruction.setEncrypted(false);
                    screenInstruction.setInstructionOrderNumber(instructionList.size());
                    screenInstruction.setOptional(false);
                    screenInstruction.setActions(ABRConstants.SCREEN);
                    screenInstruction.setOnHoldSeconds(0);
                    screenInstruction.setBlock(block);
                    screenInstruction.setExportToABR(false);
                    ABRSharedResources.getInstance()
                            .addEntity(
                                    screenInstruction,
                                    BlockLoopInstructionDTO.class,
                                    () -> new ABRAlertScene(
                                            Alert.AlertType.INFORMATION,
                                            "Instruction Added",
                                            "Instruction Screenshot Browser has been added successfully",
                                            ButtonType.OK));
                    return null;
                }
            };
            new Thread(addCloseTask).start();
        }
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
                            abrWebDriver.getDriver().findElements(By.xpath(abrWebElement.getXPath()));
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
                            abrWebDriver.getDriver().findElements((By.xpath(abrWebElement.getXPath())));
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
                if (checkBoxAction.isSelected()) {
                    try {
                        abrWebElement.setxPath(getXPath(abrWebDriver.getDriver(), abrWebElement.getElement()));
                        abrWebDriver.dehighlightElement(abrWebElement.getElement());

                        WebElement elementXPath =
                                abrWebDriver.getDriver().findElement(By.xpath(abrWebElement.getXPath()));
                        if (elementXPath != null) {
                            elementXPath.click();
                        }
                        //                                abrWebElement.getElement().click();
                    } catch (Exception e) {
                        System.out.println("Cannot find the XPath for this Element");
                    }
                } else {
                    ABRLogger.getInstance(ABRScannedElementPane.class)
                            .info("Double clicked the element: " + abrWebElement.getXPath());
                    ABRLogger.getInstance(ABRScannedElementPane.class).fine("Going to show the confirmation Alert");
                    Alert alert = new Alert(
                            Alert.AlertType.CONFIRMATION,
                            "Are you sure you want to Add the Instruction Selected to the Bot-Job?",
                            ButtonType.YES,
                            ButtonType.NO);
                    ABRLogger.getInstance(ABRScannedElementPane.class)
                            .fine("Confirmation Alert shown. Waiting for result");
                    Optional<ButtonType> result = alert.showAndWait();
                    ABRLogger.getInstance(ABRScannedElementPane.class).finer("result got: " + result.get());
                    if (result.isPresent() && result.get() == ButtonType.YES) {
                        ABRLogger.getInstance(ABRScannedElementPane.class).info("Clicked on YES");
                        ABRLogger.getInstance(ABRScannedElementPane.class).fine("Creating Thread");
                        Task<Void> handleEvent = new Task<>() {
                            @Override
                            protected Void call() throws Exception {
                                ABRLogger.getInstance(Task.class).info("THREAD: Started");

                                if (Strings.isNullOrEmpty(abrWebElement.getXPath())) {
                                    try {
                                        abrWebElement.setxPath(
                                                getXPath(abrWebDriver.getDriver(), abrWebElement.getElement()));
                                        abrWebDriver.dehighlightElement(abrWebElement.getElement());
                                    } catch (Exception e) {
                                        ABRLogger.getInstance(Task.class)
                                                .severe("Cannot find the XPath for this Element ");
                                    }
                                }

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
                                                (instr) -> instr.getBlock().getId() == block.getId());
                                ABRLogger.getInstance(Task.class).finer("THREAD: instruction list size " + list.size());
                                BlockLoopInstructionDTO instruction =
                                        abrWebElement.buildBlockLoopInstruction(list.size());
                                instruction.setBlock(block);
                                instruction.setInstructionOrderNumber(list.size() + 1);
                                ABRLogger.getInstance(Task.class).fine("THREAD: adding instruction to database");
                                ABRSharedResources.getInstance()
                                        .addEntity(instruction, BlockLoopInstructionDTO.class, () -> {
                                            abrWebElement.setInstructionId(instruction.getId());
                                            LinkedBlockingQueue<InstructionReferenceDTO> queue =
                                                    new LinkedBlockingQueue<>();
                                            for (String key : abrWebElement
                                                    .getSavedReferences()
                                                    .keySet()) {
                                                InstructionReferenceDTO reference = new InstructionReferenceDTO();
                                                reference.setReferenceType(key);
                                                reference.setValue(abrWebElement
                                                        .getSavedReferences()
                                                        .get(key));
                                                reference.setBlockLoopInstructionDTO(instruction);
                                                queue.add(reference);
                                                ABRLogger.getInstance(Task.class)
                                                        .fine("THREAD: reference added to queue");
                                            }
                                            ABRLogger.getInstance(Task.class)
                                                    .fine("THREAD: adding " + queue.size() + "Instruction elements");
                                            try {
                                                ABRSharedResources.getInstance()
                                                        .addAllEntity(queue, InstructionReferenceDTO.class, () -> {
                                                            Platform.runLater(() -> {
                                                                new ABRAlertScene(
                                                                        Alert.AlertType.INFORMATION,
                                                                        "Instruction Added",
                                                                        "Instruction " + instruction.getName()
                                                                                + " has been added successfully",
                                                                        ButtonType.OK);
                                                            });
                                                        });

                                            } catch (Exception ex) {
                                                ABRLogger.getInstance(Task.class)
                                                        .severe("Error Adding Instruction elements");
                                            }
                                        });
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

    private List<WebElement> managePrioritiesCriteria() {
        // Gets Alwasy the Latest info form DB
        loadUserData(botJob.getHomeBanking().getId());
        abrPriorities.loadSearchElementsConfig(databaseUserDto.getSearchConfig());

        List<WebElement> elementsResponse = new ArrayList<>();
        if (abrPriorities.getSearchConfigList() == null) {
            System.out.println("Is going to Search using \"searchConfigTemplate\"!  Please Add to the DB");
            return null;
        }
        if (abrPriorities.getSearchConfigList().size() > 0) {

            // Fetch the HTML content of the page
            Document docJSoup = null;
            docJSoup = JsoupConnect(botJob.getHomeBanking().getUrl());
            Set<WebElementWrapper> uniqueElements = new HashSet<>();
            List<WebElement> finalList = new ArrayList<>();
            List<WebElement> searchingElems = new ArrayList<>();
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
                                searchingElems = abrWebDriver.getDriver().findElements((By.xpath(name)));
                                // Add elements from the first list to the set
                                for (WebElement element : searchingElems) {
                                    String href = element.getAttribute("href");
                                    String text = element.getText();
                                    String uniqueKey = href + text;
                                    WebElementWrapper wrapper = new WebElementWrapper(name, text, href, element);
                                    if (uniqueElements.add(wrapper)) {
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

                                    for (WebElementWrapper wrapper : uniqueElements) {
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
                                        if (uniqueElements.add(wrapper)) {
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
                            //                                for (WebElementWrapper wrapper : uniqueElements) {
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
                                    searchingElems = abrWebDriver.getDriver().findElements((By.tagName(name)));
                                    // Add elements from the first list to the set
                                    for (WebElement element : searchingElems) {
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
                                    searchingElems = abrWebDriver.getDriver().findElements((By.tagName(name)));
                                    // Add elements from the first list to the set
                                    for (WebElement element : searchingElems) {
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
                                searchingElems = searchAllInputs(abrWebDriver.getDriver());
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
                                searchingElems =
                                        abrWebDriver.getDriver().findElements((By.cssSelector("button[" + name + "]")));
                                //                                List<WebElement> elements2 = webElements =
                                // abrWebDriver
                                //                                        .getDriver()
                                //                                        .findElements(By.xpath("//*[@" +
                                // searchConfig.getName() + "]"));

                                // Add elements from the first list to the set
                                for (WebElement element : searchingElems) {
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

                            try {
                                searchingElems =
                                        abrWebDriver.getDriver().findElements((By.cssSelector("input[" + name + "]")));
                                //                                List<WebElement> elements2 = webElements =
                                // abrWebDriver
                                //                                        .getDriver()
                                //                                        .findElements(By.xpath("//*[@" +
                                // searchConfig.getName() + "]"));

                                // Add elements from the first list to the set
                                for (WebElement element : searchingElems) {
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
                            searchingElems = abrWebDriver.getDriver().findElements(new ByChained(locators));
                            for (WebElement element : searchingElems) {
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
            e.printStackTrace();
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

    public void saveReferencesToFile(String filePath, List<ABRWebElement> elements) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            for (ABRWebElement element : elements) {
                Map<String, String> savedReferences = element.getSavedReferences();

                for (Map.Entry<String, String> entry : savedReferences.entrySet()) {
                    writer.write(entry.getKey() + "=" + entry.getValue());
                    writer.newLine();
                }
            }
            System.out.println("References saved to " + filePath);
        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
        }
    }

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
            e.printStackTrace();
        } finally {
            // Close the browser
            driver.quit();
        }
    }

    private void periodicThread(WebDriver driver) {
        // JavaScript code to inject
        String jsCode = "(function() {"
                + "    var tooltip = document.createElement('div');"
                + "    tooltip.id = 'Martini-Is-Awesome';"
                + "    tooltip.style.position = 'absolute';"
                + "    tooltip.style.backgroundColor = 'rgba(255, 165, 0, 0.5)';" // Slightly opaque light orange
                + "    tooltip.style.border = '1px solid #ccc';"
                + "    tooltip.style.padding = '10px';"
                + "    tooltip.style.borderRadius = '5px';"
                + "    tooltip.style.boxShadow = '0 2px 4px rgba(0, 0, 0, 0.2)';"
                + "    tooltip.style.fontFamily = 'Arial, sans-serif';"
                + "    tooltip.style.fontSize = '14px';"
                + "    tooltip.style.color = '#333';"
                + "    tooltip.style.zIndex = '10000';" // Higher z-index
                + "    tooltip.style.display = 'none';"
                + "    document.body.appendChild(tooltip);"
                + "    function getMartiniAbsoluteXPath(element) {"
                + "        if (element === document.body) {"
                + "            return '/html/' + element.tagName.toLowerCase();"
                + "        }"
                + "        var ix = 0;"
                + "        var siblings = element.parentNode.childNodes;"
                + "        for (var i = 0; i < siblings.length; i++) {"
                + "            var sibling = siblings[i];"
                + "            if (sibling === element) {"
                + "                return getMartiniAbsoluteXPath(element.parentNode) + '/' + element.tagName.toLowerCase() + '[' + (ix + 1) + ']';"
                + "            }"
                + "            if (sibling.nodeType === 1 && sibling.tagName === element.tagName) {"
                + "                ix++;"
                + "            }"
                + "        }"
                + "        return '';"
                + "    }"
                + "    function getMartiniXPath(element) {"
                + "        if (element === document.body) {"
                + "            return '/html/body';"
                + "        }"
                + "        var ix = 0;"
                + "        var siblings = element.parentNode ? element.parentNode.childNodes : [];"
                + "        for (var i = 0; i < siblings.length; i++) {"
                + "            var sibling = siblings[i];"
                + "            if (sibling.nodeType === 1 && sibling.tagName === element.tagName) {"
                + "                if (sibling === element) {"
                + "                    return getMartiniXPath(element.parentNode) + '/' + element.tagName.toLowerCase() + '[' + (ix + 1) + ']';"
                + "                }"
                + "                ix++;"
                + "            }"
                + "        }"
                + "        return '';"
                + "    }"
                + "    function getMartiniCustomXPath(element) {"
                + "        if (element === document.body) {"
                + "            return '/html/' + element.tagName.toLowerCase();"
                + "        }"
                + "        var className = element.className.split(' ').filter(function(cls) { return !/\\d/.test(cls); }).join('.');"
                + "        var tagName = element.tagName.toLowerCase();"
                + "        var ix = 0;"
                + "        var siblings = element.parentNode.childNodes;"
                + "        for (var i = 0; i < siblings.length; i++) {"
                + "            var sibling = siblings[i];"
                + "            if (sibling === element) {"
                + "                var path = getMartiniCustomXPath(element.parentNode) + '/' + tagName;"
                + "                if (className) {"
                + "                    path += '[contains(@class, \"' + className + '\")]';"
                + "                } else {"
                + "                    path += '[' + (ix + 1) + ']';"
                + "                }"
                + "                return path;"
                + "            }"
                + "            if (sibling.nodeType === 1 && sibling.tagName === element.tagName) {"
                + "                ix++;"
                + "            }"
                + "        }"
                + "        return '';"
                + "    }"
                + "    function showMartiniTooltip(event) {"
                + "        var elementBelowTooltip = document.elementFromPoint(event.clientX, event.clientY);"
                + "        window.tagNameTemp = elementBelowTooltip.tagName.toLowerCase();"
                + "        window.coordsTemp = elementBelowTooltip.getBoundingClientRect();"
                + "        window.coordsTemp = window.coordsTemp.left + ',' + window.coordsTemp.top;"
                + "        tooltip.textContent = window.tagNameTemp + '-Coordinates:(' + window.coordsTemp + ')';"
                + "        var tooltipWidth = tooltip.offsetWidth;"
                + "        var tooltipHeight = tooltip.offsetHeight;"
                + "        var left = event.pageX - tooltipWidth / 2;"
                + "        var top = event.pageY - tooltipHeight / 2;"
                + "        "
                + "        tooltip.style.left = left + 'px';"
                + "        tooltip.style.top = top + 'px';"
                + "        tooltip.style.display = 'block';"
                + "    }"
                + "    function hideMartiniTooltip() {"
                + "        tooltip.style.display = 'none';"
                + "    }"
                + "    function handleMartiniClick(event) {"
                + "          event.preventDefault(); "
                + "          event.stopPropagation(); "
                + "          tooltip.style.display = 'none';"
                + "          var elementBelowTooltip = document.elementFromPoint(event.clientX, event.clientY);"
                + "          tooltip.style.display = 'block';"
                + "          console.log(elementBelowTooltip);"
                + "          var xpath = getMartiniXPath(elementBelowTooltip);"
                + "          var absoluteXPath = getMartiniAbsoluteXPath(elementBelowTooltip);"
                + "          var customXPath = getMartiniCustomXPath(elementBelowTooltip);"
                + "          window.currentXPath = xpath;"
                + "          window.currentAbsoluteXPath = absoluteXPath;"
                + "          window.customXPath = customXPath;"
                + "          window.attribId = elementBelowTooltip.id || '';"
                + "          window.attribName = elementBelowTooltip.name || '';"
                + "          window.tagName = elementBelowTooltip.tagName.toLowerCase();"
                + "          window.coords = elementBelowTooltip.getBoundingClientRect();"
                + "          window.coords = window.coords.left + ',' + window.coords.top;"
                + "    }"
                + "    window.currentXPath = '';"
                + "    window.currentAbsoluteXPath = '';"
                + "    window.customXPath = '';"
                + "    window.attribId = '';"
                + "    window.attribName = '';"
                + "    window.tagName = '';"
                + "    window.coords = '';"
                + "    window.tagNameTemp = '';"
                + "    window.coordsTemp = '';"
                + "    document.addEventListener('mouseover', showMartiniTooltip);"
                //                + "    document.addEventListener('mouseout', hideMartiniTooltip);"
                + "    document.addEventListener('click', handleMartiniClick);"
                + "    window.removeClickListener = function() {"
                + "        document.removeEventListener('mouseover', showMartiniTooltip);"
                //                + "        document.removeEventListener('mouseout', hideMartiniTooltip);"
                + "        document.removeEventListener('click', handleMartiniClick);"
                + "    };"
                + "})();";

        // Inject the JavaScript into the webpage
        jsExecutor = (JavascriptExecutor) driver;
        jsExecutor.executeScript(jsCode);

        // Start a thread to periodically check the XPath value and update the TextField
        new Thread(() -> {
                    while (periodicActivated) {
                        //                        String currentXPath = (String) jsExecutor.executeScript("return
                        // window.currentXPath;");

                        // Execute JavaScript to construct and return a custom object
                        LinkedHashMap<String, Object> linkedHashMap = (LinkedHashMap<String, Object>)
                                jsExecutor.executeScript(
                                        "var obj = { attribId: window.attribId, attribName: window.attribName, customXPath: window.customXPath, currentXPath: window.currentXPath, currentAbsoluteXPath: window.currentAbsoluteXPath, tagName: window.tagName, coords: window.coords }; return obj;");

                        // Convert the LinkedHashMap to a Java Map (if necessary)
                        Map<String, Object> resultMap = new LinkedHashMap<>(linkedHashMap);

                        if (linkedHashMap != null) {
                            Platform.runLater(() -> {
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

    private void loadUserData(int bankId) {
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
            e.printStackTrace();
        }
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
                            for (BlockLoopInstructionDTO blockInstruction : block.getBlockLoopInstructions()) {
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
            e.printStackTrace();
        }
        //        jobUserList.clear();
        //        loadBotJobData();
    }

    private void loadBlockAll(int botJobId) {
        String query = "SELECT bj.id AS bot_job_id, bj.name AS bot_job_name, "
                + " b.id AS block_id, b.block_order_number, b.name AS block_name, "
                + " b.description AS block_description, b.type_id, "
                + " bli.id AS block_loop_instruction_id, bli.instruction_order_number, "
                + " bli.actions, bli.name AS instruction_name, bli.path, bli.description AS instruction_description, "
                + " bli.optional, bli.block_marked, bli.default_val, bli.action_custom_max_wait_sec, "
                + " bli.on_hold_seconds, bli.encrypted, bli.export_to_abr, "
                + " irl.reference_type, irl.value, "
                + "  bli.operation, bli.parent_id "
                + " FROM bot_job bj "
                + " LEFT JOIN block b ON b.bot_job_id = bj.id "
                + " JOIN block_loop_instruction bli ON bli.block_id = b.id "
                + " LEFT JOIN instruction_reference irl ON irl.block_loop_instruction_id = bli.id "
                + " where bot_job_id = " + botJobId
                + " ORDER BY bj.id, b.block_order_number, bli.instruction_order_number, irl.id ASC";

        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(query)) {

            botLoadJobs.clear();

            int previousBotJobId = -1;
            int previousBlockId = -1;
            int previousInstructionId = -1;

            BotJobLoadDTO previousBotJob = null;
            BlockLoadDTO previousBlock = null;
            BlockLoopInstructionLoadDTO previousInstruction = null;

            while (rs.next()) {
                int currentBotJobId = rs.getInt("bot_job_id");
                if (previousBotJobId < 0 || previousBotJobId != currentBotJobId) {
                    previousBotJob = new BotJobLoadDTO();
                    previousBotJob.setId(currentBotJobId);
                    previousBotJob.setName(rs.getString("bot_job_name"));
                    previousBotJob.setBlockLoadDTOList(new ArrayList<>());
                    botLoadJobs.add(previousBotJob);
                    previousBotJobId = currentBotJobId;
                }

                int currentBlockId = rs.getInt("block_id");
                if (previousBlockId < 0 || previousBlockId != currentBlockId) {
                    previousBlock = new BlockLoadDTO();
                    previousBlock.setId(currentBlockId);
                    previousBlock.setBlockOrderNumber(rs.getInt("block_order_number"));
                    previousBlock.setName(rs.getString("block_name"));
                    previousBlock.setDescription(rs.getString("block_description"));
                    previousBlock.setTypeId(rs.getInt("type_id"));
                    previousBlock.setBotJobLoadDTO(previousBotJob);

                    previousBlockId = currentBlockId;

                    previousBlock.setBlockLoopInstructionLoadDTOS(new ArrayList<>());
                    previousBotJob.getBlockLoadDTOList().add(previousBlock);
                }

                int currentInstructionId = rs.getInt("block_loop_instruction_id");
                if (previousInstructionId < 0 || previousInstructionId != currentInstructionId) {
                    previousInstruction = new BlockLoopInstructionLoadDTO();
                    previousInstruction.setId(currentInstructionId);
                    previousInstruction.setInstructionOrderNumber(rs.getInt("instruction_order_number"));
                    previousInstruction.setActions(rs.getString("actions"));
                    previousInstruction.setName(rs.getString("instruction_name"));
                    previousInstruction.setPath(rs.getString("path"));
                    previousInstruction.setDescription(rs.getString("instruction_description"));
                    previousInstruction.setOptional(rs.getInt("optional"));
                    previousInstruction.setBlockMarked(rs.getBoolean("block_marked"));
                    previousInstruction.setDefault_val(rs.getString("default_val"));
                    previousInstruction.setActionCustomMaxWaitSec(rs.getInt("action_custom_max_wait_sec"));
                    previousInstruction.setOnHoldSeconds(rs.getInt("on_hold_seconds"));
                    previousInstruction.setEncrypted(rs.getInt("encrypted"));
                    previousInstruction.setExportToABR(rs.getInt("export_to_abr"));
                    previousInstruction.setOperation(rs.getString("operation"));
                    previousInstruction.setParentId(rs.getInt("parent_id"));

                    previousInstructionId = currentInstructionId;

                    previousInstruction.setInstructionReferenceLoadDTOList(new ArrayList<>());
                    previousBlock.getBlockLoopInstructionLoadDTOS().add(previousInstruction);
                }

                String referenceType = rs.getString("reference_type");
                if (referenceType != null) {
                    InstructionReferenceLoadDTO reference = new InstructionReferenceLoadDTO();
                    reference.setReferenceType(referenceType);
                    reference.setValue(rs.getString("value"));
                    previousInstruction.getInstructionReferenceLoadDTOList().add(reference);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void recallJob() {
        executeJob();
        // Review if Has Not Executed Instructions
        boolean hasUnexecutedInstructions = botLoadJobs.get(0).getBlockLoadDTOList().stream()
                .flatMap(block -> block.getBlockLoopInstructionLoadDTOS().stream())
                .anyMatch(instruction -> instruction.getExecuted() == null || !instruction.getExecuted());

        if (hasUnexecutedInstructions) {
            Alert alert = new Alert(
                    Alert.AlertType.CONFIRMATION,
                    "Recall the Executions for this page?",
                    ButtonType.YES,
                    ButtonType.NO);
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.YES) {
                recallJob();
            }
        }
    }

    private boolean executeJob() {
        if (waitForPage == null) {
            String updateTimeout =
                    ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.WEBDRIVER_PAGE_UPDATE_TIMEOUT_SEC);
            String interactionTimeout =
                    ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.WEBDRIVER_PAGE_UPDATE_TIMEOUT_SEC);
            waitForPage =
                    new WebDriverWait(abrWebDriver.getDriver(), Duration.ofSeconds(Integer.parseInt(updateTimeout)));
            waitForAction = new WebDriverWait(
                    abrWebDriver.getDriver(), Duration.ofSeconds(Integer.parseInt(interactionTimeout)));
        }

        if (repository == null) {
            repository = new Repository(ABRSharedResources.getInstance().getSession());
        }
        try {
            baseLogFile = new File(ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.FOLDER_PATH_LOG)
                    + ABRConstants.FILE_NAME_SCANNER_BASE_LOG);
        } catch (Exception e) {
            e.printStackTrace();
        }

        List<BlockLoadDTO> blocksLoaded = botLoadJobs.get(0).getBlockLoadDTOList();

        //        ABRPropertyManager managerProps = ABRPropertyManager.getInstance();
        String excelPath = ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.FOLDER_PATH_EXCEL);
        excelPath = excelPath + "\\" + blocksLoaded.get(0).getBotJobLoadDTO().getName() + ".xlsx";
        if (!(new File(excelPath)).exists()) {
            new ABRAlertScene(
                    Alert.AlertType.WARNING,
                    "Missing file excel",
                    "Please generate and compile the data of the file excel first before launching the bot job",
                    new ButtonType[] {ButtonType.OK});
        }

        Labels.initializeLabelsInSpecLang("en");
        Properties labelsValue = Labels.labelsValue;

        ExcelReader excelReader = new ExcelReader();
        ExtractedData extractedData = null;
        try {
            extractedData = excelReader.extractData(excelPath, blocksLoaded);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (extractedData.getErrorMessage() != null) {
            //				showAlert("Excel Data File", "Warning: Excel File exist" , "Fields in the excel not matching the
            // botjob requirements");
            System.out.println("Fields in the excel not matching the botjob requirements");
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
        //                .filter(action -> action.contains(Constants.CLICK))
        //                .collect(Collectors.toSet());

        //        String browser = ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.BROWSER);
        //            WebPage webPage = new WebPage(browser, homeBankingDTO.getUrl());

        int botJobId = blocksLoaded.get(0).getBotJobLoadDTO().getId();

        // Original BotJobDTO
        BotJobDTO selectedJob = ABRSharedResources.getInstance().getEntityById(BotJobDTO.class, botJobId);

        String baseLogString = blocksLoaded.get(0).getBotJobLoadDTO().getName()
                + Constants.FIELDS_SEPARATOR
                + labelsValue.getProperty(Labels.START);
        printBaseLog(baseLogFile, generateTimestamp(), baseLogString);
        ExcelWriter.ExcelChain writer =
                new ExcelWriter(selectedJob.getName(), abrWebDriver.getDriver()).withPurpose("report");
        writer.insertReportHead();
        boolean success = true;
        boolean stopAll = false;
        long botJobStartTime = System.nanoTime();
        long totalExecutionTime = 0;
        String lastInstructionExecuted = "No instruction executed yet";
        String resultAcions = "";
        short status = (short) ExcelReportStatusEnum.ERROR.ordinal();
        Map<String, String> dataExcel = null;

        clearFields();

        ExcelReportDTO report = new ExcelReportDTO();
        report.setOrder((short) blocksLoaded.get(0).getId());
        report.setStartDate(LocalDateTime.now());
        report.setBatchJobId(selectedJob.getId());
        report.setBotJobDTO(selectedJob);
        report.setStatus((short) ExcelReportStatusEnum.NOT_RUN.ordinal());

        mapOperators = new HashMap<>();

        if (extractedData.getNumberOfDataRows() > 0) {
            for (BlockLoadDTO instructionsLoad : blocksLoaded.stream().collect(Collectors.toList())) {
                instructionsExecuted.clear();
                if (stopAll) {
                    break;
                }
                for (int i = 0; success && i < extractedData.getNumberOfDataRows(); i++) {
                    if (stopAll) {
                        break;
                    }

                    writer.insertBlockSeparation(instructionsLoad.getName());

                    // Call the method to get the filtered list
                    List<BlockLoopInstructionLoadDTO> unexecutedInstructions = getUnexecutedInstructions(
                            instructionsExecuted, instructionsLoad.getBlockLoopInstructionLoadDTOS());

                    for (BlockLoopInstructionLoadDTO currentInstruction : unexecutedInstructions) {
                        if (stopAll) {
                            break;
                        }
                        if (currentInstruction.getExecuted() == null || !currentInstruction.getExecuted()) {
                            boolean execOperation = false;
                            boolean checkOperation = false;
                            String xPathOperation = null;
                            String parentField = null;

                            String[] actions =
                                    currentInstruction.getActions().split(Constants.ACTIONS_AND_PATHS_SPLITTER);
                            String[] operations = currentInstruction.getOperation() != null
                                    ? currentInstruction.getOperation().split(Constants.ACTION_SPECIFICATIONS_SPLITTER)
                                    : null;

                            if (actions[0].equalsIgnoreCase(WebElementTagNameEnum.GET.getValue())
                                    || actions[0].equalsIgnoreCase(WebElementTagNameEnum.SET.getValue())) {

                                execOperation = true;
                                try {
                                    xPathOperation = instructionsLoad.getBlockLoopInstructionLoadDTOS().stream()
                                            .filter(f -> f.getId() == currentInstruction.getParentId())
                                            .findFirst()
                                            .get()
                                            .getPath();
                                } catch (Exception ex) {
                                    Alert alert = new Alert(Alert.AlertType.ERROR);
                                    alert.setTitle("Parent Id Error");
                                    alert.setHeaderText("Check Parent Id");
                                    alert.setContentText("The Parent Id: " + currentInstruction.getParentId()
                                            + "\nFor the : "
                                            + currentInstruction.getOperation() + "\nDoes not belong to this block");
                                    alert.showAndWait();

                                    stopAll = true;

                                    resultAcions = String.format(
                                            "This ParentId: %d does not belong to this block: %d",
                                            currentInstruction.getParentId(), instructionsLoad.getId());
                                    success = false;

                                    lastInstructionExecuted = "";

                                    ABRLogger.getInstance(ABRScannedElementPane.class)
                                            .severe(String.format(
                                                    "Parent Id Error\nCheck Parent Id: %d"
                                                            + "\nFor the %s \nDoes not belong to this block",
                                                    currentInstruction.getParentId(),
                                                    currentInstruction.getOperation()));

                                    break;
                                }

                                parentField = instructionsLoad.getBlockLoopInstructionLoadDTOS().stream()
                                        .filter(f -> f.getId() == currentInstruction.getParentId())
                                        .findFirst()
                                        .get()
                                        .getName();

                            } else if (actions[0].equalsIgnoreCase(WebElementTagNameEnum.CK.getValue())) {
                                parentField = instructionsLoad.getBlockLoopInstructionLoadDTOS().stream()
                                        .filter(f -> f.getId() == currentInstruction.getParentId())
                                        .findFirst()
                                        .get()
                                        .getName();

                                checkOperation = true;
                            }

                            long currentInstructionStartTime = System.nanoTime();
                            File logFileForSingleExcel = excelReader.createLogFile(excelPath);

                            fillUpCurretLocators(currentInstruction);

                            try {
                                if (!execOperation && !checkOperation) {
                                    dataExcel = extractedData.getRowFieldValues(i);

                                    lastInstructionExecuted = currentInstruction.getName()
                                            + Constants.BLANK_STRING
                                            + currentInstruction.getPath();
                                    resultAcions = performActions(
                                            dataExcel, currentInstruction, botJobId, instructionsLoad.getName());
                                    long currentInstructionEndTime = System.nanoTime();
                                    totalExecutionTime += currentInstructionEndTime - currentInstructionStartTime;

                                    if (resultAcions != null) {

                                        ABRLogger.getInstance(ABRScannedElementPane.class)
                                                .fine("SUCCESSFUL INSTRUCTION on element: " + resultAcions + " Cmd: "
                                                        + lastInstructionExecuted);

                                        currentInstruction.setExecuted(true);

                                        // Assuming currentInstruction and instructionsExecuted are already defined
                                        if (currentInstruction != null
                                                && instructionsExecuted.stream()
                                                        .noneMatch(
                                                                instruction -> instruction.getInstructionOrderNumber()
                                                                        == currentInstruction
                                                                                .getInstructionOrderNumber())) {
                                            instructionsExecuted.add(currentInstruction);
                                        }
                                        success = true;
                                    } else {
                                        resultAcions = "Failed to Execute -> " + currentInstruction.getName();
                                        success = false;
                                    }

                                    writer.insertInstructionResult(
                                            currentInstruction,
                                            dataExcel,
                                            LocalTime.ofNanoOfDay(
                                                    currentInstructionEndTime - currentInstructionStartTime),
                                            success ? "success" : "failed");

                                } else if (execOperation) {
                                    // Special Operators
                                    lastInstructionExecuted = currentInstruction.getName()
                                            + Constants.BLANK_STRING
                                            + currentInstruction.getActions()
                                            + Constants.BLANK_STRING
                                            + currentInstruction.getOperation();

                                    if (operations.length == 2) {
                                        resultAcions = performActionOperator(
                                                currentInstruction,
                                                xPathOperation,
                                                actions[0],
                                                operations,
                                                parentField);

                                        long currentInstructionEndTime = System.nanoTime();
                                        totalExecutionTime += currentInstructionEndTime - currentInstructionStartTime;

                                        if (resultAcions != null) {

                                            ABRLogger.getInstance(ABRScannedElementPane.class)
                                                    .fine("SUCCESSFUL INSTRUCTION on element: " + resultAcions
                                                            + " Cmd: " + lastInstructionExecuted);

                                            currentInstruction.setExecuted(true);

                                            // Assuming currentInstruction and instructionsExecuted are already
                                            // defined
                                            if (currentInstruction != null
                                                    && instructionsExecuted.stream()
                                                            .noneMatch(instruction ->
                                                                    instruction.getInstructionOrderNumber()
                                                                            == currentInstruction
                                                                                    .getInstructionOrderNumber())) {
                                                instructionsExecuted.add(currentInstruction);
                                            }
                                            success = true;
                                        } else {
                                            resultAcions = "Failed to Execute Cmd: " + lastInstructionExecuted;
                                            success = false;
                                        }
                                    } else {
                                        resultAcions = "Failed to Execute Cmd: " + lastInstructionExecuted;
                                        success = false;
                                    }
                                } else if (checkOperation) {
                                    // Special Operators
                                    lastInstructionExecuted = currentInstruction.getName()
                                            + Constants.BLANK_STRING
                                            + currentInstruction.getActions()
                                            + Constants.BLANK_STRING
                                            + currentInstruction.getOperation();

                                    if (operations.length == 3) {
                                        if (mapOperators.containsKey(parentField)) {

                                            //                                        mapOperators =
                                            // performActionOperator(currentInstruction, xPathOperation,
                                            // mapOperators,
                                            // actions[0],operations[1]);
                                            resultAcions = "(" + parentField + ")" + String.join(":", operations);
                                            boolean isOperationValid = false;
                                            if (operations[1].equalsIgnoreCase("=")) {
                                                isOperationValid = mapOperators
                                                        .get(parentField)
                                                        .equalsIgnoreCase(operations[2]);

                                            } else if (operations[1].equalsIgnoreCase(">")) {
                                                isOperationValid = mapOperators
                                                        .get(parentField)
                                                        .equalsIgnoreCase(operations[2]);
                                            }

                                            long currentInstructionEndTime = System.nanoTime();
                                            totalExecutionTime +=
                                                    currentInstructionEndTime - currentInstructionStartTime;

                                            if (isOperationValid) {

                                                ABRLogger.getInstance(ABRScannedElementPane.class)
                                                        .fine("SUCCESSFUL INSTRUCTION on element: " + resultAcions
                                                                + " Cmd: " + lastInstructionExecuted);

                                                currentInstruction.setExecuted(true);

                                                // Assuming currentInstruction and instructionsExecuted are already
                                                // defined
                                                if (currentInstruction != null
                                                        && instructionsExecuted.stream()
                                                                .noneMatch(instruction ->
                                                                        instruction.getInstructionOrderNumber()
                                                                                == currentInstruction
                                                                                        .getInstructionOrderNumber())) {
                                                    instructionsExecuted.add(currentInstruction);
                                                }
                                                success = true;

                                            } else {
                                                Alert alert = new Alert(Alert.AlertType.ERROR);
                                                alert.setTitle("Validation Error");
                                                alert.setHeaderText("Check Validation Error");
                                                alert.setContentText("The Value: " + operations[2]
                                                        + "\nis not " + operations[1] + " "
                                                        + mapOperators.get(parentField)
                                                        + " Length: ("
                                                        + mapOperators
                                                                .get(parentField)
                                                                .length()
                                                        + ")" + "\n --------------------- "
                                                        + "\nCheck the SET/GET of "
                                                        + operations[0] + " for " + parentField
                                                        + "\nCurrent value: "
                                                        + operations[2] + " Length: (" + operations[2].length()
                                                        + ")" + "\nExpected value: "
                                                        + mapOperators.get(parentField)
                                                        + " Length: ("
                                                        + mapOperators
                                                                .get(parentField)
                                                                .length()
                                                        + ")");
                                                alert.showAndWait();

                                                stopAll = true;

                                                resultAcions = "Failed to Execute Cmd: " + lastInstructionExecuted;
                                                success = false;
                                            }
                                        } else {
                                            Alert alert = new Alert(Alert.AlertType.ERROR);
                                            alert.setTitle("Validation Error");
                                            alert.setHeaderText("GET/SET is Not Defined");
                                            alert.setContentText("There is NOT GET VALUE defined for: " + parentField
                                                    + "\n --------------------- "
                                                    + "\nCheck the SET/GET for "
                                                    + parentField);
                                            alert.showAndWait();

                                            stopAll = true;

                                            resultAcions = "Failed to Execute Cmd: " + lastInstructionExecuted;
                                            success = false;
                                        }

                                    } else {
                                        resultAcions = "Failed to Execute Cmd: " + lastInstructionExecuted;
                                        success = false;
                                    }
                                }

                            } catch (Throwable t) {
                                success = false;
                                currentInstruction.setExecuted(false);
                                if (currentInstruction.isOptional()) {
                                    long currentInstructionEndTime = System.nanoTime();
                                    long duration = currentInstructionEndTime - botJobStartTime;
                                    ABRLogger.getInstance(ABRScannedElementPane.class)
                                            .fine("FAILED OPTIONAL INSTRUCTION on element: " + resultAcions + " Cmd: "
                                                    + lastInstructionExecuted
                                                    + "- Duration: "
                                                    + LocalTime.ofNanoOfDay(duration)
                                                            .format(FORMAT_TIME));
                                    writer.insertInstructionResult(
                                            currentInstruction,
                                            dataExcel,
                                            LocalTime.ofNanoOfDay(
                                                    currentInstructionEndTime - currentInstructionStartTime),
                                            "optional skipped");
                                    status = (short) ExcelReportStatusEnum.WARNING.ordinal();

                                } else {
                                    long currentInstructionEndTime = System.nanoTime();
                                    long duration = currentInstructionEndTime - botJobStartTime;
                                    ABRLogger.getInstance(ABRScannedElementPane.class)
                                            .fine("FAILED MANDATORY INSTRUCTION on element: " + resultAcions + " Cmd: "
                                                    + lastInstructionExecuted
                                                    + "- Duration: "
                                                    + LocalTime.ofNanoOfDay(duration)
                                                            .format(FORMAT_TIME));
                                    writer.insertInstructionResult(
                                            currentInstruction,
                                            null,
                                            LocalTime.ofNanoOfDay(
                                                    currentInstructionEndTime - currentInstructionStartTime),
                                            "failed");
                                    status = (short) ExcelReportStatusEnum.ERROR.ordinal();
                                }
                                //                            throw new RuntimeException(t);
                            }
                            printLog(generateTimestamp(), logFileForSingleExcel, resultAcions, success);
                            if (!success) {
                                countdownTextField.setStyle("-fx-font-size: 16px; -fx-text-fill: red;");
                                countdownTextField.setText(resultAcions);
                                return false;
                            }
                        }
                    }
                }
            }
        } else { //  if dataExel is NULL
            // Creating Dynamic Data if Default is Null
            Map<String, String> dataDynamic = new HashMap<>();
            for (int j = 0; success && j < blocksLoaded.size(); j++) {

                // Call the method to get the filtered list
                List<BlockLoopInstructionLoadDTO> unexecutedInstructions = getUnexecutedInstructions(
                        instructionsExecuted, blocksLoaded.get(j).getBlockLoopInstructionLoadDTOS());

                for (BlockLoopInstructionLoadDTO currentInstruction : unexecutedInstructions) {
                    if (currentInstruction.getDefaultValue() == null) {
                        String[] arr = UtilsMethods.splitIfContains(
                                currentInstruction.getActions(), Constants.ACTION_SPECIFICATIONS_SPLITTER);
                        if (arr.length > 1) {
                            String dataFieldName = arr[1].split(Constants.PATH_FIELD_SUBSTITUTION)[0];
                            insertRandomName(dataDynamic, dataFieldName);
                        }
                    }
                }
            }
            for (int j = 0; success && j < blocksLoaded.size(); j++) {

                // Call the method to get the filtered list
                List<BlockLoopInstructionLoadDTO> unexecutedInstructions = getUnexecutedInstructions(
                        instructionsExecuted, blocksLoaded.get(j).getBlockLoopInstructionLoadDTOS());

                for (BlockLoopInstructionLoadDTO currentInstruction : unexecutedInstructions) {
                    long currentInstructionStartTime = System.nanoTime();
                    File logFileForSingleExcel = excelReader.createLogFile(excelPath);
                    try {
                        lastInstructionExecuted =
                                currentInstruction.getName() + Constants.BLANK_STRING + currentInstruction.getPath();
                        resultAcions = performActions(
                                dataDynamic,
                                currentInstruction,
                                botJobId,
                                blocksLoaded.get(j).getName());
                        long currentInstructionEndTime = System.nanoTime();
                        totalExecutionTime += currentInstructionEndTime - currentInstructionStartTime;
                        if (resultAcions != null) {

                            ABRLogger.getInstance(ABRScannedElementPane.class)
                                    .fine("SUCCESSFUL INSTRUCTION on element: " + resultAcions + " Cmd: "
                                            + lastInstructionExecuted);

                            currentInstruction.setExecuted(true);
                            success = true;
                        } else {
                            resultAcions = "Failed to Execute -> " + currentInstruction.getName();
                            success = false;
                        }
                        writer.insertInstructionResult(
                                currentInstruction,
                                dataDynamic,
                                LocalTime.ofNanoOfDay(currentInstructionEndTime - currentInstructionStartTime),
                                success ? "success" : "failed");

                    } catch (Throwable t) {
                        success = false;
                        currentInstruction.setExecuted(false);
                        if (currentInstruction.isOptional()) {
                            long currentInstructionEndTime = System.nanoTime();
                            long duration = currentInstructionEndTime - botJobStartTime;
                            ABRLogger.getInstance(ABRScannedElementPane.class)
                                    .fine("FAILED OPTIONAL INSTRUCTION on element: " + resultAcions
                                            + " Cmd: "
                                            + lastInstructionExecuted + "- Duration: "
                                            + LocalTime.ofNanoOfDay(duration).format(FORMAT_TIME));
                            writer.insertInstructionResult(
                                    currentInstruction,
                                    dataDynamic,
                                    LocalTime.ofNanoOfDay(currentInstructionEndTime - currentInstructionStartTime),
                                    "optional skipped");
                            status = (short) ExcelReportStatusEnum.WARNING.ordinal();
                        } else {
                            long currentInstructionEndTime = System.nanoTime();
                            long duration = currentInstructionEndTime - botJobStartTime;
                            ABRLogger.getInstance(ABRScannedElementPane.class)
                                    .fine("FAILED MANDATORY INSTRUCTION on element: " + resultAcions
                                            + " Cmd: "
                                            + lastInstructionExecuted + "- Duration: "
                                            + LocalTime.ofNanoOfDay(duration).format(FORMAT_TIME));
                            writer.insertInstructionResult(
                                    currentInstruction,
                                    null,
                                    LocalTime.ofNanoOfDay(currentInstructionEndTime - currentInstructionStartTime),
                                    "failed");
                            status = (short) ExcelReportStatusEnum.ERROR.ordinal();
                        }
                        //                        throw new RuntimeException(t);
                    }
                    printLog(generateTimestamp(), logFileForSingleExcel, resultAcions, success);
                }
            }
        }

        if (totalExecutionTime == 0) {
            report.setDuration(0);
            writer.insertTotalExecutionTimes(botJobStartTime, botJobStartTime);
            try {
                ABRSharedResources.getInstance().addEntity(report, ExcelReportDTO.class);
            } catch (Exception ex) {
                ABRLogger.getInstance(ABRScannedElementPane.class)
                        .warning("Repository.write(report) Error:\n" + ex.getMessage());
            }
        }

        // PRINT END BASE LOG//
        if (success) {
            report.setStatus((short) ExcelReportStatusEnum.SUCCESS.ordinal());
            report.setDuration(totalExecutionTime / 100);
            writer.insertTotalExecutionTimes(botJobStartTime, System.nanoTime());
            try {
                ABRSharedResources.getInstance().addEntity(report, ExcelReportDTO.class);
            } catch (Exception ex) {
                ABRLogger.getInstance(ABRScannedElementPane.class)
                        .warning("Repository.write(report) Error:\n" + ex.getMessage());
            }
            baseLogString = selectedJob.getName()
                    + Constants.FIELDS_SEPARATOR
                    + labelsValue.getProperty(Labels.END)
                    + Constants.FIELDS_SEPARATOR
                    + labelsValue.getProperty(Labels.OK);
        } else {
            baseLogString = selectedJob.getName()
                    + Constants.FIELDS_SEPARATOR
                    + labelsValue.getProperty(Labels.END)
                    + Constants.FIELDS_SEPARATOR
                    + labelsValue.getProperty(Labels.KO)
                    + lastInstructionExecuted;
            report.setStatus(status);
            report.setDuration(totalExecutionTime / 100);
            writer.insertTotalExecutionTimes(botJobStartTime, System.nanoTime());
            try {
                ABRSharedResources.getInstance().addEntity(report, ExcelReportDTO.class);
                //                repository.write(report);
            } catch (Exception ex) {
                ABRLogger.getInstance(ABRScannedElementPane.class)
                        .warning("Repository.write(report) Error:\n" + ex.getMessage());
            }
        }
        printBaseLog(baseLogFile, generateTimestamp(), baseLogString);

        //        Platform.runLater(() -> {
        //            try {
        //                repository.closeSession();
        //            } catch (Exception ex) {
        //                ABRLogger.getInstance(ABRScannedElementPane.class)
        //                        .warning("Repository.closeSession Error:\n" + ex.getMessage());
        //            }
        //        });
        return true;
    }

    private void clearFields() {
        absolutXPathTextField.setText("");
        currentXPathTextField.setText("");
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
        String log = String.join(Constants.FIELDS_SEPARATOR, timeStamp, msg);

        try {
            FileWriter fileWriter = new FileWriter(logFile, true);
            fileWriter.write(log + System.lineSeparator());
            fileWriter.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String generateTimestamp() {
        Date date = new Date();
        dateFormatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        return dateFormatter.format(date);
    }

    private static void printLog(String timeStamp, File logFile, String resultAcions, boolean result) {
        String resultMsg = result ? Constants.SUCCESS : Constants.FAIL;
        String log = String.join(Constants.FIELDS_SEPARATOR, timeStamp, resultMsg, resultAcions);

        try {
            FileWriter fileWriter = new FileWriter(logFile, true);
            fileWriter.write(log + System.lineSeparator());
            fileWriter.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void insertRandomName(Map<String, String> map, String key) {
        String randomName = generateRandomName();
        map.put(key, randomName);
    }

    public static String generateRandomName() {
        int length = RANDOM.nextInt(MAX_LENGTH - MIN_LENGTH + 1) + MIN_LENGTH;
        StringBuilder nameBuilder = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            char randomChar = CHARACTERS.charAt(RANDOM.nextInt(CHARACTERS.length()));
            nameBuilder.append(randomChar);
        }

        return nameBuilder.toString();
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
                || actions[0].equals(Constants.SCREEN)
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
                    case Constants.EXTRACT:
                        result = "insertValueFieldNameInExcel-->"
                                + insertValueFieldNameInExcel(instructionElement, instruction, action, blockJobName);
                        break;
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
            String parentField)
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

    private String insertValueFieldNameInExcel(
            WebElement element, BlockLoopInstructionLoadDTO instruction, String action, String botJobName) {
        String innerHTMLValue = element.getAttribute(WebElementAttributeEnum.INNER_HTML.getValue());
        if (innerHTMLValue.contains("<div")) {
            int lastIndexOfDiv = innerHTMLValue.lastIndexOf("<div");
            innerHTMLValue = innerHTMLValue.substring(lastIndexOfDiv + 1);
            int firstIndexOfOpenTag = innerHTMLValue.indexOf("<");
            int firstIndexOfCloseTag = innerHTMLValue.indexOf(">");
            innerHTMLValue = innerHTMLValue.substring(firstIndexOfCloseTag + 1, firstIndexOfOpenTag);
        }
        String fieldName = null;
        String[] arr = UtilsMethods.splitIfContains(action, Constants.ACTION_SPECIFICATIONS_SPLITTER);
        if (arr.length > 1) {
            fieldName = arr[1].split(Constants.PATH_FIELD_SUBSTITUTION)[0];
        }

        new ExcelWriter(botJobName, abrWebDriver.getDriver())
                .withPurpose("excel")
                .insertValueFieldName(fieldName, innerHTMLValue);
        return action + " fieldName " + fieldName;
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

    private void scrollToElement(WebElement element) throws Exception {
        UtilsMethods.exceptionIfNullWebElement(element);
        ((JavascriptExecutor) abrWebDriver.getDriver()).executeScript("arguments[0].scrollIntoView(true);", element);
    }

    private String clickElement(WebElement element) throws Exception {
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

    private synchronized String onHoldForSeconds(BlockLoopInstructionLoadDTO instruction) throws Exception {
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

    private void refreshPage() {
        abrWebDriver.getDriver().navigate().refresh();
        justCalledRefreshPage = true;
    }

    private void waitPage() {
        waitForPage.until(driver -> ((JavascriptExecutor) abrWebDriver.getDriver())
                .executeScript("return document.readyState")
                .equals("complete"));
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

    public void quit(int status) {
        abrWebDriver.getDriver().quit();
        if (status == 0) {
            System.exit(status);
        }
        Close();
    }

    private void executeActionsAtInstructionCoordinates(
            BlockLoopInstructionLoadDTO instruction, Map<String, String> data) throws Exception {

        List<com.allinweb.ch.util.Priority> priorityList = ABRPriorities.getAllPriorityList();
        Optional<com.allinweb.ch.util.Priority> priority = priorityList.stream()
                .filter(p -> p.getPriorityType() == PriorityTypeEnum.coordinates)
                .findFirst();
        if (priority.isPresent()) {
            List<InstructionReferenceLoadDTO> instructionReferenceList =
                    instruction.getInstructionReferenceLoadDTOList();
            Optional<InstructionReferenceLoadDTO> reference = instructionReferenceList.stream()
                    .filter(ref -> ref.getReferenceType().equals(priority.get().getName()))
                    .findFirst();
            int x = 0;
            int y = 0;
            int xCoord = 0;
            int yCoord = 0;
            if (reference.isPresent()) {
                String[] coordinates = reference.get().getValue().split(ABRConstants.FIELDS_SEPARATOR);
                x = Integer.parseInt(coordinates[0]);
                y = Integer.parseInt(coordinates[1]);
                int maxHeight =
                        abrWebDriver.getDriver().manage().window().getSize().getHeight();
                int maxWidth =
                        abrWebDriver.getDriver().manage().window().getSize().getWidth();
                int offsetY = y - maxHeight;
                int offsetX = x - maxWidth;
                xCoord = x > maxWidth ? x - offsetX : x;
                yCoord = y > maxHeight ? y - offsetY : y;
            }
            String[] actions = instruction.getActions().split(ABRConstants.ACTIONS_AND_PATHS_SPLITTER);
            for (String action : actions) {
                switch (String.valueOf(action.charAt(0))) {
                    case Constants.VISUALIZE:
                        scrollToCoordinates(x, y);
                        break;
                    case Constants.CLICK:
                        scrollToCoordinates(x, y);
                        onHoldForSeconds(null);
                        clickAtCoordinates(xCoord, yCoord);
                        break;
                    case Constants.INSERT:
                        scrollToCoordinates(x, y);
                        onHoldForSeconds(null);
                        clickAtCoordinates(xCoord, yCoord);
                        onHoldForSeconds(null);
                        typeCharacters(instruction, action, data);
                        break;
                    case Constants.HOLD:
                        onHoldForSeconds(instruction);
                        break;
                    case Constants.REFRESH:
                        refreshPage();
                        break;
                    case Constants.QUIT:
                        quit(0);
                        break;
                    case Constants.SCREEN:
                        // screenshot();
                        break;
                    case Constants.EXTRACT:
                        break;
                    case Constants.LIST_OPERATION:
                }
                onHoldForSeconds(null);
            }
        }
    }

    private void scrollToCoordinates(int x, int y) {
        int maxHeight = abrWebDriver.getDriver().manage().window().getSize().getHeight();
        int maxWidth = abrWebDriver.getDriver().manage().window().getSize().getWidth();
        int offsetY = y - maxHeight;
        int offsetX = x - maxWidth;
        if (offsetX > 0 || offsetY > 0) {
            String script = "function getScrollableParent(element){\n" + "    console.log(\"finding\");"
                    + "    let value = window.getComputedStyle(element).overflowY;\n"
                    + "    if(value !== \"scroll\" && value !== \"auto\"){\n"
                    + "        return getScrollableParent(element.parentNode);\n"
                    + "    }\n"
                    + "    return element;\n"
                    + "}\n"
                    + "getScrollableParent(document.elementFromPoint("
                    + (maxWidth / 2) + "," + (maxHeight / 2)
                    + ")).scrollTo(" + Math.max(offsetX, 0) + "," + Math.max(offsetY, 0) + ");" + "return true;";
            new WebDriverWait(abrWebDriver.getDriver(), Duration.ofSeconds(10))
                    .until((item) -> (Boolean) ((JavascriptExecutor) abrWebDriver.getDriver()).executeScript(script));
        }
    }

    private void clickAtCoordinates(int x, int y) {
        /*
        String script = "function createCircle(x, y, diameter) {\n" +
                "    const randomColor = Math.floor(Math.random()*16777215).toString(16);\n" +
                "\n" +
                "    return `\n" +
                "    <svg style='height:100%;width:100%;position:absolute;top:0;z-index:9999'><circle\n" +
                "        cx=\"${x}\"\n" +
                "      cy=\"${y}\"\n" +
                "      r=\"${diameter/2}\"\n" +
                "      fill=\"#${randomColor}\"\n" +
                "    ></circle></svg>\n" +
                "  `;\n" +
                "}\n" +
                "\n" +
                "function pri(ev){\n" +
                "    console.log(ev);\n" +
                "    document.body.innerHTML += createCircle(ev.pageX,ev.pageY,10);\n" +
                "}\n" +
                "\n" +
                "window.addEventListener(\"click\", pri);";
        ((JavascriptExecutor)driver).executeScript(script);
         */
        new Actions(abrWebDriver.getDriver()).moveToLocation(x, y).click().perform();
    }

    private void typeCharacters(BlockLoopInstructionLoadDTO instruction, String action, Map<String, String> data) {
        String value = null;
        if (data != null) {
            String[] arr = UtilsMethods.splitIfContains(action, Constants.ACTION_SPECIFICATIONS_SPLITTER);
            if (arr.length > 1) {
                String dataFieldName = arr[1].split(Constants.PATH_FIELD_SUBSTITUTION)[0];
                value = data.get(dataFieldName);
            }
        } else {
            value = instruction.getDefaultValue();
        }
        if (instruction.isEncrypted()) {
            value = CryptationAlgorithm.decrypt(value);
        }
        new Actions(abrWebDriver.getDriver()).sendKeys(value).perform();
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

    public List<ABRWebElement> createAdvancedABRElement(Map<String, WebElement> mapAdvanced, String attribute) {
        List<ABRWebElement> listABRElements = new ArrayList<>();
        if (!mapAdvanced.isEmpty()) {
            ABRLogger.getInstance(ABRScannedElementPane.class)
                    .fine(String.format("Advance Search Element with total of %s elements", mapAdvanced.size()));

            for (Map.Entry<String, WebElement> entry : mapAdvanced.entrySet()) {
                WebElement element = entry.getValue();
                String xpath = entry.getKey();
                String attributeValue = element.getAttribute(attribute);
                System.out.println("ABR Element Creation ->  Tag: " + element.getTagName() + ", " + attribute + ": "
                        + attributeValue + ", XPath: " + xpath);

                try {
                    listABRElements.add(new ABRWebElement(entry, attribute, botJob.getId()));
                } catch (Exception ex) {
                    ABRLogger.getInstance(ABRScannedElementPane.class)
                            .fine(String.format(
                                    "Error attempt to create Advance Element  attribute: %s xPath: %s\nError: %s",
                                    attributeValue, xpath, ex.getMessage()));
                }
            }
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
        jsExecutor = (JavascriptExecutor) driver;
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
}

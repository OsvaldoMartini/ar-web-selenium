package com.allinweb.ch.component.pane;

import com.allinweb.ch.builder.WebElementAttributeEnum;
import com.allinweb.ch.builder.WebElementAttributeTypeValueEnum;
import com.allinweb.ch.builder.WebElementTagNameEnum;
import com.allinweb.ch.component.listCell.ABRCellFactory;
import com.allinweb.ch.component.listCell.ABRWebElementListCell;
import com.allinweb.ch.component.model.BlockLoadDTO;
import com.allinweb.ch.component.model.BlockLoopInstructionLoadDTO;
import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.model.InstructionReferenceLoadDTO;
import com.allinweb.ch.component.pane.base.ABRPane;
import com.allinweb.ch.component.scene.ABRAlertScene;
import com.allinweb.ch.component.scene.ABRNewHomeBankingScene;
import com.allinweb.ch.control.ABRComponentBuilder;
import com.allinweb.ch.core.ABRSharedResources;
import com.allinweb.ch.driver.ABRWebDriver;
import com.allinweb.ch.driver.ABRWebElement;
import com.allinweb.ch.facade.PerformActions;
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
import java.util.*;
import java.util.Date;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
import javax.net.ssl.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.remote.RemoteWebElement;
import org.openqa.selenium.support.pagefactory.ByChained;
import org.openqa.selenium.support.ui.WebDriverWait;

public class ABRScannedElementPane extends ABRPane {

    //    private Stage compStage;

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

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int MIN_LENGTH = 3;
    private static final int MAX_LENGTH = 30;
    private static final Random RANDOM = new Random();

    private final ABRComponentBuilder componentBuilder = new ABRComponentBuilder();

    private DatabaseUserDTO databaseUserDto;

    private BotJobDTO botJob;
    private BlockDTO blockJob;
    private int currentBlockId;
    private List<BlockLoadDTO> blockLoadList = new ArrayList<>();
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
    private Button leftButton;
    private Button rightButton;
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

    private Text currentURL;
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
    private Map<String, String> mapExport;

    List<BlockLoopInstructionLoadDTO> instructionsExecuted = new ArrayList<>();

    Map<String, WebElement> mapAdvanced = new HashMap<>();

    // Very important sequence on initiation
    private static ABRPriorities abrPriorities;
    private static Map<String, String> savedReferences;
    private static int reduceSearchCriteria;
    private static ABRPropertyManager managerProps;
    private static final PerformActions performAction;
    // Static block to initialize
    static {
        abrPriorities = ABRPriorities.getInstance();
        performAction = PerformActions.getInstance();
        savedReferences = new HashMap<>();
        managerProps = ABRPropertyManager.getInstance();
    }

    public ABRWebDriver getAbrWebDriver() {
        return abrWebDriver;
    }

    public ABRScannedElementPane(BotJobDTO botJob, BlockDTO blockJob, ABRWebDriver abrWebDriver) {
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

        handleWindowHandlesChange();

        topPane = componentBuilder.createTopPanel(ABRConstants.SPACE_L, ABRConstants.SPACE_SM);
        bottomPane = componentBuilder.createBottomPanel(ABRConstants.SPACE_L, ABRConstants.SPACE_SM);
        bottomPaneTime = componentBuilder.createBottomPanel(ABRConstants.SPACE_L, ABRConstants.SPACE_SM);
        contentPane =
                componentBuilder.createContentPanel(ABRConstants.SPACE_L, ABRConstants.SPACE_XL, ABRConstants.SPACE_SM);

        scanButton = componentBuilder.buildButton(
                "Scan", ABRConstants.SPACE_L, ABRConstants.ICON_SEARCH, ABRConstants.SPACE_M, new Insets(5));
        addNewElement = componentBuilder.buildButton(
                "Add Element", ABRConstants.SPACE_L, ABRConstants.ICON_TICK, ABRConstants.SPACE_M, new Insets(5));

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

        leftButton = componentBuilder.buildButton(
                "Previous", ABRConstants.SPACE_M, ABRConstants.ICON_LEFT, ABRConstants.SPACE_M, new Insets(5.0D));
        rightButton = componentBuilder.buildButton(
                "Next", ABRConstants.SPACE_M, ABRConstants.ICON_RIGHT, ABRConstants.SPACE_M, new Insets(5.0D));

        leftButton.setDisable(true);
        rightButton.setDisable(true);

        leftButton.setOnAction(e -> switchToLeftTab());
        rightButton.setOnAction(e -> switchToRightTab());

        currentURL = new Text("");
        currentURL.setFill(Color.BLUE);
        currentURL.setStyle("-fx-font-size: 16px;");

        updateSceneTitleWithCurrentURL(botJob.getHomeBanking().getUrl());

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
            gridPaneTop.add(leftButton, 7, 0);
            gridPaneTop.add(rightButton, 8, 0);

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
            stackLabelOthers.getChildren().add(labelOthers);
            stackLabelOthers.setAlignment(Pos.CENTER);
            VBox elements3VBox = new VBox(stackLabelOthers, scannedElements3);

            boxListViews.getChildren().addAll(elements1VBox, elements2VBox, elements3VBox, textFieldVBox);

            VBox.setVgrow(boxListViews, Priority.ALWAYS);

            verticalBox.getChildren().addAll(currentURLBox, boxListViews);
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

            lastBrowserTab();

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
        addNewElement.setOnAction(e -> {
            if (searchReturn.getElement() != null) {
                insertNewElement();
            }
        });

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

    private void lastBrowserTab() {
        // Get all window handles (all open tabs/windows)
        windowHandles = abrWebDriver.getDriver().getWindowHandles();

        // Convert the window handles set to a list
        List<String> windowHandlesList = new ArrayList<>(windowHandles);

        // Switch to the last window (newly opened tab)
        abrWebDriver.getDriver().switchTo().window(windowHandlesList.get(windowHandlesList.size() - 1));
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
                                Constants.ABSOLUT_XPATH); // BECAUSE OS LIMITATION OF ACCESS DB 255 CHARACTER
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
        manageUIScanWithoutNameAndId();

        manageUIScanPriorities();
        manageUIScanInputs();
        manageUIScanClickable();
        //        manageUIScanOutputs();
    }

    private void manageUIScanWithoutNameAndId() {
        idAttributeFirst = false;
        nameAttributeFirst = false;
        withoutNameAndId = true;
        // addProgressBar();
        scanABRElementsAsync(
                null, null, null, webElementObservableList1, "input", "UI Scan \"Inputs\" Without Name And Id");
        // addProgressBar();
        scanABRElementsAsync(
                null, null, null, webElementObservableList1, "button", "UI Scan \"Buttons\" Without Name And Id");
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

    private void manageUIScanIdsFirst() {
        idAttributeFirst = true;
        nameAttributeFirst = false;
        withoutNameAndId = false;
        scanABRElementsAsync(null, null, null, webElementObservableList1, "id", "UI Scan Ids First");
    }

    private void manageUIScanInputs() {
        List<WebElementTagNameEnum> inputTags = WebElementTagNameEnum.insertableTags();
        for (WebElementTagNameEnum tag : inputTags) {
            // addProgressBar();
            scanABRElementsAsync(
                    null,
                    By.tagName(tag.getValue()),
                    ABRWebElement::isNotClickable,
                    webElementObservableList1,
                    null,
                    "UI Scan Inputs");
        }
    }

    private void manageUIScanClickable() {
        List<WebElementTagNameEnum> clickableTags = WebElementTagNameEnum.clickableTags();
        for (WebElementTagNameEnum tag : clickableTags) {
            // addProgressBar();
            scanABRElementsAsync(
                    null,
                    By.tagName(tag.getValue()),
                    ABRWebElement::isClickable,
                    webElementObservableList2,
                    null,
                    "UI Scan Clickable");
        }
    }

    private void manageUIScanPriorities() {
        List<WebElement> webElements = managePrioritiesCriteria();
        //        manageUIScanPrioritiesJSoup();
        //        scanABRElementsAsync(By.cssSelector("*[" + extRef + "]"), webElementObservableList3);
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
        String extRef = ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.WEBDRIVER_EXT_REFERENCE);
        // addProgressBar();
        scanABRElementsAsync(By.cssSelector("*[" + extRef + "]"), webElementObservableList2, "UI Scan Outputs");
    }

    private void scanABRElementsAsync(
            By criteria, ObservableList<ABRWebElement> listToAddNewElements, String criteriaMSG) {
        scanABRElementsAsync(null, criteria, null, listToAddNewElements, null, criteriaMSG);
    }

    private void scanABRElementsAsync(
            List<WebElement> preElements,
            By criteria,
            Predicate<ABRWebElement> filterCondition,
            ObservableList<ABRWebElement> listToAddNewElements,
            String elementType,
            String criteriaMSG) {

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
        manageUIScanClickable();
        manageUIScanOutputs();
    }

    private void refreshOtherElemBtn() {
        webElementObservableList3.clear();
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

                    if (abrWebElement.getSavedReferences().size() == 0) {
                        performAction.showAlert(
                                Alert.AlertType.ERROR,
                                "ERROR ADD WEB ELEMENT",
                                "Instructions CANNOT BE ADDED WITHOUT LOCATORS!",
                                "The Instruction \""
                                        + abrWebElement.getElement().getTagName() + "\" don't have any locators");
                        return;
                    }

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
                        loadBlocksForBotJob(this.botJob.getId());

                        BotJobLoadDTO botJobLoadDTO = loadBotJob(this.botJob.getId());

                        if (botJobLoadDTO == null) {
                            performAction.showAlert(
                                    Alert.AlertType.ERROR,
                                    "Bot Job DOES NOT EXIST",
                                    "Verify the Bot Job Name if have any: ",
                                    String.format(
                                            "Check if you already have a Bot Job \"%\" Created!",
                                            this.botJob.getName()));

                            ABRLogger.getInstance(Thread.class)
                                    .severe(String.format(
                                            "Check if you already have a Bot Job \"%\" Created!",
                                            this.botJob.getName()));
                            return;
                        }

                        // It Prevents Start without blocks
                        if (blockLoadList.isEmpty()) {

                            // It Prevents Start without blocks
                            SavedBlocksDTO savedBlocksDTO = new SavedBlocksDTO();

                            savedBlocksDTO.setDescription("Default Block description");
                            savedBlocksDTO.setName("Default Block");
                            BlockDTO blockDTO =
                                    performAction.createBlocksDTOFromSavedBlocksDTO(savedBlocksDTO, this.botJob);
                            BotJobDTO botJob = ABRSharedResources.getInstance()
                                    .getEntityById(BotJobDTO.class, this.botJob.getId());
                            blockDTO.setTypeId(1);
                            blockDTO.setBotJob(botJob);
                            blockDTO.setName("Default Block");
                            blockDTO.setDescription("Default Block description");

                            //            ABRSharedResources.getInstance().addEntity(blockDTO, BlockDTO.class);

                            currentBlockId = createBlock(blockDTO);

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
                        } else {
                            if (blockLoadList.size() > 0 && this.blockJob == null) {
                                currentBlockId = blockLoadList.get(0).getId();
                                setBlockJob(
                                        ABRSharedResources.getInstance().getEntityById(BlockDTO.class, currentBlockId));
                            } else if (this.blockJob != null) {
                                currentBlockId = this.blockJob.getId();
                            }
                        }

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
                                                (instr) -> instr.getBlock().getId() == currentBlockId);
                                ABRLogger.getInstance(Task.class).finer("THREAD: instruction list size " + list.size());

                                BlockLoopInstructionDTO instruction =
                                        abrWebElement.buildBlockLoopInstruction(list.size());
                                instruction.setBlock(blockJob);
                                instruction.setInstructionOrderNumber(list.size() + 1);

                                ABRLogger.getInstance(Task.class).fine("THREAD: adding instruction to database");
                                //                                ABRSharedResources.getInstance()
                                //                                        .addEntity(instruction,
                                // BlockLoopInstructionDTO.class, () -> {

                                int newId = preFillAddInstruction(
                                        instruction.getName(),
                                        instruction.getDescription(),
                                        instruction.getActions(),
                                        instruction.getOperation(),
                                        instruction.getOnHoldSeconds(),
                                        instruction.getVariableId(),
                                        instruction.getInstructionOrderNumber(),
                                        instruction.getExportToABR(),
                                        instruction.getPath(),
                                        currentBlockId);

                                if (newId < 0) {

                                    performAction.showAlert(
                                            Alert.AlertType.ERROR,
                                            "Error Add New \"Component\" Instruction",
                                            "Not possible to insert new Operation",
                                            String.format(
                                                    "\"Component\" Instruction \"%s\"\nCannot be saved",
                                                    instruction.getName()));

                                    return null;
                                }

                                instruction.setId(newId);

                                abrWebElement.setInstructionId(instruction.getId());
                                List<InstructionReferenceDTO> queue = new ArrayList<>();
                                for (String key :
                                        abrWebElement.getSavedReferences().keySet()) {
                                    InstructionReferenceDTO reference = new InstructionReferenceDTO();
                                    reference.setReferenceType(key);
                                    reference.setValue(
                                            abrWebElement.getSavedReferences().get(key));
                                    //
                                    // reference.setBlockLoopInstructionDTO(instruction);
                                    queue.add(reference);
                                }
                                try {

                                    Platform.runLater(() -> {
                                        boolean saved = insertReferences(queue, instruction.getId());
                                        if (saved) {

                                            new ABRAlertScene(
                                                    Alert.AlertType.INFORMATION,
                                                    "Web Instruction Add",
                                                    "The Web Instruction \"" + instruction.getName()
                                                            + "\" with "
                                                            + queue.size() + " reference locators"
                                                            + "\nHas been added successfully!",
                                                    ButtonType.OK);
                                        } else {
                                            new ABRAlertScene(
                                                    Alert.AlertType.ERROR,
                                                    "Add Web Instruction FAILED",
                                                    "The Instruction " + instruction.getName() + " with "
                                                            + queue.size() + " reference locators"
                                                            + "\nWas Added!"
                                                            + "\nTHE ENGINE IS GOING TO FAIL FOR THIS ELEMENT",
                                                    ButtonType.OK);
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
                    previousBlock.setBotJobId(previousBotJob.getId());
                    previousBlock.setBotJobName(previousBotJob.getName());

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

        if (abrWebDriver.getDriver().getWindowHandles().size() != performAction.windowHandlesList.size()) {
            performAction.updateWindowHandlesList();
            updateButtonState();
        }

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
            e.printStackTrace();
        }

        List<BlockLoadDTO> blocksLoaded = botLoadJobs.get(0).getBlockLoadDTOList();

        //        ABRPropertyManager managerProps = ABRPropertyManager.getInstance();
        String excelPath = ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.FOLDER_PATH_EXCEL);
        excelPath = excelPath + "\\" + blocksLoaded.get(0).getBotJobName() + ".xlsx";
        if (!(new File(excelPath)).exists()) {
            performAction.showAlert(
                    Alert.AlertType.ERROR,
                    "Missing file excel",
                    "IS MANDATORY TO HAVE EXCEL FILE FOR TESTS"
                            + "\nPlease generate and compile the data of the file excel first before launching the bot job",
                    excelPath);
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
            performAction.showAlert(
                    Alert.AlertType.ERROR, "Excel File Empty", "IS MANDATORY TO HAVE DATA FOR TESTS", excelPath);
            return false;
            //            Platform.exit();
        }
        if (extractedData.getErrorMessage() != null) {
            //				showAlert("Excel Data File", "Warning: Excel File exist" , "Fields in the excel not matching the
            // botjob requirements");
            //            System.out.println("Fields in the excel not matching the botjob requirements");
            //            performAction.showAlert(Alert.AlertType.ERROR,"Excel File Empty", "IS MANDATORY TO HAVE DATA
            // FOR TESTS",
            // excelPath);
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

        int botJobId = blocksLoaded.get(0).getBotJobId();

        // Original BotJobDTO
        BotJobDTO selectedJob = ABRSharedResources.getInstance().getEntityById(BotJobDTO.class, botJobId);

        String baseLogString = blocksLoaded.get(0).getBotJobName()
                + Constants.FIELDS_SEPARATOR
                + labelsValue.getProperty(Labels.START);
        printBaseLog(baseLogFile, generateTimestamp(), baseLogString);
        ExcelWriter.ExcelChain writerReport =
                new ExcelWriter(selectedJob.getName(), abrWebDriver.getDriver()).withPurpose("report");
        writerReport.insertReportHead();

        ExcelWriter.ExcelChain writerExport =
                new ExcelWriter(selectedJob.getName(), abrWebDriver.getDriver()).withPurpose("export");
        boolean excelExportOnceCreation = true;
        //        writerExport.insertReportHead();

        boolean success = true;
        boolean stopAll = false;
        long botJobStartTime = System.nanoTime();
        long totalExecutionTime = 0;
        String lastInstructionExecuted = "No instruction executed yet";
        String resultActions = "";
        short status = (short) ExcelReportStatusEnum.ERROR.ordinal();
        Map<String, String> dataExcel = null;

        clearFields();

        //        ExcelReportDTO report = new ExcelReportDTO();
        //        report.setOrder((short) blocksLoaded.get(0).getId());
        //        report.setStartDate(LocalDateTime.now());
        //        report.setBatchJobId(selectedJob.getId());
        //        report.setBotJobDTO(selectedJob);
        //        report.setStatus((short) ExcelReportStatusEnum.NOT_RUN.ordinal());

        mapOperators = new HashMap<>();
        mapExport = new HashMap<>();
        int executionTimes = 0;
        int execLimitReach = 0;
        String limitReach = ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.BLOCK_EXEC_LIMIT);
        if (limitReach != null) {
            execLimitReach = Integer.parseInt(limitReach);
        }

        int exportIndex = 1;
        if (extractedData.getNumberOfDataRows() > 0) {
            int currentBlock = 0;
            outerLoop:
            while (currentBlock <= blocksLoaded.size() - 1
                    && blocksLoaded.size() > 0
                    && !stopAll
                    && executionTimes < execLimitReach) {
                instructionsExecuted.clear();
                BlockLoadDTO blockLoad = blocksLoaded.get(currentBlock);
                executionTimes++;
                boolean jumpGoto = false;

                for (int i = 0; success && i < extractedData.getNumberOfDataRows() && !stopAll; i++) {
                    boolean ifClause = false;
                    boolean ifFailed = false;
                    boolean ifDone = false;
                    boolean elseClause = false;
                    boolean elseFailed = false;
                    mapExport.clear();
                    writerReport.insertBlockSeparation(blockLoad.getName());

                    // Insert the field name and value rows below the block name
                    for (int j = 0;
                            j < blockLoad.getBlockLoopInstructionLoadDTOS().size() && !stopAll;
                            j++) {
                        BlockLoopInstructionLoadDTO currentInstruction =
                                blockLoad.getBlockLoopInstructionLoadDTOS().get(j);

                        // Allow Re-Execute Instructions in Previous Blocks
                        //                        if (currentInstruction.getExecuted() == null ||
                        // !currentInstruction.getExecuted()) {
                        boolean execOperation = false;
                        boolean checkOperation = false;
                        boolean excelWriteOperation = false;

                        String xPathOperation = null;
                        String parentField = null;
                        int parentId = currentInstruction.getParentId();

                        String[] actions = currentInstruction.getActions().split(Constants.ACTIONS_AND_PATHS_SPLITTER);
                        String[] operations = currentInstruction.getOperation() != null
                                ? currentInstruction.getOperation().split(Constants.ACTION_SPECIFICATIONS_SPLITTER)
                                : null;

                        // If IF clause failed, look for ELSE to start executing the ELSE block

                        if (actions[0].equalsIgnoreCase(ABRConstants.IF)) {

                            ABRLogger.getInstance(ABRScannedElementPane.class)
                                    .info("Initial Execution \"IF\" for Block :\"" + blockLoad.getName() + "\"");

                            ifClause = true;
                            ifFailed = false; // Reset failure status for this IF clause
                            ifDone = false;
                            continue;
                        }

                        if (ifClause && ifFailed && !ifDone) {

                            if (actions[0].equalsIgnoreCase(ABRConstants.ELSE)) {
                                ABRLogger.getInstance(ABRScannedElementPane.class)
                                        .warning("Closing Block { IF -> ELSE} -> Failed Execution \"IF\" for Block :\""
                                                + blockLoad.getName() + "\"");

                                ifClause = false;
                                ifFailed = false;
                                elseClause = true;
                                elseFailed = false; // Reset failure status for this ELSE clause
                                continue;
                            } else {
                                // Skip until ELSE is found
                                continue;
                            }
                        } else if (elseClause && elseFailed) {
                            if (actions[0].equalsIgnoreCase(ABRConstants.ENDIF)) {
                                ABRLogger.getInstance(ABRScannedElementPane.class)
                                        .warning(
                                                "Closing Block { ELSE -> ENDIF } -> Failed Execution \"ELSE\" for Block :\""
                                                        + blockLoad.getName() + "\"");

                                elseClause = false;
                                elseFailed = false; // Reset failure status for this ELSE clause
                                continue;
                            } else {
                                // Skip until ELSE is found
                                continue;
                            }
                        }

                        // Process ENDIF to reset flags and resume normal flow after IF-ELSE blocks
                        if (ifClause && !ifFailed && actions[0].equalsIgnoreCase(ABRConstants.ELSE)) {

                            ABRLogger.getInstance(ABRScannedElementPane.class)
                                    .info("Closing Block { IF -> ELSE } -> Success Execution \"IF\" for Block :\""
                                            + blockLoad.getName() + "\"");

                            ifClause = false;
                            ifFailed = false;
                            ifDone = true;
                            continue;
                        }

                        // Process ENDIF to reset flags and resume normal flow after IF-ELSE blocks
                        if (elseClause && !elseFailed && actions[0].equalsIgnoreCase(ABRConstants.ENDIF)) {

                            ABRLogger.getInstance(ABRScannedElementPane.class)
                                    .info("Closing Block { ELSE -> ENDIF } -> Success Execution \"ELSE\" for Block :\""
                                            + blockLoad.getName() + "\"");

                            elseClause = false;
                            elseFailed = false;
                            continue;
                        } else if (ifDone && !actions[0].equalsIgnoreCase(ABRConstants.ENDIF)) {
                            continue;
                        }

                        // Process ENDIF to reset flags and resume normal flow after IF-ELSE blocks
                        if (!ifClause
                                && !ifFailed
                                && !elseClause
                                && !elseFailed
                                && ifDone
                                && actions[0].equalsIgnoreCase(ABRConstants.ENDIF)) {
                            ifDone = false;
                            ABRLogger.getInstance(ABRScannedElementPane.class)
                                    .info("Skiping { ENDIF } -> Success Skipping \"ENDIF\" for Block :\""
                                            + blockLoad.getName() + "\"");

                            continue;
                        }

                        if (actions[0].equalsIgnoreCase(ABRConstants.GOTO)) {
                            jumpGoto = true;

                        } else if (actions[0].equalsIgnoreCase(ABRConstants.GET_VALUE)
                                || actions[0].equalsIgnoreCase(ABRConstants.SET_VALUE)) {

                            execOperation = true;
                            try {
                                xPathOperation = blockLoad.getBlockLoopInstructionLoadDTOS().stream()
                                        .filter(f -> f.getId() == currentInstruction.getParentId())
                                        .findFirst()
                                        .get()
                                        .getPath();

                                parentField = blockLoad.getBlockLoopInstructionLoadDTOS().stream()
                                        .filter(f -> f.getId() == currentInstruction.getParentId())
                                        .findFirst()
                                        .get()
                                        .getName();

                                parentField = parentId + "-" + parentField;

                            } catch (Exception ex) {
                                resultActions = performAction.parentIdWrongBlock(
                                        currentInstruction, blockLoad, ifClause, elseClause);

                                if (!ifClause && !elseClause) {
                                    stopAll = true;
                                    success = false;
                                    break;
                                } else if (ifClause) {
                                    ifFailed = true;
                                } else if (elseClause) {
                                    elseFailed = true;
                                }
                            }

                        } else if (actions[0].equalsIgnoreCase(ABRConstants.CHECK_VALUE)) {
                            try {
                                parentField = blockLoad.getBlockLoopInstructionLoadDTOS().stream()
                                        .filter(f -> f.getId() == currentInstruction.getParentId())
                                        .findFirst()
                                        .get()
                                        .getName();

                                parentField = parentId + "-" + parentField;

                                checkOperation = true;
                            } catch (Exception ex) {
                                resultActions = performAction.getValueIsNotDefined(
                                        currentInstruction, lastInstructionExecuted, ifClause, elseClause);

                                if (!ifClause && !elseClause) {
                                    stopAll = true;
                                    success = false;
                                    break;
                                } else if (ifClause) {
                                    ifFailed = true;
                                } else if (elseClause) {
                                    elseFailed = true;
                                }

                                break;
                            }
                        } else if (actions[0].equalsIgnoreCase(ABRConstants.EXTRACT_FIELD)) {
                            try {
                                parentField = blockLoad.getBlockLoopInstructionLoadDTOS().stream()
                                        .filter(f -> f.getId() == currentInstruction.getParentId())
                                        .findFirst()
                                        .get()
                                        .getName();

                                parentField = parentId + "-" + parentField;

                                excelWriteOperation = true;
                            } catch (Exception ex) {
                                resultActions = performAction.getValueIsNotDefined(
                                        currentInstruction, lastInstructionExecuted, ifClause, elseClause);

                                if (!ifClause && !elseClause) {
                                    stopAll = true;
                                    success = false;
                                    break;
                                } else if (ifClause) {
                                    ifFailed = true;
                                } else if (elseClause) {
                                    elseFailed = true;
                                }

                                break;
                            }
                        }

                        long currentInstructionStartTime = System.nanoTime();
                        File logFileForSingleExcel = excelReader.createLogFile(excelPath);

                        fillUpCurretLocators(currentInstruction);

                        try {
                            if (jumpGoto) {

                                lastInstructionExecuted = currentInstruction.getName()
                                        + Constants.BLANK_STRING
                                        + currentInstruction.getOperation();

                                try {
                                    int blockOrderNumber = blocksLoaded.stream()
                                            .filter(block -> block.getId()
                                                    == currentInstruction.getParentId()) // Filter by blockId
                                            .findFirst() // Get the first matching block
                                            .map(BlockLoadDTO::getBlockOrderNumber) // Map to blockOrderNumber
                                            .orElseThrow(() -> new NoSuchElementException(
                                                    "No block found with the given blockId")); // Handle if no
                                    // block is found
                                    currentBlock = blockOrderNumber - 1;
                                    currentInstruction.setExecuted(true);

                                    resultActions = "GO TO -->" + currentInstruction.getName() + " --> "
                                            + currentInstruction.getOperation();

                                    // Assuming currentInstruction and instructionsExecuted are already defined
                                    if (currentInstruction != null
                                            && instructionsExecuted.stream()
                                                    .noneMatch(instruction -> instruction.getInstructionOrderNumber()
                                                            == currentInstruction.getInstructionOrderNumber())) {
                                        instructionsExecuted.add(currentInstruction);
                                    }

                                    success = true;
                                } catch (Exception ex) {
                                    resultActions = "Failed to Execute -> " + currentInstruction.getName() + " --> "
                                            + currentInstruction.getOperation();
                                    success = false;

                                    resultActions = performAction.blockGotoFailed(resultActions);
                                }

                                long duration = performAction.duration(currentInstructionStartTime);
                                performAction.excelReportWrite(
                                        success, currentInstruction, duration, dataExcel, writerReport);
                                totalExecutionTime += duration;

                                status = performAction.operationLog(
                                        success,
                                        currentInstruction.isOptional()
                                                ? "OPTIONAL INSTRUCTION"
                                                : "MANDATORY INSTRUCTION",
                                        resultActions,
                                        lastInstructionExecuted,
                                        duration);
                                if (success) {
                                    continue outerLoop;
                                } else {

                                    if (!ifClause && !elseClause) {
                                        stopAll = true;
                                        break;
                                    } else if (ifClause) {
                                        ifFailed = true;
                                    } else if (elseClause) {
                                        elseFailed = true;
                                    }
                                }

                            } else if (!execOperation && !checkOperation && !excelWriteOperation) {
                                dataExcel = extractedData.getRowFieldValues(i);

                                lastInstructionExecuted = currentInstruction.getName()
                                        + Constants.BLANK_STRING
                                        + currentInstruction.getPath();

                                resultActions = performAction.performWebActions(
                                        dataExcel, currentInstruction, botJobId, blockLoad.getName());

                                if (resultActions != null) {
                                    currentInstruction.setExecuted(true);
                                    // Assuming currentInstruction and instructionsExecuted are already defined
                                    if (currentInstruction != null
                                            && instructionsExecuted.stream()
                                                    .noneMatch(instruction -> instruction.getInstructionOrderNumber()
                                                            == currentInstruction.getInstructionOrderNumber())) {
                                        instructionsExecuted.add(currentInstruction);
                                    }
                                    success = true;
                                } else {
                                    resultActions = "Failed to Execute -> " + currentInstruction.getName();
                                    success = false;
                                }

                                long duration = performAction.duration(currentInstructionStartTime);
                                performAction.excelReportWrite(
                                        success, currentInstruction, duration, dataExcel, writerReport);
                                totalExecutionTime += duration;

                                status = performAction.operationLog(
                                        success,
                                        currentInstruction.isOptional()
                                                ? "OPTIONAL INSTRUCTION"
                                                : "MANDATORY INSTRUCTION",
                                        resultActions,
                                        lastInstructionExecuted,
                                        duration);

                            } else if (execOperation) {
                                // Special Operators
                                lastInstructionExecuted = currentInstruction.getName()
                                        + Constants.BLANK_STRING
                                        + currentInstruction.getActions()
                                        + Constants.BLANK_STRING
                                        + currentInstruction.getOperation();

                                if (operations.length == 2) {
                                    resultActions = performAction.performActionOperator(
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
                                        success = true;
                                    } else {
                                        resultActions = "Failed to Execute Cmd: " + lastInstructionExecuted;
                                        success = false;
                                    }

                                } else {
                                    resultActions = "Failed to Execute Cmd: " + lastInstructionExecuted;
                                    success = false;
                                }

                                long duration = performAction.duration(currentInstructionStartTime);
                                performAction.excelReportWrite(
                                        success, currentInstruction, duration, dataExcel, writerReport);
                                totalExecutionTime += duration;

                                status = performAction.operationLog(
                                        success,
                                        currentInstruction.isOptional()
                                                ? "OPTIONAL INSTRUCTION"
                                                : "MANDATORY INSTRUCTION",
                                        resultActions,
                                        lastInstructionExecuted,
                                        duration);

                            } else if (checkOperation) {
                                // Check Validation Operator
                                lastInstructionExecuted = currentInstruction.getName()
                                        + Constants.BLANK_STRING
                                        + currentInstruction.getActions()
                                        + Constants.BLANK_STRING
                                        + currentInstruction.getOperation();

                                if (operations.length == 3) {
                                    if (mapOperators.containsKey(parentField)) {
                                        resultActions = "(" + parentField + ")" + String.join(":", operations);
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

                                        if (isOperationValid) {
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
                                            resultActions = performAction.checkValidationFailed(
                                                    parentField,
                                                    mapOperators.get(parentField),
                                                    lastInstructionExecuted,
                                                    operations,
                                                    ifClause,
                                                    elseClause);

                                            if (!ifClause && !elseClause) {
                                                stopAll = true;
                                                success = false;
                                                break;
                                            } else if (ifClause) {
                                                ifFailed = true;
                                            } else if (elseClause) {
                                                elseFailed = true;
                                            }
                                        }

                                    } else {
                                        resultActions = performAction.getValueIsNotDefined(
                                                currentInstruction, lastInstructionExecuted, ifClause, elseClause);
                                        if (!ifClause && !elseClause) {
                                            stopAll = true;
                                            success = false;
                                            break;
                                        } else if (ifClause) {
                                            ifFailed = true;
                                        } else if (elseClause) {
                                            elseFailed = true;
                                        }
                                    }

                                } else {
                                    resultActions = "Failed to Execute Cmd: " + lastInstructionExecuted;
                                    success = false;
                                }

                                long duration = performAction.duration(currentInstructionStartTime);
                                performAction.excelReportWrite(
                                        success, currentInstruction, duration, dataExcel, writerReport);
                                totalExecutionTime += duration;

                                status = performAction.operationLog(
                                        success,
                                        currentInstruction.isOptional()
                                                ? "OPTIONAL INSTRUCTION"
                                                : "MANDATORY INSTRUCTION",
                                        resultActions,
                                        lastInstructionExecuted,
                                        duration);

                            } else if (excelWriteOperation) {
                                // Excel Write Operator
                                lastInstructionExecuted = currentInstruction.getName()
                                        + Constants.BLANK_STRING
                                        + currentInstruction.getActions()
                                        + Constants.BLANK_STRING
                                        + currentInstruction.getOperation();

                                if (operations.length == 2) {
                                    if (mapOperators.containsKey(parentField)) {

                                        if (excelExportOnceCreation) {
                                            writerExport.insertReportHead();
                                            excelExportOnceCreation = false;
                                        }

                                        resultActions = "insertValueFieldNameInExcel-->" + parentField + "-"
                                                + mapOperators.get(parentField);
                                        if (mapExport.size() == 0) {
                                            writerExport.insertBlockSeparation(blockLoad.getName());
                                            exportIndex *= 2;
                                        }

                                        mapExport.put(parentField, mapOperators.get(parentField));
                                        // Insert the updated mapExport into the Excel after each instruction
                                        writerExport.insertFieldNameAndValueLastColumn(mapExport, exportIndex);

                                        performAction.onHoldForSeconds(null);

                                        if (resultActions != null) {
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
                                            resultActions = "Failed to Execute Cmd: " + lastInstructionExecuted;
                                            success = false;
                                        }

                                    } else {
                                        resultActions = performAction.getValueIsNotDefined(
                                                currentInstruction, lastInstructionExecuted, ifClause, elseClause);
                                        if (!ifClause && !elseClause) {
                                            stopAll = true;
                                            success = false;
                                            break;
                                        } else if (ifClause) {
                                            ifFailed = true;
                                        } else if (elseClause) {
                                            elseFailed = true;
                                        }
                                        break;
                                    }

                                } else {
                                    resultActions = "Failed to Execute Cmd: " + lastInstructionExecuted;
                                    success = false;
                                }

                                long duration = performAction.duration(currentInstructionStartTime);
                                performAction.excelReportWrite(
                                        success, currentInstruction, duration, dataExcel, writerReport);
                                totalExecutionTime += duration;

                                status = performAction.operationLog(
                                        success,
                                        currentInstruction.isOptional()
                                                ? "OPTIONAL INSTRUCTION"
                                                : "MANDATORY INSTRUCTION",
                                        resultActions,
                                        lastInstructionExecuted,
                                        duration);
                            }

                        } catch (Throwable t) {
                            if (!ifClause && !elseClause) {
                                stopAll = true;
                                success = false;
                                break;
                            } else if (ifClause) {
                                ifFailed = true;
                            } else if (elseClause) {
                                elseFailed = true;
                            }
                            currentInstruction.setExecuted(false);

                            long duration = performAction.duration(currentInstructionStartTime);
                            performAction.excelReportWrite(
                                    false, currentInstruction, duration, dataExcel, writerReport);
                            totalExecutionTime += duration;

                            status = performAction.operationLog(
                                    false,
                                    currentInstruction.isOptional() ? "OPTIONAL INSTRUCTION" : "MANDATORY INSTRUCTION",
                                    resultActions,
                                    lastInstructionExecuted,
                                    duration);

                            //                            throw new RuntimeException(t);
                        }

                        printLog(generateTimestamp(), logFileForSingleExcel, resultActions, success);

                        if (!success) {
                            //                            resultActions =
                            //                                    String.format("BotJob : %s failed",
                            // this.botJob.getName());
                            countdownTextField.setStyle("-fx-font-size: 16px; -fx-text-fill: red;");
                            countdownTextField.setText(resultActions);
                            if (!ifClause && !elseClause) {
                                stopAll = true;
                                success = false;
                                break;
                            }
                            //                                return false;
                        }

                        if (resultActions.equalsIgnoreCase("Close Browser")) {
                            stopAll = true;
                            break;
                        }
                        // }     END IF (currentInstruction.getExecuted() == null || !currentInstruction.getExecuted())
                        // ...
                    }
                }
                currentBlock++;
            }

            if (executionTimes >= execLimitReach) {
                performAction.alertExecutionTimes(executionTimes, lastInstructionExecuted);
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
                        resultActions = performAction.performWebActions(
                                dataDynamic,
                                currentInstruction,
                                botJobId,
                                blocksLoaded.get(j).getName());

                        if (resultActions != null) {
                            currentInstruction.setExecuted(true);
                            success = true;
                        } else {
                            resultActions = "Failed to Execute -> " + currentInstruction.getName();
                            success = false;
                        }

                        long duration = performAction.duration(currentInstructionStartTime);
                        performAction.excelReportWrite(success, currentInstruction, duration, null, writerReport);
                        totalExecutionTime += duration;

                        status = performAction.operationLog(
                                success,
                                currentInstruction.isOptional() ? "OPTIONAL INSTRUCTION" : "MANDATORY INSTRUCTION",
                                resultActions,
                                lastInstructionExecuted,
                                duration);

                    } catch (Throwable t) {
                        success = false;
                        currentInstruction.setExecuted(false);

                        long duration = performAction.duration(currentInstructionStartTime);
                        performAction.excelReportWrite(false, currentInstruction, duration, null, writerReport);
                        totalExecutionTime += duration;

                        status = performAction.operationLog(
                                false,
                                currentInstruction.isOptional() ? "OPTIONAL INSTRUCTION" : "MANDATORY INSTRUCTION",
                                resultActions,
                                lastInstructionExecuted,
                                duration);
                        //                        throw new RuntimeException(t);
                    }
                    printLog(generateTimestamp(), logFileForSingleExcel, resultActions, success);
                }
            }
        }

        if (totalExecutionTime == 0) {
            //            report.setDuration(0);
            writerReport.insertTotalExecutionTimes(botJobStartTime, botJobStartTime);
            //            try {
            //                ABRSharedResources.getInstance().addEntity(report, ExcelReportDTO.class);
            //            } catch (Exception ex) {
            //                ABRLogger.getInstance(ABRScannedElementPane.class)
            //                        .warning("Repository.write(report) Error:\n" + ex.getMessage());
            //            }
        }

        // PRINT END BASE LOG//
        if (success) {
            //            report.setStatus((short) ExcelReportStatusEnum.SUCCESS.ordinal());
            //            report.setDuration(totalExecutionTime / 100);
            writerReport.insertTotalExecutionTimes(botJobStartTime, System.nanoTime());
            //            try {
            //                ABRSharedResources.getInstance().addEntity(report, ExcelReportDTO.class);
            //            } catch (Exception ex) {
            //                ABRLogger.getInstance(ABRScannedElementPane.class)
            //                        .warning("Repository.write(report) Error:\n" + ex.getMessage());
            //            }
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
                    + Constants.FIELDS_SEPARATOR
                    + lastInstructionExecuted;
            //            report.setStatus(status);
            //            report.setDuration(totalExecutionTime / 100);
            writerReport.insertTotalExecutionTimes(botJobStartTime, System.nanoTime());
            //            try {
            //                ABRSharedResources.getInstance().addEntity(report, ExcelReportDTO.class);
            //                //                repository.write(report);
            //            } catch (Exception ex) {
            //                ABRLogger.getInstance(ABRScannedElementPane.class)
            //                        .warning("Repository.write(report) Error:\n" + ex.getMessage());
            //            }
        }
        printBaseLog(baseLogFile, generateTimestamp(), baseLogString);
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

    private static void printLog(String timeStamp, File logFile, String resultActions, boolean result) {
        String resultMsg = result ? Constants.SUCCESS : Constants.FAIL;
        String log = String.join(Constants.FIELDS_SEPARATOR, timeStamp, resultMsg, resultActions);

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
        String[] arr = UtilsMethods.splitIfContains(action, Constants.ACTION_SPECIFICATIONS_SPLITTER);
        if (arr.length > 1) {
            fieldName = arr[1].split(Constants.PATH_FIELD_SUBSTITUTION)[0];
        }

        new ExcelWriter(botJobName, abrWebDriver.getDriver())
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
                        performAction.onHoldForSeconds(null);
                        clickAtCoordinates(xCoord, yCoord);
                        break;
                    case Constants.INSERT:
                        scrollToCoordinates(x, y);
                        performAction.onHoldForSeconds(null);
                        clickAtCoordinates(xCoord, yCoord);
                        performAction.onHoldForSeconds(null);
                        typeCharacters(instruction, action, data);
                        break;
                    case Constants.HOLD:
                        performAction.onHoldForSeconds(instruction);
                        break;
                    case Constants.REFRESH:
                        performAction.refreshPage();
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
                performAction.onHoldForSeconds(null);
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
                    if (listABRElements.size() < 30) {
                        addProgressBar(1);
                    }
                    listABRElements.add(new ABRWebElement(entry, attribute, botJob.getId()));
                } catch (EnumConstantNotPresentException ex) {
                    throw ex;
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

    public List<BlockLoadDTO> loadBlocksForBotJob(int botJobId) {
        // SQL query to get the blocks for a specific bot job
        String query = "SELECT " + "b.id AS block_id, "
                + "b.block_order_number, "
                + "b.name AS block_name, "
                + "b.description AS block_description, "
                + "b.type_id, "
                + "bj.id AS bot_job_id, "
                + "bj.name AS bot_job_name "
                + "FROM bot_job bj "
                + "JOIN block b ON b.bot_job_id = bj.id "
                + "WHERE bj.id = "
                + botJobId + " " + // Use the botJobId directly in the query string
                "ORDER BY b.block_order_number ASC";

        // Initialize the necessary data structures
        blockLoadList.clear();
        Map<Integer, BlockLoadDTO> blockMap = new HashMap<>();

        // Use Statement to execute the query
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                // Load the Block information
                int blockId = rs.getInt("block_id");
                BlockLoadDTO blockDTO = blockMap.get(blockId);

                if (blockDTO == null) {
                    blockDTO = new BlockLoadDTO();
                    blockDTO.setId(blockId);
                    blockDTO.setBlockOrderNumber(rs.getInt("block_order_number"));
                    blockDTO.setName(rs.getString("block_name"));
                    blockDTO.setDescription(rs.getString("block_description"));
                    blockDTO.setTypeId(rs.getInt("type_id"));
                    blockDTO.setBotJobId(rs.getInt("bot_job_id"));
                    blockDTO.setBotJobName(rs.getString("bot_job_name"));

                    blockMap.put(blockId, blockDTO);
                    blockLoadList.add(blockDTO);
                }
            }
        } catch (SQLException e) {
            ABRLogger.getInstance(Thread.class)
                    .severe(String.format("Error loadBlockAll for botJobId %d\nError: %s", botJobId, e.getMessage()));
        }

        return blockLoadList;
    }

    public BotJobLoadDTO loadBotJob(int botJobId) {
        // SQL query to get the blocks for a specific bot job
        String query = "SELECT bj.id, "
                + " bj.name, "
                + " bj.description, "
                + " bj.home_banking_id, "
                + " bj.priority "
                + " FROM bot_job bj "
                + " WHERE bj.id = " + botJobId;

        // Initialize the necessary data structures

        // Use Statement to execute the query
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(query)) {
            BotJobLoadDTO botJobLoadDTO = new BotJobLoadDTO();

            while (rs.next()) {
                botJobLoadDTO = new BotJobLoadDTO();

                botJobLoadDTO.setId(rs.getInt("id"));
                botJobLoadDTO.setName(rs.getString("name"));
                botJobLoadDTO.setDescription(rs.getString("description"));
                botJobLoadDTO.setPriority(rs.getString("priority"));
                botJobLoadDTO.setHomeBankingId(rs.getInt("home_banking_id"));
            }
            return botJobLoadDTO;

        } catch (SQLException e) {
            ABRLogger.getInstance(Thread.class)
                    .severe(String.format("Error loadBlockAll for botJobId %d\nError: %s", botJobId, e.getMessage()));
        }

        return null;
    }

    private int createBlock(BlockDTO blockDTO) {
        // Generate a Unique-ID for the block
        Integer nextId = loadNextIdBlockData() + 1;
        Integer nextBlockOrder =
                loadNextBlockOrderNumber(blockDTO.getBotJobDTO().getId()) + 1;

        // Build the SQL insert query
        String insertSQL = "INSERT INTO block(id, block_order_number, description, name, type_id, bot_job_id) VALUES ("
                + nextId + ", "
                + nextBlockOrder + ", " // block_order_number
                + "'" + blockDTO.getDescription() + "', " // description
                + "'" + blockDTO.getName() + "', " // name
                + 1 + ", " // type_id
                + blockDTO.getBotJobDTO().getId() + ")"; // bot_job_id, assuming BotJobDTO has an ID

        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {
            stmt.executeUpdate(insertSQL);
            ABRLogger.getInstance(ABRScannedElementPane.class).info("Block data saved successfully id: " + nextId);
            return nextId;
        } catch (SQLException e) {
            ABRLogger.getInstance(ABRScannedElementPane.class).severe("saveBlock  \nError: " + e.getMessage());
            return -1;
        }
    }

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

    private Integer loadNextBlockOrderNumber(int botJobId) {
        //        String selectSQL = "SELECT NEXT_VAL fROM homeBankingSeq";
        String selectSQL = "SELECT MAX(ID) AS max_id FROM block where bot_job_id = " + botJobId;
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

    private boolean insertReferences(List<InstructionReferenceDTO> queue, int instructionId) {
        // Generate a Unique-ID for the block

        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {

            for (InstructionReferenceDTO reference : queue) {

                Integer nextId = loadNextIdBReferenceData() + 1;

                // Build the SQL insert query
                String insertSQL =
                        "INSERT INTO instruction_reference(id, reference_type, value, block_loop_instruction_id) VALUES ("
                                + nextId + ", "
                                + "'" + reference.getReferenceType() + "', "
                                + "'" + reference.getValue() + "', " // name
                                + instructionId + ")"; // bot_job_id, assuming BotJobDTO has an ID

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
            int savedCurrentBlockId) {

        BlockLoopInstructionDTO instructionDTO = new BlockLoopInstructionDTO();

        instructionDTO.setName(name);

        instructionDTO.setEncrypted(false);
        instructionDTO.setExportToABR(true);

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

        instructionDTO.setPath(xPath);

        // Wrap the persistence in a try-catch block
        int newId = -1;

        try {
            newId = insertInstruction(instructionDTO, savedCurrentBlockId);

        } catch (SQLException e) {

            ABRLogger.getInstance(ABRScannedElementPane.class)
                    .severe(String.format(
                            "Cannot Insert \"Instruction\"  \"%s\"\nCannot be saved!\nError: %s",
                            instructionDTO.getName(), e.getMessage()));
        }
        return newId;
    }

    private int insertInstruction(BlockLoopInstructionDTO instructionDTO, int savedCurrentBlockId) throws SQLException {
        // Generate a Unique-ID for the block

        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {

            Integer nextId = loadNextIdInstructionData() + 1;
            instructionDTO.setId(nextId);

            String pathValue = (instructionDTO.getPath() != null) ? "'" + instructionDTO.getPath() + "'" : "null";

            // Build the SQL insert query

            String insertSQL = "INSERT INTO block_loop_instruction(\n" + "id, "
                    + "action_custom_max_wait_sec, "
                    + "actions, "
                    + "block_marked, "
                    + "default_val, "
                    + "description, "
                    + "encrypted, "
                    + "export_to_abr, "
                    + "instruction_order_number, "
                    + "name, "
                    + "on_hold_seconds, "
                    //                    + "operation, "
                    + "optional, "
                    + "parent_id, "
                    + "path, "
                    + "variable_id, "
                    + "block_id)\n"
                    + "VALUES ("
                    + instructionDTO.getId()
                    + ", " + instructionDTO.getActionCustomMaxWaitSec()
                    + ", '" + instructionDTO.getActions() + "'"
                    + ", " + (instructionDTO.isBlockMarked() ? "true" : "false")
                    + ", '" + instructionDTO.getDefaultValue() + "'"
                    + ", '" + instructionDTO.getDescription() + "'"
                    + ", " + (instructionDTO.isEncrypted() ? 1 : 0)
                    + ", " + (instructionDTO.getExportToABR() ? 1 : 0)
                    + ", " + instructionDTO.getInstructionOrderNumber()
                    + ", '" + instructionDTO.getName() + "'"
                    + ", " + instructionDTO.getOnHoldSeconds()
                    //                    + ", '" + instructionDTO.getOperation() + "'"
                    + ", " + (instructionDTO.isOptional() ? 1 : 0)
                    + ", " + instructionDTO.getParentId()
                    + ", " + pathValue
                    + ", " + instructionDTO.getVariableId()
                    + ", " + savedCurrentBlockId
                    + ");";

            int rowsAffected = stmt.executeUpdate(insertSQL);
            if (rowsAffected > 0) {
                ABRLogger.getInstance(ABRNewCommandPane.class)
                        .info(String.format(
                                "New Instruction SAVED SUCCESSFULLY\nid: %d\nName: %s\nActions: %s\nOperation: %s",
                                instructionDTO.getId(),
                                instructionDTO.getName(),
                                instructionDTO.getActions(),
                                instructionDTO.getOperation()));
                return nextId;
            } else {
                ABRLogger.getInstance(ABRNewCommandPane.class)
                        .warning(String.format(
                                "Instruction NOT SAVED\nid: %d\nName: %s\nActions: %s\nOperations: %s",
                                instructionDTO.getId(),
                                instructionDTO.getName(),
                                instructionDTO.getActions(),
                                instructionDTO.getOperation()));
                return -1;
            }
        }
    }

    private Integer loadNextIdInstructionData() {
        //        String selectSQL = "SELECT NEXT_VAL fROM homeBankingSeq";
        String selectSQL = "SELECT MAX(ID) AS max_id FROM block_loop_instruction";
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(selectSQL)) {
            while (rs.next()) {
                return rs.getInt("max_id");
            }
        } catch (SQLException e) {
            ABRLogger.getInstance(ABRScannedElementPane.class)
                    .severe("loadNextIdInstructionData  \nError: " + e.getMessage());
        }
        return null;
    }
}

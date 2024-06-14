package com.allinweb.ch.component.pane;

import com.allinweb.ch.builder.WebElementAttributeEnum;
import com.allinweb.ch.builder.WebElementTagNameEnum;
import com.allinweb.ch.component.listCell.ABRCellFactory;
import com.allinweb.ch.component.listCell.ABRWebElementListCell;
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
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
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
import javafx.scene.control.*;
import javafx.scene.control.Alert;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.layout.Priority;
import javafx.stage.Modality;
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

    private static Wait<WebDriver> waitForPage;
    private static Wait<WebDriver> waitForAction;
    private boolean justCalledRefreshPage = false;

    private static File baseLogFile = null;
    private static SimpleDateFormat dateFormatter;

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int MIN_LENGTH = 3;
    private static final int MAX_LENGTH = 30;
    private static final Random RANDOM = new Random();
    private static final DateTimeFormatter FORMAT_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final String CONNECTION_TYPE = "jdbc:ucanaccess://";
    private static final String CONNECTION_PARAMETERS = ";memory=false;newDatabaseVersion=V2010";
    private static final Double LIST_VIEW_WIDTH = 350D;

    private static final int SECONDS = 10; // Total seconds for the countdown
    private int remainingSeconds = SECONDS;
    private Timeline timeline;
    private ExecutorService executorService;
    private Alert alertToShow;

    private final ABRComponentBuilder componentBuilder = new ABRComponentBuilder();

    private DatabaseUserDTO databaseUserDto;

    private ABRWebDriver abrWebDriver;
    private BotJobDTO botJob;
    private BlockDTO block;

    // UI COMPONENTS
    private HBox topPane;
    private HBox bottomPane;
    private AnchorPane contentPane;
    private ObservableList<ABRWebElement> webElementObservableList1;
    private ObservableList<ABRWebElement> webElementObservableList2;
    private ObservableList<ABRWebElement> webElementObservableList3;
    private ListView<ABRWebElement> scannedElements1;
    private ListView<ABRWebElement> scannedElements2;
    private ListView<ABRWebElement> scannedElements3;
    private Button scanButton;
    private Button addWaitButton;
    private Button addCloseActionButton;
    private Button addScreenButton;
    private Button configureButton;
    private Button launchBotJobButton;
    private Button refreshInputFieldsButton;
    private Button refreshOutputFieldsButton;
    private Button refreshOtherFieldsButton;
    private CheckBox checkBoxAction;
    private CheckBox checkActiveHover;
    private TextField xpathTextField;
    private TextField coordinatesTextField;

    private Boolean periodicActivated = false;

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
        abrWebDriver.openDriver(botJob.getHomeBanking().getUrl());

        topPane = componentBuilder.createTopPanel(ABRConstants.SPACE_L, ABRConstants.SPACE_SM);
        bottomPane = componentBuilder.createBottomPanel(ABRConstants.SPACE_L, ABRConstants.SPACE_SM);

        contentPane =
                componentBuilder.createContentPanel(ABRConstants.SPACE_L, ABRConstants.SPACE_XL, ABRConstants.SPACE_SM);

        scanButton = componentBuilder.buildButton(
                "Scan", ABRConstants.SPACE_L, ABRConstants.ICON_SEARCH, ABRConstants.SPACE_M, new Insets(5));
        addWaitButton = componentBuilder.buildButton(
                "Add Wait", ABRConstants.SPACE_L, ABRConstants.ICON_WAIT, ABRConstants.SPACE_M, new Insets(5));
        addCloseActionButton = componentBuilder.buildButton(
                "Add Close Browser",
                ABRConstants.SPACE_L,
                ABRConstants.ICON_CROSS,
                ABRConstants.SPACE_M,
                new Insets(5));
        addScreenButton = componentBuilder.buildButton(
                "Add Screenshot", ABRConstants.SPACE_L, ABRConstants.ICON_SCREEN, ABRConstants.SPACE_M, new Insets(5));
        refreshInputFieldsButton = componentBuilder.buildButton(
                "Input Fields", ABRConstants.SPACE_ZERO, "/refresh.png", ABRConstants.SPACE_M, new Insets(5.0D));
        refreshOutputFieldsButton = componentBuilder.buildButton(
                "Output Fields", ABRConstants.SPACE_ZERO, "/refresh.png", ABRConstants.SPACE_M, new Insets(5.0D));
        refreshOtherFieldsButton = componentBuilder.buildButton(
                "Other Elements", ABRConstants.SPACE_ZERO, "/refresh.png", ABRConstants.SPACE_M, new Insets(5.0D));
        checkBoxAction = new CheckBox("Execute Action\n(RELEASE AFTER USE)");
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
                "Launch Test", ABRConstants.SPACE_ZERO, "/play.png", ABRConstants.SPACE_M, new Insets(5.0D));

        // Create a label to display the countdown
        Label countdownLabel = new Label(String.valueOf(remainingSeconds));
        countdownLabel.setStyle("-fx-font-size: 24px;");

        // Create a stack pane to hold the label
        StackPane stackPane = new StackPane(countdownLabel);
        stackPane.setPadding(new Insets(20));

        // Create a dialog for the alert
        alertToShow = new Alert(Alert.AlertType.INFORMATION);
        alertToShow.setTitle("Countdown Alert");
        alertToShow.setHeaderText(null);
        alertToShow.initModality(Modality.APPLICATION_MODAL);
        // Set the content of the alert
        alertToShow.getDialogPane().setContent(stackPane);
        // Create a single-threaded executor service
        executorService = Executors.newSingleThreadExecutor();
        // Create a timeline to update the countdown
        timeline = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), event -> {
            remainingSeconds--;
            countdownLabel.setText(String.valueOf(remainingSeconds));
            if (remainingSeconds <= 0) {
                timeline.stop(); // Stop the timeline when countdown finishes
                alertToShow.close(); // Close the alert dialog
            }
        }));

        checkActiveHover = new CheckBox("Identify");

        xpathTextField = new TextField();
        xpathTextField.setPromptText("Hovered element XPath will appear here");

        coordinatesTextField = new TextField();
        coordinatesTextField.setPromptText("Hovered element Coordinates will appear here");

        HBox buttonBox = new HBox();
        buttonBox.setSpacing(10);
        buttonBox
                .getChildren()
                .addAll(
                        scanButton,
                        addWaitButton,
                        addCloseActionButton,
                        addScreenButton,
                        configureButton,
                        launchBotJobButton,
                        checkActiveHover,
                        xpathTextField,
                        coordinatesTextField);
        addNodesToPane(topPane, buttonBox);

        //        addNodesToPane(contentPane, refreshInputFieldsButton, refreshOutputFieldsButton,
        // refreshOtherFieldsButton);
        // Set the layout constraints for the buttonscreate
        //        AnchorPane.setTopAnchor(refreshInputFieldsButton, topPane.getBoundsInLocal().getHeight());
        //        AnchorPane.setTopAnchor(refreshOutputFieldsButton, topPane.getBoundsInLocal().getHeight());
        //        AnchorPane.setTopAnchor(refreshOtherFieldsButton, topPane.getBoundsInLocal().getHeight());
        //        AnchorPane.setLeftAnchor(refreshOutputFieldsButton, 300D);
        //        AnchorPane.setLeftAnchor(refreshOtherFieldsButton, 600D);

        //        AnchorPane.setLeftAnchor(refreshInputFieldsButton, 30D);

        VBox verticalBox = new VBox();

        HBox buttonsBox = new HBox();
        //        buttonsBox.setSpacing(300);

        Region space1 = new Region();
        Region space2 = new Region();

        HBox.setMargin(refreshInputFieldsButton, new Insets(0, 0, 0, 100));
        HBox.setMargin(refreshOtherFieldsButton, new Insets(0, 100, 0, 0));

        HBox.setHgrow(space1, Priority.ALWAYS);
        HBox.setHgrow(space2, Priority.ALWAYS);

        // Set Hgrow for each ListView to make them equally distributed
        //        crefreshInputFieldsButton, Priority.ALWAYS);
        //        HBox.setHgrow(refreshOutputFieldsButton, Priority.ALWAYS);
        //        HBox.setHgrow(refreshOtherFieldsButton, Priority.ALWAYS);

        buttonsBox
                .getChildren()
                .addAll(
                        refreshInputFieldsButton,
                        space1,
                        refreshOutputFieldsButton,
                        space2,
                        refreshOtherFieldsButton,
                        checkBoxAction);

        // Set the vertical alignment for checkBoxAction to TOP
        VBox.setMargin(checkBoxAction, new Insets(0, 0, 0, 80)); // Example margin, adjust as needed
        //        verticalBox.getChildren().addAll(buttonsBox, checkBoxAction);

        HBox boxListViews = new HBox();

        // Bind the  height of ListViews to the heigh of the HBox
        scannedElements1.prefHeightProperty().bind(contentPane.heightProperty());
        scannedElements2.prefHeightProperty().bind(contentPane.heightProperty());
        scannedElements3.prefHeightProperty().bind(contentPane.heightProperty());
        //        scannedElements1.setPrefHeight(contentPane.heightProperty().getValue() -
        // bottomPane.heightProperty().getValue());
        //        scannedElements2.setPrefHeight(contentPane.heightProperty().getValue() -
        // bottomPane.heightProperty().getValue());
        //        scannedElements3.setPrefHeight(contentPane.heightProperty().getValue() -
        // bottomPane.heightProperty().getValue());

        boxListViews.setSpacing(5);

        // Set Hgrow for each ListView to make them equally distributed
        HBox.setHgrow(scannedElements1, Priority.ALWAYS);
        HBox.setHgrow(scannedElements2, Priority.ALWAYS);
        HBox.setHgrow(scannedElements3, Priority.ALWAYS);

        boxListViews.getChildren().addAll(scannedElements1, scannedElements2, scannedElements3);

        verticalBox.getChildren().addAll(buttonsBox, boxListViews, bottomPane);
        //        contentPane.getChildren().addAll(scannedElements1, scannedElements2, scannedElements3);
        contentPane.getChildren().addAll(verticalBox);

        scannedElements1.setPrefWidth(LIST_VIEW_WIDTH);
        scannedElements2.setPrefWidth(LIST_VIEW_WIDTH);
        scannedElements3.setPrefWidth(LIST_VIEW_WIDTH);

        //        boxListViews.setAlignment(Pos.BOTTOM_CENTER);
        // Set the layout constraints for the ListViews
        //        AnchorPane.setTopAnchor(boxListViews, 30D);
        //        AnchorPane.setLeftAnchor(boxListViews, 0D);

        //        AnchorPane.setTopAnchor(scannedElements1, 30D);
        //        AnchorPane.setLeftAnchor(scannedElements1, 0D);
        //        AnchorPane.setTopAnchor(scannedElements2, 30D);
        //        AnchorPane.setLeftAnchor(scannedElements2, scannedElements1.getPrefWidth());
        //        AnchorPane.setTopAnchor(scannedElements3, 30D);
        //        AnchorPane.setLeftAnchor(scannedElements3, scannedElements1.getPrefWidth() * 2);
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

            // Set all instructions' executed field to false
            botJob.getBlocks().stream()
                    .flatMap(block -> block.getBlockLoopInstructions().stream())
                    .forEach(instruction -> instruction.setExecuted(false));

            recallJob();
        });
        checkActiveHover.setOnMouseClicked(e -> handleHoverCheckClick());
        scanButton.setOnAction(e -> manageUIScan());
        addWaitButton.setOnAction(e -> addWaitTask(30));
        addCloseActionButton.setOnAction(e -> addCloseBrowserTask());
        addScreenButton.setOnAction(e -> addScreenTask());

        refreshInputFieldsButton.setOnAction(e -> refreshInputBtn());
        refreshOutputFieldsButton.setOnAction(e -> refreshOutputBtn());
        refreshOtherFieldsButton.setOnAction(e -> refreshOtherElemBtn());

        scannedElements1.getItems().addListener(this::addBehaviourToAddedElements);
        scannedElements2.getItems().addListener(this::addBehaviourToAddedElements);
        scannedElements3.getItems().addListener(this::addBehaviourToAddedElements);

        //        manageUIScan();
    }

    private void handleHoverCheckClick() {
        if (checkActiveHover.isSelected()) {
            periodicThread(abrWebDriver.getDriver());
            //            injectJavaScript(abrWebDriver.getDriver());
            //            injectJumpTab(abrWebDriver.getDriver());
        } else {
            revertInjectedChanges(abrWebDriver.getDriver());
        }
        periodicActivated = checkActiveHover.isSelected();
    }

    private void manageUIScan() {
        ABRLogger.getInstance(ABRScannedElementPane.class).info("General scan triggered");
        webElementObservableList1.clear();
        webElementObservableList2.clear();
        webElementObservableList3.clear();
        manageUIScanPriorities();
        manageUIScanInputs();
        manageUIScanClickable();
        //        manageUIScanOutputs();
    }

    private void manageUIScanInputs() {
        List<WebElementTagNameEnum> inputTags = WebElementTagNameEnum.insertableTags();
        for (WebElementTagNameEnum tag : inputTags) {
            scanABRElementsAsync(
                    null, By.tagName(tag.getValue()), ABRWebElement::isNotClickable, webElementObservableList1);
        }
    }

    private void manageUIScanClickable() {
        List<WebElementTagNameEnum> clickableTags = WebElementTagNameEnum.clickableTags();
        for (WebElementTagNameEnum tag : clickableTags) {
            scanABRElementsAsync(
                    null, By.tagName(tag.getValue()), ABRWebElement::isClickable, webElementObservableList2);
        }
    }

    private void manageUIScanPriorities() {
        String extRef = ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.WEBDRIVER_EXT_REFERENCE);
        List<WebElement> webElements = managePrioritiesCriteria();
        //        manageUIScanPrioritiesJSoup();
        //        scanABRElementsAsync(By.cssSelector("*[" + extRef + "]"), webElementObservableList3);
        try {
            if (webElements != null && webElements.size() > 0) {
                scanABRElementsAsync(webElements, webElementObservableList3);
            }
        } catch (Exception e) {
            System.out.println("Error " + e.getMessage());
        }
    }

    private void manageUIScanOutputs() {
        String extRef = ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.WEBDRIVER_EXT_REFERENCE);
        scanABRElementsAsync(By.cssSelector("*[" + extRef + "]"), webElementObservableList2);
    }

    private void scanABRElementsAsync(By criteria, ObservableList<ABRWebElement> listToAddElements) {
        scanABRElementsAsync(null, criteria, null, listToAddElements);
    }

    private void scanABRElementsAsync(List<WebElement> preElements, ObservableList<ABRWebElement> listToAddElements) {
        scanABRElementsAsync(preElements, null, null, listToAddElements);
    }

    private void scanABRElementsAsync(
            List<WebElement> preElements,
            By criteria,
            Predicate<ABRWebElement> filterCondition,
            ObservableList<ABRWebElement> listToAddElements) {

        ProgressBar progressBar = new ProgressBar();
        addNodesToPane(bottomPane, progressBar);
        Task<Void> workingTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try {
                    ABRLogger.getInstance(ABRScannedElementPane.class)
                            .fine("Starting scan of elements for criteria: " + criteria);

                    List<WebElement> scannedElementList = new ArrayList<>();
                    if (preElements != null && preElements.size() > 0) {
                        scannedElementList.addAll(preElements);
                    } else if (criteria != null) {
                        scannedElementList = abrWebDriver.scan(criteria);
                    }

                    if (scannedElementList != null && scannedElementList.size() > 0) {
                        ABRLogger.getInstance(ABRScannedElementPane.class)
                                .finer("list of scanned elements has " + scannedElementList.size()
                                        + " elements for Search Criteria " + criteria);

                        ABRLogger.getInstance(ABRScannedElementPane.class)
                                .fine("Reduces to the Limit of ABRWebElements : " + reduceSearchCriteria);
                        List<WebElement> scannedElementListReduced = scannedElementList.size() > reduceSearchCriteria
                                ? new ArrayList<>(scannedElementList.subList(0, reduceSearchCriteria))
                                : scannedElementList;
                        List<ABRWebElement> listABRElements = null;
                        try {
                            listABRElements = scannedElementListReduced.stream()
                                    .filter(element -> element != null) // Filter out null elements
                                    .map(element -> new ABRWebElement(element, botJob.getId()))
                                    .collect(Collectors.toList());

                        } finally {

                        }

                        if (preElements == null) {

                            // Saved REferences From Priorities
                            // Update the savedReferences field for each element in the stream
                            // Iterate over the list to update the savedReferences field
                            //                        for (ABRWebElement element : listABRElements) {

                            if (listABRElements != null && listABRElements.size() > 0) {
                                //
                                // buildPriorityReferences(listABRElements);saveReferencesToFile(
                                ////
                                // "C:\\Projects\\Martini\\abr-web-selenium\\savedRef.txt", listABRElements);
                                //
                            }
                            // Update the savedReferences field
                            //                            element.getSavedReferences().put("key", "value");

                            //                        }
                        }

                        //                    if (filterCondition != null) {
                        //                        ABRLogger.getInstance(ABRScannedElementPane.class).fine("Starting
                        // filtering elements");
                        //                        stream = stream.filter(filterCondition);
                        //                        ABRLogger.getInstance(ABRScannedElementPane.class).fine("Filtering
                        // elements ended");
                        //                    }
                        if (listABRElements != null) {

                            for (ABRWebElement element : listABRElements) {
                                Platform.runLater(() -> {
                                    listToAddElements.add(element);
                                    ABRLogger.getInstance(ABRScannedElementPane.class)
                                            .finer("add request to JavaFX thread ended for ABRWebElement with xPath: "
                                                    + element.getXPath());
                                });
                                Thread.sleep(100);
                            }
                        }
                        Platform.runLater(() -> {
                            removeNodesFromPane(bottomPane, progressBar);
                        });
                    }

                } catch (Exception e) {
                    ABRLogger.getInstance(Thread.class)
                            .severe("an exception has occurred in the thread for criteria " + criteria + ": Message: "
                                    + e.getMessage() + " Cause: " + e.getCause());
                }
                return null;
            }
        };
        progressBar.progressProperty().bind(workingTask.workDoneProperty());
        ABRLogger.getInstance(ABRScannedElementPane.class).fine("starting scanning thread for " + criteria);
        new Thread(workingTask).start();
    }

    private void addWaitTask(Integer secondsToWait) {
        Alert alert = new Alert(
                Alert.AlertType.CONFIRMATION,
                "Are you sure you want to add a wait of 30 seconds to the botjob?",
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
                    closeInstruction.setInstructionOrderNumber(instructionList.size());
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
            Task<Void> handleEvent = new Task<Void>() {
                @Override
                protected Void call() throws Exception {
                    //                    WebElement element =
                    // abrWebDriver.getDriver().findElement(By.xpath(abrWebElement.getXPath()));
                    //                    if (element != null){
                    //                        abrWebDriver.highlightElement(abrWebElement.getElement());
                    //                    }
                    List<WebElement> elementList = abrWebDriver.scan(By.xpath(abrWebElement.getXPath()));
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

                    List<WebElement> elementList = abrWebDriver.scan(By.xpath(abrWebElement.getXPath()));
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
                            "Are you sure you want to add the instruction selected to the bot job?",
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

                                try {
                                    abrWebElement.setxPath(
                                            getXPath(abrWebDriver.getDriver(), abrWebElement.getElement()));
                                    abrWebDriver.dehighlightElement(abrWebElement.getElement());
                                } catch (Exception e) {
                                    System.out.println("Cannot find the XPath for this Element");
                                }

                                ABRLogger.getInstance(Task.class)
                                        .fine("THREAD: starting element scan by xpath " + abrWebElement.getXPath());
                                //                            List<WebElement> elementList =
                                // abrWebDriver.scan(By.xpath(abrWebElement.getXPath()));
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
                                ABRLogger.getInstance(Task.class).fine("THREAD: creating instruction from webelement");
                                BlockLoopInstructionDTO instruction =
                                        abrWebElement.buildBlockLoopInstruction(list.size());
                                ABRLogger.getInstance(Task.class).fine("THREAD: instruction from webelement created");
                                ABRLogger.getInstance(Task.class)
                                        .fine("THREAD: setting block " + block.getId() + " on instruction");
                                instruction.setBlock(block);
                                ABRLogger.getInstance(Task.class).fine("THREAD: block " + block.getId() + " set");
                                ABRLogger.getInstance(Task.class).fine("THREAD: adding instruction to database");
                                ABRSharedResources.getInstance()
                                        .addEntity(instruction, BlockLoopInstructionDTO.class, () -> {
                                            ABRLogger.getInstance(Task.class)
                                                    .fine("THREAD: instruction added successfully to database");
                                            ABRLogger.getInstance(Task.class)
                                                    .fine("THREAD: setting instuctionId " + instruction.getId());
                                            abrWebElement.setInstructionId(instruction.getId());
                                            ABRLogger.getInstance(Task.class)
                                                    .fine("THREAD: instuctionId set " + instruction.getId());
                                            ABRLogger.getInstance(Task.class)
                                                    .fine("THREAD: creating queue for references");
                                            LinkedBlockingQueue<InstructionReferenceDTO> queue =
                                                    new LinkedBlockingQueue<>();
                                            for (String key : abrWebElement
                                                    .getSavedReferences()
                                                    .keySet()) {
                                                ABRLogger.getInstance(Task.class)
                                                        .finer("THREAD: creating reference " + key);
                                                InstructionReferenceDTO reference = new InstructionReferenceDTO();
                                                reference.setReferenceType(key);
                                                ABRLogger.getInstance(Task.class)
                                                        .finer("THREAD: setting value of reference: "
                                                                + abrWebElement
                                                                        .getSavedReferences()
                                                                        .get(key));
                                                reference.setValue(abrWebElement
                                                        .getSavedReferences()
                                                        .get(key));
                                                ABRLogger.getInstance(Task.class)
                                                        .fine("THREAD: reference value set");
                                                ABRLogger.getInstance(Task.class)
                                                        .finer("THREAD: setting reference instruction: " + instruction);
                                                reference.setBlockLoopInstructionDTO(instruction);
                                                ABRLogger.getInstance(Task.class)
                                                        .fine("THREAD: reference instruction set");
                                                ABRLogger.getInstance(Task.class)
                                                        .fine("THREAD: adding reference to queue");
                                                queue.add(reference);
                                                ABRLogger.getInstance(Task.class)
                                                        .fine("THREAD: reference added to queue");
                                            }
                                            ABRLogger.getInstance(Task.class)
                                                    .fine("THREAD: adding " + queue.size() + "queue elements");
                                            ABRSharedResources.getInstance()
                                                    .addAllEntity(queue, InstructionReferenceDTO.class, () -> {
                                                        ABRLogger.getInstance(Task.class)
                                                                .fine(
                                                                        "THREAD: queue elements added successfully. Showing alert on JAVAFX thread");
                                                        Platform.runLater(() -> {
                                                            ABRLogger.getInstance(Task.class)
                                                                    .fine("JAVAFX: showing alert");
                                                            new ABRAlertScene(
                                                                    Alert.AlertType.INFORMATION,
                                                                    "Instruction Added",
                                                                    "Instruction " + instruction.getName()
                                                                            + " has been added successfully",
                                                                    ButtonType.OK);
                                                            ABRLogger.getInstance(Task.class)
                                                                    .fine("JAVAFX: alert shown");
                                                        });
                                                    });
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
                                searchingElems = abrWebDriver.scan(By.xpath(name));
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
                                    searchingElems = abrWebDriver.scan(By.tagName(name));
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
                                    searchingElems = abrWebDriver.scan(By.tagName(name));
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
                                searchingElems = abrWebDriver.scan(By.cssSelector("button[" + name + "]"));
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
                                searchingElems = abrWebDriver.scan(By.cssSelector("input[" + name + "]"));
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
                        //                            webElements = abrWebDriver.scan(By.cssSelector("[" +
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
                        //                            webElements = abrWebDriver.scan(By.cssSelector("[" +
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
        //        String jsCode = "(function() {" +
        //                "    var tooltip = document.createElement('div');" +
        //                "    tooltip.style.position = 'absolute';" +
        //                "    tooltip.style.backgroundColor = 'black';" +
        //                "    tooltip.style.color = 'white';" +
        //                "    tooltip.style.padding = '5px';" +
        //                "    tooltip.style.borderRadius = '3px';" +
        //                "    tooltip.style.display = 'none';" +
        //                "    tooltip.style.zIndex = '1000';" +
        //                "    document.body.appendChild(tooltip);" +
        //                "    function getXPath(element) {" +
        //                "        if (element.id !== '') {" +
        //                "            return 'id(\"' + element.id + '\")';" +
        //                "        }" +
        //                "        if (element === document.body) {" +
        //                "            return element.tagName;" +
        //                "        }" +
        //                "        var ix = 0;" +
        //                "        var siblings = element.parentNode.childNodes;" +
        //                "        for (var i = 0; i < siblings.length; i++) {" +
        //                "            var sibling = siblings[i];" +
        //                "            if (sibling === element) {" +
        //                "                return getXPath(element.parentNode) + '/' + element.tagName + '[' + (ix + 1)
        // + ']';" +
        //                "            }" +
        //                "            if (sibling.nodeType === 1 && sibling.tagName === element.tagName) {" +
        //                "                ix++;" +
        //                "            }" +
        //                "        }" +
        //                "        return '';" +
        //                "    }" +
        //                "    function showTooltip(event) {" +
        //                "        var tagName = event.target.tagName.toLowerCase();" +
        //                "        var xpath = getXPath(event.target);" +
        //                "        tooltip.textContent = tagName;" +
        //                "        tooltip.style.left = event.pageX + 'px';" +
        //                "        tooltip.style.top = (event.pageY + 15) + 'px';" +
        //                "        tooltip.style.display = 'block';" +
        //                "        window.currentXPath = xpath;" +
        //                "    }" +
        //                "    function hideTooltip() {" +
        //                "        tooltip.style.display = 'none';" +
        //                "    }" +
        //                "    document.addEventListener('mouseover', showTooltip);" +
        //                "    document.addEventListener('mouseout', hideTooltip);" +
        //                "})();";

        // JavaScript code to inject
        String jsCode = "(function() {" + "    var tooltip = document.createElement('div');"
                + "    tooltip.id = 'Martini-Is-Awesome';"
                + "    tooltip.style.position = 'absolute';"
                + "    tooltip.style.backgroundColor = 'black';"
                + "    tooltip.style.color = 'white';"
                + "    tooltip.style.padding = '5px';"
                + "    tooltip.style.borderRadius = '3px';"
                + "    tooltip.style.display = 'none';"
                + "    tooltip.style.zIndex = '1000';"
                + "    document.body.appendChild(tooltip);"
                + "    function getXPath(element) {"
                + "        if (element.id !== '') {"
                + "            return 'id(\"' + element.id + '\")';"
                + "        }"
                + "        if (element === document.body) {"
                + "            return element.tagName;"
                + "        }"
                + "        var ix = 0;"
                + "        var siblings = element.parentNode.childNodes;"
                + "        for (var i = 0; i < siblings.length; i++) {"
                + "            var sibling = siblings[i];"
                + "            if (sibling === element) {"
                + "                return getXPath(element.parentNode) + '/' + element.tagName + '[' + (ix + 1) + ']';"
                + "            }"
                + "            if (sibling.nodeType === 1 && sibling.tagName === element.tagName) {"
                + "                ix++;"
                + "            }"
                + "        }"
                + "        return '';"
                + "    }"
                + "    function showTooltip(event) {"
                + "        var tagName = event.target.tagName.toLowerCase();"
                + "        var coords = event.target.getBoundingClientRect();"
                + "        tooltip.textContent = tagName + ' Coordinates: (' + coords.left + ', ' + coords.top + ')';"
                + "        tooltip.style.left = event.pageX + 'px';"
                + "        tooltip.style.top = (event.pageY + 15) + 'px';"
                + "        tooltip.style.display = 'block';"
                + "    }"
                + "    function hideTooltip() {"
                + "        tooltip.style.display = 'none';"
                + "    }"
                + "    function handleClick(event) {"
                + "        var xpath = getXPath(event.target);"
                + "        var coords = event.target.getBoundingClientRect();"
                + "        window.currentXPath = coords;"
                + "        window.coordinates = xpath;"
                + "    }"
                + "    document.addEventListener('mouseover', showTooltip);"
                + "    document.addEventListener('mouseout', hideTooltip);"
                + "    document.addEventListener('click', handleClick);"
                + "})();";

        // Inject the JavaScript into the webpage
        JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;
        jsExecutor.executeScript(jsCode);

        // Start a thread to periodically check the XPath value and update the TextField
        new Thread(() -> {
                    while (periodicActivated) {
                        String coordinates = (String) jsExecutor.executeScript("return window.coordinates;");
                        Platform.runLater(() -> coordinatesTextField.setText(coordinates));
                        try {
                            Thread.sleep(500); // Check every 500 milliseconds
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                })
                .start();

        // Start a thread to periodically check the XPath value and update the TextField
        new Thread(() -> {
                    while (periodicActivated) {
                        String currentXPath = (String) jsExecutor.executeScript("return window.currentXPath;");
                        Platform.runLater(() -> xpathTextField.setText(currentXPath));
                        try {
                            Thread.sleep(500); // Check every 500 milliseconds
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                })
                .start();
    }

    private static void revertInjectedChanges(WebDriver driver) {
        JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;

        // Remove the injected element
        jsExecutor.executeScript(
                "let elem = document.getElementById('Martini-Is-Awesome'); if (elem) { elem.remove(); }");

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
        System.out.println("Number of input elements: " + inputElements.size());

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
        String selectSQL = " SELECT ID, Name, Url, priority, COUNT(bot.ID) Jobs, searchConfig, username, password "
                + " FROM home_banking bank "
                + " left join bot_job bot on bot.home_banking_id = bank.id "
                + " WHERE bank.id = " + bankId
                + " group by bank.ID, bank.Name, bank.Url, bank.priority, bank.searchConfig, bank.username, bank.password ";
        try (Statement stmt = getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(selectSQL)) {
            while (rs.next()) {
                String id = rs.getString("ID");
                String jobs = rs.getString("Jobs");
                String name = rs.getString("Name");
                String url = rs.getString("Url");
                String priority = rs.getString("Priority");
                String searchConfig = rs.getString("searchConfig");
                String username = rs.getString("username");
                String password = rs.getString("password");
                databaseUserDto = new DatabaseUserDTO(id, jobs, name, url, priority, searchConfig, username, password);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        //        jobUserList.clear();
        //        loadBotJobData();
    }

    private Connection getConnection() {
        if (conn == null) {
            String dbPath = ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.FOLDER_PATH_DB);
            String dbUrl = CONNECTION_TYPE + dbPath + ABRConstants.FILE_NAME_DB + CONNECTION_PARAMETERS;
            try {
                conn = DriverManager.getConnection(dbUrl);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return conn;
    }

    private void recallJob() {
        executeJob();
        // Review if Has Not Executed Instructions
        boolean hasUnexecutedInstructions = botJob.getBlocks().stream()
                .flatMap(block -> block.getBlockLoopInstructions().stream())
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

    private void executeJob() {
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

        try {
            baseLogFile = new File(ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.FOLDER_PATH_LOG)
                    + ABRConstants.FILE_NAME_ENGINE_LOG);
        } catch (Exception e) {
            e.printStackTrace();
        }

        ABRPropertyManager managerProps = ABRPropertyManager.getInstance();
        String excelPath = ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.FOLDER_PATH_EXCEL);
        excelPath = excelPath + "\\" + this.botJob.getName() + ".xlsx";
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
            extractedData = excelReader.extractData(excelPath, botJob);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (extractedData.getErrorMessage() != null) {
            //				showAlert("Excel Data File", "Warning: Excel File exist" , "Fields in the excel not matching the
            // botjob requirements");
            System.out.println("Fields in the excel not matching the botjob requirements");
        }

        Set<String> blockClickables = botJob.getBlocks().stream()
                .map(BlockDTO::getBlockLoopInstructions)
                .reduce((identity, accumulated) -> {
                    accumulated.addAll(identity);
                    return accumulated;
                })
                .get()
                .stream()
                .map(BlockLoopInstructionDTO::getActions)
                .filter(action -> action.contains(Constants.CLICK))
                .collect(Collectors.toSet());

        //        String browser = ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.BROWSER);
        //            WebPage webPage = new WebPage(browser, homeBankingDTO.getUrl());

        String baseLogString = botJob.getName() + Constants.FIELDS_SEPARATOR + labelsValue.getProperty(Labels.START);
        printBaseLog(baseLogFile, generateTimestamp(), baseLogString);

        boolean success = true;
        long botJobStartTime = System.nanoTime();
        long totalExecutionTime = 0;
        String lastInstructionExecuted = "No istruction executed yet";
        String resultAcions = "";
        Map<String, String> dataExcel = null;

        if (extractedData.getNumberOfDataRows() > 0) {
            for (int i = 0; success && i < extractedData.getNumberOfDataRows(); i++) {
                List<BlockDTO> blockList = botJob.getBlocks();
                for (int j = 0; success && j < blockList.size(); j++) {
                    for (BlockLoopInstructionDTO currentInstruction :
                            blockList.get(j).getBlockLoopInstructions()) {
                        if (currentInstruction.getExecuted() == null || !currentInstruction.getExecuted()) {

                            long currentInstructionStartTime = System.nanoTime();
                            File logFileForSingleExcel = excelReader.createLogFile(excelPath);
                            dataExcel = extractedData.getRowFieldValues(i);
                            try {
                                lastInstructionExecuted = currentInstruction.getName()
                                        + Constants.BLANK_STRING
                                        + currentInstruction.getPath();
                                resultAcions = performActions(dataExcel, currentInstruction);
                                long currentInstructionEndTime = System.nanoTime();
                                totalExecutionTime += currentInstructionEndTime - currentInstructionStartTime;
                                System.out.println("SUCCESSFUL INSTRUCTION on element: " + resultAcions + " --> "
                                        + lastInstructionExecuted);
                                currentInstruction.setExecuted(true);
                                success = true;
                            } catch (Throwable t) {
                                success = false;
                                currentInstruction.setExecuted(false);
                                if (currentInstruction.isOptional()) {
                                    long currentInstructionEndTime = System.nanoTime();
                                    long duration = currentInstructionEndTime - botJobStartTime;
                                    LocalTime time = LocalTime.now();
                                    System.out.println("FAILED OPTIONAL INSTRUCTION on element: " + resultAcions
                                            + " --> "
                                            + lastInstructionExecuted + "- Duration: "
                                            + LocalTime.ofNanoOfDay(duration).format(FORMAT_TIME));

                                } else {
                                    long currentInstructionEndTime = System.nanoTime();
                                    long duration = currentInstructionEndTime - botJobStartTime;
                                    LocalTime time = LocalTime.now();
                                    System.out.println("FAILED MANDATORY INSTRUCTION on element: " + resultAcions
                                            + " --> "
                                            + lastInstructionExecuted + "- Duration: "
                                            + LocalTime.ofNanoOfDay(duration).format(FORMAT_TIME));
                                }
                                //                            throw new RuntimeException(t);
                            }
                            printLog(generateTimestamp(), logFileForSingleExcel, resultAcions, success);
                        }
                    }
                }
            }
        } else { //  if dataExel is NULL
            List<BlockDTO> blockList = botJob.getBlocks();

            // Creating Dynamic Data if Default is Null
            Map<String, String> dataDynamic = new HashMap<>();
            for (BlockDTO blockDTO : blockList) {
                for (BlockLoopInstructionDTO currentInstruction : blockDTO.getBlockLoopInstructions()) {
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
            for (int j = 0; success && j < blockList.size(); j++) {
                for (BlockLoopInstructionDTO currentInstruction :
                        blockList.get(j).getBlockLoopInstructions()) {
                    long currentInstructionStartTime = System.nanoTime();
                    File logFileForSingleExcel = excelReader.createLogFile(excelPath);
                    try {
                        lastInstructionExecuted =
                                currentInstruction.getName() + Constants.BLANK_STRING + currentInstruction.getPath();
                        resultAcions = performActions(dataDynamic, currentInstruction);
                        long currentInstructionEndTime = System.nanoTime();
                        totalExecutionTime += currentInstructionEndTime - currentInstructionStartTime;
                        System.out.println("SUCCESSFUL INSTRUCTION on element: " + resultAcions + " --> "
                                + lastInstructionExecuted);
                        currentInstruction.setExecuted(true);
                        success = true;
                    } catch (Throwable t) {
                        success = false;
                        currentInstruction.setExecuted(false);
                        if (currentInstruction.isOptional()) {
                            long currentInstructionEndTime = System.nanoTime();
                            long duration = currentInstructionEndTime - botJobStartTime;
                            LocalTime time = LocalTime.now();
                            System.out.println("FAILED OPTIONAL INSTRUCTION on element: " + resultAcions + " --> "
                                    + lastInstructionExecuted + "- Duration: "
                                    + LocalTime.ofNanoOfDay(duration).format(FORMAT_TIME));
                        } else {
                            long currentInstructionEndTime = System.nanoTime();
                            long duration = currentInstructionEndTime - botJobStartTime;
                            LocalTime time = LocalTime.now();
                            System.out.println("FAILED MANDATORY INSTRUCTION on element: " + resultAcions + " --> "
                                    + lastInstructionExecuted + "- Duration: "
                                    + LocalTime.ofNanoOfDay(duration).format(FORMAT_TIME));
                        }
                        //                        throw new RuntimeException(t);
                    }
                    printLog(generateTimestamp(), logFileForSingleExcel, resultAcions, success);
                }
            }
        }

        if (totalExecutionTime == 0) {}

        // PRINT END BASE LOG//
        if (success) {
            baseLogString = botJob.getName()
                    + Constants.FIELDS_SEPARATOR
                    + labelsValue.getProperty(Labels.END)
                    + Constants.FIELDS_SEPARATOR
                    + labelsValue.getProperty(Labels.OK);
        } else {
            baseLogString = botJob.getName()
                    + Constants.FIELDS_SEPARATOR
                    + labelsValue.getProperty(Labels.END)
                    + Constants.FIELDS_SEPARATOR
                    + labelsValue.getProperty(Labels.KO)
                    + lastInstructionExecuted;
        }
        printBaseLog(baseLogFile, generateTimestamp(), baseLogString);
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

    public String performActions(Map<String, String> data, BlockLoopInstructionDTO instruction) throws Exception {
        WebElement instructionElement = null;
        String[] actions = instruction.getActions().split(Constants.ACTIONS_AND_PATHS_SPLITTER);

        if (!StringUtils.isBlank(instruction.getPath())) {
            instructionElement = locateElement(instruction, data);
        }
        String result = "";
        if (instructionElement != null) {

            for (String action : actions) {
                switch (String.valueOf(action.charAt(0))) {
                    case Constants.VISUALIZE:
                        scrollToElement(instructionElement);
                        break;
                    case Constants.CLICK:
                        result = "clickElement -->" + clickElement(instructionElement);
                        break;
                    case Constants.INSERT:
                        result = insertInElement(instructionElement, data, action, instruction);
                        break;
                    case Constants.LIST_OPERATION:
                        listOperation(instruction, data);
                        break;
                    case Constants.HOLD:
                        executeAlert(instruction);
                        onHoldForSeconds(instruction);
                        break;
                    case Constants.REFRESH:
                        refreshPage();
                        break;
                    case Constants.QUIT:
                        quit(0);
                        break;
                    case Constants.EXTRACT:
                        insertValueFieldNameInExcel(instructionElement, instruction, action);
                        break;
                    case Constants.SCREEN:
                        break;
                }
                onHoldForSeconds(null);
            }
        } else {
            executeActionsAtInstructionCoordinates(instruction, data);
            onHoldForSeconds(null);
        }
        return result;
    }

    private void executeAlert(BlockLoopInstructionDTO instruction) {
        // Execute the countdown in a separate thread
        if (instruction != null) {
            Integer instructionSeconds = instruction.getOnHoldSeconds();
            executorService.execute(() -> {
                timeline.setCycleCount(instructionSeconds); // Run for SECONDS seconds
                timeline.play(); // Start the timeline

                // Show the alert on the JavaFX Application Thread
                javafx.application.Platform.runLater(() -> alertToShow.showAndWait());
            });
        }
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    private WebElement locateElement(BlockLoopInstructionDTO instruction, Map<String, String> data) throws Exception {

        String instructionPath = instruction.getPath();
        String tagName = null;
        try {
            tagName = removeTrailingSlash(instructionPath);
            tagName = extractTagName(instructionPath);
        } catch (Exception e) {
            System.out.println("Error trying to get tagName" + e.getMessage());
        }
        List<InstructionReferenceDTO> instructionReferenceList = instruction.getInstructionReferenceDTOList();

        waitPage();

        // If Not Loaded get if the JobId Changed
        if (abrPriorities.getJobId() == null) {
            abrPriorities.setJobId(instruction.getBlock().getBotJob().getId());
            if (instruction.getBlock().getBotJob().getPriority() != null) {
                abrPriorities.loadPrioritiesFromString(
                        instruction.getBlock().getBotJob().getPriority());
            } else {
                abrPriorities.loadPriorities();
            }
        } else if (abrPriorities.getJobId()
                != instruction.getBlock().getBotJob().getId()) {
            abrPriorities.setJobId(instruction.getBlock().getBotJob().getId());
            if (instruction.getBlock().getBotJob().getPriority() != null) {
                abrPriorities.loadPrioritiesFromString(
                        instruction.getBlock().getBotJob().getPriority());
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
                Optional<InstructionReferenceDTO> instructionReference = instructionReferenceList.stream()
                        .filter(reference ->
                                priority.getPriorityType().toString().equalsIgnoreCase(reference.getReferenceType()))
                        .findFirst();

                // Print or process the first matching instruction reference
                instructionReference.ifPresent((f) -> System.out.println(String.format(
                        "Search for %s   Type:  %s   Value: %s",
                        priority.getName(), f.getReferenceType(), f.getValue())));

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
                                        System.out.println("Could not fin the element");
                                    }
                                } else if (instruction.getActionCustomMaxWaitSec() != null) {
                                    try {

                                        new WebDriverWait(
                                                        abrWebDriver.getDriver(),
                                                        Duration.ofSeconds(instruction.getActionCustomMaxWaitSec()))
                                                .until(ExpectedConditions.presenceOfElementLocated(criteria));
                                    } catch (Exception e) {
                                        System.out.println("Could not fin the element");
                                    }
                                } else {
                                    try {

                                        waitForAction.until(ExpectedConditions.visibilityOfElementLocated(criteria));
                                    } catch (Exception e) {
                                        System.out.println("Could not fin the element");
                                    }
                                }
                                int k = 0;
                                //                            MAYBE THIS SHOUL BE NOT NECESSARY  USE UNIQUE ID   OR
                                // SESSION  SAVED TO GET THE SAME XPATHORELEMENT
                                while (elementFound == null && k < foundElementList.size()) {
                                    String xpath = ABRWebUtil.extractXPath(
                                            foundElementList.get(k).toString());
                                    Optional<InstructionReferenceDTO> xpathReference = instructionReferenceList.stream()
                                            .filter(ref ->
                                                    ref.getReferenceType().equals(PriorityTypeEnum.xpath.name()))
                                            .findFirst();
                                    if (xpathReference.isPresent()
                                            && xpath.equals(xpathReference.get().getValue())) {
                                        elementFound = foundElementList.get(k);
                                        break;
                                    }
                                    k++;
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

    private void insertValueFieldNameInExcel(WebElement element, BlockLoopInstructionDTO instruction, String action) {
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

        BotJobDTO botJob = instruction.getBlock().getBotJob();
        new ExcelWriter(botJob).withPurpose("excel").insertValueFieldName(fieldName, innerHTMLValue);
    }

    private void listOperation(BlockLoopInstructionDTO instructionDTO, Map<String, String> data) {

        /*
        TODO: Da rivedere, attualmente non del tutto funzionante
        Complex instruction string interpretation:
        [       0       ||       1      ||       2         ||    3    ||        4       ||  5   ||            6                ]
        [backward_button||forward_button||list_elements_tag||condition||expected_results||action||sub_element_on_execute_action]
        */
        List<ComplexInstructionDTO> complexInstructionDTOS = instructionDTO.getComplexInstrucions();
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

    private String insertInElement(
            WebElement element,
            Map<String, String> data,
            String singleInstruction,
            BlockLoopInstructionDTO instructionDTO)
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

    private synchronized void onHoldForSeconds(BlockLoopInstructionDTO instruction) throws Exception {
        if (instruction != null) {
            Integer instructionSeconds = instruction.getOnHoldSeconds();
            if (instructionSeconds != null && instructionSeconds > 0) {
                wait(fromSecondsToMilliseconds(TimeUnit.SECONDS, instructionSeconds));
            } else {
                String stopSeconds =
                        ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.DEFAULT_INSTRUCTION_STOP_SECONDS);
                wait(fromSecondsToMilliseconds(TimeUnit.SECONDS, Integer.parseInt(stopSeconds)));
            }
        } else {
            wait(400);
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
        if (status > 0) {
            System.exit(status);
        }
    }

    private void executeActionsAtInstructionCoordinates(BlockLoopInstructionDTO instruction, Map<String, String> data)
            throws Exception {

        List<com.allinweb.ch.util.Priority> priorityList = ABRPriorities.getAllPriorityList();
        Optional<com.allinweb.ch.util.Priority> priority = priorityList.stream()
                .filter(p -> p.getPriorityType() == PriorityTypeEnum.coordinates)
                .findFirst();
        if (priority.isPresent()) {
            List<InstructionReferenceDTO> instructionReferenceList = instruction.getInstructionReferenceDTOList();
            Optional<InstructionReferenceDTO> reference = instructionReferenceList.stream()
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

    private void typeCharacters(BlockLoopInstructionDTO instruction, String action, Map<String, String> data) {
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
}

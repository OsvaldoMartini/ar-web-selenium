package com.allinweb.ch.component.pane;

import com.allinweb.ch.builder.WebElementTagNameEnum;
import com.allinweb.ch.component.listCell.ABRCellFactory;
import com.allinweb.ch.component.listCell.ABRWebElementListCell;
import com.allinweb.ch.component.pane.base.ABRPane;
import com.allinweb.ch.component.scene.ABRAlertScene;
import com.allinweb.ch.control.ABRComponentBuilder;
import com.allinweb.ch.core.ABRSharedResources;
import com.allinweb.ch.driver.ABRWebDriver;
import com.allinweb.ch.driver.ABRWebElement;
import com.allinweb.ch.persistence.*;
import com.allinweb.ch.util.*;
import com.google.common.base.Strings;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.layout.Priority;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

public class ABRScannedElementPane extends ABRPane {

    private static final Double LIST_VIEW_WIDTH = 350D;

    private final ABRComponentBuilder componentBuilder = new ABRComponentBuilder();

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
    private Button refreshInputFieldsButton;
    private Button refreshOutputFieldsButton;
    private Button refreshOtherFieldsButton;
    private String priority;

    // Very important sequence on initiation
    private static ABRPriorities abrPriorities;
    private static Map<String, String> savedReferences;
    // Static block to initialize
    static {
        abrPriorities = ABRPriorities.getInstance();
        savedReferences = new HashMap<>();
    }

    public ABRScannedElementPane(String priority, BotJobDTO botJob, BlockDTO block, ABRWebDriver abrWebDriver) {
        super();
        if ((botJob != null && abrPriorities.getJobId() == null) || (abrPriorities.getJobId() != botJob.getId())) {
            abrPriorities.setJobId(botJob.getId());
            if (botJob.getHomeBanking().getPriority() != null) {
                abrPriorities.loadPrioritiesFromString(botJob.getHomeBanking().getPriority());
            } else {
                abrPriorities.loadPriorities();
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

        addNodesToPane(topPane, scanButton, addWaitButton, addCloseActionButton, addScreenButton);

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
                .addAll(refreshInputFieldsButton, space1, refreshOutputFieldsButton, space2, refreshOtherFieldsButton);

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

        manageUIScan();
    }

    private void manageUIScan() {
        ABRLogger.getInstance(ABRScannedElementPane.class).info("General scan triggered");
        webElementObservableList1.clear();
        webElementObservableList2.clear();
        webElementObservableList3.clear();
        managePrioritiesScan();
        manageUIScanInputs();
        manageUIScanClickable();
        manageUIScanOutputs();
    }

    private void manageUIScanInputs() {
        List<WebElementTagNameEnum> inputTags = WebElementTagNameEnum.insertableTags();
        for (WebElementTagNameEnum tag : inputTags) {
            scanABRElementsAsync(By.tagName(tag.getValue()), ABRWebElement::isNotClickable, webElementObservableList1);
        }
    }

    private void manageUIScanClickable() {
        List<WebElementTagNameEnum> clickableTags = WebElementTagNameEnum.clickableTags();
        for (WebElementTagNameEnum tag : clickableTags) {
            scanABRElementsAsync(By.tagName(tag.getValue()), ABRWebElement::isClickable, webElementObservableList2);
        }
    }

    private void managePrioritiesScan() {
        String extRef = ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.WEBDRIVER_EXT_REFERENCE);
        List<WebElement> webElements = managePrioritiesCriteria();
        managePrioritiesScanJSoup();
        scanABRElementsAsync(By.cssSelector("*[" + extRef + "]"), webElementObservableList3);
    }

    private void manageUIScanOutputs() {
        String extRef = ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.WEBDRIVER_EXT_REFERENCE);
        scanABRElementsAsync(By.cssSelector("*[" + extRef + "]"), webElementObservableList3);
    }

    private void scanABRElementsAsync(By criteria, ObservableList<ABRWebElement> listToAddElements) {
        scanABRElementsAsync(criteria, null, listToAddElements);
    }

    private void scanABRElementsAsync(
            By criteria, Predicate<ABRWebElement> filterCondition, ObservableList<ABRWebElement> listToAddElements) {
        ABRLogger.getInstance(ABRScannedElementPane.class)
                .fine("Going to execute the scan asynchronously for " + criteria.toString());
        ProgressBar progressBar = new ProgressBar();
        addNodesToPane(bottomPane, progressBar);
        Task<Void> workingTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                ABRLogger.getInstance(ABRScannedElementPane.class)
                        .fine("Scan thread executed for criteria: " + criteria);
                try {
                    ABRLogger.getInstance(ABRScannedElementPane.class)
                            .fine("Starting scan of elements for criteria: " + criteria);
                    List<WebElement> scannedElementList = abrWebDriver.scan(criteria);
                    ABRLogger.getInstance(ABRScannedElementPane.class)
                            .fine("Scan of elements for criteria: " + criteria + " ended");
                    ABRLogger.getInstance(ABRScannedElementPane.class)
                            .finer("list of scanned elements has " + scannedElementList.size() + " elements");
                    ABRLogger.getInstance(ABRScannedElementPane.class)
                            .fine("Starting mapping of web elements into ABRWebElements for criteria: " + criteria);

                    ABRLogger.getInstance(ABRScannedElementPane.class)
                            .fine("Reduces to the Limit of ABRWebElements : " + 50);
                    List<WebElement> scannedElementListReduced = scannedElementList.size() > 50
                            ? new ArrayList<>(scannedElementList.subList(0, 50))
                            : scannedElementList;

                    Stream<ABRWebElement> stream =
                            scannedElementListReduced.stream().map(ABRWebElement::new);

                    // Saved REferences From Priorities
                    // Update the savedReferences field for each element in the stream
                    stream.map(element -> {
                                // Update the savedReferences field
                                element.getSavedReferences().put("key", "value");
                                return element;
                            })
                            .forEach(element -> {
                                // Perform any action you want with the updated ABRWebElement
                                // For example, print the updated savedReferences field
                                System.out.println(element.getSavedReferences());
                            });

                    ABRLogger.getInstance(ABRScannedElementPane.class)
                            .fine("mapping of web elements into ABRWebElements for criteria: " + criteria + " ended");
                    ABRLogger.getInstance(ABRScannedElementPane.class)
                            .finer("Checking filtering condition != null: " + (filterCondition != null));
                    if (filterCondition != null) {
                        ABRLogger.getInstance(ABRScannedElementPane.class).fine("Starting filtering elements");
                        stream = stream.filter(filterCondition);
                        ABRLogger.getInstance(ABRScannedElementPane.class).fine("Filtering elements ended");
                    }
                    ABRLogger.getInstance(ABRScannedElementPane.class).fine("Starting loop to add ABRWebElements");
                    for (ABRWebElement element : stream.toList()) {
                        ABRLogger.getInstance(ABRScannedElementPane.class).fine("sending add request to JavaFX thread");
                        ABRLogger.getInstance(ABRScannedElementPane.class)
                                .finer("sending add request to JavaFX thread for ABRWebElement with xPath: "
                                        + element.getXPath());
                        Platform.runLater(() -> {
                            ABRLogger.getInstance(ABRScannedElementPane.class)
                                    .fine("JAVAFX Thread: adding element to list");
                            listToAddElements.add(element);
                            ABRLogger.getInstance(ABRScannedElementPane.class).fine("JAVAFX Thread: element added");
                        });
                        ABRLogger.getInstance(ABRScannedElementPane.class)
                                .finer("add request to JavaFX thread ended for ABRWebElement with xPath: "
                                        + element.getXPath());
                        Thread.sleep(100);
                    }
                    ABRLogger.getInstance(ABRScannedElementPane.class).fine("Loop to add ABRWebElements ended");
                    ABRLogger.getInstance(ABRScannedElementPane.class)
                            .fine("sending bar removal request to JAVAFX thread");
                    Platform.runLater(() -> {
                        ABRLogger.getInstance(ABRScannedElementPane.class).fine("JAVAFX Thread: start removing bar");
                        removeNodesFromPane(bottomPane, progressBar);
                        ABRLogger.getInstance(ABRScannedElementPane.class).fine("JAVAFX Thread: removing bar ended");
                    });
                    ABRLogger.getInstance(ABRScannedElementPane.class)
                            .fine("bar removal request to JAVAFX thread ended");
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
    }

    private void refreshOtherElemBtn() {
        webElementObservableList3.clear();
        manageUIScanOutputs();
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
                    List<WebElement> elementList = abrWebDriver.scan(By.xpath(abrWebElement.getXPath()));
                    for (WebElement element : elementList) {
                        abrWebDriver.highlightElement(element);
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
                    List<WebElement> elementList = abrWebDriver.scan(By.xpath(abrWebElement.getXPath()));
                    for (WebElement element : elementList) {
                        abrWebDriver.dehighlightElement(element);
                    }
                    return null;
                }
            };
            new Thread(handleEvent).start();
        };

        EventHandler<MouseEvent> mouseClickedHandler = mouseEvent -> {
            if (mouseEvent.getClickCount() == 2) {
                ABRLogger.getInstance(ABRScannedElementPane.class)
                        .info("Double clicked the element: " + abrWebElement.getXPath());
                ABRLogger.getInstance(ABRScannedElementPane.class).fine("Going to show the confirmation Alert");
                Alert alert = new Alert(
                        Alert.AlertType.CONFIRMATION,
                        "Are you sure you want to add the instruction selected to the bot job?",
                        ButtonType.YES,
                        ButtonType.NO);
                ABRLogger.getInstance(ABRScannedElementPane.class).fine("Confirmation Alert shown. Waiting for result");
                Optional<ButtonType> result = alert.showAndWait();
                ABRLogger.getInstance(ABRScannedElementPane.class).finer("result got: " + result.get());
                if (result.isPresent() && result.get() == ButtonType.YES) {
                    ABRLogger.getInstance(ABRScannedElementPane.class).info("Clicked on YES");
                    ABRLogger.getInstance(ABRScannedElementPane.class).fine("Creating Thread");
                    Task<Void> handleEvent = new Task<>() {
                        @Override
                        protected Void call() throws Exception {
                            ABRLogger.getInstance(Task.class).info("THREAD: Started");
                            ABRLogger.getInstance(Task.class)
                                    .fine("THREAD: starting element scan by xpath " + abrWebElement.getXPath());
                            List<WebElement> elementList = abrWebDriver.scan(By.xpath(abrWebElement.getXPath()));
                            ABRLogger.getInstance(Task.class)
                                    .fine("THREAD: scan ended. Detected " + elementList.size() + "element(s)");
                            ABRLogger.getInstance(Task.class).fine("THREAD: dehighlighting all elements of list");
                            for (WebElement element : elementList) {
                                ABRLogger.getInstance(Task.class).finer("THREAD: dehilighting " + element);
                                abrWebDriver.dehighlightElement(element);
                                ABRLogger.getInstance(Task.class).finer("THREAD: dehilighted " + element);
                            }
                            ABRLogger.getInstance(Task.class).fine("THREAD: fetching instruction list from database");
                            ObservableList<BlockLoopInstructionDTO> list = ABRSharedResources.getInstance()
                                    .getEntityList(
                                            BlockLoopInstructionDTO.class,
                                            (instr) -> instr.getBlock().getId() == block.getId());
                            ABRLogger.getInstance(Task.class).finer("THREAD: instruction list size " + list.size());
                            ABRLogger.getInstance(Task.class).fine("THREAD: creating instruction from webelement");
                            BlockLoopInstructionDTO instruction = abrWebElement.buildBlockLoopInstruction(list.size());
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
                                        ABRLogger.getInstance(Task.class).fine("THREAD: creating queue for references");
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
                                            ABRLogger.getInstance(Task.class).fine("THREAD: reference value set");
                                            ABRLogger.getInstance(Task.class)
                                                    .finer("THREAD: setting reference instruction: " + instruction);
                                            reference.setBlockLoopInstructionDTO(instruction);
                                            ABRLogger.getInstance(Task.class).fine("THREAD: reference instruction set");
                                            ABRLogger.getInstance(Task.class).fine("THREAD: adding reference to queue");
                                            queue.add(reference);
                                            ABRLogger.getInstance(Task.class).fine("THREAD: reference added to queue");
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
        };

        abrWebElement.addEventHandler(MouseEvent.MOUSE_ENTERED, mouseEnteredHandler);
        abrWebElement.addEventHandler(MouseEvent.MOUSE_EXITED, mouseExitedHandler);
        abrWebElement.addEventHandler(MouseEvent.MOUSE_CLICKED, mouseClickedHandler);
    }

    private List<WebElement> managePrioritiesCriteria() {
        List<WebElement> webElements = new ArrayList<>();
        if (abrPriorities.getAllPriorityList().size() > 0) {

            // Fetch the HTML content of the page
            Document docJSoup = null;
            try {
                docJSoup = Jsoup.connect(botJob.getHomeBanking().getUrl()).get();
                for (com.allinweb.ch.util.Priority priority : abrPriorities.getAllPriorityList()) {
                    switch (priority.getPriorityType()) {
                        case ByXPath -> {
                            List<String> names = priority.getName();

                            Elements elementJSoup = null;
                            // TO DO  SEARCH VARIANTS AND DISTINCT BY THOSE WERE FOUND
                            for (String name : names) {
                                try{
                                    webElements = abrWebDriver.scan(By.xpath(name));
                                }catch (Exception e){
                                    System.out.println(String.format("WebDriver cannot read this format: %s", name));
                                }
                                try{
                                    elementJSoup = docJSoup.select(name);
                                }catch (Exception e){
                                    System.out.println(String.format("Jsoup cannot read this format: %s", name));
                                }
                            }
                            // Iterate over the selected links
                            for (Element link : elementJSoup) {
                                // Get the URL and text of the link
                                String url = link.absUrl("href");
                                String text = link.text();

                                // Print the URL and text
                                if (Strings.isNullOrEmpty(url)) {
                                    url = link.attr("href");
                                }

                                // Check if the text is empty
                                if (link.text().isEmpty()) {
                                    // Check for nested elements like SVG
                                    Element svg = link.selectFirst("svg");
                                    if (svg != null && svg.selectFirst("use") != null && svg.hasAttr("xlink:href")) {
                                        String svgHref = svg.selectFirst("use").attr("xlink:href");
                                        System.out.println(
                                                "Found SVG with href: " + svgHref + " inside anchor with href: " + url);
                                        text = svgHref.toString();
                                    } else if (svg != null) {
                                        System.out.println(
                                                "Found anchor with href: " + url + " containing nested SVG.");
                                        text = svg.toString();
                                    } else {
                                        System.out.println(
                                                "Anchor with href: " + url + " has no text and no nested SVG.");
                                    }
                                } else {
                                    System.out.println("Anchor with href: " + url + " has text: " + link.text());
                                }
                                savedReferences.put(text, url);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return webElements;
    }

    private void managePrioritiesScanJSoup() {

        if (abrPriorities.getAllPriorityList().size() > 0) {

            // Fetch the HTML content of the page
            Document doc2 = null;
            try {
                doc2 = Jsoup.connect(botJob.getHomeBanking().getUrl()).get();
                for (com.allinweb.ch.util.Priority priority : abrPriorities.getAllPriorityList()) {
                    switch (priority.getPriorityType()) {
                        case jsoup -> {
                            Elements links = doc2.select(priority.getName().get(0));
                            // Iterate over the selected links
                            for (Element link : links) {
                                // Get the URL and text of the link
                                String url = link.absUrl("href");
                                String text = link.text();
                                // Print the URL and text
                                System.out.println("URL: " + url);
                                System.out.println("Text: " + text);
                                // Add the Element to the list
                                //                        jsoupElements.add(link);
                                savedReferences.put(text, url);

                                // Convert the Element to a WebElement and add it to the list
                                // WebElement webElement = driver.findElementByXPath(link.cssSelector());
                                // webElements.add(webElement);
                            }
                        }
                        case xpath -> {
                            Elements links = doc2.select(priority.getName().get(0));
                            for (Element link : links) {
                                //                                savedReferences.put(priority.getName(),
                                //                                        ABRWebUtil.extractWebElementXPath(link));
                            }
                        }
                        case coordinates -> {
                            Elements links = doc2.select(priority.getName().get(0));
                            for (Element link : links) {
                                //                                Rectangle coordinates = link.getRect();
                                //                                savedReferences.put(
                                //                                        priority.getName(),
                                //                                        (coordinates.getX() + (coordinates.getWidth()
                                // / 2)) + ","
                                //                                                + (coordinates.getY() +
                                // (coordinates.getHeight() / 2)));
                            }
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}

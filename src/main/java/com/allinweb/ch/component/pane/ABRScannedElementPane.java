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
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Predicate;
import java.util.stream.Collectors;
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
import javax.net.ssl.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.*;
import org.openqa.selenium.remote.RemoteWebElement;

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
    private CheckBox checkBoxAction;

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
        manageUIScanPriorities();
        manageUIScanInputs();
        manageUIScanClickable();
        manageUIScanOutputs();
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
            if (webElements != null) {
                scanABRElementsAsync(webElements, webElementObservableList3);
            }
        } catch (Exception e) {
            System.out.println("Error " + e.getMessage());
        }
    }

    private void manageUIScanOutputs() {
        String extRef = ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.WEBDRIVER_EXT_REFERENCE);
        scanABRElementsAsync(By.cssSelector("*[" + extRef + "]"), webElementObservableList3);
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
        ABRLogger.getInstance(ABRScannedElementPane.class)
                .fine("Going to execute the scan asynchronously for " + criteria);
        ABRLogger.getInstance(ABRScannedElementPane.class)
                .fine("Going to execute the scan asynchronously for " + criteria);

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

                    List<WebElement> scannedElementList = new ArrayList<>();
                    if (preElements != null) {
                        scannedElementList.addAll(preElements);
                    } else {
                        scannedElementList = abrWebDriver.scan(criteria);
                    }

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
                    List<ABRWebElement> listABRElements = null;
                    try {
                        listABRElements = scannedElementListReduced.stream()
                                .filter(element -> element != null) // Filter out null elements
                                .map(ABRWebElement::new)
                                .collect(Collectors.toList());

                    } catch (Exception e) {
                        System.out.println("Error ABRWebElement creation" + e.getMessage());
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

                    ABRLogger.getInstance(ABRScannedElementPane.class)
                            .fine("mapping of web elements into ABRWebElements for criteria: " + criteria + " ended");
                    ABRLogger.getInstance(ABRScannedElementPane.class)
                            .finer("Checking filtering condition != null: " + (filterCondition != null));
                    //                    if (filterCondition != null) {
                    //                        ABRLogger.getInstance(ABRScannedElementPane.class).fine("Starting
                    // filtering elements");
                    //                        stream = stream.filter(filterCondition);
                    //                        ABRLogger.getInstance(ABRScannedElementPane.class).fine("Filtering
                    // elements ended");
                    //                    }
                    ABRLogger.getInstance(ABRScannedElementPane.class).fine("Starting loop to add ABRWebElements");
                    if (listABRElements != null) {

                        for (ABRWebElement element : listABRElements) {
                            ABRLogger.getInstance(ABRScannedElementPane.class)
                                    .fine("sending add request to JavaFX thread");
                            ABRLogger.getInstance(ABRScannedElementPane.class)
                                    .finer("sending add request to JavaFX thread for ABRWebElement with xPath: "
                                            + element.getXPath());
                            Platform.runLater(() -> {
                                ABRLogger.getInstance(ABRScannedElementPane.class)
                                        .fine("JAVAFX Thread: adding element to list");
                                listToAddElements.add(element);
                                ABRLogger.getInstance(ABRScannedElementPane.class)
                                        .fine("JAVAFX Thread: element added");
                            });
                            ABRLogger.getInstance(ABRScannedElementPane.class)
                                    .finer("add request to JavaFX thread ended for ABRWebElement with xPath: "
                                            + element.getXPath());
                            Thread.sleep(100);
                        }
                        ABRLogger.getInstance(ABRScannedElementPane.class).fine("Loop to add ABRWebElements ended");
                        ABRLogger.getInstance(ABRScannedElementPane.class)
                                .fine("sending bar removal request to JAVAFX thread");
                    }
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
        List<WebElement> webElements = new ArrayList<>();
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
                                webElements = abrWebDriver.scan(By.xpath(name));
                                // Add elements from the first list to the set
                                for (WebElement element : webElements) {
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
                            // WebElements
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
                        webElements.addAll(finalList);
                        finalList.clear();
                    }
                    case ByTagName -> {
                        List<String> names = searchConfig.getName();

                        // TO DO  SEARCH VARIANTS AND DISTINCT BY THOSE WERE FOUND
                        for (String name : names) {

                            if (name.equalsIgnoreCase("label")) {
                                try {
                                    webElements = abrWebDriver.scan(By.tagName(name));
                                    // Add elements from the first list to the set
                                    for (WebElement element : webElements) {
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
                            } else {
                                try {
                                    webElements = abrWebDriver.scan(By.tagName(name));
                                    // Add elements from the first list to the set
                                    for (WebElement element : webElements) {
                                        String labelText = element.getText();

                                        if (!labelText.trim().isEmpty()) {
                                            finalList.add(element);
                                        }
                                    }
                                } catch (Exception e) {
                                    System.out.println(String.format("WebDriver cannot read this format: %s", name));
                                }
                            }
                        }
                        // Iterate over the selected links
                        //                          savedReferences.put(text, url);
                        webElements.addAll(finalList);
                        finalList.clear();
                    }
                    case attribute -> {
                        try {
                            webElements = abrWebDriver.scan(By.cssSelector("[" + searchConfig.getName() + "]"));
                            webElements = abrWebDriver
                                    .getDriver()
                                    .findElements(By.xpath("//*[@" + searchConfig.getName() + "]"));
                            // Add elements from the first list to the set
                            for (WebElement element : webElements) {
                                String attributeValue = element.getAttribute(
                                        searchConfig.getName().get(0));
                                if (attributeValue != null && !attributeValue.isBlank()) {
                                    savedReferences.put(searchConfig.getName().get(0), attributeValue);
                                }
                            }
                        } catch (Exception e) {
                            System.out.println(
                                    String.format("WebDriver cannot read this format: %s", searchConfig.getName()));
                        }
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
        return webElements;
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
            driver.get("https://www.ca-nextbank.ch/en/contact");

            // The JavaScript code to be injected
            String jsCode = "const hint = document.createElement('div');" + "hint.id = 'hint';"
                    + "hint.className = 'hint';"
                    + "document.body.appendChild(hint);"
                    + "const style = document.createElement('style');"
                    + "style.innerHTML = ` .hint {"
                    + "  position: absolute;"
                    + "  background-color: #f9f9f9;"
                    + "  border: 1px solid #ccc;"
                    + "  padding: 5px;"
                    + "  border-radius: 3px;"
                    + "  display: none;"
                    + "  z-index: 1000;"
                    + "} `;"
                    + "document.head.appendChild(style);"
                    + "document.body.addEventListener('mouseover', function(event) {"
                    + "  const target = event.target;"
                    + "  let hintText = `Tag: ${target.tagName.toLowerCase()}`;"
                    + "  if (target.type) {"
                    + "    hintText += `, Type: ${target.type}`;"
                    + "  }"
                    + "  if (target.innerText) {"
                    + "    hintText += `, Text: ${target.innerText}`;"
                    + "  }"
                    + "  hint.innerText = hintText;"
                    + "  hint.style.display = 'block';"
                    + "  hint.style.left = event.pageX + 'px';"
                    + "  hint.style.top = event.pageY + 'px';"
                    + "});"
                    + "document.body.addEventListener('mousemove', function(event) {"
                    + "  hint.style.left = event.pageX + 'px';"
                    + "  hint.style.top = event.pageY + 'px';"
                    + "});"
                    + "document.body.addEventListener('mouseout', function() {"
                    + "  hint.style.display = 'none';"
                    + "});";

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
}

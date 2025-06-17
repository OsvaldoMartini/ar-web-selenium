package com.allinweb.ch.component.pane;

import com.allinweb.ch.builder.WebElementAttributeEnum;
import com.allinweb.ch.builder.WebElementAttributeTypeValueEnum;
import com.allinweb.ch.builder.WebElementTagNameEnum;
import com.allinweb.ch.component.model.AttributeData;
import com.allinweb.ch.component.model.BlockLoadDTO;
import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.model.ElementDTO;
import com.allinweb.ch.component.model.ElementSplitDTO;
import com.allinweb.ch.component.model.HomeBankingLoadDTO;
import com.allinweb.ch.component.model.InstructionLoadDTO;
import com.allinweb.ch.component.model.InstructionReferenceLoadDTO;
import com.allinweb.ch.component.model.PayloadJson;
import com.allinweb.ch.component.model.RowStatus;
import com.allinweb.ch.component.model.VariableLoadDTO;
import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.component.scene.ARNewHomeBankingScene;
import com.allinweb.ch.component.scene.ARScannedElementScene;
import com.allinweb.ch.control.ARComponentBuilder;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.facade.PerformActions;
import com.allinweb.ch.facade.PerformCloneLoad;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.facade.PerformPreLoad;
import com.allinweb.ch.persistence.*;
import com.allinweb.ch.readersAndWriters.ExcelReader;
import com.allinweb.ch.readersAndWriters.ExcelWriter;
import com.allinweb.ch.socket.WebSocketSessionManager;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
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
import javafx.util.Pair;
import javax.websocket.ClientEndpoint;
import javax.websocket.ContainerProvider;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.WebDriverWait;

@ClientEndpoint
public class ARScannedElementPane extends ARPane {

    protected static volatile ARScannedElementPane instance;

    // Private constructor to prevent instantiation
    private ARScannedElementPane() {
        // Initialize if necessary
    }

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

    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    //    private static final ScheduledExecutorService pingScheduler = Executors.newScheduledThreadPool(1);

    private final Gson gson = new Gson();

    private void stopKeepAlivePings() {
        scheduler.shutdownNow();
    }

    private void startKeepAlivePings() {
        scheduler.scheduleAtFixedRate(
                () -> {
                    try {
                        if (session != null && session.isOpen()) {
                            session.getBasicRemote()
                                    .sendText("ping-scanner-element-pane"); // Or a specific keep-alive message
                        }
                    } catch (IOException e) {
                        System.err.println("Error sending ping: " + e.getMessage());
                        // Handle potential disconnection
                    }
                },
                0,
                15,
                TimeUnit.SECONDS); // Adjust interval as needed
    }

    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        latch.countDown(); // Release the latch after connection is established
        System.out.println("Connected to WebSocket server at: " + session.getRequestURI());
        // Sending an initial message
        sendMessage("Hello from JavaFX WebSocket client!");
    }

    @OnClose
    public void onClose(Session session) {
        System.out.println("Connection closed.");
        stopKeepAlivePings();
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        System.out.println("Error: " + throwable.getMessage());
        stopKeepAlivePings();
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

    public void connectWebSocketClient(int portSocket, String sessionId) {
        executorWebSocket.submit(() -> {
            String serverUri = "ws://localhost:" + portSocket + "/websocket?sessionId=" + sessionId;
            try {
                WebSocketContainer container = ContainerProvider.getWebSocketContainer();
                container.connectToServer(this, new URI(serverUri));
                latch.await();
                startKeepAlivePings();
                isConnectWebSocket = true;
            } catch (Exception e) {
                isConnectWebSocket = false;
                System.err.println("WebSocket connection failed sessionId: " + sessionId + " error: " + e.getMessage());
            }
        });
    }

    @OnMessage
    public void onMessage(String message) {
        System.out.println("Received: " + message);
        if (message == null || message.trim().isEmpty() || message.contains("CONNECT") || message.contains("ping")) {
            // Ignore null or empty messages
            message = message.replaceAll("ping-", "");
            System.out.println("Active : " + message);
            return;
        }

        String type = null;
        String body = null;
        //        int homeBankingId = -1;
        try {
            // Parse the incoming message (assuming JSON format)
            JsonObject jsonObjMSG = JsonParser.parseString(message).getAsJsonObject();
            //            homeBankingId = jsonObjMSG.has("homeBankingId")
            //                    ? Integer.parseInt(jsonObjMSG.get("homeBankingId").getAsString())
            //                    : -1;

            body = jsonObjMSG.has("body") ? jsonObjMSG.get("body").getAsString() : "unknown";
            if (!body.equalsIgnoreCase("unknown")) {
                JsonObject objSecond = JsonParser.parseString(body).getAsJsonObject();
                if (objSecond.has("type") && objSecond.get("type").getAsString().equalsIgnoreCase("CLOSE_BROWSER")) {
                    type = "CLOSE_BROWSER";
                } else {
                    type = jsonObjMSG.has("type") ? jsonObjMSG.get("type").getAsString() : "unknown";
                }
            } else {
                type = jsonObjMSG.has("type") ? jsonObjMSG.get("type").getAsString() : "unknown";
            }

            // After Decoding
            if (type == null || type.trim().isEmpty() || type.contains("CONNECT") || type.contains("ping")) {
                // Ignore null or empty messages
                type = type.replaceAll("ping-", "");
                System.out.println("Active : " + type);
                return;
            }

            String sessionId =
                    jsonObjMSG.has("sessionId") ? jsonObjMSG.get("sessionId").getAsString() : "unknown";

            // Process the message based on its type
            switch (type) {
                case "CLOSE_BROWSER":
                    if (!isJobRunning.get()) {
                        if (this.launchBotJobButton != null && !performActions.isJustCalledRefreshPage()) {
                            ARLogger.getInstance(ARScannedElementPane.class).finer("CLOSE_BROWSER");
                            Platform.runLater(() -> {
                                Stage stage =
                                        (Stage) launchBotJobButton.getScene().getWindow();
                                if (stage != null) {
                                    stage.close(); // <-- actually closes the Stage
                                }

                                // Clean ARScannedElementPane singleton instance
                                ARScannedElementPane.getInstance().destroy();
                                ARScannedElementScene.getInstance().destroyPanel(); //
                            });
                        }

                        if (performActions.isJustCalledRefreshPage()) {
                            performActions.setJustCalledRefreshPage(false);
                        }
                    }

                    break;
                case "NEW_ELEMENT_DTO":
                    checkRunningProcess();
                    // Extract the "body" field from the JsonObject
                    ElementSplitDTO processDTO = gson.fromJson(jsonObjMSG, ElementSplitDTO.class);
                    targetSelected = extractPickClone(processDTO.getDetails()[0]);
                    itPrintsElementDTO(targetSelected);
                    stepsInsertOneDTO(targetSelected);
                    break;
                case "SEND_ALL_ELEMENTS_DTO":
                    sendAll = true;
                    checkRunningProcess();
                    // Extract the "body" field from the JsonObject
                    processDTO = gson.fromJson(jsonObjMSG, ElementSplitDTO.class);
                    stepsInsertManyDTO(processDTO);
                    sendAll = false;
                    break;
                case "TEST_CLICK_DTO":
                case "TEST_INPUT_DTO":
                    checkRunningProcess();
                    // Extract the "body" field from the JsonObject
                    processDTO = gson.fromJson(jsonObjMSG, ElementSplitDTO.class);
                    targetSelected = extractPickClone(processDTO.getDetails()[0]);
                    itPrintsElementDTO(targetSelected);
                    testingActions(targetSelected, processDTO.getType());
                    break;
                case "DEL_ELEMENT_DTO":
                case "DETAILS_ELEMENT_DTO":
                    // Extract the "body" field from the JsonObject
                    processDTO = gson.fromJson(jsonObjMSG, ElementSplitDTO.class);
                    targetSelected = extractPickClone(processDTO.getDetails()[0]);
                    itPrintsElementDTO(targetSelected);
                    break;
                default:
                    break;
            }
        } catch (Exception error) {
            if (error.getMessage().contains("invalid session id")) {
                performMessage.errorMessage(
                        "Browser is Closed",
                        "<span style='color: #2E7D32; font-weight: bold; font-size: 1.1em;'>To perform this action, please</span> ✅",
                        "<span style='color: #1976D2;'>reopen the browser via the Scanner:</span>",
                        "<span style='font-weight: bold;'>Click the \"Scanner\" button in the previous window</span>",
                        null,
                        0);
            }

            System.err.println("Closed processing message: " + error.getMessage());
        }
    }

    private void stepsInsertOneDTO(TargetElement targetInsertOne) {
        int blockExist = validateBlockDB("Default Block", this.botJobLoad.getId());
        if (blockExist > 0 && currentBlockId > 0) {

            //            preTestCoordinates(targetInsertOne);

            List<InstructionLoadDTO> listInstr =
                    performDataBase.getInstructionsByBlockId(botJobLoad.getId(), currentBlockId, "instruction");

            int nextOrder = listInstr.size() + 1;

            if (!Strings.isNullOrEmpty(defineNameField.getText().trim())
                    && !targetInsertOne
                            .getDefinedName()
                            .equalsIgnoreCase(defineNameField.getText().trim())) {
                targetInsertOne.setDefinedName(defineNameField.getText().trim());
            }

            insertNewElementDTO(currentBlockId, nextOrder, targetInsertOne);
        }
    }

    public void destroy() {
        clearPane(getPaneReference());
        pane = null;
        scene = null;
        instance = null;
    }

    private void stepsInsertManyDTO(ElementSplitDTO processDTO) {
        validateBlockDB("Default Block", this.botJobLoad.getId());
        if (currentBlockId > 0) {
            List<InstructionLoadDTO> listInstr =
                    performDataBase.getInstructionsByBlockId(botJobLoad.getId(), currentBlockId, "instruction");

            int nextOrder = listInstr.size() + 1;

            for (ElementDTO elementDTO : processDTO.getDetails()) {
                TargetElement targetEach = extractPickClone(elementDTO);

                WebElement elementFound = performActions.findWebElement(targetEach);
                targetEach.setElement(elementFound);
                // 3 Different Coordinates
                // Original from JavaScript
                // WebDriver Selenium ElementFound
                // FallBack React Computed
                performActions.defineSavedReferenced(targetEach);

                insertNewElementDTO(currentBlockId, nextOrder, targetEach);
                nextOrder++;
            }
        }
    }

    private void preTestCoordinates(TargetElement targetPreTest) {

        Pair<String, String> filedData = new Pair<>("martini", "Martini");
        try {
            if (checkCloneElement.isSelected()) {

                performActions.executeActionsAtCoordinates(
                        targetPreTest.getCoordinates(), filedData, ARConstants.CLICK, false);
            } else {
                performActions.executeActionsAtCoordinates(
                        targetPreTest.getCoordinates(), filedData, ARConstants.COORD_MOVE_CLICK_RED, false);
            }

        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    private int validateBlockDB(String blockName, int botJobId) {

        int newBlockID = performActions.createBlockIfNone(blockName, botJobId);
        if (newBlockID > 0) {
            Platform.runLater(() -> {
                refreshBlocks(true);
            });
        }

        if (newBlockID > 0) {
            currentBlockId = newBlockID;
            currentBlockName = blockName;
        } else {
            try {
                currentBlockId = comboBoxBlocks.getValue().getBlockId();
                currentBlockName = comboBoxBlocks.getValue().getText();

            } catch (Exception error) {
                currentBlockId = -1;
                currentBlockName = "Default Block";
            }

            if (currentBlockId < 0) {

                performMessage.showCustomModalDialogDragWin11(
                        this.botJobLoad.getName(),
                        "Select the block to Add New Command!",
                        null,
                        null,
                        null,
                        true,
                        "OK",
                        null,
                        300);
            } else {
                return currentBlockId;
            }
        }
        return newBlockID;
    }

    private void insertNewElementDTO(int currentBlockId, int nextInstOrderNumber, TargetElement targetInsert) {

        if (targetInsert.getXPath() == null) {
            targetInsert.setXPath(targetInsert.getSavedReferences().get("currentXPath"));
        }

        if (targetInsert.getCoordinates() == null) {
            targetInsert.setCoordinates(targetInsert.getSavedReferences().get("coordinates"));
        }

        Task<Void> handleEvent = new Task<>() {
            @Override
            protected Void call() throws Exception {
                ARLogger.getInstance(Task.class).finer("THREAD: instruction list size " + nextInstOrderNumber);

                String actionReq = checkClickElement.isSelected()
                        ? ARConstants.CLICK
                        : checkInputText.isSelected()
                                ? ARConstants.INSERT
                                : checkOutputText.isSelected() ? ARConstants.OUTPUT : ARConstants.OTHER;

                targetInsert.setClickElement(checkClickElement.isSelected());

                WebElementTagNameEnum tagType = targetInsert.getTagType();

                if (checkForceEnterText.isSelected() && tagType.equals(WebElementTagNameEnum.INPUT)) {
                    tagType = WebElementTagNameEnum.INPUT_ENTER;
                }

                InstructionLoadDTO instruction = performActions.buildNewInstruction(
                        tagType, actionReq, false, nextInstOrderNumber, targetInsert);

                instruction.setForceCoordinates(checkForceCoordText.isSelected());

                instruction.setCoordinates(targetInsert.getCoordinates());
                instruction.setIFrameXPath(targetInsert.getIFrameXPath());

                instruction.setShadowHost(targetInsert.getShadowHost());
                instruction.setShadowRoot(targetInsert.getShadowRoot());
                instruction.setCssSelector(targetInsert.getCssSelector());

                instruction.setBlockId(currentBlockId);

                Integer currentBotJobId = botJobLoad.getId();

                // Change the Name on the fly
                instruction.setName(targetInsert.getDefinedName());

                // Update the action string if it contains "I:"
                String actions = instruction.getActions();
                String[] parts = actions.split(",");

                if (actions.startsWith("I:")) {
                    for (int i = 0; i < parts.length; i++) {
                        parts[i] = parts[i].trim(); // Ensure no leading/trailing spaces
                        if (parts[i].startsWith("I:")) {
                            if (parts[i].contains(":E:")) {
                                parts[i] = "I:E:" + targetInsert.getDefinedName();
                            } else {
                                parts[i] = "I:" + targetInsert.getDefinedName();
                            }
                            break;
                        }
                    }

                    instruction.setActions(parts[0]);
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
                        instruction.getTagName(),
                        instruction.getShadowHost(),
                        instruction.getShadowRoot(),
                        instruction.getCssSelector(),
                        currentBotJobId,
                        currentBlockId,
                        false);

                if (newId < 0) {
                    performMessage.errorMessage(
                            "Error Adding New Component Instruction",
                            "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Failed to insert new Operation!</span> ❌",
                            "<span style='color: #E65100; font-weight: bold;'>Instruction Name:</span> <span style='font-weight: bold;'>"
                                    + instruction.getName() + "</span>",
                            "<span style='font-style: italic;'>This operation could not be added. Please review the application state and any related processes.</span>",
                            null,
                            0);

                    return null;
                }

                instruction.setId(newId);

                targetInsert.setInstructionId(instruction.getId());
                List<InstructionReferenceLoadDTO> queue = new ArrayList<>();
                for (String key : targetInsert.getSavedReferences().keySet()) {
                    InstructionReferenceLoadDTO reference = new InstructionReferenceLoadDTO();
                    reference.setReferenceType(key);
                    reference.setValue(targetInsert.getSavedReferences().get(key));

                    reference.setBotJobId(currentBotJobId);

                    //
                    // reference.setBlockLoopInstructionLoadDTO(instruction);
                    queue.add(reference);
                }
                try {

                    //                    Platform.runLater(() -> {
                    try {
                        performDataBase.insertReferences(queue, instruction.getId());
                        if (!sendAll) {
                            updateBotJobTasks(currentBotJobId);
                        }
                    } catch (SQLException error) {
                        ARLogger.getInstance(PerformDataBase.class)
                                .severe("Cannot Insert References. Error: " + error.getMessage());
                        performMessage.errorMessage(
                                "Web Instruction Analysis",
                                "<span style='color: #FFA000; font-weight: bold; font-size: 1.1em;'>Potential Issue with Web Instruction</span> ⚠️",
                                "<span style='color: #E65100;'>Instruction:</span> <span style='font-weight: bold;'>\""
                                        + instruction.getName() + "\"</span>",
                                "<span style='color: #757575;'>Added with " + queue.size()
                                        + " reference locators.</span>",
                                "<span style='font-style: italic;'>Warning: The engine might not process this element correctly due to insufficient identifiable attributes. Consider adding more specific locators.</span>",
                                0);
                    }

                    //                    });
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

    private void updateBotJobTasks(int currentBotJobId) {
        botJobLoadList = performDataBase.loadCompleteJobs(currentBotJobId);
        String jsonData = "[]";
        if (!botJobLoadList.isEmpty()) {
            List<InstructionLoadDTO> blockLoopInstructions =
                    performDataBase.buildJsonViewData(botJobLoadList, "instruction");
            jsonData = gson.toJson(blockLoopInstructions);
        }
        webSocketSessionManager.sendMessageJson(
                homeBanking.getId(),
                "botJobTasks", // + currentBotJobId,
                jsonData,
                "updateInstructions");
    }

    private void testingActions(TargetElement targetTest, String testType) {
        try {
            if (targetTest.getElement() != null) {

                //                            arWebDriver.dehighlightElement(targetTest.getElement());

                //                            WebElement elementXPath =
                //
                // performActions.getCurrentDriver().findElement(By.xpath(arWebElement.getTargetElement().getXPath()));
                //                            if (elementXPath != null) {
                //                                elementXPath.click();
                //                            }

                Pair<String, String> fieldData = new Pair<>("Test", testActionsField.getText());

                String mainCoordenates = targetTest.getCoordinates();
                String savedCoordenates = targetTest.getSavedReferences().get("coordinates");
                if (Strings.isNullOrEmpty(mainCoordenates)) {
                    mainCoordenates = targetTest.getCoordinates();
                }

                if (Strings.isNullOrEmpty(savedCoordenates)) {
                    savedCoordenates = mainCoordenates;
                }

                String mainCoordinates = targetTest.getCoordinates();
                //                String savedCoordinates = targetTest.getSavedReferences().get("coordinates");

                if (Strings.isNullOrEmpty(mainCoordinates)) {
                    mainCoordinates = targetTest.getCoordinates();
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

                WebDriver driverTestActions = performActions.getCurrentDriver();

                String result = performActions.sequenceOfCommands(
                        targetTest.getElement(), ARConstants.SELECT, coordinates, fieldData, driverTestActions, false);
                System.out.println(result);
                actionsTested.append(result + System.lineSeparator());
                actionText2 = new Text(result);
                if (result.contains("Failed")) {
                    actionText2.setStyle("-fx-font-size: 12px; -fx-fill: red;");
                } else {
                    actionText2.setStyle("-fx-font-size: 12px; -fx-fill: green;");
                }

                if (testType.equals("TEST_CLICK_DTO")) {
                    result = performActions.sequenceOfCommands(
                            targetTest.getElement(),
                            ARConstants.CLICK,
                            coordinates,
                            fieldData,
                            driverTestActions,
                            false);
                    System.out.println(result);
                    actionsTested.append(result + System.lineSeparator());
                    actionText3 = new Text(result);
                    if (result.contains("Failed")) {
                        actionText3.setStyle("-fx-font-size: 12px; -fx-fill: red;");
                    } else {
                        actionText3.setStyle("-fx-font-size: 12px; -fx-fill: green;");
                    }
                }
                //                result = performActions.sequenceOfCommands(
                //                        targetTest.getElement(),
                //                        ARConstants.GET_VALUE,
                //                        coordinates,
                //                        fieldData,
                //                        driverTestActions,
                //                        false);
                //                System.out.println(result);
                //                actionsTested.append(result + System.lineSeparator());
                //                actionText4 = new Text(result);
                //                if (result.contains("Failed")) {
                //                    actionText4.setStyle("-fx-font-size: 12px; -fx-fill: red;");
                //                } else {
                //                    actionText4.setStyle("-fx-font-size: 12px; -fx-fill: green;");
                //                }

                if (testType.equals("TEST_INPUT_DTO")) {
                    result = performActions.sequenceOfCommands(
                            targetTest.getElement(),
                            ARConstants.CLICK,
                            coordinates,
                            fieldData,
                            driverTestActions,
                            false);
                    System.out.println(result);
                    actionsTested.append(result + System.lineSeparator());
                    actionText3 = new Text(result);
                    if (result.contains("Failed")) {
                        actionText3.setStyle("-fx-font-size: 12px; -fx-fill: red;");
                    } else {
                        actionText3.setStyle("-fx-font-size: 12px; -fx-fill: green;");
                    }
                    performActions.onHoldInSeconds(1);

                    result = performActions.sequenceOfCommands(
                            targetTest.getElement(),
                            ARConstants.CLEAR,
                            coordinates,
                            fieldData,
                            driverTestActions,
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
                            targetTest.getElement(),
                            ARConstants.INSERT,
                            coordinates,
                            fieldData,
                            driverTestActions,
                            false);
                    System.out.println(result);
                    actionsTested.append(result + System.lineSeparator());
                    actionText6 = new Text(result);
                    if (result.contains("Failed")) {
                        actionText6.setStyle("-fx-font-size: 12px; -fx-fill: red;");
                    } else {
                        actionText6.setStyle("-fx-font-size: 12px; -fx-fill: green;");
                    }

                    performActions.onHoldInSeconds(1);
                    result = performActions.sequenceOfCommands(
                            targetTest.getElement(),
                            ARConstants.CLEAR,
                            coordinates,
                            fieldData,
                            driverTestActions,
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
                            targetTest.getElement(),
                            ARConstants.COORD_CLICK,
                            coordinates,
                            fieldData,
                            driverTestActions,
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
                            targetTest.getElement(),
                            ARConstants.COORD_INSERT,
                            coordinates,
                            fieldData,
                            driverTestActions,
                            false);
                    System.out.println(result);
                    actionsTested.append(result + System.lineSeparator());
                    actionText5 = new Text(result);
                    if (result.contains("Failed")) {
                        actionText5.setStyle("-fx-font-size: 12px; -fx-fill: red;");
                    } else {
                        actionText5.setStyle("-fx-font-size: 12px; -fx-fill: green;");
                    }

                    performActions.onHoldInSeconds(1);

                    result = performActions.sequenceOfCommands(
                            targetTest.getElement(),
                            ARConstants.CLEAR,
                            coordinates,
                            fieldData,
                            driverTestActions,
                            false);
                    System.out.println(result);
                    actionsTested.append(result + System.lineSeparator());
                    actionText5 = new Text(result);
                    if (result.contains("Failed")) {
                        actionText5.setStyle("-fx-font-size: 12px; -fx-fill: red;");
                    } else {
                        actionText5.setStyle("-fx-font-size: 12px; -fx-fill: green;");
                    }
                }

                //                result = performActions.sequenceOfCommands(
                //                        targetTest.getElement(), ARConstants.FOCUS, coordinates, fieldData,
                // driverTestActions, false);
                //                System.out.println(result);
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
                //                        targetTest.getElement(), ARConstants.TAB, coordinates, fieldData,
                // driverTestActions, false);
                //                System.out.println(result);
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
                //                        targetTest.getElement(),
                //                        ARConstants.COORD_VISUALIZA,
                //                        coordinates,
                //                        fieldData,
                //                        driverTestActions,
                //                        false);
                //                System.out.println(result);
                //                actionsTested.append(result + System.lineSeparator());
                //                actionText9 = new Text(result);
                //                if (result.contains("Failed")) {
                //                    actionText9.setStyle("-fx-font-size: 12px; -fx-fill: red;");
                //                } else {
                //                    actionText9.setStyle("-fx-font-size: 12px; -fx-fill: green;");
                //                }

                //                result = performActions.sequenceOfCommands(
                //                        targetTest.getElement(),
                //                        ARConstants.COORD_CLICK,
                //                        coordinates,
                //                        fieldData,
                //                        driverTestActions,
                //                        false);
                //                System.out.println(result);
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
                //                        targetTest.getElement(),
                //                        ARConstants.COORD_INSERT,
                //                        coordinates,
                //                        fieldData,
                //                        driverTestActions,
                //                        false);
                //                System.out.println(result);
                //                actionsTested.append(result + System.lineSeparator());
                //                actionText11 = new Text(result);
                //                if (result.contains("Failed")) {
                //                    actionText11.setStyle("-fx-font-size: 12px; -fx-fill: red;");
                //                } else {
                //                    actionText11.setStyle("-fx-font-size: 12px; -fx-fill: green;");
                //                }

                //                result = performActions.sequenceOfCommands(
                //                        targetTest.getElement(),
                //                        ARConstants.COORD_INSERT,
                //                        coordinates,
                //                        fieldData,
                //                        driverTestActions,
                //                        true);
                //                System.out.println(result);
                //                actionsTested.append(result + System.lineSeparator());
                //                actionText12 = new Text(result);
                //                if (result.contains("Failed")) {
                //                    actionText12.setStyle("-fx-font-size: 12px; -fx-fill: red;");
                //                } else {
                //                    actionText12.setStyle("-fx-font-size: 12px; -fx-fill: green;");
                //                }

                //                result = performActions.sequenceOfCommands(
                //                        targetTest.getElement(),
                //                        ARConstants.COORD_MOVE_CLICK_RED,
                //                        coordinates,
                //                        fieldData,
                //                        driverTestActions,
                //                        true);
                //                System.out.println(result);
                //                actionsTested.append(result + System.lineSeparator());
                //                actionText13 = new Text(result);
                //                if (result.contains("Failed")) {
                //                    actionText13.setStyle("-fx-font-size: 12px; -fx-fill: red;");
                //                } else {
                //                    actionText13.setStyle("-fx-font-size: 12px; -fx-fill: green;");
                //                }
                //
                //                System.out.println(actionsTested);

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
        }
    }

    public WebDriver currentDriver;
    private Set<String> windowHandles;

    private static final CountDownLatch latch = new CountDownLatch(1);
    private Session session;

    private ExecutorService executorWebSocket;
    private ExecutorService executorServicePreLaunch;
    private final AtomicBoolean isJobRunning = new AtomicBoolean(false);
    private BooleanProperty interceptBotJob = new SimpleBooleanProperty(false);

    private static TargetElement targetSelected = new TargetElement();

    private static File baseLogFile = null;
    private static SimpleDateFormat dateFormatter;

    private static JavascriptExecutor jsExecutor;

    private final ARComponentBuilder componentBuilder = new ARComponentBuilder();

    private DatabaseUserDTO databaseUserDto;

    private BotJobLoadDTO botJobLoad;
    private BlockLoadDTO blockLoad;

    private int currentBlockId;
    private String currentBlockName;

    double comboWidth = 200;

    private List<BotJobLoadDTO> botJobLoadList = new ArrayList<>();
    private List<BlockLoadDTO> blockLoadList = new ArrayList<>();
    private HomeBankingLoadDTO homeBanking;
    private boolean sendAll;

    private ComboBox<ComboBoxVars> comboBoxBlocks;
    private final ObservableList<ComboBoxVars> blocksItems = FXCollections.observableArrayList();

    Button refreshBlocksButton;

    // UI COMPONENTS
    private HBox topPane;
    private VBox verticalBox;
    private AnchorPane mainPane;

    private final WebView webView = new WebView();
    private WebEngine webEngine;
    private VBox elements2VBox;
    private HBox componentBox;

    private Button cloneElementsButton;
    private Button configureButton;
    private Button launchBotJobButton;
    private Button stopBotJobButton;
    private Button searchWebElementsButton;
    private Button refreshWebPageButton;
    private Button leftButton;
    private Button rightButton;
    private Button cleanListButton;
    private Button turnOnOffButton;
    private Button searchButton;

    private CheckBox checkCloneElement;

    private Label testActionLabel;
    private CheckBox checkClickElement;
    private CheckBox checkInputText;
    private CheckBox checkOutputText;
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
    private TextField defineNameField;

    private TextField testActionsField;
    private TextField searchAttribValueField;
    private TextField coordsTextField;

    private Map<String, String> mapOperators;
    private Map<String, String> mapExportRows;
    private Set<String> headersExport = new LinkedHashSet<>();
    private List<String> columnsCSV = new ArrayList<>();
    private List<List<String>> rowsCSV = new ArrayList<>();
    private static final String END_OF_FILE_MARKER = "END OF FILE";
    String excelFieldName;
    String delimiterCSV;

    private List<VariableLoadDTO> variablesLoaded;

    private int portSocketInitial = 54525;
    private boolean isConnectWebSocket = false;

    private String[] defaultSearch;
    private boolean searchHiddenFields;
    private String xpathTextPrevious;

    private String jsonData;
    private String sessionIdFromJava;

    private String sessionRowStatus;
    private String jsonStatus;
    private RowStatus rowStatus = new RowStatus();
    private PayloadJson payloadEmpty;

    private static String[] lstAllPaths;

    // Very important sequence on initiation
    private static final ARPropertyManager arPropertyManager;
    private static final ARPriorities arPriorities;
    private static final WebSocketSessionManager webSocketSessionManager;

    private ARWebDriver currentARWebDriver;

    private static final ARScannedElementScene arScannedElementScene;
    private static final PerformCloneLoad performCloneLoad;
    private static final PerformDataBase performDataBase;
    private static final PerformActions performActions;
    private static final PerformMessage performMessage;
    private static final PerformPreLoad performPreLoad;
    //    private static final PerformCloseBrowser performCloseBrowser;

    private static final ARNewHomeBankingScene arNewHomeBankingScene;

    // Static block to initialize
    static {
        arScannedElementScene = ARScannedElementScene.getInstance();
        arPropertyManager = ARPropertyManager.getInstance();
        webSocketSessionManager = WebSocketSessionManager.getInstance();
        performDataBase = PerformDataBase.getInstance();
        performActions = PerformActions.getInstance();
        performMessage = PerformMessage.getInstance();
        performPreLoad = PerformPreLoad.getInstance();
        //        performCloseBrowser = PerformCloseBrowser.getInstance();

        performCloneLoad = PerformCloneLoad.getInstance();
        arPriorities = ARPriorities.getInstance();
        arNewHomeBankingScene = ARNewHomeBankingScene.getInstance();
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

    public void initialize(
            ARWebDriver currentARWebDriver,
            HomeBankingLoadDTO homeBanking,
            BotJobLoadDTO botJobLoadDTO,
            BlockLoadDTO blockLoadDTO,
            ExecutorService executorWebSocket,
            ExecutorService executorServicePreLaunch) {
        this.currentARWebDriver = currentARWebDriver;
        this.homeBanking = homeBanking;

        this.executorWebSocket = executorWebSocket;
        this.executorServicePreLaunch = executorServicePreLaunch;

        String port = arPropertyManager.getProperty(ARPropertyEnum.PORT_SOCKET);
        if (!Strings.isNullOrEmpty(port)) {
            portSocketInitial = Integer.parseInt(port);
        }

        if (!isConnectWebSocket) {
            connectWebSocketClient(portSocketInitial, "scannerReceiver");
        }

        searchHiddenFields = false;

        defaultSearch = new String[] {"input", "textarea", "button", "a", "select", "label"};

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
        this.botJobLoad = botJobLoadDTO;
        this.blockLoad = blockLoadDTO;
        performActions.initialize(arPriorities);
        performActions.setCurrentDriver(currentARWebDriver.getCurrentDriver());

        updateSceneTitleWithCurrentURL(homeBanking.getUrl());

        //        if (!initializeWebView()) {
        //            return;
        //        }

        if (componentBox != null) {
            Platform.runLater(() -> refreshBlocks(false));

            Platform.runLater(() -> refreshGrids());

            if (!openWebDriver(false)) {
                arScannedElementScene.closeWebDrivers();
                arScannedElementScene.closeModal();
                return;
            }

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
                this.botJobLoad.getHomeBankingId(), "scannerGrid", jsonData, "searchTerms");
    }

    private boolean initializeWebView() {
        setPayloadEmpty();

        webEngine = webView.getEngine();
        webEngine.javaScriptEnabledProperty().set(true);

        String jsonData = gson.toJson(payloadEmpty);

        // sessionIdFromJava
        // (SENDER: scannerTool) -> scannerGrid /  (SENDER: insertTool) -> botJobTasks /
        sessionIdFromJava = "scannerGrid"; // + this.homeBanking.getId();
        buildWebView(
                webEngine,
                jsonData,
                portSocketInitial,
                sessionIdFromJava,
                homeBanking.getId(),
                this.botJobLoad.getId(),
                this.botJobLoad.getName());

        if (isBrowserClosed(performActions.getCurrentDriver()) && performActions.getCurrentDriver() != null) {
            performActions.getCurrentDriver().quit();
            performActions.setCurrentDriver(null);
            currentARWebDriver.getCurrentDriver().quit();
            currentARWebDriver.setCurrentDriver(null);
        }

        String version = System.getProperty("java.version");
        System.out.println("Detected Java Version: " + version);

        int majorVersion = getMajorJavaVersion(version);
        if (majorVersion >= 17) {
            System.out.println("✅ Java 17 or higher is installed.");
        } else {
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
        //                    homeBanking.getId(),
        //                    homeBanking.getUrl());
        //        });

        performActions.getIframeElementsMap();

        handleWindowHandlesChange();

        return true;
    }

    private boolean openWebDriver(boolean firstLoad) {

        String webDriverPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_WEBDRIVER);
        if (!(new File(webDriverPath)).exists()) {
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
            WebDriver returned = currentARWebDriver.openDriver(
                    browserType,
                    webDriverPath,
                    homeBanking.getUrl(),
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
                currentARWebDriver.getCurrentDriver().get(homeBanking.getUrl());
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
    }

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
                    ARLogger.getInstance(ARViewBotJobPane.class).severe("buildWebView  \nError: " + e.getMessage());
                }
            }
        });
    }

    private void buildUIComponents() {
        topPane = componentBuilder.createTopPanel(ARConstants.SPACE_L, ARConstants.SPACE_SM);
        mainPane = componentBuilder.createContentPanel(ARConstants.SPACE_L, ARConstants.SPACE_XL, ARConstants.SPACE_SM);

        cloneElementsButton = componentBuilder.buildButton(
                "Clone", ARConstants.SPACE_L, ARConstants.ICON_TICK, ARConstants.SPACE_SM, new Insets(5));
        searchWebElementsButton = componentBuilder.buildButton(
                "Page Scanner", ARConstants.SPACE_ZERO, "/refresh.png", ARConstants.SPACE_M, new Insets(5.0D));

        turnOnOffButton = new Button("Search Hidden Fields: Off");
        turnOnOffButton.setStyle("-fx-background-color: grey; -fx-text-fill: white;");

        refreshWebPageButton = componentBuilder.buildButton(
                "Refresh Web Page", ARConstants.SPACE_ZERO, "/refresh.png", ARConstants.SPACE_M, new Insets(5.0D));

        cleanListButton = componentBuilder.buildButton(
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

        configureButton = componentBuilder.buildButton(
                "Config", ARConstants.SPACE_M, ARConstants.ICON_CONFIG, ARConstants.SPACE_M, new Insets(5.0D));

        launchBotJobButton = componentBuilder.buildButton(
                "Pre-Launch", ARConstants.SPACE_ZERO, "/play.png", ARConstants.SPACE_M, new Insets(5.0D));
        stopBotJobButton = componentBuilder.buildButton(
                "STOP", ARConstants.SPACE_ZERO, "/stop.png", ARConstants.SPACE_M, new Insets(5.0D));

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

        leftButton = componentBuilder.buildButton(
                "Previous", ARConstants.SPACE_M, ARConstants.ICON_LEFT, ARConstants.SPACE_M, new Insets(5.0D));
        rightButton = componentBuilder.buildButton(
                "Next", ARConstants.SPACE_M, ARConstants.ICON_RIGHT, ARConstants.SPACE_M, new Insets(5.0D));
        searchButton = componentBuilder.buildButton(
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
            //                        homeBanking.getId(),
            //                        homeBanking.getUrl());
            //            });
        });

        cleanListButton.setOnAction(e -> {
            if (webEngine != null) {
                //                webEngine.reload();

                var processDTO = new ElementSplitDTO();
                processDTO.setHomeBankingId(homeBanking.getId());
                processDTO.setSessionId("scannerGrid"); // + homeBanking.getId());
                processDTO.setOperationId("searchTerms");
                processDTO.setDetails(new ElementDTO[0]);
                webSocketSessionManager.sendMessageJson(
                        homeBanking.getId(), "scannerGrid", gson.toJson(processDTO), "searchTerms");

                Platform.runLater(() -> {
                    countdownTextField.setText("Pre-Launch status: Ready");
                });
            }
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
            gridPaneTop.add(searchWebElementsButton, 0, 0);
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
            System.out.println("interceptBotJob changed from " + oldVal + " to " + newVal);
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

            this.botJobLoadList = performDataBase.loadCompleteJobs(botJobLoad.getId());

            // Set all instructions' executed field to false
            botJobLoadList.get(0).getBlockLoadDTOList().stream()
                    .flatMap(block -> block.getInstructionLoadDTOS().stream())
                    .forEach(instruction -> instruction.setExecuted(false));

            recallJob();
        });

        stopBotJobButton.setOnMouseClicked(e -> {
            launchBotJobButton.setDisable(false);
            performActions.setInterceptBotJob(true);
            setInterceptBotJob(true);
            isJobRunning.set(false);

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
                String destinationId = "scannerGrid"; // + homeBanking.getId();
                Platform.runLater(() -> periodicPickOneCloneThread(
                        performActions.getCurrentDriver(),
                        false,
                        finalPort,
                        socketSessionId,
                        destinationId,
                        "addPickOne",
                        homeBanking.getId(),
                        performActions.getCurrentDriver().getCurrentUrl()));
            }

            Platform.runLater(() -> {
                launchBotJobButton.setDisable(checkCloneElement.isSelected());

                if (!checkCloneElement.isSelected()) {
                    defineNameField.clear();
                }
            });
        });

        cloneElementsButton.setOnAction(e -> {
            if (targetSelected != null && targetSelected.getElement() != null) {
                cloneElementDTO(targetSelected);
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

        searchWebElementsButton.setOnAction(e -> searchTermsBtn(null));

        searchButton.setOnAction(e -> searchTermsBtn(searchTermsField.getText().trim()));

        turnOnOffButton.setVisible(false);
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

            var processDTO = new ElementSplitDTO();
            processDTO.setHomeBankingId(homeBanking.getId());
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
            processDTO.setDetails(detailsArray);

            for (int x = 0; x < detailsArray.length; x++) {
                detailsArray[x].setTypeElement("tagName-Found");
                detailsArray[x].setId(x + 1);
            }

            webSocketSessionManager.sendMessageJson(
                    homeBanking.getId(), "scannerGrid", gson.toJson(processDTO), "clonedElement");
        }
    }

    private TargetElement extractPickClone(ElementDTO elementDTO) {

        xpathTextPrevious = elementDTO.getXPath();

        TargetElement targetLocal = performActions.defineSearchReturn(elementDTO, null);

        WebElement elementFound = performActions.findWebElement(targetLocal);
        targetLocal.setElement(elementFound);
        // 3 Different Coordinates // Original from JavaScript  // WebDriver Selenium ElementFound
        // FallBack React Computed
        performActions.defineSavedReferenced(targetLocal);

        targetLocal = performActions.defineNameTitles(targetLocal);

        // First  Search for ShadowRoot
        if (Strings.isNullOrEmpty(targetLocal.getShadowHost()) && Strings.isNullOrEmpty(targetLocal.getCssSelector())) {

            TargetElement targetValidated = checkValidateSearchPriorities(targetLocal);

            if (targetValidated.getElement() == null) {

                performMessage.errorMessage(
                        "I Cannot define this element",
                        "I will use the Locator \"COORDINATES\"",
                        "Try to get it again -> \"HOVER PICK  ELEMENT\" or \"PICK ONE \"",
                        null,
                        null,
                        0);

                return null;
            }
        } else {
            targetLocal.setXPathWorkedFirst(ARConstants.SHADOW_DOM);
        }

        //        targetElement = performActions.defineTagType(targetElement);

        defineCheckBoxesClickable(targetLocal);

        return targetLocal;
    }

    private void defineCheckBoxesClickable(TargetElement targetCheck) {
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

    private TargetElement checkValidateSearchPriorities(TargetElement target) {
        WebElement elementValid = null;
        if (!Strings.isNullOrEmpty(target.getCurrentXPath())) {

            if (target.getForceCoordinates() != null && target.getForceCoordinates()) {
                // Try by coordinates
                try {
                    Pair<String, String> filedData = new Pair("&EMPTY", "&EMPTY");
                    boolean passed = performActions.executeActionsAtCoordinates(
                            target.getCoordinates(), filedData, ARConstants.VISUALIZE, false);
                    if (passed) {
                        elementValid = performActions.getElementFromCoordinates(target.getCoordinates());
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
                homeBanking.getId());

        //        Platform.runLater(() -> periodicSearchThread(
        //                performActions.getCurrentDriver(),
        //                performActions.getCurrentDriver().getCurrentUrl(),
        //                dataArray,
        //                finalPort));
    }

    private void searchTermsBtn(String searchTerms) {

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

    private void revertSearchTermsInjections(WebDriver driver) {
        try {
            jsExecutor = (JavascriptExecutor) driver;
            jsExecutor.executeScript("window.revertSearchInjections();");
        } catch (Exception ignore) {
        }
    }

    private void itPrintsElementDTO(TargetElement target) {

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

        if (target.getElement() != null) {

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

            } else if (targetSelected.getAttributeData().length > 0) {

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

        //                sb.append(this.targetElement.getOriginalTagName() + "-" +
        // this.targetElement.getSomeText())
        //                        .append("\n");

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
        for (AttributeData attribute : targetSelected.getAttributeData()) {
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

        defineCheckBoxesClickable(targetSelected);
        performActions.getCurrentDriver().switchTo().defaultContent();
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

    private void periodicPickOneCloneThread(
            WebDriver driver,
            boolean searchHiddenFields,
            int port,
            String sessionId,
            String destination,
            String operationId,
            int homeBankingId,
            String currentUrl) {

        ErrorMessage errorMessage = performCloneLoad.dynamicPickOneCloneElementsDTO(
                driver, searchHiddenFields, port, sessionId, destination, operationId, homeBankingId, currentUrl);

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

    private void periodicSearchThread(
            WebDriver driver,
            String[] dataArray,
            int port,
            String sessionId,
            String destinationId,
            String operationId,
            int homeBankingId) {
        // "scannerTool", "scannerGrid", "searchTerms"
        ErrorMessage errorMessage = performPreLoad.dynamicLoadElementsDTO(
                driver, dataArray, searchHiddenFields, port, sessionId, destinationId, operationId, homeBankingId);

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

    private void recallJob() {
        if (isJobRunning.compareAndSet(false, true)) { // Try to set to true if currently false
            try {
                if (executorServicePreLaunch == null || executorServicePreLaunch.isShutdown()) {
                    executorServicePreLaunch = Executors.newSingleThreadExecutor();
                }

                executorServicePreLaunch.submit(() -> {
                    try {
                        executeJob();
                    } finally {
                        isJobRunning.set(false);
                    }
                });
            } catch (Exception ignore) {
                // Log the error properly instead of ignoring
                ARLogger.getInstance(ARScannedElementPane.class)
                        .severe("Error submitting to executorServicePreLaunch: " + ignore.getMessage());
                isJobRunning.set(false); // Ensure flag is reset on submission failure
            }
        } else {
            // Optionally log that a new execution was requested but is already running
            System.out.println("recallJob() requested, but executeJob() is already running.");
            ARLogger.getInstance(ARScannedElementPane.class)
                    .info("recallJob() requested while executeJob() was running.");
        }

        if (performActions.getCurrentDriver().getWindowHandles().size() != performActions.windowHandlesList.size()) {
            performActions.updateWindowHandlesList();
            updateButtonState();
        }
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

        try {
            baseLogFile = new File(
                    arPropertyManager.getProperty(ARPropertyEnum.PATH_LOG) + ARConstants.FILE_NAME_SCANNER_BASE_LOG);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        List<BlockLoadDTO> blocksLoaded = botJobLoadList.get(0).getBlockLoadDTOList();
        String botJobName = botJobLoadList.get(0).getName();

        //        ARPropertyManager managerProps = ARPropertyManager.getInstance();
        String excelPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_EXCEL);
        excelPath = excelPath + "\\" + blocksLoaded.get(0).getBotJobName() + ".xlsx";
        if (!(new File(excelPath)).exists()) {

            performMessage.errorMessage(
                    "Duplicate Name",
                    "<span style='color: #000080; font-weight: bold; font-size: 14px;'>File Excel Does not Exist</span>",
                    "<span style='color: #000080; font-weight: bold; font-size: 14px;'>Excel file: </span>",
                    "<span style='color: #000080; font-weight: bold;'>" + excelPath + "</span>",
                    "<span style='color: red; font-weight: bold;'>IS MANDATORY TO HAVE EXCEL FILE FOR TESTS!!!</span>",
                    0);

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

        if (!new File(excelPath).exists()) {
            performMessage.errorMessage(
                    "Action Required: Prepare Excel Data",
                    "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Crucial Step: Prepare Excel Data Before Launch!</span>",
                    "<span style='color: #2E7D32; font-weight: bold;'>To successfully initiate the bot job, the Excel data file must be generated and compiled *first*.</span>",
                    "<span style='font-style: italic;'>Ensure this preparation is complete before attempting to launch the automation process.</span>",
                    null,
                    0);

            return false;
        }

        ExcelReader excelReader = new ExcelReader();
        ExtractedData extractedData = null;
        try {
            extractedData = excelReader.extractData(excelPath, allActions);
        } catch (Exception e) {
            performMessage.errorMessage(
                    "Error Processing Excel File",
                    "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Failed to Execute Excel File!</span> ⚠️",
                    "<span style='color: #E65100; font-weight: bold;'>Please carefully review all Excel columns and their values for potential errors.</span>",
                    "<span style='font-style: italic;'>Inconsistent or incorrect data can prevent the application from processing the file.</span>",
                    null,
                    0);

            //            Platform.exit();
        }

        if (extractedData.getNumberOfDataRows() == 0) {
            extractedData.addField("$EMPTY");
            extractedData.addFieldValue("$EMPTY", "$EMPTY", 0);
        }

        if (extractedData != null && extractedData.getErrorMessage() != null) {

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

        //        String browser = arPropertyManager.getProperty(ARPropertyEnum.BROWSER);
        //            WebPage webPage = new WebPage(browser, homeBankingDTO.getUrl());

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

        String mainMsg = "";
        boolean byPassNotFound = false;
        boolean byPassFlagLoop;
        boolean success = true;
        boolean stopAll = false;
        long botJobStartTime = System.nanoTime();
        long totalExecutionTime = 0;
        String resultActions = "No instruction executed yet";
        String failedMessage = "";
        Map<String, String> dataExcel = null;

        clearFields();

        // Execute All Blocks starting from executeSpecificBlock if Defined
        int botJobId = this.botJobLoad.getId();
        int executeSpecificBlock = comboBoxBlocks.getValue().getInstructionId();
        sessionRowStatus = "botJobTasks"; // + botJobId;

        mapOperators = new HashMap<>();
        variablesLoaded = performDataBase.loadAllVariables(botJobId);
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
        boolean webElementWork = false;

        if (extractedData.getNumberOfDataRows() > 0) {
            List<InstructionLoadDTO> excelDataGoto = performDataBase.loadExcelGotoBlock(homeBanking.getId(), botJobId);

            if (extractedData.getNumberOfDataRows() > 1 && excelDataGoto.isEmpty()) {

                respModal = performMessage.showCustomModalDialogDragWin11(
                        "Multiple Excel Rows Detected",
                        "<span style='font-weight: bold;'>Your Excel data file contains multiple rows.</span>",
                        "By default, each Excel test row <span style='font-weight: bold; color: #e854c8;'>will be processed through all blocks</span>, and after  will jump back to <span style='font-weight: bold;'>first block (Use Case).</span>",
                        "Add the <span style='font-weight: bold; color: #FF4500;'>'Excel GOTO'</span> operation to your flow to modify the <span style='font-weight: bold;'>default behaviour.</span>",
                        "The <span style='font-weight: bold; color: #FF4500;'>Excel GOTO</span> allows you to specify which block <span style='font-weight: bold;'>the flow should continue from</span>, after the execution of the first row across all blocks.",
                        false,
                        "Continue",
                        "Stop All",
                        0);

                if (respModal.equals(ARConstants.DialogModal.STOP)) {

                    launchBotJobButton.setDisable(false);
                    performActions.setInterceptBotJob(true);
                    setInterceptBotJob(true);
                    isJobRunning.set(false);

                    if (!lastBrowserTab()) {
                        return false;
                    }
                }
            }

            // Execute All Blocks starting from executeSpecificBlock if Defined
            int currentBlock = (executeSpecificBlock > -1) ? executeSpecificBlock - 1 : 0;
            int blockInitial = currentBlock;

            if (!excelDataGoto.isEmpty() && !blocksLoaded.isEmpty()) {
                Integer parentId = excelDataGoto.get(excelDataGoto.size() - 1).getParentId();
                blockInitial = performActions.getBlockOrderNumber(blocksLoaded, parentId) - 1;
            }

            int xExcelCurrentRow = 0;
            int xExcelDataSize = extractedData.getNumberOfDataRows();
            mapExportRows = new LinkedHashMap<>();

            while (xExcelCurrentRow <= xExcelDataSize - 1 && !blocksLoaded.isEmpty() && !stopAll) {

                blockLoop:
                while (currentBlock <= blocksLoaded.size() - 1 && !blocksLoaded.isEmpty() && !stopAll) {
                    long blockStartTime = System.nanoTime();

                    currentCondition = ARConstants.ConditionStatus.NONE;
                    previousCondition = ARConstants.ConditionStatus.NONE;
                    progressCondition = ARConstants.ConditionStatus.NONE;

                    respModal = ARConstants.DialogModal.NONE;

                    int parentBlockCondition = -1;

                    BlockLoadDTO blockLoad = blocksLoaded.get(currentBlock);

                    String blockName = blocksLoaded.get(currentBlock).getName();
                    int blockOrder = blocksLoaded.get(currentBlock).getBlockOrderNumber();
                    String blockReportName = "#" + blockOrder + " " + blockName;

                    int blockWait = blocksLoaded.get(currentBlock).getWait() > 0
                            ? blocksLoaded.get(currentBlock).getWait()
                            : 2;

                    boolean blockActive = blocksLoaded.get(currentBlock).getActive();

                    if (blockActive) {
                        excelFieldName = blockLoad.getExportFile();
                    }

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

                    // Step 1: Get all ParentIds For LOOPs Filter rows where actions = "REFRESH_LOOP" or "LOOP" on
                    // current
                    // Block
                    parentIdsForLoop = performActions.getParentIdsForLoop(
                            blocksLoaded.get(currentBlock).getInstructionLoadDTOS());

                    // Step 2: Get all Conditional By parentId for Index Locator on current Block Relocate "IF",
                    // "ELSEIF",
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

                    while (success && xExcelCurrentRow < extractedData.getNumberOfDataRows() && !stopAll) {
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

                            InstructionLoadDTO currentInstruction =
                                    blockLoad.getInstructionLoadDTOS().get(currentIndex);

                            byPassFlagLoop = parentIdsForLoop.contains(currentInstruction.getId());

                            mainMsg =
                                    currentInstruction.getOptional() ? "OPTIONAL INSTRUCTION" : "MANDATORY INSTRUCTION";

                            if (!currentInstruction.getInstructionActive()) {

                                String nameInstruc =
                                        "(" + currentInstruction.getId() + ") " + currentInstruction.getName();
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
                            boolean execCheckValue = false;
                            boolean execOutPut = false;
                            boolean excelWriteOperation = false;
                            boolean pauseOperation = false;

                            String xPathOperation = null;
                            String[] parentActions = null;
                            String parentField = null;
                            String parentFieldLoop = null;
                            String variableField = null;
                            String localFormat = null;
                            delimiterCSV = null;
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
                                        homeBanking.getId(), sessionRowStatus, jsonStatus, "rowStatus");
                            } else {
                                // Previous
                                rowStatus.setColor("green"); // #1d9c06 green
                                jsonStatus = gson.toJson(rowStatus);
                                webSocketSessionManager.sendMessageJson(
                                        homeBanking.getId(), sessionRowStatus, jsonStatus, "rowStatus");
                                try {
                                    Thread.sleep(300);
                                } catch (Exception e) {
                                }
                                // Current
                                rowStatus.setInstructionId(currentInstruction.getId());
                                rowStatus.setColor("yellow"); // #fcba03 deep carmine yellow
                                jsonStatus = gson.toJson(rowStatus);
                                webSocketSessionManager.sendMessageJson(
                                        homeBanking.getId(), sessionRowStatus, jsonStatus, "rowStatus");
                            }

                            //                        String[] operation =
                            // UtilsMethods.splitIfContains(instruction.getOperation(),
                            // ARConstants.ACTION_SPECIFICATIONS_SPLITTER);
                            String[] actions =
                                    currentInstruction.getActions().split(ARConstants.ACTION_SPECIFICATIONS_SPLITTER);
                            String[] operations = currentInstruction.getOperation() != null
                                    ? currentInstruction
                                            .getOperation()
                                            .split(ARConstants.ACTION_SPECIFICATIONS_SPLITTER)
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
                            String valueInsert = "CHANGE ME";
                            if (actions[0].equals(ARConstants.INSERT) && actions[1].equals(ARConstants.ENTER)) {
                                String reference = actions[2];
                                valueInsert = dataExcel.get(reference);
                            } else if (actions[0].equals(ARConstants.INSERT)) {
                                String reference = actions[1];
                                valueInsert = dataExcel.get(reference);
                            }

                            Pair<String, String> msgInstruction = null;
                            if (actions[0].equalsIgnoreCase(ARConstants.EXCEL_GOTO)) {

                                //                                currentIndex++;
                                continue instructionLoop;

                            } else if (actions[0].equalsIgnoreCase(ARConstants.NEXT_ROW)) {
                                // <currentId:blockId:blockOrderNumber:bockName>
                                xExcelCurrentRow++;

                                String bodyMsg = "Excel Data Calling Next Row: " + xExcelCurrentRow + 1;

                                if (xExcelCurrentRow >= xExcelDataSize - 1) {
                                    xExcelCurrentRow = xExcelDataSize - 1;
                                    msgInstruction = new Pair<>(
                                            "Excel Data limit reached keeping", String.valueOf(xExcelCurrentRow + 1));
                                    bodyMsg = "Excel Data limit reached keeping: " + xExcelCurrentRow + 1;
                                } else {
                                    msgInstruction =
                                            new Pair<>("Excel Data next row", String.valueOf(xExcelCurrentRow + 1));
                                }

                                // Excel Report and Log
                                performActions.logAndReport(
                                        currentCondition,
                                        true,
                                        true,
                                        blockStartTime,
                                        blockReportName,
                                        success,
                                        new String[] {ARConstants.NEXT_ROW},
                                        msgInstruction,
                                        dataExcel,
                                        writerReport,
                                        "Excel Data Calling Next Row",
                                        bodyMsg);

                                //                                currentIndex++;
                                continue instructionLoop;

                            } else if (actions[0].equalsIgnoreCase(ARConstants.GOTO)) {
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
                                            msgInstruction.getKey(),
                                            String.valueOf(mapLoops.get(msgInstruction.getKey())));
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
                                    String[] parts = msgInstruction.getValue().split(":"); // Split by ':'
                                    mapLoops.put(msgInstruction.getKey(), Integer.valueOf(parts[1])); // Loop Times
                                    mapRefresh.put(msgInstruction.getKey(), Integer.valueOf(parts[0])); // Wait Time
                                } else if (mapLoops.containsKey(msgInstruction.getKey())) {
                                    // Updates the msgInstruction
                                    msgInstruction = new Pair<>(
                                            msgInstruction.getKey(),
                                            String.valueOf(mapLoops.get(msgInstruction.getKey())));
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
                                                : (actions[0].equalsIgnoreCase(ARConstants.INSERT))
                                                        ? valueInsert
                                                        : ""));
                            } else {
                                msgInstruction = new Pair(
                                        "(" + currentInstruction.getId() + ")-" + currentInstruction.getName(),
                                        (currentInstruction.getOperation() != null
                                                ? currentInstruction.getOperation()
                                                : (actions[0].equalsIgnoreCase(ARConstants.INSERT))
                                                        ? valueInsert
                                                        : ""));
                            }

                            resultActions = performActions.actionResultMessage(blockName, actions, msgInstruction);

                            if (actions[0].equalsIgnoreCase(ARConstants.PAUSE)) {
                                pauseOperation = true;

                                respModal = performMessage.showCustomModalDialogDragWin11(
                                        "PAUSE BOT JOB",
                                        "PAUSED at Block Name",
                                        blockLoad.getName(),
                                        " Please click OK to continue!",
                                        null,
                                        false,
                                        "Continue",
                                        "Stop all",
                                        0);
                            }

                            if (actions[0].equalsIgnoreCase(ARConstants.LOOP)) {
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

                            } else if (actions[0].equalsIgnoreCase(ARConstants.REFRESH_ONLY)) {
                                refreshOnly = true;
                            } else if (actions[0].equalsIgnoreCase(ARConstants.REFRESH_LOOP)) {
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
                            } else if (actions[0].equalsIgnoreCase(ARConstants.GET_VALUE)
                                    || actions[0].equalsIgnoreCase(ARConstants.SET_VALUE)) {

                                execGetOrSet = true;

                                xPathOperation = performActions.getXPathInstruction(currentInstruction, blockLoad);
                                String actionsParent =
                                        performActions.getInstructionParentActions(currentInstruction, blockLoad);
                                parentActions = actionsParent != null
                                        ? actionsParent.split(ARConstants.ACTION_SPECIFICATIONS_SPLITTER)
                                        : null;

                                parentField = performActions.getInstructionParentField(currentInstruction, blockLoad);
                                variableField =
                                        performActions.getInstructionVariableField(currentInstruction, variablesLoaded);
                                localFormat = performActions.getInstructionVariableFormat(
                                        currentInstruction, variablesLoaded);
                                if (variableField == null) {
                                    variableField = "Not Variable defined";
                                }

                            } else if (actions[0].equalsIgnoreCase(ARConstants.OUTPUT)) {
                                execOutPut = true;
                                fieldName = currentInstruction.getId() + "-" + currentInstruction.getName();
                            } else if (actions[0].equalsIgnoreCase(ARConstants.CHECK_VALUE)) {
                                execCheckValue = true;
                                parentField = performActions.getInstructionParentField(currentInstruction, blockLoad);
                                variableField =
                                        performActions.getInstructionVariableField(currentInstruction, variablesLoaded);
                                if (variableField == null) {
                                    variableField = "Not Variable defined";
                                }
                            } else if (actions[0].equalsIgnoreCase(ARConstants.EXTRACT_FIELD)) {
                                excelWriteOperation = true;
                                parentField = performActions.getInstructionParentField(currentInstruction, blockLoad);
                                variableField =
                                        performActions.getInstructionVariableField(currentInstruction, variablesLoaded);
                                delimiterCSV = performActions.getInstructionVariableDelimiter(
                                        currentInstruction, variablesLoaded);
                                if (variableField == null) {
                                    variableField = "Not Variable defined";
                                }
                            }

                            File logFileForSingleExcel = excelReader.createLogFile(excelPath);

                            try {
                                if (jumpGoto) {

                                    if (jumpGotoError) {
                                        success = false;
                                        failedMessage = "Failed: GO TO";
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

                                                currentBlock = blockOrderNumber - 1;
                                                currentInstruction.setExecuted(true);

                                                success = true;

                                            } catch (Exception ex) {
                                                failedMessage = "Failed: GO TO";
                                                msgInstruction = updateMSGInstruction(msgInstruction, failedMessage);

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

                                            ARLogger.getInstance(ARScannedElementPane.class)
                                                    .info(String.format(
                                                            "Loop to Parent :\"%s\" - %d Times",
                                                            parts[0] + "-(" + parts[1] + ") " + parts[2],
                                                            mapLoops.get(parentFieldLoop)));

                                            if (refreshLoop) {

                                                String extraLog = performActions.actionResultMessage(
                                                        blockName,
                                                        new String[] {ARConstants.REFRESH_HOLD},
                                                        msgInstruction);

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
                                                        blockName,
                                                        new String[] {ARConstants.REFRESH_ONLY},
                                                        msgInstruction);

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
                                                    finalLogMessage(failedMessage, resultActions));

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

                                    resultActions = "Refresh Current Web Page ->  inside Block :\""
                                            + blockLoad.getName() + "\"";

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

                                    webElementWork = true;

                                    // Extract dataFieldName and dataFieldValue using a separate method
                                    Pair<String, String> fieldData = performActions.extractFieldData(
                                            dataExcel,
                                            actions,
                                            currentInstruction.getDefaultValue(),
                                            currentInstruction.getCodified());

                                    WebElement webElementFound = null;
                                    boolean forceCoordinates = currentInstruction.getForceCoordinates() != null
                                            && currentInstruction.getForceCoordinates();
                                    try {
                                        webElementFound = performActions.searchElement(
                                                currentInstruction, botJobId, forceCoordinates);
                                    } catch (Exception ex) {
                                        success = false;
                                    }

                                    if (webElementFound == null && forceCoordinates) {

                                        Boolean pressEnterAfter = false;
                                        if (actions[0].equals(ARConstants.INSERT)
                                                && actions[1].equals(ARConstants.ENTER)) {
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

                                    byPassNotFound = byPassFlagLoop
                                            || !currentCondition.equals(ARConstants.ConditionStatus.NONE);

                                    if (webElementFound != null && success) {

                                        success = performActions.performWebActions(
                                                byPassNotFound,
                                                mapSavedLocators.get("coordinates"),
                                                fieldData,
                                                currentInstruction,
                                                mapOperators,
                                                webElementFound,
                                                actions);

                                        if (execOutPut) {
                                            if (mapOperators.containsKey(fieldName)) {
                                                msgInstruction = new Pair(fieldName, mapOperators.get(fieldName));
                                            } else {
                                                msgInstruction = new Pair(fieldName, "TEXT OUTPUT NOT FOUND");
                                            }
                                        }
                                    }
                                    // Special Cases for Select Responses
                                    // It could be Improved the case
                                    if (resultActions.contains("Error:")
                                            || (webElementFound == null && !forceCoordinates)) {
                                        failedMessage = "Failed execution Web Element";
                                        msgInstruction = updateMSGInstruction(msgInstruction, failedMessage);
                                        success = false;
                                    } else if (resultActions != null && success) {
                                        currentInstruction.setExecuted(true);
                                    }

                                } else if (execGetOrSet) {
                                    // GET && SET Special Operators

                                    if (parentField != null && parentId != 0) {
                                        parentField = parentId + "-" + parentField;
                                    }
                                    // Mandatory for GET_VALUE
                                    if (xPathOperation == null && actions[0].equalsIgnoreCase(ARConstants.GET_VALUE)) {
                                        failedMessage = "Parent Id in Wrong Block";
                                        msgInstruction = updateMSGInstruction(msgInstruction, failedMessage);
                                        resultActions = performActions.parentIdWrongBlock(
                                                currentInstruction, blockLoad, resultActions, currentCondition);
                                        success = false;
                                    } else if (parentField == null) {
                                        failedMessage = "Parent Id in Wrong Block";
                                        msgInstruction = updateMSGInstruction(msgInstruction, failedMessage);
                                        resultActions = performActions.parentIdWrongBlock(
                                                currentInstruction, blockLoad, resultActions, currentCondition);
                                        success = false;
                                    } else {

                                        resultActions = performActions.performOperatorActions(
                                                byPassNotFound,
                                                currentInstruction,
                                                xPathOperation,
                                                parentActions,
                                                actions[0],
                                                operations,
                                                parentField,
                                                variableField,
                                                mapOperators);

                                        if (resultActions.contains("Error:")) {
                                            failedMessage = "Failed: Operation (GetValue / SetValue)";
                                            msgInstruction = updateMSGInstruction(msgInstruction, failedMessage);
                                            success = false;
                                        } else {
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
                                        failedMessage = "Get Value Is Not Defined";
                                        msgInstruction = updateMSGInstruction(msgInstruction, failedMessage);
                                        resultActions = performActions.getValueIsNotDefined(
                                                actions[0],
                                                currentInstruction,
                                                resultActions,
                                                ARConstants.ConditionStatus
                                                        .NONE, // NOT  currentCondition to Force Message,
                                                parentField,
                                                variableField);

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
                                            success = true;
                                        } else {
                                            failedMessage = "Failed: Check Validation";
                                            msgInstruction = updateMSGInstruction(msgInstruction, failedMessage);
                                            resultActions = performActions.checkValidationFailed(
                                                    invalidValues,
                                                    parentField,
                                                    mapOperators.get(variableField),
                                                    resultActions,
                                                    operations,
                                                    currentCondition,
                                                    byPassNotFound);

                                            success = false;
                                        }
                                    }

                                } else if (excelWriteOperation) {
                                    // Excel Write Operator

                                    if (parentField == null) {
                                        failedMessage = "Parent Id in Wrong Block";
                                        msgInstruction = updateMSGInstruction(msgInstruction, failedMessage);
                                        resultActions = performActions.parentIdWrongBlock(
                                                currentInstruction, blockLoad, resultActions, currentCondition);

                                        success = false;

                                    } else if (!mapOperators.containsKey(variableField)) {
                                        failedMessage = "Get Value Is Not Defined";
                                        msgInstruction = updateMSGInstruction(msgInstruction, failedMessage);
                                        resultActions = performActions.getValueIsNotDefined(
                                                actions[0],
                                                currentInstruction,
                                                resultActions,
                                                ARConstants.ConditionStatus
                                                        .NONE, // NOT  currentCondition to Force Message,
                                                parentField,
                                                variableField);

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

                                            resultActions = "insertValueFieldNameInExcel -> " + variableField + "-"
                                                    + mapOperators.get(variableField);
                                        } else {
                                            resultActions = "NO Export Excel File defined -> " + variableField + "-"
                                                    + mapOperators.get(variableField);
                                        }

                                        if (mapExportRows.size() == 0) {
                                            //
                                            // writerExport.insertBlockSeparation(blockLoad.getName());
                                            //                                            exportIndex *= 2;
                                        }

                                        // Insert the updated mapExport into the Excel after each instruction
                                        if (writerExport != null) {
                                            headersExport.add(parentField.trim());
                                            mapExportRows.put(
                                                    parentField.trim(),
                                                    mapOperators
                                                            .get(variableField)
                                                            .trim());

                                            //                                            addRowFromMap(mapExportRows);
                                            if (excelFieldName != null
                                                    && excelFieldName
                                                            .toLowerCase()
                                                            .endsWith(".csv")) {
                                                if (Strings.isNullOrEmpty(delimiterCSV)) {
                                                    delimiterCSV = ",";
                                                }

                                                //
                                                //                                                String csvContent =
                                                // getBancaStatoCsvContent(delimiterCSV);
                                                //
                                                // writeToFile(excelFieldName, csvContent);

                                                // writerExport.writeMapToCSV(mapExport, excelFieldName, delimiterCSV);
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
                                            success = true;
                                        } else {
                                            failedMessage = "Failed: Generate File -> Excel/CSV";
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
                                    failedMessage = "Failed: General Execution";
                                    msgInstruction = updateMSGInstruction(msgInstruction, failedMessage);
                                }

                                performMessage.errorMessage(resultActions, msg1, msg2, msg3, null, 260);
                                //                            throw new RuntimeException(t);
                            }

                            printLog(
                                    generateTimestamp(),
                                    logFileForSingleExcel,
                                    finalLogMessage(failedMessage, resultActions),
                                    success);

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
                                    finalLogMessage(failedMessage, resultActions));

                            if (pauseOperation && respModal.equals(ARConstants.DialogModal.STOP)) {

                                String nameInstruc =
                                        "(" + currentInstruction.getId() + ") " + currentInstruction.getName();

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
                            if (!success
                                    && !byPassFlagLoop
                                    && currentCondition.equals(ARConstants.ConditionStatus.NONE)) {
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

                        // Has Transversed All Columns in the Block
                        // Way Out from the Current Excel Data Row to another Block keeping the Same Excel Data Row
                        break;
                    }
                    currentBlock++;
                }

                currentBlock = blockInitial;
                xExcelCurrentRow++;
                addRowFromMap(mapExportRows);
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
                } else {
                    //
                    //                    writerExport.insertFieldNameAndValueLastColumn(mapExportRows, exportIndex -
                    // 1);
                    writerExport.insertCSVContentIntoExcel(columnsCSV, rowsCSV, exportIndex - 1);
                }
            }
        } else { //  if dataExel is NULL
            // Creating Dynamic Data if Default is Null
            Pair<String, String> dataDynamic = null;
            for (int j = 0; success && j < blocksLoaded.size(); j++) {

                for (InstructionLoadDTO currentInstruction : blocksLoaded.get(j).getInstructionLoadDTOS()) {
                    if (currentInstruction.getDefaultValue() == null) {
                        String[] arr = UtilsMethods.splitIfContains(
                                currentInstruction.getActions(), ARConstants.ACTION_SPECIFICATIONS_SPLITTER);
                        if (arr.length > 1) {
                            String dataFieldName = arr[1].split(ARConstants.PATH_FIELD_SUBSTITUTION)[0];
                            PerformActions.insertRandomName(dataFieldName);
                        }
                    }
                }
            }
            for (int j = 0; success && j < blocksLoaded.size(); j++) {

                String blockName = blocksLoaded.get(j).getName();
                int blockOrder = blocksLoaded.get(j).getBlockOrderNumber();
                String blockReportName = "#" + blockOrder + " " + blockName;

                for (InstructionLoadDTO currentInstruction : blocksLoaded.get(j).getInstructionLoadDTOS()) {

                    long currentInstructionStartTime = System.nanoTime();
                    File logFileForSingleExcel = excelReader.createLogFile(excelPath);

                    String[] actions = currentInstruction.getActions().split(ARConstants.ACTIONS_AND_PATHS_SPLITTER);

                    // Case for Inputs
                    String valueInsert = "CHANGE ME";
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
                                    finalLogMessage(failedMessage, resultActions));

                            continue;
                        }

                        WebElement webElementFound = null;
                        boolean forceCoordinates = currentInstruction.getForceCoordinates() != null
                                && currentInstruction.getForceCoordinates();

                        try {
                            webElementFound =
                                    performActions.searchElement(currentInstruction, botJobId, forceCoordinates);
                        } catch (Exception ex) {
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
                            failedMessage = "Failed: Execution";
                            msgInstruction = updateMSGInstruction(msgInstruction, failedMessage);

                            resultActions = currentInstruction.getName();
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
                                finalLogMessage(failedMessage, resultActions));

                    } catch (Throwable t) {
                        success = false;
                        currentInstruction.setExecuted(false);

                        failedMessage = "Failed: ";
                        msgInstruction = updateMSGInstruction(msgInstruction, failedMessage);

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
                                finalLogMessage(failedMessage, resultActions));

                        //                        throw new RuntimeException(t);
                    }
                    printLog(
                            generateTimestamp(),
                            logFileForSingleExcel,
                            finalLogMessage(failedMessage, resultActions),
                            success);
                }
            }
        }
        launchBotJobButton.setDisable(false);

        totalExecutionTime = performActions.getTotalExecutionTime();

        if (totalExecutionTime == 0) {
            writerReport.insertTotalExecutionTimes(botJobStartTime, botJobStartTime);
        } else {
            writerReport.insertTotalExecutionTimes(botJobStartTime, System.nanoTime());
        }

        // PRINT END BASE LOG//

        if (success) {
            baseLogString = blocksLoaded.get(0).getName()
                    + ARConstants.FIELDS_SEPARATOR
                    + labelsValue.getProperty(Labels.END)
                    + ARConstants.FIELDS_SEPARATOR
                    + labelsValue.getProperty(Labels.OK);

            if (!isInterceptBotJob()) {
                rowStatus.setColor("green"); // #1d9c06 deep carmine green
                jsonStatus = gson.toJson(rowStatus);
                webSocketSessionManager.sendMessageJson(homeBanking.getId(), sessionRowStatus, jsonStatus, "rowStatus");

                performMessage.showCustomModalDialogDragWin11(
                        "Bot-Job Finished - successfully",
                        botJobName,
                        "Last Execution:",
                        resultActions,
                        null,
                        false,
                        "OK",
                        null,
                        300);
            } else {
                rowStatus.setColor("yellow"); // #fcba03 deep carmine yellow
                jsonStatus = gson.toJson(rowStatus);
                webSocketSessionManager.sendMessageJson(homeBanking.getId(), sessionRowStatus, jsonStatus, "rowStatus");

                performMessage.showCustomModalDialogDragWin11(
                        "Bot-Job Interrupted successfully",
                        botJobName,
                        "Last Execution:",
                        resultActions,
                        null,
                        false,
                        "OK",
                        null,
                        300);
            }

            performActions.setInterceptBotJob(false);
            setInterceptBotJob(false);
            isJobRunning.set(false);

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

            if (isInterceptBotJob()) {
                rowStatus.setColor("yellow"); // #fcba03 deep carmine yellow
                jsonStatus = gson.toJson(rowStatus);
                webSocketSessionManager.sendMessageJson(homeBanking.getId(), sessionRowStatus, jsonStatus, "rowStatus");

                performMessage.showCustomModalDialogDragWin11(
                        "Bot-Job Interrupted successfully",
                        botJobName,
                        "Last Execution:",
                        resultActions,
                        null,
                        false,
                        "OK",
                        null,
                        300);
            } else if (webElementWork) {

                rowStatus.setColor("red"); // #FF3131 deep carmine red
                jsonStatus = gson.toJson(rowStatus);
                webSocketSessionManager.sendMessageJson(homeBanking.getId(), sessionRowStatus, jsonStatus, "rowStatus");

                performMessage.errorMessage(
                        "Failed finding element (5 attempts).",
                        "Use \"Force Coordinates\" in some cases.",
                        !Strings.isNullOrEmpty(failedMessage) ? failedMessage : "Failed:",
                        "Last Execution:",
                        resultActions,
                        350);
            } else {

                rowStatus.setColor("red"); // #FF3131 deep carmine red
                jsonStatus = gson.toJson(rowStatus);
                webSocketSessionManager.sendMessageJson(homeBanking.getId(), sessionRowStatus, jsonStatus, "rowStatus");

                performMessage.errorMessage(
                        "Process Execution Terminated",
                        !Strings.isNullOrEmpty(failedMessage) ? failedMessage : "Failed:",
                        "Last Execution:",
                        resultActions,
                        null,
                        350);
            }
        }
        printBaseLog(baseLogFile, generateTimestamp(), baseLogString);

        shutDownExecutorService(executorServicePreLaunch);
        performActions.setInterceptBotJob(true);
        setInterceptBotJob(false);
        isJobRunning.set(false);
        return true;
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
                    System.err.println("ExecutorService did not terminate");
                    ARLogger.getInstance(ARWebDriver.class).severe("ExecutorService did not terminate");
                }
            }
        } catch (InterruptedException error) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
            ARLogger.getInstance(ARWebDriver.class).severe("ExecutorService did not terminate\n" + error.getMessage());
        }
    }

    private void clearFields() {
        coordsTextField.setText("");
        countdownTextField.setText("Pre-Launch status: Ready");
        countdownTextField.setStyle("-fx-font-size: 12px; -fx-text-fill: blue;");
        mainPane.requestLayout();
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

            if (iframeElementsObject instanceof List<?> iframeElementsList) {
                // Convert List to String[]
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
        executorServicePreLaunch.shutdown();
        try {
            if (!executorServicePreLaunch.awaitTermination(5, TimeUnit.SECONDS)) {
                executorServicePreLaunch.shutdownNow();
                if (!executorServicePreLaunch.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.err.println("ExecutorService did not terminate");
                    ARLogger.getInstance(ARWebDriver.class).severe("ExecutorService did not terminate");
                }
            }
        } catch (InterruptedException e) {
            executorServicePreLaunch.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void Close() {
        ARLogger.getInstance(ARScannedElementPane.class).finer("ARScannedElementPane Close()");
        Platform.runLater(() -> {
            Stage stage = (Stage) mainPane.getScene().getWindow();
            stage.close();
        });
    }

    private static void showAlertInfo(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
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
            String tagName,
            String shadowHost,
            String shadowRoot,
            String cssSelector,
            Integer currentBotJobId,
            Integer currentBlockId,
            boolean updateRow) {

        InstructionLoadDTO instructionLoadDTO = new InstructionLoadDTO();

        instructionLoadDTO.setXpath(xPath);
        instructionLoadDTO.setCoordinates(coordinates);
        instructionLoadDTO.setForceCoordinates(forceCoordinates);
        instructionLoadDTO.setIFrameXPath(iFrameXPath);

        instructionLoadDTO.setTagName(tagName);
        instructionLoadDTO.setShadowHost(shadowHost);
        instructionLoadDTO.setShadowRoot(shadowRoot);
        instructionLoadDTO.setCssSelector(cssSelector);

        instructionLoadDTO.setName(name);

        instructionLoadDTO.setCodified(false);

        instructionLoadDTO.setInstructionOrderNumber(instructionOrderNumber);

        instructionLoadDTO.setOptional(false);

        //        InstructionLoadDTO.setOperation(operation);
        instructionLoadDTO.setActions(actions);
        instructionLoadDTO.setDescription(description);

        instructionLoadDTO.setVariableId(varId);

        instructionLoadDTO.setActionCustomMaxWaitSec(30);
        instructionLoadDTO.setOnHoldSeconds(onHold);
        //        InstructionLoadDTO.setBlock(savedBlockDTO);
        instructionLoadDTO.setExportToABR(exportToAR);
        instructionLoadDTO.setInstructionActive(true);

        // Wrap the persistence in a try-catch block
        int newId = -1;

        try {
            if (!updateRow) {
                newId = performDataBase.insertInstruction(
                        "botJobTasks", instructionLoadDTO, currentBotJobId, currentBlockId, homeBanking.getId());
            } else {
                newId = performDataBase.updateInstruction(
                        "botJobTasks", instructionLoadDTO, currentBotJobId, currentBlockId, homeBanking.getId());
            }

        } catch (Exception e) {

            ARLogger.getInstance(ARScannedElementPane.class)
                    .severe(String.format(
                            "Cannot Insert \"Instruction\"  \"%s\"\nCannot be saved!\nError: %s",
                            instructionLoadDTO.getName(), e.getMessage()));

            return -1;
        }
        return newId;
    }

    private void loadAllBlockItems(List<BlockLoadDTO> blockLoadDTOList) {
        blocksItems.clear();
        if (blockLoadDTOList.size() > 0) {
            blocksItems.add(new ComboBoxVars("Execute All Blocks", "", -1, -1, -1, -1, null, -1, null));
        } else {
            blocksItems.add(new ComboBoxVars("#1 Default Block", "Default Block", 1, 1, -1, -1, null, -1, null));
        }
        for (BlockLoadDTO block : blockLoadDTOList) {
            blocksItems.add(new ComboBoxVars(
                    block.getBlockOrderNumber() + "# " + block.getName(),
                    block.getName(),
                    block.getBlockOrderNumber(),
                    block.getId(),
                    -1,
                    -1,
                    null,
                    -1,
                    null));
        }
    }

    private Button createPathButton() {
        Button button = componentBuilder.buildButton(
                "", ARConstants.SPACE_L, ARConstants.ICON_REFRESH, ARConstants.SPACE_M, new Insets(3D));
        button.setMaxWidth(ARConstants.SPACE_L);
        AnchorPane.setRightAnchor(button, 0D);
        return button;
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
        String webDriverPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_WEBDRIVER);
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
        try {
            double num1 = Double.parseDouble(value1);
            double num2 = Double.parseDouble(value2);
            return num1 > num2 ? 1 : 0;
        } catch (NumberFormatException e) {
            // Handle non-numeric values (e.g., log an error, return false)
            return -1; // Or throw an exception
        }
    }

    private int handleLessThan(String value1, String value2) {
        try {
            double num1 = Double.parseDouble(value1);
            double num2 = Double.parseDouble(value2);
            return num1 < num2 ? 1 : 0;
        } catch (NumberFormatException e) {
            // Handle non-numeric values
            return -1; // Or throw an exception
        }
    }

    private String finalLogMessage(String failedMessage, String resultActions) {
        if (!Strings.isNullOrEmpty(failedMessage)) {
            return failedMessage + resultActions;
        }
        return resultActions;
    }

    private void checkRunningProcess() {
        checkCloneElement.setSelected(false);
        launchBotJobButton.setDisable(false);
        revertCloneInjections(performActions.getCurrentDriver());
        revertHoverPickInjections(performActions.getCurrentDriver());
        if (isJobRunning.get()) {
            setInterceptBotJob(true);
        }
    }

    private Pair<String, String> updateMSGInstruction(Pair<String, String> msgInstruction, String failedMessage) {
        String currentKey = msgInstruction.getKey();
        String updatedKey = failedMessage + " - " + currentKey;
        return new Pair<>(updatedKey, msgInstruction.getValue());
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

    private void setPayloadEmpty() {
        this.botJobLoadList = new ArrayList<>();
        BotJobLoadDTO botJobDTO = new BotJobLoadDTO();
        botJobDTO.setId(this.botJobLoad.getId() != null ? this.botJobLoad.getId() : 0);
        botJobDTO.setName(this.botJobLoad.getName() != null ? this.botJobLoad.getName() : "Bot Job Name Default");
        botJobDTO.setBlockLoadDTOList(new ArrayList<>());
        this.botJobLoadList.add(botJobDTO);

        this.payloadEmpty = new PayloadJson(this.botJobLoad.getId(), this.botJobLoad.getName(), 0);
    }

    /**
     * Adds a row with values matching the columns.
     * Missing values are filled with empty strings.
     * @param values Array of values; may be less than columns.
     */
    public void addRow(String... values) {
        if (columnsCSV.isEmpty()) {
            throw new IllegalStateException("Columns must be initialized before adding a row using values.");
        }

        List<String> row = new ArrayList<>();
        int maxCols = columnsCSV.size();

        for (int i = 0; i < maxCols; i++) {
            if (i < values.length) {
                row.add(values[i]);
            } else {
                row.add(""); // fill missing with empty string
            }
        }
        rowsCSV.add(row);
    }

    /**
     * Adds a row using a Map<String, String>. If this is the first row added,
     * it sets the column order based on the map's keys.
     */
    public void addRowFromMap(Map<String, String> map) {
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
        rowsCSV.add(row);
    }

    public String getCsvContent() {
        StringBuilder sb = new StringBuilder();
        sb.append("0: ").append(String.join(",", columnsCSV)).append("\n");

        int rowNumber = 1;
        for (List<String> row : rowsCSV) {
            sb.append(rowNumber).append(": ").append(String.join(",", row)).append("\n");
            rowNumber++;
        }
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
        for (List<String> row : rowsCSV) {
            sb.append("EXTERNAL_" + xRow)
                    .append(delimiter)
                    .append(String.join(delimiter, row))
                    .append("\n");
            xRow++;
        }

        //        sb.append(END_OF_FILE_MARKER);
        return sb.toString();
    }

    public void writeToFile(String filename, String content) {
        try (FileWriter writer = new FileWriter(filename)) {
            writer.write(content);
            System.out.println("CSV written to file: " + filename);
        } catch (IOException e) {
            System.err.println("Error writing file: " + e.getMessage());
        }
    }

    public void printCsv() {
        System.out.println(getCsvContent());
    }
}

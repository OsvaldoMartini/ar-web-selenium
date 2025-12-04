package com.allinweb.ch.component.scene;

import com.allinweb.ch.builder.WebElementTagNameEnum;
import com.allinweb.ch.component.TargetElementHelper;
import com.allinweb.ch.component.pane.ARViewBotJobPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.facade.*;
import com.allinweb.ch.model.*;
import com.allinweb.ch.socket.WebSocketSessionManager;
import com.allinweb.ch.util.*;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.swing.*;
import javax.websocket.*;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;

@ClientEndpoint
@Slf4j
public class ARViewBotJobScene extends ARScene {

    private static final ARPropertyManager arPropertyManager = ARPropertyManager.getInstance();
    private static final WebSocketSessionManager webSocketSessionManager = WebSocketSessionManager.getInstance();
    private static final PerformLists performLists = PerformLists.getInstance();
    private static final PerformDBEngine performDBEngine = PerformDBEngine.getInstance();
    private static final PerformDataBase performDataBase = PerformDataBase.getInstance();
    private static final ARViewBotJobPane arViewBotJobPane = ARViewBotJobPane.getInstance();
    private static final PerformMessage performMessage = PerformMessage.getInstance();
    private static final TargetElementHelper targetElementHelper = TargetElementHelper.getInstance();

    private final Gson gson = new Gson();

    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static final CountDownLatch latch = new CountDownLatch(1);

    private static final int SCENE_HEIGHT = 600;
    private static final int SCENE_WIDTH = 1100;
    private static final String TITLE = "Bot Job Details";

    protected static volatile ARViewBotJobScene instance;

    //    private static ARNewCommandScene arNewCommandScene = ARNewCommandScene.getInstance();

    private JDialog modalDialog;
    private boolean isEnabledLicence;
    private int portSocketInitial = 54525;
    private boolean isConnectWebSocket = false;
    private Session session;
    private ExecutorService executorWebSocket = Executors.newSingleThreadExecutor();
    private ARWebDriver arWebDriver;
    private BotJobLoadDTO selectedBotJob;
    private int currentBlockId;
    private List<InstructionLoad> instructionList = new ArrayList<>();

    private ARViewBotJobScene() {
        super();
    }

    public static ARViewBotJobScene getInstance() {
        if (instance == null) {
            synchronized (ARViewBotJobScene.class) {
                if (instance == null) {
                    instance = new ARViewBotJobScene();
                }
            }
        }
        return instance;
    }

    public void initialize(ARWebDriver arWebDriver, BotJobLoadDTO selectedBotJob, boolean isEnabledLicence) {
        this.isEnabledLicence = isEnabledLicence;
        this.arWebDriver = arWebDriver;
        this.selectedBotJob = selectedBotJob;

        String port = arPropertyManager.getProperty(ARPropertyEnum.PORT_SOCKET);
        if (!Strings.isNullOrEmpty(port)) {
            portSocketInitial = Integer.parseInt(port);
        }

        //        if (!arNewCommandScene.isConnectWebSocket) {
        //            arNewCommandScene.connectWebSocketClient(portSocketInitial, "new-command-scene");
        //        }

        if (!isConnectWebSocket) {
            connectWebSocketClient(portSocketInitial, "bot-job-scene");
        }
    }

    @Override
    public IARPane buildPane() {
        return arViewBotJobPane;
    }

    @Override
    public int getSceneHeight() {
        return SCENE_HEIGHT;
    }

    @Override
    public int getSceneWidth() {
        return SCENE_WIDTH;
    }

    @Override
    public String getTitle() {
        if (selectedBotJob.getId() != null) {
            return TITLE + " WebSite Id: " + selectedBotJob.getHomeBankingId() + " Id: " + selectedBotJob.getId();
        }
        return TITLE;
    }

    public void showModal(Frame parent) {
        arViewBotJobPane.initialize(this, selectedBotJob, isEnabledLicence);

        if (modalDialog == null) {
            modalDialog = new JDialog(parent, getTitle(), true);
            modalDialog.setSize(getSceneWidth(), getSceneHeight());
            modalDialog.setAlwaysOnTop(true);
            modalDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

            modalDialog.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    handleCloseRequest();
                }
            });

            IARPane pane = buildPane();
            if (pane != null) {
                modalDialog.setContentPane(pane.createPane());
            } else {
                log.error("Failed to build pane for modal.");
                return;
            }
        }

        modalDialog.setTitle(getTitle());

        SwingUtilities.invokeLater(() -> modalDialog.setVisible(true));
    }

    public void closeModal() {
        if (modalDialog != null) {
            SwingUtilities.invokeLater(() -> modalDialog.dispose());
            modalDialog = null;
        }
    }

    private void handleCloseRequest() {
        log.info("Handle Close: Exiting Threads and Quitting WebDriver");
        threadList.forEach(this::interruptThread);
        closeWebDrivers();
    }

    private void closeWebDrivers() {
        for (WebDriver driver : arWebDriver.getWebDriverList()) {
            try {
                driver.quit();
                log.info("WebDriver closed.");
            } catch (Exception e) {
                log.warn("Error closing WebDriver: " + e.getMessage());
            }
        }
        SwingUtilities.invokeLater(() -> arWebDriver.getWebDriverList().clear());
    }

    private void stopKeepAlivePings() {
        scheduler.shutdownNow();
    }

    private void startKeepAlivePings() {
        scheduler.scheduleAtFixedRate(
                () -> {
                    try {
                        if (session != null && session.isOpen()) {
                            session.getBasicRemote().sendText("ping-bot-job-scene");
                        }
                    } catch (IOException e) {
                        log.error("Error sending ping: " + e.getMessage());
                    }
                },
                0,
                15,
                TimeUnit.SECONDS);
    }

    @OnMessage
    public void onMessage(String message) {
        log.info("Received: " + message);
        if (message == null || message.trim().isEmpty() || message.contains("CONNECT") || message.contains("ping")) {
            return;
        }

        String type = null;
        String body = null;

        try {
            JsonObject jsonObjMSG = JsonParser.parseString(message).getAsJsonObject();
            body = jsonObjMSG.has("body") ? jsonObjMSG.get("body").getAsString() : "unknown";

            if (!body.equalsIgnoreCase("unknown")) {
                JsonObject objSecond = JsonParser.parseString(body).getAsJsonObject();
                if (objSecond.has("type") && objSecond.get("type").getAsString().equalsIgnoreCase("CLOSE_BROWSER")) {
                    type = "CLOSE_BROWSER";
                } else if (objSecond.has("type")) {
                    type = objSecond.get("type").getAsString();
                } else {
                    type = jsonObjMSG.has("type") ? jsonObjMSG.get("type").getAsString() : "unknown";
                }
            } else {
                type = jsonObjMSG.has("type") ? jsonObjMSG.get("type").getAsString() : "unknown";
            }

            if (type == null || type.trim().isEmpty() || type.contains("CONNECT") || type.contains("ping")) {
                return;
            }

            SplitDTO splitDTO = parseSplitDTO(jsonObjMSG);

            switch (type) {
                case "NEW_ELEMENT_DTO":
                case "SEND_ALL_ELEMENTS_DTO":
                    boolean isMany = "SEND_ALL_ELEMENTS_DTO".equalsIgnoreCase(type);
                    stepsInsertManyDTO(splitDTO, isMany);
                    break;
                default:
                    break;
            }
        } catch (Exception error) {
            log.error("Error processing message: " + error.getMessage());
        }
    }

    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        latch.countDown();
        log.info("Connected to WebSocket server at: " + session.getRequestURI());
        sendMessage("Hello from Swing WebSocket client!");
    }

    @OnClose
    public void onClose(Session session) {
        log.info("Connection closed.");
        stopKeepAlivePings();
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        log.info("Error: " + throwable.getMessage());
        stopKeepAlivePings();
    }

    public void sendMessage(String message) {
        executorWebSocket.submit(() -> {
            if (session != null && session.isOpen()) {
                try {
                    session.getBasicRemote().sendText(message);
                } catch (Exception e) {
                    log.error("Error sending WebSocket message: " + e.getMessage());
                }
            }
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
                log.error("WebSocket connection failed: " + e.getMessage());
            }
        });
    }

    private SplitDTO parseSplitDTO(JsonObject jsonEntry) {
        if (jsonEntry == null || jsonEntry.isEmpty()) {
            log.warn("parseSplitDTO called with null or empty JSON object");
            return null;
        }
        try {
            if (jsonEntry.has("body")) {
                String bodyStr = jsonEntry.get("body").getAsString();
                JsonObject inner = gson.fromJson(bodyStr, JsonObject.class);
                return gson.fromJson(inner, SplitDTO.class);
            }
            return gson.fromJson(jsonEntry, SplitDTO.class);
        } catch (Exception error) {
            log.error("Cannot parse SplitDTO: " + error.getMessage() + " | JSON: " + jsonEntry);
            return null;
        }
    }

    // All remaining methods (stepsInsertManyDTO, createBlockIfNone, validateBlockDB,
    // prepareToInsertElementDTO, buildNewInstruction, buildAction, etc.) remain unchanged
    // as in your original JavaFX class.

    private void stepsInsertManyDTO(SplitDTO processDTO, boolean isMany) {
        currentBlockId = validateBlockDB("block", this.selectedBotJob.getId(), isMany);
        if (currentBlockId > 0) {
            performDataBase.loadInstructions(selectedBotJob.getId(), currentBlockId, -1, "instruction");
            List<InstructionLoad> instruc = performLists.getListInstruction();

            int nextOrder = instruc.size() + 1;

            if (!Objects.equals(selectedBotJob.getId(), processDTO.getBotJobId())) {
                performMessage.errorMessage(
                        "Bot Job Mismatch Detected",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Operation Failed ❌</span>",
                        "<span style='color: #E65100; font-weight: bold;'>The Bot Job sent from AR Mobile:</span> "
                                + "<span style='color: #1565C0; font-weight: bold;'>" + processDTO.getBotJobName()
                                + "</span><br>",
                        "<span style='color: #E65100; font-weight: bold;'>The Bot Job currently selected:</span> "
                                + "<span style='color: #1565C0; font-weight: bold;'>" + selectedBotJob.getName()
                                + "</span><br>",
                        "<span style='font-style: italic; color: #555;'>Please open the correct Bot Job before continuing.</span>",
                        0);
                return;
            }

            instructionList.clear();
            targetElementHelper.initialize(instance);

            for (ElementDTO elementDTO : processDTO.getElementDetails()) {
                TargetElement targetEach = targetElementHelper.extractPickClone(elementDTO, true);

                //                WebElement elementFound = performActions.findWebElement(targetEach);
                //                if (targetEach.getElement() == null && elementFound != null) {
                //                    targetEach.setElement(elementFound);
                //                }
                // 3 Different Coordinates
                // Original from JavaScript
                // WebDriver Selenium ElementFound
                // FallBack React Computed
                //                performActions.defineSavedReferenced(targetEach);

                //                if (!isMany) {
                //                    if (!Strings.isNullOrEmpty(arScannedElementPane
                //                            .defineNameField
                //                            .getText()
                //                            .trim())
                //                            && !targetEach
                //                            .getDefinedName()
                //                            .equalsIgnoreCase(arScannedElementPane
                //                                    .defineNameField
                //                                    .getText()
                //                                    .trim())) {
                //                        targetEach.setDefinedName(
                //                                arScannedElementPane.defineNameField.getText().trim());
                //                        Platform.runLater(() -> {
                //                            arScannedElementPane.defineNameField.clear();
                //                            arScannedElementPane.searchAttribValueField.clear();
                //                        });
                //                    }
                //                    arScannedElementPane.targetSelected = targetEach;
                //                    //                    itPrintsElementDTO();
                //                }

                prepareToInsertElementDTO(instructionList, currentBlockId, nextOrder, targetEach, true);
                nextOrder++;
            }

            if (instructionList.size() > 0) {

                ErrorMessage errorMessage = performDataBase.insertInstructionsBatch(
                        "botJobTasks",
                        instructionList,
                        selectedBotJob.getId(),
                        currentBlockId,
                        selectedBotJob.getHomeBankingId());

                if (instructionList.size()
                        != performDataBase.getIdsInstrucAfter().size()) {
                    log.error(
                            "Error Inserting ALL Elements - Expected (from list):{} - Actual (inserted): {}",
                            instructionList.size(),
                            performDataBase.getIdsInstrucAfter().size());
                    performMessage.errorMessage(
                            "Error Inserting ALL Elements",
                            "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Batch Insertion Failed ❌</span>",
                            "<span style='color: #E65100; font-weight: bold;'>Mismatch detected:</span> The number of inserted instructions does not match the expected size.",
                            "<span style='font-style: italic;'>Expected (from list):</span> " + instructionList.size(),
                            "<span style='font-style: italic;'>Actual (inserted):</span> "
                                    + performDataBase.getIdsInstrucAfter().size(),
                            0);

                    sendStatusButton("scannerGrid", "activate-insert-all", "Insert All Elements button activated");

                    //                    updateBotJobTasks();

                    return;
                }

                if (errorMessage == null) {
                    for (int i = 0; i < instructionList.size(); i++) {
                        InstructionLoad instruction = instructionList.get(i);
                        Integer newId = performDataBase.getIdsInstrucAfter().get(i);
                        instruction.setId(newId);
                    }

                    errorMessage = performDataBase.insertReferencesBatch(instructionList);
                }

                updateBotJobTasks(this.selectedBotJob.getId());
                sendStatusButton("scannerGrid", "activate-insert-all", "Insert All Elements button activated");

                if (errorMessage != null) {
                    performMessage.errorMessageOperationFailed(errorMessage);
                }
            }
        } else {
            sendStatusButton("scannerGrid", "activate-insert-all", "Insert All Elements button activated");
        }
    }

    private void sendStatusButton(String sessionId, String operationId, String message) {
        WebSocketSignal webSockteSocketSignal = WebSocketSignal.builder()
                .sessionId(sessionId)
                .operationId(operationId)
                .message(message)
                .build();

        String jsonData = gson.toJson(webSockteSocketSignal);

        webSocketSessionManager.sendMessageJson(selectedBotJob.getHomeBankingId(), sessionId, jsonData, operationId);
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
        } else {
            if (!performLists.getListBlock().isEmpty()) {
                return performLists.getListBlock().get(0).getId();
            }
        }
        return -1;
    }

    public int validateBlockDB(String blockTable, int whereId, boolean isMany) {
        int newBlockID = createBlockIfNone(blockTable, whereId);
        if (newBlockID > 0) {
            ErrorMessage errorMessage = performDataBase.loadBlocks(whereId, "", blockTable);
            if (errorMessage != null) {
                log.error(
                        "Error: {} Title: {} Message: {}",
                        errorMessage.getErrorHeader(),
                        errorMessage.getErrorTitle(),
                        errorMessage.getErrorMessage());
                return -1;
            }
        }

        if (newBlockID > 0) {
            currentBlockId = newBlockID;
        } else {
            currentBlockId = -1;
            if (currentBlockId < 0) {
                String insertOne = isMany ? "Insert ALL" : "Insert one Element";
                performMessage.errorMessage(
                        "Operation \"" + insertOne + "\" No Block Selected",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>No Block Selected ❌</span>",
                        "<span style='color: #E65100; font-weight: bold;'>You must select a Block from the dropdown list</span> before adding a new command.",
                        "<span style='font-style: italic;'>Context:</span> Bot Job: <b>" + "Default Block" + "</b>",
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

        String actionReq;
        String tagName = targetInsert.getTagName().toLowerCase();

        if (manyElements) {
            actionReq = tagName; // keep as before
        } else {
            switch (tagName) {
                case "input":
                case "textarea":
                    actionReq = ARConstants.INSERT;
                    break;
                case "select":
                case "button":
                case "a":
                case "link":
                    actionReq = ARConstants.CLICK;
                    break;
                case "label":
                    actionReq = ARConstants.OUTPUT;
                    break;
                default:
                    actionReq = ARConstants.CLICK;
                    break;
            }
        }

        //        targetInsert.setClickElement(checkClickElement.isSelected());
        WebElementTagNameEnum tagType = targetInsert.getTagType();
        //        if (checkForceEnterText.isSelected() && tagType.equals(WebElementTagNameEnum.INPUT)) {
        //            tagType = WebElementTagNameEnum.INPUT_ENTER;
        //        }

        Integer currentBotJobId = selectedBotJob.getId();

        InstructionLoad instruction = buildNewInstruction(tagType, actionReq, false, nextInstOrderNumber, targetInsert);

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

    public InstructionLoad buildNewInstruction(
            WebElementTagNameEnum forceTag,
            String actionReq,
            boolean identityHover,
            Integer orderNumber,
            TargetElement targetBuild) {

        InstructionLoad loop = new InstructionLoad();
        loop.setActionCustomMaxWaitSec(30);
        loop.setDescription("loop desc");
        loop.setCodified(false);
        loop.setInstructionOrderNumber(orderNumber);
        loop.setOptional(false);
        loop.setInstructionActive(true);
        loop.setXpath(targetBuild.getXPath());

        loop.setTagName(targetBuild.getTagName());
        loop.setShadowHost(targetBuild.getShadowHost());
        loop.setShadowRoot(targetBuild.getShadowRoot());
        loop.setCssSelector(targetBuild.getCssSelector());

        loop.setDefaultValue(targetBuild.getSearchAttributeValue());

        String action = buildAction(forceTag, actionReq, identityHover, targetBuild);
        loop.setActions(action);
        loop.setExportToABR(true);

        return loop;
    }

    private String buildAction(
            WebElementTagNameEnum forceTag, String actionReq, boolean identityHover, TargetElement targetBuild) {

        if (identityHover) {
            return handleIdentityHover(actionReq, forceTag, targetBuild.getNameLabel(), targetBuild.getClickElement());
        } else {
            return handleTargetBuildAction(
                    forceTag, targetBuild, targetBuild.getNameLabel(), targetBuild.getClickElement());
        }
    }

    private String handleIdentityHover(
            String actionReq, WebElementTagNameEnum forceTag, String nameLabel, Boolean clickElement) {
        return switch (actionReq.toUpperCase()) {
            case ARConstantsEngine.INSERT -> buildInsertAction(forceTag, nameLabel);
            case ARConstantsEngine.OUTPUT -> ARConstantsEngine.OUTPUT
                    + ARConstantsEngine.ACTION_SPECIFICATIONS_SPLITTER
                    + nameLabel;
            case ARConstantsEngine.OTHER -> ARConstantsEngine.OTHER
                    + ARConstantsEngine.ACTION_SPECIFICATIONS_SPLITTER
                    + nameLabel;
            case ARConstantsEngine.CLICK -> ARConstantsEngine.CLICK;
            default -> clickElement
                    ? ARConstantsEngine.CLICK
                    : ARConstantsEngine.INSERT + ARConstantsEngine.ACTION_SPECIFICATIONS_SPLITTER + nameLabel;
        };
    }

    private String buildInsertAction(WebElementTagNameEnum forceTag, String nameLabel) {
        if (forceTag.equals(WebElementTagNameEnum.INPUT_ENTER)) {
            return ARConstantsEngine.INSERT_ENTER + ARConstantsEngine.ACTION_SPECIFICATIONS_SPLITTER + nameLabel;
        } else {
            return ARConstantsEngine.INSERT + ARConstantsEngine.ACTION_SPECIFICATIONS_SPLITTER + nameLabel;
        }
    }

    private String handleTargetBuildAction(
            WebElementTagNameEnum forceTag, TargetElement targetBuild, String nameLabel, boolean clickElement) {
        if (targetBuild.getTagType() == null) {
            return clickElement
                    ? ARConstantsEngine.CLICK
                    : ARConstantsEngine.INSERT + ARConstantsEngine.ACTION_SPECIFICATIONS_SPLITTER + nameLabel;
        }

        return switch (targetBuild.getTagType()) {
            case INPUT -> buildInsertAction(forceTag, nameLabel);
            case HIDDEN -> ARConstantsEngine.INSERT
                    + ARConstantsEngine.ACTION_SPECIFICATIONS_SPLITTER
                    + nameLabel
                    + ARConstantsEngine.ACTION_SPECIFICATIONS_SPLITTER
                    + ARConstantsEngine.HIDDEN;
            case BUTTON -> ARConstantsEngine.CLICK;
            default -> ARConstantsEngine.OUTPUT + ARConstantsEngine.ACTION_SPECIFICATIONS_SPLITTER + nameLabel;
        };
    }

    public void updateBotJobTasks(int currentBotJobId) {
        ErrorMessage errorMessage = performDBEngine.loadCompleteJobs(currentBotJobId);
        if (errorMessage != null) {
            performMessage.errorMessageOperationFailed(errorMessage);
            return;
        }

        String jsonData = "[]";
        if (!performLists.getListBotJob().isEmpty()) {
            List<InstructionLoad> blockLoopInstructions = performLists.buildJsonViewData(performLists.getListBotJob());
            jsonData = gson.toJson(blockLoopInstructions);
        }
        webSocketSessionManager.sendMessageJson(
                selectedBotJob.getHomeBankingId(),
                "botJobTasks", // + currentBotJobId,
                jsonData,
                "updateInstructions");
    }
}

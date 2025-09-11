package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.model.*;
import com.allinweb.ch.component.pane.ARNewCommandPane;
import com.allinweb.ch.component.pane.ARScannedElementPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.facade.*;
import com.allinweb.ch.socket.WebSocketSessionManager;
import com.allinweb.ch.util.ARConstants;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.allinweb.ch.util.ErrorMessage;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javax.websocket.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

@ClientEndpoint
@Slf4j
public class ARScannedElementScene extends ARScene {

    private static final Double SCENE_HEIGHT = 650D;
    private static final Double SCENE_WIDTH = 1100D;
    private static final String TITLE = "AR Web Factory";
    private static final DateTimeFormatter FORMAT_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final CountDownLatch latch = new CountDownLatch(1);
    private static final WebSocketSessionManager webSocketSessionManager = WebSocketSessionManager.getInstance();
    private static final PerformLists performLists = PerformLists.getInstance();
    private static final PerformDBEngine performDBEngine = PerformDBEngine.getInstance();
    private static final PerformDataBase performDataBase = PerformDataBase.getInstance();
    private static final ARScannedElementPane arScannedElementPane = ARScannedElementPane.getInstance();
    private static final ARWebDriver arWebDriver = ARWebDriver.getInstance();
    private static final ARPropertyManager arPropertyManager = ARPropertyManager.getInstance();
    private static final PerformMessage performMessage = PerformMessage.getInstance();
    private static final ARNewCommandPane arNewCommandPane = ARNewCommandPane.getInstance();
    private static final PerformActions performActions = PerformActions.getInstance();
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    protected static volatile ARScannedElementScene instance;
    private final Gson gson = new Gson();
    private HomeBankingLoadDTO homeBankingLoadDTO;

    @Getter
    private BotJobLoadDTO currentBotJob;

    private int currentBlockId;
    private BlockLoadDTO blockLoadDTO;
    private Session session;
    private int portSocketInitial = 54525;
    private boolean isConnectWebSocket = false;
    private ExecutorService executorWebSocket;
    private ExecutorService executorServicePreLaunch;
    private Stage modalStage;
    private Scene modalScene;
    private String previousBlock = null;
    private PayloadJson payloadEmpty;
    private List<InstructionLoad> instructionList = new ArrayList<>();
    // Private constructor to prevent instantiation
    private ARScannedElementScene() {

        super();
    }

    public static ARScannedElementScene getInstance() {
        if (instance == null) {
            synchronized (ARScannedElementScene.class) {
                if (instance == null) {
                    instance = new ARScannedElementScene();
                }
            }
        }
        return instance;
    }

    public void initialize(
            HomeBankingLoadDTO homeBankingLoadDTO, BotJobLoadDTO botJobLoadDTO, BlockLoadDTO blockLoadDTO) {
        this.homeBankingLoadDTO = homeBankingLoadDTO;
        this.currentBotJob = botJobLoadDTO;
        this.blockLoadDTO = blockLoadDTO;
        this.executorWebSocket = Executors.newSingleThreadExecutor();
        this.executorServicePreLaunch = Executors.newSingleThreadExecutor();

        String port = arPropertyManager.getProperty(ARPropertyEnum.PORT_SOCKET);
        if (!Strings.isNullOrEmpty(port)) {
            portSocketInitial = Integer.parseInt(port);
        }

        if (!isConnectWebSocket) {
            connectWebSocketClient(portSocketInitial, "scanner-element-pane");
        }
    }
    //    private static final ScheduledExecutorService pingScheduler = Executors.newScheduledThreadPool(1);

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
                        log.error("Error sending ping: " + e.getMessage());
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
        log.info("Connected to WebSocket server at: " + session.getRequestURI());
        // Sending an initial message
        sendMessage("Hello from JavaFX WebSocket client!");
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
                log.error("WebSocket connection failed sessionId: " + sessionId + " error: " + e.getMessage());
            }
        });
    }

    @OnMessage
    public void onMessage(String message) {
        log.info("Received: " + message);
        if (message == null || message.trim().isEmpty() || message.contains("CONNECT") || message.contains("ping")) {
            // Ignore null or empty messages
            message = message.replaceAll("ping-", "");
            // log.info("Active : " + message);
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
                } else if (objSecond.has("type")) {
                    type = objSecond.has("type") ? objSecond.get("type").getAsString() : "unknown";
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
                // log.info("Active : " + type);
                return;
            }

            String sessionId =
                    jsonObjMSG.has("sessionId") ? jsonObjMSG.get("sessionId").getAsString() : "unknown";

            // Process the message based on its type
            switch (type) {
                case "UPDATE_BLOCKS":
                    BlockMoveDTO blockMoveDTO = gson.fromJson(body, BlockMoveDTO.class);

                    if (previousBlock != null && !previousBlock.equals(type)) {
                        arNewCommandPane.closePane();
                        previousBlock = type;
                    }

                    String blockUpdate = blockMoveDTO.getSessionId().equals("componentTasks")
                            ? "UPDATE_BLOCKS_COMP"
                            : "UPDATE_BLOCKS";

                    String blockTable =
                            blockMoveDTO.getSessionId().equals("componentTasks") ? "component_block" : "block";

                    // Attempt to get it from currentBotJob
                    int whereId = blockMoveDTO.getSessionId().equals("componentTasks")
                            ? currentBotJob.getHomeBankingId() != null ? currentBotJob.getHomeBankingId() : -1
                            : currentBotJob.getId() != null ? currentBotJob.getId() : -1;

                    if (whereId == -1) {
                        whereId = blockMoveDTO.getSessionId().equals("componentTasks")
                                ? blockMoveDTO.getHomeBankingId() != null ? blockMoveDTO.getHomeBankingId() : -1
                                : blockMoveDTO.getBotJobId() != null ? blockMoveDTO.getBotJobId() : -1;
                    }

                    if (previousBlock != null && !previousBlock.equals(blockUpdate)) {
                        arNewCommandPane.closePane();
                        previousBlock = blockUpdate;
                    } else if (previousBlock == null) {
                        previousBlock = blockUpdate;
                    }

                    try {

                        arScannedElementPane.refreshBlocks(false);

                    } catch (Exception error) {
                        log.error("Error: " + error.getMessage());
                    }
                    break;
                case "CLOSE_BROWSER":
                    if (!arScannedElementPane.isJobRunning.get()) {
                        if (arScannedElementPane.launchBotJobButton != null
                                && !performActions.isJustCalledRefreshPage()) {
                            log.info("CLOSE_BROWSER");
                            Platform.runLater(() -> {
                                Stage stage = (Stage) arScannedElementPane
                                        .launchBotJobButton
                                        .getScene()
                                        .getWindow();
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
                case "SEND_ALL_ELEMENTS_DTO":
                    arScannedElementPane.checkRunningProcess();
                    // Extract the "body" field from the JsonObject
                    SplitDTO processDTO = gson.fromJson(jsonObjMSG, SplitDTO.class);

                    blockUpdate =
                            processDTO.getSessionId().equals("componentTasks") ? "UPDATE_BLOCKS_COMP" : "UPDATE_BLOCKS";

                    if (previousBlock != null && !previousBlock.equals(blockUpdate)) {
                        arNewCommandPane.closePane();
                        previousBlock = blockUpdate;
                    } else if (previousBlock == null) {
                        previousBlock = blockUpdate;
                    }

                    boolean isMany = "SEND_ALL_ELEMENTS_DTO".equalsIgnoreCase(type);
                    stepsInsertManyDTO(processDTO, isMany);
                    //                    stepsInsertOneDTO(targetSelected);
                    break;
                case "TEST_CLICK_DTO":
                case "TEST_INPUT_DTO":
                    arScannedElementPane.checkRunningProcess();
                    // Extract the "body" field from the JsonObject
                    processDTO = gson.fromJson(jsonObjMSG, SplitDTO.class);

                    String tableName = "instruction";
                    whereId = processDTO.getBotJobId() != null ? processDTO.getBotJobId() : currentBotJob.getId();
                    if (processDTO.getSessionId().equals("componentTasks")) {
                        tableName = "component_instruction";
                        whereId = processDTO.getHomeBankingId() != null
                                ? processDTO.getHomeBankingId()
                                : currentBotJob.getHomeBankingId();
                    }

                    blockUpdate =
                            processDTO.getSessionId().equals("componentTasks") ? "UPDATE_BLOCKS_COMP" : "UPDATE_BLOCKS";

                    if (previousBlock != null && !previousBlock.equals(blockUpdate)) {
                        arNewCommandPane.closePane();
                        previousBlock = blockUpdate;
                    } else if (previousBlock == null) {
                        previousBlock = blockUpdate;
                    }

                    if (processDTO.getOperationId() != null
                            && processDTO.getOperationId().equalsIgnoreCase("TEST_STEP")) {

                        // I want all Instructions
                        if (tableName.equals("instruction")
                                        && performLists.getListInstruction().isEmpty()
                                || (tableName.equals("component_instruction")
                                        && performLists.getListInstructionComp().isEmpty())) {

                            ErrorMessage errorMessage = performDataBase.loadInstructions(whereId, -1, -1, tableName);
                            if (errorMessage != null) {
                                performMessage.errorMessageOperationFailed(errorMessage);
                            }
                        }

                        InstructionLoad instruction = performLists.getInstructionById(
                                tableName, whereId, processDTO.getElementDetails()[0].getId());
                        if (instruction != null && instruction.getId() != null) {
                            ElementDTO elementDTO = performActions.buildElementDTO(instruction);
                            arScannedElementPane.targetSelected = extractPickClone(elementDTO);
                            arScannedElementPane.itPrintsElementDTO();
                            arScannedElementPane.testingActions(
                                    arScannedElementPane.targetSelected, processDTO.getType());
                        } else {
                            arScannedElementPane.targetSelected =
                                    extractPickClone(processDTO.getElementDetails()[0]);
                            arScannedElementPane.itPrintsElementDTO();
                            arScannedElementPane.testingActions(
                                    arScannedElementPane.targetSelected, processDTO.getType());
                        }
                    } else {
                        arScannedElementPane.targetSelected =
                                extractPickClone(processDTO.getElementDetails()[0]);
                        arScannedElementPane.itPrintsElementDTO();
                        arScannedElementPane.testingActions(arScannedElementPane.targetSelected, processDTO.getType());
                    }
                    break;
                case "DEL_ELEMENT_DTO":
                case "DETAILS_ELEMENT_DTO":
                    // Extract the "body" field from the JsonObject
                    processDTO = gson.fromJson(jsonObjMSG, SplitDTO.class);

                    blockUpdate =
                            processDTO.getSessionId().equals("componentTasks") ? "UPDATE_BLOCKS_COMP" : "UPDATE_BLOCKS";

                    if (previousBlock != null && !previousBlock.equals(blockUpdate)) {
                        arNewCommandPane.closePane();
                        previousBlock = blockUpdate;
                    } else if (previousBlock == null) {
                        previousBlock = blockUpdate;
                    }

                    arScannedElementPane.targetSelected =
                            extractPickClone(processDTO.getElementDetails()[0]);
                    arScannedElementPane.itPrintsElementDTO();
                    break;
                default:
                    break;
            }
        } catch (Exception error) {
            if (error.getMessage().contains("invalid session id")) {
                log.warn("Browser is Closed");
                performMessage.errorMessage(
                        "Browser is Closed",
                        "<span style='color: #2E7D32; font-weight: bold; font-size: 1.1em;'>To perform this action, please</span> ✅",
                        "<span style='color: #1976D2;'>reopen the browser via the Scanner:</span>",
                        "<span style='font-weight: bold;'>Click the \"Scanner\" button in the previous window</span>",
                        null,
                        0);
            }

            log.error("Closed processing message: " + error.getMessage());
        }
    }

    @Override
    public IARPane buildPane() {
        //        arScannedElementPane.initialize(
        //                arWebDriver,
        //                homeBankingLoadDTO,
        //                botJobLoadDTO,
        //                blockLoadDTO,
        //                executorWebSocket,
        //                executorServicePreLaunch);
        return arScannedElementPane;
    }

    @Override
    public void setStageBehaviour(Stage stage) {
        super.setStageBehaviour(stage); // Call the parent class method

        // Only set the close request handler if it's not already set
        if (!isCloseHandlerSet) {
            stage.setOnCloseRequest(this::handleCloseRequest);
            isCloseHandlerSet = true; // Update the flag to prevent setting it again
        }
    }

    public void handleCloseRequest(WindowEvent event) {
        log.info("Handle Close: Exiting Threads and Quitting WebDriver");

        // Interrupt running threads
        threadList.forEach(this::interruptThread);

        // Close WebDriver if it's initialized
        if (arWebDriver != null) {
            try {
                closeWebDrivers();

                //                arWebDriver.closeDriver(); // Quit WebDriver
                arWebDriver.getCurrentDriver().quit(); // Quit WebDriver
                arWebDriver.setCurrentDriver(null);

                shutDownExecutorService(executorWebSocket);
                shutDownExecutorService(executorServicePreLaunch);

                log.info("WebDriver quit successfully.");
            } catch (Exception e) {
                log.error("Error closing WebDriver: " + e.getMessage());
            }
        }
    }

    // Method to close all WebDriver instances
    public void closeWebDrivers() {
        for (WebDriver driver : arWebDriver.getWebDriverList()) {
            try {
                driver.quit();
                log.info("WebDriver closed.");
            } catch (Exception e) {
                log.warn("Closing WebDriver: " + e.getMessage());
            }
        }
        Platform.runLater(() -> {
            arWebDriver.getWebDriverList().clear();
            arWebDriver.setCurrentDriver(null); // reset current driver

            arWebDriver.closeAllDrivers();
        });

        Platform.runLater(() -> arWebDriver.getWebDriverList().clear());
    }

    @Override
    public String getTitle() {
        return TITLE;
    }

    @Override
    public Double getSceneHeight() {
        return SCENE_HEIGHT;
    }

    @Override
    public Double getSceneWidth() {
        return SCENE_WIDTH;
    }

    private void shutDownExecutorService(ExecutorService executorService) {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("ExecutorService did not terminate");
                }
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
            log.warn("ExecutorService did not terminate" + e.getMessage());
        }
    }

    public void showModal() {

        Platform.runLater(() -> {
            arScannedElementPane.initialize(arWebDriver, currentBotJob, portSocketInitial);

            try {

                if (modalStage == null) {
                    modalStage = new Stage();
                    Platform.runLater(() -> arScannedElementPane.setStage(modalStage));
                    modalStage.getIcons().add(icon);
                    IARPane pane = buildPane();
                    if (pane != null) {
                        modalScene = new Scene(pane.createPane(), getSceneWidth(), getSceneHeight());
                        modalStage.setScene(modalScene);
                        modalStage.setTitle(getTitle());
                        modalStage.initModality(Modality.WINDOW_MODAL);
                        modalStage.setAlwaysOnTop(true); // Set always on top
                        modalStage.toFront();
                        // Reset alwaysOnTop after showing so it behaves normally afterward
                        modalStage.setAlwaysOnTop(false);

                        // Once shown, reset AlwaysOnTop to false so it behaves normally
                        modalStage.setOnShown(event -> {
                            Platform.runLater(() -> modalStage.setAlwaysOnTop(false));
                        });
                    } else {
                        // Handle the case where pane creation failed
                        log.error("Failed to build pane for modal.");
                        return;
                    }
                }

                modalStage.setTitle(getTitle());

                // Check if the stage is already showing
                if (!modalStage.isShowing()) {
                    modalStage.showAndWait(); // Show and wait only if not already showing
                }
            } catch (Exception error) {
                closeWebDrivers();
                closeModal();

                if (!error.getMessage().contains("Not on FX application thread")) {

                    String browser = arPropertyManager.getProperty(ARPropertyEnum.BROWSER);
                    String webDriverPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_WEBDRIVER);
                    int lastSlashIndex = webDriverPath.lastIndexOf('\\');
                    String directoryPath =
                            webDriverPath.substring(0, lastSlashIndex + 1); // includes the last backslash
                    String fileName = webDriverPath.substring(lastSlashIndex + 1);

                    log.error("Invalid URL or Navigation Error: {} - {} - {}", browser, directoryPath, fileName);
                    performMessage.errorMessage(
                            "Invalid URL or Navigation Error",
                            "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>The provided URL is invalid or cannot be reached.</span>",
                            "<span style='font-weight: bold;'>Please verify the following:</span>",
                            "<ul>"
                                    + "   <li>The entered URL is valid and accessible.</li>"
                                    + "   <li>The installed browser version: <span style='color: #008b8b; font-weight: bold;'>"
                                    + browser + "</span></li>"
                                    + "   <li>The WebDriver path:<br><span style='color: #008b8b; font-weight: bold;'>"
                                    + directoryPath + "</span></li>"
                                    + "   <li>The WebDriver file:<br><span style='color: #008b8b; font-weight: bold;'>"
                                    + fileName + "</span></li>"
                                    + "   <li>Ensure the WebDriver and browser are compatible and correctly configured.</li>"
                                    + "</ul>",
                            "<span style='font-style: italic;'>Check the URL format (e.g., including https://) and review browser/WebDriver logs for more details.</span>",
                            0);

                } else {

                    log.error("Scanner Pane showModal error:" + error.getMessage());
                }
            }
        });
    }

    public void closeModal() {
        try {
            if (modalStage != null) {
                modalStage.close();
            }
            modalStage = null;
        } catch (Exception error) {
            log.error("Browser Closed Before Web Scanner. Error: " + error.getMessage());
        }
    }

    private void stepsInsertManyDTO(SplitDTO processDTO, boolean isMany) {
        currentBlockId = arScannedElementPane.validateBlockDB("block", this.currentBotJob.getId(), isMany);
        if (currentBlockId > 0) {
            performDataBase.loadInstructions(currentBotJob.getId(), currentBlockId, -1, "instruction");
            List<InstructionLoad> instruc = performLists.getListInstruction();

            int nextOrder = instruc.size() + 1;

            instructionList.clear();
            for (ElementDTO elementDTO : processDTO.getElementDetails()) {
                TargetElement targetEach = extractPickClone(elementDTO);

                WebElement elementFound = performActions.findWebElement(targetEach);
                targetEach.setElement(elementFound);
                // 3 Different Coordinates
                // Original from JavaScript
                // WebDriver Selenium ElementFound
                // FallBack React Computed
                performActions.defineSavedReferenced(targetEach);

                if (!isMany) {
                    if (!Strings.isNullOrEmpty(arScannedElementPane
                                    .defineNameField
                                    .getText()
                                    .trim())
                            && !targetEach
                                    .getDefinedName()
                                    .equalsIgnoreCase(arScannedElementPane
                                            .defineNameField
                                            .getText()
                                            .trim())) {
                        targetEach.setDefinedName(
                                arScannedElementPane.defineNameField.getText().trim());
                        Platform.runLater(() -> {
                            arScannedElementPane.defineNameField.clear();
                            arScannedElementPane.searchAttribValueField.clear();
                        });
                    }
                    arScannedElementPane.targetSelected = targetEach;
                    //                    itPrintsElementDTO();
                }

                arScannedElementPane.prepareToInsertElementDTO(
                        instructionList, currentBlockId, nextOrder, targetEach, true);
                nextOrder++;
            }

            if (instructionList.size() > 0) {

                ErrorMessage errorMessage = performDataBase.insertInstructionsBatch(
                        "botJobTasks",
                        instructionList,
                        currentBotJob.getId(),
                        currentBlockId,
                        currentBotJob.getHomeBankingId());

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

                    sendStatusButton();
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

                updateBotJobTasks(this.currentBotJob.getId());
                sendStatusButton();

                if (errorMessage != null) {
                    performMessage.errorMessageOperationFailed(errorMessage);
                }
            }
        } else {
            sendStatusButton();
        }
    }

    private void sendStatusButton() {
        WebSocketSignal webSockteSocketSignal = WebSocketSignal.builder()
                .operationId("activate-insert-all")
                .sessionId("scannerGrid")
                .message("Insert All Elements button activated")
                .build();

        String jsonData = gson.toJson(webSockteSocketSignal);

        webSocketSessionManager.sendMessageJson(
                currentBotJob.getHomeBankingId(),
                "scannerGrid", // + currentBotJobId,
                jsonData,
                "activate-insert-all");
    }

    public TargetElement extractPickClone(ElementDTO elementDTO) {

        arScannedElementPane.xpathTextPrevious = elementDTO.getXPath();

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
                log.error(
                        "I Cannot define this element. Try to get it again -> \"HOVER PICK  ELEMENT\" or \"PICK ONE \"");
                performMessage.errorMessage(
                        "I Cannot define this element",
                        "I will use the Locator \"COORDINATES\"",
                        "Try to get it again -> \"HOVER PICK  ELEMENT\" or \"PICK ONE \"",
                        null,
                        null,
                        0);

                return null;
            }
        } else if (!Strings.isNullOrEmpty(targetLocal.getCssSelector())) {
            targetLocal.setXPathWorkedFirst(ARConstants.REGULAR_XPATH);

        } else {
            targetLocal.setXPathWorkedFirst(ARConstants.SHADOW_DOM);
        }

        //        targetElement = performActions.defineTagType(targetElement);

        arScannedElementPane.defineCheckBoxesClickable(targetLocal);

        return targetLocal;
    }

    private TargetElement checkValidateSearchPriorities(TargetElement target) {
        WebElement elementValid = null;
        if (!Strings.isNullOrEmpty(target.getCurrentXPath())) {

            if (target.getForceCoordinates() != null && target.getForceCoordinates()) {
                // Try by coordinates
                try {
                    FieldData filedData = new FieldData("&EMPTY", "&EMPTY");
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

                    log.warn(String.format("Cannot locate a Web Element with Name: %s", target.getAttribName()));
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

                    log.warn(String.format(
                            "Cannot locate a Web Element with Regular XPath: %s", target.getCurrentXPath()));
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

                    log.warn(String.format(
                            "Cannot locate a Web Element with Absolut XPath: %s", target.getAttributeData()));
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

                            log.warn(String.format("Cannot locate a Web Element with ID: %s", target.getAttribId()));
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

                            log.warn(
                                    String.format("Cannot locate a Web Element with Name: %s", target.getAttribName()));
                        }
                    }
                }
            }
        }

        target.setElement(elementValid);

        return target;
    }

    public void destroyPanel() {
        arScannedElementPane.destroy();
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
                currentBotJob.getHomeBankingId(),
                "botJobTasks", // + currentBotJobId,
                jsonData,
                "updateInstructions");
    }
}

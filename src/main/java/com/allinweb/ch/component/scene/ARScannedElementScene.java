package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ARScannedElementPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.facade.*;
import com.allinweb.ch.model.*;
import com.allinweb.ch.socket.WebSocketSessionManager;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.allinweb.ch.util.ErrorMessage;
import com.allinweb.ch.util.TargetElementHelper;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javax.websocket.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
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
    private static final TargetElementHelper targetElementHelper = TargetElementHelper.getInstance();
    private static final ARScannedElementPane arScannedElementPane = ARScannedElementPane.getInstance();
    private static final ARWebDriver arWebDriver = ARWebDriver.getInstance();
    private static final ARPropertyManager arPropertyManager = ARPropertyManager.getInstance();
    private static final PerformMessage performMessage = PerformMessage.getInstance();
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

        ErrorMessage errorMessage = performDataBase.loadBlocks(currentBotJob.getId(), "", "block");
        if (errorMessage == null) {
            arScannedElementPane.refreshBlocks(false);
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
                // Bulk grid payloads (select all -> insert all) exceed the 64KB default receive buffer.
                container.setDefaultMaxTextMessageBufferSize(8 * 1024 * 1024);
                container.setDefaultMaxBinaryMessageBufferSize(8 * 1024 * 1024);
                container.connectToServer(ARScannedElementScene.getInstance(), new URI(serverUri));
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

            SplitDTO splitDTO = parseSplitDTO(jsonObjMSG);

            // Process the message based on its type
            switch (type) {
                case "UPDATE_BLOCKS":
                    BlockMoveDTO blockMoveDTO = gson.fromJson(body, BlockMoveDTO.class);

                    if (previousBlock != null && !previousBlock.equals(type)) {
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

                    blockUpdate =
                            splitDTO.getSessionId().equals("componentTasks") ? "UPDATE_BLOCKS_COMP" : "UPDATE_BLOCKS";

                    if (previousBlock != null && !previousBlock.equals(blockUpdate)) {
                        previousBlock = blockUpdate;
                    } else if (previousBlock == null) {
                        previousBlock = blockUpdate;
                    }

                    boolean isMany = "SEND_ALL_ELEMENTS_DTO".equalsIgnoreCase(type);
                    stepsInsertManyDTO(splitDTO, isMany);
                    //                    stepsInsertOneDTO(targetSelected);
                    break;
                case "UPDATE_ALL_ELEMENTS_DTO":
                    arScannedElementPane.checkRunningProcess();
                    // Extract the "body" field from the JsonObject

                    blockUpdate =
                            splitDTO.getSessionId().equals("componentTasks") ? "UPDATE_BLOCKS_COMP" : "UPDATE_BLOCKS";

                    if (previousBlock != null && !previousBlock.equals(blockUpdate)) {
                        previousBlock = blockUpdate;
                    } else if (previousBlock == null) {
                        previousBlock = blockUpdate;
                    }

                    stepsUpdateManyDTO(splitDTO);
                    //                    stepsInsertOneDTO(targetSelected);
                    break;
                case "TEST_CLICK_DTO":
                case "TEST_INPUT_DTO":
                    if (!performLists.getListBotJob().isEmpty() && splitDTO.getBotJobId() != null) {
                        performLists.getListBotJob().stream()
                                .filter(j -> java.util.Objects.equals(j.getId(), splitDTO.getBotJobId()))
                                .findFirst()
                                .ifPresent(j -> {
                                    splitDTO.setBotJobName(j.getName());
                                    splitDTO.setProjectType(j.getPriority());
                                });
                    }
                    if (splitDTO.getProjectType() != null
                            && (splitDTO.getProjectType().equalsIgnoreCase("Android")
                                    || splitDTO.getProjectType().equalsIgnoreCase("iOS"))) {
                        sessionId = "mobileScannerGrid";
                        splitDTO.setSessionId(sessionId);
                    }

                    if ("mobileScannerGrid".equals(sessionId)) {

                        // Safely extract the first element ID (if present)
                        Integer elementId = Optional.ofNullable(splitDTO.getElementDetails())
                                .filter(arr -> arr.length > 0)
                                .map(arr -> arr[0])
                                .map(ElementDTO::getId)
                                .orElse(null);

                        // Find matching instruction by variableId
                        InstructionLoad matchingInstruction =
                                Optional.ofNullable(performLists.getListInstruction())
                                        .orElse(Collections.emptyList())
                                        .stream()
                                        .filter(i -> Objects.equals(i.getId(), elementId))
                                        .findFirst()
                                        .orElse(null);

                        // 2) Apply only non-empty values into splitDTO and elementDetails[0]
                        if (matchingInstruction != null) {
                            // >>> Add AttrData:* references into elementDetails.attributesData
                            SplitDTO.applyAttrDataFromReferences(splitDTO, matchingInstruction);

                            SplitDTO.applyInstructionToSplit(splitDTO, matchingInstruction);
                        }

                        splitDTO.setOperationId(type);
                        String jsonData = gson.toJson(splitDTO);

                        if (!"NEW_ELEMENT_DTO".equals(type) && !"SEND_ALL_ELEMENTS_DTO".equals(type)) {
                            webSocketSessionManager.sendMessageJson(
                                    splitDTO.getHomeBankingId(), "mobile-return-server", jsonData, type);
                        }
                    } else {
                        arScannedElementPane.checkRunningProcess();

                        // Extract the "body" field from the JsonObject
                        //                    splitDTO = gson.fromJson(jsonObjMSG, SplitDTO.class);

                        String tableName = "instruction";
                        whereId = splitDTO.getBotJobId() != null
                                ? splitDTO.getBotJobId()
                                : currentBotJob.getId() != null ? currentBotJob.getId() : -1;

                        if (splitDTO.getSessionId().equals("componentTasks")) {
                            tableName = "component_instruction";
                            whereId = splitDTO.getHomeBankingId() != null
                                    ? splitDTO.getHomeBankingId()
                                    : currentBotJob.getHomeBankingId() != null ? currentBotJob.getHomeBankingId() : -1;
                        }

                        blockUpdate = splitDTO.getSessionId().equals("componentTasks")
                                ? "UPDATE_BLOCKS_COMP"
                                : "UPDATE_BLOCKS";

                        if (previousBlock != null && !previousBlock.equals(blockUpdate)) {
                            previousBlock = blockUpdate;
                        } else if (previousBlock == null) {
                            previousBlock = blockUpdate;
                        }

                        if (splitDTO.getOperationId() != null
                                && splitDTO.getOperationId().equalsIgnoreCase("TEST_STEP")) {

                            // I want all Instructions
                            if (tableName.equals("instruction")
                                            && performLists.getListInstruction().isEmpty()
                                    || (tableName.equals("component_instruction")
                                            && performLists
                                                    .getListInstructionComp()
                                                    .isEmpty())) {

                                ErrorMessage errorMessage =
                                        performDataBase.loadInstructions(whereId, -1, -1, tableName);
                                if (errorMessage != null) {
                                    performMessage.errorMessageOperationFailed(errorMessage);
                                }
                            }

                            InstructionLoad instruction = performLists.getInstructionById(
                                    tableName, whereId, splitDTO.getElementDetails()[0].getId());
                            if (instruction != null && instruction.getId() != null) {
                                ElementDTO elementDTO = performActions.buildElementDTO(instruction);
                                targetElementHelper.initialize(
                                        performActions, new JavaFxScannerTargetContext(arScannedElementPane));
                                arScannedElementPane.targetSelected = targetElementHelper.extractPickClone(elementDTO);
                                applyForceCoordinatesFromIncomingDto(
                                        arScannedElementPane.targetSelected, splitDTO.getElementDetails()[0]);
                                arScannedElementPane.itPrintsElementDTO();
                                arScannedElementPane.testingActions(
                                        arScannedElementPane.targetSelected,
                                        splitDTO.getType(),
                                        splitDTO.getElementDetails()[0].getDefaultValue());
                            } else {
                                targetElementHelper.initialize(
                                        performActions, new JavaFxScannerTargetContext(arScannedElementPane));
                                arScannedElementPane.targetSelected =
                                        targetElementHelper.extractPickClone(splitDTO.getElementDetails()[0]);
                                applyForceCoordinatesFromIncomingDto(
                                        arScannedElementPane.targetSelected, splitDTO.getElementDetails()[0]);
                                arScannedElementPane.itPrintsElementDTO();
                                arScannedElementPane.testingActions(
                                        arScannedElementPane.targetSelected,
                                        splitDTO.getType(),
                                        splitDTO.getElementDetails()[0].getDefaultValue());
                            }
                        } else {
                            targetElementHelper.initialize(
                                    performActions, new JavaFxScannerTargetContext(arScannedElementPane));
                            arScannedElementPane.targetSelected =
                                    targetElementHelper.extractPickClone(splitDTO.getElementDetails()[0]);
                            applyForceCoordinatesFromIncomingDto(
                                    arScannedElementPane.targetSelected, splitDTO.getElementDetails()[0]);
                            arScannedElementPane.itPrintsElementDTO();
                            arScannedElementPane.testingActions(
                                    arScannedElementPane.targetSelected,
                                    splitDTO.getType(),
                                    splitDTO.getElementDetails()[0].getDefaultValue());
                        }
                        break;
                    }
                case "DEL_ELEMENT_DTO":
                case "DETAILS_ELEMENT_DTO":
                    // Extract the "body" field from the JsonObject
                    //                    splitDTO = gson.fromJson(jsonObjMSG, SplitDTO.class);

                    if (type.equals("TEST_CLICK_DTO")) {
                        blockUpdate = splitDTO.getSessionId().equals("componentTasks")
                                ? "UPDATE_BLOCKS_COMP"
                                : "UPDATE_BLOCKS";

                        if (previousBlock != null && !previousBlock.equals(blockUpdate)) {
                            previousBlock = blockUpdate;
                        } else if (previousBlock == null) {
                            previousBlock = blockUpdate;
                        }

                        targetElementHelper.initialize(
                                performActions, new JavaFxScannerTargetContext(arScannedElementPane));
                        arScannedElementPane.targetSelected =
                                targetElementHelper.extractPickClone(splitDTO.getElementDetails()[0]);
                        arScannedElementPane.itPrintsElementDTO();
                    } else {
                        targetElementHelper.initialize(
                                performActions, new JavaFxScannerTargetContext(arScannedElementPane));
                        arScannedElementPane.targetSelected =
                                targetElementHelper.extractPickClone(splitDTO.getElementDetails()[0]);
                        arScannedElementPane.itPrintsElementDTO();
                    }
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

                // Always log the REAL cause with a stack trace — previously it was discarded and
                // every failure was blamed on the WebDriver, which is meaningless in Playwright mode.
                log.error("Scanner Pane showModal failed", error);

                String message = error.getMessage() == null ? "" : error.getMessage();

                // In Playwright-only mode there is no Selenium WebDriver, so the "check your
                // WebDriver / browser version" dialog is misleading. Only show it when Selenium is
                // actually in play; otherwise surface the true error.
                boolean playwrightOnly = arWebDriver != null && arWebDriver.isPlaywrightOnly();

                if (message.contains("Not on FX application thread")) {
                    log.error("Scanner Pane showModal error:" + message);
                } else if (playwrightOnly) {
                    performMessage.errorMessage(
                            "Scanner Error",
                            "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>The Scanner could not open.</span>",
                            "<span style='font-weight: bold;'>The Playwright browser opened, but building the scanner failed.</span>",
                            message.isBlank()
                                    ? null
                                    : "<span style='font-style: italic;'>Details: " + message + "</span>",
                            null,
                            0);
                } else {

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

    /**
     * Copy the per-element F/E/T/N/S bits from the incoming ElementDTO onto
     * the already-built TargetElement so the test path honours the badges the
     * user toggled in GridItemScann. {@code extractPickClone} doesn't read
     * {@code forceCoordinates}, so without this the TargetElement always has
     * an empty flag string and pressAfter/performWebActions fall back to the
     * legacy TAB default. Empty/null is left alone.
     */
    private void applyForceCoordinatesFromIncomingDto(TargetElement target, ElementDTO elementDTO) {
        if (target == null || elementDTO == null) return;
        String incoming = elementDTO.getForceCoordinates();
        log.info(
                "applyForceCoordinatesFromIncomingDto - incoming='{}', existingOnTarget='{}'",
                incoming,
                target.getForceCoordinates());
        if (!Strings.isNullOrEmpty(incoming)) {
            target.setForceCoordinates(incoming);
        }
    }

    private void stepsInsertManyDTO(SplitDTO processDTO, boolean isMany) {
        if (processDTO.getBlockId() != null && processDTO.getBlockId() > 0) {
            performInsertManyDTO(processDTO, isMany);
            return;
        }
        // If blocks exist but the user didn't pick one, open the create-block
        // modal on the FX thread and chain the insert to its Create handler.
        // Cancel → nothing happens. If a block IS selected (or no blocks exist
        // at all, which triggers the Default-Block auto-create inside
        // validateBlockDB), fall through to performInsertManyDTO directly.
        if (!performLists.getListBlock().isEmpty() && !arScannedElementPane.isRealBlockSelectedForInsert()) {
            Platform.runLater(() ->
                    arScannedElementPane.ensureBlockSelectedOrPrompt(() -> performInsertManyDTO(processDTO, isMany)));
            return;
        }
        performInsertManyDTO(processDTO, isMany);
    }

    private void performInsertManyDTO(SplitDTO processDTO, boolean isMany) {
        String insertMsg = isMany ? "Insert ALL" : "Insert one Element";
        currentBlockId = processDTO.getBlockId() != null && processDTO.getBlockId() > 0
                ? processDTO.getBlockId()
                : arScannedElementPane.validateBlockDB("block", this.currentBotJob.getId(), insertMsg);
        if (currentBlockId > 0) {
            performDataBase.loadInstructions(currentBotJob.getId(), currentBlockId, -1, "instruction");
            List<InstructionLoad> instruc = performLists.getListInstruction();

            int nextOrder = instruc.size() + 1;

            instructionList.clear();
            targetElementHelper.initialize(performActions, new JavaFxScannerTargetContext(arScannedElementPane));

            for (ElementDTO elementDTO : processDTO.getElementDetails()) {
                TargetElement targetEach = targetElementHelper.extractPickClone(elementDTO);

                WebElement elementFound = performActions.findWebElement(targetEach);
                if (targetEach.getElement() == null && elementFound != null) {
                    targetEach.setElement(elementFound);
                }
                // 3 Different Coordinates
                // Original from JavaScript
                // WebDriver Selenium ElementFound
                // FallBack React Computed
                performActions.defineSavedReferenced(targetEach);

                // Propagate per-element F/E/T/N/S flags toggled in GridItemScann so
                // prepareToInsertElementDTO can honour them instead of the pane's
                // single-pick checkboxes. Empty string/null is "use checkboxes".
                if (!Strings.isNullOrEmpty(elementDTO.getForceCoordinates())) {
                    targetEach.setForceCoordinates(elementDTO.getForceCoordinates());
                }

                if (!isMany) {
                    // definedNameLabel is a read-only display; renames flow through the React
                    // grid and instruction.client_named, not through definedName overrides here.
                    arScannedElementPane.targetSelected = targetEach;
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

                updateBotJobTasks(this.currentBotJob.getId());
                sendStatusButton("scannerGrid", "activate-insert-all", "Insert All Elements button activated");

                if (errorMessage != null) {
                    performMessage.errorMessageOperationFailed(errorMessage);
                }
            }
        } else {
            sendStatusButton("scannerGrid", "activate-insert-all", "Insert All Elements button activated");
        }
    }

    private void stepsUpdateManyDTO(SplitDTO processDTO) {
        currentBlockId = arScannedElementPane.validateBlockDB("block", this.currentBotJob.getId(), "Update All");
        if (currentBlockId > 0) {
            performDataBase.loadInstructions(currentBotJob.getId(), currentBlockId, -1, "instruction");
            List<InstructionLoad> instruc = performLists.getListInstruction();

            int nextOrder = instruc.size() + 1;

            instructionList.clear();
            targetElementHelper.initialize(performActions, new JavaFxScannerTargetContext(arScannedElementPane));

            for (ElementDTO elementDTO : processDTO.getElementDetails()) {
                TargetElement targetEach = targetElementHelper.extractPickClone(elementDTO);

                WebElement elementFound = performActions.findWebElement(targetEach);
                if (targetEach.getElement() == null && elementFound != null) {
                    targetEach.setElement(elementFound);
                }
                // 3 Different Coordinates
                // Original from JavaScript
                // WebDriver Selenium ElementFound
                // FallBack React Computed
                //                performActions.defineSavedReferenced(targetEach);

                arScannedElementPane.prepareToInsertElementDTO(
                        instructionList, currentBlockId, nextOrder, targetEach, true);
                nextOrder++;
            }

            if (instructionList.size() > 0) {

                ErrorMessage errorMessage = performDataBase.updateInstructionsBatchByNameAndBlockId(
                        "botJobTasks",
                        instructionList,
                        currentBotJob.getId(),
                        currentBlockId,
                        currentBotJob.getHomeBankingId());

                log.info("Total:" + performDataBase.getIdsInstrucAfter().size());
                instructionList.removeIf(instruction -> instruction.getId() == null);

                if (errorMessage == null) {
                    errorMessage = performDataBase.upsertReferencesBatch("botJobTasks", instructionList);
                }

                updateBotJobTasks(this.currentBotJob.getId());
                sendStatusButton("scannerGrid", "activate-update-all", "Update All Elements button activated");
                if (errorMessage != null) {
                    performMessage.errorMessageOperationFailed(errorMessage);
                }
            }
        } else {
            sendStatusButton("scannerGrid", "activate-update-all", "Update All Elements button activated");
        }
    }

    private void sendStatusButton(String sessionId, String operationId, String message) {
        WebSocketSignal webSockteSocketSignal = WebSocketSignal.builder()
                .sessionId(sessionId)
                .operationId(operationId)
                .message(message)
                .build();

        String jsonData = gson.toJson(webSockteSocketSignal);

        webSocketSessionManager.sendMessageJson(currentBotJob.getHomeBankingId(), sessionId, jsonData, operationId);
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

    private SplitDTO parseSplitDTO(JsonObject jsonEntry) {
        if (jsonEntry == null || jsonEntry.isEmpty()) {
            log.warn("parseSplitDTO called with null or empty JSON object");
            return null;
        }

        try {
            // Step 1: If there's a "body" key, extract its string and parse it as JSON
            if (jsonEntry.has("body")) {
                String bodyStr = jsonEntry.get("body").getAsString();
                JsonObject inner = gson.fromJson(bodyStr, JsonObject.class);
                return gson.fromJson(inner, SplitDTO.class);
            }

            // Step 2: Otherwise, parse the current object directly
            return gson.fromJson(jsonEntry, SplitDTO.class);

        } catch (Exception error) {
            log.error("Cannot parse SplitDTO: " + error.getMessage() + " | JSON: " + jsonEntry);
            return null;
        }
    }
}

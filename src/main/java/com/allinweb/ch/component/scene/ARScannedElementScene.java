package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ARScannedElementPaneProvider;
import com.allinweb.ch.component.pane.ARScannedElementPanePort;
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
    private final ScannerGridStatusPublisher scannerGridStatusPublisher = new ScannerGridStatusPublisher();
    private final ScannerElementPanePublisher scannerElementPanePublisher = new ScannerElementPanePublisher();
    private final ARScannedElementPanePort arScannedElementPane;
    private final ScannerElementTestActionService scannerElementTestActionService =
            new ScannerElementTestActionService();
    private final ScannerElementTestLookupService scannerElementTestLookupService =
            new ScannerElementTestLookupService(new SceneElementTestListsPort(), new SceneElementTestDataPort());
    private final ScannerInsertBlockSelectionService scannerInsertBlockSelectionService;
    private final ScannerInsertPreparationService scannerInsertPreparationService =
            new ScannerInsertPreparationService();
    private final ScannerUpdatePreparationService scannerUpdatePreparationService =
            new ScannerUpdatePreparationService();
    private final ScannerInsertPersistenceService scannerInsertPersistenceService =
            new ScannerInsertPersistenceService(new SceneInsertPersistenceDataPort());
    private final ScannerUpdatePersistenceService scannerUpdatePersistenceService =
            new ScannerUpdatePersistenceService(new SceneUpdatePersistenceDataPort());
    private final BotJobWorkspaceCapabilityService botJobWorkspaceCapabilityService =
            BotJobWorkspaceCapabilityService.getInstance();
    private final ScannerMobileTestRoute scannerMobileTestRoute = ScannerMobileTestRoute.standard();
    // Private constructor to prevent instantiation
    private ARScannedElementScene() {

        super();
        this.arScannedElementPane = ARScannedElementPaneProvider.getInstance().currentPane();
        this.scannerInsertBlockSelectionService =
                new ScannerInsertBlockSelectionService(new SceneInsertBlockListsPort(), arScannedElementPane);
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
            connectWebSocketClient(portSocketInitial, scannerElementPanePublisher.destinationSessionId());
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
                                    .sendText("ping-" + scannerElementPanePublisher.destinationSessionId());
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
                if (objSecond.has("type")
                        && objSecond.get("type").getAsString().equalsIgnoreCase(ScannerWorkspaceOperations.CLOSE_BROWSER)) {
                    type = ScannerWorkspaceOperations.CLOSE_BROWSER;
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
                case ScannerWorkspaceOperations.UPDATE_BLOCKS:
                    BlockMoveDTO blockMoveDTO = gson.fromJson(body, BlockMoveDTO.class);

                    if (previousBlock != null && !previousBlock.equals(type)) {
                        previousBlock = type;
                    }

                    String blockUpdate = blockMoveDTO.getSessionId().equals(ScannerWorkspaceSessions.COMPONENT_TASKS)
                            ? ScannerWorkspaceOperations.UPDATE_BLOCKS_COMP
                            : ScannerWorkspaceOperations.UPDATE_BLOCKS;

                    String blockTable =
                            blockMoveDTO.getSessionId().equals(ScannerWorkspaceSessions.COMPONENT_TASKS) ? "component_block" : "block";

                    // Attempt to get it from currentBotJob
                    int whereId = blockMoveDTO.getSessionId().equals(ScannerWorkspaceSessions.COMPONENT_TASKS)
                            ? currentBotJob.getHomeBankingId() != null ? currentBotJob.getHomeBankingId() : -1
                            : currentBotJob.getId() != null ? currentBotJob.getId() : -1;

                    if (whereId == -1) {
                        whereId = blockMoveDTO.getSessionId().equals(ScannerWorkspaceSessions.COMPONENT_TASKS)
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
                case ScannerWorkspaceOperations.CLOSE_BROWSER:
                    if (!arScannedElementPane.isJobRunning()) {
                        if (!performActions.isJustCalledRefreshPage()) {
                            log.info(ScannerWorkspaceOperations.CLOSE_BROWSER);
                            Platform.runLater(() -> {
                                arScannedElementPane.closeLaunchWindowIfPresent();

                                // Clean ARScannedElementPane singleton instance
                                arScannedElementPane.destroy();
                                ARScannedElementScene.getInstance().destroyPanel(); //
                            });
                        }

                        if (performActions.isJustCalledRefreshPage()) {
                            performActions.setJustCalledRefreshPage(false);
                        }
                    }

                    break;
                case ScannerWorkspaceOperations.NEW_ELEMENT_DTO:
                case ScannerWorkspaceOperations.SEND_ALL_ELEMENTS_DTO:
                    arScannedElementPane.checkRunningProcess();
                    // Extract the "body" field from the JsonObject

                    blockUpdate =
                            splitDTO.getSessionId().equals(ScannerWorkspaceSessions.COMPONENT_TASKS)
                                    ? ScannerWorkspaceOperations.UPDATE_BLOCKS_COMP
                                    : ScannerWorkspaceOperations.UPDATE_BLOCKS;

                    if (previousBlock != null && !previousBlock.equals(blockUpdate)) {
                        previousBlock = blockUpdate;
                    } else if (previousBlock == null) {
                        previousBlock = blockUpdate;
                    }

                    boolean isMany = ScannerWorkspaceOperations.SEND_ALL_ELEMENTS_DTO.equalsIgnoreCase(type);
                    stepsInsertManyDTO(splitDTO, isMany);
                    //                    stepsInsertOneDTO(targetSelected);
                    break;
                case ScannerWorkspaceOperations.UPDATE_ALL_ELEMENTS_DTO:
                    arScannedElementPane.checkRunningProcess();
                    // Extract the "body" field from the JsonObject

                    blockUpdate =
                            splitDTO.getSessionId().equals(ScannerWorkspaceSessions.COMPONENT_TASKS)
                                    ? ScannerWorkspaceOperations.UPDATE_BLOCKS_COMP
                                    : ScannerWorkspaceOperations.UPDATE_BLOCKS;

                    if (previousBlock != null && !previousBlock.equals(blockUpdate)) {
                        previousBlock = blockUpdate;
                    } else if (previousBlock == null) {
                        previousBlock = blockUpdate;
                    }

                    stepsUpdateManyDTO(splitDTO);
                    //                    stepsInsertOneDTO(targetSelected);
                    break;
                case ScannerWorkspaceOperations.TEST_CLICK_DTO:
                case ScannerWorkspaceOperations.TEST_INPUT_DTO:
                    if (!performLists.getListBotJob().isEmpty() && splitDTO.getBotJobId() != null) {
                        performLists.getListBotJob().stream()
                                .filter(j -> java.util.Objects.equals(j.getId(), splitDTO.getBotJobId()))
                                .findFirst()
                                .ifPresent(j -> {
                                    splitDTO.setBotJobName(j.getName());
                                    splitDTO.setProjectType(j.getPriority());
                                });
                    }
                    if (botJobWorkspaceCapabilityService.supportsNativeMobileTools(splitDTO.getProjectType())) {
                        sessionId = scannerMobileTestRoute.scannerSessionId();
                        splitDTO.setSessionId(sessionId);
                    }

                    if (scannerMobileTestRoute.isScannerSession(sessionId)) {

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

                        if (!ScannerWorkspaceOperations.NEW_ELEMENT_DTO.equals(type)
                                && !ScannerWorkspaceOperations.SEND_ALL_ELEMENTS_DTO.equals(type)) {
                            webSocketSessionManager.sendMessageJson(
                                    splitDTO.getHomeBankingId(),
                                    scannerMobileTestRoute.returnSessionId(),
                                    jsonData,
                                    type);
                        }
                    } else {
                        arScannedElementPane.checkRunningProcess();

                        // Extract the "body" field from the JsonObject
                        //                    splitDTO = gson.fromJson(jsonObjMSG, SplitDTO.class);

                        blockUpdate = splitDTO.getSessionId().equals(ScannerWorkspaceSessions.COMPONENT_TASKS)
                                ? ScannerWorkspaceOperations.UPDATE_BLOCKS_COMP
                                : ScannerWorkspaceOperations.UPDATE_BLOCKS;

                        if (previousBlock != null && !previousBlock.equals(blockUpdate)) {
                            previousBlock = blockUpdate;
                        } else if (previousBlock == null) {
                            previousBlock = blockUpdate;
                        }

                        ScannerElementTestLookupService.Result testLookup =
                                scannerElementTestLookupService.resolve(splitDTO, currentBotJob);
                        if (testLookup.loadError() != null) {
                            performMessage.errorMessageOperationFailed(testLookup.loadError());
                        }
                        if (testLookup.instruction() != null && testLookup.instruction().getId() != null) {
                            ElementDTO elementDTO = performActions.buildElementDTO(testLookup.instruction());
                            runElementTestAction(elementDTO, splitDTO);
                        } else {
                            runElementTestAction(splitDTO.getElementDetails()[0], splitDTO);
                        }
                        break;
                    }
                case ScannerWorkspaceOperations.DEL_ELEMENT_DTO:
                case ScannerWorkspaceOperations.DETAILS_ELEMENT_DTO:
                    // Extract the "body" field from the JsonObject
                    //                    splitDTO = gson.fromJson(jsonObjMSG, SplitDTO.class);

                    if (type.equals(ScannerWorkspaceOperations.TEST_CLICK_DTO)) {
                        blockUpdate = splitDTO.getSessionId().equals(ScannerWorkspaceSessions.COMPONENT_TASKS)
                                ? ScannerWorkspaceOperations.UPDATE_BLOCKS_COMP
                                : ScannerWorkspaceOperations.UPDATE_BLOCKS;

                        if (previousBlock != null && !previousBlock.equals(blockUpdate)) {
                            previousBlock = blockUpdate;
                        } else if (previousBlock == null) {
                            previousBlock = blockUpdate;
                        }

                        targetElementHelper.initialize(performActions, arScannedElementPane.scannerTargetContext());
                        arScannedElementPane.setTargetSelected(
                                targetElementHelper.extractPickClone(splitDTO.getElementDetails()[0]));
                        arScannedElementPane.itPrintsElementDTO();
                    } else {
                        targetElementHelper.initialize(performActions, arScannedElementPane.scannerTargetContext());
                        arScannedElementPane.setTargetSelected(
                                targetElementHelper.extractPickClone(splitDTO.getElementDetails()[0]));
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

    private void runElementTestAction(ElementDTO sourceElement, SplitDTO splitDTO) {
        scannerElementTestActionService.run(
                performActions,
                targetElementHelper,
                arScannedElementPane,
                sourceElement,
                splitDTO.getElementDetails()[0],
                splitDTO.getType(),
                splitDTO.getElementDetails()[0].getDefaultValue());
    }

    private void stepsInsertManyDTO(SplitDTO processDTO, boolean isMany) {
        if (scannerInsertBlockSelectionService.decide(processDTO)
                == ScannerInsertBlockSelectionService.Decision.PROMPT_FOR_BLOCK) {
            // Chain the insert after the legacy block picker confirms a block.
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

            scannerInsertPreparationService.prepare(
                    new SceneInsertActionsPort(),
                    new SceneInsertTargetExtractor(),
                    arScannedElementPane,
                    instructionList,
                    processDTO.getElementDetails(),
                    currentBlockId,
                    nextOrder,
                    isMany);

            ScannerInsertPersistenceService.Result insertResult = scannerInsertPersistenceService.persist(
                    instructionList, currentBotJob.getId(), currentBlockId, currentBotJob.getHomeBankingId());
            if (insertResult.status() == ScannerInsertPersistenceService.Status.MISMATCH) {
                    log.error(
                            "Error Inserting ALL Elements - Expected (from list):{} - Actual (inserted): {}",
                            insertResult.expectedCount(),
                            insertResult.actualCount());
                    performMessage.errorMessage(
                            "Error Inserting ALL Elements",
                            "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Batch Insertion Failed ❌</span>",
                            "<span style='color: #E65100; font-weight: bold;'>Mismatch detected:</span> The number of inserted instructions does not match the expected size.",
                            "<span style='font-style: italic;'>Expected (from list):</span> "
                                    + insertResult.expectedCount(),
                            "<span style='font-style: italic;'>Actual (inserted):</span> "
                                    + insertResult.actualCount(),
                            0);

                sendScannerGridStatusButton(
                        ScannerWorkspaceOperations.ACTIVATE_INSERT_ALL, "Insert All Elements button activated");
                return;
            }
            if (insertResult.status() == ScannerInsertPersistenceService.Status.PERSISTED) {
                updateBotJobTasks(this.currentBotJob.getId());
                sendScannerGridStatusButton(
                        ScannerWorkspaceOperations.ACTIVATE_INSERT_ALL, "Insert All Elements button activated");

                if (insertResult.error() != null) {
                    performMessage.errorMessageOperationFailed(insertResult.error());
                }
            }
        } else {
            sendScannerGridStatusButton(
                    ScannerWorkspaceOperations.ACTIVATE_INSERT_ALL, "Insert All Elements button activated");
        }
    }

    private void stepsUpdateManyDTO(SplitDTO processDTO) {
        currentBlockId = arScannedElementPane.validateBlockDB("block", this.currentBotJob.getId(), "Update All");
        if (currentBlockId > 0) {
            performDataBase.loadInstructions(currentBotJob.getId(), currentBlockId, -1, "instruction");
            List<InstructionLoad> instruc = performLists.getListInstruction();

            int nextOrder = instruc.size() + 1;

            scannerUpdatePreparationService.prepare(
                    new SceneInsertActionsPort(),
                    new SceneInsertTargetExtractor(),
                    arScannedElementPane,
                    instructionList,
                    processDTO.getElementDetails(),
                    currentBlockId,
                    nextOrder);

            if (instructionList.size() > 0) {

                ScannerUpdatePersistenceService.Result updateResult = scannerUpdatePersistenceService.persist(
                        instructionList,
                        currentBotJob.getId(),
                        currentBlockId,
                        currentBotJob.getHomeBankingId());

                if (updateResult.status() == ScannerUpdatePersistenceService.Status.PERSISTED) {
                    log.info("Total:" + updateResult.updatedCount());
                    updateBotJobTasks(this.currentBotJob.getId());
                    sendScannerGridStatusButton(
                            ScannerWorkspaceOperations.ACTIVATE_UPDATE_ALL, "Update All Elements button activated");
                    if (updateResult.error() != null) {
                        performMessage.errorMessageOperationFailed(updateResult.error());
                    }
                }
            }
        } else {
            sendScannerGridStatusButton(
                    ScannerWorkspaceOperations.ACTIVATE_UPDATE_ALL, "Update All Elements button activated");
        }
    }

    private void sendScannerGridStatusButton(String operationId, String message) {
        scannerGridStatusPublisher.publishScannerGridStatus(currentBotJob.getHomeBankingId(), operationId, message);
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
                ScannerWorkspaceSessions.BOT_JOB_TASKS, // + currentBotJobId,
                jsonData,
                ScannerWorkspaceOperations.UPDATE_INSTRUCTIONS);
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

    private static final class SceneElementTestListsPort implements ScannerElementTestLookupService.ListsPort {
        @Override
        public boolean isInstructionListEmpty(String tableName) {
            return ScannerElementTestLookupService.BOT_JOB_INSTRUCTION_TABLE.equals(tableName)
                    ? performLists.getListInstruction().isEmpty()
                    : performLists.getListInstructionComp().isEmpty();
        }

        @Override
        public InstructionLoad getInstructionById(String tableName, int whereId, int instructionId) {
            return performLists.getInstructionById(tableName, whereId, instructionId);
        }
    }

    private static final class SceneElementTestDataPort implements ScannerElementTestLookupService.DataPort {
        @Override
        public ErrorMessage loadInstructions(int whereId, String tableName) {
            return performDataBase.loadInstructions(whereId, -1, -1, tableName);
        }
    }

    private static final class SceneInsertBlockListsPort implements ScannerInsertBlockSelectionService.ListsPort {
        @Override
        public boolean hasBlocks() {
            return !performLists.getListBlock().isEmpty();
        }
    }

    private static final class SceneInsertActionsPort implements ScannerInsertPreparationService.ActionsPort {
        @Override
        public WebElement findWebElement(TargetElement target) {
            return performActions.findWebElement(target);
        }

        @Override
        public void defineSavedReferenced(TargetElement target) {
            performActions.defineSavedReferenced(target);
        }
    }

    private static final class SceneInsertTargetExtractor implements ScannerInsertPreparationService.TargetExtractor {
        @Override
        public void initialize(ScannerTargetContext scannerTargetContext) {
            targetElementHelper.initialize(performActions, scannerTargetContext);
        }

        @Override
        public TargetElement extractPickClone(ElementDTO elementDTO) {
            return targetElementHelper.extractPickClone(elementDTO);
        }
    }

    private static final class SceneInsertPersistenceDataPort implements ScannerInsertPersistenceService.DataPort {
        @Override
        public ErrorMessage insertInstructionsBatch(
                String sessionId,
                List<InstructionLoad> instructions,
                int botJobId,
                int blockId,
                int homeBankingId) {
            return performDataBase.insertInstructionsBatch(sessionId, instructions, botJobId, blockId, homeBankingId);
        }

        @Override
        public List<Integer> insertedInstructionIds() {
            return performDataBase.getIdsInstrucAfter();
        }

        @Override
        public ErrorMessage insertReferencesBatch(List<InstructionLoad> instructions) {
            return performDataBase.insertReferencesBatch(instructions);
        }
    }

    private static final class SceneUpdatePersistenceDataPort implements ScannerUpdatePersistenceService.DataPort {
        @Override
        public ErrorMessage updateInstructionsBatchByNameAndBlockId(
                String sessionId,
                List<InstructionLoad> instructions,
                int botJobId,
                int blockId,
                int homeBankingId) {
            return performDataBase.updateInstructionsBatchByNameAndBlockId(
                    sessionId, instructions, botJobId, blockId, homeBankingId);
        }

        @Override
        public List<Integer> updatedInstructionIds() {
            return performDataBase.getIdsInstrucAfter();
        }

        @Override
        public ErrorMessage upsertReferencesBatch(String sessionId, List<InstructionLoad> instructions) {
            return performDataBase.upsertReferencesBatch(sessionId, instructions);
        }
    }
}

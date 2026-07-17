package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ARScannedElementPaneProvider;
import com.allinweb.ch.component.pane.ARScannedElementPanePort;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.facade.*;
import com.allinweb.ch.model.*;
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
import javax.websocket.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;

@ClientEndpoint
@Slf4j
public class ARScannedElementScene {

    private static final DateTimeFormatter FORMAT_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final CountDownLatch latch = new CountDownLatch(1);
    private static final PerformLists performLists = PerformLists.getInstance();
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
    private String previousBlock = null;
    private PayloadJson payloadEmpty;
    private List<InstructionLoad> instructionList = new ArrayList<>();
    private final ScannerGridStatusPublisher scannerGridStatusPublisher = new ScannerGridStatusPublisher();
    private final ScannerElementPanePublisher scannerElementPanePublisher = new ScannerElementPanePublisher();
    private final ARScannedElementPanePort arScannedElementPane;
    private final ScannerElementTestActionService scannerElementTestActionService =
            new ScannerElementTestActionService();
    private final ScannerElementTestLookupService scannerElementTestLookupService =
            new ScannerElementTestLookupService(
                    new ScannerRuntimeDataPorts.ElementTestListsPort(performLists),
                    new ScannerRuntimeDataPorts.ElementTestDataPort(performDataBase));
    private final ScannerInsertBlockSelectionService scannerInsertBlockSelectionService;
    private final ScannerInsertPreparationService scannerInsertPreparationService =
            new ScannerInsertPreparationService();
    private final ScannerUpdatePreparationService scannerUpdatePreparationService =
            new ScannerUpdatePreparationService();
    private final ScannerInsertPersistenceService scannerInsertPersistenceService =
            new ScannerInsertPersistenceService(
                    new ScannerInstructionPersistenceDataPorts.InsertDataPort(performDataBase));
    private final ScannerUpdatePersistenceService scannerUpdatePersistenceService =
            new ScannerUpdatePersistenceService(
                    new ScannerInstructionPersistenceDataPorts.UpdateDataPort(performDataBase));
    private final ScannerBotJobTasksPublisher scannerBotJobTasksPublisher =
            ScannerBotJobTasksPublisher.getInstance();
    private final ScannerInstructionOrderService scannerInstructionOrderService =
            new ScannerInstructionOrderService(
                    new ScannerRuntimeDataPorts.InstructionOrderDataPort(performDataBase, performLists));
    private final ScannerBlockUpdateRouteService scannerBlockUpdateRouteService =
            new ScannerBlockUpdateRouteService();
    private final BotJobWorkspaceCapabilityService botJobWorkspaceCapabilityService =
            BotJobWorkspaceCapabilityService.getInstance();
    private final ScannerMobileTestRoute scannerMobileTestRoute = ScannerMobileTestRoute.standard();
    private final ScannerMobileTestForwarder scannerMobileTestForwarder =
            new ScannerMobileTestForwarder(scannerMobileTestRoute);
    private final ScannerTestMessageMetadataService scannerTestMessageMetadataService =
            new ScannerTestMessageMetadataService(new ScannerTestMessageMetadataService.DefaultDataPort());
    private final ScannerElementDetailsSelectionService scannerElementDetailsSelectionService =
            new ScannerElementDetailsSelectionService();
    private final ScannerCloseRequestService scannerCloseRequestService = new ScannerCloseRequestService();
    // Private constructor to prevent instantiation
    private ARScannedElementScene() {

        this.arScannedElementPane = ARScannedElementPaneProvider.getInstance().currentPane();
        ScannerShellLifecycle.getInstance().install(new ScannerRuntimeShellHandler(this));
        this.scannerInsertBlockSelectionService =
                new ScannerInsertBlockSelectionService(
                        new ScannerRuntimeDataPorts.InsertBlockListsPort(performLists), arScannedElementPane);
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
        ScannerCurrentJobContext.getInstance().setCurrentBotJob(botJobLoadDTO);
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
                    ScannerBlockUpdateRouteService.Result blockRoute =
                            scannerBlockUpdateRouteService.resolve(type, blockMoveDTO, currentBotJob, previousBlock);
                    previousBlock = blockRoute.previousBlock();

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
                            UiThreadDispatcher.getInstance().execute(() -> {
                                arScannedElementPane.closeLaunchWindowIfPresent();

                                // Clean ARScannedElementPane singleton instance
                                arScannedElementPane.destroy();
                                destroyPanel();
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

                    previousBlock = scannerBlockUpdateRouteService.transitionPreviousBlockForSession(
                            splitDTO.getSessionId(), previousBlock);

                    boolean isMany = ScannerWorkspaceOperations.SEND_ALL_ELEMENTS_DTO.equalsIgnoreCase(type);
                    stepsInsertManyDTO(splitDTO, isMany);
                    //                    stepsInsertOneDTO(targetSelected);
                    break;
                case ScannerWorkspaceOperations.UPDATE_ALL_ELEMENTS_DTO:
                    arScannedElementPane.checkRunningProcess();
                    // Extract the "body" field from the JsonObject

                    previousBlock = scannerBlockUpdateRouteService.transitionPreviousBlockForSession(
                            splitDTO.getSessionId(), previousBlock);

                    stepsUpdateManyDTO(splitDTO);
                    //                    stepsInsertOneDTO(targetSelected);
                    break;
                case ScannerWorkspaceOperations.TEST_CLICK_DTO:
                case ScannerWorkspaceOperations.TEST_INPUT_DTO:
                    scannerTestMessageMetadataService.enrich(splitDTO);
                    if (botJobWorkspaceCapabilityService.supportsNativeMobileTools(splitDTO.getProjectType())) {
                        sessionId = scannerMobileTestRoute.scannerSessionId();
                        splitDTO.setSessionId(sessionId);
                    }

                    if (scannerMobileTestRoute.isScannerSession(sessionId)) {
                        scannerMobileTestForwarder.forward(splitDTO, type);
                    } else {
                        arScannedElementPane.checkRunningProcess();

                        // Extract the "body" field from the JsonObject
                        //                    splitDTO = gson.fromJson(jsonObjMSG, SplitDTO.class);

                        previousBlock = scannerBlockUpdateRouteService.transitionPreviousBlockForSession(
                                splitDTO.getSessionId(), previousBlock);

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
                        previousBlock = scannerBlockUpdateRouteService.transitionPreviousBlockForSession(
                                splitDTO.getSessionId(), previousBlock);
                    }
                    scannerElementDetailsSelectionService.select(
                            new ScannerRuntimeDataPorts.ElementDetailsTargetExtractor(
                                    targetElementHelper, performActions),
                            arScannedElementPane,
                            splitDTO.getElementDetails()[0]);
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

    public void handleCloseRequest() {
        log.info("Handle Close: Exiting Threads and Quitting WebDriver");
        scannerCloseRequestService.close(new SceneCloseRequest());
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
        UiThreadDispatcher.getInstance().execute(() -> {
            arWebDriver.getWebDriverList().clear();
            arWebDriver.setCurrentDriver(null); // reset current driver

            arWebDriver.closeAllDrivers();
        });

        UiThreadDispatcher.getInstance().execute(() -> arWebDriver.getWebDriverList().clear());
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

    private final class SceneCloseRequest implements ScannerCloseRequestService.CloseRequest {
        @Override
        public void interruptThreads() {
            // ARScannedElementScene no longer owns UI threads directly; executor shutdown is handled below.
        }

        @Override
        public boolean hasWebDriver() {
            return arWebDriver != null;
        }

        @Override
        public void closeWebDrivers() {
            ARScannedElementScene.this.closeWebDrivers();
        }

        @Override
        public void quitCurrentDriver() {
            arWebDriver.getCurrentDriver().quit();
        }

        @Override
        public void clearCurrentDriver() {
            arWebDriver.setCurrentDriver(null);
        }

        @Override
        public void shutdownExecutors() {
            shutDownExecutorService(executorWebSocket);
            shutDownExecutorService(executorServicePreLaunch);
            log.info("WebDriver quit successfully.");
        }

        @Override
        public void closeFailed(Exception error) {
            log.error("Error closing WebDriver: " + error.getMessage());
        }
    }

    public void showModal() {

        UiThreadDispatcher.getInstance().execute(() -> {
            arScannedElementPane.initialize(arWebDriver, currentBotJob, portSocketInitial);

            try {
                log.info(
                        "Scanner presentation is owned by React; Java scanner runtime initialized for botJobId={} session={}",
                        currentBotJob == null ? null : currentBotJob.getId(),
                        scannerElementPanePublisher.destinationSessionId());
            } catch (Exception error) {
                // Always log the REAL cause with a stack trace — previously it was discarded and
                // every failure was blamed on the WebDriver, which is meaningless in Playwright mode.
                log.error("Scanner Pane showModal failed", error);

            }
        });
    }

    public void closeModal() {
        try {
            ScannerCurrentJobContext.getInstance().clear();
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
            UiThreadDispatcher.getInstance().execute(() ->
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
            int nextOrder = scannerInstructionOrderService.nextOrder(currentBotJob.getId(), currentBlockId);

            scannerInsertPreparationService.prepare(
                    new ScannerRuntimeDataPorts.InsertActionsPort(performActions),
                    new ScannerRuntimeDataPorts.InsertTargetExtractor(targetElementHelper, performActions),
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
            int nextOrder = scannerInstructionOrderService.nextOrder(currentBotJob.getId(), currentBlockId);

            scannerUpdatePreparationService.prepare(
                    new ScannerRuntimeDataPorts.InsertActionsPort(performActions),
                    new ScannerRuntimeDataPorts.InsertTargetExtractor(targetElementHelper, performActions),
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
        ErrorMessage errorMessage =
                scannerBotJobTasksPublisher.publish(currentBotJob.getHomeBankingId(), currentBotJobId);
        if (errorMessage != null) {
            performMessage.errorMessageOperationFailed(errorMessage);
        }
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

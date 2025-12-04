// package com.allinweb.ch.component.scene;
//
// import com.allinweb.ch.component.TargetElementHelper;
// import com.allinweb.ch.component.pane.ARNewCommandPane;
// import com.allinweb.ch.component.pane.ARScannedElementPane;
// import com.allinweb.ch.component.pane.base.IARPane;
// import com.allinweb.ch.component.scene.base.ARScene;
// import com.allinweb.ch.driver.ARWebDriver;
// import com.allinweb.ch.facade.*;
// import com.allinweb.ch.model.*;
// import com.allinweb.ch.socket.WebSocketSessionManager;
// import com.allinweb.ch.util.ARPropertyEnum;
// import com.allinweb.ch.util.ARPropertyManager;
// import com.allinweb.ch.util.ErrorMessage;
// import com.google.common.base.Strings;
// import com.google.gson.Gson;
// import com.google.gson.JsonObject;
// import com.google.gson.JsonParser;
// import java.awt.Dialog;
// import java.awt.Frame;
// import java.awt.Window;
// import java.awt.event.WindowAdapter;
// import java.awt.event.WindowEvent;
// import java.io.IOException;
// import java.net.URI;
// import java.time.format.DateTimeFormatter;
// import java.util.*;
// import java.util.concurrent.*;
// import javax.swing.JComponent;
// import javax.swing.JDialog;
// import javax.swing.SwingUtilities;
// import javax.websocket.ClientEndpoint;
// import javax.websocket.ContainerProvider;
// import javax.websocket.OnClose;
// import javax.websocket.OnError;
// import javax.websocket.OnMessage;
// import javax.websocket.OnOpen;
// import javax.websocket.Session;
// import javax.websocket.WebSocketContainer;
// import lombok.Getter;
// import lombok.extern.slf4j.Slf4j;
// import org.openqa.selenium.WebDriver;
// import org.openqa.selenium.WebElement;
//
// @ClientEndpoint
// @Slf4j
// public class ARScannedElementScene extends ARScene {
//
//    private static final int SCENE_HEIGHT = 650D;
//    private static final int SCENE_WIDTH = 1100D;
//    private static final String TITLE = "AR Web Factory";
//    private static final DateTimeFormatter FORMAT_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
//    private static final CountDownLatch latch = new CountDownLatch(1);
//    private static final WebSocketSessionManager webSocketSessionManager = WebSocketSessionManager.getInstance();
//    private static final PerformLists performLists = PerformLists.getInstance();
//    private static final PerformDBEngine performDBEngine = PerformDBEngine.getInstance();
//    private static final PerformDataBase performDataBase = PerformDataBase.getInstance();
//    private static final TargetElementHelper targetElementHelper = TargetElementHelper.getInstance();
//    private static final ARScannedElementPane arScannedElementPane = ARScannedElementPane.getInstance();
//    private static final ARWebDriver arWebDriver = ARWebDriver.getInstance();
//    private static final ARPropertyManager arPropertyManager = ARPropertyManager.getInstance();
//    private static final PerformMessage performMessage = PerformMessage.getInstance();
//    private static final ARNewCommandPane arNewCommandPane = ARNewCommandPane.getInstance();
//    private static final PerformActions performActions = PerformActions.getInstance();
//    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
//    protected static volatile ARScannedElementScene instance;
//
//    private final Gson gson = new Gson();
//    private HomeBankingLoadDTO homeBankingLoadDTO;
//
//    @Getter
//    private BotJobLoadDTO currentBotJob;
//
//    private int currentBlockId;
//    private BlockLoadDTO blockLoadDTO;
//    private Session session;
//    private int portSocketInitial = 54525;
//    private boolean isConnectWebSocket = false;
//    private ExecutorService executorWebSocket;
//    private ExecutorService executorServicePreLaunch;
//    private JDialog modalDialog;
//    private String previousBlock = null;
//    private PayloadJson payloadEmpty;
//    private List<InstructionLoad> instructionList = new ArrayList<>();
//
//    // Private constructor to prevent instantiation
//    private ARScannedElementScene() {
//        super();
//    }
//
//    public static ARScannedElementScene getInstance() {
//        if (instance == null) {
//            synchronized (ARScannedElementScene.class) {
//                if (instance == null) {
//                    instance = new ARScannedElementScene();
//                }
//            }
//        }
//        return instance;
//    }
//
//    public void initialize(
//            HomeBankingLoadDTO homeBankingLoadDTO, BotJobLoadDTO botJobLoadDTO, BlockLoadDTO blockLoadDTO) {
//
//        this.homeBankingLoadDTO = homeBankingLoadDTO;
//        this.currentBotJob = botJobLoadDTO;
//        this.blockLoadDTO = blockLoadDTO;
//        this.executorWebSocket = Executors.newSingleThreadExecutor();
//        this.executorServicePreLaunch = Executors.newSingleThreadExecutor();
//
//        String port = arPropertyManager.getProperty(ARPropertyEnum.PORT_SOCKET);
//        if (!Strings.isNullOrEmpty(port)) {
//            portSocketInitial = Integer.parseInt(port);
//        }
//
//        if (!isConnectWebSocket) {
//            connectWebSocketClient(portSocketInitial, "scanner-element-pane");
//        }
//    }
//
//    private void stopKeepAlivePings() {
//        scheduler.shutdownNow();
//    }
//
//    private void startKeepAlivePings() {
//        scheduler.scheduleAtFixedRate(
//                () -> {
//                    try {
//                        if (session != null && session.isOpen()) {
//                            session.getBasicRemote().sendText("ping-scanner-element-pane");
//                        }
//                    } catch (IOException e) {
//                        log.error("Error sending ping: " + e.getMessage());
//                    }
//                },
//                0,
//                15,
//                TimeUnit.SECONDS);
//    }
//
//    @OnOpen
//    public void onOpen(Session session) {
//        this.session = session;
//        latch.countDown();
//        log.info("Connected to WebSocket server at: " + session.getRequestURI());
//        sendMessage("Hello from WebSocket client!");
//    }
//
//    @OnClose
//    public void onClose(Session session) {
//        log.info("Connection closed.");
//        stopKeepAlivePings();
//    }
//
//    @OnError
//    public void onError(Session session, Throwable throwable) {
//        log.info("Error: " + throwable.getMessage());
//        stopKeepAlivePings();
//    }
//
//    // Method to send a message
//    public void sendMessage(String message) {
//        executorWebSocket.submit(() -> {
//            // Implementation intentionally disabled as in original
//            // if (session != null && session.isOpen()) { ... }
//        });
//    }
//
//    public void connectWebSocketClient(int portSocket, String sessionId) {
//        executorWebSocket.submit(() -> {
//            String serverUri = "ws://localhost:" + portSocket + "/websocket?sessionId=" + sessionId;
//            try {
//                WebSocketContainer container = ContainerProvider.getWebSocketContainer();
//                container.connectToServer(this, new URI(serverUri));
//                latch.await();
//                startKeepAlivePings();
//                isConnectWebSocket = true;
//            } catch (Exception e) {
//                isConnectWebSocket = false;
//                log.error("WebSocket connection failed sessionId: " + sessionId + " error: " + e.getMessage());
//            }
//        });
//    }
//
//    @OnMessage
//    public void onMessage(String message) {
//        log.info("Received: " + message);
//        if (message == null || message.trim().isEmpty() || message.contains("CONNECT") || message.contains("ping")) {
//            message = message.replaceAll("ping-", "");
//            return;
//        }
//
//        String type;
//        String body;
//
//        try {
//            JsonObject jsonObjMSG = JsonParser.parseString(message).getAsJsonObject();
//
//            body = jsonObjMSG.has("body") ? jsonObjMSG.get("body").getAsString() : "unknown";
//            if (!body.equalsIgnoreCase("unknown")) {
//                JsonObject objSecond = JsonParser.parseString(body).getAsJsonObject();
//                if (objSecond.has("type") && objSecond.get("type").getAsString().equalsIgnoreCase("CLOSE_BROWSER")) {
//                    type = "CLOSE_BROWSER";
//                } else if (objSecond.has("type")) {
//                    type = objSecond.get("type").getAsString();
//                } else {
//                    type = jsonObjMSG.has("type") ? jsonObjMSG.get("type").getAsString() : "unknown";
//                }
//            } else {
//                type = jsonObjMSG.has("type") ? jsonObjMSG.get("type").getAsString() : "unknown";
//            }
//
//            if (type == null || type.trim().isEmpty() || type.contains("CONNECT") || type.contains("ping")) {
//                type = type != null ? type.replaceAll("ping-", "") : null;
//                return;
//            }
//
//            String sessionId =
//                    jsonObjMSG.has("sessionId") ? jsonObjMSG.get("sessionId").getAsString() : "unknown";
//
//            SplitDTO splitDTO = parseSplitDTO(jsonObjMSG);
//
//            switch (type) {
//                case "UPDATE_BLOCKS": {
//                    BlockMoveDTO blockMoveDTO = gson.fromJson(body, BlockMoveDTO.class);
//
//                    if (previousBlock != null && !previousBlock.equals(type)) {
//                        arNewCommandPane.closePane();
//                        previousBlock = type;
//                    }
//
//                    String blockUpdate = blockMoveDTO.getSessionId().equals("componentTasks")
//                            ? "UPDATE_BLOCKS_COMP"
//                            : "UPDATE_BLOCKS";
//
//                    String blockTable =
//                            blockMoveDTO.getSessionId().equals("componentTasks") ? "component_block" : "block";
//
//                    int whereId = blockMoveDTO.getSessionId().equals("componentTasks")
//                            ? currentBotJob.getHomeBankingId() != null ? currentBotJob.getHomeBankingId() : -1
//                            : currentBotJob.getId() != null ? currentBotJob.getId() : -1;
//
//                    if (whereId == -1) {
//                        whereId = blockMoveDTO.getSessionId().equals("componentTasks")
//                                ? blockMoveDTO.getHomeBankingId() != null ? blockMoveDTO.getHomeBankingId() : -1
//                                : blockMoveDTO.getBotJobId() != null ? blockMoveDTO.getBotJobId() : -1;
//                    }
//
//                    if (previousBlock != null && !previousBlock.equals(blockUpdate)) {
//                        arNewCommandPane.closePane();
//                        previousBlock = blockUpdate;
//                    } else if (previousBlock == null) {
//                        previousBlock = blockUpdate;
//                    }
//
//                    try {
//                        arScannedElementPane.refreshBlocks(false);
//                    } catch (Exception error) {
//                        log.error("Error: " + error.getMessage());
//                    }
//                    break;
//                }
//                case "CLOSE_BROWSER":
//                    if (!arScannedElementPane.isJobRunning.get()) {
//                        if (arScannedElementPane.launchBotJobButton != null
//                                && !performActions.isJustCalledRefreshPage()) {
//                            log.info("CLOSE_BROWSER");
//
//                            SwingUtilities.invokeLater(() -> {
//                                Window w = SwingUtilities.getWindowAncestor(arScannedElementPane.launchBotJobButton);
//                                if (w != null) {
//                                    w.dispose();
//                                }
//
//                                ARScannedElementPane.getInstance().destroy();
//                                ARScannedElementScene.getInstance().destroyPanel();
//                            });
//                        }
//
//                        if (performActions.isJustCalledRefreshPage()) {
//                            performActions.setJustCalledRefreshPage(false);
//                        }
//                    }
//                    break;
//
//                case "NEW_ELEMENT_DTO":
//                case "SEND_ALL_ELEMENTS_DTO": {
//                    arScannedElementPane.checkRunningProcess();
//
//                    String blockUpdate =
//                            splitDTO.getSessionId().equals("componentTasks") ? "UPDATE_BLOCKS_COMP" : "UPDATE_BLOCKS";
//
//                    if (previousBlock != null && !previousBlock.equals(blockUpdate)) {
//                        arNewCommandPane.closePane();
//                        previousBlock = blockUpdate;
//                    } else if (previousBlock == null) {
//                        previousBlock = blockUpdate;
//                    }
//
//                    boolean isMany = "SEND_ALL_ELEMENTS_DTO".equalsIgnoreCase(type);
//                    stepsInsertManyDTO(splitDTO, isMany);
//                    break;
//                }
//
//                case "TEST_CLICK_DTO":
//                case "TEST_INPUT_DTO": {
//                    if (!performLists.getListBotJob().isEmpty() && splitDTO.getBotJobId() != null) {
//                        performLists.getListBotJob().stream()
//                                .filter(j -> Objects.equals(j.getId(), splitDTO.getBotJobId()))
//                                .findFirst()
//                                .ifPresent(j -> {
//                                    splitDTO.setBotJobName(j.getName());
//                                    splitDTO.setProjectType(j.getPriority());
//                                });
//                    }
//                    if (splitDTO.getProjectType() != null
//                            && (splitDTO.getProjectType().equalsIgnoreCase("Android")
//                                    || splitDTO.getProjectType().equalsIgnoreCase("iOS"))) {
//                        sessionId = "mobileScannerGrid";
//                        splitDTO.setSessionId(sessionId);
//                    }
//
//                    if ("mobileScannerGrid".equals(sessionId)) {
//
//                        Integer elementId = Optional.ofNullable(splitDTO.getElementDetails())
//                                .filter(arr -> arr.length > 0)
//                                .map(arr -> arr[0])
//                                .map(ElementDTO::getId)
//                                .orElse(null);
//
//                        InstructionLoad matchingInstruction =
//                                Optional.ofNullable(performLists.getListInstruction())
//                                        .orElse(Collections.emptyList())
//                                        .stream()
//                                        .filter(i -> Objects.equals(i.getId(), elementId))
//                                        .findFirst()
//                                        .orElse(null);
//
//                        if (matchingInstruction != null) {
//                            SplitDTO.applyAttrDataFromReferences(splitDTO, matchingInstruction);
//                            SplitDTO.applyInstructionToSplit(splitDTO, matchingInstruction);
//                        }
//
//                        splitDTO.setOperationId(type);
//                        String jsonData = gson.toJson(splitDTO);
//
//                        if (!"NEW_ELEMENT_DTO".equals(type) && !"SEND_ALL_ELEMENTS_DTO".equals(type)) {
//                            webSocketSessionManager.sendMessageJson(
//                                    splitDTO.getHomeBankingId(), "mobile-return-server", jsonData, type);
//                        }
//                    } else {
//                        arScannedElementPane.checkRunningProcess();
//
//                        String tableName = "instruction";
//                        int whereId = splitDTO.getBotJobId() != null ? splitDTO.getBotJobId() : currentBotJob.getId();
//                        if (splitDTO.getSessionId().equals("componentTasks")) {
//                            tableName = "component_instruction";
//                            whereId = splitDTO.getHomeBankingId() != null
//                                    ? splitDTO.getHomeBankingId()
//                                    : currentBotJob.getHomeBankingId();
//                        }
//
//                        String blockUpdate = splitDTO.getSessionId().equals("componentTasks")
//                                ? "UPDATE_BLOCKS_COMP"
//                                : "UPDATE_BLOCKS";
//
//                        if (previousBlock != null && !previousBlock.equals(blockUpdate)) {
//                            arNewCommandPane.closePane();
//                            previousBlock = blockUpdate;
//                        } else if (previousBlock == null) {
//                            previousBlock = blockUpdate;
//                        }
//
//                        if (splitDTO.getOperationId() != null
//                                && splitDTO.getOperationId().equalsIgnoreCase("TEST_STEP")) {
//
//                            if ((tableName.equals("instruction")
//                                            && performLists.getListInstruction().isEmpty())
//                                    || (tableName.equals("component_instruction")
//                                            && performLists
//                                                    .getListInstructionComp()
//                                                    .isEmpty())) {
//
//                                ErrorMessage errorMessage =
//                                        performDataBase.loadInstructions(whereId, -1, -1, tableName);
//                                if (errorMessage != null) {
//                                    performMessage.errorMessageOperationFailed(errorMessage);
//                                }
//                            }
//
//                            InstructionLoad instruction = performLists.getInstructionById(
//                                    tableName, whereId, splitDTO.getElementDetails()[0].getId());
//                            if (instruction != null && instruction.getId() != null) {
//                                ElementDTO elementDTO = performActions.buildElementDTO(instruction);
//                                targetElementHelper.initialize(performActions, arScannedElementPane);
//                                arScannedElementPane.targetSelected =
// targetElementHelper.extractPickClone(elementDTO);
//                                arScannedElementPane.itPrintsElementDTO();
//                                arScannedElementPane.testingActions(
//                                        arScannedElementPane.targetSelected, splitDTO.getType());
//                            } else {
//                                targetElementHelper.initialize(performActions, arScannedElementPane);
//                                arScannedElementPane.targetSelected =
//                                        targetElementHelper.extractPickClone(splitDTO.getElementDetails()[0]);
//                                arScannedElementPane.itPrintsElementDTO();
//                                arScannedElementPane.testingActions(
//                                        arScannedElementPane.targetSelected, splitDTO.getType());
//                            }
//                        } else {
//                            targetElementHelper.initialize(performActions, arScannedElementPane);
//                            arScannedElementPane.targetSelected =
//                                    targetElementHelper.extractPickClone(splitDTO.getElementDetails()[0]);
//                            arScannedElementPane.itPrintsElementDTO();
//                            arScannedElementPane.testingActions(
//                                    arScannedElementPane.targetSelected, splitDTO.getType());
//                        }
//                    }
//                    break;
//                }
//
//                case "DEL_ELEMENT_DTO":
//                case "DETAILS_ELEMENT_DTO": {
//                    if (type.equals("TEST_CLICK_DTO")) {
//                        String blockUpdate = splitDTO.getSessionId().equals("componentTasks")
//                                ? "UPDATE_BLOCKS_COMP"
//                                : "UPDATE_BLOCKS";
//
//                        if (previousBlock != null && !previousBlock.equals(blockUpdate)) {
//                            arNewCommandPane.closePane();
//                            previousBlock = blockUpdate;
//                        } else if (previousBlock == null) {
//                            previousBlock = blockUpdate;
//                        }
//
//                        targetElementHelper.initialize(performActions, arScannedElementPane);
//                        arScannedElementPane.targetSelected =
//                                targetElementHelper.extractPickClone(splitDTO.getElementDetails()[0]);
//                        arScannedElementPane.itPrintsElementDTO();
//                    }
//                    break;
//                }
//                default:
//                    break;
//            }
//        } catch (Exception error) {
//            if (error.getMessage() != null && error.getMessage().contains("invalid session id")) {
//                log.warn("Browser is Closed");
//                performMessage.errorMessage(
//                        "Browser is Closed",
//                        "<span style='color: #2E7D32; font-weight: bold; font-size: 1.1em;'>To perform this action,
// please</span> ✅",
//                        "<span style='color: #1976D2;'>reopen the browser via the Scanner:</span>",
//                        "<span style='font-weight: bold;'>Click the \"Scanner\" button in the previous window</span>",
//                        null,
//                        0);
//            }
//
//            log.error("Closed processing message: " + error.getMessage());
//        }
//    }
//
//    @Override
//    public IARPane buildPane() {
//        // arScannedElementPane.initialize(...);
//        return arScannedElementPane;
//    }
//
//    /**
//     * Swing-specific frame behaviour for the main frame.
//     */
//    @Override
//    public void setFrameBehaviour(javax.swing.JFrame frame) {
//        super.setFrameBehaviour(frame);
//
//        if (!isCloseHandlerSet) {
//            frame.setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
//            frame.addWindowListener(new WindowAdapter() {
//                @Override
//                public void windowClosing(WindowEvent e) {
//                    handleCloseRequest();
//                }
//            });
//            isCloseHandlerSet = true;
//        }
//    }
//
//    public void handleCloseRequest() {
//        log.info("Handle Close: Exiting Threads and Quitting WebDriver");
//
//        threadList.forEach(this::interruptThread);
//
//        if (arWebDriver != null) {
//            try {
//                closeWebDrivers();
//
//                if (arWebDriver.getCurrentDriver() != null) {
//                    arWebDriver.getCurrentDriver().quit();
//                }
//                arWebDriver.setCurrentDriver(null);
//
//                shutDownExecutorService(executorWebSocket);
//                shutDownExecutorService(executorServicePreLaunch);
//
//                log.info("WebDriver quit successfully.");
//            } catch (Exception e) {
//                log.error("Error closing WebDriver: " + e.getMessage());
//            }
//        }
//    }
//
//    // Method to close all WebDriver instances
//    public void closeWebDrivers() {
//        for (WebDriver driver : arWebDriver.getWebDriverList()) {
//            try {
//                driver.quit();
//                log.info("WebDriver closed.");
//            } catch (Exception e) {
//                log.warn("Closing WebDriver: " + e.getMessage());
//            }
//        }
//
//        SwingUtilities.invokeLater(() -> {
//            arWebDriver.getWebDriverList().clear();
//            arWebDriver.setCurrentDriver(null);
//            arWebDriver.closeAllDrivers();
//        });
//    }
//
//    @Override
//    public String getTitle() {
//        return TITLE;
//    }
//
//    @Override
//    public int getSceneHeight() {
//        return SCENE_HEIGHT;
//    }
//
//    @Override
//    public Double getSceneWidth() {
//        return SCENE_WIDTH;
//    }
//
//    private void shutDownExecutorService(ExecutorService executorService) {
//        if (executorService == null) return;
//
//        executorService.shutdown();
//        try {
//            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
//                executorService.shutdownNow();
//                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
//                    log.warn("ExecutorService did not terminate");
//                }
//            }
//        } catch (InterruptedException e) {
//            executorService.shutdownNow();
//            Thread.currentThread().interrupt();
//            log.warn("ExecutorService did not terminate " + e.getMessage());
//        }
//    }
//
//    public void showModal() {
//
//        SwingUtilities.invokeLater(() -> {
//            arScannedElementPane.initialize(arWebDriver, currentBotJob, portSocketInitial);
//
//            try {
//                if (modalDialog == null) {
//                    // Try to find an owner
//                    Window owner = null;
//                    for (Frame f : Frame.getFrames()) {
//                        if (f.isVisible()) {
//                            owner = f;
//                            break;
//                        }
//                    }
//
//                    modalDialog = new JDialog(owner, getTitle(), Dialog.ModalityType.APPLICATION_MODAL);
//                    modalDialog.setSize(
//                            getSceneWidth(), getSceneHeight());
//                    modalDialog.setLocationRelativeTo(owner);
//
//                    if (icon != null) {
//                        modalDialog.setIconImage(icon);
//                    }
//
//                    IARPane pane = buildPane();
//                    if (pane != null) {
//                        JComponent content = (JComponent) pane.createPane();
//                        modalDialog.setContentPane(content);
//                    } else {
//                        log.error("Failed to build pane for modal.");
//                        return;
//                    }
//                }
//
//                modalDialog.setTitle(getTitle());
//
//                if (!modalDialog.isVisible()) {
//                    modalDialog.setVisible(true); // blocks until closed
//                }
//            } catch (Exception error) {
//                closeWebDrivers();
//                closeModal();
//
//                if (error.getMessage() == null || !error.getMessage().contains("Not on FX application thread")) {
//                    String browser = arPropertyManager.getProperty(ARPropertyEnum.BROWSER);
//                    String webDriverPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_WEBDRIVER);
//                    int lastSlashIndex = webDriverPath.lastIndexOf('\\');
//                    String directoryPath = webDriverPath.substring(0, lastSlashIndex + 1);
//                    String fileName = webDriverPath.substring(lastSlashIndex + 1);
//
//                    log.error("Invalid URL or Navigation Error: {} - {} - {}", browser, directoryPath, fileName);
//                    performMessage.errorMessage(
//                            "Invalid URL or Navigation Error",
//                            "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>The provided URL is
// invalid or cannot be reached.</span>",
//                            "<span style='font-weight: bold;'>Please verify the following:</span>",
//                            "<ul>"
//                                    + "   <li>The entered URL is valid and accessible.</li>"
//                                    + "   <li>The installed browser version: <span style='color: #008b8b; font-weight:
// bold;'>"
//                                    + browser + "</span></li>"
//                                    + "   <li>The WebDriver path:<br><span style='color: #008b8b; font-weight:
// bold;'>"
//                                    + directoryPath + "</span></li>"
//                                    + "   <li>The WebDriver file:<br><span style='color: #008b8b; font-weight:
// bold;'>"
//                                    + fileName + "</span></li>"
//                                    + "   <li>Ensure the WebDriver and browser are compatible and correctly
// configured.</li>"
//                                    + "</ul>",
//                            "<span style='font-style: italic;'>Check the URL format (e.g., including https://) and
// review browser/WebDriver logs for more details.</span>",
//                            0);
//
//                } else {
//                    log.error("Scanner Pane showModal error:" + error.getMessage());
//                }
//            }
//        });
//    }
//
//    public void closeModal() {
//        try {
//            if (modalDialog != null) {
//                modalDialog.dispose();
//            }
//            modalDialog = null;
//        } catch (Exception error) {
//            log.error("Browser Closed Before Web Scanner. Error: " + error.getMessage());
//        }
//    }
//
//    private void stepsInsertManyDTO(SplitDTO processDTO, boolean isMany) {
//        currentBlockId = arScannedElementPane.validateBlockDB("block", this.currentBotJob.getId(), isMany);
//        if (currentBlockId > 0) {
//            performDataBase.loadInstructions(currentBotJob.getId(), currentBlockId, -1, "instruction");
//            List<InstructionLoad> instruc = performLists.getListInstruction();
//
//            int nextOrder = instruc.size() + 1;
//
//            instructionList.clear();
//            targetElementHelper.initialize(performActions, arScannedElementPane);
//
//            for (ElementDTO elementDTO : processDTO.getElementDetails()) {
//                TargetElement targetEach = targetElementHelper.extractPickClone(elementDTO);
//
//                WebElement elementFound = performActions.findWebElement(targetEach);
//                if (targetEach.getElement() == null && elementFound != null) {
//                    targetEach.setElement(elementFound);
//                }
//
//                performActions.defineSavedReferenced(targetEach);
//
//                if (!isMany) {
//                    if (!Strings.isNullOrEmpty(arScannedElementPane
//                                    .defineNameField
//                                    .getText()
//                                    .trim())
//                            && !targetEach
//                                    .getDefinedName()
//                                    .equalsIgnoreCase(arScannedElementPane
//                                            .defineNameField
//                                            .getText()
//                                            .trim())) {
//
//                        targetEach.setDefinedName(
//                                arScannedElementPane.defineNameField.getText().trim());
//
//                        SwingUtilities.invokeLater(() -> {
//                            arScannedElementPane.defineNameField.setText("");
//                            arScannedElementPane.searchAttribValueField.setText("");
//                        });
//                    }
//                    arScannedElementPane.targetSelected = targetEach;
//                }
//
//                arScannedElementPane.prepareToInsertElementDTO(
//                        instructionList, currentBlockId, nextOrder, targetEach, true);
//                nextOrder++;
//            }
//
//            if (!instructionList.isEmpty()) {
//
//                ErrorMessage errorMessage = performDataBase.insertInstructionsBatch(
//                        "botJobTasks",
//                        instructionList,
//                        currentBotJob.getId(),
//                        currentBlockId,
//                        currentBotJob.getHomeBankingId());
//
//                if (instructionList.size()
//                        != performDataBase.getIdsInstrucAfter().size()) {
//                    log.error(
//                            "Error Inserting ALL Elements - Expected (from list):{} - Actual (inserted): {}",
//                            instructionList.size(),
//                            performDataBase.getIdsInstrucAfter().size());
//                    performMessage.errorMessage(
//                            "Error Inserting ALL Elements",
//                            "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Batch Insertion Failed
// ❌</span>",
//                            "<span style='color: #E65100; font-weight: bold;'>Mismatch detected:</span> The number of
// inserted instructions does not match the expected size.",
//                            "<span style='font-style: italic;'>Expected (from list):</span> " +
// instructionList.size(),
//                            "<span style='font-style: italic;'>Actual (inserted):</span> "
//                                    + performDataBase.getIdsInstrucAfter().size(),
//                            0);
//
//                    sendStatusButton("scannerGrid", "activate-insert-all", "Insert All Elements button activated");
//
//                    return;
//                }
//
//                if (errorMessage == null) {
//                    for (int i = 0; i < instructionList.size(); i++) {
//                        InstructionLoad instruction = instructionList.get(i);
//                        Integer newId = performDataBase.getIdsInstrucAfter().get(i);
//                        instruction.setId(newId);
//                    }
//
//                    errorMessage = performDataBase.insertReferencesBatch(instructionList);
//                }
//
//                updateBotJobTasks(this.currentBotJob.getId());
//                sendStatusButton("scannerGrid", "activate-insert-all", "Insert All Elements button activated");
//
//                if (errorMessage != null) {
//                    performMessage.errorMessageOperationFailed(errorMessage);
//                }
//            }
//        } else {
//            sendStatusButton("scannerGrid", "activate-insert-all", "Insert All Elements button activated");
//        }
//    }
//
//    private void sendStatusButton(String sessionId, String operationId, String message) {
//        WebSocketSignal webSockteSocketSignal = WebSocketSignal.builder()
//                .sessionId(sessionId)
//                .operationId(operationId)
//                .message(message)
//                .build();
//
//        String jsonData = gson.toJson(webSockteSocketSignal);
//
//        webSocketSessionManager.sendMessageJson(currentBotJob.getHomeBankingId(), sessionId, jsonData, operationId);
//    }
//
//    public void destroyPanel() {
//        arScannedElementPane.destroy();
//    }
//
//    public void updateBotJobTasks(int currentBotJobId) {
//        ErrorMessage errorMessage = performDBEngine.loadCompleteJobs(currentBotJobId);
//        if (errorMessage != null) {
//            performMessage.errorMessageOperationFailed(errorMessage);
//            return;
//        }
//
//        String jsonData = "[]";
//        if (!performLists.getListBotJob().isEmpty()) {
//            List<InstructionLoad> blockLoopInstructions =
// performLists.buildJsonViewData(performLists.getListBotJob());
//            jsonData = gson.toJson(blockLoopInstructions);
//        }
//        webSocketSessionManager.sendMessageJson(
//                currentBotJob.getHomeBankingId(), "botJobTasks", jsonData, "updateInstructions");
//    }
//
//    private SplitDTO parseSplitDTO(JsonObject jsonEntry) {
//        if (jsonEntry == null || jsonEntry.isEmpty()) {
//            log.warn("parseSplitDTO called with null or empty JSON object");
//            return null;
//        }
//
//        try {
//            if (jsonEntry.has("body")) {
//                String bodyStr = jsonEntry.get("body").getAsString();
//                JsonObject inner = gson.fromJson(bodyStr, JsonObject.class);
//                return gson.fromJson(inner, SplitDTO.class);
//            }
//
//            return gson.fromJson(jsonEntry, SplitDTO.class);
//
//        } catch (Exception error) {
//            log.error("Cannot parse SplitDTO: " + error.getMessage() + " | JSON: " + jsonEntry);
//            return null;
//        }
//    }
// }

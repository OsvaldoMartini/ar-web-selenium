// package com.allinweb.ch.component.scene;
//
// import com.allinweb.ch.component.pane.ARNewCommandPane;
// import com.allinweb.ch.component.pane.base.IARPane;
// import com.allinweb.ch.facade.PerformDBEngine;
// import com.allinweb.ch.facade.PerformDataBase;
// import com.allinweb.ch.facade.PerformMessage;
// import com.allinweb.ch.model.BlockMoveDTO;
// import com.allinweb.ch.model.InstructionLoad;
// import com.allinweb.ch.model.SplitDTO;
// import com.allinweb.ch.util.ErrorMessage;
// import com.google.gson.Gson;
// import com.google.gson.JsonObject;
// import com.google.gson.JsonParser;
// import lombok.Getter;
// import lombok.Setter;
// import lombok.extern.slf4j.Slf4j;
//
// import javax.swing.JComponent;
// import javax.swing.JDialog;
// import javax.swing.SwingUtilities;
// import javax.swing.WindowConstants;
// import javax.websocket.ClientEndpoint;
// import javax.websocket.ContainerProvider;
// import javax.websocket.OnClose;
// import javax.websocket.OnError;
// import javax.websocket.OnMessage;
// import javax.websocket.OnOpen;
// import javax.websocket.Session;
// import javax.websocket.WebSocketContainer;
// import java.awt.Dialog;
// import java.awt.Frame;
// import java.awt.Window;
// import java.io.IOException;
// import java.net.URI;
// import java.util.List;
// import java.util.concurrent.*;
//
/// **
// * Swing-based replacement for the original JavaFX ARNewCommandScene.
// *
// * Responsibilities:
// *  - WebSocket client used by the "new command" dialog
// *  - Opens a Swing JDialog containing {@link ARNewCommandPane}
// */
// @ClientEndpoint
// @Slf4j
// public class ARNewCommandScene {
//
//    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
//    private static final CountDownLatch latch = new CountDownLatch(1);
//
//    private static final double DIALOG_HEIGHT = 300d;
//    private static final double DIALOG_WIDTH  = 800d;
//    private static final String TITLE = "Add/Update Operations";
//
//    private static final PerformMessage performMessage   = PerformMessage.getInstance();
//    private static final PerformDBEngine performDBEngine = PerformDBEngine.getInstance();
//    private static final PerformDataBase performDataBase = PerformDataBase.getInstance();
//    private static final ARNewCommandPane arNewCommandPane = ARNewCommandPane.getInstance();
//
//    protected static volatile ARNewCommandScene instance;
//
//    private final Gson gson = new Gson();
//
//    public boolean isConnectWebSocket = false;
//
//    @Getter
//    @Setter
//    private SplitDTO splitDTO;
//
//    private String previousBlock = null;
//
//    private final ExecutorService executorWebSocket = Executors.newSingleThreadExecutor();
//    private Session session;
//
//    // Swing dialog instead of JavaFX Stage
//    private JDialog modalDialog;
//
//    // Private constructor to prevent instantiation
//    private ARNewCommandScene() {
//        // no-op
//    }
//
//    public static ARNewCommandScene getInstance() {
//        if (instance == null) {
//            synchronized (ARNewCommandScene.class) {
//                if (instance == null) {
//                    instance = new ARNewCommandScene();
//                }
//            }
//        }
//        return instance;
//    }
//
//    // ------------------------------------------------------------------------
//    // WebSocket helpers
//    // ------------------------------------------------------------------------
//
//    private static void sendMessageJson(
//            int homeBankingId, Session session, String sessionId, String body, String operationId) {
//
//        if (session != null && session.isOpen()) {
//            try {
//                JsonObject jsonMessage = new JsonObject();
//                jsonMessage.addProperty("homeBankingId", homeBankingId);
//                jsonMessage.addProperty("sessionId", sessionId);
//                jsonMessage.addProperty("body", body);
//                if (operationId != null && !operationId.isEmpty()) {
//                    jsonMessage.addProperty("operationId", operationId);
//                }
//                session.getBasicRemote().sendText(jsonMessage.toString());
//            } catch (IOException e) {
//                log.error("Error sending message to session {}: {}", sessionId, e.getMessage());
//            }
//        } else {
//            log.error("Session {} not found or closed.", sessionId);
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
//                            session.getBasicRemote().sendText("ping-new-command-scene");
//                        }
//                    } catch (IOException e) {
//                        log.error("Error sending ping: {}", e.getMessage());
//                    }
//                },
//                0,
//                15,
//                TimeUnit.SECONDS);
//    }
//
//    // ------------------------------------------------------------------------
//    // WebSocket callbacks
//    // ------------------------------------------------------------------------
//
//    @OnMessage
//    public void onMessage(String message, Session session) {
//        log.info("Received: {}", message);
//
//        if (message == null || message.contains("CONNECT") || message.contains("ping")) {
//            // Ignore null, CONNECT, or ping messages
//            return;
//        }
//
//        int homeBankingId = -1;
//        String sessionId = null;
//        String type = "unknown";
//        String body = null;
//
//        try {
//            JsonObject jsonObjMSG = JsonParser.parseString(message).getAsJsonObject();
//
//            if (jsonObjMSG.has("homeBankingId")) {
//                homeBankingId = jsonObjMSG.get("homeBankingId").getAsInt();
//            }
//
//            body = jsonObjMSG.has("body") ? jsonObjMSG.get("body").getAsString() : "unknown";
//
//            if (!"unknown".equalsIgnoreCase(body)) {
//                try {
//                    JsonObject objSecond = JsonParser.parseString(body).getAsJsonObject();
//                    if (objSecond.has("type")) {
//                        type = objSecond.get("type").getAsString();
//                    }
//                } catch (Exception ignore) {
//                    // ignore
//                }
//            }
//
//            if ("unknown".equals(type) && jsonObjMSG.has("type")) {
//                type = jsonObjMSG.get("type").getAsString();
//            }
//
//            if ("unknown".equals(type) && jsonObjMSG.has("operationId")) {
//                type = jsonObjMSG.get("operationId").getAsString();
//            }
//
//            sessionId = jsonObjMSG.has("sessionId") ? jsonObjMSG.get("sessionId").getAsString() : null;
//
//            log.info("Decoded: homeBankingId={}, sessionId={}, type={}, body={}",
//                    homeBankingId, sessionId, type, body);
//
//            if (type == null || type.trim().isEmpty() || type.contains("CONNECT") || type.contains("ping")) {
//                return;
//            }
//
//            switch (type) {
//                case "UPDATE_BLOCKS": {
//                    BlockMoveDTO blockMoveDTO = gson.fromJson(body, BlockMoveDTO.class);
//
//                    if (previousBlock != null && !previousBlock.equals(type)) {
//                        arNewCommandPane.closePane();
//                        previousBlock = type;
//                    } else if (previousBlock == null) {
//                        previousBlock = type;
//                    }
//
//                    ErrorMessage errorMessage =
//                            arNewCommandPane.reloadDBBlocks(blockMoveDTO.getBotJobId(), "block");
//                    if (errorMessage != null) {
//                        performMessage.errorMessageOperationFailed(errorMessage);
//                    }
//                    break;
//                }
//                case "UPDATE_BLOCKS_COMP": {
//                    BlockMoveDTO blockMoveDTO = gson.fromJson(body, BlockMoveDTO.class);
//
//                    if (previousBlock != null && !previousBlock.equals(type)) {
//                        arNewCommandPane.closePane();
//                        previousBlock = type;
//                    } else if (previousBlock == null) {
//                        previousBlock = type;
//                    }
//
//                    ErrorMessage errorMessage =
//                            arNewCommandPane.reloadDBBlocks(blockMoveDTO.getHomeBankingId(), "component_block");
//                    if (errorMessage != null) {
//                        performMessage.errorMessageOperationFailed(errorMessage);
//                    }
//                    break;
//                }
//                case "INSERT_BEFORE":
//                case "INSERT_AFTER":
//                case "INSERT_NEW":
//                case "INSERT_AFTER_ELSEIF":
//                case "INSERT_BEFORE_ELSEIF":
//                case "EDIT_OPERATION": {
//                    try {
//                        SplitDTO splitDTO = gson.fromJson(jsonObjMSG, SplitDTO.class);
//
//                        String instTable = splitDTO.getSessionId().equals("componentTasks")
//                                ? "component_instruction"
//                                : "instruction";
//                        String blockTable = splitDTO.getSessionId().equals("componentTasks")
//                                ? "component_block"
//                                : "block";
//                        int whereId = splitDTO.getSessionId().equals("componentTasks")
//                                ? splitDTO.getHomeBankingId()
//                                : splitDTO.getBotJobId();
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
//                        ErrorMessage errorMessage =
//                                performDataBase.preDeleteNullBlocks(blockTable, whereId, instTable);
//                        if (errorMessage == null) {
//                            errorMessage = arNewCommandPane.reloadDBBlocks(whereId, blockTable);
//                        }
//                        if (errorMessage != null) {
//                            performMessage.errorMessageOperationFailed(errorMessage);
//                        }
//
//                        initialize(splitDTO);
//
//                        SwingUtilities.invokeLater(this::showModal);
//                    } catch (Exception error) {
//                        log.info("Cannot map SplitDTO from message body: {}", error.getMessage());
//                    }
//                    break;
//                }
//                default:
//                    // ignore
//                    break;
//            }
//
//        } catch (Exception error) {
//            log.error("Error processing message: {}", error.getMessage(), error);
//            if (type != null) {
//                sendMessageJson(homeBankingId, session, type,
//                        "Action type : \"" + type + "\"", "cannot be processed");
//            } else {
//                sendMessageJson(homeBankingId, session, type,
//                        "Closed processing message", "No \"type\" definition");
//            }
//        }
//    }
//
//    @OnOpen
//    public void onOpen(Session session) {
//        this.session = session;
//        latch.countDown();
//        log.info("Connected to WebSocket server at: {}", session.getRequestURI());
//        sendMessage("Hello from Swing ARNewCommandScene WebSocket client!");
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
//        log.info("Error: {}", throwable.getMessage());
//        stopKeepAlivePings();
//    }
//
//    // Method to send a message (currently no-op by design in original code)
//    public void sendMessage(String message) {
//        executorWebSocket.submit(() -> {
//            // Intentionally empty – kept for compatibility.
//            // Uncomment if you need active outbound messaging.
//            // if (session != null && session.isOpen()) {
//            //     try {
//            //         session.getBasicRemote().sendText(message);
//            //     } catch (Exception e) {
//            //         log.error("sendMessage error: {}", e.getMessage());
//            //     }
//            // }
//        });
//    }
//
//    public void initialize(SplitDTO splitDTO) {
//        this.splitDTO = splitDTO;
//    }
//
//    // ------------------------------------------------------------------------
//    // Swing dialog handling
//    // ------------------------------------------------------------------------
//
//    private String buildTitle() {
//        if (splitDTO == null) {
//            return TITLE;
//        }
//        String description = createDescriptionString(splitDTO);
//        return description != null ? TITLE + ": " + description : TITLE;
//    }
//
//    public void showModal() {
//        // Excel GOTO special handling
//        if (splitDTO != null && "EXCEL GOTO".equals(splitDTO.getActions())) {
//            String tableName = splitDTO.getSessionId().equals("componentTasks")
//                    ? "component_instruction"
//                    : "instruction";
//            int whereId = splitDTO.getSessionId().equals("componentTasks")
//                    ? splitDTO.getHomeBankingId()
//                    : splitDTO.getBotJobId();
//            try {
//                List<InstructionLoad> excelDataGoto =
//                        performDBEngine.loadExcelGotoBlock(whereId, tableName);
//
//                if (!excelDataGoto.isEmpty()) {
//                    splitDTO.setType("EDIT_OPERATION");
//                }
//            } catch (Exception error) {
//                log.warn("Error reading 'EXCEL GOTO' instructions: {}", error.getMessage());
//            }
//        }
//
//        arNewCommandPane.initialize(splitDTO);
//
//        if (modalDialog == null) {
//            // Try to find a reasonable owner window (optional)
//            Window owner = null;
//            for (Window w : Window.getWindows()) {
//                if (w.isActive()) {
//                    owner = w;
//                    break;
//                }
//            }
//
//            Frame ownerFrame = (owner instanceof Frame) ? (Frame) owner : null;
//
//            modalDialog = new JDialog(ownerFrame, buildTitle(), Dialog.ModalityType.MODELESS);
//            modalDialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
//
//            IARPane pane = buildPane();
//            if (pane != null) {
//                JComponent content = pane.createPane();
//                modalDialog.setContentPane(content);
//                modalDialog.setSize((int) DIALOG_WIDTH, (int) DIALOG_HEIGHT);
//                modalDialog.setLocationRelativeTo(owner);
//            } else {
//                log.error("Failed to build pane for modal.");
//                return;
//            }
//        }
//
//        modalDialog.setTitle(buildTitle());
//
//        if (!modalDialog.isVisible()) {
//            modalDialog.setVisible(true);
//        } else {
//            modalDialog.toFront();
//        }
//    }
//
//    public void closeModal() {
//        try {
//            if (modalDialog != null) {
//                modalDialog.dispose();
//            }
//            modalDialog = null;
//        } catch (Exception error) {
//            log.warn("Error closing ARNewCommandScene dialog: {}", error.getMessage());
//        }
//    }
//
//    public String createDescriptionString(SplitDTO splitDTO) {
//        if (splitDTO == null) {
//            return null;
//        }
//        return " " + splitDTO.getType().replace("_", " ")
//                + " -> Block Selected: " + splitDTO.getBlockName();
//    }
//
//    // ------------------------------------------------------------------------
//    // Public WebSocket entry point
//    // ------------------------------------------------------------------------
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
//                log.error("WebSocket connection failed sessionId: {} error: {}", sessionId, e.getMessage());
//            }
//        });
//    }
//
//    // helper to keep old pattern (was overriding in JavaFX version)
//    private IARPane buildPane() {
//        return arNewCommandPane;
//    }
// }

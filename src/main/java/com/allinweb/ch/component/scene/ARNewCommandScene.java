package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.model.*;
import com.allinweb.ch.component.pane.ARNewCommandPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import com.allinweb.ch.facade.PerformDBEngine;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.util.ARLogger;
import com.allinweb.ch.util.ErrorMessage;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javax.websocket.ClientEndpoint;
import javax.websocket.ContainerProvider;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import lombok.Getter;
import lombok.Setter;

@ClientEndpoint
public class ARNewCommandScene extends ARScene {

    protected static volatile ARNewCommandScene instance;

    // Private constructor to prevent instantiation
    private ARNewCommandScene() {

        super();
    }

    public static ARNewCommandScene getInstance() {
        if (instance == null) {
            synchronized (ARNewCommandScene.class) {
                if (instance == null) {
                    instance = new ARNewCommandScene();
                }
            }
        }
        return instance;
    }

    private final Gson gson = new Gson();
    private String previousBlock = null;

    public boolean isConnectWebSocket = false;

    private ExecutorService executorWebSocket = Executors.newSingleThreadExecutor();
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static final CountDownLatch latch = new CountDownLatch(1);
    private Session session;

    private void stopKeepAlivePings() {
        scheduler.shutdownNow();
    }

    private void startKeepAlivePings() {
        scheduler.scheduleAtFixedRate(
                () -> {
                    try {
                        if (session != null && session.isOpen()) {
                            session.getBasicRemote()
                                    .sendText("ping-new-command-scene"); // Or a specific keep-alive message
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

    @OnMessage
    public void onMessage(String message, Session session) {
        System.out.println("Received: " + message);
        if (message == null || message.contains("CONNECT") || message.contains("ping")) {
            // Ignore null, CONNECT, or ping messages
            message = message.replaceAll("ping-", "");
            // System.out.println("Active : " + message);
            return;
        }

        int homeBankingId = -1;
        String sessionId = null;
        String type = "unknown";
        String body = null;

        try {
            // Parse the incoming message (assuming JSON format)
            JsonObject jsonObjMSG = JsonParser.parseString(message).getAsJsonObject();

            // Extract homeBankingId
            if (jsonObjMSG.has("homeBankingId")) {
                homeBankingId = jsonObjMSG.get("homeBankingId").getAsInt();
            }

            // Extract body
            body = jsonObjMSG.has("body") ? jsonObjMSG.get("body").getAsString() : "unknown";

            // Determine type (priority: body.type → json.type → operationId)
            if (!"unknown".equalsIgnoreCase(body)) {
                try {
                    JsonObject objSecond = JsonParser.parseString(body).getAsJsonObject();
                    if (objSecond.has("type")) {
                        type = objSecond.get("type").getAsString();
                    }
                } catch (Exception ignore) {

                }
            }

            if ("unknown".equals(type) && jsonObjMSG.has("type")) {
                type = jsonObjMSG.get("type").getAsString();
            }

            if ("unknown".equals(type) && jsonObjMSG.has("operationId")) {
                type = jsonObjMSG.get("operationId").getAsString();
            }

            // Extract sessionId
            sessionId =
                    jsonObjMSG.has("sessionId") ? jsonObjMSG.get("sessionId").getAsString() : null;

            // Debug print (optional)
            System.out.printf(
                    "homeBankingId=%d, sessionId=%s, type=%s, body=%s%n", homeBankingId, sessionId, type, body);
            // After Decoding
            if (type == null || type.trim().isEmpty() || type.contains("CONNECT") || type.contains("ping")) {
                // Ignore null or empty messages
                type = type.replaceAll("ping-", "");
                // System.out.println("Active : " + type);
                return;
            }

            // Process the message based on its type
            switch (type) {
                case "UPDATE_BLOCKS":
                    BlockMoveDTO blockMoveDTO = gson.fromJson(body, BlockMoveDTO.class);
                    if (previousBlock != null && !previousBlock.equals(type)) {
                        arNewCommandPane.closePane();
                        previousBlock = type;
                    } else if (previousBlock == null) {
                        previousBlock = type;
                    }
                    ErrorMessage errorMessage = arNewCommandPane.reloadDBBlocks(blockMoveDTO.getBotJobId(), "block");
                    if (errorMessage != null) {
                        performMessage.errorMessage(
                                errorMessage.getErrorTitle(),
                                "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Operation Failed!</span> ❌",
                                "<span style='color: #E65100; font-weight: bold;'>Error Type:</span> "
                                        + errorMessage.getErrorHeader(),
                                "<span style='font-style: italic;'>Detail:</span> " + errorMessage.getErrorMessage(),
                                null,
                                0);
                    }
                    break;
                case "UPDATE_BLOCKS_COMP":
                    blockMoveDTO = gson.fromJson(body, BlockMoveDTO.class);

                    if (previousBlock != null && !previousBlock.equals(type)) {
                        arNewCommandPane.closePane();
                        previousBlock = type;
                    } else if (previousBlock == null) {
                        previousBlock = type;
                    }

                    errorMessage = arNewCommandPane.reloadDBBlocks(blockMoveDTO.getHomeBankingId(), "component_block");
                    if (errorMessage != null) {
                        performMessage.errorMessage(
                                errorMessage.getErrorTitle(),
                                "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Operation Failed!</span> ❌",
                                "<span style='color: #E65100; font-weight: bold;'>Error Type:</span> "
                                        + errorMessage.getErrorHeader(),
                                "<span style='font-style: italic;'>Detail:</span> " + errorMessage.getErrorMessage(),
                                null,
                                0);
                    }

                    break;
                case "INSERT_BEFORE":
                case "INSERT_AFTER":
                case "INSERT_NEW":
                case "INSERT_AFTER_ELSEIF":
                case "INSERT_BEFORE_ELSEIF":
                case "EDIT_OPERATION":
                    try {
                        SplitDTO splitDTO = gson.fromJson(jsonObjMSG, SplitDTO.class);
                        //                        if (previousBlock == null)  {
                        //                            previousBlock = type;
                        //                        }

                        //                this.webPageItems =
                        // performDataBase.loadWebPageFields(splitDTO.getBotJobId());

                        // Ensure JavaFX UI updates are done on the JavaFX Application Thread
                        String instTable = splitDTO.getSessionId().equals("componentTasks")
                                ? "component_instruction"
                                : "instruction";
                        String blockTable =
                                splitDTO.getSessionId().equals("componentTasks") ? "component_block" : "block";
                        int whereId = splitDTO.getSessionId().equals("componentTasks")
                                ? splitDTO.getHomeBankingId()
                                : splitDTO.getBotJobId();
                        String blockUpdate = splitDTO.getSessionId().equals("componentTasks")
                                ? "UPDATE_BLOCKS_COMP"
                                : "UPDATE_BLOCKS";

                        if (previousBlock != null && !previousBlock.equals(blockUpdate)) {
                            arNewCommandPane.closePane();
                            previousBlock = blockUpdate;
                        } else if (previousBlock == null) {
                            previousBlock = blockUpdate;
                        }

                        performDataBase.preDeleteNullBlocks(blockTable, whereId, instTable);

                        errorMessage = arNewCommandPane.reloadDBBlocks(whereId, blockTable);

                        if (errorMessage != null) {
                            performMessage.errorMessage(
                                    errorMessage.getErrorTitle(),
                                    "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Operation Failed!</span> ❌",
                                    "<span style='color: #E65100; font-weight: bold;'>Error Type:</span> "
                                            + errorMessage.getErrorHeader(),
                                    "<span style='font-style: italic;'>Detail:</span> "
                                            + errorMessage.getErrorMessage(),
                                    null,
                                    0);
                        }

                        initialize(splitDTO);
                        Platform.runLater(() -> showModal());
                    } catch (Exception error) {
                        ARLogger.getInstance(ARNewCommandScene.class).finer("Cannot Missing Value from  RowMoveDTO");
                    }
                    break;
                default:
                    break;
            }

        } catch (Exception error) {
            System.err.println("Closed processing message: " + error.getMessage());
            if (type != null) {
                sendMessageJson(homeBankingId, session, type, "Action type : \"" + type + "\"", "cannot be processed");
            } else {
                sendMessageJson(homeBankingId, session, type, "Closed processing message", "No \"type\" definition");
            }
        }
    }

    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        latch.countDown(); // Release the latch after connection is established
        System.out.println("Connected to WebSocket server at: " + session.getRequestURI());
        // Sending an initial message
        sendMessage("Hello from JavaFX ARNewCommandScene WebSocket client!");
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

    private Stage modalStage;
    private Scene modalScene;

    private static final Double SCENE_HEIGHT = 300D;
    private static final Double SCENE_WIDTH = 800D;
    private static final String TITLE = "Add/Update Operations";

    @Getter
    @Setter
    public SplitDTO splitDTO;

    private static final PerformMessage performMessage = PerformMessage.getInstance();
    private static final PerformDBEngine performDBEngine = PerformDBEngine.getInstance();
    private static final PerformDataBase performDataBase = PerformDataBase.getInstance();
    private static final ARNewCommandPane arNewCommandPane = ARNewCommandPane.getInstance();

    public void initialize(SplitDTO splitDTO) {
        this.splitDTO = splitDTO;
    }

    @Override
    public IARPane buildPane() {
        return arNewCommandPane;
    }

    @Override
    public Double getSceneHeight() {
        return SCENE_HEIGHT;
    }

    @Override
    public Double getSceneWidth() {
        return SCENE_WIDTH;
    }

    @Override
    public String getTitle() {
        String titleMsg = createDescriptionString(splitDTO);
        if (titleMsg != null) {
            return TITLE + ": " + titleMsg;
        } else {
            return TITLE;
        }
    }

    public void showModal() {
        if (splitDTO.getActions() != null && splitDTO.getActions().equals("EXCEL GOTO")) {

            String tableName =
                    splitDTO.getSessionId().equals("componentTasks") ? "component_instruction" : "instruction";
            int whereId = splitDTO.getSessionId().equals("componentTasks")
                    ? splitDTO.getHomeBankingId()
                    : splitDTO.getBotJobId();
            try {
                List<InstructionLoad> excelDataGoto = performDBEngine.loadExcelGotoBlock(whereId, tableName);

                if (!excelDataGoto.isEmpty()) {
                    splitDTO.setType("EDIT_OPERATION");
                }

            } catch (Exception error) {
                ARLogger.getInstance(ARNewCommandScene.class)
                        .severe("Error reading 'EXCEL GOTO' instructions: " + error.getMessage());
                //                    performMessage.errorMessage(
                //                            "Excel GOTO Detected",
                //                            "<span style='font-weight: bold;'>This Bot Job already has an </span><span
                // style='font-weight: bold; color: #e854c8;'>'Excel GOTO'</span><span style='font-weight: bold;'>
                // instruction.</span>",
                //                            "<span style='font-weight: bold; color: #FF4500;'>Only one  </span><span
                // style='font-weight: bold; color: #e854c8;'>'Excel GOTO'</span><span style='font-weight: bold; color:
                // #FF4500;'> instruction is necessary per Bot Job.</span>",
                //                            " This single instruction is sufficient to process <span
                // style='font-weight:
                // bold;'>all rows individually</span> from your Excel data.",
                //                            null,
                //                            0);
                //
                //                    return;

            }
        }
        arNewCommandPane.initialize(splitDTO);

        if (modalStage == null) {
            modalStage = new Stage();
            arNewCommandPane.setStage(modalStage);
            modalStage.getIcons().add(icon);
            IARPane pane = buildPane();
            if (pane != null) {
                modalScene = new Scene(pane.createPane(), getSceneWidth(), getSceneHeight());
                modalStage.setScene(modalScene);
                modalStage.setTitle(getTitle());
                modalStage.initModality(Modality.NONE);
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
                ARLogger.getInstance(ARNewCommandScene.class).severe("Failed to build pane for modal.");
                return;
            }
        }

        modalStage.setTitle(getTitle()); // Update title if it might have changed

        // Check if the stage is already showing
        if (!modalStage.isShowing()) {
            modalStage.showAndWait(); // Show and wait only if not already showing
        }
    }

    public void closeModal() {
        try {
            if (modalStage != null) { // && modalStage.isShowing()) {
                modalStage.close();
            }
            modalStage = null;
        } catch (Exception error) {

        }
    }

    public String createDescriptionString(SplitDTO splitDTO) {
        // Ensure there are updatedRows to work with
        if (splitDTO == null) {
            return "No updated rows available";
        }

        // Construct the final string
        String result = " " + splitDTO.getType().replace("_", " ") + " -> Block Selected: " + splitDTO.getBlockName();

        return result;
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

    private static void sendMessageJson(
            int homeBankingId, Session session, String sessionId, String body, String operationId) {
        if (session != null && session.isOpen()) {
            try {
                JsonObject jsonMessage = new JsonObject();
                jsonMessage.addProperty("homeBankingId", homeBankingId);
                jsonMessage.addProperty("sessionId", sessionId);
                jsonMessage.addProperty("body", body);
                if (operationId != null && !operationId.isEmpty()) {
                    jsonMessage.addProperty("operationId", operationId);
                }
                session.getBasicRemote().sendText(jsonMessage.toString());
            } catch (IOException e) {
                System.err.println("Error sending message to session " + sessionId + ": " + e.getMessage());
            }
        } else {
            System.err.println("Session " + sessionId + " not found or closed.");
        }
    }
}

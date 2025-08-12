package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.model.InstructionLoadDTO;
import com.allinweb.ch.component.model.RowMoveDTO;
import com.allinweb.ch.component.pane.ARNewCommandPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.util.ARLogger;
import com.allinweb.ch.util.ARPropertyManager;
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
        // Initialize if necessary
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
        if (message == null || message.contains("CONNECT") || message.contains("ping")) {
            // Ignore null or empty messages
            message = message.replaceAll("ping-", "");
            System.out.println("Active : " + message);
            return;
        }
        String type = null;
        int homeBankingId = -1;
        try {
            // Parse the incoming message (assuming JSON format)
            JsonObject jsonObjMSG = JsonParser.parseString(message).getAsJsonObject();
            homeBankingId = jsonObjMSG.has("homeBankingId")
                    ? Integer.parseInt(jsonObjMSG.get("homeBankingId").getAsString())
                    : -1;

            type = jsonObjMSG.has("type") ? jsonObjMSG.get("type").getAsString() : "unknown";
            String sessionId =
                    jsonObjMSG.has("sessionId") ? jsonObjMSG.get("sessionId").getAsString() : "unknown";

            // After Decoding
            if (type == null || type.trim().isEmpty() || type.contains("CONNECT") || type.contains("ping")) {
                // Ignore null or empty messages
                type = type.replaceAll("ping-", "");
                System.out.println("Active : " + type);
                return;
            }

            try {

                RowMoveDTO rowUpdateDTO = gson.fromJson(jsonObjMSG, RowMoveDTO.class);
                //                this.webPageItems = performDataBase.loadWebPageFields(rowUpdateDTO.getBotJobId());

                // Ensure JavaFX UI updates are done on the JavaFX Application Thread
                initialize(rowUpdateDTO);
                Platform.runLater(() -> showModal());
            } catch (Exception error) {
                ARLogger.getInstance(ARNewCommandScene.class).finer("Cannot Missing Value from  RowMoveDTO");
            }
            //                });

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
    public RowMoveDTO rowMoveDTO;

    private static final ARNewCommandPane arNewCommandPane;
    private static final PerformDataBase performDataBase;
    private static final ARPropertyManager arPropertyManager;

    static {
        arPropertyManager = ARPropertyManager.getInstance();
        performDataBase = PerformDataBase.getInstance();
        arNewCommandPane = ARNewCommandPane.getInstance();
    }

    public void initialize(RowMoveDTO rowMoveDTO) {
        this.rowMoveDTO = rowMoveDTO;
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
        String titleMsg = createDescriptionString(rowMoveDTO);
        if (titleMsg != null) {
            return TITLE + ": " + titleMsg;
        } else {
            return TITLE;
        }
    }

    public void showModal() {
        if (rowMoveDTO.getUpdatedRows().get(0).getActions() != null
                && rowMoveDTO.getUpdatedRows().get(0).getActions().equals("EXCEL GOTO")) {
            List<InstructionLoadDTO> excelDataGoto =
                    performDataBase.loadExcelGotoBlock(rowMoveDTO.getHomeBankingId(), rowMoveDTO.getBotJobId());

            if (!excelDataGoto.isEmpty()) {
                rowMoveDTO.setType("EDIT_OPERATION");
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

        arNewCommandPane.initialize(rowMoveDTO);

        if (modalStage == null) {
            modalStage = new Stage();
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

    public String createDescriptionString(RowMoveDTO rowMoveDTO) {
        // Ensure there are updatedRows to work with
        if (rowMoveDTO.getUpdatedRows() == null || rowMoveDTO.getUpdatedRows().isEmpty()) {
            return "No updated rows available";
        }

        // Construct the final string
        String result =
                " " + rowMoveDTO.getType().replace("_", " ") + " -> Block Selected: " + rowMoveDTO.getBlockName();

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

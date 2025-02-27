package com.allinweb.ch.socket;

import com.allinweb.ch.component.model.BlockDetailsDTO;
import com.allinweb.ch.component.model.BlockMoveDTO;
import com.allinweb.ch.component.model.BlockOrderDTO;
import com.allinweb.ch.component.model.BlockOrderDetailDTO;
import com.allinweb.ch.component.model.BlockSplitDTO;
import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.model.DeleteBlockDTO;
import com.allinweb.ch.component.model.ElementDTO;
import com.allinweb.ch.component.model.ElementSplitDTO;
import com.allinweb.ch.component.model.InstructionLoadDTO;
import com.allinweb.ch.component.model.RollBackBlocksDTO;
import com.allinweb.ch.component.model.RowMoveDTO;
import com.allinweb.ch.component.pane.ARScannedElementPane;
import com.allinweb.ch.component.scene.ARExcelFileScene;
import com.allinweb.ch.component.scene.ARNewCommandScene;
import com.allinweb.ch.component.scene.ARSaveComponentScene;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.util.ARConstants;
import com.allinweb.ch.util.ARLogger;
import com.allinweb.ch.util.ComboBoxVars;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

@ServerEndpoint("/websocket")
public class SimpleWebSocketServer {

    //    // Static final variable to hold the singleton instance
    //    protected static final SingletonSupplier<SimpleWebSocketServer> instance = () -> new SimpleWebSocketServer();
    //
    //    // Private constructor to prevent instantiation
    //    private SimpleWebSocketServer() {
    //        // Initialize if necessary
    //    }
    //
    //    public void initializeSimpleWebSocketServer() {}
    //
    //    // Public method to access the singleton instance
    //    public static SimpleWebSocketServer getInstance() {
    //        return instance.get();
    //    }

    private static final Map<String, Session> activeSessions = new ConcurrentHashMap<>();

    // Store active sessions when a new connection is established
    public static void addSession(String sessionId, Session session) {
        activeSessions.put(sessionId, session);
    }

    // Remove session when disconnected
    public static void removeSession(String sessionId) {
        activeSessions.remove(sessionId);
    }

    public static Map<String, Session> getAllSessions() {
        return activeSessions;
    }

    private String generateCustomSessionId(Session session) {
        // Get the current date in yyyyMMdd format
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
        String date = dateFormat.format(new Date());

        // Generate custom session ID with the date and timestamp
        return date + "-" + System.currentTimeMillis();
    }

    private static final PerformDataBase performDataBase;
    //    private static final PerformDBSavedBlock performDBSavedBlock;
    private static final PerformMessage performMessage;
    // Static block to initialize
    static {
        performDataBase = PerformDataBase.getInstance();
        //        performDBSavedBlock = PerformDBSavedBlock.getInstance();
        performMessage = PerformMessage.getInstance();
    }

    // Store all connected sessions
    private final Gson gson = new Gson();
    private ObservableList<ComboBoxVars> webPageItems = FXCollections.observableArrayList();

    private List<BotJobLoadDTO> botJobLoadList = new ArrayList<>();

    @OnOpen
    public void onOpen(Session session) {
        // Get the sessionId from the query parameter passed by the frontend
        String sessionId = null;
        try {
            sessionId = session.getRequestParameterMap().get("sessionId").get(0);

            if (!Strings.isNullOrEmpty(sessionId)) {
                addSession(sessionId, session);
            } else {
                addSession(generateCustomSessionId(session), session);
            }
        } catch (Exception noSessionId) {
            addSession(generateCustomSessionId(session), session);
        }

        if (sessionId != null) {
            addSession(sessionId, session); // Store the session with the custom ID
            System.out.println("New connection: Custom Session ID = " + sessionId);
        } else {
            System.out.println("No session ID provided by client");
        }
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        if (message == null || message.trim().isEmpty() || message.contains("CONNECT")) {
            // Ignore null or empty messages
            return;
        }

        String type = null;
        try {
            // Parse the incoming message (assuming JSON format)
            JsonObject jsonMessage = JsonParser.parseString(message).getAsJsonObject();
            type = jsonMessage.has("type") ? jsonMessage.get("type").getAsString() : "unknown";
            String sessionId =
                    jsonMessage.has("sessionId") ? jsonMessage.get("sessionId").getAsString() : "unknown";

            // if Not have Session and does not Exist into the activeSessions
            // Is Going to Handle the Control
            if (Strings.isNullOrEmpty(sessionId)) {
                sessionId = null;
                try {
                    sessionId =
                            session.getRequestParameterMap().get("sessionId").get(0);
                    if (!Strings.isNullOrEmpty(sessionId)) {
                        if (!activeSessions.containsKey(sessionId)) {
                            if (!activeSessions.get(sessionId).isOpen()) {
                                addSession(sessionId, session);
                            }
                        }
                    } else {
                        addSession(generateCustomSessionId(session), session);
                    }

                } catch (Exception noSessionId) {
                    addSession(generateCustomSessionId(session), session);
                }
            }

            // Process the message based on its type
            switch (type) {
                case "broadcast":
                    String broadcastMessage = jsonMessage.get("body").getAsString();
                    broadcastMessageToAll(broadcastMessage);
                    break;
                case "echo":
                    sendMessageJson(
                            sessionId, "Echo: " + jsonMessage.get("body").getAsString(), "sessionId: " + sessionId);
                    break;
                default:
                    handleMessageByType(type, jsonMessage, session, sessionId);
                    break;
            }
        } catch (Exception error) {
            System.err.println("Error processing message: " + error.getMessage());
            if (type != null) {
                sendMessageJson(session, "Action type : \"" + type + "\"", "cannot be processed");
            } else {
                sendMessageJson(session, "Error processing message", "No \"type\" definition");
            }
        }
    }

    private void handleMessageByType(String type, JsonObject jsonEntry, Session session, String sessionId) {
        // Dispatch to the correct method based on the message type

        int botJobIdTask = -1;
        int homeBankingId = -1;
        String sessionIdToSend = null;
        boolean alreadySent = false;

        switch (type) {
            case "SEARCH_TOOL":
                // Extract the "body" field from the JsonObject
                ElementSplitDTO elementSplitDTO = gson.fromJson(jsonEntry, ElementSplitDTO.class);
                //                elementSplitDTO.setType("RETURN FROM MARTINI Total Rows: " +
                // elementSplitDTO.getDetails().length);
                sessionIdToSend = elementSplitDTO.getSessionId();
                botJobIdTask = elementSplitDTO.getBotJobId();
                homeBankingId = elementSplitDTO.getHomeBankingId();

                if (sessionIdToSend.equals("scannerGrid")) {
                    String jsonData = gson.toJson(elementSplitDTO);
                    sendMessageJson(sessionIdToSend, jsonData, null);
                    //                    broadcastMessageToAll(jsonData);
                    performMessage.outputJsonElementDTO(elementSplitDTO.getDetails());
                }

                alreadySent = true;

                break;
            case "NEW_ELEMENT_DTO":
            case "DEL_ELEMENT_DTO":
            case "DETAILS_ELEMENT_DTO":
                // Extract the "body" field from the JsonObject
                ElementSplitDTO processDTO = gson.fromJson(jsonEntry, ElementSplitDTO.class);
                middleWareMsg(processDTO);
                break;
            case "RESPONSE_BACK":
                // Extract the "body" field from the JsonObject
                BlockSplitDTO received = gson.fromJson(jsonEntry, BlockSplitDTO.class);
                received.setType("MARTINI");
                String jsonData = gson.toJson(received);
                sendMessageJson(session, jsonData, null);
                alreadySent = true;
                break;
            case "BLOCKS_COMPONENT":
                BlockSplitDTO blockComponentDTO = gson.fromJson(jsonEntry, BlockSplitDTO.class);
                createBlockComponent(blockComponentDTO);
                alreadySent = true;
                break;
            case "BLOCKS_SPLITTER":
                BlockSplitDTO blockSplitDTO = gson.fromJson(jsonEntry, BlockSplitDTO.class);

                sessionIdToSend = blockSplitDTO.getSessionId();
                botJobIdTask = blockSplitDTO.getBotJobId();
                homeBankingId = blockSplitDTO.getHomeBankingId();
                alreadySent = false;

                splitBlocks(blockSplitDTO);

                break;
            case "BLOCK_MOVE":
                BlockMoveDTO blockMoveDTO = gson.fromJson(jsonEntry, BlockMoveDTO.class);

                sessionIdToSend = blockMoveDTO.getSessionId();
                botJobIdTask = blockMoveDTO.getBotJobId();
                homeBankingId = blockMoveDTO.getHomeBankingId();
                alreadySent = false;

                moveBlock(blockMoveDTO);

                break;
            case "ROW_UPDATE":
                RowMoveDTO rowUpdateDTO = gson.fromJson(jsonEntry, RowMoveDTO.class);

                sessionIdToSend = rowUpdateDTO.getSessionId();
                botJobIdTask = rowUpdateDTO.getBotJobId();
                homeBankingId = rowUpdateDTO.getHomeBankingId();
                alreadySent = false;

                rowUpdate(rowUpdateDTO);

                break;
            case "ROW_MOVE":
                RowMoveDTO rowMoveDTO = gson.fromJson(jsonEntry, RowMoveDTO.class);

                sessionIdToSend = rowMoveDTO.getSessionId();
                botJobIdTask = rowMoveDTO.getBotJobId();
                homeBankingId = rowMoveDTO.getHomeBankingId();
                alreadySent = false;

                if (performDataBase.updateMoveRowsOrder(rowMoveDTO.getUpdatedRows())
                        && rowMoveDTO.getDeleteBlockId() != null
                        && rowMoveDTO.getDeleteBlockId() > -1) {
                    performDataBase.deleteBlock(rowMoveDTO.getBotJobId(), rowMoveDTO.getDeleteBlockId());

                    performDataBase.updateBlockOrderNumber(
                            performDataBase.selectAllBlocks(rowMoveDTO.getBotJobId()), true);
                }

                break;
            case "INSERT_BEFORE":
            case "INSERT_AFTER":
            case "INSERT_NEW":
            case "INSERT_AFTER_ELSEIF":
            case "INSERT_BEFORE_ELSEIF":
            case "EDIT_OPERATION":
                RowMoveDTO insertBeforeDTO = gson.fromJson(jsonEntry, RowMoveDTO.class);

                sessionIdToSend = insertBeforeDTO.getSessionId();

                injectStepAfterOrBefore(sessionIdToSend, insertBeforeDTO);

                alreadySent = true;

                break;
            case "BLOCK_EXCEL_FILE":
                BlockDetailsDTO blockExcelDTO = gson.fromJson(jsonEntry, BlockDetailsDTO.class);
                sessionIdToSend = blockExcelDTO.getSessionId();
                botJobIdTask = blockExcelDTO.getBotJobId();
                homeBankingId = blockExcelDTO.getHomeBankingId();
                alreadySent = false;

                excelFileBlock(blockExcelDTO);

                break;
            case "BLOCK_ORDER":
                BlockOrderDTO blockReorder = gson.fromJson(jsonEntry, BlockOrderDTO.class);
                if (blockReorder.getUpdatedBlocks().size() > 0) {

                    sessionIdToSend = blockReorder.getSessionId();
                    botJobIdTask = blockReorder.getBotJobId();
                    homeBankingId = blockReorder.getHomeBankingId();
                    alreadySent = false;

                    performDataBase.updateBlockOrderNumber(
                            performDataBase.selectAllBlocks(
                                    blockReorder.getUpdatedBlocks().get(0).getBotJobId()),
                            true);
                    performDataBase.deleteNullBlocks(
                            blockReorder.getUpdatedBlocks().get(0).getBotJobId());
                }
                break;
            case "INSTRUCTION_STATUS":
                InstructionLoadDTO InstructionLoadDTO = gson.fromJson(jsonEntry, InstructionLoadDTO.class);

                sessionIdToSend = InstructionLoadDTO.getSessionId();
                botJobIdTask = InstructionLoadDTO.getBotJobId();
                homeBankingId = InstructionLoadDTO.getHomeBankingId();
                alreadySent = false;

                performDataBase.updateInstructionStatus(InstructionLoadDTO);

                break;
            case "BLOCK_STATUS":
                RowMoveDTO blockStateDTO = gson.fromJson(jsonEntry, RowMoveDTO.class);

                sessionIdToSend = blockStateDTO.getSessionId();
                botJobIdTask = blockStateDTO.getBotJobId();
                homeBankingId = blockStateDTO.getHomeBankingId();
                alreadySent = false;

                performDataBase.updateBlockStatus(
                        blockStateDTO.getBotJobId(),
                        blockStateDTO.getBlockId(),
                        blockStateDTO.getBlockName(),
                        blockStateDTO.getBlockActive(),
                        3); // Block wait time Default 3 seconds per block

                performDataBase.updateInstructionStatusByBlock(
                        blockStateDTO.getBotJobId(), blockStateDTO.getBlockId(), blockStateDTO.getBlockActive());

                break;
            case "BLOCK_UPDATE":
                RowMoveDTO blockUpdateDTO = gson.fromJson(jsonEntry, RowMoveDTO.class);

                sessionIdToSend = blockUpdateDTO.getSessionId();
                botJobIdTask = blockUpdateDTO.getBotJobId();
                homeBankingId = blockUpdateDTO.getHomeBankingId();
                alreadySent = false;

                performDataBase.updateBlockName(
                        blockUpdateDTO.getBotJobId(), blockUpdateDTO.getBlockId(), blockUpdateDTO.getBlockName());

                this.botJobLoadList = performDataBase.loadBotJobComplete(blockUpdateDTO.getBotJobId());

                break;
            case "DELETE_INSTRUCTION":
                InstructionLoadDTO deleteInstructionLoadDTO = gson.fromJson(jsonEntry, InstructionLoadDTO.class);

                sessionIdToSend = deleteInstructionLoadDTO.getSessionId();
                botJobIdTask = deleteInstructionLoadDTO.getBotJobId();
                homeBankingId = deleteInstructionLoadDTO.getHomeBankingId();
                alreadySent = false;

                if (sessionIdToSend.equals("botJobTasks")) {
                    performDataBase.deleteInstruction(deleteInstructionLoadDTO.getBotJobId(), deleteInstructionLoadDTO);
                } else if (sessionIdToSend.equals("componentTasks")) {
                    performDataBase.deleteComponent(deleteInstructionLoadDTO);
                }

                List<InstructionLoadDTO> rowList = performDataBase.getInstructionsByBlockId(
                        deleteInstructionLoadDTO.getBotJobId(), deleteInstructionLoadDTO.getBlockId());
                performDataBase.reorderInstructions(rowList);

                break;
            case "DELETE_BLOCK":
                DeleteBlockDTO deleteBlockDTO = gson.fromJson(jsonEntry, DeleteBlockDTO.class);

                sessionIdToSend = deleteBlockDTO.getSessionId();
                botJobIdTask = deleteBlockDTO.getBotJobId();
                homeBankingId = deleteBlockDTO.getHomeBankingId();
                alreadySent = false;

                performDataBase.deleteBlock(deleteBlockDTO);

                break;
            case "BLOCK_ROLLBACK":
                RollBackBlocksDTO rollBackBlocksDTO = gson.fromJson(jsonEntry, RollBackBlocksDTO.class);

                sessionIdToSend = rollBackBlocksDTO.getSessionId();
                botJobIdTask = rollBackBlocksDTO.getBotJobId();
                homeBankingId = rollBackBlocksDTO.getHomeBankingId();

                alreadySent = false;

                performDataBase.rollBackBlocksRows(rollBackBlocksDTO);
                performDataBase.deleteNullBlocks(rollBackBlocksDTO.getBotJobId());

                break;

            default:
                sendMessageJson(session, "Action type : \"" + type + "\"", "cannot be processed");
                break;
        }

        if (!alreadySent && sessionId.equals("botJobTasks")) {
            this.botJobLoadList = performDataBase.loadBotJobComplete(botJobIdTask);
            if (botJobLoadList.size() > 0) {
                List<InstructionLoadDTO> blockLoopInstructions = performDataBase.buildJsonViewData(botJobLoadList);

                String jsonData = gson.toJson(blockLoopInstructions);

                sendMessageJson(sessionIdToSend, jsonData, "updateInstructions");
            }
        } else if (!alreadySent && sessionId.equals("componentTasks")) {
            this.botJobLoadList = performDataBase.loadComponentsComplete(homeBankingId);
            if (botJobLoadList.size() > 0) {
                List<InstructionLoadDTO> blockLoopInstructions = performDataBase.buildJsonViewData(botJobLoadList);

                String jsonData = gson.toJson(blockLoopInstructions);

                sendMessageJson(sessionIdToSend, jsonData, "updateInstructions");
            }
        }
    }

    private void middleWareMsg(ElementSplitDTO processDTO) {
        if (processDTO.getDetails() != null && processDTO.getDetails().length > 0) {
            ElementDTO elementDTO = processDTO.getDetails()[0];
            if (processDTO.getType().equals("DETAILS_ELEMENT_DTO")) {
                sendMessageJson(
                        "scannerReceiver", "Action type : \"" + "scannerReceiver" + "\"", "cannot be processed");
            }
        }
    }

    @OnClose
    public void onClose(Session session) {
        // Clean up session when it closes
        String sessionId = getSessionIdBySession(session);
        if (sessionId != null) {
            removeSession(sessionId);
            System.out.println("Connection closed: Session ID = " + sessionId);
        }
    }

    // Method to get the session ID based on the session object
    private String getSessionIdBySession(Session session) {
        return activeSessions.entrySet().stream()
                .filter(entry -> entry.getValue().equals(session))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        System.err.println("Error in session " + session.getId() + ": " + throwable.getMessage());
        try {
            if (session.isOpen()) {
                session.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing session: " + e.getMessage());
        }
    }

    private void broadcastMessageToAll(String message) {
        for (Session session : activeSessions.values()) { // Looping correctly
            if (session.isOpen()) {
                sendMessageJson(session, message, null);
            }
        }
    }

    //    private void broadcastMessageToAll(String message) {
    //        synchronized (sessions) {
    //            for (Session session : sessions) {
    //                if (session.isOpen()) {
    //                    sendMessageJson(session, message, null);
    //                }
    //            }
    //        }
    //    }

    private void sendMessage(Session session, String message) {
        try {
            session.getBasicRemote().sendText(message);
        } catch (IOException e) {
            System.err.println("Error sending message to session " + session.getId() + ": " + e.getMessage());
        }
    }

    // Method to send a message to a specific session ID
    public static void sendMessageJson(String sessionId, String msg1, String msg2) {
        Session session = activeSessions.get(sessionId);

        if (session != null && session.isOpen()) {
            try {
                JsonObject jsonMessage = new JsonObject();
                jsonMessage.addProperty("body", msg1);
                if (msg2 != null && !msg2.isEmpty()) {
                    jsonMessage.addProperty("operationId", msg2);
                }
                session.getBasicRemote().sendText(jsonMessage.toString());
            } catch (IOException e) {
                System.err.println("Error sending message to session " + sessionId + ": " + e.getMessage());
            }
        } else {
            System.err.println("Session " + sessionId + " not found or closed.");
        }
    }

    private void sendMessageJson(Session session, String msg1, String msg2) {
        try {
            // Create a JSON object with the key "body" and the provided message
            JsonObject jsonMessage = new JsonObject();
            jsonMessage.addProperty("body", msg1);
            if (!Strings.isNullOrEmpty(msg2)) {
                jsonMessage.addProperty("footer", msg2);
            }
            // Convert the JSON object to a string
            String jsonString = jsonMessage.toString();

            // Send the JSON string over WebSocket
            session.getBasicRemote().sendText(jsonString);
        } catch (IOException e) {
            System.err.println("Error sending message to session " + session.getId() + ": " + e.getMessage());
        }
    }

    // Method to extract the type field from a JSON string
    public static String extractType(String json) {
        try {
            JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
            if (jsonObject.has("type")) {
                return jsonObject.get("type").getAsString();
            } else {
                return "Unknown type";
            }
        } catch (Exception e) {
            return "Invalid JSON";
        }
    }

    // Handle BLOCKS_SPLITTED message
    private void splitBlocks(BlockSplitDTO blockSplitDTO) {
        BlockDetailsDTO originalBlock = blockSplitDTO.getDetails().getOriginalBlock();
        BlockDetailsDTO newBlock = blockSplitDTO.getDetails().getNewBlock();
        List<BlockOrderDetailDTO> updatedBlock = blockSplitDTO.getDetails().getUpdatedBlocks();
        System.out.println("Original Block ID: " + originalBlock.getBlockId());
        System.out.println("New Block Name: " + newBlock.getBlockName());
        System.out.println("Updated Block: " + updatedBlock.size());
        newBlock.setForceOrder(true);

        int newBlockId = performDataBase.createNewBlock(newBlock);
        if (performDataBase.updateInstructionsSplitter(
                newBlock.getInstructions(), (int) originalBlock.getBlockId(), newBlockId)) {
            if (updatedBlock.size() > 0) {
                performDataBase.updateBlockOrderNumber(updatedBlock, false);
                //                performDataBase.updateBlockOrderNumber(
                //                        performDataBase.selectAllBlocks(updatedBlock.get(0).getBotJobId()), true);
            }
        }
    }

    // Handle BLOCK_MOVE message
    private void moveBlock(BlockMoveDTO blockMoveDTO) {
        List<BlockOrderDetailDTO> updatedBlocks = blockMoveDTO.getUpdatedBlocks();
        performDataBase.updateBlockOrderNumber(updatedBlocks, false);
    }

    private void rowUpdate(RowMoveDTO rowUpdateDTO) {
        if (rowUpdateDTO.getUpdatedRows().size() > 0) {
            performDataBase.rowsUpdateName(rowUpdateDTO.getUpdatedRows());
        }

        // Add business logic to handle ROW_MOVE
    }

    private void injectStepAfterOrBefore(String sessionId, RowMoveDTO rowMoveDTO) {

        if (rowMoveDTO.getUpdatedRows().size() > 0) {

            //            List<BotJobLoadDTO> botJobLoadList =
            // performDataBase.loadBotJobComplete(rowMoveDTO.getBotJobId());
            BotJobLoadDTO botJobLoad = performDataBase.loadBotJobById(rowMoveDTO.getBotJobId());

            if (!rowMoveDTO.getType().equals("INSERT_BEFORE_ELSEIF")
                    && !rowMoveDTO.getType().equals("INSERT_AFTER_ELSEIF")) {

                this.webPageItems = performDataBase.loadWebPageFields(rowMoveDTO.getBotJobId());

                // Ensure JavaFX UI updates are done on the JavaFX Application Thread
                Platform.runLater(() -> {
                    ARNewCommandScene newCommandScene =
                            new ARNewCommandScene(rowMoveDTO, botJobLoad, this.webPageItems, sessionId);
                    newCommandScene.show();
                });
            } else {

                if (rowMoveDTO.getUpdatedRows().size() > 0) {

                    int parentId = rowMoveDTO.getUpdatedRows().get(0).getParentId();
                    try {
                        // Run the instruction add in a separate Task

                        int newRowId = performDataBase.preFillInstruction(
                                "ELSEIF",
                                "ELSEIF",
                                ARConstants.ELSEIF,
                                ARConstants.ELSEIF,
                                1,
                                null,
                                null,
                                parentId,
                                rowMoveDTO,
                                botJobLoad,
                                false);

                    } catch (Exception e) {

                        ARLogger.getInstance(ARScannedElementPane.class)
                                .severe(String.format(
                                        "Cannot Insert \"Instruction\"  \"%s\"\nCannot be saved!\nError: %s",
                                        ARConstants.ELSEIF, e.getMessage()));
                    }
                }
            }
        }
    }

    private void excelFileBlock(BlockDetailsDTO blockExcelDTO) {
        // Ensure JavaFX UI updates are done on the JavaFX Application Thread
        Platform.runLater(() -> {
            ARExcelFileScene excelFileScene = new ARExcelFileScene(blockExcelDTO);
            excelFileScene.showModal();
        });
    }

    private void createBlockComponent(BlockSplitDTO blockSplitDTO) {
        // Ensure JavaFX UI updates are done on the JavaFX Application Thread

        BlockDetailsDTO blockDetailsDTO = blockSplitDTO.getDetails().getNewBlock();
        blockDetailsDTO.setHomeBankingId(blockSplitDTO.getHomeBankingId());
        blockDetailsDTO.setBotJobId(blockSplitDTO.getBotJobId());
        blockDetailsDTO.setSessionId(blockSplitDTO.getSessionId());
        if (blockDetailsDTO.getBlockDescription() == null) {
            blockDetailsDTO.setBlockDescription(blockDetailsDTO.getBlockName() + " description");
        }
        Platform.runLater(() -> {
            ARSaveComponentScene newSaveBlockScene = new ARSaveComponentScene(blockDetailsDTO);
            newSaveBlockScene.showModal();
        });
    }
}

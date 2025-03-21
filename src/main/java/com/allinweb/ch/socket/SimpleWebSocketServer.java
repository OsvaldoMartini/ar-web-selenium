package com.allinweb.ch.socket;

import com.allinweb.ch.component.model.BlockDetailsDTO;
import com.allinweb.ch.component.model.BlockMoveDTO;
import com.allinweb.ch.component.model.BlockOrderDTO;
import com.allinweb.ch.component.model.BlockOrderDetailDTO;
import com.allinweb.ch.component.model.BlockSplitDTO;
import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.model.DeleteBlockDTO;
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
import com.allinweb.ch.util.ErrorMessage;
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

    private static Map<String, Session> activeSessions = new ConcurrentHashMap<>();

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
                    String broadcastMessage = jsonObjMSG.get("body").getAsString();
                    broadcastMessageToAll(homeBankingId, broadcastMessage);
                    break;
                case "echo":
                    sendMessageJson(
                            homeBankingId,
                            sessionId,
                            "Echo: " + jsonObjMSG.get("body").getAsString(),
                            "sessionId: " + sessionId);
                    break;
                default:
                    handleMessageByType(type, jsonObjMSG, session, sessionId);
                    break;
            }
        } catch (Exception error) {
            System.err.println("Error processing message: " + error.getMessage());
            if (type != null) {
                sendMessageJson(homeBankingId, session, type, "Action type : \"" + type + "\"", "cannot be processed");
            } else {
                sendMessageJson(homeBankingId, session, type, "Error processing message", "No \"type\" definition");
            }
        }
    }

    private void handleMessageByType(String type, JsonObject jsonEntry, Session session, String sessionId) {
        // Dispatch to the correct method based on the message type

        int botJobIdTask = -1;
        int homeBankingId = -1;
        String sessionIdToSend = null;
        boolean alreadySentMgsSocket = false;

        switch (type) {
            case "SEARCH_TOOL":
                // Extract the "body" field from the JsonObject
                ElementSplitDTO elementSplitDTO = gson.fromJson(jsonEntry, ElementSplitDTO.class);
                //                elementSplitDTO.setType("RETURN FROM MARTINI Total Rows: " +
                // elementSplitDTO.getDetails().length);

                homeBankingId = elementSplitDTO.getHomeBankingId() != null ? elementSplitDTO.getHomeBankingId() : -1;
                sessionIdToSend = elementSplitDTO.getSessionId();

                if (sessionIdToSend.equals("scannerGrid-" + homeBankingId)) {
                    String jsonData = gson.toJson(elementSplitDTO);
                    sendMessageJson(homeBankingId, sessionIdToSend, jsonData, null);
                    //                    broadcastMessageToAll(jsonData);
                    performMessage.outputJsonElementDTO(elementSplitDTO.getDetails());
                }

                alreadySentMgsSocket = true;

                break;
            case "NEW_ELEMENT_DTO":
            case "SEND_ALL_ELEMENTS_DTO":
            case "DEL_ELEMENT_DTO":
            case "DETAILS_ELEMENT_DTO":
                // Extract the "body" field from the JsonObject
                ElementSplitDTO processDTO = gson.fromJson(jsonEntry, ElementSplitDTO.class);

                homeBankingId = processDTO.getHomeBankingId();
                sessionIdToSend = processDTO.getSessionId();
                //                botJobIdTask = processDTO.getBotJobId();

                if (processDTO.getDetails() != null && processDTO.getDetails().length > 0) {
                    sendMessageJson("scannerReceiver-" + homeBankingId, gson.toJson(processDTO)); // Sending as details
                }
                alreadySentMgsSocket = true;
                break;
            case "RESPONSE_BACK":
                // Extract the "body" field from the JsonObject
                BlockSplitDTO received = gson.fromJson(jsonEntry, BlockSplitDTO.class);
                received.setType("MARTINI");

                String jsonData = gson.toJson(received);

                homeBankingId = received.getHomeBankingId();
                sessionIdToSend = received.getSessionId();
                botJobIdTask = received.getBotJobId();

                sendMessageJson(homeBankingId, session, "Martini", jsonData, null);

                alreadySentMgsSocket = true;
                break;
            case "BLOCKS_COMPONENT":
                BlockSplitDTO blockComponentDTO = gson.fromJson(jsonEntry, BlockSplitDTO.class);

                homeBankingId = blockComponentDTO.getHomeBankingId();
                sessionIdToSend = blockComponentDTO.getSessionId();
                botJobIdTask = blockComponentDTO.getBotJobId();

                createBlockComponent(blockComponentDTO);

                alreadySentMgsSocket = true;
                break;
            case "COMPONENT_INJECT":
                BlockSplitDTO componentToInjectDTO = gson.fromJson(jsonEntry, BlockSplitDTO.class);

                homeBankingId = componentToInjectDTO.getHomeBankingId();
                sessionIdToSend = componentToInjectDTO.getSessionId();
                botJobIdTask = componentToInjectDTO.getBotJobId();

                injectBlockComponent(componentToInjectDTO);

                alreadySentMgsSocket = true;

                break;
            case "BLOCKS_SPLITTER":
                BlockSplitDTO blockSplitDTO = gson.fromJson(jsonEntry, BlockSplitDTO.class);

                homeBankingId = blockSplitDTO.getHomeBankingId();
                sessionIdToSend = blockSplitDTO.getSessionId();
                botJobIdTask = blockSplitDTO.getBotJobId();

                alreadySentMgsSocket = false;

                if ((sessionIdToSend != null && sessionIdToSend.matches(".*botJobTasks.*"))) {
                    splitBlocks(blockSplitDTO);
                } else if ((sessionIdToSend != null && sessionIdToSend.matches(".*componentTasks.*"))) {
                }

                break;
            case "BLOCK_MOVE":
                BlockMoveDTO blockMoveDTO = gson.fromJson(jsonEntry, BlockMoveDTO.class);

                homeBankingId = blockMoveDTO.getHomeBankingId();
                sessionIdToSend = blockMoveDTO.getSessionId();
                botJobIdTask = blockMoveDTO.getBotJobId();

                alreadySentMgsSocket = false;

                if ((sessionIdToSend != null && sessionIdToSend.matches(".*botJobTasks.*"))) {
                    moveBlock(blockMoveDTO);
                } else if ((sessionIdToSend != null && sessionIdToSend.matches(".*componentTasks.*"))) {
                }

                break;
            case "ROW_UPDATE":
                RowMoveDTO rowUpdateDTO = gson.fromJson(jsonEntry, RowMoveDTO.class);

                homeBankingId = rowUpdateDTO.getHomeBankingId();
                sessionIdToSend = rowUpdateDTO.getSessionId();
                botJobIdTask = rowUpdateDTO.getBotJobId();

                alreadySentMgsSocket = false;

                if (rowUpdateDTO.getUpdatedRows().size() > 0) {
                    if ((sessionIdToSend != null && sessionIdToSend.matches(".*botJobTasks.*"))) {
                        performDataBase.rowsUpdateName(rowUpdateDTO.getUpdatedRows());
                    } else if ((sessionIdToSend != null && sessionIdToSend.matches(".*componentTasks.*"))) {
                        performDataBase.rowsCompUpdateName(rowUpdateDTO.getUpdatedRows());
                    }
                }

                break;
            case "ROW_MOVE":
                RowMoveDTO rowMoveDTO = gson.fromJson(jsonEntry, RowMoveDTO.class);

                homeBankingId = rowMoveDTO.getHomeBankingId();
                sessionIdToSend = rowMoveDTO.getSessionId();
                botJobIdTask = rowMoveDTO.getBotJobId();

                alreadySentMgsSocket = false;

                if ((sessionIdToSend != null && sessionIdToSend.matches(".*botJobTasks.*"))) {

                    if (performDataBase.updateMoveRowsOrder(rowMoveDTO.getUpdatedRows())
                            && rowMoveDTO.getDeleteBlockId() != null
                            && rowMoveDTO.getDeleteBlockId() > -1) {

                        performDataBase.deleteBlockDirect(rowMoveDTO.getBotJobId(), rowMoveDTO.getDeleteBlockId());

                        performDataBase.updateBlockOrderNumber(
                                performDataBase.selectAllBlocks(rowMoveDTO.getBotJobId()), true);
                    }

                } else if ((sessionIdToSend != null && sessionIdToSend.matches(".*componentTasks.*"))) {

                    if (performDataBase.updateCompMoveRowsOrder(rowMoveDTO.getUpdatedRows())
                            && rowMoveDTO.getDeleteBlockId() != null
                            && rowMoveDTO.getDeleteBlockId() > -1) {

                        performDataBase.deleteCompBlockDirect(rowMoveDTO.getBotJobId(), rowMoveDTO.getDeleteBlockId());

                        performDataBase.updateCompBlockOrderNumber(
                                performDataBase.selectCompAllBlocks(rowMoveDTO.getBotJobId()), true);
                    }
                }

                break;
            case "INSERT_BEFORE":
            case "INSERT_AFTER":
            case "INSERT_NEW":
            case "INSERT_AFTER_ELSEIF":
            case "INSERT_BEFORE_ELSEIF":
            case "EDIT_OPERATION":
                RowMoveDTO insertBeforeDTO = gson.fromJson(jsonEntry, RowMoveDTO.class);

                homeBankingId = insertBeforeDTO.getHomeBankingId();
                botJobIdTask = insertBeforeDTO.getBotJobId();
                sessionIdToSend = insertBeforeDTO.getSessionId();

                injectStepAfterOrBefore(sessionIdToSend, insertBeforeDTO);

                alreadySentMgsSocket = true;

                break;
            case "BLOCK_EXCEL_FILE":
                BlockDetailsDTO blockExcelDTO = gson.fromJson(jsonEntry, BlockDetailsDTO.class);

                homeBankingId = blockExcelDTO.getHomeBankingId();
                sessionIdToSend = blockExcelDTO.getSessionId();
                botJobIdTask = blockExcelDTO.getBotJobId();

                excelFileBlock(sessionIdToSend, blockExcelDTO);

                alreadySentMgsSocket = true;
                break;
            case "BLOCK_ORDER":
                BlockOrderDTO blockReorder = gson.fromJson(jsonEntry, BlockOrderDTO.class);
                if (blockReorder.getUpdatedBlocks().size() > 0) {

                    homeBankingId = blockReorder.getHomeBankingId();
                    sessionIdToSend = blockReorder.getSessionId();
                    botJobIdTask = blockReorder.getBotJobId();

                    alreadySentMgsSocket = false;

                    if ((sessionIdToSend != null && sessionIdToSend.matches(".*botJobTasks.*"))) {
                        performDataBase.updateBlockOrderNumber(
                                performDataBase.selectAllBlocks(
                                        blockReorder.getUpdatedBlocks().get(0).getBotJobId()),
                                true);
                        performDataBase.deleteNullBlocks(
                                blockReorder.getUpdatedBlocks().get(0).getBotJobId());

                    } else if ((sessionIdToSend != null && sessionIdToSend.matches(".*componentTasks.*"))) {
                    }
                }
                break;
            case "INSTRUCTION_STATUS":
                InstructionLoadDTO InstructionLoadDTO = gson.fromJson(jsonEntry, InstructionLoadDTO.class);

                homeBankingId = InstructionLoadDTO.getHomeBankingId();
                sessionIdToSend = InstructionLoadDTO.getSessionId();
                botJobIdTask = InstructionLoadDTO.getBotJobId();

                alreadySentMgsSocket = false;

                if ((sessionIdToSend != null && sessionIdToSend.matches(".*botJobTasks.*"))) {
                    performDataBase.updateInstructionStatus(InstructionLoadDTO);
                } else if ((sessionIdToSend != null && sessionIdToSend.matches(".*componentTasks.*"))) {
                    performDataBase.updateCompInstructionStatus(InstructionLoadDTO);
                }
                break;
            case "BLOCK_STATUS":
                RowMoveDTO blockStateDTO = gson.fromJson(jsonEntry, RowMoveDTO.class);

                homeBankingId = blockStateDTO.getHomeBankingId();
                sessionIdToSend = blockStateDTO.getSessionId();
                botJobIdTask = blockStateDTO.getBotJobId();

                alreadySentMgsSocket = false;

                if ((sessionIdToSend != null && sessionIdToSend.matches(".*botJobTasks.*"))) {
                    performDataBase.updateBlockStatus(
                            blockStateDTO.getBotJobId(),
                            blockStateDTO.getBlockId(),
                            blockStateDTO.getBlockName(),
                            blockStateDTO.getBlockActive(),
                            3); // Block wait time Default 3 seconds per block

                    performDataBase.updateInstructionStatusByBlock(
                            blockStateDTO.getBotJobId(), blockStateDTO.getBlockId(), blockStateDTO.getBlockActive());
                } else if ((sessionIdToSend != null && sessionIdToSend.matches(".*componentTasks.*"))) {
                    performDataBase.updateCompBlockStatus(
                            blockStateDTO.getBotJobId(),
                            blockStateDTO.getBlockId(),
                            blockStateDTO.getBlockName(),
                            blockStateDTO.getBlockActive(),
                            3); // Block wait time Default 3 seconds per block

                    performDataBase.updateCompInstructionStatusByBlock(
                            blockStateDTO.getBotJobId(), blockStateDTO.getBlockId(), blockStateDTO.getBlockActive());
                }

                break;
            case "BLOCK_UPDATE":
                RowMoveDTO blockUpdateDTO = gson.fromJson(jsonEntry, RowMoveDTO.class);

                homeBankingId = blockUpdateDTO.getHomeBankingId();
                sessionIdToSend = blockUpdateDTO.getSessionId();
                botJobIdTask = blockUpdateDTO.getBotJobId();

                alreadySentMgsSocket = false;

                if ((sessionIdToSend != null && sessionIdToSend.matches(".*botJobTasks.*"))) {
                    performDataBase.updateBlockName(
                            blockUpdateDTO.getBotJobId(), blockUpdateDTO.getBlockId(), blockUpdateDTO.getBlockName());
                } else if ((sessionIdToSend != null && sessionIdToSend.matches(".*componentTasks.*"))) {
                    performDataBase.updateCompBlockName(
                            blockUpdateDTO.getBotJobId(), blockUpdateDTO.getBlockId(), blockUpdateDTO.getBlockName());
                }

                break;
            case "DELETE_INSTRUCTION":
                InstructionLoadDTO deleteInstructionLoadDTO = gson.fromJson(jsonEntry, InstructionLoadDTO.class);

                homeBankingId = deleteInstructionLoadDTO.getHomeBankingId();
                sessionIdToSend = deleteInstructionLoadDTO.getSessionId();
                botJobIdTask = deleteInstructionLoadDTO.getBotJobId();

                alreadySentMgsSocket = false;

                if ((sessionIdToSend != null && sessionIdToSend.matches(".*botJobTasks.*"))) {
                    performDataBase.deleteInstruction(deleteInstructionLoadDTO.getBotJobId(), deleteInstructionLoadDTO);
                } else if ((sessionIdToSend != null && sessionIdToSend.matches(".*componentTasks.*"))) {
                    performDataBase.deleteComponent(deleteInstructionLoadDTO);
                }

                break;
            case "DELETE_BLOCK":
                DeleteBlockDTO deleteBlockDTO = gson.fromJson(jsonEntry, DeleteBlockDTO.class);

                homeBankingId = deleteBlockDTO.getHomeBankingId();
                sessionIdToSend = deleteBlockDTO.getSessionId();
                botJobIdTask = deleteBlockDTO.getBotJobId();

                alreadySentMgsSocket = false;

                if ((sessionIdToSend != null && sessionIdToSend.matches(".*botJobTasks.*"))) {
                    performDataBase.deleteBlock(deleteBlockDTO);
                } else if ((sessionIdToSend != null && sessionIdToSend.matches(".*componentTasks.*"))) {
                    performDataBase.deleteCompBlock(deleteBlockDTO);
                }

                break;
            case "BLOCK_ROLLBACK":
                RollBackBlocksDTO rollBackBlocksDTO = gson.fromJson(jsonEntry, RollBackBlocksDTO.class);

                homeBankingId = rollBackBlocksDTO.getHomeBankingId();
                sessionIdToSend = rollBackBlocksDTO.getSessionId();
                botJobIdTask = rollBackBlocksDTO.getBotJobId();

                alreadySentMgsSocket = false;

                if ((sessionIdToSend != null && sessionIdToSend.matches(".*botJobTasks.*"))) {
                    performDataBase.rollBackBlocksRows("instructions", rollBackBlocksDTO);
                    performDataBase.deleteNullBlocks(rollBackBlocksDTO.getBotJobId());
                } else if ((sessionIdToSend != null && sessionIdToSend.matches(".*componentTasks.*"))) {
                    performDataBase.rollBackBlocksRows("component_instructions", rollBackBlocksDTO);
                    performDataBase.deleteCompNullBlocks(
                            rollBackBlocksDTO.getHomeBankingId(), rollBackBlocksDTO.getBotJobId());
                }

                break;

            default:
                sendMessageJson(homeBankingId, session, type, "Action type : \"" + type + "\"", "cannot be processed");
                break;
        }

        if (!alreadySentMgsSocket && (sessionIdToSend != null && sessionIdToSend.matches(".*botJobTasks.*"))) {
            this.botJobLoadList = performDataBase.loadCompleteJobs(botJobIdTask);
            String jsonData = "[]";
            if (botJobLoadList.size() > 0) {
                List<InstructionLoadDTO> blockLoopInstructions = performDataBase.buildJsonViewData(botJobLoadList);
                jsonData = gson.toJson(blockLoopInstructions);
            }
            sendMessageJson(homeBankingId, sessionIdToSend, jsonData, "updateInstructions");

        } else if (!alreadySentMgsSocket
                && (sessionIdToSend != null && sessionIdToSend.matches(".*componentTasks.*"))) {
            this.botJobLoadList = performDataBase.loadComponentsComplete(homeBankingId);
            String jsonData = "[]";
            if (botJobLoadList.size() > 0) {
                List<InstructionLoadDTO> blockLoopInstructions = performDataBase.buildJsonViewData(botJobLoadList);
                jsonData = gson.toJson(blockLoopInstructions);
            }

            broadcastMessageToAll(homeBankingId, "componentTasks", jsonData, "componentsUpdate");
            //            sendMessageJson(sessionIdToSend, jsonData, "componentsUpdate");
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

    public static void broadcastMessageToAll(int homeBankingId, String broadTo, String body, String operationId) {
        for (Map.Entry<String, Session> entry : activeSessions.entrySet()) {
            String sessionKey = entry.getKey();
            Session session = entry.getValue();

            // Ensure session is open before sending
            if (session.isOpen() && sessionKey.contains(broadTo)) {
                try {
                    sendMessageJson(homeBankingId, session, entry.getKey(), body, operationId);
                } catch (Exception e) {
                    System.err.println("Failed to send message to session: " + sessionKey);
                    e.printStackTrace();
                }
            }
        }
    }

    private void broadcastMessageToAll(int homeBankingId, String message) {
        activeSessions = SimpleWebSocketServer.getAllSessions();

        for (Session session : activeSessions.values()) { // Looping correctly
            if (session.isOpen()) {
                sendMessageJson(homeBankingId, session, "Broad-All", message, null);
            }
        }
    }

    private void sendMessage(Session session, String message) {
        try {
            session.getBasicRemote().sendText(message);
        } catch (IOException e) {
            System.err.println("Error sending message to session " + session.getId() + ": " + e.getMessage());
        }
    }

    // Method to send a message to a specific session ID
    public static void sendMessageJson(String sessionId, String message) {
        activeSessions = SimpleWebSocketServer.getAllSessions();
        Session session = activeSessions.get(sessionId);

        if (session != null && session.isOpen()) {
            try {
                session.getBasicRemote().sendText(message);
            } catch (IOException e) {
                System.err.println("Error sending message to session " + sessionId + ": " + e.getMessage());
            }
        } else {
            System.err.println("Session " + sessionId + " not found or closed.");
        }
    }

    // Method to send a message to a specific session ID
    public static void sendMessageJson(int homeBankingId, String sessionId, String body, String operationId) {
        activeSessions = SimpleWebSocketServer.getAllSessions();
        Session session = activeSessions.get(sessionId);

        if (session != null && session.isOpen()) {
            try {
                JsonObject jsonMessage = new JsonObject();
                jsonMessage.addProperty("body", body);
                jsonMessage.addProperty("sessionId", sessionId);
                jsonMessage.addProperty("homeBankingId", homeBankingId);
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
                    newCommandScene.showModal();
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

    private void excelFileBlock(String sessionId, BlockDetailsDTO blockExcelDTO) {
        // Ensure JavaFX UI updates are done on the JavaFX Application Thread
        Platform.runLater(() -> {
            ARExcelFileScene excelFileScene = new ARExcelFileScene(sessionId, blockExcelDTO);
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

    private void injectBlockComponent(BlockSplitDTO blockSplitDTO) {
        // Ensure JavaFX UI updates are done on the JavaFX Application Thread

        BlockDetailsDTO blockDetailsDTO = blockSplitDTO.getDetails().getNewBlock();
        blockDetailsDTO.setHomeBankingId(blockSplitDTO.getHomeBankingId());
        blockDetailsDTO.setBotJobId(blockSplitDTO.getBotJobId());
        blockDetailsDTO.setSessionId(blockSplitDTO.getSessionId());

        ErrorMessage errorMessage = performDataBase.injectNewComponent(blockDetailsDTO);
        if (errorMessage == null) {
            List<BotJobLoadDTO> botJobLoadList = performDataBase.loadCompleteJobs(blockDetailsDTO.getBotJobId());

            String jsonData = "[]";
            if (botJobLoadList.size() > 0) {
                List<InstructionLoadDTO> blockLoopInstructions = performDataBase.buildJsonViewData(botJobLoadList);
                jsonData = gson.toJson(blockLoopInstructions);
            }
            sendMessageJson(
                    blockDetailsDTO.getHomeBankingId(), blockDetailsDTO.getSessionId(), jsonData, "updateInstructions");

        } else {
            performMessage.errorMessage(
                    "Access Database error",
                    errorMessage.getErrorTitle(),
                    errorMessage.getErrorHeader(),
                    "Verify  [INSERT] or [UPDATE] or [SELECT]",
                    null,
                    0);
        }
    }
}

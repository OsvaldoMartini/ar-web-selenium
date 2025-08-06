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
import com.allinweb.ch.component.model.ParentOperations;
import com.allinweb.ch.component.model.RollBackBlocksDTO;
import com.allinweb.ch.component.model.RowMoveDTO;
import com.allinweb.ch.component.scene.ARExcelFileScene;
import com.allinweb.ch.component.scene.ARSaveComponentScene;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.util.ARConstants;
import com.allinweb.ch.util.ARLogger;
import com.allinweb.ch.util.ErrorMessage;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javafx.application.Platform;
import javax.websocket.CloseReason;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

// Simple WebSocket server endpoint (for demonstration)
@ServerEndpoint("/websocket")
public class SimpleWebSocketServer {

    protected static volatile SimpleWebSocketServer instance;

    // Private constructor to prevent instantiation
    public SimpleWebSocketServer() {
        // Initialize if necessary
    }

    public static SimpleWebSocketServer getInstance() {
        if (instance == null) {
            synchronized (SimpleWebSocketServer.class) {
                if (instance == null) {
                    instance = new SimpleWebSocketServer();
                }
            }
        }
        return instance;
    }

    private static WebSocketSessionManager webSocketSessionManager;
    private static final PerformDataBase performDataBase;
    private static final PerformMessage performMessage;
    private static ARExcelFileScene arExcelFileScene;

    // Static block to initialize
    static {
        webSocketSessionManager = WebSocketSessionManager.getInstance();
        performDataBase = PerformDataBase.getInstance();
        performMessage = PerformMessage.getInstance();
        arExcelFileScene = ARExcelFileScene.getInstance();
    }

    private final Gson gson = new Gson();
    private List<BotJobLoadDTO> botJobLoadList = new ArrayList<>();

    @OnOpen
    public void onOpen(Session session) {
        // Get the sessionId from the query parameter passed by the frontend
        String sessionId = null;
        try {
            sessionId = session.getRequestParameterMap().get("sessionId").get(0);

            if (!Strings.isNullOrEmpty(sessionId)) {
                webSocketSessionManager.addSession(sessionId, session);
            } else {
                //                addSession(generateCustomSessionId(session), session);
            }
        } catch (Exception noSessionId) {
            //            addSession(generateCustomSessionId(session), session);
        }

        if (sessionId != null) {
            webSocketSessionManager.addSession(sessionId, session); // Store the session with the custom ID
            System.out.println("New connection: Session ID = " + sessionId);
        } else {
            System.out.println("No session ID provided by client");
        }
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        if (message == null || message.contains("CONNECT") || message.contains("ping")) {
            // Ignore null or empty messages
            message = message.replaceAll("ping-", "");
            System.out.println("Active : " + message);
            return;
        }

        try {
            // Decode from Base64
            byte[] decodedBytes = Base64.getDecoder().decode(message);
            message = new String(decodedBytes, "UTF-8");

            //            System.out.println("Decoded Received Data: " + message);

            // Process the message as needed...
        } catch (IllegalArgumentException e) {
            //            System.err.println("Invalid Base64 message received: " + message);
        } catch (Exception e) {
            //            e.printStackTrace();
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

            // if Not have Session and does not Exist into the activeSessions
            // Is Going to Handle the Control
            if (Strings.isNullOrEmpty(sessionId)) {
                sessionId = null;
                try {
                    sessionId =
                            session.getRequestParameterMap().get("sessionId").get(0);
                    if (!Strings.isNullOrEmpty(sessionId)) {
                        if (!webSocketSessionManager.containsSession(sessionId)) {
                            if (!webSocketSessionManager.isSessionOpen(sessionId)) {
                                webSocketSessionManager.addSession(sessionId, session);
                            }
                        }
                    } else {
                        //                        addSession(generateCustomSessionId(session), session);
                    }

                } catch (Exception noSessionId) {
                    //                    addSession(generateCustomSessionId(session), session);
                }
            }

            // Process the message based on its type
            switch (type) {
                case "broadcast":
                    String broadcastMessage = jsonObjMSG.get("body").getAsString();
                    webSocketSessionManager.broadcastMessageToAll(homeBankingId, broadcastMessage);
                    break;
                case "echo":
                    webSocketSessionManager.sendMessageJson(
                            homeBankingId,
                            sessionId,
                            "echo: " + jsonObjMSG.get("body").getAsString(),
                            "sessionId: " + sessionId);
                    break;
                default:
                    handleMessageByType(type, jsonObjMSG, session, sessionId);
                    break;
            }
        } catch (Exception error) {
            System.err.println("Closed processing message: " + error.getMessage());
            if (type != null) {
                webSocketSessionManager.sendMessageJson(
                        homeBankingId, session, type, "Action type : \"" + type + "\"", "cannot be processed");
            } else {
                webSocketSessionManager.sendMessageJson(
                        homeBankingId, session, type, "Closed processing message", "No \"type\" definition");
            }
        }
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

    private void handleMessageByType(String type, JsonObject jsonEntry, Session session, String sessionId) {
        // Dispatch to the correct method based on the message type

        int botJobIdTask = -1;
        String botJobNameTask = "No Name Defined";
        int homeBankingId = -1;
        String sessionIdToSend = null;
        boolean alreadySentMgsSocket = false;

        switch (type) {
            case "CLOSE_BROWSER":
                // Extract the "body" field from the JsonObject
                ElementSplitDTO elementSplitDTO = gson.fromJson(jsonEntry, ElementSplitDTO.class);

                homeBankingId = elementSplitDTO.getHomeBankingId() != null ? elementSplitDTO.getHomeBankingId() : -1;
                sessionIdToSend = elementSplitDTO.getSessionId();

                if (sessionIdToSend.equals("scannerReceiver")) {
                    elementSplitDTO.setOperationId("closeBrowser");
                    String jsonData = gson.toJson(elementSplitDTO);
                    webSocketSessionManager.sendMessageJson(homeBankingId, sessionIdToSend, jsonData, "closeBrowser");
                }

                alreadySentMgsSocket = true;

                break;
            case "HOVERED_ROW":
                // Extract the "body" field from the JsonObject
                elementSplitDTO = gson.fromJson(jsonEntry, ElementSplitDTO.class);

                homeBankingId = elementSplitDTO.getHomeBankingId() != null ? elementSplitDTO.getHomeBankingId() : -1;
                sessionIdToSend = elementSplitDTO.getSessionId();

                if (sessionIdToSend.equals("scannerTool")) {
                    elementSplitDTO.setOperationId("highlight");
                    String jsonData = gson.toJson(elementSplitDTO);
                    webSocketSessionManager.sendMessageJson(homeBankingId, sessionIdToSend, jsonData, null);
                }

                alreadySentMgsSocket = true;

                break;
            case "SEARCH_TOOL":
                // Extract the "body" field from the JsonObject
                elementSplitDTO = gson.fromJson(jsonEntry, ElementSplitDTO.class);
                //                elementSplitDTO.setType("RETURN FROM MARTINI Total Rows: " +
                // elementSplitDTO.getDetails().length);

                homeBankingId = elementSplitDTO.getHomeBankingId() != null ? elementSplitDTO.getHomeBankingId() : -1;
                sessionIdToSend = elementSplitDTO.getSessionId();

                if (sessionIdToSend.equals("scannerGrid")) {
                    String jsonData = gson.toJson(elementSplitDTO);
                    webSocketSessionManager.sendMessageJson(homeBankingId, sessionIdToSend, jsonData, "addPickOne");
                    //                    broadcastMessageToAll(jsonData);
                    List<String> excludeList = List.of("optional", "blockMarked", "editMode");
                    performMessage.outputJsonElementDTO(elementSplitDTO.getDetails(), excludeList, "elementDTO");
                    excludeList = List.of(
                            "optional",
                            "blockMarked",
                            "editMode",
                            "id",
                            "attributeData",
                            "typeElement",
                            "customXPath",
                            "shadowRoot",
                            "nestedShadow",
                            "searchAttributeValue",
                            "attributeType",
                            "attributeValue");
                    performMessage.outputJsonElementDTO(elementSplitDTO.getDetails(), excludeList, "AI-ElementDTO");
                }

                alreadySentMgsSocket = true;

                break;
            case "NEW_ELEMENT_DTO":
            case "SEND_ALL_ELEMENTS_DTO":
            case "DEL_ELEMENT_DTO":
            case "DETAILS_ELEMENT_DTO":
            case "TEST_CLICK_DTO":
            case "TEST_INPUT_DTO":
                // Extract the "body" field from the JsonObject
                ElementSplitDTO processDTO = gson.fromJson(jsonEntry, ElementSplitDTO.class);

                homeBankingId = processDTO.getHomeBankingId();
                sessionIdToSend = processDTO.getSessionId();
                //                botJobIdTask = processDTO.getBotJobId();

                if (processDTO.getDetails() != null && processDTO.getDetails().length > 0) {
                    webSocketSessionManager.sendMessageJson(
                            "scannerReceiver", gson.toJson(processDTO)); // Sending as details
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

                webSocketSessionManager.sendMessageJson(homeBankingId, session, "Martini", jsonData, null);

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
                        if (performDataBase.rowsUpdateName(rowUpdateDTO.getUpdatedRows())) {
                            List<ParentOperations> listParents = performDataBase.loadAllParents(
                                    rowUpdateDTO.getBotJobId(),
                                    rowUpdateDTO.getUpdatedRows().get(0).getInstructionId());
                            if (!listParents.isEmpty()) {
                                for (ParentOperations parent : listParents) {
                                    if ("GET".equals(parent.getActions())) {
                                        List<String> operationsList = new ArrayList<>();
                                        if (parent.getParentName() != null) {
                                            String[] parts =
                                                    parent.getOperations().split(":");
                                            parent.setOperations(parent.getParentName() + ":" + parts[1]);
                                        }
                                    }
                                }

                                performDataBase.rowsGetUpdateName(listParents);
                            }
                        }
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
                                performDataBase.selectCompAllBlocks(
                                        rowMoveDTO.getHomeBankingId(), rowMoveDTO.getBotJobId()),
                                true);
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

                //                homeBankingId = insertBeforeDTO.getHomeBankingId();
                //                botJobIdTask = insertBeforeDTO.getBotJobId();
                //                sessionIdToSend = insertBeforeDTO.getSessionId();

                injectStepAfterOrBefore(sessionIdToSend, insertBeforeDTO);

                if (type.equals("INSERT_AFTER_ELSEIF") || type.equals("INSERT_BEFORE_ELSEIF")) {
                    alreadySentMgsSocket = false;
                } else {
                    alreadySentMgsSocket = true;
                }

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
                botJobIdTask = deleteInstructionLoadDTO.getBotJobId();
                sessionIdToSend = deleteInstructionLoadDTO.getSessionId();

                alreadySentMgsSocket = false;

                if ((sessionIdToSend != null && sessionIdToSend.matches(".*botJobTasks.*"))) {
                    performDataBase.deleteInstruction(
                            deleteInstructionLoadDTO.getBotJobId(), deleteInstructionLoadDTO, false);
                } else if ((sessionIdToSend != null && sessionIdToSend.matches(".*componentTasks.*"))) {
                    performDataBase.deleteComponent(
                            homeBankingId, deleteInstructionLoadDTO.getBlockId(), deleteInstructionLoadDTO, false);
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
                    performDataBase.rollBackBlocksRows("instruction", rollBackBlocksDTO);
                    performDataBase.deleteNullBlocks(rollBackBlocksDTO.getBotJobId());
                } else if ((sessionIdToSend != null && sessionIdToSend.matches(".*componentTasks.*"))) {
                    performDataBase.rollBackBlocksRows("component_instruction", rollBackBlocksDTO);
                    performDataBase.deleteCompNullBlocks(rollBackBlocksDTO.getHomeBankingId());
                }

                break;

            default:
                webSocketSessionManager.sendMessageJson(
                        homeBankingId, session, type, "Action type : \"" + type + "\"", "cannot be processed");
                break;
        }

        if (!alreadySentMgsSocket && (sessionIdToSend != null && sessionIdToSend.matches(".*botJobTasks.*"))) {
            this.botJobLoadList = performDataBase.loadCompleteJobs(botJobIdTask);
            String jsonData = "[]";
            if (!botJobLoadList.isEmpty()) {
                List<InstructionLoadDTO> blockLoopInstructions =
                        performDataBase.buildJsonViewData(botJobLoadList, "instruction");
                jsonData = gson.toJson(blockLoopInstructions);
            }
            webSocketSessionManager.sendMessageJson(homeBankingId, sessionIdToSend, jsonData, "updateInstructions");

        } else if (!alreadySentMgsSocket
                && (sessionIdToSend != null && sessionIdToSend.matches(".*componentTasks.*"))) {
            this.botJobLoadList = performDataBase.loadComponentsComplete(homeBankingId, botJobIdTask, botJobNameTask);
            String jsonData = "[]";
            if (!botJobLoadList.isEmpty()) {
                List<InstructionLoadDTO> blockLoopInstructions =
                        performDataBase.buildJsonViewData(botJobLoadList, "component_instruction");
                jsonData = gson.toJson(blockLoopInstructions);
            }

            webSocketSessionManager.sendMessageJson(homeBankingId, "componentTasks", jsonData, "componentsUpdate");

            //            broadcastMessageToAll(homeBankingId, "componentTasks", jsonData, "componentsUpdate");
            //            sendMessageJson(sessionIdToSend, jsonData, "componentsUpdate");
        }
    }

    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        // Clean up session when it closes
        String sessionId = webSocketSessionManager.getSessionIdBySession(session);
        if (sessionId != null) {
            System.out.println("Connection closed: Session ID = " + sessionId + ", Reason: "
                    + closeReason.getReasonPhrase() + " (Code: "
                    + closeReason.getCloseCode() + ")");
            webSocketSessionManager.removeSession(sessionId);
        } else {
            System.out.println("Connection closed for unknown session, Reason: " + closeReason.getReasonPhrase()
                    + " (Code: " + closeReason.getCloseCode() + ")");
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

        ErrorMessage errorMessage = performDataBase.initiateNewBlock(newBlock, blockSplitDTO.getBotJobId());

        if (errorMessage == null) {
            int newBlockId = -9999;
            if (!performDataBase.getIdsBlockAfter().isEmpty()
                    && performDataBase.getIdsBlockAfter().get(0) > 0) {
                newBlockId = performDataBase.getIdsBlockAfter().get(0);
            }
            if (performDataBase.updateInstructionsSplitter(
                    newBlock.getInstructions(), (int) originalBlock.getBlockId(), newBlockId)) {
                if (updatedBlock.size() > 0) {
                    performDataBase.updateBlockOrderNumber(updatedBlock, false);
                    //                performDataBase.updateBlockOrderNumber(
                    //                        performDataBase.selectAllBlocks(updatedBlock.get(0).getBotJobId()), true);
                }
            }
        } else {
            performMessage.errorMessage(
                    errorMessage.getErrorTitle(),
                    "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Operation Failed!</span> ❌",
                    "<span style='color: #E65100; font-weight: bold;'>Error Type:</span> "
                            + errorMessage.getErrorTitle(),
                    "<span style='font-style: italic;'>Detail:</span> " + errorMessage.getErrorMessage(),
                    null,
                    0);
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
            //            BotJobLoadDTO botJobLoad = performDataBase.loadBotJobById(rowMoveDTO.getBotJobId());

            if (!rowMoveDTO.getType().equals("INSERT_BEFORE_ELSEIF")
                    && !rowMoveDTO.getType().equals("INSERT_AFTER_ELSEIF")) {

                webSocketSessionManager.sendMessageJson(
                        "new-command-scene", gson.toJson(rowMoveDTO)); // Sending as details

                //                this.webPageItems = performDataBase.loadWebPageFields(rowMoveDTO.getBotJobId());

                // Ensure JavaFX UI updates are done on the JavaFX Application Thread
                //                Platform.runLater(() -> {
                //                    arNewCommandScene.initialize(rowMoveDTO, botJobLoad, this.webPageItems,
                // sessionId);
                //                    arNewCommandScene.showModal();
                //                });
            } else {

                if (!rowMoveDTO.getUpdatedRows().isEmpty()) {

                    int parentId = rowMoveDTO.getUpdatedRows().get(0).getParentId();
                    try {
                        // Run the instruction add in a separate Task

                        ErrorMessage message = performDataBase.preFillNewInstruction(
                                "ELSEIF", "ELSEIF", ARConstants.ELSEIF, ARConstants.ELSEIF, 1, null, rowMoveDTO, false);

                    } catch (Exception e) {

                        ARLogger.getInstance(SimpleWebSocketServer.class)
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
            arExcelFileScene.initialize(sessionId, blockExcelDTO);
            arExcelFileScene.showModal();
        });
    }

    private void createBlockComponent(BlockSplitDTO blockSplitDTO) {
        // Ensure JavaFX UI updates are done on the JavaFX Application Thread

        BlockDetailsDTO blockDetailsDTO = blockSplitDTO.getDetails().getNewBlock();
        blockDetailsDTO.setHomeBankingId(blockSplitDTO.getHomeBankingId());
        blockDetailsDTO.setBotJobId(blockSplitDTO.getBotJobId());
        blockDetailsDTO.setBotJobName(blockSplitDTO.getBotJobName());
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

        performDataBase.deleteCompNullBlocks(blockDetailsDTO.getHomeBankingId());

        ErrorMessage errorMessage = performDataBase.createInjectBlock(blockDetailsDTO);
        if (errorMessage == null) {
            errorMessage = performDataBase.createInjectInstructions(blockDetailsDTO);
        }
        if (errorMessage == null) {
            errorMessage = performDataBase.createInjectVariables(blockDetailsDTO);
        }
        if (errorMessage == null) {
            errorMessage = performDataBase.createUpdateInjectInstruction(blockDetailsDTO);
        }
        if (errorMessage == null) {
            errorMessage = performDataBase.createInjectReferences(blockDetailsDTO);
        }

        if (errorMessage == null) {
            List<BotJobLoadDTO> botJobLoadList = performDataBase.loadCompleteJobs(blockDetailsDTO.getBotJobId());

            String jsonData = "[]";
            if (!botJobLoadList.isEmpty()) {
                List<InstructionLoadDTO> blockLoopInstructions =
                        performDataBase.buildJsonViewData(botJobLoadList, "instruction");
                jsonData = gson.toJson(blockLoopInstructions);
            }
            webSocketSessionManager.sendMessageJson(
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

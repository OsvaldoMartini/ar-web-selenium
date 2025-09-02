package com.allinweb.ch.socket;

import com.allinweb.ch.component.model.*;
import com.allinweb.ch.component.scene.ARExcelFileScene;
import com.allinweb.ch.component.scene.ARSaveComponentScene;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.util.ARConstants;
import com.allinweb.ch.util.ARLogger;
import com.allinweb.ch.util.ErrorMessage;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
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
    public SimpleWebSocketServer() {}

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

    private static WebSocketSessionManager webSocketSessionManager = WebSocketSessionManager.getInstance();
    private static final PerformLists performLists = PerformLists.getInstance();
    private static final PerformDataBase performDataBase = PerformDataBase.getInstance();
    private static final PerformMessage performMessage = PerformMessage.getInstance();
    private static ARExcelFileScene arExcelFileScene = ARExcelFileScene.getInstance();
    private static ARSaveComponentScene arSaveComponentScene = ARSaveComponentScene.getInstance();

    private final Gson gson = new Gson();
    private PayloadJson payloadEmpty;

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
            // System.out.println("Active : " + message);
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
                // System.out.println("Active : " + type);
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
        String botJobNameTask = "1# Default Block";
        int homeBankingId = -1;
        String sessionIdToSend = null;
        boolean alreadySentMgsSocket = false;

        switch (type) {
            case "CLOSE_BROWSER":
                // Extract the "body" field from the JsonObject
                ElementSplitDTO elementSplitDTO = gson.fromJson(jsonEntry, ElementSplitDTO.class);

                homeBankingId = elementSplitDTO.getHomeBankingId() != null ? elementSplitDTO.getHomeBankingId() : -1;
                sessionIdToSend = elementSplitDTO.getSessionId();

                if (sessionIdToSend.equals("scanner-element-pane")) {
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
                            "scanner-element-pane", gson.toJson(processDTO)); // Sending as details
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
                BlockSplitDTO creteComp = gson.fromJson(jsonEntry, BlockSplitDTO.class);

                homeBankingId = creteComp.getHomeBankingId();
                sessionIdToSend = creteComp.getSessionId();
                botJobIdTask = creteComp.getBotJobId();

                createBlockComponent(creteComp);

                alreadySentMgsSocket = true;

                // calls perform list block update
                creteComp.setType("UPDATE_BLOCKS_COMP");
                jsonData = gson.toJson(creteComp);
                webSocketSessionManager.sendMessageJson(
                        homeBankingId, "perform-list-data", jsonData, "UPDATE_BLOCKS_COMP");

                break;
            case "COMPONENT_INJECT":
                BlockSplitDTO injectComp = gson.fromJson(jsonEntry, BlockSplitDTO.class);

                homeBankingId = injectComp.getHomeBankingId();
                sessionIdToSend = injectComp.getSessionId();
                botJobIdTask = injectComp.getBotJobId();

                injectBlockComponent(injectComp);

                alreadySentMgsSocket = true;

                // calls perform list block update
                injectComp.setType("UPDATE_BLOCKS");
                jsonData = gson.toJson(injectComp);
                webSocketSessionManager.sendMessageJson(homeBankingId, "perform-list-data", jsonData, "UPDATE_BLOCKS");

                break;
            case "BLOCKS_SPLITTER":
                BlockSplitDTO blockSplitDTO = gson.fromJson(jsonEntry, BlockSplitDTO.class);

                homeBankingId = blockSplitDTO.getHomeBankingId();
                sessionIdToSend = blockSplitDTO.getSessionId();
                botJobIdTask = blockSplitDTO.getBotJobId();

                alreadySentMgsSocket = false;

                ErrorMessage errorMessage = null;
                String blockTable = null;
                int whereId = -1;
                String updteBlocks = "";

                if (sessionIdToSend != null) {
                    if (sessionIdToSend.matches(".*botJobTasks.*")) {
                        blockTable = "block";
                        whereId = blockSplitDTO.getBotJobId();
                        updteBlocks = "UPDATE_BLOCKS";
                    } else if (sessionIdToSend.matches(".*componentTasks.*")) {
                        blockTable = "component_block";
                        whereId = blockSplitDTO.getHomeBankingId();
                        updteBlocks = "UPDATE_BLOCKS_COMP";
                    }
                }

                errorMessage = splitBlocks(blockSplitDTO);

                if (blockTable != null && errorMessage == null) {
                    //                    errorMessage = performDataBase.loadBlocks(whereId, "", blockTable);

                    // updateBlockOrderNumber  ALREADY UPDATE MEMORY LIST
                    if (errorMessage == null) {
                        errorMessage = performDataBase.updateBlockOrderNumber(blockTable, whereId, true);
                    }
                }

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

                // calls perform list block update
                blockSplitDTO.setType(updteBlocks);
                jsonData = gson.toJson(blockSplitDTO);
                webSocketSessionManager.sendMessageJson(homeBankingId, "perform-list-data", jsonData, updteBlocks);

                break;
            case "BLOCK_MOVE":
                BlockMoveDTO blockMoveDTO = gson.fromJson(jsonEntry, BlockMoveDTO.class);

                homeBankingId = blockMoveDTO.getHomeBankingId();
                sessionIdToSend = blockMoveDTO.getSessionId();
                botJobIdTask = blockMoveDTO.getBotJobId();

                alreadySentMgsSocket = false;

                errorMessage = null;
                blockTable = null;
                whereId = -1;
                updteBlocks = "";

                if (sessionIdToSend != null) {
                    if (sessionIdToSend.matches(".*botJobTasks.*")) {
                        blockTable = "block";
                        whereId = blockMoveDTO.getBotJobId();
                        updteBlocks = "UPDATE_BLOCKS";
                    } else if (sessionIdToSend.matches(".*componentTasks.*")) {
                        blockTable = "component_block";
                        whereId = blockMoveDTO.getHomeBankingId();
                        updteBlocks = "UPDATE_BLOCKS_COMP";
                    }
                }

                try {

                    if (blockTable != null) {

                        List<BlockLoadDTO> mappedBlocks =
                                mapToBlockLoad(homeBankingId, blockMoveDTO.getUpdatedBlocks());
                        errorMessage = performDataBase.updateSwiftBlockOrderNumber(blockTable, whereId, mappedBlocks);

                        // UPDATE BLOCK ORDER MEMORY LIST
                        if (errorMessage == null) {
                            performLists.updateMemorySwiftBlockOrder(blockTable, whereId, mappedBlocks);
                        }

                        // calls perform list block update
                        blockMoveDTO.setType(updteBlocks);
                        jsonData = gson.toJson(blockMoveDTO);
                        webSocketSessionManager.sendMessageJson(
                                homeBankingId, "perform-list-data", jsonData, updteBlocks);
                    }

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
                } catch (Exception error) {
                    ARLogger.getInstance(SimpleWebSocketServer.class).severe("Error: " + error.getMessage());
                }

                break;
            case "ROW_UPDATE":
                RowMoveDTO rowUpdateDTO = gson.fromJson(jsonEntry, RowMoveDTO.class);

                homeBankingId = rowUpdateDTO.getHomeBankingId();
                sessionIdToSend = rowUpdateDTO.getSessionId();
                botJobIdTask = rowUpdateDTO.getBotJobId();

                alreadySentMgsSocket = false;

                whereId = -1;
                updteBlocks = null;

                String instTable = "instruction";
                if (sessionIdToSend != null) {
                    if (sessionIdToSend.matches(".*botJobTasks.*")) {
                        instTable = "instruction";
                        whereId = rowUpdateDTO.getBotJobId();
                    } else if (sessionIdToSend.matches(".*componentTasks.*")) {
                        instTable = "component_instruction";
                        whereId = rowUpdateDTO.getHomeBankingId();
                    }
                }

                if (rowUpdateDTO.getUpdatedRows().size() > 0) {

                    errorMessage = performDataBase.rowsUpdateName(instTable, whereId, rowUpdateDTO.getUpdatedRows());

                    if (errorMessage == null) {
                        errorMessage = performDataBase.loadAllParents(
                                instTable,
                                whereId,
                                rowUpdateDTO.getUpdatedRows().get(0).getId());

                        if (errorMessage == null) {
                            if (!performLists.getListParentOperations().isEmpty()) {
                                for (ParentOperations parent : performLists.getListParentOperations()) {
                                    if ("GET".equals(parent.getActions()) || "SET".equals(parent.getActions())) {
                                        if (parent.getParentName() != null) {
                                            String[] parts =
                                                    parent.getOperations().split(":");
                                            parent.setOperations(parent.getParentName() + ":" + parts[1]);
                                        }
                                    }
                                }

                                errorMessage = performDataBase.rowsGetUpdateName(
                                        instTable, whereId, performLists.getListParentOperations());

                                // UPDATE MEMORY LIST FOR PARENTS OPERATION NAMES
                                //                            performLists.updateMemoryParentOpenName(instTable,
                                // whereId,
                                // listParents);
                            }
                        }
                    }

                    // UPDATE MEMORY LIST FOR PARENTS INSTRUCION NAME
                    if (errorMessage == null) {
                        performLists.updateMemoryInstructionName(instTable, whereId, rowUpdateDTO.getUpdatedRows());
                    }

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
                }
                break;
            case "ROW_MOVE":
                RowMoveDTO rowMoveDTO = gson.fromJson(jsonEntry, RowMoveDTO.class);

                homeBankingId = rowMoveDTO.getHomeBankingId();
                sessionIdToSend = rowMoveDTO.getSessionId();
                botJobIdTask = rowMoveDTO.getBotJobId();

                alreadySentMgsSocket = false;

                blockTable = null;
                whereId = -1;
                updteBlocks = null;

                instTable = "instruction";
                if (sessionIdToSend != null) {
                    if (sessionIdToSend.matches(".*botJobTasks.*")) {
                        instTable = "instruction";
                        blockTable = "block";
                        whereId = rowMoveDTO.getBotJobId();
                        updteBlocks = "UPDATE_BLOCKS";
                    } else if (sessionIdToSend.matches(".*componentTasks.*")) {
                        instTable = "component_instruction";
                        blockTable = "component_block";
                        whereId = rowMoveDTO.getHomeBankingId();
                        updteBlocks = "UPDATE_BLOCKS_COMP";
                    }
                }

                errorMessage = null;
                if (blockTable != null) {

                    if (blockTable.equals("block")
                                    && performLists.getListBlock().isEmpty()
                            || blockTable.equals("component_block")
                                    && performLists.getListBlock().isEmpty()) {
                        performDataBase.loadBlocks(whereId, "", blockTable);
                    }

                    // Snapshot of previous IDs
                    List<Integer> previousIds = (blockTable.equals("block")
                                    ? performLists.getListBlock()
                                    : performLists.getListBlockComp())
                            .stream()
                                    .map(BlockLoadDTO::getId)
                                    .filter(Objects::nonNull)
                                    .toList();

                    errorMessage =
                            performDataBase.updateMoveRowsOrder(blockTable, whereId, rowMoveDTO.getUpdatedRows());

                    performDataBase.loadInstructions(whereId, -1, -1, instTable);

                    InstructionLoad hasExcelGotoOneBlock = hasOnlyExcelGoto(instTable);

                    if (hasExcelGotoOneBlock != null) {
                        errorMessage =
                                performDataBase.deleteInstruction(instTable, whereId, hasExcelGotoOneBlock, false);
                    }

                    // Snapshot of previous IDs without repetitions
                    List<Integer> currentIds = (instTable.equals("instruction")
                                    ? performLists.getListInstruction()
                                    : performLists.getListInstructionComp())
                            .stream()
                                    .map(InstructionLoad::getBlockId)
                                    .filter(Objects::nonNull)
                                    .distinct() // removes duplicates
                                    .toList();

                    List<Integer> restToDeleteIds = previousIds.stream()
                            .filter(id -> !currentIds.contains(id))
                            .collect(Collectors.toList());

                    List<BlockLoadDTO> listBlocks =
                            blockTable.equals("block") ? performLists.getListBlock() : performLists.getListBlockComp();
                    // Keep at least One for BLOCK TABLE
                    if (errorMessage == null
                            && !restToDeleteIds.isEmpty()
                            && (blockTable.equals("block") && listBlocks.size() > 1)) {
                        errorMessage = performDataBase.deleteNullBlocks(blockTable, whereId, restToDeleteIds);

                        // UPDATE REMOVAL MEMORY LIST
                        if (errorMessage == null) {
                            performLists.updateMemoryRemoveBlockIds(blockTable, whereId, restToDeleteIds);
                        }
                    } else if (errorMessage == null
                            && !restToDeleteIds.isEmpty()
                            && (blockTable.equals("component_block"))) {
                        errorMessage = performDataBase.deleteNullBlocks(blockTable, whereId, restToDeleteIds);

                        // UPDATE REMOVAL MEMORY LIST
                        if (errorMessage == null) {
                            performLists.updateMemoryRemoveBlockIds(blockTable, whereId, restToDeleteIds);
                        }
                    }

                    // updateMemoryRowMove  ALREADY UPDATE MEMORY LIST
                    performLists.updateMemoryRowMove(blockTable, whereId, rowMoveDTO.getUpdatedRows());

                    // updateBlockOrderNumber  ALREADY UPDATE MEMORY LIST
                    if (errorMessage == null) {
                        errorMessage = performDataBase.updateBlockOrderNumber(blockTable, whereId, true);
                    }

                    // calls perform list block update
                    rowMoveDTO.setType(updteBlocks);
                    jsonData = gson.toJson(rowMoveDTO);
                    webSocketSessionManager.sendMessageJson(homeBankingId, "perform-list-data", jsonData, updteBlocks);
                }

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

                    blockTable = null;
                    updteBlocks = null;
                    whereId = -1;

                    if (sessionIdToSend != null) {
                        if (sessionIdToSend.matches(".*botJobTasks.*")) {
                            blockTable = "block";
                            whereId = blockReorder.getBotJobId();
                            updteBlocks = "UPDATE_BLOCKS";
                        } else if (sessionIdToSend.matches(".*componentTasks.*")) {
                            blockTable = "component_block";
                            whereId = blockReorder.getHomeBankingId();
                            updteBlocks = "UPDATE_BLOCKS_COMP";
                        }
                    }

                    errorMessage = null;
                    if (blockTable != null) {
                        errorMessage = performDataBase.loadBlocks(whereId, "", blockTable);

                        // updateBlockOrderNumber  ALREADY UPDATE MEMORY LIST
                        if (errorMessage == null) {
                            errorMessage = performDataBase.updateBlockOrderNumber(blockTable, whereId, true);
                        }

                        // calls perform list block update
                        blockReorder.setType(updteBlocks);
                        jsonData = gson.toJson(blockReorder);
                        webSocketSessionManager.sendMessageJson(
                                homeBankingId, "perform-list-data", jsonData, updteBlocks);
                    }

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
                }

                break;
            case "INSTRUCTION_STATUS":
                InstructionLoad instructions = gson.fromJson(jsonEntry, InstructionLoad.class);

                homeBankingId = instructions.getHomeBankingId();
                sessionIdToSend = instructions.getSessionId();
                botJobIdTask = instructions.getBotJobId();

                alreadySentMgsSocket = false;

                if ((sessionIdToSend != null && sessionIdToSend.matches(".*botJobTasks.*"))) {
                    performDataBase.updateInstructionStatus(instructions);
                } else if ((sessionIdToSend != null && sessionIdToSend.matches(".*componentTasks.*"))) {
                    performDataBase.updateCompInstructionStatus(instructions);
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

                blockTable = null;
                updteBlocks = null;
                whereId = -1;
                errorMessage = null;

                if (sessionIdToSend != null) {
                    if (sessionIdToSend.matches(".*botJobTasks.*")) {
                        blockTable = "block";
                        whereId = blockUpdateDTO.getBotJobId();
                        updteBlocks = "UPDATE_BLOCKS";
                    } else if (sessionIdToSend.matches(".*componentTasks.*")) {
                        blockTable = "component_block";
                        whereId = blockUpdateDTO.getHomeBankingId();
                        updteBlocks = "UPDATE_BLOCKS_COMP";
                    }
                }

                if (blockTable != null) {
                    errorMessage = performDataBase.updateBlockName(
                            whereId, blockTable, blockUpdateDTO.getBlockId(), blockUpdateDTO.getBlockName());
                    blockUpdateDTO.setType(updteBlocks);
                    blockUpdateDTO.setBlockName(blockUpdateDTO.getBlockName());

                    performLists.updateMemoryBlockName(
                            blockTable, whereId, blockUpdateDTO.getBlockId(), blockUpdateDTO.getBlockName());

                    jsonData = gson.toJson(blockUpdateDTO);
                    webSocketSessionManager.sendMessageJson(homeBankingId, "perform-list-data", jsonData, updteBlocks);
                }

                if (errorMessage != null) {
                    performMessage.errorMessage(
                            errorMessage.getErrorTitle(),
                            "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Operation Failed!</span> ❌",
                            "<span style='color: #E65100; font-weight: bold;'>Error Type:</span> "
                                    + errorMessage.getErrorHeader(),
                            "<span style='font-style: italic;'>Detail:</span> " + errorMessage.getErrorMessage(),
                            null,
                            0);
                } // calls perform list block update

                break;
            case "DELETE_INSTRUCTION":
                InstructionLoad toDelete = gson.fromJson(jsonEntry, InstructionLoad.class);

                homeBankingId = toDelete.getHomeBankingId();
                botJobIdTask = toDelete.getBotJobId();
                sessionIdToSend = toDelete.getSessionId();

                alreadySentMgsSocket = false;

                instTable = "instruction";
                blockTable = null;
                whereId = -1;
                updteBlocks = null;

                if (sessionIdToSend != null) {
                    if (sessionIdToSend.matches(".*botJobTasks.*")) {
                        instTable = "instruction";
                        blockTable = "block";
                        whereId = toDelete.getBotJobId();
                        updteBlocks = "UPDATE_BLOCKS";
                    } else if (sessionIdToSend.matches(".*componentTasks.*")) {
                        instTable = "component_instruction";
                        blockTable = "component_block";
                        whereId = toDelete.getHomeBankingId();
                        updteBlocks = "UPDATE_BLOCKS_COMP";
                    }
                }

                errorMessage = performDataBase.loadBlocks(whereId, "", blockTable);

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

                // Snapshot of previous IDs
                List<Integer> previousIds = (blockTable.equals("block")
                                ? performLists.getListBlock()
                                : performLists.getListBlockComp())
                        .stream()
                                .map(BlockLoadDTO::getId)
                                .filter(Objects::nonNull)
                                .toList();

                List<Integer> currentIds;
                List<Integer> restToDeleteIds;
                List<BlockLoadDTO> listBlocks;

                if (instTable != null) {

                    ARConstants.DialogModal respModal = ARConstants.DialogModal.NONE;

                    errorMessage = performDataBase.loadAllParents(instTable, whereId, toDelete.getId());

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

                    boolean continueDelete = true;
                    boolean deleteParents = false;

                    if (!performLists.getListParentOperations().isEmpty()) {

                        boolean isIF = toDelete.getActions().equalsIgnoreCase("IF")
                                || toDelete.getActions().equalsIgnoreCase("ELSE")
                                || toDelete.getActions().equalsIgnoreCase("ENDIF")
                                || toDelete.getActions().equalsIgnoreCase("ELSEIF");

                        if (!isIF) {
                            List<String> lstMsg =
                                    performMessage.distributeMsg(performLists.getListParentOperations().stream()
                                            .map(p -> p.getName() + " --> (" + p.getInstructionId() + ")-"
                                                    + p.getParentName())
                                            .collect(Collectors.toList()));

                            respModal = performMessage.showCustomModalDialogDragWin11(
                                    "Steps Attached",
                                    "Are you Sure you want to delete?",
                                    lstMsg.get(0),
                                    lstMsg.get(1),
                                    lstMsg.get(2),
                                    false,
                                    "Confirm",
                                    "Cancel",
                                    0);

                            continueDelete = respModal.equals(ARConstants.DialogModal.OK);
                            deleteParents = continueDelete;
                        }

                        if (continueDelete && deleteParents) {
                            errorMessage = performDataBase.deleteRowParents(instTable, whereId, toDelete.getId());
                            if (errorMessage == null) {
                                performLists.updateMemoryRemoveInstructionId(instTable, whereId, toDelete.getId());
                            }

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
                        }
                    } else {
                        continueDelete = true;
                        deleteParents = true;
                    }

                    if (continueDelete && deleteParents) {

                        errorMessage = performDataBase.deleteInstruction(instTable, whereId, toDelete, false);
                        if (errorMessage == null) {
                            performLists.updateMemoryRemoveInstructionId(instTable, whereId, toDelete.getId());
                        }

                        if (errorMessage == null) {
                            errorMessage = performDataBase.loadInstructions(whereId, -1, -1, instTable);
                        }

                        InstructionLoad hasExcelGotoOneBlock = hasOnlyExcelGoto(instTable);

                        if (hasExcelGotoOneBlock != null) {
                            errorMessage =
                                    performDataBase.deleteInstruction(instTable, whereId, hasExcelGotoOneBlock, false);
                        }

                        // Snapshot of previous IDs without repetitions
                        currentIds = (instTable.equals("instruction")
                                        ? performLists.getListInstruction()
                                        : performLists.getListInstructionComp())
                                .stream()
                                        .map(InstructionLoad::getBlockId)
                                        .filter(Objects::nonNull)
                                        .distinct() // removes duplicates
                                        .toList();

                        restToDeleteIds = previousIds.stream()
                                .filter(id -> !currentIds.contains(id))
                                .collect(Collectors.toList());

                        listBlocks = instTable.equals("instruction")
                                ? performLists.getListBlock()
                                : performLists.getListBlockComp();
                        // Keep at least One for BLOCK TABLE
                        if (errorMessage == null
                                && !restToDeleteIds.isEmpty()
                                && (blockTable.equals("block") && listBlocks.size() > 1)) {
                            errorMessage = performDataBase.deleteNullBlocks(blockTable, whereId, restToDeleteIds);

                            // UPDATE REMOVAL MEMORY LIST
                            if (errorMessage == null) {
                                performLists.updateMemoryRemoveBlockIds(blockTable, whereId, restToDeleteIds);
                            }

                        } else if (errorMessage == null
                                && !restToDeleteIds.isEmpty()
                                && (blockTable.equals("component_block"))) {
                            errorMessage = performDataBase.deleteNullBlocks(blockTable, whereId, restToDeleteIds);

                            // UPDATE REMOVAL MEMORY LIST
                            if (errorMessage == null) {
                                performLists.updateMemoryRemoveBlockIds(blockTable, whereId, restToDeleteIds);
                            }
                        }

                        // updateBlockOrderNumber  ALREADY UPDATE MEMORY LIST
                        if (errorMessage == null) {
                            performDataBase.updateBlockOrderNumber(blockTable, whereId, true);
                        }

                        // calls perform list block update
                        toDelete.setType(updteBlocks);
                        jsonData = gson.toJson(toDelete);
                        webSocketSessionManager.sendMessageJson(
                                homeBankingId, "perform-list-data", jsonData, updteBlocks);

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
                    }
                }

                break;
            case "DELETE_BLOCK":
                DeleteBlockDTO deleteBlock = gson.fromJson(jsonEntry, DeleteBlockDTO.class);

                homeBankingId = deleteBlock.getHomeBankingId();
                sessionIdToSend = deleteBlock.getSessionId();
                botJobIdTask = deleteBlock.getBotJobId();

                blockTable = null;
                whereId = -1;
                updteBlocks = null;
                if (sessionIdToSend != null) {
                    if (sessionIdToSend.matches(".*botJobTasks.*")) {
                        blockTable = "block";
                        whereId = deleteBlock.getBotJobId();
                        updteBlocks = "UPDATE_BLOCKS";
                    } else if (sessionIdToSend.matches(".*componentTasks.*")) {
                        blockTable = "component_block";
                        whereId = deleteBlock.getHomeBankingId();
                        updteBlocks = "UPDATE_BLOCKS_COMP";
                    }
                }

                errorMessage = null;

                alreadySentMgsSocket = false;

                if (blockTable != null) {
                    errorMessage = performDataBase.deleteBlockDirect(blockTable, whereId, deleteBlock.getBlockId());
                }

                // UPDATE REMOVAL MEMORY LIST
                if (errorMessage == null) {
                    List<Integer> listDelete = Collections.singletonList(deleteBlock.getBlockId());
                    performLists.updateMemoryRemoveBlockIds(blockTable, whereId, listDelete);
                }

                // updateBlockOrderNumber  ALREADY UPDATE MEMORY LIST
                if (errorMessage == null) {
                    performDataBase.updateBlockOrderNumber(blockTable, whereId, true);
                }

                // calls perform list block update
                deleteBlock.setType(updteBlocks);
                jsonData = gson.toJson(deleteBlock);
                webSocketSessionManager.sendMessageJson(homeBankingId, "perform-list-data", jsonData, updteBlocks);

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
            case "BLOCK_ROLLBACK":
                RollBackBlocksDTO rollBack = gson.fromJson(jsonEntry, RollBackBlocksDTO.class);

                homeBankingId = rollBack.getHomeBankingId();
                sessionIdToSend = rollBack.getSessionId();
                botJobIdTask = rollBack.getBotJobId();

                instTable = null;
                blockTable = null;
                whereId = -1;
                updteBlocks = null;

                if (sessionIdToSend != null) {
                    if (sessionIdToSend.matches(".*botJobTasks.*")) {
                        instTable = "instruction";
                        blockTable = "block";
                        whereId = rollBack.getBotJobId();
                        updteBlocks = "UPDATE_BLOCKS";
                    } else if (sessionIdToSend.matches(".*componentTasks.*")) {
                        instTable = "component_instruction";
                        blockTable = "component_block";
                        whereId = rollBack.getHomeBankingId();
                        updteBlocks = "UPDATE_BLOCKS_COMP";
                    }
                }

                errorMessage = null;

                alreadySentMgsSocket = false;

                if (blockTable != null) {
                    if (performLists.getListBlock().isEmpty()) {
                        performDataBase.loadBlocks(whereId, "", blockTable);
                    }

                    // Snapshot of previous IDs
                    previousIds = (blockTable.equals("block")
                                    ? performLists.getListBlock()
                                    : performLists.getListBlockComp())
                            .stream()
                                    .map(BlockLoadDTO::getId)
                                    .filter(Objects::nonNull)
                                    .toList();

                    errorMessage = performDataBase.rollBackBlocksRows("instruction", rollBack);

                    performDataBase.loadInstructions(whereId, -1, -1, instTable);

                    // Snapshot of previous IDs without repetitions
                    currentIds = (instTable.equals("instruction")
                                    ? performLists.getListInstruction()
                                    : performLists.getListInstructionComp())
                            .stream()
                                    .map(InstructionLoad::getBlockId)
                                    .filter(Objects::nonNull)
                                    .distinct() // removes duplicates
                                    .toList();

                    restToDeleteIds = previousIds.stream()
                            .filter(id -> !currentIds.contains(id))
                            .collect(Collectors.toList());

                    listBlocks =
                            blockTable.equals("block") ? performLists.getListBlock() : performLists.getListBlockComp();
                    // Keep at least One for BLOCK TABLE
                    if (errorMessage == null
                            && !restToDeleteIds.isEmpty()
                            && (blockTable.equals("block") && listBlocks.size() > 1)) {
                        errorMessage = performDataBase.deleteNullBlocks(blockTable, whereId, restToDeleteIds);

                        // UPDATE REMOVAL MEMORY LIST
                        if (errorMessage == null) {
                            performLists.updateMemoryRollBackToOneBlock(blockTable, whereId, restToDeleteIds);
                        }
                    } else if (errorMessage == null
                            && !restToDeleteIds.isEmpty()
                            && (blockTable.equals("component_block"))) {
                        errorMessage = performDataBase.deleteNullBlocks(blockTable, whereId, restToDeleteIds);

                        // UPDATE REMOVAL MEMORY LIST
                        if (errorMessage == null) {
                            performLists.updateMemoryRollBackToOneBlock(blockTable, whereId, restToDeleteIds);
                        }
                    }

                    //                    // ROLL BACK I LOAD AGAIN JUST FOR SAFETY
                    //                    if (errorMessage == null) {
                    //                        errorMessage = performDataBase.loadBlocks(whereId, "", blockTable);
                    //                    }
                }

                // calls perform list block update
                rollBack.setType(updteBlocks);
                jsonData = gson.toJson(rollBack);
                webSocketSessionManager.sendMessageJson(homeBankingId, "perform-list-data", jsonData, updteBlocks);

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

            default:
                webSocketSessionManager.sendMessageJson(
                        homeBankingId, session, type, "Action type : \"" + type + "\"", "cannot be processed");
                break;
        }

        String instrTable = "instruction";
        String updateAction = "updateInstructions";
        ErrorMessage errorMessage = null;

        if (!alreadySentMgsSocket && (sessionIdToSend != null && sessionIdToSend.matches(".*botJobTasks.*"))) {
            if (performLists.getListBotJob().isEmpty()) {
                errorMessage = performDataBase.loadCompleteJobs(botJobIdTask);
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
            }
        } else if (!alreadySentMgsSocket
                && (sessionIdToSend != null && sessionIdToSend.matches(".*componentTasks.*"))) {
            instrTable = "component_instruction";
            updateAction = "componentsUpdate";

            if (performLists.getListBotJobComp().isEmpty()) {
                errorMessage = performDataBase.loadComponentsComplete(homeBankingId, botJobIdTask, botJobNameTask);
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
            }
        }

        if (!alreadySentMgsSocket) {
            List<BotJobLoadDTO> listBot =
                    instrTable.equals("instruction") ? performLists.getListBotJob() : performLists.getListBotJobComp();

            setPayloadEmpty(sessionId, homeBankingId, botJobIdTask, botJobNameTask);
            String jsonData = gson.toJson(payloadEmpty);
            if (!listBot.isEmpty()) {
                List<InstructionLoad> instructionLoads =
                        performDataBase.buildJsonViewData(listBot, homeBankingId, instrTable);
                jsonData = gson.toJson(instructionLoads);
            }

            webSocketSessionManager.sendMessageJson(homeBankingId, sessionIdToSend, jsonData, updateAction);

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
    private ErrorMessage splitBlocks(BlockSplitDTO blockSplitDTO) {
        BlockDetailsDTO originalBlock = blockSplitDTO.getDetails().getOriginalBlock();
        BlockDetailsDTO newBlock = blockSplitDTO.getDetails().getNewBlock();
        List<BlockOrderDetailDTO> updatedBlock = blockSplitDTO.getDetails().getUpdatedBlocks();
        System.out.println("Original Block ID: " + originalBlock.getBlockId());
        System.out.println("New Block Name: " + newBlock.getBlockName());
        System.out.println("Updated Block: " + updatedBlock.size());
        newBlock.setForceOrder(true);

        int homeBankId = blockSplitDTO.getHomeBankingId();
        ErrorMessage errorMessage = performDataBase.insertNewBlock("block", blockSplitDTO.getBotJobId(), newBlock);

        if (errorMessage == null) {
            int newBlockId = -9999;
            if (!performDataBase.getIdsBlockAfter().isEmpty()
                    && performDataBase.getIdsBlockAfter().get(0) > 0) {
                newBlockId = performDataBase.getIdsBlockAfter().get(0);
            }

            errorMessage = performDataBase.updateInstructionsSplitter(
                    newBlock.getInstructions(), originalBlock.getBlockId(), newBlockId);

            // this is Important to update Easi the Memory
            if (errorMessage == null) {
                errorMessage = performDataBase.loadBlocks(blockSplitDTO.getBotJobId(), "", "block");
            }

            if (errorMessage == null) {
                errorMessage = performDataBase.loadCompleteJobs(blockSplitDTO.getBotJobId());
            }

            if (errorMessage == null && !updatedBlock.isEmpty()) {

                if (errorMessage == null) {
                    // Work directly with the List<BlockLoadDTO> in performLists
                    for (BlockOrderDetailDTO updated : updatedBlock) {
                        for (BlockLoadDTO current : performLists.getListBlock()) {
                            if (current.getId() != null && current.getId().equals(updated.getBlockId())) {
                                current.setBlockOrderNumber(updated.getBlockOrderNumber());
                                break;
                            }
                        }
                    }

                    List<BlockLoadDTO> mappedBlocks = mapToBlockLoad(homeBankId, updatedBlock);

                    //                performDataBase.loadBlocks(blockSplitDTO.getBotJobId(), "", "block");
                    errorMessage = performDataBase.updateSwiftBlockOrderNumber(
                            "block", blockSplitDTO.getBotJobId(), mappedBlocks);

                    // UPDATE BLOCK ORDER MEMORY LIST
                    if (errorMessage == null) {
                        performLists.updateMemorySwiftBlockOrder("block", blockSplitDTO.getBotJobId(), mappedBlocks);
                    }
                }

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
            }
        } else {
            performMessage.errorMessage(
                    errorMessage.getErrorTitle(),
                    "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Operation Failed!</span> ❌",
                    "<span style='color: #E65100; font-weight: bold;'>Error Type:</span> "
                            + errorMessage.getErrorHeader(),
                    "<span style='font-style: italic;'>Detail:</span> " + errorMessage.getErrorMessage(),
                    null,
                    0);
        }

        //        performDataBase.loadBlocks(blockSplitDTO.getBotJobId(), "", "block");
        return errorMessage;
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
                                "ELSEIF", "ELSEIF", ARConstants.ELSEIF, ARConstants.ELSEIF, 1, rowMoveDTO, false);

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

        // Add at the end of it
        if (!performLists.getListBlockComp().isEmpty()) {
            blockDetailsDTO.setBlockOrderNumber(performLists.getListBlockComp().size() + 1);
        } else {
            blockDetailsDTO.setBlockOrderNumber(1);
        }

        Platform.runLater(() -> {
            arSaveComponentScene.initialize(blockDetailsDTO);
            arSaveComponentScene.showModal();
        });
    }

    private void injectBlockComponent(BlockSplitDTO blockSplitDTO) {
        // Ensure JavaFX UI updates are done on the JavaFX Application Thread

        BlockDetailsDTO blockDetailsDTO = blockSplitDTO.getDetails().getNewBlock();
        blockDetailsDTO.setHomeBankingId(blockSplitDTO.getHomeBankingId());
        blockDetailsDTO.setBotJobId(blockSplitDTO.getBotJobId());
        blockDetailsDTO.setSessionId(blockSplitDTO.getSessionId());

        ErrorMessage errorMessage = null;

        // Add at the end of it
        if (!performLists.getListBlock().isEmpty()) {
            blockDetailsDTO.setBlockOrderNumber(performLists.getListBlock().size() + 1);
        } else {
            blockDetailsDTO.setBlockOrderNumber(1);
        }

        if (errorMessage == null) {
            errorMessage = performDataBase.createInjectBlock(blockDetailsDTO);
        }

        int newBlockId = -9999;
        if (!performDataBase.getIdsBlockAfter().isEmpty()
                && performDataBase.getIdsBlockAfter().get(0) > 0) {
            newBlockId = performDataBase.getIdsBlockAfter().get(0);
        }

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
            errorMessage = performDataBase.updateBlockOrderNumber("block", blockDetailsDTO.getBotJobId(), true);
        }

        if (errorMessage == null) {
            errorMessage = performDataBase.loadBlocks(blockDetailsDTO.getBotJobId(), "", "block");
        }
        if (errorMessage == null) {

            errorMessage = performDataBase.loadCompleteJobs(blockDetailsDTO.getBotJobId());
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

            String jsonData = "[]";
            if (!performLists.getListBotJob().isEmpty()) {
                List<InstructionLoad> blockLoopInstructions = performDataBase.buildJsonViewData(
                        performLists.getListBotJob(), blockDetailsDTO.getBotJobId(), "instruction");
                jsonData = gson.toJson(blockLoopInstructions);
            }
            webSocketSessionManager.sendMessageJson(
                    blockDetailsDTO.getHomeBankingId(), blockDetailsDTO.getSessionId(), jsonData, "updateInstructions");

        } else {
            performDataBase.deleteBlockDirect("block", blockDetailsDTO.getBotJobId(), newBlockId);
            performMessage.errorMessage(
                    "Access Database error",
                    errorMessage.getErrorTitle(),
                    errorMessage.getErrorHeader(),
                    "Verify  [INSERT] or [UPDATE] or [SELECT]",
                    null,
                    0);
        }
    }

    private void setPayloadEmpty(String destination, int homeBankId, int botJobId, String botJobName) {
        int blockId = -1;
        int whereId = -1;
        if (destination.equalsIgnoreCase("botJobTasks")) {
            if (performLists.getListBlock().isEmpty()) {
                performDataBase.loadBlocks(botJobId, botJobName, "block");
            }
            whereId = botJobId;
            if (!performLists.getListBlock().isEmpty()) {
                blockId = performLists.getListBlock().get(0).getId();
            }

        } else if (destination.equalsIgnoreCase("componentTasks")) {
            if (!performLists.getListBotJobComp().isEmpty()
                    && performLists.getListBlockComp().isEmpty()) {
                performDataBase.loadBlocks(homeBankId, "", "component_block");
            }
            whereId = homeBankId;
            if (!performLists.getListBlockComp().isEmpty()) {
                blockId = performLists.getListBlockComp().get(0).getId();
            }
        }
        this.payloadEmpty = new PayloadJson(whereId, blockId, botJobName, 0);
    }

    public InstructionLoad hasOnlyExcelGoto(String instTable) {
        List<InstructionLoad> instructions = instTable.equals("instruction")
                ? performLists.getListInstruction()
                : performLists.getListInstructionComp();

        List<InstructionLoad> excelGotoInstructions = instructions.stream()
                .filter(instr -> "EXCEL GOTO".equalsIgnoreCase(instr.getActions()))
                .toList();

        if (excelGotoInstructions.isEmpty()) {
            return null;
        }

        // all blockIds used by "EXCEL GOTO"
        Set<Integer> excelGotoBlockIds = excelGotoInstructions.stream()
                .map(InstructionLoad::getBlockId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // check each blockId has *only* EXCEL GOTO instructions
        boolean uniqueBlocks = excelGotoBlockIds.size() == 1
                && instructions.stream()
                        .filter(instr -> excelGotoBlockIds.contains(instr.getBlockId()))
                        .allMatch(instr -> "EXCEL GOTO".equalsIgnoreCase(instr.getActions()));

        if (uniqueBlocks) {
            InstructionLoad first = excelGotoInstructions.get(0);
            // remove all EXCEL GOTO instructions from that block
            instructions.removeIf(instr -> excelGotoBlockIds.contains(instr.getBlockId()));
            return first;
        }

        return null;
    }

    public List<BlockLoadDTO> mapToBlockLoad(int homeBankId, List<BlockOrderDetailDTO> updatedBlock) {
        if (updatedBlock == null || updatedBlock.isEmpty()) {
            return new ArrayList<>();
        }

        return updatedBlock.stream()
                .map(dto -> {
                    BlockLoadDTO blockLoadDTO = new BlockLoadDTO();
                    blockLoadDTO.setHomeBankingId(homeBankId);
                    blockLoadDTO.setId(dto.getBlockId());
                    blockLoadDTO.setBotJobId(dto.getBotJobId());
                    blockLoadDTO.setBlockOrderNumber(dto.getBlockOrderNumber());
                    blockLoadDTO.setName(dto.getBlockName());

                    // Optional: initialize remaining fields to default/null
                    blockLoadDTO.setHomeBankingName(null);
                    blockLoadDTO.setDescription(null);
                    blockLoadDTO.setTypeId(null);
                    blockLoadDTO.setBotJobName(null);
                    blockLoadDTO.setExportFile(null);
                    blockLoadDTO.setActive(null);
                    blockLoadDTO.setWait(null);
                    blockLoadDTO.setInstructionLoad(new ArrayList<>());

                    return blockLoadDTO;
                })
                .toList();
    }
}

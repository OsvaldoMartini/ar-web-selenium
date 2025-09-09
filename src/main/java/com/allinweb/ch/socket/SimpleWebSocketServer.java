package com.allinweb.ch.socket;

import com.allinweb.ch.component.model.*;
import com.allinweb.ch.component.scene.ARExcelFileScene;
import com.allinweb.ch.component.scene.ARSaveComponentScene;
import com.allinweb.ch.facade.PerformDBEngine;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.util.ARConstants;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.allinweb.ch.util.ErrorMessage;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;

// Simple WebSocket server endpoint (for demonstration)
@Slf4j
@ServerEndpoint("/websocket")
public class SimpleWebSocketServer {

    private static final ARPropertyManager arPropertyManager = ARPropertyManager.getInstance();
    private static final PerformLists performLists = PerformLists.getInstance();
    private static final PerformDBEngine performDBEngine = PerformDBEngine.getInstance();
    private static final PerformDataBase performDataBase = PerformDataBase.getInstance();
    private static final PerformMessage performMessage = PerformMessage.getInstance();
    protected static volatile SimpleWebSocketServer instance;
    private static WebSocketSessionManager webSocketSessionManager = WebSocketSessionManager.getInstance();
    private static ARExcelFileScene arExcelFileScene = ARExcelFileScene.getInstance();
    private static ARSaveComponentScene arSaveComponentScene = ARSaveComponentScene.getInstance();
    private final Gson gson = new Gson();
    private PayloadJson payloadEmpty;
    private RowStatus rowStatus = new RowStatus();
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

            if (sessionId.equals("engine-perform-bot-job")) {
                JsonObject jsonRowStatus = JsonParser.parseString(
                                jsonObjMSG.get("body").getAsString())
                        .getAsJsonObject();

                if (jsonRowStatus.has("instructionId") && jsonRowStatus.has("color")) {
                    int instructionId = jsonRowStatus.get("instructionId").getAsInt();
                    String color = jsonRowStatus.get("color").getAsString();

                    rowStatus.setInstructionId(instructionId);
                    rowStatus.setColor(color); // e.g. "#fcba03" deep carmine yellow
                }

                String jsonStatus = gson.toJson(rowStatus);
                webSocketSessionManager.sendMessageJson(homeBankingId, "botJobTasks", jsonStatus, "rowStatus");
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

        boolean alreadySentMgsSocket = false;
        ErrorMessage errorMessage = null;
        SplitDTO splitDTO = parseSplitDTO(jsonEntry);

        String sessionIdToSend = splitDTO.getSessionId();
        String operationId = splitDTO.getOperationId() != null ? splitDTO.getOperationId() : "";
        int homeBankingId = splitDTO.getHomeBankingId() != null ? splitDTO.getHomeBankingId() : -1;
        int botJobIdTask = splitDTO.getBotJobId() != null ? splitDTO.getBotJobId() : -1;
        String botJobNameTask = splitDTO.getBotJobName() != null ? splitDTO.getBotJobName() : "1# Default Block";

        // Block-level details
        int blockId = splitDTO.getBlockId() != null ? splitDTO.getBlockId() : -1;
        String blockName = splitDTO.getBlockName() != null ? splitDTO.getBlockName() : "";
        int blockOrderNum = splitDTO.getBlockOrderNumber() != null ? splitDTO.getBlockOrderNumber() : -1;
        boolean blockActive = splitDTO.getBlockActive() != null && splitDTO.getBlockActive();

        // Instruction-level details
        int instructionId = splitDTO.getInstructionId() != null ? splitDTO.getInstructionId() : -1;
        String instructionName = splitDTO.getInstructionName() != null ? splitDTO.getInstructionName() : "";
        int instructionOrderNum =
                splitDTO.getInstructionOrderNumber() != null ? splitDTO.getInstructionOrderNumber() : -1;
        boolean instrucionActive = splitDTO.getInstructionActive() != null && splitDTO.getInstructionActive();

        // Other instruction metadata
        String actions = splitDTO.getActions() != null ? splitDTO.getActions() : "";
        String operation = splitDTO.getOperation() != null ? splitDTO.getOperation() : "";

        // Hierarchy & variables
        int variableId = splitDTO.getVariableId() != null ? splitDTO.getVariableId() : -1;
        int parentId = splitDTO.getParentId() != null ? splitDTO.getParentId() : -1;
        int parentBlockId = splitDTO.getParentBlockId() != null ? splitDTO.getParentBlockId() : -1;

        // Optional fields for SplitDTO
        ElementDTO[] elementDetails =
                splitDTO.getElementDetails() != null ? splitDTO.getElementDetails() : new ElementDTO[0];

        // Optional fields for BlockSplitDTO
        DetailsDTO details = splitDTO.getDetails() != null ? splitDTO.getDetails() : new DetailsDTO();

        // Optional fields for UpdateRows
        List<UpdatedRow> updatedRows =
                splitDTO.getUpdatedRows() != null ? splitDTO.getUpdatedRows() : new ArrayList<>();

        String instrTable = null;
        String blockTable = null;
        String variableTable = null;
        String updteBlocks = "";
        String updateAction = null;

        int whereId = -1;

        if (sessionIdToSend != null) {
            if (sessionIdToSend.matches(".*botJobTasks.*")
                    || sessionIdToSend.matches(".*scannerTool.*")
                    || sessionIdToSend.matches(".*scannerGrid.*")
                    || sessionIdToSend.matches(".*scanner-element-pane.*")) {
                instrTable = "instruction";
                blockTable = "block";
                variableTable = "variable";
                whereId = splitDTO.getBotJobId();
                updteBlocks = "UPDATE_BLOCKS";
                updateAction = "updateInstructions";
            } else if (sessionIdToSend.matches(".*componentTasks.*")) {
                instrTable = "component_instruction";
                blockTable = "component_block";
                variableTable = "component_variable";
                whereId = splitDTO.getHomeBankingId();
                updteBlocks = "UPDATE_BLOCKS_COMP";
                updateAction = "componentsUpdate";
            }
        }

        errorMessage = performDataBase.loadBlocks(whereId, "", blockTable);

        List<Integer> previousBlockIds = (blockTable.equals("block")
                        ? performLists.getListBlock()
                        : performLists.getListBlockComp())
                .stream().map(BlockLoadDTO::getId).filter(Objects::nonNull).toList();

        performDataBase.loadInstructions(whereId, -1, -1, instrTable);

        try {
            List<BotJobLoadDTO> listBotJob =
                    blockTable.equals("block") ? performLists.getListBotJob() : performLists.getListBotJobComp();

            List<BlockLoadDTO> listBlocks =
                    blockTable.equals("block") ? performLists.getListBlock() : performLists.getListBlockComp();

            if (!listBotJob.isEmpty() && listBlocks.isEmpty()) {
                errorMessage = performDataBase.loadBlocks(whereId, botJobNameTask, blockTable);
            }

            if (errorMessage == null) {
                errorMessage = performDataBase.checkGapsBlockOrder(listBlocks, blockTable, whereId, botJobNameTask);
            }

            switch (type) {
                case "CLOSE_BROWSER":
                    if (sessionIdToSend.equals("scanner-element-pane")) {
                        splitDTO.setOperationId("closeBrowser");
                        String jsonData = gson.toJson(splitDTO);
                        webSocketSessionManager.sendMessageJson(
                                homeBankingId, sessionIdToSend, jsonData, "closeBrowser");
                    }
                    alreadySentMgsSocket = true;
                    break;
                case "HOVERED_ROW":
                    if (sessionIdToSend.equals("scannerTool")) {
                        splitDTO.setOperationId("highlight");
                        String jsonData = gson.toJson(splitDTO);
                        webSocketSessionManager.sendMessageJson(homeBankingId, sessionIdToSend, jsonData, null);
                    }
                    alreadySentMgsSocket = true;
                    break;
                case "SEARCH_TOOL":
                    if (sessionIdToSend.equals("scannerGrid")) {
                        String jsonData = gson.toJson(splitDTO);
                        webSocketSessionManager.sendMessageJson(homeBankingId, sessionIdToSend, jsonData, "addPickOne");

                        List<String> excludeList = List.of("optional", "blockMarked", "editMode");
                        String jsonPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_DB);
                        performMessage.outputJsonElementDTO(
                                splitDTO.getElementDetails(), excludeList, "elementDTO", jsonPath);
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
                        performMessage.outputJsonElementDTO(
                                splitDTO.getElementDetails(), excludeList, "AI-ElementDTO", jsonPath);
                    }
                    alreadySentMgsSocket = true;
                    break;
                case "NEW_ELEMENT_DTO":
                case "SEND_ALL_ELEMENTS_DTO":
                case "DEL_ELEMENT_DTO":
                case "DETAILS_ELEMENT_DTO":
                case "TEST_CLICK_DTO":
                case "TEST_INPUT_DTO":
                    if (splitDTO.getElementDetails() != null && splitDTO.getElementDetails().length > 0) {
                        webSocketSessionManager.sendMessageJson(
                                "scanner-element-pane", gson.toJson(splitDTO)); // Sending as details
                    }
                    alreadySentMgsSocket = true;
                    break;
                case "RESPONSE_BACK":
                    splitDTO.setType("MARTINI");
                    String jsonData = gson.toJson(splitDTO);
                    webSocketSessionManager.sendMessageJson(homeBankingId, session, "Martini", jsonData, null);
                    alreadySentMgsSocket = true;
                    break;
                case "BLOCKS_COMPONENT":
                    createBlockComponent(splitDTO);

                    // calls perform list block update
                    splitDTO.setType("UPDATE_BLOCKS_COMP");
                    jsonData = gson.toJson(splitDTO);
                    webSocketSessionManager.sendMessageJson(
                            homeBankingId, "perform-list-data", jsonData, "UPDATE_BLOCKS_COMP");
                    alreadySentMgsSocket = true;
                    break;
                case "COMPONENT_INJECT":
                    injectBlockComponent(splitDTO);
                    // calls perform list block update
                    splitDTO.setType("UPDATE_BLOCKS");
                    jsonData = gson.toJson(splitDTO);
                    webSocketSessionManager.sendMessageJson(
                            homeBankingId, "perform-list-data", jsonData, "UPDATE_BLOCKS");
                    alreadySentMgsSocket = true;
                    break;
                case "BLOCKS_SPLITTER":
                    errorMessage = splitBlocks(splitDTO);

                    if (blockTable != null && errorMessage == null) {
                        // updateBlockOrderNumber  ALREADY UPDATE MEMORY LIST
                        errorMessage = performDataBase.updateBlockOrderNumber(blockTable, whereId, true);
                    }

                    // calls perform list block update
                    splitDTO.setType(updteBlocks);
                    jsonData = gson.toJson(splitDTO);
                    webSocketSessionManager.sendMessageJson(homeBankingId, "perform-list-data", jsonData, updteBlocks);
                    alreadySentMgsSocket = false;
                    break;
                case "BLOCK_MOVE":
                    try {
                        if (blockTable != null) {
                            List<BlockLoadDTO> mappedBlocks =
                                    mapToBlockLoad(homeBankingId, splitDTO.getUpdatedBlocks());
                            errorMessage =
                                    performDataBase.updateSwiftBlockOrderNumber(blockTable, whereId, mappedBlocks);

                            // UPDATE BLOCK ORDER MEMORY LIST
                            if (errorMessage == null) {
                                performLists.updateMemorySwiftBlockOrder(blockTable, whereId, mappedBlocks);
                            }

                            // calls perform list block update
                            splitDTO.setType(updteBlocks);
                            jsonData = gson.toJson(splitDTO);
                            webSocketSessionManager.sendMessageJson(
                                    homeBankingId, "perform-list-data", jsonData, updteBlocks);
                        }

                    } catch (Exception error) {
                        log.error("Error: " + error.getMessage());
                    }
                    alreadySentMgsSocket = false;
                    break;
                case "ROW_UPDATE":
                    InstructionLoad instructionLoad = SplitDTO.mapSplitToInstruction(splitDTO);
                    errorMessage = performDataBase.rowsUpdateName(
                            instrTable, whereId, Collections.singletonList(instructionLoad));

                    if (errorMessage == null) {
                        errorMessage = performDataBase.loadAllParents(instrTable, whereId, splitDTO.getInstructionId());

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
                                        instrTable, whereId, performLists.getListParentOperations());

                                // UPDATE MEMORY LIST FOR PARENTS OPERATION NAMES
                                //                            performLists.updateMemoryParentOpenName(instrTable,
                                // whereId,
                                // listParents);
                            }
                        }
                    }

                    // UPDATE MEMORY LIST FOR PARENTS INSTRUCTION NAME
                    if (errorMessage == null) {
                        performLists.updateMemoryInstructionName(
                                instrTable, whereId, Collections.singletonList(instructionLoad));
                        performLists.updateMemoryParentOpenName(
                                instrTable, whereId, performLists.getListParentOperations());
                    }

                    alreadySentMgsSocket = false;
                    break;
                case "ROW_MOVE":
                    errorMessage = performDataBase.updateMoveRowsOrder(blockTable, whereId, splitDTO.getUpdatedRows());

                    // It needs to Reload this List
                    if (errorMessage == null) {
                        errorMessage = performDataBase.loadInstructions(whereId, -1, -1, instrTable);
                    }

                    List<InstructionLoad> rowsList = instrTable.equals("instruction")
                            ? performLists.getListInstruction()
                            : performLists.getListInstructionComp();

                    InstructionLoad hasExcelGotoOneBlock = hasOnlyExcelGoto(rowsList, instrTable);

                    if (hasExcelGotoOneBlock != null) {
                        errorMessage =
                                performDataBase.deleteInstruction(instrTable, whereId, hasExcelGotoOneBlock, false);
                    }

                    // FIRST updateMemoryRowMove  ALREADY UPDATE MEMORY LIST
                    performLists.updateMemoryRowMove(blockTable, whereId, splitDTO.getUpdatedRows());

                    errorMessage = deleteNullsAndMemoryReload(instrTable, blockTable, whereId, previousBlockIds);

                    // updateBlockOrderNumber  ALREADY UPDATE MEMORY LIST
                    if (errorMessage == null) {
                        errorMessage = performDataBase.updateBlockOrderNumber(blockTable, whereId, true);
                    }

                    if (errorMessage == null) {
                        final int finalWhereId = whereId;

                        List<BlockLoadDTO> blockLoad = instrTable.equals("instruction")
                                ? performLists.getListBotJob().stream()
                                        .filter(b -> Objects.equals(b.getId(), finalWhereId))
                                        .findFirst()
                                        .map(BotJobLoadDTO::getBlockLoadDTOList)
                                        .orElse(Collections.emptyList())
                                : performLists.getListBotJobComp().stream()
                                        .filter(b -> Objects.equals(b.getHomeBankingId(), finalWhereId))
                                        .findFirst()
                                        .map(BotJobLoadDTO::getBlockLoadDTOList)
                                        .orElse(Collections.emptyList());

                        errorMessage = performDataBase.reorderInstructionsListBlock(blockLoad, instrTable, true);
                    }

                    // calls perform list block update
                    splitDTO.setType(updteBlocks);
                    jsonData = gson.toJson(splitDTO);
                    webSocketSessionManager.sendMessageJson(homeBankingId, "perform-list-data", jsonData, updteBlocks);

                    alreadySentMgsSocket = false;
                    break;
                case "INSERT_BEFORE":
                case "INSERT_AFTER":
                case "INSERT_NEW":
                case "INSERT_AFTER_ELSEIF":
                case "INSERT_BEFORE_ELSEIF":
                case "EDIT_OPERATION":
                    injectStepAfterOrBefore(blockTable, whereId, splitDTO);

                    if (type.equals("INSERT_AFTER_ELSEIF") || type.equals("INSERT_BEFORE_ELSEIF")) {
                        alreadySentMgsSocket = false;
                    } else {
                        alreadySentMgsSocket = true;
                    }
                    break;
                case "BLOCK_EXCEL_FILE":
                    excelFileBlock(sessionIdToSend, splitDTO);
                    alreadySentMgsSocket = true;
                    break;
                case "BLOCK_ORDER":
                    if (!splitDTO.getUpdatedBlocks().isEmpty()) {
                        errorMessage = performDataBase.loadBlocks(whereId, "", blockTable);

                        // updateBlockOrderNumber  ALREADY UPDATE MEMORY LIST
                        if (errorMessage == null) {
                            errorMessage = performDataBase.updateBlockOrderNumber(blockTable, whereId, true);
                        }

                        // calls perform list block update
                        splitDTO.setType(updteBlocks);
                        jsonData = gson.toJson(splitDTO);
                        webSocketSessionManager.sendMessageJson(
                                homeBankingId, "perform-list-data", jsonData, updteBlocks);

                        alreadySentMgsSocket = false;
                    }
                    break;
                case "INSTRUCTION_STATUS":
                    errorMessage = performDataBase.updateInstructionStatus(
                            instrTable, whereId, instructionId, blockId, parentId, actions, instrucionActive);

                    // MEMORY UPDATE
                    if (errorMessage == null) {
                        performLists.updateMemoryInstructionStatusUpdate(
                                instrTable, whereId, instructionId, instrucionActive);
                    }
                    alreadySentMgsSocket = false;
                    break;
                case "BLOCK_STATUS":
                    errorMessage = performDataBase.updateBlockStatus(blockTable, whereId, blockId, blockActive);

                    if (errorMessage == null) {
                        performDataBase.updateInstructionStatusByBlock(instrTable, whereId, blockId, blockActive);
                    }

                    if (errorMessage == null) {
                        performLists.updateMemoryBlockStatusUpdate(blockTable, whereId, blockId, blockActive);
                    }

                    alreadySentMgsSocket = false;
                    break;
                case "BLOCK_UPDATE":
                    errorMessage = performDataBase.updateBlockName(whereId, blockTable, blockId, blockName);
                    splitDTO.setType(updteBlocks);

                    // MEMORY UPDATE BLOCK NAME
                    performLists.updateMemoryBlockName(blockTable, whereId, blockId, blockName);

                    jsonData = gson.toJson(splitDTO);
                    webSocketSessionManager.sendMessageJson(homeBankingId, "perform-list-data", jsonData, updteBlocks);
                    alreadySentMgsSocket = false;
                    break;
                case "DELETE_INSTRUCTION":
                    ARConstants.DialogModal respModal = ARConstants.DialogModal.NONE;

                    errorMessage = performDataBase.loadAllParents(instrTable, whereId, instructionId);

                    boolean continueDelete = true;
                    boolean deleteParents = false;

                    if (errorMessage == null
                            && !performLists.getListParentOperations().isEmpty()) {

                        boolean isIF = actions.equalsIgnoreCase("IF")
                                || actions.equalsIgnoreCase("ELSE")
                                || actions.equalsIgnoreCase("ENDIF")
                                || actions.equalsIgnoreCase("ELSEIF");

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
                            errorMessage =
                                    performDataBase.deleteRowParents(instrTable, whereId, splitDTO.getInstructionId());
                            if (errorMessage == null) {
                                for (ParentOperations parent : performLists.getListParentOperations()) {
                                    performLists.updateMemoryRemoveInstructionId(instrTable, whereId, parent.getId());
                                }
                            }
                        }
                    } else {
                        continueDelete = true;
                        deleteParents = true;
                    }

                    if (continueDelete && deleteParents) {

                        InstructionLoad instrDelete = SplitDTO.mapSplitToInstruction(splitDTO);

                        errorMessage = performDataBase.deleteInstruction(instrTable, whereId, instrDelete, false);
                        if (errorMessage == null) {
                            performLists.updateMemoryRemoveInstructionId(
                                    instrTable, whereId, splitDTO.getInstructionId());
                        }

                        if (errorMessage == null) {
                            errorMessage = performDataBase.loadInstructions(whereId, -1, -1, instrTable);
                        }

                        rowsList = instrTable.equals("instruction")
                                ? performLists.getListInstruction()
                                : performLists.getListInstructionComp();

                        hasExcelGotoOneBlock = hasOnlyExcelGoto(rowsList, instrTable);

                        if (hasExcelGotoOneBlock != null) {
                            errorMessage =
                                    performDataBase.deleteInstruction(instrTable, whereId, hasExcelGotoOneBlock, false);
                        }

                        if (errorMessage == null) {
                            errorMessage =
                                    deleteNullsAndMemoryReload(instrTable, blockTable, whereId, previousBlockIds);
                        }
                        // updateBlockOrderNumber  ALREADY UPDATE MEMORY LIST
                        if (errorMessage == null) {
                            performDataBase.updateBlockOrderNumber(blockTable, whereId, true);
                        }

                        if (errorMessage == null) {
                            final int finalWhereId = whereId;

                            List<BlockLoadDTO> blockLoad = instrTable.equals("instruction")
                                    ? performLists.getListBotJob().stream()
                                            .filter(b -> Objects.equals(b.getId(), finalWhereId))
                                            .findFirst()
                                            .map(b -> b.getBlockLoadDTOList().stream()
                                                    .filter(block ->
                                                            Objects.equals(block.getId(), splitDTO.getBlockId()))
                                                    .toList())
                                            .orElse(Collections.emptyList())
                                    : performLists.getListBotJobComp().stream()
                                            .filter(b -> Objects.equals(b.getHomeBankingId(), finalWhereId))
                                            .findFirst()
                                            .map(b -> b.getBlockLoadDTOList().stream()
                                                    .filter(block ->
                                                            Objects.equals(block.getId(), splitDTO.getBlockId()))
                                                    .toList())
                                            .orElse(Collections.emptyList());

                            // Only Need the Block I am Current Working
                            errorMessage = performDataBase.reorderInstructionsListBlock(blockLoad, instrTable, true);
                        }

                        // calls perform list block update
                        splitDTO.setType(updteBlocks);
                        jsonData = gson.toJson(splitDTO);
                        webSocketSessionManager.sendMessageJson(
                                homeBankingId, "perform-list-data", jsonData, updteBlocks);
                    }

                    alreadySentMgsSocket = false;
                    break;
                case "DELETE_BLOCK":
                    errorMessage = performDataBase.deleteBlockDirect(blockTable, whereId, splitDTO.getBlockId());
                    // UPDATE REMOVAL MEMORY LIST
                    if (errorMessage == null) {
                        List<Integer> listDelete = Collections.singletonList(splitDTO.getBlockId());
                        performLists.updateMemoryRemoveBlockIds(blockTable, whereId, listDelete);
                    }

                    // updateBlockOrderNumber  ALREADY UPDATE MEMORY LIST
                    if (errorMessage == null) {
                        performDataBase.updateBlockOrderNumber(blockTable, whereId, true);
                    }

                    // calls perform list block update
                    splitDTO.setType(updteBlocks);
                    jsonData = gson.toJson(splitDTO);
                    webSocketSessionManager.sendMessageJson(homeBankingId, "perform-list-data", jsonData, updteBlocks);

                    alreadySentMgsSocket = false;
                    break;
                case "BLOCK_ROLLBACK":
                    errorMessage = performDataBase.rollBackBlocksRows("instruction", splitDTO);

                    if (errorMessage == null) {
                        errorMessage =
                                deleteNullsRollbackAndMemoryReload(instrTable, blockTable, whereId, previousBlockIds);
                    }

                    // calls perform list block update
                    splitDTO.setType(updteBlocks);
                    jsonData = gson.toJson(splitDTO);
                    webSocketSessionManager.sendMessageJson(homeBankingId, "perform-list-data", jsonData, updteBlocks);
                    alreadySentMgsSocket = false;
                    break;
                default:
                    webSocketSessionManager.sendMessageJson(
                            homeBankingId, session, type, "Action type : \"" + type + "\"", "cannot be processed");
                    break;
            }

            if (errorMessage == null) {
                errorMessage = performDataBase.checkGapsBlockOrder(listBlocks, blockTable, whereId, botJobNameTask);
            }
        } catch (Exception error) {
            log.error("Error: " + error.getMessage());

            if (errorMessage == null) {
                errorMessage = deleteNullsAndMemoryReload(instrTable, blockTable, whereId, previousBlockIds);
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

        if (!alreadySentMgsSocket && (sessionIdToSend != null && sessionIdToSend.matches(".*botJobTasks.*"))) {
            if (performLists.getListBotJob().isEmpty()) {
                errorMessage = performDBEngine.loadCompleteJobs(botJobIdTask);
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
                List<InstructionLoad> instructionLoads = performLists.buildJsonViewData(listBot);
                if (!instructionLoads.isEmpty()) {
                    jsonData = gson.toJson(instructionLoads);
                }
            }

            webSocketSessionManager.sendMessageJson(homeBankingId, sessionIdToSend, jsonData, updateAction);

            //            broadcastMessageToAll(homeBankingId, "componentTasks", jsonData, "componentsUpdate");
            //            sendMessageJson(sessionIdToSend, jsonData, "componentsUpdate");
        }
    }

    private ErrorMessage deleteNullsAndMemoryReload(
            String instrTable, String blockTable, int whereId, List<Integer> previousBlockIds) {
        ErrorMessage errorMessage = null;
        // Snapshot of previous IDs without repetitions

        List<Integer> currentIds = (instrTable.equals("instruction")
                        ? performLists.getListInstruction()
                        : performLists.getListInstructionComp())
                .stream()
                        .map(InstructionLoad::getBlockId)
                        .filter(Objects::nonNull)
                        .distinct() // removes duplicates
                        .toList();

        if (!currentIds.isEmpty()) {

            List<Integer> restToDeleteIds = previousBlockIds.stream()
                    .filter(id -> !currentIds.contains(id))
                    .collect(Collectors.toList());

            List<BlockLoadDTO> listBlocks =
                    instrTable.equals("instruction") ? performLists.getListBlock() : performLists.getListBlockComp();
            // Keep at least One for BLOCK TABLE
            if (errorMessage == null
                    && !restToDeleteIds.isEmpty()
                    && (blockTable.equals("block") && listBlocks.size() > 1)) {
                errorMessage = performDataBase.deleteNullBlocks(blockTable, whereId, restToDeleteIds);

                // UPDATE REMOVAL MEMORY LIST
                if (errorMessage == null) {
                    performLists.updateMemoryRemoveBlockIds(blockTable, whereId, restToDeleteIds);
                }

            } else if (errorMessage == null && !restToDeleteIds.isEmpty() && (blockTable.equals("component_block"))) {
                errorMessage = performDataBase.deleteNullBlocks(blockTable, whereId, restToDeleteIds);

                // UPDATE REMOVAL MEMORY LIST
                if (errorMessage == null) {
                    performLists.updateMemoryRemoveBlockIds(blockTable, whereId, restToDeleteIds);
                }
            }
        }
        return errorMessage;
    }

    private ErrorMessage deleteNullsRollbackAndMemoryReload(
            String instrTable, String blockTable, int whereId, List<Integer> previousIds) {
        ErrorMessage errorMessage = performDataBase.loadInstructions(whereId, -1, -1, instrTable);

        // Snapshot of previous IDs without repetitions
        List<Integer> currentIds = (instrTable.equals("instruction")
                        ? performLists.getListInstruction()
                        : performLists.getListInstructionComp())
                .stream()
                        .map(InstructionLoad::getBlockId)
                        .filter(Objects::nonNull)
                        .distinct() // removes duplicates
                        .toList();

        if (!currentIds.isEmpty()) {
            List<Integer> restToDeleteIds =
                    previousIds.stream().filter(id -> !currentIds.contains(id)).collect(Collectors.toList());

            List<BlockLoadDTO> listBlocks =
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
            } else if (errorMessage == null && !restToDeleteIds.isEmpty() && (blockTable.equals("component_block"))) {
                errorMessage = performDataBase.deleteNullBlocks(blockTable, whereId, restToDeleteIds);

                // UPDATE REMOVAL MEMORY LIST
                if (errorMessage == null) {
                    performLists.updateMemoryRollBackToOneBlock(blockTable, whereId, restToDeleteIds);
                }
            }
        }

        //                    // ROLL BACK I LOAD AGAIN JUST FOR SAFETY
        //                    if (errorMessage == null) {
        //                        errorMessage = performDataBase.loadBlocks(whereId, "", blockTable);
        //                    }
        return errorMessage;
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
    private ErrorMessage splitBlocks(SplitDTO blockSplitDTO) {
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
                errorMessage = performDBEngine.loadCompleteJobs(blockSplitDTO.getBotJobId());
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

    private void injectStepAfterOrBefore(String blockTable, int whereId, SplitDTO splitDTO) {

        BlockLoadDTO blockLoadFound = performLists.getBlockLoadByBankId(blockTable, whereId, splitDTO.getBlockId());

        ErrorMessage errorMessage = null;
        if (blockLoadFound == null) {
            errorMessage =
                    performDataBase.initiateNewBlock(blockTable, whereId, "Default Block", "Default Block", 1, false);
        }

        if (errorMessage == null && blockLoadFound == null) {
            int newBlockId = -9999;
            if (!performDataBase.getIdsBlockAfter().isEmpty()
                    && performDataBase.getIdsBlockAfter().get(0) > 0) {
                newBlockId = performDataBase.getIdsBlockAfter().get(0);
            }
            // IT SETS THE NEW TARGET IN CASE TO ADD MORE INSTRUCTIONS
            splitDTO.setBlockId(newBlockId);
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

        if (!splitDTO.getType().equals("INSERT_BEFORE_ELSEIF")
                && !splitDTO.getType().equals("INSERT_AFTER_ELSEIF")) {

            webSocketSessionManager.sendMessageJson("new-command-scene", gson.toJson(splitDTO));

        } else {

            try {
                ErrorMessage message = performDataBase.preFillNewInstruction(
                        "ELSEIF", "ELSEIF", ARConstants.ELSEIF, ARConstants.ELSEIF, 1, splitDTO, false);

            } catch (Exception e) {

                log.error(String.format(
                        "Cannot Insert \"Instruction\"  \"%s\"\nCannot be saved!\nError: %s",
                        ARConstants.ELSEIF, e.getMessage()));
            }
        }
    }

    private void excelFileBlock(String sessionId, SplitDTO splitDTO) {
        // Ensure JavaFX UI updates are done on the JavaFX Application Thread
        Platform.runLater(() -> {
            arExcelFileScene.initialize(sessionId, splitDTO);
            arExcelFileScene.showModal();
        });
    }

    private void createBlockComponent(SplitDTO blockSplitDTO) {
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

    private void injectBlockComponent(SplitDTO blockSplitDTO) {
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

            errorMessage = performDBEngine.loadCompleteJobs(blockDetailsDTO.getBotJobId());
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
                List<InstructionLoad> blockLoopInstructions =
                        performLists.buildJsonViewData(performLists.getListBotJob());
                jsonData = gson.toJson(blockLoopInstructions);
            }
            webSocketSessionManager.sendMessageJson(
                    blockDetailsDTO.getHomeBankingId(), blockDetailsDTO.getSessionId(), jsonData, "updateInstructions");

        } else {
            performDataBase.deleteBlockDirect("block", blockDetailsDTO.getBotJobId(), newBlockId);
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

    public InstructionLoad hasOnlyExcelGoto(List<InstructionLoad> instructions, String instrTable) {

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

    private SplitDTO parseSplitDTO(JsonObject jsonEntry) {
        if (jsonEntry == null || jsonEntry.isEmpty()) {

            log.warn("parseSplitDTO called with null or empty JSON object");
            return null;
        }

        try {
            return gson.fromJson(jsonEntry, SplitDTO.class);
        } catch (Exception error) {

            log.error("Cannot parse SplitDTO: " + error.getMessage() + " | JSON: " + jsonEntry);
        }
        return null;
    }
}

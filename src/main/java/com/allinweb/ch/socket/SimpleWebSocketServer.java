package com.allinweb.ch.socket;

import com.allinweb.ch.component.model.BlockDetailsDTO;
import com.allinweb.ch.component.model.BlockMoveDTO;
import com.allinweb.ch.component.model.BlockOrderDetailDTO;
import com.allinweb.ch.component.model.BlockSplitDTO;
import com.allinweb.ch.component.model.RowMoveDTO;
import com.allinweb.ch.facade.PerformDBSavedBlock;
import com.allinweb.ch.facade.PerformDataBase;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

@ServerEndpoint("/websocket")
public class SimpleWebSocketServer {

    // Store all connected sessions
    private static final Set<Session> sessions = Collections.synchronizedSet(new HashSet<>());
    private final Gson gson = new Gson();

    private static final PerformDataBase performDataBase;
    private static final PerformDBSavedBlock performDBSavedBlock;
    // Static block to initialize
    static {
        performDataBase = PerformDataBase.getInstance();
        performDBSavedBlock = PerformDBSavedBlock.getInstance();
    }

    @OnOpen
    public void onOpen(Session session) {
        sessions.add(session);
        System.out.println("New connection: Session ID = " + session.getId());
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

            // Process the message based on its type
            switch (type) {
                case "broadcast":
                    String broadcastMessage = jsonMessage.get("body").getAsString();
                    broadcastMessageToAll(broadcastMessage);
                    break;
                case "echo":
                    sendMessageJson(session, "Echo: " + jsonMessage.get("body").getAsString(), null);
                    break;
                default:
                    handleMessageByType(type, jsonMessage, session);
                    break;
            }
        } catch (Exception e) {
            System.err.println("Error processing message: " + e.getMessage());
            if (type != null) {
                sendMessageJson(session, "Action type : \"" + type + "\"", "cannot be processed");
            } else {
                sendMessageJson(session, "Error processing message", "No \"type\" definition");
            }
        }
    }

    private void handleMessageByType(String type, JsonObject jsonEntry, Session session) {
        // Dispatch to the correct method based on the message type
        switch (type) {
            case "RESPONSE_BACK":
                // Extract the "body" field from the JsonObject
                BlockSplitDTO received = gson.fromJson(jsonEntry, BlockSplitDTO.class);
                received.setType("MARTINI");
                String jsonData = gson.toJson(received);
                sendMessageJson(session, jsonData, null);
                break;
                //            case "BLOCKS_COMPONENT":
                //                BlockSplitDTO blockComponentDTO = gson.fromJson(body, BlockSplitDTO.class);
                //                createBlockComponent(blockComponentDTO);
                //                break;
            case "BLOCKS_SPLITTER":
                BlockSplitDTO blockSplitDTO = gson.fromJson(jsonEntry, BlockSplitDTO.class);
                splitBlocks(blockSplitDTO);
                sendMessageJson(session, "Success block splitter", null);
                break;
            case "BLOCK_MOVE":
                BlockMoveDTO blockMoveDTO = gson.fromJson(jsonEntry, BlockMoveDTO.class);
                moveBlock(blockMoveDTO);
                sendMessageJson(session, "Success block move", null);
                break;
            case "ROW_UPDATE":
                RowMoveDTO rowUpdateDTO = gson.fromJson(jsonEntry, RowMoveDTO.class);
                rowUpdate(rowUpdateDTO);
                break;
                //            case "ROW_MOVE":
                //                RowMoveDTO rowMoveDTO = gson.fromJson(body, RowMoveDTO.class);
                //                if (performDataBase.updateMoveRowsOrder(rowMoveDTO.getUpdatedRows())
                //                        && rowMoveDTO.getDeleteBlockId() > -1) {
                //                    performDataBase.deleteBlock(rowMoveDTO.getBotJobId(),
                // rowMoveDTO.getDeleteBlockId());
                //
                //                    performDataBase.updateBlockOrderNumber(
                //                            performDataBase.selectAllBlocks(rowMoveDTO.getBotJobId()), true);
                //                }
                //                sendMessageToAll("ROW_MOVE");
                //                ABRSharedResources.getInstance().changeDbConnection();
                //                break;
                //            case "INSERT_BEFORE":
                //            case "INSERT_AFTER":
                //            case "INSERT_NEW":
                //            case "INSERT_AFTER_ELSEIF":
                //            case "INSERT_BEFORE_ELSEIF":
                //            case "EDIT_OPERATION":
                //                RowMoveDTO insertBeforeDTO = gson.fromJson(body, RowMoveDTO.class);
                //                injectStepAfterOrBefore(insertBeforeDTO);
                //                ABRSharedResources.getInstance().changeDbConnection();
                //                break;
                //            case "BLOCK_EXCEL_FILE":
                //                BlockDetailsDTO blockExcelDTO = gson.fromJson(body, BlockDetailsDTO.class);
                //                excelFileBlock(blockExcelDTO);
                //                ABRSharedResources.getInstance().changeDbConnection();
                //                break;
                //            case "BLOCK_ORDER":
                //                BlockOrderDTO blockReorder = gson.fromJson(body, BlockOrderDTO.class);
                //                if (blockReorder.getUpdatedBlocks().size() > 0) {
                //                    performDataBase.updateBlockOrderNumber(
                //                            performDataBase.selectAllBlocks(
                //                                    blockReorder.getUpdatedBlocks().get(0).getBotJobId()),
                //                            true);
                //                    performDataBase.deleteNullBlocks(
                //                            blockReorder.getUpdatedBlocks().get(0).getBotJobId());
                //
                //                    ABRSharedResources.getInstance().changeDbConnection();
                //                }
                //                break;
                //            case "INSTRUCTION_STATUS":
                //                InstructionDTO instructionDTO = gson.fromJson(body, InstructionDTO.class);
                //                performDataBase.updateInstructionStatus(instructionDTO);
                //
                //                ABRSharedResources.getInstance().changeDbConnection();
                //                break;
                //            case "BLOCK_STATUS":
                //                RowMoveDTO blockStateDTO = gson.fromJson(body, RowMoveDTO.class);
                //                performDataBase.updateBlockStatus(
                //                        blockStateDTO.getBotJobId(),
                //                        blockStateDTO.getBlockId(),
                //                        blockStateDTO.getBlockName(),
                //                        blockStateDTO.getBlockActive(),
                //                        3); // Block wait time Default 3 seconds per block
                //
                //                performDataBase.updateInstructionStatusByBlock(
                //                        blockStateDTO.getBotJobId(), blockStateDTO.getBlockId(),
                // blockStateDTO.getBlockActive());
                //
                //                ABRSharedResources.getInstance().changeDbConnection();
                //
                //                break;
                //            case "BLOCK_UPDATE":
                //                RowMoveDTO blockUpdateDTO = gson.fromJson(body, RowMoveDTO.class);
                //                performDataBase.updateBlockName(
                //                        blockUpdateDTO.getBotJobId(), blockUpdateDTO.getBlockId(),
                // blockUpdateDTO.getBlockName());
                //                ABRSharedResources.getInstance().changeDbConnection();
                //
                //                break;
                //            case "DELETE_INSTRUCTION":
                //                InstructionDTO deleteInstructionDTO = gson.fromJson(body, InstructionDTO.class);
                //                performDataBase.deleteInstruction(deleteInstructionDTO.getBotJobId(),
                // deleteInstructionDTO);
                //
                //                List<InstructionDTO> rowList = performDataBase.getInstructionsByBlockId(
                //                        deleteInstructionDTO.getBotJobId(), deleteInstructionDTO.getBlockId());
                //                performDataBase.reorderInstructions(rowList);
                //                ABRSharedResources.getInstance().changeDbConnection();
                //                break;
                //            case "DELETE_BLOCK":
                //                DeleteBlockDTO deleteBlockDTO = gson.fromJson(body, DeleteBlockDTO.class);
                //                performDataBase.deleteBlock(deleteBlockDTO);
                //                ABRSharedResources.getInstance().changeDbConnection();
                //                sendMessageToAll("deleteBlock");
                //                break;
                //            case "BLOCK_ROLLBACK":
                //                RollBackBlocksDTO rollBackBlocksDTO = gson.fromJson(body, RollBackBlocksDTO.class);
                //                performDataBase.rollBackBlocksRows(rollBackBlocksDTO);
                //                performDataBase.deleteNullBlocks(rollBackBlocksDTO.getBotJobId());
                //                ABRSharedResources.getInstance().changeDbConnection();
                //                break;

            default:
                sendMessageJson(session, "Action type : \"" + type + "\"", "cannot be processed");
                break;
        }
    }

    @OnClose
    public void onClose(Session session) {
        sessions.remove(session);
        System.out.println("Connection closed: Session ID = " + session.getId());
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
        synchronized (sessions) {
            for (Session session : sessions) {
                if (session.isOpen()) {
                    sendMessageJson(session, message, null);
                }
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

    private void sendMessageJson(Session session, String msg1, String msg2) {
        try {
            // Create a JSON object with the key "body" and the provided message
            JsonObject jsonMessage = new JsonObject();
            jsonMessage.addProperty("body", msg1);
            jsonMessage.addProperty("footer", msg2);

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
}

package com.allinweb.ch.socket;

import com.allinweb.ch.component.model.BlockDetailsDTO;
import com.allinweb.ch.component.model.BlockLoadDTO;
import com.allinweb.ch.component.model.BlockLoopInstructionLoadDTO;
import com.allinweb.ch.component.model.BlockMoveDTO;
import com.allinweb.ch.component.model.BlockOrderDTO;
import com.allinweb.ch.component.model.BlockOrderDetailDTO;
import com.allinweb.ch.component.model.BlockSplitDTO;
import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.model.DeleteBlockDTO;
import com.allinweb.ch.component.model.InstructionDTO;
import com.allinweb.ch.component.model.InstructionReferenceLoadDTO;
import com.allinweb.ch.component.model.RollBackBlocksDTO;
import com.allinweb.ch.component.model.RowMoveDTO;
import com.allinweb.ch.component.pane.ABRViewBotJobPane;
import com.allinweb.ch.component.scene.ABRAlertScene;
import com.allinweb.ch.component.scene.ABRNewCommandScene;
import com.allinweb.ch.component.scene.ABRSaveBlockScene;
import com.allinweb.ch.core.ABRSharedResources;
import com.allinweb.ch.driver.ABRWebDriver;
import com.allinweb.ch.persistence.BlockDTO;
import com.allinweb.ch.persistence.BlockLoopInstructionDTO;
import com.allinweb.ch.persistence.BotJobDTO;
import com.allinweb.ch.persistence.SavedBlockLoopInstructionDTO;
import com.allinweb.ch.persistence.SavedBlocksDTO;
import com.allinweb.ch.util.ABRConstants;
import com.allinweb.ch.util.ABRLogger;
import com.allinweb.ch.util.ComboBoxVars;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

@ServerEndpoint(
        value = "/websocket",
        subprotocols = {"v12.stomp", "v11.stomp", "v10.stomp"}, // Supported STOMP subprotocols
        configurator = StompConfigurator.class // Use the custom configurator
        )
public class WebSocketStompServer {

    // Store all connected sessions
    public static Set<Session> sessions = Collections.synchronizedSet(new HashSet<>());

    private StompHandler stompHandler = new StompHandler();
    private Gson gson = new GsonBuilder().setPrettyPrinting().create(); // Initialize Gson
    private List<BotJobLoadDTO> botLoadJobs = new ArrayList<>();
    private ObservableList<ComboBoxVars> webPageItems = FXCollections.observableArrayList();

    @OnOpen
    public void onOpen(Session session) {
        // Check if the session is already in the sessions set before adding it
        if (!sessions.contains(session)) {
            sessions.add(session);
            ABRLogger.getInstance(ABRWebDriver.class)
                    .info(String.format("Open Socket Connection - Session Id: %s", session.getId()));
        } else {
            ABRLogger.getInstance(ABRWebDriver.class)
                    .info(String.format("Reusing existing Socket Connection - Session Id: %s", session.getId()));
        }
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        try {
            // Parse the incoming STOMP frame
            StompFrame frame = StompParser.parse(message);

            // Handle the STOMP frame (e.g., CONNECT, SEND, SUBSCRIBE)
            stompHandler.handleFrame(frame, session);

            // Extract and handle the message type
            String type = extractType(frame.getBody());
            handleMessageByType(type, frame.getBody(), session);

            // Send a ping to keep the connection alive
            session.getAsyncRemote().sendPing(ByteBuffer.wrap(new byte[0]));
        } catch (IOException e) {
            ABRLogger.getInstance(ABRWebDriver.class)
                    .warning(String.format("onMessage - IO Error: %s", e.getMessage()));
        } catch (Exception e) {
            ABRLogger.getInstance(ABRWebDriver.class).warning(String.format("onMessage - Error: %s", e.getMessage()));
        }
    }

    public static void sendMessageToAll(String message) {
        synchronized (sessions) {
            for (Session session : sessions) {
                if (session.isOpen()) {
                    try {
                        String stompMessage = "MESSAGE\nsubscription:/topic/messages\ncontent-length:"
                                + message.length() + "\n\n" + message + "\u0000";
                        session.getBasicRemote().sendText(stompMessage);
                        ABRLogger.getInstance(ABRWebDriver.class)
                                .info(String.format("Sent message to session %s: %s", session.getId(), message));
                    } catch (IOException e) {
                        ABRLogger.getInstance(ABRWebDriver.class)
                                .warning(String.format("sendMessageToAll - IO Error: %s", e.getMessage()));
                    }
                }
            }
        }
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        ABRLogger.getInstance(ABRWebDriver.class).warning(String.format("WebSocket error: %s", throwable.getMessage()));
        try {
            if (session.isOpen()) {
                session.close();
            }
        } catch (IOException e) {
            ABRLogger.getInstance(ABRWebDriver.class).warning(String.format("onError - IO Error: %s", e.getMessage()));
        }
    }

    @OnClose
    public void onClose(Session session) {
        sessions.remove(session);
        ABRLogger.getInstance(ABRWebDriver.class).info(String.format("Client disconnected: %s", session.getId()));
    }

    private void handleMessageByType(String type, String body, Session session) {
        // Dispatch to the correct method based on the message type
        switch (type) {
            case "BLOCKS_COMPONENT":
                BlockSplitDTO blockComponentDTO = gson.fromJson(body, BlockSplitDTO.class);
                createBlockComponent(blockComponentDTO);
                break;
            case "BLOCKS_SPLITTER":
                BlockSplitDTO blockSplitDTO = gson.fromJson(body, BlockSplitDTO.class);
                splitBlocks(blockSplitDTO);
                ABRSharedResources.getInstance().changeDbConnection();
                break;
            case "BLOCK_MOVE":
                BlockMoveDTO blockMoveDTO = gson.fromJson(body, BlockMoveDTO.class);
                moveBlock(blockMoveDTO);
                ABRSharedResources.getInstance().changeDbConnection();
                break;
            case "ROW_UPDATE":
                RowMoveDTO rowUpdateDTO = gson.fromJson(body, RowMoveDTO.class);
                rowUpdate(rowUpdateDTO);
                ABRSharedResources.getInstance().changeDbConnection();
                break;
            case "ROW_MOVE":
                RowMoveDTO rowMoveDTO = gson.fromJson(body, RowMoveDTO.class);
                rowMove(rowMoveDTO);
                ABRSharedResources.getInstance().changeDbConnection();
                break;
            case "INSERT_BEFORE":
            case "INSERT_AFTER":
                RowMoveDTO insertBeforeDTO = gson.fromJson(body, RowMoveDTO.class);
                injectStepAfterOrBefore(insertBeforeDTO);
                ABRSharedResources.getInstance().changeDbConnection();
                break;
            case "BLOCK_ORDER":
                BlockOrderDTO blockReorder = gson.fromJson(body, BlockOrderDTO.class);
                if (blockReorder.getUpdatedBlocks().size() > 0) {
                    updateBlockOrderNumber(
                            selectAllBlocks(
                                    blockReorder.getUpdatedBlocks().get(0).getBotJobId()),
                            true);
                    deleteNullBlocks(blockReorder.getUpdatedBlocks().get(0).getBotJobId());
                    ABRSharedResources.getInstance().changeDbConnection();
                }
                break;
            case "BLOCK_UPDATE":
                RowMoveDTO blockUpdateDTO = gson.fromJson(body, RowMoveDTO.class);
                updateBlockName(
                        blockUpdateDTO.getBotJobId(), blockUpdateDTO.getBlockId(), blockUpdateDTO.getBlockName());
                ABRSharedResources.getInstance().changeDbConnection();
                break;

            case "DELETE_INSTRUCTION":
                InstructionDTO deleteInstructionDTO = gson.fromJson(body, InstructionDTO.class);
                deleteInstruction(deleteInstructionDTO.getBotJobId(), deleteInstructionDTO);

                List<InstructionDTO> rowList =
                        getInstructionsByBlockId(deleteInstructionDTO.getBotJobId(), deleteInstructionDTO.getBlockId());
                reorderInstructions(rowList);
                ABRSharedResources.getInstance().changeDbConnection();
                break;

            case "DELETE_BLOCK":
                DeleteBlockDTO deleteBlockDTO = gson.fromJson(body, DeleteBlockDTO.class);
                deleteBlock(deleteBlockDTO);
                ABRSharedResources.getInstance().changeDbConnection();
                break;
            case "BLOCK_ROLLBACK":
                RollBackBlocksDTO rollBackBlocksDTO = gson.fromJson(body, RollBackBlocksDTO.class);
                rollBackBlocksRows(rollBackBlocksDTO);
                rollBackBlocksOrder(rollBackBlocksDTO);
                deleteNullBlocks(rollBackBlocksDTO.getBotJobId());
                ABRSharedResources.getInstance().changeDbConnection();
                break;

            default:
                //                    System.err.println("Unknown message type: " + type);
                break;
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

    private void createBlockComponent(BlockSplitDTO blockSplitDTO) {
        // Ensure JavaFX UI updates are done on the JavaFX Application Thread
        SavedBlocksDTO savedBlocksDTO = createSavedBlockDTO(blockSplitDTO);

        BlockDTO blockDTO = new BlockDTO();
        blockDTO.setId(blockSplitDTO.getDetails().getNewBlock().getBlockId());
        //        blockDTO.setBotJob(blockSplitDTO.getDetails().getNewBlock().getBotJobId());

        Platform.runLater(() -> {
            ABRSaveBlockScene newSaveBlockScene = new ABRSaveBlockScene(
                    savedBlocksDTO, blockDTO, blockSplitDTO.getDetails().getNewBlock());
            newSaveBlockScene.showModal();
        });
    }

    // Handle BLOCKS_SPLITTED message
    private void splitBlocks(BlockSplitDTO blockSplitDTO) {
        BlockDetailsDTO originalBlock = blockSplitDTO.getDetails().getOriginalBlock();
        BlockDetailsDTO newBlock = blockSplitDTO.getDetails().getNewBlock();
        List<BlockOrderDetailDTO> updatedBlock = blockSplitDTO.getDetails().getUpdatedBlocks();
        System.out.println("Original Block ID: " + originalBlock.getBlockId());
        System.out.println("New Block Name: " + newBlock.getBlockName());
        System.out.println("Updated Block: " + updatedBlock.size());

        int newBlockId = createNewBlock(newBlock);
        if (updateInstructionsSplitter(newBlock.getInstructions(), (int) originalBlock.getBlockId(), newBlockId)) {
            if (updatedBlock.size() > 0) {
                updateBlockOrderNumber(selectAllBlocks(updatedBlock.get(0).getBotJobId()), true);
            }
        }

        sendMessageToAll("splitBlocks");
    }

    // Handle BLOCK_MOVE message
    private void moveBlock(BlockMoveDTO blockMoveDTO) {
        List<BlockOrderDetailDTO> updatedBlocks = blockMoveDTO.getUpdatedBlocks();
        updateBlockOrderNumber(updatedBlocks, false);
    }

    // Handle ROW_MOVE message
    private void rowUpdate(RowMoveDTO rowUpdateDTO) {
        if (rowUpdateDTO.getUpdatedRows().size() > 0) {
            rowsUpdateName(rowUpdateDTO.getUpdatedRows());
        }

        // Add business logic to handle ROW_MOVE
    }

    // Handle ROW_MOVE message
    private void rowMove(RowMoveDTO rowMoveDTO) {

        updateMoveRowsOrder(rowMoveDTO.getUpdatedRows());

        // Add business logic to handle ROW_MOVE
    }

    private void injectStepAfterOrBefore(RowMoveDTO rowMoveDTO) {

        if (rowMoveDTO.getUpdatedRows().size() > 0) {

            loadBlockAll(rowMoveDTO.getBotJobId());

            loadWebPageFields(rowMoveDTO.getBotJobId());

            // Ensure JavaFX UI updates are done on the JavaFX Application Thread
            Platform.runLater(() -> {
                ABRNewCommandScene newCommandScene = new ABRNewCommandScene(
                        rowMoveDTO, this.botLoadJobs.get(0).getBlockLoadDTOList(), this.webPageItems);
                newCommandScene.showModal();
            });
        }
    }

    public static List<BlockOrderDetailDTO> selectAllBlocks(int botJobId) {
        List<BlockOrderDetailDTO> blockOrderDetails = new ArrayList<>();
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {

            // Select blocks based on botJobId, ordered by block_order_number ASC
            String selectSQL =
                    "SELECT id FROM block WHERE bot_job_id = " + botJobId + " ORDER BY block_order_number ASC";
            ResultSet rs = stmt.executeQuery(selectSQL);

            int newOrderNumber = 1;
            // Iterate through the result set and build BlockOrderDetailDTO list
            while (rs.next()) {
                int blockId = rs.getInt("id");

                // Create a BlockOrderDetailDTO object with blockId and the new order number
                BlockOrderDetailDTO blockDetail = BlockOrderDetailDTO.builder()
                        .blockId(blockId)
                        .botJobId(botJobId)
                        .blockOrderNumber(newOrderNumber)
                        .build();

                // Add the block detail to the list
                blockOrderDetails.add(blockDetail);

                // Increment the order number for the next block
                newOrderNumber++;
            }

        } catch (SQLException e) {
            ABRLogger.getInstance(ABRWebDriver.class)
                    .severe(String.format(
                            "Error selecting blocks for botJobId ID %d. Error: %s", botJobId, e.getMessage()));
        }
        return blockOrderDetails;
    }

    // Handle DELETE_BLOCK message
    public static void updateBlockOrderNumber(List<BlockOrderDetailDTO> blockOrderDetailDTOList, boolean reorderAll) {
        // Sort the blockOrderDetailDTOList based on the previous blockOrderNumber in ascending order
        blockOrderDetailDTOList.sort(Comparator.comparingInt(BlockOrderDetailDTO::getBlockOrderNumber));

        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {
            int newOrderNumber = 1; // Start reordering from 1

            for (BlockOrderDetailDTO blockOrderDetailDTO : blockOrderDetailDTOList) {
                // Update each block's block_order_number starting from 1
                String updateSQL = "UPDATE block SET block_order_number = "
                        + (reorderAll ? newOrderNumber : blockOrderDetailDTO.getBlockOrderNumber())
                        + " WHERE id = "
                        + blockOrderDetailDTO.getBlockId()
                        + " AND bot_job_id = " + blockOrderDetailDTO.getBotJobId();

                int rowsAffected = stmt.executeUpdate(updateSQL);

                if (rowsAffected > 0) {
                    ABRLogger.getInstance(ABRWebDriver.class)
                            .info(String.format(
                                    "Block Order Number updated blockId: %s, newBlockOrderNumber: %s",
                                    blockOrderDetailDTO.getBlockId(), newOrderNumber));
                } else {
                    ABRLogger.getInstance(ABRWebDriver.class)
                            .warning(String.format(
                                    "UpdateBlockOrderNumber - No matching record found to update botJobId: %d blockId: %d",
                                    blockOrderDetailDTO.getBotJobId(), blockOrderDetailDTO.getBlockId()));
                }

                newOrderNumber++; // Increment the new order number for the next block
            }

            sendMessageToAll("updateBlockOrderNumber");
        } catch (SQLException e) {
            ABRLogger.getInstance(ABRWebDriver.class)
                    .severe(String.format("Error UpdateBlockOrderNumber. Error: %s", e.getMessage()));
        }
    }

    // Handle BLOCK_UPDATE message
    private void updateBlockName(int botJobId, int blockId, String blockName) {
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {

            // Update each block's block_order_number starting from 1
            String updateSQL = "UPDATE block SET name = '" + blockName + "'"
                    + " WHERE id = " + blockId
                    + " and bot_job_id = " + botJobId;

            int rowsAffected = stmt.executeUpdate(updateSQL);

            if (rowsAffected > 0) {
                ABRLogger.getInstance(ABRWebDriver.class)
                        .info(String.format("Block Name updated blockId: %s, name: %s", blockId, blockName));
            } else {
                ABRLogger.getInstance(ABRWebDriver.class)
                        .warning(String.format(
                                "UpdateBlockOrderName - No matching record found to update botJobId: %d blockId: %d",
                                botJobId, blockId));
            }

            sendMessageToAll("updateBlockOrderNumber");
        } catch (SQLException e) {
            ABRLogger.getInstance(ABRWebDriver.class)
                    .severe(String.format("Error UpdateBlockOrderNumber. Error: %s", e.getMessage()));
        }
    }

    // Handle DELETE_INSTRUCTION message
    public static void deleteInstruction(int botJobId, InstructionDTO deleteInstructionDTO) {
        if (deleteVariable(botJobId, deleteInstructionDTO.getInstructionId()))
            if (deleteReferences(botJobId, deleteInstructionDTO.getInstructionId()))
                if (deleteRow(deleteInstructionDTO)) {
                    deleteNullBlocks(botJobId);
                    updateBlockOrderNumber(selectAllBlocks(deleteInstructionDTO.getBlockId()), true);
                }
    }

    // Handle DELETE_BLOCK message
    private void deleteBlock(DeleteBlockDTO deleteBlockDTO) {
        List<InstructionDTO> deleteList =
                getInstructionsByBlockId(deleteBlockDTO.getBotJobId(), deleteBlockDTO.getBlockId());
        if (deleteList.size() > 0) {
            for (InstructionDTO deleteDTO : deleteList) {
                deleteInstruction(deleteBlockDTO.getBotJobId(), deleteDTO);
                //                updateOtherBlocks()
            }
        }
        deleteBlock((int) deleteBlockDTO.getBotJobId(), (int) deleteBlockDTO.getBlockId());
        //        updateOtherBlocks(deleteBlockDTO.getUpdatedBlockDTO());
        deleteNullBlocks((int) deleteBlockDTO.getBotJobId());
        if (deleteBlockDTO.getUpdatedBlocks().size() > 0) {
            updateBlockOrderNumber(
                    selectAllBlocks(deleteBlockDTO.getUpdatedBlocks().get(0).getBotJobId()), true);
        }

        sendMessageToAll("deleteBlock");
    }

    // Method to create a new BlockDTO entity and save it to the database
    private int createNewBlock(BlockDetailsDTO newBlockDetails) {
        try {
            // Persist the BlockDTO entity using the saveBlock method
            int newBlockId = saveBlock(newBlockDetails, newBlockDetails.getBotJobId());
            if (newBlockId > -1) {
                // Update the Instruction blockId
                return newBlockId;
            }

            sendMessageToAll("Created New Block");

        } catch (Exception e) {
            ABRLogger.getInstance(ABRWebDriver.class)
                    .severe(String.format("createNewBlock - \nError: %s", e.getMessage()));
        }

        return -1;
    }

    // Dummy method to simulate fetching the BotJobDTO (replace with actual logic)
    private BotJobDTO fetchBotJob() {
        // Replace this with actual logic to fetch the associated BotJobDTO
        return new BotJobDTO(); // Example placeholder
    }

    private int saveBlock(BlockDetailsDTO blockDTO, int botJobId) {
        // Generate a Unique-ID for the block
        Integer nextId = loadNextIdBlockData() + 1;
        Integer nextBlockOrder = loadNextBlockOrderNUmber(blockDTO.getBotJobId());

        // Build the SQL insert query
        String insertSQL = "INSERT INTO block(id, block_order_number, description, name, type_id, bot_job_id) VALUES ("
                + nextId + ", "
                + nextBlockOrder + ", " // block_order_number
                + "'" + blockDTO.getBlockName() + " description', " // description
                + "'" + blockDTO.getBlockName() + "', " // name
                + 1 + ", " // type_id
                + botJobId + ")"; // bot_job_id, assuming BotJobDTO has an ID

        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {
            stmt.executeUpdate(insertSQL);
            ABRLogger.getInstance(ABRWebDriver.class)
                    .info(String.format("Block data saved successfully.\n BlockId: %d", nextId));
            return nextId;
        } catch (SQLException e) {
            ABRLogger.getInstance(ABRWebDriver.class).severe(String.format("saveBlock - \nError: %s", e.getMessage()));
            return -1;
        }
    }

    private boolean updateInstructionsSplitter(List<InstructionDTO> instructions, int originalBlockId, int newBlockId) {
        // Build the SQL update statement

        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {
            for (InstructionDTO instruction : instructions) {

                String updateSQL = "UPDATE block_loop_instruction SET  "
                        + " instruction_order_number = " + instruction.getInstructionOrderNumber() + ","
                        + " block_id = " + newBlockId
                        + " WHERE id = " + instruction.getInstructionId()
                        + " and block_id = " + originalBlockId;

                int rowsAffected = stmt.executeUpdate(updateSQL);
                if (rowsAffected > 0) {
                } else {
                    ABRLogger.getInstance(ABRWebDriver.class)
                            .warning(String.format(
                                    "updateInstructionsSplitter - No matching record found to update blockId: ",
                                    originalBlockId));
                }
            }
            return true;
        } catch (SQLException e) {
            ABRLogger.getInstance(ABRWebDriver.class)
                    .severe(String.format(
                            "This '%s' \n cannot be updated.\nError: %s", originalBlockId, e.getMessage()));
        }
        return false;
    }

    private boolean rowsUpdateName(List<InstructionDTO> instructions) {
        // Build the SQL update statement
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {
            for (InstructionDTO instruction : instructions) {

                String updateSQL = "UPDATE block_loop_instruction SET  "
                        + " name = '" + instruction.getInstructionName() + "'"
                        + " WHERE id = " + instruction.getInstructionId()
                        + " and block_id = " + instruction.getBlockId();

                int rowsAffected = stmt.executeUpdate(updateSQL);
                if (rowsAffected > 0) {
                    ABRLogger.getInstance(ABRWebDriver.class)
                            .warning(String.format(
                                    "RowsUpdateName - InstructionId: %s now have name: %s",
                                    instruction.getInstructionId(), instruction.getInstructionName()));
                } else {
                    ABRLogger.getInstance(ABRWebDriver.class)
                            .warning(String.format(
                                    "UpdateMoveRowsOrder - No matching record found to update InstructionId: %d and name: %s",
                                    instruction.getInstructionId(), instruction.getInstructionName()));
                }
            }
            return true;
        } catch (SQLException e) {
            ABRLogger.getInstance(ABRWebDriver.class)
                    .severe(String.format("This Instruction\n cannot be updated.\nError: %s", e.getMessage()));
        }
        return false;
    }

    private boolean updateMoveRowsOrder(List<InstructionDTO> instructions) {
        // Build the SQL update statement
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {
            for (InstructionDTO instruction : instructions) {

                String updateSQL = "UPDATE block_loop_instruction SET  "
                        + " instruction_order_number = " + instruction.getInstructionOrderNumber() + ","
                        + " block_id = " + instruction.getBlockId()
                        + " WHERE id = " + instruction.getInstructionId();

                int rowsAffected = stmt.executeUpdate(updateSQL);
                if (rowsAffected > 0) {
                    ABRLogger.getInstance(ABRWebDriver.class)
                            .warning(String.format(
                                    "UpdateMoveRowsOrder - InstructionId: %s now have order number: %d",
                                    instruction.getInstructionId(), instruction.getInstructionOrderNumber()));
                } else {
                    ABRLogger.getInstance(ABRWebDriver.class)
                            .warning(String.format(
                                    "UpdateMoveRowsOrder - No matching record found to update blockId: %d and InstructionId: $d",
                                    instruction.getBlockId(), instruction.getInstructionId()));
                }
            }

            sendMessageToAll("updateMoveRowsOrder");
            return true;
        } catch (SQLException e) {
            ABRLogger.getInstance(ABRWebDriver.class)
                    .severe(String.format(
                            "This Order Number for Instructions\n cannot be updated.\nError: %s", e.getMessage()));
        }
        return false;
    }

    private void rollBackBlocksRows(RollBackBlocksDTO rollBackBlocksDTO) {
        // Build the SQL update statement

        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {
            for (InstructionDTO instruction : rollBackBlocksDTO.getInstructions()) {

                String updateSQL = "UPDATE block_loop_instruction SET  "
                        + " instruction_order_number = " + instruction.getInstructionOrderNumber() + ","
                        + " block_id = " + rollBackBlocksDTO.getBlockId()
                        + " WHERE id = " + instruction.getInstructionId();

                int rowsAffected = stmt.executeUpdate(updateSQL);
                if (rowsAffected > 0) {
                    ABRLogger.getInstance(ABRWebDriver.class)
                            .warning(String.format(
                                    "RollBackBlocks - InstructionId %d for blockId: %d updated successfully",
                                    instruction.getInstructionId(), rollBackBlocksDTO.getBlockId()));

                } else {
                    ABRLogger.getInstance(ABRWebDriver.class)
                            .warning(String.format(
                                    "RollBackBlocks - No matching record found to update InstructionId %d for blockId: %d",
                                    instruction.getInstructionId(), rollBackBlocksDTO.getBlockId()));
                }
            }
        } catch (SQLException e) {
            ABRLogger.getInstance(ABRWebDriver.class)
                    .severe(String.format(
                            "This BlockId '%d' \n cannot be updated.\nError: %s",
                            rollBackBlocksDTO.getBlockId(), e.getMessage()));
            return;
        }
    }

    private void rollBackBlocksOrder(RollBackBlocksDTO rollBackBlocksDTO) {
        // Build the SQL update statement

        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {

            String updateSQL = "UPDATE block SET  "
                    + " block_order_number = " + 1
                    + " WHERE id = " + rollBackBlocksDTO.getBlockId()
                    + " and bot_job_id = " + rollBackBlocksDTO.getBotJobId();

            int rowsAffected = stmt.executeUpdate(updateSQL);
            if (rowsAffected > 0) {
                ABRLogger.getInstance(ABRWebDriver.class)
                        .warning(String.format(
                                "rollBackBlocksOrder - Block Order Reset for blockId: %d - Name: %s",
                                rollBackBlocksDTO.getBlockId(), rollBackBlocksDTO.getBlockName()));
            } else {
                ABRLogger.getInstance(ABRWebDriver.class)
                        .warning(String.format(
                                "RollBackBlocks - No matching record found to update for blockId: %d - Name: %s",
                                rollBackBlocksDTO.getBlockId(), rollBackBlocksDTO.getBlockName()));
            }
        } catch (SQLException e) {
            ABRLogger.getInstance(ABRWebDriver.class)
                    .severe(String.format(
                            "This BlockId '%d' - Name: %s \n cannot be updated.\nError: %s",
                            rollBackBlocksDTO.getBlockId(), rollBackBlocksDTO.getBlockName(), e.getMessage()));
            return;
        }
    }

    private static boolean deleteVariable(int bot_job_id, int instructionId) {
        // Build the SQL delete statement

        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {

            String deleteSQL = "DELETE FROM variable WHERE "
                    + " block_loop_instruction_id = " + instructionId
                    + " AND bot_job_id = " + bot_job_id;

            // Execute the update statement and check if any rows were affected
            int rowsAffected = stmt.executeUpdate(deleteSQL);
            if (rowsAffected > 0) {
                ABRLogger.getInstance(ABRWebDriver.class)
                        .info(String.format(
                                "Delete Variables for instruction ID %d has been successfully deleted from botJobId %d:",
                                instructionId, bot_job_id));
            } else {
                /*ABRLogger.getInstance(ABRWebDriver.class)
                       .warning(String.format(
                               "No matching record found for instruction ID %d in botJobId %d:",
                               instructionId, bot_job_id));

                */
            }
            return true;

        } catch (SQLException e) {
            ABRLogger.getInstance(ABRWebDriver.class)
                    .severe(String.format(
                            "Error deleting  Variable ID %d from botJobId ID %d. Error: %s: ",
                            instructionId, bot_job_id, e.getMessage()));
        }
        return false;
    }

    private static boolean deleteRow(InstructionDTO deleteInstructionDTO) {
        // Build the SQL delete statement
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {

            int rowsAffected = 0;
            String deleteSQL = "DELETE FROM block_loop_instruction" + " WHERE id = "
                    + deleteInstructionDTO.getInstructionId()
                    + (deleteInstructionDTO.getBlockId() > 0
                            ? " AND block_id = " + deleteInstructionDTO.getBlockId()
                            : " AND block_id IS NULL");

            if (deleteInstructionDTO.getActions().equals("IF")
                    || deleteInstructionDTO.getActions().equals("ELSE")
                    || deleteInstructionDTO.getActions().equals("ENDIF")) {
                rowsAffected += stmt.executeUpdate("DELETE FROM block_loop_instruction  WHERE block_id = "
                        + deleteInstructionDTO.getBlockId() + " AND name = 'IF' OR name = 'ELSE' or name='ENDIF';");
            } else {

                rowsAffected += stmt.executeUpdate(deleteSQL);
            }

            // Execute the update statement and check if any rows were affected
            if (rowsAffected > 0) {
                ABRLogger.getInstance(ABRWebDriver.class)
                        .info(String.format(
                                "The instruction with ID %d has been successfully deleted from block %d.",
                                deleteInstructionDTO.getInstructionId(), deleteInstructionDTO.getBlockId()));
            } else {
                //                ABRLogger.getInstance(ABRWebDriver.class)
                //                        .warning(String.format(
                //                                "No matching record found for instruction ID %d in block %d.",
                // instructionId, blockId));
            }
            return true;

        } catch (SQLException e) {
            ABRLogger.getInstance(ABRWebDriver.class)
                    .severe(String.format(
                            "Error deleting instruction ID %d from block ID %d. Error: %s",
                            deleteInstructionDTO.getInstructionId(),
                            deleteInstructionDTO.getBlockId(),
                            e.getMessage()));
        }
        return false;
    }

    private static boolean deleteReferences(int botJobId, int instructionId) {
        // Build the SQL delete statement
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {

            String deleteSQL =
                    "DELETE FROM instruction_reference" + " WHERE block_loop_instruction_id = " + instructionId;

            // Execute the update statement and check if any rows were affected
            int rowsAffected = stmt.executeUpdate(deleteSQL);
            if (rowsAffected > 0) {
                ABRLogger.getInstance(ABRWebDriver.class)
                        .info(String.format(
                                "Delete References for Instruction ID %d has been successfully deleted from botJobId %d.",
                                instructionId, botJobId));
            } else {
                //                ABRLogger.getInstance(ABRWebDriver.class)
                //                        .warning(String.format(
                //                                "No matching record found for instruction ID %d in block %d.",
                //                                instructionId, botJobId));
            }
            return true;

        } catch (SQLException e) {
            ABRLogger.getInstance(ABRWebDriver.class)
                    .severe(String.format(
                            "Error deleting instruction ID %d from botJobId ID %d. Error: %s",
                            instructionId, botJobId, e.getMessage()));
        }
        return false;
    }

    public static void deleteNullBlocks(int botJobId) {
        // Build the SQL delete statement
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {

            String deleteSQL = "DELETE FROM block b "
                    + "WHERE b.bot_job_id = " + botJobId
                    + " AND b.block_order_number != 1 " // Exclude block with blockOrderNumber = 1
                    + " AND NOT EXISTS ( "
                    + "     SELECT 1 "
                    + "     FROM block_loop_instruction bli "
                    + "     WHERE bli.block_id = b.id);";

            // Execute the update statement and check if any rows were affected
            int rowsAffected = stmt.executeUpdate(deleteSQL);
            if (rowsAffected > 0) {
                ABRLogger.getInstance(ABRWebDriver.class)
                        .info(String.format(
                                "The %d Nulls Blocks successfully deleted from botJobId %d.", rowsAffected, botJobId));
            }

        } catch (SQLException e) {
            ABRLogger.getInstance(ABRWebDriver.class)
                    .severe(String.format(
                            "Error deleting Null Blocks with BotJobId ID %d. Error: %s", botJobId, e.getMessage()));
        }
    }

    public static List<InstructionDTO> getBlockLoopInstructionIdsWithNullBlock(int botJobId) {
        // List to store IDs of block loop instructions where block_id is null
        List<InstructionDTO> instructions = new ArrayList<>();

        // SQL query to select block_loop_instruction IDs where block_id is null
        String selectSQL = "SELECT i.id FROM block_loop_instruction i " + " WHERE i.block_id IS NULL";

        // Try-with-resources to handle the SQL statement and result set
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {
            ResultSet rs = stmt.executeQuery(selectSQL);

            // Iterate through the result set and add each ID to the list
            while (rs.next()) {
                InstructionDTO instructionDTO = new InstructionDTO();
                instructionDTO.setInstructionId(rs.getInt("id"));
                instructionDTO.setBlockId(-1);
                instructions.add(instructionDTO);
            }

        } catch (SQLException e) {
            // Log the error if any SQL exception occurs
            ABRLogger.getInstance(ABRWebDriver.class)
                    .severe(String.format(
                            "Error fetching block loop instruction IDs with null block_id for botJobId %d. Error: %s",
                            botJobId, e.getMessage()));
        }

        // Return the list of block loop instruction IDs
        return instructions;
    }

    private void deleteBlock(int botJobId, int blockId) {
        // Build the SQL delete statement
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {

            String deleteSQL = "DELETE FROM block " + " WHERE id = " + blockId + " and bot_job_id = " + botJobId;

            // Execute the update statement and check if any rows were affected
            int rowsAffected = stmt.executeUpdate(deleteSQL);
            if (rowsAffected > 0) {
                ABRLogger.getInstance(ABRWebDriver.class)
                        .info(String.format(
                                "The Block id %d has been successfully deleted from botJobId %d.", blockId, botJobId));
            } else {
                ABRLogger.getInstance(ABRWebDriver.class)
                        .warning(String.format(
                                "No matching record found for blockId ID %d in botJobId %d.", blockId, botJobId));
            }

        } catch (SQLException e) {
            ABRLogger.getInstance(ABRWebDriver.class)
                    .severe(String.format(
                            "Error deleting BotJobId ID %d from block ID %d. Error: %s",
                            botJobId, blockId, e.getMessage()));
        }
    }

    private Integer loadNextIdBlockData() {
        //        String selectSQL = "SELECT NEXT_VAL fROM homeBankingSeq";
        String selectSQL = "SELECT MAX(ID) AS max_id FROM block";
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(selectSQL)) {
            while (rs.next()) {
                return rs.getInt("max_id");
            }
        } catch (SQLException e) {
            ABRLogger.getInstance(ABRWebDriver.class)
                    .severe(String.format(
                            "loadNextIdBlockData - Error selecting Next Id Block. Error: %s", e.getMessage()));
        }
        return null;
    }

    private Integer loadNextBlockOrderNUmber(int botJobId) {
        //        String selectSQL = "SELECT NEXT_VAL fROM homeBankingSeq";
        String selectSQL = "SELECT MAX(ID) AS max_id FROM block where bot_job_id = " + botJobId;
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(selectSQL)) {
            while (rs.next()) {
                return rs.getInt("max_id");
            }
        } catch (SQLException e) {
            ABRLogger.getInstance(ABRViewBotJobPane.class).severe("loadNextIdBlockData  \nError: " + e.getMessage());
        }
        return null;
    }

    private void loadBlockAll(int botJobId) {
        String query = "SELECT bj.id AS bot_job_id, bj.name AS bot_job_name, "
                + " b.id AS block_id, b.block_order_number, b.name AS block_name, "
                + " b.description AS block_description, b.type_id, "
                + " bli.id AS block_loop_instruction_id, bli.instruction_order_number, "
                + " bli.actions, bli.name AS instruction_name, bli.path, bli.description AS instruction_description, "
                + " bli.optional, bli.block_marked, bli.default_val, bli.action_custom_max_wait_sec, "
                + " bli.on_hold_seconds, bli.encrypted, bli.export_to_abr, "
                + " irl.reference_type, irl.value, "
                + "  bli.operation, bli.parent_id "
                + " FROM bot_job bj "
                + " LEFT JOIN block b ON b.bot_job_id = bj.id "
                + " JOIN block_loop_instruction bli ON bli.block_id = b.id "
                + " LEFT JOIN instruction_reference irl ON irl.block_loop_instruction_id = bli.id "
                + " where bot_job_id = " + botJobId
                + "  ORDER BY bj.id, b.block_order_number, bli.instruction_order_number, irl.id ASC";

        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(query)) {

            Map<Integer, BotJobLoadDTO> botJobMap = new HashMap<>();
            Map<Integer, BlockLoadDTO> blockMap = new HashMap<>();
            Map<Integer, BlockLoopInstructionLoadDTO> instructionMap = new HashMap<>();

            botLoadJobs.clear();

            while (rs.next()) {
                botJobId = rs.getInt("bot_job_id");
                BotJobLoadDTO botJobDTO = botJobMap.get(botJobId);

                if (botJobDTO == null) {
                    botJobDTO = new BotJobLoadDTO();
                    botJobDTO.setId(botJobId);
                    botJobDTO.setName(rs.getString("bot_job_name"));
                    botJobDTO.setBlockLoadDTOList(new ArrayList<>());
                    botJobMap.put(botJobId, botJobDTO);
                    botLoadJobs.add(botJobDTO);
                }

                int blockId = rs.getInt("block_id");
                BlockLoadDTO blockDTO = blockMap.get(blockId);

                if (blockDTO == null) {
                    blockDTO = new BlockLoadDTO();
                    blockDTO.setId(blockId);
                    blockDTO.setBlockOrderNumber(rs.getInt("block_order_number"));
                    blockDTO.setName(rs.getString("block_name"));
                    blockDTO.setDescription(rs.getString("block_description"));
                    blockDTO.setTypeId(rs.getInt("type_id"));
                    blockDTO.setBotJobId(botJobDTO.getId());
                    blockDTO.setBotJobName(botJobDTO.getName());

                    blockDTO.setBlockLoopInstructionLoadDTOS(new ArrayList<>());
                    botJobDTO.getBlockLoadDTOList().add(blockDTO);
                    blockMap.put(blockId, blockDTO);
                }

                int instructionId = rs.getInt("block_loop_instruction_id");
                BlockLoopInstructionLoadDTO instruction = instructionMap.get(instructionId);

                if (instruction == null) {
                    instruction = new BlockLoopInstructionLoadDTO();
                    instruction.setId(instructionId);
                    instruction.setInstructionOrderNumber(rs.getInt("instruction_order_number"));
                    instruction.setActions(rs.getString("actions"));
                    instruction.setName(rs.getString("instruction_name"));
                    instruction.setPath(rs.getString("path"));
                    instruction.setDescription(rs.getString("instruction_description"));
                    instruction.setOptional(rs.getInt("optional"));
                    instruction.setBlockMarked(rs.getBoolean("block_marked"));
                    instruction.setDefault_val(rs.getString("default_val"));
                    instruction.setActionCustomMaxWaitSec(rs.getInt("action_custom_max_wait_sec"));
                    instruction.setOnHoldSeconds(rs.getInt("on_hold_seconds"));
                    instruction.setEncrypted(rs.getInt("encrypted"));
                    instruction.setExportToABR(rs.getInt("export_to_abr"));
                    instruction.setOperation(rs.getString("operation"));
                    instruction.setParentId(rs.getInt("parent_id"));

                    instruction.setInstructionReferenceLoadDTOList(new ArrayList<>());
                    blockDTO.getBlockLoopInstructionLoadDTOS().add(instruction);
                    instructionMap.put(instructionId, instruction);
                }

                String referenceType = rs.getString("reference_type");
                if (referenceType != null) {
                    InstructionReferenceLoadDTO reference = new InstructionReferenceLoadDTO();
                    reference.setReferenceType(referenceType);
                    reference.setValue(rs.getString("value"));
                    instruction.getInstructionReferenceLoadDTOList().add(reference);
                }
            }
        } catch (SQLException e) {
            ABRLogger.getInstance(Thread.class)
                    .severe(String.format("Error loadBlockAll for botJobId %d\nError: %s", botJobId, e.getMessage()));
        }
    }

    private void loadWebPageFields(int botJobId) {
        webPageItems.clear();
        String selectSQL = " SELECT  "
                + "  bj.id AS bot_job_id,  "
                + "  bli.id AS block_loop_instruction_id,  "
                + "  bli.instruction_order_number,  "
                + "  bli.actions,  "
                + "  bli.name AS instruction_name,  "
                + "  bli.path,  "
                + "  bli.operation      "
                + " FROM bot_job bj  "
                + " LEFT JOIN block b ON b.bot_job_id = bj.id  "
                + " JOIN block_loop_instruction bli ON bli.block_id = b.id  "
                + " where bj.id = " + botJobId
                + "   and operation is null  "
                + "  ORDER BY bj.id, b.block_order_number, bli.instruction_order_number ASC;";

        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(selectSQL)) {
            while (rs.next()) {
                int id = rs.getInt("block_loop_instruction_id");
                String name = rs.getString("instruction_name");
                String actions = rs.getString("actions");

                // Filter out "SET", "GET", "CK", adn "H"
                if (actions != null
                        && !actions.equalsIgnoreCase(ABRConstants.SET_VALUE)
                        && !actions.equalsIgnoreCase(ABRConstants.GET_VALUE)
                        && !actions.equalsIgnoreCase(ABRConstants.CHECK_VALUE)
                        && !actions.equalsIgnoreCase(ABRConstants.HOLD)) {
                    webPageItems.add(new ComboBoxVars(name + "(" + id + ")", name, id, -1));
                }
            }
        } catch (SQLException e) {
            ABRLogger.getInstance(ABRWebDriver.class)
                    .severe(String.format(
                            "loadWebPageFields - Error selecting Web Page Fields.\n Error: %s", e.getMessage()));
        }
    }

    private void addInstruction(
            String name, String operation, Integer variableId, Integer parentId, RowMoveDTO rowMoveDTO) {

        // Create and show alert inside Platform.runLater
        Platform.runLater(() -> {
            // Create a label to display the instruction
            Label newInstruction = new Label("\"" + name + "\" -> \"" + operation + "\"");
            newInstruction.setStyle("-fx-font-size: 18px;");

            StackPane stackPane = new StackPane(newInstruction);
            stackPane.setPadding(new Insets(20));

            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, null, ButtonType.YES, ButtonType.NO);
            alert.setHeaderText("Are you sure you want to Add the Instruction to the Bot-Job?");
            alert.getDialogPane().setContent(stackPane);

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.YES) {
                List<BlockLoopInstructionDTO> instructionList = null;
                BotJobDTO botJob =
                        ABRSharedResources.getInstance().getEntityById(BotJobDTO.class, rowMoveDTO.getBotJobId());
                List<BlockDTO> matchingBlocks = null;
                if (rowMoveDTO != null && rowMoveDTO.getUpdatedRows().size() > 0) {
                    int targetBlockId = rowMoveDTO.getUpdatedRows().get(0).getBlockId();

                    matchingBlocks = botJob.getBlocks().stream()
                            .filter(block -> block.getId() == targetBlockId)
                            .collect(Collectors.toList());

                    if (!matchingBlocks.isEmpty()) {
                        instructionList = matchingBlocks.get(0).getBlockLoopInstructions();
                    } else {
                        instructionList = botJob.getBlocks().get(0).getBlockLoopInstructions();
                    }
                }

                List<BlockLoopInstructionDTO> finalInstructionList = instructionList;
                List<BlockDTO> finalMatchingBlocks = matchingBlocks;

                Task<Void> waitTask = new Task<>() {
                    @Override
                    protected Void call() throws Exception {
                        try {
                            BlockLoopInstructionDTO instruction = new BlockLoopInstructionDTO();
                            instruction.setName(name);
                            instruction.setDescription("loop desc");
                            instruction.setOperation(operation);
                            instruction.setVariableId(variableId);
                            instruction.setParentId(parentId);
                            instruction.setEncrypted(false);
                            instruction.setExportToABR(true);
                            if (rowMoveDTO != null
                                    && rowMoveDTO.getUpdatedRows().size() > 0) {
                                instruction.setInstructionOrderNumber(
                                        rowMoveDTO.getUpdatedRows().get(0).getInstructionOrderNumber());
                            } else {
                                instruction.setInstructionOrderNumber(finalInstructionList.size());
                            }
                            instruction.setOptional(false);
                            if (name.equalsIgnoreCase("setValue")) {
                                instruction.setActions(ABRConstants.SET_VALUE);
                            } else if (name.equalsIgnoreCase("getValue")) {
                                instruction.setActions(ABRConstants.GET_VALUE);
                            } else if (name.equalsIgnoreCase("check")) {
                                instruction.setActions(ABRConstants.CHECK_VALUE);
                            } else if (name.equalsIgnoreCase("ExcelWrite")) {
                                instruction.setActions(ABRConstants.EXTRACT_FIELD);
                            } else if (name.equalsIgnoreCase("GoTo")) {
                                instruction.setActions(ABRConstants.GOTO);
                            } else if (name.equalsIgnoreCase("IF")) {
                                instruction.setActions(ABRConstants.IF);
                            }
                            instruction.setActionCustomMaxWaitSec(30);
                            instruction.setOnHoldSeconds(1);
                            if (finalMatchingBlocks != null) {
                                instruction.setBlock(finalMatchingBlocks.get(0));
                            } else {
                                instruction.setBlock(botJob.getBlocks().get(0));
                            }
                            instruction.setExportToABR(false);

                            // Wrap the persistence in a try-catch block
                            try {
                                ABRSharedResources.getInstance().addEntity(instruction, BlockLoopInstructionDTO.class);
                            } catch (Exception e) {
                                System.err.println("Error while saving instruction: " + e.getMessage());
                                e.printStackTrace();
                            }

                            // Move the UI update to the JavaFX Application Thread
                            Platform.runLater(() -> {
                                new ABRAlertScene(
                                        Alert.AlertType.INFORMATION,
                                        "Instruction Added",
                                        "Instruction " + instruction.getName() + " has been added successfully",
                                        ButtonType.OK);
                            });
                        } catch (Exception ex) {
                            ex.printStackTrace(); // Handle any exception
                        }
                        return null;
                    }
                };
                new Thread(waitTask).start();
            }
        });
    }

    public List<InstructionDTO> getInstructionsByBlockId(int botJobId, int blockId) {
        // List to store the fetched instructions
        List<InstructionDTO> instructions = new ArrayList<>();

        // Build the SQL query statement
        String querySQL = "SELECT * FROM block_loop_instruction WHERE block_id = " + blockId
                + " order by instruction_order_number ASC";

        // Execute the query and process the result set
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(querySQL)) {

            while (rs.next()) {
                // Assuming you have an Instruction class, populate it with data from the ResultSet
                InstructionDTO instruction = new InstructionDTO();
                instruction.setInstructionId(rs.getInt("id"));
                instruction.setInstructionName(rs.getString("name"));
                instruction.setInstructionOrderNumber(rs.getInt("instruction_order_number"));
                instruction.setBlockId(rs.getInt("block_id"));
                instruction.setBlockOrderNumber(instruction.getBlockOrderNumber());
                instruction.setBotJobId(botJobId);

                instruction.setActions(rs.getString("actions"));
                instruction.setPath(rs.getString("path"));
                instruction.setDescription(rs.getString("description"));
                instruction.setOptional(rs.getInt("optional"));
                instruction.setActionCustomMaxWaitSec(rs.getInt("action_custom_max_wait_sec"));
                instruction.setOnHoldSeconds(rs.getInt("on_hold_seconds"));
                instruction.setEncrypted(rs.getInt("encrypted"));
                instruction.setExportToABR(rs.getInt("export_to_abr"));

                // Add the instruction to the list
                instructions.add(instruction);
            }

            ABRLogger.getInstance(ABRWebDriver.class)
                    .info(String.format("Fetched %d instructions for Block ID %d:", instructions.size(), blockId));

        } catch (SQLException e) {
            ABRLogger.getInstance(ABRWebDriver.class)
                    .severe(String.format(
                            "Error fetching instructions for Block ID %d. Error: %s: ", blockId, e.getMessage()));
        }

        return instructions;
    }

    public boolean reorderInstructions(List<InstructionDTO> rowList) {
        int orderNumber = 1;

        // Iterate through the list and update the instructionOrderNumber
        for (InstructionDTO instruction : rowList) {
            instruction.setInstructionOrderNumber(orderNumber);
            orderNumber++; // Increment the order number for the next instruction
        }

        // Build the SQL update statement
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {
            // Loop through each instruction in the rowList
            for (InstructionDTO instruction : rowList) {
                // Increment the instructionOrderNumber by 1 for each instruction
                String updateSQL = "UPDATE block_loop_instruction SET  "
                        + " instruction_order_number = " + instruction.getInstructionOrderNumber()
                        + " WHERE id = " + instruction.getInstructionId()
                        + " AND block_id = " + instruction.getBlockId();

                int rowsAffected = stmt.executeUpdate(updateSQL);
                if (rowsAffected > 0) {
                    ABRLogger.getInstance(ABRWebDriver.class)
                            .warning(String.format(
                                    "preInsertStep - InstructionId: %s in BlockId: %s now has order number: %d",
                                    instruction.getInstructionId(),
                                    instruction.getBlockId(),
                                    instruction.getInstructionOrderNumber() + 1));
                } else {
                    ABRLogger.getInstance(ABRWebDriver.class)
                            .warning(String.format(
                                    "preInsertStep - No matching record found for BlockId: %d and InstructionId: %d",
                                    instruction.getBlockId(), instruction.getInstructionId()));
                }
            }

            return true;
        } catch (SQLException e) {
            ABRLogger.getInstance(ABRWebDriver.class)
                    .severe(String.format("Error updating instruction order numbers.\nError: %s", e.getMessage()));
        }
        return false;
    }

    private SavedBlocksDTO createSavedBlockDTO(BlockSplitDTO blockSplitDTO) {

        List<InstructionDTO> instructions = getInstructionsByBlockId(
                blockSplitDTO.getDetails().getNewBlock().getBotJobId(),
                blockSplitDTO.getDetails().getNewBlock().getBlockId());

        List<SavedBlockLoopInstructionDTO> savedBlockLoopInstructions = new ArrayList<>();

        for (InstructionDTO instructionDTO : instructions) {
            // Create mock SavedBlockLoopInstructionDTO entries
            SavedBlockLoopInstructionDTO instruction = new SavedBlockLoopInstructionDTO();

            instruction.setInstructionOrderNumber(1);
            instruction.setActions(instructionDTO.getActions());
            instruction.setName(instructionDTO.getInstructionName());
            instruction.setPath(instructionDTO.getPath());
            instruction.setDescription(instructionDTO.getDescription());
            instruction.setOptional(instructionDTO.getOptional() == 1 ? true : false);
            instruction.setActionCustomMaxWaitSec(instructionDTO.getActionCustomMaxWaitSec());
            instruction.setOnHoldSeconds(instructionDTO.getOnHoldSeconds());
            instruction.setEncrypted(instructionDTO.getEncrypted() == 1 ? true : false);
            instruction.setExportToABR(instructionDTO.getExportToABR() == 1 ? true : false);

            savedBlockLoopInstructions.add(instruction);
        }

        // Assign mock instructions to mock blocks
        SavedBlocksDTO savedBlock = new SavedBlocksDTO();
        savedBlock.setName("Comp-" + blockSplitDTO.getDetails().getNewBlock().getBlockName());
        savedBlock.setDescription("Component for: ...");
        savedBlock.setTypeId(1);
        savedBlock.setSavedBlockLoopInstructions(savedBlockLoopInstructions);
        return savedBlock;
    }
}

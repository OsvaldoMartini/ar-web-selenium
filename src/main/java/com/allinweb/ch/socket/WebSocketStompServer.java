package com.allinweb.ch.socket;

import com.allinweb.ch.builder.WebElementTagNameEnum;
import com.allinweb.ch.component.model.BlockDetailsDTO;
import com.allinweb.ch.component.model.BlockMoveDTO;
import com.allinweb.ch.component.model.BlockOrderDTO;
import com.allinweb.ch.component.model.BlockOrderDetailDTO;
import com.allinweb.ch.component.model.BlockSplitDTO;
import com.allinweb.ch.component.model.DeleteBlockDTO;
import com.allinweb.ch.component.model.InstructionDTO;
import com.allinweb.ch.component.model.RollBackBlocksDTO;
import com.allinweb.ch.component.model.RowMoveDTO;
import com.allinweb.ch.component.scene.ABRAlertScene;
import com.allinweb.ch.component.scene.ABRNewCommandScene;
import com.allinweb.ch.core.ABRSharedResources;
import com.allinweb.ch.driver.ABRWebDriver;
import com.allinweb.ch.persistence.BlockDTO;
import com.allinweb.ch.persistence.BlockLoopInstructionDTO;
import com.allinweb.ch.persistence.BotJobDTO;
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
import java.util.HashSet;
import java.util.List;
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
    private ObservableList<ComboBoxVars> webPageItems = FXCollections.observableArrayList();

    @OnOpen
    public void onOpen(Session session) {
        sessions.add(session);
        ABRLogger.getInstance(ABRWebDriver.class)
                .info(String.format("Open Socket Connection - Session Id: %s", session.getId()));
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        try {
            // Parse the incoming STOMP frame
            StompFrame frame = StompParser.parse(message);
            // Handle the STOMP frame (e.g., CONNECT, SEND, SUBSCRIBE)
            stompHandler.handleFrame(frame, session);

            // Example: Assume the message is a STOMP SUBSCRIBE frame for a topic
            //            if (message.contains("SUBSCRIBE") && message.contains("/topic/messages")) {
            //                try {
            //                    // Send a STOMP-compatible message to the client
            //                    session.getBasicRemote().sendText("MESSAGE\nsubscription:/topic/messages\n\nHello
            // React! This is a test message from Java.\u0000");
            //                    System.out.println("Sent test message to client " + session.getId());
            //                } catch (IOException e) {
            //                    e.printStackTrace();
            //                }
            //            }

            String type = extractType(frame.getBody());

            // Dispatch to the correct method based on the message type
            switch (type) {
                case "BLOCKS_SPLITTER":
                    BlockSplitDTO blockSplitDTO = gson.fromJson(frame.getBody(), BlockSplitDTO.class);
                    splitBlocks(blockSplitDTO);
                    break;

                case "BLOCK_MOVE":
                    BlockMoveDTO blockMoveDTO = gson.fromJson(frame.getBody(), BlockMoveDTO.class);
                    moveBlock(blockMoveDTO);
                    break;
                case "ROW_UPDATE":
                    RowMoveDTO rowUpdateDTO = gson.fromJson(frame.getBody(), RowMoveDTO.class);
                    rowUpdate(rowUpdateDTO);
                    break;
                case "ROW_MOVE":
                    RowMoveDTO rowMoveDTO = gson.fromJson(frame.getBody(), RowMoveDTO.class);
                    rowMove(rowMoveDTO);
                    break;
                case "INSERT_BEFORE":
                case "INSERT_AFTER":
                    RowMoveDTO insertBeforeDTO = gson.fromJson(frame.getBody(), RowMoveDTO.class);
                    injectStepAfterOrBefore(insertBeforeDTO);
                    break;
                case "BLOCK_ORDER":
                    BlockOrderDTO blockReorder = gson.fromJson(frame.getBody(), BlockOrderDTO.class);
                    if (blockReorder.getUpdatedBlocks().size() > 0) {
                        updateBlockOrderNumber(selectAllBlocks(
                                blockReorder.getUpdatedBlocks().get(0).getBotJobId()));
                        deleteNullBlocks(blockReorder.getUpdatedBlocks().get(0).getBotJobId());
                    }
                    break;

                case "DELETE_INSTRUCTION":
                    InstructionDTO deleteInstructionDTO = gson.fromJson(frame.getBody(), InstructionDTO.class);
                    deleteInstruction(deleteInstructionDTO.getBotJobId(), deleteInstructionDTO);
                    break;

                case "DELETE_BLOCK":
                    DeleteBlockDTO deleteBlockDTO = gson.fromJson(frame.getBody(), DeleteBlockDTO.class);
                    deleteBlock(deleteBlockDTO);
                    break;
                case "BLOCK_ROLLBACK":
                    RollBackBlocksDTO rollBackBlocksDTO = gson.fromJson(frame.getBody(), RollBackBlocksDTO.class);
                    rollBackBlocksRows(rollBackBlocksDTO);
                    rollBackBlocksOrder(rollBackBlocksDTO);
                    deleteNullBlocks(rollBackBlocksDTO.getBotJobId());
                    break;

                default:
                    //                    System.err.println("Unknown message type: " + type);
                    break;
            }

            session.getAsyncRemote().sendPing(ByteBuffer.wrap(new byte[0]));
        } catch (IOException e) {
            ABRLogger.getInstance(ABRWebDriver.class)
                    .warning(String.format("onMessage - IO Error:  %s", e.getMessage()));

        } catch (Exception e) {
            ABRLogger.getInstance(ABRWebDriver.class)
                    .warning((String.format("onMessage - Error:  %s", e.getMessage())));
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
                        System.out.println("Sent message to session " + session.getId());
                    } catch (IOException e) {
                        ABRLogger.getInstance(ABRWebDriver.class)
                                .warning((String.format("sendMessageToAll - IO Error:  %s", e.getMessage())));
                    }
                }
            }
        }
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        // Log the error and attempt to handle it (e.g., reconnect or clean up resources)
        System.err.println("WebSocket error: " + throwable.getMessage());
        throwable.printStackTrace();

        // Attempt to reconnect or clean up session
        try {
            if (session.isOpen()) {
                session.close();
            }
        } catch (IOException e) {
            ABRLogger.getInstance(ABRWebDriver.class).warning(String.format("onError - IO Error:  %s", e.getMessage()));
        }
    }

    @OnClose
    public void onClose(Session session) {
        System.out.println("Client disconnected: " + session.getId());
    }

    // Method to extract the type field from a JSON string
    public static String extractType(String json) {
        try {
            // Parse the JSON into a JsonObject
            JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();

            // Check if the "type" field exists and return its value
            if (jsonObject.has("type")) {
                return jsonObject.get("type").getAsString();
            } else {
                return "Unknown type";
            }
        } catch (Exception e) {
            // Handle the case where the JSON might be malformed
            //            e.printStackTrace();
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

        int newBlockId = createNewBlock(newBlock);
        if (updateInstructionsSplitter(newBlock.getInstructions(), (int) originalBlock.getBlockId(), newBlockId)) {
            if (updatedBlock.size() > 0) {
                updateBlockOrderNumber(selectAllBlocks(updatedBlock.get(0).getBotJobId()));
            }
        }

        sendMessageToAll("splitBlocks");
    }

    // Handle BLOCK_MOVE message
    private void moveBlock(BlockMoveDTO blockMoveDTO) {
        List<BlockOrderDetailDTO> updatedBlocks = blockMoveDTO.getUpdatedBlocks();
        updateBlockOrderNumber(updatedBlocks);
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
            loadWebPageFields(rowMoveDTO.getBotJobId());

            // Ensure JavaFX UI updates are done on the JavaFX Application Thread
            Platform.runLater(() -> {
                ABRNewCommandScene newCommandScene = new ABRNewCommandScene(rowMoveDTO, this.webPageItems);
                newCommandScene.showModal();
            });
        }
    }

    private List<BlockOrderDetailDTO> selectAllBlocks(int botJobId) {
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
    private void updateBlockOrderNumber(List<BlockOrderDetailDTO> blockOrderDetailDTOList) {
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {
            for (BlockOrderDetailDTO blockOrderDetailDTO : blockOrderDetailDTOList) {

                // Update each block's block_order_number starting from 1
                String updateSQL = "UPDATE block SET block_order_number = " + blockOrderDetailDTO.getBlockOrderNumber()
                        + " WHERE id = "
                        + blockOrderDetailDTO.getBlockId()
                        + " and bot_job_id = " + blockOrderDetailDTO.getBotJobId();

                int rowsAffected = stmt.executeUpdate(updateSQL);

                if (rowsAffected > 0) {
                    ABRLogger.getInstance(ABRWebDriver.class)
                            .info(String.format(
                                    "Block Order Number updated blockId: %s, newBlockOrderNumber: %s",
                                    blockOrderDetailDTO.getBlockId(), blockOrderDetailDTO.getBlockOrderNumber()));
                } else {
                    ABRLogger.getInstance(ABRWebDriver.class)
                            .warning(String.format(
                                    "UpdateBlockOrderNumber - No matching record found to update botJobId: %d blockId: %d",
                                    blockOrderDetailDTO.getBotJobId(), blockOrderDetailDTO.getBlockId()));
                }
            }

            sendMessageToAll("updateBlockOrderNumber");
        } catch (SQLException e) {
            ABRLogger.getInstance(ABRWebDriver.class)
                    .severe(String.format("Error UpdateBlockOrderNumber. Error: %s", e.getMessage()));
        }
    }

    // Handle DELETE_INSTRUCTION message
    private void deleteInstruction(int botJobId, InstructionDTO deleteInstructionDTO) {
        if (deleteVariable(botJobId, deleteInstructionDTO.getInstructionId()))
            if (deleteReferences(botJobId, deleteInstructionDTO.getInstructionId()))
                if (deleteRow(deleteInstructionDTO.getBlockId(), (int) deleteInstructionDTO.getInstructionId())) {
                    deleteNullBlocks(botJobId);
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
                    selectAllBlocks(deleteBlockDTO.getUpdatedBlocks().get(0).getBotJobId()));
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

        // Build the SQL insert query
        String insertSQL = "INSERT INTO block(id, block_order_number, description, name, type_id, bot_job_id) VALUES ("
                + nextId + ", "
                + blockDTO.getBlockOrderNumber() + ", " // block_order_number
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
                                    "UpdateMoveRowsOrder - No matching record found to update InstructionId: %d and name: $s",
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
                        + " instruction_order_number = " + instruction.getInstructionOrderNumber()
                        + " WHERE id = " + instruction.getInstructionId()
                        + " and block_id = " + instruction.getBlockId();

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

    private boolean deleteVariable(int bot_job_id, int instructionId) {
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

    private List<InstructionDTO> getInstructionsByBlockId(int botJobId, int blockId) {
        // List to store the fetched instructions
        List<InstructionDTO> instructions = new ArrayList<>();

        // Build the SQL query statement
        String querySQL = "SELECT * FROM block_loop_instruction WHERE block_id = " + blockId;

        // Execute the query and process the result set
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(querySQL)) {

            while (rs.next()) {
                // Assuming you have an Instruction class, populate it with data from the ResultSet
                InstructionDTO instruction = new InstructionDTO();
                instruction.setInstructionId(rs.getInt("id"));
                instruction.setInstructionOrderNumber(rs.getInt("instruction_order_number"));
                instruction.setBlockId(rs.getInt("block_id"));
                instruction.setBlockOrderNumber(instruction.getBlockOrderNumber());
                instruction.setBotJobId(botJobId);
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

    private boolean deleteRow(int blockId, int instructionId) {
        // Build the SQL delete statement
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {

            String deleteSQL = "DELETE FROM block_loop_instruction" + " WHERE id = "
                    + instructionId
                    + " AND block_id = " + blockId;

            // Execute the update statement and check if any rows were affected
            int rowsAffected = stmt.executeUpdate(deleteSQL);
            if (rowsAffected > 0) {
                ABRLogger.getInstance(ABRWebDriver.class)
                        .info(String.format(
                                "The instruction with ID %d has been successfully deleted from block %d.",
                                instructionId, blockId));
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
                            instructionId, blockId, e.getMessage()));
        }
        return false;
    }

    private boolean deleteReferences(int botJobId, int instructionId) {
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

    private void deleteNullBlocks(int botJobId) {
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
                        && !actions.equalsIgnoreCase(WebElementTagNameEnum.SET.getValue())
                        && !actions.equalsIgnoreCase(WebElementTagNameEnum.GET.getValue())
                        && !actions.equalsIgnoreCase(WebElementTagNameEnum.CK.getValue())
                        && !actions.equalsIgnoreCase(ABRConstants.HOLD)) {
                    webPageItems.add(new ComboBoxVars(name, name, id, -1));
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
                                instruction.setActions(ABRConstants.EXTRACT);
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
}

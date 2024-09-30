package com.allinweb.ch.socket;

import com.allinweb.ch.component.model.BlockDetailsDTO;
import com.allinweb.ch.component.model.BlockMoveDTO;
import com.allinweb.ch.component.model.BlockOrderDTO;
import com.allinweb.ch.component.model.BlockOrderDetailDTO;
import com.allinweb.ch.component.model.BlockSplitDTO;
import com.allinweb.ch.component.model.DeleteBlockDTO;
import com.allinweb.ch.component.model.DeleteInstructionDTO;
import com.allinweb.ch.component.model.InstructionDTO;
import com.allinweb.ch.component.model.RollBackBlocksDTO;
import com.allinweb.ch.component.model.RowMoveDTO;
import com.allinweb.ch.component.model.UpdatedBlockDTO;
import com.allinweb.ch.core.ABRSharedResources;
import com.allinweb.ch.driver.ABRWebDriver;
import com.allinweb.ch.persistence.BotJobDTO;
import com.allinweb.ch.util.ABRLogger;
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
import java.util.Set;
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

    @OnOpen
    public void onOpen(Session session) {
        sessions.add(session);
        System.out.println("New connection: " + session.getId());
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

                case "ROW_MOVE":
                    RowMoveDTO rowMoveDTO = gson.fromJson(frame.getBody(), RowMoveDTO.class);
                    rowMove(rowMoveDTO);
                    break;
                case "BLOCK_ORDER":
                    BlockOrderDTO blockReorder = gson.fromJson(frame.getBody(), BlockOrderDTO.class);
                    updateBlockOrderNumber(blockReorder);
                    break;

                case "DELETE_INSTRUCTION":
                    DeleteInstructionDTO deleteInstructionDTO =
                            gson.fromJson(frame.getBody(), DeleteInstructionDTO.class);
                    deleteInstruction(deleteInstructionDTO);
                    break;

                case "DELETE_BLOCK":
                    DeleteBlockDTO deleteBlockDTO = gson.fromJson(frame.getBody(), DeleteBlockDTO.class);
                    deleteBlock(deleteBlockDTO);
                    break;
                case "BLOCK_ROLLBACK":
                    RollBackBlocksDTO rollBackBlocksDTO = gson.fromJson(frame.getBody(), RollBackBlocksDTO.class);
                    rollBackBlocks(rollBackBlocksDTO);
                    deleteNullBlocks(rollBackBlocksDTO.getBotJobId());
                    break;

                default:
                    //                    System.err.println("Unknown message type: " + type);
                    break;
            }

            session.getAsyncRemote().sendPing(ByteBuffer.wrap(new byte[0]));
        } catch (IOException e) {
            e.printStackTrace();
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
                        e.printStackTrace();
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
            e.printStackTrace();
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
        List<UpdatedBlockDTO> updatedBlock = blockSplitDTO.getDetails().getUpdatedBlocks();
        System.out.println("Original Block ID: " + originalBlock.getBlockId());
        System.out.println("New Block Name: " + newBlock.getBlockName());
        System.out.println("Updated Block: " + updatedBlock.size());

        int newBlockId = createNewBlock(newBlock);
        if (updateInstructionsSplitter(newBlock.getInstructions(), (int) originalBlock.getBlockId(), newBlockId)) {
            if (updatedBlock.size() > 0) {
                updateOtherBlocks(updatedBlock);
            }
        }

        sendMessageToAll("splitBlocks");
    }

    // Handle BLOCK_MOVE message
    private void moveBlock(BlockMoveDTO blockMoveDTO) {
        BlockMoveDTO.BlocksDTO blocks = blockMoveDTO.getBlocks();
        BlockMoveDTO.BlockDTO currentBlock = blocks.getCurrentBlock();
        BlockMoveDTO.BlockDTO nextBlock = blocks.getNextBlock(); // or use getPreviousBlock() if needed

        System.out.println("Current Block ID: " + currentBlock.getBlockId());
        if (nextBlock != null) {
            System.out.println("Next Block ID: " + nextBlock.getBlockId());
        }
        // Add business logic to handle BLOCK_MOVE
    }

    // Handle ROW_MOVE message
    private void rowMove(RowMoveDTO rowMoveDTO) {
        RowMoveDTO.RowsDTO rows = rowMoveDTO.getRows();
        RowMoveDTO.RowDTO currentRow = rows.getCurrentRow();
        RowMoveDTO.RowDTO nextRow = rows.getNextRow(); // or use getPreviousRow() if needed

        System.out.println("Current Row Instruction ID: " + currentRow.getInstructionId());
        if (nextRow != null) {
            System.out.println("Next Row Instruction ID: " + nextRow.getInstructionId());
        }
        // Add business logic to handle ROW_MOVE
    }

    // Handle DELETE_BLOCK message
    private void updateBlockOrderNumber(BlockOrderDTO reorderBlockDTO) {
        // Build the SQL update statement
        try {

            try (Statement stmt =
                    ABRSharedResources.getInstance().getConnection().createStatement()) {
                for (BlockOrderDetailDTO block : reorderBlockDTO.getBlocks()) {

                    String updateSQL = "UPDATE block SET  "
                            + " block_order_number = " + block.getBlockOrderNumber()
                            + " WHERE id = " + block.getBlockId();

                    int rowsAffected = stmt.executeUpdate(updateSQL);
                    if (rowsAffected > 0) {
                        ABRLogger.getInstance(ABRWebDriver.class)
                                .info(String.format(
                                        "Block Order Number updated blockId: %s  blockOrderNumber: %s",
                                        block.getBlockId(), block.getBlockOrderNumber()));
                    } else {
                        ABRLogger.getInstance(ABRWebDriver.class)
                                .warning(String.format(
                                        "UpdateBlockOrderNumber - No matching record found to update blockId: ",
                                        block.getBlockId()));
                    }
                    sendMessageToAll("deleteBlock");
                }
            } catch (SQLException e) {
                ABRLogger.getInstance(ABRWebDriver.class)
                        .severe(String.format(
                                "This '%s' \n cannot be updated.\nError: %s",
                                reorderBlockDTO.getType(), e.getMessage()));
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid ID format.");
            ABRLogger.getInstance(ABRWebDriver.class).severe("Invalid ID format.\nCause: " + e.getMessage());
        }
    }

    // Handle DELETE_INSTRUCTION message
    private void deleteInstruction(DeleteInstructionDTO deleteInstructionDTO) {
        if (deleteVariable((int) deleteInstructionDTO.getBotJobId(), (int) deleteInstructionDTO.getInstructionId()))
            if (deleteReferences(
                    (int) deleteInstructionDTO.getBotJobId(), (int) deleteInstructionDTO.getInstructionId()))
                if (deleteRow((int) deleteInstructionDTO.getBlockId(), (int) deleteInstructionDTO.getInstructionId()))
                    deleteNullBlocks((int) deleteInstructionDTO.getBotJobId());
    }

    // Handle DELETE_BLOCK message
    private void deleteBlock(DeleteBlockDTO deleteBlockDTO) {
        System.out.println("Deleting Block ID: " + deleteBlockDTO.getBlockId());
        List<DeleteInstructionDTO> deleteList =
                getInstructionsByBlockId((int) deleteBlockDTO.getBotJobId(), (int) deleteBlockDTO.getBlockId());
        if (deleteList.size() > 0) {
            for (DeleteInstructionDTO deleteDTO : deleteList) {
                deleteInstruction(deleteDTO);
            }
        } else {

            deleteBlock((int) deleteBlockDTO.getBotJobId(), (int) deleteBlockDTO.getBlockId());
            deleteNullBlocks((int) deleteBlockDTO.getBotJobId());
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
            e.printStackTrace();
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
            System.out.println("Block data saved successfully.");
            return nextId;
        } catch (SQLException e) {
            e.printStackTrace();
            return -1;
        }
    }

    private boolean updateOtherBlocks(List<UpdatedBlockDTO> updatedBlockList) {
        // Build the SQL insert query
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {
            for (UpdatedBlockDTO updatedBlock : updatedBlockList) {

                String updateSQL = "UPDATE block SET  "
                        + " id = " + updatedBlock.getBlockId() + ","
                        + " block_order_number = " + updatedBlock.getBlockOrderNumber()
                        + " WHERE id = " + updatedBlock.getBlockId()
                        + " and bot_job_id = " + updatedBlock.getBotJobId();

                int rowsAffected = stmt.executeUpdate(updateSQL);
                if (rowsAffected > 0) {
                } else {
                    ABRLogger.getInstance(ABRWebDriver.class)
                            .warning("updateOtherBlocks - No matching record found to update blocks Order Numbers");
                }
            }
            return true;
        } catch (SQLException e) {
            ABRLogger.getInstance(ABRWebDriver.class)
                    .severe(String.format(
                            "This Block List (%d) blocks \n cannot be updated.\nError: %s",
                            updatedBlockList.size(), e.getMessage()));
        }
        return false;
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

    private void rollBackBlocks(RollBackBlocksDTO rollBackBlocksDTO) {
        // Build the SQL update statement

        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {
            for (InstructionDTO instruction : rollBackBlocksDTO.getInstructions()) {

                String updateSQL = "UPDATE block_loop_instruction SET  "
                        + " instruction_order_number = " + instruction.getInstructionOrderNumber() + ","
                        + " block_id = " + rollBackBlocksDTO.getBlockId()
                        + " WHERE id = " + instruction.getInstructionId();

                int rowsAffected = stmt.executeUpdate(updateSQL);
                if (rowsAffected > 0) {
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

    private boolean deleteVariable(int bot_job_id, int instructionId) {
        // Build the SQL delete statement

        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {

            String deleteSQL = "DELETE FROM variable WHERE "
                    + " block_loop_instruction_id = " + instructionId
                    + "AND bot_job_id = " + bot_job_id;

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
                            "Error deleting instruction ID %d from botJobId ID %d. Error: %s: ",
                            instructionId, bot_job_id, e.getMessage()));
        }
        return false;
    }

    private List<DeleteInstructionDTO> getInstructionsByBlockId(int botJobId, int blockId) {
        // List to store the fetched instructions
        List<DeleteInstructionDTO> instructions = new ArrayList<>();

        // Build the SQL query statement
        String querySQL = "SELECT * FROM block_loop_instruction WHERE block_id = " + blockId;

        // Execute the query and process the result set
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(querySQL)) {

            while (rs.next()) {
                // Assuming you have an Instruction class, populate it with data from the ResultSet
                DeleteInstructionDTO instruction = new DeleteInstructionDTO();
                instruction.setInstructionId(rs.getInt("id"));
                instruction.setBlockId(rs.getInt("block_id"));
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
                    + " and  NOT EXISTS ( "
                    + "     SELECT 1 "
                    + " FROM public.block_loop_instruction bli "
                    + " WHERE bli.block_id = b.id); ";

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
            e.printStackTrace();
        }
        return null;
    }
}

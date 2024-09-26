package com.allinweb.ch.socket;

import com.allinweb.ch.component.model.BlockDetailsDTO;
import com.allinweb.ch.component.model.BlockMoveDTO;
import com.allinweb.ch.component.model.BlockSplitDTO;
import com.allinweb.ch.component.model.DeleteBlockDTO;
import com.allinweb.ch.component.model.DeleteInstructionDTO;
import com.allinweb.ch.component.model.InstructionDTO;
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
import java.util.List;
import javafx.scene.control.Alert;
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

    private StompHandler stompHandler = new StompHandler();
    private Gson gson = new GsonBuilder().setPrettyPrinting().create(); // Initialize Gson

    @OnOpen
    public void onOpen(Session session) {
        System.out.println("Client connected: " + session.getId());
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        try {
            // Parse the incoming STOMP frame
            StompFrame frame = StompParser.parse(message);
            // Handle the STOMP frame (e.g., CONNECT, SEND, SUBSCRIBE)
            stompHandler.handleFrame(frame, session);

            String type = extractType(frame.getBody());

            // Dispatch to the correct method based on the message type
            switch (type) {
                case "BLOCKS_SPLITTED":
                    BlockSplitDTO blockSplitDTO = gson.fromJson(frame.getBody(), BlockSplitDTO.class);
                    splitBlocks(blockSplitDTO, session);
                    break;

                case "BLOCK_MOVE":
                    BlockMoveDTO blockMoveDTO = gson.fromJson(frame.getBody(), BlockMoveDTO.class);
                    moveBlock(blockMoveDTO, session);
                    break;

                case "ROW_MOVE":
                    RowMoveDTO rowMoveDTO = gson.fromJson(frame.getBody(), RowMoveDTO.class);
                    rowMove(rowMoveDTO, session);
                    break;

                case "DELETE_INSTRUCTION":
                    DeleteInstructionDTO deleteInstructionDTO =
                            gson.fromJson(frame.getBody(), DeleteInstructionDTO.class);
                    deleteInstruction(deleteInstructionDTO, session);
                    break;

                case "DELETE_BLOCK":
                    DeleteBlockDTO deleteBlockDTO = gson.fromJson(frame.getBody(), DeleteBlockDTO.class);
                    deleteBlock(deleteBlockDTO, session);
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
    private void splitBlocks(BlockSplitDTO blockSplitDTO, Session session) {
        BlockDetailsDTO originalBlock = blockSplitDTO.getDetails().getOriginalBlock();
        BlockDetailsDTO newBlock = blockSplitDTO.getDetails().getNewBlock();
        List<UpdatedBlockDTO> updatedBlock = blockSplitDTO.getDetails().getUpdatedBlocks();
        System.out.println("Original Block ID: " + originalBlock.getBlockId());
        System.out.println("New Block Name: " + newBlock.getBlockName());
        System.out.println("Updated Block: " + updatedBlock.size());

        createNewBlock(newBlock, (int) originalBlock.getBlockId());
    }

    // Handle BLOCK_MOVE message
    private void moveBlock(BlockMoveDTO blockMoveDTO, Session session) {
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
    private void rowMove(RowMoveDTO rowMoveDTO, Session session) {
        RowMoveDTO.RowsDTO rows = rowMoveDTO.getRows();
        RowMoveDTO.RowDTO currentRow = rows.getCurrentRow();
        RowMoveDTO.RowDTO nextRow = rows.getNextRow(); // or use getPreviousRow() if needed

        System.out.println("Current Row Instruction ID: " + currentRow.getInstructionId());
        if (nextRow != null) {
            System.out.println("Next Row Instruction ID: " + nextRow.getInstructionId());
        }
        // Add business logic to handle ROW_MOVE
    }

    // Handle DELETE_INSTRUCTION message
    private void deleteInstruction(DeleteInstructionDTO deleteInstructionDTO, Session session) {
        System.out.println("Deleting Instruction ID: " + deleteInstructionDTO.getInstructionId());
        // Add business logic to handle DELETE_INSTRUCTION
    }

    // Handle DELETE_BLOCK message
    private void deleteBlock(DeleteBlockDTO deleteBlockDTO, Session session) {
        System.out.println("Deleting Block ID: " + deleteBlockDTO.getBlockId());
        // Add business logic to handle DELETE_BLOCK
    }

    // Method to create a new BlockDTO entity and save it to the database
    private void createNewBlock(BlockDetailsDTO newBlockDetails, int originaBlockId) {
        try {
            // Assuming you have a method to fetch or associate a BotJobDTO
            //            BotJobDTO associatedBotJob = fetchBotJob();  // Replace with actual method to fetch the
            // relevant BotJobDTO
            //            newBlock.setBotJobDTO(associatedBotJob);

            // Now process instructions (if any)

            // Persist the BlockDTO entity using the saveBlock method
            int newBlockId = saveBlock(newBlockDetails, newBlockDetails.getBotJobId());
            if (newBlockId > -1) {
                // Update the Instruction blockId
                updateBlockLoopInstructions(newBlockDetails.getInstructions(), originaBlockId, newBlockId);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Dummy method to simulate fetching the BotJobDTO (replace with actual logic)
    private BotJobDTO fetchBotJob() {
        // Replace this with actual logic to fetch the associated BotJobDTO
        return new BotJobDTO(); // Example placeholder
    }

    //    // Method to persist the BlockDTO entity using Hibernate's session
    //    private void saveBlock(BlockDTO blockDTO) {
    //        // Get the session
    //        org.hibernate.Session session = ABRSharedResources.getInstance().getSession();
    //
    //        // Start the transaction
    //        session.getTransaction().begin();
    //
    //        // Persist the entity
    //        session.save(blockDTO);
    //
    //        // Commit the transaction
    //        session.getTransaction().commit();
    //    }

    private int saveBlock(BlockDetailsDTO blockDTO, int botJobId) {
        // Generate a Unique-ID for the block
        Integer nextId = loadNextIdData() + 1;

        // Build the SQL insert query
        String insertSQL =
                "INSERT INTO public.block(id, block_order_number, description, name, type_id, bot_job_id) VALUES ("
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

    private void updateBlockLoopInstructions(List<InstructionDTO> instructions, int originalBlockId, int newBlockId) {
        // Build the SQL update statement
        try {

            try (Statement stmt =
                    ABRSharedResources.getInstance().getConnection().createStatement()) {
                for (InstructionDTO instruction : instructions) {

                    String updateSQL = "UPDATE public.block_loop_instruction SET  "
                            + " instruction_order_number = " + instruction.getOrderNumber() + ","
                            + " block_id = " + newBlockId
                            + " WHERE id = " + instruction.getInstructionId()
                            + " and block_id = " + originalBlockId;

                    int rowsAffected = stmt.executeUpdate(updateSQL);
                    if (rowsAffected > 0) {
                    } else {
                        ABRLogger.getInstance(ABRWebDriver.class)
                                .severe( String.format("No matching record found to update blockId: ", originalBlockId));
                    }
                }
            } catch (SQLException e) {
                ABRLogger.getInstance(ABRWebDriver.class)
                        .severe(String.format(
                                "This '%s' \n cannot be updated.\nError: %s", originalBlockId, e.getMessage()));
                return;
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid ID format.");
            ABRLogger.getInstance(ABRWebDriver.class).severe("Invalid ID format.\nCause: " + e.getMessage());
        }
    }

    private Integer loadNextIdData() {
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

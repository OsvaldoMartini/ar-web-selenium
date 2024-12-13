package com.allinweb.ch.facade;

import com.allinweb.ch.component.model.BlockDetailsDTO;
import com.allinweb.ch.component.model.BlockLoadDTO;
import com.allinweb.ch.component.model.BlockLoopInstructionLoadDTO;
import com.allinweb.ch.component.model.BlockOrderDetailDTO;
import com.allinweb.ch.component.model.BlockSplitDTO;
import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.model.DeleteBlockDTO;
import com.allinweb.ch.component.model.InstructionDTO;
import com.allinweb.ch.component.model.InstructionReferenceLoadDTO;
import com.allinweb.ch.component.model.RollBackBlocksDTO;
import com.allinweb.ch.component.model.RowMoveDTO;
import com.allinweb.ch.component.scene.ABRAlertScene;
import com.allinweb.ch.core.ABRSharedResources;
import com.allinweb.ch.persistence.BlockDTO;
import com.allinweb.ch.persistence.BlockLoopInstructionDTO;
import com.allinweb.ch.persistence.BotJobDTO;
import com.allinweb.ch.persistence.SavedBlockLoopInstructionDTO;
import com.allinweb.ch.persistence.SavedBlocksDTO;
import com.allinweb.ch.util.ABRConstants;
import com.allinweb.ch.util.ABRLogger;
import com.allinweb.ch.util.ComboBoxVars;
import java.awt.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import javafx.scene.layout.VBox;
import javax.swing.*;

public class PerformDataBase {
    private List<BlockLoadDTO> blockLoadList = new ArrayList<>();

    private List<BotJobLoadDTO> botLoadJobs = new ArrayList<>();
    private ObservableList<ComboBoxVars> webPageItems = FXCollections.observableArrayList();

    // Static final variable to hold the singleton instance
    protected static final SingletonSupplier<PerformDataBase> instance = () -> new PerformDataBase();

    private static final DateTimeFormatter FORMAT_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    // Private constructor to prevent instantiation
    private PerformDataBase() {
        // Initialize if necessary
    }

    public void initializePerformActions() {}

    // Public method to access the singleton instance
    public static PerformDataBase getInstance() {
        return instance.get();
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

    private static boolean deleteVariable(int bot_job_id, int instructionId) {
        // Build the SQL delete statement

        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {

            String deleteSQL = "DELETE FROM variable WHERE "
                    + " block_loop_instruction_id = " + instructionId
                    + " AND bot_job_id = " + bot_job_id;

            // Execute the update statement and check if any rows were affected
            int rowsAffected = stmt.executeUpdate(deleteSQL);
            if (rowsAffected > 0) {
                ABRLogger.getInstance(PerformDataBase.class)
                        .info(String.format(
                                "Delete Variables for instruction ID %d has been successfully deleted from botJobId %d:",
                                instructionId, bot_job_id));
            } else {
                /*ABRLogger.getInstance(PerformDataBase.class)
                       .warning(String.format(
                               "No matching record found for instruction ID %d in botJobId %d:",
                               instructionId, bot_job_id));

                */
            }
            return true;

        } catch (SQLException e) {
            ABRLogger.getInstance(PerformDataBase.class)
                    .severe(String.format(
                            "Error deleting  Variable ID %d from botJobId ID %d. Error: %s: ",
                            instructionId, bot_job_id, e.getMessage()));
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
                ABRLogger.getInstance(PerformDataBase.class)
                        .info(String.format(
                                "Delete References for Instruction ID %d has been successfully deleted from botJobId %d.",
                                instructionId, botJobId));
            } else {
                //                ABRLogger.getInstance(PerformDataBase.class)
                //                        .warning(String.format(
                //                                "No matching record found for instruction ID %d in block %d.",
                //                                instructionId, botJobId));
            }
            return true;

        } catch (SQLException e) {
            ABRLogger.getInstance(PerformDataBase.class)
                    .severe(String.format(
                            "Error deleting instruction ID %d from botJobId ID %d. Error: %s",
                            instructionId, botJobId, e.getMessage()));
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

                rowsAffected += stmt.executeUpdate("DELETE FROM block_loop_instruction  "
                        + " WHERE "
                        + " block_id = " + deleteInstructionDTO.getBlockId() + " AND parent_id = "
                        + deleteInstructionDTO.getParentId());
            } else {

                rowsAffected += stmt.executeUpdate(deleteSQL);
            }

            // Execute the update statement and check if any rows were affected
            if (rowsAffected > 0) {
                ABRLogger.getInstance(PerformDataBase.class)
                        .info(String.format(
                                "The instruction with ID %d has been successfully deleted from block %d.",
                                deleteInstructionDTO.getInstructionId(), deleteInstructionDTO.getBlockId()));
            } else {
                //                ABRLogger.getInstance(PerformDataBase.class)
                //                        .warning(String.format(
                //                                "No matching record found for instruction ID %d in block %d.",
                // instructionId, blockId));
            }
            return true;

        } catch (SQLException e) {
            ABRLogger.getInstance(PerformDataBase.class)
                    .severe(String.format(
                            "Error deleting instruction ID %d from block ID %d. Error: %s",
                            deleteInstructionDTO.getInstructionId(),
                            deleteInstructionDTO.getBlockId(),
                            e.getMessage()));
        }
        return false;
    }

    public static void deleteNullBlocks(int botJobId) {
        // Build the SQL delete statement
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {

            String deleteSQL = "DELETE FROM block b "
                    + "WHERE b.bot_job_id = " + botJobId
                    //                    + " AND b.block_order_number != 1 " // Exclude block with blockOrderNumber = 1
                    + " AND NOT EXISTS ( "
                    + "     SELECT 1 "
                    + "     FROM block_loop_instruction bli "
                    + "     WHERE bli.block_id = b.id);";

            // Execute the update statement and check if any rows were affected
            int rowsAffected = stmt.executeUpdate(deleteSQL);
            if (rowsAffected > 0) {
                ABRLogger.getInstance(PerformDataBase.class)
                        .info(String.format(
                                "The %d Nulls Blocks successfully deleted from botJobId %d.", rowsAffected, botJobId));
            }

        } catch (SQLException e) {
            ABRLogger.getInstance(PerformDataBase.class)
                    .severe(String.format(
                            "Error deleting Null Blocks with BotJobId ID %d. Error: %s", botJobId, e.getMessage()));
        }
    }

    public static void updateBlockOrderNumber(List<BlockOrderDetailDTO> blockOrderDetailDTOList, boolean reorderAll) {
        //         Sort the blockOrderDetailDTOList based on the previous blockOrderNumber in ascending order
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
                    ABRLogger.getInstance(PerformDataBase.class)
                            .info(String.format(
                                    "Block Order Number updated blockId: %s, newBlockOrderNumber: %s",
                                    blockOrderDetailDTO.getBlockId(), newOrderNumber));
                } else {
                    ABRLogger.getInstance(PerformDataBase.class)
                            .warning(String.format(
                                    "UpdateBlockOrderNumber - No matching record found to update botJobId: %d blockId: %d",
                                    blockOrderDetailDTO.getBotJobId(), blockOrderDetailDTO.getBlockId()));
                }

                newOrderNumber++; // Increment the new order number for the next block
            }

        } catch (SQLException e) {
            ABRLogger.getInstance(PerformDataBase.class)
                    .severe(String.format("Error UpdateBlockOrderNumber. Error: %s", e.getMessage()));
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
            ABRLogger.getInstance(PerformDataBase.class)
                    .severe(String.format(
                            "Error selecting blocks for botJobId ID %d. Error: %s", botJobId, e.getMessage()));
        }
        return blockOrderDetails;
    }

    // Handle BLOCK_UPDATE message
    public void updateBlockName(int botJobId, int blockId, String blockName) {
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {

            // Update each block's block_order_number starting from 1
            String updateSQL = "UPDATE block SET name = '" + blockName + "',"
                    + " description = '" + blockName + "'"
                    + " WHERE id = " + blockId
                    + " and bot_job_id = " + botJobId;

            int rowsAffected = stmt.executeUpdate(updateSQL);

            if (rowsAffected > 0) {
                ABRLogger.getInstance(PerformDataBase.class)
                        .info(String.format("Block Name updated blockId: %s, name: %s", blockId, blockName));
            } else {
                ABRLogger.getInstance(PerformDataBase.class)
                        .warning(String.format(
                                "UpdateBlockOrderName - No matching record found to update botJobId: %d blockId: %d",
                                botJobId, blockId));
            }

        } catch (SQLException e) {
            ABRLogger.getInstance(PerformDataBase.class)
                    .severe(String.format("Error UpdateBlockOrderNumber. Error: %s", e.getMessage()));
        }
    }

    // Handle BLOCK_UPDATE message
    public boolean updateBlockExportFile(int botJobId, int blockId, String expoprtFile) {
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {

            // Update each block's block_order_number starting from 1
            String updateSQL = "UPDATE block SET export_file = '" + expoprtFile + "'"
                    + " WHERE id = " + blockId
                    + " and bot_job_id = " + botJobId;

            int rowsAffected = stmt.executeUpdate(updateSQL);

            if (rowsAffected > 0) {
                ABRLogger.getInstance(PerformDataBase.class)
                        .info(String.format("Block Export File updated blockId: %s, name: %s", blockId, expoprtFile));
            } else {
                ABRLogger.getInstance(PerformDataBase.class)
                        .warning(String.format(
                                "updateBlockExportFile - No matching record found to update botJobId: %d blockId: %d",
                                botJobId, blockId));

                return false;
            }

            return true;

        } catch (SQLException e) {
            ABRLogger.getInstance(PerformDataBase.class)
                    .severe(String.format("Error updateBlockExportFile. Error: %s", e.getMessage()));
        }
        return false;
    }

    // Handle DELETE_BLOCK message
    public boolean deleteBlock(DeleteBlockDTO deleteBlockDTO) {
        boolean blockDeletion = false;
        List<InstructionDTO> deleteList =
                getInstructionsByBlockId(deleteBlockDTO.getBotJobId(), deleteBlockDTO.getBlockId());
        if (deleteList.size() > 0) {
            for (InstructionDTO deleteDTO : deleteList) {
                deleteInstruction(deleteBlockDTO.getBotJobId(), deleteDTO);
                //                updateOtherBlocks()
            }
        }
        blockDeletion = deleteBlock((int) deleteBlockDTO.getBotJobId(), (int) deleteBlockDTO.getBlockId());
        //        updateOtherBlocks(deleteBlockDTO.getUpdatedBlockDTO());
        deleteNullBlocks((int) deleteBlockDTO.getBotJobId());
        if (deleteBlockDTO.getUpdatedBlocks() != null
                && deleteBlockDTO.getUpdatedBlocks().size() > 0) {
            updateBlockOrderNumber(
                    selectAllBlocks(deleteBlockDTO.getUpdatedBlocks().get(0).getBotJobId()), true);
        }

        return blockDeletion;
    }

    // Method to create a new BlockDTO entity and save it to the database
    public int createNewBlock(BlockDetailsDTO newBlockDetails) {
        try {
            // Persist the BlockDTO entity using the saveBlock method
            int newBlockId = saveBlock(newBlockDetails, newBlockDetails.getBotJobId());
            if (newBlockId > -1) {
                // Update the Instruction blockId
                return newBlockId;
            }

        } catch (Exception e) {
            ABRLogger.getInstance(PerformDataBase.class)
                    .severe(String.format("createNewBlock - \nError: %s", e.getMessage()));
        }

        return -1;
    }

    private int saveBlock(BlockDetailsDTO blockDTO, int botJobId) {
        // Generate a Unique-ID for the block
        Integer nextId = loadNextIdBlockData() + 1;
        Integer nextBlockOrder = -1;
        if (blockDTO.isForceOrder()) {
            nextBlockOrder = blockDTO.getBlockOrderNumber();
        } else {
            nextBlockOrder = loadNextBlockOrderNumber(blockDTO.getBotJobId()) + 1;
        }

        if (nextId < 0 || nextBlockOrder < 0) {
            return -1;
        }

        // Build the SQL insert query
        String insertSQL =
                "INSERT INTO block(id, block_order_number, description, name, type_id, active, wait, bot_job_id) VALUES ("
                        + nextId + ", "
                        + nextBlockOrder + ", " // block_order_number
                        + "'" + blockDTO.getBlockName() + " description', " // description
                        + "'" + blockDTO.getBlockName() + "', " // name
                        + 1 + ", " // type_id
                        + 1 + ", " // active
                        + 3 + ", " // wait
                        + botJobId + ")"; // bot_job_id, assuming BotJobDTO has an ID
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {
            stmt.executeUpdate(insertSQL);
            ABRLogger.getInstance(PerformDataBase.class)
                    .info(String.format("Block data saved successfully.\n BlockId: %d", nextId));
            return nextId;
        } catch (SQLException e) {
            ABRLogger.getInstance(PerformDataBase.class)
                    .severe(String.format("saveBlock - \nError: %s", e.getMessage()));
            return -1;
        }
    }

    public boolean updateInstructionsSplitter(List<InstructionDTO> instructions, int originalBlockId, int newBlockId) {
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
                    ABRLogger.getInstance(PerformDataBase.class)
                            .warning(String.format(
                                    "updateInstructionsSplitter - No matching record found to update blockId: ",
                                    originalBlockId));
                }
            }
            return true;
        } catch (SQLException e) {
            ABRLogger.getInstance(PerformDataBase.class)
                    .severe(String.format(
                            "This '%s' \n cannot be updated.\nError: %s", originalBlockId, e.getMessage()));
        }
        return false;
    }

    public boolean rowsUpdateName(List<InstructionDTO> instructions) {
        // Build the SQL update statement
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {
            for (InstructionDTO instruction : instructions) {

                String updateSQL = "UPDATE block_loop_instruction SET  "
                        + " name = '" + instruction.getInstructionName() + "',"
                        + " actions = '" + instruction.getActions() + "'"
                        + " WHERE id = " + instruction.getInstructionId()
                        + " and block_id = " + instruction.getBlockId();

                int rowsAffected = stmt.executeUpdate(updateSQL);
                if (rowsAffected > 0) {
                    ABRLogger.getInstance(PerformDataBase.class)
                            .warning(String.format(
                                    "RowsUpdateName - InstructionId: %s now have name: %s",
                                    instruction.getInstructionId(), instruction.getInstructionName()));
                } else {
                    ABRLogger.getInstance(PerformDataBase.class)
                            .warning(String.format(
                                    "UpdateMoveRowsOrder - No matching record found to update InstructionId: %d and name: %s",
                                    instruction.getInstructionId(), instruction.getInstructionName()));
                }
            }
            return true;
        } catch (SQLException e) {
            ABRLogger.getInstance(PerformDataBase.class)
                    .severe(String.format("This Instruction\n cannot be updated.\nError: %s", e.getMessage()));
        }
        return false;
    }

    public boolean updateMoveRowsOrder(List<InstructionDTO> instructions) {
        // Build the SQL update statement
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {
            for (InstructionDTO instruction : instructions) {

                String updateSQL = "UPDATE block_loop_instruction SET  "
                        + " instruction_order_number = " + instruction.getInstructionOrderNumber() + ","
                        + " block_id = " + instruction.getBlockId()
                        + " WHERE id = " + instruction.getInstructionId();

                int rowsAffected = stmt.executeUpdate(updateSQL);
                if (rowsAffected > 0) {
                    ABRLogger.getInstance(PerformDataBase.class)
                            .warning(String.format(
                                    "UpdateMoveRowsOrder - InstructionId: %s now have order number: %d",
                                    instruction.getInstructionId(), instruction.getInstructionOrderNumber()));
                } else {
                    ABRLogger.getInstance(PerformDataBase.class)
                            .warning(String.format(
                                    "UpdateMoveRowsOrder - No matching record found to update blockId: %d and InstructionId: $d",
                                    instruction.getBlockId(), instruction.getInstructionId()));
                }
            }

            return true;
        } catch (SQLException e) {
            ABRLogger.getInstance(PerformDataBase.class)
                    .severe(String.format(
                            "This Order Number for Instructions\n cannot be updated.\nError: %s", e.getMessage()));
        }
        return false;
    }

    public void rollBackBlocksRows(RollBackBlocksDTO rollBackBlocksDTO) {
        // Build the SQL update statement

        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {
            for (InstructionDTO instruction : rollBackBlocksDTO.getInstructions()) {

                String updateSQL = "UPDATE block_loop_instruction SET  "
                        + " instruction_order_number = " + instruction.getInstructionOrderNumber() + ","
                        + " block_id = " + rollBackBlocksDTO.getBlockId()
                        + " WHERE id = " + instruction.getInstructionId();

                int rowsAffected = stmt.executeUpdate(updateSQL);
                if (rowsAffected > 0) {
                    ABRLogger.getInstance(PerformDataBase.class)
                            .warning(String.format(
                                    "RollBackBlocks - InstructionId %d for blockId: %d updated successfully",
                                    instruction.getInstructionId(), rollBackBlocksDTO.getBlockId()));

                } else {
                    ABRLogger.getInstance(PerformDataBase.class)
                            .warning(String.format(
                                    "RollBackBlocks - No matching record found to update InstructionId %d for blockId: %d",
                                    instruction.getInstructionId(), rollBackBlocksDTO.getBlockId()));
                }
            }
        } catch (SQLException e) {
            ABRLogger.getInstance(PerformDataBase.class)
                    .severe(String.format(
                            "This BlockId '%d' \n cannot be updated.\nError: %s",
                            rollBackBlocksDTO.getBlockId(), e.getMessage()));
            return;
        }
    }

    public void rollBackBlocksOrder(RollBackBlocksDTO rollBackBlocksDTO) {
        // Build the SQL update statement

        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {

            String updateSQL = "UPDATE block SET  "
                    + " block_order_number = " + 1
                    + " WHERE id = " + rollBackBlocksDTO.getBlockId()
                    + " and bot_job_id = " + rollBackBlocksDTO.getBotJobId();

            int rowsAffected = stmt.executeUpdate(updateSQL);
            if (rowsAffected > 0) {
                ABRLogger.getInstance(PerformDataBase.class)
                        .warning(String.format(
                                "rollBackBlocksOrder - Block Order Reset for blockId: %d - Name: %s",
                                rollBackBlocksDTO.getBlockId(), rollBackBlocksDTO.getBlockName()));
            } else {
                ABRLogger.getInstance(PerformDataBase.class)
                        .warning(String.format(
                                "RollBackBlocks - No matching record found to update for blockId: %d - Name: %s",
                                rollBackBlocksDTO.getBlockId(), rollBackBlocksDTO.getBlockName()));
            }
        } catch (SQLException e) {
            ABRLogger.getInstance(PerformDataBase.class)
                    .severe(String.format(
                            "This BlockId '%d' - Name: %s \n cannot be updated.\nError: %s",
                            rollBackBlocksDTO.getBlockId(), rollBackBlocksDTO.getBlockName(), e.getMessage()));
            return;
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
            ABRLogger.getInstance(PerformDataBase.class)
                    .severe(String.format(
                            "Error fetching block loop instruction IDs with null block_id for botJobId %d. Error: %s",
                            botJobId, e.getMessage()));
        }

        // Return the list of block loop instruction IDs
        return instructions;
    }

    public boolean deleteBlock(int botJobId, int blockId) {
        // Build the SQL delete statement
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {

            String deleteSQL = "DELETE FROM block " + " WHERE id = " + blockId + " and bot_job_id = " + botJobId;

            // Execute the update statement and check if any rows were affected
            int rowsAffected = stmt.executeUpdate(deleteSQL);
            if (rowsAffected > 0) {
                ABRLogger.getInstance(PerformDataBase.class)
                        .info(String.format(
                                "The Block id %d has been successfully deleted from botJobId %d.", blockId, botJobId));
            } else {
                ABRLogger.getInstance(PerformDataBase.class)
                        .warning(String.format(
                                "No matching record found for blockId ID %d in botJobId %d.", blockId, botJobId));
            }

            return true;

        } catch (SQLException e) {
            ABRLogger.getInstance(PerformDataBase.class)
                    .severe(String.format(
                            "Error deleting BotJobId ID %d from block ID %d. Error: %s",
                            botJobId, blockId, e.getMessage()));
        }
        return false;
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
            ABRLogger.getInstance(PerformDataBase.class).severe("loadNextIdBlockData  \nError: " + e.getMessage());
        }
        return null;
    }

    public int loadNextBlockOrderNumber(int botJobId) {
        //        String selectSQL = "SELECT NEXT_VAL fROM homeBankingSeq";
        String selectSQL = "SELECT COUNT(*) AS quantity FROM block WHERE bot_job_id = " + botJobId;
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(selectSQL)) {
            while (rs.next()) {
                return rs.getInt("quantity");
            }
        } catch (SQLException e) {
            ABRLogger.getInstance(PerformDataBase.class).severe("loadNextIdBlockData  \nError: " + e.getMessage());
        }
        return -1;
    }

    public List<BotJobLoadDTO> loadBlockAll(int botJobId) {
        String query = "SELECT bj.id AS bot_job_id, bj.name AS bot_job_name, "
                + " b.id AS block_id, b.block_order_number, b.name AS block_name, "
                + " b.description AS block_description, b.type_id, "
                + " bli.id AS block_loop_instruction_id, bli.instruction_order_number, "
                + " bli.actions, bli.name AS instruction_name, bli.path, bli.description AS instruction_description, "
                + " bli.optional, bli.block_marked, bli.default_val, bli.action_custom_max_wait_sec, "
                + " bli.on_hold_seconds, bli.encrypted, bli.export_to_abr, "
                + " irl.reference_type, irl.value, "
                + "  bli.operation, bli.parent_id, "
                + "  b.export_file, "
                + "  b.active, b.wait "
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
                    blockDTO.setActive(rs.getBoolean("active"));
                    blockDTO.setWait(rs.getInt("wait"));
                    blockDTO.setBotJobId(botJobDTO.getId());
                    blockDTO.setBotJobName(botJobDTO.getName());
                    blockDTO.setExportFile(rs.getString("export_file"));

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

        return botLoadJobs;
    }

    //    private void loadBlockAll(int botJobId) {
    //        String query = "SELECT bj.id AS bot_job_id, bj.name AS bot_job_name, "
    //                + " b.id AS block_id, b.block_order_number, b.name AS block_name, "
    //                + " b.description AS block_description, b.type_id, "
    //                + " bli.id AS block_loop_instruction_id, bli.instruction_order_number, "
    //                + " bli.actions, bli.name AS instruction_name, bli.path, bli.description AS
    // instruction_description, "
    //                + " bli.optional, bli.block_marked, bli.default_val, bli.action_custom_max_wait_sec, "
    //                + " bli.on_hold_seconds, bli.encrypted, bli.export_to_abr, "
    //                + " irl.reference_type, irl.value, "
    //                + "  bli.operation, bli.parent_id, "
    //                + "  b.export_file "
    //                + " FROM bot_job bj "
    //                + " LEFT JOIN block b ON b.bot_job_id = bj.id "
    //                + " JOIN block_loop_instruction bli ON bli.block_id = b.id "
    //                + " LEFT JOIN instruction_reference irl ON irl.block_loop_instruction_id = bli.id "
    //                + " where bot_job_id = " + botJobId
    //                + "  ORDER BY bj.id, b.block_order_number, bli.instruction_order_number, irl.id ASC";
    //
    //        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement();
    //             ResultSet rs = stmt.executeQuery(query)) {
    //
    //            Map<Integer, BotJobLoadDTO> botJobMap = new HashMap<>();
    //            Map<Integer, BlockLoadDTO> blockMap = new HashMap<>();
    //            Map<Integer, BlockLoopInstructionLoadDTO> instructionMap = new HashMap<>();
    //
    //            botLoadJobs.clear();
    //
    //            while (rs.next()) {
    //                botJobId = rs.getInt("bot_job_id");
    //                BotJobLoadDTO botJobDTO = botJobMap.get(botJobId);
    //
    //                if (botJobDTO == null) {
    //                    botJobDTO = new BotJobLoadDTO();
    //                    botJobDTO.setId(botJobId);
    //                    botJobDTO.setName(rs.getString("bot_job_name"));
    //                    botJobDTO.setBlockLoadDTOList(new ArrayList<>());
    //                    botJobMap.put(botJobId, botJobDTO);
    //                    botLoadJobs.add(botJobDTO);
    //                }
    //
    //                int blockId = rs.getInt("block_id");
    //                BlockLoadDTO blockDTO = blockMap.get(blockId);
    //
    //                if (blockDTO == null) {
    //                    blockDTO = new BlockLoadDTO();
    //                    blockDTO.setId(blockId);
    //                    blockDTO.setBlockOrderNumber(rs.getInt("block_order_number"));
    //                    blockDTO.setName(rs.getString("block_name"));
    //                    blockDTO.setDescription(rs.getString("block_description"));
    //                    blockDTO.setTypeId(rs.getInt("type_id"));
    //                    blockDTO.setBotJobId(botJobDTO.getId());
    //                    blockDTO.setBotJobName(botJobDTO.getName());
    //                    blockDTO.setExportFile(rs.getString("export_file"));
    //
    //                    blockDTO.setBlockLoopInstructionLoadDTOS(new ArrayList<>());
    //                    botJobDTO.getBlockLoadDTOList().add(blockDTO);
    //                    blockMap.put(blockId, blockDTO);
    //                }
    //
    //                int instructionId = rs.getInt("block_loop_instruction_id");
    //                BlockLoopInstructionLoadDTO instruction = instructionMap.get(instructionId);
    //
    //                if (instruction == null) {
    //                    instruction = new BlockLoopInstructionLoadDTO();
    //                    instruction.setId(instructionId);
    //                    instruction.setInstructionOrderNumber(rs.getInt("instruction_order_number"));
    //                    instruction.setActions(rs.getString("actions"));
    //                    instruction.setName(rs.getString("instruction_name"));
    //                    instruction.setPath(rs.getString("path"));
    //                    instruction.setDescription(rs.getString("instruction_description"));
    //                    instruction.setOptional(rs.getInt("optional"));
    //                    instruction.setBlockMarked(rs.getBoolean("block_marked"));
    //                    instruction.setDefault_val(rs.getString("default_val"));
    //                    instruction.setActionCustomMaxWaitSec(rs.getInt("action_custom_max_wait_sec"));
    //                    instruction.setOnHoldSeconds(rs.getInt("on_hold_seconds"));
    //                    instruction.setEncrypted(rs.getInt("encrypted"));
    //                    instruction.setExportToABR(rs.getInt("export_to_abr"));
    //                    instruction.setOperation(rs.getString("operation"));
    //                    instruction.setParentId(rs.getInt("parent_id"));
    //
    //                    instruction.setInstructionReferenceLoadDTOList(new ArrayList<>());
    //                    blockDTO.getBlockLoopInstructionLoadDTOS().add(instruction);
    //                    instructionMap.put(instructionId, instruction);
    //                }
    //
    //                String referenceType = rs.getString("reference_type");
    //                if (referenceType != null) {
    //                    InstructionReferenceLoadDTO reference = new InstructionReferenceLoadDTO();
    //                    reference.setReferenceType(referenceType);
    //                    reference.setValue(rs.getString("value"));
    //                    instruction.getInstructionReferenceLoadDTOList().add(reference);
    //                }
    //            }
    //        } catch (SQLException e) {
    //            ABRLogger.getInstance(ABRViewBotJobPane.class).severe("loadBlockAll  \nError: " + e.getMessage());
    //        }
    //    }

    public ObservableList<ComboBoxVars> loadWebPageFields(int botJobId) {
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
                String name = rs.getString("instruction_name").trim();
                String actions = rs.getString("actions").trim();

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
            ABRLogger.getInstance(PerformDataBase.class)
                    .severe(String.format(
                            "loadWebPageFields - Error selecting Web Page Fields.\n Error: %s", e.getMessage()));
        }
        return webPageItems;
    }

    private void addInstruction(
            String name, String operation, Integer variableId, Integer parentId, RowMoveDTO rowMoveDTO) {

        // Create and show alert inside Platform.runLater
        Platform.runLater(() -> {
            // Create a label to display the instruction
            javafx.scene.control.Label newInstruction = new Label("\"" + name + "\" -> \"" + operation + "\"");
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
                    ABRLogger.getInstance(PerformDataBase.class)
                            .warning(String.format(
                                    "preInsertStep - InstructionId: %s in BlockId: %s now has order number: %d",
                                    instruction.getInstructionId(),
                                    instruction.getBlockId(),
                                    instruction.getInstructionOrderNumber() + 1));
                } else {
                    ABRLogger.getInstance(PerformDataBase.class)
                            .warning(String.format(
                                    "preInsertStep - No matching record found for BlockId: %d and InstructionId: %d",
                                    instruction.getBlockId(), instruction.getInstructionId()));
                }
            }

            return true;
        } catch (SQLException e) {
            ABRLogger.getInstance(PerformDataBase.class)
                    .severe(String.format("Error updating instruction order numbers.\nError: %s", e.getMessage()));
        }
        return false;
    }

    public SavedBlocksDTO createSavedBlockDTO(BlockSplitDTO blockSplitDTO) {

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
        savedBlock.setActive(blockSplitDTO.getDetails().getNewBlock().getActive());
        savedBlock.setWait(blockSplitDTO.getDetails().getNewBlock().getWait());
        savedBlock.setSavedBlockLoopInstructions(savedBlockLoopInstructions);
        return savedBlock;
    }

    public boolean showAlertCombinedVBOX(
            Alert.AlertType alertType, String title, String header, String content, VBox combinedTextContainer) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.getDialogPane().setContent(combinedTextContainer);

        if (alertType.equals(Alert.AlertType.CONFIRMATION)) {
            alert.getButtonTypes().set(0, ButtonType.YES);
            alert.getButtonTypes().set(1, ButtonType.NO);
        }
        Optional<ButtonType> result = alert.showAndWait();

        if (alertType.equals(Alert.AlertType.CONFIRMATION)) {
            return result.isPresent() && result.get() == ButtonType.YES;
        } else {
            return result.isPresent() && result.get() == ButtonType.OK;
        }
    }

    public boolean deleteBotJob(int botJobId) {
        // Build the SQL delete statement
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {

            String deleteSQL = "DELETE FROM bot_job " + " WHERE id = " + botJobId;

            // Execute the update statement and check if any rows were affected
            int rowsAffected = stmt.executeUpdate(deleteSQL);
            if (rowsAffected > 0) {
                ABRLogger.getInstance(PerformDataBase.class)
                        .info(String.format("The Bot Job  id %d has been successfully deleted!", botJobId));
            } else {
                ABRLogger.getInstance(PerformDataBase.class)
                        .warning(String.format("No matching record found for botJobId %d.", botJobId));
            }
            return true;
        } catch (SQLException e) {
            ABRLogger.getInstance(PerformDataBase.class)
                    .severe(String.format("Error deleting BotJobId ID %d. Error: %s", botJobId, e.getMessage()));
        }
        return false;
    }

    public boolean updateBotJobNme(int botJobId, String name, String description) {
        // Build the SQL delete statement
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {

            String updateSQL = "UPDATE bot_job set name = '" + name + "', description = '" + description
                    + "' WHERE id = " + botJobId;

            // Execute the update statement and check if any rows were affected
            int rowsAffected = stmt.executeUpdate(updateSQL);
            if (rowsAffected > 0) {
                ABRLogger.getInstance(PerformDataBase.class)
                        .info(String.format("The Bot Job  id %d has been successfully updated!", botJobId));
            } else {
                ABRLogger.getInstance(PerformDataBase.class)
                        .warning(String.format("No matching record found for botJobId %d.", botJobId));
            }
            return true;
        } catch (SQLException e) {
            ABRLogger.getInstance(PerformDataBase.class)
                    .severe(String.format("Error updating BotJobId ID %d. Error: %s", botJobId, e.getMessage()));
        }
        return false;
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

            ABRLogger.getInstance(PerformDataBase.class)
                    .info(String.format("Fetched %d instructions for Block ID %d:", instructions.size(), blockId));

        } catch (SQLException e) {
            ABRLogger.getInstance(PerformDataBase.class)
                    .severe(String.format(
                            "Error fetching instructions for Block ID %d. Error: %s: ", blockId, e.getMessage()));
        }

        return instructions;
    }

    public List<BotJobLoadDTO> loadJustJobBlocks(int botJobId) {
        String query = "SELECT bj.id AS bot_job_id, bj.name AS bot_job_name, "
                + " b.id AS block_id, b.block_order_number, b.name AS block_name, "
                + " b.description AS block_description, b.type_id,"
                + " b.active, b.wait"
                + " FROM bot_job bj "
                + " LEFT JOIN block b ON b.bot_job_id = bj.id "
                + " where bot_job_id = " + botJobId
                + "  ORDER BY bj.id, b.block_order_number ASC";

        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(query)) {

            Map<Integer, BotJobLoadDTO> botJobMap = new HashMap<>();
            Map<Integer, BlockLoadDTO> blockMap = new HashMap<>();

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
                    blockDTO.setActive(rs.getBoolean("active"));
                    blockDTO.setWait(rs.getInt("wait"));

                    blockDTO.setBotJobId(botJobDTO.getId());
                    blockDTO.setBotJobName(botJobDTO.getName());

                    blockDTO.setBlockLoopInstructionLoadDTOS(new ArrayList<>());
                    botJobDTO.getBlockLoadDTOList().add(blockDTO);
                    blockMap.put(blockId, blockDTO);
                }
            }
        } catch (SQLException e) {
            ABRLogger.getInstance(Thread.class)
                    .severe(String.format("Error loadBlockAll for botJobId %d\nError: %s", botJobId, e.getMessage()));
        }

        return botLoadJobs;
    }

    public void updateBlockStatus(int botJobId, int blockId, String blockName, boolean blockActive, int wait) {
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {

            // Update each block's block_order_number starting from 1
            String updateSQL = "UPDATE block SET active = '" + blockActive + "',"
                    + " wait = " + wait
                    + " WHERE id = " + blockId
                    + " and bot_job_id = " + botJobId;

            int rowsAffected = stmt.executeUpdate(updateSQL);

            if (rowsAffected > 0) {
                ABRLogger.getInstance(PerformDataBase.class)
                        .info(String.format(
                                "Block Status updated blockId: %s, name: %s, Active: %s",
                                blockId, blockName, blockActive));
            } else {
                ABRLogger.getInstance(PerformDataBase.class)
                        .warning(String.format(
                                "updateBlockStatus - No matching record found to update botJobId: %d blockId: %d",
                                botJobId, blockId));
            }

        } catch (SQLException e) {
            ABRLogger.getInstance(PerformDataBase.class)
                    .severe(String.format("Error updateBlockStatus. Error: %s", e.getMessage()));
        }
    }

    public List<BlockLoadDTO> loadBlocksForBotJob(int botJobId) {
        // SQL query to get the blocks for a specific bot job
        String query = "SELECT " + "b.id AS block_id, "
                + "b.block_order_number, "
                + "b.name AS block_name, "
                + "b.description AS block_description, "
                + "b.type_id, "
                + "bj.id AS bot_job_id, "
                + "bj.name AS bot_job_name "
                + "FROM bot_job bj "
                + "JOIN block b ON b.bot_job_id = bj.id "
                + "WHERE bj.id = "
                + botJobId + " " + // Use the botJobId directly in the query string
                "ORDER BY b.block_order_number ASC";

        // Initialize the necessary data structures
        blockLoadList.clear();
        Map<Integer, BlockLoadDTO> blockMap = new HashMap<>();

        // Use Statement to execute the query
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                // Load the Block information
                int blockId = rs.getInt("block_id");
                BlockLoadDTO blockDTO = blockMap.get(blockId);

                if (blockDTO == null) {
                    blockDTO = new BlockLoadDTO();
                    blockDTO.setId(blockId);
                    blockDTO.setBlockOrderNumber(rs.getInt("block_order_number"));
                    blockDTO.setName(rs.getString("block_name"));
                    blockDTO.setDescription(rs.getString("block_description"));
                    blockDTO.setTypeId(rs.getInt("type_id"));
                    blockDTO.setBotJobId(rs.getInt("bot_job_id"));
                    blockDTO.setBotJobName(rs.getString("bot_job_name"));

                    blockMap.put(blockId, blockDTO);
                    blockLoadList.add(blockDTO);
                }
            }
        } catch (SQLException e) {
            ABRLogger.getInstance(Thread.class)
                    .severe(String.format("Error loadBlockAll for botJobId %d\nError: %s", botJobId, e.getMessage()));
        }

        return blockLoadList;
    }
}

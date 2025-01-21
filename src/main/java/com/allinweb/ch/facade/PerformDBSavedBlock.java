package com.allinweb.ch.facade;

import com.allinweb.ch.component.model.BlockLoadDTO;
import com.allinweb.ch.component.model.BlockLoopInstructionLoadDTO;
import com.allinweb.ch.component.model.BlockSplitDTO;
import com.allinweb.ch.component.model.InstructionDTO;
import com.allinweb.ch.component.model.InstructionReferenceLoadDTO;
import com.allinweb.ch.core.ABRSharedResources;
import com.allinweb.ch.persistence.BlockDTO;
import com.allinweb.ch.persistence.BlockLoopInstructionDTO;
import com.allinweb.ch.persistence.BotJobDTO;
import com.allinweb.ch.persistence.SavedBlockLoopInstructionDTO;
import com.allinweb.ch.persistence.SavedBlocksDTO;
import com.allinweb.ch.persistence.SavedInstructionReferenceDTO;
import com.allinweb.ch.util.ABRLogger;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

public class PerformDBSavedBlock {

    private static final PerformMessage performMessage;
    private static final PerformDataBase performDataBase;
    // Static block to initialize
    static {
        performMessage = PerformMessage.getInstance();
        performDataBase = PerformDataBase.getInstance();
    }

    // Static final variable to hold the singleton instance
    protected static final SingletonSupplier<PerformDBSavedBlock> instance = () -> new PerformDBSavedBlock();

    private static final DateTimeFormatter FORMAT_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    // Private constructor to prevent instantiation
    private PerformDBSavedBlock() {
        // Initialize if necessary
    }

    public void initializePerformActions() {}

    // Public method to access the singleton instance
    public static PerformDBSavedBlock getInstance() {
        return instance.get();
    }

    // Creating SAVED BLOCKS FORM BLOCKS DTO
    public static SavedBlocksDTO createSavedBlocksDTOFromBlocksDTO(BlockDTO blockDTO) {
        SavedBlocksDTO savedBlocksDTO = new SavedBlocksDTO();
        savedBlocksDTO.setName(blockDTO.getName());
        savedBlocksDTO.setDescription(blockDTO.getDescription());
        savedBlocksDTO.setTypeId(blockDTO.getTypeId());

        return savedBlocksDTO;
    }

    public SavedBlocksDTO createSavedBlockDTO(BlockSplitDTO blockSplitDTO) {

        List<InstructionDTO> instructions = performDataBase.getInstructionsByBlockId(
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
            instruction.setOptional(instructionDTO.getOptional());
            instruction.setActionCustomMaxWaitSec(instructionDTO.getActionCustomMaxWaitSec());
            instruction.setOnHoldSeconds(instructionDTO.getOnHoldSeconds());
            instruction.setCodified(instructionDTO.getCodified());
            instruction.setExportToABR(instructionDTO.getExportToABR());
            instruction.setActive(instructionDTO.getInstructionActive());

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

    public int insertSavedInstruction(
            SavedBlockLoopInstructionDTO savedInstructionDTO, int savedCurrentBotJobId, int savedCurrentBlockId)
            throws SQLException {
        // Generate a Unique-ID for the block

        try (Statement stmt = performDataBase.getConnection().createStatement()) {

            Integer nextId = loadNextIdSavedInstructionData() + 1;
            savedInstructionDTO.setId(nextId);

            String pathValue = (savedInstructionDTO.getPath() != null) ? "'" + savedInstructionDTO.getPath() + "'" : "";

            // Build the SQL insert query

            String insertSQL = "INSERT INTO saved_block_loop_instruction(\n" + "id, "
                    + "action_custom_max_wait_sec, "
                    + "actions, "
                    + "block_marked, "
                    //                    + "default_value, "
                    + "description, "
                    + "codified, "
                    + "export_to_abr, "
                    + "instruction_order_number, "
                    + "name, "
                    + "on_hold_seconds, "
                    + "operation, "
                    + "optional, "
                    + "parent_id, "
                    + "path, "
                    + "variable_id, "
                    + "saved_block_id, "
                    + "bot_job_id, "
                    + "active)\n"
                    + "VALUES ("
                    + savedInstructionDTO.getId()
                    + ", " + savedInstructionDTO.getActionCustomMaxWaitSec()
                    + ", '" + savedInstructionDTO.getActions() + "'"
                    + ", " + savedInstructionDTO.getBlockMarked()
                    //                    + ", '" + savedInstructionDTO.getDefaultValue() + "'"
                    + ", '" + savedInstructionDTO.getDescription() + "'"
                    + ", " + savedInstructionDTO.getCodified()
                    + ", " + savedInstructionDTO.getExportToABR()
                    + ", " + savedInstructionDTO.getInstructionOrderNumber()
                    + ", '" + savedInstructionDTO.getName() + "'"
                    + ", " + savedInstructionDTO.getOnHoldSeconds()
                    + ", '" + savedInstructionDTO.getOperation() + "'"
                    + ", " + savedInstructionDTO.getOptional()
                    + ", " + savedInstructionDTO.getParentId()
                    + ", " + pathValue
                    + ", " + savedInstructionDTO.getVariableId()
                    + ", " + savedCurrentBlockId + ", "
                    + ", " + savedCurrentBotJobId + ", "
                    + savedInstructionDTO.getActive() // active
                    + ");";

            int rowsAffected = stmt.executeUpdate(insertSQL);
            if (rowsAffected > 0) {
                ABRLogger.getInstance(PerformDBSavedBlock.class)
                        .info(String.format(
                                "New Instruction SAVED SUCCESSFULLY id: %d Name: %s Actions: %s Operation: %s",
                                savedInstructionDTO.getId(),
                                savedInstructionDTO.getName(),
                                savedInstructionDTO.getActions(),
                                savedInstructionDTO.getOperation()));
                return nextId;
            } else {
                ABRLogger.getInstance(PerformDBSavedBlock.class)
                        .warning(String.format(
                                "Instruction NOT SAVED\nid: %d Name: %s Actions: %s Operations: %s",
                                savedInstructionDTO.getId(),
                                savedInstructionDTO.getName(),
                                savedInstructionDTO.getActions(),
                                savedInstructionDTO.getOperation()));
                return -1;
            }
        }
    }

    private Integer loadNextIdSavedInstructionData() {
        //        String selectSQL = "SELECT NEXT_VAL fROM homeBankingSeq";
        String selectSQL = "SELECT MAX(ID) AS max_id FROM saved_block_loop_instruction";
        try (Statement stmt = performDataBase.getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(selectSQL)) {
            while (rs.next()) {
                return rs.getInt("max_id");
            }
        } catch (SQLException e) {
            ABRLogger.getInstance(PerformDBSavedBlock.class)
                    .severe("loadNextIdBReferenceData  \nError: " + e.getMessage());
        }
        return null;
    }

    // Creating COMPONENT SAVED INSTRUCTIONS FOR BLOCK INSTRUCTIONS
    public static List<SavedBlockLoopInstructionDTO> createSavedBlockLoopInstructionsFromBlocksDTO(
            BlockDTO blockDTO, SavedBlocksDTO savedBlocksDTO) {
        SavedBlockLoopInstructionDTO savedBlockLoopInstructionDTO;
        List<SavedBlockLoopInstructionDTO> savedBlockLoopInstructionDTOs = new ArrayList<>();

        List<BlockLoopInstructionDTO> instructionList = ABRSharedResources.getInstance()
                .getEntityList(
                        BlockLoopInstructionDTO.class,
                        instruction -> instruction.getBlock().getId().equals(blockDTO.getId()));

        List<BlockLoopInstructionDTO> instructionFiltered = performDataBase.filterInstructions(instructionList);

        for (BlockLoopInstructionDTO blockLoopInstructionDTO : instructionFiltered) {
            savedBlockLoopInstructionDTO = new SavedBlockLoopInstructionDTO();

            savedBlockLoopInstructionDTO.setActionCustomMaxWaitSec(blockLoopInstructionDTO.getActionCustomMaxWaitSec());
            savedBlockLoopInstructionDTO.setActions(blockLoopInstructionDTO.getActions());
            savedBlockLoopInstructionDTO.setBlock(savedBlocksDTO);

            savedBlockLoopInstructionDTO.setDefaultValue(blockLoopInstructionDTO.getDefaultValue());
            savedBlockLoopInstructionDTO.setDescription(blockLoopInstructionDTO.getDescription());
            savedBlockLoopInstructionDTO.setCodified(blockLoopInstructionDTO.getCodified());
            savedBlockLoopInstructionDTO.setExportToABR(blockLoopInstructionDTO.getExportToABR());
            savedBlockLoopInstructionDTO.setActive(blockLoopInstructionDTO.getActive());
            savedBlockLoopInstructionDTO.setInstructionOrderNumber(blockLoopInstructionDTO.getInstructionOrderNumber());
            savedBlockLoopInstructionDTO.setName(blockLoopInstructionDTO.getName());
            savedBlockLoopInstructionDTO.setOnHoldSeconds(blockLoopInstructionDTO.getOnHoldSeconds());
            savedBlockLoopInstructionDTO.setOptional(blockLoopInstructionDTO.getOptional());
            savedBlockLoopInstructionDTO.setPath(blockLoopInstructionDTO.getPath());

            List<SavedInstructionReferenceDTO> referenceDTOList = new ArrayList<>(
                    SavedInstructionReferenceDTO.createSavedReferencesFromInstructionForSavedInstruction(
                            blockLoopInstructionDTO, savedBlockLoopInstructionDTO));
            savedBlockLoopInstructionDTO.setSavedInstructionReferenceDTOList(referenceDTOList);

            savedBlockLoopInstructionDTOs.add(savedBlockLoopInstructionDTO);
        }

        return savedBlockLoopInstructionDTOs;
    }

    // Creating BLOCKS DTO FROM SAVED BLOCKS
    public static BlockLoadDTO createBlocksDTOFromSavedBlocksDTO(SavedBlocksDTO savedBlocksDTO, BotJobDTO botJobDTO) {
        BlockLoadDTO blocksDTO = new BlockLoadDTO();
        blocksDTO.setName(savedBlocksDTO.getName());
        blocksDTO.setBotJobId(botJobDTO.getId());
        blocksDTO.setDescription(savedBlocksDTO.getDescription());
        blocksDTO.setTypeId(savedBlocksDTO.getTypeId());
        blocksDTO.setExportFile(savedBlocksDTO.getExportFile());
        return blocksDTO;
    }

    // Creating BLOCK INSTRUCTIONS FROM COMPONENT SAVED INSTRUCTIONS
    public static List<BlockLoopInstructionLoadDTO> createBlockLoopInstructionsFromSavedBlocksDTO(
            SavedBlocksDTO savedBlocksDTO) {

        BlockLoopInstructionLoadDTO blockLoopInstructionDTO;

        //        List<BlockLoopInstructionLoadDTO> savedInstructions = ABRSharedResources.getInstance()
        //                .getEntityList(
        //                        SavedBlockLoopInstructionDTO.class,
        //                        saved -> saved.getBlock().getId().equals(savedBlocksDTO.getId()));

        List<BlockLoopInstructionLoadDTO> savedInstructions =
                getSavedInstructionsByBlockId(savedBlocksDTO.getBotJobDTO().getId(), savedBlocksDTO.getId());

        //        for (BlockLoopInstructionLoadDTO savedBlockLoopInstructionDTO : savedInstructions) {
        //            blockLoopInstructionDTO = new BlockLoopInstructionLoadDTO();
        //
        //
        // blockLoopInstructionDTO.setActionCustomMaxWaitSec(savedBlockLoopInstructionDTO.getActionCustomMaxWaitSec());
        //            blockLoopInstructionDTO.setActions(savedBlockLoopInstructionDTO.getActions());
        //
        //            blockLoopInstructionDTO.setBlockId(blockDTO.getId());
        //            blockLoopInstructionDTO.setDefaultValue(savedBlockLoopInstructionDTO.getDefaultValue());
        //            blockLoopInstructionDTO.setDescription(savedBlockLoopInstructionDTO.getDescription());
        //            blockLoopInstructionDTO.setCodified(savedBlockLoopInstructionDTO.getCodified());
        //            blockLoopInstructionDTO.setExportToABR(savedBlockLoopInstructionDTO.getExportToABR());
        //            blockLoopInstructionDTO.setInstructionActive(savedBlockLoopInstructionDTO.getInstructionActive());
        //
        // blockLoopInstructionDTO.setInstructionOrderNumber(savedBlockLoopInstructionDTO.getInstructionOrderNumber());
        //            blockLoopInstructionDTO.setName(savedBlockLoopInstructionDTO.getName());
        //            blockLoopInstructionDTO.setOnHoldSeconds(savedBlockLoopInstructionDTO.getOnHoldSeconds());
        //            blockLoopInstructionDTO.setOptional(savedBlockLoopInstructionDTO.getOptional());
        //            blockLoopInstructionDTO.setPath(savedBlockLoopInstructionDTO.getPath());
        //
        //            List<InstructionReferenceLoadDTO> referenceDTOList =
        //                    new
        // ArrayList<>(InstructionReferenceDTO.createReferencesFromSavedInstructionForInstruction(
        //                            savedBlockLoopInstructionDTO, blockLoopInstructionDTO));
        //            blockLoopInstructionDTO.setInstructionReferenceDTOList(referenceDTOList);
        //
        //            blockLoopInstructionDTOs.add(blockLoopInstructionDTO);
        //        }

        return savedInstructions;
    }

    private int createSavedBlock(BlockDTO blockDTO) {
        // Generate a Unique-ID for the block
        Integer nextId = loadNextIdSavedBlockData() + 1;
        Integer nextBlockOrder =
                loadNextSavedBlockOrderNumber(blockDTO.getBotJobDTO().getId()) + 1;

        // Build the SQL insert query
        String insertSQL =
                "INSERT INTO saved_blocks(id, block_order_number, description, name, type_id, bot_job_id, active) VALUES ("
                        + nextId + ", "
                        + nextBlockOrder + ", " // block_order_number
                        + "'" + blockDTO.getDescription() + "', " // description
                        + "'" + blockDTO.getName() + "', " // name
                        + 1 + ", " // type_id
                        + blockDTO.getBotJobDTO().getId() + ", " // bot_job_id, assuming BotJobDTO has an ID
                        + blockDTO.getActive() + ", " // active
                        + ")";

        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {
            stmt.executeUpdate(insertSQL);
            ABRLogger.getInstance(PerformActions.class).info("Block data saved successfully id: " + nextId);
            return nextId;
        } catch (SQLException e) {
            ABRLogger.getInstance(PerformActions.class).severe("saveBlock  \nError: " + e.getMessage());
            return -1;
        }
    }

    private Integer loadNextSavedBlockOrderNumber(int botJobId) {
        //        String selectSQL = "SELECT NEXT_VAL fROM homeBankingSeq";
        String selectSQL = "SELECT MAX(ID) AS max_id FROM saved_blocks where bot_job_id = " + botJobId;
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(selectSQL)) {
            while (rs.next()) {
                return rs.getInt("max_id");
            }
        } catch (SQLException e) {
            ABRLogger.getInstance(PerformActions.class).severe("loadNextIdBlockData  \nError: " + e.getMessage());
        }
        return null;
    }

    private Integer loadNextIdSavedBlockData() {
        //        String selectSQL = "SELECT NEXT_VAL fROM homeBankingSeq";
        String selectSQL = "SELECT MAX(ID) AS max_id FROM saved_blocks";
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(selectSQL)) {
            while (rs.next()) {
                return rs.getInt("max_id");
            }
        } catch (SQLException e) {
            ABRLogger.getInstance(PerformActions.class).severe("loadNextIdBlockData  \nError: " + e.getMessage());
        }
        return null;
    }

    public static List<BlockLoopInstructionLoadDTO> getSavedInstructionsByBlockId(int botJobId, int blockId) {
        // List to store the fetched instructions
        List<BlockLoopInstructionLoadDTO> instructions = new ArrayList<>();

        // SQL query to fetch block_loop_instruction
        String blockLoopQuery =
                """
        SELECT id, action_custom_max_wait_sec, actions, active, block_marked, codified,
               default_val, description, export_to_abr, instruction_order_number, name,
               on_hold_seconds, operation, optional, parent_id, path, variable_id, block_id, bot_job_id
        FROM block_loop_instruction
        WHERE block_id = ?
        ORDER BY instruction_order_number ASC
    """;

        // SQL query to fetch instruction_reference for a specific block_loop_instruction
        String instructionReferenceQuery =
                """
        SELECT id, reference_type, value, block_loop_instruction_id, bot_job_id
        FROM instruction_reference
        WHERE block_loop_instruction_id = ?
    """;

        try (Connection connection = performDataBase.getConnection();
                PreparedStatement blockLoopStmt = connection.prepareStatement(blockLoopQuery);
                PreparedStatement instructionRefStmt = connection.prepareStatement(instructionReferenceQuery)) {

            // Set parameters and execute the block_loop_instruction query
            blockLoopStmt.setInt(1, blockId);
            try (ResultSet blockLoopRs = blockLoopStmt.executeQuery()) {
                while (blockLoopRs.next()) {
                    // Populate BlockLoopInstructionLoadDTO
                    BlockLoopInstructionLoadDTO instruction = new BlockLoopInstructionLoadDTO();
                    instruction.setId(blockLoopRs.getInt("id"));
                    instruction.setBotJobId(blockLoopRs.getInt("bot_job_id"));
                    instruction.setInstructionOrderNumber(blockLoopRs.getInt("instruction_order_number"));
                    instruction.setName(blockLoopRs.getString("name"));
                    instruction.setDescription(blockLoopRs.getString("description"));
                    instruction.setActions(blockLoopRs.getString("actions"));
                    instruction.setPath(blockLoopRs.getString("path"));
                    instruction.setOptional(blockLoopRs.getBoolean("optional"));
                    instruction.setCodified(blockLoopRs.getBoolean("codified"));
                    instruction.setExportToABR(blockLoopRs.getBoolean("export_to_abr"));
                    instruction.setActionCustomMaxWaitSec(blockLoopRs.getInt("action_custom_max_wait_sec"));
                    instruction.setOnHoldSeconds(blockLoopRs.getInt("on_hold_seconds"));
                    instruction.setBlockId(blockLoopRs.getInt("block_id"));
                    instruction.setParentId(blockLoopRs.getInt("parent_id"));
                    instruction.setBlockMarked(blockLoopRs.getBoolean("block_marked"));
                    instruction.setDefaultValue(blockLoopRs.getString("default_val"));
                    instruction.setVariableId(blockLoopRs.getInt("variable_id"));
                    instruction.setOperation(blockLoopRs.getString("operation"));
                    instruction.setInstructionActive(blockLoopRs.getBoolean("active"));

                    // Fetch related InstructionReferenceLoadDTO data
                    List<InstructionReferenceLoadDTO> references = new ArrayList<>();
                    instructionRefStmt.setInt(1, instruction.getId());
                    try (ResultSet instructionRefRs = instructionRefStmt.executeQuery()) {
                        while (instructionRefRs.next()) {
                            InstructionReferenceLoadDTO reference = new InstructionReferenceLoadDTO();
                            reference.setId(instructionRefRs.getInt("id"));
                            reference.setReferenceType(instructionRefRs.getString("reference_type"));
                            reference.setValue(instructionRefRs.getString("value"));
                            reference.setBlockLoopInstructionId(instructionRefRs.getInt("block_loop_instruction_id"));
                            reference.setBotJobId(instructionRefRs.getInt("bot_job_id"));
                            references.add(reference);
                        }
                    }
                    // Set the references list
                    instruction.setInstructionReferenceLoadDTOList(references);

                    // Add the populated instruction to the list
                    instructions.add(instruction);
                }
            }
        } catch (SQLException e) {
            ABRLogger.getInstance(PerformDataBase.class)
                    .severe(String.format(
                            "Error fetching instructions for Block ID %d. Error: %s", blockId, e.getMessage()));
        }

        return instructions;
    }
}

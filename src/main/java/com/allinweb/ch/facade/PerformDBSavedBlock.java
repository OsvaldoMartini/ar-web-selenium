package com.allinweb.ch.facade;

import com.allinweb.ch.component.model.BlockLoadDTO;
import com.allinweb.ch.component.model.BlockLoopInstructionLoadDTO;
import com.allinweb.ch.component.model.BlockSplitDTO;
import com.allinweb.ch.component.model.DetailsDTO;
import com.allinweb.ch.component.model.InstructionDTO;
import com.allinweb.ch.component.model.InstructionReferenceLoadDTO;
import com.allinweb.ch.core.ARSharedResources;
import com.allinweb.ch.persistence.BlockDTO;
import com.allinweb.ch.persistence.ComponentBlockDTO;
import com.allinweb.ch.persistence.ComponentInstructionDTO;
import com.allinweb.ch.persistence.ComponentReferenceDTO;
import com.allinweb.ch.util.ARLogger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

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
    public static ComponentBlockDTO createSavedBlocksDTOFromBlocksDTO(BlockDTO blockDTO) {
        ComponentBlockDTO componentBlockDTO = new ComponentBlockDTO();
        componentBlockDTO.setName(blockDTO.getName());
        componentBlockDTO.setDescription(blockDTO.getDescription());
        componentBlockDTO.setTypeId(blockDTO.getTypeId());

        return componentBlockDTO;
    }

    public ComponentBlockDTO createSavedBlockDTO(BlockSplitDTO blockSplitDTO) {

        List<InstructionDTO> instructions = performDataBase.getInstructionsByBlockId(
                blockSplitDTO.getDetails().getNewBlock().getBotJobId(),
                blockSplitDTO.getDetails().getNewBlock().getBlockId());

        List<ComponentInstructionDTO> savedBlockLoopInstructions = new ArrayList<>();

        for (InstructionDTO instructionDTO : instructions) {
            // Create mock SavedBlockLoopInstructionDTO entries
            ComponentInstructionDTO instruction = new ComponentInstructionDTO();
            instruction.setInstructionOrderNumber(instruction.getInstructionOrderNumber());
            instruction.setActions(instructionDTO.getActions());
            instruction.setName(instructionDTO.getInstructionName());
            instruction.setPath(instructionDTO.getPath());
            instruction.setDescription(instructionDTO.getDescription());
            instruction.setOptional(instructionDTO.getOptional());
            instruction.setActionCustomMaxWaitSec(instructionDTO.getActionCustomMaxWaitSec());
            instruction.setOnHoldSeconds(instructionDTO.getOnHoldSeconds());
            instruction.setCodified(instructionDTO.getCodified());
            instruction.setExportToAR(instructionDTO.getExportToAR());
            instruction.setActive(instructionDTO.getInstructionActive());

            savedBlockLoopInstructions.add(instruction);
        }

        // Assign mock instructions to mock blocks
        ComponentBlockDTO savedBlock = new ComponentBlockDTO();
        savedBlock.setHomeBankingId(blockSplitDTO.getDetails().getNewBlock().getHomeBankingId());
        savedBlock.setName("Comp-" + blockSplitDTO.getDetails().getNewBlock().getBlockName());
        savedBlock.setDescription("Component for: ...");
        savedBlock.setTypeId(1);
        savedBlock.setActive(blockSplitDTO.getDetails().getNewBlock().getActive());
        savedBlock.setWait(blockSplitDTO.getDetails().getNewBlock().getWait());
        savedBlock.setSavedBlockLoopInstructions(savedBlockLoopInstructions);
        return savedBlock;
    }

    public int insertSavedInstruction(
            ComponentInstructionDTO savedInstructionDTO, int savedCurrentBotJobId, int savedCurrentBlockId)
            throws SQLException {
        // Generate a Unique-ID for the block

        try (Statement stmt = performDataBase.getConnection().createStatement()) {

            Integer nextId = loadNextIdSavedInstructionData() + 1;
            savedInstructionDTO.setId(nextId);

            String pathValue = (savedInstructionDTO.getPath() != null) ? "'" + savedInstructionDTO.getPath() + "'" : "";

            // Build the SQL insert query

            String insertSQL = "INSERT INTO component_instruction(\n" + "id, "
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
                    + "component_block_id, "
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
                    + ", " + savedInstructionDTO.getExportToAR()
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
                ARLogger.getInstance(PerformDBSavedBlock.class)
                        .info(String.format(
                                "New Instruction SAVED SUCCESSFULLY id: %d Name: %s Actions: %s Operation: %s",
                                savedInstructionDTO.getId(),
                                savedInstructionDTO.getName(),
                                savedInstructionDTO.getActions(),
                                savedInstructionDTO.getOperation()));
                return nextId;
            } else {
                ARLogger.getInstance(PerformDBSavedBlock.class)
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
        String selectSQL = "SELECT MAX(ID) AS max_id FROM component_instruction";
        try (Statement stmt = performDataBase.getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(selectSQL)) {
            while (rs.next()) {
                return rs.getInt("max_id");
            }
        } catch (SQLException e) {
            ARLogger.getInstance(PerformDBSavedBlock.class)
                    .severe("loadNextIdBReferenceData  \nError: " + e.getMessage());
        }
        return null;
    }

    // Creating COMPONENT SAVED INSTRUCTIONS FOR BLOCK INSTRUCTIONS
    public static List<ComponentInstructionDTO> createSavedBlockLoopInstructionsFromBlocksDTO(
            DetailsDTO detailsDTO, ComponentBlockDTO componentBlockDTO) {
        ComponentInstructionDTO componentInstructionDTO;
        List<ComponentInstructionDTO> componentInstructionDTOS = new ArrayList<>();

        //        List<BlockLoopInstructionDTO> instructionList = ARSharedResources.getInstance()
        //                .getEntityList(
        //                        BlockLoopInstructionDTO.class,
        //                        instruction -> instruction.getBlock().getId().equals(blockDTO.getId()));
        if (detailsDTO != null && detailsDTO.getNewBlock() != null) {

            List<InstructionDTO> instructionList = performDataBase.getInstructionsByBlockId(
                    detailsDTO.getNewBlock().getBotJobId(),
                    detailsDTO.getNewBlock().getBlockId());

            List<InstructionDTO> instructionFiltered = performDataBase.filterInstructions(instructionList);

            for (InstructionDTO instructionDTO : instructionFiltered) {
                componentInstructionDTO = new ComponentInstructionDTO();

                componentInstructionDTO.setActionCustomMaxWaitSec(instructionDTO.getActionCustomMaxWaitSec());
                componentInstructionDTO.setActions(instructionDTO.getActions());
                componentInstructionDTO.setBlock(componentBlockDTO);

                componentInstructionDTO.setDefaultValue(instructionDTO.getDefaultValue());
                componentInstructionDTO.setDescription(instructionDTO.getDescription());
                componentInstructionDTO.setCodified(instructionDTO.getCodified());
                componentInstructionDTO.setExportToAR(instructionDTO.getExportToAR());
                componentInstructionDTO.setActive(instructionDTO.getInstructionActive());
                componentInstructionDTO.setInstructionOrderNumber(instructionDTO.getInstructionOrderNumber());
                componentInstructionDTO.setName(instructionDTO.getInstructionName());
                componentInstructionDTO.setOnHoldSeconds(instructionDTO.getOnHoldSeconds());
                componentInstructionDTO.setOptional(instructionDTO.getOptional());
                componentInstructionDTO.setPath(instructionDTO.getPath());

                List<ComponentReferenceDTO> referenceDTOList =
                        new ArrayList<>(ComponentReferenceDTO.createSavedReferencesFromInstructionForSavedInstruction(
                                instructionDTO, componentInstructionDTO));
                componentInstructionDTO.setSavedInstructionReferenceDTOList(referenceDTOList);

                componentInstructionDTOS.add(componentInstructionDTO);
            }
        }
        return componentInstructionDTOS;
    }

    // Creating BLOCKS DTO FROM SAVED BLOCKS
    public static BlockLoadDTO createBlocksDTOFromSavedBlocksDTO(
            ComponentBlockDTO componentBlockDTO, Integer botJobId) {
        BlockLoadDTO blockDTO = new BlockLoadDTO();

        blockDTO.setTypeId(1);
        blockDTO.setHomeBankingId(componentBlockDTO.getHomeBankingId());
        blockDTO.setBotJobId(botJobId);
        blockDTO.setName(componentBlockDTO.getName());
        blockDTO.setDescription(componentBlockDTO.getDescription());
        blockDTO.setExportFile(componentBlockDTO.getExportFile());
        blockDTO.setActive(componentBlockDTO.getActive());

        return blockDTO;
    }

    // Creating BLOCK INSTRUCTIONS FROM COMPONENT SAVED INSTRUCTIONS
    public static List<BlockLoopInstructionLoadDTO> createBlockLoopInstructionsFromSavedBlocksDTO(
            ComponentBlockDTO componentBlockDTO) {

        BlockLoopInstructionLoadDTO blockLoopInstructionDTO;

        //        List<BlockLoopInstructionLoadDTO> savedInstructions = ARSharedResources.getInstance()
        //                .getEntityList(
        //                        SavedBlockLoopInstructionDTO.class,
        //                        saved -> saved.getBlock().getId().equals(savedBlocksDTO.getId()));

        List<BlockLoopInstructionLoadDTO> savedInstructions =
                getSavedInstructionsByBlockId(componentBlockDTO.getBotJobDTO().getId(), componentBlockDTO.getId());

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
        //            blockLoopInstructionDTO.setExportToAR(savedBlockLoopInstructionDTO.getExportToAR());
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
                "INSERT INTO component_block(id, block_order_number, description, name, type_id, bot_job_id, active) VALUES ("
                        + nextId + ", "
                        + nextBlockOrder + ", " // block_order_number
                        + "'" + blockDTO.getDescription() + "', " // description
                        + "'" + blockDTO.getName() + "', " // name
                        + 1 + ", " // type_id
                        + blockDTO.getBotJobDTO().getId() + ", " // bot_job_id, assuming BotJobDTO has an ID
                        + blockDTO.getActive() + ", " // active
                        + ")";

        try (Statement stmt = ARSharedResources.getInstance().getConnection().createStatement()) {
            stmt.executeUpdate(insertSQL);
            ARLogger.getInstance(PerformActions.class).info("Block data saved successfully id: " + nextId);
            return nextId;
        } catch (SQLException e) {
            ARLogger.getInstance(PerformActions.class).severe("saveBlock  \nError: " + e.getMessage());
            return -1;
        }
    }

    private Integer loadNextSavedBlockOrderNumber(int botJobId) {
        //        String selectSQL = "SELECT NEXT_VAL fROM homeBankingSeq";
        String selectSQL = "SELECT MAX(ID) AS max_id FROM component_block where bot_job_id = " + botJobId;
        try (Statement stmt = ARSharedResources.getInstance().getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(selectSQL)) {
            while (rs.next()) {
                return rs.getInt("max_id");
            }
        } catch (SQLException e) {
            ARLogger.getInstance(PerformActions.class).severe("loadNextIdBlockData  \nError: " + e.getMessage());
        }
        return null;
    }

    private Integer loadNextIdSavedBlockData() {
        //        String selectSQL = "SELECT NEXT_VAL fROM homeBankingSeq";
        String selectSQL = "SELECT MAX(ID) AS max_id FROM component_block";
        try (Statement stmt = ARSharedResources.getInstance().getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(selectSQL)) {
            while (rs.next()) {
                return rs.getInt("max_id");
            }
        } catch (SQLException e) {
            ARLogger.getInstance(PerformActions.class).severe("loadNextIdBlockData  \nError: " + e.getMessage());
        }
        return null;
    }

    public static List<BlockLoopInstructionLoadDTO> getSavedInstructionsByBlockId(int botJobId, int blockId) {
        // List to store the fetched instructions
        List<BlockLoopInstructionLoadDTO> instructions = new ArrayList<>();

        // SQL query to fetch instruction
        String blockLoopQuery =
                """
        SELECT id, action_custom_max_wait_sec, actions, active, block_marked, codified,
               default_val, description, export_to_abr, instruction_order_number, name,
               on_hold_seconds, operation, optional, parent_id, path, variable_id, block_id, bot_job_id
        FROM instruction
        WHERE block_id = ?
        ORDER BY instruction_order_number ASC
    """;

        // SQL query to fetch reference for a specific instruction
        String instructionReferenceQuery =
                """
        SELECT id, reference_type, value, instruction_id, bot_job_id
        FROM reference
        WHERE instruction_id = ?
    """;

        try (Connection connection = performDataBase.getConnection();
                PreparedStatement blockLoopStmt = connection.prepareStatement(blockLoopQuery);
                PreparedStatement instructionRefStmt = connection.prepareStatement(instructionReferenceQuery)) {

            // Set parameters and execute the instruction query
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
                    instruction.setExportToAR(blockLoopRs.getBoolean("export_to_abr"));
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
                            reference.setBlockLoopInstructionId(instructionRefRs.getInt("instruction_id"));
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
            ARLogger.getInstance(PerformDataBase.class)
                    .severe(String.format(
                            "Error fetching instructions for Block ID %d. Error: %s", blockId, e.getMessage()));
        }

        return instructions;
    }
}

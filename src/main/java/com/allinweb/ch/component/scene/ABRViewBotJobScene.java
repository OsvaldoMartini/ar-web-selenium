package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.model.BlockDetailsDTO;
import com.allinweb.ch.component.model.BlockLoadDTO;
import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.pane.ABRViewBotJobPane;
import com.allinweb.ch.component.pane.base.IABRPane;
import com.allinweb.ch.component.scene.base.ABRScene;
import com.allinweb.ch.core.ABRSharedResources;
import com.allinweb.ch.facade.PerformActions;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.persistence.BlockDTO;
import com.allinweb.ch.persistence.BotJobDTO;
import com.allinweb.ch.persistence.SavedBlocksDTO;
import com.allinweb.ch.util.ABRLogger;
import com.google.common.base.Strings;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.scene.control.Alert;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

public class ABRViewBotJobScene extends ABRScene {

    private ABRScene currentScene;

    private List<BotJobLoadDTO> botLoadJobs = new ArrayList<>();
    private List<BlockLoadDTO> blockLoadList = new ArrayList<>();

    private static final Double SCENE_HEIGHT = 600D;
    private static final Double SCENE_WIDTH = 1100D;
    private static final String TITLE = "Bot Job Details";

    private static final PerformDataBase performDatabase;
    private static final PerformActions performAction;

    // Static block to initialize
    static {
        performDatabase = PerformDataBase.getInstance();
        performAction = PerformActions.getInstance();
    }

    private final Integer botJobId;

    public ABRViewBotJobScene(Integer botJobId) {
        super();
        this.botJobId = botJobId;
        this.currentScene = currentScene;
    }

    @Override
    public IABRPane buildPane() {

        //        ABRSharedResources.getInstance().cacheEntitiesFromDB();

        BotJobDTO botJobDTO = ABRSharedResources.getInstance().getEntityById(BotJobDTO.class, this.botJobId);

        loadBlocksForBotJob(this.botJobId);
        this.botLoadJobs = performDatabase.loadBlockAll(this.botJobId);

        // It Prevents Start without blocks
        if (blockLoadList.isEmpty()) {

            // It Prevents Start without blocks
            SavedBlocksDTO savedBlocksDTO = new SavedBlocksDTO();

            savedBlocksDTO.setDescription("Default Block description");
            savedBlocksDTO.setName("Default Block");
            BlockDTO blockDTO = performAction.createBlocksDTOFromSavedBlocksDTO(savedBlocksDTO, botJobDTO);
            BotJobDTO botJob = ABRSharedResources.getInstance().getEntityById(BotJobDTO.class, botJobDTO.getId());
            blockDTO.setTypeId(1);
            blockDTO.setActive(blockDTO.getActive());
            blockDTO.setWait(blockDTO.getWait());

            blockDTO.setBotJob(botJob);
            blockDTO.setName("Default Block");
            blockDTO.setDescription("Default Block description");

            BlockDetailsDTO newBlockDetails = new BlockDetailsDTO();
            newBlockDetails.setBlockName(blockDTO.getName() + " default block");
            newBlockDetails.setBlockDescription(
                    !Strings.isNullOrEmpty(blockDTO.getDescription())
                            ? blockDTO.getDescription()
                            : blockDTO.getName() + " block description");
            newBlockDetails.setTypeId(1);
            newBlockDetails.setActive(blockDTO.getActive());
            newBlockDetails.setWait(blockDTO.getWait());

            newBlockDetails.setBotJobId(blockDTO.getId());

            int newBlockId = performDatabase.createNewBlock(newBlockDetails);
            ABRLogger.getInstance(Thread.class)
                    .info(String.format("Created a new Block id %d for bot job Id %d", newBlockId, botJob.getId()));
        }

        return new ABRViewBotJobPane(botJobDTO, this);
    }

    @Override
    public Double getSceneHeight() {
        return SCENE_HEIGHT;
    }

    @Override
    public Double getSceneWidth() {
        return SCENE_WIDTH;
    }

    @Override
    public String getTitle() {
        return TITLE;
    }

    public List<BlockLoadDTO> loadBlocksForBotJob(int botJobId) {
        // SQL query to get the blocks for a specific bot job
        String query = "SELECT " + "b.id AS block_id, "
                + "b.block_order_number, "
                + "b.name AS block_name, "
                + "b.description AS block_description, "
                + "b.type_id, "
                + "b.active, "
                + "b.wait, "
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
                    blockDTO.setActive(rs.getBoolean("active"));
                    blockDTO.setWait(rs.getInt("wait"));
                    blockDTO.setBotJobId(rs.getInt("bot_job_id"));
                    blockDTO.setBotJobName(rs.getString("bot_job_name"));

                    blockMap.put(blockId, blockDTO);
                    blockLoadList.add(blockDTO);
                }
            }
        } catch (SQLException e) {
            ABRLogger.getInstance(Thread.class)
                    .severe(String.format("Error loadBlockAll for botJobId %d\nError: %s", botJobId, e.getMessage()));

            Text variableText1Styled = new Text("Web Element \"NAME\" must be defined!");
            variableText1Styled.setStyle("-fx-font-size: 18px; -fx-fill: red;");

            VBox combinedTextContainer = new VBox();
            combinedTextContainer.setSpacing(5); // Add some sp

            combinedTextContainer.getChildren().add(variableText1Styled);

            performAction.showAlertCombinedVBOX(
                    Alert.AlertType.ERROR, "Database", "Define the Element Name", null, combinedTextContainer);
        }

        return blockLoadList;
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
    //                ResultSet rs = stmt.executeQuery(query)) {
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
    //            ABRLogger.getInstance(Thread.class)
    //                    .severe(String.format("Error loadBlockAll for botJobId %d\nError: %s", botJobId,
    // e.getMessage()));
    //        }
    //    }

    // Now you can access currentScene anywhere in this class
    public ABRScene getCurrentScene() {
        return currentScene;
    }
}

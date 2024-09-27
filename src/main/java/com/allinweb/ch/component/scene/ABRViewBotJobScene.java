package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.model.BlockDetailsDTO;
import com.allinweb.ch.component.pane.ABRViewBotJobPane;
import com.allinweb.ch.component.pane.base.IABRPane;
import com.allinweb.ch.component.scene.base.ABRScene;
import com.allinweb.ch.core.ABRSharedResources;
import com.allinweb.ch.persistence.BlockDTO;
import com.allinweb.ch.persistence.BotJobDTO;
import com.allinweb.ch.persistence.SavedBlocksDTO;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;

public class ABRViewBotJobScene extends ABRScene {

    private static final Double SCENE_HEIGHT = 600D;
    private static final Double SCENE_WIDTH = 1100D;
    private static final String TITLE = "Bot Job Details";

    private final Integer botJobId;

    public ABRViewBotJobScene(Integer botJobId) {
        super();
        this.botJobId = botJobId;
    }

    @Override
    public IABRPane buildPane() {

        BotJobDTO botJobDTO = ABRSharedResources.getInstance().getEntityById(BotJobDTO.class, this.botJobId);
        
        
        // It Prevents Start without blocks
        if (botJobDTO.getBlocks() != null && botJobDTO.getBlocks().size() < 1) {
            // It Prevents Start without blocks
            if (botJobDTO.getBlocks() != null && botJobDTO.getBlocks().size() < 1) {
                SavedBlocksDTO savedBlocksDTO = new SavedBlocksDTO();

                savedBlocksDTO.setDescription("Default Block description");
                savedBlocksDTO.setName("Default Block");
                BlockDTO blockDTO =
                        BlockDTO.createBlocksDTOFromSavedBlocksDTO(savedBlocksDTO, botJobDTO);
                BotJobDTO botJob = ABRSharedResources.getInstance()
                        .getEntityById(
                                BotJobDTO.class, botJobDTO.getId());
                blockDTO.setBotJob(botJob);
                blockDTO.setBlockOrderNumber(botJob.getBlocks().size() + 1);

                ABRSharedResources.getInstance().addEntity(blockDTO, BlockDTO.class);
            }
        }
        
        
        return new ABRViewBotJobPane(botJobDTO);
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

    private int saveBlock(BlockDetailsDTO blockDTO) {
        // Generate a Unique-ID for the block
        Integer nextId = loadNextIdBlockData() + 1;

        // Build the SQL insert query
        String insertSQL = "INSERT INTO block(id, block_order_number, description, name, type_id, bot_job_id) VALUES ("
                + nextId + ", "
                + blockDTO.getBlockOrderNumber() + ", " // block_order_number
                + "'" + blockDTO.getBlockName() + " description', " // description
                + "'" + blockDTO.getBlockName() + "', " // name
                + 1 + ", " // type_id
                + blockDTO.getBotJobId() + ")"; // bot_job_id, assuming BotJobDTO has an ID

        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {
            stmt.executeUpdate(insertSQL);
            System.out.println("Block data saved successfully.");
            return nextId;
        } catch (SQLException e) {
            e.printStackTrace();
            return -1;
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

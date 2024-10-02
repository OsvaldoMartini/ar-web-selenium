package com.allinweb.ch.persistence;

import com.allinweb.ch.core.ABRSharedResources;
import java.util.*;
import javax.persistence.*;

@Entity
@Table(name = "block")
@SequenceGenerator(initialValue = 1, name = "idgen", sequenceName = "blockSeq", allocationSize = 1)
public class BlockDTO extends BaseDTO {

    @Column(name = "block_order_number")
    private int blockOrderNumber;

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "type_id")
    private Integer typeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bot_job_id")
    private BotJobDTO botJobDTO;

    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    @OrderBy("instruction_order_number ASC")
    @JoinColumn(name = "block_id")
    private List<BlockLoopInstructionDTO> blockLoopInstructionDTOS = new ArrayList<>();

    public BlockDTO() {
        super();
    }

    public BlockDTO(int id) {
        super(id);
    }

    public BlockDTO(BotJobDTO botJobDTO) {
        super();
        this.botJobDTO = botJobDTO;
    }

    public int getBlockOrderNumber() {
        return blockOrderNumber;
    }

    public void setBlockOrderNumber(int blockOrderNumber) {
        this.blockOrderNumber = blockOrderNumber;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getTypeId() {
        return typeId;
    }

    public void setTypeId(Integer typeId) {
        this.typeId = typeId;
    }

    public BotJobDTO getBotJobDTO() {
        return botJobDTO;
    }

    public void setBotJob(BotJobDTO botJobDTO) {
        this.botJobDTO = botJobDTO;
    }

    public List<BlockLoopInstructionDTO> getBlockLoopInstructions() {
        return ABRSharedResources.getInstance()
                .getEntityList(
                        BlockLoopInstructionDTO.class,
                        instruction -> instruction.getBlock().getId() == this.getId());
    }

    public void setBlockLoopInstructions(List<BlockLoopInstructionDTO> blockLoopInstructionDTOS) {
        this.blockLoopInstructionDTOS = blockLoopInstructionDTOS;
    }

    public static BlockDTO createBlocksDTOFromSavedBlocksDTO(SavedBlocksDTO savedBlocksDTO, BotJobDTO botJobDTO) {
        BlockDTO blocksDTO = new BlockDTO();
        blocksDTO.setName(savedBlocksDTO.getName());
        blocksDTO.setBotJob(botJobDTO);
        blocksDTO.setDescription(savedBlocksDTO.getDescription());
        blocksDTO.setTypeId(savedBlocksDTO.getTypeId());
        return blocksDTO;
    }

    public static Queue<BlockLoopInstructionDTO> createBlockLoopInstructionsFromSavedBlocksDTO(
            SavedBlocksDTO savedBlocksDTO, BlockDTO blockDTO) {
        Queue<BlockLoopInstructionDTO> blockLoopInstructionDTOs = new LinkedList<>();

        BlockLoopInstructionDTO blockLoopInstructionDTO;

        List<SavedBlockLoopInstructionDTO> savedInstructions = ABRSharedResources.getInstance()
                .getEntityList(
                        SavedBlockLoopInstructionDTO.class,
                        saved -> saved.getBlock().getId() == savedBlocksDTO.getId());

        for (SavedBlockLoopInstructionDTO savedBlockLoopInstructionDTO : savedInstructions) {
            blockLoopInstructionDTO = new BlockLoopInstructionDTO();

            blockLoopInstructionDTO.setActionCustomMaxWaitSec(savedBlockLoopInstructionDTO.getActionCustomMaxWaitSec());
            blockLoopInstructionDTO.setActions(savedBlockLoopInstructionDTO.getActions());

            blockLoopInstructionDTO.setBlock(blockDTO);
            blockLoopInstructionDTO.setDefaultValue(savedBlockLoopInstructionDTO.getDefaultValue());
            blockLoopInstructionDTO.setDescription(savedBlockLoopInstructionDTO.getDescription());
            blockLoopInstructionDTO.setEncrypted(savedBlockLoopInstructionDTO.isEncrypted());
            blockLoopInstructionDTO.setExportToABR(savedBlockLoopInstructionDTO.getExportToABR());
            blockLoopInstructionDTO.setInstructionOrderNumber(savedBlockLoopInstructionDTO.getInstructionOrderNumber());
            blockLoopInstructionDTO.setName(savedBlockLoopInstructionDTO.getName());
            blockLoopInstructionDTO.setOnHoldSeconds(savedBlockLoopInstructionDTO.getOnHoldSeconds());
            blockLoopInstructionDTO.setOptional(savedBlockLoopInstructionDTO.isOptional());
            blockLoopInstructionDTO.setPath(savedBlockLoopInstructionDTO.getPath());

            List<InstructionReferenceDTO> referenceDTOList =
                    new ArrayList<>(InstructionReferenceDTO.createReferencesFromSavedInstructionForInstruction(
                            savedBlockLoopInstructionDTO, blockLoopInstructionDTO));
            blockLoopInstructionDTO.setInstructionReferenceDTOList(referenceDTOList);

            blockLoopInstructionDTOs.add(blockLoopInstructionDTO);
        }

        return blockLoopInstructionDTOs;
    }

    public List<BlockLoopInstructionDTO> getBlockLoopInstructionDTOS() {
        return blockLoopInstructionDTOS;
    }

    public void setBlockLoopInstructionDTOS(List<BlockLoopInstructionDTO> blockLoopInstructionDTOS) {
        this.blockLoopInstructionDTOS = blockLoopInstructionDTOS;
    }
}

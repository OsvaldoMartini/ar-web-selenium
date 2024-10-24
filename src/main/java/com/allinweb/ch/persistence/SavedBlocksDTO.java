package com.allinweb.ch.persistence;

import com.allinweb.ch.core.ABRSharedResources;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import javax.persistence.*;
import org.hibernate.annotations.GenericGenerator;

@Entity
@Table(name = "saved_blocks")
@GenericGenerator(name = "idgen", strategy = "native")
public class SavedBlocksDTO extends BaseDTO {

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

    @OneToMany(cascade = CascadeType.ALL)
    @OrderBy("instruction_order_number ASC")
    @JoinColumn(name = "saved_block_id")
    private List<SavedBlockLoopInstructionDTO> savedBlockLoopInstructionDTO = new ArrayList<>();

    public SavedBlocksDTO() {
        super();
    }

    public SavedBlocksDTO(BotJobDTO botJobDTO) {
        super();
        this.botJobDTO = botJobDTO;
    }

    public BotJobDTO getBotJobDTO() {
        return botJobDTO;
    }

    public void setBotJob(BotJobDTO botJobDTO) {
        this.botJobDTO = botJobDTO;
    }

    public SavedBlocksDTO(int id) {
        super(id);
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

    public List<SavedBlockLoopInstructionDTO> getSavedBlockLoopInstructions() {
        return ABRSharedResources.getInstance()
                .getEntityList(
                        SavedBlockLoopInstructionDTO.class,
                        instruction -> instruction.getBlock().getId() == this.getId());
    }

    public void setSavedBlockLoopInstructions(List<SavedBlockLoopInstructionDTO> savedBlockLoopInstructionDTO) {
        this.savedBlockLoopInstructionDTO = savedBlockLoopInstructionDTO;
    }

    public static SavedBlocksDTO createSavedBlocksDTOFromBlocksDTO(BlockDTO blockDTO) {
        SavedBlocksDTO savedBlocksDTO = new SavedBlocksDTO();
        savedBlocksDTO.setName(blockDTO.getName());
        savedBlocksDTO.setDescription(blockDTO.getDescription());
        savedBlocksDTO.setTypeId(blockDTO.getTypeId());

        return savedBlocksDTO;
    }

    public static Queue<SavedBlockLoopInstructionDTO> createSavedBlockLoopInstructionsFromBlocksDTO(
            BlockDTO blockDTO, SavedBlocksDTO savedBlocksDTO) {
        SavedBlockLoopInstructionDTO savedBlockLoopInstructionDTO;
        Queue<SavedBlockLoopInstructionDTO> savedBlockLoopInstructionDTOs = new LinkedList<>();

        List<BlockLoopInstructionDTO> instructionList = ABRSharedResources.getInstance()
                .getEntityList(
                        BlockLoopInstructionDTO.class,
                        instruction -> instruction.getBlock().getId() == blockDTO.getId());

        for (BlockLoopInstructionDTO blockLoopInstructionDTO : instructionList) {
            savedBlockLoopInstructionDTO = new SavedBlockLoopInstructionDTO();

            savedBlockLoopInstructionDTO.setActionCustomMaxWaitSec(blockLoopInstructionDTO.getActionCustomMaxWaitSec());
            savedBlockLoopInstructionDTO.setActions(blockLoopInstructionDTO.getActions());
            savedBlockLoopInstructionDTO.setBlock(savedBlocksDTO);

            savedBlockLoopInstructionDTO.setDefaultValue(blockLoopInstructionDTO.getDefaultValue());
            savedBlockLoopInstructionDTO.setDescription(blockLoopInstructionDTO.getDescription());
            savedBlockLoopInstructionDTO.setEncrypted(blockLoopInstructionDTO.isEncrypted());
            savedBlockLoopInstructionDTO.setExportToABR(blockLoopInstructionDTO.getExportToABR());
            savedBlockLoopInstructionDTO.setInstructionOrderNumber(blockLoopInstructionDTO.getInstructionOrderNumber());
            savedBlockLoopInstructionDTO.setName(blockLoopInstructionDTO.getName());
            savedBlockLoopInstructionDTO.setOnHoldSeconds(blockLoopInstructionDTO.getOnHoldSeconds());
            savedBlockLoopInstructionDTO.setOptional(blockLoopInstructionDTO.isOptional());
            savedBlockLoopInstructionDTO.setPath(blockLoopInstructionDTO.getPath());

            List<SavedInstructionReferenceDTO> referenceDTOList = new ArrayList<>(
                    SavedInstructionReferenceDTO.createSavedReferencesFromInstructionForSavedInstruction(
                            blockLoopInstructionDTO, savedBlockLoopInstructionDTO));
            savedBlockLoopInstructionDTO.setSavedInstructionReferenceDTOList(referenceDTOList);

            savedBlockLoopInstructionDTOs.add(savedBlockLoopInstructionDTO);
        }

        return savedBlockLoopInstructionDTOs;
    }
}

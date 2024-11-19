package com.allinweb.ch.persistence;

import com.allinweb.ch.core.ABRSharedResources;
import java.util.ArrayList;
import java.util.List;
import javax.persistence.*;

@Entity
@Table(name = "saved_blocks")
@SequenceGenerator(initialValue = 1, name = "idgen", sequenceName = "savedBlockSeq", allocationSize = 1)
public class SavedBlocksDTO extends BaseDTO {

    @Column(name = "block_order_number")
    private int blockOrderNumber;

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "type_id")
    private Integer typeId;

    @Column(name = "export_file")
    private String exportFile;

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

    public String getExportFile() {
        return exportFile;
    }

    public void setExportFile(String exportFile) {
        this.exportFile = exportFile;
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
}

package com.allinweb.ch.persistence;

import com.allinweb.ch.core.ARSharedResources;
import java.util.ArrayList;
import java.util.List;
import javax.persistence.*;

@Entity
@Table(name = "component_block")
// @SequenceGenerator(initialValue = 1, name = "idgen", sequenceName = "savedBlockSeq", allocationSize = 1)
public class ComponentBlockDTO extends BaseDTO {

    @Column(name = "home_banking_id")
    private Integer homeBankingId;

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

    @Column(name = "active")
    private Boolean active;

    @Column(name = "wait")
    private Integer wait;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bot_job_id")
    private BotJobDTO botJobDTO;

    @OneToMany(cascade = CascadeType.ALL)
    @OrderBy("instruction_order_number ASC")
    @JoinColumn(name = "component_block_id")
    private List<ComponentInstructionDTO> componentInstructionDTO = new ArrayList<>();

    public Integer getHomeBankingId() {
        return homeBankingId;
    }

    public void setHomeBankingId(Integer homeBankingId) {
        this.homeBankingId = homeBankingId;
    }

    public ComponentBlockDTO() {
        super();
    }

    public ComponentBlockDTO(BotJobDTO botJobDTO) {
        super();
        this.botJobDTO = botJobDTO;
    }

    public BotJobDTO getBotJobDTO() {
        return botJobDTO;
    }

    public void setBotJob(BotJobDTO botJobDTO) {
        this.botJobDTO = botJobDTO;
    }

    public ComponentBlockDTO(int id) {
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

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Integer getWait() {
        return wait;
    }

    public void setWait(Integer wait) {
        this.wait = wait;
    }

    public List<ComponentInstructionDTO> getSavedBlockLoopInstructions() {
        return ARSharedResources.getInstance()
                .getEntityList(
                        ComponentInstructionDTO.class,
                        instruction -> instruction.getBlock().getId() == this.getId());
    }

    public void setSavedBlockLoopInstructions(List<ComponentInstructionDTO> componentInstructionDTO) {
        this.componentInstructionDTO = componentInstructionDTO;
    }
}

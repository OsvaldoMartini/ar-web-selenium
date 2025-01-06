package com.allinweb.ch.persistence;

import java.util.List;
import javax.persistence.*;

/**
 * actions syntax: A -> type of action (in this case 'A' is a placeholder)
 *                  if A == I -> insert -> I:X
 *                                           X -> name of field
 */
@Entity
@Table(name = "saved_block_loop_instruction")
// @SequenceGenerator(initialValue = 1, name = "idgen", sequenceName = "savedBlockLoopInstructionSeq", allocationSize =
// 1)
public class SavedBlockLoopInstructionDTO extends BaseDTO {

    @Column(name = "instruction_order_number")
    private int instructionOrderNumber;

    @Column(name = "actions", length = 1000)
    private String actions;

    @Column(name = "name")
    private String name;

    @Column(name = "path", length = 1000)
    private String path;

    @Column(name = "description")
    private String description;

    @Column(name = "operation", length = 500)
    private String operation;

    @Column(name = "optional")
    private Boolean optional;

    @Column(name = "block_marked")
    private Boolean blockMarked;

    @Column(name = "default_value")
    private String defaultValue;

    @Column(name = "action_custom_max_wait_sec")
    private Integer actionCustomMaxWaitSec;

    @Column(name = "on_hold_seconds")
    private Integer onHoldSeconds;

    @Column(name = "codified")
    private Boolean codified;

    @Column(name = "export_to_abr")
    private Boolean exportToABR;

    @Column(name = "active")
    private Boolean active;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saved_block_id")
    private SavedBlocksDTO savedBlocksDTO;

    @Transient
    private Boolean executed;

    @Transient
    private String priority;

    @Column(name = "variable_id")
    private Integer variableId;

    @Column(name = "parent_id")
    private Integer parentId;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "bot_job_id")
    private BotJobDTO botJobDTO;

    public BotJobDTO getBotJobDTO() {
        return botJobDTO;
    }

    public void setBotJobDTO(BotJobDTO botJobDTO) {
        this.botJobDTO = botJobDTO;
    }

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "saved_block_loop_instruction_id")
    private List<SavedInstructionReferenceDTO> savedInstructionReferenceDTOList;

    public SavedBlockLoopInstructionDTO() {
        super();
    }

    public SavedBlockLoopInstructionDTO(int id) {
        super(id);
    }

    public SavedBlockLoopInstructionDTO(SavedBlocksDTO savedBlocksDTO) {
        super();
        this.savedBlocksDTO = savedBlocksDTO;
    }

    public int getInstructionOrderNumber() {
        return instructionOrderNumber;
    }

    public void setInstructionOrderNumber(int instructionOrderNumber) {
        this.instructionOrderNumber = instructionOrderNumber;
    }

    public Integer getOnHoldSeconds() {
        return onHoldSeconds;
    }

    public void setOnHoldSeconds(Integer onHoldSeconds) {
        this.onHoldSeconds = onHoldSeconds;
    }

    public String getActions() {
        return actions;
    }

    public void setActions(String actions) {
        this.actions = actions;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public SavedBlocksDTO getBlock() {
        return savedBlocksDTO;
    }

    public void setBlock(SavedBlocksDTO savedBlocksDTO) {
        this.savedBlocksDTO = savedBlocksDTO;
    }

    public Integer getActionCustomMaxWaitSec() {
        return actionCustomMaxWaitSec;
    }

    public void setActionCustomMaxWaitSec(Integer actionCustomMaxWaitSec) {
        this.actionCustomMaxWaitSec = actionCustomMaxWaitSec;
    }

    public Boolean getOptional() {
        return optional;
    }

    public void setOptional(Boolean optional) {
        this.optional = optional;
    }

    public Boolean getCodified() {
        return codified;
    }

    public void setCodified(Boolean codified) {
        this.codified = codified;
    }

    public Boolean getExportToABR() {
        return exportToABR;
    }

    public void setExportToABR(Boolean exportToABR) {
        this.exportToABR = exportToABR;
    }

    public Boolean getBlockMarked() {
        return blockMarked;
    }

    public void setBlockMarked(Boolean blockMarked) {
        this.blockMarked = blockMarked;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public List<SavedInstructionReferenceDTO> getSavedInstructionReferenceDTOList() {
        return savedInstructionReferenceDTOList;
    }

    public void setSavedInstructionReferenceDTOList(
            List<SavedInstructionReferenceDTO> savedInstructionReferenceDTOList) {
        this.savedInstructionReferenceDTOList = savedInstructionReferenceDTOList;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public Boolean getExecuted() {
        return executed;
    }

    public void setExecuted(Boolean executed) {
        this.executed = executed;
    }

    public Integer getVariableId() {
        return variableId;
    }

    public void setVariableId(Integer variableId) {
        this.variableId = variableId;
    }

    public Integer getParentId() {
        return parentId;
    }

    public void setParentId(Integer parentId) {
        this.parentId = parentId;
    }
}

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
@SequenceGenerator(initialValue = 1, name = "idgen", sequenceName = "savedBlockLoopInstructionSeq", allocationSize = 1)
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
    private int optional;

    @Column(name = "block_marked")
    private boolean blockMarked;

    @Column(name = "default_val")
    private String default_val;

    @Column(name = "action_custom_max_wait_sec")
    private Integer actionCustomMaxWaitSec;

    @Column(name = "on_hold_seconds")
    private Integer onHoldSeconds;

    @Column(name = "encrypted")
    private Integer encrypted;

    @Column(name = "export_to_abr")
    private Integer exportToABR;

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

    public String getDefaultValue() {
        return default_val;
    }

    public void setDefaultValue(String default_val) {
        this.default_val = default_val;
    }

    public Integer getActionCustomMaxWaitSec() {
        return actionCustomMaxWaitSec;
    }

    public void setActionCustomMaxWaitSec(Integer actionCustomMaxWaitSec) {
        this.actionCustomMaxWaitSec = actionCustomMaxWaitSec;
    }

    public boolean isOptional() {
        return optional >= 1;
    }

    public void setOptional(boolean optional) {
        this.optional = optional ? 1 : 0;
    }

    public void setBlockMarked(boolean isMarked) {
        this.blockMarked = isMarked;
    }

    public boolean isBlockMarked() {
        return blockMarked;
    }

    public boolean isEncrypted() {
        return encrypted >= 1;
    }

    public void setEncrypted(boolean encrypted) {
        this.encrypted = encrypted ? 1 : 0;
    }

    public boolean getExportToABR() {
        return exportToABR >= 1;
    }

    public void setExportToABR(boolean exportToABR) {
        this.exportToABR = exportToABR ? 1 : 0;
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

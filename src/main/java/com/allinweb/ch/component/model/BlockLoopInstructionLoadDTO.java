package com.allinweb.ch.component.model;

import java.util.List;
import lombok.Data;

@Data
public class BlockLoopInstructionLoadDTO {
    private int id;
    private int botJobId;
    private int instructionOrderNumber;
    private String actions;
    private String name;
    private String path;
    private String description;
    private int optional;
    private boolean blockMarked;
    private String default_val;
    private Integer actionCustomMaxWaitSec;
    private Integer onHoldSeconds;
    private Integer encrypted;
    private Integer exportToABR;
    private Boolean executed;
    private String priority;
    private String operation;
    private int parentId;
    private int blockId;
    private int blockOrderNumber;
    private String blockName;
    private boolean editMode = false; // Add an editMode flag

    // Getters and setters for 'editMode'
    public boolean isEditMode() {
        return editMode;
    }

    public void setEditMode(boolean editMode) {
        this.editMode = editMode;
    }

    public BlockLoopInstructionLoadDTO() {}

    // Constructor
    public BlockLoopInstructionLoadDTO(
            int botJobId,
            int id,
            int instructionOrderNumber,
            String name,
            String description,
            int blockId,
            int blockOrderNumber,
            String blockName,
            String actions) {
        this.botJobId = botJobId;
        this.id = id;
        this.instructionOrderNumber = instructionOrderNumber;
        this.name = name;
        this.description = description;
        this.blockId = blockId;
        this.blockOrderNumber = blockOrderNumber;
        this.blockName = blockName;
        this.actions = actions;
    }

    public int getBotJobId() {
        return botJobId;
    }

    public void setBotJobId(int botJobId) {
        this.botJobId = botJobId;
    }

    public String getDefaultValue() {
        return default_val;
    }

    public void setDefaultValue(String default_val) {
        this.default_val = default_val;
    }

    public boolean isOptional() {
        return optional >= 1;
    }

    public boolean isEncrypted() {
        return encrypted >= 1;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public int getParentId() {
        return parentId;
    }

    public void setParentId(int parentId) {
        this.parentId = parentId;
    }

    private List<ComplexInstructionLoadDTO> complexInstructionLoadDTOList;
    private List<InstructionReferenceLoadDTO> instructionReferenceLoadDTOList;

    @Override
    public String toString() {
        return "BlockLoopInstructionLoadDTO{" + "id="
                + id + ", instructionOrderNumber="
                + instructionOrderNumber + ", actions='"
                + actions + '\'' + ", name='"
                + name + '\'' + ", path='"
                + path + '\'' + ", description='"
                + description + '\'' + ", optional="
                + optional + ", blockMarked="
                + blockMarked + ", default_val='"
                + default_val + '\'' + ", actionCustomMaxWaitSec="
                + actionCustomMaxWaitSec + ", onHoldSeconds="
                + onHoldSeconds + ", encrypted="
                + encrypted + ", exportToABR="
                + exportToABR + ", executed="
                + executed + ", priority='"
                + priority + '\'' + ", complexInstructionLoadDTOList="
                + complexInstructionLoadDTOList + ", instructionReferenceLoadDTOList="
                + instructionReferenceLoadDTOList + '}';
    }
}

package com.allinweb.ch.component.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlockLoopInstructionLoadDTO {
    private int id;
    private int botJobId;
    private String botJobName;
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
    private String exportFile;
    private int parentId;
    private int blockId;
    private int blockOrderNumber;
    private String blockName;
    private boolean blockActive;
    private boolean instructionActive;
    private int blockWait;
    private boolean editMode = false; // Add an editMode flag
    private boolean refreshLoop;
    private boolean loopOnly;

    private List<ComplexInstructionLoadDTO> complexInstructionLoadDTOList;
    private List<InstructionReferenceLoadDTO> instructionReferenceLoadDTOList;

    // Custom constructor
    public BlockLoopInstructionLoadDTO(
            int botJobId,
            String botJobName,
            int id,
            int instructionOrderNumber,
            String name,
            String description,
            int blockId,
            int blockOrderNumber,
            String blockName,
            boolean blockActive,
            boolean instructionActive,
            int blockWait,
            String actions,
            int parentId,
            String operation,
            String exportFile) {
        this.botJobId = botJobId;
        this.botJobName = botJobName;
        this.id = id;
        this.instructionOrderNumber = instructionOrderNumber;
        this.name = name;
        this.description = description;
        this.blockId = blockId;
        this.blockOrderNumber = blockOrderNumber;
        this.blockName = blockName;
        this.blockActive = blockActive;
        this.instructionActive = instructionActive;
        this.blockWait = blockWait;
        this.actions = actions;
        this.parentId = parentId;
        this.operation = operation;
        this.exportFile = exportFile;
    }

    public boolean isOptional() {
        return optional >= 1;
    }

    public boolean isEncrypted() {
        return encrypted >= 1;
    }

    public String getDefaultValue() {
        return default_val;
    }
}

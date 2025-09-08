package com.allinweb.ch.component.model;

import java.util.List;
import lombok.Data;

@Data
public class SplitDTO {
    private String type;
    private String sessionId;
    private String operationId;

    private Integer homeBankingId;
    private Integer botJobId;
    private String botJobName;

    private Integer blockId;
    private String blockName;
    private Integer blockOrderNumber;
    private Boolean blockActive;

    private Integer instructionId;
    private String instructionName;
    private Integer instructionOrderNumber;
    private Boolean instructionActive;

    private String actions;
    private String operation;

    private Integer variableId;
    private Integer parentId;
    private Integer parentBlockId;

    private String exportFile;

    // Optional fields for SplitDTO
    private ElementDTO[] elementDetails;

    // Optional fields for BlockSplitDTO
    private DetailsDTO details;

    // Optional fields for UpdtateRows
    private List<UpdatedRow> updatedRows;
    // Optional fields for UpdtateRows
    private List<UpdatedRow> instructions;

    // Optional fields for BlockOrderDetail
    private List<BlockOrderDetailDTO> updatedBlocks;

    public static InstructionLoad mapSplitToInstruction(SplitDTO split) {
        if (split == null) return null;

        InstructionLoad instruction = new InstructionLoad();

        // Map all relevant fields from SplitDTO to InstructionLoad
        instruction.setId(split.getInstructionId());
        instruction.setBotJobId(split.getBotJobId());
        instruction.setHomeBankingId(split.getHomeBankingId());
        instruction.setBlockId(split.getBlockId());
        instruction.setParentBlockId(split.getParentBlockId());
        instruction.setParentId(split.getParentId());
        instruction.setActions(split.getActions());
        instruction.setSessionId(split.getSessionId());
        instruction.setOperation(split.getOperationId());
        instruction.setInstructionOrderNumber(split.getInstructionOrderNumber());
        instruction.setBlockOrderNumber(split.getBlockOrderNumber());
        instruction.setName(split.getInstructionName());
        instruction.setBlockName(split.getBlockName() != null ? split.getBlockName() : split.getBotJobName());

        // Optional element details mapping (if needed)
        if (split.getElementDetails() != null) {
            // map or store element details in a list, depending on your business logic
        }

        // Flags or defaults
        instruction.setInstructionActive(true);

        return instruction;
    }
}

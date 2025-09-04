package com.allinweb.ch.component.model;

import lombok.Data;

@Data
public class SplitDTO {
    private String type;
    private Integer botJobId;
    private Integer homeBankingId;
    private String sessionId;

    private String actions;
    private String operation;

    private Integer instructionId;
    private Integer parentId;
    private Integer parentBlockId;
    private Integer variableId;
    private Integer blockId;
    private Integer instructionOrderNumber;
    private Integer blockOrderNumber;
    private String instructionName;
    private String blockName;

    // Optional fields for SplitDTO
    private String operationId;
    private ElementDTO[] elementDetails;

    // Optional fields for BlockSplitDTO
    private String botJobName;
    private DetailsDTO details;

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
        instruction.setInstructionName(split.getInstructionName());
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

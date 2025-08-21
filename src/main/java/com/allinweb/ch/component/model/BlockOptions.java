package com.allinweb.ch.component.model;

import lombok.Getter;

@Getter
public class BlockOptions {
    private final String text;
    private final String value;
    private final Integer instructionId;
    private final Integer blockId;

    public BlockOptions(String text, String value, Integer instructionId, Integer blockId) {
        this.text = text;
        this.value = value;
        this.instructionId = instructionId;
        this.blockId = blockId;
    }

    // 🔹 Converter from BlockLoadDTO → BlockOptions
    public static BlockOptions fromBlockLoadDTO(BlockLoadDTO block) {
        Integer firstInstructionId = null;

        if (block.getInstructionLoadDTOS() != null
                && !block.getInstructionLoadDTOS().isEmpty()) {
            InstructionLoadDTO firstInstr = block.getInstructionLoadDTOS().get(0);
            firstInstructionId = firstInstr.getId(); // adapt if InstructionLoadDTO has another identifier
        }

        return new BlockOptions(
                block.getName(), // text
                String.valueOf(block.getId()), // value
                firstInstructionId, // instructionId
                block.getId() // blockId
                );
    }
}

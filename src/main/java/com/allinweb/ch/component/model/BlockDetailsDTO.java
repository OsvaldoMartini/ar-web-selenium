package com.allinweb.ch.component.model;

import java.util.List;

public class BlockDetailsDTO {
    private long blockId;
    private String blockName;
    private int blockOrderNumber;
    private int botJobId;
    private List<InstructionDTO> updatedInstructions; // For originalBlock
    private List<InstructionDTO> instructions; // For newBlock

    // Getters and Setters
    public long getBlockId() {
        return blockId;
    }

    public void setBlockId(long blockId) {
        this.blockId = blockId;
    }

    public String getBlockName() {
        return blockName;
    }

    public void setBlockName(String blockName) {
        this.blockName = blockName;
    }

    public int getBlockOrderNumber() {
        return blockOrderNumber;
    }

    public void setBlockOrderNumber(int blockOrderNumber) {
        this.blockOrderNumber = blockOrderNumber;
    }

    public int getBotJobId() {
        return botJobId;
    }

    public void setBotJobId(int botJobId) {
        this.botJobId = botJobId;
    }

    public List<InstructionDTO> getUpdatedInstructions() {
        return updatedInstructions;
    }

    public void setUpdatedInstructions(List<InstructionDTO> updatedInstructions) {
        this.updatedInstructions = updatedInstructions;
    }

    public List<InstructionDTO> getInstructions() {
        return instructions;
    }

    public void setInstructions(List<InstructionDTO> instructions) {
        this.instructions = instructions;
    }
}

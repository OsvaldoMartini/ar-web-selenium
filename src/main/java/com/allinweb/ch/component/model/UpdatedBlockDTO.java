package com.allinweb.ch.component.model;

public class UpdatedBlockDTO {
    private long blockId;
    private String blockName;
    private int blockOrderNumber;

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
}

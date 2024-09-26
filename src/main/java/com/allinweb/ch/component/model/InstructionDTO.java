package com.allinweb.ch.component.model;

public class InstructionDTO {
    private long instructionId;
    private long blockId;
    private int orderNumber;

    // Getters and Setters
    public long getInstructionId() {
        return instructionId;
    }

    public void setInstructionId(long instructionId) {
        this.instructionId = instructionId;
    }

    public long getBlockId() {
        return blockId;
    }

    public void setBlockId(long blockId) {
        this.blockId = blockId;
    }

    public int getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(int orderNumber) {
        this.orderNumber = orderNumber;
    }
}

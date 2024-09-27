package com.allinweb.ch.component.model;

public class DeleteInstructionDTO {
    private String type;
    private long instructionId;
    private long botJobId;
    private long blockId;

    // Getters and Setters
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public long getInstructionId() {
        return instructionId;
    }

    public void setInstructionId(long instructionId) {
        this.instructionId = instructionId;
    }

    public long getBotJobId() {
        return botJobId;
    }

    public void setBotJobId(long botJobId) {
        this.botJobId = botJobId;
    }

    public long getBlockId() {
        return blockId;
    }

    public void setBlockId(long blockId) {
        this.blockId = blockId;
    }
}

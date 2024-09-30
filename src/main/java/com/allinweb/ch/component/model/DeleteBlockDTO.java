package com.allinweb.ch.component.model;

public class DeleteBlockDTO {
    private String type;
    private long blockId;
    private long botJobId;

    // Getters and Setters
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public long getBlockId() {
        return blockId;
    }

    public void setBlockId(long blockId) {
        this.blockId = blockId;
    }

    public long getBotJobId() {
        return botJobId;
    }

    public void setBotJobId(long botJobId) {
        this.botJobId = botJobId;
    }
}

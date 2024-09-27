package com.allinweb.ch.component.model;

public class BlockOrderDetailDTO {
    private Integer botJobId;
    private Integer blockId;
    private Integer blockOrderNumber;

    // Constructors
    public BlockOrderDetailDTO() {}

    public BlockOrderDetailDTO(Integer botJobId, Integer blockId, Integer blockOrderNumber) {
        this.botJobId = botJobId;
        this.blockId = blockId;
        this.blockOrderNumber = blockOrderNumber;
    }

    // Getters and Setters
    public Integer getBotJobId() {
        return botJobId;
    }

    public void setBotJobId(Integer botJobId) {
        this.botJobId = botJobId;
    }

    public Integer getBlockId() {
        return blockId;
    }

    public void setBlockId(Integer blockId) {
        this.blockId = blockId;
    }

    public Integer getBlockOrderNumber() {
        return blockOrderNumber;
    }

    public void setBlockOrderNumber(Integer blockOrderNumber) {
        this.blockOrderNumber = blockOrderNumber;
    }

    // toString method for debugging
    @Override
    public String toString() {
        return "BlockDTO{" + "botJobId="
                + botJobId + ", blockId="
                + blockId + ", blockOrderNumber="
                + blockOrderNumber + '}';
    }
}

package com.allinweb.ch.component.model;

import java.util.List;

public class BlockOrderDTO {
    private String type;
    private List<BlockOrderDetailDTO> blocks;

    // Constructors
    public BlockOrderDTO() {}

    public BlockOrderDTO(String type, List<BlockOrderDetailDTO> blocks) {
        this.type = type;
        this.blocks = blocks;
    }

    // Getters and Setters
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<BlockOrderDetailDTO> getBlocks() {
        return blocks;
    }

    public void setBlocks(List<BlockOrderDetailDTO> blocks) {
        this.blocks = blocks;
    }

    // toString method for debugging
    @Override
    public String toString() {
        return "BlockOrderDTO{" + "type='" + type + '\'' + ", blocks=" + blocks + '}';
    }
}

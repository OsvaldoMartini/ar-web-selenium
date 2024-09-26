package com.allinweb.ch.component.model;

import java.util.List;

public class DetailsDTO {
    private BlockDetailsDTO originalBlock;
    private BlockDetailsDTO newBlock;
    private List<UpdatedBlockDTO> updatedBlocks;

    // Getters and Setters
    public BlockDetailsDTO getOriginalBlock() {
        return originalBlock;
    }

    public void setOriginalBlock(BlockDetailsDTO originalBlock) {
        this.originalBlock = originalBlock;
    }

    public BlockDetailsDTO getNewBlock() {
        return newBlock;
    }

    public void setNewBlock(BlockDetailsDTO newBlock) {
        this.newBlock = newBlock;
    }

    public List<UpdatedBlockDTO> getUpdatedBlocks() {
        return updatedBlocks;
    }

    public void setUpdatedBlocks(List<UpdatedBlockDTO> updatedBlocks) {
        this.updatedBlocks = updatedBlocks;
    }
}

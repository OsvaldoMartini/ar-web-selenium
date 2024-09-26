package com.allinweb.ch.component.model;

public class BlockMoveDTO {
    private String type;
    private BlocksDTO blocks;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public BlocksDTO getBlocks() {
        return blocks;
    }

    public void setBlocks(BlocksDTO blocks) {
        this.blocks = blocks;
    }

    public static class BlocksDTO {
        private BlockDTO currentBlock;
        private BlockDTO nextBlock; // For nextBlock scenario
        private BlockDTO previousBlock; // For previousBlock scenario

        // Getters and Setters
        public BlockDTO getCurrentBlock() {
            return currentBlock;
        }

        public void setCurrentBlock(BlockDTO currentBlock) {
            this.currentBlock = currentBlock;
        }

        public BlockDTO getNextBlock() {
            return nextBlock;
        }

        public void setNextBlock(BlockDTO nextBlock) {
            this.nextBlock = nextBlock;
        }

        public BlockDTO getPreviousBlock() {
            return previousBlock;
        }

        public void setPreviousBlock(BlockDTO previousBlock) {
            this.previousBlock = previousBlock;
        }
    }

    public static class BlockDTO {
        private long blockId;
        private int newOrderNumber;

        // Getters and Setters
        public long getBlockId() {
            return blockId;
        }

        public void setBlockId(long blockId) {
            this.blockId = blockId;
        }

        public int getNewOrderNumber() {
            return newOrderNumber;
        }

        public void setNewOrderNumber(int newOrderNumber) {
            this.newOrderNumber = newOrderNumber;
        }
    }
}

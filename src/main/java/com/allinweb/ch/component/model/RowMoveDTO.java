package com.allinweb.ch.component.model;

public class RowMoveDTO {
    private String type;
    private RowsDTO rows;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public RowsDTO getRows() {
        return rows;
    }

    public void setRows(RowsDTO rows) {
        this.rows = rows;
    }

    public static class RowsDTO {
        private long blockId;
        private RowDTO currentRow;
        private RowDTO nextRow; // For nextRow scenario
        private RowDTO previousRow; // For previousRow scenario

        // Getters and Setters
        public long getBlockId() {
            return blockId;
        }

        public void setBlockId(long blockId) {
            this.blockId = blockId;
        }

        public RowDTO getCurrentRow() {
            return currentRow;
        }

        public void setCurrentRow(RowDTO currentRow) {
            this.currentRow = currentRow;
        }

        public RowDTO getNextRow() {
            return nextRow;
        }

        public void setNextRow(RowDTO nextRow) {
            this.nextRow = nextRow;
        }

        public RowDTO getPreviousRow() {
            return previousRow;
        }

        public void setPreviousRow(RowDTO previousRow) {
            this.previousRow = previousRow;
        }
    }

    public static class RowDTO {
        private long instructionId;
        private int newOrderNumber;

        // Getters and Setters
        public long getInstructionId() {
            return instructionId;
        }

        public void setInstructionId(long instructionId) {
            this.instructionId = instructionId;
        }

        public int getNewOrderNumber() {
            return newOrderNumber;
        }

        public void setNewOrderNumber(int newOrderNumber) {
            this.newOrderNumber = newOrderNumber;
        }
    }
}

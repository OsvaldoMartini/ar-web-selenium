package com.allinweb.ch.component.model;

public class DeleteInstructionDTO {
    private String type;
    private long instructionId;

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
}

package com.allinweb.ch.component.model;

import lombok.Data;

@Data
public class InstructionDTO {
    private String type;
    private int botJobId;
    private int instructionId;
    private int blockId;
    private int blockOrderNumber;
    private int instructionOrderNumber;
    private String instructionName;
}

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
    private String description;
    private String path;
    private String operation;
    private String actions;
    private int parentId;
    private int optional;
    private int actionCustomMaxWaitSec;
    private int onHoldSeconds;
    private int encrypted;
    private int exportToABR;
}

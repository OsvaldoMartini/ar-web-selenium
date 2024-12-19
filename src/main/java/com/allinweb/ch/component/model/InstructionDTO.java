package com.allinweb.ch.component.model;

import lombok.Data;

@Data
public class InstructionDTO {
    private String type;
    private Integer botJobId;
    private Integer instructionId;
    private Boolean instructionActive;
    private Integer blockId;
    private Integer blockOrderNumber;
    private Integer instructionOrderNumber;
    private String instructionName;
    private String description;
    private String path;
    private String operation;
    private String actions;
    private Integer parentId;
    private Integer optional;
    private Integer actionCustomMaxWaitSec;
    private Integer onHoldSeconds;
    private Integer encrypted;
    private Integer exportToABR;
}

package com.allinweb.ch.component.model;

import lombok.Data;

@Data
public class InstructionDTO {
    private Integer instructionId;
    private Integer blockId;
    private Integer blockOrderNumber;
    private Integer instructionOrderNumber;
}

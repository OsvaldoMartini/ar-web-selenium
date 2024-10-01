package com.allinweb.ch.component.model;

import lombok.Data;

import java.util.List;

@Data
public class RowMoveDTO {
    private String type;
    private List<InstructionDTO> updatedRows;
}

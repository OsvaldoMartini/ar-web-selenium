package com.allinweb.ch.component.model;

import java.util.List;
import lombok.Data;

@Data
public class RowMoveDTO {
    private String type;
    private List<InstructionDTO> updatedRows;
}

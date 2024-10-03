package com.allinweb.ch.component.model;

import java.util.List;
import lombok.Data;

@Data
public class RowMoveDTO {
    private String type;
    private int botJobId;
    private List<InstructionDTO> updatedRows;
}

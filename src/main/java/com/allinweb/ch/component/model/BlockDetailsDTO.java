package com.allinweb.ch.component.model;

import java.util.List;
import lombok.Data;

@Data
public class BlockDetailsDTO {
    private int blockId;
    private String blockName;
    private int blockOrderNumber;
    private int botJobId;
    private String exportFile;
    private List<InstructionDTO> updatedInstructions; // For originalBlock
    private List<InstructionDTO> instructions; // For newBlock
}

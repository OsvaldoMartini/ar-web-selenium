package com.allinweb.ch.facade;

import com.allinweb.ch.model.InstructionLoad;
import java.util.List;

/** Loads scanner instructions for a block and returns the next order number. */
public final class ScannerInstructionOrderService {
    private static final String INSTRUCTION_TABLE = "instruction";

    private final DataPort data;

    public ScannerInstructionOrderService(DataPort data) {
        this.data = data;
    }

    public int nextOrder(int botJobId, int blockId) {
        data.loadInstructions(botJobId, blockId, -1, INSTRUCTION_TABLE);
        List<InstructionLoad> instructions = data.instructions();
        return instructions.size() + 1;
    }

    public interface DataPort {
        void loadInstructions(int botJobId, int blockId, int instructionId, String tableName);

        List<InstructionLoad> instructions();
    }
}

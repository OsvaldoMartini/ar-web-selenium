package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.allinweb.ch.model.InstructionLoad;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerInstructionOrderServiceTest {

    @Test
    void loadsBlockInstructionsAndReturnsNextOrder() {
        RecordingData data = new RecordingData();
        data.instructions.add(new InstructionLoad());
        data.instructions.add(new InstructionLoad());
        ScannerInstructionOrderService service = new ScannerInstructionOrderService(data);

        int nextOrder = service.nextOrder(42, 7);

        assertEquals(3, nextOrder);
        assertEquals("load:42:7:-1:instruction", data.loadCall);
    }

    @Test
    void returnsOneWhenTheBlockHasNoInstructions() {
        RecordingData data = new RecordingData();
        ScannerInstructionOrderService service = new ScannerInstructionOrderService(data);

        int nextOrder = service.nextOrder(42, 7);

        assertEquals(1, nextOrder);
    }

    private static final class RecordingData implements ScannerInstructionOrderService.DataPort {
        private final List<InstructionLoad> instructions = new ArrayList<>();
        private String loadCall;

        @Override
        public void loadInstructions(int botJobId, int blockId, int instructionId, String tableName) {
            loadCall = "load:" + botJobId + ":" + blockId + ":" + instructionId + ":" + tableName;
        }

        @Override
        public List<InstructionLoad> instructions() {
            return instructions;
        }
    }
}

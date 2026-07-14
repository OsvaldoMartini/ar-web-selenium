package com.allinweb.ch.component.pane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.BlockLoadDTO;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerCreateBlockPlannerTest {

    @Test
    void computeInsertOrderNumberAppendsAfterMaxOrderForAtEndOrNull() {
        ScannerCreateBlockPlanner planner = new ScannerCreateBlockPlanner();
        List<BlockLoadDTO> blocks = List.of(block(1, "Login"), block(3, "Submit"), block(null, "No order"));

        assertEquals(4, planner.computeInsertOrderNumber("At end", blocks));
        assertEquals(4, planner.computeInsertOrderNumber(null, blocks));
    }

    @Test
    void computeInsertOrderNumberParsesBeforePositionLabel() {
        ScannerCreateBlockPlanner planner = new ScannerCreateBlockPlanner();
        List<BlockLoadDTO> blocks = List.of(block(1, "Login"), block(2, "Confirm"));

        assertEquals(2, planner.computeInsertOrderNumber("Before 2# Confirm", blocks));
    }

    @Test
    void computeInsertOrderNumberFallsBackToListSizeForInvalidBeforeLabel() {
        ScannerCreateBlockPlanner planner = new ScannerCreateBlockPlanner();
        List<BlockLoadDTO> blocks = List.of(block(10, "Login"), block(20, "Confirm"));

        assertEquals(3, planner.computeInsertOrderNumber("Before bad# Confirm", blocks));
    }

    @Test
    void buildCreateBlockPreviewReportsNoShiftWhenAppending() {
        ScannerCreateBlockPlanner planner = new ScannerCreateBlockPlanner();

        assertEquals(
                "New block will be #3. No existing blocks are affected.",
                planner.buildCreateBlockPreview(3, List.of(block(1, "Login"), block(2, "Confirm"))));
    }

    @Test
    void buildCreateBlockPreviewListsShiftedBlocks() {
        ScannerCreateBlockPlanner planner = new ScannerCreateBlockPlanner();

        String preview = planner.buildCreateBlockPreview(
                2, List.of(block(1, "Login"), block(2, "Confirm"), block(3, "Logout")));

        assertTrue(preview.startsWith("New block will be #2. Existing blocks will shift down by one:"));
        assertTrue(preview.contains("2# Confirm"));
        assertTrue(preview.contains("3# Confirm"));
        assertTrue(preview.contains("3# Logout"));
        assertTrue(preview.contains("4# Logout"));
    }

    private static BlockLoadDTO block(Integer order, String name) {
        BlockLoadDTO block = new BlockLoadDTO();
        block.setBlockOrderNumber(order);
        block.setName(name);
        return block;
    }
}

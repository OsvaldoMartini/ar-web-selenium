package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.BlockLoadDTO;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerCreateBlockPlannerTest {

    @Test
    void computeInsertOrderNumberAppendsAfterMaxOrderForAtEndOrNull() {
        ScannerCreateBlockPlanner planner = new ScannerCreateBlockPlanner();
        List<BlockLoadDTO> blocks =
                List.of(block(null, 1, "Login"), block(null, 3, "Submit"), block(null, null, "No order"));

        assertEquals(4, planner.computeInsertOrderNumber("At end", blocks));
        assertEquals(4, planner.computeInsertOrderNumber(null, blocks));
    }

    @Test
    void computeInsertOrderNumberParsesBeforePositionLabel() {
        ScannerCreateBlockPlanner planner = new ScannerCreateBlockPlanner();
        List<BlockLoadDTO> blocks = List.of(block(null, 1, "Login"), block(null, 2, "Confirm"));

        assertEquals(2, planner.computeInsertOrderNumber("Before 2# Confirm", blocks));
    }

    @Test
    void computeInsertOrderNumberFallsBackToListSizeForInvalidBeforeLabel() {
        ScannerCreateBlockPlanner planner = new ScannerCreateBlockPlanner();
        List<BlockLoadDTO> blocks = List.of(block(null, 10, "Login"), block(null, 20, "Confirm"));

        assertEquals(3, planner.computeInsertOrderNumber("Before bad# Confirm", blocks));
    }

    @Test
    void buildCreateBlockPreviewReportsNoShiftWhenAppending() {
        ScannerCreateBlockPlanner planner = new ScannerCreateBlockPlanner();

        assertEquals(
                "New block will be #3. No existing blocks are affected.",
                planner.buildCreateBlockPreview(3, List.of(block(null, 1, "Login"), block(null, 2, "Confirm"))));
    }

    @Test
    void buildCreateBlockPreviewListsShiftedBlocks() {
        ScannerCreateBlockPlanner planner = new ScannerCreateBlockPlanner();

        String preview = planner.buildCreateBlockPreview(
                2,
                List.of(
                        block(null, 1, "Login"),
                        block(null, 2, "Confirm"),
                        block(null, 3, "Logout")));

        assertTrue(preview.startsWith("New block will be #2. Existing blocks will shift down by one:"));
        assertTrue(preview.contains("2# Confirm"));
        assertTrue(preview.contains("3# Confirm"));
        assertTrue(preview.contains("3# Logout"));
        assertTrue(preview.contains("4# Logout"));
    }

    @Test
    void buildRenumberPlanShiftsOnlyMatchingBotJobAtOrAfterTargetOrder() {
        ScannerCreateBlockPlanner planner = new ScannerCreateBlockPlanner();

        List<BlockLoadDTO> plan = planner.buildRenumberPlan(
                7,
                2,
                List.of(
                        block(7, 1, "Keep"),
                        block(7, 2, "Shift one"),
                        block(7, 4, "Shift two"),
                        block(8, 2, "Other job"),
                        block(7, null, "No order")));

        assertEquals(2, plan.size());
        assertEquals(3, plan.get(0).getBlockOrderNumber());
        assertEquals(5, plan.get(1).getBlockOrderNumber());
        assertEquals(7, plan.get(0).getBotJobId());
    }

    private static BlockLoadDTO block(Integer botJobId, Integer order, String name) {
        BlockLoadDTO block = new BlockLoadDTO();
        block.setId(order == null ? null : order * 10);
        block.setBotJobId(botJobId);
        block.setHomeBankingId(100 + (botJobId == null ? 0 : botJobId));
        block.setBlockOrderNumber(order);
        block.setName(name);
        return block;
    }
}

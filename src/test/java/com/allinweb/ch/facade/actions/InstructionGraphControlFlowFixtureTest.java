package com.allinweb.ch.facade.actions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.FieldData;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.util.ARConstantsEngine;
import com.allinweb.ch.util.ARExecution;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Synthetic-fixture coverage for {@link InstructionGraph}'s control-flow helpers: IF/ELSEIF/ELSE/ENDIF
 * jump resolution and LOOP/output bookkeeping. These are the "commands" that do not exist anywhere in
 * the real BancaStato production database (see {@code CLAUDE_vs_CODEX_MIGRATION_CHECKS_2026_07_12.md}
 * — grepping the whole production DB for IF/ELSE/ENDIF/GOTO returned zero rows), so real-data Playwright
 * coverage (see {@code BancaStatoAperturaContoAllBlocksPlaywrightIT}) cannot exercise them. This class
 * fills that gap with fabricated {@link InstructionLoad}/{@link BlockLoadDTO} fixtures, mirroring the
 * existing {@link GotoExecutionRoutingTest} pattern.
 *
 * <p><b>Why this does not test {@code ScannerRuntimeBackend.executeJob()} directly:</b> that method (and
 * its {@code mapLoops}/{@code jumpGoto} dispatch, where the forward-GOTO NPE tracked as finding C-1
 * lives) still runs through the legacy scanner runtime host and eagerly loads browser/job singletons.
 * Touching that host from a small unit fixture would pull in a full scanner lifecycle instead of only
 * the control-flow graph logic. Until that extraction is finished, the block/row control-flow behavior
 * is only exercisable through these pure {@link InstructionGraph} helpers plus the real end-to-end
 * Playwright integration tests. */
class InstructionGraphControlFlowFixtureTest {

    @Test
    void testRunAcceptsRootInstructionsWithoutAParentId() {
        InstructionLoad root = new InstructionLoad();
        InstructionLoad nested = new InstructionLoad();
        nested.setParentId(42);

        assertEquals(0, InstructionGraph.executionParentId(root));
        assertEquals(42, InstructionGraph.executionParentId(nested));
    }

    @Test
    void resolvesEndifIndexForElseAfterIf() {
        BlockLoadDTO block = ifElseEndifBlock();
        Map<String, List<Integer>> conditionalMap = InstructionGraph.getConditionIndexMapByParentId(block);

        int endifIndex = InstructionGraph.searchMapConditional(
                conditionalMap, 100, ARExecution.ConditionStatus.ENDIF, 0, false);

        assertEquals(4, endifIndex, "ENDIF for parent 100 should be found at its fixture index");
    }

    @Test
    void checkActionToJumpElseDelegatesToEndifSearch() {
        BlockLoadDTO block = ifElseEndifBlock();
        Map<String, List<Integer>> conditionalMap = InstructionGraph.getConditionIndexMapByParentId(block);

        int jumpIndex = InstructionGraph.checkActionToJump(
                ARConstantsEngine.ELSE, ARExecution.ConditionStatus.IF_FAILED, conditionalMap, 100, 0);

        assertEquals(4, jumpIndex, "ELSE dispatch must jump to the same ENDIF index as a direct search");
    }

    @Test
    void checkActionToJumpElseifDelegatesToEndifSearch() {
        BlockLoadDTO block = ifElseEndifBlock();
        Map<String, List<Integer>> conditionalMap = InstructionGraph.getConditionIndexMapByParentId(block);

        int jumpIndex = InstructionGraph.checkActionToJump(
                ARConstantsEngine.ELSEIF, ARExecution.ConditionStatus.IF_FAILED, conditionalMap, 100, 0);

        assertEquals(4, jumpIndex, "ELSEIF dispatch must jump to the same ENDIF index as a direct search");
    }

    @Test
    void checkActionToJumpReturnsZeroForNonBranchingActions() {
        Map<String, List<Integer>> conditionalMap = InstructionGraph.getConditionIndexMapByParentId(ifElseEndifBlock());

        int jumpIndex = InstructionGraph.checkActionToJump(
                ARConstantsEngine.CLICK, ARExecution.ConditionStatus.NONE, conditionalMap, 100, 0);

        assertEquals(0, jumpIndex, "A plain CLICK is not a jump action and must not move the cursor");
    }

    @Test
    void searchMapConditionalReturnsMinusOneWhenNoConditionMatchesAndSuppressesTheDialog() {
        Map<String, List<Integer>> conditionalMap = InstructionGraph.getConditionIndexMapByParentId(ifElseEndifBlock());

        // parentBlockCondition 999 does not exist in the fixture; showMessage=false keeps this
        // as a pure graph test and avoids presentation alerts.
        int result = InstructionGraph.searchMapConditional(
                conditionalMap, 999, ARExecution.ConditionStatus.ENDIF, 0, false);

        assertEquals(-1, result, "An unmatched parent/condition pair must report -1, not throw or dialog");
    }

    @Test
    void getConditionIndexMapByParentIdIgnoresNonBranchingActions() {
        BlockLoadDTO block = ifElseEndifBlock();

        Map<String, List<Integer>> conditionalMap = InstructionGraph.getConditionIndexMapByParentId(block);

        assertEquals(
                Set.of("100-IF", "100-ELSE", "100-ENDIF"),
                conditionalMap.keySet(),
                "Only IF/ELSEIF/ELSE/ENDIF rows contribute keys; CLICK/INSERT rows must be excluded");
    }

    @Test
    void loopOperationIsParsedIntoRepeatAndRefreshBounds() {
        InstructionLoad loop = InstructionLoad.builder()
                .id(500)
                .parentId(100)
                .actions(ARConstantsEngine.LOOP)
                .operation("3:0")
                .build();
        InstructionLoad refreshLoop = InstructionLoad.builder()
                .id(501)
                .parentId(200)
                .actions("REFRESH_LOOP")
                .operation("2:1")
                .build();
        InstructionLoad notALoop = InstructionLoad.builder()
                .id(502)
                .parentId(100)
                .actions(ARConstantsEngine.CLICK)
                .operation("9:9")
                .build();

        Map<String, Integer[]> loops =
                InstructionGraph.getLoopAndRefreshLoops(List.of(loop, refreshLoop, notALoop));

        assertEquals(2, loops.size(), "Only LOOP/REFRESH_LOOP rows produce an entry");
        assertTrue(java.util.Arrays.equals(new Integer[] {3, 0}, loops.get("500")));
        assertTrue(java.util.Arrays.equals(new Integer[] {2, 1}, loops.get("501")));
    }

    @Test
    void loopOperationWithEmptyOperationYieldsEmptyBoundsInsteadOfThrowing() {
        InstructionLoad loop =
                InstructionLoad.builder().id(600).actions(ARConstantsEngine.LOOP).operation("").build();

        Map<String, Integer[]> loops = InstructionGraph.getLoopAndRefreshLoops(List.of(loop));

        assertEquals(0, loops.get("600").length, "An empty operation string must yield an empty bounds array");
    }

    @Test
    void getParentIdsForLoopCollectsOnlyLoopAndRefreshLoopParents() {
        InstructionLoad loop = InstructionLoad.builder()
                .id(700)
                .parentId(11)
                .actions(ARConstantsEngine.LOOP)
                .build();
        InstructionLoad refreshLoop = InstructionLoad.builder()
                .id(701)
                .parentId(22)
                .actions("REFRESH_LOOP")
                .build();
        InstructionLoad plainClick = InstructionLoad.builder()
                .id(702)
                .parentId(33)
                .actions(ARConstantsEngine.CLICK)
                .build();

        Set<Integer> parentIds = InstructionGraph.getParentIdsForLoop(List.of(loop, refreshLoop, plainClick));

        assertEquals(Set.of(11, 22), parentIds, "Only LOOP/REFRESH_LOOP rows contribute a parent id");
    }

    @Test
    void getAllOutputsPerBlockMatchesOnlyOutputPrefixedActions() {
        InstructionLoad output = InstructionLoad.builder()
                .id(800)
                .actions("O:Saldo")
                .build();
        InstructionLoad insert = InstructionLoad.builder()
                .id(801)
                .actions("I:User number")
                .build();

        Set<Integer> outputs = InstructionGraph.getAllOutputsPerBlock(List.of(output, insert));

        assertEquals(Set.of(800), outputs, "Only actions starting with \"O:\" are outputs");
    }

    @Test
    void gotoTargetIndexHandlesBackwardTargetsAndMalformedKeys() {
        // Backward GOTO (target block order 1, i.e. index 0) — the counterpart to the forward-GOTO
        // case already covered by GotoExecutionRoutingTest.
        assertEquals(0, InstructionGraph.gotoTargetIndex(new FieldData("77:12:1:Back to start", "2")));

        // Fewer than 3 ":"-separated segments must report -1, not throw ArrayIndexOutOfBounds.
        assertEquals(-1, InstructionGraph.gotoTargetIndex(new FieldData("77:12", "2")));

        // Non-numeric order segment must report -1, not throw NumberFormatException.
        assertEquals(-1, InstructionGraph.gotoTargetIndex(new FieldData("77:12:NaN:Broken", "2")));
    }

    @Test
    void getBlockOrderNumberResolvesAndFallsBackToMinusOne() {
        BlockLoadDTO block = new BlockLoadDTO();
        block.setId(9);
        block.setBlockOrderNumber(4);

        assertEquals(4, InstructionGraph.getBlockOrderNumber(List.of(block), 9));
        assertEquals(-1, InstructionGraph.getBlockOrderNumber(List.of(block), 12345), "Unknown block id must fall back to -1");
    }

    /**
     * IF (index 0, parentId 100) / CLICK under IF (index 1) / ELSE (index 2, parentId 100) / CLICK
     * under ELSE (index 3) / ENDIF (index 4, parentId 100) / CLICK after ENDIF (index 5).
     */
    private static BlockLoadDTO ifElseEndifBlock() {
        InstructionLoad ifRow =
                InstructionLoad.builder().id(1).parentId(100).actions(ARConstantsEngine.IF).build();
        InstructionLoad underIf = InstructionLoad.builder()
                .id(2)
                .parentId(1)
                .actions(ARConstantsEngine.CLICK)
                .build();
        InstructionLoad elseRow =
                InstructionLoad.builder().id(3).parentId(100).actions(ARConstantsEngine.ELSE).build();
        InstructionLoad underElse = InstructionLoad.builder()
                .id(4)
                .parentId(3)
                .actions(ARConstantsEngine.CLICK)
                .build();
        InstructionLoad endifRow =
                InstructionLoad.builder().id(5).parentId(100).actions(ARConstantsEngine.ENDIF).build();
        InstructionLoad afterEndif = InstructionLoad.builder()
                .id(6)
                .parentId(5)
                .actions(ARConstantsEngine.CLICK)
                .build();

        BlockLoadDTO block = new BlockLoadDTO();
        block.setId(100);
        block.setBlockOrderNumber(1);
        block.setName("IF/ELSE/ENDIF fixture");
        block.setInstructionLoad(List.of(ifRow, underIf, elseRow, underElse, endifRow, afterEndif));
        return block;
    }
}

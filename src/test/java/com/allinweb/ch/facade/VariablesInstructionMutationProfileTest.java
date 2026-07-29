package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.allinweb.ch.facade.BotJobGraphMutationTransaction.GraphInstructionFact;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.GraphSnapshot;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.MutationRefusedException;
import com.allinweb.ch.model.InstructionGraphMutationV3;
import com.allinweb.ch.model.InstructionGraphMutationV3.LayoutRow;
import java.util.List;
import org.junit.jupiter.api.Test;

class VariablesInstructionMutationProfileTest {

    private final VariablesInstructionMutationProfile profile =
            new VariablesInstructionMutationProfile();

    @Test
    void acceptsOneEligibleCommandReinsertedAcrossOrdinaryRows() throws Exception {
        profile.validate(
                request(
                        102,
                        List.of(
                                row(101, 10, 1),
                                row(103, 10, 2),
                                row(102, 10, 3),
                                row(104, 10, 4),
                                row(105, 10, 5),
                                row(106, 10, 6))),
                snapshot());
    }

    @Test
    void refusesAnyRelationshipOrVariablePatch() {
        InstructionGraphMutationV3.Request candidate =
                new InstructionGraphMutationV3.Request(
                        InstructionGraphMutationV3.CONTRACT_VERSION,
                        InstructionGraphMutationV3.MutationKind.ROW_MOVE,
                        "variables-move",
                        4L,
                        "revision",
                        10L,
                        owner(),
                        102,
                        List.of(
                                row(101, 10, 1),
                                row(103, 10, 2),
                                row(102, 10, 3),
                                row(104, 10, 4),
                                row(105, 10, 5),
                                row(106, 10, 6)),
                        List.of(new InstructionGraphMutationV3.InstructionRelationPatch(
                                102,
                                InstructionGraphMutationV3.InstructionRelationKind.ELEMENT_TARGET,
                                InstructionGraphMutationV3.PatchOperation.KEEP,
                                new InstructionGraphMutationV3.InstructionRelationState(101, 10),
                                new InstructionGraphMutationV3.InstructionRelationState(101, 10))),
                        List.of(),
                        List.of());

        assertRefused("VARIABLES_PATCH_NOT_ALLOWED", candidate);
    }

    @Test
    void refusesCrossBlockMove() {
        assertRefused(
                "VARIABLES_CROSS_BLOCK_NOT_READY",
                request(
                        102,
                        List.of(
                                row(101, 10, 1),
                                row(103, 10, 2),
                                row(104, 10, 3),
                                row(105, 10, 4),
                                row(106, 10, 5),
                                new LayoutRow(102, 11, 2, 1))));
    }

    @Test
    void refusesMovingAcrossStructuralBoundary() {
        assertRefused(
                "VARIABLES_STRUCTURAL_BOUNDARY",
                request(
                        102,
                        List.of(
                                row(101, 10, 1),
                                row(103, 10, 2),
                                row(104, 10, 3),
                                row(105, 10, 4),
                                row(102, 10, 5),
                                row(106, 10, 6))));
    }

    @Test
    void refusesReorderingOtherRowsAroundTheDraggedInstruction() {
        assertRefused(
                "VARIABLES_NOT_SINGLE_REINSERT",
                request(
                        102,
                        List.of(
                                row(101, 10, 1),
                                row(104, 10, 2),
                                row(103, 10, 3),
                                row(102, 10, 4),
                                row(105, 10, 5),
                                row(106, 10, 6))));
    }

    @Test
    void refusesDraggingAnOrdinaryOrStructuralInstruction() {
        assertRefused(
                "VARIABLES_SOURCE_NOT_ELIGIBLE",
                request(
                        103,
                        List.of(
                                row(101, 10, 1),
                                row(103, 10, 2),
                                row(102, 10, 3),
                                row(104, 10, 4),
                                row(105, 10, 5),
                                row(106, 10, 6))));
    }

    @Test
    void refusesAStaleMutationRevisionBeforePersistence() {
        InstructionGraphMutationV3.Request candidate =
                new InstructionGraphMutationV3.Request(
                        InstructionGraphMutationV3.CONTRACT_VERSION,
                        InstructionGraphMutationV3.MutationKind.ROW_MOVE,
                        "variables-move",
                        3L,
                        "stale",
                        10L,
                        owner(),
                        102,
                        List.of(
                                row(101, 10, 1),
                                row(103, 10, 2),
                                row(102, 10, 3),
                                row(104, 10, 4),
                                row(105, 10, 5),
                                row(106, 10, 6)),
                        List.of(),
                        List.of(),
                        List.of());

        assertRefused("VARIABLES_GRAPH_CHANGED", candidate);
    }

    private void assertRefused(
            String code,
            InstructionGraphMutationV3.Request request) {
        MutationRefusedException refused = assertThrows(
                MutationRefusedException.class,
                () -> profile.validate(request, snapshot()));
        assertEquals(code, refused.code());
    }

    private InstructionGraphMutationV3.Request request(
            int draggedId,
            List<LayoutRow> layout) {
        return new InstructionGraphMutationV3.Request(
                InstructionGraphMutationV3.CONTRACT_VERSION,
                InstructionGraphMutationV3.MutationKind.ROW_MOVE,
                "variables-move",
                4L,
                "revision",
                10L,
                owner(),
                draggedId,
                layout,
                List.of(),
                List.of(),
                List.of());
    }

    private InstructionGraphMutationV3.OwnerAssertion owner() {
        return new InstructionGraphMutationV3.OwnerAssertion(
                InstructionGraphMutationV3.WorkspaceKind.BOT_JOB,
                2,
                5);
    }

    private GraphSnapshot snapshot() {
        return new GraphSnapshot(
                4L,
                "revision",
                List.of(
                        row(101, 10, 1),
                        row(102, 10, 2),
                        row(103, 10, 3),
                        row(104, 10, 4),
                        row(105, 10, 5),
                        row(106, 10, 6)),
                List.of(
                        fact(101, 1, "CLICK", null),
                        fact(102, 2, "GET", 7),
                        fact(103, 3, "H", null),
                        fact(104, 4, "CK", 7),
                        fact(105, 5, "LOOP", null),
                        fact(106, 6, "PDF CHECK", 7)));
    }

    private LayoutRow row(int instructionId, int blockId, int order) {
        return new LayoutRow(instructionId, blockId, blockId == 10 ? 1 : 2, order);
    }

    private GraphInstructionFact fact(
            int instructionId,
            int order,
            String action,
            Integer variableId) {
        return new GraphInstructionFact(
                instructionId,
                10,
                1,
                order,
                action,
                instructionId == 102 || instructionId == 104 || instructionId == 106
                        ? 101
                        : null,
                instructionId == 102 || instructionId == 104 || instructionId == 106
                        ? 10
                        : null,
                variableId);
    }
}

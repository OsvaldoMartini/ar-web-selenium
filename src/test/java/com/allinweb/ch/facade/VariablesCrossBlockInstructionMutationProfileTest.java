package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.allinweb.ch.facade.BotJobGraphMutationTransaction.GraphInstructionFact;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.GraphSnapshot;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.MutationRefusedException;
import com.allinweb.ch.model.InstructionGraphMutationV3;
import com.allinweb.ch.model.InstructionGraphMutationV3.InstructionRelationKind;
import com.allinweb.ch.model.InstructionGraphMutationV3.InstructionRelationPatch;
import com.allinweb.ch.model.InstructionGraphMutationV3.InstructionRelationState;
import com.allinweb.ch.model.InstructionGraphMutationV3.LayoutRow;
import com.allinweb.ch.model.InstructionGraphMutationV3.PatchOperation;
import java.util.List;
import org.junit.jupiter.api.Test;

class VariablesCrossBlockInstructionMutationProfileTest {

    private final VariablesCrossBlockInstructionMutationProfile profile =
            new VariablesCrossBlockInstructionMutationProfile();

    @Test
    void acceptsOneConsumerMovedAndExplicitlyDisconnected() throws Exception {
        profile.validate(
                request(
                        102,
                        crossBlockLayout(),
                        relationPatch(
                                102,
                                PatchOperation.CLEAR,
                                state(101, 10),
                                InstructionRelationState.disconnected())),
                snapshot());
    }

    @Test
    void acceptsOneConsumerReconnectedToEarlierDestinationWebElement()
            throws Exception {
        profile.validate(
                request(
                        102,
                        crossBlockLayout(),
                        relationPatch(
                                102,
                                PatchOperation.SET,
                                state(101, 10),
                                state(201, 11))),
                snapshot());
    }

    @Test
    void refusesGetOrSetAsCrossBlockSource() {
        assertRefused(
                "VARIABLES_CROSS_SOURCE_NOT_ELIGIBLE",
                request(
                        104,
                        List.of(
                                row(101, 10, 1, 1),
                                row(102, 10, 1, 2),
                                row(103, 10, 1, 3),
                                row(201, 11, 2, 1),
                                row(104, 11, 2, 2),
                                row(202, 11, 2, 3)),
                        relationPatch(
                                104,
                                PatchOperation.CLEAR,
                                state(null, null),
                                InstructionRelationState.disconnected())));
    }

    @Test
    void refusesReorderingAnyOtherInstruction() {
        assertRefused(
                "VARIABLES_CROSS_NOT_SINGLE_REINSERT",
                request(
                        102,
                        List.of(
                                row(103, 10, 1, 1),
                                row(101, 10, 1, 2),
                                row(201, 11, 2, 1),
                                row(102, 11, 2, 2),
                                row(202, 11, 2, 3),
                                row(104, 12, 3, 1)),
                        clearPatch()));
    }

    @Test
    void refusesMovingTheLastInstructionOutOfTheSourceBlock() {
        GraphSnapshot oneRowSource = new GraphSnapshot(
                4L,
                "revision",
                List.of(
                        row(102, 10, 1, 1),
                        row(201, 11, 2, 1),
                        row(202, 11, 2, 2)),
                List.of(
                        fact(102, 10, 1, 1, "E", 201, 11, 7),
                        fact(201, 11, 2, 1, "CLICK", null, null, null),
                        fact(202, 11, 2, 2, "H", null, null, null)));
        InstructionGraphMutationV3.Request request = request(
                102,
                List.of(
                        row(201, 11, 2, 1),
                        row(102, 11, 2, 2),
                        row(202, 11, 2, 3)),
                relationPatch(
                        102,
                        PatchOperation.SET,
                        state(201, 11),
                        state(201, 11)));

        MutationRefusedException refused = assertThrows(
                MutationRefusedException.class,
                () -> profile.validate(request, oneRowSource));
        assertEquals("VARIABLES_CROSS_EMPTY_SOURCE_BLOCK", refused.code());
    }

    @Test
    void refusesStructuralSourceOrDestinationBlock() {
        GraphSnapshot structural = replaceFact(
                snapshot(),
                fact(103, 10, 1, 3, "LOOP", null, null, null));

        MutationRefusedException refused = assertThrows(
                MutationRefusedException.class,
                () -> profile.validate(
                        request(102, crossBlockLayout(), clearPatch()),
                        structural));
        assertEquals("VARIABLES_CROSS_STRUCTURAL_BLOCK", refused.code());
    }

    @Test
    void refusesConsumerWhoseAuthoritativeSourceRelationIsDisconnectedOrDangling() {
        GraphSnapshot disconnected = replaceFact(
                snapshot(),
                fact(102, 10, 1, 2, "E", null, null, 7));
        MutationRefusedException disconnectedRefusal = assertThrows(
                MutationRefusedException.class,
                () -> profile.validate(
                        request(
                                102,
                                crossBlockLayout(),
                                relationPatch(
                                        102,
                                        PatchOperation.SET,
                                        state(null, null),
                                        state(201, 11))),
                        disconnected));
        assertEquals(
                "VARIABLES_CROSS_SOURCE_RELATION_INVALID",
                disconnectedRefusal.code());

        GraphSnapshot dangling = replaceFact(
                snapshot(),
                fact(102, 10, 1, 2, "E", 999, 10, 7));
        MutationRefusedException danglingRefusal = assertThrows(
                MutationRefusedException.class,
                () -> profile.validate(
                        request(
                                102,
                                crossBlockLayout(),
                                relationPatch(
                                        102,
                                        PatchOperation.CLEAR,
                                        state(999, 10),
                                        InstructionRelationState.disconnected())),
                        dangling));
        assertEquals(
                "VARIABLES_CROSS_SOURCE_RELATION_INVALID",
                danglingRefusal.code());

        GraphSnapshot commandParent = replaceFact(
                snapshot(),
                fact(101, 10, 1, 1, "PAUSE", null, null, null));
        MutationRefusedException commandParentRefusal = assertThrows(
                MutationRefusedException.class,
                () -> profile.validate(
                        request(102, crossBlockLayout(), clearPatch()),
                        commandParent));
        assertEquals(
                "VARIABLES_CROSS_SOURCE_RELATION_INVALID",
                commandParentRefusal.code());
    }

    @Test
    void refusesAStoredLayoutWhoseOrdersAreNotContiguous() {
        GraphSnapshot gapped = new GraphSnapshot(
                4L,
                "revision",
                List.of(
                        row(101, 10, 1, 1),
                        row(102, 10, 1, 3),
                        row(103, 10, 1, 4),
                        row(201, 11, 2, 1),
                        row(202, 11, 2, 2),
                        row(104, 12, 3, 1)),
                List.of(
                        fact(101, 10, 1, 1, "CLICK", null, null, null),
                        fact(102, 10, 1, 3, "E", 101, 10, 7),
                        fact(103, 10, 1, 4, "H", null, null, null),
                        fact(201, 11, 2, 1, "CLICK", null, null, null),
                        fact(202, 11, 2, 2, "H", null, null, null),
                        fact(104, 12, 3, 1, "GET", null, null, 7)));

        MutationRefusedException refused = assertThrows(
                MutationRefusedException.class,
                () -> profile.validate(
                        request(102, crossBlockLayout(), clearPatch()),
                        gapped));
        assertEquals("VARIABLES_CROSS_ORDER_INVALID", refused.code());
    }

    @Test
    void refusesMissingKeepOrMismatchedRelationshipPatch() {
        assertRefused(
                "VARIABLES_CROSS_RELATION_PATCH_REQUIRED",
                request(102, crossBlockLayout(), null));
        assertRefused(
                "VARIABLES_CROSS_RELATION_PATCH_INVALID",
                request(
                        102,
                        crossBlockLayout(),
                        relationPatch(
                                102,
                                PatchOperation.CLEAR,
                                state(999, 10),
                                InstructionRelationState.disconnected())));
        assertRefused(
                "VARIABLES_CROSS_RELATION_PATCH_INVALID",
                request(
                        102,
                        crossBlockLayout(),
                        relationPatch(
                                102,
                                PatchOperation.KEEP,
                                state(101, 10),
                                state(101, 10))));
    }

    @Test
    void refusesReconnectTargetAfterConsumerOrCommandTarget() {
        assertRefused(
                "VARIABLES_CROSS_TARGET_INVALID",
                request(
                        102,
                        crossBlockLayout(),
                        relationPatch(
                                102,
                                PatchOperation.SET,
                                state(101, 10),
                                state(202, 11))));

        GraphSnapshot commandTarget = replaceFact(
                snapshot(),
                fact(201, 11, 2, 1, "H", null, null, null));
        MutationRefusedException refused = assertThrows(
                MutationRefusedException.class,
                () -> profile.validate(
                        request(
                                102,
                                crossBlockLayout(),
                                relationPatch(
                                        102,
                                        PatchOperation.SET,
                                        state(101, 10),
                                        state(201, 11))),
                        commandTarget));
        assertEquals("VARIABLES_CROSS_TARGET_INVALID", refused.code());
    }

    @Test
    void refusesSourceThatStillHasDirectDependants() {
        GraphSnapshot withDependant = replaceFact(
                snapshot(),
                fact(103, 10, 1, 3, "CLICK", 102, 10, null));

        MutationRefusedException refused = assertThrows(
                MutationRefusedException.class,
                () -> profile.validate(
                        request(102, crossBlockLayout(), clearPatch()),
                        withDependant));
        assertEquals("VARIABLES_CROSS_SOURCE_HAS_DEPENDANTS", refused.code());
    }

    @Test
    void refusesVariableBindingOrOwnerChanges() {
        InstructionGraphMutationV3.Request request =
                new InstructionGraphMutationV3.Request(
                        InstructionGraphMutationV3.CONTRACT_VERSION,
                        InstructionGraphMutationV3.MutationKind.ROW_MOVE,
                        "variables-cross",
                        4L,
                        "revision",
                        10L,
                        owner(),
                        102,
                        crossBlockLayout(),
                        List.of(clearPatch()),
                        List.of(new InstructionGraphMutationV3.VariableBindingPatch(
                                102,
                                PatchOperation.CLEAR,
                                InstructionGraphMutationV3.NullableId.of(7),
                                InstructionGraphMutationV3.NullableId.of(null))),
                        List.of());

        assertRefused("VARIABLES_CROSS_VARIABLE_PATCH_NOT_ALLOWED", request);
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
            List<LayoutRow> layout,
            InstructionRelationPatch relationPatch) {
        return new InstructionGraphMutationV3.Request(
                InstructionGraphMutationV3.CONTRACT_VERSION,
                InstructionGraphMutationV3.MutationKind.ROW_MOVE,
                "variables-cross",
                4L,
                "revision",
                10L,
                owner(),
                draggedId,
                layout,
                relationPatch == null ? List.of() : List.of(relationPatch),
                List.of(),
                List.of());
    }

    private List<LayoutRow> crossBlockLayout() {
        return List.of(
                row(101, 10, 1, 1),
                row(103, 10, 1, 2),
                row(201, 11, 2, 1),
                row(102, 11, 2, 2),
                row(202, 11, 2, 3),
                row(104, 12, 3, 1));
    }

    private InstructionRelationPatch clearPatch() {
        return relationPatch(
                102,
                PatchOperation.CLEAR,
                state(101, 10),
                InstructionRelationState.disconnected());
    }

    private InstructionRelationPatch relationPatch(
            int instructionId,
            PatchOperation operation,
            InstructionRelationState expected,
            InstructionRelationState replacement) {
        return new InstructionRelationPatch(
                instructionId,
                InstructionRelationKind.ELEMENT_TARGET,
                operation,
                expected,
                replacement);
    }

    private InstructionRelationState state(Integer parentId, Integer parentBlockId) {
        return new InstructionRelationState(parentId, parentBlockId);
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
                        row(101, 10, 1, 1),
                        row(102, 10, 1, 2),
                        row(103, 10, 1, 3),
                        row(201, 11, 2, 1),
                        row(202, 11, 2, 2),
                        row(104, 12, 3, 1)),
                List.of(
                        fact(101, 10, 1, 1, "CLICK", null, null, null),
                        fact(102, 10, 1, 2, "E", 101, 10, 7),
                        fact(103, 10, 1, 3, "H", null, null, null),
                        fact(201, 11, 2, 1, "CLICK", null, null, null),
                        fact(202, 11, 2, 2, "H", null, null, null),
                        fact(104, 12, 3, 1, "GET", null, null, 7)));
    }

    private GraphSnapshot replaceFact(
            GraphSnapshot snapshot,
            GraphInstructionFact replacement) {
        List<GraphInstructionFact> facts = snapshot.instructionFacts().stream()
                .map(fact -> fact.instructionId() == replacement.instructionId()
                        ? replacement
                        : fact)
                .toList();
        return new GraphSnapshot(
                snapshot.graphVersion(),
                snapshot.graphRevision(),
                snapshot.layoutRows(),
                facts);
    }

    private LayoutRow row(
            int instructionId,
            int blockId,
            int blockOrder,
            int instructionOrder) {
        return new LayoutRow(
                instructionId, blockId, blockOrder, instructionOrder);
    }

    private GraphInstructionFact fact(
            int instructionId,
            int blockId,
            int blockOrder,
            int instructionOrder,
            String action,
            Integer parentId,
            Integer parentBlockId,
            Integer variableId) {
        return new GraphInstructionFact(
                instructionId,
                blockId,
                blockOrder,
                instructionOrder,
                action,
                parentId,
                parentBlockId,
                variableId);
    }
}

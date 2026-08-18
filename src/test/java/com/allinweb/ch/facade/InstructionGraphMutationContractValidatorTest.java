package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.facade.InstructionGraphMutationContractValidator.ErrorCode;
import com.allinweb.ch.facade.InstructionGraphMutationContractValidator.NormalizedInstruction;
import com.allinweb.ch.facade.InstructionGraphMutationContractValidator.OwnerGraph;
import com.allinweb.ch.facade.InstructionGraphMutationContractValidator.OwnerScope;
import com.allinweb.ch.facade.InstructionGraphMutationContractValidator.StoredBlock;
import com.allinweb.ch.facade.InstructionGraphMutationContractValidator.StoredInstruction;
import com.allinweb.ch.facade.InstructionGraphMutationContractValidator.StoredVariable;
import com.allinweb.ch.facade.InstructionGraphMutationContractValidator.Validation;
import com.allinweb.ch.model.InstructionGraphMutationV3;
import com.allinweb.ch.model.InstructionGraphMutationV3.InstructionRelationKind;
import com.allinweb.ch.model.InstructionGraphMutationV3.InstructionRelationPatch;
import com.allinweb.ch.model.InstructionGraphMutationV3.InstructionRelationState;
import com.allinweb.ch.model.InstructionGraphMutationV3.LayoutRow;
import com.allinweb.ch.model.InstructionGraphMutationV3.PatchOperation;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class InstructionGraphMutationContractValidatorTest {

    private static final int LOOP_ID = 101;
    private static final int GOTO_ID = 102;

    private final InstructionGraphMutationContractValidator validator =
            new InstructionGraphMutationContractValidator();

    @Test
    void distinguishesAnOmittedPatchFromAnExplicitNullRelationship() {
        Validation omitted = validator.validateAndNormalize(
                request(defaultLayout(), List.of()),
                graph());

        assertTrue(omitted.successful());
        NormalizedInstruction kept = instruction(omitted, LOOP_ID);
        assertEquals(100, kept.parentId());
        assertEquals(10, kept.parentBlockId());

        Validation explicitNull = validator.validateAndNormalize(
                request(
                        defaultLayout(),
                        List.of(relationPatch(
                                LOOP_ID,
                                InstructionRelationKind.LOOP_ANCHOR,
                                PatchOperation.CLEAR,
                                relation(100, 10),
                                InstructionRelationState.disconnected()))),
                graph());

        assertTrue(explicitNull.successful());
        NormalizedInstruction cleared = instruction(explicitNull, LOOP_ID);
        assertNull(cleared.parentId());
        assertNull(cleared.parentBlockId());
    }

    @Test
    void acceptsLegacyInstructionTargetWithoutParentBlockProjection() {
        OwnerGraph legacyGraph = graph(
                List.of(
                        stored(100, 10, 1, null, null, null, null),
                        stored(
                                LOOP_ID,
                                10,
                                2,
                                InstructionRelationKind.LOOP_ANCHOR,
                                100,
                                null,
                                null),
                        stored(
                                GOTO_ID,
                                20,
                                1,
                                InstructionRelationKind.BLOCK_TARGET,
                                null,
                                10,
                                null),
                        stored(103, 20, 2, null, null, null, null),
                        stored(104, 30, 1, null, null, null, null)),
                List.of());

        Validation kept = validator.validateAndNormalize(
                request(defaultLayout(), List.of()),
                legacyGraph);

        assertTrue(kept.successful());
        assertEquals(100, instruction(kept, LOOP_ID).parentId());
        assertNull(instruction(kept, LOOP_ID).parentBlockId());

        Validation cleared = validator.validateAndNormalize(
                request(
                        defaultLayout(),
                        List.of(relationPatch(
                                LOOP_ID,
                                InstructionRelationKind.LOOP_ANCHOR,
                                PatchOperation.CLEAR,
                                relation(100, null),
                                InstructionRelationState.disconnected()))),
                legacyGraph);

        assertTrue(cleared.successful());
        assertNull(instruction(cleared, LOOP_ID).parentId());
        assertNull(instruction(cleared, LOOP_ID).parentBlockId());
    }

    @Test
    void rejectsAnOmittedStateObjectInsteadOfInferringClear() {
        Validation validation = validator.validateAndNormalize(
                request(
                        defaultLayout(),
                        List.of(relationPatch(
                                LOOP_ID,
                                InstructionRelationKind.LOOP_ANCHOR,
                                PatchOperation.CLEAR,
                                relation(100, 10),
                                null))),
                graph());

        assertError(validation, ErrorCode.MISSING_RELATION_STATE);
    }

    @Test
    void supportsGotoKeepClearAndSetWithoutChangingTheContainingBlock() {
        Validation keep = validator.validateAndNormalize(
                request(
                        defaultLayout(),
                        List.of(relationPatch(
                                GOTO_ID,
                                InstructionRelationKind.BLOCK_TARGET,
                                PatchOperation.KEEP,
                                relation(null, 10),
                                relation(null, 10)))),
                graph());
        assertTrue(keep.successful());
        assertEquals(10, instruction(keep, GOTO_ID).parentBlockId());

        Validation clear = validator.validateAndNormalize(
                request(
                        defaultLayout(),
                        List.of(relationPatch(
                                GOTO_ID,
                                InstructionRelationKind.BLOCK_TARGET,
                                PatchOperation.CLEAR,
                                relation(null, 10),
                                InstructionRelationState.disconnected()))),
                graph());
        assertTrue(clear.successful());
        assertNull(instruction(clear, GOTO_ID).parentBlockId());

        Validation set = validator.validateAndNormalize(
                request(
                        defaultLayout(),
                        List.of(relationPatch(
                                GOTO_ID,
                                InstructionRelationKind.BLOCK_TARGET,
                                PatchOperation.SET,
                                relation(null, 10),
                                relation(null, 30)))),
                graph());
        assertTrue(set.successful());
        assertEquals(30, instruction(set, GOTO_ID).parentBlockId());
    }

    @Test
    void rejectsGotoSetToItsOwnContainingBlock() {
        Validation validation = validator.validateAndNormalize(
                request(
                        defaultLayout(),
                        List.of(relationPatch(
                                GOTO_ID,
                                InstructionRelationKind.BLOCK_TARGET,
                                PatchOperation.SET,
                                relation(null, 10),
                                relation(null, 20)))),
                graph());

        assertError(validation, ErrorCode.BLOCK_TARGET_EQUALS_CONTAINING_BLOCK);
    }

    @Test
    void rejectsGotoKeepWhenMovingTheRowIntoItsTargetBlock() {
        Validation validation = validator.validateAndNormalize(
                request(
                        layoutWithGotoInBlockTen(),
                        List.of(relationPatch(
                                GOTO_ID,
                                InstructionRelationKind.BLOCK_TARGET,
                                PatchOperation.KEEP,
                                relation(null, 10),
                                relation(null, 10)))),
                graph());

        assertError(validation, ErrorCode.BLOCK_TARGET_EQUALS_CONTAINING_BLOCK);
    }

    @Test
    void rejectsOmittedGotoPatchWhenMovingTheRowIntoItsTargetBlock() {
        Validation validation = validator.validateAndNormalize(
                request(layoutWithGotoInBlockTen(), List.of()),
                graph());

        assertError(validation, ErrorCode.BLOCK_TARGET_EQUALS_CONTAINING_BLOCK);
    }

    @Test
    void supportsLoopKeepClearAndCrossBlockSet() {
        Validation keep = validator.validateAndNormalize(
                request(
                        defaultLayout(),
                        List.of(relationPatch(
                                LOOP_ID,
                                InstructionRelationKind.LOOP_ANCHOR,
                                PatchOperation.KEEP,
                                relation(100, 10),
                                relation(100, 10)))),
                graph());
        assertTrue(keep.successful());
        assertEquals(100, instruction(keep, LOOP_ID).parentId());
        assertEquals(10, instruction(keep, LOOP_ID).parentBlockId());

        Validation clear = validator.validateAndNormalize(
                request(
                        defaultLayout(),
                        List.of(relationPatch(
                                LOOP_ID,
                                InstructionRelationKind.LOOP_ANCHOR,
                                PatchOperation.CLEAR,
                                relation(100, 10),
                                InstructionRelationState.disconnected()))),
                graph());
        assertTrue(clear.successful());
        assertNull(instruction(clear, LOOP_ID).parentId());
        assertNull(instruction(clear, LOOP_ID).parentBlockId());

        Validation set = validator.validateAndNormalize(
                request(
                        defaultLayout(),
                        List.of(relationPatch(
                                LOOP_ID,
                                InstructionRelationKind.LOOP_ANCHOR,
                                PatchOperation.SET,
                                relation(100, 10),
                                relation(103, 20)))),
                graph());
        assertTrue(set.successful());
        assertEquals(103, instruction(set, LOOP_ID).parentId());
        assertEquals(20, instruction(set, LOOP_ID).parentBlockId());
    }

    @Test
    void rejectsDuplicateAndIncompleteLayouts() {
        List<LayoutRow> duplicate = new ArrayList<>(defaultLayout());
        duplicate.set(duplicate.size() - 1, row(100, 10, 1, 3));

        Validation duplicateValidation =
                validator.validateAndNormalize(request(duplicate, List.of()), graph());
        assertError(duplicateValidation, ErrorCode.DUPLICATE_LAYOUT_INSTRUCTION);

        List<LayoutRow> incomplete = defaultLayout().subList(0, defaultLayout().size() - 1);
        Validation incompleteValidation =
                validator.validateAndNormalize(request(incomplete, List.of()), graph());
        assertError(incompleteValidation, ErrorCode.INCOMPLETE_LAYOUT);
    }

    @Test
    void refusesCrossOwnerInstructionAndBlockTargets() {
        Validation instructionTarget = validator.validateAndNormalize(
                request(
                        defaultLayout(),
                        List.of(relationPatch(
                                LOOP_ID,
                                InstructionRelationKind.LOOP_ANCHOR,
                                PatchOperation.SET,
                                relation(100, 10),
                                relation(999, 10)))),
                graph());
        assertError(instructionTarget, ErrorCode.CROSS_OWNER_INSTRUCTION_TARGET);

        Validation blockTarget = validator.validateAndNormalize(
                request(
                        defaultLayout(),
                        List.of(relationPatch(
                                GOTO_ID,
                                InstructionRelationKind.BLOCK_TARGET,
                                PatchOperation.SET,
                                relation(null, 10),
                                relation(null, 999)))),
                graph());
        assertError(blockTarget, ErrorCode.CROSS_OWNER_BLOCK_TARGET);
    }

    @Test
    void refusesAClientRelationshipKindThatDoesNotMatchTheAuthoritativeRow() {
        Validation validation = validator.validateAndNormalize(
                request(
                        defaultLayout(),
                        List.of(relationPatch(
                                GOTO_ID,
                                InstructionRelationKind.LOOP_ANCHOR,
                                PatchOperation.KEEP,
                                relation(null, 10),
                                relation(null, 10)))),
                graph());

        assertError(validation, ErrorCode.RELATION_KIND_MISMATCH);
    }

    @Test
    void finalStatePassRejectsDanglingRelationshipsAndVariableReferencesOnOmittedKeep() {
        OwnerGraph danglingParent = graph(
                List.of(
                        stored(100, 10, 1, null, null, null, null),
                        stored(
                                LOOP_ID,
                                10,
                                2,
                                InstructionRelationKind.LOOP_ANCHOR,
                                999,
                                10,
                                null),
                        stored(
                                GOTO_ID,
                                20,
                                1,
                                InstructionRelationKind.BLOCK_TARGET,
                                null,
                                10,
                                null),
                        stored(103, 20, 2, null, null, null, null),
                        stored(104, 30, 1, null, null, null, null)),
                List.of());
        assertError(
                validator.validateAndNormalize(
                        request(defaultLayout(), List.of()),
                        danglingParent),
                ErrorCode.CROSS_OWNER_INSTRUCTION_TARGET);

        OwnerGraph danglingVariable = graph(
                List.of(
                        stored(100, 10, 1, null, null, null, 999),
                        stored(
                                LOOP_ID,
                                10,
                                2,
                                InstructionRelationKind.LOOP_ANCHOR,
                                100,
                                10,
                                null),
                        stored(
                                GOTO_ID,
                                20,
                                1,
                                InstructionRelationKind.BLOCK_TARGET,
                                null,
                                10,
                                null),
                        stored(103, 20, 2, null, null, null, null),
                        stored(104, 30, 1, null, null, null, null)),
                List.of());
        assertError(
                validator.validateAndNormalize(
                        request(defaultLayout(), List.of()),
                        danglingVariable),
                ErrorCode.CROSS_OWNER_VARIABLE_TARGET);
    }

    @Test
    void preservesAnUntouchedDanglingLegacyVariableOwnerDuringUnrelatedMutation() {
        OwnerGraph danglingOwner = graph(
                List.of(
                        stored(100, 10, 1, null, null, null, null),
                        stored(
                                LOOP_ID,
                                10,
                                2,
                                InstructionRelationKind.LOOP_ANCHOR,
                                100,
                                10,
                                null),
                        stored(
                                GOTO_ID,
                                20,
                                1,
                                InstructionRelationKind.BLOCK_TARGET,
                                null,
                                10,
                                null),
                        stored(103, 20, 2, null, null, null, null),
                        stored(104, 30, 1, null, null, null, null)),
                List.of(new StoredVariable(501, 999999)));

        Validation validation = validator.validateAndNormalize(
                request(defaultLayout(), List.of()),
                danglingOwner);

        assertTrue(validation.successful());
        assertEquals(
                999999,
                validation.mutation().variables().get(0).instructionId());
    }

    private InstructionGraphMutationV3.Request request(
            List<LayoutRow> layout,
            List<InstructionRelationPatch> relationPatches) {
        return new InstructionGraphMutationV3.Request(
                InstructionGraphMutationV3.CONTRACT_VERSION,
                InstructionGraphMutationV3.MutationKind.ROW_MOVE,
                "request-1",
                42L,
                "rev-42",
                12L,
                new InstructionGraphMutationV3.OwnerAssertion(
                        InstructionGraphMutationV3.WorkspaceKind.BOT_JOB,
                        2,
                        5),
                LOOP_ID,
                layout,
                relationPatches,
                List.of(),
                List.of());
    }

    private OwnerGraph graph() {
        return graph(
                List.of(
                        stored(100, 10, 1, null, null, null, null),
                        stored(
                                LOOP_ID,
                                10,
                                2,
                                InstructionRelationKind.LOOP_ANCHOR,
                                100,
                                10,
                                null),
                        stored(
                                GOTO_ID,
                                20,
                                1,
                                InstructionRelationKind.BLOCK_TARGET,
                                null,
                                10,
                                null),
                        stored(103, 20, 2, null, null, null, null),
                        stored(104, 30, 1, null, null, null, null)),
                List.of());
    }

    private OwnerGraph graph(
            List<StoredInstruction> instructions,
            List<StoredVariable> variables) {
        return new OwnerGraph(
                new OwnerScope(
                        InstructionGraphMutationV3.WorkspaceKind.BOT_JOB,
                        2,
                        5,
                        12L,
                        42L,
                        "rev-42"),
                List.of(
                        new StoredBlock(10, 1),
                        new StoredBlock(20, 2),
                        new StoredBlock(30, 3)),
                instructions,
                variables);
    }

    private List<LayoutRow> defaultLayout() {
        return List.of(
                row(100, 10, 1, 1),
                row(LOOP_ID, 10, 1, 2),
                row(GOTO_ID, 20, 2, 1),
                row(103, 20, 2, 2),
                row(104, 30, 3, 1));
    }

    private List<LayoutRow> layoutWithGotoInBlockTen() {
        return List.of(
                row(100, 10, 1, 1),
                row(LOOP_ID, 10, 1, 2),
                row(GOTO_ID, 10, 1, 3),
                row(103, 20, 2, 1),
                row(104, 30, 3, 1));
    }

    private StoredInstruction stored(
            int id,
            int blockId,
            int order,
            InstructionRelationKind relationKind,
            Integer parentId,
            Integer parentBlockId,
            Integer variableId) {
        return new StoredInstruction(
                id,
                blockId,
                order,
                relationKind,
                parentId,
                parentBlockId,
                variableId);
    }

    private LayoutRow row(
            int instructionId,
            int blockId,
            int blockOrder,
            int instructionOrder) {
        return new LayoutRow(instructionId, blockId, blockOrder, instructionOrder);
    }

    private InstructionRelationPatch relationPatch(
            int instructionId,
            InstructionRelationKind kind,
            PatchOperation operation,
            InstructionRelationState expected,
            InstructionRelationState replacement) {
        return new InstructionRelationPatch(
                instructionId,
                kind,
                operation,
                expected,
                replacement);
    }

    private InstructionRelationState relation(Integer parentId, Integer parentBlockId) {
        return new InstructionRelationState(parentId, parentBlockId);
    }

    private NormalizedInstruction instruction(Validation validation, int instructionId) {
        return validation.mutation().instructions().stream()
                .filter(instruction -> instruction.instructionId() == instructionId)
                .findFirst()
                .orElseThrow();
    }

    private void assertError(Validation validation, ErrorCode expected) {
        assertFalse(validation.successful());
        assertEquals(expected, validation.error().code());
    }
}

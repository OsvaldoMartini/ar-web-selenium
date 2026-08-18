package com.allinweb.ch.model;

import java.util.List;

/**
 * Additive version-3 instruction graph mutation contract.
 *
 * <p>This contract is intentionally independent from {@link SplitDTO} and {@link UpdatedRow}.
 * Relationship intent is expressed by an explicit operation plus complete expected/replacement
 * state objects. Omitting a patch means KEEP; a CLEAR is never inferred from a missing or JSON-null
 * scalar field.
 */
public final class InstructionGraphMutationV3 {

    public static final int CONTRACT_VERSION = 3;

    private InstructionGraphMutationV3() {}

    public enum WorkspaceKind {
        BOT_JOB,
        COMPONENT
    }

    public enum MutationKind {
        ROW_MOVE,
        RELATIONSHIP_UPDATE
    }

    public enum PatchOperation {
        KEEP,
        SET,
        CLEAR
    }

    public enum InstructionRelationKind {
        ELEMENT_TARGET,
        LOOP_ANCHOR,
        CONDITIONAL_ROOT,
        BLOCK_TARGET
    }

    public record OwnerAssertion(
            WorkspaceKind workspaceKind,
            Integer homeBankingId,
            Integer botJobId) {}

    public record LayoutRow(
            Integer instructionId,
            Integer blockId,
            Integer blockOrderNumber,
            Integer instructionOrderNumber) {}

    /**
     * Complete persisted parent relationship state.
     *
     * <p>The wrapper itself is required on a patch. Inside the wrapper, null is an explicit
     * disconnected value. This makes an omitted state object different from an explicitly
     * disconnected state.
     */
    public record InstructionRelationState(
            Integer parentId,
            Integer parentBlockId) {

        public static InstructionRelationState disconnected() {
            return new InstructionRelationState(null, null);
        }
    }

    public record InstructionRelationPatch(
            Integer instructionId,
            InstructionRelationKind relationKind,
            PatchOperation operation,
            InstructionRelationState expected,
            InstructionRelationState replacement) {}

    /**
     * Explicit nullable ID value used by variable patches.
     *
     * <p>A missing wrapper is invalid. {@code new NullableId(null)} is an explicit null value.
     */
    public record NullableId(Integer value) {

        public static NullableId of(Integer value) {
            return new NullableId(value);
        }
    }

    public record VariableBindingPatch(
            Integer instructionId,
            PatchOperation operation,
            NullableId expected,
            NullableId replacement) {}

    public record VariableOwnerPatch(
            Integer variableId,
            PatchOperation operation,
            NullableId expected,
            NullableId replacement) {}

    public record Request(
            Integer contractVersion,
            MutationKind mutationKind,
            String requestId,
            Long baseGraphVersion,
            String graphRevision,
            Long workspaceEpoch,
            OwnerAssertion ownerAssertion,
            Integer draggedInstructionId,
            List<LayoutRow> layoutRows,
            List<InstructionRelationPatch> instructionRelationPatches,
            List<VariableBindingPatch> variableBindingPatches,
            List<VariableOwnerPatch> variableOwnerPatches) {

        public Request {
            layoutRows = immutable(layoutRows);
            instructionRelationPatches = immutable(instructionRelationPatches);
            variableBindingPatches = immutable(variableBindingPatches);
            variableOwnerPatches = immutable(variableOwnerPatches);
        }
    }

    private static <T> List<T> immutable(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}

package com.allinweb.ch.facade.execution;

import java.util.List;
import java.util.Objects;

/**
 * Immutable, persistence-independent facts used to decide whether one Bot Job is safe to run.
 *
 * <p>The future database loader must build one snapshot for exactly one owner. Consequently, an
 * instruction or Block identifier that is absent from this snapshot is not owned by this Bot Job.
 * This class deliberately contains no DTO or database dependency so the execution rule can be
 * tested independently and can never repair stored data while validating it.
 */
public record ExecutionPreflightSnapshot(
        Owner owner,
        List<BlockFact> blocks,
        List<InstructionFact> instructions,
        List<VariableFact> variables) {

    public ExecutionPreflightSnapshot {
        owner = Objects.requireNonNull(owner, "owner");
        blocks = List.copyOf(Objects.requireNonNull(blocks, "blocks"));
        instructions = List.copyOf(Objects.requireNonNull(instructions, "instructions"));
        variables = List.copyOf(Objects.requireNonNull(variables, "variables"));
    }

    /** Exact account and Bot Job identity that owns every supplied fact. */
    public record Owner(int homeBankingId, int botJobId) {
        public Owner {
            requirePositive(homeBankingId, "homeBankingId");
            requirePositive(botJobId, "botJobId");
        }
    }

    /** Execution-relevant Block state. Block order is one-based. */
    public record BlockFact(int id, int order, boolean active) {
        public BlockFact {
            requirePositive(id, "block id");
            requirePositive(order, "block order");
        }
    }

    /**
     * Execution-relevant instruction state.
     *
     * <p>{@code tagName} is needed because SET can only target writable elements. Nullable
     * relationship IDs remain nullable; zero is rejected rather than silently treated as missing.
     */
    public record InstructionFact(
            int id,
            int blockId,
            int order,
            String action,
            String tagName,
            boolean active,
            Integer parentId,
            Integer parentBlockId,
            Integer variableId) {

        public InstructionFact {
            requirePositive(id, "instruction id");
            requirePositive(blockId, "instruction blockId");
            requirePositive(order, "instruction order");
            action = action == null ? "" : action;
            requirePositiveWhenPresent(parentId, "parentId");
            requirePositiveWhenPresent(parentBlockId, "parentBlockId");
            requirePositiveWhenPresent(variableId, "variableId");
        }
    }

    /**
     * Variable metadata needed by the runtime gate.
     *
     * <p>An ownerless variable is valid durable memory. Runtime eligibility is decided by the
     * instruction that binds to it, not by {@code ownerInstructionId} being non-null.
     */
    public record VariableFact(int id, String type, Integer ownerInstructionId) {
        public VariableFact {
            requirePositive(id, "variable id");
            requirePositiveWhenPresent(ownerInstructionId, "variable ownerInstructionId");
        }
    }

    private static void requirePositive(int value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be a positive integer");
        }
    }

    private static void requirePositiveWhenPresent(Integer value, String field) {
        if (value != null) {
            requirePositive(value, field);
        }
    }
}

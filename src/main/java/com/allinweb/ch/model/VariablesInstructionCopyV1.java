package com.allinweb.ch.model;

import java.util.List;

/**
 * Explicit Variables-workspace instruction-copy contract.
 *
 * <p>React owns the exact ordered source ID list and target block. Java never discovers or expands
 * a connected group; it authenticates the detached workspace owner, rejects stale graph facts,
 * and persists fresh instruction IDs in one transaction.
 */
public final class VariablesInstructionCopyV1 {

    public static final int CONTRACT_VERSION = 1;

    private VariablesInstructionCopyV1() {}

    public enum Scope {
        ONLY_INSTRUCTION,
        WITH_PARENTS
    }

    public record Request(
            Integer contractVersion,
            String requestId,
            String bindingEpoch,
            Long workspaceEpoch,
            Long baseGraphVersion,
            String graphRevision,
            Integer targetBlockId,
            Integer selectedInstructionId,
            Scope scope,
            List<Integer> sourceInstructionIds) {

        public Request {
            sourceInstructionIds =
                    sourceInstructionIds == null ? List.of() : List.copyOf(sourceInstructionIds);
        }
    }
}

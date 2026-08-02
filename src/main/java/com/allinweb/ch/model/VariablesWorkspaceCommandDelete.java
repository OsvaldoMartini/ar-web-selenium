package com.allinweb.ch.model;

import java.util.List;

/** Exact, React-planned deletion of one command from the Variables workspace. */
public final class VariablesWorkspaceCommandDelete {
    public static final int CONTRACT_VERSION = 1;

    private VariablesWorkspaceCommandDelete() {}

    public record Request(
            Integer contractVersion,
            String requestId,
            String bindingEpoch,
            Long workspaceEpoch,
            Long baseGraphVersion,
            String graphRevision,
            Integer instructionId,
            Integer expectedBlockId,
            Integer expectedInstructionOrder,
            List<Integer> parentRepairInstructionIds,
            List<Integer> variableOwnerIds) {

        public Request {
            parentRepairInstructionIds = parentRepairInstructionIds == null
                    ? List.of()
                    : List.copyOf(parentRepairInstructionIds);
            variableOwnerIds = variableOwnerIds == null
                    ? List.of()
                    : List.copyOf(variableOwnerIds);
        }
    }
}

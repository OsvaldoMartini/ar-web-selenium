package com.allinweb.ch.model;

import java.util.List;

/**
 * Explicit Variables-workspace variable deletion contract.
 *
 * <p>React chooses the exact variable IDs. Java authenticates the detached workspace owner,
 * rejects stale graph facts, and persists only those submitted IDs.
 */
public final class VariablesWorkspaceVariableDelete {

    public static final int CONTRACT_VERSION = 1;

    private VariablesWorkspaceVariableDelete() {}

    public enum Mode {
        SINGLE,
        ALL
    }

    public record Request(
            Integer contractVersion,
            String requestId,
            Long baseGraphVersion,
            String graphRevision,
            Long workspaceEpoch,
            Mode mode,
            List<Integer> variableIds) {

        public Request {
            variableIds = variableIds == null ? List.of() : List.copyOf(variableIds);
        }
    }
}

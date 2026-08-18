package com.allinweb.ch.model;

import java.util.List;

/**
 * Explicit Variables-workspace variable deletion contract.
 *
 * <p>Java authenticates the workspace owner and deletes volatile variable definitions without
 * requiring graph-mutation authority. SINGLE is idempotent; ALL resolves the complete current
 * catalog inside the transaction.
 */
public final class VariablesWorkspaceVariableDelete {

    public static final int CONTRACT_VERSION = 2;

    private VariablesWorkspaceVariableDelete() {}

    public enum Mode {
        SINGLE,
        ALL
    }

    public record Request(
            Integer contractVersion,
            String requestId,
            Long workspaceEpoch,
            Mode mode,
            List<Integer> variableIds) {

        public Request {
            variableIds = variableIds == null ? List.of() : List.copyOf(variableIds);
        }
    }
}

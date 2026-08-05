package com.allinweb.ch.model;

import java.util.List;

/**
 * Typed contract for the Variables Resolve Connections variable auto-resolution:
 * creates/reuses deterministic variable names and connects every missing slot
 * in one owner-scoped transaction.
 */
public final class VariablesVariableAutoResolveV1 {
    public static final int CONTRACT_VERSION = 2;
    public static final int LEGACY_CONTRACT_VERSION = 1;

    private VariablesVariableAutoResolveV1() {}

    public record Request(
            Integer contractVersion,
            String requestId,
            String bindingEpoch,
            Long workspaceEpoch,
            Long baseGraphVersion,
            String graphRevision,
            List<Integer> instructionIds,
            String variableMode) {}
}

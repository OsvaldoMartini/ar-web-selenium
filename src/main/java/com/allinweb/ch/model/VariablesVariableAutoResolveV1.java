package com.allinweb.ch.model;

import java.util.List;

/**
 * Typed contract for the Variables Resolve Connections variable auto-resolution:
 * connects the oldest compatible variable to commands missing one, creates
 * sequential default variables (Variable_N, Left_Operand, Right_Operand) when
 * none exists, and repairs CHECKVALUE right operands.
 */
public final class VariablesVariableAutoResolveV1 {
    public static final int CONTRACT_VERSION = 1;

    private VariablesVariableAutoResolveV1() {}

    public record Request(
            Integer contractVersion,
            String requestId,
            String bindingEpoch,
            Long workspaceEpoch,
            Long baseGraphVersion,
            String graphRevision,
            List<Integer> instructionIds) {}
}

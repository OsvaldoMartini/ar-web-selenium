package com.allinweb.ch.model;

/** Typed command active/inactive request owned by the detached Variables workspace. */
public final class VariablesWorkspaceInstructionStatus {
    public static final int CONTRACT_VERSION = 1;

    private VariablesWorkspaceInstructionStatus() {}

    public record Request(
            Integer contractVersion,
            String requestId,
            String bindingEpoch,
            Long workspaceEpoch,
            Integer instructionId,
            Boolean expectedActive,
            Boolean active) {}
}

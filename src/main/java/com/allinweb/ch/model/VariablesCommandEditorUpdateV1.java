package com.allinweb.ch.model;

/** Typed UPDATE contract used only by the Variables Command Editor modal. */
public final class VariablesCommandEditorUpdateV1 {
    public static final int CONTRACT_VERSION = 1;

    private VariablesCommandEditorUpdateV1() {}

    public enum PlacementKind { KEEP, TOP, END, AFTER_INSTRUCTION }

    public enum ConfigurationKind { LOOP, REFRESH_LOOP, WAIT }

    public record Placement(PlacementKind kind, Integer referenceInstructionId) {}

    public record Configuration(
            ConfigurationKind kind,
            Integer intervalSeconds,
            Integer iterations,
            Integer waitSeconds) {}

    public record Request(
            Integer contractVersion,
            String requestId,
            String bindingEpoch,
            Long workspaceEpoch,
            Long baseGraphVersion,
            String graphRevision,
            Integer sourceInstructionId,
            Integer targetBlockId,
            Placement placement,
            Configuration configuration) {}
}

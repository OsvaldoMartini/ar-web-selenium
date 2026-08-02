package com.allinweb.ch.model;

/** Typed UPDATE contract used only by the Variables Command Editor modal. */
public final class VariablesCommandEditorUpdateV1 {
    public static final int CONTRACT_VERSION = 1;

    private VariablesCommandEditorUpdateV1() {}

    public enum PlacementKind { KEEP, TOP, END, AFTER_INSTRUCTION }

    public enum ConfigurationKind {
        LOOP,
        REFRESH_LOOP,
        WAIT,
        CHECK_VALUE,
        EXTERNAL_CHECK,
        EXCEL_WRITE,
        GOTO,
        SWIPE
    }

    public record Placement(PlacementKind kind, Integer referenceInstructionId) {}

    public record Configuration(
            ConfigurationKind kind,
            Integer intervalSeconds,
            Integer iterations,
            Integer waitSeconds,
            String operator,
            String operandKind,
            String operandRawValue,
            Integer operandVariableId,
            String outputKey,
            String outputColumn,
            String outputFile,
            String externalSourceKey,
            String formatPolicy,
            Integer count) {
        public String comparisonOperator() {
            return operator;
        }

        public Configuration withoutVariableReferences() {
            if (operandVariableId == null) return this;
            return new Configuration(
                    kind,
                    intervalSeconds,
                    iterations,
                    waitSeconds,
                    operator,
                    "VOID",
                    "",
                    null,
                    outputKey,
                    outputColumn,
                    outputFile,
                    externalSourceKey,
                    formatPolicy,
                    count);
        }
    }

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

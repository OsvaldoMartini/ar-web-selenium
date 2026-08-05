package com.allinweb.ch.model;

import java.util.List;

/** Compact contract for one CheckValue LEFT-slot persistence operation. */
public final class VariablesCheckLeftOperandV1 {
    public static final int CONTRACT_VERSION = 3;

    private VariablesCheckLeftOperandV1() {}

    public record Request(
            Integer contractVersion,
            String requestId,
            String bindingEpoch,
            Long workspaceEpoch,
            Long baseGraphVersion,
            String graphRevision,
            List<VariableBindingPatch> variableBindingPatches) {

        public Request {
            variableBindingPatches = variableBindingPatches == null
                    ? List.of() : List.copyOf(variableBindingPatches);
        }

        public VariableBindingPatch patch() {
            return variableBindingPatches.size() == 1 ? variableBindingPatches.get(0) : null;
        }

        public boolean isDisconnect() {
            VariableBindingPatch value = patch();
            return value != null && "CLEAR".equalsIgnoreCase(value.operation());
        }

        public boolean isConnect() {
            VariableBindingPatch value = patch();
            return value != null && "SET".equalsIgnoreCase(value.operation());
        }
    }

    public record VariableBindingPatch(
            Integer instructionId,
            String slot,
            String operation,
            VariableValue expected,
            VariableValue replacement) {}

    public record VariableValue(Integer value) {}
}

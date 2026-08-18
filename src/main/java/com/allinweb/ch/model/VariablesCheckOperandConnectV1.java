package com.allinweb.ch.model;

import java.util.List;

/**
 * NEW variable-connection rules, step 1 (2026-08-03): connect one variable
 * (Right_Operand) into the RIGHT spot of the React-authored CheckValue list.
 * Java persists only this independent RIGHT-slot relationship.
 */
public final class VariablesCheckOperandConnectV1 {
    public static final int CONTRACT_VERSION = 3;

    private VariablesCheckOperandConnectV1() {}

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

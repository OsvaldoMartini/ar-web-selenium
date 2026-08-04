package com.allinweb.ch.model;

import java.util.List;

/**
 * NEW variable-connection rules, step 1 (2026-08-03): connect one variable
 * (Right_Operand) into the RIGHT spot of the React-authored CheckValue list.
 * Java persists only - free spots are filled, occupied spots are skipped.
 */
public final class VariablesCheckOperandConnectV1 {
    public static final int CONTRACT_VERSION = 1;

    private VariablesCheckOperandConnectV1() {}

    public record Request(
            Integer contractVersion,
            String requestId,
            String bindingEpoch,
            Long workspaceEpoch,
            Long baseGraphVersion,
            String graphRevision,
            Integer rightVariableId,
            List<Integer> instructionIds,
            /**
             * 'CONNECT' (default) fills free RIGHT spots; 'RELEASE' clears occupied ones;
             * 'UPDATE_OPERATOR' (2026-08-04 middle-shim dropdown) changes only the stored
             * comparison operator - the right operand connectivity is left untouched.
             */
            String operation,
            String comparisonOperator) {

        public Request {
            instructionIds = instructionIds == null ? List.of() : List.copyOf(instructionIds);
        }

        public boolean isRelease() {
            return "RELEASE".equalsIgnoreCase(operation == null ? "" : operation.trim());
        }

        public boolean isUpdateOperator() {
            return "UPDATE_OPERATOR".equalsIgnoreCase(operation == null ? "" : operation.trim());
        }
    }
}

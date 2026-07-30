package com.allinweb.ch.facade.actions;

import java.util.Objects;

/**
 * Run-scoped variable value.
 *
 * <p>{@link State#VOID} means that no producer value is available. It is deliberately not
 * represented by a String sentinel because an empty String (and even the literal text "VOID") can
 * be legitimate Web data.
 */
public record RuntimeVariableValue(
        State state,
        String value,
        VoidReason voidReason) {

    public RuntimeVariableValue {
        state = Objects.requireNonNull(state, "state");
        if (state == State.VALUE) {
            value = Objects.requireNonNull(value, "value");
            if (voidReason != null) {
                throw new IllegalArgumentException("A produced value cannot have a VOID reason");
            }
        } else {
            value = null;
            voidReason = Objects.requireNonNull(voidReason, "voidReason");
        }
    }

    public static RuntimeVariableValue value(String value) {
        return new RuntimeVariableValue(State.VALUE, value, null);
    }

    public static RuntimeVariableValue voidValue(VoidReason reason) {
        return new RuntimeVariableValue(State.VOID, null, reason);
    }

    public boolean isValue() {
        return state == State.VALUE;
    }

    public boolean isVoid() {
        return state == State.VOID;
    }

    public boolean isEmptyValue() {
        return isValue() && value.isEmpty();
    }

    public enum State {
        VALUE,
        VOID
    }

    public enum VoidReason {
        NO_PRODUCER_YET,
        MISSING_BINDING,
        MISSING_PARENT,
        PRODUCER_FAILED,
        EVALUATION_FAILED,
        METADATA_UNAVAILABLE
    }
}

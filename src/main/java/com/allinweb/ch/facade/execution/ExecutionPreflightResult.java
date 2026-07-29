package com.allinweb.ch.facade.execution;

import java.util.List;
import java.util.Objects;

/** Deterministic result of the pure execution relationship preflight. */
public record ExecutionPreflightResult(
        Status status,
        ExecutionPreflightSnapshot.Owner owner,
        RunScope runScope,
        List<Integer> reachableBlockIds,
        List<Integer> reachableInstructionIds,
        List<Issue> issues) {

    public ExecutionPreflightResult {
        status = Objects.requireNonNull(status, "status");
        owner = Objects.requireNonNull(owner, "owner");
        runScope = Objects.requireNonNull(runScope, "runScope");
        reachableBlockIds =
                List.copyOf(Objects.requireNonNull(reachableBlockIds, "reachableBlockIds"));
        reachableInstructionIds =
                List.copyOf(Objects.requireNonNull(reachableInstructionIds, "reachableInstructionIds"));
        issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
        if ((status == Status.READY) != issues.isEmpty()) {
            throw new IllegalArgumentException("READY requires no issues and BLOCKED requires at least one issue");
        }
    }

    public boolean ready() {
        return status == Status.READY;
    }

    public enum Status {
        READY,
        BLOCKED
    }

    public enum RelationshipKind {
        ELEMENT_TARGET,
        VARIABLE_BINDING,
        VARIABLE_ORDER,
        LOOP_ANCHOR,
        CONDITIONAL_ROOT,
        BLOCK_TARGET,
        SNAPSHOT,
        RUN_SCOPE
    }

    /** Stable machine-readable issue codes shared later by WebSocket responses and React UX. */
    public enum IssueCode {
        DUPLICATE_BLOCK_ID,
        DUPLICATE_INSTRUCTION_ID,
        DUPLICATE_VARIABLE_ID,
        INSTRUCTION_BLOCK_NOT_FOUND,
        SELECTED_BLOCK_NOT_FOUND,

        MISSING_ELEMENT_TARGET,
        DANGLING_ELEMENT_TARGET,
        ELEMENT_TARGET_WRONG_BLOCK,
        INCOMPATIBLE_ELEMENT_TARGET,
        INACTIVE_ELEMENT_TARGET,
        ELEMENT_TARGET_ORDER,

        MISSING_VARIABLE_BINDING,
        DANGLING_VARIABLE_BINDING,
        INCOMPATIBLE_VARIABLE_TYPE,
        MISSING_RUNTIME_VALUE_WRITER,
        RUNTIME_VALUE_WRITER_AFTER_READER,
        RUNTIME_VALUE_WRITER_OUTSIDE_SCOPE,

        MISSING_LOOP_ANCHOR,
        DANGLING_LOOP_ANCHOR,
        LOOP_ANCHOR_WRONG_BLOCK,
        INCOMPATIBLE_LOOP_ANCHOR,
        INACTIVE_LOOP_ANCHOR,
        LOOP_ANCHOR_ORDER,

        CONDITIONAL_ROOT_NOT_SELF,
        ORPHAN_CONDITIONAL_BOUNDARY,
        CONDITIONAL_ROOT_MISMATCH,
        ELSEIF_AFTER_ELSE,
        DUPLICATE_ELSE,
        MISSING_ENDIF,

        MISSING_BLOCK_TARGET,
        DANGLING_BLOCK_TARGET,
        BLOCK_TARGET_EQUALS_CONTAINING_BLOCK,
        INACTIVE_BLOCK_TARGET
    }

    /**
     * One blocking relationship problem.
     *
     * <p>Relationship issues always carry the exact source instruction ID. Snapshot and run-scope
     * issues may not have an instruction source, so their instruction ID is nullable.
     */
    public record Issue(
            IssueCode code,
            RelationshipKind kind,
            Integer blockId,
            Integer instructionId,
            String message) {

        public Issue {
            code = Objects.requireNonNull(code, "code");
            kind = Objects.requireNonNull(kind, "kind");
            message = Objects.requireNonNull(message, "message");
        }
    }
}

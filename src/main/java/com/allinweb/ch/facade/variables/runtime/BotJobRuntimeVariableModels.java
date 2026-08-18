package com.allinweb.ch.facade.variables.runtime;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Canonical domain contracts for durable Bot Job variable definitions and runtime values. */
public final class BotJobRuntimeVariableModels {
    private BotJobRuntimeVariableModels() {}

    public record OwnerKey(int homeBankingId, int botJobId) {
        public OwnerKey {
            if (homeBankingId <= 0 || botJobId <= 0) {
                throw new IllegalArgumentException("Bot Job variable owner IDs must be positive");
            }
        }
    }

    public record Definition(
            long id,
            OwnerKey owner,
            String type,
            String name,
            String configuredValue,
            String localFormat,
            String delimiter,
            Long producerInstructionId,
            Instant createdAt,
            Instant updatedAt) {
        public Definition {
            if (id <= 0L) {
                throw new IllegalArgumentException("Variable ID must be positive");
            }
            Objects.requireNonNull(owner, "owner");
            name = requireName(name);
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(updatedAt, "updatedAt");
        }
    }

    public record DefinitionDraft(
            String type,
            String name,
            String configuredValue,
            String localFormat,
            String delimiter,
            Long producerInstructionId,
            ValueState initialState,
            String initialRawValue) {
        public DefinitionDraft {
            name = requireName(name);
            initialState = initialState == null ? ValueState.VOID : initialState;
            if (initialState == ValueState.VALUE) {
                Objects.requireNonNull(
                        initialRawValue,
                        "VALUE initial state requires an exact raw value; an empty String is valid");
            } else if (initialRawValue != null) {
                throw new IllegalArgumentException("VOID initial state cannot carry raw text");
            }
        }

        public static DefinitionDraft voidDefinition(String name) {
            return new DefinitionDraft(
                    "$String",
                    name,
                    null,
                    null,
                    null,
                    null,
                    ValueState.VOID,
                    null);
        }
    }

    public record DefinitionPatch(
            String type,
            String name,
            String configuredValue,
            String localFormat,
            String delimiter,
            Long producerInstructionId) {
        public DefinitionPatch {
            name = requireName(name);
        }
    }

    public enum ValueState {
        VALUE,
        VOID
    }

    public enum VoidReason {
        NO_PRODUCER_YET,
        MISSING_BINDING,
        MISSING_PARENT,
        PRODUCER_FAILED,
        EVALUATION_FAILED,
        METADATA_UNAVAILABLE,
        CLIENT_RESET,
        DEFINITION_DELETED
    }

    public enum ValueSource {
        EXECUTION,
        MANUAL,
        RESET,
        SYSTEM,
        MIGRATION
    }

    public record RuntimeValue(
            long variableId,
            ValueState state,
            String rawValue,
            VoidReason voidReason,
            ValueSource source,
            long entryRevision,
            Long lastExecutionId,
            Instant updatedAt) {
        public RuntimeValue {
            if (variableId <= 0L || entryRevision < 0L) {
                throw new IllegalArgumentException("Runtime variable ID/revision is invalid");
            }
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(updatedAt, "updatedAt");
            if (state == ValueState.VALUE) {
                Objects.requireNonNull(
                        rawValue,
                        "VALUE requires exact raw text; an empty String is a valid value");
                if (voidReason != null) {
                    throw new IllegalArgumentException("VALUE cannot carry a VOID reason");
                }
            } else {
                if (rawValue != null) {
                    throw new IllegalArgumentException("VOID cannot carry raw text");
                }
                Objects.requireNonNull(voidReason, "VOID requires a reason");
            }
        }

        public static RuntimeValue initialVoid(long variableId, Instant now) {
            return new RuntimeValue(
                    variableId,
                    ValueState.VOID,
                    null,
                    VoidReason.NO_PRODUCER_YET,
                    ValueSource.SYSTEM,
                    0L,
                    null,
                    now);
        }
    }

    public record MemoryState(
            OwnerKey owner,
            long runtimeRevision,
            long resetGeneration,
            long nextVariableId,
            Instant createdAt,
            Instant updatedAt) {
        public MemoryState {
            Objects.requireNonNull(owner, "owner");
            if (runtimeRevision < 0L || resetGeneration < 0L || nextVariableId <= 0L) {
                throw new IllegalArgumentException("Runtime memory counters are invalid");
            }
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(updatedAt, "updatedAt");
        }
    }

    public record Snapshot(
            MemoryState memory,
            List<Definition> definitions,
            List<RuntimeValue> values) {
        public Snapshot {
            Objects.requireNonNull(memory, "memory");
            definitions = definitions == null ? List.of() : List.copyOf(definitions);
            values = values == null ? List.of() : List.copyOf(values);
        }
    }

    public enum MutationStatus {
        APPLIED,
        STALE_RUNTIME_REVISION,
        STALE_ENTRY_REVISION,
        VARIABLE_NOT_FOUND,
        OWNER_NOT_FOUND,
        DUPLICATE_PRODUCER,
        PRODUCER_NOT_FOUND
    }

    public record MutationResult(
            MutationStatus status,
            Snapshot snapshot,
            Definition definition,
            RuntimeValue value,
            String message) {
        public MutationResult {
            Objects.requireNonNull(status, "status");
        }

        public boolean applied() {
            return status == MutationStatus.APPLIED;
        }
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Variable name is required");
        }
        return name;
    }
}

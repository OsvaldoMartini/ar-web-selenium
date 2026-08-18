package com.allinweb.ch.facade.variables.runtime;

import com.allinweb.ch.db.BotJobRuntimeMemoryRepository;
import com.allinweb.ch.db.BotJobVariableDefinitionRepository;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.Definition;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.DefinitionDraft;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.DefinitionPatch;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.MemoryState;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.MutationResult;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.MutationStatus;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.OwnerKey;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.RuntimeValue;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.Snapshot;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.ValueSource;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.ValueState;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.VoidReason;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Transaction boundary and domain policy for durable Bot Job variable memory.
 *
 * <p>Database state is authoritative. Callers broadcast the returned committed snapshot only after
 * their surrounding transaction commits. React may optimistically mirror it, but neither React nor
 * the process-local legacy registry is a durable authority.
 */
public final class BotJobRuntimeVariableService {
    private final BotJobVariableDefinitionRepository definitions;
    private final BotJobRuntimeMemoryRepository memory;

    public BotJobRuntimeVariableService() {
        this(
                new BotJobVariableDefinitionRepository(),
                new BotJobRuntimeMemoryRepository());
    }

    public BotJobRuntimeVariableService(
            BotJobVariableDefinitionRepository definitions,
            BotJobRuntimeMemoryRepository memory) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.memory = Objects.requireNonNull(memory, "memory");
    }

    /** Loads durable definitions and values, repairing a missing value as initial VOID. */
    public Snapshot hydrate(Connection connection, OwnerKey owner) throws SQLException {
        return atomic(connection, () -> hydrateInternal(connection, owner));
    }

    /**
     * Creates an owner-scoped durable definition and its initial runtime row.
     *
     * <p>Independent variables have a null producer and begin VOID unless the explicit UI contract
     * supplies {@code initialState=VALUE}. Empty initial raw text is preserved as VALUE("").
     */
    public MutationResult createDefinition(
            Connection connection,
            OwnerKey owner,
            DefinitionDraft draft,
            Long expectedRuntimeRevision)
            throws SQLException {
        Objects.requireNonNull(draft, "draft");
        return mutation(connection, owner, () -> {
            MemoryState current = requireExpectedRevision(
                    memory.loadOrCreateMemory(connection, owner),
                    expectedRuntimeRevision);
            if (!definitions.producerBelongsToOwner(
                    connection, owner, draft.producerInstructionId())) {
                throw refused(
                        MutationStatus.PRODUCER_NOT_FOUND,
                        "The producer instruction does not belong to this Bot Job");
            }
            if (definitions.producerAlreadyDefined(
                    connection, owner, draft.producerInstructionId(), null)) {
                throw refused(
                        MutationStatus.DUPLICATE_PRODUCER,
                        "This producer already owns a variable definition");
            }

            long id = memory.reserveNextVariableId(
                    connection, owner, current.nextVariableId());
            if (id < 0L) {
                throw refused(
                        MutationStatus.STALE_RUNTIME_REVISION,
                        "The variable ID allocator changed");
            }
            Definition created = definitions.insert(connection, owner, id, draft);
            RuntimeValue initial = draft.initialState() == ValueState.VALUE
                    ? memory.insertInitial(
                            connection,
                            owner,
                            id,
                            ValueState.VALUE,
                            draft.initialRawValue(),
                            null,
                            ValueSource.MANUAL)
                    : memory.insertInitial(
                            connection,
                            owner,
                            id,
                            ValueState.VOID,
                            null,
                            VoidReason.NO_PRODUCER_YET,
                            ValueSource.SYSTEM);
            advanceRevision(connection, owner, current, false);
            return applied(
                    hydrateInternal(connection, owner),
                    created,
                    initial,
                    "Variable definition created");
        });
    }

    public MutationResult updateDefinition(
            Connection connection,
            OwnerKey owner,
            long variableId,
            DefinitionPatch patch,
            Long expectedRuntimeRevision)
            throws SQLException {
        Objects.requireNonNull(patch, "patch");
        return mutation(connection, owner, () -> {
            MemoryState current = requireExpectedRevision(
                    memory.loadOrCreateMemory(connection, owner),
                    expectedRuntimeRevision);
            if (!definitions.producerBelongsToOwner(
                    connection, owner, patch.producerInstructionId())) {
                throw refused(
                        MutationStatus.PRODUCER_NOT_FOUND,
                        "The producer instruction does not belong to this Bot Job");
            }
            if (definitions.producerAlreadyDefined(
                    connection, owner, patch.producerInstructionId(), variableId)) {
                throw refused(
                        MutationStatus.DUPLICATE_PRODUCER,
                        "This producer already owns another variable definition");
            }
            Optional<Definition> updated =
                    definitions.update(connection, owner, variableId, patch);
            if (updated.isEmpty()) {
                throw refused(
                        MutationStatus.VARIABLE_NOT_FOUND,
                        "Variable definition was not found");
            }
            advanceRevision(connection, owner, current, false);
            return applied(
                    hydrateInternal(connection, owner),
                    updated.get(),
                    memory.loadValue(connection, owner, variableId).orElse(null),
                    "Variable definition updated");
        });
    }

    /**
     * Deletes selected definitions and detaches instruction consumers in the same transaction.
     *
     * <p>Runtime rows are explicitly removed before definitions, so cleanup does not depend on a
     * JDBC driver's foreign-key/cascade setting.
     */
    public MutationResult deleteDefinitions(
            Connection connection,
            OwnerKey owner,
            Collection<Long> variableIds,
            Long expectedRuntimeRevision)
            throws SQLException {
        Set<Long> ids = sanitizeIds(variableIds);
        return mutation(connection, owner, () -> {
            MemoryState current = requireExpectedRevision(
                    memory.loadOrCreateMemory(connection, owner),
                    expectedRuntimeRevision);
            for (long variableId : ids) {
                if (definitions.load(connection, owner, variableId).isEmpty()) {
                    throw refused(
                            MutationStatus.VARIABLE_NOT_FOUND,
                            "Variable definition " + variableId + " was not found");
                }
            }
            detachInstructions(connection, owner, ids);
            for (long variableId : ids) {
                memory.deleteValue(connection, owner, variableId);
                if (definitions.delete(connection, owner, variableId) != 1) {
                    throw new SQLException(
                            "Variable definition " + variableId
                                    + " was not deleted exactly once");
                }
            }
            if (!ids.isEmpty()) {
                advanceRevision(connection, owner, current, false);
            }
            return applied(
                    hydrateInternal(connection, owner),
                    null,
                    null,
                    ids.size() + " variable definition(s) deleted");
        });
    }

    /**
     * Persists exact raw text. No trim, parsing, locale conversion, or sentinel substitution is
     * performed.
     */
    public MutationResult setValue(
            Connection connection,
            OwnerKey owner,
            long variableId,
            String rawValue,
            ValueSource source,
            Long lastExecutionId,
            Long expectedEntryRevision)
            throws SQLException {
        return setValue(
                connection,
                owner,
                variableId,
                rawValue,
                source,
                lastExecutionId,
                null,
                expectedEntryRevision);
    }

    public MutationResult setValue(
            Connection connection,
            OwnerKey owner,
            long variableId,
            String rawValue,
            ValueSource source,
            Long lastExecutionId,
            Long expectedRuntimeRevision,
            Long expectedEntryRevision)
            throws SQLException {
        Objects.requireNonNull(
                rawValue,
                "A VALUE requires exact raw text; use clearValue for VOID");
        Objects.requireNonNull(source, "source");
        return replaceValue(
                connection,
                owner,
                variableId,
                ValueState.VALUE,
                rawValue,
                null,
                source,
                lastExecutionId,
                expectedRuntimeRevision,
                expectedEntryRevision);
    }

    public MutationResult clearValue(
            Connection connection,
            OwnerKey owner,
            long variableId,
            VoidReason reason,
            ValueSource source,
            Long lastExecutionId,
            Long expectedEntryRevision)
            throws SQLException {
        return clearValue(
                connection,
                owner,
                variableId,
                reason,
                source,
                lastExecutionId,
                null,
                expectedEntryRevision);
    }

    public MutationResult clearValue(
            Connection connection,
            OwnerKey owner,
            long variableId,
            VoidReason reason,
            ValueSource source,
            Long lastExecutionId,
            Long expectedRuntimeRevision,
            Long expectedEntryRevision)
            throws SQLException {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(source, "source");
        return replaceValue(
                connection,
                owner,
                variableId,
                ValueState.VOID,
                null,
                reason,
                source,
                lastExecutionId,
                expectedRuntimeRevision,
                expectedEntryRevision);
    }

    /**
     * Atomically sets all existing values to VOID and advances both global revision and reset
     * generation. Definitions and instruction relationships are retained.
     */
    public MutationResult clearAll(
            Connection connection,
            OwnerKey owner,
            long expectedRuntimeRevision,
            VoidReason reason,
            ValueSource source,
            Long lastExecutionId)
            throws SQLException {
        return mutation(connection, owner, () -> {
            MemoryState current = requireExpectedRevision(
                    memory.loadOrCreateMemory(connection, owner),
                    expectedRuntimeRevision);
            ensureRuntimeRows(connection, owner);
            memory.clearAllValues(
                    connection,
                    owner,
                    Objects.requireNonNull(reason, "reason"),
                    Objects.requireNonNull(source, "source"),
                    lastExecutionId);
            advanceRevision(connection, owner, current, true);
            return applied(
                    snapshot(connection, owner),
                    null,
                    null,
                    "All runtime values cleared to VOID");
        });
    }

    /** RESET execution policy; KEEP policy calls {@link #hydrate(Connection, OwnerKey)} only. */
    public MutationResult resetForExecution(
            Connection connection,
            OwnerKey owner,
            Long lastExecutionId)
            throws SQLException {
        MemoryState current = hydrate(connection, owner).memory();
        return clearAll(
                connection,
                owner,
                current.runtimeRevision(),
                VoidReason.CLIENT_RESET,
                ValueSource.RESET,
                lastExecutionId);
    }

    private MutationResult replaceValue(
            Connection connection,
            OwnerKey owner,
            long variableId,
            ValueState state,
            String rawValue,
            VoidReason reason,
            ValueSource source,
            Long executionId,
            Long expectedRuntimeRevision,
            Long expectedEntryRevision)
            throws SQLException {
        return mutation(connection, owner, () -> {
            if (definitions.load(connection, owner, variableId).isEmpty()) {
                throw refused(
                        MutationStatus.VARIABLE_NOT_FOUND,
                        "Variable definition was not found");
            }
            MemoryState current = requireExpectedRevision(
                    memory.loadOrCreateMemory(connection, owner),
                    expectedRuntimeRevision);
            RuntimeValue existing = memory.loadValue(connection, owner, variableId)
                    .orElseGet(() -> {
                        try {
                            return memory.insertInitial(
                                    connection,
                                    owner,
                                    variableId,
                                    ValueState.VOID,
                                    null,
                                    VoidReason.NO_PRODUCER_YET,
                                    ValueSource.SYSTEM);
                        } catch (SQLException exception) {
                            throw new RepositoryRuntimeException(exception);
                        }
                    });
            if (expectedEntryRevision != null
                    && existing.entryRevision() != expectedEntryRevision) {
                throw refused(
                        MutationStatus.STALE_ENTRY_REVISION,
                        "Runtime value revision changed");
            }
            RuntimeValue next = new RuntimeValue(
                    variableId,
                    state,
                    rawValue,
                    reason,
                    source,
                    existing.entryRevision() + 1L,
                    executionId,
                    memory.now());
            if (!memory.compareAndSetValue(
                    connection, owner, next, existing.entryRevision())) {
                throw refused(
                        MutationStatus.STALE_ENTRY_REVISION,
                        "Runtime value revision changed");
            }
            advanceRevision(connection, owner, current, false);
            return applied(
                    snapshot(connection, owner),
                    definitions.load(connection, owner, variableId).orElse(null),
                    next,
                    state == ValueState.VALUE
                            ? "Runtime value saved"
                            : "Runtime value cleared to VOID");
        });
    }

    private Snapshot hydrateInternal(Connection connection, OwnerKey owner) throws SQLException {
        if (!definitions.ownerExists(connection, owner)) {
            throw refused(MutationStatus.OWNER_NOT_FOUND, "Bot Job owner was not found");
        }
        memory.loadOrCreateMemory(connection, owner);
        ensureRuntimeRows(connection, owner);
        return snapshot(connection, owner);
    }

    private void ensureRuntimeRows(Connection connection, OwnerKey owner) throws SQLException {
        Set<Long> existingIds = memory.loadValues(connection, owner).stream()
                .map(RuntimeValue::variableId)
                .collect(java.util.stream.Collectors.toSet());
        for (Definition definition : definitions.loadAll(connection, owner)) {
            if (!existingIds.contains(definition.id())) {
                memory.insertInitial(
                        connection,
                        owner,
                        definition.id(),
                        ValueState.VOID,
                        null,
                        VoidReason.NO_PRODUCER_YET,
                        ValueSource.SYSTEM);
            }
        }
    }

    private Snapshot snapshot(Connection connection, OwnerKey owner) throws SQLException {
        return new Snapshot(
                memory.loadMemory(connection, owner)
                        .orElseThrow(() -> new SQLException("Runtime memory is missing")),
                definitions.loadAll(connection, owner),
                memory.loadValues(connection, owner));
    }

    private MemoryState requireExpectedRevision(
            MemoryState current,
            Long expectedRuntimeRevision)
            throws MutationRefused {
        if (expectedRuntimeRevision != null
                && current.runtimeRevision() != expectedRuntimeRevision) {
            throw refused(
                    MutationStatus.STALE_RUNTIME_REVISION,
                    "Runtime memory revision changed");
        }
        return current;
    }

    private MemoryState requireExpectedRevision(
            MemoryState current,
            long expectedRuntimeRevision)
            throws MutationRefused {
        return requireExpectedRevision(current, Long.valueOf(expectedRuntimeRevision));
    }

    private void advanceRevision(
            Connection connection,
            OwnerKey owner,
            MemoryState current,
            boolean reset)
            throws SQLException {
        if (memory.compareAndSetMemoryRevision(
                        connection, owner, current.runtimeRevision(), reset)
                .isEmpty()) {
            throw refused(
                    MutationStatus.STALE_RUNTIME_REVISION,
                    "Runtime memory revision changed");
        }
    }

    private static void detachInstructions(
            Connection connection,
            OwnerKey owner,
            Set<Long> variableIds)
            throws SQLException {
        if (variableIds.isEmpty()) {
            return;
        }
        String placeholders = String.join(
                ",",
                java.util.Collections.nCopies(variableIds.size(), "?"));
        String sql = "DELETE FROM instruction_variable_slot"
                + " WHERE home_banking_id=? AND bot_job_id=?"
                + " AND variable_id IN (" + placeholders + ")";
        try (PreparedStatement update = connection.prepareStatement(sql)) {
            update.setInt(1, owner.homeBankingId());
            update.setInt(2, owner.botJobId());
            int parameter = 3;
            for (Long variableId : variableIds) {
                update.setLong(parameter++, variableId);
            }
            update.executeUpdate();
        }
        String legacySql = "UPDATE instruction SET variable_id=NULL"
                + " WHERE bot_job_id=? AND variable_id IN (" + placeholders + ")";
        try (PreparedStatement update = connection.prepareStatement(legacySql)) {
            update.setInt(1, owner.botJobId());
            int parameter = 2;
            for (Long variableId : variableIds) {
                update.setLong(parameter++, variableId);
            }
            update.executeUpdate();
        }
    }

    private static Set<Long> sanitizeIds(Collection<Long> variableIds) {
        if (variableIds == null || variableIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> ids = new HashSet<>();
        for (Long id : variableIds) {
            if (id == null || id <= 0L) {
                throw new IllegalArgumentException("Variable IDs must be positive");
            }
            ids.add(id);
        }
        return Set.copyOf(ids);
    }

    private MutationResult mutation(
            Connection connection,
            OwnerKey owner,
            SqlSupplier<MutationResult> action)
            throws SQLException {
        Objects.requireNonNull(owner, "owner");
        if (!definitions.ownerExists(connection, owner)) {
            return new MutationResult(
                    MutationStatus.OWNER_NOT_FOUND,
                    null,
                    null,
                    null,
                    "Bot Job owner was not found");
        }
        try {
            return atomic(connection, action);
        } catch (MutationRefused refused) {
            Snapshot current;
            try {
                current = hydrate(connection, owner);
            } catch (SQLException ignored) {
                current = null;
            }
            return new MutationResult(
                    refused.status,
                    current,
                    null,
                    null,
                    refused.getMessage());
        } catch (RepositoryRuntimeException wrapper) {
            throw wrapper.sqlException;
        }
    }

    private static MutationResult applied(
            Snapshot snapshot,
            Definition definition,
            RuntimeValue value,
            String message) {
        return new MutationResult(
                MutationStatus.APPLIED,
                snapshot,
                definition,
                value,
                message);
    }

    private static MutationRefused refused(
            MutationStatus status,
            String message) {
        return new MutationRefused(status, message);
    }

    private static <T> T atomic(Connection connection, SqlSupplier<T> action)
            throws SQLException {
        if (connection == null || connection.isClosed()) {
            throw new SQLException("An open database connection is required");
        }
        boolean ownedTransaction = connection.getAutoCommit();
        Savepoint savepoint = null;
        if (ownedTransaction) {
            connection.setAutoCommit(false);
        } else {
            savepoint = connection.setSavepoint();
        }
        try {
            T result = action.get();
            if (ownedTransaction) {
                connection.commit();
            } else if (savepoint != null) {
                connection.releaseSavepoint(savepoint);
            }
            return result;
        } catch (SQLException | RuntimeException exception) {
            if (ownedTransaction) {
                connection.rollback();
            } else if (savepoint != null) {
                connection.rollback(savepoint);
            }
            throw exception;
        } finally {
            if (ownedTransaction) {
                connection.setAutoCommit(true);
            }
        }
    }

    @FunctionalInterface
    private interface SqlSupplier<T> {
        T get() throws SQLException;
    }

    private static final class MutationRefused extends SQLException {
        private final MutationStatus status;

        private MutationRefused(MutationStatus status, String message) {
            super(message);
            this.status = status;
        }
    }

    private static final class RepositoryRuntimeException extends RuntimeException {
        private final SQLException sqlException;

        private RepositoryRuntimeException(SQLException sqlException) {
            super(sqlException);
            this.sqlException = sqlException;
        }
    }
}

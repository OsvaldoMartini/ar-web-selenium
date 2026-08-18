package com.allinweb.ch.facade.actions;

import com.allinweb.ch.facade.actions.RuntimeVariableValue.VoidReason;
import com.allinweb.ch.facade.actions.RuntimeVariableMemoryRegistry.BotJobKey;
import com.allinweb.ch.facade.actions.RuntimeVariableMemoryRegistry.Definition;
import com.allinweb.ch.facade.actions.RuntimeVariableMemoryRegistry.ValueSource;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.MutationResult;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.MutationStatus;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.OwnerKey;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableService;
import com.allinweb.ch.model.VariableLoadDTO;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Runtime variable access for either one isolated caller or one persistent Bot Job memory.
 *
 * <p>The store is keyed by the stable numeric variable ID. Display names are never keys, which
 * prevents unrelated instructions with missing metadata from sharing one placeholder entry.
 */
public final class RuntimeVariableStore {
    private final Map<Integer, RuntimeVariableValue> values = new HashMap<>();
    /**
     * Values visible only to this execution after a durable mutation fails.
     *
     * <p>The shared cache must continue to represent committed database state. At the same time,
     * an execution must not consume an older committed VALUE after its current producer failed to
     * persist. This overlay gives downstream commands VOID for the remainder of this store's
     * lifetime without publishing uncommitted state to other pages or executions.
     */
    private final Map<Integer, RuntimeVariableValue> executionOverrides = new HashMap<>();
    private final Set<Integer> knownVariableIds = new HashSet<>();
    private final RuntimeVariableMemoryRegistry registry;
    private final BotJobKey owner;
    private final BotJobRuntimeVariableService durableService;
    private final OwnerKey durableOwner;
    private final SqlConnectionProvider connectionProvider;
    private VoidReason unresolvedReason = VoidReason.NO_PRODUCER_YET;
    private boolean metadataAvailable = true;

    /** Creates an isolated store for non-Bot-Job callers and focused unit tests. */
    public RuntimeVariableStore() {
        registry = null;
        owner = null;
        durableService = null;
        durableOwner = null;
        connectionProvider = null;
    }

    /**
     * Creates a store backed by durable Bot Job memory and a process-local read cache.
     *
     * <p>Persistence always completes before the cache is replaced. If durable storage is
     * temporarily unavailable, variable commands resolve to VOID/failure without blocking the Bot
     * Job execution.
     */
    public RuntimeVariableStore(int homeBankingId, int botJobId) {
        if (homeBankingId > 0 && botJobId > 0) {
            registry = RuntimeVariableMemoryRegistry.getInstance();
            owner = new BotJobKey(homeBankingId, botJobId);
            durableService = new BotJobRuntimeVariableService();
            durableOwner = new OwnerKey(homeBankingId, botJobId);
            connectionProvider = () -> PerformDataBase.getInstance().getConnection();
            hydrateDurableCache();
        } else {
            // Runtime variables are optional execution support. An incomplete launch identity
            // must never prevent a Bot Job from running; fall back to the isolated VOID-aware
            // store used by legacy/non-Bot-Job callers.
            registry = null;
            owner = null;
            durableService = null;
            durableOwner = null;
            connectionProvider = null;
        }
    }

    /** Package-private cache-only constructor for deterministic focused tests. */
    RuntimeVariableStore(
            RuntimeVariableMemoryRegistry registry,
            BotJobKey owner) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.owner = Objects.requireNonNull(owner, "owner");
        durableService = null;
        durableOwner = null;
        connectionProvider = null;
    }

    /**
     * Package-private durable constructor for focused failure-path tests.
     *
     * <p>The supplied provider lets tests emulate a database outage without changing the shared
     * registry or introducing a second persistence implementation.
     */
    RuntimeVariableStore(
            RuntimeVariableMemoryRegistry registry,
            BotJobKey owner,
            BotJobRuntimeVariableService durableService,
            OwnerKey durableOwner,
            SqlConnectionProvider connectionProvider) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.durableService = Objects.requireNonNull(durableService, "durableService");
        this.durableOwner = Objects.requireNonNull(durableOwner, "durableOwner");
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider");
        hydrateDurableCache();
    }

    /**
     * Legacy name retained for callers outside the runtime backend.
     *
     * <p>Definition refresh no longer clears produced values. Runtime memory is intentionally
     * retained until a command or authorized manual edit changes it.
     */
    public void reset(
            Collection<VariableLoadDTO> definitions,
            boolean metadataAvailable) {
        reconcileDefinitions(definitions, metadataAvailable);
    }

    public void reconcileDefinitions(
            Collection<VariableLoadDTO> definitions,
            boolean metadataAvailable) {
        if (registry != null) {
            if (hydrateDurableCache()) {
                return;
            }
            List<Definition> runtimeDefinitions = definitions == null
                    ? List.of()
                    : definitions.stream()
                            .filter(Objects::nonNull)
                            .filter(variable -> validId(variable.getId()))
                            .map(variable -> new Definition(
                                    variable.getId(),
                                    variable.getName(),
                                    variable.getType()))
                            .toList();
            if (metadataAvailable) {
                Set<Integer> currentDefinitionIds = runtimeDefinitions.stream()
                        .map(Definition::variableId)
                        .collect(java.util.stream.Collectors.toSet());
                executionOverrides.keySet().removeIf(
                        variableId -> !currentDefinitionIds.contains(variableId));
            }
            registry.reconcileDefinitions(
                    owner, runtimeDefinitions, metadataAvailable);
            return;
        }

        this.metadataAvailable = metadataAvailable;
        unresolvedReason = metadataAvailable
                ? VoidReason.NO_PRODUCER_YET
                : VoidReason.METADATA_UNAVAILABLE;
        if (!metadataAvailable) {
            return;
        }

        Set<Integer> nextIds = definitions == null
                ? Set.of()
                : definitions.stream()
                .filter(Objects::nonNull)
                .map(VariableLoadDTO::getId)
                .filter(RuntimeVariableStore::validId)
                .collect(java.util.stream.Collectors.toSet());
        knownVariableIds.clear();
        knownVariableIds.addAll(nextIds);
        values.keySet().removeIf(id -> !nextIds.contains(id));
        nextIds.forEach(id -> values.putIfAbsent(
                id, RuntimeVariableValue.voidValue(unresolvedReason)));
    }

    public RuntimeVariableValue read(Integer variableId) {
        if (validId(variableId)) {
            RuntimeVariableValue executionValue = executionOverrides.get(variableId);
            if (executionValue != null) return executionValue;
        }
        if (registry != null) return registry.read(owner, variableId);
        if (!validId(variableId)) {
            return RuntimeVariableValue.voidValue(VoidReason.MISSING_BINDING);
        }
        RuntimeVariableValue currentValue = values.get(variableId);
        if (currentValue != null) {
            return currentValue;
        }
        if (!metadataAvailable) {
            return RuntimeVariableValue.voidValue(VoidReason.METADATA_UNAVAILABLE);
        }
        if (!knownVariableIds.contains(variableId)) {
            return RuntimeVariableValue.voidValue(VoidReason.MISSING_BINDING);
        }
        return RuntimeVariableValue.voidValue(unresolvedReason);
    }

    public void markVoid(Integer variableId, VoidReason reason) {
        if (durableService != null) {
            if (!validId(variableId) || reason == null) return;
            try (Connection connection = connectionProvider.getConnection()) {
                MutationResult result = durableService.clearValue(
                        connection,
                        durableOwner,
                        variableId.longValue(),
                        durableReason(reason),
                        com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels
                                .ValueSource.EXECUTION,
                        null,
                        null,
                        null);
                if (!result.applied() && retryable(result.status())) {
                    result = durableService.clearValue(
                            connection,
                            durableOwner,
                            variableId.longValue(),
                            durableReason(reason),
                            com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels
                                    .ValueSource.EXECUTION,
                            null,
                            null,
                            null);
                }
                if (result.applied() && result.snapshot() != null) {
                    registry.hydrateDurableSnapshot(result.snapshot());
                    executionOverrides.remove(variableId);
                } else {
                    executionOverrides.put(
                            variableId, RuntimeVariableValue.voidValue(reason));
                }
            } catch (SQLException | RuntimeException ignored) {
                // Runtime variables are optional. A persistence outage yields VOID to consumers
                // but must never stop navigation or the Bot Job execution.
                executionOverrides.put(
                        variableId, RuntimeVariableValue.voidValue(reason));
            }
            return;
        }
        if (registry != null) {
            registry.markVoid(owner, variableId, reason, ValueSource.EXECUTION);
            return;
        }
        if (validId(variableId)) {
            values.put(variableId, RuntimeVariableValue.voidValue(reason));
        }
    }

    public boolean write(Integer variableId, String value) {
        if (durableService != null) {
            if (!validId(variableId) || value == null) return false;
            try (Connection connection = connectionProvider.getConnection()) {
                MutationResult result = durableService.setValue(
                        connection,
                        durableOwner,
                        variableId.longValue(),
                        value,
                        com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels
                                .ValueSource.EXECUTION,
                        null,
                        null,
                        null);
                if (!result.applied() && retryable(result.status())) {
                    result = durableService.setValue(
                            connection,
                            durableOwner,
                            variableId.longValue(),
                            value,
                            com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels
                                    .ValueSource.EXECUTION,
                            null,
                            null,
                            null);
                }
                if (!result.applied() || result.snapshot() == null) {
                    executionOverrides.put(
                            variableId,
                            RuntimeVariableValue.voidValue(VoidReason.PRODUCER_FAILED));
                    return false;
                }
                registry.hydrateDurableSnapshot(result.snapshot());
                executionOverrides.remove(variableId);
                return true;
            } catch (SQLException | RuntimeException ignored) {
                // Do not publish an uncommitted process-local value. Downstream commands in this
                // execution see VOID instead of reusing a stale committed VALUE; other pages and
                // executions continue to see the database-backed shared cache.
                executionOverrides.put(
                        variableId,
                        RuntimeVariableValue.voidValue(VoidReason.PRODUCER_FAILED));
                return false;
            }
        }
        if (registry != null) {
            return registry.write(owner, variableId, value, ValueSource.EXECUTION);
        }
        if (!validId(variableId) || value == null) {
            return false;
        }
        // A positive instruction-carried ID is a safe run-scoped key even when metadata is
        // temporarily unavailable. It never persists or invents a database relationship, and it
        // lets a later producer recover VOID for downstream commands in the same run.
        values.put(variableId, RuntimeVariableValue.value(value));
        return true;
    }

    private static boolean validId(Integer variableId) {
        return variableId != null && variableId > 0;
    }

    private static boolean retryable(MutationStatus status) {
        return status == MutationStatus.STALE_RUNTIME_REVISION
                || status == MutationStatus.STALE_ENTRY_REVISION;
    }

    private boolean hydrateDurableCache() {
        if (durableService == null) return false;
        try (Connection connection = connectionProvider.getConnection()) {
            registry.hydrateDurableSnapshot(
                    durableService.hydrate(connection, durableOwner));
            return true;
        } catch (SQLException | RuntimeException ignored) {
            return false;
        }
    }

    private static com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.VoidReason
            durableReason(VoidReason reason) {
        return switch (reason) {
            case NO_PRODUCER_YET ->
                    com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.VoidReason
                            .NO_PRODUCER_YET;
            case MISSING_BINDING ->
                    com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.VoidReason
                            .MISSING_BINDING;
            case MISSING_PARENT ->
                    com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.VoidReason
                            .MISSING_PARENT;
            case PRODUCER_FAILED ->
                    com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.VoidReason
                            .PRODUCER_FAILED;
            case EVALUATION_FAILED ->
                    com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.VoidReason
                            .EVALUATION_FAILED;
            case METADATA_UNAVAILABLE ->
                    com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.VoidReason
                            .METADATA_UNAVAILABLE;
        };
    }

    @FunctionalInterface
    interface SqlConnectionProvider {
        Connection getConnection() throws SQLException;
    }
}

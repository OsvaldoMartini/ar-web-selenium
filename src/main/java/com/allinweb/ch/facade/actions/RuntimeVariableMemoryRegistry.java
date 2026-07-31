package com.allinweb.ch.facade.actions;

import com.allinweb.ch.facade.actions.RuntimeVariableValue.State;
import com.allinweb.ch.facade.actions.RuntimeVariableValue.VoidReason;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Process-local runtime variable memory, isolated by the authoritative Bot Job owner.
 *
 * <p>Values are keyed only by stable numeric variable IDs. Definition reconciliation never clears
 * an existing value: a value remains available until an executing command or an authorized manual
 * edit changes it. New definitions begin as an explicit {@link State#VOID} value.
 */
public final class RuntimeVariableMemoryRegistry {

    private static final RuntimeVariableMemoryRegistry INSTANCE =
            new RuntimeVariableMemoryRegistry();

    private final Map<BotJobKey, JobMemory> jobs = new ConcurrentHashMap<>();
    private final List<ChangeListener> listeners = new CopyOnWriteArrayList<>();

    public static RuntimeVariableMemoryRegistry getInstance() {
        return INSTANCE;
    }

    public void addChangeListener(ChangeListener listener) {
        if (listener != null) listeners.add(listener);
    }

    /**
     * Reconciles the definition catalog while preserving values for IDs that still exist.
     *
     * <p>When metadata is unavailable the last known catalog is retained. A successful empty
     * catalog is different: it removes every definition and value for this Bot Job.
     */
    public Snapshot reconcileDefinitions(
            BotJobKey owner,
            Collection<Definition> definitions,
            boolean metadataAvailable) {
        Objects.requireNonNull(owner, "owner");
        JobMemory memory = jobs.computeIfAbsent(owner, ignored -> new JobMemory());
        synchronized (memory) {
            memory.reconcile(definitions, metadataAvailable);
            return memory.snapshot(owner);
        }
    }

    public Snapshot snapshot(BotJobKey owner) {
        Objects.requireNonNull(owner, "owner");
        JobMemory memory = jobs.get(owner);
        if (memory == null) {
            return new Snapshot(
                    owner.homeBankingId(),
                    owner.botJobId(),
                    0L,
                    false,
                    List.of());
        }
        synchronized (memory) {
            return memory.snapshot(owner);
        }
    }

    /**
     * Replaces one process-local execution cache from the committed durable snapshot.
     *
     * <p>This registry is deliberately only a fast execution cache. The database snapshot is the
     * authority: definitions, exact raw values, VOID states, entry revisions, and the Bot Job-wide
     * revision are copied without merging process-local values.
     */
    public Snapshot hydrateDurableSnapshot(
            com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.Snapshot durable) {
        Objects.requireNonNull(durable, "durable");
        BotJobKey owner = new BotJobKey(
                durable.memory().owner().homeBankingId(),
                durable.memory().owner().botJobId());
        JobMemory memory = jobs.computeIfAbsent(owner, ignored -> new JobMemory());
        Snapshot hydrated;
        boolean changed;
        synchronized (memory) {
            Snapshot before = memory.snapshot(owner);
            memory.definitions.clear();
            memory.values.clear();
            memory.entryRevisions.clear();
            memory.sources.clear();
            for (com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.Definition
                    definition : durable.definitions()) {
                int variableId = Math.toIntExact(definition.id());
                memory.definitions.put(
                        variableId,
                        new Definition(variableId, definition.name(), definition.type()));
            }
            for (com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.RuntimeValue
                    value : durable.values()) {
                int variableId = Math.toIntExact(value.variableId());
                if (!memory.definitions.containsKey(variableId)) {
                    continue;
                }
                memory.values.put(variableId, runtimeValue(value));
                memory.entryRevisions.put(variableId, value.entryRevision());
                memory.sources.put(variableId, source(value.source()));
            }
            for (Integer variableId : memory.definitions.keySet()) {
                memory.values.putIfAbsent(
                        variableId,
                        RuntimeVariableValue.voidValue(VoidReason.NO_PRODUCER_YET));
                memory.entryRevisions.putIfAbsent(variableId, 0L);
                memory.sources.putIfAbsent(variableId, ValueSource.SYSTEM);
            }
            memory.metadataAvailable = true;
            memory.revision = durable.memory().runtimeRevision();
            hydrated = memory.snapshot(owner);
            changed = !hydrated.equals(before);
        }
        if (changed) {
            notifyChanged(owner, hydrated.revision());
        }
        return hydrated;
    }

    public boolean containsDefinition(BotJobKey owner, Integer variableId) {
        if (!validId(variableId)) return false;
        JobMemory memory = jobs.get(owner);
        if (memory == null) return false;
        synchronized (memory) {
            return memory.definitions.containsKey(variableId);
        }
    }

    public RuntimeVariableValue read(BotJobKey owner, Integer variableId) {
        Objects.requireNonNull(owner, "owner");
        if (!validId(variableId)) {
            return RuntimeVariableValue.voidValue(VoidReason.MISSING_BINDING);
        }
        JobMemory memory = jobs.get(owner);
        if (memory == null) {
            return RuntimeVariableValue.voidValue(VoidReason.METADATA_UNAVAILABLE);
        }
        synchronized (memory) {
            RuntimeVariableValue value = memory.values.get(variableId);
            if (value != null) return value;
            if (!memory.metadataAvailable) {
                return RuntimeVariableValue.voidValue(VoidReason.METADATA_UNAVAILABLE);
            }
            return RuntimeVariableValue.voidValue(VoidReason.MISSING_BINDING);
        }
    }

    public boolean write(
            BotJobKey owner,
            Integer variableId,
            String value,
            ValueSource source) {
        if (!validId(variableId) || value == null) return false;
        return replace(
                owner,
                variableId,
                RuntimeVariableValue.value(value),
                source == null ? ValueSource.EXECUTION : source);
    }

    public boolean markVoid(
            BotJobKey owner,
            Integer variableId,
            VoidReason reason,
            ValueSource source) {
        if (!validId(variableId) || reason == null) return false;
        return replace(
                owner,
                variableId,
                RuntimeVariableValue.voidValue(reason),
                source == null ? ValueSource.EXECUTION : source);
    }

    public void remove(BotJobKey owner) {
        if (owner != null) jobs.remove(owner);
    }

    /** Removes every retained owner for one deleted or otherwise retired Bot Job. */
    public void removeBotJob(int botJobId) {
        if (botJobId <= 0) return;
        jobs.keySet().removeIf(owner -> owner.botJobId() == botJobId);
    }

    /** Clears process-local values after an authoritative database reload or restore. */
    public void clearAll() {
        jobs.clear();
    }

    private boolean replace(
            BotJobKey owner,
            int variableId,
            RuntimeVariableValue replacement,
            ValueSource source) {
        Objects.requireNonNull(owner, "owner");
        JobMemory memory = jobs.computeIfAbsent(owner, ignored -> new JobMemory());
        long revision;
        synchronized (memory) {
            // When the definition catalog is authoritative, a dangling instruction variable_id
            // must remain VOID/MISSING_BINDING. Never retain a hidden value that could later be
            // exposed if a different definition reuses the same numeric ID.
            if (memory.metadataAvailable
                    && !memory.definitions.containsKey(variableId)) {
                return false;
            }
            RuntimeVariableValue current = memory.values.get(variableId);
            ValueSource currentSource = memory.sources.get(variableId);
            if (replacement.equals(current) && source == currentSource) return true;
            memory.values.put(variableId, replacement);
            memory.sources.put(variableId, source);
            memory.entryRevisions.put(
                    variableId,
                    memory.entryRevisions.getOrDefault(variableId, 0L) + 1L);
            revision = ++memory.revision;
        }
        notifyChanged(owner, revision);
        return true;
    }

    private void notifyChanged(BotJobKey owner, long revision) {
        for (ChangeListener listener : listeners) {
            try {
                listener.changed(owner, revision);
            } catch (RuntimeException ignored) {
                // Runtime memory writes must not fail an executing command because one observer
                // could not publish its optional presentation update.
            }
        }
    }

    private static boolean validId(Integer variableId) {
        return variableId != null && variableId > 0;
    }

    private static RuntimeVariableValue runtimeValue(
            com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.RuntimeValue value) {
        if (value.state()
                == com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.ValueState.VALUE) {
            return RuntimeVariableValue.value(value.rawValue());
        }
        return RuntimeVariableValue.voidValue(voidReason(value.voidReason()));
    }

    private static VoidReason voidReason(
            com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.VoidReason reason) {
        if (reason == null) return VoidReason.NO_PRODUCER_YET;
        return switch (reason) {
            case NO_PRODUCER_YET, CLIENT_RESET -> VoidReason.NO_PRODUCER_YET;
            case MISSING_BINDING, DEFINITION_DELETED -> VoidReason.MISSING_BINDING;
            case MISSING_PARENT -> VoidReason.MISSING_PARENT;
            case PRODUCER_FAILED -> VoidReason.PRODUCER_FAILED;
            case EVALUATION_FAILED -> VoidReason.EVALUATION_FAILED;
            case METADATA_UNAVAILABLE -> VoidReason.METADATA_UNAVAILABLE;
        };
    }

    private static ValueSource source(
            com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.ValueSource source) {
        if (source == null) return ValueSource.SYSTEM;
        return switch (source) {
            case EXECUTION -> ValueSource.EXECUTION;
            case MANUAL -> ValueSource.MANUAL;
            case RESET, SYSTEM, MIGRATION -> ValueSource.SYSTEM;
        };
    }

    @FunctionalInterface
    public interface ChangeListener {
        void changed(BotJobKey owner, long revision);
    }

    public enum ValueSource {
        SYSTEM,
        EXECUTION,
        MANUAL
    }

    public record BotJobKey(int homeBankingId, int botJobId) {
        public BotJobKey {
            if (homeBankingId <= 0 || botJobId <= 0) {
                throw new IllegalArgumentException(
                        "Runtime variable memory requires positive owner IDs.");
            }
        }
    }

    public record Definition(int variableId, String name, String type) {
        public Definition {
            if (variableId <= 0) {
                throw new IllegalArgumentException(
                        "A runtime variable definition requires a positive ID.");
            }
            name = name == null ? "" : name;
            type = type == null ? "" : type;
        }
    }

    public record VariableSnapshot(
            int variableId,
            String name,
            String type,
            State state,
            String value,
            VoidReason voidReason,
            long entryRevision,
            ValueSource source) {}

    public record Snapshot(
            int homeBankingId,
            int botJobId,
            long revision,
            boolean metadataAvailable,
            List<VariableSnapshot> variables) {
        public Snapshot {
            variables = variables == null ? List.of() : List.copyOf(variables);
        }
    }

    private static final class JobMemory {
        private final Map<Integer, Definition> definitions = new LinkedHashMap<>();
        private final Map<Integer, RuntimeVariableValue> values = new HashMap<>();
        private final Map<Integer, Long> entryRevisions = new HashMap<>();
        private final Map<Integer, ValueSource> sources = new HashMap<>();
        private long revision;
        private boolean metadataAvailable;

        private void reconcile(
                Collection<Definition> incoming,
                boolean available) {
            if (!available) {
                if (metadataAvailable) {
                    metadataAvailable = false;
                    revision++;
                }
                return;
            }

            Map<Integer, Definition> next = new LinkedHashMap<>();
            if (incoming != null) {
                incoming.stream()
                        .filter(Objects::nonNull)
                        .sorted(Comparator.comparingInt(Definition::variableId))
                        .forEach(definition -> next.put(definition.variableId(), definition));
            }

            boolean changed = !metadataAvailable || !definitions.equals(next);
            metadataAvailable = true;
            definitions.clear();
            definitions.putAll(next);

            if (values.keySet().removeIf(id -> !next.containsKey(id))) changed = true;
            entryRevisions.keySet().removeIf(id -> !next.containsKey(id));
            sources.keySet().removeIf(id -> !next.containsKey(id));
            for (Integer id : next.keySet()) {
                if (!values.containsKey(id)) {
                    values.put(
                            id,
                            RuntimeVariableValue.voidValue(
                                    VoidReason.NO_PRODUCER_YET));
                    entryRevisions.put(id, 0L);
                    sources.put(id, ValueSource.SYSTEM);
                    changed = true;
                }
            }
            if (changed) revision++;
        }

        private Snapshot snapshot(BotJobKey owner) {
            List<VariableSnapshot> rows = new ArrayList<>(definitions.size());
            for (Definition definition : definitions.values()) {
                RuntimeVariableValue current = values.getOrDefault(
                        definition.variableId(),
                        RuntimeVariableValue.voidValue(
                                metadataAvailable
                                        ? VoidReason.NO_PRODUCER_YET
                                        : VoidReason.METADATA_UNAVAILABLE));
                rows.add(new VariableSnapshot(
                        definition.variableId(),
                        definition.name(),
                        definition.type(),
                        current.state(),
                        current.value(),
                        current.voidReason(),
                        entryRevisions.getOrDefault(definition.variableId(), 0L),
                        sources.getOrDefault(
                                definition.variableId(),
                                ValueSource.SYSTEM)));
            }
            return new Snapshot(
                    owner.homeBankingId(),
                    owner.botJobId(),
                    revision,
                    metadataAvailable,
                    rows);
        }
    }
}

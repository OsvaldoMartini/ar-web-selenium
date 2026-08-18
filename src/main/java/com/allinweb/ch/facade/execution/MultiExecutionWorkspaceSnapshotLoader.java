package com.allinweb.ch.facade.execution;

import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.VariableRelationshipService;
import com.allinweb.ch.facade.actions.RuntimeVariableMemoryRegistry;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.Plan;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.Definition;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.OwnerKey;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.RuntimeValue;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.Snapshot;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.ValueState;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Builds the React-owned graph/runtime snapshot for one prepared multi-run row. */
public final class MultiExecutionWorkspaceSnapshotLoader {
    private static final AtomicLong EPOCHS = new AtomicLong();
    private final VariableRelationshipService relationships = VariableRelationshipService.getInstance();
    private final BotJobRuntimeVariableService runtimeVariables = new BotJobRuntimeVariableService();

    public JsonObject load(Plan plan, String requestId) throws Exception {
        JsonObject graph = relationships.load(plan.owner().botJobId());
        if (!graph.has("ok") || !graph.get("ok").getAsBoolean()) {
            throw new IllegalStateException("Variable relationships could not be loaded.");
        }
        Snapshot runtime;
        try (Connection connection = PerformDataBase.getInstance().getConnection()) {
            runtime = runtimeVariables.hydrate(
                    connection,
                    new OwnerKey(plan.owner().homeBankingId(), plan.owner().botJobId()));
        }
        RuntimeVariableMemoryRegistry.getInstance().hydrateDurableSnapshot(runtime);
        JsonObject result = graph.deepCopy();
        result.addProperty("message", "Multi-run program and Runtime Variables frozen.");
        result.addProperty("requestId", requestId);
        result.addProperty("bindingEpoch", UUID.randomUUID().toString());
        result.addProperty("workspaceEpoch", EPOCHS.incrementAndGet());
        JsonObject botJob = new JsonObject();
        botJob.addProperty("id", plan.owner().botJobId());
        botJob.addProperty("name", plan.environment().botJobName());
        botJob.addProperty("homeBankingId", plan.owner().homeBankingId());
        botJob.addProperty("organizationName", plan.environment().organizationName());
        result.add("botJob", botJob);
        result.add("runtimeMemory", runtimeJson(runtime));
        return result;
    }

    private static JsonObject runtimeJson(Snapshot snapshot) {
        JsonObject result = new JsonObject();
        result.addProperty("revision", snapshot.memory().runtimeRevision());
        Map<Long, RuntimeValue> values = new HashMap<>();
        snapshot.values().forEach(value -> values.put(value.variableId(), value));
        JsonArray rows = new JsonArray();
        for (Definition definition : snapshot.definitions()) {
            RuntimeValue value = values.get(definition.id());
            JsonObject row = new JsonObject();
            row.addProperty("variableId", definition.id());
            row.addProperty("name", definition.name());
            row.addProperty("type", definition.type());
            boolean hasValue = value != null && value.state() == ValueState.VALUE;
            row.addProperty("state", hasValue ? "VALUE" : "VOID");
            row.addProperty("value", hasValue ? value.rawValue() : "");
            if (hasValue) row.add("voidReason", null);
            else row.addProperty("voidReason", value == null ? "NO_PRODUCER_YET" : value.voidReason().name());
            row.addProperty("entryRevision", value == null ? 0L : value.entryRevision());
            row.addProperty("source", value == null ? "SYSTEM" : value.source().name());
            rows.add(row);
        }
        result.add("variables", rows);
        return result;
    }
}

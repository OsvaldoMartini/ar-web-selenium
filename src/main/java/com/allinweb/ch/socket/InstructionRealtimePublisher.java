package com.allinweb.ch.socket;

import com.allinweb.ch.model.RowStatus;
import com.allinweb.ch.model.ScannerWorkspaceOperations;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;

/**
 * Single publication boundary for live instruction-grid state.
 *
 * <p>Mutation acknowledgements must be emitted before snapshots because the React grid may batch
 * adjacent WebSocket messages and consume the newest one. The canonical instruction snapshot is
 * therefore always the final event in a successful mutation sequence.
 */
@Slf4j
public final class InstructionRealtimePublisher {
    private static final InstructionRealtimePublisher INSTANCE = new InstructionRealtimePublisher();

    private final WebSocketSessionManager sessions = WebSocketSessionManager.getInstance();
    private final Gson gson = new Gson();

    private InstructionRealtimePublisher() {}

    public static InstructionRealtimePublisher getInstance() {
        return INSTANCE;
    }

    public String snapshotOperation(String sessionId) {
        return ScannerWorkspaceSessions.COMPONENT_TASKS.equals(sessionId)
                ? ScannerWorkspaceOperations.COMPONENTS_UPDATE
                : ScannerWorkspaceOperations.UPDATE_INSTRUCTIONS;
    }

    public void publishResponse(
            int homeBankingId, String sessionId, String operationId, Object response) {
        sessions.sendMessageJson(homeBankingId, sessionId, gson.toJson(response), operationId);
    }

    public void publishSnapshot(int homeBankingId, String sessionId, Object snapshot) {
        publishSerializedSnapshot(homeBankingId, sessionId, gson.toJson(snapshot));
    }

    public void publishSerializedSnapshot(int homeBankingId, String sessionId, String snapshotJson) {
        String operationId = snapshotOperation(sessionId);
        sessions.sendMessageJson(homeBankingId, sessionId, snapshotJson, operationId);
        if (ScannerWorkspaceSessions.BOT_JOB_TASKS.equals(sessionId)) {
            VariablesWorkspaceService.getInstance()
                    .notifyMutation(snapshotBotJobId(snapshotJson));
        }
        log.debug("Instruction snapshot published: session={} operation={}", sessionId, operationId);
    }

    public void publishMutationThenSnapshot(
            int homeBankingId,
            String sessionId,
            String responseOperationId,
            Object response,
            Object snapshot) {
        publishResponse(homeBankingId, sessionId, responseOperationId, response);
        publishSnapshot(homeBankingId, sessionId, snapshot);
    }

    public void publishExecutionStatus(
            int homeBankingId, String sessionId, Integer instructionId, String color) {
        RowStatus status = new RowStatus();
        status.setInstructionId(instructionId);
        status.setColor(color);
        sessions.sendMessageJson(homeBankingId, sessionId, gson.toJson(status), "rowStatus");
    }

    private static int snapshotBotJobId(String snapshotJson) {
        try {
            return snapshotBotJobId(JsonParser.parseString(snapshotJson));
        } catch (RuntimeException malformedSnapshot) {
            return -1;
        }
    }

    private static int snapshotBotJobId(JsonElement root) {
        if (root == null || root.isJsonNull()) return -1;
        if (root.isJsonObject()) {
            if (root.getAsJsonObject().has("botJobId")
                    && !root.getAsJsonObject().get("botJobId").isJsonNull()) {
                int direct = root.getAsJsonObject().get("botJobId").getAsInt();
                if (direct > 0) return direct;
            }
            for (String collection : new String[] {"instructions", "rows", "data"}) {
                if (root.getAsJsonObject().has(collection)) {
                    int nested =
                            snapshotBotJobId(root.getAsJsonObject().get(collection));
                    if (nested > 0) return nested;
                }
            }
            return -1;
        }
        if (!root.isJsonArray()) return -1;
        int botJobId = -1;
        for (JsonElement item : root.getAsJsonArray()) {
            int candidate = snapshotBotJobId(item);
            if (candidate <= 0) continue;
            if (botJobId > 0 && botJobId != candidate) return -1;
            botJobId = candidate;
        }
        return botJobId;
    }
}

package com.allinweb.ch.facade;

import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.ScannerWorkspaceOperations;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.allinweb.ch.socket.WebSocketSessionManager;
import com.allinweb.ch.socket.VariablesWorkspaceService;
import com.allinweb.ch.util.ErrorMessage;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;

/** Reloads and publishes the bot-job task grid after scanner mutations. */
public final class ScannerBotJobTasksPublisher {
    private static final ScannerBotJobTasksPublisher INSTANCE = new ScannerBotJobTasksPublisher(
            new DefaultDataPort(), new DefaultSenderPort(), new Gson());

    private final DataPort data;
    private final SenderPort sender;
    private final Gson gson;

    ScannerBotJobTasksPublisher(DataPort data, SenderPort sender, Gson gson) {
        this.data = data;
        this.sender = sender;
        this.gson = gson;
    }

    public static ScannerBotJobTasksPublisher getInstance() {
        return INSTANCE;
    }

    public ErrorMessage publish(int homeBankingId, int botJobId) {
        VariablesWorkspaceService.getInstance().notifyMutation(botJobId);
        return publishGridOnly(homeBankingId, botJobId);
    }

    /**
     * Reloads and publishes only the Bot Job Details grid.
     *
     * <p>Callers that own a stricter acknowledgement/publication sequence can publish this view
     * first and notify other detached workspaces afterward.
     */
    public ErrorMessage publishGridOnly(int homeBankingId, int botJobId) {
        ErrorMessage error = data.loadCompleteJobs(botJobId);
        if (error != null) {
            return error;
        }

        String json = "[]";
        List<BotJobLoadDTO> jobs = data.botJobs();
        if (jobs != null && !jobs.isEmpty()) {
            json = gson.toJson(data.buildJsonViewData(jobs));
        }
        boolean delivered = sender.sendMessageJson(
                homeBankingId,
                ScannerWorkspaceSessions.BOT_JOB_TASKS,
                json,
                ScannerWorkspaceOperations.UPDATE_INSTRUCTIONS);
        if (!delivered) {
            return refreshUnavailable();
        }
        return null;
    }

    /**
     * Reloads and publishes a complete Bot Job grid envelope.
     *
     * <p>Memory List mutations use this shape because a bare instruction array cannot describe an
     * empty block or authoritatively replace stale block order in Bot Job Details.
     */
    public ErrorMessage publishStructured(
            int homeBankingId,
            int botJobId,
            JsonArray blocks,
            JsonObject correlation) {
        VariablesWorkspaceService.getInstance().notifyMutation(botJobId);
        ErrorMessage error = data.loadCompleteJobs(botJobId);
        if (error != null) {
            return error;
        }

        List<InstructionLoad> instructions = List.of();
        List<BotJobLoadDTO> jobs = data.botJobs();
        if (jobs != null && !jobs.isEmpty()) {
            List<InstructionLoad> loaded = data.buildJsonViewData(jobs);
            if (loaded != null) instructions = loaded;
        }

        JsonObject payload = new JsonObject();
        payload.add("instructions", gson.toJsonTree(instructions));
        payload.add("blocks", blocks == null ? new JsonArray() : blocks.deepCopy());
        payload.addProperty("homeBankingId", homeBankingId);
        payload.addProperty("botJobId", botJobId);
        if (correlation != null) {
            correlation.entrySet().forEach(entry -> {
                if (!payload.has(entry.getKey()) && entry.getValue() != null) {
                    payload.add(entry.getKey(), entry.getValue().deepCopy());
                }
            });
        }
        boolean delivered = sender.sendMessageJson(
                homeBankingId,
                ScannerWorkspaceSessions.BOT_JOB_TASKS,
                gson.toJson(payload),
                ScannerWorkspaceOperations.UPDATE_INSTRUCTIONS);
        if (!delivered) {
            return refreshUnavailable();
        }
        return null;
    }

    private ErrorMessage refreshUnavailable() {
        return new ErrorMessage(
                "Bot Job Details Refresh",
                "The database was saved, but Bot Job Details could not be refreshed.",
                "The Bot Job Details session is unavailable. Reopen or refresh that workspace "
                        + "to load the committed changes.");
    }

    interface DataPort {
        ErrorMessage loadCompleteJobs(int botJobId);

        List<BotJobLoadDTO> botJobs();

        List<InstructionLoad> buildJsonViewData(List<BotJobLoadDTO> jobs);
    }

    interface SenderPort {
        boolean sendMessageJson(
                int homeBankingId, String sessionId, String json, String operationId);
    }

    private static final class DefaultDataPort implements DataPort {
        private final PerformDBEngine engine = PerformDBEngine.getInstance();
        private final PerformLists lists = PerformLists.getInstance();

        @Override
        public ErrorMessage loadCompleteJobs(int botJobId) {
            return engine.loadCompleteJobs(botJobId);
        }

        @Override
        public List<BotJobLoadDTO> botJobs() {
            return lists.getListBotJob();
        }

        @Override
        public List<InstructionLoad> buildJsonViewData(List<BotJobLoadDTO> jobs) {
            return lists.buildJsonViewData(jobs);
        }
    }

    private static final class DefaultSenderPort implements SenderPort {
        private final WebSocketSessionManager sessions = WebSocketSessionManager.getInstance();

        @Override
        public boolean sendMessageJson(
                int homeBankingId, String sessionId, String json, String operationId) {
            return sessions.sendMessageJson(homeBankingId, sessionId, json, operationId) != null;
        }
    }
}

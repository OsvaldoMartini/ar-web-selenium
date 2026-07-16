package com.allinweb.ch.facade;

import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.ScannerWorkspaceOperations;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.allinweb.ch.socket.WebSocketSessionManager;
import com.allinweb.ch.util.ErrorMessage;
import com.google.gson.Gson;
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
        ErrorMessage error = data.loadCompleteJobs(botJobId);
        if (error != null) {
            return error;
        }

        String json = "[]";
        List<BotJobLoadDTO> jobs = data.botJobs();
        if (jobs != null && !jobs.isEmpty()) {
            json = gson.toJson(data.buildJsonViewData(jobs));
        }
        sender.sendMessageJson(
                homeBankingId,
                ScannerWorkspaceSessions.BOT_JOB_TASKS,
                json,
                ScannerWorkspaceOperations.UPDATE_INSTRUCTIONS);
        return null;
    }

    interface DataPort {
        ErrorMessage loadCompleteJobs(int botJobId);

        List<BotJobLoadDTO> botJobs();

        List<InstructionLoad> buildJsonViewData(List<BotJobLoadDTO> jobs);
    }

    interface SenderPort {
        void sendMessageJson(int homeBankingId, String sessionId, String json, String operationId);
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
        public void sendMessageJson(int homeBankingId, String sessionId, String json, String operationId) {
            sessions.sendMessageJson(homeBankingId, sessionId, json, operationId);
        }
    }
}

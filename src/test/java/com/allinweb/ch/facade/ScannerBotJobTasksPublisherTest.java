package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.ScannerWorkspaceOperations;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.allinweb.ch.util.ErrorMessage;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerBotJobTasksPublisherTest {

    @Test
    void reloadsAndPublishesTaskGridSnapshot() {
        RecordingData data = new RecordingData();
        data.jobs.add(new BotJobLoadDTO());
        data.viewRows = List.of(InstructionLoad.builder().id(12).name("Login").build());
        RecordingSender sender = new RecordingSender();
        ScannerBotJobTasksPublisher publisher = new ScannerBotJobTasksPublisher(data, sender, new Gson());

        ErrorMessage result = publisher.publish(7, 42);

        assertNull(result);
        assertEquals(List.of("load:42", "jobs", "view:1"), data.calls);
        assertEquals("send:7:" + ScannerWorkspaceSessions.BOT_JOB_TASKS + ":"
                + ScannerWorkspaceOperations.UPDATE_INSTRUCTIONS, sender.sendCall);
        assertEquals("[{\"id\":12,\"name\":\"Login\"}]", sender.json);
    }

    @Test
    void publishesEmptyArrayWhenThereAreNoJobs() {
        RecordingData data = new RecordingData();
        RecordingSender sender = new RecordingSender();
        ScannerBotJobTasksPublisher publisher = new ScannerBotJobTasksPublisher(data, sender, new Gson());

        ErrorMessage result = publisher.publish(7, 42);

        assertNull(result);
        assertEquals("[]", sender.json);
    }

    @Test
    void returnsLoadErrorAndDoesNotPublish() {
        ErrorMessage error = new ErrorMessage("title", "header", "detail");
        RecordingData data = new RecordingData();
        data.loadError = error;
        RecordingSender sender = new RecordingSender();
        ScannerBotJobTasksPublisher publisher = new ScannerBotJobTasksPublisher(data, sender, new Gson());

        ErrorMessage result = publisher.publish(7, 42);

        assertSame(error, result);
        assertNull(sender.sendCall);
    }

    private static final class RecordingData implements ScannerBotJobTasksPublisher.DataPort {
        private final List<String> calls = new ArrayList<>();
        private final List<BotJobLoadDTO> jobs = new ArrayList<>();
        private List<InstructionLoad> viewRows = List.of();
        private ErrorMessage loadError;

        @Override
        public ErrorMessage loadCompleteJobs(int botJobId) {
            calls.add("load:" + botJobId);
            return loadError;
        }

        @Override
        public List<BotJobLoadDTO> botJobs() {
            calls.add("jobs");
            return jobs;
        }

        @Override
        public List<InstructionLoad> buildJsonViewData(List<BotJobLoadDTO> jobs) {
            calls.add("view:" + jobs.size());
            return viewRows;
        }
    }

    private static final class RecordingSender implements ScannerBotJobTasksPublisher.SenderPort {
        private String sendCall;
        private String json;

        @Override
        public void sendMessageJson(int homeBankingId, String sessionId, String json, String operationId) {
            sendCall = "send:" + homeBankingId + ":" + sessionId + ":" + operationId;
            this.json = json;
        }
    }
}

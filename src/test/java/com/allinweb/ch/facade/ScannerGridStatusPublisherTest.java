package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.allinweb.ch.model.ScannerWorkspaceOperations;
import com.allinweb.ch.model.SplitDTO;
import com.allinweb.ch.model.WebSocketSignal;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerGridStatusPublisherTest {

    private final Gson gson = new Gson();

    @Test
    void publishesScannerGridStatusSignal() {
        RecordingSender sender = new RecordingSender();
        ScannerGridStatusPublisher publisher = new ScannerGridStatusPublisher(sender);

        publisher.publishScannerGridStatus(7, ScannerWorkspaceOperations.ACTIVATE_INSERT_ALL, "ready");

        assertEquals(1, sender.messages.size());
        Message message = sender.messages.get(0);
        assertEquals(7, message.homeBankingId);
        assertEquals(publisher.destinationSessionId(), message.sessionId);
        assertEquals(ScannerWorkspaceOperations.ACTIVATE_INSERT_ALL, message.operationId);

        WebSocketSignal signal = gson.fromJson(message.json, WebSocketSignal.class);
        assertEquals(publisher.destinationSessionId(), signal.getSessionId());
        assertEquals(ScannerWorkspaceOperations.ACTIVATE_INSERT_ALL, signal.getOperationId());
        assertEquals("ready", signal.getMessage());
        assertNull(signal.getSplitDTO());
    }

    @Test
    void publishesOptionalSplitDto() {
        RecordingSender sender = new RecordingSender();
        ScannerGridStatusPublisher publisher = new ScannerGridStatusPublisher(sender);
        SplitDTO splitDTO = new SplitDTO();
        splitDTO.setBotJobId(42);

        publisher.publishScannerGridStatus(7, ScannerWorkspaceOperations.ACTIVATE_UPDATE_ALL, "updated", splitDTO);

        WebSocketSignal signal = gson.fromJson(sender.messages.get(0).json, WebSocketSignal.class);
        assertEquals(42, signal.getSplitDTO().getBotJobId());
    }

    @Test
    void exposesDestinationSessionId() {
        ScannerGridStatusPublisher publisher = new ScannerGridStatusPublisher(new RecordingSender());

        assertEquals(ScannerSearchRoute.standardPageScanner().destinationSessionId(), publisher.destinationSessionId());
    }

    private static final class RecordingSender implements ScannerGridStatusPublisher.Sender {
        private final List<Message> messages = new ArrayList<>();

        @Override
        public void sendMessageJson(int homeBankingId, String sessionId, String json, String operationId) {
            messages.add(new Message(homeBankingId, sessionId, json, operationId));
        }
    }

    private record Message(int homeBankingId, String sessionId, String json, String operationId) {}
}

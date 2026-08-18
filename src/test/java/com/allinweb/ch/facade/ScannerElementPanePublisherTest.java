package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.allinweb.ch.model.ScannerWorkspaceOperations;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScannerElementPanePublisherTest {

    @Test
    void publishesOpenOcrConfigToScannerElementPane() {
        RecordingSender sender = new RecordingSender();
        ScannerElementPanePublisher publisher = new ScannerElementPanePublisher(sender);

        publisher.publishOpenOcrConfig(7, Map.of("homeUrlId", 9));

        assertEquals(1, sender.messages.size());
        assertEquals(7, sender.messages.get(0).homeBankingId);
        assertEquals(publisher.destinationSessionId(), sender.messages.get(0).sessionId);
        assertEquals(ScannerElementPanePublisher.OPEN_OCR_CONFIG, sender.messages.get(0).operationId);
        assertEquals("{\"homeUrlId\":9}", sender.messages.get(0).json);
    }

    @Test
    void publishesUpdateBlocksToScannerElementPane() {
        RecordingSender sender = new RecordingSender();
        ScannerElementPanePublisher publisher = new ScannerElementPanePublisher(sender);

        publisher.publishUpdateBlocks(7, Map.of("type", ScannerWorkspaceOperations.UPDATE_BLOCKS));

        assertEquals(1, sender.messages.size());
        assertEquals(publisher.destinationSessionId(), sender.messages.get(0).sessionId);
        assertEquals(ScannerWorkspaceOperations.UPDATE_BLOCKS, sender.messages.get(0).operationId);
    }

    @Test
    void publishesRawJsonToScannerElementPane() {
        RecordingSender sender = new RecordingSender();
        ScannerElementPanePublisher publisher = new ScannerElementPanePublisher(sender);

        publisher.publishRawJson("{\"ok\":true}");

        assertEquals(1, sender.rawMessages.size());
        assertEquals(publisher.destinationSessionId(), sender.rawMessages.get(0).sessionId);
        assertEquals("{\"ok\":true}", sender.rawMessages.get(0).json);
    }

    private static final class RecordingSender implements ScannerElementPanePublisher.Sender {
        private final List<Message> messages = new ArrayList<>();
        private final List<RawMessage> rawMessages = new ArrayList<>();

        @Override
        public void sendMessageJson(int homeBankingId, String sessionId, String json, String operationId) {
            messages.add(new Message(homeBankingId, sessionId, json, operationId));
        }

        @Override
        public void sendMessageJson(String sessionId, String json) {
            rawMessages.add(new RawMessage(sessionId, json));
        }
    }

    private record Message(int homeBankingId, String sessionId, String json, String operationId) {}

    private record RawMessage(String sessionId, String json) {}
}

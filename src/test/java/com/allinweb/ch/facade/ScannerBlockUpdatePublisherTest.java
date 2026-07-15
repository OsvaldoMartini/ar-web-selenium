package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.allinweb.ch.model.ScannerWorkspaceOperations;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerBlockUpdatePublisherTest {

    @Test
    void publishesBlockCreationUpdateToScannerAndPreScannerWorkspaces() {
        RecordingSender sender = new RecordingSender();
        ScannerBlockUpdatePublisher publisher = new ScannerBlockUpdatePublisher(sender);

        publisher.publishBlockCreationUpdate(7, "{\"block\":1}", ScannerWorkspaceOperations.UPDATE_BLOCKS);

        assertEquals(3, sender.messages.size());
        assertEquals(
                List.of(
                        ScannerWorkspaceSessions.PERFORM_LIST_DATA,
                        ScannerWorkspaceSessions.SCANNER_GRID,
                        ScannerWorkspaceSessions.PRE_SCANNER_GRID),
                sender.messages.stream().map(Message::sessionId).toList());
        assertEquals(
                List.of(
                        ScannerWorkspaceOperations.UPDATE_BLOCKS,
                        ScannerWorkspaceOperations.BLOCKS_UPDATE,
                        ScannerWorkspaceOperations.BLOCKS_UPDATE),
                sender.messages.stream().map(Message::operationId).toList());
        assertEquals(List.of(7, 7, 7), sender.messages.stream().map(Message::homeBankingId).toList());
        assertEquals(List.of("{\"block\":1}", "{\"block\":1}", "{\"block\":1}"),
                sender.messages.stream().map(Message::json).toList());
    }

    private static final class RecordingSender implements ScannerBlockUpdatePublisher.Sender {
        private final List<Message> messages = new ArrayList<>();

        @Override
        public void sendMessageJson(int homeBankingId, String sessionId, String json, String operationId) {
            messages.add(new Message(homeBankingId, sessionId, json, operationId));
        }
    }

    private record Message(int homeBankingId, String sessionId, String json, String operationId) {}
}

package com.allinweb.ch.facade;

import com.allinweb.ch.model.ScannerWorkspaceOperations;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.allinweb.ch.socket.WebSocketSessionManager;

public final class ScannerBlockUpdatePublisher {
    private final Sender sender;

    public ScannerBlockUpdatePublisher() {
        this(new WebSocketSessionSender());
    }

    ScannerBlockUpdatePublisher(Sender sender) {
        this.sender = sender;
    }

    public void publishBlockCreationUpdate(int homeBankingId, String jsonPayload, String performListOperationId) {
        sender.sendMessageJson(homeBankingId, performListDestinationSessionId(), jsonPayload, performListOperationId);
        sender.sendMessageJson(homeBankingId, scannerGridDestinationSessionId(), jsonPayload, blocksUpdateOperationId());
        sender.sendMessageJson(
                homeBankingId,
                preScannerGridDestinationSessionId(),
                jsonPayload,
                blocksUpdateOperationId());
    }

    public String performListDestinationSessionId() {
        return ScannerWorkspaceSessions.PERFORM_LIST_DATA;
    }

    public String scannerGridDestinationSessionId() {
        return ScannerWorkspaceSessions.SCANNER_GRID;
    }

    public String preScannerGridDestinationSessionId() {
        return ScannerWorkspaceSessions.PRE_SCANNER_GRID;
    }

    public String blocksUpdateOperationId() {
        return ScannerWorkspaceOperations.BLOCKS_UPDATE;
    }

    interface Sender {
        void sendMessageJson(int homeBankingId, String sessionId, String json, String operationId);
    }

    private static final class WebSocketSessionSender implements Sender {
        private final WebSocketSessionManager sessions = WebSocketSessionManager.getInstance();

        @Override
        public void sendMessageJson(int homeBankingId, String sessionId, String json, String operationId) {
            sessions.sendMessageJson(homeBankingId, sessionId, json, operationId);
        }
    }
}

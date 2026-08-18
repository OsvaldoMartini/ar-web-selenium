package com.allinweb.ch.facade;

import com.allinweb.ch.model.ScannerWorkspaceOperations;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.allinweb.ch.socket.WebSocketSessionManager;
import com.google.gson.Gson;

public final class ScannerElementPanePublisher {
    public static final String OPEN_OCR_CONFIG = "openOcrConfig";

    private final Sender sender;
    private final Gson gson = new Gson();

    public ScannerElementPanePublisher() {
        this(new WebSocketSessionSender());
    }

    ScannerElementPanePublisher(Sender sender) {
        this.sender = sender;
    }

    public void publish(int homeBankingId, Object payload, String operationId) {
        sender.sendMessageJson(
                homeBankingId,
                destinationSessionId(),
                gson.toJson(payload),
                operationId);
    }

    public void publishRawJson(String jsonPayload) {
        sender.sendMessageJson(destinationSessionId(), jsonPayload);
    }

    public String destinationSessionId() {
        return ScannerWorkspaceSessions.SCANNER_ELEMENT_PANE;
    }

    public void publishOpenOcrConfig(int homeBankingId, Object payload) {
        publish(homeBankingId, payload, OPEN_OCR_CONFIG);
    }

    public void publishUpdateBlocks(int homeBankingId, Object payload) {
        publish(homeBankingId, payload, ScannerWorkspaceOperations.UPDATE_BLOCKS);
    }

    interface Sender {
        void sendMessageJson(int homeBankingId, String sessionId, String json, String operationId);

        void sendMessageJson(String sessionId, String json);
    }

    private static final class WebSocketSessionSender implements Sender {
        private final WebSocketSessionManager sessions = WebSocketSessionManager.getInstance();

        @Override
        public void sendMessageJson(int homeBankingId, String sessionId, String json, String operationId) {
            sessions.sendMessageJson(homeBankingId, sessionId, json, operationId);
        }

        @Override
        public void sendMessageJson(String sessionId, String json) {
            sessions.sendMessageJson(sessionId, json);
        }
    }
}

package com.allinweb.ch.facade;

import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.allinweb.ch.model.SplitDTO;
import com.allinweb.ch.model.WebSocketSignal;
import com.allinweb.ch.socket.WebSocketSessionManager;
import com.google.gson.Gson;

public final class ScannerGridStatusPublisher {
    private final Sender sender;
    private final Gson gson = new Gson();

    public ScannerGridStatusPublisher() {
        this(new WebSocketSessionSender());
    }

    ScannerGridStatusPublisher(Sender sender) {
        this.sender = sender;
    }

    public void publishScannerGridStatus(int homeBankingId, String operationId, String message) {
        publishScannerGridStatus(homeBankingId, operationId, message, null);
    }

    public void publishScannerGridStatus(int homeBankingId, String operationId, String message, SplitDTO splitDTO) {
        publishStatus(homeBankingId, destinationSessionId(), operationId, message, splitDTO);
    }

    public String destinationSessionId() {
        return ScannerWorkspaceSessions.SCANNER_GRID;
    }

    private void publishStatus(int homeBankingId, String sessionId, String operationId, String message, SplitDTO splitDTO) {
        WebSocketSignal signal = WebSocketSignal.builder()
                .sessionId(sessionId)
                .operationId(operationId)
                .message(message)
                .splitDTO(splitDTO)
                .build();

        sender.sendMessageJson(homeBankingId, sessionId, gson.toJson(signal), operationId);
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

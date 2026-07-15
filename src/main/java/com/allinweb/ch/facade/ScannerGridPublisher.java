package com.allinweb.ch.facade;

import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.ScannerWorkspaceOperations;
import com.allinweb.ch.model.SplitDTO;
import com.allinweb.ch.socket.WebSocketSessionManager;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.List;

final class ScannerGridPublisher implements ScannerWorkspaceService.GridPublisher {
    private final Sender sender;
    private final Gson gson = new Gson();

    ScannerGridPublisher() {
        this(new WebSocketSessionSender());
    }

    ScannerGridPublisher(Sender sender) {
        this.sender = sender;
    }

    @Override
    public void publishSearchTerms(String sessionId, int homeBankingId, SplitDTO payload) {
        sender.sendMessageJson(homeBankingId, sessionId, gson.toJson(payload), ScannerWorkspaceOperations.SEARCH_TERMS);
    }

    @Override
    public void publishSearchTermsChunks(String sessionId, int homeBankingId, SplitDTO payload, int chunkSize) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("Scanner chunk size must be positive");
        }
        ElementDTO[] elementDetails = payload.getElementDetails();
        List<ElementDTO> elements = elementDetails == null ? List.of() : Arrays.asList(elementDetails);
        for (int i = 0; i < elements.size(); i += chunkSize) {
            int end = Math.min(i + chunkSize, elements.size());
            SplitDTO chunkPayload = chunkPayload(payload, elements.subList(i, end));
            sender.sendMessageJson(
                    homeBankingId, sessionId, gson.toJson(chunkPayload), ScannerWorkspaceOperations.SEARCH_TERMS);
        }
    }

    private SplitDTO chunkPayload(SplitDTO payload, List<ElementDTO> elements) {
        SplitDTO copy = gson.fromJson(gson.toJson(payload), SplitDTO.class);
        copy.setElementDetails(elements.toArray(new ElementDTO[0]));
        return copy;
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

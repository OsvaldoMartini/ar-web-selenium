package com.allinweb.ch.facade;

import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.SplitDTO;
import com.allinweb.ch.socket.WebSocketSessionManager;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.List;

final class ScannerGridPublisher implements ScannerWorkspaceService.GridPublisher {
    private final WebSocketSessionManager sessions = WebSocketSessionManager.getInstance();
    private final Gson gson = new Gson();

    @Override
    public void publishSearchTerms(String sessionId, int homeBankingId, SplitDTO payload) {
        sessions.sendMessageJson(homeBankingId, sessionId, gson.toJson(payload), "searchTerms");
    }

    @Override
    public void publishSearchTermsChunks(String sessionId, int homeBankingId, SplitDTO payload, int chunkSize) {
        ElementDTO[] elementDetails = payload.getElementDetails();
        List<ElementDTO> elements = elementDetails == null ? List.of() : Arrays.asList(elementDetails);
        for (int i = 0; i < elements.size(); i += chunkSize) {
            int end = Math.min(i + chunkSize, elements.size());
            payload.setElementDetails(elements.subList(i, end).toArray(new ElementDTO[0]));
            sessions.sendMessageJson(homeBankingId, sessionId, gson.toJson(payload), "searchTerms");
        }
    }
}

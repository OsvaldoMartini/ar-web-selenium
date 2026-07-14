package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.BotJobDetailsState;
import com.allinweb.ch.model.ScannerWorkspaceRequest;
import com.allinweb.ch.model.ScannerWorkspaceResponse;
import com.allinweb.ch.model.SplitDTO;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerWorkspaceServiceTest {

    @Test
    void bootstrapMapsBotJobDetailsStateToScannerState() {
        RecordingPublisher publisher = new RecordingPublisher();
        ScannerWorkspaceService service = new ScannerWorkspaceService(id -> state(), publisher);

        ScannerWorkspaceResponse response = service.bootstrap(request("bootstrap-1", null));

        assertTrue(response.ok());
        assertEquals("bootstrap-1", response.requestId());
        assertEquals(42, response.state().botJobId());
        assertEquals("https://bank.example", response.state().environmentUrl());
        assertEquals(1, response.state().blocks().size());
        assertTrue(response.state().capabilities().canRefreshState());
    }

    @Test
    void refreshStateActionReturnsCorrelatedState() {
        RecordingPublisher publisher = new RecordingPublisher();
        ScannerWorkspaceService service = new ScannerWorkspaceService(id -> state(), publisher);

        ScannerWorkspaceResponse response = service.action(request("refresh-1", "REFRESH_STATE"));

        assertTrue(response.ok());
        assertEquals("REFRESH_STATE", response.action());
        assertEquals("refresh-1", response.requestId());
        assertEquals(9L, response.state().revision());
    }

    @Test
    void clearGridPublishesEmptySearchTermsPayload() {
        RecordingPublisher publisher = new RecordingPublisher();
        ScannerWorkspaceService service = new ScannerWorkspaceService(id -> state(), publisher);

        ScannerWorkspaceResponse response = service.action(request("clear-1", "CLEAR_GRID"));

        assertTrue(response.ok());
        assertEquals("CLEAR_GRID", response.action());
        assertEquals(1, publisher.calls.size());
        RecordingPublisher.Call call = publisher.calls.get(0);
        assertEquals("scannerGrid", call.sessionId);
        assertEquals(2, call.homeBankingId);
        assertEquals(42, call.payload.getBotJobId());
        assertEquals("scannerGrid", call.payload.getSessionId());
        assertEquals("searchTerms", call.payload.getOperationId());
        assertEquals(0, call.payload.getElementDetails().length);
    }

    private ScannerWorkspaceRequest request(String requestId, String action) {
        JsonObject body = new JsonObject();
        body.addProperty("requestId", requestId);
        body.addProperty("botJobId", 42);
        if (action != null) body.addProperty("action", action);
        return new ScannerWorkspaceRequest("scannerGrid", requestId, 42, body);
    }

    private BotJobDetailsState state() {
        return new BotJobDetailsState(
                9L,
                5L,
                42,
                "Apre Acconto",
                "desc",
                "Web",
                true,
                2,
                "Banca Stato",
                11,
                "Production",
                "https://bank.example",
                3,
                true,
                List.of(),
                List.of(new BotJobDetailsState.Block(100, 1, "Login", "", 1, true, 0)),
                new BotJobDetailsState.Capabilities(true, true, true, true, true, true, true, true),
                "IDLE",
                "scanner",
                false);
    }

    private static final class RecordingPublisher implements ScannerWorkspaceService.GridPublisher {
        private final List<Call> calls = new ArrayList<>();

        @Override
        public void publishSearchTerms(String sessionId, int homeBankingId, SplitDTO payload) {
            calls.add(new Call(sessionId, homeBankingId, payload));
        }

        private record Call(String sessionId, int homeBankingId, SplitDTO payload) {}
    }
}

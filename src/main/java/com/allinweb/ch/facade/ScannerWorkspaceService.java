package com.allinweb.ch.facade;

import com.allinweb.ch.model.BotJobDetailsState;
import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.ScannerWorkspaceAction;
import com.allinweb.ch.model.ScannerWorkspaceRequest;
import com.allinweb.ch.model.ScannerWorkspaceResponse;
import com.allinweb.ch.model.ScannerWorkspaceState;
import com.allinweb.ch.model.SplitDTO;
import com.allinweb.ch.socket.WebSocketSessionManager;
import com.google.gson.Gson;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

public final class ScannerWorkspaceService {

    private static final ScannerWorkspaceService INSTANCE =
            new ScannerWorkspaceService(
                    BotJobDetailsService.getInstance()::currentState,
                    new WebSocketGridPublisher());

    private final IntFunction<BotJobDetailsState> botJobStateProvider;
    private final GridPublisher gridPublisher;

    ScannerWorkspaceService(IntFunction<BotJobDetailsState> botJobStateProvider, GridPublisher gridPublisher) {
        this.botJobStateProvider = botJobStateProvider;
        this.gridPublisher = gridPublisher;
    }

    public static ScannerWorkspaceService getInstance() {
        return INSTANCE;
    }

    public ScannerWorkspaceResponse bootstrap(ScannerWorkspaceRequest request) {
        try {
            return ScannerWorkspaceResponse.success("Scanner workspace loaded", request, state(request.botJobId()));
        } catch (RuntimeException error) {
            return ScannerWorkspaceResponse.failure(
                    safe(error.getMessage()), "SCANNER_BOOTSTRAP_FAILED", request, null);
        }
    }

    public ScannerWorkspaceResponse action(ScannerWorkspaceRequest request) {
        ScannerWorkspaceAction action;
        try {
            action = ScannerWorkspaceAction.parse(request.body().get("action").getAsString());
        } catch (RuntimeException error) {
            return ScannerWorkspaceResponse.failure(safe(error.getMessage()), "INVALID_SCANNER_ACTION", request, null);
        }
        try {
            ScannerWorkspaceState state = state(request.botJobId());
            if (action == ScannerWorkspaceAction.CLEAR_GRID) {
                gridPublisher.publishSearchTerms(request.sessionId(), state.homeBankingId(), emptyPayload(state));
            }
            return ScannerWorkspaceResponse.actionSuccess(
                    action, actionMessage(action), request, state);
        } catch (RuntimeException error) {
            return ScannerWorkspaceResponse.failure(safe(error.getMessage()), "SCANNER_ACTION_FAILED", request, action);
        }
    }

    public ScannerWorkspaceState state(int botJobId) {
        return toScannerState(botJobStateProvider.apply(botJobId));
    }

    private ScannerWorkspaceState toScannerState(BotJobDetailsState source) {
        List<ScannerWorkspaceState.Block> blocks = source.blocks().stream()
                .map(block -> new ScannerWorkspaceState.Block(block.id(), block.order(), block.name(), block.active()))
                .toList();
        BotJobDetailsState.Capabilities sourceCapabilities = source.capabilities();
        ScannerWorkspaceState.Capabilities capabilities = new ScannerWorkspaceState.Capabilities(
                true,
                sourceCapabilities.canUsePreScan(),
                sourceCapabilities.canUsePreScan(),
                sourceCapabilities.canExecute(),
                sourceCapabilities.canUseWorkspaceActions());
        return new ScannerWorkspaceState(
                source.revision(),
                source.botJobId(),
                source.name(),
                source.homeBankingId(),
                source.environmentUrl(),
                blocks,
                new ScannerWorkspaceState.Browser("UNKNOWN", "", "", 0, false),
                new ScannerWorkspaceState.Focus("default", List.of()),
                new ScannerWorkspaceState.Ocr(sourceCapabilities.canUsePreScan(), "IDLE"),
                capabilities,
                source.executionState());
    }

    private String safe(String message) {
        return message == null || message.isBlank() ? "Scanner operation failed" : message;
    }

    private String actionMessage(ScannerWorkspaceAction action) {
        return action == ScannerWorkspaceAction.CLEAR_GRID ? "Scanner grid cleared" : "Scanner state refreshed";
    }

    private SplitDTO emptyPayload(ScannerWorkspaceState state) {
        SplitDTO payload = new SplitDTO();
        payload.setHomeBankingId(state.homeBankingId());
        payload.setBotJobId(state.botJobId());
        payload.setBotJobName(state.botJobName());
        payload.setType("SEARCH_TOOL");
        payload.setSessionId("scannerGrid");
        payload.setOperationId("searchTerms");
        payload.setElementDetails(new ElementDTO[0]);
        payload.setBlocks(state.blocks().stream().map(block -> {
            Map<String, Object> option = new LinkedHashMap<>();
            option.put("blockId", block.id());
            option.put("blockOrderNumber", block.order());
            option.put("blockName", block.name());
            return option;
        }).toList());
        return payload;
    }

    interface GridPublisher {
        void publishSearchTerms(String sessionId, int homeBankingId, SplitDTO payload);
    }

    private static final class WebSocketGridPublisher implements GridPublisher {
        private final WebSocketSessionManager sessions = WebSocketSessionManager.getInstance();
        private final Gson gson = new Gson();

        @Override
        public void publishSearchTerms(String sessionId, int homeBankingId, SplitDTO payload) {
            sessions.sendMessageJson(homeBankingId, sessionId, gson.toJson(payload), "searchTerms");
        }
    }
}

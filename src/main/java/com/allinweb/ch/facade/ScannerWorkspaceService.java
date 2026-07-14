package com.allinweb.ch.facade;

import com.allinweb.ch.model.BotJobDetailsState;
import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.ScannerWorkspaceAction;
import com.allinweb.ch.model.ScannerWorkspaceRequest;
import com.allinweb.ch.model.ScannerWorkspaceResponse;
import com.allinweb.ch.model.ScannerWorkspaceState;
import com.allinweb.ch.model.SplitDTO;
import java.util.List;
import java.util.function.IntFunction;

public final class ScannerWorkspaceService {

    private static final int SCANNER_CHUNK_SIZE = 25;
    private static final ScannerWorkspaceService INSTANCE =
            new ScannerWorkspaceService(
                    BotJobDetailsService.getInstance()::currentState,
                    new ScannerGridPublisher(),
                    new ScannerBrowserOperations(),
                    new ScannerExecutionOperations());

    private final IntFunction<BotJobDetailsState> botJobStateProvider;
    private final GridPublisher gridPublisher;
    private final BrowserOperations browserOperations;
    private volatile ExecutionOperations executionOperations;

    ScannerWorkspaceService(
            IntFunction<BotJobDetailsState> botJobStateProvider,
            GridPublisher gridPublisher,
            BrowserOperations browserOperations,
            ExecutionOperations executionOperations) {
        this.botJobStateProvider = botJobStateProvider;
        this.gridPublisher = gridPublisher;
        this.browserOperations = browserOperations;
        this.executionOperations = executionOperations;
    }

    public static ScannerWorkspaceService getInstance() {
        return INSTANCE;
    }

    public void installExecutionOperations(ExecutionOperations executionOperations) {
        if (executionOperations == null) {
            throw new IllegalArgumentException("Scanner execution operations are required");
        }
        this.executionOperations = executionOperations;
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
            performAction(action, request, state);
            ScannerWorkspaceState updatedState = state(request.botJobId());
            return ScannerWorkspaceResponse.actionSuccess(
                    action, actionMessage(action), request, updatedState);
        } catch (RuntimeException error) {
            return ScannerWorkspaceResponse.failure(safe(error.getMessage()), "SCANNER_ACTION_FAILED", request, action);
        }
    }

    public ScannerWorkspaceState state(int botJobId) {
        return ScannerWorkspaceStateMapper.toScannerState(
                botJobStateProvider.apply(botJobId), browserOperations.browserState());
    }

    private String safe(String message) {
        return message == null || message.isBlank() ? "Scanner operation failed" : message;
    }

    private void performAction(
            ScannerWorkspaceAction action, ScannerWorkspaceRequest request, ScannerWorkspaceState state) {
        switch (action) {
            case CLEAR_GRID -> clearGrid(request, state);
            case REFRESH_PAGE -> refreshPage();
            case PREVIOUS_TAB -> switchTab(-1);
            case NEXT_TAB -> switchTab(1);
            case PRE_LAUNCH -> preLaunch(state);
            case STOP_PRE_LAUNCH -> stopPreLaunch(state);
            case PAGE_SCANNER -> pageScanner(request, state);
            default -> {
            }
        }
    }

    private void clearGrid(ScannerWorkspaceRequest request, ScannerWorkspaceState state) {
        gridPublisher.publishSearchTerms(
                request.sessionId(), state.homeBankingId(), ScannerWorkspacePayloads.emptyPayload(state));
    }

    private void refreshPage() {
        browserOperations.refreshPage();
    }

    private void switchTab(int direction) {
        browserOperations.switchTab(direction);
    }

    private void preLaunch(ScannerWorkspaceState state) {
        executionOperations.preLaunch(state.botJobId());
    }

    private void stopPreLaunch(ScannerWorkspaceState state) {
        executionOperations.stopPreLaunch(state.botJobId());
    }

    private void pageScanner(ScannerWorkspaceRequest request, ScannerWorkspaceState state) {
        List<ElementDTO> elements = browserOperations.scanPage(
                ScannerWorkspacePayloads.searchTerms(request), state.homeBankingId(), state.botJobId());
        if (elements.isEmpty()) {
            return;
        }
        clearGrid(request, state);
        gridPublisher.publishSearchTermsChunks(
                request.sessionId(),
                state.homeBankingId(),
                ScannerWorkspacePayloads.payload(state, elements),
                SCANNER_CHUNK_SIZE);
    }

    private String actionMessage(ScannerWorkspaceAction action) {
        return switch (action) {
            case CLEAR_GRID -> "Scanner grid cleared";
            case REFRESH_PAGE -> "Scanner browser page refreshed";
            case PAGE_SCANNER -> "Page scanner completed";
            case PREVIOUS_TAB -> "Scanner browser moved to previous tab";
            case NEXT_TAB -> "Scanner browser moved to next tab";
            case PRE_LAUNCH -> "Scanner Pre-Launch started";
            case STOP_PRE_LAUNCH -> "Scanner Pre-Launch stop requested";
            default -> "Scanner state refreshed";
        };
    }

    interface GridPublisher {
        void publishSearchTerms(String sessionId, int homeBankingId, SplitDTO payload);

        void publishSearchTermsChunks(String sessionId, int homeBankingId, SplitDTO payload, int chunkSize);
    }

    interface BrowserOperations {
        ScannerWorkspaceState.Browser browserState();

        void refreshPage();

        void switchTab(int direction);

        List<ElementDTO> scanPage(String[] searchTerms, int homeBankingId, int botJobId);
    }

    public interface ExecutionOperations {
        void preLaunch(int botJobId);

        void stopPreLaunch(int botJobId);
    }

}

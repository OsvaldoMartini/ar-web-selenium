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
            action = parseAction(request);
        } catch (RuntimeException error) {
            return ScannerWorkspaceResponse.failure(safe(error.getMessage()), "INVALID_SCANNER_ACTION", request, null);
        }
        try {
            ScannerWorkspaceState state = state(request.botJobId());
            validateActionAllowed(action, state);
            ActionOutcome outcome = performAction(action, request, state);
            ScannerWorkspaceState updatedState = state(request.botJobId());
            return ScannerWorkspaceResponse.actionSuccess(
                    action, outcome.message(), request, updatedState);
        } catch (RuntimeException error) {
            return ScannerWorkspaceResponse.failure(safe(error.getMessage()), "SCANNER_ACTION_FAILED", request, action);
        }
    }

    private ScannerWorkspaceAction parseAction(ScannerWorkspaceRequest request) {
        if (!request.body().has("action") || request.body().get("action").isJsonNull()) {
            throw new IllegalArgumentException("Scanner action is required");
        }
        return ScannerWorkspaceAction.parse(request.body().get("action").getAsString());
    }

    private void validateActionAllowed(ScannerWorkspaceAction action, ScannerWorkspaceState state) {
        switch (action) {
            case PAGE_SCANNER -> {
                if (!state.capabilities().canUsePageScanner()) {
                    throw new IllegalStateException("Page Scanner is not available for this Bot Job");
                }
                requireScannableBrowser("Page Scanner", state);
            }
            case REFRESH_PAGE -> requireScannableBrowser("Refresh Web Page", state);
            case PREVIOUS_TAB, NEXT_TAB -> requireScannableBrowser("Browser tab navigation", state);
            case PRE_LAUNCH -> {
                if (!state.capabilities().canExecute()) {
                    throw new IllegalStateException("Scanner execution is not available for this Bot Job");
                }
            }
            default -> {
            }
        }
    }

    private void requireScannableBrowser(String actionName, ScannerWorkspaceState state) {
        if (!state.browser().scannable()) {
            throw new IllegalStateException(actionName + " requires an open scanner browser");
        }
    }

    public ScannerWorkspaceState state(int botJobId) {
        return ScannerWorkspaceStateMapper.toScannerState(
                botJobStateProvider.apply(botJobId), browserOperations.browserState());
    }

    private String safe(String message) {
        return message == null || message.isBlank() ? "Scanner operation failed" : message;
    }

    private ActionOutcome performAction(
            ScannerWorkspaceAction action, ScannerWorkspaceRequest request, ScannerWorkspaceState state) {
        return switch (action) {
            case CLEAR_GRID -> clearGrid(request, state);
            case REFRESH_PAGE -> refreshPage();
            case PREVIOUS_TAB -> switchTab(-1);
            case NEXT_TAB -> switchTab(1);
            case PRE_LAUNCH -> preLaunch(state);
            case STOP_PRE_LAUNCH -> stopPreLaunch(state);
            case PAGE_SCANNER -> pageScanner(request, state);
            default -> ActionOutcome.message("Scanner state refreshed");
        };
    }

    private ActionOutcome clearGrid(ScannerWorkspaceRequest request, ScannerWorkspaceState state) {
        gridPublisher.publishSearchTerms(
                request.sessionId(), state.homeBankingId(), ScannerWorkspacePayloads.emptyPayload(state));
        return ActionOutcome.message("Scanner grid cleared");
    }

    private ActionOutcome refreshPage() {
        browserOperations.refreshPage();
        return ActionOutcome.message("Scanner browser page refreshed");
    }

    private ActionOutcome switchTab(int direction) {
        browserOperations.switchTab(direction);
        return ActionOutcome.message(
                direction < 0 ? "Scanner browser moved to previous tab" : "Scanner browser moved to next tab");
    }

    private ActionOutcome preLaunch(ScannerWorkspaceState state) {
        executionOperations.preLaunch(state.botJobId());
        return ActionOutcome.message("Scanner Pre-Launch started");
    }

    private ActionOutcome stopPreLaunch(ScannerWorkspaceState state) {
        executionOperations.stopPreLaunch(state.botJobId());
        return ActionOutcome.message("Scanner Pre-Launch stop requested");
    }

    private ActionOutcome pageScanner(ScannerWorkspaceRequest request, ScannerWorkspaceState state) {
        List<ElementDTO> elements = browserOperations.scanPage(
                ScannerWorkspacePayloads.searchTerms(request), state.homeBankingId(), state.botJobId());
        clearGrid(request, state);
        if (elements.isEmpty()) {
            return ActionOutcome.message("Page scanner completed: 0 elements");
        }
        gridPublisher.publishSearchTermsChunks(
                request.sessionId(),
                state.homeBankingId(),
                ScannerWorkspacePayloads.payload(state, elements),
                SCANNER_CHUNK_SIZE);
        return ActionOutcome.message("Page scanner completed: " + elements.size() + " elements");
    }

    private record ActionOutcome(String message) {
        private static ActionOutcome message(String message) {
            return new ActionOutcome(message);
        }
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

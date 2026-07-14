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
            if (action == ScannerWorkspaceAction.CLEAR_GRID) {
                gridPublisher.publishSearchTerms(
                        request.sessionId(), state.homeBankingId(), ScannerWorkspacePayloads.emptyPayload(state));
            } else if (action == ScannerWorkspaceAction.REFRESH_PAGE) {
                browserOperations.refreshPage();
            } else if (action == ScannerWorkspaceAction.PREVIOUS_TAB) {
                browserOperations.switchTab(-1);
            } else if (action == ScannerWorkspaceAction.NEXT_TAB) {
                browserOperations.switchTab(1);
            } else if (action == ScannerWorkspaceAction.PRE_LAUNCH) {
                executionOperations.preLaunch(state.botJobId());
            } else if (action == ScannerWorkspaceAction.STOP_PRE_LAUNCH) {
                executionOperations.stopPreLaunch(state.botJobId());
            } else if (action == ScannerWorkspaceAction.PAGE_SCANNER) {
                List<ElementDTO> elements = browserOperations.scanPage(
                        ScannerWorkspacePayloads.searchTerms(request), state.homeBankingId(), state.botJobId());
                if (!elements.isEmpty()) {
                    gridPublisher.publishSearchTerms(
                            request.sessionId(), state.homeBankingId(), ScannerWorkspacePayloads.emptyPayload(state));
                    gridPublisher.publishSearchTermsChunks(
                            request.sessionId(),
                            state.homeBankingId(),
                            ScannerWorkspacePayloads.payload(state, elements),
                            SCANNER_CHUNK_SIZE);
                }
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
                browserOperations.browserState(),
                new ScannerWorkspaceState.Focus(
                        "All - Interactive controls", ScannerWorkspacePayloads.defaultPageScanTerms()),
                new ScannerWorkspaceState.Ocr(sourceCapabilities.canUsePreScan(), "IDLE"),
                capabilities,
                source.executionState());
    }

    private String safe(String message) {
        return message == null || message.isBlank() ? "Scanner operation failed" : message;
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

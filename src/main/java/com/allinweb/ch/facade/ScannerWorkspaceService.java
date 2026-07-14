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
    private static final ScannerWorkspaceService INSTANCE =
            new ScannerWorkspaceService(
                    BotJobDetailsService.getInstance()::currentState,
                    new ScannerGridPublisher(),
                    new ScannerBrowserOperations(),
                    new ScannerExecutionOperations());

    private final IntFunction<BotJobDetailsState> botJobStateProvider;
    private final GridPublisher gridPublisher;
    private final BrowserOperations browserOperations;
    private final ScannerWorkspaceActionParser actionParser = new ScannerWorkspaceActionParser();
    private final ScannerWorkspaceActionGate actionGate = new ScannerWorkspaceActionGate();
    private final ScannerWorkspaceActionExecutor actionExecutor;
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
        this.actionExecutor = new ScannerWorkspaceActionExecutor(
                gridPublisher, browserOperations, () -> this.executionOperations);
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
                    error.getMessage(), "SCANNER_BOOTSTRAP_FAILED", request, null);
        }
    }

    public ScannerWorkspaceResponse action(ScannerWorkspaceRequest request) {
        ScannerWorkspaceAction action;
        try {
            action = actionParser.parse(request);
        } catch (RuntimeException error) {
            return ScannerWorkspaceResponse.failure(error.getMessage(), "INVALID_SCANNER_ACTION", request, null);
        }
        try {
            ScannerWorkspaceState state = state(request.botJobId());
            actionGate.validateAllowed(action, state);
            ScannerWorkspaceActionExecutor.Outcome outcome = actionExecutor.perform(action, request, state);
            ScannerWorkspaceState updatedState = state(request.botJobId());
            return ScannerWorkspaceResponse.actionSuccess(
                    action, outcome.message(), request, updatedState);
        } catch (RuntimeException error) {
            return ScannerWorkspaceResponse.failure(error.getMessage(), "SCANNER_ACTION_FAILED", request, action);
        }
    }

    public ScannerWorkspaceState state(int botJobId) {
        return ScannerWorkspaceStateMapper.toScannerState(
                botJobStateProvider.apply(botJobId), browserOperations.browserState());
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

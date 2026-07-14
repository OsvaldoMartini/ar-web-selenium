package com.allinweb.ch.facade;

import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.ScannerWorkspaceAction;
import com.allinweb.ch.model.ScannerWorkspaceRequest;
import com.allinweb.ch.model.ScannerWorkspaceState;
import java.util.List;
import java.util.function.Supplier;

final class ScannerWorkspaceActionExecutor {
    private static final int SCANNER_CHUNK_SIZE = 25;

    private final ScannerWorkspaceService.GridPublisher gridPublisher;
    private final ScannerWorkspaceService.BrowserOperations browserOperations;
    private final Supplier<ScannerWorkspaceService.ExecutionOperations> executionOperations;

    ScannerWorkspaceActionExecutor(
            ScannerWorkspaceService.GridPublisher gridPublisher,
            ScannerWorkspaceService.BrowserOperations browserOperations,
            Supplier<ScannerWorkspaceService.ExecutionOperations> executionOperations) {
        this.gridPublisher = gridPublisher;
        this.browserOperations = browserOperations;
        this.executionOperations = executionOperations;
    }

    Outcome perform(ScannerWorkspaceAction action, ScannerWorkspaceRequest request, ScannerWorkspaceState state) {
        return switch (action) {
            case CLEAR_GRID -> clearGrid(request, state);
            case REFRESH_PAGE -> refreshPage();
            case PREVIOUS_TAB -> switchTab(-1);
            case NEXT_TAB -> switchTab(1);
            case PRE_LAUNCH -> preLaunch(state);
            case STOP_PRE_LAUNCH -> stopPreLaunch(state);
            case PAGE_SCANNER -> pageScanner(request, state);
            default -> Outcome.message("Scanner state refreshed");
        };
    }

    private Outcome clearGrid(ScannerWorkspaceRequest request, ScannerWorkspaceState state) {
        publishClearGrid(request, state);
        return Outcome.message("Scanner grid cleared");
    }

    private void publishClearGrid(ScannerWorkspaceRequest request, ScannerWorkspaceState state) {
        gridPublisher.publishSearchTerms(
                request.sessionId(), state.homeBankingId(), ScannerWorkspacePayloads.emptyPayload(state));
    }

    private Outcome refreshPage() {
        browserOperations.refreshPage();
        return Outcome.message("Scanner browser page refreshed");
    }

    private Outcome switchTab(int direction) {
        browserOperations.switchTab(direction);
        return Outcome.message(
                direction < 0 ? "Scanner browser moved to previous tab" : "Scanner browser moved to next tab");
    }

    private Outcome preLaunch(ScannerWorkspaceState state) {
        executionOperations.get().preLaunch(state.botJobId());
        return Outcome.message("Scanner Pre-Launch started");
    }

    private Outcome stopPreLaunch(ScannerWorkspaceState state) {
        executionOperations.get().stopPreLaunch(state.botJobId());
        return Outcome.message("Scanner Pre-Launch stop requested");
    }

    private Outcome pageScanner(ScannerWorkspaceRequest request, ScannerWorkspaceState state) {
        List<ElementDTO> elements = browserOperations.scanPage(
                ScannerWorkspacePayloads.searchTerms(request), state.homeBankingId(), state.botJobId());
        publishClearGrid(request, state);
        if (elements.isEmpty()) {
            return Outcome.message("Page scanner completed: 0 elements");
        }
        gridPublisher.publishSearchTermsChunks(
                request.sessionId(),
                state.homeBankingId(),
                ScannerWorkspacePayloads.payload(state, elements),
                SCANNER_CHUNK_SIZE);
        return Outcome.message("Page scanner completed: " + elements.size() + " elements");
    }

    record Outcome(String message) {
        private static Outcome message(String message) {
            return new Outcome(message);
        }
    }
}

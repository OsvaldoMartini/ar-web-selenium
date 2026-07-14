package com.allinweb.ch.facade;

import com.allinweb.ch.model.BotJobDetailsState;
import com.allinweb.ch.model.ScannerWorkspaceAction;
import com.allinweb.ch.model.ScannerWorkspaceRequest;
import com.allinweb.ch.model.ScannerWorkspaceResponse;
import com.allinweb.ch.model.ScannerWorkspaceState;
import java.util.List;
import java.util.function.IntFunction;

public final class ScannerWorkspaceService {

    private static final ScannerWorkspaceService INSTANCE =
            new ScannerWorkspaceService(BotJobDetailsService.getInstance()::currentState);

    private final IntFunction<BotJobDetailsState> botJobStateProvider;

    ScannerWorkspaceService(IntFunction<BotJobDetailsState> botJobStateProvider) {
        this.botJobStateProvider = botJobStateProvider;
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
        if (action != ScannerWorkspaceAction.REFRESH_STATE) {
            return ScannerWorkspaceResponse.failure("Unsupported Scanner action", "INVALID_SCANNER_ACTION", request, action);
        }
        try {
            return ScannerWorkspaceResponse.actionSuccess(
                    action, "Scanner state refreshed", request, state(request.botJobId()));
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
}

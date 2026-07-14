package com.allinweb.ch.facade;

import com.allinweb.ch.model.BotJobDetailsState;
import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.ScannerWorkspaceAction;
import com.allinweb.ch.model.ScannerWorkspaceRequest;
import com.allinweb.ch.model.ScannerWorkspaceResponse;
import com.allinweb.ch.model.ScannerWorkspaceState;
import com.allinweb.ch.model.SplitDTO;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

public final class ScannerWorkspaceService {

    private static final int SCANNER_CHUNK_SIZE = 25;
    private static final String[] DEFAULT_PAGE_SCAN_TERMS = {
        "input",
        "textarea",
        "button",
        "a",
        "select",
        "option",
        "label",
        "[contenteditable='true']",
        "[role='button']",
        "[role='link']",
        "[role='option']",
        "[role='menuitem']",
        "[role='tab']",
        "[role='checkbox']",
        "[role='radio']",
        "[role='switch']",
        "[role='treeitem']",
        "[role='combobox']",
        "[role='textbox']",
        "[aria-haspopup]",
        "mat-select",
        "mat-option",
        "mat-radio-button",
        "mat-checkbox",
        "mat-slide-toggle",
        "mat-button-toggle",
        "mat-expansion-panel-header",
        "mat-tab",
        "mat-menu-item",
        "mat-tree-node",
        "svg[role='button']",
        "svg[aria-label]",
        "[mat-icon-button]",
        "mat-icon"
    };

    private static final ScannerWorkspaceService INSTANCE =
            new ScannerWorkspaceService(
                    BotJobDetailsService.getInstance()::currentState,
                    new ScannerGridPublisher(),
                    new ScannerBrowserOperations(),
                    new DefaultExecutionOperations());

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
                gridPublisher.publishSearchTerms(request.sessionId(), state.homeBankingId(), emptyPayload(state));
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
                List<ElementDTO> elements =
                        browserOperations.scanPage(searchTerms(request), state.homeBankingId(), state.botJobId());
                if (!elements.isEmpty()) {
                    gridPublisher.publishSearchTerms(request.sessionId(), state.homeBankingId(), emptyPayload(state));
                    gridPublisher.publishSearchTermsChunks(
                            request.sessionId(), state.homeBankingId(), payload(state, elements), SCANNER_CHUNK_SIZE);
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
                new ScannerWorkspaceState.Focus("All - Interactive controls", List.of(DEFAULT_PAGE_SCAN_TERMS)),
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

    private String[] searchTerms(ScannerWorkspaceRequest request) {
        if (!request.body().has("searchTerms")) {
            return Arrays.copyOf(DEFAULT_PAGE_SCAN_TERMS, DEFAULT_PAGE_SCAN_TERMS.length);
        }
        JsonElement value = request.body().get("searchTerms");
        if (value == null || value.isJsonNull()) {
            return Arrays.copyOf(DEFAULT_PAGE_SCAN_TERMS, DEFAULT_PAGE_SCAN_TERMS.length);
        }
        if (value.isJsonArray()) {
            JsonArray array = value.getAsJsonArray();
            List<String> terms = java.util.stream.StreamSupport.stream(array.spliterator(), false)
                    .filter(JsonElement::isJsonPrimitive)
                    .map(JsonElement::getAsString)
                    .map(String::trim)
                    .filter(term -> !term.isEmpty())
                    .toList();
            return terms.isEmpty() ? Arrays.copyOf(DEFAULT_PAGE_SCAN_TERMS, DEFAULT_PAGE_SCAN_TERMS.length)
                    : terms.toArray(new String[0]);
        }
        String searchText = value.getAsString();
        if (searchText == null || searchText.isBlank()) {
            return Arrays.copyOf(DEFAULT_PAGE_SCAN_TERMS, DEFAULT_PAGE_SCAN_TERMS.length);
        }
        return Arrays.stream(searchText.split("\\s*,\\s*"))
                .map(String::trim)
                .filter(term -> !term.isEmpty())
                .toArray(String[]::new);
    }

    private SplitDTO emptyPayload(ScannerWorkspaceState state) {
        return payload(state, List.of());
    }

    private SplitDTO payload(ScannerWorkspaceState state, List<ElementDTO> elements) {
        SplitDTO payload = new SplitDTO();
        payload.setHomeBankingId(state.homeBankingId());
        payload.setBotJobId(state.botJobId());
        payload.setBotJobName(state.botJobName());
        payload.setType("SEARCH_TOOL");
        payload.setSessionId("scannerGrid");
        payload.setOperationId("searchTerms");
        payload.setElementDetails(elements.toArray(new ElementDTO[0]));
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

    private static final class DefaultExecutionOperations implements ExecutionOperations {
        @Override
        public void preLaunch(int botJobId) {
            throw new IllegalStateException("Scanner Pre-Launch backend adapter is not connected yet");
        }

        @Override
        public void stopPreLaunch(int botJobId) {
            PerformActions.getInstance().setInterceptBotJob(true);
        }
    }
}

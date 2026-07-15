package com.allinweb.ch.facade;

import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.ScannerWorkspaceOperations;
import com.allinweb.ch.model.ScannerWorkspaceRequest;
import com.allinweb.ch.model.ScannerWorkspaceState;
import com.allinweb.ch.model.SplitDTO;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.Arrays;
import java.util.List;

final class ScannerWorkspacePayloads {
    private static final ScannerGridPublisher SCANNER_GRID_PUBLISHER =
            new ScannerGridPublisher(new NoopScannerGridSender());
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

    private ScannerWorkspacePayloads() {}

    static List<String> defaultPageScanTerms() {
        return List.of(DEFAULT_PAGE_SCAN_TERMS);
    }

    static String[] searchTerms(ScannerWorkspaceRequest request) {
        if (!request.body().has(ScannerWorkspaceOperations.SEARCH_TERMS)) {
            return Arrays.copyOf(DEFAULT_PAGE_SCAN_TERMS, DEFAULT_PAGE_SCAN_TERMS.length);
        }
        JsonElement value = request.body().get(ScannerWorkspaceOperations.SEARCH_TERMS);
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
        String[] terms = Arrays.stream(searchText.split("\\s*,\\s*"))
                .map(String::trim)
                .filter(term -> !term.isEmpty())
                .toArray(String[]::new);
        return terms.length == 0 ? Arrays.copyOf(DEFAULT_PAGE_SCAN_TERMS, DEFAULT_PAGE_SCAN_TERMS.length) : terms;
    }

    static SplitDTO emptyPayload(ScannerWorkspaceState state) {
        return payload(state, List.of());
    }

    static SplitDTO payload(ScannerWorkspaceState state, List<ElementDTO> elements) {
        return SCANNER_GRID_PUBLISHER.searchTermsPayload(
                state.homeBankingId(),
                state.botJobId(),
                state.botJobName(),
                elements.toArray(new ElementDTO[0]),
                state.blocks().stream().map(ScannerWorkspaceBlockOptions::from).toList());
    }

    private static final class NoopScannerGridSender implements ScannerGridPublisher.Sender {
        @Override
        public void sendMessageJson(int homeBankingId, String sessionId, String json, String operationId) {}
    }
}

package com.allinweb.ch.model;

import java.util.List;

public record ScannerWorkspaceState(
        long revision,
        int botJobId,
        String botJobName,
        int homeBankingId,
        String environmentUrl,
        List<Block> blocks,
        Browser browser,
        Focus focus,
        Ocr ocr,
        Capabilities capabilities,
        String executionState) {

    public ScannerWorkspaceState {
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
    }

    public record Block(int id, int order, String name, boolean active) {}

    public record Browser(String state, String activeUrl, String activeTitle, int openTabs, boolean scannable) {}

    public record Focus(String profile, List<String> searchTerms) {
        public Focus {
            searchTerms = searchTerms == null ? List.of() : List.copyOf(searchTerms);
        }
    }

    public record Ocr(boolean available, String status) {}

    public record Capabilities(
            boolean canRefreshState,
            boolean canUsePageScanner,
            boolean canUseOcr,
            boolean canExecute,
            boolean canApplyElements) {}
}

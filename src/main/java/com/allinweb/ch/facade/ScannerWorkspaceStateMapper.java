package com.allinweb.ch.facade;

import com.allinweb.ch.model.BotJobDetailsState;
import com.allinweb.ch.model.ScannerWorkspaceState;
import java.util.List;

final class ScannerWorkspaceStateMapper {

    private ScannerWorkspaceStateMapper() {}

    static ScannerWorkspaceState toScannerState(
            BotJobDetailsState source, ScannerWorkspaceState.Browser browserState) {
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
                browserState,
                new ScannerWorkspaceState.Focus(
                        "All - Interactive controls", ScannerWorkspacePayloads.defaultPageScanTerms()),
                new ScannerWorkspaceState.Ocr(sourceCapabilities.canUsePreScan(), "IDLE"),
                capabilities,
                source.executionState());
    }
}

package com.allinweb.ch.facade;

import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.HomeBankingLoadDTO;
import java.util.Objects;

final class ScannerRuntimeShellHandler implements ScannerShellLifecycle.Handler {
    private final ScannerRuntime runtime;

    ScannerRuntimeShellHandler(ScannerRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    @Override
    public void openShell(HomeBankingLoadDTO homeBanking, BotJobLoadDTO botJob, BlockLoadDTO block) {
        runtime.initialize(homeBanking, botJob, block);
        runtime.showModal();
    }

    @Override
    public void closeWebDrivers() {
        runtime.closeWebDrivers();
    }

    @Override
    public void closeModal() {
        runtime.closeModal();
    }

    @Override
    public Integer currentBotJobId() {
        BotJobLoadDTO current = runtime.getCurrentBotJob();
        return current == null ? null : current.getId();
    }
}

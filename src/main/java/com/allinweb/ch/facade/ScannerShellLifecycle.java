package com.allinweb.ch.facade;

import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.HomeBankingLoadDTO;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Lifecycle hook for the legacy scanner shell, kept outside callers to avoid direct scene coupling. */
public final class ScannerShellLifecycle {
    private static final ScannerShellLifecycle INSTANCE = new ScannerShellLifecycle();

    private final AtomicReference<Handler> handler = new AtomicReference<>(Handler.noop());

    private ScannerShellLifecycle() {}

    public static ScannerShellLifecycle getInstance() {
        return INSTANCE;
    }

    public void install(Handler handler) {
        this.handler.set(Objects.requireNonNull(handler, "handler"));
    }

    public void reset() {
        handler.set(Handler.noop());
    }

    public void openShell(HomeBankingLoadDTO homeBanking, BotJobLoadDTO botJob, BlockLoadDTO block) {
        handler.get().openShell(homeBanking, botJob, block);
    }

    public void closeShell() {
        Handler current = handler.get();
        current.closeWebDrivers();
        current.closeModal();
    }

    public void closeWebDrivers() {
        handler.get().closeWebDrivers();
    }

    public void closeModal() {
        handler.get().closeModal();
    }

    public Integer currentBotJobId() {
        return handler.get().currentBotJobId();
    }

    public interface Handler {
        void openShell(HomeBankingLoadDTO homeBanking, BotJobLoadDTO botJob, BlockLoadDTO block);

        void closeWebDrivers();

        void closeModal();

        Integer currentBotJobId();

        static Handler noop() {
            return new Handler() {
                @Override
                public void openShell(HomeBankingLoadDTO homeBanking, BotJobLoadDTO botJob, BlockLoadDTO block) {}

                @Override
                public void closeWebDrivers() {}

                @Override
                public void closeModal() {}

                @Override
                public Integer currentBotJobId() {
                    return null;
                }
            };
        }
    }
}

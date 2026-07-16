package com.allinweb.ch.facade;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Close hook for the legacy scanner shell, kept outside the pane to avoid pane-to-scene coupling. */
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

    public void closeShell() {
        Handler current = handler.get();
        current.closeWebDrivers();
        current.closeModal();
    }

    public interface Handler {
        void closeWebDrivers();

        void closeModal();

        static Handler noop() {
            return new Handler() {
                @Override
                public void closeWebDrivers() {}

                @Override
                public void closeModal() {}
            };
        }
    }
}

package com.allinweb.ch.facade;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Lifecycle hook for the legacy main dashboard shell. */
public final class MainShellLifecycle {
    private static final MainShellLifecycle INSTANCE = new MainShellLifecycle();

    private final AtomicReference<Handler> handler = new AtomicReference<>(Handler.noop());

    private MainShellLifecycle() {}

    public static MainShellLifecycle getInstance() {
        return INSTANCE;
    }

    public void install(Handler handler) {
        this.handler.set(Objects.requireNonNull(handler, "handler"));
    }

    public void reset() {
        handler.set(Handler.noop());
    }

    public void openMain(boolean enabledLicence) {
        handler.get().openMain(enabledLicence);
    }

    public void openMain(boolean enabledLicence, String initialSessionId) {
        handler.get().openMain(enabledLicence, initialSessionId);
    }

    public interface Handler {
        void openMain(boolean enabledLicence);

        void openMain(boolean enabledLicence, String initialSessionId);

        static Handler noop() {
            return new Handler() {
                @Override
                public void openMain(boolean enabledLicence) {}

                @Override
                public void openMain(boolean enabledLicence, String initialSessionId) {}
            };
        }
    }
}

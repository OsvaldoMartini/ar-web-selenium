package com.allinweb.ch.facade;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Lifecycle hook for the legacy configuration manager shell. */
public final class ConfigManagerLifecycle {
    private static final ConfigManagerLifecycle INSTANCE = new ConfigManagerLifecycle();

    private final AtomicReference<Handler> handler = new AtomicReference<>(Handler.noop());

    private ConfigManagerLifecycle() {}

    public static ConfigManagerLifecycle getInstance() {
        return INSTANCE;
    }

    public void install(Handler handler) {
        this.handler.set(Objects.requireNonNull(handler, "handler"));
    }

    public void reset() {
        handler.set(Handler.noop());
    }

    public void openConfig(boolean enabledLicence) {
        handler.get().openConfig(enabledLicence);
    }

    public void closeModal() {
        handler.get().closeModal();
    }

    public interface Handler {
        void openConfig(boolean enabledLicence);

        void closeModal();

        static Handler noop() {
            return new Handler() {
                @Override
                public void openConfig(boolean enabledLicence) {}

                @Override
                public void closeModal() {}
            };
        }
    }
}

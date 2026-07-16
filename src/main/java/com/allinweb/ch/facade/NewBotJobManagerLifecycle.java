package com.allinweb.ch.facade;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Lifecycle hook for the legacy new bot job manager shell. */
public final class NewBotJobManagerLifecycle {
    private static final NewBotJobManagerLifecycle INSTANCE = new NewBotJobManagerLifecycle();

    private final AtomicReference<Handler> handler = new AtomicReference<>(Handler.noop());

    private NewBotJobManagerLifecycle() {}

    public static NewBotJobManagerLifecycle getInstance() {
        return INSTANCE;
    }

    public void install(Handler handler) {
        this.handler.set(Objects.requireNonNull(handler, "handler"));
    }

    public void reset() {
        handler.set(Handler.noop());
    }

    public void openNewBotJob(boolean enabledLicence) {
        handler.get().openNewBotJob(enabledLicence);
    }

    public void closeModal() {
        handler.get().closeModal();
    }

    public interface Handler {
        void openNewBotJob(boolean enabledLicence);

        void closeModal();

        static Handler noop() {
            return new Handler() {
                @Override
                public void openNewBotJob(boolean enabledLicence) {}

                @Override
                public void closeModal() {}
            };
        }
    }
}

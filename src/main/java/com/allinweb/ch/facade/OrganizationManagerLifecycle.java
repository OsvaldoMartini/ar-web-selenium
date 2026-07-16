package com.allinweb.ch.facade;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Lifecycle hook for the legacy organization manager shell. */
public final class OrganizationManagerLifecycle {
    private static final OrganizationManagerLifecycle INSTANCE = new OrganizationManagerLifecycle();

    private final AtomicReference<Handler> handler = new AtomicReference<>(Handler.noop());

    private OrganizationManagerLifecycle() {}

    public static OrganizationManagerLifecycle getInstance() {
        return INSTANCE;
    }

    public void install(Handler handler) {
        this.handler.set(Objects.requireNonNull(handler, "handler"));
    }

    public void reset() {
        handler.set(Handler.noop());
    }

    public void openOrganizations() {
        handler.get().openOrganizations();
    }

    public void closeModal() {
        handler.get().closeModal();
    }

    public interface Handler {
        void openOrganizations();

        void closeModal();

        static Handler noop() {
            return new Handler() {
                @Override
                public void openOrganizations() {}

                @Override
                public void closeModal() {}
            };
        }
    }
}

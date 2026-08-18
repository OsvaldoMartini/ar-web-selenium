package com.allinweb.ch.facade;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Dispatches UI work without coupling callers to a concrete UI toolkit. */
public final class UiThreadDispatcher {
    private static final UiThreadDispatcher INSTANCE = new UiThreadDispatcher();

    private final AtomicReference<Dispatcher> dispatcher = new AtomicReference<>(Runnable::run);

    private UiThreadDispatcher() {}

    public static UiThreadDispatcher getInstance() {
        return INSTANCE;
    }

    public void install(Dispatcher dispatcher) {
        this.dispatcher.set(Objects.requireNonNull(dispatcher, "dispatcher"));
    }

    public void reset() {
        dispatcher.set(Runnable::run);
    }

    public void execute(Runnable task) {
        dispatcher.get().execute(Objects.requireNonNull(task, "task"));
    }

    @FunctionalInterface
    public interface Dispatcher {
        void execute(Runnable task);
    }
}

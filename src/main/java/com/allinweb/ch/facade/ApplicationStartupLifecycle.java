package com.allinweb.ch.facade;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Coordinates startup continuation without coupling backend handlers to the application shell. */
public final class ApplicationStartupLifecycle {
    private static final ApplicationStartupLifecycle INSTANCE = new ApplicationStartupLifecycle();

    private final AtomicBoolean activationPending = new AtomicBoolean(false);
    private final AtomicBoolean continuationStarted = new AtomicBoolean(false);
    private final AtomicReference<Runnable> activationContinuation = new AtomicReference<>(() -> {});

    private ApplicationStartupLifecycle() {}

    public static ApplicationStartupLifecycle getInstance() {
        return INSTANCE;
    }

    public void waitForActivation(Runnable continuation) {
        activationContinuation.set(Objects.requireNonNull(continuation, "continuation"));
        continuationStarted.set(false);
        activationPending.set(true);
    }

    public boolean continueAfterActivation() {
        if (!activationPending.get()) {
            return false;
        }
        if (!continuationStarted.compareAndSet(false, true)) {
            return false;
        }
        activationPending.set(false);
        activationContinuation.get().run();
        return true;
    }

    public void reset() {
        activationPending.set(false);
        continuationStarted.set(false);
        activationContinuation.set(() -> {});
    }
}

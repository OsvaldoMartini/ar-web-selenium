package com.allinweb.ch.socket;

import com.allinweb.ch.facade.ApplicationShutdownCoordinator;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Turns loss of the primary desktop shell's stable control socket into terminal application
 * shutdown. A short grace period distinguishes a real native-window close from refresh/StrictMode
 * reconnects.
 */
final class MainApplicationControlLifecycle {

    static final String SESSION_ID = "mainApplicationControl";
    static final Duration RECONNECT_GRACE = Duration.ofSeconds(2);

    private final ConnectionProbe connectionProbe;
    private final ShutdownRequest shutdownRequest;
    private final DeferredExecutor deferredExecutor;
    private final Duration reconnectGrace;
    private long generation;
    private boolean connected;

    private MainApplicationControlLifecycle() {
        this(
                WebSocketSessionManager::isSessionOpen,
                () -> ApplicationShutdownCoordinator.getInstance().requestShutdown(),
                (delay, task) -> CompletableFuture.delayedExecutor(
                                delay.toMillis(), TimeUnit.MILLISECONDS)
                        .execute(task),
                RECONNECT_GRACE);
    }

    MainApplicationControlLifecycle(
            ConnectionProbe connectionProbe,
            ShutdownRequest shutdownRequest,
            DeferredExecutor deferredExecutor,
            Duration reconnectGrace) {
        this.connectionProbe = Objects.requireNonNull(connectionProbe, "Main application connection probe is required");
        this.shutdownRequest = Objects.requireNonNull(shutdownRequest, "Main application shutdown request is required");
        this.deferredExecutor = Objects.requireNonNull(deferredExecutor, "Main application deferred executor is required");
        this.reconnectGrace = Objects.requireNonNull(reconnectGrace, "Main application reconnect grace is required");
        if (reconnectGrace.isZero() || reconnectGrace.isNegative()) {
            throw new IllegalArgumentException("Main application reconnect grace must be positive");
        }
    }

    static MainApplicationControlLifecycle getInstance() {
        return InstanceHolder.INSTANCE;
    }

    static boolean isControlSessionId(String sessionId) {
        return SESSION_ID.equals(sessionId);
    }

    synchronized void connected(String sessionId) {
        requireControlSession(sessionId);
        connected = true;
        generation++;
    }

    synchronized void disconnected(String sessionId) {
        requireControlSession(sessionId);
        connected = false;
        long disconnectedGeneration = ++generation;
        deferredExecutor.schedule(
                reconnectGrace,
                () -> shutdownIfStillDisconnected(disconnectedGeneration));
    }

    private synchronized void shutdownIfStillDisconnected(long disconnectedGeneration) {
        if (generation != disconnectedGeneration || connected) return;
        if (connectionProbe.isOpen(SESSION_ID)) {
            connected = true;
            generation++;
            return;
        }
        // Advance first so a duplicate delayed callback can never own the same close event.
        generation++;
        shutdownRequest.request();
    }

    private static void requireControlSession(String sessionId) {
        if (!isControlSessionId(sessionId)) {
            throw new IllegalArgumentException("Main application control session is invalid");
        }
    }

    @FunctionalInterface
    interface ConnectionProbe {
        boolean isOpen(String sessionId);
    }

    @FunctionalInterface
    interface ShutdownRequest {
        void request();
    }

    @FunctionalInterface
    interface DeferredExecutor {
        void schedule(Duration delay, Runnable task);
    }

    private static final class InstanceHolder {
        private static final MainApplicationControlLifecycle INSTANCE = new MainApplicationControlLifecycle();
    }
}

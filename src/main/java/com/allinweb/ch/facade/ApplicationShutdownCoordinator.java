package com.allinweb.ch.facade;

import com.allinweb.ch.component.pane.BotJobDetailsWorkspaceHost;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.executors.AppExecutors;
import com.allinweb.ch.socket.ARWebSocketServer;
import com.allinweb.ch.socket.ARWebSocketServerIP;
import com.allinweb.ch.socket.SmokeTestIntegrationService;
import com.allinweb.ch.socket.WebSocketSessionManager;
import com.allinweb.ch.util.SingleInstance;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

/**
 * Owns the one terminal shutdown sequence for the AR Web desktop application.
 *
 * <p>The coordinator is deliberately idempotent: the Exit button, a main-window close notification,
 * and a WebSocket error may all request shutdown at nearly the same time, but only one worker is
 * allowed to run. Every cleanup stage is best-effort so one damaged subsystem cannot prevent the
 * backend process from terminating.
 */
@Slf4j
public final class ApplicationShutdownCoordinator {

    public static final String SHUTDOWN_OPERATION = "application.shutdown";
    private static final ApplicationShutdownCoordinator INSTANCE =
            new ApplicationShutdownCoordinator(new DefaultShutdownOperations(), ApplicationShutdownCoordinator::startWorker);

    private final ShutdownOperations operations;
    private final Executor executor;
    private final AtomicBoolean shutdownRequested = new AtomicBoolean();

    ApplicationShutdownCoordinator(ShutdownOperations operations, Executor executor) {
        this.operations = Objects.requireNonNull(operations, "Shutdown operations are required");
        this.executor = Objects.requireNonNull(executor, "Shutdown executor is required");
    }

    public static ApplicationShutdownCoordinator getInstance() {
        return INSTANCE;
    }

    /**
     * Starts the terminal shutdown sequence once and returns immediately in production.
     *
     * @return {@code true} only for the request that acquired shutdown ownership
     */
    public boolean requestShutdown() {
        if (!shutdownRequested.compareAndSet(false, true)) return false;
        try {
            executor.execute(this::shutdown);
        } catch (RuntimeException rejected) {
            log.error("The configured shutdown executor rejected the terminal cleanup; using a fallback worker", rejected);
            startWorker(this::shutdown);
        }
        return true;
    }

    public boolean isShutdownRequested() {
        return shutdownRequested.get();
    }

    private void shutdown() {
        log.info("AR Web application shutdown started");
        runStage("notify desktop pages", operations::broadcastShutdown);
        runStage("stop owned automation", operations::stopOwnedAutomation);
        runStage("stop plugin watcher", operations::stopPluginWatcher);
        runStage("retire desktop workspaces", operations::retireWorkspaces);
        runStage("close WebSocket sessions", operations::closeSessions);
        runStage("shutdown executors", operations::shutdownExecutors);
        runStage("stop local servers", operations::stopServers);
        runStage("release single-instance lock", operations::releaseSingleInstance);
        runStage("terminate backend", () -> operations.exitBackend(0));
    }

    private static void runStage(String name, Runnable stage) {
        try {
            stage.run();
        } catch (Throwable failure) {
            log.warn("Application shutdown stage '{}' failed: {}", name, failure.getMessage(), failure);
        }
    }

    private static void startWorker(Runnable task) {
        Thread worker = new Thread(task, "arweb-application-shutdown");
        // This worker must survive after Jetty/executor threads have been stopped and reach System.exit.
        worker.setDaemon(false);
        worker.start();
    }

    interface ShutdownOperations {
        void broadcastShutdown();

        void stopOwnedAutomation();

        void stopPluginWatcher();

        void retireWorkspaces();

        void closeSessions();

        void shutdownExecutors();

        void stopServers();

        void releaseSingleInstance();

        void exitBackend(int status);
    }

    private static final class DefaultShutdownOperations implements ShutdownOperations {
        @Override
        public void broadcastShutdown() {
            WebSocketSessionManager.getInstance()
                    .broadcastJsonToAll(-1, "{\"reason\":\"Main application window closed\"}", SHUTDOWN_OPERATION);
        }

        @Override
        public void stopOwnedAutomation() {
            SmokeTestIntegrationService.getInstance().shutdown();
            BotJobDetailsWorkspaceHost.getInstance().shutdownForApplication();
            ScannerRuntime.getInstance().shutdownForApplication();
            // Preserve the original terminal cleanup contract: close the Playwright object itself,
            // not only the current browser/context owned by ScannerRuntime.
            ARWebDriver.getInstance().shutdown();
        }

        @Override
        public void stopPluginWatcher() {
            PluginFileWatcher.getInstance().stop();
        }

        @Override
        public void retireWorkspaces() {
            ARWebSocketServer.retireOwnedDesktopWorkspaces();
        }

        @Override
        public void closeSessions() {
            WebSocketSessionManager.closeAllSessions("AR Web application is shutting down");
        }

        @Override
        public void shutdownExecutors() {
            AppExecutors.get().close();
        }

        @Override
        public void stopServers() {
            ARWebSocketServerIP.stopIfInitialized();
            ARWebSocketServer.stopIfInitialized();
        }

        @Override
        public void releaseSingleInstance() {
            SingleInstance.release();
        }

        @Override
        public void exitBackend(int status) {
            System.exit(status);
        }
    }
}

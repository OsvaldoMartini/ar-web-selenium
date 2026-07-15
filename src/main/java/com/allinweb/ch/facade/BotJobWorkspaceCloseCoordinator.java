package com.allinweb.ch.facade;

import com.allinweb.ch.model.ScannerWorkspaceSessions;
import java.util.List;

/** UI-independent close gate and cleanup ordering for the Bot Job Details workspace. */
public final class BotJobWorkspaceCloseCoordinator {

    private static final List<String> SESSIONS = List.of(
            "botJobTasks", "componentTasks", ScannerWorkspaceSessions.PRE_SCANNER_GRID);

    private final BusyPort busy;
    private final ExecutionPort execution;
    private final SurfacePort surfaces;
    private final SessionPort sessions;
    private final BrowserPort browser;
    private final ErrorPort errors;

    public BotJobWorkspaceCloseCoordinator(
            BusyPort busy,
            ExecutionPort execution,
            SurfacePort surfaces,
            SessionPort sessions,
            BrowserPort browser,
            ErrorPort errors) {
        this.busy = busy;
        this.execution = execution;
        this.surfaces = surfaces;
        this.sessions = sessions;
        this.browser = browser;
        this.errors = errors;
    }

    public boolean canClose(Integer botJobId) {
        if (busy.isBusy()) return false;
        if (botJobId == null || botJobId <= 0) return true;
        try {
            return !execution.isActive(botJobId);
        } catch (RuntimeException staleWorkspace) {
            return true;
        }
    }

    public void close(Integer botJobId) {
        if (!canClose(botJobId)) {
            throw new IllegalStateException(
                    "Wait for the active Bot Job operation to finish or stop TEST RUN first");
        }
        RuntimeException surfaceFailure = null;
        if (botJobId != null && botJobId > 0) {
            try {
                surfaces.suspend(botJobId);
            } catch (RuntimeException failure) {
                surfaceFailure = failure;
            } finally {
                execution.close(botJobId);
                for (String session : SESSIONS) sessions.clear(session, botJobId);
            }
        }
        try {
            browser.shutdown();
        } catch (RuntimeException failure) {
            errors.browserCloseFailure(failure);
        }
        if (surfaceFailure != null) throw surfaceFailure;
    }

    @FunctionalInterface
    public interface BusyPort { boolean isBusy(); }

    public interface ExecutionPort {
        boolean isActive(int botJobId);
        void close(int botJobId);
    }

    @FunctionalInterface
    public interface SurfacePort { void suspend(int botJobId); }

    @FunctionalInterface
    public interface SessionPort { void clear(String sessionId, int botJobId); }

    @FunctionalInterface
    public interface BrowserPort { void shutdown(); }

    @FunctionalInterface
    public interface ErrorPort { void browserCloseFailure(RuntimeException failure); }
}

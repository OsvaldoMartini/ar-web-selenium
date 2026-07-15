package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.ScannerWorkspaceSessions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class BotJobWorkspaceCloseCoordinatorTest {

    @Test
    void activeLocalOrExecutionWorkBlocksCloseWithoutCleanup() {
        AtomicBoolean busy = new AtomicBoolean(true);
        List<String> calls = new ArrayList<>();
        BotJobWorkspaceCloseCoordinator coordinator = coordinator(busy, true, calls);

        assertFalse(coordinator.canClose(42));
        assertThrows(IllegalStateException.class, () -> coordinator.close(42));
        busy.set(false);
        assertFalse(coordinator.canClose(42));
        assertTrue(calls.isEmpty());
    }

    @Test
    void closeRetiresSurfaceRegistrySessionsAndBrowserInOrder() {
        List<String> calls = new ArrayList<>();
        BotJobWorkspaceCloseCoordinator coordinator = coordinator(new AtomicBoolean(false), false, calls);

        coordinator.close(42);

        assertEquals(List.of(
                "suspend:42", "registry:42", "session:" + ScannerWorkspaceSessions.BOT_JOB_TASKS + ":42",
                "session:" + ScannerWorkspaceSessions.COMPONENT_TASKS + ":42",
                "session:" + ScannerWorkspaceSessions.PRE_SCANNER_GRID + ":42", "browser"), calls);
    }

    @Test
    void staleRegistryIsTreatedAsAlreadyClosed() {
        BotJobWorkspaceCloseCoordinator coordinator = new BotJobWorkspaceCloseCoordinator(
                () -> false,
                new BotJobWorkspaceCloseCoordinator.ExecutionPort() {
                    public boolean isActive(int botJobId) { throw new IllegalArgumentException("stale"); }
                    public void close(int botJobId) {}
                },
                id -> {}, (session, id) -> {}, () -> {}, failure -> {});

        assertTrue(coordinator.canClose(42));
    }

    @Test
    void surfaceAndBrowserFailuresStillRetireLogicalOwnership() {
        List<String> calls = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        BotJobWorkspaceCloseCoordinator coordinator = new BotJobWorkspaceCloseCoordinator(
                () -> false,
                new BotJobWorkspaceCloseCoordinator.ExecutionPort() {
                    public boolean isActive(int botJobId) { return false; }
                    public void close(int botJobId) { calls.add("registry"); }
                },
                id -> { throw new IllegalStateException("surface failed"); },
                (session, id) -> calls.add(session),
                () -> { throw new IllegalStateException("browser failed"); },
                failure -> errors.add(failure.getMessage()));

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> coordinator.close(42));

        assertEquals("surface failed", failure.getMessage());
        assertEquals(List.of(
                "registry",
                ScannerWorkspaceSessions.BOT_JOB_TASKS,
                ScannerWorkspaceSessions.COMPONENT_TASKS,
                ScannerWorkspaceSessions.PRE_SCANNER_GRID), calls);
        assertEquals(List.of("browser failed"), errors);
    }

    private static BotJobWorkspaceCloseCoordinator coordinator(
            AtomicBoolean busy, boolean executionActive, List<String> calls) {
        return new BotJobWorkspaceCloseCoordinator(
                busy::get,
                new BotJobWorkspaceCloseCoordinator.ExecutionPort() {
                    public boolean isActive(int botJobId) { return executionActive; }
                    public void close(int botJobId) { calls.add("registry:" + botJobId); }
                },
                id -> calls.add("suspend:" + id),
                (session, id) -> calls.add("session:" + session + ":" + id),
                () -> calls.add("browser"),
                failure -> calls.add("error:" + failure.getMessage()));
    }
}

package com.allinweb.ch.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.allinweb.ch.facade.execution.GridItemTestActionExecutor.Outcome;
import com.allinweb.ch.facade.execution.GridItemTestInstructionRepository.InstructionSnapshot;
import com.allinweb.ch.model.GridItemTestActionContracts.Action;
import com.allinweb.ch.model.GridItemTestActionContracts.Request;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.websocket.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GridItemTestActionServiceTest {
    @BeforeEach
    void clearSessions() {
        WebSocketSessionManager.clearSessions();
    }

    @AfterEach
    void cleanSessions() {
        WebSocketSessionManager.clearSessions();
    }

    @Test
    void acceptsOnlyTheCurrentPhysicalBotJobTasksTransport() {
        Session active = mock(Session.class);
        Session other = mock(Session.class);
        when(active.isOpen()).thenReturn(true);
        when(other.isOpen()).thenReturn(true);
        assertTrue(WebSocketSessionManager.addSession(
                ScannerWorkspaceSessions.BOT_JOB_TASKS, active));
        assertSame(active, WebSocketSessionManager.getSession(
                ScannerWorkspaceSessions.BOT_JOB_TASKS));

        GridItemTestActionService service = GridItemTestActionService.getInstance();
        assertTrue(service.authoritativeTransport(
                ScannerWorkspaceSessions.BOT_JOB_TASKS, active));
        assertFalse(service.authoritativeTransport("variablesManager", active));
        assertFalse(service.authoritativeTransport(
                ScannerWorkspaceSessions.BOT_JOB_TASKS, other));
    }

    @Test
    void permitsOnlyOnePhysicalActionAtATime() throws Exception {
        ThreadPoolExecutor worker = GridItemTestActionService.newWorker();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            worker.execute(() -> {
                entered.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertThrows(RejectedExecutionException.class, () -> worker.execute(() -> {}));
        } finally {
            release.countDown();
            worker.shutdownNow();
        }
    }

    @Test
    void permitsBothTestsForEveryPersistedWebElementAndRejectsCommands() {
        for (String storedAction : List.of(
                "I:Account", "O:Balance", "C", "A:Terms", "W:Custom")) {
            assertDoesNotThrow(() -> GridItemTestActionService.validateStoredAction(
                    instruction(storedAction), Action.INPUT));
            assertDoesNotThrow(() -> GridItemTestActionService.validateStoredAction(
                    instruction(storedAction), Action.CLICK));
        }

        for (String storedAction : List.of(
                "GET", "SET", "CK", "E", "IF", "NEXT ROW", "BACK", "UNKNOWN")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> GridItemTestActionService.validateStoredAction(
                            instruction(storedAction), Action.INPUT));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> GridItemTestActionService.validateStoredAction(
                            instruction(storedAction), Action.CLICK));
        }
    }

    @Test
    void responseCarriesCorrelationAndMetadataButNeverTheInputValue() {
        Request request = new Request(1, "request-1", 2, 29, 101, Action.INPUT, 0,
                4L, 7L, "a".repeat(64));
        Outcome outcome = new Outcome(
                true,
                "INPUT_COMPLETED",
                "completed",
                "EXCEL_MEMORY",
                "REAL",
                "Customer number",
                0,
                11L,
                12L);

        JsonObject response = GridItemTestActionService.getInstance().response(request, outcome);

        assertEquals("request-1", response.get("requestId").getAsString());
        assertEquals(101, response.get("instructionId").getAsInt());
        assertEquals("EXCEL_MEMORY", response.get("valueSource").getAsString());
        assertFalse(response.has("value"));
        assertFalse(response.has("inputValue"));
    }

    private static InstructionSnapshot instruction(String action) {
        return new InstructionSnapshot(
                2, 29, "Lloyds", "NORMAL", 10, 1, "Login", true, 0, "",
                101, 1, action, "Customer", "Customer number", "", "//input",
                "", "", "", "input", "", "", "#customer", "", null,
                false, false, 6, 0, false, false, true, null, null, List.of());
    }
}

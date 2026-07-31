package com.allinweb.ch.socket;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.allinweb.ch.facade.BotJobDetailsWorkspaceRegistry;
import com.allinweb.ch.model.BotJobDetailsRequest;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.util.Set;
import javax.websocket.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VariablesWorkspaceAuthorizationTest {
    private final SimpleWebSocketServer endpoint = new SimpleWebSocketServer();
    private final BotJobDetailsWorkspaceRegistry registry =
            BotJobDetailsWorkspaceRegistry.getInstance();

    @BeforeEach
    void setUp() {
        WebSocketSessionManager.clearSessions();
        BotJobLoadDTO job = new BotJobLoadDTO();
        job.setId(42);
        job.setName("Authorized Variables Job");
        job.setHomeBankingId(7);
        registry.activate(job, false);
    }

    @AfterEach
    void tearDown() {
        registry.close(42);
        WebSocketSessionManager.clearSessions();
    }

    @Test
    void acceptsOnlyExactAuthoritativeBotJobTransportAndActiveJob() {
        Session authoritative = openSession();
        Session forged = openSession();
        WebSocketSessionManager.addSession(
                ScannerWorkspaceSessions.BOT_JOB_TASKS, authoritative);
        WebSocketSessionManager.addSession(
                ScannerWorkspaceSessions.COMPONENT_TASKS, forged);
        BotJobDetailsRequest request = new BotJobDetailsRequest(
                ScannerWorkspaceSessions.BOT_JOB_TASKS,
                "variables-open",
                42,
                new JsonObject());

        assertDoesNotThrow(
                () -> endpoint.requireVariablesWorkspaceOpenAuthority(
                        request, authoritative));
        assertThrows(
                IllegalArgumentException.class,
                () -> endpoint.requireVariablesWorkspaceOpenAuthority(request, forged));

        BotJobDetailsRequest wrongJob = new BotJobDetailsRequest(
                ScannerWorkspaceSessions.BOT_JOB_TASKS,
                "variables-wrong-job",
                99,
                new JsonObject());
        assertThrows(
                IllegalArgumentException.class,
                () -> endpoint.requireVariablesWorkspaceOpenAuthority(
                        wrongJob, authoritative));
    }

    @Test
    @SuppressWarnings("unchecked")
    void detachedVariablesTransportAllowsOnlyItsExplicitWorkspaceRoutes()
            throws Exception {
        Field operationsField =
                SimpleWebSocketServer.class.getDeclaredField(
                        "DETACHED_VARIABLES_OPERATIONS");
        operationsField.setAccessible(true);

        assertEquals(
                Set.of(
                        "variablesWorkspace.bootstrap",
                        "variablesWorkspace.refresh",
                        "variablesWorkspace.runtimeMemory.update",
                        "variablesWorkspace.runtimeMemory.clearAll",
                        "variablesWorkspace.variables.create",
                        "variablesWorkspace.graphMutationV3",
                        "variablesWorkspace.instructions.copy",
                        "variablesWorkspace.variables.delete",
                        "pagesOpen.open",
                        "pagesOpen.summary"),
                (Set<String>) operationsField.get(null));
    }

    private Session openSession() {
        Session session = mock(Session.class);
        when(session.isOpen()).thenReturn(true);
        return session;
    }
}

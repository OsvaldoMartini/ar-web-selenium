package com.allinweb.ch.socket;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.allinweb.ch.db.InstructionGraphStateRepository.OwnerKey;
import com.allinweb.ch.facade.BotJobDetailsWorkspaceRegistry;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.CommitResult;
import com.allinweb.ch.facade.LicenseService;
import com.allinweb.ch.facade.ScannerBotJobTasksPublisher;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

class SimpleWebSocketServerBotJobGraphMutationPublicationTest {

    @AfterEach
    void clearSessions() {
        WebSocketSessionManager.clearSessions();
    }

    @Test
    void successfulGridGraphMutationPublishesGridBeforeNotifyingVariablesWorkspace() {
        Session transport = mock(Session.class);
        when(transport.isOpen()).thenReturn(true);
        when(transport.getRequestParameterMap()).thenReturn(Map.of(
                "sessionId", List.of(ScannerWorkspaceSessions.BOT_JOB_TASKS)));
        when(transport.getBasicRemote()).thenReturn(mock(RemoteEndpoint.Basic.class));
        WebSocketSessionManager.addSession(
                ScannerWorkspaceSessions.BOT_JOB_TASKS, transport);

        BotJobDetailsWorkspaceRegistry registry =
                mock(BotJobDetailsWorkspaceRegistry.class);
        BotJobDetailsWorkspaceRegistry.Snapshot active =
                new BotJobDetailsWorkspaceRegistry.Snapshot(
                        1L,
                        1L,
                        91L,
                        42,
                        "Job 42",
                        "",
                        "",
                        7,
                        "Bank",
                        3,
                        false,
                        true,
                        "TASKS",
                        false,
                        "IDLE",
                        0L);
        CommitResult committed = new CommitResult(
                OwnerKey.botJob(7, 42),
                91L,
                "mutation-1",
                10L,
                11L,
                "revision-11");
        when(registry.require(42)).thenReturn(active);
        when(registry.commitWorkspaceMutation(eq(42), eq(91L), any()))
                .thenReturn(committed);

        ScannerBotJobTasksPublisher gridPublisher =
                mock(ScannerBotJobTasksPublisher.class);
        VariablesWorkspaceService variables = mock(VariablesWorkspaceService.class);
        LicenseService license = mock(LicenseService.class);
        when(license.permits("BOT_JOB_GRAPH_MUTATION")).thenReturn(true);

        try (MockedStatic<BotJobDetailsWorkspaceRegistry> registries =
                        mockStatic(BotJobDetailsWorkspaceRegistry.class);
                MockedStatic<ScannerBotJobTasksPublisher> publishers =
                        mockStatic(ScannerBotJobTasksPublisher.class);
                MockedStatic<VariablesWorkspaceService> variableServices =
                        mockStatic(VariablesWorkspaceService.class);
                MockedStatic<LicenseService> licenses =
                        mockStatic(LicenseService.class)) {
            registries.when(BotJobDetailsWorkspaceRegistry::getInstance)
                    .thenReturn(registry);
            publishers.when(ScannerBotJobTasksPublisher::getInstance)
                    .thenReturn(gridPublisher);
            variableServices.when(VariablesWorkspaceService::getInstance)
                    .thenReturn(variables);
            licenses.when(LicenseService::getInstance).thenReturn(license);

            new SimpleWebSocketServer().onMessage(encodedMutation(), transport);

            InOrder publicationOrder = inOrder(gridPublisher, variables);
            publicationOrder.verify(gridPublisher).publishGridOnly(7, 42);
            publicationOrder.verify(variables).notifyMutation(42);
        }
    }

    private static String encodedMutation() {
        JsonObject owner = new JsonObject();
        owner.addProperty("workspaceKind", "BOT_JOB");
        owner.addProperty("homeBankingId", 7);
        owner.addProperty("botJobId", 42);

        JsonObject mutation = new JsonObject();
        mutation.addProperty("type", "BOT_JOB_GRAPH_MUTATION");
        mutation.addProperty("sessionId", ScannerWorkspaceSessions.BOT_JOB_TASKS);
        mutation.addProperty("homeBankingId", 7);
        mutation.addProperty("contractVersion", 3);
        mutation.addProperty("requestId", "mutation-1");
        mutation.addProperty("workspaceEpoch", 91L);
        mutation.add("ownerAssertion", owner);
        return Base64.getEncoder()
                .encodeToString(mutation.toString().getBytes(StandardCharsets.UTF_8));
    }
}

package com.allinweb.ch.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.allinweb.ch.facade.BotJobWorkspaceController;
import com.allinweb.ch.model.BotJobDetailsRequest;
import com.allinweb.ch.model.BotJobDetailsResponse;
import com.allinweb.ch.model.BotJobDetailsState;
import com.allinweb.ch.model.BotJobToolbarAction;
import com.allinweb.ch.model.BotJobToolbarActionResult;
import com.allinweb.ch.model.BotJobWorkspaceAction;
import com.allinweb.ch.model.BotJobWorkspaceActionResult;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.allinweb.ch.model.SplitDTO;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.websocket.RemoteEndpoint;
import javax.websocket.SendHandler;
import javax.websocket.SendResult;
import javax.websocket.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SimpleWebSocketServerBotJobBootstrapTest {

    private final BotJobWorkspaceController workspaceController = BotJobWorkspaceController.getInstance();
    private long workspaceGeneration;

    @AfterEach
    void deactivateFakeWorkspace() {
        if (workspaceGeneration > 0) {
            workspaceController.deactivate(workspaceGeneration);
        }
    }

    @Test
    void publishesInstructionSnapshotOnlyAfterCorrelatedBootstrapResponseIsAcknowledged() {
        List<String> events = new ArrayList<>();
        AtomicReference<String> publishedSessionId = new AtomicReference<>();
        AtomicInteger publishedBotJobId = new AtomicInteger();
        workspaceGeneration = workspaceController.activate(new BotJobWorkspaceController.HostPort() {
            @Override
            public CompletableFuture<BotJobWorkspaceActionResult> workspaceAction(
                    BotJobWorkspaceAction action, int botJobId) {
                throw new AssertionError("Unexpected workspace action");
            }

            @Override
            public CompletableFuture<BotJobToolbarActionResult> toolbarAction(
                    BotJobToolbarAction action, BotJobDetailsRequest request) {
                throw new AssertionError("Unexpected toolbar action");
            }

            @Override
            public CompletableFuture<Void> applyMetadata(BotJobDetailsState state) {
                throw new AssertionError("Unexpected metadata update");
            }

            @Override
            public void publishGridBootstrap(String sessionId, int botJobId) {
                publishedSessionId.set(sessionId);
                publishedBotJobId.set(botJobId);
                events.add(InstructionRealtimePublisher.getInstance().snapshotOperation(sessionId));
            }

            @Override
            public void preScanCommand(String type, JsonObject body) {
                throw new AssertionError("Unexpected Pre Scan command");
            }

            @Override
            public void preScanElementTest(SplitDTO payload, String type) {
                throw new AssertionError("Unexpected Pre Scan element test");
            }
        });

        Session transport = mock(Session.class);
        RemoteEndpoint.Async asyncRemote = mock(RemoteEndpoint.Async.class);
        AtomicReference<String> outboundFrame = new AtomicReference<>();
        AtomicReference<SendHandler> acknowledgement = new AtomicReference<>();
        when(transport.isOpen()).thenReturn(true);
        when(transport.getAsyncRemote()).thenReturn(asyncRemote);
        doAnswer(invocation -> {
                    outboundFrame.set(invocation.getArgument(0));
                    acknowledgement.set(invocation.getArgument(1));
                    events.add("botJobDetails.bootstrapResponse");
                    return null;
                })
                .when(asyncRemote)
                .sendText(anyString(), any(SendHandler.class));

        JsonObject requestBody = new JsonObject();
        BotJobDetailsRequest request = new BotJobDetailsRequest(
                ScannerWorkspaceSessions.BOT_JOB_TASKS,
                "bootstrap-request-42",
                42,
                requestBody);
        BotJobDetailsResponse response = BotJobDetailsResponse.success(
                "Bot Job Details loaded", request, null);

        new SimpleWebSocketServer().sendBotJobDetailsBootstrap(transport, request, response);

        assertEquals(List.of("botJobDetails.bootstrapResponse"), events);
        assertNull(publishedSessionId.get());
        assertEquals(0, publishedBotJobId.get());

        String serializedEnvelope = outboundFrame.get();
        assertNotNull(serializedEnvelope);
        JsonObject envelope = JsonParser.parseString(serializedEnvelope).getAsJsonObject();
        assertEquals("botJobDetails.bootstrapResponse", envelope.get("operationId").getAsString());
        assertEquals(ScannerWorkspaceSessions.BOT_JOB_TASKS, envelope.get("sessionId").getAsString());
        JsonObject body = JsonParser.parseString(envelope.get("body").getAsString()).getAsJsonObject();
        assertTrue(body.get("ok").getAsBoolean());
        assertEquals("bootstrap-request-42", body.get("requestId").getAsString());
        assertEquals(42, body.get("botJobId").getAsInt());

        SendHandler sendAcknowledgement = acknowledgement.get();
        assertNotNull(sendAcknowledgement);
        events.add("acknowledgement");
        sendAcknowledgement.onResult(new SendResult());

        assertEquals(
                List.of("botJobDetails.bootstrapResponse", "acknowledgement", "updateInstructions"),
                events);
        assertEquals(ScannerWorkspaceSessions.BOT_JOB_TASKS, publishedSessionId.get());
        assertEquals(42, publishedBotJobId.get());
    }
}

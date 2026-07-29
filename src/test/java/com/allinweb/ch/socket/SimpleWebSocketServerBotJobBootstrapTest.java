package com.allinweb.ch.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.allinweb.ch.facade.BotJobWorkspaceController;
import com.allinweb.ch.facade.execution.ExecutionPreflightReport;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private String registeredSessionId;
    private Session registeredTransport;

    @AfterEach
    void deactivateFakeWorkspace() {
        if (registeredSessionId != null && registeredTransport != null) {
            WebSocketSessionManager.removeSession(registeredSessionId, registeredTransport);
        }
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
        registerTransport(ScannerWorkspaceSessions.BOT_JOB_TASKS, transport);
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

    @Test
    void reportsCorrelatedResyncWhenPostAcknowledgementGridPublicationFails() {
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
            public CompletableFuture<Void> publishGridBootstrapAsync(
                    String sessionId, int botJobId) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("Component catalog refresh failed"));
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
        List<String> outboundFrames = new ArrayList<>();
        List<SendHandler> acknowledgements = new ArrayList<>();
        when(transport.isOpen()).thenReturn(true);
        when(transport.getAsyncRemote()).thenReturn(asyncRemote);
        registerTransport(ScannerWorkspaceSessions.COMPONENT_TASKS, transport);
        doAnswer(invocation -> {
                    outboundFrames.add(invocation.getArgument(0));
                    acknowledgements.add(invocation.getArgument(1));
                    return null;
                })
                .when(asyncRemote)
                .sendText(anyString(), any(SendHandler.class));

        BotJobDetailsRequest request = new BotJobDetailsRequest(
                ScannerWorkspaceSessions.COMPONENT_TASKS,
                "component-bootstrap-42",
                42,
                new JsonObject());
        BotJobDetailsResponse response = BotJobDetailsResponse.success(
                "Bot Job Details loaded", request, null);

        new SimpleWebSocketServer().sendBotJobDetailsBootstrap(transport, request, response);

        assertEquals(1, outboundFrames.size());
        assertEquals(
                "botJobDetails.bootstrapResponse",
                JsonParser.parseString(outboundFrames.get(0))
                        .getAsJsonObject()
                        .get("operationId")
                        .getAsString());

        acknowledgements.get(0).onResult(new SendResult());

        assertEquals(2, outboundFrames.size());
        JsonObject failureEnvelope =
                JsonParser.parseString(outboundFrames.get(1)).getAsJsonObject();
        assertEquals(
                "instructionEditor.resyncRequired",
                failureEnvelope.get("operationId").getAsString());
        assertEquals(
                ScannerWorkspaceSessions.COMPONENT_TASKS,
                failureEnvelope.get("sessionId").getAsString());
        JsonObject failure =
                JsonParser.parseString(failureEnvelope.get("body").getAsString())
                        .getAsJsonObject();
        assertFalse(failure.get("ok").getAsBoolean());
        assertTrue(failure.get("resyncRequired").getAsBoolean());
        assertEquals("component-bootstrap-42", failure.get("requestId").getAsString());
        assertEquals(42, failure.get("botJobId").getAsInt());
        assertTrue(failure.get("error").getAsString().contains("Components"));
        assertTrue(failure.get("action").getAsString().contains("Refresh"));

        acknowledgements.get(1).onResult(new SendResult());
    }

    @Test
    void includesAuthoritativeExecutionPreflightInToolbarResponsePayload() {
        ExecutionPreflightReport report = new ExecutionPreflightReport(
                "WARN",
                "WOULD_BLOCK",
                "BOT_JOB_DETAILS_TEST_RUN",
                new ExecutionPreflightReport.OwnerReport(2, 5),
                new ExecutionPreflightReport.RunScopeReport("ONE", 10),
                17L,
                "content-revision",
                List.of(10),
                List.of(91),
                1,
                List.of(new ExecutionPreflightReport.IssueReport(
                        "MISSING_LOOP_ANCHOR",
                        "LOOP_ANCHOR",
                        10,
                        91,
                        "Instruction 91 has no LOOP anchor")),
                null);
        BotJobToolbarActionResult result = BotJobToolbarActionResult
                .success(BotJobToolbarAction.TEST_RUN, "TEST RUN accepted")
                .withExecutionPreflight(report);
        Map<String, Object> response = new LinkedHashMap<>();

        SimpleWebSocketServer.addBotJobDetailsToolbarResult(response, result);

        JsonObject payload = JsonParser.parseString(
                        SimpleWebSocketServer.serializeBotJobDetailsResponse(response))
                .getAsJsonObject();
        assertTrue(payload.get("ok").getAsBoolean());
        JsonObject serializedReport = payload.getAsJsonObject("executionPreflight");
        assertEquals("WARN", serializedReport.get("enforcement").getAsString());
        assertEquals("WOULD_BLOCK", serializedReport.get("status").getAsString());
        assertEquals(2, serializedReport.getAsJsonObject("owner")
                .get("homeBankingId")
                .getAsInt());
        assertEquals("ONE", serializedReport.getAsJsonObject("runScope")
                .get("kind")
                .getAsString());
        assertEquals(1, serializedReport.get("totalIssues").getAsInt());
        assertEquals(
                91,
                serializedReport.getAsJsonArray("issues")
                        .get(0)
                        .getAsJsonObject()
                        .get("instructionId")
                        .getAsInt());
        assertTrue(serializedReport.get("unavailableReason").isJsonNull());
    }

    private void registerTransport(String sessionId, Session transport) {
        assertTrue(WebSocketSessionManager.addSession(sessionId, transport));
        registeredSessionId = sessionId;
        registeredTransport = transport;
    }
}

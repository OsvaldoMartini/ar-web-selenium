package com.allinweb.ch.socket;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.CompletionException;
import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import com.allinweb.ch.facade.BotJobTransferPathRegistry;
import com.allinweb.ch.facade.BotJobDetailsWorkspaceRegistry;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.google.gson.JsonObject;
import javax.websocket.CloseReason;
import javax.websocket.RemoteEndpoint;
import javax.websocket.SendHandler;
import javax.websocket.SendResult;
import javax.websocket.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SimpleWebSocketServerSessionLifecycleTest {

    @Test
    void pageScannerProtocolUsesAnExactInboundAllowlist() {
        assertTrue(SimpleWebSocketServer.isSupportedPageScannerOperation("pageScannerWorkspace.open"));
        assertTrue(SimpleWebSocketServer.isSupportedPageScannerOperation("pageScanner.scan"));
        assertTrue(SimpleWebSocketServer.isSupportedPageScannerOperation("pageScanner.locator.generate"));
        assertTrue(SimpleWebSocketServer.isSupportedPageScannerOperation("pageScanner.locator.apply"));
        assertTrue(SimpleWebSocketServer.isSupportedPageScannerOperation("pageScannerProfile.list"));
        assertTrue(SimpleWebSocketServer.isSupportedPageScannerOperation("pageScannerProfile.save"));
        assertTrue(SimpleWebSocketServer.isSupportedPageScannerOperation("pageScannerProfile.delete"));
        assertTrue(SimpleWebSocketServer.isSupportedPageScannerOperation("pageScanner.close"));
        assertFalse(SimpleWebSocketServer.isSupportedPageScannerOperation("pageScanner.deleteEverything"));
        assertFalse(SimpleWebSocketServer.isSupportedPageScannerOperation("pageScannerWorkspace.unknown"));
        assertFalse(SimpleWebSocketServer.isSupportedPageScannerOperation("locatorGenerator.generate"));
    }

    @Test
    void detachedPageScannerGuardAllowsOnlyTheBoundBotJobExecutionContract() {
        assertTrue(SimpleWebSocketServer.isAllowedFromDetachedPageScannerTransport("pageScannerProfile.list"));
        assertTrue(SimpleWebSocketServer.isAllowedFromDetachedPageScannerTransport("pageScannerProfile.save"));
        assertTrue(SimpleWebSocketServer.isAllowedFromDetachedPageScannerTransport("pageScannerProfile.delete"));
        assertTrue(SimpleWebSocketServer.isAllowedFromDetachedPageScannerTransport("pageScanner.scan"));
        assertTrue(SimpleWebSocketServer.isAllowedFromDetachedPageScannerTransport("pageScanner.locator.generate"));
        assertTrue(SimpleWebSocketServer.isAllowedFromDetachedPageScannerTransport("pageScanner.locator.apply"));
        assertTrue(SimpleWebSocketServer.isAllowedFromDetachedPageScannerTransport("ocrWorkspace.open"));
        assertTrue(SimpleWebSocketServer.isAllowedFromDetachedPageScannerTransport("botJobDetails.bootstrap"));
        assertTrue(SimpleWebSocketServer.isAllowedFromDetachedPageScannerTransport("botJobDetails.toolbar.action"));
        assertTrue(SimpleWebSocketServer.isAllowedFromDetachedPageScannerTransport("pagesOpen.open"));
        assertTrue(SimpleWebSocketServer.isAllowedFromDetachedPageScannerTransport("pagesOpen.summary"));

        assertTrue(SimpleWebSocketServer.isAllowedDetachedPageScannerToolbarAction("TEST_RUN"));
        assertTrue(SimpleWebSocketServer.isAllowedDetachedPageScannerToolbarAction("STOP_TEST_RUN"));
        assertTrue(SimpleWebSocketServer.isAllowedDetachedPageScannerToolbarAction("REFRESH_BLOCKS"));
        assertFalse(SimpleWebSocketServer.isAllowedDetachedPageScannerToolbarAction("LAUNCH"));
        assertFalse(SimpleWebSocketServer.isAllowedDetachedPageScannerToolbarAction("IMPORT_JOB"));

        assertFalse(SimpleWebSocketServer.isAllowedFromDetachedPageScannerTransport("broadcast"));
        assertFalse(SimpleWebSocketServer.isAllowedFromDetachedPageScannerTransport("DELETE_INSTRUCTION"));
        assertFalse(SimpleWebSocketServer.isAllowedFromDetachedPageScannerTransport("locatorGenerator.generate"));
        assertFalse(SimpleWebSocketServer.isAllowedFromDetachedPageScannerTransport("pagesOpen.bootstrap"));
        assertFalse(SimpleWebSocketServer.isAllowedFromDetachedPageScannerTransport("pagesOpen.closePage"));
        assertFalse(SimpleWebSocketServer.isAllowedFromDetachedPageScannerTransport("pagesOpen.focusPage"));
        assertFalse(SimpleWebSocketServer.isAllowedFromDetachedPageScannerTransport(null));
    }

    @Test
    void pauseResponseAcceptsOnlyTheExactBotJobDetailsTransport() {
        assertTrue(SimpleWebSocketServer.isBotJobExecutionPauseTransport("botJobTasks"));
        assertFalse(SimpleWebSocketServer.isBotJobExecutionPauseTransport("botJobTasks-stale"));
        assertFalse(SimpleWebSocketServer.isBotJobExecutionPauseTransport("componentTasks"));
        assertFalse(SimpleWebSocketServer.isBotJobExecutionPauseTransport(null));
    }

    @Test
    void componentTableRoutingAcceptsOnlyTheExactComponentWorkspaceName() {
        assertTrue(SimpleWebSocketServer.isComponentInstructionWorkspaceSession("componentTasks"));
        assertFalse(SimpleWebSocketServer.isComponentInstructionWorkspaceSession("x-componentTasks"));
        assertFalse(SimpleWebSocketServer.isComponentInstructionWorkspaceSession("componentTasks-stale"));
        assertFalse(SimpleWebSocketServer.isComponentInstructionWorkspaceSession(null));
    }

    @Test
    void componentTransportCannotRelabelAMutationAsBotJobDetails() {
        assertTrue(SimpleWebSocketServer.isComponentTransportRouteConsistent(
                "COMPONENT_ROW_MOVE", "componentTasks", "componentTasks", null));
        assertTrue(SimpleWebSocketServer.isComponentTransportRouteConsistent(
                "TEST_CLICK_DTO",
                "componentTasks",
                ScannerWorkspaceSessions.SCANNER_ELEMENT_PANE,
                "componentTasks"));

        assertFalse(SimpleWebSocketServer.isComponentTransportRouteConsistent(
                "ROW_MOVE", "componentTasks", "botJobTasks", null));
        assertFalse(SimpleWebSocketServer.isComponentTransportRouteConsistent(
                "ROW_MOVE", "componentTasks", "botJobTasks", "componentTasks"));
        assertFalse(SimpleWebSocketServer.isComponentTransportRouteConsistent(
                "TEST_CLICK_DTO", "componentTasks", "botJobTasks", "componentTasks"));
        assertFalse(SimpleWebSocketServer.isComponentTransportRouteConsistent(
                "TEST_INPUT_DTO", "componentTasks", "botJobTasks", "componentTasks"));
        assertFalse(SimpleWebSocketServer.isComponentTransportRouteConsistent(
                "HOVERED_ROW", "componentTasks", "botJobTasks", "componentTasks"));
        assertFalse(SimpleWebSocketServer.isComponentTransportRouteConsistent(
                "COMPONENT_ROW_MOVE", "botJobTasks", "componentTasks", null));
    }

    @Test
    void botJobGridMutationsRequireTheExactPhysicalBotJobTransport() {
        assertTrue(SimpleWebSocketServer.isBotJobGridMutationTransportConsistent(
                "ROW_MOVE", "botJobTasks", "botJobTasks"));
        assertTrue(SimpleWebSocketServer.isBotJobGridMutationTransportConsistent(
                "TEST_CLICK_DTO", "scannerTool", "botJobTasks"));

        assertFalse(SimpleWebSocketServer.isBotJobGridMutationTransportConsistent(
                "ROW_MOVE", "scannerTool", "botJobTasks"));
        assertFalse(SimpleWebSocketServer.isBotJobGridMutationTransportConsistent(
                "DELETE_INSTRUCTION", "botJobTasks", "x-botJobTasks"));
        assertFalse(SimpleWebSocketServer.isBotJobGridMutationTransportConsistent(
                "CREATE_BLOCK", "componentTasks", "botJobTasks"));
        assertFalse(SimpleWebSocketServer.isBotJobGridMutationTransportConsistent(
                "BLOCKS_SPLITTER", "scannerTool", "unknown"));
    }

    @Test
    void componentCommandRequestsAreCanonicalizedToTheActiveOrganization() throws Exception {
        BotJobLoadDTO job = new BotJobLoadDTO();
        job.setId(42);
        job.setName("Active Job");
        job.setHomeBankingId(7);
        BotJobDetailsWorkspaceRegistry registry =
                BotJobDetailsWorkspaceRegistry.getInstance();
        registry.activate(job, false);
        Session component =
                sessionWithId(ScannerWorkspaceSessions.COMPONENT_TASKS, true);
        endpoint.onOpen(component);
        try {
            JsonObject request = new JsonObject();
            request.addProperty(
                    "targetSessionId",
                    ScannerWorkspaceSessions.COMPONENT_TASKS);
            request.addProperty("homeBankingId", 7);
            request.addProperty("botJobId", 42);

            JsonObject canonical = endpoint.authorizeInstructionGridRequest(
                    request,
                    ScannerWorkspaceSessions.COMPONENT_TASKS,
                    component);
            assertEquals(7, canonical.get("homeBankingId").getAsInt());
            assertEquals(42, canonical.get("botJobId").getAsInt());
            assertEquals("Active Job", canonical.get("botJobName").getAsString());

            request.addProperty("homeBankingId", 999);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> endpoint.authorizeInstructionGridRequest(
                            request,
                            ScannerWorkspaceSessions.COMPONENT_TASKS,
                            component));
        } finally {
            registry.close(42);
        }
    }

    @Test
    void pageScannerFailureCorrelationCopiesOnlyAPositiveBotJobIdentity() {
        JsonObject request = new JsonObject();
        request.addProperty("botJobId", 42);
        JsonObject response = new JsonObject();

        SimpleWebSocketServer.copyPositivePageScannerBotJobId(request, response);

        org.junit.jupiter.api.Assertions.assertEquals(42, response.get("botJobId").getAsInt());

        request.addProperty("botJobId", "not-a-job");
        JsonObject malformedResponse = new JsonObject();
        SimpleWebSocketServer.copyPositivePageScannerBotJobId(request, malformedResponse);
        org.junit.jupiter.api.Assertions.assertFalse(malformedResponse.has("botJobId"));
    }

    @TempDir
    Path temporaryDirectory;

    private final SimpleWebSocketServer endpoint = new SimpleWebSocketServer();

    @BeforeEach
    void startWithEmptyRegistry() {
        WebSocketSessionManager.clearSessions();
    }

    @AfterEach
    void clearRegistry() {
        WebSocketSessionManager.clearSessions();
    }

    @Test
    void botJobWorkspaceReloadTakesOverThePreviousLiveConnection() throws Exception {
        Session original = sessionWithId(ScannerWorkspaceSessions.BOT_JOB_TASKS, true);
        Session replacement = sessionWithId(ScannerWorkspaceSessions.BOT_JOB_TASKS, true);

        endpoint.onOpen(original);
        endpoint.onOpen(replacement);

        assertSame(replacement, WebSocketSessionManager.getSession(ScannerWorkspaceSessions.BOT_JOB_TASKS));
        verify(original)
                .close(argThat(reason -> CloseReason.CloseCodes.NORMAL_CLOSURE.equals(reason.getCloseCode())));
        verify(replacement, never()).close(any(CloseReason.class));
    }

    @Test
    void onlyTheCurrentlyRegisteredComponentTransportIsAuthoritative() throws Exception {
        Session component = sessionWithId(ScannerWorkspaceSessions.COMPONENT_TASKS, true);
        Session other = sessionWithId(ScannerWorkspaceSessions.BOT_JOB_TASKS, true);
        endpoint.onOpen(component);
        endpoint.onOpen(other);

        assertTrue(endpoint.isAuthoritativeComponentTransport(component));
        assertFalse(endpoint.isAuthoritativeComponentTransport(other));
    }

    @Test
    void onlyTheCurrentlyRegisteredBotJobTransportIsAuthoritative() throws Exception {
        Session botJob = sessionWithId(ScannerWorkspaceSessions.BOT_JOB_TASKS, true);
        Session other = sessionWithId(ScannerWorkspaceSessions.COMPONENT_TASKS, true);
        endpoint.onOpen(botJob);
        endpoint.onOpen(other);

        assertTrue(endpoint.isAuthoritativeBotJobTransport(botJob));
        assertFalse(endpoint.isAuthoritativeBotJobTransport(other));
    }

    @Test
    void physicalFailureResponseCannotBeRedirectedToAClaimedVictimSession() {
        Session requester = sessionWithId(ScannerWorkspaceSessions.SCANNER_TOOL, true);
        Session victim = sessionWithId(ScannerWorkspaceSessions.BOT_JOB_TASKS, true);
        RemoteEndpoint.Async requesterRemote = mock(RemoteEndpoint.Async.class);
        RemoteEndpoint.Async victimRemote = mock(RemoteEndpoint.Async.class);
        when(requester.getAsyncRemote()).thenReturn(requesterRemote);
        when(victim.getAsyncRemote()).thenReturn(victimRemote);
        String[] sentPayload = new String[1];
        doAnswer(invocation -> {
                    sentPayload[0] = invocation.getArgument(0);
                    SendHandler handler = invocation.getArgument(1);
                    handler.onResult(new SendResult());
                    return null;
                })
                .when(requesterRemote)
                .sendText(anyString(), any(SendHandler.class));
        WebSocketSessionManager.addSession(ScannerWorkspaceSessions.SCANNER_TOOL, requester);
        WebSocketSessionManager.addSession(ScannerWorkspaceSessions.BOT_JOB_TASKS, victim);

        endpoint.sendPhysicalTransportResponseAcknowledged(
                        requester,
                        7,
                        "instructionEditor.rowMoveResponse",
                        Map.of("ok", false, "requestId", "request-1"))
                .join();

        assertTrue(sentPayload[0].contains(
                "\"sessionId\":\"" + ScannerWorkspaceSessions.SCANNER_TOOL + "\""));
        verify(requesterRemote).sendText(anyString(), any(SendHandler.class));
        verify(victimRemote, never()).sendText(anyString(), any(SendHandler.class));
    }

    @Test
    void primaryApplicationControlReloadTakesOverWithoutCreatingADuplicateOwner() throws Exception {
        Session original = sessionWithId(MainApplicationControlLifecycle.SESSION_ID, true);
        Session replacement = sessionWithId(MainApplicationControlLifecycle.SESSION_ID, true);

        endpoint.onOpen(original);
        endpoint.onOpen(replacement);

        assertSame(
                replacement,
                WebSocketSessionManager.getSession(MainApplicationControlLifecycle.SESSION_ID));
        verify(original)
                .close(argThat(reason -> CloseReason.CloseCodes.NORMAL_CLOSURE.equals(reason.getCloseCode())));
        verify(replacement, never()).close(any(CloseReason.class));
    }

    @Test
    void rejectsConnectionWithoutSessionId() throws Exception {
        Session session = sessionWithParameters(Collections.emptyMap(), true);

        endpoint.onOpen(session);

        assertNull(WebSocketSessionManager.getSession(""));
        verify(session)
                .close(argThat(reason -> CloseReason.CloseCodes.VIOLATED_POLICY.equals(reason.getCloseCode())));
    }

    @Test
    void staleCloseCannotRemoveReplacementConnectionOrPreserveItsTransferGrant() throws Exception {
        Session original = sessionWithId(ScannerWorkspaceSessions.SCANNER_GRID, false);
        Session replacement = sessionWithId(ScannerWorkspaceSessions.SCANNER_GRID, true);
        endpoint.onOpen(original);
        Path selected = Files.createDirectory(temporaryDirectory.resolve("stale-exports"));
        BotJobTransferPathRegistry paths = BotJobTransferPathRegistry.getInstance();
        paths.select(ScannerWorkspaceSessions.SCANNER_GRID, 42, selected.toFile());
        endpoint.onOpen(replacement);

        assertThrows(
                IllegalStateException.class,
                () -> paths.require(ScannerWorkspaceSessions.SCANNER_GRID, 42, selected.toString()));

        endpoint.onClose(
                original, new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "old transport closed"));

        assertSame(replacement, WebSocketSessionManager.getSession(ScannerWorkspaceSessions.SCANNER_GRID));
    }

    @Test
    void detachedOcrWorkspaceReloadTakesOverTheExactLogicalSession() throws Exception {
        String logicalSession = "ocr-config-reload-safe-session";
        Session original = sessionWithId(logicalSession, true);
        Session replacement = sessionWithId(logicalSession, true);

        endpoint.onOpen(original);
        endpoint.onOpen(replacement);

        assertSame(replacement, WebSocketSessionManager.getSession(logicalSession));
        verify(original)
                .close(argThat(reason -> CloseReason.CloseCodes.NORMAL_CLOSURE.equals(reason.getCloseCode())));
        verify(replacement, never()).close(any(CloseReason.class));
    }

    @Test
    void detachedPageScannerReloadTakesOverOnlyItsExactLogicalSession() throws Exception {
        String logicalSession = "page-scanner-9cb0468e-4822-4d7a-91a8-f314c57f5ad4";
        Session original = sessionWithId(logicalSession, true);
        Session replacement = sessionWithId(logicalSession, true);

        endpoint.onOpen(original);
        endpoint.onOpen(replacement);

        assertSame(replacement, WebSocketSessionManager.getSession(logicalSession));
        verify(original)
                .close(argThat(reason -> CloseReason.CloseCodes.NORMAL_CLOSURE.equals(reason.getCloseCode())));
        verify(replacement, never()).close(any(CloseReason.class));
    }

    @Test
    void errorClosesAndRemovesExactRegisteredTransport() throws Exception {
        Session session = sessionWithId("mainDashboard", true);
        when(session.getId()).thenReturn("transport-1");
        endpoint.onOpen(session);

        endpoint.onError(session, new IllegalStateException("boom"));

        verify(session).close();
        assertNull(WebSocketSessionManager.getSession("mainDashboard"));
    }

    @Test
    void exactSocketCloseRevokesEveryTransferFolderGrantForTheLogicalSession() throws Exception {
        String logicalSession = ScannerWorkspaceSessions.BOT_JOB_TASKS;
        Session session = sessionWithId(logicalSession, true);
        Path selected = Files.createDirectory(temporaryDirectory.resolve("exports"));
        BotJobTransferPathRegistry paths = BotJobTransferPathRegistry.getInstance();
        endpoint.onOpen(session);
        paths.select(logicalSession, 42, selected.toFile());

        endpoint.onClose(
                session, new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "done"));

        assertThrows(
                IllegalStateException.class,
                () -> paths.require(logicalSession, 42, selected.toString()));
    }

    @Test
    void acknowledgedBotJobStateSendPropagatesAsynchronousTransportFailure() {
        Session session = sessionWithId(ScannerWorkspaceSessions.BOT_JOB_TASKS, true);
        RemoteEndpoint.Async asyncRemote = mock(RemoteEndpoint.Async.class);
        when(session.getAsyncRemote()).thenReturn(asyncRemote);
        WebSocketSessionManager.addSession(ScannerWorkspaceSessions.BOT_JOB_TASKS, session);
        doAnswer(invocation -> {
                    SendHandler handler = invocation.getArgument(1);
                    handler.onResult(new SendResult(new IOException("transport failed")));
                    return null;
                })
                .when(asyncRemote)
                .sendText(anyString(), any(SendHandler.class));

        CompletionException failure = assertThrows(
                CompletionException.class,
                () -> endpoint.sendBotJobDetailsResponseAcknowledged(
                                session,
                                7,
                                ScannerWorkspaceSessions.BOT_JOB_TASKS,
                                Map.of("state", "PASSED"),
                                "botJobDetails.state")
                        .join());

        assertSame(IOException.class, failure.getCause().getClass());
    }

    @Test
    void acknowledgedBotJobStateSendCompletesOnlyAfterTransportAcknowledgement() {
        Session session = sessionWithId(ScannerWorkspaceSessions.BOT_JOB_TASKS, true);
        RemoteEndpoint.Async asyncRemote = mock(RemoteEndpoint.Async.class);
        when(session.getAsyncRemote()).thenReturn(asyncRemote);
        WebSocketSessionManager.addSession(ScannerWorkspaceSessions.BOT_JOB_TASKS, session);
        doAnswer(invocation -> {
                    SendHandler handler = invocation.getArgument(1);
                    handler.onResult(new SendResult());
                    return null;
                })
                .when(asyncRemote)
                .sendText(anyString(), any(SendHandler.class));

        endpoint.sendBotJobDetailsResponseAcknowledged(
                        session,
                        7,
                        ScannerWorkspaceSessions.BOT_JOB_TASKS,
                        Map.of("state", "PASSED"),
                        "botJobDetails.state")
                .join();

        verify(asyncRemote).sendText(anyString(), any(SendHandler.class));
    }

    @Test
    void scannerParseFailureResponseIncludesContractErrorAndCorrelation() {
        JsonObject envelope = new JsonObject();
        JsonObject body = new JsonObject();
        body.addProperty("requestId", "scanner-request-1");
        body.addProperty("botJobId", 42);
        body.addProperty("action", "page_scanner");
        envelope.add("body", body);

        Map<String, Object> response = endpoint.scannerParseFailureResponse(envelope, "Scanner action is invalid");

        org.junit.jupiter.api.Assertions.assertEquals("scanner-request-1", response.get("requestId"));
        org.junit.jupiter.api.Assertions.assertEquals(42, response.get("botJobId"));
        org.junit.jupiter.api.Assertions.assertEquals("PAGE_SCANNER", response.get("action"));
        org.junit.jupiter.api.Assertions.assertEquals(false, response.get("ok"));
        org.junit.jupiter.api.Assertions.assertEquals("Scanner action is invalid", response.get("message"));
        org.junit.jupiter.api.Assertions.assertEquals("INVALID_SCANNER_REQUEST", response.get("errorCode"));
    }

    @Test
    void scannerParseFailureResponseUsesStableFallbacksForMalformedBody() {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("body", "{not-json");

        Map<String, Object> response = endpoint.scannerParseFailureResponse(envelope, "");

        org.junit.jupiter.api.Assertions.assertEquals(-1, response.get("botJobId"));
        org.junit.jupiter.api.Assertions.assertEquals(false, response.get("ok"));
        org.junit.jupiter.api.Assertions.assertEquals("Invalid Scanner request", response.get("message"));
        org.junit.jupiter.api.Assertions.assertEquals("INVALID_SCANNER_REQUEST", response.get("errorCode"));
        org.junit.jupiter.api.Assertions.assertFalse(response.containsKey("action"));
    }

    private Session sessionWithId(String sessionId, boolean open) {
        return sessionWithParameters(Map.of("sessionId", List.of(sessionId)), open);
    }

    private Session sessionWithParameters(Map<String, List<String>> parameters, boolean open) {
        Session session = mock(Session.class);
        when(session.getRequestParameterMap()).thenReturn(parameters);
        when(session.isOpen()).thenReturn(open);
        return session;
    }
}

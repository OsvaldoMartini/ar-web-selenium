package com.allinweb.ch.socket;

import com.allinweb.ch.component.pane.BotJobDetailsWorkspaceHost;
import com.allinweb.ch.component.pane.ScannerPluginDownloadCommandService;
import com.allinweb.ch.facade.*;
import com.allinweb.ch.model.*;
import com.allinweb.ch.util.*;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;

// Simple WebSocket server endpoint (for demonstration)
@Slf4j
@ServerEndpoint("/websocket")
public class SimpleWebSocketServer {

    private static final ARPropertyManager arPropertyManager = ARPropertyManager.getInstance();
    private static final PerformLists performLists = PerformLists.getInstance();
    private static final PerformDBEngine performDBEngine = PerformDBEngine.getInstance();
    private static final PerformDataBase performDataBase = PerformDataBase.getInstance();
    private static final PerformMessage performMessage = PerformMessage.getInstance();
    private static final PerformActions performActions = PerformActions.getInstance();
    private static final OrganizationManagerService organizationManagerService =
            OrganizationManagerService.getInstance();
    private static final MainDashboardService mainDashboardService = MainDashboardService.getInstance();
    private static final AutomationTestCatalogService automationTestCatalogService =
            AutomationTestCatalogService.getInstance();
    private static final NewBotJobService newBotJobService = NewBotJobService.getInstance();
    private static final CloneJobService cloneJobService = CloneJobService.getInstance();
    private static final ConfigService configService = ConfigService.getInstance();
    private static final BotJobDetailsService botJobDetailsService = BotJobDetailsService.getInstance();
    private static final ExecutionPauseCoordinator executionPauseCoordinator =
            ExecutionPauseCoordinator.getInstance();
    private static final ScannerWorkspaceService scannerWorkspaceService = ScannerWorkspaceService.getInstance();
    private static final BotJobDetailsActionLedger botJobDetailsActionLedger =
            new BotJobDetailsActionLedger();
    private static final BotJobDetailsToolbarLedger botJobDetailsToolbarLedger =
            new BotJobDetailsToolbarLedger();
    private static final BotJobDetailsMutationLedger botJobDetailsMutationLedger =
            new BotJobDetailsMutationLedger();
    private static final ScannerWorkspaceRequestLedger scannerWorkspaceRequestLedger =
            new ScannerWorkspaceRequestLedger();
    private static final InstructionRealtimePublisher instructionRealtimePublisher =
            InstructionRealtimePublisher.getInstance();
    private static final ExcelExportService excelExportService = ExcelExportService.getInstance();
    private static final SaveComponentService saveComponentService = SaveComponentService.getInstance();
    private static final OcrManagerService ocrManagerService = OcrManagerService.getInstance();
    private static final OcrTestService ocrTestService = OcrTestService.getInstance();
    private static final OcrWorkspaceCoordinator ocrWorkspaceCoordinator =
            OcrWorkspaceCoordinator.getInstance();
    private static final PageScannerWorkspaceCoordinator pageScannerWorkspaceCoordinator =
            PageScannerWorkspaceCoordinator.getInstance();
    private static final PageScannerMutationLedger pageScannerMutationLedger =
            PageScannerMutationLedger.getInstance();
    private static final PageScannerProfileService pageScannerProfileService =
            PageScannerProfileService.getInstance();
    private static final MemoryListWorkspaceService memoryListWorkspaceService =
            MemoryListWorkspaceService.getInstance();
    private static final PagesOpenWorkspaceService pagesOpenWorkspaceService =
            PagesOpenWorkspaceService.getInstance();
    private static final CommandEditorWorkspaceService commandEditorWorkspaceService =
            CommandEditorWorkspaceService.getInstance();
    private static final int MAX_PAGE_SCANNER_BODY_CHARACTERS = 2_000_000;
    private static final int MAX_PAGE_SCANNER_ELEMENTS = 1_000;
    private static final int MAX_PAGE_SCANNER_SEARCH_TERMS = 8_192;
    private static final int MAX_PAGE_SCANNER_BLOCK_NAME = 256;
    private static final Set<String> PAGE_SCANNER_OPERATIONS = Set.of(
            "pageScannerWorkspace.open",
            "pageScannerWorkspace.bootstrap",
            "pageScanner.scan",
            "pageScanner.refresh",
            "pageScanner.clear",
            "pageScanner.testElement",
            "pageScanner.apply",
            "pageScanner.locator.generate",
            "pageScanner.locator.apply",
            "pageScanner.createBlock",
            "pageScannerProfile.list",
            "pageScannerProfile.save",
            "pageScannerProfile.delete",
            "pageScanner.close");
    private static final Set<String> DETACHED_PAGE_SCANNER_BOT_JOB_OPERATIONS = Set.of(
            "botJobDetails.bootstrap",
            "botJobDetails.toolbar.action");
    private static final Set<String> DETACHED_PAGE_SCANNER_TOOLBAR_ACTIONS = Set.of(
            "REFRESH_BLOCKS",
            "TEST_RUN",
            "STOP_TEST_RUN");
    private static final Set<String> DETACHED_COMMAND_EDITOR_OPERATIONS = Set.of(
            "commandEditor.workspaceBootstrap",
            "commandEditor.bootstrap",
            "commandEditor.select",
            "commandEditor.apply",
            "commandEditor.insertElseIf",
            "instructionGraph.previewSplit",
            "instructionGraph.previewMove",
            "instructionEditor.memoryCapabilities",
            "variableEditor.bootstrap",
            "variableEditor.save",
            "variableEditor.delete",
            "pagesOpen.open",
            "pagesOpen.summary");
    private static final ScannerPluginDownloadCommandService scannerPluginDownloadCommandService =
            ScannerPluginDownloadCommandService.getInstance();
    protected static volatile SimpleWebSocketServer instance;
    private static WebSocketSessionManager webSocketSessionManager = WebSocketSessionManager.getInstance();
    private static ActionExecutorClient actionExecutorClient = ActionExecutorClient.getInstance();
    private static final Map<String, Boolean> processedInstructionDeletes = new LinkedHashMap<>();
    private static final Map<String, Boolean> processedBlockDeletes = new LinkedHashMap<>();
    // ROW_MOVE idempotency + validation now live in facade.RowMoveService (one method per concern).
    // Dedicated backend request-traffic logger → ar_web_scanner_backend.log (see logback.xml)
    private static final org.slf4j.Logger logBackend = org.slf4j.LoggerFactory.getLogger("com.allinweb.backend");
    private final Gson gson = new Gson();
    private final BotJobWorkspaceCapabilityService botJobWorkspaceCapabilityService =
            BotJobWorkspaceCapabilityService.getInstance();
    private final ScannerMobileTestRoute scannerMobileTestRoute = ScannerMobileTestRoute.standard();
    private final ScannerMobilePickRoute scannerMobilePickRoute = ScannerMobilePickRoute.standard();
    private final ScannerBlockUpdatePublisher scannerBlockUpdatePublisher = new ScannerBlockUpdatePublisher();
    private final ScannerElementPanePublisher scannerElementPanePublisher = new ScannerElementPanePublisher();
    private static final ScannerWorkspaceSessionClassifier scannerWorkspaceSessionClassifier =
            new ScannerWorkspaceSessionClassifier();
    private PayloadJson payloadEmpty;
    private RowStatus rowStatus = new RowStatus();
    // Private constructor to prevent instantiation
    public SimpleWebSocketServer() {}

    private static boolean sessionIdContains(String sessionId, String expectedSessionId) {
        return sessionId != null && sessionId.contains(expectedSessionId);
    }

    private static boolean isBotJobTasksSession(String sessionId) {
        return sessionIdContains(sessionId, ScannerWorkspaceSessions.BOT_JOB_TASKS);
    }

    static boolean isBotJobExecutionPauseTransport(String sessionId) {
        return ScannerWorkspaceSessions.BOT_JOB_TASKS.equals(sessionId);
    }

    private static boolean isComponentInstructionWorkspaceSession(String sessionId) {
        return sessionIdContains(sessionId, ScannerWorkspaceSessions.COMPONENT_TASKS);
    }

    private static boolean isBotJobInstructionWorkspaceSession(String sessionId) {
        return scannerWorkspaceSessionClassifier.isInstructionWorkspaceSession(sessionId);
    }

    private static boolean isScannerGridSession(String sessionId) {
        return scannerWorkspaceSessionClassifier.isScannerGridSession(sessionId);
    }

    private static boolean isScannerToolSession(String sessionId) {
        return scannerWorkspaceSessionClassifier.isScannerToolSession(sessionId);
    }

    private static boolean isScannerElementPaneSession(String sessionId) {
        return scannerWorkspaceSessionClassifier.isScannerElementPaneSession(sessionId);
    }

    private static boolean isPerformListDataSession(String sessionId) {
        return ScannerWorkspaceSessions.PERFORM_LIST_DATA.equals(sessionId);
    }

    private boolean isScannerElementPaneOpen() {
        return WebSocketSessionManager.isSessionOpen(scannerElementPanePublisher.destinationSessionId());
    }

    public static SimpleWebSocketServer getInstance() {
        if (instance == null) {
            synchronized (SimpleWebSocketServer.class) {
                if (instance == null) {
                    instance = new SimpleWebSocketServer();
                }
            }
        }
        return instance;
    }

    @OnOpen
    public void onOpen(Session session) {
        String sessionId = null;
        try {
            List<String> values = session.getRequestParameterMap().get("sessionId");
            if (values != null && !values.isEmpty()) {
                sessionId = values.get(0);
            }
        } catch (RuntimeException ignored) {
            // Rejected below with the same generic policy response.
        }

        if (Strings.isNullOrEmpty(sessionId)) {
            log.warn("Rejected WebSocket connection without a session ID");
            closeRejectedSession(session, "Session ID is required");
            return;
        }

        boolean botJobWindowControl = BotJobDetailsWindowCoordinator.isControlSessionId(sessionId);
        boolean mainApplicationControl = MainApplicationControlLifecycle.isControlSessionId(sessionId);
        if (botJobWindowControl
                && !BotJobDetailsWindowCoordinator.getInstance().isActiveControlSession(sessionId)) {
            log.warn("Rejected unknown Bot Job Details window control session: {}", sessionId);
            closeRejectedSession(session, "Bot Job Details window session is not active");
            return;
        }

        if (ScannerWorkspaceSessions.BOT_JOB_TASKS.equals(sessionId)) {
            Session previousSource = WebSocketSessionManager.getSession(sessionId);
            if (previousSource != null && previousSource != session) {
                commandEditorWorkspaceService.disconnected(sessionId, previousSource);
            }
        }

        if (ScannerWorkspaceSessions.BOT_JOB_TASKS.equals(sessionId)
                || mainApplicationControl
                || botJobWindowControl
                || OcrWorkspaceCoordinator.isWorkspaceSessionId(sessionId)
                || ScannerWorkspaceSessions.isPageScannerSession(sessionId)
                || CommandEditorWorkspaceService.isWorkspaceSession(sessionId)) {
            // Only one Bot Job workspace is active in the backend at a time -- opening a job in a
            // new tab takes over from whichever tab had it before, rather than being rejected as a
            // duplicate session. Detached OCR pages use the same exact-session takeover so a page
            // reload can reconnect without losing its backend-owned workspace context. The fixed
            // Command Editor likewise preserves its binding while replacing a reloaded transport.
            WebSocketSessionManager.takeOverSession(sessionId, session);
        } else if (!webSocketSessionManager.addSession(sessionId, session)) {
            log.warn("Rejected duplicate live WebSocket session: {}", sessionId);
            closeRejectedSession(session, "Session is already connected");
            return;
        }

        BotJobTransferPathRegistry.getInstance().clearSession(sessionId);
        log.info("New connection: Session ID = {}", sessionId);

        if (mainApplicationControl) {
            MainApplicationControlLifecycle.getInstance().connected(sessionId);
        }

        if (botJobWindowControl) {
            try {
                if (!BotJobDetailsWindowCoordinator.getInstance().connected(sessionId)) {
                    log.warn("Bot Job Details window target could not be delivered to {}", sessionId);
                }
            } catch (IllegalArgumentException staleControlSession) {
                closeRejectedSession(session, "Bot Job Details window session is no longer active");
                return;
            }
        }

        if (CommandEditorWorkspaceService.isWorkspaceSession(sessionId)) {
            commandEditorWorkspaceService.connected(sessionId, session);
        }

        if ("mainDashboardBootstrap".equals(sessionId)) {
            // Transient handshake session the React shell opens once on load, before it knows
            // which real session ("mainDashboard", a bot job tab, etc.) it should become. Reply
            // directly on this connection rather than via the session registry -- nothing is
            // registered under "mainDashboard" yet, so a registry-keyed send would find no
            // target. The shell closes this socket once it has read the reply.
            sendBootstrapReactSessionOpen(session);
        }
        pagesOpenWorkspaceService.sessionRegistryChanged();
    }

    private void sendBootstrapReactSessionOpen(Session session) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("targetSession", "mainDashboard");
            payload.put("port", resolveBootstrapSocketPort());
            payload.put("botJobId", -9999);
            payload.put("source", "mainDashboard");

            JsonObject envelope = new JsonObject();
            envelope.addProperty("body", gson.toJson(payload));
            envelope.addProperty("sessionId", "mainDashboard");
            envelope.addProperty("homeBankingId", -1);
            envelope.addProperty("operationId", "react.session.open");
            WebSocketSessionManager.sendText(session, envelope.toString());
        } catch (IOException e) {
            log.warn("Failed to send bootstrap react.session.open: {}", e.getMessage());
        }
    }

    private int resolveBootstrapSocketPort() {
        try {
            String port = System.getProperty("ARWebChosenPort");
            if (Strings.isNullOrEmpty(port)) {
                port = arPropertyManager.getProperty(ARPropertyEnum.PORT_SOCKET);
            }
            if (!Strings.isNullOrEmpty(port)) {
                return Integer.parseInt(port);
            }
        } catch (Exception e) {
            log.warn("Invalid PORT_SOCKET during bootstrap: {}", e.getMessage());
        }
        return 54525;
    }

    private void closeRejectedSession(Session session, String reason) {
        try {
            if (session != null && session.isOpen()) {
                session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, reason));
            }
        } catch (IOException error) {
            log.debug("Could not close rejected WebSocket session: {}", error.getMessage());
        }
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        if (Strings.isNullOrEmpty(transportSessionId(session))) {
            closeRejectedSession(session, "Session is not registered");
            return;
        }
        if (message == null || message.contains("CONNECT") || message.contains("ping")) return;

        try {
            // Decode from Base64
            byte[] decodedBytes = Base64.getDecoder().decode(message);
            message = new String(decodedBytes, "UTF-8");

            //            log.info("Decoded Received Data: " + message);

            // Process the message as needed...
        } catch (IllegalArgumentException e) {
            //            log.error("Invalid Base64 message received: " + message);
        } catch (Exception e) {
            //            e.printStackTrace();
        }

        String type = null;
        int homeBankingId = -1;
        try {
            // Parse the incoming message (assuming JSON format)
            JsonObject jsonObjMSG = JsonParser.parseString(message).getAsJsonObject();

            homeBankingId = jsonObjMSG.has("homeBankingId")
                    ? Integer.parseInt(jsonObjMSG.get("homeBankingId").getAsString())
                    : -1;

            // 1) read top-level type (if any)
            type = jsonObjMSG.has("type") ? jsonObjMSG.get("type").getAsString() : "unknown";

            // 2) if still unknown, try to read "type" from inside body (which may be a stringified JSON)
            if ("unknown".equals(type) && jsonObjMSG.has("body")) {
                try {
                    var bodyEl = jsonObjMSG.get("body");
                    JsonObject bodyObj = null;

                    if (bodyEl.isJsonPrimitive() && bodyEl.getAsJsonPrimitive().isString()) {
                        // body is a JSON string -> parse it
                        bodyObj = JsonParser.parseString(bodyEl.getAsString()).getAsJsonObject();
                    } else if (bodyEl.isJsonObject()) {
                        // body already an object
                        bodyObj = bodyEl.getAsJsonObject();
                    }

                    if (bodyObj != null && bodyObj.has("type")) {
                        type = bodyObj.get("type").getAsString();
                    }
                } catch (Exception ignore) {
                    // leave type as "unknown"
                }
            }

            String claimedSessionId =
                    jsonObjMSG.has("sessionId") ? jsonObjMSG.get("sessionId").getAsString() : "unknown";
            String transportSessionId = webSocketSessionManager.getSessionIdBySession(session);
            boolean ocrWorkspaceOperation = type.startsWith("ocrWorkspace.");
            boolean detachedOcrTransport = OcrWorkspaceCoordinator.isWorkspaceSessionId(transportSessionId);
            boolean pageScannerOperation = isPageScannerTransportOperation(type);
            boolean detachedPageScannerTransport =
                    ScannerWorkspaceSessions.isPageScannerSession(transportSessionId);
            boolean memoryListOperation = type.startsWith("memoryList.");
            boolean pagesOpenOperation = type.startsWith("pagesOpen.");
            boolean configOperation = type.startsWith("config.");
            boolean commandEditorOperation =
                    type.startsWith("commandEditor.");
            boolean detachedCommandEditorTransport =
                    CommandEditorWorkspaceService.isWorkspaceSession(transportSessionId);
            String sessionId = ocrWorkspaceOperation
                            || detachedOcrTransport
                            || pageScannerOperation
                             || detachedPageScannerTransport
                             || memoryListOperation
                             || pagesOpenOperation
                             || configOperation
                             || commandEditorOperation
                             || detachedCommandEditorTransport
                    ? transportSessionId
                    : claimedSessionId;
            ReactReplyChannel.set(sessionId);

            // A retarget keeps the physical application window but retires its old logical
            // identity. Reject any late request from that stale transport before it can mutate
            // the newly selected Bot Job/scanner context.
            if (detachedOcrTransport && !ocrWorkspaceCoordinator.isActiveWorkspace(transportSessionId)) {
                log.warn("Rejected stale detached OCR transport {}", transportSessionId);
                closeRejectedSession(session, "OCR workspace session is no longer active");
                return;
            }
            if (detachedPageScannerTransport
                    && !pageScannerWorkspaceCoordinator.isActiveWorkspace(transportSessionId)) {
                log.warn("Rejected stale detached Page Scanner transport {}", transportSessionId);
                closeRejectedSession(session, "Page Scanner workspace session is no longer active");
                return;
            }

            // After Decoding
            if (type == null || type.trim().isEmpty() || type.contains("CONNECT") || type.contains("ping")) {
                // Ignore null or empty messages
                type = (type == null) ? "unknown" : type.replaceAll("ping-", "");
                return;
            }

            if (pageScannerOperation && !isSupportedPageScannerOperation(type)) {
                log.warn("Rejected unsupported Page Scanner operation {}", type);
                sendPageScannerFailure(
                        bodyOrEmpty(jsonObjMSG),
                        homeBankingId,
                        transportSessionId,
                        session,
                        "pageScanner.errorResponse",
                        "Unsupported Page Scanner operation.");
                return;
            }

            boolean closeWithoutLicense = "botJobDetails.action".equals(type)
                    && "CLOSE".equals(botJobDetailsAction(jsonObjMSG));
            boolean stopWithoutLicense = "botJobDetails.toolbar.action".equals(type)
                    && "STOP_TEST_RUN".equals(botJobDetailsToolbarAction(jsonObjMSG));
            boolean pauseResponseWithoutLicense = "botJobExecution.pause.response".equals(type);
            boolean pageScannerCloseWithoutLicense = "pageScanner.close".equals(type);
            if (!LicenseService.getInstance().permits(type)
                    && !closeWithoutLicense
                    && !stopWithoutLicense
                    && !pauseResponseWithoutLicense
                    && !pageScannerCloseWithoutLicense) {
                if (type.startsWith("botJobDetails.")) {
                    sendBotJobDetailsLicenseFailure(jsonObjMSG, session, type);
                    return;
                }
                if (pageScannerOperation || detachedPageScannerTransport) {
                    sendPageScannerFailure(
                            bodyOrEmpty(jsonObjMSG),
                            homeBankingId,
                            transportSessionId,
                            session,
                            pageScannerResponseOperation(type),
                            "An active license is required for this Page Scanner operation");
                    return;
                }
                sendCommandEditorResponse(
                        homeBankingId,
                        sessionId,
                        "license.requiredResponse",
                        LicenseService.getInstance().startup());
                return;
            }

            if (detachedPageScannerTransport
                    && !isAllowedFromDetachedPageScannerTransport(type)) {
                log.warn(
                        "Rejected legacy operation {} from detached Page Scanner transport {}",
                        type,
                        transportSessionId);
                sendPageScannerFailure(
                        bodyOrEmpty(jsonObjMSG),
                        homeBankingId,
                        transportSessionId,
                        session,
                        "pageScanner.errorResponse",
                        "Operation is not allowed from a detached Page Scanner workspace.");
                return;
            }
            if (detachedCommandEditorTransport
                    && !isAllowedFromDetachedCommandEditorTransport(type)) {
                log.warn(
                        "Rejected operation {} from detached Command Editor transport {}",
                        type,
                        transportSessionId);
                sendCommandEditorResponse(
                        homeBankingId,
                        transportSessionId,
                        "commandEditor.errorResponse",
                        commandEditorFailure(
                                extractBody(jsonObjMSG),
                                "Operation is not allowed from the detached Command Editor."));
                return;
            }

            // Process the message based on its type
            switch (type) {
                case "broadcast":
                    String broadcastMessage = jsonObjMSG.get("body").getAsString();
                    webSocketSessionManager.broadcastMessageToAll(homeBankingId, broadcastMessage);
                    break;
                case "echo":
                    webSocketSessionManager.sendMessageJson(
                            homeBankingId,
                            sessionId,
                            "echo: " + jsonObjMSG.get("body").getAsString(),
                            "sessionId: " + sessionId);
                    break;
                case "license.bootstrap":
                case "license.status":
                    sendCommandEditorResponse(
                            homeBankingId,
                            sessionId,
                            type + "Response",
                            LicenseService.getInstance().bootstrap());
                    break;
                case "license.startup":
                    sendCommandEditorResponse(
                            homeBankingId,
                            sessionId,
                            "license.startupResponse",
                            LicenseService.getInstance().startup());
                    break;
                case "about.bootstrap":
                    sendCommandEditorResponse(
                            homeBankingId,
                            sessionId,
                            "about.bootstrapResponse",
                            LicenseService.getInstance().about());
                    break;
                case "about.openLicense":
                    sendCommandEditorResponse(
                            homeBankingId,
                            sessionId,
                            "about.openLicenseResponse",
                            mainDashboardService.openLicense());
                    break;
                case "license.request":
                    JsonObject requestResponse = LicenseService.getInstance().request(extractBody(jsonObjMSG));
                    sendCommandEditorResponse(homeBankingId, sessionId, "license.requestResponse", requestResponse);
                    publishLicenseStatus(requestResponse);
                    break;
                case "license.activate":
                    JsonObject activationResponse = LicenseService.getInstance().activate(extractBody(jsonObjMSG));
                    if (isActiveLicenseResponse(activationResponse))
                        ApplicationStartupLifecycle.getInstance().continueAfterActivation();
                    sendCommandEditorResponse(homeBankingId, sessionId, "license.activateResponse", activationResponse);
                    publishLicenseStatus(activationResponse);
                    break;
                case "license.useExisting":
                    JsonObject existingResponse = LicenseService.getInstance().useExisting(extractBody(jsonObjMSG));
                    if (isActiveLicenseResponse(existingResponse))
                        ApplicationStartupLifecycle.getInstance().continueAfterActivation();
                    sendCommandEditorResponse(homeBankingId, sessionId, "license.useExistingResponse", existingResponse);
                    publishLicenseStatus(existingResponse);
                    break;
                case "excelExport.bootstrap":
                    sendCommandEditorResponse(homeBankingId, sessionId, "excelExport.bootstrapResponse",
                            excelExportService.bootstrap(extractBody(jsonObjMSG)));
                    break;
                case "excelExport.chooseDirectory":
                    sendCommandEditorResponse(homeBankingId, sessionId, "excelExport.chooseDirectoryResponse",
                            excelExportService.chooseDirectory(extractBody(jsonObjMSG)));
                    break;
                case "excelExport.save":
                case "excelExport.clear":
                    Map<String, Object> excelResponse = excelExportService.save(extractBody(jsonObjMSG));
                    sendCommandEditorResponse(homeBankingId, sessionId, "excelExport.saveResponse", excelResponse);
                    if (Boolean.TRUE.equals(excelResponse.get("ok"))) {
                        String updateOperation = String.valueOf(excelResponse.get("updateOperation"));
                        webSocketSessionManager.sendMessageJson(homeBankingId, sessionId,
                                gson.toJson(excelResponse.get("instructions")), updateOperation);
                    }
                    break;
                case "componentSave.bootstrap":
                    sendCommandEditorResponse(homeBankingId, sessionId, "componentSave.bootstrapResponse",
                            saveComponentService.bootstrap(extractBody(jsonObjMSG)));
                    break;
                case "componentSave.apply":
                    Map<String, Object> componentResponse = saveComponentService.save(extractBody(jsonObjMSG));
                    sendCommandEditorResponse(homeBankingId, sessionId, "componentSave.applyResponse", componentResponse);
                    if (Boolean.TRUE.equals(componentResponse.get("ok"))) {
                        webSocketSessionManager.sendMessageJson(
                                homeBankingId,
                                ScannerWorkspaceSessions.COMPONENT_TASKS,
                                gson.toJson(componentResponse.get("instructions")),
                                ScannerWorkspaceOperations.COMPONENTS_UPDATE);
                    }
                    break;
                case "ocrWorkspace.open":
                    handleOcrWorkspaceOpen(
                            jsonObjMSG,
                            homeBankingId,
                            claimedSessionId,
                            transportSessionId,
                            session);
                    break;
                case "ocrWorkspace.bootstrap":
                    handleOcrWorkspaceBootstrap(
                            jsonObjMSG,
                            homeBankingId,
                            claimedSessionId,
                            transportSessionId,
                            session);
                    break;
                case "ocrWorkspace.applySuggestions":
                    handleOcrWorkspaceApplySuggestions(
                            jsonObjMSG,
                            homeBankingId,
                            claimedSessionId,
                            transportSessionId,
                            session);
                    break;
                case "pageScannerWorkspace.open":
                    handlePageScannerWorkspaceOpen(
                            jsonObjMSG,
                            homeBankingId,
                            claimedSessionId,
                            transportSessionId,
                            session);
                    break;
                case "pageScannerWorkspace.bootstrap":
                    handlePageScannerWorkspaceBootstrap(
                            jsonObjMSG,
                            homeBankingId,
                            claimedSessionId,
                            transportSessionId,
                            session);
                    break;
                case "pageScanner.scan":
                case "pageScanner.refresh":
                case "pageScanner.clear":
                    handlePageScannerCommand(
                            type,
                            jsonObjMSG,
                            homeBankingId,
                            claimedSessionId,
                            transportSessionId,
                            session);
                    break;
                case "pageScanner.testElement":
                    handlePageScannerElementTest(
                            jsonObjMSG,
                            homeBankingId,
                            claimedSessionId,
                            transportSessionId,
                            session);
                    break;
                case "pageScanner.apply":
                    handlePageScannerApply(
                            jsonObjMSG,
                            homeBankingId,
                            claimedSessionId,
                            transportSessionId,
                            session);
                    break;
                case "pageScanner.locator.generate":
                    handlePageScannerLocatorGenerate(
                            jsonObjMSG,
                            homeBankingId,
                            claimedSessionId,
                            transportSessionId,
                            session);
                    break;
                case "pageScanner.locator.apply":
                    handlePageScannerLocatorApply(
                            jsonObjMSG,
                            homeBankingId,
                            claimedSessionId,
                            transportSessionId,
                            session);
                    break;
                case "pageScanner.createBlock":
                    handlePageScannerCreateBlock(
                            jsonObjMSG,
                            homeBankingId,
                            claimedSessionId,
                            transportSessionId,
                            session);
                    break;
                case "pageScannerProfile.list":
                case "pageScannerProfile.save":
                case "pageScannerProfile.delete":
                    handlePageScannerProfileCommand(
                            type,
                            jsonObjMSG,
                            homeBankingId,
                            claimedSessionId,
                            transportSessionId,
                            session);
                    break;
                case "pageScanner.close":
                    handlePageScannerClose(
                            jsonObjMSG,
                            homeBankingId,
                            claimedSessionId,
                            transportSessionId,
                            session);
                    break;
                case "ocrConfig.bootstrap":
                    sendCommandEditorResponse(homeBankingId, sessionId, "ocrConfig.bootstrapResponse",
                            ocrManagerService.bootstrap(extractBody(jsonObjMSG)));
                    break;
                case "ocrConfig.profile":
                    sendCommandEditorResponse(homeBankingId, sessionId, "ocrConfig.profileResponse",
                            ocrManagerService.profile(extractBody(jsonObjMSG)));
                    break;
                case "ocrConfig.save":
                    sendCommandEditorResponse(homeBankingId, sessionId, "ocrConfig.saveResponse",
                            ocrManagerService.save(extractBody(jsonObjMSG)));
                    break;
                case "ocrConfig.delete":
                    sendCommandEditorResponse(homeBankingId, sessionId, "ocrConfig.deleteResponse",
                            ocrManagerService.delete(extractBody(jsonObjMSG)));
                    break;
                case "ocrConfig.cleanupPreview":
                    sendCommandEditorResponse(homeBankingId, sessionId, "ocrConfig.cleanupPreviewResponse",
                            ocrManagerService.previewCleanup(extractBody(jsonObjMSG)));
                    break;
                case "ocrConfig.cleanupApply":
                    sendCommandEditorResponse(homeBankingId, sessionId, "ocrConfig.cleanupApplyResponse",
                            ocrManagerService.applyCleanup(extractBody(jsonObjMSG)));
                    break;
                case "ocrTest.run":
                    sendCommandEditorResponse(homeBankingId, sessionId, "ocrTest.runResponse",
                            ocrTestService.run(extractBody(jsonObjMSG)));
                    break;
                case "scanner.plugin.download":
                    sendCommandEditorResponse(
                            homeBankingId,
                            sessionId,
                            "scanner.plugin.downloadResponse",
                            scannerPluginDownloadCommandService.download(extractBody(jsonObjMSG)));
                    break;
                case "scanner.plugin.downloadBatch":
                    sendCommandEditorResponse(
                            homeBankingId,
                            sessionId,
                            "scanner.plugin.downloadBatchResponse",
                            scannerPluginDownloadCommandService.downloadBatch(extractBody(jsonObjMSG)));
                    break;
                case "commandEditor.workspaceOpen": {
                    JsonObject commandWorkspaceBody = extractBody(jsonObjMSG);
                    sendCommandEditorResponse(
                            homeBankingId,
                            sessionId,
                            "commandEditor.workspaceOpenResponse",
                            commandEditorWorkspaceService.open(
                                    commandWorkspaceBody, sessionId, session));
                    break;
                }
                case "commandEditor.workspaceBootstrap": {
                    JsonObject commandWorkspaceBody = extractBody(jsonObjMSG);
                    sendCommandEditorResponse(
                            homeBankingId,
                            sessionId,
                            "commandEditor.workspaceBootstrapResponse",
                            commandEditorWorkspaceService.bootstrap(
                                    commandWorkspaceBody, sessionId, session));
                    break;
                }
                case "commandEditor.select": {
                    JsonObject commandSelectBody = extractBody(jsonObjMSG);
                    sendCommandEditorResponse(
                            homeBankingId,
                            sessionId,
                            "commandEditor.selectResponse",
                            commandEditorWorkspaceService.select(
                                    commandSelectBody, sessionId, session));
                    break;
                }
                case "commandEditor.bootstrap": {
                    JsonObject commandBootstrapBody = extractBody(jsonObjMSG);
                    try {
                        commandBootstrapBody = authorizeCommandEditorRequest(
                                commandBootstrapBody, sessionId, session);
                    } catch (IllegalArgumentException authorizationError) {
                        sendCommandEditorResponse(
                                homeBankingId,
                                sessionId,
                                "commandEditor.bootstrapResponse",
                                commandEditorFailure(
                                        commandBootstrapBody,
                                        authorizationError.getMessage()));
                        break;
                    }
                    sendCommandEditorResponse(
                            commandEditorHomeBankingId(
                                    commandBootstrapBody, homeBankingId),
                            sessionId,
                            "commandEditor.bootstrapResponse",
                            attachCommandEditorBindingEpoch(
                                    CommandEditorService.getInstance()
                                            .bootstrap(commandBootstrapBody),
                                    commandBootstrapBody,
                                    sessionId));
                    break;
                }
                case "commandEditor.apply": {
                    JsonObject commandApplyBody = extractBody(jsonObjMSG);
                    boolean detachedCommandEditor =
                            CommandEditorWorkspaceService.isWorkspaceSession(sessionId);
                    int commandHomeBankingId = homeBankingId;
                    boolean commandMutationReplayed = false;
                    JsonObject commandApplyResponse;
                    try {
                        if (detachedCommandEditor) {
                            CommandEditorWorkspaceService.AuthorizedMutation mutation =
                                    commandEditorWorkspaceService.executeMutation(
                                            commandApplyBody,
                                            sessionId,
                                            session,
                                            CommandEditorService.getInstance()::apply);
                            commandApplyBody = mutation.request().body();
                            commandHomeBankingId = mutation.request().homeBankingId();
                            commandMutationReplayed = mutation.replayed();
                            commandApplyResponse = mutation.response();
                        } else {
                            commandApplyResponse =
                                    CommandEditorService.getInstance().apply(commandApplyBody);
                        }
                    } catch (Exception commandError) {
                        log.error(
                                "COMMAND_EDITOR_APPLY_EXCEPTION requestId={} action={}",
                                commandLogValue(commandApplyBody, "requestId"),
                                commandLogValue(commandApplyBody, "action"),
                                commandError);
                        commandApplyResponse = commandEditorFailure(
                                commandApplyBody,
                                commandError.getMessage() == null
                                        ? "The command could not be saved."
                                        : commandError.getMessage());
                    }
                    log.info(
                            "COMMAND_EDITOR_APPLY_RECEIVED requestId={} wsSession={} targetSession={}"
                                    + " instructionId={} blockId={} action={} mode={} parentBlockId={} count={}"
                                    + " graphRevisionPresent={}",
                            commandLogValue(commandApplyBody, "requestId"),
                            sessionId,
                            commandLogValue(commandApplyBody, "targetSessionId"),
                            commandLogValue(commandApplyBody, "instructionId"),
                            commandLogValue(commandApplyBody, "blockId"),
                            commandLogValue(commandApplyBody, "action"),
                            commandLogValue(commandApplyBody, "mode"),
                            commandLogValue(commandApplyBody, "parentBlockId"),
                            commandLogValue(commandApplyBody, "count"),
                            commandApplyBody != null
                                    && commandApplyBody.has("graphRevision")
                                    && !commandApplyBody.get("graphRevision").isJsonNull());
                    boolean commandSaved = commandApplyResponse.has("ok")
                            && commandApplyResponse.get("ok").getAsBoolean();
                    String commandResult = commandSaved
                            ? commandLogValue(commandApplyResponse, "message")
                            : commandLogValue(commandApplyResponse, "error");
                    if (commandSaved) {
                        log.info(
                                "COMMAND_EDITOR_APPLY_RESPONSE requestId={} instructionId={} action={} ok=true result={}",
                                commandLogValue(commandApplyBody, "requestId"),
                                commandLogValue(commandApplyBody, "instructionId"),
                                commandLogValue(commandApplyBody, "action"),
                                commandResult);
                    } else {
                        log.warn(
                                "COMMAND_EDITOR_APPLY_RESPONSE requestId={} instructionId={} action={} ok=false error={}",
                                commandLogValue(commandApplyBody, "requestId"),
                                commandLogValue(commandApplyBody, "instructionId"),
                                commandLogValue(commandApplyBody, "action"),
                                commandResult);
                    }
                    if (commandSaved
                            && commandApplyResponse.has("instructions")
                            && commandApplyResponse.get("instructions").isJsonArray()) {
                        String targetSessionId = commandLogValue(commandApplyBody, "targetSessionId");
                        if ("<missing>".equals(targetSessionId) || "<blank>".equals(targetSessionId)) {
                            targetSessionId = ScannerWorkspaceSessions.BOT_JOB_TASKS;
                        }
                        String updateOperationId = instructionRealtimePublisher.snapshotOperation(targetSessionId);
                        if (detachedCommandEditor) {
                            instructionRealtimePublisher.publishResponse(
                                    commandHomeBankingId,
                                    sessionId,
                                    "commandEditor.applyResponse",
                                    commandApplyResponse);
                            if (!commandMutationReplayed) {
                                instructionRealtimePublisher.publishSnapshot(
                                        commandHomeBankingId,
                                        targetSessionId,
                                        commandApplyResponse.getAsJsonArray("instructions"));
                            }
                        } else {
                            instructionRealtimePublisher.publishMutationThenSnapshot(
                                    commandHomeBankingId,
                                    targetSessionId,
                                    "commandEditor.applyResponse",
                                    commandApplyResponse,
                                    commandApplyResponse.getAsJsonArray("instructions"));
                        }
                        if (!commandMutationReplayed) {
                            log.info(
                                    "COMMAND_EDITOR_REALTIME_UPDATE requestId={} targetSession={} operationId={} rows={}",
                                    commandLogValue(commandApplyBody, "requestId"),
                                    targetSessionId,
                                    updateOperationId,
                                    commandApplyResponse.getAsJsonArray("instructions").size());
                        }
                    } else {
                        instructionRealtimePublisher.publishResponse(
                                commandHomeBankingId,
                                sessionId,
                                "commandEditor.applyResponse",
                                commandApplyResponse);
                    }
                    break;
                }
                case "commandEditor.insertElseIf": {
                    JsonObject commandElseIfBody = extractBody(jsonObjMSG);
                    JsonObject commandElseIfResponse;
                    int commandElseIfHomeBankingId = homeBankingId;
                    try {
                        if (CommandEditorWorkspaceService.isWorkspaceSession(sessionId)) {
                            CommandEditorWorkspaceService.AuthorizedMutation mutation =
                                    commandEditorWorkspaceService.executeMutation(
                                            commandElseIfBody,
                                            sessionId,
                                            session,
                                            CommandEditorService.getInstance()::insertElseIf);
                            commandElseIfBody = mutation.request().body();
                            commandElseIfHomeBankingId =
                                    mutation.request().homeBankingId();
                            commandElseIfResponse = mutation.response();
                        } else {
                            commandElseIfResponse = CommandEditorService.getInstance()
                                    .insertElseIf(commandElseIfBody);
                        }
                    } catch (IllegalArgumentException authorizationError) {
                        sendCommandEditorResponse(
                                commandElseIfHomeBankingId,
                                sessionId,
                                "commandEditor.insertElseIfResponse",
                                commandEditorFailure(
                                        commandElseIfBody,
                                        authorizationError.getMessage()));
                        break;
                    }
                    sendCommandEditorResponse(
                            commandElseIfHomeBankingId,
                            sessionId,
                            "commandEditor.insertElseIfResponse",
                            commandElseIfResponse);
                    break;
                }
                case "instructionGraph.previewSplit": {
                    JsonObject commandPreviewBody = extractBody(jsonObjMSG);
                    try {
                        commandPreviewBody = authorizeCommandEditorRequest(
                                commandPreviewBody, sessionId, session);
                    } catch (IllegalArgumentException authorizationError) {
                        sendCommandEditorResponse(
                                homeBankingId,
                                sessionId,
                                "instructionGraph.previewSplitResponse",
                                commandEditorFailure(
                                        commandPreviewBody,
                                        authorizationError.getMessage()));
                        break;
                    }
                    sendCommandEditorResponse(
                            commandEditorHomeBankingId(
                                    commandPreviewBody, homeBankingId),
                            sessionId,
                            "instructionGraph.previewSplitResponse",
                            attachCommandEditorBindingEpoch(
                                    CommandEditorService.getInstance()
                                            .previewSplit(commandPreviewBody),
                                    commandPreviewBody,
                                    sessionId));
                    break;
                }
                case "instructionGraph.previewMove": {
                    JsonObject commandPreviewBody = extractBody(jsonObjMSG);
                    try {
                        commandPreviewBody = authorizeCommandEditorRequest(
                                commandPreviewBody, sessionId, session);
                    } catch (IllegalArgumentException authorizationError) {
                        sendCommandEditorResponse(
                                homeBankingId,
                                sessionId,
                                "instructionGraph.previewMoveResponse",
                                commandEditorFailure(
                                        commandPreviewBody,
                                        authorizationError.getMessage()));
                        break;
                    }
                    sendCommandEditorResponse(
                            commandEditorHomeBankingId(
                                    commandPreviewBody, homeBankingId),
                            sessionId,
                            "instructionGraph.previewMoveResponse",
                            attachCommandEditorBindingEpoch(
                                    CommandEditorService.getInstance()
                                            .previewMove(commandPreviewBody),
                                    commandPreviewBody,
                                    sessionId));
                    break;
                }
                case "instructionEditor.memoryCapabilities": {
                    JsonObject commandMemoryBody = extractBody(jsonObjMSG);
                    try {
                        commandMemoryBody = authorizeCommandEditorRequest(
                                commandMemoryBody, sessionId, session);
                    } catch (IllegalArgumentException authorizationError) {
                        sendCommandEditorResponse(
                                homeBankingId,
                                sessionId,
                                "instructionEditor.memoryCapabilitiesResponse",
                                commandEditorFailure(
                                        commandMemoryBody,
                                        authorizationError.getMessage()));
                        break;
                    }
                    sendCommandEditorResponse(
                            commandEditorHomeBankingId(
                                    commandMemoryBody, homeBankingId),
                            sessionId,
                            "instructionEditor.memoryCapabilitiesResponse",
                            attachCommandEditorBindingEpoch(
                                    CommandEditorService.getInstance()
                                            .memoryCapabilities(commandMemoryBody),
                                    commandMemoryBody,
                                    sessionId));
                    break;
                }
                case "variableEditor.bootstrap": {
                    JsonObject variableEditorBody = extractBody(jsonObjMSG);
                    try {
                        variableEditorBody = authorizeCommandEditorRequest(
                                variableEditorBody, sessionId, session);
                    } catch (IllegalArgumentException authorizationError) {
                        sendCommandEditorResponse(
                                homeBankingId,
                                sessionId,
                                "variableEditor.bootstrapResponse",
                                commandEditorFailure(
                                        variableEditorBody,
                                        authorizationError.getMessage()));
                        break;
                    }
                    sendCommandEditorResponse(
                            commandEditorHomeBankingId(
                                    variableEditorBody, homeBankingId),
                            sessionId,
                            "variableEditor.bootstrapResponse",
                            attachCommandEditorBindingEpoch(
                                    VariableEditorService.getInstance()
                                            .list(variableEditorBody),
                                    variableEditorBody,
                                    sessionId));
                    break;
                }
                case "variableEditor.save": {
                    JsonObject variableEditorBody = extractBody(jsonObjMSG);
                    JsonObject variableEditorResponse;
                    int variableEditorHomeBankingId = homeBankingId;
                    try {
                        if (CommandEditorWorkspaceService.isWorkspaceSession(sessionId)) {
                            CommandEditorWorkspaceService.AuthorizedMutation mutation =
                                    commandEditorWorkspaceService.executeMutation(
                                            variableEditorBody,
                                            sessionId,
                                            session,
                                            VariableEditorService.getInstance()::save);
                            variableEditorBody = mutation.request().body();
                            variableEditorHomeBankingId =
                                    mutation.request().homeBankingId();
                            variableEditorResponse = mutation.response();
                        } else {
                            variableEditorResponse =
                                    VariableEditorService.getInstance().save(variableEditorBody);
                        }
                    } catch (IllegalArgumentException authorizationError) {
                        sendCommandEditorResponse(
                                variableEditorHomeBankingId,
                                sessionId,
                                "variableEditor.saveResponse",
                                commandEditorFailure(
                                        variableEditorBody,
                                        authorizationError.getMessage()));
                        break;
                    }
                    sendCommandEditorResponse(
                            variableEditorHomeBankingId,
                            sessionId,
                            "variableEditor.saveResponse",
                            variableEditorResponse);
                    break;
                }
                case "variableEditor.delete": {
                    JsonObject variableEditorBody = extractBody(jsonObjMSG);
                    JsonObject variableEditorResponse;
                    int variableEditorHomeBankingId = homeBankingId;
                    try {
                        if (CommandEditorWorkspaceService.isWorkspaceSession(sessionId)) {
                            CommandEditorWorkspaceService.AuthorizedMutation mutation =
                                    commandEditorWorkspaceService.executeMutation(
                                            variableEditorBody,
                                            sessionId,
                                            session,
                                            VariableEditorService.getInstance()::delete);
                            variableEditorBody = mutation.request().body();
                            variableEditorHomeBankingId =
                                    mutation.request().homeBankingId();
                            variableEditorResponse = mutation.response();
                        } else {
                            variableEditorResponse =
                                    VariableEditorService.getInstance().delete(variableEditorBody);
                        }
                    } catch (IllegalArgumentException authorizationError) {
                        sendCommandEditorResponse(
                                variableEditorHomeBankingId,
                                sessionId,
                                "variableEditor.deleteResponse",
                                commandEditorFailure(
                                        variableEditorBody,
                                        authorizationError.getMessage()));
                        break;
                    }
                    sendCommandEditorResponse(
                            variableEditorHomeBankingId,
                            sessionId,
                            "variableEditor.deleteResponse",
                            variableEditorResponse);
                    break;
                }
                case "botJob.getInputInstructions":
                    handleBotJobInputInstructions(jsonObjMSG, sessionId, homeBankingId);
                    break;
                case "botJob.getBlocks":
                    handleBotJobGetBlocks(jsonObjMSG, sessionId, homeBankingId);
                    break;
                case "funcTest.loadMappings":
                    handleFuncTestLoadMappings(jsonObjMSG, sessionId, homeBankingId);
                    break;
                case "funcTest.saveMappings":
                    handleFuncTestSaveMappings(jsonObjMSG, sessionId, homeBankingId);
                    break;
                case "useCase.list":
                    handleUseCaseList(jsonObjMSG, sessionId, homeBankingId);
                    break;
                case "useCase.save":
                    handleUseCaseSave(jsonObjMSG, sessionId, homeBankingId);
                    break;
                case "useCase.delete":
                    handleUseCaseDelete(jsonObjMSG, sessionId, homeBankingId);
                    break;
                case "flow.list":
                    handleFlowList(jsonObjMSG, sessionId, homeBankingId);
                    break;
                case "flow.save":
                    handleFlowSave(jsonObjMSG, sessionId, homeBankingId);
                    break;
                case "flow.delete":
                    handleFlowDelete(jsonObjMSG, sessionId, homeBankingId);
                    break;
                case "flow.steps.load":
                    handleFlowStepsLoad(jsonObjMSG, sessionId, homeBankingId);
                    break;
                case "flow.steps.save":
                    handleFlowStepsSave(jsonObjMSG, sessionId, homeBankingId);
                    break;
                case "requirement.list":
                    handleRequirementList(jsonObjMSG, sessionId, homeBankingId);
                    break;
                case "requirement.save":
                    handleRequirementSave(jsonObjMSG, sessionId, homeBankingId);
                    break;
                case "requirement.delete":
                    handleRequirementDelete(jsonObjMSG, sessionId, homeBankingId);
                    break;
                case "requirement.links.load":
                    handleRequirementLinksLoad(jsonObjMSG, sessionId, homeBankingId);
                    break;
                case "requirement.links.save":
                    handleRequirementLinksSave(jsonObjMSG, sessionId, homeBankingId);
                    break;
                case "organization.list":
                    handleOrganizationList(sessionId);
                    break;
                case "organization.create":
                    handleOrganizationCreate(jsonObjMSG, sessionId);
                    break;
                case "organization.update":
                    handleOrganizationUpdate(jsonObjMSG, sessionId);
                    break;
                case "organization.delete":
                    handleOrganizationDelete(jsonObjMSG, sessionId);
                    break;
                case "organization.template":
                    handleOrganizationTemplate(sessionId);
                    break;
                case "homeUrl.list":
                    handleHomeUrlList(jsonObjMSG, sessionId);
                    break;
                case "homeUrl.create":
                    handleHomeUrlCreate(jsonObjMSG, sessionId);
                    break;
                case "homeUrl.update":
                    handleHomeUrlUpdate(jsonObjMSG, sessionId);
                    break;
                case "homeUrl.delete":
                    handleHomeUrlDelete(jsonObjMSG, sessionId);
                    break;
                case "mainDashboard.list":
                    handleMainDashboardList(sessionId);
                    break;
                case "automationTests.list":
                    handleAutomationTestsList(sessionId);
                    break;
                case "mainDashboard.openOrganizations":
                    handleMainDashboardOpenOrganizations(sessionId);
                    break;
                case "mainDashboard.newBotJob":
                    handleMainDashboardNewBotJob(sessionId);
                    break;
                case "mainDashboard.cloneBotJob":
                    handleMainDashboardCloneBotJob(jsonObjMSG, sessionId);
                    break;
                case "mainDashboard.openBotJob":
                    handleMainDashboardOpenBotJob(jsonObjMSG, sessionId);
                    break;
                case "mainDashboard.launchBotJob":
                    handleMainDashboardLaunchBotJob(jsonObjMSG, sessionId);
                    break;
                case "mainDashboard.deleteBotJob":
                    handleMainDashboardDeleteBotJob(jsonObjMSG, sessionId);
                    break;
                case "mainDashboard.openConfig":
                    handleMainDashboardOpenConfig(sessionId);
                    break;
                case "mainDashboard.openTemplate":
                    handleMainDashboardOpenTemplate(sessionId);
                    break;
                case "mainDashboard.openInfo":
                    handleMainDashboardOpenInfo(sessionId);
                    break;
                case "mainDashboard.exit":
                    handleMainDashboardExit(sessionId);
                    break;
                case "memoryList.open":
                    handleMemoryListOpen(jsonObjMSG, transportSessionId, session);
                    break;
                case "memoryList.sync":
                    handleMemoryListSync(jsonObjMSG, transportSessionId, session);
                    break;
                case "memoryList.bootstrap":
                    handleMemoryListBootstrap(jsonObjMSG, transportSessionId, session);
                    break;
                case "memoryList.command":
                    handleMemoryListCommand(jsonObjMSG, transportSessionId, session);
                    break;
                case "pagesOpen.open":
                    handlePagesOpenOpen(jsonObjMSG, transportSessionId, session);
                    break;
                case "pagesOpen.summary":
                    handlePagesOpenSummary(jsonObjMSG, transportSessionId, session);
                    break;
                case "pagesOpen.bootstrap":
                    handlePagesOpenBootstrap(jsonObjMSG, transportSessionId, session);
                    break;
                case "pagesOpen.closePage":
                    handlePagesOpenClosePage(jsonObjMSG, transportSessionId, session);
                    break;
                case "pagesOpen.focusPage":
                    handlePagesOpenFocusPage(jsonObjMSG, transportSessionId, session);
                    break;
                case "pagesOpen.inlineState":
                    handlePagesOpenInlineState(jsonObjMSG, transportSessionId, session);
                    break;
                case "botJobDetails.action":
                    handleBotJobDetailsAction(jsonObjMSG, session);
                    break;
                case "botJobDetails.toolbar.action":
                    handleBotJobDetailsToolbarAction(jsonObjMSG, session);
                    break;
                case "botJobDetails.bootstrap":
                    handleBotJobDetailsBootstrap(jsonObjMSG, session);
                    break;
                case "botJobDetails.metadata.update":
                    handleBotJobDetailsMetadataUpdate(jsonObjMSG, session);
                    break;
                case "botJobDetails.environments.refresh":
                    handleBotJobDetailsEnvironmentRefresh(jsonObjMSG, session);
                    break;
                case "botJobExecution.pause.response":
                    handleBotJobExecutionPauseResponse(jsonObjMSG, session);
                    break;
                case ScannerWorkspaceOperations.BOOTSTRAP_COMMAND:
                    handleScannerBootstrap(jsonObjMSG, session);
                    break;
                case ScannerWorkspaceOperations.ACTION_COMMAND:
                    handleScannerAction(jsonObjMSG, session);
                    break;
                case "newBotJob.bootstrap":
                    handleNewBotJobBootstrap(sessionId);
                    break;
                case "newBotJob.environments":
                    handleNewBotJobEnvironments(sessionId);
                    break;
                case "newBotJob.create":
                    handleNewBotJobCreate(jsonObjMSG, sessionId);
                    break;
                case "newBotJob.openOrganizations":
                    handleNewBotJobOpenOrganizations(sessionId);
                    break;
                case "newBotJob.cancel":
                    handleNewBotJobCancel(sessionId);
                    break;
                case "cloneJob.bootstrap":
                    sendCloneJobResponse(sessionId, cloneJobService.bootstrap(extractBody(jsonObjMSG)), "cloneJob.bootstrapResponse");
                    break;
                case "cloneJob.environments":
                    sendCloneJobResponse(sessionId, cloneJobService.environments(extractBody(jsonObjMSG)), "cloneJob.environmentsResponse");
                    break;
                case "cloneJob.validateName":
                    sendCloneJobResponse(sessionId, cloneJobService.validateName(extractBody(jsonObjMSG)), "cloneJob.validateNameResponse");
                    break;
                case "cloneJob.create":
                case "cloneJob.clone":
                    sendCloneJobResponse(sessionId, cloneJobService.create(extractBody(jsonObjMSG)), "cloneJob.cloneResponse");
                    break;
                case "cloneJob.openOrganizations":
                    MainDashboardPresentationRegistry.getInstance().current().openCloneOrganizations();
                    sendCloneJobResponse(sessionId, java.util.Map.of("ok", true, "message", "Organizations opened"), "cloneJob.actionResponse");
                    break;
                case "cloneJob.cancel":
                    sendCloneJobResponse(
                            sessionId,
                            java.util.Map.of(
                                    "ok", true,
                                    "message", "Close this detached Clone Job window"),
                            "cloneJob.actionResponse");
                    break;
                case "config.bootstrap":
                    handleConfigBootstrap(sessionId);
                    break;
                case "config.choosePath":
                    handleConfigChoosePath(jsonObjMSG, sessionId, session);
                    break;
                case "config.save":
                    handleConfigSave(jsonObjMSG, sessionId, session);
                    break;
                case "config.backup":
                    handleConfigBackup(jsonObjMSG, sessionId, session);
                    break;
                case "config.browser.update":
                    handleConfigBrowserUpdate(jsonObjMSG, sessionId, session);
                    break;
                case "config.restore":
                    handleConfigRestore(jsonObjMSG, sessionId, session);
                    break;
                case "config.deleteAllJobs":
                    handleConfigDeleteAllJobs(jsonObjMSG, sessionId);
                    break;
                case "config.openOrganizations":
                    handleConfigOpenOrganizations(sessionId);
                    break;
                case "config.loadGenFlowPrompt":
                    handleConfigLoadGenFlowPrompt(sessionId);
                    break;
                case "config.saveGenFlowPrompt":
                    handleConfigSaveGenFlowPrompt(jsonObjMSG, sessionId);
                    break;
                case "config.cancel":
                    handleConfigCancel(sessionId);
                    break;
                default:
                    handleMessageByType(type, jsonObjMSG, session, sessionId);
                    break;
            }
        } catch (Exception error) {
            log.error("Closed processing message: " + error.getMessage());
            if (type != null) {
                webSocketSessionManager.sendMessageJson(
                        homeBankingId, session, type, "Action type : \"" + type + "\"", "cannot be processed");
            } else {
                webSocketSessionManager.sendMessageJson(
                        homeBankingId, session, type, "Closed processing message", "No \"type\" definition");
            }
        } finally {
            ReactReplyChannel.clear();
        }
    }

    private void handleOrganizationList(String sessionId) {
        sendOrganizationResponse(sessionId, organizationManagerService.list(), "organization.listResponse");
    }

    private void handleOrganizationCreate(JsonObject jsonObjMSG, String sessionId) {
        sendOrganizationResponse(
                sessionId,
                organizationManagerService.createOrganization(extractBody(jsonObjMSG)),
                "organization.saveResponse");
    }

    private void handleOrganizationUpdate(JsonObject jsonObjMSG, String sessionId) {
        sendOrganizationResponse(
                sessionId,
                organizationManagerService.updateOrganization(extractBody(jsonObjMSG)),
                "organization.saveResponse");
    }

    private void handleOrganizationDelete(JsonObject jsonObjMSG, String sessionId) {
        sendOrganizationResponse(
                sessionId,
                organizationManagerService.deleteOrganization(extractBody(jsonObjMSG)),
                "organization.deleteResponse");
    }

    private void handleOrganizationTemplate(String sessionId) {
        sendOrganizationResponse(sessionId, organizationManagerService.template(), "organization.templateResponse");
    }

    private void handleHomeUrlList(JsonObject jsonObjMSG, String sessionId) {
        sendOrganizationResponse(
                sessionId, organizationManagerService.listHomeUrls(extractBody(jsonObjMSG)), "homeUrl.listResponse");
    }

    private void handleHomeUrlCreate(JsonObject jsonObjMSG, String sessionId) {
        sendOrganizationResponse(
                sessionId, organizationManagerService.createHomeUrl(extractBody(jsonObjMSG)), "homeUrl.saveResponse");
    }

    private void handleHomeUrlUpdate(JsonObject jsonObjMSG, String sessionId) {
        sendOrganizationResponse(
                sessionId, organizationManagerService.updateHomeUrl(extractBody(jsonObjMSG)), "homeUrl.saveResponse");
    }

    private void handleHomeUrlDelete(JsonObject jsonObjMSG, String sessionId) {
        sendOrganizationResponse(
                sessionId, organizationManagerService.deleteHomeUrl(extractBody(jsonObjMSG)), "homeUrl.deleteResponse");
    }

    private void sendOrganizationResponse(String sessionId, Object response, String operationId) {
        webSocketSessionManager.sendMessageJson(-1, sessionId, gson.toJson(response), operationId);
    }

    private void sendCloneJobResponse(String sessionId, Object response, String operationId) {
        webSocketSessionManager.sendMessageJson(-1, sessionId, gson.toJson(response), operationId);
    }

    private void handleMainDashboardList(String sessionId) {
        sendMainDashboardResponse(sessionId, mainDashboardService.list(), "mainDashboard.listResponse");
    }

    private void handleAutomationTestsList(String sessionId) {
        sendMainDashboardResponse(
                sessionId, automationTestCatalogService.list(), "automationTests.listResponse");
    }

    private void handleMainDashboardOpenOrganizations(String sessionId) {
        sendMainDashboardResponse(
                sessionId, mainDashboardService.openOrganizations(), "mainDashboard.actionResponse");
    }

    private void handleMainDashboardNewBotJob(String sessionId) {
        sendMainDashboardResponse(sessionId, mainDashboardService.newBotJob(), "mainDashboard.actionResponse");
    }

    private void handleMainDashboardCloneBotJob(JsonObject jsonObjMSG, String sessionId) {
        sendMainDashboardResponse(
                sessionId, mainDashboardService.cloneBotJob(extractBody(jsonObjMSG)), "mainDashboard.actionResponse");
    }

    private void handleMainDashboardOpenBotJob(JsonObject jsonObjMSG, String sessionId) {
        sendMainDashboardResponse(
                sessionId, mainDashboardService.openBotJob(extractBody(jsonObjMSG)), "mainDashboard.actionResponse");
    }

    private void handleMainDashboardLaunchBotJob(JsonObject jsonObjMSG, String sessionId) {
        sendMainDashboardResponse(
                sessionId, mainDashboardService.launchBotJob(extractBody(jsonObjMSG)), "mainDashboard.actionResponse");
    }

    private void handleMainDashboardDeleteBotJob(JsonObject jsonObjMSG, String sessionId) {
        sendMainDashboardResponse(
                sessionId, mainDashboardService.deleteBotJob(extractBody(jsonObjMSG)), "mainDashboard.actionResponse");
    }

    private void handleMainDashboardOpenConfig(String sessionId) {
        sendMainDashboardResponse(sessionId, mainDashboardService.openConfig(), "mainDashboard.actionResponse");
    }

    private void handleMainDashboardOpenTemplate(String sessionId) {
        sendMainDashboardResponse(sessionId, mainDashboardService.openTemplate(), "mainDashboard.actionResponse");
    }

    private void handleMainDashboardOpenInfo(String sessionId) {
        sendMainDashboardResponse(sessionId, mainDashboardService.openInfo(), "mainDashboard.actionResponse");
    }

    private void handleMainDashboardExit(String sessionId) {
        sendMainDashboardResponse(sessionId, mainDashboardService.exit(), "mainDashboard.actionResponse");
    }

    private void handleMemoryListOpen(
            JsonObject envelope, String transportSessionId, Session transportSession) {
        JsonObject body = extractBody(envelope);
        sendCommandEditorResponse(
                memoryListHomeBankingId(envelope, body),
                transportSessionId,
                "memoryList.openResponse",
                memoryListWorkspaceService.open(body, transportSessionId, transportSession));
    }

    private void handleMemoryListSync(
            JsonObject envelope, String transportSessionId, Session transportSession) {
        JsonObject body = extractBody(envelope);
        sendCommandEditorResponse(
                memoryListHomeBankingId(envelope, body),
                transportSessionId,
                "memoryList.syncResponse",
                memoryListWorkspaceService.sync(body, transportSessionId, transportSession));
    }

    private void handleMemoryListBootstrap(
            JsonObject envelope, String transportSessionId, Session transportSession) {
        JsonObject body = extractBody(envelope);
        sendCommandEditorResponse(
                memoryListHomeBankingId(envelope, body),
                transportSessionId,
                "memoryList.bootstrapResponse",
                memoryListWorkspaceService.bootstrap(body, transportSessionId, transportSession));
    }

    private void handleMemoryListCommand(
            JsonObject envelope, String transportSessionId, Session transportSession) {
        JsonObject body = extractBody(envelope);
        sendCommandEditorResponse(
                memoryListHomeBankingId(envelope, body),
                transportSessionId,
                "memoryList.commandResponse",
                memoryListWorkspaceService.command(body, transportSessionId, transportSession));
    }

    private void handlePagesOpenOpen(
            JsonObject envelope, String transportSessionId, Session transportSession) {
        sendPagesOpenResponse(
                transportSessionId,
                transportSession,
                "pagesOpen.openResponse",
                pagesOpenWorkspaceService.open(
                        extractBody(envelope), transportSessionId, transportSession));
    }

    private void handlePagesOpenSummary(
            JsonObject envelope, String transportSessionId, Session transportSession) {
        sendPagesOpenResponse(
                transportSessionId,
                transportSession,
                "pagesOpen.summaryResponse",
                pagesOpenWorkspaceService.summary(
                        extractBody(envelope), transportSessionId, transportSession));
    }

    private void handlePagesOpenBootstrap(
            JsonObject envelope, String transportSessionId, Session transportSession) {
        sendPagesOpenResponse(
                transportSessionId,
                transportSession,
                "pagesOpen.bootstrapResponse",
                pagesOpenWorkspaceService.bootstrap(
                        extractBody(envelope), transportSessionId, transportSession));
    }

    private void handlePagesOpenClosePage(
            JsonObject envelope, String transportSessionId, Session transportSession) {
        sendPagesOpenResponse(
                transportSessionId,
                transportSession,
                "pagesOpen.closePageResponse",
                pagesOpenWorkspaceService.closePage(
                        extractBody(envelope), transportSessionId, transportSession));
    }

    private void handlePagesOpenFocusPage(
            JsonObject envelope, String transportSessionId, Session transportSession) {
        sendPagesOpenResponse(
                transportSessionId,
                transportSession,
                "pagesOpen.focusPageResponse",
                pagesOpenWorkspaceService.focusPage(
                        extractBody(envelope), transportSessionId, transportSession));
    }

    private void handlePagesOpenInlineState(
            JsonObject envelope, String transportSessionId, Session transportSession) {
        sendPagesOpenResponse(
                transportSessionId,
                transportSession,
                "pagesOpen.inlineStateResponse",
                pagesOpenWorkspaceService.inlineState(
                        extractBody(envelope), transportSessionId, transportSession));
    }

    private void sendPagesOpenResponse(
            String sessionId, Session transport, String operationId, JsonObject response) {
        WebSocketSessionManager.sendMessageJson(
                -1, transport, sessionId, gson.toJson(response), operationId);
    }

    private int memoryListHomeBankingId(JsonObject envelope, JsonObject body) {
        int homeBankingId = positiveJsonInteger(body, "homeBankingId");
        if (homeBankingId > 0) return homeBankingId;
        return positiveJsonInteger(envelope, "homeBankingId");
    }

    private int positiveJsonInteger(JsonObject source, String field) {
        if (source == null || !source.has(field) || source.get(field).isJsonNull()) return -1;
        try {
            int value = source.get(field).getAsInt();
            return value > 0 ? value : -1;
        } catch (RuntimeException invalidInteger) {
            return -1;
        }
    }

    private void sendMainDashboardResponse(String sessionId, Object response, String operationId) {
        webSocketSessionManager.sendMessageJson(-1, sessionId, gson.toJson(response), operationId);
    }

    private void handleBotJobDetailsAction(JsonObject envelope, Session transportSession) {
        Map<String, Object> response = new LinkedHashMap<>();
        try {
            BotJobDetailsRequest request = parseBotJobDetailsRequest(envelope, transportSession);
            response.put("requestId", request.requestId());
            response.put("botJobId", request.botJobId());
            BotJobWorkspaceAction action = BotJobWorkspaceAction.parse(
                    request.body().has("action") ? request.body().get("action").getAsString() : null);
            botJobDetailsActionLedger
                    .executeOnce(
                            request.sessionId(),
                            request.requestId(),
                            request.botJobId(),
                            action,
                            () -> BotJobWorkspaceController.getInstance()
                                    .workspaceAction(action, request.botJobId()))
                    .whenComplete((result, failure) -> {
                        if (failure != null) {
                            response.put("ok", false);
                            response.put("action", action.name());
                            response.put("message", failure.getMessage());
                        } else {
                            response.put("ok", result.ok());
                            response.put("action", result.action());
                            response.put("message", result.message());
                            if (result.ok()) {
                                response.put("activeSurface", result.activeSurface());
                                response.put("componentsVisible", result.componentsVisible());
                            }
                        }
                        sendBotJobDetailsResponse(
                                transportSession,
                                authoritativeHomeBankingId(request.botJobId()),
                                request.sessionId(),
                                response,
                                "botJobDetails.actionResponse");
                        if (failure == null && result.ok() && action != BotJobWorkspaceAction.CLOSE) {
                            publishBotJobDetailsStateAsync(request);
                        }
                    });
        } catch (Exception error) {
            addBotJobDetailsCorrelation(response, envelope);
            addBotJobDetailsAction(response, envelope);
            response.put("ok", false);
            response.put("message", error.getMessage());
            sendBotJobDetailsResponse(
                    transportSession,
                    -1,
                    transportSessionId(transportSession),
                    response,
                    "botJobDetails.actionResponse");
        }
    }

    private void handleBotJobDetailsToolbarAction(JsonObject envelope, Session transportSession) {
        Map<String, Object> response = new LinkedHashMap<>();
        try {
            BotJobDetailsRequest request = parseBotJobDetailsRequest(envelope, transportSession);
            response.put("requestId", request.requestId());
            response.put("botJobId", request.botJobId());
            BotJobToolbarAction action = BotJobToolbarAction.parse(
                    request.body().has("action") ? request.body().get("action").getAsString() : null);
            if (ScannerWorkspaceSessions.isPageScannerSession(request.sessionId())
                    && !isAllowedDetachedPageScannerToolbarAction(action.name())) {
                throw new IllegalArgumentException(
                        "Only block refresh, TEST RUN, and STOP are allowed from Page Scanner");
            }
            botJobDetailsToolbarLedger
                    .executeOnce(
                            request.sessionId(),
                            request.requestId(),
                            request.botJobId(),
                            action,
                            request.body().toString(),
                            () -> BotJobWorkspaceController.getInstance().toolbarAction(action, request))
                    .whenComplete((result, failure) -> {
                        if (failure != null) {
                            response.put("ok", false);
                            response.put("action", action.name());
                            response.put("message", failure.getMessage());
                        } else {
                            response.put("ok", result.ok());
                            response.put("action", result.action());
                            response.put("message", result.message());
                            if (!Strings.isNullOrEmpty(result.selectedPath())) {
                                response.put("selectedPath", result.selectedPath());
                            }
                        }
                        sendBotJobDetailsResponse(
                                transportSession,
                                authoritativeHomeBankingId(request.botJobId()),
                                request.sessionId(),
                                response,
                                "botJobDetails.toolbar.actionResponse");
                        if (failure == null) publishBotJobDetailsStateAsync(request);
                    });
        } catch (Exception error) {
            addBotJobDetailsCorrelation(response, envelope);
            addBotJobDetailsToolbarAction(response, envelope);
            response.put("ok", false);
            response.put("message", error.getMessage());
            sendBotJobDetailsResponse(
                    transportSession,
                    -1,
                    transportSessionId(transportSession),
                    response,
                    "botJobDetails.toolbar.actionResponse");
        }
    }

    private void handleBotJobDetailsBootstrap(JsonObject envelope, Session transportSession) {
        try {
            BotJobDetailsRequest request = parseBotJobDetailsRequest(envelope, transportSession);
            BotJobDetailsResponse response = botJobDetailsService.bootstrap(request);
            sendBotJobDetailsBootstrap(transportSession, request, response);
        } catch (Exception error) {
            sendBotJobDetailsParseFailure(
                    transportSession,
                    envelope,
                    "botJobDetails.bootstrapResponse",
                    error.getMessage());
        }
    }

    void sendBotJobDetailsBootstrap(
            Session transportSession,
            BotJobDetailsRequest request,
            BotJobDetailsResponse response) {
        sendBotJobDetailsResponseAcknowledged(
                        transportSession,
                        authoritativeHomeBankingId(response),
                        request.sessionId(),
                        response,
                        "botJobDetails.bootstrapResponse")
                .whenComplete((ignored, sendFailure) -> {
                    if (sendFailure != null) {
                        log.error(
                                "Unable to send Bot Job Details bootstrap response to session {}",
                                request.sessionId(),
                                sendFailure);
                        return;
                    }
                    if (!response.ok()) return;
                    if (ScannerWorkspaceSessions.isPageScannerSession(request.sessionId())) return;
                    try {
                        BotJobWorkspaceController.getInstance()
                                .publishGridBootstrap(request.sessionId(), request.botJobId());
                    } catch (RuntimeException gridFailure) {
                        log.error(
                                "Unable to publish Bot Job instruction grid to session {}",
                                request.sessionId(),
                                gridFailure);
                    }
                });
    }

    private void handleBotJobDetailsEnvironmentRefresh(JsonObject envelope, Session transportSession) {
        try {
            BotJobDetailsRequest request = parseBotJobDetailsRequest(envelope, transportSession);
            BotJobDetailsResponse response = botJobDetailsMutationLedger.executeOnce(
                    request,
                    "environments.refresh",
                    request.body().toString(),
                    () -> botJobDetailsService.refreshEnvironments(request));
            sendBotJobDetailsResponse(
                    transportSession,
                    authoritativeHomeBankingId(response),
                    request.sessionId(),
                    response,
                    "botJobDetails.environments.refreshResponse");
            if (response.ok()) publishBotJobDetailsState(response, request.requestId());
        } catch (Exception error) {
            sendBotJobDetailsParseFailure(
                    transportSession,
                    envelope,
                    "botJobDetails.environments.refreshResponse",
                    error.getMessage());
        }
    }

    private void handleBotJobDetailsMetadataUpdate(JsonObject envelope, Session transportSession) {
        BotJobDetailsRequest request;
        try {
            request = parseBotJobDetailsRequest(envelope, transportSession);
        } catch (Exception error) {
            sendBotJobDetailsParseFailure(
                    transportSession,
                    envelope,
                    "botJobDetails.metadata.updateResponse",
                    error.getMessage());
            return;
        }

        final BotJobDetailsResponse response;
        try {
            response = botJobDetailsMutationLedger.executeOnce(
                    request,
                    "metadata.update",
                    request.body().toString(),
                    () -> botJobDetailsService.updateMetadata(request));
        } catch (RuntimeException error) {
            BotJobDetailsResponse failureResponse = BotJobDetailsResponse.failure(
                    Strings.isNullOrEmpty(error.getMessage())
                            ? "Bot Job details could not be saved"
                            : error.getMessage(),
                    "METADATA_UPDATE_FAILED",
                    request,
                    null,
                    Map.of());
            sendBotJobDetailsResponse(
                    transportSession,
                    authoritativeHomeBankingId(request.botJobId()),
                    request.sessionId(),
                    failureResponse,
                    "botJobDetails.metadata.updateResponse");
            return;
        }
        if (!response.ok() || response.state() == null) {
            sendBotJobDetailsResponse(
                    transportSession,
                    authoritativeHomeBankingId(response),
                    request.sessionId(),
                    response,
                    "botJobDetails.metadata.updateResponse");
            return;
        }

        CompletableFuture<Void> desktopSync;
        try {
            desktopSync = BotJobWorkspaceController.getInstance().applyMetadata(response.state());
        } catch (RuntimeException error) {
            BotJobDetailsResponse syncFailure = BotJobDetailsResponse.failure(
                    "Metadata was saved but the open desktop context could not be synchronized",
                    "DESKTOP_STATE_SYNC_FAILED",
                    request,
                    response.state(),
                    Map.of());
            sendBotJobDetailsResponse(
                    transportSession,
                    authoritativeHomeBankingId(response),
                    request.sessionId(),
                    syncFailure,
                    "botJobDetails.metadata.updateResponse");
            return;
        }

        desktopSync.whenComplete((ignored, applyFailure) -> {
            Object outgoing = response;
            if (applyFailure != null) {
                outgoing = BotJobDetailsResponse.failure(
                        "Metadata was saved but the open desktop context could not be synchronized",
                        "DESKTOP_STATE_SYNC_FAILED",
                        request,
                        response.state(),
                        Map.of());
            }
            sendBotJobDetailsResponse(
                    transportSession,
                    authoritativeHomeBankingId(response),
                    request.sessionId(),
                    outgoing,
                    "botJobDetails.metadata.updateResponse");
            if (applyFailure == null) {
                publishBotJobDetailsState(response, request.requestId());
            }
        });
    }

    private void handleBotJobDetailsStateRequest(
            JsonObject envelope,
            Session transportSession,
            String operationId,
            java.util.function.Function<BotJobDetailsRequest, BotJobDetailsResponse> operation) {
        try {
            BotJobDetailsRequest request = parseBotJobDetailsRequest(envelope, transportSession);
            BotJobDetailsResponse response = operation.apply(request);
            sendBotJobDetailsResponse(
                    transportSession,
                    authoritativeHomeBankingId(response),
                    request.sessionId(),
                    response,
                    operationId);
            if (response.ok() && !"botJobDetails.bootstrapResponse".equals(operationId)) {
                publishBotJobDetailsState(response, request.requestId());
            }
        } catch (Exception error) {
            sendBotJobDetailsParseFailure(transportSession, envelope, operationId, error.getMessage());
        }
    }

    private BotJobDetailsRequest parseBotJobDetailsRequest(JsonObject envelope, Session transportSession) {
        BotJobDetailsRequest request =
                BotJobDetailsRequest.parse(envelope, transportSessionId(transportSession));
        if (ScannerWorkspaceSessions.isPageScannerSession(request.sessionId())) {
            PageScannerWorkspaceCoordinator.BootstrapContext workspace =
                    pageScannerWorkspaceCoordinator.bootstrap(request.sessionId());
            if (workspace.context().botJobId() != request.botJobId()) {
                throw new IllegalArgumentException(
                        "Page Scanner Bot Job does not match its active workspace");
            }
        }
        return request;
    }

    private void handleScannerBootstrap(JsonObject envelope, Session transportSession) {
        handleScannerRequest(
                envelope,
                transportSession,
                ScannerWorkspaceOperations.BOOTSTRAP_RESPONSE,
                scannerWorkspaceService::bootstrap);
    }

    private void handleScannerAction(JsonObject envelope, Session transportSession) {
        handleScannerRequest(
                envelope,
                transportSession,
                ScannerWorkspaceOperations.ACTION_RESPONSE,
                scannerWorkspaceService::action);
    }

    private void handleScannerRequest(
            JsonObject envelope,
            Session transportSession,
            String operationId,
            java.util.function.Function<ScannerWorkspaceRequest, ScannerWorkspaceResponse> operation) {
        try {
            ScannerWorkspaceRequest request =
                    ScannerWorkspaceRequest.parse(envelope, transportSessionId(transportSession));
            ScannerWorkspaceResponse response = scannerWorkspaceRequestLedger.executeOnce(
                    request, operationId, () -> operation.apply(request));
            sendBotJobDetailsResponse(
                    transportSession,
                    scannerHomeBankingId(response),
                    request.sessionId(),
                    response,
                    operationId);
            if (response.ok()) {
                publishScannerState(response, request.sessionId(), request.requestId());
            }
        } catch (Exception error) {
            sendScannerParseFailure(transportSession, envelope, operationId, error.getMessage());
        }
    }

    private void publishScannerState(ScannerWorkspaceResponse response, String sessionId, String causeRequestId) {
        if (response == null || response.state() == null) return;
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("ok", true);
        event.put("message", response.message());
        event.put("requestId", causeRequestId == null ? "" : causeRequestId);
        event.put("botJobId", response.botJobId());
        event.put("state", response.state());
        Session target = WebSocketSessionManager.getSession(sessionId);
        if (target != null && target.isOpen()) {
            sendBotJobDetailsResponse(
                    target,
                    response.state().homeBankingId(),
                    sessionId,
                    event,
                    ScannerWorkspaceOperations.STATE_EVENT);
        }
    }

    private int scannerHomeBankingId(ScannerWorkspaceResponse response) {
        return response != null && response.state() != null ? response.state().homeBankingId() : -1;
    }

    private void sendScannerParseFailure(
            Session session, JsonObject envelope, String operationId, String message) {
        Map<String, Object> response = scannerParseFailureResponse(envelope, message);
        sendBotJobDetailsResponse(session, -1, transportSessionId(session), response, operationId);
    }

    Map<String, Object> scannerParseFailureResponse(JsonObject envelope, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        ScannerWorkspaceRequest.Correlation correlation = ScannerWorkspaceRequest.correlation(envelope);
        if (!correlation.requestId().isBlank()) response.put("requestId", correlation.requestId());
        response.put("botJobId", correlation.botJobId() > 0 ? correlation.botJobId() : -1);
        String action = scannerAction(envelope);
        if (!action.isBlank()) response.put("action", action);
        response.put("ok", false);
        response.put("message", Strings.isNullOrEmpty(message) ? "Invalid Scanner request" : message);
        response.put("errorCode", "INVALID_SCANNER_REQUEST");
        return response;
    }

    private String transportSessionId(Session transportSession) {
        String sessionId = webSocketSessionManager.getSessionIdBySession(transportSession);
        return Strings.isNullOrEmpty(sessionId) ? "" : sessionId;
    }

    private void sendBotJobDetailsParseFailure(
            Session session, JsonObject envelope, String operationId, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        addBotJobDetailsCorrelation(response, envelope);
        response.put("ok", false);
        response.put("message", Strings.isNullOrEmpty(message) ? "Invalid Bot Job Details request" : message);
        sendBotJobDetailsResponse(session, -1, transportSessionId(session), response, operationId);
    }

    private void addBotJobDetailsCorrelation(Map<String, Object> response, JsonObject envelope) {
        BotJobDetailsRequest.Correlation correlation = BotJobDetailsRequest.correlation(envelope);
        if (!correlation.requestId().isBlank()) response.putIfAbsent("requestId", correlation.requestId());
        if (correlation.botJobId() > 0) response.putIfAbsent("botJobId", correlation.botJobId());
    }

    private String botJobDetailsAction(JsonObject envelope) {
        try {
            JsonObject body = extractBody(envelope);
            if (body == null || !body.has("action") || body.get("action").isJsonNull()) return "";
            return body.get("action").getAsString().trim().toUpperCase(java.util.Locale.ROOT);
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private String scannerAction(JsonObject envelope) {
        try {
            JsonObject body = extractBody(envelope);
            if (body == null || !body.has("action") || body.get("action").isJsonNull()) return "";
            String action = body.get("action").getAsString().trim().toUpperCase(java.util.Locale.ROOT);
            ScannerWorkspaceAction.parse(action);
            return action;
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private void addBotJobDetailsAction(Map<String, Object> response, JsonObject envelope) {
        String action = botJobDetailsAction(envelope);
        if (!action.isBlank()) response.putIfAbsent("action", action);
    }

    private String botJobDetailsToolbarAction(JsonObject envelope) {
        return botJobDetailsAction(envelope);
    }

    private void addBotJobDetailsToolbarAction(Map<String, Object> response, JsonObject envelope) {
        addBotJobDetailsAction(response, envelope);
    }

    private void sendBotJobDetailsLicenseFailure(JsonObject envelope, Session session, String requestType) {
        String operationId = switch (requestType) {
            case "botJobDetails.action" -> "botJobDetails.actionResponse";
            case "botJobDetails.toolbar.action" -> "botJobDetails.toolbar.actionResponse";
            case "botJobDetails.bootstrap" -> "botJobDetails.bootstrapResponse";
            case "botJobDetails.metadata.update" -> "botJobDetails.metadata.updateResponse";
            case "botJobDetails.environments.refresh" -> "botJobDetails.environments.refreshResponse";
            default -> "botJobDetails.actionResponse";
        };
        Map<String, Object> response = new LinkedHashMap<>();
        addBotJobDetailsCorrelation(response, envelope);
        if ("botJobDetails.action".equals(requestType)
                || "botJobDetails.toolbar.action".equals(requestType)) {
            addBotJobDetailsAction(response, envelope);
        }
        response.put("ok", false);
        response.put("message", "An active license is required for this Bot Job Details operation");
        response.put("errorCode", "LICENSE_REQUIRED");
        sendBotJobDetailsResponse(session, -1, transportSessionId(session), response, operationId);
    }

    private void handleBotJobExecutionPauseResponse(JsonObject envelope, Session session) {
        String transportSessionId = transportSessionId(session);
        if (!isBotJobExecutionPauseTransport(transportSessionId)) {
            throw new IllegalArgumentException("PAUSE confirmation must come from Bot Job Details");
        }
        JsonObject body = extractBody(envelope);
        ExecutionPauseCoordinator.PauseResponse response = new ExecutionPauseCoordinator.PauseResponse(
                requiredString(body, "requestId", "PAUSE requestId is required"),
                requiredPositiveInt(body, "botJobId", "PAUSE Bot Job ID must be positive"),
                requiredPositiveLong(body, "workspaceEpoch", "PAUSE workspace epoch must be positive"),
                requiredPositiveLong(body, "executionId", "PAUSE execution ID must be positive"),
                requiredNonNegativeLong(
                        body, "executionAttemptId", "PAUSE execution attempt cannot be negative"),
                requiredString(body, "decision", "PAUSE decision is required"));
        ExecutionPauseCoordinator.ResponseResult result = executionPauseCoordinator.respond(response);
        Map<String, Object> acknowledgement = new LinkedHashMap<>();
        acknowledgement.put("requestId", response.requestId());
        acknowledgement.put("botJobId", response.botJobId());
        acknowledgement.put("ok", result.accepted());
        acknowledgement.put("decision", result.decision() == null ? null : result.decision().name());
        acknowledgement.put("message", result.message());
        sendBotJobDetailsResponse(
                session,
                -1,
                transportSessionId,
                acknowledgement,
                "botJobExecution.pause.responseAck");
    }

    private static String requiredString(JsonObject body, String field, String message) {
        String value = stringValue(body, field);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }

    private static int requiredPositiveInt(JsonObject body, String field, String message) {
        long value = requiredPositiveLong(body, field, message);
        if (value > Integer.MAX_VALUE) throw new IllegalArgumentException(message);
        return (int) value;
    }

    private static long requiredPositiveLong(JsonObject body, String field, String message) {
        long value = requiredNonNegativeLong(body, field, message);
        if (value <= 0) throw new IllegalArgumentException(message);
        return value;
    }

    private static long requiredNonNegativeLong(JsonObject body, String field, String message) {
        try {
            if (body == null || !body.has(field) || body.get(field).isJsonNull()) {
                throw new IllegalArgumentException(message);
            }
            long value = body.get(field).getAsLong();
            if (value < 0) throw new IllegalArgumentException(message);
            return value;
        } catch (IllegalArgumentException invalid) {
            throw invalid;
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException(message, invalid);
        }
    }

    private void sendBotJobDetailsResponse(
            Session targetSession, int homeBankingId, String sessionId, Object response, String operationId) {
        sendBotJobDetailsResponseAcknowledged(
                        targetSession, homeBankingId, sessionId, response, operationId)
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        log.error("Unable to send Bot Job Details response to session {}", sessionId, error);
                    }
                });
    }

    CompletableFuture<Void> sendBotJobDetailsResponseAcknowledged(
            Session targetSession, int homeBankingId, String sessionId, Object response, String operationId) {
        Session activeSession = WebSocketSessionManager.getSession(sessionId);
        if (activeSession == null || !activeSession.isOpen()) {
            log.debug("Bot Job Details response session {} is unavailable", sessionId);
            return CompletableFuture.completedFuture(null);
        }
        if (!activeSession.equals(targetSession)) {
            log.debug("Bot Job Details response session {} has been superseded; using active transport", sessionId);
            targetSession = activeSession;
        }
        if (targetSession == null || !targetSession.isOpen()) {
            log.debug("Bot Job Details response session {} is unavailable", sessionId);
            return CompletableFuture.completedFuture(null);
        }
        JsonObject outbound = new JsonObject();
        outbound.addProperty("body", gson.toJson(response));
        outbound.addProperty("sessionId", sessionId);
        outbound.addProperty("homeBankingId", homeBankingId);
        outbound.addProperty("operationId", operationId);
        return WebSocketSessionManager.sendTextAcknowledged(activeSession, outbound.toString());
    }

    private int authoritativeHomeBankingId(int botJobId) {
        try {
            return botJobDetailsService.activeHomeBankingId(botJobId);
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    private int authoritativeHomeBankingId(BotJobDetailsResponse response) {
        return response != null && response.state() != null ? response.state().homeBankingId() : -1;
    }

    private void publishBotJobDetailsStateAsync(BotJobDetailsRequest request) {
        CompletableFuture.runAsync(() -> {
            try {
                BotJobDetailsResponse response = BotJobDetailsResponse.success(
                        "Bot Job Details state changed",
                        request,
                        botJobDetailsService.currentState(request.botJobId()));
                publishBotJobDetailsState(response, request.requestId());
            } catch (RuntimeException error) {
                log.debug("Unable to publish Bot Job Details state: {}", error.getMessage());
            }
        });
    }

    private void publishBotJobDetailsState(BotJobDetailsResponse response, String causeRequestId) {
        if (response == null || response.state() == null) return;
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("ok", true);
        event.put("message", response.message());
        event.put("requestId", causeRequestId);
        event.put("botJobId", response.botJobId());
        event.put("state", response.state());
        for (String targetId : botJobDetailsStateTargets(response.botJobId())) {
            Session target = WebSocketSessionManager.getSession(targetId);
            if (target != null && target.isOpen()) {
                sendBotJobDetailsResponse(
                        target,
                        response.state().homeBankingId(),
                        targetId,
                        event,
                        "botJobDetails.state");
            }
        }
    }

    /** Publishes a terminal runtime transition produced after the original toolbar request completed. */
    public void publishBotJobDetailsRuntimeState(int botJobId, String causeRequestId) {
        try {
            publishBotJobDetailsRuntimeStateStrict(botJobId, causeRequestId);
        } catch (RuntimeException error) {
            log.debug("Unable to publish Bot Job execution state: {}", error.getMessage());
        }
    }

    /**
     * Publishes and acknowledges a Bot Job runtime transition. Failures are propagated so lifecycle
     * owners can retry instead of assuming an asynchronous WebSocket send succeeded.
     */
    public void publishBotJobDetailsRuntimeStateStrict(int botJobId, String causeRequestId) {
        BotJobDetailsState state = botJobDetailsService.currentState(botJobId);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("ok", true);
        event.put("message", "Bot Job execution state changed");
        event.put("requestId", causeRequestId == null ? "" : causeRequestId);
        event.put("botJobId", botJobId);
        event.put("state", state);
        List<CompletableFuture<Void>> sends = new ArrayList<>();
        for (String targetId : botJobDetailsStateTargets(botJobId)) {
            Session target = WebSocketSessionManager.getSession(targetId);
            if (target != null && target.isOpen()) {
                sends.add(sendBotJobDetailsResponseAcknowledged(
                        target,
                        state.homeBankingId(),
                        targetId,
                        event,
                        "botJobDetails.state"));
            }
        }
        CompletableFuture.allOf(sends.toArray(CompletableFuture[]::new))
                .orTimeout(2L, TimeUnit.SECONDS)
                .join();
    }

    private Set<String> botJobDetailsStateTargets(int botJobId) {
        Set<String> targets = new LinkedHashSet<>(BotJobWorkspaceSessions.stateTargets());
        pageScannerWorkspaceCoordinator.activeSessionIdForBotJob(botJobId).ifPresent(targets::add);
        return targets;
    }

    private void handleNewBotJobBootstrap(String sessionId) {
        sendNewBotJobResponse(sessionId, newBotJobService.bootstrap(), "newBotJob.bootstrapResponse");
    }

    private void handleNewBotJobEnvironments(String sessionId) {
        sendNewBotJobResponse(sessionId, newBotJobService.environments(), "newBotJob.environmentsResponse");
    }

    private void handleNewBotJobCreate(JsonObject jsonObjMSG, String sessionId) {
        sendNewBotJobResponse(sessionId, newBotJobService.create(extractBody(jsonObjMSG)), "newBotJob.createResponse");
    }

    private void handleNewBotJobOpenOrganizations(String sessionId) {
        sendNewBotJobResponse(sessionId, newBotJobService.openOrganizations(), "newBotJob.actionResponse");
    }

    private void handleNewBotJobCancel(String sessionId) {
        sendNewBotJobResponse(sessionId, newBotJobService.cancel(), "newBotJob.actionResponse");
    }

    private void sendNewBotJobResponse(String sessionId, Object response, String operationId) {
        webSocketSessionManager.sendMessageJson(-1, sessionId, gson.toJson(response), operationId);
    }

    private void handleConfigBootstrap(String sessionId) {
        sendConfigResponse(sessionId, configService.bootstrap(), "config.bootstrapResponse");
    }

    private void handleConfigChoosePath(
            JsonObject jsonObjMSG, String sessionId, Session transport) {
        if (!isConfigurationReloadRequester(sessionId, transport)) {
            sendConfigResponse(
                    transport,
                    sessionId,
                    Map.of("ok", false, "message", "Path selection requires Config or TEMP."),
                    "config.pathResponse");
            return;
        }
        sendConfigResponse(
                transport,
                sessionId,
                configService.choosePath(extractBody(jsonObjMSG)),
                "config.pathResponse");
    }

    private void handleConfigSave(JsonObject jsonObjMSG, String sessionId, Session transport) {
        if (!isConfigurationReloadRequester(sessionId, transport)) {
            sendConfigResponse(
                    transport,
                    sessionId,
                    Map.of("ok", false, "message", "Configuration save requires Config or TEMP."),
                    "config.saveResponse");
            return;
        }
        if (!BotJobDetailsWorkspaceHost.getInstance().canCloseWorkspace()) {
            sendConfigResponse(
                    transport,
                    sessionId,
                    Map.of(
                            "ok",
                            false,
                            "message",
                            "Stop the active Bot Job operation before saving configuration."),
                    "config.saveResponse");
            return;
        }
        Map<String, Object> response = configService.save(extractBody(jsonObjMSG));
        closePagesAfterSuccessfulConfigReload(sessionId, transport, response);
        sendConfigResponse(transport, sessionId, response, "config.saveResponse");
    }

    private void handleConfigBackup(
            JsonObject jsonObjMSG, String sessionId, Session transport) {
        if (!isConfigurationReloadRequester(sessionId, transport)) {
            sendConfigResponse(
                    transport,
                    sessionId,
                    Map.of("ok", false, "message", "Database backup requires Config or TEMP."),
                    "config.backupResponse");
            return;
        }
        sendConfigResponse(
                transport,
                sessionId,
                configService.backup(extractBody(jsonObjMSG)),
                "config.backupResponse");
    }

    private void handleConfigBrowserUpdate(
            JsonObject jsonObjMSG, String sessionId, Session transport) {
        if (!isConfigurationReloadRequester(sessionId, transport)) {
            sendConfigResponse(
                    transport,
                    sessionId,
                    Map.of("ok", false, "message", "Browser selection requires Config or TEMP."),
                    "config.browserResponse");
            return;
        }

        Map<String, Object> response = configService.updateBrowser(extractBody(jsonObjMSG));
        sendConfigResponse(
                transport,
                sessionId,
                response,
                "config.browserResponse");
        if (Boolean.TRUE.equals(response.get("ok"))) {
            broadcastBrowserConfig(response);
        }
    }

    private void handleConfigRestore(JsonObject jsonObjMSG, String sessionId, Session transport) {
        if (!isConfigurationReloadRequester(sessionId, transport)) {
            sendConfigResponse(
                    transport,
                    sessionId,
                    Map.of("ok", false, "message", "Configuration restore requires Config or TEMP."),
                    "config.restoreResponse");
            return;
        }
        if (!BotJobDetailsWorkspaceHost.getInstance().canCloseWorkspace()) {
            sendConfigResponse(
                    transport,
                    sessionId,
                    Map.of(
                            "ok",
                            false,
                            "message",
                            "Stop the active Bot Job operation before restoring configuration."),
                    "config.restoreResponse");
            return;
        }
        Map<String, Object> response = configService.restore(extractBody(jsonObjMSG));
        closePagesAfterSuccessfulConfigReload(sessionId, transport, response);
        sendConfigResponse(transport, sessionId, response, "config.restoreResponse");
    }

    private void closePagesAfterSuccessfulConfigReload(
            String requesterSessionId, Session requesterTransport, Map<String, Object> response) {
        if (!Boolean.TRUE.equals(response.get("ok"))) return;
        if (!isConfigurationReloadRequester(requesterSessionId, requesterTransport)) {
            response.put(
                    "workspaceCloseWarning",
                    "Configuration reloaded, but the requesting Config/TEMP page is no longer authoritative.");
            return;
        }
        try {
            response.put(
                    "closedPages",
                    pagesOpenWorkspaceService.closeForDatabaseReload(requesterSessionId));
        } catch (IllegalArgumentException | IllegalStateException closeFailure) {
            log.warn(
                    "Configuration reload completed, but open pages could not all be closed: {}",
                    closeFailure.getMessage());
            response.put("workspaceCloseWarning", closeFailure.getMessage());
        }
    }

    private boolean isConfigurationReloadRequester(String sessionId, Session transport) {
        return (DetachedWorkspaceSessions.CONFIG_MANAGER.equals(sessionId)
                        || DetachedWorkspaceSessions.A_TEMPLATE_MANAGER.equals(sessionId))
                && transport != null
                && transport.isOpen()
                && WebSocketSessionManager.getSession(sessionId) == transport;
    }

    private void broadcastBrowserConfig(Map<String, Object> response) {
        for (String targetSessionId : List.of(
                DetachedWorkspaceSessions.CONFIG_MANAGER,
                DetachedWorkspaceSessions.A_TEMPLATE_MANAGER)) {
            Session target = WebSocketSessionManager.getSession(targetSessionId);
            if (target == null || !target.isOpen()) continue;
            sendConfigResponse(
                    target,
                    targetSessionId,
                    response,
                    "config.browserUpdated");
        }
    }

    private void handleConfigDeleteAllJobs(JsonObject jsonObjMSG, String sessionId) {
        sendConfigResponse(sessionId, configService.deleteAllJobs(extractBody(jsonObjMSG)), "config.deleteResponse");
    }

    private void handleConfigOpenOrganizations(String sessionId) {
        sendConfigResponse(sessionId, configService.openOrganizations(), "config.actionResponse");
    }

    private void handleConfigLoadGenFlowPrompt(String sessionId) {
        sendConfigResponse(sessionId, configService.loadGenFlowPrompt(), "config.promptResponse");
    }

    private void handleConfigSaveGenFlowPrompt(JsonObject jsonObjMSG, String sessionId) {
        sendConfigResponse(sessionId, configService.saveGenFlowPrompt(extractBody(jsonObjMSG)), "config.promptResponse");
    }

    private void handleConfigCancel(String sessionId) {
        sendConfigResponse(sessionId, configService.cancel(), "config.actionResponse");
    }

    private void sendConfigResponse(String sessionId, Object response, String operationId) {
        webSocketSessionManager.sendMessageJson(-1, sessionId, gson.toJson(response), operationId);
    }

    private void sendConfigResponse(
            Session transport, String sessionId, Object response, String operationId) {
        WebSocketSessionManager.sendMessageJson(
                -1, transport, sessionId, gson.toJson(response), operationId);
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        String sessionId = webSocketSessionManager.getSessionIdBySession(session);
        log.error(
                "Error in session {}: {}",
                session == null ? "unknown" : session.getId(),
                throwable == null ? "unknown" : throwable.getMessage());
        try {
            if (session != null && session.isOpen()) {
                session.close();
            }
        } catch (IOException e) {
            log.error("Error closing session: " + e.getMessage());
        } finally {
            if (!Strings.isNullOrEmpty(sessionId)) {
                if (webSocketSessionManager.removeSession(sessionId, session)) {
                    BotJobTransferPathRegistry.getInstance().clearSession(sessionId);
                    notifyBotJobWindowDisconnected(sessionId);
                    notifyPageScannerWindowDisconnected(sessionId);
                    notifyOcrWindowDisconnected(sessionId);
                    notifyMainApplicationDisconnected(sessionId);
                    commandEditorWorkspaceService.disconnected(sessionId, session);
                    pagesOpenWorkspaceService.sessionRegistryChanged();
                }
            }
        }
    }

    /**
     * Lightweight read-only handler for the Functional Test mapping tab in MultiTest.
     * Pulls INPUT-text instructions (actions LIKE 'I:%') for a single bot job and
     * returns them on the same socket session that asked.
     *
     * Inbound shape (from React):
     *   { "type": "botJob.getInputInstructions",
     *     "sessionId": "funcTest-...",        // target for the response
     *     "body": "{ \"botJobId\": 42 }" }    // body is a stringified JSON object
     *
     * Outbound type back to the client: "botJob.inputInstructions".
     */
    private void handleBotJobInputInstructions(JsonObject jsonObjMSG, String sessionId, int homeBankingId) {
        try {
            int botJobId = -1;
            if (jsonObjMSG.has("body")) {
                var bodyEl = jsonObjMSG.get("body");
                JsonObject bodyObj = null;
                if (bodyEl.isJsonPrimitive() && bodyEl.getAsJsonPrimitive().isString()) {
                    bodyObj = JsonParser.parseString(bodyEl.getAsString()).getAsJsonObject();
                } else if (bodyEl.isJsonObject()) {
                    bodyObj = bodyEl.getAsJsonObject();
                }
                if (bodyObj != null && bodyObj.has("botJobId")) {
                    botJobId = bodyObj.get("botJobId").getAsInt();
                }
            }
            // Fallback: top-level botJobId field
            if (botJobId <= 0 && jsonObjMSG.has("botJobId")) {
                botJobId = jsonObjMSG.get("botJobId").getAsInt();
            }

            List<Map<String, Object>> rows = performDataBase.loadInputTextInstructionsForBotJob(botJobId);
            String json = gson.toJson(rows);
            webSocketSessionManager.sendMessageJson(homeBankingId, sessionId, json, "botJob.inputInstructions");
        } catch (Exception e) {
            log.error("handleBotJobInputInstructions failed: {}", e.getMessage());
            webSocketSessionManager.sendMessageJson(homeBankingId, sessionId, "[]", "botJob.inputInstructions");
        }
    }

    /**
     * Functional Test tab — load all persisted field mappings for a use case
     * (Phase 1a). If body carries {@code useCaseId}, load that one; otherwise
     * fall back to the Default use case for the given {@code botJobId}.
     *
     * Inbound:
     *   { "type": "funcTest.loadMappings", "sessionId": "funcTest-...",
     *     "body": "{ \"useCaseId\": 5 }" }                     // preferred
     *   { "type": "funcTest.loadMappings", "sessionId": "funcTest-...",
     *     "body": "{ \"botJobId\": 42 }" }                    // back-compat
     * Outbound type: "funcTest.mappingsLoaded".
     */
    private void handleFuncTestLoadMappings(JsonObject jsonObjMSG, String sessionId, int homeBankingId) {
        try {
            JsonObject body = extractBody(jsonObjMSG);
            int useCaseId = body != null && body.has("useCaseId")
                    ? body.get("useCaseId").getAsInt()
                    : -1;
            List<FieldMappingDTO> rows;
            if (useCaseId > 0) {
                rows = performDataBase.loadFieldMappingsForUseCase(useCaseId);
            } else {
                int botJobId = extractBotJobId(jsonObjMSG);
                rows = performDataBase.loadFieldMappings(botJobId);
            }
            String json = gson.toJson(rows);
            webSocketSessionManager.sendMessageJson(homeBankingId, sessionId, json, "funcTest.mappingsLoaded");
        } catch (Exception e) {
            log.error("handleFuncTestLoadMappings failed: {}", e.getMessage());
            webSocketSessionManager.sendMessageJson(homeBankingId, sessionId, "[]", "funcTest.mappingsLoaded");
        }
    }

    /**
     * Functional Test tab — replace all field mappings for a use case (or for
     * the bot job's Default use case if useCaseId is missing). Wipes-and-inserts.
     *
     * Inbound:
     *   { "type": "funcTest.saveMappings", "sessionId": "funcTest-...",
     *     "body": "{ \"botJobId\": 42, \"useCaseId\": 5, \"mappings\": [ ... ] }" }
     * Outbound type: "funcTest.mappingsSaved" — body { ok, count, botJobId, useCaseId }.
     */
    private void handleFuncTestSaveMappings(JsonObject jsonObjMSG, String sessionId, int homeBankingId) {
        try {
            JsonObject body = extractBody(jsonObjMSG);
            int botJobId =
                    body != null && body.has("botJobId") ? body.get("botJobId").getAsInt() : -1;
            int useCaseId = body != null && body.has("useCaseId")
                    ? body.get("useCaseId").getAsInt()
                    : -1;
            List<FieldMappingDTO> mappings = new ArrayList<>();
            if (body != null && body.has("mappings") && body.get("mappings").isJsonArray()) {
                for (var el : body.getAsJsonArray("mappings")) {
                    mappings.add(gson.fromJson(el, FieldMappingDTO.class));
                }
            }
            boolean ok;
            if (useCaseId > 0) {
                ok = performDataBase.saveFieldMappingsForUseCase(botJobId, useCaseId, mappings);
            } else {
                ok = performDataBase.saveFieldMappings(botJobId, mappings);
                if (ok) useCaseId = performDataBase.ensureDefaultUseCase(botJobId);
            }
            JsonObject resp = new JsonObject();
            resp.addProperty("ok", ok);
            resp.addProperty("count", mappings.size());
            resp.addProperty("botJobId", botJobId);
            resp.addProperty("useCaseId", useCaseId);
            webSocketSessionManager.sendMessageJson(
                    homeBankingId, sessionId, gson.toJson(resp), "funcTest.mappingsSaved");
        } catch (Exception e) {
            log.error("handleFuncTestSaveMappings failed: {}", e.getMessage());
            JsonObject resp = new JsonObject();
            resp.addProperty("ok", false);
            resp.addProperty("error", e.getMessage());
            webSocketSessionManager.sendMessageJson(
                    homeBankingId, sessionId, gson.toJson(resp), "funcTest.mappingsSaved");
        }
    }

    /**
     * Use Case CRUD — list use cases for a bot job (Phase 1a of ROADMAP_9).
     *
     * Inbound:  { "type": "useCase.list", "sessionId": "...", "body": "{\"botJobId\":42}" }
     * Outbound: "useCase.listResponse" — body is JSON array of UseCaseDTO.
     *
     * If the bot job has no use cases yet AND has at least one mapping (legacy
     * pre-Phase-1a state), this also auto-creates the Default use case so the
     * UI always has at least one entry to show.
     */
    private void handleUseCaseList(JsonObject jsonObjMSG, String sessionId, int homeBankingId) {
        try {
            int botJobId = extractBotJobId(jsonObjMSG);
            List<UseCaseDTO> rows = performDataBase.loadUseCases(botJobId);
            if (rows.isEmpty() && botJobId > 0) {
                int defId = performDataBase.ensureDefaultUseCase(botJobId);
                if (defId > 0) {
                    rows = performDataBase.loadUseCases(botJobId);
                }
            }
            webSocketSessionManager.sendMessageJson(
                    homeBankingId, sessionId, gson.toJson(rows), "useCase.listResponse");
        } catch (Exception e) {
            log.error("handleUseCaseList failed: {}", e.getMessage());
            webSocketSessionManager.sendMessageJson(homeBankingId, sessionId, "[]", "useCase.listResponse");
        }
    }

    /**
     * Use Case CRUD — create or rename. Body must carry a UseCaseDTO; id null
     * = create, id non-null = update.
     *
     * Inbound:  { "type": "useCase.save", "sessionId": "...",
     *             "body": "{\"useCase\": {...UseCaseDTO...}}" }
     * Outbound: "useCase.saveResponse" — body { ok: bool, id: N|null, useCase: {...} }
     */
    private void handleUseCaseSave(JsonObject jsonObjMSG, String sessionId, int homeBankingId) {
        try {
            JsonObject body = extractBody(jsonObjMSG);
            UseCaseDTO dto =
                    body != null && body.has("useCase") ? gson.fromJson(body.get("useCase"), UseCaseDTO.class) : null;
            Integer id = performDataBase.saveUseCase(dto);
            JsonObject resp = new JsonObject();
            resp.addProperty("ok", id != null);
            if (id != null) {
                if (dto != null) dto.setId(id);
                resp.add("useCase", gson.toJsonTree(dto));
                resp.addProperty("id", id);
            }
            webSocketSessionManager.sendMessageJson(
                    homeBankingId, sessionId, gson.toJson(resp), "useCase.saveResponse");
        } catch (Exception e) {
            log.error("handleUseCaseSave failed: {}", e.getMessage());
            JsonObject resp = new JsonObject();
            resp.addProperty("ok", false);
            resp.addProperty("error", e.getMessage());
            webSocketSessionManager.sendMessageJson(
                    homeBankingId, sessionId, gson.toJson(resp), "useCase.saveResponse");
        }
    }

    /**
     * Use Case CRUD — delete a use case (cascades to its field mappings).
     *
     * Inbound:  { "type": "useCase.delete", "sessionId": "...", "body": "{\"useCaseId\":5}" }
     * Outbound: "useCase.deleteResponse" — body { ok: bool, useCaseId: N }
     */
    private void handleUseCaseDelete(JsonObject jsonObjMSG, String sessionId, int homeBankingId) {
        try {
            JsonObject body = extractBody(jsonObjMSG);
            int useCaseId = body != null && body.has("useCaseId")
                    ? body.get("useCaseId").getAsInt()
                    : -1;
            boolean ok = performDataBase.deleteUseCase(useCaseId);
            JsonObject resp = new JsonObject();
            resp.addProperty("ok", ok);
            resp.addProperty("useCaseId", useCaseId);
            webSocketSessionManager.sendMessageJson(
                    homeBankingId, sessionId, gson.toJson(resp), "useCase.deleteResponse");
        } catch (Exception e) {
            log.error("handleUseCaseDelete failed: {}", e.getMessage());
            JsonObject resp = new JsonObject();
            resp.addProperty("ok", false);
            resp.addProperty("error", e.getMessage());
            webSocketSessionManager.sendMessageJson(
                    homeBankingId, sessionId, gson.toJson(resp), "useCase.deleteResponse");
        }
    }

    private void handleOcrWorkspaceOpen(
            JsonObject envelope,
            int envelopeHomeBankingId,
            String claimedSessionId,
            String transportSessionId,
            Session transport) {
        JsonObject body = bodyOrEmpty(envelope);
        if (!validateOcrWorkspaceTransport(
                body,
                envelopeHomeBankingId,
                claimedSessionId,
                transportSessionId,
                transport,
                "ocrWorkspace.openResponse")) {
            return;
        }

        try {
            OcrWorkspaceCoordinator.Kind kind = OcrWorkspaceCoordinator.Kind.parse(stringValue(body, "kind"));
            int homeBankingId = intValue(body, "homeBankingId", envelopeHomeBankingId);
            int botJobId = intValue(body, "botJobId", extractBotJobId(envelope));
            Integer homeUrlId = optionalPositiveInt(body, "homeUrlId");
            JsonArray parameters = body.has("parameters") && body.get("parameters").isJsonArray()
                    ? body.getAsJsonArray("parameters")
                    : new JsonArray();
            OcrWorkspaceCoordinator.OpenResult result = ocrWorkspaceCoordinator.open(
                    new OcrWorkspaceCoordinator.OpenRequest(
                            kind,
                            transportSessionId,
                            homeBankingId,
                            botJobId,
                            homeUrlId,
                            parameters));

            JsonObject response = responseWithRequestId(body);
            response.addProperty("ok", result.ok());
            response.addProperty("kind", result.kind().routeValue());
            response.addProperty("sessionId", result.sessionId());
            response.addProperty("message", result.message());
            response.addProperty("expiresAt", result.expiresAt().toString());
            sendOcrWorkspaceResponse(
                    homeBankingId,
                    transportSessionId,
                    transport,
                    "ocrWorkspace.openResponse",
                    response);
        } catch (IllegalArgumentException | IllegalStateException invalidRequest) {
            sendOcrWorkspaceFailure(
                    body,
                    envelopeHomeBankingId,
                    transportSessionId,
                    transport,
                    "ocrWorkspace.openResponse",
                    invalidRequest.getMessage());
        } catch (RuntimeException failure) {
            log.error("Unable to open detached OCR workspace", failure);
            sendOcrWorkspaceFailure(
                    body,
                    envelopeHomeBankingId,
                    transportSessionId,
                    transport,
                    "ocrWorkspace.openResponse",
                    "Unable to open the OCR workspace.");
        }
    }

    private void handleOcrWorkspaceBootstrap(
            JsonObject envelope,
            int envelopeHomeBankingId,
            String claimedSessionId,
            String transportSessionId,
            Session transport) {
        JsonObject body = bodyOrEmpty(envelope);
        if (!validateOcrWorkspaceTransport(
                body,
                envelopeHomeBankingId,
                claimedSessionId,
                transportSessionId,
                transport,
                "ocrWorkspace.bootstrapResponse")) {
            return;
        }

        try {
            OcrWorkspaceCoordinator.BootstrapContext context =
                    ocrWorkspaceCoordinator.bootstrap(transportSessionId);
            JsonObject response = responseWithRequestId(body);
            response.addProperty("ok", true);
            response.addProperty("kind", context.kind().routeValue());
            response.addProperty("sessionId", context.sessionId());
            response.addProperty("homeBankingId", context.homeBankingId());
            response.addProperty("botJobId", context.botJobId());
            if (context.homeUrlId() != null) response.addProperty("homeUrlId", context.homeUrlId());
            response.add("parameters", context.parameters());
            response.addProperty("createdAt", context.createdAt().toString());
            response.addProperty("expiresAt", context.expiresAt().toString());
            sendOcrWorkspaceResponse(
                    context.homeBankingId(),
                    transportSessionId,
                    transport,
                    "ocrWorkspace.bootstrapResponse",
                    response);
        } catch (IllegalArgumentException | IllegalStateException invalidRequest) {
            sendOcrWorkspaceFailure(
                    body,
                    envelopeHomeBankingId,
                    transportSessionId,
                    transport,
                    "ocrWorkspace.bootstrapResponse",
                    invalidRequest.getMessage());
        } catch (RuntimeException failure) {
            log.error("Unable to bootstrap detached OCR workspace", failure);
            sendOcrWorkspaceFailure(
                    body,
                    envelopeHomeBankingId,
                    transportSessionId,
                    transport,
                    "ocrWorkspace.bootstrapResponse",
                    "Unable to load the OCR workspace.");
        }
    }

    private void handleOcrWorkspaceApplySuggestions(
            JsonObject envelope,
            int envelopeHomeBankingId,
            String claimedSessionId,
            String transportSessionId,
            Session transport) {
        JsonObject body = bodyOrEmpty(envelope);
        if (!validateOcrWorkspaceTransport(
                body,
                envelopeHomeBankingId,
                claimedSessionId,
                transportSessionId,
                transport,
                "ocrWorkspace.applySuggestionsResponse")) {
            return;
        }

        try {
            List<OcrWorkspaceCoordinator.Suggestion> suggestions = new ArrayList<>();
            if (body.has("suggestions") && body.get("suggestions").isJsonArray()) {
                for (var value : body.getAsJsonArray("suggestions")) {
                    if (!value.isJsonObject()) {
                        throw new IllegalArgumentException("OCR suggestions must be JSON objects");
                    }
                    JsonObject suggestion = value.getAsJsonObject();
                    suggestions.add(new OcrWorkspaceCoordinator.Suggestion(
                            stringValue(suggestion, "xPath"),
                            stringValue(suggestion, "clientNamed")));
                }
            }

            OcrWorkspaceCoordinator.ApplyResult result =
                    ocrWorkspaceCoordinator.applySuggestions(transportSessionId, suggestions);
            JsonObject response = responseWithRequestId(body);
            response.addProperty("ok", result.published());
            response.addProperty("published", result.published());
            response.addProperty("suggestionCount", result.suggestionCount());
            response.addProperty("message", result.message());
            sendOcrWorkspaceResponse(
                    envelopeHomeBankingId,
                    transportSessionId,
                    transport,
                    "ocrWorkspace.applySuggestionsResponse",
                    response);
        } catch (IllegalArgumentException | IllegalStateException invalidRequest) {
            sendOcrWorkspaceFailure(
                    body,
                    envelopeHomeBankingId,
                    transportSessionId,
                    transport,
                    "ocrWorkspace.applySuggestionsResponse",
                    invalidRequest.getMessage());
        } catch (RuntimeException failure) {
            log.error("Unable to apply detached OCR suggestions", failure);
            sendOcrWorkspaceFailure(
                    body,
                    envelopeHomeBankingId,
                    transportSessionId,
                    transport,
                    "ocrWorkspace.applySuggestionsResponse",
                    "Unable to apply the OCR suggestions.");
        }
    }

    private void handlePageScannerWorkspaceOpen(
            JsonObject envelope,
            int envelopeHomeBankingId,
            String claimedSessionId,
            String transportSessionId,
            Session transport) {
        JsonObject body = bodyOrEmpty(envelope);
        if (!validatePageScannerTransport(
                body,
                envelopeHomeBankingId,
                claimedSessionId,
                transportSessionId,
                transport,
                "pageScannerWorkspace.openResponse")) {
            return;
        }

        try {
            String requestId = requirePageScannerRequestId(body);
            int botJobId = requirePositivePageScannerInt(body, "botJobId");
            BotJobDetailsWorkspaceRegistry.Snapshot workspace =
                    BotJobDetailsWorkspaceRegistry.getInstance().require(botJobId);
            PreScanWorkflowService.Context scanContext =
                    BotJobWorkspaceController.getInstance().pageScannerContext(botJobId);
            if (workspace.homeBankingId() != scanContext.homeBankingId()) {
                throw new IllegalArgumentException("Page Scanner organization does not match the active Bot Job");
            }

            PageScannerWorkspaceCoordinator.WorkspaceContext context =
                    new PageScannerWorkspaceCoordinator.WorkspaceContext(
                            scanContext.homeBankingId(),
                            scanContext.botJobId(),
                            workspace.workspaceEpoch(),
                            scanContext.botJobName(),
                            scanContext.homeUrlId(),
                            scanContext.endpointUrl(),
                            scanContext.browserType(),
                            scanContext.optionsConfig(),
                            scanContext.jsonPath());
            PageScannerWorkspaceCoordinator.OpenResult result = pageScannerWorkspaceCoordinator.open(
                    new PageScannerWorkspaceCoordinator.OpenRequest(transportSessionId, context));

            JsonObject response = new JsonObject();
            response.addProperty("requestId", requestId);
            response.addProperty("ok", result.ok());
            response.addProperty("botJobId", botJobId);
            response.addProperty("launched", result.launched());
            response.addProperty("alreadyOpen", result.alreadyOpen());
            response.addProperty("sessionId", result.sessionId());
            response.addProperty("message", result.message());
            response.addProperty("expiresAt", result.expiresAt().toString());
            sendPageScannerResponse(
                    scanContext.homeBankingId(),
                    transportSessionId,
                    transport,
                    "pageScannerWorkspace.openResponse",
                    response);
        } catch (IllegalArgumentException | IllegalStateException invalidRequest) {
            sendPageScannerFailure(
                    body,
                    envelopeHomeBankingId,
                    transportSessionId,
                    transport,
                    "pageScannerWorkspace.openResponse",
                    invalidRequest.getMessage());
        } catch (RuntimeException failure) {
            log.error("Unable to open detached Page Scanner workspace", failure);
            sendPageScannerFailure(
                    body,
                    envelopeHomeBankingId,
                    transportSessionId,
                    transport,
                    "pageScannerWorkspace.openResponse",
                    "Unable to open the Page Scanner workspace.");
        }
    }

    private void handlePageScannerWorkspaceBootstrap(
            JsonObject envelope,
            int envelopeHomeBankingId,
            String claimedSessionId,
            String transportSessionId,
            Session transport) {
        JsonObject body = bodyOrEmpty(envelope);
        if (!validatePageScannerTransport(
                body,
                envelopeHomeBankingId,
                claimedSessionId,
                transportSessionId,
                transport,
                "pageScannerWorkspace.bootstrapResponse")) {
            return;
        }

        try {
            String requestId = requirePageScannerRequestId(body);
            PageScannerWorkspaceCoordinator.BootstrapContext workspace =
                    requireActivePageScannerWorkspace(transportSessionId);
            PreScanWorkflowService.Context scanContext = preScanContext(workspace.context());
            PageScannerWorkspaceCoordinator.WorkspaceContext context = workspace.context();

            JsonObject response = new JsonObject();
            response.addProperty("requestId", requestId);
            response.addProperty("ok", true);
            response.addProperty("sessionId", workspace.sessionId());
            response.addProperty("sourceSessionId", workspace.sourceBotJobSessionId());
            response.addProperty("homeBankingId", context.homeBankingId());
            response.addProperty("botJobId", context.botJobId());
            response.addProperty("botJobName", context.botJobName());
            if (context.homeUrlId() != null) response.addProperty("homeUrlId", context.homeUrlId());
            response.addProperty("mode", "preScan");
            response.addProperty("createdAt", workspace.createdAt().toString());
            response.addProperty("expiresAt", workspace.expiresAt().toString());
            sendPageScannerResponse(
                    context.homeBankingId(),
                    transportSessionId,
                    transport,
                    "pageScannerWorkspace.bootstrapResponse",
                    response);

            BotJobWorkspaceController.getInstance()
                    .pageScannerBootstrap(transportSessionId, scanContext);
        } catch (IllegalArgumentException | IllegalStateException invalidRequest) {
            sendPageScannerFailure(
                    body,
                    envelopeHomeBankingId,
                    transportSessionId,
                    transport,
                    "pageScannerWorkspace.bootstrapResponse",
                    invalidRequest.getMessage());
        } catch (RuntimeException failure) {
            log.error("Unable to bootstrap detached Page Scanner workspace", failure);
            sendPageScannerFailure(
                    body,
                    envelopeHomeBankingId,
                    transportSessionId,
                    transport,
                    "pageScannerWorkspace.bootstrapResponse",
                    "Unable to load the Page Scanner workspace.");
        }
    }

    private void handlePageScannerCommand(
            String operation,
            JsonObject envelope,
            int envelopeHomeBankingId,
            String claimedSessionId,
            String transportSessionId,
            Session transport) {
        JsonObject body = bodyOrEmpty(envelope);
        String responseOperation = pageScannerResponseOperation(operation);
        if (!validatePageScannerTransport(
                body,
                envelopeHomeBankingId,
                claimedSessionId,
                transportSessionId,
                transport,
                responseOperation)) {
            return;
        }

        try {
            String requestId = requirePageScannerRequestId(body);
            PageScannerWorkspaceCoordinator.BootstrapContext workspace =
                    requireActivePageScannerWorkspace(transportSessionId);
            PreScanWorkflowService.Context scanContext = preScanContext(workspace.context());
            String legacyOperation = switch (operation) {
                case "pageScanner.scan" -> ScannerWorkspaceOperations.PRE_SCAN_PAGE;
                case "pageScanner.refresh" -> ScannerWorkspaceOperations.PRE_SCAN_REFRESH_PAGE;
                case "pageScanner.clear" -> ScannerWorkspaceOperations.PRE_SCAN_CLEAR_GRID;
                default -> throw new IllegalArgumentException("Unsupported Page Scanner command");
            };
            String searchTerms = stringValue(body, "searchTerms");
            if (searchTerms != null && searchTerms.length() > MAX_PAGE_SCANNER_SEARCH_TERMS) {
                throw new IllegalArgumentException("Page Scanner search terms are too long");
            }
            BotJobWorkspaceController.getInstance()
                    .pageScannerCommand(legacyOperation, body, transportSessionId, scanContext);

            JsonObject response = new JsonObject();
            response.addProperty("requestId", requestId);
            response.addProperty("ok", true);
            response.addProperty("accepted", true);
            response.addProperty("message", switch (operation) {
                case "pageScanner.scan" -> "Page Scanner started.";
                case "pageScanner.refresh" -> "Page refresh started.";
                default -> "Page Scanner grid cleared.";
            });
            sendPageScannerResponse(
                    workspace.context().homeBankingId(),
                    transportSessionId,
                    transport,
                    responseOperation,
                    response);
        } catch (IllegalArgumentException | IllegalStateException invalidRequest) {
            sendPageScannerFailure(
                    body,
                    envelopeHomeBankingId,
                    transportSessionId,
                    transport,
                    responseOperation,
                    invalidRequest.getMessage());
        } catch (RuntimeException failure) {
            log.error("Unable to execute detached Page Scanner command {}", operation, failure);
            sendPageScannerFailure(
                    body,
                    envelopeHomeBankingId,
                    transportSessionId,
                    transport,
                    responseOperation,
                    "Unable to execute the Page Scanner command.");
        }
    }

    private void handlePageScannerProfileCommand(
            String operation,
            JsonObject envelope,
            int envelopeHomeBankingId,
            String claimedSessionId,
            String transportSessionId,
            Session transport) {
        JsonObject body = bodyOrEmpty(envelope);
        String responseOperation = pageScannerResponseOperation(operation);
        if (!validatePageScannerTransport(
                body,
                envelopeHomeBankingId,
                claimedSessionId,
                transportSessionId,
                transport,
                responseOperation)) {
            return;
        }

        try {
            String requestId = requirePageScannerRequestId(body);
            PageScannerWorkspaceCoordinator.BootstrapContext workspace =
                    requireActivePageScannerWorkspace(transportSessionId);
            Map<String, Object> result = switch (operation) {
                case "pageScannerProfile.list" -> pageScannerProfileService.list(body);
                case "pageScannerProfile.save" -> pageScannerProfileService.save(body);
                case "pageScannerProfile.delete" -> pageScannerProfileService.delete(body);
                default -> throw new IllegalArgumentException("Unsupported Page Scanner profile command");
            };
            JsonObject response = gson.toJsonTree(result).getAsJsonObject();
            response.addProperty("requestId", requestId);
            sendPageScannerResponse(
                    workspace.context().homeBankingId(),
                    transportSessionId,
                    transport,
                    responseOperation,
                    response);
        } catch (IllegalArgumentException | IllegalStateException invalidRequest) {
            sendPageScannerFailure(
                    body,
                    envelopeHomeBankingId,
                    transportSessionId,
                    transport,
                    responseOperation,
                    invalidRequest.getMessage());
        } catch (RuntimeException failure) {
            log.error("Unable to execute detached Page Scanner profile command {}", operation, failure);
            sendPageScannerFailure(
                    body,
                    envelopeHomeBankingId,
                    transportSessionId,
                    transport,
                    responseOperation,
                    "Unable to update Page Scanner focus profiles.");
        }
    }

    private void handlePageScannerElementTest(
            JsonObject envelope,
            int envelopeHomeBankingId,
            String claimedSessionId,
            String transportSessionId,
            Session transport) {
        JsonObject body = bodyOrEmpty(envelope);
        String responseOperation = "pageScanner.testElementResponse";
        if (!validatePageScannerTransport(
                body,
                envelopeHomeBankingId,
                claimedSessionId,
                transportSessionId,
                transport,
                responseOperation)) {
            return;
        }

        try {
            String requestId = requirePageScannerRequestId(body);
            PageScannerWorkspaceCoordinator.BootstrapContext workspace =
                    requireActivePageScannerWorkspace(transportSessionId);
            String testType = stringValue(body, "testType");
            if (testType == null) testType = stringValue(body, "action");
            if (!ScannerWorkspaceOperations.TEST_CLICK_DTO.equals(testType)
                    && !ScannerWorkspaceOperations.TEST_INPUT_DTO.equals(testType)) {
                throw new IllegalArgumentException("Page Scanner testType is invalid");
            }
            SplitDTO payload = gson.fromJson(body, SplitDTO.class);
            bindPageScannerPayload(payload, workspace.context(), transportSessionId, requestId);
            if (payload.getElementDetails() == null || payload.getElementDetails().length != 1) {
                throw new IllegalArgumentException("Page Scanner element test requires exactly one element");
            }
            BotJobWorkspaceController.getInstance().pageScannerElementTest(
                    payload,
                    testType,
                    transportSessionId,
                    preScanContext(workspace.context()));

            JsonObject response = new JsonObject();
            response.addProperty("requestId", requestId);
            response.addProperty("ok", true);
            response.addProperty("accepted", true);
            response.addProperty("testType", testType);
            response.addProperty("message", "Page Scanner element test started.");
            sendPageScannerResponse(
                    workspace.context().homeBankingId(),
                    transportSessionId,
                    transport,
                    responseOperation,
                    response);
        } catch (IllegalArgumentException | IllegalStateException invalidRequest) {
            sendPageScannerFailure(
                    body,
                    envelopeHomeBankingId,
                    transportSessionId,
                    transport,
                    responseOperation,
                    invalidRequest.getMessage());
        } catch (RuntimeException failure) {
            log.error("Unable to test a detached Page Scanner element", failure);
            sendPageScannerFailure(
                    body,
                    envelopeHomeBankingId,
                    transportSessionId,
                    transport,
                    responseOperation,
                    "Unable to test the selected Page Scanner element.");
        }
    }

    private void handlePageScannerApply(
            JsonObject envelope,
            int envelopeHomeBankingId,
            String claimedSessionId,
            String transportSessionId,
            Session transport) {
        JsonObject body = bodyOrEmpty(envelope);
        String responseOperation = "pageScanner.applyResponse";
        if (!validatePageScannerTransport(
                body,
                envelopeHomeBankingId,
                claimedSessionId,
                transportSessionId,
                transport,
                responseOperation)) {
            return;
        }

        try {
            String requestId = requirePageScannerRequestId(body);
            PageScannerWorkspaceCoordinator.BootstrapContext workspace =
                    requireActivePageScannerWorkspace(transportSessionId);
            JsonObject response = BotJobDetailsWorkspaceRegistry.getInstance().commitWorkspaceMutation(
                    workspace.context().botJobId(),
                    workspace.context().workspaceEpoch(),
                    () -> pageScannerMutationLedger.executeOnce(
                            transportSessionId,
                            requestId,
                            "pageScanner.apply",
                            body,
                            () -> applyPageScannerElements(
                                    body, requestId, transportSessionId, workspace.context())));
            sendPageScannerResponse(
                    workspace.context().homeBankingId(),
                    transportSessionId,
                    transport,
                    responseOperation,
                    response);
        } catch (IllegalArgumentException | IllegalStateException invalidRequest) {
            sendPageScannerFailure(
                    body,
                    envelopeHomeBankingId,
                    transportSessionId,
                    transport,
                    responseOperation,
                    invalidRequest.getMessage());
        } catch (RuntimeException failure) {
            log.error("Unable to apply detached Page Scanner elements", failure);
            sendPageScannerFailure(
                    body,
                    envelopeHomeBankingId,
                    transportSessionId,
                    transport,
                    responseOperation,
                    "Unable to add the selected elements to the Bot Job.");
        }
    }

    private JsonObject applyPageScannerElements(
            JsonObject body,
            String requestId,
            String transportSessionId,
            PageScannerWorkspaceCoordinator.WorkspaceContext context) {
        SplitDTO payload = gson.fromJson(body, SplitDTO.class);
        bindPageScannerPayload(payload, context, transportSessionId, requestId);
        payload.setType(ScannerWorkspaceOperations.SEND_ALL_ELEMENTS_DTO);
        if (payload.getBlockId() == null || payload.getBlockId() <= 0) {
            throw new IllegalArgumentException("Select a target block before applying");
        }
        requirePageScannerBlock(context, payload.getBlockId());
        ElementDTO[] elements = payload.getElementDetails();
        if (elements == null || elements.length == 0) {
            throw new IllegalArgumentException("No Page Scanner elements were selected");
        }
        if (elements.length > MAX_PAGE_SCANNER_ELEMENTS) {
            throw new IllegalArgumentException("Too many Page Scanner elements were selected");
        }
        if (Arrays.stream(elements).anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Page Scanner element selection contains an empty row");
        }

        PreScanApplyService.ApplyResult result =
                PreScanApplyService.getInstance().applyElementsDetailed(payload);
        ErrorMessage error = result.error();
        boolean committed = error == null;
        JsonObject response = new JsonObject();
        response.addProperty("requestId", requestId);
        response.addProperty("ok", committed);
        response.addProperty("committed", committed);
        response.addProperty("synchronized", result.synchronizedSnapshot());
        response.addProperty("insertedCount", result.insertedCount());
        response.addProperty("blockId", payload.getBlockId());
        response.addProperty(
                "message",
                committed
                        ? result.synchronizedSnapshot()
                                ? "Added " + result.insertedCount() + " element(s) to the Bot Job."
                                : "Elements were added, but Bot Job Details could not refresh yet."
                        : error.getErrorMessage());
        if (error != null) response.addProperty("errorCode", "APPLY_FAILED");
        if (committed && !result.synchronizedSnapshot()) {
            response.addProperty("warningCode", "BOT_JOB_SYNC_FAILED");
        }
        return response;
    }

    private void handlePageScannerLocatorGenerate(
            JsonObject envelope,
            int envelopeHomeBankingId,
            String claimedSessionId,
            String transportSessionId,
            Session transport) {
        JsonObject body = bodyOrEmpty(envelope);
        String responseOperation = "pageScanner.locator.generateResponse";
        if (!validatePageScannerTransport(
                body,
                envelopeHomeBankingId,
                claimedSessionId,
                transportSessionId,
                transport,
                responseOperation)) {
            return;
        }

        try {
            requirePageScannerRequestId(body);
            PageScannerWorkspaceCoordinator.BootstrapContext workspace =
                    requireActivePageScannerWorkspace(transportSessionId);
            JsonObject response = LocatorGeneratorService.getInstance().generate(body);
            sendPageScannerResponse(
                    workspace.context().homeBankingId(),
                    transportSessionId,
                    transport,
                    responseOperation,
                    response);
        } catch (IllegalArgumentException | IllegalStateException invalidRequest) {
            sendPageScannerFailure(
                    body,
                    envelopeHomeBankingId,
                    transportSessionId,
                    transport,
                    responseOperation,
                    invalidRequest.getMessage());
        } catch (RuntimeException failure) {
            log.error("Unable to generate detached Page Scanner locators", failure);
            sendPageScannerFailure(
                    body,
                    envelopeHomeBankingId,
                    transportSessionId,
                    transport,
                    responseOperation,
                    "Unable to generate locators from the pasted HTML.");
        }
    }

    private void handlePageScannerLocatorApply(
            JsonObject envelope,
            int envelopeHomeBankingId,
            String claimedSessionId,
            String transportSessionId,
            Session transport) {
        JsonObject body = bodyOrEmpty(envelope);
        String responseOperation = "pageScanner.locator.applyResponse";
        if (!validatePageScannerTransport(
                body,
                envelopeHomeBankingId,
                claimedSessionId,
                transportSessionId,
                transport,
                responseOperation)) {
            return;
        }

        try {
            String requestId = requirePageScannerRequestId(body);
            PageScannerWorkspaceCoordinator.BootstrapContext workspace =
                    requireActivePageScannerWorkspace(transportSessionId);
            JsonObject response = BotJobDetailsWorkspaceRegistry.getInstance().commitWorkspaceMutation(
                    workspace.context().botJobId(),
                    workspace.context().workspaceEpoch(),
                    () -> pageScannerMutationLedger.executeOnce(
                            transportSessionId,
                            requestId,
                            "pageScanner.locator.apply",
                            body,
                            () -> LocatorGeneratorService.getInstance().apply(
                                    body,
                                    workspace.context().homeBankingId(),
                                    workspace.context().botJobId(),
                                    workspace.context().homeUrlId(),
                                    BotJobDetailsWorkspaceHost.getInstance()
                                            .currentPageScannerUrl())));
            copyBoundedPageScannerString(body, response, "elementKey", 2_048);
            sendPageScannerResponse(
                    workspace.context().homeBankingId(),
                    transportSessionId,
                    transport,
                    responseOperation,
                    response);
        } catch (IllegalArgumentException | IllegalStateException invalidRequest) {
            sendPageScannerFailure(
                    body,
                    envelopeHomeBankingId,
                    transportSessionId,
                    transport,
                    responseOperation,
                    invalidRequest.getMessage());
        } catch (RuntimeException failure) {
            log.error("Unable to apply a detached Page Scanner locator", failure);
            sendPageScannerFailure(
                    body,
                    envelopeHomeBankingId,
                    transportSessionId,
                    transport,
                    responseOperation,
                    "Unable to apply the generated XPath to the selected element.");
        }
    }

    private void handlePageScannerCreateBlock(
            JsonObject envelope,
            int envelopeHomeBankingId,
            String claimedSessionId,
            String transportSessionId,
            Session transport) {
        JsonObject body = bodyOrEmpty(envelope);
        String responseOperation = "pageScanner.createBlockResponse";
        if (!validatePageScannerTransport(
                body,
                envelopeHomeBankingId,
                claimedSessionId,
                transportSessionId,
                transport,
                responseOperation)) {
            return;
        }

        try {
            String requestId = requirePageScannerRequestId(body);
            PageScannerWorkspaceCoordinator.BootstrapContext workspace =
                    requireActivePageScannerWorkspace(transportSessionId);
            JsonObject response = BotJobDetailsWorkspaceRegistry.getInstance().commitWorkspaceMutation(
                    workspace.context().botJobId(),
                    workspace.context().workspaceEpoch(),
                    () -> pageScannerMutationLedger.executeOnce(
                            transportSessionId,
                            requestId,
                            "pageScanner.createBlock",
                            body,
                            () -> createPageScannerBlock(
                                    body, requestId, transportSessionId, workspace.context())));
            sendPageScannerResponse(
                    workspace.context().homeBankingId(),
                    transportSessionId,
                    transport,
                    responseOperation,
                    response);
        } catch (IllegalArgumentException | IllegalStateException invalidRequest) {
            sendPageScannerFailure(
                    body,
                    envelopeHomeBankingId,
                    transportSessionId,
                    transport,
                    responseOperation,
                    invalidRequest.getMessage());
        } catch (RuntimeException failure) {
            log.error("Unable to create a detached Page Scanner block", failure);
            sendPageScannerFailure(
                    body,
                    envelopeHomeBankingId,
                    transportSessionId,
                    transport,
                    responseOperation,
                    "Unable to create the Page Scanner target block.");
        }
    }

    private JsonObject createPageScannerBlock(
            JsonObject body,
            String requestId,
            String transportSessionId,
            PageScannerWorkspaceCoordinator.WorkspaceContext context) {
        SplitDTO payload = gson.fromJson(body, SplitDTO.class);
        bindPageScannerPayload(payload, context, transportSessionId, requestId);
        String blockName = payload.getBlockName() == null ? "" : payload.getBlockName().trim();
        if (blockName.isEmpty() || blockName.length() > MAX_PAGE_SCANNER_BLOCK_NAME) {
            throw new IllegalArgumentException("Page Scanner block name must contain 1 to 256 characters");
        }
        payload.setBlockName(blockName);
        String position = payload.getInsertPosition() == null
                ? "END"
                : payload.getInsertPosition().trim().toUpperCase(Locale.ROOT);
        payload.setInsertPosition(position);
        if ("BEFORE".equals(position)) {
            if (payload.getBeforeBlockId() == null || payload.getBeforeBlockId() <= 0) {
                throw new IllegalArgumentException("Choose the block position before creating a block");
            }
            requirePageScannerBlock(context, payload.getBeforeBlockId());
        } else if (!"END".equals(position)) {
            throw new IllegalArgumentException("Page Scanner block position must be END or BEFORE");
        }

        BlockCreationService.Result result = BlockCreationService.getInstance().createFrom(payload);
        boolean committed = result.newBlockId() != null && result.newBlockId() > 0;
        JsonObject response = new JsonObject();
        response.addProperty("requestId", requestId);
        response.addProperty("ok", committed);
        response.addProperty("committed", committed);
        response.addProperty("blockName", payload.getBlockName());
        if (result.newBlockId() != null) response.addProperty("createdBlockId", result.newBlockId());
        if (result.newBlockOrderNumber() != null) {
            response.addProperty("createdBlockOrderNumber", result.newBlockOrderNumber());
        }
        response.add("blocks", gson.toJsonTree(mapBlockOptions("block", context.botJobId())));
        response.addProperty(
                "message",
                committed
                        ? result.error() == null
                                ? "Target block created."
                                : "Target block was created, but the refreshed block list is not available yet."
                        : result.error() == null
                                ? "Target block could not be created."
                                : result.error().getErrorMessage());
        if (!committed) {
            response.addProperty("errorCode", "BLOCK_CREATE_FAILED");
        } else {
            if (result.error() != null) response.addProperty("warningCode", "BLOCK_REFRESH_FAILED");
            publishPageScannerBotJobSnapshot(context, result.newBlockId());
        }
        return response;
    }

    private void handlePageScannerClose(
            JsonObject envelope,
            int envelopeHomeBankingId,
            String claimedSessionId,
            String transportSessionId,
            Session transport) {
        JsonObject body = bodyOrEmpty(envelope);
        String responseOperation = "pageScanner.closeResponse";
        if (!validatePageScannerTransport(
                body,
                envelopeHomeBankingId,
                claimedSessionId,
                transportSessionId,
                transport,
                responseOperation)) {
            return;
        }

        try {
            String requestId = requirePageScannerRequestId(body);
            PageScannerWorkspaceCoordinator.BootstrapContext workspace =
                    pageScannerWorkspaceCoordinator.bootstrap(transportSessionId);
            pageScannerWorkspaceCoordinator.close(transportSessionId);
            pageScannerMutationLedger.clearSession(transportSessionId);

            JsonObject response = new JsonObject();
            response.addProperty("requestId", requestId);
            response.addProperty("ok", true);
            response.addProperty("message", "Page Scanner workspace closed.");
            sendPageScannerResponse(
                    workspace.context().homeBankingId(),
                    transportSessionId,
                    transport,
                    responseOperation,
                    response);
        } catch (IllegalArgumentException | IllegalStateException invalidRequest) {
            sendPageScannerFailure(
                    body,
                    envelopeHomeBankingId,
                    transportSessionId,
                    transport,
                    responseOperation,
                    invalidRequest.getMessage());
        }
    }

    private PageScannerWorkspaceCoordinator.BootstrapContext requireActivePageScannerWorkspace(
            String transportSessionId) {
        PageScannerWorkspaceCoordinator.BootstrapContext workspace =
                pageScannerWorkspaceCoordinator.bootstrap(transportSessionId);
        PageScannerWorkspaceCoordinator.WorkspaceContext context = workspace.context();
        BotJobDetailsWorkspaceRegistry.getInstance()
                .require(context.botJobId(), context.workspaceEpoch());
        return workspace;
    }

    private static PreScanWorkflowService.Context preScanContext(
            PageScannerWorkspaceCoordinator.WorkspaceContext context) {
        return new PreScanWorkflowService.Context(
                context.botJobId(),
                context.botJobName(),
                context.homeBankingId(),
                context.homeUrlId(),
                context.endpointUrl(),
                context.browserType(),
                context.optionsConfig(),
                context.jsonPath());
    }

    private static void bindPageScannerPayload(
            SplitDTO payload,
            PageScannerWorkspaceCoordinator.WorkspaceContext context,
            String transportSessionId,
            String requestId) {
        if (payload == null) throw new IllegalArgumentException("Page Scanner payload is required");
        payload.setSessionId(transportSessionId);
        payload.setRequestId(requestId);
        payload.setHomeBankingId(context.homeBankingId());
        payload.setBotJobId(context.botJobId());
        payload.setBotJobName(context.botJobName());
    }

    private BlockLoadDTO requirePageScannerBlock(
            PageScannerWorkspaceCoordinator.WorkspaceContext context, int blockId) {
        ErrorMessage loadError = performDataBase.loadBlocks(context.botJobId(), context.botJobName(), "block");
        if (loadError != null) {
            throw new IllegalStateException(loadError.getErrorMessage());
        }
        return performLists.getListBlock().stream()
                .filter(Objects::nonNull)
                .filter(block -> block.getId() != null && block.getId() == blockId)
                .filter(block -> block.getBotJobId() != null && block.getBotJobId() == context.botJobId())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "The selected target block does not belong to the active Bot Job"));
    }

    private void publishPageScannerBotJobSnapshot(
            PageScannerWorkspaceCoordinator.WorkspaceContext context, Integer createdBlockId) {
        ErrorMessage loadError = publishBotJobTasksAuthoritativeSnapshot(
                context.homeBankingId(), context.botJobId(), createdBlockId, null, null);
        if (loadError != null) {
            log.warn("Page Scanner block was created but Bot Job refresh failed: {}", loadError.getErrorMessage());
        }
    }

    private ErrorMessage publishBotJobTasksAuthoritativeSnapshot(
            int homeBankingId,
            int botJobId,
            Integer createdBlockId,
            String createdBlockName,
            Integer createdBlockOrderNumber) {
        ErrorMessage loadError = performDBEngine.loadCompleteJobs(botJobId);
        if (loadError != null) {
            return loadError;
        }
        loadError = performDataBase.loadBlocks(botJobId, "", "block");
        if (loadError != null) {
            return loadError;
        }

        List<InstructionLoad> instructions = performLists.getListBotJob().isEmpty()
                ? List.of()
                : performLists.buildJsonViewData(performLists.getListBotJob());
        JsonObject update = new JsonObject();
        update.add("instructions", gson.toJsonTree(instructions));
        update.add("blocks", gson.toJsonTree(mapBlockOptions("block", botJobId)));
        update.addProperty("botJobId", botJobId);
        if (createdBlockId != null) update.addProperty("createdBlockId", createdBlockId);
        if (createdBlockName != null) update.addProperty("createdBlockName", createdBlockName);
        if (createdBlockOrderNumber != null) {
            update.addProperty("createdBlockOrderNumber", createdBlockOrderNumber);
        }
        instructionRealtimePublisher.publishSerializedSnapshot(
                homeBankingId,
                ScannerWorkspaceSessions.BOT_JOB_TASKS,
                gson.toJson(update));
        return null;
    }

    private boolean validatePageScannerTransport(
            JsonObject body,
            int homeBankingId,
            String claimedSessionId,
            String transportSessionId,
            Session transport,
            String responseOperation) {
        if (body != null && body.toString().length() > MAX_PAGE_SCANNER_BODY_CHARACTERS) {
            sendPageScannerFailure(
                    body,
                    homeBankingId,
                    transportSessionId,
                    transport,
                    responseOperation,
                    "Page Scanner request is too large.");
            return false;
        }
        if (transportSessionId != null && transportSessionId.equals(claimedSessionId)) return true;
        log.warn(
                "Rejected Page Scanner request with mismatched transport identity: claimed={}, actual={}",
                claimedSessionId,
                transportSessionId);
        sendPageScannerFailure(
                body,
                homeBankingId,
                transportSessionId,
                transport,
                responseOperation,
                "WebSocket session identity mismatch.");
        return false;
    }

    private void sendPageScannerFailure(
            JsonObject body,
            int homeBankingId,
            String transportSessionId,
            Session transport,
            String operationId,
            String message) {
        JsonObject response = responseWithRequestId(body);
        response.addProperty("ok", false);
        copyPositivePageScannerBotJobId(body, response);
        copyBoundedPageScannerString(body, response, "elementKey", 2_048);
        response.addProperty(
                "message",
                message == null || message.isBlank()
                        ? "Invalid Page Scanner request."
                        : message);
        sendPageScannerResponse(
                homeBankingId, transportSessionId, transport, operationId, response);
    }

    private void sendPageScannerResponse(
            int homeBankingId,
            String transportSessionId,
            Session transport,
            String operationId,
            JsonObject response) {
        if (transportSessionId == null || transport == null) return;
        WebSocketSessionManager.sendMessageJson(
                homeBankingId,
                transport,
                transportSessionId,
                gson.toJson(response),
                operationId);
    }

    static void copyPositivePageScannerBotJobId(
            JsonObject requestBody, JsonObject response) {
        if (requestBody == null || !requestBody.has("botJobId")) return;
        try {
            int botJobId = requestBody.get("botJobId").getAsInt();
            if (botJobId > 0) response.addProperty("botJobId", botJobId);
        } catch (RuntimeException invalidBotJobId) {
            // Preserve the original validation failure without reflecting malformed identity data.
        }
    }

    private static void copyBoundedPageScannerString(
            JsonObject requestBody,
            JsonObject response,
            String field,
            int maximumLength) {
        if (requestBody == null || response == null || field == null || !requestBody.has(field)) return;
        try {
            String value = requestBody.get(field).getAsString();
            if (value != null && !value.isBlank() && value.length() <= maximumLength) {
                response.addProperty(field, value);
            }
        } catch (RuntimeException invalidValue) {
            // Do not reflect malformed correlation data.
        }
    }

    private static String pageScannerResponseOperation(String requestOperation) {
        if (requestOperation == null || requestOperation.isBlank()) {
            return "pageScanner.errorResponse";
        }
        return requestOperation.endsWith("Response")
                ? requestOperation
                : requestOperation + "Response";
    }

    static boolean isSupportedPageScannerOperation(String operation) {
        return operation != null && PAGE_SCANNER_OPERATIONS.contains(operation);
    }

    static boolean isPageScannerTransportOperation(String operation) {
        return operation != null
                && (operation.startsWith("pageScanner.")
                        || operation.startsWith("pageScannerWorkspace.")
                        || operation.startsWith("pageScannerProfile."));
    }

    static boolean isAllowedFromDetachedPageScannerTransport(String operation) {
        return isPageScannerTransportOperation(operation)
                || "ocrWorkspace.open".equals(operation)
                || "memoryList.open".equals(operation)
                || "memoryList.sync".equals(operation)
                || (operation != null && DETACHED_PAGE_SCANNER_BOT_JOB_OPERATIONS.contains(operation));
    }

    static boolean isAllowedFromDetachedCommandEditorTransport(String operation) {
        return operation != null && DETACHED_COMMAND_EDITOR_OPERATIONS.contains(operation);
    }

    static boolean isAllowedDetachedPageScannerToolbarAction(String action) {
        return action != null
                && DETACHED_PAGE_SCANNER_TOOLBAR_ACTIONS.contains(
                        action.trim().toUpperCase(Locale.ROOT));
    }

    private static String requirePageScannerRequestId(JsonObject body) {
        String requestId = stringValue(body, "requestId");
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("Page Scanner requestId is required");
        }
        String normalized = requestId.trim();
        if (normalized.length() > 160 || !normalized.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalArgumentException("Page Scanner requestId is invalid");
        }
        return normalized;
    }

    private static int requirePositivePageScannerInt(JsonObject body, String field) {
        int value = intValue(body, field, -1);
        if (value <= 0) throw new IllegalArgumentException("Page Scanner " + field + " must be positive");
        return value;
    }

    private boolean validateOcrWorkspaceTransport(
            JsonObject body,
            int homeBankingId,
            String claimedSessionId,
            String transportSessionId,
            Session transport,
            String responseOperation) {
        if (transportSessionId != null && transportSessionId.equals(claimedSessionId)) return true;
        log.warn(
                "Rejected OCR workspace request with mismatched transport identity: claimed={}, actual={}",
                claimedSessionId,
                transportSessionId);
        sendOcrWorkspaceFailure(
                body,
                homeBankingId,
                transportSessionId,
                transport,
                responseOperation,
                "WebSocket session identity mismatch.");
        return false;
    }

    private void sendOcrWorkspaceFailure(
            JsonObject body,
            int homeBankingId,
            String transportSessionId,
            Session transport,
            String operationId,
            String message) {
        JsonObject response = responseWithRequestId(body);
        response.addProperty("ok", false);
        response.addProperty("message", message == null || message.isBlank()
                ? "Invalid OCR workspace request."
                : message);
        sendOcrWorkspaceResponse(homeBankingId, transportSessionId, transport, operationId, response);
    }

    private void sendOcrWorkspaceResponse(
            int homeBankingId,
            String transportSessionId,
            Session transport,
            String operationId,
            JsonObject response) {
        if (transportSessionId == null || transport == null) return;
        WebSocketSessionManager.sendMessageJson(
                homeBankingId,
                transport,
                transportSessionId,
                gson.toJson(response),
                operationId);
    }

    private JsonObject bodyOrEmpty(JsonObject envelope) {
        JsonObject body = extractBody(envelope);
        return body == null ? new JsonObject() : body;
    }

    private static JsonObject responseWithRequestId(JsonObject body) {
        JsonObject response = new JsonObject();
        if (body != null && body.has("requestId") && !body.get("requestId").isJsonNull()) {
            response.add("requestId", body.get("requestId").deepCopy());
        }
        return response;
    }

    private static String stringValue(JsonObject object, String field) {
        if (object == null || !object.has(field) || object.get(field).isJsonNull()) return null;
        try {
            return object.get(field).getAsString();
        } catch (RuntimeException invalidValue) {
            return null;
        }
    }

    private static int intValue(JsonObject object, String field, int fallback) {
        if (object == null || !object.has(field) || object.get(field).isJsonNull()) return fallback;
        try {
            return object.get(field).getAsInt();
        } catch (RuntimeException invalidValue) {
            return fallback;
        }
    }

    private static Integer optionalPositiveInt(JsonObject object, String field) {
        int value = intValue(object, field, -1);
        return value > 0 ? value : null;
    }

    /** Pull botJobId from the message body (string-encoded JSON) or top-level field. */
    private int extractBotJobId(JsonObject jsonObjMSG) {
        JsonObject body = extractBody(jsonObjMSG);
        if (body != null && body.has("botJobId")) {
            return body.get("botJobId").getAsInt();
        }
        if (jsonObjMSG.has("botJobId")) {
            return jsonObjMSG.get("botJobId").getAsInt();
        }
        return -1;
    }

    /** Decode the {@code body} field whether it arrives as a JSON string or a JSON object. */
    private JsonObject extractBody(JsonObject jsonObjMSG) {
        if (!jsonObjMSG.has("body")) return null;
        var bodyEl = jsonObjMSG.get("body");
        if (bodyEl.isJsonPrimitive() && bodyEl.getAsJsonPrimitive().isString()) {
            try {
                return JsonParser.parseString(bodyEl.getAsString()).getAsJsonObject();
            } catch (Exception ignored) {
                return null;
            }
        }
        if (bodyEl.isJsonObject()) {
            return bodyEl.getAsJsonObject();
        }
        return null;
    }

    private boolean isActiveLicenseResponse(JsonObject response) {
        return response != null
                && response.has("ok")
                && response.get("ok").getAsBoolean()
                && response.has("active")
                && response.get("active").getAsBoolean();
    }

    private void publishLicenseStatus(JsonObject response) {
        if (response != null && response.has("active")) {
            webSocketSessionManager.broadcastJsonToAll(-1, gson.toJson(response), "license.statusChanged");
        }
    }

    /**
     * List blocks for a bot job — used by the Flow tab UI step inspector
     * (Phase 2c) for the block picker.
     *
     * Inbound:  { "type": "botJob.getBlocks", "sessionId": "...", "body": "{\"botJobId\":42}" }
     * Outbound: "botJob.blocks" — body is JSON array of block rows
     *           {id, blockOrderNumber, name, description, active}.
     */
    private void handleBotJobGetBlocks(JsonObject jsonObjMSG, String sessionId, int homeBankingId) {
        try {
            int botJobId = extractBotJobId(jsonObjMSG);
            List<Map<String, Object>> rows = performDataBase.loadBlocksForBotJob(botJobId);
            webSocketSessionManager.sendMessageJson(homeBankingId, sessionId, gson.toJson(rows), "botJob.blocks");
        } catch (Exception e) {
            log.error("handleBotJobGetBlocks failed: {}", e.getMessage());
            webSocketSessionManager.sendMessageJson(homeBankingId, sessionId, "[]", "botJob.blocks");
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Flow CRUD verbs (Phase 2a of ROADMAP_9 — Flow tab skeleton)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * List flows for a bot job (steps NOT included — left-rail rendering only).
     *
     * Inbound:  { "type": "flow.list", "sessionId": "...", "body": "{\"botJobId\":42}" }
     * Outbound: "flow.listResponse" — body is JSON array of FlowDTO.
     */
    private void handleFlowList(JsonObject jsonObjMSG, String sessionId, int homeBankingId) {
        try {
            int botJobId = extractBotJobId(jsonObjMSG);
            List<FlowDTO> rows = performDataBase.loadFlows(botJobId);
            webSocketSessionManager.sendMessageJson(homeBankingId, sessionId, gson.toJson(rows), "flow.listResponse");
        } catch (Exception e) {
            log.error("handleFlowList failed: {}", e.getMessage());
            webSocketSessionManager.sendMessageJson(homeBankingId, sessionId, "[]", "flow.listResponse");
        }
    }

    /**
     * Create or rename a flow. Body must carry a FlowDTO; id null = create,
     * id non-null = update.
     *
     * Inbound:  { "type": "flow.save", "sessionId": "...",
     *             "body": "{\"flow\": {...FlowDTO...}}" }
     * Outbound: "flow.saveResponse" — body { ok: bool, id: N|null, flow: {...} }
     */
    private void handleFlowSave(JsonObject jsonObjMSG, String sessionId, int homeBankingId) {
        try {
            JsonObject body = extractBody(jsonObjMSG);
            FlowDTO dto = body != null && body.has("flow") ? gson.fromJson(body.get("flow"), FlowDTO.class) : null;
            Integer id = performDataBase.saveFlow(dto);
            JsonObject resp = new JsonObject();
            resp.addProperty("ok", id != null);
            if (id != null) {
                if (dto != null) dto.setId(id);
                resp.add("flow", gson.toJsonTree(dto));
                resp.addProperty("id", id);
            }
            webSocketSessionManager.sendMessageJson(homeBankingId, sessionId, gson.toJson(resp), "flow.saveResponse");
        } catch (Exception e) {
            log.error("handleFlowSave failed: {}", e.getMessage());
            JsonObject resp = new JsonObject();
            resp.addProperty("ok", false);
            resp.addProperty("error", e.getMessage());
            webSocketSessionManager.sendMessageJson(homeBankingId, sessionId, gson.toJson(resp), "flow.saveResponse");
        }
    }

    /**
     * Delete a flow (cascades to flow_step rows).
     *
     * Inbound:  { "type": "flow.delete", "sessionId": "...", "body": "{\"flowId\":7}" }
     * Outbound: "flow.deleteResponse" — body { ok: bool, flowId: N }
     */
    private void handleFlowDelete(JsonObject jsonObjMSG, String sessionId, int homeBankingId) {
        try {
            JsonObject body = extractBody(jsonObjMSG);
            int flowId = body != null && body.has("flowId") ? body.get("flowId").getAsInt() : -1;
            boolean ok = performDataBase.deleteFlow(flowId);
            JsonObject resp = new JsonObject();
            resp.addProperty("ok", ok);
            resp.addProperty("flowId", flowId);
            webSocketSessionManager.sendMessageJson(homeBankingId, sessionId, gson.toJson(resp), "flow.deleteResponse");
        } catch (Exception e) {
            log.error("handleFlowDelete failed: {}", e.getMessage());
            JsonObject resp = new JsonObject();
            resp.addProperty("ok", false);
            resp.addProperty("error", e.getMessage());
            webSocketSessionManager.sendMessageJson(homeBankingId, sessionId, gson.toJson(resp), "flow.deleteResponse");
        }
    }

    /**
     * Load all steps for a flow (ordered).
     *
     * Inbound:  { "type": "flow.steps.load", "sessionId": "...", "body": "{\"flowId\":7}" }
     * Outbound: "flow.stepsLoaded" — body is JSON array of FlowStepDTO.
     */
    private void handleFlowStepsLoad(JsonObject jsonObjMSG, String sessionId, int homeBankingId) {
        try {
            JsonObject body = extractBody(jsonObjMSG);
            int flowId = body != null && body.has("flowId") ? body.get("flowId").getAsInt() : -1;
            List<FlowStepDTO> rows = performDataBase.loadFlowSteps(flowId);
            webSocketSessionManager.sendMessageJson(homeBankingId, sessionId, gson.toJson(rows), "flow.stepsLoaded");
        } catch (Exception e) {
            log.error("handleFlowStepsLoad failed: {}", e.getMessage());
            webSocketSessionManager.sendMessageJson(homeBankingId, sessionId, "[]", "flow.stepsLoaded");
        }
    }

    /**
     * Replace every step of a flow with the given list (wipe-and-insert).
     *
     * Inbound:  { "type": "flow.steps.save", "sessionId": "...",
     *             "body": "{\"flowId\":7, \"steps\": [...FlowStepDTO...]}" }
     * Outbound: "flow.stepsSaved" — body { ok: bool, count: N, flowId: N }
     */
    private void handleFlowStepsSave(JsonObject jsonObjMSG, String sessionId, int homeBankingId) {
        try {
            JsonObject body = extractBody(jsonObjMSG);
            int flowId = body != null && body.has("flowId") ? body.get("flowId").getAsInt() : -1;
            List<FlowStepDTO> steps = new ArrayList<>();
            if (body != null && body.has("steps") && body.get("steps").isJsonArray()) {
                for (var el : body.getAsJsonArray("steps")) {
                    steps.add(gson.fromJson(el, FlowStepDTO.class));
                }
            }
            boolean ok = performDataBase.saveFlowSteps(flowId, steps);
            JsonObject resp = new JsonObject();
            resp.addProperty("ok", ok);
            resp.addProperty("count", steps.size());
            resp.addProperty("flowId", flowId);
            webSocketSessionManager.sendMessageJson(homeBankingId, sessionId, gson.toJson(resp), "flow.stepsSaved");
        } catch (Exception e) {
            log.error("handleFlowStepsSave failed: {}", e.getMessage());
            JsonObject resp = new JsonObject();
            resp.addProperty("ok", false);
            resp.addProperty("error", e.getMessage());
            webSocketSessionManager.sendMessageJson(homeBankingId, sessionId, gson.toJson(resp), "flow.stepsSaved");
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Requirement CRUD verbs (Requirements tab)
    // ──────────────────────────────────────────────────────────────────────

    /** body {botJobId} → "requirement.listResponse" body [RequirementDTO...] (with rolled-up coverage counts). */
    private void handleRequirementList(JsonObject jsonObjMSG, String sessionId, int homeBankingId) {
        try {
            int botJobId = extractBotJobId(jsonObjMSG);
            List<RequirementDTO> rows = performDataBase.loadRequirements(botJobId);
            webSocketSessionManager.sendMessageJson(
                    homeBankingId, sessionId, gson.toJson(rows), "requirement.listResponse");
        } catch (Exception e) {
            log.error("handleRequirementList failed: {}", e.getMessage());
            webSocketSessionManager.sendMessageJson(homeBankingId, sessionId, "[]", "requirement.listResponse");
        }
    }

    /** body {requirement: {...RequirementDTO...}} → "requirement.saveResponse" {ok, id, requirement}. */
    private void handleRequirementSave(JsonObject jsonObjMSG, String sessionId, int homeBankingId) {
        try {
            JsonObject body = extractBody(jsonObjMSG);
            RequirementDTO dto = body != null && body.has("requirement")
                    ? gson.fromJson(body.get("requirement"), RequirementDTO.class)
                    : null;
            Integer id = performDataBase.saveRequirement(dto);
            JsonObject resp = new JsonObject();
            resp.addProperty("ok", id != null);
            if (id != null) {
                if (dto != null) dto.setId(id);
                resp.add("requirement", gson.toJsonTree(dto));
                resp.addProperty("id", id);
            }
            webSocketSessionManager.sendMessageJson(
                    homeBankingId, sessionId, gson.toJson(resp), "requirement.saveResponse");
        } catch (Exception e) {
            log.error("handleRequirementSave failed: {}", e.getMessage());
            JsonObject resp = new JsonObject();
            resp.addProperty("ok", false);
            resp.addProperty("error", e.getMessage());
            webSocketSessionManager.sendMessageJson(
                    homeBankingId, sessionId, gson.toJson(resp), "requirement.saveResponse");
        }
    }

    /** body {requirementId} → "requirement.deleteResponse" {ok, requirementId}. */
    private void handleRequirementDelete(JsonObject jsonObjMSG, String sessionId, int homeBankingId) {
        try {
            JsonObject body = extractBody(jsonObjMSG);
            int reqId = body != null && body.has("requirementId")
                    ? body.get("requirementId").getAsInt()
                    : -1;
            boolean ok = performDataBase.deleteRequirement(reqId);
            JsonObject resp = new JsonObject();
            resp.addProperty("ok", ok);
            resp.addProperty("requirementId", reqId);
            webSocketSessionManager.sendMessageJson(
                    homeBankingId, sessionId, gson.toJson(resp), "requirement.deleteResponse");
        } catch (Exception e) {
            log.error("handleRequirementDelete failed: {}", e.getMessage());
            JsonObject resp = new JsonObject();
            resp.addProperty("ok", false);
            resp.addProperty("error", e.getMessage());
            webSocketSessionManager.sendMessageJson(
                    homeBankingId, sessionId, gson.toJson(resp), "requirement.deleteResponse");
        }
    }

    /** body {requirementId} → "requirement.linksLoaded" RequirementLinksDTO. */
    private void handleRequirementLinksLoad(JsonObject jsonObjMSG, String sessionId, int homeBankingId) {
        try {
            JsonObject body = extractBody(jsonObjMSG);
            int reqId = body != null && body.has("requirementId")
                    ? body.get("requirementId").getAsInt()
                    : -1;
            RequirementLinksDTO links = performDataBase.loadRequirementLinks(reqId);
            webSocketSessionManager.sendMessageJson(
                    homeBankingId, sessionId, gson.toJson(links), "requirement.linksLoaded");
        } catch (Exception e) {
            log.error("handleRequirementLinksLoad failed: {}", e.getMessage());
            RequirementLinksDTO empty = new RequirementLinksDTO();
            webSocketSessionManager.sendMessageJson(
                    homeBankingId, sessionId, gson.toJson(empty), "requirement.linksLoaded");
        }
    }

    /** body {requirementId, useCaseIds: [...], flowIds: [...]} → "requirement.linksSaved" {ok, requirementId}. */
    private void handleRequirementLinksSave(JsonObject jsonObjMSG, String sessionId, int homeBankingId) {
        try {
            JsonObject body = extractBody(jsonObjMSG);
            int reqId = body != null && body.has("requirementId")
                    ? body.get("requirementId").getAsInt()
                    : -1;
            List<Integer> useCaseIds = new ArrayList<>();
            List<Integer> flowIds = new ArrayList<>();
            if (body != null && body.has("useCaseIds") && body.get("useCaseIds").isJsonArray()) {
                for (var el : body.getAsJsonArray("useCaseIds")) useCaseIds.add(el.getAsInt());
            }
            if (body != null && body.has("flowIds") && body.get("flowIds").isJsonArray()) {
                for (var el : body.getAsJsonArray("flowIds")) flowIds.add(el.getAsInt());
            }
            boolean ok = performDataBase.saveRequirementLinks(reqId, useCaseIds, flowIds);
            JsonObject resp = new JsonObject();
            resp.addProperty("ok", ok);
            resp.addProperty("requirementId", reqId);
            webSocketSessionManager.sendMessageJson(
                    homeBankingId, sessionId, gson.toJson(resp), "requirement.linksSaved");
        } catch (Exception e) {
            log.error("handleRequirementLinksSave failed: {}", e.getMessage());
            JsonObject resp = new JsonObject();
            resp.addProperty("ok", false);
            resp.addProperty("error", e.getMessage());
            webSocketSessionManager.sendMessageJson(
                    homeBankingId, sessionId, gson.toJson(resp), "requirement.linksSaved");
        }
    }

    private void handleMessageByType(String type, JsonObject jsonEntry, Session session, String sessionId) {
        // Dispatch to the correct method based on the message type

        // Backend request-traffic log (ar_web_scanner_backend.log): one line per incoming
        // verb so runaway client loops (a verb re-sent many times per second) are visible.
        logBackend.info("REQ type={} sessionId={}", type, sessionId);

        boolean alreadySentMgsSocket = false;
        boolean authoritativeBotJobSnapshotPublished = false;
        ErrorMessage errorMessage = null;
        SplitDTO splitDTO = parseSplitDTO(jsonEntry);

        if (sessionId.equals("engine-perform-bot-job")
                || (splitDTO.getOperationId() != null
                        && splitDTO.getOperationId().equals("rowStatus"))) {
            JsonObject jsonRowStatus =
                    JsonParser.parseString(jsonEntry.get("body").getAsString()).getAsJsonObject();

            if (jsonRowStatus.has("instructionId") && jsonRowStatus.has("color")) {
                int instructionId = jsonRowStatus.get("instructionId").getAsInt();
                String color = jsonRowStatus.get("color").getAsString();

                rowStatus.setInstructionId(instructionId);
                rowStatus.setColor(color); // e.g. "#fcba03" deep carmine yellow
            }

            instructionRealtimePublisher.publishExecutionStatus(
                    splitDTO.getHomeBankingId(),
                    ScannerWorkspaceSessions.BOT_JOB_TASKS,
                    rowStatus.getInstructionId(),
                    rowStatus.getColor());
            return;
        }

        String sessionIdToSend = splitDTO.getSessionId();
        String operationId = splitDTO.getOperationId() != null ? splitDTO.getOperationId() : "";
        int homeBankingId = splitDTO.getHomeBankingId() != null ? splitDTO.getHomeBankingId() : -1;
        int botJobIdTask = splitDTO.getBotJobId() != null ? splitDTO.getBotJobId() : -1;
        String botJobNameTask = splitDTO.getBotJobName() != null ? splitDTO.getBotJobName() : "1# Default Block";

        // Block-level details
        int blockId = splitDTO.getBlockId() != null ? splitDTO.getBlockId() : -1;
        String blockName = splitDTO.getBlockName() != null ? splitDTO.getBlockName() : "";
        int blockOrderNum = splitDTO.getBlockOrderNumber() != null ? splitDTO.getBlockOrderNumber() : -1;
        boolean blockActive = splitDTO.getBlockActive() != null && splitDTO.getBlockActive();

        // Instruction-level details
        int instructionId = splitDTO.getInstructionId() != null ? splitDTO.getInstructionId() : -1;
        String instructionName = splitDTO.getInstructionName() != null ? splitDTO.getInstructionName() : "";
        int instructionOrderNum =
                splitDTO.getInstructionOrderNumber() != null ? splitDTO.getInstructionOrderNumber() : -1;
        boolean instrucionActive = splitDTO.getInstructionActive() != null && splitDTO.getInstructionActive();

        // Other instruction metadata
        String actions = splitDTO.getActions() != null ? splitDTO.getActions() : "";
        String operation = splitDTO.getOperation() != null ? splitDTO.getOperation() : "";

        // Hierarchy & variables
        int variableId = splitDTO.getVariableId() != null ? splitDTO.getVariableId() : -1;
        int parentId = splitDTO.getParentId() != null ? splitDTO.getParentId() : -1;
        int parentBlockId = splitDTO.getParentBlockId() != null ? splitDTO.getParentBlockId() : -1;

        // Optional fields for SplitDTO
        ElementDTO[] elementDetails =
                splitDTO.getElementDetails() != null ? splitDTO.getElementDetails() : new ElementDTO[0];

        // Optional fields for BlockSplitDTO
        DetailsDTO details = splitDTO.getDetails() != null ? splitDTO.getDetails() : new DetailsDTO();

        // Optional fields for UpdateRows
        List<UpdatedRow> updatedRows =
                splitDTO.getUpdatedRows() != null ? splitDTO.getUpdatedRows() : new ArrayList<>();

        String instrTable = "instruction";
        String blockTable = "block";
        String variableTable = "variable";
        String updteBlocks = "";
        String updateAction = null;

        int whereId = -1;

        if (sessionIdToSend != null) {
            if (isBotJobInstructionWorkspaceSession(sessionIdToSend)) {
                instrTable = "instruction";
                blockTable = "block";
                variableTable = "variable";
                whereId = splitDTO.getBotJobId() != null ? splitDTO.getBotJobId() : -1;
                updteBlocks = ScannerWorkspaceOperations.UPDATE_BLOCKS;
                updateAction = ScannerWorkspaceOperations.UPDATE_INSTRUCTIONS;
            } else if (isComponentInstructionWorkspaceSession(sessionIdToSend)) {
                instrTable = "component_instruction";
                blockTable = "component_block";
                variableTable = "component_variable";
                whereId = splitDTO.getHomeBankingId() != null ? splitDTO.getHomeBankingId() : -1;
                updteBlocks = ScannerWorkspaceOperations.UPDATE_BLOCKS_COMP;
                updateAction = ScannerWorkspaceOperations.COMPONENTS_UPDATE;
            }
        }

        errorMessage = performDataBase.loadBlocks(whereId, "", blockTable);

        List<Integer> previousBlockIds = (blockTable.equals("block")
                        ? performLists.getListBlock()
                        : performLists.getListBlockComp())
                .stream().map(BlockLoadDTO::getId).filter(Objects::nonNull).toList();

        if (errorMessage == null) {
            errorMessage = performDataBase.loadInstructions(whereId, -1, -1, instrTable);
        }
        if (errorMessage != null && !"ROW_MOVE".equals(type) && !"DELETE_INSTRUCTION".equals(type)
                && !"DELETE_BLOCK".equals(type)) {
            performMessage.errorMessageOperationFailed(errorMessage);
        }

        try {
            List<BotJobLoadDTO> listBotJob =
                    blockTable.equals("block") ? performLists.getListBotJob() : performLists.getListBotJobComp();

            List<BlockLoadDTO> listBlocks =
                    blockTable.equals("block") ? performLists.getListBlock() : performLists.getListBlockComp();

            if (!listBotJob.isEmpty() && listBlocks.isEmpty()) {
                errorMessage = performDataBase.loadBlocks(whereId, botJobNameTask, blockTable);
            }

            if (errorMessage == null) {
                errorMessage = performDataBase.checkGapsBlockOrder(listBlocks, blockTable, whereId, botJobNameTask);
            }

            switch (type) {
                case ScannerWorkspaceOperations.LAUNCH_BOT_JOB_TEST:
                    if (isMobileReturnSession(sessionIdToSend)) {
                        forwardToMobileReturn(homeBankingId, type, splitDTO);
                    }
                    alreadySentMgsSocket = true;
                    break;
                case ScannerWorkspaceOperations.ATTACHED_DEVICE:
                case ScannerWorkspaceOperations.DISCOVERY_APP:
                case ScannerWorkspaceOperations.SCANNER_APP:
                case ScannerWorkspaceOperations.MOBILE_SCROLL_UP:
                case ScannerWorkspaceOperations.MOBILE_SCROLL_DOWN:
                case ScannerWorkspaceOperations.MOBILE_BACK:
                case ScannerWorkspaceOperations.MOBILE_HOME:
                case ScannerWorkspaceOperations.MOBILE_RECENTS:
                case ScannerWorkspaceOperations.MOBILE_CLOSE_ALL:
                case ScannerWorkspaceOperations.MOBILE_NEXT_DONE:
                case ScannerWorkspaceOperations.MOBILE_CLOSE_KEYBOARD:
                    if (isMobileReturnSession(sessionIdToSend)) {
                        forwardToMobileReturn(homeBankingId, type, splitDTO);
                    }
                    alreadySentMgsSocket = true;
                    break;
                case ScannerWorkspaceOperations.REACTIVATE_BUTTONS:
                    if (isMobileReturnSession(sessionId)) {
                        splitDTO.setElementDetails(null);

                        // Convert your JsonObject to a proper JSON string
                        sendStatusButton(
                                splitDTO.getHomeBankingId(),
                                scannerMobilePickRoute.payloadSessionId(),
                                operationId,
                                "Activated button ",
                                splitDTO);
                    }
                    alreadySentMgsSocket = true;
                    break;

                case ScannerWorkspaceOperations.MOBILE_LOAD_JOBS: // DATA CONTROL FOR THE MOBILE SCANNER GRID
                    if (isMobileReturnSession(sessionId)) {
                        splitDTO.setOperationId(ScannerWorkspaceOperations.BOT_JOB_LIST);

                        try {
                            performDataBase.setMobileDevices(true);
                            errorMessage = performDataBase.loadQuickBotJobs();
                            if (errorMessage == null) {
                                List<BotJobLoadDTO> fetched = Optional.ofNullable(performLists.getQuickBotJobs())
                                        .orElse(Collections.emptyList());
                                sendMobileScannerGridPayload(
                                        homeBankingId, ScannerWorkspaceOperations.BOT_JOB_LIST, fetched);
                            }
                        } finally {
                            performDataBase.setMobileDevices(false);
                        }
                    }
                    alreadySentMgsSocket = true;
                    break;
                case ScannerWorkspaceOperations.MOBILE_VALIDATE_FIELDS: // DATA CONTROL FOR THE MOBILE SCANNER GRID
                    if (isMobileReturnSession(sessionId)) {
                        splitDTO.setOperationId(ScannerWorkspaceOperations.VALIDATE_FIELDS);

                        try {
                            performDataBase.setMobileDevices(true);
                            sendMobileScannerGridPayload(
                                    homeBankingId,
                                    ScannerWorkspaceOperations.VALIDATE_FIELDS,
                                    splitDTO.getFieldsToValidate());
                        } finally {
                            performDataBase.setMobileDevices(false);
                        }
                    }
                    alreadySentMgsSocket = true;
                    break;
                case ScannerSupportRequestPublisher.DOM_REVIEW_RESPONSE:
                    String reviewAction =
                            jsonEntry.has("action") ? jsonEntry.get("action").getAsString() : "cancel";
                    log.info("{} received: action={}", ScannerSupportRequestPublisher.DOM_REVIEW_RESPONSE, reviewAction);
                    ScannerSupportRequestHandlers.getInstance().handleDomReviewResponse(reviewAction);
                    alreadySentMgsSocket = true;
                    break;
                case ScannerSupportRequestPublisher.SUPPORT_REQUEST_RESPONSE:
                    String supportAction =
                            jsonEntry.has("action") ? jsonEntry.get("action").getAsString() : "cancel";
                    String supportMessage =
                            jsonEntry.has("message") ? jsonEntry.get("message").getAsString() : "";
                    log.info(
                            "{} received: action={}, messageLen={}",
                            ScannerSupportRequestPublisher.SUPPORT_REQUEST_RESPONSE,
                            supportAction,
                            supportMessage.length());
                    ScannerSupportRequestHandlers.getInstance()
                            .handleSupportRequestResponse(supportAction, supportMessage);
                    alreadySentMgsSocket = true;
                    break;
                case ScannerSupportRequestPublisher.REQUEST_SUPPORT_ELEMENTS:
                    log.info("{} received", ScannerSupportRequestPublisher.REQUEST_SUPPORT_ELEMENTS);
                    ScannerSupportRequestHandlers.getInstance().requestSupportElements();
                    alreadySentMgsSocket = true;
                    break;
                case ScannerSupportRequestPublisher.SUPPORT_REQUEST_ELEMENTS_RESPONSE:
                    String elementsSupportAction =
                            jsonEntry.has("action") ? jsonEntry.get("action").getAsString() : "cancel";
                    String elementsSupportMessage =
                            jsonEntry.has("message") ? jsonEntry.get("message").getAsString() : "";
                    String elementsJson = jsonEntry.has("elementDetails")
                            ? jsonEntry.get("elementDetails").toString()
                            : "[]";
                    log.info(
                            "{} received: action={}, messageLen={}, elementsJsonLen={}",
                            ScannerSupportRequestPublisher.SUPPORT_REQUEST_ELEMENTS_RESPONSE,
                            elementsSupportAction,
                            elementsSupportMessage.length(),
                            elementsJson.length());
                    ScannerSupportRequestHandlers.getInstance()
                            .handleSupportRequestElementsResponse(
                                    elementsSupportAction, elementsSupportMessage, elementsJson);
                    alreadySentMgsSocket = true;
                    break;
                case ScannerWorkspaceOperations.PRE_SCAN_PAGE:
                case ScannerWorkspaceOperations.PRE_SCAN_REFRESH_PAGE:
                case ScannerWorkspaceOperations.PRE_SCAN_CLEAR_GRID:
                    BotJobWorkspaceController.getInstance().preScanCommand(type, jsonEntry);
                    alreadySentMgsSocket = true;
                    break;
                case ScannerWorkspaceOperations.CLOSE_BROWSER:
                    if (isScannerElementPaneSession(sessionIdToSend)) {
                        splitDTO.setOperationId(ScannerWorkspaceOperations.CLOSE_BROWSER_OPERATION);
                        String jsonData = gson.toJson(splitDTO);
                        webSocketSessionManager.sendMessageJson(
                                homeBankingId,
                                sessionIdToSend,
                                jsonData,
                                ScannerWorkspaceOperations.CLOSE_BROWSER_OPERATION);
                    }
                    alreadySentMgsSocket = true;
                    break;
                case ScannerWorkspaceOperations.HOVERED_ROW:
                    if (isScannerToolSession(sessionIdToSend)) {
                        splitDTO.setOperationId(ScannerWorkspaceOperations.HIGHLIGHT);
                        String jsonData = gson.toJson(splitDTO);
                        webSocketSessionManager.sendMessageJson(homeBankingId, sessionIdToSend, jsonData, null);
                    }
                    alreadySentMgsSocket = true;
                    break;
                case ScannerWorkspaceOperations.CLEAR_HOVER_PICK_FILE: {
                    // Picker UI's "Clear Grid All" button when Hover Pick mode is on.
                    // Truncates elementDTO-HP.json + AI-ElementDTO-HP.json so the next pick
                    // starts a fresh cumulative list.
                    String hpClearPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_DB);
                    performMessage.clearHoverPickJson(hpClearPath);
                    alreadySentMgsSocket = true;
                    break;
                }
                case ScannerWorkspaceOperations.SEARCH_TOOL:
                    if (isScannerGridSession(sessionIdToSend)) {
                        // 1. UI gets the raw DTOs immediately (resolver enrichment is async-from-UI's POV).
                        String jsonData = gson.toJson(splitDTO);
                        webSocketSessionManager.sendMessageJson(
                                homeBankingId, sessionIdToSend, jsonData, scannerMobilePickRoute.payloadOperationId());

                        String jsonPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_DB);
                        var arWebDriver = performActions.getCurrentARWebDriver();
                        var playwrightDriver = arWebDriver == null ? null : arWebDriver.currentPlaywrightDriver();

                        // 2. DOM rects (needed by OCR correlator).
                        PageDiagnosticDumper.dumpRectsFromElements(
                                playwrightDriver, splitDTO.getElementDetails(), jsonPath, "page-HP");

                        // 3. OCR pipeline writes ocr-correlation-HP.json that the resolver consumes.
                        PageOcrDumper.runAndDump(
                                playwrightDriver, splitDTO.getElementDetails(), jsonPath, "page-HP", null, null);

                        // 4. Resolve someText + definedName from DOM + OCR (mutates DTOs in place).
                        //    Pass the active OcrConfig so the resolver picks up the OCR weight knobs
                        //    from the "DOM-First (Anti-Drift)" profile when the user has it active.
                        {
                            Integer cfgHbId = homeBankingId > 0 ? homeBankingId : null;
                            Integer cfgHomeUrlId = currentHomeUrlId();
                            com.allinweb.ch.model.OcrConfig resolverCfg =
                                    com.allinweb.ch.facade.OcrConfigService.getInstance()
                                            .resolveFor(cfgHbId, cfgHomeUrlId);
                            ElementTextResolver.resolveAll(
                                    splitDTO.getElementDetails(),
                                    java.nio.file.Paths.get(
                                            jsonPath,
                                            com.allinweb.ch.util.PageDiagnosticDumper.SUBFOLDER,
                                            "ocr-correlation-HP.json"),
                                    resolverCfg);
                        }

                        // 4b. Persist locators for Roadmap 3 recovery (no-op if defined_name is empty).
                        try {
                            Integer hbId = homeBankingId > 0 ? homeBankingId : null;
                            Integer homeUrlId = currentHomeUrlId();
                            ElementLocatorRepository.getInstance()
                                    .upsertOnPickBatch(splitDTO.getElementDetails(), hbId, homeUrlId);
                        } catch (Exception locEx) {
                            log.warn("Locator upsert failed (non-fatal): {}", locEx.getMessage());
                        }

                        // 5. Persist enriched DTOs in CUMULATIVE mode — each hover-pick appends
                        // to the running list (deduped by xPath). The picker UI clears the file
                        // via the new "Clear Grid All" button when Hover Pick mode is on.
                        List<String> excludeList = List.of("optional", "blockMarked", "editMode");
                        performMessage.outputJsonElementDTO(
                                splitDTO.getElementDetails(), excludeList, "elementDTO-HP", jsonPath, true);
                        excludeList = List.of(
                                "optional",
                                "blockMarked",
                                "editMode",
                                "id",
                                "attributeData",
                                "typeElement",
                                "customXPath",
                                "shadowRoot",
                                "nestedShadow",
                                "searchAttributeValue",
                                "attributeType",
                                "attributeValue");
                        performMessage.outputJsonElementDTO(
                                splitDTO.getElementDetails(), excludeList, "AI-ElementDTO-HP", jsonPath, true);
                    } else if (isMobileReturnSession(sessionIdToSend)) {
                        sendMobileScannerGridPayload(
                                homeBankingId, scannerMobilePickRoute.payloadOperationId(), splitDTO);

                        String jsonPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_DB);
                        var arWebDriver = performActions.getCurrentARWebDriver();
                        var playwrightDriver = arWebDriver == null ? null : arWebDriver.currentPlaywrightDriver();

                        PageDiagnosticDumper.dumpRectsFromElements(
                                playwrightDriver, splitDTO.getElementDetails(), jsonPath, "page-HP");

                        PageOcrDumper.runAndDump(
                                playwrightDriver, splitDTO.getElementDetails(), jsonPath, "page-HP", null, null);

                        ElementTextResolver.resolveAll(
                                splitDTO.getElementDetails(),
                                java.nio.file.Paths.get(
                                        jsonPath,
                                        com.allinweb.ch.util.PageDiagnosticDumper.SUBFOLDER,
                                        "ocr-correlation-HP.json"));

                        List<String> excludeList = List.of("optional", "blockMarked", "editMode");
                        performMessage.outputJsonElementDTO(
                                splitDTO.getElementDetails(), excludeList, "elementDTO-HP", jsonPath);
                        excludeList = List.of(
                                "optional",
                                "blockMarked",
                                "editMode",
                                "id",
                                "attributeData",
                                "typeElement",
                                "customXPath",
                                "shadowRoot",
                                "nestedShadow",
                                "searchAttributeValue",
                                "attributeType",
                                "attributeValue");
                        performMessage.outputJsonElementDTO(
                                splitDTO.getElementDetails(), excludeList, "AI-ElementDTO-HP", jsonPath);
                    }
                    alreadySentMgsSocket = true;
                    break;
                case ScannerWorkspaceOperations.ACTION_EXECUTOR:
                    // Route actionExecutor results to the ActionExecutorClient
                    actionExecutorClient.onResult(jsonEntry);
                    alreadySentMgsSocket = true;
                    break;
                case ScannerWorkspaceOperations.UPDATE_LIST_ELEMENTS:
                    // calls perform list block update
                    if (isPerformListDataSession(sessionIdToSend)) {
                        splitDTO.setType(ScannerWorkspaceOperations.UPDATE_LIST_ELEMENTS);
                        String jsonData = gson.toJson(splitDTO);
                        webSocketSessionManager.sendMessageJson(
                                homeBankingId,
                                ScannerWorkspaceSessions.PERFORM_LIST_DATA,
                                jsonData,
                                ScannerWorkspaceOperations.UPDATE_LIST_ELEMENTS);
                    }
                    alreadySentMgsSocket = true;
                    break;
                case ScannerWorkspaceOperations.NEW_ELEMENT_DTO:
                case ScannerWorkspaceOperations.SEND_ALL_ELEMENTS_DTO:
                case ScannerWorkspaceOperations.UPDATE_ALL_ELEMENTS_DTO:
                case ScannerWorkspaceOperations.DEL_ELEMENT_DTO:
                case ScannerWorkspaceOperations.DETAILS_ELEMENT_DTO:
                case ScannerWorkspaceOperations.TEST_CLICK_DTO:
                case ScannerWorkspaceOperations.TEST_INPUT_DTO: {
                    if (!performLists.getListBotJob().isEmpty() && splitDTO.getBotJobId() != null) {
                        performLists.getListBotJob().stream()
                                .filter(j -> java.util.Objects.equals(j.getId(), splitDTO.getBotJobId()))
                                .findFirst()
                                .ifPresent(j -> {
                                    splitDTO.setBotJobName(j.getName());
                                    splitDTO.setProjectType(j.getPriority());
                                });
                    }
                    if (botJobWorkspaceCapabilityService.supportsNativeMobileTools(splitDTO.getProjectType())) {
                        sessionIdToSend = scannerMobileTestRoute.returnSessionId();
                        splitDTO.setSessionId(sessionIdToSend);
                    }

                    if (isMobileReturnSession(sessionIdToSend)) {

                        // Safely extract the first element ID (if present)
                        Integer elementId = Optional.ofNullable(splitDTO.getElementDetails())
                                .filter(arr -> arr.length > 0)
                                .map(arr -> arr[0])
                                .map(ElementDTO::getId)
                                .orElse(null);

                        // Find matching instruction by variableId
                        InstructionLoad matchingInstruction =
                                Optional.ofNullable(performLists.getListInstruction())
                                        .orElse(Collections.emptyList())
                                        .stream()
                                        .filter(i -> Objects.equals(i.getId(), elementId))
                                        .findFirst()
                                        .orElse(null);

                        // 2) Apply only non-empty values into splitDTO and elementDetails[0]
                        if (matchingInstruction != null) {
                            // >>> Add AttrData:* references into elementDetails.attributesData
                            splitDTO.setElementDetails(null);
                            SplitDTO.applyAttrDataFromReferences(splitDTO, matchingInstruction);

                            SplitDTO.applyInstructionToSplit(splitDTO, matchingInstruction);
                        }

                        splitDTO.setOperationId(type);

                        if (!ScannerWorkspaceOperations.NEW_ELEMENT_DTO.equals(type)
                                && !ScannerWorkspaceOperations.SEND_ALL_ELEMENTS_DTO.equals(type)) {
                            forwardToMobileReturn(homeBankingId, type, splitDTO);
                        } else {
                            ErrorMessage applyError = null;
                            try {
                                BotJobDetailsWorkspaceRegistry.getInstance().require(splitDTO.getBotJobId());
                                applyError = PreScanApplyService.getInstance().applyElements(splitDTO);
                            } catch (IllegalArgumentException unavailable) {
                                performMessage.errorMessage(
                                        "Bot Job Details Not Open",
                                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Operation Failed!</span>",
                                        "<span style='color: #1565C0; font-weight: bold;'>" + splitDTO.getBotJobName()
                                                + "</span>.",
                                        "<span style='color: #E65100; font-weight: bold;'>The Integration AR Mobile is waiting for the Bot Job to be open.</span>",
                                        "<span style='font-style: italic;'>Details: Please select and open the Bot Job on AR Web.</span>",
                                        0);
                                break;
                            }
                            if (applyError != null) {
                                log.error("AR Mobile insert failed: {}", applyError.getErrorMessage());
                                performMessage.errorMessageOperationFailed(applyError);
                            }
                        }
                    } else if (splitDTO.getElementDetails() != null && splitDTO.getElementDetails().length > 0) {
                        boolean isInsertType = ScannerWorkspaceOperations.NEW_ELEMENT_DTO.equals(type)
                                || ScannerWorkspaceOperations.SEND_ALL_ELEMENTS_DTO.equals(type);
                        boolean isTestType = ScannerWorkspaceOperations.TEST_CLICK_DTO.equals(type)
                                || ScannerWorkspaceOperations.TEST_INPUT_DTO.equals(type);
                        boolean paneOpen =
                                isScannerElementPaneOpen();
                        if (isTestType && !paneOpen) {
                            // PRE SCAN dashboard row test with AR Web Factory closed: the scanned
                            // page lives in the isolated pre-scan browser, so the test runs there.
                            BotJobWorkspaceController.getInstance().preScanElementTest(splitDTO, type);
                        } else if (isInsertType && !paneOpen) {
                            // PRE SCAN dashboard Apply with AR Web Factory closed: the pane
                            // session would swallow the message, so persist via the pane-free
                            // service instead (same insert + task grid refresh).
                            ErrorMessage applyError =
                                    PreScanApplyService.getInstance().applyElements(splitDTO);
                            if (applyError != null) {
                                log.error("PRE SCAN Apply failed: {}", applyError.getErrorMessage());
                                performMessage.errorMessageOperationFailed(applyError);
                            }
                        } else {
                            scannerElementPanePublisher.publishRawJson(gson.toJson(splitDTO));
                        }
                    }
                    alreadySentMgsSocket = true;
                    break;
                }
                case "RESPONSE_BACK":
                    splitDTO.setType("MARTINI");
                    String jsonData = gson.toJson(splitDTO);
                    webSocketSessionManager.sendMessageJson(homeBankingId, session, "Martini", jsonData, null);
                    alreadySentMgsSocket = true;
                    break;
                case "COMPONENT_INJECT":
                    injectBlockComponent(splitDTO);
                    // calls perform list block update
                    splitDTO.setType(ScannerWorkspaceOperations.UPDATE_BLOCKS);
                    jsonData = gson.toJson(splitDTO);
                    webSocketSessionManager.sendMessageJson(
                            homeBankingId,
                            ScannerWorkspaceSessions.PERFORM_LIST_DATA,
                            jsonData,
                            ScannerWorkspaceOperations.UPDATE_BLOCKS);
                    alreadySentMgsSocket = true;
                    break;
                case "BLOCK_CREATE":
                case "CREATE_BLOCK":
                    BlockCreationService.Result createResult =
                            BlockCreationService.getInstance().createFrom(splitDTO);
                    errorMessage = createResult.error();
                    if (errorMessage == null) {
                        splitDTO.setBlockId(createResult.newBlockId());
                        splitDTO.setBlockOrderNumber(createResult.newBlockOrderNumber());
                        splitDTO.setBlocks(mapBlockOptions(blockTable, whereId));
                    }
                    splitDTO.setType(updteBlocks);
                    jsonData = gson.toJson(splitDTO);
                    scannerBlockUpdatePublisher.publishBlockCreationUpdate(homeBankingId, jsonData, updteBlocks);
                    alreadySentMgsSocket = false;
                    break;
                case "BLOCKS_SPLITTER":
                    errorMessage = CommandEditorService.getInstance().executeSplit(splitDTO, () -> splitBlocks(splitDTO));

                    JsonObject splitResponse = new JsonObject();
                    splitResponse.addProperty("ok", errorMessage == null);
                    splitResponse.addProperty("requestId", splitDTO.getRequestId());
                    if (errorMessage == null) {
                        splitResponse.add("blocks", gson.toJsonTree(performLists.getListBlock()));
                        splitResponse.add("instructions", gson.toJsonTree(
                                performLists.buildJsonViewData(performLists.getListBotJob())));
                    } else {
                        splitResponse.addProperty("errorTitle", errorMessage.getErrorTitle());
                        splitResponse.addProperty("errorHeader", errorMessage.getErrorHeader());
                        splitResponse.addProperty("error", errorMessage.getErrorMessage());
                    }
                    sendCommandEditorResponse(
                            homeBankingId,
                            sessionIdToSend,
                            "instructionGraph.applySplitResponse",
                            splitResponse);

                    // calls perform list block update
                    splitDTO.setType(updteBlocks);
                    jsonData = gson.toJson(splitDTO);
                    webSocketSessionManager.sendMessageJson(
                            homeBankingId, ScannerWorkspaceSessions.PERFORM_LIST_DATA, jsonData, updteBlocks);
                    alreadySentMgsSocket = false;
                    break;
                case "BLOCK_MOVE":
                    try {
                        if (blockTable != null) {
                            List<BlockLoadDTO> mappedBlocks =
                                    mapToBlockLoad(homeBankingId, splitDTO.getUpdatedBlocks());
                            errorMessage =
                                    performDataBase.updateSwiftBlockOrderNumber(blockTable, whereId, mappedBlocks);

                            // UPDATE BLOCK ORDER MEMORY LIST
                            if (errorMessage == null) {
                                performLists.updateMemorySwiftBlockOrder(blockTable, whereId, mappedBlocks);
                            }

                            // calls perform list block update
                            splitDTO.setType(updteBlocks);
                            jsonData = gson.toJson(splitDTO);
                            webSocketSessionManager.sendMessageJson(
                                    homeBankingId, ScannerWorkspaceSessions.PERFORM_LIST_DATA, jsonData, updteBlocks);
                        }

                    } catch (Exception error) {
                        log.error("Error: " + error.getMessage());
                    }
                    alreadySentMgsSocket = false;
                    break;
                case "ROW_UPDATE":
                    InstructionLoad instructionLoad = SplitDTO.mapSplitToInstruction(splitDTO);
                    errorMessage = performDataBase.rowsUpdateName(
                            instrTable, whereId, Collections.singletonList(instructionLoad));

                    if (errorMessage == null) {
                        errorMessage = performDataBase.loadAllParents(instrTable, whereId, splitDTO.getInstructionId());

                        if (errorMessage == null) {
                            if (!performLists.getListParentOperations().isEmpty()) {
                                for (ParentOperations parent : performLists.getListParentOperations()) {
                                    if ("GET".equals(parent.getActions()) || "SET".equals(parent.getActions())) {
                                        if (parent.getParentName() != null) {
                                            String[] parts =
                                                    parent.getOperations().split(":");
                                            parent.setOperations(parent.getParentName() + ":" + parts[1]);
                                        }
                                    }
                                }

                                errorMessage = performDataBase.rowsGetUpdateName(
                                        instrTable, whereId, performLists.getListParentOperations());

                                // UPDATE MEMORY LIST FOR PARENTS OPERATION NAMES
                                //                            performLists.updateMemoryParentOpenName(instrTable,
                                // whereId,
                                // listParents);
                            }
                        }
                    }

                    // UPDATE MEMORY LIST FOR PARENTS INSTRUCTION NAME
                    if (errorMessage == null) {
                        performLists.updateMemoryInstructionName(
                                instrTable, whereId, Collections.singletonList(instructionLoad));
                        performLists.updateMemoryParentOpenName(
                                instrTable, whereId, performLists.getListParentOperations());
                    }

                    alreadySentMgsSocket = false;
                    break;
                case "ROW_MOVE":
                    // Full pipeline (request-id, idempotency, revision, graph validation,
                    // transactional persist, state refresh) lives in RowMoveService — one
                    // method per concern. Works for both Bot Job and Components workspaces.
                    errorMessage = RowMoveService.getInstance().move(splitDTO, instrTable, blockTable, whereId);

                    // ROW_MOVE refreshes Bot Job Details through the authoritative mutation response
                    // and updateInstructions snapshot below. Do not also emit the legacy
                    // perform-list-data/UPDATE_BLOCKS frame here; it only carries partial row layout
                    // data and can race the full grid refresh consumed by React.
                    alreadySentMgsSocket = false;
                    break;
                case "INSERT_BEFORE":
                case "INSERT_AFTER":
                case "INSERT_NEW":
                case "INSERT_AFTER_ELSEIF":
                case "INSERT_BEFORE_ELSEIF":
                case "EDIT_OPERATION":
                    performDataBase.loadBlocks(whereId, "", blockTable);
                    injectStepAfterOrBefore(blockTable, whereId, splitDTO);

                    if (type.equals("INSERT_AFTER_ELSEIF") || type.equals("INSERT_BEFORE_ELSEIF")) {
                        alreadySentMgsSocket = false;
                        if (isBotJobTasksSession(sessionIdToSend)) {
                            performLists.getListBotJob().clear();
                        } else if (isComponentInstructionWorkspaceSession(sessionIdToSend)) {
                            performLists.getListBotJob().clear();
                        }
                    } else {
                        alreadySentMgsSocket = true;
                    }
                    break;
                case "BLOCK_ORDER":
                    if (!splitDTO.getUpdatedBlocks().isEmpty()) {
                        errorMessage = performDataBase.loadBlocks(whereId, "", blockTable);

                        // updateBlockOrderNumber  ALREADY UPDATE MEMORY LIST
                        if (errorMessage == null) {
                            errorMessage = performDataBase.updateBlockOrderNumber(blockTable, whereId, true);
                        }

                        // calls perform list block update
                        splitDTO.setType(updteBlocks);
                        jsonData = gson.toJson(splitDTO);
                        webSocketSessionManager.sendMessageJson(
                                homeBankingId, ScannerWorkspaceSessions.PERFORM_LIST_DATA, jsonData, updteBlocks);

                        alreadySentMgsSocket = false;
                    }
                    break;
                case "INSTRUCTION_STATUS":
                    errorMessage = performDataBase.updateInstructionStatus(
                            instrTable, whereId, instructionId, blockId, parentId, actions, instrucionActive);

                    // MEMORY UPDATE
                    if (errorMessage == null) {
                        performLists.updateMemoryInstructionStatusUpdate(
                                instrTable, whereId, instructionId, instrucionActive);
                    }
                    alreadySentMgsSocket = false;
                    break;
                case "ACTIONS_UPDATE":
                    errorMessage = performDataBase.updateInstructionActions(
                            instrTable, whereId, instructionId, blockId, actions);

                    // MEMORY UPDATE
                    if (errorMessage == null) {
                        performLists.updateMemoryInstructionActionsUpdate(instrTable, whereId, instructionId, actions);
                    }

                    if (instrTable.equals("instruction")) {
                        performLists.getListBotJob().clear();
                    } else {
                        performLists.getListBotJobComp().clear();
                    }

                    alreadySentMgsSocket = false;
                    break;
                case "FORCE_COORDINATES_UPDATE":
                    // Flag-column update (F / E / T / N bits) — analogous to ACTIONS_UPDATE
                    // but writes to the force_coordinates column introduced by the
                    // 2026-04-25__force_coordinates_varchar migration.
                    errorMessage = performDataBase.updateInstructionForceCoordinates(
                            instrTable, whereId, instructionId, blockId, splitDTO.getForceCoordinates());

                    // MEMORY UPDATE
                    if (errorMessage == null) {
                        performLists.updateMemoryInstructionForceCoordinatesUpdate(
                                instrTable, whereId, instructionId, splitDTO.getForceCoordinates());
                    }

                    if (instrTable.equals("instruction")) {
                        performLists.getListBotJob().clear();
                    } else {
                        performLists.getListBotJobComp().clear();
                    }

                    alreadySentMgsSocket = false;
                    break;
                case "BLOCK_STATUS":
                    errorMessage = performDataBase.updateBlockStatus(blockTable, whereId, blockId, blockActive);

                    if (errorMessage == null) {
                        performDataBase.updateInstructionStatusByBlock(instrTable, whereId, blockId, blockActive);
                    }

                    if (errorMessage == null) {
                        performLists.updateMemoryBlockStatusUpdate(blockTable, whereId, blockId, blockActive);
                    }

                    alreadySentMgsSocket = false;
                    break;
                case "BLOCK_UPDATE":
                    errorMessage = performDataBase.updateBlockName(whereId, blockTable, blockId, blockName);
                    splitDTO.setType(updteBlocks);

                    // MEMORY UPDATE BLOCK NAME
                    performLists.updateMemoryBlockName(blockTable, whereId, blockId, blockName);

                    jsonData = gson.toJson(splitDTO);
                    webSocketSessionManager.sendMessageJson(
                            homeBankingId, ScannerWorkspaceSessions.PERFORM_LIST_DATA, jsonData, updteBlocks);
                    alreadySentMgsSocket = false;
                    break;
                case "DELETE_INSTRUCTION": {
                    String deleteRequestId = splitDTO.getRequestId() == null ? "" : splitDTO.getRequestId().trim();
                    if (deleteRequestId.isEmpty()) {
                        errorMessage = new ErrorMessage(
                                "Delete Instruction Refused",
                                "Request ID is required",
                                "Refresh the grid and try the deletion again.");
                        break;
                    }
                    synchronized (processedInstructionDeletes) {
                        if (processedInstructionDeletes.containsKey(deleteRequestId)) break;
                    }
                    errorMessage = CommandEditorService.getInstance().validateDeleteRevision(splitDTO);
                    if (errorMessage != null) break;

                    errorMessage = performDataBase.loadInstructions(whereId, -1, -1, instrTable);
                    if (errorMessage != null) break;
                    InstructionLoad storedDelete = findInstructionInMemory(instrTable, whereId, instructionId);
                    if (storedDelete == null) {
                        errorMessage = new ErrorMessage(
                                "Delete Instruction Refused",
                                "Instruction not found",
                                "The selected instruction no longer exists. Refresh the grid.");
                        break;
                    }
                    int storedBlockId = storedDelete.getBlockId() == null ? -1 : storedDelete.getBlockId();
                    errorMessage = CommandEditorService.getInstance()
                            .validateDeleteMetadata(splitDTO, storedDelete);
                    if (errorMessage != null) break;

                    boolean isIfFamily = actions.equalsIgnoreCase("IF")
                            || actions.equalsIgnoreCase("ELSE")
                            || actions.equalsIgnoreCase("ENDIF")
                            || actions.equalsIgnoreCase("ELSEIF");

                    if (isIfFamily) {
                        List<InstructionLoad> conditionalBlock = (instrTable.equals("instruction")
                                        ? performLists.getListInstruction()
                                        : performLists.getListInstructionComp())
                                .stream()
                                .filter(row -> row != null && Objects.equals(row.getBlockId(), storedBlockId))
                                .sorted(Comparator.comparingInt(row -> row.getInstructionOrderNumber() == null
                                        ? Integer.MAX_VALUE
                                        : row.getInstructionOrderNumber()))
                                .toList();
                        String conditionalError = new ConditionalGraphValidator().validate(conditionalBlock);
                        if (conditionalError != null) {
                            errorMessage = new ErrorMessage(
                                    "Delete Instruction Refused",
                                    "Invalid conditional graph",
                                    conditionalError);
                            break;
                        }
                    }

                    // only IF/ELSE/ENDIF delete the whole group (root IF)
                    boolean isIfFamilyRootDelete = actions.equalsIgnoreCase("IF")
                            || actions.equalsIgnoreCase("ELSE")
                            || actions.equalsIgnoreCase("ENDIF");

                    int ifRootId = actions.equalsIgnoreCase("IF") ? instructionId : splitDTO.getParentId();

                    // ELSEIF must load for itself (doesn't matter much now, but keep correct)
                    int parentsRootId = (isIfFamilyRootDelete ? ifRootId : instructionId);
                    errorMessage = performDataBase.loadAllParents(instrTable, whereId, parentsRootId);

                    boolean continueDelete = true;
                    boolean deleteParents = false;

                    if (errorMessage == null) {

                        // Dependent-row cascades require explicit React impact confirmation.
                        if (!performLists.getListParentOperations().isEmpty() && !isIfFamily) {
                            String dependencies = performLists.getListParentOperations().stream()
                                    .map(parent -> parent.getName() + " (" + parent.getInstructionId() + ")")
                                    .collect(Collectors.joining(", "));
                            errorMessage = new ErrorMessage(
                                    "Delete Instruction Refused",
                                    "Steps are attached to this instruction",
                                    "Dependent steps: " + dependencies);
                            continueDelete = false;
                            deleteParents = false;

                        } else {
                            // IF-family OR no children -> proceed
                            continueDelete = true;
                            deleteParents = true;
                        }

                    }

                    if (continueDelete && deleteParents && errorMessage == null) {
                        List<InstructionLoad> currentDeleteRows = instrTable.equals("instruction")
                                ? performLists.getListInstruction()
                                : performLists.getListInstructionComp();
                        List<Integer> deleteIds;
                        if (actions.equalsIgnoreCase("LOOP") || actions.equalsIgnoreCase("REFRESH_LOOP")) {
                            List<InstructionLoad> blockRows = currentDeleteRows.stream()
                                    .filter(row -> row != null && Objects.equals(row.getBlockId(), storedBlockId))
                                    .toList();
                            deleteIds = new LoopGroupService().groupIds(blockRows, splitDTO.getInstructionId());
                        } else if (actions.equalsIgnoreCase("ELSEIF")) {
                            List<InstructionLoad> blockRows = currentDeleteRows.stream()
                                    .filter(row -> row != null && Objects.equals(row.getBlockId(), storedBlockId))
                                    .toList();
                            deleteIds = new ConditionalBranchService()
                                    .elseIfBranchIds(blockRows, splitDTO.getInstructionId());
                        } else if (isIfFamilyRootDelete) {
                            deleteIds = currentDeleteRows.stream()
                                        .filter(row -> row != null && row.getId() != null
                                                && (row.getId() == ifRootId
                                                        || (row.getParentId() != null && row.getParentId() == ifRootId)))
                                        .map(InstructionLoad::getId)
                                        .distinct()
                                        .toList();
                        } else {
                            deleteIds = List.of(splitDTO.getInstructionId());
                        }

                        if (deleteIds.isEmpty()) {
                            errorMessage = new ErrorMessage(
                                    "Delete Instruction Refused",
                                    "Instruction group is invalid",
                                    "Refresh the grid before deleting this instruction group.");
                            break;
                        }

                        errorMessage = performDataBase.deleteInstructionGraphAtomic(
                                instrTable, whereId, deleteIds);

                        if (errorMessage == null) {
                            for (Integer removedId : deleteIds) {
                                performLists.updateMemoryRemoveInstructionId(instrTable, whereId, removedId);
                            }

                            if (errorMessage == null) {
                                errorMessage = performDataBase.loadInstructions(whereId, -1, -1, instrTable);
                            }

                            List<InstructionLoad> rowsList = instrTable.equals("instruction")
                                    ? performLists.getListInstruction()
                                    : performLists.getListInstructionComp();

                            InstructionLoad hasExcelGotoOneBlock = hasOnlyExcelGoto(rowsList, instrTable);

                            if (hasExcelGotoOneBlock != null) {
                                errorMessage = performDataBase.deleteInstruction(
                                        instrTable, whereId, hasExcelGotoOneBlock, false);
                            }

                            if (errorMessage == null) {
                                errorMessage =
                                        deleteNullsAndMemoryReload(instrTable, blockTable, whereId, previousBlockIds);
                            }

                            if (errorMessage == null) {
                                performDataBase.updateBlockOrderNumber(blockTable, whereId, true);
                            }

                            if (errorMessage == null) {
                                final int finalWhereId = whereId;

                                List<BlockLoadDTO> blockLoad = instrTable.equals("instruction")
                                        ? performLists.getListBotJob().stream()
                                                .filter(b -> Objects.equals(b.getId(), finalWhereId))
                                                .findFirst()
                                                .map(b -> b.getBlockLoadDTOList().stream()
                                                        .filter(block ->
                                                                Objects.equals(block.getId(), splitDTO.getBlockId()))
                                                        .toList())
                                                .orElse(Collections.emptyList())
                                        : performLists.getListBotJobComp().stream()
                                                .filter(b -> Objects.equals(b.getHomeBankingId(), finalWhereId))
                                                .findFirst()
                                                .map(b -> b.getBlockLoadDTOList().stream()
                                                        .filter(block ->
                                                                Objects.equals(block.getId(), splitDTO.getBlockId()))
                                                        .toList())
                                                .orElse(Collections.emptyList());

                                errorMessage =
                                        performDataBase.reorderInstructionsListBlock(blockLoad, instrTable, true);
                            }

                            splitDTO.setType(updteBlocks);
                            jsonData = gson.toJson(splitDTO);
                            webSocketSessionManager.sendMessageJson(
                                    homeBankingId, ScannerWorkspaceSessions.PERFORM_LIST_DATA, jsonData, updteBlocks);
                        }
                    }

                    if (errorMessage == null) {
                        rememberCompletedRequest(processedInstructionDeletes, deleteRequestId);
                    }
                    alreadySentMgsSocket = false;
                    break;
                }
                case "DELETE_BLOCK":
                    String blockDeleteRequestId = splitDTO.getRequestId() == null ? "" : splitDTO.getRequestId().trim();
                    if (blockDeleteRequestId.isEmpty()) {
                        errorMessage = new ErrorMessage("Delete Block Refused", "Request ID is required", "Refresh the grid and try again.");
                        break;
                    }
                    synchronized (processedBlockDeletes) {
                        if (processedBlockDeletes.containsKey(blockDeleteRequestId)) break;
                    }
                    errorMessage = CommandEditorService.getInstance().validateDeleteRevision(splitDTO);
                    if (errorMessage != null) break;
                    errorMessage = performDataBase.deleteBlockGraphAtomic(blockTable, whereId, splitDTO.getBlockId());
                    if (errorMessage == null) errorMessage = performDataBase.loadInstructions(whereId, -1, -1, instrTable);
                    if (errorMessage == null) errorMessage = performDataBase.loadBlocks(whereId, "", blockTable);
                    // loadInstructions/loadBlocks refresh only the GLOBAL lists; the
                    // task-update snapshot pushed below is built from the NESTED
                    // listBotJob.blockLoadDTOList (buildJsonViewData), so the deleted
                    // block must be evicted there too or it reappears on the board.
                    if (errorMessage == null) {
                        performLists.updateMemoryRemoveBlockIds(blockTable, whereId, List.of(splitDTO.getBlockId()));
                    }
                    if (errorMessage == null) rememberCompletedRequest(processedBlockDeletes, blockDeleteRequestId);

                    // calls perform list block update
                    splitDTO.setType(updteBlocks);
                    jsonData = gson.toJson(splitDTO);
                    webSocketSessionManager.sendMessageJson(
                            homeBankingId, ScannerWorkspaceSessions.PERFORM_LIST_DATA, jsonData, updteBlocks);

                    alreadySentMgsSocket = false;
                    break;
                case "BLOCK_ROLLBACK":
                    errorMessage = performDataBase.rollBackBlocksRows("instruction", splitDTO);

                    if (errorMessage == null) {
                        errorMessage =
                                deleteNullsRollbackAndMemoryReload(instrTable, blockTable, whereId, previousBlockIds);
                    }

                    // calls perform list block update
                    splitDTO.setType(updteBlocks);
                    jsonData = gson.toJson(splitDTO);
                    webSocketSessionManager.sendMessageJson(
                            homeBankingId, ScannerWorkspaceSessions.PERFORM_LIST_DATA, jsonData, updteBlocks);
                    alreadySentMgsSocket = false;
                    break;
                default:
                    webSocketSessionManager.sendMessageJson(
                            homeBankingId, session, type, "Action type : \"" + type + "\"", "cannot be processed");
                    break;
            }

            if (errorMessage == null) {
                errorMessage = performDataBase.checkGapsBlockOrder(listBlocks, blockTable, whereId, botJobNameTask);
            }
        } catch (Exception error) {
            log.error("Error: " + error.getMessage());

            if (errorMessage == null) {
                errorMessage = deleteNullsAndMemoryReload(instrTable, blockTable, whereId, previousBlockIds);
            }
        }

        if (errorMessage != null
                && !"ROW_MOVE".equals(type)
                && !"DELETE_INSTRUCTION".equals(type)
                && !"DELETE_BLOCK".equals(type)) {
            performMessage.errorMessageOperationFailed(errorMessage);
        }

        if (!alreadySentMgsSocket && isBotJobTasksSession(sessionIdToSend)) {
            if (performLists.getListBotJob().isEmpty()) {
                errorMessage = performDBEngine.loadCompleteJobs(botJobIdTask);
                if (errorMessage != null) {
                    performMessage.errorMessageOperationFailed(errorMessage);
                }
            }
        } else if (!alreadySentMgsSocket && isComponentInstructionWorkspaceSession(sessionIdToSend)) {
            if (performLists.getListBotJobComp().isEmpty()) {
                errorMessage = performDataBase.loadComponentsComplete(homeBankingId, botJobIdTask, botJobNameTask);
                if (errorMessage != null) {
                    performMessage.errorMessageOperationFailed(errorMessage);
                }
            }
        }

        // Send mutation acknowledgement before the refreshed grid. The React grid consumes the
        // newest WebSocket message in a batched render, so the task-update operation must be
        // the final message or a following success response can hide the live refresh.
        if ("ROW_MOVE".equals(type) || "DELETE_INSTRUCTION".equals(type) || "DELETE_BLOCK".equals(type)) {
            JsonObject mutationResponse = new JsonObject();
            mutationResponse.addProperty("ok", errorMessage == null);
            mutationResponse.addProperty("requestId", splitDTO.getRequestId());
            if (errorMessage != null) {
                mutationResponse.addProperty("errorTitle", errorMessage.getErrorTitle());
                mutationResponse.addProperty("errorHeader", errorMessage.getErrorHeader());
                mutationResponse.addProperty("error", errorMessage.getErrorMessage());
            }
            String responseOperation = "ROW_MOVE".equals(type)
                    ? "instructionEditor.rowMoveResponse"
                    : "DELETE_BLOCK".equals(type)
                            ? "instructionEditor.blockDeleteResponse"
                            : "instructionEditor.deleteResponse";
            instructionRealtimePublisher.publishResponse(
                    homeBankingId, sessionIdToSend, responseOperation, mutationResponse);
            log.info(
                    "INSTRUCTION_MUTATION_RESPONSE type={} requestId={} session={} ok={}",
                    type,
                    splitDTO.getRequestId(),
                    sessionIdToSend,
                    errorMessage == null);
        }

        boolean requiresAuthoritativeBotJobSnapshot = errorMessage == null
                && isBotJobTasksSession(sessionIdToSend)
                && ("ROW_MOVE".equals(type)
                        || "BLOCK_CREATE".equals(type)
                        || "CREATE_BLOCK".equals(type));
        if (requiresAuthoritativeBotJobSnapshot) {
            boolean blockCreated = "BLOCK_CREATE".equals(type) || "CREATE_BLOCK".equals(type);
            ErrorMessage snapshotError = publishBotJobTasksAuthoritativeSnapshot(
                    homeBankingId,
                    botJobIdTask,
                    blockCreated ? splitDTO.getBlockId() : null,
                    blockCreated ? splitDTO.getBlockName() : null,
                    blockCreated ? splitDTO.getBlockOrderNumber() : null);
            authoritativeBotJobSnapshotPublished = snapshotError == null;
            if (snapshotError != null) {
                log.warn(
                        "Bot Job mutation succeeded but the authoritative grid refresh failed: type={} botJobId={} error={}",
                        type,
                        botJobIdTask,
                        snapshotError.getErrorMessage());
                performMessage.errorMessageOperationFailed(snapshotError);
            }
        }

        if (!alreadySentMgsSocket && !authoritativeBotJobSnapshotPublished) {
            List<BotJobLoadDTO> listBot =
                    instrTable.equals("instruction") ? performLists.getListBotJob() : performLists.getListBotJobComp();

            setPayloadEmpty(sessionId, homeBankingId, botJobIdTask, botJobNameTask);
            String jsonData = gson.toJson(payloadEmpty);
            List<InstructionLoad> instructionLoads = new ArrayList<>();
            if (!listBot.isEmpty()) {
                instructionLoads = performLists.buildJsonViewData(listBot);
                if (!instructionLoads.isEmpty()) {
                    jsonData = gson.toJson(instructionLoads);
                }
            }
            if (ScannerWorkspaceOperations.UPDATE_INSTRUCTIONS.equals(updateAction)) {
                JsonObject updatePayload = new JsonObject();
                updatePayload.add("instructions", gson.toJsonTree(instructionLoads));
                updatePayload.add("blocks", gson.toJsonTree(mapBlockOptions("block", botJobIdTask)));
                if (("BLOCK_CREATE".equals(type) || "CREATE_BLOCK".equals(type)) && splitDTO.getBlockId() != null) {
                    updatePayload.addProperty("createdBlockId", splitDTO.getBlockId());
                    updatePayload.addProperty("createdBlockName", splitDTO.getBlockName());
                    updatePayload.addProperty("createdBlockOrderNumber", splitDTO.getBlockOrderNumber());
                }
                jsonData = gson.toJson(updatePayload);
            }

            if (sessionIdToSend != null) {
                instructionRealtimePublisher.publishSerializedSnapshot(
                        homeBankingId, sessionIdToSend, jsonData);
                if ("ROW_MOVE".equals(type)
                        || "DELETE_INSTRUCTION".equals(type)
                        || "DELETE_BLOCK".equals(type)) {
                    log.info(
                            "INSTRUCTION_MUTATION_REALTIME_UPDATE type={} requestId={} session={} operationId={} rows={}",
                            type,
                            splitDTO.getRequestId(),
                            sessionIdToSend,
                            updateAction,
                            instructionLoads.size());
                }
            }

        }

    }

    private InstructionLoad findInstructionInMemory(String instrTable, int whereId, int instructionId) {

        List<InstructionLoad> rowsList = instrTable.equals("instruction")
                ? performLists.getListInstruction()
                : performLists.getListInstructionComp();

        return rowsList.stream()
                .filter(i -> Objects.equals(i.getId(), instructionId))
                .findFirst()
                .orElse(null);
    }

    private ErrorMessage deleteNullsAndMemoryReload(
            String instrTable, String blockTable, int whereId, List<Integer> previousBlockIds) {
        ErrorMessage errorMessage = null;
        // Snapshot of previous IDs without repetitions

        List<Integer> currentIds = (instrTable.equals("instruction")
                        ? performLists.getListInstruction()
                        : performLists.getListInstructionComp())
                .stream()
                        .map(InstructionLoad::getBlockId)
                        .filter(Objects::nonNull)
                        .distinct() // removes duplicates
                        .toList();

        if (!currentIds.isEmpty()) {

            List<Integer> restToDeleteIds = previousBlockIds.stream()
                    .filter(id -> !currentIds.contains(id))
                    .collect(Collectors.toList());

            List<BlockLoadDTO> listBlocks =
                    instrTable.equals("instruction") ? performLists.getListBlock() : performLists.getListBlockComp();
            // Keep at least One for BLOCK TABLE
            if (errorMessage == null
                    && !restToDeleteIds.isEmpty()
                    && (blockTable.equals("block") && listBlocks.size() > 1)) {
                errorMessage = performDataBase.deleteNullBlocks(blockTable, whereId, restToDeleteIds);

                // UPDATE REMOVAL MEMORY LIST
                if (errorMessage == null) {
                    performLists.updateMemoryRemoveBlockIds(blockTable, whereId, restToDeleteIds);
                }

            } else if (errorMessage == null && !restToDeleteIds.isEmpty() && (blockTable.equals("component_block"))) {
                errorMessage = performDataBase.deleteNullBlocks(blockTable, whereId, restToDeleteIds);

                // UPDATE REMOVAL MEMORY LIST
                if (errorMessage == null) {
                    performLists.updateMemoryRemoveBlockIds(blockTable, whereId, restToDeleteIds);
                }
            }
        }
        return errorMessage;
    }

    private ErrorMessage deleteNullsRollbackAndMemoryReload(
            String instrTable, String blockTable, int whereId, List<Integer> previousIds) {
        ErrorMessage errorMessage = performDataBase.loadInstructions(whereId, -1, -1, instrTable);

        // Snapshot of previous IDs without repetitions
        List<Integer> currentIds = (instrTable.equals("instruction")
                        ? performLists.getListInstruction()
                        : performLists.getListInstructionComp())
                .stream()
                        .map(InstructionLoad::getBlockId)
                        .filter(Objects::nonNull)
                        .distinct() // removes duplicates
                        .toList();

        if (!currentIds.isEmpty()) {
            List<Integer> restToDeleteIds =
                    previousIds.stream().filter(id -> !currentIds.contains(id)).collect(Collectors.toList());

            List<BlockLoadDTO> listBlocks =
                    blockTable.equals("block") ? performLists.getListBlock() : performLists.getListBlockComp();
            // Keep at least One for BLOCK TABLE
            if (errorMessage == null
                    && !restToDeleteIds.isEmpty()
                    && (blockTable.equals("block") && listBlocks.size() > 1)) {
                errorMessage = performDataBase.deleteNullBlocks(blockTable, whereId, restToDeleteIds);

                // UPDATE REMOVAL MEMORY LIST
                if (errorMessage == null) {
                    performLists.updateMemoryRollBackToOneBlock(blockTable, whereId, restToDeleteIds);
                }
            } else if (errorMessage == null && !restToDeleteIds.isEmpty() && (blockTable.equals("component_block"))) {
                errorMessage = performDataBase.deleteNullBlocks(blockTable, whereId, restToDeleteIds);

                // UPDATE REMOVAL MEMORY LIST
                if (errorMessage == null) {
                    performLists.updateMemoryRollBackToOneBlock(blockTable, whereId, restToDeleteIds);
                }
            }
        }

        //                    // ROLL BACK I LOAD AGAIN JUST FOR SAFETY
        //                    if (errorMessage == null) {
        //                        errorMessage = performDataBase.loadBlocks(whereId, "", blockTable);
        //                    }
        return errorMessage;
    }

    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        // Clean up session when it closes
        String sessionId = webSocketSessionManager.getSessionIdBySession(session);
        if (sessionId != null) {
            log.info("Connection closed: Session ID = " + sessionId + ", Reason: "
                    + closeReason.getReasonPhrase() + " (Code: "
                    + closeReason.getCloseCode() + ")");
            if (webSocketSessionManager.removeSession(sessionId, session)) {
                BotJobTransferPathRegistry.getInstance().clearSession(sessionId);
                notifyBotJobWindowDisconnected(sessionId);
                notifyPageScannerWindowDisconnected(sessionId);
                notifyOcrWindowDisconnected(sessionId);
                notifyMainApplicationDisconnected(sessionId);
                commandEditorWorkspaceService.disconnected(sessionId, session);
                pagesOpenWorkspaceService.sessionRegistryChanged();
            }
        } else {
            log.info("Connection closed for unknown session, Reason: " + closeReason.getReasonPhrase() + " (Code: "
                    + closeReason.getCloseCode() + ")");
        }
    }

    private static void notifyBotJobWindowDisconnected(String sessionId) {
        if (!BotJobDetailsWindowCoordinator.isControlSessionId(sessionId)) return;
        try {
            BotJobDetailsWindowCoordinator.getInstance().disconnected(sessionId);
        } catch (IllegalArgumentException staleControlSession) {
            log.debug("Ignoring stale Bot Job Details window disconnect for {}", sessionId);
        }
    }

    private static void notifyPageScannerWindowDisconnected(String sessionId) {
        if (!ScannerWorkspaceSessions.isPageScannerSession(sessionId)) return;
        try {
            PageScannerWorkspaceCoordinator.getInstance().disconnected(sessionId);
        } catch (IllegalArgumentException staleWorkspaceSession) {
            log.debug("Ignoring stale Page Scanner window disconnect for {}", sessionId);
        }
    }

    private static void notifyOcrWindowDisconnected(String sessionId) {
        if (!OcrWorkspaceCoordinator.isWorkspaceSessionId(sessionId)) return;
        OcrWorkspaceCoordinator.getInstance().disconnected(sessionId);
    }

    private static void notifyMainApplicationDisconnected(String sessionId) {
        if (!MainApplicationControlLifecycle.isControlSessionId(sessionId)) return;
        MainApplicationControlLifecycle.getInstance().disconnected(sessionId);
    }

    // Handle BLOCKS_SPLITTED message
    private ErrorMessage splitBlocks(SplitDTO blockSplitDTO) {
        BlockDetailsDTO originalBlock = blockSplitDTO.getDetails().getOriginalBlock();
        BlockDetailsDTO newBlock = blockSplitDTO.getDetails().getNewBlock();
        List<BlockOrderDetailDTO> updatedBlock = blockSplitDTO.getDetails().getUpdatedBlocks();
        log.info("Original Block ID: " + originalBlock.getBlockId());
        log.info("New Block Name: " + newBlock.getBlockName());
        log.info("Updated Block: " + updatedBlock.size());
        newBlock.setForceOrder(true);

        ErrorMessage errorMessage = performDataBase.splitBlockAtomic(
                blockSplitDTO.getBotJobId(),
                newBlock,
                originalBlock.getBlockId(),
                newBlock.getInstructions(),
                updatedBlock);

        if (errorMessage == null) {
            // this is Important to update Easi the Memory
            if (errorMessage == null) {
                errorMessage = performDataBase.loadBlocks(blockSplitDTO.getBotJobId(), "", "block");
            }

            if (errorMessage == null) {
                errorMessage = performDBEngine.loadCompleteJobs(blockSplitDTO.getBotJobId());
            }

            if (errorMessage == null && !updatedBlock.isEmpty()) {

                if (errorMessage == null) {
                    // Work directly with the List<BlockLoadDTO> in performLists
                    for (BlockOrderDetailDTO updated : updatedBlock) {
                        for (BlockLoadDTO current : performLists.getListBlock()) {
                            if (current.getId() != null && current.getId().equals(updated.getBlockId())) {
                                current.setBlockOrderNumber(updated.getBlockOrderNumber());
                                break;
                            }
                        }
                    }

                    List<BlockLoadDTO> mappedBlocks = mapToBlockLoad(blockSplitDTO.getHomeBankingId(), updatedBlock);

                    // UPDATE BLOCK ORDER MEMORY LIST
                    if (errorMessage == null) {
                        performLists.updateMemorySwiftBlockOrder("block", blockSplitDTO.getBotJobId(), mappedBlocks);
                    }
                }

                if (errorMessage != null) {
                    performMessage.errorMessageOperationFailed(errorMessage);
                }
            }
        } else {
            performMessage.errorMessageOperationFailed(errorMessage);
        }

        //        performDataBase.loadBlocks(blockSplitDTO.getBotJobId(), "", "block");
        return errorMessage;
    }

    private void injectStepAfterOrBefore(String blockTable, int whereId, SplitDTO splitDTO) {

        if (whereId > 0) {
            BlockLoadDTO blockLoadFound = performLists.getBlockLoadByBankId(blockTable, whereId, splitDTO.getBlockId());

            if (blockLoadFound != null) {
                splitDTO.setBlockId(blockLoadFound.getId());
            } else {
                splitDTO.setBlockId(-1);
            }

            ErrorMessage errorMessage = null;
            if (blockLoadFound == null) {
                errorMessage = performDataBase.initiateNewBlock(
                        blockTable, whereId, "Default Block", "Default Block", 1, false);
            }

            if (errorMessage == null && blockLoadFound == null) {
                int newBlockId = -9999;
                if (!performDataBase.getIdsBlockAfter().isEmpty()
                        && performDataBase.getIdsBlockAfter().get(0) > 0) {
                    newBlockId = performDataBase.getIdsBlockAfter().get(0);
                }
                // IT SETS THE NEW TARGET IN CASE TO ADD MORE INSTRUCTIONS
                splitDTO.setBlockId(newBlockId);
            }

            if (errorMessage != null) {
                performMessage.errorMessageOperationFailed(errorMessage);
            }
        }

        if (splitDTO.getType().equals("INSERT_BEFORE_ELSEIF")
                || splitDTO.getType().equals("INSERT_AFTER_ELSEIF")) {

            try {
                ErrorMessage message = performDataBase.preFillNewInstruction(
                        "ELSEIF", "ELSEIF", ARConstants.ELSEIF, ARConstants.ELSEIF, 1, splitDTO, false);

            } catch (Exception e) {

                log.error(String.format(
                        "Cannot Insert \"Instruction\"  \"%s\"\nCannot be saved!\nError: %s",
                        ARConstants.ELSEIF, e.getMessage()));
            }
        }
    }

    private void injectBlockComponent(SplitDTO blockSplitDTO) {
        // Ensure workspace updates are routed through the active presentation boundary.

        BlockDetailsDTO blockDetailsDTO = blockSplitDTO.getDetails().getNewBlock();
        blockDetailsDTO.setHomeBankingId(blockSplitDTO.getHomeBankingId());
        blockDetailsDTO.setBotJobId(blockSplitDTO.getBotJobId());
        blockDetailsDTO.setSessionId(blockSplitDTO.getSessionId());

        ErrorMessage errorMessage = null;

        // Add at the end of it
        if (!performLists.getListBlock().isEmpty()) {
            blockDetailsDTO.setBlockOrderNumber(performLists.getListBlock().size() + 1);
        } else {
            blockDetailsDTO.setBlockOrderNumber(1);
        }

        if (errorMessage == null) {
            errorMessage = performDataBase.createInjectBlock(blockDetailsDTO);
        }

        int newBlockId = -9999;
        if (!performDataBase.getIdsBlockAfter().isEmpty()
                && performDataBase.getIdsBlockAfter().get(0) > 0) {
            newBlockId = performDataBase.getIdsBlockAfter().get(0);
        }

        if (errorMessage == null) {
            errorMessage = performDataBase.createInjectInstructions(blockDetailsDTO);
        }
        if (errorMessage == null) {
            errorMessage = performDataBase.createInjectVariables(blockDetailsDTO);
        }
        if (errorMessage == null) {
            errorMessage = performDataBase.createUpdateInjectInstruction(blockDetailsDTO);
        }
        if (errorMessage == null) {
            errorMessage = performDataBase.createInjectReferences(blockDetailsDTO);
        }

        if (errorMessage == null) {
            errorMessage = performDataBase.updateBlockOrderNumber("block", blockDetailsDTO.getBotJobId(), true);
        }

        if (errorMessage == null) {
            errorMessage = performDataBase.loadBlocks(blockDetailsDTO.getBotJobId(), "", "block");
        }
        if (errorMessage == null) {

            errorMessage = performDBEngine.loadCompleteJobs(blockDetailsDTO.getBotJobId());
            if (errorMessage != null) {
                performMessage.errorMessageOperationFailed(errorMessage);
            }

            String jsonData = "[]";
            if (!performLists.getListBotJob().isEmpty()) {
                List<InstructionLoad> blockLoopInstructions =
                        performLists.buildJsonViewData(performLists.getListBotJob());
                jsonData = gson.toJson(blockLoopInstructions);
            }
            webSocketSessionManager.sendMessageJson(
                    blockDetailsDTO.getHomeBankingId(),
                    blockDetailsDTO.getSessionId(),
                    jsonData,
                    ScannerWorkspaceOperations.UPDATE_INSTRUCTIONS);

        } else {
            performDataBase.deleteBlockDirect("block", blockDetailsDTO.getBotJobId(), newBlockId);
        }
    }

    private void setPayloadEmpty(String destination, int homeBankId, int botJobId, String botJobName) {
        int blockId = -1;
        int whereId = -1;
        if (destination.equalsIgnoreCase(ScannerWorkspaceSessions.BOT_JOB_TASKS)) {
            if (performLists.getListBlock().isEmpty()) {
                performDataBase.loadBlocks(botJobId, botJobName, "block");
            }
            whereId = botJobId;
            if (!performLists.getListBlock().isEmpty()) {
                blockId = performLists.getListBlock().get(0).getId();
            }

        } else if (destination.equalsIgnoreCase(ScannerWorkspaceSessions.COMPONENT_TASKS)) {
            if (!performLists.getListBotJobComp().isEmpty()
                    && performLists.getListBlockComp().isEmpty()) {
                performDataBase.loadBlocks(homeBankId, "", "component_block");
            }
            whereId = homeBankId;
            if (!performLists.getListBlockComp().isEmpty()) {
                blockId = performLists.getListBlockComp().get(0).getId();
            }
        }
        this.payloadEmpty = new PayloadJson(whereId, blockId, botJobName, 0);
    }

    public InstructionLoad hasOnlyExcelGoto(List<InstructionLoad> instructions, String instrTable) {

        List<InstructionLoad> excelGotoInstructions = instructions.stream()
                .filter(instr -> "EXCEL GOTO".equalsIgnoreCase(instr.getActions()))
                .toList();

        if (excelGotoInstructions.isEmpty()) {
            return null;
        }

        // all blockIds used by "EXCEL GOTO"
        Set<Integer> excelGotoBlockIds = excelGotoInstructions.stream()
                .map(InstructionLoad::getBlockId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // check each blockId has *only* EXCEL GOTO instructions
        boolean uniqueBlocks = excelGotoBlockIds.size() == 1
                && instructions.stream()
                        .filter(instr -> excelGotoBlockIds.contains(instr.getBlockId()))
                        .allMatch(instr -> "EXCEL GOTO".equalsIgnoreCase(instr.getActions()));

        if (uniqueBlocks) {
            InstructionLoad first = excelGotoInstructions.get(0);
            // remove all EXCEL GOTO instructions from that block
            instructions.removeIf(instr -> excelGotoBlockIds.contains(instr.getBlockId()));
            return first;
        }

        return null;
    }

    public List<BlockLoadDTO> mapToBlockLoad(int homeBankId, List<BlockOrderDetailDTO> updatedBlock) {
        if (updatedBlock == null || updatedBlock.isEmpty()) {
            return new ArrayList<>();
        }

        return updatedBlock.stream()
                .map(dto -> {
                    BlockLoadDTO blockLoadDTO = new BlockLoadDTO();
                    blockLoadDTO.setHomeBankingId(homeBankId);
                    blockLoadDTO.setId(dto.getBlockId());
                    blockLoadDTO.setBotJobId(dto.getBotJobId());
                    blockLoadDTO.setBlockOrderNumber(dto.getBlockOrderNumber());
                    blockLoadDTO.setName(dto.getBlockName());

                    // Optional: initialize remaining fields to default/null
                    blockLoadDTO.setHomeBankingName(null);
                    blockLoadDTO.setDescription(null);
                    blockLoadDTO.setTypeId(null);
                    blockLoadDTO.setBotJobName(null);
                    blockLoadDTO.setExportFile(null);
                    blockLoadDTO.setActive(null);
                    blockLoadDTO.setWait(null);
                    blockLoadDTO.setInstructionLoad(new ArrayList<>());

                    return blockLoadDTO;
                })
                .toList();
    }

    private List<Map<String, Object>> mapBlockOptions(String blockTable, int whereId) {
        List<BlockLoadDTO> blocks =
                blockTable.equals("block") ? performLists.getListBlock() : performLists.getListBlockComp();

        return blocks.stream()
                .filter(block -> {
                    if (block == null || block.getId() == null) {
                        return false;
                    }
                    if (blockTable.equals("block")) {
                        return block.getBotJobId() != null
                                && block.getBotJobId().equals(whereId);
                    }
                    return block.getHomeBankingId() != null
                            && block.getHomeBankingId().equals(whereId);
                })
                .sorted(Comparator.comparingInt(
                        block -> block.getBlockOrderNumber() == null ? Integer.MAX_VALUE : block.getBlockOrderNumber()))
                .map(block -> {
                    Map<String, Object> option = new LinkedHashMap<>();
                    option.put("blockId", block.getId());
                    option.put("blockOrderNumber", block.getBlockOrderNumber());
                    option.put("blockName", block.getName());
                    return option;
                })
                .toList();
    }

    private SplitDTO parseSplitDTO(JsonObject jsonEntry) {
        if (jsonEntry == null || jsonEntry.isEmpty()) {
            log.warn("parseSplitDTO called with null or empty JSON object");
            return null;
        }

        try {
            JsonObject merged = new JsonObject();

            // Step 1: If there's a "body" key, parse it as JSON and merge it
            if (jsonEntry.has("body")) {
                String bodyStr = jsonEntry.get("body").getAsString();
                JsonObject inner = gson.fromJson(bodyStr, JsonObject.class);
                inner.entrySet().forEach(entry -> merged.add(entry.getKey(), entry.getValue()));
            }

            // Step 2: Merge all top-level fields (except "body")
            jsonEntry.entrySet().forEach(entry -> {
                if (!"body".equals(entry.getKey())) {
                    merged.add(entry.getKey(), entry.getValue());
                }
            });

            // Step 3: Deserialize merged object into DTO
            return gson.fromJson(merged, SplitDTO.class);

        } catch (Exception error) {
            log.error("Cannot parse SplitDTO: " + error.getMessage() + " | JSON: " + jsonEntry);
            return null;
        }
    }

    private void sendStatusButton(
            int homeBankId, String sessionId, String operationId, String message, SplitDTO splitDTO) {
        WebSocketSignal webSockteSocketSignal = WebSocketSignal.builder()
                .sessionId(sessionId)
                .operationId(operationId)
                .message(message)
                .splitDTO(splitDTO)
                .build();

        String jsonData = gson.toJson(webSockteSocketSignal);

        webSocketSessionManager.sendMessageJson(homeBankId, sessionId, jsonData, operationId);
    }

    private JsonObject authorizeCommandEditorRequest(
            JsonObject body, String sessionId, Session transport) {
        if (!CommandEditorWorkspaceService.isWorkspaceSession(sessionId)) {
            return body == null ? new JsonObject() : body;
        }
        return commandEditorWorkspaceService
                .authorize(body, sessionId, transport)
                .body();
    }

    private JsonObject attachCommandEditorBindingEpoch(
            JsonObject response, JsonObject authorizedBody, String sessionId) {
        JsonObject correlated = response == null ? new JsonObject() : response;
        if (!CommandEditorWorkspaceService.isWorkspaceSession(sessionId)
                || authorizedBody == null
                || !authorizedBody.has("bindingEpoch")
                || authorizedBody.get("bindingEpoch").isJsonNull()) {
            return correlated;
        }
        correlated.add(
                "bindingEpoch",
                authorizedBody.get("bindingEpoch").deepCopy());
        return correlated;
    }

    private JsonObject commandEditorFailure(JsonObject request, String message) {
        JsonObject response = new JsonObject();
        response.addProperty("ok", false);
        response.addProperty(
                "error",
                Strings.isNullOrEmpty(message)
                        ? "The Command Editor request was refused."
                        : message);
        if (request != null
                && request.has("requestId")
                && !request.get("requestId").isJsonNull()) {
            response.add("requestId", request.get("requestId").deepCopy());
        }
        if (request != null
                && request.has("bindingEpoch")
                && !request.get("bindingEpoch").isJsonNull()) {
            response.add(
                    "bindingEpoch",
                    request.get("bindingEpoch").deepCopy());
        }
        return response;
    }

    private int commandEditorHomeBankingId(
            JsonObject authorizedBody, int fallback) {
        try {
            return authorizedBody != null
                            && authorizedBody.has("homeBankingId")
                            && !authorizedBody.get("homeBankingId").isJsonNull()
                    ? authorizedBody.get("homeBankingId").getAsInt()
                    : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private void sendCommandEditorResponse(
            int homeBankId, String sessionId, String operationId, Object response) {
        webSocketSessionManager.sendMessageJson(homeBankId, sessionId, gson.toJson(response), operationId);
    }

    private boolean isMobileReturnSession(String sessionId) {
        return scannerMobileTestRoute.returnSessionId().equals(sessionId);
    }

    private void forwardToMobileReturn(int homeBankingId, String operationId, SplitDTO splitDTO) {
        splitDTO.setOperationId(operationId);
        webSocketSessionManager.sendMessageJson(
                homeBankingId,
                scannerMobileTestRoute.returnSessionId(),
                gson.toJson(splitDTO),
                operationId);
    }

    private void sendMobileScannerGridPayload(int homeBankingId, String operationId, Object payload) {
        webSocketSessionManager.sendMessageJson(
                homeBankingId,
                scannerMobilePickRoute.payloadSessionId(),
                gson.toJson(payload),
                operationId);
    }

    private static String commandLogValue(JsonObject body, String field) {
        if (body == null || !body.has(field) || body.get(field).isJsonNull()) return "<missing>";
        try {
            String value = body.get(field).getAsString();
            return value == null || value.isBlank() ? "<blank>" : value.replaceAll("[\\r\\n\\t]", " ");
        } catch (RuntimeException invalidValue) {
            return "<invalid>";
        }
    }

    private void rememberCompletedRequest(Map<String, Boolean> completedRequests, String requestId) {
        synchronized (completedRequests) {
            completedRequests.put(requestId, Boolean.TRUE);
            while (completedRequests.size() > 256) {
                completedRequests.remove(completedRequests.keySet().iterator().next());
            }
        }
    }

    /** Best-effort lookup of the current pick's home_url_id. Null when no scanner job is active. */
    private static Integer currentHomeUrlId() {
        return ScannerCurrentJobContext.getInstance().currentHomeUrlId();
    }
}

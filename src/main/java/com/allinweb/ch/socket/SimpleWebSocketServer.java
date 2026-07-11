package com.allinweb.ch.socket;

import com.allinweb.ch.ARControlPanel;
import com.allinweb.ch.component.pane.ARScannedElementPane;
import com.allinweb.ch.component.pane.ARViewBotJobPane;
import com.allinweb.ch.facade.*;
import com.allinweb.ch.model.*;
import com.allinweb.ch.util.*;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import javafx.application.Platform;
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
    private static final NewBotJobService newBotJobService = NewBotJobService.getInstance();
    private static final ConfigService configService = ConfigService.getInstance();
    private static final ExcelExportService excelExportService = ExcelExportService.getInstance();
    private static final SaveComponentService saveComponentService = SaveComponentService.getInstance();
    private static final OcrManagerService ocrManagerService = OcrManagerService.getInstance();
    private static final OcrTestService ocrTestService = OcrTestService.getInstance();
    protected static volatile SimpleWebSocketServer instance;
    private static WebSocketSessionManager webSocketSessionManager = WebSocketSessionManager.getInstance();
    private static ActionExecutorClient actionExecutorClient = ActionExecutorClient.getInstance();
    private static final Map<String, Boolean> processedInstructionDeletes = new LinkedHashMap<>();
    private static final Map<String, Boolean> processedRowMoves = new LinkedHashMap<>();
    private static final Map<String, Boolean> processedBlockDeletes = new LinkedHashMap<>();
    private static final InstructionMoveValidator instructionMoveValidator = new InstructionMoveValidator();
    private final Gson gson = new Gson();
    private PayloadJson payloadEmpty;
    private RowStatus rowStatus = new RowStatus();
    // Private constructor to prevent instantiation
    public SimpleWebSocketServer() {}

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
        // Get the sessionId from the query parameter passed by the frontend
        try {
            String sessionId = session.getRequestParameterMap().get("sessionId").get(0);

            if (!Strings.isNullOrEmpty(sessionId)) {
                webSocketSessionManager.addSession(sessionId, session); // Store the session with the custom ID
                log.info("New connection: Session ID = " + sessionId);
            } else {
                log.info("No session ID provided by client");
            }
        } catch (Exception noSessionId) {
            //            addSession(generateCustomSessionId(session), session);
            log.info("No session ID provided by client");
        }
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        if (message == null || message.contains("CONNECT") || message.contains("ping")) {
            // Ignore null or empty messages
            message = message.replaceAll("ping-", "");
            // log.info("Active : " + message);
            return;
        }

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

            String sessionId =
                    jsonObjMSG.has("sessionId") ? jsonObjMSG.get("sessionId").getAsString() : "unknown";

            // After Decoding
            if (type == null || type.trim().isEmpty() || type.contains("CONNECT") || type.contains("ping")) {
                // Ignore null or empty messages
                type = (type == null) ? "unknown" : type.replaceAll("ping-", "");
                return;
            }

            // Is Going to Handle the Control
            if (Strings.isNullOrEmpty(sessionId)) {
                sessionId = null;
                try {
                    sessionId =
                            session.getRequestParameterMap().get("sessionId").get(0);
                    if (!Strings.isNullOrEmpty(sessionId)) {
                        if (!webSocketSessionManager.containsSession(sessionId)) {
                            if (!webSocketSessionManager.isSessionOpen(sessionId)) {
                                webSocketSessionManager.addSession(sessionId, session);
                            }
                        }
                    } else {
                        //                        addSession(generateCustomSessionId(session), session);
                    }

                } catch (Exception noSessionId) {
                    //                    addSession(generateCustomSessionId(session), session);
                }
            }

            if (!LicenseService.getInstance().permits(type)) {
                sendCommandEditorResponse(
                        homeBankingId,
                        sessionId,
                        "license.requiredResponse",
                        LicenseService.getInstance().startup());
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
                case "license.request":
                    JsonObject requestResponse = LicenseService.getInstance().request(extractBody(jsonObjMSG));
                    sendCommandEditorResponse(homeBankingId, sessionId, "license.requestResponse", requestResponse);
                    publishLicenseStatus(requestResponse);
                    break;
                case "license.activate":
                    JsonObject activationResponse = LicenseService.getInstance().activate(extractBody(jsonObjMSG));
                    if (isActiveLicenseResponse(activationResponse)) ARControlPanel.continueAfterLicenseActivation();
                    sendCommandEditorResponse(homeBankingId, sessionId, "license.activateResponse", activationResponse);
                    publishLicenseStatus(activationResponse);
                    break;
                case "license.useExisting":
                    JsonObject existingResponse = LicenseService.getInstance().useExisting(extractBody(jsonObjMSG));
                    if (isActiveLicenseResponse(existingResponse)) ARControlPanel.continueAfterLicenseActivation();
                    sendCommandEditorResponse(homeBankingId, sessionId, "license.useExistingResponse", existingResponse);
                    publishLicenseStatus(existingResponse);
                    break;
                case "excelExport.bootstrap":
                    sendCommandEditorResponse(homeBankingId, sessionId, "excelExport.bootstrapResponse",
                            excelExportService.bootstrap(extractBody(jsonObjMSG)));
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
                        webSocketSessionManager.sendMessageJson(homeBankingId, "componentTasks",
                                gson.toJson(componentResponse.get("instructions")), "componentsUpdate");
                    }
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
                case "commandEditor.bootstrap":
                    sendCommandEditorResponse(
                            homeBankingId,
                            sessionId,
                            "commandEditor.bootstrapResponse",
                            CommandEditorService.getInstance().bootstrap(extractBody(jsonObjMSG)));
                    break;
                case "commandEditor.apply":
                    JsonObject commandApplyBody = extractBody(jsonObjMSG);
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
                    JsonObject commandApplyResponse = CommandEditorService.getInstance().apply(commandApplyBody);
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
                    sendCommandEditorResponse(
                            homeBankingId,
                            sessionId,
                            "commandEditor.applyResponse",
                            commandApplyResponse);
                    if (commandSaved
                            && commandApplyResponse.has("instructions")
                            && commandApplyResponse.get("instructions").isJsonArray()) {
                        String targetSessionId = commandLogValue(commandApplyBody, "targetSessionId");
                        if ("<missing>".equals(targetSessionId) || "<blank>".equals(targetSessionId)) {
                            targetSessionId = "botJobTasks";
                        }
                        String updateOperationId = "componentTasks".equals(targetSessionId)
                                ? "componentsUpdate"
                                : "updateInstructions";
                        webSocketSessionManager.sendMessageJson(
                                homeBankingId,
                                targetSessionId,
                                gson.toJson(commandApplyResponse.getAsJsonArray("instructions")),
                                updateOperationId);
                        log.info(
                                "COMMAND_EDITOR_REALTIME_UPDATE requestId={} targetSession={} operationId={} rows={}",
                                commandLogValue(commandApplyBody, "requestId"),
                                targetSessionId,
                                updateOperationId,
                                commandApplyResponse.getAsJsonArray("instructions").size());
                    }
                    break;
                case "commandEditor.insertElseIf":
                    sendCommandEditorResponse(
                            homeBankingId,
                            sessionId,
                            "commandEditor.insertElseIfResponse",
                            CommandEditorService.getInstance().insertElseIf(extractBody(jsonObjMSG)));
                    break;
                case "instructionGraph.previewSplit":
                    sendCommandEditorResponse(
                            homeBankingId,
                            sessionId,
                            "instructionGraph.previewSplitResponse",
                            CommandEditorService.getInstance().previewSplit(extractBody(jsonObjMSG)));
                    break;
                case "instructionGraph.previewMove":
                    sendCommandEditorResponse(
                            homeBankingId,
                            sessionId,
                            "instructionGraph.previewMoveResponse",
                            CommandEditorService.getInstance().previewMove(extractBody(jsonObjMSG)));
                    break;
                case "instructionEditor.memoryCapabilities":
                    sendCommandEditorResponse(
                            homeBankingId,
                            sessionId,
                            "instructionEditor.memoryCapabilitiesResponse",
                            CommandEditorService.getInstance().memoryCapabilities(extractBody(jsonObjMSG)));
                    break;
                case "variableEditor.bootstrap":
                    sendCommandEditorResponse(homeBankingId, sessionId, "variableEditor.bootstrapResponse",
                            VariableEditorService.getInstance().list(extractBody(jsonObjMSG)));
                    break;
                case "variableEditor.save":
                    sendCommandEditorResponse(homeBankingId, sessionId, "variableEditor.saveResponse",
                            VariableEditorService.getInstance().save(extractBody(jsonObjMSG)));
                    break;
                case "variableEditor.delete":
                    sendCommandEditorResponse(homeBankingId, sessionId, "variableEditor.deleteResponse",
                            VariableEditorService.getInstance().delete(extractBody(jsonObjMSG)));
                    break;
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
                case "mainDashboard.openInfo":
                    handleMainDashboardOpenInfo(sessionId);
                    break;
                case "mainDashboard.exit":
                    handleMainDashboardExit(sessionId);
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
                case "config.bootstrap":
                    handleConfigBootstrap(sessionId);
                    break;
                case "config.choosePath":
                    handleConfigChoosePath(jsonObjMSG, sessionId);
                    break;
                case "config.save":
                    handleConfigSave(jsonObjMSG, sessionId);
                    break;
                case "config.backup":
                    handleConfigBackup(jsonObjMSG, sessionId);
                    break;
                case "config.restore":
                    handleConfigRestore(jsonObjMSG, sessionId);
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

    private void handleMainDashboardList(String sessionId) {
        sendMainDashboardResponse(sessionId, mainDashboardService.list(), "mainDashboard.listResponse");
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

    private void handleMainDashboardOpenInfo(String sessionId) {
        sendMainDashboardResponse(sessionId, mainDashboardService.openInfo(), "mainDashboard.actionResponse");
    }

    private void handleMainDashboardExit(String sessionId) {
        sendMainDashboardResponse(sessionId, mainDashboardService.exit(), "mainDashboard.actionResponse");
    }

    private void sendMainDashboardResponse(String sessionId, Object response, String operationId) {
        webSocketSessionManager.sendMessageJson(-1, sessionId, gson.toJson(response), operationId);
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

    private void handleConfigChoosePath(JsonObject jsonObjMSG, String sessionId) {
        sendConfigResponse(sessionId, configService.choosePath(extractBody(jsonObjMSG)), "config.pathResponse");
    }

    private void handleConfigSave(JsonObject jsonObjMSG, String sessionId) {
        sendConfigResponse(sessionId, configService.save(extractBody(jsonObjMSG)), "config.saveResponse");
    }

    private void handleConfigBackup(JsonObject jsonObjMSG, String sessionId) {
        sendConfigResponse(sessionId, configService.backup(extractBody(jsonObjMSG)), "config.backupResponse");
    }

    private void handleConfigRestore(JsonObject jsonObjMSG, String sessionId) {
        sendConfigResponse(sessionId, configService.restore(extractBody(jsonObjMSG)), "config.restoreResponse");
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

    @OnError
    public void onError(Session session, Throwable throwable) {
        log.error("Error in session " + session.getId() + ": " + throwable.getMessage());
        try {
            if (session.isOpen()) {
                session.close();
            }
        } catch (IOException e) {
            log.error("Error closing session: " + e.getMessage());
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

        boolean alreadySentMgsSocket = false;
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

            String jsonStatus = gson.toJson(rowStatus);
            webSocketSessionManager.sendMessageJson(
                    splitDTO.getHomeBankingId(), "botJobTasks", jsonStatus, "rowStatus");
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
            if (sessionIdToSend.matches(".*botJobTasks.*")
                    || sessionIdToSend.matches(".*scannerTool.*")
                    || sessionIdToSend.matches(".*scannerGrid.*")
                    || sessionIdToSend.matches(".*mobileScannerGrid.*")
                    || sessionIdToSend.matches(".*scanner-element-pane.*")) {
                instrTable = "instruction";
                blockTable = "block";
                variableTable = "variable";
                whereId = splitDTO.getBotJobId() != null ? splitDTO.getBotJobId() : -1;
                updteBlocks = "UPDATE_BLOCKS";
                updateAction = "updateInstructions";
            } else if (sessionIdToSend.matches(".*componentTasks.*")) {
                instrTable = "component_instruction";
                blockTable = "component_block";
                variableTable = "component_variable";
                whereId = splitDTO.getHomeBankingId() != null ? splitDTO.getHomeBankingId() : -1;
                updteBlocks = "UPDATE_BLOCKS_COMP";
                updateAction = "componentsUpdate";
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
                case "LAUNCH_BOT_JOB_TEST":
                    if (sessionIdToSend.equals("mobile-return-server")) {
                        splitDTO.setOperationId(type);
                        String jsonData = gson.toJson(splitDTO);
                        webSocketSessionManager.sendMessageJson(homeBankingId, "mobile-return-server", jsonData, type);
                    }
                    alreadySentMgsSocket = true;
                    break;
                case "ATTACHED_DEVICE":
                case "DISCOVERY_APP":
                case "SCANNER_APP":
                case "MOBILE_SCROLL_UP":
                case "MOBILE_SCROLL_DOWN":
                case "MOBILE_BACK":
                case "MOBILE_HOME":
                case "MOBILE_RECENTS":
                case "MOBILE_CLOSE_ALL":
                case "MOBILE_NEXT_DONE":
                case "MOBILE_CLOSE_KEYBOARD":
                    if (sessionIdToSend.equals("mobile-return-server")) {
                        splitDTO.setOperationId(type);
                        String jsonData = gson.toJson(splitDTO);
                        webSocketSessionManager.sendMessageJson(homeBankingId, "mobile-return-server", jsonData, type);
                    }
                    alreadySentMgsSocket = true;
                    break;
                case "REACTIVATE_BUTTONS":
                    if (sessionId.equals("mobile-return-server")) {
                        splitDTO.setElementDetails(null);

                        // Convert your JsonObject to a proper JSON string
                        sendStatusButton(
                                splitDTO.getHomeBankingId(),
                                "mobileScannerGrid",
                                operationId,
                                "Activated button ",
                                splitDTO);
                    }
                    alreadySentMgsSocket = true;
                    break;

                case "MOBILE_LOAD_JOBS": //  DATA CONTROL FOR THE MOBILE mobileScannerGrid
                    if (sessionId.equals("mobile-return-server")) {
                        splitDTO.setOperationId("botJobList");

                        performDataBase.setMobileDevices(true);
                        errorMessage = performDataBase.loadQuickBotJobs();
                        if (errorMessage == null) {
                            List<BotJobLoadDTO> fetched = Optional.ofNullable(performLists.getQuickBotJobs())
                                    .orElse(Collections.emptyList());
                            String jsonData = gson.toJson(fetched);
                            webSocketSessionManager.sendMessageJson(
                                    homeBankingId, "mobileScannerGrid", jsonData, "botJobList");
                        }
                        performDataBase.setMobileDevices(false);
                    }
                    alreadySentMgsSocket = true;
                    break;
                case "MOBILE_VALIDATE_FIELDS": //  DATA CONTROL FOR THE MOBILE mobileScannerGrid
                    if (sessionId.equals("mobile-return-server")) {
                        splitDTO.setOperationId("validateFields");

                        performDataBase.setMobileDevices(true);
                        String jsonData = gson.toJson(splitDTO.getFieldsToValidate());
                        webSocketSessionManager.sendMessageJson(
                                homeBankingId, "mobileScannerGrid", jsonData, "validateFields");
                        performDataBase.setMobileDevices(false);
                    }
                    alreadySentMgsSocket = true;
                    break;
                case "DOM_REVIEW_RESPONSE":
                    String reviewAction =
                            jsonEntry.has("action") ? jsonEntry.get("action").getAsString() : "cancel";
                    log.info("DOM_REVIEW_RESPONSE received: action={}", reviewAction);
                    ARScannedElementPane.getInstance().handleDomReviewResponse(reviewAction);
                    alreadySentMgsSocket = true;
                    break;
                case "SUPPORT_REQUEST_RESPONSE":
                    String supportAction =
                            jsonEntry.has("action") ? jsonEntry.get("action").getAsString() : "cancel";
                    String supportMessage =
                            jsonEntry.has("message") ? jsonEntry.get("message").getAsString() : "";
                    log.info(
                            "SUPPORT_REQUEST_RESPONSE received: action={}, messageLen={}",
                            supportAction,
                            supportMessage.length());
                    ARScannedElementPane.getInstance().handleSupportRequestResponse(supportAction, supportMessage);
                    alreadySentMgsSocket = true;
                    break;
                case "REQUEST_SUPPORT_ELEMENTS":
                    log.info("REQUEST_SUPPORT_ELEMENTS received");
                    ARScannedElementPane.getInstance().requestSupportElements();
                    alreadySentMgsSocket = true;
                    break;
                case "SUPPORT_REQUEST_ELEMENTS_RESPONSE":
                    String elementsSupportAction =
                            jsonEntry.has("action") ? jsonEntry.get("action").getAsString() : "cancel";
                    String elementsSupportMessage =
                            jsonEntry.has("message") ? jsonEntry.get("message").getAsString() : "";
                    String elementsJson = jsonEntry.has("elementDetails")
                            ? jsonEntry.get("elementDetails").toString()
                            : "[]";
                    log.info(
                            "SUPPORT_REQUEST_ELEMENTS_RESPONSE received: action={}, messageLen={}, elementsJsonLen={}",
                            elementsSupportAction,
                            elementsSupportMessage.length(),
                            elementsJson.length());
                    ARScannedElementPane.getInstance()
                            .handleSupportRequestElementsResponse(
                                    elementsSupportAction, elementsSupportMessage, elementsJson);
                    alreadySentMgsSocket = true;
                    break;
                case "PRE_SCAN_PAGE":
                case "PRE_SCAN_REFRESH_PAGE":
                case "PRE_SCAN_CLEAR_GRID":
                case "PRE_SCAN_OCR_CONFIG":
                    ARViewBotJobPane.getInstance().handlePreScanCommand(type, jsonEntry);
                    alreadySentMgsSocket = true;
                    break;
                case "CLOSE_BROWSER":
                    if (sessionIdToSend.equals("scanner-element-pane")) {
                        splitDTO.setOperationId("closeBrowser");
                        String jsonData = gson.toJson(splitDTO);
                        webSocketSessionManager.sendMessageJson(
                                homeBankingId, sessionIdToSend, jsonData, "closeBrowser");
                    }
                    alreadySentMgsSocket = true;
                    break;
                case "HOVERED_ROW":
                    if (sessionIdToSend.equals("scannerTool")) {
                        splitDTO.setOperationId("highlight");
                        String jsonData = gson.toJson(splitDTO);
                        webSocketSessionManager.sendMessageJson(homeBankingId, sessionIdToSend, jsonData, null);
                    }
                    alreadySentMgsSocket = true;
                    break;
                case "CLEAR_HOVER_PICK_FILE": {
                    // Picker UI's "Clear Grid All" button when Hover Pick mode is on.
                    // Truncates elementDTO-HP.json + AI-ElementDTO-HP.json so the next pick
                    // starts a fresh cumulative list.
                    String hpClearPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_DB);
                    performMessage.clearHoverPickJson(hpClearPath);
                    alreadySentMgsSocket = true;
                    break;
                }
                case "SEARCH_TOOL":
                    if (sessionIdToSend.equals("scannerGrid")) {
                        // 1. UI gets the raw DTOs immediately (resolver enrichment is async-from-UI's POV).
                        String jsonData = gson.toJson(splitDTO);
                        webSocketSessionManager.sendMessageJson(homeBankingId, sessionIdToSend, jsonData, "addPickOne");

                        String jsonPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_DB);

                        // 2. DOM rects (needed by OCR correlator).
                        PageDiagnosticDumper.dumpRectsFromElements(
                                performActions.getCurrentDriver(), splitDTO.getElementDetails(), jsonPath, "page-HP");

                        // 3. OCR pipeline writes ocr-correlation-HP.json that the resolver consumes.
                        PageOcrDumper.runAndDump(
                                performActions.getCurrentDriver(), splitDTO.getElementDetails(), jsonPath, "page-HP");

                        // 4. Resolve someText + definedName from DOM + OCR (mutates DTOs in place).
                        //    Pass the active OcrConfig so the resolver picks up the OCR weight knobs
                        //    from the "DOM-First (Anti-Drift)" profile when the user has it active.
                        {
                            Integer cfgHbId = homeBankingId > 0 ? homeBankingId : null;
                            Integer cfgHomeUrlId = currentHomeUrlIdFromScene();
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
                            Integer homeUrlId = currentHomeUrlIdFromScene();
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
                    } else if (sessionIdToSend.equals("mobile-return-server")) {
                        String jsonData = gson.toJson(splitDTO);
                        webSocketSessionManager.sendMessageJson(
                                homeBankingId, "mobileScannerGrid", jsonData, "addPickOne");

                        String jsonPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_DB);

                        PageDiagnosticDumper.dumpRectsFromElements(
                                performActions.getCurrentDriver(), splitDTO.getElementDetails(), jsonPath, "page-HP");

                        PageOcrDumper.runAndDump(
                                performActions.getCurrentDriver(), splitDTO.getElementDetails(), jsonPath, "page-HP");

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
                case "ACTION_EXECUTOR":
                    // Route actionExecutor results to the ActionExecutorClient
                    actionExecutorClient.onResult(jsonEntry);
                    alreadySentMgsSocket = true;
                    break;
                case "UPDATE_LIST_ELEMENTS":
                    // calls perform list block update
                    if (sessionIdToSend.equals("perform-list-data")) {
                        splitDTO.setType("UPDATE_LIST_ELEMENTS");
                        String jsonData = gson.toJson(splitDTO);
                        webSocketSessionManager.sendMessageJson(
                                homeBankingId, "perform-list-data", jsonData, "UPDATE_LIST_ELEMENTS");
                    }
                    alreadySentMgsSocket = true;
                    break;
                case "NEW_ELEMENT_DTO":
                case "SEND_ALL_ELEMENTS_DTO":
                case "UPDATE_ALL_ELEMENTS_DTO":
                case "DEL_ELEMENT_DTO":
                case "DETAILS_ELEMENT_DTO":
                case "TEST_CLICK_DTO":
                case "TEST_INPUT_DTO": {
                    if (!performLists.getListBotJob().isEmpty() && splitDTO.getBotJobId() != null) {
                        performLists.getListBotJob().stream()
                                .filter(j -> java.util.Objects.equals(j.getId(), splitDTO.getBotJobId()))
                                .findFirst()
                                .ifPresent(j -> {
                                    splitDTO.setBotJobName(j.getName());
                                    splitDTO.setProjectType(j.getPriority());
                                });
                    }
                    if (splitDTO.getProjectType() != null
                            && (splitDTO.getProjectType().equalsIgnoreCase("Android")
                                    || splitDTO.getProjectType().equalsIgnoreCase("iOS"))) {
                        sessionIdToSend = "mobile-return-server";
                        splitDTO.setSessionId(sessionIdToSend);
                    }

                    if ("mobile-return-server".equals(sessionIdToSend)) {

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
                        String jsonData = gson.toJson(splitDTO);

                        if (!"NEW_ELEMENT_DTO".equals(type) && !"SEND_ALL_ELEMENTS_DTO".equals(type)) {
                            webSocketSessionManager.sendMessageJson(
                                    homeBankingId, "mobile-return-server", jsonData, type);
                        } else {
                            Session sessionBotJob = webSocketSessionManager.sendMessageJson(
                                    homeBankingId, "bot-job-scene", jsonData, type);
                            if (sessionBotJob == null) {
                                performMessage.errorMessage(
                                        "Bot Job Details Not Open",
                                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Operation Failed!</span>",
                                        "<span style='color: #1565C0; font-weight: bold;'>" + splitDTO.getBotJobName()
                                                + "</span>.",
                                        "<span style='color: #E65100; font-weight: bold;'>The Integration AR Mobile is waiting for the Bot Job to be open.</span>",
                                        "<span style='font-style: italic;'>Details: Please select and open the Bot Job on AR Web.</span>",
                                        0);
                            }
                        }
                    } else if (splitDTO.getElementDetails() != null && splitDTO.getElementDetails().length > 0) {
                        boolean isInsertType = "NEW_ELEMENT_DTO".equals(type) || "SEND_ALL_ELEMENTS_DTO".equals(type);
                        boolean isTestType = "TEST_CLICK_DTO".equals(type) || "TEST_INPUT_DTO".equals(type);
                        boolean paneOpen = WebSocketSessionManager.isSessionOpen("scanner-element-pane");
                        if (isTestType && !paneOpen) {
                            // PRE SCAN dashboard row test with AR Web Factory closed: the scanned
                            // page lives in the isolated pre-scan browser, so the test runs there.
                            ARViewBotJobPane.getInstance().handlePreScanElementTest(splitDTO, type);
                        } else if (isInsertType && !paneOpen) {
                            // PRE SCAN dashboard Apply with AR Web Factory closed: the pane
                            // session would swallow the message, so persist via the pane-free
                            // service instead (same insert + botJobTasks refresh).
                            ErrorMessage applyError =
                                    PreScanApplyService.getInstance().applyElements(splitDTO);
                            if (applyError != null) {
                                log.error("PRE SCAN Apply failed: {}", applyError.getErrorMessage());
                                performMessage.errorMessageOperationFailed(applyError);
                            }
                        } else {
                            webSocketSessionManager.sendMessageJson("scanner-element-pane", gson.toJson(splitDTO));
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
                    splitDTO.setType("UPDATE_BLOCKS");
                    jsonData = gson.toJson(splitDTO);
                    webSocketSessionManager.sendMessageJson(
                            homeBankingId, "perform-list-data", jsonData, "UPDATE_BLOCKS");
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
                    webSocketSessionManager.sendMessageJson(homeBankingId, "perform-list-data", jsonData, updteBlocks);
                    webSocketSessionManager.sendMessageJson(homeBankingId, "scannerGrid", jsonData, "blocksUpdate");
                    // The pre-scan dashboard has its own session; without this its block
                    // dropdown never refreshes after Create new block.
                    webSocketSessionManager.sendMessageJson(homeBankingId, "preScannerGrid", jsonData, "blocksUpdate");
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
                    webSocketSessionManager.sendMessageJson(homeBankingId, "perform-list-data", jsonData, updteBlocks);
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
                                    homeBankingId, "perform-list-data", jsonData, updteBlocks);
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
                    String moveRequestId = splitDTO.getRequestId() == null ? "" : splitDTO.getRequestId().trim();
                    if (moveRequestId.isEmpty()) {
                        errorMessage = new ErrorMessage(
                                "Move Instruction Refused", "Request ID is required", "Refresh the grid and try again.");
                        break;
                    }
                    synchronized (processedRowMoves) {
                        if (processedRowMoves.containsKey(moveRequestId)) break;
                    }
                    errorMessage = CommandEditorService.getInstance().validateMoveRevision(splitDTO);
                    if (errorMessage != null) break;
                    List<InstructionLoad> currentMoveRows = instrTable.equals("instruction")
                            ? performLists.getListInstruction()
                            : performLists.getListInstructionComp();
                    String moveError = instructionMoveValidator.validate(currentMoveRows, splitDTO.getUpdatedRows());
                    if (moveError != null) {
                        errorMessage = new ErrorMessage("Move Instruction Refused", "Invalid instruction graph", moveError);
                        break;
                    }
                    errorMessage = performDataBase.updateMoveRowsOrder(blockTable, whereId, splitDTO.getUpdatedRows());

                    // It needs to Reload this List
                    if (errorMessage == null) {
                        errorMessage = performDataBase.loadInstructions(whereId, -1, -1, instrTable);
                    }
                    if (errorMessage == null) {
                        errorMessage = performDataBase.loadBlocks(whereId, "", blockTable);
                    }
                    if (errorMessage == null) {
                        rememberCompletedRequest(processedRowMoves, moveRequestId);
                    }

                    // calls perform list block update
                    splitDTO.setType(updteBlocks);
                    jsonData = gson.toJson(splitDTO);
                    webSocketSessionManager.sendMessageJson(homeBankingId, "perform-list-data", jsonData, updteBlocks);

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
                        if (sessionIdToSend.matches(".*botJobTasks.*")) {
                            performLists.getListBotJob().clear();
                        } else if (sessionIdToSend.matches(".*componentTasks.*")) {
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
                                homeBankingId, "perform-list-data", jsonData, updteBlocks);

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
                    webSocketSessionManager.sendMessageJson(homeBankingId, "perform-list-data", jsonData, updteBlocks);
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
                    String storedAction = storedDelete.getActions() == null ? "" : storedDelete.getActions();
                    int storedParentId = storedDelete.getParentId() == null ? -1 : storedDelete.getParentId();
                    int storedBlockId = storedDelete.getBlockId() == null ? -1 : storedDelete.getBlockId();
                    if (!storedAction.equalsIgnoreCase(actions)
                            || storedParentId != parentId
                            || storedBlockId != blockId) {
                        errorMessage = new ErrorMessage(
                                "Delete Instruction Refused",
                                "Instruction metadata changed",
                                "Refresh the grid before deleting this instruction.");
                        break;
                    }

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
                                    homeBankingId, "perform-list-data", jsonData, updteBlocks);
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
                    if (errorMessage == null) rememberCompletedRequest(processedBlockDeletes, blockDeleteRequestId);

                    // calls perform list block update
                    splitDTO.setType(updteBlocks);
                    jsonData = gson.toJson(splitDTO);
                    webSocketSessionManager.sendMessageJson(homeBankingId, "perform-list-data", jsonData, updteBlocks);

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
                    webSocketSessionManager.sendMessageJson(homeBankingId, "perform-list-data", jsonData, updteBlocks);
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

        if (!alreadySentMgsSocket && (sessionIdToSend != null && sessionIdToSend.matches(".*botJobTasks.*"))) {
            if (performLists.getListBotJob().isEmpty()) {
                errorMessage = performDBEngine.loadCompleteJobs(botJobIdTask);
                if (errorMessage != null) {
                    performMessage.errorMessageOperationFailed(errorMessage);
                }
            }
        } else if (!alreadySentMgsSocket
                && (sessionIdToSend != null && sessionIdToSend.matches(".*componentTasks.*"))) {
            if (performLists.getListBotJobComp().isEmpty()) {
                errorMessage = performDataBase.loadComponentsComplete(homeBankingId, botJobIdTask, botJobNameTask);
                if (errorMessage != null) {
                    performMessage.errorMessageOperationFailed(errorMessage);
                }
            }
        }

        if (!alreadySentMgsSocket) {
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
            if ("updateInstructions".equals(updateAction)) {
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
                webSocketSessionManager.sendMessageJson(homeBankingId, sessionIdToSend, jsonData, updateAction);
            }

            //            broadcastMessageToAll(homeBankingId, "componentTasks", jsonData, "componentsUpdate");
            //            sendMessageJson(sessionIdToSend, jsonData, "componentsUpdate");
        }

        if ("ROW_MOVE".equals(type) || "DELETE_INSTRUCTION".equals(type) || "DELETE_BLOCK".equals(type)) {
            JsonObject mutationResponse = new JsonObject();
            mutationResponse.addProperty("ok", errorMessage == null);
            mutationResponse.addProperty("requestId", splitDTO.getRequestId());
            if (errorMessage != null) {
                mutationResponse.addProperty("errorTitle", errorMessage.getErrorTitle());
                mutationResponse.addProperty("errorHeader", errorMessage.getErrorHeader());
                mutationResponse.addProperty("error", errorMessage.getErrorMessage());
            }
            sendCommandEditorResponse(
                    homeBankingId,
                    sessionIdToSend,
                    "ROW_MOVE".equals(type) ? "instructionEditor.rowMoveResponse"
                            : "DELETE_BLOCK".equals(type) ? "instructionEditor.blockDeleteResponse"
                            : "instructionEditor.deleteResponse",
                    mutationResponse);
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
            webSocketSessionManager.removeSession(sessionId);
        } else {
            log.info("Connection closed for unknown session, Reason: " + closeReason.getReasonPhrase() + " (Code: "
                    + closeReason.getCloseCode() + ")");
        }
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

        if (!splitDTO.getType().equals("INSERT_BEFORE_ELSEIF")
                && !splitDTO.getType().equals("INSERT_AFTER_ELSEIF")) {

            webSocketSessionManager.sendMessageJson("bot-job-scene", gson.toJson(splitDTO));

        } else {

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
        // Ensure JavaFX UI updates are done on the JavaFX Application Thread

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
                    blockDetailsDTO.getHomeBankingId(), blockDetailsDTO.getSessionId(), jsonData, "updateInstructions");

        } else {
            performDataBase.deleteBlockDirect("block", blockDetailsDTO.getBotJobId(), newBlockId);
        }
    }

    private void setPayloadEmpty(String destination, int homeBankId, int botJobId, String botJobName) {
        int blockId = -1;
        int whereId = -1;
        if (destination.equalsIgnoreCase("botJobTasks")) {
            if (performLists.getListBlock().isEmpty()) {
                performDataBase.loadBlocks(botJobId, botJobName, "block");
            }
            whereId = botJobId;
            if (!performLists.getListBlock().isEmpty()) {
                blockId = performLists.getListBlock().get(0).getId();
            }

        } else if (destination.equalsIgnoreCase("componentTasks")) {
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

    private void sendCommandEditorResponse(
            int homeBankId, String sessionId, String operationId, Object response) {
        webSocketSessionManager.sendMessageJson(homeBankId, sessionId, gson.toJson(response), operationId);
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

    /** Best-effort lookup of the current pick's home_url_id via the scene's currentBotJob. Null when unavailable. */
    private static Integer currentHomeUrlIdFromScene() {
        try {
            com.allinweb.ch.component.scene.ARScannedElementScene scene =
                    com.allinweb.ch.component.scene.ARScannedElementScene.getInstance();
            if (scene == null) return null;
            BotJobLoadDTO job = scene.getCurrentBotJob();
            return job == null ? null : job.getHomeUrlId();
        } catch (Throwable t) {
            // Scene not initialised yet (early boot) or any unexpected NPE — locator scope falls back to bank-level.
            return null;
        }
    }
}

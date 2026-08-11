package com.allinweb.ch.socket;

import com.allinweb.ch.facade.BotJobDetailsWorkspaceRegistry;
import com.allinweb.ch.facade.GridItemWebElementTypeUpdateService;
import com.allinweb.ch.facade.GridItemWebElementTypeUpdateTransaction.MutationRefusedException;
import com.allinweb.ch.facade.GridItemWebElementTypeUpdateTransaction.UpdateResult;
import com.allinweb.ch.facade.PerformDBEngine;
import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.DetachedWorkspaceSessions;
import com.allinweb.ch.model.GridItemWebElementTypeContracts;
import com.allinweb.ch.model.GridItemWebElementTypeContracts.Request;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.ScannerWorkspaceOperations;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.allinweb.ch.util.ErrorMessage;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.sql.SQLException;
import java.util.List;
import javax.websocket.Session;
import lombok.extern.slf4j.Slf4j;

/** WebSocket boundary for one persisted GridItem INPUT/OUTPUT/CLICK type change. */
@Slf4j
public final class GridItemWebElementTypeService {
    private static final GridItemWebElementTypeService INSTANCE =
            new GridItemWebElementTypeService();

    private final BotJobDetailsWorkspaceRegistry workspaces;
    private final GridItemWebElementTypeUpdateService updates;
    private final PerformDBEngine engine;
    private final PerformLists lists;
    private final VariablesWorkspaceService variables;
    private final Gson gson;

    private GridItemWebElementTypeService() {
        this.workspaces = BotJobDetailsWorkspaceRegistry.getInstance();
        this.updates = GridItemWebElementTypeUpdateService.getInstance();
        this.engine = PerformDBEngine.getInstance();
        this.lists = PerformLists.getInstance();
        this.variables = VariablesWorkspaceService.getInstance();
        this.gson = new Gson();
    }

    public static GridItemWebElementTypeService getInstance() {
        return INSTANCE;
    }

    /** Emits this contract's response operation when the global license gate refuses the request. */
    public void rejectLicense(JsonObject body, Session transport) {
        send(
                transport,
                safePositiveInt(body, "homeBankingId"),
                failureBody(
                        safeText(body, "requestId"),
                        safePositiveInt(body, "instructionId"),
                        "LICENSE_REQUIRED",
                        "An active license is required to change a Web Element type."));
    }

    public void handle(JsonObject body, String sessionId, Session transport) {
        if (!authoritativeTransport(sessionId, transport)) {
            send(
                    transport,
                    safePositiveInt(body, "homeBankingId"),
                    failureBody(
                            safeText(body, "requestId"),
                            safePositiveInt(body, "instructionId"),
                            "TRANSPORT_NOT_AUTHORIZED",
                            "Only the active Bot Job Details or Smoke Test page can change this Web Element type."));
            return;
        }

        final Request request;
        try {
            request = GridItemWebElementTypeContracts.parse(body);
        } catch (IllegalArgumentException invalid) {
            send(
                    transport,
                    safePositiveInt(body, "homeBankingId"),
                    failureBody(
                            safeText(body, "requestId"),
                            safePositiveInt(body, "instructionId"),
                            "INVALID_REQUEST",
                            invalid.getMessage()));
            return;
        }

        JsonObject authorizedBody = body == null ? new JsonObject() : body.deepCopy();
        try {
            authorizeRequest(request, authorizedBody, sessionId, transport);

            Processed processed = workspaces.commitWorkspaceMutation(
                    request.botJobId(),
                    request.workspaceEpoch(),
                    () -> process(request));
            PublicationPlan publication = publicationPlan(processed.result());
            if (!authoritativeTransport(sessionId, transport)) {
                log.warn(
                        "GridItem Web Element type committed after transport changed requestId={} botJobId={} instructionId={}",
                        request.requestId(),
                        request.botJobId(),
                        request.instructionId());
                if (publication.notifyVariables()) {
                    VariablesWorkspaceService.getInstance().notifyMutation(request.botJobId());
                }
                return;
            }

            send(transport, request.homeBankingId(), successBody(processed));
            if (ScannerWorkspaceSessions.BOT_JOB_TASKS.equals(sessionId)
                    && processed.snapshotJson() != null) {
                // Keep the post-ack snapshot bound to the same physical transport. Looking it up
                // again by logical session ID could publish this Bot Job into a replacement page.
                WebSocketSessionManager.sendMessageJson(
                        request.homeBankingId(),
                        transport,
                        ScannerWorkspaceSessions.BOT_JOB_TASKS,
                        processed.snapshotJson(),
                        ScannerWorkspaceOperations.UPDATE_INSTRUCTIONS);
            }
            if (publication.notifyVariables()) {
                VariablesWorkspaceService.getInstance().notifyMutation(request.botJobId());
            }
        } catch (PersistenceFailure persistenceFailure) {
            Throwable cause = persistenceFailure.getCause();
            if (cause instanceof MutationRefusedException refused) {
                send(
                        transport,
                        request.homeBankingId(),
                        failure(request, refused.code(), refused.getMessage()));
                return;
            }
            log.error(
                    "GridItem Web Element type persistence failed requestId={} botJobId={} instructionId={}",
                    request.requestId(),
                    request.botJobId(),
                    request.instructionId(),
                    cause);
            send(
                    transport,
                    request.homeBankingId(),
                    failure(
                            request,
                            "WEB_ELEMENT_TYPE_PERSISTENCE_FAILED",
                            "The Web Element type could not be saved."));
        } catch (IllegalArgumentException | IllegalStateException refused) {
            send(
                    transport,
                    request.homeBankingId(),
                    failure(request, "WEB_ELEMENT_TYPE_REFUSED", refused.getMessage()));
        } catch (RuntimeException unexpected) {
            log.error(
                    "Unexpected GridItem Web Element type failure requestId={} botJobId={} instructionId={}",
                    request.requestId(),
                    request.botJobId(),
                    request.instructionId(),
                    unexpected);
            send(
                    transport,
                    request.homeBankingId(),
                    failure(
                            request,
                            "WEB_ELEMENT_TYPE_UNAVAILABLE",
                            "The Web Element type service is unavailable."));
        }
    }

    private Processed process(Request request) {
        try {
            UpdateResult result = updates.update(request);
            if (!publicationPlan(result).prepareSnapshot()) {
                return new Processed(result, null, false);
            }
            PreparedSnapshot prepared;
            try {
                prepared = prepareSnapshot(request.botJobId());
            } catch (RuntimeException refreshFailure) {
                log.warn(
                        "GridItem Web Element type saved but snapshot preparation failed botJobId={}",
                        request.botJobId(),
                        refreshFailure);
                prepared = new PreparedSnapshot(null, true);
            }
            return new Processed(
                    result,
                    prepared.snapshotJson(),
                    prepared.resyncRequired());
        } catch (SQLException databaseFailure) {
            throw new PersistenceFailure(databaseFailure);
        }
    }

    private BotJobDetailsWorkspaceRegistry.Snapshot authorizeRequest(
            Request request,
            JsonObject body,
            String sessionId,
            Session transport) {
        if (!authoritativeTransport(sessionId, transport)) {
            throw new IllegalStateException(
                    "The Web Element type page changed before the update started.");
        }
        BotJobDetailsWorkspaceRegistry.Snapshot workspace;
        if (DetachedWorkspaceSessions.SMOKE_TEST_MANAGER.equals(sessionId)) {
            VariablesWorkspaceService.SmokeIntegrationAuthorization authority =
                    variables.authorizeSmokeIntegration(body, sessionId, transport);
            if (authority.homeBankingId() != request.homeBankingId()
                    || authority.botJobId() != request.botJobId()
                    || authority.workspaceEpoch() != request.workspaceEpoch()) {
                throw new IllegalArgumentException(
                        "The Smoke Test Web Element type no longer matches the active Bot Job.");
            }
            workspace = workspaces.require(request.botJobId(), request.workspaceEpoch());
        } else {
            workspace = workspaces.require(request.botJobId(), request.workspaceEpoch());
        }
        if (workspace.homeBankingId() != request.homeBankingId()) {
            throw new IllegalArgumentException(
                    "The Web Element organization does not match the active Bot Job.");
        }
        return workspace;
    }

    static PublicationPlan publicationPlan(UpdateResult result) {
        if (result == null || !result.changed()) {
            return new PublicationPlan(false, false);
        }
        // A replay still needs an authoritative snapshot because the original response or
        // snapshot may have been lost. Variables already received the notification from the
        // original committed request, so replaying must not broadcast it a second time.
        return new PublicationPlan(true, !result.duplicate());
    }

    private PreparedSnapshot prepareSnapshot(int botJobId) {
        ErrorMessage error = engine.loadCompleteJobs(botJobId);
        if (error != null) {
            log.warn(
                    "GridItem Web Element type saved but authoritative grid reload failed botJobId={}",
                    botJobId);
            return new PreparedSnapshot(null, true);
        }

        List<InstructionLoad> instructions = List.of();
        List<BotJobLoadDTO> jobs = lists.getListBotJob();
        if (jobs != null && !jobs.isEmpty()) {
            List<InstructionLoad> loaded = lists.buildJsonViewData(jobs);
            if (loaded != null) instructions = loaded;
        }
        return new PreparedSnapshot(gson.toJson(instructions), false);
    }

    boolean authoritativeTransport(String sessionId, Session transport) {
        if (transport == null || !transport.isOpen()) return false;
        String registered = WebSocketSessionManager.getInstance()
                .getSessionIdBySession(transport);
        if (DetachedWorkspaceSessions.SMOKE_TEST_MANAGER.equals(sessionId)) {
            return sessionId.equals(registered)
                    && WebSocketSessionManager.getSession(sessionId) == transport;
        }
        return ScannerWorkspaceSessions.BOT_JOB_TASKS.equals(sessionId)
                && ScannerWorkspaceSessions.BOT_JOB_TASKS.equals(registered)
                && WebSocketSessionManager.getSession(ScannerWorkspaceSessions.BOT_JOB_TASKS)
                        == transport;
    }

    private JsonObject successBody(Processed processed) {
        UpdateResult result = processed.result();
        JsonObject response = new JsonObject();
        response.addProperty("contractVersion", GridItemWebElementTypeContracts.CONTRACT_VERSION);
        response.addProperty("requestId", result.requestId());
        response.addProperty("ok", true);
        response.addProperty("changed", result.changed());
        response.addProperty("replayed", result.duplicate());
        response.addProperty("instructionId", result.instructionId());
        response.addProperty("previousType", result.previousType().name());
        response.addProperty("committedType", result.committedType().name());
        response.addProperty("committedAction", result.committedAction());
        response.addProperty("graphVersion", result.committedGraphVersion());
        response.addProperty("graphRevision", result.graphRevision());
        response.addProperty("workspaceEpoch", result.workspaceEpoch());
        response.addProperty("resyncRequired", processed.resyncRequired());
        response.addProperty(
                "message",
                processed.resyncRequired()
                        ? "Web Element type saved. Refresh Bot Job Details to load it."
                        : result.duplicate()
                                ? "Web Element type request already completed."
                                : result.changed()
                                        ? "Web Element type updated."
                                        : "The Web Element already uses that type.");
        return response;
    }

    private static JsonObject failure(Request request, String code, String message) {
        return failureBody(
                request == null ? null : request.requestId(),
                request == null ? 0 : request.instructionId(),
                code,
                message);
    }

    private static JsonObject failureBody(
            String requestId,
            int instructionId,
            String code,
            String message) {
        JsonObject response = new JsonObject();
        response.addProperty("contractVersion", GridItemWebElementTypeContracts.CONTRACT_VERSION);
        if (requestId != null && !requestId.isBlank()) {
            response.addProperty("requestId", requestId.trim());
        }
        response.addProperty("ok", false);
        response.addProperty("changed", false);
        response.addProperty("instructionId", Math.max(0, instructionId));
        response.addProperty("code", code);
        response.addProperty(
                "message",
                message == null || message.isBlank()
                        ? "The Web Element type could not be changed."
                        : message);
        response.addProperty("resyncRequired", false);
        return response;
    }

    private void send(Session transport, int homeBankingId, JsonObject body) {
        String targetSessionId = transport == null
                ? ""
                : WebSocketSessionManager.getInstance().getSessionIdBySession(transport);
        if (!ScannerWorkspaceSessions.BOT_JOB_TASKS.equals(targetSessionId)
                && !DetachedWorkspaceSessions.SMOKE_TEST_MANAGER.equals(targetSessionId)) {
            return;
        }
        WebSocketSessionManager.sendMessageJson(
                Math.max(0, homeBankingId),
                transport,
                targetSessionId,
                gson.toJson(body),
                GridItemWebElementTypeContracts.RESPONSE);
    }

    private static String safeText(JsonObject body, String field) {
        if (body == null) return null;
        JsonElement value = body.get(field);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) return null;
        try {
            String text = value.getAsString();
            return text == null || text.isBlank() ? null : text.trim();
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private static int safePositiveInt(JsonObject body, String field) {
        if (body == null) return 0;
        JsonElement value = body.get(field);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) return 0;
        try {
            return Math.max(0, value.getAsInt());
        } catch (RuntimeException invalid) {
            return 0;
        }
    }

    private record PreparedSnapshot(String snapshotJson, boolean resyncRequired) {}

    private record Processed(
            UpdateResult result,
            String snapshotJson,
            boolean resyncRequired) {}

    record PublicationPlan(boolean prepareSnapshot, boolean notifyVariables) {}

    private static final class PersistenceFailure extends RuntimeException {
        private PersistenceFailure(SQLException cause) {
            super(cause);
        }
    }
}

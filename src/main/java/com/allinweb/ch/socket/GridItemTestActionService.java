package com.allinweb.ch.socket;

import com.allinweb.ch.facade.BotJobDetailsWorkspaceRegistry;
import com.allinweb.ch.facade.BotJobGraphMutationService;
import com.allinweb.ch.facade.CommandRegistry;
import com.allinweb.ch.facade.ExecutionPauseCoordinator;
import com.allinweb.ch.facade.execution.GridItemTestActionExecutor;
import com.allinweb.ch.facade.execution.GridItemTestActionExecutor.InputValuePolicy;
import com.allinweb.ch.facade.execution.GridItemTestActionExecutor.Outcome;
import com.allinweb.ch.facade.execution.GridItemTestInstructionRepository;
import com.allinweb.ch.facade.execution.GridItemTestInstructionRepository.InstructionSnapshot;
import com.allinweb.ch.model.GridItemTestActionContracts;
import com.allinweb.ch.model.GridItemTestActionContracts.Action;
import com.allinweb.ch.model.GridItemTestActionContracts.Request;
import com.allinweb.ch.model.DetachedWorkspaceSessions;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.websocket.Session;
import lombok.extern.slf4j.Slf4j;

/** Independent one-request/one-action GridItem Playwright boundary. */
@Slf4j
public final class GridItemTestActionService {
    private static final int MAX_LEDGER_ENTRIES = 256;
    private static final Set<String> TESTABLE_WEB_ELEMENT_ACTIONS = Set.of(
            "I", "INPUT", "O", "OUTPUT", "C", "CLICK", "A", "ANCHOR", "W", "OTHER");
    private static final GridItemTestActionService INSTANCE = new GridItemTestActionService();

    private final BotJobDetailsWorkspaceRegistry workspaces;
    private final BotJobGraphMutationService graphs;
    private final GridItemTestInstructionRepository instructions;
    private final ExcelDataWorkspaceService excel;
    private final VariablesWorkspaceService variables;
    private final GridItemTestActionExecutor executor;
    private final ExecutionPauseCoordinator execution;
    private final ThreadPoolExecutor worker;
    private final Map<String, LedgerEntry> ledger = new LinkedHashMap<>();

    private GridItemTestActionService() {
        this.workspaces = BotJobDetailsWorkspaceRegistry.getInstance();
        this.graphs = BotJobGraphMutationService.getInstance();
        this.instructions = new GridItemTestInstructionRepository();
        this.excel = ExcelDataWorkspaceService.getInstance();
        this.variables = VariablesWorkspaceService.getInstance();
        this.executor = new GridItemTestActionExecutor(excel::publishActiveCell);
        this.execution = ExecutionPauseCoordinator.getInstance();
        this.worker = newWorker();
    }

    static ThreadPoolExecutor newWorker() {
        return new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new SynchronousQueue<>(),
                runnable -> {
                    Thread thread = new Thread(runnable, "grid-item-test-action");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    public static GridItemTestActionService getInstance() {
        return INSTANCE;
    }

    /** Validates and schedules one action without blocking the WebSocket transport thread. */
    public void handle(JsonObject body, String sessionId, Session transport) {
        if (!authoritativeTransport(sessionId, transport)) {
            send(
                    transport,
                    safePositiveInt(body, "homeBankingId"),
                    failureBody(
                            safeText(body, "requestId"),
                            safePositiveInt(body, "instructionId"),
                            safeText(body, "action"),
                            "TRANSPORT_NOT_AUTHORIZED",
                            "Only the active Bot Job Details or Smoke Test page can run this action."));
            return;
        }

        final Request request;
        try {
            request = GridItemTestActionContracts.parse(body);
        } catch (IllegalArgumentException invalid) {
            send(
                    transport,
                    safePositiveInt(body, "homeBankingId"),
                    failureBody(
                            safeText(body, "requestId"),
                            safePositiveInt(body, "instructionId"),
                            safeText(body, "action"),
                            "INVALID_REQUEST",
                            invalid.getMessage()));
            return;
        }

        JsonObject authorizedBody = body == null ? new JsonObject() : body.deepCopy();
        try {
            authorizeRequest(request, authorizedBody, sessionId, transport);
        } catch (IllegalArgumentException | IllegalStateException refused) {
            log.warn(
                    "GridItem test admission refused sessionId={} botJobId={} instructionId={} action={} reason={}",
                    sessionId,
                    request.botJobId(),
                    request.instructionId(),
                    request.action(),
                    humanMessage(refused));
            send(
                    transport,
                    request.homeBankingId(),
                    failure(request, "ACTION_REFUSED", humanMessage(refused)));
            return;
        }

        String fingerprint = fingerprint(request);
        JsonObject immediate = reserve(request, fingerprint);
        if (immediate != null) {
            send(transport, request.homeBankingId(), immediate);
            return;
        }
        try {
            worker.execute(() -> process(
                    request, fingerprint, authorizedBody, sessionId, transport));
        } catch (RejectedExecutionException busy) {
            synchronized (ledger) {
                ledger.remove(request.requestId());
            }
            send(
                    transport,
                    request.homeBankingId(),
                    failure(request, "ACTION_BUSY", "Another GridItem test action is still running."));
        }
    }

    private void process(
            Request request,
            String fingerprint,
            JsonObject authorizedBody,
            String sessionId,
            Session transport) {
        JsonObject response;
        try {
            BotJobDetailsWorkspaceRegistry.Snapshot workspace = authorizeRequest(
                    request, authorizedBody, sessionId, transport);

            try (ExecutionPauseCoordinator.ScannerActivity ignored =
                    execution.beginScannerActivity(request.botJobId(), workspace.workspaceEpoch())) {
                validateGraphIfProvided(request, workspace.workspaceEpoch());
                InstructionSnapshot instruction = instructions.load(
                        request.homeBankingId(), request.botJobId(), request.instructionId());
                validateStoredAction(instruction, request.action());
                Optional<ExcelDataWorkspaceService.GridItemDataset> selected =
                        request.action() == Action.INPUT
                                ? excel.freezeSelectedGridItemData(
                                        request.homeBankingId(), request.botJobId())
                                : Optional.empty();
                Outcome outcome = executor.execute(
                        instruction,
                        request.action(),
                        request.excelRowIndex(),
                        selected,
                        DetachedWorkspaceSessions.SMOKE_TEST_MANAGER.equals(sessionId)
                                ? InputValuePolicy.REQUIRE_EXCEL_MEMORY
                                : InputValuePolicy.ALLOW_ABC_FALLBACK);
                log.info(
                        "GridItem test outcome sessionId={} botJobId={} instructionId={} action={} ok={} code={} valueSource={} datasetMode={} excelRowIndex={} column={}",
                        sessionId,
                        request.botJobId(),
                        request.instructionId(),
                        request.action(),
                        outcome.passed(),
                        outcome.code(),
                        outcome.valueSource(),
                        outcome.datasetMode(),
                        outcome.excelRowIndex(),
                        outcome.column());
                response = response(request, outcome);
            }
        } catch (IllegalArgumentException | IllegalStateException refused) {
            log.warn(
                    "GridItem test refused sessionId={} botJobId={} instructionId={} action={} reason={}",
                    sessionId,
                    request.botJobId(),
                    request.instructionId(),
                    request.action(),
                    humanMessage(refused));
            response = failure(request, "ACTION_REFUSED", humanMessage(refused));
        } catch (SQLException databaseFailure) {
            log.warn(
                    "GridItem test database refusal requestId={} botJobId={} instructionId={}: {}",
                    request.requestId(),
                    request.botJobId(),
                    request.instructionId(),
                    databaseFailure.getMessage());
            response = failure(
                    request,
                    "INSTRUCTION_NOT_AVAILABLE",
                    "The GridItem instruction could not be loaded from the active Bot Job.");
        } catch (RuntimeException actionFailure) {
            log.error(
                    "GridItem Playwright test failed requestId={} botJobId={} instructionId={} action={}; details redacted",
                    request.requestId(),
                    request.botJobId(),
                    request.instructionId(),
                    request.action());
            response = failure(
                    request,
                    "PLAYWRIGHT_ACTION_FAILED",
                    "The active Playwright page could not complete this test action.");
        }

        complete(request.requestId(), fingerprint, response);
        send(transport, request.homeBankingId(), response);
    }

    /** Correlated license refusal used by either supported detached page. */
    public void rejectLicense(JsonObject body, Session transport) {
        send(
                transport,
                safePositiveInt(body, "homeBankingId"),
                failureBody(
                        safeText(body, "requestId"),
                        safePositiveInt(body, "instructionId"),
                        safeText(body, "action"),
                        "LICENSE_REQUIRED",
                        "An active license is required for this Playwright test action."));
    }

    private BotJobDetailsWorkspaceRegistry.Snapshot authorizeRequest(
            Request request, JsonObject body, String sessionId, Session transport) {
        if (!authoritativeTransport(sessionId, transport)) {
            throw new IllegalStateException(
                    "The instruction-test page changed before the action started.");
        }
        BotJobDetailsWorkspaceRegistry.Snapshot workspace;
        if (DetachedWorkspaceSessions.SMOKE_TEST_MANAGER.equals(sessionId)) {
            VariablesWorkspaceService.SmokeTestActionAuthorization authority =
                    variables.authorizeSmokeTestAction(body, sessionId, transport);
            if (authority.homeBankingId() != request.homeBankingId()
                    || authority.botJobId() != request.botJobId()
                    || request.workspaceEpoch() == null
                    || authority.workspaceEpoch() != request.workspaceEpoch()) {
                throw new IllegalArgumentException(
                        "The Smoke Test action no longer matches the active Bot Job.");
            }
            workspace = workspaces.require(request.botJobId(), request.workspaceEpoch());
        } else {
            workspace = request.workspaceEpoch() == null
                    ? workspaces.require(request.botJobId())
                    : workspaces.require(request.botJobId(), request.workspaceEpoch());
        }
        if (workspace.homeBankingId() != request.homeBankingId()) {
            throw new IllegalArgumentException(
                    "The instruction-test organization does not match the active Bot Job.");
        }
        return workspace;
    }

    private void validateGraphIfProvided(Request request, long workspaceEpoch) throws SQLException {
        if (request.baseGraphVersion() == null && request.graphRevision() == null) return;
        var current = graphs.inspect(
                request.homeBankingId(), request.botJobId(), workspaceEpoch);
        if (request.baseGraphVersion() != null
                && request.baseGraphVersion() != current.graphVersion()) {
            throw new IllegalStateException(
                    "The GridItem graph changed. Refresh before testing this instruction.");
        }
        if (request.graphRevision() != null
                && !request.graphRevision().equalsIgnoreCase(current.graphRevision())) {
            throw new IllegalStateException(
                    "The GridItem graph changed. Refresh before testing this instruction.");
        }
    }

    static void validateStoredAction(
            InstructionSnapshot instruction, Action requestedAction) {
        String stored = CommandRegistry.canonicalize(instruction.storedAction());
        if (requestedAction == null || !TESTABLE_WEB_ELEMENT_ACTIONS.contains(stored)) {
            throw new IllegalArgumentException(
                    "The selected GridItem row is not a testable Web Element. Refresh before running this test.");
        }
    }

    private JsonObject reserve(Request request, String fingerprint) {
        synchronized (ledger) {
            LedgerEntry existing = ledger.get(request.requestId());
            if (existing != null) {
                if (!existing.fingerprint().equals(fingerprint)) {
                    return failure(
                            request,
                            "REQUEST_ID_REUSED",
                            "This GridItem test request ID was already used for another action.");
                }
                if (existing.response() == null) {
                    return failure(
                            request,
                            "ACTION_IN_PROGRESS",
                            "This GridItem test action is already running.");
                }
                JsonObject replay = existing.response().deepCopy();
                replay.addProperty("replayed", true);
                return replay;
            }
            ledger.put(request.requestId(), new LedgerEntry(fingerprint, null));
            trimLedger();
            return null;
        }
    }

    private void complete(String requestId, String fingerprint, JsonObject response) {
        synchronized (ledger) {
            LedgerEntry current = ledger.get(requestId);
            if (current != null && current.fingerprint().equals(fingerprint)) {
                ledger.put(requestId, new LedgerEntry(fingerprint, response.deepCopy()));
            }
            trimLedger();
        }
    }

    private void trimLedger() {
        while (ledger.size() > MAX_LEDGER_ENTRIES) {
            String first = ledger.keySet().iterator().next();
            LedgerEntry entry = ledger.get(first);
            if (entry != null && entry.response() == null) return;
            ledger.remove(first);
        }
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
                && transport.isOpen()
                && ScannerWorkspaceSessions.BOT_JOB_TASKS.equals(
                        registered)
                && WebSocketSessionManager.getSession(ScannerWorkspaceSessions.BOT_JOB_TASKS)
                        == transport;
    }

    private static String fingerprint(Request request) {
        return request.homeBankingId()
                + ":" + request.botJobId()
                + ":" + request.instructionId()
                + ":" + request.action()
                + ":" + request.excelRowIndex()
                + ":" + request.workspaceEpoch()
                + ":" + request.baseGraphVersion()
                + ":" + request.graphRevision();
    }

    JsonObject response(Request request, Outcome outcome) {
        JsonObject response = base(request);
        response.addProperty("ok", outcome.passed());
        response.addProperty("status", outcome.passed() ? "PASSED" : "FAILED");
        response.addProperty("code", outcome.code());
        response.addProperty("message", outcome.message());
        response.addProperty("valueSource", outcome.valueSource());
        addNullable(response, "datasetMode", outcome.datasetMode());
        addNullable(response, "excelRowIndex", outcome.excelRowIndex());
        addNullable(response, "column", outcome.column());
        addNullable(response, "datasetEpoch", outcome.datasetEpoch());
        addNullable(response, "datasetRevision", outcome.datasetRevision());
        response.addProperty("replayed", false);
        return response;
    }

    private JsonObject failure(Request request, String code, String message) {
        JsonObject response = base(request);
        response.addProperty("ok", false);
        response.addProperty("status", "FAILED");
        response.addProperty("code", code);
        response.addProperty("message", message);
        response.addProperty("valueSource", request.action() == Action.INPUT
                ? "UNAVAILABLE" : "NOT_APPLICABLE");
        response.add("datasetMode", null);
        response.addProperty("excelRowIndex", request.excelRowIndex());
        response.add("column", null);
        response.addProperty("replayed", false);
        return response;
    }

    private JsonObject base(Request request) {
        JsonObject response = new JsonObject();
        response.addProperty("contractVersion", GridItemTestActionContracts.CONTRACT_VERSION);
        response.addProperty("requestId", request.requestId());
        response.addProperty("botJobId", request.botJobId());
        response.addProperty("instructionId", request.instructionId());
        response.addProperty("action", request.action().name());
        return response;
    }

    private JsonObject failureBody(
            String requestId,
            int instructionId,
            String action,
            String code,
            String message) {
        JsonObject response = new JsonObject();
        response.addProperty("contractVersion", GridItemTestActionContracts.CONTRACT_VERSION);
        response.addProperty("requestId", requestId == null ? "" : requestId);
        response.addProperty("ok", false);
        if (instructionId > 0) response.addProperty("instructionId", instructionId);
        if (action != null) response.addProperty("action", action);
        response.addProperty("status", "FAILED");
        response.addProperty("code", code);
        response.addProperty("message", message == null || message.isBlank()
                ? "The GridItem test request is invalid." : message);
        response.addProperty("valueSource", "UNAVAILABLE");
        response.add("datasetMode", null);
        response.add("column", null);
        response.addProperty("replayed", false);
        return response;
    }

    private void send(Session transport, int homeBankingId, JsonObject response) {
        String targetSessionId = transport == null
                ? ""
                : WebSocketSessionManager.getInstance().getSessionIdBySession(transport);
        if (!ScannerWorkspaceSessions.BOT_JOB_TASKS.equals(targetSessionId)
                && !DetachedWorkspaceSessions.SMOKE_TEST_MANAGER.equals(targetSessionId)) {
            return;
        }
        WebSocketSessionManager.sendMessageJson(
                Math.max(homeBankingId, 0),
                transport,
                targetSessionId,
                response.toString(),
                GridItemTestActionContracts.RESPONSE);
    }

    private static String safeText(JsonObject body, String field) {
        if (body == null) return null;
        JsonElement value = body.get(field);
        if (value == null || value.isJsonNull()) return null;
        try {
            return value.getAsString();
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private static int safePositiveInt(JsonObject body, String field) {
        if (body == null) return 0;
        JsonElement value = body.get(field);
        if (value == null || value.isJsonNull()) return 0;
        try {
            return Math.max(value.getAsInt(), 0);
        } catch (RuntimeException invalid) {
            return 0;
        }
    }

    private static String humanMessage(Throwable failure) {
        String message = failure == null ? null : failure.getMessage();
        return message == null || message.isBlank()
                ? "The GridItem test action is unavailable." : message;
    }

    private static void addNullable(JsonObject target, String name, String value) {
        if (value == null) target.add(name, null);
        else target.addProperty(name, value);
    }

    private static void addNullable(JsonObject target, String name, Long value) {
        if (value == null) target.add(name, null);
        else target.addProperty(name, value);
    }

    private static void addNullable(JsonObject target, String name, Integer value) {
        if (value == null) target.add(name, null);
        else target.addProperty(name, value);
    }

    private record LedgerEntry(String fingerprint, JsonObject response) {}
}

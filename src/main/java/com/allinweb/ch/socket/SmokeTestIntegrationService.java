package com.allinweb.ch.socket;

import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.driver.ARPlaywrightDriver;
import com.allinweb.ch.facade.BotJobDetailsWorkspaceRegistry;
import com.allinweb.ch.facade.ExecutionPauseCoordinator;
import com.allinweb.ch.facade.actions.RuntimeVariableValue;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.Owner;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.Plan;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationStepExecutor;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationV1RecoveryCoordinator;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationExcelWriteService;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationStepExecutor.Outcome;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationStepExecutor.RunVariables;
import com.allinweb.ch.facade.execution.v2.ExecutionRuntimeRunCoordinator;
import com.allinweb.ch.facade.execution.v2.ExecutionV2RuntimeSupervisor;
import com.allinweb.ch.facade.execution.v2.ExecutionV2Contracts.AuthorizedGrantFacts;
import com.allinweb.ch.facade.execution.v2.ExecutionV2Contracts.DataMode;
import com.allinweb.ch.facade.execution.v2.SmokeTestIntegrationV2StepExecutor;
import com.allinweb.ch.model.DetachedWorkspaceSessions;
import com.allinweb.ch.model.SmokeTestIntegrationContracts;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.Correlation;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.FinishRequest;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.ForceStopRequest;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.ExcelWriteRequest;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.PagePolicy;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.RefreshRequest;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.RecoveryRequest;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.RecoveryDecision;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.RunStatus;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.RuntimeSnapshot;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.RuntimeMode;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.StartRequest;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.StartResponse;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.StepRequest;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.StepResponse;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.StepStatus;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.StopRequest;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.TerminalResponse;
import com.allinweb.ch.socket.ExcelDataWorkspaceService.IntegrationDataset;
import com.allinweb.ch.socket.VariablesWorkspaceService.SmokeIntegrationAuthorization;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import javax.websocket.Session;
import lombok.extern.slf4j.Slf4j;

/**
 * Owns the one process-wide real-browser Smoke Test Integration run.
 *
 * <p>React owns the program counter and sends one instruction ID at a time. This service never
 * calls {@code executeJob}, {@code TestRunLauncher}, or a legacy global list. It freezes SQL and
 * Excel facts once, executes only the requested authoritative step, and keeps exclusive browser
 * ownership until STOP, FINISH, disconnect, or shutdown.
 */
@Slf4j
public final class SmokeTestIntegrationService {
    private static final org.slf4j.Logger executionTrace =
            org.slf4j.LoggerFactory.getLogger("com.allinweb.smoke.execution");
    private static final int MAX_REQUEST_LEDGER = 512;
    private static final int MAX_SEQUENCE_LEDGER = 2_048;
    private static final String SESSION_ID = DetachedWorkspaceSessions.SMOKE_TEST_MANAGER;
    private static final SmokeTestIntegrationService INSTANCE =
            new SmokeTestIntegrationService();

    private final Object stateLock = new Object();
    private final Gson gson;
    private final BindingPort variables;
    private final DatasetPort excel;
    private final SnapshotPort snapshots;
    private final StepPort steps;
    private final V2Port v2;
    private final BrowserOwnershipPort browserOwnership;
    private final WorkspacePort workspaces;
    private final BrowserStartPort browser;
    private final ResponsePort responses;
    private final ThreadPoolExecutor worker;
    private final AtomicLong integrationEpochs = new AtomicLong();
    private final ExecutionV2RuntimeSupervisor runtimeSupervisor =
            ExecutionV2RuntimeSupervisor.getInstance();
    private final SmokeTestIntegrationExcelWriteService excelWrites = new SmokeTestIntegrationExcelWriteService();
    private final LinkedHashMap<TransportRequest, RequestLedgerEntry> requestLedger =
            new LinkedHashMap<>();
    private static final int MAX_ACTIVE_V2_RUNS = 5;

    private final LinkedHashMap<String, Run> activeRuns = new LinkedHashMap<>();
    private final LinkedHashMap<TransportRequest, PendingStart> pendingStartAttempts =
            new LinkedHashMap<>();
    private int pendingStarts;
    private int pendingV1Starts;
    private boolean refreshPending;
    private boolean runtimeControlPending;

    private SmokeTestIntegrationService() {
        this(
                new Gson(),
                new DefaultBindingPort(),
                new DefaultDatasetPort(),
                new DefaultSnapshotPort(),
                new DefaultStepPort(),
                new DefaultV2Port(),
                new DefaultBrowserOwnershipPort(),
                new DefaultWorkspacePort(),
                new DefaultBrowserStartPort(),
                new WebSocketResponsePort(),
                newWorker());
    }

    SmokeTestIntegrationService(
            Gson gson,
            BindingPort variables,
            DatasetPort excel,
            SnapshotPort snapshots,
            StepPort steps,
            V2Port v2,
            BrowserOwnershipPort browserOwnership,
            WorkspacePort workspaces,
            BrowserStartPort browser,
            ResponsePort responses,
            ThreadPoolExecutor worker) {
        this.gson = Objects.requireNonNull(gson, "gson");
        this.variables = Objects.requireNonNull(variables, "variables");
        this.excel = Objects.requireNonNull(excel, "excel");
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
        this.steps = Objects.requireNonNull(steps, "steps");
        this.v2 = Objects.requireNonNull(v2, "Execution V2 port is required");
        this.browserOwnership = Objects.requireNonNull(browserOwnership, "browserOwnership");
        this.workspaces = Objects.requireNonNull(workspaces, "workspaces");
        this.browser = Objects.requireNonNull(browser, "browser");
        this.responses = Objects.requireNonNull(responses, "responses");
        this.worker = Objects.requireNonNull(worker, "worker");
    }

    public static SmokeTestIntegrationService getInstance() {
        return INSTANCE;
    }

    /** Parses, deduplicates, and asynchronously handles one exact integration operation. */
    public void handle(String operation, JsonObject body, String sessionId, Session transport) {
        boolean smokeTransport = SESSION_ID.equals(sessionId) && isRegisteredTransport(transport);
        boolean dashboardTransport = "mainDashboard".equals(sessionId)
                && transport != null
                && transport.isOpen()
                && WebSocketSessionManager.getSession("mainDashboard") == transport;
        if (!smokeTransport && !dashboardTransport) {
            rejectTransport(operation, body, transport);
            return;
        }
        try {
            switch (operation) {
                case SmokeTestIntegrationContracts.START -> {
                    if (dashboardTransport && !hasMultiBatch(body)) {
                        throw new IllegalArgumentException(
                                "Main Dashboard Integration requires a prepared multi-run batch.");
                    }
                    handleStart(SmokeTestIntegrationContracts.parseStart(body), body, transport);
                }
                case SmokeTestIntegrationContracts.REFRESH -> handleRefresh(
                        dashboardTransport
                                ? throwUnsupportedDashboardRefresh()
                                : SmokeTestIntegrationContracts.parseRefresh(body),
                        body,
                        transport);
                case SmokeTestIntegrationContracts.STEP -> handleStep(
                        SmokeTestIntegrationContracts.parseStep(body), transport);
                case SmokeTestIntegrationContracts.RECOVER -> handleRecovery(
                        SmokeTestIntegrationContracts.parseRecovery(body), transport);
                case SmokeTestIntegrationContracts.EXCEL_WRITE -> handleExcelWrite(
                        SmokeTestIntegrationContracts.parseExcelWrite(body), transport);
                case SmokeTestIntegrationContracts.STOP -> handleStop(
                        SmokeTestIntegrationContracts.parseStop(body), transport);
                case SmokeTestIntegrationContracts.FORCE_STOP -> handleForceStop(
                        SmokeTestIntegrationContracts.parseForceStop(body), body, transport);
                case SmokeTestIntegrationContracts.FINISH -> handleFinish(
                        SmokeTestIntegrationContracts.parseFinish(body), transport);
                case SmokeTestIntegrationContracts.RUNTIME_STATUS ->
                        handleRuntimeControl(body, transport, false);
                case SmokeTestIntegrationContracts.RUNTIME_CONTROL ->
                        handleRuntimeControl(body, transport, true);
                case SmokeTestIntegrationContracts.RUNTIME_INSTANCES ->
                        handleRuntimeInstances(body, transport);
                case SmokeTestIntegrationContracts.RUNTIME_INSTANCE_CONTROL ->
                        handleRuntimeInstanceControl(body, transport);
                default -> publish(
                        transport,
                        -1,
                        responseOperation(operation),
                        rejected(body, "UNSUPPORTED_OPERATION", "Unsupported Integration operation."));
            }
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            log.warn("Rejected invalid Smoke Test Integration contract: {}", invalid.getMessage());
            publish(
                    transport,
                    -1,
                    responseOperation(operation),
                    rejected(body, "INVALID_CONTRACT", invalid.getMessage()));
        }
    }

    private static RefreshRequest throwUnsupportedDashboardRefresh() {
        throw new IllegalArgumentException("Main Dashboard multi-run does not support shared-page refresh.");
    }

    private static boolean hasMultiBatch(JsonObject body) {
        return body != null
                && body.has("multiBatchId")
                && body.get("multiBatchId").isJsonPrimitive()
                && !body.get("multiBatchId").getAsString().isBlank();
    }

    private void handleRuntimeControl(JsonObject body, Session transport, boolean mutation) {
        SmokeIntegrationAuthorization authorization = variables.authorize(body, transport);
        String requestId = requiredRuntimeText(body, "requestId", 200);
        String action = mutation ? requiredRuntimeText(body, "action", 12) : "STATUS";
        if (!java.util.Set.of("STATUS", "START", "STOP").contains(action)) {
            throw new IllegalArgumentException("Execution V2 runtime action is invalid.");
        }
        if (mutation) {
            synchronized (stateLock) {
                if (runtimeControlPending) {
                    throw new IllegalStateException("Execution V2 runtime control is already pending.");
                }
                if ("STOP".equals(action)) {
                    boolean activeV2 = activeRuns.values().stream().anyMatch(run ->
                            run.runtimeMode == RuntimeMode.TYPESCRIPT_PLAYWRIGHT_V2);
                    if (activeV2 || pendingStarts > 0) {
                        throw new IllegalStateException(
                                "Stop active V2 Integration runs before stopping the runtime.");
                    }
                }
                runtimeControlPending = true;
            }
        }
        Runnable task = () -> {
            JsonObject response;
            try {
                ExecutionV2RuntimeSupervisor.Status status;
                if ("START".equals(action)) {
                    status = runtimeSupervisor.start();
                } else if ("STOP".equals(action)) {
                    status = runtimeSupervisor.stop();
                } else {
                    status = runtimeSupervisor.status();
                }
                response = runtimeStatusResponse(requestId, authorization, status, true, "");
            } catch (RuntimeException failure) {
                executionTrace.warn(
                        "phase=V2_RUNTIME_CONTROL requestId={} action={} hb={} bot={} status=REFUSED failureType={}",
                        requestId, action, authorization.homeBankingId(), authorization.botJobId(),
                        failure.getClass().getSimpleName());
                response = runtimeStatusResponse(
                        requestId, authorization, runtimeSupervisor.status(), false,
                        safeMessage(failure, "Execution V2 runtime control was refused."));
            } finally {
                if (mutation) {
                    synchronized (stateLock) {
                        runtimeControlPending = false;
                    }
                }
            }
            publish(
                    transport,
                    authorization.homeBankingId(),
                    mutation
                            ? SmokeTestIntegrationContracts.RUNTIME_CONTROL_RESPONSE
                            : SmokeTestIntegrationContracts.RUNTIME_STATUS_RESPONSE,
                    response);
        };
        try {
            worker.execute(task);
        } catch (RejectedExecutionException busy) {
            if (mutation) {
                synchronized (stateLock) {
                    runtimeControlPending = false;
                }
            }
            throw new IllegalStateException("Execution V2 runtime control is busy.");
        }
    }

    private void handleRuntimeInstances(JsonObject body, Session transport) {
        SmokeIntegrationAuthorization authorization = variables.authorize(body, transport);
        String requestId = requiredRuntimeText(body, "requestId", 200);
        JsonObject response = new JsonObject();
        response.addProperty("ok", true);
        response.addProperty("contractVersion", SmokeTestIntegrationContracts.CONTRACT_VERSION);
        response.addProperty("requestId", requestId);
        response.addProperty("bindingEpoch", authorization.bindingEpoch());
        response.addProperty("workspaceEpoch", authorization.workspaceEpoch());
        response.addProperty("homeBankingId", authorization.homeBankingId());
        response.addProperty("botJobId", authorization.botJobId());
        com.google.gson.JsonArray instances = new com.google.gson.JsonArray();
        synchronized (stateLock) {
            for (Run run : activeRuns.values()) {
                JsonObject instance = new JsonObject();
                instance.addProperty("runId", run.runId);
                instance.addProperty("integrationEpoch", run.integrationEpoch);
                instance.addProperty("runtimeMode", run.runtimeMode.name());
                instance.addProperty("homeBankingId", run.authorization.homeBankingId());
                instance.addProperty("botJobId", run.authorization.botJobId());
                instance.addProperty("botJobName", run.authorization.botJobName());
                instance.addProperty("status", run.cancelled ? RunStatus.STOPPING.name() : run.status.name());
                instance.addProperty("stepPending", run.stepPending);
                instance.addProperty("terminalPending", run.terminalPending);
                instance.addProperty("currentInstructionId", run.currentInstructionId);
                instance.addProperty("currentRequestId", run.currentRequestId);
                instance.addProperty("startedAt", run.startedAt.toString());
                instance.addProperty("browserSession", run.runtimeMode == RuntimeMode.JAVA_V1
                        ? "SHARED_JAVA_PLAYWRIGHT" : run.v2Run.runId());
                instances.add(instance);
            }
        }
        response.add("instances", instances);
        executionTrace.info("phase=RUNTIME_INSTANCES requestId={} count={} hb={} bot={}",
                requestId, instances.size(), authorization.homeBankingId(), authorization.botJobId());
        publish(transport, authorization.homeBankingId(),
                SmokeTestIntegrationContracts.RUNTIME_INSTANCES_RESPONSE, response);
    }

    private void handleRuntimeInstanceControl(JsonObject body, Session transport) {
        SmokeIntegrationAuthorization authorization = variables.authorize(body, transport);
        String requestId = requiredRuntimeText(body, "requestId", 200);
        String runId = requiredRuntimeText(body, "runId", 200);
        String action = requiredRuntimeText(body, "action", 12);
        if (!java.util.Set.of("STOP", "KILL").contains(action)) {
            throw new IllegalArgumentException("Runtime instance action must be STOP or KILL.");
        }
        executionTrace.warn(
                "phase=RUNTIME_CONTROL_RECEIVED requestId={} runId={} action={} hb={} bot={}",
                requestId, runId, action, authorization.homeBankingId(), authorization.botJobId());
        Run run = resolveRun(runId, transport);
        if (run == null) {
            executionTrace.warn(
                    "phase=RUNTIME_CONTROL_REFUSED requestId={} runId={} action={} code=RUN_NOT_ACTIVE",
                    requestId, runId, action);
            publish(transport, -1, SmokeTestIntegrationContracts.RUNTIME_INSTANCE_CONTROL_RESPONSE,
                    rejected(requestId, runId, "RUN_NOT_ACTIVE", "The runtime instance is not active."));
            return;
        }
        run.cancelled = true;
        run.status = RunStatus.STOPPING;
        run.terminalPending = true;
        executionTrace.warn(
                "phase=RUNTIME_CONTROL_ADMITTED requestId={} runId={} action={} mode={} hb={} bot={} preserveBrowser={}",
                requestId, runId, action, run.runtimeMode,
                run.authorization.homeBankingId(), run.authorization.botJobId(),
                "STOP".equals(action));
        interruptActiveOperation(run, "RUNTIME_" + action);
        if (run.lease != null) browserOwnership.requestRelease();
        Runnable controlTask = () -> {
                JsonObject response;
                RuntimeException controlFailure = null;
                String browserDisposition = "PRESERVED";
                if ("KILL".equals(action)) {
                    executionTrace.warn(
                            "phase=RUNTIME_CONTROL_BROWSER_CLOSE_REQUESTED requestId={} runId={} mode={}",
                            requestId, runId, run.runtimeMode);
                    try {
                        if (run.runtimeMode == RuntimeMode.JAVA_V1) steps.forceStop();
                        else v2.closeBrowser(run.v2Run);
                        browserDisposition = "CLOSED";
                        executionTrace.warn(
                                "phase=RUNTIME_CONTROL_BROWSER_CLOSE_SETTLED requestId={} runId={} mode={}",
                                requestId, runId, run.runtimeMode);
                    } catch (RuntimeException closeFailure) {
                        controlFailure = closeFailure;
                        browserDisposition = "UNKNOWN";
                        executionTrace.error(
                                "phase=RUNTIME_CONTROL_BROWSER_CLOSE_FAILED requestId={} runId={} mode={} failureType={}",
                                requestId, runId, run.runtimeMode,
                                closeFailure.getClass().getSimpleName());
                    }
                }
                try {
                    synchronized (run.operationLock) {
                        terminate(run, RunStatus.STOPPED);
                    }
                } catch (RuntimeException terminationFailure) {
                    if (controlFailure == null) controlFailure = terminationFailure;
                    else controlFailure.addSuppressed(terminationFailure);
                }
                if (controlFailure == null) {
                    response = new JsonObject();
                    response.addProperty("ok", true);
                    response.addProperty("contractVersion", SmokeTestIntegrationContracts.CONTRACT_VERSION);
                    response.addProperty("requestId", requestId);
                    response.addProperty("runId", runId);
                    response.addProperty("status", "KILL".equals(action) ? "KILLED" : "STOPPED");
                    response.addProperty("browserDisposition", browserDisposition);
                    response.addProperty("message", "KILL".equals(action)
                            ? "The selected runtime instance was killed."
                            : "The selected runtime run was stopped.");
                    executionTrace.warn(
                            "phase=RUNTIME_CONTROL_SETTLED requestId={} runId={} action={} mode={} browserDisposition={} status={}",
                            requestId, runId, action, run.runtimeMode, browserDisposition,
                            response.get("status").getAsString());
                } else {
                    executionTrace.error(
                            "phase=RUNTIME_CONTROL_FAILED requestId={} runId={} action={} mode={} browserDisposition={} failureType={}",
                            requestId, runId, action, run.runtimeMode, browserDisposition,
                            controlFailure.getClass().getSimpleName());
                    response = rejected(requestId, runId, "RUNTIME_INSTANCE_CONTROL_FAILED",
                            safeMessage(controlFailure, "The runtime instance could not be stopped."));
                }
                publish(transport, run.authorization.homeBankingId(),
                        SmokeTestIntegrationContracts.RUNTIME_INSTANCE_CONTROL_RESPONSE, response);
            };
        try {
            worker.execute(controlTask);
        } catch (RejectedExecutionException busy) {
            executionTrace.warn(
                    "phase=RUNTIME_CONTROL_EXECUTOR_FALLBACK requestId={} runId={} action={} mode={}",
                    requestId, runId, action, run.runtimeMode);
            controlTask.run();
        }
    }

    private JsonObject runtimeStatusResponse(
            String requestId,
            SmokeIntegrationAuthorization authorization,
            ExecutionV2RuntimeSupervisor.Status status,
            boolean ok,
            String message) {
        JsonObject response = new JsonObject();
        response.addProperty("ok", ok);
        response.addProperty("contractVersion", SmokeTestIntegrationContracts.CONTRACT_VERSION);
        response.addProperty("requestId", requestId);
        response.addProperty("bindingEpoch", authorization.bindingEpoch());
        response.addProperty("workspaceEpoch", authorization.workspaceEpoch());
        response.addProperty("homeBankingId", authorization.homeBankingId());
        response.addProperty("botJobId", authorization.botJobId());
        response.addProperty("graphRevision", authorization.graphRevision());
        response.addProperty("state", status.state());
        response.addProperty("code", status.code());
        response.addProperty("port", status.port());
        response.addProperty("managed", status.managed());
        response.addProperty("message", message == null || message.isBlank()
                ? "Execution V2 runtime is " + status.state().toLowerCase(java.util.Locale.ROOT) + "."
                : message);
        return response;
    }

    private static String requiredRuntimeText(JsonObject body, String name, int maximum) {
        if (body == null || !body.has(name) || !body.get(name).isJsonPrimitive()
                || !body.getAsJsonPrimitive(name).isString()) {
            throw new IllegalArgumentException("Execution V2 runtime " + name + " is required.");
        }
        String value = body.get(name).getAsString().trim();
        if (value.isBlank() || value.length() > maximum) {
            throw new IllegalArgumentException("Execution V2 runtime " + name + " is invalid.");
        }
        return value;
    }

    /** Exact-session failure used by the WebSocket route before any operation can be dispatched. */
    public void rejectTransport(String operation, JsonObject body, Session transport) {
        publish(
                transport,
                -1,
                responseOperation(operation),
                rejected(
                        body,
                        "UNAUTHORIZED_TRANSPORT",
                        "Only the active Smoke Test page can use Integration."));
    }

    public void rejectLicense(String operation, JsonObject body, Session transport) {
        publish(
                transport,
                -1,
                responseOperation(operation),
                rejected(
                        body,
                        "LICENSE_REQUIRED",
                        "An active license is required for Smoke Test Integration."));
    }

    private void handleStart(StartRequest request, JsonObject rawBody, Session transport) {
        executionTrace.info(
                "phase=START_RECEIVED requestId={} mode={} hb={} bot={} pagePolicy={} excelMode={}",
                request.requestId(), request.runtimeMode(), request.homeBankingId(),
                request.botJobId(), request.pagePolicy(), request.excelMode());
        String fingerprint = gson.toJson(request);
        if (replayExisting(
                SmokeTestIntegrationContracts.START,
                request.requestId(),
                fingerprint,
                transport,
                request.homeBankingId())) {
            return;
        }
        PendingStart pendingStart = new PendingStart(request, transport);
        synchronized (stateLock) {
            boolean v1Requested = request.runtimeMode() == RuntimeMode.JAVA_V1;
            boolean v1Active = activeRuns.values().stream()
                    .anyMatch(run -> run.runtimeMode == RuntimeMode.JAVA_V1);
            boolean unavailable = refreshPending
                    || (v1Requested && (!activeRuns.isEmpty() || pendingStarts > 0))
                    || (!v1Requested && (v1Active
                            || pendingV1Starts > 0
                            || runtimeControlPending
                            || activeRuns.size() + pendingStarts >= MAX_ACTIVE_V2_RUNS));
            if (unavailable) {
                executionTrace.warn(
                        "phase=START_ADMISSION_REFUSED requestId={} mode={} hb={} bot={} code=INTEGRATION_ALREADY_ACTIVE activeRuns={} pendingStarts={}",
                        request.requestId(), request.runtimeMode(), request.homeBankingId(),
                        request.botJobId(), activeRuns.size(), pendingStarts);
                publish(
                        transport,
                        request.homeBankingId(),
                        SmokeTestIntegrationContracts.START_RESPONSE,
                        rejected(
                                rawBody,
                                "INTEGRATION_ALREADY_ACTIVE",
                                "Another Integration run already owns Playwright."));
                return;
            }
            pendingStarts++;
            if (v1Requested) pendingV1Starts++;
            pendingStartAttempts.put(pendingStart.key, pendingStart);
            executionTrace.info(
                    "phase=START_ADMITTED requestId={} mode={} hb={} bot={} activeRuns={} pendingStarts={}",
                    request.requestId(), request.runtimeMode(), request.homeBankingId(),
                    request.botJobId(), activeRuns.size(), pendingStarts);
        }
        submitOnce(
                SmokeTestIntegrationContracts.START,
                request.requestId(),
                fingerprint,
                transport,
                request.homeBankingId(),
                () -> start(request, rawBody, transport, pendingStart),
                () -> settlePendingStart(pendingStart));
    }

    private void handleRefresh(
            RefreshRequest request, JsonObject rawBody, Session transport) {
        executionTrace.info(
                "phase=REFRESH_RECEIVED requestId={} hb={} bot={}",
                request.requestId(), request.homeBankingId(), request.botJobId());
        String fingerprint = gson.toJson(request);
        if (replayExisting(
                SmokeTestIntegrationContracts.REFRESH,
                request.requestId(),
                fingerprint,
                transport,
                request.homeBankingId())) {
            return;
        }
        synchronized (stateLock) {
            if (!activeRuns.isEmpty() || pendingStarts > 0 || refreshPending) {
                publish(
                        transport,
                        request.homeBankingId(),
                        SmokeTestIntegrationContracts.REFRESH_RESPONSE,
                        rejected(
                                request.requestId(),
                                "",
                                "INTEGRATION_BUSY",
                                "Wait for the current Integration operation to finish."));
                return;
            }
            refreshPending = true;
        }
        submitOnce(
                SmokeTestIntegrationContracts.REFRESH,
                request.requestId(),
                fingerprint,
                transport,
                request.homeBankingId(),
                () -> refreshPage(request, rawBody, transport),
                () -> {
                    synchronized (stateLock) {
                        refreshPending = false;
                    }
                });
    }

    private JsonObject refreshPage(
            RefreshRequest request, JsonObject rawBody, Session transport) {
        BrowserLease lease = null;
        try {
            SmokeIntegrationAuthorization authorization = variables.authorize(rawBody, transport);
            String executionState = workspaces.executionState(
                    authorization.botJobId(), authorization.workspaceEpoch());
            if (BotJobDetailsWorkspaceRegistry.isExecutionActive(executionState)) {
                throw new IllegalStateException(
                        "TEST RUN already owns the Playwright browser.");
            }
            lease = browserOwnership.reserve();
            boolean refreshed = BotJobDetailsWorkspaceRegistry.getInstance()
                    .commitWorkspaceMutation(
                            authorization.botJobId(),
                            authorization.workspaceEpoch(),
                            browser::reloadCurrentPage);
            if (!refreshed) {
                throw new IllegalStateException(
                        "Open the Bot Job Playwright page before refreshing it.");
            }
            if (!variables.isCurrent(authorization, transport)) {
                throw new IllegalStateException(
                        "The Smoke Test target changed while the page was refreshing.");
            }
            JsonObject response = new JsonObject();
            response.addProperty("ok", true);
            response.addProperty("contractVersion", SmokeTestIntegrationContracts.CONTRACT_VERSION);
            response.addProperty("requestId", request.requestId());
            response.addProperty("bindingEpoch", authorization.bindingEpoch());
            response.addProperty("workspaceEpoch", authorization.workspaceEpoch());
            response.addProperty("homeBankingId", authorization.homeBankingId());
            response.addProperty("botJobId", authorization.botJobId());
            response.addProperty("graphRevision", authorization.graphRevision());
            response.addProperty("status", "REFRESHED");
            response.addProperty("code", "PLAYWRIGHT_PAGE_REFRESHED");
            response.addProperty("message", "The active Playwright web page was refreshed.");
            executionTrace.info(
                    "phase=REFRESH_SETTLED requestId={} hb={} bot={} status=REFRESHED",
                    request.requestId(), authorization.homeBankingId(), authorization.botJobId());
            return response;
        } catch (IllegalArgumentException | IllegalStateException refused) {
            executionTrace.warn(
                    "phase=REFRESH_SETTLED requestId={} hb={} bot={} status=REFUSED failureType={}",
                    request.requestId(), request.homeBankingId(), request.botJobId(),
                    refused.getClass().getSimpleName());
            log.warn("Smoke Test Playwright refresh refused: {}", refused.getMessage());
            return rejected(
                    request.requestId(),
                    "",
                    "PLAYWRIGHT_REFRESH_REFUSED",
                    safeMessage(refused, "The Playwright web page could not be refreshed."));
        } finally {
            if (lease != null) lease.close();
        }
    }

    private JsonObject start(
            StartRequest request,
            JsonObject rawBody,
            Session transport,
            PendingStart pendingStart) {
        BrowserLease lease = null;
        V2Run v2Run = null;
        pendingStart.workerThread = Thread.currentThread();
        try {
            pendingStart.requireActive();
            boolean dashboardMulti = hasMultiBatch(rawBody);
            MainDashboardMultiExecutionRegistry.PreparedJob prepared = dashboardMulti
                    ? MainDashboardMultiExecutionRegistry.getInstance().require(
                            rawBody.get("multiBatchId").getAsString(),
                            request.homeBankingId(),
                            request.botJobId(),
                            request.excelMode().name(),
                            transport)
                    : null;
            SmokeIntegrationAuthorization authorization = dashboardMulti
                    ? multiAuthorization(prepared, request)
                    : variables.authorize(rawBody, transport);
            pendingStart.requireActive();
            executionTrace.info(
                    "phase=START_AUTHORIZED requestId={} mode={} hb={} bot={} workspaceEpoch={} dashboardMulti={}",
                    request.requestId(), request.runtimeMode(), authorization.homeBankingId(),
                    authorization.botJobId(), authorization.workspaceEpoch(), dashboardMulti);
            if (!dashboardMulti) variables.requireSupportingWorkspacesReady(authorization, transport);
            pendingStart.requireActive();
            executionTrace.info(
                    "phase=SUPPORTING_WORKSPACES_READY requestId={} mode={} hb={} bot={}",
                    request.requestId(), request.runtimeMode(), authorization.homeBankingId(),
                    authorization.botJobId());
            Plan plan = dashboardMulti
                    ? prepared.plan()
                    : snapshots.load(
                            new Owner(authorization.homeBankingId(), authorization.botJobId()),
                            request.scope());
            pendingStart.requireActive();
            executionTrace.info(
                    "phase=PLAN_FROZEN requestId={} mode={} hb={} bot={} blocks={} instructions={} planRevision={}",
                    request.requestId(), request.runtimeMode(), authorization.homeBankingId(),
                    authorization.botJobId(), plan.blocks().size(), plan.instructions().size(),
                    plan.planRevision());
            IntegrationDataset dataset = dashboardMulti
                    ? prepared.dataset().integration()
                    : excel.freeze(authorization.botJobId(), request.excelMode().name());
            pendingStart.requireActive();
            executionTrace.info(
                    "phase=DATASET_FROZEN requestId={} mode={} hb={} bot={} rows={} datasetEpoch={} datasetRevision={}",
                    request.requestId(), request.runtimeMode(), authorization.homeBankingId(),
                    authorization.botJobId(), dataset.data().getNumberOfDataRows(),
                    dataset.datasetEpoch(), dataset.datasetRevision());
            if (dataset.homeBankingId() != authorization.homeBankingId()) {
                throw new IllegalStateException(
                        "The frozen Excel dataset belongs to another organization.");
            }
            // Re-read the relationship revision after both frozen snapshots were loaded. A
            // concurrent graph mutation must fail start instead of combining a stale React graph
            // assertion with newer SQL/Excel facts.
            if (!dashboardMulti) authorization = variables.authorize(rawBody, transport);
            pendingStart.requireActive();
            if (request.runtimeMode() == RuntimeMode.JAVA_V1) {
                executionTrace.info(
                        "phase=V1_BROWSER_RESERVING requestId={} hb={} bot={} pagePolicy={}",
                        request.requestId(), authorization.homeBankingId(),
                        authorization.botJobId(), request.pagePolicy());
                lease = browserOwnership.reserve();
                pendingStart.lease = lease;
                pendingStart.requireActive();
                String executionState = workspaces.executionState(
                        authorization.botJobId(), authorization.workspaceEpoch());
                if (BotJobDetailsWorkspaceRegistry.isExecutionActive(executionState)) {
                    throw new IllegalStateException(
                            "TEST RUN already owns the Playwright browser.");
                }
                boolean pageReady = workspaces.commitMutation(
                        authorization.botJobId(),
                        authorization.workspaceEpoch(),
                        () -> request.pagePolicy() == PagePolicy.PRESERVE_ACTIVE
                                ? browser.openPreservingCurrentPageAndWait(
                                        plan.environment().browserType(),
                                        plan.environment().url(),
                                        plan.environment().optionsConfig())
                                : browser.openSelectedPageAndWait(
                                        plan.environment().browserType(),
                                        plan.environment().url(),
                                        plan.environment().optionsConfig()));
                pendingStart.requireActive();
                if (!pageReady) {
                    throw new IllegalStateException(
                            request.pagePolicy() == PagePolicy.PRESERVE_ACTIVE
                                    ? "The current Playwright page could not be preserved or opened."
                                    : "The selected Bot Job Playwright page could not be opened and settled.");
                }
                executionTrace.info(
                        "phase=V1_BROWSER_READY requestId={} hb={} bot={}",
                        request.requestId(), authorization.homeBankingId(), authorization.botJobId());
            } else {
                if (request.pagePolicy() != PagePolicy.RELOAD_SELECTED) {
                    throw new IllegalArgumentException(
                            "Execution V2 requires a new isolated page at the selected Bot Job URL.");
                }
                executionTrace.info(
                        "phase=V2_RUNTIME_STARTING requestId={} hb={} bot={}",
                        request.requestId(), authorization.homeBankingId(), authorization.botJobId());
                v2Run = v2.start(authorization, plan, dataset.mode());
                pendingStart.v2Run = v2Run;
                pendingStart.requireActive();
                executionTrace.info(
                        "phase=V2_RUNTIME_READY requestId={} runId={} hb={} bot={}",
                        request.requestId(), v2Run.runId(), authorization.homeBankingId(),
                        authorization.botJobId());
            }
            if (!(dashboardMulti ? isCurrentDashboardTransport(transport)
                    : variables.isCurrent(authorization, transport))) {
                throw new IllegalStateException(
                        "The Smoke Test target changed while Integration was starting.");
            }
            pendingStart.requireActive();

            long integrationEpoch = integrationEpochs.incrementAndGet();
            Run run = new Run(
                    v2Run == null ? UUID.randomUUID().toString() : v2Run.runId(),
                    integrationEpoch,
                    transport,
                    authorization,
                    plan,
                    dataset,
                    request.durableRuntimeWrites(),
                    new RunVariables(
                            authorization.homeBankingId(),
                            authorization.botJobId(),
                            request.durableRuntimeWrites()),
                    request.runtimeMode(),
                    request.pagePolicy(),
                    lease,
                    v2Run,
                    dashboardMulti);
            synchronized (stateLock) {
                boolean v1Run = request.runtimeMode() == RuntimeMode.JAVA_V1;
                boolean v1Active = activeRuns.values().stream()
                        .anyMatch(existing -> existing.runtimeMode == RuntimeMode.JAVA_V1);
                if (!isRegisteredTransport(transport)
                        || activeRuns.containsKey(run.runId)
                        || (v1Run && !activeRuns.isEmpty())
                        || (!v1Run && (v1Active || activeRuns.size() >= MAX_ACTIVE_V2_RUNS))) {
                    throw new IllegalStateException(
                            "The Smoke Test page disconnected while Integration was starting.");
                }
                pendingStart.requireActive();
                activeRuns.put(run.runId, run);
                lease = null;
                v2Run = null;
                pendingStart.lease = null;
                pendingStart.v2Run = null;
            }
            executionTrace.info(
                    "phase=RUN_REGISTERED requestId={} runId={} integrationEpoch={} mode={} hb={} bot={}",
                    request.requestId(), run.runId, run.integrationEpoch, run.runtimeMode,
                    authorization.homeBankingId(), authorization.botJobId());
            StartResponse response = new StartResponse(
                    SmokeTestIntegrationContracts.CONTRACT_VERSION,
                    request.requestId(),
                    run.runId,
                    run.integrationEpoch,
                    RunStatus.STARTED,
                    authorization.bindingEpoch(),
                    authorization.workspaceEpoch(),
                    authorization.homeBankingId(),
                    authorization.botJobId(),
                    authorization.graphRevision(),
                    plan.planRevision(),
                    dataset.mode(),
                    dataset.datasetEpoch(),
                    dataset.datasetRevision(),
                    dataset.contentRevision(),
                    dataset.data().getNumberOfDataRows(),
                    request.runtimeMode().name(),
                    request.pagePolicy().name(),
                    request.durableRuntimeWrites(),
                    run.runtimeSnapshot,
                    plan.blocks().size(),
                    plan.instructions().size(),
                    "INTEGRATION_STARTED",
                    request.pagePolicy() == PagePolicy.PRESERVE_ACTIVE
                            ? "Integration continues from the current Playwright page."
                            : "Integration owns the reloaded Bot Job Playwright page.");
            return successful(response);
        } catch (IllegalArgumentException | IllegalStateException refused) {
            executionTrace.warn(
                    "phase=START_REFUSED requestId={} mode={} hb={} bot={} failureType={}",
                    request.requestId(), request.runtimeMode(), request.homeBankingId(),
                    request.botJobId(), refused.getClass().getSimpleName());
            log.warn("Smoke Test Integration start refused: {}", refused.getMessage());
            return rejected(
                    rawBody,
                    "INTEGRATION_START_REFUSED",
                    safeMessage(refused, "Integration could not be started."));
        } catch (java.sql.SQLException persistenceFailure) {
            executionTrace.error(
                    "phase=START_FAILED requestId={} mode={} hb={} bot={} code=INTEGRATION_PLAN_UNAVAILABLE failureType={}",
                    request.requestId(), request.runtimeMode(), request.homeBankingId(),
                    request.botJobId(), persistenceFailure.getClass().getSimpleName());
            log.error("Unable to load the authoritative Smoke Test Integration plan", persistenceFailure);
            return rejected(
                    rawBody,
                    "INTEGRATION_PLAN_UNAVAILABLE",
                    "The Integration plan could not be loaded from the database.");
        } catch (RuntimeException failure) {
            executionTrace.error(
                    "phase=START_FAILED requestId={} mode={} hb={} bot={} code=INTEGRATION_START_FAILED failureType={}",
                    request.requestId(), request.runtimeMode(), request.homeBankingId(),
                    request.botJobId(), failure.getClass().getSimpleName());
            log.error("Unable to start Smoke Test Integration", failure);
            return rejected(
                    rawBody,
                    "INTEGRATION_START_FAILED",
                    "Integration could not start the Playwright page.");
        } finally {
            pendingStart.workerThread = null;
            if (lease != null) lease.close();
            if (v2Run != null) closeV2AfterFailedStart(v2Run);
        }
    }

    private static SmokeIntegrationAuthorization multiAuthorization(
            MainDashboardMultiExecutionRegistry.PreparedJob prepared,
            StartRequest request) {
        if (prepared == null) {
            throw new IllegalArgumentException("The multi-run preparation is unavailable.");
        }
        if (request.runtimeMode() != RuntimeMode.TYPESCRIPT_PLAYWRIGHT_V2
                || request.pagePolicy() != PagePolicy.RELOAD_SELECTED
                || request.durableRuntimeWrites()) {
            throw new IllegalArgumentException(
                    "Main Dashboard multi-run requires isolated V2, reload-selected, and run-local variables.");
        }
        JsonObject snapshot = prepared.workspaceSnapshot();
        String bindingEpoch = snapshot.get("bindingEpoch").getAsString();
        long workspaceEpoch = snapshot.get("workspaceEpoch").getAsLong();
        String graphRevision = snapshot.get("graphRevision").getAsString();
        if (!bindingEpoch.equals(request.bindingEpoch())
                || workspaceEpoch != request.workspaceEpoch()
                || !graphRevision.equalsIgnoreCase(request.graphRevision())) {
            throw new IllegalArgumentException("The multi-run React program no longer matches its preparation.");
        }
        List<Integer> requestedBlocks = request.scope().blockIds().stream().sorted().toList();
        List<Integer> preparedBlocks = prepared.plan().blocks().stream()
                .filter(com.allinweb.ch.facade.execution
                        .SmokeTestIntegrationSnapshotRepository.BlockSnapshot::active)
                .map(com.allinweb.ch.facade.execution
                        .SmokeTestIntegrationSnapshotRepository.BlockSnapshot::id)
                .sorted()
                .toList();
        if (!requestedBlocks.equals(preparedBlocks)) {
            throw new IllegalArgumentException(
                    "Main Dashboard multi-run must execute the complete frozen active program.");
        }
        return new SmokeIntegrationAuthorization(
                bindingEpoch,
                workspaceEpoch,
                prepared.plan().owner().botJobId(),
                prepared.plan().owner().homeBankingId(),
                prepared.plan().environment().botJobName(),
                prepared.plan().environment().organizationName(),
                graphRevision);
    }

    private static boolean isCurrentDashboardTransport(Session transport) {
        return transport != null
                && transport.isOpen()
                && WebSocketSessionManager.getSession("mainDashboard") == transport;
    }

    private boolean isCurrentRunAuthority(Run run, Session transport) {
        return run.dashboardMulti
                ? isCurrentDashboardTransport(transport)
                : variables.isCurrent(run.authorization, transport);
    }

    private void handleStep(StepRequest request, Session transport) {
        String fingerprint = gson.toJson(request);
        if (replayExisting(
                SmokeTestIntegrationContracts.STEP,
                request.requestId(),
                fingerprint,
                transport,
                -1)) {
            return;
        }
        Run run = resolveRun(request.runId(), transport);
        synchronized (stateLock) {
            if (run == null || activeRuns.get(run.runId) != run) {
                publish(
                        transport,
                        -1,
                        SmokeTestIntegrationContracts.STEP_RESPONSE,
                        rejected(request.requestId(), request.runId(), "RUN_NOT_ACTIVE",
                                "The Integration run is not active."));
                return;
            }
            SequenceResult previous = run.sequenceResults.get(request.sequence());
            if (previous != null) {
                if (!previous.matches(request)) {
                    publish(
                            transport,
                            run.authorization.homeBankingId(),
                            SmokeTestIntegrationContracts.STEP_RESPONSE,
                            rejected(request.requestId(), request.runId(), "SEQUENCE_CONFLICT",
                                    "This Integration sequence was already used by another step."));
                    return;
                }
                JsonObject replay = previous.response.deepCopy();
                replay.addProperty("requestId", request.requestId());
                replay.addProperty("replayed", true);
                publish(
                        transport,
                        run.authorization.homeBankingId(),
                        SmokeTestIntegrationContracts.STEP_RESPONSE,
                        replay);
                return;
            }
            if (request.sequence() <= run.lastSequence) {
                publish(
                        transport,
                        run.authorization.homeBankingId(),
                        SmokeTestIntegrationContracts.STEP_RESPONSE,
                        rejected(request.requestId(), request.runId(), "SEQUENCE_EXPIRED",
                                "This old Integration sequence is no longer replayable."));
                return;
            }
            if (request.sequence() != run.lastSequence + 1L) {
                publish(
                        transport,
                        run.authorization.homeBankingId(),
                        SmokeTestIntegrationContracts.STEP_RESPONSE,
                        rejected(request.requestId(), request.runId(), "SEQUENCE_OUT_OF_ORDER",
                                "Integration steps must be sent in sequence."));
                return;
            }
            if (run.sequenceResults.values().stream()
                    .anyMatch(result -> result.recoveryPending)) {
                publish(
                        transport,
                        run.authorization.homeBankingId(),
                        SmokeTestIntegrationContracts.STEP_RESPONSE,
                        rejected(request.requestId(), request.runId(), "RECOVERY_PENDING",
                                "Resolve or bypass the pending locator recovery before continuing."));
                return;
            }
            if (run.stepPending || run.terminalPending || run.cancelled) {
                publish(
                        transport,
                        run.authorization.homeBankingId(),
                        SmokeTestIntegrationContracts.STEP_RESPONSE,
                        rejected(request.requestId(), request.runId(), "INTEGRATION_BUSY",
                                "Wait for the current Integration operation to finish."));
                return;
            }
            run.stepPending = true;
        }
        submitOnce(
                SmokeTestIntegrationContracts.STEP,
                request.requestId(),
                fingerprint,
                transport,
                run.authorization.homeBankingId(),
                () -> step(run, request),
                () -> {
                    synchronized (stateLock) {
                        run.stepPending = false;
                    }
                });
    }

    private JsonObject step(Run run, StepRequest request) {
        synchronized (run.operationLock) {
            return stepLocked(run, request);
        }
    }

    private JsonObject stepLocked(Run run, StepRequest request) {
        run.activeOperationThread = Thread.currentThread();
        run.currentInstructionId = request.instructionId();
        run.currentRequestId = request.requestId();
        try {
        executionTrace.info(
                "phase=STEP_STARTED requestId={} runId={} sequence={} instructionId={} mode={} hb={} bot={}",
                request.requestId(), run.runId, request.sequence(), request.instructionId(),
                run.runtimeMode, run.authorization.homeBankingId(), run.authorization.botJobId());
        if (run.cancelled) {
            return stoppedStep(run, request, "Integration stop was requested.");
        }
        if (!isCurrentRunAuthority(run, run.responseTransport)) {
            JsonObject failed = stepResponse(
                    run,
                    request,
                    new Outcome(
                            StepStatus.FAILED,
                            SmokeTestIntegrationContracts.StepDisposition.UNSUPPORTED,
                            "BINDING_RETIRED",
                            "The Smoke Test page changed or disconnected.",
                            null,
                            null),
                    false);
            recordStep(run, request, failed, StepStatus.FAILED);
            terminate(run, RunStatus.FAILED);
            return failed;
        }

        Outcome outcome = run.runtimeMode == RuntimeMode.TYPESCRIPT_PLAYWRIGHT_V2
                ? v2.execute(
                        run.v2Run,
                        run.plan,
                        run.dataset,
                        request.sequence(),
                        request.instructionId(),
                        request.excelRowIndex(),
                        run.variables)
                : steps.execute(
                        run.plan,
                        run.dataset,
                        request.instructionId(),
                        request.excelRowIndex(),
                        run.variables);
        if (run.runtimeMode == RuntimeMode.JAVA_V1) {
            outcome = steps.prepareRecovery(
                    run.runId,
                    run.plan,
                    run.dataset,
                    request.instructionId(),
                    request.excelRowIndex(),
                    run.variables,
                    outcome);
        }
        if (outcome.recovery() != null && !request.recoveryVerificationEnabled()) {
            cancelRecovery(run, request.instructionId(), "VERIFICATION_DISABLED");
            outcome = new Outcome(
                    StepStatus.SKIPPED,
                    SmokeTestIntegrationContracts.StepDisposition.PHYSICAL,
                    "RECOVERY_BYPASSED",
                    "Locator recovery verification is off; the unresolved instruction was bypassed.",
                    null,
                    null);
        }
        if (run.cancelled) {
            return stoppedStep(run, request, "Integration stop interrupted the current action.");
        }
        JsonObject response = stepResponse(run, request, outcome, false);
        recordStep(run, request, response, outcome.status());
        executionTrace.info(
                "phase=STEP_SETTLED requestId={} runId={} sequence={} instructionId={} mode={} status={} code={} recoveryPending={}",
                request.requestId(), run.runId, request.sequence(), request.instructionId(),
                run.runtimeMode, outcome.status(), outcome.code(), outcome.recovery() != null);
        return response;
        } finally {
            if (run.activeOperationThread == Thread.currentThread()) run.activeOperationThread = null;
            run.currentInstructionId = 0;
            run.currentRequestId = "";
        }
    }

    private void handleRecovery(RecoveryRequest request, Session transport) {
        String fingerprint = gson.toJson(request);
        if (replayExisting(
                SmokeTestIntegrationContracts.RECOVER,
                request.requestId(),
                fingerprint,
                transport,
                -1)) return;
        Run run = resolveRun(request.runId(), transport);
        synchronized (stateLock) {
            SequenceResult previous = run == null ? null : run.sequenceResults.get(request.sequence());
            if (run == null
                    || activeRuns.get(run.runId) != run
                    || previous == null
                    || previous.instructionId != request.instructionId()
                    || !previous.recoveryPending) {
                publish(
                        transport,
                        run == null ? -1 : run.authorization.homeBankingId(),
                        SmokeTestIntegrationContracts.RECOVER_RESPONSE,
                        rejected(request.requestId(), request.runId(), "RECOVERY_NOT_PENDING",
                                "This instruction no longer has a pending locator recovery."));
                return;
            }
            if (run.stepPending || run.terminalPending || run.cancelled) {
                publish(
                        transport,
                        run.authorization.homeBankingId(),
                        SmokeTestIntegrationContracts.RECOVER_RESPONSE,
                        rejected(request.requestId(), request.runId(), "INTEGRATION_BUSY",
                                "Wait for the current Integration operation to finish."));
                return;
            }
            run.stepPending = true;
        }
        submitOnce(
                SmokeTestIntegrationContracts.RECOVER,
                request.requestId(),
                fingerprint,
                transport,
                run.authorization.homeBankingId(),
                () -> recover(run, request),
                () -> {
                    synchronized (stateLock) { run.stepPending = false; }
                });
    }

    private JsonObject recover(Run run, RecoveryRequest request) {
        synchronized (run.operationLock) {
            executionTrace.info(
                    "phase=RECOVERY_DECISION requestId={} runId={} sequence={} instructionId={} mode={} decision={}",
                    request.requestId(), run.runId, request.sequence(), request.instructionId(),
                    run.runtimeMode, request.decision());
            if (run.cancelled) {
                return rejected(request.requestId(), request.runId(), "INTEGRATION_STOPPING",
                        "Integration stop was requested.");
            }
            if (request.decision() == RecoveryDecision.CANCEL
                    || request.decision() == RecoveryDecision.BYPASS) {
                cancelRecovery(run, request.instructionId(), request.decision().name());
                boolean bypass = request.decision() == RecoveryDecision.BYPASS;
                synchronized (stateLock) {
                    SequenceResult previous = run.sequenceResults.get(request.sequence());
                    if (previous != null && previous.recoveryPending) {
                        JsonObject settled = previous.response.deepCopy();
                        settled.remove("recovery");
                        settled.addProperty("status", bypass
                                ? StepStatus.SKIPPED.name()
                                : previous.status.name());
                        settled.addProperty("code", bypass
                                ? "RECOVERY_BYPASSED"
                                : "RECOVERY_CANCELLED");
                        settled.addProperty("message", bypass
                                ? "The unresolved instruction was bypassed by the user."
                                : "Locator recovery was cancelled.");
                        if (bypass) {
                            decrement(run, previous.status);
                            run.skipped++;
                        }
                        run.sequenceResults.put(
                                request.sequence(),
                                new SequenceResult(
                                        previous.instructionId,
                                        previous.excelRowIndex,
                                        settled,
                                        bypass ? StepStatus.SKIPPED : previous.status,
                                        false,
                                        previous.recoveryVerificationEnabled));
                    }
                }
                if (bypass) {
                    executionTrace.info(
                            "phase=RECOVERY_SETTLED requestId={} runId={} instructionId={} mode={} status=BYPASSED code=RECOVERY_BYPASSED",
                            request.requestId(), run.runId, request.instructionId(), run.runtimeMode);
                    return recoveryResponse(run, request, null, "BYPASSED",
                            "The unresolved instruction was bypassed; execution may continue.");
                }
                executionTrace.info(
                        "phase=RECOVERY_SETTLED requestId={} runId={} instructionId={} mode={} status=CANCELLED code=RECOVERY_CANCELLED",
                        request.requestId(), run.runId, request.instructionId(), run.runtimeMode);
                return recoveryResponse(run, request, null, "CANCELLED",
                        "Locator recovery was cancelled.");
            }
            Outcome outcome = run.runtimeMode == RuntimeMode.TYPESCRIPT_PLAYWRIGHT_V2
                    ? v2.recover(
                            run.v2Run,
                            request.instructionId(),
                            request.recoveryCandidateId(),
                            request.decision() == RecoveryDecision.USE_AND_SAVE)
                    : steps.recover(
                            run.runId,
                            request.instructionId(),
                            request.recoveryCandidateId(),
                            request.decision() == RecoveryDecision.USE_AND_SAVE,
                            run.variables);
            if (outcome.status() == StepStatus.FAILED) {
                executionTrace.warn(
                        "phase=RECOVERY_SETTLED requestId={} runId={} instructionId={} mode={} status=FAILED code={}",
                        request.requestId(), run.runId, request.instructionId(),
                        run.runtimeMode, outcome.code());
                return recoveryResponse(run, request, outcome, "FAILED", outcome.message());
            }
            synchronized (stateLock) {
                SequenceResult previous = run.sequenceResults.get(request.sequence());
                if (previous == null || !previous.recoveryPending) {
                    return rejected(request.requestId(), request.runId(), "RECOVERY_NOT_PENDING",
                            "This instruction no longer has a pending locator recovery.");
                }
                decrement(run, previous.status);
                if (outcome.status() == StepStatus.WARNING) run.warnings++;
                else run.passed++;
                JsonObject replacement = stepResponse(
                        run,
                        new StepRequest(
                                SmokeTestIntegrationContracts.CONTRACT_VERSION,
                                request.requestId(),
                                request.runId(),
                                request.sequence(),
                                request.instructionId(),
                                previous.excelRowIndex,
                                true),
                        outcome,
                        false);
                run.sequenceResults.put(
                        request.sequence(),
                        new SequenceResult(
                                request.instructionId(),
                                previous.excelRowIndex,
                                replacement.deepCopy(),
                                outcome.status(),
                                false,
                                previous.recoveryVerificationEnabled));
            }
            executionTrace.info(
                    "phase=RECOVERY_SETTLED requestId={} runId={} instructionId={} mode={} status={} code={} saveRequested={}",
                    request.requestId(), run.runId, request.instructionId(), run.runtimeMode,
                    outcome.status(), outcome.code(),
                    request.decision() == RecoveryDecision.USE_AND_SAVE);
            return recoveryResponse(run, request, outcome, "COMPLETED",
                    outcome.status() == StepStatus.WARNING
                            ? outcome.message()
                            : request.decision() == RecoveryDecision.USE_AND_SAVE
                            ? "The selected locator was used and saved."
                            : "The selected locator was used once.");
        }
    }

    private JsonObject recoveryResponse(
            Run run,
            RecoveryRequest request,
            Outcome outcome,
            String status,
            String message) {
        JsonObject response = new JsonObject();
        response.addProperty("contractVersion", SmokeTestIntegrationContracts.CONTRACT_VERSION);
        response.addProperty("requestId", request.requestId());
        response.addProperty("runId", run.runId);
        response.addProperty("integrationEpoch", run.integrationEpoch);
        response.addProperty("sequence", request.sequence());
        response.addProperty("instructionId", request.instructionId());
        response.addProperty("status", status);
        response.addProperty("message", message);
        response.addProperty("locatorSaved", request.decision() == RecoveryDecision.USE_AND_SAVE
                && "COMPLETED".equals(status)
                && (outcome == null
                || !"RECOVERY_ACTION_COMPLETED_SAVE_FAILED".equals(outcome.code())));
        response.addProperty("ok", "COMPLETED".equals(status)
                || "BYPASSED".equals(status)
                || "CANCELLED".equals(status));
        if (outcome != null) response.addProperty("code", outcome.code());
        else if ("BYPASSED".equals(status)) response.addProperty("code", "RECOVERY_BYPASSED");
        else if ("CANCELLED".equals(status)) response.addProperty("code", "RECOVERY_CANCELLED");
        return response;
    }

    private void cancelRecovery(Run run, int instructionId, String reason) {
        if (run.runtimeMode == RuntimeMode.TYPESCRIPT_PLAYWRIGHT_V2) {
            v2.cancelRecovery(run.v2Run, instructionId);
        } else {
            steps.cancelRecovery(run.runId, instructionId, reason);
        }
    }

    private static void decrement(Run run, StepStatus status) {
        switch (status) {
            case PASSED -> run.passed = Math.max(0, run.passed - 1);
            case WARNING -> run.warnings = Math.max(0, run.warnings - 1);
            case FAILED -> run.failed = Math.max(0, run.failed - 1);
            case SKIPPED -> run.skipped = Math.max(0, run.skipped - 1);
        }
    }

    private void handleExcelWrite(ExcelWriteRequest request, Session transport) {
        executionTrace.info(
                "phase=EXCEL_WRITE_RECEIVED requestId={} runId={}",
                request.requestId(), request.runId());
        String fingerprint = gson.toJson(request);
        if (replayExisting(SmokeTestIntegrationContracts.EXCEL_WRITE, request.requestId(), fingerprint, transport, -1)) return;
        Run run = resolveRun(request.runId(), transport);
        synchronized (stateLock) {
            if (run == null || activeRuns.get(run.runId) != run) {
                publish(transport, -1, SmokeTestIntegrationContracts.EXCEL_WRITE_RESPONSE,
                        rejected(request.requestId(), request.runId(), "RUN_NOT_ACTIVE", "The Integration run is not active."));
                return;
            }
            if (run.stepPending || run.terminalPending || run.cancelled) {
                publish(transport, run.authorization.homeBankingId(), SmokeTestIntegrationContracts.EXCEL_WRITE_RESPONSE,
                        rejected(request.requestId(), request.runId(), "INTEGRATION_BUSY", "Wait for the current Integration operation to finish."));
                return;
            }
            run.stepPending = true;
        }
        submitOnce(SmokeTestIntegrationContracts.EXCEL_WRITE, request.requestId(), fingerprint, transport,
                run.authorization.homeBankingId(), () -> saveExcelWrite(run, request), () -> {
                    synchronized (stateLock) { run.stepPending = false; }
                });
    }

    private JsonObject saveExcelWrite(Run run, ExcelWriteRequest request) {
        synchronized (run.operationLock) {
            return saveExcelWriteLocked(run, request);
        }
    }

    private JsonObject saveExcelWriteLocked(Run run, ExcelWriteRequest request) {
        try {
            if (!isCurrentRunAuthority(run, run.responseTransport)) {
                throw new IllegalStateException("The Smoke Test page changed before ExcelWrite could save.");
            }
            SmokeTestIntegrationExcelWriteService.Result saved = run.dashboardMulti
                    ? excelWrites.save(run.plan, request)
                    : workspaces.commitMutation(
                            run.authorization.botJobId(), run.authorization.workspaceEpoch(),
                            () -> {
                                try { return excelWrites.save(run.plan, request); }
                                catch (Exception failure) { throw new ExcelWriteFailure(failure); }
                            });
            JsonObject response = new JsonObject();
            response.addProperty("ok", true);
            response.addProperty("contractVersion", SmokeTestIntegrationContracts.CONTRACT_VERSION);
            response.addProperty("requestId", request.requestId());
            response.addProperty("runId", request.runId());
            response.addProperty("sha256", saved.sha256());
            response.addProperty("revision", saved.revision());
            response.addProperty("message", saved.message());
            executionTrace.info(
                    "phase=EXCEL_WRITE_SETTLED requestId={} runId={} status=SAVED revision={}",
                    request.requestId(), request.runId(), saved.revision());
            return response;
        } catch (ExcelWriteFailure wrapped) {
            executionTrace.warn(
                    "phase=EXCEL_WRITE_SETTLED requestId={} runId={} status=REFUSED failureType={}",
                    request.requestId(), request.runId(), wrapped.getCause().getClass().getSimpleName());
            log.warn("Smoke Integration ExcelWrite refused: {}", safeMessage(wrapped.getCause(), "save failed"));
            return rejected(request.requestId(), request.runId(), "EXCEL_WRITE_REFUSED",
                    safeMessage(wrapped.getCause(), "ExcelWrite could not save the finalized artifact."));
        } catch (IllegalArgumentException | IllegalStateException refused) {
            executionTrace.warn(
                    "phase=EXCEL_WRITE_SETTLED requestId={} runId={} status=REFUSED failureType={}",
                    request.requestId(), request.runId(), refused.getClass().getSimpleName());
            return rejected(request.requestId(), request.runId(), "EXCEL_WRITE_REFUSED",
                    safeMessage(refused, "ExcelWrite could not save the finalized artifact."));
        } catch (Exception failure) {
            executionTrace.error(
                    "phase=EXCEL_WRITE_SETTLED requestId={} runId={} status=FAILED failureType={}",
                    request.requestId(), request.runId(), failure.getClass().getSimpleName());
            log.warn("Smoke Integration ExcelWrite failed", failure);
            return rejected(request.requestId(), request.runId(), "EXCEL_WRITE_FAILED",
                    "ExcelWrite could not save the finalized artifact.");
        }
    }

    private void handleStop(StopRequest request, Session transport) {
        executionTrace.info(
                "phase=STOP_RECEIVED requestId={} runId={}",
                request.requestId(), request.runId());
        String fingerprint = gson.toJson(request);
        if (replayExisting(
                SmokeTestIntegrationContracts.STOP,
                request.requestId(),
                fingerprint,
                transport,
                -1)) {
            return;
        }
        Run run = resolveRun(request.runId(), transport);
        synchronized (stateLock) {
            if (run == null || activeRuns.get(run.runId) != run) {
                publish(
                        transport,
                        -1,
                        SmokeTestIntegrationContracts.STOP_RESPONSE,
                        rejected(request.requestId(), request.runId(), "RUN_NOT_ACTIVE",
                                "The Integration run is not active."));
                return;
            }
            run.cancelled = true;
            run.status = RunStatus.STOPPING;
            if (run.lease != null) browserOwnership.requestRelease();
            // A second correlated Stop may arrive after a reconnect/message-buffer generation
            // change. Serialize it behind the accepted Stop and return the same terminal outcome;
            // terminate() and the browser lease are independently idempotent.
            run.terminalPending = true;
            executionTrace.info(
                    "phase=STOP_ADMITTED requestId={} runId={} mode={} hb={} bot={}",
                    request.requestId(), run.runId, run.runtimeMode,
                    run.authorization.homeBankingId(), run.authorization.botJobId());
        }
        // V2 interruption performs an IPC call. Never hold the service state lock while waiting
        // on a runtime boundary; otherwise inventory/control/reconnect requests can deadlock
        // behind a slow or unavailable runtime.
        interruptActiveOperation(run, "STOP_REQUESTED");
        submitOnce(
                SmokeTestIntegrationContracts.STOP,
                request.requestId(),
                fingerprint,
                transport,
                run.authorization.homeBankingId(),
                () -> stop(run, request),
                () -> {
                    synchronized (stateLock) {
                        run.terminalPending = false;
                    }
                });
    }

    private void handleForceStop(
            ForceStopRequest request, JsonObject rawBody, Session transport) {
        SmokeIntegrationAuthorization authorization =
                variables.authorizeEmergencyStop(rawBody, transport);
        List<PendingStart> starts;
        List<Run> runs;
        synchronized (stateLock) {
            starts = pendingStartAttempts.values().stream()
                    .filter(start -> start.matches(authorization))
                    .toList();
            runs = activeRuns.values().stream()
                    .filter(run -> !run.dashboardMulti
                            && sameOwner(run.authorization, authorization))
                    .toList();
            starts.forEach(start -> {
                start.cancelled.set(true);
                settlePendingStartLocked(start);
            });
            runs.forEach(run -> {
                run.cancelled = true;
                run.status = RunStatus.STOPPING;
                run.terminalPending = true;
                if (run.lease != null) browserOwnership.requestRelease();
            });
        }

        boolean interruptPendingV1 = starts.stream().anyMatch(start ->
                start.request.runtimeMode() == RuntimeMode.JAVA_V1 && start.lease != null);
        for (PendingStart start : starts) {
            V2Run pendingV2 = start.v2Run;
            if (pendingV2 != null) safelyInterruptV2(pendingV2, "pending-start-force-stop");
            Thread thread = start.workerThread;
            if (thread != null) thread.interrupt();
        }
        for (Run run : runs) {
            interruptActiveOperation(run, "FORCE_STOP_REQUESTED");
        }
        // Emergency STOP is browser-preserving. Active V1 runs were interrupted above; a pending
        // V1 start has no Run yet, so interrupt its shared Playwright operation explicitly.
        if (interruptPendingV1) steps.interrupt();
        for (Run run : runs) {
            try {
                worker.execute(() -> terminateSafely(run, RunStatus.STOPPED, "force-stop"));
            } catch (RejectedExecutionException shutdown) {
                terminateSafely(run, RunStatus.STOPPED, "force-stop-after-shutdown");
            }
        }

        JsonObject response = new JsonObject();
        response.addProperty("ok", true);
        response.addProperty("contractVersion", SmokeTestIntegrationContracts.CONTRACT_VERSION);
        response.addProperty("requestId", request.requestId());
        response.addProperty("bindingEpoch", authorization.bindingEpoch());
        response.addProperty("workspaceEpoch", authorization.workspaceEpoch());
        response.addProperty("homeBankingId", authorization.homeBankingId());
        response.addProperty("botJobId", authorization.botJobId());
        response.addProperty("graphRevision", authorization.graphRevision());
        response.addProperty("status", starts.isEmpty() && runs.isEmpty() ? "IDLE" : "STOP_REQUESTED");
        response.addProperty("pendingStartsCancelled", starts.size());
        response.addProperty("activeRunsInterrupted", runs.size());
        response.addProperty("browserDisposition", "PRESERVED");
        response.addProperty("message", starts.isEmpty() && runs.isEmpty()
                ? "No Integration startup or run is active for this Bot Job."
                : "Emergency Stop interrupted the current Integration startup or run.");
        executionTrace.warn(
                "phase=FORCE_STOP_SETTLED requestId={} hb={} bot={} pendingStartsCancelled={} activeRunsInterrupted={} pendingV1Interrupted={} browserDisposition=PRESERVED",
                request.requestId(), authorization.homeBankingId(), authorization.botJobId(),
                starts.size(), runs.size(), interruptPendingV1);
        publish(transport, authorization.homeBankingId(),
                SmokeTestIntegrationContracts.FORCE_STOP_RESPONSE, response);
    }

    private void safelyInterruptV2(V2Run run, String context) {
        try {
            v2.interrupt(run);
        } catch (RuntimeException failure) {
            executionTrace.warn(
                    "phase=V2_INTERRUPT_FAILED runId={} context={} failureType={}",
                    run.runId(), context, failure.getClass().getSimpleName());
        }
    }

    private static boolean sameOwner(
            SmokeIntegrationAuthorization first, SmokeIntegrationAuthorization second) {
        return first.homeBankingId() == second.homeBankingId()
                && first.botJobId() == second.botJobId();
    }

    private void settlePendingStart(PendingStart pendingStart) {
        synchronized (stateLock) {
            settlePendingStartLocked(pendingStart);
        }
    }

    private void settlePendingStartLocked(PendingStart pendingStart) {
        if (!pendingStart.settled.compareAndSet(false, true)) return;
        pendingStartAttempts.remove(pendingStart.key, pendingStart);
        pendingStarts--;
        if (pendingStart.request.runtimeMode() == RuntimeMode.JAVA_V1) pendingV1Starts--;
    }

    private JsonObject stop(Run run, StopRequest request) {
        synchronized (run.operationLock) {
            try {
                TerminalResponse response = terminalResponse(
                        run,
                        request.requestId(),
                        RunStatus.STOPPED,
                        "INTEGRATION_STOPPED",
                        terminalMessage(run, "Integration stopped"));
                return successful(response);
            } finally {
                terminate(run, RunStatus.STOPPED);
            }
        }
    }

    private void interruptActiveOperation(Run run, String reason) {
        Thread active = run.activeOperationThread;
        executionTrace.warn(
                "phase=RUN_INTERRUPT_REQUESTED runId={} mode={} instructionId={} requestId={} reason={} thread={}",
                run.runId, run.runtimeMode, run.currentInstructionId, run.currentRequestId, reason,
                active == null ? "none" : active.getName());
        if (run.runtimeMode == RuntimeMode.JAVA_V1) steps.interrupt();
        else if (run.v2Run != null) v2.interrupt(run.v2Run);
        if (active != null) active.interrupt();
    }

    private void handleFinish(FinishRequest request, Session transport) {
        executionTrace.info(
                "phase=FINISH_RECEIVED requestId={} runId={} lastSequence={}",
                request.requestId(), request.runId(), request.lastSequence());
        String fingerprint = gson.toJson(request);
        if (replayExisting(
                SmokeTestIntegrationContracts.FINISH,
                request.requestId(),
                fingerprint,
                transport,
                -1)) {
            return;
        }
        Run run = resolveRun(request.runId(), transport);
        synchronized (stateLock) {
            if (run == null || activeRuns.get(run.runId) != run) {
                publish(
                        transport,
                        -1,
                        SmokeTestIntegrationContracts.FINISH_RESPONSE,
                        rejected(request.requestId(), request.runId(), "RUN_NOT_ACTIVE",
                                "The Integration run is not active."));
                return;
            }
            if (run.stepPending || run.terminalPending) {
                publish(
                        transport,
                        run.authorization.homeBankingId(),
                        SmokeTestIntegrationContracts.FINISH_RESPONSE,
                        rejected(request.requestId(), request.runId(), "INTEGRATION_BUSY",
                                "Wait for the current Integration operation to finish."));
                return;
            }
            run.terminalPending = true;
            executionTrace.info(
                    "phase=FINISH_ADMITTED requestId={} runId={} mode={} hb={} bot={}",
                    request.requestId(), run.runId, run.runtimeMode,
                    run.authorization.homeBankingId(), run.authorization.botJobId());
        }
        submitOnce(
                SmokeTestIntegrationContracts.FINISH,
                request.requestId(),
                fingerprint,
                transport,
                run.authorization.homeBankingId(),
                () -> finish(run, request),
                () -> {
                    synchronized (stateLock) {
                        run.terminalPending = false;
                    }
                });
    }

    private JsonObject finish(Run run, FinishRequest request) {
        synchronized (run.operationLock) {
            return finishLocked(run, request);
        }
    }

    private JsonObject finishLocked(Run run, FinishRequest request) {
        if (request.lastSequence() != run.lastSequence) {
            return rejected(
                    request.requestId(),
                    request.runId(),
                    "LAST_SEQUENCE_MISMATCH",
                    "Finish does not match the last completed Integration step.");
        }
        RunStatus status = run.failed > 0 ? RunStatus.FAILED : RunStatus.FINISHED;
        try {
            TerminalResponse response = terminalResponse(
                    run,
                    request.requestId(),
                    status,
                    status == RunStatus.FINISHED ? "INTEGRATION_FINISHED" : "INTEGRATION_FAILED",
                    terminalMessage(
                            run,
                            status == RunStatus.FINISHED
                                    ? "Integration finished"
                                    : "Integration finished with failed steps"));
            return successful(response);
        } finally {
            terminate(run, status);
        }
    }

    /** Cancels and releases a run owned by a transport that closed or was superseded. */
    public void disconnected(String sessionId, Session transport) {
        if (transport == null) return;
        List<Run> runs;
        synchronized (stateLock) {
            runs = activeRuns.values().stream()
                    .filter(run -> run.responseTransport == transport)
                    .toList();
            if (runs.isEmpty()) return;
            runs.forEach(run -> {
                run.cancelled = true;
                if (run.lease != null) browserOwnership.requestRelease();
            });
        }
        for (Run run : runs) {
            try {
                worker.execute(() -> terminateSafely(run, RunStatus.STOPPED, "disconnect"));
            } catch (RejectedExecutionException shutdown) {
                terminateSafely(run, RunStatus.STOPPED, "disconnect-after-shutdown");
            }
        }
    }

    /** Retires a run when the same detached window is rebound to a new Bot Job/binding epoch. */
    public void bindingChanged(Session transport, String currentBindingEpoch) {
        if (transport == null) return;
        List<Run> runs;
        synchronized (stateLock) {
            runs = activeRuns.values().stream()
                    .filter(run -> run.responseTransport == transport
                            && !run.authorization.bindingEpoch().equals(currentBindingEpoch))
                    .toList();
            if (runs.isEmpty()) return;
            runs.forEach(run -> {
                run.cancelled = true;
                if (run.lease != null) browserOwnership.requestRelease();
            });
        }
        for (Run run : runs) {
            executionTrace.warn(
                    "phase=BINDING_CHANGE_RUN_INVALIDATED runId={} mode={} hb={} bot={} reason=OWNER_REBOUND",
                    run.runId, run.runtimeMode, run.authorization.homeBankingId(),
                    run.authorization.botJobId());
            interruptActiveOperation(run, "BINDING_CHANGED");
            try {
                worker.execute(() -> terminateSafely(run, RunStatus.STOPPED, "binding-change"));
            } catch (RejectedExecutionException shutdown) {
                terminateSafely(run, RunStatus.STOPPED, "binding-change-after-shutdown");
            }
        }
    }

    /** Prevents Bot Job retarget from crossing an active or not-yet-settled Integration owner. */
    public boolean isActiveOrStarting() {
        synchronized (stateLock) {
            return pendingV1Starts > 0
                    || refreshPending
                    || activeRuns.values().stream().anyMatch(run ->
                            run.runtimeMode == RuntimeMode.JAVA_V1 || run.terminalPending);
        }
    }

    /** Terminal application cleanup. The browser itself is owned by the application lifecycle. */
    public void shutdown() {
        List<Run> runs;
        synchronized (stateLock) {
            runs = List.copyOf(activeRuns.values());
            for (Run run : runs) {
                run.cancelled = true;
                if (run.lease != null) browserOwnership.requestRelease();
            }
        }
        try {
            for (Run run : runs) {
                terminateSafely(run, RunStatus.STOPPED, "service-shutdown");
            }
        } finally {
            worker.shutdownNow();
        }
    }

    private void submitOnce(
            String operation,
            String requestId,
            String fingerprint,
            Session transport,
            int homeBankingId,
            Task task,
            Runnable completion) {
        TransportRequest key = new TransportRequest(transport, requestId);
        RequestLedgerEntry entry;
        synchronized (stateLock) {
            RequestLedgerEntry existing = requestLedger.get(key);
            if (existing != null) {
                if (!existing.operation.equals(operation)
                        || !existing.fingerprint.equals(fingerprint)) {
                    publish(
                            transport,
                            homeBankingId,
                            responseOperation(operation),
                            rejected(requestId, "", "REQUEST_ID_CONFLICT",
                                    "This request ID was already used for another operation."));
                    return;
                }
                if (existing.response != null) {
                    JsonObject replay = existing.response.deepCopy();
                    if (SmokeTestIntegrationContracts.STEP.equals(operation)) {
                        replay.addProperty("replayed", true);
                    }
                    publish(transport, homeBankingId, responseOperation(operation), replay);
                } else {
                    existing.waiters.add(new Waiter(transport, homeBankingId));
                }
                return;
            }
            entry = new RequestLedgerEntry(operation, fingerprint, homeBankingId);
            requestLedger.put(key, entry);
            trimRequestLedger();
        }

        try {
            worker.execute(() -> {
                JsonObject result;
                try {
                    result = task.run();
                } catch (Throwable failure) {
                    executionTrace.error(
                            "phase=OPERATION_FAILED operation={} requestId={} failureType={}",
                            operation, requestId, failure.getClass().getSimpleName());
                    log.error("Smoke Test Integration operation {} failed", operation, failure);
                    result = rejected(requestId, "", "INTEGRATION_OPERATION_FAILED",
                            "Integration could not complete the requested operation.");
                } finally {
                    completion.run();
                }
                List<Waiter> waiters;
                synchronized (stateLock) {
                    entry.response = result.deepCopy();
                    waiters = List.copyOf(entry.waiters);
                    entry.waiters.clear();
                }
                publish(transport, homeBankingId, responseOperation(operation), result);
                for (Waiter waiter : waiters) {
                    JsonObject replay = result.deepCopy();
                    if (SmokeTestIntegrationContracts.STEP.equals(operation)) {
                        replay.addProperty("replayed", true);
                    }
                    publish(waiter.transport, waiter.homeBankingId, responseOperation(operation), replay);
                }
            });
        } catch (RejectedExecutionException busy) {
            executionTrace.warn(
                    "phase=OPERATION_REJECTED operation={} requestId={} code=INTEGRATION_BUSY",
                    operation, requestId);
            synchronized (stateLock) {
                requestLedger.remove(key, entry);
            }
            completion.run();
            publish(
                    transport,
                    homeBankingId,
                    responseOperation(operation),
                    rejected(requestId, "", "INTEGRATION_BUSY",
                            "Integration is busy. Wait for the current operation."));
        }
    }

    /** Replays or joins an already accepted request before busy/run-state checks are evaluated. */
    private boolean replayExisting(
            String operation,
            String requestId,
            String fingerprint,
            Session transport,
            int fallbackHomeBankingId) {
        JsonObject replay = null;
        String conflict = null;
        int homeBankingId = fallbackHomeBankingId;
        synchronized (stateLock) {
            RequestLedgerEntry existing = requestLedger.get(
                    new TransportRequest(transport, requestId));
            if (existing == null) return false;
            if (existing.homeBankingId >= 0) homeBankingId = existing.homeBankingId;
            if (!existing.operation.equals(operation)
                    || !existing.fingerprint.equals(fingerprint)) {
                conflict = "This request ID was already used for another operation.";
            } else if (existing.response == null) {
                executionTrace.info(
                        "phase=REQUEST_ATTACHED operation={} requestId={}", operation, requestId);
                existing.waiters.add(new Waiter(transport, homeBankingId));
            } else {
                executionTrace.info(
                        "phase=REQUEST_REPLAYED operation={} requestId={}", operation, requestId);
                replay = existing.response.deepCopy();
                if (SmokeTestIntegrationContracts.STEP.equals(operation)) {
                    replay.addProperty("replayed", true);
                }
            }
        }
        if (conflict != null) {
            publish(
                    transport,
                    homeBankingId,
                    responseOperation(operation),
                    rejected(requestId, "", "REQUEST_ID_CONFLICT", conflict));
        } else if (replay != null) {
            publish(transport, homeBankingId, responseOperation(operation), replay);
        }
        return true;
    }

    private void recordStep(
            Run run,
            StepRequest request,
            JsonObject response,
            StepStatus status) {
        synchronized (stateLock) {
            run.lastSequence = request.sequence();
            switch (status) {
                case PASSED -> run.passed++;
                case WARNING -> run.warnings++;
                case FAILED -> run.failed++;
                case SKIPPED -> run.skipped++;
            }
            run.sequenceResults.put(
                    request.sequence(),
                    new SequenceResult(
                            request.instructionId(),
                            request.excelRowIndex(),
                            response.deepCopy()));
            while (run.sequenceResults.size() > MAX_SEQUENCE_LEDGER) {
                Long oldest = run.sequenceResults.keySet().iterator().next();
                run.sequenceResults.remove(oldest);
            }
        }
    }

    private JsonObject stoppedStep(Run run, StepRequest request, String message) {
        Outcome stopped = new Outcome(
                StepStatus.SKIPPED,
                SmokeTestIntegrationContracts.StepDisposition.INACTIVE,
                "INTEGRATION_STOPPING",
                message,
                null,
                null);
        JsonObject response = stepResponse(run, request, stopped, false);
        recordStep(run, request, response, StepStatus.SKIPPED);
        return response;
    }

    private JsonObject stepResponse(
            Run run, StepRequest request, Outcome outcome, boolean replayed) {
        StepResponse response = new StepResponse(
                SmokeTestIntegrationContracts.CONTRACT_VERSION,
                request.requestId(),
                run.runId,
                run.integrationEpoch,
                request.sequence(),
                request.instructionId(),
                outcome.status(),
                outcome.disposition(),
                outcome.code(),
                outcome.message(),
                request.recoveryVerificationEnabled(),
                replayed);
        JsonObject json = successful(response);
        if (outcome.runtimeVariableId() != null && outcome.runtimeValue() != null) {
            JsonObject update = new JsonObject();
            update.addProperty("variableId", outcome.runtimeVariableId());
            RuntimeVariableValue value = outcome.runtimeValue();
            update.addProperty("state", value.state().name());
            if (value.isValue()) update.addProperty("value", value.value());
            else if (value.voidReason() != null) {
                update.addProperty("voidReason", value.voidReason().name());
            }
            json.add("runtimeUpdate", update);
        }
        if (outcome.recovery() != null) {
            json.add("recovery", outcome.recovery().deepCopy());
        }
        return json;
    }

    private TerminalResponse terminalResponse(
            Run run,
            String requestId,
            RunStatus status,
            String code,
            String message) {
        return new TerminalResponse(
                SmokeTestIntegrationContracts.CONTRACT_VERSION,
                requestId,
                run.runId,
                run.integrationEpoch,
                status,
                run.lastSequence,
                run.passed,
                run.warnings,
                run.failed,
                run.skipped,
                code,
                message);
    }

    private Run resolveRun(String runId, Session transport) {
        Run run;
        synchronized (stateLock) {
            run = activeRuns.get(runId);
            if (run == null || run.released.get()) return null;
            if (run.responseTransport == transport) return run;
        }
        if (!isCurrentRunAuthority(run, transport)) return null;
        synchronized (stateLock) {
            if (activeRuns.get(runId) != run || run.released.get()) return null;
            run.responseTransport = transport;
            return run;
        }
    }

    private void terminate(Run expected, RunStatus terminalStatus) {
        synchronized (stateLock) {
            if (activeRuns.get(expected.runId) != expected || expected.released.get()) return;
        }
        if (!expected.releaseInProgress.compareAndSet(false, true)) return;
        executionTrace.info(
                "phase=RUN_TERMINATING runId={} integrationEpoch={} mode={} hb={} bot={} terminalStatus={}",
                expected.runId, expected.integrationEpoch, expected.runtimeMode,
                expected.authorization.homeBankingId(), expected.authorization.botJobId(),
                terminalStatus);
        try {
            if (expected.runtimeMode == RuntimeMode.JAVA_V1) {
                steps.clearRecovery(expected.runId, terminalStatus.name());
            }
            if (expected.v2Run != null) v2.close(expected.v2Run);
            if (expected.lease != null) expected.lease.close();
            expected.status = terminalStatus;
            expected.released.set(true);
            synchronized (stateLock) {
                activeRuns.remove(expected.runId, expected);
                expected.stepPending = false;
                expected.terminalPending = false;
            }
            executionTrace.info(
                    "phase=RUN_TERMINATED runId={} integrationEpoch={} mode={} terminalStatus={} passed={} warnings={} failed={} skipped={}",
                    expected.runId, expected.integrationEpoch, expected.runtimeMode, terminalStatus,
                    expected.passed, expected.warnings, expected.failed, expected.skipped);
        } catch (RuntimeException cleanupFailure) {
            executionTrace.error(
                    "phase=RUN_TERMINATION_FAILED runId={} integrationEpoch={} mode={} terminalStatus={} failureType={}",
                    expected.runId, expected.integrationEpoch, expected.runtimeMode, terminalStatus,
                    cleanupFailure.getClass().getSimpleName());
            expected.releaseInProgress.set(false);
            throw cleanupFailure;
        }
    }

    private void terminateSafely(Run run, RunStatus status, String context) {
        try {
            terminate(run, status);
        } catch (RuntimeException cleanupFailure) {
            log.error(
                    "Smoke Integration cleanup requires retry context={} runId={}",
                    context,
                    run.runId,
                    cleanupFailure);
        }
    }

    private static String terminalMessage(Run run, String prefix) {
        return prefix + ". The Playwright page remains open; only a Close Browser instruction closes it.";
    }

    private void closeV2AfterFailedStart(V2Run run) {
        try {
            v2.close(run);
        } catch (RuntimeException cleanupFailure) {
            log.error("Execution V2 failed-start cleanup requires retry runId={}", run.runId());
        }
    }

    private void trimRequestLedger() {
        while (requestLedger.size() > MAX_REQUEST_LEDGER) {
            Map.Entry<TransportRequest, RequestLedgerEntry> oldest =
                    requestLedger.entrySet().iterator().next();
            if (oldest.getValue().response == null) return;
            requestLedger.remove(oldest.getKey());
        }
    }

    private boolean isRegisteredTransport(Session transport) {
        return transport != null
                && transport.isOpen()
                && WebSocketSessionManager.getSession(SESSION_ID) == transport;
    }

    private void publish(
            Session transport, int homeBankingId, String operation, JsonObject response) {
        if (transport == null || response == null) return;
        responses.publish(transport, homeBankingId, operation, response);
    }

    private JsonObject successful(Object response) {
        JsonObject json = gson.toJsonTree(response).getAsJsonObject();
        json.addProperty("ok", true);
        return json;
    }

    private JsonObject rejected(JsonObject request, String code, String message) {
        Correlation correlation = SmokeTestIntegrationContracts.correlation(request);
        return rejected(correlation.requestId(), correlation.runId(), code, message);
    }

    private JsonObject rejected(
            String requestId, String runId, String code, String message) {
        JsonObject response = new JsonObject();
        response.addProperty("ok", false);
        response.addProperty("contractVersion", SmokeTestIntegrationContracts.CONTRACT_VERSION);
        if (requestId != null && !requestId.isBlank()) response.addProperty("requestId", requestId);
        if (runId != null && !runId.isBlank()) response.addProperty("runId", runId);
        response.addProperty("status", RunStatus.REJECTED.name());
        response.addProperty("code", code);
        response.addProperty("message", message == null || message.isBlank()
                ? "Integration request was refused." : message);
        return response;
    }

    private static String safeMessage(Throwable failure, String fallback) {
        String value = failure == null ? "" : failure.getMessage();
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String responseOperation(String operation) {
        if (SmokeTestIntegrationContracts.START.equals(operation)) {
            return SmokeTestIntegrationContracts.START_RESPONSE;
        }
        if (SmokeTestIntegrationContracts.STEP.equals(operation)) {
            return SmokeTestIntegrationContracts.STEP_RESPONSE;
        }
        if (SmokeTestIntegrationContracts.RECOVER.equals(operation)) {
            return SmokeTestIntegrationContracts.RECOVER_RESPONSE;
        }
        if (SmokeTestIntegrationContracts.EXCEL_WRITE.equals(operation)) {
            return SmokeTestIntegrationContracts.EXCEL_WRITE_RESPONSE;
        }
        if (SmokeTestIntegrationContracts.REFRESH.equals(operation)) {
            return SmokeTestIntegrationContracts.REFRESH_RESPONSE;
        }
        if (SmokeTestIntegrationContracts.STOP.equals(operation)) {
            return SmokeTestIntegrationContracts.STOP_RESPONSE;
        }
        if (SmokeTestIntegrationContracts.FORCE_STOP.equals(operation)) {
            return SmokeTestIntegrationContracts.FORCE_STOP_RESPONSE;
        }
        if (SmokeTestIntegrationContracts.FINISH.equals(operation)) {
            return SmokeTestIntegrationContracts.FINISH_RESPONSE;
        }
        if (SmokeTestIntegrationContracts.RUNTIME_STATUS.equals(operation)) {
            return SmokeTestIntegrationContracts.RUNTIME_STATUS_RESPONSE;
        }
        if (SmokeTestIntegrationContracts.RUNTIME_CONTROL.equals(operation)) {
            return SmokeTestIntegrationContracts.RUNTIME_CONTROL_RESPONSE;
        }
        if (SmokeTestIntegrationContracts.RUNTIME_INSTANCES.equals(operation)) {
            return SmokeTestIntegrationContracts.RUNTIME_INSTANCES_RESPONSE;
        }
        if (SmokeTestIntegrationContracts.RUNTIME_INSTANCE_CONTROL.equals(operation)) {
            return SmokeTestIntegrationContracts.RUNTIME_INSTANCE_CONTROL_RESPONSE;
        }
        return "smokeTest.integration.errorResponse";
    }

    private static ThreadPoolExecutor newWorker() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                MAX_ACTIVE_V2_RUNS * 2,
                MAX_ACTIVE_V2_RUNS * 2,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(MAX_ACTIVE_V2_RUNS * 8),
                runnable -> {
                    Thread thread = new Thread(runnable, "smoke-test-integration");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
        executor.prestartCoreThread();
        return executor;
    }

    private interface Task {
        JsonObject run();
    }

    interface BindingPort {
        SmokeIntegrationAuthorization authorize(JsonObject request, Session transport);

        default SmokeIntegrationAuthorization authorizeEmergencyStop(
                JsonObject request, Session transport) {
            return authorize(request, transport);
        }

        boolean isCurrent(SmokeIntegrationAuthorization expected, Session transport);

        default void requireRuntimeVariablesReady(
                SmokeIntegrationAuthorization expected, Session transport) {}

        default void requireSupportingWorkspacesReady(
                SmokeIntegrationAuthorization expected, Session transport) {
            requireRuntimeVariablesReady(expected, transport);
        }
    }

    interface DatasetPort {
        IntegrationDataset freeze(int botJobId, String mode);
    }

    interface SnapshotPort {
        Plan load(Owner owner, SmokeTestIntegrationContracts.Scope scope)
                throws java.sql.SQLException;
    }

    interface StepPort {
        Outcome execute(
                Plan plan,
                IntegrationDataset dataset,
                int instructionId,
                int excelRowIndex,
                RunVariables variables);

        default Outcome prepareRecovery(
                String runId,
                Plan plan,
                IntegrationDataset dataset,
                int instructionId,
                int excelRowIndex,
                RunVariables variables,
                Outcome failure) {
            return failure;
        }

        default Outcome recover(
                String runId,
                int instructionId,
                String recoveryCandidateId,
                boolean save,
                RunVariables variables) {
            throw new UnsupportedOperationException("Java V1 recovery is unavailable");
        }

        default void cancelRecovery(String runId, int instructionId, String reason) {}

        default void clearRecovery(String runId, String reason) {}

        default void interrupt() {}

        default void forceStop() { interrupt(); }
    }

    interface V2Port {
        V2Run start(SmokeIntegrationAuthorization authorization, Plan plan, String datasetMode);

        Outcome execute(
                V2Run run,
                Plan plan,
                IntegrationDataset dataset,
                long sequence,
                int instructionId,
                int excelRowIndex,
                RunVariables variables);

        default void interrupt(V2Run run) {}

        default Outcome recover(
                V2Run run,
                int instructionId,
                String recoveryCandidateId,
                boolean save) {
            throw new UnsupportedOperationException("V2 recovery is unavailable");
        }

        default void cancelRecovery(V2Run run, int instructionId) {}

        void closeBrowser(V2Run run);

        void close(V2Run run);
    }

    record V2Run(String runId, Object authority) {
        V2Run {
            if (runId == null || runId.isBlank() || authority == null) {
                throw new IllegalArgumentException("Execution V2 run is invalid");
            }
        }
    }

    interface BrowserOwnershipPort {
        BrowserLease reserve();

        default boolean requestRelease() {
            return false;
        }
    }

    @FunctionalInterface
    interface BrowserLease extends AutoCloseable {
        @Override
        void close();
    }

    interface WorkspacePort {
        String executionState(int botJobId, long workspaceEpoch);

        default boolean commitMutation(
                int botJobId, long workspaceEpoch, BooleanSupplier mutation) {
            return mutation.getAsBoolean();
        }

        default <T> T commitMutation(int botJobId, long workspaceEpoch, java.util.function.Supplier<T> mutation) {
            return mutation.get();
        }
    }

    interface BrowserStartPort {
        boolean openSelectedPageAndWait(String browserType, String url, String optionsConfig);

        default boolean openPreservingCurrentPageAndWait(
                String browserType, String url, String optionsConfig) {
            return openSelectedPageAndWait(browserType, url, optionsConfig);
        }

        default boolean reloadCurrentPage() {
            return false;
        }
    }

    interface ResponsePort {
        void publish(Session transport, int homeBankingId, String operation, JsonObject response);
    }

    private static final class DefaultBrowserStartPort implements BrowserStartPort {
        @Override
        public boolean openSelectedPageAndWait(
                String browserType, String url, String optionsConfig) {
            ARWebDriver driver = ARWebDriver.getInstance();
            if (!driver.openBrowserForOwnerSwitch(browserType, url, optionsConfig)) return false;
            var current = driver.currentPlaywrightDriver();
            if (current == null || !current.isOpen()) return false;
            current.waitForPageSettled(15_000L);
            String currentUrl = current.currentUrl();
            return currentUrl != null
                    && !currentUrl.isBlank()
                    && !"about:blank".equalsIgnoreCase(currentUrl.trim());
        }

        @Override
        public boolean openPreservingCurrentPageAndWait(
                String browserType, String url, String optionsConfig) {
            ARWebDriver driver = ARWebDriver.getInstance();
            if (!driver.openBrowserPreservingCurrentPage(browserType, url, optionsConfig)) {
                return false;
            }
            var current = driver.currentPlaywrightDriver();
            if (current == null || !current.isOpen()) return false;
            current.waitForPageSettled(15_000L);
            String currentUrl = current.currentUrl();
            return currentUrl != null
                    && !currentUrl.isBlank()
                    && !"about:blank".equalsIgnoreCase(currentUrl.trim());
        }

        @Override
        public boolean reloadCurrentPage() {
            var current = ARWebDriver.getInstance().currentPlaywrightDriver();
            if (current == null || !current.isOpen()) return false;
            current.reload();
            current.waitForPageSettled(15_000L);
            return true;
        }
    }

    private static final class DefaultBindingPort implements BindingPort {
        private final VariablesWorkspaceService delegate = VariablesWorkspaceService.getInstance();

        @Override
        public SmokeIntegrationAuthorization authorize(JsonObject request, Session transport) {
            return delegate.authorizeSmokeIntegration(request, SESSION_ID, transport);
        }

        @Override
        public SmokeIntegrationAuthorization authorizeEmergencyStop(
                JsonObject request, Session transport) {
            return delegate.authorizeSmokeIntegrationEmergencyStop(
                    request, SESSION_ID, transport);
        }

        @Override
        public boolean isCurrent(
                SmokeIntegrationAuthorization expected, Session transport) {
            return delegate.isCurrentSmokeIntegrationBinding(expected, transport);
        }

        @Override
        public void requireRuntimeVariablesReady(
                SmokeIntegrationAuthorization expected, Session transport) {
            delegate.requireRuntimeVariablesReadyForSmokeIntegration(expected, transport);
        }

        @Override
        public void requireSupportingWorkspacesReady(
                SmokeIntegrationAuthorization expected, Session transport) {
            delegate.requireSupportingWorkspacesReadyForSmoke(expected, transport);
        }
    }

    private static final class DefaultDatasetPort implements DatasetPort {
        @Override
        public IntegrationDataset freeze(int botJobId, String mode) {
            return ExcelDataWorkspaceService.getInstance()
                    .freezeIntegrationData(botJobId, mode);
        }
    }

    private static final class DefaultSnapshotPort implements SnapshotPort {
        private final SmokeTestIntegrationSnapshotRepository delegate =
                new SmokeTestIntegrationSnapshotRepository();

        @Override
        public Plan load(Owner owner, SmokeTestIntegrationContracts.Scope scope)
                throws java.sql.SQLException {
            return delegate.load(owner, scope);
        }
    }

    private static final class DefaultStepPort implements StepPort {
        private final SmokeTestIntegrationStepExecutor delegate =
                new SmokeTestIntegrationStepExecutor();
        private final SmokeTestIntegrationV1RecoveryCoordinator recovery =
                new SmokeTestIntegrationV1RecoveryCoordinator();

        @Override
        public Outcome execute(
                Plan plan,
                IntegrationDataset dataset,
                int instructionId,
                int excelRowIndex,
                RunVariables variables) {
            return delegate.execute(plan, dataset, instructionId, excelRowIndex, variables);
        }

        @Override
        public Outcome prepareRecovery(
                String runId,
                Plan plan,
                IntegrationDataset dataset,
                int instructionId,
                int excelRowIndex,
                RunVariables variables,
                Outcome failure) {
            return recovery.prepare(
                    runId, plan, dataset, instructionId, excelRowIndex, variables, failure);
        }

        @Override
        public Outcome recover(
                String runId,
                int instructionId,
                String recoveryCandidateId,
                boolean save,
                RunVariables variables) {
            return recovery.recover(
                    runId, instructionId, recoveryCandidateId, save, variables);
        }

        @Override
        public void cancelRecovery(String runId, int instructionId, String reason) {
            recovery.cancel(runId, instructionId, reason);
        }

        @Override
        public void clearRecovery(String runId, String reason) {
            recovery.clearRun(runId, reason);
        }

        @Override
        public void interrupt() {
            ARPlaywrightDriver driver = ARWebDriver.getInstance().currentPlaywrightDriver();
            if (driver != null) driver.cancelCurrentOperation();
        }

        @Override
        public void forceStop() {
            interrupt();
            ARWebDriver.getInstance().closeBrowser();
        }
    }

    private static final class DefaultV2Port implements V2Port {
        private final ExecutionRuntimeRunCoordinator coordinator;
        private final SmokeTestIntegrationV2StepExecutor executor;

        private DefaultV2Port() {
            coordinator = ExecutionV2RuntimeSupervisor.getInstance().coordinator();
            executor = new SmokeTestIntegrationV2StepExecutor(coordinator);
            executionTrace.info("phase=V2_CONFIGURATION status=SUPERVISED");
        }

        @Override
        public V2Run start(
                SmokeIntegrationAuthorization authorization, Plan plan, String datasetMode) {
            AuthorizedGrantFacts facts = new AuthorizedGrantFacts(
                    authorization.homeBankingId(),
                    authorization.homeBankingId(),
                    authorization.botJobId(),
                    authorization.workspaceEpoch(),
                    authorization.graphRevision(),
                    plan.planRevision(),
                    DataMode.parse(datasetMode));
            ExecutionRuntimeRunCoordinator.Run run = coordinator.start(facts, plan);
            return new V2Run(run.runId(), run);
        }

        @Override
        public Outcome execute(
                V2Run run,
                Plan plan,
                IntegrationDataset dataset,
                long sequence,
                int instructionId,
                int excelRowIndex,
                RunVariables variables) {
            return executor.execute(
                    authority(run), plan, dataset, sequence, instructionId, excelRowIndex, variables);
        }

        @Override
        public void interrupt(V2Run run) {
            coordinator.interrupt(authority(run));
        }

        @Override
        public Outcome recover(
                V2Run run,
                int instructionId,
                String recoveryCandidateId,
                boolean save) {
            return executor.recover(authority(run), instructionId, recoveryCandidateId, save);
        }

        @Override
        public void cancelRecovery(V2Run run, int instructionId) {
            executor.cancelRecovery(authority(run), instructionId);
        }

        @Override
        public void closeBrowser(V2Run run) {
            coordinator.closeBrowser(authority(run));
        }

        @Override
        public void close(V2Run run) {
            coordinator.close(authority(run));
        }

        private static ExecutionRuntimeRunCoordinator.Run authority(V2Run run) {
            if (run == null || !(run.authority() instanceof ExecutionRuntimeRunCoordinator.Run value)) {
                throw new IllegalArgumentException("Execution V2 run authority is invalid");
            }
            return value;
        }
    }

    private static final class DefaultBrowserOwnershipPort implements BrowserOwnershipPort {
        @Override
        public BrowserLease reserve() {
            ExecutionPauseCoordinator.ExecutionStart lease =
                    ExecutionPauseCoordinator.getInstance().reserveExecutionStart();
            return lease::close;
        }

        @Override
        public boolean requestRelease() {
            return ExecutionPauseCoordinator.getInstance().requestExecutionRelease();
        }
    }

    private static final class DefaultWorkspacePort implements WorkspacePort {
        @Override
        public String executionState(int botJobId, long workspaceEpoch) {
            return BotJobDetailsWorkspaceRegistry.getInstance()
                    .require(botJobId, workspaceEpoch)
                    .executionState();
        }

        @Override
        public boolean commitMutation(
                int botJobId, long workspaceEpoch, BooleanSupplier mutation) {
            return BotJobDetailsWorkspaceRegistry.getInstance()
                    .commitWorkspaceMutation(
                            botJobId, workspaceEpoch, mutation::getAsBoolean);
        }

        @Override
        public <T> T commitMutation(int botJobId, long workspaceEpoch, java.util.function.Supplier<T> mutation) {
            return BotJobDetailsWorkspaceRegistry.getInstance().commitWorkspaceMutation(botJobId, workspaceEpoch, mutation);
        }
    }

    private static final class WebSocketResponsePort implements ResponsePort {
        private final Gson gson = new Gson();

        @Override
        public void publish(
                Session transport, int homeBankingId, String operation, JsonObject response) {
            WebSocketSessionManager.sendMessageJson(
                    homeBankingId,
                    transport,
                    SESSION_ID,
                    gson.toJson(response),
                    operation);
        }
    }

    private static final class Run {
        private final String runId;
        private final long integrationEpoch;
        private final Object operationLock = new Object();
        private volatile Session responseTransport;
        private final SmokeIntegrationAuthorization authorization;
        private final Plan plan;
        private final IntegrationDataset dataset;
        private final boolean durableRuntimeWrites;
        private final RunVariables variables;
        private final RuntimeSnapshot runtimeSnapshot;
        private final RuntimeMode runtimeMode;
        private final PagePolicy pagePolicy;
        private final BrowserLease lease;
        private final V2Run v2Run;
        private final boolean dashboardMulti;
        private final java.util.concurrent.atomic.AtomicBoolean released =
                new java.util.concurrent.atomic.AtomicBoolean();
        private final java.util.concurrent.atomic.AtomicBoolean releaseInProgress =
                new java.util.concurrent.atomic.AtomicBoolean();
        private final java.time.Instant startedAt = java.time.Instant.now();
        private volatile Thread activeOperationThread;
        private volatile int currentInstructionId;
        private volatile String currentRequestId = "";
        private final LinkedHashMap<Long, SequenceResult> sequenceResults =
                new LinkedHashMap<>();
        private volatile boolean cancelled;
        private volatile RunStatus status = RunStatus.RUNNING;
        private boolean stepPending;
        private boolean terminalPending;
        private long lastSequence;
        private int passed;
        private int warnings;
        private int failed;
        private int skipped;

        private Run(
                String runId,
                long integrationEpoch,
                Session transport,
                SmokeIntegrationAuthorization authorization,
                Plan plan,
                IntegrationDataset dataset,
                boolean durableRuntimeWrites,
                RunVariables variables,
                RuntimeMode runtimeMode,
                PagePolicy pagePolicy,
                BrowserLease lease,
                V2Run v2Run,
                boolean dashboardMulti) {
            this.runId = runId;
            this.integrationEpoch = integrationEpoch;
            this.responseTransport = transport;
            this.authorization = authorization;
            this.plan = plan;
            this.dataset = dataset;
            this.durableRuntimeWrites = durableRuntimeWrites;
            this.variables = variables;
            this.runtimeSnapshot = variables.initialSnapshot();
            this.runtimeMode = runtimeMode;
            this.pagePolicy = pagePolicy;
            this.lease = lease;
            this.v2Run = v2Run;
            this.dashboardMulti = dashboardMulti;
        }
    }

    private static final class PendingStart {
        private final StartRequest request;
        private final TransportRequest key;
        private final java.util.concurrent.atomic.AtomicBoolean cancelled =
                new java.util.concurrent.atomic.AtomicBoolean();
        private final java.util.concurrent.atomic.AtomicBoolean settled =
                new java.util.concurrent.atomic.AtomicBoolean();
        private volatile Thread workerThread;
        private volatile BrowserLease lease;
        private volatile V2Run v2Run;

        private PendingStart(StartRequest request, Session transport) {
            this.request = request;
            this.key = new TransportRequest(transport, request.requestId());
        }

        private boolean matches(SmokeIntegrationAuthorization authorization) {
            return request.homeBankingId() == authorization.homeBankingId()
                    && request.botJobId() == authorization.botJobId();
        }

        private void requireActive() {
            if (cancelled.get() || Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException("Integration startup was force-stopped.");
            }
        }
    }

    private static final class TransportRequest {
        private final Session transport;
        private final String requestId;

        private TransportRequest(Session transport, String requestId) {
            this.transport = transport;
            this.requestId = requestId;
        }

        @Override
        public boolean equals(Object candidate) {
            return candidate instanceof TransportRequest other
                    && transport == other.transport
                    && requestId.equals(other.requestId);
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(transport) + requestId.hashCode();
        }
    }

    private static final class RequestLedgerEntry {
        private final String operation;
        private final String fingerprint;
        private final int homeBankingId;
        private final List<Waiter> waiters = new ArrayList<>();
        private JsonObject response;

        private RequestLedgerEntry(String operation, String fingerprint, int homeBankingId) {
            this.operation = operation;
            this.fingerprint = fingerprint;
            this.homeBankingId = homeBankingId;
        }
    }

    private record Waiter(Session transport, int homeBankingId) {}

    private static final class ExcelWriteFailure extends RuntimeException {
        private ExcelWriteFailure(Throwable cause) { super(cause); }
    }

    private record SequenceResult(
            int instructionId,
            int excelRowIndex,
            JsonObject response,
            StepStatus status,
            boolean recoveryPending,
            boolean recoveryVerificationEnabled) {
        private SequenceResult(int instructionId, int excelRowIndex, JsonObject response) {
            this(
                    instructionId,
                    excelRowIndex,
                    response,
                    StepStatus.valueOf(response.get("status").getAsString()),
                    response.has("recovery"),
                    response.get("recoveryVerificationEnabled").getAsBoolean());
        }
        private boolean matches(StepRequest request) {
            return instructionId == request.instructionId()
                    && excelRowIndex == request.excelRowIndex()
                    && recoveryVerificationEnabled == request.recoveryVerificationEnabled();
        }
    }
}

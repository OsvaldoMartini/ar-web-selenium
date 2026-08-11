package com.allinweb.ch.socket;

import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.facade.BotJobDetailsWorkspaceRegistry;
import com.allinweb.ch.facade.ExecutionPauseCoordinator;
import com.allinweb.ch.facade.actions.RuntimeVariableValue;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.Owner;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.Plan;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationStepExecutor;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationStepExecutor.Outcome;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationStepExecutor.RunVariables;
import com.allinweb.ch.facade.execution.v2.ExecutionRuntimeRunCoordinator;
import com.allinweb.ch.facade.execution.v2.ExecutionV2Contracts.AuthorizedGrantFacts;
import com.allinweb.ch.facade.execution.v2.ExecutionV2Contracts.DataMode;
import com.allinweb.ch.facade.execution.v2.SmokeTestIntegrationV2StepExecutor;
import com.allinweb.ch.model.DetachedWorkspaceSessions;
import com.allinweb.ch.model.SmokeTestIntegrationContracts;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.Correlation;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.FinishRequest;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.PagePolicy;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.RefreshRequest;
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
import java.net.URI;
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
    private final LinkedHashMap<TransportRequest, RequestLedgerEntry> requestLedger =
            new LinkedHashMap<>();
    private Run active;
    private boolean startPending;
    private boolean refreshPending;
    private boolean stepPending;
    private boolean terminalPending;

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
        if (!SESSION_ID.equals(sessionId) || !isRegisteredTransport(transport)) {
            rejectTransport(operation, body, transport);
            return;
        }
        try {
            switch (operation) {
                case SmokeTestIntegrationContracts.START -> handleStart(
                        SmokeTestIntegrationContracts.parseStart(body), body, transport);
                case SmokeTestIntegrationContracts.REFRESH -> handleRefresh(
                        SmokeTestIntegrationContracts.parseRefresh(body), body, transport);
                case SmokeTestIntegrationContracts.STEP -> handleStep(
                        SmokeTestIntegrationContracts.parseStep(body), transport);
                case SmokeTestIntegrationContracts.STOP -> handleStop(
                        SmokeTestIntegrationContracts.parseStop(body), transport);
                case SmokeTestIntegrationContracts.FINISH -> handleFinish(
                        SmokeTestIntegrationContracts.parseFinish(body), transport);
                default -> publish(
                        transport,
                        -1,
                        responseOperation(operation),
                        rejected(body, "UNSUPPORTED_OPERATION", "Unsupported Integration operation."));
            }
        } catch (IllegalArgumentException invalid) {
            log.warn("Rejected invalid Smoke Test Integration contract: {}", invalid.getMessage());
            publish(
                    transport,
                    -1,
                    responseOperation(operation),
                    rejected(body, "INVALID_CONTRACT", invalid.getMessage()));
        }
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
        String fingerprint = gson.toJson(request);
        if (replayExisting(
                SmokeTestIntegrationContracts.START,
                request.requestId(),
                fingerprint,
                transport,
                request.homeBankingId())) {
            return;
        }
        synchronized (stateLock) {
            if (active != null || startPending || refreshPending) {
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
            startPending = true;
        }
        submitOnce(
                SmokeTestIntegrationContracts.START,
                request.requestId(),
                fingerprint,
                transport,
                request.homeBankingId(),
                () -> start(request, rawBody, transport),
                () -> {
                    synchronized (stateLock) {
                        startPending = false;
                    }
                });
    }

    private void handleRefresh(
            RefreshRequest request, JsonObject rawBody, Session transport) {
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
            if (active != null || startPending || refreshPending || stepPending || terminalPending) {
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
            return response;
        } catch (IllegalArgumentException | IllegalStateException refused) {
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

    private JsonObject start(StartRequest request, JsonObject rawBody, Session transport) {
        BrowserLease lease = null;
        V2Run v2Run = null;
        try {
            SmokeIntegrationAuthorization authorization = variables.authorize(rawBody, transport);
            Plan plan = snapshots.load(
                    new Owner(authorization.homeBankingId(), authorization.botJobId()),
                    request.scope());
            IntegrationDataset dataset = excel.freeze(
                    authorization.botJobId(), request.excelMode().name());
            if (dataset.homeBankingId() != authorization.homeBankingId()) {
                throw new IllegalStateException(
                        "The frozen Excel dataset belongs to another organization.");
            }
            // Re-read the relationship revision after both frozen snapshots were loaded. A
            // concurrent graph mutation must fail start instead of combining a stale React graph
            // assertion with newer SQL/Excel facts.
            authorization = variables.authorize(rawBody, transport);
            if (request.runtimeMode() == RuntimeMode.JAVA_V1) {
                lease = browserOwnership.reserve();
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
                if (!pageReady) {
                    throw new IllegalStateException(
                            request.pagePolicy() == PagePolicy.PRESERVE_ACTIVE
                                    ? "The current Playwright page is unavailable or belongs to another site."
                                    : "The selected Bot Job Playwright page could not be opened and settled.");
                }
            } else {
                if (request.pagePolicy() != PagePolicy.RELOAD_SELECTED) {
                    throw new IllegalArgumentException(
                            "Execution V2 requires a new isolated page at the selected Bot Job URL.");
                }
                v2Run = v2.start(authorization, plan, dataset.mode());
            }
            if (!variables.isCurrent(authorization, transport)) {
                throw new IllegalStateException(
                        "The Smoke Test target changed while Integration was starting.");
            }

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
                    v2Run);
            synchronized (stateLock) {
                if (active != null || !isRegisteredTransport(transport)) {
                    throw new IllegalStateException(
                            "The Smoke Test page disconnected while Integration was starting.");
                }
                active = run;
                lease = null;
                v2Run = null;
            }
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
            log.warn("Smoke Test Integration start refused: {}", refused.getMessage());
            return rejected(
                    rawBody,
                    "INTEGRATION_START_REFUSED",
                    safeMessage(refused, "Integration could not be started."));
        } catch (java.sql.SQLException persistenceFailure) {
            log.error("Unable to load the authoritative Smoke Test Integration plan", persistenceFailure);
            return rejected(
                    rawBody,
                    "INTEGRATION_PLAN_UNAVAILABLE",
                    "The Integration plan could not be loaded from the database.");
        } catch (RuntimeException failure) {
            log.error("Unable to start Smoke Test Integration", failure);
            return rejected(
                    rawBody,
                    "INTEGRATION_START_FAILED",
                    "Integration could not start the Playwright page.");
        } finally {
            if (lease != null) lease.close();
            if (v2Run != null) closeV2AfterFailedStart(v2Run);
        }
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
        Run run;
        synchronized (stateLock) {
            run = requireRun(request.runId(), transport);
            if (run == null) {
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
            if (stepPending || terminalPending || run.cancelled) {
                publish(
                        transport,
                        run.authorization.homeBankingId(),
                        SmokeTestIntegrationContracts.STEP_RESPONSE,
                        rejected(request.requestId(), request.runId(), "INTEGRATION_BUSY",
                                "Wait for the current Integration operation to finish."));
                return;
            }
            stepPending = true;
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
                        stepPending = false;
                    }
                });
    }

    private JsonObject step(Run run, StepRequest request) {
        if (run.cancelled) {
            return stoppedStep(run, request, "Integration stop was requested.");
        }
        if (!variables.isCurrent(run.authorization, run.transport)) {
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
        JsonObject response = stepResponse(run, request, outcome, false);
        recordStep(run, request, response, outcome.status());
        return response;
    }

    private void handleStop(StopRequest request, Session transport) {
        String fingerprint = gson.toJson(request);
        if (replayExisting(
                SmokeTestIntegrationContracts.STOP,
                request.requestId(),
                fingerprint,
                transport,
                -1)) {
            return;
        }
        Run run;
        synchronized (stateLock) {
            run = requireRun(request.runId(), transport);
            if (run == null) {
                publish(
                        transport,
                        -1,
                        SmokeTestIntegrationContracts.STOP_RESPONSE,
                        rejected(request.requestId(), request.runId(), "RUN_NOT_ACTIVE",
                                "The Integration run is not active."));
                return;
            }
            run.cancelled = true;
            if (terminalPending) {
                publish(
                        transport,
                        run.authorization.homeBankingId(),
                        SmokeTestIntegrationContracts.STOP_RESPONSE,
                        rejected(request.requestId(), request.runId(), "INTEGRATION_BUSY",
                                "Integration termination is already in progress."));
                return;
            }
            terminalPending = true;
        }
        submitOnce(
                SmokeTestIntegrationContracts.STOP,
                request.requestId(),
                fingerprint,
                transport,
                run.authorization.homeBankingId(),
                () -> stop(run, request),
                () -> {
                    synchronized (stateLock) {
                        terminalPending = false;
                    }
                });
    }

    private JsonObject stop(Run run, StopRequest request) {
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

    private void handleFinish(FinishRequest request, Session transport) {
        String fingerprint = gson.toJson(request);
        if (replayExisting(
                SmokeTestIntegrationContracts.FINISH,
                request.requestId(),
                fingerprint,
                transport,
                -1)) {
            return;
        }
        Run run;
        synchronized (stateLock) {
            run = requireRun(request.runId(), transport);
            if (run == null) {
                publish(
                        transport,
                        -1,
                        SmokeTestIntegrationContracts.FINISH_RESPONSE,
                        rejected(request.requestId(), request.runId(), "RUN_NOT_ACTIVE",
                                "The Integration run is not active."));
                return;
            }
            if (stepPending || terminalPending) {
                publish(
                        transport,
                        run.authorization.homeBankingId(),
                        SmokeTestIntegrationContracts.FINISH_RESPONSE,
                        rejected(request.requestId(), request.runId(), "INTEGRATION_BUSY",
                                "Wait for the current Integration operation to finish."));
                return;
            }
            terminalPending = true;
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
                        terminalPending = false;
                    }
                });
    }

    private JsonObject finish(Run run, FinishRequest request) {
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
        Run run;
        synchronized (stateLock) {
            run = active != null && active.transport == transport ? active : null;
            if (run == null) return;
            run.cancelled = true;
        }
        try {
            worker.execute(() -> terminateSafely(run, RunStatus.STOPPED, "disconnect"));
        } catch (RejectedExecutionException shutdown) {
            terminateSafely(run, RunStatus.STOPPED, "disconnect-after-shutdown");
        }
    }

    /** Retires a run when the same detached window is rebound to a new Bot Job/binding epoch. */
    public void bindingChanged(Session transport, String currentBindingEpoch) {
        if (transport == null) return;
        Run run;
        synchronized (stateLock) {
            run = active != null
                            && active.transport == transport
                            && !active.authorization.bindingEpoch().equals(currentBindingEpoch)
                    ? active
                    : null;
            if (run == null) return;
            run.cancelled = true;
        }
        try {
            worker.execute(() -> terminateSafely(run, RunStatus.STOPPED, "binding-change"));
        } catch (RejectedExecutionException shutdown) {
            terminateSafely(run, RunStatus.STOPPED, "binding-change-after-shutdown");
        }
    }

    /** Terminal application cleanup. The browser itself is owned by the application lifecycle. */
    public void shutdown() {
        Run run;
        synchronized (stateLock) {
            run = active;
            if (run != null) run.cancelled = true;
        }
        try {
            if (run != null) terminateSafely(run, RunStatus.STOPPED, "service-shutdown");
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
            entry = new RequestLedgerEntry(operation, fingerprint);
            requestLedger.put(key, entry);
            trimRequestLedger();
        }

        try {
            worker.execute(() -> {
                JsonObject result;
                try {
                    result = task.run();
                } catch (Throwable failure) {
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
            Run run = active != null && active.transport == transport ? active : null;
            if (run != null) homeBankingId = run.authorization.homeBankingId();
            if (!existing.operation.equals(operation)
                    || !existing.fingerprint.equals(fingerprint)) {
                conflict = "This request ID was already used for another operation.";
            } else if (existing.response == null) {
                existing.waiters.add(new Waiter(transport, homeBankingId));
            } else {
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

    private Run requireRun(String runId, Session transport) {
        return active != null
                        && active.transport == transport
                        && active.runId.equals(runId)
                ? active
                : null;
    }

    private void terminate(Run expected, RunStatus terminalStatus) {
        synchronized (stateLock) {
            if (active != expected || expected.released.get()) return;
        }
        if (!expected.releaseInProgress.compareAndSet(false, true)) return;
        try {
            if (expected.v2Run != null) v2.close(expected.v2Run);
            if (expected.lease != null) expected.lease.close();
            expected.status = terminalStatus;
            expected.released.set(true);
            synchronized (stateLock) {
                if (active == expected) active = null;
                stepPending = false;
                terminalPending = false;
            }
        } catch (RuntimeException cleanupFailure) {
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
        String suffix = run.runtimeMode == RuntimeMode.TYPESCRIPT_PLAYWRIGHT_V2
                ? "The isolated Playwright session was closed."
                : "The Playwright page remains open.";
        return prefix + ". " + suffix;
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
        if (SmokeTestIntegrationContracts.REFRESH.equals(operation)) {
            return SmokeTestIntegrationContracts.REFRESH_RESPONSE;
        }
        if (SmokeTestIntegrationContracts.STOP.equals(operation)) {
            return SmokeTestIntegrationContracts.STOP_RESPONSE;
        }
        if (SmokeTestIntegrationContracts.FINISH.equals(operation)) {
            return SmokeTestIntegrationContracts.FINISH_RESPONSE;
        }
        return "smokeTest.integration.errorResponse";
    }

    private static ThreadPoolExecutor newWorker() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(4),
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

        boolean isCurrent(SmokeIntegrationAuthorization expected, Session transport);
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
            var current = driver.currentPlaywrightDriver();
            if (current != null && current.isOpen()
                    && !sameOrigin(current.currentUrl(), url)) {
                return false;
            }
            if (!driver.openBrowserPreservingCurrentPage(browserType, url, optionsConfig)) {
                return false;
            }
            current = driver.currentPlaywrightDriver();
            if (current == null || !current.isOpen()) return false;
            current.waitForPageSettled(15_000L);
            String currentUrl = current.currentUrl();
            return currentUrl != null
                    && !currentUrl.isBlank()
                    && !"about:blank".equalsIgnoreCase(currentUrl.trim())
                    && sameOrigin(currentUrl, url);
        }

        private static boolean sameOrigin(String currentUrl, String selectedUrl) {
            try {
                URI current = URI.create(currentUrl);
                URI selected = URI.create(selectedUrl);
                return equalIgnoreCase(current.getScheme(), selected.getScheme())
                        && equalIgnoreCase(current.getHost(), selected.getHost())
                        && effectivePort(current) == effectivePort(selected);
            } catch (IllegalArgumentException invalidUrl) {
                return false;
            }
        }

        private static int effectivePort(URI uri) {
            if (uri.getPort() >= 0) return uri.getPort();
            return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
        }

        private static boolean equalIgnoreCase(String left, String right) {
            return left != null && right != null && left.equalsIgnoreCase(right);
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
        public boolean isCurrent(
                SmokeIntegrationAuthorization expected, Session transport) {
            return delegate.isCurrentSmokeIntegrationBinding(expected, transport);
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

        @Override
        public Outcome execute(
                Plan plan,
                IntegrationDataset dataset,
                int instructionId,
                int excelRowIndex,
                RunVariables variables) {
            return delegate.execute(plan, dataset, instructionId, excelRowIndex, variables);
        }
    }

    private static final class DefaultV2Port implements V2Port {
        private final ExecutionRuntimeRunCoordinator coordinator;
        private final SmokeTestIntegrationV2StepExecutor executor;

        private DefaultV2Port() {
            coordinator = ExecutionRuntimeRunCoordinator.configured().orElse(null);
            executor = coordinator == null ? null : new SmokeTestIntegrationV2StepExecutor(coordinator);
        }

        @Override
        public V2Run start(
                SmokeIntegrationAuthorization authorization, Plan plan, String datasetMode) {
            if (coordinator == null) {
                throw new IllegalStateException("The TypeScript Playwright runtime is not configured.");
            }
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
        private final Session transport;
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
        private final java.util.concurrent.atomic.AtomicBoolean released =
                new java.util.concurrent.atomic.AtomicBoolean();
        private final java.util.concurrent.atomic.AtomicBoolean releaseInProgress =
                new java.util.concurrent.atomic.AtomicBoolean();
        private final LinkedHashMap<Long, SequenceResult> sequenceResults =
                new LinkedHashMap<>();
        private volatile boolean cancelled;
        private volatile RunStatus status = RunStatus.RUNNING;
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
                V2Run v2Run) {
            this.runId = runId;
            this.integrationEpoch = integrationEpoch;
            this.transport = transport;
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
        private final List<Waiter> waiters = new ArrayList<>();
        private JsonObject response;

        private RequestLedgerEntry(String operation, String fingerprint) {
            this.operation = operation;
            this.fingerprint = fingerprint;
        }
    }

    private record Waiter(Session transport, int homeBankingId) {}

    private record SequenceResult(int instructionId, int excelRowIndex, JsonObject response) {
        private boolean matches(StepRequest request) {
            return instructionId == request.instructionId()
                    && excelRowIndex == request.excelRowIndex();
        }
    }
}

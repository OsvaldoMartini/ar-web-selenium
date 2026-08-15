package com.allinweb.ch.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.Environment;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.Owner;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.Plan;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationStepExecutor.Outcome;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationStepExecutor.RunVariables;
import com.allinweb.ch.model.DetachedWorkspaceSessions;
import com.allinweb.ch.model.SmokeTestIntegrationContracts;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.Scope;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.StepDisposition;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.StepStatus;
import com.allinweb.ch.socket.ExcelDataWorkspaceService.IntegrationDataset;
import com.allinweb.ch.socket.VariablesWorkspaceService.SmokeIntegrationAuthorization;
import com.allinweb.ch.util.ExtractedData;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.websocket.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SmokeTestIntegrationServiceTest {
    private static final Owner OWNER = new Owner(2, 32);
    private static final long WORKSPACE_EPOCH = 7L;
    private static final String BINDING_EPOCH = "binding-epoch-7";
    private static final String GRAPH_REVISION = "a".repeat(64);
    private static final String PLAN_REVISION = "b".repeat(64);
    private static final String DATA_REVISION = "c".repeat(64);
    private static final int INSTRUCTION_ID = 1728;

    private Session transport;
    private ThreadPoolExecutor worker;
    private RecordingResponses responses;
    private RecordingBrowserOwnership browserOwnership;
    private RecordingSteps steps;
    private RecordingV2 v2;
    private AtomicReference<String> openedBrowserUrl;
    private AtomicInteger supportingWorkspacesReadyCalls;
    private RecordingBrowserStart browserStart;
    private SmokeTestIntegrationService service;

    @BeforeEach
    void setUp() {
        WebSocketSessionManager.clearSessions();
        transport = mock(Session.class);
        when(transport.isOpen()).thenReturn(true);
        assertTrue(WebSocketSessionManager.addSession(
                DetachedWorkspaceSessions.SMOKE_TEST_MANAGER, transport));

        worker = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>());
        responses = new RecordingResponses();
        browserOwnership = new RecordingBrowserOwnership();
        steps = new RecordingSteps();
        v2 = new RecordingV2();
        openedBrowserUrl = new AtomicReference<>();
        supportingWorkspacesReadyCalls = new AtomicInteger();
        browserStart = new RecordingBrowserStart(openedBrowserUrl, supportingWorkspacesReadyCalls);

        SmokeIntegrationAuthorization authorization = new SmokeIntegrationAuthorization(
                BINDING_EPOCH,
                WORKSPACE_EPOCH,
                OWNER.botJobId(),
                OWNER.homeBankingId(),
                "Lifecycle Bot Job",
                "Lifecycle Bank",
                GRAPH_REVISION);
        service = new SmokeTestIntegrationService(
                new Gson(),
                new SmokeTestIntegrationService.BindingPort() {
                    @Override
                    public SmokeIntegrationAuthorization authorize(
                            JsonObject request, Session candidate) {
                        return authorization;
                    }

                    @Override
                    public boolean isCurrent(
                            SmokeIntegrationAuthorization expected, Session candidate) {
                        return expected.equals(authorization) && candidate == transport;
                    }

                    @Override
                    public void requireSupportingWorkspacesReady(
                            SmokeIntegrationAuthorization expected, Session candidate) {
                        assertEquals(authorization, expected);
                        assertEquals(transport, candidate);
                        supportingWorkspacesReadyCalls.incrementAndGet();
                    }
                },
                (botJobId, mode) -> dataset(),
                (owner, scope) -> plan(),
                steps,
                v2,
                browserOwnership,
                (botJobId, workspaceEpoch) -> "IDLE",
                browserStart,
                responses,
                worker);
    }

    @AfterEach
    void tearDown() {
        if (service != null) service.shutdown();
        WebSocketSessionManager.clearSessions();
    }

    @Test
    void startStepAndCorrelatedFinishReleaseTheBrowserLease() throws Exception {
        JsonObject started = start("start-1");
        String runId = started.get("runId").getAsString();

        service.handle(
                SmokeTestIntegrationContracts.STEP,
                stepRequest("step-1", runId, 1L),
                DetachedWorkspaceSessions.SMOKE_TEST_MANAGER,
                transport);
        Published stepped = responses.await(SmokeTestIntegrationContracts.STEP_RESPONSE);

        assertTrue(stepped.body.get("ok").getAsBoolean());
        assertEquals(1, supportingWorkspacesReadyCalls.get());
        assertEquals(1, browserStart.readyCallsAtFirstOpen.get());
        assertEquals("step-1", stepped.body.get("requestId").getAsString());
        assertEquals(runId, stepped.body.get("runId").getAsString());
        assertEquals(1L, stepped.body.get("sequence").getAsLong());
        assertEquals(INSTRUCTION_ID, stepped.body.get("instructionId").getAsInt());
        assertEquals(1, steps.calls.get());

        service.handle(
                SmokeTestIntegrationContracts.FINISH,
                finishRequest("finish-1", runId, 1L),
                DetachedWorkspaceSessions.SMOKE_TEST_MANAGER,
                transport);
        Published finished = responses.await(SmokeTestIntegrationContracts.FINISH_RESPONSE);

        assertTrue(finished.body.get("ok").getAsBoolean());
        assertEquals("finish-1", finished.body.get("requestId").getAsString());
        assertEquals(runId, finished.body.get("runId").getAsString());
        assertEquals("FINISHED", finished.body.get("status").getAsString());
        assertEquals(1L, finished.body.get("lastSequence").getAsLong());
        assertTrue(browserOwnership.awaitClosed());
        assertEquals(1, browserOwnership.closes.get());
    }

    @Test
    void lastSequenceMismatchPreservesRunUntilStopAndReleasesExactlyOnce() throws Exception {
        JsonObject started = start("start-2");
        String runId = started.get("runId").getAsString();

        service.handle(
                SmokeTestIntegrationContracts.STEP,
                stepRequest("step-2", runId, 1L),
                DetachedWorkspaceSessions.SMOKE_TEST_MANAGER,
                transport);
        assertTrue(responses.await(SmokeTestIntegrationContracts.STEP_RESPONSE)
                .body.get("ok").getAsBoolean());

        service.handle(
                SmokeTestIntegrationContracts.FINISH,
                finishRequest("finish-mismatch", runId, 0L),
                DetachedWorkspaceSessions.SMOKE_TEST_MANAGER,
                transport);
        Published refused = responses.await(SmokeTestIntegrationContracts.FINISH_RESPONSE);

        assertFalse(refused.body.get("ok").getAsBoolean());
        assertEquals("LAST_SEQUENCE_MISMATCH", refused.body.get("code").getAsString());
        assertEquals(0, browserOwnership.closes.get(), "A refused finish must preserve the active run");

        service.handle(
                SmokeTestIntegrationContracts.STOP,
                stopRequest("stop-after-refusal", runId),
                DetachedWorkspaceSessions.SMOKE_TEST_MANAGER,
                transport);
        Published stopped = responses.await(SmokeTestIntegrationContracts.STOP_RESPONSE);

        assertTrue(stopped.body.get("ok").getAsBoolean());
        assertEquals("STOPPED", stopped.body.get("status").getAsString());
        assertEquals(1, browserOwnership.releaseRequests.get());
        assertTrue(browserOwnership.awaitClosed());
        assertEquals(1, browserOwnership.closes.get());

        service.disconnected(DetachedWorkspaceSessions.SMOKE_TEST_MANAGER, transport);
        worker.submit(() -> {}).get(5, TimeUnit.SECONDS);
        assertEquals(1, browserOwnership.closes.get(), "The released lease must remain idempotent");
    }

    @Test
    void disconnectReleasesTheActiveBrowserLeaseExactlyOnce() throws Exception {
        start("start-3");

        assertTrue(WebSocketSessionManager.removeSession(
                DetachedWorkspaceSessions.SMOKE_TEST_MANAGER, transport));
        service.disconnected(DetachedWorkspaceSessions.SMOKE_TEST_MANAGER, transport);

        assertTrue(browserOwnership.awaitClosed());
        assertEquals(1, browserOwnership.releaseRequests.get());
        service.disconnected(DetachedWorkspaceSessions.SMOKE_TEST_MANAGER, transport);
        worker.submit(() -> {}).get(5, TimeUnit.SECONDS);
        assertEquals(1, browserOwnership.closes.get());
    }

    @Test
    void duplicateStopRequestsJoinCleanupAndBothReceiveStopped() throws Exception {
        JsonObject started = start("start-duplicate-stop");
        String runId = started.get("runId").getAsString();
        CountDownLatch releaseWorker = new CountDownLatch(1);
        worker.execute(() -> {
            try {
                releaseWorker.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });

        service.handle(
                SmokeTestIntegrationContracts.STOP,
                stopRequest("stop-first", runId),
                DetachedWorkspaceSessions.SMOKE_TEST_MANAGER,
                transport);
        service.handle(
                SmokeTestIntegrationContracts.STOP,
                stopRequest("stop-second", runId),
                DetachedWorkspaceSessions.SMOKE_TEST_MANAGER,
                transport);
        releaseWorker.countDown();

        Published first = responses.await(SmokeTestIntegrationContracts.STOP_RESPONSE);
        Published second = responses.await(SmokeTestIntegrationContracts.STOP_RESPONSE);
        assertTrue(first.body.get("ok").getAsBoolean());
        assertTrue(second.body.get("ok").getAsBoolean());
        assertEquals("STOPPED", first.body.get("status").getAsString());
        assertEquals("STOPPED", second.body.get("status").getAsString());
        assertTrue(browserOwnership.awaitClosed());
        assertEquals(1, browserOwnership.closes.get());
    }

    @Test
    void explicitV2RunNeverOpensOrExecutesThroughTheSharedJavaBrowser() throws Exception {
        JsonObject request = startRequest("start-v2");
        request.addProperty("runtimeMode", "TYPESCRIPT_PLAYWRIGHT_V2");
        request.addProperty("pagePolicy", "RELOAD_SELECTED");
        service.handle(
                SmokeTestIntegrationContracts.START,
                request,
                DetachedWorkspaceSessions.SMOKE_TEST_MANAGER,
                transport);
        JsonObject started = responses.await(SmokeTestIntegrationContracts.START_RESPONSE).body;
        String runId = started.get("runId").getAsString();

        assertTrue(started.get("ok").getAsBoolean());
        assertEquals("TYPESCRIPT_PLAYWRIGHT_V2", started.get("runtimeMode").getAsString());
        assertEquals("RELOAD_SELECTED", started.get("pagePolicy").getAsString());
        assertEquals("runtime-v2-run", runId);
        assertEquals(1, v2.starts.get());
        assertEquals(0, browserOwnership.closes.get());
        assertEquals(null, openedBrowserUrl.get());

        service.handle(
                SmokeTestIntegrationContracts.STEP,
                stepRequest("step-v2", runId, 1L),
                DetachedWorkspaceSessions.SMOKE_TEST_MANAGER,
                transport);
        assertTrue(responses.await(SmokeTestIntegrationContracts.STEP_RESPONSE)
                .body.get("ok").getAsBoolean());
        assertEquals(1, v2.steps.get());
        assertEquals(0, steps.calls.get());

        service.handle(
                SmokeTestIntegrationContracts.FINISH,
                finishRequest("finish-v2", runId, 1L),
                DetachedWorkspaceSessions.SMOKE_TEST_MANAGER,
                transport);
        assertTrue(responses.await(SmokeTestIntegrationContracts.FINISH_RESPONSE)
                .body.get("ok").getAsBoolean());
        assertEquals(1, v2.closes.get());
        assertEquals(0, browserOwnership.closes.get());
    }

    @Test
    void v2RecoveryCanBypassAnInstructionWithNoCandidatesAndContinue() throws Exception {
        JsonObject recovery = new JsonObject();
        recovery.addProperty("state", "AWAITING_USER");
        recovery.add("candidates", new com.google.gson.JsonArray());
        v2.nextOutcome.set(new Outcome(
                StepStatus.FAILED,
                StepDisposition.PHYSICAL,
                "TARGET_NOT_FOUND",
                "No matching element was found.",
                null,
                null,
                recovery));

        JsonObject request = startRequest("start-v2-bypass");
        request.addProperty("runtimeMode", "TYPESCRIPT_PLAYWRIGHT_V2");
        request.addProperty("pagePolicy", "RELOAD_SELECTED");
        service.handle(
                SmokeTestIntegrationContracts.START,
                request,
                DetachedWorkspaceSessions.SMOKE_TEST_MANAGER,
                transport);
        String runId = responses.await(SmokeTestIntegrationContracts.START_RESPONSE)
                .body.get("runId").getAsString();

        service.handle(
                SmokeTestIntegrationContracts.STEP,
                stepRequest("step-v2-bypass", runId, 1L),
                DetachedWorkspaceSessions.SMOKE_TEST_MANAGER,
                transport);
        Published failed = responses.await(SmokeTestIntegrationContracts.STEP_RESPONSE);
        assertEquals("FAILED", failed.body.get("status").getAsString());
        assertTrue(failed.body.getAsJsonObject("recovery")
                .getAsJsonArray("candidates").isEmpty());

        service.handle(
                SmokeTestIntegrationContracts.RECOVER,
                recoveryRequest("recover-v2-bypass", runId, 1L, "BYPASS"),
                DetachedWorkspaceSessions.SMOKE_TEST_MANAGER,
                transport);
        Published bypassed = responses.await(SmokeTestIntegrationContracts.RECOVER_RESPONSE);
        assertTrue(bypassed.body.get("ok").getAsBoolean());
        assertEquals("BYPASSED", bypassed.body.get("status").getAsString());
        assertEquals("RECOVERY_BYPASSED", bypassed.body.get("code").getAsString());
        assertEquals(1, v2.cancelledRecoveries.get());

        service.handle(
                SmokeTestIntegrationContracts.FINISH,
                finishRequest("finish-v2-bypass", runId, 1L),
                DetachedWorkspaceSessions.SMOKE_TEST_MANAGER,
                transport);
        Published finished = responses.await(SmokeTestIntegrationContracts.FINISH_RESPONSE);
        assertEquals(0, finished.body.get("failed").getAsInt());
        assertEquals(1, finished.body.get("skipped").getAsInt());
    }

    @Test
    void v2DisabledRecoveryVerificationCancelsTheRuntimeHoldAndBypassesImmediately()
            throws Exception {
        JsonObject recovery = new JsonObject();
        recovery.addProperty("state", "AWAITING_USER");
        recovery.add("candidates", new com.google.gson.JsonArray());
        v2.nextOutcome.set(new Outcome(
                StepStatus.FAILED,
                StepDisposition.PHYSICAL,
                "TARGET_NOT_FOUND",
                "No matching element was found.",
                null,
                null,
                recovery));

        JsonObject request = startRequest("start-v2-recovery-off");
        request.addProperty("runtimeMode", "TYPESCRIPT_PLAYWRIGHT_V2");
        request.addProperty("pagePolicy", "RELOAD_SELECTED");
        service.handle(
                SmokeTestIntegrationContracts.START,
                request,
                DetachedWorkspaceSessions.SMOKE_TEST_MANAGER,
                transport);
        String runId = responses.await(SmokeTestIntegrationContracts.START_RESPONSE)
                .body.get("runId").getAsString();

        JsonObject step = stepRequest("step-v2-recovery-off", runId, 1L);
        step.addProperty("recoveryVerificationEnabled", false);
        service.handle(
                SmokeTestIntegrationContracts.STEP,
                step,
                DetachedWorkspaceSessions.SMOKE_TEST_MANAGER,
                transport);

        Published bypassed = responses.await(SmokeTestIntegrationContracts.STEP_RESPONSE);
        assertTrue(bypassed.body.get("ok").getAsBoolean());
        assertEquals("SKIPPED", bypassed.body.get("status").getAsString());
        assertEquals("RECOVERY_BYPASSED", bypassed.body.get("code").getAsString());
        assertFalse(bypassed.body.get("recoveryVerificationEnabled").getAsBoolean());
        assertFalse(bypassed.body.has("recovery"));
        assertEquals(1, v2.cancelledRecoveries.get());

        service.handle(
                SmokeTestIntegrationContracts.STEP,
                stepRequest("step-v2-recovery-on-conflict", runId, 1L),
                DetachedWorkspaceSessions.SMOKE_TEST_MANAGER,
                transport);
        Published conflict = responses.await(SmokeTestIntegrationContracts.STEP_RESPONSE);
        assertFalse(conflict.body.get("ok").getAsBoolean());
        assertEquals("SEQUENCE_CONFLICT", conflict.body.get("code").getAsString());
    }

    @Test
    void v2StopRequestsImmediateRuntimeInterruptionBeforeTerminalCleanup() throws Exception {
        JsonObject request = startRequest("start-v2-stop");
        request.addProperty("runtimeMode", "TYPESCRIPT_PLAYWRIGHT_V2");
        request.addProperty("pagePolicy", "RELOAD_SELECTED");
        service.handle(
                SmokeTestIntegrationContracts.START,
                request,
                DetachedWorkspaceSessions.SMOKE_TEST_MANAGER,
                transport);
        String runId = responses.await(SmokeTestIntegrationContracts.START_RESPONSE)
                .body.get("runId").getAsString();

        service.handle(
                SmokeTestIntegrationContracts.STOP,
                stopRequest("stop-v2", runId),
                DetachedWorkspaceSessions.SMOKE_TEST_MANAGER,
                transport);
        Published stopped = responses.await(SmokeTestIntegrationContracts.STOP_RESPONSE);

        assertTrue(stopped.body.get("ok").getAsBoolean());
        assertEquals("STOPPED", stopped.body.get("status").getAsString());
        assertEquals(1, v2.interrupts.get());
        assertEquals(1, v2.closes.get());
        assertEquals(0, browserOwnership.closes.get());
    }

    @Test
    void runtimeInstancesExposeTheAuthoritativeActiveRun() throws Exception {
        JsonObject started = start("start-runtime-inventory");
        String runId = started.get("runId").getAsString();

        JsonObject request = new JsonObject();
        request.addProperty("contractVersion", SmokeTestIntegrationContracts.CONTRACT_VERSION);
        request.addProperty("requestId", "runtime-inventory-1");
        service.handle(
                "smokeTest.integration.runtimeInstances",
                request,
                DetachedWorkspaceSessions.SMOKE_TEST_MANAGER,
                transport);

        Published inventory = responses.await("smokeTest.integration.runtimeInstancesResponse");
        assertTrue(inventory.body.get("ok").getAsBoolean());
        assertEquals(1, inventory.body.getAsJsonArray("instances").size());
        JsonObject instance = inventory.body.getAsJsonArray("instances").get(0).getAsJsonObject();
        assertEquals(runId, instance.get("runId").getAsString());
        assertEquals("JAVA_V1", instance.get("runtimeMode").getAsString());
        assertEquals(OWNER.botJobId(), instance.get("botJobId").getAsInt());
        assertEquals("RUNNING", instance.get("status").getAsString());
    }

    @Test
    void stopInterruptsABlockedV1StepWithoutWaitingForTheStepTimeout() throws Exception {
        JsonObject started = start("start-blocked-v1");
        String runId = started.get("runId").getAsString();
        steps.blockNextCall();
        service.handle(
                SmokeTestIntegrationContracts.STEP,
                stepRequest("blocked-step", runId, 1L),
                DetachedWorkspaceSessions.SMOKE_TEST_MANAGER,
                transport);
        assertTrue(steps.awaitBlocked(), "The V1 step did not enter its blocking action");

        service.handle(
                SmokeTestIntegrationContracts.STOP,
                stopRequest("stop-blocked-v1", runId),
                DetachedWorkspaceSessions.SMOKE_TEST_MANAGER,
                transport);

        Published interruptedStep = responses.awaitWithin(
                SmokeTestIntegrationContracts.STEP_RESPONSE, 1, TimeUnit.SECONDS);
        assertEquals("INTEGRATION_STOPPING", interruptedStep.body.get("code").getAsString());
        Published stopped = responses.awaitWithin(
                SmokeTestIntegrationContracts.STOP_RESPONSE, 1, TimeUnit.SECONDS);
        assertTrue(stopped.body.get("ok").getAsBoolean());
        assertEquals("STOPPED", stopped.body.get("status").getAsString());
        assertTrue(steps.awaitInterrupted(), "The active V1 instruction was not interrupted");
    }

    @Test
    void emergencyStopInterruptsV1StartupBeforeARunIdExists() throws Exception {
        browserStart.blockNextOpen();
        service.handle(
                SmokeTestIntegrationContracts.START,
                startRequest("start-blocked-before-run-id"),
                DetachedWorkspaceSessions.SMOKE_TEST_MANAGER,
                transport);
        assertTrue(browserStart.awaitBlocked(), "V1 startup did not enter browser navigation");

        service.handle(
                SmokeTestIntegrationContracts.FORCE_STOP,
                forceStopRequest("force-stop-pending-start"),
                DetachedWorkspaceSessions.SMOKE_TEST_MANAGER,
                transport);

        Published forced = responses.awaitWithin(
                SmokeTestIntegrationContracts.FORCE_STOP_RESPONSE, 1, TimeUnit.SECONDS);
        assertTrue(forced.body.get("ok").getAsBoolean());
        assertEquals("STOP_REQUESTED", forced.body.get("status").getAsString());
        assertEquals(1, forced.body.get("pendingStartsCancelled").getAsInt());
        assertEquals(0, forced.body.get("activeRunsInterrupted").getAsInt());
        assertEquals(1, steps.forceStops.get());
        assertTrue(browserStart.awaitInterrupted(), "V1 browser startup was not interrupted");

        Published cancelledStart = responses.awaitWithin(
                SmokeTestIntegrationContracts.START_RESPONSE, 1, TimeUnit.SECONDS);
        assertFalse(cancelledStart.body.get("ok").getAsBoolean());

        service.handle(
                SmokeTestIntegrationContracts.START,
                startRequest("start-after-emergency-stop"),
                DetachedWorkspaceSessions.SMOKE_TEST_MANAGER,
                transport);
        Published restarted = responses.await(SmokeTestIntegrationContracts.START_RESPONSE);
        assertTrue(restarted.body.get("ok").getAsBoolean());
    }

    @Test
    void emergencyStopInterruptsTheCurrentOwnersActiveV2Run() throws Exception {
        JsonObject request = startRequest("start-v2-emergency-stop");
        request.addProperty("runtimeMode", "TYPESCRIPT_PLAYWRIGHT_V2");
        request.addProperty("pagePolicy", "RELOAD_SELECTED");
        service.handle(
                SmokeTestIntegrationContracts.START,
                request,
                DetachedWorkspaceSessions.SMOKE_TEST_MANAGER,
                transport);
        assertTrue(responses.await(SmokeTestIntegrationContracts.START_RESPONSE)
                .body.get("ok").getAsBoolean());

        service.handle(
                SmokeTestIntegrationContracts.FORCE_STOP,
                forceStopRequest("force-stop-active-v2"),
                DetachedWorkspaceSessions.SMOKE_TEST_MANAGER,
                transport);

        Published forced = responses.await(SmokeTestIntegrationContracts.FORCE_STOP_RESPONSE);
        assertTrue(forced.body.get("ok").getAsBoolean());
        assertEquals("STOP_REQUESTED", forced.body.get("status").getAsString());
        assertEquals(0, forced.body.get("pendingStartsCancelled").getAsInt());
        assertEquals(1, forced.body.get("activeRunsInterrupted").getAsInt());
        assertEquals(1, v2.interrupts.get());
        worker.submit(() -> {}).get(5, TimeUnit.SECONDS);
        assertEquals(1, v2.closes.get());
    }

    @Test
    void killTargetsOnlyTheRequestedRuntimeInstance() throws Exception {
        JsonObject started = start("start-runtime-kill");
        String runId = started.get("runId").getAsString();

        JsonObject request = stopRequest("runtime-kill-1", runId);
        request.addProperty("action", "KILL");
        service.handle(
                "smokeTest.integration.runtimeInstanceControl",
                request,
                DetachedWorkspaceSessions.SMOKE_TEST_MANAGER,
                transport);

        Published killed = responses.await("smokeTest.integration.runtimeInstanceControlResponse");
        assertTrue(killed.body.get("ok").getAsBoolean());
        assertEquals(runId, killed.body.get("runId").getAsString());
        assertEquals("KILLED", killed.body.get("status").getAsString());
        assertEquals(1, steps.forceStops.get());
        assertTrue(browserOwnership.awaitClosed());
    }

    @Test
    void bindingChangeInterruptsAndRetiresTheSupersededV2Run() throws Exception {
        JsonObject request = startRequest("start-v2-binding-change");
        request.addProperty("runtimeMode", "TYPESCRIPT_PLAYWRIGHT_V2");
        request.addProperty("pagePolicy", "RELOAD_SELECTED");
        service.handle(
                SmokeTestIntegrationContracts.START,
                request,
                DetachedWorkspaceSessions.SMOKE_TEST_MANAGER,
                transport);
        JsonObject started = responses.await(SmokeTestIntegrationContracts.START_RESPONSE).body;
        assertTrue(started.get("ok").getAsBoolean());

        service.bindingChanged(transport, "replacement-binding");
        worker.submit(() -> {}).get(5, TimeUnit.SECONDS);

        assertEquals(1, v2.interrupts.get());
        assertEquals(1, v2.closes.get());

        service.handle(
                SmokeTestIntegrationContracts.RUNTIME_INSTANCES,
                forceStopRequest("instances-after-binding-change"),
                DetachedWorkspaceSessions.SMOKE_TEST_MANAGER,
                transport);
        JsonObject inventory = responses.await(
                SmokeTestIntegrationContracts.RUNTIME_INSTANCES_RESPONSE).body;
        assertEquals(0, inventory.getAsJsonArray("instances").size());
    }

    @Test
    void reloadPolicyUsesStrictSelectedPageInsteadOfPreservingTheCurrentPage() throws Exception {
        JsonObject request = startRequest("start-reload");
        request.addProperty("pagePolicy", "RELOAD_SELECTED");
        service.handle(
                SmokeTestIntegrationContracts.START,
                request,
                DetachedWorkspaceSessions.SMOKE_TEST_MANAGER,
                transport);
        JsonObject started = responses.await(SmokeTestIntegrationContracts.START_RESPONSE).body;

        assertTrue(started.get("ok").getAsBoolean());
        assertEquals("RELOAD_SELECTED", started.get("pagePolicy").getAsString());
        assertEquals(1, browserStart.selected.get());
        assertEquals(0, browserStart.preserved.get());
    }

    private JsonObject start(String requestId) throws InterruptedException {
        service.handle(
                SmokeTestIntegrationContracts.START,
                startRequest(requestId),
                DetachedWorkspaceSessions.SMOKE_TEST_MANAGER,
                transport);
        Published response = responses.await(SmokeTestIntegrationContracts.START_RESPONSE);
        assertTrue(response.body.get("ok").getAsBoolean());
        assertEquals(requestId, response.body.get("requestId").getAsString());
        assertEquals(OWNER.homeBankingId(), response.homeBankingId);
        assertEquals("PRESERVE_ACTIVE", response.body.get("pagePolicy").getAsString());
        assertEquals("https://example.test", openedBrowserUrl.get());
        assertEquals(0, browserStart.selected.get());
        assertEquals(1, browserStart.preserved.get());
        return response.body;
    }

    private static JsonObject startRequest(String requestId) {
        JsonObject request = new JsonObject();
        request.addProperty("contractVersion", SmokeTestIntegrationContracts.CONTRACT_VERSION);
        request.addProperty("requestId", requestId);
        request.addProperty("bindingEpoch", BINDING_EPOCH);
        request.addProperty("workspaceEpoch", WORKSPACE_EPOCH);
        request.addProperty("homeBankingId", OWNER.homeBankingId());
        request.addProperty("botJobId", OWNER.botJobId());
        request.addProperty("graphRevision", GRAPH_REVISION);
        JsonObject scope = new JsonObject();
        scope.addProperty("kind", "ALL");
        request.add("scope", scope);
        request.addProperty("excelMode", "REAL");
        request.addProperty("pagePolicy", "PRESERVE_ACTIVE");
        request.addProperty("durableRuntimeWrites", false);
        return request;
    }

    private static JsonObject stepRequest(String requestId, String runId, long sequence) {
        JsonObject request = new JsonObject();
        request.addProperty("contractVersion", SmokeTestIntegrationContracts.CONTRACT_VERSION);
        request.addProperty("requestId", requestId);
        request.addProperty("runId", runId);
        request.addProperty("sequence", sequence);
        request.addProperty("instructionId", INSTRUCTION_ID);
        request.addProperty("excelRowIndex", 0);
        return request;
    }

    private static JsonObject finishRequest(String requestId, String runId, long lastSequence) {
        JsonObject request = stopRequest(requestId, runId);
        request.addProperty("lastSequence", lastSequence);
        return request;
    }

    private static JsonObject recoveryRequest(
            String requestId, String runId, long sequence, String decision) {
        JsonObject request = stepRequest(requestId, runId, sequence);
        request.remove("excelRowIndex");
        request.addProperty("recoveryCandidateId", "");
        request.addProperty("decision", decision);
        return request;
    }

    private static JsonObject stopRequest(String requestId, String runId) {
        JsonObject request = new JsonObject();
        request.addProperty("contractVersion", SmokeTestIntegrationContracts.CONTRACT_VERSION);
        request.addProperty("requestId", requestId);
        request.addProperty("runId", runId);
        return request;
    }

    private static JsonObject forceStopRequest(String requestId) {
        JsonObject request = new JsonObject();
        request.addProperty("contractVersion", SmokeTestIntegrationContracts.CONTRACT_VERSION);
        request.addProperty("requestId", requestId);
        request.addProperty("bindingEpoch", BINDING_EPOCH);
        request.addProperty("workspaceEpoch", WORKSPACE_EPOCH);
        request.addProperty("homeBankingId", OWNER.homeBankingId());
        request.addProperty("botJobId", OWNER.botJobId());
        request.addProperty("graphRevision", GRAPH_REVISION);
        return request;
    }

    private static Plan plan() {
        Environment environment = new Environment(
                OWNER.homeBankingId(),
                "Lifecycle Bank",
                OWNER.botJobId(),
                "Lifecycle Bot Job",
                "",
                1,
                "TEST",
                "https://example.test",
                "",
                "Chromium");
        return new Plan(
                OWNER,
                environment,
                Scope.all(),
                List.of(),
                List.of(),
                PLAN_REVISION);
    }

    private static IntegrationDataset dataset() {
        return new IntegrationDataset(
                OWNER.botJobId(),
                OWNER.homeBankingId(),
                "REAL",
                1L,
                0L,
                DATA_REVISION,
                Instant.parse("2026-08-06T00:00:00Z"),
                new ExtractedData());
    }

    private static final class RecordingSteps implements SmokeTestIntegrationService.StepPort {
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicInteger forceStops = new AtomicInteger();
        private final AtomicReference<CountDownLatch> blocking = new AtomicReference<>();
        private final CountDownLatch blocked = new CountDownLatch(1);
        private final CountDownLatch interrupted = new CountDownLatch(1);

        @Override
        public Outcome execute(
                Plan plan,
                IntegrationDataset dataset,
                int instructionId,
                int excelRowIndex,
                com.allinweb.ch.facade.execution.SmokeTestIntegrationStepExecutor.RunVariables variables) {
            calls.incrementAndGet();
            CountDownLatch latch = blocking.getAndSet(null);
            if (latch != null) {
                blocked.countDown();
                try {
                    latch.await();
                } catch (InterruptedException stopped) {
                    Thread.currentThread().interrupt();
                    interrupted.countDown();
                    return new Outcome(
                            StepStatus.SKIPPED,
                            StepDisposition.PHYSICAL,
                            "ACTION_CANCELLED",
                            "The V1 action was interrupted.",
                            null,
                            null);
                }
            }
            return new Outcome(
                    StepStatus.PASSED,
                    StepDisposition.LOGICAL_ONLY,
                    "STEP_ACCEPTED",
                    "The correlated step completed.",
                    null,
                    null);
        }

        @Override
        public void forceStop() {
            forceStops.incrementAndGet();
            CountDownLatch latch = blocking.getAndSet(null);
            if (latch != null) latch.countDown();
        }

        private void blockNextCall() {
            blocking.set(new CountDownLatch(1));
        }

        private boolean awaitBlocked() throws InterruptedException {
            return blocked.await(5, TimeUnit.SECONDS);
        }

        private boolean awaitInterrupted() throws InterruptedException {
            return interrupted.await(5, TimeUnit.SECONDS);
        }
    }

    private static final class RecordingV2 implements SmokeTestIntegrationService.V2Port {
        private final AtomicInteger starts = new AtomicInteger();
        private final AtomicInteger steps = new AtomicInteger();
        private final AtomicInteger interrupts = new AtomicInteger();
        private final AtomicInteger closes = new AtomicInteger();
        private final AtomicInteger cancelledRecoveries = new AtomicInteger();
        private final AtomicReference<Outcome> nextOutcome = new AtomicReference<>();

        @Override
        public SmokeTestIntegrationService.V2Run start(
                SmokeIntegrationAuthorization authorization, Plan plan, String datasetMode) {
            starts.incrementAndGet();
            return new SmokeTestIntegrationService.V2Run("runtime-v2-run", new Object());
        }

        @Override
        public Outcome execute(
                SmokeTestIntegrationService.V2Run run,
                Plan plan,
                IntegrationDataset dataset,
                long sequence,
                int instructionId,
                int excelRowIndex,
                RunVariables variables) {
            steps.incrementAndGet();
            Outcome configured = nextOutcome.getAndSet(null);
            return configured == null ? new Outcome(
                    StepStatus.PASSED,
                    StepDisposition.PHYSICAL,
                    "COMPLETED",
                    "V2 completed.",
                    null,
                    null) : configured;
        }

        @Override
        public void cancelRecovery(
                SmokeTestIntegrationService.V2Run run, int instructionId) {
            cancelledRecoveries.incrementAndGet();
        }

        @Override
        public void close(SmokeTestIntegrationService.V2Run run) {
            closes.incrementAndGet();
        }

        @Override
        public void interrupt(SmokeTestIntegrationService.V2Run run) {
            interrupts.incrementAndGet();
        }
    }

    private static final class RecordingBrowserStart
            implements SmokeTestIntegrationService.BrowserStartPort {
        private final AtomicReference<String> openedUrl;
        private final AtomicInteger runtimeReadyCalls;
        private final AtomicInteger readyCallsAtFirstOpen = new AtomicInteger(-1);
        private final AtomicInteger selected = new AtomicInteger();
        private final AtomicInteger preserved = new AtomicInteger();
        private final AtomicReference<CountDownLatch> blocking = new AtomicReference<>();
        private final CountDownLatch blocked = new CountDownLatch(1);
        private final CountDownLatch interrupted = new CountDownLatch(1);

        private RecordingBrowserStart(
                AtomicReference<String> openedUrl, AtomicInteger runtimeReadyCalls) {
            this.openedUrl = openedUrl;
            this.runtimeReadyCalls = runtimeReadyCalls;
        }

        @Override
        public boolean openSelectedPageAndWait(
                String browserType, String url, String optionsConfig) {
            selected.incrementAndGet();
            readyCallsAtFirstOpen.compareAndSet(-1, runtimeReadyCalls.get());
            openedUrl.set(url);
            return true;
        }

        @Override
        public boolean openPreservingCurrentPageAndWait(
                String browserType, String url, String optionsConfig) {
            preserved.incrementAndGet();
            readyCallsAtFirstOpen.compareAndSet(-1, runtimeReadyCalls.get());
            openedUrl.set(url);
            CountDownLatch latch = blocking.getAndSet(null);
            if (latch != null) {
                blocked.countDown();
                try {
                    latch.await();
                } catch (InterruptedException stopped) {
                    Thread.currentThread().interrupt();
                    interrupted.countDown();
                    return false;
                }
            }
            return true;
        }

        private void blockNextOpen() {
            blocking.set(new CountDownLatch(1));
        }

        private boolean awaitBlocked() throws InterruptedException {
            return blocked.await(5, TimeUnit.SECONDS);
        }

        private boolean awaitInterrupted() throws InterruptedException {
            return interrupted.await(5, TimeUnit.SECONDS);
        }
    }

    private static final class RecordingBrowserOwnership
            implements SmokeTestIntegrationService.BrowserOwnershipPort {
        private final AtomicInteger closes = new AtomicInteger();
        private final AtomicInteger releaseRequests = new AtomicInteger();
        private final CountDownLatch closed = new CountDownLatch(1);

        @Override
        public SmokeTestIntegrationService.BrowserLease reserve() {
            return () -> {
                closes.incrementAndGet();
                closed.countDown();
            };
        }

        @Override
        public boolean requestRelease() {
            releaseRequests.incrementAndGet();
            return true;
        }

        private boolean awaitClosed() throws InterruptedException {
            return closed.await(5, TimeUnit.SECONDS);
        }
    }

    private static final class RecordingResponses implements SmokeTestIntegrationService.ResponsePort {
        private final LinkedBlockingQueue<Published> published = new LinkedBlockingQueue<>();

        @Override
        public void publish(
                Session candidate,
                int homeBankingId,
                String operation,
                JsonObject response) {
            published.add(new Published(
                    candidate, homeBankingId, operation, response.deepCopy()));
        }

        private Published await(String expectedOperation) throws InterruptedException {
            return awaitWithin(expectedOperation, 5, TimeUnit.SECONDS);
        }

        private Published awaitWithin(
                String expectedOperation, long timeout, TimeUnit unit) throws InterruptedException {
            Published next = published.poll(timeout, unit);
            assertTrue(next != null, "Timed out waiting for " + expectedOperation);
            assertEquals(expectedOperation, next.operation);
            return next;
        }
    }

    private record Published(
            Session transport, int homeBankingId, String operation, JsonObject body) {}
}

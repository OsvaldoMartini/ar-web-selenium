package com.allinweb.ch.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.allinweb.ch.db.InstructionGraphStateRepository.OwnerKey;
import com.allinweb.ch.facade.BotJobDetailsWorkspaceRegistry;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.CommitResult;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.GraphInstructionFact;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.GraphSnapshot;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.GraphVariableFact;
import com.allinweb.ch.facade.ScannerBotJobTasksPublisher;
import com.allinweb.ch.facade.VariablesInstructionCopyTransaction.CopyResult;
import com.allinweb.ch.facade.VariablesVariableDeleteTransaction.DeleteResult;
import com.allinweb.ch.facade.actions.RuntimeVariableMemoryRegistry;
import com.allinweb.ch.facade.actions.RuntimeVariableMemoryRegistry.BotJobKey;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.DetachedWorkspaceSessions;
import com.allinweb.ch.model.InstructionGraphMutationV3;
import com.allinweb.ch.model.InstructionGraphMutationV3.LayoutRow;
import com.allinweb.ch.model.VariablesInstructionCopyV1;
import com.allinweb.ch.model.VariablesWorkspaceVariableDelete;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.websocket.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class VariablesWorkspaceServiceTest {
    private final FakeWorkspaces workspaces = new FakeWorkspaces();
    private final FakeGraphs graphs = new FakeGraphs();
    private final FakeWindows windows = new FakeWindows();
    private final FakeTasks tasks = new FakeTasks();
    private VariablesWorkspaceService service;

    @BeforeEach
    void setUp() {
        workspaces.add(new VariablesWorkspaceService.WorkspaceContext(
                10L, 5, 2, "Job Five", "Bank"));
        workspaces.add(new VariablesWorkspaceService.WorkspaceContext(
                11L, 6, 3, "Job Six", "Other Bank"));
        graphs.responses.put(5, graph("revision-five"));
        graphs.responses.put(6, graph("revision-six"));
        service =
                new VariablesWorkspaceService(workspaces, graphs, windows, new Gson(), tasks);
    }

    @Test
    void opensFromCanonicalBotJobAndBootstrapsOnlyRegisteredManager() {
        JsonObject opened = service.openForBotJob(5);

        assertTrue(opened.get("ok").getAsBoolean());
        assertEquals(1, windows.launches);
        assertEquals(5, windows.lastBotJobId);
        assertEquals(10L, opened.get("workspaceEpoch").getAsLong());

        Session manager = openSession();
        windows.register(manager);
        service.connected(VariablesWorkspaceService.WORKSPACE_SESSION_ID, manager);
        JsonObject request = new JsonObject();
        request.addProperty("requestId", "bootstrap-1");
        JsonObject bootstrap = service.bootstrap(
                request, VariablesWorkspaceService.WORKSPACE_SESSION_ID, manager);

        assertTrue(bootstrap.get("ok").getAsBoolean());
        assertEquals("bootstrap-1", bootstrap.get("requestId").getAsString());
        assertEquals(5, bootstrap.getAsJsonObject("botJob").get("id").getAsInt());
        assertEquals("Bank", bootstrap.getAsJsonObject("botJob")
                .get("organizationName")
                .getAsString());

        Session forged = openSession();
        JsonObject refused = service.bootstrap(
                new JsonObject(), VariablesWorkspaceService.WORKSPACE_SESSION_ID, forged);
        assertFalse(refused.get("ok").getAsBoolean());
        assertTrue(refused.get("preserveSnapshot").getAsBoolean());
        assertEquals(5, refused.get("botJobId").getAsInt());
    }

    @Test
    void retargetsTheExistingSingletonAndRotatesBindingEpoch() {
        JsonObject first = service.openForBotJob(5);
        JsonObject second = service.openForBotJob(6);

        assertTrue(first.get("ok").getAsBoolean());
        assertTrue(second.get("ok").getAsBoolean());
        assertEquals(1, windows.launches);
        assertEquals(2, windows.openOrFocusCalls);
        assertTrue(second.get("alreadyOpen").getAsBoolean());
        assertTrue(second.get("retargeted").getAsBoolean());
        assertNotEquals(
                first.get("bindingEpoch").getAsString(),
                second.get("bindingEpoch").getAsString());
        assertEquals(6, windows.lastBotJobId);
        assertEquals(
                VariablesWorkspaceService.SNAPSHOT_OPERATION,
                windows.sent.get(0).operationId());
    }

    @Test
    void integrationWaitsUntilExactRuntimeVariablesSnapshotIsRendered() throws Exception {
        JsonObject smokeOpened = service.openSmokeTestForBotJob(5);
        assertTrue(smokeOpened.get("ok").getAsBoolean());
        Session smoke = openSession();
        windows.register(DetachedWorkspaceSessions.SMOKE_TEST_MANAGER, smoke);
        JsonObject smokeRequest = new JsonObject();
        smokeRequest.addProperty("botJobId", 5);
        JsonObject smokeSnapshot = service.smokeTestBootstrap(
                smokeRequest, DetachedWorkspaceSessions.SMOKE_TEST_MANAGER, smoke);
        assertTrue(smokeSnapshot.get("ok").getAsBoolean());
        VariablesWorkspaceService.SmokeIntegrationAuthorization authorization =
                new VariablesWorkspaceService.SmokeIntegrationAuthorization(
                        smokeSnapshot.get("bindingEpoch").getAsString(),
                        smokeSnapshot.get("workspaceEpoch").getAsLong(),
                        5,
                        2,
                        "Job Five",
                        "Bank",
                        smokeSnapshot.get("graphRevision").getAsString());

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> waiting = executor.submit(() ->
                    service.requireRuntimeVariablesReadyForSmokeIntegration(
                            authorization, smoke));
            assertTrue(windows.runtimeVariablesOpened.await(2, TimeUnit.SECONDS));

            Session runtime = openSession();
            windows.register(DetachedWorkspaceSessions.RUNTIME_VARIABLES_MANAGER, runtime);
            JsonObject runtimeRequest = new JsonObject();
            runtimeRequest.addProperty("botJobId", 5);
            JsonObject runtimeSnapshot = service.runtimeVariablesBootstrap(
                    runtimeRequest,
                    DetachedWorkspaceSessions.RUNTIME_VARIABLES_MANAGER,
                    runtime);
            assertTrue(runtimeSnapshot.get("ok").getAsBoolean());
            assertFalse(waiting.isDone());

            JsonObject ready = new JsonObject();
            ready.addProperty("bindingEpoch", runtimeSnapshot.get("bindingEpoch").getAsString());
            ready.addProperty("workspaceEpoch", runtimeSnapshot.get("workspaceEpoch").getAsLong());
            ready.addProperty("homeBankingId", 2);
            ready.addProperty("botJobId", 5);
            JsonObject acknowledged = service.runtimeVariablesReady(
                    ready,
                    DetachedWorkspaceSessions.RUNTIME_VARIABLES_MANAGER,
                    runtime);

            assertTrue(acknowledged.get("ok").getAsBoolean());
            waiting.get(2, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void opensExcelWriterOnlyForTheExactSmokeOwner() {
        assertTrue(service.openSmokeTestForBotJob(5).get("ok").getAsBoolean());
        Session smoke = openSession();
        windows.register(DetachedWorkspaceSessions.SMOKE_TEST_MANAGER, smoke);
        JsonObject bootstrapRequest = new JsonObject();
        bootstrapRequest.addProperty("botJobId", 5);
        JsonObject snapshot = service.smokeTestBootstrap(
                bootstrapRequest,
                DetachedWorkspaceSessions.SMOKE_TEST_MANAGER,
                smoke);

        JsonObject request = new JsonObject();
        request.addProperty("requestId", "open-excel-writer");
        request.addProperty("bindingEpoch", snapshot.get("bindingEpoch").getAsString());
        request.addProperty("workspaceEpoch", snapshot.get("workspaceEpoch").getAsLong());
        request.addProperty("homeBankingId", 2);
        request.addProperty("botJobId", 5);
        int launchesBefore = windows.openOrFocusCalls;
        JsonObject opened = service.openExcelWriterWorkspace(
                request,
                DetachedWorkspaceSessions.SMOKE_TEST_MANAGER,
                smoke);

        assertTrue(opened.get("ok").getAsBoolean());
        assertEquals("open-excel-writer", opened.get("requestId").getAsString());
        assertEquals(DetachedWorkspaceSessions.EXCEL_WRITER_MANAGER, windows.lastSessionId);
        assertEquals(5, windows.lastBotJobId);

        request.addProperty("botJobId", 6);
        JsonObject refused = service.openExcelWriterWorkspace(
                request,
                DetachedWorkspaceSessions.SMOKE_TEST_MANAGER,
                smoke);
        assertFalse(refused.get("ok").getAsBoolean());
        assertEquals(launchesBefore + 1, windows.openOrFocusCalls);
    }

    @Test
    void relaysSmokeStateUsingTheExcelWriterManagersAuthority() {
        assertTrue(service.openSmokeTestForBotJob(5).get("ok").getAsBoolean());
        Session smoke = openSession();
        windows.register(DetachedWorkspaceSessions.SMOKE_TEST_MANAGER, smoke);
        JsonObject smokeRequest = new JsonObject();
        smokeRequest.addProperty("botJobId", 5);
        JsonObject smokeSnapshot = service.smokeTestBootstrap(
                smokeRequest, DetachedWorkspaceSessions.SMOKE_TEST_MANAGER, smoke);

        JsonObject state = new JsonObject();
        state.addProperty("bindingEpoch", smokeSnapshot.get("bindingEpoch").getAsString());
        state.addProperty("workspaceEpoch", smokeSnapshot.get("workspaceEpoch").getAsLong());
        state.addProperty("homeBankingId", 2);
        state.addProperty("botJobId", 5);
        state.add("state", new JsonObject());

        JsonObject beforeOpen = service.relayExcelWriterState(
                state, DetachedWorkspaceSessions.SMOKE_TEST_MANAGER, smoke);
        assertTrue(beforeOpen.get("ok").getAsBoolean());
        assertFalse(beforeOpen.get("delivered").getAsBoolean());

        JsonObject opened = service.openExcelWriterWorkspace(
                state, DetachedWorkspaceSessions.SMOKE_TEST_MANAGER, smoke);
        assertTrue(opened.get("ok").getAsBoolean());
        Session manager = openSession();
        windows.register(DetachedWorkspaceSessions.EXCEL_WRITER_MANAGER, manager);
        JsonObject bootstrapRequest = new JsonObject();
        bootstrapRequest.addProperty("botJobId", 5);
        JsonObject managerBinding = service.excelWriterBootstrap(
                bootstrapRequest, DetachedWorkspaceSessions.EXCEL_WRITER_MANAGER, manager);
        assertTrue(managerBinding.get("ok").getAsBoolean());

        JsonObject relayed = service.relayExcelWriterState(
                state, DetachedWorkspaceSessions.SMOKE_TEST_MANAGER, smoke);
        assertTrue(relayed.get("ok").getAsBoolean());
        Sent delivered = windows.sent.get(windows.sent.size() - 1);
        assertEquals("excelWriterWorkspace.state", delivered.operationId());
        assertEquals(
                managerBinding.get("bindingEpoch").getAsString(),
                delivered.body().get("bindingEpoch").getAsString());
        assertNotEquals(
                smokeSnapshot.get("bindingEpoch").getAsString(),
                delivered.body().get("bindingEpoch").getAsString());
        assertEquals(5, delivered.body().get("botJobId").getAsInt());
        assertEquals(2, delivered.body().get("homeBankingId").getAsInt());
    }

    @Test
    void suppressesUnchangedRealtimeGraphAndPublishesChangedRevision() {
        service.openForBotJob(5);
        Session manager = openSession();
        windows.register(manager);
        service.connected(VariablesWorkspaceService.WORKSPACE_SESSION_ID, manager);
        JsonObject bootstrap = service.bootstrap(
                new JsonObject(), VariablesWorkspaceService.WORKSPACE_SESSION_ID, manager);
        assertTrue(bootstrap.get("ok").getAsBoolean());

        int before = windows.sent.size();
        assertTrue(service.publishIfOpen(5));
        assertEquals(before + 1, windows.sent.size());
        assertFalse(service.publishIfOpen(5));
        assertEquals(before + 1, windows.sent.size());

        graphs.responses.put(5, graph("revision-five-changed"));
        assertTrue(service.publishIfOpen(5));
        assertEquals(before + 2, windows.sent.size());
        JsonObject published = windows.sent.get(windows.sent.size() - 1).body();
        assertEquals(
                "revision-five-changed",
                published.get("graphRevision").getAsString());
    }

    @Test
    void closeBeforeOpenReloadPreservesBindingForAuthoritativeReplacement() {
        service.openForBotJob(5);
        Session oldManager = openSession();
        windows.register(oldManager);
        service.connected(VariablesWorkspaceService.WORKSPACE_SESSION_ID, oldManager);

        service.disconnected(VariablesWorkspaceService.WORKSPACE_SESSION_ID, oldManager);
        assertEquals(1, tasks.disconnects.size());

        Session replacement = openSession();
        windows.register(replacement);
        service.connected(VariablesWorkspaceService.WORKSPACE_SESSION_ID, replacement);
        tasks.runDisconnects();

        JsonObject response = service.bootstrap(
                new JsonObject(),
                VariablesWorkspaceService.WORKSPACE_SESSION_ID,
                replacement);
        assertTrue(response.get("ok").getAsBoolean());
    }

    @Test
    void explicitCloseUltimatelyRetiresBindingAfterReloadGrace() {
        service.openForBotJob(5);
        Session manager = openSession();
        windows.register(manager);
        service.connected(VariablesWorkspaceService.WORKSPACE_SESSION_ID, manager);

        service.disconnected(VariablesWorkspaceService.WORKSPACE_SESSION_ID, manager);
        tasks.runDisconnects();

        Session late = openSession();
        windows.register(late);
        service.connected(VariablesWorkspaceService.WORKSPACE_SESSION_ID, late);
        JsonObject closed = service.bootstrap(
                new JsonObject(),
                VariablesWorkspaceService.WORKSPACE_SESSION_ID,
                late);
        assertFalse(closed.get("ok").getAsBoolean());
        assertTrue(windows.sent.stream()
                .anyMatch(item -> PagesOpenWorkspaceService.WORKSPACE_CLOSE_OPERATION.equals(
                        item.operationId())));
    }

    @Test
    void staleDisconnectAfterTakeoverCannotClearReplacementBinding() {
        service.openForBotJob(5);
        Session oldManager = openSession();
        windows.register(oldManager);
        service.connected(VariablesWorkspaceService.WORKSPACE_SESSION_ID, oldManager);

        Session replacement = openSession();
        windows.register(replacement);
        service.connected(VariablesWorkspaceService.WORKSPACE_SESSION_ID, replacement);
        service.disconnected(VariablesWorkspaceService.WORKSPACE_SESSION_ID, oldManager);
        tasks.runDisconnects();

        JsonObject response = service.bootstrap(
                new JsonObject(),
                VariablesWorkspaceService.WORKSPACE_SESSION_ID,
                replacement);
        assertTrue(response.get("ok").getAsBoolean());
    }

    @Test
    void graphFailureKeepsBindingMetadataAndLastRevisionUsable() {
        service.openForBotJob(5);
        Session manager = openSession();
        windows.register(manager);
        service.connected(VariablesWorkspaceService.WORKSPACE_SESSION_ID, manager);
        assertTrue(service.bootstrap(
                        new JsonObject(),
                        VariablesWorkspaceService.WORKSPACE_SESSION_ID,
                        manager)
                .get("ok")
                .getAsBoolean());

        JsonObject graphFailure = new JsonObject();
        graphFailure.addProperty("ok", false);
        graphFailure.addProperty("error", "database unavailable");
        graphs.responses.put(5, graphFailure);
        JsonObject request = new JsonObject();
        request.addProperty("requestId", "refresh-failure");
        JsonObject failed = service.refresh(
                request, VariablesWorkspaceService.WORKSPACE_SESSION_ID, manager);

        assertFalse(failed.get("ok").getAsBoolean());
        assertTrue(failed.get("preserveSnapshot").getAsBoolean());
        assertEquals("refresh-failure", failed.get("requestId").getAsString());
        assertEquals(5, failed.get("botJobId").getAsInt());
        assertEquals(10L, failed.get("workspaceEpoch").getAsLong());
        assertTrue(failed.has("bindingEpoch"));

        graphs.responses.put(5, graph("recovered"));
        JsonObject recovered = service.refresh(
                new JsonObject(), VariablesWorkspaceService.WORKSPACE_SESSION_ID, manager);
        assertTrue(recovered.get("ok").getAsBoolean());
        assertEquals("recovered", recovered.get("graphRevision").getAsString());
    }

    @Test
    void bootstrapsUpdatesAndPublishesPersistentRuntimeMemory() {
        BotJobKey owner = new BotJobKey(2, 5);
        JsonObject graph = graph("runtime-memory");
        JsonArray rawVariables = new JsonArray();
        JsonObject variable = new JsonObject();
        variable.addProperty("id", 7);
        variable.addProperty("name", "Amount");
        variable.addProperty("type", "$String");
        rawVariables.add(variable);
        graph.add("rawVariables", rawVariables);
        graph.add("rawCommands", new JsonArray());
        graphs.responses.put(5, graph);

        try {
            service.openForBotJob(5);
            Session manager = openSession();
            windows.register(manager);
            service.connected(VariablesWorkspaceService.WORKSPACE_SESSION_ID, manager);
            JsonObject bootstrap = service.bootstrap(
                    new JsonObject(),
                    VariablesWorkspaceService.WORKSPACE_SESSION_ID,
                    manager);

            JsonObject initial = bootstrap.getAsJsonObject("runtimeMemory")
                    .getAsJsonArray("variables")
                    .get(0)
                    .getAsJsonObject();
            assertEquals("VOID", initial.get("state").getAsString());

            JsonObject request = new JsonObject();
            request.addProperty("requestId", "runtime-set");
            request.addProperty(
                    "bindingEpoch",
                    bootstrap.get("bindingEpoch").getAsString());
            request.addProperty("workspaceEpoch", 10L);
            request.addProperty("contractVersion", 1);
            request.addProperty(
                    "baseRuntimeRevision",
                    bootstrap.getAsJsonObject("runtimeMemory")
                            .get("revision")
                            .getAsLong());
            request.addProperty("variableId", 7);
            request.addProperty("operation", "SET");
            request.addProperty("expectedEntryRevision", 0L);
            request.addProperty("value", "");
            JsonObject updated = service.updateRuntimeMemory(
                    request,
                    VariablesWorkspaceService.WORKSPACE_SESSION_ID,
                    manager);

            assertTrue(updated.get("ok").getAsBoolean(), updated.toString());
            JsonObject current = updated.getAsJsonObject("runtimeMemory")
                    .getAsJsonArray("variables")
                    .get(0)
                    .getAsJsonObject();
            assertEquals("VALUE", current.get("state").getAsString());
            assertEquals("", current.get("value").getAsString());
            assertEquals("MANUAL", current.get("source").getAsString());

            assertTrue(service.publishRuntimeMemoryIfOpen(owner));
            Sent published = windows.sent.get(windows.sent.size() - 1);
            assertEquals(
                    VariablesWorkspaceService.RUNTIME_MEMORY_SNAPSHOT_OPERATION,
                    published.operationId());
            assertEquals(
                    "",
                    published.body()
                            .getAsJsonObject("runtimeMemory")
                            .getAsJsonArray("variables")
                            .get(0)
                            .getAsJsonObject()
                            .get("value")
                            .getAsString());
        } finally {
            RuntimeVariableMemoryRegistry.getInstance().remove(owner);
        }
    }

    @Test
    void retiresOnlyTheMatchingWorkspaceAndPreservesRuntimeMemory() {
        BotJobKey owner = new BotJobKey(2, 5);
        RuntimeVariableMemoryRegistry.getInstance().reconcileDefinitions(
                owner,
                List.of(new RuntimeVariableMemoryRegistry.Definition(
                        7, "Amount", "$String")),
                true);
        service.openForBotJob(5);

        assertFalse(service.retireForBotJob(6, "wrong job"));
        assertEquals(
                1,
                RuntimeVariableMemoryRegistry.getInstance()
                        .snapshot(owner)
                        .variables()
                        .size());
        assertEquals(0, windows.closes);
        assertTrue(service.retireForBotJob(5, "job closed"));
        assertEquals(
                1,
                RuntimeVariableMemoryRegistry.getInstance()
                        .snapshot(owner)
                        .variables()
                        .size());
        assertEquals(1, windows.closes);
        JsonObject tombstone = windows.sent.get(windows.sent.size() - 1).body();
        assertTrue(tombstone.get("retired").getAsBoolean());
        assertFalse(tombstone.get("preserveSnapshot").getAsBoolean());
        assertFalse(service.publishIfOpen(5));
        RuntimeVariableMemoryRegistry.getInstance().remove(owner);
    }

    @Test
    void failedSnapshotDeliveryDoesNotAdvanceRevisionAndIsRetried() {
        service.openForBotJob(5);
        Session manager = openSession();
        windows.register(manager);
        service.connected(VariablesWorkspaceService.WORKSPACE_SESSION_ID, manager);
        windows.sendResult = false;

        assertFalse(service.publishIfOpen(5));
        int failedAttempts = windows.sendAttempts;

        windows.sendResult = true;
        assertTrue(service.publishIfOpen(5));
        assertEquals(failedAttempts + 1, windows.sendAttempts);
        assertFalse(service.publishIfOpen(5));
    }

    @Test
    void retirementFallsBackToForceCloseAndNeverKeepsLiveBinding() {
        service.openForBotJob(5);
        windows.closeResult = false;
        windows.forceCloseResult = true;

        VariablesWorkspaceService.RetireResult retired =
                service.retireForBotJobDetailed(5, "owner closed");

        assertTrue(retired.matched());
        assertTrue(retired.closed());
        assertTrue(retired.forceClosed());
        assertEquals(1, windows.forceCloses);
        assertFalse(service.publishIfOpen(5));
    }

    @Test
    void mutationNotificationIsDeferredUntilCallerMonitorCanRelease() {
        service.openForBotJob(5);
        Session manager = openSession();
        windows.register(manager);
        service.connected(VariablesWorkspaceService.WORKSPACE_SESSION_ID, manager);
        int loadsBefore = graphs.loads;

        service.notifyMutation(5);

        assertEquals(loadsBefore, graphs.loads);
        assertEquals(1, tasks.mutations.size());
        tasks.runMutations();
        assertTrue(graphs.loads > loadsBefore);
    }

    @Test
    void runtimeNotificationsAreScopedAndCoalescedPerOwner() {
        BotJobKey owner = new BotJobKey(2, 5);
        RuntimeVariableMemoryRegistry.getInstance().reconcileDefinitions(
                owner,
                List.of(new RuntimeVariableMemoryRegistry.Definition(
                        7, "Amount", "$String")),
                true);
        try {
            service.notifyRuntimeMemoryChanged(owner, 1L);
            assertEquals(0, tasks.mutations.size());

            service.openForBotJob(5);
            Session manager = openSession();
            windows.register(manager);
            service.connected(
                    VariablesWorkspaceService.WORKSPACE_SESSION_ID,
                    manager);
            service.notifyRuntimeMemoryChanged(owner, 2L);
            service.notifyRuntimeMemoryChanged(owner, 3L);

            assertEquals(1, tasks.mutations.size());
            tasks.runMutations();
            long publications = windows.sent.stream()
                    .filter(item -> VariablesWorkspaceService
                            .RUNTIME_MEMORY_SNAPSHOT_OPERATION
                            .equals(item.operationId()))
                    .count();
            assertEquals(1L, publications);

            service.openForBotJob(6);
            service.notifyRuntimeMemoryChanged(owner, 4L);
            assertEquals(0, tasks.mutations.size());
        } finally {
            RuntimeVariableMemoryRegistry.getInstance().remove(owner);
        }
    }

    @Test
    void committedDeletionPublishesGridThenReconcilesRuntimeCatalogBeforeSnapshotQueue() {
        service.openForBotJob(5);
        Session manager = openSession();
        windows.register(manager);
        service.connected(
                VariablesWorkspaceService.WORKSPACE_SESSION_ID, manager);
        JsonObject bootstrap = service.bootstrap(
                new JsonObject(),
                VariablesWorkspaceService.WORKSPACE_SESSION_ID,
                manager);
        assertTrue(bootstrap.get("ok").getAsBoolean());

        BotJobKey owner = new BotJobKey(2, 5);
        RuntimeVariableMemoryRegistry registry =
                RuntimeVariableMemoryRegistry.getInstance();
        registry.reconcileDefinitions(
                owner,
                List.of(
                        new RuntimeVariableMemoryRegistry.Definition(
                                501, "Deleted", "$String"),
                        new RuntimeVariableMemoryRegistry.Definition(
                                502, "Retained", "$String")),
                true);
        registry.write(
                owner,
                501,
                "temporary",
                RuntimeVariableMemoryRegistry.ValueSource.MANUAL);
        JsonObject afterDeletion = graph("revision-after-delete");
        JsonObject retained = new JsonObject();
        retained.addProperty("id", 502);
        retained.addProperty("name", "Retained");
        retained.addProperty("type", "$String");
        afterDeletion.getAsJsonArray("rawVariables").add(retained);
        graphs.responses.put(5, afterDeletion);

        JsonObject committed = new JsonObject();
        committed.addProperty("ok", true);
        committed.addProperty("committed", true);
        committed.addProperty("botJobId", 5);
        committed.addProperty("homeBankingId", 2);
        ScannerBotJobTasksPublisher grid =
                mock(ScannerBotJobTasksPublisher.class);
        try (MockedStatic<ScannerBotJobTasksPublisher> publishers =
                mockStatic(ScannerBotJobTasksPublisher.class)) {
            publishers.when(ScannerBotJobTasksPublisher::getInstance)
                    .thenReturn(grid);
            tasks.beforeMutationQueued =
                    () -> verify(grid).publishGridOnly(2, 5);

            service.publishCommittedMutation(committed);

            verify(grid).publishGridOnly(2, 5);
            assertFalse(registry.containsDefinition(owner, 501));
            assertTrue(registry.containsDefinition(owner, 502));
            assertEquals(1, tasks.mutations.size());
            tasks.runMutations();
            assertTrue(windows.sent.stream().anyMatch(
                    sent -> VariablesWorkspaceService.SNAPSHOT_OPERATION.equals(
                            sent.operationId())
                            && "revision-after-delete".equals(
                                    sent.body()
                                            .get("graphRevision")
                                            .getAsString())));
        } finally {
            registry.remove(owner);
        }
    }

    @Test
    void registryLockAndVariablesStateCannotDeadlockEachOther() throws Exception {
        LockingWorkspaces lockingWorkspaces = new LockingWorkspaces();
        lockingWorkspaces.add(new VariablesWorkspaceService.WorkspaceContext(
                10L, 5, 2, "Job Five", "Bank"));
        lockingWorkspaces.add(new VariablesWorkspaceService.WorkspaceContext(
                11L, 6, 3, "Job Six", "Other Bank"));
        VariablesWorkspaceService concurrentService =
                new VariablesWorkspaceService(
                        lockingWorkspaces, graphs, windows, new Gson(), tasks);
        assertTrue(concurrentService.openForBotJob(5).get("ok").getAsBoolean());
        lockingWorkspaces.coordinateJobSixRequire = true;

        ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "variables-lock-order-test");
            thread.setDaemon(true);
            return thread;
        });
        try {
            Future<Boolean> publishWhileHoldingRegistry = executor.submit(() -> {
                synchronized (lockingWorkspaces.registryLock) {
                    lockingWorkspaces.registryHeld.countDown();
                    assertTrue(lockingWorkspaces.jobSixRequireEntered.await(
                            2, TimeUnit.SECONDS));
                    return concurrentService.publishIfOpen(5);
                }
            });
            Future<JsonObject> retarget = executor.submit(() -> {
                assertTrue(lockingWorkspaces.registryHeld.await(
                        2, TimeUnit.SECONDS));
                return concurrentService.openForBotJob(6);
            });

            assertTrue(publishWhileHoldingRegistry.get(2, TimeUnit.SECONDS));
            assertTrue(retarget.get(2, TimeUnit.SECONDS).get("ok").getAsBoolean());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void bootstrapAdvertisesExactVersionedMutationCapabilityAlongsideContentRevision() {
        FakeMutations mutations = FakeMutations.ready();
        VariablesWorkspaceService mutableService =
                new VariablesWorkspaceService(
                        workspaces,
                        graphs,
                        windows,
                        new Gson(),
                        tasks,
                        mutations);
        mutableService.openForBotJob(5);
        Session manager = openSession();
        windows.register(manager);
        mutableService.connected(VariablesWorkspaceService.WORKSPACE_SESSION_ID, manager);

        JsonObject response = mutableService.bootstrap(
                new JsonObject(),
                VariablesWorkspaceService.WORKSPACE_SESSION_ID,
                manager);

        assertTrue(response.get("ok").getAsBoolean());
        assertEquals("revision-five", response.get("contentRevision").getAsString());
        assertEquals("revision-five", response.get("graphRevision").getAsString());
        assertEquals(4L, response.get("graphVersion").getAsLong());
        JsonObject capability = response.getAsJsonObject("mutationCapability");
        assertTrue(capability.get("enabled").getAsBoolean());
        assertEquals(
                InstructionGraphMutationV3.CONTRACT_VERSION,
                capability.get("contractVersion").getAsInt());
        assertEquals(
                "VARIABLES_INDIVIDUAL_ROW_V1",
                capability.get("profile").getAsString());
        assertEquals(
                "VARIABLES_INDIVIDUAL_CROSS_BLOCK_V1",
                capability.get("crossBlockProfile").getAsString());
        assertEquals(
                "VARIABLES_REACT_AUTHORED_V1",
                capability.get("reactAuthoredProfile").getAsString());
        assertEquals(4L, capability.get("graphVersion").getAsLong());
        assertEquals("mutation-five", capability.get("graphRevision").getAsString());
        assertEquals(
                "BOT_JOB",
                capability.getAsJsonObject("ownerAssertion")
                        .get("workspaceKind")
                        .getAsString());
        assertEquals(2, capability.getAsJsonObject("ownerAssertion")
                .get("homeBankingId")
                .getAsInt());
        assertEquals(5, capability.getAsJsonObject("ownerAssertion")
                .get("botJobId")
                .getAsInt());
        assertEquals(3, capability.getAsJsonArray("layoutRows").size());
        assertEquals(3, capability.getAsJsonArray("instructionFacts").size());
        JsonArray variableFacts = capability.getAsJsonArray("variableFacts");
        assertEquals(3, variableFacts.size());
        assertEquals(7, variableFacts.get(0).getAsJsonObject()
                .get("variableId").getAsInt());
        assertEquals(100, variableFacts.get(0).getAsJsonObject()
                .get("ownerInstructionId").getAsInt());
        assertEquals(8, variableFacts.get(1).getAsJsonObject()
                .get("variableId").getAsInt());
        assertTrue(variableFacts.get(1).getAsJsonObject()
                .get("ownerInstructionId").isJsonNull());
        assertEquals(9, variableFacts.get(2).getAsJsonObject()
                .get("variableId").getAsInt());
        assertEquals(999999, variableFacts.get(2).getAsJsonObject()
                .get("ownerInstructionId").getAsInt());
        assertEquals(1, mutations.inspectCalls);
        assertEquals(2, mutations.lastHomeBankingId);
        assertEquals(5, mutations.lastBotJobId);
        assertEquals(10L, mutations.lastWorkspaceEpoch);

        int publishedBefore = windows.sent.size();
        graphs.responses.put(5, graph("revision-five-changed"));
        assertTrue(mutableService.publishIfOpen(5));
        assertEquals(publishedBefore + 1, windows.sent.size());
        JsonObject changed = windows.sent.get(windows.sent.size() - 1).body();
        assertEquals("revision-five-changed", changed.get("graphRevision").getAsString());
        assertEquals(
                "mutation-five",
                changed.getAsJsonObject("mutationCapability")
                        .get("graphRevision")
                        .getAsString());
    }

    @Test
    void mutationRefusesForgedTransportAndStaleBindingBeforePersistence() {
        FakeMutations mutations = FakeMutations.ready();
        VariablesWorkspaceService mutableService =
                new VariablesWorkspaceService(
                        workspaces,
                        graphs,
                        windows,
                        new Gson(),
                        tasks,
                        mutations);
        mutableService.openForBotJob(5);
        Session manager = openSession();
        windows.register(manager);
        mutableService.connected(VariablesWorkspaceService.WORKSPACE_SESSION_ID, manager);
        JsonObject bootstrap = mutableService.bootstrap(
                new JsonObject(),
                VariablesWorkspaceService.WORKSPACE_SESSION_ID,
                manager);
        String bindingEpoch = bootstrap.get("bindingEpoch").getAsString();

        JsonObject forgedRequest = new JsonObject();
        forgedRequest.addProperty("requestId", "forged-request");
        forgedRequest.addProperty("bindingEpoch", bindingEpoch);
        JsonObject forged = mutableService.mutate(
                forgedRequest,
                VariablesWorkspaceService.WORKSPACE_SESSION_ID,
                openSession());

        assertFalse(forged.get("ok").getAsBoolean());
        assertTrue(forged.get("preserveSnapshot").getAsBoolean());
        assertEquals("REQUEST_REFUSED", forged.get("errorCode").getAsString());
        assertEquals("forged-request", forged.get("requestId").getAsString());

        JsonObject staleRequest = new JsonObject();
        staleRequest.addProperty("requestId", "stale-binding");
        staleRequest.addProperty("bindingEpoch", "retired-binding");
        JsonObject stale = mutableService.mutate(
                staleRequest,
                VariablesWorkspaceService.WORKSPACE_SESSION_ID,
                manager);

        assertFalse(stale.get("ok").getAsBoolean());
        assertTrue(stale.get("preserveSnapshot").getAsBoolean());
        assertEquals("REQUEST_REFUSED", stale.get("errorCode").getAsString());
        assertEquals("stale-binding", stale.get("requestId").getAsString());
        assertEquals(bindingEpoch, stale.get("bindingEpoch").getAsString());
        assertEquals(0, mutations.mutateCalls);
    }

    @Test
    void mutationUsesBoundOwnerAndReturnsCorrelatedCommittedVersion() {
        BotJobDetailsWorkspaceRegistry registry =
                BotJobDetailsWorkspaceRegistry.getInstance();
        BotJobLoadDTO botJob = new BotJobLoadDTO();
        botJob.setId(5);
        botJob.setName("Job Five");
        botJob.setHomeBankingId(2);
        BotJobDetailsWorkspaceRegistry.Snapshot active =
                registry.activate(botJob, false);
        try {
            FakeWorkspaces exactWorkspaces = new FakeWorkspaces();
            exactWorkspaces.add(new VariablesWorkspaceService.WorkspaceContext(
                    active.workspaceEpoch(),
                    5,
                    2,
                    "Job Five",
                    "Bank"));
            FakeMutations mutations = FakeMutations.ready();
            mutations.commitResult = new CommitResult(
                    OwnerKey.botJob(2, 5),
                    active.workspaceEpoch(),
                    "variables-mutate-1",
                    4L,
                    5L,
                    "mutation-after");
            VariablesWorkspaceService mutableService =
                    new VariablesWorkspaceService(
                            exactWorkspaces,
                            graphs,
                            windows,
                            new Gson(),
                            tasks,
                            mutations);
            mutableService.openForBotJob(5);
            Session manager = openSession();
            windows.register(manager);
            mutableService.connected(
                    VariablesWorkspaceService.WORKSPACE_SESSION_ID,
                    manager);
            JsonObject bootstrap = mutableService.bootstrap(
                    new JsonObject(),
                    VariablesWorkspaceService.WORKSPACE_SESSION_ID,
                    manager);
            InstructionGraphMutationV3.Request mutation =
                    new InstructionGraphMutationV3.Request(
                            InstructionGraphMutationV3.CONTRACT_VERSION,
                            InstructionGraphMutationV3.MutationKind.ROW_MOVE,
                            "variables-mutate-1",
                            4L,
                            "mutation-five",
                            active.workspaceEpoch(),
                            new InstructionGraphMutationV3.OwnerAssertion(
                                    InstructionGraphMutationV3.WorkspaceKind.BOT_JOB,
                                    2,
                                    5),
                            100,
                            List.of(
                                    new LayoutRow(101, 10, 1, 1),
                                    new LayoutRow(100, 10, 1, 2)),
                            List.of(),
                            List.of(),
                            List.of());
            JsonObject request =
                    new Gson().toJsonTree(mutation).getAsJsonObject();
            request.addProperty(
                    "bindingEpoch",
                    bootstrap.get("bindingEpoch").getAsString());
            request.addProperty("homeBankingId", 999);
            request.addProperty("botJobId", 999);

            JsonObject response = mutableService.mutate(
                    request,
                    VariablesWorkspaceService.WORKSPACE_SESSION_ID,
                    manager);

            assertTrue(response.get("ok").getAsBoolean(), response.toString());
            assertEquals(
                    "variables-mutate-1",
                    response.get("requestId").getAsString());
            assertEquals(
                    bootstrap.get("bindingEpoch").getAsString(),
                    response.get("bindingEpoch").getAsString());
            assertEquals(active.workspaceEpoch(), response.get("workspaceEpoch").getAsLong());
            assertEquals(5, response.get("botJobId").getAsInt());
            assertEquals(2, response.get("homeBankingId").getAsInt());
            assertEquals(5L, response.get("committedGraphVersion").getAsLong());
            assertEquals("mutation-after", response.get("graphRevision").getAsString());
            assertTrue(response.get("committed").getAsBoolean());
            assertFalse(response.get("resyncRequired").getAsBoolean());
            assertEquals(1, mutations.mutateCalls);
            assertEquals(2, mutations.inspectCalls);
            assertEquals(2, mutations.lastHomeBankingId);
            assertEquals(5, mutations.lastBotJobId);
            assertEquals(active.workspaceEpoch(), mutations.lastWorkspaceEpoch);
            assertEquals("variables-mutate-1", mutations.lastRequest.requestId());
            assertEquals(3, mutations.lastRequest.layoutRows().size());
            assertEquals(
                    "VARIABLES_INDIVIDUAL_ROW_V1",
                    mutations.lastMutationProfile);
        } finally {
            registry.close(5);
        }
    }

    @Test
    void mutationProfilesDefaultToV1AndRejectUnknownValues() {
        assertEquals(
                "VARIABLES_INDIVIDUAL_ROW_V1",
                VariablesWorkspaceService.normalizeMutationProfile(""));
        assertEquals(
                "VARIABLES_INDIVIDUAL_CROSS_BLOCK_V1",
                VariablesWorkspaceService.normalizeMutationProfile(
                        " VARIABLES_INDIVIDUAL_CROSS_BLOCK_V1 "));
        assertEquals(
                "VARIABLES_REACT_AUTHORED_V1",
                VariablesWorkspaceService.normalizeMutationProfile(
                        " VARIABLES_REACT_AUTHORED_V1 "));
        assertThrows(
                IllegalArgumentException.class,
                () -> VariablesWorkspaceService.normalizeMutationProfile(
                        "VARIABLES_FUTURE_PROFILE"));
    }

    @Test
    void variableDeletionRefusesForgedTransportAndStaleBindingBeforePersistence() {
        FakeMutations mutations = FakeMutations.ready();
        FakeVariableDeletes deletes = new FakeVariableDeletes();
        VariablesWorkspaceService mutableService =
                new VariablesWorkspaceService(
                        workspaces,
                        graphs,
                        windows,
                        new Gson(),
                        tasks,
                        mutations,
                        deletes);
        mutableService.openForBotJob(5);
        Session manager = openSession();
        windows.register(manager);
        mutableService.connected(
                VariablesWorkspaceService.WORKSPACE_SESSION_ID, manager);
        JsonObject bootstrap = mutableService.bootstrap(
                new JsonObject(),
                VariablesWorkspaceService.WORKSPACE_SESSION_ID,
                manager);
        String bindingEpoch = bootstrap.get("bindingEpoch").getAsString();

        JsonObject forgedRequest = deleteRequest(
                bindingEpoch, 10L, "delete-forged");
        JsonObject forged = mutableService.deleteVariables(
                forgedRequest,
                VariablesWorkspaceService.WORKSPACE_SESSION_ID,
                openSession());
        assertFalse(forged.get("ok").getAsBoolean());
        assertTrue(forged.get("preserveSnapshot").getAsBoolean());
        assertEquals(
                "VARIABLE_DELETE_REQUEST_REFUSED",
                forged.get("errorCode").getAsString());

        JsonObject staleRequest = deleteRequest(
                "retired-binding", 10L, "delete-stale");
        JsonObject stale = mutableService.deleteVariables(
                staleRequest,
                VariablesWorkspaceService.WORKSPACE_SESSION_ID,
                manager);
        assertFalse(stale.get("ok").getAsBoolean());
        assertTrue(stale.get("preserveSnapshot").getAsBoolean());
        assertEquals(
                "VARIABLE_DELETE_REQUEST_REFUSED",
                stale.get("errorCode").getAsString());
        assertEquals(bindingEpoch, stale.get("bindingEpoch").getAsString());
        assertEquals(0, deletes.calls);
    }

    @Test
    void variableDeletionUsesBoundOwnerAndReturnsExactCommittedCounts() {
        BotJobDetailsWorkspaceRegistry registry =
                BotJobDetailsWorkspaceRegistry.getInstance();
        BotJobLoadDTO botJob = new BotJobLoadDTO();
        botJob.setId(5);
        botJob.setName("Job Five");
        botJob.setHomeBankingId(2);
        BotJobDetailsWorkspaceRegistry.Snapshot active =
                registry.activate(botJob, false);
        try {
            FakeWorkspaces exactWorkspaces = new FakeWorkspaces();
            exactWorkspaces.add(new VariablesWorkspaceService.WorkspaceContext(
                    active.workspaceEpoch(),
                    5,
                    2,
                    "Job Five",
                    "Bank"));
            FakeMutations mutations = FakeMutations.ready();
            FakeVariableDeletes deletes = new FakeVariableDeletes();
            deletes.result = new DeleteResult(
                    OwnerKey.botJob(2, 5),
                    active.workspaceEpoch(),
                    "delete-variable-501",
                    VariablesWorkspaceVariableDelete.Mode.SINGLE,
                    List.of(501),
                    1,
                    3,
                    4L,
                    5L,
                    "revision-after-delete");
            VariablesWorkspaceService mutableService =
                    new VariablesWorkspaceService(
                            exactWorkspaces,
                            graphs,
                            windows,
                            new Gson(),
                            tasks,
                            mutations,
                            deletes);
            mutableService.openForBotJob(5);
            Session manager = openSession();
            windows.register(manager);
            mutableService.connected(
                    VariablesWorkspaceService.WORKSPACE_SESSION_ID, manager);
            JsonObject bootstrap = mutableService.bootstrap(
                    new JsonObject(),
                    VariablesWorkspaceService.WORKSPACE_SESSION_ID,
                    manager);
            JsonObject request = deleteRequest(
                    bootstrap.get("bindingEpoch").getAsString(),
                    active.workspaceEpoch(),
                    "delete-variable-501");
            request.addProperty("homeBankingId", 999);
            request.addProperty("botJobId", 999);

            JsonObject response = mutableService.deleteVariables(
                    request,
                    VariablesWorkspaceService.WORKSPACE_SESSION_ID,
                    manager);

            assertTrue(response.get("ok").getAsBoolean(), response.toString());
            assertTrue(response.get("committed").getAsBoolean());
            assertFalse(response.get("resyncRequired").getAsBoolean());
            assertEquals(1, response.get("deletedCount").getAsInt());
            assertEquals(
                    3, response.get("clearedInstructionCount").getAsInt());
            assertEquals(
                    4L, response.get("previousGraphVersion").getAsLong());
            assertEquals(
                    5L, response.get("committedGraphVersion").getAsLong());
            assertEquals(
                    "revision-after-delete",
                    response.get("graphRevision").getAsString());
            assertEquals(5, response.get("botJobId").getAsInt());
            assertEquals(2, response.get("homeBankingId").getAsInt());
            assertEquals(1, deletes.calls);
            assertEquals(2, deletes.lastHomeBankingId);
            assertEquals(5, deletes.lastBotJobId);
            assertEquals(active.workspaceEpoch(), deletes.lastWorkspaceEpoch);
            assertEquals(
                    List.of(501), deletes.lastRequest.variableIds());
        } finally {
            registry.close(5);
        }
    }

    @Test
    void instructionCopyRefusesForgedTransportAndStaleBindingBeforePersistence() {
        FakeMutations mutations = FakeMutations.ready();
        FakeVariableDeletes deletes = new FakeVariableDeletes();
        FakeInstructionCopies copies = new FakeInstructionCopies();
        VariablesWorkspaceService mutableService =
                new VariablesWorkspaceService(
                        workspaces,
                        graphs,
                        windows,
                        new Gson(),
                        tasks,
                        mutations,
                        deletes,
                        copies);
        mutableService.openForBotJob(5);
        Session manager = openSession();
        windows.register(manager);
        mutableService.connected(
                VariablesWorkspaceService.WORKSPACE_SESSION_ID, manager);
        JsonObject bootstrap =
                mutableService.bootstrap(
                        new JsonObject(),
                        VariablesWorkspaceService.WORKSPACE_SESSION_ID,
                        manager);
        String bindingEpoch = bootstrap.get("bindingEpoch").getAsString();

        JsonObject forged =
                mutableService.copyInstructions(
                        copyRequest(bindingEpoch, 10L, "copy-forged"),
                        VariablesWorkspaceService.WORKSPACE_SESSION_ID,
                        openSession());
        assertFalse(forged.get("ok").getAsBoolean());
        assertTrue(forged.get("preserveSnapshot").getAsBoolean());
        assertEquals(
                "VARIABLE_COPY_REQUEST_REFUSED",
                forged.get("errorCode").getAsString());

        JsonObject stale =
                mutableService.copyInstructions(
                        copyRequest("retired-binding", 10L, "copy-stale"),
                        VariablesWorkspaceService.WORKSPACE_SESSION_ID,
                        manager);
        assertFalse(stale.get("ok").getAsBoolean());
        assertTrue(stale.get("preserveSnapshot").getAsBoolean());
        assertEquals(
                "VARIABLE_COPY_REQUEST_REFUSED",
                stale.get("errorCode").getAsString());
        assertEquals(bindingEpoch, stale.get("bindingEpoch").getAsString());
        assertEquals(0, copies.calls);
    }

    @Test
    void instructionCopyUsesBoundOwnerAndReturnsFreshIdsInExactSourceOrder() {
        BotJobDetailsWorkspaceRegistry registry =
                BotJobDetailsWorkspaceRegistry.getInstance();
        BotJobLoadDTO botJob = new BotJobLoadDTO();
        botJob.setId(5);
        botJob.setName("Job Five");
        botJob.setHomeBankingId(2);
        BotJobDetailsWorkspaceRegistry.Snapshot active =
                registry.activate(botJob, false);
        try {
            FakeWorkspaces exactWorkspaces = new FakeWorkspaces();
            exactWorkspaces.add(
                    new VariablesWorkspaceService.WorkspaceContext(
                            active.workspaceEpoch(), 5, 2, "Job Five", "Bank"));
            FakeInstructionCopies copies = new FakeInstructionCopies();
            copies.result =
                    new CopyResult(
                            OwnerKey.botJob(2, 5),
                            active.workspaceEpoch(),
                            "copy-instructions-1",
                            VariablesInstructionCopyV1.Scope.WITH_PARENTS,
                            102,
                            20,
                            List.of(102, 100),
                            new LinkedHashMap<>(Map.of(102, 901, 100, 902)),
                            new LinkedHashMap<>(Map.of(7, 77)),
                            2,
                            4L,
                            5L,
                            "revision-after-copy",
                            false);
            VariablesWorkspaceService mutableService =
                    new VariablesWorkspaceService(
                            exactWorkspaces,
                            graphs,
                            windows,
                            new Gson(),
                            tasks,
                            FakeMutations.ready(),
                            new FakeVariableDeletes(),
                            copies);
            mutableService.openForBotJob(5);
            Session manager = openSession();
            windows.register(manager);
            mutableService.connected(
                    VariablesWorkspaceService.WORKSPACE_SESSION_ID, manager);
            JsonObject bootstrap =
                    mutableService.bootstrap(
                            new JsonObject(),
                            VariablesWorkspaceService.WORKSPACE_SESSION_ID,
                            manager);
            JsonObject request =
                    copyRequest(
                            bootstrap.get("bindingEpoch").getAsString(),
                            active.workspaceEpoch(),
                            "copy-instructions-1");
            request.addProperty("homeBankingId", 999);
            request.addProperty("botJobId", 999);

            JsonObject response =
                    mutableService.copyInstructions(
                            request,
                            VariablesWorkspaceService.WORKSPACE_SESSION_ID,
                            manager);

            assertTrue(response.get("ok").getAsBoolean(), response.toString());
            assertTrue(response.get("committed").getAsBoolean());
            assertEquals(
                    List.of(901, 902),
                    new Gson()
                            .fromJson(
                                    response.get("createdInstructionIds"),
                                    new com.google.gson.reflect.TypeToken<List<Integer>>() {}
                                            .getType()));
            assertEquals(2, response.get("appliedCount").getAsInt());
            assertEquals(2, response.get("copiedReferenceCount").getAsInt());
            assertEquals(5L, response.get("committedGraphVersion").getAsLong());
            assertEquals("revision-after-copy", response.get("graphRevision").getAsString());
            assertEquals(5, response.get("botJobId").getAsInt());
            assertEquals(2, response.get("homeBankingId").getAsInt());
            assertEquals(1, copies.calls);
            assertEquals(2, copies.lastHomeBankingId);
            assertEquals(5, copies.lastBotJobId);
            assertEquals(active.workspaceEpoch(), copies.lastWorkspaceEpoch);
        } finally {
            registry.close(5);
        }
    }

    private JsonObject copyRequest(
            String bindingEpoch, long workspaceEpoch, String requestId) {
        VariablesInstructionCopyV1.Request request =
                new VariablesInstructionCopyV1.Request(
                        VariablesInstructionCopyV1.CONTRACT_VERSION,
                        requestId,
                        bindingEpoch,
                        workspaceEpoch,
                        4L,
                        "mutation-five",
                        20,
                        102,
                        VariablesInstructionCopyV1.Scope.WITH_PARENTS,
                        List.of(102, 100));
        return new Gson().toJsonTree(request).getAsJsonObject();
    }

    private JsonObject deleteRequest(
            String bindingEpoch, long workspaceEpoch, String requestId) {
        VariablesWorkspaceVariableDelete.Request request =
                new VariablesWorkspaceVariableDelete.Request(
                        VariablesWorkspaceVariableDelete.CONTRACT_VERSION,
                        requestId,
                        workspaceEpoch,
                        VariablesWorkspaceVariableDelete.Mode.SINGLE,
                        List.of(501));
        JsonObject json = new Gson().toJsonTree(request).getAsJsonObject();
        json.addProperty("bindingEpoch", bindingEpoch);
        return json;
    }

    @Test
    void mutationForwardsExplicitCrossBlockProfileOutsideCoreV3Request() {
        BotJobDetailsWorkspaceRegistry registry =
                BotJobDetailsWorkspaceRegistry.getInstance();
        BotJobLoadDTO botJob = new BotJobLoadDTO();
        botJob.setId(5);
        botJob.setName("Job Five");
        botJob.setHomeBankingId(2);
        BotJobDetailsWorkspaceRegistry.Snapshot active =
                registry.activate(botJob, false);
        try {
            FakeWorkspaces exactWorkspaces = new FakeWorkspaces();
            exactWorkspaces.add(new VariablesWorkspaceService.WorkspaceContext(
                    active.workspaceEpoch(),
                    5,
                    2,
                    "Job Five",
                    "Bank"));
            FakeMutations mutations = FakeMutations.ready();
            VariablesWorkspaceService mutableService =
                    new VariablesWorkspaceService(
                            exactWorkspaces,
                            graphs,
                            windows,
                            new Gson(),
                            tasks,
                            mutations);
            mutableService.openForBotJob(5);
            Session manager = openSession();
            windows.register(manager);
            mutableService.connected(
                    VariablesWorkspaceService.WORKSPACE_SESSION_ID,
                    manager);
            JsonObject bootstrap = mutableService.bootstrap(
                    new JsonObject(),
                    VariablesWorkspaceService.WORKSPACE_SESSION_ID,
                    manager);

            InstructionGraphMutationV3.Request mutation =
                    new InstructionGraphMutationV3.Request(
                            InstructionGraphMutationV3.CONTRACT_VERSION,
                            InstructionGraphMutationV3.MutationKind.ROW_MOVE,
                            "variables-cross-profile",
                            4L,
                            "mutation-five",
                            active.workspaceEpoch(),
                            new InstructionGraphMutationV3.OwnerAssertion(
                                    InstructionGraphMutationV3.WorkspaceKind.BOT_JOB,
                                    2,
                                    5),
                            102,
                            List.of(new LayoutRow(102, 11, 2, 1)),
                            List.of(),
                            List.of(),
                            List.of());
            JsonObject request = new Gson().toJsonTree(mutation).getAsJsonObject();
            request.addProperty(
                    "bindingEpoch",
                    bootstrap.get("bindingEpoch").getAsString());
            request.addProperty(
                    "mutationProfile",
                    "VARIABLES_INDIVIDUAL_CROSS_BLOCK_V1");

            JsonObject response = mutableService.mutate(
                    request,
                    VariablesWorkspaceService.WORKSPACE_SESSION_ID,
                    manager);

            assertTrue(response.get("ok").getAsBoolean(), response.toString());
            assertEquals(1, mutations.mutateCalls);
            assertEquals(
                    "VARIABLES_INDIVIDUAL_CROSS_BLOCK_V1",
                    mutations.lastMutationProfile);
            assertEquals(
                    "variables-cross-profile",
                    mutations.lastRequest.requestId());
            assertEquals(3, mutations.lastRequest.layoutRows().size());
        } finally {
            registry.close(5);
        }
    }

    @Test
    void mutationReturnsCommittedResyncSuccessWhenTransportRotatesAfterCommit() {
        BotJobDetailsWorkspaceRegistry registry =
                BotJobDetailsWorkspaceRegistry.getInstance();
        BotJobLoadDTO botJob = new BotJobLoadDTO();
        botJob.setId(5);
        botJob.setName("Job Five");
        botJob.setHomeBankingId(2);
        BotJobDetailsWorkspaceRegistry.Snapshot active =
                registry.activate(botJob, false);
        try {
            FakeWorkspaces exactWorkspaces = new FakeWorkspaces();
            exactWorkspaces.add(new VariablesWorkspaceService.WorkspaceContext(
                    active.workspaceEpoch(),
                    5,
                    2,
                    "Job Five",
                    "Bank"));
            FakeMutations mutations = FakeMutations.ready();
            mutations.commitResult = new CommitResult(
                    OwnerKey.botJob(2, 5),
                    active.workspaceEpoch(),
                    "variables-resync",
                    4L,
                    5L,
                    "mutation-after");
            VariablesWorkspaceService mutableService =
                    new VariablesWorkspaceService(
                            exactWorkspaces,
                            graphs,
                            windows,
                            new Gson(),
                            tasks,
                            mutations);
            mutableService.openForBotJob(5);
            Session original = openSession();
            windows.register(original);
            mutableService.connected(
                    VariablesWorkspaceService.WORKSPACE_SESSION_ID,
                    original);
            JsonObject bootstrap = mutableService.bootstrap(
                    new JsonObject(),
                    VariablesWorkspaceService.WORKSPACE_SESSION_ID,
                    original);

            Session replacement = openSession();
            mutations.afterMutate = () -> {
                windows.register(replacement);
                mutableService.connected(
                        VariablesWorkspaceService.WORKSPACE_SESSION_ID,
                        replacement);
            };
            InstructionGraphMutationV3.Request mutation =
                    new InstructionGraphMutationV3.Request(
                            InstructionGraphMutationV3.CONTRACT_VERSION,
                            InstructionGraphMutationV3.MutationKind.ROW_MOVE,
                            "variables-resync",
                            4L,
                            "mutation-five",
                            active.workspaceEpoch(),
                            new InstructionGraphMutationV3.OwnerAssertion(
                                    InstructionGraphMutationV3.WorkspaceKind.BOT_JOB,
                                    2,
                                    5),
                            100,
                            List.of(
                                    new LayoutRow(101, 10, 1, 1),
                                    new LayoutRow(100, 10, 1, 2),
                                    new LayoutRow(102, 10, 1, 3)),
                            List.of(),
                            List.of(),
                            List.of());
            JsonObject request =
                    new Gson().toJsonTree(mutation).getAsJsonObject();
            request.addProperty(
                    "bindingEpoch",
                    bootstrap.get("bindingEpoch").getAsString());

            JsonObject response = mutableService.mutate(
                    request,
                    VariablesWorkspaceService.WORKSPACE_SESSION_ID,
                    original);

            assertTrue(response.get("ok").getAsBoolean(), response.toString());
            assertTrue(response.get("committed").getAsBoolean());
            assertTrue(response.get("resyncRequired").getAsBoolean());
            assertFalse(response.has("preserveSnapshot"));
            assertEquals(5, response.get("botJobId").getAsInt());
            assertEquals(2, response.get("homeBankingId").getAsInt());
            assertEquals(active.workspaceEpoch(), response.get("workspaceEpoch").getAsLong());
            assertEquals(5L, response.get("committedGraphVersion").getAsLong());
            assertEquals("mutation-after", response.get("graphRevision").getAsString());
            assertEquals(1, mutations.mutateCalls);
        } finally {
            registry.close(5);
        }
    }

    private JsonObject graph(String revision) {
        JsonObject graph = new JsonObject();
        graph.addProperty("ok", true);
        graph.addProperty("graphRevision", revision);
        JsonObject summary = new JsonObject();
        summary.addProperty("variableCount", 0);
        summary.addProperty("producerCount", 0);
        summary.addProperty("consumerCount", 0);
        summary.addProperty("literalAssignmentCount", 0);
        summary.addProperty("unusedCount", 0);
        summary.addProperty("warningCount", 0);
        graph.add("summary", summary);
        graph.add("blocks", new JsonArray());
        graph.add("variables", new JsonArray());
        graph.add("rawVariables", new JsonArray());
        graph.add("rawCommands", new JsonArray());
        graph.add("edges", new JsonArray());
        graph.add("diagnostics", new JsonArray());
        return graph;
    }

    private Session openSession() {
        Session session = mock(Session.class);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    private static final class FakeWorkspaces
            implements VariablesWorkspaceService.WorkspacePort {
        private final Map<Integer, VariablesWorkspaceService.WorkspaceContext> contexts =
                new LinkedHashMap<>();

        void add(VariablesWorkspaceService.WorkspaceContext context) {
            contexts.put(context.botJobId(), context);
        }

        @Override
        public VariablesWorkspaceService.WorkspaceContext require(
                int botJobId, long workspaceEpoch) {
            VariablesWorkspaceService.WorkspaceContext context = contexts.get(botJobId);
            if (context == null
                    || (workspaceEpoch > 0
                            && context.workspaceEpoch() != workspaceEpoch)) {
                throw new IllegalArgumentException("stale Bot Job");
            }
            return context;
        }
    }

    private static final class LockingWorkspaces
            implements VariablesWorkspaceService.WorkspacePort {
        private final Object registryLock = new Object();
        private final CountDownLatch registryHeld = new CountDownLatch(1);
        private final CountDownLatch jobSixRequireEntered = new CountDownLatch(1);
        private final Map<Integer, VariablesWorkspaceService.WorkspaceContext> contexts =
                new LinkedHashMap<>();
        private volatile boolean coordinateJobSixRequire;

        void add(VariablesWorkspaceService.WorkspaceContext context) {
            contexts.put(context.botJobId(), context);
        }

        @Override
        public VariablesWorkspaceService.WorkspaceContext require(
                int botJobId, long workspaceEpoch) {
            if (coordinateJobSixRequire && botJobId == 6) {
                jobSixRequireEntered.countDown();
            }
            synchronized (registryLock) {
                VariablesWorkspaceService.WorkspaceContext context =
                        contexts.get(botJobId);
                if (context == null
                        || (workspaceEpoch > 0
                                && context.workspaceEpoch() != workspaceEpoch)) {
                    throw new IllegalArgumentException("stale Bot Job");
                }
                return context;
            }
        }
    }

    private static final class FakeGraphs implements VariablesWorkspaceService.GraphPort {
        private final Map<Integer, JsonObject> responses = new LinkedHashMap<>();
        private int loads;

        @Override
        public JsonObject load(int botJobId) {
            loads++;
            JsonObject response = responses.get(botJobId);
            return response == null ? new JsonObject() : response.deepCopy();
        }
    }

    private static final class FakeWindows implements VariablesWorkspaceService.WindowPort {
        private boolean open;
        private Session registered;
        private final Map<String, Session> registeredBySession = new LinkedHashMap<>();
        private final CountDownLatch runtimeVariablesOpened = new CountDownLatch(1);
        private int launches;
        private int closes;
        private int forceCloses;
        private int sendAttempts;
        private int openOrFocusCalls;
        private int lastBotJobId;
        private String lastSessionId = "";
        private boolean closeResult = true;
        private boolean forceCloseResult = true;
        private boolean sendResult = true;
        private final List<Sent> sent = new ArrayList<>();

        void register(Session transport) {
            registered = transport;
            registeredBySession.put(VariablesWorkspaceService.WORKSPACE_SESSION_ID, transport);
            open = true;
        }

        void register(String sessionId, Session transport) {
            registeredBySession.put(sessionId, transport);
            open = true;
        }

        @Override
        public boolean isOpen(String sessionId) {
            return open;
        }

        @Override
        public boolean isRegistered(String sessionId, Session transport) {
            Session expected = registeredBySession.get(sessionId);
            return transport == (expected == null ? registered : expected)
                    && transport != null
                    && transport.isOpen();
        }

        @Override
        public boolean openOrFocus(String sessionId, int botJobId, String reason) {
            openOrFocusCalls++;
            lastSessionId = sessionId;
            lastBotJobId = botJobId;
            if (DetachedWorkspaceSessions.RUNTIME_VARIABLES_MANAGER.equals(sessionId)) {
                runtimeVariablesOpened.countDown();
            }
            if (!open) launches++;
            open = true;
            return true;
        }

        @Override
        public boolean close(String sessionId, String reason) {
            closes++;
            if (closeResult) open = false;
            return closeResult;
        }

        @Override
        public boolean forceClose(String sessionId, String reason) {
            forceCloses++;
            if (forceCloseResult) open = false;
            return forceCloseResult;
        }

        @Override
        public boolean send(
                int homeBankingId,
                String sessionId,
                String operationId,
                JsonObject body) {
            sendAttempts++;
            if (!open || !sendResult) return false;
            sent.add(new Sent(homeBankingId, sessionId, operationId, body.deepCopy()));
            return true;
        }
    }

    private static final class FakeTasks implements VariablesWorkspaceService.TaskPort {
        private final List<Runnable> mutations = new ArrayList<>();
        private final List<Runnable> disconnects = new ArrayList<>();
        private Runnable beforeMutationQueued = () -> {};

        @Override
        public void executeMutation(Runnable task) {
            beforeMutationQueued.run();
            mutations.add(task);
        }

        @Override
        public void scheduleDisconnect(Runnable task, long delayMillis) {
            disconnects.add(task);
        }

        void runMutations() {
            List<Runnable> pending = new ArrayList<>(mutations);
            mutations.clear();
            pending.forEach(Runnable::run);
        }

        void runDisconnects() {
            List<Runnable> pending = new ArrayList<>(disconnects);
            disconnects.clear();
            pending.forEach(Runnable::run);
        }
    }

    private static final class FakeMutations
            implements VariablesWorkspaceService.MutationPort {
        private GraphSnapshot snapshot;
        private CommitResult commitResult;
        private int inspectCalls;
        private int mutateCalls;
        private int lastHomeBankingId;
        private int lastBotJobId;
        private long lastWorkspaceEpoch;
        private InstructionGraphMutationV3.Request lastRequest;
        private String lastMutationProfile;
        private Runnable afterMutate = () -> {};

        private static FakeMutations ready() {
            FakeMutations mutations = new FakeMutations();
            mutations.snapshot = new GraphSnapshot(
                    4L,
                    "mutation-five",
                    List.of(
                            new LayoutRow(100, 10, 1, 1),
                            new LayoutRow(101, 10, 1, 2),
                            new LayoutRow(102, 10, 1, 3)),
                    List.of(
                            new GraphInstructionFact(
                                    100,
                                    10,
                                    1,
                                    1,
                                    "GET",
                                    null,
                                    null,
                                    7),
                            new GraphInstructionFact(
                                    101,
                                    10,
                                    1,
                                    2,
                                    "H",
                                    null,
                                    null,
                                    null),
                            new GraphInstructionFact(
                                    102,
                                    10,
                                    1,
                                    3,
                                    "CK",
                                    null,
                                    null,
                                    7)),
                    List.of(
                            new GraphVariableFact(7, 100),
                            new GraphVariableFact(8, null),
                            new GraphVariableFact(9, 999999)));
            return mutations;
        }

        @Override
        public GraphSnapshot inspect(
                int homeBankingId,
                int botJobId,
                long workspaceEpoch)
                throws SQLException {
            inspectCalls++;
            recordIdentity(homeBankingId, botJobId, workspaceEpoch);
            return snapshot;
        }

        @Override
        public CommitResult mutate(
                int homeBankingId,
                int botJobId,
                long workspaceEpoch,
                InstructionGraphMutationV3.Request request,
                String mutationProfile)
                throws SQLException {
            mutateCalls++;
            recordIdentity(homeBankingId, botJobId, workspaceEpoch);
            lastRequest = request;
            lastMutationProfile = mutationProfile;
            if (commitResult == null) {
                commitResult = new CommitResult(
                        OwnerKey.botJob(homeBankingId, botJobId),
                        workspaceEpoch,
                        request.requestId(),
                        request.baseGraphVersion(),
                        request.baseGraphVersion() + 1L,
                        "mutation-after");
            }
            afterMutate.run();
            return commitResult;
        }

        private void recordIdentity(
                int homeBankingId,
                int botJobId,
                long workspaceEpoch) {
            lastHomeBankingId = homeBankingId;
            lastBotJobId = botJobId;
            lastWorkspaceEpoch = workspaceEpoch;
        }
    }

    private static final class FakeVariableDeletes
            implements VariablesWorkspaceService.VariableDeletePort {
        private DeleteResult result;
        private int calls;
        private int lastHomeBankingId;
        private int lastBotJobId;
        private long lastWorkspaceEpoch;
        private VariablesWorkspaceVariableDelete.Request lastRequest;

        @Override
        public DeleteResult delete(
                int homeBankingId,
                int botJobId,
                long workspaceEpoch,
                VariablesWorkspaceVariableDelete.Request request)
                throws SQLException {
            calls++;
            lastHomeBankingId = homeBankingId;
            lastBotJobId = botJobId;
            lastWorkspaceEpoch = workspaceEpoch;
            lastRequest = request;
            if (result != null) return result;
            return new DeleteResult(
                    OwnerKey.botJob(homeBankingId, botJobId),
                    workspaceEpoch,
                    request.requestId(),
                    request.mode(),
                    request.variableIds(),
                    request.variableIds().size(),
                    0,
                    4L,
                    5L,
                    "deletion-after");
        }
    }

    private static final class FakeInstructionCopies
            implements VariablesWorkspaceService.InstructionCopyPort {
        private CopyResult result;
        private int calls;
        private int lastHomeBankingId;
        private int lastBotJobId;
        private long lastWorkspaceEpoch;

        @Override
        public CopyResult copy(
                int homeBankingId,
                int botJobId,
                long workspaceEpoch,
                VariablesInstructionCopyV1.Request request)
                throws SQLException {
            calls++;
            lastHomeBankingId = homeBankingId;
            lastBotJobId = botJobId;
            lastWorkspaceEpoch = workspaceEpoch;
            if (result != null) return result;
            return new CopyResult(
                    OwnerKey.botJob(homeBankingId, botJobId),
                    workspaceEpoch,
                    request.requestId(),
                    request.scope(),
                    request.selectedInstructionId(),
                    request.targetBlockId(),
                    request.sourceInstructionIds(),
                    Map.of(request.selectedInstructionId(), 900),
                    Map.of(),
                    0,
                    request.baseGraphVersion(),
                    request.baseGraphVersion() + 1L,
                    "copy-after",
                    false);
        }
    }

    private record Sent(
            int homeBankingId, String sessionId, String operationId, JsonObject body) {}
}

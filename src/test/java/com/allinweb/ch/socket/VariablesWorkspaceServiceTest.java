package com.allinweb.ch.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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
    void retiresOnlyTheMatchingBotJob() {
        service.openForBotJob(5);

        assertFalse(service.retireForBotJob(6, "wrong job"));
        assertEquals(0, windows.closes);
        assertTrue(service.retireForBotJob(5, "job closed"));
        assertEquals(1, windows.closes);
        JsonObject tombstone = windows.sent.get(windows.sent.size() - 1).body();
        assertTrue(tombstone.get("retired").getAsBoolean());
        assertFalse(tombstone.get("preserveSnapshot").getAsBoolean());
        assertFalse(service.publishIfOpen(5));
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
        private int launches;
        private int closes;
        private int forceCloses;
        private int sendAttempts;
        private int openOrFocusCalls;
        private int lastBotJobId;
        private boolean closeResult = true;
        private boolean forceCloseResult = true;
        private boolean sendResult = true;
        private final List<Sent> sent = new ArrayList<>();

        void register(Session transport) {
            registered = transport;
            open = true;
        }

        @Override
        public boolean isOpen(String sessionId) {
            return open;
        }

        @Override
        public boolean isRegistered(String sessionId, Session transport) {
            return VariablesWorkspaceService.WORKSPACE_SESSION_ID.equals(sessionId)
                    && transport == registered
                    && transport != null
                    && transport.isOpen();
        }

        @Override
        public boolean openOrFocus(String sessionId, int botJobId, String reason) {
            openOrFocusCalls++;
            lastBotJobId = botJobId;
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

        @Override
        public void executeMutation(Runnable task) {
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

    private record Sent(
            int homeBankingId, String sessionId, String operationId, JsonObject body) {}
}

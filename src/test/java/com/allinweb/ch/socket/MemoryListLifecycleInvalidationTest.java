package com.allinweb.ch.socket;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.allinweb.ch.facade.BotJobDetailsWorkspaceRegistry;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.DetachedWorkspaceSessions;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.websocket.Session;
import javax.websocket.RemoteEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MemoryListLifecycleInvalidationTest {

    private final MemoryListWorkspaceService service =
            MemoryListWorkspaceService.getInstance();

    @BeforeEach
    void startWithCleanSingletonState() throws Exception {
        clearSingletonState();
    }

    @AfterEach
    void clearSingletonState() throws Exception {
        service.allBotJobsReplaced();
        BotJobDetailsWorkspaceRegistry.getInstance().closeActive();
        WebSocketSessionManager.clearSessions();
    }

    @Test
    void committedDeleteRetiresTheCompleteOwnerAndClosesItsStaleWindow()
            throws Exception {
        seedMemoryOwner(42, 7, "owner-epoch");
        Session memoryWindow = memoryWindow();
        WebSocketSessionManager.addSession(
                DetachedWorkspaceSessions.MEMORY_LIST_MANAGER, memoryWindow);

        service.botJobsDeleted(List.of(99));
        assertNotNull(currentState());

        service.botJobsDeleted(List.of(42));

        assertNull(currentState());
        assertNull(WebSocketSessionManager.getSession(
                DetachedWorkspaceSessions.MEMORY_LIST_MANAGER));
        verify(memoryWindow).close();
    }

    @Test
    void fullReplacementRetiresMemoryEvenWithoutAPageMappingsBinding()
            throws Exception {
        seedMemoryOwner(42, 7, "owner-epoch");

        service.allBotJobsReplaced();

        assertNull(currentState());
    }

    @Test
    void everyGenericSourceOpenLinearizesWithDeleteAndRejectsSameIdReuse()
            throws Exception {
        assertSourceCannotRecreateAfterDelete(ScannerWorkspaceSessions.BOT_JOB_TASKS);
        assertSourceCannotRecreateAfterDelete(ScannerWorkspaceSessions.SCANNER_GRID);
        assertSourceCannotRecreateAfterDelete(ScannerWorkspaceSessions.COMPONENT_TASKS);
    }

    @Test
    void staticSourcesRequireTheExactWorkspaceEpochAfterBotJobIdReuse()
            throws Exception {
        for (String sourceSessionId : List.of(
                ScannerWorkspaceSessions.BOT_JOB_TASKS,
                ScannerWorkspaceSessions.SCANNER_GRID,
                ScannerWorkspaceSessions.COMPONENT_TASKS)) {
            service.allBotJobsReplaced();
            WebSocketSessionManager.clearSessions();
            BotJobDetailsWorkspaceRegistry registry =
                    BotJobDetailsWorkspaceRegistry.getInstance();
            registry.closeActive();
            BotJobDetailsWorkspaceRegistry.Snapshot original =
                    registry.activate(botJob(42, 7, "Original"), false);
            Session source = authoritativeSession(sourceSessionId);
            WebSocketSessionManager.addSession(sourceSessionId, source);
            JsonObject stale = sourceRequest(42, 7, original.workspaceEpoch());

            registry.retire(42);
            BotJobDetailsWorkspaceRegistry.Snapshot reused =
                    registry.activate(botJob(42, 7, "Reused"), false);

            assertFalse(service.summary(stale, sourceSessionId, source)
                    .get("ok").getAsBoolean());
            JsonObject missing = sourceRequest(42, 7, reused.workspaceEpoch());
            missing.remove("workspaceEpoch");
            missing.getAsJsonObject("snapshot").remove("workspaceEpoch");
            assertFalse(service.summary(missing, sourceSessionId, source)
                    .get("ok").getAsBoolean());
            assertTrue(service.summary(
                            sourceRequest(42, 7, reused.workspaceEpoch()),
                            sourceSessionId,
                            source)
                    .get("ok").getAsBoolean());
        }
    }

    @Test
    void oldGenerationSummarySubscriberReceivesClearedValuesForSameIdReuse()
            throws Exception {
        BotJobDetailsWorkspaceRegistry registry =
                BotJobDetailsWorkspaceRegistry.getInstance();
        BotJobDetailsWorkspaceRegistry.Snapshot original =
                registry.activate(botJob(42, 7, "Original"), false);
        Session source = authoritativeSession(ScannerWorkspaceSessions.BOT_JOB_TASKS);
        WebSocketSessionManager.addSession(ScannerWorkspaceSessions.BOT_JOB_TASKS, source);
        Object replacementState = newMemoryOwner(
                42, 7, original.workspaceEpoch() + 1, "replacement-owner");
        setStateRevision(replacementState, 17);
        setCurrentState(replacementState);

        JsonObject response = service.summary(
                sourceRequest(42, 7, original.workspaceEpoch()),
                ScannerWorkspaceSessions.BOT_JOB_TASKS,
                source);

        assertTrue(response.get("ok").getAsBoolean());
        assertEquals(original.workspaceEpoch(), response.get("workspaceEpoch").getAsLong());
        assertEquals(0, response.get("itemCount").getAsInt());
        assertEquals(0, response.get("revision").getAsLong());
        assertEquals("", response.get("ownerEpoch").getAsString());
    }

    @Test
    void nestedSnapshotAndCommandResponseCarryCanonicalWorkspaceEpoch()
            throws Exception {
        Object owner = newMemoryOwner(42, 7, 73, "owner-epoch");
        setCurrentState(owner);
        Method snapshotResponse = MemoryListWorkspaceService.class.getDeclaredMethod(
                "snapshotResponse", owner.getClass(), String.class);
        snapshotResponse.setAccessible(true);

        JsonObject snapshotEnvelope = (JsonObject) snapshotResponse.invoke(
                service, owner, "Memory List loaded.");

        assertEquals(
                73,
                snapshotEnvelope
                        .getAsJsonObject("snapshot")
                        .get("workspaceEpoch")
                        .getAsLong());

        Session memoryTransport = memoryWindow();
        WebSocketSessionManager.addSession(
                DetachedWorkspaceSessions.MEMORY_LIST_MANAGER, memoryTransport);
        JsonObject command = new JsonObject();
        command.addProperty("requestId", "workspace-epoch-command");
        command.addProperty("ownerEpoch", "owner-epoch");
        command.addProperty("command", "CLEAR");

        JsonObject commandResponse = service.command(
                command,
                DetachedWorkspaceSessions.MEMORY_LIST_MANAGER,
                memoryTransport);

        assertTrue(commandResponse.get("ok").getAsBoolean());
        assertEquals(73, commandResponse.get("workspaceEpoch").getAsLong());
    }

    @Test
    void deleteClosesOnlyTheCapturedMemoryTransportAndNeverItsReplacement()
            throws Exception {
        seedMemoryOwner(42, 7, "owner-old");
        CountDownLatch oldCloseStarted = new CountDownLatch(1);
        CountDownLatch releaseOldClose = new CountDownLatch(1);
        Session oldWindow = memoryWindow();
        doAnswer(invocation -> {
                    oldCloseStarted.countDown();
                    assertTrue(releaseOldClose.await(5, TimeUnit.SECONDS));
                    return null;
                })
                .when(oldWindow)
                .close();
        WebSocketSessionManager.addSession(
                DetachedWorkspaceSessions.MEMORY_LIST_MANAGER, oldWindow);

        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<?> deletion = worker.submit(() -> service.botJobsDeleted(List.of(42)));
            assertTrue(oldCloseStarted.await(5, TimeUnit.SECONDS));
            assertNull(WebSocketSessionManager.getSession(
                    DetachedWorkspaceSessions.MEMORY_LIST_MANAGER));

            Object replacementOwner = seedMemoryOwner(42, 7, "owner-reused");
            Session replacement = memoryWindow();
            assertTrue(WebSocketSessionManager.addSession(
                    DetachedWorkspaceSessions.MEMORY_LIST_MANAGER, replacement));
            releaseOldClose.countDown();
            deletion.get(5, TimeUnit.SECONDS);

            assertSame(replacementOwner, currentState());
            assertSame(
                    replacement,
                    WebSocketSessionManager.getSession(
                            DetachedWorkspaceSessions.MEMORY_LIST_MANAGER));
            verify(replacement, never()).close();
        } finally {
            releaseOldClose.countDown();
            worker.shutdownNow();
        }
    }

    @Test
    void staleLaunchFailureCannotClearTheReusedOwnersPendingGeneration()
            throws Exception {
        CountDownLatch oldLaunchStarted = new CountDownLatch(1);
        CountDownLatch releaseOldLaunch = new CountDownLatch(1);
        AtomicInteger launches = new AtomicInteger();
        MemoryListWorkspaceService isolated = new MemoryListWorkspaceService(
                (botJobId, capability) -> {
                    if (launches.incrementAndGet() == 1) {
                        oldLaunchStarted.countDown();
                        try {
                            releaseOldLaunch.await(5, TimeUnit.SECONDS);
                            return false;
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            return false;
                        }
                    }
                    return true;
                },
                testPageScannerCoordinator());
        BotJobDetailsWorkspaceRegistry registry =
                BotJobDetailsWorkspaceRegistry.getInstance();
        registry.closeActive();
        BotJobDetailsWorkspaceRegistry.Snapshot original =
                registry.activate(botJob(42, 7, "Original"), false);
        Session oldSource = authoritativeSession(ScannerWorkspaceSessions.BOT_JOB_TASKS);
        WebSocketSessionManager.addSession(
                ScannerWorkspaceSessions.BOT_JOB_TASKS, oldSource);

        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<JsonObject> staleOpen = worker.submit(() -> isolated.open(
                    sourceRequest(42, 7, original.workspaceEpoch()),
                    ScannerWorkspaceSessions.BOT_JOB_TASKS,
                    oldSource));
            assertTrue(oldLaunchStarted.await(5, TimeUnit.SECONDS));

            registry.retire(42);
            isolated.botJobsDeleted(List.of(42));
            BotJobDetailsWorkspaceRegistry.Snapshot reused =
                    registry.activate(botJob(42, 7, "Reused"), false);
            Session replacementSource =
                    authoritativeSession(ScannerWorkspaceSessions.BOT_JOB_TASKS);
            WebSocketSessionManager.takeOverSession(
                    ScannerWorkspaceSessions.BOT_JOB_TASKS, replacementSource);
            JsonObject reusedOpen = isolated.open(
                    sourceRequest(42, 7, reused.workspaceEpoch()),
                    ScannerWorkspaceSessions.BOT_JOB_TASKS,
                    replacementSource);
            assertTrue(reusedOpen.get("ok").getAsBoolean());
            Object reusedState = currentState(isolated);
            String reusedCapability = windowCapability(reusedState);
            assertTrue(launchPending(isolated));

            releaseOldLaunch.countDown();
            assertFalse(staleOpen.get(5, TimeUnit.SECONDS).get("ok").getAsBoolean());
            assertSame(reusedState, currentState(isolated));
            assertEquals(reusedCapability, windowCapability(currentState(isolated)));
            assertTrue(launchPending(isolated));
            assertEquals(2, launches.get());
        } finally {
            releaseOldLaunch.countDown();
            worker.shutdownNow();
            isolated.allBotJobsReplaced();
        }
    }

    @Test
    void connectedPendingWindowPreventsCapabilityRotationAndDuplicateLaunch()
            throws Exception {
        AtomicInteger launches = new AtomicInteger();
        MemoryListWorkspaceService isolated = new MemoryListWorkspaceService(
                (botJobId, capability) -> {
                    launches.incrementAndGet();
                    return true;
                },
                testPageScannerCoordinator());
        BotJobDetailsWorkspaceRegistry registry =
                BotJobDetailsWorkspaceRegistry.getInstance();
        registry.closeActive();
        BotJobDetailsWorkspaceRegistry.Snapshot owner =
                registry.activate(botJob(42, 7, "Owner"), false);
        Session source = authoritativeSession(ScannerWorkspaceSessions.BOT_JOB_TASKS);
        WebSocketSessionManager.addSession(ScannerWorkspaceSessions.BOT_JOB_TASKS, source);
        JsonObject request = sourceRequest(42, 7, owner.workspaceEpoch());

        assertTrue(isolated.open(
                        request, ScannerWorkspaceSessions.BOT_JOB_TASKS, source)
                .get("ok").getAsBoolean());
        Object state = currentState(isolated);
        String capability = windowCapability(state);
        RemoteEndpoint.Basic remote = mock(RemoteEndpoint.Basic.class);
        Session memoryWindow = memoryWindow(capability, remote);
        assertTrue(isolated.authorizeAndTakeOverWindowTransport(memoryWindow));

        JsonObject reopened = isolated.open(
                request, ScannerWorkspaceSessions.BOT_JOB_TASKS, source);

        assertTrue(reopened.get("ok").getAsBoolean());
        assertTrue(reopened.get("alreadyOpen").getAsBoolean());
        assertSame(state, currentState(isolated));
        assertEquals(capability, windowCapability(currentState(isolated)));
        assertEquals(1, launches.get());
        isolated.allBotJobsReplaced();
    }

    @Test
    void staleCapturedOwnerCannotPublishIntoTheReusedMemoryWindow()
            throws Exception {
        Object staleOwner = newMemoryOwner(42, 7, "owner-stale");
        Object reusedOwner = seedMemoryOwner(42, 7, "owner-reused");
        RemoteEndpoint.Basic replacementRemote = mock(RemoteEndpoint.Basic.class);
        Session replacement = memoryWindow(windowCapability(reusedOwner), replacementRemote);
        WebSocketSessionManager.addSession(
                DetachedWorkspaceSessions.MEMORY_LIST_MANAGER, replacement);

        Method publishSnapshot = MemoryListWorkspaceService.class.getDeclaredMethod(
                "publishSnapshot", staleOwner.getClass());
        publishSnapshot.setAccessible(true);
        publishSnapshot.invoke(service, staleOwner);

        verify(replacementRemote, never()).sendText(anyString());
        assertSame(reusedOwner, currentState());
        assertSame(
                replacement,
                WebSocketSessionManager.getSession(
                        DetachedWorkspaceSessions.MEMORY_LIST_MANAGER));
    }

    @Test
    void supersededMemoryTransportCannotBootstrapOrMutateAfterTakeover()
            throws Exception {
        Object owner = seedMemoryOwner(42, 7, "owner-epoch");
        CountDownLatch outerValidationPassed = new CountDownLatch(1);
        Session staleWindow = mock(Session.class);
        when(staleWindow.isOpen()).thenAnswer(invocation -> {
            outerValidationPassed.countDown();
            return true;
        });
        when(staleWindow.getRequestParameterMap()).thenReturn(Map.of(
                "sessionId", List.of(DetachedWorkspaceSessions.MEMORY_LIST_MANAGER)));
        WebSocketSessionManager.addSession(
                DetachedWorkspaceSessions.MEMORY_LIST_MANAGER, staleWindow);
        JsonObject command = new JsonObject();
        command.addProperty("requestId", "stale-memory-command");
        command.addProperty("ownerEpoch", "owner-epoch");
        command.addProperty("command", "CLEAR");

        Object lock = stateLock();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<JsonObject> staleCommand;
            Session replacement = memoryWindow();
            synchronized (lock) {
                staleCommand = worker.submit(() -> service.command(
                        command,
                        DetachedWorkspaceSessions.MEMORY_LIST_MANAGER,
                        staleWindow));
                assertTrue(outerValidationPassed.await(5, TimeUnit.SECONDS));
                WebSocketSessionManager.takeOverSession(
                        DetachedWorkspaceSessions.MEMORY_LIST_MANAGER, replacement);
            }

            assertFalse(staleCommand.get(5, TimeUnit.SECONDS).get("ok").getAsBoolean());
            assertEquals(0, stateRevision(owner));
            assertSame(
                    replacement,
                    WebSocketSessionManager.getSession(
                            DetachedWorkspaceSessions.MEMORY_LIST_MANAGER));

            CountDownLatch bootstrapOuterValidationPassed = new CountDownLatch(1);
            Session staleBootstrap = mock(Session.class);
            when(staleBootstrap.isOpen()).thenAnswer(invocation -> {
                bootstrapOuterValidationPassed.countDown();
                return true;
            });
            when(staleBootstrap.getRequestParameterMap()).thenReturn(Map.of(
                    "sessionId", List.of(DetachedWorkspaceSessions.MEMORY_LIST_MANAGER)));
            WebSocketSessionManager.takeOverSession(
                    DetachedWorkspaceSessions.MEMORY_LIST_MANAGER, staleBootstrap);
            Future<JsonObject> staleBootstrapResponse;
            Session finalOwner = memoryWindow();
            synchronized (lock) {
                staleBootstrapResponse = worker.submit(() -> service.bootstrap(
                        new JsonObject(),
                        DetachedWorkspaceSessions.MEMORY_LIST_MANAGER,
                        staleBootstrap));
                assertTrue(bootstrapOuterValidationPassed.await(5, TimeUnit.SECONDS));
                WebSocketSessionManager.takeOverSession(
                        DetachedWorkspaceSessions.MEMORY_LIST_MANAGER, finalOwner);
            }
            assertFalse(staleBootstrapResponse.get(5, TimeUnit.SECONDS)
                    .get("ok")
                    .getAsBoolean());
            assertSame(
                    finalOwner,
                    WebSocketSessionManager.getSession(
                            DetachedWorkspaceSessions.MEMORY_LIST_MANAGER));
        } finally {
            worker.shutdownNow();
        }
    }

    @Test
    void detachedPageScannerMemoryUsesCoordinatorBeforeStateWithoutDeadlock()
            throws Exception {
        PageScannerWorkspaceCoordinator coordinator = testPageScannerCoordinator();
        PageScannerWorkspaceCoordinator.OpenResult opened = coordinator.open(
                new PageScannerWorkspaceCoordinator.OpenRequest(
                        ScannerWorkspaceSessions.BOT_JOB_TASKS,
                        scannerContext(7, 42, 21, "Payments")));
        MemoryListWorkspaceService isolated = new MemoryListWorkspaceService(
                (botJobId, capability) -> true,
                coordinator);
        CountDownLatch exactTransportChecked = new CountDownLatch(1);
        Session scanner = mock(Session.class);
        when(scanner.isOpen()).thenAnswer(invocation -> {
            exactTransportChecked.countDown();
            return true;
        });
        when(scanner.getRequestParameterMap()).thenReturn(
                Map.of("sessionId", List.of(opened.sessionId())));
        WebSocketSessionManager.addSession(opened.sessionId(), scanner);
        JsonObject request = sourceRequest(42, 7, 21);

        Object lock = stateLock(isolated);
        CountDownLatch followerAttempting = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<JsonObject> memorySummary;
            Future<Boolean> coordinatorFollower;
            synchronized (lock) {
                memorySummary = workers.submit(() -> isolated.summary(
                        request, opened.sessionId(), scanner));
                assertTrue(exactTransportChecked.await(5, TimeUnit.SECONDS));
                coordinatorFollower = workers.submit(() -> {
                    followerAttempting.countDown();
                    return coordinator.withAuthoritativeContext(
                            opened.sessionId(), context -> true);
                });
                assertTrue(followerAttempting.await(5, TimeUnit.SECONDS));
                assertFalse(coordinatorFollower.isDone());
            }

            assertTrue(memorySummary.get(5, TimeUnit.SECONDS).get("ok").getAsBoolean());
            assertTrue(coordinatorFollower.get(5, TimeUnit.SECONDS));
        } finally {
            workers.shutdownNow();
            isolated.allBotJobsReplaced();
        }
    }

    private void assertSourceCannotRecreateAfterDelete(String sourceSessionId)
            throws Exception {
        service.allBotJobsReplaced();
        WebSocketSessionManager.clearSessions();
        BotJobDetailsWorkspaceRegistry registry =
                BotJobDetailsWorkspaceRegistry.getInstance();
        registry.closeActive();
        BotJobDetailsWorkspaceRegistry.Snapshot original =
                registry.activate(botJob(42, 7, "Original"), false);

        CountDownLatch requestValidated = new CountDownLatch(1);
        Session source = mock(Session.class);
        when(source.isOpen()).thenAnswer(invocation -> {
            requestValidated.countDown();
            return true;
        });
        when(source.getRequestParameterMap()).thenReturn(
                Map.of("sessionId", List.of(sourceSessionId)));
        WebSocketSessionManager.addSession(sourceSessionId, source);
        JsonObject request = sourceRequest(42, 7, original.workspaceEpoch());

        Object stateLock = stateLock();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<JsonObject> staleOpen;
            Future<?> deletion;
            synchronized (stateLock) {
                staleOpen = workers.submit(() -> service.open(
                        request, sourceSessionId, source));
                assertTrue(requestValidated.await(5, TimeUnit.SECONDS));
                deletion = workers.submit(() -> {
                    registry.retire(42);
                    WebSocketSessionManager.closeSession(sourceSessionId);
                    service.botJobsDeleted(List.of(42));
                });
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (registrySnapshotIsOpen(registry, 42)
                        && System.nanoTime() < deadline) {
                    Thread.onSpinWait();
                }
                assertFalse(registrySnapshotIsOpen(registry, 42));
            }

            assertFalse(staleOpen.get(5, TimeUnit.SECONDS).get("ok").getAsBoolean());
            deletion.get(5, TimeUnit.SECONDS);
            assertNull(currentState());

            BotJobDetailsWorkspaceRegistry.Snapshot reused =
                    registry.activate(botJob(42, 7, "Reused"), false);
            assertFalse(service.summary(request, sourceSessionId, source)
                    .get("ok").getAsBoolean());
            Session replacement = authoritativeSession(sourceSessionId);
            WebSocketSessionManager.addSession(sourceSessionId, replacement);
            JsonObject reusedRequest = sourceRequest(42, 7, reused.workspaceEpoch());
            assertTrue(service.summary(reusedRequest, sourceSessionId, replacement)
                    .get("ok").getAsBoolean());
        } finally {
            workers.shutdownNow();
            registry.closeActive();
            service.allBotJobsReplaced();
            WebSocketSessionManager.clearSessions();
        }
    }

    private Session memoryWindow() {
        Session session = mock(Session.class);
        when(session.isOpen()).thenReturn(true);
        when(session.getRequestParameterMap()).thenReturn(Map.of(
                "sessionId", List.of(DetachedWorkspaceSessions.MEMORY_LIST_MANAGER)));
        return session;
    }

    private Session memoryWindow(String capability, RemoteEndpoint.Basic remote) {
        Session session = mock(Session.class);
        when(session.isOpen()).thenReturn(true);
        when(session.getBasicRemote()).thenReturn(remote);
        when(session.getRequestParameterMap()).thenReturn(Map.of(
                "sessionId",
                List.of(DetachedWorkspaceSessions.MEMORY_LIST_MANAGER),
                "windowCapability",
                List.of(capability)));
        return session;
    }

    private Object seedMemoryOwner(int botJobId, int homeBankingId, String ownerEpoch)
            throws Exception {
        Object owner = newMemoryOwner(botJobId, homeBankingId, ownerEpoch);
        setCurrentState(owner);
        return owner;
    }

    private static Object newMemoryOwner(
            int botJobId, int homeBankingId, String ownerEpoch) throws Exception {
        return newMemoryOwner(botJobId, homeBankingId, 0, ownerEpoch);
    }

    private static Object newMemoryOwner(
            int botJobId,
            int homeBankingId,
            long workspaceEpoch,
            String ownerEpoch) throws Exception {
        Class<?> stateClass = Class.forName(
                MemoryListWorkspaceService.class.getName() + "$MemoryState");
        Constructor<?> constructor =
                stateClass.getDeclaredConstructor(
                        int.class, int.class, long.class, String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(
                botJobId, homeBankingId, workspaceEpoch, ownerEpoch);
    }

    private Object currentState() throws Exception {
        return currentState(service);
    }

    private static Object currentState(MemoryListWorkspaceService target) throws Exception {
        Field current = MemoryListWorkspaceService.class.getDeclaredField("current");
        current.setAccessible(true);
        return current.get(target);
    }

    private void setCurrentState(Object value) throws Exception {
        Field current = MemoryListWorkspaceService.class.getDeclaredField("current");
        current.setAccessible(true);
        current.set(service, value);
    }

    private Object stateLock() throws Exception {
        return stateLock(service);
    }

    private static Object stateLock(MemoryListWorkspaceService target) throws Exception {
        Field lock = MemoryListWorkspaceService.class.getDeclaredField("stateLock");
        lock.setAccessible(true);
        return lock.get(target);
    }

    private static boolean launchPending(MemoryListWorkspaceService target) throws Exception {
        Field pending = MemoryListWorkspaceService.class.getDeclaredField("launchPending");
        pending.setAccessible(true);
        return pending.getBoolean(target);
    }

    private static String windowCapability(Object state) throws Exception {
        Field capability = state.getClass().getDeclaredField("windowCapability");
        capability.setAccessible(true);
        return (String) capability.get(state);
    }

    private static long stateRevision(Object state) throws Exception {
        Field revision = state.getClass().getDeclaredField("revision");
        revision.setAccessible(true);
        return revision.getLong(state);
    }

    private static void setStateRevision(Object state, long value) throws Exception {
        Field revision = state.getClass().getDeclaredField("revision");
        revision.setAccessible(true);
        revision.setLong(state, value);
    }

    private static boolean registrySnapshotIsOpen(
            BotJobDetailsWorkspaceRegistry registry, int botJobId) {
        try {
            registry.require(botJobId);
            return true;
        } catch (IllegalArgumentException inactive) {
            return false;
        }
    }

    private static BotJobLoadDTO botJob(
            int botJobId, int homeBankingId, String botJobName) {
        BotJobLoadDTO botJob = new BotJobLoadDTO();
        botJob.setId(botJobId);
        botJob.setHomeBankingId(homeBankingId);
        botJob.setName(botJobName);
        return botJob;
    }

    private static JsonObject sourceRequest(
            int botJobId, int homeBankingId, long workspaceEpoch) {
        JsonObject snapshot = new JsonObject();
        snapshot.addProperty("botJobId", botJobId);
        snapshot.addProperty("homeBankingId", homeBankingId);
        snapshot.addProperty("workspaceEpoch", workspaceEpoch);
        snapshot.addProperty("botJobName", "Job " + botJobId);
        snapshot.add("items", new JsonArray());
        snapshot.add("blocks", new JsonArray());
        JsonObject body = new JsonObject();
        body.addProperty("requestId", "memory-source-race");
        body.addProperty("botJobId", botJobId);
        body.addProperty("homeBankingId", homeBankingId);
        body.addProperty("workspaceEpoch", workspaceEpoch);
        body.add("snapshot", snapshot);
        return body;
    }

    private static Session authoritativeSession(String sessionId) {
        Session session = mock(Session.class);
        when(session.isOpen()).thenReturn(true);
        when(session.getRequestParameterMap())
                .thenReturn(Map.of("sessionId", List.of(sessionId)));
        return session;
    }

    private static PageScannerWorkspaceCoordinator testPageScannerCoordinator() {
        return new PageScannerWorkspaceCoordinator(
                sessionId -> true,
                () -> "memory-lifecycle",
                Clock.fixed(Instant.parse("2026-08-08T12:00:00Z"), ZoneOffset.UTC));
    }

    private static PageScannerWorkspaceCoordinator.WorkspaceContext scannerContext(
            int homeBankingId,
            int botJobId,
            long workspaceEpoch,
            String botJobName) {
        return new PageScannerWorkspaceCoordinator.WorkspaceContext(
                homeBankingId,
                botJobId,
                workspaceEpoch,
                botJobName,
                11,
                "https://bank.example/login",
                "chromium",
                "--start-maximized",
                "C:\\ARWeb\\data");
    }
}

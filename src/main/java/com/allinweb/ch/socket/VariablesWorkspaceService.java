package com.allinweb.ch.socket;

import com.allinweb.ch.facade.BotJobDetailsWorkspaceRegistry;
import com.allinweb.ch.facade.VariableRelationshipService;
import com.allinweb.ch.model.DetachedWorkspaceSessions;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.websocket.Session;
import lombok.extern.slf4j.Slf4j;

/**
 * Owns the one detached Variables page and binds it to one exact active Bot Job workspace epoch.
 *
 * <p>The binding is backend-owned. Browser query parameters and request identity fields are never
 * accepted as authority. A failed refresh emits a structured preserve-snapshot error so the React
 * page can keep rendering its last known-good graph.
 */
@Slf4j
public final class VariablesWorkspaceService {
    public static final String WORKSPACE_SESSION_ID =
            DetachedWorkspaceSessions.VARIABLES_MANAGER;
    public static final String SNAPSHOT_OPERATION = "variablesWorkspace.snapshot";
    private static final long RELOAD_GRACE_MILLIS = 2_500L;

    private static final VariablesWorkspaceService INSTANCE =
            new VariablesWorkspaceService(
                     VariablesWorkspaceService::activeWorkspace,
                     VariableRelationshipService.getInstance()::load,
                     new DefaultWindowPort(),
                     new Gson(),
                     DefaultTaskPort.INSTANCE);

    private final WorkspacePort workspaces;
    private final GraphPort graphs;
    private final WindowPort windows;
    private final Gson gson;
    private final TaskPort tasks;
    private final Object stateLock = new Object();
    private Binding binding;
    private Session managerTransport;
    private long disconnectGeneration;

    VariablesWorkspaceService(
             WorkspacePort workspaces, GraphPort graphs, WindowPort windows, Gson gson) {
        this(workspaces, graphs, windows, gson, DefaultTaskPort.INSTANCE);
    }

    VariablesWorkspaceService(
            WorkspacePort workspaces,
            GraphPort graphs,
            WindowPort windows,
            Gson gson,
            TaskPort tasks) {
        this.workspaces = workspaces;
        this.graphs = graphs;
        this.windows = windows;
        this.gson = gson;
        this.tasks = tasks;
    }

    public static VariablesWorkspaceService getInstance() {
        return INSTANCE;
    }

    /** Opens or retargets the single Variables page for an already-authorized Bot Job action. */
    public JsonObject openForBotJob(int botJobId) {
        JsonObject request = new JsonObject();
        request.addProperty("botJobId", botJobId);
        try {
            // Registry work is deliberately outside the Variables state monitor.
            WorkspaceContext workspace = workspaces.require(botJobId, 0L);
            Binding next = new Binding(
                    UUID.randomUUID().toString(),
                    workspace.workspaceEpoch(),
                    workspace.botJobId(),
                    workspace.homeBankingId(),
                    workspace.botJobName(),
                    workspace.organizationName(),
                    "");
            Binding previous;
            synchronized (stateLock) {
                previous = binding;
                binding = next;
                disconnectGeneration++;
            }

            boolean alreadyOpen = windows.isOpen(WORKSPACE_SESSION_ID);
            boolean opened;
            try {
                opened = windows.openOrFocus(
                        WORKSPACE_SESSION_ID,
                        workspace.botJobId(),
                        "Bot Job Details requested the Variables workspace.");
            } catch (RuntimeException error) {
                restoreIfCurrent(next, previous);
                throw error;
            }
            if (!opened) {
                restoreIfCurrent(next, previous);
                return failure(
                        request,
                        "The Variables workspace could not be opened.",
                        currentBinding());
            }

            Binding current = currentBindingFor(next.botJobId(), next.workspaceEpoch());
            if (current == null) {
                return failure(
                        request,
                        "The Variables workspace target changed before it could be opened.",
                        next);
            }
            JsonObject response = success(
                    request,
                    current,
                    alreadyOpen
                            ? "Variables workspace retargeted and brought to front."
                            : "Variables workspace opened.");
            response.addProperty("alreadyOpen", alreadyOpen);
            response.addProperty(
                    "retargeted",
                    alreadyOpen
                            && (previous == null
                                    || previous.botJobId() != current.botJobId()
                                    || previous.workspaceEpoch() != current.workspaceEpoch()));

            if (alreadyOpen) {
                JsonObject snapshot =
                        loadSnapshot(current, null, "Variables workspace synchronized.");
                if (isCurrent(current)) {
                    boolean delivered = publish(snapshot, current);
                    if (delivered && isSuccessful(snapshot)) {
                        advanceDeliveredRevision(
                                current, text(snapshot, "graphRevision"));
                    }
                }
            }
            return response;
        } catch (IllegalArgumentException | IllegalStateException error) {
            return failure(request, error.getMessage(), currentBinding());
        } catch (RuntimeException error) {
            log.error("Unable to open the Variables workspace", error);
            return failure(
                    request,
                    "The Variables workspace could not be opened.",
                    currentBinding());
        }
    }

    /** Loads the page from the exact registered Variables transport. */
    public JsonObject bootstrap(
            JsonObject body, String requesterSessionId, Session requesterTransport) {
        return loadForManager(
                body,
                requesterSessionId,
                requesterTransport,
                "Variables workspace loaded.");
    }

    /** Explicitly refreshes the graph while preserving the prior UI snapshot on failure. */
    public JsonObject refresh(
            JsonObject body, String requesterSessionId, Session requesterTransport) {
        return loadForManager(
                body,
                requesterSessionId,
                requesterTransport,
                "Variable relationships refreshed.");
    }

    /**
     * Pushes a changed graph to the detached page. Calling this for execution color/status events
     * is intentionally unsupported; callers invoke it only after persisted graph mutations.
     */
    public boolean publishIfOpen(int botJobId) {
        Binding current = currentBinding();
        if (current == null
                || current.botJobId() != botJobId
                || !windows.isOpen(WORKSPACE_SESSION_ID)) {
            return false;
        }
        try {
            WorkspaceContext workspace =
                    workspaces.require(current.botJobId(), current.workspaceEpoch());
            current = current.withWorkspace(workspace);
            JsonObject snapshot =
                    loadSnapshot(current, null, "Variable relationships updated.");
            if (!isSuccessful(snapshot)) {
                if (isCurrent(current)) publish(snapshot, current);
                return false;
            }
            String revision = text(snapshot, "graphRevision");
            Binding latest = currentBinding(current.bindingEpoch());
            if (latest == null) return false;
            if (!revision.isBlank() && revision.equals(latest.graphRevision())) {
                return false;
            }
            boolean delivered = publish(snapshot, latest);
            if (delivered) {
                advanceDeliveredRevision(latest, revision);
            }
            return delivered;
        } catch (IllegalArgumentException | IllegalStateException staleWorkspace) {
            JsonObject error = failure(null, staleWorkspace.getMessage(), current);
            if (isCurrent(current)) publish(error, current);
            return false;
        } catch (RuntimeException error) {
            log.warn(
                    "Unable to publish Variables snapshot for Bot Job {}: {}",
                    botJobId,
                    error.getMessage());
            JsonObject response = failure(
                    null,
                    "Variable relationships could not be refreshed.",
                    current);
            if (isCurrent(current)) publish(response, current);
            return false;
        }
    }

    /**
     * Queues relationship refresh after the caller's transaction/registry monitor can unwind.
     */
    public void notifyMutation(int botJobId) {
        if (botJobId <= 0) return;
        tasks.executeMutation(() -> publishIfOpen(botJobId));
    }

    /** Retires the binding and closes only this detached page. */
    public boolean retireForBotJob(int botJobId, String reason) {
        RetireResult result = retireForBotJobDetailed(botJobId, reason);
        return result.matched() && result.closed();
    }

    public RetireResult retireForBotJobDetailed(int botJobId, String reason) {
        Binding retired;
        synchronized (stateLock) {
            if (binding == null || binding.botJobId() != botJobId) {
                return new RetireResult(false, true, false, false);
            }
            retired = binding;
            binding = null;
            managerTransport = null;
            disconnectGeneration++;
        }

        String closeReason = reason == null || reason.isBlank()
                ? "The Variables workspace was retired."
                : reason;
        boolean wasOpen = windows.isOpen(WORKSPACE_SESSION_ID);
        boolean tombstoneDelivered = !wasOpen || publishRetired(retired, closeReason);
        boolean closeRequested = !wasOpen || windows.close(WORKSPACE_SESSION_ID, closeReason);
        boolean forceClosed = false;
        if (wasOpen && !closeRequested) {
            forceClosed = windows.forceClose(WORKSPACE_SESSION_ID, closeReason);
        }
        boolean closed = !wasOpen || closeRequested || forceClosed;
        if (!closed || !tombstoneDelivered) {
            log.warn(
                    "Variables workspace retirement incomplete for Bot Job {}: tombstoneDelivered={} closeRequested={} forceClosed={}",
                    botJobId,
                    tombstoneDelivered,
                    closeRequested,
                    forceClosed);
        }
        return new RetireResult(true, closed, tombstoneDelivered, forceClosed);
    }

    /**
     * Rotates the binding epoch for a newly connected/reloaded singleton transport, or closes a
     * shell that connected after its Bot Job binding had already retired.
     */
    public void connected(String sessionId, Session transport) {
        if (!isWorkspaceSession(sessionId)
                || !windows.isRegistered(sessionId, transport)) {
            return;
        }
        Binding current = currentBinding();
        if (current == null) {
            sendRetiredClose();
            return;
        }
        try {
            workspaces.require(current.botJobId(), current.workspaceEpoch());
        } catch (IllegalArgumentException | IllegalStateException stale) {
            clearIfCurrent(current);
            sendRetiredClose();
            return;
        }
        synchronized (stateLock) {
            if (binding == null
                    || !binding.bindingEpoch().equals(current.bindingEpoch())) {
                return;
            }
            managerTransport = transport;
            binding = binding.withBindingEpoch(UUID.randomUUID().toString());
            disconnectGeneration++;
        }
    }

    /**
     * Preserves a short tombstone window for browser reloads whose old socket closes before the new
     * socket opens. If no replacement connects, an explicit page close retires after the grace.
     */
    public void disconnected(String sessionId, Session transport) {
        Binding disconnectedBinding;
        long generation;
        synchronized (stateLock) {
            if (!isWorkspaceSession(sessionId)
                    || transport == null
                    || managerTransport != transport) {
                return;
            }
            managerTransport = null;
            disconnectedBinding = binding;
            generation = ++disconnectGeneration;
        }
        if (disconnectedBinding == null) return;
        tasks.scheduleDisconnect(
                () -> expireDisconnectedBinding(disconnectedBinding, generation),
                RELOAD_GRACE_MILLIS);
    }

    private void expireDisconnectedBinding(Binding expected, long generation) {
        // Probe the owner outside stateLock. Both a stale owner and an explicit close retire after
        // the grace; an authoritative reconnect invalidates this generation.
        try {
            workspaces.require(expected.botJobId(), expected.workspaceEpoch());
        } catch (IllegalArgumentException | IllegalStateException ignored) {
            // The guarded state transition below is the same for either condition.
        }
        synchronized (stateLock) {
            if (disconnectGeneration != generation
                    || managerTransport != null
                    || binding == null
                    || !binding.bindingEpoch().equals(expected.bindingEpoch())) {
                return;
            }
            binding = null;
            disconnectGeneration++;
        }
    }

    public static boolean isWorkspaceSession(String sessionId) {
        return WORKSPACE_SESSION_ID.equals(sessionId);
    }

    private JsonObject loadForManager(
            JsonObject body,
            String requesterSessionId,
            Session requesterTransport,
            String message) {
        JsonObject request = body == null ? new JsonObject() : body;
        Binding current = null;
        try {
            requireManagerTransport(requesterSessionId, requesterTransport);
            current = currentBinding();
            if (current == null) {
                throw new IllegalArgumentException(
                        "No Bot Job is bound to the Variables workspace.");
            }
            String requestedEpoch = text(request, "bindingEpoch");
            if (!requestedEpoch.isBlank()
                    && !requestedEpoch.equals(current.bindingEpoch())) {
                throw new IllegalArgumentException(
                        "The Variables target changed. Reload the current Bot Job.");
            }

            WorkspaceContext workspace =
                    workspaces.require(current.botJobId(), current.workspaceEpoch());
            current = current.withWorkspace(workspace);
            JsonObject snapshot = loadSnapshot(current, request, message);
            if (!isCurrent(current)
                    || !isManagerTransport(requesterTransport)) {
                return failure(
                        request,
                        "The Variables target changed while it was loading.",
                        currentBinding());
            }
            return snapshot;
        } catch (IllegalArgumentException | IllegalStateException error) {
            return failure(
                    request,
                    error.getMessage(),
                    current == null ? currentBinding() : current);
        } catch (RuntimeException error) {
            log.error("Unable to load the Variables workspace", error);
            return failure(
                    request,
                    "Variable relationships could not be loaded.",
                    current == null ? currentBinding() : current);
        }
    }

    private JsonObject loadSnapshot(
            Binding current, JsonObject request, String message) {
        JsonObject graph = graphs.load(current.botJobId());
        if (!isSuccessful(graph)) {
            String error = text(graph, "error");
            return failure(
                    request,
                    error.isBlank()
                            ? "Variable relationships could not be loaded."
                            : error,
                    current);
        }

        // Reject a graph that crossed a Bot Job activation boundary while its SQL was running.
        WorkspaceContext authoritative =
                workspaces.require(current.botJobId(), current.workspaceEpoch());
        JsonObject response = graph.deepCopy();
        response.addProperty("message", message);
        response.addProperty("bindingEpoch", current.bindingEpoch());
        response.addProperty("workspaceEpoch", current.workspaceEpoch());
        JsonObject botJob = new JsonObject();
        botJob.addProperty("id", authoritative.botJobId());
        botJob.addProperty("name", authoritative.botJobName());
        botJob.addProperty("homeBankingId", authoritative.homeBankingId());
        botJob.addProperty("organizationName", authoritative.organizationName());
        response.add("botJob", botJob);
        correlate(request, response);
        return response;
    }

    private boolean publish(JsonObject response, Binding current) {
        if (response == null
                || current == null
                || !windows.isOpen(WORKSPACE_SESSION_ID)) {
            return false;
        }
        JsonObject payload = response.deepCopy();
        if (!payload.has("bindingEpoch")) {
            payload.addProperty("bindingEpoch", current.bindingEpoch());
        }
        if (!payload.has("workspaceEpoch")) {
            payload.addProperty("workspaceEpoch", current.workspaceEpoch());
        }
        return windows.send(
                current.homeBankingId(),
                WORKSPACE_SESSION_ID,
                SNAPSHOT_OPERATION,
                payload);
    }

    private void requireManagerTransport(String sessionId, Session transport) {
        if (!isWorkspaceSession(sessionId)
                || !windows.isRegistered(sessionId, transport)
                || !isManagerTransport(transport)) {
            throw new IllegalArgumentException(
                    "The Variables workspace requester is not authoritative.");
        }
    }

    private JsonObject success(JsonObject request, Binding current, String message) {
        JsonObject response = new JsonObject();
        response.addProperty("ok", true);
        response.addProperty("message", message);
        response.addProperty("bindingEpoch", current.bindingEpoch());
        response.addProperty("workspaceEpoch", current.workspaceEpoch());
        response.addProperty("botJobId", current.botJobId());
        response.addProperty("homeBankingId", current.homeBankingId());
        correlate(request, response);
        return response;
    }

    private JsonObject failure(JsonObject request, String message, Binding current) {
        JsonObject response = new JsonObject();
        response.addProperty("ok", false);
        response.addProperty("preserveSnapshot", true);
        response.addProperty(
                "error",
                message == null || message.isBlank()
                        ? "The Variables workspace request was refused."
                        : message);
        if (current != null) {
            response.addProperty("bindingEpoch", current.bindingEpoch());
            response.addProperty("workspaceEpoch", current.workspaceEpoch());
            response.addProperty("botJobId", current.botJobId());
            response.addProperty("homeBankingId", current.homeBankingId());
        }
        correlate(request, response);
        return response;
    }

    private Binding currentBinding() {
        synchronized (stateLock) {
            return binding;
        }
    }

    private Binding currentBinding(String bindingEpoch) {
        synchronized (stateLock) {
            return binding != null
                            && binding.bindingEpoch().equals(bindingEpoch)
                    ? binding
                    : null;
        }
    }

    private Binding currentBindingFor(int botJobId, long workspaceEpoch) {
        synchronized (stateLock) {
            return binding != null
                            && binding.botJobId() == botJobId
                            && binding.workspaceEpoch() == workspaceEpoch
                    ? binding
                    : null;
        }
    }

    private boolean isCurrent(Binding expected) {
        return expected != null && currentBinding(expected.bindingEpoch()) != null;
    }

    private boolean isManagerTransport(Session transport) {
        synchronized (stateLock) {
            return transport != null && managerTransport == transport;
        }
    }

    private void restoreIfCurrent(Binding expected, Binding replacement) {
        synchronized (stateLock) {
            if (binding != null
                    && binding.bindingEpoch().equals(expected.bindingEpoch())) {
                binding = replacement;
                disconnectGeneration++;
            }
        }
    }

    private void clearIfCurrent(Binding expected) {
        synchronized (stateLock) {
            if (binding != null
                    && binding.bindingEpoch().equals(expected.bindingEpoch())) {
                binding = null;
                managerTransport = null;
                disconnectGeneration++;
            }
        }
    }

    private void advanceDeliveredRevision(Binding expected, String revision) {
        synchronized (stateLock) {
            if (binding != null
                    && binding.bindingEpoch().equals(expected.bindingEpoch())) {
                binding = binding.withGraphRevision(revision);
            }
        }
    }

    private boolean publishRetired(Binding retired, String reason) {
        JsonObject payload = new JsonObject();
        payload.addProperty("ok", false);
        payload.addProperty("retired", true);
        payload.addProperty("preserveSnapshot", false);
        payload.addProperty("error", reason);
        payload.addProperty("bindingEpoch", retired.bindingEpoch());
        payload.addProperty("workspaceEpoch", retired.workspaceEpoch());
        payload.addProperty("botJobId", retired.botJobId());
        return windows.send(
                retired.homeBankingId(),
                WORKSPACE_SESSION_ID,
                SNAPSHOT_OPERATION,
                payload);
    }

    private void sendRetiredClose() {
        JsonObject payload = new JsonObject();
        payload.addProperty(
                "reason", "The Variables workspace no longer has an active Bot Job.");
        windows.send(
                -1,
                WORKSPACE_SESSION_ID,
                PagesOpenWorkspaceService.WORKSPACE_CLOSE_OPERATION,
                payload);
    }

    private static boolean isSuccessful(JsonObject response) {
        return response != null
                && response.has("ok")
                && !response.get("ok").isJsonNull()
                && response.get("ok").getAsBoolean();
    }

    private static void correlate(JsonObject request, JsonObject response) {
        if (request == null) return;
        for (String field : new String[] {"requestId"}) {
            if (request.has(field)
                    && !request.get(field).isJsonNull()
                    && !response.has(field)) {
                response.add(field, request.get(field).deepCopy());
            }
        }
    }

    private static String text(JsonObject source, String field) {
        try {
            return source != null
                            && source.has(field)
                            && !source.get(field).isJsonNull()
                    ? source.get(field).getAsString()
                    : "";
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static WorkspaceContext activeWorkspace(int botJobId, long workspaceEpoch) {
        BotJobDetailsWorkspaceRegistry.Snapshot snapshot = workspaceEpoch > 0
                ? BotJobDetailsWorkspaceRegistry.getInstance()
                        .require(botJobId, workspaceEpoch)
                : BotJobDetailsWorkspaceRegistry.getInstance().require(botJobId);
        return new WorkspaceContext(
                snapshot.workspaceEpoch(),
                snapshot.botJobId(),
                snapshot.homeBankingId(),
                snapshot.name(),
                snapshot.organizationName());
    }

    interface WorkspacePort {
        WorkspaceContext require(int botJobId, long workspaceEpoch);
    }

    interface GraphPort {
        JsonObject load(int botJobId);
    }

    interface WindowPort {
        boolean isOpen(String sessionId);

        boolean isRegistered(String sessionId, Session transport);

        boolean openOrFocus(String sessionId, int botJobId, String reason);

        boolean close(String sessionId, String reason);

        boolean forceClose(String sessionId, String reason);

        boolean send(int homeBankingId, String sessionId, String operationId, JsonObject body);
    }

    interface TaskPort {
        void executeMutation(Runnable task);

        void scheduleDisconnect(Runnable task, long delayMillis);
    }

    record WorkspaceContext(
            long workspaceEpoch,
            int botJobId,
            int homeBankingId,
            String botJobName,
            String organizationName) {}

    public record RetireResult(
            boolean matched, boolean closed, boolean tombstoneDelivered, boolean forceClosed) {}

    private record Binding(
            String bindingEpoch,
            long workspaceEpoch,
            int botJobId,
            int homeBankingId,
            String botJobName,
            String organizationName,
            String graphRevision) {
        private Binding withBindingEpoch(String value) {
            return new Binding(
                    value,
                    workspaceEpoch,
                    botJobId,
                    homeBankingId,
                    botJobName,
                    organizationName,
                    graphRevision);
        }

        private Binding withGraphRevision(String value) {
            return new Binding(
                    bindingEpoch,
                    workspaceEpoch,
                    botJobId,
                    homeBankingId,
                    botJobName,
                    organizationName,
                    value == null ? "" : value);
        }

        private Binding withWorkspace(WorkspaceContext workspace) {
            return new Binding(
                    bindingEpoch,
                    workspace.workspaceEpoch(),
                    workspace.botJobId(),
                    workspace.homeBankingId(),
                    workspace.botJobName(),
                    workspace.organizationName(),
                    graphRevision);
        }
    }

    private static final class DefaultWindowPort implements WindowPort {
        private final PagesOpenWorkspaceService pages =
                PagesOpenWorkspaceService.getInstance();
        private final WebSocketSessionManager sessions =
                WebSocketSessionManager.getInstance();
        private final Gson gson = new Gson();

        @Override
        public boolean isOpen(String sessionId) {
            return WebSocketSessionManager.isSessionOpen(sessionId);
        }

        @Override
        public boolean isRegistered(String sessionId, Session transport) {
            return sessionId != null
                    && transport != null
                    && transport.isOpen()
                    && WebSocketSessionManager.getSession(sessionId) == transport;
        }

        @Override
        public boolean openOrFocus(String sessionId, int botJobId, String reason) {
            return pages.openOrFocusDetachedWorkspace(sessionId, botJobId, reason);
        }

        @Override
        public boolean close(String sessionId, String reason) {
            return pages.closeDetachedWorkspaceSession(sessionId, reason);
        }

        @Override
        public boolean forceClose(String sessionId, String reason) {
            return !WebSocketSessionManager.isSessionOpen(sessionId)
                    || WebSocketSessionManager.closeSession(sessionId);
        }

        @Override
        public boolean send(
                int homeBankingId, String sessionId, String operationId, JsonObject body) {
            return sessions.sendMessageJson(
                            homeBankingId,
                            sessionId,
                            gson.toJson(body),
                            operationId)
                    != null;
        }
    }

    private static final class DefaultTaskPort implements TaskPort {
        private static final DefaultTaskPort INSTANCE = new DefaultTaskPort();
        private final ExecutorService mutationExecutor =
                Executors.newSingleThreadExecutor(runnable -> daemon(runnable, "variables-mutations"));
        private final ScheduledExecutorService disconnectExecutor =
                Executors.newSingleThreadScheduledExecutor(
                        runnable -> daemon(runnable, "variables-disconnect"));

        @Override
        public void executeMutation(Runnable task) {
            if (task != null) mutationExecutor.execute(task);
        }

        @Override
        public void scheduleDisconnect(Runnable task, long delayMillis) {
            if (task != null) {
                disconnectExecutor.schedule(
                        task, Math.max(0L, delayMillis), TimeUnit.MILLISECONDS);
            }
        }

        private static Thread daemon(Runnable runnable, String name) {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        }
    }
}

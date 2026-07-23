package com.allinweb.ch.socket;

import com.allinweb.ch.model.DetachedWorkspaceSessions;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Set;
import java.util.UUID;
import javax.websocket.Session;

/**
 * Owns the small amount of cross-window state required by the detached Memory List.
 *
 * <p>The Bot Job Details or Page Scanner transport remains authoritative. The service only retains
 * its latest presentation snapshot and relays a bounded set of UI commands back to the exact
 * transport that supplied that snapshot. It never performs an instruction mutation itself.
 */
public final class MemoryListWorkspaceService {

    public static final String WORKSPACE_SESSION_ID = DetachedWorkspaceSessions.MEMORY_LIST_MANAGER;
    public static final String SNAPSHOT_OPERATION = "memoryList.snapshot";
    public static final String FOCUS_OPERATION = "memoryList.focus";
    public static final String SOURCE_COMMAND_OPERATION = "memoryList.command";

    private static final int MAX_SNAPSHOT_CHARACTERS = 1_000_000;
    private static final int MAX_COMMAND_PAYLOAD_CHARACTERS = 128_000;
    private static final int MAX_COLLECTION_ITEMS = 1_000;
    private static final int MAX_REQUEST_ID_CHARACTERS = 160;
    private static final int MAX_COMMAND_NAME_CHARACTERS = 64;
    private static final long LAUNCH_PENDING_NANOS = java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
    private static final Set<String> COMMANDS = Set.of(
            "REMOVE",
            "CLEAR",
            "SELECT_TARGET_BLOCK",
            "APPLY",
            "CREATE_BLOCK");

    private static final MemoryListWorkspaceService INSTANCE = new MemoryListWorkspaceService();

    private final Object stateLock = new Object();
    private final Gson gson = new Gson();
    private MemoryState current;
    private boolean launchPending;
    private long launchPendingSince;

    private MemoryListWorkspaceService() {}

    public static MemoryListWorkspaceService getInstance() {
        return INSTANCE;
    }

    /**
     * Stores the source's current Memory List and opens, or reuses, the one detached native shell.
     */
    public JsonObject open(JsonObject body, String transportSessionId, Session transportSession) {
        JsonObject validation = validateSourceRequest(body, transportSessionId, transportSession);
        if (validation != null) return validation;

        MemoryState next = capture(
                body,
                transportSessionId,
                transportSession,
                UUID.randomUUID().toString());
        boolean alreadyOpen = WebSocketSessionManager.isSessionOpen(WORKSPACE_SESSION_ID);
        boolean launchRequired;
        long now = System.nanoTime();
        synchronized (stateLock) {
            current = next;
            if (alreadyOpen) {
                launchPending = false;
                launchRequired = false;
            } else if (launchPending && now - launchPendingSince < LAUNCH_PENDING_NANOS) {
                launchRequired = false;
            } else {
                launchPending = true;
                launchPendingSince = now;
                launchRequired = true;
            }
        }

        boolean launched = !launchRequired || ARWebSocketServer.getInstance()
                .openDetachedWorkspaceDesktopShell(WORKSPACE_SESSION_ID, next.botJobId());
        if (!launched) {
            synchronized (stateLock) {
                launchPending = false;
            }
            return failure(body, "Memory List workspace could not be opened.");
        }

        if (alreadyOpen) {
            publishSnapshot(next);
            publishFocus(next);
        }
        boolean pendingReuse = !alreadyOpen && !launchRequired;
        JsonObject response = snapshotResponse(next, alreadyOpen
                ? "Memory List workspace already open."
                : pendingReuse
                        ? "Memory List workspace is opening."
                        : "Memory List workspace opened.");
        copyRequestId(response, body);
        response.addProperty("alreadyOpen", alreadyOpen);
        response.addProperty("reused", alreadyOpen || pendingReuse);
        return response;
    }

    /** Replaces the stored snapshot and publishes it to the detached page when connected. */
    public JsonObject sync(JsonObject body, String transportSessionId, Session transportSession) {
        JsonObject validation = validateSourceRequest(body, transportSessionId, transportSession);
        if (validation != null) return validation;

        MemoryState next;
        synchronized (stateLock) {
            MemoryState owner = current;
            if (owner == null) {
                return failure(body, "Open the Memory List before synchronizing it.");
            }
            String ownerEpoch = string(body, "ownerEpoch");
            if (owner.sourceTransport() != transportSession
                    || !owner.sourceSessionId().equals(transportSessionId)
                    || owner.botJobId() != positiveInteger(body, "botJobId")
                    || ownerEpoch.isEmpty()
                    || !owner.ownerEpoch().equals(ownerEpoch)) {
                return failure(
                        body,
                        "Memory List ownership changed. Open this Memory List again to continue.");
            }
            next = capture(body, transportSessionId, transportSession, owner.ownerEpoch());
            current = next;
        }
        publishSnapshot(next);
        JsonObject response = snapshotResponse(next, "Memory List synchronized.");
        copyRequestId(response, body);
        return response;
    }

    /** Returns the latest source snapshot to the one fixed detached Memory List session. */
    public JsonObject bootstrap(JsonObject body, String transportSessionId, Session transportSession) {
        JsonObject validation = validateWorkspaceTransport(
                body, transportSessionId, transportSession);
        if (validation != null) return validation;

        MemoryState snapshot;
        synchronized (stateLock) {
            launchPending = false;
            snapshot = current;
        }
        if (snapshot == null) {
            return failure(body, "No Memory List is available.");
        }
        publishSnapshot(snapshot);
        JsonObject response = snapshotResponse(snapshot, "Memory List loaded.");
        copyRequestId(response, body);
        return response;
    }

    /**
     * Relays a detached-page command only to the exact live source transport that most recently
     * supplied the snapshot.
     */
    public JsonObject command(JsonObject body, String transportSessionId, Session transportSession) {
        JsonObject validation = validateWorkspaceTransport(
                body, transportSessionId, transportSession);
        if (validation != null) return validation;
        if (body == null) return failure(null, "Memory List command body is required.");

        String requestId = string(body, "requestId");
        if (requestId.length() > MAX_REQUEST_ID_CHARACTERS) {
            return failure(body, "A valid Memory List request ID is required.");
        }
        if (requestId.isEmpty()) {
            requestId = "memory-list-" + UUID.randomUUID();
        }
        String command = string(body, "command");
        if (command.isEmpty()) command = string(body, "action");
        command = canonicalCommand(command);
        if (command.isEmpty()
                || command.length() > MAX_COMMAND_NAME_CHARACTERS
                || !COMMANDS.contains(command)) {
            return failure(body, "Unsupported Memory List command.");
        }

        JsonElement payloadElement = body.get("payload");
        JsonObject payload = payloadElement != null && payloadElement.isJsonObject()
                ? payloadElement.getAsJsonObject().deepCopy()
                : commandPayload(body);
        if (payload.toString().length() > MAX_COMMAND_PAYLOAD_CHARACTERS) {
            return failure(body, "Memory List command payload is too large.");
        }

        MemoryState source;
        synchronized (stateLock) {
            source = current;
            if (source == null) {
                return failure(body, "No Memory List source is available.");
            }
            String ownerEpoch = string(body, "ownerEpoch");
            if (ownerEpoch.isEmpty() || !source.ownerEpoch().equals(ownerEpoch)) {
                return failure(
                        body,
                        "Memory List content changed. Refresh the detached Memory List and try again.");
            }
            Session registeredSource = WebSocketSessionManager.getSession(source.sourceSessionId());
            if (registeredSource == null
                    || registeredSource != source.sourceTransport()
                    || !registeredSource.isOpen()) {
                return failure(body, "The Memory List source is no longer connected.");
            }

            JsonObject forwarded = new JsonObject();
            forwarded.addProperty("requestId", requestId);
            forwarded.addProperty("command", command);
            forwarded.addProperty("action", command);
            forwarded.addProperty("botJobId", source.botJobId());
            forwarded.addProperty("homeBankingId", source.homeBankingId());
            forwarded.addProperty("memoryListSessionId", WORKSPACE_SESSION_ID);
            forwarded.addProperty("ownerEpoch", source.ownerEpoch());
            forwarded.add("payload", payload);
            WebSocketSessionManager.sendMessageJson(
                    source.homeBankingId(),
                    registeredSource,
                    source.sourceSessionId(),
                    gson.toJson(forwarded),
                    SOURCE_COMMAND_OPERATION);
        }

        JsonObject response = baseResponse(body, true, "Memory List command delivered.");
        response.addProperty("command", command);
        response.addProperty("requestId", requestId);
        response.addProperty("botJobId", source.botJobId());
        response.addProperty("sourceSessionId", source.sourceSessionId());
        return response;
    }

    private JsonObject validateSourceRequest(
            JsonObject body, String transportSessionId, Session transportSession) {
        boolean botJobDetailsSource = ScannerWorkspaceSessions.BOT_JOB_TASKS.equals(transportSessionId);
        boolean pageScannerSource = ScannerWorkspaceSessions.isOcrSourceScannerSession(transportSessionId);
        if (!botJobDetailsSource && !pageScannerSource) {
            return failure(body, "Only Bot Job Details or Page Scanner can update the Memory List.");
        }
        if (!isRegisteredTransport(transportSessionId, transportSession)) {
            return failure(body, "The Memory List source is not authoritative.");
        }
        if (body == null) return failure(null, "Memory List body is required.");

        int botJobId = positiveInteger(body, "botJobId");
        if (botJobId <= 0) {
            return failure(body, "A positive Bot Job ID is required.");
        }
        JsonObject snapshot = snapshot(body);
        if (snapshot == null) {
            return failure(body, "A Memory List snapshot is required.");
        }
        if (snapshot.toString().length() > MAX_SNAPSHOT_CHARACTERS) {
            return failure(body, "Memory List snapshot is too large.");
        }
        for (String collection : Set.of(
                "steps", "memorySteps", "items", "blocks", "blockOptions", "memoryBlockOptions")) {
            JsonElement value = snapshot.get(collection);
            if (value != null
                    && value.isJsonArray()
                    && value.getAsJsonArray().size() > MAX_COLLECTION_ITEMS) {
                return failure(body, "Memory List collection is too large: " + collection);
            }
        }
        return null;
    }

    private JsonObject validateWorkspaceTransport(
            JsonObject body, String transportSessionId, Session transportSession) {
        if (!WORKSPACE_SESSION_ID.equals(transportSessionId)) {
            return failure(body, "Only the detached Memory List can use this operation.");
        }
        if (!isRegisteredTransport(transportSessionId, transportSession)) {
            return failure(body, "The detached Memory List session is not authoritative.");
        }
        return null;
    }

    private boolean isRegisteredTransport(String sessionId, Session transportSession) {
        return transportSession != null
                && transportSession.isOpen()
                && WebSocketSessionManager.getSession(sessionId) == transportSession;
    }

    private MemoryState capture(
            JsonObject body,
            String transportSessionId,
            Session transportSession,
            String ownerEpoch) {
        JsonObject snapshot = snapshot(body);
        int homeBankingId = positiveInteger(body, "homeBankingId");
        if (homeBankingId <= 0) {
            homeBankingId = positiveInteger(snapshot, "homeBankingId");
        }
        JsonObject canonicalSnapshot = snapshot.deepCopy();
        canonicalSnapshot.addProperty("ownerEpoch", ownerEpoch);
        return new MemoryState(
                positiveInteger(body, "botJobId"),
                homeBankingId,
                transportSessionId,
                transportSession,
                ownerEpoch,
                canonicalSnapshot);
    }

    private JsonObject snapshot(JsonObject body) {
        if (body == null) return null;
        JsonElement nested = body.get("snapshot");
        if (nested != null) {
            return nested.isJsonObject() ? nested.getAsJsonObject() : null;
        }
        JsonObject copy = body.deepCopy();
        copy.remove("requestId");
        return copy;
    }

    private void publishSnapshot(MemoryState state) {
        synchronized (stateLock) {
            if (current == null || !current.ownerEpoch().equals(state.ownerEpoch())) return;
            if (!WebSocketSessionManager.isSessionOpen(WORKSPACE_SESSION_ID)) return;
            WebSocketSessionManager.getInstance().sendMessageJson(
                    state.homeBankingId(),
                    WORKSPACE_SESSION_ID,
                    gson.toJson(snapshotResponse(state, "Memory List synchronized.")),
                    SNAPSHOT_OPERATION);
        }
    }

    private void publishFocus(MemoryState state) {
        synchronized (stateLock) {
            if (current == null || !current.ownerEpoch().equals(state.ownerEpoch())) return;
            if (!WebSocketSessionManager.isSessionOpen(WORKSPACE_SESSION_ID)) return;
            JsonObject focus = new JsonObject();
            focus.addProperty("sessionId", WORKSPACE_SESSION_ID);
            focus.addProperty("botJobId", state.botJobId());
            WebSocketSessionManager.getInstance().sendMessageJson(
                    state.homeBankingId(),
                    WORKSPACE_SESSION_ID,
                    gson.toJson(focus),
                    FOCUS_OPERATION);
        }
    }

    private JsonObject snapshotResponse(MemoryState state, String message) {
        JsonObject response = new JsonObject();
        response.addProperty("ok", true);
        response.addProperty("message", message);
        response.addProperty("sessionId", WORKSPACE_SESSION_ID);
        response.addProperty("sourceSessionId", state.sourceSessionId());
        response.addProperty("botJobId", state.botJobId());
        response.addProperty("homeBankingId", state.homeBankingId());
        response.addProperty("ownerEpoch", state.ownerEpoch());
        for (var field : state.snapshot().entrySet()) {
            if (!response.has(field.getKey())) {
                response.add(field.getKey(), field.getValue().deepCopy());
            }
        }
        response.add("snapshot", state.snapshot().deepCopy());
        return response;
    }

    private void copyRequestId(JsonObject response, JsonObject request) {
        String requestId = string(request, "requestId");
        if (!requestId.isEmpty() && requestId.length() <= MAX_REQUEST_ID_CHARACTERS) {
            response.addProperty("requestId", requestId);
        }
    }

    private String canonicalCommand(String command) {
        if (command == null) return "";
        String normalized = command.trim().toUpperCase(java.util.Locale.ROOT);
        if ("SELECT_BLOCK".equals(normalized)) return "SELECT_TARGET_BLOCK";
        return normalized;
    }

    private JsonObject commandPayload(JsonObject body) {
        JsonObject payload = body.deepCopy();
        payload.remove("requestId");
        payload.remove("command");
        payload.remove("action");
        payload.remove("ownerEpoch");
        payload.remove("payload");
        return payload;
    }

    private JsonObject failure(JsonObject request, String message) {
        return baseResponse(request, false, message);
    }

    private JsonObject baseResponse(JsonObject request, boolean ok, String message) {
        JsonObject response = new JsonObject();
        response.addProperty("ok", ok);
        response.addProperty("message", message);
        response.addProperty("sessionId", WORKSPACE_SESSION_ID);
        String requestId = string(request, "requestId");
        if (!requestId.isEmpty() && requestId.length() <= MAX_REQUEST_ID_CHARACTERS) {
            response.addProperty("requestId", requestId);
        }
        String ownerEpoch = string(request, "ownerEpoch");
        if (!ownerEpoch.isEmpty() && ownerEpoch.length() <= MAX_REQUEST_ID_CHARACTERS) {
            response.addProperty("ownerEpoch", ownerEpoch);
        }
        return response;
    }

    private static String string(JsonObject source, String name) {
        if (source == null || !source.has(name) || source.get(name).isJsonNull()) return "";
        try {
            return source.get(name).getAsString();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static int positiveInteger(JsonObject source, String name) {
        if (source == null || !source.has(name) || source.get(name).isJsonNull()) return -1;
        try {
            int value = source.get(name).getAsInt();
            return value > 0 ? value : -1;
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    private record MemoryState(
            int botJobId,
            int homeBankingId,
            String sourceSessionId,
            Session sourceTransport,
            String ownerEpoch,
            JsonObject snapshot) {}
}

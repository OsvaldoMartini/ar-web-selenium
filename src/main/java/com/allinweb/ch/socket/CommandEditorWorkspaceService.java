package com.allinweb.ch.socket;

import com.allinweb.ch.facade.BotJobDetailsWorkspaceRegistry;
import com.allinweb.ch.facade.CommandEditorService;
import com.allinweb.ch.model.DetachedWorkspaceSessions;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import javax.websocket.Session;
import lombok.extern.slf4j.Slf4j;

/**
 * Owns the single detached Command Editor window and its authoritative instruction binding.
 *
 * <p>The browser page is deliberately reusable. Selecting another instruction retargets that same
 * physical window and advances {@code bindingEpoch}; late requests from its previous view are then
 * rejected before they can reach the existing command/variable services.
 */
@Slf4j
public final class CommandEditorWorkspaceService {

    public static final String WORKSPACE_SESSION_ID =
            DetachedWorkspaceSessions.COMMAND_EDITOR_MANAGER;
    public static final String TARGET_OPERATION = "commandEditor.workspaceTarget";
    private static final int MAX_MUTATION_REQUEST_ID_CHARACTERS = 160;

    private static final CommandEditorWorkspaceService INSTANCE =
            new CommandEditorWorkspaceService();

    private final BotJobDetailsWorkspaceRegistry workspaceRegistry =
            BotJobDetailsWorkspaceRegistry.getInstance();
    private final CommandEditorService commandEditorService =
            CommandEditorService.getInstance();
    private final PagesOpenWorkspaceService pagesOpenWorkspaceService =
            PagesOpenWorkspaceService.getInstance();
    private final WebSocketSessionManager sessions =
            WebSocketSessionManager.getInstance();
    private final Gson gson = new Gson();

    private Binding binding;
    private final Set<String> completedMutationRequests = new LinkedHashSet<>();

    private CommandEditorWorkspaceService() {}

    public static CommandEditorWorkspaceService getInstance() {
        return INSTANCE;
    }

    /**
     * Opens the one Command Editor window or retargets and focuses its existing instance.
     */
    public synchronized JsonObject open(
            JsonObject body, String requesterSessionId, Session requesterTransport) {
        JsonObject request = body == null ? new JsonObject() : body;
        try {
            String targetSessionId =
                    string(request, "targetSessionId", requesterSessionId);
            requireSupportedSource(
                    targetSessionId, requesterSessionId, requesterTransport);

            int botJobId = integer(request, "botJobId", -1);
            int instructionId = integer(request, "instructionId", -1);
            BotJobDetailsWorkspaceRegistry.Snapshot workspace =
                    workspaceRegistry.require(botJobId);
            long requestedWorkspaceEpoch = longValue(request, "workspaceEpoch", 0L);
            if (requestedWorkspaceEpoch > 0
                    && requestedWorkspaceEpoch != workspace.workspaceEpoch()) {
                throw new IllegalArgumentException(
                        "Bot Job Details changed. Reopen the Command Editor.");
            }
            int requestedHomeBankingId =
                    integer(request, "homeBankingId", workspace.homeBankingId());
            if (requestedHomeBankingId > 0
                    && requestedHomeBankingId != workspace.homeBankingId()) {
                throw new IllegalArgumentException(
                        "The Command Editor organization does not match Bot Job Details.");
            }

            InstructionLoad instruction = commandEditorService.resolveInstruction(
                    targetSessionId,
                    workspace.botJobId(),
                    workspace.homeBankingId(),
                    instructionId);

            Binding previous = binding;
            boolean sameTarget = previous != null
                    && previous.sourceTransport() == requesterTransport
                    && previous.sourceSessionId().equals(targetSessionId)
                    && previous.botJobId() == workspace.botJobId()
                    && previous.botJobWorkspaceEpoch() == workspace.workspaceEpoch()
                    && previous.instructionId() == instructionId;
            // Every explicit open gets a new epoch, including reopening the same instruction.
            // This makes late replies from the previous panel view harmless and forces the
            // detached page to reload authoritative row/graph metadata.
            String bindingEpoch = UUID.randomUUID().toString();
            Binding next = new Binding(
                    bindingEpoch,
                    targetSessionId,
                    requesterTransport,
                    workspace.workspaceEpoch(),
                    workspace.botJobId(),
                    workspace.homeBankingId(),
                    workspace.name(),
                    instructionId,
                    instruction);
            binding = next;

            boolean alreadyOpen =
                    WebSocketSessionManager.isSessionOpen(WORKSPACE_SESSION_ID);
            boolean opened = pagesOpenWorkspaceService.openOrFocusDetachedWorkspace(
                    WORKSPACE_SESSION_ID,
                    workspace.botJobId(),
                    "An instruction requested the Command Editor.");
            if (!opened) {
                binding = previous;
                return failure(request, "The Command Editor workspace could not be opened.");
            }

            if (alreadyOpen) {
                publishTarget(
                        next,
                        sameTarget
                                ? "Command Editor target refreshed."
                                : "Command Editor target changed.");
            }

            JsonObject response = targetPayload(
                    next,
                    alreadyOpen
                            ? sameTarget
                                    ? "Command Editor brought to front."
                                    : "Command Editor retargeted and brought to front."
                            : "Command Editor opened.");
            response.addProperty("alreadyOpen", alreadyOpen);
            response.addProperty("retargeted", alreadyOpen && !sameTarget);
            correlate(request, response);
            return response;
        } catch (IllegalArgumentException error) {
            return failure(request, error.getMessage());
        } catch (RuntimeException error) {
            log.error("Unable to open the detached Command Editor", error);
            return failure(request, "The Command Editor workspace could not be opened.");
        }
    }

    /**
     * Bootstraps the detached page from the exact registered Command Editor transport.
     */
    public synchronized JsonObject bootstrap(
            JsonObject body, String requesterSessionId, Session requesterTransport) {
        JsonObject request = body == null ? new JsonObject() : body;
        try {
            requireManagerTransport(requesterSessionId, requesterTransport);
            if (binding == null) {
                throw new IllegalArgumentException(
                        "No instruction is selected. Open Command Editor from Bot Job Details.");
            }
            String requestedEpoch = string(request, "bindingEpoch", "");
            if (!requestedEpoch.isBlank()
                    && !requestedEpoch.equals(binding.bindingEpoch())) {
                throw new IllegalArgumentException(
                        "The Command Editor target changed. Reload the current selection.");
            }

            Binding current = refreshBinding(binding);
            binding = current;
            JsonObject canonical = canonicalIdentity(request, current);
            JsonObject editorBootstrap = commandEditorService.bootstrap(canonical);
            JsonObject response = targetPayload(current, "Command Editor loaded.");
            merge(response, editorBootstrap);
            correlate(request, response);
            return response;
        } catch (IllegalArgumentException error) {
            return failureForCurrentBinding(request, error.getMessage());
        } catch (RuntimeException error) {
            log.error("Unable to bootstrap the detached Command Editor", error);
            return failureForCurrentBinding(
                    request, "The Command Editor could not load its instruction.");
        }
    }

    /**
     * Authorizes one existing command/variable operation and replaces all ownership and anchor
     * metadata with the backend-owned binding.
     */
    public synchronized AuthorizedRequest authorize(
            JsonObject body, String requesterSessionId, Session requesterTransport) {
        JsonObject request = body == null ? new JsonObject() : body;
        requireManagerTransport(requesterSessionId, requesterTransport);
        if (binding == null) {
            throw new IllegalArgumentException(
                    "The Command Editor has no active instruction target.");
        }
        String requestedEpoch = string(request, "bindingEpoch", "");
        if (requestedEpoch.isBlank()) {
            throw new IllegalArgumentException(
                    "Command Editor binding is required. Reload the current selection.");
        }
        if (!requestedEpoch.equals(binding.bindingEpoch())) {
            throw new IllegalArgumentException(
                    "The Command Editor target changed. Reload the current selection.");
        }

        Binding current = refreshBinding(binding);
        binding = current;
        return new AuthorizedRequest(
                canonicalIdentity(request, current),
                current.sourceSessionId(),
                current.homeBankingId(),
                current.bindingEpoch());
    }

    /**
     * Keeps authorization, binding stability, active-workspace validation, idempotency scoping,
     * and the delegated database mutation inside one lifecycle boundary.
     */
    public synchronized AuthorizedMutation executeMutation(
            JsonObject body,
            String requesterSessionId,
            Session requesterTransport,
            Function<JsonObject, JsonObject> mutation) {
        if (mutation == null) {
            throw new IllegalArgumentException(
                    "A Command Editor mutation operation is required.");
        }
        JsonObject original = body == null ? new JsonObject() : body;
        AuthorizedRequest authorized =
                authorize(original, requesterSessionId, requesterTransport);
        Binding current = binding;
        JsonObject scopedBody = authorized.body().deepCopy();
        String clientRequestId = requireMutationRequestId(original);
        String scopedRequestId = current.bindingEpoch() + ":" + clientRequestId;
        scopedBody.addProperty("requestId", scopedRequestId);
        boolean replayed = completedMutationRequests.contains(scopedRequestId);

        JsonObject response = workspaceRegistry.commitWorkspaceMutation(
                current.botJobId(),
                current.botJobWorkspaceEpoch(),
                () -> mutation.apply(scopedBody));
        if (response == null) response = new JsonObject();
        response.addProperty("requestId", clientRequestId);
        response.addProperty("bindingEpoch", authorized.bindingEpoch());
        if (!replayed
                && response.has("ok")
                && response.get("ok").getAsBoolean()) {
            completedMutationRequests.add(scopedRequestId);
            while (completedMutationRequests.size() > 256) {
                completedMutationRequests.remove(
                        completedMutationRequests.iterator().next());
            }
        }
        return new AuthorizedMutation(
                new AuthorizedRequest(
                        authorized.body(),
                        authorized.targetSessionId(),
                        authorized.homeBankingId(),
                        authorized.bindingEpoch()),
                response,
                replayed);
    }

    /** Clears and closes the Command Editor when its owning Bot Job workspace is retired. */
    public synchronized boolean retireForBotJob(int botJobId, String reason) {
        if (binding == null || binding.botJobId() != botJobId) return false;
        binding = null;
        return pagesOpenWorkspaceService.closeDetachedWorkspaceSession(
                WORKSPACE_SESSION_ID, safeReason(reason));
    }

    /** Retires bindings whose exact source or manager WebSocket has disconnected. */
    public synchronized void disconnected(String sessionId, Session transport) {
        if (binding == null || sessionId == null || transport == null) return;
        if (WORKSPACE_SESSION_ID.equals(sessionId)) {
            binding = null;
            return;
        }
        if (binding.sourceSessionId().equals(sessionId)
                && binding.sourceTransport() == transport) {
            int botJobId = binding.botJobId();
            binding = null;
            pagesOpenWorkspaceService.closeDetachedWorkspaceSession(
                    WORKSPACE_SESSION_ID,
                    "Bot Job Details closed; Command Editor was retired.");
            log.debug(
                    "Retired Command Editor after source transport closed for Bot Job {}",
                    botJobId);
        }
    }

    public static boolean isWorkspaceSession(String sessionId) {
        return WORKSPACE_SESSION_ID.equals(sessionId);
    }

    /**
     * Closes a late detached shell that connected after its pending binding had already retired.
     */
    public synchronized void connected(String sessionId, Session transport) {
        if (!WORKSPACE_SESSION_ID.equals(sessionId)
                || !isRegisteredTransport(sessionId, transport)) {
            return;
        }
        if (binding != null) {
            // A reloaded singleton transport must not accept a late response emitted for the
            // superseded page instance. Its initial workspace bootstrap does not require an epoch
            // and will receive this new authoritative value.
            binding = binding.withBindingEpoch(UUID.randomUUID().toString());
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("reason", "The Command Editor target is no longer active.");
        sessions.sendMessageJson(
                -1,
                WORKSPACE_SESSION_ID,
                gson.toJson(payload),
                PagesOpenWorkspaceService.WORKSPACE_CLOSE_OPERATION);
    }

    private Binding refreshBinding(Binding current) {
        if (!isRegisteredTransport(
                current.sourceSessionId(), current.sourceTransport())) {
            throw new IllegalArgumentException(
                    "Bot Job Details is no longer connected. Reopen the Command Editor.");
        }
        BotJobDetailsWorkspaceRegistry.Snapshot workspace = workspaceRegistry.require(
                current.botJobId(), current.botJobWorkspaceEpoch());
        if (workspace.homeBankingId() != current.homeBankingId()) {
            throw new IllegalArgumentException(
                    "The active Bot Job organization changed. Reopen the Command Editor.");
        }
        InstructionLoad instruction = commandEditorService.resolveInstruction(
                current.sourceSessionId(),
                current.botJobId(),
                current.homeBankingId(),
                current.instructionId());
        return current.withInstruction(workspace.name(), instruction);
    }

    private void requireSupportedSource(
            String targetSessionId, String requesterSessionId, Session requesterTransport) {
        if (!ScannerWorkspaceSessions.BOT_JOB_TASKS.equals(targetSessionId)) {
            throw new IllegalArgumentException(
                    "Command Editor can only be opened from Bot Job Details.");
        }
        if (!targetSessionId.equals(requesterSessionId)
                || !isRegisteredTransport(requesterSessionId, requesterTransport)) {
            throw new IllegalArgumentException(
                    "The Command Editor requester is not authoritative.");
        }
    }

    private void requireManagerTransport(
            String requesterSessionId, Session requesterTransport) {
        if (!WORKSPACE_SESSION_ID.equals(requesterSessionId)) {
            throw new IllegalArgumentException(
                    "Only the detached Command Editor can use this operation.");
        }
        if (!isRegisteredTransport(requesterSessionId, requesterTransport)) {
            throw new IllegalArgumentException(
                    "The Command Editor requester is not authoritative.");
        }
    }

    private boolean isRegisteredTransport(String sessionId, Session transport) {
        return sessionId != null
                && transport != null
                && transport.isOpen()
                && WebSocketSessionManager.getSession(sessionId) == transport;
    }

    private JsonObject canonicalIdentity(JsonObject request, Binding current) {
        JsonObject canonical =
                request == null ? new JsonObject() : request.deepCopy();
        InstructionLoad instruction = current.instruction();
        canonical.addProperty("bindingEpoch", current.bindingEpoch());
        canonical.addProperty("targetSessionId", current.sourceSessionId());
        canonical.addProperty("workspaceEpoch", current.botJobWorkspaceEpoch());
        canonical.addProperty("homeBankingId", current.homeBankingId());
        canonical.addProperty("botJobId", current.botJobId());
        canonical.addProperty("botJobName", current.botJobName());
        canonical.addProperty("instructionId", current.instructionId());
        canonical.addProperty("instructionName", safe(instruction.getName()));
        canonical.addProperty("instructionActions", safe(instruction.getActions()));
        canonical.addProperty("blockId", value(instruction.getBlockId(), -1));
        canonical.addProperty("blockName", safe(instruction.getBlockName()));
        canonical.addProperty(
                "blockOrderNumber", value(instruction.getBlockOrderNumber(), 1));
        canonical.addProperty(
                "instructionOrderNumber",
                value(instruction.getInstructionOrderNumber(), 1));
        return canonical;
    }

    private JsonObject targetPayload(Binding current, String message) {
        JsonObject response = new JsonObject();
        response.addProperty("ok", true);
        response.addProperty("message", message);
        response.addProperty("bindingEpoch", current.bindingEpoch());
        response.addProperty("targetSessionId", current.sourceSessionId());
        response.addProperty("workspaceEpoch", current.botJobWorkspaceEpoch());
        response.addProperty("homeBankingId", current.homeBankingId());
        response.addProperty("botJobId", current.botJobId());
        response.addProperty("botJobName", current.botJobName());
        response.add("instruction", gson.toJsonTree(current.instruction()));
        return response;
    }

    private void publishTarget(Binding current, String message) {
        if (!WebSocketSessionManager.isSessionOpen(WORKSPACE_SESSION_ID)) return;
        JsonObject payload = targetPayload(current, message);
        sessions.sendMessageJson(
                current.homeBankingId(),
                WORKSPACE_SESSION_ID,
                gson.toJson(payload),
                TARGET_OPERATION);
    }

    private static void merge(JsonObject target, JsonObject source) {
        if (source == null) return;
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            target.add(entry.getKey(), entry.getValue().deepCopy());
        }
    }

    private static void correlate(JsonObject request, JsonObject response) {
        if (request != null
                && request.has("requestId")
                && !request.get("requestId").isJsonNull()
                && !response.has("requestId")) {
            response.add("requestId", request.get("requestId").deepCopy());
        }
        if (request != null
                && request.has("bindingEpoch")
                && !request.get("bindingEpoch").isJsonNull()
                && !response.has("bindingEpoch")) {
            response.add(
                    "bindingEpoch",
                    request.get("bindingEpoch").deepCopy());
        }
    }

    private static JsonObject failure(JsonObject request, String message) {
        JsonObject response = new JsonObject();
        response.addProperty("ok", false);
        response.addProperty(
                "error",
                message == null || message.isBlank()
                        ? "The Command Editor request was refused."
                        : message);
        correlate(request, response);
        return response;
    }

    private JsonObject failureForCurrentBinding(JsonObject request, String message) {
        JsonObject response = failure(request, message);
        if (binding != null && !response.has("bindingEpoch")) {
            response.addProperty("bindingEpoch", binding.bindingEpoch());
        }
        return response;
    }

    private static String string(JsonObject body, String key, String fallback) {
        try {
            return body != null
                            && body.has(key)
                            && !body.get(key).isJsonNull()
                    ? body.get(key).getAsString()
                    : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static int integer(JsonObject body, String key, int fallback) {
        try {
            return body != null
                            && body.has(key)
                            && !body.get(key).isJsonNull()
                    ? body.get(key).getAsInt()
                    : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static long longValue(JsonObject body, String key, long fallback) {
        try {
            return body != null
                            && body.has(key)
                            && !body.get(key).isJsonNull()
                    ? body.get(key).getAsLong()
                    : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String requireMutationRequestId(JsonObject body) {
        String requestId = string(body, "requestId", "").trim();
        if (requestId.isEmpty()) {
            throw new IllegalArgumentException(
                    "Command Editor mutation request ID is required.");
        }
        if (requestId.length() > MAX_MUTATION_REQUEST_ID_CHARACTERS
                || !requestId.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalArgumentException(
                    "Command Editor mutation request ID is invalid.");
        }
        return requestId;
    }

    private static int value(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String safeReason(String reason) {
        return reason == null || reason.isBlank()
                ? "The Command Editor workspace was retired."
                : reason;
    }

    public record AuthorizedRequest(
            JsonObject body,
            String targetSessionId,
            int homeBankingId,
            String bindingEpoch) {}

    public record AuthorizedMutation(
            AuthorizedRequest request, JsonObject response, boolean replayed) {}

    private record Binding(
            String bindingEpoch,
            String sourceSessionId,
            Session sourceTransport,
            long botJobWorkspaceEpoch,
            int botJobId,
            int homeBankingId,
            String botJobName,
            int instructionId,
            InstructionLoad instruction) {

        private Binding withInstruction(
                String refreshedBotJobName, InstructionLoad refreshedInstruction) {
            return new Binding(
                    bindingEpoch,
                    sourceSessionId,
                    sourceTransport,
                    botJobWorkspaceEpoch,
                    botJobId,
                    homeBankingId,
                    refreshedBotJobName,
                    instructionId,
                    refreshedInstruction);
        }

        private Binding withBindingEpoch(String refreshedBindingEpoch) {
            return new Binding(
                    refreshedBindingEpoch,
                    sourceSessionId,
                    sourceTransport,
                    botJobWorkspaceEpoch,
                    botJobId,
                    homeBankingId,
                    botJobName,
                    instructionId,
                    instruction);
        }
    }
}

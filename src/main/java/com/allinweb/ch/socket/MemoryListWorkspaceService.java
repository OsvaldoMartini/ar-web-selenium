package com.allinweb.ch.socket;

import com.allinweb.ch.facade.BlockCreationService;
import com.allinweb.ch.facade.BotJobDetailsWorkspaceRegistry;
import com.allinweb.ch.facade.ComponentMemoryApplyService;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.facade.PageMappingInstructionReference;
import com.allinweb.ch.facade.PreScanApplyService;
import com.allinweb.ch.facade.ScannerBotJobTasksPublisher;
import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.DetachedWorkspaceSessions;
import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.allinweb.ch.model.SplitDTO;
import com.allinweb.ch.util.ErrorMessage;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import javax.websocket.Session;

/**
 * Backend-owned state for the one detached Memory List.
 *
 * <p>Bot Job Details, Components, and Page Scanner contribute independently to one ordered list. A
 * contribution updates only its own rows; it can no longer replace rows supplied by another page.
 * Commands that mutate the mixed list are performed here so the list remains usable even when one
 * source window is temporarily behind or has been closed.
 */
public final class MemoryListWorkspaceService {

    public static final String WORKSPACE_SESSION_ID = DetachedWorkspaceSessions.MEMORY_LIST_MANAGER;
    public static final String SNAPSHOT_OPERATION = "memoryList.snapshot";
    public static final String FOCUS_OPERATION = "memoryList.focus";
    public static final String SOURCE_COMMAND_OPERATION = "memoryList.command";
    public static final String SUMMARY_CHANGED_OPERATION = "memoryList.summaryChanged";

    private static final String BOT_JOB_SOURCE = "BOT_JOB";
    private static final String PAGE_SCANNER_SOURCE = "PAGE_SCANNER";
    private static final String PAGE_MAPPINGS_SOURCE = "PAGE_MAPPINGS";
    private static final String COMPONENT_SOURCE = "COMPONENT";
    private static final String MIXED_SOURCE = "MIXED";
    private static final int MAX_SNAPSHOT_CHARACTERS = 1_000_000;
    private static final int MAX_COMMAND_PAYLOAD_CHARACTERS = 128_000;
    private static final int MAX_COLLECTION_ITEMS = 1_000;
    private static final int MAX_REQUEST_ID_CHARACTERS = 160;
    private static final int MAX_COMMAND_NAME_CHARACTERS = 64;
    private static final int PENDING_NEW_BLOCK_PLACEHOLDER_ID = 1;
    private static final long LAUNCH_PENDING_NANOS =
            java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
    private static final SecureRandom WINDOW_CAPABILITY_RANDOM = new SecureRandom();
    private static final int WINDOW_CAPABILITY_BYTES = 32;
    private static final Set<String> COMMANDS = Set.of(
            "REMOVE",
            "CLEAR",
            "SELECT_TARGET_BLOCK",
            "APPLY",
            "CREATE_BLOCK",
            "CREATE_BLOCK_AND_APPLY",
            "REORDER");
    private static final Set<String> PAGE_MAPPING_REFERENCE_FIELDS = Set.of(
            "captureId",
            "pageKey",
            "scannedElementId",
            "elementHash",
            "expectedLastScannedAt",
            "expectedScanCount");

    private static final MemoryListWorkspaceService INSTANCE = new MemoryListWorkspaceService();

    private final Object stateLock = new Object();
    private final Gson gson = new Gson();
    private final PerformDataBase performDataBase = PerformDataBase.getInstance();
    private final PerformLists performLists = PerformLists.getInstance();
    private final PreScanApplyService preScanApplyService = PreScanApplyService.getInstance();
    private final ComponentMemoryApplyService componentMemoryApplyService =
            ComponentMemoryApplyService.getInstance();
    private final BlockCreationService blockCreationService = BlockCreationService.getInstance();
    private final ScannerBotJobTasksPublisher botJobTasksPublisher =
            ScannerBotJobTasksPublisher.getInstance();
    private final DetachedWindowLauncher detachedWindowLauncher;
    private final PageScannerWorkspaceCoordinator pageScannerWorkspaceCoordinator;
    private final Map<String, SummarySubscriber> summarySubscribers = new LinkedHashMap<>();
    private MemoryState current;
    private boolean launchPending;
    private long launchPendingSince;

    private MemoryListWorkspaceService() {
        this(
                (botJobId, windowCapability) -> ARWebSocketServer.getInstance()
                        .openDetachedWorkspaceDesktopShell(
                                WORKSPACE_SESSION_ID, botJobId, windowCapability),
                PageScannerWorkspaceCoordinator.getInstance());
    }

    MemoryListWorkspaceService(
            DetachedWindowLauncher detachedWindowLauncher,
            PageScannerWorkspaceCoordinator pageScannerWorkspaceCoordinator) {
        this.detachedWindowLauncher = Objects.requireNonNull(
                detachedWindowLauncher, "Memory List window launcher is required");
        this.pageScannerWorkspaceCoordinator = Objects.requireNonNull(
                pageScannerWorkspaceCoordinator,
                "Page Scanner workspace coordinator is required");
    }

    public static MemoryListWorkspaceService getInstance() {
        return INSTANCE;
    }

    /** Adds or updates one source contribution and opens/focuses the fixed detached window. */
    public JsonObject open(JsonObject body, String transportSessionId, Session transportSession) {
        if (PAGE_MAPPINGS_SOURCE.equals(sourceKind(transportSessionId))) {
            return withAuthorizedPageMappingsSource(
                    body,
                    transportSessionId,
                    transportSession,
                    sourceOwner -> openAuthorized(
                            body, transportSessionId, transportSession, sourceOwner));
        }
        if (ScannerWorkspaceSessions.isPageScannerSession(transportSessionId)) {
            return withAuthorizedDetachedPageScannerSource(
                    body,
                    transportSessionId,
                    transportSession,
                    sourceOwner -> openAuthorized(
                            body, transportSessionId, transportSession, sourceOwner));
        }
        return openAuthorized(body, transportSessionId, transportSession, null);
    }

    private JsonObject openAuthorized(
            JsonObject body,
            String transportSessionId,
            Session transportSession,
            SourceOwner authorizedSourceOwner) {
        JsonObject validation = validateSourceRequest(
                body, transportSessionId, transportSession);
        if (validation != null) return validation;

        MemoryState state;
        boolean alreadyOpen;
        boolean launchRequired;
        String launchCapability;
        try {
            synchronized (stateLock) {
                long now = System.nanoTime();
                // Connection/takeover uses this same lock. Reading the live-session state here
                // prevents a just-connected pending window from being mistaken for a closed one,
                // having its capability rotated, and being replaced by a duplicate launch.
                alreadyOpen = WebSocketSessionManager.isSessionOpen(WORKSPACE_SESSION_ID);
                SourceOwner sourceOwner = resolveSourceOwner(
                        authorizedSourceOwner, body, transportSessionId, transportSession);
                requireUsableSourceOwner(sourceOwner);
                int botJobId = sourceOwner.botJobId;
                int homeBankingId = sourceOwner.homeBankingId;
                MemoryState previousOwner = current;
                boolean validPending = launchPending
                        && now - launchPendingSince < LAUNCH_PENDING_NANOS;
                if (current == null
                        || current.botJobId != botJobId
                        || (homeBankingId > 0 && current.homeBankingId != homeBankingId)
                        || (sourceOwner.workspaceEpoch > 0
                                && current.workspaceEpoch != sourceOwner.workspaceEpoch)) {
                    current =
                            new MemoryState(
                                    botJobId,
                                    homeBankingId,
                                    sourceOwner.workspaceEpoch,
                                    UUID.randomUUID().toString());
                    current.windowCapability = windowCapabilityForOwnerTransition(
                            previousOwner == null ? null : previousOwner.windowCapability,
                            alreadyOpen,
                            validPending);
                } else if (homeBankingId > 0) {
                    current.homeBankingId = homeBankingId;
                }
                upsertSource(
                        current,
                        body,
                        transportSessionId,
                        transportSession,
                        sourceOwner);
                if (COMPONENT_SOURCE.equals(sourceKind(transportSessionId))
                        || PAGE_MAPPINGS_SOURCE.equals(sourceKind(transportSessionId))) {
                    reloadBlocks(current);
                }
                state = current;
                if (alreadyOpen) {
                    launchPending = false;
                    launchRequired = false;
                } else if (validPending) {
                    launchRequired = false;
                } else {
                    launchPending = true;
                    launchPendingSince = now;
                    current.windowCapability = newWindowCapability();
                    launchRequired = true;
                }
                launchCapability = current.windowCapability;
            }
        } catch (IllegalArgumentException inactiveWorkspace) {
            return failure(
                    body,
                    firstNonBlank(
                            inactiveWorkspace.getMessage(),
                            "The Memory List source no longer matches its active workspace."));
        }

        boolean launched = !launchRequired
                || detachedWindowLauncher.open(state.botJobId, launchCapability);
        if (!launched) {
            synchronized (stateLock) {
                if (!matchesOwnerGeneration(state, launchCapability)) {
                    return failure(
                            body,
                            "Memory List ownership changed while the workspace was opening.");
                }
                launchPending = false;
            }
            return failure(body, "Memory List workspace could not be opened.");
        }

        synchronized (stateLock) {
            if (!matchesOwnerGeneration(state, launchCapability)) {
                return failure(
                        body,
                        "Memory List ownership changed while the workspace was opening.");
            }
        }

        if (alreadyOpen) {
            publishAndFocusCurrent(state, launchCapability);
        }
        boolean pendingReuse = !alreadyOpen && !launchRequired;
        JsonObject response;
        synchronized (stateLock) {
            if (!matchesOwnerGeneration(state, launchCapability)) {
                return failure(
                        body,
                        "Memory List ownership changed while the workspace was opening.");
            }
            response = snapshotResponse(
                    state,
                    alreadyOpen
                            ? "Memory List workspace already open."
                            : pendingReuse
                                    ? "Memory List workspace is opening."
                                    : "Memory List workspace opened.");
        }
        copyRequestId(response, body);
        response.addProperty("alreadyOpen", alreadyOpen);
        response.addProperty("reused", alreadyOpen || pendingReuse);
        publishSummaryChanged(state);
        return response;
    }

    /** Upserts only the calling source; rows from the other source remain in the global list. */
    public JsonObject sync(JsonObject body, String transportSessionId, Session transportSession) {
        if (PAGE_MAPPINGS_SOURCE.equals(sourceKind(transportSessionId))) {
            return withAuthorizedPageMappingsSource(
                    body,
                    transportSessionId,
                    transportSession,
                    sourceOwner -> syncAuthorized(
                            body, transportSessionId, transportSession, sourceOwner));
        }
        if (ScannerWorkspaceSessions.isPageScannerSession(transportSessionId)) {
            return withAuthorizedDetachedPageScannerSource(
                    body,
                    transportSessionId,
                    transportSession,
                    sourceOwner -> syncAuthorized(
                            body, transportSessionId, transportSession, sourceOwner));
        }
        return syncAuthorized(body, transportSessionId, transportSession, null);
    }

    private JsonObject syncAuthorized(
            JsonObject body,
            String transportSessionId,
            Session transportSession,
            SourceOwner authorizedSourceOwner) {
        JsonObject validation = validateSourceRequest(
                body, transportSessionId, transportSession);
        if (validation != null) return validation;

        MemoryState state;
        JsonObject response;
        try {
            synchronized (stateLock) {
                SourceOwner sourceOwner = resolveSourceOwner(
                        authorizedSourceOwner, body, transportSessionId, transportSession);
                requireUsableSourceOwner(sourceOwner);
                state = current;
                if (state == null) {
                    return failure(body, "Open the Memory List before synchronizing it.");
                }
                String ownerEpoch = string(body, "ownerEpoch");
                if (state.botJobId != sourceOwner.botJobId
                        || ownerEpoch.isEmpty()
                        || !state.ownerEpoch.equals(ownerEpoch)) {
                    return failure(
                            body,
                            "Memory List ownership changed. Open this Memory List again to continue.");
                }
                SourceState source = state.sources.get(sourceKind(transportSessionId));
                if (source == null
                        || source.transport != transportSession
                        || !source.sessionId.equals(transportSessionId)) {
                    return failure(body, "Memory List source ownership changed. Open it again.");
                }
                upsertSource(
                        state,
                        body,
                        transportSessionId,
                        transportSession,
                        sourceOwner);
                if (COMPONENT_SOURCE.equals(sourceKind(transportSessionId))
                        || PAGE_MAPPINGS_SOURCE.equals(sourceKind(transportSessionId))) {
                    reloadBlocks(state);
                }
                response = snapshotResponse(state, "Memory List synchronized.");
            }
        } catch (IllegalArgumentException inactiveWorkspace) {
            return failure(
                    body,
                    firstNonBlank(
                            inactiveWorkspace.getMessage(),
                            "The Memory List source no longer matches its active workspace."));
        }
        publishSnapshot(state);
        publishSummaryChanged(state);
        copyRequestId(response, body);
        return response;
    }

    /** Returns the current canonical mixed snapshot to the fixed Memory List page. */
    public JsonObject bootstrap(JsonObject body, String transportSessionId, Session transportSession) {
        JsonObject validation =
                validateWorkspaceTransport(body, transportSessionId, transportSession);
        if (validation != null) return validation;

        MemoryState state;
        synchronized (stateLock) {
            validation = validateWorkspaceTransport(
                    body, transportSessionId, transportSession);
            if (validation != null) return validation;
            launchPending = false;
            state = current;
        }
        if (state == null) return failure(body, "No Memory List is available.");
        publishSnapshot(state);
        JsonObject response = snapshotResponse(state, "Memory List loaded.");
        copyRequestId(response, body);
        return response;
    }

    /** Executes or routes a command against the canonical mixed list. */
    public JsonObject command(JsonObject body, String transportSessionId, Session transportSession) {
        JsonObject validation =
                validateWorkspaceTransport(body, transportSessionId, transportSession);
        if (validation != null) return validation;
        if (body == null) return failure(null, "Memory List command body is required.");

        String requestId = string(body, "requestId");
        if (requestId.length() > MAX_REQUEST_ID_CHARACTERS) {
            return failure(body, "A valid Memory List request ID is required.");
        }
        if (requestId.isEmpty()) requestId = "memory-list-" + UUID.randomUUID();
        String command = canonicalCommand(string(body, "command"));
        if (command.isEmpty()) command = canonicalCommand(string(body, "action"));
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

        JsonObject response;
        MemoryState state;
        synchronized (stateLock) {
            validation = validateWorkspaceTransport(
                    body, transportSessionId, transportSession);
            if (validation != null) return validation;
            state = current;
            if (state == null) return failure(body, "No Memory List source is available.");
            String ownerEpoch = string(body, "ownerEpoch");
            if (ownerEpoch.isEmpty() || !state.ownerEpoch.equals(ownerEpoch)) {
                return failure(
                        body,
                        "Memory List content changed. Refresh the detached Memory List and try again.");
            }

            response = switch (command) {
                case "REORDER" -> reorder(state, payload, body);
                case "REMOVE" -> remove(state, payload, body, requestId);
                case "CLEAR" -> clear(state, body, requestId);
                case "SELECT_TARGET_BLOCK" -> selectTarget(state, payload, body, requestId);
                case "CREATE_BLOCK" -> createBlock(state, payload, body, requestId);
                case "CREATE_BLOCK_AND_APPLY" ->
                        createBlockAndApply(state, payload, body, requestId);
                case "APPLY" -> apply(state, payload, body, requestId);
                default -> failure(body, "Unsupported Memory List command.");
            };
        }
        publishSnapshot(state);
        if (booleanValue(response, "ok")) {
            publishSummaryChanged(state);
        }
        return response;
    }

    /**
     * Registers one lightweight count subscriber for a Memory List producer surface.
     *
     * <p>The canonical mixed list stays backend-owned. Producers receive only the count and
     * ownership revision for their exact Home Banking/Bot Job scope, never the full mixed payload.
     */
    public JsonObject summary(
            JsonObject body, String transportSessionId, Session transportSession) {
        if (PAGE_MAPPINGS_SOURCE.equals(sourceKind(transportSessionId))) {
            return withAuthorizedPageMappingsSource(
                    body,
                    transportSessionId,
                    transportSession,
                    sourceOwner -> summaryAuthorized(
                            body, transportSessionId, transportSession, sourceOwner));
        }
        if (ScannerWorkspaceSessions.isPageScannerSession(transportSessionId)) {
            return withAuthorizedDetachedPageScannerSource(
                    body,
                    transportSessionId,
                    transportSession,
                    sourceOwner -> summaryAuthorized(
                            body, transportSessionId, transportSession, sourceOwner));
        }
        return summaryAuthorized(body, transportSessionId, transportSession, null);
    }

    private JsonObject summaryAuthorized(
            JsonObject body,
            String transportSessionId,
            Session transportSession,
            SourceOwner authorizedSourceOwner) {
        JsonObject validation = validateSummaryRequest(
                body, transportSessionId, transportSession);
        if (validation != null) return validation;

        try {
            synchronized (stateLock) {
                SourceOwner sourceOwner = resolveSourceOwner(
                        authorizedSourceOwner, body, transportSessionId, transportSession);
                requireUsableSourceOwner(sourceOwner);
                SummarySubscriber subscriber = new SummarySubscriber(
                        sourceKind(transportSessionId),
                        transportSessionId,
                        transportSession,
                        sourceOwner.botJobId,
                        sourceOwner.homeBankingId,
                        sourceOwner.workspaceEpoch);
                pruneSummarySubscribers();
                // There is one authoritative producer transport per source kind. A reconnect or
                // Bot-Job retarget replaces the previous subscription instead of accumulating rows.
                summarySubscribers.put(subscriber.kind, subscriber);
                return summaryResponse(body, subscriber, current, "Memory List count loaded.");
            }
        } catch (IllegalArgumentException inactiveWorkspace) {
            return failure(
                    body,
                    firstNonBlank(
                            inactiveWorkspace.getMessage(),
                            "The Memory List source no longer matches its active workspace."));
        }
    }

    /** Removes only the exact closed/taken-over producer transport. */
    public void disconnected(String transportSessionId, Session transportSession) {
        if (transportSessionId == null || transportSession == null) return;
        synchronized (stateLock) {
            summarySubscribers.entrySet().removeIf(entry -> {
                SummarySubscriber subscriber = entry.getValue();
                return subscriber.transport == transportSession
                        && subscriber.sessionId.equals(transportSessionId);
            });
        }
    }

    /**
     * Atomically validates the current Memory owner generation before registration/takeover.
     *
     * <p>A detached browser process can connect after its Bot Job was deleted. The capability is
     * bound to the current in-memory owner and is revoked by clearing or replacing that owner, so a
     * delayed process cannot reclaim the fixed logical session or attach to a reused numeric ID.
     */
    boolean authorizeAndTakeOverWindowTransport(Session requesterTransport) {
        String assertedCapability = requestParameter(requesterTransport, "windowCapability");
        if (assertedCapability.isBlank() || assertedCapability.length() > 256) return false;
        synchronized (stateLock) {
            if (current == null
                    || !constantTimeEquals(
                            current.windowCapability, assertedCapability)) {
                return false;
            }
            WebSocketSessionManager.takeOverSession(
                    WORKSPACE_SESSION_ID, requesterTransport);
            launchPending = false;
            return true;
        }
    }

    /**
     * Removes only the Page Mappings contribution owned by the previous binding.
     *
     * <p>A detached Page Mappings window is reused across Bot Jobs. Its staged rows must not
     * survive that retarget and later be applied to the new owner.
     */
    void pageMappingsRetargeted(
            PageMappingsWorkspaceService.Binding previous,
            PageMappingsWorkspaceService.Binding currentBinding) {
        MemoryState changedState = null;
        boolean stateCleared = false;
        Session retiredWorkspace = null;
        String retirementReason =
                "Memory List was closed because its Bot Job owner is no longer available.";
        synchronized (stateLock) {
            summarySubscribers.remove(PAGE_MAPPINGS_SOURCE);
            if (currentBinding == null) {
                boolean clearEveryOwner = previous == null;
                if (clearEveryOwner) {
                    summarySubscribers.clear();
                } else {
                    summarySubscribers.entrySet().removeIf(entry -> {
                        SummarySubscriber subscriber = entry.getValue();
                        return subscriber.botJobId == previous.botJobId()
                                && subscriber.homeBankingId == previous.homeBankingId();
                    });
                }
                if (current != null
                        && (clearEveryOwner
                                || (current.botJobId == previous.botJobId()
                                        && current.homeBankingId == previous.homeBankingId()))) {
                    // A committed delete/full replacement must retire the complete owner state,
                    // including ownerEpoch, completed-request ledger, other stale sources, and
                    // row ordering. Keeping only a source-level cleanup permits Bot Job ID reuse
                    // to inherit authority from the deleted owner.
                    current = null;
                    launchPending = false;
                    stateCleared = true;
                    retiredWorkspace = retireWorkspaceTransportLocked(retirementReason);
                }
                if (!stateCleared) return;
            } else {
                if (previous == null || current == null) return;
                SourceState source = current.sources.get(PAGE_MAPPINGS_SOURCE);
                if (source == null
                        || !previous.bindingEpoch().equals(
                                string(source.snapshot, "bindingEpoch"))) {
                    return;
                }
                current.sources.remove(PAGE_MAPPINGS_SOURCE);
                for (String itemKey : source.itemKeys) {
                    current.items.remove(itemKey);
                    current.order.remove(itemKey);
                    current.suppressedKeys.remove(itemKey);
                }
                rebuildBlocks(current);
                current.revision++;
                changedState = current;
            }
        }
        if (changedState != null) {
            publishSnapshot(changedState);
        }
        if (changedState != null || stateCleared) publishSummaryChanged();
        if (stateCleared) {
            closeRetiredWorkspace(retiredWorkspace);
        }
    }

    /** Retires the complete Memory owner for any Bot Job that was committed deleted. */
    void botJobsDeleted(java.util.Collection<Integer> botJobIds) {
        if (botJobIds == null || botJobIds.isEmpty()) return;
        Set<Integer> deleted = botJobIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (deleted.isEmpty()) return;
        boolean changed;
        boolean ownerStateCleared = false;
        Session retiredWorkspace = null;
        String retirementReason = "Memory List was closed because its Bot Job was deleted.";
        synchronized (stateLock) {
            changed = summarySubscribers.entrySet().removeIf(
                    entry -> deleted.contains(entry.getValue().botJobId));
            if (current != null && deleted.contains(current.botJobId)) {
                current = null;
                launchPending = false;
                changed = true;
                ownerStateCleared = true;
                retiredWorkspace = retireWorkspaceTransportLocked(retirementReason);
            }
        }
        if (changed) publishSummaryChanged();
        if (ownerStateCleared) {
            closeRetiredWorkspace(retiredWorkspace);
        }
    }

    /** Retires every Memory owner after a committed full database replacement. */
    void allBotJobsReplaced() {
        boolean changed;
        boolean hadOwner;
        Session retiredWorkspace;
        String retirementReason =
                "Memory List was closed because the Bot Job database was replaced.";
        synchronized (stateLock) {
            changed = current != null
                    || !summarySubscribers.isEmpty()
                    || launchPending
                    || WebSocketSessionManager.getSession(WORKSPACE_SESSION_ID) != null;
            hadOwner = current != null;
            current = null;
            summarySubscribers.clear();
            launchPending = false;
            retiredWorkspace = retireWorkspaceTransportLocked(retirementReason);
        }
        if (changed) publishSummaryChanged();
        if (hadOwner || retiredWorkspace != null) {
            closeRetiredWorkspace(retiredWorkspace);
        }
    }

    /**
     * Retires only the exact physical transport present when its owner is invalidated.
     *
     * <p>This runs under {@code stateLock}, the same lock used by capability takeover and new
     * owner creation. A replacement transport therefore cannot register between clearing the old
     * owner and removing its socket, and a delayed close can never kill the replacement.
     */
    private Session retireWorkspaceTransportLocked(String reason) {
        Session retired = WebSocketSessionManager.getSession(WORKSPACE_SESSION_ID);
        if (retired == null) return null;
        try {
            PagesOpenWorkspaceService.getInstance()
                    .closeDetachedWorkspaceSession(WORKSPACE_SESSION_ID, reason);
        } catch (RuntimeException closeMessageFailure) {
            // Exact server-side retirement below remains authoritative.
        }
        WebSocketSessionManager.removeSession(WORKSPACE_SESSION_ID, retired);
        return retired;
    }

    private static void closeRetiredWorkspace(Session retired) {
        if (retired == null) return;
        try {
            if (retired.isOpen()) retired.close();
        } catch (java.io.IOException | RuntimeException ignored) {
            // The exact registry pair was already retired under stateLock.
        }
    }

    private JsonObject reorder(MemoryState state, JsonObject payload, JsonObject request) {
        JsonArray requested = array(payload, "orderedItemKeys");
        List<String> requestedKeys = null;
        if (requested != null) {
            requestedKeys = new ArrayList<>(requested.size());
            for (JsonElement value : requested) {
                try {
                    requestedKeys.add(value.getAsString());
                } catch (RuntimeException invalid) {
                    return failure(request, "Memory List order contains an invalid row.");
                }
            }
        }
        MemoryListReorder.Outcome outcome =
                MemoryListReorder.resolveGrouped(
                        state.order,
                        state.items.keySet(),
                        dependencyGroups(state),
                        requestedKeys);
        if (!outcome.ok()) {
            return failure(request, outcome.error());
        }
        state.order.clear();
        state.order.addAll(outcome.orderedKeys());
        state.revision++;
        return success(request, state, "Memory List order updated.");
    }

    private JsonObject remove(
            MemoryState state, JsonObject payload, JsonObject request, String requestId) {
        String globalKey = string(payload, "itemKey");
        AggregatedItem item = state.items.get(globalKey);
        if (item == null) return failure(request, "Memory List row is no longer available.");
        List<String> removalKeys = MemoryListReorder.connectedRemovalKeys(
                state.order, dependencyGroups(state), globalKey);
        List<AggregatedItem> removalItems = removalKeys.stream()
                .map(state.items::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        for (AggregatedItem removalItem : removalItems) {
            suppressAndRemove(state, removalItem);
        }
        for (AggregatedItem removalItem : removalItems) {
            JsonObject sourcePayload = new JsonObject();
            sourcePayload.addProperty("itemKey", removalItem.sourceItemKey);
            sourcePayload.addProperty("sourceItemKey", removalItem.sourceItemKey);
            forward(
                    state,
                    removalItem.sourceKind,
                    requestId,
                    "REMOVE",
                    sourcePayload);
        }
        state.revision++;
        return success(
                request,
                state,
                removalItems.size() <= 1
                        ? "Memory List row removed."
                        : removalItems.size()
                                + " connected Memory List rows removed together.");
    }

    private JsonObject clear(MemoryState state, JsonObject request, String requestId) {
        for (AggregatedItem item : new ArrayList<>(state.items.values())) {
            state.suppressedKeys.add(item.globalKey);
        }
        state.items.clear();
        state.order.clear();
        for (SourceState source : state.sources.values()) {
            forward(state, source.kind, requestId, "CLEAR", new JsonObject());
        }
        state.revision++;
        return success(request, state, "Memory List cleared.");
    }

    private JsonObject selectTarget(
            MemoryState state, JsonObject payload, JsonObject request, String requestId) {
        int blockId = positiveInteger(payload, "blockId");
        if (blockId <= 0) {
            state.targetBlockId = null;
        } else if (!state.blocks.containsKey(blockId)) {
            return failure(request, "The selected target block is no longer available.");
        } else {
            state.targetBlockId = blockId;
        }
        JsonObject sourcePayload = new JsonObject();
        if (state.targetBlockId == null) {
            sourcePayload.add("blockId", com.google.gson.JsonNull.INSTANCE);
        } else {
            sourcePayload.addProperty("blockId", state.targetBlockId);
        }
        for (SourceState source : state.sources.values()) {
            forward(state, source.kind, requestId, "SELECT_TARGET_BLOCK", sourcePayload);
        }
        state.revision++;
        return success(request, state, "Memory List target block updated.");
    }

    private JsonObject createBlock(
            MemoryState state, JsonObject payload, JsonObject request, String requestId) {
        if (!isActiveBotJob(state.botJobId)) {
            return failure(
                    request,
                    "Memory List belongs to a Bot Job that is no longer open. Add the rows again.");
        }
        String blockName = string(payload, "blockName").trim();
        if (blockName.isEmpty() || blockName.length() > 256) {
            return failure(request, "Block name must contain 1 to 256 characters.");
        }
        JsonObject position = object(payload, "position");
        String positionType = string(position, "type").toUpperCase(Locale.ROOT);
        SplitDTO dto = new SplitDTO();
        dto.setBotJobId(state.botJobId);
        dto.setHomeBankingId(state.homeBankingId);
        dto.setBlockName(blockName);
        if ("BEFORE".equals(positionType)) {
            dto.setInsertPosition("BEFORE");
            dto.setBeforeBlockId(positiveInteger(position, "blockId"));
            dto.setBeforeBlockOrderNumber(positiveInteger(position, "blockOrderNumber"));
        } else {
            dto.setInsertPosition("END");
        }

        BlockCreationService.Result result = blockCreationService.createFrom(dto);
        if (result.newBlockId() == null || result.newBlockId() <= 0) {
            return failure(
                    request,
                    result.error() == null
                            ? "The target block could not be created."
                            : result.error().getErrorMessage());
        }
        ErrorMessage blockRefreshError = reloadBlocks(state);
        state.targetBlockId = result.newBlockId();
        state.revision++;

        JsonObject selected = new JsonObject();
        selected.addProperty("blockId", state.targetBlockId);
        for (SourceState source : state.sources.values()) {
            forward(state, source.kind, requestId, "SELECT_TARGET_BLOCK", selected);
        }
        boolean synchronizedSnapshot = blockRefreshError == null
                && publishCreatedBlockSnapshot(
                        state,
                        requestId,
                        result.newBlockId(),
                        blockName,
                        result.newBlockOrderNumber());
        JsonObject response = success(
                request,
                state,
                synchronizedSnapshot
                        ? "Target block created."
                        : "Target block created; Bot Job Details refresh is pending.");
        response.addProperty("createdBlockId", result.newBlockId());
        response.addProperty("createdBlockOrderNumber", result.newBlockOrderNumber());
        response.addProperty("synchronized", synchronizedSnapshot);
        return response;
    }

    /**
     * Performs the detached Memory List's one-click new-block workflow in one database transaction.
     *
     * <p>The transactional apply service creates and reads back the block before inserting any
     * instructions, then commits both together. A validation or insert failure therefore leaves
     * neither an empty block nor partial instructions.
     */
    private JsonObject createBlockAndApply(
            MemoryState state, JsonObject payload, JsonObject request, String requestId) {
        String requestFingerprint =
                completedApplyFingerprint("CREATE_BLOCK_AND_APPLY", payload);
        JsonObject completed =
                completedApplyResponse(state, requestId, requestFingerprint, request);
        if (completed != null) {
            return completed;
        }

        String blockName = string(payload, "blockName").trim();
        if (blockName.isEmpty() || blockName.length() > 256) {
            return compositeFailure(
                    request, "Block name must contain 1 to 256 characters.", blockName);
        }

        JsonObject position = object(payload, "position");
        String positionType = string(position, "type").toUpperCase(Locale.ROOT);
        BlockCreationService.Position insertPosition = "BEFORE".equals(positionType)
                ? BlockCreationService.Position.BEFORE
                : BlockCreationService.Position.END;
        ComponentMemoryApplyService.NewTargetBlock target =
                new ComponentMemoryApplyService.NewTargetBlock(
                        blockName,
                        insertPosition,
                        "BEFORE".equals(positionType)
                                ? positiveInteger(position, "blockId")
                                : null,
                        "BEFORE".equals(positionType)
                                ? positiveInteger(position, "blockOrderNumber")
                                : null);

        JsonObject response =
                apply(
                        state,
                        new JsonObject(),
                        request,
                        requestId,
                        target,
                        requestFingerprint);
        boolean committed = booleanValue(response, "committed");
        boolean synchronizedSnapshot = booleanValue(response, "synchronized");
        int createdBlockId = positiveInteger(response, "createdBlockId");
        response.addProperty("workflow", "CREATE_BLOCK_AND_APPLY");
        response.addProperty("blockCreated", committed && createdBlockId > 0);
        response.addProperty("blockVerified", committed && createdBlockId > 0);
        response.addProperty("instructionsApplied", committed);
        response.addProperty("createdBlockName", blockName);
        if (committed) {
            int appliedCount = positiveInteger(response, "appliedCount");
            response.addProperty(
                    "message",
                    "Block \"" + blockName + "\" was created and verified; "
                            + (appliedCount > 0
                                    ? appliedCount + " Memory List item"
                                            + (appliedCount == 1 ? " was" : "s were")
                                            + " applied successfully"
                                    : "the Memory List was applied successfully")
                            + (synchronizedSnapshot
                                    ? "."
                                    : "; Bot Job Details refresh is pending."));
        } else {
            response.addProperty(
                    "message",
                    firstNonBlank(
                                    string(response, "message"),
                                    "The new block and its instructions could not be saved.")
                            + " No block or instruction was saved.");
        }
        if (committed) {
            rememberCompletedApply(state, requestId, requestFingerprint, response);
        }
        return response;
    }

    private JsonObject compositeFailure(
            JsonObject request, String message, String blockName) {
        JsonObject response = failure(request, message);
        response.addProperty("workflow", "CREATE_BLOCK_AND_APPLY");
        response.addProperty("committed", false);
        response.addProperty("blockCreated", false);
        response.addProperty("blockVerified", false);
        response.addProperty("instructionsApplied", false);
        response.addProperty("createdBlockName", blockName);
        return response;
    }

    private boolean publishCreatedBlockSnapshot(
            MemoryState state,
            String requestId,
            Integer createdBlockId,
            String createdBlockName,
            Integer createdBlockOrderNumber) {
        JsonObject correlation = new JsonObject();
        correlation.addProperty("memoryListRequestId", requestId);
        correlation.addProperty("createdBlockId", createdBlockId);
        correlation.addProperty("createdBlockName", createdBlockName);
        correlation.addProperty("createdBlockOrderNumber", createdBlockOrderNumber);
        correlation.addProperty("targetBlockId", createdBlockId);
        correlation.addProperty("botJobName", state.botJobName);
        return publishStructuredBotJobSnapshot(state, correlation) == null;
    }

    private ErrorMessage publishStructuredBotJobSnapshot(
            MemoryState state, JsonObject correlation) {
        JsonArray blocks = new JsonArray();
        state.persistedBlocks.values().forEach(block -> blocks.add(block.deepCopy()));
        return botJobTasksPublisher.publishStructured(
                state.homeBankingId, state.botJobId, blocks, correlation);
    }

    private JsonObject apply(
            MemoryState state, JsonObject payload, JsonObject request, String requestId) {
        return apply(
                state,
                payload,
                request,
                requestId,
                null,
                completedApplyFingerprint("APPLY", payload));
    }

    private JsonObject apply(
            MemoryState state,
            JsonObject payload,
            JsonObject request,
            String requestId,
            ComponentMemoryApplyService.NewTargetBlock newTargetBlock,
            String requestFingerprint) {
        JsonObject completed =
                completedApplyResponse(state, requestId, requestFingerprint, request);
        if (completed != null) {
            return completed;
        }
        if (!isActiveBotJob(state.botJobId)) {
            return failure(
                    request,
                    "Memory List belongs to a Bot Job that is no longer open. Add the rows again.");
        }
        int requestedTarget = positiveInteger(payload, "targetBlockId");
        int targetBlockId = newTargetBlock != null
                ? -1
                : requestedTarget > 0
                        ? requestedTarget
                        : state.targetBlockId == null ? -1 : state.targetBlockId;

        List<AggregatedItem> applicable = state.order.stream()
                .map(state.items::get)
                .filter(Objects::nonNull)
                .filter(item -> !isElementSource(item.sourceKind)
                        || !item.presentation.has("active")
                        || item.presentation.get("active").isJsonNull()
                        || item.presentation.get("active").getAsBoolean())
                .toList();
        if (applicable.isEmpty()) return failure(request, "No active Memory List rows are available.");

        List<ComponentMemoryApplyService.OrderedItem> orderedItems = new ArrayList<>();
        // The transactional service assigns the final authoritative target order. This value is
        // only a valid placeholder required by the Page Scanner DTO converter.
        int nextTargetOrder = 1;
        for (AggregatedItem item : applicable) {
            if (BOT_JOB_SOURCE.equals(item.sourceKind)) {
                int instructionId = positiveInteger(item.payload, "instructionId");
                if (instructionId <= 0) instructionId = positiveInteger(item.presentation, "sourceItemKey");
                String sourceRevision = string(item.payload, "sourceRevision");
                orderedItems.add(
                        ComponentMemoryApplyService.OrderedItem.botJob(
                                item.globalKey, instructionId, sourceRevision));
                continue;
            }
            if (PAGE_MAPPINGS_SOURCE.equals(item.sourceKind)) {
                PageMappingInstructionReference reference = pageMappingReference(item.payload);
                if (reference == null || !reference.valid()) {
                    return failure(
                            request,
                            "A Page Mapping row is stale or incomplete. Reload Page Mappings and add it again.");
                }
                orderedItems.add(
                        ComponentMemoryApplyService.OrderedItem.pageMapping(
                                item.globalKey, reference));
                continue;
            }
            if (PAGE_SCANNER_SOURCE.equals(item.sourceKind)) {
                JsonObject elementObject = object(item.payload, "elementDTO");
                if (elementObject == null) {
                    return failure(request, "A Page Scanner row has no element data. Add it again.");
                }
                ElementDTO element;
                try {
                    element = gson.fromJson(elementObject, ElementDTO.class);
                } catch (RuntimeException invalid) {
                    return failure(request, "A Page Scanner row contains invalid element data.");
                }
                // The scanner DTO converter requires a positive block ID. For a new-block request
                // this value is transient only; ComponentMemoryApplyService replaces it with the
                // verified generated block ID before inserting the row.
                InstructionLoad instruction = preScanApplyService.buildMemoryListInstruction(
                        element,
                        state.botJobId,
                        targetBlockId > 0
                                ? targetBlockId
                                : PENDING_NEW_BLOCK_PLACEHOLDER_ID,
                        nextTargetOrder++);
                if (instruction == null) {
                    return failure(
                            request,
                            "A Page Scanner row could not be converted to an instruction.");
                }
                orderedItems.add(
                        ComponentMemoryApplyService.OrderedItem.scanner(
                                item.globalKey, instruction));
                continue;
            }
            if (!COMPONENT_SOURCE.equals(item.sourceKind)) {
                return failure(request, "Memory List contains an unsupported source row.");
            }
            String kind = string(item.payload, "kind");
            ComponentMemoryApplyService.ItemKind componentKind =
                    ComponentMemoryApplyService.ItemKind.componentKind(kind);
            String sourceRevision = string(item.payload, "sourceRevision");
            if (sourceRevision.isEmpty()) {
                sourceRevision = string(item.payload, "graphRevision");
            }
            if (componentKind == ComponentMemoryApplyService.ItemKind.COMPONENT_INSTRUCTION) {
                orderedItems.add(ComponentMemoryApplyService.OrderedItem.componentInstruction(
                        item.globalKey,
                        positiveInteger(item.payload, "componentInstructionId"),
                        positiveInteger(item.payload, "componentBlockId"),
                        sourceRevision));
            } else if (componentKind == ComponentMemoryApplyService.ItemKind.COMPONENT_BLOCK) {
                orderedItems.add(ComponentMemoryApplyService.OrderedItem.componentBlock(
                        item.globalKey,
                        positiveInteger(item.payload, "componentBlockId"),
                        sourceRevision));
            } else {
                return failure(
                        request,
                        "A Components row has no valid INSTRUCTION or BLOCK payload.");
            }
        }

        ComponentMemoryApplyService.Result transaction = componentMemoryApplyService.apply(
                new ComponentMemoryApplyService.Request(
                        requestId,
                        state.botJobId,
                        state.homeBankingId,
                        targetBlockId,
                        orderedItems,
                        newTargetBlock));
        if (!transaction.committed()) {
            ErrorMessage transactionError = transaction.error();
            String message = transactionError == null
                    ? "Memory List could not be applied."
                    : firstNonBlank(
                            transactionError.getErrorHeader(),
                            transactionError.getErrorMessage(),
                            transactionError.getErrorTitle());
            JsonObject response = failure(request, message);
            response.addProperty("committed", false);
            response.addProperty("synchronized", false);
            return response;
        }

        int effectiveTargetBlockId = transaction.createdTargetBlockId() > 0
                ? transaction.createdTargetBlockId()
                : targetBlockId;
        ErrorMessage refreshError = reloadBlocks(state);
        if (refreshError == null) {
            JsonObject correlation = new JsonObject();
            correlation.addProperty("memoryListRequestId", requestId);
            correlation.addProperty("targetBlockId", effectiveTargetBlockId);
            if (transaction.createdTargetBlockId() > 0) {
                correlation.addProperty(
                        "createdBlockId", transaction.createdTargetBlockId());
                correlation.addProperty(
                        "createdBlockName", newTargetBlock.blockName());
                correlation.addProperty(
                        "createdBlockOrderNumber",
                        transaction.createdTargetBlockOrderNumber());
            }
            correlation.addProperty("botJobName", state.botJobName);
            correlation.addProperty("memoryListAppliedCount", transaction.appliedCount());
            refreshError = publishStructuredBotJobSnapshot(state, correlation);
        }
        if (transaction.createdTargetBlockId() > 0) {
            JsonObject selected = new JsonObject();
            selected.addProperty("blockId", transaction.createdTargetBlockId());
            for (SourceState source : state.sources.values()) {
                forward(state, source.kind, requestId, "SELECT_TARGET_BLOCK", selected);
            }
        }
        Set<String> appliedKeys = applicable.stream().map(item -> item.globalKey).collect(
                java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        Map<String, List<AggregatedItem>> appliedBySource = new LinkedHashMap<>();
        for (String key : appliedKeys) {
            AggregatedItem item = state.items.get(key);
            if (item == null) continue;
            appliedBySource.computeIfAbsent(item.sourceKind, ignored -> new ArrayList<>()).add(item);
            suppressAndRemove(state, item);
        }
        for (var sourceItems : appliedBySource.entrySet()) {
            SourceState source = state.sources.get(sourceItems.getKey());
            if (source == null) continue;
            boolean removedEverySourceItem = source.itemKeys.stream().allMatch(appliedKeys::contains);
            if (removedEverySourceItem) {
                forward(state, source.kind, requestId, "CLEAR", new JsonObject());
            } else {
                for (AggregatedItem item : sourceItems.getValue()) {
                    JsonObject removePayload = new JsonObject();
                    removePayload.addProperty("itemKey", item.sourceItemKey);
                    removePayload.addProperty("sourceItemKey", item.sourceItemKey);
                    forward(state, source.kind, requestId, "REMOVE", removePayload);
                }
            }
        }
        if (effectiveTargetBlockId > 0) state.targetBlockId = effectiveTargetBlockId;
        state.revision++;

        JsonObject response = success(
                request,
                state,
                refreshError == null
                        ? "Memory List applied."
                        : "Memory List applied; Bot Job Details refresh is pending.");
        response.addProperty("committed", true);
        response.addProperty("duplicate", transaction.duplicate());
        response.addProperty("synchronized", refreshError == null);
        response.addProperty("appliedCount", transaction.appliedCount());
        if (transaction.createdTargetBlockId() > 0) {
            response.addProperty("createdBlockId", transaction.createdTargetBlockId());
            response.addProperty(
                    "createdBlockOrderNumber",
                    transaction.createdTargetBlockOrderNumber());
        }
        response.add(
                "generatedInstructionIds",
                gson.toJsonTree(transaction.generatedInstructionIds()));
        response.add("generatedBlockIds", gson.toJsonTree(transaction.generatedBlockIds()));
        rememberCompletedApply(state, requestId, requestFingerprint, response);
        return response;
    }

    private boolean isActiveBotJob(int botJobId) {
        try {
            BotJobDetailsWorkspaceRegistry.getInstance().require(botJobId);
            return true;
        } catch (IllegalArgumentException inactiveWorkspace) {
            return false;
        }
    }

    private void suppressAndRemove(MemoryState state, AggregatedItem item) {
        state.suppressedKeys.add(item.globalKey);
        state.items.remove(item.globalKey);
        state.order.remove(item.globalKey);
    }

    private Map<String, String> dependencyGroups(MemoryState state) {
        Map<String, String> groups = new LinkedHashMap<>();
        for (AggregatedItem item : state.items.values()) {
            String groupKey = string(item.presentation, "dependencyGroupKey");
            if (!groupKey.isBlank()) groups.put(item.globalKey, groupKey);
        }
        return groups;
    }

    private void forward(
            MemoryState state,
            String sourceKind,
            String requestId,
            String command,
            JsonObject payload) {
        SourceState source = state.sources.get(sourceKind);
        if (source == null) return;
        Session registered = WebSocketSessionManager.getSession(source.sessionId);
        if (registered == null || registered != source.transport || !registered.isOpen()) return;
        JsonObject forwarded = new JsonObject();
        forwarded.addProperty("requestId", requestId);
        forwarded.addProperty("command", command);
        forwarded.addProperty("action", command);
        forwarded.addProperty("botJobId", state.botJobId);
        forwarded.addProperty("homeBankingId", state.homeBankingId);
        forwarded.addProperty("memoryListSessionId", WORKSPACE_SESSION_ID);
        forwarded.addProperty("ownerEpoch", state.ownerEpoch);
        addSourceCorrelation(
                forwarded, sourceKind, source.snapshot, state.workspaceEpoch);
        forwarded.add("payload", payload.deepCopy());
        WebSocketSessionManager.sendMessageJson(
                state.homeBankingId,
                registered,
                source.sessionId,
                gson.toJson(forwarded),
                SOURCE_COMMAND_OPERATION);
    }

    static void addSourceCorrelation(
            JsonObject forwarded,
            String sourceKind,
            JsonObject sourceSnapshot,
            long workspaceEpoch) {
        if (forwarded == null) return;
        forwarded.addProperty("workspaceEpoch", workspaceEpoch);
        if (!PAGE_MAPPINGS_SOURCE.equals(sourceKind)) return;
        String bindingEpoch = string(sourceSnapshot, "bindingEpoch");
        if (!bindingEpoch.isBlank()) {
            forwarded.addProperty("sourceBindingEpoch", bindingEpoch);
        }
    }

    private void upsertSource(
            MemoryState state,
            JsonObject body,
            String transportSessionId,
            Session transportSession,
            SourceOwner sourceOwner) {
        JsonObject submittedSnapshot = snapshot(body).deepCopy();
        String kind = sourceKind(transportSessionId);
        if (COMPONENT_SOURCE.equals(kind)) {
            submittedSnapshot.addProperty("botJobId", sourceOwner.botJobId);
            submittedSnapshot.addProperty("botJobName", sourceOwner.botJobName);
            submittedSnapshot.addProperty("homeBankingId", sourceOwner.homeBankingId);
            submittedSnapshot.add("blocks", new JsonArray());
            submittedSnapshot.remove("targetBlockId");
        } else if (PAGE_MAPPINGS_SOURCE.equals(kind)) {
            submittedSnapshot.addProperty("botJobId", sourceOwner.botJobId);
            submittedSnapshot.addProperty("botJobName", sourceOwner.botJobName);
            submittedSnapshot.addProperty("homeBankingId", sourceOwner.homeBankingId);
            submittedSnapshot.addProperty("workspaceEpoch", sourceOwner.workspaceEpoch);
            submittedSnapshot.addProperty("bindingEpoch", sourceOwner.bindingEpoch);
        }
        SourceState previous = state.sources.get(kind);
        Set<String> previousKeys =
                previous == null ? Set.of() : new HashSet<>(previous.itemKeys);
        Set<String> submittedKeys = new HashSet<>();
        JsonArray submittedItems = array(submittedSnapshot, "items");
        if (submittedItems != null) {
            for (JsonElement value : submittedItems) {
                if (!value.isJsonObject()) continue;
                JsonObject item = value.getAsJsonObject();
                String submittedKey = string(item, "key");
                String rawKey = string(item, "sourceItemKey");
                if (rawKey.isEmpty()) {
                    rawKey = submittedKey.startsWith(kind + ":")
                            ? submittedKey.substring((kind + ":").length())
                            : submittedKey;
                }
                if (rawKey.isEmpty()) continue;
                String globalKey = kind + ":" + rawKey;
                submittedKeys.add(globalKey);
                if (state.suppressedKeys.contains(globalKey)) continue;

                JsonObject presentation = item.deepCopy();
                presentation.addProperty("key", globalKey);
                presentation.addProperty("sourceKind", kind);
                presentation.addProperty("sourceItemKey", rawKey);
                JsonObject payload = object(presentation, "payload");
                if (payload == null) payload = new JsonObject();
                if (PAGE_MAPPINGS_SOURCE.equals(kind)) {
                    PageMappingInstructionReference reference = pageMappingReference(payload);
                    payload = pageMappingPayload(reference);
                    presentation = pageMappingPresentation(
                            item, globalKey, rawKey, payload);
                }
                if (BOT_JOB_SOURCE.equals(kind)
                        && positiveInteger(payload, "instructionId") <= 0) {
                    try {
                        payload.addProperty("instructionId", Integer.parseInt(rawKey));
                    } catch (NumberFormatException ignored) {
                        // Apply will reject a non-numeric instruction key with a clear message.
                    }
                }
                presentation.add("payload", payload);
                AggregatedItem aggregate =
                        new AggregatedItem(globalKey, kind, rawKey, presentation, payload.deepCopy());
                if (!state.items.containsKey(globalKey)) state.order.add(globalKey);
                state.items.put(globalKey, aggregate);
            }
        }

        for (String oldKey : previousKeys) {
            if (!submittedKeys.contains(oldKey)) {
                state.items.remove(oldKey);
                state.order.remove(oldKey);
                state.suppressedKeys.remove(oldKey);
            }
        }
        state.suppressedKeys.removeIf(key ->
                key.startsWith(kind + ":") && !submittedKeys.contains(key));
        submittedSnapshot.addProperty("ownerEpoch", state.ownerEpoch);
        state.sources.put(
                kind,
                new SourceState(
                        kind,
                        transportSessionId,
                        transportSession,
                        submittedSnapshot,
                        submittedKeys));
        String botJobName = string(submittedSnapshot, "botJobName");
        if (!botJobName.isEmpty()) state.botJobName = botJobName;
        int selectedTarget = positiveInteger(submittedSnapshot, "targetBlockId");
        if (selectedTarget > 0) state.targetBlockId = selectedTarget;
        rebuildBlocks(state);
        state.revision++;
    }

    private void rebuildBlocks(MemoryState state) {
        LinkedHashMap<Integer, JsonObject> merged = new LinkedHashMap<>();
        for (SourceState source : state.sources.values()) {
            JsonArray blocks = array(source.snapshot, "blocks");
            if (blocks == null) continue;
            for (JsonElement value : blocks) {
                if (!value.isJsonObject()) continue;
                JsonObject block = value.getAsJsonObject();
                int blockId = positiveInteger(block, "blockId");
                if (blockId > 0) merged.put(blockId, block.deepCopy());
            }
        }
        for (var block : state.persistedBlocks.entrySet()) {
            merged.put(block.getKey(), block.getValue().deepCopy());
        }
        state.blocks.clear();
        merged.entrySet().stream()
                .sorted(Comparator.comparingInt(entry ->
                        positiveInteger(entry.getValue(), "blockOrderNumber")))
                .forEach(entry -> state.blocks.put(entry.getKey(), entry.getValue()));
        if (state.targetBlockId != null && !state.blocks.containsKey(state.targetBlockId)) {
            state.targetBlockId = null;
        }
    }

    private ErrorMessage reloadBlocks(MemoryState state) {
        ErrorMessage error = performDataBase.loadBlocks(state.botJobId, "", "block");
        if (error != null) return error;
        state.persistedBlocks.clear();
        for (BlockLoadDTO block : performLists.getListBlock()) {
            if (block == null || block.getId() == null || block.getId() <= 0) continue;
            JsonObject option = new JsonObject();
            option.addProperty("blockId", block.getId());
            option.addProperty("blockName", Objects.toString(block.getName(), ""));
            option.addProperty(
                    "blockOrderNumber",
                    block.getBlockOrderNumber() == null ? 0 : block.getBlockOrderNumber());
            option.addProperty("blockActive", block.getActive() == null || block.getActive());
            option.addProperty("blockWait", block.getWait() == null ? 0 : block.getWait());
            if (block.getExportFile() != null) {
                option.addProperty("exportFile", block.getExportFile());
            }
            state.persistedBlocks.put(block.getId(), option);
        }
        rebuildBlocks(state);
        return null;
    }

    private JsonObject validateSourceRequest(
            JsonObject body,
            String transportSessionId,
            Session transportSession) {
        String sourceKind = sourceKind(transportSessionId);
        if (sourceKind.isEmpty()) {
            return failure(
                    body,
                    "Only Bot Job Details, Page Scanner, or Components can update the Memory List.");
        }
        if (!isRegisteredTransport(transportSessionId, transportSession)) {
            return failure(body, "The Memory List source is not authoritative.");
        }
        if (body == null) return failure(null, "Memory List body is required.");
        if (!PAGE_MAPPINGS_SOURCE.equals(sourceKind)
                && positiveInteger(body, "botJobId") <= 0) {
            return failure(body, "A positive Bot Job ID is required.");
        }
        JsonObject snapshot = snapshot(body);
        if (snapshot == null) return failure(body, "A Memory List snapshot is required.");
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
        if (COMPONENT_SOURCE.equals(sourceKind)) {
            JsonObject componentValidation = validateComponentSnapshot(body, snapshot);
            if (componentValidation != null) return componentValidation;
        }
        if (PAGE_MAPPINGS_SOURCE.equals(sourceKind)) {
            JsonObject mappingValidation = validatePageMappingsSnapshot(body, snapshot);
            if (mappingValidation != null) return mappingValidation;
        }
        return null;
    }

    private JsonObject validatePageMappingsSnapshot(
            JsonObject request, JsonObject submittedSnapshot) {
        JsonArray items = array(submittedSnapshot, "items");
        if (items == null) return null;
        Set<String> keys = new HashSet<>();
        for (JsonElement value : items) {
            if (value == null || !value.isJsonObject()) {
                return failure(request, "A Page Mapping Memory List row is invalid.");
            }
            JsonObject item = value.getAsJsonObject();
            String sourceItemKey = string(item, "sourceItemKey");
            if (sourceItemKey.isBlank() || !keys.add(sourceItemKey)) {
                return failure(
                        request,
                        "Page Mapping Memory List rows require unique stable source keys.");
            }
            PageMappingInstructionReference reference =
                    pageMappingReference(object(item, "payload"));
            if (reference == null || !reference.valid()) {
                return failure(
                        request,
                        "A Page Mapping row is stale or incomplete. Reload Page Mappings and add it again.");
            }
        }
        return null;
    }

    /** Whitelists the only six values that may survive Page Mappings staging. */
    static JsonObject pageMappingPayload(PageMappingInstructionReference reference) {
        JsonObject payload = new JsonObject();
        if (reference == null) return payload;
        payload.addProperty("captureId", reference.captureId());
        payload.addProperty("pageKey", reference.pageKey());
        payload.addProperty("scannedElementId", reference.scannedElementId());
        payload.addProperty("elementHash", reference.elementHash());
        payload.addProperty("expectedLastScannedAt", reference.expectedLastScannedAt());
        payload.addProperty("expectedScanCount", reference.expectedScanCount());
        return payload;
    }

    static PageMappingInstructionReference pageMappingReference(JsonObject payload) {
        if (payload == null || !payload.keySet().equals(PAGE_MAPPING_REFERENCE_FIELDS)) return null;
        return new PageMappingInstructionReference(
                string(payload, "captureId"),
                string(payload, "pageKey"),
                positiveLong(payload, "scannedElementId"),
                string(payload, "elementHash"),
                string(payload, "expectedLastScannedAt"),
                positiveInteger(payload, "expectedScanCount"));
    }

    private static JsonObject pageMappingPresentation(
            JsonObject submitted,
            String globalKey,
            String sourceItemKey,
            JsonObject payload) {
        JsonObject presentation = new JsonObject();
        presentation.addProperty("key", globalKey);
        presentation.addProperty("sourceKind", PAGE_MAPPINGS_SOURCE);
        presentation.addProperty("sourceItemKey", sourceItemKey);
        for (String field : List.of("label", "detail", "icon", "active")) {
            JsonElement value = submitted.get(field);
            if (value != null && value.isJsonPrimitive()) {
                presentation.add(field, value.deepCopy());
            }
        }
        presentation.add("payload", payload);
        return presentation;
    }

    private JsonObject validateSummaryRequest(
            JsonObject body,
            String transportSessionId,
            Session transportSession) {
        String kind = sourceKind(transportSessionId);
        if (kind.isEmpty()) {
            return failure(
                    body,
                    "Only Bot Job Details, Page Scanner, or Components can read the Memory List count.");
        }
        if (!isRegisteredTransport(transportSessionId, transportSession)) {
            return failure(body, "The Memory List count requester is not authoritative.");
        }
        if (body == null) return failure(null, "Memory List summary body is required.");
        if (!PAGE_MAPPINGS_SOURCE.equals(kind)
                && positiveInteger(body, "botJobId") <= 0) {
            return failure(body, "A positive Bot Job ID is required.");
        }
        String requestId = string(body, "requestId");
        if (requestId.length() > MAX_REQUEST_ID_CHARACTERS) {
            return failure(body, "A valid Memory List request ID is required.");
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

    private String sourceKind(String transportSessionId) {
        if (ScannerWorkspaceSessions.BOT_JOB_TASKS.equals(transportSessionId)) {
            return BOT_JOB_SOURCE;
        }
        if (ScannerWorkspaceSessions.isOcrSourceScannerSession(transportSessionId)) {
            return PAGE_SCANNER_SOURCE;
        }
        if (DetachedWorkspaceSessions.PAGE_MAPPINGS_MANAGER.equals(transportSessionId)) {
            return PAGE_MAPPINGS_SOURCE;
        }
        if (ScannerWorkspaceSessions.COMPONENT_TASKS.equals(transportSessionId)) {
            return COMPONENT_SOURCE;
        }
        return "";
    }

    private boolean isElementSource(String sourceKind) {
        return PAGE_SCANNER_SOURCE.equals(sourceKind) || PAGE_MAPPINGS_SOURCE.equals(sourceKind);
    }

    private JsonObject withAuthorizedPageMappingsSource(
            JsonObject body,
            String transportSessionId,
            Session transportSession,
            Function<SourceOwner, JsonObject> operation) {
        try {
            return PageMappingsWorkspaceService.getInstance()
                    .withAuthorizedMemoryListSource(
                            body,
                            transportSessionId,
                            transportSession,
                            binding -> operation.apply(sourceOwner(binding)));
        } catch (IllegalArgumentException inactiveWorkspace) {
            return failure(
                    body,
                    firstNonBlank(
                            inactiveWorkspace.getMessage(),
                            "The Memory List source no longer matches its active workspace."));
        }
    }

    /**
     * Runs one detached Page Scanner Memory operation while its generation remains authoritative.
     *
     * <p>The coordinator monitor is deliberately acquired before {@code stateLock}. Page Mappings
     * retargeting uses the same coordinator-to-Memory order, so a scanner request cannot deadlock
     * with a retarget while each waits for the other's lifecycle lock.
     */
    private JsonObject withAuthorizedDetachedPageScannerSource(
            JsonObject body,
            String transportSessionId,
            Session transportSession,
            Function<SourceOwner, JsonObject> operation) {
        try {
            return pageScannerWorkspaceCoordinator.withAuthoritativeContext(
                    transportSessionId,
                    context -> {
                        if (!isRegisteredTransport(transportSessionId, transportSession)) {
                            throw new IllegalArgumentException(
                                    "The Page Scanner Memory transport is not authoritative.");
                        }
                        SourceOwner sourceOwner = new SourceOwner(
                                context.homeBankingId(),
                                context.botJobId(),
                                context.workspaceEpoch(),
                                "",
                                context.botJobName());
                        assertSourceOwnerAssertions(body, sourceOwner);
                        return operation.apply(sourceOwner);
                    });
        } catch (IllegalArgumentException inactiveWorkspace) {
            return failure(
                    body,
                    firstNonBlank(
                            inactiveWorkspace.getMessage(),
                            "The Memory List source no longer matches its active workspace."));
        }
    }

    private static SourceOwner sourceOwner(PageMappingsWorkspaceService.Binding owner) {
        return new SourceOwner(
                owner.homeBankingId(),
                owner.botJobId(),
                owner.workspaceEpoch(),
                owner.bindingEpoch(),
                owner.botJobName());
    }

    private static void requireUsableSourceOwner(SourceOwner owner) {
        if (owner == null || owner.homeBankingId <= 0 || owner.botJobId <= 0) {
            throw new IllegalArgumentException(
                    "The Memory List source has no authoritative owner.");
        }
    }

    private SourceOwner resolveSourceOwner(
            SourceOwner authorizedSourceOwner,
            JsonObject body,
            String transportSessionId,
            Session transportSession) {
        if (authorizedSourceOwner != null) return authorizedSourceOwner;
        if (PAGE_MAPPINGS_SOURCE.equals(sourceKind(transportSessionId))) {
            // Page Mappings must always arrive through bindingLock -> stateLock. Reauthorizing
            // here would invert that order and permit a delete/retarget deadlock.
            throw new IllegalArgumentException(
                    "The Page Mappings Memory owner is not authoritative.");
        }
        if (PAGE_SCANNER_SOURCE.equals(sourceKind(transportSessionId))
                && ScannerWorkspaceSessions.isPageScannerSession(transportSessionId)) {
            // Detached Page Scanner operations must enter through coordinator -> stateLock.
            // Resolving the coordinator here would invert that order and permit a retarget
            // deadlock.
            throw new IllegalArgumentException(
                    "The detached Page Scanner Memory owner is not authoritative.");
        }
        return sourceOwner(body, transportSessionId, transportSession);
    }

    private SourceOwner sourceOwner(
            JsonObject body, String transportSessionId, Session transportSession) {
        if (!isRegisteredTransport(transportSessionId, transportSession)) {
            throw new IllegalArgumentException(
                    "The Memory List source transport is no longer authoritative.");
        }
        String kind = sourceKind(transportSessionId);
        if (BOT_JOB_SOURCE.equals(kind)
                || COMPONENT_SOURCE.equals(kind)
                || PAGE_SCANNER_SOURCE.equals(kind)) {
            int claimedBotJobId = positiveInteger(body, "botJobId");
            if (claimedBotJobId <= 0) {
                claimedBotJobId = positiveInteger(snapshot(body), "botJobId");
            }
            BotJobDetailsWorkspaceRegistry.Snapshot context =
                    BotJobDetailsWorkspaceRegistry.getInstance().require(claimedBotJobId);
            SourceOwner owner = new SourceOwner(
                    context.homeBankingId(),
                    context.botJobId(),
                    context.workspaceEpoch(),
                    "",
                    context.name());
            assertSourceOwnerAssertions(body, owner);
            requireExactStaticSourceEpoch(body, owner);
            return owner;
        }
        throw new IllegalArgumentException(
                "The Memory List source has no authoritative owner.");
    }

    private static void assertSourceOwnerAssertions(JsonObject body, SourceOwner owner) {
        assertSourceOwnerAssertion(body, owner);
        assertSourceOwnerAssertion(snapshotOrNull(body), owner);
    }

    private static void assertSourceOwnerAssertion(JsonObject assertion, SourceOwner owner) {
        if (assertion == null) return;
        int botJobId = positiveInteger(assertion, "botJobId");
        int homeBankingId = positiveInteger(assertion, "homeBankingId");
        long workspaceEpoch = positiveLong(assertion, "workspaceEpoch");
        if ((botJobId > 0 && botJobId != owner.botJobId)
                || (homeBankingId > 0 && homeBankingId != owner.homeBankingId)
                || (workspaceEpoch > 0 && workspaceEpoch != owner.workspaceEpoch)) {
            throw new IllegalArgumentException(
                    "The Memory List source owner changed. Refresh this page.");
        }
    }

    private static void requireExactStaticSourceEpoch(JsonObject body, SourceOwner owner) {
        JsonObject nested = snapshotOrNull(body);
        boolean bodyHasEpoch = body != null && body.has("workspaceEpoch");
        boolean snapshotHasEpoch = nested != null && nested.has("workspaceEpoch");
        long bodyEpoch = positiveLong(body, "workspaceEpoch");
        long snapshotEpoch = positiveLong(nested, "workspaceEpoch");
        if ((bodyHasEpoch && bodyEpoch <= 0)
                || (snapshotHasEpoch && snapshotEpoch <= 0)
                || (!bodyHasEpoch && !snapshotHasEpoch)) {
            throw new IllegalArgumentException(
                    "The Memory List source workspace epoch is required.");
        }
        if ((bodyEpoch > 0 && bodyEpoch != owner.workspaceEpoch)
                || (snapshotEpoch > 0 && snapshotEpoch != owner.workspaceEpoch)) {
            throw new IllegalArgumentException(
                    "The Memory List source owner changed. Refresh this page.");
        }
    }

    private static JsonObject snapshotOrNull(JsonObject body) {
        if (body == null) return null;
        JsonElement nested = body.get("snapshot");
        return nested != null && nested.isJsonObject() ? nested.getAsJsonObject() : null;
    }

    private JsonObject validateComponentSnapshot(JsonObject request, JsonObject submittedSnapshot) {
        JsonArray items = array(submittedSnapshot, "items");
        if (items == null) return null;
        Set<String> sourceKeys = new HashSet<>();
        for (JsonElement value : items) {
            if (value == null || !value.isJsonObject()) {
                return failure(request, "A Components Memory List row is invalid.");
            }
            JsonObject item = value.getAsJsonObject();
            String sourceItemKey = string(item, "sourceItemKey");
            if (sourceItemKey.isBlank() || !sourceKeys.add(sourceItemKey)) {
                return failure(
                        request,
                        "Components Memory List rows require unique stable source keys.");
            }
            JsonObject payload = object(item, "payload");
            if (payload == null) {
                return failure(request, "A Components Memory List row has no typed payload.");
            }
            ComponentMemoryApplyService.ItemKind kind =
                    ComponentMemoryApplyService.ItemKind.componentKind(string(payload, "kind"));
            String revision = string(payload, "sourceRevision");
            if (revision.isEmpty()) revision = string(payload, "graphRevision");
            if (revision.isBlank() || revision.length() > 128) {
                return failure(
                        request,
                        "A Components row has no valid source graph revision. Refresh Components.");
            }
            if (kind == ComponentMemoryApplyService.ItemKind.COMPONENT_INSTRUCTION) {
                if (positiveInteger(payload, "componentInstructionId") <= 0
                        || positiveInteger(payload, "componentBlockId") <= 0) {
                    return failure(
                            request,
                            "A Components instruction row has invalid authoritative IDs.");
                }
            } else if (kind == ComponentMemoryApplyService.ItemKind.COMPONENT_BLOCK) {
                if (positiveInteger(payload, "componentBlockId") <= 0) {
                    return failure(
                            request, "A Components block row has an invalid authoritative ID.");
                }
            } else {
                return failure(
                        request,
                        "A Components row must be typed as INSTRUCTION or BLOCK.");
            }
        }
        return null;
    }

    private JsonObject snapshot(JsonObject body) {
        if (body == null) return null;
        JsonElement nested = body.get("snapshot");
        if (nested != null) return nested.isJsonObject() ? nested.getAsJsonObject() : null;
        JsonObject copy = body.deepCopy();
        copy.remove("requestId");
        return copy;
    }

    private void publishSnapshot(MemoryState state) {
        synchronized (stateLock) {
            if (current != state || !WebSocketSessionManager.isSessionOpen(WORKSPACE_SESSION_ID)) {
                return;
            }
            WebSocketSessionManager.getInstance().sendMessageJson(
                    state.homeBankingId,
                    WORKSPACE_SESSION_ID,
                    gson.toJson(snapshotResponse(state, "Memory List synchronized.")),
                    SNAPSHOT_OPERATION);
        }
    }

    private void publishSummaryChanged() {
        publishSummaryChanged(null);
    }

    /** Publishes only while an optional captured owner remains the current generation. */
    private void publishSummaryChanged(MemoryState expectedState) {
        synchronized (stateLock) {
            if (expectedState != null && current != expectedState) return;
            pruneSummarySubscribers();
            for (SummarySubscriber subscriber : summarySubscribers.values()) {
                if (!isRegisteredTransport(subscriber.sessionId, subscriber.transport)) continue;
                WebSocketSessionManager.sendMessageJson(
                        subscriber.homeBankingId,
                        subscriber.transport,
                        subscriber.sessionId,
                        gson.toJson(summaryResponse(
                                null,
                                subscriber,
                                current,
                                "Memory List count updated.")),
                        SUMMARY_CHANGED_OPERATION);
            }
        }
    }

    private void pruneSummarySubscribers() {
        summarySubscribers.entrySet().removeIf(entry -> {
            SummarySubscriber subscriber = entry.getValue();
            return !isRegisteredTransport(subscriber.sessionId, subscriber.transport);
        });
    }

    private JsonObject summaryResponse(
            JsonObject request,
            SummarySubscriber subscriber,
            MemoryState state,
            String message) {
        boolean matches = state != null
                && state.botJobId == subscriber.botJobId
                && state.homeBankingId == subscriber.homeBankingId
                && state.workspaceEpoch == subscriber.workspaceEpoch;
        JsonObject response = baseResponse(request, true, message);
        response.addProperty("sourceSessionId", subscriber.sessionId);
        response.addProperty("sourceKind", subscriber.kind);
        response.addProperty("botJobId", subscriber.botJobId);
        response.addProperty("homeBankingId", subscriber.homeBankingId);
        response.addProperty("workspaceEpoch", subscriber.workspaceEpoch);
        response.addProperty("itemCount", matches ? state.items.size() : 0);
        response.addProperty("revision", matches ? state.revision : 0);
        response.addProperty("ownerEpoch", matches ? state.ownerEpoch : "");
        return response;
    }

    private void publishFocus(MemoryState state) {
        synchronized (stateLock) {
            if (current != state || !WebSocketSessionManager.isSessionOpen(WORKSPACE_SESSION_ID)) {
                return;
            }
            JsonObject focus = new JsonObject();
            focus.addProperty("sessionId", WORKSPACE_SESSION_ID);
            focus.addProperty("botJobId", state.botJobId);
            WebSocketSessionManager.getInstance().sendMessageJson(
                    state.homeBankingId,
                    WORKSPACE_SESSION_ID,
                    gson.toJson(focus),
                    FOCUS_OPERATION);
        }
    }

    private void publishAndFocusCurrent(MemoryState state, String capability) {
        synchronized (stateLock) {
            if (!matchesOwnerGeneration(state, capability)
                    || !WebSocketSessionManager.isSessionOpen(WORKSPACE_SESSION_ID)) {
                return;
            }
            publishSnapshot(state);
            PagesOpenWorkspaceService.getInstance()
                    .focusSession(
                            WORKSPACE_SESSION_ID,
                            "Memory button requested the existing Memory List workspace.");
            publishFocus(state);
        }
    }

    private boolean matchesOwnerGeneration(MemoryState state, String capability) {
        return current == state
                && capability != null
                && constantTimeEquals(state.windowCapability, capability);
    }

    private JsonObject snapshotResponse(MemoryState state, String message) {
        JsonObject snapshot = combinedSnapshot(state);
        JsonObject response = new JsonObject();
        response.addProperty("ok", true);
        response.addProperty("message", message);
        response.addProperty("sessionId", WORKSPACE_SESSION_ID);
        response.addProperty("sourceSessionId", sourceLabel(state));
        response.addProperty("botJobId", state.botJobId);
        response.addProperty("homeBankingId", state.homeBankingId);
        response.addProperty("workspaceEpoch", state.workspaceEpoch);
        response.addProperty("ownerEpoch", state.ownerEpoch);
        response.addProperty("revision", state.revision);
        response.addProperty("itemCount", state.items.size());
        for (var field : snapshot.entrySet()) {
            if (!response.has(field.getKey())) response.add(field.getKey(), field.getValue().deepCopy());
        }
        response.add("snapshot", snapshot);
        return response;
    }

    private JsonObject combinedSnapshot(MemoryState state) {
        JsonObject snapshot = new JsonObject();
        snapshot.addProperty("ownerEpoch", state.ownerEpoch);
        snapshot.addProperty("revision", state.revision);
        snapshot.addProperty("sourceKind", sourceLabel(state));
        snapshot.addProperty("homeBankingId", state.homeBankingId);
        snapshot.addProperty("botJobId", state.botJobId);
        snapshot.addProperty("workspaceEpoch", state.workspaceEpoch);
        snapshot.addProperty("botJobName", state.botJobName);
        snapshot.addProperty("itemCount", state.items.size());
        JsonArray items = new JsonArray();
        for (String key : state.order) {
            AggregatedItem item = state.items.get(key);
            if (item != null) items.add(item.presentation.deepCopy());
        }
        snapshot.add("items", items);
        JsonArray blocks = new JsonArray();
        state.blocks.values().forEach(block -> blocks.add(block.deepCopy()));
        snapshot.add("blocks", blocks);
        if (state.targetBlockId == null) {
            snapshot.add("targetBlockId", com.google.gson.JsonNull.INSTANCE);
        } else {
            snapshot.addProperty("targetBlockId", state.targetBlockId);
        }
        snapshot.addProperty(
                "emptyMessage",
                "Click \"+\" on Bot Job instructions, Components, or Page Scanner elements to add them here.");
        boolean busy = state.sources.values().stream()
                .map(source -> source.snapshot)
                .anyMatch(source -> booleanValue(source, "busy"));
        snapshot.addProperty("busy", busy);
        Set<Integer> selectedComponentBlocks = state.items.values().stream()
                .filter(item -> COMPONENT_SOURCE.equals(item.sourceKind))
                .filter(item -> "BLOCK".equalsIgnoreCase(string(item.payload, "kind")))
                .map(item -> positiveInteger(item.payload, "componentBlockId"))
                .filter(blockId -> blockId > 0)
                .collect(java.util.stream.Collectors.toSet());
        boolean targetRequired = state.items.values().stream().anyMatch(item -> {
            if (!COMPONENT_SOURCE.equals(item.sourceKind)) return true;
            if ("BLOCK".equalsIgnoreCase(string(item.payload, "kind"))) return false;
            return !selectedComponentBlocks.contains(
                    positiveInteger(item.payload, "componentBlockId"));
        });
        boolean validTarget = state.targetBlockId != null
                && state.blocks.containsKey(state.targetBlockId);
        snapshot.addProperty(
                "canApply",
                !busy && !state.items.isEmpty() && (!targetRequired || validTarget));
        snapshot.addProperty(
                "status",
                state.items.size()
                        + " item"
                        + (state.items.size() == 1 ? "" : "s")
                        + " in global Memory List");
        return snapshot;
    }

    private String sourceLabel(MemoryState state) {
        if (state.sources.size() > 1) return MIXED_SOURCE;
        return state.sources.keySet().stream().findFirst().orElse(BOT_JOB_SOURCE);
    }

    private JsonObject success(JsonObject request, MemoryState state, String message) {
        JsonObject response = baseResponse(request, true, message);
        response.addProperty("botJobId", state.botJobId);
        response.addProperty("homeBankingId", state.homeBankingId);
        response.addProperty("workspaceEpoch", state.workspaceEpoch);
        response.addProperty("ownerEpoch", state.ownerEpoch);
        response.addProperty("revision", state.revision);
        return response;
    }

    private String completedApplyFingerprint(String command, JsonObject payload) {
        return canonicalCommand(command) + ":" + gson.toJson(
                payload == null ? new JsonObject() : payload);
    }

    private JsonObject completedApplyResponse(
            MemoryState state,
            String requestId,
            String requestFingerprint,
            JsonObject request) {
        CompletedApplyResponse completed = state.completedApplyRequests.get(requestId);
        if (completed == null) return null;
        if (!completed.fingerprint().equals(requestFingerprint)) {
            JsonObject conflict = failure(
                    request,
                    "This request ID was already used for different Memory List data.");
            conflict.addProperty("committed", false);
            conflict.addProperty("synchronized", false);
            return conflict;
        }
        JsonObject duplicate = completed.response().deepCopy();
        duplicate.addProperty("duplicate", true);
        return duplicate;
    }

    private void rememberCompletedApply(
            MemoryState state,
            String requestId,
            String requestFingerprint,
            JsonObject response) {
        if (requestId == null
                || requestId.isBlank()
                || requestFingerprint == null
                || response == null) {
            return;
        }
        state.completedApplyRequests.put(
                requestId,
                new CompletedApplyResponse(requestFingerprint, response.deepCopy()));
        while (state.completedApplyRequests.size() > 256) {
            String eldest = state.completedApplyRequests.keySet().iterator().next();
            state.completedApplyRequests.remove(eldest);
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return "Memory List operation failed.";
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "Memory List operation failed.";
    }

    private void copyRequestId(JsonObject response, JsonObject request) {
        String requestId = string(request, "requestId");
        if (!requestId.isEmpty() && requestId.length() <= MAX_REQUEST_ID_CHARACTERS) {
            response.addProperty("requestId", requestId);
        }
    }

    private String canonicalCommand(String command) {
        if (command == null) return "";
        String normalized = command.trim().toUpperCase(Locale.ROOT);
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
        return baseResponse(request, false, message == null || message.isBlank()
                ? "Memory List operation failed."
                : message);
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

    private static JsonObject object(JsonObject source, String name) {
        if (source == null || !source.has(name)) return null;
        JsonElement value = source.get(name);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static JsonArray array(JsonObject source, String name) {
        if (source == null || !source.has(name)) return null;
        JsonElement value = source.get(name);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
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

    private static long positiveLong(JsonObject source, String name) {
        if (source == null || !source.has(name) || source.get(name).isJsonNull()) return -1L;
        try {
            long value = source.get(name).getAsLong();
            return value > 0 ? value : -1L;
        } catch (RuntimeException ignored) {
            return -1L;
        }
    }

    private static boolean booleanValue(JsonObject source, String name) {
        if (source == null || !source.has(name) || source.get(name).isJsonNull()) return false;
        try {
            return source.get(name).getAsBoolean();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static String newWindowCapability() {
        byte[] capability = new byte[WINDOW_CAPABILITY_BYTES];
        WINDOW_CAPABILITY_RANDOM.nextBytes(capability);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(capability);
    }

    static String windowCapabilityForOwnerTransition(
            String previousCapability, boolean alreadyOpen, boolean validPending) {
        if (previousCapability != null
                && !previousCapability.isBlank()
                && (alreadyOpen || validPending)) {
            return previousCapability;
        }
        return newWindowCapability();
    }

    private static boolean constantTimeEquals(String expected, String asserted) {
        return expected != null
                && MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.UTF_8),
                        asserted.getBytes(StandardCharsets.UTF_8));
    }

    private static String requestParameter(Session session, String field) {
        if (session == null || field == null) return "";
        try {
            List<String> values = session.getRequestParameterMap().get(field);
            return values == null || values.isEmpty() || values.get(0) == null
                    ? ""
                    : values.get(0).trim();
        } catch (RuntimeException invalidRequest) {
            return "";
        }
    }

    private static final class MemoryState {
        private final int botJobId;
        private int homeBankingId;
        private final long workspaceEpoch;
        private final String ownerEpoch;
        private String windowCapability;
        private long revision;
        private String botJobName = "";
        private Integer targetBlockId;
        private final Map<String, SourceState> sources = new LinkedHashMap<>();
        private final Map<String, AggregatedItem> items = new LinkedHashMap<>();
        private final List<String> order = new ArrayList<>();
        private final Set<String> suppressedKeys = new HashSet<>();
        private final Map<Integer, JsonObject> persistedBlocks = new LinkedHashMap<>();
        private final Map<Integer, JsonObject> blocks = new LinkedHashMap<>();
        private final LinkedHashMap<String, CompletedApplyResponse> completedApplyRequests =
                new LinkedHashMap<>(16, 0.75f, true);

        private MemoryState(int botJobId, int homeBankingId, String ownerEpoch) {
            this(botJobId, homeBankingId, 0, ownerEpoch);
        }

        private MemoryState(
                int botJobId,
                int homeBankingId,
                long workspaceEpoch,
                String ownerEpoch) {
            this.botJobId = botJobId;
            this.homeBankingId = homeBankingId;
            this.workspaceEpoch = workspaceEpoch;
            this.ownerEpoch = ownerEpoch;
            this.windowCapability = newWindowCapability();
        }
    }

    private record CompletedApplyResponse(String fingerprint, JsonObject response) {}

    private record SummarySubscriber(
            String kind,
            String sessionId,
            Session transport,
            int botJobId,
            int homeBankingId,
            long workspaceEpoch) {}

    private record SourceOwner(
            int homeBankingId,
            int botJobId,
            long workspaceEpoch,
            String bindingEpoch,
            String botJobName) {}

    @FunctionalInterface
    interface DetachedWindowLauncher {
        boolean open(int botJobId, String windowCapability);
    }

    private static final class SourceState {
        private final String kind;
        private final String sessionId;
        private final Session transport;
        private final JsonObject snapshot;
        private final Set<String> itemKeys;

        private SourceState(
                String kind,
                String sessionId,
                Session transport,
                JsonObject snapshot,
                Set<String> itemKeys) {
            this.kind = kind;
            this.sessionId = sessionId;
            this.transport = transport;
            this.snapshot = snapshot;
            this.itemKeys = Set.copyOf(itemKeys);
        }
    }

    private static final class AggregatedItem {
        private final String globalKey;
        private final String sourceKind;
        private final String sourceItemKey;
        private final JsonObject presentation;
        private final JsonObject payload;

        private AggregatedItem(
                String globalKey,
                String sourceKind,
                String sourceItemKey,
                JsonObject presentation,
                JsonObject payload) {
            this.globalKey = globalKey;
            this.sourceKind = sourceKind;
            this.sourceItemKey = sourceItemKey;
            this.presentation = presentation;
            this.payload = payload;
        }
    }
}

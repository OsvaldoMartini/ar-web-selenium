package com.allinweb.ch.socket;

import com.allinweb.ch.facade.BlockCreationService;
import com.allinweb.ch.facade.BotJobDetailsWorkspaceRegistry;
import com.allinweb.ch.facade.InstructionMoveValidator;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.facade.PreScanApplyService;
import com.allinweb.ch.facade.ScannerBotJobTasksPublisher;
import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.DetachedWorkspaceSessions;
import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.allinweb.ch.model.SplitDTO;
import com.allinweb.ch.model.UpdatedRow;
import com.allinweb.ch.util.ErrorMessage;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.websocket.Session;

/**
 * Backend-owned state for the one detached Memory List.
 *
 * <p>Bot Job Details and Page Scanner contribute independently to one ordered list. A contribution
 * updates only its own rows; it can no longer replace rows supplied by the other page. Commands
 * that mutate the mixed list are performed here so the list remains usable even when one source
 * window is temporarily behind or has been closed.
 */
public final class MemoryListWorkspaceService {

    public static final String WORKSPACE_SESSION_ID = DetachedWorkspaceSessions.MEMORY_LIST_MANAGER;
    public static final String SNAPSHOT_OPERATION = "memoryList.snapshot";
    public static final String FOCUS_OPERATION = "memoryList.focus";
    public static final String SOURCE_COMMAND_OPERATION = "memoryList.command";

    private static final String BOT_JOB_SOURCE = "BOT_JOB";
    private static final String PAGE_SCANNER_SOURCE = "PAGE_SCANNER";
    private static final String MIXED_SOURCE = "MIXED";
    private static final int MAX_SNAPSHOT_CHARACTERS = 1_000_000;
    private static final int MAX_COMMAND_PAYLOAD_CHARACTERS = 128_000;
    private static final int MAX_COLLECTION_ITEMS = 1_000;
    private static final int MAX_REQUEST_ID_CHARACTERS = 160;
    private static final int MAX_COMMAND_NAME_CHARACTERS = 64;
    private static final long LAUNCH_PENDING_NANOS =
            java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
    private static final Set<String> COMMANDS = Set.of(
            "REMOVE",
            "CLEAR",
            "SELECT_TARGET_BLOCK",
            "APPLY",
            "CREATE_BLOCK",
            "REORDER");

    private static final MemoryListWorkspaceService INSTANCE = new MemoryListWorkspaceService();

    private final Object stateLock = new Object();
    private final Gson gson = new Gson();
    private final PerformDataBase performDataBase = PerformDataBase.getInstance();
    private final PerformLists performLists = PerformLists.getInstance();
    private final PreScanApplyService preScanApplyService = PreScanApplyService.getInstance();
    private final InstructionMoveValidator instructionMoveValidator = new InstructionMoveValidator();
    private final BlockCreationService blockCreationService = BlockCreationService.getInstance();
    private final ScannerBotJobTasksPublisher botJobTasksPublisher =
            ScannerBotJobTasksPublisher.getInstance();
    private MemoryState current;
    private boolean launchPending;
    private long launchPendingSince;

    private MemoryListWorkspaceService() {}

    public static MemoryListWorkspaceService getInstance() {
        return INSTANCE;
    }

    /** Adds or updates one source contribution and opens/focuses the fixed detached window. */
    public JsonObject open(JsonObject body, String transportSessionId, Session transportSession) {
        JsonObject validation = validateSourceRequest(body, transportSessionId, transportSession);
        if (validation != null) return validation;

        MemoryState state;
        boolean alreadyOpen = WebSocketSessionManager.isSessionOpen(WORKSPACE_SESSION_ID);
        boolean launchRequired;
        long now = System.nanoTime();
        synchronized (stateLock) {
            int botJobId = positiveInteger(body, "botJobId");
            int homeBankingId = sourceHomeBankingId(body);
            if (current == null || current.botJobId != botJobId) {
                current = new MemoryState(botJobId, homeBankingId, UUID.randomUUID().toString());
            } else if (homeBankingId > 0) {
                current.homeBankingId = homeBankingId;
            }
            upsertSource(current, body, transportSessionId, transportSession);
            state = current;
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
                .openDetachedWorkspaceDesktopShell(WORKSPACE_SESSION_ID, state.botJobId);
        if (!launched) {
            synchronized (stateLock) {
                launchPending = false;
            }
            return failure(body, "Memory List workspace could not be opened.");
        }

        if (alreadyOpen) {
            publishSnapshot(state);
            publishFocus(state);
        }
        boolean pendingReuse = !alreadyOpen && !launchRequired;
        JsonObject response = snapshotResponse(
                state,
                alreadyOpen
                        ? "Memory List workspace already open."
                        : pendingReuse
                                ? "Memory List workspace is opening."
                                : "Memory List workspace opened.");
        copyRequestId(response, body);
        response.addProperty("alreadyOpen", alreadyOpen);
        response.addProperty("reused", alreadyOpen || pendingReuse);
        return response;
    }

    /** Upserts only the calling source; rows from the other source remain in the global list. */
    public JsonObject sync(JsonObject body, String transportSessionId, Session transportSession) {
        JsonObject validation = validateSourceRequest(body, transportSessionId, transportSession);
        if (validation != null) return validation;

        MemoryState state;
        synchronized (stateLock) {
            state = current;
            if (state == null) {
                return failure(body, "Open the Memory List before synchronizing it.");
            }
            String ownerEpoch = string(body, "ownerEpoch");
            if (state.botJobId != positiveInteger(body, "botJobId")
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
            upsertSource(state, body, transportSessionId, transportSession);
        }
        publishSnapshot(state);
        JsonObject response = snapshotResponse(state, "Memory List synchronized.");
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
                case "APPLY" -> apply(state, payload, body, requestId);
                default -> failure(body, "Unsupported Memory List command.");
            };
        }
        publishSnapshot(state);
        return response;
    }

    private JsonObject reorder(MemoryState state, JsonObject payload, JsonObject request) {
        JsonArray requested = array(payload, "orderedItemKeys");
        if (requested == null || requested.size() != state.order.size()) {
            return failure(request, "Memory List order is incomplete. Refresh and try again.");
        }
        List<String> next = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        for (JsonElement value : requested) {
            String key;
            try {
                key = value.getAsString();
            } catch (RuntimeException invalid) {
                return failure(request, "Memory List order contains an invalid row.");
            }
            if (!state.items.containsKey(key) || !unique.add(key)) {
                return failure(request, "Memory List order contains a missing or duplicate row.");
            }
            next.add(key);
        }
        state.order.clear();
        state.order.addAll(next);
        state.revision++;
        return success(request, state, "Memory List order updated.");
    }

    private JsonObject remove(
            MemoryState state, JsonObject payload, JsonObject request, String requestId) {
        String globalKey = string(payload, "itemKey");
        AggregatedItem item = state.items.get(globalKey);
        if (item == null) return failure(request, "Memory List row is no longer available.");
        suppressAndRemove(state, item);
        JsonObject sourcePayload = new JsonObject();
        sourcePayload.addProperty("itemKey", item.sourceItemKey);
        sourcePayload.addProperty("sourceItemKey", item.sourceItemKey);
        forward(state, item.sourceKind, requestId, "REMOVE", sourcePayload);
        state.revision++;
        return success(request, state, "Memory List row removed.");
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
        reloadBlocks(state);
        state.targetBlockId = result.newBlockId();
        state.revision++;

        JsonObject selected = new JsonObject();
        selected.addProperty("blockId", state.targetBlockId);
        for (SourceState source : state.sources.values()) {
            forward(state, source.kind, requestId, "SELECT_TARGET_BLOCK", selected);
        }
        boolean synchronizedSnapshot = publishCreatedBlockSnapshot(
                state,
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

    private boolean publishCreatedBlockSnapshot(
            MemoryState state,
            Integer createdBlockId,
            String createdBlockName,
            Integer createdBlockOrderNumber) {
        try {
            List<InstructionLoad> instructions = performLists.getListBotJob().isEmpty()
                    ? List.of()
                    : performLists.buildJsonViewData(performLists.getListBotJob());
            JsonObject update = new JsonObject();
            update.add("instructions", gson.toJsonTree(instructions));
            JsonArray blocks = new JsonArray();
            state.blocks.values().forEach(block -> blocks.add(block.deepCopy()));
            update.add("blocks", blocks);
            update.addProperty("botJobId", state.botJobId);
            update.addProperty("createdBlockId", createdBlockId);
            update.addProperty("createdBlockName", createdBlockName);
            update.addProperty("createdBlockOrderNumber", createdBlockOrderNumber);
            InstructionRealtimePublisher.getInstance()
                    .publishSerializedSnapshot(
                            state.homeBankingId,
                            ScannerWorkspaceSessions.BOT_JOB_TASKS,
                            gson.toJson(update));
            return true;
        } catch (RuntimeException refreshFailure) {
            return false;
        }
    }

    private JsonObject apply(
            MemoryState state, JsonObject payload, JsonObject request, String requestId) {
        if (!isActiveBotJob(state.botJobId)) {
            return failure(
                    request,
                    "Memory List belongs to a Bot Job that is no longer open. Add the rows again.");
        }
        int requestedTarget = positiveInteger(payload, "targetBlockId");
        int targetBlockId = requestedTarget > 0
                ? requestedTarget
                : state.targetBlockId == null ? -1 : state.targetBlockId;
        if (targetBlockId <= 0 || !state.blocks.containsKey(targetBlockId)) {
            return failure(request, "Select a valid target block before applying.");
        }

        List<AggregatedItem> applicable = state.order.stream()
                .map(state.items::get)
                .filter(Objects::nonNull)
                .filter(item -> !PAGE_SCANNER_SOURCE.equals(item.sourceKind)
                        || !item.presentation.has("active")
                        || item.presentation.get("active").isJsonNull()
                        || item.presentation.get("active").getAsBoolean())
                .toList();
        if (applicable.isEmpty()) return failure(request, "No active Memory List rows are available.");

        ErrorMessage loadError =
                performDataBase.loadInstructions(state.botJobId, -1, -1, "instruction");
        if (loadError != null) return failure(request, loadError.getErrorMessage());
        List<InstructionLoad> currentRows = new ArrayList<>(performLists.getListInstruction());
        Map<Integer, InstructionLoad> currentById = new HashMap<>();
        for (InstructionLoad row : currentRows) {
            if (row != null && row.getId() != null) currentById.put(row.getId(), row);
        }

        List<InstructionLoad> scannerInstructions = new ArrayList<>();
        Map<String, Integer> orderedIds = new LinkedHashMap<>();
        int dummyId = -1;
        int nextTargetOrder = currentRows.stream()
                        .filter(row -> Objects.equals(row.getBlockId(), targetBlockId))
                        .map(InstructionLoad::getInstructionOrderNumber)
                        .filter(Objects::nonNull)
                        .max(Integer::compareTo)
                        .orElse(0)
                + 1;
        for (AggregatedItem item : applicable) {
            if (BOT_JOB_SOURCE.equals(item.sourceKind)) {
                int instructionId = positiveInteger(item.payload, "instructionId");
                if (instructionId <= 0) instructionId = positiveInteger(item.presentation, "sourceItemKey");
                if (!currentById.containsKey(instructionId)) {
                    return failure(request, "An instruction in Memory List no longer exists. Refresh and try again.");
                }
                orderedIds.put(item.globalKey, instructionId);
                continue;
            }
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
            InstructionLoad instruction = preScanApplyService.buildMemoryListInstruction(
                    element, state.botJobId, targetBlockId, nextTargetOrder++);
            if (instruction == null) {
                return failure(request, "A Page Scanner row could not be converted to an instruction.");
            }
            instruction.setId(dummyId);
            scannerInstructions.add(instruction);
            orderedIds.put(item.globalKey, dummyId);
            dummyId--;
        }

        List<InstructionLoad> proposedRows = new ArrayList<>(currentRows);
        proposedRows.addAll(scannerInstructions);
        List<UpdatedRow> proposedLayout =
                buildFinalLayout(proposedRows, targetBlockId, new ArrayList<>(orderedIds.values()));
        boolean layoutChanged = layoutChanged(proposedRows, proposedLayout);
        if (layoutChanged) {
            String graphError = instructionMoveValidator.validate(proposedRows, proposedLayout);
            if (graphError != null) {
                return failure(request, "Memory List order creates an invalid instruction graph: " + graphError);
            }
        }

        Map<Integer, Integer> generatedIds = new HashMap<>();
        if (!scannerInstructions.isEmpty()) {
            List<Integer> dummyIds = scannerInstructions.stream().map(InstructionLoad::getId).toList();
            PerformDataBase.AtomicInstructionInsertResult insertion =
                    performDataBase.insertInstructionsAndReferencesAtomic(
                            scannerInstructions, state.botJobId, targetBlockId);
            if (insertion.error() != null) {
                return failure(request, insertion.error().getErrorMessage());
            }
            if (insertion.instructionIds().size() != dummyIds.size()) {
                return failure(request, "Inserted instruction count does not match Memory List.");
            }
            for (int index = 0; index < dummyIds.size(); index++) {
                generatedIds.put(dummyIds.get(index), insertion.instructionIds().get(index));
            }
        }

        if (layoutChanged) {
            List<UpdatedRow> persistedLayout = proposedLayout.stream()
                    .map(row -> copyWithGeneratedId(row, generatedIds))
                    .toList();
            ErrorMessage moveError =
                    performDataBase.updateMoveRowsOrder("block", state.botJobId, persistedLayout);
            if (moveError != null) {
                return failure(
                        request,
                        "Instructions were prepared, but their final order could not be saved: "
                                + moveError.getErrorMessage());
            }
        }

        ErrorMessage refreshError =
                botJobTasksPublisher.publish(state.homeBankingId, state.botJobId);
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
        state.targetBlockId = targetBlockId;
        state.revision++;
        reloadBlocks(state);

        JsonObject response = success(
                request,
                state,
                refreshError == null
                        ? "Memory List applied."
                        : "Memory List applied; Bot Job Details refresh is pending.");
        response.addProperty("committed", true);
        response.addProperty("synchronized", refreshError == null);
        response.addProperty("appliedCount", appliedKeys.size());
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

    private List<UpdatedRow> buildFinalLayout(
            List<InstructionLoad> rows, int targetBlockId, List<Integer> orderedSelectedIds) {
        Set<Integer> selected = new HashSet<>(orderedSelectedIds);
        Map<Integer, List<InstructionLoad>> blocks = new LinkedHashMap<>();
        rows.stream()
                .filter(row -> row != null && row.getId() != null && row.getBlockId() != null)
                .sorted(Comparator.comparing(
                                (InstructionLoad row) -> row.getBlockId() == null ? Integer.MAX_VALUE : row.getBlockId())
                        .thenComparing(row -> row.getInstructionOrderNumber() == null
                                ? Integer.MAX_VALUE
                                : row.getInstructionOrderNumber()))
                .forEach(row -> blocks.computeIfAbsent(row.getBlockId(), ignored -> new ArrayList<>()).add(row));
        Map<Integer, InstructionLoad> byId = new HashMap<>();
        rows.forEach(row -> {
            if (row != null && row.getId() != null) byId.put(row.getId(), row);
        });
        blocks.values().forEach(blockRows -> blockRows.removeIf(row -> selected.contains(row.getId())));
        List<InstructionLoad> target =
                blocks.computeIfAbsent(targetBlockId, ignored -> new ArrayList<>());
        for (Integer instructionId : orderedSelectedIds) {
            InstructionLoad row = byId.get(instructionId);
            if (row != null) target.add(row);
        }

        List<UpdatedRow> result = new ArrayList<>();
        for (var block : blocks.entrySet()) {
            List<InstructionLoad> blockRows = block.getValue();
            for (int index = 0; index < blockRows.size(); index++) {
                UpdatedRow update = new UpdatedRow();
                update.setInstructionId(blockRows.get(index).getId());
                update.setBlockId(block.getKey());
                update.setInstructionOrderNumber(index + 1);
                result.add(update);
            }
        }
        return result;
    }

    private boolean layoutChanged(List<InstructionLoad> rows, List<UpdatedRow> updates) {
        Map<Integer, InstructionLoad> currentById = new HashMap<>();
        rows.forEach(row -> {
            if (row != null && row.getId() != null) currentById.put(row.getId(), row);
        });
        for (UpdatedRow update : updates) {
            InstructionLoad currentRow = currentById.get(update.getInstructionId());
            if (currentRow == null
                    || !Objects.equals(currentRow.getBlockId(), update.getBlockId())
                    || !Objects.equals(
                            currentRow.getInstructionOrderNumber(),
                            update.getInstructionOrderNumber())) {
                return true;
            }
        }
        return false;
    }

    private UpdatedRow copyWithGeneratedId(UpdatedRow source, Map<Integer, Integer> generatedIds) {
        UpdatedRow copy = new UpdatedRow();
        copy.setInstructionId(generatedIds.getOrDefault(source.getInstructionId(), source.getInstructionId()));
        copy.setInstructionOrderNumber(source.getInstructionOrderNumber());
        copy.setBlockId(source.getBlockId());
        copy.setBlockOrderNumber(source.getBlockOrderNumber());
        copy.setParentBlockId(source.getParentBlockId());
        copy.setParentId(source.getParentId());
        return copy;
    }

    private void suppressAndRemove(MemoryState state, AggregatedItem item) {
        state.suppressedKeys.add(item.globalKey);
        state.items.remove(item.globalKey);
        state.order.remove(item.globalKey);
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
        forwarded.add("payload", payload.deepCopy());
        WebSocketSessionManager.sendMessageJson(
                state.homeBankingId,
                registered,
                source.sessionId,
                gson.toJson(forwarded),
                SOURCE_COMMAND_OPERATION);
    }

    private void upsertSource(
            MemoryState state,
            JsonObject body,
            String transportSessionId,
            Session transportSession) {
        JsonObject submittedSnapshot = snapshot(body).deepCopy();
        String kind = sourceKind(transportSessionId);
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

    private void reloadBlocks(MemoryState state) {
        ErrorMessage error = performDataBase.loadBlocks(state.botJobId, "", "block");
        if (error != null) return;
        state.persistedBlocks.clear();
        for (BlockLoadDTO block : performLists.getListBlock()) {
            if (block == null || block.getId() == null || block.getId() <= 0) continue;
            JsonObject option = new JsonObject();
            option.addProperty("blockId", block.getId());
            option.addProperty("blockName", Objects.toString(block.getName(), ""));
            option.addProperty(
                    "blockOrderNumber",
                    block.getBlockOrderNumber() == null ? 0 : block.getBlockOrderNumber());
            state.persistedBlocks.put(block.getId(), option);
        }
        rebuildBlocks(state);
    }

    private JsonObject validateSourceRequest(
            JsonObject body, String transportSessionId, Session transportSession) {
        String sourceKind = sourceKind(transportSessionId);
        if (sourceKind.isEmpty()) {
            return failure(body, "Only Bot Job Details or Page Scanner can update the Memory List.");
        }
        if (!isRegisteredTransport(transportSessionId, transportSession)) {
            return failure(body, "The Memory List source is not authoritative.");
        }
        if (body == null) return failure(null, "Memory List body is required.");
        if (positiveInteger(body, "botJobId") <= 0) {
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
        return "";
    }

    private int sourceHomeBankingId(JsonObject body) {
        int value = positiveInteger(body, "homeBankingId");
        if (value > 0) return value;
        JsonObject nested = snapshot(body);
        return positiveInteger(nested, "homeBankingId");
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

    private JsonObject snapshotResponse(MemoryState state, String message) {
        JsonObject snapshot = combinedSnapshot(state);
        JsonObject response = new JsonObject();
        response.addProperty("ok", true);
        response.addProperty("message", message);
        response.addProperty("sessionId", WORKSPACE_SESSION_ID);
        response.addProperty("sourceSessionId", sourceLabel(state));
        response.addProperty("botJobId", state.botJobId);
        response.addProperty("homeBankingId", state.homeBankingId);
        response.addProperty("ownerEpoch", state.ownerEpoch);
        response.addProperty("revision", state.revision);
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
        snapshot.addProperty("botJobName", state.botJobName);
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
                "Click \"+\" on Bot Job instructions or Page Scanner elements to add them here.");
        boolean busy = state.sources.values().stream()
                .map(source -> source.snapshot)
                .anyMatch(source -> booleanValue(source, "busy"));
        snapshot.addProperty("busy", busy);
        snapshot.addProperty(
                "canApply",
                !busy
                        && !state.items.isEmpty()
                        && state.targetBlockId != null
                        && state.blocks.containsKey(state.targetBlockId));
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
        response.addProperty("ownerEpoch", state.ownerEpoch);
        response.addProperty("revision", state.revision);
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

    private static boolean booleanValue(JsonObject source, String name) {
        if (source == null || !source.has(name) || source.get(name).isJsonNull()) return false;
        try {
            return source.get(name).getAsBoolean();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static final class MemoryState {
        private final int botJobId;
        private int homeBankingId;
        private final String ownerEpoch;
        private long revision;
        private String botJobName = "";
        private Integer targetBlockId;
        private final Map<String, SourceState> sources = new LinkedHashMap<>();
        private final Map<String, AggregatedItem> items = new LinkedHashMap<>();
        private final List<String> order = new ArrayList<>();
        private final Set<String> suppressedKeys = new HashSet<>();
        private final Map<Integer, JsonObject> persistedBlocks = new LinkedHashMap<>();
        private final Map<Integer, JsonObject> blocks = new LinkedHashMap<>();

        private MemoryState(int botJobId, int homeBankingId, String ownerEpoch) {
            this.botJobId = botJobId;
            this.homeBankingId = homeBankingId;
            this.ownerEpoch = ownerEpoch;
        }
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

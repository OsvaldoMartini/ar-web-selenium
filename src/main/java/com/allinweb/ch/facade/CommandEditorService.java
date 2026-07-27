package com.allinweb.ch.facade;

import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.BlockDetailsDTO;
import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.BlockOrderDetailDTO;
import com.allinweb.ch.model.DetailsDTO;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.allinweb.ch.model.SplitDTO;
import com.allinweb.ch.model.UpdatedRow;
import com.allinweb.ch.socket.WebSocketSessionManager;
import com.allinweb.ch.util.ErrorMessage;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/** Pane-free backend for the React instruction command panel. */
@Slf4j
public final class CommandEditorService {

    private static final CommandEditorService INSTANCE = new CommandEditorService();
    private final PerformDataBase database = PerformDataBase.getInstance();
    private final PerformDBEngine engine = PerformDBEngine.getInstance();
    private final PerformLists lists = PerformLists.getInstance();
    private final WebSocketSessionManager sessions = WebSocketSessionManager.getInstance();
    private final Gson gson = new Gson();
    private final CommandOperationCodec operationCodec = new CommandOperationCodec();
    private final CommandCapabilityService capabilityService = new CommandCapabilityService();
    private final ConditionalGraphValidator conditionalValidator = new ConditionalGraphValidator();
    private final CompletedRequestCache completedRequests = new CompletedRequestCache(256);
    private final Map<String, Boolean> completedSplitRequests = new LinkedHashMap<>();
    private final InstructionSplitValidator splitValidator = new InstructionSplitValidator();
    private final ConditionalBranchService conditionalBranchService = new ConditionalBranchService();
    private final LoopGroupService loopGroupService = new LoopGroupService();
    private final InstructionMoveGroupService moveGroupService = new InstructionMoveGroupService();
    private final InstructionGraphRevisionService revisionService = new InstructionGraphRevisionService();
    private final InstructionDeleteImpactService deleteImpactService = new InstructionDeleteImpactService();

    private CommandEditorService() {}

    public static CommandEditorService getInstance() {
        return INSTANCE;
    }

    /**
     * Resolves one command-editor anchor from the authoritative instruction workspace.
     *
     * <p>The detached command editor uses this boundary before it opens and again before every
     * operation. Client-supplied block and instruction metadata is therefore never treated as an
     * ownership assertion. Bot Job rows are scoped by {@code bot_job_id}; reusable Component rows
     * are scoped independently by {@code home_banking_id}.
     */
    public InstructionLoad resolveInstruction(
            String targetSessionId, int botJobId, int homeBankingId, int instructionId) {
        InstructionTarget target =
                instructionTarget(targetSessionId, botJobId, homeBankingId);
        if (target.ownerId() <= 0 || instructionId <= 0) {
            throw new IllegalArgumentException(target.component()
                    ? "A valid organization and instruction are required."
                    : "A valid Bot Job and instruction are required.");
        }

        ErrorMessage error =
                database.loadInstructions(target.ownerId(), -1, -1, target.instructionTable());
        if (error != null) {
            throw new IllegalArgumentException(error.getErrorMessage());
        }

        List<InstructionLoad> instructionRows =
                ownerInstructionCopies(targetSessionId, target.ownerId());
        InstructionLoad selected = instructionRows.stream()
                .filter(row -> row != null
                        && row.getId() != null
                        && row.getId() == instructionId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "The selected instruction is no longer available. Refresh "
                                + target.workspaceLabel()
                                + "."));
        if (selected.getBlockId() == null || selected.getBlockId() <= 0) {
            throw new IllegalArgumentException("The selected instruction has no valid Block.");
        }

        error = database.loadBlocks(target.ownerId(), "", target.blockTable());
        if (error != null) {
            throw new IllegalArgumentException(error.getErrorMessage());
        }
        List<BlockLoadDTO> blockRows =
                ownerBlockCopies(targetSessionId, target.ownerId());
        InstructionLoad resolved = resolveSelectionFromRows(
                targetSessionId,
                blockRows,
                instructionRows,
                botJobId,
                homeBankingId,
                selected.getBlockId(),
                instructionId);
        return enrichAndOrderInstructions(
                        List.of(gson.fromJson(gson.toJson(resolved), InstructionLoad.class)),
                        blockRows)
                .get(0);
    }

    /**
     * Resolves an instruction and its Block as one owner-scoped Command Editor selection.
     *
     * <p>The detached page is allowed to change its selection, but it is never allowed to assert
     * ownership by sending IDs. Both collections are reloaded for the active workspace owner and
     * the submitted Instruction must belong to the submitted Block.
     */
    public InstructionLoad resolveSelection(
            String targetSessionId,
            int botJobId,
            int homeBankingId,
            int blockId,
            int instructionId) {
        InstructionTarget target =
                instructionTarget(targetSessionId, botJobId, homeBankingId);
        if (target.ownerId() <= 0 || blockId <= 0 || instructionId <= 0) {
            throw new IllegalArgumentException(target.component()
                    ? "A valid organization, Block, and Instruction are required."
                    : "A valid Bot Job, Block, and Instruction are required.");
        }

        ErrorMessage error =
                database.loadInstructions(target.ownerId(), -1, -1, target.instructionTable());
        if (error == null) {
            error = database.loadBlocks(target.ownerId(), "", target.blockTable());
        }
        if (error != null) {
            throw new IllegalArgumentException(error.getErrorMessage());
        }

        List<BlockLoadDTO> blockRows =
                ownerBlockCopies(targetSessionId, target.ownerId());
        List<InstructionLoad> instructionRows =
                ownerInstructionCopies(targetSessionId, target.ownerId());
        InstructionLoad resolved = resolveSelectionFromRows(
                targetSessionId,
                blockRows,
                instructionRows,
                botJobId,
                homeBankingId,
                blockId,
                instructionId);
        InstructionLoad detached =
                gson.fromJson(gson.toJson(resolved), InstructionLoad.class);
        return enrichAndOrderInstructions(List.of(detached), blockRows)
                .get(0);
    }

    static InstructionLoad resolveSelectionFromRows(
            List<BlockLoadDTO> blocks,
            List<InstructionLoad> instructionRows,
            int botJobId,
            int homeBankingId,
            int blockId,
            int instructionId) {
        return resolveSelectionFromRows(
                ScannerWorkspaceSessions.BOT_JOB_TASKS,
                blocks,
                instructionRows,
                botJobId,
                homeBankingId,
                blockId,
                instructionId);
    }

    static InstructionLoad resolveSelectionFromRows(
            String targetSessionId,
            List<BlockLoadDTO> blocks,
            List<InstructionLoad> instructionRows,
            int botJobId,
            int homeBankingId,
            int blockId,
            int instructionId) {
        InstructionTarget target =
                instructionTarget(targetSessionId, botJobId, homeBankingId);
        if (target.ownerId() <= 0) {
            throw new IllegalArgumentException(target.component()
                    ? "A valid organization is required."
                    : "A valid Bot Job is required.");
        }
        BlockLoadDTO block = blocks == null
                ? null
                : blocks.stream()
                        .filter(row -> row != null
                                && row.getId() != null
                                && row.getId() == blockId)
                        .findFirst()
                        .orElse(null);
        if (block == null) {
            throw new IllegalArgumentException(
                    "The selected Block is no longer available. Refresh the Command Editor.");
        }
        if (target.component()) {
            if (!Objects.equals(block.getHomeBankingId(), target.ownerId())) {
                throw new IllegalArgumentException(
                        "The selected Block does not belong to this organization.");
            }
        } else if (!Objects.equals(block.getBotJobId(), target.ownerId())) {
            throw new IllegalArgumentException(
                    "The selected Block does not belong to this Bot Job.");
        } else if (homeBankingId > 0
                && block.getHomeBankingId() != null
                && block.getHomeBankingId() > 0
                && block.getHomeBankingId() != homeBankingId) {
            throw new IllegalArgumentException(
                    "The selected Block does not belong to this organization.");
        }

        InstructionLoad instruction = instructionRows == null
                ? null
                : instructionRows.stream()
                        .filter(row -> row != null
                                && row.getId() != null
                                && row.getId() == instructionId)
                        .findFirst()
                        .orElse(null);
        if (instruction == null) {
            throw new IllegalArgumentException(
                    "The selected instruction is no longer available. Refresh the Command Editor.");
        }
        if (target.component()) {
            if (!Objects.equals(instruction.getHomeBankingId(), target.ownerId())) {
                throw new IllegalArgumentException(
                        "The selected instruction does not belong to this organization.");
            }
        } else if (!Objects.equals(instruction.getBotJobId(), target.ownerId())) {
            throw new IllegalArgumentException(
                    "The selected instruction does not belong to this Bot Job.");
        } else if (homeBankingId > 0
                && instruction.getHomeBankingId() != null
                && instruction.getHomeBankingId() > 0
                && instruction.getHomeBankingId() != homeBankingId) {
            throw new IllegalArgumentException(
                    "The selected instruction does not belong to this organization.");
        }
        if (instruction.getBlockId() == null || instruction.getBlockId() != blockId) {
            throw new IllegalArgumentException(
                    "The selected instruction does not belong to the selected Block.");
        }
        return instruction;
    }

    static InstructionTarget instructionTarget(
            String targetSessionId, int botJobId, int homeBankingId) {
        if (ScannerWorkspaceSessions.BOT_JOB_TASKS.equals(targetSessionId)) {
            return new InstructionTarget(
                    false,
                    botJobId,
                    "instruction",
                    "block",
                    "Bot Job Details");
        }
        if (ScannerWorkspaceSessions.COMPONENT_TASKS.equals(targetSessionId)) {
            return new InstructionTarget(
                    true,
                    homeBankingId,
                    "component_instruction",
                    "component_block",
                    "Components");
        }
        throw new IllegalArgumentException(
                "The Command Editor target session is not supported.");
    }

    record InstructionTarget(
            boolean component,
            int ownerId,
            String instructionTable,
            String blockTable,
            String workspaceLabel) {}

    public JsonObject bootstrap(JsonObject body) {
        JsonObject response = new JsonObject();
        String sessionId = string(body, "targetSessionId", ScannerWorkspaceSessions.BOT_JOB_TASKS);
        boolean componentSession = isComponentSession(sessionId);
        int whereId = componentSession
                ? integer(body, "homeBankingId", -1)
                : integer(body, "botJobId", -1);
        String variableTable = componentSession ? "component_variable" : "variable";
        String blockTable = componentSession ? "component_block" : "block";
        int instructionId = integer(
                body,
                "selectedInstructionId",
                integer(body, "instructionId", -1));

        ErrorMessage error = database.loadInstructions(
                whereId, -1, -1, componentSession ? "component_instruction" : "instruction");
        List<InstructionLoad> loadedInstructions =
                error == null ? ownerInstructionCopies(sessionId, whereId) : List.of();
        if (error == null) error = database.loadBlocks(whereId, "", blockTable);
        List<BlockLoadDTO> orderedBlocks = error == null
                ? orderedBlocks(ownerBlockCopies(sessionId, whereId))
                : List.of();
        List<InstructionLoad> orderedInstructions =
                enrichAndOrderInstructions(loadedInstructions, orderedBlocks);
        InstructionLoad selectedInstruction = orderedInstructions.stream()
                .filter(row -> row.getId() != null && row.getId() == instructionId)
                .findFirst()
                .orElse(null);
        int selectedBlockId = selectedInstruction != null
                        && selectedInstruction.getBlockId() != null
                ? selectedInstruction.getBlockId()
                : integer(body, "selectedBlockId", integer(body, "blockId", -1));

        response.addProperty("ok", error == null);
        response.add("variables", loadVariables(variableTable, whereId));
        response.add("webFields", webFields(orderedInstructions));
        response.add("blocks", gson.toJsonTree(orderedBlocks));
        response.add("instructions", gson.toJsonTree(orderedInstructions));
        response.addProperty("selectedBlockId", selectedBlockId);
        response.addProperty(
                "selectedInstructionId",
                selectedInstruction == null ? -1 : selectedInstruction.getId());
        boolean excelGotoConflict = excelGotoExists(
                sessionId,
                integer(body, "botJobId", -1),
                integer(body, "homeBankingId", -1),
                instructionId);
        response.add("commands", capabilityService.catalog(string(body, "instructionActions", ""), excelGotoConflict));
        response.addProperty("graphRevision", revisionService.revision(orderedInstructions));
        JsonObject rowCapabilities = new JsonObject();
        rowCapabilities.addProperty(
                "canInsertElseIf", canInsertElseIf(orderedInstructions, instructionId));
        rowCapabilities.addProperty(
                "canSplit",
                canSplit(orderedInstructions, instructionId));
        response.add("rowCapabilities", rowCapabilities);
        orderedInstructions.stream()
                .filter(row -> row.getId() != null && row.getId() == instructionId)
                .findFirst()
                .filter(row -> CommandRegistry.isEditableCommand(row.getActions()))
                .ifPresent(row -> response.add("draft", operationCodec.decode(row)));
        if (error != null) response.addProperty("error", error.getErrorMessage());
        return response;
    }

    static List<InstructionLoad> orderedInstructions(List<InstructionLoad> rows) {
        if (rows == null) return List.of();
        return rows.stream()
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator
                        .comparingInt((InstructionLoad row) ->
                                value(row.getBlockOrderNumber(), Integer.MAX_VALUE))
                        .thenComparingInt(row ->
                                value(row.getBlockId(), Integer.MAX_VALUE))
                        .thenComparingInt(row ->
                                value(row.getInstructionOrderNumber(), Integer.MAX_VALUE))
                        .thenComparingInt(row ->
                                value(row.getId(), Integer.MAX_VALUE)))
                .toList();
    }

    static List<BlockLoadDTO> orderedBlocks(List<BlockLoadDTO> rows) {
        if (rows == null) return List.of();
        return rows.stream()
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator
                        .comparingInt((BlockLoadDTO row) ->
                                value(row.getBlockOrderNumber(), Integer.MAX_VALUE))
                        .thenComparingInt(row -> value(row.getId(), Integer.MAX_VALUE)))
                .toList();
    }

    private List<InstructionLoad> ownerInstructionCopies(
            String sessionId, int ownerId) {
        boolean componentSession = isComponentSession(sessionId);
        return instructions(sessionId).stream()
                .filter(Objects::nonNull)
                .filter(row -> componentSession
                        ? Objects.equals(row.getHomeBankingId(), ownerId)
                        : Objects.equals(row.getBotJobId(), ownerId))
                .map(row -> gson.fromJson(gson.toJson(row), InstructionLoad.class))
                .toList();
    }

    private List<BlockLoadDTO> ownerBlockCopies(String sessionId, int ownerId) {
        boolean componentSession = isComponentSession(sessionId);
        List<BlockLoadDTO> source =
                componentSession ? lists.getListBlockComp() : lists.getListBlock();
        return source.stream()
                .filter(Objects::nonNull)
                .filter(row -> componentSession
                        ? Objects.equals(row.getHomeBankingId(), ownerId)
                        : Objects.equals(row.getBotJobId(), ownerId))
                .map(row -> gson.fromJson(gson.toJson(row), BlockLoadDTO.class))
                .toList();
    }

    static List<InstructionLoad> enrichAndOrderInstructions(
            List<InstructionLoad> instructionRows, List<BlockLoadDTO> blockRows) {
        Map<Integer, BlockLoadDTO> blocksById = new LinkedHashMap<>();
        if (blockRows != null) {
            blockRows.stream()
                    .filter(row -> row != null && row.getId() != null)
                    .forEach(row -> blocksById.put(row.getId(), row));
        }
        if (instructionRows != null) {
            instructionRows.stream()
                    .filter(Objects::nonNull)
                    .forEach(row -> {
                        BlockLoadDTO block = blocksById.get(row.getBlockId());
                        if (block == null) return;
                        row.setBlockName(block.getName());
                        row.setBlockOrderNumber(block.getBlockOrderNumber());
                    });
        }
        return orderedInstructions(instructionRows);
    }

    public JsonObject memoryCapabilities(JsonObject body) {
        String sessionId = string(body, "targetSessionId", ScannerWorkspaceSessions.BOT_JOB_TASKS);
        boolean componentSession = isComponentSession(sessionId);
        ErrorMessage error = reloadInstructions(body, sessionId);
        int whereId = componentSession
                ? integer(body, "homeBankingId", -1)
                : integer(body, "botJobId", -1);
        if (error == null) {
            error = database.loadBlocks(whereId, "", componentSession ? "component_block" : "block");
        }
        JsonObject response = new JsonObject();
        response.addProperty("ok", error == null);
        JsonArray capabilities = new JsonArray();
        JsonArray blockCapabilities = new JsonArray();
        if (error == null) {
            List<InstructionLoad> instructionRows = instructions(sessionId);
            List<BlockLoadDTO> blockRows = componentSession
                    ? lists.getListBlockComp()
                    : lists.getListBlock();
            Set<Integer> protectedIds = memoryProtectedIds(instructionRows);
            Set<Integer> invalidConditionalIds = invalidConditionalBlockIds(instructionRows);
            for (InstructionLoad row : instructionRows) {
                if (row == null || row.getId() == null) continue;
                String addReason = memoryBlockReason(row, protectedIds);
                String moveReason = moveGroupService.resolve(instructionRows, row.getId()).isEmpty()
                        ? "The connected move group could not be resolved."
                        : null;
                JsonObject capability = new JsonObject();
                capability.addProperty("instructionId", row.getId());
                capability.addProperty("canAddToMemory", addReason == null);
                capability.addProperty("canMove", moveReason == null);
                JsonArray allowedBlockIds = new JsonArray();
                if (moveReason == null) {
                    blockRows.stream()
                            .filter(block -> block != null && block.getId() != null)
                            .sorted(Comparator.comparingInt(block -> block.getBlockOrderNumber() == null
                                    ? Integer.MAX_VALUE
                                    : block.getBlockOrderNumber()))
                            .forEach(block -> allowedBlockIds.add(block.getId()));
                }
                capability.add("allowedBlockIds", allowedBlockIds);
                String deleteReason = deleteBlockReason(row, invalidConditionalIds, instructionRows);
                capability.addProperty("canDelete", deleteReason == null);
                capability.addProperty("deleteCount", deleteImpactCount(row, instructionRows));
                capability.add("deleteRows", deleteImpactRows(row, instructionRows));
                if (moveReason != null) capability.addProperty("reason", moveReason);
                if (addReason != null) capability.addProperty("addReason", addReason);
                if (deleteReason != null) capability.addProperty("deleteReason", deleteReason);
                capabilities.add(capability);
            }
            for (BlockLoadDTO block : blockRows) {
                if (block == null || block.getId() == null) continue;
                List<InstructionLoad> contained = instructionRows.stream()
                        .filter(row -> row != null && block.getId().equals(row.getBlockId()))
                        .sorted(Comparator.comparingInt(row -> row.getInstructionOrderNumber() == null
                                ? Integer.MAX_VALUE
                                : row.getInstructionOrderNumber()))
                        .toList();
                List<InstructionLoad> externalReferences = instructionRows.stream()
                        .filter(row -> row != null && row.getId() != null
                                && !block.getId().equals(row.getBlockId())
                                && block.getId().equals(row.getParentBlockId()))
                        .toList();
                String blockDeleteReason = blockRows.size() <= 1
                        ? "A job must keep at least one block."
                        : !externalReferences.isEmpty()
                                ? "Commands in other blocks reference this block."
                                : null;
                JsonObject blockCapability = new JsonObject();
                blockCapability.addProperty("blockId", block.getId());
                blockCapability.addProperty("blockName", block.getName());
                blockCapability.addProperty("canDelete", blockDeleteReason == null);
                blockCapability.addProperty("instructionCount", contained.size());
                JsonArray impactRows = new JsonArray();
                contained.forEach(row -> addDeleteImpactRow(impactRows, row));
                blockCapability.add("deleteRows", impactRows);
                JsonArray references = new JsonArray();
                externalReferences.forEach(row -> addDeleteImpactRow(references, row));
                blockCapability.add("externalReferences", references);
                if (blockDeleteReason != null) blockCapability.addProperty("reason", blockDeleteReason);
                blockCapabilities.add(blockCapability);
            }
        } else {
            response.addProperty("error", error.getErrorMessage());
        }
        response.add("capabilities", capabilities);
        response.add("blockCapabilities", blockCapabilities);
        if (error == null) response.addProperty("graphRevision", graphRevision(sessionId));
        return response;
    }

    private Set<Integer> invalidConditionalBlockIds(List<InstructionLoad> rows) {
        Set<Integer> invalidIds = new java.util.HashSet<>();
        for (List<InstructionLoad> blockRows : rows.stream()
                .filter(row -> row != null && row.getBlockId() != null)
                .collect(java.util.stream.Collectors.groupingBy(InstructionLoad::getBlockId)).values()) {
            blockRows.sort(Comparator.comparingInt(row -> row.getInstructionOrderNumber() == null
                    ? Integer.MAX_VALUE : row.getInstructionOrderNumber()));
            if (conditionalValidator.validate(blockRows) != null) {
                blockRows.stream().filter(row -> row.getId() != null).forEach(row -> invalidIds.add(row.getId()));
            }
        }
        return invalidIds;
    }

    private String deleteBlockReason(InstructionLoad row, Set<Integer> invalidConditionalIds,
            List<InstructionLoad> rows) {
        if (invalidConditionalIds.contains(row.getId())) return "Conditional graph is invalid.";
        if (Set.of("LOOP", "REFRESH_LOOP").contains(row.getActions())
                && loopGroupService.groupIds(rows, row.getId()).isEmpty()) {
            return "Loop parent relationship is invalid.";
        }
        if ("ELSEIF".equals(row.getActions())
                && conditionalBranchService.elseIfBranchIds(rows, row.getId()).isEmpty()) {
            return "ELSEIF branch boundaries are invalid.";
        }
        return null;
    }

    private int deleteImpactCount(InstructionLoad row, List<InstructionLoad> rows) {
        return deleteImpactService.resolve(row, rows).size();
    }

    private JsonArray deleteImpactRows(InstructionLoad row, List<InstructionLoad> rows) {
        JsonArray impact = new JsonArray();
        deleteImpactService.resolve(row, rows).forEach(candidate -> addDeleteImpactRow(impact, candidate));
        return impact;
    }

    private void addDeleteImpactRow(JsonArray impact, InstructionLoad candidate) {
        JsonObject item = new JsonObject();
        item.addProperty("id", candidate.getId());
        item.addProperty("name", candidate.getName());
        item.addProperty("action", candidate.getActions());
        item.addProperty("order", candidate.getInstructionOrderNumber());
        impact.add(item);
    }

    public ErrorMessage validateMoveRevision(SplitDTO split) {
        if (split == null) return new ErrorMessage("Move Instruction Refused", "Missing request", "ROW_MOVE payload is missing.");
        String sessionId = targetTaskSession(split.getSessionId());
        JsonObject body = new JsonObject();
        body.addProperty("botJobId", split.getBotJobId() == null ? -1 : split.getBotJobId());
        body.addProperty("homeBankingId", split.getHomeBankingId() == null ? -1 : split.getHomeBankingId());
        ErrorMessage error = reloadInstructions(body, sessionId);
        if (error != null) return error;
        if (split.getGraphRevision() == null || split.getGraphRevision().isBlank()) {
            return new ErrorMessage("Move Instruction Refused", "Graph revision is required", "Refresh the grid and try again.");
        }
        if (!split.getGraphRevision().equals(graphRevision(sessionId))) {
            return new ErrorMessage("Move Instruction Refused", "Instructions changed", "Refresh the grid before moving rows.");
        }
        return null;
    }

    public ErrorMessage validateDeleteRevision(SplitDTO split) {
        if (split == null) return new ErrorMessage("Delete Instruction Refused", "Missing request", "Delete payload is missing.");
        String sessionId = targetTaskSession(split.getSessionId());
        JsonObject body = new JsonObject();
        body.addProperty("botJobId", split.getBotJobId() == null ? -1 : split.getBotJobId());
        body.addProperty("homeBankingId", split.getHomeBankingId() == null ? -1 : split.getHomeBankingId());
        ErrorMessage error = reloadInstructions(body, sessionId);
        if (error != null) return error;
        if (split.getGraphRevision() == null || split.getGraphRevision().isBlank()) {
            return new ErrorMessage("Delete Instruction Refused", "Graph revision is required", "Refresh the grid and try again.");
        }
        if (!split.getGraphRevision().equals(graphRevision(sessionId))) {
            return new ErrorMessage("Delete Instruction Refused", "Instructions changed", "Refresh the grid before deleting.");
        }
        return null;
    }

    public ErrorMessage validateRollbackRevision(SplitDTO split) {
        if (split == null) {
            return new ErrorMessage(
                    "Rollback Block Refused",
                    "Missing request",
                    "BLOCK_ROLLBACK payload is missing.");
        }
        String sessionId = targetTaskSession(split.getSessionId());
        JsonObject body = new JsonObject();
        body.addProperty(
                "botJobId",
                split.getBotJobId() == null ? -1 : split.getBotJobId());
        body.addProperty(
                "homeBankingId",
                split.getHomeBankingId() == null ? -1 : split.getHomeBankingId());
        ErrorMessage error = reloadInstructions(body, sessionId);
        if (error != null) return error;
        if (split.getGraphRevision() == null || split.getGraphRevision().isBlank()) {
            return new ErrorMessage(
                    "Rollback Block Refused",
                    "Graph revision is required",
                    "Refresh the grid and try again.");
        }
        if (!split.getGraphRevision().equals(graphRevision(sessionId))) {
            return new ErrorMessage(
                    "Rollback Block Refused",
                    "Instructions changed",
                    "Refresh the grid before rolling back Blocks.");
        }
        return null;
    }

    public ErrorMessage validateDeleteMetadata(SplitDTO request, InstructionLoad stored) {
        if (request == null || stored == null) {
            return new ErrorMessage(
                    "Delete Instruction Refused",
                    "Instruction not found",
                    "The selected instruction no longer exists. Refresh the grid.");
        }
        if (deleteMetadataMatches(request, stored)) return null;

        log.warn(
                "DELETE_INSTRUCTION_METADATA_MISMATCH requestId={} instructionId={}"
                        + " requestedAction={} storedAction={} requestedParentId={} storedParentId={}"
                        + " requestedBlockId={} storedBlockId={}",
                request.getRequestId(),
                request.getInstructionId(),
                request.getActions(),
                stored.getActions(),
                request.getParentId(),
                stored.getParentId(),
                request.getBlockId(),
                stored.getBlockId());
        return new ErrorMessage(
                "Delete Instruction Refused",
                "Instruction metadata changed",
                "Refresh the grid before deleting this instruction.");
    }

    static boolean deleteMetadataMatches(SplitDTO request, InstructionLoad stored) {
        if (request == null || stored == null) return false;
        String requestedAction = request.getActions() == null ? "" : request.getActions().trim();
        String storedAction = stored.getActions() == null ? "" : stored.getActions().trim();
        return storedAction.equalsIgnoreCase(requestedAction)
                && normalizeOptionalRelationshipId(stored.getParentId())
                        == normalizeOptionalRelationshipId(request.getParentId())
                && java.util.Objects.equals(stored.getBlockId(), request.getBlockId());
    }

    private static int normalizeOptionalRelationshipId(Integer value) {
        return value == null || value <= 0 ? -1 : value;
    }

    private Set<Integer> memoryProtectedIds(List<InstructionLoad> rows) {
        Set<Integer> protectedIds = new java.util.HashSet<>();
        Map<Integer, InstructionLoad> byId = rows.stream()
                .filter(row -> row != null && row.getId() != null)
                .collect(java.util.stream.Collectors.toMap(InstructionLoad::getId, row -> row));
        for (InstructionLoad row : rows) {
            if (row == null || !Set.of("LOOP", "REFRESH_LOOP").contains(row.getActions())) continue;
            if (row.getId() != null) protectedIds.add(row.getId());
            if (row.getParentId() != null) {
                protectedIds.add(row.getParentId());
                InstructionLoad parent = byId.get(row.getParentId());
                if (parent != null && parent.getBlockId() != null && parent.getInstructionOrderNumber() != null
                        && row.getInstructionOrderNumber() != null) {
                    int first = Math.min(parent.getInstructionOrderNumber(), row.getInstructionOrderNumber());
                    int last = Math.max(parent.getInstructionOrderNumber(), row.getInstructionOrderNumber());
                    rows.stream()
                            .filter(member -> member != null && parent.getBlockId().equals(member.getBlockId())
                                    && member.getInstructionOrderNumber() != null
                                    && member.getInstructionOrderNumber() >= first
                                    && member.getInstructionOrderNumber() <= last
                                    && member.getId() != null)
                            .forEach(member -> protectedIds.add(member.getId()));
                }
            }
        }
        for (List<InstructionLoad> blockRows : rows.stream()
                .filter(row -> row != null && row.getBlockId() != null)
                .collect(java.util.stream.Collectors.groupingBy(InstructionLoad::getBlockId)).values()) {
            blockRows.sort(Comparator.comparingInt(row -> row.getInstructionOrderNumber() == null
                    ? Integer.MAX_VALUE : row.getInstructionOrderNumber()));
            int depth = 0;
            for (InstructionLoad row : blockRows) {
                if ("IF".equals(row.getActions())) depth++;
                if (depth > 0 && row.getId() != null) protectedIds.add(row.getId());
                if ("ENDIF".equals(row.getActions()) && depth > 0) depth--;
            }
        }
        return protectedIds;
    }

    private String memoryBlockReason(InstructionLoad row, Set<Integer> protectedIds) {
        if (protectedIds.contains(row.getId())) return "Part of a conditional or loop group.";
        if (CommandRegistry.isSpecialAction(row.getActions())
                && (row.getParentId() != null || row.getVariableId() != null || row.getParentBlockId() != null)) {
            return "Command has Web Field, Variable, or Block dependencies.";
        }
        return null;
    }

    private JsonArray webFields(List<InstructionLoad> instructions) {
        JsonArray rows = new JsonArray();
        for (InstructionLoad instruction : instructions) {
            if (instruction == null || instruction.getId() == null || isSpecialAction(instruction.getActions())) continue;
            JsonObject row = new JsonObject();
            row.addProperty("id", instruction.getId());
            row.addProperty("name", instruction.getName());
            row.addProperty("actions", instruction.getActions());
            row.addProperty("tagName", instruction.getTagName());
            row.addProperty("blockId", instruction.getBlockId());
            row.addProperty("blockName", instruction.getBlockName());
            row.addProperty("instructionOrderNumber", instruction.getInstructionOrderNumber());
            rows.add(row);
        }
        return rows;
    }

    private JsonArray loadVariables(String table, int whereId) {
        JsonArray rows = new JsonArray();
        String ownerColumn = "component_variable".equals(table) ? "home_banking_id" : "bot_job_id";
        String instructionTable = "component_variable".equals(table) ? "component_instruction" : "instruction";
        String sql = "SELECT v.id,v.type,v.name,v.value,v.local_format,v.delimiter,v.instruction_id,"
                + "COUNT(i.variable_id) used_vars FROM " + table + " v LEFT JOIN " + instructionTable
                + " i ON i.variable_id=v.id WHERE v." + ownerColumn + "=? "
                + "GROUP BY v.id,v.type,v.name,v.value,v.local_format,v.delimiter,v.instruction_id ORDER BY v.id";
        try (Connection connection = database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, whereId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    JsonObject row = new JsonObject();
                    row.addProperty("id", result.getInt("id"));
                    row.addProperty("type", result.getString("type"));
                    row.addProperty("name", result.getString("name"));
                    row.addProperty("value", result.getString("value"));
                    row.addProperty("localFormat", result.getString("local_format"));
                    row.addProperty("delimiter", result.getString("delimiter"));
                    row.addProperty("instructionId", result.getInt("instruction_id"));
                    row.addProperty("usedVars", result.getInt("used_vars"));
                    rows.add(row);
                }
            }
        } catch (SQLException exception) {
            JsonObject error = new JsonObject();
            error.addProperty("error", exception.getMessage());
            rows.add(error);
        }
        return rows;
    }

    private String variableType(String sessionId, int variableId, int botJobId, int homeBankingId) {
        boolean componentSession = isComponentSession(sessionId);
        String table = componentSession ? "component_variable" : "variable";
        String ownerColumn = componentSession ? "home_banking_id" : "bot_job_id";
        int whereId = componentSession ? homeBankingId : botJobId;
        String sql = "SELECT type FROM " + table + " WHERE id=? AND " + ownerColumn + "=?";
        try (Connection connection = database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, variableId);
            statement.setInt(2, whereId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString("type") : null;
            }
        } catch (SQLException exception) {
            return null;
        }
    }

    private boolean isSpecialAction(String action) {
        return CommandRegistry.isSpecialAction(action);
    }

    public JsonObject apply(JsonObject body) {
        return correlateResponse(body, serializedMutation(body, () -> applyOnce(body)));
    }

    static JsonObject correlateResponse(JsonObject request, JsonObject response) {
        JsonObject correlated = response == null ? new JsonObject() : response;
        if (request != null && request.has("requestId") && !request.get("requestId").isJsonNull()
                && !correlated.has("requestId")) {
            correlated.add("requestId", request.get("requestId").deepCopy());
        }
        return correlated;
    }

    private JsonObject applyOnce(JsonObject body) {
        String requestId = string(body, "requestId", "").trim();
        JsonObject completed = completedRequest(requestId);
        if (completed != null) {
            log.info("COMMAND_EDITOR_APPLY_CACHE_HIT requestId={}", requestId);
            return completed;
        }
        JsonObject response = new JsonObject();
        String targetSession = string(body, "targetSessionId", ScannerWorkspaceSessions.BOT_JOB_TASKS);
        ErrorMessage loadError = reloadInstructions(body, targetSession);
        if (loadError != null) return failure(loadError.getErrorMessage());
        JsonObject stale = validateGraphRevision(body, targetSession);
        if (stale != null) return stale;
        String mode = string(body, "mode", "after");
        String action = CommandRegistry.canonicalize(string(body, "action", ""));
        String name = string(body, "name", action).trim();
        String operation;
        int hold = integer(body, "hold", CommandRegistry.defaultHold(action));

        if (action.isEmpty() || name.isEmpty()) return failure("Command and name are required.");
        if (!CommandRegistry.isCommand(action)) return failure("Unsupported command action: " + action);
        if ("edit".equals(mode) && "IF".equals(action)) {
            return failure("IF must be inserted as a complete conditional family and cannot replace one instruction.");
        }
        if (integer(body, "blockId", -1) < 1) return failure("A valid block is required.");
        if ("edit".equals(mode) && !editableCommand(targetSession, integer(body, "instructionId", -1))) {
            return failure("Original Web Fields and conditional boundaries cannot be converted with Edit Command.");
        }
        if ("EXCEL GOTO".equals(action)
                && excelGotoExists(targetSession, integer(body, "botJobId", -1), integer(body, "homeBankingId", -1),
                        "edit".equals(mode) ? integer(body, "instructionId", -1) : -1)) {
            return failure("Only one EXCEL GOTO command is allowed in this job.");
        }
        boolean requiresWebField = CommandRegistry.requires(action, "webField");
        boolean requiresVariable = CommandRegistry.requires(action, "variable");
        boolean requiresBlock = CommandRegistry.requires(action, "block");
        Integer submittedParentId = nullableInteger(body, "parentId");
        Integer submittedVariableId = nullableInteger(body, "variableId");
        Integer submittedParentBlockId = nullableInteger(body, "parentBlockId");

        // The React panel can retain selections from the previously viewed command. Canonicalize
        // the draft before validation/persistence so a block-only command such as GOTO cannot be
        // rejected because of stale Web Field or Variable identifiers.
        Integer parentId = requiresWebField ? submittedParentId : null;
        Integer variableId = requiresVariable ? submittedVariableId : null;
        Integer parentBlockId = requiresBlock ? submittedParentBlockId : null;
        if ((!requiresWebField && submittedParentId != null)
                || (!requiresVariable && submittedVariableId != null)) {
            log.info(
                    "COMMAND_EDITOR_IGNORED_STALE_FIELDS requestId={} action={} parentIdIgnored={} variableIdIgnored={}",
                    requestId,
                    action,
                    !requiresWebField && submittedParentId != null,
                    !requiresVariable && submittedVariableId != null);
        }

        if (requiresWebField && parentId == null) return failure("Select a compatible Web Field.");
        if (requiresVariable && variableId == null) return failure("Select a Variable for the Web Field.");
        if (requiresBlock && parentBlockId == null) return failure("Select a destination Block.");
        if (requiresBlock) {
            boolean componentSession = isComponentSession(targetSession);
            int ownerId = componentSession
                    ? integer(body, "homeBankingId", -1)
                    : integer(body, "botJobId", -1);
            ErrorMessage blockLoadError = database.loadBlocks(
                    ownerId,
                    "",
                    componentSession ? "component_block" : "block");
            if (blockLoadError != null) {
                return failure(blockLoadError.getErrorMessage());
            }
            List<BlockLoadDTO> ownerBlocks = componentSession
                    ? lists.getListBlockComp()
                    : lists.getListBlock();
            boolean destinationExists = ownerBlocks.stream()
                    .anyMatch(block -> block != null
                            && block.getId() != null
                            && block.getId().equals(parentBlockId));
            if (!destinationExists) {
                return failure(
                        "The selected destination Block is no longer available. Refresh the grid.");
            }
            if (parentBlockId.equals(integer(body, "blockId", -1))) {
                return failure("Select a destination Block different from the current Block.");
            }
        }
        InstructionLoad selectedWebField = parentId == null ? null : webField(targetSession, parentId);
        if (parentId != null && (selectedWebField == null
                || selectedWebField.getBlockId() == null
                || selectedWebField.getBlockId() != integer(body, "blockId", -1))) {
            return failure("The selected Web Field is outside the reference instruction block.");
        }
        if (selectedWebField != null && !CommandRegistry.supportsTag(action, selectedWebField.getTagName())) {
            return failure(action + " is not compatible with the selected Web Field type.");
        }
        if (variableId != null && parentId != null && !variableBelongsToWebField(targetSession, variableId, parentId, integer(body, "botJobId", -1), integer(body, "homeBankingId", -1))) {
            return failure("The selected Variable does not belong to the selected Web Field.");
        }
        if (variableId != null) {
            String selectedVariableType = variableType(
                    targetSession, variableId, integer(body, "botJobId", -1), integer(body, "homeBankingId", -1));
            if (!CommandRegistry.supportsVariableType(action, selectedVariableType)) {
                return failure("The selected Variable type is not compatible with " + action + ".");
            }
        }
        try {
            operation = operationCodec.encode(
                    canonicalOperationBody(body, parentId, variableId, parentBlockId), action);
        } catch (Exception exception) {
            return failure(exception.getMessage());
        }

        SplitDTO split = new SplitDTO();
        split.setType("edit".equals(mode) ? "EDIT_OPERATION" : "before".equals(mode) ? "INSERT_BEFORE" : "INSERT_AFTER");
        split.setSessionId(targetSession);
        split.setHomeBankingId(integer(body, "homeBankingId", -1));
        split.setBotJobId(integer(body, "botJobId", -1));
        split.setBotJobName(string(body, "botJobName", ""));
        split.setBlockId(integer(body, "blockId", -1));
        split.setBlockName(string(body, "blockName", ""));
        split.setBlockOrderNumber(integer(body, "blockOrderNumber", 1));
        split.setInstructionId(integer(body, "instructionId", -1));
        split.setInstructionName(string(body, "instructionName", name));
        split.setInstructionOrderNumber(integer(body, "instructionOrderNumber", 1));
        split.setActions(action);
        split.setOperation(operation);
        split.setVariableId(variableId);
        split.setParentId(parentId);
        split.setParentBlockId(parentBlockId);

        log.info(
                "COMMAND_EDITOR_PERSIST_START requestId={} targetSession={} botJobId={} homeBankingId={}"
                        + " instructionId={} blockId={} action={} mode={} parentBlockId={} count={}",
                requestId,
                targetSession,
                split.getBotJobId(),
                split.getHomeBankingId(),
                split.getInstructionId(),
                split.getBlockId(),
                action,
                mode,
                parentBlockId,
                integer(body, "count", 1));
        ErrorMessage error = database.preFillNewInstruction(name, name, action, operation, hold, split, false);
        if (error != null) {
            log.warn(
                    "COMMAND_EDITOR_PERSIST_FAILED requestId={} instructionId={} action={} error={}",
                    requestId,
                    split.getInstructionId(),
                    action,
                    error.getErrorMessage());
            return failure(error.getErrorMessage());
        }

        if (isComponentSession(targetSession)) {
            error = database.loadComponentsComplete(split.getHomeBankingId(), split.getBotJobId(), split.getBotJobName());
        } else {
            error = engine.loadCompleteJobs(split.getBotJobId());
        }
        if (error != null) return failure(error.getErrorMessage());

        List<BotJobLoadDTO> jobs = isComponentSession(targetSession)
                ? lists.getListBotJobComp()
                : lists.getListBotJob();
        List<InstructionLoad> instructions = lists.buildJsonViewData(jobs);
        response.addProperty("ok", true);
        response.addProperty("message", "Command saved.");
        response.add("instructions", gson.toJsonTree(instructions));
        if (body.has("requestId")) response.add("requestId", body.get("requestId"));
        if (!requestId.isEmpty()) rememberCompleted(requestId, response);
        log.info(
                "COMMAND_EDITOR_PERSIST_SUCCESS requestId={} instructionId={} action={} parentBlockId={}",
                requestId,
                split.getInstructionId(),
                action,
                parentBlockId);
        return response;
    }

    static JsonObject canonicalOperationBody(
            JsonObject body, Integer parentId, Integer variableId, Integer parentBlockId) {
        JsonObject canonical = body == null ? new JsonObject() : body.deepCopy();
        replaceNullableInteger(canonical, "parentId", parentId);
        replaceNullableInteger(canonical, "variableId", variableId);
        replaceNullableInteger(canonical, "parentBlockId", parentBlockId);
        return canonical;
    }

    private static void replaceNullableInteger(JsonObject body, String key, Integer value) {
        if (value == null) body.remove(key);
        else body.addProperty(key, value);
    }

    public JsonObject insertElseIf(JsonObject body) {
        return serializedMutation(body, () -> insertElseIfOnce(body));
    }

    private JsonObject insertElseIfOnce(JsonObject body) {
        String requestId = string(body, "requestId", "").trim();
        JsonObject completed = completedRequest(requestId);
        if (completed != null) return completed;
        String targetSession = string(body, "targetSessionId", ScannerWorkspaceSessions.BOT_JOB_TASKS);
        boolean componentSession = isComponentSession(targetSession);
        int whereId = componentSession
                ? integer(body, "homeBankingId", -1)
                : integer(body, "botJobId", -1);
        int blockId = integer(body, "blockId", -1);
        int anchorId = integer(body, "instructionId", -1);
        String instructionTable = componentSession
                ? "component_instruction"
                : "instruction";
        ErrorMessage error = database.loadInstructions(whereId, -1, -1, instructionTable);
        if (error != null) return failure(error.getErrorMessage());
        JsonObject stale = validateGraphRevision(body, targetSession);
        if (stale != null) return stale;

        List<InstructionLoad> blockRows = (componentSession
                        ? lists.getListInstructionComp()
                        : lists.getListInstruction())
                .stream()
                .filter(row -> row != null && row.getBlockId() != null && row.getBlockId() == blockId)
                .sorted(Comparator.comparingInt(row -> row.getInstructionOrderNumber() == null
                        ? Integer.MAX_VALUE
                        : row.getInstructionOrderNumber()))
                .toList();

        String graphError = conditionalValidator.validate(blockRows);
        if (graphError != null) return failure("Invalid conditional graph: " + graphError);

        Integer rootId = enclosingIfRoot(blockRows, anchorId);
        if (rootId == null) return failure("The selected instruction is not inside a valid IF family.");

        InstructionLoad boundary = blockRows.stream()
                .filter(row -> row.getParentId() != null && row.getParentId().equals(rootId))
                .filter(row -> "ELSE".equals(row.getActions()) || "ENDIF".equals(row.getActions()))
                .findFirst()
                .orElse(null);
        if (boundary == null) return failure("The IF family has no valid ELSE or ENDIF boundary.");

        SplitDTO split = new SplitDTO();
        split.setType("INSERT_BEFORE");
        split.setSessionId(targetSession);
        split.setHomeBankingId(integer(body, "homeBankingId", -1));
        split.setBotJobId(integer(body, "botJobId", -1));
        split.setBotJobName(string(body, "botJobName", ""));
        split.setBlockId(blockId);
        split.setBlockName(string(body, "blockName", ""));
        split.setBlockOrderNumber(integer(body, "blockOrderNumber", 1));
        split.setInstructionId(boundary.getId());
        split.setInstructionName("ELSEIF");
        split.setInstructionOrderNumber(boundary.getInstructionOrderNumber());
        split.setActions("ELSEIF");
        split.setOperation("ELSEIF");
        split.setParentId(rootId);

        error = database.preFillNewInstruction("ELSEIF", "ELSEIF", "ELSEIF", "ELSEIF", 1, split, false);
        if (error != null) return failure(error.getErrorMessage());
        JsonObject response = refreshAfterMutation(split, "ELSEIF inserted.", body);
        if (!requestId.isEmpty() && response.has("ok") && response.get("ok").getAsBoolean()) {
            rememberCompleted(requestId, response);
        }
        return response;
    }

    public ErrorMessage executeSplit(SplitDTO split, java.util.function.Supplier<ErrorMessage> mutation) {
        String requestId = split == null || split.getRequestId() == null ? "" : split.getRequestId().trim();
        if (requestId.isEmpty()) return splitError("Split request ID is required.");
        synchronized (completedSplitRequests) {
            if (completedSplitRequests.containsKey(requestId)) return null;
            ErrorMessage error = validateSplit(split);
            if (error != null) return error;
            error = mutation.get();
            if (error == null) {
                completedSplitRequests.put(requestId, Boolean.TRUE);
                while (completedSplitRequests.size() > 256) {
                    completedSplitRequests.remove(completedSplitRequests.keySet().iterator().next());
                }
            }
            return error;
        }
    }

    public JsonObject previewSplit(JsonObject body) {
        JsonObject response = new JsonObject();
        String targetSession = string(body, "targetSessionId", ScannerWorkspaceSessions.BOT_JOB_TASKS);
        InstructionTarget target;
        try {
            target = instructionTarget(
                    targetSession,
                    integer(body, "botJobId", -1),
                    integer(body, "homeBankingId", -1));
        } catch (IllegalArgumentException invalidTarget) {
            return failure(invalidTarget.getMessage());
        }
        if (target.ownerId() <= 0) {
            return failure(target.component()
                    ? "A valid organization is required."
                    : "A valid Bot Job is required.");
        }

        ErrorMessage loadError = reloadInstructions(body, targetSession);
        if (loadError != null) return failure(loadError.getErrorMessage());
        loadError = database.loadBlocks(target.ownerId(), "", target.blockTable());
        if (loadError != null) return failure(loadError.getErrorMessage());
        JsonObject stale = validateGraphRevision(body, targetSession);
        if (stale != null) return stale;

        int anchorId = integer(body, "instructionId", -1);
        InstructionLoad anchor = instructions(targetSession).stream()
                .filter(row -> row != null && row.getId() != null && row.getId() == anchorId)
                .findFirst()
                .orElse(null);
        if (anchor == null || anchor.getBlockId() == null) return failure("The split anchor no longer exists.");

        List<InstructionLoad> blockRows = instructions(targetSession).stream()
                .filter(row -> row != null && anchor.getBlockId().equals(row.getBlockId()))
                .sorted(Comparator.comparingInt(row -> row.getInstructionOrderNumber() == null
                        ? Integer.MAX_VALUE
                        : row.getInstructionOrderNumber()))
                .toList();
        String reason = splitValidator.validate(blockRows, anchorId);
        if (reason != null) return failure(reason);

        int splitIndex = indexOf(blockRows, anchorId);
        response.addProperty("ok", true);
        response.addProperty("graphRevision", graphRevision(targetSession));
        response.addProperty("blockId", anchor.getBlockId());
        response.addProperty("anchorInstructionId", anchorId);
        response.add("retainedRows", splitPreviewRows(blockRows.subList(0, splitIndex + 1)));
        response.add("movedRows", splitPreviewRows(blockRows.subList(splitIndex + 1, blockRows.size())));
        response.addProperty("retainedCount", splitIndex + 1);
        response.addProperty("movedCount", blockRows.size() - splitIndex - 1);
        if (body.has("requestId")) response.add("requestId", body.get("requestId"));
        return response;
    }

    public JsonObject previewMove(JsonObject body) {
        String targetSession = string(body, "targetSessionId", ScannerWorkspaceSessions.BOT_JOB_TASKS);
        ErrorMessage loadError = reloadInstructions(body, targetSession);
        if (loadError != null) return failure(loadError.getErrorMessage());
        JsonObject stale = validateGraphRevision(body, targetSession);
        if (stale != null) return stale;

        int instructionId = integer(body, "instructionId", -1);
        int destinationBlockId = integer(body, "destinationBlockId", -1);
        List<InstructionLoad> group = moveGroupService.resolve(instructions(targetSession), instructionId);
        if (group.isEmpty()) return failure("The move instruction no longer exists.");
        if (destinationBlockId < 1) return failure("A destination block is required.");

        boolean destinationExists = (isComponentSession(targetSession)
                        ? lists.getListBlockComp()
                        : lists.getListBlock())
                .stream()
                .anyMatch(block -> block != null && block.getId() != null && block.getId() == destinationBlockId);
        if (!destinationExists) return failure("The destination block no longer exists.");

        JsonObject response = new JsonObject();
        response.addProperty("ok", true);
        response.addProperty("graphRevision", graphRevision(targetSession));
        response.addProperty("sourceBlockId", group.get(0).getBlockId());
        response.addProperty("destinationBlockId", destinationBlockId);
        response.addProperty("destinationIndex", integer(body, "destinationIndex", 0));
        response.addProperty("groupCount", group.size());
        response.add("groupRows", movePreviewRows(group));
        if (body.has("requestId")) response.add("requestId", body.get("requestId"));
        return response;
    }

    private JsonArray movePreviewRows(List<InstructionLoad> rows) {
        JsonArray preview = new JsonArray();
        for (InstructionLoad row : rows) {
            JsonObject item = new JsonObject();
            item.addProperty("id", row.getId());
            item.addProperty("order", row.getInstructionOrderNumber());
            item.addProperty("name", row.getName());
            item.addProperty("action", row.getActions());
            item.addProperty("parentId", row.getParentId());
            item.addProperty("blockId", row.getBlockId());
            preview.add(item);
        }
        return preview;
    }

    private JsonArray splitPreviewRows(List<InstructionLoad> rows) {
        JsonArray preview = new JsonArray();
        for (InstructionLoad row : rows) {
            JsonObject item = new JsonObject();
            item.addProperty("id", row.getId());
            item.addProperty("order", row.getInstructionOrderNumber());
            item.addProperty("name", row.getName());
            item.addProperty("action", row.getActions());
            item.addProperty("parentId", row.getParentId());
            preview.add(item);
        }
        return preview;
    }

    private ErrorMessage validateSplit(SplitDTO split) {
        if (split == null || split.getInstructionId() == null) {
            return splitError("Split request is missing its owner or anchor instruction.");
        }
        String targetSession = split.getSessionId();
        InstructionTarget target;
        try {
            target = instructionTarget(
                    targetSession,
                    split.getBotJobId() == null ? -1 : split.getBotJobId(),
                    split.getHomeBankingId() == null ? -1 : split.getHomeBankingId());
        } catch (IllegalArgumentException invalidTarget) {
            return splitError(invalidTarget.getMessage());
        }
        if (target.ownerId() <= 0) {
            return splitError(target.component()
                    ? "Split request is missing its organization."
                    : "Split request is missing its Bot Job.");
        }
        ErrorMessage error = database.loadInstructions(
                target.ownerId(), -1, -1, target.instructionTable());
        if (error != null) return error;
        error = database.loadBlocks(target.ownerId(), "", target.blockTable());
        if (error != null) return error;
        if (split.getGraphRevision() == null || split.getGraphRevision().isBlank()) {
            return splitError("Instruction graph revision is required. Reopen the command panel.");
        }
        if (!split.getGraphRevision().equals(graphRevision(targetSession))) {
            return splitError("Instructions changed while this panel was open. Reopen it before splitting.");
        }
        if (!canSplit(targetSession, split.getInstructionId())) {
            return splitError("Split Component is not allowed at the selected instruction.");
        }
        String partitionError = validateSplitPartition(split, targetSession, target);
        if (partitionError != null) return splitError(partitionError);
        return null;
    }

    private String validateSplitPartition(
            SplitDTO split, String targetSession, InstructionTarget target) {
        InstructionLoad anchor = instructions(targetSession).stream()
                .filter(row -> row != null && row.getId() != null && row.getId().equals(split.getInstructionId()))
                .findFirst()
                .orElse(null);
        if (anchor == null || anchor.getBlockId() == null) return "The split anchor no longer exists.";
        List<InstructionLoad> currentRows = instructions(targetSession).stream()
                .filter(row -> row != null && anchor.getBlockId().equals(row.getBlockId()))
                .sorted(Comparator.comparingInt(row -> row.getInstructionOrderNumber() == null
                        ? Integer.MAX_VALUE
                        : row.getInstructionOrderNumber()))
                .toList();
        int anchorIndex = indexOf(currentRows, split.getInstructionId());
        DetailsDTO details = split.getDetails();
        BlockDetailsDTO original = details == null ? null : details.getOriginalBlock();
        BlockDetailsDTO created = details == null ? null : details.getNewBlock();
        if (anchorIndex < 0 || original == null || created == null
                || !anchor.getBlockId().equals(original.getBlockId())) {
            return "The split block details do not match the selected instruction.";
        }
        if (target.component()) {
            if ((original.getHomeBankingId() != null
                            && original.getHomeBankingId() > 0
                            && !original.getHomeBankingId().equals(target.ownerId()))
                    || (created.getHomeBankingId() != null
                            && created.getHomeBankingId() > 0
                            && !created.getHomeBankingId().equals(target.ownerId()))) {
                return "The split block details do not match the active organization.";
            }
        } else if (original.getBotJobId() == null
                || !original.getBotJobId().equals(target.ownerId())
                || created.getBotJobId() == null
                || !created.getBotJobId().equals(target.ownerId())) {
            return "The split block details do not match the selected instruction.";
        }
        if (!matchesPartition(original.getUpdatedInstructions(), currentRows.subList(0, anchorIndex + 1), true)
                || !matchesPartition(created.getInstructions(), currentRows.subList(anchorIndex + 1, currentRows.size()), false)) {
            return "The split rows are stale or do not form a contiguous block partition.";
        }
        String blockOrderError =
                validateSplitBlockOrders(split, targetSession, target, original, created);
        if (blockOrderError != null) return blockOrderError;
        return null;
    }

    private String validateSplitBlockOrders(
            SplitDTO split,
            String targetSession,
            InstructionTarget target,
            BlockDetailsDTO original,
            BlockDetailsDTO created) {
        List<BlockLoadDTO> ownerBlocks =
                isComponentSession(targetSession)
                        ? lists.getListBlockComp()
                        : lists.getListBlock();
        BlockLoadDTO currentBlock = ownerBlocks.stream()
                .filter(block -> block != null && block.getId() != null && block.getId().equals(original.getBlockId()))
                .findFirst()
                .orElse(null);
        if (currentBlock == null || currentBlock.getBlockOrderNumber() == null
                || original.getBlockOrderNumber() == null
                || !original.getBlockOrderNumber().equals(currentBlock.getBlockOrderNumber())
                || created.getBlockOrderNumber() == null
                || created.getBlockOrderNumber() != currentBlock.getBlockOrderNumber() + 1) {
            return "The split block order does not match the current database order.";
        }
        List<BlockLoadDTO> expectedLater = ownerBlocks.stream()
                .filter(block -> block != null && block.getId() != null && block.getBlockOrderNumber() != null
                        && block.getBlockOrderNumber() > currentBlock.getBlockOrderNumber())
                .sorted(Comparator.comparingInt(BlockLoadDTO::getBlockOrderNumber))
                .toList();
        List<BlockOrderDetailDTO> submitted = split.getDetails().getUpdatedBlocks();
        return validateLaterBlockOrders(
                targetSession, target.ownerId(), expectedLater, submitted);
    }

    static String validateLaterBlockOrders(
            int botJobId, List<BlockLoadDTO> expectedLater, List<BlockOrderDetailDTO> submitted) {
        return validateLaterBlockOrders(
                ScannerWorkspaceSessions.BOT_JOB_TASKS,
                botJobId,
                expectedLater,
                submitted);
    }

    static String validateLaterBlockOrders(
            String targetSessionId,
            int ownerId,
            List<BlockLoadDTO> expectedLater,
            List<BlockOrderDetailDTO> submitted) {
        if (submitted == null || submitted.size() != expectedLater.size()) {
            return "The split request does not include every affected later block.";
        }
        Map<Integer, BlockOrderDetailDTO> submittedByBlockId = new LinkedHashMap<>();
        for (BlockOrderDetailDTO actual : submitted) {
            if (actual == null
                    || actual.getBlockId() == null
                    || submittedByBlockId.putIfAbsent(actual.getBlockId(), actual) != null) {
                return "The later block order updates are stale or invalid.";
            }
        }
        for (BlockLoadDTO expected : expectedLater) {
            BlockOrderDetailDTO actual = submittedByBlockId.get(expected.getId());
            boolean ownerMismatch = isComponentSession(targetSessionId)
                    ? actual != null
                            && actual.getHomeBankId() != null
                            && actual.getHomeBankId() > 0
                            && actual.getHomeBankId() != ownerId
                    : actual == null
                            || actual.getBotJobId() == null
                            || actual.getBotJobId() != ownerId;
            if (actual == null
                    || ownerMismatch
                    || actual.getBlockOrderNumber() == null
                    || actual.getBlockOrderNumber() != expected.getBlockOrderNumber() + 1) {
                return "The later block order updates are stale or invalid.";
            }
        }
        return null;
    }

    private boolean matchesPartition(List<UpdatedRow> submitted, List<InstructionLoad> expected, boolean original) {
        if (submitted == null || submitted.size() != expected.size()) return false;
        for (int index = 0; index < expected.size(); index++) {
            UpdatedRow actual = submitted.get(index);
            InstructionLoad row = expected.get(index);
            if (actual == null || actual.getInstructionId() == null || row.getId() == null
                    || !actual.getInstructionId().equals(row.getId())
                    || actual.getInstructionOrderNumber() == null
                    || actual.getInstructionOrderNumber() != index + 1) return false;
            if (original && (actual.getBlockId() == null || !actual.getBlockId().equals(row.getBlockId()))) return false;
        }
        return true;
    }

    private ErrorMessage splitError(String message) {
        return new ErrorMessage("Split Component", "Split operation refused", message);
    }

    private Integer enclosingIfRoot(List<InstructionLoad> rows, int anchorId) {
        Deque<Integer> roots = new ArrayDeque<>();
        for (InstructionLoad row : rows) {
            String action = row.getActions();
            if ("IF".equals(action) && row.getId() != null) roots.push(row.getId());
            if (row.getId() != null && row.getId() == anchorId) {
                if (Set.of("ELSE", "ENDIF").contains(action)) return null;
                return roots.peek();
            }
            if ("ENDIF".equals(action) && !roots.isEmpty()) roots.pop();
        }
        return null;
    }

    private boolean canInsertElseIf(String sessionId, int instructionId) {
        return canInsertElseIf(instructions(sessionId), instructionId);
    }

    private boolean canInsertElseIf(
            List<InstructionLoad> instructionRows, int instructionId) {
        InstructionLoad selected = instructionRows.stream()
                .filter(row -> row != null && row.getId() != null && row.getId() == instructionId)
                .findFirst()
                .orElse(null);
        if (selected == null || selected.getBlockId() == null) return false;
        List<InstructionLoad> blockRows = instructionRows.stream()
                .filter(row -> row != null && selected.getBlockId().equals(row.getBlockId()))
                .sorted(Comparator.comparingInt(row -> row.getInstructionOrderNumber() == null
                        ? Integer.MAX_VALUE
                        : row.getInstructionOrderNumber()))
                .toList();
        if (conditionalValidator.validate(blockRows) != null) return false;
        Integer rootId = enclosingIfRoot(blockRows, instructionId);
        if (rootId == null) return false;
        InstructionLoad boundary = blockRows.stream()
                .filter(row -> row.getParentId() != null
                        && row.getParentId().equals(rootId)
                        && Set.of("ELSE", "ENDIF").contains(row.getActions()))
                .findFirst()
                .orElse(null);
        if (boundary == null || boundary.getInstructionOrderNumber() == null
                || selected.getInstructionOrderNumber() == null) return false;
        return selected.getInstructionOrderNumber() < boundary.getInstructionOrderNumber();
    }

    private boolean canSplit(String sessionId, int instructionId) {
        return canSplit(instructions(sessionId), instructionId);
    }

    private boolean canSplit(List<InstructionLoad> instructionRows, int instructionId) {
        InstructionLoad selected = instructionRows.stream()
                .filter(row -> row != null && row.getId() != null && row.getId() == instructionId)
                .findFirst()
                .orElse(null);
        if (selected == null || selected.getBlockId() == null) return false;
        List<InstructionLoad> blockRows = instructionRows.stream()
                .filter(row -> row != null && selected.getBlockId().equals(row.getBlockId()))
                .sorted(Comparator.comparingInt(row -> row.getInstructionOrderNumber() == null
                        ? Integer.MAX_VALUE
                        : row.getInstructionOrderNumber()))
                .toList();
        return splitValidator.validate(blockRows, instructionId) == null;
    }

    private int indexOf(List<InstructionLoad> rows, int instructionId) {
        for (int index = 0; index < rows.size(); index++) {
            InstructionLoad row = rows.get(index);
            if (row.getId() != null && row.getId() == instructionId) return index;
        }
        return -1;
    }

    private JsonObject refreshAfterMutation(SplitDTO split, String message, JsonObject request) {
        ErrorMessage error;
        if (isComponentSession(split.getSessionId())) {
            error = database.loadComponentsComplete(split.getHomeBankingId(), split.getBotJobId(), split.getBotJobName());
        } else {
            error = engine.loadCompleteJobs(split.getBotJobId());
        }
        if (error != null) return failure(error.getErrorMessage());
        List<BotJobLoadDTO> jobs = isComponentSession(split.getSessionId())
                ? lists.getListBotJobComp()
                : lists.getListBotJob();
        List<InstructionLoad> instructions = lists.buildJsonViewData(jobs);
        com.allinweb.ch.socket.InstructionRealtimePublisher.getInstance()
                .publishSnapshot(split.getHomeBankingId(), split.getSessionId(), instructions);
        JsonObject response = new JsonObject();
        response.addProperty("ok", true);
        response.addProperty("message", message);
        response.add("instructions", gson.toJsonTree(instructions));
        if (request.has("requestId")) response.add("requestId", request.get("requestId"));
        return response;
    }

    private void rememberCompleted(String requestId, JsonObject response) {
        completedRequests.remember(requestId, response);
    }

    private JsonObject completedRequest(String requestId) {
        if (requestId == null || requestId.isEmpty()) return null;
        return completedRequests.get(requestId);
    }

    private JsonObject serializedMutation(JsonObject body, java.util.function.Supplier<JsonObject> mutation) {
        String requestId = string(body, "requestId", "").trim();
        if (requestId.isEmpty()) return mutation.get();
        return completedRequests.execute(requestId, mutation, false);
    }

    private boolean editableCommand(String sessionId, int instructionId) {
        return instructions(sessionId).stream().anyMatch(row -> row != null
                && row.getId() != null
                && row.getId() == instructionId
                && CommandRegistry.isEditableCommand(row.getActions()));
    }

    private List<InstructionLoad> instructions(String sessionId) {
        return isComponentSession(sessionId) ? lists.getListInstructionComp() : lists.getListInstruction();
    }

    private ErrorMessage reloadInstructions(JsonObject body, String sessionId) {
        boolean component = isComponentSession(sessionId);
        int ownerId = component ? integer(body, "homeBankingId", -1) : integer(body, "botJobId", -1);
        return database.loadInstructions(ownerId, -1, -1, component ? "component_instruction" : "instruction");
    }

    private JsonObject validateGraphRevision(JsonObject body, String sessionId) {
        String expected = string(body, "graphRevision", "");
        if (expected.isBlank()) return failure("Instruction graph revision is required. Reopen the command panel.");
        if (!expected.equals(graphRevision(sessionId))) {
            return failure("Instructions changed while this panel was open. Reopen it before saving.");
        }
        return null;
    }

    private String graphRevision(String sessionId) {
        return revisionService.revision(instructions(sessionId));
    }

    private boolean excelGotoExists(
            String sessionId, int botJobId, int homeBankingId, int excludedInstructionId) {
        boolean component = isComponentSession(sessionId);
        String table = component ? "component_instruction" : "instruction";
        String owner = component ? "home_banking_id" : "bot_job_id";
        int ownerId = component ? homeBankingId : botJobId;
        String sql = "SELECT COUNT(*) FROM " + table
                + " WHERE " + owner + "=? AND actions='EXCEL GOTO' AND id<>?";
        try (Connection connection = database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, ownerId);
            statement.setInt(2, excludedInstructionId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt(1) > 0;
            }
        } catch (SQLException exception) {
            return true;
        }
    }

    private JsonObject failure(String message) {
        JsonObject response = new JsonObject();
        response.addProperty("ok", false);
        response.addProperty("error", message == null ? "Command operation failed." : message);
        return response;
    }

    private InstructionLoad webField(String sessionId, int instructionId) {
        return instructions(sessionId).stream().filter(row -> row != null
                && row.getId() != null
                && row.getId() == instructionId
                && !isSpecialAction(row.getActions()))
                .findFirst()
                .orElse(null);
    }

    private boolean variableBelongsToWebField(
            String sessionId, int variableId, int webFieldId, int botJobId, int homeBankingId) {
        boolean component = isComponentSession(sessionId);
        String table = component ? "component_variable" : "variable";
        String ownerColumn = component ? "home_banking_id" : "bot_job_id";
        int ownerId = component ? homeBankingId : botJobId;
        String sql = "SELECT COUNT(*) FROM " + table
                + " WHERE id=? AND instruction_id=? AND " + ownerColumn + "=?";
        try (Connection connection = database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, variableId);
            statement.setInt(2, webFieldId);
            statement.setInt(3, ownerId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt(1) == 1;
            }
        } catch (SQLException exception) {
            return false;
        }
    }

    private static String targetTaskSession(String sessionId) {
        return isComponentSession(sessionId)
                ? ScannerWorkspaceSessions.COMPONENT_TASKS
                : ScannerWorkspaceSessions.BOT_JOB_TASKS;
    }

    private static boolean isComponentSession(String sessionId) {
        return ScannerWorkspaceSessions.COMPONENT_TASKS.equals(sessionId);
    }

    private static String string(JsonObject body, String key, String fallback) {
        return body != null && body.has(key) && !body.get(key).isJsonNull() ? body.get(key).getAsString() : fallback;
    }

    private static int integer(JsonObject body, String key, int fallback) {
        try {
            return body != null && body.has(key) && !body.get(key).isJsonNull() ? body.get(key).getAsInt() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int value(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private static Integer nullableInteger(JsonObject body, String key) {
        return body != null && body.has(key) && !body.get(key).isJsonNull() ? body.get(key).getAsInt() : null;
    }
}

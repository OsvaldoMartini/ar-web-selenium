package com.allinweb.ch.facade;

import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.BlockDetailsDTO;
import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.BlockOrderDetailDTO;
import com.allinweb.ch.model.DetailsDTO;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.SplitDTO;
import com.allinweb.ch.model.UpdatedRow;
import com.allinweb.ch.socket.WebSocketSessionManager;
import com.allinweb.ch.util.ErrorMessage;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Pane-free backend for the React instruction command panel. */
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
    private final Map<String, JsonObject> completedRequests = new LinkedHashMap<>();
    private final Map<String, Boolean> completedSplitRequests = new LinkedHashMap<>();
    private final InstructionSplitValidator splitValidator = new InstructionSplitValidator();
    private final ConditionalBranchService conditionalBranchService = new ConditionalBranchService();
    private final LoopGroupService loopGroupService = new LoopGroupService();
    private final InstructionMoveGroupService moveGroupService = new InstructionMoveGroupService();

    private CommandEditorService() {}

    public static CommandEditorService getInstance() {
        return INSTANCE;
    }

    public JsonObject bootstrap(JsonObject body) {
        JsonObject response = new JsonObject();
        String sessionId = string(body, "targetSessionId", "botJobTasks");
        int whereId = "componentTasks".equals(sessionId)
                ? integer(body, "homeBankingId", -1)
                : integer(body, "botJobId", -1);
        String variableTable = "componentTasks".equals(sessionId) ? "component_variable" : "variable";
        String blockTable = "componentTasks".equals(sessionId) ? "component_block" : "block";
        int instructionId = integer(body, "instructionId", -1);
        String instructionName = string(body, "instructionName", "");

        ErrorMessage error = database.loadInstructions(
                whereId, -1, -1, "componentTasks".equals(sessionId) ? "component_instruction" : "instruction");
        if (error == null) error = database.loadBlocks(whereId, "", blockTable);

        response.addProperty("ok", error == null);
        response.add("variables", loadVariables(variableTable, whereId));
        response.add("webFields", webFields(sessionId));
        response.add("blocks", gson.toJsonTree(
                "componentTasks".equals(sessionId) ? lists.getListBlockComp() : lists.getListBlock()));
        boolean excelGotoConflict = excelGotoExists(
                sessionId,
                integer(body, "botJobId", -1),
                integer(body, "homeBankingId", -1),
                instructionId);
        response.add("commands", capabilityService.catalog(string(body, "instructionActions", ""), excelGotoConflict));
        response.addProperty("graphRevision", graphRevision(sessionId));
        JsonObject rowCapabilities = new JsonObject();
        rowCapabilities.addProperty("canInsertElseIf", canInsertElseIf(sessionId, instructionId));
        rowCapabilities.addProperty("canSplit", canSplit(sessionId, instructionId));
        response.add("rowCapabilities", rowCapabilities);
        instructions(sessionId).stream()
                .filter(row -> row != null && row.getId() != null && row.getId() == instructionId)
                .findFirst()
                .filter(row -> CommandRegistry.isEditableCommand(row.getActions()))
                .ifPresent(row -> response.add("draft", operationCodec.decode(row)));
        if (error != null) response.addProperty("error", error.getErrorMessage());
        return response;
    }

    public JsonObject memoryCapabilities(JsonObject body) {
        String sessionId = string(body, "targetSessionId", "botJobTasks");
        ErrorMessage error = reloadInstructions(body, sessionId);
        int whereId = "componentTasks".equals(sessionId)
                ? integer(body, "homeBankingId", -1)
                : integer(body, "botJobId", -1);
        if (error == null) {
            error = database.loadBlocks(whereId, "", "componentTasks".equals(sessionId) ? "component_block" : "block");
        }
        JsonObject response = new JsonObject();
        response.addProperty("ok", error == null);
        JsonArray capabilities = new JsonArray();
        JsonArray blockCapabilities = new JsonArray();
        if (error == null) {
            List<InstructionLoad> instructionRows = instructions(sessionId);
            List<BlockLoadDTO> blockRows = "componentTasks".equals(sessionId)
                    ? lists.getListBlockComp()
                    : lists.getListBlock();
            Set<Integer> protectedIds = memoryProtectedIds(instructionRows);
            Set<Integer> referencedIds = instructionRows.stream()
                    .filter(row -> row != null && row.getParentId() != null && !row.getParentId().equals(row.getId()))
                    .map(InstructionLoad::getParentId)
                    .collect(java.util.stream.Collectors.toSet());
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
                String deleteReason = deleteBlockReason(row, referencedIds, invalidConditionalIds, instructionRows);
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

    private String deleteBlockReason(InstructionLoad row, Set<Integer> referencedIds, Set<Integer> invalidConditionalIds,
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
        if (referencedIds.contains(row.getId()) && !Set.of("IF", "ELSE", "ENDIF").contains(row.getActions())) {
            return "Other instructions depend on this row.";
        }
        return null;
    }

    private int deleteImpactCount(InstructionLoad row, List<InstructionLoad> rows) {
        String rowAction = row.getActions() == null ? "" : row.getActions();
        if (Set.of("LOOP", "REFRESH_LOOP").contains(rowAction)) {
            return loopGroupService.groupIds(rows, row.getId()).size();
        }
        if ("ELSEIF".equals(row.getActions())) {
            return conditionalBranchService.elseIfBranchIds(rows, row.getId()).size();
        }
        if (!Set.of("IF", "ELSE", "ENDIF").contains(row.getActions())) return 1;
        Integer rootId = "IF".equals(row.getActions()) ? row.getId() : row.getParentId();
        if (rootId == null) return 1;
        long children = rows.stream()
                .filter(candidate -> candidate != null && candidate.getParentId() != null
                        && candidate.getParentId().equals(rootId) && !rootId.equals(candidate.getId()))
                .count();
        return Math.toIntExact(children + 1);
    }

    private JsonArray deleteImpactRows(InstructionLoad row, List<InstructionLoad> rows) {
        JsonArray impact = new JsonArray();
        if (row == null || row.getId() == null) return impact;
        String action = row.getActions() == null ? "" : row.getActions();
        if (Set.of("LOOP", "REFRESH_LOOP").contains(action)) {
            Set<Integer> groupIds = new java.util.HashSet<>(loopGroupService.groupIds(rows, row.getId()));
            rows.stream()
                    .filter(candidate -> candidate != null && candidate.getId() != null
                            && groupIds.contains(candidate.getId()))
                    .sorted(Comparator.comparingInt(candidate -> candidate.getInstructionOrderNumber() == null
                            ? Integer.MAX_VALUE
                            : candidate.getInstructionOrderNumber()))
                    .forEach(candidate -> addDeleteImpactRow(impact, candidate));
            return impact;
        }
        if ("ELSEIF".equals(action)) {
            Set<Integer> branchIds = new java.util.HashSet<>(
                    conditionalBranchService.elseIfBranchIds(rows, row.getId()));
            rows.stream()
                    .filter(candidate -> candidate != null && candidate.getId() != null
                            && branchIds.contains(candidate.getId()))
                    .sorted(Comparator.comparingInt(candidate -> candidate.getInstructionOrderNumber() == null
                            ? Integer.MAX_VALUE
                            : candidate.getInstructionOrderNumber()))
                    .forEach(candidate -> addDeleteImpactRow(impact, candidate));
            return impact;
        }
        Integer rootId = Set.of("IF", "ELSE", "ENDIF").contains(action)
                ? ("IF".equals(action) ? row.getId() : row.getParentId())
                : null;
        rows.stream()
                .filter(candidate -> candidate != null && candidate.getId() != null)
                .filter(candidate -> rootId == null
                        ? candidate.getId().equals(row.getId())
                        : candidate.getId().equals(rootId)
                                || (candidate.getParentId() != null && candidate.getParentId().equals(rootId)))
                .sorted(Comparator.comparingInt(candidate -> candidate.getInstructionOrderNumber() == null
                        ? Integer.MAX_VALUE
                        : candidate.getInstructionOrderNumber()))
                .forEach(candidate -> addDeleteImpactRow(impact, candidate));
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
        String sessionId = "componentTasks".equals(split.getSessionId()) ? "componentTasks" : "botJobTasks";
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
        String sessionId = "componentTasks".equals(split.getSessionId()) ? "componentTasks" : "botJobTasks";
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

    private JsonArray webFields(String sessionId) {
        List<InstructionLoad> instructions = "componentTasks".equals(sessionId)
                ? lists.getListInstructionComp()
                : lists.getListInstruction();
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
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
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
        String table = "componentTasks".equals(sessionId) ? "component_variable" : "variable";
        String ownerColumn = "componentTasks".equals(sessionId) ? "home_banking_id" : "bot_job_id";
        int whereId = "componentTasks".equals(sessionId) ? homeBankingId : botJobId;
        String sql = "SELECT type FROM " + table + " WHERE id=? AND " + ownerColumn + "=?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
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
        return serializedMutation(body, () -> applyOnce(body));
    }

    private JsonObject applyOnce(JsonObject body) {
        String requestId = string(body, "requestId", "").trim();
        JsonObject completed = completedRequest(requestId);
        if (completed != null) return completed;
        JsonObject response = new JsonObject();
        String targetSession = string(body, "targetSessionId", "botJobTasks");
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
        Integer parentId = nullableInteger(body, "parentId");
        Integer variableId = nullableInteger(body, "variableId");
        Integer parentBlockId = nullableInteger(body, "parentBlockId");
        if (CommandRegistry.requires(action, "webField") && parentId == null) return failure("Select a compatible Web Field.");
        if (CommandRegistry.requires(action, "variable") && variableId == null) return failure("Select a Variable for the Web Field.");
        if (CommandRegistry.requires(action, "block") && parentBlockId == null) return failure("Select a destination Block.");
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
            operation = operationCodec.encode(body, action);
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

        ErrorMessage error = database.preFillNewInstruction(name, name, action, operation, hold, split, false);
        if (error != null) return failure(error.getErrorMessage());

        if ("componentTasks".equals(targetSession)) {
            error = database.loadComponentsComplete(split.getHomeBankingId(), split.getBotJobId(), split.getBotJobName());
        } else {
            error = engine.loadCompleteJobs(split.getBotJobId());
        }
        if (error != null) return failure(error.getErrorMessage());

        List<BotJobLoadDTO> jobs = "componentTasks".equals(targetSession)
                ? lists.getListBotJobComp()
                : lists.getListBotJob();
        List<InstructionLoad> instructions = lists.buildJsonViewData(jobs);
        String operationId = "componentTasks".equals(targetSession) ? "componentsUpdate" : "updateInstructions";
        sessions.sendMessageJson(split.getHomeBankingId(), targetSession, gson.toJson(instructions), operationId);

        response.addProperty("ok", true);
        response.addProperty("message", "Command saved.");
        response.add("instructions", gson.toJsonTree(instructions));
        if (body.has("requestId")) response.add("requestId", body.get("requestId"));
        if (!requestId.isEmpty()) rememberCompleted(requestId, response);
        return response;
    }

    public JsonObject insertElseIf(JsonObject body) {
        return serializedMutation(body, () -> insertElseIfOnce(body));
    }

    private JsonObject insertElseIfOnce(JsonObject body) {
        String requestId = string(body, "requestId", "").trim();
        JsonObject completed = completedRequest(requestId);
        if (completed != null) return completed;
        String targetSession = string(body, "targetSessionId", "botJobTasks");
        int whereId = "componentTasks".equals(targetSession)
                ? integer(body, "homeBankingId", -1)
                : integer(body, "botJobId", -1);
        int blockId = integer(body, "blockId", -1);
        int anchorId = integer(body, "instructionId", -1);
        String instructionTable = "componentTasks".equals(targetSession)
                ? "component_instruction"
                : "instruction";
        ErrorMessage error = database.loadInstructions(whereId, -1, -1, instructionTable);
        if (error != null) return failure(error.getErrorMessage());
        JsonObject stale = validateGraphRevision(body, targetSession);
        if (stale != null) return stale;

        List<InstructionLoad> blockRows = ("componentTasks".equals(targetSession)
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
        String targetSession = string(body, "targetSessionId", "botJobTasks");
        if ("componentTasks".equals(targetSession)) return failure("Component blocks cannot be split here.");

        ErrorMessage loadError = reloadInstructions(body, targetSession);
        if (loadError != null) return failure(loadError.getErrorMessage());
        loadError = database.loadBlocks(integer(body, "botJobId", -1), "", "block");
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
        String targetSession = string(body, "targetSessionId", "botJobTasks");
        ErrorMessage loadError = reloadInstructions(body, targetSession);
        if (loadError != null) return failure(loadError.getErrorMessage());
        JsonObject stale = validateGraphRevision(body, targetSession);
        if (stale != null) return stale;

        int instructionId = integer(body, "instructionId", -1);
        int destinationBlockId = integer(body, "destinationBlockId", -1);
        List<InstructionLoad> group = moveGroupService.resolve(instructions(targetSession), instructionId);
        if (group.isEmpty()) return failure("The move instruction no longer exists.");
        if (destinationBlockId < 1) return failure("A destination block is required.");

        boolean destinationExists = ("componentTasks".equals(targetSession)
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
        if (split == null || split.getBotJobId() == null || split.getInstructionId() == null) {
            return splitError("Split request is missing its job or anchor instruction.");
        }
        ErrorMessage error = database.loadInstructions(split.getBotJobId(), -1, -1, "instruction");
        if (error != null) return error;
        error = database.loadBlocks(split.getBotJobId(), "", "block");
        if (error != null) return error;
        if (split.getGraphRevision() == null || split.getGraphRevision().isBlank()) {
            return splitError("Instruction graph revision is required. Reopen the command panel.");
        }
        if (!split.getGraphRevision().equals(graphRevision("botJobTasks"))) {
            return splitError("Instructions changed while this panel was open. Reopen it before splitting.");
        }
        if (!canSplit("botJobTasks", split.getInstructionId())) {
            return splitError("Split Component is not allowed at the selected instruction.");
        }
        String partitionError = validateSplitPartition(split);
        if (partitionError != null) return splitError(partitionError);
        return null;
    }

    private String validateSplitPartition(SplitDTO split) {
        InstructionLoad anchor = instructions("botJobTasks").stream()
                .filter(row -> row != null && row.getId() != null && row.getId().equals(split.getInstructionId()))
                .findFirst()
                .orElse(null);
        if (anchor == null || anchor.getBlockId() == null) return "The split anchor no longer exists.";
        List<InstructionLoad> currentRows = instructions("botJobTasks").stream()
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
                || !anchor.getBlockId().equals(original.getBlockId())
                || original.getBotJobId() == null || !original.getBotJobId().equals(split.getBotJobId())
                || created.getBotJobId() == null || !created.getBotJobId().equals(split.getBotJobId())) {
            return "The split block details do not match the selected instruction.";
        }
        if (!matchesPartition(original.getUpdatedInstructions(), currentRows.subList(0, anchorIndex + 1), true)
                || !matchesPartition(created.getInstructions(), currentRows.subList(anchorIndex + 1, currentRows.size()), false)) {
            return "The split rows are stale or do not form a contiguous block partition.";
        }
        String blockOrderError = validateSplitBlockOrders(split, original, created);
        if (blockOrderError != null) return blockOrderError;
        return null;
    }

    private String validateSplitBlockOrders(SplitDTO split, BlockDetailsDTO original, BlockDetailsDTO created) {
        BlockLoadDTO currentBlock = lists.getListBlock().stream()
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
        List<BlockLoadDTO> expectedLater = lists.getListBlock().stream()
                .filter(block -> block != null && block.getId() != null && block.getBlockOrderNumber() != null
                        && block.getBlockOrderNumber() > currentBlock.getBlockOrderNumber())
                .sorted(Comparator.comparingInt(BlockLoadDTO::getBlockOrderNumber))
                .toList();
        List<BlockOrderDetailDTO> submitted = split.getDetails().getUpdatedBlocks();
        if (submitted == null || submitted.size() != expectedLater.size()) {
            return "The split request does not include every affected later block.";
        }
        for (int index = 0; index < expectedLater.size(); index++) {
            BlockLoadDTO expected = expectedLater.get(index);
            BlockOrderDetailDTO actual = submitted.get(index);
            if (actual == null || actual.getBlockId() == null || !actual.getBlockId().equals(expected.getId())
                    || actual.getBotJobId() == null || !actual.getBotJobId().equals(split.getBotJobId())
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
        InstructionLoad selected = instructions(sessionId).stream()
                .filter(row -> row != null && row.getId() != null && row.getId() == instructionId)
                .findFirst()
                .orElse(null);
        if (selected == null || selected.getBlockId() == null) return false;
        List<InstructionLoad> blockRows = instructions(sessionId).stream()
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
        if ("componentTasks".equals(sessionId)) return false;
        InstructionLoad selected = instructions(sessionId).stream()
                .filter(row -> row != null && row.getId() != null && row.getId() == instructionId)
                .findFirst()
                .orElse(null);
        if (selected == null || selected.getBlockId() == null) return false;
        List<InstructionLoad> blockRows = instructions(sessionId).stream()
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
        if ("componentTasks".equals(split.getSessionId())) {
            error = database.loadComponentsComplete(split.getHomeBankingId(), split.getBotJobId(), split.getBotJobName());
        } else {
            error = engine.loadCompleteJobs(split.getBotJobId());
        }
        if (error != null) return failure(error.getErrorMessage());
        List<BotJobLoadDTO> jobs = "componentTasks".equals(split.getSessionId())
                ? lists.getListBotJobComp()
                : lists.getListBotJob();
        List<InstructionLoad> instructions = lists.buildJsonViewData(jobs);
        String operationId = "componentTasks".equals(split.getSessionId()) ? "componentsUpdate" : "updateInstructions";
        sessions.sendMessageJson(split.getHomeBankingId(), split.getSessionId(), gson.toJson(instructions), operationId);
        JsonObject response = new JsonObject();
        response.addProperty("ok", true);
        response.addProperty("message", message);
        response.add("instructions", gson.toJsonTree(instructions));
        if (request.has("requestId")) response.add("requestId", request.get("requestId"));
        return response;
    }

    private void rememberCompleted(String requestId, JsonObject response) {
        synchronized (completedRequests) {
            completedRequests.put(requestId, response.deepCopy());
            while (completedRequests.size() > 256) {
                String oldest = completedRequests.keySet().iterator().next();
                completedRequests.remove(oldest);
            }
        }
    }

    private JsonObject completedRequest(String requestId) {
        if (requestId == null || requestId.isEmpty()) return null;
        synchronized (completedRequests) {
            JsonObject completed = completedRequests.get(requestId);
            return completed == null ? null : completed.deepCopy();
        }
    }

    private JsonObject serializedMutation(JsonObject body, java.util.function.Supplier<JsonObject> mutation) {
        String requestId = string(body, "requestId", "").trim();
        if (requestId.isEmpty()) return mutation.get();
        synchronized (completedRequests) {
            JsonObject completed = completedRequests.get(requestId);
            if (completed != null) return completed.deepCopy();
            return mutation.get();
        }
    }

    private boolean editableCommand(String sessionId, int instructionId) {
        return instructions(sessionId).stream().anyMatch(row -> row != null
                && row.getId() != null
                && row.getId() == instructionId
                && CommandRegistry.isEditableCommand(row.getActions()));
    }

    private List<InstructionLoad> instructions(String sessionId) {
        return "componentTasks".equals(sessionId) ? lists.getListInstructionComp() : lists.getListInstruction();
    }

    private ErrorMessage reloadInstructions(JsonObject body, String sessionId) {
        boolean component = "componentTasks".equals(sessionId);
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
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            instructions(sessionId).stream()
                    .filter(row -> row != null)
                    .sorted(Comparator.comparing(row -> row.getId() == null ? Integer.MAX_VALUE : row.getId()))
                    .forEach(row -> digest.update(graphRow(row).getBytes(StandardCharsets.UTF_8)));
            byte[] hash = digest.digest();
            StringBuilder revision = new StringBuilder(hash.length * 2);
            for (byte value : hash) revision.append(String.format("%02x", value));
            return revision.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private String graphRow(InstructionLoad row) {
        return String.join("|",
                String.valueOf(row.getId()), String.valueOf(row.getBlockId()),
                String.valueOf(row.getInstructionOrderNumber()), String.valueOf(row.getActions()),
                String.valueOf(row.getParentId()), String.valueOf(row.getParentBlockId()),
                String.valueOf(row.getVariableId()), String.valueOf(row.getOperation())) + "\n";
    }

    private boolean excelGotoExists(
            String sessionId, int botJobId, int homeBankingId, int excludedInstructionId) {
        boolean component = "componentTasks".equals(sessionId);
        String table = component ? "component_instruction" : "instruction";
        String owner = component ? "home_banking_id" : "bot_job_id";
        int ownerId = component ? homeBankingId : botJobId;
        String sql = "SELECT COUNT(*) FROM " + table
                + " WHERE " + owner + "=? AND actions='EXCEL GOTO' AND id<>?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
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
        boolean component = "componentTasks".equals(sessionId);
        String table = component ? "component_variable" : "variable";
        String ownerColumn = component ? "home_banking_id" : "bot_job_id";
        int ownerId = component ? homeBankingId : botJobId;
        String sql = "SELECT COUNT(*) FROM " + table
                + " WHERE id=? AND instruction_id=? AND " + ownerColumn + "=?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
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

    private static Integer nullableInteger(JsonObject body, String key) {
        return body != null && body.has(key) && !body.get(key).isJsonNull() ? body.get(key).getAsInt() : null;
    }
}

package com.allinweb.ch.facade;

import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.ReferenceLoadDTO;
import com.allinweb.ch.model.UpdatedRow;
import com.allinweb.ch.model.VariableLoadDTO;
import com.allinweb.ch.util.ErrorMessage;
import com.google.gson.Gson;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Atomically applies the heterogeneous Memory List to one Bot Job.
 *
 * <p>This service deliberately does not call the legacy {@code createInject*} methods. Those
 * methods use separate connections and intermediate commits, so a failed variable/reference copy
 * can leave a partially injected component. Here source rows are loaded again from the database,
 * ownership and graph revision are checked in the transaction, generated keys are mapped on the
 * same connection, and the complete copy/insert is committed once.
 */
public final class ComponentMemoryApplyService {

    private static final int MAX_IDEMPOTENT_RESULTS = 512;
    private static final String COMPONENT_INSTRUCTION_SELECT = """
            SELECT id, instruction_order_number, actions, name, xpath, coordinates,
                   force_coordinates, iframe_xpath, tag_name, shadow_host, shadow_root,
                   css_selector, description, operation, optional, block_marked,
                   default_value, action_custom_max_wait_sec, on_hold_seconds, codified,
                   export_to_abr, active, block_id, variable_id, parent_block_id, parent_id,
                   home_banking_id, client_named
              FROM component_instruction
             WHERE home_banking_id = ?
             ORDER BY block_id, instruction_order_number, id
            """;
    private static final String BOT_INSTRUCTION_SELECT = """
            SELECT id, instruction_order_number, actions, name, xpath, coordinates,
                   force_coordinates, iframe_xpath, tag_name, shadow_host, shadow_root,
                   css_selector, description, operation, optional, block_marked,
                   default_value, action_custom_max_wait_sec, on_hold_seconds, codified,
                   export_to_abr, active, block_id, variable_id, parent_block_id, parent_id,
                   bot_job_id, client_named
              FROM instruction
             WHERE bot_job_id = ?
             ORDER BY block_id, instruction_order_number, id
            """;
    private static final String INSTRUCTION_INSERT = """
            INSERT INTO instruction (
                   instruction_order_number, actions, name, xpath, coordinates,
                   force_coordinates, iframe_xpath, tag_name, shadow_host, shadow_root,
                   css_selector, description, operation, optional, block_marked,
                   default_value, action_custom_max_wait_sec, on_hold_seconds, codified,
                   export_to_abr, active, block_id, variable_id, parent_block_id, parent_id,
                   bot_job_id, client_named)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String BLOCK_INSERT = """
            INSERT INTO block (
                   block_order_number, name, description, type_id, export_file, active, wait,
                   bot_job_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final ComponentMemoryApplyService INSTANCE =
            new ComponentMemoryApplyService(() -> PerformDataBase.getInstance().getConnection());

    private final ConnectionProvider connectionProvider;
    private final InstructionGraphRevisionService revisionService =
            new InstructionGraphRevisionService();
    private final InstructionMoveValidator moveValidator = new InstructionMoveValidator();
    private final InstructionDependencyClosureService dependencyClosureService =
            new InstructionDependencyClosureService();
    private final ConditionalGraphValidator conditionalValidator = new ConditionalGraphValidator();
    private final Gson gson = new Gson();
    private final LinkedHashMap<String, CompletedRequest> successfulRequests =
            new LinkedHashMap<>(32, 0.75f, true);

    ComponentMemoryApplyService(ConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider);
    }

    public static ComponentMemoryApplyService getInstance() {
        return INSTANCE;
    }

    /**
     * Applies a request once per process/request ID. Only committed results are cached; failed
     * requests remain retryable after the user corrects stale data.
     */
    public synchronized Result apply(Request request) {
        ErrorMessage requestError = validateRequest(request);
        if (requestError != null) return Result.failed(requestError);

        String idempotencyKey =
                request.homeBankingId() + ":" + request.botJobId() + ":" + request.requestId();
        String requestFingerprint = gson.toJson(request);
        CompletedRequest cached = successfulRequests.get(idempotencyKey);
        if (cached != null) {
            if (cached.fingerprint().equals(requestFingerprint)) {
                return cached.result().asDuplicate();
            }
            return Result.failed(error(
                    "Memory List Apply Refused",
                    "This request ID was already used for different Memory List data.",
                    "Refresh Memory List and retry with a new request."));
        }

        Result result;
        Connection connection;
        try {
            connection = connectionProvider.open();
        } catch (SQLException | RuntimeException failure) {
            return Result.failed(error(
                    "Memory List Apply Failed",
                    "The database connection could not be opened.",
                    failure.getMessage()));
        }
        try {
            result = applyTransaction(connection, request);
        } catch (RuntimeException failure) {
            result = Result.failed(error(
                    "Memory List Apply Failed",
                    "The database transaction could not be completed.",
                    failure.getMessage()));
        }
        try {
            connection.close();
        } catch (SQLException closeFailure) {
            // A committed transaction must never be reported as retryable merely because closing
            // its connection failed. Retrying would duplicate copied component rows.
            if (!result.committed()) {
                return Result.failed(error(
                        "Memory List Apply Failed",
                        "The database connection could not be closed safely.",
                        closeFailure.getMessage()));
            }
        }
        if (result.committed()) {
            successfulRequests.put(
                    idempotencyKey, new CompletedRequest(requestFingerprint, result));
            while (successfulRequests.size() > MAX_IDEMPOTENT_RESULTS) {
                String eldest = successfulRequests.keySet().iterator().next();
                successfulRequests.remove(eldest);
            }
        }
        return result;
    }

    /** Package-visible transaction seam used by focused database tests. */
    Result applyTransaction(Connection connection, Request request) {
        boolean previousAutoCommit;
        try {
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
        } catch (SQLException failure) {
            return Result.failed(error(
                    "Memory List Apply Failed",
                    "The database transaction could not be started.",
                    failure.getMessage()));
        }

        try {
            validateBotJobOwner(connection, request.botJobId(), request.homeBankingId());

            List<InstructionRow> currentBotRows =
                    loadInstructionRows(connection, BOT_INSTRUCTION_SELECT, request.botJobId(), false);
            Map<Integer, InstructionRow> currentBotById = indexInstructions(currentBotRows);
            Map<Integer, BlockRow> botBlocks = loadBotBlocks(connection, request.botJobId());
            List<VariableLoadDTO> botVariables = loadDependencyVariables(
                    connection, false, request.botJobId(), request.homeBankingId());

            List<InstructionRow> componentRows =
                    loadInstructionRows(
                            connection,
                            COMPONENT_INSTRUCTION_SELECT,
                            request.homeBankingId(),
                            true);
            Map<Integer, InstructionRow> componentById = indexInstructions(componentRows);
            Map<Integer, BlockRow> componentBlocks =
                    loadComponentBlocks(connection, request.homeBankingId());
            List<VariableLoadDTO> componentVariables = loadDependencyVariables(
                    connection, true, request.homeBankingId(), request.homeBankingId());
            String currentComponentRevision = revisionService.revision(
                    componentRows.stream().map(InstructionRow::asInstruction).toList());

            ComponentSelectionPlan componentPlan = planComponentSelections(
                    request,
                    componentRows,
                    componentById,
                    componentBlocks,
                    componentVariables,
                    currentComponentRevision);

            Set<Integer> selectedBotJobInstructionIds = validateExistingSelections(
                    request, currentBotById, currentBotRows, botVariables);
            boolean targetRequired = request.orderedItems().stream().anyMatch(item -> switch (item.kind()) {
                case COMPONENT_BLOCK -> false;
                case COMPONENT_INSTRUCTION ->
                    !componentPlan.coveredBySelectedBlock().contains(item.componentInstructionId());
                default -> true;
            });
            int effectiveTargetBlockId = request.targetBlockId();
            int createdTargetBlockId = -1;
            int createdTargetBlockOrderNumber = -1;
            NewTargetBlock newTargetBlock = request.newTargetBlock();
            if (newTargetBlock != null) {
                if (!targetRequired) {
                    throw new ApplyRefused(
                            "The selected Memory List rows create complete blocks and do not use a "
                                    + "new target block.");
                }
                BlockCreationService.InsertedBlock inserted =
                        BlockCreationService.insertBlockWithoutCommit(
                                connection,
                                request.botJobId(),
                                newTargetBlock.blockName(),
                                newTargetBlock.position(),
                                newTargetBlock.beforeBlockId(),
                                newTargetBlock.beforeBlockOrderNumber());
                createdTargetBlockId = inserted.blockId();
                createdTargetBlockOrderNumber = inserted.orderNumber();

                // Read the generated row back on this same transaction. This is both the required
                // create verification and the authoritative block-order catalog after a BEFORE
                // insertion shifted existing rows.
                botBlocks = loadBotBlocks(connection, request.botJobId());
                BlockRow verified = botBlocks.get(createdTargetBlockId);
                if (verified == null
                        || verified.order() != createdTargetBlockOrderNumber
                        || !Objects.equals(verified.name(), newTargetBlock.blockName())) {
                    throw new ApplyRefused(
                            "The new target block could not be verified. No data was saved.");
                }
                effectiveTargetBlockId = createdTargetBlockId;
            }
            if (targetRequired
                    && (effectiveTargetBlockId <= 0
                            || !botBlocks.containsKey(effectiveTargetBlockId))) {
                throw new ApplyRefused("Select a valid target block before applying.");
            }
            final int resolvedTargetBlockId = effectiveTargetBlockId;

            int nextBlockOrder = botBlocks.values().stream()
                            .map(BlockRow::order)
                            .max(Integer::compareTo)
                            .orElse(0)
                    + 1;
            Map<Integer, Integer> generatedBlockIds = new LinkedHashMap<>();
            Map<String, Integer> generatedBlocksByItem = new LinkedHashMap<>();
            try (PreparedStatement insertBlock =
                    connection.prepareStatement(BLOCK_INSERT, Statement.RETURN_GENERATED_KEYS)) {
                for (OrderedItem item : request.orderedItems()) {
                    if (item.kind() != ItemKind.COMPONENT_BLOCK) continue;
                    int sourceBlockId = item.componentBlockId();
                    if (generatedBlockIds.containsKey(sourceBlockId)) {
                        generatedBlocksByItem.put(item.itemKey(), generatedBlockIds.get(sourceBlockId));
                        continue;
                    }
                    BlockRow source = componentBlocks.get(sourceBlockId);
                    int generatedId =
                            insertBlock(insertBlock, source, nextBlockOrder++, request.botJobId());
                    generatedBlockIds.put(sourceBlockId, generatedId);
                    generatedBlocksByItem.put(item.itemKey(), generatedId);
                    botBlocks.put(
                            generatedId,
                            new BlockRow(
                                    generatedId,
                                    source.name(),
                                    source.description(),
                                    source.typeId(),
                                    source.exportFile(),
                                    source.active(),
                                    source.waitSeconds(),
                                    nextBlockOrder - 1));
                }
            }

            int nextTargetOrder = currentBotRows.stream()
                            .filter(row -> row.blockId() == resolvedTargetBlockId)
                            .map(InstructionRow::order)
                            .max(Integer::compareTo)
                            .orElse(0)
                    + 1;
            Map<Integer, Integer> generatedComponentInstructionIds = new LinkedHashMap<>();
            Map<Integer, Integer> generatedBotJobInstructionIds = new LinkedHashMap<>();
            Map<String, Integer> generatedBotJobInstructionsByItem = new LinkedHashMap<>();
            Map<String, Integer> generatedInstructionsByItem = new LinkedHashMap<>();
            Map<String, Integer> generatedScannerInstructionsByItem = new LinkedHashMap<>();
            List<InstructionRow> insertedRows = new ArrayList<>();

            try (PreparedStatement insertInstruction =
                    connection.prepareStatement(INSTRUCTION_INSERT, Statement.RETURN_GENERATED_KEYS)) {
                // A selected component block is cloned as a complete block in component row order.
                for (OrderedItem item : request.orderedItems()) {
                    if (item.kind() != ItemKind.COMPONENT_BLOCK) continue;
                    int sourceBlockId = item.componentBlockId();
                    int targetBlockId = generatedBlockIds.get(sourceBlockId);
                    for (InstructionRow source : componentPlan.rowsBySelectedBlock()
                            .getOrDefault(sourceBlockId, List.of())) {
                        if (generatedComponentInstructionIds.containsKey(source.id())) continue;
                        int generatedId = insertInstruction(
                                insertInstruction,
                                source.withoutRelations(),
                                source.order(),
                                targetBlockId,
                                request.botJobId());
                        generatedComponentInstructionIds.put(source.id(), generatedId);
                        insertedRows.add(source.asInserted(generatedId, targetBlockId, source.order()));
                    }
                }

                // Individual component rows and scanner rows are copied into the selected target.
                for (OrderedItem item : request.orderedItems()) {
                    if (item.kind() == ItemKind.BOT_JOB_INSTRUCTION) {
                        int sourceInstructionId = item.botJobInstructionId();
                        InstructionRow source = currentBotById.get(sourceInstructionId);
                        int generatedId = insertInstruction(
                                insertInstruction,
                                source.withoutRelations(),
                                nextTargetOrder++,
                                resolvedTargetBlockId,
                                request.botJobId());
                        generatedBotJobInstructionIds.put(sourceInstructionId, generatedId);
                        generatedBotJobInstructionsByItem.put(item.itemKey(), generatedId);
                        insertedRows.add(
                                source.asInserted(
                                        generatedId,
                                        resolvedTargetBlockId,
                                        nextTargetOrder - 1));
                    } else if (item.kind() == ItemKind.COMPONENT_INSTRUCTION) {
                        int sourceInstructionId = item.componentInstructionId();
                        Integer alreadyGenerated =
                                generatedComponentInstructionIds.get(sourceInstructionId);
                        if (alreadyGenerated != null) {
                            generatedInstructionsByItem.put(item.itemKey(), alreadyGenerated);
                            continue;
                        }
                        InstructionRow source = componentById.get(sourceInstructionId);
                        int generatedId = insertInstruction(
                                insertInstruction,
                                source.withoutRelations(),
                                nextTargetOrder++,
                                resolvedTargetBlockId,
                                request.botJobId());
                        generatedComponentInstructionIds.put(sourceInstructionId, generatedId);
                        generatedInstructionsByItem.put(item.itemKey(), generatedId);
                        insertedRows.add(
                                source.asInserted(
                                        generatedId,
                                        resolvedTargetBlockId,
                                        nextTargetOrder - 1));
                    } else if (item.kind() == ItemKind.PAGE_SCANNER_INSTRUCTION) {
                        InstructionRow source = InstructionRow.fromScanner(item.scannerInstruction());
                        int generatedId = insertInstruction(
                                insertInstruction,
                                source,
                                nextTargetOrder++,
                                resolvedTargetBlockId,
                                request.botJobId());
                        generatedScannerInstructionsByItem.put(item.itemKey(), generatedId);
                        insertedRows.add(
                                source.asInserted(
                                        generatedId,
                                        resolvedTargetBlockId,
                                        nextTargetOrder - 1));
                    }
                }
            }

            copyBotJobVariables(
                    connection,
                    request,
                    selectedBotJobInstructionIds,
                    currentBotById,
                    generatedBotJobInstructionIds,
                    insertedRows);
            remapBotJobRelationships(
                    connection,
                    request.botJobId(),
                    currentBotById,
                    botBlocks,
                    generatedBotJobInstructionIds,
                    insertedRows);
            copyBotJobReferences(
                    connection,
                    request,
                    selectedBotJobInstructionIds,
                    generatedBotJobInstructionIds);
            copyComponentVariables(
                    connection,
                    request,
                    componentPlan.selectedInstructionIds(),
                    generatedComponentInstructionIds,
                    insertedRows);
            remapComponentRelationships(
                    connection,
                    request.botJobId(),
                    insertedRows,
                    generatedComponentInstructionIds,
                    generatedBlockIds);
            copyComponentReferences(
                    connection,
                    request,
                    componentPlan.selectedInstructionIds(),
                    generatedComponentInstructionIds);
            copyScannerReferences(
                    connection,
                    request,
                    generatedScannerInstructionsByItem);

            // A row covered by a selected block maps to that block's generated instruction and is
            // not duplicated into the target block.
            for (OrderedItem item : request.orderedItems()) {
                if (item.kind() == ItemKind.COMPONENT_INSTRUCTION) {
                    Integer generated =
                            generatedComponentInstructionIds.get(item.componentInstructionId());
                    if (generated != null) generatedInstructionsByItem.put(item.itemKey(), generated);
                }
            }

            List<InstructionRow> completeRows = new ArrayList<>(currentBotRows);
            completeRows.addAll(insertedRows);
            assertTargetLayoutUnchanged(connection, request.botJobId(), completeRows);
            List<Integer> orderedTargetIds = new ArrayList<>();
            Set<Integer> appendedTargetIds = new HashSet<>();
            for (OrderedItem item : request.orderedItems()) {
                switch (item.kind()) {
                    case BOT_JOB_INSTRUCTION -> {
                        Integer generated =
                                generatedBotJobInstructionsByItem.get(item.itemKey());
                        if (generated != null && appendedTargetIds.add(generated)) {
                            orderedTargetIds.add(generated);
                        }
                    }
                    case PAGE_SCANNER_INSTRUCTION -> {
                        Integer generated =
                                generatedScannerInstructionsByItem.get(item.itemKey());
                        if (generated != null && appendedTargetIds.add(generated)) {
                            orderedTargetIds.add(generated);
                        }
                    }
                    case COMPONENT_INSTRUCTION -> {
                        // Do not pull a row out of its newly cloned block when the same component
                        // block was selected. The block selection owns that row.
                        if (!componentPlan.coveredBySelectedBlock()
                                .contains(item.componentInstructionId())) {
                            Integer generated =
                                    generatedInstructionsByItem.get(item.itemKey());
                            if (generated != null && appendedTargetIds.add(generated)) {
                                orderedTargetIds.add(generated);
                            }
                        }
                    }
                    case COMPONENT_BLOCK -> {
                        // Whole blocks preserve their own block-local order.
                    }
                }
            }
            orderedTargetIds =
                    normalizeMemoryDependencyOrder(completeRows, orderedTargetIds);

            List<UpdatedRow> finalLayout =
                    finalLayout(completeRows, resolvedTargetBlockId, orderedTargetIds);
            if (layoutChanged(completeRows, finalLayout)) {
                String graphError = moveValidator.validate(
                        completeRows.stream().map(InstructionRow::asInstruction).toList(),
                        finalLayout);
                if (graphError != null) {
                    throw new ApplyRefused(
                            "Memory List order creates an invalid instruction graph: " + graphError);
                }
            }
            validateEveryConditionalBlock(completeRows, finalLayout);
            persistLayout(connection, request.botJobId(), completeRows, finalLayout);

            connection.commit();
            restoreAutoCommit(connection, previousAutoCommit);
            Map<String, Integer> generatedByItem = new LinkedHashMap<>();
            generatedByItem.putAll(generatedBotJobInstructionsByItem);
            generatedByItem.putAll(generatedInstructionsByItem);
            generatedByItem.putAll(generatedScannerInstructionsByItem);
            return new Result(
                    null,
                    true,
                    false,
                    request.orderedItems().size(),
                    Map.copyOf(generatedByItem),
                    Map.copyOf(generatedBlocksByItem),
                    createdTargetBlockId,
                    createdTargetBlockOrderNumber);
        } catch (ApplyRefused refused) {
            rollback(connection);
            restoreAutoCommit(connection, previousAutoCommit);
            return Result.failed(error(
                    "Memory List Apply Refused",
                    refused.getMessage(),
                    "No Bot Job data was changed."));
        } catch (SQLException | RuntimeException failure) {
            rollback(connection);
            restoreAutoCommit(connection, previousAutoCommit);
            return Result.failed(error(
                    "Memory List Apply Failed",
                    "No Bot Job data was changed.",
                    failure.getMessage()));
        }
    }

    private ErrorMessage validateRequest(Request request) {
        if (request == null) {
            return error("Memory List Apply Refused", "The apply request is required.", null);
        }
        if (request.botJobId() <= 0 || request.homeBankingId() <= 0) {
            return error(
                    "Memory List Apply Refused",
                    "A positive Bot Job and organization are required.",
                    null);
        }
        if (request.requestId() == null || request.requestId().isBlank()) {
            return error(
                    "Memory List Apply Refused", "A request ID is required for safe retry.", null);
        }
        if (request.orderedItems() == null || request.orderedItems().isEmpty()) {
            return error(
                    "Memory List Apply Refused", "No active Memory List rows are available.", null);
        }
        if (request.newTargetBlock() != null) {
            if (request.targetBlockId() > 0) {
                return error(
                        "Memory List Apply Refused",
                        "Choose either an existing target block or create a new one, not both.",
                        null);
            }
            String blockName = request.newTargetBlock().blockName();
            if (blockName == null || blockName.isBlank()) {
                return error(
                        "Memory List Apply Refused", "The new block name is required.", null);
            }
            if (blockName.length() > 256) {
                return error(
                        "Memory List Apply Refused",
                        "The new block name is too long.",
                        "Use 256 characters or fewer.");
            }
        }
        Set<String> keys = new HashSet<>();
        for (OrderedItem item : request.orderedItems()) {
            if (item == null || item.itemKey() == null || item.itemKey().isBlank()) {
                return error(
                        "Memory List Apply Refused", "A Memory List row has no stable key.", null);
            }
            if (!keys.add(item.itemKey())) {
                return error(
                        "Memory List Apply Refused",
                        "Memory List contains a duplicate row key.",
                        item.itemKey());
            }
            if (item.kind() == null) {
                return error(
                        "Memory List Apply Refused", "A Memory List row has no item kind.", null);
            }
            if (item.kind() == ItemKind.BOT_JOB_INSTRUCTION
                    && item.botJobInstructionId() <= 0) {
                return error(
                        "Memory List Apply Refused",
                        "A Bot Job Memory List row has an invalid instruction ID.",
                        item.itemKey());
            }
            if (item.kind() == ItemKind.COMPONENT_INSTRUCTION
                    && (item.componentInstructionId() <= 0
                            || item.componentBlockId() <= 0)) {
                return error(
                        "Memory List Apply Refused",
                        "A Components instruction row has invalid source IDs.",
                        item.itemKey());
            }
            if (item.kind() == ItemKind.COMPONENT_BLOCK && item.componentBlockId() <= 0) {
                return error(
                        "Memory List Apply Refused",
                        "A Components block row has an invalid source block ID.",
                        item.itemKey());
            }
            if (item.kind() == ItemKind.PAGE_SCANNER_INSTRUCTION) {
                InstructionLoad scanner = item.scannerInstruction();
                if (scanner == null) {
                    return error(
                            "Memory List Apply Refused",
                            "A Page Scanner row has no instruction data.",
                            item.itemKey());
                }
                if (scanner.getParentId() != null
                        || scanner.getParentBlockId() != null
                        || scanner.getVariableId() != null) {
                    return error(
                            "Memory List Apply Refused",
                            "A Page Scanner row contains unsupported command relationships.",
                            item.itemKey());
                }
            }
        }
        return null;
    }

    private void validateBotJobOwner(Connection connection, int botJobId, int homeBankingId)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "SELECT home_banking_id, active FROM bot_job WHERE id = ?")) {
            statement.setInt(1, botJobId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new ApplyRefused("The active Bot Job no longer exists.");
                int storedOwner = result.getInt("home_banking_id");
                if (result.wasNull() || storedOwner != homeBankingId) {
                    throw new ApplyRefused(
                            "The component organization does not own the active Bot Job.");
                }
                Object active = result.getObject("active");
                if ((active instanceof Number number && number.intValue() == 0)
                        || (active instanceof Boolean enabled && !enabled)) {
                    throw new ApplyRefused("The target Bot Job is inactive.");
                }
            }
        }
    }

    private ComponentSelectionPlan planComponentSelections(
            Request request,
            List<InstructionRow> allRows,
            Map<Integer, InstructionRow> byId,
            Map<Integer, BlockRow> blocks,
            List<VariableLoadDTO> variables,
            String currentRevision) {
        LinkedHashSet<Integer> selectedBlocks = new LinkedHashSet<>();
        LinkedHashSet<Integer> selectedInstructions = new LinkedHashSet<>();
        for (OrderedItem item : request.orderedItems()) {
            if (item.kind() != ItemKind.COMPONENT_BLOCK
                    && item.kind() != ItemKind.COMPONENT_INSTRUCTION) {
                continue;
            }
            if (item.sourceRevision() == null
                    || item.sourceRevision().isBlank()
                    || !item.sourceRevision().equals(currentRevision)) {
                throw new ApplyRefused(
                        "Components changed after they were added. Refresh Components and add them again.");
            }
            if (item.kind() == ItemKind.COMPONENT_BLOCK) {
                if (item.componentBlockId() <= 0
                        || !blocks.containsKey(item.componentBlockId())) {
                    throw new ApplyRefused(
                            "A selected component block no longer exists in this organization.");
                }
                selectedBlocks.add(item.componentBlockId());
            } else {
                InstructionRow row = byId.get(item.componentInstructionId());
                if (row == null) {
                    throw new ApplyRefused(
                            "A selected component instruction no longer exists in this organization.");
                }
                if (item.componentBlockId() > 0
                        && row.blockId() != item.componentBlockId()) {
                    throw new ApplyRefused(
                            "A component instruction changed blocks. Refresh Components and add it again.");
                }
                selectedInstructions.add(item.componentInstructionId());
            }
        }

        Map<Integer, List<InstructionRow>> rowsByBlock = new LinkedHashMap<>();
        for (InstructionRow row : allRows) {
            if (selectedBlocks.contains(row.blockId())) {
                rowsByBlock.computeIfAbsent(row.blockId(), ignored -> new ArrayList<>()).add(row);
            }
        }
        rowsByBlock.values().forEach(rows -> rows.sort(
                Comparator.comparingInt(InstructionRow::order).thenComparingInt(InstructionRow::id)));

        Set<Integer> covered = rowsByBlock.values().stream()
                .flatMap(List::stream)
                .map(InstructionRow::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        LinkedHashSet<Integer> selectedAll = new LinkedHashSet<>(covered);
        selectedAll.addAll(selectedInstructions);

        List<InstructionLoad> dependencyGraph =
                allRows.stream().map(InstructionRow::asInstruction).toList();
        for (Integer selectedInstructionId : selectedInstructions) {
            InstructionDependencyClosureService.Result closure =
                    dependencyClosureService.resolve(
                            dependencyGraph,
                            variables,
                            selectedInstructionId,
                            InstructionDependencyClosureService.Mode.COMPONENT_COPY);
            requireCompleteClosure(closure, selectedAll, selectedBlocks, "component");
        }
        for (Integer coveredInstructionId : covered) {
            InstructionDependencyClosureService.Result closure =
                    dependencyClosureService.resolve(
                            dependencyGraph,
                            variables,
                            coveredInstructionId,
                            InstructionDependencyClosureService.Mode.COMPONENT_COPY);
            requireCompleteClosure(closure, selectedAll, selectedBlocks, "component Block");
        }

        for (Integer instructionId : selectedAll) {
            InstructionRow row = byId.get(instructionId);
            if (row == null) {
                throw new ApplyRefused("A selected component instruction no longer exists.");
            }
            Integer parentId = row.parentId();
            if (parentId != null
                    && !parentId.equals(row.id())
                    && !selectedAll.contains(parentId)) {
                throw new ApplyRefused(
                        "A selected component command references an instruction that was not selected.");
            }
            Integer parentBlockId = row.parentBlockId();
            if (parentBlockId != null && !selectedBlocks.contains(parentBlockId)) {
                InstructionRow selectedParent =
                        parentId == null ? null : byId.get(parentId);
                boolean derivedParentBlock = selectedParent != null
                        && selectedAll.contains(selectedParent.id())
                        && selectedParent.blockId() == parentBlockId
                        && !CommandRegistry.isCrossBlockNavigation(
                                row.actions(), row.parentBlockId(), row.blockId());
                if (!derivedParentBlock) {
                    throw new ApplyRefused(
                            "A selected component GOTO references a block that was not selected.");
                }
            }
        }

        for (Integer instructionId : selectedAll) {
            InstructionRow consumer = byId.get(instructionId);
            if (consumer == null
                    || !VariableDefinitionPolicy.isConsumer(consumer.actions())) {
                continue;
            }
            if (consumer.variableId() == null) {
                throw new ApplyRefused(
                        "A selected variable consumer has no variable declaration.");
            }
            boolean hasSelectedProducer = selectedAll.stream()
                    .map(byId::get)
                    .filter(Objects::nonNull)
                    .anyMatch(candidate -> VariableDefinitionPolicy.isProducer(candidate.actions())
                            && Objects.equals(
                                    candidate.variableId(), consumer.variableId()));
            if (!hasSelectedProducer) {
                throw new ApplyRefused(
                        "A selected "
                                + CommandRegistry.canonicalize(consumer.actions())
                                + " command requires its matching GET producer in Memory List.");
            }
        }

        return new ComponentSelectionPlan(
                Set.copyOf(selectedAll),
                Set.copyOf(covered),
                Map.copyOf(rowsByBlock));
    }

    private Set<Integer> validateExistingSelections(
            Request request,
            Map<Integer, InstructionRow> currentBotById,
            List<InstructionRow> currentBotRows,
            List<VariableLoadDTO> variables) {
        Set<Integer> ids = new HashSet<>();
        for (OrderedItem item : request.orderedItems()) {
            if (item.kind() != ItemKind.BOT_JOB_INSTRUCTION) continue;
            if (item.botJobInstructionId() <= 0
                    || !ids.add(item.botJobInstructionId())
                    || !currentBotById.containsKey(item.botJobInstructionId())) {
                throw new ApplyRefused(
                        "An instruction in Memory List no longer exists. Refresh and try again.");
            }
        }
        List<InstructionLoad> dependencyGraph =
                currentBotRows.stream().map(InstructionRow::asInstruction).toList();
        for (Integer instructionId : ids) {
            InstructionRow selected = currentBotById.get(instructionId);
            if (selected != null
                    && "EXCEL GOTO".equals(
                            CommandRegistry.canonicalize(selected.actions()))) {
                throw new ApplyRefused(
                        "EXCEL GOTO cannot be copied inside the same Bot Job because only one "
                                + "EXCEL GOTO command is allowed.");
            }
            InstructionDependencyClosureService.Result closure =
                    dependencyClosureService.resolve(
                            dependencyGraph,
                            variables,
                            instructionId,
                            InstructionDependencyClosureService.Mode.BOT_JOB_COPY);
            requireCompleteClosure(closure, ids, Set.of(), "Bot Job");
        }
        return Set.copyOf(ids);
    }

    private void requireCompleteClosure(
            InstructionDependencyClosureService.Result closure,
            Set<Integer> selectedInstructionIds,
            Set<Integer> selectedBlockIds,
            String sourceLabel) {
        if (!closure.successful()) {
            throw new ApplyRefused(closure.error().message());
        }
        boolean missingInstruction = closure.orderedInstructions().stream()
                .map(InstructionLoad::getId)
                .anyMatch(id -> !selectedInstructionIds.contains(id));
        if (missingInstruction || !selectedBlockIds.containsAll(closure.requiredBlockIds())) {
            throw new ApplyRefused(
                    "The complete connected " + sourceLabel
                            + " instruction group must be present in Memory List.");
        }
    }

    private List<InstructionRow> loadInstructionRows(
            Connection connection, String sql, int ownerId, boolean component)
            throws SQLException {
        List<InstructionRow> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, ownerId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) rows.add(InstructionRow.fromResult(result, component));
            }
        }
        return rows;
    }

    private Map<Integer, BlockRow> loadBotBlocks(Connection connection, int botJobId)
            throws SQLException {
        return loadBlocks(
                connection,
                "SELECT id, block_order_number, name, description, type_id, export_file, active, wait "
                        + "FROM block WHERE bot_job_id = ? ORDER BY block_order_number, id",
                botJobId);
    }

    private Map<Integer, BlockRow> loadComponentBlocks(
            Connection connection, int homeBankingId) throws SQLException {
        return loadBlocks(
                connection,
                "SELECT id, block_order_number, name, description, type_id, export_file, active, wait "
                        + "FROM component_block WHERE home_banking_id = ? ORDER BY block_order_number, id",
                homeBankingId);
    }

    private List<VariableLoadDTO> loadDependencyVariables(
            Connection connection, boolean component, int ownerId, int homeBankingId)
            throws SQLException {
        String table = component ? "component_variable" : "variable";
        String ownerColumn = component ? "home_banking_id" : "bot_job_id";
        String sql = "SELECT id, instruction_id FROM " + table
                + " WHERE " + ownerColumn + " = ? ORDER BY id";
        List<VariableLoadDTO> variables = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, ownerId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    variables.add(new VariableLoadDTO(
                            result.getInt("id"),
                            homeBankingId,
                            component ? null : ownerId,
                            nullableInteger(result, "instruction_id"),
                            null,
                            null,
                            null,
                            null,
                            null,
                            0));
                }
            }
        }
        return List.copyOf(variables);
    }

    private Map<Integer, BlockRow> loadBlocks(Connection connection, String sql, int ownerId)
            throws SQLException {
        Map<Integer, BlockRow> rows = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, ownerId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    BlockRow row = new BlockRow(
                            result.getInt("id"),
                            result.getString("name"),
                            result.getString("description"),
                            nullableInteger(result, "type_id"),
                            result.getString("export_file"),
                            nullableInteger(result, "active"),
                            nullableInteger(result, "wait"),
                            result.getInt("block_order_number"));
                    rows.put(row.id(), row);
                }
            }
        }
        return rows;
    }

    private int insertBlock(
            PreparedStatement statement, BlockRow source, int order, int botJobId)
            throws SQLException {
        int parameter = 1;
        statement.setInt(parameter++, order);
        statement.setString(parameter++, source.name());
        statement.setString(parameter++, source.description());
        statement.setObject(parameter++, source.typeId());
        statement.setString(parameter++, source.exportFile());
        statement.setObject(parameter++, source.active() == null ? 1 : source.active());
        statement.setObject(parameter++, source.waitSeconds());
        statement.setInt(parameter, botJobId);
        if (statement.executeUpdate() != 1) {
            throw new SQLException("Component block insert did not create exactly one row.");
        }
        return generatedId(statement, "component block");
    }

    private int insertInstruction(
            PreparedStatement statement,
            InstructionRow source,
            int order,
            int blockId,
            int botJobId)
            throws SQLException {
        int parameter = 1;
        statement.setInt(parameter++, order);
        statement.setObject(parameter++, source.actions());
        statement.setObject(parameter++, source.name());
        statement.setObject(parameter++, source.xpath());
        statement.setObject(parameter++, source.coordinates());
        statement.setObject(parameter++, source.forceCoordinates());
        statement.setObject(parameter++, source.iframeXPath());
        statement.setObject(parameter++, source.tagName());
        statement.setObject(parameter++, source.shadowHost());
        statement.setObject(parameter++, source.shadowRoot());
        statement.setObject(parameter++, source.cssSelector());
        statement.setObject(parameter++, source.description());
        statement.setObject(parameter++, source.operation());
        statement.setObject(parameter++, source.optional());
        statement.setObject(parameter++, source.blockMarked());
        statement.setObject(parameter++, source.defaultValue());
        statement.setObject(parameter++, source.actionCustomMaxWaitSec());
        statement.setObject(parameter++, source.onHoldSeconds());
        statement.setObject(parameter++, source.codified());
        statement.setObject(parameter++, source.exportToAbr());
        statement.setObject(parameter++, source.active() == null ? 1 : source.active());
        statement.setInt(parameter++, blockId);
        statement.setNull(parameter++, Types.INTEGER);
        statement.setNull(parameter++, Types.INTEGER);
        statement.setNull(parameter++, Types.INTEGER);
        statement.setInt(parameter++, botJobId);
        statement.setObject(parameter, source.clientNamed());
        if (statement.executeUpdate() != 1) {
            throw new SQLException("Instruction insert did not create exactly one row.");
        }
        return generatedId(statement, "instruction");
    }

    private int generatedId(PreparedStatement statement, String entity) throws SQLException {
        try (ResultSet keys = statement.getGeneratedKeys()) {
            if (!keys.next()) throw new SQLException(entity + " insert returned no generated ID.");
            return keys.getInt(1);
        }
    }

    private void copyBotJobVariables(
            Connection connection,
            Request request,
            Set<Integer> selectedInstructionIds,
            Map<Integer, InstructionRow> sourceInstructions,
            Map<Integer, Integer> generatedInstructionIds,
            List<InstructionRow> insertedRows)
            throws SQLException {
        if (selectedInstructionIds.isEmpty()) return;

        List<VariableCopyRow> sourceVariables = new ArrayList<>();
        String select = "SELECT id, type, name, value, local_format, delimiter, instruction_id "
                + "FROM variable WHERE bot_job_id = ? ORDER BY id";
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setInt(1, request.botJobId());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Integer sourceInstructionId = nullableInteger(result, "instruction_id");
                    if (sourceInstructionId == null
                            || !selectedInstructionIds.contains(sourceInstructionId)) {
                        continue;
                    }
                    sourceVariables.add(new VariableCopyRow(
                            result.getInt("id"),
                            result.getObject("type"),
                            result.getObject("name"),
                            result.getObject("value"),
                            result.getObject("local_format"),
                            result.getObject("delimiter"),
                            sourceInstructionId));
                }
            }
        }

        String insert = "INSERT INTO variable "
                + "(type, name, value, local_format, delimiter, instruction_id, bot_job_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        Map<Integer, Integer> generatedVariableIds = new LinkedHashMap<>();
        try (PreparedStatement statement =
                connection.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
            for (VariableCopyRow source : sourceVariables) {
                Integer generatedOwnerId =
                        generatedInstructionIds.get(source.instructionId());
                if (generatedOwnerId == null) {
                    throw new ApplyRefused(
                            "A Bot Job variable owner was not copied with its connected group.");
                }
                statement.setObject(1, source.type());
                statement.setObject(2, source.name());
                statement.setObject(3, source.value());
                statement.setObject(4, source.localFormat());
                statement.setObject(5, source.delimiter());
                statement.setInt(6, generatedOwnerId);
                statement.setInt(7, request.botJobId());
                if (statement.executeUpdate() != 1) {
                    throw new SQLException(
                            "Bot Job variable copy did not create exactly one row.");
                }
                generatedVariableIds.put(
                        source.id(), generatedId(statement, "Bot Job variable"));
            }
        }

        for (Map.Entry<Integer, Integer> generated :
                generatedInstructionIds.entrySet()) {
            InstructionRow source = sourceInstructions.get(generated.getKey());
            if (source == null || source.variableId() == null) continue;
            Integer generatedVariableId = generatedVariableIds.get(source.variableId());
            if (generatedVariableId == null) {
                throw new ApplyRefused(
                        "A copied Bot Job instruction references a variable that was not copied.");
            }
            replaceInsertedRow(
                    insertedRows,
                    generated.getValue(),
                    row -> row.withVariableId(generatedVariableId));
        }
    }

    private void remapBotJobRelationships(
            Connection connection,
            int botJobId,
            Map<Integer, InstructionRow> sourceInstructions,
            Map<Integer, BlockRow> botBlocks,
            Map<Integer, Integer> generatedInstructionIds,
            List<InstructionRow> insertedRows)
            throws SQLException {
        String update = "UPDATE instruction "
                + "SET variable_id = ?, parent_block_id = ?, parent_id = ? "
                + "WHERE id = ? AND bot_job_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(update)) {
            for (Map.Entry<Integer, Integer> generated :
                    generatedInstructionIds.entrySet()) {
                InstructionRow source = sourceInstructions.get(generated.getKey());
                if (source == null) {
                    throw new ApplyRefused(
                            "A Bot Job instruction disappeared while its copy was being prepared.");
                }
                InstructionRow inserted =
                        findInsertedRow(insertedRows, generated.getValue());

                boolean crossBlockNavigation = CommandRegistry.isCrossBlockNavigation(
                        source.actions(), source.parentBlockId(), source.blockId());
                Integer generatedParentId = null;
                Integer generatedParentBlockId = null;
                if (crossBlockNavigation) {
                    generatedParentId = source.parentId();
                    generatedParentBlockId = source.parentBlockId();
                    if (generatedParentBlockId == null
                            || !botBlocks.containsKey(generatedParentBlockId)) {
                        throw new ApplyRefused(
                                "A copied Bot Job GOTO references a block that no longer exists.");
                    }
                    if (inserted.blockId() == generatedParentBlockId) {
                        throw new ApplyRefused(
                                "A copied Bot Job GOTO cannot use its own destination block as "
                                        + "the Memory List target.");
                    }
                    if (generatedParentId != null) {
                        InstructionRow target = sourceInstructions.get(generatedParentId);
                        if (target == null
                                || target.blockId() != generatedParentBlockId) {
                            throw new ApplyRefused(
                                    "A copied Bot Job GOTO references an instruction that no longer exists.");
                        }
                    }
                } else {
                    if (source.parentId() != null) {
                        generatedParentId =
                                generatedInstructionIds.get(source.parentId());
                        if (generatedParentId == null) {
                            throw new ApplyRefused(
                                    "A copied Bot Job instruction references a parent that was not copied.");
                        }
                    }
                    if (source.parentId() != null || source.parentBlockId() != null) {
                        generatedParentBlockId = inserted.blockId();
                    }
                }

                bindNullableInteger(statement, 1, inserted.variableId());
                bindNullableInteger(statement, 2, generatedParentBlockId);
                bindNullableInteger(statement, 3, generatedParentId);
                statement.setInt(4, inserted.id());
                statement.setInt(5, botJobId);
                if (statement.executeUpdate() != 1) {
                    throw new SQLException(
                            "Copied Bot Job instruction could not be relationship-remapped.");
                }
                Integer remappedParentId = generatedParentId;
                Integer remappedParentBlockId = generatedParentBlockId;
                replaceInsertedRow(
                        insertedRows,
                        inserted.id(),
                        row -> row.withRelations(
                                remappedParentId, remappedParentBlockId));
            }
        }
    }

    private void copyBotJobReferences(
            Connection connection,
            Request request,
            Set<Integer> selectedInstructionIds,
            Map<Integer, Integer> generatedInstructionIds)
            throws SQLException {
        if (selectedInstructionIds.isEmpty()) return;

        List<ReferenceCopyRow> sourceReferences = new ArrayList<>();
        String select = "SELECT reference_type, value, instruction_id "
                + "FROM reference WHERE bot_job_id = ? ORDER BY id";
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setInt(1, request.botJobId());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Integer sourceInstructionId =
                            nullableInteger(result, "instruction_id");
                    if (sourceInstructionId == null
                            || !selectedInstructionIds.contains(sourceInstructionId)) {
                        continue;
                    }
                    sourceReferences.add(new ReferenceCopyRow(
                            result.getObject("reference_type"),
                            result.getObject("value"),
                            sourceInstructionId));
                }
            }
        }

        String insert = "INSERT INTO reference "
                + "(reference_type, value, instruction_id, bot_job_id) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(insert)) {
            for (ReferenceCopyRow source : sourceReferences) {
                Integer generatedInstructionId =
                        generatedInstructionIds.get(source.instructionId());
                if (generatedInstructionId == null) {
                    throw new ApplyRefused(
                            "A Bot Job locator reference belongs to an instruction that was not copied.");
                }
                statement.setObject(1, source.referenceType());
                statement.setObject(2, source.value());
                statement.setInt(3, generatedInstructionId);
                statement.setInt(4, request.botJobId());
                if (statement.executeUpdate() != 1) {
                    throw new SQLException(
                            "Bot Job reference copy did not create exactly one row.");
                }
            }
        }
    }

    private InstructionRow findInsertedRow(List<InstructionRow> rows, int instructionId) {
        return rows.stream()
                .filter(row -> row.id() == instructionId)
                .findFirst()
                .orElseThrow(() -> new ApplyRefused(
                        "A generated instruction is missing from the pending Memory List copy."));
    }

    private void replaceInsertedRow(
            List<InstructionRow> rows,
            int instructionId,
            java.util.function.UnaryOperator<InstructionRow> update) {
        for (int index = 0; index < rows.size(); index++) {
            InstructionRow row = rows.get(index);
            if (row.id() != instructionId) continue;
            rows.set(index, update.apply(row));
            return;
        }
        throw new ApplyRefused(
                "A generated instruction is missing from the pending Memory List copy.");
    }

    private void copyComponentVariables(
            Connection connection,
            Request request,
            Set<Integer> selectedInstructionIds,
            Map<Integer, Integer> instructionIds,
            List<InstructionRow> insertedRows)
            throws SQLException {
        if (selectedInstructionIds.isEmpty()) return;
        String select = "SELECT id, type, name, value, local_format, delimiter, instruction_id "
                + "FROM component_variable WHERE home_banking_id = ? ORDER BY id";
        String insert = "INSERT INTO variable "
                + "(type, name, value, local_format, delimiter, instruction_id, bot_job_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        Map<Integer, Integer> variableIds = new HashMap<>();
        try (PreparedStatement selectStatement = connection.prepareStatement(select);
                PreparedStatement insertStatement =
                        connection.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
            selectStatement.setInt(1, request.homeBankingId());
            try (ResultSet result = selectStatement.executeQuery()) {
                while (result.next()) {
                    int sourceInstructionId = result.getInt("instruction_id");
                    if (result.wasNull()
                            || !selectedInstructionIds.contains(sourceInstructionId)) {
                        continue;
                    }
                    Integer generatedInstructionId = instructionIds.get(sourceInstructionId);
                    if (generatedInstructionId == null) {
                        throw new ApplyRefused(
                                "A component variable references an instruction that was not selected.");
                    }
                    int insertParameter = 1;
                    insertStatement.setObject(insertParameter++, result.getObject("type"));
                    insertStatement.setObject(insertParameter++, result.getObject("name"));
                    insertStatement.setObject(insertParameter++, result.getObject("value"));
                    insertStatement.setObject(insertParameter++, result.getObject("local_format"));
                    insertStatement.setObject(insertParameter++, result.getObject("delimiter"));
                    insertStatement.setInt(insertParameter++, generatedInstructionId);
                    insertStatement.setInt(insertParameter, request.botJobId());
                    if (insertStatement.executeUpdate() != 1) {
                        throw new SQLException(
                                "Component variable insert did not create exactly one row.");
                    }
                    variableIds.put(result.getInt("id"), generatedId(insertStatement, "variable"));
                }
            }
        }
        for (int index = 0; index < insertedRows.size(); index++) {
            InstructionRow row = insertedRows.get(index);
            if (row.sourceComponentId() == null || row.variableId() == null) continue;
            Integer generatedVariableId = variableIds.get(row.variableId());
            if (generatedVariableId == null) {
                throw new ApplyRefused(
                        "A selected component instruction references a variable that was not copied.");
            }
            insertedRows.set(index, row.withVariableId(generatedVariableId));
        }
    }

    private void remapComponentRelationships(
            Connection connection,
            int botJobId,
            List<InstructionRow> insertedRows,
            Map<Integer, Integer> instructionIds,
            Map<Integer, Integer> blockIds)
            throws SQLException {
        Map<Integer, Integer> destinationBlockBySourceInstruction = new HashMap<>();
        for (InstructionRow row : insertedRows) {
            if (row.sourceComponentId() != null) {
                destinationBlockBySourceInstruction.put(row.sourceComponentId(), row.blockId());
            }
        }
        String update = "UPDATE instruction "
                + "SET variable_id = ?, parent_block_id = ?, parent_id = ? "
                + "WHERE id = ? AND bot_job_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(update)) {
            for (int index = 0; index < insertedRows.size(); index++) {
                InstructionRow row = insertedRows.get(index);
                if (row.sourceComponentId() == null) continue;
                Integer parentId = row.parentId() == null
                        ? null
                        : instructionIds.get(row.parentId());
                if (row.parentId() != null && parentId == null) {
                    throw new ApplyRefused(
                            "A copied component instruction references a parent that was not copied.");
                }
                Integer parentBlockId = null;
                if (row.parentBlockId() != null) {
                    parentBlockId = blockIds.get(row.parentBlockId());
                    if (parentBlockId == null && row.parentId() != null) {
                        parentBlockId =
                                destinationBlockBySourceInstruction.get(row.parentId());
                    }
                    if (parentBlockId == null) {
                        throw new ApplyRefused(
                                "A copied component instruction references a Block that was not copied.");
                    }
                } else if (row.parentId() != null) {
                    // Legacy Component rows may have a valid parent_id but a null
                    // parent_block_id. Normalize only the generated Bot Job clone; the reusable
                    // Component source remains untouched.
                    parentBlockId =
                            destinationBlockBySourceInstruction.get(row.parentId());
                    if (parentBlockId == null) {
                        throw new ApplyRefused(
                                "A copied component instruction references a parent Block that was not copied.");
                    }
                }
                bindNullableInteger(statement, 1, row.variableId());
                bindNullableInteger(statement, 2, parentBlockId);
                bindNullableInteger(statement, 3, parentId);
                statement.setInt(4, row.id());
                statement.setInt(5, botJobId);
                if (statement.executeUpdate() != 1) {
                    throw new SQLException(
                            "Copied component instruction could not be relationship-remapped.");
                }
                insertedRows.set(index, row.withRelations(parentId, parentBlockId));
            }
        }
    }

    private void copyComponentReferences(
            Connection connection,
            Request request,
            Set<Integer> selectedInstructionIds,
            Map<Integer, Integer> instructionIds)
            throws SQLException {
        if (selectedInstructionIds.isEmpty()) return;
        String select = "SELECT reference_type, value, instruction_id "
                + "FROM component_reference WHERE home_banking_id = ? ORDER BY id";
        String insert = "INSERT INTO reference "
                + "(reference_type, value, instruction_id, bot_job_id) VALUES (?, ?, ?, ?)";
        try (PreparedStatement selectStatement = connection.prepareStatement(select);
                PreparedStatement insertStatement = connection.prepareStatement(insert)) {
            selectStatement.setInt(1, request.homeBankingId());
            try (ResultSet result = selectStatement.executeQuery()) {
                while (result.next()) {
                    int sourceInstructionId = result.getInt("instruction_id");
                    if (result.wasNull()
                            || !selectedInstructionIds.contains(sourceInstructionId)) {
                        continue;
                    }
                    Integer generatedId = instructionIds.get(sourceInstructionId);
                    if (generatedId == null) {
                        throw new ApplyRefused(
                                "A component locator reference belongs to an instruction that was not selected.");
                    }
                    insertStatement.setObject(1, result.getObject("reference_type"));
                    insertStatement.setObject(2, result.getObject("value"));
                    insertStatement.setInt(3, generatedId);
                    insertStatement.setInt(4, request.botJobId());
                    if (insertStatement.executeUpdate() != 1) {
                        throw new SQLException(
                                "Component reference insert did not create exactly one row.");
                    }
                }
            }
        }
    }

    private void copyScannerReferences(
            Connection connection,
            Request request,
            Map<String, Integer> generatedByItem)
            throws SQLException {
        String insert = "INSERT INTO reference "
                + "(reference_type, value, instruction_id, bot_job_id) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(insert)) {
            for (OrderedItem item : request.orderedItems()) {
                if (item.kind() != ItemKind.PAGE_SCANNER_INSTRUCTION
                        || item.scannerInstruction() == null
                        || item.scannerInstruction().getReferenceLoadDTOList() == null) {
                    continue;
                }
                int generatedId = generatedByItem.get(item.itemKey());
                for (ReferenceLoadDTO reference :
                        item.scannerInstruction().getReferenceLoadDTOList()) {
                    if (reference == null
                            || "customXPath".equalsIgnoreCase(reference.getReferenceType())) {
                        continue;
                    }
                    statement.setObject(1, reference.getReferenceType());
                    statement.setObject(2, reference.getValue());
                    statement.setInt(3, generatedId);
                    statement.setInt(4, request.botJobId());
                    if (statement.executeUpdate() != 1) {
                        throw new SQLException(
                                "Page Scanner reference insert did not create exactly one row.");
                    }
                }
            }
        }
    }

    private List<UpdatedRow> finalLayout(
            List<InstructionRow> rows, int targetBlockId, List<Integer> orderedSelectedIds) {
        Set<Integer> selected = new HashSet<>(orderedSelectedIds);
        Map<Integer, InstructionRow> byId = indexInstructions(rows);
        int nextTargetOrder = rows.stream()
                        .filter(row -> row.blockId() == targetBlockId)
                        .filter(row -> !selected.contains(row.id()))
                        .map(InstructionRow::order)
                        .max(Integer::compareTo)
                        .orElse(0)
                + 1;
        Map<Integer, UpdatedRow> selectedUpdates = new LinkedHashMap<>();
        for (Integer instructionId : orderedSelectedIds) {
            if (!byId.containsKey(instructionId)) continue;
            UpdatedRow update = new UpdatedRow();
            update.setInstructionId(instructionId);
            update.setBlockId(targetBlockId);
            update.setInstructionOrderNumber(nextTargetOrder++);
            selectedUpdates.put(instructionId, update);
        }

        // Preserve every source and unrelated destination row exactly. Memory List Apply is a
        // clone/insert workflow; only generated rows may receive a new position here.
        List<UpdatedRow> updates = new ArrayList<>();
        for (InstructionRow row : rows.stream()
                .sorted(Comparator.comparingInt(InstructionRow::blockId)
                        .thenComparingInt(InstructionRow::order)
                        .thenComparingInt(InstructionRow::id))
                .toList()) {
            UpdatedRow update = selectedUpdates.get(row.id());
            if (update == null) {
                update = new UpdatedRow();
                update.setInstructionId(row.id());
                update.setBlockId(row.blockId());
                update.setInstructionOrderNumber(row.order());
            }
            updates.add(update);
        }
        return updates;
    }

    /**
     * Keeps execution dependencies valid even when the detached Memory List was manually reordered.
     * Unrelated rows retain their requested order; only a dependent family is corrected.
     */
    private List<Integer> normalizeMemoryDependencyOrder(
            List<InstructionRow> rows, List<Integer> requestedIds) {
        List<Integer> ordered = new ArrayList<>(new LinkedHashSet<>(requestedIds));
        Map<Integer, InstructionRow> byId = indexInstructions(rows);
        Set<String> nonWebFieldActions =
                Set.of("IF", "ELSEIF", "ELSE", "ENDIF", "LOOP", "REFRESH_LOOP");

        // A Web Field must execute before every command that references it.
        for (int pass = 0; pass < ordered.size(); pass++) {
            boolean changed = false;
            for (Integer childId : List.copyOf(ordered)) {
                InstructionRow child = byId.get(childId);
                if (child == null
                        || child.parentId() == null
                        || nonWebFieldActions.contains(normalizedAction(child.actions()))) {
                    continue;
                }
                int parentIndex = ordered.indexOf(child.parentId());
                int childIndex = ordered.indexOf(childId);
                if (parentIndex >= 0 && childIndex >= 0 && parentIndex > childIndex) {
                    Integer parentId = ordered.remove(parentIndex);
                    ordered.add(childIndex, parentId);
                    changed = true;
                }
            }
            if (!changed) break;
        }

        // GET produces the variable consumed by Extract and Check commands.
        for (Integer consumerId : List.copyOf(ordered)) {
            InstructionRow consumer = byId.get(consumerId);
            if (consumer == null
                    || !VariableDefinitionPolicy.isConsumer(consumer.actions())
                    || consumer.variableId() == null) {
                continue;
            }
            Integer producerId = ordered.stream()
                    .map(byId::get)
                    .filter(Objects::nonNull)
                    .filter(row -> VariableDefinitionPolicy.isProducer(row.actions()))
                    .filter(row -> Objects.equals(row.variableId(), consumer.variableId()))
                    .map(InstructionRow::id)
                    .findFirst()
                    .orElse(null);
            int producerIndex = producerId == null ? -1 : ordered.indexOf(producerId);
            int consumerIndex = ordered.indexOf(consumerId);
            if (producerIndex > consumerIndex && consumerIndex >= 0) {
                ordered.remove(producerIndex);
                ordered.add(consumerIndex, producerId);
            }
        }
        return ordered;
    }

    private String normalizedAction(String action) {
        return action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
    }

    private boolean layoutChanged(List<InstructionRow> rows, List<UpdatedRow> updates) {
        Map<Integer, InstructionRow> byId = indexInstructions(rows);
        for (UpdatedRow update : updates) {
            InstructionRow current = byId.get(update.getInstructionId());
            if (current == null
                    || current.blockId() != update.getBlockId()
                    || current.order() != update.getInstructionOrderNumber()) {
                return true;
            }
        }
        return false;
    }

    private void validateEveryConditionalBlock(
            List<InstructionRow> rows, List<UpdatedRow> layout) {
        Map<Integer, UpdatedRow> updates = new HashMap<>();
        for (UpdatedRow row : layout) updates.put(row.getInstructionId(), row);
        Map<Integer, List<InstructionLoad>> blocks = new HashMap<>();
        for (InstructionRow row : rows) {
            UpdatedRow update = updates.get(row.id());
            InstructionLoad graph = row.asInstruction();
            if (update != null) {
                graph.setBlockId(update.getBlockId());
                graph.setInstructionOrderNumber(update.getInstructionOrderNumber());
            }
            blocks.computeIfAbsent(graph.getBlockId(), ignored -> new ArrayList<>()).add(graph);
        }
        for (List<InstructionLoad> block : blocks.values()) {
            block.sort(Comparator.comparingInt(InstructionLoad::getInstructionOrderNumber));
            String error = conditionalValidator.validate(block);
            if (error != null) {
                throw new ApplyRefused(
                        "Memory List creates an invalid conditional graph: " + error);
            }
        }
    }

    private void assertTargetLayoutUnchanged(
            Connection connection, int botJobId, List<InstructionRow> expectedRows)
            throws SQLException {
        List<InstructionRow> current =
                loadInstructionRows(connection, BOT_INSTRUCTION_SELECT, botJobId, false);
        Map<Integer, InstructionRow> expectedById = indexInstructions(expectedRows);
        Map<Integer, InstructionRow> currentById = indexInstructions(current);
        if (!expectedById.keySet().equals(currentById.keySet())) {
            throw new ApplyRefused(
                    "Bot Job instructions changed while Memory List was applying. Refresh and retry.");
        }
        for (Map.Entry<Integer, InstructionRow> entry : expectedById.entrySet()) {
            InstructionRow expected = entry.getValue();
            InstructionRow actual = currentById.get(entry.getKey());
            if (actual == null
                    || actual.blockId() != expected.blockId()
                    || actual.order() != expected.order()) {
                throw new ApplyRefused(
                        "Bot Job instruction order changed while Memory List was applying. Refresh and retry.");
            }
        }
    }

    private void persistLayout(
            Connection connection,
            int botJobId,
            List<InstructionRow> expectedRows,
            List<UpdatedRow> layout)
            throws SQLException {
        String update = "UPDATE instruction SET instruction_order_number = ?, block_id = ?, "
                + "parent_block_id = ? "
                + "WHERE id = ? AND bot_job_id = ? "
                + "AND instruction_order_number = ? AND block_id = ?";
        Map<Integer, InstructionRow> expectedById = indexInstructions(expectedRows);
        try (PreparedStatement statement = connection.prepareStatement(update)) {
            for (UpdatedRow row : layout) {
                InstructionRow expected = expectedById.get(row.getInstructionId());
                if (expected == null) {
                    throw new SQLException(
                            "Instruction " + row.getInstructionId()
                                    + " was not part of the validated Memory List graph.");
                }
                if (expected.order() == row.getInstructionOrderNumber()
                        && expected.blockId() == row.getBlockId()) {
                    // Memory Apply must never normalize or repair an unchanged source row. Every
                    // relationship on generated rows was already persisted by the copy/remap
                    // phase, so an unchanged position is a complete no-op here.
                    continue;
                }
                statement.setInt(1, row.getInstructionOrderNumber());
                statement.setInt(2, row.getBlockId());
                Integer parentBlockId = expected.parentBlockId();
                if (expected.parentId() != null
                        && !CommandRegistry.isCrossBlockNavigation(
                                expected.actions(),
                                expected.parentBlockId(),
                                expected.blockId())) {
                    parentBlockId = row.getBlockId();
                }
                bindNullableInteger(statement, 3, parentBlockId);
                statement.setInt(4, row.getInstructionId());
                statement.setInt(5, botJobId);
                statement.setInt(6, expected.order());
                statement.setInt(7, expected.blockId());
                if (statement.executeUpdate() != 1) {
                    throw new ApplyRefused(
                            "Instruction " + row.getInstructionId()
                                    + " changed while Memory List was applying. Refresh and retry.");
                }
            }
        }
    }

    private Map<Integer, InstructionRow> indexInstructions(List<InstructionRow> rows) {
        Map<Integer, InstructionRow> indexed = new LinkedHashMap<>();
        for (InstructionRow row : rows) indexed.put(row.id(), row);
        return indexed;
    }

    private static Integer nullableInteger(ResultSet result, String column) throws SQLException {
        Object value = result.getObject(column);
        return value == null ? null : ((Number) value).intValue();
    }

    private static void bindNullableInteger(
            PreparedStatement statement, int parameter, Integer value) throws SQLException {
        if (value == null) statement.setNull(parameter, Types.INTEGER);
        else statement.setInt(parameter, value);
    }

    private static void rollback(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // The original failure remains the useful client-facing cause.
        }
    }

    private static void restoreAutoCommit(Connection connection, boolean autoCommit) {
        try {
            connection.setAutoCommit(autoCommit);
        } catch (SQLException ignored) {
            // The connection is immediately closed by the production caller.
        }
    }

    private static ErrorMessage error(String title, String header, String detail) {
        return new ErrorMessage(title, header, detail == null ? "" : detail);
    }

    @FunctionalInterface
    interface ConnectionProvider {
        Connection open() throws SQLException;
    }

    public enum ItemKind {
        BOT_JOB_INSTRUCTION,
        PAGE_SCANNER_INSTRUCTION,
        COMPONENT_INSTRUCTION,
        COMPONENT_BLOCK;

        public static ItemKind componentKind(String value) {
            String normalized =
                    value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
            return switch (normalized) {
                case "INSTRUCTION" -> COMPONENT_INSTRUCTION;
                case "BLOCK" -> COMPONENT_BLOCK;
                default -> null;
            };
        }
    }

    public record OrderedItem(
            String itemKey,
            ItemKind kind,
            int botJobInstructionId,
            int componentInstructionId,
            int componentBlockId,
            String sourceRevision,
            InstructionLoad scannerInstruction) {

        public static OrderedItem botJob(String itemKey, int instructionId) {
            return new OrderedItem(
                    itemKey,
                    ItemKind.BOT_JOB_INSTRUCTION,
                    instructionId,
                    -1,
                    -1,
                    "",
                    null);
        }

        public static OrderedItem scanner(String itemKey, InstructionLoad instruction) {
            return new OrderedItem(
                    itemKey,
                    ItemKind.PAGE_SCANNER_INSTRUCTION,
                    -1,
                    -1,
                    -1,
                    "",
                    instruction);
        }

        public static OrderedItem componentInstruction(
                String itemKey,
                int instructionId,
                int blockId,
                String sourceRevision) {
            return new OrderedItem(
                    itemKey,
                    ItemKind.COMPONENT_INSTRUCTION,
                    -1,
                    instructionId,
                    blockId,
                    sourceRevision,
                    null);
        }

        public static OrderedItem componentBlock(
                String itemKey, int blockId, String sourceRevision) {
            return new OrderedItem(
                    itemKey,
                    ItemKind.COMPONENT_BLOCK,
                    -1,
                    -1,
                    blockId,
                    sourceRevision,
                    null);
        }
    }

    public record NewTargetBlock(
            String blockName,
            BlockCreationService.Position position,
            Integer beforeBlockId,
            Integer beforeBlockOrderNumber) {
        public NewTargetBlock {
            blockName = blockName == null ? "" : blockName.trim();
            position = position == null ? BlockCreationService.Position.END : position;
        }
    }

    public record Request(
            String requestId,
            int botJobId,
            int homeBankingId,
            int targetBlockId,
            List<OrderedItem> orderedItems,
            NewTargetBlock newTargetBlock) {
        public Request {
            orderedItems = orderedItems == null ? List.of() : List.copyOf(orderedItems);
        }

        public Request(
                String requestId,
                int botJobId,
                int homeBankingId,
                int targetBlockId,
                List<OrderedItem> orderedItems) {
            this(requestId, botJobId, homeBankingId, targetBlockId, orderedItems, null);
        }
    }

    public record Result(
            ErrorMessage error,
            boolean committed,
            boolean duplicate,
            int appliedCount,
            Map<String, Integer> generatedInstructionIds,
            Map<String, Integer> generatedBlockIds,
            int createdTargetBlockId,
            int createdTargetBlockOrderNumber) {

        static Result failed(ErrorMessage error) {
            return new Result(error, false, false, 0, Map.of(), Map.of(), -1, -1);
        }

        Result asDuplicate() {
            return new Result(
                    error,
                    committed,
                    true,
                    appliedCount,
                    generatedInstructionIds,
                    generatedBlockIds,
                    createdTargetBlockId,
                    createdTargetBlockOrderNumber);
        }
    }

    private record ComponentSelectionPlan(
            Set<Integer> selectedInstructionIds,
            Set<Integer> coveredBySelectedBlock,
            Map<Integer, List<InstructionRow>> rowsBySelectedBlock) {}

    private record CompletedRequest(String fingerprint, Result result) {}

    private record VariableCopyRow(
            int id,
            Object type,
            Object name,
            Object value,
            Object localFormat,
            Object delimiter,
            int instructionId) {}

    private record ReferenceCopyRow(
            Object referenceType, Object value, int instructionId) {}

    private record BlockRow(
            int id,
            String name,
            String description,
            Integer typeId,
            String exportFile,
            Integer active,
            Integer waitSeconds,
            int order) {}

    private record InstructionRow(
            int id,
            Integer sourceComponentId,
            int order,
            String actions,
            String name,
            String xpath,
            String coordinates,
            Object forceCoordinates,
            String iframeXPath,
            String tagName,
            String shadowHost,
            String shadowRoot,
            String cssSelector,
            String description,
            String operation,
            Object optional,
            Object blockMarked,
            String defaultValue,
            Object actionCustomMaxWaitSec,
            Object onHoldSeconds,
            Object codified,
            Object exportToAbr,
            Object active,
            int blockId,
            Integer variableId,
            Integer parentBlockId,
            Integer parentId,
            String clientNamed,
            List<ReferenceLoadDTO> scannerReferences) {

        static InstructionRow fromResult(ResultSet result, boolean component)
                throws SQLException {
            int id = result.getInt("id");
            return new InstructionRow(
                    id,
                    component ? id : null,
                    result.getInt("instruction_order_number"),
                    result.getString("actions"),
                    result.getString("name"),
                    result.getString("xpath"),
                    result.getString("coordinates"),
                    result.getObject("force_coordinates"),
                    result.getString("iframe_xpath"),
                    result.getString("tag_name"),
                    result.getString("shadow_host"),
                    result.getString("shadow_root"),
                    result.getString("css_selector"),
                    result.getString("description"),
                    result.getString("operation"),
                    result.getObject("optional"),
                    result.getObject("block_marked"),
                    result.getString("default_value"),
                    result.getObject("action_custom_max_wait_sec"),
                    result.getObject("on_hold_seconds"),
                    result.getObject("codified"),
                    result.getObject("export_to_abr"),
                    result.getObject("active"),
                    result.getInt("block_id"),
                    nullableInteger(result, "variable_id"),
                    nullableInteger(result, "parent_block_id"),
                    nullableInteger(result, "parent_id"),
                    result.getString("client_named"),
                    List.of());
        }

        static InstructionRow fromScanner(InstructionLoad instruction) {
            if (instruction == null) {
                throw new ApplyRefused(
                        "A Page Scanner row could not be converted to an instruction.");
            }
            return new InstructionRow(
                    -1,
                    null,
                    instruction.getInstructionOrderNumber() == null
                            ? 1
                            : instruction.getInstructionOrderNumber(),
                    instruction.getActions(),
                    instruction.getName(),
                    instruction.getXpath(),
                    instruction.getCoordinates(),
                    instruction.getForceCoordinates(),
                    instruction.getIFrameXPath(),
                    instruction.getTagName(),
                    instruction.getShadowHost(),
                    instruction.getShadowRoot(),
                    instruction.getCssSelector(),
                    instruction.getDescription(),
                    instruction.getOperation(),
                    bool(instruction.getOptional()),
                    bool(instruction.getBlockMarked()),
                    instruction.getDefaultValue(),
                    instruction.getActionCustomMaxWaitSec(),
                    instruction.getOnHoldSeconds(),
                    bool(instruction.getCodified()),
                    bool(instruction.getExportToABR()),
                    bool(instruction.getInstructionActive()),
                    instruction.getBlockId() == null ? -1 : instruction.getBlockId(),
                    instruction.getVariableId(),
                    instruction.getParentBlockId(),
                    instruction.getParentId(),
                    instruction.getClientNamed(),
                    instruction.getReferenceLoadDTOList() == null
                            ? List.of()
                            : List.copyOf(instruction.getReferenceLoadDTOList()));
        }

        private static Integer bool(Boolean value) {
            return value == null ? null : value ? 1 : 0;
        }

        InstructionRow withoutRelations() {
            return new InstructionRow(
                    id,
                    sourceComponentId,
                    order,
                    actions,
                    name,
                    xpath,
                    coordinates,
                    forceCoordinates,
                    iframeXPath,
                    tagName,
                    shadowHost,
                    shadowRoot,
                    cssSelector,
                    description,
                    operation,
                    optional,
                    blockMarked,
                    defaultValue,
                    actionCustomMaxWaitSec,
                    onHoldSeconds,
                    codified,
                    exportToAbr,
                    active,
                    blockId,
                    variableId,
                    parentBlockId,
                    parentId,
                    clientNamed,
                    scannerReferences);
        }

        InstructionRow asInserted(int generatedId, int targetBlockId, int targetOrder) {
            return new InstructionRow(
                    generatedId,
                    sourceComponentId,
                    targetOrder,
                    actions,
                    name,
                    xpath,
                    coordinates,
                    forceCoordinates,
                    iframeXPath,
                    tagName,
                    shadowHost,
                    shadowRoot,
                    cssSelector,
                    description,
                    operation,
                    optional,
                    blockMarked,
                    defaultValue,
                    actionCustomMaxWaitSec,
                    onHoldSeconds,
                    codified,
                    exportToAbr,
                    active,
                    targetBlockId,
                    variableId,
                    parentBlockId,
                    parentId,
                    clientNamed,
                    scannerReferences);
        }

        InstructionRow withVariableId(Integer generatedVariableId) {
            return new InstructionRow(
                    id,
                    sourceComponentId,
                    order,
                    actions,
                    name,
                    xpath,
                    coordinates,
                    forceCoordinates,
                    iframeXPath,
                    tagName,
                    shadowHost,
                    shadowRoot,
                    cssSelector,
                    description,
                    operation,
                    optional,
                    blockMarked,
                    defaultValue,
                    actionCustomMaxWaitSec,
                    onHoldSeconds,
                    codified,
                    exportToAbr,
                    active,
                    blockId,
                    generatedVariableId,
                    parentBlockId,
                    parentId,
                    clientNamed,
                    scannerReferences);
        }

        InstructionRow withRelations(Integer generatedParentId, Integer generatedParentBlockId) {
            return new InstructionRow(
                    id,
                    sourceComponentId,
                    order,
                    actions,
                    name,
                    xpath,
                    coordinates,
                    forceCoordinates,
                    iframeXPath,
                    tagName,
                    shadowHost,
                    shadowRoot,
                    cssSelector,
                    description,
                    operation,
                    optional,
                    blockMarked,
                    defaultValue,
                    actionCustomMaxWaitSec,
                    onHoldSeconds,
                    codified,
                    exportToAbr,
                    active,
                    blockId,
                    variableId,
                    generatedParentBlockId,
                    generatedParentId,
                    clientNamed,
                    scannerReferences);
        }

        InstructionLoad asInstruction() {
            InstructionLoad row = new InstructionLoad();
            row.setId(id);
            row.setInstructionOrderNumber(order);
            row.setActions(actions);
            row.setName(name);
            row.setBlockId(blockId);
            row.setVariableId(variableId);
            row.setParentBlockId(parentBlockId);
            row.setParentId(parentId);
            row.setOperation(operation);
            return row;
        }
    }

    private static final class ApplyRefused extends RuntimeException {
        private ApplyRefused(String message) {
            super(message);
        }
    }
}

package com.allinweb.ch.facade;

import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.ReferenceLoadDTO;
import com.allinweb.ch.model.UpdatedRow;
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
 * same connection, and the complete copy/move is committed once.
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

            List<InstructionRow> componentRows =
                    loadInstructionRows(
                            connection,
                            COMPONENT_INSTRUCTION_SELECT,
                            request.homeBankingId(),
                            true);
            Map<Integer, InstructionRow> componentById = indexInstructions(componentRows);
            Map<Integer, BlockRow> componentBlocks =
                    loadComponentBlocks(connection, request.homeBankingId());
            String currentComponentRevision = revisionService.revision(
                    componentRows.stream().map(InstructionRow::asInstruction).toList());

            ComponentSelectionPlan componentPlan = planComponentSelections(
                    request,
                    componentRows,
                    componentById,
                    componentBlocks,
                    currentComponentRevision);

            validateExistingSelections(request, currentBotById);
            boolean targetRequired = request.orderedItems().stream().anyMatch(item -> switch (item.kind()) {
                case COMPONENT_BLOCK -> false;
                case COMPONENT_INSTRUCTION ->
                    !componentPlan.coveredBySelectedBlock().contains(item.componentInstructionId());
                default -> true;
            });
            if (targetRequired
                    && (request.targetBlockId() <= 0
                            || !botBlocks.containsKey(request.targetBlockId()))) {
                throw new ApplyRefused("Select a valid target block before applying.");
            }

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
                            .filter(row -> row.blockId() == request.targetBlockId())
                            .map(InstructionRow::order)
                            .max(Integer::compareTo)
                            .orElse(0)
                    + 1;
            Map<Integer, Integer> generatedComponentInstructionIds = new LinkedHashMap<>();
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
                    if (item.kind() == ItemKind.COMPONENT_INSTRUCTION) {
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
                                request.targetBlockId(),
                                request.botJobId());
                        generatedComponentInstructionIds.put(sourceInstructionId, generatedId);
                        generatedInstructionsByItem.put(item.itemKey(), generatedId);
                        insertedRows.add(
                                source.asInserted(
                                        generatedId,
                                        request.targetBlockId(),
                                        nextTargetOrder - 1));
                    } else if (item.kind() == ItemKind.PAGE_SCANNER_INSTRUCTION) {
                        InstructionRow source = InstructionRow.fromScanner(item.scannerInstruction());
                        int generatedId = insertInstruction(
                                insertInstruction,
                                source,
                                nextTargetOrder++,
                                request.targetBlockId(),
                                request.botJobId());
                        generatedScannerInstructionsByItem.put(item.itemKey(), generatedId);
                        insertedRows.add(
                                source.asInserted(
                                        generatedId,
                                        request.targetBlockId(),
                                        nextTargetOrder - 1));
                    }
                }
            }

            copyComponentVariables(
                    connection,
                    request,
                    componentPlan.selectedInstructionIds(),
                    generatedComponentInstructionIds,
                    insertedRows);
            remapComponentRelationships(
                    connection,
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
                        if (appendedTargetIds.add(item.botJobInstructionId())) {
                            orderedTargetIds.add(item.botJobInstructionId());
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

            List<UpdatedRow> finalLayout =
                    finalLayout(completeRows, request.targetBlockId(), orderedTargetIds);
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
            generatedByItem.putAll(generatedInstructionsByItem);
            generatedByItem.putAll(generatedScannerInstructionsByItem);
            return new Result(
                    null,
                    true,
                    false,
                    request.orderedItems().size(),
                    Map.copyOf(generatedByItem),
                    Map.copyOf(generatedBlocksByItem));
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
                throw new ApplyRefused(
                        "A selected component GOTO references a block that was not selected.");
            }
        }

        return new ComponentSelectionPlan(
                Set.copyOf(selectedAll),
                Set.copyOf(covered),
                Map.copyOf(rowsByBlock));
    }

    private void validateExistingSelections(
            Request request, Map<Integer, InstructionRow> currentBotById) {
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
            List<InstructionRow> insertedRows,
            Map<Integer, Integer> instructionIds,
            Map<Integer, Integer> blockIds)
            throws SQLException {
        String update =
                "UPDATE instruction SET variable_id = ?, parent_block_id = ?, parent_id = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(update)) {
            for (int index = 0; index < insertedRows.size(); index++) {
                InstructionRow row = insertedRows.get(index);
                if (row.sourceComponentId() == null) continue;
                Integer parentId = row.parentId() == null
                        ? null
                        : instructionIds.get(row.parentId());
                Integer parentBlockId = row.parentBlockId() == null
                        ? null
                        : blockIds.get(row.parentBlockId());
                bindNullableInteger(statement, 1, row.variableId());
                bindNullableInteger(statement, 2, parentBlockId);
                bindNullableInteger(statement, 3, parentId);
                statement.setInt(4, row.id());
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
        Map<Integer, List<InstructionRow>> blocks = new LinkedHashMap<>();
        rows.stream()
                .sorted(Comparator.comparingInt(InstructionRow::blockId)
                        .thenComparingInt(InstructionRow::order)
                        .thenComparingInt(InstructionRow::id))
                .forEach(row ->
                        blocks.computeIfAbsent(row.blockId(), ignored -> new ArrayList<>()).add(row));
        Map<Integer, InstructionRow> byId = indexInstructions(rows);
        blocks.values().forEach(block -> block.removeIf(row -> selected.contains(row.id())));
        if (!orderedSelectedIds.isEmpty()) {
            List<InstructionRow> target =
                    blocks.computeIfAbsent(targetBlockId, ignored -> new ArrayList<>());
            for (Integer instructionId : orderedSelectedIds) {
                InstructionRow row = byId.get(instructionId);
                if (row != null) target.add(row);
            }
        }
        List<UpdatedRow> updates = new ArrayList<>();
        for (Map.Entry<Integer, List<InstructionRow>> block : blocks.entrySet()) {
            for (int index = 0; index < block.getValue().size(); index++) {
                UpdatedRow update = new UpdatedRow();
                update.setInstructionId(block.getValue().get(index).id());
                update.setBlockId(block.getKey());
                update.setInstructionOrderNumber(index + 1);
                updates.add(update);
            }
        }
        return updates;
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
        String update = "UPDATE instruction SET instruction_order_number = ?, block_id = ? "
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
                statement.setInt(1, row.getInstructionOrderNumber());
                statement.setInt(2, row.getBlockId());
                statement.setInt(3, row.getInstructionId());
                statement.setInt(4, botJobId);
                statement.setInt(5, expected.order());
                statement.setInt(6, expected.blockId());
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

    public record Request(
            String requestId,
            int botJobId,
            int homeBankingId,
            int targetBlockId,
            List<OrderedItem> orderedItems) {
        public Request {
            orderedItems = orderedItems == null ? List.of() : List.copyOf(orderedItems);
        }
    }

    public record Result(
            ErrorMessage error,
            boolean committed,
            boolean duplicate,
            int appliedCount,
            Map<String, Integer> generatedInstructionIds,
            Map<String, Integer> generatedBlockIds) {

        static Result failed(ErrorMessage error) {
            return new Result(error, false, false, 0, Map.of(), Map.of());
        }

        Result asDuplicate() {
            return new Result(
                    error,
                    committed,
                    true,
                    appliedCount,
                    generatedInstructionIds,
                    generatedBlockIds);
        }
    }

    private record ComponentSelectionPlan(
            Set<Integer> selectedInstructionIds,
            Set<Integer> coveredBySelectedBlock,
            Map<Integer, List<InstructionRow>> rowsBySelectedBlock) {}

    private record CompletedRequest(String fingerprint, Result result) {}

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

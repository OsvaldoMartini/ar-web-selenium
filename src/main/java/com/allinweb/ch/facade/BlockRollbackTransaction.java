package com.allinweb.ch.facade;

import com.allinweb.ch.model.BlockOrderDetailDTO;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.UpdatedRow;
import com.allinweb.ch.model.VariableLoadDTO;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Performs the instruction portion of a block rollback as one verified transaction.
 *
 * <p>The browser payload is treated only as the requested destination layout. Relationship
 * metadata is read from the database: a relationship to an instruction's own source block follows
 * that instruction to the destination. A genuine cross-block target is refused because rollback
 * removes empty source blocks and silently preserving it would create a dangling GOTO.
 */
final class BlockRollbackTransaction {

    void execute(
            Connection connection,
            String instructionTable,
            int ownerId,
            int destinationBlockId,
            String expectedGraphRevision,
            List<BlockOrderDetailDTO> expectedBlocks,
            List<UpdatedRow> requestedRows)
            throws SQLException {
        boolean botJobTable = "instruction".equals(instructionTable);
        boolean componentTable = "component_instruction".equals(instructionTable);
        if (!botJobTable && !componentTable) {
            throw new SQLException("Unsupported rollback instruction table");
        }
        if (connection == null
                || ownerId <= 0
                || destinationBlockId <= 0
                || expectedGraphRevision == null
                || expectedGraphRevision.isBlank()
                || expectedBlocks == null
                || expectedBlocks.isEmpty()
                || requestedRows == null
                || requestedRows.isEmpty()) {
            throw new SQLException("Rollback context is incomplete");
        }

        String blockTable = componentTable ? "component_block" : "block";
        String variableTable = componentTable ? "component_variable" : "variable";
        String ownerColumn = componentTable ? "home_banking_id" : "bot_job_id";
        boolean previousAutoCommit = connection.getAutoCommit();
        if (!previousAutoCommit) {
            throw new SQLException("Rollback requires an independent auto-commit connection");
        }

        connection.setAutoCommit(false);
        try {
            Map<Integer, UpdatedRow> requests =
                    validateRequestedLayout(requestedRows, destinationBlockId);
            Map<Integer, StoredBlock> ownedBlocks =
                    loadOwnedBlocks(connection, blockTable, ownerColumn, ownerId);
            validateCompleteBlockCatalog(
                    expectedBlocks, ownedBlocks, ownerId, componentTable);
            Set<Integer> ownedBlockIds = ownedBlocks.keySet();
            if (!ownedBlockIds.contains(destinationBlockId)) {
                throw new SQLException(
                        "The destination block does not belong to the active workspace");
            }

            Map<Integer, StoredInstruction> storedRows =
                    loadOwnedInstructions(connection, instructionTable, ownerColumn, ownerId);
            validateGraphRevision(
                    connection,
                    variableTable,
                    ownerColumn,
                    ownerId,
                    expectedGraphRevision,
                    storedRows);
            validateCompleteOwnerLayout(requests, storedRows);
            validateStoredRelationships(storedRows, ownedBlockIds);

            Map<Integer, Integer> expectedParentBlocks = new LinkedHashMap<>();
            for (StoredInstruction stored : storedRows.values()) {
                Integer parentBlockId = stored.parentBlockId();
                expectedParentBlocks.put(
                        stored.id(),
                        Objects.equals(parentBlockId, stored.sourceBlockId())
                                ? Integer.valueOf(destinationBlockId)
                                : parentBlockId);
            }

            updateRows(
                    connection,
                    instructionTable,
                    ownerColumn,
                    ownerId,
                    destinationBlockId,
                    requests,
                    storedRows,
                    expectedParentBlocks);
            verifyPersistedLayout(
                    connection,
                    instructionTable,
                    ownerColumn,
                    ownerId,
                    destinationBlockId,
                    requests,
                    expectedParentBlocks);
            deleteEmptySourceBlocks(
                    connection,
                    blockTable,
                    instructionTable,
                    ownerColumn,
                    ownerId,
                    destinationBlockId);
            normalizeDestinationBlockOrder(
                    connection,
                    blockTable,
                    ownerColumn,
                    ownerId,
                    destinationBlockId);
            connection.commit();
        } catch (SQLException | RuntimeException error) {
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                error.addSuppressed(rollbackFailure);
            }
            if (error instanceof SQLException sqlError) {
                throw sqlError;
            }
            throw new SQLException("Rollback transaction failed", error);
        } finally {
            restoreAutoCommit(connection, previousAutoCommit);
        }
    }

    private void normalizeDestinationBlockOrder(
            Connection connection,
            String blockTable,
            String ownerColumn,
            int ownerId,
            int destinationBlockId)
            throws SQLException {
        String sql = "UPDATE " + blockTable
                + " SET block_order_number = 1 WHERE id = ? AND " + ownerColumn + " = ?";
        try (PreparedStatement update = connection.prepareStatement(sql)) {
            update.setInt(1, destinationBlockId);
            update.setInt(2, ownerId);
            if (update.executeUpdate() != 1) {
                throw new SQLException(
                        "The rollback destination block could not be normalized");
            }
        }
    }

    private void restoreAutoCommit(Connection connection, boolean previousAutoCommit) {
        try {
            connection.setAutoCommit(previousAutoCommit);
        } catch (SQLException ignored) {
            // The production caller closes the connection; do not hide a committed result.
        }
    }

    private Map<Integer, UpdatedRow> validateRequestedLayout(
            List<UpdatedRow> requestedRows, int destinationBlockId) throws SQLException {
        Map<Integer, UpdatedRow> requests = new LinkedHashMap<>();
        Set<Integer> orders = new HashSet<>();
        for (UpdatedRow row : requestedRows) {
            if (row == null
                    || row.getInstructionId() == null
                    || row.getInstructionId() <= 0
                    || row.getInstructionOrderNumber() == null
                    || row.getInstructionOrderNumber() <= 0) {
                throw new SQLException("Rollback contains an invalid instruction row");
            }
            if (row.getBlockId() != null
                    && !Integer.valueOf(destinationBlockId).equals(row.getBlockId())) {
                throw new SQLException(
                        "Rollback row "
                                + row.getInstructionId()
                                + " has a different destination block");
            }
            if (requests.putIfAbsent(row.getInstructionId(), row) != null) {
                throw new SQLException(
                        "Rollback contains duplicate instruction ID " + row.getInstructionId());
            }
            if (!orders.add(row.getInstructionOrderNumber())) {
                throw new SQLException(
                        "Rollback contains duplicate instruction order "
                                + row.getInstructionOrderNumber());
            }
        }
        for (int order = 1; order <= requests.size(); order++) {
            if (!orders.contains(order)) {
                throw new SQLException(
                        "Rollback instruction orders must be contiguous from 1 to "
                                + requests.size());
            }
        }
        return requests;
    }

    private Map<Integer, StoredBlock> loadOwnedBlocks(
            Connection connection, String blockTable, String ownerColumn, int ownerId)
            throws SQLException {
        Map<Integer, StoredBlock> blocks = new LinkedHashMap<>();
        String sql = "SELECT id, block_order_number, name, active, wait, export_file FROM "
                + blockTable
                + " WHERE "
                + ownerColumn
                + " = ? ORDER BY block_order_number, id";
        try (PreparedStatement select = connection.prepareStatement(sql)) {
            select.setInt(1, ownerId);
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    int id = rows.getInt("id");
                    Object active = rows.getObject("active");
                    Object wait = rows.getObject("wait");
                    StoredBlock block = new StoredBlock(
                            id,
                            rows.getInt("block_order_number"),
                            rows.getString("name"),
                            active == null || ((Number) active).intValue() != 0,
                            wait == null ? 0 : ((Number) wait).intValue(),
                            rows.getString("export_file"));
                    if (blocks.putIfAbsent(id, block) != null) {
                        throw new SQLException("Database returned duplicate block ID " + id);
                    }
                }
            }
        }
        return blocks;
    }

    private Map<Integer, StoredInstruction> loadOwnedInstructions(
            Connection connection, String instructionTable, String ownerColumn, int ownerId)
            throws SQLException {
        Map<Integer, StoredInstruction> instructions = new LinkedHashMap<>();
        String sql = "SELECT id, block_id, instruction_order_number, actions, parent_id, "
                + "parent_block_id, variable_id, operation FROM "
                + instructionTable
                + " WHERE "
                + ownerColumn
                + " = ? ORDER BY id";
        try (PreparedStatement select = connection.prepareStatement(sql)) {
            select.setInt(1, ownerId);
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    Integer sourceBlockId = nullableInteger(rows, "block_id");
                    if (sourceBlockId == null || sourceBlockId <= 0) {
                        throw new SQLException(
                                "Instruction "
                                        + rows.getInt("id")
                                        + " has no valid source block");
                    }
                    StoredInstruction instruction = new StoredInstruction(
                            rows.getInt("id"),
                            sourceBlockId,
                            rows.getInt("instruction_order_number"),
                            rows.getString("actions"),
                            nullableInteger(rows, "parent_id"),
                            nullableInteger(rows, "parent_block_id"),
                            nullableInteger(rows, "variable_id"),
                            rows.getString("operation"));
                    if (instructions.putIfAbsent(instruction.id(), instruction) != null) {
                        throw new SQLException(
                                "Database returned duplicate instruction ID " + instruction.id());
                    }
                }
            }
        }
        return instructions;
    }

    private void validateCompleteBlockCatalog(
            List<BlockOrderDetailDTO> expectedBlocks,
            Map<Integer, StoredBlock> storedBlocks,
            int ownerId,
            boolean componentTable)
            throws SQLException {
        Map<Integer, BlockOrderDetailDTO> expectedById = new LinkedHashMap<>();
        for (BlockOrderDetailDTO expected : expectedBlocks) {
            if (expected == null
                    || expected.getBlockId() == null
                    || expected.getBlockId() <= 0
                    || expected.getBlockOrderNumber() == null
                    || expected.getBlockOrderNumber() <= 0
                    || expected.getBlockName() == null) {
                throw new SQLException("Rollback contains an invalid block catalog row");
            }
            Integer submittedOwner =
                    componentTable ? expected.getHomeBankId() : expected.getBotJobId();
            if (submittedOwner != null && submittedOwner != ownerId) {
                throw new SQLException(
                        "Rollback block "
                                + expected.getBlockId()
                                + " belongs to a different workspace");
            }
            if (expectedById.putIfAbsent(expected.getBlockId(), expected) != null) {
                throw new SQLException(
                        "Rollback contains duplicate block ID " + expected.getBlockId());
            }
        }

        Set<Integer> foreignIds = new LinkedHashSet<>(expectedById.keySet());
        foreignIds.removeAll(storedBlocks.keySet());
        Set<Integer> missingIds = new LinkedHashSet<>(storedBlocks.keySet());
        missingIds.removeAll(expectedById.keySet());
        if (!foreignIds.isEmpty() || !missingIds.isEmpty()) {
            throw new SQLException(
                    "Blocks changed while the rollback was being applied"
                            + " (foreign="
                            + foreignIds
                            + ", missing="
                            + missingIds
                            + ")");
        }

        for (StoredBlock stored : storedBlocks.values()) {
            BlockOrderDetailDTO expected = expectedById.get(stored.id());
            boolean expectedActive =
                    expected.getBlockActive() == null || expected.getBlockActive();
            int expectedWait =
                    expected.getBlockWait() == null ? 0 : expected.getBlockWait();
            if (expected.getBlockOrderNumber() != stored.order()
                    || !Objects.equals(expected.getBlockName(), stored.name())
                    || expectedActive != stored.active()
                    || expectedWait != stored.blockWait()
                    || !Objects.equals(expected.getExportFile(), stored.exportFile())) {
                throw new SQLException(
                        "Block "
                                + stored.id()
                                + " changed while the rollback was being applied");
            }
        }
    }

    private void validateGraphRevision(
            Connection connection,
            String variableTable,
            String ownerColumn,
            int ownerId,
            String expectedGraphRevision,
            Map<Integer, StoredInstruction> storedRows)
            throws SQLException {
        List<InstructionLoad> graphRows = new ArrayList<>();
        for (StoredInstruction stored : storedRows.values()) {
            InstructionLoad row = new InstructionLoad();
            row.setId(stored.id());
            row.setBlockId(stored.sourceBlockId());
            row.setInstructionOrderNumber(stored.sourceOrder());
            row.setActions(stored.actions());
            row.setParentId(stored.parentId());
            row.setParentBlockId(stored.parentBlockId());
            row.setVariableId(stored.variableId());
            row.setOperation(stored.operation());
            graphRows.add(row);
        }
        List<VariableLoadDTO> variableOwnership =
                loadVariableOwnership(
                        connection, variableTable, ownerColumn, ownerId);
        String actualGraphRevision = new InstructionGraphRevisionService()
                .revision(graphRows, variableOwnership);
        if (!expectedGraphRevision.equals(actualGraphRevision)) {
            throw new SQLException(
                    "Instructions changed while the rollback was being applied");
        }
    }

    private List<VariableLoadDTO> loadVariableOwnership(
            Connection connection,
            String variableTable,
            String ownerColumn,
            int ownerId)
            throws SQLException {
        List<VariableLoadDTO> variables = new ArrayList<>();
        String sql = "SELECT id, instruction_id FROM "
                + variableTable
                + " WHERE "
                + ownerColumn
                + " = ? ORDER BY id";
        try (PreparedStatement select = connection.prepareStatement(sql)) {
            select.setInt(1, ownerId);
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    variables.add(new VariableLoadDTO(
                            rows.getInt("id"),
                            "component_variable".equals(variableTable) ? ownerId : null,
                            "variable".equals(variableTable) ? ownerId : null,
                            nullableInteger(rows, "instruction_id"),
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

    private void validateCompleteOwnerLayout(
            Map<Integer, UpdatedRow> requests,
            Map<Integer, StoredInstruction> storedRows)
            throws SQLException {
        Set<Integer> foreignIds = new LinkedHashSet<>(requests.keySet());
        foreignIds.removeAll(storedRows.keySet());
        Set<Integer> missingIds = new LinkedHashSet<>(storedRows.keySet());
        missingIds.removeAll(requests.keySet());
        if (!foreignIds.isEmpty() || !missingIds.isEmpty()) {
            throw new SQLException(
                    "Rollback must contain every instruction owned by the workspace exactly once"
                            + " (foreign="
                            + foreignIds
                            + ", missing="
                            + missingIds
                            + ")");
        }
    }

    private void validateStoredRelationships(
            Map<Integer, StoredInstruction> storedRows, Set<Integer> ownedBlockIds)
            throws SQLException {
        for (StoredInstruction instruction : storedRows.values()) {
            if (!ownedBlockIds.contains(instruction.sourceBlockId())) {
                throw new SQLException(
                        "Instruction "
                                + instruction.id()
                                + " references a source block outside the active workspace");
            }
            Integer parentBlockId = instruction.parentBlockId();
            if (parentBlockId != null
                    && parentBlockId > 0
                    && !ownedBlockIds.contains(parentBlockId)) {
                throw new SQLException(
                        "Instruction "
                                + instruction.id()
                                + " references a parent block outside the active workspace");
            }
            if (parentBlockId != null
                    && parentBlockId > 0
                    && !Objects.equals(parentBlockId, instruction.sourceBlockId())) {
                throw new SQLException(
                        "Rollback is unsafe while instruction "
                                + instruction.id()
                                + " has a cross-block reference. Remove or retarget it first.");
            }
        }
    }

    private void deleteEmptySourceBlocks(
            Connection connection,
            String blockTable,
            String instructionTable,
            String ownerColumn,
            int ownerId,
            int destinationBlockId)
            throws SQLException {
        String sql = "DELETE FROM "
                + blockTable
                + " WHERE "
                + ownerColumn
                + " = ? AND id <> ? AND NOT EXISTS (SELECT 1 FROM "
                + instructionTable
                + " WHERE "
                + instructionTable
                + ".block_id = "
                + blockTable
                + ".id AND "
                + instructionTable
                + "."
                + ownerColumn
                + " = ?)";
        try (PreparedStatement delete = connection.prepareStatement(sql)) {
            delete.setInt(1, ownerId);
            delete.setInt(2, destinationBlockId);
            delete.setInt(3, ownerId);
            delete.executeUpdate();
        }
    }

    private void updateRows(
            Connection connection,
            String instructionTable,
            String ownerColumn,
            int ownerId,
            int destinationBlockId,
            Map<Integer, UpdatedRow> requests,
            Map<Integer, StoredInstruction> storedRows,
            Map<Integer, Integer> expectedParentBlocks)
            throws SQLException {
        String sql = "UPDATE "
                + instructionTable
                + " SET instruction_order_number = ?, block_id = ?, parent_block_id = ?"
                + " WHERE id = ? AND "
                + ownerColumn
                + " = ? AND block_id = ?"
                + " AND instruction_order_number = ?"
                + " AND ((parent_block_id = ?) OR (parent_block_id IS NULL AND ? IS NULL))";
        try (PreparedStatement update = connection.prepareStatement(sql)) {
            for (UpdatedRow request : requests.values()) {
                StoredInstruction stored = storedRows.get(request.getInstructionId());
                Integer oldParentBlockId = stored.parentBlockId();
                Integer newParentBlockId = expectedParentBlocks.get(stored.id());

                update.setInt(1, request.getInstructionOrderNumber());
                update.setInt(2, destinationBlockId);
                setNullableInteger(update, 3, newParentBlockId);
                update.setInt(4, stored.id());
                update.setInt(5, ownerId);
                update.setInt(6, stored.sourceBlockId());
                update.setInt(7, stored.sourceOrder());
                setNullableInteger(update, 8, oldParentBlockId);
                setNullableInteger(update, 9, oldParentBlockId);
                if (update.executeUpdate() != 1) {
                    throw new SQLException(
                            "Instruction "
                                    + stored.id()
                                    + " changed while the rollback was being applied");
                }
            }
        }
    }

    private void verifyPersistedLayout(
            Connection connection,
            String instructionTable,
            String ownerColumn,
            int ownerId,
            int destinationBlockId,
            Map<Integer, UpdatedRow> requests,
            Map<Integer, Integer> expectedParentBlocks)
            throws SQLException {
        String sql = "SELECT id, block_id, instruction_order_number, parent_block_id FROM "
                + instructionTable
                + " WHERE "
                + ownerColumn
                + " = ?";
        Set<Integer> persistedIds = new HashSet<>();
        Set<Integer> persistedOrders = new HashSet<>();
        try (PreparedStatement select = connection.prepareStatement(sql)) {
            select.setInt(1, ownerId);
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    int instructionId = rows.getInt("id");
                    UpdatedRow request = requests.get(instructionId);
                    if (request == null
                            || rows.getInt("block_id") != destinationBlockId
                            || rows.getInt("instruction_order_number")
                                    != request.getInstructionOrderNumber()
                            || !Objects.equals(
                                    nullableInteger(rows, "parent_block_id"),
                                    expectedParentBlocks.get(instructionId))) {
                        throw new SQLException(
                                "Rollback verification failed for instruction " + instructionId);
                    }
                    if (!persistedIds.add(instructionId)
                            || !persistedOrders.add(rows.getInt("instruction_order_number"))) {
                        throw new SQLException(
                                "Rollback verification found a duplicate instruction or order");
                    }
                }
            }
        }
        if (!persistedIds.equals(requests.keySet())
                || persistedOrders.size() != requests.size()) {
            throw new SQLException("Rollback verification found an incomplete destination layout");
        }
        for (int order = 1; order <= requests.size(); order++) {
            if (!persistedOrders.contains(order)) {
                throw new SQLException("Rollback verification found a gap at order " + order);
            }
        }
    }

    private Integer nullableInteger(ResultSet rows, String column) throws SQLException {
        Object value = rows.getObject(column);
        return value == null ? null : ((Number) value).intValue();
    }

    private void setNullableInteger(PreparedStatement statement, int index, Integer value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    private record StoredBlock(
            int id,
            int order,
            String name,
            boolean active,
            int blockWait,
            String exportFile) {}

    private record StoredInstruction(
            int id,
            int sourceBlockId,
            int sourceOrder,
            String actions,
            Integer parentId,
            Integer parentBlockId,
            Integer variableId,
            String operation) {}
}

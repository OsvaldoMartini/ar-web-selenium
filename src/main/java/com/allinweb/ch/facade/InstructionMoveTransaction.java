package com.allinweb.ch.facade;

import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.UpdatedRow;
import com.allinweb.ch.model.VariableLoadDTO;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Connection-scoped, verified instruction movement used by production and rollback fixtures. */
final class InstructionMoveTransaction {
    void execute(Connection connection, String blockTable, int whereId, List<UpdatedRow> updates) throws SQLException {
        execute(connection, blockTable, whereId, updates, null, 2);
    }

    void execute(Connection connection, String blockTable, int whereId, List<UpdatedRow> updates,
            String expectedRevision, Integer layoutVersion) throws SQLException {
        if (!Integer.valueOf(2).equals(layoutVersion)) {
            throw new SQLException("ROW_MOVE requires layout version 2.");
        }
        if (updates == null || updates.isEmpty()) {
            throw new SQLException("ROW_MOVE layout must include every instruction owned by the active owner.");
        }
        String owner = "block".equals(blockTable) ? "bot_job_id" : "home_banking_id";
        String instructionTable = "block".equals(blockTable) ? "instruction" : "component_instruction";
        String selectParents =
                "SELECT id,parent_id,block_id,parent_block_id FROM " + instructionTable + " WHERE " + owner + "=?";
        String updateSql = "UPDATE " + instructionTable
                + " SET instruction_order_number=?,block_id=?,parent_block_id=? WHERE id=? AND " + owner + "=?";
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            verifyExpectedRevision(
                    connection, instructionTable, owner, whereId, expectedRevision);

            List<Integer> blockIds = new ArrayList<>();
            Map<Integer, Integer> blockOrders = new HashMap<>();
            try (PreparedStatement blocks = connection.prepareStatement(
                    "SELECT id,block_order_number FROM " + blockTable + " WHERE " + owner
                            + "=? ORDER BY block_order_number,id")) {
                blocks.setInt(1, whereId);
                try (ResultSet rows = blocks.executeQuery()) {
                    while (rows.next()) {
                        int blockId = rows.getInt("id");
                        blockIds.add(blockId);
                        blockOrders.put(blockId, rows.getInt("block_order_number"));
                    }
                }
            }
            Set<Integer> ownedBlockIds = new HashSet<>(blockIds);
            for (UpdatedRow row : updates) {
                if (row == null || row.getInstructionId() == null || row.getBlockId() == null
                        || row.getBlockOrderNumber() == null || row.getInstructionOrderNumber() == null
                        || row.getInstructionOrderNumber() < 1
                        || !ownedBlockIds.contains(row.getBlockId())) {
                    throw new SQLException("Destination block does not belong to the active owner.");
                }
                if (!java.util.Objects.equals(blockOrders.get(row.getBlockId()), row.getBlockOrderNumber())) {
                    throw new SQLException("Submitted block order does not match the active owner block catalog.");
                }
            }
            Map<Integer, Integer> parents = new HashMap<>();
            Map<Integer, Integer> storedBlocks = new HashMap<>();
            Map<Integer, Integer> storedParentBlocks = new HashMap<>();
            Set<Integer> originallyOccupied = new HashSet<>();
            try (PreparedStatement select = connection.prepareStatement(selectParents)) {
                select.setInt(1, whereId);
                try (ResultSet rows = select.executeQuery()) {
                    while (rows.next()) {
                        int storedBlockId = rows.getInt("block_id");
                        int storedInstructionId = rows.getInt("id");
                        originallyOccupied.add(storedBlockId);
                        storedBlocks.put(storedInstructionId, storedBlockId);
                        int parentId = rows.getInt("parent_id");
                        if (!rows.wasNull()) parents.put(storedInstructionId, parentId);
                        int parentBlockId = rows.getInt("parent_block_id");
                        if (!rows.wasNull()) storedParentBlocks.put(storedInstructionId, parentBlockId);
                    }
                }
            }
            Set<Integer> submittedIds = new HashSet<>();
            Map<Integer, Integer> destinations = new HashMap<>();
            Map<Integer, Set<Integer>> ordersByBlock = new HashMap<>();
            for (UpdatedRow row : updates) {
                if (!submittedIds.add(row.getInstructionId())) throw new SQLException("Duplicate instruction in layout.");
                destinations.put(row.getInstructionId(), row.getBlockId());
                if (!ordersByBlock.computeIfAbsent(row.getBlockId(), ignored -> new HashSet<>())
                        .add(row.getInstructionOrderNumber())) throw new SQLException("Duplicate instruction order in block.");
            }
            if (!submittedIds.equals(storedBlocks.keySet()))
                throw new SQLException("ROW_MOVE layout must include every instruction owned by the active owner.");
            for (Set<Integer> orders : ordersByBlock.values()) {
                for (int order = 1; order <= orders.size(); order++) {
                    if (!orders.contains(order)) throw new SQLException("Instruction orders must be positive and contiguous.");
                }
            }
            Map<Integer, Integer> finalParentBlocks = new HashMap<>();
            for (UpdatedRow row : updates) {
                int instructionId = row.getInstructionId();
                Integer storedParentId = parents.get(instructionId);
                if (!Objects.equals(storedParentId, row.getParentId()))
                    throw new SQLException("Submitted parent id differs from the stored relationship.");

                Integer storedParentBlock = storedParentBlocks.get(instructionId);
                Integer submittedParentBlock = row.getParentBlockId();
                boolean parentBlockChanged = !Objects.equals(storedParentBlock, submittedParentBlock);
                if (parentBlockChanged
                        && submittedParentBlock != null
                        && !ownedBlockIds.contains(submittedParentBlock)) {
                    throw new SQLException("Changed parent block does not belong to the active owner.");
                }

                // The persistence layer owns structural integrity, not action semantics. An
                // unchanged stored value is always valid (including legacy/GOTO targets outside
                // this owner). When parent_id resolves to an owned instruction, React may instead
                // submit that parent's final block after planning a connected cross-block move.
                boolean unchangedStoredParentBlock = Objects.equals(submittedParentBlock, storedParentBlock);
                boolean resolvedFinalParentBlock = false;
                if (storedParentId != null && storedBlocks.containsKey(storedParentId)) {
                    resolvedFinalParentBlock =
                            Objects.equals(submittedParentBlock, destinations.get(storedParentId));
                }
                if (!unchangedStoredParentBlock && !resolvedFinalParentBlock) {
                    throw new SQLException("Submitted parent block does not match the final relationship.");
                }
                finalParentBlocks.put(instructionId, submittedParentBlock);
            }
            try (PreparedStatement update = connection.prepareStatement(updateSql)) {
                for (UpdatedRow row : updates) {
                    Integer parentBlock = finalParentBlocks.get(row.getInstructionId());
                    update.setInt(1, row.getInstructionOrderNumber());
                    update.setInt(2, row.getBlockId());
                    if (parentBlock == null) update.setNull(3, Types.INTEGER); else update.setInt(3, parentBlock);
                    update.setInt(4, row.getInstructionId());
                    update.setInt(5, whereId);
                    if (update.executeUpdate() != 1) throw new SQLException("Instruction was not updated exactly once.");
                }
            }
            Set<Integer> occupied = new HashSet<>();
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT DISTINCT block_id FROM " + instructionTable + " WHERE " + owner + "=?")) {
                select.setInt(1, whereId);
                try (ResultSet rows = select.executeQuery()) {
                    while (rows.next()) occupied.add(rows.getInt("block_id"));
                }
            }
            Set<Integer> referencedFinalParentBlocks = new HashSet<>();
            for (UpdatedRow row : updates) {
                Integer finalParentBlock = finalParentBlocks.get(row.getInstructionId());
                if (finalParentBlock != null) referencedFinalParentBlocks.add(finalParentBlock);
            }
            Set<Integer> newlyEmpty = new HashSet<>(originallyOccupied);
            newlyEmpty.removeAll(occupied);
            newlyEmpty.removeAll(referencedFinalParentBlocks);
            List<Integer> remaining =
                    blockIds.stream().filter(blockId -> !newlyEmpty.contains(blockId)).toList();
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM " + blockTable + " WHERE id=? AND " + owner + "=?")) {
                for (Integer blockId : blockIds) {
                    if (!newlyEmpty.contains(blockId)) continue;
                    delete.setInt(1, blockId);
                    delete.setInt(2, whereId);
                    if (delete.executeUpdate() != 1) throw new SQLException("Empty block was not deleted exactly once.");
                }
            }
            try (PreparedStatement reorder = connection.prepareStatement(
                    "UPDATE " + blockTable + " SET block_order_number=? WHERE id=? AND " + owner + "=?")) {
                for (int index = 0; index < remaining.size(); index++) {
                    reorder.setInt(1, index + 1);
                    reorder.setInt(2, remaining.get(index));
                    reorder.setInt(3, whereId);
                    if (reorder.executeUpdate() != 1) throw new SQLException("Block was not reordered exactly once.");
                }
            }
            connection.commit();
        } catch (SQLException exception) {
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                exception.addSuppressed(rollbackFailure);
            }
            throw exception;
        } finally {
            restoreAutoCommit(connection, previousAutoCommit);
        }
    }

    private void verifyExpectedRevision(
            Connection connection,
            String instructionTable,
            String ownerColumn,
            int ownerId,
            String expectedRevision)
            throws SQLException {
        if (expectedRevision == null || expectedRevision.isBlank()) return;

        List<InstructionLoad> instructionRows = new ArrayList<>();
        String instructionQuery = "SELECT id,block_id,instruction_order_number,actions,parent_id,"
                + "parent_block_id,variable_id,operation FROM "
                + instructionTable
                + " WHERE "
                + ownerColumn
                + "=? ORDER BY id";
        try (PreparedStatement statement = connection.prepareStatement(instructionQuery)) {
            statement.setInt(1, ownerId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    InstructionLoad row = new InstructionLoad();
                    row.setId(nullableInteger(result, "id"));
                    row.setBlockId(nullableInteger(result, "block_id"));
                    row.setInstructionOrderNumber(
                            nullableInteger(result, "instruction_order_number"));
                    row.setActions(result.getString("actions"));
                    row.setParentId(nullableInteger(result, "parent_id"));
                    row.setParentBlockId(nullableInteger(result, "parent_block_id"));
                    row.setVariableId(nullableInteger(result, "variable_id"));
                    row.setOperation(result.getString("operation"));
                    instructionRows.add(row);
                }
            }
        }

        boolean component = "component_instruction".equals(instructionTable);
        String variableTable = component ? "component_variable" : "variable";
        List<VariableLoadDTO> variableOwnership = new ArrayList<>();
        String variableQuery = "SELECT id,instruction_id FROM "
                + variableTable
                + " WHERE "
                + ownerColumn
                + "=? ORDER BY id";
        try (PreparedStatement statement = connection.prepareStatement(variableQuery)) {
            statement.setInt(1, ownerId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    variableOwnership.add(new VariableLoadDTO(
                            nullableInteger(result, "id"),
                            component ? ownerId : null,
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

        String currentRevision =
                new InstructionGraphRevisionService().revision(instructionRows, variableOwnership);
        if (!expectedRevision.trim().equals(currentRevision)) {
            throw new SQLException("Instruction graph revision changed before persistence.");
        }
    }

    private Integer nullableInteger(ResultSet result, String column) throws SQLException {
        int value = result.getInt(column);
        return result.wasNull() ? null : value;
    }

    private void restoreAutoCommit(Connection connection, boolean previousAutoCommit) {
        try {
            connection.setAutoCommit(previousAutoCommit);
        } catch (SQLException ignored) {
            // The mutation outcome is already known and the production caller closes the connection.
        }
    }
}

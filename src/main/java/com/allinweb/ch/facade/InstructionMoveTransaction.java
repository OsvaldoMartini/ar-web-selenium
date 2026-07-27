package com.allinweb.ch.facade;

import com.allinweb.ch.model.UpdatedRow;
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
import java.util.Set;
import java.util.stream.Collectors;

/** Connection-scoped, verified instruction movement used by production and rollback fixtures. */
final class InstructionMoveTransaction {
    void execute(Connection connection, String blockTable, int whereId, List<UpdatedRow> updates) throws SQLException {
        String owner = "block".equals(blockTable) ? "bot_job_id" : "home_banking_id";
        String instructionTable = "block".equals(blockTable) ? "instruction" : "component_instruction";
        String selectParents =
                "SELECT id,parent_id,block_id FROM " + instructionTable + " WHERE " + owner + "=?";
        String updateSql = "UPDATE " + instructionTable
                + " SET instruction_order_number=?,block_id=?,parent_block_id=? WHERE id=? AND " + owner + "=?";
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            List<Integer> blockIds = new ArrayList<>();
            try (PreparedStatement blocks = connection.prepareStatement(
                    "SELECT id FROM " + blockTable + " WHERE " + owner + "=? ORDER BY block_order_number,id")) {
                blocks.setInt(1, whereId);
                try (ResultSet rows = blocks.executeQuery()) {
                    while (rows.next()) blockIds.add(rows.getInt("id"));
                }
            }
            Set<Integer> ownedBlockIds = new HashSet<>(blockIds);
            for (UpdatedRow row : updates) {
                if (row == null || row.getBlockId() == null || !ownedBlockIds.contains(row.getBlockId())) {
                    throw new SQLException("Destination block does not belong to the active owner.");
                }
            }

            Map<Integer, Integer> destinations = updates.stream().collect(Collectors.toMap(
                    UpdatedRow::getInstructionId, UpdatedRow::getBlockId, (first, ignored) -> first));
            Map<Integer, Integer> parents = new HashMap<>();
            Map<Integer, Integer> storedBlocks = new HashMap<>();
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
                    }
                }
            }
            try (PreparedStatement update = connection.prepareStatement(updateSql)) {
                for (UpdatedRow row : updates) {
                    Integer parentId = parents.get(row.getInstructionId());
                    Integer parentBlock = parentId == null
                            ? null
                            : destinations.getOrDefault(parentId, storedBlocks.get(parentId));
                    if (parentId != null && parentBlock == null) throw new SQLException("Parent is missing from layout.");
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
            Set<Integer> newlyEmpty = new HashSet<>(originallyOccupied);
            newlyEmpty.removeAll(occupied);
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

    private void restoreAutoCommit(Connection connection, boolean previousAutoCommit) {
        try {
            connection.setAutoCommit(previousAutoCommit);
        } catch (SQLException ignored) {
            // The mutation outcome is already known and the production caller closes the connection.
        }
    }
}

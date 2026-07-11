package com.allinweb.ch.facade;

import com.allinweb.ch.model.UpdatedRow;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Connection-scoped, verified instruction movement used by production and rollback fixtures. */
final class InstructionMoveTransaction {
    void execute(Connection connection, String blockTable, int whereId, List<UpdatedRow> updates) throws SQLException {
        String owner = "block".equals(blockTable) ? "bot_job_id" : "home_banking_id";
        String instructionTable = "block".equals(blockTable) ? "instruction" : "component_instruction";
        String selectParents = "SELECT id,parent_id FROM " + instructionTable + " WHERE " + owner + "=?";
        String updateSql = "UPDATE " + instructionTable
                + " SET instruction_order_number=?,block_id=?,parent_block_id=? WHERE id=? AND " + owner + "=?";
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            Map<Integer, Integer> destinations = updates.stream().collect(Collectors.toMap(
                    UpdatedRow::getInstructionId, UpdatedRow::getBlockId, (first, ignored) -> first));
            Map<Integer, Integer> parents = new HashMap<>();
            try (PreparedStatement select = connection.prepareStatement(selectParents)) {
                select.setInt(1, whereId);
                try (ResultSet rows = select.executeQuery()) {
                    while (rows.next()) {
                        int parentId = rows.getInt("parent_id");
                        if (!rows.wasNull()) parents.put(rows.getInt("id"), parentId);
                    }
                }
            }
            try (PreparedStatement update = connection.prepareStatement(updateSql)) {
                for (UpdatedRow row : updates) {
                    Integer parentId = parents.get(row.getInstructionId());
                    Integer parentBlock = parentId == null ? null : destinations.get(parentId);
                    if (parentId != null && parentBlock == null) throw new SQLException("Parent is missing from layout.");
                    update.setInt(1, row.getInstructionOrderNumber());
                    update.setInt(2, row.getBlockId());
                    if (parentBlock == null) update.setNull(3, Types.INTEGER); else update.setInt(3, parentBlock);
                    update.setInt(4, row.getInstructionId());
                    update.setInt(5, whereId);
                    if (update.executeUpdate() != 1) throw new SQLException("Instruction was not updated exactly once.");
                }
            }
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }
}

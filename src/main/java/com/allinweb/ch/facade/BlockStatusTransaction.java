package com.allinweb.ch.facade;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/** Atomically keeps a block and every owned child instruction at the same active state. */
final class BlockStatusTransaction {

    void execute(
            Connection connection,
            String blockTable,
            String instructionTable,
            int ownerId,
            int blockId,
            boolean active)
            throws SQLException {
        String ownerColumn;
        if ("block".equals(blockTable) && "instruction".equals(instructionTable)) {
            ownerColumn = "bot_job_id";
        } else if ("component_block".equals(blockTable)
                && "component_instruction".equals(instructionTable)) {
            ownerColumn = "home_banking_id";
        } else {
            throw new SQLException("Unsupported block status table pair.");
        }

        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (PreparedStatement updateBlock = connection.prepareStatement(
                    "UPDATE " + blockTable
                            + " SET active=? WHERE id=? AND " + ownerColumn + "=?")) {
                updateBlock.setBoolean(1, active);
                updateBlock.setInt(2, blockId);
                updateBlock.setInt(3, ownerId);
                if (updateBlock.executeUpdate() != 1) {
                    throw new SQLException(
                            "The block does not belong to the active owner.");
                }
            }
            try (PreparedStatement updateInstructions = connection.prepareStatement(
                    "UPDATE " + instructionTable
                            + " SET active=? WHERE block_id=? AND " + ownerColumn + "=?")) {
                updateInstructions.setBoolean(1, active);
                updateInstructions.setInt(2, blockId);
                updateInstructions.setInt(3, ownerId);
                updateInstructions.executeUpdate();
            }
            connection.commit();
        } catch (SQLException failure) {
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        } finally {
            restoreAutoCommit(connection, previousAutoCommit);
        }
    }

    private void restoreAutoCommit(Connection connection, boolean previousAutoCommit) {
        try {
            connection.setAutoCommit(previousAutoCommit);
        } catch (SQLException ignored) {
            // The caller closes the connection; preserve the already-known transaction result.
        }
    }
}

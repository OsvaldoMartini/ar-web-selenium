package com.allinweb.ch.facade;

import com.allinweb.ch.model.BlockLoadDTO;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Validates and atomically persists one complete owner-scoped block permutation. */
final class BlockOrderTransaction {

    void execute(
            Connection connection,
            String blockTable,
            int ownerId,
            List<BlockLoadDTO> submitted)
            throws SQLException {
        String ownerColumn;
        if ("block".equals(blockTable)) {
            ownerColumn = "bot_job_id";
        } else if ("component_block".equals(blockTable)) {
            ownerColumn = "home_banking_id";
        } else {
            throw new SQLException("Unsupported block table.");
        }
        if (submitted == null || submitted.isEmpty()) {
            throw new SQLException("A complete block order is required.");
        }

        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            List<Integer> ownedIds = new ArrayList<>();
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT id FROM " + blockTable + " WHERE " + ownerColumn + "=?")) {
                select.setInt(1, ownerId);
                try (ResultSet rows = select.executeQuery()) {
                    while (rows.next()) ownedIds.add(rows.getInt("id"));
                }
            }

            Set<Integer> owned = new HashSet<>(ownedIds);
            Set<Integer> submittedIds = new HashSet<>();
            Set<Integer> submittedOrders = new HashSet<>();
            if (submitted.size() != owned.size()) {
                throw new SQLException("The submitted block order is incomplete.");
            }
            for (BlockLoadDTO block : submitted) {
                if (block == null
                        || block.getId() == null
                        || !owned.contains(block.getId())
                        || !submittedIds.add(block.getId())) {
                    throw new SQLException(
                            "The submitted block order contains an unknown or duplicate block.");
                }
                Integer order = block.getBlockOrderNumber();
                if (order == null
                        || order < 1
                        || order > submitted.size()
                        || !submittedOrders.add(order)) {
                    throw new SQLException(
                            "The submitted block order is not a contiguous permutation.");
                }
            }

            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE " + blockTable
                            + " SET block_order_number=? WHERE id=? AND "
                            + ownerColumn + "=?")) {
                // Move through unique temporary values so schemas with a
                // UNIQUE(owner, block_order_number) constraint can swap safely.
                for (int index = 0; index < submitted.size(); index++) {
                    BlockLoadDTO block = submitted.get(index);
                    update.setInt(1, -(index + 1));
                    update.setInt(2, block.getId());
                    update.setInt(3, ownerId);
                    if (update.executeUpdate() != 1) {
                        throw new SQLException(
                                "A block order row was not staged exactly once.");
                    }
                }
                for (BlockLoadDTO block : submitted) {
                    update.setInt(1, block.getBlockOrderNumber());
                    update.setInt(2, block.getId());
                    update.setInt(3, ownerId);
                    if (update.executeUpdate() != 1) {
                        throw new SQLException(
                                "A block order row was not updated exactly once.");
                    }
                }
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
            // A cleanup failure after commit must not turn a committed reorder into a retry.
        }
    }
}

package com.allinweb.ch.facade;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/** Atomically removes Bot Jobs and every Bot-Job-owned row that has no database cascade. */
@Slf4j
final class BotJobDeleteTransaction {

    private static final List<OwnedTable> OWNED_TABLES = List.of(
            new OwnedTable("instruction_variable_slot", "bot_job_id", null),
            new OwnedTable("instruction_variable_command_config", "bot_job_id", null),
            new OwnedTable("instruction_graph_state", "owner_id", "workspace_kind='BOT_JOB'"),
            new OwnedTable("scanned_element", "bot_job_id", null),
            new OwnedTable("page_scan_snapshot", "bot_job_id", null),
            new OwnedTable("bot_job_variable_migration_note", "bot_job_id", null));

    private final PageScanSnapshotArtifactLifecycle snapshotArtifacts;

    BotJobDeleteTransaction() {
        this(PageScanSnapshotArtifactLifecycle.configured());
    }

    BotJobDeleteTransaction(PageScanSnapshotArtifactLifecycle snapshotArtifacts) {
        this.snapshotArtifacts = snapshotArtifacts;
    }

    List<Integer> execute(Connection connection, Collection<Integer> submittedIds)
            throws SQLException {
        if (connection == null) {
            throw new SQLException("Bot Job deletion requires a database connection.");
        }
        List<Integer> botJobIds = canonicalIds(submittedIds);
        boolean previousAutoCommit = connection.getAutoCommit();
        PageScanSnapshotArtifactLifecycle.Plan artifactPlan =
                PageScanSnapshotArtifactLifecycle.Plan.none();
        boolean committed = false;
        connection.setAutoCommit(false);
        try {
            requireExistingBotJobs(connection, botJobIds);
            snapshotArtifacts.reconcile(connection);
            artifactPlan = snapshotArtifacts.stage(botJobIds);
            for (OwnedTable table : OWNED_TABLES) {
                if (tableExists(connection, table.name())) {
                    deleteOwnedRows(connection, table, botJobIds);
                }
            }
            deleteBotJobs(connection, botJobIds);
            connection.commit();
            committed = true;
        } catch (SQLException | IOException failure) {
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            try {
                snapshotArtifacts.restore(artifactPlan);
            } catch (IOException restoreFailure) {
                failure.addSuppressed(restoreFailure);
            }
            if (failure instanceof SQLException sqlFailure) throw sqlFailure;
            throw new SQLException("Bot Job deletion could not stage Page Scanner artifacts.", failure);
        } finally {
            restoreAutoCommit(connection, previousAutoCommit);
        }
        if (committed) {
            try {
                snapshotArtifacts.purge(artifactPlan);
            } catch (IOException cleanupFailure) {
                // The database commit is final and the artifacts are no longer visible at their
                // active owner path. A later deletion or startup reconciliation retries cleanup.
                log.error("Bot Job deletion committed, but pending Page Scanner artifacts could not "
                                + "be removed: {}",
                        cleanupFailure.getMessage(), cleanupFailure);
            }
        }
        return List.copyOf(botJobIds);
    }

    private List<Integer> canonicalIds(Collection<Integer> submittedIds) throws SQLException {
        if (submittedIds == null || submittedIds.isEmpty()) {
            throw new SQLException("Select at least one Bot Job to delete.");
        }
        Set<Integer> uniqueIds = new LinkedHashSet<>();
        for (Integer submittedId : submittedIds) {
            if (submittedId == null || submittedId <= 0) {
                throw new SQLException("Bot Job deletion requires positive numeric IDs.");
            }
            uniqueIds.add(submittedId);
        }
        return new ArrayList<>(uniqueIds);
    }

    private void requireExistingBotJobs(Connection connection, List<Integer> botJobIds)
            throws SQLException {
        List<Integer> missing = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT id FROM bot_job WHERE id=?")) {
            for (Integer botJobId : botJobIds) {
                select.setInt(1, botJobId);
                try (ResultSet rows = select.executeQuery()) {
                    if (!rows.next()) missing.add(botJobId);
                }
            }
        }
        if (!missing.isEmpty()) {
            throw new SQLException("Bot Job deletion was cancelled because IDs no longer exist: "
                    + missing);
        }
    }

    private void deleteOwnedRows(
            Connection connection, OwnedTable table, List<Integer> botJobIds)
            throws SQLException {
        String sql = "DELETE FROM " + table.name() + " WHERE "
                + (table.predicate() == null ? "" : table.predicate() + " AND ")
                + table.ownerColumn() + "=?";
        try (PreparedStatement delete = connection.prepareStatement(sql)) {
            for (Integer botJobId : botJobIds) {
                delete.setInt(1, botJobId);
                delete.addBatch();
            }
            delete.executeBatch();
        }
    }

    private void deleteBotJobs(Connection connection, List<Integer> botJobIds)
            throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM bot_job WHERE id=?")) {
            for (Integer botJobId : botJobIds) {
                delete.setInt(1, botJobId);
                delete.addBatch();
            }
            int[] results = delete.executeBatch();
            if (results.length != botJobIds.size()) {
                throw new SQLException("Bot Job deletion did not report every requested row.");
            }
            for (int index = 0; index < results.length; index++) {
                int affected = results[index];
                if (affected != 1 && affected != Statement.SUCCESS_NO_INFO) {
                    throw new SQLException("Bot Job " + botJobIds.get(index)
                            + " was not deleted; the complete operation was rolled back.");
                }
            }
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet tables = metadata.getTables(null, null, null, new String[] {"TABLE"})) {
            while (tables.next()) {
                if (tableName.equalsIgnoreCase(tables.getString("TABLE_NAME"))) return true;
            }
        }
        return false;
    }

    private void restoreAutoCommit(Connection connection, boolean previousAutoCommit) {
        try {
            connection.setAutoCommit(previousAutoCommit);
        } catch (SQLException ignored) {
            // The owning database facade closes this connection after the transaction.
        }
    }

    private record OwnedTable(String name, String ownerColumn, String predicate) {}
}

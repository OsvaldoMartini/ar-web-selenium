package com.allinweb.ch.facade;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/** Atomically clears all Bot Job and saved-component rows plus Page Scanner snapshots. */
@Slf4j
final class AllJobDetailsDeleteTransaction {

    private static final List<String> DELETE_ORDER = List.of(
            "bot_job_runtime_variable_value",
            "bot_job_variable_definition",
            "instruction_variable_slot",
            "instruction_variable_command_config",
            "instruction_graph_state",
            "page_scan_snapshot",
            "scanned_element",
            "bot_job_variable_migration_note",
            "variable",
            "reference",
            "instruction",
            "block",
            "bot_job",
            "component_variable",
            "component_reference",
            "component_instruction",
            "component_block");

    private final PageScanSnapshotArtifactLifecycle snapshotArtifacts;

    AllJobDetailsDeleteTransaction() {
        this(PageScanSnapshotArtifactLifecycle.configured());
    }

    AllJobDetailsDeleteTransaction(PageScanSnapshotArtifactLifecycle snapshotArtifacts) {
        this.snapshotArtifacts = snapshotArtifacts;
    }

    void execute(Connection connection) throws SQLException {
        execute(connection, () -> {});
    }

    void execute(Connection connection, Runnable committedReplacementObserver)
            throws SQLException {
        if (connection == null) throw new SQLException("Job cleanup requires a database connection.");
        boolean previousAutoCommit = connection.getAutoCommit();
        if (!previousAutoCommit) {
            throw new SQLException(
                    "Job cleanup requires a fresh autocommit connection for lifecycle recovery.");
        }
        PageScanSnapshotArtifactLifecycle.Plan artifactPlan =
                PageScanSnapshotArtifactLifecycle.Plan.none();
        boolean committed = false;
        boolean commitAttempted = false;
        boolean transactionStarted = false;
        try {
        synchronized (PageScanSnapshotLifecycleLock.MONITOR) {
            try {
                PageScanSnapshotLifecycleCoordinator.reconcileAll(connection);
                connection.setAutoCommit(false);
                transactionStarted = true;
                artifactPlan = snapshotArtifacts.stageAll(connection);
                try (Statement statement = connection.createStatement()) {
                    for (String table : DELETE_ORDER) {
                        if (tableExists(connection, table)) {
                            statement.executeUpdate("DELETE FROM " + table);
                        }
                    }
                }
                commitAttempted = true;
                connection.commit();
                committed = true;
            } catch (SQLException | IOException failure) {
                if (transactionStarted && !commitAttempted) {
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
                } else if (transactionStarted && commitAttempted && !committed) {
                    log.error(
                            "Job cleanup commit outcome is unknown; artifact journal retained",
                            failure);
                }
                if (failure instanceof SQLException sqlFailure) throw sqlFailure;
                throw new SQLException("Job cleanup could not stage Page Scanner artifacts.", failure);
            } finally {
                if (!transactionStarted || !commitAttempted || committed) {
                    restoreAutoCommit(connection, previousAutoCommit);
                }
            }
        }
        } catch (SQLException failure) {
            if (transactionStarted && commitAttempted && !committed) {
                PageScanSnapshotStorageHealth.markUnhealthy(failure);
                notifyReplacementBoundary(committedReplacementObserver);
                throw new SQLException(
                        "Job cleanup outcome is unknown. Reload after restarting the application.",
                        failure);
            }
            throw failure;
        }
        if (committed) {
            notifyReplacementBoundary(committedReplacementObserver);
            try {
                synchronized (PageScanSnapshotLifecycleLock.MONITOR) {
                    snapshotArtifacts.purge(artifactPlan);
                }
            } catch (IOException cleanupFailure) {
                log.error("Job cleanup committed, but pending Page Scanner artifacts could not be "
                                + "removed: {}",
                        cleanupFailure.getMessage(), cleanupFailure);
            }
        }
    }

    private static void notifyReplacementBoundary(Runnable observer) {
        if (observer == null) return;
        try {
            observer.run();
        } catch (RuntimeException lifecycleFailure) {
            // The database commit is final. Continue safe artifact purge and report the failure.
            log.warn(
                    "Job cleanup committed, but workspace invalidation failed",
                    lifecycleFailure);
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
}

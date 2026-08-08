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
        if (connection == null) throw new SQLException("Job cleanup requires a database connection.");
        boolean previousAutoCommit = connection.getAutoCommit();
        PageScanSnapshotArtifactLifecycle.Plan artifactPlan =
                PageScanSnapshotArtifactLifecycle.Plan.none();
        boolean committed = false;
        connection.setAutoCommit(false);
        try {
            if (tableExists(connection, "bot_job")) snapshotArtifacts.reconcile(connection);
            artifactPlan = snapshotArtifacts.stageAll();
            try (Statement statement = connection.createStatement()) {
                for (String table : DELETE_ORDER) {
                    if (tableExists(connection, table)) {
                        statement.executeUpdate("DELETE FROM " + table);
                    }
                }
            }
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
            throw new SQLException("Job cleanup could not stage Page Scanner artifacts.", failure);
        } finally {
            restoreAutoCommit(connection, previousAutoCommit);
        }
        if (committed) {
            try {
                snapshotArtifacts.purge(artifactPlan);
            } catch (IOException cleanupFailure) {
                log.error("Job cleanup committed, but pending Page Scanner artifacts could not be "
                                + "removed: {}",
                        cleanupFailure.getMessage(), cleanupFailure);
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
}

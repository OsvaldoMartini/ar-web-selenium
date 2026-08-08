package com.allinweb.ch.facade;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

/** One ordered recovery boundary for creation, retention, and owner-deletion journals. */
public final class PageScanSnapshotLifecycleCoordinator {

    private PageScanSnapshotLifecycleCoordinator() {}

    public static void reconcileAll(Connection connection) throws IOException, SQLException {
        PageScanSnapshotStorageHealth.requireHealthy();
        synchronized (PageScanSnapshotLifecycleLock.MONITOR) {
            PageScanSnapshotCreationLifecycle.reconcile(connection);
            PageScanSnapshotRetentionService.getInstance().reconcile(connection);
            PageScanSnapshotArtifactLifecycle.configured().reconcile(connection);
        }
    }
}

package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

@Isolated("Mutates process-wide PageScanSnapshotStorageHealth state")
class PageScanSnapshotStorageHealthTest {

    @TempDir
    Path temporaryDirectory;

    private PageScanSnapshotTestState state;

    @BeforeEach
    void isolateSnapshotConfiguration() throws Exception {
        state = PageScanSnapshotTestState.isolate(temporaryDirectory.resolve("configured"));
    }

    @AfterEach
    void restoreSnapshotConfiguration() throws Exception {
        state.close();
    }

    @Test
    void missingPathDbFailsClosed() {
        state.clearPathDb();

        IOException unavailable = assertThrows(
                IOException.class, PageScanSnapshotStorageHealth::requireHealthy);

        assertTrue(unavailable.getMessage().contains("PATH_DB is not configured"));
    }

    @Test
    void initializedGenerationRejectsALivePathSwitchUntilRestart() throws Exception {
        PageScanSnapshotStorageHealth.markHealthyConfiguredRoot();
        assertDoesNotThrow(PageScanSnapshotStorageHealth::requireHealthy);

        state.setPathDb(temporaryDirectory.resolve("replacement"));
        IOException unavailable = assertThrows(
                IOException.class, PageScanSnapshotStorageHealth::requireHealthy);

        assertTrue(unavailable.getMessage().contains("until restart"));
        assertTrue(unavailable.getMessage().contains("no longer matches"));
    }

    @Test
    void recoveryFailureRemainsFailClosedEvenWithAValidConfiguredRoot() {
        PageScanSnapshotStorageHealth.markUnhealthy(
                new IOException("private ACL verification failed"));

        IOException unavailable = assertThrows(
                IOException.class, PageScanSnapshotStorageHealth::requireHealthy);

        assertTrue(unavailable.getMessage().contains("until restart"));
        assertTrue(unavailable.getMessage().contains("private ACL verification failed"));
    }

    @Test
    void staleScanContextPoisonsTheGenerationInsteadOfAllowingLaterReads() throws Exception {
        String configured = temporaryDirectory.resolve("configured").toString();
        assertDoesNotThrow(() -> PageScanSnapshotStorageHealth.requireHealthyContext(configured));

        IOException stale = assertThrows(
                IOException.class,
                () -> PageScanSnapshotStorageHealth.requireHealthyContext(
                        temporaryDirectory.resolve("old-generation").toString()));
        assertTrue(stale.getMessage().contains("changed while the scan was running"));

        IOException unavailable = assertThrows(
                IOException.class, PageScanSnapshotStorageHealth::requireHealthy);
        assertTrue(unavailable.getMessage().contains("stale PATH_DB configuration generation"));
    }

    @Test
    void explicitConfigurationChangeInvalidatesAnInitializedGeneration() throws Exception {
        PageScanSnapshotStorageHealth.markHealthyConfiguredRoot();

        PageScanSnapshotStorageHealth.configurationChanged();

        IOException unavailable = assertThrows(
                IOException.class, PageScanSnapshotStorageHealth::requireHealthy);
        assertTrue(unavailable.getMessage().contains("restart is required"));
    }
}

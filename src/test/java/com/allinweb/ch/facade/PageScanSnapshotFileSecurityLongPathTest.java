package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class PageScanSnapshotFileSecurityLongPathTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void privateAclApplyAndVerificationAcceptAWindowsExtendedLengthStagingPath()
            throws Exception {
        Path longDirectory = temporaryDirectory;
        for (int index = 0; longDirectory.toAbsolutePath().toString().length() <= 300; index++) {
            longDirectory = longDirectory.resolve("snapshot-segment-" + index + "-abcdef");
        }
        Files.createDirectories(longDirectory);
        assertTrue(longDirectory.toAbsolutePath().toString().length() > 260);

        Path target = longDirectory;
        assertDoesNotThrow(() -> PageScanSnapshotFileSecurity.secureDirectory(target));
        assertDoesNotThrow(() -> PageScanSnapshotFileSecurity.requirePrivateDirectory(target));
    }
}

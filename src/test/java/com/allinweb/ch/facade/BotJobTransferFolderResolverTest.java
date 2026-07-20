package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BotJobTransferFolderResolverTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void createsAndReturnsTheConfiguredCanonicalTransferFolder() throws Exception {
        Path configured = temporaryDirectory.resolve("nested").resolve("exports");

        File selected = BotJobTransferFolderResolver.resolve(configured.toString());

        assertTrue(selected.isDirectory());
        assertEquals(configured.toRealPath(), selected.toPath());
    }

    @Test
    void rejectsAConfiguredTransferPathThatIsAFile() throws Exception {
        Path configured = Files.writeString(temporaryDirectory.resolve("exports.txt"), "not a folder");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> BotJobTransferFolderResolver.resolve(configured.toString()));

        assertTrue(error.getMessage().contains("must point to a folder"));
    }
}

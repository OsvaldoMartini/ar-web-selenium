package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScannerSupportFileSaveServiceTest {

    private final ScannerSupportFileSaveService service = new ScannerSupportFileSaveService();

    @Test
    void writesSupportFileAndReturnsDisplayPath(@TempDir Path tempDir) throws Exception {
        Path target = tempDir.resolve("capture.support");
        ScannerSupportFileService.SupportFile supportFile =
                new ScannerSupportFileService.SupportFile("capture.support", "{\"ok\":true}");

        ScannerSupportFileSaveService.SavedSupportFile saved = service.save(supportFile, target);

        assertEquals("{\"ok\":true}", Files.readString(target, StandardCharsets.UTF_8));
        assertEquals(target.toAbsolutePath().toString(), saved.absolutePath());
        assertEquals(
                "File: " + target.toAbsolutePath() + "\n\nDrag & drop this file.",
                saved.portalMessage("Drag & drop this file."));
    }
}

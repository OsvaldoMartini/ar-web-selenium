package com.allinweb.ch.facade;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ScannerSupportFileSaveService {

    public SavedSupportFile save(ScannerSupportFileService.SupportFile supportFile, Path path) throws IOException {
        Files.writeString(path, supportFile.json(), StandardCharsets.UTF_8);
        return new SavedSupportFile(path.toAbsolutePath().toString());
    }

    public record SavedSupportFile(String absolutePath) {
        public String portalMessage(String instruction) {
            return "File: " + absolutePath + "\n\n" + instruction;
        }
    }
}

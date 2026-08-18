package com.allinweb.ch.facade;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Writer-side limits for immutable Page Mappings artifacts. */
final class PageScanArtifactPolicy {

    // These limits are the writer half of the bounded reader contract in
    // PageMappingsWorkspaceService. A snapshot must not become READY when its payload cannot be
    // consumed by that reader.
    static final long MAX_MANIFEST_BYTES = 1_000_000L;
    static final long MAX_METADATA_BYTES = 1_000_000L;
    static final long MAX_JSON_ARTIFACT_BYTES = 16_000_000L;
    static final long MAX_SCREENSHOT_BYTES = 8_000_000L;

    private static final List<String> PAYLOAD_FILES = List.of(
            "elements.json", "rects.json", "meta.json", "screenshot.png");
    private static final Set<String> PAYLOAD_FILE_SET = Set.copyOf(PAYLOAD_FILES);

    private PageScanArtifactPolicy() {}

    static void requireWritableSize(String fileName, long bytes) throws IOException {
        long maximum = maximumBytes(fileName);
        if (bytes < 0 || bytes > maximum) {
            throw new IOException("The page scan artifact exceeds its safe size: " + fileName);
        }
    }

    static void validatePayload(Path folder) throws IOException {
        Set<String> actual = new LinkedHashSet<>();
        try (var entries = Files.list(folder)) {
            for (Path entry : entries.toList()) {
                String fileName = entry.getFileName().toString();
                if (Files.isSymbolicLink(entry)
                        || !Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("The page scan artifact payload is invalid: " + fileName);
                }
                actual.add(fileName);
            }
        }
        if (!actual.equals(PAYLOAD_FILE_SET)) {
            throw new IOException("The page scan artifact file set is invalid");
        }
        for (String fileName : PAYLOAD_FILES) {
            requireWritableSize(fileName, Files.size(folder.resolve(fileName)));
        }
    }

    static long maximumBytes(String fileName) throws IOException {
        return switch (fileName) {
            case "manifest.json" -> MAX_MANIFEST_BYTES;
            case "meta.json" -> MAX_METADATA_BYTES;
            case "screenshot.png" -> MAX_SCREENSHOT_BYTES;
            case "elements.json", "rects.json" -> MAX_JSON_ARTIFACT_BYTES;
            default -> throw new IOException("The page scan artifact file is not allowed: " + fileName);
        };
    }
}

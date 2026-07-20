package com.allinweb.ch.facade;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/** Resolves the server-owned Bot Job import/export folder configured by {@code PATH_EXPORT}. */
public final class BotJobTransferFolderResolver {

    private BotJobTransferFolderResolver() {}

    public static File resolve(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            throw new IllegalArgumentException("Configure PATH_EXPORT before importing or exporting a Bot Job");
        }

        final Path folder;
        try {
            folder = Path.of(configuredPath.trim()).toAbsolutePath().normalize();
        } catch (InvalidPathException invalidPath) {
            throw new IllegalArgumentException("PATH_EXPORT is not a valid folder", invalidPath);
        }

        try {
            if (Files.exists(folder) && !Files.isDirectory(folder)) {
                throw new IllegalArgumentException("PATH_EXPORT must point to a folder: " + folder);
            }
            Files.createDirectories(folder);
            if (!Files.isDirectory(folder)) {
                throw new IllegalStateException("Unable to create the PATH_EXPORT folder: " + folder);
            }
            return folder.toRealPath().toFile();
        } catch (IOException error) {
            throw new IllegalStateException("Unable to use the PATH_EXPORT folder: " + folder, error);
        }
    }
}

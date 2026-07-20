package com.allinweb.ch.util;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.apache.poi.ss.usermodel.Workbook;

/** Filesystem boundary for safely publishing generated Excel workbooks. */
final class ExcelFileStore {

    private ExcelFileStore() {}

    static File requireOutputDirectory(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            throw new IllegalStateException("Configure PATH_EXCEL before generating an Excel file");
        }

        final Path folder;
        try {
            folder = Path.of(configuredPath.trim()).toAbsolutePath().normalize();
        } catch (InvalidPathException invalidPath) {
            throw new IllegalStateException("PATH_EXCEL is not a valid folder", invalidPath);
        }

        try {
            if (Files.exists(folder) && !Files.isDirectory(folder)) {
                throw new IllegalStateException("PATH_EXCEL must point to a folder: " + folder);
            }
            Files.createDirectories(folder);
            if (!Files.isDirectory(folder)) {
                throw new IllegalStateException("Unable to create the PATH_EXCEL folder: " + folder);
            }
            return folder.toRealPath().toFile();
        } catch (IOException error) {
            throw new IllegalStateException("Unable to use the PATH_EXCEL folder: " + folder, error);
        }
    }

    static File writeAtomically(Workbook workbook, File destination) {
        if (workbook == null) throw new IllegalArgumentException("Excel workbook is required");
        if (destination == null) throw new IllegalArgumentException("Excel destination is required");

        Path target = destination.toPath().toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new IllegalStateException("Excel output folder does not exist: " + parent);
        }

        Path temporary = null;
        try {
            temporary = Files.createTempFile(parent, ".arweb-excel-", ".tmp");
            try (OutputStream output = Files.newOutputStream(temporary)) {
                workbook.write(output);
            }
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            temporary = null;

            if (!Files.isRegularFile(target) || Files.size(target) == 0L) {
                throw new IOException("The generated workbook is empty or unavailable");
            }
            return target.toRealPath().toFile();
        } catch (IOException error) {
            throw new IllegalStateException("Unable to generate Excel file: " + target, error);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // The incomplete temporary file was never published as the requested workbook.
                }
            }
        }
    }
}

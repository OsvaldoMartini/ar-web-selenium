package com.allinweb.ch.facade;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Canonical parser/encoder for a block's persisted Excel/CSV execution-export target. */
public record ExcelExportTarget(Path path, String delimiter) {
    private static final List<String> FILE_TYPES = List.of(".xlsx", ".csv");

    public ExcelExportTarget {
        if (path == null) throw new IllegalArgumentException("Excel export path is required");
        if (!path.isAbsolute() && path.getRoot() == null) {
            throw new IllegalArgumentException("Excel export path must be absolute");
        }
        path = path.toAbsolutePath().normalize();
        delimiter = requireDelimiter(delimiter);
        String fileName = path.getFileName() == null
                ? ""
                : path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (FILE_TYPES.stream().noneMatch(fileName::endsWith)) {
            throw new IllegalArgumentException("Excel export file must use .xlsx or .csv");
        }
    }

    public static Optional<ExcelExportTarget> decode(String encoded) {
        String value = encoded == null ? "" : encoded.trim();
        if (value.isEmpty() || "No Excel Export File".equals(value)) return Optional.empty();

        String delimiter = ",";
        if (value.endsWith(":,") || value.endsWith(":|")) {
            delimiter = value.substring(value.length() - 1);
            value = value.substring(0, value.length() - 2).trim();
        }
        if (value.isEmpty()) return Optional.empty();

        try {
            return Optional.of(new ExcelExportTarget(Path.of(value), delimiter));
        } catch (InvalidPathException invalidPath) {
            throw new IllegalArgumentException("Excel export path is invalid", invalidPath);
        }
    }

    public static String encode(Path path, String delimiter) {
        ExcelExportTarget target = new ExcelExportTarget(path, delimiter);
        return target.path().toString().replace('\\', '/') + ':' + target.delimiter();
    }

    public String fileType() {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".csv") ? ".csv" : ".xlsx";
    }

    private static String requireDelimiter(String value) {
        if (!",".equals(value) && !"|".equals(value)) {
            throw new IllegalArgumentException("Excel export delimiter must be comma or pipe");
        }
        return value;
    }
}

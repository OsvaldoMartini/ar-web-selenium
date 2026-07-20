package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExcelExportTargetTest {

    @TempDir
    Path temporary;

    @Test
    void roundTripsAbsoluteWorkbookTargetAndDelimiter() {
        Path target = temporary.resolve("execution results.xlsx");
        String encoded = ExcelExportTarget.encode(target, "|");
        ExcelExportTarget decoded = ExcelExportTarget.decode(encoded).orElseThrow();

        assertEquals(target.toAbsolutePath().normalize(), decoded.path());
        assertEquals("|", decoded.delimiter());
        assertEquals(".xlsx", decoded.fileType());
    }

    @Test
    void suffixParsingDoesNotSplitWindowsDriveColon() {
        ExcelExportTarget decoded = ExcelExportTarget.decode("C:/exports/result.csv:,").orElseThrow();

        assertEquals("result.csv", decoded.path().getFileName().toString());
        assertEquals(",", decoded.delimiter());
        assertEquals(".csv", decoded.fileType());
    }

    @Test
    void rejectsUnsupportedExecutionExportFiles() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ExcelExportTarget.decode(temporary.resolve("result.txt") + ":,"));
        assertTrue(ExcelExportTarget.decode("No Excel Export File").isEmpty());
    }

    @Test
    void legacySentinelDoesNotHideARealPathContainingTheSameWords() {
        Path target = temporary
                .resolve("No Excel Export File archive")
                .resolve("results.xlsx");

        ExcelExportTarget decoded = ExcelExportTarget.decode(
                        ExcelExportTarget.encode(target, ","))
                .orElseThrow();

        assertEquals(target.toAbsolutePath().normalize(), decoded.path());
    }
}

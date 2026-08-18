package com.allinweb.ch.readersAndWriters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.CsvTable;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExcelWriterExecutionExportTest {

    @TempDir
    Path temporary;

    @Test
    void runtimeExportChainPublishesConfiguredWorkbookWithOrderedResults() throws Exception {
        Path destination = temporary.resolve("execution-results.xlsx");
        CsvTable table = new CsvTable(destination.getFileName().toString(), destination.toString(), "|");
        table.addColumns(List.of("Account", "Status"));
        table.put(0, "Account", "CH001");
        table.put(0, "Status", "OPEN");
        table.put(1, "Account", "CH002");
        table.put(1, "Status", "CLOSED");

        new ExcelWriter(destination.toString(), true)
                .withPurpose("export")
                .insertCSVContentIntoExcel(table.getColumns(), table, 0);

        // Runtime can publish the same accumulated target at block, row, and job boundaries.
        // The second writer must not retain an unused workbook handle on the existing file.
        table.put(0, "Status", "REVIEWED");
        new ExcelWriter(destination.toString(), true)
                .withPurpose("export")
                .insertCSVContentIntoExcel(table.getColumns(), table, 0);

        assertTrue(Files.isRegularFile(destination));
        assertTrue(Files.size(destination) > 0L);
        try (InputStream input = Files.newInputStream(destination);
                XSSFWorkbook workbook = new XSSFWorkbook(input)) {
            var sheet = workbook.getSheetAt(0);
            assertEquals("KEY", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("Account", sheet.getRow(0).getCell(1).getStringCellValue());
            assertEquals("Status", sheet.getRow(0).getCell(2).getStringCellValue());
            assertEquals("EXTERNAL_1", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals("CH001", sheet.getRow(1).getCell(1).getStringCellValue());
            assertEquals("REVIEWED", sheet.getRow(1).getCell(2).getStringCellValue());
            assertEquals("EXTERNAL_2", sheet.getRow(2).getCell(0).getStringCellValue());
            assertEquals("CH002", sheet.getRow(2).getCell(1).getStringCellValue());
            assertEquals("CLOSED", sheet.getRow(2).getCell(2).getStringCellValue());
        }
    }
}

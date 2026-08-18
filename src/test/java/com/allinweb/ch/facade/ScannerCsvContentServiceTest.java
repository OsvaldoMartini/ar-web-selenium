package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.allinweb.ch.model.CsvTable;
import org.junit.jupiter.api.Test;

class ScannerCsvContentServiceTest {
    private final ScannerCsvContentService service = new ScannerCsvContentService();

    @Test
    void headerOnlyContentWritesColumnHeader() {
        CsvTable table = new CsvTable("out.csv", "out.csv", "|");
        table.addColumns(java.util.List.of("User number", "Name"));

        assertEquals("0: User number,Name\n", service.headerOnlyContent(table));
    }

    @Test
    void bancaStatoContentUsesExternalForSingleRow() {
        CsvTable table = new CsvTable("out.csv", "out.csv", "|");
        table.put(0, "User number", "434234");

        assertEquals("KEY|User number\nEXTERNAL|434234\n", service.bancaStatoContent(table, "|"));
    }

    @Test
    void bancaStatoContentNumbersMultipleRows() {
        CsvTable table = new CsvTable("out.csv", "out.csv", "|");
        table.put(0, "User number", "434234");
        table.put(1, "User number", "353534");

        assertEquals(
                "KEY|User number\nEXTERNAL_1|434234\nEXTERNAL_2|353534\n",
                service.bancaStatoContent(table, "|"));
    }
}

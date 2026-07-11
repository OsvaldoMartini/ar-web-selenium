package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExcelExportServiceTest {
    private final ExcelExportService service = ExcelExportService.getInstance();

    @Test
    void parsesWindowsDriveAndTrailingDelimiterWithoutSplittingDriveColon() {
        Map<String, Object> parsed = ExcelExportService.parse("C:/exports/report.csv:|");
        assertEquals("C:/exports", parsed.get("directory"));
        assertEquals("report.csv", parsed.get("filename"));
        assertEquals(".csv", parsed.get("fileType"));
        assertEquals("|", parsed.get("delimiter"));
    }

    @Test
    void parsesWorkbookAndLegacyEmptyValues() {
        Map<String, Object> workbook = ExcelExportService.parse("/tmp/report.xlsx:,");
        assertEquals("/tmp", workbook.get("directory"));
        assertEquals("report.xlsx", workbook.get("filename"));
        assertEquals(".xlsx", workbook.get("fileType"));
        assertEquals(",", workbook.get("delimiter"));

        Map<String, Object> empty = ExcelExportService.parse("No Excel Export File");
        assertEquals("", empty.get("directory"));
        assertEquals("", empty.get("filename"));
    }

    @Test
    void bootstrapReturnsTypedOptionsForValidBotJobContext() {
        JsonObject body = context("botJobTasks", 19, 2);
        body.addProperty("exportFile", "/tmp/export.csv:|");
        Map<String, Object> response = service.bootstrap(body);
        assertTrue((Boolean) response.get("ok"));
        assertEquals(java.util.List.of(".xlsx", ".csv"), response.get("fileTypes"));
        assertEquals(java.util.List.of(",", "|"), response.get("delimiters"));
    }

    @Test
    void rejectsInvalidContextAndFieldsBeforePersistence() {
        assertFalse((Boolean) service.bootstrap(new JsonObject()).get("ok"));

        JsonObject body = context("botJobTasks", 19, 2);
        body.addProperty("directory", "/tmp");
        body.addProperty("filename", "../report");
        body.addProperty("fileType", ".csv");
        body.addProperty("delimiter", ",");
        Map<String, Object> response = service.save(body);
        assertFalse((Boolean) response.get("ok"));
        assertEquals("Enter a valid export filename.", response.get("error"));
    }

    @Test
    void requiresHomeBankingOwnerForComponentContext() {
        JsonObject body = context("componentTasks", 19, -1);
        Map<String, Object> response = service.bootstrap(body);
        assertFalse((Boolean) response.get("ok"));
        assertEquals("Excel export owner context is invalid.", response.get("error"));
    }

    private JsonObject context(String sessionId, int botJobId, int homeBankingId) {
        JsonObject body = new JsonObject();
        body.addProperty("sessionId", sessionId);
        body.addProperty("botJobId", botJobId);
        body.addProperty("homeBankingId", homeBankingId);
        body.addProperty("blockId", 10);
        body.addProperty("blockName", "Export");
        body.addProperty("blockOrderNumber", 2);
        body.addProperty("requestId", "excel-test");
        return body;
    }
}

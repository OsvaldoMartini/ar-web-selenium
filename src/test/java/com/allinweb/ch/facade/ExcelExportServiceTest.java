package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExcelExportServiceTest {
    private final ExcelExportService service = ExcelExportService.getInstance();

    @TempDir
    Path temporary;

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
        JsonObject body = context(ScannerWorkspaceSessions.BOT_JOB_TASKS, 19, 2);
        body.addProperty("exportFile", "/tmp/export.csv:|");
        Map<String, Object> response = service.bootstrap(body);
        assertTrue((Boolean) response.get("ok"));
        assertEquals(java.util.List.of(".xlsx", ".csv"), response.get("fileTypes"));
        assertEquals(java.util.List.of(",", "|"), response.get("delimiters"));
    }

    @Test
    void rejectsInvalidContextAndFieldsBeforePersistence() {
        assertFalse((Boolean) service.bootstrap(new JsonObject()).get("ok"));

        JsonObject body = context(ScannerWorkspaceSessions.BOT_JOB_TASKS, 19, 2);
        body.addProperty("directory", "/tmp");
        body.addProperty("filename", "../report");
        body.addProperty("fileType", ".csv");
        body.addProperty("delimiter", ",");
        Map<String, Object> response = service.save(body);
        assertFalse((Boolean) response.get("ok"));
        assertEquals("Enter a valid export filename.", response.get("error"));
        assertEquals("excel-test", response.get("requestId"));
        assertEquals(19, response.get("botJobId"));
        assertEquals(10, response.get("blockId"));
        assertEquals(ScannerWorkspaceSessions.BOT_JOB_TASKS, response.get("sessionId"));
    }

    @Test
    void requiresHomeBankingOwnerForComponentContext() {
        JsonObject body = context(ScannerWorkspaceSessions.COMPONENT_TASKS, 19, -1);
        Map<String, Object> response = service.bootstrap(body);
        assertFalse((Boolean) response.get("ok"));
        assertEquals("Excel export owner context is invalid.", response.get("error"));
    }

    @Test
    void nativeDirectorySelectionReturnsRealDestinationWithoutPersisting() throws Exception {
        JsonObject body = context(ScannerWorkspaceSessions.BOT_JOB_TASKS, 19, 2);
        Map<String, Object> response = service.chooseDirectory(body, ignored -> temporary.toFile());

        assertTrue((Boolean) response.get("ok"));
        assertEquals(false, response.get("cancelled"));
        assertEquals(temporary.toRealPath().toString(), response.get("directory"));
        assertEquals("Excel export folder selected", response.get("message"));
    }

    @Test
    void directorySelectionFailurePreservesRequestCorrelation() {
        JsonObject body = context(ScannerWorkspaceSessions.BOT_JOB_TASKS, 19, 2);
        Map<String, Object> response = service.chooseDirectory(
                body, ignored -> temporary.resolve("missing-directory").toFile());

        assertFalse((Boolean) response.get("ok"));
        assertEquals("excel-test", response.get("requestId"));
        assertEquals(19, response.get("botJobId"));
        assertEquals(10, response.get("blockId"));
        assertEquals(ScannerWorkspaceSessions.BOT_JOB_TASKS, response.get("sessionId"));
    }

    @Test
    void buildsPersistedExecutionTargetInsideSelectedWritableDirectory() throws Exception {
        String encoded = ExcelExportService.buildEncodedTarget(
                temporary.toString(), "daily results", ".xlsx", "|");
        ExcelExportTarget target = ExcelExportTarget.decode(encoded).orElseThrow();

        assertEquals(temporary.toRealPath().resolve("daily results.xlsx"), target.path());
        assertEquals("|", target.delimiter());
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

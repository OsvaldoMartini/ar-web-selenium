package com.allinweb.ch.facade.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.facade.ExcelExportTarget;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.BlockSnapshot;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.Environment;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.InstructionSnapshot;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.Owner;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.Plan;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.ExcelWriteRequest;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.Scope;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SmokeTestIntegrationExcelWriteServiceTest {
    @TempDir Path temporary;

    @Test
    void validatesFrozenInstructionAndWritesCsvBeforeAReadableWorkbook() throws Exception {
        String databaseUrl = "jdbc:sqlite:" + temporary.resolve("excel-write.db");
        Path workbookTarget = temporary.resolve("reports").resolve("result.xlsx");
        Files.createDirectories(workbookTarget.getParent());
        String encodedTarget = ExcelExportTarget.encode(workbookTarget, ",");
        bootstrap(databaseUrl, encodedTarget);
        String csv = "Value\r\nAlice\r\n";
        ExcelWriteRequest request = new ExcelWriteRequest(
                1, "excel-write-1", "run-1", encodedTarget, ",", List.of("Value"),
                List.of(10), csv, sha256(csv), 1L);

        SmokeTestIntegrationExcelWriteService.Result result =
                new SmokeTestIntegrationExcelWriteService(
                        () -> DriverManager.getConnection(databaseUrl)).save(plan(), request);

        Path csvTarget = workbookTarget.resolveSibling("result.csv");
        assertEquals(csv, Files.readString(csvTarget));
        assertTrue(Files.isRegularFile(workbookTarget));
        try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(workbookTarget))) {
            assertEquals("Value", workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
            assertEquals("Alice", workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue());
        }
        assertEquals(request.sha256(), result.sha256());
        assertEquals(1L, result.revision());
    }

    private static void bootstrap(String databaseUrl, String outputFile) throws Exception {
        try (Connection connection = DriverManager.getConnection(databaseUrl);
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE instruction(id INTEGER PRIMARY KEY,bot_job_id INTEGER,actions TEXT)");
            statement.execute("CREATE TABLE instruction_variable_command_config(home_banking_id INTEGER,bot_job_id INTEGER,instruction_id INTEGER,output_file TEXT,output_column TEXT)");
            statement.execute("CREATE TABLE instruction_variable_slot(home_banking_id INTEGER,bot_job_id INTEGER,instruction_id INTEGER,slot TEXT,variable_id INTEGER)");
            statement.execute("INSERT INTO instruction VALUES(10,32,'E')");
            try (var config = connection.prepareStatement("INSERT INTO instruction_variable_command_config VALUES(2,32,10,?, 'Value')")) {
                config.setString(1, outputFile);
                config.executeUpdate();
            }
            statement.execute("INSERT INTO instruction_variable_slot VALUES(2,32,10,'READ',99)");
        }
    }

    private static Plan plan() {
        Owner owner = new Owner(2, 32);
        BlockSnapshot block = new BlockSnapshot(1, 1, "Block", "", null, "", true, null);
        InstructionSnapshot instruction = new InstructionSnapshot(
                owner, "Bot Job", "", block, 10, 1, "E", "ExcelWrite", null, "",
                "", "", "", "", "", "", "", "", "", "", false, false,
                null, null, false, false, true, null, null, List.of(), Map.of("READ", 99));
        Environment environment = new Environment(
                2, "Bank", 32, "Bot Job", "", 1, "TEST", "https://example.test", "", "Chromium");
        return new Plan(owner, environment, Scope.all(), List.of(block), List.of(instruction), "a".repeat(64));
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}

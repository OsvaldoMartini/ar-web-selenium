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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SmokeTestIntegrationExcelWriteServiceTest {
    @TempDir Path temporary;

    @Test
    void validatesFrozenInstructionAndWritesFinalizedReactArtifactsWithoutBuildingThem() throws Exception {
        String databaseUrl = "jdbc:sqlite:" + temporary.resolve("excel-write.db");
        Path workbookTarget = temporary.resolve("reports").resolve("result.xlsx");
        Files.createDirectories(workbookTarget.getParent());
        String encodedTarget = ExcelExportTarget.encode(workbookTarget, ",");
        bootstrap(databaseUrl, encodedTarget);
        byte[] csv = "Value\r\nAlice\r\n".getBytes(StandardCharsets.UTF_8);
        ExcelWriteRequest csvRequest = new ExcelWriteRequest(
                1, "excel-write-1", "run-1", encodedTarget, ",", List.of("Value"),
                List.of(10), "CSV", java.util.Base64.getEncoder().encodeToString(csv), csv.length,
                sha256(csv), 1L);
        byte[] finalizedWorkbook = "frontend-finalized-xlsx-bytes".getBytes(StandardCharsets.UTF_8);
        ExcelWriteRequest xlsxRequest = new ExcelWriteRequest(
                1, "excel-write-2", "run-1", encodedTarget, ",", List.of("Value"),
                List.of(10), "XLSX", java.util.Base64.getEncoder().encodeToString(finalizedWorkbook),
                finalizedWorkbook.length, sha256(finalizedWorkbook), 1L);

        SmokeTestIntegrationExcelWriteService service = new SmokeTestIntegrationExcelWriteService(
                () -> DriverManager.getConnection(databaseUrl));
        SmokeTestIntegrationExcelWriteService.Result csvResult = service.save(plan(), csvRequest);
        SmokeTestIntegrationExcelWriteService.Result xlsxResult = service.save(plan(), xlsxRequest);

        Path csvTarget = workbookTarget.resolveSibling("result.csv");
        assertEquals("Value\r\nAlice\r\n", Files.readString(csvTarget));
        assertTrue(java.util.Arrays.equals(finalizedWorkbook, Files.readAllBytes(workbookTarget)));
        assertEquals(csvRequest.sha256(), csvResult.sha256());
        assertEquals(xlsxRequest.sha256(), xlsxResult.sha256());
        assertEquals(1L, xlsxResult.revision());
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

    private static String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value));
    }
}

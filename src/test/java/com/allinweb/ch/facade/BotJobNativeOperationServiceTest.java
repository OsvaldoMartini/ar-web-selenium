package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.BotJobToolbarContext;
import com.allinweb.ch.util.ARPropertyEnum;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BotJobNativeOperationServiceTest {

    @TempDir
    Path temporary;

    @Test
    void opensOnlyExistingExcelAndReportFiles() throws Exception {
        FakeProperties properties = properties();
        Path excel = temporary.resolve("Payments.xlsx");
        Files.createFile(excel);
        Path report = temporary.resolve("report.xlsx");
        Files.createFile(report);
        AtomicReference<File> opened = new AtomicReference<>();
        BotJobNativeOperationService service = new BotJobNativeOperationService(
                properties, opened::set, specification -> {});

        service.openExcel(context("Web App"));
        assertEquals(excel.toFile(), opened.get());
        service.openFile(report.toFile());
        assertEquals(report.toFile(), opened.get());
        assertEquals(temporary.toFile(), service.reportDirectory());
        assertThrows(IllegalArgumentException.class, () -> service.openFile(temporary.resolve("missing").toFile()));
    }

    @Test
    void createsBatWithStableEngineContractAndTestConfigurationFallback() throws Exception {
        FakeProperties properties = properties();
        Files.createFile(temporary.resolve("Payments.xlsx"));
        Path engine = Files.createFile(temporary.resolve("engine.jar"));
        properties.values.put(ARPropertyEnum.PATH_ENGINE, engine.toString());
        String previous = System.clearProperty("ARWebConfig");
        try {
            BotJobNativeOperationService service = new BotJobNativeOperationService(
                    properties, file -> {}, specification -> {});
            File bat = service.createBat(context("Web App"));
            String command = Files.readString(bat.toPath());

            assertEquals("execute_web_app_7_Botjob_42.bat", bat.getName());
            assertTrue(command.contains("execute/j 7 42 1"));
            assertTrue(command.contains("-c \"TESTS.config\""));
            assertTrue(command.contains(engine.toAbsolutePath().toString()));
        } finally {
            if (previous != null) System.setProperty("ARWebConfig", previous);
        }
    }

    @Test
    void launchBuildsTypedCommandAndLogDestinations() throws Exception {
        FakeProperties properties = properties();
        Path excel = Files.createFile(temporary.resolve("Payments.xlsx"));
        Path engine = Files.createFile(temporary.resolve("engine.jar"));
        Path driver = Files.createFile(temporary.resolve("driver.exe"));
        Path logs = temporary.resolve("logs");
        properties.values.put(ARPropertyEnum.PATH_ENGINE, engine.toString());
        properties.values.put(ARPropertyEnum.PATH_WEBDRIVER, driver.toString());
        properties.values.put(ARPropertyEnum.PATH_LOG, logs.toString());
        AtomicReference<BotJobNativeOperationService.LaunchSpecification> launched = new AtomicReference<>();
        BotJobNativeOperationService service = new BotJobNativeOperationService(
                properties, file -> {}, launched::set);

        service.launchExternalEngine(context("Rest Api"));

        BotJobNativeOperationService.LaunchSpecification spec = launched.get();
        assertEquals("java.exe", spec.command().get(0));
        assertEquals(engine.toAbsolutePath().toString(), spec.command().get(2));
        assertEquals(excel.toAbsolutePath().toString(), spec.command().get(7));
        assertEquals("TESTS.config", spec.command().get(9));
        assertEquals(logs.resolve("engine_debug_log_output.log").toFile(), spec.standardOutput());
        assertEquals(logs.resolve("engine_debug_log_error.log").toFile(), spec.standardError());
        assertTrue(Files.isDirectory(logs));
    }

    @Test
    void launchRejectsMobileAndMissingPrerequisitesBeforeStartingProcess() throws Exception {
        FakeProperties properties = properties();
        Files.createFile(temporary.resolve("Payments.xlsx"));
        AtomicReference<BotJobNativeOperationService.LaunchSpecification> launched = new AtomicReference<>();
        BotJobNativeOperationService service = new BotJobNativeOperationService(
                properties, file -> {}, launched::set);

        assertThrows(IllegalStateException.class, () -> service.launchExternalEngine(context("Android")));
        assertThrows(IllegalStateException.class, () -> service.launchExternalEngine(context("Web App")));
        assertEquals(null, launched.get());
    }

    private FakeProperties properties() {
        FakeProperties properties = new FakeProperties();
        properties.values.put(ARPropertyEnum.PATH_EXCEL, temporary.toString());
        properties.values.put(ARPropertyEnum.PATH_REPORT, temporary.toString());
        properties.values.put(ARPropertyEnum.PATH_DB, temporary.toString());
        return properties;
    }

    private static BotJobToolbarContext context(String projectType) {
        return new BotJobToolbarContext(1, 42, 7, 8, "Payments", projectType, "Bank", "https://test", true);
    }

    private static final class FakeProperties implements BotJobNativeOperationService.PropertyPort {
        private final Map<ARPropertyEnum, String> values = new EnumMap<>(ARPropertyEnum.class);
        public String get(ARPropertyEnum key) { return values.get(key); }
        public String configurationFileName() { return "TESTS.config"; }
    }
}

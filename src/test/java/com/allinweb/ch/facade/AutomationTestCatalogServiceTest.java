package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class AutomationTestCatalogServiceTest {
    private static final Pattern JUNIT_TEST = Pattern.compile(
            "(?m)^\\s*@(Test|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate)\\b");

    @Test
    void loadsAConsistentPackagedCatalogAcrossRecordedMigrationProjects() {
        JsonObject response = AutomationTestCatalogService.getInstance().list();
        assertTrue(response.get("ok").getAsBoolean(), response.toString());

        JsonArray tests = response.getAsJsonArray("tests");
        JsonObject summary = response.getAsJsonObject("summary");
        assertFalse(tests.size() == 0);
        assertEquals(tests.size(), summary.get("catalogEntries").getAsInt());

        Set<String> ids = new HashSet<>();
        Set<String> projects = new HashSet<>();
        for (JsonElement element : tests) {
            JsonObject test = element.getAsJsonObject();
            assertTrue(ids.add(test.get("id").getAsString()), "Duplicate catalog id: " + test);
            projects.add(test.get("project").getAsString());
        }
        assertTrue(projects.contains("AR Web Scanner"));
        assertTrue(projects.contains("AR React UI"));

        Set<String> recordedSources = new HashSet<>();
        for (JsonElement element : response.getAsJsonArray("sources")) {
            recordedSources.add(element.getAsJsonObject().get("project").getAsString());
        }
        assertTrue(recordedSources.contains("AR Web Scanner"));
        assertTrue(recordedSources.contains("AR React UI"));
        assertTrue(recordedSources.contains("AR Web Engine"));
    }

    @Test
    void catalogContainsEveryBackendJunitDeclarationInTheCurrentCheckout() throws IOException {
        Path testRoot = Path.of("src", "test", "java").toAbsolutePath().normalize();
        if (!Files.isDirectory(testRoot)) return;

        int discoveredDeclarations = 0;
        Set<String> discoveredFiles = new HashSet<>();
        try (Stream<Path> files = Files.walk(testRoot)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                int declarations = (int) JUNIT_TEST.matcher(source).results().count();
                if (declarations > 0) {
                    discoveredDeclarations += declarations;
                    discoveredFiles.add(testRoot.getParent().getParent().getParent()
                            .relativize(file)
                            .toString()
                            .replace('\\', '/'));
                }
            }
        }

        JsonArray tests = AutomationTestCatalogService.getInstance().list().getAsJsonArray("tests");
        int catalogDeclarations = 0;
        Set<String> catalogFiles = new HashSet<>();
        for (JsonElement element : tests) {
            JsonObject test = element.getAsJsonObject();
            if (!"AR Web Scanner".equals(test.get("project").getAsString())
                    || !"AUTOMATED_CASE".equals(test.get("recordType").getAsString())) continue;
            catalogDeclarations++;
            catalogFiles.add(test.get("sourcePath").getAsString());
        }

        assertEquals(discoveredDeclarations, catalogDeclarations, "Regenerate automation-tests.json");
        assertEquals(discoveredFiles, catalogFiles, "Regenerate automation-tests.json");
    }
}

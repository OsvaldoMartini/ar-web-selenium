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
    private static final Pattern LEADING_ANNOTATION =
            Pattern.compile("^@[A-Za-z_$][\\w$]*(?:\\s*\\([^)]*\\))?\\s*");
    private static final Pattern JAVA_METHOD = Pattern.compile(
            "\\b(?:void|[A-Za-z_$][\\w$\\.\\[\\]<>?,]*)\\s+([A-Za-z_$][\\w$]*)\\s*\\(");

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
        Set<String> discoveredMethods = new HashSet<>();
        try (Stream<Path> files = Files.walk(testRoot)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                int declarations = (int) JUNIT_TEST.matcher(source).results().count();
                if (declarations > 0) {
                    discoveredDeclarations += declarations;
                    String sourcePath = testRoot.getParent().getParent().getParent()
                            .relativize(file)
                            .toString()
                            .replace('\\', '/');
                    discoveredFiles.add(sourcePath);
                    String[] lines = source.split("\\R", -1);
                    for (int index = 0; index < lines.length; index++) {
                        if (!JUNIT_TEST.matcher(lines[index]).find()) continue;
                        discoveredMethods.add(sourcePath + "#" + javaMethodName(lines, index));
                    }
                }
            }
        }

        JsonArray tests = AutomationTestCatalogService.getInstance().list().getAsJsonArray("tests");
        int catalogDeclarations = 0;
        Set<String> catalogFiles = new HashSet<>();
        Set<String> catalogMethods = new HashSet<>();
        for (JsonElement element : tests) {
            JsonObject test = element.getAsJsonObject();
            if (!"AR Web Scanner".equals(test.get("project").getAsString())
                    || !"AUTOMATED_CASE".equals(test.get("recordType").getAsString())) continue;
            catalogDeclarations++;
            String sourcePath = test.get("sourcePath").getAsString();
            catalogFiles.add(sourcePath);
            catalogMethods.add(sourcePath + "#" + test.get("name").getAsString());
        }

        assertEquals(discoveredDeclarations, catalogDeclarations, "Regenerate automation-tests.json");
        assertEquals(discoveredFiles, catalogFiles, "Regenerate automation-tests.json");
        assertEquals(discoveredMethods, catalogMethods, "Regenerate automation-tests.json");
    }

    private static String javaMethodName(String[] lines, int annotationIndex) {
        StringBuilder signature = new StringBuilder();
        for (int index = annotationIndex; index < Math.min(lines.length, annotationIndex + 35); index++) {
            String trimmed = lines[index].trim();
            if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("*")) continue;
            while (trimmed.startsWith("@")) {
                var annotation = LEADING_ANNOTATION.matcher(trimmed);
                if (!annotation.find()) break;
                trimmed = trimmed.substring(annotation.end()).trim();
            }
            if (trimmed.isEmpty()) continue;
            if (!signature.isEmpty()) signature.append(' ');
            signature.append(trimmed);
            var method = JAVA_METHOD.matcher(signature);
            if (method.find()) return method.group(1);
            if (trimmed.contains("{") || trimmed.endsWith(";")) break;
        }
        return "unresolvedTestAtLine" + (annotationIndex + 1);
    }
}

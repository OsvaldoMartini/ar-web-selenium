package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.allinweb.ch.model.ElementDTO;
import com.google.gson.Gson;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/**
 * Compares the pre-scan dashboard dump ({@code elementDTO-PS-BJ.json}) against the legacy
 * Page Scanner dump ({@code elementDTO-PS.json}) so classification drift between the two
 * scan paths is visible and trackable.
 *
 * <p>Ground truth for classification is the ACTION the bot must execute on the element:
 * {@code input} = can type into it, {@code button} = can click it, {@code output} = can
 * read its value. The legacy scanner is known to misclassify new/unusual web elements, so
 * legacy violations are REPORTED but do not fail the build; violations in the NEW dump do
 * fail, because the new path must hold the action rule.
 *
 * <p>{@code definedName} is ignored: pre-scan intentionally skips the OCR
 * {@code ElementTextResolver}, so that field is empty in the {@code -BJ} dump by design.
 *
 * <p>How to generate the inputs (both against the SAME page):
 * <ol>
 *   <li>AR Web Factory → Page Scanner → writes {@code elementDTO-PS.json}</li>
 *   <li>Bot Job Details → PRE SCAN → Page Scanner → writes {@code elementDTO-PS-BJ.json}</li>
 * </ol>
 * Both land in {@code <path_db>/page_diagnostics/} ({@code path_db} from
 * {@code Config-4.2/TESTS.config} or the {@code -DARWebConfig} override). The test is
 * skipped when either file is missing. A diff report is written next to the dumps as
 * {@code elementDTO-PS-compare-report.txt}.
 */
class PreScanDumpComparisonTest {

    private static final String LEGACY_FILE = "elementDTO-PS.json";
    private static final String PRESCAN_FILE = "elementDTO-PS-BJ.json";
    private static final String REPORT_FILE = "elementDTO-PS-compare-report.txt";
    private static final String DIAG_SUBFOLDER = "page_diagnostics";
    private static final int MAX_LISTED = 50;

    @Test
    void preScanDumpMatchesLegacyClassification() throws Exception {
        Path diagDir = resolveDiagnosticsDir();
        assumeTrue(diagDir != null, "path_db not configured (Config-4.2/TESTS.config) — skipping");

        Path legacyPath = diagDir.resolve(LEGACY_FILE);
        Path preScanPath = diagDir.resolve(PRESCAN_FILE);
        assumeTrue(Files.exists(legacyPath), "Missing " + legacyPath + " — run the AR Web Factory Page Scanner first");
        assumeTrue(Files.exists(preScanPath), "Missing " + preScanPath + " — run PRE SCAN → Page Scanner first");

        ElementDTO[] legacy = readDump(legacyPath);
        ElementDTO[] preScan = readDump(preScanPath);

        Map<String, ElementDTO> legacyByXPath = indexByXPath(legacy);
        Map<String, ElementDTO> preScanByXPath = indexByXPath(preScan);

        List<String> missingInPreScan = new ArrayList<>();
        List<String> extraInPreScan = new ArrayList<>();
        List<String> classificationMismatches = new ArrayList<>();
        List<String> legacyUnclassified = new ArrayList<>();
        List<String> preScanUnclassified = new ArrayList<>();

        for (Map.Entry<String, ElementDTO> entry : legacyByXPath.entrySet()) {
            ElementDTO legacyDto = entry.getValue();
            String legacyBucket = actionBucket(legacyDto);
            if ("unclassified".equals(legacyBucket)) {
                legacyUnclassified.add(describe(legacyDto, legacyBucket));
            }

            ElementDTO preScanDto = preScanByXPath.get(entry.getKey());
            if (preScanDto == null) {
                missingInPreScan.add(describe(legacyDto, legacyBucket));
                continue;
            }

            String preScanBucket = actionBucket(preScanDto);
            if (!legacyBucket.equals(preScanBucket)) {
                classificationMismatches.add(
                        describe(legacyDto, legacyBucket) + "  ->  PRE SCAN says '" + preScanBucket + "'");
            }
        }

        for (Map.Entry<String, ElementDTO> entry : preScanByXPath.entrySet()) {
            ElementDTO preScanDto = entry.getValue();
            String bucket = actionBucket(preScanDto);
            if ("unclassified".equals(bucket)) {
                preScanUnclassified.add(describe(preScanDto, bucket));
            }
            if (!legacyByXPath.containsKey(entry.getKey())) {
                extraInPreScan.add(describe(preScanDto, bucket));
            }
        }

        String report = buildReport(
                legacyPath,
                preScanPath,
                legacy.length,
                preScan.length,
                missingInPreScan,
                extraInPreScan,
                classificationMismatches,
                legacyUnclassified,
                preScanUnclassified);
        Files.writeString(diagDir.resolve(REPORT_FILE), report, StandardCharsets.UTF_8);
        System.out.println(report);

        // Hard invariant for the NEW path only: every pre-scan element must map to an
        // executable action. Legacy violations are known bugs and only reported.
        assertTrue(
                preScanUnclassified.isEmpty(),
                "PRE SCAN produced elements without an executable action bucket "
                        + "(input=type / button=click / output=read):\n"
                        + String.join("\n", preScanUnclassified));
    }

    // ── classification: the action the bot must execute ────────────────────────

    /**
     * input = can type, button = can click, output = can read. typeElement (the scanner's
     * decision) wins; raw tag is only a fallback for dumps predating the decided-category
     * convention. Returns "unclassified" when no action can be derived.
     */
    private static String actionBucket(ElementDTO dto) {
        String type = Objects.toString(dto.getTypeElement(), "").toLowerCase(Locale.ROOT);
        if (type.equals("input")) return "input";
        if (type.equals("button")) return "button";
        if (type.equals("output") || type.equals("o") || type.equals("label")) return "output";

        String tag = Objects.toString(dto.getTagName(), "").toLowerCase(Locale.ROOT);
        if (tag.equals("input") || tag.equals("textarea")) return "input";
        if (tag.equals("button")
                || tag.equals("a")
                || tag.equals("link")
                || tag.equals("select")
                || tag.equals("option")
                || tag.equals("svg")
                || tag.startsWith("mat-")) return "button";
        if (tag.equals("label")) return "output";

        return "unclassified";
    }

    private static String describe(ElementDTO dto, String bucket) {
        return String.format(
                "[%s] tag=%s typeElement=%s someText='%s' xPath=%s",
                bucket,
                Objects.toString(dto.getTagName(), ""),
                Objects.toString(dto.getTypeElement(), ""),
                truncate(Objects.toString(dto.getSomeText(), ""), 60),
                Objects.toString(dto.getXPath(), ""));
    }

    // ── plumbing ────────────────────────────────────────────────────────────────

    /** {@code -DARWebConfig} override first, then {@code <user.dir>/Config-4.2/TESTS.config}. */
    private static Path resolveDiagnosticsDir() {
        String configFile = System.getProperty("ARWebConfig");
        if (configFile == null) {
            configFile = System.getProperty("user.dir") + "/Config-4.2/TESTS.config";
        }
        Properties properties = new Properties();
        try (FileInputStream in = new FileInputStream(configFile)) {
            properties.load(in);
        } catch (IOException e) {
            return null;
        }
        String pathDb = properties.getProperty("path_db");
        if (pathDb == null || pathDb.isBlank()) {
            return null;
        }
        return Paths.get(pathDb.trim(), DIAG_SUBFOLDER);
    }

    private static ElementDTO[] readDump(Path path) throws IOException {
        String json = Files.readString(path, StandardCharsets.UTF_8);
        ElementDTO[] parsed = new Gson().fromJson(json, ElementDTO[].class);
        return parsed == null ? new ElementDTO[0] : parsed;
    }

    /** Same key the dumps dedup on. Later duplicates win, mirroring the writer. */
    private static Map<String, ElementDTO> indexByXPath(ElementDTO[] elements) {
        Map<String, ElementDTO> byXPath = new LinkedHashMap<>();
        for (ElementDTO dto : elements) {
            if (dto == null) continue;
            String xPath = Objects.toString(dto.getXPath(), "");
            if (xPath.isBlank()) continue;
            byXPath.put(xPath, dto);
        }
        return byXPath;
    }

    private static String buildReport(
            Path legacyPath,
            Path preScanPath,
            int legacyCount,
            int preScanCount,
            List<String> missingInPreScan,
            List<String> extraInPreScan,
            List<String> classificationMismatches,
            List<String> legacyUnclassified,
            List<String> preScanUnclassified) {
        StringBuilder sb = new StringBuilder();
        sb.append("ELEMENT DTO COMPARISON — legacy Page Scanner vs PRE SCAN dashboard\n");
        sb.append("legacy : ")
                .append(legacyPath)
                .append(" (")
                .append(legacyCount)
                .append(" elements)\n");
        sb.append("prescan: ")
                .append(preScanPath)
                .append(" (")
                .append(preScanCount)
                .append(" elements)\n");
        sb.append("note   : definedName is ignored (pre-scan skips the OCR resolver by design)\n\n");

        appendSection(sb, "MISSING IN PRE SCAN (legacy found, pre-scan did not)", missingInPreScan);
        appendSection(sb, "EXTRA IN PRE SCAN (pre-scan found, legacy did not)", extraInPreScan);
        appendSection(sb, "CLASSIFICATION MISMATCHES (action bucket differs)", classificationMismatches);
        appendSection(sb, "LEGACY UNCLASSIFIED (known legacy bugs — reported only)", legacyUnclassified);
        appendSection(sb, "PRE SCAN UNCLASSIFIED (MUST be empty — fails the test)", preScanUnclassified);
        return sb.toString();
    }

    private static void appendSection(StringBuilder sb, String title, List<String> lines) {
        sb.append("== ").append(title).append(": ").append(lines.size()).append("\n");
        lines.stream()
                .limit(MAX_LISTED)
                .forEach(line -> sb.append("   ").append(line).append("\n"));
        if (lines.size() > MAX_LISTED) {
            sb.append("   ... and ").append(lines.size() - MAX_LISTED).append(" more\n");
        }
        sb.append("\n");
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }
}

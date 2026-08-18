package com.allinweb.ch.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.driver.ARPlaywrightDriver;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.facade.PerformDBEngine;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.HomeBankingLoadDTO;
import com.allinweb.ch.model.HomeUrlDTO;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.ReferenceLoadDTO;
import com.allinweb.ch.util.ARConstantsEngine;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.allinweb.ch.util.ErrorMessage;
import com.google.common.base.Strings;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * DB-backed direct bot-job test for the BancaStato contact form.
 *
 * <p>This is intentionally disabled by default because it opens a real browser and hits a live
 * website. It loads a sanitized config and consistent SQLite snapshot under the JUnit temporary
 * directory; the production reference files remain read-only. Click/fill actions are always
 * dry-run locator checks; mutating action coverage belongs to {@link BancaStatoLocalhostPlaywrightIT}.
 * Enable the safe diagnostic with:
 *
 * <pre>
 * mvn -Dtest=BancaStatoBotJobPlaywrightIT -DbancastatoIT=true test
 * </pre>
 */
@Isolated("Mutates ARPropertyManager, ARWebDriver, and PerformLists singletons")
class BancaStatoBotJobPlaywrightIT {

    private static final int HOME_BANKING_ID = 2;
    private static final int BOT_JOB_ID = 5;
    private static final int BLOCK_ORDER_NUMBER = 1;
    private static final String ENDPOINT = "https://www.bancastato.ch/supporto-e-contatti/formulario-di-contatto";

    private final PerformDBEngine performDBEngine = PerformDBEngine.getInstance();
    private final PerformDataBase performDataBase = PerformDataBase.getInstance();
    private final PerformLists performLists = PerformLists.getInstance();
    private final ARPropertyManager properties = ARPropertyManager.getInstance();
    private BancaStatoIsolatedFixture fixture;

    @TempDir
    Path tempDirectory;

    @BeforeEach
    void loadBancaStatoTestConfiguration() throws Exception {
        fixture = BancaStatoIsolatedFixture.create(tempDirectory);
        fixture.activate(properties);
    }

    @AfterEach
    void closeBrowser() {
        try {
            holdBrowserOpenWhenRequested();
            ARWebDriver.getInstance().closeBrowser();
        } finally {
            performLists.clearAllLists();
            if (fixture != null) {
                fixture.close();
            }
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "bancastatoIT", matches = "true")
    void runsBancaStatoBlockOneWithPlaywrightDiagnosticReport() throws Exception {
        forcePlaywrightOnly();

        BotJobLoadDTO botJob = loadBotJob(BOT_JOB_ID);
        assertEquals(HOME_BANKING_ID, botJob.getHomeBankingId());

        BlockLoadDTO block = botJob.getBlockLoadDTOList().stream()
                .filter(candidate -> candidate.getBlockOrderNumber() != null
                        && candidate.getBlockOrderNumber() == BLOCK_ORDER_NUMBER)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Block #1 was not found for bot job " + BOT_JOB_ID));

        List<InstructionLoad> instructions = loadInstructions(botJob, block);
        runPlaywrightDiagnostic(botJob, block, instructions);

        assertTrue(!instructions.isEmpty(), "No instructions were loaded from block #1");
    }

    private BotJobLoadDTO loadBotJob(int botJobId) {
        ErrorMessage error = performDBEngine.loadHomeBanking(null);
        assertNoError(error, "loadHomeBanking");

        error = performDBEngine.loadHomeUrls(HOME_BANKING_ID);
        assertNoError(error, "loadHomeUrls");

        error = performDBEngine.loadCompleteJobs(botJobId);
        assertNoError(error, "loadCompleteJobs");

        assertTrue(!performLists.getListBotJob().isEmpty(), "No bot job loaded for id " + botJobId);
        BotJobLoadDTO botJob = performLists.getListBotJob().get(0);
        assertNotNull(botJob.getBlockLoadDTOList(), "Bot job blocks were not loaded");

        HomeBankingLoadDTO homeBanking = performLists.getHomeBankingById(botJob.getHomeBankingId());
        assertNotNull(homeBanking, "Home banking " + botJob.getHomeBankingId() + " was not loaded");
        homeBanking.setUrl(ENDPOINT);
        botJob.setHomeBankingLoadDTO(homeBanking);

        HomeUrlDTO homeUrl = performLists.getHomeUrlByBankId(botJob.getHomeBankingId(), botJob.getHomeUrlId());
        if (homeUrl != null) {
            homeUrl.setUrl(ENDPOINT);
        }
        return botJob;
    }

    private List<InstructionLoad> loadInstructions(BotJobLoadDTO botJob, BlockLoadDTO block) {
        List<InstructionLoad> instructions =
                performDataBase.loadBlockInstructionsReadOnly(botJob.getId(), block.getId());
        assertTrue(
                !instructions.isEmpty(),
                "Block #" + BLOCK_ORDER_NUMBER + " has no instructions in bot job " + botJob.getId());
        return instructions;
    }

    private String runPlaywrightDiagnostic(BotJobLoadDTO botJob, BlockLoadDTO block, List<InstructionLoad> instructions)
            throws Exception {
        String browserType = properties.getProperty(ARPropertyEnum.BROWSER);
        ARPlaywrightDriver driver = ARWebDriver.getInstance().getPlaywrightDriver();

        StringBuilder report = new StringBuilder();
        report.append("\n================ BancaStato Playwright Diagnostic ================\n")
                .append("database: ")
                .append(effectiveDatabasePath())
                .append('\n')
                .append("homeBankingId: ")
                .append(botJob.getHomeBankingId())
                .append('\n')
                .append("botJobId: ")
                .append(botJob.getId())
                .append('\n')
                .append("botJobName: ")
                .append(botJob.getName())
                .append('\n')
                .append("blockId: ")
                .append(block.getId())
                .append('\n')
                .append("blockOrderNumber: ")
                .append(block.getBlockOrderNumber())
                .append('\n')
                .append("blockName: ")
                .append(block.getName())
                .append('\n')
                .append("endpoint: ")
                .append(ENDPOINT)
                .append('\n')
                .append("instructionsLoaded: ")
                .append(instructions.size())
                .append('\n');

        int attempted = 0;
        int succeeded = 0;
        int failed = 0;
        int unsupported = 0;
        try {
            driver.openReadOnlyDiagnostic(browserType, ENDPOINT, false);
            report.append("openedUrl: ").append(driver.currentUrl()).append('\n');
            report.append("liveActionsEnabled: false (read-only diagnostic)\n");

            for (InstructionLoad instruction : instructions) {
                attempted++;
                String action = firstAction(instruction);
                String beforeUrl = safeCurrentUrl(driver);
                String locatorStatus = locatorStatus(driver, instruction);
                boolean ok = false;
                String detail = "";

                try {
                    switch (action) {
                        case ARConstantsEngine.CLICK:
                        case ARConstantsEngine.OTHER:
                            ok = hasResolvableLocator(locatorStatus);
                            detail = "read-only diagnostic; click was not executed";
                            break;
                        case ARConstantsEngine.INSERT:
                            ok = hasResolvableLocator(locatorStatus);
                            detail = "read-only diagnostic; fill was not executed";
                            break;
                        case ARConstantsEngine.OUTPUT:
                            String text = driver.text(instruction);
                            ok = !Strings.isNullOrEmpty(text);
                            detail = "output text=" + abbreviate(text, 120);
                            break;
                        default:
                            unsupported++;
                            detail = "unsupported action";
                            break;
                    }
                } catch (Exception error) {
                    detail = "exception=" + error.getMessage();
                }

                String afterUrl = safeCurrentUrl(driver);
                if (ok) {
                    succeeded++;
                } else if (!"unsupported action".equals(detail)) {
                    failed++;
                }

                report.append('\n')
                        .append(stepHeader(attempted, instruction))
                        .append('\n')
                        .append("  action: ")
                        .append(action)
                        .append('\n')
                        .append("  locatorFoundBefore: ")
                        .append(locatorStatus)
                        .append('\n')
                        .append("  result: ")
                        .append(ok ? "OK" : "FAILED")
                        .append(" - ")
                        .append(detail)
                        .append('\n')
                        .append("  urlBefore: ")
                        .append(beforeUrl)
                        .append('\n')
                        .append("  urlAfter:  ")
                        .append(afterUrl)
                        .append('\n')
                        .append("  navigationChanged: ")
                        .append(!safeEquals(beforeUrl, afterUrl))
                        .append('\n')
                        .append("  xpath: ")
                        .append(nullToBlank(instruction.getXpath()))
                        .append('\n')
                        .append("  css: ")
                        .append(nullToBlank(instruction.getCssSelector()))
                        .append('\n')
                        .append("  references: ")
                        .append(referenceSummary(instruction))
                        .append('\n');
            }
        } catch (Exception error) {
            report.append("\nRUN ERROR: ").append(error.getMessage()).append('\n');
            throw error;
        } finally {
            report.append("\nSUMMARY attempted=")
                    .append(attempted)
                    .append(" succeeded=")
                    .append(succeeded)
                    .append(" failed=")
                    .append(failed)
                    .append(" unsupported=")
                    .append(unsupported)
                    .append('\n')
                    .append("=================================================================\n");
            String finalReport = report.toString();
            System.out.println(finalReport);
            writeReport(finalReport);
        }

        return report.toString();
    }

    private String locatorStatus(ARPlaywrightDriver driver, InstructionLoad instruction) {
        List<String> checks = new ArrayList<>();
        if (!Strings.isNullOrEmpty(instruction.getCssSelector())) {
            checks.add("css=" + existsByCss(driver, instruction.getCssSelector()));
        }
        if (!Strings.isNullOrEmpty(instruction.getXpath())) {
            checks.add("xpath=" + existsByXpath(driver, instruction.getXpath()));
        }
        for (ReferenceLoadDTO ref : safeReferences(instruction)) {
            String type =
                    ref.getReferenceType() == null ? "" : ref.getReferenceType().toLowerCase(Locale.ROOT);
            String value = ref.getValue();
            if (Strings.isNullOrEmpty(value)) continue;
            if (type.contains("test-id") || type.contains("data-testid")) {
                checks.add(ref.getReferenceType() + "=" + existsByCss(driver, "[data-testid='" + jsCss(value) + "']"));
            } else if (type.contains("id") && !type.contains("xpath")) {
                checks.add(ref.getReferenceType() + "=" + existsByCss(driver, "#" + jsCss(value)));
            } else if (type.contains("name")) {
                checks.add(ref.getReferenceType() + "=" + existsByCss(driver, "[name='" + jsCss(value) + "']"));
            }
        }
        return checks.isEmpty() ? "no locator fields to pre-check" : String.join(", ", checks);
    }

    private boolean existsByCss(ARPlaywrightDriver driver, String css) {
        try {
            Object result = driver.evaluate("(selector) => !!document.querySelector(selector)", css);
            return Boolean.TRUE.equals(result);
        } catch (Exception error) {
            return false;
        }
    }

    private boolean existsByXpath(ARPlaywrightDriver driver, String xpath) {
        try {
            Object result = driver.evaluate(
                    "(xpath) => !!document.evaluate(xpath, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue",
                    xpath);
            return Boolean.TRUE.equals(result);
        } catch (Exception error) {
            return false;
        }
    }

    private void forcePlaywrightOnly() {
        if (properties.getProperty(ARPropertyEnum.BROWSER) == null
                || properties.getProperty(ARPropertyEnum.BROWSER).isBlank()) {
            properties.setProperty(ARPropertyEnum.BROWSER.getValue(), ARConstantsEngine.EDGE);
        }
    }

    private static void assertNoError(ErrorMessage error, String operation) {
        if (error != null) {
            throw new AssertionError(operation + " failed: " + error.getErrorMessage());
        }
    }

    private String effectiveDatabasePath() {
        return properties.getProperty(ARPropertyEnum.PATH_DB) + "\\database.db";
    }

    private static String firstAction(InstructionLoad instruction) {
        String actions = instruction.getActions();
        return Strings.isNullOrEmpty(actions) ? "" : actions.split(ARConstantsEngine.ACTION_SPECIFICATIONS_SPLITTER)[0];
    }

    private static String safeCurrentUrl(ARPlaywrightDriver driver) {
        try {
            return driver.currentUrl();
        } catch (Exception error) {
            return "(url unavailable: " + error.getMessage() + ")";
        }
    }

    private static String stepHeader(int index, InstructionLoad instruction) {
        return "#"
                + index
                + " instructionId="
                + instruction.getId()
                + " order="
                + instruction.getInstructionOrderNumber()
                + " name="
                + nullToBlank(instruction.getName());
    }

    private static String referenceSummary(InstructionLoad instruction) {
        List<ReferenceLoadDTO> refs = safeReferences(instruction);
        if (refs.isEmpty()) {
            return "(none)";
        }
        List<String> values = new ArrayList<>();
        for (ReferenceLoadDTO ref : refs) {
            values.add(nullToBlank(ref.getReferenceType()) + "=(redacted)");
        }
        return String.join(" | ", values);
    }

    private static boolean hasResolvableLocator(String locatorStatus) {
        return locatorStatus != null && locatorStatus.contains("=true");
    }

    private static List<ReferenceLoadDTO> safeReferences(InstructionLoad instruction) {
        return instruction.getReferenceLoadDTOList() == null ? List.of() : instruction.getReferenceLoadDTOList();
    }

    private static boolean safeEquals(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private static String abbreviate(String value, int max) {
        if (value == null || value.length() <= max) {
            return nullToBlank(value);
        }
        return value.substring(0, max) + "...";
    }

    private static String jsCss(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private static void writeReport(String report) {
        try {
            Path reportPath = Path.of("target", "bancastato-playwright-diagnostic.txt");
            Files.createDirectories(reportPath.getParent());
            Files.writeString(reportPath, report, StandardCharsets.UTF_8);
            System.out.println("Diagnostic report written to: " + reportPath.toAbsolutePath());
        } catch (IOException error) {
            System.out.println("Could not write diagnostic report: " + error.getMessage());
        }
    }

    private static void holdBrowserOpenWhenRequested() {
        if (!Boolean.parseBoolean(System.getProperty("bancastatoKeepBrowserOpen", "false"))) {
            return;
        }
        System.out.println("BancaStato Playwright browser is still open. Press Enter here to finish the test.");
        try {
            new InputStreamReader(System.in, StandardCharsets.UTF_8).read();
        } catch (IOException error) {
            System.out.println("Browser hold ended: " + error.getMessage());
        }
    }
}

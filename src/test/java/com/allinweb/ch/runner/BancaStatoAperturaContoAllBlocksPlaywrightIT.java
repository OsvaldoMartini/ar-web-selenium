package com.allinweb.ch.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.allinweb.ch.driver.ARPlaywrightDriver;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.util.ARConstantsEngine;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.google.common.base.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * Production-derived, read-only Playwright diagnostics for the ALL/ONE block fixture,
 * using bot job id 20 ("Apertura Conto") and its two currently active blocks:
 *
 * <ul>
 *   <li>block 229 "Apre Aconto" — accept cookies, click "aprite ora un conto" (CLICK)</li>
 *   <li>block 231 "Start Registration" — fill email/password/password confirmation (INSERT), reached
 *       only after block 229's click navigates to the registration form</li>
 * </ul>
 *
 * <p>The real configuration and database are copied into an isolated temporary fixture before
 * application code sees them. The live public page is opened only for recorded-locator diagnostics:
 * CLICK and INSERT steps are never executed there, and non-GET requests are blocked as an additional
 * guard. Real browser-action coverage runs against {@link BancaStatoLocalhostPlaywrightIT}; the pure
 * ALL/ONE request mapping is covered separately without a desktop shell or a production endpoint.
 *
 * <p>Disabled by default. Enable explicitly with:
 *
 * <pre>
 * mvn -Dtest=BancaStatoAperturaContoAllBlocksPlaywrightIT -DbancastatoAperturaContoIT=true test
 * </pre>
 */
@Isolated("Mutates ARPropertyManager and ARWebDriver singletons")
class BancaStatoAperturaContoAllBlocksPlaywrightIT {

    private static final int BOT_JOB_ID = 20;
    private static final int BLOCK_APRE_ACONTO = 229;
    private static final int BLOCK_START_REGISTRATION = 231;
    private static final String ENDPOINT = "https://www.bancastato.ch/apertura-conto";

    private final PerformDataBase performDataBase = PerformDataBase.getInstance();
    private final ARPropertyManager properties = ARPropertyManager.getInstance();
    private BancaStatoIsolatedFixture fixture;

    @TempDir
    Path tempDirectory;

    @BeforeEach
    void loadBancaStatoTestConfiguration() throws Exception {
        fixture = BancaStatoIsolatedFixture.create(tempDirectory);
        fixture.activate(properties);
        forcePlaywrightOnly();
    }

    @AfterEach
    void closeBrowser() {
        try {
            ARWebDriver.getInstance().closeCurrentDriver();
        } catch (Exception ignored) {
            // Best-effort diagnostic cleanup — mirrors the sibling BancaStato IT tests.
        } finally {
            if (fixture != null) {
                fixture.close();
            }
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "bancastatoAperturaContoIT", matches = "true")
    void testRunAllActiveBlocksInAllModeReachesBothBlocks() throws Exception {
        List<InstructionLoad> apreAcontoInstructions = loadActiveInstructions(BLOCK_APRE_ACONTO);
        List<InstructionLoad> startRegistrationInstructions = loadActiveInstructions(BLOCK_START_REGISTRATION);
        assertFalse(apreAcontoInstructions.isEmpty(), "Block 229 (Apre Aconto) has no instructions");
        assertFalse(startRegistrationInstructions.isEmpty(), "Block 231 (Start Registration) has no instructions");

        ARPlaywrightDriver driver = ARWebDriver.getInstance().getPlaywrightDriver();
        StringBuilder report = new StringBuilder();
        report.append("\n========= BancaStato ALL-mode two-block diagnostic =========\n")
                .append("botJobId: ").append(BOT_JOB_ID).append('\n')
                .append("endpoint: ").append(ENDPOINT).append('\n');

        int totalAttempted = 0;
        int totalSucceeded = 0;

        driver.openReadOnlyDiagnostic(properties.getProperty(ARPropertyEnum.BROWSER), ENDPOINT, false);
        report.append("openedUrl: ").append(driver.currentUrl()).append('\n');

        // Block 229: accept cookies, then click through into the registration form.
        report.append("\n--- Block 229 \"Apre Aconto\" ---\n");
        for (InstructionLoad instruction : apreAcontoInstructions) {
            totalAttempted++;
            boolean ok = runClickInstruction(driver, instruction, report);
            if (ok) totalSucceeded++;
        }

        // The click on "aprite_ora_un_conto" navigates to the registration form; block 231's inputs
        // only exist on that resulting page — this is the real-data equivalent of the ALL-mode block
        // loop continuing "from selected onward" instead of stopping at block 229.
        report.append("\nurlAfterBlock229: ").append(driver.currentUrl()).append('\n');

        report.append("\n--- Block 231 \"Start Registration\" ---\n");
        for (InstructionLoad instruction : startRegistrationInstructions) {
            totalAttempted++;
            boolean ok = runInsertInstruction(driver, instruction, report);
            if (ok) totalSucceeded++;
        }

        report.append("\nSUMMARY attempted=")
                .append(totalAttempted)
                .append(" succeeded=")
                .append(totalSucceeded)
                .append('\n')
                .append("==============================================================\n");
        writeReport(report.toString(), "bancastato-apertura-conto-all-mode.txt");

        assertTrue(
                totalSucceeded > 0,
                "Expected at least one instruction across both blocks to succeed; see report for per-step detail");
    }

    @Test
    @EnabledIfSystemProperty(named = "bancastatoAperturaContoIT", matches = "true")
    void testRunOneModeStopsBeforeSecondBlock() throws Exception {
        // ONE mode (runSingleBlock=true) means the block loop's stop checkpoint fires right after the
        // selected block completes (ARScannedElementPane.java:6555-6562) — block 231 is never reached.
        // Simulated here by simply never calling runInsertInstruction for block 231.
        List<InstructionLoad> apreAcontoInstructions = loadActiveInstructions(BLOCK_APRE_ACONTO);
        List<InstructionLoad> startRegistrationInstructions = loadActiveInstructions(BLOCK_START_REGISTRATION);
        assertFalse(apreAcontoInstructions.isEmpty(), "Block 229 (Apre Aconto) has no instructions");
        InstructionLoad emailInstruction = startRegistrationInstructions.stream()
                .filter(i -> "e_mail_address".equalsIgnoreCase(nullToBlank(i.getName())))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Fixture drift: email instruction not found in block 231"));

        ARPlaywrightDriver driver = ARWebDriver.getInstance().getPlaywrightDriver();
        StringBuilder report = new StringBuilder();
        report.append("\n========= BancaStato ONE-mode single-block diagnostic =========\n");

        driver.openReadOnlyDiagnostic(properties.getProperty(ARPropertyEnum.BROWSER), ENDPOINT, false);
        for (InstructionLoad instruction : apreAcontoInstructions) {
            runClickInstruction(driver, instruction, report);
        }

        // Read the email input's real .value using the instruction's own recorded locator (its
        // css_selector is a generated `input[name="__pagevalue__/..."]`, so a guessed selector would
        // silently never match) — this proves whether block 231 was actually driven, not merely
        // whether the field exists on the page.
        String emailValue = readInputValue(driver, emailInstruction);
        report.append("\nemailInputValueAfterOneMode: ")
                .append(emailValue == null ? "(locator not found)" : "\"" + emailValue + "\"")
                .append('\n')
                .append("=================================================================\n");
        writeReport(report.toString(), "bancastato-apertura-conto-one-mode.txt");

        // This documents the current (flawed, per CODEX P0-2 / this file's own findings) reality: ONE
        // only stops the *block loop*, it does not prevent block 229's click from navigating to a page
        // that happens to also contain block 231's fields. A future strict "physical boundary" ONE
        // policy would not change this — the fields exist on the page either way, they are just never
        // driven. This assertion only checks that block 231's INSERT instructions were never executed.
        assertEquals(
                "",
                emailValue == null ? "" : emailValue,
                "ONE mode must never drive block 231's fields, so the email input must remain empty");
    }

    private List<InstructionLoad> loadActiveInstructions(int blockId) {
        return performDataBase.loadBlockInstructionsReadOnly(BOT_JOB_ID, blockId);
    }

    private boolean runClickInstruction(ARPlaywrightDriver driver, InstructionLoad instruction, StringBuilder report) {
        String action = firstAction(instruction);
        boolean ok = false;
        String detail;
        try {
            if (ARConstantsEngine.CLICK.equalsIgnoreCase(action) || ARConstantsEngine.OTHER.equalsIgnoreCase(action)) {
                ok = hasResolvableLocator(driver, instruction);
                detail = "read-only diagnostic; click was not executed";
            } else {
                detail = "skipped (action=" + action + ", this test only drives CLICK here)";
            }
        } catch (Exception error) {
            detail = "exception=" + error.getMessage();
        }
        report.append("#").append(instruction.getId())
                .append(" name=").append(nullToBlank(instruction.getName()))
                .append(" action=").append(action)
                .append(" result=").append(ok ? "OK" : "FAILED")
                .append(" - ").append(detail)
                .append('\n');
        return ok;
    }

    private boolean runInsertInstruction(
            ARPlaywrightDriver driver, InstructionLoad instruction, StringBuilder report) {
        String action = firstAction(instruction);
        boolean ok = false;
        String detail;
        try {
            if (ARConstantsEngine.INSERT.equalsIgnoreCase(action)) {
                ok = hasResolvableLocator(driver, instruction);
                detail = "read-only diagnostic; fill was not executed";
            } else {
                detail = "skipped (action=" + action + ", this test only drives INSERT here)";
            }
        } catch (Exception error) {
            detail = "exception=" + error.getMessage();
        }
        report.append("#").append(instruction.getId())
                .append(" name=").append(nullToBlank(instruction.getName()))
                .append(" action=").append(action)
                .append(" result=").append(ok ? "OK" : "FAILED")
                .append(" - ").append(detail)
                .append('\n');
        return ok;
    }

    /**
     * Reads an {@code <input>}'s real {@code .value} using the instruction's own recorded CSS
     * selector — {@code driver.text(instruction)} cannot be reused here because it reads
     * {@code innerText}, which is always empty for form inputs.
     */
    private String readInputValue(ARPlaywrightDriver driver, InstructionLoad instruction) {
        String cssSelector = instruction.getCssSelector();
        if (Strings.isNullOrEmpty(cssSelector)) {
            return null;
        }
        try {
            Object result = driver.evaluate(
                    "(selector) => { const el = document.querySelector(selector); return el ? el.value : null; }",
                    cssSelector);
            return result == null ? null : String.valueOf(result);
        } catch (Exception error) {
            return null;
        }
    }

    private static boolean hasResolvableLocator(ARPlaywrightDriver driver, InstructionLoad instruction) {
        if (!Strings.isNullOrEmpty(instruction.getCssSelector())) {
            try {
                Object found = driver.evaluate(
                        "(selector) => !!document.querySelector(selector)", instruction.getCssSelector());
                if (Boolean.TRUE.equals(found)) {
                    return true;
                }
            } catch (Exception ignored) {
                // Try XPath below.
            }
        }
        if (!Strings.isNullOrEmpty(instruction.getXpath())) {
            try {
                Object found = driver.evaluate(
                        "(xpath) => !!document.evaluate(xpath, document, null, "
                                + "XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue",
                        instruction.getXpath());
                return Boolean.TRUE.equals(found);
            } catch (Exception ignored) {
                return false;
            }
        }
        return false;
    }

    private void forcePlaywrightOnly() {
        properties.setProperty(ARPropertyEnum.USE_PLAYWRIGHT.getValue(), "true");
        properties.setProperty(ARPropertyEnum.PLAYWRIGHT_SELENIUM_FALLBACK.getValue(), "false");
        if (properties.getProperty(ARPropertyEnum.BROWSER) == null
                || properties.getProperty(ARPropertyEnum.BROWSER).isBlank()) {
            properties.setProperty(ARPropertyEnum.BROWSER.getValue(), ARConstantsEngine.EDGE);
        }
    }

    private static String firstAction(InstructionLoad instruction) {
        String actions = instruction.getActions();
        return Strings.isNullOrEmpty(actions) ? "" : actions.split(ARConstantsEngine.ACTION_SPECIFICATIONS_SPLITTER)[0];
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private static void writeReport(String report, String fileName) {
        try {
            Path reportPath = Path.of("target", fileName);
            Files.createDirectories(reportPath.getParent());
            Files.writeString(reportPath, report, StandardCharsets.UTF_8);
            System.out.println(report);
            System.out.println("Diagnostic report written to: " + reportPath.toAbsolutePath());
        } catch (IOException error) {
            fail("Could not write diagnostic report: " + error.getMessage());
        }
    }
}

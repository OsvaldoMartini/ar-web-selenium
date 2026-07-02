package com.allinweb.ch.runner;

import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.FieldData;
import com.allinweb.ch.model.HomeBankingLoadDTO;
import com.allinweb.ch.model.HomeUrlDTO;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.util.ARConstantsEngine;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.google.common.base.Strings;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * TEST RUN — Playwright-native, single-browser local execution of ONE selected block.
 *
 * <p>Unlike the "Launch" button (which spawns the external AR_Web_Engine.jar) and unlike the
 * Scanner's hybrid Selenium+Playwright session, this opens a single Playwright browser at the
 * bot job's endpoint URL and drives the selected block's instructions straight through
 * {@link ARPlaywrightDriver} (click/fill/text). It is aimed at the GEN FLOW-generated
 * navigation blocks (CLICK per link/button) but runs any CLICK/INSERT/OUTPUT block.
 *
 * <p>All Playwright calls funnel onto the driver's own single thread, so this runner is safe
 * to invoke from a background worker thread.
 */
@Slf4j
public final class TestRunLauncher {

    private static final long STEP_PAUSE_MS = 700;

    private final PerformLists performLists = PerformLists.getInstance();
    private final PerformDataBase performDataBase = PerformDataBase.getInstance();
    private final ARPropertyManager propertyManager = ARPropertyManager.getInstance();

    public record TestRunResult(int clicked, int inserted, int read, int failed, String endpoint) {}

    /** Opens the endpoint in a Playwright browser and executes the selected block's steps. */
    public TestRunResult run(BotJobLoadDTO botJob, BlockLoadDTO block) throws Exception {
        HomeUrlDTO homeUrl = performLists.getHomeUrlByBankId(botJob.getHomeBankingId(), botJob.getHomeUrlId());
        if (homeUrl == null || Strings.isNullOrEmpty(homeUrl.getUrl())) {
            throw new IllegalStateException("No endpoint URL is configured for this bot job.");
        }
        String url = homeUrl.getUrl();

        HomeBankingLoadDTO homeBanking = performLists.getHomeBankingById(botJob.getHomeBankingId());
        String optionsConfig =
                homeBanking != null && homeBanking.getOptionsConfig() != null ? homeBanking.getOptionsConfig() : "";
        String browserType = propertyManager.getProperty(ARPropertyEnum.BROWSER);

        List<InstructionLoad> instructions =
                performDataBase.loadBlockInstructionsReadOnly(botJob.getId(), block.getId());
        if (instructions.isEmpty()) {
            throw new IllegalStateException("Block \"" + block.getName() + "\" has no instructions to run.");
        }

        ARWebDriver arWebDriver = ARWebDriver.getInstance();
        log.info(
                "TEST RUN — opening Playwright at {} for block \"{}\" ({} steps)",
                url,
                block.getName(),
                instructions.size());
        arWebDriver.getPlaywrightDriver().open(browserType, url, optionsConfig);

        int clicked = 0;
        int inserted = 0;
        int read = 0;
        int failed = 0;

        for (InstructionLoad instruction : instructions) {
            String actionsRaw = instruction.getActions();
            if (Strings.isNullOrEmpty(actionsRaw)) {
                continue;
            }
            String action = actionsRaw.split(ARConstantsEngine.ACTION_SPECIFICATIONS_SPLITTER)[0];
            try {
                switch (action) {
                    case ARConstantsEngine.CLICK, ARConstantsEngine.OTHER -> {
                        if (arWebDriver.getPlaywrightDriver().click(instruction)) clicked++;
                        else failed++;
                    }
                    case ARConstantsEngine.INSERT -> {
                        String value = Strings.isNullOrEmpty(instruction.getDefaultValue())
                                ? "test"
                                : instruction.getDefaultValue();
                        if (arWebDriver
                                .getPlaywrightDriver()
                                .fill(instruction, new FieldData(instruction.getName(), value))) inserted++;
                        else failed++;
                    }
                    case ARConstantsEngine.OUTPUT -> {
                        arWebDriver.getPlaywrightDriver().text(instruction);
                        read++;
                    }
                    default -> log.info(
                            "TEST RUN — skipping unsupported action '{}' on '{}'", action, instruction.getName());
                }
            } catch (Exception e) {
                failed++;
                log.warn("TEST RUN — step failed [{}] '{}': {}", action, instruction.getName(), e.getMessage());
            }

            try {
                Thread.sleep(STEP_PAUSE_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        log.info("TEST RUN — done: {} clicked, {} inserted, {} read, {} failed", clicked, inserted, read, failed);
        return new TestRunResult(clicked, inserted, read, failed, url);
    }
}

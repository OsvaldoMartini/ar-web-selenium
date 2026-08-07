package com.allinweb.ch.facade.actions;

import com.allinweb.ch.driver.ARPlaywrightDriver;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.facade.PlaywrightActionExecutor.TextResult;
import com.allinweb.ch.model.FieldData;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.util.ARConstantsEngine;
import com.google.common.base.Strings;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Playwright and actionExecutor-JS routing (cluster C). Remaining Selenium fallback bodies live
 * downstream temporarily and are unreachable while they are migrated to Playwright operations.
 */
public class PlaywrightBridge {

    private static final Logger logOperations = LoggerFactory.getLogger("com.allinweb.operations");

    private final ActionContext ctx;

    public PlaywrightBridge(ActionContext ctx) {
        this.ctx = ctx;
    }

    public boolean tryPlaywrightWebAction(
            InstructionLoad instruction,
            FieldData data,
            String action,
            Map<String, String> outputValues) {
        ARWebDriver runtime = ctx.arWebDriver();
        if (runtime == null || !runtime.isPlaywrightEnabled()) {
            return false;
        }

        ARPlaywrightDriver activeDriver = runtime.currentPlaywrightDriver();
        if (activeDriver == null || !activeDriver.isOpen()) {
            return false;
        }

        try {
            switch (action) {
                case ARConstantsEngine.CLICK:
                case ARConstantsEngine.OTHER:
                    if (activeDriver.click(instruction)) {
                        return true;
                    }
                    return healAndRetry(instruction, activeDriver, activeDriver::click);
                case ARConstantsEngine.INSERT:
                    if (activeDriver.fill(instruction, data)) {
                        return true;
                    }
                    return healAndRetry(
                            instruction, activeDriver, healed -> activeDriver.fill(healed, data));
                case ARConstantsEngine.OUTPUT:
                    String value = readPlaywrightText(instruction);
                    if (value != null) {
                        if (outputValues != null) {
                            outputValues.put(
                                    instruction.getId() + "-" + instruction.getName(),
                                    value);
                        }
                        return true;
                    }
                    return false;
                default:
                    return false;
            }
        } catch (Exception error) {
            logOperations.warn("Playwright action failed, falling back to Selenium: {}", error.getMessage());
            return false;
        }
    }

    /**
     * Reads one Web Element through the active Playwright page.
     *
     * <p>{@code ""} is a successful, legitimate empty Web value. {@code null} means that both the
     * primary locator and the bounded self-healing fallback failed.
     */
    public String readPlaywrightText(InstructionLoad instruction) {
        ARWebDriver runtime = ctx.arWebDriver();
        if (runtime == null || !runtime.isPlaywrightEnabled()) {
            return null;
        }
        ARPlaywrightDriver activeDriver = runtime.currentPlaywrightDriver();
        if (activeDriver == null || !activeDriver.isOpen()) {
            return null;
        }
        try {
            TextResult result = activeDriver.textResult(instruction);
            if (result != null && result.found()) {
                return result.value();
            }
            String[] healedValue = new String[1];
            boolean healed = healAndRetry(instruction, activeDriver, candidate -> {
                TextResult candidateResult = activeDriver.textResult(candidate);
                if (candidateResult == null || !candidateResult.found()) {
                    return false;
                }
                healedValue[0] = candidateResult.value();
                return true;
            });
            return healed ? healedValue[0] : null;
        } catch (Exception error) {
            logOperations.warn(
                    "Playwright text read failed; value remains VOID: {}",
                    error.getMessage());
            return null;
        }
    }

    /**
     * Self-healing fallback: the primary Playwright locate failed, so consult the scanned_element
     * source-of-truth registry (scoped by the running bot job) for a confident match and retry the
     * action with the registry's current locator. Only fires on failure and only for high-confidence
     * matches — a pure improvement over "action failed".
     */
    private boolean healAndRetry(
            InstructionLoad instruction,
            ARPlaywrightDriver activeDriver,
            java.util.function.Predicate<InstructionLoad> retry) {
        Integer botJobId = ctx.priorities() == null ? null : ctx.priorities().getJobId();
        if (botJobId == null) {
            return false;
        }
        String currentPageUrl = activeDriver.currentUrl();
        com.allinweb.ch.facade.ScannedElementResolver.Result r = com.allinweb.ch.facade.PerformDataBase.getInstance()
                .resolveScannedElementByBotJobAndPage(botJobId, currentPageUrl, instruction);
        if (!r.matched() || r.confidence() < 0.75) {
            return false;
        }
        com.allinweb.ch.model.ScannedElement s = r.element();
        InstructionLoad healed = new InstructionLoad();
        healed.setName(instruction.getName());
        healed.setActions(instruction.getActions());
        healed.setForceCoordinates(instruction.getForceCoordinates());
        healed.setXpath(Strings.isNullOrEmpty(s.getCustomXPath()) ? s.getXPath() : s.getCustomXPath());
        healed.setCssSelector(s.getCssSelector());
        healed.setCoordinates(s.getCoordinates());
        healed.setIFrameXPath(s.getIFrameXPath());
        logOperations.info(
                "self-heal: '{}' re-resolved via registry (strategy={}, conf={}) -> xpath={}",
                instruction.getName(),
                r.strategy(),
                r.confidence(),
                s.getXPath());
        return retry.test(healed);
    }

    public boolean isPlaywrightOnlyMode() {
        return ctx.arWebDriver() != null;
    }

}

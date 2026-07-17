package com.allinweb.ch.facade.actions;

import com.allinweb.ch.facade.ActionExecutorClient;
import com.allinweb.ch.model.FieldData;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.util.ARConstantsEngine;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.google.common.base.Strings;
import org.openqa.selenium.JavascriptExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Routing between the Selenium path and the Playwright / actionExecutor-JS paths (cluster C).
 * All USE_PLAYWRIGHT / PLAYWRIGHT_SELENIUM_FALLBACK flag handling lives here — the Playwright
 * migration (Phases 4-6) extends this class instead of editing the facade. Bodies moved
 * verbatim from PerformActions.
 */
public class PlaywrightBridge {

    private static final Logger logOperations = LoggerFactory.getLogger("com.allinweb.operations");

    private final ActionContext ctx;

    public PlaywrightBridge(ActionContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Check if the actionExecutor JS plugin is alive in the browser.
     * If not, re-inject it via the callback set by ScannerRuntimeBackend.
     * Called before every action step to ensure the plugin is always available.
     */
    public void ensureActionExecutor() {
        if (ctx.driver() == null || ctx.actionExecutorInjector() == null) return;

        try {
            JavascriptExecutor js = (JavascriptExecutor) ctx.driver();
            Object alive = js.executeScript("return window.__actionExecutorActive === true;");
            if (Boolean.TRUE.equals(alive)) return;

            logOperations.info("actionExecutor not alive in browser - re-injecting");
            ctx.actionExecutorInjector().run();
        } catch (Exception e) {
            logOperations.warn("ensureActionExecutor check failed: {} - re-injecting", e.getMessage());
            try {
                ctx.actionExecutorInjector().run();
            } catch (Exception re) {
                logOperations.warn("actionExecutor re-injection failed: {}", re.getMessage());
            }
        }
    }

    public boolean tryPlaywrightWebAction(InstructionLoad instruction, FieldData data, String action) {
        if (ctx.arWebDriver() == null || !ctx.arWebDriver().isPlaywrightEnabled()) {
            return false;
        }

        try {
            switch (action) {
                case ARConstantsEngine.CLICK:
                case ARConstantsEngine.OTHER:
                    if (ctx.arWebDriver().getPlaywrightDriver().click(instruction)) {
                        return true;
                    }
                    return healAndRetry(
                            instruction,
                            healed -> ctx.arWebDriver().getPlaywrightDriver().click(healed));
                case ARConstantsEngine.INSERT:
                    if (ctx.arWebDriver().getPlaywrightDriver().fill(instruction, data)) {
                        return true;
                    }
                    return healAndRetry(
                            instruction,
                            healed -> ctx.arWebDriver().getPlaywrightDriver().fill(healed, data));
                case ARConstantsEngine.OUTPUT:
                    String value = ctx.arWebDriver().getPlaywrightDriver().text(instruction);
                    return !Strings.isNullOrEmpty(value);
                default:
                    return false;
            }
        } catch (Exception error) {
            logOperations.warn("Playwright action failed, falling back to Selenium: {}", error.getMessage());
            return false;
        }
    }

    /**
     * Self-healing fallback: the primary Playwright locate failed, so consult the scanned_element
     * source-of-truth registry (scoped by the running bot job) for a confident match and retry the
     * action with the registry's current locator. Only fires on failure and only for high-confidence
     * matches — a pure improvement over "action failed".
     */
    private boolean healAndRetry(InstructionLoad instruction, java.util.function.Predicate<InstructionLoad> retry) {
        Integer botJobId = ctx.priorities() == null ? null : ctx.priorities().getJobId();
        if (botJobId == null) {
            return false;
        }
        com.allinweb.ch.facade.ScannedElementResolver.Result r = com.allinweb.ch.facade.PerformDataBase.getInstance()
                .resolveScannedElementByBotJob(botJobId, instruction);
        if (!r.matched() || r.confidence() < 0.75) {
            return false;
        }
        com.allinweb.ch.model.ScannedElement s = r.element();
        InstructionLoad healed = new InstructionLoad();
        healed.setName(instruction.getName());
        healed.setActions(instruction.getActions());
        healed.setForceCoordinates(instruction.getForceCoordinates());
        healed.setXpath(s.getXPath());
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
        if (ctx.arWebDriver() == null || !ctx.arWebDriver().isPlaywrightEnabled()) {
            return false;
        }

        String configured = ARPropertyManager.getInstance().getProperty(ARPropertyEnum.PLAYWRIGHT_SELENIUM_FALLBACK);
        return configured != null && !Boolean.parseBoolean(configured.trim());
    }

    /**
     * Fallback: send an action command to the injected actionExecutor JS plugin
     * via WebSocket.  The browser executes it directly in DOM context -
     * no Selenium visibility / pointer-events checks.
     *
     * @param action      "click", "type", "select", "clear", etc.
     * @param instruction the current instruction (provides xPath, cssSelector, coordinates, attribId)
     * @param value       the value to type or select (nullable)
     * @return true if the JS-side action succeeded
     */
    public boolean tryActionExecutor(String action, InstructionLoad instruction, String value) {
        // Make sure the plugin is alive before sending a command
        ensureActionExecutor();

        try {
            ActionExecutorClient client = ActionExecutorClient.getInstance();
            ActionExecutorClient.ActionResult result = client.sendAction(
                    action,
                    instruction.getXpath(),
                    instruction.getCssSelector(),
                    instruction.getCoordinates(),
                    null, // attribId not on InstructionLoad; JS will fallback to xPath/css/coords
                    value);

            if (result.isSuccess()) {
                logOperations.info(
                        "actionExecutor fallback succeeded: {} - {} (verified={})",
                        action,
                        result.getMessage(),
                        result.isVerified());
                return true;
            } else {
                logOperations.warn(
                        "actionExecutor fallback failed: {} - {} (verified={})",
                        action,
                        result.getMessage(),
                        result.isVerified());
                return false;
            }
        } catch (Exception e) {
            logOperations.warn("actionExecutor fallback error: {} - {}", action, e.getMessage());
            return false;
        }
    }
}

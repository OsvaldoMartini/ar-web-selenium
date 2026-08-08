package com.allinweb.ch.facade.actions;

import com.allinweb.ch.driver.ARPlaywrightDriver;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.facade.PlaywrightRuntimeHealingExecutor.Result;
import com.allinweb.ch.facade.RuntimeElementHealingService;
import com.allinweb.ch.facade.RuntimeElementHealingService.Preparation;
import com.allinweb.ch.model.FieldData;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.util.ARConstantsEngine;
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
    private final RuntimeElementHealingService healingService;

    public PlaywrightBridge(ActionContext ctx) {
        this.ctx = ctx;
        this.healingService = RuntimeElementHealingService.getInstance();
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
                    return completed(
                            action,
                            instruction,
                            activeDriver.runtimeClick(
                                    instruction, prepare(instruction, activeDriver)));
                case ARConstantsEngine.INSERT:
                    return completed(
                            action,
                            instruction,
                            activeDriver.runtimeInput(
                                    instruction, data, prepare(instruction, activeDriver)));
                case ARConstantsEngine.OUTPUT:
                    Result output = activeDriver.runtimeOutput(
                            instruction, prepare(instruction, activeDriver));
                    logResult(action, instruction, output);
                    if (output != null && output.succeeded() && output.found()) {
                        if (outputValues != null) {
                            outputValues.put(
                                    instruction.getId() + "-" + instruction.getName(),
                                    output.value());
                        }
                        return true;
                    }
                    return false;
                default:
                    return false;
            }
        } catch (Exception error) {
            logOperations.warn(
                    "runtime-action exception action={} instructionId={} failureType={}",
                    safeAction(action),
                    instruction == null ? null : instruction.getId(),
                    error.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * Reads one Web Element through the active Playwright page.
     *
     * <p>{@code ""} is a successful, legitimate empty Web value. {@code null} means the bounded,
     * fail-closed runtime resolver did not complete the read.
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
            Result result = activeDriver.runtimeOutput(
                    instruction, prepare(instruction, activeDriver));
            logResult(ARConstantsEngine.OUTPUT, instruction, result);
            return result != null && result.succeeded() && result.found()
                    ? result.value()
                    : null;
        } catch (Exception error) {
            logOperations.warn(
                    "runtime-action exception action={} instructionId={} failureType={}",
                    ARConstantsEngine.OUTPUT,
                    instruction == null ? null : instruction.getId(),
                    error.getClass().getSimpleName());
            return null;
        }
    }

    /** Builds one server-owned, current-page registry preparation before browser resolution. */
    private Preparation prepare(InstructionLoad instruction, ARPlaywrightDriver activeDriver) {
        Integer currentBotJobId =
                ctx.priorities() == null ? null : ctx.priorities().getJobId();
        Integer botJobId = instruction != null
                        && currentBotJobId != null
                        && currentBotJobId.equals(instruction.getBotJobId())
                ? instruction.getBotJobId()
                : currentBotJobId;
        Integer assertedHomeBankingId = instruction == null
                ? null
                : instruction.getHomeBankingId();
        return healingService.prepare(
                assertedHomeBankingId,
                botJobId,
                activeDriver.currentUrl(),
                instruction);
    }

    private boolean completed(String action, InstructionLoad instruction, Result result) {
        logResult(action, instruction, result);
        return result != null && result.succeeded();
    }

    /** Logs only the executor's structured, redacted diagnostic; never its value. */
    private void logResult(String action, InstructionLoad instruction, Result result) {
        if (result == null) {
            logOperations.warn(
                    "runtime-action result missing action={} instructionId={}",
                    safeAction(action),
                    instruction == null ? null : instruction.getId());
            return;
        }
        if (result.succeeded()) {
            logOperations.debug(
                    "runtime-action completed action={} instructionId={} diagnostic={}",
                    safeAction(action),
                    instruction == null ? null : instruction.getId(),
                    result.diagnostic());
        } else {
            logOperations.warn(
                    "runtime-action refused action={} instructionId={} diagnostic={}",
                    safeAction(action),
                    instruction == null ? null : instruction.getId(),
                    result.diagnostic());
        }
    }

    private static String safeAction(String action) {
        return action == null ? "" : action;
    }

    public boolean isPlaywrightOnlyMode() {
        return ctx.arWebDriver() != null;
    }
}

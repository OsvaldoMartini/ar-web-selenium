package com.allinweb.ch.facade.execution.v2;

import com.allinweb.ch.facade.CommandRegistry;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.InstructionSnapshot;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.Plan;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationStepExecutor.Outcome;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.StepDisposition;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.StepStatus;
import com.allinweb.ch.socket.ExcelDataWorkspaceService;
import com.allinweb.ch.socket.ExcelDataWorkspaceService.IntegrationDataset;
import com.allinweb.ch.util.CryptationAlgorithm;
import com.allinweb.ch.util.ExtractedData;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Locale;
import java.util.Objects;

/** Translates one frozen Smoke step to the isolated TypeScript Playwright runtime. */
public final class SmokeTestIntegrationV2StepExecutor {
    private static final java.util.Set<String> LOGICAL_ONLY = java.util.Set.of(
            "CK", "IF", "ELSEIF", "ELSE", "ENDIF", "LOOP", "GOTO", "EXCEL GOTO",
            "NEXT ROW", "H", "PAUSE", "EXCEL_BLOCK_HEADER");

    private final ActionPort runtime;
    private final ActiveCellPort activeCells;

    public SmokeTestIntegrationV2StepExecutor(ExecutionRuntimeRunCoordinator coordinator) {
        this(
                new ActionPort() {
                    @Override
                    public JsonObject action(
                            ExecutionRuntimeRunCoordinator.Run run,
                            long sequence,
                            int instructionId,
                            String inputValue) {
                        return coordinator.action(run, sequence, instructionId, inputValue);
                    }

                    @Override
                    public JsonObject refresh(ExecutionRuntimeRunCoordinator.Run run) {
                        return coordinator.refresh(run);
                    }
                },
                ExcelDataWorkspaceService.getInstance()::publishActiveCell);
    }

    SmokeTestIntegrationV2StepExecutor(ActionPort runtime, ActiveCellPort activeCells) {
        this.runtime = Objects.requireNonNull(runtime, "Execution V2 action port is required");
        this.activeCells = Objects.requireNonNull(activeCells, "Execution V2 active-cell port is required");
    }

    public Outcome execute(
            ExecutionRuntimeRunCoordinator.Run run,
            Plan plan,
            IntegrationDataset dataset,
            long sequence,
            int instructionId,
            int excelRowIndex) {
        Objects.requireNonNull(run, "Execution V2 run is required");
        Objects.requireNonNull(plan, "Frozen Integration plan is required");
        Objects.requireNonNull(dataset, "Frozen Integration dataset is required");
        InstructionSnapshot instruction = plan.instruction(instructionId);
        if (instruction == null) {
            return failed(false, "INSTRUCTION_OUTSIDE_FROZEN_SCOPE",
                    "The instruction is not part of this frozen Integration scope.");
        }
        if (!instruction.active() || !instruction.block().active()) {
            return new Outcome(
                    StepStatus.SKIPPED,
                    StepDisposition.INACTIVE,
                    "INACTIVE_INSTRUCTION",
                    "The inactive instruction was skipped.",
                    null,
                    null);
        }

        String action = CommandRegistry.canonicalize(instruction.action());
        if (LOGICAL_ONLY.contains(action)) {
            return new Outcome(
                    StepStatus.PASSED,
                    StepDisposition.LOGICAL_ONLY,
                    "LOGICAL_ONLY",
                    "React retained control of this logical instruction.",
                    null,
                    null);
        }
        try {
            if ("REFRESH".equals(action)
                    || "REFRESH_LOOP".equals(action)
                    || "REFRESH_HOLD".equals(action)) {
                runtime.refresh(run);
                return passed("The isolated Playwright page was refreshed.");
            }
            if (!CommandRegistry.isWebElementAction(action)) {
                return failed(
                        instruction.optional(),
                        "V2_COMMAND_NOT_MIGRATED",
                        "This command is not yet supported by the TypeScript Playwright runtime.");
            }
            String inputValue = null;
            if ("I".equals(action)) {
                inputValue = inputValue(dataset, instruction, excelRowIndex);
                if (inputValue == null) {
                    return failed(
                            instruction.optional(),
                            "EXCEL_INPUT_UNAVAILABLE",
                            "The frozen Excel input value is unavailable.");
                }
                activeCells.publish(
                        instruction.owner().botJobId(),
                        instruction.block().name(),
                        resolveColumn(dataset.data(), instruction),
                        excelRowIndex,
                        instruction.id());
            }
            return runtimeOutcome(
                    instruction.optional(),
                    runtime.action(run, sequence, instructionId, inputValue));
        } catch (RuntimeException failure) {
            return failed(
                    instruction.optional(),
                    "V2_RUNTIME_ACTION_FAILED",
                    "The isolated TypeScript Playwright action could not be completed.");
        }
    }

    private static String inputValue(
            IntegrationDataset dataset, InstructionSnapshot instruction, int rowIndex) {
        ExtractedData data = dataset.data();
        if (rowIndex < 0 || rowIndex >= data.getNumberOfDataRows()) return null;
        String column = resolveColumn(data, instruction);
        if (column == null) return null;
        String value = data.getFieldValue(instruction.block().name(), column, rowIndex);
        if (value == null) return null;
        return instruction.codified() ? CryptationAlgorithm.decrypt(value) : value;
    }

    private static String resolveColumn(ExtractedData data, InstructionSnapshot instruction) {
        String block = instruction.block().name();
        String display = instruction.displayKey();
        if (display != null && data.containsField(block, display)) return display;
        String canonical = instruction.name();
        return canonical != null && data.containsField(block, canonical) ? canonical : null;
    }

    private static Outcome runtimeOutcome(boolean optional, JsonObject result) {
        if (result == null
                || !result.has("ok")
                || !result.get("ok").isJsonPrimitive()
                || !result.getAsJsonPrimitive("ok").isBoolean()
                || !result.has("diagnostic")
                || !result.get("diagnostic").isJsonObject()) {
            return failed(optional, "V2_RUNTIME_RESPONSE_INVALID",
                    "The TypeScript Playwright runtime returned an invalid response.");
        }
        JsonObject diagnostic = result.getAsJsonObject("diagnostic");
        String code = safeCode(diagnostic.get("code"));
        if (result.get("ok").getAsBoolean()) {
            return new Outcome(
                    StepStatus.PASSED,
                    StepDisposition.PHYSICAL,
                    code.isEmpty() ? "STEP_COMPLETED" : code,
                    "The isolated TypeScript Playwright runtime completed the instruction.",
                    null,
                    null);
        }
        return failed(
                optional,
                code.isEmpty() ? "V2_RUNTIME_ACTION_REFUSED" : code,
                "The TypeScript Playwright runtime refused the physical action.");
    }

    private static String safeCode(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            return "";
        }
        String code = value.getAsString().trim().toUpperCase(Locale.ROOT);
        return code.matches("[A-Z][A-Z0-9_]{2,80}") ? code : "";
    }

    private static Outcome passed(String message) {
        return new Outcome(
                StepStatus.PASSED,
                StepDisposition.PHYSICAL,
                "STEP_COMPLETED",
                message,
                null,
                null);
    }

    private static Outcome failed(boolean optional, String code, String message) {
        return new Outcome(
                optional ? StepStatus.WARNING : StepStatus.FAILED,
                StepDisposition.PHYSICAL,
                code,
                message,
                null,
                null);
    }

    interface ActionPort {
        JsonObject action(
                ExecutionRuntimeRunCoordinator.Run run,
                long sequence,
                int instructionId,
                String inputValue);

        JsonObject refresh(ExecutionRuntimeRunCoordinator.Run run);
    }

    interface ActiveCellPort {
        void publish(int botJobId, String blockName, String column, int rowIndex, int instructionId);
    }
}

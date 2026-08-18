package com.allinweb.ch.facade.execution.v2;

import com.allinweb.ch.facade.CommandRegistry;
import com.allinweb.ch.facade.actions.RuntimeVariableValue;
import com.allinweb.ch.facade.actions.RuntimeVariableValue.VoidReason;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.InstructionSnapshot;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.Plan;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationStepExecutor.Outcome;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationStepExecutor.RunVariables;
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
            "NEXT ROW", "H", "PAUSE", "EXCEL_BLOCK_HEADER", "E");

    /**
     * Reports whether the current React/V2 Integration path owns this command family.
     * ExcelWrite is included because React owns its in-memory arrival and sends only finalized
     * artifacts through the Java boundary; it is deliberately not a Node physical action.
     */
    public static boolean supportsIntegrationAction(String rawAction) {
        String action = CommandRegistry.canonicalize(rawAction);
        return LOGICAL_ONLY.contains(action)
                || "E".equals(action)
                || "EXCELWRITE".equals(action)
                || "Q".equals(action)
                || "REFRESH".equals(action)
                || "REFRESH_LOOP".equals(action)
                || "REFRESH_HOLD".equals(action)
                || "GET".equals(action)
                || "SET".equals(action)
                || CommandRegistry.isWebElementAction(action);
    }

    private final ActionPort runtime;
    private final ActiveCellPort activeCells;

    public SmokeTestIntegrationV2StepExecutor(ExecutionRuntimeRunCoordinator coordinator) {
        this(
                new CoordinatorActionPort(coordinator),
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
            int excelRowIndex,
            RunVariables variables) {
        Objects.requireNonNull(run, "Execution V2 run is required");
        Objects.requireNonNull(plan, "Frozen Integration plan is required");
        Objects.requireNonNull(dataset, "Frozen Integration dataset is required");
        Objects.requireNonNull(variables, "Execution V2 run variables are required");
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
            if ("Q".equals(action)) {
                runtime.closeBrowser(run);
                return passed("The explicit Close Browser instruction closed the Playwright page.");
            }
            if ("GET".equals(action)) {
                return executeGet(run, instruction, sequence, variables);
            }
            if ("SET".equals(action)) {
                return executeSet(run, instruction, sequence, variables);
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

    public Outcome recover(
            ExecutionRuntimeRunCoordinator.Run run,
            int instructionId,
            String recoveryCandidateId,
            boolean save) {
        return recover(run, instructionId, recoveryCandidateId, save, "CLICK", null);
    }

    public Outcome recover(
            ExecutionRuntimeRunCoordinator.Run run,
            int instructionId,
            String recoveryCandidateId,
            boolean save,
            String requestedAction,
            String requestedInput) {
        try {
            return runtimeOutcome(
                    false,
                    ((CoordinatorActionPort) runtime).recover(
                            run, instructionId, recoveryCandidateId, save,
                            requestedAction, requestedInput));
        } catch (RuntimeException failure) {
            return failed(
                    false,
                    "V2_RECOVERY_FAILED",
                    "The selected locator recovery could not be completed.");
        }
    }

    public void cancelRecovery(
            ExecutionRuntimeRunCoordinator.Run run, int instructionId) {
        if (runtime instanceof CoordinatorActionPort coordinator) {
            coordinator.cancelRecovery(run, instructionId);
        }
    }

    private Outcome executeGet(
            ExecutionRuntimeRunCoordinator.Run run,
            InstructionSnapshot instruction,
            long sequence,
            RunVariables variables) {
        Integer variableId = instruction.variableId("GET_WRITE");
        if (variableId == null) {
            return failed(instruction.optional(), "GET_VARIABLE_MISSING",
                    "GET has no GET_WRITE variable slot.");
        }
        JsonObject result;
        try {
            result = runtime.get(run, sequence, instruction.id());
        } catch (RuntimeException failure) {
            variables.markVoid(variableId, VoidReason.PRODUCER_FAILED);
            throw failure;
        }
        if (!successfulResult(result)) {
            variables.markVoid(variableId, VoidReason.PRODUCER_FAILED);
            return runtimeOutcome(instruction.optional(), result);
        }
        JsonElement output = result.get("output");
        if (output == null || !output.isJsonPrimitive() || !output.getAsJsonPrimitive().isString()) {
            variables.markVoid(variableId, VoidReason.PRODUCER_FAILED);
            return failed(instruction.optional(), "V2_RUNTIME_OUTPUT_INVALID",
                    "The TypeScript Playwright runtime returned an invalid GET value.");
        }
        String value = output.getAsString();
        boolean durable = variables.write(variableId, value);
        if (!durable) {
            return new Outcome(
                    StepStatus.FAILED,
                    StepDisposition.PHYSICAL,
                    "GET_RUNTIME_PERSISTENCE_FAILED",
                    "GET read the page, but its durable runtime value was not saved.",
                    variableId,
                    RuntimeVariableValue.value(value));
        }
        return new Outcome(
                StepStatus.PASSED,
                StepDisposition.PHYSICAL,
                "GET_VALUE_WRITTEN",
                "GET updated the run-local variable.",
                variableId,
                RuntimeVariableValue.value(value));
    }

    private Outcome executeSet(
            ExecutionRuntimeRunCoordinator.Run run,
            InstructionSnapshot instruction,
            long sequence,
            RunVariables variables) {
        Integer variableId = instruction.variableId("READ_SET");
        if (variableId == null) {
            return failed(instruction.optional(), "SET_VARIABLE_MISSING",
                    "SET has no READ_SET variable slot.");
        }
        RuntimeVariableValue value = variables.read(variableId);
        if (value == null || value.isVoid()) {
            return failed(instruction.optional(), "SET_VARIABLE_VOID",
                    "SET cannot write because its runtime variable is VOID.");
        }
        return runtimeOutcome(
                instruction.optional(),
                runtime.set(run, sequence, instruction.id(), value.value()));
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
            if (result.has("recoverySaveFailed")
                    && result.get("recoverySaveFailed").isJsonPrimitive()
                    && result.get("recoverySaveFailed").getAsBoolean()) {
                return new Outcome(
                        StepStatus.WARNING,
                        StepDisposition.PHYSICAL,
                        "RECOVERY_ACTION_COMPLETED_SAVE_FAILED",
                        "The selected element action completed, but its locator was not saved.",
                        null,
                        null);
            }
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
                "The TypeScript Playwright runtime refused the physical action.",
                result.has("recovery") && result.get("recovery").isJsonObject()
                        ? result.getAsJsonObject("recovery").deepCopy()
                        : null);
    }

    private static boolean successfulResult(JsonObject result) {
        return result != null
                && result.has("ok")
                && result.get("ok").isJsonPrimitive()
                && result.getAsJsonPrimitive("ok").isBoolean()
                && result.get("ok").getAsBoolean()
                && result.has("diagnostic")
                && result.get("diagnostic").isJsonObject();
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
        return failed(optional, code, message, null);
    }

    private static Outcome failed(
            boolean optional, String code, String message, JsonObject recovery) {
        return new Outcome(
                optional ? StepStatus.WARNING : StepStatus.FAILED,
                StepDisposition.PHYSICAL,
                code,
                message,
                null,
                null,
                recovery);
    }

    interface ActionPort {
        JsonObject action(
                ExecutionRuntimeRunCoordinator.Run run,
                long sequence,
                int instructionId,
                String inputValue);

        JsonObject refresh(ExecutionRuntimeRunCoordinator.Run run);

        default void closeBrowser(ExecutionRuntimeRunCoordinator.Run run) {
            throw new UnsupportedOperationException("Close Browser is unavailable");
        }

        JsonObject get(
                ExecutionRuntimeRunCoordinator.Run run, long sequence, int instructionId);

        JsonObject set(
                ExecutionRuntimeRunCoordinator.Run run,
                long sequence,
                int instructionId,
                String value);
    }

    private static final class CoordinatorActionPort implements ActionPort {
        private final ExecutionRuntimeRunCoordinator coordinator;

        private CoordinatorActionPort(ExecutionRuntimeRunCoordinator coordinator) {
            this.coordinator = coordinator;
        }

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

        @Override
        public void closeBrowser(ExecutionRuntimeRunCoordinator.Run run) {
            coordinator.closeBrowser(run);
        }

        @Override
        public JsonObject get(
                ExecutionRuntimeRunCoordinator.Run run, long sequence, int instructionId) {
            return coordinator.get(run, sequence, instructionId);
        }

        @Override
        public JsonObject set(
                ExecutionRuntimeRunCoordinator.Run run,
                long sequence,
                int instructionId,
                String value) {
            return coordinator.set(run, sequence, instructionId, value);
        }

        private JsonObject recover(
                ExecutionRuntimeRunCoordinator.Run run,
                int instructionId,
                String candidateId,
                boolean save,
                String requestedAction,
                String requestedInput) {
            return coordinator.recover(
                    run, instructionId, candidateId, save,
                    requestedAction, requestedInput);
        }

        private void cancelRecovery(
                ExecutionRuntimeRunCoordinator.Run run, int instructionId) {
            coordinator.cancelRecovery(run, instructionId);
        }
    }

    interface ActiveCellPort {
        void publish(int botJobId, String blockName, String column, int rowIndex, int instructionId);
    }
}

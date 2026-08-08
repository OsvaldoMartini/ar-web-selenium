package com.allinweb.ch.facade.execution;

import com.allinweb.ch.driver.ARPlaywrightDriver;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.facade.CommandRegistry;
import com.allinweb.ch.facade.PlaywrightActionExecutor.TextResult;
import com.allinweb.ch.facade.PlaywrightRuntimeHealingExecutor.Result;
import com.allinweb.ch.facade.RuntimeElementHealingService;
import com.allinweb.ch.facade.RuntimeElementHealingService.Preparation;
import com.allinweb.ch.facade.actions.RuntimeVariableMemoryRegistry;
import com.allinweb.ch.facade.actions.RuntimeVariableMemoryRegistry.BotJobKey;
import com.allinweb.ch.facade.actions.RuntimeVariableStore;
import com.allinweb.ch.facade.actions.RuntimeVariableValue;
import com.allinweb.ch.facade.actions.RuntimeVariableValue.VoidReason;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.InstructionSnapshot;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.Plan;
import com.allinweb.ch.model.FieldData;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.FrozenRuntimeValue;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.RuntimeSnapshot;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.RuntimeValueState;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.RuntimeVoidReason;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.StepDisposition;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.StepStatus;
import com.allinweb.ch.socket.ExcelDataWorkspaceService;
import com.allinweb.ch.socket.ExcelDataWorkspaceService.IntegrationDataset;
import com.allinweb.ch.util.CryptationAlgorithm;
import com.allinweb.ch.util.ExtractedData;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Executes exactly one backend-authoritative instruction for a React-owned Integration run. */
public final class SmokeTestIntegrationStepExecutor {
    private static final Logger logOperations = LoggerFactory.getLogger("com.allinweb.operations");
    private static final java.util.Set<String> LOGICAL_ONLY = java.util.Set.of(
            "CK",
            "IF",
            "ELSEIF",
            "ELSE",
            "ENDIF",
            "LOOP",
            "GOTO",
            "EXCEL GOTO",
            "NEXT ROW",
            "H",
            "PAUSE",
            "EXCEL_BLOCK_HEADER");

    private final BrowserPort browser;
    private final ActiveCellPort activeCells;

    public SmokeTestIntegrationStepExecutor() {
        this(new DefaultBrowserPort(), ExcelDataWorkspaceService.getInstance()::publishActiveCell);
    }

    SmokeTestIntegrationStepExecutor(BrowserPort browser, ActiveCellPort activeCells) {
        this.browser = Objects.requireNonNull(browser, "browser");
        this.activeCells = Objects.requireNonNull(activeCells, "activeCells");
    }

    public Outcome execute(
            Plan plan,
            IntegrationDataset dataset,
            int instructionId,
            int excelRowIndex,
            RunVariables variables) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(dataset, "dataset");
        Objects.requireNonNull(variables, "variables");
        InstructionSnapshot instruction = plan.instruction(instructionId);
        if (instruction == null) {
            return failed(
                    false,
                    StepDisposition.UNSUPPORTED,
                    "INSTRUCTION_OUTSIDE_FROZEN_SCOPE",
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
        try {
            if (LOGICAL_ONLY.contains(action)) {
                return new Outcome(
                        StepStatus.PASSED,
                        StepDisposition.LOGICAL_ONLY,
                        "LOGICAL_ONLY",
                        "React retained control of this logical instruction.",
                        null,
                        null);
            }
            if ("REFRESH".equals(action)
                    || "REFRESH_LOOP".equals(action)
                    || "REFRESH_HOLD".equals(action)) {
                return executeRefresh(instruction.optional());
            }
            if (CommandRegistry.isWebElementAction(action)) {
                return executeWebElement(instruction, dataset, excelRowIndex, action);
            }
            return switch (action) {
                case "GET" -> executeGet(plan, instruction, variables);
                case "SET" -> executeSet(plan, instruction, variables);
                default -> failed(
                        instruction.optional(),
                        StepDisposition.UNSUPPORTED,
                        "UNSUPPORTED_ACTION",
                        "This action is not supported by Integration v1.");
            };
        } catch (RuntimeException actionFailure) {
            return failed(
                    instruction.optional(),
                    StepDisposition.PHYSICAL,
                    "PLAYWRIGHT_ACTION_FAILED",
                    "The Playwright action could not be completed.");
        }
    }

    private Outcome executeWebElement(
            InstructionSnapshot instruction,
            IntegrationDataset dataset,
            int rowIndex,
            String action) {
        return switch (action) {
            case "C" -> physical(
                    instruction.optional(),
                    browser.clickOnce(instruction),
                    "CLICK_FAILED",
                    "The Web Element click could not be completed.");
            case "I" -> executeInput(instruction, dataset, rowIndex);
            case "O" -> {
                TextResult value = browser.text(instruction);
                yield !value.found()
                        ? failed(
                                instruction.optional(),
                                StepDisposition.PHYSICAL,
                                "OUTPUT_READ_FAILED",
                                "The Web Element text could not be read.")
                        : passed("Web Element text read.");
            }
            default -> failed(
                    instruction.optional(),
                    StepDisposition.UNSUPPORTED,
                    "UNSUPPORTED_WEB_ELEMENT_ACTION",
                    "This Web Element action is not supported by Integration v1.");
        };
    }

    private Outcome executeInput(
            InstructionSnapshot instruction,
            IntegrationDataset dataset,
            int rowIndex) {
        ExtractedData data = dataset.data();
        if (rowIndex >= data.getNumberOfDataRows()) {
            return failed(
                    instruction.optional(),
                    StepDisposition.PHYSICAL,
                    "EXCEL_ROW_MISSING",
                    "The selected Excel memory row does not exist.");
        }
        String column = resolveColumn(data, instruction);
        if (column == null) {
            return failed(
                    instruction.optional(),
                    StepDisposition.PHYSICAL,
                    "EXCEL_COLUMN_MISSING",
                    "The input has no matching column in the frozen Excel dataset.");
        }
        String value = data.getFieldValue(instruction.block().name(), column, rowIndex);
        if (value == null) {
            return failed(
                    instruction.optional(),
                    StepDisposition.PHYSICAL,
                    "EXCEL_VALUE_MISSING",
                    "The input value is missing from the frozen Excel dataset.");
        }
        if (instruction.codified()) {
            value = CryptationAlgorithm.decrypt(value);
            if (value == null) {
                return failed(
                        instruction.optional(),
                        StepDisposition.PHYSICAL,
                        "EXCEL_VALUE_DECRYPT_FAILED",
                        "The protected input value could not be decoded.");
            }
        }
        activeCells.publish(
                instruction.owner().botJobId(),
                instruction.block().name(),
                column,
                rowIndex,
                instruction.id());
        return physical(
                instruction.optional(),
                browser.fillOnce(instruction, new FieldData(column, value)),
                "INPUT_FAILED",
                "The Web Element input could not be completed.");
    }

    private Outcome executeGet(
            Plan plan, InstructionSnapshot command, RunVariables variables) {
        InstructionSnapshot parent = requireWebElementParent(plan, command);
        if (parent == null) {
            return failed(
                    command.optional(),
                    StepDisposition.PHYSICAL,
                    "GET_PARENT_MISSING",
                    "GET has no valid Web Element parent in the frozen plan.");
        }
        Integer variableId = command.variableId("GET_WRITE");
        if (variableId == null) {
            return failed(
                    command.optional(),
                    StepDisposition.PHYSICAL,
                    "GET_VARIABLE_MISSING",
                    "GET has no GET_WRITE variable slot.");
        }
        TextResult read = browser.text(parent);
        if (!read.found()) {
            variables.markVoid(variableId, VoidReason.PRODUCER_FAILED);
            return failed(
                    command.optional(),
                    StepDisposition.PHYSICAL,
                    "GET_READ_FAILED",
                    "GET could not read its Web Element parent.");
        }
        String value = read.value();
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
            Plan plan, InstructionSnapshot command, RunVariables variables) {
        InstructionSnapshot parent = requireWebElementParent(plan, command);
        if (parent == null) {
            return failed(
                    command.optional(),
                    StepDisposition.PHYSICAL,
                    "SET_PARENT_MISSING",
                    "SET has no valid Web Element parent in the frozen plan.");
        }
        Integer variableId = command.variableId("READ_SET");
        if (variableId == null) {
            return failed(
                    command.optional(),
                    StepDisposition.PHYSICAL,
                    "SET_VARIABLE_MISSING",
                    "SET has no READ_SET variable slot.");
        }
        RuntimeVariableValue value = variables.read(variableId);
        if (value == null || value.isVoid()) {
            return failed(
                    command.optional(),
                    StepDisposition.PHYSICAL,
                    "SET_VARIABLE_VOID",
                    "SET cannot write because its runtime variable is VOID.");
        }
        return physical(
                command.optional(),
                browser.fillOnce(parent, new FieldData(parent.displayKey(), value.value())),
                "SET_FAILED",
                "SET could not update its Web Element parent.");
    }

    private Outcome executeRefresh(boolean optional) {
        browser.reload();
        return new Outcome(
                StepStatus.PASSED,
                StepDisposition.PHYSICAL,
                "PAGE_RELOADED",
                "The active Playwright page was reloaded.",
                null,
                null);
    }

    private static InstructionSnapshot requireWebElementParent(
            Plan plan, InstructionSnapshot command) {
        if (command.parentId() == null) return null;
        InstructionSnapshot parent = plan.instruction(command.parentId());
        if (parent == null
                || parent.block().id() != command.block().id()
                || (command.parentBlockId() != null
                        && command.parentBlockId() != parent.block().id())
                || !CommandRegistry.isWebElementAction(parent.action())) {
            return null;
        }
        return parent;
    }

    private static String resolveColumn(ExtractedData data, InstructionSnapshot instruction) {
        String block = instruction.block().name();
        String display = instruction.displayKey();
        if (display != null && data.containsField(block, display)) return display;
        String canonical = instruction.name();
        return canonical != null && data.containsField(block, canonical) ? canonical : null;
    }

    private static Outcome physical(
            boolean optional, boolean succeeded, String failureCode, String failureMessage) {
        return succeeded ? passed("Playwright completed the instruction.") : failed(
                optional, StepDisposition.PHYSICAL, failureCode, failureMessage);
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

    private static Outcome failed(
            boolean optional,
            StepDisposition disposition,
            String code,
            String message) {
        return new Outcome(
                optional ? StepStatus.WARNING : StepStatus.FAILED,
                disposition,
                code,
                message,
                null,
                null);
    }

    public record Outcome(
            StepStatus status,
            StepDisposition disposition,
            String code,
            String message,
            Integer runtimeVariableId,
            RuntimeVariableValue runtimeValue) {}

    /** Run-local variable overlay, optionally mirrored to durable memory after a successful GET. */
    public static final class RunVariables {
        private final Map<Integer, RuntimeVariableValue> local = new HashMap<>();
        private final RuntimeVariableStore durable;
        private final boolean metadataAvailable;
        private final RuntimeSnapshot initialSnapshot;

        public RunVariables(int homeBankingId, int botJobId, boolean durableRuntimeWrites) {
            BotJobKey owner = new BotJobKey(homeBankingId, botJobId);
            RuntimeVariableMemoryRegistry shared = RuntimeVariableMemoryRegistry.getInstance();
            durable = durableRuntimeWrites ? new RuntimeVariableStore(homeBankingId, botJobId) : null;
            RuntimeVariableMemoryRegistry.Snapshot frozen = shared.snapshot(owner);
            metadataAvailable = frozen.metadataAvailable();
            Map<Integer, FrozenRuntimeValue> initialValues = new LinkedHashMap<>();
            for (RuntimeVariableMemoryRegistry.VariableSnapshot variable : frozen.variables()) {
                VoidReason reason = variable.voidReason() == null
                        ? VoidReason.NO_PRODUCER_YET
                        : variable.voidReason();
                RuntimeVariableValue value = variable.state()
                                == RuntimeVariableValue.State.VALUE
                        ? RuntimeVariableValue.value(variable.value())
                        : RuntimeVariableValue.voidValue(reason);
                local.put(variable.variableId(), value);
                initialValues.put(
                        variable.variableId(),
                        value.isValue()
                                ? new FrozenRuntimeValue(
                                        RuntimeValueState.VALUE,
                                        value.value(),
                                        null,
                                        variable.entryRevision())
                                : new FrozenRuntimeValue(
                                        RuntimeValueState.VOID,
                                        null,
                                        RuntimeVoidReason.valueOf(reason.name()),
                                        variable.entryRevision()));
            }
            initialSnapshot = new RuntimeSnapshot(
                    frozen.revision(), frozen.metadataAvailable(), initialValues);
        }

        /** The immutable runtime facts returned to React in the successful START response. */
        public RuntimeSnapshot initialSnapshot() {
            return initialSnapshot;
        }

        public RuntimeVariableValue read(Integer variableId) {
            if (variableId == null || variableId <= 0) {
                return RuntimeVariableValue.voidValue(VoidReason.MISSING_BINDING);
            }
            RuntimeVariableValue current = local.get(variableId);
            if (current != null) return current;
            return RuntimeVariableValue.voidValue(
                    metadataAvailable
                            ? VoidReason.MISSING_BINDING
                            : VoidReason.METADATA_UNAVAILABLE);
        }

        /** Returns false only when an explicitly requested durable mirror failed. */
        public boolean write(Integer variableId, String value) {
            if (variableId == null || variableId <= 0 || value == null) return false;
            local.put(variableId, RuntimeVariableValue.value(value));
            return durable == null || durable.write(variableId, value);
        }

        public void markVoid(Integer variableId, VoidReason reason) {
            if (variableId == null || variableId <= 0 || reason == null) return;
            local.put(variableId, RuntimeVariableValue.voidValue(reason));
            if (durable != null) durable.markVoid(variableId, reason);
        }
    }

    interface BrowserPort {
        boolean clickOnce(InstructionSnapshot instruction);

        boolean fillOnce(InstructionSnapshot instruction, FieldData data);

        TextResult text(InstructionSnapshot instruction);

        void reload();
    }

    @FunctionalInterface
    interface ActiveCellPort {
        void publish(int botJobId, String blockName, String column, int rowIndex, Integer instructionId);
    }

    private static final class DefaultBrowserPort implements BrowserPort {
        private final RuntimeElementHealingService healingService =
                RuntimeElementHealingService.getInstance();

        private ARPlaywrightDriver driver() {
            ARPlaywrightDriver driver = ARWebDriver.getInstance().currentPlaywrightDriver();
            if (driver == null || !driver.isOpen()) {
                throw new IllegalStateException("The Playwright page is not open");
            }
            return driver;
        }

        @Override
        public boolean clickOnce(InstructionSnapshot instruction) {
            ARPlaywrightDriver activeDriver = driver();
            com.allinweb.ch.model.InstructionLoad target = instruction.toInstructionLoad();
            Result result = activeDriver.runtimeClick(
                    target, prepare(activeDriver, instruction, target));
            logResult("CLICK", instruction, result);
            return result.succeeded();
        }

        @Override
        public boolean fillOnce(InstructionSnapshot instruction, FieldData data) {
            ARPlaywrightDriver activeDriver = driver();
            com.allinweb.ch.model.InstructionLoad target = instruction.toInstructionLoad();
            Result result = activeDriver.runtimeInput(
                    target, data, prepare(activeDriver, instruction, target));
            logResult("INPUT", instruction, result);
            return result.succeeded();
        }

        @Override
        public TextResult text(InstructionSnapshot instruction) {
            ARPlaywrightDriver activeDriver = driver();
            com.allinweb.ch.model.InstructionLoad target = instruction.toInstructionLoad();
            Result result = activeDriver.runtimeOutput(
                    target, prepare(activeDriver, instruction, target));
            logResult("OUTPUT", instruction, result);
            return result.succeeded() && result.found()
                    ? TextResult.found(result.value())
                    : TextResult.missing();
        }

        @Override
        public void reload() {
            driver().reload();
        }

        private Preparation prepare(
                ARPlaywrightDriver activeDriver,
                InstructionSnapshot instruction,
                com.allinweb.ch.model.InstructionLoad target) {
            return healingService.prepare(
                    instruction.owner().homeBankingId(),
                    instruction.owner().botJobId(),
                    activeDriver.currentUrl(),
                    target);
        }

        private static void logResult(
                String action, InstructionSnapshot instruction, Result result) {
            if (result == null) {
                logOperations.warn(
                        "smoke-runtime result missing action={} instructionId={}",
                        action,
                        instruction.id());
                return;
            }
            if (result.succeeded()) {
                logOperations.debug(
                        "smoke-runtime completed action={} instructionId={} diagnostic={}",
                        action,
                        instruction.id(),
                        result.diagnostic());
            } else {
                logOperations.warn(
                        "smoke-runtime refused action={} instructionId={} diagnostic={}",
                        action,
                        instruction.id(),
                        result.diagnostic());
            }
        }
    }
}

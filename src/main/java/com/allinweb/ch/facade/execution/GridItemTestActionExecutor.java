package com.allinweb.ch.facade.execution;

import com.allinweb.ch.driver.ARPlaywrightDriver;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.facade.execution.GridItemTestInstructionRepository.InstructionSnapshot;
import com.allinweb.ch.model.FieldData;
import com.allinweb.ch.model.GridItemTestActionContracts.Action;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.socket.ExcelDataWorkspaceService.GridItemDataset;
import com.allinweb.ch.util.CryptationAlgorithm;
import com.allinweb.ch.util.ExtractedData;
import java.util.Objects;
import java.util.Optional;

/** Executes exactly one GridItem CLICK or INPUT against the already-open Playwright page. */
public final class GridItemTestActionExecutor {
    public static final String ABC_FALLBACK = "ABC";

    private final BrowserPort browser;
    private final ActiveCellPort activeCells;
    private final Decoder decoder;

    public GridItemTestActionExecutor() {
        this(new DefaultBrowserPort(), (job, block, column, row, instruction) -> {},
                CryptationAlgorithm::decrypt);
    }

    public GridItemTestActionExecutor(ActiveCellPort activeCells) {
        this(new DefaultBrowserPort(), activeCells, CryptationAlgorithm::decrypt);
    }

    GridItemTestActionExecutor(BrowserPort browser, ActiveCellPort activeCells, Decoder decoder) {
        this.browser = Objects.requireNonNull(browser, "browser");
        this.activeCells = Objects.requireNonNull(activeCells, "activeCells");
        this.decoder = Objects.requireNonNull(decoder, "decoder");
    }

    public Outcome execute(
            InstructionSnapshot instruction,
            Action action,
            int excelRowIndex,
            Optional<GridItemDataset> selectedDataset) {
        Objects.requireNonNull(instruction, "instruction");
        Objects.requireNonNull(action, "action");
        Optional<GridItemDataset> dataset = selectedDataset == null
                ? Optional.empty() : selectedDataset;
        InstructionLoad target = instruction.toInstructionLoad(action);
        if (action == Action.CLICK) {
            boolean passed = browser.clickOnce(target);
            return passed
                    ? Outcome.passed(
                            "CLICK_COMPLETED",
                            "Test Click completed on the active Playwright page.",
                            "NOT_APPLICABLE",
                            null,
                            null,
                            excelRowIndex,
                            null,
                            null)
                    : Outcome.failed(
                            "CLICK_FAILED",
                            "Test Click could not find or activate the GridItem element.",
                            "NOT_APPLICABLE",
                            null,
                            null,
                            excelRowIndex,
                            null,
                            null);
        }

        Integer effectiveRowIndex;
        if (dataset.isPresent()) {
            effectiveRowIndex = dataset.get().selectedRowIndex();
        } else {
            effectiveRowIndex = Integer.valueOf(excelRowIndex);
        }
        ResolvedInput input = resolveInput(instruction, effectiveRowIndex, dataset);
        if (input.failureCode() != null) {
            return Outcome.failed(
                    input.failureCode(),
                    input.failureMessage(),
                    input.valueSource(),
                    input.datasetMode(),
                    input.column(),
                    effectiveRowIndex,
                    input.datasetEpoch(),
                    input.datasetRevision());
        }
        boolean passed = browser.fillOnce(target, new FieldData(input.column(), input.value()));
        if (passed
                && effectiveRowIndex != null
                && !"ABC_FALLBACK".equals(input.valueSource())) {
            activeCells.publish(
                    instruction.botJobId(),
                    instruction.blockName(),
                    input.column(),
                    effectiveRowIndex,
                    instruction.id());
        }
        return passed
                ? Outcome.passed(
                        "INPUT_COMPLETED",
                        "Test Input completed on the active Playwright page.",
                        input.valueSource(),
                        input.datasetMode(),
                        input.column(),
                        effectiveRowIndex,
                        input.datasetEpoch(),
                        input.datasetRevision())
                : Outcome.failed(
                        "INPUT_FAILED",
                        "Test Input could not find or update the GridItem element.",
                        input.valueSource(),
                        input.datasetMode(),
                        input.column(),
                        effectiveRowIndex,
                        input.datasetEpoch(),
                        input.datasetRevision());
    }

    private ResolvedInput resolveInput(
            InstructionSnapshot instruction,
            Integer rowIndex,
            Optional<GridItemDataset> selectedDataset) {
        String expectedColumn = instruction.displayKey();
        if (expectedColumn == null || expectedColumn.isBlank()) expectedColumn = instruction.name();
        GridItemDataset dataset = selectedDataset.orElse(null);
        if (dataset == null) {
            return ResolvedInput.fallback(expectedColumn, null, null, null);
        }
        ExtractedData data = dataset.data();
        String column = resolveColumn(data, instruction);
        String value = column == null
                        || rowIndex == null
                        || rowIndex < 0
                        || rowIndex >= data.getNumberOfDataRows()
                ? null
                : data.getFieldValue(instruction.blockName(), column, rowIndex);
        if (column == null || value == null) {
            return ResolvedInput.fallback(
                    expectedColumn,
                    dataset.mode(),
                    dataset.datasetEpoch(),
                    dataset.datasetRevision());
        }
        if (instruction.codified()) {
            String decoded;
            try {
                decoded = decoder.decode(value);
            } catch (RuntimeException protectedValueFailure) {
                decoded = null;
            }
            if (decoded == null) {
                return new ResolvedInput(
                        null,
                        "EXCEL_MEMORY",
                        dataset.mode(),
                        column,
                        dataset.datasetEpoch(),
                        dataset.datasetRevision(),
                        "INPUT_VALUE_DECRYPT_FAILED",
                        "The selected protected Excel value could not be decoded.");
            }
            value = decoded;
        }
        return new ResolvedInput(
                value,
                "EXCEL_MEMORY",
                dataset.mode(),
                column,
                dataset.datasetEpoch(),
                dataset.datasetRevision(),
                null,
                null);
    }

    private static String resolveColumn(ExtractedData data, InstructionSnapshot instruction) {
        if (data == null) return null;
        String display = instruction.displayKey();
        if (display != null && !display.isBlank()
                && data.containsField(instruction.blockName(), display)) {
            return display;
        }
        String canonical = instruction.name();
        return canonical != null && !canonical.isBlank()
                        && data.containsField(instruction.blockName(), canonical)
                ? canonical : null;
    }

    public record Outcome(
            boolean passed,
            String code,
            String message,
            String valueSource,
            String datasetMode,
            String column,
            Integer excelRowIndex,
            Long datasetEpoch,
            Long datasetRevision) {
        static Outcome passed(
                String code,
                String message,
                String valueSource,
                String datasetMode,
                String column,
                Integer row,
                Long epoch,
                Long revision) {
            return new Outcome(
                    true, code, message, valueSource, datasetMode, column, row, epoch, revision);
        }

        static Outcome failed(
                String code,
                String message,
                String valueSource,
                String datasetMode,
                String column,
                Integer row,
                Long epoch,
                Long revision) {
            return new Outcome(
                    false, code, message, valueSource, datasetMode, column, row, epoch, revision);
        }
    }

    private record ResolvedInput(
            String value,
            String valueSource,
            String datasetMode,
            String column,
            Long datasetEpoch,
            Long datasetRevision,
            String failureCode,
            String failureMessage) {
        static ResolvedInput fallback(
                String column, String mode, Long epoch, Long revision) {
            return new ResolvedInput(
                    ABC_FALLBACK,
                    "ABC_FALLBACK",
                    mode,
                    column == null ? "" : column,
                    epoch,
                    revision,
                    null,
                    null);
        }
    }

    interface BrowserPort {
        boolean clickOnce(InstructionLoad instruction);

        boolean fillOnce(InstructionLoad instruction, FieldData value);
    }

    @FunctionalInterface
    public interface ActiveCellPort {
        void publish(int botJobId, String blockName, String column, int rowIndex, Integer instructionId);
    }

    @FunctionalInterface
    interface Decoder {
        String decode(String value);
    }

    private static final class DefaultBrowserPort implements BrowserPort {
        private ARPlaywrightDriver driver() {
            ARPlaywrightDriver driver = ARWebDriver.getInstance().currentPlaywrightDriver();
            if (driver == null || !driver.isOpen()) {
                throw new IllegalStateException("No active Playwright page is open.");
            }
            return driver;
        }

        @Override
        public boolean clickOnce(InstructionLoad instruction) {
            return driver().clickOnce(instruction);
        }

        @Override
        public boolean fillOnce(InstructionLoad instruction, FieldData value) {
            return driver().fillOnceWithoutValueLogging(instruction, value);
        }
    }
}

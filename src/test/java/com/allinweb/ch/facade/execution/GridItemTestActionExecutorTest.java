package com.allinweb.ch.facade.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.facade.execution.GridItemTestActionExecutor.Outcome;
import com.allinweb.ch.facade.execution.GridItemTestInstructionRepository.InstructionSnapshot;
import com.allinweb.ch.model.FieldData;
import com.allinweb.ch.model.GridItemTestActionContracts.Action;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.socket.ExcelDataWorkspaceService.GridItemDataset;
import com.allinweb.ch.util.ExtractedData;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GridItemTestActionExecutorTest {
    private static final String BLOCK = "Login";
    private static final String COLUMN = "Customer number";

    @Test
    void fillsExactlyOnceFromTheSelectedRealOrSyntheticMemoryDataset() {
        for (String mode : List.of("REAL", "SYNTHETIC")) {
            ExtractedData data = new ExtractedData();
            data.addFieldValue(BLOCK, COLUMN, mode + "-value", 0);
            RecordingBrowser browser = new RecordingBrowser();
            RecordingCells cells = new RecordingCells();
            GridItemTestActionExecutor executor =
                    new GridItemTestActionExecutor(browser, cells, value -> value);

            Outcome result = executor.execute(
                    instruction("I", false), Action.INPUT, 9, Optional.of(dataset(mode, data, 0)));

            assertTrue(result.passed());
            assertEquals("EXCEL_MEMORY", result.valueSource());
            assertEquals(mode, result.datasetMode());
            assertEquals(0, result.excelRowIndex());
            assertEquals(List.of(mode + "-value"), browser.filledValues);
            assertEquals(List.of(new ActiveCell(29, BLOCK, COLUMN, 0, 101)), cells.events);
        }
    }

    @Test
    void preservesAnExplicitEmptyExcelCellInsteadOfUsingTheFallback() {
        ExtractedData data = new ExtractedData();
        data.addFieldValue(BLOCK, COLUMN, "", 0);
        RecordingBrowser browser = new RecordingBrowser();
        RecordingCells cells = new RecordingCells();
        GridItemTestActionExecutor executor =
                new GridItemTestActionExecutor(browser, cells, value -> value);

        Outcome result = executor.execute(
                instruction("I", false), Action.INPUT, 9, Optional.of(dataset("REAL", data, 0)));

        assertTrue(result.passed());
        assertEquals("EXCEL_MEMORY", result.valueSource());
        assertEquals(List.of(""), browser.filledValues);
        assertEquals(1, cells.events.size());
    }

    @Test
    void usesUppercaseAbcOnlyWhenTheSelectedMemoryCellIsMissing() {
        RecordingBrowser browser = new RecordingBrowser();
        RecordingCells cells = new RecordingCells();
        GridItemTestActionExecutor executor =
                new GridItemTestActionExecutor(browser, cells, value -> value);

        Outcome withoutDataset = executor.execute(
                instruction("I", false), Action.INPUT, 3, Optional.empty());
        ExtractedData wrongColumn = new ExtractedData();
        wrongColumn.addFieldValue(BLOCK, "Other", "not selected", 0);
        Outcome withoutCell = executor.execute(
                instruction("I", false),
                Action.INPUT,
                0,
                Optional.of(dataset("SYNTHETIC", wrongColumn, 0)));

        assertEquals("ABC_FALLBACK", withoutDataset.valueSource());
        assertEquals(3, withoutDataset.excelRowIndex());
        assertEquals("ABC_FALLBACK", withoutCell.valueSource());
        assertEquals(List.of("ABC", "ABC"), browser.filledValues);
        assertTrue(cells.events.isEmpty(), "Fallback values must not highlight an Excel cell");
    }

    @Test
    void resolvesTheBackendSelectedRowAndIgnoresTheRequestFallback() {
        ExtractedData data = new ExtractedData();
        data.addFieldValue(BLOCK, "Customer", "row zero", 0);
        data.addFieldValue(BLOCK, "Customer", "row one", 1);
        RecordingBrowser browser = new RecordingBrowser();
        GridItemTestActionExecutor executor = new GridItemTestActionExecutor(
                browser, (job, block, column, row, instruction) -> {}, value -> value);

        Outcome result = executor.execute(
                instruction("I", false), Action.INPUT, 0, Optional.of(dataset("REAL", data, 1)));

        assertTrue(result.passed());
        assertEquals("Customer", result.column());
        assertEquals(1, result.excelRowIndex());
        assertEquals(List.of("row one"), browser.filledValues);
    }

    @Test
    void retainedEmptyMemoryDoesNotUseTheRequestRowAsASelection() {
        RecordingBrowser browser = new RecordingBrowser();
        GridItemTestActionExecutor executor = new GridItemTestActionExecutor(
                browser, (job, block, column, row, instruction) -> {}, value -> value);

        Outcome result = executor.execute(
                instruction("I", false),
                Action.INPUT,
                7,
                Optional.of(dataset("REAL", new ExtractedData(), null)));

        assertTrue(result.passed());
        assertEquals("ABC_FALLBACK", result.valueSource());
        assertNull(result.excelRowIndex());
        assertEquals(List.of("ABC"), browser.filledValues);
    }

    @Test
    void refusesProtectedDataThatCannotBeDecodedWithoutCallingTheBrowser() {
        ExtractedData data = new ExtractedData();
        data.addFieldValue(BLOCK, COLUMN, "protected", 0);
        RecordingBrowser browser = new RecordingBrowser();
        GridItemTestActionExecutor executor = new GridItemTestActionExecutor(
                browser,
                (job, block, column, row, instruction) -> {},
                value -> { throw new IllegalArgumentException("sensitive protected value"); });

        Outcome result = executor.execute(
                instruction("I", true), Action.INPUT, 0, Optional.of(dataset("REAL", data, 0)));

        assertFalse(result.passed());
        assertEquals("INPUT_VALUE_DECRYPT_FAILED", result.code());
        assertTrue(browser.filledValues.isEmpty());
    }

    @Test
    void clicksExactlyOnceAndNeverFills() {
        RecordingBrowser browser = new RecordingBrowser();
        GridItemTestActionExecutor executor = new GridItemTestActionExecutor(
                browser, (job, block, column, row, instruction) -> {}, value -> value);

        Outcome result = executor.execute(
                instruction("C", false), Action.CLICK, 0, Optional.empty());

        assertTrue(result.passed());
        assertEquals(1, browser.clicks);
        assertTrue(browser.filledValues.isEmpty());
    }

    private static GridItemDataset dataset(
            String mode, ExtractedData data, Integer selectedRowIndex) {
        return new GridItemDataset(
                29,
                2,
                mode,
                4L,
                7L,
                selectedRowIndex,
                Instant.parse("2026-08-07T00:00:00Z"),
                data);
    }

    private static InstructionSnapshot instruction(String action, boolean codified) {
        return new InstructionSnapshot(
                2, 29, "Lloyds", "NORMAL", 10, 1, BLOCK, true, 0, "",
                101, 1, action, "Customer", COLUMN, "", "//input[@id='customer']",
                "", "", "", "input", "", "", "#customer", "", null,
                false, false, 6, 0, codified, false, true, null, null, List.of());
    }

    private static final class RecordingBrowser implements GridItemTestActionExecutor.BrowserPort {
        private int clicks;
        private final List<String> filledValues = new ArrayList<>();

        @Override
        public boolean clickOnce(InstructionLoad instruction) {
            clicks++;
            return true;
        }

        @Override
        public boolean fillOnce(InstructionLoad instruction, FieldData value) {
            filledValues.add(value.getValue());
            return true;
        }
    }

    private static final class RecordingCells implements GridItemTestActionExecutor.ActiveCellPort {
        private final List<ActiveCell> events = new ArrayList<>();

        @Override
        public void publish(
                int botJobId,
                String blockName,
                String column,
                int rowIndex,
                Integer instructionId) {
            events.add(new ActiveCell(botJobId, blockName, column, rowIndex, instructionId));
        }
    }

    private record ActiveCell(
            int botJobId, String blockName, String column, int rowIndex, Integer instructionId) {}
}

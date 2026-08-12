package com.allinweb.ch.facade.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.facade.PlaywrightActionExecutor.TextResult;
import com.allinweb.ch.facade.actions.RuntimeVariableMemoryRegistry;
import com.allinweb.ch.facade.actions.RuntimeVariableMemoryRegistry.BotJobKey;
import com.allinweb.ch.facade.actions.RuntimeVariableMemoryRegistry.Definition;
import com.allinweb.ch.facade.actions.RuntimeVariableValue;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.BlockSnapshot;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.Environment;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.InstructionSnapshot;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.Owner;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.Plan;
import com.allinweb.ch.model.FieldData;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.Scope;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.RuntimeValueState;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.StepDisposition;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.StepStatus;
import com.allinweb.ch.socket.ExcelDataWorkspaceService.IntegrationDataset;
import com.allinweb.ch.util.ExtractedData;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SmokeTestIntegrationStepExecutorTest {
    private static final Owner OWNER = new Owner(9_202, 93_032);
    private static final BlockSnapshot BLOCK =
            new BlockSnapshot(223, 1, "Login", "", null, "", true, null);
    private static final Environment ENVIRONMENT = new Environment(
            OWNER.homeBankingId(),
            "Test organization",
            OWNER.botJobId(),
            "Integration test Bot Job",
            "NORMAL",
            1,
            "Test environment",
            "https://example.test",
            "{}",
            "chromium");
    private static final String PLAN_REVISION = "a".repeat(64);
    private static final String DATA_REVISION = "b".repeat(64);

    @AfterEach
    void clearRuntimeMemory() {
        RuntimeVariableMemoryRegistry.getInstance()
                .remove(new BotJobKey(OWNER.homeBankingId(), OWNER.botJobId()));
    }

    @Test
    void executesClickInputOutputAndRefreshThroughTheNarrowBrowserPort() {
        InstructionSnapshot click =
                instruction(BLOCK, 101, 1, "C", "Submit", null, true, null, null, Map.of());
        InstructionSnapshot input = instruction(
                BLOCK, 102, 2, "I", "Username", "Login name", true, null, null, Map.of());
        InstructionSnapshot output =
                instruction(BLOCK, 103, 3, "O", "Status", null, true, null, null, Map.of());
        InstructionSnapshot refresh = instruction(
                BLOCK, 104, 4, "REFRESH", "Refresh", null, true, null, null, Map.of());
        InstructionSnapshot refreshLoop = instruction(
                BLOCK,
                105,
                5,
                "REFRESH_LOOP",
                "Refresh loop",
                null,
                true,
                null,
                null,
                Map.of());
        InstructionSnapshot refreshHold = instruction(
                BLOCK,
                106,
                6,
                "REFRESH_HOLD",
                "Refresh hold",
                null,
                true,
                null,
                null,
                Map.of());
        InstructionSnapshot back = instruction(
                BLOCK, 107, 7, "BACK", "Back", null, true, null, null, Map.of());
        InstructionSnapshot screenshot = instruction(
                BLOCK, 108, 8, "P", "Screenshot", null, true, null, null, Map.of());
        InstructionSnapshot quit = instruction(
                BLOCK, 109, 9, "Q", "Close Browser", null, true, null, null, Map.of());
        InstructionSnapshot nextEnter = instruction(
                BLOCK, 110, 10, "NEXT_ENTER", "Next Enter", null, true, null, null, Map.of());
        InstructionSnapshot swipeDown = instructionWithOperation(
                BLOCK, 111, 11, "SWIPE_DOWN", "Swipe Down", "2", true);
        InstructionSnapshot swipeUp = instructionWithOperation(
                BLOCK, 112, 12, "SWIPE_UP", "Swipe Up", "1", true);
        Plan plan = plan(
                List.of(BLOCK),
                List.of(
                        click, input, output, refresh, refreshLoop, refreshHold, back,
                        screenshot, nextEnter, swipeDown, swipeUp, quit));
        ExtractedData data = new ExtractedData();
        data.addFieldValue(BLOCK.name(), input.displayKey(), "client@example.test", 0);
        FakeBrowser browser = new FakeBrowser();
        browser.textByInstruction.put(output.id(), TextResult.found(""));
        RecordingActiveCells activeCells = new RecordingActiveCells();
        SmokeTestIntegrationStepExecutor executor =
                new SmokeTestIntegrationStepExecutor(browser, activeCells);
        SmokeTestIntegrationStepExecutor.RunVariables variables = runVariables();

        assertPassedPhysical(executor.execute(plan, dataset(data), click.id(), 0, variables));
        assertPassedPhysical(executor.execute(plan, dataset(data), input.id(), 0, variables));
        SmokeTestIntegrationStepExecutor.Outcome emptyOutput =
                executor.execute(plan, dataset(data), output.id(), 0, variables);
        assertPassedPhysical(emptyOutput);
        assertPassedPhysical(executor.execute(plan, dataset(data), refresh.id(), 0, variables));
        assertPassedPhysical(executor.execute(plan, dataset(data), refreshLoop.id(), 0, variables));
        assertPassedPhysical(executor.execute(plan, dataset(data), refreshHold.id(), 0, variables));
        assertPassedPhysical(executor.execute(plan, dataset(data), back.id(), 0, variables));
        assertPassedPhysical(executor.execute(plan, dataset(data), screenshot.id(), 0, variables));
        assertPassedPhysical(executor.execute(plan, dataset(data), nextEnter.id(), 0, variables));
        assertPassedPhysical(executor.execute(plan, dataset(data), swipeDown.id(), 0, variables));
        assertPassedPhysical(executor.execute(plan, dataset(data), swipeUp.id(), 0, variables));
        assertPassedPhysical(executor.execute(plan, dataset(data), quit.id(), 0, variables));

        assertEquals(List.of(click.id()), browser.clickedInstructionIds);
        assertEquals(1, browser.fills.size());
        assertEquals(input.id(), browser.fills.get(0).instructionId());
        assertEquals(input.displayKey(), browser.fills.get(0).field().getKey());
        assertEquals("client@example.test", browser.fills.get(0).field().getValue());
        assertEquals(List.of(output.id()), browser.textInstructionIds);
        assertEquals(3, browser.reloadCount);
        assertEquals(1, browser.backCount);
        assertEquals(1, browser.screenshotCount);
        assertEquals(1, browser.nextEnterCount);
        assertEquals(List.of(2, -1), browser.scrollDirections);
        assertEquals(1, browser.closeCount);
        assertEquals(
                List.of(new ActiveCell(
                        OWNER.botJobId(), BLOCK.name(), input.displayKey(), 0, input.id())),
                activeCells.events);
    }

    @Test
    void distinguishesAProducedEmptyOutputFromASelectorMiss() {
        InstructionSnapshot empty =
                instruction(BLOCK, 111, 1, "O", "Empty", null, true, null, null, Map.of());
        InstructionSnapshot missing =
                instruction(BLOCK, 112, 2, "O", "Missing", null, true, null, null, Map.of());
        Plan plan = plan(List.of(BLOCK), List.of(empty, missing));
        FakeBrowser browser = new FakeBrowser();
        browser.textByInstruction.put(empty.id(), TextResult.found(""));
        SmokeTestIntegrationStepExecutor executor =
                new SmokeTestIntegrationStepExecutor(browser, ignoredActiveCells());

        assertPassedPhysical(executor.execute(
                plan, dataset(new ExtractedData()), empty.id(), 0, runVariables()));
        assertFailureCode(
                executor.execute(
                        plan, dataset(new ExtractedData()), missing.id(), 0, runVariables()),
                "OUTPUT_READ_FAILED");
        assertEquals(List.of(empty.id(), missing.id()), browser.textInstructionIds);
    }

    @Test
    void getWritesRunLocalValueAndSetReadsItWithoutDurablePersistence() {
        int variableId = 701;
        InstructionSnapshot output =
                instruction(BLOCK, 201, 1, "O", "Balance", null, true, null, null, Map.of());
        InstructionSnapshot input =
                instruction(BLOCK, 202, 2, "I", "Payment", null, true, null, null, Map.of());
        InstructionSnapshot get = instruction(
                BLOCK,
                203,
                3,
                "GET",
                "Get balance",
                null,
                true,
                output.id(),
                BLOCK.id(),
                Map.of("GET_WRITE", variableId));
        InstructionSnapshot set = instruction(
                BLOCK,
                204,
                4,
                "SET",
                "Set payment",
                null,
                true,
                input.id(),
                BLOCK.id(),
                Map.of("READ_SET", variableId));
        Plan plan = plan(List.of(BLOCK), List.of(output, input, get, set));
        FakeBrowser browser = new FakeBrowser();
        browser.textByInstruction.put(output.id(), TextResult.found("CHF 42.00"));
        SmokeTestIntegrationStepExecutor executor =
                new SmokeTestIntegrationStepExecutor(browser, ignoredActiveCells());
        SmokeTestIntegrationStepExecutor.RunVariables variables = runVariables();

        SmokeTestIntegrationStepExecutor.Outcome getResult =
                executor.execute(plan, dataset(new ExtractedData()), get.id(), 0, variables);
        SmokeTestIntegrationStepExecutor.Outcome setResult =
                executor.execute(plan, dataset(new ExtractedData()), set.id(), 0, variables);

        assertPassedPhysical(getResult);
        assertEquals("GET_VALUE_WRITTEN", getResult.code());
        assertEquals(variableId, getResult.runtimeVariableId());
        assertEquals(RuntimeVariableValue.value("CHF 42.00"), getResult.runtimeValue());
        assertEquals("CHF 42.00", variables.read(variableId).value());
        assertPassedPhysical(setResult);
        assertEquals(1, browser.fills.size());
        assertEquals(input.id(), browser.fills.get(0).instructionId());
        assertEquals(input.displayKey(), browser.fills.get(0).field().getKey());
        assertEquals("CHF 42.00", browser.fills.get(0).field().getValue());
    }

    @Test
    void freezesSharedRuntimeValuesAtRunStartAndKeepsLaterGetWritesLocal() {
        int variableId = 702;
        RuntimeVariableMemoryRegistry registry = RuntimeVariableMemoryRegistry.getInstance();
        BotJobKey owner = new BotJobKey(OWNER.homeBankingId(), OWNER.botJobId());
        registry.reconcileDefinitions(
                owner, List.of(new Definition(variableId, "Balance", "$String")), true);
        assertTrue(registry.write(
                owner,
                variableId,
                "before",
                RuntimeVariableMemoryRegistry.ValueSource.EXECUTION));
        SmokeTestIntegrationStepExecutor.RunVariables variables = runVariables();

        assertTrue(registry.write(
                owner,
                variableId,
                "after",
                RuntimeVariableMemoryRegistry.ValueSource.EXECUTION));
        assertEquals("before", variables.read(variableId).value());
        assertEquals(
                RuntimeValueState.VALUE,
                variables.initialSnapshot().values().get(variableId).state());
        assertEquals(
                "before",
                variables.initialSnapshot().values().get(variableId).value());
        assertThrows(
                UnsupportedOperationException.class,
                () -> variables.initialSnapshot().values().clear());

        InstructionSnapshot output =
                instruction(BLOCK, 211, 1, "O", "Balance", null, true, null, null, Map.of());
        InstructionSnapshot get = instruction(
                BLOCK,
                212,
                2,
                "GET",
                "Get balance",
                null,
                true,
                output.id(),
                BLOCK.id(),
                Map.of("GET_WRITE", variableId));
        FakeBrowser browser = new FakeBrowser();
        browser.textByInstruction.put(output.id(), TextResult.found("local GET"));
        SmokeTestIntegrationStepExecutor executor =
                new SmokeTestIntegrationStepExecutor(browser, ignoredActiveCells());

        SmokeTestIntegrationStepExecutor.Outcome result = executor.execute(
                plan(List.of(BLOCK), List.of(output, get)),
                dataset(new ExtractedData()),
                get.id(),
                0,
                variables);

        assertPassedPhysical(result);
        assertEquals("local GET", variables.read(variableId).value());
        assertEquals(
                "after",
                registry.read(owner, variableId).value(),
                "durableRuntimeWrites=false must not mutate shared runtime memory");
    }

    @Test
    void refusesMissingExcelCellBeforePublishingOrFilling() {
        InstructionSnapshot input =
                instruction(BLOCK, 301, 1, "I", "Username", null, true, null, null, Map.of());
        Plan plan = plan(List.of(BLOCK), List.of(input));
        ExtractedData data = new ExtractedData();
        data.addField(BLOCK.name(), input.displayKey());
        data.addFieldValue(BLOCK.name(), "Different column", "keeps row zero present", 0);
        FakeBrowser browser = new FakeBrowser();
        RecordingActiveCells activeCells = new RecordingActiveCells();
        SmokeTestIntegrationStepExecutor executor =
                new SmokeTestIntegrationStepExecutor(browser, activeCells);

        SmokeTestIntegrationStepExecutor.Outcome result =
                executor.execute(plan, dataset(data), input.id(), 0, runVariables());

        assertEquals(StepStatus.FAILED, result.status());
        assertEquals(StepDisposition.PHYSICAL, result.disposition());
        assertEquals("EXCEL_VALUE_MISSING", result.code());
        assertTrue(browser.fills.isEmpty());
        assertTrue(activeCells.events.isEmpty());
    }

    @Test
    void validatesGetAndSetParentsAndTypedVariableSlotsBeforeBrowserAccess() {
        BlockSnapshot otherBlock =
                new BlockSnapshot(224, 2, "Other", "", null, "", true, null);
        InstructionSnapshot crossBlockParent = instruction(
                otherBlock, 401, 1, "O", "Remote output", null, true, null, null, Map.of());
        InstructionSnapshot validOutput =
                instruction(BLOCK, 402, 1, "O", "Output", null, true, null, null, Map.of());
        InstructionSnapshot validInput =
                instruction(BLOCK, 403, 2, "I", "Input", null, true, null, null, Map.of());
        InstructionSnapshot getWithCrossBlockParent = instruction(
                BLOCK,
                404,
                3,
                "GET",
                "Invalid parent",
                null,
                true,
                crossBlockParent.id(),
                otherBlock.id(),
                Map.of("GET_WRITE", 801));
        InstructionSnapshot getWithoutSlot = instruction(
                BLOCK,
                405,
                4,
                "GET",
                "Missing slot",
                null,
                true,
                validOutput.id(),
                BLOCK.id(),
                Map.of());
        InstructionSnapshot setWithoutSlot = instruction(
                BLOCK,
                406,
                5,
                "SET",
                "Missing slot",
                null,
                true,
                validInput.id(),
                BLOCK.id(),
                Map.of());
        Plan plan = plan(
                List.of(BLOCK, otherBlock),
                List.of(
                        crossBlockParent,
                        validOutput,
                        validInput,
                        getWithCrossBlockParent,
                        getWithoutSlot,
                        setWithoutSlot));
        FakeBrowser browser = new FakeBrowser();
        SmokeTestIntegrationStepExecutor executor =
                new SmokeTestIntegrationStepExecutor(browser, ignoredActiveCells());
        SmokeTestIntegrationStepExecutor.RunVariables variables = runVariables();

        assertFailureCode(
                executor.execute(
                        plan,
                        dataset(new ExtractedData()),
                        getWithCrossBlockParent.id(),
                        0,
                        variables),
                "GET_PARENT_MISSING");
        assertFailureCode(
                executor.execute(plan, dataset(new ExtractedData()), getWithoutSlot.id(), 0, variables),
                "GET_VARIABLE_MISSING");
        assertFailureCode(
                executor.execute(plan, dataset(new ExtractedData()), setWithoutSlot.id(), 0, variables),
                "SET_VARIABLE_MISSING");
        assertTrue(browser.clickedInstructionIds.isEmpty());
        assertTrue(browser.fills.isEmpty());
        assertTrue(browser.textInstructionIds.isEmpty());
    }

    @Test
    void acknowledgesLogicalInstructionsAndSkipsInactiveRows() {
        InstructionSnapshot checkValue =
                instruction(BLOCK, 501, 1, "CK", "Compare", null, true, null, null, Map.of());
        InstructionSnapshot csvCheck =
                instruction(BLOCK, 503, 3, "CSV CHECK", "CSV Compare", null, true, null, null, Map.of());
        InstructionSnapshot pdfCheck =
                instruction(BLOCK, 504, 4, "PDF CHECK", "PDF Compare", null, true, null, null, Map.of());
        InstructionSnapshot inactive =
                instruction(BLOCK, 502, 2, "C", "Inactive", null, false, null, null, Map.of());
        Plan plan = plan(List.of(BLOCK), List.of(checkValue, csvCheck, pdfCheck, inactive));
        FakeBrowser browser = new FakeBrowser();
        SmokeTestIntegrationStepExecutor executor =
                new SmokeTestIntegrationStepExecutor(browser, ignoredActiveCells());
        SmokeTestIntegrationStepExecutor.RunVariables variables = runVariables();

        SmokeTestIntegrationStepExecutor.Outcome logical =
                executor.execute(plan, dataset(new ExtractedData()), checkValue.id(), 0, variables);
        SmokeTestIntegrationStepExecutor.Outcome skipped =
                executor.execute(plan, dataset(new ExtractedData()), inactive.id(), 0, variables);
        SmokeTestIntegrationStepExecutor.Outcome csvLogical =
                executor.execute(plan, dataset(new ExtractedData()), csvCheck.id(), 0, variables);
        SmokeTestIntegrationStepExecutor.Outcome pdfLogical =
                executor.execute(plan, dataset(new ExtractedData()), pdfCheck.id(), 0, variables);

        assertEquals(StepStatus.PASSED, logical.status());
        assertEquals(StepDisposition.LOGICAL_ONLY, logical.disposition());
        assertEquals("LOGICAL_ONLY", logical.code());
        assertEquals(StepDisposition.LOGICAL_ONLY, csvLogical.disposition());
        assertEquals(StepDisposition.LOGICAL_ONLY, pdfLogical.disposition());
        assertEquals(StepStatus.SKIPPED, skipped.status());
        assertEquals(StepDisposition.INACTIVE, skipped.disposition());
        assertTrue(browser.clickedInstructionIds.isEmpty());
        assertTrue(browser.fills.isEmpty());
        assertTrue(browser.textInstructionIds.isEmpty());
        assertEquals(0, browser.reloadCount);
    }

    @ParameterizedTest
    @ValueSource(strings = {"E", "EXCELWRITE"})
    void failsUnsupportedVariableAndFileCommandsClosed(String action) {
        InstructionSnapshot unsupported =
                instruction(BLOCK, 601, 1, action, action, null, true, null, null, Map.of());
        FakeBrowser browser = new FakeBrowser();
        SmokeTestIntegrationStepExecutor executor =
                new SmokeTestIntegrationStepExecutor(browser, ignoredActiveCells());

        SmokeTestIntegrationStepExecutor.Outcome result = executor.execute(
                plan(List.of(BLOCK), List.of(unsupported)),
                dataset(new ExtractedData()),
                unsupported.id(),
                0,
                runVariables());

        assertEquals(StepStatus.FAILED, result.status());
        assertEquals(StepDisposition.UNSUPPORTED, result.disposition());
        assertTrue(
                result.code().equals("UNSUPPORTED_ACTION")
                        || result.code().equals("UNSUPPORTED_WEB_ELEMENT_ACTION"));
        assertTrue(browser.clickedInstructionIds.isEmpty());
        assertTrue(browser.fills.isEmpty());
        assertTrue(browser.textInstructionIds.isEmpty());
    }

    private static SmokeTestIntegrationStepExecutor.RunVariables runVariables() {
        return new SmokeTestIntegrationStepExecutor.RunVariables(
                OWNER.homeBankingId(), OWNER.botJobId(), false);
    }

    private static SmokeTestIntegrationStepExecutor.ActiveCellPort ignoredActiveCells() {
        return (botJobId, blockName, column, rowIndex, instructionId) -> {};
    }

    private static IntegrationDataset dataset(ExtractedData data) {
        return new IntegrationDataset(
                OWNER.botJobId(),
                OWNER.homeBankingId(),
                "REAL",
                1L,
                1L,
                DATA_REVISION,
                Instant.parse("2026-08-06T00:00:00Z"),
                data);
    }

    private static Plan plan(List<BlockSnapshot> blocks, List<InstructionSnapshot> instructions) {
        return new Plan(OWNER, ENVIRONMENT, Scope.all(), blocks, instructions, PLAN_REVISION);
    }

    private static InstructionSnapshot instruction(
            BlockSnapshot block,
            int id,
            int order,
            String action,
            String name,
            String clientNamed,
            boolean active,
            Integer parentId,
            Integer parentBlockId,
            Map<String, Integer> slots) {
        return new InstructionSnapshot(
                OWNER,
                ENVIRONMENT.botJobName(),
                ENVIRONMENT.botJobPriority(),
                block,
                id,
                order,
                action,
                name,
                clientNamed,
                "",
                "//test/" + id,
                "",
                "",
                "",
                "input",
                "",
                "",
                "",
                "",
                "",
                false,
                false,
                null,
                null,
                false,
                false,
                active,
                parentId,
                parentBlockId,
                List.of(),
                slots);
    }

    private static InstructionSnapshot instructionWithOperation(
            BlockSnapshot block,
            int id,
            int order,
            String action,
            String name,
            String operation,
            boolean active) {
        InstructionSnapshot base = instruction(
                block, id, order, action, name, null, active, null, null, Map.of());
        return new InstructionSnapshot(
                base.owner(), base.botJobName(), base.botJobPriority(), base.block(), base.id(),
                base.order(), base.action(), base.name(), base.clientNamed(), operation, base.xpath(),
                base.coordinates(), base.forceCoordinates(), base.iframeXpath(), base.tagName(),
                base.shadowHost(), base.shadowRoot(), base.cssSelector(), base.description(),
                base.defaultValue(), base.optional(), base.blockMarked(), base.actionCustomMaxWaitSec(),
                base.onHoldSeconds(), base.codified(), base.exportToAbr(), base.active(), base.parentId(),
                base.parentBlockId(), base.references(), base.variableSlots());
    }

    private static void assertPassedPhysical(SmokeTestIntegrationStepExecutor.Outcome result) {
        assertEquals(StepStatus.PASSED, result.status());
        assertEquals(StepDisposition.PHYSICAL, result.disposition());
    }

    private static void assertFailureCode(
            SmokeTestIntegrationStepExecutor.Outcome result, String expectedCode) {
        assertEquals(StepStatus.FAILED, result.status());
        assertEquals(StepDisposition.PHYSICAL, result.disposition());
        assertEquals(expectedCode, result.code());
        assertNull(result.runtimeVariableId());
    }

    private static final class FakeBrowser implements SmokeTestIntegrationStepExecutor.BrowserPort {
        private final List<Integer> clickedInstructionIds = new ArrayList<>();
        private final List<Fill> fills = new ArrayList<>();
        private final List<Integer> textInstructionIds = new ArrayList<>();
        private final Map<Integer, TextResult> textByInstruction = new HashMap<>();
        private int reloadCount;
        private int backCount;
        private int screenshotCount;
        private int nextEnterCount;
        private final List<Integer> scrollDirections = new ArrayList<>();
        private int closeCount;

        @Override
        public boolean clickOnce(InstructionSnapshot instruction) {
            clickedInstructionIds.add(instruction.id());
            return true;
        }

        @Override
        public boolean fillOnce(InstructionSnapshot instruction, FieldData data) {
            fills.add(new Fill(instruction.id(), data));
            return true;
        }

        @Override
        public TextResult text(InstructionSnapshot instruction) {
            textInstructionIds.add(instruction.id());
            return textByInstruction.getOrDefault(instruction.id(), TextResult.missing());
        }

        @Override
        public void reload() {
            reloadCount++;
        }

        @Override
        public void back() {
            backCount++;
        }

        @Override
        public void nextEnter() {
            nextEnterCount++;
        }

        @Override
        public int scrollViewports(int direction, int count) {
            scrollDirections.add(direction * count);
            return count;
        }

        @Override
        public byte[] screenshot() {
            screenshotCount++;
            return new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        }

        @Override
        public void close() {
            closeCount++;
        }
    }

    private static final class RecordingActiveCells
            implements SmokeTestIntegrationStepExecutor.ActiveCellPort {
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

    private record Fill(int instructionId, FieldData field) {}

    private record ActiveCell(
            int botJobId, String blockName, String column, int rowIndex, Integer instructionId) {}
}

package com.allinweb.ch.facade.execution.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.BlockSnapshot;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.Environment;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.InstructionSnapshot;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.Owner;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.Plan;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationStepExecutor.Outcome;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.Scope;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.StepDisposition;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.StepStatus;
import com.allinweb.ch.socket.ExcelDataWorkspaceService.IntegrationDataset;
import com.allinweb.ch.util.ExtractedData;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SmokeTestIntegrationV2StepExecutorTest {
    private static final Owner OWNER = new Owner(13, 29);
    private static final BlockSnapshot BLOCK =
            new BlockSnapshot(7, 1, "Login", "", null, "", true, 0);
    private static final String REVISION = "a".repeat(64);

    @Test
    void resolvesFrozenInputAndUsesOnlyTheV2ActionPort() {
        InstructionSnapshot input = instruction(1733, "I", "username", "Login name", false);
        ExtractedData data = new ExtractedData();
        data.addFieldValue(BLOCK.name(), input.displayKey(), "client@example.test", 0);
        RecordingActions actions = new RecordingActions(success("COMPLETED"));
        List<String> activeCells = new ArrayList<>();
        SmokeTestIntegrationV2StepExecutor executor = new SmokeTestIntegrationV2StepExecutor(
                actions,
                (botJobId, blockName, column, rowIndex, instructionId) ->
                        activeCells.add(botJobId + ":" + blockName + ":" + column + ":" + rowIndex));

        Outcome outcome = executor.execute(
                mock(ExecutionRuntimeRunCoordinator.Run.class),
                plan(List.of(input)),
                dataset(data),
                1L,
                input.id(),
                0);

        assertEquals(StepStatus.PASSED, outcome.status());
        assertEquals(StepDisposition.PHYSICAL, outcome.disposition());
        assertEquals("client@example.test", actions.inputValue);
        assertEquals(1L, actions.sequence);
        assertEquals(List.of("29:Login:Login name:0"), activeCells);
    }

    @Test
    void refusesNodeAmbiguityAndNeverFallsBackForUnmigratedCommands() {
        InstructionSnapshot click = instruction(1734, "C", "login", null, false);
        InstructionSnapshot get = instruction(1735, "GET", "read_login", null, false);
        RecordingActions actions = new RecordingActions(failure("AMBIGUOUS_TARGET"));
        SmokeTestIntegrationV2StepExecutor executor = new SmokeTestIntegrationV2StepExecutor(
                actions, (botJobId, blockName, column, rowIndex, instructionId) -> {});
        ExecutionRuntimeRunCoordinator.Run run = mock(ExecutionRuntimeRunCoordinator.Run.class);
        Plan plan = plan(List.of(click, get));

        Outcome ambiguous = executor.execute(run, plan, dataset(new ExtractedData()), 1L, click.id(), 0);
        Outcome unsupported = executor.execute(run, plan, dataset(new ExtractedData()), 2L, get.id(), 0);

        assertEquals(StepStatus.FAILED, ambiguous.status());
        assertEquals("AMBIGUOUS_TARGET", ambiguous.code());
        assertEquals("V2_COMMAND_NOT_MIGRATED", unsupported.code());
        assertEquals(1, actions.calls);
        assertNull(actions.inputValue);
    }

    private static JsonObject success(String code) {
        JsonObject result = failure(code);
        result.addProperty("ok", true);
        return result;
    }

    private static JsonObject failure(String code) {
        JsonObject diagnostic = new JsonObject();
        diagnostic.addProperty("code", code);
        JsonObject result = new JsonObject();
        result.addProperty("ok", false);
        result.add("diagnostic", diagnostic);
        return result;
    }

    private static InstructionSnapshot instruction(
            int id, String action, String name, String clientName, boolean optional) {
        return new InstructionSnapshot(
                OWNER, "Lloyds", "", BLOCK, id, id - 1732, action, name, clientName, "",
                "//*[@id='" + name + "']", "", "", "", "input", "", "", "", "", "",
                optional, false, null, null, false, false, true, null, null, List.of(), Map.of());
    }

    private static Plan plan(List<InstructionSnapshot> instructions) {
        Environment environment = new Environment(
                13, "Lloyds", 29, "Lloyds", "", 15, "TEST",
                "https://www.lloydsbank.com/", "", "CHROMIUM");
        return new Plan(OWNER, environment, Scope.all(), List.of(BLOCK), instructions, REVISION);
    }

    private static IntegrationDataset dataset(ExtractedData data) {
        return new IntegrationDataset(
                29, 13, "REAL", 1L, 1L, REVISION,
                Instant.parse("2026-08-11T12:00:00Z"), data);
    }

    private static final class RecordingActions
            implements SmokeTestIntegrationV2StepExecutor.ActionPort {
        private final JsonObject result;
        private int calls;
        private long sequence;
        private String inputValue;

        private RecordingActions(JsonObject result) {
            this.result = result;
        }

        @Override
        public JsonObject action(
                ExecutionRuntimeRunCoordinator.Run run,
                long sequence,
                int instructionId,
                String inputValue) {
            calls++;
            this.sequence = sequence;
            this.inputValue = inputValue;
            return result.deepCopy();
        }

        @Override
        public JsonObject refresh(ExecutionRuntimeRunCoordinator.Run run) {
            JsonObject result = new JsonObject();
            result.addProperty("state", "READY");
            return result;
        }
    }
}
